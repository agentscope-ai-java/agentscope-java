---
title: ReMe
---

`agentscope-extensions-reme`는 자체 호스팅되는 ReMe 메모리 서비스와 통합됩니다. 이 확장의 특징은 **궤적(trajectory) 기반** 메모리 추출과 **워크스페이스 수준**의 격리입니다.

## 사용 시점

- 쉽게 구동할 수 있는 경량 자체 호스팅 메모리 서비스를 원할 때.
- 개별 메시지가 아니라 전체 대화 궤적을 요약하는 데 관심이 있을 때.
- `userId`로 논리적 워크스페이스를 표현할 수 있을 때(사용자당 하나의 워크스페이스).

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-reme</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 빠른 시작

```java
import io.agentscope.core.memory.reme.ReMeLongTermMemory;

ReMeLongTermMemory memory = ReMeLongTermMemory.builder()
    .userId("task_workspace")            // ReMe의 workspace_id에 매핑됨
    .apiBaseUrl("http://localhost:8002") // 사용자의 ReMe 서버
    .build();

ReActAgent agent = ReActAgent.builder()
    .name("Assistant")
    .model(model)
    .longTermMemory(memory)
    .longTermMemoryMode(LongTermMemoryMode.BOTH)
    .build();
```

`userId`는 ReMe의 `workspace_id`로 매핑됩니다 — ReMe에서 메모리를 분할하는 가장 작은 단위입니다.

## 동작 방식

- **쓰기(record)**: 필터링된 메시지들이 하나의 `ReMeTrajectory`로 합쳐져 ReMe의 `add` 엔드포인트로 전송됩니다. 그러면 서버가 해당 궤적에 대해 LLM 추출을 실행하여 검색 가능한 메모리 스니펫을 생성합니다.
- **검색(retrieve)**: 현재 메시지가 ReMe의 `search`에 대한 쿼리로 사용됩니다. 서버에서 집계된 `answer` 필드가 있으면 그것을 반환하고, 없으면 여러 메모리 스니펫을 합쳐서 반환합니다.

쓰기 시에는 바이리안과 동일한 필터링이 적용됩니다.

- `USER`와 `ASSISTANT` 메시지만 유지됩니다.
- `ToolUseBlock`(도구 호출 요청)을 포함한 어시스턴트 메시지는 건너뜁니다.
- `<compressed_history>` 마커가 포함된 메시지는 건너뜁니다.

## 빌더 참조

| 메서드 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `userId(String)` | ✅ | - | 워크스페이스 ID (쓰기와 읽기 모두에 사용됨) |
| `apiBaseUrl(String)` | ✅ | - | ReMe 서비스 URL, 예: `http://localhost:8002` |
| `timeout(Duration)` | ❌ | `60s` | HTTP 타임아웃 |

> ReMe는 아직 더 세분화된 메타데이터 필터링을 제공하지 않습니다. 태그 기반 세그먼트가 필요하다면 `userId` 안에 인코딩하세요 (예: `tenant-a:project-1`).
