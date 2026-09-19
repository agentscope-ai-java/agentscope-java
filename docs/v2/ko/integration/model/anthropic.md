---
title: Anthropic Model
---

`agentscope-extensions-model-anthropic`는 Anthropic 전용 포맷터 및 요청 DTO 지원을 포함하여 Anthropic Claude 모델을 통합합니다.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-anthropic</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## ModelRegistry

`ANTHROPIC_API_KEY`를 설정한 다음, `anthropic:<model>` ID를 사용합니다.

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("anthropic:claude-sonnet-4.5") // Resolved internally by ModelRegistry.resolve(modelId)
    .build();
```

## 명시적 빌더

사용자 지정 엔드포인트, 포맷터, 전송 방식, 프롬프트 캐싱, 사고(thinking), 또는 생성 옵션이 필요할 때 빌더를 사용합니다.

```java
import io.agentscope.extensions.model.anthropic.AnthropicChatModel;

AnthropicChatModel model = AnthropicChatModel.builder()
    .apiKey(System.getenv("ANTHROPIC_API_KEY"))
    .modelName("claude-sonnet-4.5")
    .stream(true)
    .build();
```

## Spring Boot

Spring Boot 애플리케이션에서는 Anthropic 스타터를 사용할 수 있습니다.

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-anthropic-spring-boot-starter</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

전체 빌더 옵션, 포맷터, 자격 증명, 레지스트리 컨텍스트에 대한 세부 내용은 [Model](/v2/ko/docs/building-blocks/model)에서 다룹니다.
