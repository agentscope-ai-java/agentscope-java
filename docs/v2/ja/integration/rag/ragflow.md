# RAGFlow Knowledge

`agentscope-extensions-rag-ragflow` は [RAGFlow](https://ragflow.io/) と統合します。RAGFlow はドキュメント解析（OCR、テーブル抽出、ナレッジグラフによる拡張）に強く、非構造化データの多い KB で真価を発揮します。

## いつ使うか

- KB のほとんどがスキャンされた PDF、複雑なレイアウト、画像やテーブルである。
- RAGFlow のチャンク分割戦略とリランカーを使いたい。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-rag-ragflow</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## クイックスタート

```java
import io.agentscope.core.rag.integration.ragflow.RAGFlowConfig;
import io.agentscope.core.rag.integration.ragflow.RAGFlowKnowledge;
import io.agentscope.core.rag.model.RetrieveConfig;

RAGFlowConfig config = RAGFlowConfig.builder()
    .apiKey("ragflow-xxxxxxxx")
    .baseUrl("http://localhost:9380")
    .knowledgeBaseId("kb-xxxxx")
    .topK(10)
    .similarityThreshold(0.5)
    .enableRerank(true)
    .build();

RAGFlowKnowledge knowledge = RAGFlowKnowledge.builder()
    .config(config)
    .build();

List<Document> hits = knowledge.retrieve(
    "What is AI?",
    RetrieveConfig.builder().limit(5).build()
).block();
```

## 仕組み

このプラグインは RAGFlow の `POST /api/v1/datasets/{dataset_id}/retrieve-chunks` を呼び出します。

- 設定可能な `topK` によるベクトル類似度検索。
- サーバー側での `similarityThreshold` によるフィルタリング。
- `RAGFlowConfig` によるメタデータフィルタリング。
- オプションの RAGFlow リランク。

> 注: RAGFlow の retrieve-chunks API は現在、文脈を考慮した検索のための会話履歴を受け付けていません。それが必要な場合は、自分でクエリの先頭に指示を追加してください。

## ドキュメント管理は RAGFlow 経由で行う

`addDocuments(...)` はサポートされていません — ドキュメントのアップロードとインデックス作成は RAGFlow コンソールまたはそのネイティブ API を通じて行ってください。

## 主要パラメータ

| フィールド | 備考 |
| --- | --- |
| `apiKey` | RAGFlow API キー（必須） |
| `baseUrl` | RAGFlow サービスの URL（必須） |
| `knowledgeBaseId` | データセット / KB ID（必須） |
| `topK` | サーバー側の top-K |
| `similarityThreshold` | サーバー側の最小類似度 |
| `enableRerank` | リランクを有効化 |
