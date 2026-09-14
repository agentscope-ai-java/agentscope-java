---
title: "컨텍스트 & AgentState"
description: "상태 비저장 에이전트 엔진, AgentState 생명주기, 상태 영속화, RuntimeContext"
---

## 상태 비저장 에이전트 엔진

`ReActAgent`(그리고 이를 감싸는 `HarnessAgent`)는 **상태 비저장 엔진**으로 설계되었습니다: 에이전트 인스턴스 자체는 불변 구성 — 시스템 프롬프트, 모델, 도구, 미들웨어 체인 — 만을 보유하며, 세션별 가변 데이터는 모두 `(userId, sessionId)`로 색인된 `AgentState`에 존재합니다. 단일 에이전트 인스턴스는 많은 사용자와 세션을 동시에 처리할 수 있습니다; 호출자는 각 `call()`마다 서로 다른 `RuntimeContext`를 전달하기만 하면 됩니다.

```
┌──────────────────────────────────────────────────────────────────┐
│                     HarnessAgent (싱글턴)                          │
│  불변 구성: sysPrompt, model, toolkit, middlewares                │
│                                                                  │
│  ┌─ 상태 캐시 ─────────────────────────────────────────────────┐   │
│  │  ("alice","s1") → AgentState  ← call(…, RC(alice,s1))       │
│  │  ("bob","s2")   → AgentState  ← call(…, RC(bob,s2))        │
│  └───────────────────────────────────────────────────────────┘   │
│                                                                  │
│  세션별 게이트: 동일한 (uid,sid) 호출은 직렬화, 그 외는 병렬 ∥       │
└──────────────────────────────────────────────────────────────────┘
```

### 이것이 의미하는 바

- **사용자별 에이전트 레지스트리가 필요 없습니다.** 하나의 `HarnessAgent` 인스턴스가 모든 사용자를 처리할 수 있습니다 — 요청마다 `RuntimeContext.userId`와 `RuntimeContext.sessionId`만 바꾸면 됩니다.
- **동시성이 기본 내장되어 있습니다.** 서로 다른 `(userId, sessionId)` 쌍은 완전히 병렬로 실행되며, 동일한 쌍은 대화 일관성을 유지하기 위해 자동으로 직렬화됩니다.
- **상태는 완전히 내부적으로 관리됩니다.** 에이전트는 호출 진입 시 저장소에서 `AgentState`를 로드하고 호출 종료 시 저장합니다 — 호출자는 상태 객체를 직접 관리하지 않습니다.
- **호출별 격리.** 각 `call()`은 자신만의 `AgentState` 스냅샷으로 동작합니다. 미들웨어와 도구는 (프레임워크가 호출 진입 시 주입하는) `RuntimeContext.getAgentState()`를 통해 호출 범위 상태에 접근하므로, 동시 호출들이 서로의 상태를 보는 일은 없습니다.

---

## AgentState

[`AgentStateStore`](../../../en/integration/session/index.md)는 **`AgentState`**(`io.agentscope.core.state.AgentState`) — 에이전트를 재시작 가능하게 만드는 모든 것의 완전한 스냅샷 — 를 영속화합니다:

| `AgentState` 필드 | 내용 |
|---|---|
| `getSessionId()` | 이 상태가 속한 세션 식별자 |
| `getUserId()` | 사용자 식별자 (익명 세션의 경우 null 가능) |
| `getContext()` / `contextMutable()` | 현재 대화 이력 (사용자 / 어시스턴트 / 도구 호출 / 도구 결과) |
| `getSummary()` | 압축된 요약 (압축이 활성화된 경우) |
| `getPermissionContext()` | 도구 권한 규칙 — [권한](../../../en/docs/building-blocks/permission-system.md) 참고 |
| `getPlanModeContext()` | Plan 모드 활성화 여부, 현재 플랜 파일 경로 |
| `getTasksContext()` | `todo_write` 작업 목록 |
| `getToolContext()` | 활성화된 툴킷 그룹 (`activatedGroups`) |

`AgentState`는 세션별 인터럽트 신호를 위한 일시적(transient)이고 직렬화되지 않는 `InterruptControl`도 가지고 있습니다 — 아래 [세션별 인터럽트](#세션별-인터럽트)를 참고하세요.

각 `call()`이 끝날 때, 프레임워크는 전체 `AgentState`를 `agent_state`라는 키로 상태 저장소에 기록하며, 이는 호출의 `(userId, sessionId)`로 지정됩니다. 동일한 `(userId, sessionId)`를 가진 다음 `call()`은 이를 자동으로 다시 로드합니다. **상태 저장소가 분산되어 있다면(예: Redis), 서로 다른 프로세스 — 심지어 서로 다른 물리 머신 — 의 에이전트 인스턴스들이 동일한 상태를 보게 됩니다.**

### 자동 영속화 및 복구 흐름

```
call(msgs, RuntimeContext(userId, sessionId))
  │
  ├─ 세션별 게이트: 동일한 (uid, sid)는 직렬화, 그 외는 병렬 실행
  │
  ▼
  캐시 또는 stateStore에서 AgentState 로드
  │   RuntimeContext에 주입: rc.setAgentState(state)
  │
  ▼
  추론 루프
  │   미들웨어가 state.contextMutable()을 변경
  │   (압축, Plan, todo_write, 권한 등)
  │
  ▼
  AgentState 저장
  │   stateStore.save(userId, sessionId, "agent_state", state)
  │
  ▼
  결과 반환
```

이 배선은 `ReActAgent` 자체에 구현되어 있으며; `HarnessAgent`는 이를 그대로 물려받습니다. 에이전트 인스턴스는 고정된 세션을 갖지 않습니다 — 각 호출은 자신의 `RuntimeContext`가 지정하는 슬롯을 읽고 씁니다(없으면 빌더 시점의 `defaultSessionId`로 폴백).

> `call()` 도중의 상태 변경은 메모리 내 `AgentState`에 대해 일어납니다. **상태 저장소는 메시지마다가 아니라 호출당 한 번(그리고 종료 시)만 기록됩니다** — 따라서 저장소에 걸리는 처리량 부담은 낮게 유지됩니다.

### 내장 및 확장 구현체

`io.agentscope.core.state.AgentStateStore`를 구현하는 모든 것이 동작합니다. 배포 형태에 따라 선택하세요:

| 구현체 | 모듈 | 사용 사례 |
|---|---|---|
| `InMemoryAgentStateStore` | `agentscope-core` | 단위 테스트 / 단일 프로세스 데모; 종료 시 소실됨 |
| `JsonFileAgentStateStore` | `agentscope-core` | 파일 영속화를 사용하는 로컬 개발용; 크로스 노드 불가. **`HarnessAgent`의 기본값**이며, `~/.agentscope/state/<agentId>/`를 루트로 함(기반 경로는 `agentscope.state.home` 시스템 프로퍼티로 재정의 가능); **단일 호스트** 전용 |
| `RedisAgentStateStore` | `agentscope-extensions-redis` | 다중 레플리카 배포를 위한 **프로덕션 기본값**; Jedis / Lettuce / Redisson(Standalone / Cluster / Sentinel)을 지원 |
| `MysqlAgentStateStore` | `agentscope-extensions-mysql` | 상태가 관계형 저장소(감사, 리포팅)로 흘러가야 할 때 |

전환은 빌더 시점의 호출 하나로 끝납니다:

```java
// 기본값 (단일 호스트) — .stateStore(...)를 생략하면 로컬 JsonFileAgentStateStore가 자동으로 사용됩니다
HarnessAgent agent = HarnessAgent.builder()
    .name("MyAgent")
    .model(model)
    .workspace(workspace)
    .build();

// 프로덕션 다중 레플리카 — DistributedStore 사용
JedisPooled jedis = new JedisPooled("redis://redis.prod:6379");
HarnessAgent agent = HarnessAgent.builder()
        .name("MyAgent")
        .model(model)
        .workspace(workspace)
        .stateStore(new RedisAgentStateStore(jedis))
        .distributedStore(RedisDistributedStore.fromJedis(jedis))
        .build();
```

:::{warning}
내장 `JsonFileAgentStateStore` / `InMemoryAgentStateStore`는 단일 호스트 전용입니다. 이미 `filesystem(SandboxFilesystemSpec)` 또는 `filesystem(RemoteFilesystemSpec)`(분산 워크스페이스)을 선택했다면, HarnessAgent는 빌드 시점에 `IllegalStateException`으로 로컬 상태 저장소를 **거부**합니다 — 샌드박스 상태는 레플리카 간에 공유되어야 합니다. `.distributedStore(...)`(예: `RedisDistributedStore`) 또는 `.stateStore(...)`를 통해 분산 저장소를 구성하세요.
:::

### 프로세스와 머신 간 실시간 재개

상태 저장소가 분산되어 있다면(예: Redis), 머신 간 재개는 **자동**입니다:

```java
// 노드 A — 대화 시작
HarnessAgent agentA = HarnessAgent.builder()
    .stateStore(redisStore)
    /* ... */ .build();
agentA.call(msg, RuntimeContext.builder()
    .sessionId("alice-2026-06-02-001")
    .userId("alice")
    .build()).block();

// 노드 B — 다른 물리 머신, 별도의 JVM
HarnessAgent agentB = HarnessAgent.builder()
    .stateStore(redisStore)
    /* 동일한 상태 저장소 */ .build();

// 동일한 (userId, sessionId)를 가진 노드 B의 첫 call()은 노드 A가 Redis에 남긴 AgentState를 로드합니다
agentB.call(nextMsg, RuntimeContext.builder()
    .sessionId("alice-2026-06-02-001")
    .userId("alice")
    .build()).block();
```

이로써 다음을 얻습니다:

- **장애 조치(Failover)**: 노드가 크래시하면 — 대화가 정상 노드로 이전되고, 사용자는 아무것도 알아차리지 못합니다.
- **롤링 배포**: 이전 파드는 종료 시 저장하고, 새 파드는 첫 호출 시 로드합니다 — **릴리스 간에도 대화가 끊기지 않습니다**.
- **크로스 서페이스 연속성**: 사용자가 Web UI에서 시작해 CLI로 전환해도 — 동일한 `(userId, sessionId)`이면 모든 메모리가 그대로 유지됩니다.

`(userId, sessionId)` 쌍이 네임스페이스를 정의합니다: 대부분의 경우 `sessionId`만으로 충분하며, 사용자별 파티셔닝이 필요할 때는 `userId`를 추가하세요.

### 다중 사용자 격리

`sessionId`와 `userId`는 서로 다른 문제를 해결합니다:

- **`sessionId`** — 어떤 대화인가; 독립적인 `AgentState` 스냅샷.
- **`userId`** — 이 대화를 소유한 사용자; 어떤 사용자의 네임스페이스 파일에 저장되는지도 결정함 — [파일시스템](../../../en/docs/harness/filesystem.md) 참고.

```java
agent.call(msg, RuntimeContext.builder()
    .sessionId("alice-1").userId("alice").build()).block();

agent.call(msg, RuntimeContext.builder()
    .sessionId("bob-1").userId("bob").build()).block();
```

두 사용자는 — 상태도, 파일시스템 경로도 별개이며, 서로 간섭이 없습니다. 프로덕션에서 `AgentState` 수준의 사용자 격리를 위해서는 `RuntimeContext`에 `userId`를 설정하세요: 저장소는 파일시스템 경로 버킷팅에 의존하는 대신 `(userId, sessionId)`로 각 슬롯을 지정합니다(`RedisAgentStateStore`를 사용하면 `userId`가 Redis 키의 일부가 됩니다).

### `AgentState`를 직접 읽고 쓰기

에이전트 루프를 우회해야 할 때(관리 콘솔, 감사, 일괄 마이그레이션):

```java
import io.agentscope.core.state.AgentState;

AgentState state = agent.getAgentState("alice", "session-001");
System.out.println("messages: " + state.getContext().size());

String json = state.toJson();
AgentState restored = AgentState.fromJsonString(json);
```

| 메서드 | 설명 |
|------|------|
| `getContext()` | 현재 대화 이력 (불변 뷰) |
| `contextMutable()` | 쓰기 가능한 뷰, 주의해서 사용 |
| `setSummary(...)` / `getSummary()` | 커스텀 압축 요약 (자체 압축 미들웨어용) |
| `toJson()` / `fromJsonString(String)` | 직렬화 / 역직렬화 |

### 세션의 대화 컨텍스트 지우기

사용자가 새 세션을 만들지 않고도 새로운 주제를 시작할 수 있게 하려면 `clearContext`를 호출하세요. 이는
동일한 `(userId, sessionId)`를 유지하며 권한, 도구, 작업, Plan 모드와 같은 비대화형 상태를 보존합니다.
모델에 보이는 메시지 버퍼와 압축 요약을 지운 다음, 에이전트에 `AgentStateStore`가 있는 경우 즉시
결과를 영속화합니다.

```java
agent.clearContext("alice", "session-001");

// 또는 호출에 사용했던 것과 동일한 RuntimeContext를 사용하세요.
agent.clearContext(RuntimeContext.builder()
    .userId("alice")
    .sessionId("session-001")
    .build());
```

세션의 현재 요청이 완료된 후에 호출하세요. 이는 진행 중인 호출을 취소하지 않습니다;
다음 호출은 지워진 대화 컨텍스트로 시작됩니다.

:::{note}
1.0의 `Memory` 인터페이스(`InMemoryMemory` / `LongTermMemory` 등)는 2.0에서 `@Deprecated(forRemoval = true)`입니다. 새 코드는 `AgentState.getContext()`와 `AgentStateStore`를 사용해야 합니다; `Memory`는 소스 호환성 shim으로만 남아 있습니다.
:::

### 세션별 인터럽트

각 `AgentState`는 일시적인 `InterruptControl`(`io.agentscope.core.interruption.InterruptControl`)을 가지고 있습니다 — 상태 저장소에 **절대 직렬화되지 않는**(`AgentState`에서 `@JsonIgnore transient`로 표시됨) 세션별 인터럽트 신호입니다. 이를 통해 동일한 에이전트 인스턴스의 다른 동시 호출에 영향을 주지 않고, 단일 세션의 진행 중인 호출을 대상으로 한 인터럽트가 가능합니다.

```java
// 특정 세션을 인터럽트 — 오직 해당 세션의 호출만 이 신호를 관찰함
agent.interrupt("alice", "session-001");

// 주입된 사용자 메시지와 함께 인터럽트
agent.interrupt("alice", "session-001", Msg.userMsg("Please stop and summarise."));
```

추론 루프는 각 반복 전에 `state.interruptControl().isInterrupted()`를 확인합니다. 트리거되면, 루프는 `handleInterrupt` 경로로 진입하여 상태를 저장하고 부분 결과를 반환합니다.

레거시 인자 없는 `interrupt()`는 단일 세션 시나리오에서 여전히 동작합니다 — 현재 활성화된 세션의 `InterruptControl`로 라우팅됩니다.

:::{note}
`InterruptControl`은 런타임 전용 신호입니다; 절대 영속화되지 않습니다. 세션이 장애 조치 후 다른 노드에서 재개되면, 인터럽트 플래그는 초기화된 상태로 시작합니다. (영속화**되는**) 별도의 `AgentState.shutdownInterrupted` 플래그는 세션이 정상 종료(graceful shutdown)로 인해 인터럽트되었는지를 기록하며 — 에이전트는 다음 로드 시 이를 감지하고 복구할 수 있습니다.
:::

### 동시 사용

에이전트는 상태 비저장 엔진이므로, 단일 인스턴스가 자연스럽게 동시 요청을 처리합니다:

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("SharedAssistant")
    .model(model)
    .workspace(workspace)
    .stateStore(redisStore)
    .build();

// 서로 다른 사용자 — 완전히 병렬, 경합 없음
Mono<Msg> aliceCall = agent.call(aliceMsg, RuntimeContext.builder()
    .userId("alice").sessionId("s1").build());
Mono<Msg> bobCall = agent.call(bobMsg, RuntimeContext.builder()
    .userId("bob").sessionId("s2").build());

Mono.zip(aliceCall, bobCall).block();  // 둘 다 병렬로 실행됨

// 동일한 사용자, 동일한 세션 — 자동으로 직렬화됨
Mono<Msg> call1 = agent.call(msg1, RuntimeContext.builder()
    .userId("alice").sessionId("s1").build());
Mono<Msg> call2 = agent.call(msg2, RuntimeContext.builder()
    .userId("alice").sessionId("s1").build());

// call2는 call1 뒤에서 대기함 — 대화 이력의 일관성이 유지됨
Flux.merge(call1, call2).collectList().block();
```

**동시성 규칙:**
- **서로 다른 `(userId, sessionId)`** → 완전히 병렬, 각 호출은 자신만의 `AgentState`로 동작.
- **동일한 `(userId, sessionId)`** → 세션별 비동기 게이트가 FIFO 순서로 호출을 직렬화 — 외부 잠금 없이도 상태 일관성이 보장됨.
- **`interrupt(userId, sessionId)`** → 정확히 하나의 세션만 대상으로 하며, 그 외 진행 중인 호출에는 영향 없음.

:::{tip}
메모리 내 상태 캐시는 단일 에이전트 인스턴스가 처리한 서로 다른 세션 수만큼 커집니다. 대부분의 배포(수백 개 세션)에서는 무시할 수 있는 수준입니다. 매우 대규모 시나리오(프로세스당 수백만 세션)의 경우 제한된 인스턴스 풀을 가진 에이전트 팩토리 패턴을 고려하세요 — 다만 `AgentState` 객체는 가볍기 때문에 이런 경우는 드뭅니다.
:::

---

## `RuntimeContext` — 호출별 메타데이터

`RuntimeContext`(`io.agentscope.core.agent`에 위치)는 `agent.call(msgs, ctx)`에 전달되는 경량의 호출별 운반체입니다; 훅과 도구는 한 번의 호출이 진행되는 동안 이를 공유합니다. 자유 형식 / 타입 속성은 **영속화되지 않습니다**; `sessionId` / `userId` 필드는 이 호출에 대해 상태 저장소가 로드하고 저장할 `AgentState` 슬롯을 선택합니다. 호출 진입 시, 프레임워크는 호출 범위의 `AgentState`를 `RuntimeContext`에 주입하여 미들웨어, 도구, 훅이 `ctx.getAgentState()`를 통해 올바른 호출별 상태에 접근할 수 있게 합니다.

```java
import io.agentscope.core.agent.RuntimeContext;

RuntimeContext ctx = RuntimeContext.builder()
        .userId("alice")
        .sessionId("s-001")
        .put("request_id", "req-2026-06-01-abc")
        .put(MyTenantInfo.class, new MyTenantInfo("tenant-7"))
        .build();

Msg result = agent.call(List.of(new UserMessage("Hi")), ctx).block();
```

사용 가능한 접근자:

| 메서드 | 설명 |
|------|------|
| `getSessionId()` / `getUserId()` | 상태 슬롯과 테넌트를 라우팅하는 데 사용되는 내장 필드 |
| `getAgentState()` / `setAgentState(AgentState)` | 호출 진입 시 프레임워크가 주입하는 호출 범위 `AgentState`. 미들웨어와 도구는 `agent.getAgentState()`가 아니라 여기서 상태를 읽어야 함 |
| `resolveAgentState(ctx, agent)` | 정적 헬퍼: 사용 가능하면 `ctx.getAgentState()`를 반환하고, 없으면 `agent.getAgentState()`로 폴백함. 동시성 안전을 위해 미들웨어/도구에서 사용하세요 |
| `get(String)` / `put(String, Object)` | 문자열 키 기반 get/put |
| `get(Class<T>)` / `put(Class<T>, T)` | 타입 싱글턴 get/put |
| `getExtra()` | 문자열 속성 맵에 대한 직접 접근 (가변 뷰) |
| `RuntimeContext.empty()` | 빈 컨텍스트 |

:::{tip}
**`AgentStateStore`는 빌더 시점에 바인딩되며, `RuntimeContext`를 통해 호출마다 전환할 수 없습니다.** 호출마다 실제로 달라지는 것은 그것이 지정하는 `(userId, sessionId)` 슬롯입니다 — 사용자별 격리를 위해서는 `userId`(또는 저장소의 커스텀 `keyPrefix`)를 설정하고, 각 호출에 서로 다른 상태 저장소 인스턴스를 넘기려 하지 마세요.
:::

:::{tip}
**미들웨어와 도구에서 `AgentState`에 접근하기:** 호출 실행 중에는 항상 `agent.getAgentState()`가 아니라 `RuntimeContext.resolveAgentState(ctx, agent)`를 사용하세요. 동시성 상황에서 `agent.getAgentState()`는 마지막으로 활성화된 세션의 상태를 반환합니다(여러 호출이 진행 중일 때는 임의의 선택입니다), 반면 `ctx.getAgentState()`는 **현재 호출**의 세션에 대한 상태를 반환합니다 — 거의 항상 이것이 원하는 값입니다.
:::

---

## 관련 페이지

- [에이전트](./agent.md) — 전체 `ReActAgent` API와 빌더 필드
- [컨텍스트 압축](../../../en/docs/harness/compaction.md) — 대화 요약, 도구 결과 제거, 오버플로 복구 (여기서 설명한 AgentState 기반 위에 구축됨)
- [메모리](../../../en/docs/harness/memory.md) — 장기 메모리, 백그라운드 유지 관리
- [권한](../../../en/docs/building-blocks/permission-system.md) — 권한 규칙의 영속화
