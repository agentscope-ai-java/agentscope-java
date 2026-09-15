# Ollama 모델

`agentscope-extensions-model-ollama`는 로컬에 호스팅된 Ollama 모델을 통합합니다. 로컬 개발, 사설(private) 배포, 오프라인 모델 서빙에 유용합니다.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-ollama</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## ModelRegistry

`ollama:<model>` id를 사용하세요. `OLLAMA_BASE_URL`은 선택 사항이며, 생략하면 로컬 Ollama 엔드포인트를 기본값으로 사용합니다.

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("ollama:llama3") // ModelRegistry.resolve(modelId)에 의해 내부적으로 리졸브됨
    .build();
```

## 명시적 빌더 사용

기본값이 아닌 Ollama 엔드포인트, formatter, transport, 프록시 또는 Ollama 옵션이 필요한 경우 빌더를 사용하세요:

```java
import io.agentscope.extensions.model.ollama.OllamaChatModel;

OllamaChatModel model = OllamaChatModel.builder()
    .modelName("llama3")
    .baseUrl("http://localhost:11434")
    .build();
```

## Spring Boot

Spring Boot 애플리케이션은 Ollama starter를 사용할 수 있습니다:

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-ollama-spring-boot-starter</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

`agentscope.model.provider=ollama`로 로컬 Ollama 모델을 구성하세요. base URL은 선택 사항이며
기본값은 `http://localhost:11434`입니다:

```yaml
agentscope:
  model:
    provider: ollama
  ollama:
    model-name: llama3
    # base-url: http://localhost:11434
```

전체 빌더 옵션, formatter, 자격 증명(credentials), 레지스트리 컨텍스트에 대한 세부 내용은 [Model](../../docs/building-blocks/model.md)에서 다룹니다.
