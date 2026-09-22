---
title: Configuration
description: Complete HarnessAgent.builder() reference grouped by concern — identity,
  model, tools, workspace, filesystem, state, subagents, skills, plan mode, and every
  switch that turns a built-in capability off
---

`HarnessAgent.builder()` is the single place where every harness capability is turned on, swapped
out, or turned off. This page is the complete builder reference, grouped by the problem each option
solves. For the bare ReAct loop without workspace, memory, or subagents, see
[`ReActAgent` builder fields](/v2/en/docs/building-blocks/agent#builder-fields) instead — the two
builders share most names, so moving between them is mechanical.

## The smallest agent that runs

Only `name`, `sysPrompt`, and `model` are required. Everything else has a working default:

```java
import io.agentscope.harness.agent.HarnessAgent;

HarnessAgent agent =
        HarnessAgent.builder()
                .name("assistant")
                .sysPrompt("You are a helpful assistant.")
                .model("dashscope:qwen-plus")   // reads DASHSCOPE_API_KEY from the environment
                .build();

String reply = agent.call("What can you do?").block().getTextContent();
System.out.println(reply);
```

With no `.workspace(...)`, the agent resolves one at `${user.dir}/.agentscope/workspace`, persists
state under `~/.agentscope/state/<agentId>/`, and registers the default filesystem, shell, memory,
and todo tools.

## A production configuration

A configuration that pins every choice a long-running deployment normally cares about:

```java
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.compaction.CompactionConfig;
import java.nio.file.Paths;
import java.time.Duration;

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new MyDomainTools());

HarnessAgent agent =
        HarnessAgent.builder()
                // --- identity ---
                .name("support-agent")
                .agentId("support-agent-v3")         // stable namespace key for stored state
                .sysPrompt("You are a support engineer.")

                // --- model ---
                .model("dashscope:qwen-max")
                .fallbackModel("openai:gpt-5.5")     // used when the primary model keeps failing
                .maxRetries(3)
                .modelExecutionConfig(
                        ExecutionConfig.builder()
                                .timeout(Duration.ofSeconds(90))
                                .maxAttempts(3)
                                .build())

                // --- tools + loop ---
                .toolkit(toolkit)
                .maxIters(20)
                .permissionContext(
                        PermissionContextState.builder()
                                .mode(PermissionMode.DEFAULT)
                                .build())

                // --- workspace + context budget ---
                .workspace(Paths.get("/data/agent-workspace"))
                .additionalContextFile("PREFERENCES.md")
                .maxContextTokens(8000)

                // --- history management ---
                .compaction(CompactionConfig.builder().build())

                .build();
```

## Identity and prompt

| Method | Default | What it does |
|--------|---------|--------------|
| `name(String)` | required | Agent identifier, used in messages and logs, and as the fallback namespace key |
| `sysPrompt(String)` | required | Base system prompt, before workspace content is layered on |
| `description(String)` | `"Agent(<agentId>) <name>"` | Metadata only for this agent; it becomes the **tool description** when this agent is exposed to a parent — see below |
| `agentId(String)` | falls back to `name` | Stable namespace key for stored state (`[agents, <agentId>, users, <userId>, …]`). Set it explicitly so renaming the agent does not orphan its state |
| `environmentMemory(String)` | `null` | Extra text appended to the per-session environment block of the system prompt, next to the session id |
| `environment(String)` | `"prod"` | Deployment environment label read by the skill `EnvironmentFilter` |

<Note>

**What `description` actually does.** It is *not* injected into this agent's own system prompt and does not affect its tool behavior. It matters only when the agent is exposed to a *parent* agent as a tool: `SubAgentTool` resolves that tool's description as `SubagentDeclaration.description` → this agent's `description(...)` → `"Call <name> to complete tasks"`, taking the first non-empty value. Left unset, `description` defaults to `"Agent(<agentId>) <name>"`, which tells a parent orchestrator nothing useful — so set it on any agent you intend to delegate to.

</Note>

<Tip>

Set `agentId` in any deployment that persists state. `name` doubles as the namespace key when
`agentId` is unset, so changing a display name would otherwise point the agent at a fresh, empty
state namespace.

</Tip>

## Model

| Method | Default | What it does |
|--------|---------|--------------|
| `model(String)` | required | `ModelRegistry` id, `"<provider>:<model>"`; reads the provider's API-key env var |
| `model(Model)` | required | An explicit `ChatModelBase` when you need custom endpoints, timeouts, or headers |
| `fallbackModel(String)` / `fallbackModel(Model)` | `null` | Model to switch to when the primary keeps failing |
| `maxRetries(int)` | provider default | Retry attempts before failing over |
| `failoverListener(FailoverListener)` | `null` | Notified when the fallback model takes over |
| `modelExecutionConfig(ExecutionConfig)` | `ExecutionConfig.MODEL_DEFAULTS` | Timeout, attempts, and backoff for model calls |
| `generateOptions(GenerateOptions)` | provider default | Temperature, top-p, and other sampling options |
| `modelResolver(Function<String, Model>)` | `null` | Resolves model-name strings to `Model` instances **for subagents** |

`ExecutionConfig` controls timeout and retry independently for model calls and tool calls:

```java
import io.agentscope.core.model.ExecutionConfig;
import java.time.Duration;

HarnessAgent agent =
        HarnessAgent.builder()
                .name("agent")
                .sysPrompt("…")
                .model("dashscope:qwen-plus")
                .modelExecutionConfig(
                        ExecutionConfig.builder()
                                .timeout(Duration.ofSeconds(120))
                                .maxAttempts(3)
                                .initialBackoff(Duration.ofSeconds(1))
                                .backoffMultiplier(2.0)
                                .build())
                .toolExecutionConfig(
                        ExecutionConfig.builder()
                                .timeout(Duration.ofSeconds(30))
                                .maxAttempts(1)     // don't retry side-effecting tools
                                .build())
                .build();
```

## Tools and the ReAct loop

| Method | Default | What it does |
|--------|---------|--------------|
| `toolkit(Toolkit)` | default toolkit | The `Toolkit` holding tools, MCP clients, skills, and tool groups |
| `maxIters(int)` | `10` | Maximum reasoning/acting iterations per call |
| `toolExecutionConfig(ExecutionConfig)` | `ExecutionConfig.TOOL_DEFAULTS` | Timeout, attempts, and backoff for tool calls |
| `permissionContext(PermissionContextState)` | `DEFAULT` mode | Allow / ask / deny rules, see [Permission System](/v2/en/docs/building-blocks/permission-system) |
| `stopOnReject(boolean)` | `false` | Stop the loop when a tool call is rejected instead of continuing |
| `enableMetaTool(boolean)` | `false` | Register the `reset_tools` meta tool for self-managed tool groups |
| `enableTaskList()` / `enableTaskList(boolean)` | off | Register the built-in todo/task-list tools |
| `enablePendingToolRecovery(boolean)` | `false` | Recover orphaned tool calls when a new message arrives |
| `toolsConfig(ToolsConfig)` | reads `workspace/tools.json` | Programmatic override for the MCP + allowlist config file |
| `registerExternalSchemas(List<ToolSchema>)` | `List.of()` | Schema-only external tools that suspend for a worker to execute |
| `mcpServerRegistrationListener(…)` | `null` | Receives terminal MCP server registration results; not propagated to subagents |
| `webHttpClient(HttpClient)` | JDK default | Custom `HttpClient` for the built-in `web_fetch` / `web_search` tools |
| `artifactDeliveryTarget(ArtifactDeliveryTarget)` | `null` | Registers `deliver_artifact` so the agent can hand produced files back |
| `checkRunning(boolean)` | `true` | Reject concurrent calls on the same session instead of queueing |

<Note>

`webHttpClient` exists mainly to force HTTP/1.1. The default client negotiates HTTP/2 with an
automatic HTTP/1.1 fallback; a few servers fail that negotiation, and injecting an HTTP/1.1-only
client here fixes it without touching tool code.

</Note>

## Workspace and context

| Method | Default | What it does |
|--------|---------|--------------|
| `workspace(Path)` / `workspace(String)` | see resolution order | Workspace root holding `AGENTS.md`, `MEMORY.md`, `skills/`, `subagents/`, `tools.json` |
| `additionalContextFile(String)` | none | Workspace-relative file inlined in full into the system prompt; call repeatedly to add several |
| `maxContextTokens(int)` | `8000` | Budget for `MEMORY.md` injection |
| `useLegacyXmlWorkspaceContext(boolean)` | `false` | Render workspace context as legacy XML instead of markdown |

When `workspace(...)` is not called, `build()` resolves it in this order: the builder value, then
the `agentscope.workspace` system property, then the `AGENTSCOPE_WORKSPACE` environment variable,
then `${user.dir}/.agentscope/workspace`. See [Workspace](/v2/en/docs/harness/workspace) for the
directory layout and how each file is loaded.

## Filesystem and sandbox

| Method | Default | What it does |
|--------|---------|--------------|
| `filesystem(LocalFilesystemSpec)` | local | Local disk plus shell, with a path allow-list |
| `filesystem(RemoteFilesystemSpec)` | — | Redis / JDBC / OSS-backed shared store |
| `filesystem(SandboxFilesystemSpec)` | — | Files and commands isolated in a Docker / K8s sandbox |
| `filesystemRoute(String, AbstractFilesystem)` | none | Mount an extra filesystem under a path prefix, alongside the primary one |
| `abstractFilesystem(AbstractFilesystem)` | — | Escape hatch: supply a custom implementation directly |
| `distributedStore(DistributedStore)` | `null` | Supplies state store, remote store, and snapshot spec together in one call |

See [Filesystem](/v2/en/docs/harness/filesystem) and [Sandbox](/v2/en/docs/harness/sandbox).

## State, memory, and history

| Method | Default | What it does |
|--------|---------|--------------|
| `stateStore(AgentStateStore)` | `JsonFileAgentStateStore` under `~/.agentscope/state/<agentId>/` | Where `AgentState` is persisted per `(userId, sessionId)` |
| `defaultSessionId(String)` | agent `name` | Session id used when a call's `RuntimeContext` carries none |
| `memory(MemoryConfig)` | `MemoryConfig.defaults()` | Long-term memory prompts and trigger policy |
| `compaction(CompactionConfig)` | default config | When and how conversation history is compacted |
| `toolResultEviction(ToolResultEvictionConfig)` | defaults | Offload oversized tool results to disk with a placeholder |
| `transcriptStore(TranscriptStore)` | default | Override the segmented append store for session transcripts |
| `transcriptTenant(String)` | `"default"` | Tenant segment used in transcript object keys |

See [Memory](/v2/en/docs/harness/memory) and [Compaction](/v2/en/docs/harness/compaction).

## Subagents

| Method | Default | What it does |
|--------|---------|--------------|
| `subagent(SubagentDeclaration)` | none | Declare one subagent programmatically |
| `subagents(List<SubagentDeclaration>)` | none | Declare several at once |
| `subagentFactory(String, Function<String, Agent>)` | none | Fully custom construction for a given agent id |
| `subagentFactory(String, String, Function<String, Agent>)` | none | Same, plus a description shown to the orchestrator |
| `taskRepository(TaskRepository)` | default | Where background subagent task records are stored |
| `externalSubagentTool(Object)` | `null` | Inject an external subagent tool, typically `SessionsTool` |

Subagents can also be declared as files in `workspace/subagents/`, with no code change. See
[Subagent](/v2/en/docs/harness/subagent).

## Skills

| Method | Default | What it does |
|--------|---------|--------------|
| `skillRepository(AgentSkillRepository)` | none | Add one skill source (Git, Nacos, MySQL, classpath) |
| `skillRepositories(List<…>)` | none | Add several sources at once |
| `projectGlobalSkillsDir(Path)` | `null` | Project-global skills directory, layered below marketplace and workspace skills |
| `enableSkills(String...)` | all | Allow-list: only these skills are exposed |
| `disableSkills(String...)` | none | Deny-list: everything except these |
| `skillsEnabled(boolean)` | `true` | Turn all skills on or off at once |
| `skillFilter(SkillFilter)` | `null` | Full control over which skills are visible |
| `enableSkillManageTool(…)` | off | Let the agent create / edit / archive its own workspace skills |
| `enableSkillPromotionGate(…)` | off | Promotion gate and visibility filter chain for agent-authored skills |
| `enableSkillCurator(SkillCuratorConfig)` | off | Background skill curator; requires `enableSkillManageTool` |

`enableSkills` and `disableSkills` are shorthands that both write `skillFilter`, so the last one
called wins:

```java
// Expose only these two skills, whatever the repositories provide.
HarnessAgent.builder()
        .name("agent")
        .sysPrompt("…")
        .model("dashscope:qwen-plus")
        .enableSkills("code-review", "changelog")
        .build();
```

See [Skill](/v2/en/docs/harness/skill).

## Plan Mode

| Method | Default | What it does |
|--------|---------|--------------|
| `enablePlanMode()` / `enablePlanMode(boolean)` | off | Read-only think-first phase with a human exit gate |
| `planFileDirectory(String)` | `plans/` | Where plan files are written |
| `allowShellInPlanMode()` / `allowShellInPlanMode(boolean)` | `false` | Allow the shell tool during plan mode |

Plan mode is strictly read-only by default, and the shell is denied because it is dual-use and
cannot be classified read-only by name. Opt in when shell-based investigation is needed. See
[Plan Mode](/v2/en/docs/harness/plan-mode).

## Middleware and hooks

| Method | Default | What it does |
|--------|---------|--------------|
| `middleware(MiddlewareBase)` | none | Add one middleware; yours run **before** the harness built-ins |
| `middlewares(List<? extends MiddlewareBase>)` | none | Add several at once |
| `hook(Hook)` / `hooks(List<Hook>)` | none | Lower-level lifecycle hooks |
| `enableAgentTracingLog(boolean)` | `true` | Agent execution trace logging via `AgentTraceMiddleware` |

See [Middleware](/v2/en/docs/building-blocks/middleware).

## Teams, message bus, and async tools

| Method | Default | What it does |
|--------|---------|--------------|
| `teamsMode(TeamClient, TeamContext)` | off | AgentTeams mode: attaches `TeamsMiddleware` and the role-clipped `team` tool |
| `teamsMode(TeamClient, TeamContext, String)` | off | Same, bound to a session id so control-plane team events reach this agent |
| `messageBus(MessageBus)` | `null` | Inbox-based delivery; registers `InboxMiddleware` to drain the inbox before each reasoning step |
| `asyncToolTimeout(Duration)` | `null` | Offload tools that exceed the timeout to the background; **requires `messageBus`** |
| `asyncToolRegistry(AsyncToolRegistry)` | `null` | Tracks async tool executions so stale ones can be detected and cleaned up |

## Turning built-ins off

Every harness capability is on by default where it is safe to be. These switches remove them —
useful for debugging, for trimming the tool surface, or for embedding the agent somewhere the
capability does not apply:

| Method | What it disables |
|--------|------------------|
| `disableWorkspaceContext()` | system-prompt injection of `AGENTS.md` / `MEMORY.md` / `knowledge/` |
| `disableSessionPersistence()` | automatic `AgentState` persistence |
| `disableCompaction()` | conversation compaction entirely |
| `disableToolResultEviction()` | offloading of oversized tool results |
| `disableTranscript()` | the independent session-transcript middleware |
| `disableMemoryHooks()` | memory flush and background maintenance |
| `disableMemoryTools()` | `memory_search` / `memory_get` / `memory_save` / `session_search` |
| `disableFilesystemTools()` | the built-in filesystem tool |
| `disableShellTool()` | the built-in shell tool |
| `disableWebTools()` | the Tavily-backed `web_search` / `web_fetch` tools |
| `disableSubagents()` | the whole subagent subsystem |
| `disableDynamicSubagents()` | runtime subagent creation, keeping declared ones |
| `disableDynamicSkills()` | per-turn skill re-merge; falls back to a one-shot merge at build time |
| `disableDefaultWorkspaceSkills()` | the default namespaced workspace skill repository |
| `disableToolsConfig()` | reading `workspace/tools.json` |
| `disableAtPathExpansion()` | expanding `@path` references in user messages into attached file blocks |

A minimal agent with the tool surface cut back to just your own tools:

```java
HarnessAgent agent =
        HarnessAgent.builder()
                .name("narrow-agent")
                .sysPrompt("You answer questions using only the provided tools.")
                .model("dashscope:qwen-plus")
                .toolkit(myToolkit)
                .disableShellTool()
                .disableFilesystemTools()
                .disableWebTools()
                .disableSubagents()
                .disableMemoryTools()
                .build();
```

## Configuration sources outside code

Two settings can be supplied from the environment, so the same image runs in several deployments:

| Setting | System property | Environment variable |
|---------|-----------------|----------------------|
| Workspace root | `agentscope.workspace` | `AGENTSCOPE_WORKSPACE` |
| Model API keys | — | `DASHSCOPE_API_KEY`, `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `DEEPSEEK_API_KEY`, `GEMINI_API_KEY` |

```dockerfile
ENV AGENTSCOPE_WORKSPACE=/data/agent-workspace
ENV DASHSCOPE_API_KEY=sk-...
```

Everything else is set in code on the builder. A blank value for the workspace property or variable
is treated as unset and falls through to the next source.

## Related pages

- [Harness Architecture](/v2/en/docs/harness/architecture) — how these capabilities compose
- [Workspace](/v2/en/docs/harness/workspace) — the directory layout each option reads
- [ReActAgent builder fields](/v2/en/docs/building-blocks/agent#builder-fields) — the core builder
- [Permission System](/v2/en/docs/building-blocks/permission-system)
- [Middleware](/v2/en/docs/building-blocks/middleware)
