---
hide-toc: true
---

# AgentScope Builder — OpenClaw의 "자가 진화"를 팀 전체를 위한 플랫폼으로

AgentScope Java 1.1.0에서 우리는 OpenClaw와 Coding Agent로부터 "워크스페이스가 곧 진실 + 자가 진화"라는 경험을 증류하여 Harness 엔지니어링 기반으로 만들었다: `HarnessAgent` + `AbstractFilesystem` + 내장 압축과 계층화된 메모리. 그때 우리는 한 가지 약속을 했다: **에이전트 로직을 한 번만 작성하고, 필요에 따라 배포 형태를 전환한다 — 개인 노트북에서부터 엔터프라이즈 분산 배포까지**.

`HarnessAgent`는 많은 관심을 끌었고, 많은 개발자가 실제 응용 사례를 요청했다. 오늘 우리는 AgentScope Claw와 AgentScope Builder를 함께 릴리스한다. 이 둘은 AgentScope Harness의 출시 제품이자 구체적인 사례 연구다.

- **agentscope-claw** — "단일 사용자, 로컬" 진영에서의 Harness의 완전한 구현이다. **AgentScope Harness를 사용해, 우리는 Java 버전의 OpenClaw를 구축했다**.
- **agentscope-builder** — "다중 사용자, 엔터프라이즈" 진영에서의 Harness의 완전한 구현이며, 오늘 출시된다. **Builder는 OpenClaw의 분산 버전으로 이해할 수 있다**: 하나의 플랫폼 위에서, 팀 전체가 자가 진화하는 에이전트를 개발·운영·공유할 수 있다.

먼저 AgentScope Claw를 깊이 있게 설명하겠다 — Builder는 허공에서 갑자기 나타난 것이 아니라, Claw가 해결할 수 없었던 엔터프라이즈 요구 사항에서 태어났기 때문이다.

---

## AgentScope Claw — Harness를 사용해 OpenClaw를 만들다

### 이것은 무엇인가

AgentScope Claw는 [OpenClaw]의 가벼운 Java 버전이다: **여러분 자신의 컴퓨터에서 실행되는 개인 비서**다. 이는 여러분을 대신해 행동하며, 여러분의 파일 시스템과 셸 안에서 동작하고, 사용하면서 서서히 "성장"한다 — 학습한 스킬, 생성한 서브에이전트, 축적한 메모리는 모두 워크스페이스에 기록되는 파일이다.

Claw는 다음 저장소 경로에 존재한다.

```
agentscope-examples/agents/agentscope-claw/
```

이는 샘플 코드가 아니라 **완전한 Spring Boot 애플리케이션**이다: JDK 17, `mvn package` 한 번, `java -jar` 한 번이면 되고, 브라우저에서 <http://localhost:8080>을 열면 끝이다. 모든 상태는 `~/.agentscope/` 워크스페이스 아래에 영속화되며, 이는 `CLAW_HOME` 환경 변수로 재정의할 수 있다. 처음 실행하면 내장된 `default` 에이전트가 자동으로 생성되므로, 코드를 한 줄도 작성하지 않고 곧바로 채팅을 시작할 수 있다.

### 세 가지 핵심 역량

Claw를 흥미롭게 만드는 것은 "채팅할 수 있다"는 것이 아니라, 아래 세 가지다 — 그리고 이들은 실제 제품 안에서 Harness의 설계 약속이 처음으로 완전히 표현된 것이다.

**1. 워크스페이스 기반 자가 진화**

Claw의 모든 상태는 데이터베이스가 아니라 `~/.agentscope/claw/workspace/` 아래에 존재한다.

- `AGENTS.md` — 에이전트 페르소나와 행동 계약으로, 매 추론 전에 시스템 프롬프트로 자동 주입된다
- `skills/` — 에이전트 스스로 작성하고 사용하는 재사용 가능한 스킬
- `subagents/` — 서브에이전트 스펙 선언으로, 자동으로 발견되고 로드된다
- `MEMORY.md` + `memory/YYYY-MM-DD.md` — 계층화된 메모리로, 백그라운드 LLM이 자동으로 유지 관리한다
- `agents/<subId>/sessions/` — 전체 대화 로그(JSONL)와 압축된 컨텍스트

매 대화가 끝날 때마다 새로운 사실이 증류되어 일일 메모리 원장에 덧붙여지고, 백그라운드 스케줄러가 주기적으로 이를 `MEMORY.md`로 병합한다. **에이전트의 페르소나, 지식, 스킬을 조정하는 데는 코드 변경이 필요 없다 — 워크스페이스 안의 파일만 편집하면 된다. 파일을 바꾸는 것은 에이전트를 업그레이드하는 것과 같다**. OpenClaw는 이미 이렇게 했고, 이제 Java도 AgentScope Harness 런타임을 등에 업고 이를 할 수 있다.

**2. 로컬 파일 시스템과 셸에 대한 직접 접근**

Claw는 `LocalFilesystemWithShell` 백엔드를 사용한다 — 샌드박스도, 원격 서버도 없다. 모든 읽기, 쓰기, 명령은 로컬 OS를 직접 두드린다. 여러분 자신의 머신에서는 이것이 버그가 아니라 기능이다: "`~/Downloads`에서 세 달 넘은 파일을 아카이브 디렉터리로 옮겨줘"라고 요청하면, 셸을 갖고 있기 때문에 실제로 그 일을 해낼 수 있다.

Harness는 백엔드 기능에 따라 조건부로 도구를 등록한다 — Claw의 로컬 모드에서는 `execute` 셸 도구가 에이전트의 도구 집합에 자동으로 나타나고, 신뢰할 수 없는 환경(예: 뒤에서 설명할 Builder의 원격 모드)으로 전환하면 동일한 에이전트 코드에서 동일한 셸 도구가 자동으로 사라진다. **이것이 "동일한 에이전트 로직, 다른 형태"의 첫 번째 구체적인 시연이다**.

**3. 이미 사용 중인 앱과의 직접 통합**

Claw는 여섯 개의 내장 채널을 즉시 사용 가능한 형태로 제공한다.

| `type` | 전송 방식 | 설명 |
| --- | --- | --- |
| `chatui` | 인프로세스 | 기본 로컬 Web UI |
| `dingtalk` | Stream(WebSocket) | DingTalk 내부 앱, 공개 포트 불필요 |
| `wecom` | HTTP 콜백 + REST API | 자체 호스팅 WeCom 앱 |
| `feishu` | HTTP 이벤트 콜백 + REST API | Feishu 커스텀 앱 + 이벤트 구독 |
| `github` | Webhook + REST API | issue / PR 리뷰 코멘트 이벤트 수신 |
| `gitlab` | Webhook + REST API | Issue / MR Note Hook 수신 |

즉, DingTalk DM이나 GitHub issue 코멘트에서 @claw로 부를 수 있고, 전체 워크스페이스 컨텍스트를 담아 응답한다. 부트스트랩 과정에서 각 에이전트는 `outbound_send` 도구도 등록하여, 어떤 채널로든 **능동적으로** 메시지를 보낼 수 있게 한다 — 서브태스크가 끝나면 `HarnessGateway.tryDispatchAnnounce`가 인바운드 주소를 자동으로 재사용하여, 완료 알림이 이를 트리거한 DingTalk나 WeCom 세션으로 자연스럽게 돌아가게 한다.

채널 레이어는 또한 기본적인 신뢰성 메커니즘 세트를 함께 제공한다 — 멱등 중복 제거, 봇 루프 방지, WeCom 서명 검증, AES-256-CBC 복호화, 액세스 토큰 갱신. 이는 엔터프라이즈 IM 통합에서 "잘못 작성하면 깨지는" 세부 사항들로, 프레임워크가 이미 처리해준다.

### Claw의 경계

Claw는 의도적으로 단순함을 유지한다 — **로그인 없음, 멀티테넌트 격리 없음, Docker 샌드박스 없음, 수평 확장 없음**. 이는 의도적으로 더 많은 것을 하지 않는 것인데, 그런 것들이 "설치하고 바로 실행한다"는 경험을 깨뜨릴 것이기 때문이다.

하지만 이를 팀 안에 두려고 시도하는 순간, 문제들이 연이어 나타난다: 여러 사람이 어떻게 동일한 프로세스를 공유하는가? 자가 진화한 워크스페이스는 사용자별로 어떻게 격리되는가? 멀티 레플리카 배포에서 노드 사이에 사용자의 메모리는 어떻게 일관성을 유지하는가? 사용자가 제출한 코드를 어떻게 안전하게 실행하는가? 좋은 에이전트를 망가뜨리지 않으면서 동료와 어떻게 공유하는가?

이 다섯 가지 문제는 각각으로는 작지만, 합쳐지면 **Claw가 다른 컨테이너로 다시 포장되어야 함**을 의미한다. 이것이 Builder의 출발점이다.

---

## Claw에서 Builder로 — OpenClaw의 엔터프라이즈 배포 형태

Claw는 "한 대의 머신, 한 명의 사용자, 하나의 워크스페이스"를 전제한다. 이 전제를 팀에 그대로 적용하면 동시에 다섯 군데에서 깨진다 — 그리고 그중 어느 것도 "Claw 프로세스를 몇 개 더 돌린다"고 해결되지 않는다.

1. **여러 사람이 하나의 프로세스를 공유하지만, 각자는 자신만의 뷰가 필요하다.** Claw는 현재의 로컬 사용자만 인식한다. 다중 사용자 로그인, 토큰 기반 인증, 사용자별 세션 분리는 모두 Claw의 범위 밖이다.
2. **각 사용자의 워크스페이스는 다른 사람을 오염시켜서는 안 된다.** 에이전트 자가 진화의 부작용은 **파일을 쓴다**는 것이다 — 학습한 스킬, 생성된 서브에이전트, 축적된 `MEMORY.md`. Alice가 튜닝한 에이전트는 Bob이 봐서는 안 될 것을 보게 해서는 안 되며, Bob의 대화가 Alice의 메모리를 덮어써서도 안 된다. 하지만 Claw는 단일 전역 워크스페이스를 사용한다.
3. **멀티 레플리카 배포에서는 동일한 사용자가 일관된 워크스페이스를 보아야 한다.** 두 대의 머신에서 두 개의 Claw 프로세스를 실행한다는 것은 두 개의 격리된 로컬 디스크를 의미한다. 동일한 사용자의 요청이 서로 다른 레플리카에 도달하면 서로 다른 두 개의 메모리를 보게 된다.
4. **서버에서 사용자가 제출한 코드를 실행하려면 OS 수준의 격리가 필요하다.** Claw는 기본적으로 로컬 셸을 활성화한다 — 여러분 자신의 머신에서는 핵심 경험이지만, 멀티테넌트 서비스에서는 직접적인 공격 표면이다.
5. **좋은 에이전트는 망가뜨릴 수 없는 방식으로 공유될 수 있어야 한다.** 팀이 필요로 하는 것은 "전체를 내보내고 다른 사람이 가져가게 하는 것"이 아니라, 세밀한 "이 그룹에게 사용을 허가하지만, 수정은 못 하게 하는 것"이다.

이 다섯 가지 문제는 결국 하나로 귀결된다: **"한 명의 사용자, 한 대의 머신, 하나의 워크스페이스"는 "여러 사용자, 여러 머신, 네임스페이스로 격리된 여러 워크스페이스"가 되어야 한다.** 이는 Claw에 패치를 붙여서 해결할 수 있는 문제가 아니다 — Harness의 워크스페이스 추상화 위에 멀티테넌트 분산 격리 레이어를 구축해야 한다.

---

## Builder 제품 포지션 1 — 멀티테넌트 분산 OpenClaw

Builder는 Claw의 핵심 경험을 **팀 및 엔터프라이즈를 위한** 웹 플랫폼으로 패키징한다. 한 문장으로 요약하면:

> **Builder는 OpenClaw의 분산 버전이다** — 동일한 자가 진화, 동일한 워크스페이스 기반 설계, 동일한 Harness 런타임이지만, "한 사람"에서 "하나의 조직"으로, "한 대의 노트북"에서 "수평으로 확장 가능한 서비스 집합"으로 규모만 확장되었다.

플랫폼 제품으로서, 핵심 역량은 다음과 같다.
1. 하나의 플랫폼에서 여러 사용자를 지원하는 멀티테넌트 분산 버전의 OpenClaw로서, 각 사용자의 에이전트가 격리되고, 멀티 레플리카 배포를 지원하며, 노드 간에 사용자 워크스페이스의 일관성을 유지한다.
2. 코드 한 줄도 작성하지 않고 Web UI에서 에이전트를 생성·튜닝·공유할 수 있는 노코드 에이전트 개발 플랫폼으로, 모든 에이전트 상태는 워크스페이스에 영속화되어 자가 진화를 자동으로 구동한다.

## Builder 제품 포지션 2 — 노코드 에이전트 개발 플랫폼

지난 1년 동안 LangSmith Fleet, Coze, Dify와 같은 플랫폼들이 "노코드 에이전트 구축"이라는 흐름을 촉발했다 — 사용자는 코드를 작성하지 않고도 브라우저 안에서 동작하는 에이전트를 조립할 수 있다. 핵심 경험은 일관적이다: **템플릿 선택 → 파라미터 설정 → 도구 연결 → 게시**로, 에이전트 개발의 진입 장벽을 낮춘다.

Builder도 마찬가지다: **사용자는 브라우저에서 로그인하여 코드 작성 없이 자신만의 에이전트를 만든다**. UI에서 템플릿을 선택하거나(또는 빈 스캐폴드에서 시작하거나), 모델을 고르고, 시스템 프롬프트를 작성하고, 스킬/서브에이전트/도구/MCP 서비스를 체크한 뒤 저장하고 채팅을 시작한다 — 다른 노코드 플랫폼과 마찬가지로 낮은 진입 장벽, 빠른 온보딩, WYSIWYG.

### 진짜 차이점: 생성은 시작점일 뿐, 에이전트는 계속 진화한다

대부분의 노코드 플랫폼은 **정적인 에이전트**를 만들어낸다 — 여러분이 할 수 있는 일을 설정하면, 그것만 영원히 한다. 새로운 일을 시키고 싶다면 관리 패널로 돌아가 수동으로 설정을 바꿔야 한다. 에이전트의 역량 상한선은 생성 시점에 여러분이 생각해낸 모든 것과 같다.

Builder는 다르다. 모든 에이전트 뒤에는 지속적으로 성장하는 워크스페이스가 있다.

- **자동 메모리 축적**: 매 대화가 끝나면, 에이전트는 새로운 사실을 증류해 메모리에 기록한다. 다음번에는 여러분과 여러분의 비즈니스를 이전보다 더 잘 알게 된다. 지식 베이스를 수동으로 갱신할 필요가 없다 — 스스로 성장한다.
- **자동 스킬 습득**: 태스크를 완료하는 동안, 에이전트가 재사용 가능한 워크플로우를 발견하면, 이를 `skills/` 디렉터리 아래의 새 스킬로 구조화할 수 있다. 다음에 비슷한 상황을 만나면, 처음부터 추론하는 대신 학습된 스킬을 호출한다.
- **자동 서브에이전트 생성**: 특정 종류의 서브태스크가 계속 반복되면, 에이전트는 이를 전용 서브에이전트로 분리해(`subagents/`에 기록) 스스로 처리하는 대신 직접 위임할 수 있다.

이 세 가지는 **사용자가 관리 패널로 돌아갈 필요가 없다** — 에이전트가 워크스페이스에 파일을 쓰고, 워크스페이스는 대화 중에 지속적으로 진화한다. **여러분이 설정하는 것은 에이전트의 시작점이지, 상한선이 아니다**.

물론 "자동 진화"가 완전한 자유를 의미하는 것은 아니다. 워크스페이스 안의 모든 것은 **파일: 편집 가능하고, 버전 관리 가능**하다 — 사용자는 UI에서 `AGENTS.md`(페르소나)를 편집하고, `skills/`(스킬 추가/삭제)를 관리하고, `knowledge/`(도메인 지식)를 공급하고, `MEMORY.md`(메모리 수정)를 검토할 수 있다. "노코드"는 "제어 불가"를 의미하지 않는다 — "코드 없이도 에이전트를 제어할 수 있지만, 언제든 파일을 통해 제어할 수 있다"는 것을 의미한다.

### LangSmith Fleet과 같은 플랫폼과의 비교

| | 전형적인 노코드 플랫폼(Fleet / Coze / Dify) | AgentScope Builder |
|---|---|---|
| **에이전트 역량 상한선** | 생성 시점에 설정된 것 | 생성은 시작점이며, 역량은 **지속적으로 성장** |
| **메모리와 스킬** | 단기 메모리 + 수동으로 유지되는 도구 커넥터 | 계층화된 자동 메모리 + 에이전트가 스스로 스킬을 습득 |
| **상태 저장 매체** | 데이터베이스 / 내부 구조체(사용자가 직접 편집 불가) | **워크스페이스 파일** — 읽고, 편집하고, Git으로 버전 관리 가능 |
| **오프라인 마이그레이션** | 플랫폼에 종속 | 워크스페이스 하위 트리 = 표준 파일; 복사해서 그대로 사용 가능 |

**한 문장 요약**: Builder는 "노코드 + 자가 진화"형 에이전트 플랫폼이다 — 진입 장벽은 Fleet/Coze/Dify만큼 낮지만, **여러분이 만드는 것은 정적인 도구가 아니라 성장하는 디지털 비서**다.

---

## Builder의 핵심 메커니즘: CompositeFilesystem

Builder의 구현을 한 문장으로 설명해야 한다면 다음과 같다.

> **Builder는 모든 에이전트를 `HarnessAgent` + `CompositeFilesystem` 위에서 실행한다 — 전자는 에이전트의 런타임 오케스트레이션을 처리하고, 후자는 워크스페이스를 네임스페이스로 격리되고, 배포 가능하며, 샌드박스로 투영 가능한 자산으로 바꾼다.**

구체적인 요청 하나를 따라가며 이 두 요소를 풀어보자.

### HarnessGateway — (사용자, 에이전트)마다 독립된 런타임 하나

웹 레이어 아래에는 **HarnessGateway**가 있다. 그 역할은 간단하다: 각 `(userId, agentId)` 쌍을 독립적인 `HarnessAgent` 인스턴스로 라우팅한다.

- Alice가 `agent-A`를 호출 → `Agent(alice, agent-A)`로 도달
- Alice가 `agent-B`를 호출 → `Agent(alice, agent-B)`로 도달
- Bob이 동일한 `agent-A`를 호출 → `Agent(bob, agent-A)`로 도달 — Alice의 것과는 완전히 독립적

각 `HarnessAgent` 인스턴스는 해당 사용자와 해당 에이전트의 네임스페이스에 바인딩된 `CompositeFilesystem`을 받는다. 다시 말해 — **HarnessGateway는 격리가 어떻게 구현되는지 신경 쓰지 않는다. 그저 "올바른 요청을 올바른 에이전트 인스턴스에 전달"할 뿐이다**. 실제 격리 작업은 다음 레이어에서 일어난다.

### CompositeFilesystem — 워크스페이스를 격리된 자산으로 바꾸기

`CompositeFilesystem`은 Builder 전체를 동작하게 만드는 열쇠다. 이름 그대로다 — **합성 파일 시스템**이다.

```
┌──────────────────────────────────────────────────┐
│  CompositeFilesystem                             │
│                                                  │
│  ┌───────────────────────────────────────────┐   │
│  │  Layer 1: Namespace routing                │  │
│  │    transparently rewrites all paths to    │  │
│  │    users/{userId}/agents/{agentId}/...    │  │
│  └───────────────────┬───────────────────────┘   │
│                      ▼                           │
│  ┌───────────────────────────────────────────┐   │
│  │  Layer 2: Storage backend                  │  │
│  │    local disk / Docker container /        │  │
│  │    remote KV — choose one                 │  │
│  └───────────────────────────────────────────┘   │
└──────────────────────────────────────────────────┘
```

- **상위 레이어는 네임스페이스 라우팅**이다: 에이전트가 `read("AGENTS.md")`를 호출하면, CompositeFilesystem은 현재 `RuntimeContext`에서 `(userId, agentId)`를 읽어 경로를 `users/{userId}/agents/{agentId}/AGENTS.md`로 투명하게 재작성한다. 에이전트 코드는 자신이 "전체 파일 시스템"을 다루고 있다고 생각하지만, **실제로 보는 것은 오직 자신에게만 속한, 네임스페이스로 잘라낸 하위 트리**다.
- **하위 레이어는 물리적 스토리지 백엔드**다: 네임스페이스 라우팅 이후, 데이터가 실제로 어디에 저장되는지는 백엔드 구현에 달려 있다. 기본값은 `LocalFilesystemWithShell`을 통한 호스트 디스크이며, 경로는 `~/.agentscope/builder/workspace/users/{userId}/agents/{agentId}/...`로 해석된다.

핵심은: **에이전트 코드는 이 두 레이어 중 어느 것도 존재하는지 알지 못한다**는 것이다. 여전히 Claw에서와 마찬가지로 Harness의 통일된 `read / write / ls / grep` API를 사용한다. 격리는 비즈니스 코드가 "다른 사람의 디렉터리를 조심스럽게 피하는" 방식이 아니라, CompositeFilesystem 내부에서 구현된다.

### 쓰기 작업의 엔드 투 엔드 워크스루

이 추상화를 구체적으로 보여주기 위해 — Alice가 UI에서 자신의 `agent-A`에게 새로운 스킬을 학습하라고(즉 `skills/sql-helper/SKILL.md`에 쓰기) 요청할 때, 전체 호출 체인은 다음과 같다.

1. **웹 레이어**: JWT 파싱으로 `userId=alice`를 얻고, URL 경로 파싱으로 `agentId=agent-A`를 얻으며, 둘 다 `RuntimeContext`에 첨부된다.
2. **HarnessGateway**: `Agent(alice, agent-A)`의 `HarnessAgent` 인스턴스로 라우팅한다.
3. **에이전트 추론**: 모델이 `write_file("skills/sql-helper/SKILL.md", ...)` 도구를 호출하기로 결정한다.
4. **CompositeFilesystem 상위 레이어**: 호출을 가로채, `RuntimeContext`에서 `(alice, agent-A)`를 읽고, 경로를 `users/alice/agents/agent-A/skills/sql-helper/SKILL.md`로 재작성한다.
5. **CompositeFilesystem 하위 레이어**: 기본 로컬 스토리지는 이 상대 경로를 `~/.agentscope/builder/workspace/`에 덧붙여, 최종적으로 `~/.agentscope/builder/workspace/users/alice/agents/agent-A/skills/sql-helper/SKILL.md`에 디스크로 쓴다.
6. **다음 추론**: 다음 대화가 시작되면, `WorkspaceContextHook`이 시스템 프롬프트를 주입하고 CompositeFilesystem을 통해 `skills/`도 읽어, Alice/agent-A 자신의 하위 트리를 자동으로 찾아낸다. 새로 학습된 스킬이 도구 집합에 나타난다.

Bob의 `agent-A`가 같은 일을 하면, 경로는 `users/bob/agents/agent-A/...`로 재작성된다 — Alice의 것과는 물리적으로 완전히 다른 디렉터리 트리다. **"이게 Alice인가 Bob인가"를 묻는 비즈니스 코드는 존재하지 않는다. 격리는 추상화 레이어로부터 자연스럽게 발생한다**.

### 더 강한 격리가 필요할 때: 동일한 컴포지트 위에 샌드박스를 얹기

Claw의 로컬 모드에서 셸 명령은 호스트를 직접 두드린다. Builder의 기본 로컬 모드도 이를 물려받는다 — 신뢰할 수 있는 팀과 단일 노드 배포에 적합하다. 하지만 여러분의 시나리오가 **신뢰할 수 없는 코드가 에이전트에 유입되는 것**을 포함하게 되면(예: 에이전트가 사용자가 제출한 SQL, Python, 셸 스크립트를 실행하도록 하는 경우), 호스트는 더 이상 이를 직접 받아낼 수 없다.

Builder는 이 시나리오를 위해 "샌드박스 솔루션을 다시 구축"하지 않는다. 대신 **CompositeFilesystem 위에 투영 레이어를 추가**한다.

- 샌드박스 모드로 진입한 후, 런타임 전체가 Docker 컨테이너 안으로 옮겨진다
- 호스트 측은 여전히 워크스페이스의 "본체"로 남으며, CompositeFilesystem은 `AGENTS.md`, `skills/`, `subagents/`, `knowledge/`와 같은 핵심 파일을 호스트에서 컨테이너 안의 `/workspace`로 투영한다
- 컨테이너 안의 에이전트는 호스트와 정확히 동일한 작업대를 본다 — 동일한 `AGENTS.md`를 읽고 동일한 스킬 세트를 사용한다
- 셸 명령은 컨테이너 안에서 실행되며, 호스트 프로세스는 어떤 사용자 입력의 영향도 직접 받지 않는다

주의 — **이것은 "또 다른 파일 시스템"이 아니다**. 이는 하위 레이어에 "호스트 ↔ 컨테이너" 물리적 매핑이 추가된 동일한 CompositeFilesystem이다. 에이전트 코드, 워크스페이스 디렉터리 구조, UI 경험은 변하지 않으며, 셸 명령이 착지하는 경계만 컨테이너 벽으로 옮겨간다.

샌드박스 격리 단위 — 세션당, 사용자당, 에이전트당, 또는 전역 공유 컨테이너 하나 — 는 비즈니스 시나리오별로 설정할 수 있는 실제 배포 결정이다. 기본값인 `USER`(사용자당 하나의 컨테이너, 여러 세션에 걸쳐 공유)는 대부분의 멀티테넌트 SaaS에 합리적인 출발점이다.

### 한 대의 머신으로는 부족할 때: 스토리지 레이어를 분산 백엔드로 교체하기

지금까지 Builder는 여전히 단일 노드였다 — 워크스페이스는 한 대의 머신의 로컬 디스크(또는 그 머신 위 Docker 컨테이너)에 존재한다. Builder를 멀티 레플리카로 만들어, 사용자 요청이 어느 노드에 도달하든 일관된 상태를 보게 하려면, 문제는 처음의 "레플리카들이 워크스페이스를 어떻게 공유하는가"로 돌아간다.

CompositeFilesystem의 해법은 직접적이다: **하위 스토리지 백엔드를 "로컬 디스크"에서 "분산 KV"로 교체한다.** Builder는 `BaseStore` 인터페이스를 추상화하며, 구현체는 Redis, 오브젝트 스토리지(OSS / S3), 또는 여러분 자신의 KV 서비스일 수 있다. 교체 후에는:

- 모든 에이전트 런타임의 읽기/쓰기는 `RemoteFilesystem`을 거쳐 `BaseStore`에 도달한다
- 사용자 워크스페이스를 관리하는 웹 레이어도 동일한 `BaseStore`를 사용한다 — 웹이 보는 것과 에이전트가 보는 것은 같은 데이터다
- 분산 `Session`(전형적인 구현: `RedisSession`)과 결합하면, Builder 프로세스 자체를 동등한 레플리카로 배포할 수 있다

다이어그램의 "네임스페이스 라우팅 상위 레이어"는 전혀 바뀌지 않는다 — 네임스페이스 라우팅은 CompositeFilesystem 내부에서 이루어지며, 스토리지 백엔드가 로컬 디스크든, Docker 컨테이너든, Redis든 이에 대해 전혀 알지 못한다. **바로 이 지점에서 [Harness 아티클](agentscope-v1-harness.md)에서 다룬 `AbstractFilesystem`이 진짜 힘을 발휘한다** — 비즈니스 코드는 단 한 줄도 바뀌지 않고, 배포 측에서 Bean 하나만 교체하면 단일 노드에서 분산 환경으로의 마이그레이션이 완료된다.

---

## Builder 아키텍처 한눈에 보기

```
┌─────────────────────────────────────────────────────────────────────┐
│  AgentScope Builder (Spring Boot, port 8080)                        │
│                                                                     │
│   React SPA ──▶  REST API (JWT)                                     │
│                  │                                                  │
│                  ▼                                                  │
│   ┌──────────────────────────────────────────────────────────────┐  │
│   │  HarnessGateway                                              │  │
│   │   ├─ Agent (alice, agent-A) ──┐                              │  │
│   │   ├─ Agent (alice, agent-B)   │ one HarnessAgent per (user,id)│  │
│   │   └─ Agent (bob,   agent-A) ──┘                              │  │
│   └──────────────────────────────────┬───────────────────────────┘  │
│                                      ▼                              │
│   ┌──────────────────────────────────────────────────────────────┐  │
│   │  CompositeFilesystem                                         │  │
│   │   ├─ Top: namespace routing  (userId, agentId) → subtree     │  │
│   │   └─ Bottom: physical storage backend                        │  │
│   │         · Default: local disk                                │  │
│   │         · Sandbox mode: host ⇄ container projection          │  │
│   │         · Distributed: BaseStore (Redis / OSS / custom)      │  │
│   └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
│   User and agent metadata (default H2; production can use          │
│   MySQL / PostgreSQL)                                               │
└─────────────────────────────────────────────────────────────────────┘
```

전체 다이어그램에서 진짜로 "새로운" 것은 맨 윗줄뿐이다 — React SPA + JWT REST API + HarnessGateway 라우팅. 중간과 하위 레이어는 Harness의 `HarnessAgent`와 `AbstractFilesystem`을 그대로 조합한 것일 뿐이다.

이것이 Builder의 설계 철학이다: **에이전트 런타임을 재발명하지 않고, 멀티테넌트 엔터프라이즈 환경에서 이를 실행하는 데 필요한 운영 셸만 추가한다**.

---

## 빠른 시작

### Claw

```bash
# 1. 모델 API 키 설정(기본값은 DashScope)
export DASHSCOPE_API_KEY=sk-xxx

# 2. 빌드 후 실행
mvn -pl agentscope-examples/agents/agentscope-claw -am clean package -DskipTests
java -jar agentscope-examples/agents/agentscope-claw/target/agentscope-claw-*.jar
```

<http://localhost:8080>을 연다. 기본 홈 디렉터리는 `~/.agentscope`이다. DingTalk / WeCom / Feishu 등의 채널을 연결하려면 `~/.agentscope/agentscope.json`을 편집하고 해당하는 채널 항목을 추가하라. 자세한 내용은 [Claw README]를 참고하라.

### Builder

```bash
export DASHSCOPE_API_KEY=sk-xxx

mvn -pl agentscope-examples/agents/agentscope-builder -am clean package -DskipTests
java -jar agentscope-examples/agents/agentscope-builder/target/agentscope-builder-*.jar
```

서비스는 8080 포트에서 시작된다. `admin/admin`, `bob/bob`, 또는 `alice/alice`로 로그인하면 전체 UI에 접근할 수 있다. 프로덕션 배포(데이터베이스 전환, 샌드박스 이미지, 분산 백엔드)에 대해서는 [Builder README]를 참고하라.

[Claw README]: https://github.com/agentscope-ai/agentscope-java/tree/main/agentscope-examples/agents/agentscope-paw
[Builder README]: https://github.com/agentscope-ai/agentscope-java/tree/main/agentscope-service

---

## Claw vs Builder — 무엇을 선택할 것인가

| | Claw | Builder |
|---|---|---|
| **사용 사례** | 여러분 자신의 노트북 / 워크스테이션에서 동작하는 개인 비서 | 자가 진화하는 에이전트를 함께 구축하고 운영하는 팀 또는 회사 |
| **사용자** | 1명 | 여러 명 — 로그인한 사용자마다 자신만의 워크스페이스 |
| **진입점** | Web UI + DingTalk / WeCom / Feishu / GitHub / GitLab | React SPA + JWT REST API |
| **격리** | 없음 — 여러분 자신으로서 직접 실행 | `(userId, agentId)` 네임스페이스; 선택적 Docker 샌드박스 |
| **공유** | 없음 — 한 대의 머신, 한 명의 사람 | run / edit / fork 3단계 권한 레벨 |
| **분산** | 단일 프로세스, 단일 노드 | BaseStore 백엔드로 전환해 수평 확장 |
| **파일 시스템** | `LocalFilesystemWithShell` | `CompositeFilesystem` |

**두 경로는 상호 배타적이지 않다** — Harness 워크스페이스는 파일이며, `AGENTS.md / skills/ / subagents/` 하위 트리 전체는 버전 관리하고, 코드 리뷰하고, Claw에서 Builder로 그대로 복사할 수 있는 자산이다. 흔한 워크플로우는 다음과 같다: 개발자가 Claw로 자신의 머신에서 만족할 때까지 에이전트를 튜닝하고, 워크스페이스 디렉터리를 템플릿으로 저장소에 제출하면, 운영팀이 이를 Builder를 통해 팀 전체에 배포한다.

---

## 요약

[Harness 아티클](agentscope-v1-harness.md)에서 우리는 "자가 진화하는 에이전트 런타임" — `HarnessAgent` + 워크스페이스 컨벤션 + 플러그형 파일 시스템 + 훅 파이프라인 — 을 제시했다.

오늘 글은 그 런타임을 **곧바로 실행 가능한 두 개의 제품**으로 바꾼다.

- **Claw가 증명하는 것**: AgentScope Harness를 사용하면, 우리는 이미 완전한 Java 버전의 OpenClaw를 만들 수 있다 — 자가 진화, 로컬 셸, 다섯 개의 IM 채널 통합, 이 모두가 `mvn package`로 실행할 수 있는 하나의 Spring Boot 애플리케이션으로 패키징되어 있다.
- **Builder가 증명하는 것**: 동일한 Harness 런타임에 "워크스페이스 격리와 공유"를 위한 운영 셸을 더하면, 팀을 위한 멀티테넌트 플랫폼으로 곧장 진화한다. Claw에서 Builder까지, **에이전트 비즈니스 로직은 단 한 줄도 바뀌지 않았다**. 오직 CompositeFilesystem이 어느 레이어에서 격리를 제공하는지, 어떤 매체에 데이터를 영속화하는지만 바뀌었다.

이것이 바로 Harness가 처음부터 약속했던 것이다: **에이전트 로직을 한 번만 작성하고, 필요에 따라 배포 형태를 전환한다.** 오늘부터, 그 약속은 저장소 안에 두 개의 실행 가능한 증거를 갖게 되었다.

개인 비서가 필요하다면 [Claw README] 빠른 시작으로 시작하고, 팀/회사 플랫폼이 필요하다면 [Builder README]로 시작하라. 두 경로 모두 같은 Harness로 수렴한다 — 그리고 이것이 우리가 이것을 만든 이유다.
