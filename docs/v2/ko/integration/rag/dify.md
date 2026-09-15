# Dify Knowledge

`agentscope-extensions-rag-dify`는 [Dify](https://dify.ai/) 데이터셋과 통합되어, 이미 Dify에서 유지 관리 중인 지식 베이스를 재사용합니다.

## 사용 시기

- 팀이 Dify에서 콘텐츠와 문서 관리를 운영하는 경우.
- Dify의 다양한 검색 모드(키워드 / 시맨틱 / 하이브리드 / 전문 검색)를 활용하고 싶은 경우.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-rag-dify</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 빠른 시작

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

## 검색 모드

`RetrievalMode`는 Dify가 데이터셋을 검색하는 방식을 선택합니다.

| Enum | Description |
| --- | --- |
| `KEYWORD_SEARCH` | 키워드 전용 |
| `SEMANTIC_SEARCH` | 벡터 / 시맨틱 전용 |
| `HYBRID_SEARCH` | 키워드 + 벡터 하이브리드 (권장) |
| `FULL_TEXT_SEARCH` | 전문 검색 |

## 셀프 호스팅 Dify

`baseUrl`을 여러분의 배포 환경으로 지정하세요.

```java
DifyRAGConfig config = DifyRAGConfig.builder()
    .apiKey("dataset-xxx")
    .baseUrl("https://dify.mycompany.com")
    .datasetId("ds-xxxx")
    .retrievalMode(RetrievalMode.HYBRID_SEARCH)
    .build();
```

## 메타데이터 필터링

Dify에서 구성한 메타데이터 필드로 필터링하려면 `MetadataFilter / MetadataFilterCondition`을 사용하세요.

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

## 검색 전용

`addDocuments(...)`는 지원되지 않습니다 — Dify 콘솔을 사용하세요: 로그인 → Knowledge → 데이터셋 선택 → 문서 업로드 → 인덱싱 완료 대기. 이는 Bailian, HayStack, RAGFlow와 동일합니다.

## 주요 파라미터

| Field | Notes |
| --- | --- |
| `apiKey` | Dify 데이터셋 API 키 (필수) |
| `datasetId` | 데이터셋 ID (필수) |
| `baseUrl` | 기본값 `https://api.dify.ai/v1`, 셀프 호스팅 시 재정의 |
| `retrievalMode` | 위 표 참고 |
| `enableRerank` | Rerank 활성화 |
| `metadataFilter` | 메타데이터 필터 조건 |
