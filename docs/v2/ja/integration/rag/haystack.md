# HayStack Knowledge

`agentscope-extensions-rag-haystack` は AgentScope を [HayStack](https://haystack.deepset.ai/) の RAG サービスに接続します。ドキュメント管理とインデックス作成は HayStack 側で行われ、AgentScope はその検索 API を呼び出すだけです。

## いつ使うか

- すでに HayStack 上で RAG（インデックス作成パイプライン、ChromaDB、リランカーなど）を運用している。
- HayStack のエンドツーエンドな検索機能を再利用したい。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-rag-haystack</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## クイックスタート

```java
import io.agentscope.core.rag.integration.haystack.HayStackConfig;
import io.agentscope.core.rag.integration.haystack.HayStackKnowledge;
import io.agentscope.core.rag.model.RetrieveConfig;

HayStackConfig config = HayStackConfig.builder()
    .baseUrl("http://localhost:8080")  // your HayStack service
    .topK(10)
    .build();

HayStackKnowledge knowledge = HayStackKnowledge.builder()
    .config(config)
    .build();

List<Document> hits = knowledge.retrieve(
    "What is AI?",
    RetrieveConfig.builder().limit(5).build()
).block();
```

## Agent への組み込み

```java
ReActAgent agent = ReActAgent.builder()
    .knowledge(knowledge)
    .ragMode(RAGMode.AGENTIC)
    .build();
```

## ドキュメント管理はここでは行わない

`addDocuments(...)` は `UnsupportedOperationException` をスローします。ドキュメントを追加・更新するには次の手順を行います。

1. ソースファイルを HayStack のパイプラインのソースディレクトリに配置する。
2. HayStack のインデックス作成パイプラインをトリガー / 再実行する。
3. インデックス作成が完了したら、このプラグインが新しいドキュメントを検索できるようになる。

この分離によって、両側でインデックス状態が不整合になることを防げます。

## 主要パラメータ

| フィールド | 備考 |
| --- | --- |
| `baseUrl` | HayStack サービスの URL（必須） |
| `topK` | デフォルトの top-K |
| `filterPolicy` | フィルタポリシー（`FilterPolicy` を参照） |

`HayStackConfig` は、あなたの HayStack デプロイの認証方式に合わせて、タイムアウト、カスタムヘッダー、API キーも公開しています。
