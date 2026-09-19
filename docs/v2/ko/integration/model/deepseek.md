---
title: DeepSeek Model
---

`agentscope-extensions-model-openai`는 OpenAI 호환 모델 스택을 통해 정식 DeepSeek 지원을 제공합니다. OpenAI 모델 확장 모듈을 추가한 다음, `ModelRegistry`와 함께 `deepseek:<model>`을 사용하세요.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-openai</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## ModelRegistry

`DEEPSEEK_API_KEY`를 설정한 다음, `deepseek:<model>` ID를 사용합니다.

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("deepseek:deepseek-v4-flash") // Resolved internally by ModelRegistry.resolve(modelId)
    .build();
```

이 프로바이더는 기본적으로 `https://api.deepseek.com`을 사용하며, 모델 이름을 전송하기 전에 `deepseek:` 접두사를 제거하고, `io.agentscope.extensions.model.openai.compat.deepseek`의 DeepSeek 포맷터를 사용합니다.

## 사고(Thinking) 모드

모델을 해석(resolve)할 때 `ModelCreationContext`를 통해 DeepSeek 사고 모드를 활성화합니다.

```java
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;

Model model = ModelRegistry.resolve(
    "deepseek:deepseek-v4-flash",
    ModelCreationContext.builder()
        .enableThinking(true)
        .build());
```

스트리밍을 사용하는 호출자는 `ThinkingBlockDeltaEvent`를 `TextBlockDeltaEvent`와 별도로 렌더링할 수 있습니다.

## 호환성 참고 사항

DeepSeek 포맷터는 `system` 역할과 지원되는 `name` 필드를 포함하여 DeepSeek 호환 메시지 필드를 보존합니다. 또한 이전 턴의 오래된 추론(reasoning) 콘텐츠를 제거하면서, 현재 도구 호출 컨텍스트에 필요한 추론 콘텐츠는 유지합니다.

DeepSeek의 안정(stable) 엔드포인트는 기본적으로 도구 스키마의 `strict` 필드를 사용하지 않으므로, 도구가 엄격한 스키마 검증으로 등록된 경우에도 기본 포맷터는 `strict`를 생략합니다. 호환 가능한 엔드포인트에 대해 네이티브 구조화 출력을 명시적으로 구성하지 않는 한, 구조화 출력은 일반적인 AgentScope 폴백 동작을 사용합니다.

베타 또는 호환 엔드포인트의 경우, `ModelCreationContext`를 통해 `baseUrl`, `endpointPath`, 생성 옵션, 또는 포맷터 오버라이드를 전달하세요.
