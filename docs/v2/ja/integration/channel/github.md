---
title: GitHub チャンネル
---

`agentscope-extensions-channel-github` は Agent を GitHub の issue や PR のコメントスレッドに接続します。誰かが issue や pull request にコメントすると、Agent は新しいコメントとして返信します。

## 利用シーン

- GitHub の issue や PR コメントに応答する AI 搭載 bot が欲しい場合。
- HMAC-SHA256 による webhook 署名検証と bot ループの自己検出が必要な場合。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-channel-github</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 前提条件

1. `issues:write` と `pull_requests:write` の権限を持つ**パーソナルアクセストークン**(PAT)を作成します。
2. リポジトリまたは組織に **webhook** を設定します:
   - Payload URL: `https://your-host/api/channels/github/{channelId}/webhook`
   - Content type: `application/json`
   - Secret: HMAC-SHA256 署名検証用の共有シークレット
   - Events: **Issue comments** と **Pull request review comments** を選択

## クイックスタート

```java
GitHubChannel channel = GitHubChannel.fromProperties(
    "my-github",
    ChannelConfig.of("my-github", "main"),
    Map.of(
        "token",         "ghp_xxxxxxxxxxxx",
        "webhookSecret", "your-webhook-secret"
    ));

GatewayBootstrap gw = GatewayBootstrap.builder()
    .agent("main", agent)
    .channel(channel)
    .build();

gw.start();   // resolves bot identity via GET /user, starts accepting webhooks
```

## 設定プロパティ

| プロパティ | 必須 | デフォルト | 説明 |
|----------|----------|---------|-------------|
| `token` | はい | — | REST API 用のパーソナルアクセストークン(PAT) |
| `webhookSecret` | はい | — | `X-Hub-Signature-256` 検証用の共有シークレット |
| `apiBase` | いいえ | `https://api.github.com` | REST API のベース(GitHub Enterprise Server の場合に設定) |
| `webhookPath` | いいえ | `/api/channels/github/{channelId}/webhook` | webhook URL パスの上書き |
| `botUserLogin` | いいえ | (自動解決) | ループ検出用の bot アカウントログインの上書き |

## bot ループ保護

起動時に、channel は設定された PAT で `GET /user` を呼び出して bot の GitHub user id を解決します。この id によって投稿された受信コメントは、無限の bot 間ループを防ぐために黙って破棄されます。

## メッセージフロー

**インバウンド:** `GitHubWebhookController` → HMAC-SHA256 署名検証 → イベントタイプのフィルタ(`issue_comment`、`pull_request_review_comment`) → comment.id の重複排除 → bot 自己コメントのフィルタ → `GitHubInboundMapper`(`action=created` のみ) → bot ループガード → Gateway。

**アウトバウンド:** `GitHubOutboundClient` が `POST /repos/{owner}/{repo}/issues/{number}/comments` を通じて返信を投稿します。

## ピアモデル

各 issue/PR スレッドは id `owner/repo#number` を持つ `THREAD` ピアとしてモデル化されます。これは、Gateway が issue/PR スレッドごとに 1 つのセッションを作成することを意味し、Agent はそのスレッド内の会話履歴全体を確認できます。
