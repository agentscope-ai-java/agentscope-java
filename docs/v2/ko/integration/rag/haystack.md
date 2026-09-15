# HayStack Knowledge

`agentscope-extensions-rag-haystack`은 AgentScope를 [HayStack](https://haystack.deepset.ai/) RAG 서비스에 연결합니다. 문서 관리와 인덱싱은 HayStack 측에서 이루어지며, AgentScope는 그 검색 API만 호출합니다.

## 사용 시기

- 이미 HayStack에서 RAG를 운영 중인 경우 (인덱싱 파이프라인, ChromaDB, 리랭커 등).
- HayStack의 엔드투엔드 검색 기능을 재사용하고 싶은 경우.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-rag-haystack</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 빠른 시작

```java
import io.agentscope.core.rag.integration.haystack.HayStackConfig;
import io.agentscope.core.rag.integration.haystack.HayStackKnowledge;
import io.agentscope.core.rag.model.RetrieveConfig;

HayStackConfig config = HayStackConfig.builder()
    .baseUrl("http://localhost:8080")  // your HayStack service
    .topK(10)
    .build();

HayStackKnowledge knowledge = HayStackKnowledge.builder()
    .config(config)
    .build();

List<Document> hits = knowledge.retrieve(
    "What is AI?",
    RetrieveConfig.builder().limit(5).build()
).block();
```

## Agent에 연결하기

```java
ReActAgent agent = ReActAgent.builder()
    .knowledge(knowledge)
    .ragMode(RAGMode.AGENTIC)
    .build();
```

## 문서 관리는 여기서 처리하지 않음

`addDocuments(...)`는 `UnsupportedOperationException`을 던집니다. 문서를 추가하거나 업데이트하려면 다음을 따르세요.

1. HayStack 파이프라인의 소스 디렉터리에 원본 파일을 배치합니다.
2. HayStack의 인덱싱 파이프라인을 트리거 / 재실행합니다.
3. 인덱싱이 완료되면 이 플러그인이 새 문서를 검색할 수 있습니다.

이러한 분리는 양측 간 인덱싱 상태 불일치를 방지합니다.

## 주요 파라미터

| Field | Notes |
| --- | --- |
| `baseUrl` | HayStack 서비스 URL (필수) |
| `topK` | 기본 top-K |
| `filterPolicy` | 필터 정책 (`FilterPolicy` 참고) |

`HayStackConfig`는 여러분의 HayStack 배포 인증 방식에 맞게 타임아웃, 커스텀 헤더, API 키도 제공합니다.
