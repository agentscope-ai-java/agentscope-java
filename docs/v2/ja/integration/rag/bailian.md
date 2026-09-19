---
title: Bailian Knowledge
---

`agentscope-extensions-rag-bailian` はアリババクラウドの Bailian ナレッジベースと統合します。embedding、インデックス作成、検索はすべて Bailian 側で管理されます。Agent はクエリを送信し、ドキュメントを受け取るだけです。

## いつ使うか

- ドキュメントがすでに Bailian コンソールにアップロードされ、処理済みである。
- リランク、フィルタリング、構造化 / 非構造化 / 画像 KB といったエンタープライズ機能を使いたい。
- 自前のベクトルストアを運用したくない。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-rag-bailian</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## クイックスタート

```java
import io.agentscope.core.rag.integration.bailian.BailianConfig;
import io.agentscope.core.rag.integration.bailian.BailianKnowledge;
import io.agentscope.core.rag.model.RetrieveConfig;

BailianConfig config = BailianConfig.builder()
    .accessKeyId(System.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID"))
    .accessKeySecret(System.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET"))
    .workspaceId("llm-xxxxxx")
    .indexId("kb-xxxxxx")
    .build();

BailianKnowledge knowledge = BailianKnowledge.builder()
    .config(config)
    .build();

List<Document> hits = knowledge.retrieve(
    "How do I request an invoice?",
    RetrieveConfig.builder().limit(5).scoreThreshold(0.5).build()
).block();
```

## Agent への組み込み

```java
ReActAgent agent = ReActAgent.builder()
    .name("Assistant")
    .model(chatModel)
    .knowledge(knowledge)
    .ragMode(RAGMode.AGENTIC)
    .build();
```

またはツールとして公開します。

```java
KnowledgeRetrievalTools tools = new KnowledgeRetrievalTools(knowledge);
Toolkit toolkit = new Toolkit();
toolkit.registerObject(tools);
```

## リランク / クエリ書き換え

`BailianConfig.builder()` はオプションで `rerankConfig(...)` と `rewriteConfig(...)` を受け付けます。

```java
BailianConfig config = BailianConfig.builder()
    .accessKeyId(ak).accessKeySecret(sk)
    .workspaceId("llm-xxx").indexId("kb-xxx")
    .rerankConfig(RerankConfig.builder().enable(true).topN(5).build())
    .rewriteConfig(RewriteConfig.builder().enable(true).build())
    .build();
```

有効にすると、Bailian は初期の再現結果をリランクしたり、より高い関連性のためにクエリを書き換えたりします — その分レイテンシとクォータ消費が増えます。実際に必要なものだけを有効にしてください。

## 検索専用

`BailianKnowledge.addDocuments(...)` はサポートされていません — ドキュメントの管理には Bailian コンソールまたはプラットフォーム SDK を使ってください。これは Dify、HayStack、RAGFlow の統合と一貫しています。サードパーティの RAG プラットフォームが取り込みの責任を持ち、Java 側は読み取りのみを行います。

## 設定

| フィールド | 備考 |
| --- | --- |
| `accessKeyId / accessKeySecret` | アリババクラウドの認証情報（必須） |
| `workspaceId` | Bailian ワークスペース ID（必須） |
| `indexId` | KB インデックス ID（必須） |
| `rerankConfig` | リランクの有効化フラグとパラメータ |
| `rewriteConfig` | クエリ書き換えの有効化フラグとパラメータ |
