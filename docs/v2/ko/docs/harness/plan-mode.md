---
title: "플랜 모드(Plan Mode)"
description: "행동에 앞서 생각하기: 계획 파일을 작성하는 읽기 전용 단계, 실행 전에 HITL 승인이 필요함"
---

## 역할

플랜 모드는 에이전트가 실행에 앞서 "의도를 파악하고 기록"하도록 한다. 활성화되어 있는 동안 에이전트는 **읽기 전용 단계**에 머문다.

- **읽기 전용 도구**와 화이트리스트에 등록된 4개 도구 `plan_enter` / `plan_write` / `plan_exit` / `todo_write`만 동작한다(셸은 옵트인으로 허용할 수 있다 — [아래](#계획-단계-중-셸-허용하기옵트인) 참고).
- 그 외의 도구 호출은 즉시 거부된다(에이전트에게는 "plan-mode denied" 알림이 보인다).
- 플랜 모드를 종료하려면 HITL 확인이 필요하다(권한 시스템의 ASK를 재사용), 따라서 모델이 일방적으로 실행 단계로 넘어갈 수 없다.

이 파이프라인은 "설계 → 계획 → 사람의 검토 → 실행"을 코드화한다 — `todo_write`와 서브에이전트를 결합하면, 긴 작업에서 "즉흥적으로 처리하다가 일을 망치는" 결과를 눈에 띄게 줄여준다.

## 옵트인

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("planner")
    .model(model)
    .workspace(workspace)
    .enablePlanMode()                          // 플랜 모드 삼종 세트를 설치
    .planFileDirectory("plans")                // 선택 사항; 기본값 "plans"
    .build();
```

Builder 옵션:

| 메서드 | 기본값 | 참고 |
|--------|---------|-------|
| `enablePlanMode()` / `enablePlanMode(boolean)` | `false` | 플랜 모드 활성화 |
| `planFileDirectory(String)` | `"plans"` | 계획 파일 루트(워크스페이스 기준 상대 경로) |
| `allowShellInPlanMode()` / `allowShellInPlanMode(boolean)` | `false` | 계획 단계 중 셸(`execute`) 실행을 옵트인 — [계획 단계 중 셸 허용하기](#계획-단계-중-셸-허용하기옵트인) 참고 |

`enableTaskList()`를 호출하면 계획 단계 중에 만들어진 todo가 매 추론 스텝 전에 작은 리마인더로 표시된다.

## 세 가지 도구

| 도구 | 목적 | 파라미터 |
|------|---------|--------|
| `plan_enter` | 플랜 모드 진입 | 없음 |
| `plan_write` | 현재 계획 파일(기본 `plans/PLAN.md`)에 내용 작성 | `content` |
| `plan_exit` | 플랜 모드 종료 → 실행 단계; HITL 확인 | `rationale`(선택) |

`plan_write`는 **플랜 모드 전용 쓰기 진입점**이다 — 범용 `write_file`을 화이트리스트에 등록할 경우 모델이 계획 단계 동안 어디든 쓸 수 있게 되는 보안 위험을 피한다.

## 워크플로

```{mermaid}
sequenceDiagram
    autonumber
    participant U as User
    participant A as Agent
    participant H as Human (HITL)
    participant FS as workspace

    U->>A: "Refactor module X for me"
    A->>A: plan_enter
    A->>A: think → call read_file / grep_files (read-only)
    A->>FS: plan_write to plans/PLAN.md
    A->>H: plan_exit → HITL confirmation
    H-->>A: ConfirmResult(true)
    A->>A: enter execution phase: all tools allowed
```

계획 단계 중 화이트리스트에 없는 도구 호출(예: `write_file`, 또는 [옵트인](#계획-단계-중-셸-허용하기옵트인)하지 않은 `execute`)은 다음과 같은 메시지와 함께 즉시 거부된다.

```text
[Tool denied — plan mode is active]
Only read-only tools and plan_enter / plan_write / plan_exit / todo_write are allowed.
```

거부를 본 모델은 자연스럽게 "먼저 계획을 작성하는" 흐름으로 되돌아간다.

## 결과 읽어내기

플랜 모드 진입은 자율적으로 이루어지므로, 실행은 네 가지 상태 중 하나로 끝날 수 있다. `isPlanModeActive() == false`만으로는 모호하다 — 실제로 계획이 이루어졌는지 확인하지 않고 성공으로 간주하지 말아야 한다.

| 종료 상태 | 의미 |
|----------------|---------|
| 플랜 모드에 진입한 적이 없음 | 모델이 빌드 모드에서 바로 작업하기로 결정함 — 타당한 판단이며, 흔히 작업이 워크스페이스와 맞지 않을 때 발생한다. |
| 진입 → `plan_exit` | 성공: 계획을 세우고, 승인을 받고, 이제 빌드 모드에 있음. |
| 여전히 플랜 모드이며 `PLAN.md`가 존재함 | 계획 초안을 작성했지만 종료하지 않음; 세션을 재개해 승인하라. |
| 여전히 플랜 모드이며 `PLAN.md`가 없음 | "서술만 하고 행동하지 않음": 마지막 메시지는 계획처럼 *읽힐* 수 있지만 실제로 작성된 것은 없음 — 더 구체적인 입력이나 일치하는 코드베이스를 제공하라. |

프로그래밍적으로 이를 구분하려면, `plan_enter` / `plan_write`가 호출되었는지(예: `ToolCallStartEvent`로부터), 그리고 최종 `isPlanModeActive()`와 계획 파일의 존재 여부를 함께 추적하라.

## 계획 단계 중 셸 허용하기(옵트인)

기본적으로 셸 도구(`execute`)는 계획 단계 중에 **거부된다**. 셸은 *이중 용도*다. 단일 도구 호출로 읽을 수도(`cat` / `ls` / `grep` / `git log`), 변경할 수도(`rm` / `>` / `git commit` / `npm install`) 있으며, 플랜 모드는 순전히 **도구 이름**만으로 무엇을 허용할지 결정하므로 읽기 호출과 쓰기 호출을 구분할 수 없다. 셸을 거부하면 읽기 전용 보장이 온전히 유지된다.

하지만 셸 접근은 종종 코드베이스를 조사하고 *현실적인* 계획을 세우는 가장 유연한 방법이다. 이 트레이드오프를 받아들인다면 옵트인하라.

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("planner")
    .model(model)
    .workspace(workspace)
    .enablePlanMode()
    .allowShellInPlanMode()   // 계획 단계 중 모델이 읽기 전용으로 셸을 실행하도록 허용
    .build();
```

옵트인을 활성화하면:

- `execute`가 계획 단계 허용 목록에 추가되어, 모델이 셸을 통해 조사할 수 있다.
- 계획 배너에 모델에게 셸 사용을 **읽기 전용**(`cat` / `ls` / `grep` / `git log/diff/show/status`)으로 유지하고, 계획이 승인되기 전까지는 변경을 일으키는 명령을 실행하지 **말라**는 추가 지시가 붙는다.
- 전용 파일 편집 도구(`write_file` / `edit_file`)는 **계속 거부된다** — 이들이 주된 변경 경로이므로, 파일 쓰기에 대해서는 읽기 전용 의도가 계속 강제된다.

이는 OpenCode가 자신의 plan agent를 다루는 방식과 유사하다. 조사를 위해 셸은 허용하되 edit/write 도구는 강하게 차단하고, 셸을 읽기 전용으로 유지하는 일은 프롬프트에 의존한다. 따라서 이 보장은 기본값보다 *더 약하다*(모델이 셸을 통해 여전히 변경을 일으킬 수 있다). 그러니 폭발 반경을 억제하기 위해 이 기능은 **샌드박스 파일 시스템**과 함께 활성화하는 것을 권장한다.

## 런타임 권한 전환("우회" 탈출구)

플랜 모드는 하나의 구체적인 단계 전환이다. 그 아래에서는 모든 세션이 권한 엔진이 평가하는 [`PermissionMode`](../building-blocks/context.md)를 가지고 있다. 이 모드를 런타임에 전환할 수 있다 — 예를 들어 (다른 코딩 도구의 YOLO / 위험 감수 스킵 스위치와 유사한) 의도적이고 사용자가 시작한 "모든 권한 프롬프트 건너뛰기" 토글을 부여하는 경우다.

```java
RuntimeContext ctx = RuntimeContext.builder().sessionId("my-session").build();

agent.setPermissionMode(ctx, PermissionMode.BYPASS);    // 모든 것을 허용, 프롬프트 없음
// ... 완전한 접근이 필요한 작업을 실행 ...
agent.setPermissionMode(ctx, PermissionMode.DEFAULT);   // 정상적인 강제 규칙으로 복원

PermissionMode current = agent.getPermissionMode(userId, sessionId);
```

`setPermissionMode(...)`는 세션에 설정된 allow/deny/ask 규칙과 작업 디렉터리는 그대로 유지하며 — 오직 모드만 바뀐다 — 해당 세션의 캐시된 권한 엔진을 다시 빌드하므로 전환은 **다음** 호출부터 적용된다. 이미 진행 중인 호출은 시작할 때 사용했던 엔진을 그대로 유지한다.

⚠ `BYPASS`는 모든 규칙 평가를 비활성화하므로, 이를 명시적이고 세션 단위의 옵트인 동작으로 취급하고 샌드박스와 함께 사용하는 것을 권장한다. 프롬프트 없이 무인으로 실행하되 강제 규칙은 *유지*하고 싶다면 대신 `PermissionMode.DONT_ASK`를 사용하라(ASK 결정이 자동 허용이 아니라 DENY가 된다).

## 계획 상태는 영속화된다

플랜 모드는 **런타임 상태**이며 `AgentState`와 함께 자동으로 영속화된다 — 프로세스 재시작, 노드 장애 조치, 레플리카 간 복원 모두 계획 단계를 되돌려 놓는다. 계획 파일 자체는 워크스페이스의 `plans/`에 기록되며, 설정한 파일 시스템 모드(로컬 / 샌드박스 / 원격 KV)를 그대로 거치므로 분산 환경에서도 안전하다.

## 프로그래밍 방식의 진입/종료

애플리케이션 코드가 플랜 모드를 직접 제어할 때(예: 관리 콘솔의 버튼):

```java
RuntimeContext ctx = RuntimeContext.builder().sessionId("my-session").build();
agent.enterPlanMode(ctx);    // LLM이 plan_enter를 호출하는 것과 동일
agent.exitPlanMode(ctx);     // plan_exit와 동일; 프로그래밍적 진입은 HITL을 트리거하지 않음
agent.isPlanModeActive(ctx);
```

`agentscope-admin-spring-boot-starter`를 사용한다면, 관리자 HTTP API에서도 플랜 모드 제어를 제공한다(`POST /v1/admin/sessions/{id}:enter-plan-mode` / `:exit-plan-mode` / `GET /v1/admin/sessions/{id}/plan`).

## 서브에이전트와의 상호작용

⚠ 현재 **알려진 제약**: 플랜 모드 중 `agent_spawn`으로 생성된 서브에이전트는 **읽기 전용 제약을 자동으로 상속하지 않는다**. 자식을 제한하려면:

- 자식의 선언에서 `tools`를 읽기 전용 집합으로 좁히거나,
- 자식 자신의 빌더에서도 `enablePlanMode()`를 호출하여 명시적으로 진입시켜라

향후 릴리스에서는 플랜 모드 제약을 부모 → 자식으로 자동 전파할 예정이다.

## `todo_write`와의 상호작용

플랜 모드와 (코어에서 제공하는) `todo_write`는 **독립적이지만 흔히 함께 사용된다**.

- **플랜 모드** — 단계 전환 + 계획 파일 + HITL 종료
- **`todo_write`** — 실행 중 구조화된 "지금 해야 할 일" 목록을 유지(전체 목록 교체; 정확히 하나의 `in_progress`)

일반적인 워크플로: 계획 단계에서 `PLAN.md` 작성 → `plan_exit` → 실행 단계에서 `todo_write`를 사용해 PLAN을 5~8개의 todo로 쪼갬 → 하나씩 진행. 매 추론 스텝마다 에이전트에게 todo 리마인더를 보여줘 집중을 유지시킨다.

⚠ 서브에이전트의 **백그라운드 작업**(`task_output` / `task_cancel` / `task_list`)과 혼동하지 말 것 — 이는 다른 개념이다; [서브에이전트](./subagent.md) 참고.

## 작업 목록 확인하기

작업 목록은 `AgentState.tasksContext`에 위치하며 매 `call()`마다 자동으로 영속화된다. 애플리케이션 코드에서 읽으려면:

```java
List<Task> tasks = agent.getAgentState(userId, sessionId)
        .getTasksContext()
        .getTasks();

for (Task t : tasks) {
    System.out.printf("[%s] %s%n", t.getState(), t.getSubject());
    // state: PENDING / IN_PROGRESS / COMPLETED
}
```

`agentscope-admin-spring-boot-starter`를 사용한다면, 관리자 REST API가 이미 만들어진 엔드포인트를 제공한다.

```
GET /v1/admin/sessions/{sessionId}/tasks
```

이는 각 작업의 subject, state, owner, 의존성 정보(`blocks` / `blockedBy`)를 반환한다.

이벤트 스트림을 통해 실시간으로 작업 변경을 관찰하려면, `streamEvents()`에서 `todo_write` 도구 호출을 리슨하라.

```java
agent.streamEvents(message)
    .filter(e -> e.getType() == AgentEventType.TOOL_RESULT_END)
    .filter(e -> "todo_write".equals(((ToolResultEndEvent) e).getToolCallName()))
    .doOnNext(e -> {
        // Re-read the latest task list from state
        var tasks = agent.getAgentState(userId, sessionId)
                .getTasksContext().getTasks();
        updateUI(tasks);
    })
    .subscribe();
```

## 관련 문서

- [워크스페이스](./workspace.md) — `plans/` 디렉터리 위치
- [서브에이전트](./subagent.md) — `todo_write` ≠ 서브에이전트 작업; 혼동하지 말 것
- [아키텍처](./architecture.md) — 플랜 모드가 call() 타임라인 어디에 위치하는지
