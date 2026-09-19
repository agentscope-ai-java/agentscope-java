---
title: RAGFlow Knowledge
---

`agentscope-extensions-rag-ragflow`는 [RAGFlow](https://ragflow.io/)와 통합됩니다. RAGFlow는 문서 파싱(OCR, 표 추출, 지식 그래프 증강)에 강점이 있으며, 비구조화 데이터 비중이 높은 KB에 적합합니다.

## 사용 시기

- KB가 대부분 스캔된 PDF, 복잡한 레이아웃, 또는 이미지와 표로 이루어진 경우.
- RAGFlow의 청킹 전략과 리랭커를 사용하고 싶은 경우.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-rag-ragflow</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 빠른 시작

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

## 동작 방식

이 플러그인은 RAGFlow의 `POST /api/v1/datasets/{dataset_id}/retrieve-chunks`를 호출합니다.

- 구성 가능한 `topK`를 사용한 벡터 유사도 검색.
- 서버 측 `similarityThreshold` 필터링.
- `RAGFlowConfig`를 통한 메타데이터 필터링.
- 선택적인 RAGFlow rerank.

> 참고: RAGFlow의 retrieve-chunks API는 현재 컨텍스트 인식 검색을 위한 대화 이력을 받지 않습니다. 이 기능이 필요하다면 쿼리 앞에 직접 지시문을 추가하세요.

## 문서 관리는 RAGFlow를 통해 이루어짐

`addDocuments(...)`는 지원되지 않습니다 — RAGFlow 콘솔이나 네이티브 API를 통해 문서를 업로드하고 인덱싱하세요.

## 주요 파라미터

| Field | Notes |
| --- | --- |
| `apiKey` | RAGFlow API 키 (필수) |
| `baseUrl` | RAGFlow 서비스 URL (필수) |
| `knowledgeBaseId` | 데이터셋 / KB ID (필수) |
| `topK` | 서버 측 top-K |
| `similarityThreshold` | 서버 측 최소 유사도 |
| `enableRerank` | Rerank 활성화 |
