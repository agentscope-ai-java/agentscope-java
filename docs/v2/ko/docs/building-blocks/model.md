---
title: "Model"
description: "AgentScope Java에서 LLM 모델 프로바이더를 설정하고 연결하기"
---

## 개요

모델 계층은 공통 계약(contract)과 프로바이더별 구현을 분리한다. `agentscope-core`는 공통 API(`Model`, `ChatModelBase`, `Formatter`, `ModelRegistry`, 그리고 `ModelProvider` SPI)만 보관하며, OpenAI, DashScope, Gemini, Anthropic, Ollama 구현은 각자의 모델 확장 모듈에 위치한다.

런타임에서 모델 계층은 2단 구조를 이룬다. 최상단에는 **Credential**(`io.agentscope.core.credential` 기반)이 있으며 프로바이더의 API 인증 필드를 담는다. 그 아래에는 **Chat Model**이 있으며 하나의 credential에 연결된 구체적인 추론(inference) 구현이다.

```text
CredentialBase/
└── ChatModelBase/
    ├── OpenAIChatModel
    ├── AnthropicChatModel
    ├── DashScopeChatModel
    ├── GeminiChatModel
    └── OllamaChatModel
```

**Credential**은 프로바이더의 API 인증 필드(`apiKey`, `baseUrl` 등)를 담는다. credential을 기점으로 `listModels()`를 호출하면 해당 프로바이더에서 사용 가능한 모델 목록을 열거할 수 있다(반환 타입은 `Mono<List<ModelCard>>`).

이 계층 구조는 프런트엔드의 자연스러운 UX와 맞물린다 — 먼저 credential을 등록하고, 그 아래에서 모델을 선택한다. 따라서 UI는 한 번만 인증하고 해당 프로바이더가 지원하는 모든 항목을 보여줄 수 있다.

## 모델 확장 모듈

프로바이더별 모델 구현은 `agentscope-core`에서 분리되어 독립적인 확장 모듈로 이동했다. 각 프로바이더 모듈은 자신의 chat model, credential, formatter, DTO, exception, SDK/API 클라이언트 등을 소유한다.

| 프로바이더 | Maven artifact | 주요 패키지 |
|----------|----------------|--------------|
| OpenAI | `agentscope-extensions-model-openai` | `io.agentscope.extensions.model.openai` |
| DashScope | `agentscope-extensions-model-dashscope` | `io.agentscope.extensions.model.dashscope` |
| Gemini | `agentscope-extensions-model-gemini` | `io.agentscope.extensions.model.gemini` |
| Anthropic | `agentscope-extensions-model-anthropic` | `io.agentscope.extensions.model.anthropic` |
| Ollama | `agentscope-extensions-model-ollama` | `io.agentscope.extensions.model.ollama` |

### 마이그레이션 체크리스트

1. 프로바이더 확장 모듈 의존성을 추가한다. 예를 들어 DashScope의 경우:

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-dashscope</artifactId>
</dependency>
```

다른 프로바이더 artifact도 동일한 패턴을 따른다: `agentscope-extensions-model-openai`, `agentscope-extensions-model-gemini`, `agentscope-extensions-model-anthropic`, `agentscope-extensions-model-ollama`.

2. 프로바이더 관련 import를 `io.agentscope.core.model.*`에서 `io.agentscope.extensions.model.<provider>.*`로 교체한다.
3. 프로바이더 formatter import를 `io.agentscope.core.formatter.<provider>.*`에서 `io.agentscope.extensions.model.<provider>.formatter.*`로 교체한다.
4. Spring Boot 애플리케이션의 경우, 범용 모델 생성 경로를 해당 프로바이더 전용 스타터와 그 `agentscope.<provider>.*` 프로퍼티로 교체한다.

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-dashscope-spring-boot-starter</artifactId>
</dependency>
```

## 생성 경로 선택

### 문자열 모델 id

간단한 non-Spring 애플리케이션이라면 `dashscope:qwen-plus`, `openai:gpt-4.1-mini`, `deepseek:deepseek-v4-flash`와 같은 `ModelRegistry` 문자열 id를 사용한다. 해당 모델 확장 모듈을 추가하고, `DASHSCOPE_API_KEY`, `OPENAI_API_KEY`, `DEEPSEEK_API_KEY` 등 프로바이더의 표준 환경 변수를 설정한 뒤 id를 agent에 직접 넘기면 된다.

```java
ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .model("dashscope:qwen-plus") // resolved internally by ModelRegistry.resolve(modelId)
                .build();
```

확장 모듈은 Java SPI를 통해 탐색된다. 모델 프로바이더는 `DASHSCOPE_API_KEY`, `OPENAI_API_KEY`, `DEEPSEEK_API_KEY`, `GLM_API_KEY`, `ANTHROPIC_API_KEY`, `GEMINI_API_KEY`와 같은 표준 환경 변수를 읽는다. Ollama는 `OLLAMA_BASE_URL`이 존재하면 이를 읽고, 없으면 로컬 Ollama 엔드포인트를 기본값으로 사용한다.

### 명시적 모델 빌더

커스텀 API 키, base URL, formatter, transport, timeout, generation 옵션, 그 외 프로바이더별 설정이 필요하다면 모델을 명시적으로 빌드하고 `Model` 인스턴스를 agent에 넘긴다.

```java
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;

DashScopeChatModel model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .stream(true)
                .formatter(new DashScopeChatFormatter())
                .build();

ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .model(model)
                .build();
```

### Spring Boot 애플리케이션

Spring Boot에서는 `agentscope-openai-spring-boot-starter`, `agentscope-dashscope-spring-boot-starter`, `agentscope-gemini-spring-boot-starter`, `agentscope-anthropic-spring-boot-starter`, `agentscope-ollama-spring-boot-starter`와 같은 프로바이더 전용 스타터를 우선 사용한다. 이 스타터들은 해당 모델 확장에 직접 의존하고, Spring이 관리하는 `Model` 빈을 생성하며, 범용 스타터는 AgentScope의 공통 인프라에만 집중하도록 남겨둔다. 이 스타터들은 정적 `ModelRegistry`를 통해 모델을 생성하지 않는다. 고급 사용자는 언제든 자신만의 `Model` 빈을 직접 제공할 수 있다.

OpenAI 예시:

```yaml
agentscope:
  model:
    provider: openai
  openai:
    api-key: ${OPENAI_API_KEY}
    model-name: gpt-4.1-mini
    stream: true
```

#### 빌더 커스터마이저

프로바이더 전용 스타터는 자동 구성된 chat model 빌더에 대해 순서가 있는 Spring 빈 커스터마이저도 제공한다. 프로퍼티 바인딩이 일반적인 설정은 커버하지만 커스텀 formatter, 기본 generation 옵션, 프록시/클라이언트 설정, 프로바이더별 플래그처럼 빌더에만 있는 옵션을 더 조정해야 할 때 사용한다.

| 스타터 | 커스터마이저 타입 |
|---------|-----------------|
| `agentscope-openai-spring-boot-starter` | `OpenAIChatModelBuilderCustomizer` |
| `agentscope-dashscope-spring-boot-starter` | `DashScopeChatModelBuilderCustomizer` |
| `agentscope-gemini-spring-boot-starter` | `GeminiChatModelBuilderCustomizer` |
| `agentscope-anthropic-spring-boot-starter` | `AnthropicChatModelBuilderCustomizer` |
| `agentscope-ollama-spring-boot-starter` | `OllamaChatModelBuilderCustomizer` |

커스터마이저 빈은 스타터 프로퍼티가 바인딩된 이후, `builder.build()`가 호출되기 전에 적용된다. 여러 개의 커스터마이저를 동시에 사용할 수 있으며 Spring의 `@Order` / `Ordered` 순서 규칙을 따른다.

```java
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.spring.boot.openai.OpenAIChatModelBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration(proxyBeanMethods = false)
class ModelCustomizerConfiguration {

    @Bean
    @Order(0)
    OpenAIChatModelBuilderCustomizer openAIModelDefaults() {
        return builder ->
                builder.defaultOptions(
                        GenerateOptions.builder()
                                .temperature(0.2)
                                .parallelToolCalls(false)
                                .build());
    }
}
```

## ModelRegistry와 ModelCreationContext

`ModelRegistry`는 모델 인스턴스 생성과 조회를 위한 전역 레지스트리로, 여러 가지 해석(resolution) 전략을 지원한다. 해석 과정에서는 우선순위 순서로 시도한다: `ModelRegistry.register(name, model)`로 직접 등록된 이름 있는 모델 인스턴스, `registerFactory(regex, factory)`로 등록된 커스텀 팩토리, 그리고 Java SPI 메커니즘을 통해 확장 모듈에서 자동으로 탐색되는 `ModelProvider` 구현체.

단순한 시나리오라면 `provider:model` 형식의 문자열 id와 프로바이더의 표준 환경 변수를 함께 사용하는 편을 우선하고, 세밀한 제어가 필요하다면 명시적 모델 빌더를 사용한다. `ModelCreationContext`는 주로 모델을 동적으로 해석해야 하는 통합 계층 코드를 위한 것이다.

### 고급 통합 컨텍스트

`ModelCreationContext`는 멀티테넌트 게이트웨이, 플러그인 시스템, 프레임워크 어댑터처럼 구체적인 프로바이더 빌더를 import하지 않고도 모델을 동적으로 생성해야 하는 통합 계층을 위한 것이다. API 키, base URL, 엔드포인트 경로, 스트림 모드, 확장에서 정의한 옵션/컴포넌트와 같은 공통 값을 SPI 프로바이더에 전달할 수 있다.

```java
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;

ModelCreationContext context =
        ModelCreationContext.builder()
                .apiKey(tenantApiKey)
                .baseUrl(tenantBaseUrl)
                .stream(false)
                // Extension-defined scalar options, keyed by names the provider documents.
                .option("contextWindowSize", 128000)
                // Type-keyed components for richer provider settings, transports, or formatters.
                .component(
                        GenerateOptions.class,
                        GenerateOptions.builder()
                                .parallelToolCalls(false)
                                .build())
                .build();

Model model = ModelRegistry.resolve("openai:gpt-4.1-mini", context);
```

### 캐시 정책

`ModelRegistry`는 단순한 `provider:model` 문자열로부터 해석된 모델을 캐시한다. 컨텍스트를 사용하는 생성은 서로 다른 테넌트의 API 키, base URL, 스트림 설정을 가진 모델 인스턴스가 재사용되는 것을 막기 위해 기본적으로 캐시되지 않는다.

| 정책 | 동작 |
|--------|----------|
| `DEFAULT` | `resolve(String)`는 기존 방식대로 model-id 캐싱을 유지한다. `resolve(String, nonEmptyContext)`는 캐시되지 않는다. |
| `DISABLED` | 절대 캐시하지 않는다. 모든 해석이 새 모델 인스턴스를 생성한다. |
| `ENABLED` | 호출자가 명시적으로 선택한 경우에만 캐시한다. 테넌트별·설정별 identity에는 `cacheId(...)`를 사용한다. |

`CachePolicy.ENABLED`를 `option(...)` 또는 `component(...)`와 함께 사용하는 경우, 사용자는 반드시 `cacheId`를 제공해야 한다.

### ModelProvider SPI

프로바이더 확장 모듈은 `META-INF/services/io.agentscope.core.model.spi.ModelProvider`를 통한 Java SPI로 탐색된다. 프로바이더는 컨텍스트 값을 사용하기 위해 `supports(String, ModelCreationContext)`와 `create(String, ModelCreationContext)`를 구현할 수 있다. 컨텍스트 인지(aware) 메서드는 호환 가능한 기본 구현을 제공하므로, 단순한 프로바이더는 기존의 `supports(String)`와 `create(String)` 메서드만 구현해도 된다.

## Chat Model

**Chat Model**은 대화와 도구 호출을 이끄는 LLM이며, 입력과 출력이 여러 모달리티에 걸쳐 있을 수 있다. AgentScope Java는 현재 다음을 제공한다.

| 프로바이더 | 클래스 | 비고 |
|----------|-------|-------|
| OpenAI | `OpenAIChatModel` | Chat Completions API; vLLM 및 OpenAI 호환 엔드포인트(DeepSeek, Kimi 등)와 동작 |
| Anthropic | `AnthropicChatModel` | Claude 모델; prompt caching과 thinking |
| DashScope | `DashScopeChatModel` | Qwen 모델; 멀티모달(vision/audio/video), reasoning |
| Gemini | `GeminiChatModel` | Google Gemini; 멀티모달 |
| Ollama | `OllamaChatModel` | 로컬에 호스팅되는 LLM; credential 선택 사항 |

프로바이더별 credential 클래스는 각자의 모델 확장 모듈에 있다. 예를 들어 `OpenAICredential`, `AnthropicCredential`, `DashScopeCredential`, `GeminiCredential`, `OllamaCredential`이다. `DeepSeekCredential`, `KimiCredential`, `XAICredential`과 같은 OpenAI 호환 credential은 계속 core에서 제공된다.

### Chat Model 생성하기

각 chat model은 빌더로 만들어진다. 가장 흔히 쓰이는 필드는 `apiKey`, `modelName`, `stream`, `formatter`, `defaultOptions`다. 세 가지 전형적인 설정 예시:

::::{tab-set}
:::{tab-item} 스트리밍
```java
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;

DashScopeChatModel model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .stream(true)
                .formatter(new DashScopeChatFormatter())
                .build();
```
:::
:::{tab-item} 도구
```java
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;

DashScopeChatModel model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .stream(false)
                .formatter(new DashScopeChatFormatter())
                .defaultOptions(
                        GenerateOptions.builder()
                                .parallelToolCalls(false)
                                .build())
                .build();
```
:::
:::{tab-item} 추론
```java
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;

DashScopeChatModel model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen3-235b-a22b-thinking-2507")
                .stream(true)
                .enableThinking(true)
                .formatter(new DashScopeChatFormatter())
                .defaultOptions(
                        GenerateOptions.builder()
                                .thinkingBudget(2048)
                                .build())
                .build();
```
:::
::::

공통 빌더 필드:

| 필드 | 타입 | 설명 |
|-------|------|-------------|
| `apiKey` | `String` | API 키(일부 프로바이더는 `credential(...)`도 지원) |
| `modelName` | `String` | 모델 식별자(예: `"qwen-plus"`) |
| `stream` | `boolean` | 출력을 스트리밍할지 여부 |
| `defaultOptions` | `GenerateOptions` | 프로바이더별 옵션(`temperature`, `maxTokens`, `thinkingBudget`, `parallelToolCalls` 등) |
| `formatter` | `Formatter` | 기본 메시지 formatter를 오버라이드 |
| `baseUrl` | `String` | 커스텀 서비스 엔드포인트(예: OpenAI 호환 프록시) |

### Chat Model 호출하기

`Model` 인터페이스는 `Flux<ChatResponse>`를 반환하는 통합된 `stream(messages, tools, options)`을 제공한다.

```java
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import java.util.List;

DashScopeChatModel model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .stream(true)
                .formatter(new DashScopeChatFormatter())
                .build();

model.stream(
                List.of(new UserMessage("Count from 1 to 5.")),
                /* tools = */ List.of(),
                GenerateOptions.builder().build())
        .doOnNext(chunk -> System.out.println("Chunk: " + chunk.getContent()))
        .doOnComplete(() -> System.out.println("Stream completed"))
        .blockLast();
```

`ChatResponse`는 콘텐츠 블록 목록(`TextBlock`, `ThinkingBlock`, `ToolUseBlock`, `DataBlock`)과 토큰 수 및 시간을 기록하는 `ChatUsage`를 담고 있다.

실제로는 보통 `ReActAgent`를 통해, 또는 워크스페이스, 세션 지속성, 서브에이전트까지 필요하다면
[`HarnessAgent`](../harness/architecture.md#harnessagent-구축하기)를 통해 모델을 간접적으로 호출한다 — 두 빌더의 `.model(...)`은
위에서 만든 동일한 `ChatModelBase` 인스턴스를 그대로 받는다. 가벼운 직접 호출 예시는
`agentscope-examples/documentation/.../model/ModelRegistryExample.java`를 참고한다.

### 구조화된 출력 생성하기

agent 계층은 `ReActAgent.call(msgs, structuredOutputClass, runtimeContext)`를 통해 모델 출력을 Java POJO에 바인딩하는 편의 오버로드를 제공한다.

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import java.util.List;

public class WeatherInfo {
    public String city;
    public double temperature;
    public String unit;
}

Msg msg =
        agent.call(
                        List.of(new UserMessage("What's the weather in Shanghai?")),
                        WeatherInfo.class,
                        RuntimeContext.empty())
                .block();

WeatherInfo info = msg.getStructuredData(WeatherInfo.class);
```

동작 방식: 프레임워크는 대상 클래스로부터 강제된(forced) 구조화 도구 호출을 합성하고, 모델 출력을 검증·복구한 뒤 결과를 `structured_output` 키 아래 `Msg.metadata`에 기록한다. 그래서 `getStructuredData(Class)`가 이를 바로 역직렬화할 수 있다. 전체 예시: `agentscope-examples/documentation/.../structuredoutput/StructuredOutputExample.java`.

#### 구조화된 출력 경로 선택

프레임워크는 두 가지 구조화된 출력 경로를 제공한다.

| 경로 | 조건 | 메커니즘 |
|------|-----------|-----------|
| **Native** | `supportsNativeStructuredOutput() = true` | 직접 JSON 출력을 위해 `response_format` + `json_schema` 사용 |
| **Fallback**(기본값) | `supportsNativeStructuredOutput() = false` | `generate_response`라는 합성 도구를 주입; 모델이 도구 호출을 통해 구조화된 데이터를 반환 |

native 경로가 실패하면(예: 모델이 HTTP 400을 반환하는 경우), 프레임워크는 **자동으로** 합성 도구 경로로 **폴백**한다 — 사용자 개입이 필요 없다.

#### 프로바이더별 기본 동작

| 프로바이더 | `supportsNativeStructuredOutput` | 비고 |
|----------|----------------------------------|-------|
| OpenAI(GPT-4o 등) | `true` | Native `json_schema` 지원 |
| OpenAI(DeepSeek/GLM formatter) | `false` | 지원하지 않음; 자동 폴백 |
| DashScope | `false` | native 엔드포인트는 `json_object`만 지원하고 `json_schema`는 지원하지 않음; 기본적으로 폴백 |
| Anthropic | `false`(기본값) | — |

> **DashScope 사용자**: Thinking 모드(`enableThinking(true)`)는 구조화된 출력을 전혀 지원하지 않는다 — 프레임워크가 폴백 경로를 강제한다.

#### 명시적 설정

모델/엔드포인트가 `json_schema`를 지원한다고 확신한다면, 빌더를 통해 native 경로를 활성화한다.

```java
DashScopeChatModel model = DashScopeChatModel.builder()
        .apiKey(System.getenv("DASHSCOPE_API_KEY"))
        .modelName("qwen-plus")
        .nativeStructuredOutput(true)  // explicitly enable native json_schema path
        .build();
```

#### 도구 호출과 함께 사용하는 구조화된 출력

agent가 도구와 구조화된 출력을 동시에 사용할 때, 일부 OpenAI 호환 프로바이더(예: Kimi, Deepseek)는 `response_format` 제약을 우선시해서 도구 호출을 아예 건너뛴다. `nativeStructuredOutputWithTools(false)`를 설정해 이를 해결한다.

```java
OpenAIChatModel model = OpenAIChatModel.builder()
        .apiKey("...")
        .baseUrl("https://api.moonshot.cn/v1")
        .modelName("moonshot-v1-8k")
        .nativeStructuredOutputWithTools(false)
        .build();
```

`DashScopeChatModel`도 이 옵션을 지원한다. native OpenAI 모델(GPT-4o 등)의 경우 기본 동작이 두 경우 모두 올바르게 처리하므로 별도 설정이 필요 없다.

### Formatter

**Formatter**는 AgentScope의 `Msg` 객체를 각 프로바이더의 API가 기대하는 요청 페이로드로 변환한다. chat model 빌더의 `formatter(...)`를 통해 설정한다. 각 프로바이더는 두 가지 formatter를 제공한다.

| 타입 | 사용 사례 |
|------|----------|
| **ChatFormatter**(기본값) | 표준적인 단일 agent 대화. 각 `Msg`는 API 메시지 하나에 1:1로 매핑되며 role(`USER`, `ASSISTANT`, `SYSTEM`)을 그대로 유지한다. |
| **MultiAgentFormatter** | 토론이나 moderator 구성 같은 멀티 agent 시나리오. 연속된 agent 메시지들을 집계하고 발신자 이름으로 태그를 붙인다. |

멀티 agent 모드로 전환하려면 MultiAgent 변형을 넘기기만 하면 된다 — agent 코드는 변경할 필요가 없다.

```java
import io.agentscope.extensions.model.dashscope.formatter.DashScopeMultiAgentFormatter;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;

DashScopeChatModel model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .stream(true)
                .formatter(new DashScopeMultiAgentFormatter())
                .build();
```

프로바이더별 formatter는 이제 각자의 프로바이더 확장 모듈에 위치한다.

| 프로바이더 | Chat | MultiAgent |
|----------|------|------------|
| DashScope | `DashScopeChatFormatter` | `DashScopeMultiAgentFormatter` |
| OpenAI | `OpenAIChatFormatter` | `OpenAIMultiAgentFormatter` |
| Anthropic | `AnthropicChatFormatter` | `AnthropicMultiAgentFormatter` |
| Gemini | `GeminiChatFormatter` | `GeminiMultiAgentFormatter` |
| Ollama | `OllamaChatFormatter` | `OllamaMultiAgentFormatter` |

프로바이더의 페이로드가 이들 중 어느 것에도 맞지 않는다면 `Formatter<TReq, TResp, TParams>` 인터페이스(`io.agentscope.core.formatter`)를 구현하고 동일한 `formatter(...)` 빌더로 전달하면 된다.

### 커스텀 프로바이더

새 프로바이더를 추가하는 가장 단순한 경로: `CredentialBase` 서브클래스와 `ChatModelBase` 서브클래스를 구현한다.

#### 1단계: credential 정의

`CredentialBase`를 확장하고 `getChatModelClass()`를 구현한다.

```java
import io.agentscope.core.credential.CredentialBase;
import io.agentscope.core.model.ChatModelBase;

public class MyProviderCredential extends CredentialBase {

    private final String apiKey;
    private final String baseUrl;

    public MyProviderCredential(String apiKey, String baseUrl) {
        super("my_provider:" + apiKey.substring(0, Math.min(4, apiKey.length())));
        this.apiKey = apiKey;
        this.baseUrl = baseUrl == null ? "https://api.myprovider.com/v1" : baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public Class<? extends ChatModelBase> getChatModelClass() {
        return MyProviderChatModel.class;
    }
}
```

#### 2단계: chat model 구현

`ChatModelBase`를 확장하고 `doStream`을 구현한다.

```java
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;
import java.util.List;
import reactor.core.publisher.Flux;

public class MyProviderChatModel extends ChatModelBase {

    private final MyProviderCredential credential;
    private final String modelName;

    public MyProviderChatModel(MyProviderCredential credential, String modelName) {
        this.credential = credential;
        this.modelName = modelName;
    }

    @Override
    protected Flux<ChatResponse> doStream(
            List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        // Call the provider's API, wrap responses into a Flux<ChatResponse>.
        return Flux.empty();
    }
}
```

#### 3단계: ModelRegistry에 등록(선택 사항)

`ModelRegistry`를 사용하면 `ReActAgent.builder().model("provider:model-name")`이 문자열로부터 모델을 해석할 수 있다.

```java
import io.agentscope.core.model.ModelRegistry;

ModelRegistry.registerFactory(
        "myprov:.*",
        modelId -> new MyProviderChatModel(
                new MyProviderCredential(System.getenv("MYPROV_API_KEY"), null),
                modelId.substring("myprov:".length())));

// Then:
// ReActAgent.builder().model("myprov:my-model-v1")...
```

## 프런트엔드 통합

### ModelCard란

`ModelCard`(`credential/ModelCard.java`)는 모델의 기능과 제약을 선언적으로 기술한 것이다. 이를 통해 프런트엔드 — 모델 선택기, 파라미터 폼, 기능 토글 — 가 어떤 프로바이더별 로직도 하드코딩하지 않고도 이를 기반으로 동적으로 렌더링할 수 있다.

현재 `ModelCard`는 최소한의 record다.

| 메서드 | 타입 | 설명 |
|--------|------|------|
| `modelName()` | `String` | 모델 식별자(예: `"claude-sonnet-4-6"`) |
| `displayName()` | `String` | 사람이 읽기 쉬운 라벨(예: `"Claude Sonnet 4.6"`) |
| `contextSize()` | `Integer` | 최대 컨텍스트 윈도우(토큰 단위) |

:::{note}
`ModelCard` 스키마는 현재 단계에서는 의도적으로 최소한으로 유지된다. 모델 탐색 인프라가 성숙해짐에 따라 기능 플래그(입력/출력 MIME 타입)와 파라미터 스키마가 추가될 예정이다.
:::

### ModelCard 가져오기

`CredentialBase#listModels()`를 호출하면 `Mono<List<ModelCard>>`가 반환된다.

```java
import io.agentscope.core.credential.ModelCard;
import io.agentscope.extensions.model.anthropic.credential.AnthropicCredential;
import java.util.List;

AnthropicCredential cred = new AnthropicCredential(System.getenv("ANTHROPIC_API_KEY"));
List<ModelCard> cards = cred.listModels().block();

for (ModelCard card : cards) {
    System.out.println(
            card.modelName() + ": context=" + card.contextSize());
}
```

`getChatModelClass()`는 매칭되는 `ChatModelBase` 서브클래스를 반환한다 — 기본 모델을 리플렉션으로 생성할 때 유용하다.

```java
Class<? extends io.agentscope.core.model.ChatModelBase> modelCls = cred.getChatModelClass();
```

이 설계 덕분에 프런트엔드는 credential 하나만으로 해당 프로바이더에서 사용 가능한 모든 모델을 프로바이더별 하드코딩 로직 없이 발견할 수 있다.
