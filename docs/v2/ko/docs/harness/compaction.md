---
title: "컨텍스트 압축"
description: "중요한 정보를 잃지 않으면서 대화를 모델의 토큰 예산 안에 유지하기"
---

:::{note}
이 페이지는 **컨텍스트 압축** — `HarnessAgent`가 대화를 모델의 토큰 예산 안에 유지하기 위해 사용하는 전략들 — 을 다룬다. 이는 [Context & AgentState](../building-blocks/context.md)에서 설명한 상태 없는(stateless) 엔진 설계와 `AgentState` 지속성 위에 세워져 있다. 아직 읽지 않았다면 그 페이지를 먼저 읽어 보라 — 압축은 지속성 계층이 저장하고 복원하는 것과 동일한 `AgentState`에 대해 동작한다.

**두 경로가 협력하는 방식**: 압축은 메모리 상의 `AgentState.contextMutable()`을 변경하고, 상태 저장소는 call이 끝날 때 갱신된 `AgentState`를 기록한다. 두 경로는 독립적이지만 항상 이 순서로 실행된다 — 상태 저장소가 보는 것은 언제나 압축 이후의 상태다.
:::

모델의 토큰 예산은 유한하다. 오래 실행되는 대화는 선제적으로 압축되거나, 결국 모델의 하드 리밋에 부딪히게 된다. `HarnessAgent`는 완전한 압축 스택을 제공하며 — `.compaction(...)` / `.toolResultEviction(...)`으로 선택적으로 켤 수 있다.

## `HarnessAgent`가 제공하는 것

| 전략 | 해결하는 문제 | 발동 시점 | Middleware |
|------|----------|----------|--------|
| **대화 요약** | 컨텍스트가 너무 *깊어짐* — 메시지 수 / 총 토큰 수가 쌓임 | 매 모델 추론 호출 전 | `CompactionMiddleware` |
| **큰 도구 결과 축출** | 컨텍스트가 너무 *넓어짐* — 하나의 도구 결과가 거대함 | 도구 실행 후 | `ToolResultEvictionMiddleware` |
| **Overflow 안전망** | 모델이 실제로 `context_length_exceeded`를 반환함 | `call()`이 예외를 던질 때 | `HarnessAgent.recoverFromOverflow` |
| **요약 전 인자 잘라내기** | 도구 호출 인자(예: `write_file` 본문)는 큰데 나중에 아무도 읽지 않음 | 요약 이전의 가벼운 전처리 패스 | `CompactionConfig.TruncateArgsConfig` |

이 네 가지는 **서로 독립적이며 자유롭게 조합할 수 있다.** 네 가지 모두 기본값은 꺼짐(off)이다.

### 1. 대화 요약(`CompactionMiddleware`)

메시지 수나 추정 토큰 수를 기준으로 발동한다. 대화의 **앞부분(prefix)**을 하나의 LLM 호출을 통해 구조화된 요약으로 증류하고, **마지막 N개의 메시지는 원문 그대로 유지**하며, `[summary] + [recent tail]`을 `AgentState.contextMutable()`에 다시 기록한다.

```java
HarnessAgent.builder()
    .compaction(CompactionConfig.builder()
        .triggerMessages(30)     // fire at 30 messages
        .keepMessages(10)        // keep last 10 verbatim
        .build())
    .build();
```

기본 요약 프롬프트는 내용을 `SESSION INTENT / SUMMARY / ARTIFACTS / NEXT STEPS`로 정리한다 — 엔지니어링/오케스트레이션 agent에 잘 맞는다. `CompactionConfig`는 요약용 LLM 호출에 전용 모델을 지정하는 `.model(...)`도 지원한다(설정하지 않으면 agent의 주 모델로 대체된다). 전체 설정 항목(`triggerTokens`, `keepTokens`, `flushBeforeCompact`, `offloadBeforeCompact`, `model`, `TruncateArgsConfig`)과 요약 프롬프트 템플릿은 [Memory — 압축 활성화하기](./memory.md#enable-compaction)에 있으며, 여기서는 중복해서 다루지 않는다.

### 2. 큰 도구 결과 축출(`ToolResultEvictionMiddleware`)

요약과 독립적이다. 도구 결과가 임계값(기본값 8만 자 ≈ 2만 토큰)을 초과하면, 전체 출력은 워크스페이스 디렉터리에 기록되고 **컨텍스트 내 메시지는 머리 + 꼬리 미리보기(각 약 2천 자)와 `read_file` 포인터로 대체**된다. agent는 필요할 때 전체 버전을 읽는다.

```java
HarnessAgent.builder()
    .toolResultEviction(ToolResultEvictionConfig.defaults())
    .build();
```

`read_file` / `write_file` / `edit_file` / `list_files` / `memory_*` / `session_search`는 기본적으로 제외된다 — 이들은 스스로 페이지네이션을 하거나 아주 작은 페이로드만 반환하기 때문이다. `grep_files`와 `glob_files`는 결과 개수 제한을 강제하지만, 개별 매치가 유독 클 때를 대비한 2차 안전망으로서 여전히 축출 대상이 될 수 있다. **셸 `execute`는 명령 출력이 임의로 커질 수 있기 때문에 의도적으로 제외 대상에서 빠져 있다.**

자세한 내용은 [Memory — 큰 도구 결과 오프로딩](./memory.md#large-tool-result-offloading)에 있다.

### 3. Overflow 안전망

모델이 `context_length_exceeded` / `maximum context` / `token limit` 에러를 반환하면, `HarnessAgent.recoverFromOverflow()`가 강제로 `triggerMessages=1`인 극단적 압축을 실행하고 **자동으로 한 번 재시도**한다. 빌드 시점에 `.compaction(...)`이 설정되어 있어야 하며, 그렇지 않으면 에러가 그대로 전파된다.

추가 설정은 필요 없다: 압축을 켜기만 하면 overflow 복구도 함께 따라온다.

### 4. 요약 전 인자 잘라내기(선택 사항)

LLM 요약 패스 이전에, **LLM을 사용하지 않는** 문자열 잘라내기 패스가 지나치게 큰 도구 호출 인자(`write_file`, `edit_file` 본문)를 잘라낸다.

```java
CompactionConfig.builder()
    .triggerMessages(80)
    .truncateArgs(CompactionConfig.TruncateArgsConfig.builder()
        .maxArgLength(2000)
        .truncationText("... [truncated] ...")
        .build())
    .build();
```

많은 워크로드에서 이 단계 하나만으로도 거의 비용 없이 요약 발동 시점을 상당히 늦출 수 있다.

## Memory와의 협업

`CompactionConfig.flushBeforeCompact`(기본값 `true`)는 **요약하기 전에 대화 prefix에서 사실을 추출해 장기 기억으로 보낼지**를 결정한다 — 이는 `<workspace>/MEMORY.md`와 `memory/*.md`를 읽고 새로운 사실을 점진적으로 덧붙이는 `MemoryFlushMiddleware` + `MemoryFlushManager`가 처리한다. 요약이 prefix 메시지를 제거하더라도 정보는 남아 있다: agent는 `memory_search` / `memory_get`을 통해 이를 다시 가져올 수 있다.

마찬가지로 `offloadBeforeCompact`(기본값 `true`)는 요약 전에 **원본 메시지**를 압축되지 않은 `*.log.jsonl`에 기록하므로, `session_search`가 여전히 이에 접근할 수 있다.

> 전체 Memory 서브시스템 — 2계층 구조, 백그라운드 유지보수(아카이브, 병합), memory 도구 — 은 [Memory](./memory.md)에 있다. 압축과 memory는 함께 쓰이는 경우가 많지만 스위치는 독립적이다.

## 압축이 건드리지 않는 것

`ConversationCompactor`는 오직 `AgentState.contextMutable()` 안의 **대화 메시지 목록**만을 다룬다. 다음은 다른 `AgentState` 필드에 있으며 **요약의 영향을 받지 않는다**.

- **Plan Mode 상태**(`AgentState.getPlanModeContext()`): plan 모드가 활성 상태인지, 현재 plan 파일 경로. plan 파일 자체는 워크스페이스의 `plans/` 아래에 있으며 Plan Mode 자체의 생명주기로 관리된다. [Plan Mode](./plan-mode.md)를 참고한다.
- **서브에이전트 백그라운드 작업**(`task_id`, 상태, 결과): `<workspace>/agents/<parentAgentId>/tasks/<sessionId>.json`에 저장되며 `TaskRepository`가 관리한다. 완료된 결과는 다음 추론 턴에서 system reminder를 통해 부모에게 다시 주입된다 — 이들은 대화 메시지 스트림에 **들어가지 않으므로** 요약이 이를 건드릴 수 없다. [Subagent — 백그라운드 작업 저장소](./subagent.md#background-task-storage)를 참고한다.
- **`todo_write` 작업 목록**(`AgentState.getTasksContext()`): 독립된 필드로, `AgentState`와 함께 지속되지만 압축 경로에는 포함되지 않는다. [Plan Mode — `todo_write`와의 상호작용](./plan-mode.md#interaction-with-todo_write)을 참고한다.
- **권한 규칙**(`getPermissionContext()`): 독립된 필드로, 스스로 지속된다.

이들 각각은 자기만의 상태 머신과 복구 경로를 가지고 있으며, 압축 트랙은 이들에게 투명하다 — plan이나 진행 중인 백그라운드 작업을 잃을 걱정 없이 `.compaction(...)`을 켤 수 있다.

## agent가 자신의 히스토리를 조회하게 하기

세션 기능이 켜져 있으면(기본값), 세 가지 조회 도구가 자동으로 등록된다.

- `session_list agentId="..."` — 한 agent의 과거 세션 목록을 조회한다.
- `session_history agentId="..." sessionId="..." lastN=20` — 한 세션의 최근 N개 메시지.
- `session_search query="..." agentId="..."` — 히스토리 전체에 대한 키워드 검색.

이 도구들은 **압축되지 않은 대화 로그**(`<workspace>/agents/<agentId>/sessions/<sessionId>.log.jsonl`)를 읽으므로, 컨텍스트 내 대화가 요약되었더라도 agent는 여전히 원본 메시지를 끌어올 수 있다.

---

## 관련 문서

- [Context & AgentState](../building-blocks/context.md) — 상태 없는 엔진 설계, `AgentState` 구조, 상태 지속성, `RuntimeContext`
- [Architecture](./architecture.md) — 하나의 call 안에서 컨텍스트, 상태 지속성, 워크스페이스가 어떻게 협력하는지
- [Memory](./memory.md) — 장기 기억, 전체 압축 설정, 큰 도구 결과 오프로딩, 백그라운드 유지보수
- [Plan Mode](./plan-mode.md) — plan 상태의 독립적인 지속성과 복구
- [Subagent](./subagent.md) — 백그라운드 작업이 어디에 있고 노드 마이그레이션을 어떻게 견디는지
- [Filesystem](./filesystem.md) — `userId` 기반의 다중 테넌트 경로 격리
