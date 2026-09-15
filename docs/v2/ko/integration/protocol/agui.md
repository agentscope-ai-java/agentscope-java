# AG-UI

## 호환성 참고 사항

`agentscope-extensions-agui`는 AgentScope v2 `AgentEvent` 스트림을 [AG-UI Protocol](https://github.com/ag-ui-protocol/ag-ui) 이벤트로 변환하여, 프런트엔드 UI가 텍스트, reasoning, 도구 호출, 상태, 커스텀 이벤트, 토큰 사용량, HITL 인터럽트를 포함한 에이전트 실행을 실시간으로 렌더링할 수 있게 한다.

`RUN_ERROR`와 `RUN_FINISHED`는 상호 배타적인 종료 이벤트다. 레거시 `RUN_ERROR` + `RUN_FINISHED` 시퀀스가 여전히 필요한 경우에만 `emitRunFinishedAfterError=true`로 설정한다.

`AguiMessage.content`는 타입이 지정된 메시지 콘텐츠로 표현된다. 텍스트 전용 코드 경로에서는 `getTextContent()`를 사용한다.

멀티모달 입력은 지원되지만, 문서(document) 타입은 아직 지원되지 않는다.

`AguiMessageConverter.toAguiMessage()`는 현재 텍스트와 도구 호출 필드만 보존한다. 이미지, 오디오, 비디오, 문서 콘텐츠 블록은 AG-UI 메시지 콘텐츠로 다시 직렬화되지 않는다.

## 언제 사용하는가

- AgentScope 에이전트를 AG-UI 호환 프런트엔드나 커스텀 채팅 UI에 연결해야 할 때.
- `RUN_*`, `TEXT_MESSAGE_*`, `TOOL_CALL_*`, `CUSTOM` 등 관련 AG-UI 이벤트를 SSE로 스트리밍해야 할 때.
- 프런트엔드 도구, 사용자 승인 인터럽트, 런타임 컨텍스트 전파, 또는 커스텀 이벤트 변환 확장이 필요할 때.

## 의존성 추가하기

어댑터를 수동으로 사용하려면 다음을 추가한다:

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-agui</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

Spring Boot 애플리케이션은 스타터를 사용할 수 있다:

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-agui-spring-boot-starter</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 빠른 시작

```java
import io.agentscope.core.agui.adapter.AguiAdapterConfig;
import io.agentscope.core.agui.adapter.AguiAgentAdapter;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.agui.model.RunAgentInput;
import reactor.core.publisher.Flux;

AguiAdapterConfig config = AguiAdapterConfig.builder()
    .enableReasoning(true)
    .emitTokenUsage(true)
    .runTimeout(Duration.ofMinutes(5))
    .build();

AguiAgentAdapter adapter = new AguiAgentAdapter(agent, config);

// SSE를 통해 프런트엔드로 전달할 이벤트
Flux<AguiEvent> events = adapter.run(runAgentInput);
```

프런트엔드는 `threadId`, `runId`, `messages`, `tools`, `state` 등 관련 필드를 포함한 `RunAgentInput`을 제공한다. 어댑터는 AG-UI 메시지를 AgentScope `Msg` 객체로 변환하고, v2 `streamEvents(...)`를 호출한 뒤, 각 `AgentEvent`를 AG-UI 이벤트로 변환한다.

## 이벤트 매핑

v2 경로는 `AgentEvent`를 소비한다. 내장 컨버터가 의미론적 매핑을 처리하며, 매핑되지 않은 이벤트는 공식 `RAW` 이벤트로 폴백된다.

| AgentScope 이벤트 / 콘텐츠             | AG-UI 이벤트 |
|----------------------------------------| --- |
| `AgentStartEvent`                      | `RUN_STARTED` |
| `AgentEndEvent`                        | `RUN_FINISHED` |
| 텍스트                              | `TEXT_MESSAGE_START` / `TEXT_MESSAGE_CONTENT` / `TEXT_MESSAGE_END` |
| Thinking (`enableReasoning=true`) | `REASONING_MESSAGE_START` / `REASONING_MESSAGE_CONTENT` / `REASONING_MESSAGE_END` |
| 도구 호출 및 인자 델타          | `TOOL_CALL_START` / `TOOL_CALL_ARGS` / `TOOL_CALL_END` |
| 도구 결과                            | `TOOL_CALL_RESULT` |
| `CustomEvent`                          | `CUSTOM` |
| 토큰 사용량 (`emitTokenUsage=true`)    | `CUSTOM`, `name=token_usage` |
| 매핑되지 않은 `AgentEvent`                  | `RAW`, 공식 `event`와 `source` 필드 포함 |

일반적인 `RUN_STARTED`와 `RUN_FINISHED` 이벤트는 상위 `AgentStartEvent`와 `AgentEndEvent`에 의해 발생한다. 정상 스트림이 상위 `AgentEndEvent` 없이 완료되면 어댑터는 `RUN_FINISHED`를 합성하지 않는다. 오류가 발생하면 어댑터는 `timestamp`가 포함된 `RUN_ERROR`를 발행한다. `RUN_ERROR`와 `RUN_FINISHED`는 상호 배타적인 종료 이벤트다. 오류 이후에도 종료 이벤트를 기대하는 레거시 클라이언트를 위해서만 `emitRunFinishedAfterError=true`(또는 Spring Boot의 `agentscope.agui.emit-run-finished-after-error=true`)를 설정한다.

## 서브에이전트 이벤트

기본값(`emitSubagentEventsAsNative=false`)에서는 `source`가 null이 아닌 AgentEvent(자식/원격 서브에이전트 이벤트)가 네이티브 `TEXT_MESSAGE_*` / `RUN_*` / 도구 호출 이벤트로 매핑되지 **않는다**. 이들은 부모 실행 라이프사이클이나 텍스트 스트림을 오염시키지 않도록 `subagent.*` 네임스페이스 아래의 AG-UI `CUSTOM` 이벤트가 된다:

| CUSTOM `name` | 일반적인 AgentEvent |
| --- | --- |
| `subagent.lifecycle` | `AgentStartEvent` / `AgentEndEvent` |
| `subagent.text` | `TextBlockDeltaEvent` |
| `subagent.thinking` | `ThinkingBlockDeltaEvent` |
| `subagent.tool_call` | `ToolCallStartEvent` / `ToolCallEndEvent` |
| `subagent.tool_result` | `ToolResultEndEvent` |
| `subagent.require_confirm` | `RequireUserConfirmEvent` |

각 페이로드에는 최소한 `source`와 `type`이 포함된다(그 외 `delta`나 `toolCallId` 같은 타입별 필드도 포함될 수 있다).

자식 이벤트가 부모와 동일한 네이티브 컨버터를 사용하던 이전 동작으로 되돌리려면:

```java
AguiAdapterConfig config = AguiAdapterConfig.builder()
    .emitSubagentEventsAsNative(true)
    .build();
```

## AG-UI 기본 이벤트 속성

모든 `AguiEvent`는 공식 기본 이벤트 속성인 선택적 `timestamp`와 `rawEvent`를 지원한다.

기본 설정에서는 `BaseEventPropertiesEnricher`가 활성화되지 않으므로, 프레임워크는 기본적으로 모든 이벤트에 `timestamp`를 추가하지 않으며 내부 `AgentEvent` 객체를 `rawEvent`로 노출하지도 않는다. timestamp를 채우고 싶다면 기본 enricher를 명시적으로 활성화한다:

```java
AguiAdapterConfig config = AguiAdapterConfig.builder()
    .baseEventPropertiesEnricherEnabled(true)
    .build();
```

`BaseEventPropertiesEnricher`는 누락된 `timestamp`만 채운다. 기존 timestamp는 보존하며 `rawEvent`는 기록하지 않는다. `rawEvent`를 노출하려면 커스텀 `AguiEventEnricher`를 등록한다.

Spring Boot 스타터는 기본 base properties enricher를 암묵적으로 활성화하지 않는다. 이 동작을 원한다면 `BaseEventPropertiesEnricher` 빈이나 직접 만든 `AguiEventEnricher` 빈을 노출한다.

## 커스텀 컨버터와 Enricher

`AgentEventConverter`는 의미론적 매핑을 확장하거나 재정의한다. 동일한 `AgentEvent` 타입에 대해 사용자 컨버터가 내장 컨버터를 재정의한다.

```java
@Bean
AgentEventConverter customEventConverter() {
    return new AgentEventConverter() {
        @Override
        public Set<Class<? extends AgentEvent>> eventTypes() {
            return Set.of(CustomEvent.class);
        }

        @Override
        public void convert(AgentEvent event, AguiStreamContext context) {
            CustomEvent customEvent = (CustomEvent) event;
            context.emit(new AguiEvent.Custom(
                context.getThreadId(),
                context.getRunId(),
                customEvent.getName(),
                customEvent.getValue()));
        }
    };
}
```

`AguiEventEnricher`는 변환 이후에 실행된다. `timestamp`, `rawEvent`, 트레이싱 필드 등 횡단 관심사나 그 밖의 이벤트 장식을 위한 것이다. 컨버터의 출력을 수정, 추가, 필터링할 수 있다.

```java
@Bean
AguiEventEnricher timestampEnricher() {
    return (source, events, context) -> events.stream()
        .map(event -> AguiEvents.withBaseProperties(
            event,
            event.timestamp() != null ? event.timestamp() : System.currentTimeMillis(),
            event.rawEvent()))
        .toList();
}
```

Spring Boot 스타터는 `AgentEventConverter`와 `AguiEventEnricher` 빈을 자동으로 수집하고 `orderedStream()`을 사용하여 `@Order` / `Ordered`를 존중한다.

## 토큰 사용량

토큰 사용량은 기본적으로 비활성화되어 있다. 수동 설정:

```java
AguiAdapterConfig config = AguiAdapterConfig.builder()
    .emitTokenUsage(true)
    .build();
```

Spring Boot 설정:

```yaml
agentscope:
  agui:
    emit-token-usage: true
```

활성화하면 usage가 있는 모든 `ModelCallEndEvent`가 `CUSTOM` 이벤트를 발행한다. `delta`는 현재 모델 호출의 사용량이고, `cumulative`는 현재 AG-UI 실행 내에서 누적된 사용량이다.

## RuntimeContext

`AguiAgentAdapter.run(input, runtimeContext)`는 호출자가 제공한 `RuntimeContext`를 받는다. 어댑터는 먼저 호출자의 컨텍스트를 복사한 다음, 필요한 기본값이 유실되지 않도록 AG-UI 프로토콜 메타데이터를 적용한다.

| RuntimeContext 항목 | 출처 |
| --- | --- |
| `sessionId` | `RunAgentInput.threadId` |
| `RunAgentInput.class` | 전체 `RunAgentInput` |
| `agui.threadId` | `RunAgentInput.threadId` |
| `agui.runId` | `RunAgentInput.runId` |
| `agui.messages` | `RunAgentInput.messages` |
| `agui.tools` | `RunAgentInput.tools` |
| `agui.context` | `RunAgentInput.context` |
| `agui.state` | `RunAgentInput.state` |
| `agui.forwardedProps` | `RunAgentInput.forwardedProps` |
| `agui.resume` | `RunAgentInput.resume` |

`sessionId`는 항상 `threadId`에서 오기 때문에, 동일한 에이전트 인스턴스라도 AG-UI 스레드 간에는 격리된 상태로 유지된다.

## Spring Boot 통합

스타터는 MVC 또는 WebFlux 엔드포인트를 자동으로 등록한다. 공통 설정:

```yaml
agentscope:
  agui:
    path-prefix: /agui
    cors-enabled: true
    run-timeout: 10m
    default-agent-id: default
    enable-path-routing: true
    agent-id-header: X-Agent-Id
    emit-state-events: true
    emit-tool-call-args: true
    emit-token-usage: false
    enable-reasoning: false
    emit-run-finished-after-error: false
    server-side-memory: false
    interrupt-on-disconnect: true
```

`interrupt-on-disconnect`는 MVC/WebFlux SSE 연결이 닫히거나, 타임아웃되거나, 이벤트 전송 중 실패했을 때 에이전트 실행을 인터럽트할지 여부를 제어한다. 하위 호환성을 위해 기본값은 `true`다. `false`로 설정하면 클라이언트가 연결을 끊은 뒤에도 에이전트가 계속 실행되도록 할 수 있다. 단, 연결이 닫혀 있는 동안 발생한 이벤트는 스타터가 재생하지 않는다.

다음 빈으로 기본 체인을 확장할 수 있다:

- `AgentEventConverter`: 커스텀 이벤트 의미론적 매핑.
- `AguiEventEnricher`: 횡단 관심사 이벤트 보강.
- `AguiRuntimeContextResolver`: 요청 범위(request-scoped) `RuntimeContext` 주입.
- `AguiAgentAdapterFactory`: 기본 `AguiAgentAdapter` 생성 방식을 대체.

`AguiRuntimeContextResolver`는 transport, 경로의 agent id, 헤더의 agent id, 헤더, 쿼리 파라미터, 네이티브 Web request를 읽을 수 있다.

```java
@Bean
AguiRuntimeContextResolver runtimeContextResolver() {
    return request -> RuntimeContext.builder()
        .put("tenantId", request.firstHeader("X-Tenant-Id"))
        .put("traceId", request.firstHeader("X-Trace-Id"))
        .build();
}
```

`forwardedProps`는 클라이언트 요청 본문에서 오며 UI 옵션이나 프런트엔드 컨텍스트에 적합하다. 이를 신뢰할 수 있는 신원 출처로 취급해서는 안 된다. 서버 측 사용자 신원은 인증이나 서버 측 리졸버에서 가져와야 한다.

## 프런트엔드 도구와 병합 모드

AG-UI 프런트엔드는 `RunAgentInput.tools`를 통해 도구 스키마를 전달할 수 있다. 어댑터는 한 번의 실행이 시작될 때 이러한 도구를 에이전트 툴킷에 주입하고, 실행이 완료되거나 취소된 후에는 정리한다.

| `ToolMergeMode` | 동작 |
| --- | --- |
| `FRONTEND_ONLY` | 프런트엔드가 제공한 도구만 사용하고 기존 에이전트 도구는 일시적으로 숨긴다 |
| `AGENT_ONLY` | 프런트엔드가 제공한 도구를 무시하고 에이전트 툴킷만 사용한다 |
| `MERGE_FRONTEND_PRIORITY` | 양쪽을 병합하며, 이름이 충돌하면 프런트엔드 도구가 우선한다 |

기본값은 `MERGE_FRONTEND_PRIORITY`다. 주입은 실행 범위로 한정되며 에이전트 툴킷을 영구적으로 변경하지 않는다.

## HITL 인터럽트

실행이 도구 결정을 위해 일시 중지되면 AG-UI 어댑터는 `RUN_FINISHED`에 공식 인터럽트 결과(outcome)를 발행한다. AgentScope Java에는 두 가지 내장 도구 호출 인터럽트 경로가 있다:

- **도구 중단(suspension) / 외부 실행**: 중단된 `ToolResultBlock`이 `tool_call` 인터럽트가 되며, `ToolResultBlock`으로 재개된다.
- **권한 확인(permission confirmation)**: `RequireUserConfirmEvent`가 AgentScope 메타데이터를 포함한 `tool_call` 인터럽트가 되며, `ConfirmResult`로 재개된다.

인터럽트가 특정 `toolCallId`에 결합되어 있으므로 둘 다 공식 AG-UI `reason: "tool_call"`을 사용한다. 이러한 도구 결합 승인에는 `reason: "confirmation"`을 사용하지 않는다.

```json
{
  "type": "RUN_FINISHED",
  "outcome": {
    "type": "interrupt",
    "interrupts": [
      {
        "id": "reply-1:call-1",
        "reason": "tool_call",
        "toolCallId": "call-1",
        "message": "이 도구를 실행하기 전에 승인이 필요합니다",
        "responseSchema": {
          "type": "object",
          "properties": {
            "approved": { "type": "boolean" },
            "editedArgs": {
              "type": "object",
              "description": "도구 인자의 전체 교체본입니다. 병합되지 않습니다."
            }
          },
          "required": ["approved"]
        },
        "metadata": {
          "agentscope.interruptKind": "permission_confirm",
          "toolName": "request_approval",
          "toolInput": { "path": "/tmp/report.txt" },
          "toolContent": "{\"path\":\"/tmp/report.txt\"}",
          "replyId": "reply-1"
        }
      }
    ]
  }
}
```

프런트엔드는 승인 또는 외부 실행 UI를 보여줄 수 있다. 사용자가 조작을 마치면 동일한 `threadId`에 대한 다음 `runAgent` 요청에 공식 `resume[]` 필드를 담아 전송한다:

```json
{
  "threadId": "thread-1",
  "runId": "run-2",
  "messages": [],
  "resume": [
    {
      "interruptId": "reply-1:call-1",
      "status": "resolved",
      "payload": {
        "approved": true,
        "editedArgs": {
          "path": "/tmp/reviewed-report.txt"
        }
      }
    }
  ]
}
```

`status`는 공식 값인 `resolved`와 `cancelled`를 지원한다. 사용자가 도구 요청을 거부하는 일반적인 승인 시나리오에서는 `resolved`를 사용하고 비즈니스 결정을 `payload`에 표현하는 것이 좋다(예: `{ "approved": false }`). 인터럽트 자체가 취소된 경우에는 `cancelled`를 사용한다.

권한 확인의 경우, 도구를 승인하려면 `payload.approved`가 불리언 `true`여야 한다. 값이 없거나 불리언이 아니거나 `false`이면 거부로 처리된다. `payload.editedArgs`가 존재할 경우 반드시 JSON 객체여야 하며, 원본 도구 인자의 **전체 교체**이지 부분 병합이 아니다. AgentScope Java는 `editedArgs`로부터 `ToolUseBlock.input`과 원시 JSON `ToolUseBlock.content`를 모두 재구성하므로, 승인된 도구는 수정된 인자로 실행된다.

프런트엔드는 `resume[]`에서 `metadata`를 그대로 되돌려 보낼 필요가 없으며, `interruptId`, `status`, `payload`만 전송하면 된다. Spring `AguiRequestProcessor` 진입점을 통해 AgentScope Java는 최신 `RUN_FINISHED.outcome.interrupts[]`를 서버 측에 기록하고, 다음 `resume[]`이 열려 있는 모든 인터럽트를 포함하는지 검증하며, 원래의 인터럽트를 변환을 위해 어댑터로 전달한다.

## 예제 프로젝트

전체 예제는 [agentscope-examples/agui](https://github.com/agentscope-ai/agentscope-java/tree/main/agentscope-examples/agui)에서 확인할 수 있다:

```bash
export DASHSCOPE_API_KEY=your-key
cd agentscope-examples/agui
mvn spring-boot:run
```

시작한 뒤 http://localhost:8080 에 접속한다. 이 예제는 멀티 에이전트 라우팅, 커스텀 컨버터, 커스텀 enricher, 토큰 사용량, HITL 인터럽트를 보여준다.
