---
title: 設定
description: HarnessAgent.builder() の完全なリファレンス。アイデンティティ、モデル、ツール、ワークスペース、ファイルシステム、状態、サブエージェント、スキル、Plan
  Mode、および組み込み機能を無効化するすべてのスイッチを関心事ごとに整理
---

`HarnessAgent.builder()` は、あらゆる Harness 機能を有効化・差し替え・無効化する唯一の場所です。このページは、各オプションが解決する問題ごとに整理した完全な builder リファレンスです。ワークスペースもメモリもサブエージェントも要らない素の ReAct ループだけが必要なら、[`ReActAgent` の builder フィールド](/v2/ja/docs/building-blocks/agent#builder-fields)を参照してください。両者はフィールド名の大半を共有しており、移行はほぼ機械的です。

## 動く最小構成

必須は `name`、`sysPrompt`、`model` の 3 つだけで、残りはすべて実用的な既定値を持ちます:

```java
import io.agentscope.harness.agent.HarnessAgent;

HarnessAgent agent =
        HarnessAgent.builder()
                .name("assistant")
                .sysPrompt("You are a helpful assistant.")
                .model("dashscope:qwen-plus")   // 環境変数 DASHSCOPE_API_KEY を自動で読む
                .build();

String reply = agent.call("What can you do?").block().getTextContent();
System.out.println(reply);
```

`.workspace(...)` を呼ばない場合、エージェントはワークスペースを `${user.dir}/.agentscope/workspace` に解決し、状態を `~/.agentscope/state/<agentId>/` に永続化し、既定のファイルシステム・シェル・メモリ・todo ツールを登録します。

## 本番構成

長期稼働するデプロイで通常気にするすべての選択を固定した構成です:

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
                // --- アイデンティティ ---
                .name("support-agent")
                .agentId("support-agent-v3")         // 保存される状態の安定した名前空間キー
                .sysPrompt("You are a support engineer.")

                // --- モデル ---
                .model("dashscope:qwen-max")
                .fallbackModel("openai:gpt-5.5")     // 主モデルが失敗し続けたときに使用
                .maxRetries(3)
                .modelExecutionConfig(
                        ExecutionConfig.builder()
                                .timeout(Duration.ofSeconds(90))
                                .maxAttempts(3)
                                .build())

                // --- ツールとループ ---
                .toolkit(toolkit)
                .maxIters(20)
                .permissionContext(
                        PermissionContextState.builder()
                                .mode(PermissionMode.DEFAULT)
                                .build())

                // --- ワークスペースとコンテキスト予算 ---
                .workspace(Paths.get("/data/agent-workspace"))
                .additionalContextFile("PREFERENCES.md")
                .maxContextTokens(8000)

                // --- 履歴管理 ---
                .compaction(CompactionConfig.builder().build())

                .build();
```

## アイデンティティとプロンプト

| メソッド | 既定値 | 役割 |
|----------|--------|------|
| `name(String)` | 必須 | メッセージとログで使うエージェント識別子。名前空間キーのフォールバックにもなる |
| `sysPrompt(String)` | 必須 | ワークスペース内容が重なる前のベースシステムプロンプト |
| `description(String)` | `"Agent(<agentId>) <name>"` | このエージェント自身にとってはメタデータのみ。親に公開されるとき**ツールの説明**になる — 下記参照 |
| `agentId(String)` | `name` にフォールバック | 保存される状態の安定した名前空間キー(`[agents, <agentId>, users, <userId>, …]`)。明示設定すればリネームで状態が迷子にならない |
| `environmentMemory(String)` | `null` | システムプロンプトのセッション環境ブロックに、session id と並べて追記されるテキスト |
| `environment(String)` | `"prod"` | スキルの `EnvironmentFilter` が読むデプロイ環境ラベル |

<Note>

**`description` が実際にすること。** このエージェント自身のシステムプロンプトに注入されることは*なく*、ツールの挙動にも影響しません。効くのは、このエージェントが*親*エージェントにツールとして公開されるときだけです。`SubAgentTool` はそのツールの説明を `SubagentDeclaration.description` → このエージェントの `description(...)` → `"Call <name> to complete tasks"` の順で、最初の非空値として解決します。未設定なら `description` は `"Agent(<agentId>) <name>"` になり、親のオーケストレータには何の情報も与えません。委譲先にするエージェントには必ず設定してください。

</Note>

<Tip>

状態を永続化するデプロイでは `agentId` を必ず設定してください。未設定だと `name` が名前空間キーを兼ねるため、表示名を変えただけでエージェントが空の新しい状態名前空間を指してしまいます。

</Tip>

## モデル

| メソッド | 既定値 | 役割 |
|----------|--------|------|
| `model(String)` | 必須 | `ModelRegistry` の id(`"<provider>:<model>"`)。対応する API キー環境変数を読む |
| `model(Model)` | 必須 | エンドポイント・タイムアウト・ヘッダーを自前で決めたいときの明示的な `ChatModelBase` |
| `fallbackModel(String)` / `fallbackModel(Model)` | `null` | 主モデルが失敗し続けたときに切り替える先 |
| `maxRetries(int)` | プロバイダ既定 | フェイルオーバー前のリトライ回数 |
| `failoverListener(FailoverListener)` | `null` | フォールバックモデルが引き継いだときに通知される |
| `modelExecutionConfig(ExecutionConfig)` | `ExecutionConfig.MODEL_DEFAULTS` | モデル呼び出しのタイムアウト・試行回数・バックオフ |
| `generateOptions(GenerateOptions)` | プロバイダ既定 | temperature、top-p などのサンプリング設定 |
| `modelResolver(Function<String, Model>)` | `null` | モデル名文字列を `Model` に解決する。**サブエージェント用** |

`ExecutionConfig` はモデル呼び出しとツール呼び出しのタイムアウト・リトライを別々に制御します:

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
                                .maxAttempts(1)     // 副作用のあるツールはリトライしない
                                .build())
                .build();
```

## ツールと ReAct ループ

| メソッド | 既定値 | 役割 |
|----------|--------|------|
| `toolkit(Toolkit)` | 既定の toolkit | ツール・MCP クライアント・スキル・ツールグループを保持する `Toolkit` |
| `maxIters(int)` | `10` | 1 回の呼び出しでの推論/行動の最大反復数 |
| `toolExecutionConfig(ExecutionConfig)` | `ExecutionConfig.TOOL_DEFAULTS` | ツール呼び出しのタイムアウト・試行回数・バックオフ |
| `permissionContext(PermissionContextState)` | `DEFAULT` モード | allow / ask / deny ルール。[権限システム](/v2/ja/docs/building-blocks/permission-system)を参照 |
| `stopOnReject(boolean)` | `false` | ツール呼び出しが拒否されたら続行せずループを止める |
| `enableMetaTool(boolean)` | `false` | 自己管理ツールグループ用に `reset_tools` メタツールを登録 |
| `enableTaskList()` / `enableTaskList(boolean)` | 無効 | 組み込みの todo / タスクリストツールを登録 |
| `enablePendingToolRecovery(boolean)` | `false` | 新しいメッセージ到着時に孤立したツール呼び出しを回復 |
| `toolsConfig(ToolsConfig)` | `workspace/tools.json` を読む | MCP と許可リスト設定ファイルのプログラム的な上書き |
| `registerExternalSchemas(List<ToolSchema>)` | `List.of()` | スキーマのみの外部ツール。実行は中断され worker に委ねられる |
| `mcpServerRegistrationListener(…)` | `null` | MCP サーバー登録の終端結果を受け取る。サブエージェントには伝播しない |
| `webHttpClient(HttpClient)` | JDK 既定 | 組み込み `web_fetch` / `web_search` が使う `HttpClient` を差し替える |
| `artifactDeliveryTarget(ArtifactDeliveryTarget)` | `null` | `deliver_artifact` を登録し、生成物を引き渡せるようにする |
| `checkRunning(boolean)` | `true` | 同一セッションの同時呼び出しをキューに入れず拒否する |

<Note>

`webHttpClient` は主に HTTP/1.1 を強制するためにあります。既定クライアントは HTTP/2 をネゴシエートし失敗時に HTTP/1.1 へ自動フォールバックしますが、一部のサーバーはこのネゴシエーションに失敗します。HTTP/1.1 専用クライアントを差し込めば、ツールのコードを触らずに解決できます。

</Note>

## ワークスペースとコンテキスト

| メソッド | 既定値 | 役割 |
|----------|--------|------|
| `workspace(Path)` / `workspace(String)` | 解決順を参照 | `AGENTS.md`、`MEMORY.md`、`skills/`、`subagents/`、`tools.json` を置くルート |
| `additionalContextFile(String)` | なし | ワークスペース相対のファイルを全文システムプロンプトに埋め込む。複数回呼べる |
| `maxContextTokens(int)` | `8000` | `MEMORY.md` 注入の予算 |
| `useLegacyXmlWorkspaceContext(boolean)` | `false` | ワークスペースコンテキストを Markdown でなく旧 XML 形式で描画 |

`workspace(...)` を呼ばない場合、`build()` は builder の値 → システムプロパティ `agentscope.workspace` → 環境変数 `AGENTSCOPE_WORKSPACE` → `${user.dir}/.agentscope/workspace` の順で解決します。ディレクトリ構成と各ファイルの読み込みは[ワークスペース](/v2/ja/docs/harness/workspace)を参照してください。

## ファイルシステムとサンドボックス

| メソッド | 既定値 | 役割 |
|----------|--------|------|
| `filesystem(LocalFilesystemSpec)` | ローカル | ローカルディスク + シェル、パス許可リスト付き |
| `filesystem(RemoteFilesystemSpec)` | — | Redis / JDBC / OSS を裏に持つ共有ストア |
| `filesystem(SandboxFilesystemSpec)` | — | ファイルとコマンドを Docker / K8s サンドボックスに隔離 |
| `filesystemRoute(String, AbstractFilesystem)` | なし | 主ファイルシステムと並べて、パスプレフィックス配下に追加のファイルシステムをマウント |
| `abstractFilesystem(AbstractFilesystem)` | — | 脱出ハッチ: 独自実装を直接渡す |
| `distributedStore(DistributedStore)` | `null` | 状態ストア・リモートストア・スナップショット仕様を一括で提供 |

[ファイルシステム](/v2/ja/docs/harness/filesystem)と[サンドボックス](/v2/ja/docs/harness/sandbox)を参照。

## 状態・メモリ・履歴

| メソッド | 既定値 | 役割 |
|----------|--------|------|
| `stateStore(AgentStateStore)` | `~/.agentscope/state/<agentId>/` の `JsonFileAgentStateStore` | `(userId, sessionId)` ごとに `AgentState` を永続化する先 |
| `defaultSessionId(String)` | エージェントの `name` | 呼び出しの `RuntimeContext` に session id が無いときの既定値 |
| `memory(MemoryConfig)` | `MemoryConfig.defaults()` | 長期メモリのプロンプトとトリガーポリシー |
| `compaction(CompactionConfig)` | 既定設定 | 会話履歴をいつどう圧縮するか |
| `toolResultEviction(ToolResultEvictionConfig)` | 既定値 | 巨大なツール結果をディスクへ退避しプレースホルダを残す |
| `transcriptStore(TranscriptStore)` | 既定 | セッショントランスクリプトの分割追記ストアを上書き |
| `transcriptTenant(String)` | `"default"` | トランスクリプトのオブジェクトキーに使うテナントセグメント |

[メモリ](/v2/ja/docs/harness/memory)と[コンパクション](/v2/ja/docs/harness/compaction)を参照。

## サブエージェント

| メソッド | 既定値 | 役割 |
|----------|--------|------|
| `subagent(SubagentDeclaration)` | なし | サブエージェントをコードで 1 つ宣言 |
| `subagents(List<SubagentDeclaration>)` | なし | まとめて宣言 |
| `subagentFactory(String, Function<String, Agent>)` | なし | 指定した agent id の構築を完全に自前で行う |
| `subagentFactory(String, String, Function<String, Agent>)` | なし | 同上 + オーケストレータに見せる説明を付与 |
| `taskRepository(TaskRepository)` | 既定 | バックグラウンドのサブエージェントタスク記録の保存先 |
| `externalSubagentTool(Object)` | `null` | 外部サブエージェントツール(通常は `SessionsTool`)を注入 |

サブエージェントはコードを変えずに `workspace/subagents/` のファイルとして宣言することもできます。[サブエージェント](/v2/ja/docs/harness/subagent)を参照。

## スキル

| メソッド | 既定値 | 役割 |
|----------|--------|------|
| `skillRepository(AgentSkillRepository)` | なし | スキルの供給元を 1 つ追加(Git、Nacos、MySQL、classpath) |
| `skillRepositories(List<…>)` | なし | 供給元をまとめて追加 |
| `projectGlobalSkillsDir(Path)` | `null` | プロジェクト全体のスキルディレクトリ。マーケットプレイスとワークスペースより低い優先度 |
| `enableSkills(String...)` | すべて | 許可リスト: これらのスキルだけを公開 |
| `disableSkills(String...)` | なし | 拒否リスト: これら以外をすべて公開 |
| `skillsEnabled(boolean)` | `true` | スキル全体を一括で有効・無効化 |
| `skillFilter(SkillFilter)` | `null` | どのスキルを見せるかを完全に制御 |
| `enableSkillManageTool(…)` | 無効 | エージェント自身にワークスペーススキルの作成・編集・アーカイブを許可 |
| `enableSkillPromotionGate(…)` | 無効 | エージェント作成スキルの昇格ゲートと可視性フィルタ連鎖 |
| `enableSkillCurator(SkillCuratorConfig)` | 無効 | バックグラウンドのスキルキュレーター。`enableSkillManageTool` が前提 |

`enableSkills` と `disableSkills` はどちらも `skillFilter` を書き換えるショートハンドなので、後に呼んだ方が勝ちます:

```java
// リポジトリが何を提供していても、この 2 つのスキルだけを公開する。
HarnessAgent.builder()
        .name("agent")
        .sysPrompt("…")
        .model("dashscope:qwen-plus")
        .enableSkills("code-review", "changelog")
        .build();
```

[スキル](/v2/ja/docs/harness/skill)を参照。

## Plan Mode

| メソッド | 既定値 | 役割 |
|----------|--------|------|
| `enablePlanMode()` / `enablePlanMode(boolean)` | 無効 | 人間の承認で抜ける、読み取り専用の「まず考える」フェーズ |
| `planFileDirectory(String)` | `plans/` | プランファイルの書き出し先 |
| `allowShellInPlanMode()` / `allowShellInPlanMode(boolean)` | `false` | Plan Mode 中もシェルツールを許可 |

Plan Mode は既定で厳密に読み取り専用で、シェルは拒否されます。シェルは用途が両義的で、名前から読み取り専用と判定できないためです。シェルでの調査が必要なときだけ明示的に有効化してください。[Plan Mode](/v2/ja/docs/harness/plan-mode)を参照。

## ミドルウェアと hook

| メソッド | 既定値 | 役割 |
|----------|--------|------|
| `middleware(MiddlewareBase)` | なし | ミドルウェアを 1 つ追加。自分のものは Harness 組み込みより**先**に走る |
| `middlewares(List<? extends MiddlewareBase>)` | なし | まとめて追加 |
| `hook(Hook)` / `hooks(List<Hook>)` | なし | より低レベルのライフサイクル hook |
| `enableAgentTracingLog(boolean)` | `true` | `AgentTraceMiddleware` による実行トレースログ |

[ミドルウェア](/v2/ja/docs/building-blocks/middleware)を参照。

## Teams・メッセージバス・非同期ツール

| メソッド | 既定値 | 役割 |
|----------|--------|------|
| `teamsMode(TeamClient, TeamContext)` | 無効 | AgentTeams モード: `TeamsMiddleware` とロール制限された `team` ツールを登録 |
| `teamsMode(TeamClient, TeamContext, String)` | 無効 | 同上 + session id に束縛し、コントロールプレーンの team イベントを届ける |
| `messageBus(MessageBus)` | `null` | インボックス経由の配送。各推論ステップ前にインボックスを排出する `InboxMiddleware` を自動登録 |
| `asyncToolTimeout(Duration)` | `null` | タイムアウトを超えたツールをバックグラウンドへ退避。**`messageBus` が前提** |
| `asyncToolRegistry(AsyncToolRegistry)` | `null` | 非同期ツールの実行を追跡し、滞留したものを検出・掃除できるようにする |

## 組み込み機能を切る

Harness の各機能は、安全である限り既定で有効です。以下のスイッチはそれらを外します。デバッグ、ツール面の絞り込み、あるいはその機能が不要な環境への組み込みに使えます:

| メソッド | 無効になるもの |
|----------|----------------|
| `disableWorkspaceContext()` | `AGENTS.md` / `MEMORY.md` / `knowledge/` のシステムプロンプト注入 |
| `disableSessionPersistence()` | `AgentState` の自動永続化 |
| `disableCompaction()` | 会話コンパクション全体 |
| `disableToolResultEviction()` | 巨大なツール結果の退避 |
| `disableTranscript()` | 独立したセッショントランスクリプトミドルウェア |
| `disableMemoryHooks()` | メモリのフラッシュとバックグラウンド保守 |
| `disableMemoryTools()` | `memory_search` / `memory_get` / `memory_save` / `session_search` |
| `disableFilesystemTools()` | 組み込みファイルシステムツール |
| `disableShellTool()` | 組み込みシェルツール |
| `disableWebTools()` | Tavily ベースの `web_search` / `web_fetch` |
| `disableSubagents()` | サブエージェントのサブシステム全体 |
| `disableDynamicSubagents()` | 実行時のサブエージェント生成。宣言済みのものは残る |
| `disableDynamicSkills()` | ターンごとのスキル再マージ。ビルド時の一度きりのマージに退化 |
| `disableDefaultWorkspaceSkills()` | 既定の名前空間付きワークスペーススキルリポジトリ |
| `disableToolsConfig()` | `workspace/tools.json` の読み込み |
| `disableAtPathExpansion()` | ユーザーメッセージ中の `@path` 参照を添付ファイルブロックに展開する処理 |

ツール面を自前のツールだけに絞った最小エージェント:

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

## コード外からの設定

2 つの設定は環境から与えられるので、同じイメージを複数のデプロイで使えます:

| 設定 | システムプロパティ | 環境変数 |
|------|--------------------|----------|
| ワークスペースのルート | `agentscope.workspace` | `AGENTSCOPE_WORKSPACE` |
| モデルの API キー | — | `DASHSCOPE_API_KEY`、`OPENAI_API_KEY`、`ANTHROPIC_API_KEY`、`DEEPSEEK_API_KEY`、`GEMINI_API_KEY` |

```dockerfile
ENV AGENTSCOPE_WORKSPACE=/data/agent-workspace
ENV DASHSCOPE_API_KEY=sk-...
```

それ以外はすべてコード側で builder に設定します。ワークスペースのプロパティや変数が空白値の場合は未設定とみなされ、次の供給元へフォールバックします。

## 関連ページ

- [Harness アーキテクチャ](/v2/ja/docs/harness/architecture) — これらの機能がどう組み合わさるか
- [ワークスペース](/v2/ja/docs/harness/workspace) — 各オプションが読むディレクトリ構成
- [ReActAgent の builder フィールド](/v2/ja/docs/building-blocks/agent#builder-fields) — コアの builder
- [権限システム](/v2/ja/docs/building-blocks/permission-system)
- [ミドルウェア](/v2/ja/docs/building-blocks/middleware)
