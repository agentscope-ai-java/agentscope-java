---
title: Simple Knowledge
---

`agentscope-extensions-rag-simple`은 "DIY 엔드투엔드" RAG 구현체입니다: 문서 리더, 청킹 전략, 임베딩 어댑터, 그리고 다섯 가지 즉시 사용 가능한 벡터 스토어 어댑터를 제공합니다.

사용 시기: 임베딩 + 벡터 스토어를 직접 운영해도 괜찮고, 서드파티 RAG 플랫폼을 원하지 않는 경우.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-rag-simple</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 빠른 시작

```java
import io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.store.InMemoryStore;
import io.agentscope.core.rag.model.RetrieveConfig;

// 1) Embedding model
EmbeddingModel embeddings = DashScopeTextEmbedding.builder()
    .apiKey(System.getenv("DASHSCOPE_API_KEY"))
    .modelName("text-embedding-v3")
    .dimensions(1024)
    .build();

// 2) Vector store (in-process here)
VDBStoreBase store = InMemoryStore.builder().dimensions(1024).build();

// 3) Assemble Knowledge
SimpleKnowledge knowledge = SimpleKnowledge.builder()
    .embeddingModel(embeddings)
    .embeddingStore(store)
    .build();

// 4) Ingest documents
List<Document> docs = new TikaReader().read(input).block();
knowledge.addDocuments(docs).block();

// 5) Retrieve
List<Document> hits = knowledge.retrieve(
    "What is AgentScope?",
    RetrieveConfig.builder().limit(5).scoreThreshold(0.5).build()
).block();
```

## 내장 문서 리더

`io.agentscope.core.rag.reader` 패키지에는 일반적인 형식을 위한 리더가 포함되어 있으며, 각각 `List<Document>`를 생성합니다.

| Reader | Input |
| --- | --- |
| `TextReader` | 일반 텍스트 |
| `PDFReader` | PDF (PDFBox 기반) |
| `WordReader` | Microsoft Word 문서 |
| `ImageReader` | 이미지, 멀티모달 임베딩과 함께 사용 |
| `TikaReader` | 범용 Apache Tika 폴백 |
| `ExternalApiReader` | 외부 파서 API (OCR / 커스텀 파이프라인) |

결과로 생성되는 `Document` 객체는 이미 메타데이터를 포함하고 있습니다. 청킹을 위해 `TextChunker`와 `SplitStrategy`를 함께 사용하세요.

## 내장 임베딩 제공자

| Class | Service | Mode |
| --- | --- | --- |
| `DashScopeTextEmbedding` | Alibaba Cloud DashScope | 텍스트 |
| `DashScopeMultiModalEmbedding` | Alibaba Cloud DashScope | 멀티모달 (텍스트/이미지) |
| `OpenAITextEmbedding` | OpenAI 호환 API | 텍스트 |
| `OllamaTextEmbedding` | 로컬 Ollama | 텍스트 |

직접 만든 구현을 추가하려면 `EmbeddingModel`을 구현하세요.

## 내장 벡터 스토어

| Implementation | Deployment |
| --- | --- |
| `InMemoryStore` | 인프로세스 (개발 / 테스트) |
| `PgVectorStore` | PostgreSQL + pgvector |
| `MilvusStore` | Milvus |
| `QdrantStore` | Qdrant |
| `ElasticsearchStore` | Elasticsearch (`dense_vector`) |

스토어 전환은 한 줄만 바꾸면 됩니다: `SimpleKnowledge.builder().embeddingStore(...)`에 다른 `VDBStoreBase`를 전달하세요.

## 검색 파라미터

`RetrieveConfig`는 검색을 제어합니다.

| Field | Notes |
| --- | --- |
| `limit` | Top-K |
| `scoreThreshold` | 최소 점수 (0–1) |
| `metadata` | 문서 메타데이터로 필터링 |

## Agent에 연결하기

```java
ReActAgent agent = ReActAgent.builder()
    .name("Assistant")
    .model(model)
    .knowledge(knowledge)
    .ragMode(RAGMode.AGENTIC)   // Agent decides when to retrieve
    .build();
```
