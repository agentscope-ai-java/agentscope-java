---
title: Gemini Model
---

`agentscope-extensions-model-gemini`는 Gemini API를 통해 Google Gemini 모델을 통합하며, 명시적 구성을 통해 Vertex AI 경로도 지원합니다.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-gemini</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## ModelRegistry

`GEMINI_API_KEY`를 설정한 다음, `gemini:<model>` ID를 사용합니다.

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("gemini:gemini-2.0-flash") // Resolved internally by ModelRegistry.resolve(modelId)
    .build();
```

## 명시적 빌더

사용자 지정 API 설정, Vertex AI 자격 증명, 포맷터, 전송 방식, 또는 생성 옵션이 필요할 때 빌더를 사용합니다.

```java
import io.agentscope.extensions.model.gemini.GeminiChatModel;

GeminiChatModel model = GeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_API_KEY"))
    .modelName("gemini-2.0-flash")
    .streamEnabled(true)
    .build();
```

## Spring Boot

Spring Boot 애플리케이션에서는 Gemini 스타터를 사용할 수 있습니다.

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-gemini-spring-boot-starter</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

전체 빌더 옵션, 포맷터, 자격 증명, 레지스트리 컨텍스트에 대한 세부 내용은 [Model](/v2/ko/docs/building-blocks/model)에서 다룹니다.
