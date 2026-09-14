# WeCom チャネル

`agentscope-extensions-channel-wecom` は、**暗号化コールバック**の仕組みを通じて Agent を WeCom（企業微信 / WeChat Work）に接続します。Spring の `@RestController` がメッセージコールバックを受信し、復号したうえで Gateway へディスパッチします。

## 使いどころ

- Agent が 1:1 チャットまたはグループチャットで WeCom ボットのメッセージに応答する必要がある。
- アプリケーションがすでに Spring Boot 上で動作している。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-channel-wecom</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 前提条件

1. [WeCom 管理コンソール](https://work.weixin.qq.com/) で**アプリケーション**を作成します。
2. **メッセージ受信** API を有効化し、コールバック URL を設定します。
   `https://your-host/api/channels/wecom/{channelId}/callback`
3. **Corp ID**、**Agent ID**、**Secret**、**Token**、**EncodingAESKey** を控えておきます。

## クイックスタート

```java
WeComChannel channel = WeComChannel.fromProperties(
    "my-wecom",
    ChannelConfig.of("my-wecom", "main"),
    Map.of(
        "corpId",         "your-corp-id",
        "agentId",        "1000002",
        "secret",         "your-secret",
        "token",          "your-callback-token",
        "encodingAesKey",  "your-encoding-aes-key"
    ));

GatewayBootstrap gw = GatewayBootstrap.builder()
    .agent("main", agent)
    .channel(channel)
    .build();

gw.start();
```

## 設定プロパティ

| プロパティ | 必須 | デフォルト | 説明 |
|----------|----------|---------|-------------|
| `corpId` | はい | — | 企業の Corp ID |
| `agentId` | はい | — | アプリケーションの Agent ID |
| `secret` | はい | — | アクセストークン取得用のアプリケーションシークレット |
| `token` | はい | — | 署名検証用のコールバックトークン |
| `encodingAesKey` | はい | — | メッセージの暗号化・復号に使う AES キー |
| `callbackPath` | いいえ | `/api/channels/wecom/{channelId}/callback` | コールバック URL のパスを上書き |
| `apiBase` | いいえ | `https://qyapi.weixin.qq.com` | WeCom API のベース URL |

## 暗号化

WeCom のコールバックはすべて暗号化されています。アダプターは [WeCom コールバック暗号化仕様](https://developer.work.weixin.qq.com/document/path/90238) を実装した `WeComCrypto` を使って、復号と署名検証を自動的に処理します。

## メッセージフロー

**インバウンド:** `WeComCallbackController` → URL 検証（echostr）→ 復号 → MsgId 重複排除 → `WeComInboundMapper`（テキストメッセージ）→ ボットループガード → Gateway。

**アウトバウンド:** `WeComOutboundClient` が `/cgi-bin/message/send`（DM）または `/cgi-bin/appchat/send`（グループ）経由で返信を送信し、`WeComAccessTokenProvider` から取得した `access_token` で認証します。
