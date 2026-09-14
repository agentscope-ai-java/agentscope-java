---
title: "Plan Mode"
description: "行動する前に考える:プランファイルを書き出し、実行前に HITL 承認を必要とする読み取り専用フェーズ"
---

## 役割

プランモードは、エージェントが実行する前に「意図を整理して書き留める」ことを可能にします。有効な間、エージェントは**読み取り専用フェーズ**にあります。

- 動作するのは**読み取り専用ツール**と4つのホワイトリストツール(`plan_enter` / `plan_write` / `plan_exit` / `todo_write`)だけです(シェルはオプトインできます――[下記](#プランフェーズ中にシェル利用を許可するオプトイン)を参照)。
- それ以外のツール呼び出しはすべて即座に拒否されます(エージェントには「plan-mode denied」という注記が見えます)。
- プランモードを抜けるには HITL の確認(パーミッションシステムの ASK を再利用)が必要なため、モデルが一方的に実行へ飛び込むことはできません。

このパイプラインは「設計 → プラン → 人間によるレビュー → 実行」をエンコードします――`todo_write` とサブエージェントを組み合わせると、長いタスクにおける「即興で進めて後で壊す」という結末を目に見えて減らせます。

## オプトイン

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("planner")
    .model(model)
    .workspace(workspace)
    .enablePlanMode()                          // installs the Plan Mode trio
    .planFileDirectory("plans")                // optional; default "plans"
    .build();
```

ビルダーのオプション:

| メソッド | デフォルト | 備考 |
|--------|---------|-------|
| `enablePlanMode()` / `enablePlanMode(boolean)` | `false` | プランモードを有効にする |
| `planFileDirectory(String)` | `"plans"` | プランファイルのルート(ワークスペース相対) |
| `allowShellInPlanMode()` / `allowShellInPlanMode(boolean)` | `false` | プランフェーズ中にシェル(`execute`)を実行することをオプトインする――[プランフェーズ中にシェル利用を許可する](#プランフェーズ中にシェル利用を許可するオプトイン)を参照 |

`enableTaskList()` を呼び出すこともでき、プランフェーズ中に作成された todo が、各推論ステップの前に小さなリマインダーとして表示されるようになります。

## 3つのツール

| ツール | 目的 | パラメータ |
|------|---------|--------|
| `plan_enter` | プランモードに入る | なし |
| `plan_write` | 現在のプランファイル(デフォルト `plans/PLAN.md`)に内容を書き込む | `content` |
| `plan_exit` | プランモードを抜けて実行フェーズへ → HITL 確認 | `rationale`(任意) |

`plan_write` は**プランモード専用の書き込みエントリポイント**です――汎用の `write_file` をホワイトリストに入れる(プラン中にモデルがどこにでも書き込めてしまう)セキュリティリスクを回避しています。

## ワークフロー

```{mermaid}
sequenceDiagram
    autonumber
    participant U as User
    participant A as Agent
    participant H as Human (HITL)
    participant FS as workspace

    U->>A: "Refactor module X for me"
    A->>A: plan_enter
    A->>A: think → call read_file / grep_files (read-only)
    A->>FS: plan_write to plans/PLAN.md
    A->>H: plan_exit → HITL confirmation
    H-->>A: ConfirmResult(true)
    A->>A: enter execution phase: all tools allowed
```

プランフェーズ中にホワイトリストにないツール呼び出し(例:`write_file`、あるいは[オプトイン](#プランフェーズ中にシェル利用を許可するオプトイン)しない限りの `execute`)は、次のようなもので即座に拒否されます。

```text
[Tool denied — plan mode is active]
Only read-only tools and plan_enter / plan_write / plan_exit / todo_write are allowed.
```

拒否を見たモデルは、自然に「まずプランを書く」という方向に戻ります。

## 結果を読み取る

プランモードへの突入は自律的に行われるため、実行は4つの状態のいずれかで終わることがあります。`isPlanModeActive() == false` だけでは曖昧です――実際にプランニングが行われたかどうかを確認せずに成功と見なしてはいけません。

| 終了状態 | 意味 |
|----------------|---------|
| プランモードに入らなかった | モデルは build モードで直接作業することを選んだ――妥当な判断であり、タスクがワークスペースに合致していないことが多い理由です。 |
| 入って → `plan_exit` した | 成功:プランを立て、承認を得て、いまは build モードにいる。 |
| まだプランモードにいて `PLAN.md` が存在する | プランを起草したが抜けていない;セッションを再開して承認する。 |
| まだプランモードにいて `PLAN.md` が存在しない | 「語るだけで行動しない」:最終メッセージはプランのように*読める*かもしれないが、何も書かれていない――より具体的な入力か、合致するコードベースを与えてください。 |

これらをプログラムで区別するには、`plan_enter` / `plan_write` が呼ばれたかどうか(例えば `ToolCallStartEvent` から)を、最終的な `isPlanModeActive()` とプランファイルの存在とあわせて追跡してください。

## プランフェーズ中にシェル利用を許可する(オプトイン)

デフォルトでは、プランフェーズ中はシェルツール(`execute`)が**拒否**されます。シェルは*両用*です:1回のツール呼び出しで、読み取り(`cat` / `ls` / `grep` / `git log`)にも変更(`rm` / `>` / `git commit` / `npm install`)にもなり得ます。プランモードは何を許可するかを純粋に**ツール名**で判断するため、読み取り呼び出しと書き込み呼び出しを区別できません。シェルを拒否することで、読み取り専用の保証を保っています。

しかし、シェルアクセスはしばしばコードベースを調査し、*現実的な*プランを作成するための最も柔軟な手段です。このトレードオフを受け入れる場合は、オプトインしてください。

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("planner")
    .model(model)
    .workspace(workspace)
    .enablePlanMode()
    .allowShellInPlanMode()   // let the model run the shell read-only during plan
    .build();
```

オプトインを有効にすると:

- `execute` がプランフェーズの許可リストに追加され、モデルはシェル経由で調査できるようになります。
- プランバナーに、シェルの使用を**読み取り専用**(`cat` / `ls` / `grep` / `git log/diff/show/status`)に保ち、プランが承認されるまで変更コマンドを実行**しない**よう指示する追加の指示が加わります。
- 専用のファイル編集ツール(`write_file` / `edit_file`)は**拒否されたままです**――これらは主要な変更経路であるため、ファイル書き込みについては読み取り専用の意図が引き続き強制されます。

これは、OpenCode がプランエージェントを扱う方法を反映しています:調査のためにシェルを許可し、編集/書き込みツールをハードブロックし、プロンプトに頼ってシェルを読み取り専用に保ちます。したがってこの保証はデフォルトよりも*ソフト*です(モデルはシェル経由で依然として変更できる可能性がある)。爆発半径を抑えるために、これを**サンドボックス化されたファイルシステム**と併せて有効にすることを推奨します。

## ランタイムでのパーミッション切り替え(「bypass」という脱出口)

プランモードは特定のフェーズ切り替えの1つです。その下では、すべてのセッションが、パーミッションエンジンが評価対象とする [`PermissionMode`](../building-blocks/context.md) を保持しています。このモードはランタイムで切り替えることができます――たとえば、他のコーディングツールにある YOLO / 危険スキップスイッチに似た、意図的でユーザー起点の「すべてのパーミッションプロンプトをスキップする」トグルを付与するために。

```java
RuntimeContext ctx = RuntimeContext.builder().sessionId("my-session").build();

agent.setPermissionMode(ctx, PermissionMode.BYPASS);    // allow everything, no prompts
// ... run the operations that need full access ...
agent.setPermissionMode(ctx, PermissionMode.DEFAULT);   // restore normal enforcement

PermissionMode current = agent.getPermissionMode(userId, sessionId);
```

`setPermissionMode(...)` はセッションに設定された allow/deny/ask ルールと作業ディレクトリを保持します――変わるのはモードだけです――そして、そのセッションのキャッシュされたパーミッションエンジンを再構築するため、切り替えは**次の**呼び出しから有効になります。実行中の呼び出しは、開始時点のエンジンを使い続けます。

⚠ `BYPASS` はすべてのルール評価を無効にするため、明示的でセッションごとのオプトイン操作として扱い、サンドボックスと組み合わせることを推奨します。プロンプトなしで無人実行しつつ強制力は*維持*したい場合は、代わりに `PermissionMode.DONT_ASK` を使ってください(ASK の判断は自動許可ではなく DENY になります)。

## プランの状態は永続化される

プランモードは**ランタイム状態**であり、`AgentState` とともに自動的に永続化されます――プロセスの再起動、ノードのフェイルオーバー、レプリカ間の復元、いずれもプランフェーズを呼び戻します。プランファイル自体はワークスペースの `plans/` に書き込まれ、設定したファイルシステムモード(ローカル / サンドボックス / リモート KV)を経由するため、分散環境でも安全です。

## プログラムによる enter/exit

アプリケーションコードがプランモードを駆動する場合(例:管理コンソールのボタン):

```java
RuntimeContext ctx = RuntimeContext.builder().sessionId("my-session").build();
agent.enterPlanMode(ctx);    // equivalent to the LLM calling plan_enter
agent.exitPlanMode(ctx);     // equivalent to plan_exit; programmatic entry does NOT trigger HITL
agent.isPlanModeActive(ctx);
```

`agentscope-admin-spring-boot-starter` を使っている場合、管理用 HTTP API もプランモードの操作(`POST /v1/admin/sessions/{id}:enter-plan-mode` / `:exit-plan-mode` / `GET /v1/admin/sessions/{id}/plan`)を公開します。

## サブエージェントとの相互作用

⚠ 現在の**既知の制約**:プランモード中に `agent_spawn` で spawn されたサブエージェントは**読み取り専用の制約を自動的に継承しません**。子を制限するには:

- 子の宣言の `tools` を読み取り専用のセットに絞り込む、あるいは
- 子自身のビルダーでも `enablePlanMode()` を呼び、明示的に入る

将来のリリースでは、プランモードの制約が親から子へ自動的に伝播するようになる予定です。

## `todo_write` との連携

プランモードと(core が提供する)`todo_write` は**独立していますが、よく一緒に使われます**。

- **プランモード** — フェーズ切り替え + プランファイル + HITL による退出
- **`todo_write`** — 実行中に構造化された「いま何をすべきか」のリストを維持する(リスト全体を置き換え;`in_progress` はちょうど1つ)

典型的なワークフロー:プランフェーズ中に `PLAN.md` を書く → `plan_exit` → 実行中に `todo_write` を使って PLAN を5〜8個の todo に分割する → 一度に1つずつ進める。各推論ステップで、エージェントに todo のリマインダーが示され、集中を保てます。

⚠ サブエージェントの**バックグラウンドタスク**(`task_output` / `task_cancel` / `task_list`)と混同しないでください――それは別の概念です。[Subagent](./subagent.md) を参照してください。

## タスクリストを表示する

タスクリストは `AgentState.tasksContext` に存在し、すべての `call()` で自動的に永続化されます。アプリケーションコードからそれを読み取るには:

```java
List<Task> tasks = agent.getAgentState(userId, sessionId)
        .getTasksContext()
        .getTasks();

for (Task t : tasks) {
    System.out.printf("[%s] %s%n", t.getState(), t.getSubject());
    // state: PENDING / IN_PROGRESS / COMPLETED
}
```

`agentscope-admin-spring-boot-starter` を使っている場合、管理用 REST API はすぐ使えるエンドポイントを提供します。

```
GET /v1/admin/sessions/{sessionId}/tasks
```

各タスクの subject、状態、所有者、依存関係情報(`blocks` / `blockedBy`)を返します。

タスクの変化をイベントストリームでリアルタイムに観察するには、`streamEvents()` 内で `todo_write` のツール呼び出しをリッスンします。

```java
agent.streamEvents(message)
    .filter(e -> e.getType() == AgentEventType.TOOL_RESULT_END)
    .filter(e -> "todo_write".equals(((ToolResultEndEvent) e).getToolCallName()))
    .doOnNext(e -> {
        // Re-read the latest task list from state
        var tasks = agent.getAgentState(userId, sessionId)
                .getTasksContext().getTasks();
        updateUI(tasks);
    })
    .subscribe();
```

## 関連ページ

- [Workspace](./workspace.md) — `plans/` ディレクトリの場所
- [Subagent](./subagent.md) — `todo_write` ≠ サブエージェントのタスク;混同しないこと
- [Architecture](./architecture.md) — プランモードが call() のタイムライン上のどこに位置するか
