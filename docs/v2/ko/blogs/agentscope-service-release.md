---
title: Agentscope Service Release
---

**AgentScope Service** — AgentScope Harness 위에 구축된 에이전트 컨트롤 플레인.


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785989956180-2b6581fd-cf41-4155-baaf-08db90a6eb5d.png)


+ **AgentScope Service는 컨트롤 플레인이다.** 엔터프라이즈 안의 모든 에이전트를 위한 에이전트 등록, 디스커버리, 분산 조정 서비스를 제공한다. AgentScope, LangChain, ADK, Claude / Qoder를 포함한 주류 에이전트 런타임과 함께 동작하며, 에이전트 지표를 한곳에서 조회하고 라이브 세션에 대한 조작(예: 세션 컨텍스트 압축)을 할 수 있는 단일한 장소를 제공한다.
+ **AgentScope Service는 노코드 Agent 생성 및 배포를 제공한다.** AgentScope Harness 런타임 위에 구축되어, 하나의 Managed Agents 플랫폼에서 여러 Agent를 통합 운영 아래 실행할 수 있다. 플랫폼은 Harness 역량을 호스팅하는 한편, 도구 실행은 여러분이 통제하는 샌드박스에 위임할 수 있다.
+ **AgentScope Service에 등록된 Agent들은 하나 또는 여러 개의 Team으로 조립될 수 있다.** Agent가 자체 호스팅되는 AgentScope 런타임이든 노코드 Managed Agent Harness 런타임이든 관계없이, Agent들은 함께 오케스트레이션되어 더 복잡한 작업을 처리할 수 있다.

## AgentScope Service란 무엇인가

AgentScope Service는 여러분의 기존 Agent 프레임워크를 대체하기 위한 것이 아니다. 이는 통합된 컨트롤 플레인을 추가하여, Claude, OpenClaw, QwenPaw 등 서로 다른 프레임워크와 스택으로 구축된 Agent들을 한곳에서 거버넌스할 수 있게 한다.

엔터프라이즈는 이미 Agent를 구축하는 다양한 방법을 갖고 있다. 역량 있는 Agent Framework인 AgentScope는 에이전트를 구축하기 위한 엔드 투 엔드 경로를 제공한다. 하지만 **전통적인 Agent Framework는 더 이상 에이전트를 구축하는 유일한 방법이 아니다**. Coding Agent 제품은 더 많은 영역으로 확장되고 있으며, Claude SDK, Qoder CLI 및 유사한 도구로 엔터프라이즈 Agent를 구축하는 것이 흔한 선택이 되어가고 있다.

1. **Agent Framework 사용**
   AgentScope, LangChain, ADK 및 유사한 프레임워크로 비즈니스 서비스 안에서 에이전트 루프를 직접 실행한다. 유연성은 높지만, 테넌트 격리, 버전 롤아웃, 세션 복구, HITL, 이벤트 영속화, 레플리카 간 조정을 각 팀이 직접 구축해야 한다. 모든 비즈니스 라인이 이러한 부분들을 각자 재발명하면 표준이 갈라진다.
2. **Coding Agent 또는 개인 작업 공간 비서 사용**
   Claude Code, 다른 Coding Agent, 개인 작업 공간 비서 등이 예시다. 빠르게 시작할 수 있고 로컬에서는 훌륭하게 느껴지지만, 상태가 개발자 머신에 존재하므로 공유하기 어렵고 감사하기 어려우며, 다중 팀 소유권에는 잘 맞지 않는다. 노트북을 닫으면 작업도 대개 함께 멈춘다.
3. **노코드 또는 Managed Agents 플랫폼 사용**
   전통적인 노코드 플랫폼은 시각적 노드로부터 Agent를 조립한다. 시작하기는 쉽지만, 메모리 관리, 컨텍스트 압축, 도구 연결과 같은 관심사를 조밀한 설정 화면으로 노출하는 경우가 많아, 사용자가 품질과 안정성 모두를 책임지게 만든다. Managed Agents는 완전히 호스팅된 클라우드 Harness 역량을 강조하여 사용자가 더 이상 그 운영 부담을 지지 않도록 하는 동시에, 사용자에게 도구 실행에 대한 더 많은 통제권을 준다 — 고객 VPC 안에서 실행되는 Hands를 포함해서 — 하지만 엔터프라이즈 멀티 에이전트 협업은 여전히 불완전했다.

이 경로들은 상호 배타적이지 않다. 한 회사 안에서 R&D는 Coding Agent를 사용하고, 비즈니스 플랫폼은 AgentScope를 운영하며, 새로운 프로젝트는 첫날부터 호스팅된 Harness를 원할 수 있다 — 이러한 혼합은 흔하다. AgentScope Service는 여러분을 어떤 단일 에이전트 프레임워크나 플랫폼에도 가두지 않는다. 이는 에이전트 런타임 전반에 걸쳐 통합된 컨트롤 플레인 역량을 제공한다.

### Control Plane

Control Plane은 AgentScope Service의 핵심이다. 모든 Agent 애플리케이션은 이를 통해 등록한다. SDK 또는 Sidecar를 통해, 주류 Agent Framework(AgentScope, LangChain, ADK)뿐 아니라 Claude, Qoder 및 유사한 런타임도 지원한다.


Dashboard는 Control Plane의 시각적 콘솔이다. 전체 플릿에 대해 온라인 에이전트, 배포 인스턴스, 활성 세션, 토큰 사용량 및 기타 전역 신호를 실시간으로 보여주어, 운영자가 클러스터 상태를 파악할 수 있게 한다.


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785934371848-7b1b934e-11ed-4625-97cc-820f2fe5d214.png)


Dashboard에서는 세션 상세 정보를 조회하고, 활성 세션의 실시간 컨텍스트 상태(컨텍스트의 서로 다른 부분이 어떻게 기여하는지 포함)를 확인하고, 세션 컨텍스트를 동적으로 조정하거나 압축하며, 실행 중인 대화에 개입할 수도 있다.


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785946414310-ff29cee8-2b2b-40df-9ec8-0211ee03fe8c.png)

### Managed Agents

Managed Agents는 `agentscope-builder` 플랫폼으로부터 진화했다. 이는 개발자에게 SaaS 스타일의 Agent 정의와 호스팅된 실행을 제공하는 노코드 Agent 플랫폼으로 남아 있다. 이번 업그레이드는 추론과 도구 실행의 분리를 한층 더 강조한다: Harness 역량은 더 철저하게 호스팅되는 한편, 도구 실행은 더 많은 사용자 통제 아래 머문다.


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785948183107-014a5cb1-6fcf-4b04-93cb-f01341b35350.png)


Agent 정의는 AgentScope Harness의 핵심 설계를 따른다. 먼저 Workspace와 Memory 같은 기초 개념을 정의한 다음, 워크스페이스와 메모리를 에이전트와 연결하여 생성한다.


Workspace 정의하기:


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785948616059-4d7be456-50bf-4e68-9ebf-3d48ce0ca9d3.png)


Agent 정의하기:


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785948697346-865380da-fb2c-420b-9968-4275b51a85b6.png)


이번 업그레이드에서 가장 큰 변화는 호스팅된 런타임 로직과 아키텍처 — Managed Agents다. 플랫폼은 정적 정의(Agent, Workspace)와 동적 런타임(Environment, Session)을 명확히 분리하고, Environment와 Session을 사용해 Agent가 실제로 어떻게 실행되는지를 오케스트레이션한다.


세션을 생성하고 셀프 호스팅 샌드박스 런타임 환경에 바인딩하기:


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785948796759-2723bb0e-e25e-49a6-aad4-27f8eb368d8d.png)


세션을 생성하는 것만으로는 SSE 이벤트 스트림이 시작되지 않는다. 대화와 전체 추론 경로는 사용자가 메시지를 보낼 때만 시작된다. 아래와 같이, 콘솔 채팅 페이지에서 사용자 메시지를 보낼 수 있다.


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785948840673-56827ddd-93f4-4091-9b7c-bbafa217a511.png)


Agent 생성 → Environment 생성 → Session 생성 → 첫 메시지 전송 → Dashboard에서 이벤트 스트림 관찰. 세션을 생성하는 것만으로는 Agent가 시작되지 않는다. 장기 실행되는 작업에서 Managed Agents는 특히 **복구 가능성**을 강조한다: 이벤트는 영속화되고, 상태는 재구축될 수 있으며, HITL은 일시 정지되었다가 재개될 수 있다. 프론트엔드 새로고침이나 서비스 레플리카 변경이 처음부터 다시 시작하는 것을 의미해서는 안 된다.

런타임 설계는 Claude Managed Agents와 긴밀하게 정렬되어 있다. Harness 인프라와 런타임은 완전히 호스팅된다(AgentScope Harness Runtime이 뒷받침). Brain/Hands 분리는 사용자에게 도구가 실제로 어디에서 실행되는지에 대한 더 많은 통제권을 준다. 배포는 Control Plane과 관리형 Dataplane으로 분리된다 — 아래 프로덕션 배포 섹션을 참고하라.

### Agent Teams

AgentScope Service Control Plane에 등록된 모든 에이전트 — 프레임워크(LangChain, AgentScope, ADK, Claude SDK 등)를 통해 스스로 배포하고 등록했든, 노코드 경로를 통해 Managed Agent로 생성되었든 — 는 하나 또는 여러 개의 Agent Team으로 오케스트레이션되어 복잡한 작업을 협업할 수 있다.


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785948895276-d0221173-9683-4a94-b281-55f9372cee65.png)

AgentScope Service에서 Team은 채팅방이 아니다. 이는 조작 가능한 협업 단위다: 태스크는 클레임될 수 있고, 계획은 승인될 수 있으며, 멤버는 깨울 수 있고, 세션이 끝났다고 해서 상태가 사라지지 않는다. 흔한 패턴은 작업을 분해하고 인수하는 Lead와, 역량에 따라 리서치·코딩·검증 등의 서브태스크를 클레임하는 Member들이다. 플랫폼이 메시지 라우팅, 태스크 보드, 생명주기를 소유한다 — 비즈니스 코드가 임시 다중 프로세스 통신을 직접 구현할 필요가 없다.

짚고 넘어갈 만한 점 하나: AgentScope Framework는 Agent Teams를 네이티브로 지원한다. 이 메커니즘은 분산 태스크 관리와 스케줄링을 위해 AgentScope Service Control Plane을 사용한다. 따라서 메인 에이전트 개발 중에 AgentScope Framework의 네이티브 Teams 역량을 사용해 멀티 에이전트 협업을 구성할 수도 있고, 특정 복잡한 태스크를 위해 콘솔에서 독립적인 Agent들을 동적으로 조립할 수도 있다. 어떤 경로를 선택할지는 시나리오에 달려 있다.

## 아키텍처 개요

### 전체 아키텍처


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785984168683-7e939049-046d-4ffa-b30d-1c0e6c0ff01b.png)

사람은 두 개의 진입점을 통해 AgentScope Service Control Plane에 접근한다: Dashboard(브라우저)와 REST API(SDK / curl / 서드파티 통합). Control Plane 아래에서는 네 가지 Agent 연결 모델이 함께 관리된다: 네이티브 AgentScope 연결, `instrument()`를 통한 LangChain, 그리고 Sidecar를 통한 Claude / QwenPaw.

### Managed Agents


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785976191807-3dde2cf8-ece0-4819-b376-328b498ed00c.png)


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785976204028-86690651-b369-4c47-b3eb-73c9f7da508e.png)

### Agent Teams 협업 플로우

Team의 멤버들은 동일한 프레임워크나 호스팅 모델에서 올 필요가 없다. 콘솔에서, 이미 Control Plane에 등록된 여러 Agent를 선택하고, 누가 Lead이고 누가 Worker인지 고르면 오케스트레이션이 끝난다 — Lead가 태스크를 생성하고 할당하며, Worker들이 이를 클레임하고 실행하고, 협업 상태는 Control Plane이 유지 관리한다.


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785985724730-b8f8d88c-669a-430c-bbf6-0c9f2d64b0f7.png)

그림의 핵심 포인트:

+ **멤버는 이질적일 수 있다.** 예시에서 Lead와 Worker 1은 Managed Agent(Control Plane이 `teamContext`와 바인딩된 Session을 생성하고 `user.message`를 전달해 시작시킴)이고, Worker 2는 셀프 배포된 네이티브 AgentScope 런타임이며, Worker 3은 LangChain(또는 Sidecar를 통해 연결된 Claude)이다. Control Plane은 서로 다른 멤버에 대해 서로 다른 참여 경로를 사용하지만(Managed Agent는 `find-or-create session`을 사용하고, BYO 에이전트는 `team_join` 명령을 사용한다), Lead에게 노출되는 협업 모델은 동일하다: 모든 멤버가 태스크를 할당받을 수 있고, Team 안에서 메시지를 보내거나 받을 수 있다.
+ **Lead의 할당과 Worker의 클레임이 모두 존재한다.** 태스크를 생성할 때 Lead는 `owner`를 설정할 수 있다(특정 Worker에게 할당); 그 Worker는 클레임 → 시작 → 완료의 순서를 따른다. Lead는 또한 `owner` 없이 태스크를 생성해 Task Board에 올려놓을 수 있으며, 한가한 Worker가 `POST .../claim`을 통해 스스로 클레임한다. 두 패턴은 동일한 Task Board 위에서 공존하므로, Lead가 모든 유휴 슬롯을 일일이 지켜볼 필요가 없다.
+ **메시지는 유니캐스트와 브로드캐스트를 모두 지원한다.** Mailbox는 `to=member`로 지정된 방향성 메시지(예: Lead가 특정 Worker를 쿡 찌르는 경우)와, `to`가 비어 있는 브로드캐스트 메시지(모든 멤버에게 보임)를 모두 지원한다. 둘 다 동일한 영속화 채널을 공유한다.
+ **협업 상태는 단일 세션에 묶여 있지 않다.** Task Board와 Mailbox 데이터는 어떤 멤버의 세션 생명주기와도 독립적이다: Worker 프로세스가 재시작되거나 세션이 종료되어도 태스크가 여전히 존재하는지, 메시지가 여전히 추적 가능한지는 지워지지 않는다. 그래서 Worker가 크래시하면 Control Plane은 해당 멤버를 `Lost`로 표시하고 복구를 트리거할 수 있으며, 팀 전체의 진행 상황이 사라지지 않는다.

### AgentScope 네이티브 프레임워크 + Control Plane

AgentScope Framework 자체는 이미 완전한 엔터프라이즈 Agent 스택 — Harness, Agent Teams, 멀티 에이전트 협업, Sandbox 격리 등 — 을 제공한다. 실제 엔터프라이즈 배포에서는 이 역량들 다수가 분산 조정에 의존한다. AgentScope Service Control Plane은 AgentScope를 위한 그 네이티브 분산 조정을 제공한다.

#### Control Plane의 분산 조정

AgentScope `HarnessAgent`가 단일 인스턴스에서 여러 레플리카로 옮겨가면, 세션 상태, 워크스페이스 파일, 샌드박스 스냅샷과 동시성 락, 레플리카 간 메시징, 비동기 도구, 서브태스크, Turn 동시성 제어는 더 이상 "프로세스 메모리가 source of truth"라는 전제를 가정할 수 없다.

아래 그림은 **런타임 토폴로지**를 보여준다: 여러 `HarnessAgent` 레플리카가 Control Plane 및 `AgentStateStore` 백엔드와 어떻게 상호작용하는지 — `DistributedStore`의 인터페이스 정의가 아니다.


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785985855412-a79588d2-9ae3-4922-a30b-337ff4e6e526.png)


**서로 절대 교차하지 않는** 두 개의 독립적인 경로가 가장 중요하다:

+ **위 방향: 조정 API 호출.** 각 `HarnessAgent` 레플리카는 SDK를 통해 Control Plane을 호출한다. 개발자가 실제로 보는 것은 네 가지 호스팅된 Harness 역량이다. 그 아래에서는 `BaseStore`, `SandboxSnapshotSpec` / `SandboxExecutionGuard`, `MessageBus`, `TaskRepository`, `SessionTurnGate`, `AsyncToolRegistry`와 같은 인터페이스로 매핑된다. 조정 상태는 Control Plane 자체의 Postgres에 저장되므로, 비즈니스 측이 또 다른 인프라 스택을 필요로 하지 않는다.
    - **워크스페이스 공유**: 워크스페이스 파일(`MEMORY.md`, `skills/`, `sessions/` 등)과 샌드박스 스냅샷 및 동시성 락으로, 어떤 레플리카든 동일한 워크스페이스를 읽고, 쓰고, 복원할 수 있다;
    - **Agent Teams**: 레플리카 간 메시지 전달과 서브태스크 위임으로, Lead/Member 사이의 유니캐스트, 브로드캐스트, 태스크 클레임이 어느 멤버가 어느 레플리카에서 실행되는지에 의존하지 않는다;
    - **세션 동시성 제어**: 레플리카 전반에 걸쳐 동일 세션에 대한 Turn 수준의 게이트로, 두 레플리카가 동시에 동일한 대화 턴을 진행하는 것을 방지한다;
    - **비동기 도구 실행**: 상태와 결과를 어떤 레플리카든 관찰하고 수집할 수 있는 장시간 실행 백그라운드 도구.
+ **아래 방향: 세션 상태 백엔드에 대한 직접 접근.** `AgentStateStore`(대화 컨텍스트, 압축 요약, 권한 규칙, Plan Mode 상태 등)는 **Control Plane을 거치지 않는다**. 각 레플리카는 비즈니스 측이 제공하는 Redis / MySQL / Postgres / OSS 백엔드에 직접 연결된다. Control Plane은 선택적으로 세션 동시성 제어(`SessionTurnGate`)와 `AgentStateStore` 자체의 `getVersioned` / `saveIfVersion` 낙관적 동시성 제어(CAS)로 이를 보완할 뿐이며, 이는 레플리카 간 중복 LLM Turn을 줄여준다 — 상태 데이터 자체가 아니라 *누가 이 Turn을 실행할 수 있는지*를 조정하는 것이다.

개발자 입장에서 이는, `HarnessAgent.builder()`에 `distributedStore`를 설정하는 것(조정 컴포넌트는 `ControlPlaneStores.fromEnv()`를 통해, `AgentStateStore`는 여러분 자신의 공유 백엔드를 가리키도록)만으로도 세션 복구, 파일 공유, 샌드박스 스냅샷, 태스크 큐를 하나하나 재구축하지 않고도 Agent에게 진짜 수평 확장을 줄 수 있음을 의미한다.

#### 자가 조립형 Agent Teams

AgentScope Framework는 폐루프(closed-loop) Agent Teams 역량도 함께 제공한다. 팀이 형성되는 방식은 Control Plane을 통한 직접 오케스트레이션과 다르다: 개발 시점에 멤버들을 고정된 Team 토폴로지로 미리 배선할 **필요가 없다**. Subagent 패턴에서와 마찬가지로, Main Agent에 호출 가능한 Subagent 풀(`agentRef`)을 미리 등록해두기만 하면 된다. 런타임에, Human(또는 상위 시스템)이 팀이 필요한 작업을 설명하는 메시지를 Main Agent에 보내면, Main Agent 스스로가 팀을 구성할지, 미리 등록된 어떤 Subagent들을 Worker로 사용할지 판단하고, Team을 동적으로 생성한다.


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785987685062-a679fc73-257a-4f74-8ff8-d214a82b7c88.png)


위 그림의 핵심 포인트:

+ **여러분이 미리 정의하는 것은 후보 멤버 풀이지 Team이 아니다.** 개발 시점에는 `reviewer`, `security-scanner`, `perf-tester` 같은 Subagent들을 Main Agent에 붙이기만 한다(Subagent 패턴과 동일한 `agentRef` 등록 메커니즘을 공유). 누가 누구와, 언제 팀을 이루는지는 정해지지 않은 채로 남는다 — Lead/Worker 구조를 미리 설계하지 않는다.
+ **트리거는 코드나 콘솔 설정이 아니라 런타임 메시지다.** Human이 "이 일을 처리할 팀을 꾸려줘" 같은 의도를 담은 평범한 메시지를 Main Agent에 보내면, Main Agent의 추론이 `createTeam`(추가 멤버가 필요하면 `spawnMember`도)을 호출하기로 결정하고, 스스로를 Lead로 설정하며, 선택된 Subagent들을 Worker로 인스턴스화한다. 이 결정은 하나의 LLM 턴 안에서 이루어진다 — 인간의 사전 오케스트레이션도, 코드 변경도 없다.
+ **일단 형성되면, Team은 동일한 협업 메커니즘을 사용한다.** Lead와 Worker들은 하나의 `TeamClient`(Task Board + Mailbox)를 공유한다. 이는 Control Plane 없는 `LocalTeamClient`(폐루프, `BaseStore` 위에서의 낙관적 동시성)일 수도 있고, 레플리카 간 조정과 Dashboard 관측 가능성을 제공하는 `ControlPlaneTeamClient`일 수도 있다. 이는 위에서 다룬 콘솔 오케스트레이션 경로와 일치한다. 유일한 차이는 Team이 어떻게 형성되는가다.

이 역량과 [Subagents](/v2/ko/docs/harness/subagent) 패턴은 동일한 Subagent 정의를 재사용하지만, 협업 모델은 완전히 다르다 — 그리고 헷갈리기 쉽다 — 따라서 명시적으로 비교해볼 가치가 있다.

```text
Subagent mode (one-way delegation, peers isolated)
    Main Agent ──task()──▶ Subagent A ──result──▶ Main Agent
    Main Agent ──task()──▶ Subagent B ──result──▶ Main Agent
    Subagent A and Subagent B have no communication path and do not share a task list

Agent Team mode (peer collaboration, shared state)
    Lead ──createTask / assignTask──▶ Worker A
    Worker A ◀── sendMessage / broadcastMessage ──▶ Worker B
    Worker A and Worker B can both claim unassigned tasks on the shared Task Board
```

+ **Subagent: 단방향 위임, 피어는 격리됨.** Main Agent는 Task 도구를 통해 Subagent에 지시를 보낸다. Subagent는 독립적이고 상태를 갖지 않는 컨텍스트에서 실행되며 결과를 Main Agent에 보고한다. 두 Subagent는 직접적인 통신 경로가 없고, 서로의 존재를 알지 못하며, 태스크 목록을 공유하지도 않는다 — 모든 조정 로직은 오직 Main Agent만이 갖는다.
+ **Agent Team: 피어 협업, 공유 상태.** Team 안의 Lead와 Worker들은 하나의 Task Board와 Mailbox를 공유한다. Lead는 특정 Worker에게 `assignTask`를 할 수 있다; Worker들은 `sendMessage` / `broadcastMessage`로 서로 대화할 수 있다; 할당되지 않은 작업은 누구든 한가한 쪽이 `claimTask`한다. 협업 상태는 더 이상 발신자만 소유하지 않는다 — 팀 전체가 유지 관리하는 공유 데이터가 된다.

요약하면: Subagent는 계층적인 "지시를 보내고 결과를 기다리는" 위임이고, Agent Team은 공유 보드, 상호 클레임, 직접 통신을 갖춘 피어 협업이다. 이 그림이 강조하는 것은, AgentScope의 네이티브 Agent Teams가 Main Agent로 하여금 런타임에, 필요에 따라, Subagent 풀로부터 피어 협업 Team을 조립할 수 있게 해준다는 점이다 — 팀 구조를 미리 계획하지 않고서도. 이는 콘솔의 동적 오케스트레이션 경로(Human이 콘솔에서 실시간으로 팀을 조립하는 것)를 보완한다. 두 경로 모두 동일한 `TeamTool` / `TeamClient` 프로그래밍 모델을 공유하며, 필요에 따라 선택할 수 있다.

#### 멀티 에이전트 협업: 원격 Subagent

Agent Teams나 AgentScope Subagent 위임의 대상이 반드시 동일한 프로세스에 있을 필요는 없다. 이는 동일한 `HarnessAgent` 안의 로컬 Subagent일 수도 있고, 다른 Managed Agent(Dataplane에서 실행 중)일 수도 있으며, `instrument()`를 통해 연결된 LangChain Agent일 수도 있다.

원격인 경우, AgentScope Service Control Plane의 역할은 Agent A가 대상이 어디에 있는지 또는 어떤 프레임워크를 사용하는지 신경 쓰지 않고도 `delegate` 호출을 발행할 수 있게 하는 것이다.


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785987846445-098ee5ce-1d73-4343-a3b2-09cc17ebf963.png)


위 그림은 Control Plane을 통한 원격 Subagent 호출의 **트래픽 프록시**를 보여준다 — 일회성 API 설계가 아니다.

+ **로컬 우선 — 가능하면 Control Plane을 건너뛴다.** `techlead`가 Agent A와 동일한 `HarnessAgent` 프로세스 안에 선언된 로컬 Subagent라면, 위임은 인프로세스 호출이며 Control Plane은 관여하지 않는다. 이것이 지연 시간이 가장 낮은 경로이며, 대개 가장 흔한 경로다.
+ **인스턴스/프레임워크 간에는, Control Plane이 세 가지 일을 한다: 디스커버리, 인증, 프록싱.** 먼저 Agent A와 `techlead` 사이에 합법적인 협업 관계가 있는지(동일 Team, 화이트리스트 ACL) 확인한 뒤, 플릿 레지스트리에서 Agent ID로 대상 인스턴스를 찾는다 — 대상은 Managed Agent(Dataplane의 Session Turn API로 전달됨)일 수도 있고, `aistio.instrument()`를 통해 등록된 LangChain Agent(그것이 보고한 채팅 엔드포인트로 전달됨)일 수도 있다 — 그리고 마지막으로 요청을 프록시하고 응답을 Agent A에게 그대로 스트리밍해 돌려준다.
+ **Agent A는 상대의 프레임워크를 알 필요가 전혀 없다.** 호출자 입장에서, `delegate("techlead", ...)`는 대상이 로컬 Subagent인지, Managed Agent인지, LangChain Agent인지에 따라 달라지지 않는다. 프레임워크 차이는 Control Plane의 라우팅 레이어가 흡수한다.

그래서 "연결 방법" 섹션은 AgentScope, LangChain, Claude가 서로 다른 방식으로 동일한 Control Plane에 연결될 수 있다는 점을 강조한다: 일단 연결되면, 각각은 다른 Agent들에게 발견 가능해지고, 위임된 작업을 받을 수 있으며, 결과를 반환할 수 있다 — 모든 프레임워크 쌍마다 점대점 통합을 만들 필요 없이.

### 프로덕션 배포 아키텍처


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785987384213-cab424c2-502c-43b3-ac78-ee0e43eb9c9c.png)

네 개의 플레인은 다음과 같이 이해할 수 있다.

| **플레인** | **소유하는 것** | **소유하지 않는 것** |
| --- | --- | --- |
| Gateway | 공개 진입점, 인증, API 라우팅 | 비즈니스 상태 및 Agent 실행 |
| Control Plane(`aistiod`) | 제품 리소스, 콘솔, Agent 상태, Session, Team, 런타임 명령 | Harness 추론 및 Session 스트림 전송 |
| Dataplane | Managed Harness Runtime, 이벤트 로그, SSE, Turn Lease, HITL, Work Queue | 제품 Catalog 테이블의 직접 읽기 |
| Scheduler | Channel, Cron, 아웃바운드 작업, Self-hosted Hands Worker | 추론 루프 |


또 하나 중요한 제품 분리가 있다: **Brain**과 **Hands**다.

+ **Brain**: 컨텍스트, 추론, 도구 결정, 이벤트 로그를 관리한다; 플랫폼이 호스팅하는 AgentScope Harness가 제공한다.
+ **Hands**: 도구가 어디에서 실행되는지를 결정한다. 옵션에는 `local`, `sandbox`(예: E2B), `remote`, 고객 측 아웃바운드 Worker를 통한 `self_hosted`가 있다.

이는 엔터프라이즈가 세 가지 질문을 개별적으로 답할 수 있게 해준다: 모델이 볼 수 있는 컨텍스트는 무엇인가? 도구가 건드릴 수 있는 네트워크와 파일은 무엇인가? 어떤 도구 결과가 Brain으로 다시 흘러갈 수 있는가? 신뢰 경계를 이런 방식으로 나누고 나면, 권한 검토와 인시던트 격리 모두 더 명확해진다.

이는 또한 "관리형"이 "모든 데이터가 고객 환경을 떠나야 함"을 의미하지 않는 이유를 설명한다. 클라우드 측에서의 비식별화가 허용되는 경우 호스팅된 샌드박스를 사용하고, 도구가 내부 시스템이나 민감한 파일 시스템에 접근해야 하는 경우 Hands를 고객 VPC에 두어 아웃바운드 Worker가 도구를 실행하고 결과를 반환하게 한다. Brain은 여전히 오케스트레이션과 상태 복구를 소유한다 — 오직 실행 플레인만 교체된다.

Turn 경로, 이벤트 계약, 스키마 경계에 대해 더 깊이 알아보고 싶은 독자는 우리의 후속 기술 글을 읽어보라: [AgentScope Service 기술 딥다이브](/v2/ko/blogs/agentscope-service-release-tech).

## Agent는 어떻게 연결하는가

AgentScope Service는 동시에 두 종류의 사용자를 서비스한다.

1. 호스팅된 에이전트를 빠르게 구축하기 위한 SaaS 경로를 원하는 플랫폼 / 플랫폼 서비스 팀 — Console / API를 통해 Managed Agent를 생성한다.
2. 이미 서로 다른 스택으로 구축된 Agent를 가지고 있으며 이를 통합 거버넌스 아래 두고 싶은 비즈니스 엔지니어링 팀 — 익스텐션 / SDK / Sidecar를 통해 Control Plane에 연결한다.

두 경로는 공존한다. 많은 팀이 새로운 제품을 출시하기 위해 Managed Agents로 시작한 뒤, 기존에 스스로 구축한 Agent(BYO)를 점진적으로 Dashboard로 가져온다.

아래에서는 두 번째 경로에 초점을 맞춘다: 자체 개발되고 자체 배포된 Agent가 AgentScope Service Control Plane에 어떻게 연결하는지. 크게 두 가지 연결 모델이 있다 — SDK와 Sidecar. Agent Framework 애플리케이션의 경우, SDK를 도입하는 것만으로 Control Plane에 등록하기에 충분하다.

### Agent Framework

#### AgentScope

AgentScope Java는 Agent 애플리케이션의 연결을 네이티브로 지원한다. `agentscope-extensions-aistio` 의존성을 추가함으로써, 기존 AgentScope Runtime은 자동으로 Control Plane에 자신을 등록하고 Managed Agents와 나란히 Dashboard에 나타날 수 있다. 세션 상태, 헬스, 런타임 신호는 동일한 계약을 통해 보고된다.

Agent Teams를 위한 레플리카 간 메시지 전달과 서브태스크 위임, 노드 간 비동기 태스크 추적, 세션 동시성 제어, Workspace 상태 동기화 및 기타 분산 배포 요구 사항은 모두 Control Plane이 네이티브로 제공할 수 있다.

#### LangChain

커뮤니티는 현재 Python SDK를 제공한다. 사용자는 `aistio.instrument()` 래퍼를 통해 연결할 수 있다. LangChain / LangGraph 애플리케이션의 경우, Control Plane은 세션 스냅샷, 컨텍스트, 런타임 지표를 대역 외(out of band)로 수집한다; 메인 비즈니스 경로가 먼저 성공하고, 보고 실패는 추론 자체에 영향을 주지 않는다.

이렇게 하면, LangChain으로 구축된 Agent도 비즈니스 경로를 다시 작성하지 않고 AgentScope Service의 플릿 관리와 세션 관측 가능성 안으로 들어올 수 있다.


Claude Agent SDK와 Google ADK 같은 더 많은 프레임워크에 대한 지원은 시간이 지나며 추가될 것이다 — 로드맵을 참고하라.

### Coding Agent

바이너리로서 수정하기 어려운 Coding Agent — Claude Code, Qoder CLI 등 — 의 경우, **Sidecar**가 그 간극을 메울 수 있다: 로컬 세션 디렉터리와 런타임 상태를 대역 외로 관찰하고, 이를 Control Plane에 보고하며, compress나 terminate 같은 운영 명령을 받아들인다.

이 경로의 요점은 엔터프라이즈가 "가장 강력한 Coding Agent를 사용"할지 "통합 거버넌스 아래 둘"지 사이에서 선택할 필요가 없다는 것이다. 생산성 도구는 개발자 환경에서 계속 실행될 수 있으면서도, 플랫폼은 여전히 이를 볼 수 있고, 관리할 수 있으며, 필요할 때 개입할 수 있다.


QwenPaw와 같은 개인 작업 공간 비서도 원칙적으로 Sidecar를 통해 연결될 수 있다 — 자세한 내용은 로드맵을 참고하라.

## 로컬에서 사용해보기

AgentScope Service는 빠르게 반복 개발되고 있다. 전체 제품 표면을 먼저 살펴보고 싶다면, 저장소를 클론하고 로컬 환경에서 시작해보라.

1. Control Plane, Managed Agents dataplane, 및 기타 컴포넌트를 시작한다(위의 프로덕션 배포 다이어그램과 같이):

```shell
git clone https://github.com/agentscope-ai/agentscope-java.git
cd agentscope-java
```

```bash
export DASHSCOPE_API_KEY=sk-xxx
cd agentscope-service
scripts/dev-down.sh && BUILDER_REBUILD=1 scripts/dev-up.sh
```

2. [http://localhost:8080](http://localhost:8080)을 열고 사용자 이름 / 비밀번호(`admin` / `admin`)로 로그인한다.

이제 Managed Agents를 사용해보고 빠르게 Agent를 만들 수 있다.

    1. **Managed Agents** 아래에서 Agent를 생성한다;
    2. `local` Environment를 생성한다;
    3. **Sessions**를 열고, Agent와 Environment를 바인딩한 뒤 첫 메시지를 보낸다;
    4. **Dashboard**로 돌아가 온라인 상태, 이벤트, 런타임 정보를 확인한다;
    5. 협업을 위해서는 **Agent Teams**를 열고, 팀을 생성한 뒤 태스크와 멤버 상태를 관찰한다.
3. BYO Agent 등록을 시도해보려면 저장소 안의 `agentscope-samples/agents/agentscope-paw`에 있는 샘플을 사용하라. 시작한 후에는 Dashboard에서 에이전트가 성공적으로 등록된 것을 확인할 수 있을 것이다.

## 로드맵 & 맺음말

AgentScope Service는 Framework, Coding Agent, Managed Agents 등 서로 다른 방식으로 구축된 Agent들을 하나의 Control Plane 위로 가져오고, Agent 간 협업에 통합된 뷰를 제공한다. Console에서 첫 번째 Agent를 만들고 AgentScope Service에서 Harness 런타임을 호스팅하든, 기존의 AgentScope / LangChain / Claude 애플리케이션을 Control Plane에 연결하든, 목표는 동일하다: **엔터프라이즈에게 원스톱 Agent 통제 및 거버넌스 센터를 제공하는 것**.

앞으로 AgentScope Service는 더 개방적인 연결, 더 완전한 자동화, 더 강력한 이벤트 기반 통합을 향해 계속 진화할 것이다. 단기 초점은 다음을 포함한다.

1. **AgentScope Framework 네이티브 역량을 계속 반복 개선한다**
2. **더 많은 Agent 프레임워크와 Coding Agent를 지원한다**
   LangChain, ADK, Claude, Qoder, OpenAI Agents 등에 대한 어댑터를 심화하고, BYO 연결 비용을 낮추며, 이질적인 Agent들이 동일한 계약 안으로 더 쉽게 들어오게 한다.
3. **자동화**
   Deployment, Cron, Webhook, Channel을 중심으로 자동 트리거와 폐루프 실행을 확장하여, Agent가 사람이 시작하는 세션에서 이벤트 기반 태스크 처리로 옮겨가게 한다.
4. **더 많은 이벤트 기반 통합**
   GitHub / GitLab, DingTalk, WeCom 등의 엔지니어링/협업 진입점을 연결하여, 코드 변경, 티켓, 그룹 메시지를 곧바로 Agent Turn이나 Team Task로 변환한다.


Alibaba Cloud의 엔터프라이즈급 제품에 관심이 있다면 [Agent Teams](https://help.aliyun.com/zh/agentteams/magic-console-product-overview)와 [Agent Loop](https://help.aliyun.com/zh/document_detail/3033860.html)도 참고하라.
