---
title: "エージェント"
description: "AgentScope Java 2.0 でエージェントを定義・設定する方法を学びます"
---

## 概要

`Agent`(インターフェースは `io.agentscope.core.agent.Agent`、デフォルト実装は `ReActAgent`)は中核となる抽象化です——モデル、ツール、パーミッションシステム、Human-in-the-Loop、コンテキスト管理、Middleware、状態管理、イベントシステムを単一の統一されたインターフェースへと統合する、推論・行動ループエンジンです。

主な責務は次のとおりです。

- 入力メッセージまたはイベントを受け取り、タスクを完了させるためにツールをオーケストレーションする。
- コンテキストを管理する(会話履歴は `AgentState.getContext()` に保持され、`AgentStateStore` によって自動的に永続化できる)。
- カスタムロジックのために、主要なライフサイクルのポイントで middleware hook を提供する。
- 並行および逐次のツール実行を自動的に管理する。

### コアインターフェース

`Agent` インターフェースは、`CallableAgent`、`StreamableAgent`、`ObservableAgent` という3つの機能インターフェースを組み合わせたものです。最もよく使われるメソッドは次のとおりです。

| メソッド | 説明 |
|--------|-------------|
| `call(List<Msg>)` / `call(List<Msg>, RuntimeContext)` | 推論・行動ループを実行し、`Mono<Msg>` を返す |
| `streamEvents(List<Msg>)` / `streamEvents(Msg)` | 同じループを実行するが、`AgentEvent` を逐次発行する |
| `observe(Msg)` / `observe(List<Msg>)` | 推論をトリガーせずにメッセージをコンテキストへ追加する(`Mono<Void>` を返す) |

`ReActAgent` は、構造化出力向けのオーバーロード(`call(msgs, structuredOutputClass, runtimeContext)`)と、`RuntimeContext` による呼び出しごとの便利なメタデータ機能を追加します。

### メインループ

各 `call` は推論・行動ループを通じて実行されます。次の図はメインの制御フローを示しています。

```{mermaid}
flowchart TD
    A([入力: メッセージ / イベント]) --> B{外部イベントを\n待機中?}
    B -- はい --> C[イベントを適用\nツール状態を更新]
    B -- いいえ --> D[コンテキストに追加]
    C --> E
    D --> E

    E{次のアクションを決定} -- 終了 --> F([返す: 外部との\nやり取りを待機中])
    E -- 推論 --> G[必要に応じてコンテキストを圧縮]
    G --> H[LLM 呼び出し]
    H -- ツール呼び出しなし --> I([最終メッセージを返す])
    H -- ツール呼び出しあり --> Acting

    subgraph Acting [実行]
        direction TB
        J[ツール呼び出しをバッチ化\n直列 / 並行] --> L[ツール呼び出しを実行]
        L --> M{パーミッション\nチェック}
        M -- 許可 --> N[ツールを実行 → 結果]
        M -- 確認 / 外部 --> O([一時停止して\nRequireUserConfirmEvent を発行])
        M -- 拒否 --> P[LLM にエラーを返す]
    end

    N --> E
    P --> E
```

## エージェントの設定

`ReActAgent.builder()...build()` でエージェントを構築します。`.model(...)` には、`ModelRegistry` によって解決される文字列 ID(最も一般的な方法——環境変数を自動的に読み込む)、または明示的な `Model` インスタンス(タイムアウトやカスタムエンドポイントなどを明示的に制御したい場合)のいずれかを渡せます。

::::{tab-set}
:::{tab-item} 文字列モデル ID(推奨)
```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;

ReActAgent agent =
        ReActAgent.builder()
                .name("my_agent")
                .sysPrompt("あなたは親切なアシスタントです。")
                // ModelRegistry によって解決される。DASHSCOPE_API_KEY を自動的に読み込む。
                // プロバイダを切り替えるには "openai:gpt-5.5" / "anthropic:claude-sonnet-4-5"
                // / "deepseek:deepseek-v4-flash" / "gemini:gemini-2.0-flash" / "ollama:llama3" を使う。
                .model("dashscope:qwen-plus")
                .toolkit(new Toolkit())
                .build();
```
:::
:::{tab-item} 明示的な Model ビルダー
```java
import io.agentscope.core.ReActAgent;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;

ReActAgent agent =
        ReActAgent.builder()
                .name("my_agent")
                .sysPrompt("あなたは親切なアシスタントです。")
                .model(
                        DashScopeChatModel.builder()
                                .apiKey("YOUR_API_KEY")
                                .modelName("qwen-max")
                                .stream(true)
                                .formatter(new DashScopeChatFormatter())
                                .build())
                .toolkit(new Toolkit())
                .build();
```
:::
:::{tab-item} Toolkit / MCP を使う
```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.builtin.TodoTools;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new TodoTools());          // @Tool メソッドをリフレクションで登録する
toolkit.registerTool(new MyCustomTools());      // カスタムツールクラス

McpClientWrapper amap = McpClientBuilder.streamableHttp()
        .name("amap")
        .url("https://mcp.amap.com/mcp?key=" + System.getenv("AMAP_API_KEY"))
        .build();
toolkit.registerMcpClient(amap).block();

ReActAgent agent =
        ReActAgent.builder()
                .name("my_agent")
                .sysPrompt("あなたは親切なアシスタントです。")
                .model("dashscope:qwen-max")
                .toolkit(toolkit)
                .build();
```
:::
::::

:::{tip}
`ModelRegistry` の文字列形式(`<provider>:<model>`)を使うには、対応するモデル拡張モジュールがクラスパス上になければなりません。`dashscope` / `openai` / `deepseek` / `anthropic` / `gemini` / `ollama` に対応しており、対応する API キー(`DASHSCOPE_API_KEY` / `OPENAI_API_KEY` / `DEEPSEEK_API_KEY` / `ANTHROPIC_API_KEY` / `GEMINI_API_KEY`)を環境変数から読み込みます。長時間稼働するシナリオでワークスペース、セッション永続化、メモリの圧縮、サブエージェントなども必要な場合は [`HarnessAgent`](../harness/architecture.md) を使ってください——これは `ReActAgent` を薄くラップしたもので、ビルダーはほぼ同一です。`ChatModelBase` と `Toolkit` を `HarnessAgent.Builder` に組み込む実例については [HarnessAgent の構築](../harness/architecture.md#building-a-harnessagent) を参照してください。
:::

### ビルダーのフィールド

| フィールド | 型 | デフォルト | 説明 |
|-------|------|---------|-------------|
| `name` | `String` | 必須 | エージェント識別子。メッセージやログで使用される |
| `sysPrompt` | `String` | 必須 | ベースとなるシステムプロンプト |
| `model` | `Model` | 必須 | 推論を駆動する LLM(`ChatModelBase` を拡張) |
| `toolkit` | `Toolkit` | `new Toolkit()` | ツール、MCP クライアント、スキル、ツールグループを管理する |
| `middlewares` | `List<? extends MiddlewareBase>` | `List.of()` | agent / reasoning / acting / model call / system prompt の各 hook に適用される |
| `stateStore` | `AgentStateStore` | `null`(永続化なし) | 設定すると、エージェントは呼び出しの `RuntimeContext` の `(userId, sessionId)` をキーとして、`call` のたびに `AgentState` を自動的にロード/保存する |
| `defaultSessionId` | `String` | エージェントの `name` | 呼び出しの `RuntimeContext` にセッション ID が含まれない場合に使われるフォールバック `sessionId` |
| `permissionContext` | `PermissionContextState` | `DEFAULT` モード | ツール実行に関するきめ細かなルール。[Permission System](./permission-system.md) を参照 |
| `modelConfig` | `ModelConfig` | デフォルト | モデルのリトライとフォールバックモデル |
| `reactConfig` | `ReactConfig` | デフォルト | 最大反復回数と拒否時の扱い |
| `maxIters` | `int` | `10` | ReAct メインループの最大反復回数(`reactConfig` の代替) |

## マルチユーザー / マルチセッションの並行処理

`ReActAgent` は**呼び出しをまたいでステートレス**です——単一のインスタンスで複数のユーザーとセッションを同時に処理できます。各 `call()` は、その `RuntimeContext` が運ぶ `(userId, sessionId)` を使って正しい会話状態を特定します。異なるセッションは完全に分離されています。

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.JsonFileAgentStateStore;
import java.nio.file.Paths;

// アプリケーション起動時にエージェントのインスタンスを1つ作成する(シングルトン)
ReActAgent agent = ReActAgent.builder()
        .name("assistant")
        .sysPrompt("あなたは親切なアシスタントです。")
        .model("dashscope:qwen-plus")
        .stateStore(new JsonFileAgentStateStore(
                Paths.get(System.getProperty("user.home"), ".agentscope/sessions")))
        .build();

// HTTP ハンドラー内 —— リクエストごとに異なる RuntimeContext を渡し、完全に分離される
agent.call(List.of(new UserMessage("こんにちは")),
        RuntimeContext.builder().userId("alice").sessionId("session-1").build()).block();

agent.call(List.of(new UserMessage("やあ")),
        RuntimeContext.builder().userId("bob").sessionId("session-2").build()).block();
```

各 `call()` の開始時、エージェントは指定された `(userId, sessionId)` の `AgentState`(会話コンテキスト、パーミッションルールなど)を自動的にロードします。呼び出しが終わると、状態は書き戻されます。異なるセッションは完全に分離されています。

:::{tip}
同一の `(userId, sessionId)` を対象とする呼び出しは**直列化**されます——2番目のリクエストは最初のリクエストの完了を待ちます。異なるセッションを対象とする呼び出しは並行して実行されます。
:::

完全な Spring Boot の例: `agentscope-examples/documentation/.../streaming/StreamingWebExample.java`。

## 中断(Interrupt)

外部から実行中の呼び出しをキャンセルする(ユーザーによるキャンセル、タイムアウト、グレースフルシャットダウンなど)には、`interrupt` を使います。

```java
import io.agentscope.core.agent.RuntimeContext;

// 対象のセッションを特定する
RuntimeContext target = RuntimeContext.builder()
        .userId("alice")
        .sessionId("session-001")
        .build();

// そのセッションで実行中の呼び出しを中断する
agent.interrupt(target);

// メッセージ付きで中断する —— セッションが再開されたとき、LLM はこのメッセージを見る
agent.interrupt(target, new UserMessage("ユーザーが操作をキャンセルしました"));
```

中断は**セッション単位**です。指定した `(userId, sessionId)` で実行中の呼び出しにのみ影響し、同じエージェント上の他の並行セッションには影響しません。

**中断後に起きること:**
- 現在の推論/ツール実行は、次のチェックポイント(推論の開始、行動の開始、各ストリーミングチャンク)で停止する
- エージェントは `GenerateReason.INTERRUPTED` が付与された Msg を返す
- 会話状態(AgentState)は自動的に保存される —— 同じセッションへの次の `call()` は中断箇所から再開する

生の `(userId, sessionId)` 文字列を使うこともできます。

```java
agent.interrupt("alice", "session-001");
agent.interrupt("alice", "session-001", interruptMsg);
```

## エージェントを実行する

`call` と `streamEvents` は同じ入力メッセージを受け取り、同じ推論・行動ループを駆動します。両者の違いは結果の受け渡し方です。

### call

`call` はすべてのイベントを内部で消費し、エージェントが完了するか外部とのやり取りのために一時停止したときに、最終的な `Msg` を返します。

```java
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import java.util.List;

UserMessage msg = new UserMessage("現在のディレクトリにあるファイルは何ですか?");
Msg result = agent.call(List.of(msg), RuntimeContext.empty()).block();
System.out.println(result.getTextContent());
```

### streamEvents

`streamEvents` は `AgentEvent` を1つずつ発行するので、テキスト、ツール呼び出しの進行状況、ライフサイクルイベントをリアルタイムで UI にストリーミングできます。各種類を処理するには `event.getType()` でディスパッチします。

```java
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;

agent.streamEvents(new UserMessage("README を要約してください。"))
        .doOnNext(event -> {
            if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA) {
                // ストリーミングされるテキストの断片 —— UI や標準出力に追記する
                System.out.print(((TextBlockDeltaEvent) event).getDelta());
            } else if (event.getType() == AgentEventType.TOOL_CALL_START) {
                // エージェントがツールを呼び出そうとしている —— 呼び出し情報を表示する
                System.out.println("\n[tool] " + ((ToolCallStartEvent) event).getToolCallName());
            }
            // その他のイベント: thinking ブロック、ツール結果、返信終了など
        })
        .blockLast();
```

イベントタイプとフィールドの完全なリファレンス: [メッセージとイベント](./message-and-event.md)。

### observe

`observe` を使うと、返信をトリガーせずにメッセージをエージェントのコンテキストへ注入できます —— あるエージェントが別のエージェントの出力を観測するようなマルチエージェント構成で有用です。

```java
agent.observe(otherAgentMsg).block();
```

## RuntimeContext (呼び出しごとのコンテキスト)

`RuntimeContext`(`io.agentscope.core.agent.RuntimeContext`)は**呼び出しごとのメタデータの入れ物**です。`call` / `stream` に1つのインスタンスを渡すと、エージェントはその呼び出しの間だけそれをバインドするため、下流のツール、middleware、hook はすべて同じ参照を参照できます。呼び出しが完了すると、フレームワークがバインドを解除します。

これは永続的な状態では**ありません**——それは `AgentState`(会話コンテキスト、圧縮された要約、パーミッションルール、ツールの状態)がカバーします。`RuntimeContext` は、単一の呼び出しにスコープされたデータ、すなわちテナント / userId / リクエスト ID、DB 接続、監査ロガー、フィーチャーフラグなどを運びます。

### 組み込みフィールドと属性レイヤー

`RuntimeContext` は3種類のスロットを公開しています。

| スロット | 設定方法 | 読み取り方法 |
|------|---------|----------|
| セッションフィールド | `sessionId(String)` / `userId(String)` | `getSessionId()` / `getUserId()` |
| 文字列属性(自由形式のキーバリュー) | `put(String key, Object value)` | `<T> T get(String key)` |
| 型付き属性(`Class<T>` によってビジネス POJO を注入) | `put(Class<T> type, T value)` / `put(String key, Class<T> type, T value)` | `<T> T get(Class<T> type)` / `<T> T get(String key, Class<T> type)` |

型付き属性はツールへの注入を支える機能です——`@Tool` メソッドに一致する型のパラメータを宣言すれば、フレームワークがその値を渡します。[Tool — コンテキストを受け取る](./tool.md#コンテキストを受け取る) を参照してください。文字列属性は、通常プロセス内の連携(たとえば middleware 間のシグナリング)に使われます。この2つのレイヤーは分離されており、型付きの値は `getExtra()` には現れず、その逆もまた同様です。

### 構築して渡す

```java
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import java.util.List;

RuntimeContext ctx =
        RuntimeContext.builder()
                .userId("alice")                                             // 省略可。null は匿名を意味する
                .sessionId("session-001")                                    // 状態のスロットを選択する
                .put("request_id", "req-abc-123")                            // 文字列レイヤー
                .put(UserContext.class, new UserContext("alice", "en"))      // 型付きレイヤー(POJO)
                .build();

Msg result = agent.call(List.of(new UserMessage("こんにちは。")), ctx).block();
```

`ReActAgent` は `call` と `stream` に `RuntimeContext` を受け取るオーバーロードを提供しますが、`streamEvents` にはありません——イベントストリームと一緒にコンテキストが必要な場合は `stream(msgs, options, ctx)` を使うか、ビルダーでグローバルな `toolExecutionContext` を設定してください。コンテキストが渡されない場合、フレームワークは `RuntimeContext.empty()`(セッションフィールドは null、属性マップは空)で代替し、エージェントはビルダー設定時の `defaultSessionId` にフォールバックします。

### 誰が読み取るのか

- **ツール**(`@Tool` メソッドと `ToolBase.callAsync`)—— [Tool — コンテキストを受け取る](./tool.md#コンテキストを受け取る) を参照。
- **Middleware**(すべての `MiddlewareBase` hook)—— 第2引数 `ctx` として受け取ります。[Middleware — RuntimeContext の読み取り](./middleware.md#runtimecontext-の読み取り) を参照。
- **同一呼び出し内のすべてのスレッド** —— 内部のマップは `ConcurrentMap` なので、hook とツールは同じインスタンスを読み書きして連携できます。

### 永続化との関係

- 自由形式 / 型付きの `RuntimeContext` 属性は決して `AgentState` に入らず、`AgentStateStore` によって書き戻されることもありません。
- `sessionId` / `userId` フィールドは永続化を**左右します**——各呼び出しは `(userId, sessionId)` の状態スロットをアクティブにするため、`RuntimeContext` に異なるアイデンティティを渡すと、ロード/保存される `AgentState` の対象が変わります。指定がない場合、エージェントはビルダー設定時の `defaultSessionId` にフォールバックします。

実行可能な例: `agentscope-examples/documentation/.../context/RuntimeContextExample.java`、`tool/ToolExecutionContextExample.java`。

:::{note}
レガシーな `ToolExecutionContext`(`io.agentscope.core.tool`)は `@Deprecated` です。新しいコードでは `RuntimeContext` を使ってください。レガシーな型は `RuntimeContext.asToolExecutionContext()` によって自動的にブリッジされるため、既存のコードはそのまま動作します。
:::

## Human-in-the-Loop

エージェントは、次の2つのケースで一時停止し、特別なイベントを発行します。1つはツール呼び出しに**ユーザー確認**が必要な場合(パーミッションシステムが ASK を返した)、もう1つはツールが**外部実行**としてマークされている場合(結果がエージェントの外部から来る必要がある)です。どちらの場合も、次の `call` を通じて結果を返すことでエージェントを再開します。

### ユーザー確認

パーミッションシステムがツール呼び出しにユーザーの承認が必要だと判断すると、エージェントは `RequireUserConfirmEvent` を発行して一時停止します。

**1. `RequireUserConfirmEvent` を受け取る** —— `streamEvents` を使って一時停止を検知します。このイベントは `getReplyId()`(再開に使う)と `getToolCalls()`(それぞれ `getId()` / `getName()` / `getInput()` / `getSuggestedRules()` を公開する `ToolUseBlock` のリスト)を運びます。

```java
import io.agentscope.core.event.RequireUserConfirmEvent;

agent.streamEvents(msg)
        .doOnNext(event -> {
            if (event instanceof RequireUserConfirmEvent confirm) {
                confirm.getToolCalls().forEach(tc -> {
                    System.out.println("ツール: " + tc.getName() + ", 入力: " + tc.getInput());
                    System.out.println("提案されたルール: " + tc.getSuggestedRules());
                });
            }
        })
        .blockLast();
```

**2. 確認結果を組み立てる** —— 保留中の各呼び出しについて `ConfirmResult` を構築します。返す際にツールの入力を変更したり、提案されたルールを受け入れて同一の将来の呼び出しを自動許可させたりすることができます。

```java
import io.agentscope.core.event.ConfirmResult;
import java.util.ArrayList;
import java.util.List;

List<ConfirmResult> confirmResults = new ArrayList<>();
for (var tc : confirmEvent.getToolCalls()) {
    confirmResults.add(
            new ConfirmResult(
                    /* confirmed = */ true,                  // 拒否する場合は false
                    /* toolCall  = */ tc,                    // (必要なら変更して)そのまま返す
                    /* rules     = */ tc.getSuggestedRules() // ルールを受け入れる → 以降の呼び出しは自動許可される
                    ));
}
```

**3. エージェントを再開する** —— `confirmResults` をメタデータ経由で次の `call` に渡します。

```java
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;

UserMessage resumeMsg =
        UserMessage.builder()
                .metadata(java.util.Map.of(
                        Msg.METADATA_CONFIRM_RESULTS, confirmResults))
                .build();

Msg result = agent.call(List.of(resumeMsg), RuntimeContext.empty()).block();
```

- **確認済み**のツール呼び出しは即座に実行され、エージェントは推論を続けます。
- **拒否された**ツール呼び出しは LLM から見えるエラー結果を生成し、LLM は別のアプローチを試みることがあります。
- **受け入れられたルール**はパーミッションエンジンに永続化されます —— 一致する将来の呼び出しは、確認を求められることなく自動的に許可されます。

### 外部でのツール実行

エージェントが `isExternalTool() == true` のツールを呼び出すと、`RequireExternalExecutionEvent` を発行して一時停止します。ツールのロジックはエージェントの外部で実行されます —— 通常は人間のオペレーターや外部システムによって実行されます。

**1. `RequireExternalExecutionEvent` を受け取る** —— ユーザー確認と同じ形です。`getReplyId()` に加え、外部実行を待つ `getToolCalls()` のリストがあります。

```java
import io.agentscope.core.event.RequireExternalExecutionEvent;

agent.streamEvents(msg)
        .doOnNext(event -> {
            if (event instanceof RequireExternalExecutionEvent ext) {
                ext.getToolCalls().forEach(tc ->
                        System.out.println("外部実行: " + tc.getName() + "(" + tc.getInput() + ")"));
            }
        })
        .blockLast();
```

**2. 外部で実行して結果を組み立てる** —— アクションをエージェントの外部で実行し、各結果を `ToolResultBlock` としてラップします。

```java
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import java.util.ArrayList;
import java.util.List;

List<ToolResultBlock> executionResults = new ArrayList<>();
for (var tc : externalEvent.getToolCalls()) {
    String output = runExternalOperation(tc.getName(), tc.getInput());
    executionResults.add(
            ToolResultBlock.builder()
                    .id(tc.getId())
                    .name(tc.getName())
                    .output(List.of(TextBlock.builder().text(output).build()))
                    .state(ToolResultState.SUCCESS)
                    .build());
}
```

**3. エージェントを再開する** —— 結果を次の `call` の入力メッセージとして返します。結果が検証されると、それらはエージェントのコンテキストに注入され、エージェントは `ExternalExecutionResultEvent` を発行します。その `getReplyId()` は先の `RequireExternalExecutionEvent#getReplyId()` と一致します。推論は一時停止した箇所から続行されます。

:::{tip}
インタラクティブな UI を構築する場合は `streamEvents` を使いましょう —— リアルタイムで一時停止を検知し、すぐにユーザーへ確認を求められます。イベントを自動的に処理するプログラム的なフローには `call` を使います。完全な実行可能例: `agentscope-examples/documentation/.../hitl/PermissionHITLExample.java`。
:::

## 状態永続化の設定(AgentStateStore)

`AgentState` は、エージェントを再開するために必要なすべて——会話コンテキスト、圧縮された要約、パーミッションルール、ツールの状態、現在の返信位置——を保持します。[`AgentStateStore`](../../integration/session/index.md) はそのストレージ抽象化です。

**ビルダーで `stateStore(...)` を設定すると、エージェントは自動的に永続化と復元を行います**——すべての `call` が `AgentState` を書き戻し、同じ `(userId, sessionId)` で次に呼び出したときにロードされます。エージェントのインスタンス自体はセッションに関してステートレスです——スロットは呼び出しごとに `RuntimeContext` から選択されます(指定がなければ `defaultSessionId` にフォールバック)。

```java
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.state.JsonFileAgentStateStore;
import java.nio.file.Paths;

ReActAgent agent = ReActAgent.builder()
        .name("my_agent")
        .sysPrompt("あなたは親切なアシスタントです。")
        .model(model)
        .toolkit(new Toolkit())
        .stateStore(new JsonFileAgentStateStore(
                Paths.get(System.getProperty("user.home"), ".agentscope/sessions")))
        .build();

// この会話のスロットを選ぶ。userId は省略可能(null は匿名を意味する)。
RuntimeContext rc = RuntimeContext.builder()
        .userId("user_123")
        .sessionId("session_789")
        .build();

// (user_123, session_789) のデータが存在すれば自動的にロードされ、呼び出し完了時に自動的に永続化される。
agent.call(List.of(new UserMessage("前のタスクを再開してください。")), rc).block();
```

組み込みおよび拡張の実装:

| 実装 | モジュール | 使いどころ |
|----------------|--------|-------------|
| `InMemoryAgentStateStore` | `agentscope-core` | 単体テスト / シングルプロセスのデモ |
| `JsonFileAgentStateStore` | `agentscope-core` | シングルマシンでの開発。`(userId, sessionId)` ごとにディレクトリを分けて JSON を保存 |
| `RedisAgentStateStore` | `agentscope-extensions-redis` | マルチレプリカの本番環境。プロセスやノードをまたいで共有される |
| `MysqlAgentStateStore` | `agentscope-extensions-mysql` | 状態をリレーショナルストアに保存する必要がある場合(監査 / レポーティング) |

多くの場合、単一の `sessionId` で十分です。ユーザー単位のパーティショニングが必要な場合は、`RuntimeContext` に `userId` も設定してください。ストアは `(userId, sessionId)` のペアで各スロットにアドレスします。

特定のセッションの状態を調べるには `agent.getAgentState(userId, sessionId)` または `agent.getAgentState(runtimeContext)` を使います。

```java
AgentState state = agent.getAgentState("alice", "session-001");
state.getContext().size();                  // 現在のメッセージ数
String json = state.toJson();               // JSON にシリアライズする
```

フィールドごとの詳細、ノードをまたいだ継続、状態ストアがコンパクション / Plan Mode / サブエージェントとどう連携するかについては、[コンテキストと AgentState](context.md) と [コンパクション](../harness/compaction.md) を参照してください。

## 構造化出力

構造化出力は、エージェントの応答を自由形式のテキストではなく、指定した JSON Schema に従わせます。エージェントの出力をプログラムから消費する必要がある場合——フォーム入力、データ抽出、分類など——には常にこれを使います。

### 基本的な使い方

Java クラス(または `JsonNode` スキーマ)を `call` に渡します。

```java
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;

// 出力構造を定義する
public record WeatherResponse(String location, String temperature, String condition) {}

Msg result = agent.call(List.of(new UserMessage("サンフランシスコの天気は?")), WeatherResponse.class).block();

// 結果から強く型付けされたデータを抽出する
WeatherResponse weather = result.getStructuredData(WeatherResponse.class);
System.out.println(weather.location());      // "San Francisco"
System.out.println(weather.temperature());   // "18°C"
```

構造化出力はツールと組み合わせて使えます —— エージェントはまずツールを呼び出して情報を収集し、その後で指定されたスキーマに従って最終結果を出力できます。

### 仕組み

フレームワークはモデルの能力に応じて、実装パスを自動的に選択します。

| パス | 条件 | 動作 |
|------|-----------|----------|
| **ネイティブ** | モデルがツールと `response_format` の両方をサポートする(OpenAI、DashScope など) | JSON Schema がモデル API に `response_format` として直接渡される。モデルは有効な JSON 出力を保証し、ループは自然に終了する |
| **フォールバック** | モデルがネイティブな構造化出力をサポートしない(Anthropic、Ollama など) | 指示のヒントを持つ合成ツール `generate_response` が注入される。モデルはこのツールを呼び出して構造化された結果を出力する |

どちらの場合も、呼び出し側のコードは同一です —— パスの選択は透過的です。

```
┌─── call(msgs, Schema.class) ───┐
│                                │
│   model.supportsNative...?     │
│      ├─ yes → response_format  │  ← オーバーヘッドゼロ、モデルネイティブ
│      └─ no  → generate_response│  ← 合成ツール + 指示
│                                │
└──── returns Msg with schema ───┘
```

### 結果を読み取る

`call` が返す `Msg` は、パース済みの構造化データをメタデータに保持しています。

```java
// 方法1: 強く型付けされた抽出
WeatherResponse data = result.getStructuredData(WeatherResponse.class);

// 方法2: Map として読み取る
@SuppressWarnings("unchecked")
Map<String, Object> map = (Map<String, Object>) result.getMetadata().get("_structured_output");
```

### JsonNode スキーマを使う

Java クラスを定義したくない場合は、生の JSON Schema を渡すこともできます。

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

ObjectMapper om = new ObjectMapper();
JsonNode schema = om.readTree("""
    {
      "type": "object",
      "properties": {
        "sentiment": { "type": "string", "enum": ["positive", "negative", "neutral"] },
        "confidence": { "type": "number" }
      },
      "required": ["sentiment", "confidence"]
    }
    """);

Msg result = agent.call(List.of(new UserMessage("このレビューの感情を分析してください")), schema).block();
```

## さらに使える機能

以下の機能はビルダー経由で設定します。詳細はそれぞれのドキュメントを参照してください。

### モデルのフォールトトレランス

```java
ReActAgent.builder()
        .model("dashscope:qwen-plus")
        .maxRetries(3)                              // モデル呼び出し失敗時に自動リトライする
        .fallbackModel("dashscope:qwen-max")        // 連続失敗後にフォールバックモデルへ切り替える
        .build();
```

### スキル

スキルは、LLM がオンデマンドで有効化できる、ホットロード可能な Markdown プロンプトモジュールです。

```java
ReActAgent.builder()
        .skillRepository(new MysqlSkillRepository(dataSource))
        .build();
```

### 組み込みツール

| ビルダーメソッド | 説明 |
|---|---|
| `enableMetaTool(true)` | `list_tools` / `activate_group` メタツールを登録する —— LLM がツールグループを発見し切り替えられるようにする |
| `enableTaskList()` | タスクリストツールを登録する —— LLM が複雑なタスクをステップに分解し、進捗を追跡できるようにする |

## さらに読む

::::{grid} 2

:::{grid-item-card} Permission System
:link: ./permission-system.html

エージェントがどのツールを、どのような条件で呼び出せるかを制御する。
:::

:::{grid-item-card} Middleware
:link: ./middleware.html

agent / reasoning / acting / model-call の各 hook でエージェントの振る舞いをインターセプトし、変更する。
:::

::::
