---
hide-toc: true
---

# AgentScope Service 기술 딥다이브: Control Plane, Data Plane, 그리고 복구 가능한 Agent 런타임

출시 발표문이 "AgentScope Service가 무엇을 할 수 있는가"에 답한다면, 이 글은 "어떻게 만들어졌는가"에 초점을 맞춘다. 우리는 제품 리소스 모델, 플레인 경계, Turn 생명주기, Brain / Hands 분리, Session 이벤트 계약, 그리고 다중 프레임워크 통합 경로를 훑어보며 이 플랫폼 뒤에 있는 시스템 설계를 설명한다.

제품 개요와 역량 요약은 함께 발행된 글을 참고하라: [AgentScope Service 정식 출시](./agentscope-service-release.md). 이 글은 독자가 이미 AgentScope 2.0 / Harness의 기본을 이해하고 있으며, 실행 가능한 단일 에이전트를 조작 가능한 플랫폼으로 확장하는 데 관심이 있다고 가정한다.

## AgentScope Service란 무엇인가(구현 관점)

구현 관점에서 보면, AgentScope Service는 단일 프로세스가 아니라 경계가 명확한 컴포넌트들의 집합이다.

| 컴포넌트 | 역할 |
| --- | --- |
| `service-gateway` | 외부 진입점: 인증, 라우팅, 공개 API |
| `aistiod` | Go Control Plane: 제품 리소스, 플릿 등록, Session / Team 런타임 상태, 콘솔 백엔드 |
| `service-dataplane` | Java Data Plane: Managed Session Brain, AgentScope Harness 기반으로 Turn을 실행 |
| `service-scheduler` | Channel, Cron, 아웃바운드 태스크, Self-hosted Hands Worker |
| PostgreSQL | 스키마별로 분리된 권위 있는 상태: `cp` / `rt` / `dp` |
| Web Console | Dashboard · Managed Agents · Agent Teams |

이는 동시에 두 가지 워크로드를 서비스한다.

1. **Managed Agents**: Control Plane이 버전이 관리되는 Snapshot을 보유하고, Data Plane이 Snapshot으로부터 `HarnessAgent`를 빌드해 이벤트화된 Turn을 실행한다;
2. **BYO Agents**: 기존의 AgentScope / LangChain / Claude 및 그 밖의 런타임이 익스텐션, `instrument()`, 또는 Sidecar를 통해 연결되어, 동일한 플릿과 Session 관측 가능성 모델 안으로 들어온다.

Control Plane은 원하는 상태(desired state)와 런타임 상태를 관리하지만, **모델 Turn을 실행하지는 않는다**; 추론 루프는 Data Plane이나 연결하는 측 자체의 Runtime에 머문다. 이 경계는 전체 아키텍처를 관통한다: Control Plane이 "곁에서 모델을 실행"하기 시작하는 순간, 플레인 책임, 스케일링, 장애 도메인이 모두 뒤엉키게 된다.

배포 형태로 보면, 로컬 개발에서는 Kubernetes Reconciler를 비활성화하고 Hosted Product 경로를 사용할 수 있다; 프로덕션에서는 Aistio의 CRD / Workload 역량을 활성화하여 선언적 에이전트와 플릿 거버넌스를 클러스터에 연결할 수 있다. 제품 브랜드는 여전히 Agent Service이며, 하부의 Control 컴포넌트는 `aistiod`다.

## 왜 이런 플랫폼이 필요한가

에이전트 루프만 놓고 보면, 오늘날의 프레임워크는 이미 데모를 작성하기에 충분하다. 어려운 부분은 "동작한다"를 "운영 가능하다"로 바꾸는 것이다.

1. **로컬 스크립트 / CLI**: 상태가 로컬 디렉터리에 존재하며, 개인에게는 좋지만 멀티 레플리카나 감사 시나리오에는 맞지 않는다.
2. **비즈니스 서비스에 내장된 SDK**: 모든 애플리케이션이 SessionStore, HITL, 리스, 이벤트 리플레이, 권한을 스스로 재구현하게 되어, 비용이 중복되고 표준이 갈라진다.
3. **로우코드 오케스트레이션**: Harness 엔지니어링 세부 사항을 비즈니스 설정자에게 노출하여, 통합된 플랫폼 업그레이드를 어렵게 만든다.
4. **단일 관리형 런타임**: 잘 동작하지만, 프레임워크를 넘나드는 플릿, 고객 VPC의 Hands, 팀 협업 상태가 종종 별개의 사일로로 끝난다.

또 하나 숨어 있는 비용은 "불명확한 상태 소유권"이다. 많은 사람이 Java/Python 프로세스 안의 에이전트 객체, 프론트엔드 SSE 스트림, 데이터베이스의 채팅 이력을 혼동한다. 객체는 폐기될 수 있고, 스트림은 끊길 수 있다; 오직 번호가 매겨져 영속화된 이벤트와 복구 가능한 상태 저장소만이 멀티 레플리카, 장애 조치, 감사 리플레이를 지원할 수 있다. AgentScope Service는 이를 비즈니스별 선택 사항으로 남겨두는 대신 기본 계약으로 만든다.

그래서 이 플랫폼의 해법은 세 문장으로 요약할 수 있다.

- **거버넌스 관심사**는 control-plane / data-plane 계약으로 수렴한다;
- **추론 커널**은 AgentScope Harness로 수렴한다;
- **도구 실행 경계**는 Environment(Hands)로 수렴한다.

비즈니스 측은 오직 에이전트의 차이점(프롬프트, 도구, Skill, 권한 정책)만 정의해야 한다; 압축, 복구, 이벤트, 리스, 승인은 플랫폼 역량으로서 진화한다. 플랫폼이 Harness를 업그레이드한 후에는, Managed Agents가 각 워크플로우를 다시 그릴 필요 없이 집단적으로 그 혜택을 받아야 한다.

## 전체 아키텍처

```text
┌────────────────────────────────────────────────────────────────────────────┐
│                              Agent Service                                 │
│                                                                            │
│  Web Console ──► Gateway :8080 ──┬──► aistiod :8081 （CP / RT）            │
│                                  └──► dataplane :8082 （DP Brain）         │
│                                              │                             │
│                                              ▼                             │
│                                     PostgreSQL                             │
│                              cp · rt · dp schemas                          │
│                                              ▲                             │
│                                              │                             │
│                                     scheduler :8083                        │
│                              Channel / Cron / Hands Worker                 │
└────────────────────────────────────────────────────────────────────────────┘
```

### 플레인 책임

| 플레인 | 책임 영역 | 명시적으로 책임지지 않는 영역 |
| --- | --- | --- |
| Gateway | JWT / 공개 라우팅 | 비즈니스 상태, 모델 호출 |
| Control(`aistiod`) | 사용자, Agent 버전, Environment, Session 바인딩, Team, 플릿 인스턴스, 런타임 명령 | 모델 Turn |
| Dataplane | Turn Lease, 이벤트 영속화, SSE, HITL, Snapshot으로부터 Harness 빌드, Work Queue | Catalog 폴백으로서 `cp` 테이블 직접 읽기 |
| Scheduler | Channel, Cron, 아웃바운드 Hands Worker | 추론 루프 |

### 데이터 소유권

각 플레인은 단일 PostgreSQL 서버를 공유할 수 있지만, **테이블은 공유하지 않는다**.

| 스키마 | 소유자 | 데이터 |
| --- | --- | --- |
| `cp` | `aistiod` 제품 API | 사용자, Agent, 버전, Environment, Session, Vault, Memory, Deployment |
| `rt` | Aistio Runtime Store | 플릿 인스턴스, 런타임 Session, Context, Team, Task, Message |
| `dp` | Java Dataplane | Session Event, 조정 상태, HITL, Work Item, data-plane 프로젝션 |

Dataplane은 Control Plane의 내부 API를 통해 Managed Session을 해석하며, 반환된 Agent Snapshot으로부터만 런타임을 빌드한다. Data-plane 레플리카는 수평으로 확장될 수 있지만, 제품 Catalog는 여전히 Control Plane을 source of truth로 삼아, 이중 쓰기와 캐시 드리프트를 피한다. 로컬 Catalog 폴백은 편리해 보일 수 있지만, 장기적으로는 "인스턴스 A는 업데이트되었지만 인스턴스 B는 여전히 오래된 정의로 실행 중"이라는 유령 버그를 만들어내는 경향이 있다.

### 하나의 완전한 Turn 경로

1. 클라이언트가 기존 Session에 `user.message`를 덧붙인다.
2. Dataplane이 Turn Lease를 획득하고 Session을 `running`으로 표시한다.
3. Control Plane이 고정된(pinned) Agent Snapshot, Environment, Workspace, Memory, Vault를 해석한다.
4. `SessionTurnRunner`가 `HarnessAgent.streamEvents`를 실행한다.
5. `agent.message`, `agent.tool_use`, `span.model_request_*`와 같은 권위 있는(authoritative) 이벤트들이 PostgreSQL에 기록된다; 선택적인 Preview Delta는 오직 타이프라이터 효과에만 사용되며 영속화되지 않는다.
6. Session은 `idle`로 돌아가거나, HITL / Tool Result를 위해 일시 정지되거나, 타입이 지정된 오류와 함께 종료된다.

클라이언트는 영속화된 이벤트 시퀀스로부터 다음을 통해 복구한다:

```http
GET /api/sessions/{id}/events/stream?after={seq}
```

증분 재개를 위해서다. 인프로세스 Agent 객체와 Preview Stream은 권위 있는 데이터 소스가 아니다 — 이것이 복구 가능한 Session의 전제다.

Turn Lease의 요점은 동시성 제어다: 어느 순간이든 오직 하나의 실행자만이 해당 Session의 Turn을 진행해야 하며, 이는 중복된 이벤트 시퀀스 번호, 반복된 유료 모델 호출, 또는 HITL 대기 중에 다른 레플리카가 실수로 이어받는 것을 방지한다. Lease 상실/만료 후의 인수 정책은 data-plane 멀티 레플리카 가용성의 핵심 세부 사항이다.

### Brain과 Hands

| Environment | 실행 모드 |
| --- | --- |
| `local` | Dataplane 호스트에서 파일 시스템과 Shell을 실행; 개발용으로만 권장 |
| `sandbox` | E2B 같은 관리형 클라우드 샌드박스 |
| `remote` | 원격 / 분산 파일 시스템, 로컬 Shell 없음 |
| `self_hosted` | Brain은 오직 Tool Schema만 노출; 고객 측 아웃바운드 Worker가 poll / ack / heartbeat / 결과 반환 |

`self_hosted`는 프라이빗 네트워크에 적합하다: Brain은 고객 인트라넷에 대한 인바운드 접근이 필요 없다; 대신 Worker가 능동적으로 아웃바운드로 폴링해 도구 호출을 가져온다. 프로토콜 시맨틱은 안정적인 `tool_use` / `tool_result` 이벤트 폐루프다 — Hands가 다른 곳으로 옮겨가더라도, Brain의 추론 루프를 다시 작성할 필요가 없다.

따라서 보안 및 컴플라이언스 팀은 세 가지 질문을 개별적으로 승인할 수 있다: 모델 컨텍스트의 가시 범위, 도구가 접근 가능한 네트워크와 파일 시스템, Brain으로 반환되는 결과의 데이터 최소화 정책. 이는 "에이전트 컨테이너 전체"를 단일 블랙박스 권한 객체로 취급하는 것보다 실현하기 쉽다.

## 핵심 역량(구현 레이어)

> UI 스크린샷은 제품 글을 참고하라; 여기서는 메커니즘에 초점을 맞춘다.

### Dashboard: 플릿 및 런타임 관측 가능성

Dashboard 데이터는 주로 Control Plane의 Runtime Store와 Data Plane의 이벤트 프로젝션에서 오며, 프론트엔드에서 임시로 집계하지 않는다.

- Agent / Instance 헬스 및 온라인 인벤토리
- Session 단계(예: `active` / `idle` / `compressing` / `archived` / `terminated`)와 Turn 소요 시간
- 컨텍스트 압박, Token 델타, 오류 수
- Team 멤버 상태, 태스크 진행 상황, 생명주기 이벤트

혼동하기 쉬운 두 개념은 분리해야 한다.

- **Session**: 복구 가능한 대화 스레드; `phase`는 그 스레드의 운영 상태를 기술한다.
- **Turn**: 한 사용자 요청부터 응답까지의 실행 단위; 소요 시간 통계는 Turn에 속하며, "활성 시간"으로 오해된 Session의 벽시계 생명주기에 속하지 않는다.

BYO Agent의 경우, 레벨 1 Session Snapshot이 어댑터에 의해 주기적으로 보고된다; Control Plane은 이를 사용해 플릿 통계와 운영(압축, 종료 등)을 수행한다. 필드 시맨틱은 프레임워크 전반에 걸쳐 일관되어야 하며, 그렇지 않으면 Dashboard는 각 어댑터가 제멋대로인 "지표 벽"으로 퇴화한다. Token 지표 역시 절대 스냅샷을 단순 누적하기보다는 델타 집계를 선호해야 한다.

### Managed Agents: 버전 관리되는 정의 + 이벤트화된 Session

제품 리소스 모델은 대략 다음과 같다.

| 리소스 | 목적 |
| --- | --- |
| Agent | 버전 관리되는 시스템 프롬프트, 모델, 도구, MCP, Skill, 협업 설정 |
| Environment | 도구 실행 경계 및 Sandbox / Worker 설정 |
| Session | Agent 버전, Environment, Memory, Vault, 이벤트 스트림의 상태를 갖는(stateful) 바인딩 |
| Memory / Vault | 세션을 넘나드는 문서와 암호화된 자격 증명 |
| Deployment / Channel | Cron, Webhook, 수동 트리거, 메시징 채널 |
| Team | Lead / Member, Message, Task, Plan, 생명주기 |

몇 가지 핵심 설계 선택:

1. **Session 생성은 정적인 바인딩이다**
   생성은 오직 리소스 관계만 기록한다; 첫 번째 `user.message`가 오기 전까지 Agent는 실행되지 않는다.

2. **이벤트 네이티브**
   인바운드 이벤트가 작업을 구동하고, 아웃바운드 이벤트가 진행 상황과 결과를 기술한다. 영속화된 모든 이벤트는 Session 안에서 단조 증가하는 시퀀스 번호를 가지므로, 클라이언트는 중단된 지점부터 재개할 수 있다.

3. **일급 시민으로서의 HITL**
   Ask Policy 도구는 Turn을 일시 정지하고 확인 요청을 발행한다; `user.tool_confirmation`이 이를 계속 진행시키거나 거부하며, 전체 이력을 보존한다.

4. **권위 있는 이벤트 vs Preview**
   SSE는 즉각적인 경험을 위해 `event_start` / `event_delta`를 푸시할 수 있지만, 영속화된 이벤트가 최종본이다. UI 새로고침, 다중 기기 복구, 감사 리플레이는 모두 동일한 source of truth에 의존한다.

5. **버전 고정**
   Session은 Agent의 특정 버전 Snapshot에 바인딩되어, 실행 도중의 핫 업데이트로 인해 재현 불가능한 궤적이 생기는 것을 방지한다. 업그레이드가 필요할 때는 명시적으로 새 Session을 생성하거나 제품이 정의한 업그레이드 경로를 따른다.

Managed Agents는 Control Plane의 Snapshot으로부터 Java Dataplane에서 빌드된다. 하부 구현은 AgentScope 2.0의 `HarnessAgent`를 직접 재사용한다: 컨텍스트 압축, 도구 결과 축출(eviction), 상태 복구, Skills / 서브태스크 등의 엔지니어링 기본값은 제품 레이어에서 또 다른 에이전트 루프로 재구현할 필요가 없다. 제품 레이어가 추가하는 것은 테넌트 리소스, ACL, 이벤트 계약, Turn Lease, HITL 티켓, Environment, Worker 큐다 — 이들이 바로 "프레임워크"를 "플랫폼"으로 바꾸는 비용이다.

### Agent Teams: 세션을 넘나드는 협업 상태 머신

Agent Teams는 멀티 에이전트 협업을 어떤 프로세스의 메모리 안에 있는 임시 채팅이 아니라 Control Plane 리소스로 만든다.

- Lead / Member 토폴로지와 동적 멤버십(개수/화이트리스트 제약)
- 유니캐스트와 브로드캐스트 메시지
- 공유 태스크, Claim / Assign, Plan Approval
- 멤버 Wakeup, 우아한 종료, 생명주기 기한, 장애 복구
- 프로세스와 Session을 넘나들며 보존되는 메시지와 태스크

Managed 멤버의 경우, Wakeup은 더 나아가 data-plane Session / Turn으로 착지할 수 있다; BYO 멤버의 경우, 이는 Control Plane 명령과 어댑터 역량을 통해 조정된다. Team 상태는 주로 `rt` 스키마에 존재하여, 세션별 `dp` 이벤트 로그와의 혼동을 피한다 — Session은 하나의 대화 궤적을 책임지고, Team은 멤버 간 협업을 위한 영속적인 단위다.

실용적인 제약 하나는, Team이 모든 멤버가 동일한 출처에서 온다고 가정하지 않는다는 것이다. Lead는 Managed Harness Agent일 수 있고, Member는 연결된 Coding Agent나 LangChain 서비스일 수 있다. Control Plane은 토폴로지, 태스크, 생명주기를 처리한다; 멤버 측은 오직 협업과 관측 가능성 계약만 충족하면 된다. 이질적인 팀 구성은 "먼저 프레임워크를 통일한 다음 협업을 논하자"는 것보다 엔터프라이즈 현실에 더 가깝다.

## 연결하는 방법

### AgentScope(네이티브)

Java 측은 `agentscope-extensions-aistio`를 통해 연결한다. 이 익스텐션은 Runtime을 Control Plane에 등록하고, Session / Context / 헬스 정보를 보고하며, 운영 명령을 처리한다. 기존 AgentScope 애플리케이션의 경우, 이것이 가장 침습적이지 않고 계약이 가장 완전한 경로다: Managed Agents와 동일한 Dashboard 및 Session 관측 가능성 모델을 공유한다.

양쪽이 동일한 AgentScope 이벤트 및 상태 시맨틱 세트를 공유하기 때문에, 레벨 1 / Context / 압축 역량이 대개 가장 먼저 정렬된다. 이미 `HarnessAgent`를 사용 중이라면, 통합의 한계 비용은 주로 의존성, 등록 설정, 런타임 신원이며, 비즈니스 프롬프트를 다시 작성하는 것이 아니다.

### LangChain

Python SDK는 `aistio.instrument()`를 제공한다. LangChain / LangGraph의 경우, 어댑터는 Callback / Checkpointer 가로채기 지점에 훅을 건다.

```python
import aistio

aistio.instrument(
    app_or_client,
    control_plane="aistiod.aistio-system:9090",
    agent_name="my-langchain-agent",
    namespace="default",
    enable_events=False,  # 레벨 2 이벤트는 기본적으로 꺼져 있음; 필요에 따라 활성화
)
```

설계 원칙은 대역 외(out-of-band) 보고다: 메인 경로가 먼저 성공하고, 보고는 조용히 실패하며 저하되어(degrade), Control Plane의 지연이 비즈니스 추론을 무너뜨리지 않도록 한다. 레벨 1은 플릿 뷰를 지원하기 위해 기본적으로 활성화되어 있다; 더 세밀한 이벤트(레벨 2)는 비용과 컴플라이언스 요구 사항에 따라 활성화할 수 있다. Context 보고는 해시 변경 디바운싱을 사용해 무효한 전체 볼륨 푸시를 줄인다.

### Claude SDK와 Sidecar

Claude Agent SDK 역시 `instrument()`를 사용해, SessionStore와 같은 경로를 데코레이팅함으로써 레벨 1 스냅샷과 압축/종료 역량을 얻는다.

Claude Code나 Qoder와 같이 SDK를 내장하기 불편한 Coding Agent의 경우, **Sidecar**를 사용한다.

- 메인 컨테이너는 원래의 CLI / Agent를 계속 실행한다;
- Sidecar는 로컬 Session 디렉터리(예: `~/.claude/`)와 런타임 상태를 관찰한다;
- 이는 플릿과 Session 정보를 Control Plane에 보고하고, 지원되는 운영 명령을 전달한다;
- 필요할 때는, 노드 간 복구를 지원하기 위해 Session 파일 상태를 외부 스토리지에 동기화한다.

Sidecar는 "에이전트 루프를 재구현"하는 것이 아니다; 이는 바이너리를 바꿀 수 없을 때 Control Plane이 요구하는 최소한의 관측 가능성 및 조작 가능성 표면이다. 이는 또한 우리에게 상기시켜준다: 많은 Coding Agent에게 source of truth는 데이터베이스가 아니라 파일 시스템 안에 있다 — Control Plane은 모든 Runtime이 먼저 PostgreSQL로 마이그레이션하도록 강제하는 대신 이 상태 형태를 이해해야 한다.

### 로컬 시작 및 검증

```bash
export DASHSCOPE_API_KEY=sk-xxx
cd agentscope-service
BUILDER_REBUILD=1 scripts/dev-up.sh
# 콘솔: http://localhost:8080
scripts/smoke.sh
```

최소 세 가지 경로를 검증할 것을 권장한다.

1. Managed Session: `user.message`를 전달하고, 이벤트 스트림으로부터 복구하며, 페이지 새로고침 후 시퀀스가 재개되는지 확인한다;
2. HITL: Ask Policy를 트리거하고, 확인 후 계속 진행하며, 이력이 완전한지 검증한다;
3. `self_hosted`: Worker의 poll / ack / heartbeat / `tool_result` 반환, 그리고 Turn이 올바르게 복구되는지 확인한다.

[`docs/guide/14-validation.md`](../../../agentscope-service/docs/guide/14-validation.md)와 [`docs/guide/02-architecture.md`](../../../agentscope-service/docs/guide/02-architecture.md)의 아키텍처 노트를 참고하라.

## 일찍 피해야 할 구현상의 함정

1. **Preview SSE를 권위 있는 로그로 취급하는 것**
   타이프라이터 효과는 재연결 시 삭제되고 재구축될 수 있다; 감사, 리뷰, 과금은 영속화된 이벤트 시퀀스 번호에 맞춰야 한다.

2. **Data plane이 제품 Catalog를 로컬에 캐시하고 Control Plane에 접근할 수 없을 때 조용히 폴백하게 두는 것**
   단기적으로는 고가용성처럼 보일 수 있지만, 장기적으로는 "알려지지 않은 버전을 실행했다"는 최악의 실패를 만들어낸다. 조용히 오래된 정의를 사용하는 것보다는 관측 가능하게 실패하는 것이 낫다.

3. **Session phase와 Turn 소요 시간을 뒤섞는 것**
   `active` / `idle`은 스레드 상태를 기술한다; 소요 시간은 Turn에 속한다. 그렇지 않으면 Dashboard의 "누가 가장 바쁜가"가 오래 매달려 있는 세션들에 의해 오도된다.

4. **BYO 어댑터에서 병렬 지표를 발명하는 것**
   플릿 KPI는 시맨틱을 공유해야 한다. 새 프레임워크를 적응시킬 때는 먼저 계약을 정렬한 다음 특화 필드를 고려하라.

5. **협업 상태를 흉내 내기 위해 Team 메시지를 멤버의 Session 이벤트 스트림에 욱여넣는 것**
   Session 궤적과 Team 상태 머신의 생명주기는 다르다; 이를 섞으면 복구, 정리, 권한 경계가 모두 깨진다.

이 항목들은 단일체(monolithic) 데모에서는 눈에 띄지 않지만, 멀티 레플리카, 멀티 프레임워크, 멀티 팀 시나리오가 함께 등장하는 순간 프로덕션 인시던트의 온상이 된다.

## 로드맵(엔지니어링 관점)

1. **어댑터 커버리지**
   LangChain, ADK, Claude, Qoder, OpenAI Agents 등에 대해 레벨 1 / 레벨 2 / Context 정렬을 심화하고, 프레임워크 특화 필드를 줄이며, Dashboard KPI가 어디서나 동일한 의미를 갖도록 보장한다.

2. **프로덕션급 멀티테넌시와 거버넌스**
   ACL, 쿼터, 감사, 카나리 릴리스, 키 로테이션, 더 엄격한 Environment 격리 정책; Vault / Memory 생명주기와 접근 경계도 계속 정교해질 것이다.

3. **자동화**
   Deployment / Cron / Webhook / Channel을 "트리거 가능"에서 "오케스트레이션 가능하고, 재생 가능하며, 보상 가능"으로 진화시킨다. 자동화된 Turn이 실패하면, 타입이 지정된 오류, 재시도 정책, 사람이 인수하는 진입점이 있어야 한다.

4. **이벤트 기반 진입점**
   GitHub / GitLab, DingTalk, WeCom 등: 멱등성과 인증 경계를 보존하면서 외부 이벤트를 Session Turn이나 Team Task로 안정적으로 매핑한다. 외부 시스템으로부터의 재시도는 정상이다; 플랫폼은 중복 작업을 방지해야 한다.

5. **Team과 복구**
   동적 멤버십, 계획 승인, 멤버 연결 끊김 복구, 세션을 넘나드는 재시작, Managed / BYO가 섞인 팀의 일관성. 협업 상태 머신은 장애 도메인이 여러 Runtime에 걸쳐 있기 때문에 단일 Session보다 어렵다.

## "그냥 Harness를 내장하는 것"과 무엇이 다른가

여러분의 비즈니스 서비스가 `HarnessAgent`를 직접 내장하고 있다면, 이미 견고한 장기 태스크와 압축 역량을 갖고 있는 것이다. 하지만 일단 멀티테넌트, 멀티 레플리카, 멀티 팀 시나리오에 직면하면, 여전히 다음을 추가해야 한다.

- 버전 관리되는 Agent 정의와 Session 고정;
- 추가 전용(append-only) 이벤트와 커서 기반 재개;
- Turn Lease와 HITL 티켓;
- Environment 전환과 Self-hosted Work Queue;
- 플릿 등록, 컨텍스트 압박, 압축/종료 명령;
- Team 태스크 보드와 세션을 넘나드는 협업 상태.

AgentScope Service는 "Harness 주위에 UI 레이어를 추가하는 것"이 아니다; 이는 이러한 분산 책임들을 안정적인 제품 리소스와 내부 계약으로 바꾼다. Harness는 플랫폼이 에이전트 루프를 다시 작성하지 않아도 되게 해주며, 플랫폼 레이어는 여전히 상태 소유권, 장애 도메인, 거버넌스 경계를 책임진다.

## 맺음말

AgentScope Service의 기술적 핵심은 세 문장으로 요약할 수 있다.

1. **Control Plane은 원하는 상태와 런타임 상태를 관리하고, Data Plane은 Turn을 실행하며, Hands는 도구가 어디에 착지할지를 결정한다**;
2. **영속화된 이벤트 시퀀스가 Session의 source of truth다; 인프로세스 객체는 오직 일회용 캐시일 뿐이다**;
3. **Managed와 BYO는 동일한 플릿 계약을 공유한다; 프레임워크 차이는 Console 전반에 흩어지는 대신 어댑터 안으로 수렴한다**.

"단일 Harness Agent"에서 "조작 가능한 에이전트 플릿"으로 옮겨가는 중이라면, 이 계층화는 많은 중복된 인프라를 제거해준다. [`agentscope-service/README.md`](../../../agentscope-service/README.md)를 직접 읽어보길 권한다; 제품 역량과 온보딩 이야기에 대해서는 [출시 글](./agentscope-service-release.md)로 돌아가라.
