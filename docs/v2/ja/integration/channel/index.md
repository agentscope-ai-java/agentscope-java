---
title: チャンネルアダプター
---

これらの拡張機能は、Harness の [Channel](/v2/ja/docs/harness/channel) インターフェースを通じて Agent を実世界のメッセージングプラットフォームに接続します。各アダプターはプラットフォーム固有の認証、webhook 検証、メッセージ解析、返信配信を処理するため、Agent のコードはプラットフォームに依存しないまま保たれます。

| 拡張機能 | プラットフォーム | トランスポート |
| --- | --- | --- |
| [DingTalk](/v2/ja/integration/channel/dingtalk) | DingTalk (钉钉) | Stream プロトコル(永続 WebSocket) |
| [Feishu](/v2/ja/integration/channel/feishu) | Feishu / Lark (飞书) | イベント購読コールバック(HTTP) |
| [GitHub](/v2/ja/integration/channel/github) | GitHub | Webhook(HTTP) |
| [GitLab](/v2/ja/integration/channel/gitlab) | GitLab | Webhook(HTTP) |
| [WeCom](/v2/ja/integration/channel/wecom) | WeCom (企业微信) | 暗号化コールバック(HTTP) |

## 仕組み

各チャンネルアダプターは同じパターンに従います:

1. **インバウンド** — プラットフォームからメッセージを受信し(webhook、WebSocket など経由)、正規化された `InboundMessage` に解析し、重複を排除し、bot ループ保護を適用し、Gateway を通じて振り分けます。
2. **アウトバウンド** — プラットフォームの送信 API を通じて Agent の返信をプラットフォームに配信します。

すべてのアダプターは `agentscope-extensions-channel-common` の 2 つの共通ユーティリティを共有します:

- **IdempotencyStore** — メッセージ id によって再試行された webhook 配信の重複を排除します。
- **BotLoopGuard** — ピアごとのレートリミッターで、暴走する bot 間ループを防ぎます。

## 共有依存関係

すべてのチャンネルアダプターは `agentscope-extensions-channel-common`(推移的に含まれる)と `agentscope-harness`(アプリケーションによって実行時に提供される)に依存します。
