# GitLab 채널

`agentscope-extensions-channel-gitlab`는 Agent를 GitLab 노트(댓글) 훅에 연결합니다. 누군가 이슈나 머지 리퀘스트에 댓글을 달면 Agent가 새 노트로 응답합니다.

## 사용 시점

- GitLab 이슈나 머지 리퀘스트 댓글에 응답하는 AI 기반 봇을 원하는 경우.
- GitLab SaaS 또는 자체 관리형 GitLab 인스턴스를 운영하는 경우.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-channel-gitlab</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 사전 준비 사항

1. `api` 스코프를 가진 **Personal Access Token**(또는 Project/Group Access Token)을 생성합니다.
2. 프로젝트 또는 그룹에 **webhook**을 구성합니다:
   - URL: `https://your-host/api/channels/gitlab/{channelId}/webhook`
   - Secret token: (선택 사항, 서명 검증용)
   - Trigger: **Note events**

## 빠른 시작

```java
GitLabChannel channel = GitLabChannel.fromProperties(
    "my-gitlab",
    ChannelConfig.of("my-gitlab", "main"),
    Map.of(
        "token", "glpat-xxxxxxxxxxxx"
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
| `token` | 예 | — | GitLab API용 액세스 토큰 |
| `apiBase` | 아니요 | `https://gitlab.com` | API 기본 URL (자체 관리형 인스턴스의 경우 설정) |
| `webhookPath` | 아니요 | `/api/channels/gitlab/{channelId}/webhook` | 웹훅 URL 경로를 재정의합니다 |

## 봇-루프 방지

시작 시, 채널은 `GET /api/v4/user`를 호출하여 봇의 GitLab 사용자 id를 확인합니다. 봇이 작성한 수신 노트는 무한 루프를 방지하기 위해 삭제됩니다.

## 메시지 흐름

**인바운드:** `GitLabWebhookController` → note.id 중복 제거 → 봇 자체 노트 필터 → `GitLabInboundMapper` → 봇-루프 가드 → Gateway.

**아웃바운드:** `GitLabOutboundClient`는 GitLab Notes API를 통해 노트 형태로 응답을 게시합니다.
