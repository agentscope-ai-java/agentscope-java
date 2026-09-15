# Feishu 채널

`agentscope-extensions-channel-feishu`는 **Event Subscription v2** 콜백 메커니즘을 통해 Agent를 Feishu / Lark(飞书)에 연결합니다. Spring `@RestController`가 웹훅 콜백을 수신하고, 필요한 경우 암호화된 페이로드를 복호화한 뒤 Gateway를 통해 메시지를 전달합니다.

## 사용 시점

- Agent가 1:1 채팅이나 그룹 @-멘션에서 Feishu 봇 메시지에 응답해야 하는 경우.
- 애플리케이션이 이미 Spring Boot로 실행 중인 경우(콜백 컨트롤러가 자동 등록됩니다).

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-channel-feishu</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 사전 준비 사항

1. [Feishu 개발자 콘솔](https://open.feishu.cn/)에서 **Custom App**을 생성합니다.
2. **Bot** 기능을 활성화합니다.
3. **Event Subscription** 콜백 URL이 애플리케이션을 가리키도록 구성합니다:
   `https://your-host/api/channels/feishu/{channelId}/callback`
4. **App ID**와 **App Secret**을 기록해 둡니다. 선택적으로 **Encrypt Key**와 **Verification Token**을 구성할 수 있습니다.

## 빠른 시작

```java
FeishuChannel channel = FeishuChannel.fromProperties(
    "my-feishu",
    ChannelConfig.of("my-feishu", "main"),
    Map.of(
        "appId",     "cli_xxxxx",
        "appSecret", "your-app-secret"
    ));

GatewayBootstrap gw = GatewayBootstrap.builder()
    .agent("main", agent)
    .channel(channel)
    .build();

gw.start();
```

`FeishuCallbackController`는 `/api/channels/feishu/{channelId}/callback`에 자동으로 등록되는 Spring `@RestController`입니다. URL 검증 핸드셰이크를 자동으로 처리합니다.

## 구성 속성

| 속성 | 필수 | 기본값 | 설명 |
|----------|----------|---------|-------------|
| `appId` | 예 | — | Feishu 커스텀 앱 id (cli_xxx) |
| `appSecret` | 예 | — | Feishu 커스텀 앱 시크릿 |
| `encryptKey` | 아니요 | — | AES-256-CBC 암호화 키; 페이로드 암호화를 활성화합니다 |
| `verificationToken` | 아니요 | — | 챌린지 핸드셰이크용 URL 검증 토큰 |
| `callbackPath` | 아니요 | `/api/channels/feishu/{channelId}/callback` | 콜백 URL 경로를 재정의합니다 |
| `apiBase` | 아니요 | `https://open.feishu.cn` | Feishu Open API 기본 URL |

## 암호화

`encryptKey`가 구성되면 콜백 본문은 `{"encrypt":"<base64>"}` 형태로 도착합니다. 어댑터는 이를 자동으로 복호화(SHA-256 키 유도를 사용하는 AES-256-CBC)하고 `X-Lark-Signature` 헤더를 검증합니다.

## 메시지 흐름

**인바운드:** `FeishuCallbackController` → 선택적 복호화 → URL 검증 확인 → event_id 중복 제거 → `FeishuInboundMapper`(MVP에서는 텍스트 메시지만 지원) → 봇-루프 가드 → Gateway.

**아웃바운드:** `FeishuOutboundClient`는 `FeishuAccessTokenProvider`의 `tenant_access_token`을 사용하여 `POST /open-apis/im/v1/messages`로 응답을 전송합니다. 토큰은 캐싱되며 TTL의 약 80% 시점에 사전에 갱신됩니다.
