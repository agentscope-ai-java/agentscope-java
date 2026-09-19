---
title: Dify Knowledge
---

`agentscope-extensions-rag-dify` は [Dify](https://dify.ai/) のデータセットと統合し、すでに Dify で維持しているナレッジベースを再利用します。

## いつ使うか

- チームが Dify でコンテンツとドキュメント管理を運用している。
- Dify の複数の検索モード（キーワード / セマンティック / ハイブリッド / 全文検索）を活用したい。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-rag-dify</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## クイックスタート

```java
import io.agentscope.core.rag.integration.dify.DifyKnowledge;
import io.agentscope.core.rag.integration.dify.DifyRAGConfig;
import io.agentscope.core.rag.integration.dify.RetrievalMode;
import io.agentscope.core.rag.model.RetrieveConfig;

DifyRAGConfig config = DifyRAGConfig.builder()
    .apiKey(System.getenv("DIFY_RAG_API_KEY"))
    .datasetId("your-dataset-id")
    .retrievalMode(RetrievalMode.HYBRID_SEARCH)
    .enableRerank(true)
    .build();

DifyKnowledge knowledge = DifyKnowledge.builder()
    .config(config)
    .build();

List<Document> hits = knowledge.retrieve(
    "How do I renew my membership?",
    RetrieveConfig.builder().limit(5).scoreThreshold(0.5).build()
).block();
```

## 検索モード

`RetrievalMode` は Dify がデータセットをどのように検索するかを選択します。

| Enum | 説明 |
| --- | --- |
| `KEYWORD_SEARCH` | キーワードのみ |
| `SEMANTIC_SEARCH` | ベクトル / セマンティックのみ |
| `HYBRID_SEARCH` | キーワード + ベクトルのハイブリッド（推奨） |
| `FULL_TEXT_SEARCH` | 全文検索 |

## セルフホストの Dify

`baseUrl` を自分のデプロイ先に向けます。

```java
DifyRAGConfig config = DifyRAGConfig.builder()
    .apiKey("dataset-xxx")
    .baseUrl("https://dify.mycompany.com")
    .datasetId("ds-xxxx")
    .retrievalMode(RetrievalMode.HYBRID_SEARCH)
    .build();
```

## メタデータフィルタリング

Dify 側で設定済みのメタデータフィールドでフィルタするには `MetadataFilter / MetadataFilterCondition` を使います。

```java
DifyRAGConfig config = DifyRAGConfig.builder()
    .apiKey(apiKey)
    .datasetId(datasetId)
    .retrievalMode(RetrievalMode.HYBRID_SEARCH)
    .metadataFilter(MetadataFilter.builder()
        .conditions(List.of(
            MetadataFilterCondition.builder()
                .name("category").comparisonOperator("=")
                .value(List.of("faq")).build()))
        .logicalOperator("and")
        .build())
    .build();
```

## 検索専用

`addDocuments(...)` はサポートされていません — Dify コンソールを使ってください。ログイン → Knowledge → データセットを選択 → ドキュメントをアップロード → インデックス作成を待つ、という流れです。これは Bailian、HayStack、RAGFlow と同様です。

## 主要パラメータ

| フィールド | 備考 |
| --- | --- |
| `apiKey` | Dify データセット API キー（必須） |
| `datasetId` | データセット ID（必須） |
| `baseUrl` | デフォルトは `https://api.dify.ai/v1`、セルフホストの場合は上書き |
| `retrievalMode` | 上の表を参照 |
| `enableRerank` | リランクを有効化 |
| `metadataFilter` | メタデータフィルタ条件 |
