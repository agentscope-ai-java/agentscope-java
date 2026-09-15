---
title: "V1 마이그레이션 가이드"
description: "AgentScope Java 1.x에서 2.0으로의 전체 마이그레이션 가이드"
---

:::{tip}
버전별 변경 기록을 찾고 계신가요? [릴리스 노트](others/release-notes.md)를 확인하세요.
:::

AgentScope Java 2.0은 가능한 한 1.x와의 호환성을 유지해 대부분의 사용자가 원활하게 업그레이드할 수 있도록 하는 것을 목표로 합니다. 다만 2.0은 API 수준의 변경도 함께 도입합니다. 이 페이지는 그러한 변경 사항을 두 섹션으로 나눕니다.

- **마이그레이션 가이드** — 1.x 대비 무엇이 바뀌었는지, 두 단계로 나눠 설명합니다.
  - **Part A · 필수** — 마이그레이션하지 않으면 코드가 컴파일에 실패하거나 런타임에 예외가 발생합니다
  - **Part B · 권장** — 여전히 동작하지만 `@Deprecated(forRemoval = true)`로 표시되었으며, 다음 마이너 버전에서 제거됩니다
- **새로운 기능** — 마이그레이션 가이드에 나타나지 않는, 완전히 새로 추가된 기능

## 마이그레이션 가이드

### Part A — 필수 (마이그레이션하지 않으면 컴파일 오류 또는 런타임 예외 발생)

이 섹션의 항목들은 제거되었거나, 이름이 바뀌었거나, 시맨틱이 더 엄격해졌습니다. 1.x에서 동작하던 코드는 2.0에서 그대로 동작하지 않습니다.

#### A.1 제거된 `ReActAgent.Builder` 메서드

| 2.0에서 제거됨 | 대체 방법 |
|---|---|
| `.memory(Memory)` | `.stateStore(AgentStateStore)` — `AgentState.getContext()`가 대화를 보관하며, 설정된 `AgentStateStore`가 모든 `call()`마다 `RuntimeContext`의 `(userId, sessionId)`를 키로 삼아 자동으로 저장/로드합니다 |
| `.statePersistence(StatePersistence)` | 동일 — `AgentStateStore`가 영속성을 포괄합니다 |
| `.structuredOutputReminder(StructuredOutputReminder)` | 더 이상 필요하지 않음 — 구조화된 출력은 이제 모델 레이어에서 네이티브로 처리됩니다(`Model.supportsNativeStructuredOutput()`); 프레임워크가 자동으로 네이티브 JSON 스키마를 선택하거나 tool-choice로 폴백합니다 |

자세히 → [Context](building-blocks/context.md)

#### A.2 제거된 패키지와 클래스

| 2.0에서 제거됨 | 대체 방법 |
|---|---|
| `io.agentscope.core.session.SessionManager` | 에이전트 빌더에 `.stateStore(AgentStateStore)`를 설정하세요; `(userId, sessionId)`별로 영속화가 자동으로 이루어집니다 |
| `io.agentscope.core.pipeline.*` — `Pipeline`, `Pipelines`, `SequentialPipeline`, `FanoutPipeline`, `MsgHub` | 멀티 에이전트 오케스트레이션을 위해 미들웨어 + 서브에이전트 + 이벤트 스트림을 조합하세요. 서브에이전트 가이드 참고 → [Subagent](harness/subagent.md) |
| `io.agentscope.core.model.tts.*`(14개 파일, DashScope TTS / Realtime TTS / `AudioPlayer` 등) | 코어는 더 이상 TTS를 제공하지 않습니다. TTS가 필요하다면 업스트림 프로바이더 SDK를 직접 통합하세요 |
| `io.agentscope.core.model.StructuredOutputReminder` | 더 이상 필요하지 않음 — 구조화된 출력은 모델 레이어에서 네이티브로 처리됩니다 |
| `io.agentscope.core.agent.StructuredOutputCapableAgent` | 제거됨 — 구조화된 출력 기능은 네이티브 모델 레이어 지원과 함께 `ReActAgent`에 인라인되었습니다 |
| `io.agentscope.core.hook.PendingToolRecoveryHook` | `Builder.enablePendingToolRecovery(boolean)`를 사용하세요 |
| `io.agentscope.core.hook.TTSHook` | TTS 모듈과 함께 제거됨 |

#### A.3 모델 프로바이더가 코어에서 분리됨

OpenAI, Gemini, Anthropic, DashScope, Ollama 채팅 모델 구현체는 더 이상 `agentscope-core`에 패키징되지 않습니다. 코어는 이제 `Model`, `ChatModelBase`, `Formatter`, `ModelRegistry`, `ModelProvider` SPI 같은 공유 모델 계약만 유지합니다.

v1 코드가 코어에서 프로바이더 클래스를 임포트하고 있었다면, 이를 해당하는 모델 확장 모듈로 교체하세요.

| v1 import / dependency | v2 대체 방법 |
|---|---|
| `io.agentscope.core.model.OpenAIChatModel` | `agentscope-extensions-model-openai`를 추가하고 `io.agentscope.extensions.model.openai.OpenAIChatModel`을 임포트하세요 |
| `io.agentscope.core.model.GeminiChatModel` | `agentscope-extensions-model-gemini`를 추가하고 `io.agentscope.extensions.model.gemini.GeminiChatModel`을 임포트하세요 |
| `io.agentscope.core.model.AnthropicChatModel` | `agentscope-extensions-model-anthropic`을 추가하고 `io.agentscope.extensions.model.anthropic.AnthropicChatModel`을 임포트하세요 |
| `io.agentscope.core.model.DashScopeChatModel` | `agentscope-extensions-model-dashscope`를 추가하고 `io.agentscope.extensions.model.dashscope.DashScopeChatModel`을 임포트하세요 |
| `io.agentscope.core.model.OllamaChatModel` | `agentscope-extensions-model-ollama`를 추가하고 `io.agentscope.extensions.model.ollama.OllamaChatModel`을 임포트하세요 |
| `io.agentscope.core.formatter.<provider>.*` | `io.agentscope.extensions.model.<provider>.formatter.*` |
| `io.agentscope.core.credential.<Provider>Credential` | `io.agentscope.extensions.model.<provider>.credential.<Provider>Credential` |

`ModelRegistry`의 문자열 id는 여전히 동작하지만, 해당 확장 모듈이 클래스패스에 있을 때만 동작합니다.

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("dashscope:qwen-plus")
    .build();
```

Spring Boot 애플리케이션은 범용적인 코어 모델 경로에 의존하는 대신 프로바이더별 스타터를 사용해야 합니다.

| 프로바이더 | Spring Boot 스타터 |
|---|---|
| OpenAI | `agentscope-openai-spring-boot-starter` |
| DashScope | `agentscope-dashscope-spring-boot-starter` |
| Gemini | `agentscope-gemini-spring-boot-starter` |
| Anthropic | `agentscope-anthropic-spring-boot-starter` |
| Ollama | `agentscope-ollama-spring-boot-starter` |

자세히 → [Model](building-blocks/model.md), [Model Providers](../integration/overview.md)

#### A.4 `state` 패키지 재구성 (컴파일 오류)

| v1 | v2 |
|---|---|
| `AgentMetaState` | `AgentState` |
| `StateModule` | **제거됨** — `Memory`, `Toolkit` 등의 상위 클래스 역할을 더 이상 하지 않습니다 |
| `StatePersistence` | **제거됨** — `AgentStateStore` 추상화로 대체됨 |
| `ToolkitState` | `io.agentscope.core.state.legacy.ToolkitState`로 이동됨(호환성만을 위해 유지됨 — 새 코드에서는 참조하지 마세요) |
| (신규) | `Task`, `TaskContextState`, `ToolContextState`, `PlanModeContextState`, `ReadCacheEntry` |

`io.agentscope.core.state`에서 `AgentMetaState`, `StateModule`, `StatePersistence`, `ToolkitState`를 임포트하는 코드는 컴파일에 실패합니다. 자세히 → [Context](building-blocks/context.md)

#### A.5 `PlanNotebook` 제거 — `HarnessAgent.enablePlanMode()`를 사용하세요

`io.agentscope.core.plan` 패키지 전체(`PlanNotebook`, `Plan`, `SubTask`, `PlanStorage`, `PlanToHint` 및 관련 클래스)가 별도의 deprecated 브리지 없이 제거되었습니다.

**변경 사항**: `PlanNotebook`은 계획을 상태 머신(todo → in_progress → done → abandoned)을 가진 구조화된 `Plan` + `SubTask` 객체와 8개의 도구 함수로 모델링했습니다. v2의 대체 방식은 근본적으로 다른 설계로, Plan Mode는 이제 에이전트가 쓰기 권한을 얻기 전에 일반 Markdown 파일에 접근 방식을 설계하는 **읽기 전용 조사 단계**입니다.

| v1 `PlanNotebook` | v2 Plan Mode |
|---|---|
| `ReActAgent.builder().planNotebook(PlanNotebook.builder().build())` | `HarnessAgent.builder().enablePlanMode()` |
| 상태 머신을 가진 구조화된 `Plan` + `SubTask` 객체 | 일반 Markdown 파일(`plans/PLAN.md`) |
| 8개 도구: `createPlan`, `reviseCurrentPlan`, `updateSubtaskState`, `finishSubtask`, `finishPlan`, `viewSubtasks`, `viewHistoricalPlans`, `recoverHistoricalPlan` | 3개 도구: `plan_enter`, `plan_write`, `plan_exit` |
| 계획과 실행이 뒤섞여 있음 — 읽기 전용 제약 없음 | Plan Mode는 읽기 전용입니다; `plan_exit`은 에이전트가 쓰기 권한을 되찾기 전에 HITL 게이트를 트리거합니다 |
| `PlanToHint`가 추론 단계마다 컨텍스트 힌트를 주입 | `PlanModeMiddleware`가 Plan Mode 동안 변형(mutating) 도구를 차단합니다 |
| `PlanStorage`(인메모리) + `StateModule` 영속성 | `WorkspaceManager`를 통해 계획 파일이 작성됨; 상태는 `AgentState.planModeContext`에 존재 |

**서브태스크 추적**: v1 코드가 `PlanNotebook`의 서브태스크 상태 추적(작업을 서브태스크로 나누고 실행 중 체크하는 것)에 의존했다면, v2에서 대응되는 것은 **작업 목록(task list)**입니다 — 빌더에서 `.enableTaskList(true)`로 활성화하면 `TodoTools`와 `TaskReminderMiddleware`가 등록됩니다.

#### A.6 `Msg` 콘텐츠 검증이 더 엄격해짐 (런타임 예외)

`Msg`는 이제 생성 시점에 `content`를 `role`에 맞춰 검증합니다.

- `USER` — `TextBlock` / `DataBlock` / `ImageBlock` / `AudioBlock` / `VideoBlock`만 허용
- `SYSTEM` — `TextBlock`만 허용
- `ASSISTANT` — 제약 없음

v1에서 허용되던 조합(예: `ToolUseBlock`을 담은 `USER` 메시지)은 이제 생성 시점에 예외를 던집니다. 호출 지점에서 role/content 호환성을 명확히 하려면 role이 고정된 서브클래스인 `UserMessage` / `AssistantMessage` / `SystemMessage` / `ToolResultMessage`를 사용하세요. 자세히 → [Message & Event](building-blocks/message-and-event.md)

#### A.7 에이전트가 완전히 상태 비저장이 됨 (아키텍처 변경)

`ReActAgent`는 이제 **완전히 상태 비저장(stateless)**입니다 — 인스턴스 자체는 가변적인 "현재 세션" 상태를 보관하지 않습니다. 호출별 가변 상태(`AgentState`, `PermissionEngine`, 이벤트 싱크)는 내부 `CallExecution` 객체에 캡슐화되어 Reactor Context를 통해 호출 체인 전체로 전파됩니다. 단일 Agent 인스턴스는 세션 간 간섭 없이 여러 `(userId, sessionId)` 조합을 동시에 안전하게 처리할 수 있습니다.

**v1 → v2 영향**:

| 제거됨 | 대체 방법 |
|---|---|
| `ReActAgent.getCurrentSessionId()` | `call()` 시점에 `RuntimeContext.getSessionId()`를 통해 제공됨 |
| `ReActAgent.getCurrentUserId()` | `call()` 시점에 `RuntimeContext.getUserId()`를 통해 제공됨 |
| `AgentBase(name, desc, checkRunning, hooks)` 생성자 | `AgentBase(name, desc, hooks)`를 사용하세요 — `checkRunning`은 더 이상 필요하지 않으며, 동시성은 세션별 직렬화로 보장됩니다 |
| `ReActAgent.getState()` | `ReActAgent.getAgentState()` 또는 `getAgentState(userId, sessionId)` |

`isCheckRunning()`은 여전히 호출 가능하며(항상 `false` 반환), `Builder.checkRunning(boolean)`도 여전히 호출 가능합니다(무시됨) — 둘 다 `@Deprecated`입니다.

#### A.8 `TracerRegistry` + `TelemetryTracer` → `OtelTracingMiddleware`

기존 트레이싱 설정은 프레임워크 레벨의 `Tracer`를 전역으로 등록했습니다.

```java
TracerRegistry.register(TelemetryTracer.builder().tracer(tracer).build());
```

현재의 2.0 소스 트리에서 `TelemetryTracer`는 `agentscope-core`가 아니라 `agentscope-extensions-studio` 모듈에 존재합니다. Studio 통합을 위해 여전히 사용할 수 있지만, 애플리케이션 전역 트레이싱을 되살리기 위해서만 Studio 확장을 추가하는 것은 권장되는 마이그레이션 방법이 아닙니다. `Tracer` 인터페이스와 `TracerRegistry`는 제거 예정으로 deprecated 처리되었습니다.

대신 표준 OpenTelemetry 컴포넌트를 통해 트레이싱을 설정하세요.

| 기존 설정 | 2.0 대체 방법 |
|---|---|
| `TelemetryTracer.builder().endpoint(...)` | `OtlpHttpSpanExporter`를 만들어 `SdkTracerProvider`에 붙이세요 |
| `TelemetryTracer.builder().addHeader(...)` | `OtlpHttpSpanExporter.builder().addHeader(...)`를 호출하세요 |
| `TracerRegistry.register(...)` | `OpenTelemetrySdk.buildAndRegisterGlobal()`로 SDK를 등록하세요 |
| 프레임워크 전역 tracer | 스팬을 방출해야 하는 각 에이전트에 `new OtelTracingMiddleware()`를 추가하세요 |
| `TracerRegistry.resetToNoop()` / tracer 종료 | 종료 시 애플리케이션이 소유한 `SdkTracerProvider`를 close하세요 |

미들웨어는 `GlobalOpenTelemetry`를 읽으므로, 에이전트가 미들웨어를 사용하기 전에 SDK가 등록되어 있어야 합니다. 필요한 의존성과 커스텀 인증 헤더를 포함한 완전한 OTLP 예제는 [Middleware — OtelTracingMiddleware](building-blocks/middleware.md#oteltracingmiddleware)를 참고하세요.

---

### Part B — 권장 (`@Deprecated(forRemoval = true)`, 현재는 여전히 호출 가능)

이 섹션의 항목들은 2.0에서 컴파일 및 실행이 되지만, 각각 다음 마이너 버전에서 제거될 예정으로 표시되어 있습니다. 여러분의 속도에 맞춰 마이그레이션하시되, 가능한 한 빨리 하시는 것을 권장합니다.

#### B.1 `SkillBox` → 스킬 저장소(skill repository)

- `SkillBox`(클래스)와 `Builder.skillBox(SkillBox)`는 모두 `@Deprecated(forRemoval = true, since = "2.0.0")`입니다.
- 권장 경로: `Builder.skillRepository(...)` / `.skillRepositories(...)`를 통해 하나 이상의 `AgentSkillRepository` 구현체(내장: `ClasspathSkillRepository`, `FileSystemSkillRepository`)를 등록하세요. 저장소가 하나라도 등록되면 `DynamicSkillMiddleware`가 자동으로 설치되어 `call()`마다 스킬 프롬프트를 다시 구성합니다.
- 세밀한 필터링: `Builder.skillFilter(SkillFilter)`.

자세히 → [Skill](harness/skill.md)

#### B.2 Hook → Middleware

`io.agentscope.core.hook` 패키지 전체 — `Hook` 인터페이스, `HookEvent`, `HookEventType`, 그리고 모든 `*Event` 클래스 — 는 `@Deprecated(forRemoval = true, since = "2.0.0")`입니다. 기존 import는 여전히 컴파일되며, `Builder.hook(...)` / `.hooks(...)`도 `LegacyHookDispatcher`를 통해 여전히 호출 가능하므로 v1 코드가 하루아침에 깨지지는 않습니다. 이제 권장되는 확장 지점은 `io.agentscope.core.middleware`입니다.

- `MiddlewareBase`는 다섯 단계를 노출합니다: onion 형태의 `onAgent` / `onReasoning` / `onActing` / `onModelCall`, 그리고 파이프라인 형태의 `onSystemPrompt`
- 빌더 메서드: `.middleware(MiddlewareBase)`와 `.middlewares(List<? extends MiddlewareBase>)`
- 내장: `TaskReminderMiddleware`(`TodoTools`와 짝을 이루며, 각 추론 단계 전에 작업 목록을 다시 주입)

자세히 → [Middleware](building-blocks/middleware.md)

#### B.3 `Memory` → `AgentStateStore` + `AgentState`

- `io.agentscope.core.memory.Memory` 인터페이스와 모든 구현체(`InMemoryMemory`, `LongTermMemory` 등)는 `@Deprecated(forRemoval = true, since = "2.0.0")`입니다.
- `Memory`는 더 이상 `StateModule`을 확장하지 않습니다. 대신 기존 구현체가 여전히 `AgentStateStore`를 왕복할 수 있도록 브리지 역할을 하는 `saveTo(AgentStateStore, userId, sessionId)` / `loadFrom(AgentStateStore, userId, sessionId)`를 갖습니다.
- 권장 모델:
  - **대화 이력**은 `AgentState.getContext()`에 존재합니다.
  - **영속성**은 `AgentStateStore` 추상화(내장: `InMemoryAgentStateStore`, `JsonFileAgentStateStore`)를 사용하며, `(userId, sessionId)` 쌍으로 파티셔닝됩니다.
  - 빌더 체인: `.stateStore(AgentStateStore)` — 호출의 `RuntimeContext`에 담긴 `(userId, sessionId)`를 키로 삼아 `AgentState`가 `call()`마다 자동으로 저장/로드됩니다.

자세히 → [Context](building-blocks/context.md)

#### B.4 이벤트 구독: hook + chunk 이벤트 → `streamEvents()`

v1에서 `Hook` + `*ChunkEvent`를 통해 텍스트나 도구 호출 델타를 감시하던 코드는 `agent.streamEvents()`로 마이그레이션할 수 있습니다. 이 메서드는 에이전트 생명주기 전체와 HITL 흐름(`RequireUserConfirmEvent`, `RequireExternalExecutionEvent`, `UserConfirmResultEvent`, `ExternalExecutionResultEvent` 등)을 아우르는 28개의 타입화된 이벤트로 구성된 `Flux<AgentEvent>`를 반환합니다.

새로운 이벤트 스트림과 함께, `Msg` 리팩터링은 다음을 추가합니다.

- `DataBlock` — base64 또는 URL 소스를 받는 통합 멀티모달 블록
- `HintBlock` — 에이전트 가이드 / 중간 추론
- `ToolUseBlock` / `ToolResultBlock`의 `ToolCallState` / `ToolResultState` — 도구 호출 생명주기
- 모든 블록의 `id` 필드 — 스트림 전체에 걸친 안정적인 참조

자세히 → [Message & Event](building-blocks/message-and-event.md)

##### `stream()` → `streamEvents()` (Python 2.0과의 정렬)

Python 2.0의 `agent.reply_stream()`은 Java의 세밀한 `io.agentscope.core.event.AgentEvent` 계층에 직접 대응되는 단일 스트리밍 시그니처(`AsyncGenerator[AgentEvent, None]`)를 노출합니다. 이를 맞추기 위해 Java 쪽의 거친(coarse-grained) `Flux<Event> stream(...)` API는 2.0.0부터 `@Deprecated`입니다.

- **메서드 (`forRemoval = true`, 다음 마이너 버전에서 제거 예정)**
  - `StreamableAgent.stream(...)` — 인터페이스의 11개 `stream(...)` 오버로드 전체(기본 구현 + 추상)
  - `AgentBase.stream(...)` — 3개의 `Flux<Event>` 구현
  - `ReActAgent.stream(..., RuntimeContext)` — 4개의 `RuntimeContext` 접미사가 붙은 오버로드
  - `HarnessAgent.stream(...)` — 9개 오버로드(3개의 인터페이스 `@Override` + 6개의 `RuntimeContext` 변형). `HarnessAgent`는 샌드박스 생명주기 `acquireForCall` / `releaseForCall`을 재사용하면서 `ReActAgent.streamEvents(...)`로 위임하는 4개의 새로운 `streamEvents(Msg/List<Msg>[, RuntimeContext])` 메서드를 얻습니다
  - `ReActAgent.streamEvents(..., RuntimeContext)`가 추가됨 — 컨텍스트 전파를 위해 `call(..., RuntimeContext)`를 미러링합니다
- **타입 (소프트 deprecation, 아직 `forRemoval` 아님)**
  - `io.agentscope.core.agent.Event`, `EventType`, `EventSource`
  - 여전히 harness 내부에서 사용됩니다(서브에이전트 이벤트 전달: `SubAgentTool` / `SubagentEventBus` / `DefaultAgentManager` / `AgentSpawnTool`), AGUI, A2A, chat-completions-web, 그리고 이벤트 버스/어댑터 입력으로서의 Kotlin 확장 모듈에서도 사용됩니다. 다운스트림 전체가 한 번에 경고로 도배되지 않도록, 이들 모듈이 `AgentEvent`로 마이그레이션한 뒤에야 `forRemoval = true`로 전환될 예정입니다.
  - 서브에이전트 이벤트는 `HarnessAgent.streamEvents(...)`에서 `null`이 아닌 `source` 경로와 함께 전달됩니다(`remoteStreaming`이 활성화된 경우 원격 Agent Protocol 자식 포함).

새로운 코드에서는 다음과 같이 사용해야 합니다.

```java
agent.streamEvents(new UserMessage("Hello"))
        .doOnNext(event -> {
            if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA) {
                System.out.print(((TextBlockDeltaEvent) event).getDelta());
            }
        })
        .blockLast();
```

#### B.5 RAG 모듈 — 진행 중

- `Knowledge`, `KnowledgeRetrievalTools`, `RAGMode`, `GenericRAGHook`은 모두 `@Deprecated(forRemoval = true, since = "2.0.0")`입니다.
- 빌더 메서드 `.knowledge(...)` / `.knowledges(...)` / `.ragMode(...)` / `.retrieveConfig(...)`도 함께 deprecated 처리되었습니다.
- v2 재작성이 진행 중입니다. 새로운 지식 베이스, 문서 리더, 스토어 API는 이후 마이너 릴리스에서 제공될 예정입니다. v1 구현체는 호환성을 위해 2.0에서도 계속 호출 가능하지만, **새 코드는 여기에 의존해서는 안 됩니다**.

#### B.6 장기 메모리 모듈 — 진행 중

- `LongTermMemory`, `LongTermMemoryMode`, `LongTermMemoryTools`는 모두 `@Deprecated(forRemoval = true, since = "2.0.0")`입니다.
- 빌더 메서드 `.longTermMemory(...)` / `.longTermMemoryMode(...)` / `.longTermMemoryAsyncRecord(...)`도 함께 deprecated 처리되었습니다.
- 동일한 상태입니다 — v2 아키텍처로 재작성 중입니다. 새 코드는 현재 API에 의존해서는 안 됩니다.

#### B.7 코어 셸 / 파일 도구 — 더 이상 deprecated 아님

- `io.agentscope.core.tool.coding.*`(`ShellCommandTool`, `CommandValidator`, `UnixCommandValidator`, `WindowsCommandValidator`)와 `io.agentscope.core.tool.file.*`(`ReadFileTool`, `WriteFileTool`, `FileToolUtils`)는 2.0.0-RC1부터 **더 이상 `@Deprecated`가 아닙니다**.
- 이 도구들은 호스트 프로세스에 대해 직접 명령을 실행하고 파일을 읽고 씁니다. 워크스페이스 / 샌드박스 격리가 필요 없는 `ReActAgent` 사용자에게는, 에이전트에 셸과 파일 접근 권한을 부여하는 권장 방법입니다.

```java
Toolkit toolkit = new Toolkit();
toolkit.registerTool(new ReadFileTool("/path/to/base/dir"));
toolkit.registerTool(new WriteFileTool("/path/to/base/dir"));
toolkit.registerTool(new ShellCommandTool());

ReActAgent agent = ReActAgent.builder()
    .toolkit(toolkit)
    /* ... */
    .build();
```

- `HarnessAgent` 사용자의 경우, harness 모듈은 로컬 / Docker / 클라우드 샌드박스 스토어를 통합하고, 권한 격리, 읽기/쓰기 캐시, HITL 승인을 갖춘 자체 워크스페이스 인식 파일 및 셸 도구(`read_file`, `write_file`, `execute` 등)를 제공합니다. 워크스페이스와 통합된 시나리오에서는 내장 harness 도구를 사용하는 것을 권장합니다.

자세히 → [Harness filesystem](harness/filesystem.md)

---

## 새로운 기능

아래 기능들은 2.0에서 추가된 것들로 — 1.x 코드를 깨뜨리지 않습니다. 위의 마이그레이션 가이드에서 이미 이벤트 시스템, 메시지 리팩터링, 미들웨어 메커니즘을 다뤘으므로 여기서는 반복하지 않습니다.

### AG-UI v2

- AG-UI 어댑터는 이제 v2 `streamEvents()` 경로를 사용합니다. 일반적인 `RUN_STARTED` / `RUN_FINISHED` 이벤트는 `AgentStartEvent` / `AgentEndEvent`로부터 변환되며, 오류 경로는 `RUN_ERROR`와 폴백 `RUN_FINISHED`를 방출합니다.
- 새로운 `AgentEventConverter`와 `AguiEventEnricher` 확장 지점: converter는 시맨틱 매핑을 처리하고, enricher는 `timestamp` / `rawEvent` 같은 횡단 관심사(cross-cutting property)를 처리합니다. Spring Boot 스타터는 두 빈 타입을 모두 자동으로 수집합니다.
- 모든 `AguiEvent`는 AG-UI 기본 이벤트 속성을 지원합니다. `BaseEventPropertiesEnricher`는 기본적으로 비활성화되어 있으며, 명시적으로 활성화하면 누락된 `timestamp` 값만 채우고 `rawEvent`는 기본값을 채우지 않습니다.
- `AguiAdapterConfig.emitTokenUsage`는 모델 호출 델타와 실행 단위의 누적 토큰 사용량을 담은 `CUSTOM token_usage` 이벤트를 방출할 수 있습니다.
- **동작 변경:** `source != null`인 AgentEvent(서브에이전트 이벤트)는 네이티브 `TEXT_MESSAGE_*` / `RUN_*` 대신 AG-UI `CUSTOM` 이벤트(`subagent.lifecycle`, `subagent.text`, `subagent.thinking`, `subagent.tool_call`, `subagent.tool_result`, `subagent.require_confirm`)로 방출됩니다. 기존의 네이티브 매핑으로 되돌리려면 `emitSubagentEventsAsNative(true)`를 설정하세요.
- Spring Boot 스타터는 `AguiRuntimeContextResolver`, 커스텀 `AguiAgentAdapterFactory`, 프런트엔드 도구 주입 / 병합 모드, HITL 인터럽트 출력을 지원합니다.

자세히 → [AG-UI](../integration/protocol/agui.md)

### Toolkit과 Permission

도구 실행은 2.0의 주요 확장 지점이며, 권한 시스템은 그 실행 경로 바로 위에 놓입니다 — 그래서 이 둘을 함께 소개합니다.

- **Toolkit 업그레이드**:
  - 통합된 베이스 클래스: `ToolBase` / `AgentTool`
  - 도구 그룹: `ToolGroup` / `ToolGroupScope` / `MetaToolFactory` — 필요에 따라 활성화하며, 예약된 `basic` 그룹은 항상 켜져 있습니다
  - 애너테이션 기반 등록: `ReflectiveFunctionTool` + `@Tool` / `@ToolParam`; `Toolkit#registerTool(Object)`는 애너테이션이 붙은 모든 메서드를 리플렉션으로 등록합니다
  - 내장 작업 도구: `io.agentscope.core.tool.builtin.TodoTools.todoWrite`(`TaskReminderMiddleware`와 짝을 이룸)
- **권한 시스템**(새 패키지 `io.agentscope.core.permission`):
  - `PermissionEngine`, `PermissionRule`, `PermissionMode`(`DEFAULT` / `ACCEPT_EDITS` / `EXPLORE` / `BYPASS` / `DONT_ASK`), `PermissionBehavior`
  - 모든 도구 호출은 `PermissionEngine`을 거칩니다: 허용 / 사용자 확인 필요 / 거부. HITL 결정은 `UserConfirmResultEvent`로 되돌아옵니다.

자세히 → [Tool](building-blocks/tool.md), [Permission System](building-blocks/permission-system.md)

### 모델 장애 허용과 credential

- 새 패키지 `io.agentscope.core.credential` — 공유 credential 계약과 `ModelCard`; 프로바이더별 credential은 모델 확장 모듈에 함께 존재합니다
- `ModelRegistry`는 해당 모델 확장 모듈이 클래스패스에 있을 때 `"provider:model"` 문자열(예: `dashscope:qwen-max`, `openai:gpt-5`)로부터 모델을 해석합니다
- 빌더 추가 사항: `.model(String)`, `.maxRetries(int)`, `.fallbackModel(Model)` / `.fallbackModel(String)`, `.stopOnReject(boolean)` — 기본 모델 장애 시 자동으로 재시도하고 폴백합니다

자세히 → [Model](building-blocks/model.md)

### Workspace (Harness 모듈)

- Workspace 추상화는 로컬 파일 시스템, Docker, E2B 클라우드 샌드박스 실행을 단일 인터페이스 뒤로 통합합니다
- Warm-up pool — 실행 환경을 일괄로 사전 초기화합니다; 병렬 RL rollout에 유용합니다

자세히 → [Workspace](harness/workspace.md)

### 그 외 새로운 Builder 메서드

- `.enableTaskList(...)` / `.enableTaskList(boolean)` — 내장 `TodoTools`를 활성화합니다
- `.permissionContext(PermissionContextState)` — 권한 규칙을 미리 로드합니다
- `ReActAgent.Builder.fromAgent(ReActAgent)` — 기존 에이전트의 관찰 가능한 설정(이름, 설명, 시스템 프롬프트, 모델, maxIters, generateOptions, toolkit)으로부터 새 빌더를 파생시킵니다
- `HarnessAgent.Builder.fromAgent(ReActAgent)` — ReActAgent → HarnessAgent 마이그레이션 헬퍼. `ReActAgent.Builder.fromAgent`와 동일한 7개 필드에 더해 **ReActAgent의 관찰 가능한 다른 모든 설정**을 상속합니다: `stateStore` / `defaultSessionId`, `ModelConfig`(`maxRetries` / `fallbackModel`), `ReactConfig.stopOnReject`, `modelExecutionConfig` / `toolExecutionConfig` / `toolExecutionContext`, `enablePendingToolRecovery`, `checkRunning`, `permissionContext`, `middlewares`, `hooks`. 복사되지 않는 유일한 플래그는 `enableMetaTool` / `enableTaskList`입니다 — 이들은 빌드 시점의 toolkit 변형(mutation) 플래그이며, toolkit 복사본은 이미 이들이 등록한 도구를 가지고 있습니다. Harness 전용 설정(workspace / filesystem / subagents / skills / plan mode / `disable*` 토글)은 여전히 명시적으로 설정해야 합니다. 전체 표는 javadoc을 참고하세요.
- **위 마이그레이션을 지원하기 위해 ReActAgent / 상위 클래스에 추가된 새로운 getter**: `getModelExecutionConfig()` / `getToolExecutionConfig()` / `getToolExecutionContext()` / `isPendingToolRecoveryEnabled()` / `getPermissionContext()`(`ReActAgent`에 있음); `isCheckRunning()`(`AgentBase`에 있으며 deprecated, 항상 `false` 반환).

자세히 → [Agent](building-blocks/agent.md)

### Memory / Compaction 전용 모델

`MemoryConfig`와 `CompactionConfig`는 `.model(Model)` / `.model(String)` 빌더 메서드를 얻어, 메모리 플러시, 통합, 컨텍스트 압축 작업에 에이전트의 주 추론 모델과 독립적으로 전용(일반적으로 더 가볍고 저렴한) 모델을 사용할 수 있게 되었습니다. 설정하지 않으면 에이전트의 주 모델이 사용됩니다(기존 동작 유지).

```java
HarnessAgent.builder()
    .model("openai:o3")
    .memory(MemoryConfig.builder()
        .model("openai:gpt-4.1-mini")
        .build())
    .compaction(CompactionConfig.builder()
        .model("openai:gpt-4.1-mini")
        .build())
    .build();
```
