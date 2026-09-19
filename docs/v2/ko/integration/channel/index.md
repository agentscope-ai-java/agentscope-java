---
title: 채널 어댑터
---

이 확장 기능들은 Harness [Channel](/v2/ko/docs/harness/channel) 인터페이스를 통해 Agent를 실제 메시징 플랫폼에 연결합니다. 각 어댑터는 플랫폼별 인증, 웹훅 검증, 메시지 파싱, 응답 전달을 처리하므로 Agent 코드는 플랫폼에 종속되지 않게 유지됩니다.

| 확장 | 플랫폼 | 전송 방식 |
| --- | --- | --- |
| [DingTalk](/v2/ko/integration/channel/dingtalk) | DingTalk (钉钉) | Stream 프로토콜 (영구 WebSocket) |
| [Feishu](/v2/ko/integration/channel/feishu) | Feishu / Lark (飞书) | Event subscription 콜백 (HTTP) |
| [GitHub](/v2/ko/integration/channel/github) | GitHub | Webhook (HTTP) |
| [GitLab](/v2/ko/integration/channel/gitlab) | GitLab | Webhook (HTTP) |
| [WeCom](/v2/ko/integration/channel/wecom) | WeCom (企业微信) | 암호화된 콜백 (HTTP) |

## 작동 방식

각 채널 어댑터는 동일한 패턴을 따릅니다:

1. **인바운드** — 플랫폼으로부터 메시지를 수신하고(웹훅, WebSocket 등을 통해), 이를 정규화된 `InboundMessage`로 파싱하고, 중복을 제거하고, 봇-루프 방지를 적용한 뒤 Gateway를 통해 전달합니다.
2. **아웃바운드** — 플랫폼의 전송 API를 통해 Agent의 응답을 플랫폼으로 전달합니다.

모든 어댑터는 `agentscope-extensions-channel-common`의 두 가지 공통 유틸리티를 공유합니다:

- **IdempotencyStore** — 메시지 id로 재시도된 웹훅 전달을 중복 제거합니다.
- **BotLoopGuard** — 폭주하는 봇-대-봇 루프를 방지하는 피어별 속도 제한기입니다.

## 공유 의존성

모든 채널 어댑터는 `agentscope-extensions-channel-common`(전이적으로 포함됨)과 `agentscope-harness`(애플리케이션이 런타임에 제공)에 의존합니다.
