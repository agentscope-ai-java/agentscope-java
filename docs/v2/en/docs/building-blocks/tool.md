---
title: Tool
description: Define, register, and manage the capabilities an agent can call
---

## Overview

Tools are how an agent acts on the world — running business operations, calling APIs, reading and writing data. Each tool exposes itself to the LLM as a JSON Schema, and the agent invokes it through a unified interface.

AgentScope organizes tool-related building blocks under three concepts:

- **Tool** — any object implementing the `AgentTool` contract (typically by extending `ToolBase`) or any plain class whose methods are annotated with `@Tool`. Java refers to the latter as *reflective function tools* — `Toolkit#registerTool(Object)` registers them by reflection automatically.
- **Toolkit** — the container that registers tools, MCP clients, and skills, exposes their JSON schemas to the model, and dispatches each tool call to the matching tool object.
- **Tool Group** — a named bundle of tools / MCP clients / skills that can be activated or deactivated as a unit. The agent uses a built-in meta tool to switch groups at runtime, keeping the context focused.

```java
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.builtin.TodoTools;

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new TodoTools());
toolkit.registerTool(new MyCustomTools());
```

When you only call `registerTool(Object)`, every `@Tool` method on the registered object joins the reserved `"basic"` group — always active. Add MCP clients, tool groups, or skills to extend the agent further — see the sections below.

## Java tools

A Java tool is any object satisfying the `AgentTool` contract. AgentScope ships an abstract base class `ToolBase` for declaring tools with explicit parameter schemas, plus a reflective adapter that wraps plain methods into tools.

### AgentTool / ToolBase contract

`ToolBase` is the abstract `AgentTool` implementation. The table below lists its properties and methods.

Properties exposed to the agent and runtime:

| Method | Type | Description |
|--------|------|-------------|
| `getName()` | `String` | The tool name shown to the agent |
| `getDescription()` | `String` | Description shown to the agent |
| `getParameters()` | `Map<String, Object>` | JSON Schema describing the parameters |
| `isConcurrencySafe()` | `boolean` | Can the tool be called concurrently? |
| `isReadOnly()` | `boolean` | Is the tool read-only / side-effect-free? |
| `isExternalTool()` | `boolean` | When `true`, execution is delegated externally (see [external execution](#external-execution-tools)) |
| `isStateInjected()` | `boolean` | When `true`, the framework injects `AgentState` as an extra parameter |
| `isMcp()` | `boolean` | Did the tool come from an MCP server? |
| `getMcpName()` | `String` | The MCP server name when `isMcp()` is `true` |

Methods that integrate with the execution flow and the permission system:

| Method | Required | Description |
|--------|----------|-------------|
| `checkPermissions(toolInput, context)` | yes | Runtime permission check before execution; returns `Mono<PermissionDecision>` |
| `matchRule(ruleContent, toolInput)` | optional | Custom rule matcher for the permission system; returns `boolean` |
| `generateSuggestions(toolInput)` | optional | Generate suggested rules from the current invocation; returns `List<PermissionRule>` |
| `callAsync(param)` | optional | Tool execution; returns `Mono<ToolResultBlock>`. External tools do not implement this. |

### Built-in tools

Two families ship with AgentScope, and they reach the agent differently.

**Core built-ins** live in `agentscope-core` and are registered by you:

| Tool | Parameters | Read-only |
|------|------------|-----------|
| `todo_write` | `todos` (`List<TodoItem>`, required) — the **complete** updated list; it replaces the existing one | no |

```java
Toolkit toolkit = new Toolkit();
toolkit.registerTool(new io.agentscope.core.tool.builtin.TodoTools());
```

**Harness built-ins** live in `agentscope-harness` and `HarnessAgent` registers them automatically — you turn them *off* rather than on (see [Configuration](/v2/en/docs/harness/configuration#turning-built-ins-off)).

Filesystem tools, from `FilesystemTool`, removed with `disableFilesystemTools()`:

| Tool | Parameters | Read-only |
|------|------------|-----------|
| `read_file` | `path` (required) · `offset` (`Integer`, default `0`) · `limit` (`Integer`, default `0` = all lines) | yes |
| `write_file` | `path` (required) · `content` (required) — creates parent directories | no |
| `edit_file` | `path` · `old_string` · `new_string` (all required) · `replace_all` (`Boolean`, default `false`) — `old_string` must be unique unless `replace_all` | no |
| `list_files` | `path` (required) | yes |
| `glob_files` | `pattern` (required, e.g. `**/*.java`) · `path` (base dir) · `limit` (`Integer`, default 200) | yes |
| `grep_files` | `pattern` (required, literal text) · `path` · `glob` (e.g. `*.java`) · `limit` (`Integer`, default 100) | yes |

Shell tool, from `ShellExecuteTool`, removed with `disableShellTool()`:

| Tool | Parameters | Read-only |
|------|------------|-----------|
| `execute` | `command` (required) · `working_directory` (relative to workspace root) · `timeout` (`Integer`, seconds, default `30`) | no |

Web tools, from `WebTools`, removed with `disableWebTools()`:

| Tool | Parameters | Read-only |
|------|------------|-----------|
| `web_fetch` | `url` (required) · `max_chars` (`Integer`, default `20000`) | yes |
| `web_search` | `query` (required) · `max_results` (`Integer`, default `5`) | yes |

Memory tools, removed with `disableMemoryTools()`: `memory_search`, `memory_get`, `memory_save`, `session_search` — see [Memory](/v2/en/docs/harness/memory).

<Note>

The shell tool is named `execute`, not `execute_shell_command` — its `@Tool` annotation sets no `name`, so the tool name falls back to the Java method name. This is the name to use in permission rules and in `tools.json` allow/deny lists.

</Note>

<Note>

The `Toolkit` automatically registers the `reset_tools` meta tool and the `load_skill_through_path` skill viewer tool when extra tool groups or skills are present — you don't need to instantiate them manually. See [self-managed tools](#self-managed-tools) and [Skill](#skill).

</Note>

### Registering tools on a `Toolkit`

Everything an agent can call is registered on a `Toolkit`, which is then handed to the agent builder. `registerTool(Object)` is the common case — it reflectively scans the object for `@Tool` methods — but the toolkit also accepts pre-built tool instances, schema-only external tools, MCP clients, and tool groups.

```java
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.builtin.TodoTools;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.harness.agent.HarnessAgent;

Toolkit toolkit = new Toolkit();

// 1. Annotated methods on a plain object — one tool per @Tool method
toolkit.registerTool(new MyDomainTools());
toolkit.registerTool(new TodoTools());

// 2. A ToolBase subclass, registered as a single tool instance
toolkit.registerAgentTool(new WebSearchTool());

// 3. An MCP server — registers every tool the server exposes
McpClientWrapper amap =
        McpClientBuilder.streamableHttp()
                .name("amap")
                .url("https://mcp.amap.com/mcp?key=" + System.getenv("AMAP_API_KEY"))
                .build();
toolkit.registerMcpClient(amap).block();

HarnessAgent agent =
        HarnessAgent.builder()
                .name("assistant")
                .sysPrompt("You are a helpful assistant.")
                .model("dashscope:qwen-plus")
                .toolkit(toolkit)
                .build();
```

| Method | Registers |
|--------|-----------|
| `registerTool(Object)` | Every `@Tool`-annotated method found on the object |
| `registerAgentTool(AgentTool)` | One `AgentTool` / `ToolBase` instance directly |
| `registerSchema(ToolSchema)` | One schema-only external tool — the agent sees it, execution suspends for an external worker |
| `registerSchemas(List<ToolSchema>)` | Several schema-only external tools at once |
| `registerMcpClient(McpClientWrapper)` | Every tool exposed by an MCP server; returns `Mono<Void>`, so `block()` or chain it |
| `registerMetaTool()` | The `reset_tools` meta tool for agent-managed tool groups |

<Note>

`registerMcpClient` is asynchronous. Calling it without `block()` (or subscribing) leaves the MCP tools unregistered when `build()` runs, and the agent silently starts without them.

</Note>

#### Managing tools after registration

Tool groups let you expose a subset of the toolkit at a time, which keeps the schema the model sees small. Registration is not one-way — tools and groups can be added and removed while the agent is running:

| Method | Effect |
|--------|--------|
| `createToolGroup(name, description)` | Create a group, active by default |
| `createToolGroup(name, description, active)` | Create a group with an explicit initial activation state |
| `registerToolGroup(ToolGroup)` | Register a pre-built `ToolGroup` instance or subclass |
| `addToolToGroup(groupName, toolName)` | Move an already-registered tool into a group |
| `setActiveGroups(List<String>)` | Replace the set of currently active groups |
| `removeToolGroups(List<String>)` | Remove groups and every tool inside them |
| `removeTool(String)` | Remove one tool by name |
| `removeToolIfSame(String, AgentTool)` | Remove only if the registered instance is the expected one — the safe form when several components share a toolkit |
| `removeMcpClient(String)` | Remove an MCP server and all of its tools; returns `Mono<Void>` |

See [self-managed tools](#self-managed-tools) for letting the agent switch groups itself.

#### Inspecting what is registered

To see what the agent will actually be offered — for a health check, a startup assertion, or a test — read the toolkit back:

```java
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.AgentTool;
import java.util.List;
import java.util.Set;

Set<String> names = toolkit.getToolNames();
System.out.println("registered: " + names);

// The exact schemas the model will receive, honoring active tool groups
List<ToolSchema> schemas = toolkit.getToolSchemas();
for (ToolSchema schema : schemas) {
    System.out.println(schema.getName() + " -> " + schema.getParameters());
}

// Fail fast at startup if an expected tool never registered
// (a common symptom of an un-blocked registerMcpClient call)
if (!names.contains("amap_maps_geo")) {
    throw new IllegalStateException("MCP tools missing: " + names);
}

AgentTool tool = toolkit.getTool("read_file");
```

| Method | Returns |
|--------|---------|
| `getToolNames()` | `Set<String>` — names of every registered tool |
| `getTool(String)` | `AgentTool` — one tool by name |
| `getToolSchemas()` | `List<ToolSchema>` — the schemas sent to the model, filtered by the toolkit's currently active groups |
| `getToolSchemas(Collection<String>)` | `List<ToolSchema>` — same, but filtered by an explicitly supplied group set; the stateless per-call variant, so it ignores the toolkit's shared activation flags |
| `getActiveGroups()` | `List<String>` — names of the currently active tool groups |

Each `ToolSchema` exposes `getName()`, `getDescription()`, `getParameters()` (the JSON Schema map), `getOutputSchema()`, and `getStrict()`.

<Tip>

`getToolSchemas()` is the ground truth for what the model sees. When a tool is registered but never called, print it and compare against the [parameter schema rules](#parameter-schemas-toolparam) — a `Map` parameter or a missing `@ToolParam` is the usual cause.

</Tip>

### Custom tools (annotation-based)

The lightest-weight way: annotate plain methods with `@Tool` and `@ToolParam`, then call `Toolkit#registerTool(Object)`. The framework derives the JSON schema from Java types and the `description` for the agent.

```java
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class SimpleTools {

    @Tool(
            name = "get_current_time",
            description = "Returns the current time in a given IANA timezone.",
            readOnly = true,
            concurrencySafe = true)
    public String getCurrentTime(
            @ToolParam(name = "timezone", description = "IANA timezone, e.g. Asia/Shanghai")
                    String timezone) {
        return LocalDateTime.now(ZoneId.of(timezone))
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new SimpleTools());
```

Common `@Tool` attributes:

| Attribute | Type | Description |
|-----------|------|-------------|
| `name` | `String` | Tool name (defaults to the method name) |
| `description` | `String` | Description shown to the agent |
| `readOnly` | `boolean` | Whether the tool is read-only (default `false`) |
| `concurrencySafe` | `boolean` | Whether the tool is safe for concurrent calls (default `false`) |
| `stateInjected` | `boolean` | Inject `AgentState` as an extra parameter (default `false`) |
| `dangerousFiles` / `dangerousDirectories` | `String[]` | Append custom dangerous paths |
| `converter` | `Class<? extends ToolResultConverter>` | Custom conversion of return values into `ToolResultBlock` |

### Parameter schemas (`@ToolParam`)

Only parameters annotated with `@ToolParam` become part of a tool's JSON schema. Every other parameter on the method signature is either framework-injected (`ToolEmitter`, `Agent`, `AgentState`, `RuntimeContext`) or resolved from the runtime context, and never reaches the model — see [receiving context](#receiving-context).

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `name` | `String` | *mandatory* | Property name in the schema. Mandatory because Java does not preserve parameter names at runtime; use snake_case for LLM compatibility |
| `description` | `String` | `""` | Written to the property's `description`. Left out of the schema when empty |
| `required` | `boolean` | `true` | Whether the property is listed in the schema's `required` array |

#### Java types to JSON Schema

Schemas are derived from the parameter's **generic** type (`Parameter#getParameterizedType()`), so type arguments on collections are preserved rather than erased:

| Java parameter type | Generated property schema |
|---------------------|---------------------------|
| `String` | `{"type": "string"}` |
| `int`, `Integer`, `long`, `Long` | `{"type": "integer"}` |
| `double`, `Double`, `float` | `{"type": "number"}` |
| `boolean`, `Boolean` | `{"type": "boolean"}` |
| `MyEnum` | `{"type": "string", "enum": ["A", "B"]}` — the constant names |
| `String[]`, `List<String>`, `Set<String>` | `{"type": "array", "items": {"type": "string"}}` |
| `List<Item>` | `{"type": "array", "items": {...}}`, where `items` is the object schema of `Item` |
| `List<List<String>>` | an `array` whose `items` are themselves an `array` of `string` |
| `Map<String, Integer>` | `{"type": "object"}` — see the caveat below |
| `Item` (POJO) | `{"type": "object", "properties": { … }}` built from `Item`'s fields |

A tool taking two `List<String>` parameters, one required and one not:

```java
@Tool(name = "tag_files", description = "Attach tags to a set of files.")
public String tagFiles(
        @ToolParam(name = "paths", description = "Absolute file paths to tag")
                List<String> paths,
        @ToolParam(name = "tags", description = "Tags to attach", required = false)
                List<String> tags) {
    // Implementation
}
```

generates:

```json
{
  "type": "object",
  "properties": {
    "paths": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Absolute file paths to tag"
    },
    "tags": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Tags to attach"
    }
  },
  "required": ["paths"]
}
```

Note that `description` lands on the array property itself, never on `items`. To describe the elements, put the description on the fields of the element type instead (see [`@ToolParam` on POJO fields](#toolparam-on-pojo-fields)).

<Warning>

`Map<K, V>` parameters generate a bare `{"type": "object"}`: the key and value types are **not** described, so the model gets no guidance about what belongs inside and nothing is validated. When the shape is known, take a POJO parameter — or a `List` of small POJOs — instead of a `Map`.

</Warning>

#### Nested types and `$defs`

Nested POJOs are inlined into the property schema. A type referenced more than once, or a recursive type, is instead emitted as a `$defs` entry that the property points at with `$ref`. Because each parameter's schema is generated independently, AgentScope hoists those definitions from the parameter level up to the root of the tool schema, so that `#/$defs/TypeName` pointers resolve against the document root:

```json
{
  "type": "object",
  "properties": {
    "recipe": {
      "type": "object",
      "properties": {
        "materials": { "type": "array", "items": { "$ref": "#/$defs/Material" } },
        "substitutes": { "type": "array", "items": { "$ref": "#/$defs/Material" } }
      }
    }
  },
  "required": ["recipe"],
  "$defs": {
    "Material": { "type": "object", "properties": { "name": { "type": "string" } } }
  }
}
```

Definition keys are plain type names, so two *different* classes with the same simple name (for example a `Material` from two different packages) reaching the same tool method collide, and schema generation fails with `IllegalStateException: Conflicting schema definition found for key: Material`. Rename one of them, or funnel both through a single POJO parameter.

#### How `required` affects the schema

`required` does **not** control whether a parameter appears in the schema. Every `@ToolParam` parameter is always listed under `properties`; `required` only controls membership in the schema's top-level `required` array:

- `required = true` (the default) — the property name is added to `required`.
- `required = false` — the property stays in `properties` but is left out of `required`, so the model may omit it.
- When no parameter is required, the `required` key is **omitted entirely** rather than emitted as an empty array.

At call time `ToolExecutor` validates the model's arguments against this schema before invoking your method. A missing required property is rejected and the validation error is handed back to the model to retry, so the method is never entered. An explicit `null` for an optional property is treated the same as omitting it.

<Warning>

An omitted optional parameter is passed to your method as `null`. Declare optional parameters with **boxed** types (`Integer`, `Double`, `Boolean`) rather than primitives: a primitive parameter marked `required = false` fails at invocation when the model omits it, because `null` cannot be passed to an `int` or a `double`.

</Warning>

#### `@ToolParam` on POJO fields

`@ToolParam` also applies to the fields of a POJO parameter, where it renames the property, supplies its description and sets whether it is required:

```java
public class Location {

    @ToolParam(name = "city_name", description = "The city name")
    private String city;

    @ToolParam(name = "zip_code", description = "The zip code", required = false)
    private String zip;

    private String country; // no annotation → optional, keeps the field name
}
```

```json
{
  "type": "object",
  "properties": {
    "city_name": { "type": "string", "description": "The city name" },
    "zip_code": { "type": "string", "description": "The zip code" },
    "country": { "type": "string" }
  },
  "required": ["city_name"]
}
```

Two differences from method parameters are worth remembering:

- A field without `@ToolParam` is still part of the schema, as an **optional** property under its own Java field name. A method parameter without `@ToolParam` is excluded from the schema altogether.
- A blank `name` falls back to the Java field name, and a blank `description` is left out of the schema.

Jackson's `@JsonPropertyDescription` and `@JsonProperty(required = true)` are honored on fields as well, so existing Jackson-annotated models can be used as tool parameters without re-annotating them.

### Custom tools (extending `ToolBase`)

When you need a custom permission policy, external execution, or a more complex schema, extend `ToolBase`:

```java
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

public class WebSearchTool extends ToolBase {

    public WebSearchTool() {
        super(
                ToolBase.builder()
                        .name("WebSearch")
                        .description("Search the web for information on a given query.")
                        .inputSchema(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "query", Map.of(
                                                "type", "string",
                                                "description", "The search query.")),
                                "required", List.of("query")))
                        .readOnly(true)
                        .concurrencySafe(true));
    }

    @Override
    public Mono<PermissionDecision> checkPermissions(
            Map<String, Object> toolInput, PermissionContextState context) {
        return Mono.just(PermissionDecision.allow("Web search is read-only."));
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        String query = (String) param.getInput().get("query");
        return doSearchAsync(query)
                .map(text ->
                        ToolResultBlock.builder()
                                .id(param.getId())
                                .name(getName())
                                .output(List.of(TextBlock.builder().text(text).build()))
                                .build());
    }
}
```

### External execution tools

External-execution tools delegate the actual work outside the agent runtime — typically to a human operator or an external system. The agent emits `RequireExternalExecutionEvent` and pauses. When the next call feeds back matching `ToolResultBlock`s, the agent emits `ExternalExecutionResultEvent` with the same `replyId` before continuing.

This pattern is the foundation of [human-in-the-loop](/v2/en/docs/building-blocks/agent#human-in-the-loop) flows — some actions need human approval or human execution.

To create an external tool, set `externalTool` to `true` and skip implementing `callAsync`:

```java
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.ToolBase;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

public class HumanApprovalTool extends ToolBase {

    public HumanApprovalTool() {
        super(
                ToolBase.builder()
                        .name("HumanApproval")
                        .description("Request human approval for a sensitive operation.")
                        .inputSchema(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "action", Map.of("type", "string"),
                                        "reason", Map.of("type", "string")),
                                "required", List.of("action", "reason")))
                        .readOnly(false)
                        .concurrencySafe(true)
                        .externalTool(true));
    }

    @Override
    public Mono<PermissionDecision> checkPermissions(
            Map<String, Object> toolInput, PermissionContextState context) {
        return Mono.just(PermissionDecision.allow("External tool dispatch is always allowed."));
    }
}
```

Runnable examples: `agentscope-examples/documentation/.../tool/ToolBaseExample.java`, `tool/ToolExecutionContextExample.java`.

## Receiving context

The [`RuntimeContext`](/v2/en/docs/building-blocks/agent#runtimecontext-per-call-context) passed to `agent.call(msgs, runtimeContext)` is forwarded to every tool invocation in that reply. Tools can read it in two ways: annotation-based tools through automatic injection, and `ToolBase.callAsync` through `ToolCallParam`.

### Automatic injection (`@Tool` methods)

Inside a `@Tool` method, any parameter **without `@ToolParam`** is treated as framework-injected. The resolution order:

| Parameter type | Source |
|----------------|--------|
| `ToolEmitter` | Streaming emitter (no-op when none configured) |
| `Agent` | The current agent instance |
| `AgentState` | The per-session state for the current call (via `RuntimeContext.getAgentState()`) |
| `RuntimeContext` | The current per-call context |
| `ToolExecutionContext` | `runtimeContext.asToolExecutionContext()` (compatibility shim, deprecated) |
| Any other user POJO type | `runtimeContext.get(ParamType.class)` — i.e. an object the caller registered via `RuntimeContext.builder().put(ParamType.class, value)` |

"User POJO" means: no `@ToolParam`, not primitive, not `ContentBlock` / `Msg`, not under `java.*` / `javax.*`. Every other parameter (those with `@ToolParam`, or that fall outside the above types) is read from the LLM-supplied JSON by name.

```java
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

public record UserContext(String username, String locale) {}

public class PersonalizedTools {

    @Tool(name = "greet", description = "Greet the user with a custom greeting")
    public String greet(
            @ToolParam(name = "greeting", description = "Greeting word, e.g. 'Hello'")
                    String greeting,                  // ← supplied by the model
            UserContext userCtx) {                    // ← injected by the framework
        return greeting + ", " + (userCtx == null ? "unknown" : userCtx.username()) + "!";
    }
}
```

The caller registers the POJO by type once; every `call` then routes the matching instance to any tool that asks for it:

```java
RuntimeContext ctx =
        RuntimeContext.builder()
                .put(UserContext.class, new UserContext("alice", "en"))
                .userId("alice")
                .build();

agent.call(List.of(new UserMessage("Greet me.")), ctx).block();
```

The model never sees `userCtx` — it is not part of the tool's JSON schema. Full example: `agentscope-examples/documentation/.../tool/ToolExecutionContextExample.java`.

### Accessing context in `ToolBase.callAsync`

Tools that extend `ToolBase` read context through `ToolCallParam`:

```java
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;

public class TenantAwareTool extends ToolBase {

    public TenantAwareTool() {
        super(/* builder ... */);
    }

    @Override
    public Mono<io.agentscope.core.message.ToolResultBlock> callAsync(ToolCallParam param) {
        RuntimeContext rc = param.getRuntimeContext();
        String tenantId = rc != null ? rc.getUserId() : null;
        TenantConfig cfg = rc != null ? rc.get(TenantConfig.class) : null;
        // ... apply tenantId / cfg ...
    }
}
```

`ToolCallParam` also exposes `getAgent()`, `getInput()`, `getEmitter()`, `getToolUseBlock()`, and the deprecated `getContext()`. Prefer `getRuntimeContext()` in new code.

### Coordinating between hooks and tools

The `RuntimeContext` string layer (`put(String, Object)` / `get(String)`) is a short-lived channel between middleware and tools during a single `call` — a middleware can write at `onActing`/`onReasoning` and a tool that injects a `RuntimeContext` parameter reads it. The instance is unbound from the agent (along with all hooks) when the call finishes.

## MCP

AgentScope integrates with the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/), letting the agent talk to any MCP-compatible tool provider. The framework handles protocol negotiation, tool discovery, and result conversion.

Three transports are supported:

- **STDIO** — local process via stdin/stdout
- **SSE / Streamable HTTP** — remote HTTP long-connection

MCP tools are exposed in the toolkit under the namespace `mcp__{server_name}__{tool_name}` to avoid collisions; tools marked `readOnlyHint` are auto-allowed by the permission system.

### Registering MCP tools

Use `McpClientBuilder` to build an `McpClientWrapper`, then register it on the `Toolkit`:


<Tabs>


<Tab title="STDIO">

```java
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;

McpClientWrapper filesystem =
        McpClientBuilder.stdio()
                .name("filesystem")
                .command("mcp-server-filesystem")
                .args("--root", "/my/project")
                .build();

Toolkit toolkit = new Toolkit();
toolkit.registerMcpClient(filesystem).block();
```

</Tab>


<Tab title="Streamable HTTP">

```java
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;

McpClientWrapper weather =
        McpClientBuilder.streamableHttp()
                .name("weather")
                .url("https://api.weather.com/mcp")
                .header("Authorization", "Bearer xxx")
                .build();

Toolkit toolkit = new Toolkit();
toolkit.registerMcpClient(weather).block();
```

</Tab>


<Tab title="SSE">

```java
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;

McpClientWrapper search =
        McpClientBuilder.sse()
                .name("search")
                .url("https://api.search.com/mcp/sse")
                .build();

Toolkit toolkit = new Toolkit();
toolkit.registerMcpClient(search).block();
```

</Tab>


</Tabs>


Runnable examples: `agentscope-examples/documentation/.../mcp/McpStdioExample.java`, `mcp/McpSseExample.java`, `mcp/McpStreamableHttpExample.java`.

## Skill

Skills are markdown-based instruction sets that extend an agent's capabilities without writing new tool code. Each skill is a directory containing a `SKILL.md` file with frontmatter metadata and detailed instructions.

Unlike tools, skills are not directly callable. The agent reads skill instructions through an auto-registered viewer tool named `load_skill_through_path`, then carries them out using whatever tools it already has.

### Registering skills

Attach one or more `AgentSkillRepository` directly via `ReActAgent.builder().skillRepository(...)`. At `build()` time the builder auto-installs `DynamicSkillMiddleware`, which rebuilds the skill prompt and tool groups from the configured sources on every `call()`:

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.skill.repository.FileSystemSkillRepository;
import java.nio.file.Paths;

ReActAgent agent =
        ReActAgent.builder()
                .name("SkillCreator")
                .sysPrompt("...")
                .model(model)
                .skillRepository(new FileSystemSkillRepository(Paths.get("/path/to/skills"), false))
                .build();
```

Multiple `skillRepository(...)` calls append in order (low → high priority); when two repositories expose a skill with the same name, the later entry wins. Use `skillRepositories(List<AgentSkillRepository>)` to replace the list.

Reference implementations: `agentscope-examples/documentation/.../skill/AgentSkillExample.java`, `skill/SkillWithToolGroupExample.java`.

### How skills work

When skills are present, the `Toolkit` performs a two-phase setup.

Initialisation:

- The toolkit scans every registered skill source and collects each skill's name, description, and directory.
- It auto-registers the built-in viewer tool `load_skill_through_path` (implemented in `io.agentscope.core.skill.SkillToolFactory`) into the `skill-build-in-tools` group.
- It assembles a system-prompt fragment listing the available skills (names + descriptions) and instructing the agent to read full content via `load_skill_through_path`.

At runtime, the agent invokes the viewer with two required arguments:

| Parameter | Type | Description |
| --- | --- | --- |
| `skillId` | `string` (enum of registered skill IDs) | The skill to load. |
| `path` | `string` | Use `"SKILL.md"` to fetch the skill's markdown instructions, or an exact resource path declared by the skill such as `"references/guide.md"` or `"scripts/run.py"`. Do not pass `"."`, `"./"`, a directory, or an absolute path. |

Example tool call payload:

```json
{
  "name": "load_skill_through_path",
  "input": { "skillId": "pdf-extractor", "path": "SKILL.md" }
}
```

Each successful call has two effects:

1. Returns the requested content (the `SKILL.md` markdown, or the named resource file).
2. **Activates the skill** — its associated tool group is enabled in the `Toolkit`, so any tools bundled with the skill become callable for the rest of the turn. If the requested `path` does not exist, the viewer returns an error that lists the available resource paths (with `SKILL.md` first) so the agent can retry.


<Note>

A skill is not a tool — the agent cannot call it directly. The agent must read the instructions via `load_skill_through_path` first, then act on them with other tools.

</Note>


### Skill script execution: configuring shell tools

Skills only provide instructions — actual execution relies on the tools the agent already has. If a skill's instructions involve running scripts (e.g. `scripts/run.py`), the agent needs shell access:

- **`ReActAgent`** — register `ShellCommandTool` in the toolkit:

```java
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.coding.ShellCommandTool;
import io.agentscope.core.tool.file.ReadFileTool;
import io.agentscope.core.tool.file.WriteFileTool;

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new ShellCommandTool());
toolkit.registerTool(new ReadFileTool("/path/to/base/dir"));
toolkit.registerTool(new WriteFileTool("/path/to/base/dir"));

ReActAgent agent =
        ReActAgent.builder()
                .name("SkillAgent")
                .sysPrompt("...")
                .model(model)
                .toolkit(toolkit)
                .skillRepository(skillRepo)
                .build();
```

- **`HarnessAgent`** — the harness module ships workspace-aware shell and file tools (`execute`, `read_file`, `write_file`, etc.) out of the box; no extra registration needed.

### Skill + ToolGroup: on-demand tool disclosure

`SkillToolGroup` binds a group of tools to a skill name — the group activates automatically when the agent loads that skill, and stays hidden from the model's schema otherwise, reducing context noise.

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;

Toolkit toolkit = new Toolkit();

// 1. Create a tool group bound to a skill (initially inactive)
toolkit.createSkillToolGroup(
        "analysis-tools",                // group name
        "Data analysis tools",           // description
        false,                           // initially inactive
        "data-analysis");                // bound skill name

// 2. Register tools into that group
toolkit.registration()
        .tool(new AnalysisTools())
        .group("analysis-tools")
        .apply();

// 3. Build the agent with meta tool for model-driven group switching
ReActAgent agent =
        ReActAgent.builder()
                .name("AnalysisAgent")
                .sysPrompt("...")
                .model(model)
                .toolkit(toolkit)
                .skillRepository(skillRepo)
                .enableMetaTool(true)
                .build();
```

When the agent loads the `data-analysis` skill via `load_skill_through_path`, the `analysis-tools` group activates and its tools become immediately available. With `enableMetaTool(true)`, the model can also manage group activation via `reset_tools`.

Reference implementation: `agentscope-examples/documentation/.../skill/SkillWithToolGroupExample.java`.

## Self-managed tools

The built-in **meta tool** (`reset_tools`) lets the agent self-manage which tool groups are active at runtime, keeping the context focused — only the tools relevant to the current task are exposed to the model.

### Defining tool groups

`ToolGroup` is a named bundle of tools / MCP clients / skills. Register the group on the `Toolkit` and turn on the meta tool through the builder:

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolGroup;
import io.agentscope.core.tool.ToolGroupScope;

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new BasicTools());

ToolGroup database =
        new ToolGroup(
                "database",
                "Tools for database operations.",
                ToolGroupScope.SESSION,
                /* active = */ false);
database.addTool("db_query");
database.addTool("db_migrate");
toolkit.registerTool(new DatabaseTools());
toolkit.registerToolGroup(database);

ToolGroup deployment =
        new ToolGroup(
                "deployment",
                "Tools for deploying services.",
                ToolGroupScope.SESSION,
                /* active = */ false);
deployment.addTool("deploy");
deployment.addTool("rollback");
toolkit.registerTool(new DeploymentTools());
toolkit.registerToolGroup(deployment);

ReActAgent agent =
        ReActAgent.builder()
                .name("router")
                .toolkit(toolkit)
                .enableMetaTool(true)
                .build();
```

`ToolGroup` takes a name, a description, a scope (`ToolGroupScope`), and an initial active flag. The reserved name `"basic"` is auto-populated by `Toolkit#registerTool(Object)` and is always active.

### Using the meta tool

Whenever there's at least one non-basic tool group and `enableMetaTool(true)` is on, the `Toolkit` auto-registers `reset_tools` and exposes its schema to the agent. Each non-basic group becomes a boolean field; calling the meta tool declares the desired final state.

Runtime behavior:

- Tools in the `"basic"` group are always exposed; the meta tool does not touch them.
- Each `reset_tools` call **wholly overwrites** the active set — any non-basic group not explicitly set to `true` is deactivated, regardless of its previous state.
- For each group that just became active, its description and (if provided) instructions are spliced into the meta tool's return value, telling the agent how to use it correctly.
- Tools in inactive groups do not appear in the agent's tool schema, leaving more context for the active toolset.


<Warning>

The meta tool's input represents the **final state** of all groups, not a delta. Any group not explicitly set to `true` is deactivated regardless of previous state.

</Warning>


## Further reading


<CardGroup cols={2}>



<Card title="Agent" href="/v2/en/docs/building-blocks/agent">


How agents orchestrate tool calls in the ReAct loop

</Card>


<Card title="Permission System" href="/v2/en/docs/building-blocks/permission-system">


Fine-grained control over which tools execute and when

</Card>


<Card title="Middleware" href="/v2/en/docs/building-blocks/middleware">


Use onion middlewares to intercept and rewrite tool calls

</Card>


<Card title="Human-in-the-Loop" href="/v2/en/docs/building-blocks/agent#human-in-the-loop">


External execution tools and approval workflows

</Card>



</CardGroup>
