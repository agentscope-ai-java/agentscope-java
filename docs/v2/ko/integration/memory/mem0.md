# Mem0

`agentscope-extensions-mem0`는 [Mem0](https://mem0.ai/)를 장기 기억 저장소로 통합하며, 벡터 검색과 LLM 기반 메모리 추출을 결합합니다. Mem0 SaaS 플랫폼, 자체 호스팅 배포, 로컬 단독 설정을 모두 지원합니다.

## 사용 시점

- Agent에서 세션 간 사실 기반 메모리(사용자 선호, 이전 결정 사항 등)를 사용하고 싶을 때.
- `agentId / userId / runId`라는 세 가지 메타데이터 계층을 통한 멀티테넌트 격리가 필요할 때.
- 검색 시점에 커스텀 메타데이터(예: `category=travel`)를 이용해 메모리를 필터링하고 싶을 때.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-mem0</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 빠른 시작

```java
import io.agentscope.core.memory.mem0.Mem0LongTermMemory;
import io.agentscope.core.memory.mem0.Mem0ApiType;

// 1. 메모리 인스턴스 생성 (로컬, 인증 없음)
Mem0LongTermMemory memory = Mem0LongTermMemory.builder()
    .agentName("Assistant")
    .userId("user_123")
    .apiBaseUrl("http://localhost:8000")
    .apiType(Mem0ApiType.SELF_HOSTED)
    .build();

// 2. Agent에 연결
ReActAgent agent = ReActAgent.builder()
    .name("Assistant")
    .model(model)
    .longTermMemory(memory)
    .longTermMemoryMode(LongTermMemoryMode.BOTH)
    .build();

// 3. 평소처럼 대화 — 메모리는 자동으로 기록/검색됨
agent.call(new UserMessage("I prefer homestays when traveling")).block();
```

## 배포 모드

`Mem0ApiType`은 URL 규칙과 인증 방식을 결정합니다.

| Enum | 사용 사례 | `apiBaseUrl` 예시 | `apiKey` |
| --- | --- | --- | --- |
| `PLATFORM` (기본값) | Mem0 SaaS | `https://api.mem0.ai` | 필수 |
| `SELF_HOSTED` | 자체 호스팅 Mem0 서버 | `http://your-host:8000` | 배포 환경에 따라 다름 |

## 멀티테넌트 격리

`agentName / userId / runName`은 Mem0가 메모리를 구성하는 데 사용하는 세 가지 식별자 계층입니다.

```java
Mem0LongTermMemory memory = Mem0LongTermMemory.builder()
    .agentName("travel-bot")     // 사용자 간 공유되는 Agent 수준 메모리
    .userId("alice")             // 사용자/테넌트 수준
    .runName("trip-2026-spring") // 세션별
    .apiBaseUrl("http://localhost:8000")
    .build();
```

세 가지 중 적어도 하나는 제공되어야 합니다. 그렇지 않으면 `build()`가 `IllegalArgumentException`을 던집니다. 검색은 메타데이터가 일치하는 메모리만 반환합니다.

## 커스텀 메타데이터 필터링

`metadata(...)`는 쓰기와 읽기 양쪽에 모두 적용됩니다 — 각 메모리와 함께 영속화되며 검색 시 필터로 주입됩니다.

```java
Map<String, Object> tags = Map.of(
    "category", "travel",
    "project_id", "proj_001"
);

Mem0LongTermMemory memory = Mem0LongTermMemory.builder()
    .agentName("Assistant")
    .userId("user_123")
    .apiBaseUrl("http://localhost:8000")
    .metadata(tags)
    .build();

// 이제 모든 record() 호출에 태그가 포함되며, retrieve()는 동일한 태그를 가진 메모리만 매칭함
```

프로젝트나 비즈니스 라인별로 지식을 나눌 때 유용합니다. 사용자 단위 분리만 필요하다면 `userId`만으로 충분합니다.

## 빌더 참조

| 메서드 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `apiBaseUrl(String)` | ✅ | - | Mem0 서비스 URL |
| `apiKey(String)` | 경우에 따라 다름 | - | API 키. 로컬 무인증 배포에서는 필요 없음 |
| `apiType(Mem0ApiType)` | ❌ | `PLATFORM` | SaaS와 자체 호스팅 라우팅 중 선택 |
| `agentName(String)` | 셋 중 하나 | - | Agent 수준 ID |
| `userId(String)` | 셋 중 하나 | - | 사용자 수준 ID |
| `runName(String)` | 셋 중 하나 | - | Run/세션 수준 ID |
| `metadata(Map)` | ❌ | `null` | 쓰기와 읽기 양쪽에 적용되는 추가 필터 |
| `timeout(Duration)` | ❌ | `60s` | HTTP 타임아웃 |

> `agentName / userId / runName` 중 적어도 하나는 설정해야 합니다. 그렇지 않으면 `build()`가 `IllegalArgumentException`을 던집니다.
