# agentscope-core

The flagship, low-level module of AgentScope Java. It provides `ReActAgent` — a
reasoning-acting loop that wires together messages/events, tools, the permission
system, structured output, model resolution, and state persistence — without any
sandboxing, workspace, or orchestration layer on top. Reach for it when you want
direct control over agent behavior; reach for `agentscope-harness` when you also
need a filesystem workspace, subagents, or skills.

## Installation

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-core</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

Model providers (DashScope, OpenAI, Anthropic, Gemini, Ollama, …) ship as separate
extension modules and are discovered via Java SPI at runtime — add the one you need
alongside `agentscope-core`, e.g. `agentscope-extensions-model-dashscope`.

## Quick Start

### A bare ReActAgent

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import java.util.List;

ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .sysPrompt("You are a helpful assistant.")
                // Resolved by ModelRegistry; reads DASHSCOPE_API_KEY from the environment.
                // Swap the prefix to switch providers: "openai:...", "anthropic:...", "gemini:...".
                .model("dashscope:qwen-plus")
                .build();

Msg result =
        agent.call(List.of(new UserMessage("What files are in the current directory?")),
                        RuntimeContext.empty())
                .block();
System.out.println(result.getTextContent());
```

### Tool calling

Annotate any method with `@Tool` / `@ToolParam`, register the instance with a
`Toolkit`, and attach it to the agent builder — the LLM decides when to call it.

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;

public class WeatherTools {
    @Tool(name = "get_weather", description = "Get current weather for a city")
    public String getWeather(
            @ToolParam(name = "city", description = "City name") String city) {
        return "Sunny, 22C in " + city;
    }
}

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new WeatherTools());

ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .sysPrompt("You are a helpful assistant.")
                .model("dashscope:qwen-plus")
                .toolkit(toolkit)
                .build();
```

### Structured output

Pass a Java class (or a raw JSON Schema `JsonNode`) as the second argument to
`call` and read the parsed result back off the returned `Msg`:

```java
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import java.util.List;

public record WeatherResponse(String location, String temperature, String condition) {}

Msg result =
        agent.call(
                        List.of(new UserMessage("What's the weather in SF?")),
                        WeatherResponse.class,
                        RuntimeContext.empty())
                .block();

WeatherResponse weather = result.getStructuredData(WeatherResponse.class);
System.out.println(weather.location() + ": " + weather.temperature());
```

### Permission system

Tool calls go through a three-way decision (`ALLOW` / `DENY` / `ASK`) governed by a
`PermissionMode` plus explicit rules. Attach a `PermissionContextState` on the
builder:

```java
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;

PermissionContextState permCtx =
        PermissionContextState.builder()
                .mode(PermissionMode.DEFAULT)
                .addAllowRule(
                        "get_weather",
                        new PermissionRule(
                                "get_weather", null, PermissionBehavior.ALLOW, "userSettings"))
                .build();

ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .sysPrompt("You are a helpful assistant.")
                .model("dashscope:qwen-plus")
                .permissionContext(permCtx)
                .build();
```

### Multi-session state persistence

`ReActAgent` is stateless between calls — one instance can serve many users and
sessions concurrently. Set a `stateStore` and pass a per-call `RuntimeContext`
carrying the `(userId, sessionId)` pair; the agent loads and saves `AgentState`
automatically:

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.JsonFileAgentStateStore;
import java.nio.file.Paths;

ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .sysPrompt("You are a helpful assistant.")
                .model("dashscope:qwen-plus")
                .stateStore(new JsonFileAgentStateStore(
                        Paths.get(System.getProperty("user.home"), ".agentscope/sessions")))
                .build();

agent.call(List.of(new UserMessage("Hello")),
        RuntimeContext.builder().userId("alice").sessionId("session-1").build()).block();
```

## Learn more

- [Agent](../docs/v2/en/docs/building-blocks/agent.md) — builder fields, streaming
  events, `RuntimeContext`, human-in-the-loop, structured output, and state
  persistence in full detail.
- [Model](../docs/v2/en/docs/building-blocks/model.md) — credentials, `ModelRegistry`,
  and the provider extension modules.
- [Tool](../docs/v2/en/docs/building-blocks/tool.md) — `@Tool`/`@ToolParam`,
  `Toolkit`, MCP clients, and tool groups.
- [Permission System](../docs/v2/en/docs/building-blocks/permission-system.md) —
  modes, rules, and human-in-the-loop confirmation.
