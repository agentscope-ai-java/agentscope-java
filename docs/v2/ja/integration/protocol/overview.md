---
title: Agent Protocols
---

AgentScope では、Agent が外部と対話できるようにするためのプロトコルアダプターをいくつか提供しています。それぞれが解決する課題は異なります。

| 拡張機能 | プロトコル | 解決する課題 |
| --- | --- | --- |
| [A2A](/v2/ja/integration/protocol/a2a) | [Agent-to-Agent](https://a2aproject.github.io/A2A/) | エージェント同士の呼び出し / マルチエージェントワークフローへの合成 |
| [AG-UI](/v2/ja/integration/protocol/agui) | [AG-UI Protocol](https://github.com/ag-ui-protocol/ag-ui) | フロントエンド UI 向けの標準化されたイベントストリーム |
| [Agent Protocol](/v2/ja/integration/protocol/agent-protocol) | [Agent Protocol](https://agentprotocol.ai/) | 他のシステムが「タスク」を投入するための HTTP ベースの手段 |

> [Chat Completions Web](/v2/ja/integration/ecosystem/chat-completions-web) も参照してください: Agent を OpenAI 互換の API の背後に公開します。

## どれを選ぶか

- **エージェントのイベント（ThinkingBlock を含む）をフロントエンド UI にストリーミングしたい** → AG-UI
- **他のバックエンドシステムが REST 経由で Agent をスケジュールしたり、リモートサブエージェントをホストしたりできるようにしたい** → Agent Protocol
- **複数の Agent（自分のものやサードパーティのもの）を互いに呼び出せるようにしたい** → A2A

> レイヤー構成: AG-UI はユーザー向け、Agent Protocol は内部のリモートサブエージェント / タスク HTTP サーフェス、A2A は外部との相互運用です。
