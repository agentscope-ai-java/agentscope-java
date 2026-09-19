---
title: Harness アーキテクチャ
description: HarnessAgent とは何か、その各機能がどう協調するか、call() の間に状態がどう流れるか
---

`HarnessAgent` は `ReActAgent` を薄くラップしたもので、長時間稼働するエージェントに必要なエンジニアリング機能――ワークスペース駆動のペルソナ、長期記憶、サブエージェントのオーケストレーション、サンドボックス分離、スキル合成、プランモード、チャネルルーティング――を1つのビルダーにまとめたものです。

素の `ReActAgent` は「1つのリクエスト → 推論 → ツール → 返信」だけを扱います。Harness はこれとは別の一連の問いに答えます。次のターンは前回の続きからどう再開するのか、コンテキストはどうやって有界に保たれるのか、ユーザーはどう分離されるのか、危険な操作はどうレビューされるのか、再利用可能な機能はどう積み上がっていくのか、といった問いです。

> インストール、依存関係、そして「最初の `HarnessAgent`」をエンドツーエンドで動かすウォークスルーは [Quickstart](/v2/ja/docs/quickstart) にあります。このページはアーキテクチャのみを扱います。

## HarnessAgent を組み立てる

`HarnessAgent`(`io.agentscope.harness.agent.HarnessAgent`)は、ユーザー向けの harness API です。
[`ReActAgent`](/v2/ja/docs/building-blocks/agent) をラップし、その上にワークスペース / ファイルシステム / サンドボックス / サブエージェント /
スキル / プランモード / MCP のオーケストレーションを追加します。こうしたプロダクション向けの機能が必要なときは常に
`HarnessAgent.builder()` を使ってください。ワークスペースも永続化もサブエージェントもない素の ReAct ループが欲しい場合は、
代わりに [`ReActAgent.builder()`](/v2/ja/docs/building-blocks/agent#エージェントの設定) を直接使います――
両方のビルダーはフィールドの大半を共有しているので、後から切り替えるのもほとんど機械的な作業です。

`HarnessAgent` は**呼び出し間でステートレス**であり、複数のユーザー/セッションを同時に処理するシングルトンとして安全に使えます――各
`call()` は `RuntimeContext` の `(userId, sessionId)` を使って状態を分離します。同じセッションへの呼び出しは自動的に直列化され、
異なるセッションは並列に実行されます。

`ReActAgent` と同様に、ビルダーの `.model(...)` は任意の [`ChatModelBase`](/v2/ja/docs/building-blocks/model)
サブクラス(`DashScopeChatModel`、`OpenAIChatModel`、`AnthropicChatModel` など)を受け付けます――あるいは、よくあるケース向けに
`ModelRegistry` の文字列 id を渡すこともできます。ツールは `ReActAgent` の場合とまったく同じく `Toolkit` に登録します。

```java
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.harness.agent.HarnessAgent;
import java.nio.file.Paths;

public class WeatherTools {
    @Tool(name = "get_weather", description = "Get the current weather for a city")
    public String getWeather(
            @ToolParam(name = "city", description = "City name, e.g. 'Tokyo'") String city) {
        return "Sunny, 24°C in " + city;
    }
}

// Any ChatModelBase subclass works here — swap in OpenAIChatModel, AnthropicChatModel, etc.
ChatModelBase model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .formatter(new DashScopeChatFormatter())
                .build();

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new WeatherTools());

HarnessAgent agent =
        HarnessAgent.builder()
                .name("weather-assistant")
                .sysPrompt("You are a helpful weather assistant.")
                .model(model)                          // HarnessAgent.Builder#model(Model)
                .toolkit(toolkit)                       // HarnessAgent.Builder#toolkit(Toolkit)
                .workspace(Paths.get(".agentscope/workspace"))
                .build();
```

`.model(...)` には `String` オーバーロード(`.model("dashscope:qwen-plus")`)もあり、`ModelRegistry` を通じて解決され、
対応する API キー環境変数を自動的に読み込みます――この形式をエンドツーエンドで見るには [Quickstart](/v2/ja/docs/quickstart) を、
すべての `ChatModelBase` プロバイダとそのビルダーオプションについては [Model](/v2/ja/docs/building-blocks/model) を参照してください。

## 中核となる動作原理

心に留めておくべきことは3つあります。

**1. 機能は推論ループに「組み込まれる」のではなく「積み重なる」。**
ワークスペースの注入、圧縮、サブエージェント、サンドボックス、プランモード――それぞれが ReAct ループの主要な瞬間にフックします。中核のアルゴリズムは変更されず、Harness は追加するだけです。

**2. 機能同士は互いに依存せず、3つのオブジェクトを共有する。**
それぞれの機能は1つの仕事だけをこなし、他の機能を意識しません。それらは以下を通じて協調します。

- **`RuntimeContext`** — この呼び出しで誰が話しているか:`sessionId`、`userId`、それに任意の追加情報。永続化されません。
- **ワークスペース** — 誰がどのファイルを読み書きするか。それらが物理的にどこに置かれるか(ローカルディスク、サンドボックス、KV ストア)は設定次第です。
- **`AgentStateStore`** — ランタイム状態が呼び出しをまたいでどう復元されるか。

**3. 組み込みの機能は固定順で実行され、あなたのミドルウェアが先に走る。**
Harness はビルド時に組み込みミドルウェアを固定の順序で配線します。`.middleware(...)` で追加したものは Harness の組み込み機能より**先に**実行されます。

## 中核コンポーネント

各機能は1つの問題に答えます。ビルダー上でオプトインしてください。

| 機能 | 何を解決するか | ビルダーのフック | 詳細 |
|---|---|---|---|
| ワークスペース駆動のペルソナ | ペルソナ、知識、サブエージェントの仕様、スキル、MCP 許可リストがすべてファイルとして存在する | `.workspace(path)` | [ワークスペース](/v2/ja/docs/harness/workspace) |
| 状態の永続化 | 同一の `(userId, sessionId)` がリクエスト、プロセス、レプリカをまたいで再開する | デフォルトで有効。`.stateStore(...)` で上書き | [Context & AgentState](/v2/ja/docs/building-blocks/context) |
| 二層構造の長期記憶 | 長い会話中の事実が `MEMORY.md` に沈殿していく | デフォルトで有効。`.memory(...)` でプロンプト/トリガーポリシーをカスタマイズ | [Memory](/v2/ja/docs/harness/memory) |
| 会話の圧縮 | 履歴を有界に保ち、実際のオーバーフロー時には強制リトライする | `.compaction(...)` | [Compaction](/v2/ja/docs/harness/compaction) |
| 大きなツール結果のオフロード | 8万文字超の結果をディスクへ退避しプレースホルダーに置き換える | `.toolResultEviction(...)` | [Compaction](/v2/ja/docs/harness/compaction) |
| サブエージェントのオーケストレーション | 子エージェントへ、同期またはバックグラウンドで委譲し、自動でプッシュバックする | `.subagent(...)` または `workspace/subagents/` に仕様を配置 | [Subagent](/v2/ja/docs/harness/subagent) |
| プラガブルなファイルシステム | ローカル+シェル / 共有ストア / サンドボックスをコード変更なしで切り替える | `.filesystem(...)` | [Filesystem](/v2/ja/docs/harness/filesystem) |
| サンドボックス分離 | ファイルとコマンドを分離し、呼び出しをまたいで復旧し、マルチレプリカに対応する | `.filesystem(new DockerFilesystemSpec()...)` | [Sandbox](/v2/ja/docs/harness/sandbox) |
| プランモード | HITL による退出を伴う読み取り専用の思考先行フェーズ | `.enablePlanMode()` | [Plan Mode](/v2/ja/docs/harness/plan-mode) |
| スキル合成 | Git / Nacos / MySQL / classpath / ワークスペースからのスキル | `.skillRepository(...)` | [Skill](/v2/ja/docs/harness/skill) |
| MCP 統合とツール許可リスト | 宣言的な MCP サーバーとツールごとの allow/deny | `workspace/tools.json` | [Workspace](/v2/ja/docs/harness/workspace) |
| チャネルルーティング | セッション管理、セッションごとの並行制御、マルチエージェントルーティング、ストリーミングイベント | `agent.channel(...)` / `GatewayBootstrap` | [Channel](/v2/ja/docs/harness/channel) |

## 状態はどう流れるか

3つの層が存在し、フレームワークはそれらの間でデータを自動的に移動させます。

- **呼び出し内の状態** — `AgentState`(会話コンテキスト、パーミッションルール、プランモードの状態、ツール状態)に加え `RuntimeContext`(`sessionId`、`userId`、サンドボックスハンドル、追加情報)。
- **呼び出しをまたぐ状態** — すべての `call()` の終わりに自動保存され、次の呼び出しで自動的にロードされます:設定済みの `AgentStateStore`(デフォルトは `~/.agentscope/state/<agentId>/`、`(userId, sessionId)` でアドレス指定)内の `AgentState` ランタイムスナップショット、`sessions/<sessionId>.log.jsonl` 配下の決して圧縮されない完全な会話ログ、サブタスクの記録、サンドボックスのメタデータ。
- **長期記憶** — セッションをまたいで蓄積されます:`memory/YYYY-MM-DD.md` は追記専用で、スロットル制御されたバックグラウンドジョブによって定期的に `MEMORY.md` にマージされます。`MEMORY.md` は推論の各ステップでシステムプロンプトに注入されます。

覚えておく価値のある3つの不変条件:

- システムプロンプトは推論の各ステップで再構築されるため、`AGENTS.md` や `MEMORY.md` への編集は即座に反映されます――再起動は不要です。
- 圧縮、記憶の蒸留、バックグラウンドのメンテナンスはスロットル制御されており、毎ターン実行されるわけではありません。
- `AgentState` は core の `ReActAgent` + `AgentStateStore` によって永続化されます。Harness はもはや独自の永続化フックを追加しません。

## 自分のミドルウェアを追加する

Harness の配線を迂回せずにカスタムの振る舞いを挿入するには:

- `.middleware(...)` を使います――あなたのミドルウェアはすべての Harness 組み込み機能より前に実行されます。
- 現在の呼び出しの identity(`userId` / `sessionId`)についてはエージェントから `RuntimeContext` を読み取ります。
- ワークスペース I/O については `harnessAgent.getWorkspaceManager()` を経由してください――サンドボックスやリモートストアモードの下でも正しくルーティングされます。`java.nio.Files` はホストディスクに書き込むため、ローカルモード以外では誤った場所に書き込まれてしまいます。

## 関連ページ

- [Workspace](/v2/ja/docs/harness/workspace) — ディレクトリレイアウト、システムプロンプトに注入される内容、`tools.json`
- [Context & AgentState](/v2/ja/docs/building-blocks/context) — `AgentState`、`RuntimeContext`、`AgentStateStore` の永続化、マルチユーザー分離
- [Memory](/v2/ja/docs/harness/memory) — 二層構造の記憶
- [Compaction](/v2/ja/docs/harness/compaction) — 要約圧縮、大きな結果のオフロード、オーバーフロー復旧
- [Filesystem](/v2/ja/docs/harness/filesystem) — ローカル+シェル / 共有ストア / サンドボックス
- [Sandbox](/v2/ja/docs/harness/sandbox) — 分離実行、呼び出しをまたぐ復旧、分散
- [Subagent](/v2/ja/docs/harness/subagent) — 宣言、同期/バックグラウンド、ストリーミング転送
- [Skill](/v2/ja/docs/harness/skill) — 四層合成、自己学習ループ
- [Plan Mode](/v2/ja/docs/harness/plan-mode) — 読み取り専用フェーズ + HITL による退出
- [Channel](/v2/ja/docs/harness/channel) — セッション管理、マルチエージェントルーティング、ストリーミング SSE
