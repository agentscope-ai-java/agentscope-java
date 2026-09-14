---
title: "파일 시스템(Filesystem)"
description: "세 가지 배포 모드: 로컬 + shell / 공유 스토어 / 샌드박스; IsolationScope 차원; 멀티유저 격리; 각 모드에서 스킬과 도구가 동작하는 방식"
---

## 역할

`HarnessAgent`는 에이전트가 바라보는 **워크스페이스**의 관점을 "반드시 로컬 디스크여야 한다"는 제약에서 벗어나 하나의 균일한 인터페이스로 추상화한다. 모든 파일 도구(`read_file` / `write_file` / `edit_file` / `grep_files` / `glob_files` / `list_files`)와 선택적인 `execute`(셸)는 모두 이 추상화를 거쳐 실행된다.

이렇게 얻는 이점은 **에이전트 코드를 바꾸지 않고도** 세 가지 배포 모드를 전환할 수 있다는 것이다.

- 로컬 + shell — 단일 프로세스, 로컬, 신뢰할 수 있는 환경;
- 공유 스토어 — 여러 레플리카 / 파드가 동일한 장기 메모리를 공유;
- 샌드박스 — 파일과 명령이 격리된 컨테이너 안에서 실행되며, 동일한 워크스페이스 상태가 호출 간에 복원된다.

## 세 가지 선언적 모드

`HarnessAgent.Builder`의 `filesystem(...)`으로 하나를 선택한다(호출하지 않으면 기본값은 모드 3이다).

| 모드 | 설정 | Shell? | 사용 시점 |
|------|--------|--------|-------------|
| **1 · 공유 스토어** | `filesystem(new RemoteFilesystemSpec(store))` | 없음 | 여러 레플리카가 `MEMORY.md` / 대화 로그 / 하위 작업 기록을 KV를 통해 공유; **호스트에서는 셸을 사용할 수 없음** |
| **2 · 샌드박스** | `filesystem(new DockerFilesystemSpec()...)`, 또는 K8s / Daytona / E2B / AgentRun | 있음(샌드박스 내부) | 격리된 실행, 호출 간 워크스페이스 복구, 선택적 스냅샷 + 분산 배포 |
| **3 · 로컬 + shell** (기본값) | `filesystem(new LocalFilesystemSpec()...)` 또는 **생략** | 있음(호스트 `sh -c`) | 단일 프로세스 / 로컬 / 신뢰할 수 있는 환경 / 스크립트와 테스트 |

> `filesystem(...)`은 `abstractFilesystem(...)`과 상호 배타적이다. 후자는 완전히 직접 관리하는 파일 시스템을 위한 탈출구이며 거의 필요하지 않다.

---

### 모드 1: 공유 스토어(`RemoteFilesystemSpec`)

"여러 레플리카를 운영하지만 사용자의 장기 메모리는 항상 동기화되어야 한다"는 요구에 맞는 모드다. `BaseStore` 구현체(Redis / JDBC / 인메모리)를 전달하면 프레임워크가 경로 접두사를 기준으로 워크스페이스 파일을 자동으로 KV 스토어에 라우팅한다.

```java
// 최소 설정(권장: 한 줄로 설정할 수 있는 DistributedStore 사용)
DistributedStore store = RedisDistributedStore.fromJedis(jedis);

HarnessAgent agent = HarnessAgent.builder()
    .name("store-agent")
    .model(model)
    .workspace(workspace)
    .distributedStore(store)
    .filesystem(new RemoteFilesystemSpec()   // baseStore가 store로부터 자동 주입됨
        .isolationScope(IsolationScope.USER))
    .build();
```

#### 전체 설정 옵션

| 메서드 | 설명 | 기본값 |
|--------|-------------|---------|
| `isolationScope(IsolationScope)` | 네임스페이스 격리 차원(아래 [IsolationScope](#isolationscope--사용자와-레플리카-간-버킷-분리) 참고) | `USER` |
| `anonymousUserId(String)` | `userId`가 없을 때 사용할 대체 식별자 | `"_default"` |
| `addSharedPrefix(String)` | 워크스페이스 상대 경로 접두사를 추가로 KV에 라우팅(예: `"prompts/"`, `"configs/"`) | 없음 |
| `workspaceIndex(WorkspaceIndex)` | 원격 ls/glob/grep를 가속하는 SQLite 인덱스 | 없음(없으면 전체 스토어 스캔으로 대체) |

#### 내장 라우팅 규칙

프레임워크는 다음 경로들을 자동으로 공유 KV로 라우팅하며, 키 충돌을 막기 위해 각각 별도의 네임스페이스 세그먼트를 사용한다.

| 경로 | KV 네임스페이스 세그먼트 |
|------|---------------------|
| `AGENTS.md`, `MEMORY.md`, `tools.json` | `root` |
| `memory/` | `memory` |
| `skills/` | `skills` |
| `subagents/` | `subagents` |
| `knowledge/` | `knowledge` |
| `agents/<agentId>/sessions/` | `sessions` |
| `agents/<agentId>/tasks/` | `tasks` |

위 표에 없는 경로는 셸이 없는 로컬 `LocalFilesystem`으로 그대로 전달된다.

#### 예시: 멀티 레플리카 고객 서비스 에이전트

세 개의 파드가 각각 `HarnessAgent`를 실행하며, 하나의 Redis를 `BaseStore`로 공유한다.

```java
DistributedStore store = RedisDistributedStore.fromJedis(
        new JedisPooled("redis://shared-redis:6379"));

HarnessAgent agent = HarnessAgent.builder()
    .name("customer-service")
    .model(model)
    .workspace(Paths.get("/opt/agent/workspace"))
    .distributedStore(store)                  // stateStore + baseStore를 한 번에 설정
    .filesystem(new RemoteFilesystemSpec()
        .isolationScope(IsolationScope.USER)      // 사용자당 하나의 네임스페이스
        .anonymousUserId("anonymous"))            // 인증되지 않은 호출자를 위한 대체값
    .build();
```

- 각 파드의 로컬 `AGENTS.md` / `knowledge/` / `skills/`는 읽기 전용 템플릿으로 작동한다(git으로 동기화됨);
- 런타임 산출물(`MEMORY.md`, `memory/`, 대화 로그)은 자동으로 Redis에 저장되며 — 어떤 파드에서 읽어도 최신 상태를 볼 수 있다;
- Alice의 메모리는 KV 키 `agents/customer-service/users/alice/memory/...` 아래에 위치한다.

이 모드는 **셸을 제공하지 않는다** — 의도적인 설계다: 셸이 필요하다면 모드 2(샌드박스) 또는 모드 3(로컬)을 사용하라.

#### 사용 가능한 `BaseStore` 구현체

| 구현체 | 설명 |
|---------------|-------------|
| `RedisStore` | Jedis 기반, 저지연 고동시성용 | `agentscope-extensions-redis` |
| `JdbcStore` | JDBC 기반, MySQL / PostgreSQL / H2용 | `agentscope-extensions-mysql` |
| `InMemoryStore` | 인메모리, 테스트용 | `agentscope-harness` |

---

### 모드 2: 샌드박스(`SandboxFilesystemSpec` 계열)

"코드가 신뢰할 수 없는 작업을 수행할 수 있다" 또는 "운영 호스트로부터 격리해야 한다"는 요구에 맞는 모드다. 모든 파일 작업과 셸 명령은 샌드박스로 전달되며, 호스트는 전혀 영향을 받지 않는다.

#### Docker 샌드박스

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("sandbox-agent")
    .model(model)
    .workspace(workspace)
    .filesystem(new DockerFilesystemSpec()
        .image("ubuntu:24.04")
        .isolationScope(IsolationScope.SESSION)
        .memorySizeBytes(512 * 1024 * 1024L)   // 메모리 제한 512 MB
        .cpuCount(2L)
        .network("host")
        .exposedPorts(8080, 3000)
        .environment(Map.of("NODE_ENV", "development"))
        .snapshotSpec(new LocalSnapshotSpec("/data/snapshots")))
    .build();
```

`DockerFilesystemSpec` — 전체 옵션:

| 메서드 | 설명 | 기본값 |
|--------|-------------|---------|
| `image(String)` | Docker 이미지 | 필수 |
| `isolationScope(IsolationScope)` | 격리 차원 | `SESSION` |
| `memorySizeBytes(Long)` | 컨테이너 메모리 제한 | Docker 기본값 |
| `cpuCount(Long)` | CPU 제한 | Docker 기본값 |
| `network(String)` | Docker 네트워크 | Docker 기본값 |
| `exposedPorts(int...)` | 노출할 포트 | 없음 |
| `environment(Map)` | 컨테이너 환경 변수 | 없음 |
| `workspaceRoot(String)` | 컨테이너 내부의 워크스페이스 마운트 지점 | `/workspace` |
| `additionalRunArgs(String...)` | 추가 `docker run` 인자 | 없음 |
| `snapshotSpec(SandboxSnapshotSpec)` | 스냅샷 전략 | `NoopSnapshotSpec`(스냅샷 없음) |
| `workspaceSpec(WorkspaceSpec)` | 워크스페이스 마운트 규칙 | 기본값 |
| `executionGuard(SandboxExecutionGuard)` | AGENT / GLOBAL 스코프를 위한 동시성 가드 | 없음 |
| `workspaceProjectionEnabled(boolean)` | 호스트 → 샌드박스 정적 자산 투영 활성화 | `true` |
| `workspaceProjectionRoots(List)` | 투영에 포함할 루트 경로 | `AGENTS.md`, `skills`, `subagents`, `knowledge`, `.skills-cache` |

#### Kubernetes 샌드박스(agent-sandbox)

Kubernetes 스토어는 전적으로 [agent-sandbox](https://github.com/kubernetes-sigs/agent-sandbox)를 기반으로 한다. 샌드박스 파드는 클러스터 내 agent-sandbox 컨트롤러가 관리하며, 이미지·리소스·PVC는 모두 `SandboxTemplate` / `SandboxWarmPool`에서 클러스터 쪽에 선언된다(Java 코드에서 설정하지 않는다). Java 쪽은 `SandboxClaim`을 통해 웜 풀에서 인스턴스를 클레임할 뿐이다. 사용하기 전에 agent-sandbox 컨트롤러를 설치하고 템플릿과 웜 풀을 생성해야 한다.

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("k8s-agent")
    .model(model)
    .workspace(workspace)
    .filesystem(new KubernetesFilesystemSpec()
        .namespace("agents")
        .warmPoolName("agent-pool")        // SandboxWarmPool 이름
        .isolationScope(IsolationScope.USER))
    .build();
```

`KubernetesFilesystemSpec`의 주요 옵션:

| 메서드 | 설명 | 기본값 |
|--------|-------------|---------|
| `namespace(String)` | SandboxClaim의 네임스페이스 | `default` |
| `warmPoolName(String)` | `SandboxWarmPool` 이름 | 필수 |
| `workspaceRoot(String)` | 샌드박스 내부의 워크스페이스 루트; **템플릿에 선언된 PVC 마운트 위치와 일치해야 함** | `/workspace` |
| `fileApiBaseDir(String)` | 런타임 파일 API 기본 디렉터리; `workspaceRoot`와 일치해야 하며, 비워두면 base64-over-exec 전송으로 대체됨 | `/workspace` |
| `apiUrl(String)` | 런타임 API에 직접 접근하는 URL(설정 시 우선) | 없음 |
| `gatewayName(String)` / `gatewayNamespace(String)` / `gatewayScheme(String)` | Gateway API를 통해 샌드박스에 접근 | 없음 |
| `serverPort(int)` | 런타임 HTTP API 포트 | `8888` |
| `kubernetesClient(KubernetesClient)` | 커스텀 fabric8 클라이언트 | kubeconfig 자동 로드 |
| `snapshotSpec(SandboxSnapshotSpec)` | 스냅샷 전략(PVC와의 트레이드오프는 샌드박스 페이지 참고) | `NoopSnapshotSpec` |

`apiUrl`과 `gateway*` 모두 설정하지 않으면 `kubectl port-forward`를 이용한 로컬 터널이 사용된다(개발용으로 적합). 런타임 이미지는 [런타임 이미지 계약](./sandbox.md#런타임-이미지-계약)을 충족해야 한다. **워크스페이스 영속성은 템플릿에 설정된 PVC에 달려 있다** — [샌드박스 - Kubernetes 상태 영속성](./sandbox.md#kubernetes-상태-영속성-pvc가-첫-번째-계층이다)을 참고하라.

#### E2B 샌드박스

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("e2b-agent")
    .model(model)
    .workspace(workspace)
    .filesystem(new E2bFilesystemSpec()
        .apiKey("${E2B_API_KEY}")
        .templateId("my-template")
        .sandboxTimeoutSeconds(300)
        .isolationScope(IsolationScope.SESSION))
    .build();
```

#### Daytona 샌드박스

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("daytona-agent")
    .model(model)
    .workspace(workspace)
    .filesystem(new DaytonaFilesystemSpec()
        .apiKey("${DAYTONA_API_KEY}")
        .controlPlaneBaseUrl("https://api.daytona.io")
        .image("python:3.12-slim")
        .cpu(2)
        .memory(4)        // GiB
        .disk(10)         // GiB
        .isolationScope(IsolationScope.USER))
    .build();
```

#### AgentRun 샌드박스(Alibaba Cloud)

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("agentrun-agent")
    .model(model)
    .workspace(workspace)
    .filesystem(new AgentRunFilesystemSpec()
        .apiKey("${AGENTRUN_API_KEY}")
        .accountId("your-account-id")
        .region("cn-hangzhou")
        .templateName("python3.12")
        .sandboxIdleTimeoutSeconds(600)
        .isolationScope(IsolationScope.USER))
    .build();
```

#### `SandboxFilesystemSpec`으로부터 상속되는 공통 옵션

| 메서드 | 설명 | 기본값 |
|--------|-------------|---------|
| `isolationScope(IsolationScope)` | 격리 차원 | 스토어별로 다름(보통 `SESSION`) |
| `snapshotSpec(SandboxSnapshotSpec)` | 스냅샷 전략 | `NoopSnapshotSpec` |
| `executionGuard(SandboxExecutionGuard)` | AGENT/GLOBAL 스코프를 위한 동시성 직렬화 가드 | 없음 |
| `workspaceProjectionEnabled(boolean)` | 호스트에서 샌드박스로 정적 자산 투영 | `true` |
| `workspaceProjectionRoots(List)` | 투영에 포함할 루트 경로 | `AGENTS.md`, `skills`, `subagents`, `knowledge`, `.skills-cache` |

#### 스냅샷 전략

스냅샷을 사용하면 다음 `call()`이 이전 샌드박스 상태(설치된 의존성, 생성된 파일 등)를 복원할 수 있다.

| 구현체 | 설명 |
|---------------|-------------|
| `NoopSnapshotSpec` | 스냅샷 없음(기본값) |
| `LocalSnapshotSpec(Path)` | 스냅샷을 호스트 로컬 디스크에 저장 |
| `RedisSnapshotSpec` | 스냅샷을 Redis에 저장 |
| `OssSnapshotSpec` | 스냅샷을 오브젝트 스토리지(Alibaba Cloud OSS)에 저장 |
| `RemoteSnapshotSpec` | 스냅샷을 `BaseStore`에 저장 |

#### 예시: 코딩 어시스턴트(Docker + 로컬 스냅샷)

```java
HarnessAgent codingAgent = HarnessAgent.builder()
    .name("coder")
    .model(model)
    .workspace(Paths.get(".agentscope/workspace"))
    .filesystem(new DockerFilesystemSpec()
        .image("node:20-slim")
        .isolationScope(IsolationScope.USER)
        .memorySizeBytes(1024 * 1024 * 1024L)
        .snapshotSpec(new LocalSnapshotSpec("/data/sandbox-snapshots")))
    .distributedStore(store)
    .build();

// Alice의 첫 호출: 샌드박스 안에서 npm install 실행 후 스냅샷 저장
RuntimeContext rc = RuntimeContext.builder()
    .userId("alice")
    .sessionId("dev-session-1")
    .build();
agent.call(Msg.user("npm install && npm test"), rc).block();

// Alice의 두 번째 호출: 스냅샷이 복원되어 node_modules가 그대로 남아 있음
agent.call(Msg.user("npm run build"), rc).block();
```

#### 워크스페이스 투영

샌드박스가 시작되면, 프레임워크는 워크스페이스의 "정적 자산"을 tar로 묶어 컨테이너 내부의 `/workspace`에 하이드레이션한다. 여기에는 다음이 포함된다.

- `AGENTS.md`(페르소나 파일)
- `skills/`(스킬 디렉터리)
- `subagents/`(서브에이전트 선언)
- `knowledge/`(지식 베이스)
- `.skills-cache/`(스킬 캐시)

투영은 SHA-256으로 내용을 비교하며, 변경되지 않은 파일은 하이드레이션을 건너뛴다. 어떤 경로를 포함할지는 `workspaceProjectionRoots(List)`로 커스터마이즈할 수 있고, `workspaceProjectionEnabled(false)`로 완전히 비활성화할 수도 있다.

---

### 모드 3: 로컬 + shell(기본값)

`filesystem(...)`을 호출하지 않을 때 얻게 되는 것: 워크스페이스는 `${cwd}/.agentscope/workspace/`에 위치하며, 셸은 호스트에서 실행된다.

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("local-agent")
    .model(model)
    .workspace(workspace)
    // .filesystem(...)을 생략하면 로컬 + shell
    .build();
```

#### 전체 설정 옵션

```java
.filesystem(new LocalFilesystemSpec()
    .executeTimeoutSeconds(120)       // 셸 명령 타임아웃
    .maxOutputBytes(100_000)          // 명령당 최대 출력 바이트 수
    .env("MY_VAR", "value")          // 추가 환경 변수
    .inheritEnv(true)                // 부모 프로세스 환경을 상속
    .mode(LocalFsMode.ROOTED)        // 경로 정책
    .project(Paths.get("/my/project")) // 프로젝트 루트(셸 cwd + 오버레이 하위 계층)
    .addRoot(Paths.get("/extra/dir"))) // 추가로 허용할 디렉터리
```

| 메서드 | 설명 | 기본값 |
|--------|-------------|---------|
| `executeTimeoutSeconds(int)` | 셸 명령 타임아웃(초) | 120 |
| `maxOutputBytes(int)` | 명령당 캡처할 최대 출력 바이트 수 | 100,000 |
| `env(String, String)` | 셸 환경 변수 추가 | 없음 |
| `inheritEnv(boolean)` | 부모 프로세스 환경을 상속 | `false` |
| `mode(LocalFsMode)` | 경로 해석 정책 | `ROOTED` |
| `project(Path)` | 프로젝트 루트 디렉터리(오버레이 하위 계층 + 셸 cwd) | `System.getProperty("user.dir")` |
| `addRoot(Path)` | 에이전트가 접근할 수 있는 추가 호스트 디렉터리 | 없음 |
| `additionalRoots(Collection)` | 추가 디렉터리를 일괄 설정 | 없음 |
| `projectWritable(boolean)` | 워크스페이스가 아닌 쓰기를 워크스페이스 대신 프로젝트 디렉터리로 라우팅 | `false` |

#### 경로 해석 정책(`LocalFsMode`)

| 모드 | 동작 |
|------|----------|
| `ROOTED`(기본값) | 절대 경로는 `workspace` + `project` + `additionalRoots` 하위에서만 허용; `..` 트래버설은 거부됨 |
| `SANDBOXED` | 모든 경로가 워크스페이스 루트를 기준으로 고정; 절대 경로와 `..` 모두 거부됨 |
| `UNRESTRICTED` | 절대 경로가 그대로 통과함. 테스트 또는 완전히 신뢰할 수 있는 환경 전용 |

#### 오버레이 파일 시스템

로컬 모드는 실제로 `OverlayFilesystem`을 생성한다.

- **상위(읽기-쓰기)**: `LocalFilesystemWithShell`, `workspace`를 루트로 하며 셸을 제공;
- **하위(읽기 전용)**: `LocalFilesystem`, `project`를 루트로 함.

읽기는 먼저 워크스페이스를 확인하고, 없으면 프로젝트로 폴백한다(카피-온-라이트 시맨틱). 셸의 `pwd`는 프로젝트 디렉터리이므로, `ls`는 프로젝트 파일을 보여준다.

#### 프로젝트 쓰기 가능 모드(`projectWritable`)

기본적으로 모든 쓰기는 워크스페이스에 저장된다 — 읽기/분석 시나리오에는 문제없지만, 에이전트의 임무가 **코드를 생성**하는 것(예: 마이크로서비스 스캐폴딩)이라면 파일이 프로젝트 디렉터리가 아니라 `.agentscope/workspace/`에 쌓이게 된다.

`projectWritable(true)`를 활성화하면 프레임워크가 경로에 따라 쓰기를 라우팅한다.

| 경로 유형 | 쓰기 대상 | 예시 |
|-----------|-----------|----------|
| 워크스페이스 메타데이터 | 워크스페이스 | `MEMORY.md`, `memory/`, `agents/`, `skills/`, `knowledge/`, `plans/`, `subagents/`, `rules/`, `tools.json` |
| 그 외 전부 | 프로젝트 디렉터리 | `src/main/java/App.java`, `pom.xml`, `README.md`, `docker-compose.yml` |

```java
.filesystem(new LocalFilesystemSpec()
    .projectWritable(true)      // 코드 파일은 프로젝트 디렉터리로 이동
    .inheritEnv(true))
```

읽기 동작은 변하지 않는다 — 워크스페이스 우선, 프로젝트 폴백.

#### 예시: 로컬 개발 어시스턴트

```java
HarnessAgent devHelper = HarnessAgent.builder()
    .name("dev-helper")
    .model(model)
    .workspace(Paths.get(".agentscope/workspace"))
    .filesystem(new LocalFilesystemSpec()
        .project(Paths.get("/Users/alice/my-project"))
        .addRoot(Paths.get("/Users/alice/.config"))
        .mode(LocalFsMode.ROOTED)
        .inheritEnv(true)
        .executeTimeoutSeconds(300))
    .build();
```

에이전트는 `/Users/alice/my-project`와 `/Users/alice/.config` 아래의 파일을 읽고 쓸 수 있으며, `/Users/alice/my-project`를 cwd로 삼아 셸 명령을 실행할 수 있지만, 다른 호스트 디렉터리에는 접근할 수 없다.

---

## IsolationScope — 사용자와 레플리카 간 버킷 분리

모드 1(공유 스토어)과 모드 2(샌드박스) 모두 **누가 누구와 상태를 공유하는지**를 결정하기 위해 동일한 `IsolationScope` 개념을 사용한다.

| Scope | 의미 | 네임스페이스 키 | 일반적인 용도 |
|-------|---------|--------------|-------------|
| `SESSION` | 각 sessionId가 독립적 | `agents/<agentId>/sessions/<sessionId>/...` | 멀티유저 SaaS, 각 대화가 완전히 격리됨 |
| `USER`(기본값) | 동일한 `userId`가 세션 간에 공유 | `agents/<agentId>/users/<userId>/...` | 같은 사용자의 여러 세션이 장기 메모리를 공유 |
| `AGENT` | 이 에이전트의 모든 사용자/세션이 공유 | `agents/<agentId>/shared/...` | 공개 지식 베이스형 에이전트 |
| `GLOBAL` | 모든 것을 위한 하나의 공유 슬롯 | `global/...` | 신중하게 사용할 것 |

### Scope별 폴백 규칙

- `USER` scope에서 `RuntimeContext.userId`가 없으면 `SESSION`으로 폴백한다(sessionId로 격리).
- `SESSION` scope에서 `RuntimeContext.sessionId`가 없으면 상태 조회를 건너뛰고 새로운 환경을 생성한다.
- `AGENT` scope는 (빌드 시점에 고정된) 에이전트 이름을 네임스페이스 키로 사용한다 — 컨텍스트 필드 누락으로 인해 성능이 저하되는 일이 없다.

### 샌드박스 모드에서의 동시성

샌드박스 모드에서 `IsolationScope`는 **순차 재사용** 방식의 공유이며, 살아있는 인스턴스를 동시에 공유하는 것이 아니다. 같은 scope 키로 들어오는 동시 호출은 각각 자신만의 실행 중인 컨테이너를 갖게 되며, 호출이 끝나면 마지막에 쓰인 스냅샷이 최종 상태가 된다. 여러 사용자가 상태를 공유하는 `AGENT` / `GLOBAL` scope의 경우, 동시 접근을 직렬화하려면 `executionGuard(SandboxExecutionGuard)`를 사용하라.

### 예시: 다양한 비즈니스 요구에 맞는 scope 조합

**시나리오 1: 세션 간 설치된 의존성을 보존하는 사용자별 코딩 샌드박스**

```java
.filesystem(new DockerFilesystemSpec()
    .image("python:3.12")
    .isolationScope(IsolationScope.USER)       // Alice의 모든 세션이 하나의 스냅샷을 공유
    .snapshotSpec(new LocalSnapshotSpec("/snapshots")))
```

**시나리오 2: 대화별 일회용 샌드박스**

```java
.filesystem(new DockerFilesystemSpec()
    .image("ubuntu:24.04")
    .isolationScope(IsolationScope.SESSION))   // sessionId마다 독립
```

**시나리오 3: 지식을 공유하는 고객 서비스 에이전트(공유 스토어)**

```java
.distributedStore(store)
    .filesystem(new RemoteFilesystemSpec()
    .isolationScope(IsolationScope.AGENT))     // 모든 사용자와 세션이 메모리/스킬을 공유
```

---

## 멀티 유저 격리가 동작하는 방식

`RuntimeContext.userId`는 멀티유저 분리의 핵심 키다.

| 모드 | userId의 역할 | 물리적 표현 |
|------|-----------------|----------------------|
| 로컬 | 사용자 수준 파일은 `workspace/<userId>/...`에 위치, 예를 들어 `workspace/alice/skills/code-reviewer/SKILL.md`는 Alice에게만 적용됨 | 경로 접두사 |
| 공유 스토어 | KV 네임스페이스 접두사 `agents/<agentId>/users/<userId>/...`로 사용 | KV 키 접두사 |
| 샌드박스 | 샌드박스 스냅샷 슬롯 키로 사용됨(`IsolationScope.USER`와 짝을 이룸) | 샌드박스 인스턴스 격리 |

`userId`가 없으면 싱글 테넌트 기본값이 적용되어 모두가 하나의 루트를 공유한다.

### 런타임 데이터 vs 정적 자산

**런타임 데이터**(대화 로그, 작업, 메모리)는 `IsolationScope` / `userId`를 따라 자동으로 격리된다.

**정적 자산**(`AGENTS.md`, `tools.json`, `knowledge/`)은 모든 사용자에게 공유되며 userId에 의해 자동으로 분할되지 **않는다**. 구분은 오직 사용자별 오버라이드 디렉터리를 통해서만 가능하다.

```
workspace/
├── skills/code-reviewer/SKILL.md     ← 공유(모두에게 보임)
└── alice/
    └── skills/code-reviewer/SKILL.md ← Alice에게만 적용; 공유 버전을 오버라이드
```

---

## 각 모드에서 스킬과 도구가 동작하는 방식

### 스킬

`DynamicSkillMiddleware`는 매 추론 턴 전에 리포지토리 목록에서 스킬을 병합하여 시스템 프롬프트에 렌더링한다. 스킬 파일 로딩은 `AbstractFilesystem` 인터페이스를 거치므로 세 모드 모두에서 투명하게 동작한다.

| 모드 | 스킬 로딩 방식 |
|------|----------------|
| 로컬 | 로컬 디스크의 `workspace/skills/`에서 직접 읽음; 사용자별 오버라이드는 `<userId>/skills/` |
| 공유 스토어 | `skills/`가 KV로 라우팅됨 — 원격을 먼저 확인하고, 없으면 로컬 템플릿으로 폴백. 관리자의 수정 사항은 다음 추론 턴부터 모든 레플리카에 반영됨 |
| 샌드박스 | 호스트의 `skills/`가 시작 시 워크스페이스 투영을 통해 샌드박스의 `/workspace/skills/`로 주입됨 |

4단계 우선순위는 변하지 않는다(낮음 → 높음): `projectGlobalSkillsDir` → `skillRepository` → `workspace/skills/` → `<userId>/skills/`.

### 파일 도구(read_file / write_file / edit_file / ...)

모든 파일 도구는 `AbstractFilesystem` 인터페이스를 통해 호출되며, 매 작업마다 현재 `RuntimeContext`를 전달한다. 실제 읽기/쓰기 위치는 파일 시스템 구현체가 결정한다. 에이전트 코드는 이 모드 차이를 전혀 알지 못한다.

| 모드 | 읽기/쓰기 동작 |
|------|-------------------|
| 로컬 | `OverlayFilesystem`: 쓰기는 워크스페이스(상위)에 저장됨; 읽기는 먼저 워크스페이스를 확인한 뒤 프로젝트(하위)로 폴백. `projectWritable(true)`이면 메타데이터가 아닌 쓰기는 프로젝트 디렉터리로 라우팅됨 |
| 공유 스토어 | `CompositeFilesystem`: 라우팅된 경로는 KV 오버레이(원격 상위 + 로컬 템플릿 하위)를 거치고, 나머지는 로컬로 처리됨 |
| 샌드박스 | 모든 파일 작업이 샌드박스 컨테이너로 전달됨 |

### 셸 실행(execute)

| 모드 | 셸 사용 가능? | 실행 위치 |
|------|-----------------|--------------|
| 로컬 | 예 | 호스트 `sh -c`, cwd = `project` 디렉터리 |
| 공유 스토어 | 아니오 | 셸이 제공되지 않음 |
| 샌드박스 | 예 | 샌드박스 컨테이너 내부 |

### tools.json / MCP 서버

`tools.json`은 `build()` 시점에 워크스페이스에서 한 번만 읽히며(`WorkspaceManager`를 통해, 2계층 읽기 지원), MCP 서버를 등록하고 allow/deny 필터를 적용한다. **동작은 세 모드 모두에서 동일하다** — 설정은 빌드 시점에 읽히며, 런타임 파일 시스템 모드의 영향을 받지 않는다.

공유 스토어 모드에서도 `tools.json`은 "원격 상위, 로컬 템플릿 하위" 오버레이를 따른다. 관리 콘솔을 통해 `tools.json`을 수정하면 **적용을 위해 에이전트를 다시 빌드해야 한다**(MCP 서버 등록은 일회성 작업이다).

---

## 워크스페이스의 2계층 읽기

`AGENTS.md`, `MEMORY.md`, `KNOWLEDGE.md`와 같은 핵심 파일은 읽을 때 "2계층 폴백"을 사용한다. 먼저 설정된 파일 시스템에서 찾아보고, 찾지 못하면 로컬 디스크로 폴백한다. 이는 **모드 1(공유 스토어)의 "템플릿 파일"**에 유용하다. 첫 번째 레플리카는 로컬에 템플릿 `AGENTS.md`를 갖고 있으므로 즉시 동작하며, 이후의 레플리카들은 공유 스토어에서 최신 버전을 읽는다.

쓰기는 항상 설정된 파일 시스템 스토어를 통해 이루어진다.

## 완전한 직접 관리: `abstractFilesystem(...)`

세 가지 모드 중 어느 것도 맞지 않는다면, 완전히 자체 구현한 파일 시스템을 전달할 수 있다.

```java
HarnessAgent.builder()
    ...
    .abstractFilesystem(myCustomFilesystem)   // filesystem(...)과 상호 배타적
    .build();
```

보통은 필요하지 않다 — 세 가지 모드가 사용 사례의 약 95%를 커버한다.

## 관련 문서

- [샌드박스](./sandbox.md) — 모드 2의 런타임 세부 사항(컨테이너 생명주기, 스냅샷 복구 체인)
- [워크스페이스](./workspace.md) — 디렉터리 레이아웃, 로딩 메커니즘, 2계층 읽기의 "하위 계층"
- [컨텍스트](../building-blocks/context.md) — `AgentState`와 `AgentStateStore`, `(userId, sessionId)` 어드레싱
- [스킬](./skill.md) — 4단계 합성, 자가 학습 루프, `<available_skills>` 블록
- [도구](../building-blocks/tool.md) — `read_file` / `write_file` / `execute` 파라미터
- [아키텍처](./architecture.md) — 파일 시스템과 런타임 컨텍스트가 협력하는 방식
