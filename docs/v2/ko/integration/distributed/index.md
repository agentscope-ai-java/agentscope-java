# 분산 스토리지 (Distributed Store)

AgentScope는 분산 영속성이 필요한 모든 컴포넌트를 `DistributedStore` 인터페이스 아래로 통합합니다. 단 한 줄의 설정으로 에이전트 상태, 워크스페이스 파일 시스템, 샌드박스 스냅샷, 동시성 락을 동일한 분산 스토어로 전환할 수 있습니다.

## 빠른 시작

```java
// Redis — 한 줄 설정
DistributedStore store = RedisDistributedStore.fromJedis(
        new JedisPooled("redis://localhost:6379"));

HarnessAgent agent = HarnessAgent.builder()
    .name("my-agent")
    .model("dashscope:qwen-plus")
    .distributedStore(store)
    .filesystem(new RemoteFilesystemSpec()            // baseStore auto-injected
            .isolationScope(IsolationScope.USER))
    .build();
```

## 기능 매트릭스

| 컴포넌트 | 인터페이스 | Redis | OSS | MySQL |
|-----------|----------|:-----:|:---:|:-----:|
| 에이전트 상태 영속성 | `AgentStateStore` | `RedisAgentStateStore` | `OssAgentStateStore` | `MysqlAgentStateStore` |
| 워크스페이스 파일 시스템 KV | `BaseStore` | `RedisStore` | `OssBaseStore` | `JdbcStore` |
| 샌드박스 스냅샷 | `SandboxSnapshotSpec` | `RedisSnapshotSpec` | `OssSnapshotSpec` | `JdbcSnapshotSpec` |
| 샌드박스 동시성 락 | `SandboxExecutionGuard` | `RedisSandboxExecutionGuard` | — | `JdbcSandboxExecutionGuard` |

> OSS는 `SandboxExecutionGuard`를 제공하지 않습니다 — 오브젝트 스토리지는 분산 락에 적합하지 않습니다. `DistributedStore.builder()`를 통해 Redis 가드를 혼합하여 사용하세요.

## 혼합 스토어

서로 다른 컴포넌트를 서로 다른 스토리지 스토어에서 가져올 수 있습니다.

```java
DistributedStore mysql = MysqlDistributedStore.create(dataSource);
DistributedStore redis = RedisDistributedStore.fromJedis(jedis);

// MySQL for state and files, Redis for sandbox lock and snapshots
DistributedStore mixed = DistributedStore.builder()
    .agentStateStore(mysql.agentStateStore())
    .baseStore(mysql.baseStore())
    .sandboxSnapshotSpec(redis.sandboxSnapshotSpec())
    .sandboxExecutionGuard(redis.sandboxExecutionGuard())
    .build();

HarnessAgent.builder()
    .distributedStore(mixed)
    .filesystem(new DockerFilesystemSpec()
            .image("ubuntu:24.04"))
    .build();
```

## 컴포넌트

### AgentStateStore — 에이전트 상태 영속성

대화 컨텍스트, 압축(compaction) 요약, 권한 규칙, Plan Mode 상태를 `(userId, sessionId)`로 식별하여 저장합니다. `distributedStore`에 의해 자동으로 연결되며, `.stateStore(...)`를 통해 재정의할 수 있습니다.

### BaseStore — 워크스페이스 파일 시스템 KV

`RemoteFilesystemSpec`을 위한 스토리지 프로바이더로, `MEMORY.md`, `memory/`, `skills/`, `sessions/`를 공유 KV 스토리지로 라우팅합니다. 인자 없는 생성자를 사용할 때 `RemoteFilesystemSpec`에 자동으로 주입됩니다.

### SandboxSnapshotSpec — 샌드박스 스냅샷

Docker/K8s 샌드박스 워크스페이스를 tar 아카이브로 영속화하여 호출 간 복구를 지원합니다. `distributedStore`에 의해 `SandboxFilesystemSpec`에 자동으로 연결됩니다.

### SandboxExecutionGuard — 샌드박스 동시성 락

다중 레플리카 배포 환경에서 `AGENT` / `GLOBAL` 격리 범위를 위한 분산 락입니다. `distributedStore`에 의해 `SandboxFilesystemSpec`에 자동으로 연결됩니다.

## 우선순위

```
Explicit builder methods (.stateStore(), .snapshotSpec() on FilesystemSpec, etc.)
    > distributedStore auto-wiring
        > local defaults (JsonFileAgentStateStore, NoopSnapshotSpec, etc.)
```

## 스토어 문서

- [Redis](redis.md) — 전체 기능을 모두 지원하며, 다중 레플리카 프로덕션 환경에 권장됩니다
- [MySQL / JDBC](mysql.md) — 기존 관계형 데이터베이스 인프라를 사용하는 경우에 적합합니다
- [Alibaba Cloud OSS](oss.md) — 오브젝트 스토리지로, 대용량 스냅샷에 가장 적합합니다

## aistio 호스팅 스토어

이미 aistio 컨트롤 플레인을 운영 중이라면, `DistributedStore`의 조정(coordination) 영역(BaseStore, 샌드박스 락/스냅샷, MessageBus, AsyncToolRegistry, **TaskRepository**, 선택적 **SessionTurnGate**)을 호스팅할 수 있습니다. 단, `AgentStateStore` 백엔드(Redis / MySQL / Postgres / OSS)는 **하나**를 직접 제공해야 합니다. 코어는 `getVersioned` / `saveIfVersion` 낙관적 동시성 제어를 노출하지만, 상태 저장소는 컨트롤 플레인에 포함되지 않습니다.

```java
ControlPlaneStores cp = ControlPlaneStores.fromEnv();
HarnessAgent.builder()
    .distributedStore(cp.withAgentStateStore(redis.agentStateStore()))
    .filesystem(new RemoteFilesystemSpec().isolationScope(IsolationScope.USER))
    .build();
```

- 컨트롤 플레인에서 `--enable-hosted-store`로 활성화하세요 (프로덕션에는 Postgres를 권장합니다).
- **`withAgentStateStore`는** 호스팅된 `TaskRepository`와 `SessionTurnGate`를 포함합니다. **`SandboxFilesystemSpec`과 서브에이전트 백그라운드 작업**을 사용할 때는 이 경로를 사용하세요 — 워크스페이스의 `TaskRepository`는 레플리카 간에 작업을 영속화할 수 없습니다.
- **AgentStateStore 버전 관리**: Redis, Postgres, MySQL, InMemory는 CAS(compare-and-swap)를 지원합니다. JsonFile, OSS, COS, JPA는 여전히 마지막 쓰기 우선(last-writer-wins) 방식입니다. 다중 레플리카 배포에서는 버전 관리를 지원하는 백엔드를 선호하세요.
- **턴 게이트와 `ConflictPolicy.FAIL`**은 선택 사항입니다 — 이들은 다중 레플리카 환경에서 중복 LLM 턴을 줄여주지만, 정확성은 여전히 백엔드가 버전 관리를 지원할 때의 CAS에서 비롯됩니다.
- 현재 인증은 공유 내부 토큰 방식이며, 테넌트(`agentName` / `namespace`)는 요청 본문에서 가져옵니다 — 하나의 컨트롤 플레인에서 상호 신뢰되지 않는 다중 테넌트 에이전트를 위한 방식은 **아닙니다**.
- `MessageBus.queueDrain`은 **파괴적**(읽으면 즉시 ack 처리)입니다 — 테넌트 키가 잘못되면 메시지가 유실됩니다.
