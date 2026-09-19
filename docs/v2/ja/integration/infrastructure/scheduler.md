---
title: Scheduler
---

`agentscope-extensions-scheduler` は Agent を定期的に実行します。例えば「毎日午前 8 時に daily-report Agent を実行する」や「5 秒ごとにヘルスチェック Agent を実行する」といった用途です。このモジュールは統一された `AgentScheduler` インターフェースを抽象化し、2 つの実装を提供します。

| サブモジュール | 実装 | デプロイ形態 |
| --- | --- | --- |
| `agentscope-extensions-scheduler-quartz` | [Quartz](https://www.quartz-scheduler.org/) | スタンドアロンまたはクラスタ(Quartz DB を共有) |
| `agentscope-extensions-scheduler-xxl-job` | [XXL-Job](https://www.xuxueli.com/xxl-job/) | 分散スケジューリング、管理サーバーが必要 |

SPI は `agentscope-extensions-scheduler-common` にあるため、他のスケジューラを組み込むこともできます。

## 共通の概念

- `AgentConfig`(または `RuntimeAgentConfig`): Agent の構築方法 — 名前、モデル設定、システムプロンプト、Toolkit。
- `ScheduleConfig`: スケジューリングポリシー — CRON、固定レート、最大並列度。
- `AgentScheduler`: コアインターフェース — `schedule(...)` / `pause(...)` / `resume(...)` / `cancel(...)` / `shutdown()`。
- `ScheduleAgentTask`: 登録から返されるハンドル。1 つのスケジュールされたタスクを表します。

トリガーのたびに新しい Agent インスタンスが実行用に作成されるため、状態が漏れることはありません。

## Quartz モード

### 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-scheduler-quartz</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

### 使い方

```java
import io.agentscope.extensions.scheduler.AgentScheduler;
import io.agentscope.extensions.scheduler.config.AgentConfig;
import io.agentscope.extensions.scheduler.config.DashScopeModelConfig;
import io.agentscope.extensions.scheduler.config.ScheduleConfig;
import io.agentscope.extensions.scheduler.config.ScheduleMode;
import io.agentscope.extensions.scheduler.quartz.QuartzAgentScheduler;

AgentScheduler scheduler = QuartzAgentScheduler.builder()
    .autoStart(true)
    .build();

AgentConfig agent = AgentConfig.builder()
    .name("DailyReportAgent")
    .modelConfig(DashScopeModelConfig.builder()
        .apiKey(apiKey).modelName("qwen-plus").build())
    .sysPrompt("You are a report assistant; please generate a sales summary every day.")
    .build();

ScheduleConfig schedule = ScheduleConfig.builder()
    .scheduleMode(ScheduleMode.FIXED_RATE)
    .fixedRate(5_000L)   // 5秒ごと
    // または .scheduleMode(ScheduleMode.CRON).cron("0 0 8 * * ?")
    .build();

scheduler.schedule(agent, schedule);
```

実行時の制御:

```java
scheduler.pause("DailyReportAgent");
scheduler.resume("DailyReportAgent");
scheduler.cancel("DailyReportAgent");
scheduler.shutdown();
```

## XXL-Job モード

### 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-scheduler-xxl-job</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

### 使い方

```java
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import io.agentscope.extensions.scheduler.xxljob.XxlJobAgentScheduler;

// 1) XXL-Job エグゼキューターを起動する
XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
executor.setAdminAddresses("http://localhost:8080/xxl-job-admin");
executor.setAppname("agentscope-demo");
executor.setAccessToken("xxxxxxxx");
executor.setPort(9999);
executor.start();

// 2) AgentScheduler としてラップする
AgentScheduler scheduler = new XxlJobAgentScheduler(executor);

// 3) Agent を JobHandler として登録する
ScheduleAgentTask task = scheduler.schedule(agentConfig, ScheduleConfig.builder().build());
```

登録後、**スケジュール(CRON、並列度、ルーティング)は XXL-Job の管理コンソールで設定します**。Agent 名 `DailyReportAgent` は、そこに JobHandler として表示されます。

## ツールのバインド

Toolkit をバインドする必要がある場合は、`RuntimeAgentConfig`(今後変更される可能性がある過渡的な API)を使用します。

```java
RuntimeAgentConfig agent = RuntimeAgentConfig.builder()
    .name("OpsAgent")
    .modelConfig(modelConfig)
    .sysPrompt("Run health checks and send alerts.")
    .toolkit(toolkit)
    .build();
```

## どれを選ぶか

- **ローカルまたは小規模クラスタで、外部のスケジューラサービスが不要な場合** → Quartz
- **コンソール、ノード間ルーティング、タスクログが必要な場合** → XXL-Job
- **独自のスケジューラフレームワークを持ち込む場合** → `AgentScheduler` SPI を実装する
