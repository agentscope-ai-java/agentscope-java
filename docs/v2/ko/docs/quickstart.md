---
title: Quickstart
description: AgentScope Java 2.0으로 시작하기 — HarnessAgent로 첫 번째 장기 실행 에이전트를 띄워보세요
---

## 설치

AgentScope Java는 JDK 17 이상을 요구합니다. Maven 3.9+를 권장합니다.

### Maven 의존성

`HarnessAgent`는 권장되는 진입점입니다 — 워크스페이스, 장기 메모리, 세션 영속성, 서브에이전트, 샌드박스 등의 엔지니어링 역량을 하나의 빌더로 패키징합니다. `agentscope-harness`에 대한 의존은 전이적으로 `agentscope-core`를 함께 가져옵니다.

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-harness</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

<Note>

`${agentscope.version}`은 최신 버전으로 대체하세요. 최신 버전과 전체 릴리스 세부 사항은 [릴리스 노트](/v2/ko/docs/others/release-notes)를 참고하세요.

</Note>

순수한 `ReActAgent` API만 필요하다면(워크스페이스 / 영속성 / 서브에이전트 / 샌드박스 없이), 에이전트 프레임워크 자체를 위해서는 `agentscope-core`만으로 충분합니다. 구체적인 모델 프로바이더는 별개입니다: 프로바이더별 채팅 모델과 포매터는 독립적인 `agentscope-extensions-model-*` 모듈에 있습니다. `ReActAgent`와 `HarnessAgent`의 차이는 [Harness Architecture](/v2/ko/docs/harness/architecture)에서 다룹니다.

아래의 퀵스타트는 `.model("dashscope:qwen-plus")`를 통해 DashScope를 사용하므로, 해당 모델 확장도 함께 추가하세요.

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-dashscope</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

MCP 통합에는 공식 MCP SDK가 필요합니다 — 동작하는 예제는 `agentscope-examples/documentation/pom.xml`을 참고하세요.

## 첫 번째 에이전트

아래 예제는 `HarnessAgent`를 사용해 세 가지를 동시에 보여줍니다: **워크스페이스 기반 페르소나**(`AGENTS.md`), **자동 세션 영속성**(동일한 `sessionId`로 두 번째 턴이 첫 번째 턴을 기억함), **대화 압축**(임계값 초과 압축 + `MEMORY.md`로 증류된 장기 사실). 모델 id는 `.model(...)`에 문자열로 전달됩니다 — `ModelRegistry`가 이를 해석하고 해당하는 API 키 환경 변수를 자동으로 읽습니다.

```java
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import java.nio.file.Paths;

public class FirstAgent {
    public static void main(String[] args) {
        HarnessAgent agent = HarnessAgent.builder()
                .name("note-taker")
                .sysPrompt("You are a note-taking assistant.")
                // String form resolved via ModelRegistry — picks up DASHSCOPE_API_KEY
                // from the environment. Use "openai:gpt-5.5", "anthropic:claude-sonnet-4-5",
                // "gemini:gemini-2.0-flash", or "ollama:llama3" to switch providers.
                .model("dashscope:qwen-plus")
                .workspace(Paths.get(".agentscope/workspace"))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .build();

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("demo-session")
                .userId("alice")
                .build();

        // Turn 1: introduce yourself + state today's task
        agent.call(new UserMessage("My name is Alice, and I'm preparing a tech talk on ReAct today."), ctx).block();

        // Turn 2: same sessionId — state from turn 1 is restored automatically
        agent.call(new UserMessage("What is my name? What am I doing today?"), ctx).block();
    }
}
```

이 실행 이후 두 개의 디렉터리 트리가 생깁니다 — **워크스페이스**와 **상태 저장소**입니다.

```
.agentscope/workspace/                          ← workspace (agent content)
├── AGENTS.md                                   ← write one to give the agent its persona (optional)
└── agents/note-taker/
    └── sessions/                               ← never-compacted raw conversation log

~/.agentscope/state/note-taker/                 ← state store (outside workspace)
└── alice/demo-session/                         ← AgentState auto-saved / auto-loaded
    └── agent_state.json
```

`AgentState`는 기본적으로 **워크스페이스 바깥**의 `~/.agentscope/state/<agentId>/`에 존재합니다 — 상태는 워크스페이스 자체를 복원하기 위한 전제 조건이므로(예: 샌드박스 초기화 이후), 워크스페이스 데이터와 뒤섞여서는 안 되기 때문입니다. 동일한 `sessionId`로 프로세스를 재시작해도 두 번째 턴은 여전히 첫 번째 턴을 기억합니다.

<Warning>

기본 `JsonFileAgentStateStore`는 개발과 단일 노드 배포에 적합한 로컬 파일 백엔드입니다. 프로덕션 클러스터에서는 `RedisAgentStateStore`(`agentscope-extensions-redis`가 제공) 같은 분산 구현체를 사용하거나 여러분만의 `AgentStateStore`를 구현하세요. [프로덕션으로 가기](/v2/ko/docs/others/going-to-production)를 참고하세요.

</Warning>

충분한 턴이 지나 압축이 트리거되면, 증류된 사실은 먼저 `workspace/memory/YYYY-MM-DD.md`에 기록된 다음, 스로틀된 백그라운드 작업이 이를 `MEMORY.md`로 병합하고, 이는 다음 추론 단계에서 시스템 프롬프트에 주입됩니다.

### 추론과 도구 호출 스트리밍

Web / TUI 렌더링에 적합한, 텍스트 델타나 도구 호출 같은 증분 이벤트를 받으려면 `call(...)`을 `streamEvents(...)`로 바꾸세요.

```java
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;

agent.streamEvents(new UserMessage("Summarize today in three bullets."))
        .doOnNext(event -> {
            if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA) {
                // Streaming text fragment — append to UI or stdout
                System.out.print(((TextBlockDeltaEvent) event).getDelta());
            } else if (event.getType() == AgentEventType.TOOL_CALL_START) {
                // The agent is about to call a tool — surface the call info
                System.out.println("\n[tool] " + ((ToolCallStartEvent) event).getToolCallName());
            }
            // Other events: thinking blocks, tool results, reply end, etc.
        })
        .blockLast();
```

<Tip>

실행 전에 환경에 `DASHSCOPE_API_KEY`를 설정하세요. 프로바이더를 전환하려면, 해당하는 `agentscope-extensions-model-*` 모듈을 추가하고, `.model(...)`에 전달하는 문자열을 바꾸고, 해당 API 키(`OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `GEMINI_API_KEY`)를 export하세요. 타임아웃이나 커스텀 엔드포인트를 명시적으로 제어해야 한다면, `DashScopeChatModel.builder()...build()` 같은 프로바이더 빌더로 모델을 만들어 `.model(Model)`에 전달하세요.

</Tip>

### 멀티 유저 동시성

에이전트는 **호출 간에 상태 비저장**입니다 — 하나의 인스턴스가 서로 다른 사용자와 세션의 요청을 처리할 수 있습니다. `RuntimeContext`를 통해 `userId` / `sessionId`를 전달하면 에이전트가 해당하는 대화 상태를 자동으로 로드하고 격리합니다.

```java
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import java.nio.file.Path;
import java.nio.file.Paths;

// Create one agent instance at startup (singleton is fine)
HarnessAgent agent = HarnessAgent.builder()
        .name("note-taker")
        .sysPrompt("You are a note-taking assistant.")
        .model("dashscope:qwen-plus")
        .workspace(Paths.get(".agentscope/workspace"))
        .compaction(CompactionConfig.builder()
                .triggerMessages(30)
                .keepMessages(10)
                .build())
        .build();

// In your HTTP handler — different requests pass different RuntimeContexts
agent.call(new UserMessage(userInput), RuntimeContext.builder()
        .sessionId(sessionId)
        .userId(userId)
        .build()).block();
```

동일한 `(userId, sessionId)`를 대상으로 하는 호출은 자동으로 직렬화되며(하나의 세션에 대한 동시 쓰기는 없음), 서로 다른 세션에 대한 호출은 병렬로 실행됩니다. 전체 프로덕션 패턴(Redis 세션, 샌드박스, 스킬 저장소)은 [프로덕션으로 가기](/v2/ko/docs/others/going-to-production)를 참고하세요.

## 다음 단계

- [Agent](/v2/ko/docs/building-blocks/agent) — 전체 `ReActAgent` API, 빌더 필드, `call` / `streamEvents` / `observe`, human-in-the-loop, `AgentStateStore` 설정
- [Harness Architecture](/v2/ko/docs/harness/architecture) — `HarnessAgent`의 여러 역량이 어떻게 협력하는지, 상태가 어떻게 흐르는지
- [Workspace](/v2/ko/docs/harness/workspace) — `AGENTS.md` / `MEMORY.md` / `skills/` / `subagents/` / `tools.json` 디렉터리 레이아웃과 로딩 모델
- [Filesystem](/v2/ko/docs/harness/filesystem) — local + shell / 공유 스토어 / 샌드박스 배포 모드
