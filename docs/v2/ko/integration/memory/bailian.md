# Bailian Memory

`agentscope-extensions-memory-bailian`은 알리바바 클라우드 바이리안(Bailian)의 장기 기억(long-term memory) 서비스와 통합됩니다. 완전관리형이며 rerank, judge, rewrite와 같은 고급 검색 기능을 지원합니다.

## 사용 시점

- 이미 알리바바 클라우드 바이리안을 사용 중이며 플랫폼의 메모리 라이브러리를 재사용하고 싶을 때.
- 검색 품질을 중시하며 바이리안의 rerank / judge / rewrite 파이프라인을 사용하고 싶을 때.
- `userId` + `memoryLibraryId` + `projectId`를 통한 3단계 격리(isolation)가 필요할 때.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-memory-bailian</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 빠른 시작

```java
import io.agentscope.core.memory.bailian.BailianLongTermMemory;

try (BailianLongTermMemory memory = BailianLongTermMemory.builder()
        .apiKey(System.getenv("DASHSCOPE_API_KEY"))
        .userId("user_001")
        .memoryLibraryId("lib_xxxxx")
        .projectId("proj_xxxxx")
        .build()) {

    ReActAgent agent = ReActAgent.builder()
        .name("Assistant")
        .model(model)
        .longTermMemory(memory)
        .longTermMemoryMode(LongTermMemoryMode.BOTH)
        .build();

    agent.call(new UserMessage("Remind me to drink water at 9am every day")).block();
}
```

`BailianLongTermMemory`는 `AutoCloseable`을 구현합니다 — 내부 HTTP 커넥션이 해제되도록 try-with-resources 사용을 권장합니다.

## 검색 기능 스위치

기본적인 recall 외에도, 바이리안은 세 가지 파이프라인 스위치를 지원합니다.

```java
BailianLongTermMemory memory = BailianLongTermMemory.builder()
    .apiKey(apiKey)
    .userId("user_001")
    .memoryLibraryId("lib_xxxxx")
    .topK(20)
    .minScore(0.4)
    .enableRerank(true)   // 결과를 재정렬(re-rank), 더 정확하지만 느림
    .enableJudge(true)    // LLM이 결과가 실제로 관련 있는지 판단하게 함
    .enableRewrite(true)  // 기록 시 메모리를 재작성/병합
    .build();
```

필요하지 않다면 기본적으로 꺼두세요 — 지연시간과 비용이 추가됩니다.

## 메시지 필터링

바이리안 메모리는 자연스러운 사용자/어시스턴트 대화만 저장합니다.

- `MsgRole.USER`와 `MsgRole.ASSISTANT` 메시지만 기록됩니다.
- `ToolUseBlock`(도구 호출 요청)을 포함한 어시스턴트 메시지는 건너뜁니다.
- 압축된 히스토리가 중복 저장되는 것을 방지하기 위해 `<compressed_history>` 마커가 있는 메시지는 건너뜁니다.

도구 실행 결과를 메모리에 넣어야 한다면, `record(...)`를 호출하기 전에 상위 로직에서 직접 작성하세요.

## 빌더 참조

| 메서드 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `apiKey(String)` | ✅ | - | 바이리안 DashScope API 키 |
| `userId(String)` | ✅ | - | 사용자 수준 ID |
| `memoryLibraryId(String)` | ❌ | - | 메모리 라이브러리 ID |
| `projectId(String)` | ❌ | - | 프로젝트 ID |
| `profileSchema(String)` | ❌ | - | 사용자 프로필 스키마 ID |
| `apiBaseUrl(String)` | ❌ | `https://dashscope.aliyuncs.com` | 커스텀 게이트웨이를 위한 재정의 값 |
| `topK(Integer)` | ❌ | `10` | 검색 결과 최대 개수 |
| `minScore(Double)` | ❌ | `0.3` | 최소 유사도 임계값 (0–1) |
| `enableRerank(Boolean)` | ❌ | `false` | rerank 활성화 |
| `enableJudge(Boolean)` | ❌ | `false` | LLM judge 활성화 |
| `enableRewrite(Boolean)` | ❌ | `false` | 기록 시 rewrite 활성화 |
| `metadata(Map)` | ❌ | - | 각 메모리와 함께 저장되는 커스텀 메타데이터 |
| `httpTransport(HttpTransport)` | ❌ | default | HTTP 클라이언트 교체 |
