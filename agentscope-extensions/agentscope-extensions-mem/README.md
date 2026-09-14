# agentscope-extensions-mem

Parent aggregator for AgentScope Java's external long-term-memory backend integrations. Each
sub-module implements `io.agentscope.core.memory.LongTermMemory` against a different memory
service so an Agent can record and retrieve cross-session facts (user preferences, prior
decisions) instead of relying on in-context memory alone. This module itself has no source —
add the dependency for the specific backend you want.

## Sub-modules

| Artifact | Backend | Description |
| --- | --- | --- |
| [`agentscope-extensions-mem0`](agentscope-extensions-mem0) | [Mem0](https://mem0.ai/) | Vector search + LLM-based memory extraction; SaaS platform or self-hosted. |
| [`agentscope-extensions-memory-bailian`](agentscope-extensions-memory-bailian) | Alibaba Cloud Bailian | Long-term memory backed by the Bailian memory service. |
| [`agentscope-extensions-reme`](agentscope-extensions-reme) | [ReMe](https://github.com/modelscope/ReMe) | Long-term memory plus trajectory-based experience recording. |

## Installation

Depend on the module for the backend you use, not on `agentscope-extensions-mem` directly:

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-mem0</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## Quick Start

> **Note:** In `agentscope-core`, the `LongTermMemory` interface, `LongTermMemoryMode`, and the
> `ReActAgent.Builder#longTermMemory` / `#longTermMemoryMode` methods are annotated
> `@Deprecated(forRemoval = true, since = "2.0.0")` as 1.x legacy-compatibility hooks. They are
> functional today (this is exactly what these sub-modules integrate against), but check the
> current `agentscope-core` Javadoc before depending on them in new code, since they may be
> removed in a future release.

Every backend follows the same shape: build a `LongTermMemory` implementation, then wire it
into a `ReActAgent`. The example below uses `agentscope-extensions-mem0` against a self-hosted
Mem0 server:

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.memory.mem0.Mem0ApiType;
import io.agentscope.core.memory.mem0.Mem0LongTermMemory;
import io.agentscope.core.message.UserMessage;

// 1. Build a memory instance (self-hosted Mem0, no metadata filtering)
Mem0LongTermMemory memory = Mem0LongTermMemory.builder()
    .agentName("Assistant")
    .userId("user_123")
    .apiBaseUrl("http://localhost:8000")
    .apiType(Mem0ApiType.SELF_HOSTED)
    .build();

// 2. Wire it into an Agent
ReActAgent agent = ReActAgent.builder()
    .name("Assistant")
    .model("dashscope:qwen-plus")
    .longTermMemory(memory)
    .longTermMemoryMode(LongTermMemoryMode.BOTH)
    .build();

// 3. Talk normally — memory is recorded/retrieved automatically
agent.call(new UserMessage("I prefer homestays when traveling")).block();
```

`agentscope-extensions-memory-bailian` and `agentscope-extensions-reme` follow the same
builder-then-wire pattern with their own `*LongTermMemory` classes (`BailianLongTermMemory`,
`ReMeLongTermMemory`) — see the "Learn more" links below for each backend's constructor/builder
options and required credentials.

## Learn more

- [Memory integrations overview](../../docs/v2/en/integration/memory/index.md)
- [Mem0](../../docs/v2/en/integration/memory/mem0.md)
- [Bailian](../../docs/v2/en/integration/memory/bailian.md)
- [ReMe](../../docs/v2/en/integration/memory/reme.md)
