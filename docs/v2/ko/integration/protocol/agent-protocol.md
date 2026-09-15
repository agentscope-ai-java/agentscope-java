# Agent Protocol

`agentscope-extensions-agent-protocol`은 AgentScope의 [Harness Agent](../../docs/harness/architecture.md)를 표준 [Agent Protocol](https://agentprotocol.ai/) HTTP API로 노출하여, 외부 시스템(CI, 다른 에이전트 플랫폼, 자동화 작업)이 구현 세부 사항을 알 필요 없이 통일된 계약으로 "작업(task)"을 제출할 수 있게 한다.

## 언제 사용하는가

- 에이전트를 클라우드 함수처럼 원격으로 스케줄링하고 싶을 때.
- 기존 팀이 Agent Protocol 클라이언트를 사용 중이어서 바로 연결하고 싶을 때.
- Harness Agent를 Spring Boot 서비스에 임베드하면서 자동으로 노출되는 `/tasks` REST 엔드포인트를 원할 때.
- 다른 Harness 부모가 HTTP로 호출하는 [원격 서브에이전트](../../docs/harness/subagent.md#원격-서브에이전트)를 호스팅할 때.

## 프로토콜 계층

AgentScope는 서로 다른 신뢰/UX 경계에 대해 다른 프로토콜을 사용한다:

| 계층 | 역할 |
| --- | --- |
| **AG-UI** | 사용자 대면 채팅 UI 이벤트 스트림(브라우저 ↔ 앱) |
| **Agent Protocol** | 내부 원격 서브에이전트 / 작업 HTTP API(부모 harness ↔ 원격 에이전트 서비스) |
| **A2A** | 외부 에이전트 간(agent-to-agent) 상호운용(별도 확장; 이 원격 서브에이전트 스트리밍/HITL 작업의 일부가 아님) |

## 의존성 추가하기

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-agent-protocol</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 활성화

이 모듈은 Spring Boot 자동 구성(auto-configuration)으로 제공된다. Spring Boot 앱에서는:

1. `HarnessAgent` 빈(또는 커스텀 `AgentFactory`)을 제공한다.
2. `application.yml`에서 활성화한다:

```yaml
agentscope:
  agent-protocol:
    enabled: true
    # 선택 사항 — control-plane TaskRecord 디렉터리(실행 에이전트의 워크스페이스가 아님)
    # task-store-path: ${user.dir}/.agentscope/agent-protocol
```

그러면 `/tasks` REST 엔드포인트가 자동으로 등록된다.

### Control-plane과 실행 워크스페이스

`AgentProtocolTaskStore`는 전용 `ProtocolTaskRepository`를 통해 프로토콜 작업 메타데이터(submit / resume / snapshot을 위한 `TaskRecord`)를 영속화한다. 기본적으로 이 저장소는 `agentscope.agent-protocol.task-store-path`(`${user.dir}/.agentscope/agent-protocol`)를 루트로 하며, `agents/_agentscope_protocol/tasks/`라는 합성 버킷 아래에 위치한다.

이 경로는 각 `HarnessAgent` 자신의 `WorkspaceManager`(MEMORY, 세션, 스킬)와는 **독립적**이다. 멀티 에이전트 팩토리는 각 에이전트에 서로 다른 `.workspace(...)`를 부여할 수 있다. 프로토콜의 `task_id` 조회는 항상 control-plane 저장소를 거친다.

기본값을 재정의하기 위해 직접 만든 `ProtocolTaskRepository` 빈을 제공할 수 있다. `AgentProtocolTaskStore`는 반드시 `ProtocolTaskRepository`만으로 구성한다 — 실행 에이전트의 `WorkspaceManager`를 전달하지 않는다.

## 동시 실행

에이전트는 호출 사이에 상태를 갖지 않는다 — 싱글턴이 여러 동시 작업을 처리한다. 각 작업은 `RuntimeContext`를 통해 고유한 `(userId, sessionId)`를 가지므로 상태가 완전히 격리된다:

```java
@Bean
public HarnessAgent harnessAgent() {
    return HarnessAgent.builder()
            .name("protocol-agent")
            .model("dashscope:qwen-plus")
            .build();
}
```

동일한 세션에 대한 동시 요청은 자동으로 직렬화되며, 서로 다른 세션은 병렬로 실행된다.

## 에이전트 선택(`AgentFactory`)

어떤 에이전트가 작업을 실행할지는 `AgentFactory` 빈이 결정한다. 별도로 제공하지 않으면 기본 팩토리는 모든 작업에 대해 단일 `HarnessAgent` 빈을 반환한다.

`agent_id`, 테넌트, 혹은 임의의 커스텀 제출 컨텍스트 키로 라우팅하려면 직접 빈을 정의한다:

```java
@Bean
AgentFactory agentFactory(Map<String, HarnessAgent> agentsByName) {
    return request -> {
        String tenant = request.contextString("tenant");
        log.info("task {} agent_id={} tenant={} resume={}",
                request.taskId(), request.agentId(), tenant, request.resume());
        return agentsByName.getOrDefault(request.agentId(), agentsByName.get("default"));
    };
}
```

`AgentRequest` 필드:

| 필드 | 설명 |
| --- | --- |
| `taskId()` | 작업 식별자이자 에이전트 세션 id |
| `agentId()` | 제출에서 요청된 `agent_id` |
| `input()` | 사용자 입력. resume 실행에서는 비어 있음 |
| `userId()` / `parentSessionId()` | `context.user_id` / `context.parent_session_id`에서 파싱됨 |
| `resume()` | 도구 확인을 기다리던 작업을 다시 실행할 때 `true` |
| `context()` | 커스텀 키를 포함하여 수신된 그대로의 제출 `context` 맵. `contextValue(key)` / `contextString(key)`는 편의 접근자 |
| `attributes()` | `context.attributes` 맵만을 의미하며, `attributeString(key)`는 편의 접근자 |

팩토리는 실행당 한 번씩 호출된다 — submit 시 한 번, 그리고 매 `/resume`마다 다시 — 원래의 제출 컨텍스트와 함께 호출되므로, HITL 일시 중지 구간에서도 라우팅 결정이 안정적으로 유지된다.

작업이 동시에 실행되는 경우에는 호출마다 별개의 인스턴스(예: prototype 범위의 빈)를 반환한다. `null`을 반환하면 작업이 오류 상태로 실패한다.

## 컨텍스트 속성

호출자는 `context.attributes`에 자신의 데이터를 전달하며, 이는 중첩되어 있어 프로토콜 자체의 컨텍스트 필드와 절대 섞이지 않는다. 속성은 `AgentFactory`에서 보이는 것 외에도, 실행 중인 에이전트의 `RuntimeContext`를 통해 전달된다.

속성은 `AgentProtocolConstants.RUNTIME_CONTEXT_ATTRIBUTES_KEY`(`agentprotocol.context.attributes`)라는 **단일 네임스페이스 키 아래의 하나의 맵**으로 도착한다:

```java
Map<String, Object> attributes = ctx.get(AgentProtocolConstants.RUNTIME_CONTEXT_ATTRIBUTES_KEY);
String tenant = attributes != null ? (String) attributes.get("tenant") : null;
```

속성이 최상위 키로 기록되지 않고 네임스페이스로 묶이는 이유는, 프레임워크 자체가 몇몇 일반 런타임 컨텍스트 키를 읽기 때문이다 — `agentId`는 비동기 도구 웨이크업 라우팅을 담당하고, `outboundAddress`는 게이트웨이 응답 주소를 전달한다. 호출자가 속성 이름을 이들 중 하나와 동일하게 지으면 에이전트의 동작이 바뀔 수 있다. 속성은 시스템 프롬프트에 절대 렌더링되지 않으므로, 모델이 보는 내용에는 영향을 주지 않는다.

### 속성을 고유 키로 승격하기

도구가 `ctx.get("tenant")`와 같은 일반 키를 기대하거나, 속성을 타입이 지정된 값으로 바꾸고 싶을 때는 `RuntimeContextCustomizer` 빈을 등록한다. `RuntimeContextCustomizer.flatten`은 명시적인 allow-list를 복사하며, 프레임워크 예약 이름은 조용히 건너뛴다:

```java
@Bean
RuntimeContextCustomizer promoteTenantKeys() {
    return RuntimeContextCustomizer.flatten("tenant", "ticket_id");
}

@Bean
RuntimeContextCustomizer tenantContext(TenantService tenants) {
    return (request, builder) -> {
        String tenant = request.attributeString("tenant");
        if (tenant != null) {
            builder.put(TenantInfo.class, tenants.load(tenant));
        }
    };
}
```

모든 customizer 빈은 네임스페이스 주입 이후 `@Order` 순서에 따라 매 실행마다 적용된다 — 나중에 적용되는 customizer가 앞선 것을 재정의한다. 직접 작성한 customizer는 신뢰되며 예약된 키를 포함해 어떤 키든 기록할 수 있다.

### 부모 에이전트에서 속성 전달하기

원격 서브에이전트에 위임하는 부모 에이전트는 두 가지 방식으로 속성을 제공하며, 병합 시에는 호출별 속성이 우선한다:

```java
// 정적, 서브에이전트별
SubagentDeclaration.builder()
        .name("researcher")
        .description("Remote researcher")
        .url("http://remote:8080")
        .remoteContextAttributes(Map.of("region", "cn"))
        .build();

// 호출별, 부모의 RuntimeContext에서
RuntimeContext.builder()
        .sessionId("sess-1")
        .put(AgentSpawnTool.CTX_REMOTE_CONTEXT_ATTRIBUTES, Map.of("tenant", "acme"))
        .build();
```

값은 JSON으로 직렬화 가능해야 한다.

## 엔드포인트

### 작업 제출하기

`POST /tasks`

```json
{
  "task_id": "task_123",
  "agent_id": "researcher",
  "input": "최신 릴리스 노트를 요약해줘",
  "context": {
    "user_id": "u-1",
    "parent_session_id": "sess-parent",
    "stream": true,
    "detail": "full",
    "deny_rules": [
      {
        "tool_name": "bash",
        "behavior": "DENY",
        "source": "parent"
      }
    ],
    "attributes": {
      "tenant": "acme",
      "ticket_id": "INC-1001"
    }
  }
}
```

선택적인 `context` 필드:

| 필드 | 설명 |
| --- | --- |
| `user_id` | 원격 에이전트의 `RuntimeContext`로 전달됨 |
| `parent_session_id` | 부모 세션 식별자(트레이싱/상관관계용) |
| `stream` | 호출자가 SSE 이벤트를 소비할 의도가 있는지 여부 |
| `detail` | `status`(기본값), `full` 또는 `verbose` — [스트림 상세 수준](#스트림-상세-수준) 참고 |
| `deny_rules` | 원격 측에서 적용할 부모의 DENY 권한 규칙 |
| `attributes` | 라우팅 및 실행의 `RuntimeContext`를 위한 호출자 정의 키/값. [컨텍스트 속성](#컨텍스트-속성) 참고 |

성공 시 응답: `{ "task_id", "status": "pending" }`.

### 폴링 / 대기 / 취소

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/tasks/{taskId}` | 스냅샷(`status`, 결과, 오류, 대기 중인 확인) |
| `GET` | `/tasks/{taskId}/wait?timeout_seconds=7200` | 종료 상태(또는 `awaiting_confirm`)가 될 때까지 블록 |
| `POST` | `/tasks/{taskId}/cancel` | 취소 요청 |

원격 에이전트가 도구 확인을 기다리는 동안, 스냅샷은 `status: awaiting_confirm`을 보고하지만 저장된 `TaskStatus`는 `RUNNING`으로 유지되어 부모 barrier가 계속 대기한다.

### 이벤트 스트리밍(SSE)

`GET /tasks/{taskId}/events`

에이전트 진행 상황에 대한 Server-Sent Events. `agentscope.agent-protocol.streaming-enabled=true`(기본값)가 필요하다.

재연결 / 재개:

- 쿼리 파라미터 `from_seq` — 이 시퀀스 번호 이후부터 시작
- 헤더 `Last-Event-ID` — `from_seq`가 생략된 경우 사용(동일한 의미)

각 SSE 메시지는 이벤트 시퀀스를 `id`로, 원격 이벤트 타입을 `event`로, JSON 본문을 `data`로 사용한다.

#### 스트림 상세 수준

제출 시의 `context.detail`이 실행 중 얼마만큼이 구독자에게 도달할지를 결정한다. 각 수준은 이전 수준의 상위 집합이다:

| `detail` | 스트림의 이벤트 타입 |
|----------|---------------------------|
| `status` (default) | `RUN_STARTED`, `RUN_FINISHED`, `RUN_ERROR`, `TOOL_CALL_START`, `TOOL_CALL_END`, `TOOL_RESULT`, `REQUIRE_CONFIRM`, `STATUS` |
| `full` | 위에 더해 `TEXT_DELTA`, `THINKING_DELTA` |
| `verbose` | 위에 더해 `AGENT_EVENT` — 블록 경계, 도구 인자 및 도구 출력 델타, 토큰 사용량을 포함한 모델 호출, 에이전트 결과, 커스텀 이벤트를 포함한 나머지 모든 에이전트 이벤트 |

`verbose`만이 에이전트 자체의 이벤트 스트림을 완전히 재현한다. 인식되지 않는 값은 `status`로 처리된다.

모든 이벤트 본문은 타입별 필드 외에도 두 개의 필드를 추가로 포함한다:

| 필드 | 의미 |
|-------|---------|
| `eventType` | 원본 `AgentEventType`의 이름(예: `MODEL_CALL_END`). 클라이언트가 `payload`를 파싱하지 않고도 필터링하거나 로깅할 수 있게 한다 |
| `payload` | 원본 `AgentEvent`를 완전히 직렬화한 것. id, timestamp, 메타데이터가 그대로 보존된 원본 이벤트를 복원하며, `AGENT_EVENT`의 유일한 표현이다 |

두 필드 모두 추가적인(additive) 것이다: 이를 무시하는 클라이언트는 이전과 동일하게 평면 필드(`text`, `toolCallId`, `status` 등)를 계속 읽을 수 있고, `AGENT_EVENT`보다 이전 버전의 클라이언트는 해당 메시지를 그냥 건너뛴다.

### HITL 이후 재개하기

`POST /tasks/{taskId}/resume`

```json
{
  "decisions": [
    { "toolCallId": "call-1", "approved": true },
    { "toolCallId": "call-2", "approved": false }
  ]
}
```

`tool_call_id`도 `toolCallId`의 별칭으로 허용된다. `agentscope.agent-protocol.hitl-enabled=true`(기본값)가 필요하다. 성공 시 `{ "task_id", "status": "running" }`을 반환한다.

원격 HITL이 호출하는 부모 harness와 어떻게 상호작용하는지는 [원격 인가](../../docs/harness/subagent.md#원격-인가)에 문서화되어 있다.

## 설정

| 속성 | 타입 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `agentscope.agent-protocol.enabled` | boolean | `false` | `/tasks` REST 엔드포인트를 등록할지 여부 |
| `agentscope.agent-protocol.streaming-enabled` | boolean | `true` | SSE `GET /tasks/{id}/events`를 노출 |
| `agentscope.agent-protocol.hitl-enabled` | boolean | `true` | 도구 확인을 위해 작업을 일시 중지하고 `/resume`을 허용 |
| `agentscope.agent-protocol.sse-replay-buffer-size` | int | `256` | 늦게 구독하는 SSE 구독자를 위한 작업별 재생 버퍼 |
| `agentscope.agent-protocol.sse-timeout-ms` | long | `10800000` | 최대 SSE 구독 지속 시간(ms) |

예시:

```yaml
agentscope:
  agent-protocol:
    enabled: true
    streaming-enabled: true
    hitl-enabled: true
    sse-replay-buffer-size: 256
    sse-timeout-ms: 10800000
```

> `enabled`이 `false`(기본값)이면 의존성은 비활성 상태를 유지한다 — REST 엔드포인트가 노출되지 않으므로 안전하게 배포할 수 있다.

## 워크스페이스 통합

각 작업은 `WorkspaceManager`로부터 격리된 워크스페이스를 받는다. 작업이 완료되면 워크스페이스 내 파일과 로그가 표준 Agent Protocol 엔드포인트를 통해 노출되어 외부 클라이언트가 아티팩트를 가져올 수 있다.
