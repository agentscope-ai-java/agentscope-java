---
title: "메시지 & 이벤트"
description: "에이전트 통신과 스트리밍을 위한 핵심 데이터 추상화"
---

메시지와 이벤트는 AgentScope에서 두 가지 근본적인 데이터 구조입니다.

- **메시지** — 에이전트 간 통신과 영속화의 기본 단위입니다. 각 `Msg`는 하나의 완전한 대화 턴이며, 컨텍스트에 저장되고 에이전트 간에 전달됩니다.
- **이벤트** — 프런트엔드 상호작용과 스트리밍의 기본 단위입니다. 이벤트는 점진적인 진행 상황 업데이트(텍스트 토큰, 도구 호출 조각, 권한 요청 등)를 담고 있으며, 실시간 UI와 human-in-the-loop 흐름을 구동합니다.

하나의 `call`이 방출하는 이벤트 시퀀스는 항상 정확히 하나의 어시스턴트 `Msg`로 응축되며, 전체 메시지 상태가 이벤트 스트림만으로도 재구성될 수 있음을 보장합니다.

## 메시지

`Msg`(`io.agentscope.core.message`)는 사용자 입력, 에이전트 응답, 또는 시스템 지침 등 대화의 한 턴을 나타내며, 콘텐츠는 타입이 지정된 `ContentBlock`의 순서 있는 목록으로 모델링됩니다.

:::{tip}
하나의 어시스턴트 `Msg`는 하나의 완전한 `call` 사이클(최종 응답에 이르기까지의 여러 추론 + 행동 반복)에 대응합니다.
:::

### 구조

`Msg`의 핵심 필드(게터를 통해):

| 메서드 | 타입 | 설명 |
|--------|------|-------------|
| `getId()` | `String` | 고유 메시지 식별자 |
| `getName()` | `String` | 발신자 이름 (null 가능) |
| `getRole()` | `MsgRole` | `USER` / `ASSISTANT` / `SYSTEM` / `TOOL` |
| `getContent()` | `List<ContentBlock>` | 콘텐츠 블록의 순서 있는 목록 (불변) |
| `getMetadata()` | `Map<String, Object>` | 임의의 key/value 메타데이터 |
| `getTimestamp()` | `String` | 생성 시각 (`yyyy-MM-dd HH:mm:ss.SSS`) |
| `getUsage()` | `ChatUsage` | 토큰 사용량 (어시스턴트 메시지에만 해당) |
| `getGenerateReason()` | `GenerateReason` | 종료 이유: `MODEL_STOP` / `TOOL_SUSPENDED` / `REASONING_STOP_REQUESTED` / `ACTING_STOP_REQUESTED` / `ALL_TOOLS_DENIED` / `INTERRUPTED` / `MAX_ITERATIONS` |

### 콘텐츠 블록

메시지 콘텐츠는 타입이 지정된 블록들로 구성되며, 각 블록은 하나의 정보 유형을 나타냅니다. 블록 클래스는 `io.agentscope.core.message`에 있습니다:

| 블록 | 설명 | 허용되는 위치 |
|-------|-------------|-----------|
| `TextBlock` | 일반 텍스트 콘텐츠 | USER, ASSISTANT, SYSTEM |
| `DataBlock` | base64 또는 URL을 통한 바이너리 데이터 (이미지 / 오디오 / 비디오) — 레거시 ImageBlock / AudioBlock / VideoBlock을 통합함 | USER, ASSISTANT |
| `ImageBlock` / `AudioBlock` / `VideoBlock` | 레거시 구체 미디어 블록 (여전히 지원되지만, 새 코드는 `DataBlock`을 선호해야 함) | USER |
| `ThinkingBlock` | 모델의 추론 / 사고 과정(chain of thought) | ASSISTANT |
| `ToolUseBlock` | 도구 호출: `id` / `name` / `input` / `state` (`ToolCallState`) | ASSISTANT |
| `ToolResultBlock` | `state`(`ToolResultState`)를 가진 도구 결과 | ASSISTANT |
| `HintBlock` | 사용자 컨텍스트로서 루프에 주입되는 지침 | ASSISTANT |

:::{note}
역할 제약은 생성 시점에 강제됩니다: `USER`는 text/data/image/audio/video 블록만 허용합니다; `SYSTEM`은 `TextBlock`만 허용합니다; `ASSISTANT`는 모든 블록 타입을 허용합니다.
:::

### 메시지 생성

역할이 고정된 서브클래스(`io.agentscope.core.message.UserMessage` / `AssistantMessage` / `SystemMessage` / `ToolResultMessage`)는 편리한 생성자를 제공합니다. `content`가 일반 문자열이면 자동으로 `TextBlock`으로 감싸집니다.

```java
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.SystemMessage;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;

// 사용자 메시지 — 텍스트만
UserMessage userText = new UserMessage("user", "What's in this image?");

// 멀티모달 사용자 메시지
UserMessage userMulti =
        new UserMessage(
                "user",
                TextBlock.builder().text("Describe this image:").build(),
                DataBlock.builder()
                        .source(Base64Source.builder()
                                .data("...")
                                .mediaType("image/png")
                                .build())
                        .build());

// 시스템 메시지 — 텍스트만
SystemMessage systemMsg = new SystemMessage("system", "You are a helpful assistant.");

// 어시스턴트 메시지 — 모든 블록 타입 허용
AssistantMessage assistantMsg = new AssistantMessage("agent", "Here's the result...");
```

더 많은 선택적 필드(`metadata`, `timestamp`, `usage`, `generateReason`)를 위해서는 각 서브클래스의 `builder()`를 사용하세요:

```java
UserMessage msg =
        UserMessage.builder()
                .name("user")
                .textContent("Hello")
                .build();
```

### 콘텐츠 접근

`Msg`는 특정 블록 타입을 추출하기 위한 헬퍼를 제공합니다:

| 메서드 | 반환값 |
|--------|---------|
| `getTextContent()` | 모든 `TextBlock`을 `\n`으로 이어 붙인 것; 없으면 빈 문자열 |
| `getContentBlocks(Class<T>)` | 타입으로 필터링된 목록 |
| `getFirstContentBlock(Class<T>)` | 처음 일치하는 블록, 없으면 null |
| `hasContentBlocks(Class<T>)` | 해당 타입의 블록이 존재하면 `true` |

```java
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.ToolResultBlock;

// 모든 텍스트 콘텐츠
String text = msg.getTextContent();

// 모든 도구 호출
List<ToolUseBlock> toolCalls = msg.getContentBlocks(ToolUseBlock.class);

// 도구 결과가 있는지 여부
if (msg.hasContentBlocks(ToolResultBlock.class)) {
    // ...
}
```

## 이벤트

이벤트는 메시지의 스트리밍 대응물입니다. 에이전트가 실행되는 동안, 점진적인 진행 상황 — 도착하는 텍스트 토큰, 조립되는 도구 호출, 스트리밍되어 돌아오는 결과 — 을 나타내는 `AgentEvent`(`io.agentscope.core.event`)의 시퀀스를 방출합니다. 각 이벤트는 가볍고 자체 완결적입니다.

### 이벤트 생명주기

모든 이벤트는 조립 중인 메시지와 연결하는 `getReplyId()`를 가집니다. 한 응답 내에서, `getBlockId()` 또는 `getToolCallId()`는 동일한 콘텐츠 블록 생명주기에 속하는 이벤트들의 상관관계 키(correlation key) 역할을 합니다. 이벤트는 **start → delta → end** 패턴을 따릅니다:

```{mermaid}
sequenceDiagram
    participant Client
    participant Agent

    Agent->>Client: AgentStartEvent

    rect rgba(100, 150, 255, 0.1)
        Note over Client,Agent: 추론 단계
        Agent->>Client: ModelCallStartEvent
        rect rgba(200, 200, 100, 0.1)
            Note over Client,Agent: TextBlock (blockId)
            Agent->>Client: TextBlockStartEvent
            Agent->>Client: TextBlockDeltaEvent (×N)
            Agent->>Client: TextBlockEndEvent
        end
        rect rgba(200, 200, 100, 0.1)
            Note over Client,Agent: DataBlock (blockId)
            Agent->>Client: DataBlockStartEvent
            Agent->>Client: DataBlockDeltaEvent (×N)
            Agent->>Client: DataBlockEndEvent
        end
        rect rgba(200, 200, 100, 0.1)
            Note over Client,Agent: ToolUseBlock (toolCallId)
            Agent->>Client: ToolCallStartEvent
            Agent->>Client: ToolCallDeltaEvent (×N)
            Agent->>Client: ToolCallEndEvent
        end
        Agent->>Client: ModelCallEndEvent
    end

    rect rgba(100, 255, 150, 0.1)
        Note over Client,Agent: 행동 단계
        rect rgba(200, 200, 100, 0.1)
            Note over Client,Agent: ToolResultBlock (toolCallId)
            Agent->>Client: ToolResultStartEvent
            Agent->>Client: ToolResultTextDeltaEvent (×N)
            Agent->>Client: ToolResultDataDeltaEvent (×N)
            Agent->>Client: ToolResultEndEvent
        end
    end

    Agent->>Client: AgentEndEvent
```

하나의 응답 안의 모든 이벤트는 동일한 `replyId`를 공유합니다. 한 응답 내에서, `blockId`는 텍스트/사고/데이터 블록 이벤트들을 함께 묶고; `toolCallId`는 도구 호출과 도구 결과를 묶습니다. `blockId`는 자신의 `replyId`에 범위가 한정되며, 전역적으로 유일하게 생성된 ID일 필요는 없습니다. 한 블록 타입이 하나의 응답 내에서 최대 하나의 생명주기만 가질 수 있는 경우, 구현체는 텍스트 블록에 대한 고정 키처럼 안정적인 타입 키를 사용할 수 있습니다.

### 이벤트 타입

모든 이벤트는 공통 메서드를 노출하는 `AgentEvent`(`io.agentscope.core.event`)를 확장합니다:

| 메서드 | 타입 | 설명 |
|--------|------|-------------|
| `getId()` | `String` | 고유 이벤트 식별자 |
| `getCreatedAt()` | `String` | ISO 8601 타임스탬프 |
| `getType()` | `AgentEventType` | 이벤트 타입 열거형 |
| `getSource()` | `String` | 이벤트를 발생시킨 에이전트를 식별하는 소스 경로. 최상위 에이전트 이벤트의 경우 `null`; 서브에이전트에서 전달된 이벤트의 경우 슬래시로 구분된 경로(예: `"main/researcher"`) |
| `getMetadata()` | `Map<String, Object>` | 선택적 key/value 모음. 원격 서브에이전트 포워딩은 이벤트가 작업(task) 기반일 때 `taskId`(`AgentEvent.METADATA_TASK_ID`)를 harness / Agent Protocol 작업 id로, `parentSessionId`(`AgentEvent.METADATA_PARENT_SESSION_ID`)를 부모 세션으로 설정합니다 |

이벤트는 아래에서 그룹별로 정리되어 있습니다; 별도 명시가 없는 한, 모든 이벤트는 조립 중인 메시지와 연결하는 `getReplyId()`도 가집니다.

  :::{dropdown} 생명주기 이벤트
**AgentStartEvent** — 에이전트가 새로운 응답을 시작합니다.

    | 메서드 | 타입 | 설명 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 응답 메시지 ID |
    | `getSessionId()` | `String` | 세션 ID |
    | `getName()` | `String` | 에이전트 이름 |
    | `getRole()` | `String` | 에이전트 역할 (기본값 `"assistant"`) |

    **AgentEndEvent** — 에이전트가 응답을 완료합니다.

    | 메서드 | 타입 | 설명 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 응답 메시지 ID |

    **ExceedMaxItersEvent** — 에이전트가 최대 추론-행동 반복 한도에 도달했습니다.

    | 메서드 | 타입 | 설명 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 응답 메시지 ID |

    **RequestStopEvent** — 미들웨어나 도구에 의해 발생한 조기 중단 요청.
:::

  :::{dropdown} 텍스트 스트리밍 이벤트
**TextBlockStartEvent** — 새로운 텍스트 블록이 시작됩니다.

    | 메서드 | 타입 | 설명 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 응답 메시지 ID |
    | `getBlockId()` | `String` | 현재 응답 내 텍스트 블록 상관관계 키 |

    **TextBlockDeltaEvent** — 점진적인 텍스트 콘텐츠가 도착합니다.

    | 메서드 | 타입 | 설명 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 응답 메시지 ID |
    | `getBlockId()` | `String` | 현재 응답 내 텍스트 블록 상관관계 키 |
    | `getDelta()` | `String` | 점진적인 텍스트 콘텐츠 |

    **TextBlockEndEvent** — 텍스트 블록이 완료됩니다.

    | 메서드 | 타입 | 설명 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 응답 메시지 ID |
    | `getBlockId()` | `String` | 현재 응답 내 텍스트 블록 상관관계 키 |
:::

  :::{dropdown} 사고 스트리밍 이벤트
**ThinkingBlockStartEvent / ThinkingBlockDeltaEvent / ThinkingBlockEndEvent** — 텍스트 스트리밍 이벤트와 동일한 형태이며, 모델의 사고 과정(chain of thought)에 특화되어 있습니다. `blockId`는 텍스트와 동일하게 응답 범위의 상관관계 키 의미를 가집니다.
:::

  :::{dropdown} 데이터 스트리밍 이벤트
**DataBlockStartEvent / DataBlockDeltaEvent / DataBlockEndEvent** — 텍스트 스트리밍 이벤트와 동일한 형태이며, 이미지 / 오디오 / 비디오 바이너리 데이터를 담습니다:

    - `DataBlockStartEvent`: `getMediaType()`은 MIME 타입을 반환합니다 (예: `"image/png"`).
    - `DataBlockDeltaEvent`: `getData()`는 점진적인 base64 인코딩 데이터를 반환합니다.
:::

  :::{dropdown} 도구 호출 스트리밍 이벤트
**ToolCallStartEvent** — 에이전트가 도구 호출을 시작합니다.

    | 메서드 | 타입 | 설명 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 응답 메시지 ID |
    | `getToolCallId()` | `String` | 고유 도구 호출 ID |
    | `getToolCallName()` | `String` | 호출되는 도구 |

    **ToolCallDeltaEvent** — 점진적인 도구 호출 인자가 도착합니다; `getDelta()`는 JSON 조각을 반환합니다.

    **ToolCallEndEvent** — 도구 호출 인자가 완성됩니다.
:::

  :::{dropdown} 도구 결과 스트리밍 이벤트
**ToolResultStartEvent** — 도구 실행이 시작됩니다 (`toolCallId`, `toolCallName`을 담음).

    **ToolResultTextDeltaEvent** — 도구로부터의 점진적인 텍스트 출력; `getDelta()`는 텍스트 조각을 반환합니다.

    **ToolResultDataDeltaEvent** — 도구로부터의 점진적인 바이너리 출력; `mediaType` / `data` / `url`을 가진 `DataBlockDeltaEvent`와 유사합니다.

    **ToolResultEndEvent** — 도구가 완료됩니다.

    | 메서드 | 타입 | 설명 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 응답 메시지 ID |
    | `getToolCallId()` | `String` | 일치하는 도구 호출 ID |
    | `getState()` | `ToolResultState` | 최종 상태: `SUCCESS`, `ERROR`, `INTERRUPTED`, `DENIED`, `RUNNING` |
:::

  :::{dropdown} 모델 호출 이벤트
**ModelCallStartEvent** — 모델 API 호출이 시작됩니다 (`modelName`을 담음).

    **ModelCallEndEvent** — 모델 API 호출이 완료됩니다 (`inputTokens` / `outputTokens`를 담음).
:::

  :::{dropdown} Human-in-the-loop 이벤트
**RequireUserConfirmEvent** — 에이전트가 사용자 확인을 위해 일시 중지됩니다.

    | 메서드 | 타입 | 설명 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 응답 메시지 ID |
    | `getToolCalls()` | `List<ToolUseBlock>` | 확인을 기다리는 도구 호출 |

    **RequireExternalExecutionEvent** — 에이전트가 외부 실행을 위해 일시 중지됩니다.

    | 메서드 | 타입 | 설명 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 응답 메시지 ID |
    | `getToolCalls()` | `List<ToolUseBlock>` | 외부 실행을 기다리는 도구 호출 |

    **UserConfirmResultEvent** — 이후의 `call()`이 일시 중지된 권한 HITL 요청을 재개할 때 발생합니다.
    하나 이상의 `ConfirmResult`를 담고 있으며, `replyId`는 앞선 `RequireUserConfirmEvent`와 일치합니다.

    | 메서드 | 타입 | 설명 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 상관된 `RequireUserConfirmEvent`의 응답 ID |
    | `getConfirmResults()` | `List<ConfirmResult>` | 이번 재개를 위해 수락된 확인 결과 |

    **ExternalExecutionResultEvent** — 이후의 `call()`이 일시 중지된 외부 실행 요청을 재개할 때 발생합니다.
    하나 이상의 `ToolResultBlock`을 담고 있으며, `replyId`는 앞선 `RequireExternalExecutionEvent`와 일치합니다.

    | 메서드 | 타입 | 설명 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 상관된 `RequireExternalExecutionEvent`의 응답 ID |
    | `getToolResults()` | `List<ToolResultBlock>` | 이번 재개를 위해 수락된 외부 실행 결과 |

    **AllToolsDeniedEvent** — 사용자가 HITL 확인을 통해 가장 최근 추론 단계의 모든 도구 호출을 거부했습니다. 이 이벤트는 `onActing` 미들웨어 체인을 통해 발생하며, 미들웨어가 `RequestStopEvent`를 발생시켜 에이전트를 중지시킬 수 있게 합니다. 어떤 미들웨어도 이를 처리하지 않으면, 에이전트는 다음 추론 반복으로 계속 진행합니다 (하위 호환성 유지).

    | 메서드 | 타입 | 설명 |
    |--------|------|-------------|
    | `getDeniedToolCalls()` | `List<ToolUseBlock>` | 거부된 도구 호출 |
:::

  :::{dropdown} 서브에이전트 이벤트
**SubagentExposedEvent** — `agent_spawn(expose_to_user=true)`를 통해 생성된 서브에이전트가 사용자가 접근 가능한 진입점으로 노출되었습니다. SSE / 스트리밍 소비자는 이를 사용해 UI에 새로운 대화 항목을 렌더링할 수 있습니다.

| 메서드 | 타입 | 설명 |
|--------|------|-------------|
| `getSubagentId()` | `String` | 서브에이전트의 고유 식별자 |
| `getAgentId()` | `String` | 서브에이전트의 에이전트 타입 ID |
| `getSessionId()` | `String` | 서브에이전트의 세션 ID |
| `getLabel()` | `String` | 사용자에게 보이는 라벨 (선택 사항) |
:::

## 이벤트로부터 메시지 재구성

이벤트와 메시지는 별개의 세계가 아닙니다 — 이들은 동일한 데이터를 바라보는 두 가지 관점입니다. `streamEvents`가 만들어내는 이벤트 스트림은 `replyId` / `blockId` / `toolCallId`로 집계되어 완전한 `AssistantMessage`로 재구성될 수 있으며, 최종 메시지 상태가 이벤트만으로도 완전히 복구 가능함을 보장합니다.

블록 ID로 그룹화하고 Reactor 연산자로 콘텐츠를 누적하는 표준 패턴은 `agentscope-core`의 `agent/StreamingHook.java`와 `agentscope-examples/documentation/.../streaming/AgentEventStreamExample.java`를 참고하세요.

```java
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;

StringBuilder accumulated = new StringBuilder();

agent.streamEvents(userMsg)
        .doOnNext(event -> {
            if (event instanceof AgentStartEvent start) {
                System.out.println("[start replyId=" + start.getReplyId() + "]");
            } else if (event instanceof TextBlockDeltaEvent delta) {
                accumulated.append(delta.getDelta());
            } else if (event instanceof ToolCallStartEvent tc) {
                System.out.println("[tool] " + tc.getToolCallName());
            } else if (event instanceof ToolResultEndEvent end) {
                System.out.println("[tool result state=" + end.getState() + "]");
            } else if (event instanceof AgentEndEvent end) {
                System.out.println("\n[end] full text:\n" + accumulated);
            }
        })
        .blockLast();
```

:::{tip}
이러한 분리는 배포를 유연하게 만듭니다: 백엔드는 SSE를 통해 이벤트 스트림을 푸시하고, 프런트엔드는 클라이언트 측에서 메시지를 재구성합니다. 연결이 끊기더라도, 어떤 체크포인트에서든 이벤트를 재생하면 메시지 상태를 정확히 복원할 수 있습니다.
:::

### 예제: 스트리밍 UI

전형적인 스트리밍 UI 루프(Spring WebFlux SSE 형태는 `streaming/StreamingWebExample.java`에 나와 있습니다):

```java
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.UserMessage;

agent.streamEvents(new UserMessage("user", "Help me fix this bug"))
        .doOnNext(event -> {
            if (event instanceof AgentStartEvent start) {
                System.out.println("[start replyId=" + start.getReplyId() + "]");
            } else if (event instanceof TextBlockDeltaEvent delta) {
                System.out.print(delta.getDelta());
            } else if (event instanceof ToolCallStartEvent tc) {
                System.out.println("\n[calling " + tc.getToolCallName() + "...]");
            } else if (event instanceof ToolResultEndEvent end) {
                System.out.println("[tool finished: " + end.getState() + "]");
            } else if (event instanceof AgentEndEvent end) {
                System.out.println("\n[done]");
            }
        })
        .blockLast();
```

## 더 읽어보기

::::{grid} 2

:::{grid-item-card} 에이전트
:link: ./agent.html

ReAct 루프에서 에이전트가 이벤트와 메시지를 방출하는 방법
:::
  :::{grid-item-card} 컨텍스트
:link: context.html

메시지가 저장되고 영속화되는 방법
:::

::::
