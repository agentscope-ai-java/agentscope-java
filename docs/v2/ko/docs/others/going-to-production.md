---
title: "프로덕션으로 가기"
description: "단일 노드 프로토타입에서 다중 레플리카 배포로: Agent State Store, Filesystem, Skill, Sandbox, Snapshot, Observability를 위한 컴포넌트 선택과 설정"
---

> 노트북에서 `HarnessAgent`를 실행하는 것은 쉽습니다. 이를 프로덕션에 배포하는 것은 완전히 다른 이야기입니다 — 레플리카는 세션을 공유해야 하고, 사용자는 서로 격리되어야 하며, 신뢰할 수 없는 코드는 샌드박스에 갇혀야 하고, pod는 재시작 후에도 대화 도중부터 재개할 수 있어야 합니다. 이 페이지는 **단일 노드와 분산 프로덕션 사이에서 바뀌는 것**만 다룹니다: 어떤 컴포넌트를 교체해야 하는지, 무엇으로 교체해야 하는지, 그리고 무언가를 놓쳤을 때 빌더가 왜 `IllegalStateException`을 던지는지를 설명합니다.

**프로덕션으로 가는 가장 빠른 경로**: `DistributedStore`를 사용해 모든 분산 컴포넌트를 한 번에 설정하세요.

```java
DistributedStore store = RedisDistributedStore.fromJedis(jedis);
// or MysqlDistributedStore.create(dataSource);
// or OssDistributedStore.create(ossClient, bucket, prefix);

HarnessAgent.builder()
    .distributedStore(store)
    .filesystem(...)  // choose your workspace mode
    .build();
```

혼합 스토어(예: 상태에는 MySQL, 샌드박스 락에는 Redis)도 지원됩니다.

```java
DistributedStore store = DistributedStore.builder()
    .agentStateStore(MysqlDistributedStore.create(ds).agentStateStore())
    .baseStore(MysqlDistributedStore.create(ds).baseStore())
    .sandboxSnapshotSpec(RedisDistributedStore.fromJedis(jedis).sandboxSnapshotSpec())
    .sandboxExecutionGuard(RedisDistributedStore.fromJedis(jedis).sandboxExecutionGuard())
    .build();
```

### 대안: aistio 호스팅 스토어

aistio 컨트롤 플레인을 운영 중이라면, BaseStore / 샌드박스 락 & 스냅샷 / MessageBus / AsyncToolRegistry / **TaskRepository** / 선택적 **SessionTurnGate**를 호스팅할 수 있습니다. 여전히 **하나**의 `AgentStateStore`(Redis/MySQL/Postgres/OSS)는 직접 제공해야 합니다; 코어는 `getVersioned` / `saveIfVersion`을 제공하지만, 스토리지 자체는 컨트롤 플레인 바깥에 남습니다.

```java
ControlPlaneStores cp = ControlPlaneStores.fromEnv();
HarnessAgent.builder()
    .distributedStore(cp.withAgentStateStore(redis.agentStateStore()))
    .filesystem(new RemoteFilesystemSpec().isolationScope(IsolationScope.USER))
    .build();
```

컨트롤 플레인에서 `--enable-hosted-store`로 활성화합니다(Postgres 권장). `withAgentStateStore`는 호스팅된 TaskRepository를 포함합니다; **SandboxFilesystem 모드에서의 서브에이전트 백그라운드 작업**은 이 경로가 필요합니다. Redis/Postgres/MySQL/InMemory `AgentStateStore` 백엔드는 버저닝 CAS를 지원하며, 그 외는 LWW로 남습니다. Turn gate와 `ConflictPolicy.FAIL`은 다중 레플리카에서 중복 턴을 줄이기 위한 선택 사항입니다; 정확성은 CAS에서 나옵니다. 인증은 요청 본문의 tenant를 포함하는 공유 내부 토큰입니다 — 하나의 CP 위에서 서로를 신뢰하지 않는 멀티 테넌트 에이전트에는 적합하지 않습니다. `queueDrain`은 파괴적입니다(읽으면 즉시 ack). [분산 스토리지 — aistio 호스팅 스토어](../../integration/distributed/index.md#aistio-호스팅-스토어)를 참고하세요.

## 한눈에 보기: 단일 노드 기본값 vs. 분산 프로덕션

| 차원 | 단일 노드 기본값(개발 / 데모) | 분산 프로덕션 교체 |
|-----------|----------------------------------|-----------------------------|
| **한 줄 설정** | 필요 없음 | **`.distributedStore(RedisDistributedStore.fromJedis(jedis))`** |
| `AgentStateStore` | `JsonFileAgentStateStore`(로컬 JSON) | `distributedStore`에 의해 자동 연결됨 |
| Filesystem | `LocalFilesystemSpec`(호출 불필요) | `RemoteFilesystemSpec` 또는 `SandboxFilesystemSpec`(`baseStore`가 store로부터 자동 주입됨) |
| 샌드박스 스냅샷 | `NoopSnapshotSpec` / `LocalSnapshotSpec` | `distributedStore`에 의해 자동 연결됨 |
| 샌드박스 실행 직렬화 | 프로세스 내에서는 필요 없음 | `distributedStore`에 의해 자동 연결됨 |
| 스킬 소스 | `workspace/skills/` | `GitSkillRepository` / `MysqlSkillRepository` / `NacosSkillRepository` |
| Observability | 기본적으로 트레이싱 없음 | `OtelTracingMiddleware` + OpenTelemetry SDK |

### DistributedStore 기능 매트릭스

| 기능 | Redis(`agentscope-extensions-redis`) | OSS(`agentscope-extensions-oss`) | MySQL(`agentscope-extensions-mysql`) |
|------------|:-----:|:---:|:-----:|
| `AgentStateStore` | `RedisAgentStateStore` | `OssAgentStateStore` | `MysqlAgentStateStore` |
| `BaseStore` | `RedisStore` | `OssBaseStore` | `JdbcStore` |
| `SandboxSnapshotSpec` | `RedisSnapshotSpec` | `OssSnapshotSpec` | `JdbcSnapshotSpec` |
| `SandboxExecutionGuard` | `RedisSandboxExecutionGuard` | —(오브젝트 스토리지는 락을 지원할 수 없음) | `JdbcSandboxExecutionGuard` |

각 컴포넌트는 서로 다른 프로덕션 문제를 해결합니다.

- `AgentStateStore`: 대화 이력, 압축 요약, 권한 규칙, Plan Mode 상태, 도구 상태를 포함한 에이전트의 런타임 세션 상태를 영속화합니다. 이것이 다른 레플리카나 재시작된 프로세스가 동일한 `(userId, sessionId)`를 이어갈 수 있게 해주는 요소입니다.
- `BaseStore`: `RemoteFilesystemSpec`을 위한 공유 KV 기반 워크스페이스 스토리지를 제공하며, `MEMORY.md`, `memory/`, `skills/`, `sessions/` 같은 경로를 담습니다. 다중 레플리카 배포에서는 서로 다른 pod가 동일한 장기 메모리와 공유 파일을 볼 수 있게 해줍니다.
- `SandboxSnapshotSpec`: 샌드박스 워크스페이스 스냅샷을 영속화합니다. 샌드박스 컨테이너가 파괴되거나, pod가 재시작되거나, 다음 요청이 새 노드에 도착했을 때, `pip install` 결과물, 생성된 파일, 임시 프로젝트 상태를 잃는 대신 이전 워크스페이스를 복원합니다.
- `SandboxExecutionGuard`: 여러 노드에 걸쳐 동일한 샌드박스 슬롯에 대한 명령 실행을 직렬화합니다. `AGENT`나 `GLOBAL` 같은 공유 scope에서는 여러 레플리카가 동시에 동일한 샌드박스에 대해 `exec`를 시도할 수 있습니다; 가드는 Redis/MySQL 락을 사용해 동시 워크스페이스 쓰기와 샌드박스 시작/중지 경쟁을 방지합니다.

> OSS는 `SandboxExecutionGuard`를 제공하지 않습니다 — 오브젝트 스토리지는 분산 락에 적합하지 않습니다. 샌드박스 동시성 제어가 필요한 OSS 사용자는 `DistributedStore.builder()`를 통해 Redis 가드를 혼합할 수 있습니다.

**핵심 검증 체인:**
- `stateStore(...)`나 `distributedStore(...)` 없이 `filesystem(RemoteFilesystemSpec)`만 설정 → `build()`가 `IllegalStateException`을 던집니다.
- 로컬 `AgentStateStore`와 함께 `filesystem(SandboxFilesystemSpec)`을 설정 → `build()`가 **경고**를 로그로 남깁니다; 프로덕션에서는 항상 `distributedStore`를 제공하세요.

## 1. State store: 먼저 `AgentState`를 어딘가 견고한 곳에 두기

> **권장**: 한 줄 설정을 위해 `distributedStore(...)`를 사용하세요. 아래의 상세 표는 `AgentStateStore`를 개별적으로 제어해야 하는 고급 사용자를 위한 것입니다.

`AgentState`(대화 컨텍스트, 압축 요약, 권한 규칙, Plan Mode 상태, 도구 상태)는 [`AgentStateStore`](../../integration/session/index.md)를 통해서만 프로세스 간에 살아남습니다.

| 구현체 | 모듈 | 사용 시점 |
|----------------|--------|-------------|
| `InMemoryAgentStateStore` | `agentscope-core` | 단위 테스트; 프로세스 종료 시 모든 것이 사라짐 |
| `JsonFileAgentStateStore` | `agentscope-core` | 단일 머신 개발; `(userId, sessionId)`당 하나의 디렉터리. **HarnessAgent 기본값**, `~/.agentscope/state/<agentId>/`에 루트를 둠; **단일 머신** |
| `RedisAgentStateStore` | `agentscope-extensions-redis` | **다중 레플리카 프로덕션 기본값**; Jedis / Lettuce / Redisson(Standalone / Cluster / Sentinel) 지원 |
| `MysqlAgentStateStore` | `agentscope-extensions-mysql` | 상태가 관계형 스토어에 있어야 할 때(감사 / 리포팅 / 조인) |

**세 클라이언트 어댑터 중 어느 것이든 사용하는 Redis**는 `RedisAgentStateStore.builder()`를 통합니다.

```java
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import redis.clients.jedis.JedisPooled;

// Jedis Standalone
AgentStateStore stateStore = RedisAgentStateStore.builder()
        .jedisClient(new JedisPooled("redis://localhost:6379"))
        .keyPrefix("myapp:session:")
        .build();

// Lettuce Cluster (better for write-heavy)
// .lettuceClusterClient(RedisClusterClient.create(...))

// Redisson (if you already use Redisson elsewhere)
// .redissonClient(redisson)
```

**테넌트별 격리.** 단순한 `sessionId`만으로는 단일 테넌트만 커버됩니다. 프로덕션에서는 각 호출의 `RuntimeContext`에 `userId`와 `sessionId`를 모두 설정해 멀티 테넌트 호출이 서로 교차해서 읽지 못하게 하세요 — store는 각 슬롯을 `(userId, sessionId)` 쌍으로 주소를 지정합니다(`RedisAgentStateStore`는 `userId`를 Redis 키에 접어 넣고; `MysqlAgentStateStore`는 이를 기본 키에 접어 넣습니다). 다른 차원(테넌트, 에이전트)은 직접 `sessionId` 문자열에 합성하세요.

```java
agent.call(msg, RuntimeContext.builder()
        .userId(tenantId + ":" + userId)
        .sessionId(agentId + ":" + sessionId)
        .build()).block();
```

전체 메커니즘은 [Context & AgentState](../building-blocks/context.md)에 있습니다.

## 2. Filesystem 모드와 `IsolationScope`: "누가 누구와 파일을 공유하는지" 결정하기

세 가지 모드 요약(자세한 내용은 [Filesystem](../harness/filesystem.md)에 있음):

| 모드 | 설정 | Shell? | 사용 시점 |
|------|--------|--------|-------------|
| **Local + shell** | `filesystem(new LocalFilesystemSpec()...)` 또는 생략 | ✅ 호스트 `sh -c` | 단일 프로세스 / 신뢰할 수 있는 환경 |
| **공유 스토어** | `filesystem(new RemoteFilesystemSpec(store))` | ❌(셸이 필요하면 샌드박스 사용) | 다중 레플리카 / 다중 pod가 장기 메모리를 공유 |
| **샌드박스** | `filesystem(new DockerFilesystemSpec()...)`와 네 개의 형제 클래스 | ✅ 샌드박스 내부 | 신뢰할 수 없는 코드 / 호출 간 복구 / 엄격한 사용자 격리 |

**`IsolationScope`는 멀티 유저 격리의 핵심 키입니다.** 공유 스토어 모드와 샌드박스 모드 모두 네임스페이스를 버킷으로 나누는 데 동일한 scope를 사용합니다.

| Scope | 의미 | 일반적인 사용처 |
|-------|---------|-------------|
| `SESSION`(샌드박스 기본값) | sessionId당 하나의 슬롯 | 멀티 유저 SaaS, 각 대화가 독립적임 |
| `USER`(Remote 기본값) | 동일한 `userId`는 세션 간에 공유됨 | 한 사용자가 여러 기기에서 장기 메모리를 공유 |
| `AGENT` | 에이전트의 모든 사용자/세션이 공유함 | 공개 지식 베이스 에이전트 |
| `GLOBAL` | 모든 것에 대해 하나의 공유 슬롯 | 신중하게 사용할 것 |

```java
// distributedStore auto-injects baseStore into RemoteFilesystemSpec
DistributedStore store = RedisDistributedStore.fromJedis(jedis);

HarnessAgent.builder()
    .distributedStore(store)
    .filesystem(new RemoteFilesystemSpec()
            .isolationScope(IsolationScope.USER)
            .anonymousUserId("_default"))   // fallback when userId is absent
    .build();
```

`anonymousUserId`는 프로덕션에서 중요한 세부 사항입니다 — `RuntimeContext.userId`는 종종 null입니다(시스템 작업, 스케줄러 트리거, 관리 작업). 빈 문자열로 폴백하지 마세요. 그러지 않으면 모든 익명 호출자가 하나의 공유 버킷에 몰리게 됩니다.

## 3. Remote 모드의 `BaseStore` 스토어: KV 선택 — 그리고 OSS가 왜 맞지 않는지

`RemoteFilesystemSpec`은 `BaseStore` 인터페이스 위에 놓입니다. 두 가지 내장 구현체가 있습니다.

| 구현체 | 의존성 | 동시성 안전성 | 사용 시점 |
|----------------|------------|--------------------|-------------|
| `RedisStore` | `agentscope-extensions-redis` | Lua 기반 CAS `putIfVersion`, 접두사 검색을 위한 `ZRANGEBYLEX` | 기본값; 다중 레플리카 공유 |
| `JdbcStore` | `agentscope-extensions-mysql`; MySQL / PostgreSQL / SQLite / H2 방언을 자동 감지 | 단일 문장 CAS UPDATE | 기존 관계형 인프라 / 조인 필요 |
| `InMemoryStore` | — | — | 테스트 |

```java
// Recommended: one-line configuration via DistributedStore
DistributedStore store = RedisDistributedStore.fromJedis(
        new JedisPooled("redis://prod-redis:6379"));

HarnessAgent agent = HarnessAgent.builder()
        .name("multi-tenant-agent")
        .model(model)
        .workspace(workspace)
        .distributedStore(store)           // auto-wires stateStore + baseStore
        .filesystem(new RemoteFilesystemSpec() // baseStore injected from store
                .isolationScope(IsolationScope.USER)
                .workspaceIndex(WorkspaceIndex.open(workspace)))  // speeds up ls/glob
        .build();

// Or with MySQL:
DistributedStore mysqlStore = MysqlDistributedStore.create(dataSource);
```

### OSS / NAS / S3는 어떤가요?

**OSS를 대상으로 `BaseStore`를 구현하지 마세요** — `MEMORY.md` / `memory/YYYY-MM-DD.md` / `agents/<id>/context/<sid>/`는 초당 여러 번 기록되며, OSS의 지연 시간과 요청당 비용이 즉시 폭발적으로 늘어납니다. 올바른 역할 분담은 다음과 같습니다.

| 데이터 형태 | Store | 소유자 |
|------------|---------|-------|
| 고빈도 소형 KV(메모리, 세션 스냅샷, 작업 기록) | Redis / MySQL(`BaseStore`) | `RemoteFilesystemSpec` |
| 대형 객체(수십 MB짜리 전체 샌드박스 워크스페이스 tar) | OSS / S3 | `OssSnapshotSpec` / 커스텀 `RemoteSnapshotSpec` |
| 노드 간 공유 볼륨(동일 디렉터리를 마운트하는 여러 샌드박스 인스턴스) | NAS / EFS | `AgentRunFilesystemSpec.nasConfig(...)`(AgentRun만 네이티브로 지원) |

### `RemoteFilesystemSpec` 라우팅 테이블

서브시스템 간 키 충돌을 방지하기 위해, spec은 워크스페이스를 독립적인 네임스페이스 세그먼트로 나눕니다.

| 워크스페이스 경로 | 네임스페이스 세그먼트 |
|----------------|-------------------|
| `AGENTS.md` / `MEMORY.md` / `tools.json` | `root` |
| `memory/` | `memory` |
| `skills/` | `skills` |
| `subagents/` | `subagents` |
| `knowledge/` | `knowledge` |
| `agents/<agentId>/sessions/` | `sessions` |
| `agents/<agentId>/tasks/` | `tasks` |
| 추가: `.addSharedPrefix("prompts/")` | 자동으로 파생됨 |

각 세그먼트는 다시 `IsolationScope`(`USER` → `agents/<agentId>/users/<userId>/`)로 버킷화됩니다. Redis 키는 결국 `agentscope:store:item:agents\0X\0users\0alice\0memory\0memory/2026-06-02.md`처럼 보이게 됩니다.

### `CompositeFilesystem`: 2계층 읽기 + write-through

`RemoteFilesystemSpec.toFilesystem(...)`은 실제로 `CompositeFilesystem`을 생성합니다: 셸이 없는 기본 `LocalFilesystem`(로컬 템플릿을 위한 폴백)에, 경로별로 하나씩의 `OverlayFilesystem`(상위 = `RemoteFilesystem`, 하위 = 읽기 전용 `LocalFilesystem` 템플릿)이 더해진 구조입니다.

효과: **쓰기는 항상 Remote로 가고, 읽기는 먼저 Remote를 확인한 뒤 로컬 템플릿으로 폴백합니다**. 이것이 [Workspace](../harness/workspace.md)에서 설명한 "2계층 읽기 아키텍처"가 Remote 모드로 구체화된 것입니다 — 로컬 `<workspace>/AGENTS.md`는 시드 역할을 하며(팀 git을 통해 동기화됨), Remote는 한 번 쓰기가 이루어지는 즉시 그 자리를 넘겨받습니다.

### `WorkspaceIndex`: 선택적인 SQLite 인덱스

```java
.filesystem(new RemoteFilesystemSpec(store).workspaceIndex(WorkspaceIndex.open(workspace)))
```

Remote 모드에서 `ls` / `glob` / `exists` / `grep`을 빠르게 만듭니다 — 이것이 없으면 모든 호출이 전체 KV를 스캔합니다. `WorkspaceIndex`는 최선 노력(best-effort) 방식의 SQLite 파일이며(`<workspace>/.index/` 아래에 있음), 실패해도 정확성에 영향을 주지 않고 조용히 성능만 저하됩니다.

## 4. 스킬 마켓플레이스: 어떤 `SkillRepository`를 선택할지

스킬은 낮은 우선순위에서 높은 우선순위로 합성됩니다(자세한 내용은 [Skill](../harness/skill.md)에 있음):

| 계층 | 소스 | 설정 방법 | 용도 |
|-------|--------|---------------|------------|
| 1 | 프로젝트 전역 | `.projectGlobalSkillsDir(Path)` | 개인 개발 머신; `~/.agentscope/skills/` |
| 2 | 마켓플레이스 | `.skillRepository(...)` | 프로젝트 간 공유 |
| 3 | 워크스페이스 공유 | `workspace/skills/` | 프로젝트별; git에 커밋됨 |
| 4 | 사용자별 | `<userId>/skills/` | 사용자 수준 오버라이드 |

### 마켓플레이스 스토어

| 저장소 | 모듈 | 참고 사항 | 적합한 용도 |
|-----------|--------|-------|----------|
| `GitSkillRepository` | `agentscope-extensions-skill-git-repository` | 팀 git 저장소; HEAD가 바뀔 때만 pull; 읽기 전용 배포 | 초기 단계 / 소규모 팀; PR을 통해 스킬 변경을 리뷰 |
| `MysqlSkillRepository` | `agentscope-extensions-skill-mysql-repository` | `DataSource` 기반; `writeable(true/false)` 토글; 에이전트가 다시 써넣을 수 있음 | 플랫폼 측의 중앙 거버넌스; 멀티 팀 멀티 에이전트 |
| `NacosSkillRepository` | `agentscope-extensions-nacos-skill` | 온라인 배포 + 구성 센터 변경 구독; `AutoCloseable` | Alibaba Cloud 생태계; "한 번 바꾸면 전체 플릿에 적용" |
| `ClasspathSkillRepository` | `agentscope-core` | JAR에 함께 제공됨; Spring Boot fat-JAR 호환 | 제품에 고정으로 내장된 역량 |

```java
HarnessAgent agent = HarnessAgent.builder()
        // ...
        .skillRepository(new GitSkillRepository("https://github.com/your-org/team-skills.git"))
        .skillRepository(MysqlSkillRepository.builder(dataSource)
                .databaseName("agentscope")
                .skillsTableName("skills")
                .createIfNotExist(true)
                .writeable(false)                  // read-only distribution; recommended for production
                .build())
        .build();
```

`skillRepository(...)`는 누적됩니다; 이름이 충돌하면 나중에 등록된 것이 우선합니다.

### 프로덕션 체크리스트

- **`MysqlSkillRepository(writeable=false)` 또는 `NacosSkillRepository`를 선호하세요** — 플랫폼 측의 중앙 거버넌스, 에이전트는 읽기 전용이며, 쓰기 반영은 관리 콘솔 + 리뷰 흐름을 거칩니다.
- 에이전트가 `workspace/skills/`를 보지 않았으면 좋겠나요? `.disableDefaultWorkspaceSkills()`를 사용하세요.
- `enableSkillManageTool`이 에이전트가 새 스킬 초안을 작성하도록 허용할 때는, **반드시** `enableSkillPromotionGate(...)`와 짝지으세요; 프로덕션에서는 절대 `autoPromote=true`로 두지 마세요.
- `NacosSkillRepository`는 `AutoCloseable`입니다 — Spring `@PreDestroy`나 `try-with-resources`에서 닫으세요, 그러지 않으면 구독이 누수됩니다.

## 5. 셸이 필요할 때: Sandbox와 필수적인 Snapshot을 선택하기

다음과 같은 경우에는 샌드박스를 사용해야 합니다.

- 모델이 신뢰할 수 없는 코드(Python / shell / `npm install` / 컴파일)를 실행할 수 있는 경우
- 호출 간에 **전체 작업 디렉터리**를 복구해야 하는 경우(`node_modules`, 생성된 파일, `pip install` 이후 환경)
- 엄격한 사용자 격리가 필요한 경우(다른 사용자의 프로세스를 들여다볼 수 없음)

### 다섯 가지 샌드박스 스토어

| Spec | 모듈 | 사용 대상 |
|------|--------|------------|
| `DockerFilesystemSpec` | `io.agentscope.harness.agent.sandbox.impl.docker` | 단일 머신 / 로컬 클러스터; 이미지로부터 컨테이너를 생성; 가장 익숙함 |
| `KubernetesFilesystemSpec` | `...impl.kubernetes` | 이미 K8s를 운영 중; pod / Job |
| `DaytonaFilesystemSpec` | `...impl.daytona` | Daytona(dev-env-as-a-service) |
| `E2bFilesystemSpec` | `...impl.e2b` | E2B 클라우드 샌드박스; 자체 관리 인프라 없이 가장 빠르게 출시 가능 |
| `AgentRunFilesystemSpec` | `...impl.agentrun` | **Alibaba Cloud AgentRun**; 네이티브 NAS / OSS 마운트; 엔터프라이즈급 |

```java
.filesystem(new DockerFilesystemSpec()
        .image("ubuntu:24.04")
        .isolationScope(IsolationScope.SESSION))
```

### 스냅샷은 샌드박스의 분산 생명줄입니다

샌드박스는 기본적으로 임시적(ephemeral)입니다 — 다음 `call()`은 새 컨테이너를 가진 다른 노드에 도착할 수 있고, 그러면 `pip install`한 모든 것과 생성된 파일을 잃게 됩니다. `SandboxSnapshotSpec`은 워크스페이스를 tar로 아카이브해, 다음 `call()`이 이를 새 컨테이너로 복원(hydrate)할 수 있게 합니다.

| Spec | Store | 모듈 | 사용 시점 |
|------|-------|--------|-------------|
| `NoopSnapshotSpec` | — | `agentscope-harness` | 프로덕션용 아님; 컨테이너를 잃을 때마다 샌드박스가 콜드 스타트함 |
| `LocalSnapshotSpec(Path)` | 로컬 디렉터리의 `tar` 파일 | `agentscope-harness` | 단일 노드 디버깅 |
| `OssSnapshotSpec` | Alibaba Cloud OSS | `agentscope-extensions-oss` | **대형 객체에 첫 번째 선택**; 오브젝트 스토리지에 자연스럽게 맞음 |
| `RedisSnapshotSpec` | Redis | `agentscope-extensions-redis` | 소형 워크스페이스 + 짧은 TTL(Redis 메모리 비용에 주의) |
| `JdbcSnapshotSpec` | MySQL / JDBC BLOB | `agentscope-extensions-mysql` | 기존 관계형 DB, 별도 미들웨어 없음 |
| 커스텀 `RemoteSnapshotClient` → `RemoteSnapshotSpec` | S3 / GCS / MinIO | — | 내장 목록에 없는 모든 것 |

```java
DistributedStore redisStore = RedisDistributedStore.fromJedis(jedis);
DistributedStore ossStore = OssDistributedStore.create(
        ossClient,
        "agentscope-sandbox-snapshots",
        "prod/");                         // key prefix for environment isolation

DistributedStore store = DistributedStore.builder()
        .agentStateStore(redisStore.agentStateStore())
        .baseStore(redisStore.baseStore())
        .sandboxSnapshotSpec(ossStore.sandboxSnapshotSpec())
        .sandboxExecutionGuard(redisStore.sandboxExecutionGuard())
        .build();

HarnessAgent agent = HarnessAgent.builder()
        .name("coding-agent")
        .model(model)
        .workspace(workspace)
        .distributedStore(store)
        .filesystem(new DockerFilesystemSpec()
                .image("python:3.12-slim")
                .isolationScope(IsolationScope.USER))
        .build();
```

`distributedStore(...)`를 사용하면, 스냅샷 spec과 실행 가드가 자동으로 주입됩니다 — 수동 설정이 필요 없습니다. OSS 버킷이나 접두사를 커스터마이즈하려면, 생성 시점에 `OssDistributedStore`를 설정하는 것을 선호하세요; 완전히 커스텀한 스냅샷 구현이 필요할 때만 `SandboxFilesystemSpec`에 `SandboxSnapshotSpec`을 명시적으로 설정하세요.

### 샌드박스 실행 직렬화: `SandboxExecutionGuard`

`SESSION` / `USER` scope에서는 버킷이 이미 세션/사용자별로 파티셔닝되어 있어서 동시 `exec`가 충돌하지 않습니다. 여러 레플리카를 가진 `AGENT` / `GLOBAL` scope에서는, N개의 노드가 동일한 샌드박스 슬롯에서 `exec`를 두고 경쟁할 수 있습니다. `distributedStore(...)`는 적절한 실행 가드를 자동으로 주입합니다.

| 구현체 | 모듈 | 메커니즘 |
|---------------|--------|-----------|
| `RedisSandboxExecutionGuard` | `agentscope-extensions-redis` | Redis `SET NX PX` 리스 |
| `JdbcSandboxExecutionGuard` | `agentscope-extensions-mysql` | MySQL `GET_LOCK()` / `RELEASE_LOCK()` |

여전히 권장되는 방법은 `DistributedStore`를 통해 가드를 주입하는 것입니다.

```java
DistributedStore store = RedisDistributedStore.fromJedis(jedis);

HarnessAgent.builder()
        .distributedStore(store)
        .filesystem(new DockerFilesystemSpec()
                .image("ubuntu:24.04")
                .isolationScope(IsolationScope.GLOBAL))
        .build();
```

리스 TTL 같은 커스텀 락 파라미터가 필요할 때만 가드를 명시적으로 오버라이드하세요.

```java
HarnessAgent.builder()
        .distributedStore(store)
        .filesystem(new DockerFilesystemSpec()
                .image("ubuntu:24.04")
                .isolationScope(IsolationScope.GLOBAL)
                .executionGuard(RedisSandboxExecutionGuard.builder(jedis)
                        .leaseTtl(Duration.ofMinutes(30))
                        .build()))
        .build();
```

Zookeeper, etcd, 그 외 다른 락 메커니즘을 연결하기 위해 `SandboxExecutionGuard`를 직접 구현할 수도 있습니다.

### 워크스페이스 투영: 시드 파일을 샌드박스로 밀어 넣기

`SandboxFilesystemSpec`은 시작 시점에 콘텐츠 해시 기반 tar 아카이브를 hydrate하여(증분 재작성) `AGENTS.md, skills, subagents, knowledge, .skills-cache`(다섯 개의 루트)를 샌드박스로 투영합니다. 다음과 같이 조정할 수 있습니다.

```java
.filesystem(new DockerFilesystemSpec()
        .image("...")
        .workspaceProjectionRoots(List.of("AGENTS.md", "skills", "knowledge"))   // drop subagents/.skills-cache
        // .workspaceProjectionEnabled(false)   // fully disable
)
```

### AgentRun 전용: NAS / OSS 마운트

`AgentRunFilesystemSpec`은 **여러 샌드박스 인스턴스가 동일한 디렉터리를 마운트하는 것**(NAS를 통해)을 네이티브로 지원하는 유일한 샌드박스 파일 시스템입니다. "한 사용자가 서로 다른 세션에서 동일한 워크스페이스를 본다"는 비즈니스 케이스라면, 매번 스냅샷을 다시 hydrate하는 것보다 AgentRun + NAS가 더 효율적입니다.

```java
.filesystem(new AgentRunFilesystemSpec()
        .apiKey(System.getenv("AGENTRUN_API_KEY"))
        .accountId(System.getenv("ALI_ACCOUNT_ID"))
        .region("cn-hangzhou")
        .templateName("python-3.12")
        .nasConfig(new AgentRunNasMountConfig().fileSystemId("...").mountTargetDomain("...").mountDir("/workspace"))
        .addOssMount(new AgentRunOssMountConfig().bucketName("data").mountDir("/mnt/oss")))
```

전체 필드는 `AgentRunNasMountConfig` / `AgentRunOssMountConfig` 소스를 참고하세요.

## 6. 다중 레플리카 배포 체크리스트(통합)

위의 개별 컴포넌트 선택을 하나의 표로 정리하면 다음과 같습니다.

| 관심사 | 권장 조합 |
|---------|-------------------|
| 세션 / `AgentState` | `RedisDistributedStore` 또는 `AgentStateStore`를 주입하는 혼합 `DistributedStore`; `(userId, sessionId)`가 테넌트/사용자/에이전트 차원을 담음 |
| 워크스페이스 파일 | `distributedStore(...)`가 주입하는 `BaseStore` + `RemoteFilesystemSpec` + `WorkspaceIndex` + `IsolationScope.USER` |
| 대형 객체 / 스냅샷 | 혼합 `DistributedStore`에서 `OssDistributedStore.sandboxSnapshotSpec()`을 사용하세요(대형 스냅샷을 Redis에 쓰지 마세요) |
| 노드 간 샌드박스 공유 | AgentRun + NAS 마운트, 또는 `distributedStore(...)`가 주입하는 `SandboxExecutionGuard`를 사용한 자체 관리 K8s |
| 스킬 거버넌스 | `MysqlSkillRepository(writeable=false)` 또는 `NacosSkillRepository`; 에이전트 측 autoPromote를 비활성화 |
| 서브에이전트 작업 기록 | Remote / Sandbox 위의 `WorkspaceTaskRepository`를 통해 자동으로 이루어짐; 추가 설정 불필요 |
| 노출된 서브에이전트(사용자가 서브에이전트와 직접 대화) | `distributedStore`가 자동으로 연결하는 레지스트리 — `subagentId`가 해석되고 서브에이전트는 어떤 레플리카에서도 / 재시작 이후에도 복구됩니다; `subagentId`의 메시지를 동일한 노드로 다시 라우팅해서(sticky) 복구가 유일한 failover 경로가 되게 하세요. `GatewayBootstrap`의 경우 `.distributedStore(...)`를 전달하세요 |
| 우아한 종료 | `GracefulShutdownManager`(JVM 훅을 자동 등록함); SIGTERM을 처리함; `setConfig(...)`로 진행 중인 작업의 대기 시간을 조정 |
| Observability | `OtelTracingMiddleware` + OpenTelemetry SDK + OTLP exporter |
| 속도 제한 | 커스텀 `MiddlewareBase`(onModelCall); [Middleware — 속도 제한 미들웨어](../building-blocks/middleware.md#속도-제한-미들웨어)를 참고하세요 |

## 7. 완전한 프로덕션 빌더 템플릿

에이전트는 호출 간에 상태 비저장입니다 — 하나의 싱글턴이 동시 요청을 처리합니다. 각 `call()`은 `RuntimeContext`의 `(userId, sessionId)`를 통해 상태를 찾으며, 완전히 격리됩니다.

```java
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.extensions.redis.RedisDistributedStore;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import io.agentscope.core.memory.compaction.CompactionConfig;
import io.agentscope.core.memory.compaction.ToolResultEvictionConfig;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import redis.clients.jedis.JedisPooled;

// --- Dependencies (create once at startup) ---
Path workspace = Paths.get("/var/agentscope/workspace");
JedisPooled jedis = new JedisPooled(System.getenv("REDIS_URI"));
DistributedStore store = RedisDistributedStore.fromJedis(jedis);

// --- Singleton agent (created once at startup) ---
HarnessAgent agent = HarnessAgent.builder()
        .name("coding-assistant")
        .model("dashscope:qwen-plus")
        .workspace(workspace)
        .distributedStore(store)  // auto-wires stateStore + snapshotSpec + executionGuard
        .filesystem(new DockerFilesystemSpec()
                .image("python:3.12-slim")
                .isolationScope(IsolationScope.USER))
        .compaction(CompactionConfig.builder()
                .triggerMessages(50)
                .keepMessages(20)
                .build())
        .toolResultEviction(ToolResultEvictionConfig.defaults())
        .skillRepository(io.agentscope.core.skill.repository.mysql.MysqlSkillRepository
                .builder(skillsDataSource())
                .createIfNotExist(false)
                .writeable(false)
                .build())
        .middlewares(List.of(new OtelTracingMiddleware()))
        .build();
```

호출 시점에는, 사용자/세션을 식별하기 위해 `RuntimeContext`를 전달하세요. 서로 다른 세션은 동일한 에이전트 인스턴스 위에서 동시에 실행됩니다.

```java
// In your HTTP handler
agent.call(msg, RuntimeContext.builder()
        .userId(httpRequest.tenantUserId())
        .sessionId(httpRequest.sessionId())
        .build()).block();
```

## 8. 흔한 함정

- **`RuntimeContext` 전달을 잊는 것** — `sessionId`가 없으면 모든 요청이 `defaultSessionId` 상태를 공유하게 되어 상태가 뒤섞입니다. 멀티 유저 시나리오에서는, 상태 격리를 보장하기 위해 **모든 `call()`에 항상 `RuntimeContext.builder().userId(...).sessionId(...).build()`를 전달하세요**. [Agent — Multi-user Concurrency](../building-blocks/agent.md#다중-사용자--다중-세션-동시성)를 참고하세요.
- **워크스페이스 쓰기에 `java.nio.Files` 사용** — 샌드박스 / Remote 모드에서는 잘못된 위치에 기록됩니다. 항상 `agent.getWorkspaceManager()`를 거치세요. **예외**: 빌드 시점 시드 파일(`initWorkspaceIfAbsent` 스타일 코드) — 아직 런타임 컨텍스트가 없으므로, 로컬 템플릿을 시드하는 것이므로 `java.nio.Files`가 올바릅니다.
- **`tools.json`의 `allow` 필터는 내장 도구도 필터링합니다** — 화이트리스트를 만들 때는 `read_file` / `memory_search` / `agent_spawn` 등을 목록에 함께 넣으세요, 그러지 않으면 모든 내장 도구가 제거됩니다.
- **`IsolationScope`를 바꿔도 기존 데이터는 마이그레이션되지 않습니다** — 배포 전에 고정하세요. 배포 후 이를 바꾸는 것은 새로운 네임스페이스로 전환하는 것과 같습니다.
- **로컬 `AgentStateStore`의 단일 머신 제약**: 분산 파일 시스템과 로컬 `JsonFileAgentStateStore`를 짝지은 K8s 다중 레플리카 빌드는 첫 번째 `build()`에서 바로 `IllegalStateException`을 던집니다. **이것은 의도된 동작입니다** — 하나의 pod 로컬 디스크에 에이전트 상태를 둘 수는 없습니다.
- **`NacosSkillRepository`를 닫지 않음** — 구독이 누수됩니다; 플릿 규모에서는 Nacos가 불평합니다. Spring `@PreDestroy`나 `destroyMethod="close"`를 사용하세요.
- **IAM 없는 OSS / NAS** — `OssSnapshotSpec`은 플랫폼 AK/SK를 받습니다; RAM Role + STS 임시 자격 증명이 더 견고합니다.
- **샌드박스 모드에서의 로컬 `AgentStateStore`는 개발 전용입니다** — 빌드 시점 경고는 의도된 것입니다; 프로덕션에서 무시하지 마세요.

## 관련 페이지

- [Quickstart](../quickstart.md) — 첫 `HarnessAgent`를 처음부터 끝까지
- [Harness Architecture](../harness/architecture.md) — 여러 역량이 어떻게 협력하는지
- [Context & AgentState](../building-blocks/context.md) — `AgentState` / `AgentStateStore` / 노드 간 복구
- [Compaction](../harness/compaction.md) — 대화 요약, 도구 결과 축출, overflow 복구
- [Workspace](../harness/workspace.md) — 디렉터리 레이아웃, 2계층 읽기, `tools.json`
- [Filesystem](../harness/filesystem.md) — 세 가지 배포 모드, `IsolationScope`
- [Sandbox](../harness/sandbox.md) — 샌드박스 상세, 다섯 가지 구현체, 스냅샷 메커니즘
- [Skill](../harness/skill.md) — 4계층 합성, 마켓플레이스 스토어, 자가 학습 루프
- [Middleware](../building-blocks/middleware.md) — 커스텀 observability / 속도 제한 / 폴백 미들웨어
