---
title: DingTalk 채널
---

`agentscope-extensions-channel-dingtalk`는 **Stream 프로토콜**을 사용하여 Agent를 DingTalk(钉钉)에 연결합니다 — 공개 웹훅 엔드포인트를 노출하지 않고도 봇 메시지를 실시간으로 수신하는 영구 WebSocket입니다.

## 사용 시점

- Agent가 DingTalk 봇 메시지(DM 및 그룹 @-멘션)에 응답해야 하는 경우.
- 폴링이나 웹훅 콜백보다 WebSocket 기반 푸시 모델을 선호하는 경우.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-channel-dingtalk</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 사전 준비 사항

1. [DingTalk 개발자 콘솔](https://open-dev.dingtalk.com/)에서 **Enterprise Internal App**을 생성합니다.
2. **Bot** 기능을 활성화하고 봇-메시지 토픽을 구독합니다.
3. **App Key**, **App Secret**, **Robot Code**를 기록해 둡니다.

## 빠른 시작

```java
DingTalkChannel channel = DingTalkChannel.fromProperties(
    "my-dingtalk",
    ChannelConfig.of("my-dingtalk", "main"),
    Map.of(
        "appKey",    "your-app-key",
        "appSecret", "your-app-secret",
        "robotCode", "your-robot-code"
    ));

GatewayBootstrap gw = GatewayBootstrap.builder()
    .agent("main", agent)
    .channel(channel)
    .build();

gw.start();   // opens the Stream WebSocket and begins dispatching
```

## 구성 속성

| 속성 | 필수 | 기본값 | 설명 |
|----------|----------|---------|-------------|
| `appKey` | 예 | — | Enterprise internal app key |
| `appSecret` | 예 | — | Enterprise internal app secret |
| `robotCode` | 예 | — | 발신자 id로 사용되는 로봇 코드 |
| `apiBase` | 아니요 | `https://api.dingtalk.com` | OpenAPI 기본 URL |
| `streamRegisterUrl` | 아니요 | `https://api.dingtalk.com/v1.0/gateway/connections/open` | Stream 게이트웨이 등록 엔드포인트 |

## 메시지 흐름

**인바운드:** `DingTalkStreamClient`가 DingTalk 게이트웨이로 WebSocket을 열고, 봇-메시지 콜백을 수신하며, 각 프레임을 ACK 처리한 뒤 `DingTalkInboundMapper` → 멱등성 검사 → 봇-루프 가드 → Gateway 순으로 전달합니다.

**아웃바운드:** 응답은 OpenAPI `batchSend` 엔드포인트를 사용하는 `DingTalkOutboundClient`를 통해 전송됩니다 — DM은 `oToMessages/batchSend`, 그룹은 `groupMessages/send`를 사용합니다. 텍스트와 Markdown 형식은 자동으로 감지됩니다.

## 재연결

WebSocket 연결이 끊어지면 Stream 클라이언트는 지수 백오프(1초 → 최대 60초)로 자동 재연결합니다.
