---
title: 미들웨어
description: 주요 생명주기 시점에서 에이전트 동작을 가로채고 확장합니다
---

## 개요

에이전트 미들웨어를 사용하면 에이전트나 모델 코드를 수정하지 않고도 에이전트 실행 흐름의 주요 지점에 커스텀 로직(로깅, 트레이싱, 입력 재작성, 접근 제어 등)을 주입할 수 있습니다.

AgentScope Java에서는 외부 응답 흐름부터 원시 모델 API 호출까지 아우르는 5개의 지점에 훅을 걸 수 있습니다:

| 위치 | 타입 | 설명 |
|----------|------|-------------|
| `onAgent` | Onion | 모든 ReAct 라운드, 도구 실행, 최종 출력을 아우르는 전체 응답 흐름을 감쌉니다 |
| `onReasoning` | Onion | ReAct 루프에서 하나의 추론 단계(입력 조립 → 모델 호출 → 스트리밍 디코딩)를 감쌉니다 |
| `onActing` | Onion | 단일 도구 호출의 실행을 감쌉니다 |
| `onModelCall` | Onion | 원시 `ChatModel` API 호출을 감쌉니다 — 모델에 가장 가까운 지점 |
| `onSystemPrompt` | Transformer | 시스템 프롬프트가 조립될 때 트리거됩니다; 여러 미들웨어가 순서대로 실행되며, 각각 이전 출력을 변형합니다 |

두 타입의 차이는 다음과 같습니다:

- **Onion** — 미들웨어가 다음 핸들러를 감쌉니다; `next.apply(input)` 전후에 로직을 삽입하고 중간 이벤트 스트림을 관찰할 수 있습니다.
- **Transformer** — 미들웨어들이 파이프라인을 형성합니다; 이전 출력이 다음 입력이 됩니다. "내부 레이어"라는 개념이 없습니다.

아래 다이어그램은 이 훅들이 에이전트 생명주기 안에서 어떻게 중첩되는지 보여줍니다. `onSystemPrompt`는 추론 단계가 시스템 프롬프트를 조립할 때 발동되므로 `onReasoning` 안에 중첩되어 있습니다:

```text
onAgent/
└── ReAct 루프 (라운드별)/
    ├── onReasoning/
    │   ├── onSystemPrompt (시스템 프롬프트 조립)
    │   └── onModelCall (모델 API 호출)
    └── onActing (도구 호출별)
```

<Note>

`onActing`은 에이전트 런타임 내부의 도구 실행만 감쌉니다. 외부 실행을 통해 에이전트 밖에서 실행되는 도구는 `onActing`으로 추적되지 않습니다.

</Note>

## 미들웨어 장착

AgentScope는 여러 훅을 하나의 `MiddlewareBase` 구현체에 담습니다 — 하나의 미들웨어 클래스가 5개 훅 중 임의의 부분집합을 구현할 수 있습니다(나머지는 기본적으로 `next.apply(input)`로 동작). 빌더의 `middlewares(...)`에 인스턴스를 전달하세요:

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import java.util.List;

ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .sysPrompt("You are a helpful assistant.")
                .model(model)
                .toolkit(toolkit)
                .middlewares(List.of(new OtelTracingMiddleware()))
                .build();
```

`middleware(...)`(단수형)는 하나를 추가하고; `middlewares(...)`는 `List<? extends MiddlewareBase>`를 받습니다. 미들웨어가 구현하지 않은 훅은 비용 없이 건너뜁니다.

## 내장 미들웨어

### OtelTracingMiddleware

`OtelTracingMiddleware`(`io.agentscope.core.tracing`)는 에이전트 생명주기를 위한 [OpenTelemetry](https://opentelemetry.io/docs/specs/semconv/gen-ai/) 트레이싱을 연결합니다. `onAgent`, `onModelCall`, `onActing`에 계측을 넣어 중첩된 스팬을 생성합니다:

- `invoke_agent <name>` — 전체 응답을 감쌉니다
- `chat <model>` — 각 모델 API 호출을 감쌉니다
- `execute_tool <name>` — 각 도구 실행을 감쌉니다

OpenTelemetry SDK가 구성되지 않은 경우(기본 no-op 프로바이더만 있는 경우), 모든 훅은 `next.apply(input)`로 즉시 단락되어 — 오버헤드가 거의 없습니다.

`OtelTracingMiddleware`는 프로세스 전역의 `GlobalOpenTelemetry` 인스턴스를 읽습니다. 스팬을 직접 내보내는 애플리케이션은 AgentScope 외에 OpenTelemetry SDK와 OTLP 익스포터가 필요합니다. OpenTelemetry BOM을 통해 버전을 맞추세요(아래 버전은 현재 AgentScope가 사용하는 버전과 일치합니다):

```xml
<properties>
    <opentelemetry.version>1.61.0</opentelemetry.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-bom</artifactId>
            <version>${opentelemetry.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-sdk</artifactId>
    </dependency>
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-exporter-otlp</artifactId>
    </dependency>
</dependencies>
```

에이전트를 생성하기 전에 프로세스당 한 번 SDK를 빌드하고 등록하세요. 이 예제의 선택적 환경 변수는 `Authorization` 헤더가 필요한 백엔드(Langfuse 포함)를 위해 `Basic <base64-credentials>`와 같은 값을 담을 수 있습니다:

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

String endpoint =
        System.getenv().getOrDefault(
                "OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318/v1/traces");
String authorization = System.getenv("OTEL_EXPORTER_OTLP_AUTHORIZATION");

var exporterBuilder = OtlpHttpSpanExporter.builder().setEndpoint(endpoint);
if (authorization != null && !authorization.isBlank()) {
    exporterBuilder.addHeader("Authorization", authorization);
}

SdkTracerProvider tracerProvider =
        SdkTracerProvider.builder()
                .addSpanProcessor(
                        BatchSpanProcessor.builder(exporterBuilder.build()).build())
                .build();

OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .buildAndRegisterGlobal();
Runtime.getRuntime().addShutdownHook(new Thread(tracerProvider::close));

ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .sysPrompt("You are a helpful assistant.")
                .model(model)
                .toolkit(toolkit)
                .middleware(new OtelTracingMiddleware())
                .build();
```

SDK는 미들웨어가 사용되기 전에 등록되어야 합니다. 사용 중인 런타임(예: Spring Boot OpenTelemetry 자동 구성)이 이미 `GlobalOpenTelemetry`를 등록한다면, 그것을 재사용하고 미들웨어만 추가하세요. 새 설정에서 더 이상 사용되지 않는 `TracerRegistry.register(...)`를 호출하지 마세요. 배치 프로세서가 대기 중인 스팬을 플러시할 수 있도록 애플리케이션 종료 시 `SdkTracerProvider`를 닫으세요.

각 응답은 에이전트 이름, 세션 ID, 모델 이름, 토큰 수, 도구 이름, 입력값 등의 속성을 가진 중첩된 스팬 트리를 생성합니다.

### TaskReminderMiddleware

`TaskReminderMiddleware`(`io.agentscope.core.middleware`)는 내장 `TodoTools`와 짝을 이룹니다: 매 추론 단계 이전에 현재 `AgentState.tasksContext`를 `<system-reminder>`로 렌더링하여 컨텍스트에 주입하며, 장시간 실행되는 작업이 계획에 맞춰 유지되도록 합니다.

`enableTaskList(true)`를 통해 `TodoTools`와 함께 활성화하세요:

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.builtin.TodoTools;

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new TodoTools());

ReActAgent agent =
        ReActAgent.builder()
                .name("planner")
                .sysPrompt("You plan tasks step by step.")
                .model(model)
                .toolkit(toolkit)
                .enableTaskList(true)
                .build();
```

### FinalAnswerFilterMiddleware

`FinalAnswerFilterMiddleware`는 최종 ReAct 추론 라운드의 텍스트만을 노출합니다. 도구 호출을 생성하는 라운드의 텍스트는 억제되며, 도구 및 기타 비텍스트 이벤트는 계속 정상적으로 스트리밍됩니다.

```java
import io.agentscope.core.middleware.FinalAnswerFilterMiddleware;

ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .model(model)
                .toolkit(toolkit)
                .middleware(new FinalAnswerFilterMiddleware())
                .build();
```

이 미들웨어는 도구 호출이 없는 것을 확인하기 전까지는 해당 라운드가 마지막인지 알 수 없기 때문에, 모델 호출이 끝날 때까지 각 라운드의 텍스트를 버퍼링합니다.

## 커스텀 미들웨어

`MiddlewareBase`(`io.agentscope.core.middleware`)를 구현하고 필요한 훅만 오버라이드하세요.

각 onion 훅은 `next` 함수를 받습니다 — `next.apply(input)`을 호출하면 다음 레이어로 진입합니다. 전후에 로직을 삽입하거나, Reactor 연산자(`doOnNext` / `flatMap` / `map` 등)를 사용해 이벤트 스트림을 관찰하고 재작성할 수 있습니다.

```java
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.middleware.ReasoningInput;
import java.util.function.Function;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** agent / reasoning / model_call / system_prompt를 동시에 관찰합니다. */
public class FullObservabilityMiddleware implements MiddlewareBase {

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent, RuntimeContext ctx, AgentInput input, Function<AgentInput, Flux<AgentEvent>> next) {
        System.out.println("[agent] start for " + agent.getName());
        return next.apply(input)
                .doOnComplete(() -> System.out.println("[agent] end for " + agent.getName()));
    }

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent, RuntimeContext ctx, ReasoningInput input, Function<ReasoningInput, Flux<AgentEvent>> next) {
        System.out.println("[reasoning] start");
        return next.apply(input).doOnComplete(() -> System.out.println("[reasoning] end"));
    }

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent, RuntimeContext ctx, ModelCallInput input, Function<ModelCallInput, Flux<AgentEvent>> next) {
        System.out.println("[model_call] " + input.model().getClass().getSimpleName());
        return next.apply(input).doOnComplete(() -> System.out.println("[model_call] done"));
    }

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext ctx, String currentPrompt) {
        System.out.println("[system_prompt] length=" + currentPrompt.length());
        return Mono.just(currentPrompt);
    }
}
```

훅별 입력 레코드 타입(`io.agentscope.core.middleware` 아래):

| 훅 | 입력 레코드 | 필드 |
|------|--------------|--------|
| `onAgent` | `AgentInput` | `msgs: List<Msg>` |
| `onReasoning` | `ReasoningInput` | `messages: List<Msg>`, `tools: List<ToolSchema>`, `options: GenerateOptions` |
| `onActing` | `ActingInput` | `toolCalls: List<ToolUseBlock>` |
| `onModelCall` | `ModelCallInput` | `messages`, `tools`, `options`, `model: Model` |
| `onSystemPrompt` | `String` | 현재 프롬프트 |

다음 레이어로 흘러가는 필드를 교체하려면, 새 입력 레코드를 생성한 다음 `next.apply(...)`를 호출하세요.

실행 가능한 예제: `agentscope-examples/documentation/.../middleware/CustomizedMiddlewareExample.java`, `middleware/ModelCallMiddlewareExample.java`, `middleware/SystemPromptMiddlewareExample.java`.

### RuntimeContext 읽기

모든 `MiddlewareBase` 훅은 이 `call` / `stream`에 바인딩된 [`RuntimeContext`](/v2/ko/docs/building-blocks/agent#runtimecontext-호출별-컨텍스트)를 두 번째 인자로 받습니다 — 세션 필드와 타입/문자열 속성을 읽을 수 있으며, 하위 훅과 도구로 값을 전달하기 위해 다시 쓸 수도 있습니다.

```java
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import java.util.function.Function;
import reactor.core.publisher.Flux;

/** 사용자 / 요청 id를 기록하고 하위 도구를 위해 trace id를 전파합니다. */
public class RequestContextMiddleware implements MiddlewareBase {

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent, RuntimeContext ctx, AgentInput input, Function<AgentInput, Flux<AgentEvent>> next) {
        System.out.printf(
                "[req] user=%s session=%s reqId=%s%n",
                ctx.getUserId(),
                ctx.getSessionId(),
                ctx.get("request_id"));
        ctx.put("trace_id", java.util.UUID.randomUUID().toString());  // 이후 훅/도구에서 확인 가능
        return next.apply(input);
    }
}
```

유의해야 할 점:

- 동일한 `RuntimeContext` 인스턴스가 해당 응답의 모든 훅과 도구에서 공유됩니다; 이 안의 맵들은 스레드 안전하므로, 어떤 훅에서든 `put`을 호출해도 안전합니다.
- 미들웨어 인스턴스 필드에 요청별 상태를 캐시하지 마세요 — 미들웨어 인스턴스는 일반적으로 여러 에이전트 / 호출에 걸쳐 재사용됩니다. 대신 `RuntimeContext`나 Reactor의 `contextWrite`를 사용하세요.
- 빌더에 전역 `toolExecutionContext`도 설정되어 있다면, 프레임워크는 도구로 디스패치할 때 호출별 컨텍스트 뒤에 이를 병합합니다(키가 충돌하면 호출별 컨텍스트가 우선함).

### 실행 순서

Onion 훅(`onAgent`, `onReasoning`, `onActing`, `onModelCall`)은 `MiddlewareBase.order()`에 따라 정렬됩니다 — **값이 클수록 가장 바깥쪽**입니다. 기본 순서는 `1`이며, 동일한 순서를 가진 미들웨어는 빌더 등록 순서를 유지합니다:

```
middlewares = [mw1(order=2), mw2(order=1)]
// 순서:
// mw1 pre → mw2 pre → inner → mw2 post → mw1 post
```

기본 순서에 상대적으로 커스텀 미들웨어를 이동시키려면 `order()`를 오버라이드하세요. 예를 들어 순서 `0`은 기본 순서 `1`을 유지하는 미들웨어 내부에서 실행됩니다:

```java
MiddlewareBase lowerPriority = new MiddlewareBase() {
    @Override
    public int order() {
        return 0;
    }
};
```

스트리밍 / 이벤트 방출 훅의 경우, 안쪽 미들웨어가 방출된 각 이벤트를 먼저 봅니다:

```
mw1_pre → mw2_pre → mw2_event → mw1_event → ... → mw2_post → mw1_post
```

Transformer 훅(`onSystemPrompt`) — **왼쪽에서 오른쪽으로 향하는 파이프라인**:

```
middlewares = [mw1, mw2]
// originalPrompt → mw1.onSystemPrompt() → mw2.onSystemPrompt() → final
```

하나의 응답에 걸친 전체 훅 실행 순서:

```
onAgent
  └── ReAct 라운드별:
        ├── onReasoning
        │     ├── 모델 입력 준비 → onSystemPrompt
        │     └── onModelCall
        └── onActing (도구 호출별)
```

## 실전 예제

### 타이밍 미들웨어

아래 미들웨어는 각 모델 호출의 실제 소요 시간을 기록합니다:

```java
import io.agentscope.core.agent.Agent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import java.util.function.Function;
import reactor.core.publisher.Flux;

public class TimingMiddleware implements MiddlewareBase {
    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent, ModelCallInput input, Function<ModelCallInput, Flux<AgentEvent>> next) {
        long start = System.nanoTime();
        return next.apply(input)
                .doFinally(sig -> {
                    long ms = (System.nanoTime() - start) / 1_000_000;
                    System.out.println(
                            "[timing] " + agent.getName() + ": " + ms + "ms");
                });
    }
}
```

### 속도 제한 미들웨어

두 모델 호출 사이에 최소 간격을 강제합니다:

```java
import io.agentscope.core.agent.Agent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RateLimitMiddleware implements MiddlewareBase {

    private final long minIntervalMs;
    private final AtomicLong lastCall = new AtomicLong(0);

    public RateLimitMiddleware(Duration minInterval) {
        this.minIntervalMs = minInterval.toMillis();
    }

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent, ModelCallInput input, Function<ModelCallInput, Flux<AgentEvent>> next) {
        long now = System.currentTimeMillis();
        long wait = minIntervalMs - (now - lastCall.get());
        Mono<Void> delay = wait > 0 ? Mono.delay(Duration.ofMillis(wait)).then() : Mono.empty();
        return delay.thenMany(next.apply(input))
                .doOnSubscribe(s -> lastCall.set(System.currentTimeMillis()));
    }
}
```

### 동적 시스템 프롬프트 미들웨어

런타임 컨텍스트를 시스템 프롬프트에 주입합니다. 또는 예제 `middleware/SystemPromptMiddlewareExample.java`를 재사용하세요:

```java
import io.agentscope.core.agent.Agent;
import io.agentscope.core.middleware.MiddlewareBase;
import java.time.Instant;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

public class DynamicContextMiddleware implements MiddlewareBase {

    private final Supplier<String> contextFn;

    public DynamicContextMiddleware(Supplier<String> contextFn) {
        this.contextFn = contextFn;
    }

    @Override
    public Mono<String> onSystemPrompt(Agent agent, String currentPrompt) {
        return Mono.just(currentPrompt + "\n\n## Current Context\n" + contextFn.get());
    }
}

// 연결 방법:
// .middlewares(List.of(new DynamicContextMiddleware(() -> "Time: " + Instant.now())))
```

### 모델 폴백 미들웨어

기본 모델이 실패하면 백업 모델로 전환합니다:

```java
import io.agentscope.core.agent.Agent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.Model;
import java.util.function.Function;
import reactor.core.publisher.Flux;

public class ModelFallbackMiddleware implements MiddlewareBase {

    private final Model fallback;

    public ModelFallbackMiddleware(Model fallback) {
        this.fallback = fallback;
    }

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent, ModelCallInput input, Function<ModelCallInput, Flux<AgentEvent>> next) {
        return next.apply(input)
                .onErrorResume(err -> {
                    System.err.println("Primary model failed: " + err.getMessage()
                            + ", switching to fallback");
                    return next.apply(
                            new ModelCallInput(
                                    input.messages(),
                                    input.tools(),
                                    input.options(),
                                    fallback));
                });
    }
}
```

<Tip>

단순한 주 모델→백업 모델 폴백의 경우, `ReActAgent.Builder`는 이미 `fallbackModel(...)`과 `maxRetries(...)`를 직접 제공합니다 — 미들웨어가 필요하지 않습니다.

</Tip>

### 모든 도구가 거부되었을 때 에이전트 중지

사용자가 HITL을 통해 한 추론 단계의 모든 도구 호출을 거부하면, 에이전트는 기본적으로 다음 추론 반복으로 계속 진행합니다(하위 호환성 유지). 이 시나리오에서 에이전트를 중지시키려면, `AllToolsDeniedEvent`를 관찰하고 `RequestStopEvent`를 발생시키는 `onActing` 미들웨어를 작성하세요:

```java
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AllToolsDeniedEvent;
import io.agentscope.core.event.RequestStopEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import java.util.function.Function;
import reactor.core.publisher.Flux;

public class StopOnAllDeniedMiddleware implements MiddlewareBase {

    @Override
    public Flux<AgentEvent> onActing(
            Agent agent, RuntimeContext ctx, ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next) {
        return next.apply(input)
                .flatMap(event -> {
                    if (event instanceof AllToolsDeniedEvent) {
                        return Flux.just(
                                event,
                                new RequestStopEvent(
                                        "All tools denied by user",
                                        GenerateReason.ALL_TOOLS_DENIED));
                    }
                    return Flux.just(event);
                });
    }
}
```

연결하고 나면, 모든 도구가 거부될 때 에이전트는 즉시 중지되며 `GenerateReason.ALL_TOOLS_DENIED`를 반환합니다:

```java
ReActAgent agent =
        ReActAgent.builder()
                .name("guarded")
                .sysPrompt("...")
                .model(model)
                .toolkit(toolkit)
                .middlewares(List.of(new StopOnAllDeniedMiddleware()))
                .build();
```
