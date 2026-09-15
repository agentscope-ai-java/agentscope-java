# Chat Completions Web

`agentscope-extensions-chat-completions-web`는 AgentScope Agent를 [OpenAI Chat Completions](https://platform.openai.com/docs/api-reference/chat) 호환 API 뒤에 노출시켜, OpenAI SDK, LangChain, LlamaIndex, ChatBox 등이 마치 OpenAI와 통신하는 것처럼 연결할 수 있게 합니다.

## 사용 시점

- 클라이언트를 수정하지 않고 Agent를 "표준 LLM"으로 노출하고 싶을 때.
- OpenAI SSE 형식과 일치하는 도구 호출(tool-call) 이벤트를 포함한 스트리밍이 필요할 때.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-chat-completions-web</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

참고: 이 모듈은 프레임워크에 종속되지 않는 핵심 어댑터만 제공합니다 — HTTP/SSE는 대응하는 Spring Boot 스타터(권장) 또는 직접 작성한 컨트롤러를 통해 연결하세요.

## 핵심 어댑터

```java
import io.agentscope.core.chat.completions.streaming.ChatCompletionsStreamingAdapter;
import io.agentscope.core.chat.completions.model.ChatCompletionsRequest;
import io.agentscope.core.chat.completions.model.ChatCompletionsChunk;
import reactor.core.publisher.Flux;

ChatCompletionsStreamingAdapter adapter = new ChatCompletionsStreamingAdapter();

// Convert OpenAI-style request → Agent invocation, and Agent events → OpenAI chunks
Flux<ChatCompletionsChunk> stream = adapter.stream(agent, request);
```

어댑터는 AgentScope의 `Event` 스트림(`REASONING`과 `TOOL_RESULT` 포함)을 OpenAI 호환 `ChatCompletionsChunk` 객체로 변환합니다.

- 텍스트 델타 → `delta.content`
- 도구 호출 → `delta.tool_calls[]`
- 스트림 종료 → `finish_reason`이 포함된 청크

## Spring Boot에서 SSE로 노출하기

가장 쉬운 방법은 스타터를 사용하는 것입니다 — 컨트롤러를 자동으로 등록합니다. 수동으로 하려면:

```java
@RestController
public class ChatController {
    private final ChatCompletionsStreamingAdapter adapter = new ChatCompletionsStreamingAdapter();
    @Autowired private Agent agent;

    @PostMapping(value = "/v1/chat/completions",
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatCompletionsRequest req) {
        return adapter.stream(agent, req)
            .map(this::toSseLine);  // serialize and wrap "data: ..."
    }
}
```

## 모델 라우팅

OpenAI 클라이언트는 `model` 필드를 전송합니다; 컨트롤러 계층에서 라우팅하세요.

```java
String model = req.getModel();   // e.g. "gpt-4o"; route to different Agents
Agent target = agentRegistry.lookup(model);
return adapter.stream(target, req);
```

## 함께 사용하면 좋은 조합

- 세밀한 UI 렌더링과 이벤트 시맨틱을 위한 **AG-UI**.
- OpenAI 호환성에 초점을 맞춘 일반적인 LLM 스타일 통합을 위한 **Chat Completions Web**.
