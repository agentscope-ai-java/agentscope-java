# MiniMax 모델

`agentscope-extensions-model-openai`는 OpenAI 호환 모델 스택을 통해 MiniMax를 완전하게 지원합니다. OpenAI 모델 확장 모듈을 추가한 다음 `ModelRegistry`와 함께 `minimax:<model>`을 사용하세요.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-openai</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## ModelRegistry

`MINIMAX_API_KEY`를 설정한 다음 `minimax:<model>` id를 사용하세요:

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("minimax:MiniMax-M3") // ModelRegistry.resolve(modelId)에 의해 내부적으로 리졸브됨
    .build();
```

프로바이더의 기본 base URL은 `https://api.minimaxi.com/v1`입니다. `OpenAIClient`가 기본 chat completions 엔드포인트를 추가하므로 최종 요청 URL은 `https://api.minimaxi.com/v1/chat/completions`가 되며, 이는 MiniMax의 OpenAI 호환 API와 일치합니다. 이 프로바이더는 모델 이름을 전송하기 전에 `minimax:` 접두사를 제거하고, `io.agentscope.extensions.model.openai.compat.minimax`의 MiniMax formatter를 사용합니다.

## Thinking 모드

모델을 리졸브할 때 `ModelCreationContext`를 통해 MiniMax의 thinking 옵션을 전달하세요:

```java
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;

Model model = ModelRegistry.resolve(
    "minimax:MiniMax-M3",
    ModelCreationContext.builder()
        .enableThinking(false)
        .build());
```

`enableThinking(false)`는 `thinking: {"type": "disabled"}`를 전송하고, `enableThinking(true)`는 `thinking: {"type": "adaptive"}`를 전송합니다. `thinking`이 생략되면 MiniMax-M3는 기본적으로 adaptive thinking을 사용합니다. M2.x 모델은 비활성화가 요청되어도 thinking이 계속 활성화된 상태로 유지됩니다. formatter는 기본적으로 `reasoning_split`을 활성화하여 MiniMax의 thinking 콘텐츠를 `ThinkingBlock`으로 파싱할 수 있도록 합니다.

## 호환성 참고 사항

MiniMax formatter는 OpenAI 스타일 요청을 MiniMax의 OpenAI 호환 Chat Completions API에 맞게 변환합니다. MiniMax가 `max_tokens`를 지원 중단(deprecated)으로 표시하고 있으므로 `max_tokens`를 `max_completion_tokens`로 매핑합니다.

MiniMax의 도구 정의는 함수 도구(function tools)를 지원하지만, 공식 스키마에는 도구 스키마의 `strict` 필드가 포함되어 있지 않습니다. 따라서 도구가 strict 스키마 검증으로 등록되어 있어도 기본 formatter는 `strict`를 생략합니다. MiniMax는 `tool_choice`도 문서화하고 있지 않으므로, 명시적인 tool-choice 설정은 MiniMax 요청에서 제거됩니다.

이 formatter는 `reasoning_effort`, `frequency_penalty`, `presence_penalty`, `thinking_budget`, `parallel_tool_calls`, `response_format`, `seed`와 같이 지원되지 않는 OpenAI 전용 요청 필드를 제거합니다. MiniMax는 스키마 제약 출력을 위한 OpenAI `response_format` 지원을 문서화하고 있지 않으므로, 구조화된 출력은 기본적으로 일반적인 AgentScope 폴백 동작을 사용합니다.

호환 가능한 엔드포인트나 자체 호스팅 엔드포인트의 경우 `ModelCreationContext`를 통해 `baseUrl`, `endpointPath`, 생성 옵션 또는 formatter 오버라이드를 전달하세요.
