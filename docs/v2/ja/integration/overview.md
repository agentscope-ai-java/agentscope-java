---
title: Integration 概要
---

このセクションでは、サードパーティ システムやエコシステムのサービスに接続する AgentScope Java の拡張機能をまとめています。各拡張機能は `agentscope-extensions/` 配下の独立した Maven モジュールです — 必要なものだけを取り込んでください。

拡張機能はトピックごとにグループ化されています。

## モデルプロバイダー

すべてのモデルプロバイダーは独立したモデル拡張モジュールへ移行しており、`agentscope-core` は共有のモデル契約のみを保持しています。作成方法の全パス、Spring Boot のセットアップ、フォーマッター、資格情報、レジストリの高度な挙動については [Model](/v2/ja/docs/building-blocks/model) を参照してください。

| プロバイダー | Maven アーティファクト | `ModelRegistry` id | 標準環境変数 | ドキュメント |
|----------|----------------|--------------------|-------------------------------|------|
| OpenAI | `agentscope-extensions-model-openai` | `openai:<model>` | `OPENAI_API_KEY` | <a class="reference internal" href="/v2/ja/integration/model/openai">OpenAI</a> |
| DeepSeek | `agentscope-extensions-model-openai` | `deepseek:<model>` | `DEEPSEEK_API_KEY` | <a class="reference internal" href="/v2/ja/integration/model/deepseek">DeepSeek</a> |
| GLM | `agentscope-extensions-model-openai` | `glm:<model>` | `ZAI_API_KEY` / `GLM_API_KEY` / `ZHIPUAI_API_KEY` | <a class="reference internal" href="/v2/ja/integration/model/glm">GLM</a> |
| Kimi | `agentscope-extensions-model-openai` | `kimi:<model>` | `MOONSHOT_API_KEY` / `KIMI_API_KEY` | <a class="reference internal" href="/v2/ja/integration/model/kimi">Kimi</a> |
| MiniMax | `agentscope-extensions-model-openai` | `minimax:<model>` | `MINIMAX_API_KEY` | <a class="reference internal" href="/v2/ja/integration/model/minimax">MiniMax</a> |
| DashScope | `agentscope-extensions-model-dashscope` | `dashscope:<model>` / `qwen*` | `DASHSCOPE_API_KEY` | <a class="reference internal" href="/v2/ja/integration/model/dashscope">DashScope</a> |
| Gemini | `agentscope-extensions-model-gemini` | `gemini:<model>` | `GEMINI_API_KEY` | <a class="reference internal" href="/v2/ja/integration/model/gemini">Gemini</a> |
| Anthropic | `agentscope-extensions-model-anthropic` | `anthropic:<model>` | `ANTHROPIC_API_KEY` | <a class="reference internal" href="/v2/ja/integration/model/anthropic">Anthropic</a> |
| Ollama | `agentscope-extensions-model-ollama` | `ollama:<model>` | `OLLAMA_BASE_URL`（任意） | <a class="reference internal" href="/v2/ja/integration/model/ollama">Ollama</a> |

<Note>

`agentscope-extensions-model-e2e-tests` はリポジトリのテストモジュールであり、ユーザー向けのモデル統合用依存関係ではありません。

</Note>

## 分散ストレージ (Distributed Store)

マルチレプリカの本番デプロイ向けのフルスタック分散ストレージコンポーネント。単一の `DistributedStore` で、Agent の状態、ワークスペースファイルシステム、サンドボックススナップショット、同時実行ロックを設定できます。

- [分散ストレージ概要](/v2/ja/integration/distributed/index) — `DistributedStore` API、機能マトリクス、混在ストア
- [Redis](/v2/ja/integration/distributed/redis) — `AgentStateStore` + `BaseStore` + `SandboxSnapshotSpec` + `SandboxExecutionGuard`
- [MySQL / JDBC](/v2/ja/integration/distributed/mysql) — `AgentStateStore` + `JdbcStore` + `JdbcSnapshotSpec` + `JdbcSandboxExecutionGuard`
- [Alibaba Cloud OSS](/v2/ja/integration/distributed/oss) — `AgentStateStore` + `OssBaseStore` + `OssSnapshotSpec`

## サンドボックス実行環境

隔離されたコード実行ストア。Docker は組み込みで、それ以外はスタンドアロンの拡張モジュールです。

- Docker — 組み込みのデフォルト、追加の依存関係不要
- [Kubernetes](/v2/ja/docs/harness/sandbox) — `agentscope-extensions-sandbox-kubernetes`
- [AgentRun (Alibaba Cloud)](/v2/ja/docs/harness/sandbox) — `agentscope-extensions-sandbox-agentrun`
- [Daytona](/v2/ja/docs/harness/sandbox) — `agentscope-extensions-sandbox-daytona`
- [E2B](/v2/ja/docs/harness/sandbox) — `agentscope-extensions-sandbox-e2b`

## メモリ

セッションをまたいでユーザーの好みや事実を永続化します。すべての実装は `LongTermMemory` インターフェースを満たしています。

- [Mem0](/v2/ja/integration/memory/mem0)
- [Bailian Memory](/v2/ja/integration/memory/bailian)
- [ReMe](/v2/ja/integration/memory/reme)

## RAG ナレッジベース

統一された `Knowledge` インターフェースの背後に、さまざまな検索ストアをプラグインできます。

- [Simple (DIY embedding + vector store)](/v2/ja/integration/rag/simple)
- [Bailian Knowledge](/v2/ja/integration/rag/bailian)
- [Dify](/v2/ja/integration/rag/dify)
- [HayStack](/v2/ja/integration/rag/haystack)
- [RAGFlow](/v2/ja/integration/rag/ragflow)

## Skill リポジトリ

`AgentSkillRepository` の複数のストレージ実装。

- [Git Skill Repository](/v2/ja/integration/skill/git-repository)
- [MySQL Skill Repository](/v2/ja/integration/skill/mysql-repository)
- [PostgreSQL Skill Repository](/v2/ja/integration/skill/postgresql-repository)
- [Nacos Skill Repository](/v2/ja/integration/infrastructure/nacos#スキルリポジトリ) も参照

## チャネルアダプター

Harness Channel インターフェースを通じて Agent をメッセージングプラットフォームに接続します。

- [DingTalk](/v2/ja/integration/channel/dingtalk)
- [Feishu / Lark](/v2/ja/integration/channel/feishu)
- [GitHub](/v2/ja/integration/channel/github)
- [GitLab](/v2/ja/integration/channel/gitlab)
- [WeCom](/v2/ja/integration/channel/wecom)

## Agent プロトコル

Agent が外部と対話するための標準化された方法。

- [A2A (Agent-to-Agent)](/v2/ja/integration/protocol/a2a)
- [AG-UI](/v2/ja/integration/protocol/agui)
- [Agent Protocol](/v2/ja/integration/protocol/agent-protocol)

## インフラストラクチャ / ミドルウェア

Agent をエンタープライズインフラストラクチャに組み込みます。

- [Higress AI Gateway](/v2/ja/integration/infrastructure/higress)
- [Nacos](/v2/ja/integration/infrastructure/nacos)
- [Scheduler (Quartz / XXL-Job)](/v2/ja/integration/infrastructure/scheduler)

## エコシステム

ランタイム、言語、デバッグ、トレーニング関連の拡張機能。

- [Chat Completions Web](/v2/ja/integration/ecosystem/chat-completions-web)
- [AgentScope Studio](/v2/ja/integration/ecosystem/studio)
- [Online Training](/v2/ja/integration/ecosystem/training)

<Note>

Spring Boot ユーザー向けに、上記の拡張機能のほとんどは、手動での配線を不要にするワンライン統合用の `agentscope-spring-boot-starter-*` を提供しています。

</Note>
