---
title: "メッセージとイベント"
description: "エージェント間通信とストリーミングのための中核データ抽象化"
---

メッセージとイベントは、AgentScope における2つの基本的なデータ構造です。

- **メッセージ** —— エージェント間通信と永続化のプリミティブです。各 `Msg` は完全な1つの会話ターンであり、コンテキストに保存され、エージェント間で受け渡されます。
- **イベント** —— フロントエンドとのやり取りとストリーミングのプリミティブです。イベントは増分的な進捗更新(テキストトークン、ツール呼び出しの断片、パーミッションリクエストなど)を運び、リアルタイム UI と Human-in-the-Loop フローを駆動します。

単一の `call` から発行されるイベント列は、常にちょうど1つのアシスタント `Msg` へと凝縮されるため、完全なメッセージ状態はイベントストリームだけから再構築できることが保証されています。

## メッセージ

`Msg`(`io.agentscope.core.message`)は、会話の1ターン——ユーザー入力、エージェントの返信、またはシステム指示——を表し、その内容は型付き `ContentBlock` の順序付きリストとしてモデル化されます。

:::{tip}
1つのアシスタント `Msg` は、1回の完全な `call` サイクル(最終的な返信に至るまでの複数回の推論 + 行動のイテレーション)に対応します。
:::

### 構造

`Msg` の中核となるフィールド(ゲッター経由):

| メソッド | 型 | 説明 |
|--------|------|-------------|
| `getId()` | `String` | 一意なメッセージ識別子 |
| `getName()` | `String` | 送信者名(null 可) |
| `getRole()` | `MsgRole` | `USER` / `ASSISTANT` / `SYSTEM` / `TOOL` |
| `getContent()` | `List<ContentBlock>` | 順序付きのコンテンツブロックのリスト(不変) |
| `getMetadata()` | `Map<String, Object>` | 任意のキー/値のメタデータ |
| `getTimestamp()` | `String` | 作成時刻(`yyyy-MM-dd HH:mm:ss.SSS`) |
| `getUsage()` | `ChatUsage` | トークン使用量(アシスタントメッセージのみ) |
| `getGenerateReason()` | `GenerateReason` | 終了理由: `MODEL_STOP` / `TOOL_SUSPENDED` / `REASONING_STOP_REQUESTED` / `ACTING_STOP_REQUESTED` / `ALL_TOOLS_DENIED` / `INTERRUPTED` / `MAX_ITERATIONS` |

### コンテンツブロック

メッセージの内容は型付きブロックで構成され、それぞれが1種類の情報を表します。ブロッククラスは `io.agentscope.core.message` にあります。

| ブロック | 説明 | 使用可能なロール |
|-------|-------------|-----------|
| `TextBlock` | プレーンテキストのコンテンツ | USER、ASSISTANT、SYSTEM |
| `DataBlock` | base64 または URL によるバイナリデータ(画像 / 音声 / 動画)——レガシーの ImageBlock / AudioBlock / VideoBlock を統一したもの | USER、ASSISTANT |
| `ImageBlock` / `AudioBlock` / `VideoBlock` | レガシーな具体的なメディアブロック(引き続きサポートされるが、新しいコードでは `DataBlock` を推奨) | USER |
| `ThinkingBlock` | モデルの推論 / 思考の連鎖 | ASSISTANT |
| `ToolUseBlock` | ツール呼び出し: `id` / `name` / `input` / `state`(`ToolCallState`) | ASSISTANT |
| `ToolResultBlock` | `state`(`ToolResultState`)を持つツール結果 | ASSISTANT |
| `HintBlock` | ユーザーコンテキストとしてループに注入される指示 | ASSISTANT |

:::{note}
ロールに関する制約は生成時に強制されます: `USER` はテキスト/データ/画像/音声/動画ブロックのみを許可し、`SYSTEM` は `TextBlock` のみを許可し、`ASSISTANT` はすべてのブロック種別を許可します。
:::

### メッセージを作成する

ロール固定のサブクラス(`io.agentscope.core.message.UserMessage` / `AssistantMessage` / `SystemMessage` / `ToolResultMessage`)は、便利なコンストラクタを提供します。`content` が単純な文字列の場合、自動的に `TextBlock` にラップされます。

```java
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.SystemMessage;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;

// ユーザーメッセージ —— テキストのみ
UserMessage userText = new UserMessage("user", "この画像には何が写っていますか?");

// マルチモーダルなユーザーメッセージ
UserMessage userMulti =
        new UserMessage(
                "user",
                TextBlock.builder().text("この画像を説明してください:").build(),
                DataBlock.builder()
                        .source(Base64Source.builder()
                                .data("...")
                                .mediaType("image/png")
                                .build())
                        .build());

// システムメッセージ —— テキストのみ
SystemMessage systemMsg = new SystemMessage("system", "あなたは親切なアシスタントです。");

// アシスタントメッセージ —— すべてのブロック種別を許可
AssistantMessage assistantMsg = new AssistantMessage("agent", "こちらが結果です...");
```

その他の任意フィールド(`metadata`、`timestamp`、`usage`、`generateReason`)を使うには、各サブクラスの `builder()` を使います。

```java
UserMessage msg =
        UserMessage.builder()
                .name("user")
                .textContent("Hello")
                .build();
```

### コンテンツへアクセスする

`Msg` は、特定のブロック種別を取り出すためのヘルパーを提供します。

| メソッド | 戻り値 |
|--------|---------|
| `getTextContent()` | すべての `TextBlock` を `\n` で連結したもの。存在しなければ空文字列 |
| `getContentBlocks(Class<T>)` | 指定した型でフィルタしたリスト |
| `getFirstContentBlock(Class<T>)` | 最初に一致するブロック、なければ null |
| `hasContentBlocks(Class<T>)` | 指定した型のブロックが存在すれば `true` |

```java
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.ToolResultBlock;

// すべてのテキストコンテンツ
String text = msg.getTextContent();

// すべてのツール呼び出し
List<ToolUseBlock> toolCalls = msg.getContentBlocks(ToolUseBlock.class);

// ツール結果が存在するかどうか
if (msg.hasContentBlocks(ToolResultBlock.class)) {
    // ...
}
```

## イベント

イベントはメッセージのストリーミング版に相当します。エージェントが実行されている間、`AgentEvent`(`io.agentscope.core.event`)の連続を発行し、増分的な進捗——到着するテキストトークン、組み立てられていくツール呼び出し、ストリーミングで返ってくる結果——を表します。各イベントは軽量で自己完結しています。

### イベントのライフサイクル

すべてのイベントは `getReplyId()` を運び、それを組み立て中のメッセージに結び付けます。1回の返信の中では、`getBlockId()` または `getToolCallId()` が、同じコンテンツブロックのライフサイクルに属するイベントの相関キーとして機能します。イベントは **start → delta → end** というパターンに従います。

```{mermaid}
sequenceDiagram
    participant Client
    participant Agent

    Agent->>Client: AgentStartEvent

    rect rgba(100, 150, 255, 0.1)
        Note over Client,Agent: 推論フェーズ
        Agent->>Client: ModelCallStartEvent
        rect rgba(200, 200, 100, 0.1)
            Note over Client,Agent: TextBlock (blockId)
            Agent->>Client: TextBlockStartEvent
            Agent->>Client: TextBlockDeltaEvent (×N)
            Agent->>Client: TextBlockEndEvent
        end
        rect rgba(200, 200, 100, 0.1)
            Note over Client,Agent: DataBlock (blockId)
            Agent->>Client: DataBlockStartEvent
            Agent->>Client: DataBlockDeltaEvent (×N)
            Agent->>Client: DataBlockEndEvent
        end
        rect rgba(200, 200, 100, 0.1)
            Note over Client,Agent: ToolUseBlock (toolCallId)
            Agent->>Client: ToolCallStartEvent
            Agent->>Client: ToolCallDeltaEvent (×N)
            Agent->>Client: ToolCallEndEvent
        end
        Agent->>Client: ModelCallEndEvent
    end

    rect rgba(100, 255, 150, 0.1)
        Note over Client,Agent: 実行フェーズ
        rect rgba(200, 200, 100, 0.1)
            Note over Client,Agent: ToolResultBlock (toolCallId)
            Agent->>Client: ToolResultStartEvent
            Agent->>Client: ToolResultTextDeltaEvent (×N)
            Agent->>Client: ToolResultDataDeltaEvent (×N)
            Agent->>Client: ToolResultEndEvent
        end
    end

    Agent->>Client: AgentEndEvent
```

1回の返信に属するすべてのイベントは、同じ `replyId` を共有します。1回の返信の中では、`blockId` がテキスト/思考/データブロックのイベントを結び付け、`toolCallId` がツール呼び出しとツール結果を結び付けます。`blockId` はその `replyId` にスコープされたものであり、グローバルに一意な生成 ID である必要はありません。あるブロック種別が1回の返信の中で最大1つのライフサイクルしか持ち得ない場合、実装はテキストブロック用の固定キーのような安定した種別キーを使ってもかまいません。

### イベントの種類

すべてのイベントは `AgentEvent`(`io.agentscope.core.event`)を継承しており、次の共通メソッドを公開します。

| メソッド | 型 | 説明 |
|--------|------|-------------|
| `getId()` | `String` | 一意なイベント識別子 |
| `getCreatedAt()` | `String` | ISO 8601 形式のタイムスタンプ |
| `getType()` | `AgentEventType` | イベント種別の enum |
| `getSource()` | `String` | イベントの発生元エージェントを識別するソースパス。トップレベルのエージェントイベントでは `null`。サブエージェントから転送されたイベントではスラッシュ区切りのパス(例: `"main/researcher"`) |
| `getMetadata()` | `Map<String, Object>` | 任意のキー/値の入れ物。リモートサブエージェントの転送では、イベントがタスクに紐づく場合、`taskId`(`AgentEvent.METADATA_TASK_ID`)を harness / Agent Protocol のタスク ID に、`parentSessionId`(`AgentEvent.METADATA_PARENT_SESSION_ID`)を親セッションに設定する |

イベントは以下でグループ化されています。特に断りがない限り、すべてのイベントは組み立て中のメッセージに紐づける `getReplyId()` も運びます。

  :::{dropdown} ライフサイクルイベント
**AgentStartEvent** —— エージェントが新しい返信を開始する。

    | メソッド | 型 | 説明 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 返信メッセージの ID |
    | `getSessionId()` | `String` | セッション ID |
    | `getName()` | `String` | エージェント名 |
    | `getRole()` | `String` | エージェントのロール(デフォルトは `"assistant"`) |

    **AgentEndEvent** —— エージェントが返信を完了する。

    | メソッド | 型 | 説明 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 返信メッセージの ID |

    **ExceedMaxItersEvent** —— エージェントが最大の推論・行動イテレーション数の上限に達した。

    | メソッド | 型 | 説明 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 返信メッセージの ID |

    **RequestStopEvent** —— middleware またはツールによって発行される早期停止リクエスト。
:::

  :::{dropdown} テキストストリーミングイベント
**TextBlockStartEvent** —— 新しいテキストブロックが開始する。

    | メソッド | 型 | 説明 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 返信メッセージの ID |
    | `getBlockId()` | `String` | 現在の返信内でのテキストブロックの相関キー |

    **TextBlockDeltaEvent** —— 増分的なテキストコンテンツが到着する。

    | メソッド | 型 | 説明 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 返信メッセージの ID |
    | `getBlockId()` | `String` | 現在の返信内でのテキストブロックの相関キー |
    | `getDelta()` | `String` | 増分的なテキストコンテンツ |

    **TextBlockEndEvent** —— テキストブロックが完了する。

    | メソッド | 型 | 説明 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 返信メッセージの ID |
    | `getBlockId()` | `String` | 現在の返信内でのテキストブロックの相関キー |
:::

  :::{dropdown} 思考ストリーミングイベント
**ThinkingBlockStartEvent / ThinkingBlockDeltaEvent / ThinkingBlockEndEvent** —— テキストストリーミングイベントと同じ形をしており、モデルの思考の連鎖に特化しています。その `blockId` も、返信にスコープされた相関キーとして同じ意味を持ちます。
:::

  :::{dropdown} データストリーミングイベント
**DataBlockStartEvent / DataBlockDeltaEvent / DataBlockEndEvent** —— テキストストリーミングイベントと同じ形をしており、画像 / 音声 / 動画のバイナリデータを運びます。

    - `DataBlockStartEvent`: `getMediaType()` が MIME タイプ(例: `"image/png"`)を返す。
    - `DataBlockDeltaEvent`: `getData()` が増分的な base64 エンコードデータを返す。
:::

  :::{dropdown} ツール呼び出しストリーミングイベント
**ToolCallStartEvent** —— エージェントがツール呼び出しを開始する。

    | メソッド | 型 | 説明 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 返信メッセージの ID |
    | `getToolCallId()` | `String` | 一意なツール呼び出し ID |
    | `getToolCallName()` | `String` | 呼び出されているツール |

    **ToolCallDeltaEvent** —— 増分的なツール呼び出し引数が到着する。`getDelta()` は JSON の断片を返す。

    **ToolCallEndEvent** —— ツール呼び出しの引数が完了する。
:::

  :::{dropdown} ツール結果ストリーミングイベント
**ToolResultStartEvent** —— ツールが実行を開始する(`toolCallId`、`toolCallName` を運ぶ)。

    **ToolResultTextDeltaEvent** —— ツールからの増分的なテキスト出力。`getDelta()` はテキストの断片を返す。

    **ToolResultDataDeltaEvent** —— ツールからの増分的なバイナリ出力。`mediaType` / `data` / `url` を持つ点で `DataBlockDeltaEvent` に似ている。

    **ToolResultEndEvent** —— ツールが完了する。

    | メソッド | 型 | 説明 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 返信メッセージの ID |
    | `getToolCallId()` | `String` | 対応するツール呼び出し ID |
    | `getState()` | `ToolResultState` | 最終状態: `SUCCESS`、`ERROR`、`INTERRUPTED`、`DENIED`、`RUNNING` |
:::

  :::{dropdown} モデル呼び出しイベント
**ModelCallStartEvent** —— モデル API 呼び出しが開始する(`modelName` を運ぶ)。

    **ModelCallEndEvent** —— モデル API 呼び出しが完了する(`inputTokens` / `outputTokens` を運ぶ)。
:::

  :::{dropdown} Human-in-the-Loop イベント
**RequireUserConfirmEvent** —— エージェントがユーザー確認のために一時停止する。

    | メソッド | 型 | 説明 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 返信メッセージの ID |
    | `getToolCalls()` | `List<ToolUseBlock>` | 確認を待っているツール呼び出し |

    **RequireExternalExecutionEvent** —— エージェントが外部実行のために一時停止する。

    | メソッド | 型 | 説明 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 返信メッセージの ID |
    | `getToolCalls()` | `List<ToolUseBlock>` | 外部実行を待っているツール呼び出し |

    **UserConfirmResultEvent** —— 後続の `call()` が、一時停止していたパーミッション HITL リクエストを再開したときに発行される。
    1つ以上の `ConfirmResult` を運び、その `replyId` は先に発行された `RequireUserConfirmEvent` と一致する。

    | メソッド | 型 | 説明 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 対応する `RequireUserConfirmEvent` の返信 ID |
    | `getConfirmResults()` | `List<ConfirmResult>` | この再開に対して受理された確認結果 |

    **ExternalExecutionResultEvent** —— 後続の `call()` が、一時停止していた外部実行リクエストを再開したときに発行される。
    1つ以上の `ToolResultBlock` を運び、その `replyId` は先に発行された `RequireExternalExecutionEvent` と一致する。

    | メソッド | 型 | 説明 |
    |--------|------|-------------|
    | `getReplyId()` | `String` | 対応する `RequireExternalExecutionEvent` の返信 ID |
    | `getToolResults()` | `List<ToolResultBlock>` | この再開に対して受理された外部実行結果 |

    **AllToolsDeniedEvent** —— 直近の推論ステップのすべてのツール呼び出しを、ユーザーが HITL 確認を通じて拒否した。このイベントは `onActing` middleware チェーンを通じて発行されるため、middleware は `RequestStopEvent` を発行してエージェントを停止させることができる。どの middleware も処理しない場合、エージェントは次の推論イテレーションへ進む(後方互換)。

    | メソッド | 型 | 説明 |
    |--------|------|-------------|
    | `getDeniedToolCalls()` | `List<ToolUseBlock>` | 拒否されたツール呼び出し |
:::

  :::{dropdown} サブエージェントイベント
**SubagentExposedEvent** —— `agent_spawn(expose_to_user=true)` によって生成されたサブエージェントが、ユーザーがアドレス可能なエントリポイントとして公開された。SSE / ストリーミングのコンシューマーは、これを使って UI に新しい会話エントリを描画できる。

| メソッド | 型 | 説明 |
|--------|------|-------------|
| `getSubagentId()` | `String` | サブエージェントの一意な識別子 |
| `getAgentId()` | `String` | サブエージェントのエージェント種別 ID |
| `getSessionId()` | `String` | サブエージェントのセッション ID |
| `getLabel()` | `String` | ユーザーに表示されるラベル(任意) |
:::

## イベントからメッセージを再構築する

イベントとメッセージは別々の世界ではなく、同じデータの2つの見方です。`streamEvents` からのイベントストリームは、`replyId` / `blockId` / `toolCallId` によって集約することで、完全な `AssistantMessage` を再構築でき、最終的なメッセージ状態がイベントだけから完全に復元可能であることを保証します。

ブロック ID でグループ化し、Reactor オペレータでコンテンツを蓄積する標準的なパターンについては、`agentscope-core` の `agent/StreamingHook.java` と `agentscope-examples/documentation/.../streaming/AgentEventStreamExample.java` を参照してください。

```java
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;

StringBuilder accumulated = new StringBuilder();

agent.streamEvents(userMsg)
        .doOnNext(event -> {
            if (event instanceof AgentStartEvent start) {
                System.out.println("[start replyId=" + start.getReplyId() + "]");
            } else if (event instanceof TextBlockDeltaEvent delta) {
                accumulated.append(delta.getDelta());
            } else if (event instanceof ToolCallStartEvent tc) {
                System.out.println("[tool] " + tc.getToolCallName());
            } else if (event instanceof ToolResultEndEvent end) {
                System.out.println("[tool result state=" + end.getState() + "]");
            } else if (event instanceof AgentEndEvent end) {
                System.out.println("\n[end] full text:\n" + accumulated);
            }
        })
        .blockLast();
```

:::{tip}
この分離により、デプロイの柔軟性が大きく高まります。バックエンドは SSE でイベントストリームをプッシュし、フロントエンドはクライアント側でメッセージを再構築します。接続が切れたとしても、任意のチェックポイントからイベントを再生すれば、メッセージ状態を正確に復元できます。
:::

### 例: ストリーミング UI

典型的なストリーミング UI のループです(Spring WebFlux の SSE 形式は `streaming/StreamingWebExample.java` にあります)。

```java
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.UserMessage;

agent.streamEvents(new UserMessage("user", "このバグの修正を手伝ってください"))
        .doOnNext(event -> {
            if (event instanceof AgentStartEvent start) {
                System.out.println("[start replyId=" + start.getReplyId() + "]");
            } else if (event instanceof TextBlockDeltaEvent delta) {
                System.out.print(delta.getDelta());
            } else if (event instanceof ToolCallStartEvent tc) {
                System.out.println("\n[calling " + tc.getToolCallName() + "...]");
            } else if (event instanceof ToolResultEndEvent end) {
                System.out.println("[tool finished: " + end.getState() + "]");
            } else if (event instanceof AgentEndEvent end) {
                System.out.println("\n[done]");
            }
        })
        .blockLast();
```

## さらに読む

::::{grid} 2

:::{grid-item-card} エージェント
:link: ./agent.html

エージェントが ReAct ループの中でどのようにイベントとメッセージを発行するか
:::
  :::{grid-item-card} コンテキスト
:link: context.html

メッセージがどのように保存・永続化されるか
:::

::::
