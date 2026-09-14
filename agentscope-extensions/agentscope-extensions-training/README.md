# agentscope-extensions-training

Reinforcement-learning training-loop integration for AgentScope agents. It lets a
sampled fraction of an agent's production calls be re-run in the background as
"shadow" rollouts against a training backend, scored with a pluggable
`RewardCalculator`, and fed back to that backend to drive online RL — without
touching the production agent's request path or blocking the caller. The module
currently targets a specific backend, **Trinity**, whose Chat API is OpenAI-compatible.

## Installation

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-training</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## Quick Start

`TrainingRunner` is the entry point. It registers a `TrainingRouter` as an
AgentScope **system hook** (`AgentBase.addSystemHook`), so once started it
transparently applies to every `Agent` in the process — you keep calling your
agent exactly as before:

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.training.reward.RewardCalculator;
import io.agentscope.core.training.runner.TrainingRunner;
import io.agentscope.core.training.strategy.SamplingRateStrategy;
import java.util.List;

ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .model("dashscope:qwen-plus")
                .sysPrompt("You are a helpful assistant.")
                .build();

// Scores a shadow rollout after it completes; typically inspects the shadow
// agent's final state/output rather than the production agent's.
RewardCalculator rewardCalculator = shadowAgent -> 1.0;

TrainingRunner runner =
        TrainingRunner.builder()
                .trinityEndpoint("http://localhost:8010")
                .modelName("/home/ecs-user/models/Qwen2.5-0.5B-Instruct")
                .selectionStrategy(SamplingRateStrategy.of(0.1)) // sample 10% of calls
                .rewardCalculator(rewardCalculator)
                .commitIntervalSeconds(300) // trigger a Trinity commit every 5 minutes
                .build();

runner.start();

// Used completely normally — 100% of calls still go through the agent's own
// model. TrainingRouter decides in the background, per call, whether to also
// run a shadow rollout against Trinity.
Msg reply =
        agent.call(
                        List.of(
                                Msg.builder()
                                        .role(MsgRole.USER)
                                        .textContent("What's the weather today?")
                                        .build()))
                .block();

runner.stop(); // flushes a final commit and unregisters the hook
```

## Architecture

### Shadow-traffic flow

`TrainingRouter` is a `Hook` with `priority()` 500 (runs after business logic).
On `PostCallEvent` it:

1. Skips agents whose name contains `-shadow` (prevents a shadow agent's own
   call from re-triggering training).
2. Asks the configured `TrainingSelectionStrategy` whether this call should be
   trained on, via `shouldSelect(agent, inputMessages, outputMessage, reactorContext)`.
3. On acceptance, resolves a Task ID (`SelectionDecision.getMetadata()`'s
   `"taskId"` if the strategy supplied one, otherwise a fresh one from
   `TaskIdGenerator`) and, for each of `TrainingConfig.getRepeatTime()` runs,
   allocates a Run ID from `RunRegistry` and builds a `RunExecutionContext`.
4. For each run, asynchronously (on a bounded elastic `Scheduler` sized by
   `shadowPoolSize`/`shadowPoolCapacity`): clones the production agent with
   `AgentCloner.cloneWithModel(agent, trinityModel)`, replays the same input
   messages against the clone, collects the message IDs Trinity returned,
   scores the run with `RewardCalculator.calculate(shadowAgent)`, and submits
   a `FeedbackRequest` via `TrinityClient.feedback(...)`.

`TrainingRunner.start()` additionally schedules a periodic `TrinityClient.commit(...)`
call (`TrainingConfig.getCommitIntervalSeconds()`) to trigger training on
accumulated feedback, and `stop()` performs one last commit before removing the
hook.

### Selection strategies

`TrainingSelectionStrategy` (`@FunctionalInterface`) decides, per call, whether
it becomes a training sample:

```java
SelectionDecision shouldSelect(
        Agent agent, List<Msg> inputMessages, Msg outputMessage, ContextView reactorContext);
```

Two implementations ship with the module:

- **`SamplingRateStrategy.of(double sampleRate)`** — accepts each call
  independently with probability `sampleRate` (priority 200, i.e. a low-priority
  fallback). `TrainingConfig` falls back to `SamplingRateStrategy.of(0.1)` when
  no strategy is configured.
- **`ExplicitMarkingStrategy.create()`** / **`.withTTL(Duration)`** — only
  accepts calls the caller has explicitly marked via `TrainingContext`
  (priority 10, i.e. takes precedence). Marking is propagated through Reactor
  `Context` so it survives the async hand-off to `PostCallEvent`:

  ```java
  import io.agentscope.core.training.strategy.TrainingContext;

  Msg reply =
          agent.call(inputs)
                  .contextWrite(TrainingContext.mark("high-quality"))
                  .block();
  ```

  `TrainingContext.mark()` also has overloads for a plain enable, for
  `(labels, metadata)`, and for `metadata` alone; a marking expires (and is
  rejected) after `ExplicitMarkingStrategy`'s TTL (default one minute).

A custom strategy is just another `TrainingSelectionStrategy` implementation —
useful for combining sampling with business rules (e.g. only train on calls
from a specific tenant).

### Trinity backend

- **`TrinityClient`** (`TrinityClient.builder().endpoint(...).timeout(...).build()`)
  is a thin OkHttp client for Trinity's two training-specific endpoints,
  `POST {endpoint}/feedback` and `POST {endpoint}/commit` — chat completions are
  **not** routed through it.
- **`TrinityModelAdapter`** (`TrinityModelAdapter.builder().baseUrl(...).modelName(...).apiKey(...).build()`)
  is a `ChatModelBase` that wraps `OpenAIChatModel` by composition (Trinity's
  chat API is OpenAI-compatible) and, on every response, extracts
  `response.getId()` into the `RunExecutionContext` it was built with — this is
  how msg IDs make it into the eventual `FeedbackRequest`. It forces
  non-streaming mode (Trinity doesn't support streaming) and is created
  internally by `TrainingRouter`; you normally never instantiate it yourself.

### Agent cloning

`AgentCloner.cloneWithModel(Agent original, Model newModel)` builds a shadow
copy of the production agent that shares no memory/state with it and only
swaps in the training model. **Only `ReActAgent` is currently supported** — it
is cloned by reflectively reading `sysPrompt`, `toolkit`, `maxIters`,
`modelExecutionConfig`, `toolExecutionConfig`, and `toolExecutionContext` off
the original and rebuilding via `ReActAgent.builder()` with the name suffixed
`-shadow`. Calling it with any other `Agent` implementation throws
`UnsupportedOperationException`.

### Configuration reference (`TrainingConfig`)

Built via `TrainingConfig.builder()` (or the equivalent shortcut methods on
`TrainingRunner.builder()`):

| Builder method | Default | Notes |
|---|---|---|
| `trinityEndpoint(String)` | — | required |
| `rewardCalculator(RewardCalculator)` | — | required |
| `trinityApiKey(String)` | `"dummy"` | some Trinity deployments don't check it |
| `modelName(String)` | `"training-model"` | training model path/id passed to Trinity |
| `selectionStrategy(TrainingSelectionStrategy)` | `SamplingRateStrategy.of(0.1)` | |
| `commitIntervalSeconds(long)` | `300` | `<= 0` disables the periodic commit scheduler |
| `httpTimeout(Duration)` | 300s | applied to `TrinityClient`'s connect/read/write timeouts |
| `enableAutoCommit(boolean)` | `true` | |
| `shadowPoolSize(int)` / `shadowPoolCapacity(int)` | `10` / `1000` | size/queue depth of the bounded elastic scheduler shadow rollouts run on |
| `repeatTime(int)` | `1` | run the same task `repeatTime` times (same Task ID, incrementing Run IDs) — useful for A/B/C/D or stability comparisons |

`TrainingConfig.Builder.sampleRate(double)` is a deprecated shortcut for
`selectionStrategy(SamplingRateStrategy.of(sampleRate))`.
