---
title: Channel
description: Channel を通じてメッセージをルーティングし、セッションを管理し、イベントをストリーミングする
---

## それぞれが何をするか

**Gateway** はアプリケーションコードとエージェントの間に位置します。以下を処理します。

- **セッション管理** — 各ユーザーの会話を安定したセッション id にマッピングします。エージェントはターンをまたいで一貫した記憶を見ます。
- **セッションごとの並行制御** — 同一セッションへの同時メッセージは公平にキューイングされ、エージェントが自分自身と競合することはありません。
- **エージェントルーティング** — マルチエージェント構成では、各メッセージを適切なエージェントへルーティングします。

**Channel** はメッセージングプラットフォーム(HTTP、WebSocket、Slack など)を Gateway のルーティングモデルに適合させます。誰がメッセージを送ったか、どのエージェントが処理すべきか、返信をどこに届けるかを解決します。

ほとんどのユースケースでは Gateway や Channel を直接扱うことはありません――`agent.channel(...)` が裏側ですべてを配線します。

## クイックスタート

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("assistant")
    .sysPrompt("You are a helpful assistant.")
    .model("dashscope:qwen-plus")
    .build();

// Bind a ChatUI channel.
ChatUiChannel chat = agent.channel(ChatUiChannel.create());

// Send messages. Each userId gets its own session automatically.
Msg reply = chat.send(SendOptions.userId("user-1"), "Hello!").block();

// Same user, same session — conversation continues.
Msg followUp = chat.send(SendOptions.userId("user-1"), "Tell me more.").block();

// Different user, different session.
Msg otherUser = chat.send(SendOptions.userId("user-2"), "Hi there").block();
```

`agent.channel(...)` は内部の gateway を遅延生成し、エージェントを登録し、gateway を channel に注入します。この呼び出しの後、`chat` はすぐに使える状態になります。

### SendOptions

`SendOptions` は channel に**誰が**話していて、**どの会話**に属しているかを伝えます。

| ファクトリ | 動作 |
|---------|----------|
| `SendOptions.userId("user-1")` | ユーザーごとに1セッション(最も一般的) |
| `SendOptions.of("user-1", "session-a")` | 明示的なセッション――ユーザーごとに複数の会話 |
| `SendOptions.userId("user-1").withAgentId("support")` | マルチエージェント構成で特定のエージェントへルーティング |
| `SendOptions.userId("user-1").withAttribute("tenant", "acme")` | エージェントのターンに文字列/型付き属性を付与 |
| `SendOptions.userId("user-1").withRuntimeContext(rtc)` | 完全な `RuntimeContext`(例:force-sync フラグ)を持ち運ぶ |

```java
// Same user, two independent conversations
chat.send(SendOptions.of("user-1", "session-a"), "Topic A").block();
chat.send(SendOptions.of("user-1", "session-b"), "Topic B").block();

// Pass application context into the agent turn
chat.send(
        SendOptions.userId("user-1")
                .withAttribute("tenant", "acme")
                .withRuntimeContext(
                        RuntimeContext.builder()
                                .put(AgentSpawnTool.CTX_FORCE_SYNC, true)
                                .build()),
        "Investigate the ticket")
        .block();
```

### マルチモーダル / 構造化メッセージ

プレーンテキストの `send(String)` は便利な省略形です。画像、音声、あるいは複数パートのターンを扱うには、事前に組み立てた `Msg`(または `List<Msg>`)を渡します――すべての `String` オーバーロードには対応する `Msg` / `List<Msg>` のバリアント(`SendOptions` や `sendStream` を含む)があります。

```java
Msg multimodal = Msg.builder()
        .role(MsgRole.USER)
        .content(
                TextBlock.builder().text("What is in this image?").build(),
                ImageBlock.builder()
                        .source(URLSource.builder().url("https://example.com/photo.png").build())
                        .build())
        .build();

chat.send(SendOptions.userId("user-1"), multimodal).block();
chat.send(SendOptions.userId("user-1"), List.of(multimodal)).block();
chat.send(multimodal).block(); // single-session mode
```

### RuntimeContext のマージ

Channel のターンは常に Gateway の内部で `RuntimeContext` を組み立てます。呼び出し元は `SendOptions` / `InboundMessage.runtimeContext()` / `ChannelRuntimeContextResolver` を通じて**呼び出し元ベース**を提供できます。マージ順は次のとおりです。

1. 呼び出し元コンテキストから始める(空でもよい)
2. `ChannelRuntimeContextResolver` が設定されていて非 null を返す場合、その値が呼び出し元ベースを**置き換える**
3. Gateway が identity フィールド――`sessionId`(`gw-…`)、`userId`、`msgContext`、`gateKey`、`outboundAddress`――を上書きし、これらは競合時に常に優先される

resolver を `GatewayBootstrap` 経由で配線します。

```java
GatewayBootstrap gw = GatewayBootstrap.builder()
        .agent("main", b -> b.name("assistant").model(model))
        .runtimeContextResolver(req ->
                RuntimeContext.builder(req.callerContext())
                        .put("tenant", resolveTenant(req))
                        .build())
        .build();
ChatUiChannel chat = gw.chatUiChannel();
```

あるいは gateway を取得した後に `gateway.setRuntimeContextResolver(...)` を呼び出します。ビジネス属性を `MsgContext.extra` に入れては**いけません**――そのマップはセッションキーに関与します。

## ストリーミングイベント + SSE

`sendStream()` は `Flux<AgentEvent>` を返します――`agent.streamEvents()` と同じきめ細かいイベントストリームですが、セッション管理を伴う gateway を通じてルーティングされます。

```java
chat.sendStream(SendOptions.userId("user-1"), "What is the weather in Beijing?")
    .doOnNext(event -> {
        if (event instanceof TextBlockDeltaEvent delta) {
            System.out.print(delta.getDelta());
        } else if (event instanceof ToolCallStartEvent tc) {
            System.out.println("\n[tool] " + tc.getToolCallName());
        }
    })
    .blockLast();
```

### Spring Boot の SSE コントローラー

```java
@GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> chat(@RequestParam String message,
                                          @RequestParam String userId,
                                          @RequestParam(required = false) String sessionId) {
    SendOptions options = sessionId != null
            ? SendOptions.of(userId, sessionId)
            : SendOptions.userId(userId);

    return chat.sendStream(options, message)
            .map(event -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("type", event.getType().name());
                payload.put("id", event.getId());
                if (event instanceof TextBlockDeltaEvent delta) {
                    payload.put("delta", delta.getDelta());
                } else if (event instanceof SubagentExposedEvent se) {
                    payload.put("subagentId", se.getSubagentId());
                    payload.put("agentId", se.getAgentId());
                    payload.put("label", se.getLabel());
                }
                return ServerSentEvent.<String>builder()
                        .data(objectMapper.writeValueAsString(payload))
                        .build();
            });
}
```

## 公開されたサブエージェントとの対話

エージェントが `expose_to_user=true` でサブエージェントを spawn すると、gateway はそのサブエージェントをユーザーが直接アドレス指定できるエントリポイントとして公開します。`SubagentExposedEvent` が `subagentId` を伴って `sendStream()` のイベントストリームに送出されます。

### 公開されたサブエージェントを発見する

```java
AtomicReference<String> subagentId = new AtomicReference<>();

chat.sendStream(SendOptions.userId("user-1"), "Spawn a researcher to investigate AI trends")
    .doOnNext(event -> {
        if (event instanceof SubagentExposedEvent se) {
            subagentId.set(se.getSubagentId());
            System.out.printf("Subagent exposed: id=%s agent=%s label=%s%n",
                    se.getSubagentId(), se.getAgentId(), se.getLabel());
        }
        if (event instanceof TextBlockDeltaEvent delta) {
            System.out.print(delta.getDelta());
        }
    })
    .blockLast();
```

`SubagentExposedEvent` のフィールド:

| フィールド | 説明 |
|-------|-------------|
| `subagentId` | このサブエージェントへメッセージを送るためのハンドル |
| `agentId` | サブエージェントの種類(例:`"researcher"`) |
| `sessionId` | サブエージェントのセッション id |
| `label` | 任意の人間可読な名前 |

### サブエージェントへメッセージを送る

`subagentId` を手に入れたら、親エージェントを完全に迂回して、サブエージェントへ直接メッセージを送信できます。

```java
// Non-streaming
Msg reply = chat.sendToSubagent(subagentId, "Focus on LLM agents").block();

// Streaming
chat.sendToSubagentStream(subagentId, "Focus on LLM agents")
    .doOnNext(event -> {
        if (event instanceof TextBlockDeltaEvent delta) {
            System.out.print(delta.getDelta());
        }
    })
    .blockLast();
```

### サブエージェント対応の SSE

典型的な SSE コントローラーは、メインエージェントとサブエージェント両方のメッセージを処理します。

```java
@GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> chat(@RequestParam String userId,
                                          @RequestParam String message,
                                          @RequestParam(required = false) String subagentId) {
    Flux<AgentEvent> events;
    if (subagentId != null) {
        events = chat.sendToSubagentStream(subagentId, message);
    } else {
        events = chat.sendStream(SendOptions.userId(userId), message);
    }
    return events.map(event -> toSSE(event));
}
```

クライアントは `SUBAGENT_EXPOSED` イベントを監視して新しい会話タブを描画し、以降のリクエストで `subagentId` を返送します。

## マルチエージェントルーティング

複数の `HarnessAgent` インスタンスを扱うシナリオでは、`GatewayBootstrap` を使います。

```java
HarnessAgent salesAgent = HarnessAgent.builder()
    .name("sales").sysPrompt("You are a sales assistant.")
    .model("dashscope:qwen-plus").build();

HarnessAgent supportAgent = HarnessAgent.builder()
    .name("support").sysPrompt("You are a support agent.")
    .model("dashscope:qwen-plus").build();

GatewayBootstrap gw = GatewayBootstrap.builder()
    .agent("sales", salesAgent)
    .agent("support", supportAgent)
    .mainAgent("sales")          // default when no agent is specified
    .build();

ChatUiChannel chat = gw.chatUiChannel();
```

### agentId によるルーティング

`SendOptions.withAgentId()` を使ってメッセージを特定のエージェントへルーティングします。

```java
// Routes to sales (the default main agent)
chat.send(SendOptions.userId("user-1"), "What products?").block();

// Routes to support explicitly
chat.send(SendOptions.userId("user-1").withAgentId("support"), "Billing issue").block();
```

### GatewayBootstrap でのスレッド公開

サブエージェントで `expose_to_user` を有効にするには、gateway のブリッジを各エージェントのサブエージェントミドルウェアに配線します。

```java
GatewayBootstrap gw = GatewayBootstrap.builder()
    .agent("main", mainAgent)
    .build();

// Wire the bridge so agent_spawn(expose_to_user=true) works.
SubagentGatewayBridge bridge = gw.gatewayBridge();
// Pass bridge to the agent's SubagentsMiddleware via setGatewayBridge().
```

`agent.channel(...)` を使えば、この配線は自動的に行われます。

## カスタム Channel

`Channel` インターフェースを実装して、新しいメッセージングプラットフォームに適合させます。

```java
public class MySlackChannel implements Channel {
    @Override public String channelId() { return "slack"; }
    @Override public ChannelConfig config() { return myConfig; }
    @Override public void init(Gateway gateway) { this.gateway = gateway; }
    @Override public void start() { /* connect to Slack */ }
    @Override public void stop() { /* disconnect */ }

    @Override
    public Mono<Msg> dispatch(InboundMessage message) {
        RouteResult route = router.resolveRoute(config(), message);
        return gateway.run(route.context(), message.messages(), route.outboundAddress());
    }

    // Optional: streaming dispatch
    @Override
    public Flux<AgentEvent> dispatchStream(InboundMessage message) {
        RouteResult route = router.resolveRoute(config(), message);
        return gateway.runStream(route.context(), message.messages(), route.outboundAddress());
    }
}
```

`GatewayBootstrap` に登録します。

```java
GatewayBootstrap gw = GatewayBootstrap.builder()
    .agent("main", agent)
    .channel(new MySlackChannel())
    .build();

gw.start();   // calls init() + start() on all channels
// ...
gw.stop();    // calls stop() on all channels
```

## 組み込みの channel アダプター

AgentScope は、拡張モジュールとして人気のメッセージングプラットフォーム向けにすぐ使える Channel アダプターを提供しています。

- [DingTalk](/v2/ja/integration/channel/dingtalk) — Stream プロトコル(持続的な WebSocket)
- [Feishu / Lark](/v2/ja/integration/channel/feishu) — イベント購読コールバック
- [GitHub](/v2/ja/integration/channel/github) — Issue / PR コメントの webhook
- [GitLab](/v2/ja/integration/channel/gitlab) — Note hook
- [WeCom](/v2/ja/integration/channel/wecom) — 暗号化コールバック

詳細は [Channel アダプター](/v2/ja/integration/channel/index) の統合概要を参照してください。

## 関連ページ

- [Subagent](/v2/ja/docs/harness/subagent) — サブエージェントの宣言と spawn、バックグラウンドタスク、ストリーミング転送
- [Architecture](/v2/ja/docs/harness/architecture) — 親エージェントと子エージェントがどう協調するか
