---
title: WeCom 채널
---

`agentscope-extensions-channel-wecom`는 **암호화된 콜백** 메커니즘을 통해 Agent를 WeCom(企业微信 / WeChat Work)에 연결합니다. Spring `@RestController`가 메시지 콜백을 수신하고 복호화한 뒤 Gateway를 통해 전달합니다.

## 사용 시점

- Agent가 1:1 채팅이나 그룹 채팅에서 WeCom 봇 메시지에 응답해야 하는 경우.
- 애플리케이션이 이미 Spring Boot로 실행 중인 경우.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-channel-wecom</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 사전 준비 사항

1. [WeCom 관리자 콘솔](https://work.weixin.qq.com/)에서 **Application**을 생성합니다.
2. **Receive Messages** API를 활성화하고 콜백 URL을 구성합니다:
   `https://your-host/api/channels/wecom/{channelId}/callback`
3. **Corp ID**, **Agent ID**, **Secret**, **Token**, **EncodingAESKey**를 기록해 둡니다.

## 빠른 시작

```java
WeComChannel channel = WeComChannel.fromProperties(
    "my-wecom",
    ChannelConfig.of("my-wecom", "main"),
    Map.of(
        "corpId",         "your-corp-id",
        "agentId",        "1000002",
        "secret",         "your-secret",
        "token",          "your-callback-token",
        "encodingAesKey",  "your-encoding-aes-key"
    ));

GatewayBootstrap gw = GatewayBootstrap.builder()
    .agent("main", agent)
    .channel(channel)
    .build();

gw.start();
```

## 구성 속성

| 속성 | 필수 | 기본값 | 설명 |
|----------|----------|---------|-------------|
| `corpId` | 예 | — | Enterprise Corp ID |
| `agentId` | 예 | — | Application Agent ID |
| `secret` | 예 | — | 액세스 토큰용 애플리케이션 시크릿 |
| `token` | 예 | — | 서명 검증용 콜백 토큰 |
| `encodingAesKey` | 예 | — | 메시지 암호화/복호화용 AES 키 |
| `callbackPath` | 아니요 | `/api/channels/wecom/{channelId}/callback` | 콜백 URL 경로를 재정의합니다 |
| `apiBase` | 아니요 | `https://qyapi.weixin.qq.com` | WeCom API 기본 URL |

## 암호화

모든 WeCom 콜백은 암호화되어 있습니다. 어댑터는 [WeCom 콜백 암호화 사양](https://developer.work.weixin.qq.com/document/path/90238)을 구현하는 `WeComCrypto`를 사용하여 복호화와 서명 검증을 자동으로 처리합니다.

## 메시지 흐름

**인바운드:** `WeComCallbackController` → URL 검증 (echostr) → 복호화 → MsgId 중복 제거 → `WeComInboundMapper`(텍스트 메시지) → 봇-루프 가드 → Gateway.

**아웃바운드:** `WeComOutboundClient`는 `WeComAccessTokenProvider`의 `access_token`으로 인증하여 `/cgi-bin/message/send`(DM) 또는 `/cgi-bin/appchat/send`(그룹)를 통해 응답을 전송합니다.
