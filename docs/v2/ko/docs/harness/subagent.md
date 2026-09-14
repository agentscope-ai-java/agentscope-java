---
title: "서브에이전트(Subagent)"
description: "서브에이전트 선언, 동기/백그라운드 호출, 자동 반향 통지, 원격 서브에이전트, 스트리밍 전달"
---

## 역할

부모 에이전트가 "독립적이고, 컨텍스트가 많으며, 병렬화 가능한" 작업을 위임할 수 있게 하여 자신의 루프가 비대해지지 않도록 한다. 각 서브에이전트는 일시적인 인스턴스(로컬 `HarnessAgent` 또는 원격 스텁)이며, 자신만의 세션을 가지고 도구 결과를 통해 결과를 반환한다.

## 최소 예제

가장 간단한 방법: 워크스페이스에 spec 파일을 두는 것이다. 파일 이름이 곧 `agent_id`가 된다.

`workspace/subagents/reviewer.md`:

```markdown
---
description: Code-review specialist. Use when the user wants to review a PR, hunt for code issues, or check code style.
---

You are a subagent focused on code review. Follow this flow:
1. First read_file / grep_files to gather context
2. Give specific suggestions by file and line
3. End with an overall 1–5 score
```

부모는 이제 추론 중에 이를 호출할 수 있다.

```
agent_spawn agent_id="reviewer" task="review every change in this PR"
```

별도의 등록 단계는 필요 없다.

## 세 가지 선언 방식

세 가지 소스가 빌드 시점에 병합된다.

| 방식 | 용도 | 방법 |
|-----|---------|-----|
| 내장 `general-purpose` | 범용 폴백(부모의 능력을 그대로 반영) | 항상 존재, 설정 불필요 |
| 워크스페이스 spec 파일 | 프로젝트 고유, 버전 관리됨 | `workspace/subagents/<id>.md` |
| 프로그래밍 방식 선언 | 런타임에 결정됨(원격, 동적 파라미터) | `builder.subagent(SubagentDeclaration.builder()...)` |

### 워크스페이스 spec 파일

`workspace/subagents/*.md`를 비재귀적으로 스캔한다; 파일 이름(`.md`를 뺀 것)이 곧 `agent_id`다 — 프런트매터에 `name`을 **함께 설정하지 말라**.

```markdown
---
description: Code review specialist     # required, the model uses this to decide whether to delegate
workspace:
  mode: isolated              # default isolated; shared = use parent's workspace
  path: ./defs/reviewer       # optional; if absent, framework auto-creates a subdir
model: openai:gpt-4o-mini     # optional; inherits parent's if absent
steps: 8                      # optional; max iterations per spawn
temperature: 0.2              # optional; overrides parent GenerateOptions
top_p: 0.95                   # optional
hidden: false                 # true = not listed to the model (still callable programmatically)
mode: subagent                # primary / subagent / all (default all); primary can't be spawned
expose_to_user: true          # optional tri-state; force/forbid user exposure (omit = no opinion)
enable_pending_tool_recovery: true # optional; omit to inherit the parent's recovery setting
tools: [read_file, grep_files]   # optional; allowlist over inherited tools
---

You are a subagent focused on code review.
```

### 프로그래밍 방식 선언

```java
HarnessAgent.builder()
    .name("orchestrator")
    .model(model)
    .workspace(workspace)
    .subagent(SubagentDeclaration.builder()
        .name("reviewer")
        .description("Code review specialist")
        .workspace(Path.of("./defs/reviewer"))
        .workspaceMode(WorkspaceMode.ISOLATED)
        .model("qwen3-max")
        .steps(8)
        .tools(List.of("read_file", "grep_files"))
        .build())
    .subagent(SubagentDeclaration.builder()
        .name("remote-researcher")
        .description("Remote research subagent")
        .url("http://agent-task-server:8080")     // remote subagent
        .headers(Map.of("Authorization", "Bearer xxx"))
        .build())
    .build();
```

세 가지 소스는 상호 배타적이다: `workspace(...)`, `inlineAgentsBody(...)`, `url(...)` — 하나만 선택하라.

`general-purpose`를 포함해 자동으로 생성되는 로컬 서브에이전트는 부모의
`HarnessAgent.Builder.enablePendingToolRecovery(...)` 설정(기본값 `false`)을 상속한다. 선언에서
`.enablePendingToolRecovery(true)` 또는 `.enablePendingToolRecovery(false)`로 이를 오버라이드할 수 있으며,
`null`은 상속을 의미한다. 워크스페이스 spec은 `enable_pending_tool_recovery`(또는 `enablePendingToolRecovery`)를 받아들인다.
활성화되면, 실패한 세션에서 로드된 호출을 포함하여, 새로운 일반 메시지가 미아가 된(orphaned) 대기 중 도구 호출을
합성된 오류 결과로 복구한다. 대기 중인 권한 확인은 여전히 확인이 필요하며, 빈 입력 재개와 호출자가 제공한
도구 결과는 기존 동작을 유지한다. 원격 에이전트와 커스텀 팩토리는 복구를 스스로 설정한다.

### 내장 `general-purpose`

spec 파일이 필요 없으며 항상 사용 가능하다. 그 역할은 "범용 폴백"이다 — 부모의 능력(동일한 모델, 도구, 스킬)을 그대로 반영하며 부모의 워크스페이스를 공유한다. 부모가 전용 spec을 작성하지 않고도 하위 작업의 컨텍스트를 분리하고 싶을 때 유용하다.

## ISOLATED vs SHARED

`workspaceMode`는 무엇이 서브에이전트의 워크스페이스로 간주되는지를 결정한다.

- **ISOLATED**(기본값): 서브에이전트는 자신만의 워크스페이스를 가진다(`workspace.path`가 없으면 프레임워크가 서브디렉터리를 자동 생성한다). 서브에이전트의 런타임 상태는 "부모 sessionId × 사용자" 단위로 버킷이 나뉜다 — 따라서 같은 사용자의 서로 다른 대화에서 같은 서브에이전트를 생성해도 서로 오염되지 않는다.
- **SHARED**: 서브에이전트가 부모의 워크스페이스를 직접 사용한다. 서브에이전트의 출력을 부모가 즉시 읽어야 하는 경우에 적합하다(예: `general-purpose`).

## 동기 혹은 백그라운드?

부모는 `agent_spawn`으로 서브에이전트를 생성한다; 핵심 노브는 `timeout_seconds`다.

- `timeout_seconds > 0`(기본값 30, 최대 600) — **동기** 호출; 부모는 이 스텝에서 블로킹되며, 결과는 도구 결과로 반환된다. 기본적으로 대기 시간이 만료되면 진행 중이던 실행은 백그라운드 작업으로 **승격**되어(`status: timeout_promoted` + `task_id`) 계속 실행된다.
- `timeout_seconds = 0` — **백그라운드** 호출; `task_id`가 즉시 반환되고, 서브에이전트는 백그라운드에서 실행된다.

**`RuntimeContext`를 통한 강제 동기화.** 애플리케이션 코드는 현재 호출의 `RuntimeContext`에 `AgentSpawnTool.CTX_FORCE_SYNC = true`를 설정하여 LLM의 비동기 선택을 오버라이드할 수 있으며, 선택적으로 `CTX_FORCE_SYNC_TIMEOUT_SECONDS`로 고정된 대기 예산(초)을 지정할 수 있다.

```java
RuntimeContext ctx = RuntimeContext.builder()
    .sessionId("s-1")
    .put(AgentSpawnTool.CTX_FORCE_SYNC, true)
    .put(AgentSpawnTool.CTX_FORCE_SYNC_TIMEOUT_SECONDS, 120) // optional; overrides LLM timeout_seconds
    .build();
```

활성화되면:

1. `CTX_FORCE_SYNC_TIMEOUT_SECONDS`가 설정되어 있으면, 이는 LLM의 `timeout_seconds`를 **완전히 대체**한다(`<= 0`은 30초로 폴백; 최대 600초).
2. 이 오버라이드가 없으면, LLM이 준 `timeout_seconds=0`은 기본 동기 타임아웃(30초)으로 강제 변환되며 — 백그라운드 작업은 **제출되지 않는다** — 반면 LLM이 준 양수 타임아웃은 그대로 유지된다.
3. 동기 대기가 만료되면, 도구는 `status: timeout`을 반환하고 서브에이전트를 중단시킨다 — 백그라운드 `task_id`로 승격되지 **않는다**.

`agent_send`도 동일한 스위치를 따른다. 하나의 턴에서 강제 동기 `agent_spawn` 호출을 여러 번 하더라도, Toolkit 기본값에 따라 여전히 병렬로 실행된다.

목표를 리소스 충돌이 없는 독립적인 하위 작업들로 분할할 수 있다면, 부모는 같은 추론 턴에서 여러 개의 동기 서브에이전트 호출을 발행할 수 있다. Toolkit은 기본적으로 병렬 도구 실행을 사용하므로(`ToolkitConfig.parallel=true`), 이러한 동기 호출들은 `ReActAgent`와 `HarnessAgent` 모두에서 병렬로 진행된다; 부모는 해당 배치의 모든 도구 결과가 반환된 뒤에야 다음 추론 스텝에 진입하며, 이는 동기적인 fan-out / fan-in 배리어를 형성한다. 도구 호출을 직렬화하려면, `ToolkitConfig.builder().parallel(false).build()`로 만든 커스텀 `Toolkit`을 전달하라.

작업을 먼저 독립성과 의존성 그래프에 따라 분해하라. 의존성 엣지가 없는 노드는 병렬 서브에이전트의 좋은 후보다; 의존성이 있는 노드는 디스패치나 병합 전에 업스트림 결과를 기다려야 한다. 짧거나 크리티컬 패스인 작업은 동기 대기나 명시적인 배리어의 좋은 후보이며, 이를 통해 부모는 그 결과를 가지고 추론을 계속할 수 있다. 긴 작업은 부모가 다른 일을 계속하는 동안 백그라운드에서 실행한 뒤, 나중에 수집하여 병합할 수 있다.

### 백그라운드 작업은 자동으로 반향 통지된다

백그라운드 작업이 끝나면, 부모는 **폴링할 필요가 없다** — 부모의 다음 추론 스텝 이전에, 프레임워크가 완료된 작업 결과를 대화 끝부분에 system reminder로 주입한다.

```
<system-reminder>
Background tasks delivered:
- task_id=xxx, agent=research-analyst, status=COMPLETED
  result summary: ...
</system-reminder>
```

부모는 자연스럽게 이에 응답하거나 이어서 작업한다. 즉, 프롬프트에 "task_output을 폴링하는 것을 기억하라"라고 쓸 **필요가 없다** — 그것은 예전 방식이다.

### 백그라운드 작업 도구

내부적으로, 서브에이전트 생명주기는 두 그룹의 도구로 나뉜다.

| 도구 | 역할 |
|------|------|
| `agent_spawn` | 서브에이전트를 생성하고 선택적으로 작업을 실행(동기 또는 백그라운드) |
| `agent_send` | 기존 서브에이전트에게 후속 메시지 전송 |
| `agent_list` | 활성 서브에이전트 인스턴스 목록 |
| `task_output` | `task_id`로 백그라운드 작업의 결과 조회(블로킹 또는 논블로킹) |
| `wait_async_results` | 백그라운드 결과를 대기; 특정 `task_ids`가 모두 끝날 때까지 기다리거나, `wait_all=true`로 현재 세션의 미완료 작업 스냅샷을 사용할 수 있음 |
| `task_cancel` | 실행 중인 백그라운드 작업 취소 |
| `task_list` | 현재 상태와 함께 모든 백그라운드 작업 나열 |

`agent_spawn` / `agent_send`는 서브에이전트 **인스턴스**를 관리한다(생성, 재사용, 통신); `task_output` / `wait_async_results` / `task_cancel` / `task_list`는 백그라운드 **작업 결과**를 관리한다(상태 확인, 출력 가져오기, 대기, 취소). 이 둘 사이의 연결 고리는 `timeout_seconds=0`일 때 `agent_spawn` 또는 `agent_send`가 반환하는 `task_id`다.

> 대부분의 경우 자동 반향 통지 메커니즘이 명시적인 도구 호출 없이도 결과를 전달한다. 작업 도구들은 탈출구로서 유용하다: 반향 통지가 일어나기 전에 진행 상황을 확인하거나, 함께 사용 가능해야 하는 결과 집합을 기다리거나, 더 이상 필요 없는 작업을 취소하거나, 대화 압축 이후 작업 상태를 복구하는 경우다.

비동기 결과를 수집하는 일반적인 방법은 세 가지다.

- **자동 반향 통지**: 블로킹하지 않을 때의 기본 경로. 완료된 자식 작업은 다음 추론 스텝 전에 `<system-reminder>`로 주입된다.
- **표적 작업 확인**: `task_output(task_id, block=false)`를 호출하여 특정 작업의 현재 상태나 최종 결과를 확인한다.
- **대기 배리어(모두 수집해야 할 때 권장)**: `wait_async_results(task_ids="id1,id2")` 또는 `wait_async_results(wait_all=true)`를 호출한다. 배리어 모드는 집합이 종료 상태가 될 때까지 대기하며 **각 작업의 결과를 도구 반환값에 포함**시키므로, 바로 이어서 작업을 계속할 수 있다. `wait_all=true`는 호출 시작 시점의 미완료 작업 스냅샷을 사용하며, 대기하는 동안 새로 생성된 작업은 추가하지 않는다.

> **레거시 inbox-any**: `task_ids`도 `wait_all`도 없이 `wait_async_results`를 호출하면 *어떤* inbox 메시지든 도착할 때까지만 기다린다. 이는 wait-all 배리어가 아니다 — 그룹 내 모든 작업이 끝나야 한다면 `task_ids` 또는 `wait_all=true`를 사용하라.

## 기존 서브에이전트에 후속 메시지 보내기

`agent_spawn`은 `agent_key`(런타임 인스턴스 핸들)를 반환한다. 이를 사용하거나 `label`을 사용해 후속 메시지를 보낼 수 있다.

```
agent_send agent_key="agent:reviewer:abc-123" message="also check the schema changes"
```

생성 시점에 `label`을 설정했다면, `agent_key` 대신 이를 사용할 수 있다.

```
agent_spawn agent_id="reviewer" task="review the PR" label="pr-reviewer"
agent_send label="pr-reviewer" message="also check the schema changes"
```

활성 서브에이전트 목록: `agent_list`.

## 영속 세션

기본적으로 모든 `agent_spawn`은 새 세션을 가진 새로운 서브에이전트를 생성한다 — 이전 호출에 대한 기억이 없다. 선언에서 `persistSession(true)`를 설정하면 여러 번의 spawn에 걸쳐 동일한 서브에이전트 인스턴스를 재사용할 수 있다.

```java
.subagent(SubagentDeclaration.builder()
    .name("note-taker")
    .description("Accumulates notes across the conversation")
    .persistSession(true)
    .build())
```

`persistSession`이 켜져 있으면, 프레임워크는 `(parentSessionId, agentId, label)`로부터 결정적인 키를 도출한다. 같은 조합으로 `agent_spawn`이 다시 호출되면, 기존 에이전트 인스턴스가 재사용된다 — 대화 이력과 상태가 보존된다.

## 서브에이전트를 사용자에게 노출하기

일반적으로 서브에이전트는 사용자에게 보이지 않는다 — 부모의 내부 도구로서 뒤에서 실행된다. `expose_to_user=true`를 사용하면, 부모는 서브에이전트를 Channel을 통해 **사용자가 직접 주소를 지정할 수 있도록** 만들 수 있다.

```
agent_spawn agent_id="researcher" task="investigate AI trends" expose_to_user=true
```

이는 두 가지를 수행한다.

1. **Gateway에 서브에이전트를 등록**하여 사용자가 주소를 지정할 수 있는 진입점으로 만든다
2. **`SubagentExposedEvent`를 스트리밍 이벤트 흐름에 발행**하며, `subagentId` 핸들을 담는다

사용자의 클라이언트는 `SubagentExposedEvent`를 수신하고, 그 이후로는 부모 에이전트를 완전히 건너뛰고 서브에이전트에게 직접 메시지를 보낼 수 있다.

```java
// Client-side: listen for exposed subagents in the event stream
chat.sendStream(SendOptions.userId("user-1"), "Spawn a researcher to investigate AI trends")
    .doOnNext(event -> {
        if (event instanceof SubagentExposedEvent se) {
            // se.getSubagentId() → use this to talk directly to the subagent
            // se.getAgentId()    → subagent type (e.g. "researcher")
            // se.getLabel()      → optional human-readable name
        }
    })
    .blockLast();

// Send a message directly to the exposed subagent
chat.sendToSubagent(subagentId, "Focus on LLM agents specifically").block();
```

이는 "분기(branch-off)" 시나리오에 유용하다: 부모가 전문가를 생성하고, 사용자는 그 전문가와 독립적으로 대화를 이어간다. 전체 Channel 쪽 API는 [Channel — Talking to exposed subagents](./channel.md#talking-to-exposed-subagents)를 참고하라.

### 활성화 방법

`agent.channel(...)`을 사용하라 — 다리(bridge)는 자동으로 연결되며, 설정이 필요 없다.

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("orchestrator")
    .model("dashscope:qwen-plus")
    .build();

// channel() creates the internal gateway and wires the bridge — expose_to_user just works.
ChatUiChannel chat = agent.channel(ChatUiChannel.create());
```

Channel 바인딩이 없으면, `agent_spawn`의 `expose_to_user=true`는 조용히 무시된다 — 서브에이전트는 여전히 정상적으로 동작하지만, 사용자에게 노출되지는 않는다. `GatewayBootstrap`을 사용하는 멀티에이전트 구성은 [Channel — Thread exposure with GatewayBootstrap](./channel.md#thread-exposure-with-gatewaybootstrap)을 참고하라.

### 코드에서 노출 제어하기

LLM이 `expose_to_user=true`를 넘겨주는 것에만 의존하는 것은 항상 충분히 유연하지는 않다. 애플리케이션 코드에서 이 결정을 두 가지 방식으로 오버라이드할 수 있으며, 최종 값은 다음 우선순위(가장 높은 것부터)로 결정된다.

1. **`RuntimeContext` 호출별 오버라이드** — 현재 호출의 모든 `agent_spawn`에 적용됨
2. **`SubagentDeclaration` 타입별 정책** — 해당 서브에이전트 타입의 정적 기본값
3. **LLM의 `expose_to_user` 도구 인자**
4. 위 중 어느 것도 의견을 표명하지 않을 때 **`false`**

**`RuntimeContext`를 통한 호출별 오버라이드.** `AgentSpawnTool.CTX_EXPOSE_TO_USER` 키 아래에 `Boolean`(또는 그 문자열 형태)을 넣는다.

```java
RuntimeContext ctx = RuntimeContext.builder()
    .userId("user-1")
    .put(AgentSpawnTool.CTX_EXPOSE_TO_USER, true)   // force on; false forbids exposure
    .build();
```

**선언에서의 타입별 정책.** 삼중 상태인 `exposeToUser`를 사용한다 — `TRUE`는 항상 노출하고, `FALSE`는 절대 노출하지 않으며(LLM의 `expose_to_user=true`를 오버라이드), `null`(기본값)은 컨텍스트 오버라이드를 따르고 그다음 LLM 인자를 따른다.

```java
SubagentDeclaration decl = SubagentDeclaration.builder()
    .name("researcher")
    .description("Investigates topics and returns a synthesized report.")
    .exposeToUser(true)   // this subagent type is always user-addressable
    .build();
```

또는 Markdown 서브에이전트 spec의 프런트매터에서도(마찬가지로 삼중 상태다 — "의견 없음"을 위해 키를 생략):

```markdown
---
name: researcher
description: Investigates topics and returns a synthesized report.
expose_to_user: true
---
```

이를 통해 모델의 판단과 무관하게 노출을 강제하거나 금지할 수 있으며, 두 코드 소스 모두 의견을 표명하지 않을 때는 여전히 LLM이 선택하도록 둘 수 있다.

### 재시작과 멀티 레플리카를 넘나들 때

기본적으로 노출은 프로세스 내부에 국한된다: `subagentId`는 이를 생성한 노드에서만 유효하며 재시작 시 사라진다. 노출된 서브에이전트를 **어떤 레플리카에서든**, **재시작을 넘어서도** 해석 가능하게 만들려면, 상태와 파일 시스템에 사용하는 것과 동일한 한 줄짜리 `distributedStore(...)`로 에이전트를 빌드하라.

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("orchestrator")
    .model("dashscope:qwen-plus")
    .distributedStore(RedisDistributedStore.fromJedis(jedis))
    .build();

ChatUiChannel chat = agent.channel(ChatUiChannel.create());  // recovery wired automatically
```

`subagentId`는 스토어에 영속화되며, 서브에이전트 자신의 대화는 세션 기준으로 분산 `AgentStateStore`에서 다시 로드된다 — 따라서 이후 메시지가 다른 노드에 도착하더라도 사용자는 계속 *동일한* 서브에이전트와 대화하게 된다. 멀티에이전트 `GatewayBootstrap`의 경우, `.distributedStore(...)`를 전달하라(그렇지 않으면 메인 에이전트의 것을 상속한다). 배포 가이드 — `subagentId`를 실제 살아있는 노드로 되돌려 라우팅하는 것(스티키 라우팅)을 포함 — 는 [Going to Production](../others/going-to-production.md)에 있다.

## 에이전트가 새로운 서브에이전트 spec을 직접 작성하게 하기

`agent_generate` 도구(**기본적으로 꺼져 있음**)는 LLM이 새로운 서브에이전트 spec 초안을 작성하고 이를 `workspace/subagents/<name>.md`에 쓸 수 있게 해준다.

```java
// Opt-in (at build time):
// Grab the builder's internal SubagentsMiddleware reference and call enableAgentGenerateTool
```

"작업 도중에 에이전트가 새로운 종류의 헬퍼가 필요하다는 것을 깨달았을 때" 유용하다. 프로덕션에서는 신중히 사용하라 — 보통은 에이전트가 spec 초안을 작성하고, 파일을 쓰기 전에 사람이 검토하도록 하는 것이 좋다.

## 동작에 관한 참고 사항

- **`description`을 잘 작성하라**: 이는 위임 여부를 결정하는 모델의 주요 신호다. "코드 리뷰"는 "사용자가 PR을 리뷰하거나 코드 스타일을 확인하고 싶을 때 사용"보다 훨씬 덜 유용하다.
- **재귀 안전성**: 서브에이전트는 추가 서브에이전트를 생성할 수 없다(강제로 leaf로 표시됨); 게다가 최대 3단계라는 하드 캡이 있다.
- **userId는 전파된다**: 부모의 `RuntimeContext.userId`는 자식에게 전달되므로, 멀티테넌트 격리 체인이 그대로 유지된다.
- **권한 상속**: 부모의 모든 DENY 권한 규칙은 자동으로 자식에게 전파된다. 부모가 어떤 도구를 거부당했다면, 자식도 마찬가지로 거부당한다 — 위임을 통해 보안 경계를 우회할 수 없다. 이를 선택 해제하려면 선언에서 `inheritParentPermissions(false)`를 설정하라.
- **스트리밍 전달**: 부모의 `stream()` 동안, 동기 서브에이전트로부터의 중간 이벤트가 부모의 `Flux`로 실시간으로(소스 태그와 함께) 전달된다; 아래 [서브에이전트 스트리밍](#서브에이전트-스트리밍)을 참고하라.

## 원격 서브에이전트

`url` + 선택적인 `headers`만 설정하면 서브에이전트는 원격 HTTP 서비스(Agent Protocol)를 통해 실행된다.

```java
.subagent(SubagentDeclaration.builder()
    .name("remote-researcher")
    .description("Remote research subagent")
    .url("http://agent-task-server:8080")
    .headers(Map.of("Authorization", "Bearer xxx"))
    .remoteStreaming(true)          // default when unset
    .remoteStreamDetail(RemoteStreamDetail.VERBOSE)  // FULL when unset
    .remoteAskPolicy(RemoteAskPolicy.DENY)  // default
    .remoteContextAttributes(Map.of("region", "cn"))
    .build())
```

동일한 동기(`timeout_seconds>0`) / 백그라운드(`timeout_seconds=0`) 시맨틱이 적용된다.

원격 모드 전용 선언 노브:

| 필드 | 기본값 | 참고 |
|-------|---------|-------|
| `remoteStreaming` | `true`(설정하지 않을 때) | 부모가 `streamEvents()`를 사용할 때, 원격 작업의 SSE 이벤트를 `source` 태그와 함께 `metadata.taskId` / `metadata.parentSessionId`(harness의 `TaskRecord` / 부모 세션과 동일한 id)를 붙여 부모 스트림으로 전달 |
| `remoteStreamDetail` | `FULL` | 원격 이벤트 스트림 중 얼마만큼을 전달할지 — [원격 스트리밍 상세](#원격-스트리밍-상세) 참고 |
| `remoteAskPolicy` | `DENY` | 원격 도구 확인(HITL) 요청을 어떻게 처리할지 — [원격 인가](#원격-인가) 참고 |
| `remoteContextAttributes` | 없음 | 매 제출마다 `context.attributes`로 전송되는 정적 호출자 속성. 호출별 값을 병합하려면 부모의 `RuntimeContext`에 있는 `AgentSpawnTool.CTX_REMOTE_CONTEXT_ATTRIBUTES` 아래에 맵을 넣어라; [Context attributes](../../integration/protocol/agent-protocol.md#context-attributes) 참고 |

### 원격 스트리밍 상세

로컬 서브에이전트는 자식의 이벤트를 그대로 전달한다. 원격 서브에이전트는 네트워크를 건너야 하며, 얼마나 많은 것이 건너는지는 `remoteStreamDetail`로 제어되며 `context.detail`로 전송된다.

| 레벨 | 전달하는 내용 |
|-------|----------|
| `STATUS` | 실행 생명주기, 도구 호출 경계, 도구 결과, 확인 요청 |
| `FULL`(기본값) | `STATUS`에 텍스트와 thinking 델타를 더한 것 |
| `VERBOSE` | 원격 에이전트가 내보내는 모든 이벤트 — 블록 경계, 도구 인자 델타, **도구 출력 델타**, 토큰 사용량이 포함된 모델 호출, 힌트, 에이전트 결과, 커스텀 이벤트 |

부모의 스트림이 서브에이전트가 로컬이든 원격이든 동일하게 보여야 할 때는 `VERBOSE`를 선택하라; 이것이 원격 서브에이전트의 도구 출력 내용과 토큰 사용량이 부모에게 도달하는 유일한 레벨이다. 이것이 기본값이 아닌 이유는, 텍스트만 렌더링하는 호출자에게는 추가 이벤트가 순수한 부담이기 때문이다.

전용 wire 타입이 없는 이벤트는 원본 이벤트가 `payload` 필드에 직렬화된 채로 `AGENT_EVENT`로 이동한다. 따라서 부모는 로컬에서 받았을 때와 정확히 동일한 클래스(id, 타임스탬프, 메타데이터 포함)를 디코딩한다. 이 필드보다 오래된 클라이언트는 여전히 평탄화된 타입별 필드를 읽으며 패스스루 이벤트는 그냥 보지 못할 뿐이다.

### 원격 인가

부모의 DENY 권한 규칙은 원격 제출의 `context.deny_rules`로 전달된다(로컬 자식과 동일한 상속 방식이며, `inheritParentPermissions(false)`로 선택 해제 가능).

원격 에이전트가 도구 확인을 위해 일시 정지할 때(`awaiting_confirm`):

- **스트리밍 부모 + `remoteAskPolicy=PROPAGATE`**: `RequireUserConfirmEvent`가 non-null `source` 태그와 함께 부모의 `streamEvents()` 스트림으로 전달된다. `decisions[{toolCallId, approved}]`와 함께 Agent Protocol [`POST /tasks/{id}/resume`](../../integration/protocol/agent-protocol.md)을 통해 원격 작업을 재개하라.
- **비스트리밍 부모(`call`) 또는 `remoteAskPolicy=DENY`(기본값)**: 대기 중인 확인은 자동으로 거부된다. 도구 결과에는 `remote tool confirmation(s) were auto-denied`라는 안내가 포함된다.

확인을 기다리는 동안, 작업 상태는 `RUNNING`으로 유지된다(`awaitingConfirm=true`). 따라서 `wait_async_results` 같은 배리어는 작업이 재개되어 종료 상태에 도달할 때까지 계속 대기한다.

## 백그라운드 작업 저장소

백그라운드 작업 상태는 기본적으로 `workspace/agents/<parentAgentId>/tasks/<sessionId>.json`에 기록된다. 따라서:

- 공유 스토어 모드(멀티 레플리카)에서는 어떤 노드든 작업 상태를 읽을 수 있다;
- 작업 실행은 **생성한 노드에 고정**되지만, 어떤 노드든 결과를 읽고 부모에게 반향 통지할 수 있다;
- 어떤 노드에서든 `task_cancel`로 취소할 수 있다 — 실행 중인 노드가 취소 플래그를 폴링하고 중단한다.

## 플랜 모드 중 위임하기

부모가 플랜 모드에 있을 때, 생성된 서브에이전트는 **읽기 전용 제약을 자동으로 상속한다**. 자식은 생성 시점에 플랜 모드로 진입하므로 쓰기 작업을 수행할 수 없다 — 위임 체인 전체에 걸쳐 안전 경계가 유지된다.

## 서브에이전트 스트리밍

> 새 코드는 `streamEvents()`(`Flux<AgentEvent>`를 반환)를 사용해야 한다. 레거시 `stream()` 계열(`Flux<Event>`)은 2.0.0부터 `@Deprecated(forRemoval = true)`다 — [Message & Event](../building-blocks/message-and-event.md)와 [V1 Migration Guide B.4](../change-log.md)를 참고하라.

부모가 `agent_spawn` / `agent_send`를 통해 동기 서브에이전트를 호출하면, 자식의 중간 이벤트는 부모의 `streamEvents()` 스트림으로 **실시간 전달**된다. 각 자식 이벤트는 `source` 필드(`"main/researcher"`와 같은 `/`로 구분된 경로)를 담고 있어, 부모 이벤트(`source == null`)와 자식 이벤트를 구분할 수 있다. 원격 Agent Protocol 자식은 추가로 `metadata.taskId`(`AgentEvent.METADATA_TASK_ID`)를 harness 작업 id로, `metadata.parentSessionId`(`AgentEvent.METADATA_PARENT_SESSION_ID`)를 부모 세션으로 설정하므로, 같은 원격 에이전트에 대한 두 개의 동시/동일 턴 호출이 `source` 경로를 공유하더라도 여전히 구분 가능하다.

```
caller
  └─ parent.streamEvents(msg, ctx)
        │
        ├─ AGENT_START                            ← parent starts
        ├─ TEXT_BLOCK_DELTA …                     ← parent reasoning
        ├─ TOOL_CALL_START "agent_spawn"
        │
        │  [child spawned]
        ├─ AGENT_START          (source="main/researcher")  ← child starts
        ├─ TEXT_BLOCK_DELTA …   (source="main/researcher")  ← child reasoning
        ├─ TOOL_CALL_START …    (source="main/researcher")
        ├─ TOOL_RESULT_END …   (source="main/researcher")
        ├─ AGENT_END            (source="main/researcher")  ← child done
        │  [agent_spawn returns; child result → parent TOOL_RESULT]
        │
        ├─ TOOL_RESULT_END                        ← parent receives tool result
        ├─ TEXT_BLOCK_DELTA …                     ← parent second round
        └─ AGENT_END                              ← parent done
```

### `streamEvents()` 사용하기(권장)

```java
parent.streamEvents(new UserMessage(message), ctx)
    .doOnNext(event -> {
        String src = event.getSource();
        String prefix = (src != null) ? "[" + src + "] " : "";

        if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA) {
            System.out.print(prefix + ((TextBlockDeltaEvent) event).getDelta());
        } else if (event.getType() == AgentEventType.TOOL_CALL_START) {
            System.out.println(prefix + "[tool] " + ((ToolCallStartEvent) event).getToolCallName());
        } else if (event.getType() == AgentEventType.AGENT_START) {
            if (src != null) System.out.println("── child started: " + src);
        } else if (event.getType() == AgentEventType.AGENT_END) {
            if (src != null) System.out.println("── child finished: " + src);
        }
    })
    .blockLast();
```

부모와 자식 이벤트 구분하기:

```java
// parent events only
events.filter(e -> e.getSource() == null).subscribe(…);

// child events only
events.filter(e -> e.getSource() != null).subscribe(…);

// events from a specific child
events.filter(e -> e.getSource() != null && e.getSource().contains("researcher")).subscribe(…);
```

### SSE 전달

```java
@GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> chat(@RequestParam String message,
                                          @RequestParam String sessionId) {
    RuntimeContext ctx = RuntimeContext.builder().sessionId(sessionId).build();
    return agent.streamEvents(new UserMessage(message), ctx)
            .map(event -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("type", event.getType().name());
                payload.put("id",   event.getId());
                if (event.getSource() != null) {
                    payload.put("source", event.getSource());
                }
                if (event instanceof TextBlockDeltaEvent delta) {
                    payload.put("delta", delta.getDelta());
                } else if (event instanceof ToolCallStartEvent start) {
                    payload.put("toolName", start.getToolCallName());
                }
                return ServerSentEvent.<String>builder()
                        .data(objectMapper.writeValueAsString(payload))
                        .build();
            });
}
```

### 동작 경계

| 시나리오 | 실시간 전달? |
|----------|------------------|
| `streamEvents()` + 동기 로컬 자식(`timeout_seconds > 0`) | ✔ |
| `call()` 모드(비스트리밍) | ✗(자식 결과는 `tool_result` 문자열로 반환됨) |
| `timeout_seconds = 0` 백그라운드 작업 | ✗(결과는 부모의 다음 라운드에 반향 통지를 통해 전달됨) |
| 원격 서브에이전트(Agent Protocol) + 부모 `streamEvents()` + `remoteStreaming=true`(기본값) | ✔ |
| 원격 서브에이전트 + 부모 `call()` 또는 `remoteStreaming=false` | ✗ |

### 오류 처리

자식이 내부적으로 예외를 던지면, 프레임워크는 이를 잡아 부모에게 `TOOL_RESULT`로 되돌려 쓴다. 이는 `onError`를 부모 스트림으로 전파하지 **않는다** — 자식의 실패가 부모를 깨뜨리지 않는다. 부모 스트림 자체에서 오류가 발생하면, 표준 Reactor 시맨틱(`onErrorResume` 등)을 사용하라.

## 관련 문서

- [Channel](./channel.md) — `expose_to_user`, `SendOptions`, 사용자-서브에이전트 직접 메시징
- [워크스페이스](./workspace.md) — `subagents/`와 `agents/<id>/tasks/` 레이아웃
- [플랜 모드](./plan-mode.md) — 플랜 단계 중 서브에이전트에 대한 제약
- [아키텍처](./architecture.md) — 부모와 자식이 협력하는 방식
- [Agent Protocol](../../integration/protocol/agent-protocol.md) — 원격 작업 엔드포인트(SSE + HITL 재개)
- [Message & Event](../building-blocks/message-and-event.md) — `AgentEvent` 계층(권장)과 폐기 예정인 `Event` / `EventType` / `StreamOptions` 타입
- [V1 Migration Guide B.4](../change-log.md) — `stream()` → `streamEvents()` 폐기 일정
