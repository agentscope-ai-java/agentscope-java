---
title: インフラストラクチャ / ミドルウェア
---

これらの拡張機能は、すでに運用しているインフラストラクチャ — ゲートウェイ、レジストリ、メッセージバス、スケジューラ — に AgentScope を組み込み、Agent を他のサービスと同じようにガバナンスおよびスケジューリングできるようにします。

| 拡張機能 | ミドルウェア | 機能 |
| --- | --- | --- |
| [Higress](/v2/ja/integration/infrastructure/higress) | [Higress](https://higress.io/) AI ゲートウェイ | ゲートウェイ上で MCP として公開されたツールを Toolkit に取り込む |
| [Nacos](/v2/ja/integration/infrastructure/nacos) | [Nacos](https://nacos.io/) | A2A AgentCard のレジストリ/ディスカバリ、プロンプト設定センター、スキルリポジトリ |
| [Scheduler](/v2/ja/integration/infrastructure/scheduler) | XXL-Job / Quartz | CRON スケジュールまたは固定レートで Agent を実行する |

## 位置付け

- **Higress / Nacos** は「ゲートウェイ、レジストリ」を AgentScope の横断的な機能に変えます。
- **Scheduler** は「Agent は常に人間によってトリガーされるわけではなく、スケジューラによってトリガーされることもある」というユースケースに対応します。

これらの拡張機能は独立しており、自由に組み合わせることができます。
