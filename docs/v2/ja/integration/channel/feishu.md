---
title: Feishu チャンネル
---

`agentscope-extensions-channel-feishu` は **Event Subscription v2** コールバック機構を通じて Agent を Feishu / Lark (飞书) に接続します。Spring の `@RestController` が webhook コールバックを受信し、必要に応じて暗号化されたペイロードを復号し、Gateway を通じてメッセージを振り分けます。

## 利用シーン

- Agent が 1:1 チャットまたはグループでの @メンションで Feishu の bot メッセージに応答する必要がある場合。
- アプリケーションがすでに Spring Boot で動作している場合(コールバックコントローラーが自動登録されます)。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-channel-feishu</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 前提条件

1. [Feishu 開発者コンソール](https://open.feishu.cn/)で**カスタムアプリ**を作成します。
2. **Bot** 機能を有効にします。
3. **Event Subscription** のコールバック URL をアプリケーションに向けて設定します:
   `https://your-host/api/channels/feishu/{channelId}/callback`
4. **App ID** と **App Secret** を控えておきます。必要に応じて **Encrypt Key** と **Verification Token** を設定します。

## クイックスタート

```java
FeishuChannel channel = FeishuChannel.fromProperties(
    "my-feishu",
    ChannelConfig.of("my-feishu", "main"),
    Map.of(
        "appId",     "cli_xxxxx",
        "appSecret", "your-app-secret"
    ));

GatewayBootstrap gw = GatewayBootstrap.builder()
    .agent("main", agent)
    .channel(channel)
    .build();

gw.start();
```

`FeishuCallbackController` は `/api/channels/feishu/{channelId}/callback` に自動登録される Spring の `@RestController` です。URL 検証のハンドシェイクを自動的に処理します。

## 設定プロパティ

| プロパティ | 必須 | デフォルト | 説明 |
|----------|----------|---------|-------------|
| `appId` | はい | — | Feishu カスタムアプリの id(cli_xxx) |
| `appSecret` | はい | — | Feishu カスタムアプリのシークレット |
| `encryptKey` | いいえ | — | AES-256-CBC 暗号化キー。ペイロードの暗号化を有効にする |
| `verificationToken` | いいえ | — | チャレンジハンドシェイク用の URL 検証トークン |
| `callbackPath` | いいえ | `/api/channels/feishu/{channelId}/callback` | コールバック URL パスの上書き |
| `apiBase` | いいえ | `https://open.feishu.cn` | Feishu Open API のベース URL |

## 暗号化

`encryptKey` が設定されている場合、コールバックのボディは `{"encrypt":"<base64>"}` として届きます。アダプターはこれを自動的に復号し(SHA-256 による鍵導出を伴う AES-256-CBC)、`X-Lark-Signature` ヘッダーを検証します。

## メッセージフロー

**インバウンド:** `FeishuCallbackController` → 任意の復号処理 → URL 検証チェック → event_id の重複排除 → `FeishuInboundMapper`(MVP ではテキストメッセージのみ) → bot ループガード → Gateway。

**アウトバウンド:** `FeishuOutboundClient` が `FeishuAccessTokenProvider` から取得した `tenant_access_token` を用いて `POST /open-apis/im/v1/messages` で返信を送信します。トークンはキャッシュされ、TTL の約 80% の時点で先回りして更新されます。
