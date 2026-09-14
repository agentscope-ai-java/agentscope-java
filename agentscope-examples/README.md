# AgentScope Java Examples

This directory contains examples demonstrating core features of AgentScope Java framework.

## 🚀 Quick Start

### Prerequisites

- **JDK 17** or higher
- **Maven 3.6+**
- **DashScope API Key** - Get one at https://dashscope.console.aliyun.com/apiKey

### Build Examples

```bash
# From the repository root, build and install every module (core, harness, extensions...)
mvn clean install -DskipTests

# Then cd into this example module — every command below assumes you're here
cd agentscope-examples/documentation
mvn compile
```

### Environment Setup

Set your API key (optional - examples will prompt for it if not set):

```bash
export DASHSCOPE_API_KEY=your_api_key_here
```

> **Note:** Every `mvn exec:java ...` command below must run from `agentscope-examples/documentation/`
> (or add `-pl agentscope-examples/documentation` when running from the repository root) —
> `exec:java` needs that module's compiled classpath.

## 📚 Examples Overview

| Example | Description | Core Concepts | Run Command |
|---------|-------------|---------------|-------------|
| **BasicChatExample** | Simplest agent conversation | Agent, Model, Memory | `mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.quickstart.BasicChatExample"` |
| **ToolCallingExample** | Equipping agents with tools | @Tool, Toolkit, Tool calling | `mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.tool.ToolCallingExample"` |
| **StructuredOutputExample** | Generate typed structured output | Structured output, Schema validation | `mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.structuredoutput.StructuredOutputExample"` |
| **ToolGroupExample** | Autonomous tool group management | Meta-tool, Tool groups, Self-activation | `mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.tool.ToolGroupExample"` |
| **PermissionHITLExample** | Gating tool calls with allow/ask/deny rules | PermissionMode, PermissionContextState, HITL | `mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.hitl.PermissionHITLExample"` |
| **McpStdioExample** | Local MCP server integration | MCP, StdIO transport | `mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.mcp.McpStdioExample"` |
| **McpSseExample** | Remote MCP server integration | MCP, SSE transport | `mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.mcp.McpSseExample"` |
| **McpStreamableHttpExample** | Remote MCP server integration | MCP, Streamable HTTP | `mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.mcp.McpStreamableHttpExample"` |
| **CustomizedMiddlewareExample** | Monitoring agent execution | Middleware, Lifecycle interception | `mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.middleware.CustomizedMiddlewareExample"` |
| **StreamingWebExample** | Spring Boot + SSE streaming | Web API, Real-time streaming | `mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.streaming.StreamingWebExample"` |
| **StateExample** | Persistent conversations | AgentStateStore, State management | `mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.state.StateExample"` |
| **InterruptionExample** | Agent interruption mechanism | User interruption, Recovery | `mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.hitl.InterruptionExample"` |

## 📖 Detailed Examples

### 1. BasicChatExample

The simplest way to create and chat with an agent — a bare `ReActAgent` with no workspace,
memory backend, or tools required:

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;

ReActAgent agent = ReActAgent.builder()
        .name("Assistant")
        .sysPrompt("You are a helpful AI assistant. Be friendly and concise.")
        // ModelRegistry resolves "dashscope:qwen-plus" and reads DASHSCOPE_API_KEY from the env
        .model("dashscope:qwen-plus")
        .toolkit(new Toolkit())
        .build();

// Stream incremental text deltas as they arrive
agent.streamEvents(new UserMessage("Hello, introduce yourself"))
        .doOnNext(event -> {
            if (event instanceof TextBlockDeltaEvent delta) {
                System.out.print(delta.getDelta());
            }
        })
        .blockLast();
```

Run the full interactive version (a REPL loop around the same agent):

```bash
mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.quickstart.BasicChatExample"
```

**What you'll learn:**
- Creating a ReActAgent
- Configuring Model, Memory, and Formatter
- Interactive conversation

**Try asking:**
- "Hello, introduce yourself"
- "What can you help me with?"

---

### 2. ToolCallingExample

Learn how to give agents access to tools. Annotate a plain method with `@Tool` /
`@ToolParam`, register the containing object on a `Toolkit`, and attach the toolkit to the agent —
the model decides when to call it:

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;

public class SimpleTools {
    @Tool(name = "calculate", description = "Calculate simple math expressions")
    public String calculate(
            @ToolParam(name = "expression", description = "Math expression, e.g., '123 + 456'")
            String expression) {
        // ... evaluate and return the result as a string
        return expression + " = 579";
    }
}

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new SimpleTools());

ReActAgent agent = ReActAgent.builder()
        .name("ToolAgent")
        .sysPrompt("You are a helpful assistant with access to tools. Use tools when needed.")
        .model("dashscope:qwen-max")
        .toolkit(toolkit)
        .build();

agent.call(new UserMessage("Calculate 123 * 456")).block();
```

For tools that need permission checks, async execution, or `RuntimeContext` injection, extend
`ToolBase` and register with `toolkit.registerAgentTool(...)` instead — see [PermissionHITLExample](#5-permissionhitlexample) below.

```bash
mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.tool.ToolCallingExample"
```

**What you'll learn:**
- Defining tools with `@Tool` annotation
- Registering tools to Toolkit
- Agent automatically calling tools

**Try asking:**
- "What time is it in Tokyo?"
- "Calculate 123 * 456"
- "Search for 'artificial intelligence'"

---

### 3. StructuredOutputExample

Generate structured, typed output from natural language queries. Pass a plain Java class as the
schema to `agent.call(msg, SchemaClass.class)`, then read it back with `getStructuredData(...)`
— no manual JSON parsing:

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import java.util.List;

public class ProductRequirements {
    public String productType;
    public String brand;
    public Integer minRam;
    public Double maxBudget;
    public List<String> features;
    public ProductRequirements() {}
}

Msg userMsg = new UserMessage(
        "Extract the product requirements from this query: I need a laptop with "
                + "at least 16GB RAM, Apple brand, budget around $2000.");

Msg reply = agent.call(userMsg, ProductRequirements.class).block();
ProductRequirements result = reply.getStructuredData(ProductRequirements.class);

System.out.println("Brand: " + result.brand + ", Min RAM: " + result.minRam + " GB");
```

```bash
mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.structuredoutput.StructuredOutputExample"
```

**What you'll learn:**
- Defining structured output schema using Java classes
- Requesting structured responses from agents
- Extracting and validating typed data

**How it works:**
This example demonstrates three use cases:

1. **Product Requirements Extraction**
   - Input: Natural language product description
   - Output: Structured `ProductRequirements` object with type, brand, specs, budget, features

2. **Contact Information Extraction**
   - Input: Text containing contact details
   - Output: Structured `ContactInfo` object with name, email, phone, company

3. **Sentiment Analysis**
   - Input: Customer review text
   - Output: Structured `SentimentAnalysis` object with sentiment, scores, topics, summary

**Example output:**
```text
=== Example 1: Product Information ===
Query: I'm looking for a laptop. I need at least 16GB RAM, prefer Apple brand...

Extracted structured data:
  Product Type: laptop
  Brand: Apple
  Min RAM: 16 GB
  Max Budget: $2000.0
  Features: [lightweight, travel-friendly]
```

**Key features:**
- ✅ Type-safe data extraction
- ✅ Automatic schema generation from Java classes
- ✅ Works with any model supporting tool calling
- ✅ No need for manual JSON parsing

---

### 4. ToolGroupExample

Agent autonomously managing tool groups using meta-tool.

```bash
mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.tool.ToolGroupExample"
```

**What you'll learn:**
- Creating tool groups to organize tools
- Agent autonomously activating tool groups using `reset_equipped_tools` meta-tool
- Agent deciding which tools to activate based on task requirements

**How it works:**
- All tool groups start as **INACTIVE**
- The agent has access to the `reset_equipped_tools` meta-tool
- When you give the agent a task, it will:
  1. Determine which tool groups are needed
  2. Call `reset_equipped_tools` to activate those groups
  3. Use the tools from the activated groups

**Example prompts to try:**

1. **Single tool group activation:**
   ```text
   You> Calculate the factorial of 5
   ```
   Watch: Agent activates `math_ops`, then uses `factorial` tool

2. **Different tool group:**
   ```text
   You> Ping google.com
   ```
   Watch: Agent activates `network_ops`, then uses `ping` tool

3. **Another tool group:**
   ```text
   You> List files in /tmp
   ```
   Watch: Agent activates `file_ops`, then uses `list_files` tool

4. **Multiple tool groups in one task:**
   ```text
   You> Calculate factorial of 7 and then ping github.com
   ```
   Watch: Agent activates both `math_ops` and `network_ops`

5. **Complex multi-group task:**
   ```text
   You> Check if 17 is prime, then list files in /tmp
   ```
   Watch: Agent activates `math_ops` and `file_ops`

This example demonstrates **autonomous tool management** - the agent intelligently decides which tools to enable based on your request!

---

### 5. PermissionHITLExample

Gate tool calls behind allow / ask / deny rules, and pause for human approval on sensitive
actions. Build a `PermissionContextState` with named rules and attach it to the agent:

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;

PermissionContextState permissionContext = PermissionContextState.builder()
        .mode(PermissionMode.DEFAULT)
        .addAllowRule("safe_read",
                new PermissionRule("safe_read", null, PermissionBehavior.ALLOW, "policy"))
        .addAskRule("dangerous_delete",
                new PermissionRule("dangerous_delete", null, PermissionBehavior.ASK, "policy"))
        .build();

ReActAgent agent = ReActAgent.builder()
        .name("GuardedAgent")
        .sysPrompt("You are a file assistant. You have safe_read and dangerous_delete tools.")
        .model("dashscope:qwen-max")
        .toolkit(toolkit)
        .permissionContext(permissionContext)
        .build();

// If the model calls a tool matched by an ASK rule, the agent returns early with
// Msg.getGenerateReason() == GenerateReason.PERMISSION_ASKING — inspect the reply and
// resume (or cancel) the call once the human has decided.
agent.call(new UserMessage("Delete the temp file")).block();
```

`PermissionMode` also has `ACCEPT_EDITS`, `EXPLORE`, `BYPASS`, and `DONT_ASK` for non-interactive
runs (e.g. CI, headless batch jobs).

```bash
mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.hitl.PermissionHITLExample"
```

**What you'll learn:**
- Declaring `ALLOW` / `ASK` / `DENY` rules per tool name with `PermissionContextState`
- Switching between interactive (`DEFAULT`) and headless (`DONT_ASK`) permission modes
- Detecting and resuming a tool call paused for human approval

---

### 6. MCP Examples

Connect to external tool servers using Model Context Protocol (MCP).

```bash
# Local subprocess over stdin/stdout
mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.mcp.McpStdioExample"

# Remote server over Server-Sent Events
mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.mcp.McpSseExample"

# Remote server over Streamable HTTP
mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.mcp.McpStreamableHttpExample"
```

**Prerequisites:**
Install an MCP server:
```bash
npm install -g @modelcontextprotocol/server-filesystem
```

For remote transports, set `MCP_SSE_URL` or `MCP_HTTP_URL` before running the corresponding
example. Optional credentials can be provided with `MCP_SSE_TOKEN` or `MCP_HTTP_API_KEY`.

**What you'll learn:**
- Connecting to MCP servers (StdIO, SSE, HTTP)
- Using external tools from MCP servers
- Selecting the appropriate MCP transport for local or remote servers

**Try asking:**
- "List files in /tmp"
- "Read the content of /tmp/test.txt"

---

### 7. CustomizedMiddlewareExample (formerly HookExample)

Monitor and intercept agent execution in real-time.

```bash
mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.middleware.CustomizedMiddlewareExample"
```

**What you'll learn:**
- Intercepting agent, reasoning, and acting stages with `MiddlewareBase`
- Streaming output monitoring
- Tool execution tracking
- ToolEmitter for progress updates

**Try asking:**
- "Process the customer dataset"

You'll see detailed logs of:
- Agent start
- Reasoning chunks (streaming)
- Tool calls and results
- Progress updates
- Completion

---

### 8. StreamingWebExample

Spring Boot web application with Server-Sent Events (SSE) streaming.

```bash
mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.streaming.StreamingWebExample"
```

**What you'll learn:**
- Building a Spring Boot REST API with reactive endpoints
- Real-time streaming with Server-Sent Events (SSE)
- Hook-based response collection for streaming
- Session persistence in web environment

**How to use:**
After starting the server, open your browser or use curl:

```bash
# Simple query
curl -N "http://localhost:8080/chat?message=Hello"

# With session persistence
curl -N "http://localhost:8080/chat?message=What%20is%20AI?&sessionId=my-session"

# Or open in browser
http://localhost:8080/chat?message=Hello
```

You'll see the agent's response streaming in real-time, character by character.

---

### 9. StateExample

Maintain persistent conversation history across runs.

```bash
mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.state.StateExample"
```

**What you'll learn:**
- Using `JsonFileAgentStateStore` for persistence
- Automatically saving and loading `AgentState`
- Resuming a conversation with the same session ID

**Try this flow:**
```text
# First run
Enter session ID: alice_session
You> My name is Alice and I love pizza

# Second run (same session ID)
Enter session ID: alice_session
You> What's my name and what do I like?
Agent> Your name is Alice and you love pizza!
```

---

### 10. InterruptionExample

Gracefully interrupt long-running agent tasks.

```bash
mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.hitl.InterruptionExample"
```

**What you'll learn:**
- User-initiated interruption
- Cooperative interruption mechanism
- Fake tool results generation
- Graceful recovery

The example automatically demonstrates interruption by starting a long task and interrupting it after 2 seconds.

---

## 🛠️ Common Operations

### Running a Specific Example

```bash
mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.quickstart.BasicChatExample"
```

### Debugging Examples

Add debug logging:
```bash
mvn exec:java -Dexec.mainClass="io.agentscope.examples.documentation2.quickstart.BasicChatExample" \
  -Dorg.slf4j.simpleLogger.defaultLogLevel=debug
```

### Code Formatting

```bash
mvn spotless:apply
```

## 📝 API Key Configuration

Examples support two ways to provide API keys:

1. **Environment Variable** (recommended):
   ```bash
   export DASHSCOPE_API_KEY=your_key_here
   mvn exec:java -Dexec.mainClass="..."
   ```

2. **Interactive Input**:
   If environment variable is not set, examples will prompt you to enter the API key.

## 🤔 Troubleshooting

### "DASHSCOPE_API_KEY not found"

Set the environment variable:
```bash
export DASHSCOPE_API_KEY=sk-xxx
```

Or the example will prompt you to enter it interactively.

### MCP Server Connection Failed

For `McpStdioExample`, ensure the MCP server is installed:
```bash
# For filesystem server
npm install -g @modelcontextprotocol/server-filesystem

# For git server
npm install -g @modelcontextprotocol/server-git
```

### Compilation Errors

Make sure you've built the main library first, from the repository root:
```bash
cd /path/to/agentscope-java
mvn clean install -DskipTests
```

## 📚 Additional Resources

- [AgentScope Java Documentation](https://java.agentscope.io/) — full docs site (quickstart, building blocks, harness, integrations)
- [Main README](../README.md) — project overview, installation, and architecture
- [CONTRIBUTING.md](../CONTRIBUTING.md) - Development guidelines

## 💡 Contributing

When adding new examples:

1. Keep each example focused on a single feature
2. Add clear documentation and comments
3. Include interactive prompts for configuration
4. Follow the existing code style
5. Run `mvn spotless:apply` before committing

## 📄 License

Apache License 2.0 - See [LICENSE](../LICENSE) for details.
