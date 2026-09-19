---
title: GitLab チャンネル
---

`agentscope-extensions-channel-gitlab` は Agent を GitLab の note(コメント)フックに接続します。誰かが issue やマージリクエストにコメントすると、Agent は新しい note として返信します。

## 利用シーン

- GitLab の issue やマージリクエストのコメントに応答する AI 搭載 bot が欲しい場合。
- GitLab SaaS またはセルフマネージドの GitLab インスタンスを運用している場合。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-channel-gitlab</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 前提条件

1. `api` スコープを持つ**パーソナルアクセストークン**(または Project/Group Access Token)を作成します。
2. プロジェクトまたはグループに **webhook** を設定します:
   - URL: `https://your-host/api/channels/gitlab/{channelId}/webhook`
   - Secret token: (署名検証用、任意)
   - Trigger: **Note events**

## クイックスタート

```java
GitLabChannel channel = GitLabChannel.fromProperties(
    "my-gitlab",
    ChannelConfig.of("my-gitlab", "main"),
    Map.of(
        "token", "glpat-xxxxxxxxxxxx"
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
| `token` | はい | — | GitLab API 用のアクセストークン |
| `apiBase` | いいえ | `https://gitlab.com` | API のベース URL(セルフマネージドインスタンスの場合に設定) |
| `webhookPath` | いいえ | `/api/channels/gitlab/{channelId}/webhook` | webhook URL パスの上書き |

## bot ループ保護

起動時に、channel は `GET /api/v4/user` を呼び出して bot の GitLab user id を解決します。bot によって投稿された受信 note は、無限ループを防ぐために破棄されます。

## メッセージフロー

**インバウンド:** `GitLabWebhookController` → note.id の重複排除 → bot 自己 note のフィルタ → `GitLabInboundMapper` → bot ループガード → Gateway。

**アウトバウンド:** `GitLabOutboundClient` が GitLab の Notes API を通じて note として返信を投稿します。
