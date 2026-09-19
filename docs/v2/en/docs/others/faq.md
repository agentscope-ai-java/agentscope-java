---
title: FAQ
description: Frequently asked questions about AgentScope Java 2.0
---

<Accordion title="Is AgentScope Java 2.0 compatible with 1.0?">

AgentScope Java 2.0 aims to preserve compatibility with 1.x where possible so that most users can upgrade smoothly. That said, 2.0 does introduce API-level breaking changes — including a redesigned agent abstraction and the new event system, permission system, and middleware stack. See the [V1 Migration Guide](/v2/en/docs/change-log) for details.

    For new projects we recommend adopting 2.0 directly to benefit from the new capabilities; the 1.0 docs remain available for existing users.

</Accordion>



<Accordion title="Is there a frontend that ships with AgentScope Java 2.0?">

Yes. The repository includes the `agentscope-admin` module, an out-of-the-box web app that speaks the same protocol as `ReActAgent`. It works without any custom UI code and integrates cleanly with the event system (`AgentEvent`) and the HITL flow of the permission system.

</Accordion>



<Accordion title="Will 2.0 ship RAG and long-term memory?">

Yes. `io.agentscope.core.rag` and `io.agentscope.core.memory.LongTermMemory` already exist in the repo, but knowledge bases, document readers and similar components are still being completed — track progress in the [Release Notes](/v2/en/docs/others/release-notes) and on GitHub releases.

</Accordion>



<Accordion title="How do I switch model providers?">

Change the `<provider>:<model-name>` string passed to `.model(...)`, add the matching
`agentscope-extensions-model-*` dependency, and set that provider's API-key environment variable.
No other code changes are needed — `ModelRegistry` resolves the provider at runtime:

```java
ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .model("openai:gpt-4.1")   // was "dashscope:qwen-plus"
                .build();
```

```bash
# Set the env var matching the row you picked above
export OPENAI_API_KEY=sk-your-key-here
```

| Provider | Model string prefix | Env var |
|---|---|---|
| DashScope | `dashscope:qwen-plus` | `DASHSCOPE_API_KEY` |
| OpenAI | `openai:gpt-4.1` | `OPENAI_API_KEY` |
| Anthropic | `anthropic:claude-sonnet-4-7` | `ANTHROPIC_API_KEY` |
| Gemini | `gemini:gemini-2.0-flash` | `GEMINI_API_KEY` |
| Ollama (local) | `ollama:llama3` | none (optional `OLLAMA_BASE_URL`) |

See [Model](/v2/en/docs/building-blocks/model) for the full provider list and explicit-builder configuration.

</Accordion>



<Accordion title="Why does .model(...) throw 'model not found'?">

This means `ModelRegistry` couldn't resolve the `<provider>:<model-name>` string — almost always
because the matching `agentscope-extensions-model-*` module isn't on the classpath yet. Add it:

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-dashscope</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

Double-check the id follows the `provider:model-name` convention (e.g. `"openai:gpt-5.5"`,
`"dashscope:qwen-max"`, `"gemini:gemini-2.0-flash"`) — a typo in the provider prefix produces the
same error even with the dependency present.

</Accordion>



<Accordion title="How do I see what the agent is doing (trace reasoning and tool calls)?">

Attach the built-in `AgentTraceMiddleware` — it works on both `ReActAgent` and `HarnessAgent` and
logs each reasoning step, tool call, and result as it happens:

```java
import io.agentscope.harness.agent.middleware.AgentTraceMiddleware;

ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .model("dashscope:qwen-plus")
                .middleware(new AgentTraceMiddleware())
                .build();
```

For your own logic instead of (or alongside) tracing, implement `MiddlewareBase` and hook
`onAgent` / `onReasoning` / `onActing` / `onModelCall` — see [Middleware](/v2/en/docs/building-blocks/middleware).

</Accordion>



<Accordion title="Is there a non-Java edition?">

Yes. AgentScope ships in three independent language editions, each in its own repository:

    - **Java** — [`agentscope-ai/agentscope-java`](https://github.com/agentscope-ai/agentscope-java) (this docs site)
    - **Python** — [`agentscope-ai/agentscope`](https://github.com/agentscope-ai/agentscope)
    - **TypeScript** — [`agentscope-ai/agentscope-typescript`](https://github.com/agentscope-ai/agentscope-typescript)

</Accordion>
