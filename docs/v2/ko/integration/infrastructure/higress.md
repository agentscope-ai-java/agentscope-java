# Higress AI 게이트웨이

`agentscope-extensions-higress`는 [Higress](https://higress.io/)에서 MCP(Model Context Protocol)로 게시된 도구를 AgentScope로 가져옵니다. Higress는 게이트웨이 계층에서 도구 검색, 인증, 속도 제한, 관측 가능성을 처리하며, Agent는 그 결과로 만들어진 도구를 호출하기만 하면 됩니다.

## 언제 사용하는가

- 이미 Higress를 AI 게이트웨이로 운영 중이며 그 도구를 Agent에 공급하고 싶을 때.
- 도구 거버넌스(라우팅, 인증, 쿼터)를 Agent 비즈니스 로직에서 분리하고 싶을 때.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-higress</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 빠른 시작

```java
import io.agentscope.extensions.higress.HigressMcpClientBuilder;
import io.agentscope.extensions.higress.HigressMcpClientWrapper;
import io.agentscope.extensions.higress.HigressToolkit;

// 1) Higress가 게시한 MCP 엔드포인트에 대한 클라이언트 생성
HigressMcpClientWrapper client = HigressMcpClientBuilder
    .create("higress")
    .streamableHttpEndpoint("http://gateway/mcp-servers/union-tools-search")
    .build();

// 2) HigressToolkit에 등록 (Higress 클라이언트를 캐싱하는 Toolkit 서브클래스)
HigressToolkit toolkit = new HigressToolkit();
toolkit.registerMcpClient(client).block();

// 3) Agent에서 사용
ReActAgent agent = ReActAgent.builder()
    .name("Assistant")
    .model(model)
    .toolkit(toolkit)
    .build();
```

## 도구를 선택적으로 활성화하기

`HigressToolkit`은 그룹/허용 목록 단위의 세밀한 제어를 위해 표준 `Toolkit`의 플루언트 등록 API를 그대로 재사용합니다.

```java
toolkit.registration()
    .mcpClient(client)
    .enableTools(List.of("search-doc", "fetch-url"))
    .group("knowledge")
    .apply();
```

## 하위 MCP 클라이언트에 접근하기

Higress 전용 확장 기능(예: `HigressToolSearchResult`를 통한 도구 검색)을 호출해야 한다면:

```java
HigressMcpClientWrapper higressClient = toolkit.getHigressMcpClient();
```

> 도구 거버넌스(인증, 속도 제한, 라우팅, 관측 가능성)는 게이트웨이에 있으므로 Agent 쪽에서 다시 구현할 필요가 없습니다.
