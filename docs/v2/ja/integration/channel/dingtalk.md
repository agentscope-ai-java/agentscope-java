# DingTalk チャンネル

`agentscope-extensions-channel-dingtalk` は **Stream プロトコル**を使って Agent を DingTalk (钉钉) に接続します。Stream プロトコルは永続的な WebSocket であり、パブリックな webhook エンドポイントを公開することなく、bot メッセージをリアルタイムで受信します。

## 利用シーン

- Agent が DingTalk の bot メッセージ(DM とグループでの @メンション)に応答する必要がある場合。
- ポーリングや webhook コールバックよりも、WebSocket ベースの push モデルを好む場合。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-channel-dingtalk</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 前提条件

1. [DingTalk 開発者コンソール](https://open-dev.dingtalk.com/)で**エンタープライズ内部アプリ**を作成します。
2. **Bot** 機能を有効にし、bot メッセージのトピックを購読します。
3. **App Key**、**App Secret**、**Robot Code** を控えておきます。

## クイックスタート

```java
DingTalkChannel channel = DingTalkChannel.fromProperties(
    "my-dingtalk",
    ChannelConfig.of("my-dingtalk", "main"),
    Map.of(
        "appKey",    "your-app-key",
        "appSecret", "your-app-secret",
        "robotCode", "your-robot-code"
    ));

GatewayBootstrap gw = GatewayBootstrap.builder()
    .agent("main", agent)
    .channel(channel)
    .build();

gw.start();   // opens the Stream WebSocket and begins dispatching
```

## 設定プロパティ

| プロパティ | 必須 | デフォルト | 説明 |
|----------|----------|---------|-------------|
| `appKey` | はい | — | エンタープライズ内部アプリの App Key |
| `appSecret` | はい | — | エンタープライズ内部アプリの App Secret |
| `robotCode` | はい | — | 送信元 id として使用される Robot Code |
| `apiBase` | いいえ | `https://api.dingtalk.com` | OpenAPI のベース URL |
| `streamRegisterUrl` | いいえ | `https://api.dingtalk.com/v1.0/gateway/connections/open` | Stream ゲートウェイの登録エンドポイント |

## メッセージフロー

**インバウンド:** `DingTalkStreamClient` が DingTalk ゲートウェイへの WebSocket を開き、bot メッセージのコールバックを受信し、各フレームを ACK した後、`DingTalkInboundMapper` → 冪等性チェック → bot ループガード → Gateway の順で処理を振り分けます。

**アウトバウンド:** 返信は `DingTalkOutboundClient` を通じて OpenAPI の `batchSend` エンドポイント(DM 用の `oToMessages/batchSend` とグループ用の `groupMessages/send`)で送信されます。テキストと Markdown のフォーマットは自動検出されます。

## 再接続

WebSocket が切断された場合、Stream クライアントは指数バックオフ(1 秒 → 上限 60 秒)で自動的に再接続します。
