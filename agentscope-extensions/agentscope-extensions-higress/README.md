# agentscope-extensions-higress

Higress AI Gateway integration for AgentScope Java. Instead of connecting an agent
directly to each MCP server, this module routes MCP tool discovery and invocation
through a [Higress](https://higress.io) gateway's unified `union-tools-search`
endpoint. Add it when you front your MCP servers with Higress and want the agent to
either see the gateway's full tool catalog, or let Higress semantically pick the
handful of tools relevant to a given request via its `x_higress_tool_search` tool.

## Installation

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-higress</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## Quick Start

`HigressMcpClientBuilder` builds a `HigressMcpClientWrapper` that talks to the
gateway (SSE or StreamableHTTP transport), and `HigressToolkit` registers it like
any other MCP client so the agent's tool schemas are populated from Higress:

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.extensions.higress.HigressMcpClientBuilder;
import io.agentscope.extensions.higress.HigressMcpClientWrapper;
import io.agentscope.extensions.higress.HigressToolkit;

// Connect to the Higress gateway's union-tools-search MCP endpoint.
HigressMcpClientWrapper client = HigressMcpClientBuilder
        .create("higress-mcp")
        .streamableHttpEndpoint("http://higress-gateway/mcp-servers/union-tools-search")
        .header("Authorization", "Bearer " + System.getenv("HIGRESS_TOKEN"))
        .buildSync();

// Register it with a HigressToolkit; all tools exposed by the gateway become
// available to the agent.
HigressToolkit toolkit = new HigressToolkit();
toolkit.registerMcpClient(client).block();

ReActAgent agent = ReActAgent.builder()
        .name("Assistant")
        .sysPrompt("You are a helpful assistant.")
        .model(model) // any io.agentscope.core.model.Model
        .toolkit(toolkit)
        .build();

Msg response = agent.call(Msg.builder()
        .name("user")
        .role(MsgRole.USER)
        .content(TextBlock.builder().text("What's the weather in Beijing?").build())
        .build()).block();
```

`HigressMcpClientBuilder` also supports `sseEndpoint(...)` for a stateful SSE
transport, `queryParam(...)`/`queryParams(...)` for URL parameters, and
`timeout(...)`/`initializationTimeout(...)` to override the 120s request / 30s
init defaults. `buildAsync()` returns a `Mono<HigressMcpClientWrapper>` for
non-blocking construction; `buildSync()` blocks and returns the initialized
client directly.

### Semantic tool search

Higress can pick the most relevant tools for a query instead of exposing its
whole catalog. Enable this on the builder with `toolSearch(...)`; the client
then calls the gateway's `x_higress_tool_search` tool internally whenever
`listTools()` runs, and the toolkit only ends up with the tools Higress
recommends:

```java
import io.agentscope.extensions.higress.HigressMcpClientBuilder;
import io.agentscope.extensions.higress.HigressMcpClientWrapper;
import io.agentscope.extensions.higress.HigressToolSearchResult;

HigressMcpClientWrapper client = HigressMcpClientBuilder
        .create("higress-mcp")
        .streamableHttpEndpoint("http://higress-gateway/mcp-servers/union-tools-search")
        .toolSearch("查询北京天气", 5) // query + topK; topK defaults to 10
        .buildSync();

client.listTools().block(); // internally calls x_higress_tool_search and caches the matches

// Or call the search directly and inspect the recommended tools yourself.
HigressToolSearchResult result = client.searchTools("查询北京天气", 5).block();
if (result.isSuccess()) {
    result.getToolNames().forEach(System.out::println);
}
```

## Architecture

- **`HigressMcpClientBuilder`** — fluent builder mirroring `McpClientBuilder`
  from `agentscope-core`. Configures the transport (`sseEndpoint` /
  `streamableHttpEndpoint`), headers, query params, timeouts, and optional tool
  search, then delegates to a `McpClientBuilder` to build the underlying MCP
  connection before wrapping and initializing it.
- **`HigressMcpClientWrapper`** — extends `io.agentscope.core.tool.mcp.McpClientWrapper`
  and delegates `initialize()`/`callTool()`/`close()` to that underlying MCP
  client. Its only real divergence is `listTools()`: when tool search is
  enabled it calls the gateway's `x_higress_tool_search` tool (exposed as the
  constant `TOOL_SEARCH_NAME`) instead of listing all tools, and converts the
  returned `HigressToolSearchResult.ToolInfo` entries into `McpSchema.Tool`.
  It also exposes `searchTools(query)` / `searchTools(query, topK)` for calling
  the search tool directly, and `isToolSearchEnabled()`.
- **`HigressToolkit`** — extends `io.agentscope.core.tool.Toolkit`. Its
  `registerMcpClient(...)` override is a normal toolkit registration that also
  caches the client, when it's a `HigressMcpClientWrapper`, so it can be
  retrieved later via `getHigressMcpClient()` (e.g. to call `searchTools(...)`
  after registration). For per-registration filtering or tool groups, use the
  toolkit's own fluent API: `toolkit.registration().mcpClient(client).enableTools(List.of("tool1")).group("myGroup").apply()`.
- **`HigressToolSearchResult`** — parses the response of an
  `x_higress_tool_search` call, trying `structuredContent` first and falling
  back to the tool's JSON text content. Exposes `isSuccess()`,
  `getErrorMessage()`, `getTools()` (list of `ToolInfo` records: `name`,
  `description`, `title`, `inputSchema`, `outputSchema`), and `getToolNames()`.
