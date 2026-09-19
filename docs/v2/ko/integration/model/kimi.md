---
title: Kimi 모델
---

`agentscope-extensions-model-openai`는 OpenAI 호환 모델 스택을 통해 Kimi(Moonshot AI)를 완전하게 지원합니다. OpenAI 모델 확장 모듈을 추가한 다음 `ModelRegistry`와 함께 `kimi:<model>`을 사용하세요.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-openai</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## ModelRegistry

`MOONSHOT_API_KEY` 또는 `KIMI_API_KEY`를 설정한 다음 `kimi:<model>` id를 사용하세요:

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("kimi:kimi-k3") // ModelRegistry.resolve(modelId)에 의해 내부적으로 리졸브됨
    .build();
```

이 프로바이더는 기본값으로 `https://api.moonshot.cn/v1`을 사용하며, 모델 이름을 전송하기 전에 `kimi:` 접두사를 제거하고, `io.agentscope.extensions.model.openai.compat.kimi`의 Kimi formatter를 사용합니다.

## Thinking 모드

모델을 리졸브할 때 `GenerateOptions`를 통해 Kimi의 thinking 옵션을 전달하세요:

```java
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;
import java.util.Map;

Model model = ModelRegistry.resolve(
    "kimi:kimi-k2.6",
    ModelCreationContext.builder()
        .component(
            GenerateOptions.class,
            GenerateOptions.builder()
                .additionalBodyParam("thinking", Map.of("type", "disabled"))
                .maxCompletionTokens(16000)
                .build())
        .build());
```

`kimi-k3`는 최상위 수준의 `reasoning_effort` 옵션(`low`, `high`, `max`)을 사용합니다. `kimi-k3`와 `kimi-k2.7-code`는 항상 thinking이 활성화된 상태로 실행됩니다. `kimi-k2.6`과 `kimi-k2.5`는 기본적으로 thinking이 활성화되어 있지만 `additionalBodyParam("thinking", Map.of("type", "disabled"))`로 비활성화할 수 있습니다.

## 호환성 참고 사항

Kimi formatter는 OpenAI 스타일 요청을 Kimi chat-completions API에 맞게 변환합니다. 도구 스키마의 `strict`를 생략하고, 메시지 히스토리에서 어시스턴트의 `reasoning_content`를 보존하며, `thinking_budget`과 같이 지원되지 않는 요청 필드를 제거합니다.

`kimi-*` 모델에서는 `temperature`, `top_p`, `n`, `frequency_penalty`, `presence_penalty`와 같은 샘플링 파라미터가 플랫폼에 의해 고정되어 있으며 요청에서 제거됩니다. `moonshot-v1` 시리즈는 이러한 파라미터를 그대로 유지합니다. Kimi는 `max_completion_tokens`를 문서화하고 있으므로, `max_completion_tokens`가 아직 설정되지 않은 경우 `max_tokens`가 `max_completion_tokens`로 매핑됩니다.

`reasoning_effort`는 `kimi-k3`에서만 유지됩니다. K2.x의 thinking 제어를 위해서는 `GenerateOptions.additionalBodyParam`을 통해 `thinking` 본문 파라미터를 전달하세요.

`tool_choice=auto`와 `tool_choice=none`은 폭넓게 지원됩니다. `tool_choice=required`는 K2.x 모델에서 `auto`로 다운그레이드됩니다. 특정 함수를 강제하는 것은 thinking이 활성화된 상태와 호환되지 않으므로, `thinking.type`이 명시적으로 `disabled`로 설정되지 않는 한 `kimi-k3`, `kimi-k2.7-code`, `kimi-k2.6` / `kimi-k2.5`에서는 `auto`로 다운그레이드됩니다.

구조화된 출력(structured output)은 기본적으로 일반적인 AgentScope 폴백 동작을 사용합니다.

호환 가능한 엔드포인트나 자체 호스팅 엔드포인트의 경우 `ModelCreationContext`를 통해 `baseUrl`, `endpointPath`, 생성 옵션 또는 formatter 오버라이드를 전달하세요.
