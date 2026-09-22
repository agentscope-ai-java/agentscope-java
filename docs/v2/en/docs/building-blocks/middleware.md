---
title: Middleware
description: Intercept and extend agent behavior at key lifecycle points
---

## Overview

Agent middleware lets you inject custom logic (logging, tracing, input rewriting, access control, …) at key points in an agent's execution flow without modifying the agent or model code.

In AgentScope Java, you can hook into 5 places — covering everything from the outer reply flow down to the raw model API call:

| Position | Type | Description |
|----------|------|-------------|
| `onAgent` | Onion | Wraps a full reply flow, covering all ReAct rounds, tool execution, and the final output |
| `onReasoning` | Onion | Wraps one reasoning step in the ReAct loop (input assembly → model call → streaming decode) |
| `onActing` | Onion | Wraps the execution of a single tool call |
| `onModelCall` | Onion | Wraps a raw `ChatModel` API call — closest to the model |
| `onSystemPrompt` | Transformer | Triggers when the system prompt is assembled; multiple middlewares run in sequence, each transforming the previous output |

The two types differ:

- **Onion** — middleware wraps the next handler; you can insert logic before/after `next.apply(input)` and observe the intermediate event stream.
- **Transformer** — middlewares form a pipeline; the previous output is the next input. There's no "inner layer" concept.

The diagram below shows how the hooks nest in the agent lifecycle. `onSystemPrompt` is nested inside `onReasoning` because it fires when the reasoning step assembles the system prompt:

```text
onAgent/
└── ReAct loop (per round)/
    ├── onReasoning/
    │   ├── onSystemPrompt (assemble system prompt)
    │   └── onModelCall (model API call)
    └── onActing (per tool call)
```


<Note>

`onActing` only wraps tool executions inside the agent runtime. Tools executed outside the agent via external execution are not tracked by `onActing`.

</Note>


## Equipping middleware

AgentScope packs a set of hooks into a single `MiddlewareBase` implementation — one middleware class can implement any subset of the 5 hooks (`onAgent`, `onReasoning`, `onActing`, `onModelCall`, `onSystemPrompt`); the rest default to `next.apply(input)`. Pass the instances to the builder's `middlewares(...)`:

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

`middleware(...)` (singular) adds one; `middlewares(...)` accepts `List<? extends MiddlewareBase>`. Hooks not implemented by a middleware are skipped at zero cost.

## Built-in middlewares

AgentScope ships five built-in middlewares. **No built-in overrides `order()`** — every one of them runs at the default order of `1`, so a custom middleware with `order() > 1` wraps outside all of them and `order() < 1` runs inside all of them. The one exception is `GracefulShutdownMiddleware`, which is always outermost regardless of `order()` (see [Built-ins vs. your middleware](#built-ins-vs-your-middleware)).

| Middleware | Package | Hooks | `order()` | How it is registered |
|---|---|---|---|---|
| `GracefulShutdownMiddleware` | `io.agentscope.core.shutdown` | `onReasoning`, `onActing` | `1` (but always outermost) | Automatic — always, prepended by the `ReActAgent` constructor |
| `OtelTracingMiddleware` | `io.agentscope.core.tracing` | `onAgent`, `onModelCall`, `onActing` | `1` | Manual — `.middleware(new OtelTracingMiddleware())` |
| `FinalAnswerFilterMiddleware` | `io.agentscope.core.middleware` | `onReasoning` | `1` | Manual — `.middleware(new FinalAnswerFilterMiddleware())` |
| `TaskReminderMiddleware` | `io.agentscope.core.middleware` | `onSystemPrompt`, `onReasoning` | `1` | Automatic — appended when `.enableTaskList(true)` is set |
| `DynamicSkillMiddleware` | `io.agentscope.core.skill` | `onSystemPrompt` | `1` | Automatic — appended when skill repositories are configured and dynamic skills are enabled |

### OtelTracingMiddleware

`OtelTracingMiddleware` (`io.agentscope.core.tracing`) wires up [OpenTelemetry](https://opentelemetry.io/docs/specs/semconv/gen-ai/) tracing for the agent lifecycle. It instruments `onAgent`, `onModelCall`, `onActing`, producing nested spans:

- `invoke_agent <name>` — wraps a full reply
- `chat <model>` — wraps each model API call
- `execute_tool <name>` — wraps each tool execution

When no OpenTelemetry SDK is configured (only the default no-op provider), every hook short-circuits to `next.apply(input)` — near-zero overhead.

`OtelTracingMiddleware` reads the process-wide `GlobalOpenTelemetry` instance. Applications that export spans themselves need the OpenTelemetry SDK and OTLP exporter in addition to AgentScope. Keep their versions aligned through the OpenTelemetry BOM (the version below matches the one currently used by AgentScope):

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

Build and register the SDK once per process before constructing the agent. The optional environment variable in this example can contain a value such as `Basic <base64-credentials>` for a backend that requires an `Authorization` header, including Langfuse:

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

The SDK must be registered before the middleware is used. If your runtime (for example, Spring Boot OpenTelemetry auto-configuration) already registers `GlobalOpenTelemetry`, reuse it and only add the middleware. Do not call the deprecated `TracerRegistry.register(...)` in the new setup. Close the `SdkTracerProvider` during application shutdown so its batch processor can flush pending spans.

Each reply produces a nested span tree with attributes such as agent name, session ID, model name, token counts, tool name, and inputs.

### TaskReminderMiddleware

`TaskReminderMiddleware` (`io.agentscope.core.middleware`) pairs with the built-in `TodoTools`: before every reasoning step it renders the current `AgentState.tasksContext` as a `<system-reminder>` and injects it into the context, keeping long-running tasks aligned with the plan.

Enable it together with `TodoTools` via `enableTaskList(true)`:

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

`FinalAnswerFilterMiddleware` exposes only the text from the final ReAct reasoning round. Text from rounds that produce tool calls is suppressed, while tool and other non-text events continue to stream normally.

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

The middleware buffers each round's text until the model call ends, because it cannot know whether the round is final until no tool call is observed.

### GracefulShutdownMiddleware

`GracefulShutdownMiddleware` (`io.agentscope.core.shutdown`) integrates graceful shutdown into the agent lifecycle. You never register it — every `ReActAgent` gets one automatically, and it is always the outermost middleware in the chain.

It checkpoints after `onReasoning` and after `onActing` completes. When the system is in the `SHUTTING_DOWN` state the agent is interrupted at these safe points, so the current reasoning or acting phase is allowed to finish rather than wasting output tokens mid-generation. Only when the global shutdown timeout is reached is the agent force-interrupted mid-phase.

Because it wraps everything, a custom `onReasoning` / `onActing` middleware always runs *inside* the shutdown checkpoint — your post-processing completes before the checkpoint decides whether to interrupt.

### DynamicSkillMiddleware

`DynamicSkillMiddleware` (`io.agentscope.core.skill`) rebuilds the agent's skill listing on every call and injects it through `onSystemPrompt`, replacing the static skill section of the prompt with one filtered for the current call.

It is added automatically when the builder has skill repositories configured and dynamic skills enabled — you do not construct it yourself. Since it is appended during `build()`, it runs *after* any `onSystemPrompt` middleware you registered, so it sees (and appends to) your transformed prompt rather than the original.

## Built-ins vs. your middleware

Two rules decide where a built-in sits relative to your own middleware.

**1. `GracefulShutdownMiddleware` is always outermost.** It is prepended by the `ReActAgent` constructor, which runs *after* the builder has sorted by `order()`. No `order()` value can place a middleware outside it:

```
GracefulShutdownMiddleware        ← always first, not sortable
  └── your middleware (sorted by order(), descending)
        └── auto-registered built-ins
              └── core agent logic
```

**2. Every other built-in is an ordinary list member at order `1`.** Manually registered built-ins (`OtelTracingMiddleware`, `FinalAnswerFilterMiddleware`) take the position you register them in. Auto-registered built-ins (`TaskReminderMiddleware`, `DynamicSkillMiddleware`) are appended during `build()`, after everything you registered — so at equal order they land *innermost*, closest to the core logic.

To position a custom middleware deliberately against a built-in, override `order()`:

```java
public class AuthMiddleware implements MiddlewareBase {
    @Override
    public int order() {
        return 10;  // outside every built-in except GracefulShutdownMiddleware
    }
    // ...
}
```

| Goal | `order()` |
|---|---|
| Wrap the built-ins (auth, global rate limit, a span that includes tracing overhead) | `> 1` |
| Interleave with built-ins by registration position | `1` (default) |
| Run inside the built-ins (per-call timing measured without tracing overhead) | `< 1` |

## Error handling

Middleware does not add any error-handling layer of its own. The chain is a plain composition of Reactor operators, so an error raised by the core logic or by any middleware propagates outward through `next.apply(input)` exactly as a Reactor error signal — each enclosing middleware sees it, and it surfaces to the caller of `call()` / `stream()` unless something handles it.

This has three practical consequences:

- **An exception thrown directly in a hook body** (before returning the `Flux`) propagates synchronously and aborts the reply. Wrap risky work in `Mono.fromCallable(...)` / `Flux.defer(...)` so it becomes a Reactor error signal instead.
- **A middleware can swallow or substitute errors** with `onErrorResume` / `onErrorReturn`, which is exactly how the [model-fallback example](#model-fallback-middleware) retries against a backup model. Only middlewares *outside* that one see the recovered stream.
- **Cleanup belongs in `doFinally`, not after `next.apply(input)`.** The hook body returns immediately; `doFinally` is the only place guaranteed to run on success, error *and* cancellation — cancellation matters here, because a shutdown interrupt cancels the stream rather than erroring it.

```java
@Override
public Flux<AgentEvent> onModelCall(
        Agent agent, RuntimeContext ctx, ModelCallInput input,
        Function<ModelCallInput, Flux<AgentEvent>> next) {
    return next.apply(input)
            .doOnError(err -> log.warn("model call failed: {}", err.getMessage()))
            .doFinally(signal -> releaseResources());  // runs on complete, error and cancel
}
```

## Custom middleware

Implement `MiddlewareBase` (`io.agentscope.core.middleware`) and override only the hooks you need.

Each onion hook receives a `next` function — calling `next.apply(input)` enters the next layer. You can insert logic before or after, or use Reactor operators (`doOnNext` / `flatMap` / `map`, …) to observe and rewrite the event stream.

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

/** Observes agent / reasoning / model_call / system_prompt at the same time. */
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

Input record types per hook (under `io.agentscope.core.middleware`):

| Hook | Input record | Fields |
|------|--------------|--------|
| `onAgent` | `AgentInput` | `msgs: List<Msg>` |
| `onReasoning` | `ReasoningInput` | `messages: List<Msg>`, `tools: List<ToolSchema>`, `options: GenerateOptions` |
| `onActing` | `ActingInput` | `toolCalls: List<ToolUseBlock>` |
| `onModelCall` | `ModelCallInput` | `messages`, `tools`, `options`, `model: Model` |
| `onSystemPrompt` | `String` | The current prompt |

To replace fields flowing into the next layer, construct a new input record, then call `next.apply(...)`.

Runnable examples: `agentscope-examples/documentation/.../middleware/CustomizedMiddlewareExample.java`, `middleware/ModelCallMiddlewareExample.java`, `middleware/SystemPromptMiddlewareExample.java`.

### Reading RuntimeContext

Every `MiddlewareBase` hook receives the [`RuntimeContext`](/v2/en/docs/building-blocks/agent#runtimecontext-per-call-context) bound for this `call` / `stream` as the second argument — you can read session fields and typed/string attributes, and you can write back to it to forward values to downstream hooks and tools.

```java
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import java.util.function.Function;
import reactor.core.publisher.Flux;

/** Log user / request id and propagate a trace id for downstream tools. */
public class RequestContextMiddleware implements MiddlewareBase {

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent, RuntimeContext ctx, AgentInput input, Function<AgentInput, Flux<AgentEvent>> next) {
        System.out.printf(
                "[req] user=%s session=%s reqId=%s%n",
                ctx.getUserId(),
                ctx.getSessionId(),
                ctx.get("request_id"));
        ctx.put("trace_id", java.util.UUID.randomUUID().toString());  // visible to later hooks / tools
        return next.apply(input);
    }
}
```

Things to keep in mind:

- The same `RuntimeContext` instance is shared by every hook and tool in the reply; its maps are thread-safe, so `put` from any hook is safe.
- Don't cache per-request state on middleware instance fields — a middleware instance is typically reused across agents / calls. Use `RuntimeContext` or Reactor's `contextWrite` instead.
- If the builder also has a global `toolExecutionContext`, the framework merges it after the per-call context when dispatching to tools (per-call wins on key collisions).

### Execution order

Onion hooks (`onAgent`, `onReasoning`, `onActing`, `onModelCall`) are ordered by `MiddlewareBase.order()` — **higher values are outermost**. The default order is `1`; middlewares with the same order retain their builder registration order:

```
middlewares = [mw1(order=2), mw2(order=1)]
// Order:
// mw1 pre → mw2 pre → inner → mw2 post → mw1 post
```

Built-in middlewares also run at the default order of `1` — none of them override `order()` — so `order() > 1` wraps outside every built-in and `order() < 1` runs inside them. `GracefulShutdownMiddleware` is the exception and is always outermost; see [Built-ins vs. your middleware](#built-ins-vs-your-middleware).

Override `order()` to move a custom middleware relative to the default order. For example, an order of `0` runs inside middleware that keeps the default order of `1`:

```java
MiddlewareBase lowerPriority = new MiddlewareBase() {
    @Override
    public int order() {
        return 0;
    }
};
```

For streaming / event-emitting hooks, the inner middleware sees each emitted event first:

```
mw1_pre → mw2_pre → mw2_event → mw1_event → ... → mw2_post → mw1_post
```

Transformer hooks (`onSystemPrompt`) — **left to right pipeline**:

```
middlewares = [mw1, mw2]
// originalPrompt → mw1.onSystemPrompt() → mw2.onSystemPrompt() → final
```

Overall hook execution order across one reply:

```
onAgent
  └── per ReAct round:
        ├── onReasoning
        │     ├── prepare model input → onSystemPrompt
        │     └── onModelCall
        └── onActing (per tool call)
```

## Practical examples

### Timing middleware

The middleware below records the wall-clock time of each model call:

```java
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import java.util.function.Function;
import reactor.core.publisher.Flux;

public class TimingMiddleware implements MiddlewareBase {
    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent, RuntimeContext ctx, ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next) {
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

### Rate-limit middleware

Enforce a minimum interval between two model calls:

```java
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
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
            Agent agent, RuntimeContext ctx, ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next) {
        long now = System.currentTimeMillis();
        long wait = minIntervalMs - (now - lastCall.get());
        Mono<Void> delay = wait > 0 ? Mono.delay(Duration.ofMillis(wait)).then() : Mono.empty();
        return delay.thenMany(next.apply(input))
                .doOnSubscribe(s -> lastCall.set(System.currentTimeMillis()));
    }
}
```

### Dynamic system-prompt middleware

Inject runtime context into the system prompt. Or reuse the example `middleware/SystemPromptMiddlewareExample.java`:

```java
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
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
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext ctx, String currentPrompt) {
        return Mono.just(currentPrompt + "\n\n## Current Context\n" + contextFn.get());
    }
}

// Wire-up:
// .middlewares(List.of(new DynamicContextMiddleware(() -> "Time: " + Instant.now())))
```

### Model-fallback middleware

Swap to a backup model if the primary fails:

```java
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
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
            Agent agent, RuntimeContext ctx, ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next) {
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

For a simple primary→backup fallback, `ReActAgent.Builder` already exposes `fallbackModel(...)` and `maxRetries(...)` directly — no middleware needed. Observing the switch is the same story: it happens below the `onModelCall` seam, so use `ReActAgent.Builder.failoverListener(...)` rather than a middleware.

</Tip>


### Stop agent when all tools are denied

When a user denies all tool calls from a reasoning step via HITL, the agent continues to the next reasoning iteration by default (backward compatible). To stop the agent in this scenario, write an `onActing` middleware that observes `AllToolsDeniedEvent` and emits a `RequestStopEvent`:

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

Once wired up, the agent stops immediately when all tools are denied, returning `GenerateReason.ALL_TOOLS_DENIED`:

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
