# A2A (Agent-to-Agent)

`agentscope-extensions-a2a` は [A2A プロトコル](https://a2aproject.github.io/A2A/) を実装しており、2 つのサブモジュールを提供します。

- `agentscope-extensions-a2a-client`: リモートの A2A Agent をローカルの `Agent` としてラップし、直接 `call(...)` できるようにします。
- `agentscope-extensions-a2a-server`: ローカルの `ReActAgent` を A2A Server として公開します。

この 2 つのモジュールは独立しており、どちらか一方だけを使うこともできます。

## クライアント: リモートの A2A Agent を呼び出す

### 依存関係を追加する

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-a2a-client</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

### AgentCard を直接渡す

```java
import io.a2a.spec.AgentCard;
import io.agentscope.core.a2a.agent.A2aAgent;

AgentCard card = AgentCard.builder()
    .name("remote-translator")
    .url("http://other-service:8080")
    // ...
    .build();

A2aAgent remote = A2aAgent.builder()
    .name("remote-translator")
    .agentCard(card)
    .build();

Msg result = remote.call(new UserMessage("英語に翻訳してください: 你好")).block();
```

### well-known 経由で自動検出する

```java
import io.agentscope.core.a2a.agent.card.WellKnownAgentCardResolver;

WellKnownAgentCardResolver resolver = new WellKnownAgentCardResolver(
    "http://127.0.0.1:8080",
    "/.well-known/agent-card.json",
    Map.of()
);

A2aAgent remote = A2aAgent.builder()
    .name("remote")
    .agentCardResolver(resolver)
    .build();
```

`A2aAgent` は `AgentBase` のサブクラスなので、Pipeline、MsgHub、Subagent などと自然に組み合わせて使えます。

## サーバー: ReActAgent を A2A Server として公開する

### 依存関係を追加する

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-a2a-server</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

### サーバーを構築する

```java
import io.agentscope.core.a2a.server.AgentScopeA2aServer;
import io.agentscope.core.a2a.server.transport.jsonrpc.JsonRpcTransportProperties;

ReActAgent.Builder agentBuilder = ReActAgent.builder()
    .name("backend-agent")
    .model(model);

AgentScopeA2aServer server = AgentScopeA2aServer.builder()
    .agentBuilder(agentBuilder)
    .transportProperties(new JsonRpcTransportProperties())
    // .agentCard(customCard)
    // .agentRegistry(myRegistry)
    .build();

// 受信リクエストを、使用している Web フレームワークのトランスポートラッパーに委譲する
TransportWrapper wrapper = server.getTransportWrapper("JSONRPC");
// ... Spring/Quarkus のコントローラーが wrapper.handle(...) に転送する

server.postEndpointReady();   // Web サーバーがリッスンを開始した後に呼び出す — 登録処理などをトリガーする
```

`AgentScopeA2aServer` はそれ自体でポートをバインドしたりエンドポイントを公開したりはしません。コンポーネントとリクエスト処理チェーンを組み立てるだけです。トランスポートは Spring Boot、Quarkus、Vert.x などに好みに応じて組み込みます。

### オプションのコンポーネント

- `TaskStore` / `QueueManager`: タスクとイベントキューのストア。デフォルトではインメモリで、本番環境では永続化バージョンに差し替えます。
- `PushNotificationConfigStore` / `PushNotificationSender`: アウトバウンド通知。
- `AgentRegistry`: `AgentCard` を Nacos のような外部レジストリに登録します（[Nacos](../infrastructure/nacos.md) を参照）。

## Spring Boot Starter

Spring Boot を使っている場合は、`agentscope-spring-boot-starter-a2a-server` の利用をお勧めします — サーバーとコントローラーを自動構成してくれます。[クイックスタート](../../docs/quickstart.md) を参照してください。
