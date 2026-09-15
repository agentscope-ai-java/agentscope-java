# DashScope Model

`agentscope-extensions-model-dashscope`는 멀티모달 및 추론 가능 Qwen 모델을 포함하여 Alibaba Cloud DashScope Qwen 모델을 통합합니다.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-dashscope</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## ModelRegistry

`DASHSCOPE_API_KEY`를 설정한 다음, `dashscope:<model>` 또는 Qwen 축약형을 사용합니다.

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("dashscope:qwen-plus") // Resolved internally by ModelRegistry.resolve(modelId)
    .build();
```

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("qwen-plus") // Resolved internally by ModelRegistry.resolve(modelId)
    .build();
```

## 명시적 빌더

엔드포인트 타입, 사고(thinking), 검색, 암호화 같은 DashScope 전용 옵션이 필요할 때 빌더를 사용합니다.

```java
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;

DashScopeChatModel model = DashScopeChatModel.builder()
    .apiKey(System.getenv("DASHSCOPE_API_KEY"))
    .modelName("qwen-plus")
    .stream(true)
    .build();
```

## Spring Boot

Spring Boot 애플리케이션에서는 DashScope 스타터를 사용할 수 있습니다.

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-dashscope-spring-boot-starter</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

전체 빌더 옵션, 포맷터, 자격 증명, 레지스트리 컨텍스트에 대한 세부 내용은 [Model](../../docs/building-blocks/model.md)에서 다룹니다.
