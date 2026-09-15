# GitHub 채널

`agentscope-extensions-channel-github`는 Agent를 GitHub 이슈 및 PR 댓글 스레드에 연결합니다. 누군가 이슈나 풀 리퀘스트에 댓글을 달면 Agent가 새 댓글로 응답합니다.

## 사용 시점

- GitHub 이슈나 PR 댓글에 응답하는 AI 기반 봇을 원하는 경우.
- HMAC-SHA256 웹훅 서명 검증과 봇-루프 자체 감지가 필요한 경우.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-channel-github</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 사전 준비 사항

1. `issues:write`와 `pull_requests:write` 권한을 가진 **Personal Access Token**(PAT)을 생성합니다.
2. 저장소 또는 조직에 **webhook**을 구성합니다:
   - Payload URL: `https://your-host/api/channels/github/{channelId}/webhook`
   - Content type: `application/json`
   - Secret: HMAC-SHA256 서명 검증을 위한 공유 시크릿
   - Events: **Issue comments**와 **Pull request review comments**를 선택합니다

## 빠른 시작

```java
GitHubChannel channel = GitHubChannel.fromProperties(
    "my-github",
    ChannelConfig.of("my-github", "main"),
    Map.of(
        "token",         "ghp_xxxxxxxxxxxx",
        "webhookSecret", "your-webhook-secret"
    ));

GatewayBootstrap gw = GatewayBootstrap.builder()
    .agent("main", agent)
    .channel(channel)
    .build();

gw.start();   // resolves bot identity via GET /user, starts accepting webhooks
```

## 구성 속성

| 속성 | 필수 | 기본값 | 설명 |
|----------|----------|---------|-------------|
| `token` | 예 | — | REST API용 Personal Access Token (PAT) |
| `webhookSecret` | 예 | — | `X-Hub-Signature-256` 검증을 위한 공유 시크릿 |
| `apiBase` | 아니요 | `https://api.github.com` | REST API 기본 경로 (GitHub Enterprise Server의 경우 설정) |
| `webhookPath` | 아니요 | `/api/channels/github/{channelId}/webhook` | 웹훅 URL 경로를 재정의합니다 |
| `botUserLogin` | 아니요 | (자동 확인) | 루프 감지를 위한 봇 계정 로그인을 재정의합니다 |

## 봇-루프 방지

시작 시, 채널은 구성된 PAT로 `GET /user`를 호출하여 봇의 GitHub 사용자 id를 확인합니다. 이 id가 작성한 수신 댓글은 무한한 봇-대-봇 루프를 방지하기 위해 조용히 삭제됩니다.

## 메시지 흐름

**인바운드:** `GitHubWebhookController` → HMAC-SHA256 서명 검증 → 이벤트 타입 필터(`issue_comment`, `pull_request_review_comment`) → comment.id 중복 제거 → 봇 자체 댓글 필터 → `GitHubInboundMapper`(`action=created`만 해당) → 봇-루프 가드 → Gateway.

**아웃바운드:** `GitHubOutboundClient`는 `POST /repos/{owner}/{repo}/issues/{number}/comments`를 통해 응답을 게시합니다.

## 피어 모델

각 이슈/PR 스레드는 id가 `owner/repo#number`인 `THREAD` 피어로 모델링됩니다. 즉, Gateway는 이슈/PR 스레드당 하나의 세션을 생성하며, Agent는 해당 스레드 내의 전체 대화 기록을 볼 수 있습니다.
