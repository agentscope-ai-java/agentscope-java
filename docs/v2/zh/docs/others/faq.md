---
title: FAQ
description: AgentScope Java 2.0 常见问题
---

<Accordion title="AgentScope Java 2.0 与 1.0 兼容吗？">

AgentScope Java 2.0 版本尽量保持了对 1.x 版本的兼容，确保大部分用户的平滑升级；但同时 2.0 也带来了 API 层面的不兼容变更（重新设计的 agent 抽象，以及新增的事件系统、权限系统、middleware 体系等），详情可参考 [V1 迁移指南](/v2/zh/docs/change-log)。

    对所有新项目，我们建议直接采用 2.0，以获得新版能力；1.0 的文档仍会保留供存量用户参考。

</Accordion>



<Accordion title="AgentScope Java 2.0 有配套的前端吗？">

有。仓库中包含 `agentscope-admin` 模块，提供与 ReActAgent 协议对齐的开箱即用 Web 应用。开发者无需自行编写 UI 即可直接体验已部署的 agent，并可通过事件系统（`AgentEvent`）与权限系统的 HITL 流程无缝集成。

</Accordion>



<Accordion title="2.0 还会提供 RAG 和 long-term memory 吗？">

会。`io.agentscope.core.rag` 与 `io.agentscope.core.memory.LongTermMemory` 模块已在仓库中存在，但 knowledge base、document reader 等组件正在持续完善，具体进度请关注 [Release Notes](/v2/zh/docs/others/release-notes) 与 GitHub 发版。

</Accordion>



<Accordion title="如何切换模型 provider？">

把传给 `.model(...)` 的 `<provider>:<model-name>` 字符串换掉，加上对应的
`agentscope-extensions-model-*` 依赖，并设置该 provider 的 API key 环境变量。不需要改其他代码——
`ModelRegistry` 在运行时解析 provider：

```java
ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .model("openai:gpt-4.1")   // 原来是 "dashscope:qwen-plus"
                .build();
```

```bash
# 设置上面选的那一行对应的环境变量
export OPENAI_API_KEY=sk-your-key-here
```

| Provider | 模型字符串前缀 | 环境变量 |
|---|---|---|
| DashScope | `dashscope:qwen-plus` | `DASHSCOPE_API_KEY` |
| OpenAI | `openai:gpt-4.1` | `OPENAI_API_KEY` |
| Anthropic | `anthropic:claude-sonnet-4-7` | `ANTHROPIC_API_KEY` |
| Gemini | `gemini:gemini-2.0-flash` | `GEMINI_API_KEY` |
| Ollama（本地） | `ollama:llama3` | 无（可选 `OLLAMA_BASE_URL`） |

完整 provider 列表和显式 builder 配置见 [Model](/v2/zh/docs/building-blocks/model)。

</Accordion>



<Accordion title="为什么 .model(...) 抛出 'model not found'？">

这说明 `ModelRegistry` 没能解析 `<provider>:<model-name>` 字符串——几乎总是因为对应的
`agentscope-extensions-model-*` 模块还没加到 classpath。加上它：

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-dashscope</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

再确认 id 是否符合 `provider:model-name` 的约定（例如 `"openai:gpt-5.5"`、
`"dashscope:qwen-max"`、`"gemini:gemini-2.0-flash"`）——provider 前缀拼错了，即使依赖已加上也会
报同样的错。

</Accordion>



<Accordion title="怎么查看 agent 在做什么（追踪推理和工具调用）？">

挂上内置的 `AgentTraceMiddleware`——它在 `ReActAgent` 和 `HarnessAgent` 上都能用，会把每一步推理、
工具调用及其结果实时打印出来：

```java
import io.agentscope.harness.agent.middleware.AgentTraceMiddleware;

ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .model("dashscope:qwen-plus")
                .middleware(new AgentTraceMiddleware())
                .build();
```

如果想要自己的逻辑（替代或叠加追踪），实现 `MiddlewareBase` 并挂上 `onAgent` / `onReasoning` /
`onActing` / `onModelCall`——详见 [Middleware](/v2/zh/docs/building-blocks/middleware)。

</Accordion>



<Accordion title="除了 Java 还有其他语言版本吗？">

有。AgentScope 目前提供三种语言实现，各自独立仓库：

    - **Java** —— [`agentscope-ai/agentscope-java`](https://github.com/agentscope-ai/agentscope-java)（即本文档对应仓库）
    - **Python** —— [`agentscope-ai/agentscope`](https://github.com/agentscope-ai/agentscope)
    - **TypeScript** —— [`agentscope-ai/agentscope-typescript`](https://github.com/agentscope-ai/agentscope-typescript)

</Accordion>
