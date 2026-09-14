---
title: "Harness Architecture"
description: "What HarnessAgent is, how its capabilities cooperate, and how state flows during a call()"
---

`HarnessAgent` is a thin wrapper around `ReActAgent` that packages the engineering capabilities long-running agents need — workspace-driven persona, long-term memory, subagent orchestration, sandbox isolation, skill composition, plan mode, channel routing — into a single builder.

A bare `ReActAgent` only handles "one request → reason → tool → reply". Harness answers a different set of questions: how does the next turn pick up where the last left off, how does context stay bounded, how do users stay isolated, how do dangerous actions get reviewed, how do reusable capabilities accumulate.

> Installation, dependency, and an end-to-end "first `HarnessAgent`" walkthrough live in [Quickstart](../quickstart.md). This page is architecture only.

## Building a HarnessAgent

`HarnessAgent` (`io.agentscope.harness.agent.HarnessAgent`) is the user-facing harness API: it wraps
a [`ReActAgent`](../building-blocks/agent.md) with workspace / filesystem / sandbox / subagent /
skill / plan-mode / MCP orchestration on top. Use `HarnessAgent.builder()` whenever you need any of
those production capabilities; for a bare ReAct loop with no workspace, persistence, or subagents,
use [`ReActAgent.builder()`](../building-blocks/agent.md#configuring-an-agent) directly instead — the
two builders share most fields, so switching between them later is mostly mechanical.

`HarnessAgent` is **stateless between calls** and safe to use as a singleton serving multiple
users/sessions concurrently — each `call()` uses the `RuntimeContext`'s `(userId, sessionId)` to
isolate state; calls on the same session are serialized automatically, different sessions run in
parallel.

Like `ReActAgent`, the builder's `.model(...)` accepts any [`ChatModelBase`](../building-blocks/model.md)
subclass (`DashScopeChatModel`, `OpenAIChatModel`, `AnthropicChatModel`, …) — or a `ModelRegistry`
string id for the common case. Tools go on a `Toolkit`, exactly as with `ReActAgent`:

```java
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.harness.agent.HarnessAgent;
import java.nio.file.Paths;

public class WeatherTools {
    @Tool(name = "get_weather", description = "Get the current weather for a city")
    public String getWeather(
            @ToolParam(name = "city", description = "City name, e.g. 'Tokyo'") String city) {
        return "Sunny, 24°C in " + city;
    }
}

// Any ChatModelBase subclass works here — swap in OpenAIChatModel, AnthropicChatModel, etc.
ChatModelBase model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .formatter(new DashScopeChatFormatter())
                .build();

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new WeatherTools());

HarnessAgent agent =
        HarnessAgent.builder()
                .name("weather-assistant")
                .sysPrompt("You are a helpful weather assistant.")
                .model(model)                          // HarnessAgent.Builder#model(Model)
                .toolkit(toolkit)                       // HarnessAgent.Builder#toolkit(Toolkit)
                .workspace(Paths.get(".agentscope/workspace"))
                .build();
```

`.model(...)` also has a `String` overload (`.model("dashscope:qwen-plus")`) that resolves through
`ModelRegistry` and reads the matching API-key environment variable automatically — see
[Quickstart](../quickstart.md) for that form end-to-end, and [Model](../building-blocks/model.md)
for every `ChatModelBase` provider and its builder options.

## Core working principle

Three things to keep in mind:

**1. Capabilities layer onto the reasoning loop, not into it.**
Workspace injection, compaction, subagents, sandbox, Plan Mode — each hooks into key moments of the ReAct loop. The core algorithm is untouched; Harness only adds.

**2. Capabilities don't depend on each other; they share three objects.**
Each capability does one job and is unaware of the others. They cooperate through:

- **`RuntimeContext`** — who is speaking in this call: `sessionId`, `userId`, plus arbitrary extras. Not persisted.
- **The workspace** — who reads and writes which files. Where they physically land (local disk, sandbox, KV store) is a configuration choice.
- **`AgentStateStore`** — how runtime state is restored across calls.

**3. Built-ins run in a fixed order; your middleware runs first.**
Harness wires its built-in middleware in a fixed order at build time. Anything you add via `.middleware(...)` runs **before** Harness's built-ins.

## Core components

Each capability answers one problem; opt in on the builder.

| Capability | What it solves | Builder hook | Detail |
|---|---|---|---|
| Workspace-driven persona | Persona, knowledge, subagent specs, skills, MCP allowlist all live as files | `.workspace(path)` | [Workspace](./workspace.md) |
| State persistence | Same `(userId, sessionId)` resumes across requests, processes, replicas | on by default; override with `.stateStore(...)` | [Context & AgentState](../building-blocks/context.md) |
| Two-layer long-term memory | Facts in long conversations sediment into `MEMORY.md` | on by default; `.memory(...)` customizes prompts / trigger policy | [Memory](./memory.md) |
| Conversation compaction | History bounded; force-retry on real overflow | `.compaction(...)` | [Compaction](./compaction.md) |
| Large tool-result offloading | >80K-char results moved to disk + placeholder | `.toolResultEviction(...)` | [Compaction](./compaction.md) |
| Subagent orchestration | Delegate to children, sync or background, with auto push-back | `.subagent(...)` or drop spec in `workspace/subagents/` | [Subagent](./subagent.md) |
| Pluggable filesystem | Local + shell / shared store / sandbox without code changes | `.filesystem(...)` | [Filesystem](./filesystem.md) |
| Sandbox isolation | Files and commands isolated; cross-call recovery; multi-replica | `.filesystem(new DockerFilesystemSpec()...)` | [Sandbox](./sandbox.md) |
| Plan Mode | Read-only think-first phase with HITL exit | `.enablePlanMode()` | [Plan Mode](./plan-mode.md) |
| Skill composition | Skills from Git / Nacos / MySQL / classpath / workspace | `.skillRepository(...)` | [Skill](./skill.md) |
| MCP integration & tool allowlist | Declarative MCP servers + allow/deny per tool | `workspace/tools.json` | [Workspace](./workspace.md) |
| Channel routing | Session management, per-session concurrency, multi-agent routing, streaming events | `agent.channel(...)` / `GatewayBootstrap` | [Channel](./channel.md) |

## How state flows

Three layers exist; the framework moves data between them automatically.

- **In-call state** — `AgentState` (conversation context, permission rules, Plan Mode state, tool state) plus `RuntimeContext` (`sessionId`, `userId`, sandbox handle, extras).
- **Cross-call state** — auto-saved at the end of every `call()` and auto-loaded on the next: the `AgentState` runtime snapshot in the configured `AgentStateStore` (default `~/.agentscope/state/<agentId>/`, addressed by `(userId, sessionId)`), the never-compacted full conversation log under `sessions/<sessionId>.log.jsonl`, subtask records, and sandbox metadata.
- **Long-term memory** — accumulated across sessions: `memory/YYYY-MM-DD.md` is append-only, periodically merged into `MEMORY.md` by a throttled background job; `MEMORY.md` is injected into the system prompt every reasoning step.

Three invariants worth remembering:

- The system prompt is rebuilt every reasoning step, so edits to `AGENTS.md` or `MEMORY.md` take effect immediately — no restart.
- Compaction, memory distillation, and background maintenance are throttled; they don't run every turn.
- `AgentState` is persisted by core's `ReActAgent` + `AgentStateStore`. Harness no longer adds its own persistence hook.

## Adding your own middleware

To insert custom behaviour without bypassing Harness's plumbing:

- Use `.middleware(...)` — your middleware runs before all Harness built-ins.
- Read `RuntimeContext` from the agent for the current call's identity (`userId` / `sessionId`).
- For workspace I/O, go through `harnessAgent.getWorkspaceManager()` — it routes correctly under sandbox or remote-store modes. `java.nio.Files` writes to the host disk and will land in the wrong place outside local mode.

## Related pages

- [Workspace](./workspace.md) — directory layout, what gets injected into the system prompt, `tools.json`
- [Context & AgentState](../building-blocks/context.md) — `AgentState`, `RuntimeContext`, `AgentStateStore` persistence, multi-user isolation
- [Memory](./memory.md) — two-layer memory
- [Compaction](./compaction.md) — summary compaction, large-result offloading, overflow recovery
- [Filesystem](./filesystem.md) — local + shell / shared store / sandbox
- [Sandbox](./sandbox.md) — isolated execution, cross-call recovery, distributed
- [Subagent](./subagent.md) — declarations, sync/background, streaming forwarding
- [Skill](./skill.md) — four-layer composition, self-learning loop
- [Plan Mode](./plan-mode.md) — read-only phase + HITL exit
- [Channel](./channel.md) — session management, multi-agent routing, streaming SSE
