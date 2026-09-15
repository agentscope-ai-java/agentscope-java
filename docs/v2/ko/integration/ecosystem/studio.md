# AgentScope Studio

`agentscope-extensions-studio`는 Agent를 [AgentScope Studio](https://github.com/agentscope-ai/agentscope-studio)와 통합합니다: 모든 Agent 호출이 시각적 디버깅, 트레이스 리플레이, human-in-the-loop 입력을 위해 Studio로 전송됩니다.

## 사용 시점

- 개발 중에 이벤트 스트림, reasoning, 도구 호출을 Studio에서 확인하고 싶을 때.
- Studio에서 `requestUserInput`을 실행하여 실제 사용자가 응답하도록 하고 싶을 때.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-studio</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 빠른 시작

```java
import io.agentscope.core.studio.StudioManager;
import io.agentscope.core.studio.StudioMessageHook;

// 1) Initialize Studio connection (HTTP + WebSocket)
StudioManager.init()
    .studioUrl("http://localhost:8000")
    .project("MyProject")
    .runName("experiment_001")
    .initialize()
    .block();

// 2) Attach StudioMessageHook so messages are pushed to Studio
ReActAgent agent = ReActAgent.builder()
    .name("Assistant")
    .model(model)
    .hook(new StudioMessageHook(StudioManager.getClient()))
    .build();

// 3) Use the Agent normally; Studio mirrors the conversation
agent.call(msg).block();
```

## Studio가 제공하는 것

- **메시지 전송**: 모든 사용자 / 어시스턴트 / 도구 메시지가 Studio로 미러링됩니다.
- **트레이스**: Studio는 이벤트를 `runName` 단위의 트레이스 트리로 구성합니다.
- **Human-in-the-loop**: `StudioUserAgent` 또는 `requestUserInput`을 통해, Studio의 UI가 실행을 계속하기 전에 실제 사용자에게 입력을 요청합니다.

## API 개요

| 클래스 | 목적 |
| --- | --- |
| `StudioManager` | 싱글턴 진입점 — 클라이언트를 초기화하고 접근 |
| `StudioConfig` | URL / 프로젝트 / runName 설정 |
| `StudioClient` | 이벤트, 메시지, 실행 등록을 위한 HTTP 클라이언트 |
| `StudioWebSocketClient` | 인바운드 명령(예: 사용자 입력)을 위한 WebSocket 클라이언트 |
| `StudioMessageHook` | `Msg`를 자동으로 전송하는 `ReActAgent`용 `Hook` |
| `StudioUserAgent` | Studio 사용자 입력을 기다리는 "사람이 직접 조작하는" Agent |

## 비활성화해야 할 때

프로덕션 환경에서는 일반적으로 이 훅을 연결하지 않는 것이 좋습니다(모든 호출이 Studio에 기록되기 때문입니다). Spring 프로필이나 `@ConditionalOnProperty`를 통해 게이트를 거세요.

```java
@Bean
@ConditionalOnProperty("agentscope.studio.enabled")
StudioMessageHook studioHook() {
    StudioManager.init().studioUrl(url).project(project).initialize().block();
    return new StudioMessageHook(StudioManager.getClient());
}
```
