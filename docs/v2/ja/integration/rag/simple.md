---
title: Simple Knowledge
---

`agentscope-extensions-rag-simple` は「DIY でエンドツーエンドに行う」RAG 実装です。ドキュメントリーダー、チャンク分割戦略、embedding アダプター、そして 5 種類のすぐ使えるベクトルストアアダプターがまとめて含まれています。

こんなときに使う: embedding とベクトルストアを自分で運用してもよく、サードパーティの RAG プラットフォームを使いたくない場合。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-rag-simple</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## クイックスタート

```java
import io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.store.InMemoryStore;
import io.agentscope.core.rag.model.RetrieveConfig;

// 1) Embedding モデル
EmbeddingModel embeddings = DashScopeTextEmbedding.builder()
    .apiKey(System.getenv("DASHSCOPE_API_KEY"))
    .modelName("text-embedding-v3")
    .dimensions(1024)
    .build();

// 2) ベクトルストア（ここではプロセス内のもの）
VDBStoreBase store = InMemoryStore.builder().dimensions(1024).build();

// 3) Knowledge を組み立てる
SimpleKnowledge knowledge = SimpleKnowledge.builder()
    .embeddingModel(embeddings)
    .embeddingStore(store)
    .build();

// 4) ドキュメントを取り込む
List<Document> docs = new TikaReader().read(input).block();
knowledge.addDocuments(docs).block();

// 5) 検索する
List<Document> hits = knowledge.retrieve(
    "What is AgentScope?",
    RetrieveConfig.builder().limit(5).scoreThreshold(0.5).build()
).block();
```

## 組み込みドキュメントリーダー

`io.agentscope.core.rag.reader` パッケージには、よく使われる形式向けのリーダーが用意されており、それぞれ `List<Document>` を生成します。

| リーダー | 入力 |
| --- | --- |
| `TextReader` | プレーンテキスト |
| `PDFReader` | PDF（PDFBox ベース） |
| `WordReader` | Microsoft Word ドキュメント |
| `ImageReader` | 画像、マルチモーダル embedding と組み合わせて使用 |
| `TikaReader` | 汎用の Apache Tika フォールバック |
| `ExternalApiReader` | 外部パーサー API（OCR / カスタムパイプライン） |

生成される `Document` オブジェクトにはすでにメタデータが付与されており、`TextChunker` と `SplitStrategy` を組み合わせてチャンク分割できます。

## 組み込み embedding プロバイダー

| クラス | サービス | モード |
| --- | --- | --- |
| `DashScopeTextEmbedding` | アリババクラウド DashScope | テキスト |
| `DashScopeMultiModalEmbedding` | アリババクラウド DashScope | マルチモーダル（テキスト/画像） |
| `OpenAITextEmbedding` | OpenAI 互換 API | テキスト |
| `OllamaTextEmbedding` | ローカル Ollama | テキスト |

独自のものを追加するには `EmbeddingModel` を実装してください。

## 組み込みベクトルストア

| 実装 | デプロイ形態 |
| --- | --- |
| `InMemoryStore` | プロセス内（開発 / テスト用） |
| `PgVectorStore` | PostgreSQL + pgvector |
| `MilvusStore` | Milvus |
| `QdrantStore` | Qdrant |
| `ElasticsearchStore` | Elasticsearch（`dense_vector`） |

ストアの切り替えは 1 行の変更で済みます。`SimpleKnowledge.builder().embeddingStore(...)` に別の `VDBStoreBase` を渡すだけです。

## 検索パラメータ

`RetrieveConfig` が検索を制御します。

| フィールド | 備考 |
| --- | --- |
| `limit` | Top-K |
| `scoreThreshold` | 最小スコア（0〜1） |
| `metadata` | ドキュメントのメタデータでフィルタ |

## Agent への組み込み

```java
ReActAgent agent = ReActAgent.builder()
    .name("Assistant")
    .model(model)
    .knowledge(knowledge)
    .ragMode(RAGMode.AGENTIC)   // Agent がいつ検索するかを判断する
    .build();
```
