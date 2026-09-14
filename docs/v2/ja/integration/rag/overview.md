# RAG ナレッジベース

`io.agentscope.core.rag.Knowledge` は、外部のナレッジベースを組み込むための AgentScope のインターフェースです。Agent は推論の過程でこれを使ってドキュメントを検索し、そのドキュメントをモデルに渡します。`agentscope-extensions-*` リポジトリには複数の実装が用意されています。

| 拡張 | タイプ | 最適な用途 |
| --- | --- | --- |
| [Simple](simple.md) | セルフマネージド: embedding + ベクトルストア | 自前のベクトルストアを使う（PgVector / Milvus / Qdrant / Elasticsearch / インメモリ） |
| [Bailian](bailian.md) | アリババクラウド Bailian ナレッジベース | Bailian がホストするエンタープライズ KB を使う |
| [Dify](dify.md) | Dify データセット | すでに Dify で KB コンテンツを管理している |
| [HayStack](haystack.md) | セルフホスト HayStack RAG | 既存の HayStack パイプラインがある |
| [RAGFlow](ragflow.md) | RAGFlow サービス | OCR / ナレッジグラフが必要な複雑なドキュメント |

## どこでも同じ組み込み方

```java
ReActAgent agent = ReActAgent.builder()
    .name("Assistant")
    .model(model)
    .knowledge(knowledge)        // どの Knowledge 実装でも可
    .ragMode(RAGMode.AGENTIC)    // または STATIC, NONE
    .build();
```

また、検索を Agent が明示的に呼び出せるツールとして公開することもできます。

```java
KnowledgeRetrievalTools tools = new KnowledgeRetrievalTools(knowledge);
Toolkit toolkit = new Toolkit();
toolkit.registerObject(tools);
```

## どれを選ぶか

| ニーズ | おすすめ |
| --- | --- |
| embedding + ストアを完全にコントロールしたい | **Simple** |
| アリババクラウドのエコシステム、エンタープライズホスティング | **Bailian** |
| チームがすでに Dify でコンテンツを管理している | **Dify** |
| 複雑な ETL（PDF テーブル、OCR、ナレッジグラフ） | **RAGFlow** |
| 既存の HayStack RAG パイプライン | **HayStack** |

> Simple を除き、プラットフォームに依存する実装は**検索専用**です。ドキュメントの取り込み / 更新はプラットフォームのコンソールまたはネイティブ API 経由で行います。これにより、Agent 側では `Knowledge` 実装を差し替え可能な状態に保てます。
