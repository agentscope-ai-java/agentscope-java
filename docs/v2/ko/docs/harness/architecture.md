---
title: "Harness 아키텍처"
description: "HarnessAgent가 무엇인지, 각 기능이 어떻게 협력하는지, call() 도중 상태가 어떻게 흐르는지"
---

`HarnessAgent`는 `ReActAgent`를 감싸는 얇은 래퍼로, 장기 실행 agent에 필요한 엔지니어링 기능들 — 워크스페이스 기반 페르소나, 장기 기억, 서브에이전트 오케스트레이션, 샌드박스 격리, skill 조합, plan 모드, channel 라우팅 — 을 하나의 빌더로 묶어낸다.

단순한 `ReActAgent`는 "요청 하나 → 추론 → 도구 → 응답"만 처리한다. Harness는 다른 질문들에 답한다: 다음 턴은 이전 턴이 멈춘 지점에서 어떻게 이어받는가, 컨텍스트는 어떻게 한계 안에 머무는가, 사용자는 어떻게 격리되는가, 위험한 작업은 어떻게 검토를 거치는가, 재사용 가능한 능력은 어떻게 쌓이는가.

> 설치, 의존성, 그리고 첫 `HarnessAgent`를 처음부터 끝까지 만들어 보는 과정은 [Quickstart](../quickstart.md)에 있다. 이 페이지는 아키텍처만 다룬다.

## HarnessAgent 구축하기

`HarnessAgent`(`io.agentscope.harness.agent.HarnessAgent`)는 사용자가 직접 사용하는 harness API다. 이는
[`ReActAgent`](../building-blocks/agent.md)를 감싸면서 그 위에 워크스페이스 / 파일시스템 / 샌드박스 / 서브에이전트 /
skill / plan 모드 / MCP 오케스트레이션을 더한다. 이러한 프로덕션 기능이 필요할 때는 언제든 `HarnessAgent.builder()`를
사용하고, 워크스페이스도 지속성도 서브에이전트도 없는 단순한 ReAct 루프가 필요하다면
[`ReActAgent.builder()`](../building-blocks/agent.md#configuring-an-agent)를 직접 사용하면 된다 — 두 빌더는
대부분의 필드를 공유하므로 나중에 서로 전환하는 작업은 대체로 기계적이다.

`HarnessAgent`는 **호출 사이에 상태를 갖지 않으며(stateless)**, 여러 사용자/세션을 동시에 처리하는 싱글턴으로
사용해도 안전하다 — 각 `call()`은 `RuntimeContext`의 `(userId, sessionId)`를 사용해 상태를 격리한다. 같은 세션에
대한 호출은 자동으로 직렬화되고, 서로 다른 세션은 병렬로 실행된다.

`ReActAgent`와 마찬가지로 빌더의 `.model(...)`은 어떤 [`ChatModelBase`](../building-blocks/model.md)
서브클래스든(`DashScopeChatModel`, `OpenAIChatModel`, `AnthropicChatModel` 등) 받아들인다 — 또는 흔히 쓰이는
`ModelRegistry` 문자열 id도 받아들인다. 도구는 `ReActAgent`와 마찬가지로 `Toolkit`에 올린다.

```java
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.harness.agent.HarnessAgent;
import java.nio.file.Paths;

public class WeatherTools {
    @Tool(name = "get_weather", description = "Get the current weather for a city")
    public String getWeather(
            @ToolParam(name = "city", description = "City name, e.g. 'Tokyo'") String city) {
        return "Sunny, 24°C in " + city;
    }
}

// Any ChatModelBase subclass works here — swap in OpenAIChatModel, AnthropicChatModel, etc.
ChatModelBase model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .formatter(new DashScopeChatFormatter())
                .build();

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new WeatherTools());

HarnessAgent agent =
        HarnessAgent.builder()
                .name("weather-assistant")
                .sysPrompt("You are a helpful weather assistant.")
                .model(model)                          // HarnessAgent.Builder#model(Model)
                .toolkit(toolkit)                       // HarnessAgent.Builder#toolkit(Toolkit)
                .workspace(Paths.get(".agentscope/workspace"))
                .build();
```

`.model(...)`에는 `String` 오버로드도 있어서(`.model("dashscope:qwen-plus")`) `ModelRegistry`를 통해 해석되고
해당 API 키 환경 변수를 자동으로 읽는다 — 이 형태를 처음부터 끝까지 보려면 [Quickstart](../quickstart.md)를,
모든 `ChatModelBase` 프로바이더와 그 빌더 옵션을 보려면 [Model](../building-blocks/model.md)을 참고한다.

## 핵심 동작 원리

기억해 둘 세 가지:

**1. 기능들은 추론 루프 안이 아니라 그 위에 얹힌다.**
워크스페이스 주입, 압축, 서브에이전트, 샌드박스, Plan Mode — 각각은 ReAct 루프의 핵심 지점에 훅으로 연결된다. 핵심 알고리즘은 그대로 유지되며, Harness는 그저 더할 뿐이다.

**2. 기능들은 서로에게 의존하지 않으며, 세 가지 객체를 공유한다.**
각 기능은 한 가지 일만 하고 다른 기능을 알지 못한다. 이들은 다음을 통해 협력한다:

- **`RuntimeContext`** — 이번 call에서 말하고 있는 것이 누구인지: `sessionId`, `userId`, 그리고 임의의 추가 값. 영속화되지 않는다.
- **워크스페이스** — 누가 어떤 파일을 읽고 쓰는지. 그것들이 실제로 어디에(로컬 디스크, 샌드박스, KV 저장소) 위치하는지는 설정 선택 사항이다.
- **`AgentStateStore`** — 호출들 사이에서 런타임 상태가 어떻게 복원되는지.

**3. 내장 기능은 고정된 순서로 실행되며, 여러분의 middleware가 먼저 실행된다.**
Harness는 빌드 시점에 내장 middleware를 고정된 순서로 연결한다. `.middleware(...)`로 추가한 것은 무엇이든 Harness의 내장 기능보다 **먼저** 실행된다.

## 핵심 구성 요소

각 기능은 하나의 문제에 답한다. 빌더에서 선택적으로 켠다.

| 기능 | 해결하는 문제 | 빌더 훅 | 상세 |
|---|---|---|---|
| 워크스페이스 기반 페르소나 | 페르소나, 지식, 서브에이전트 명세, skill, MCP 허용 목록이 모두 파일로 존재 | `.workspace(path)` | [Workspace](./workspace.md) |
| 상태 지속성 | 동일한 `(userId, sessionId)`가 요청, 프로세스, 복제본을 넘나들며 재개됨 | 기본적으로 켜짐; `.stateStore(...)`로 재정의 | [Context & AgentState](../building-blocks/context.md) |
| 2계층 장기 기억 | 긴 대화 속 사실들이 `MEMORY.md`로 침전됨 | 기본적으로 켜짐; `.memory(...)`로 프롬프트/트리거 정책 커스터마이즈 | [Memory](./memory.md) |
| 대화 압축 | 히스토리를 한계 내로 유지; 실제 overflow 시 강제 재시도 | `.compaction(...)` | [Compaction](./compaction.md) |
| 큰 도구 결과 오프로딩 | 8만 자 이상의 결과를 디스크 + placeholder로 이동 | `.toolResultEviction(...)` | [Compaction](./compaction.md) |
| 서브에이전트 오케스트레이션 | 자식에게 위임, 동기 또는 백그라운드, 자동 push-back 포함 | `.subagent(...)` 또는 `workspace/subagents/`에 명세를 배치 | [Subagent](./subagent.md) |
| 플러그형 파일시스템 | 코드 변경 없이 로컬 + 셸 / 공유 저장소 / 샌드박스 전환 | `.filesystem(...)` | [Filesystem](./filesystem.md) |
| 샌드박스 격리 | 파일과 명령이 격리됨; call 간 복구; 다중 복제본 | `.filesystem(new DockerFilesystemSpec()...)` | [Sandbox](./sandbox.md) |
| Plan Mode | HITL 종료를 가진, 읽기 전용의 먼저-생각하는 단계 | `.enablePlanMode()` | [Plan Mode](./plan-mode.md) |
| Skill 조합 | Git / Nacos / MySQL / classpath / workspace로부터 오는 skill | `.skillRepository(...)` | [Skill](./skill.md) |
| MCP 통합 및 도구 허용 목록 | 선언적 MCP 서버 + 도구별 allow/deny | `workspace/tools.json` | [Workspace](./workspace.md) |
| Channel 라우팅 | 세션 관리, 세션별 동시성, 다중 agent 라우팅, 스트리밍 이벤트 | `agent.channel(...)` / `GatewayBootstrap` | [Channel](./channel.md) |

## 상태가 흐르는 방식

세 개의 계층이 존재하며, 프레임워크가 이들 사이로 데이터를 자동으로 옮긴다.

- **call 내부 상태** — `AgentState`(대화 컨텍스트, 권한 규칙, Plan Mode 상태, 도구 상태)와 `RuntimeContext`(`sessionId`, `userId`, 샌드박스 핸들, 추가 값).
- **call 간 상태** — 매 `call()`이 끝날 때 자동으로 저장되고 다음 `call()`에서 자동으로 로드된다: 설정된 `AgentStateStore`(기본값 `~/.agentscope/state/<agentId>/`, `(userId, sessionId)`로 주소 지정) 안의 `AgentState` 런타임 스냅샷, `sessions/<sessionId>.log.jsonl` 아래의 압축되지 않는 전체 대화 로그, 하위 작업 기록, 샌드박스 메타데이터.
- **장기 기억** — 세션들을 넘나들며 누적된다: `memory/YYYY-MM-DD.md`는 append-only이며, 스로틀링된 백그라운드 작업이 주기적으로 이를 `MEMORY.md`로 병합한다. `MEMORY.md`는 매 추론 단계마다 system prompt에 주입된다.

기억해 둘 만한 세 가지 불변 규칙:

- system prompt는 매 추론 단계마다 다시 만들어지므로, `AGENTS.md`나 `MEMORY.md`에 대한 수정은 즉시 반영된다 — 재시작이 필요 없다.
- 압축, 기억 증류(distillation), 백그라운드 유지보수는 스로틀링되어 있다. 매 턴마다 실행되지는 않는다.
- `AgentState`는 core의 `ReActAgent` + `AgentStateStore`가 지속시킨다. Harness는 더 이상 자체 지속성 훅을 추가하지 않는다.

## 나만의 middleware 추가하기

Harness의 배관(plumbing)을 우회하지 않으면서 커스텀 동작을 끼워 넣으려면:

- `.middleware(...)`를 사용한다 — 여러분의 middleware는 모든 Harness 내장 기능보다 먼저 실행된다.
- 현재 call의 신원(`userId` / `sessionId`)을 얻으려면 agent로부터 `RuntimeContext`를 읽는다.
- 워크스페이스 I/O를 할 때는 `harnessAgent.getWorkspaceManager()`를 거친다 — 이는 샌드박스나 원격 저장소 모드에서도 올바르게 라우팅된다. `java.nio.Files`로 쓰면 호스트 디스크에 기록되며, 로컬 모드가 아닌 경우 엉뚱한 위치에 기록된다.

## 관련 문서

- [Workspace](./workspace.md) — 디렉터리 레이아웃, system prompt에 무엇이 주입되는지, `tools.json`
- [Context & AgentState](../building-blocks/context.md) — `AgentState`, `RuntimeContext`, `AgentStateStore` 지속성, 다중 사용자 격리
- [Memory](./memory.md) — 2계층 기억
- [Compaction](./compaction.md) — 요약 압축, 큰 결과 오프로딩, overflow 복구
- [Filesystem](./filesystem.md) — 로컬 + 셸 / 공유 저장소 / 샌드박스
- [Sandbox](./sandbox.md) — 격리된 실행, call 간 복구, 분산
- [Subagent](./subagent.md) — 선언, 동기/백그라운드, 스트리밍 전달
- [Skill](./skill.md) — 4계층 조합, 자가 학습 루프
- [Plan Mode](./plan-mode.md) — 읽기 전용 단계 + HITL 종료
- [Channel](./channel.md) — 세션 관리, 다중 agent 라우팅, 스트리밍 SSE
