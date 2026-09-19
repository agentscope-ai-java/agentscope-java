---
title: "릴리스 계획 Agent: 이 데모에서는 텍스트 초안만 생성한다"
---

Managed Agents는 에이전트가 클라우드 환경에서 실행되도록 해준다. 한편으로는 추론, 오케스트레이션, Harness 관리와 같은 핵심 단계가 클라우드에 의해 일괄적으로 호스팅되므로 아키텍처 안정성과 런타임 품질이 플랫폼에 의해 보장된다. 다른 한편으로는 장시간 실행되는 작업이 더 이상 로컬 디바이스가 온라인 상태를 유지하는 데 의존하지 않는다 — 개인용 컴퓨터가 꺼지더라도 작업은 클라우드에서 계속 실행될 수 있다.

AgentScope 2.0의 Harness 커널과 Sandbox 격리 기능을 기반으로, 완전한 엔터프라이즈급 Managed Agents 플랫폼을 빠르게 구축할 수 있다.

+ AgentScope 2.0에서 이미 프로덕션화된 Harness Agent를 Brain 런타임으로 직접 사용할 수 있다. 호스팅 커널은 안정적인 추론과 Harness 기능을 제공하며, 파일시스템, workspace, 도구 실행은 Sandbox 환경 안에서 완전히 격리된다.
+ 플랫폼 계층은 테넌트, 권한, 버전 관리, 이벤트, 실행 플레인 선택을 처리한다. Control Plane(Agent, Environment, Memory, Vault, Deployment)과 Data Plane(Session, Events, SSE)은 이러한 기능들을 멀티테넌트, 감사 가능, 운영 가능한 관리형 제품으로 조직한다.

> 이전에 공개된 오픈소스 Agent Builder를 본 적이 있다면, Managed Agents를 그 제품화된 업그레이드 버전으로 생각할 수 있다 — 하부 런타임과 주요 코드 경로는 동일하게 유지되며, 변경되는 것은 리소스 모델, API 계약, 실행 플레인 경계, 멀티테넌트 거버넌스다.
>

## Managed Agents의 배경

시장에는 이미 Bailian, Claude Code, LangChain 등에서 나온 유사한 Managed Agents 제품들이 있다. 본질적으로 나는 Managed Agents 제품이 이전의 로우코드 에이전트 플랫폼과 형태 면에서 근본적으로 다르다고 생각하지 않는다. 이들 모두 "에이전트 정의 & 실행" 기능을 담은 호스팅 플랫폼을 제공한다. 제품 표현 방식에서의 차이는, Harness 시대에 Managed Agents가 다음 두 가지를 강조한다는 점이다.

1. **더 이상 비즈니스 개발자가 Harness를 직접 조립하게 하지 않는다.** 전통적인 플랫폼은 메모리 유지, 컨텍스트 압축, 상태 복구, 도구 권한, 서브태스크 정리를 대량의 설정 항목으로 쪼개는 경우가 많았다. Managed Agents는 이러한 공통 엔지니어링 기능들을 통일된 Harness로 흡수하므로, 개발자는 주로 비즈니스와 관련된 Skill, Tool, Subagent, 권한 정책을 정의하게 된다. 플랫폼은 메커니즘의 일관성과 업그레이드 가능성을 보장하지만, 최종 작업 품질은 여전히 모델, 시스템 프롬프트, Skill 품질, 도구 반환값, 비즈니스 평가에 달려 있다.
2. **고객이 도구 실행과 데이터 반환의 경계를 통제하게 한다.** 엔터프라이즈 사용자에게 에이전트의 실제 가치는 그것을 기업 데이터 자산에 연결하는 데서 나오며, shell, 파일 I/O, MCP, 비즈니스 도구가 바로 데이터가 흐르는 진입점이다. 그래서 시스템은 의도적으로 **Brain(추론 & 오케스트레이션)**과 **Hands(도구 실행)**을 분리한다. Brain은 다음 라운드의 추론, 상태 복구, 컨텍스트 관리를 담당하고, Hands는 실제로 파일, 네트워크, 비즈니스 시스템을 건드리는 것을 담당한다. Hands는 플랫폼이 관리하는 Cloud Sandbox에서 실행되거나, 고객 VPC 내부의 Self-hosted Worker에서 실행될 수 있다.

첫 번째 포인트는 플랫폼의 추상화 수준을 실제로 바꾼다. 전통적인 로우코드 플랫폼은 종종 사용자가 "언제 메모리를 요약할지, 지나치게 긴 컨텍스트를 어떻게 절단할지, 도구 예외를 몇 번 재시도할지, 서브태스크를 어떻게 회수할지"를 결정하게 했다. 이런 옵션들은 유연해 보이지만, 실제로는 Harness의 엔지니어링 책임을 비즈니스 개발자에게 떠넘긴다 — 동일한 Agent라도 설정하는 사람의 경험에 따라 성능과 행동이 크게 달라질 수 있다. Managed Agents는 역할 프롬프트, Skills, MCP, 도구 권한, Environment 같은 비즈니스 차이만 노출시킨다. 압축 시점, 세션 복구, 도구 결과 제거, 장기 메모리 갱신 같은 것들은 지속적으로 진화하는 Harness에 맡긴다. 플랫폼이 Harness를 업그레이드하면, 모든 Agent는 각 플로우차트를 하나하나 수정할 필요 없이 동일한 엔지니어링 개선을 얻는다.

두 번째 포인트는 신뢰 경계를 바꾼다. 모델은 "무엇을 호출할지"를 결정하지만, 그렇다고 모델이 실행되는 프로세스가 반드시 그것을 "직접 실행"해야 하는 것은 아니다. 도구 호출이 안정적인 스키마, tool_use_id, 결과 이벤트로 표현되는 한, Hands는 Brain 내부의 추론 루프를 바꾸지 않고도 플랫폼 클라우드 샌드박스나 고객 VPC로 옮길 수 있다. 이를 통해 보안팀은 세 가지 별개의 질문에 답할 수 있게 된다 — 모델은 어떤 컨텍스트를 볼 수 있는가? 도구는 어떤 네트워크와 파일에 접근할 수 있는가? 그리고 도구 결과의 어떤 내용이 Brain으로 반환될 수 있는가? 이 세 질문이 분리되고 나면, 권한 감사와 문제 해결은 "하나의 통짜 Agent 컨테이너"보다 훨씬 명확해진다.

Claude Managed Agents를 예로 들면, 개발자들에게 받아들여진 중요한 이유 중 하나는 Claude Code가 이미 성숙한 Coding Agent Harness의 제품 가치를 입증했기 때문이다. 사용자가 보는 것은 모델 추론과 작업 결과이지만, 플랫폼이 실제로 호스팅하는 것은 **복구 가능한 세션 상태, 엔지니어링화된 실행 정책, 교체 가능한 Hands**다. AgentScope 2.0은 유사한 계층화 설계를 채택한다 — `HarnessAgent`가 긴 작업, 컨텍스트 오버플로, 상태 복구, 작업 위임을 처리하고, Managed Agents는 여기에 멀티테넌트 리소스, Environment, 안정적인 데이터 플레인 계약을 추가한다.

Managed Agents를 통해 Anthropic은 개인 사용자와 엔터프라이즈 사용자 사이에 점진적으로 발전하는 여러 솔루션을 제공한다.

+ **Claude Code CLI**는 개인 또는 단일 머신 개발 워크플로를 대상으로 하며, Agent가 로컬 workspace, 터미널, 세션 기록과 직접 결합되어 있다.
+ **Claude Agent SDK**는 Session, 이벤트 스트림, 도구 상호작용을 API로 노출시켜 엔터프라이즈 애플리케이션에 임베드하기 적합하다. 신원, 테넌트, 리소스 격리는 여전히 통합하는 쪽의 책임이다.
+ **Managed Agents**는 Agent, Environment, Session, 실행 플레인을 관리형 리소스로 더 한 단계 전환시키며, 플랫폼이 버전 관리, 권한, 런타임 거버넌스를 처리한다.

이 세 계층 사이의 차이는 단순히 "점점 두꺼워지는 패키징"이 아니라, 상태 소유권이 점진적으로 상위로 이동하는 것이다.

| 형태 | 주요 상태가 위치하는 곳 | 격리 책임자 | 적합한 대상 |
| --- | --- | --- | --- |
| CLI / 단일 머신 앱 | 로컬 디렉터리와 로컬 세션 | OS 사용자 | 개인 생산성 |
| SDK / Harness | 애플리케이션이 제공하는 SessionStore / StateStore | 애플리케이션 개발자 | 단일 엔터프라이즈 애플리케이션 |
| Managed Agents | 플랫폼 Control Plane, 공유 상태 저장소, Session 이벤트 로그 | 플랫폼이 User / Agent / Environment 단위로 관리 | 다중 팀, 다중 테넌트 플랫폼 |


## AgentScope 2.0이 Managed Agents에 좋은 기반인 이유

AgentScope 2.0의 모델 추상화, 도구와 MCP, 메시지와 이벤트, 상태 저장, 원격 파일시스템 / 분산 BaseStore, 그리고 플러그인 가능한 샌드박스는 모두 프로세스 외부 지속성과 다중 레플리카 배포를 위한 확장 지점을 남겨둔다. 이는 Managed Agents가 세션 복구, 도구 결과 지속성, 요청 간 컨텍스트 연속성을 처음부터 구현할 필요가 없다는 것을 의미한다. 데이터 플레인 레플리카는 AgentStateStore와 Workspace 백엔드를 공유해야 하며, turn lease와 노드 장애 극복을 올바르게 처리해야 한다.

이 중 Workspace는 Agent가 사용하는 논리적 디렉터리이고, Filesystem과 Sandbox는 이를 호스팅하는 물리적 백엔드다. 이 둘은 AbstractFileSystem에 의해 분리되어 있다 — 동일한 파일 도구 세트가 로컬 디렉터리, 분산 BaseStore, 또는 E2B 샌드박스를 가리킬 수 있다. 논리적 workspace가 물리적 실행 플레인과 분리되어 있으므로, Agent 정의는 비즈니스 프롬프트를 바꾸지 않고도 격리 정책을 전환할 수 있다.

구체적으로, HarnessAgent는 Hooks를 통해 ReActAgent 위에 장시간 실행에 필요한 엔지니어링 기본값들을 조립한다. 예를 들면:

+ **Workspace 기반 페르소나와 지식**: `AGENTS.md` / `MEMORY.md` / `KNOWLEDGE.md` 등의 파일이 시스템 프롬프트에 주입된다.
+ **세션 지속성**: agent state가 `sessionId`로 복원되므로, 프로세스가 재시작된 후에도 대화가 이어질 수 있다.
+ **압축과 오버플로 처리**: Harness는 기본적으로 압축과 도구 결과 제거를 활성화하며, 비즈니스 측에서 임계값을 재정의하거나 명시적으로 비활성화할 수 있도록 허용한다.
+ **Skills / Subagents**: workspace skill, 작업 위임(task 등)이 별도 설정 없이 바로 사용 가능하다.
+ **통일된 파일시스템 추상화**: 로컬, 원격 KV, 클라우드 샌드박스(E2B 등)가 모두 동일한 도구 시맨틱을 거치므로, Managed Agents는 Agent 비즈니스 정의를 바꾸지 않고도 Environment 유형에 따라 실행 플레인을 전환할 수 있다.

이러한 기능들은 독립적인 명사가 아니다. 긴 작업은 먼저 AgentStateStore로부터 메시지와 agent state를 복구한 다음, workspace Hook에 의해 AGENTS.md와 설치된 Skill이 주입될 수 있다. 추론 도중 컨텍스트가 윈도우 한계에 가까워지면 압축 Hook이 히스토리를 압축하며, 큰 도구 결과는 파일시스템으로 제거되어 컨텍스트에는 검색 가능한 참조만 남을 수 있다. 병렬 리서치가 필요할 때는 메인 Agent가 태스크를 Subagent에게 넘길 수 있다. 결국 파일시스템이 로컬에, 원격 KV에, 또는 E2B에 안착하든 상관없이 모델이 보는 도구 시맨틱은 일관되게 유지된다. 이 결합된 안정성이야말로 플랫폼 커널로서 Harness가 갖는 의미다.

또한 HarnessAgent와 Session은 동일한 생명주기를 공유하지 않는다. 전자는 공유된 `AgentStateStore`와 복구 가능한 Workspace 백엔드를 가진 데이터 플레인 노드에서 재구성되는 런타임 객체이고, 후자는 안정적인 ID, 이벤트 시퀀스, 영속 상태를 가진 제품 리소스다. 이 둘을 구분해야만 진정한 수평 확장이 가능하다 — 노드가 실패하면 Java 객체는 버릴 수 있지만, 대화와 장기 메모리는 공유 상태로부터 복구되어야 한다. workspace가 연속적인지 여부는 BaseStore, 샌드박스 스냅샷, 또는 고객 측 지속성에 달려 있으며, Local 디렉터리는 이를 보장하지 않는다.

단일 엔터프라이즈 에이전트 애플리케이션에서 Managed Agents로 옮겨갈 때 핵심은 추론 커널을 다시 작성하는 것이 아니라, 런타임 기능을 안정적인 플랫폼 리소스로 끌어올리는 것이다. "플랫폼 API 계층을 하나 추가한다"는 말은 단순히 Controller 몇 개를 더하는 것이 아니다 — 진짜 제품화에는 테넌트 ACL, Agent 버전 스냅샷, Session 상태 머신, append-only 이벤트, turn lease, HITL 티켓, Environment 키, Worker 큐, 공유 조정 저장소, 아카이브 감사가 필요하다. Harness는 플랫폼이 에이전트 루프를 다시 작성하지 않아도 되게 해주지만, 이러한 분산 책임들은 여전히 독립적인 엔지니어링 시스템이다.

따라서 Managed Agents의 완전한 형태는 다음과 같다 — 리소스 거버넌스를 담당하는 SaaS Control Plane, 런타임 커널을 제공하는 AgentScope 2.0, 그리고 서로 다른 신뢰 경계 아래에서 Hands를 실어 나르는 FC Sandbox / E2B 또는 고객 Worker.

## 엔터프라이즈 Managed Agents 플랫폼 상세

### 전체 배포 아키텍처

#### 핵심 컴포넌트 다이어그램

1. **Control Plane**


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785141394899-a68e0d3b-e16b-44be-9a29-4e61f163463f.png)

2. **Data Plane**


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785141716807-8d15ef5f-4d56-4c24-958a-caf553476262.png)

#### 핵심 데이터 흐름

클라이언트는 (session/event) 인터페이스를 통해 Managed Data Plane(Brain)에 작업 요청을 보낸다. Brain은 공유 상태로부터 Agent를 복원한 다음 전체 추론 및 오케스트레이션 흐름을 실행한다. 중간에 도구 호출이 있으면, Brain은 Environment 설정(관리형 샌드박스 환경, 사용자 관리 샌드박스 환경 등이 될 수 있음)에 따라 도구 호출 요청을 Worker로 라우팅한다.

|Session + Events + SSE| DP[Managed Data Plane]
  CP[Control Plane<br/>Agent / Environment / ACL] -->|versioned references| DP
  DP &lt;--> DB[(JDBC<br/>events / state / leases)]
  DP --> B[HarnessAgent Brain]
  B --> M[Model]
  B -->|local tools| L[Brain host FS / shell]
  B -->|E2B-compatible API| S[Cloud Sandbox]
  B -->|tool schema + queue| Q[Self-hosted Work Queue]
  W[Customer Worker] -->|outbound poll / result| Q
  W --> H[Customer-managed FS / sandbox] -->
![](https://intranetproxy.alipay.com/skylark/lark/__mermaid_v3/cee93bfbfdd56bdf1526682edd6df433.svg)

위의 아키텍처 분석과 구현을 종합하면, 전체 시스템은 네 개의 계층으로 읽어낼 수 있다.

| 계층 | 책임 |
| --- | --- |
| **Control Plane** | 테넌트 리소스, Agent 버전, Environment, Memory/Vault, ACL |
| **Data Plane** | Session 생명주기, 이벤트 지속성, SSE, turn lease |
| **Runtime Brain** | 정의 파싱, 캐시 히트 또는 재구축 → RuntimeContext로 복원 → `HarnessAgent.streamEvents` |
| **Hands** | 실제 파일 읽기/쓰기, shell, 도구 외부화 |


### Agent를 생성하고 실행하기

먼저 최소한의 초기화를 완료한다. Managed Agents에 로그인하고, 재사용 가능한 Workspace Copilot Agent를 생성한 다음, Local, Cloud Sandbox, Self-hosted Worker 모드를 시연한다. 이를 통해 "Agent 정의는 그대로이고, Hands의 위치만 바뀐다"는 것을 직관적으로 확인할 수 있다.

아래 내용은 Managed Agents가 `http://localhost:8080`에서 실행 중이고, 모델 키(예: `DASHSCOPE_API_KEY`)가 설정되어 있다고 가정한다.

**0. 로그인**

```bash
export BASE=http://localhost:8080
TOKEN=$(curl -fsS -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | jq -er .token)
```

**1. 샘플 Agent 정의**

"workspace copilot" Agent를 하나 만든다 — 짧은 시스템 프롬프트에 read_file, list_files, write_file 등의 도구를 더한 것으로, 서로 다른 도구가 서로 다른 worker 모드 아래에서 어떻게 실행되는지 보여준다.

```bash
AGENT=$(curl -fsS -X POST "$BASE/api/agents" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Workspace Copilot",
    "description": "blog demo agent",
    "system": "You are a workspace copilot. Prefer tools when listing or reading files. Keep answers concise.",
    "tools": [{
      "type": "agent_toolset",
      "defaultConfig": {
        "enabled": true,
        "permissionPolicy": { "type": "always_allow" }
      },
      "configs": [
        { "name": "read_file", "enabled": true },
        { "name": "list_files", "enabled": true },
        { "name": "write_file", "enabled": true }
      ]
    }]
  }')
AGENT_ID=$(echo "$AGENT" | jq -er .id)
echo "AGENT_ID=$AGENT_ID"
```


다음은 세 가지 worker 모드의 시연이다. 동일한 Agent, 동일한 Brain 추론 및 오케스트레이션 환경이 세 가지 서로 다른 Hands 경로에서 실행된다.

| 모드 | 도구가 실행되는 위치 | 도구 호출을 시작하는 주체 | 데이터 경계 | 전형적인 용도 |
| --- | --- | --- | --- | --- |
| Local | Brain이 현재 실행 중인 호스트 | Brain 프로세스 | 관리형 클러스터 내부 | 개발 및 신뢰된 환경 |
| Cloud Sandbox | E2B / FC 클라우드 샌드박스 | Brain이 E2B 호환 API를 통해 호출 | 플랫폼이 관리하는 클라우드 샌드박스 | 관리형 격리 실행 |
| Self-hosted | 고객 Worker / 고객 샌드박스 | 고객 Worker가 도구 작업을 읽어와 실행 | 고객 VPC | 프라이빗 workspace와 내장 shell / FS 도구 |


기존 Session은 도중에 worker 실행 환경을 전환하는 것을 지원하지 않는다. 신뢰 경계를 변경하려면 새 Session을 만들어야 하며, 이렇게 하면 동일한 이벤트 히스토리가 서로 다른 실행 시맨틱을 넘나들지 않게 된다.

#### Local 모드의 Worker

Local 모드는 개발 및 디버깅에 가장 적합하다. Session, Harness 추론, 모델 요청, 도구 실행이 모두 Managed 클러스터에서 시작되며, 파일과 shell은 Brain 프로세스가 보이는 로컬 환경에 직접 적용된다.

>Brain: tool_use / text
  Brain->>LocalFS: read_file / shell on host namespace
  LocalFS-->>Brain: tool_result
  Brain-->>API: agent.* + session.status_idle
  API-->>Client: SSE / events -->
![](https://intranetproxy.alipay.com/skylark/lark/__mermaid_v3/1216077e52ed0c4fd17a788f16bea26f.svg)

**Local** 모드에서는 Environment `type=local`이다. 파일시스템과 (활성화된 경우) shell은 관리형 클러스터의 호스트 네임스페이스 내부에서 완료되며, 독립된 Hands 큐도 없고 클라우드 샌드박스도 호출되지 않는다. 개발 디버깅과 신뢰된 내부 네트워크에 적합하다.

#### Cloud Sandbox 모드의 Worker

Cloud Sandbox는 Brain을 계속 호스팅하되 파일과 shell을 격리된 샌드박스로 옮긴다. Harness 추론, 모델 요청, 도구 호출의 시작 주체는 여전히 Managed 클러스터에 있으며, 실제 명령 실행과 파일 I/O는 FC Sandbox / E2B 호환 환경에서 일어난다.

>Brain: tool_use
  Note over Brain,E2B: Brain initiates sandbox lifecycle and tool calls
  Brain->>E2B: E2B-compatible API FS/shell
  E2B-->>Brain: tool_result
  Brain-->>Client: SSE agent.* / status_idle -->
![](https://intranetproxy.alipay.com/skylark/lark/__mermaid_v3/e286ba42310643e8e1a64e3db8b2417c.svg)

Agent는 E2B 클라이언트 프로토콜을 통해 컨테이너를 요청하고 그 안에서 shell / FS 작업을 실행한다. **Brain이 능동적으로 호출을 시작하며, Worker는 관여하지 않는다.** E2B 프로토콜과 호환되는 Aliyun FC Sandbox를 사용한다면, 먼저 서비스 주소, 템플릿, API Key를 준비해야 한다.

Cloud Sandbox의 관리형 경계는 세 가지 동작으로 분해할 수 있다. **샌드박스 생성, 샌드박스 내부에서의 실행, Session 종료 또는 타임아웃 후의 회수/지속화.** Managed Agents는 `E2bFilesystemSpec`을 통해 파일과 shell 도구를 동일한 샌드박스 컨텍스트에 매핑한다. `isolationScope=SESSION`인 경우, 서로 다른 Session은 기본적으로 작업 디렉터리를 공유하지 않는다. 스냅샷이나 TAR 지속화 모드를 선택한다면, 복구 정책도 `AgentStateStore`와 함께 고려해야 한다 — 파일은 복원하지 않고 모델 컨텍스트만 복원하거나 그 반대로 하면 "Agent는 작업이 끝났다고 기억하지만 workspace는 존재하지 않는" 불일치가 발생한다. 프로덕션 시스템은 이 두 가지를 하나의 복구 단위로 설계해야 한다.

#### Self-hosted 모드의 Worker

Self-hosted는 Hands를 고객 환경 안으로 더 깊이 옮긴다. Brain은 여전히 Managed 클러스터에서 Harness 추론을 완료하지만, 도구 작업은 큐에 들어가고 고객 측 Worker가 이를 능동적으로 아웃바운드 폴링한다. Worker는 로컬 workspace나 샌드박스를 관리하고 결과를 Brain에 반환한다. 이 과정 전체에서 Brain은 고객 네트워크에 진입할 필요가 없다.

>Brain: tool_use
  Brain->>Q: enqueue work + persist agent.tool_use
  Brain-->>Client: requires_action / suspended
  Worker->>Q: poll with EnvKey
  Worker->>Worker: work directory + local tool exec
  Worker->>API: user.tool_result
  API->>Brain: resume turn
  Brain-->>Client: agent.message + status_idle -->
![](https://intranetproxy.alipay.com/skylark/lark/__mermaid_v3/e2b1fc8dcc2e2f7c0aef1980ee91a93c.svg)

Self-hosted 모드에서 Brain은 **로컬 shell/FS 실행을 비활성화**하고, 관련 도구를 외부화된 스키마로 등록한다. 모델이 `tool_use`를 발생시키면 이벤트는 지속화되고 turn은 대기/큐 상태로 들어가며, 사용자 측 Worker는 Environment Key를 보유해 **아웃바운드**로 폴링한다 → 로컬 workspace를 관리하고 실행하거나, 고객 자신의 샌드박스에 연결한다 → `user.tool_result`를 반환하여 재개한다. 이는 "Brain이 능동적으로 샌드박스 API를 호출하는" Cloud Sandbox와는 정반대다 — **실행의 주도권은 사용자 측에 있으며, 샌드박스를 관리할지, 어떻게 관리할지도 고객 측 구현이 결정한다.**

Self-hosted의 목표 시나리오는 데이터베이스, 코드 저장소, 릴리스 시스템을 고객 경계 안에 유지하는 것이지만, 현재 참조 Worker는 내장 shell / FS 도구를 바로 사용할 수 있도록 지원한다. 데이터베이스, 커스텀 비즈니스 도구, 인트라넷 MCP는 여전히 향후 Worker 확장 SPI가 필요하거나, Worker 외부에서 사용자가 감싸야 한다. 이미 Worker 프로토콜과 통합된 도구의 경우, Brain은 스키마, 호출 인자, 최종 반환 결과를 볼 수 있지만 고객 VPC에 직접 연결할 필요는 없다. Worker는 플랫폼으로 HTTPS 요청을 능동적으로 시작하기만 하면 되며, 반환 전에 결과를 비식별화하고, 크기 제한을 적용하고, 감사할 수 있다.

### 더 복잡한 Agent Team 오케스트레이션 예제

#### 여러 Agent 정의하기

다음은 AgentDev 시나리오를 사용해 세 가지 역할로 구성된 팀을 보여준다. 입력은 Java 라이브러리 릴리스 계획 작업이다.

+ Repo Surgeon은 코드 품질 관점에서 체크리스트를 제공하며, workspace 읽기와 검색 기능만 가지고 있다.
+ Ops Publisher는 릴리스 프로세스 관점에서 티켓 초안을 생성한다. 이 데모에서는 텍스트 초안만 생성하며, 외부 MCP 통합은 선택적 설정으로 별도 설명한다.
+ Team Lead는 위험 요소와 인수 체크리스트를 요약한다. Team Lead는 비즈니스 데이터를 직접 건드리는 것을 가능한 한 피해야 하며, 위임과 요약만 담당해야 한다.

세 개의 Agent로 나누는 것은 역할을 쌓기 위한 것이 아니라, workspace 권한, 외부 시스템 접근, 요약 책임을 각각 제한하기 위한 것이다. 여기서 얻는 이득은 최소 권한과 독립적인 감사이지, 모든 도구를 하나의 슈퍼 Agent에 몰아넣고 프롬프트 제약에만 의존하는 것이 아니다.

먼저 fan-out에 직접 참여할 수 있는 Ops Publisher를 생성한다. 이것은 릴리스 초안만 생성하고 외부 시스템을 호출하지 않으므로, `/api/multiagent/run`이 사람 확인 단계에서 멈추는 일이 없다.

```bash
OPS_BODY=$(jq -n '{
  name: "Ops Publisher",
  system: "Draft changelogs and ticket outlines. Do not invoke tools or modify external systems.",
  tools: [{
    type: "agent_toolset",
    defaultConfig: {
      enabled: false,
      permissionPolicy: {type: "deny"}
    },
    configs: [{
      name: "read_file",
      enabled: true,
      permissionPolicy: {type: "always_allow"}
    }]
  }]
}')

OPS=$(curl -fsS -X POST "$BASE/api/agents" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "$OPS_BODY")
OPS_ID=$(echo "$OPS" | jq -er .id)
```

프로덕션에서 티켓 MCP를 통합하고 싶다면, Agent 본문에 다음 스니펫을 추가할 수 있다. `enableTools`는 명시적으로 허용된 도구만 노출하며, URL과 도구 이름은 실제 값으로 교체해야 한다.

```json
{
  "mcpServers": [{
    "name": "ticket-mcp",
    "url": "https://mcp.example.com/tickets",
    "transport": "http",
    "enableTools": ["draft_ticket"]
  }]
}
```

Skill도 함께 게시하고 싶다면, Workspace에 해당 콘텐츠가 설치되어 있는지 확인한 후 `"skills": [{"type": "workspace", "name": "release-notes"}]`를 추가할 수 있다. 현재 `mcp_toolset.defaultConfig.permissionPolicy`는 `ToolConfirmationMiddleware`에 반영되지 않으므로, 위험도가 높은 MCP 쓰기 작업은 여전히 MCP 게이트웨이 측에서 신원 확인, 승인, 멱등성 제어가 필요하며, Agent 본문의 `always_ask`에만 의존할 수 없다. 그다음 코드 workspace에 대한 읽기 전용 접근 권한을 가진 Repo Surgeon을 생성한다.

```bash
# 사용자 측 리소스 조작 — 예를 들어 "code/repository" Agent: 파일시스템 도구 + skills
REPO=$(curl -fsS -X POST "$BASE/api/agents" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Repo Surgeon",
    "system": "You review the user workspace in read-only mode and report release risks.",
    "tools": [{
      "type": "agent_toolset",
      "defaultConfig": { "enabled": true, "permissionPolicy": { "type": "always_allow" } },
      "configs": [
        { "name": "read_file", "enabled": true },
        { "name": "grep_files", "enabled": true },
        { "name": "list_files", "enabled": true }
      ]
    }]
  }')
REPO_ID=$(echo "$REPO" | jq -er .id)
```

코드 리뷰 Skill이 설치되어 있다면, `"skills": [{"type": "workspace", "name": "code-review"}]`도 추가할 수 있다. 마지막으로 Team Lead를 생성한다. 이는 위임과 결과 수집 도구를 유지하고, 실제 `MultiagentSpec`을 통해 앞의 두 멤버를 기록한다. 현재 실행 진입점은 이 필드만으로 멤버를 자동으로 시작하지 않으며, 실제 실행은 여전히 아래의 Harness 위임이나 플랫폼 fan-out에 의해 시작된다.

```bash
LEAD_BODY=$(jq -n --arg repo "$REPO_ID" --arg ops "$OPS_ID" '{
  name: "Team Lead",
  system: "You coordinate Repo Surgeon and Ops Publisher. For a direct task, delegate concrete work and collect results. When the prompt already contains member results, do not call tools or spawn sessions; only summarize risks and produce the final checklist. Use sessions_pending_completions for finished child sessions and wait_async_results only for the generic async inbox.",
  tools: [{
    type: "agent_toolset",
    defaultConfig: {
      enabled: true,
      permissionPolicy: {type: "always_allow"}
    },
    configs: [
      {name: "sessions_spawn", enabled: true},
      {name: "sessions_list", enabled: true},
      {name: "sessions_pending_completions", enabled: true},
      {name: "wait_async_results", enabled: true}
    ]
  }],
  multiagent: {
    type: "agent_team",
    agents: [
      {type: "agent", id: $repo},
      {type: "agent", id: $ops}
    ]
  }
}')

LEAD=$(curl -fsS -X POST "$BASE/api/agents" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "$LEAD_BODY")
LEAD_ID=$(echo "$LEAD" | jq -er .id)
printf 'OPS_ID=%s\nREPO_ID=%s\nLEAD_ID=%s\n' "$OPS_ID" "$REPO_ID" "$LEAD_ID"
```

`MultiagentSpec`의 와이어 스키마는 `type + agents[]`다. 멤버 참조는 `type`, `id`, 그리고 선택적인 `version`을 포함한다. `wait_async_results`는 범용 비동기 수신함을 블로킹 대기할 때 사용되고, `sessions_pending_completions`는 완료되었지만 아직 소비되지 않은 자식 Session 결과를 열거하는 데 사용된다. 이 둘은 서로 다른 비동기 패턴을 다루므로 Team Lead는 둘 다 활성화하되, 시스템 프롬프트에서 어느 것을 언제 사용해야 하는지 명확히 해야 한다.

#### 함께 오케스트레이션하기

시스템은 두 가지 서로 다른 다중 Agent 실행 방식을 제공한다.

+ **Harness 네이티브 위임**: Team Lead가 추론 중에 `sessions_spawn` / Subagent 도구를 사용해 작업을 동적으로 분해하며, 부모와 자식 태스크 사이에 명시적인 위임과 결과 회수 관계가 있다.
+ **플랫폼 fan-out**: `/api/multiagent/run`이 여러 Agent를 위한 Managed Session을 생성하고 동일한 메시지를 순차적으로 또는 병렬로 보낸다. 독립적인 분석, 배치 처리, 투표에 적합하다.

### 동작 방식 더 깊이 들여다보기

앞 절들은 사용자 관점에서 Brain과 Hands의 조율을 소개했고, Agent Team 예제를 통해 다중 에이전트 오케스트레이션 시나리오를 보여주었다. 다음은 Control Plane, Data Plane, Worker를 더 깊이 분해하여, 각 계층이 어떤 상태를 저장하는지, 어떤 장애 책임을 지는지, AgentScope 2.0이 어떤 역할을 하는지 설명한다.

**한 문장으로 말하면**: Control Plane은 "정의와 권한"을 관리하고, Data Plane은 "실제로 실행하고 기록하는 것"을 관리하며, Worker는 "누구의 머신에서 동작할지"를 관리한다. AgentScope 2.0의 `HarnessAgent` + 파일시스템/샌드박스 추상화는 Data Plane과 Hands의 커널이며, SaaS API는 추론 루프를 다시 구현하지 않고도 플랫폼 레벨의 시맨틱을 구축한다.

#### Control Plane

Control Plane은 "무엇을 실행할 수 있고 누가 사용할 수 있는지"를 담당한다. 정적 Agent 정의와 그 버전, 그리고 Model, Skills, MCP, Tools, Environment, Memory, Vault, Resources 같은 재사용 가능한 리소스를 관리한다. 리소스는 개별 사용자에게 속하여 owner / ACL로 격리될 수도 있고, 공개 Skill, MCP 카탈로그, 내장 도구 세트처럼 플랫폼에 의해 전역적으로 사전 구축될 수도 있다.

이러한 리소스들은 "정의, 참조, 마운트"라는 세 가지 관계로 이해할 수 있다. Model, Tools, MCP, Skills는 Agent 버전 정의에 포함되고, Environment는 독립적으로 존재하며 Session에 의해 참조된다. Memory Store, Vault, Files/Resources는 Session이 생성될 때 마운트된다. 전역적으로 사전 구축된 리소스는 플랫폼 기본값을 제공하고, 사용자 리소스는 owner / share ACL을 갖는다. 이렇게 하면 공개 Skill을 모든 Agent에 복사할 필요가 없으면서도, 서로 다른 테넌트가 공유 디렉터리를 통해 서로의 데이터를 볼 수 없도록 방지한다.

Control Plane은 변경 거버넌스도 담당한다. Agent 업데이트는 새 버전을 생성하고, 기존 Session은 현재 지원되는 히스토리 필드를 계속 기록하고 복원할 수 있다. Environment 키는 순환될 수 있고, 리소스는 즉시 물리적으로 삭제되는 대신 아카이브될 수 있으며, 고위험 내장 도구 권한은 버전과 함께 기록된다. 프로덕션 플랫폼에서는 이러한 기능들이 "새 모델을 호출할 수 있는지"보다 더 중요한 경우가 많다. 이는 롤백, 카나리, 사고 책임 추적이 가능한지 여부를 결정하기 때문이다.

Environment와 Session은 혼동하기 쉽지만 서로 다른 계층에 속한다. 이 글에서는 다음과 같은 경계를 사용한다.

+ **Environment는 Control Plane에 속한다**: 이는 "실행 플레인 템플릿"(`local` / `sandbox` / `remote` / `self_hosted` + config + environment key)이며, 여러 Session에 의해 참조될 수 있고, 아카이브와 공유를 지원하며, 그 자체로는 대화 이벤트를 생성하지 않는다.
+ **Session은 Data Plane에 속한다**: 이는 Agent × Environment의 실행 중인 인스턴스로, 상태 머신과 이벤트 로그를 가진다. 생성 파라미터는 Control Plane의 `agentId` / `environmentId`를 참조하지만, 생명주기 API(events / stream / interrupt)는 Data Plane의 핵심이다.

따라서:

+ 세션 생성 → **Data Plane** (다음 절에서 자세히 설명)
+ Environment 정의 → **Control Plane** (`POST /api/environments`, 키 순환, 아카이브)

#### Data Plane

Data Plane은 "Agent 버전을 기록하는 Session이 실제로 실행되도록 하고, 그 과정을 완전히 기록하는 것"을 담당한다. 모델 호출, ReAct 루프, Harness hook, turn lease, Session 상태 머신, 이벤트 지속성과 SSE 푸시를 호스팅하며, interrupt, HITL, 외부화된 도구 결과 재개를 처리한다.

이 작업들은 평범한 CRUD 부가 기능이 아니다 — 상태 머신을 중심으로 이루어진다. Session을 생성하면 Agent 버전과 Environment 참조가 기록되고, `user.message`는 상태를 idle에서 running으로 밀어 올리며, 도구 확인은 requires_action을 running으로 복원하고, interrupt는 현재 turn을 취소하려 시도하며, archive는 이후 사용은 종료시키지만 감사 히스토리는 유지하고, delete는 세션과 이벤트를 삭제한다. 클라이언트는 내부 스레드가 아직 살아있는지 폴링하는 대신 이벤트로 UI를 구동해야 한다.

Data Plane은 동등한 SaaS 레플리카들로 구성된다. 요청은 어떤 인스턴스로든 도달할 수 있다. 레플리카는 먼저 `agentId`로 Control Plane에서 버전 정의를 찾은 다음, Agent 버전, Environment, 마운트 정보로부터 build key를 계산한다. 캐시가 히트하면 `HarnessAgent`를 재사용하고, 그렇지 않으면 재구축한다. 각 turn은 `userId`와 `sessionId`를 포함하는 `RuntimeContext`를 통해 세션 상태를 찾는다. 따라서 여기서 "무상태 레플리카"란 대체 불가능한 권위 있는 상태를 보유하지 않는다는 뜻이지, 요청마다 Java 객체를 새로 만들지 않는다는 뜻이 아니다.

`RuntimeContext`는 모든 상태를 하나의 Map에 욱여넣는 것이 아니라, 한 번의 실행을 위한 "신원과 리소스 로케이터"로 이해할 수 있다. `userId`는 멀티테넌트 네임스페이스와 ACL을 결정하고, `sessionId`는 복구 가능한 단기 brain 상태를 찾아준다. Environment는 파일시스템/샌드박스 구현을 결정하며, Memory Store와 Vault는 빌드 단계에서 파일시스템 경로와 자격 증명으로 해석된다. Harness는 이러한 안정적인 추상화에만 의존하므로, 동일한 요청이 다른 레플리카에 도달해도 시맨틱적으로 동등한 런타임 환경을 재조립할 수 있다.

Data Plane은 실제로 서로 다른 생명주기를 가진 네 계층의 상태를 호스팅한다.

| 상태 계층 | 전형적인 콘텐츠 | 생명주기 / 진실 원천 |
| --- | --- | --- |
| Agent 버전 | name, system, model, tools, skills, MCP 등 | Control Plane이 전체 스냅샷을 저장한다; 현재 런타임은 히스토리 필드를 부분적으로만 재구성한다 |
| Session 이벤트 | user.message, tool_use, agent.message, status | Append-only 로그, 감사와 클라이언트 캐치업 스트림의 진실 원천 |
| Agent brain 상태 | 모델 메시지, 압축된 컨텍스트, Hook 상태 | `AgentStateStore`, userId/sessionId로 복원됨 |
| Workspace / Sandbox | 파일, 작업 산출물, 도구 부작용 | Local / BaseStore / E2B / Self-hosted 실행 플레인 |


이 네 계층은 "대화 히스토리를 저장한다"로 요약될 수 없다. 예를 들어 Session 이벤트는 모델이 한때 파일 쓰기를 요청했다는 것을 증명할 수는 있지만 파일 자체를 대신할 수는 없다. AgentStateStore는 컨텍스트를 복구할 수 있지만 외부 데이터베이스의 부작용을 자동으로 복구하지는 않는다. 복구 흐름은 각 계층을 별도로 복구한 다음, 이벤트 ID, 도구 호출 ID, 리소스 참조를 통해 다시 연관시켜야 한다.

Harness 추론이 도구를 호출해야 할 때, 구체적인 실행은 Environment에 의해 결정된다. Cloud Sandbox는 Harness의 파일시스템 / 샌드박스 추상화를 직접 재사용하며, Brain이 E2B 호환 호출을 시작한다. Self-hosted는 도구를 스키마 전용 정의로 대체하고, `agent.tool_use` 이후 turn을 일시 중단하며, 작업 큐와 Worker 프로토콜을 통해 결과를 반환한다.

여기서 AgentScope 2.0의 역할은 매우 명확하다. **HarnessAgent와 FS/Sandbox 추상화를 제공하여 "효과 기본값"과 "교체 가능한 실행 플레인"을 보장한다.** Managed Agents는 또 다른 프라이빗 ReAct를 감싸는 대신 lease, 이벤트 계약, 멀티테넌시, ACL을 담당한다.

#### Worker

Worker는 도구가 Brain에서 실제 실행 환경으로 어떻게 이동하는지에 초점을 맞춘다. 시스템은 두 가지 경로를 가지며, 누가 도구 호출을 시작하고 누가 샌드박스 생명주기를 관리하는지로 구분된다.

완전 관리형 모드에서는 Brain이 Sandbox를 생성하고 회수하는 것을 담당하며, AgentScope가 제공하는 E2B 호환 API를 통해 파일 또는 shell 호출도 능동적으로 시작한다. 백엔드는 FC Sandbox와 같은 호환 서비스로 지원될 수 있으며, 도구 프로세스와 작업 디렉터리는 샌드박스 인스턴스 내부에 있다. 플랫폼이 전체 핸들을 보유하므로 타임아웃, 격리 범위, 지속화 정책을 일괄적으로 설정할 수 있다.

Self-hosted 모드에서는 Brain이 모델로부터 도구 호출을 받은 후, 고객 VPC에 연결하는 대신 `agent.tool_use`를 지속화하고 작업 항목을 생성한다. 고객 측 Worker는 큐를 능동적으로 폴링하고, 자신의 호스트나 샌드박스에서 도구를 실행하며, `user.tool_result`를 통해 결과를 반환하여 Brain이 다음 라운드의 추론을 재개할 수 있게 한다.

이 둘의 장애 복구 책임 또한 다르다. 완전 관리형 모드에서는 Brain이 샌드박스 핸들을 알고 있으므로 타임아웃, 스냅샷, 회수 정책을 일괄적으로 설정할 수 있다. Self-hosted 모드에서는 Brain이 작업 상태와 도구 결과만 알고 있으며, 로컬 샌드박스가 여전히 살아있는지, 중복 작업이 안전한지, 결과를 비식별화해야 하는지는 고객 Worker가 책임져야 한다. 플랫폼은 프로토콜과 상태 머신을 제공하지만, 고객을 위해 비즈니스 도구의 멱등성 시맨틱을 정의할 수는 없다.

Work 상태 머신은 `queued → starting → active → stopping → stopped`다.

독립적인 Worker를 배포할 때는 Brain과 고객 측 프로세스 모두 설정이 필요하다. 아래에는 최소한의 시작 방법과 프로덕션 체크리스트가 제시되어 있다.

## 요약

AgentScope 2.0은 엔터프라이즈급 분산 시나리오를 위해 포지셔닝되어 있다. 이는 엔터프라이즈 DataAgent, SreAgent 등을 구축하기 위한 분산 Agent Framework로 사용될 수 있을 뿐만 아니라, 동일한 Harness를 사용해 엔터프라이즈 Managed Agents를 지원하며 Managed Agents 하부의 Agent Runtime이 될 수도 있다. 이는 기업이 "스스로 블록을 조립하는 것"과 "완전히 블랙박스인 관리형 호스팅" 사이에서 선택해야 하는 상황을 피하게 해준다 — 동일한 Harness 커널이 두 모드를 모두 제공할 수 있다.

+ 문서: [https://java.agentscope.io](https://java.agentscope.io)
+ GitHub: [https://github.com/agentscope-ai/agentscope-java](https://github.com/agentscope-ai/agentscope-java)
+ AgentScope Builder: [https://github.com/agentscope-ai/agentscope-java/tree/main/agentscope-service](https://github.com/agentscope-ai/agentscope-java/tree/main/agentscope-service)
