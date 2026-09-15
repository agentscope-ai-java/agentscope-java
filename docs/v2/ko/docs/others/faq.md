---
title: "FAQ"
description: "AgentScope Java 2.0에 대한 자주 묻는 질문"
---

:::{dropdown} AgentScope Java 2.0은 1.0과 호환되나요?
AgentScope Java 2.0은 가능한 한 1.x와의 호환성을 유지해 대부분의 사용자가 원활하게 업그레이드할 수 있도록 하는 것을 목표로 합니다. 다만 2.0은 재설계된 에이전트 추상화와 새로운 이벤트 시스템, 권한 시스템, 미들웨어 스택을 포함해 API 수준의 호환성이 깨지는 변경도 도입합니다. 자세한 내용은 [V1 마이그레이션 가이드](../change-log.md)를 참고하세요.

    신규 프로젝트라면 새로운 기능을 활용하기 위해 2.0을 바로 도입하는 것을 권장합니다. 기존 사용자를 위해 1.0 문서도 계속 제공됩니다.
:::

  :::{dropdown} AgentScope Java 2.0에 함께 제공되는 프런트엔드가 있나요?
있습니다. 저장소에는 `ReActAgent`와 동일한 프로토콜을 사용하는 즉시 사용 가능한 웹 앱인 `agentscope-admin` 모듈이 포함되어 있습니다. 별도의 커스텀 UI 코드 없이 동작하며, 이벤트 시스템(`AgentEvent`)과 권한 시스템의 HITL 흐름과 깔끔하게 통합됩니다.
:::

  :::{dropdown} 2.0에서 RAG와 장기 메모리를 지원하나요?
지원합니다. `io.agentscope.core.rag`와 `io.agentscope.core.memory.LongTermMemory`는 이미 저장소에 존재하지만, 지식 베이스, 문서 리더 등 관련 컴포넌트는 아직 완성 중입니다 — 진행 상황은 [릴리스 노트](release-notes.md)와 GitHub 릴리스에서 확인하세요.
:::

  :::{dropdown} 모델 프로바이더는 어떻게 전환하나요?
`.model(...)`에 전달하는 `<provider>:<model-name>` 문자열을 바꾸고, 해당하는
`agentscope-extensions-model-*` 의존성을 추가한 다음, 해당 프로바이더의 API 키 환경 변수를 설정하세요.
그 외 코드 변경은 필요 없습니다 — `ModelRegistry`가 런타임에 프로바이더를 해석합니다.

```java
ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .model("openai:gpt-4.1")   // was "dashscope:qwen-plus"
                .build();
```

```bash
# Set the env var matching the row you picked above
export OPENAI_API_KEY=sk-your-key-here
```

| 프로바이더 | 모델 문자열 접두사 | 환경 변수 |
|---|---|---|
| DashScope | `dashscope:qwen-plus` | `DASHSCOPE_API_KEY` |
| OpenAI | `openai:gpt-4.1` | `OPENAI_API_KEY` |
| Anthropic | `anthropic:claude-sonnet-4-7` | `ANTHROPIC_API_KEY` |
| Gemini | `gemini:gemini-2.0-flash` | `GEMINI_API_KEY` |
| Ollama (로컬) | `ollama:llama3` | 없음(선택적으로 `OLLAMA_BASE_URL`) |

전체 프로바이더 목록과 명시적 빌더 설정은 [Model](../building-blocks/model.md)을 참고하세요.
:::

  :::{dropdown} `.model("...")`가 왜 "model not found"를 던지나요?
이는 `ModelRegistry`가 `<provider>:<model-name>` 문자열을 해석하지 못했다는 의미로, 거의 항상
해당하는 `agentscope-extensions-model-*` 모듈이 아직 클래스패스에 없기 때문입니다. 다음과 같이 추가하세요.

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-dashscope</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

id가 `provider:model-name` 규칙을 따르는지(예: `"openai:gpt-5.5"`,
`"dashscope:qwen-max"`, `"gemini:gemini-2.0-flash"`) 다시 확인하세요 — 프로바이더 접두사의 오타는
의존성이 있어도 동일한 오류를 발생시킵니다.
:::

  :::{dropdown} 에이전트가 무엇을 하고 있는지(추론과 도구 호출) 어떻게 확인하나요?
내장된 `AgentTraceMiddleware`를 붙이세요 — `ReActAgent`와 `HarnessAgent` 모두에서 동작하며
각 추론 단계, 도구 호출, 결과를 발생하는 즉시 로그로 남깁니다.

```java
import io.agentscope.harness.agent.middleware.AgentTraceMiddleware;

ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .model("dashscope:qwen-plus")
                .middleware(new AgentTraceMiddleware())
                .build();
```

트레이싱 대신(또는 트레이싱과 함께) 여러분만의 로직을 구현하려면 `MiddlewareBase`를 구현하고
`onAgent` / `onReasoning` / `onActing` / `onModelCall`에 훅을 다세요 — [Middleware](../building-blocks/middleware.md)를 참고하세요.
:::

  :::{dropdown} Java가 아닌 에디션도 있나요?
있습니다. AgentScope는 각각 별도의 저장소를 가진 세 가지 독립적인 언어 에디션으로 제공됩니다.

    - **Java** — [`agentscope-ai/agentscope-java`](https://github.com/agentscope-ai/agentscope-java)(이 문서 사이트)
    - **Python** — [`agentscope-ai/agentscope`](https://github.com/agentscope-ai/agentscope)
    - **TypeScript** — [`agentscope-ai/agentscope-typescript`](https://github.com/agentscope-ai/agentscope-typescript)
:::
