---
title: RAG 지식 베이스
---

`io.agentscope.core.rag.Knowledge`는 외부 지식 베이스를 연결하기 위한 AgentScope의 인터페이스입니다. Agent는 추론 중에 이 인터페이스를 사용하여 문서를 검색하고, 검색된 문서는 모델에 전달됩니다. `agentscope-extensions-*` 저장소는 여러 구현체를 제공합니다.

| Extension | Type | Best for |
| --- | --- | --- |
| [Simple](/v2/ko/integration/rag/simple) | 자체 관리: 임베딩 + 벡터 스토어 | 직접 벡터 스토어를 가져오는 경우 (PgVector / Milvus / Qdrant / Elasticsearch / in-memory) |
| [Bailian](/v2/ko/integration/rag/bailian) | Alibaba Cloud Bailian Knowledge Base | Bailian이 호스팅하는 엔터프라이즈 KB 사용 |
| [Dify](/v2/ko/integration/rag/dify) | Dify dataset | 이미 Dify에서 KB 콘텐츠를 관리하고 있는 경우 |
| [HayStack](/v2/ko/integration/rag/haystack) | 셀프 호스팅 HayStack RAG | 기존 HayStack 파이프라인 |
| [RAGFlow](/v2/ko/integration/rag/ragflow) | RAGFlow service | OCR / 지식 그래프가 필요한 복잡한 문서 |

## 어디서나 동일한 연결 방식

```java
ReActAgent agent = ReActAgent.builder()
    .name("Assistant")
    .model(model)
    .knowledge(knowledge)        // any Knowledge implementation
    .ragMode(RAGMode.AGENTIC)    // or STATIC, NONE
    .build();
```

Agent가 명시적으로 호출하는 도구로 검색 기능을 노출할 수도 있습니다.

```java
KnowledgeRetrievalTools tools = new KnowledgeRetrievalTools(knowledge);
Toolkit toolkit = new Toolkit();
toolkit.registerObject(tools);
```

## 선택 가이드

| Need | Recommendation |
| --- | --- |
| 임베딩 + 스토어에 대한 완전한 제어 | **Simple** |
| Alibaba Cloud 생태계, 엔터프라이즈 호스팅 | **Bailian** |
| 팀이 이미 Dify에서 콘텐츠를 관리 중 | **Dify** |
| 복잡한 ETL (PDF 표, OCR, 지식 그래프) | **RAGFlow** |
| 기존 HayStack RAG 파이프라인 | **HayStack** |

> Simple을 제외하면, 플랫폼 기반 구현체는 **검색 전용**입니다. 문서 수집 / 업데이트는 해당 플랫폼의 콘솔이나 네이티브 API를 통해 이루어집니다. 이를 통해 Agent 측에서 `Knowledge` 구현체를 자유롭게 교체할 수 있습니다.
