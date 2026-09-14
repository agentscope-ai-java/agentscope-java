---
title: "Channel"
description: "Channel을 통해 메시지를 라우팅하고, 세션을 관리하고, 이벤트를 스트리밍하기"
---

## 하는 일

**Gateway**는 애플리케이션 코드와 agent 사이에 위치하며 다음을 처리한다.

- **세션 관리** — 각 사용자 대화를 안정적인 session id에 매핑한다. agent는 턴이 바뀌어도 일관된 기억을 본다.
- **세션별 동시성 제어** — 같은 세션으로 들어오는 동시 메시지는 공정하게 큐잉되어 agent가 스스로와 경쟁하지 않는다.
- **agent 라우팅** — 다중 agent 구성에서 각 메시지를 올바른 agent로 라우팅한다.

**Channel**은 메시징 플랫폼(HTTP, WebSocket, Slack 등)을 Gateway의 라우팅 모델에 맞게 적응시킨다. 누가 메시지를 보냈는지, 어떤 agent가 이를 처리해야 하는지, 응답을 어디로 전달해야 하는지를 결정한다.

대부분의 사용 사례에서는 Gateway나 Channel을 직접 다룰 필요가 없다 — `agent.channel(...)`이 뒤에서 모든 배선을 처리한다.

## 빠른 시작

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("assistant")
    .sysPrompt("You are a helpful assistant.")
    .model("dashscope:qwen-plus")
    .build();

// Bind a ChatUI channel.
ChatUiChannel chat = agent.channel(ChatUiChannel.create());

// Send messages. Each userId gets its own session automatically.
Msg reply = chat.send(SendOptions.userId("user-1"), "Hello!").block();

// Same user, same session — conversation continues.
Msg followUp = chat.send(SendOptions.userId("user-1"), "Tell me more.").block();

// Different user, different session.
Msg otherUser = chat.send(SendOptions.userId("user-2"), "Hi there").block();
```

`agent.channel(...)`은 내부 gateway를 지연 생성(lazily)하고, agent를 등록하고, gateway를 channel에 주입한다. 이 호출 이후 `chat`은 바로 사용할 수 있다.

### SendOptions

`SendOptions`는 channel에게 **누가** 말하고 있고 이 메시지가 **어느 대화**에 속하는지 알려준다.

| 팩토리 | 동작 |
|---------|----------|
| `SendOptions.userId("user-1")` | 사용자 한 명당 세션 하나(가장 흔한 경우) |
| `SendOptions.of("user-1", "session-a")` | 명시적 세션 — 한 사용자가 여러 대화를 가짐 |
| `SendOptions.userId("user-1").withAgentId("support")` | 다중 agent 구성에서 특정 agent로 라우팅 |
| `SendOptions.userId("user-1").withAttribute("tenant", "acme")` | agent 턴에 문자열/타입 속성을 첨부 |
| `SendOptions.userId("user-1").withRuntimeContext(rtc)` | 완전한 `RuntimeContext`를 전달(예: force-sync 플래그) |

```java
// Same user, two independent conversations
chat.send(SendOptions.of("user-1", "session-a"), "Topic A").block();
chat.send(SendOptions.of("user-1", "session-b"), "Topic B").block();

// Pass application context into the agent turn
chat.send(
        SendOptions.userId("user-1")
                .withAttribute("tenant", "acme")
                .withRuntimeContext(
                        RuntimeContext.builder()
                                .put(AgentSpawnTool.CTX_FORCE_SYNC, true)
                                .build()),
        "Investigate the ticket")
        .block();
```

### 멀티모달 / 구조화된 메시지

일반 텍스트를 다루는 `send(String)`은 편의 메서드일 뿐이다. 이미지, 오디오, 다중 파트 턴을 다루려면 미리 만들어 둔 `Msg`(또는 `List<Msg>`)를 전달한다 — 모든 String 오버로드에는 이에 대응하는 `Msg` / `List<Msg>` 변형이 있다(`SendOptions`와 `sendStream`도 포함).

```java
Msg multimodal = Msg.builder()
        .role(MsgRole.USER)
        .content(
                TextBlock.builder().text("What is in this image?").build(),
                ImageBlock.builder()
                        .source(URLSource.builder().url("https://example.com/photo.png").build())
                        .build())
        .build();

chat.send(SendOptions.userId("user-1"), multimodal).block();
chat.send(SendOptions.userId("user-1"), List.of(multimodal)).block();
chat.send(multimodal).block(); // single-session mode
```

### RuntimeContext 병합

Channel 턴은 항상 Gateway 내부에서 `RuntimeContext`를 만든다. 호출자는 `SendOptions` / `InboundMessage.runtimeContext()` / `ChannelRuntimeContextResolver`를 통해 **caller base**를 제공할 수 있다. 병합 순서:

1. caller 컨텍스트에서 시작한다(비어 있을 수 있음)
2. `ChannelRuntimeContextResolver`가 설정되어 있고 non-null 값을 반환하면, 그 값이 caller base를 **대체**한다
3. Gateway가 identity 필드들 — `sessionId`(`gw-…`), `userId`, `msgContext`, `gateKey`, `outboundAddress` — 를 덧씌우며, 이들은 충돌 시 항상 우선한다

`GatewayBootstrap`을 통해 resolver를 연결한다.

```java
GatewayBootstrap gw = GatewayBootstrap.builder()
        .agent("main", b -> b.name("assistant").model(model))
        .runtimeContextResolver(req ->
                RuntimeContext.builder(req.callerContext())
                        .put("tenant", resolveTenant(req))
                        .build())
        .build();
ChatUiChannel chat = gw.chatUiChannel();
```

또는 gateway를 얻은 뒤 `gateway.setRuntimeContextResolver(...)`를 호출해도 된다. 비즈니스 속성을 `MsgContext.extra`에 넣지 **말라** — 그 map은 session key 계산에 관여한다.

## 스트리밍 이벤트 + SSE

`sendStream()`은 `Flux<AgentEvent>`를 반환한다 — `agent.streamEvents()`와 동일하게 세분화된 이벤트 스트림이지만, 세션 관리를 갖춘 gateway를 통해 라우팅된다.

```java
chat.sendStream(SendOptions.userId("user-1"), "What is the weather in Beijing?")
    .doOnNext(event -> {
        if (event instanceof TextBlockDeltaEvent delta) {
            System.out.print(delta.getDelta());
        } else if (event instanceof ToolCallStartEvent tc) {
            System.out.println("\n[tool] " + tc.getToolCallName());
        }
    })
    .blockLast();
```

### Spring Boot SSE 컨트롤러

```java
@GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> chat(@RequestParam String message,
                                          @RequestParam String userId,
                                          @RequestParam(required = false) String sessionId) {
    SendOptions options = sessionId != null
            ? SendOptions.of(userId, sessionId)
            : SendOptions.userId(userId);

    return chat.sendStream(options, message)
            .map(event -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("type", event.getType().name());
                payload.put("id", event.getId());
                if (event instanceof TextBlockDeltaEvent delta) {
                    payload.put("delta", delta.getDelta());
                } else if (event instanceof SubagentExposedEvent se) {
                    payload.put("subagentId", se.getSubagentId());
                    payload.put("agentId", se.getAgentId());
                    payload.put("label", se.getLabel());
                }
                return ServerSentEvent.<String>builder()
                        .data(objectMapper.writeValueAsString(payload))
                        .build();
            });
}
```

## 노출된 서브에이전트와 대화하기

agent가 `expose_to_user=true`로 서브에이전트를 spawn하면, gateway는 그 서브에이전트를 사용자가 직접 주소 지정할 수 있는 진입점으로 노출한다. `sendStream()` 이벤트 스트림에는 `subagentId`를 담은 `SubagentExposedEvent`가 발행된다.

### 노출된 서브에이전트 찾기

```java
AtomicReference<String> subagentId = new AtomicReference<>();

chat.sendStream(SendOptions.userId("user-1"), "Spawn a researcher to investigate AI trends")
    .doOnNext(event -> {
        if (event instanceof SubagentExposedEvent se) {
            subagentId.set(se.getSubagentId());
            System.out.printf("Subagent exposed: id=%s agent=%s label=%s%n",
                    se.getSubagentId(), se.getAgentId(), se.getLabel());
        }
        if (event instanceof TextBlockDeltaEvent delta) {
            System.out.print(delta.getDelta());
        }
    })
    .blockLast();
```

`SubagentExposedEvent` 필드:

| 필드 | 설명 |
|-------|-------------|
| `subagentId` | 이 서브에이전트에 메시지를 보내기 위한 핸들 |
| `agentId` | 서브에이전트 타입(예: `"researcher"`) |
| `sessionId` | 서브에이전트의 session id |
| `label` | 사람이 읽기 좋은 선택적 이름 |

### 서브에이전트에 메시지 보내기

`subagentId`를 확보했다면, 부모 agent를 완전히 건너뛰고 서브에이전트에 직접 메시지를 보낼 수 있다.

```java
// Non-streaming
Msg reply = chat.sendToSubagent(subagentId, "Focus on LLM agents").block();

// Streaming
chat.sendToSubagentStream(subagentId, "Focus on LLM agents")
    .doOnNext(event -> {
        if (event instanceof TextBlockDeltaEvent delta) {
            System.out.print(delta.getDelta());
        }
    })
    .blockLast();
```

### 서브에이전트를 지원하는 SSE

전형적인 SSE 컨트롤러는 메인 agent 메시지와 서브에이전트 메시지를 모두 처리한다.

```java
@GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> chat(@RequestParam String userId,
                                          @RequestParam String message,
                                          @RequestParam(required = false) String subagentId) {
    Flux<AgentEvent> events;
    if (subagentId != null) {
        events = chat.sendToSubagentStream(subagentId, message);
    } else {
        events = chat.sendStream(SendOptions.userId(userId), message);
    }
    return events.map(event -> toSSE(event));
}
```

클라이언트는 `SUBAGENT_EXPOSED` 이벤트를 감지해 새 대화 탭을 렌더링하고, 이후 요청에서 `subagentId`를 다시 전달한다.

## 다중 agent 라우팅

여러 개의 `HarnessAgent` 인스턴스를 다루는 시나리오라면 `GatewayBootstrap`을 사용한다.

```java
HarnessAgent salesAgent = HarnessAgent.builder()
    .name("sales").sysPrompt("You are a sales assistant.")
    .model("dashscope:qwen-plus").build();

HarnessAgent supportAgent = HarnessAgent.builder()
    .name("support").sysPrompt("You are a support agent.")
    .model("dashscope:qwen-plus").build();

GatewayBootstrap gw = GatewayBootstrap.builder()
    .agent("sales", salesAgent)
    .agent("support", supportAgent)
    .mainAgent("sales")          // default when no agent is specified
    .build();

ChatUiChannel chat = gw.chatUiChannel();
```

### agentId로 라우팅하기

`SendOptions.withAgentId()`를 사용해 메시지를 특정 agent로 라우팅한다.

```java
// Routes to sales (the default main agent)
chat.send(SendOptions.userId("user-1"), "What products?").block();

// Routes to support explicitly
chat.send(SendOptions.userId("user-1").withAgentId("support"), "Billing issue").block();
```

### GatewayBootstrap으로 스레드 노출하기

서브에이전트에서 `expose_to_user`를 활성화하려면, gateway bridge를 각 agent의 서브에이전트 middleware에 연결해야 한다.

```java
GatewayBootstrap gw = GatewayBootstrap.builder()
    .agent("main", mainAgent)
    .build();

// Wire the bridge so agent_spawn(expose_to_user=true) works.
SubagentGatewayBridge bridge = gw.gatewayBridge();
// Pass bridge to the agent's SubagentsMiddleware via setGatewayBridge().
```

`agent.channel(...)`을 사용하면 이 배선은 자동으로 이루어진다.

## 커스텀 Channel

새로운 메시징 플랫폼을 적응시키려면 `Channel` 인터페이스를 구현한다.

```java
public class MySlackChannel implements Channel {
    @Override public String channelId() { return "slack"; }
    @Override public ChannelConfig config() { return myConfig; }
    @Override public void init(Gateway gateway) { this.gateway = gateway; }
    @Override public void start() { /* connect to Slack */ }
    @Override public void stop() { /* disconnect */ }

    @Override
    public Mono<Msg> dispatch(InboundMessage message) {
        RouteResult route = router.resolveRoute(config(), message);
        return gateway.run(route.context(), message.messages(), route.outboundAddress());
    }

    // Optional: streaming dispatch
    @Override
    public Flux<AgentEvent> dispatchStream(InboundMessage message) {
        RouteResult route = router.resolveRoute(config(), message);
        return gateway.runStream(route.context(), message.messages(), route.outboundAddress());
    }
}
```

`GatewayBootstrap`으로 등록한다.

```java
GatewayBootstrap gw = GatewayBootstrap.builder()
    .agent("main", agent)
    .channel(new MySlackChannel())
    .build();

gw.start();   // calls init() + start() on all channels
// ...
gw.stop();    // calls stop() on all channels
```

## 내장 channel 어댑터

AgentScope는 인기 있는 메시징 플랫폼을 위한 사용 준비된 Channel 어댑터를 확장 모듈로 제공한다.

- [DingTalk](../../integration/channel/dingtalk.md) — Stream 프로토콜(영속 WebSocket)
- [Feishu / Lark](../../integration/channel/feishu.md) — 이벤트 구독 콜백
- [GitHub](../../integration/channel/github.md) — Issue / PR 댓글 webhook
- [GitLab](../../integration/channel/gitlab.md) — Note hook
- [WeCom](../../integration/channel/wecom.md) — 암호화된 콜백

자세한 내용은 [Channel 어댑터](../../integration/channel/index.md) 통합 개요를 참고한다.

## 관련 문서

- [Subagent](./subagent.md) — 서브에이전트 선언과 spawn, 백그라운드 작업, 스트리밍 전달
- [Architecture](./architecture.md) — 부모와 자식 agent가 어떻게 협력하는지
