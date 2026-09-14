# agentscope-extensions-studio

Connects a running AgentScope Java agent to [AgentScope Studio](https://github.com/agentscope-ai/agentscope), the
web-based debugging and observation UI. `StudioManager` registers a "run" with a
Studio server, opens a persistent Socket.IO connection, and installs a hook that
forwards every agent response to the UI in real time — so you can watch a
multi-agent conversation unfold in the browser instead of scrolling logs. It also
supports human-in-the-loop (HITL) flows: `StudioUserAgent` can request input from
the Studio web UI and block until a person types a reply there, falling back to
the terminal when Studio is unavailable. A secondary, independent piece of this
module (`io.agentscope.core.tracing.telemetry`) exports AgentScope's execution
traces as OpenTelemetry GenAI-semconv spans, which `StudioManager` wires up
automatically to Studio's tracing endpoint.

## Installation

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-studio</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## Quick Start

Start Studio (see the [AgentScope Studio](https://github.com/agentscope-ai/agentscope) project) and point
`StudioManager` at it. `StudioManager.init()...initialize()` builds a `StudioConfig`,
registers the run over HTTP, opens the WebSocket connection, installs a
`StudioMessageHook` as a system hook on every agent, and registers a
`TelemetryTracer` that exports spans to Studio's tracing endpoint — all in one call:

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.studio.StudioManager;

StudioManager.init()
        .studioUrl("http://localhost:3000")
        .project("MyProject")
        .runName("experiment_001")
        .maxRetries(5)
        .reconnectAttempts(5)
        .initialize()
        .block();

// Every agent created from here on automatically forwards its responses
// to Studio, because the hook is registered as a system hook above.
ReActAgent agent = ReActAgent.builder()
        .name("Assistant")
        .model(model)
        .build();

agent.call(msg).block();

// Release the HTTP client and WebSocket connection when done.
StudioManager.shutdown();
```

Because `StudioMessageHook` is installed via `AgentBase.addSystemHook(...)`, it
applies to every agent, not just ones you attach it to explicitly. You can also
attach it to a single agent directly instead of (or in addition to) the global
hook:

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.studio.StudioManager;
import io.agentscope.core.studio.StudioMessageHook;

ReActAgent agent = ReActAgent.builder()
        .name("Assistant")
        .model(model)
        .hook(new StudioMessageHook(StudioManager.getClient()))
        .build();
```

`StudioMessageHook` forwards messages on the `PostCallEvent` (fired after an agent
produces a response); failures to reach Studio are logged and swallowed so they
never interrupt agent execution.

## Architecture

### Configuration — `StudioConfig`

`StudioConfig` (built via `StudioConfig.builder()`) holds everything needed to talk
to Studio. `StudioManager.Builder` wraps most of it — `studioUrl`, `tracingUrl`,
`project`, `runName`, `maxRetries`, and `reconnectAttempts` all have equivalent
methods there — but `runId`, `reconnectDelay`, and `reconnectMaxDelay` are only
settable via `StudioConfig.builder()` directly:

| Setting | Default |
|---|---|
| `studioUrl(String)` | `http://localhost:3000` |
| `tracingUrl(String)` | `{studioUrl}/v1/traces` |
| `project(String)` | `UnnamedProject` |
| `runName(String)` | `run_` + current timestamp |
| `runId(String)` | random UUID |
| `maxRetries(int)` | `3` (HTTP request retries) |
| `reconnectAttempts(int)` | `3` (WebSocket reconnection attempts) |
| `reconnectDelay(Duration)` | 1 second |
| `reconnectMaxDelay(Duration)` | 5 seconds |

### `StudioManager`

A static facade holding the process-wide Studio integration state. `init()`
returns a `StudioManager.Builder` (a thin wrapper over `StudioConfig.Builder`) whose
`initialize()` builds the config, constructs a `StudioClient` and a
`StudioWebSocketClient`, registers the run, connects the WebSocket, installs the
`StudioMessageHook` as a system hook, and registers a `TelemetryTracer` on
`TracerRegistry` — returning a `Mono<Void>` that completes once all of that has
succeeded. `StudioManager.getClient()`, `getWebSocketClient()`, and `getConfig()`
expose the resulting singletons; `isInitialized()` reports whether `initialize()`
has completed; `shutdown()` closes both clients, clears the singletons, and resets
`TracerRegistry` to a no-op tracer.

### `StudioClient` (HTTP) and `StudioWebSocketClient` (Socket.IO)

`StudioClient` wraps an OkHttp client and talks to Studio's tRPC-style REST
endpoints, with retry-with-backoff (`StudioConfig#getMaxRetries`) applied to every
call:

- `registerRun()` — `POST /trpc/registerRun`, announces a new run before anything
  else is sent.
- `pushMessage(Msg)` — `POST /trpc/pushMessage`, what `StudioMessageHook` calls
  after every agent response.
- `requestUserInput(agentId, agentName, structuredSchema)` — `POST
  /trpc/requestUserInput`, asks Studio to show an input form in the browser and
  returns a request ID used to correlate the reply.

`StudioWebSocketClient` connects to Studio's `/python` Socket.IO namespace,
authenticating with the run ID, and listens for `forwardUserInput` events. Calling
`waitForInput(requestId)` returns a `Mono<StudioWebSocketClient.UserInputData>`
that resolves once the matching event arrives, carrying both raw content blocks
and any structured (schema-validated) input the user submitted.

### Human-in-the-loop — `StudioUserAgent`

`StudioUserAgent` is an `AgentBase` that represents a human participant. Built
without a Studio client, it reads a line from the terminal on each `call()`.
Built with both a `StudioClient` and a `StudioWebSocketClient`, it instead calls
`requestUserInput` and then blocks on `waitForInput` (with a configurable
`inputTimeout`, default 30 minutes), converting the returned content blocks (or
structured input) into a `Msg`; if Studio is unreachable it logs a warning and
transparently falls back to terminal input:

```java
import io.agentscope.core.studio.StudioManager;
import io.agentscope.core.studio.StudioUserAgent;
import java.time.Duration;

StudioUserAgent user = StudioUserAgent.builder()
        .name("User")
        .studioClient(StudioManager.getClient())
        .webSocketClient(StudioManager.getWebSocketClient())
        .inputTimeout(Duration.ofMinutes(10))
        .build();
```

## Tracing (OpenTelemetry)

`io.agentscope.core.tracing.telemetry` is a separate concern from the Studio
websocket connection above: it implements AgentScope's `Tracer` SPI
(`TelemetryTracer`) on top of the OpenTelemetry SDK, emitting spans for agent
calls, model calls, tool calls, and message formatting, with attributes modeled
after the OpenTelemetry GenAI incubating semantic conventions (see
`AttributesExtractors`, `AgentScopeIncubatingAttributes`, and
`GenAiIncubatingAttributes`, plus the message/part model classes under
`tracing.telemetry.model`).

`StudioManager.init()...initialize()` sets this up for you automatically, building
a `TelemetryTracer` that exports OTLP/HTTP spans to `StudioConfig#getTracingUrl()`
(defaulting to `{studioUrl}/v1/traces`) and registering it on
`TracerRegistry`, so tracing "just works" once you're connected to Studio. If you
need to export traces independently of the Studio websocket connection — a
different OTLP collector, custom headers, or a pre-built OpenTelemetry `Tracer` —
build and register a `TelemetryTracer` directly via `TelemetryTracer.builder()`
(`endpoint(...)`, `addHeader(...)`, `tracer(...)`, `enabled(...)`) and
`TracerRegistry.register(...)`; see the class-level Javadoc on `TelemetryTracer`
and `TracerRegistry` for the full options.
