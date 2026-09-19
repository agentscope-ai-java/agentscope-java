---
title: OpenAI 모델
---

`agentscope-extensions-model-openai`는 OpenAI Chat Completions 스타일 모델을 통합합니다. 또한 DeepSeek, GLM, Kimi, MiniMax 등 와이어 포맷이 OpenAI API를 따르는 OpenAI 호환 엔드포인트에도 사용하는 모듈입니다.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-openai</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## ModelRegistry

`OPENAI_API_KEY`를 설정한 다음 `openai:<model>` id를 사용하세요:

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("openai:gpt-4.1-mini") // ModelRegistry.resolve(modelId)에 의해 내부적으로 리졸브됨
    .build();
```

## 명시적 빌더 사용

커스텀 엔드포인트, formatter, transport 또는 생성 옵션이 필요한 경우 빌더를 사용하세요:

```java
import io.agentscope.extensions.model.openai.OpenAIChatModel;

OpenAIChatModel model = OpenAIChatModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("gpt-4.1-mini")
    .stream(true)
    .build();
```

## Spring Boot

Spring Boot 애플리케이션은 OpenAI starter를 사용할 수 있습니다:

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-openai-spring-boot-starter</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

전체 빌더 옵션, formatter, 자격 증명(credentials), 레지스트리 컨텍스트에 대한 세부 내용은 [Model](/v2/ko/docs/building-blocks/model)에서 다룹니다.
