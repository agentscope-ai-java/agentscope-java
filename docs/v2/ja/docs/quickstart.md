---
title: クイックスタート
description: AgentScope Java 2.0 を始める — HarnessAgent で最初の長時間稼働エージェントを立ち上げる
---

## インストール

AgentScope Java は JDK 17 以降を必要とします。Maven 3.9+ を推奨します。

### Maven の依存関係

`HarnessAgent` は推奨のエントリポイントです — ワークスペース、長期記憶、セッション永続化、サブエージェント、サンドボックスなどのエンジニアリング機能を1つのビルダーにまとめています。`agentscope-harness` に依存すると、`agentscope-core` が推移的に取り込まれます。

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-harness</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

<Note>

`${agentscope.version}` は最新バージョンに置き換えてください。最新バージョンと完全なリリース詳細については [リリースノート](/v2/ja/docs/others/release-notes) を参照してください。

</Note>

素の `ReActAgent` の API だけが必要な場合(ワークスペース / 永続化 / サブエージェント / サンドボックスなし)は、エージェントフレームワーク自体には `agentscope-core` だけで十分です。具体的なモデルプロバイダーは別モジュールです。プロバイダー固有のチャットモデルとフォーマッターは、独立した `agentscope-extensions-model-*` モジュールに存在します。`ReActAgent` と `HarnessAgent` の違いは [Harness アーキテクチャ](/v2/ja/docs/harness/architecture) で説明しています。

以下のクイックスタートでは `.model("dashscope:qwen-plus")` を介して DashScope を使用するため、対応するモデル拡張も追加してください。

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-dashscope</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

MCP との統合には公式の MCP SDK が必要です — 動作する例は `agentscope-examples/documentation/pom.xml` を参照してください。

## 最初のエージェント

以下の例では `HarnessAgent` を使って3つのことを同時に示します: **ワークスペース駆動のペルソナ**(`AGENTS.md`)、**自動セッション永続化**(同じ `sessionId` を使った2ターン目が1ターン目を記憶している)、**会話のコンパクション**(閾値超過時のコンパクション + 長期的な事実が `MEMORY.md` に蒸留される)。モデル ID は文字列として `.model(...)` に渡されます — `ModelRegistry` がそれを解決し、対応する API キーの環境変数を自動的に読み取ります。

```java
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import java.nio.file.Paths;

public class FirstAgent {
    public static void main(String[] args) {
        HarnessAgent agent = HarnessAgent.builder()
                .name("note-taker")
                .sysPrompt("You are a note-taking assistant.")
                // 文字列形式は ModelRegistry によって解決され、環境変数から DASHSCOPE_API_KEY を
                // 自動的に取得します。プロバイダーを切り替えるには "openai:gpt-5.5"、
                // "anthropic:claude-sonnet-4-5"、"gemini:gemini-2.0-flash"、"ollama:llama3" を使用してください。
                .model("dashscope:qwen-plus")
                .workspace(Paths.get(".agentscope/workspace"))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .build();

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("demo-session")
                .userId("alice")
                .build();

        // ターン1: 自己紹介 + 今日のタスクを伝える
        agent.call(new UserMessage("私の名前はアリスです。今日は ReAct についての技術トークを準備しています。"), ctx).block();

        // ターン2: 同じ sessionId — ターン1の状態が自動的に復元される
        agent.call(new UserMessage("私の名前は何ですか? 今日は何をしていますか?"), ctx).block();
    }
}
```

この実行の後、2つのディレクトリツリーができます — **ワークスペース** と **状態ストア** です。

```
.agentscope/workspace/                          ← workspace (agent content)
├── AGENTS.md                                   ← write one to give the agent its persona (optional)
└── agents/note-taker/
    └── sessions/                               ← never-compacted raw conversation log

~/.agentscope/state/note-taker/                 ← state store (outside workspace)
└── alice/demo-session/                         ← AgentState auto-saved / auto-loaded
    └── agent_state.json
```

`AgentState` はデフォルトで **ワークスペースの外側** の `~/.agentscope/state/<agentId>/` に存在します — 状態はワークスペース自体を復元するための前提条件(例えばサンドボックスがワイプされた後など)であるため、ワークスペースのデータと絡み合ってはならないからです。同じ `sessionId` でプロセスを再起動しても、2ターン目は1ターン目を記憶し続けます。

<Warning>

デフォルトの `JsonFileAgentStateStore` は、開発およびシングルノードデプロイに適したローカルファイルバックエンドです。本番クラスタでは、`RedisAgentStateStore`(`agentscope-extensions-redis` が提供)のような分散実装を使うか、独自の `AgentStateStore` を実装してください。[本番投入](/v2/ja/docs/others/going-to-production) を参照してください。

</Warning>

十分なターン数でコンパクションが発動すると、蒸留された事実はまず `workspace/memory/YYYY-MM-DD.md` に落とされ、その後スロットル制御されたバックグラウンドジョブがそれらを `MEMORY.md` にマージし、次の推論ステップでシステムプロンプトに注入されます。

### 推論とツール呼び出しのストリーミング

`call(...)` を `streamEvents(...)` に置き換えると、テキスト差分やツール呼び出しなどの段階的なイベントを受け取れます — Web / TUI のレンダリングに適しています。

```java
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;

agent.streamEvents(new UserMessage("今日のまとめを3つの箇条書きで示してください。"))
        .doOnNext(event -> {
            if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA) {
                // ストリーミングされるテキストの断片 — UI や標準出力に追記する
                System.out.print(((TextBlockDeltaEvent) event).getDelta());
            } else if (event.getType() == AgentEventType.TOOL_CALL_START) {
                // エージェントがツールを呼び出そうとしている — 呼び出し情報を表示する
                System.out.println("\n[tool] " + ((ToolCallStartEvent) event).getToolCallName());
            }
            // その他のイベント: thinking ブロック、ツール結果、reply end など
        })
        .blockLast();
```

<Tip>

実行前に環境変数 `DASHSCOPE_API_KEY` を設定してください。プロバイダーを切り替えるには、対応する `agentscope-extensions-model-*` モジュールを追加し、`.model(...)` に渡す文字列を変更し、対応する API キー(`OPENAI_API_KEY`、`ANTHROPIC_API_KEY`、`GEMINI_API_KEY`)をエクスポートしてください。タイムアウトやカスタムエンドポイントを明示的に制御したい場合は、`DashScopeChatModel.builder()...build()` のようなプロバイダービルダーでモデルを構築し、それを `.model(Model)` に渡してください。

</Tip>

### マルチユーザーの並行処理

エージェントは**呼び出しの間はステートレス**です — 単一のインスタンスで異なるユーザーやセッションからのリクエストを処理できます。`RuntimeContext` 経由で `userId` / `sessionId` を渡すと、エージェントは対応する会話状態を自動的に読み込み、分離します。

```java
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import java.nio.file.Path;
import java.nio.file.Paths;

// 起動時にエージェントインスタンスを1つ作成する(シングルトンで問題ない)
HarnessAgent agent = HarnessAgent.builder()
        .name("note-taker")
        .sysPrompt("You are a note-taking assistant.")
        .model("dashscope:qwen-plus")
        .workspace(Paths.get(".agentscope/workspace"))
        .compaction(CompactionConfig.builder()
                .triggerMessages(30)
                .keepMessages(10)
                .build())
        .build();

// HTTP ハンドラー内 — リクエストごとに異なる RuntimeContext を渡す
agent.call(new UserMessage(userInput), RuntimeContext.builder()
        .sessionId(sessionId)
        .userId(userId)
        .build()).block();
```

同じ `(userId, sessionId)` を対象とする呼び出しは自動的にシリアライズされます(1つのセッションへの並行書き込みはありません)。異なるセッションへの呼び出しは並行して実行されます。完全な本番パターン(Redis セッション、サンドボックス、スキルリポジトリ)については、[本番投入](/v2/ja/docs/others/going-to-production) を参照してください。

## 次のステップ

- [Agent](/v2/ja/docs/building-blocks/agent) — 完全な `ReActAgent` API、ビルダーのフィールド、`call` / `streamEvents` / `observe`、human-in-the-loop、`AgentStateStore` の設定
- [Harness アーキテクチャ](/v2/ja/docs/harness/architecture) — `HarnessAgent` の各能力がどのように連携し、状態がどのように流れるか
- [Workspace](/v2/ja/docs/harness/workspace) — `AGENTS.md` / `MEMORY.md` / `skills/` / `subagents/` / `tools.json` のディレクトリレイアウトと読み込みモデル
- [Filesystem](/v2/ja/docs/harness/filesystem) — ローカル + シェル / 共有ストア / サンドボックスの各デプロイモード
