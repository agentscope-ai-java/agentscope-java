---
title: GLM Model
---

`agentscope-extensions-model-openai`는 OpenAI 호환 모델 스택을 통해 정식 GLM(Zhipu AI / Z.AI) 지원을 제공합니다. OpenAI 모델 확장 모듈을 추가한 다음, `ModelRegistry`와 함께 `glm:<model>`을 사용하세요.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-openai</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## ModelRegistry

`ZAI_API_KEY`, `GLM_API_KEY`, 또는 `ZHIPUAI_API_KEY`를 설정한 다음, `glm:<model>` ID를 사용합니다.

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("glm:glm-5.2") // Resolved internally by ModelRegistry.resolve(modelId)
    .build();
```

이 프로바이더는 기본적으로 `https://open.bigmodel.cn/api/paas/v4`를 사용하며, 모델 이름을 전송하기 전에 `glm:` 접두사를 제거하고, `io.agentscope.extensions.model.openai.compat.glm`의 GLM 포맷터를 사용합니다.

## 사고(Thinking) 모드

모델을 해석(resolve)할 때 `GenerateOptions`를 통해 GLM 사고 옵션을 전달합니다.

```java
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;
import java.util.Map;

Model model = ModelRegistry.resolve(
    "glm:glm-5.2",
    ModelCreationContext.builder()
        .component(
            GenerateOptions.class,
            GenerateOptions.builder()
                .additionalBodyParam("thinking", Map.of("type", "disabled"))
                .reasoningEffort("max")
                .build())
        .build());
```

GLM-4.7 및 GLM-5 시리즈는 기본적으로 사고 기능이 활성화되어 있습니다. GLM-5.2는 `reasoning_effort`도 지원하며, `additionalBodyParam("tool_stream", true)`로 스트리밍 도구 호출 인자를 활성화할 수 있습니다.

## 호환성 참고 사항

GLM 포맷터는 OpenAI 스타일 요청을 GLM chat-completions API에 맞게 적용합니다. 최소한 하나의 사용자 메시지가 존재하도록 보장하고, 지원되지 않는 메시지 `name` 필드를 제거하며, 도구 스키마의 `strict`를 생략하고, `frequency_penalty`, `presence_penalty`, `thinking_budget`, `stream_options`와 같은 지원되지 않는 요청 필드를 제거합니다.

GLM은 `max_tokens`만 지원하므로, `max_tokens`가 아직 설정되지 않은 경우 `max_completion_tokens`가 `max_tokens`로 매핑됩니다. `temperature`와 `top_p`는 GLM에서 지원하는 범위로 제한(clamp)됩니다.

GLM은 `tool_choice=auto`만 허용합니다. 강제 선택(forced choice)은 `auto`로 낮춰지며, `ToolChoice.None`은 도구 호출 없음 계약(no-tool-call contract)을 유지하기 위해 요청에서 도구를 제거합니다.

GLM의 `response_format`은 `json_object`만 지원하기 때문에, 구조화 출력은 기본적으로 일반적인 AgentScope 폴백 동작을 사용합니다. 대상 엔드포인트가 필요한 동작을 지원한다고 확인된 경우에만 네이티브 구조화 출력을 활성화하세요.

호환 가능하거나 자체 호스팅되는 엔드포인트의 경우, `ModelCreationContext`를 통해 `baseUrl`, `endpointPath`, 생성 옵션, 또는 포맷터 오버라이드를 전달하세요.
