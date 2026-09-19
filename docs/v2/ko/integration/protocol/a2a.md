---
title: A2A (Agent-to-Agent)
---

`agentscope-extensions-a2a`는 [A2A protocol](https://a2aproject.github.io/A2A/)을 구현하며 두 개의 하위 모듈을 제공한다:

- `agentscope-extensions-a2a-client`: 원격 A2A 에이전트를 로컬 `Agent`로 감싸서 바로 `call(...)`할 수 있게 한다.
- `agentscope-extensions-a2a-server`: 로컬 `ReActAgent`를 A2A 서버로 노출한다.

두 모듈은 서로 독립적이다 — 하나만 단독으로 사용해도 된다.

## 클라이언트: 원격 A2A 에이전트 호출하기

### 의존성 추가하기

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-a2a-client</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

### AgentCard를 직접 전달하기

```java
import io.a2a.spec.AgentCard;
import io.agentscope.core.a2a.agent.A2aAgent;

AgentCard card = AgentCard.builder()
    .name("remote-translator")
    .url("http://other-service:8080")
    // ...
    .build();

A2aAgent remote = A2aAgent.builder()
    .name("remote-translator")
    .agentCard(card)
    .build();

Msg result = remote.call(new UserMessage("Translate to English: 你好")).block();
```

### well-known을 통한 자동 탐색

```java
import io.agentscope.core.a2a.agent.card.WellKnownAgentCardResolver;

WellKnownAgentCardResolver resolver = new WellKnownAgentCardResolver(
    "http://127.0.0.1:8080",
    "/.well-known/agent-card.json",
    Map.of()
);

A2aAgent remote = A2aAgent.builder()
    .name("remote")
    .agentCardResolver(resolver)
    .build();
```

`A2aAgent`는 `AgentBase`의 서브클래스이므로 Pipeline, MsgHub, Subagent 등과 자연스럽게 조합된다.

## 서버: ReActAgent를 A2A 서버로 노출하기

### 의존성 추가하기

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-a2a-server</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

### 서버 구성하기

```java
import io.agentscope.core.a2a.server.AgentScopeA2aServer;
import io.agentscope.core.a2a.server.transport.jsonrpc.JsonRpcTransportProperties;

ReActAgent.Builder agentBuilder = ReActAgent.builder()
    .name("backend-agent")
    .model(model);

AgentScopeA2aServer server = AgentScopeA2aServer.builder()
    .agentBuilder(agentBuilder)
    .transportProperties(new JsonRpcTransportProperties())
    // .agentCard(customCard)
    // .agentRegistry(myRegistry)
    .build();

// 웹 프레임워크의 인바운드 요청을 transport wrapper로 위임한다
TransportWrapper wrapper = server.getTransportWrapper("JSONRPC");
// ... Spring/Quarkus 컨트롤러가 wrapper.handle(...)로 전달한다

server.postEndpointReady();   // 웹 서버가 리스닝을 시작한 뒤 호출한다 — 등록 등을 트리거한다
```

`AgentScopeA2aServer`는 스스로 포트를 바인딩하거나 엔드포인트를 노출하지 않는다. 컴포넌트와 요청 처리 체인을 조립할 뿐이다. transport는 원하는 대로 Spring Boot, Quarkus, Vert.x 등에 연결하면 된다.

### 선택적 컴포넌트

- `TaskStore` / `QueueManager`: 작업 및 이벤트 큐 저장소. 기본값은 인메모리이며, 프로덕션에서는 영속적인 버전으로 교체한다.
- `PushNotificationConfigStore` / `PushNotificationSender`: 아웃바운드 알림.
- `AgentRegistry`: `AgentCard`를 Nacos와 같은 외부 레지스트리에 등록한다([Nacos](/v2/ko/integration/infrastructure/nacos) 참고).

## Spring Boot Starter

Spring Boot를 사용 중이라면 `agentscope-spring-boot-starter-a2a-server`를 사용하는 것이 좋다 — 서버와 컨트롤러를 자동으로 구성해준다. [Quickstart](/v2/ko/docs/quickstart)를 참고한다.
