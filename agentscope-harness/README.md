# agentscope-harness

Production runtime on top of `agentscope-core`'s `ReActAgent`: `HarnessAgent` adds a workspace-driven persona, layered long-term memory, cross-call session persistence, subagent orchestration, a pluggable/sandboxed filesystem, skill composition, and Plan Mode — all through one builder, without touching the core reasoning loop. Add this module when a bare ReAct agent needs to survive restarts, isolate multiple users, or delegate work to child agents.

## Installation

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-harness</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## Quick Start

`HarnessAgent.builder()` accepts any `ChatModelBase` (or a `ModelRegistry` string id) and a `Toolkit`, exactly like `ReActAgent`, plus a `workspace(...)` directory that becomes the agent's source of truth for persona, memory, and subagent definitions:

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
                .model(model)
                .toolkit(toolkit)
                .workspace(Paths.get(".agentscope/workspace"))
                .build();
```

`HarnessAgent` is stateless between calls and safe to reuse as a singleton across users — each call's `RuntimeContext` (`userId` + `sessionId`) decides which session state, memory, and workspace bucket it reads and writes:

```java
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;

RuntimeContext ctx = RuntimeContext.builder().userId("u-1").sessionId("s-1").build();

Msg reply = agent.call("What's the weather in Tokyo?", ctx).block();
```

### Declaring a subagent

Delegate context-heavy or parallelizable subtasks to a child `HarnessAgent` without registering it separately — declare it on the parent's builder alongside a `.model(String)` shortcut and an allowlisted tool set:

```java
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.subagent.WorkspaceMode;
import java.nio.file.Path;
import java.util.List;

HarnessAgent orchestrator =
        HarnessAgent.builder()
                .name("orchestrator")
                .model("dashscope:qwen-plus")
                .workspace(Paths.get(".agentscope/workspace"))
                .subagent(
                        SubagentDeclaration.builder()
                                .name("reviewer")
                                .description("Code review specialist")
                                .workspace(Path.of("./defs/reviewer"))
                                .workspaceMode(WorkspaceMode.ISOLATED)
                                .model("qwen3-max")
                                .steps(8)
                                .tools(List.of("read_file", "grep_files"))
                                .build())
                .build();
```

The parent can now invoke it mid-reasoning as `agent_spawn agent_id="reviewer" task="review this PR"` — sync (blocking, default) or background (`timeout_seconds=0`), with completed background results pushed back automatically before the parent's next reasoning step.

## Learn more

- [Harness Architecture](../docs/v2/en/docs/harness/architecture.md) — capability list, how state flows across `RuntimeContext` / workspace / `AgentStateStore`
- [Workspace](../docs/v2/en/docs/harness/workspace.md) — directory layout, `AGENTS.md`, `MEMORY.md`, `tools.json`
- [Subagent](../docs/v2/en/docs/harness/subagent.md) — declaration sources, sync/background dispatch, streaming forwarding
