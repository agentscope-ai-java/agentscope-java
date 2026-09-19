---
title: Bailian Knowledge
---

`agentscope-extensions-rag-bailian`은 Alibaba Cloud Bailian Knowledge Base와 통합됩니다 — 임베딩, 인덱싱, 검색은 모두 Bailian이 관리합니다. Agent는 쿼리만 전송하고 문서를 돌려받습니다.

## 사용 시기

- 문서가 이미 Bailian 콘솔에 업로드되어 처리된 경우.
- rerank, 필터링, 구조화 / 비구조화 / 이미지 KB 등 엔터프라이즈 기능을 원하는 경우.
- 직접 벡터 스토어를 운영하고 싶지 않은 경우.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-rag-bailian</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 빠른 시작

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

## Agent에 연결하기

```java
ReActAgent agent = ReActAgent.builder()
    .name("Assistant")
    .model(chatModel)
    .knowledge(knowledge)
    .ragMode(RAGMode.AGENTIC)
    .build();
```

또는 도구로 노출할 수도 있습니다.

```java
KnowledgeRetrievalTools tools = new KnowledgeRetrievalTools(knowledge);
Toolkit toolkit = new Toolkit();
toolkit.registerObject(tools);
```

## Rerank / rewrite

`BailianConfig.builder()`는 선택적으로 `rerankConfig(...)`와 `rewriteConfig(...)`를 받습니다.

```java
BailianConfig config = BailianConfig.builder()
    .accessKeyId(ak).accessKeySecret(sk)
    .workspaceId("llm-xxx").indexId("kb-xxx")
    .rerankConfig(RerankConfig.builder().enable(true).topN(5).build())
    .rewriteConfig(RewriteConfig.builder().enable(true).build())
    .build();
```

활성화하면 Bailian이 초기 회수 결과를 재순위화하거나 더 나은 관련성을 위해 쿼리를 재작성합니다 — 다만 지연 시간과 쿼터 사용량이 늘어납니다. 실제로 필요한 기능만 켜세요.

## 검색 전용

`BailianKnowledge.addDocuments(...)`는 지원되지 않습니다 — 문서 관리는 Bailian 콘솔이나 플랫폼 SDK를 사용하세요. 이는 Dify, HayStack, RAGFlow 통합과 동일합니다: 서드파티 RAG 플랫폼이 수집 책임을 가지며, Java 측은 읽기만 수행합니다.

## 구성

| Field | Notes |
| --- | --- |
| `accessKeyId / accessKeySecret` | Alibaba Cloud 자격 증명 (필수) |
| `workspaceId` | Bailian 워크스페이스 ID (필수) |
| `indexId` | KB 인덱스 ID (필수) |
| `rerankConfig` | Rerank 토글 및 파라미터 |
| `rewriteConfig` | 쿼리 재작성 토글 및 파라미터 |
