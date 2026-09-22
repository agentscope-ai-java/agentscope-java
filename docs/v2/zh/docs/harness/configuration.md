---
title: 配置
description: 完整的 HarnessAgent.builder() 参考，按关注点分组——身份、模型、工具、工作区、文件系统、状态、子智能体、Skill、Plan Mode，以及关闭各内置能力的所有开关
---

`HarnessAgent.builder()` 是开启、替换或关闭每一项 Harness 能力的唯一入口。本页是完整的 builder 参考，按每个选项解决的问题分组。若只需要不带工作区、记忆和子智能体的纯 ReAct 循环，请改看 [`ReActAgent` builder 字段](/v2/zh/docs/building-blocks/agent#builder-fields)——两个 builder 的字段名大多相同，互相迁移基本是机械工作。

## 能跑起来的最小配置

只有 `name`、`sysPrompt` 和 `model` 是必填的，其余都有可用的默认值：

```java
import io.agentscope.harness.agent.HarnessAgent;

HarnessAgent agent =
        HarnessAgent.builder()
                .name("assistant")
                .sysPrompt("You are a helpful assistant.")
                .model("dashscope:qwen-plus")   // 自动读取环境变量 DASHSCOPE_API_KEY
                .build();

String reply = agent.call("What can you do?").block().getTextContent();
System.out.println(reply);
```

不调用 `.workspace(...)` 时，Agent 会把工作区解析到 `${user.dir}/.agentscope/workspace`，状态持久化到 `~/.agentscope/state/<agentId>/`，并注册默认的文件系统、Shell、记忆和 todo 工具。

## 一份生产环境配置

下面这份配置固定了长期运行部署通常关心的每一项选择：

```java
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.compaction.CompactionConfig;
import java.nio.file.Paths;
import java.time.Duration;

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new MyDomainTools());

HarnessAgent agent =
        HarnessAgent.builder()
                // --- 身份 ---
                .name("support-agent")
                .agentId("support-agent-v3")         // 存储状态的稳定命名空间键
                .sysPrompt("You are a support engineer.")

                // --- 模型 ---
                .model("dashscope:qwen-max")
                .fallbackModel("openai:gpt-5.5")     // 主模型持续失败时启用
                .maxRetries(3)
                .modelExecutionConfig(
                        ExecutionConfig.builder()
                                .timeout(Duration.ofSeconds(90))
                                .maxAttempts(3)
                                .build())

                // --- 工具与主循环 ---
                .toolkit(toolkit)
                .maxIters(20)
                .permissionContext(
                        PermissionContextState.builder()
                                .mode(PermissionMode.DEFAULT)
                                .build())

                // --- 工作区与上下文预算 ---
                .workspace(Paths.get("/data/agent-workspace"))
                .additionalContextFile("PREFERENCES.md")
                .maxContextTokens(8000)

                // --- 历史管理 ---
                .compaction(CompactionConfig.builder().build())

                .build();
```

## 身份与提示词

| 方法 | 默认值 | 作用 |
|------|--------|------|
| `name(String)` | 必填 | Agent 标识，用于消息和日志，也是命名空间键的兜底值 |
| `sysPrompt(String)` | 必填 | 基础系统提示词，工作区内容会叠加在其之上 |
| `description(String)` | `null` | 人类可读描述；当该 Agent 作为子智能体时展示给编排方 |
| `agentId(String)` | 回退到 `name` | 存储状态的稳定命名空间键（`[agents, <agentId>, users, <userId>, …]`）。显式设置可避免重命名导致状态失联 |
| `environmentMemory(String)` | `null` | 追加到系统提示词中每会话环境块的额外文本，位于 session id 旁 |
| `environment(String)` | `"prod"` | 供 Skill `EnvironmentFilter` 读取的部署环境标签 |

<Tip>

任何会持久化状态的部署都应显式设置 `agentId`。未设置时 `name` 会兼作命名空间键，此时改一个展示名就会把 Agent 指向一个全新的空状态命名空间。

</Tip>

## 模型

| 方法 | 默认值 | 作用 |
|------|--------|------|
| `model(String)` | 必填 | `ModelRegistry` id，形如 `"<provider>:<model>"`；自动读取对应厂商的 API Key 环境变量 |
| `model(Model)` | 必填 | 需要自定义 endpoint、超时或 Header 时，显式传入 `ChatModelBase` |
| `fallbackModel(String)` / `fallbackModel(Model)` | `null` | 主模型持续失败时切换到的模型 |
| `maxRetries(int)` | 厂商默认 | 故障转移前的重试次数 |
| `failoverListener(FailoverListener)` | `null` | 备用模型接管时收到通知 |
| `modelExecutionConfig(ExecutionConfig)` | `ExecutionConfig.MODEL_DEFAULTS` | 模型调用的超时、重试次数与退避策略 |
| `generateOptions(GenerateOptions)` | 厂商默认 | temperature、top-p 等采样选项 |
| `modelResolver(Function<String, Model>)` | `null` | 把模型名字符串解析为 `Model` 实例，**供子智能体使用** |

`ExecutionConfig` 让模型调用和工具调用各自独立地控制超时与重试：

```java
import io.agentscope.core.model.ExecutionConfig;
import java.time.Duration;

HarnessAgent agent =
        HarnessAgent.builder()
                .name("agent")
                .sysPrompt("…")
                .model("dashscope:qwen-plus")
                .modelExecutionConfig(
                        ExecutionConfig.builder()
                                .timeout(Duration.ofSeconds(120))
                                .maxAttempts(3)
                                .initialBackoff(Duration.ofSeconds(1))
                                .backoffMultiplier(2.0)
                                .build())
                .toolExecutionConfig(
                        ExecutionConfig.builder()
                                .timeout(Duration.ofSeconds(30))
                                .maxAttempts(1)     // 有副作用的工具不要重试
                                .build())
                .build();
```

## 工具与 ReAct 主循环

| 方法 | 默认值 | 作用 |
|------|--------|------|
| `toolkit(Toolkit)` | 默认 toolkit | 承载工具、MCP 客户端、Skill 和 tool group 的 `Toolkit` |
| `maxIters(int)` | `10` | 单次调用中推理/行动的最大轮数 |
| `toolExecutionConfig(ExecutionConfig)` | `ExecutionConfig.TOOL_DEFAULTS` | 工具调用的超时、重试次数与退避策略 |
| `permissionContext(PermissionContextState)` | `DEFAULT` 模式 | allow / ask / deny 规则，参见[权限系统](/v2/zh/docs/building-blocks/permission-system) |
| `stopOnReject(boolean)` | `false` | 工具调用被拒绝时停止主循环，而不是继续 |
| `enableMetaTool(boolean)` | `false` | 注册 `reset_tools` 元工具以支持自管理 tool group |
| `enableTaskList()` / `enableTaskList(boolean)` | 关闭 | 注册内置的 todo / 任务清单工具 |
| `enablePendingToolRecovery(boolean)` | `false` | 新消息到达时恢复孤立的工具调用 |
| `toolsConfig(ToolsConfig)` | 读取 `workspace/tools.json` | 以编程方式覆盖 MCP 与允许列表配置文件 |
| `registerExternalSchemas(List<ToolSchema>)` | `List.of()` | 仅有 Schema 的外部工具，执行时挂起交由 worker 完成 |
| `mcpServerRegistrationListener(…)` | `null` | 接收 MCP Server 注册的终态结果；不会传递给子智能体 |
| `webHttpClient(HttpClient)` | JDK 默认 | 内置 `web_fetch` / `web_search` 工具使用的自定义 `HttpClient` |
| `artifactDeliveryTarget(ArtifactDeliveryTarget)` | `null` | 注册 `deliver_artifact`，让 Agent 可以交付产物文件 |
| `checkRunning(boolean)` | `true` | 同一会话的并发调用直接拒绝，而不是排队 |

<Note>

`webHttpClient` 主要用于强制 HTTP/1.1。默认客户端会协商 HTTP/2 并在失败时自动回退到 HTTP/1.1；少数服务端协商会失败，此时注入一个仅 HTTP/1.1 的客户端即可解决，无需改动工具代码。

</Note>

## 工作区与上下文

| 方法 | 默认值 | 作用 |
|------|--------|------|
| `workspace(Path)` / `workspace(String)` | 见解析顺序 | 存放 `AGENTS.md`、`MEMORY.md`、`skills/`、`subagents/`、`tools.json` 的工作区根目录 |
| `additionalContextFile(String)` | 无 | 工作区相对路径的文件，全文内联进系统提示词；可多次调用 |
| `maxContextTokens(int)` | `8000` | `MEMORY.md` 注入的预算 |
| `useLegacyXmlWorkspaceContext(boolean)` | `false` | 用旧版 XML 风格而非 Markdown 渲染工作区上下文 |

未调用 `workspace(...)` 时，`build()` 按以下顺序解析：builder 显式值 → 系统属性 `agentscope.workspace` → 环境变量 `AGENTSCOPE_WORKSPACE` → `${user.dir}/.agentscope/workspace`。目录结构与各文件的加载方式参见[工作区](/v2/zh/docs/harness/workspace)。

## 文件系统与沙箱

| 方法 | 默认值 | 作用 |
|------|--------|------|
| `filesystem(LocalFilesystemSpec)` | 本地 | 本地磁盘 + Shell，带路径白名单 |
| `filesystem(RemoteFilesystemSpec)` | — | Redis / JDBC / OSS 支撑的共享存储 |
| `filesystem(SandboxFilesystemSpec)` | — | 文件与命令隔离在 Docker / K8s 沙箱中 |
| `filesystemRoute(String, AbstractFilesystem)` | 无 | 在主文件系统之外，按路径前缀挂载额外的文件系统 |
| `abstractFilesystem(AbstractFilesystem)` | — | 逃生舱：直接提供自定义实现 |
| `distributedStore(DistributedStore)` | `null` | 一次性提供状态存储、远程存储与快照规格 |

参见[文件系统](/v2/zh/docs/harness/filesystem)与[沙箱](/v2/zh/docs/harness/sandbox)。

## 状态、记忆与历史

| 方法 | 默认值 | 作用 |
|------|--------|------|
| `stateStore(AgentStateStore)` | `~/.agentscope/state/<agentId>/` 下的 `JsonFileAgentStateStore` | 按 `(userId, sessionId)` 持久化 `AgentState` 的位置 |
| `defaultSessionId(String)` | Agent 的 `name` | 调用的 `RuntimeContext` 未携带 session id 时使用的兜底值 |
| `memory(MemoryConfig)` | `MemoryConfig.defaults()` | 长期记忆的提示词与触发策略 |
| `compaction(CompactionConfig)` | 默认配置 | 对话历史何时、如何压缩 |
| `toolResultEviction(ToolResultEvictionConfig)` | 默认值 | 把超大工具结果转存到磁盘并留下占位符 |
| `transcriptStore(TranscriptStore)` | 默认 | 覆盖会话转写的分段追加存储 |
| `transcriptTenant(String)` | `"default"` | 转写对象键中使用的租户段 |

参见[记忆](/v2/zh/docs/harness/memory)与[上下文压缩](/v2/zh/docs/harness/compaction)。

## 子智能体

| 方法 | 默认值 | 作用 |
|------|--------|------|
| `subagent(SubagentDeclaration)` | 无 | 以代码方式声明一个子智能体 |
| `subagents(List<SubagentDeclaration>)` | 无 | 一次声明多个 |
| `subagentFactory(String, Function<String, Agent>)` | 无 | 为指定 agent id 完全自定义构造过程 |
| `subagentFactory(String, String, Function<String, Agent>)` | 无 | 同上，并附带展示给编排方的描述 |
| `taskRepository(TaskRepository)` | 默认 | 后台子智能体任务记录的存储位置 |
| `externalSubagentTool(Object)` | `null` | 注入外部子智能体工具，通常是 `SessionsTool` |

子智能体也可以直接以文件形式放在 `workspace/subagents/` 中，无需改代码。参见[子智能体](/v2/zh/docs/harness/subagent)。

## Skill

| 方法 | 默认值 | 作用 |
|------|--------|------|
| `skillRepository(AgentSkillRepository)` | 无 | 添加一个 Skill 来源（Git、Nacos、MySQL、classpath） |
| `skillRepositories(List<…>)` | 无 | 一次添加多个来源 |
| `projectGlobalSkillsDir(Path)` | `null` | 项目级全局 Skill 目录，优先级低于市场与工作区 Skill |
| `enableSkills(String...)` | 全部 | 白名单：只暴露这些 Skill |
| `disableSkills(String...)` | 无 | 黑名单：除这些之外全部暴露 |
| `skillsEnabled(boolean)` | `true` | 一次性开启或关闭全部 Skill |
| `skillFilter(SkillFilter)` | `null` | 完全控制哪些 Skill 可见 |
| `enableSkillManageTool(…)` | 关闭 | 允许 Agent 自行创建 / 编辑 / 归档工作区 Skill |
| `enableSkillPromotionGate(…)` | 关闭 | Agent 自建 Skill 的晋级闸门与可见性过滤链 |
| `enableSkillCurator(SkillCuratorConfig)` | 关闭 | 后台 Skill 策展器；依赖 `enableSkillManageTool` |

`enableSkills` 与 `disableSkills` 都是写入 `skillFilter` 的简写，因此后调用的会覆盖先调用的：

```java
// 无论仓库提供了什么，只暴露这两个 Skill。
HarnessAgent.builder()
        .name("agent")
        .sysPrompt("…")
        .model("dashscope:qwen-plus")
        .enableSkills("code-review", "changelog")
        .build();
```

参见 [Skill](/v2/zh/docs/harness/skill)。

## Plan Mode

| 方法 | 默认值 | 作用 |
|------|--------|------|
| `enablePlanMode()` / `enablePlanMode(boolean)` | 关闭 | 只读的「先想后做」阶段，带人工确认出口 |
| `planFileDirectory(String)` | `plans/` | 计划文件的写入目录 |
| `allowShellInPlanMode()` / `allowShellInPlanMode(boolean)` | `false` | 允许在 Plan Mode 期间使用 Shell 工具 |

Plan Mode 默认严格只读，且禁用 Shell——Shell 是双刃工具，无法仅凭名称判定为只读。确实需要用 Shell 做排查时再显式开启。参见 [Plan Mode](/v2/zh/docs/harness/plan-mode)。

## 中间件与 Hook

| 方法 | 默认值 | 作用 |
|------|--------|------|
| `middleware(MiddlewareBase)` | 无 | 添加一个中间件；你的中间件在 Harness 内置中间件**之前**执行 |
| `middlewares(List<? extends MiddlewareBase>)` | 无 | 一次添加多个 |
| `hook(Hook)` / `hooks(List<Hook>)` | 无 | 更底层的生命周期 Hook |
| `enableAgentTracingLog(boolean)` | `true` | 通过 `AgentTraceMiddleware` 输出执行链路日志 |

参见[中间件](/v2/zh/docs/building-blocks/middleware)。

## Teams、消息总线与异步工具

| 方法 | 默认值 | 作用 |
|------|--------|------|
| `teamsMode(TeamClient, TeamContext)` | 关闭 | AgentTeams 模式：挂载 `TeamsMiddleware` 并注册按角色裁剪的 `team` 工具 |
| `teamsMode(TeamClient, TeamContext, String)` | 关闭 | 同上，并绑定 session id，使控制面的 team 事件能抵达该 Agent |
| `messageBus(MessageBus)` | `null` | 基于收件箱的消息投递；自动注册 `InboxMiddleware`，在每轮推理前排空收件箱 |
| `asyncToolTimeout(Duration)` | `null` | 超时的工具转入后台执行；**依赖 `messageBus`** |
| `asyncToolRegistry(AsyncToolRegistry)` | `null` | 跟踪异步工具执行，以便检测并清理僵死任务 |

## 关闭内置能力

Harness 的各项能力在安全的前提下默认开启。下列开关用于移除它们——适合调试、收窄工具面，或把 Agent 嵌入到并不需要该能力的场景：

| 方法 | 关闭的内容 |
|------|-----------|
| `disableWorkspaceContext()` | 系统提示词中 `AGENTS.md` / `MEMORY.md` / `knowledge/` 的注入 |
| `disableSessionPersistence()` | `AgentState` 自动持久化 |
| `disableCompaction()` | 整个对话压缩 |
| `disableToolResultEviction()` | 超大工具结果的转存 |
| `disableTranscript()` | 独立的会话转写中间件 |
| `disableMemoryHooks()` | 记忆刷写与后台维护 |
| `disableMemoryTools()` | `memory_search` / `memory_get` / `memory_save` / `session_search` |
| `disableFilesystemTools()` | 内置文件系统工具 |
| `disableShellTool()` | 内置 Shell 工具 |
| `disableWebTools()` | 基于 Tavily 的 `web_search` / `web_fetch` 工具 |
| `disableSubagents()` | 整个子智能体子系统 |
| `disableDynamicSubagents()` | 运行时动态创建子智能体，保留已声明的 |
| `disableDynamicSkills()` | 每轮 Skill 重新合并；退化为构建时一次性合并 |
| `disableDefaultWorkspaceSkills()` | 默认的命名空间化工作区 Skill 仓库 |
| `disableToolsConfig()` | 读取 `workspace/tools.json` |
| `disableAtPathExpansion()` | 把用户消息中的 `@path` 引用展开为附件文件块 |

把工具面收窄到只剩自有工具的最小 Agent：

```java
HarnessAgent agent =
        HarnessAgent.builder()
                .name("narrow-agent")
                .sysPrompt("You answer questions using only the provided tools.")
                .model("dashscope:qwen-plus")
                .toolkit(myToolkit)
                .disableShellTool()
                .disableFilesystemTools()
                .disableWebTools()
                .disableSubagents()
                .disableMemoryTools()
                .build();
```

## 代码之外的配置来源

有两项设置可以从环境提供，使同一个镜像能用于多套部署：

| 设置项 | 系统属性 | 环境变量 |
|--------|----------|----------|
| 工作区根目录 | `agentscope.workspace` | `AGENTSCOPE_WORKSPACE` |
| 模型 API Key | — | `DASHSCOPE_API_KEY`、`OPENAI_API_KEY`、`ANTHROPIC_API_KEY`、`DEEPSEEK_API_KEY`、`GEMINI_API_KEY` |

```dockerfile
ENV AGENTSCOPE_WORKSPACE=/data/agent-workspace
ENV DASHSCOPE_API_KEY=sk-...
```

其余配置一律在代码中通过 builder 设置。工作区属性或环境变量为空白值时视为未设置，继续回退到下一个来源。

## 相关页面

- [Harness 架构](/v2/zh/docs/harness/architecture)——这些能力如何组合
- [工作区](/v2/zh/docs/harness/workspace)——各选项读取的目录结构
- [ReActAgent builder 字段](/v2/zh/docs/building-blocks/agent#builder-fields)——核心 builder
- [权限系统](/v2/zh/docs/building-blocks/permission-system)
- [中间件](/v2/zh/docs/building-blocks/middleware)
