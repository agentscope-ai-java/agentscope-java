---
title: 샌드박스(Sandbox)
description: 격리된 실행 + 호출 간 복구 + 멀티 레플리카 배포
---

> 세 가지 파일 시스템 모드 비교는 [파일 시스템](/v2/ko/docs/harness/filesystem)을 참고하라. 이 페이지는 샌드박스 모드의 사용법에 초점을 맞춘다.

## 샌드박스가 해결하는 문제

에이전트의 **파일 작업과 명령 실행**을 격리된 환경에 가둔다; 호스트는 전혀 영향을 받지 않는다. 추가로 세 가지 이점이 있다.

1. **실행 경계** — 신뢰할 수 없는 입력, 수상한 스크립트, `rm -rf` 형태의 명령이 모두 샌드박스 안에만 머문다.
2. **호출 간 복구** — 대화 상태뿐 아니라 `pip install`, `npm install`, 생성된 임시 파일(실행 환경 자체)이 스냅샷되므로, 다음 `call()`은 재설치 없이 동일한 샌드박스에서 이어서 진행된다.
3. **멀티 레플리카 친화적** — 여러 레플리카가 같은 논리적 사용자를 서비스할 때, 샌드박스 상태가 하나의 슬롯을 공유할 수 있어 어떤 노드든 같은 워크스페이스를 재개할 수 있다.

## 최소 예제

로컬 Docker, 사용자별로 격리:

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("code-agent")
    .model(model)
    .workspace(workspace)
    .filesystem(new DockerFilesystemSpec()
        .image("ubuntu:24.04"))
    .build();

agent.call(msg, RuntimeContext.builder()
    .userId("alice")
    .sessionId("conv-1")
    .build()).block();
```

`call()` 간에 동일한 `userId`를 사용하면 → 같은 샌드박스가 자동으로 재사용된다(또는 스냅샷에서 복원된다). `userId`가 다르면 → 별도의 샌드박스가 사용된다. `userId`가 없으면, `sessionId`가 격리 키로 사용된다.

## IsolationScope — 누가 샌드박스를 공유하는가

모든 샌드박스 설정은 `SandboxFilesystemSpec`(예: `DockerFilesystemSpec`)에 위치한다. 핵심 파라미터는 `isolationScope`다.

| Scope | 공유 방식 | 일반적인 용도 |
|-------|---------|-------------|
| `USER`(기본값) | 동일한 `userId`의 세션들이 공유; userId가 없으면 `SESSION`으로 폴백 | 멀티유저 SaaS — 각 사용자가 대화 간에 하나의 워크스페이스를 유지 |
| `SESSION` | 각 sessionId가 독립적 | 엄격한 대화별 격리 |
| `AGENT` | 이 에이전트의 모든 사용자/세션이 공유 | 공개 도구형 에이전트, 공유 지식 베이스 |
| `GLOBAL` | 스토어당 하나의 공유 슬롯 | 신중하게 사용할 것 |

```java
// 명시적인 SESSION scope(기본값 USER를 오버라이드)
.filesystem(new DockerFilesystemSpec()
    .image("ubuntu:24.04")
    .isolationScope(IsolationScope.SESSION))
```

`SESSION`은 (각 세션이 자신만의 슬롯을 가지므로) 태생적으로 동시성에 안전하다. 멀티 레플리카 배포에서 `USER` / `AGENT` / `GLOBAL`은 뮤텍스와 함께 사용해야 한다(아래 "동시성 제어" 참고).

**USER-scope 폴백**: `IsolationScope.USER`가 활성화되어 있지만(명시적으로 설정되었든 기본값이든) `RuntimeContext.userId`가 없으면, 프레임워크는 자동으로 `sessionId`를 사용해 `SESSION` scope로 폴백한다. 즉, userId 누락에 대비한 별도 방어 코드가 필요 없다 — 샌드박스는 우아하게 성능이 저하된다.

## 호출 간 복구 = 스냅샷

샌드박스는 각 `call()`이 끝날 때 워크스페이스를 스냅샷하고, 다음 시작 시 복원한다.

- 컨테이너가 여전히 살아있고 워크스페이스도 그대로 → 그냥 이어서 진행(가장 빠름)
- 컨테이너가 사라짐 → 스냅샷에서 재부팅하여 워크스페이스 복원
- 스냅샷 없음 → `WorkspaceSpec`에서 완전히 초기화(콜드 스타트)

스냅샷이 어디에 저장될지는 `snapshotSpec`이 결정한다.

| 옵션 | 사용 시점 |
|--------|------|
| `NoopSnapshotSpec`(기본값) | 영속성 없음; 컨테이너가 사라지면 콜드 스타트 |
| `LocalSnapshotSpec` | 호스트 로컬 파일(단일 머신 장기 실행) |
| `OssSnapshotSpec` | OSS / S3 호환(멀티 레플리카) |
| `RedisSnapshotSpec` | Redis(저지연, 작은 워크스페이스) |
| `JdbcSnapshotSpec` | MySQL / JDBC BLOB(기존 관계형 DB) |

```java
.filesystem(new DockerFilesystemSpec()
    .image("ubuntu:24.04")
    .snapshotSpec(new OssSnapshotSpec(ossClient, "my-bucket", "agentscope/")))
```

호스트 쪽 워크스페이스 파일(`AGENTS.md` / `skills/` / `subagents/` / `knowledge/`)은 매 시작 시 콘텐츠 해시로 게이팅되어 샌드박스로 동기화된다. 따라서 `skills/` 아래의 스크립트를 수정하면, 다음 `call()`은 샌드박스 내부에서 새 버전을 사용한다.

## 분산 배포

여러 레플리카가 같은 에이전트를 실행하고, 어떤 레플리카든 같은 사용자의 대화를 이어받을 수 있어야 한다면 다음이 필요하다.

1. 분산 `AgentStateStore`(예: Redis 기반) — 빌더의 `.stateStore(...)`를 통해 전달
2. `Noop`이 아닌 스냅샷(OSS / Redis / 원격 스토어) — 파일 시스템 spec에 `.snapshotSpec(...)`으로 직접 설정
3. 적절한 `IsolationScope`(기본값 `USER`가 보통 맞다)

모든 것을 한 곳에서 설정한다.

```java
HarnessAgent.builder()
    .name("assistant")
    .model(model)
    .workspace(workspace)
    .stateStore(redisStateStore)                    // 분산 상태
    .filesystem(new DockerFilesystemSpec()
        .image("ubuntu:24.04")
        .snapshotSpec(ossSnapshotSpec)              // 레플리카 간 스냅샷
        .isolationScope(IsolationScope.USER))       // 기본값이므로 생략 가능
    .build();
```

프레임워크는 샌드박스 메타데이터(컨테이너 ID, 스냅샷 포인터, 워크스페이스 준비 플래그)를 에이전트 런타임 상태를 담고 있는 동일한 `AgentStateStore`에 저장한다. 분산 스토어를 제공하면 레플리카 간 샌드박스 재개가 자동으로 활성화된다 — 별도의 설정이 필요 없다.

로컬 `AgentStateStore`(기본값인 `JsonFileAgentStateStore`)를 샌드박스 모드와 함께 사용하면, 프레임워크는 빌드 시점에 샌드박스 상태가 JVM 재시작을 견디지 못하고 인스턴스 간에 공유될 수 없다는 것을 알리는 경고 로그를 남긴다.

## 동시성 제어(멀티 레플리카)

`USER` / `AGENT` / `GLOBAL` 모드에서 레플리카 간에 두 레플리카가 동시에 같은 사용자를 서비스하면 둘 다 같은 슬롯에 쓴다 — 마지막에 쓴 것이 이긴다. 이것이 문제가 된다면 분산 락이 필요하다.

**권장**: `distributedStore(...)`를 사용하라 — 스냅샷과 실행 가드가 자동으로 주입된다.

```java
DistributedStore store = RedisDistributedStore.fromJedis(jedis);

HarnessAgent.builder()
    .distributedStore(store)    // stateStore + snapshotSpec + executionGuard를 자동 연결
    .filesystem(new DockerFilesystemSpec()
        .image("ubuntu:24.04")
        .isolationScope(IsolationScope.USER))
    .build();
```

락 파라미터를 커스터마이즈하려면, `SandboxFilesystemSpec`에 가드를 명시적으로 설정하라.

```java
.filesystem(new DockerFilesystemSpec()
    .image("ubuntu:24.04")
    .isolationScope(IsolationScope.USER)
    .executionGuard(RedisSandboxExecutionGuard.builder(jedis)
        .leaseTtl(Duration.ofMinutes(30)).build()))
```

내장 구현체: `RedisSandboxExecutionGuard`(Redis `SET NX PX`), `JdbcSandboxExecutionGuard`(MySQL `GET_LOCK()`). `SandboxExecutionGuard`를 직접 구현하여 Zookeeper, etcd, 또는 다른 락 스토어를 연결할 수도 있다.

## 직접 관리하는 샌드박스 인스턴스(고급)

기본적으로 프레임워크가 샌드박스 생명주기 전체를 소유한다. "내가 직접 관리하겠다"는 세 가지 시나리오가 있다.

**1. 이미 실행 중인 컨테이너가 있고, 에이전트가 그것을 사용하길 원한다**

```java
Sandbox mySandbox = dockerClient.create(workspaceSpec, snapshotSpec, options);
mySandbox.start();

SandboxContext callCtx = SandboxContext.builder()
    .client(dockerClient)
    .externalSandbox(mySandbox)       // 프레임워크는 호출이 끝날 때 stop()만 하고 shutdown()은 하지 않음
    .build();

agent.call(msgs, RuntimeContext.builder()
    .sessionId("my-session")
    .put(SandboxContext.class, callCtx)
    .build()).block();

// 완료되면 직접 종료
mySandbox.shutdown();
```

**2. 특정 스냅샷 문자열이 있고, 그 시점으로 복원하고 싶다**

```java
SandboxState savedState = dockerClient.deserializeState(savedStateJson);
SandboxContext callCtx = SandboxContext.builder()
    .client(dockerClient)
    .externalSandboxState(savedState)  // 프레임워크가 이 상태로부터 복원하지만 생명주기는 여전히 소유함
    .build();
```

**3. 여러 에이전트가 하나의 샌드박스를 공유**

각 에이전트의 `call()`에 동일한 `externalSandbox`를 전달한 뒤, 완료되면 직접 `shutdown()`하라.

## 샌드박스 스토어 선택하기

| 스토어 | 적합한 용도 |
|---------|----------|
| **Docker** | 로컬 개발 / 단일 머신 / 신뢰할 수 있는 셸 |
| **Kubernetes** | 자체 호스팅 K8s; 전적으로 [agent-sandbox](https://github.com/kubernetes-sigs/agent-sandbox)(SandboxClaim / WarmPool) 기반, 워크스페이스 영속성은 PVC를 통해 이루어짐(아래 참고) |
| **Daytona** | 범용 관리형 샌드박스 HTTP API |
| **E2B** | 범용 관리형 샌드박스 + 네이티브 플랫폼 스냅샷 |
| **AgentRun** | 알리바바 클라우드가 관리하는 샌드박스(Function Compute FC 3.0); 인스턴스별 NAS / OSS 자동 마운트; 중국 본토 저지연. 일반적인 `SandboxFilesystemSpec`으로 취급되며, 전체 설정 상세(템플릿, RAM 권한, NAS 우선 설정)는 통합 문서에 있다 |

모든 스토어는 동일한 인터페이스를 구현한다; 에이전트 코드, 툴킷, `AGENTS.md`는 바뀌지 않는다.

## 런타임 이미지 계약

샌드박스 이미지(Docker `image`, Kubernetes agent-sandbox 런타임 이미지 등)는 직접 선택하지만, **아무 이미지나 동작하지는 않는다**. Harness의 파일 도구(`read_file` / `write_file` / `edit_file` / `grep_files` / `glob_files` / `list_files`)와 스냅샷 메커니즘은 모두 샌드박스 내부에서 POSIX 셸 명령을 실행하는 방식으로 구현된다. 이미지는 아래 계약을 충족해야 하며, 그렇지 않으면 도구들이 진단하기 어려운 방식으로 실패한다.

### 기본 계약(모든 백엔드 공통)

이미지는 다음을 제공해야 한다.

| 범주 | 요구 사항 | 사용처 |
|----------|-------------|---------|
| 셸 | POSIX 호환 `sh`(`[ ]`, `&&`, 파이프, 리다이렉션, heredoc) | 모든 파일 도구, `execute` 도구 |
| 코어 유틸 | `mkdir` `dirname` `rm` `mv` `test` `printf` `sort` | 개별 파일 작업 |
| 텍스트/검색 | `sed`, `grep`(`-rHnF`, `--include` 지원), `find` | `read_file` 페이지네이션, `grep_files`, `glob_files` |
| 메타데이터 | GNU 스타일 `stat -c`(BSD `stat -f`는 안 됨) | `list_files`, `glob_files` |
| 아카이브/인코딩 | `tar`, `base64`(인코딩 + `-d` 디코딩) | 스냅샷 저장/복원, 파일 업로드/다운로드 |
| 인터프리터 | `python3` | `edit_file`(정확한 문자열 치환) |
| 파일 시스템 | 쓰기 가능한 워크스페이스 루트(기본 `/workspace`) | 모든 것 |

`ubuntu:24.04` 또는 `debian` 기반 이미지는 그대로 요건을 충족한다(`python3`는 설치가 필요할 수 있음); `alpine`(BusyBox `stat` / `grep`은 동작이 다름)과 distroless 이미지는 **충족하지 못한다**.

빠른 적합성 확인(이미지 내부에서 실행; 모두 성공해야 함):

```bash
sh -c 'echo ok' && python3 --version && tar --version \
  && printf x | base64 | base64 -d && stat -c %Y /tmp && grep -rHnF --include='*.txt' x /tmp; true
```

### Kubernetes(agent-sandbox)의 추가 계약

agent-sandbox 백엔드는 컨테이너에 `kubectl exec`로 들어가지 않는다. 대신 런타임 컨테이너가 노출하는 HTTP API(기본 포트 8888)와 통신한다. 이미지의 런타임 서비스는 다음을 구현해야 한다.

| 엔드포인트 | 시맨틱 |
|----------|----------|
| `POST /execute`(본문 `{"command": "..."}`) | **반드시 POSIX 셸 시맨틱으로 명령을 해석**해야 하며(`sh -c`와 동등함), `{"stdout", "stderr", "exit_code"}`를 반환해야 한다. Harness는 `cd ... && (...)`, 파이프 등 셸 구문을 포함한 명령을 보낸다 |
| `POST /upload`(멀티파트; `filename`은 상대 경로) | 파일 API 기본 디렉터리 아래에 파일을 씀 |
| `GET /download/{path}` | 상대 경로로 파일 바이트를 다운로드 |
| `GET /list/{path}`, `GET /exists/{path}` | 디렉터리 목록 / 존재 여부 확인 |

**파일 API 기본 디렉터리는 워크스페이스 루트와 일치해야 한다**(권장: 둘 다 `/workspace`). Harness는 `/upload` / `/download`를 통해 두 종류의 콘텐츠를 전송한다. 워크스페이스 스냅샷 tarball(해당 디렉터리 안의 `.agentscope-tmp/` 아래 임시 파일)과, 경로가 기본 디렉터리 아래에 있을 때 `write_file` / 파일 다운로드를 위한 단일 파일 바이트다(Linux는 단일 명령줄 인자를 약 128KiB로 제한하지만, 네이티브 파일 API 전송에는 그런 제한이 없다). 기본 디렉터리는 `KubernetesSandboxClientOptions.fileApiBaseDir`(기본값 `/workspace`)로 설정되며, 비워두면 base64-over-exec 전송으로 대체된다.

> 참고: 업스트림 agent-sandbox 리포지토리의 예제 런타임(`examples/python-runtime-sandbox`)은 셸 없이 `shlex.split` + `subprocess.run`으로 명령을 실행하며, 파일 API의 루트를 `/app`으로 둔다 — 이는 이 계약을 **충족하지 못하며**, 엔드포인트 형태에 대한 참고 자료로만 사용해야 한다. 업스트림 KEP-539.2는 런타임 인터페이스(REST/gRPC 스펙 + 적합성 테스트)를 표준화하는 중이며, 이 계약은 해당 공식 스펙이 확정되면 그에 맞춰 수렴할 수 있다.

### 왜 이렇게 설계되었는가

`Sandbox` 추상화의 주된 데이터 플레인 진입점은 `exec(command)`다. 이는 의도적인 설계다 — `edit_file` / `grep_files`(정규식, 문자열 치환, 글로빙)와 같은 도구 시맨틱은 소수의 파일 API 엔드포인트로 표현할 수 없으며, 이미지 내부의 표준 툴체인에 대해 셸 스크립트를 실행하는 것이 유일하게 이식 가능한 답이다. 파일 API(업로드/다운로드)는 순수한 바이트 전송만 처리한다. 워크스페이스 스냅샷과 단일 파일 업로드/다운로드는 이를 거친다(백엔드는 선택적 `SandboxFileTransfer` 인터페이스를 구현함으로써 이 기능을 선언한다). 그 외 모든 것은 `execute`를 거친다. 이는 곧 **이미지 계약이 샌드박스 인터페이스의 일부**임을 의미한다 — 이미지를 바꾸기 전에는 위의 적합성 확인을 반드시 실행하라.

**모델의 관점에서 경계를 넘는 것.** 위에서 설명한 파일 API(업로드/다운로드)는 내부 메커니즘이며 — LLM에게는 보이지 않고, `FilesystemTool`도 전송용 도구를 노출하지 않는다. 샌드박스 안의 에이전트가 생성한 산출물을 샌드박스 바깥의 목적지로 전달하는 지원 방식은 범용 **`deliver_artifact`** 도구다. 이는 `HarnessAgent.builder().artifactDeliveryTarget(...)`을 통해 `ArtifactDeliveryTarget`을 설정했을 때만 등록된다. SPI는 비즈니스에 종속되지 않는다 — `deliver(RuntimeContext, ArtifactDeliveryRequest) -> ArtifactDeliveryResult` — 따라서 목적지 로직(예: WebDAV 업로드)은 애플리케이션에 위치한다. 이 도구는 샌드박스 워크스페이스에서 파일 바이트를 다운로드하고 전송을 target에 위임한다. target이 설정되어 있지 않으면, 샌드박스 워크스페이스 프롬프트는 파일이 컨테이너를 벗어날 수 없다고 명확히 알린다.

## Kubernetes 상태 영속성: PVC가 첫 번째 계층이다

Kubernetes 스토어는 전적으로 agent-sandbox를 기반으로 한다. 샌드박스 파드는 agent-sandbox 컨트롤러가 관리하며, 이미지·리소스·스토리지는 모두 `SandboxTemplate` / `SandboxWarmPool`에서 클러스터 쪽에 선언된다 — Java 쪽은 인스턴스를 클레임(`SandboxClaim`)하고 연결할 뿐이다. 이는 다른 스토어와 한 가지 중요한 차이를 만든다. **워크스페이스 데이터의 영속성은 주로 PVC의 몫이지, Harness 스냅샷의 몫이 아니다.** 두 계층은 각각 하나씩을 책임진다.

| 계층 | 보존하는 것 | 복구 시나리오 |
|-------|-------------------|--------------------|
| **PVC**(`SandboxTemplate.volumeClaimTemplates`) | 워크스페이스 파일 자체 | 파드 재시작 / 축출 / 하이버네이트-후-깨어남 — 클레임이 여전히 살아있으며, 파일은 볼륨과 함께 그대로 돌아오고 전송이 없음 |
| **SandboxState + snapshotSpec**(Harness 계층) | 신원 포인터(claimName / namespace) + 워크스페이스 tarball 스냅샷 | 재개 시 샌드박스를 찾는 용도(항상 필요함); 클레임 삭제 / PVC 유실 / 클러스터 간 이동 시의 콜드 복구 |

프레임워크는 PVC에 대해 특별한 처리가 필요 없다. 재개 시, 시작 프로브가 `test -d /workspace`를 확인하며, PVC가 있으면 "워크스페이스 보존됨" 분기를 타고 스냅샷 복원은 완전히 건너뛴다.

**반드시 올바르게 설정해야 하는 세 가지:**

1. **PVC는 반드시 `workspaceRoot`(기본값 `/workspace`)에 마운트되어야 한다.** 다른 곳에 마운트하거나 `emptyDir`을 사용하면, 파드가 재시작될 때마다 워크스페이스가 사라지며 — 매 호출이 스냅샷 복원 또는 콜드 스타트로 성능이 저하된다. 참조 템플릿(업스트림 agent-sandbox 예제 기반):

```yaml
apiVersion: extensions.agents.x-k8s.io/v1beta1
kind: SandboxTemplate
spec:
  podTemplate:
    spec:
      containers:
      - name: runtime
        image: your-conformant-runtime:latest   # 위의 런타임 이미지 계약을 충족해야 함
        ports:
        - containerPort: 8888
        volumeMounts:
        - name: workspace
          mountPath: /workspace                 # = workspaceRoot = fileApiBaseDir
  volumeClaimTemplates:
  - metadata:
      name: workspace
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 1Gi
```

2. **클레임 생명주기 경계에 주의하라.** `shutdownTime` / `shutdownPolicy: Delete`가 Sandbox를 제거하면, PVC 기반의 웜 복구는 끝난다 — 다음 재개는 클레임을 찾지 못하고, 프레임워크는 (새롭고 비어있는 PVC와 함께) 새 샌드박스를 생성한다. 워크스페이스가 다시 돌아올지는 3번 항목에 달려 있다.

3. **`snapshotSpec`을 상황에 맞게 선택하라.** PVC + `NoopSnapshotSpec`(기본값): 매 호출이 끝날 때마다 tar-and-upload를 건너뛰지만, 클레임이 사라지면 콜드 스타트라는 비용을 치른다. PVC + OSS / Redis 스냅샷: 이중 안전장치다 — 클레임 만료, PVC 유실, 클러스터 간 이동 모두 스냅샷에서 복구되지만, 호출마다 전체 아카이브라는 비용이 든다.

한 가지 더 기억할 점: `SandboxState` 신원 계층은 절대 선택 사항이 아니다 — 멀티 레플리카 배포에서는 분산 `AgentStateStore`를 설정해야 한다. 그렇지 않으면 다른 레플리카들이 claimName을 알 수 없어, PVC 위의 데이터가 아무리 온전해도 접근할 수 없다.

## 워크스페이스가 샌드박스로 매핑되는 방식

`workspace/` 아래의 호스트 쪽 핵심 파일(`AGENTS.md`, `skills/`, `subagents/`, `knowledge/`)은 매 시작 시 콘텐츠 해시로 게이팅되어 샌드박스로 동기화된다 — 변경되지 않은 콘텐츠는 건너뛴다.

호스트 디렉터리(예: 코드 저장소)를 샌드박스에 바인딩하려면 `BindMountEntry`를 사용하라(Docker 전용; Kubernetes는 대신 클러스터 쪽 `SandboxTemplate` 파드 템플릿에서 마운트를 선언해야 한다; Daytona / E2B 같은 관리형 샌드박스는 클라우드에서 실행되므로 호스트 경로를 마운트할 수 없다).

샌드박스 내부의 파일 변경 사항은 호스트로 다시 동기화되지 않는다 — 샌드박스가 생성한 산출물을 가져오려면, 에이전트가 이를 `read_file`로 읽도록 하라.

## 자체 샌드박스 스토어 구현하기

Docker가 아닌 격리 환경(자체 호스팅 원격 실행기, 상용 샌드박스 API, 로컬 목 등)을 통합하려면 Harness 소스 코드를 수정할 필요가 없다 — 몇 가지 계약 인터페이스를 구현하고 `filesystem(...)`에 전달하면 된다. `agentscope-harness` 테스트 아래의 `InMemorySandbox` 계열이 복사해 쓰기 좋은 최소 골격이다.

## 관련 문서

- [파일 시스템](/v2/ko/docs/harness/filesystem) — 세 가지 선언적 모드 비교
- [워크스페이스](/v2/ko/docs/harness/workspace) — `workspace/` 아래의 어떤 파일이 샌드박스로 동기화되는지
- [아키텍처](/v2/ko/docs/harness/architecture) — 샌드박스 획득/해제가 call() 타임라인 어디에 위치하는지
