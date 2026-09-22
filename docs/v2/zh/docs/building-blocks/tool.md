---
title: Tool
description: 定义、注册并管理 agent 可调用的能力
---

## 概述

Tool 是 agent 与外部世界交互的方式 —— 执行业务操作、调用 API、读写数据等。每个 tool 通过 JSON Schema 暴露给 LLM，agent 通过统一接口完成调用。

AgentScope 把 tool 相关的构件组织成三个概念：

- **Tool** —— 任意实现 `AgentTool` 接口（通常通过继承 `ToolBase`）或在普通类的方法上标注 `@Tool` 注解的对象。Java 端把后者称为 *reflective function tool*，由 `Toolkit#registerTool(Object)` 自动反射注册。
- **Toolkit** —— 容器，负责注册 tool、MCP 客户端与 skill，向模型暴露它们的 JSON schema，并把每次工具调用分发到对应的 tool 对象。
- **Tool Group** —— 一组带名称的 tool / MCP / skill 集合，可以作为整体激活或停用。Agent 在运行时通过内置 meta tool 切换 group，让上下文保持聚焦。

```java
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.builtin.TodoTools;

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new TodoTools());
toolkit.registerTool(new MyCustomTools());
```

只调用 `registerTool(Object)` 时，被注册对象上所有 `@Tool` 方法都进入特殊的 `"basic"` 组 —— 该组始终激活。追加 MCP 客户端、tool group 或 skill 即可拓展 agent 的能力 —— 见下文各节。

## Java Tool

Java tool 是任意满足 `AgentTool` 契约的对象。AgentScope 同时提供了一个 `ToolBase` 抽象基类用于显式建模带参数 schema 的 tool，以及一个反射适配器用于把普通方法包装成 tool。

### AgentTool / ToolBase 接口

`ToolBase` 是 `AgentTool` 的抽象实现，下表列出其属性与方法。

向 agent 与运行时描述 tool 的属性：

| 方法 | 类型 | 说明 |
|---|---|---|
| `getName()` | `String` | 暴露给 agent 的 tool 名称 |
| `getDescription()` | `String` | 面向 agent 的功能描述 |
| `getParameters()` | `Map<String, Object>` | 定义参数的 JSON Schema |
| `isConcurrencySafe()` | `boolean` | 是否可并发调用 |
| `isReadOnly()` | `boolean` | 是否只读、不产生副作用 |
| `isExternalTool()` | `boolean` | 为 `true` 时执行委派给外部（见 [定义外部执行 Tool](#定义外部执行-tool)） |
| `isStateInjected()` | `boolean` | 为 `true` 时框架注入 `AgentState` 参数 |
| `isMcp()` | `boolean` | 是否来自 MCP 服务 |
| `getMcpName()` | `String` | `isMcp()` 为 `true` 时所属 MCP 服务名 |

接入执行流程与权限系统的方法：

| 方法 | 必需 | 说明 |
|---|---|---|
| `checkPermissions(toolInput, context)` | 是 | 执行前的运行时权限检查；返回 `Mono<PermissionDecision>` |
| `matchRule(ruleContent, toolInput)` | 可选 | 权限系统中的自定义规则匹配；返回 `boolean` |
| `generateSuggestions(toolInput)` | 可选 | 基于本次工具调用生成建议规则；返回 `List<PermissionRule>` |
| `callAsync(param)` | 可选 | tool 的执行逻辑；返回 `Mono<ToolResultBlock>`。外部执行 tool 不需要实现。 |

### 使用内置 Tool

AgentScope 当前提供以下内置 tool：

| Tool | 说明 | 只读 |
|------|------|------|
| `TodoTools.todoWrite` | 维护当前会话的结构化任务列表（全列表替换语义） | 否 |

使用方式：

```java
Toolkit toolkit = new Toolkit();
toolkit.registerTool(new io.agentscope.core.tool.builtin.TodoTools());
```


<Note>

Toolkit 在出现额外 tool group 或 skill 时会自动注册 `reset_tools` meta tool 与 skill 查看器工具 `load_skill_through_path`，开发者无需手动实例化。详见 [自我管理 Tool](#自我管理-tool) 与 [Skill](#skill)。

</Note>


### 在 `Toolkit` 上注册工具

Agent 能调用的一切都注册在 `Toolkit` 上，再把它交给 Agent builder。最常见的是 `registerTool(Object)`——它会反射扫描对象上的 `@Tool` 方法——此外 toolkit 也接受预先构建好的工具实例、仅有 Schema 的外部工具、MCP 客户端以及 tool group。

```java
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.builtin.TodoTools;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.harness.agent.HarnessAgent;

Toolkit toolkit = new Toolkit();

// 1. 对象上的注解方法——每个 @Tool 方法对应一个工具
toolkit.registerTool(new MyDomainTools());
toolkit.registerTool(new TodoTools());

// 2. ToolBase 子类，作为单个工具实例注册
toolkit.registerAgentTool(new WebSearchTool());

// 3. MCP Server——注册该 Server 暴露的全部工具
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

| 方法 | 注册内容 |
|------|----------|
| `registerTool(Object)` | 在对象上找到的每个 `@Tool` 注解方法 |
| `registerAgentTool(AgentTool)` | 直接注册一个 `AgentTool` / `ToolBase` 实例 |
| `registerSchema(ToolSchema)` | 一个仅有 Schema 的外部工具——Agent 能看到它，执行时挂起交由外部 worker 完成 |
| `registerSchemas(List<ToolSchema>)` | 一次注册多个仅有 Schema 的外部工具 |
| `registerMcpClient(McpClientWrapper)` | MCP Server 暴露的全部工具；返回 `Mono<Void>`，需 `block()` 或串联订阅 |
| `registerMetaTool()` | 用于 Agent 自管理 tool group 的 `reset_tools` 元工具 |

<Note>

`registerMcpClient` 是异步的。不调用 `block()`（也不订阅）就会导致 `build()` 执行时 MCP 工具尚未注册，Agent 会在没有这些工具的情况下静默启动。

</Note>

#### 注册之后的工具管理

Tool group 让你每次只暴露 toolkit 的一个子集，从而压缩模型看到的 Schema。注册并非单向操作——工具和分组都可以在 Agent 运行期间增删：

| 方法 | 效果 |
|------|------|
| `createToolGroup(name, description)` | 创建分组，默认激活 |
| `createToolGroup(name, description, active)` | 创建分组并显式指定初始激活状态 |
| `registerToolGroup(ToolGroup)` | 注册预先构建的 `ToolGroup` 实例或其子类 |
| `addToolToGroup(groupName, toolName)` | 把已注册的工具移入某个分组 |
| `setActiveGroups(List<String>)` | 替换当前激活的分组集合 |
| `removeToolGroups(List<String>)` | 移除分组及其中的全部工具 |
| `removeTool(String)` | 按名称移除一个工具 |
| `removeToolIfSame(String, AgentTool)` | 仅当注册的实例与预期一致时才移除——多个组件共用一个 toolkit 时的安全形式 |
| `removeMcpClient(String)` | 移除一个 MCP Server 及其全部工具；返回 `Mono<Void>` |

让 Agent 自行切换分组的方式参见[自我管理 Tool](#自我管理-tool)。

### 自定义 Tool（注解式）

最轻量的写法：在普通类的方法上标注 `@Tool` 与 `@ToolParam`，然后通过 `Toolkit#registerTool(Object)` 反射注册。框架自动从 Java 类型推导 JSON schema，从 `description` 取面向 agent 的说明。

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

`@Tool` 常用属性：

| 属性 | 类型 | 说明 |
|------|------|------|
| `name` | `String` | tool 名（默认取方法名） |
| `description` | `String` | 面向 agent 的描述 |
| `readOnly` | `boolean` | 是否只读（默认 `false`） |
| `concurrencySafe` | `boolean` | 是否可并发调用（默认 `false`） |
| `stateInjected` | `boolean` | 是否在调用时注入 `AgentState` 作为额外参数（默认 `false`） |
| `dangerousFiles` / `dangerousDirectories` | `String[]` | 追加自定义危险路径列表 |
| `converter` | `Class<? extends ToolResultConverter>` | 自定义返回值到 `ToolResultBlock` 的转换器 |

### 参数 Schema（`@ToolParam`）

只有标注了 `@ToolParam` 的参数才会进入 Tool 的 JSON Schema。方法签名上的其他参数要么由框架自动注入（`ToolEmitter`、`Agent`、`AgentState`、`RuntimeContext`），要么从 runtime context 解析而来，它们不会出现在发送给模型的 Schema 中——参见[接收 Context](#接收-context)。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | `String` | *必填* | Schema 中的属性名。之所以必填，是因为 Java 运行时不保留参数名；建议使用 snake_case 以兼容各家 LLM |
| `description` | `String` | `""` | 写入属性的 `description`。为空时不会出现在 Schema 中 |
| `required` | `boolean` | `true` | 该属性是否列入 Schema 的 `required` 数组 |

#### Java 类型到 JSON Schema 的映射

Schema 基于参数的**泛型**类型生成（`Parameter#getParameterizedType()`），因此集合上的类型参数会被保留，而不会被擦除：

| Java 参数类型 | 生成的属性 Schema |
|---------------|-------------------|
| `String` | `{"type": "string"}` |
| `int`、`Integer`、`long`、`Long` | `{"type": "integer"}` |
| `double`、`Double`、`float` | `{"type": "number"}` |
| `boolean`、`Boolean` | `{"type": "boolean"}` |
| `MyEnum` | `{"type": "string", "enum": ["A", "B"]}`——取枚举常量名 |
| `String[]`、`List<String>`、`Set<String>` | `{"type": "array", "items": {"type": "string"}}` |
| `List<Item>` | `{"type": "array", "items": {...}}`，其中 `items` 为 `Item` 的对象 Schema |
| `List<List<String>>` | 一个 `array`，其 `items` 本身又是 `string` 的 `array` |
| `Map<String, Integer>` | `{"type": "object"}`——参见下方注意事项 |
| `Item`（POJO） | 由 `Item` 的字段构建的 `{"type": "object", "properties": { … }}` |

一个接收两个 `List<String>` 参数（一个必填、一个可选）的 Tool：

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

会生成：

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

注意 `description` 落在数组属性本身，而不是 `items` 上。若要描述元素，请把描述写在元素类型的字段上（参见 [POJO 字段上的 `@ToolParam`](#pojo-字段上的-toolparam)）。

<Warning>

`Map<K, V>` 参数只会生成一个裸的 `{"type": "object"}`：键和值的类型**不会**被描述，模型得不到任何关于内容的提示，也不会有任何校验。当结构已知时，请改用 POJO 参数，或一个由小型 POJO 组成的 `List`，而不是 `Map`。

</Warning>

#### 嵌套类型与 `$defs`

嵌套的 POJO 会被内联到属性 Schema 中。被多次引用的类型，或递归类型，则会作为 `$defs` 条目输出，并由属性通过 `$ref` 指向。由于每个参数的 Schema 是独立生成的，AgentScope 会把这些定义从参数层级上提到 Tool Schema 的根部，使 `#/$defs/TypeName` 指针能够相对文档根正确解析：

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

定义的键是类型简单名，因此两个**不同**但简单名相同的类（例如来自两个不同包的 `Material`）出现在同一个 Tool 方法中时会发生冲突，Schema 生成将抛出 `IllegalStateException: Conflicting schema definition found for key: Material`。此时请重命名其中一个，或把两者收拢到同一个 POJO 参数中。

#### `required` 如何影响 Schema

`required` **并不**决定参数是否出现在 Schema 中。每个 `@ToolParam` 参数都始终列在 `properties` 下；`required` 只决定它是否进入 Schema 顶层的 `required` 数组：

- `required = true`（默认）——属性名会被加入 `required`。
- `required = false`——属性仍在 `properties` 中，但不在 `required` 里，因此模型可以省略它。
- 当没有任何参数是必填时，`required` 这个键会被**完全省略**，而不是输出一个空数组。

调用时，`ToolExecutor` 会先用该 Schema 校验模型给出的参数，再调用你的方法。缺少必填属性的调用会被拒绝，校验错误会回传给模型重试，方法根本不会被执行。可选属性上显式传入的 `null` 与省略等同处理。

<Warning>

被省略的可选参数会以 `null` 传入你的方法。请用**装箱**类型（`Integer`、`Double`、`Boolean`）而非基本类型声明可选参数：标注了 `required = false` 的基本类型参数在模型省略它时会在调用阶段失败，因为 `null` 无法传给 `int` 或 `double`。

</Warning>

#### POJO 字段上的 `@ToolParam`

`@ToolParam` 同样适用于 POJO 参数的字段，用于重命名属性、提供描述以及设置是否必填：

```java
public class Location {

    @ToolParam(name = "city_name", description = "The city name")
    private String city;

    @ToolParam(name = "zip_code", description = "The zip code", required = false)
    private String zip;

    private String country; // 无注解 → 可选，沿用字段名
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

有两点与方法参数不同，值得记住：

- 未标注 `@ToolParam` 的字段**仍会**进入 Schema，作为**可选**属性，并沿用其 Java 字段名。而未标注 `@ToolParam` 的方法参数则完全不会出现在 Schema 中。
- `name` 为空时回退到 Java 字段名，`description` 为空时不写入 Schema。

字段上同样支持 Jackson 的 `@JsonPropertyDescription` 和 `@JsonProperty(required = true)`，因此已有的 Jackson 注解模型无需重新标注即可直接用作 Tool 参数。

### 自定义 Tool（继承 `ToolBase`）

需要自定义权限策略、外部执行或更复杂的 schema 时，继承 `ToolBase`：

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

### 定义外部执行 Tool

外部执行 tool 把实际执行委派给 agent 运行时之外 —— 通常是人工操作员或外部系统。Agent 调用此类 tool 时会发出 `RequireExternalExecutionEvent` 并暂停。下一次调用回传匹配的 `ToolResultBlock` 后，agent 会发出带有相同 `replyId` 的 `ExternalExecutionResultEvent`，然后继续执行。

这种模式是 [human-in-the-loop](/v2/zh/docs/building-blocks/agent) 工作流的基础 —— 某些动作需要人工确认或人工执行。

创建外部执行 tool 只需把 `externalTool` 设为 `true`，不必实现 `callAsync`：

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

完整可运行示例：`agentscope-examples/documentation/.../tool/ToolBaseExample.java`、`tool/ToolExecutionContextExample.java`。

## 接收 Context

每次 `agent.call(msgs, runtimeContext)` 传入的 [`RuntimeContext`](/v2/zh/docs/building-blocks/agent#runtimecontext-per-call-上下文) 会自动透传到所在 reply 内每一次工具调用。Tool 可以用两种方式拿到它：注解式 tool 走自动注入，`ToolBase.callAsync` 走 `ToolCallParam`。

### 自动注入（`@Tool` 方法）

`@Tool` 方法签名里，**没有标注 `@ToolParam`** 的参数会被框架视为「需要从框架注入」，并按下表的优先级解析：

| 参数类型 | 注入来源 |
|---------|---------|
| `ToolEmitter` | 流式中间产物 emitter（无配置时为 no-op） |
| `Agent` | 当前 agent 实例 |
| `AgentState` | 当前 call 的 per-session 状态（通过 `RuntimeContext.getAgentState()` 获取） |
| `RuntimeContext` | 当前 per-call 上下文 |
| `ToolExecutionContext` | `runtimeContext.asToolExecutionContext()`（兼容层，已 deprecated） |
| 其它用户自定义 POJO 类型 | `runtimeContext.get(ParamType.class)` —— 即调用方在 `RuntimeContext.builder().put(ParamType.class, value)` 注册的对象 |

「用户自定义 POJO」的判定：参数没有 `@ToolParam`、不是基本类型、不是 `ContentBlock` / `Msg`、不在 `java.*` / `javax.*` 包下。其余参数（带 `@ToolParam` 或属于上述兜底类型）从 LLM 提供的 JSON 输入按名称取值。

```java
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

public record UserContext(String username, String locale) {}

public class PersonalizedTools {

    @Tool(name = "greet", description = "Greet the user with a custom greeting")
    public String greet(
            @ToolParam(name = "greeting", description = "Greeting word, e.g. 'Hello'")
                    String greeting,                  // ← 由模型提供
            UserContext userCtx) {                    // ← 由框架自动注入
        return greeting + ", " + (userCtx == null ? "unknown" : userCtx.username()) + "!";
    }
}
```

调用方按类型注册同款 POJO 后，每次 `call` 就会自动把对应实例分发到所有需要它的 tool：

```java
RuntimeContext ctx =
        RuntimeContext.builder()
                .put(UserContext.class, new UserContext("alice", "en"))
                .userId("alice")
                .build();

agent.call(List.of(new UserMessage("Greet me.")), ctx).block();
```

模型不需要把 `userCtx` 写进 JSON 参数——schema 里也不会出现它。完整示例：`agentscope-examples/documentation/.../tool/ToolExecutionContextExample.java`。

### `ToolBase.callAsync` 中访问

继承 `ToolBase` 的 tool 通过 `ToolCallParam` 取 context：

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
        // ... 用 tenantId / cfg 执行业务 ...
    }
}
```

`ToolCallParam` 同时暴露 `getAgent()`、`getInput()`、`getEmitter()`、`getToolUseBlock()` 以及（已 deprecated 的）`getContext()`。新代码使用 `getRuntimeContext()`。

### 协调 hook 与 tool

`RuntimeContext` 的 string 层（`put(String, Object)` / `get(String)`）是同一次 `call` 内 middleware 与 tool 之间的临时通信通道——middleware 在 `onActing`/`onReasoning` 等位置写入，tool 通过注入 `RuntimeContext` 参数读取；调用结束后该实例与 hook 一并解绑。

## MCP

AgentScope 集成 [Model Context Protocol (MCP)](https://modelcontextprotocol.io/)，让 agent 可以接入任意 MCP 兼容的工具提供方。框架自动处理协议协商、工具发现与结果转换。

支持三种连接方式：

- **STDIO** —— 本地进程 stdin/stdout 通信
- **SSE / Streamable HTTP** —— 远程 HTTP 长连接

MCP tool 在 toolkit 中以 `mcp__{server_name}__{tool_name}` 命名，避免冲突；标注了 `readOnlyHint` 的 tool 会被权限系统自动放行。

### 注册 MCP Tool

通过 `McpClientBuilder` 构建 `McpClientWrapper`，再注册到 `Toolkit`：


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


完整运行示例：`agentscope-examples/documentation/.../mcp/McpStdioExample.java`、`mcp/McpSseExample.java`、`mcp/McpStreamableHttpExample.java`。

## Skill

Skill 是基于 markdown 的指令集，无需写新工具代码即可拓展 agent 能力。每个 skill 是一个目录，包含一个带 frontmatter 元数据与详细指令的 `SKILL.md` 文件。

与 tool 不同，skill 不能被直接调用。Agent 通过自动注册的查看器工具 `load_skill_through_path` 读取 skill 指令，再用现有的 tool 按指令执行。

### 注册 Skill

通过 `ReActAgent.builder().skillRepository(...)` 直接挂载一个或多个 `AgentSkillRepository`。Builder 在 `build()` 时自动装配 `DynamicSkillMiddleware`，每次 `call()` 都会按 skill 来源刷新 skill prompt 与 tool group：

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

多次调用 `skillRepository(...)` 按调用顺序追加（低 → 高优先级），同名 skill 后者覆盖前者；如需替换整批，调用 `skillRepositories(List<AgentSkillRepository>)`。

参考实现：`agentscope-examples/documentation/.../skill/AgentSkillExample.java`、`skill/SkillWithToolGroupExample.java`。

### Skill 的工作方式

`Toolkit` 在含 skill 时，注册与查看分两阶段进行。

初始化阶段：

- Toolkit 扫描所有注册的 skill 来源，收集每个 skill 的名称、描述与目录。
- 自动把内置查看器工具 `load_skill_through_path`（实现位于 `io.agentscope.core.skill.SkillToolFactory`）注册到 `skill-build-in-tools` 这个 tool group。
- 组装一段 system prompt 片段，列出可用 skill（仅名称与描述），并指示 agent 通过 `load_skill_through_path` 读取完整内容。

运行时阶段，agent 用两个必填参数调用查看器：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `skillId` | `string`（枚举：已注册的 skill ID） | 要加载的 skill。 |
| `path` | `string` | 传 `"SKILL.md"` 取该 skill 的 markdown 指令；或传 skill 声明过的精确资源路径，例如 `"references/guide.md"`、`"scripts/run.py"`。不要传 `"."`、`"./"`、目录或绝对路径。 |

调用示例：

```json
{
  "name": "load_skill_through_path",
  "input": { "skillId": "pdf-extractor", "path": "SKILL.md" }
}
```

每次成功调用产生两件事：

1. 返回请求的内容（`SKILL.md` markdown，或指定的资源文件）。
2. **激活该 skill** —— Toolkit 中与之绑定的 tool group 被启用，本轮对话余下时段都可调用 skill 自带的工具。如果 `path` 不存在，查看器会返回错误并列出可用资源路径（`SKILL.md` 始终排在第一位），便于 agent 重试。


<Note>

Skill 不是 tool —— agent 不能直接调用 skill。它必须先用 `load_skill_through_path` 读取指令，再用其他 tool 按描述的步骤执行。

</Note>


### Skill 执行脚本：配置 Shell 工具

Skill 只提供指令，真正的执行依赖 agent 已有的 tool。如果 skill 指令涉及脚本执行（例如 `scripts/run.py`），agent 需要拥有 shell 执行能力：

- **`ReActAgent`** —— 注册 `ShellCommandTool` 到 toolkit：

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

- **`HarnessAgent`** —— harness 模块自带 workspace 感知的 shell 与文件工具（`execute`、`read_file`、`write_file` 等），无需额外注册。

### Skill + ToolGroup：按需披露工具

`SkillToolGroup` 把一组 tool 绑定到某个 skill name —— agent 加载该 skill 时 tool group 自动激活，未加载时 tool 不出现在模型 schema 中，减少上下文噪音。

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;

Toolkit toolkit = new Toolkit();

// 1. 创建与 skill 绑定的 tool group（初始不激活）
toolkit.createSkillToolGroup(
        "analysis-tools",                // group 名
        "Data analysis tools",           // 描述
        false,                           // 初始不激活
        "data-analysis");                // 绑定的 skill name

// 2. 把 tool 注册到该 group
toolkit.registration()
        .tool(new AnalysisTools())
        .group("analysis-tools")
        .apply();

// 3. 构建 agent，启用 meta tool 支持模型主动切换 group
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

当 agent 通过 `load_skill_through_path` 加载名为 `data-analysis` 的 skill 时，`analysis-tools` group 自动激活，其中的 tool 立即可用。配合 `enableMetaTool(true)`，模型还可以通过 `reset_tools` 主动管理 tool group 的激活状态。

参考实现：`agentscope-examples/documentation/.../skill/SkillWithToolGroupExample.java`。

## 自我管理 Tool

内置 **meta tool**（`reset_tools`）让 agent 在运行时自我管理哪些 tool group 处于激活状态，从而保持上下文聚焦 —— 只有与当前任务相关的 tool 暴露给模型。

### 定义 Tool Group

`ToolGroup` 是带名称的 tool / MCP / skill 集合。把 group 注册到 `Toolkit` 后再用 builder 启用 meta tool：

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

`ToolGroup` 接收名称、描述、作用域（`ToolGroupScope`）以及初始激活态。保留名 `"basic"` 由 `Toolkit#registerTool(Object)` 自动构成，且始终激活。

### 使用 Meta Tool

只要存在至少一个非 basic 的 tool group，并通过 `enableMetaTool(true)` 打开开关，`Toolkit` 就会自动注册 `reset_tools` 并把其 schema 暴露给 agent。每个非 basic group 在 schema 中表示为一个布尔字段，agent 调用 meta tool 时声明期望的最终状态。

运行时行为：

- `"basic"` 组中的 tool 始终暴露，meta tool 不会影响它们。
- 每次调用 `reset_tools` 都会**整体覆盖**激活集合 —— 任何未显式置为 `true` 的非 basic group 都会被停用，无论之前的状态。
- 对每个本次切换为激活的 group，其 description 与（若提供的）使用说明会被拼接进 meta tool 的返回值，告诉 agent 如何正确使用该组。
- 未激活 group 中的 tool 不会出现在 agent 的工具 schema 中，从而把上下文留给当前激活的工具集。


<Warning>

Meta tool 的输入表示所有 group 的**最终状态**而非增量。任何未显式置为 `true` 的 group 都会被停用，无论之前的状态如何。

</Warning>


## 延伸阅读


<CardGroup cols={2}>



<Card title="Agent" href="/v2/zh/docs/building-blocks/agent">


Agent 如何在 ReAct 循环中编排 tool 调用

</Card>


<Card title="Permission System" href="/v2/zh/docs/building-blocks/permission-system">


精细控制哪个 tool 可以执行、何时执行

</Card>


<Card title="Middleware" href="/v2/zh/docs/building-blocks/middleware">


用洋葱式 middleware 拦截并改写 tool 调用

</Card>


<Card title="Human-in-the-Loop" href="/v2/zh/docs/building-blocks/agent">


外部执行 tool 与人工审批工作流

</Card>



</CardGroup>
