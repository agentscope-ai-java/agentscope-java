# 에이전트 상태 저장소 (AgentStateStore)

```{note}
**권장: 한 줄 설정을 위해 [DistributedStore](../distributed/index.md)를 사용하세요** — AgentStateStore, BaseStore, SandboxSnapshotSpec, SandboxExecutionGuard를 한꺼번에 다룹니다. AgentStateStore만 개별적으로 설정해야 하는 경우에만 계속 읽어보세요.
```

`io.agentscope.core.state.AgentStateStore`는 AgentScope가 에이전트 상태를 영속화하는 데 사용하는 인터페이스입니다 — Memory, Workspace, Plan 및 기타 컴포넌트는 `State` 객체로 직렬화되어 `AgentStateStore`를 통해 저장되며, 이를 통해 재시작 복구와 노드 간 공유가 가능해집니다.

상태는 `(userId, sessionId)`로 주소가 지정됩니다:

- `sessionId` — 필수이며, 비어 있지 않아야 하고, 세션을 식별합니다.
- `userId` — 선택 사항입니다. `null`은 익명 / 단일 테넌트(CLI, 테스트 등)를 의미합니다.

## 사용 가능한 구현체

| 구현체 | 모듈 | 사용 시점 |
| --- | --- | --- |
| `InMemoryAgentStateStore` | `agentscope-core` | 단위 테스트 |
| `JsonFileAgentStateStore` | `agentscope-core` | 단일 노드 개발 (**HarnessAgent 기본값**) |
| `RedisAgentStateStore` | `agentscope-extensions-redis` | [다중 레플리카 프로덕션 기본값](../distributed/redis.md) |
| `MysqlAgentStateStore` | `agentscope-extensions-mysql` | [기존 데이터베이스 인프라](../distributed/mysql.md) |
| `OssAgentStateStore` | `agentscope-extensions-oss` | [Alibaba Cloud 생태계](../distributed/oss.md) |

## 단독 설정

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model(model)
    .stateStore(stateStore)   // any AgentStateStore implementation
    .build();
```

자세한 사용법과 코드 예제는 각 저장소의 문서를 참조하세요:

- [Redis](../distributed/redis.md#1-redisagentstatestore)
- [MySQL](../distributed/mysql.md#1-mysqlagentstatestore)
- [OSS](../distributed/oss.md#1-ossagentstatestore)
