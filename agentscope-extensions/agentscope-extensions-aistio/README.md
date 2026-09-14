# agentscope-extensions-aistio

Data plane SDK that attaches a self-hosted AgentScope Java agent to the AgentScope Service
(Aistio) control plane. It reports session snapshots, an optional event stream, and effective
context to the control plane, and serves the `/agentscope/*` HTTP contract in-process so the
control plane can query session state and issue commands (compress, terminate, abort) back to
the agent. Interception is strictly bypass: it copies data off the agent's own execution path and
never blocks or alters it.

## Installation

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-aistio</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

`agentscope-core` and `agentscope-harness` are `provided` scope dependencies of this module — the
host application supplies them.

## Quick Start

The entry point is `Aistio.instrument(...)`, which mounts a `FrameworkAdapter` onto your agent and
starts a `SessionBridge`. The simplest path instruments an agent that is already built (works with
either `ReActAgent` or `HarnessAgent`):

```java
import io.agentscope.extensions.aistio.Aistio;
import io.agentscope.extensions.aistio.AistioConfig;
import io.agentscope.extensions.aistio.SessionBridge;
import io.agentscope.harness.agent.HarnessAgent;

HarnessAgent agent = HarnessAgent.builder()
        .name("my-agent")
        .model("dashscope:qwen-plus")
        .build();

SessionBridge bridge = Aistio.instrument(agent,
        AistioConfig.builder("my-agentscope-agent")
                .controlPlaneHttp("http://localhost:8081")
                .internalToken(System.getenv("BUILDER_INTERNAL_TOKEN"))
                .namespace("default")
                .contractHttpPort(18090)
                .build());

// bridge.close() on shutdown detaches the adapter and releases both channels.
```

This reports session snapshots, effective context, history, and commands, and self-registers with
the control plane via `POST /api/v1/dataplanes/register` (standalone BYO path — set
`controlPlaneHttp` + `internalToken`, leave `startGrpc` at its default `false`).

### Full instrumentation with the event stream

The Level-2 event stream needs the observer middleware registered while the agent is being built,
because a `ReActAgent`'s middleware list is fixed at construction time:

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.extensions.aistio.Aistio;
import io.agentscope.extensions.aistio.AistioConfig;
import io.agentscope.extensions.aistio.SessionBridge;
import io.agentscope.extensions.aistio.adapter.AgentScopeAdapter;

AgentScopeAdapter adapter = new AgentScopeAdapter();
ReActAgent agent = ReActAgent.builder()
        .name("Friday")
        .model("dashscope:qwen-plus")
        .middleware(adapter.middleware())
        .build();

SessionBridge bridge = Aistio.instrument(agent,
        AistioConfig.builder("my-agentscope-agent")
                .controlPlaneHttp("http://localhost:8081")
                .internalToken(System.getenv("BUILDER_INTERNAL_TOKEN"))
                .namespace("default")
                .enableEvents(true)
                .contractHttpPort(18090)
                .build(),
        adapter);
```

Pass the same `adapter` instance to `Aistio.instrument(...)` so the bridge reuses the adapter that
is already wired as a middleware, instead of constructing a fresh `AgentScopeAdapter`.

### Building the bridge before the agent exists

`Aistio.newBridge(config)` creates a `SessionBridge` without attaching or starting it, for callers
that need to hold the adapter reference before the agent is built. Call `SessionBridge#attach`
then `SessionBridge#start` once the agent is ready:

```java
import io.agentscope.extensions.aistio.Aistio;
import io.agentscope.extensions.aistio.SessionBridge;
import io.agentscope.extensions.aistio.adapter.AgentScopeAdapter;

AgentScopeAdapter adapter = new AgentScopeAdapter();
SessionBridge bridge = Aistio.newBridge(AistioConfig.builder("my-agentscope-agent")
        .controlPlaneHttp("http://localhost:8081")
        .internalToken(System.getenv("BUILDER_INTERNAL_TOKEN"))
        .build());

// ... build agent, wiring adapter.middleware() into it if events are enabled ...

bridge.attach(agent, adapter).start();
```

## Configuration

`AistioConfig` (via `AistioConfig.builder(agentName)`) selects one or both reporting channels:

- **Standalone BYO (recommended locally)** — set `controlPlaneHttp(...)` and `internalToken(...)`,
  leave `startGrpc` at its default `false`. The bridge serves `/agentscope/*` in-process and
  self-registers through `POST /api/v1/dataplanes/register`.
- **ASDP gRPC** — set `controlPlane("host:port")` and `startGrpc(true)` to open the upstream ASDP
  channel used for the Level-1/2/4 push reporting and command dispatch described above.

Other notable builder methods: `namespace(...)`, `instanceId(...)` (defaults to `HOSTNAME`, then
the local host name), `enableEvents(...)` (off by default — it is the only level whose volume
scales with conversation traffic), `contractHttpPort(...)` (`0` binds an ephemeral port),
`publicBaseUrl(...)`, and `sessionAffinity(...)`.

## Learn more

See the [AgentScope Service README](../../agentscope-service/README.md) — in particular "How
Agents attach" — for how this SDK fits into the wider control-plane / data-plane architecture, and
the `agentscope-examples/agents/agentscope-paw` sample for a full Spring-based BYO registration
wiring.
