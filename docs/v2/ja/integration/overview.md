# Integration 概要

このセクションでは、サードパーティ システムやエコシステムのサービスに接続する AgentScope Java の拡張機能をまとめています。各拡張機能は `agentscope-extensions/` 配下の独立した Maven モジュールです — 必要なものだけを取り込んでください。

拡張機能はトピックごとにグループ化されています。

## モデルプロバイダー

すべてのモデルプロバイダーは独立したモデル拡張モジュールへ移行しており、`agentscope-core` は共有のモデル契約のみを保持しています。作成方法の全パス、Spring Boot のセットアップ、フォーマッター、資格情報、レジストリの高度な挙動については [Model](../docs/building-blocks/model.md) を参照してください。

| プロバイダー | Maven アーティファクト | `ModelRegistry` id | 標準環境変数 | ドキュメント |
|----------|----------------|--------------------|-------------------------------|------|
| OpenAI | `agentscope-extensions-model-openai` | `openai:<model>` | `OPENAI_API_KEY` | <a class="reference internal" href="model/openai.html">OpenAI</a> |
| DeepSeek | `agentscope-extensions-model-openai` | `deepseek:<model>` | `DEEPSEEK_API_KEY` | <a class="reference internal" href="model/deepseek.html">DeepSeek</a> |
| GLM | `agentscope-extensions-model-openai` | `glm:<model>` | `ZAI_API_KEY` / `GLM_API_KEY` / `ZHIPUAI_API_KEY` | <a class="reference internal" href="model/glm.html">GLM</a> |
| Kimi | `agentscope-extensions-model-openai` | `kimi:<model>` | `MOONSHOT_API_KEY` / `KIMI_API_KEY` | <a class="reference internal" href="model/kimi.html">Kimi</a> |
| MiniMax | `agentscope-extensions-model-openai` | `minimax:<model>` | `MINIMAX_API_KEY` | <a class="reference internal" href="model/minimax.html">MiniMax</a> |
| DashScope | `agentscope-extensions-model-dashscope` | `dashscope:<model>` / `qwen*` | `DASHSCOPE_API_KEY` | <a class="reference internal" href="model/dashscope.html">DashScope</a> |
| Gemini | `agentscope-extensions-model-gemini` | `gemini:<model>` | `GEMINI_API_KEY` | <a class="reference internal" href="model/gemini.html">Gemini</a> |
| Anthropic | `agentscope-extensions-model-anthropic` | `anthropic:<model>` | `ANTHROPIC_API_KEY` | <a class="reference internal" href="model/anthropic.html">Anthropic</a> |
| Ollama | `agentscope-extensions-model-ollama` | `ollama:<model>` | `OLLAMA_BASE_URL`（任意） | <a class="reference internal" href="model/ollama.html">Ollama</a> |

```{note}
`agentscope-extensions-model-e2e-tests` はリポジトリのテストモジュールであり、ユーザー向けのモデル統合用依存関係ではありません。
```

## 分散ストレージ (Distributed Store)

マルチレプリカの本番デプロイ向けのフルスタック分散ストレージコンポーネント。単一の `DistributedStore` で、Agent の状態、ワークスペースファイルシステム、サンドボックススナップショット、同時実行ロックを設定できます。

- [分散ストレージ概要](distributed/index.md) — `DistributedStore` API、機能マトリクス、混在ストア
- [Redis](distributed/redis.md) — `AgentStateStore` + `BaseStore` + `SandboxSnapshotSpec` + `SandboxExecutionGuard`
- [MySQL / JDBC](distributed/mysql.md) — `AgentStateStore` + `JdbcStore` + `JdbcSnapshotSpec` + `JdbcSandboxExecutionGuard`
- [Alibaba Cloud OSS](distributed/oss.md) — `AgentStateStore` + `OssBaseStore` + `OssSnapshotSpec`

## サンドボックス実行環境

隔離されたコード実行ストア。Docker は組み込みで、それ以外はスタンドアロンの拡張モジュールです。

- Docker — 組み込みのデフォルト、追加の依存関係不要
- [Kubernetes](../docs/harness/sandbox.md) — `agentscope-extensions-sandbox-kubernetes`
- [AgentRun (Alibaba Cloud)](../docs/harness/sandbox.md) — `agentscope-extensions-sandbox-agentrun`
- [Daytona](../docs/harness/sandbox.md) — `agentscope-extensions-sandbox-daytona`
- [E2B](../docs/harness/sandbox.md) — `agentscope-extensions-sandbox-e2b`

## メモリ

セッションをまたいでユーザーの好みや事実を永続化します。すべての実装は `LongTermMemory` インターフェースを満たしています。

- [Mem0](memory/mem0.md)
- [Bailian Memory](memory/bailian.md)
- [ReMe](memory/reme.md)

## RAG ナレッジベース

統一された `Knowledge` インターフェースの背後に、さまざまな検索ストアをプラグインできます。

- [Simple (DIY embedding + vector store)](rag/simple.md)
- [Bailian Knowledge](rag/bailian.md)
- [Dify](rag/dify.md)
- [HayStack](rag/haystack.md)
- [RAGFlow](rag/ragflow.md)

## Skill リポジトリ

`AgentSkillRepository` の複数のストレージ実装。

- [Git Skill Repository](skill/git-repository.md)
- [MySQL Skill Repository](skill/mysql-repository.md)
- [PostgreSQL Skill Repository](skill/postgresql-repository.md)
- [Nacos Skill Repository](infrastructure/nacos.md#skill-repository) も参照

## チャネルアダプター

Harness Channel インターフェースを通じて Agent をメッセージングプラットフォームに接続します。

- [DingTalk](channel/dingtalk.md)
- [Feishu / Lark](channel/feishu.md)
- [GitHub](channel/github.md)
- [GitLab](channel/gitlab.md)
- [WeCom](channel/wecom.md)

## Agent プロトコル

Agent が外部と対話するための標準化された方法。

- [A2A (Agent-to-Agent)](protocol/a2a.md)
- [AG-UI](protocol/agui.md)
- [Agent Protocol](protocol/agent-protocol.md)

## インフラストラクチャ / ミドルウェア

Agent をエンタープライズインフラストラクチャに組み込みます。

- [Higress AI Gateway](infrastructure/higress.md)
- [Nacos](infrastructure/nacos.md)
- [Scheduler (Quartz / XXL-Job)](infrastructure/scheduler.md)

## エコシステム

ランタイム、言語、デバッグ、トレーニング関連の拡張機能。

- [Chat Completions Web](ecosystem/chat-completions-web.md)
- [AgentScope Studio](ecosystem/studio.md)
- [Online Training](ecosystem/training.md)

```{note}
Spring Boot ユーザー向けに、上記の拡張機能のほとんどは、手動での配線を不要にするワンライン統合用の `agentscope-spring-boot-starter-*` を提供しています。
```
