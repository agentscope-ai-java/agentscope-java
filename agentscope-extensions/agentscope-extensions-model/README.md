# agentscope-extensions-model

Parent (`pom`-packaging) module for AgentScope Java's chat-model provider extensions. Each provider ships as its own artifact so an application depends only on the model(s) it actually calls; every provider module implements `io.agentscope.core.model.ChatModelBase` (which in turn implements `io.agentscope.core.model.Model`) and registers a `io.agentscope.core.model.spi.ModelProvider` through Java SPI so it can also be resolved by a plain `"provider:model-name"` string id.

## Sub-modules

| Artifact | Provider | Env var(s) | Main package |
|---|---|---|---|
| `agentscope-extensions-model-openai` | OpenAI (plus OpenAI-compatible DeepSeek, GLM, Kimi, MiniMax) | `OPENAI_API_KEY`, `DEEPSEEK_API_KEY`, `GLM_API_KEY`, `MOONSHOT_API_KEY`/`KIMI_API_KEY`, `MINIMAX_API_KEY` | `io.agentscope.extensions.model.openai` |
| `agentscope-extensions-model-dashscope` | Alibaba Cloud DashScope (Qwen) | `DASHSCOPE_API_KEY` | `io.agentscope.extensions.model.dashscope` |
| `agentscope-extensions-model-anthropic` | Anthropic (Claude) | `ANTHROPIC_API_KEY` | `io.agentscope.extensions.model.anthropic` |
| `agentscope-extensions-model-gemini` | Google Gemini | `GEMINI_API_KEY` | `io.agentscope.extensions.model.gemini` |
| `agentscope-extensions-model-ollama` | Ollama (local models) | `OLLAMA_BASE_URL` (optional, defaults to the local Ollama endpoint) | `io.agentscope.extensions.model.ollama` |

`agentscope-extensions-model-openai` also bundles `ModelProvider`s for a few OpenAI-compatible APIs under `io.agentscope.extensions.model.openai.compat.*`: DeepSeek (`deepseek:...`), GLM (`glm:...`), Kimi/Moonshot (`kimi:...`), and MiniMax (`minimax:...`). No separate dependency is needed for those.

A `agentscope-extensions-model-openai-official` module is declared in this parent's history but is currently commented out of the build (`<module>` entry disabled, no source directory present yet) — it is not usable today.

Only add the provider artifact(s) you need; each one pulls in that vendor's SDK/HTTP client independently.

## Installation

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-dashscope</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

Swap the `artifactId` for the provider you need, e.g. `agentscope-extensions-model-openai`, `agentscope-extensions-model-anthropic`, `agentscope-extensions-model-gemini`, or `agentscope-extensions-model-ollama`.

## Quick Start

### String model id (simplest)

With the matching module on the classpath and its API key set as an environment variable, pass a `"provider:model-name"` string straight to an agent — it is resolved internally by `ModelRegistry`:

```java
ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .model("dashscope:qwen-plus") // reads DASHSCOPE_API_KEY
                .build();
```

### Explicit builder

Build a provider's `ChatModelBase` directly when you need a custom API key, base URL, formatter, transport, or generation options:

```java
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.core.agent.ReActAgent;

DashScopeChatModel model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .stream(true)
                .formatter(new DashScopeChatFormatter())
                .build();

ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .model(model)
                .build();
```

Every provider module follows this same `<Provider>ChatModel.builder().apiKey(...).modelName(...).stream(...).build()` shape (e.g. `OpenAIChatModel.builder()`, `AnthropicChatModel.builder()`, `GeminiChatModel.builder()`, `OllamaChatModel.builder()`), plus provider-specific options such as `baseUrl(String)`, `proxy(ProxyConfig)`, or `contextWindowSize(int)`.

## Learn more

- [docs/v2/en/integration/model/index.md](../../docs/v2/en/integration/model/index.md) — one page per provider (setup, config, examples).
- [docs/v2/en/docs/building-blocks/model.md](../../docs/v2/en/docs/building-blocks/model.md) — the full model layer: credentials, `ModelRegistry`, Spring Boot starters, and the migration checklist from core-bundled models.
