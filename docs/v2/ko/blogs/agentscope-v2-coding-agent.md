---
title: "코딩 에이전트: 후반전 — 개인 생산성에서 조직형 엔지니어링 시스템으로"
---

아직도 옛날 방식으로 손수 코드를 작성하는 개발자는 사실상 무형문화재 전수자가 되기 위한 훈련을 받는 것과 다름없다. 대다수는 이미 Claude Code나 Cursor 같은 코딩 에이전트를 사용하고 있다. 방향은 맞지만 시나리오가 다르므로 해법도 달라진다 — 개인 생산성을 위해 로컬에 AI 어시스턴트를 설치하는 것과, 조직 내부에 AI 기반 엔지니어링 협업 시스템을 구축하는 것은 완전히 차원이 다른 문제다. 전자는 이미 성숙한 제품들이 존재하지만, 후자는 이제 막 시작된 영역이다. 이 글은 후자에 관한 이야기다.

---

## 이 문제를 고민하는 것은 당신만이 아니다

2025년 말부터 2026년 초 사이에 흥미로운 일이 있었다. Stripe, Ramp, Coinbase가 모두 자사 내부 코딩 에이전트를 공개적으로 밝힌 것이다 — Stripe는 이를 [Minions](https://stripe.com/engineering)라 부르고, Ramp는 Inspect, Coinbase는 Cloudbot이라 부른다. 세 회사는 서로 참고 없이 독립적으로 만들었음에도 거의 동일한 아키텍처로 수렴했다.

이는 우연이 아니다. 코딩 에이전트를 "터미널에서 한 사람이 사용하는 것"에서 "팀 전체가 Slack이나 GitHub Issue를 통해 언제든 트리거하는 것"으로 업그레이드하면, 동일한 엔지니어링 문제들에 의해 동일한 경로로 떠밀리게 된다. LangChain 팀은 이 패턴을 발견하고 2026년 3월 [Open SWE](https://github.com/langchain-ai/open-swe)를 공개했다 — Stripe/Ramp/Coinbase의 공통 패턴을 오픈소스 프레임워크로 증류한 것이다. Open SWE README의 첫머리는 직설적이다.

> Elite engineering orgs like Stripe, Ramp, and Coinbase are building their own internal coding agents — Slackbots, CLIs, and web apps that meet engineers where they already work.

"엔지니어가 이미 있는 곳에서 만난다"는 것은 엔지니어에게 새 도구를 배우게 하는 것이 아니라, 에이전트가 엔지니어들이 이미 사용하는 Slack 채널, GitHub Issue, IM 대화 속으로 스며들어 팀 워크플로의 일부가 되게 한다는 뜻이다.

우리도 AgentScope Harness를 만들면서 같은 길을 걸었다. 이 글은 공식 예제 `agentscope-codingagent`를 실마리로, 프로덕션급 코딩 에이전트가 실제 배포 과정에서 어떤 벽에 부딪히는지, 그리고 우리가 그 벽들을 어떻게 넘었는지를 설명한다.

---

## 코딩 에이전트의 두 가지 차원

먼저 포지셔닝을 명확히 하자. Claude Code는 **"내가 혼자서 더 빠르게 코드를 작성한다"**를 최적화한다 — 당신이 입력하면 그것이 동작하고, 당신은 그것이 동작하는 것을 지켜보며, 언제든 중단하고 수정할 수 있다. 상태는 로컬 머신에 존재하고, 트리거는 자기 자신이며, 신뢰 경계는 단순히 자신의 머신을 신뢰하는 것이다.

우리가 만들고 있는 것은 다른 문제를 해결한다. **"팀의 어떤 작은 작업에 대해, 나는 지켜볼 필요조차 없다 — 그냥 에이전트에게 던져두고, 실행하게 하고, 그것이 연 PR을 리뷰하면 된다."** 트리거는 어떤 Issue 댓글 작성자든 될 수 있고, 에이전트는 아무도 지켜보지 않는 상태에서 10분에서 한 시간가량 원격으로 실행된다. Stripe 엔지니어들은 Slack에서 @Minions에게 "이 버그 좀 고쳐줘"라고 태그하고, 이후 초안 PR을 받는다 — 이것이 조직형 코딩 에이전트가 갖춰야 할 모습이다.

두 형태는 기능적으로는 겹친다 — 둘 다 코드를 작성하고, 명령을 실행하고, 파일을 편집할 수 있다 — 하지만 엔지니어링 제약은 완전히 다르다. Claude Code는 당신 개인 소유의 자동차다. 운전자(자기 자신)를 신뢰하므로 에어백 이상의 보호 장치는 거의 필요 없다. 조직형 코딩 에이전트는 택시 회사의 차량 한 대다 — 승객은 소유주가 아니고, 운전은 원격으로 이루어지며, 블랙박스, GPS 추적, 주행거리 제한, 비상 제동, 그리고 차 한 대가 고장 나도 전체 차량대가 무너지지 않는다는 보장이 필요하다.

Open SWE는 이 철학을 한 문장으로 요약한다. **"먼저 격리하고, 그 경계 안에서 전체 권한을 부여하라."** 먼저 격리한 다음 권한을 위임하는 것이다.

사실 벤더들도 이 방향으로 움직이고 있다. GitHub Copilot Coding Agent는 이미 Issue에 할당되는 것만으로 트리거되어, 클라우드에서 실행된 뒤 자동으로 초안 PR을 열 수 있다. Claude Code에도 CI에서 프로그램적으로 호출할 수 있는 헤드리스 모드가 있다. 그 철학은 본질적으로 동일하다 — 샌드박스 격리, 비동기 트리거, PR 기반 출력 — 벤더들은 선도 기업들이 입증한 패턴을 제품화하여 바로 쓸 수 있는 SaaS 서비스로 포장하고 있다. Stripe, Ramp, Coinbase가 자체 구축을 선택한 주된 이유는 각자 엔지니어링 시스템의 구체적 사정 때문이다 — 내부 시스템과의 깊은 통합, 데이터 컴플라이언스 요건, 워크플로 커스터마이징 정도. 두 경로는 서로 모순되지 않는다. 어느 쪽이 더 적합한지는 조직 자체의 제약과 필요에 달려 있다.

AgentScope Harness가 하고자 하는 일은, 이 경로에서 공통으로 나타나는 엔지니어링 문제들을 조합 가능한 기반 역량으로 추상화하여, 자체 구축을 선택한 팀들이 처음부터 다시 시작할 필요가 없게 하는 것이다.

---

## 일단 실행부터 해보기

가장 빠르게 시험해 보는 방법은 — 환경 변수 하나와 Maven 명령 하나만으로, 로컬에서 대화형 REPL이 실행된다. Docker도, webhook도, GitHub App도 필요 없다.

```bash
export DASHSCOPE_API_KEY=sk-...
cd agentscope-java
mvn install -pl agentscope-examples/agents/agentscope-codingagent -am -DskipTests -q
mvn exec:java -pl agentscope-examples/agents/agentscope-codingagent
```

시작 후 `You>` 프롬프트에 도달하며, 에이전트는 자체 워크스페이스인 `~/.agentscope/codingagent/workspace/`에서 작업한다. 아무것도 설정하지 않아도 이미 완전한 워크스페이스, 세션 지속성, 장기 메모리를 갖추게 된다.

```
You> write hello.txt with a haiku about Java
You> clone https://github.com/owner/repo into the workspace and tell me what it does
You> review https://github.com/owner/repo/pull/42
```

이 시점에서 로컬 데모는 잘 동작한다. 하지만 데모와 프로덕션 사이의 거리는 대부분의 사람들이 생각하는 것보다 훨씬 멀다.

---

## 벽 1: 실행 격리

에이전트에게 `execute` 도구를 주어 셸 명령을 실행할 수 있게 한다. 첫날은 신난다 — `git clone`도 하고, `mvn test`도 실행하며, 전체 빌드를 알아서 통과시킨다. 둘째 날 문제를 깨닫는다. **트리거가 더 이상 당신이 아니다.** 어떤 GitHub Issue 댓글 작성자든, 어떤 Slack 사용자든 에이전트가 코드를 실행하게 만들 수 있고, 당신의 호스트는 모델이 결정한 명령에 직접 노출된다.

이것이 조직형 코딩 에이전트를 만드는 모든 팀이 마주치는 첫 번째 벽이다. Coinbase는 자체 구축 샌드박스 인프라로, Ramp는 Modal의 클라우드 컨테이너로, Open SWE는 Modal, Daytona, Runloop 등 다중 백엔드를 지원하는 추상화로 이를 해결한다. 우리도 동일한 추상화를 만들었다 — `FilesystemSpec`이 통일된 인터페이스이고, Docker 컨테이너, 원격 KV, 로컬 파일시스템이 플러그인 가능한 구현체다. Docker를 예로 들면 다음과 같다.

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("coding")
    .model(model)
    .workspace(workspace)
    .filesystem(new DockerFilesystemSpec()
        .image("agentscope/coding-sandbox:latest")
        .isolationScope(IsolationScope.SESSION))
    .build();
```

이 한 줄로 `read_file`, `write_file`, `execute` 같은 모든 내장 도구가 자동으로 샌드박스 백엔드로 전환되며, 에이전트 코드는 전혀 바뀔 필요가 없다. `IsolationScope.SESSION`은 각 GitHub Issue / PR / IM 대화가 자신만의 공간에서 실행되도록 보장한다.

샌드박스는 "호스트를 해치지 않는다"는 문제를 해결한다. 하지만 다음 문제가 곧바로 닥친다.

---

## 벽 2: 상태 연속성이 깨진다

한 사용자가 PR에 댓글을 하나 더 단다. "테스트를 하나 더 추가해줘." 에이전트는 이전 환경에서 이어서 작업해야 한다 — 5분을 기다려 `git clone` + `npm install`을 다시 하는 것은 견디기 힘들다.

Open SWE는 이를 "영속 샌드박스"로 해결한다 — 같은 스레드의 후속 메시지는 동일한 샌드박스를 재사용한다. 우리의 해법은 더 세밀하다. 각 `call()`이 끝날 때마다 샌드박스는 워크스페이스 상태를 스냅샷으로 패키징하고, 다음번에 필요할 때 이를 복원한다. 컨테이너가 아직 살아있다면 바로 이어서 진행하고, 컨테이너가 사라졌다면 스냅샷으로부터 새 컨테이너를 시작하며, 스냅샷도 없다면 완전한 초기화를 수행한다. 스냅샷 백엔드는 로컬 파일, OSS, Redis가 될 수 있다. 프로덕션에서는 설정 한 줄만 추가하면 된다.

```java
.snapshotSpec(new OssSnapshotSpec(ossClient, "my-bucket", "agentscope/"))
```

이어받아야 하는 것은 샌드박스 상태만이 아니다. **대화 히스토리, 압축된 요약, Plan 상태, 할 일 목록, 권한 규칙 — 전체 AgentState는 각 `call()`이 끝날 때 자동으로 영속화되고, 동일한 `(userId, sessionId)`로 다음 `call()`이 호출될 때 자동으로 로드된다.** 기본적으로 로컬 파일에 저장되며, 다중 레플리카 프로덕션 환경에서는 Redis로 전환하는 데 한 줄이면 충분하다. Redis로 전환하고 나면 노드 하나가 죽어도 세션은 다른 노드로 옮겨가고, 롤링 릴리스는 새 파드에서 자동으로 복구되며, GitHub Issue 대화 도중에 DingTalk로 전환하는 것도 가능하다 — `sessionId`만 일치하면 메모리는 그대로 유지된다.

---

## 벽 3: 컨텍스트가 폭발한다

긴 Issue는 수십 라운드의 대화를 이어가고, `git diff` 출력은 수만 자에 달하며, `mvn test` 로그는 수십 킬로바이트에 이른다 — 모델의 컨텍스트 윈도우는 금세 가득 찬다. 이는 장시간 실행 태스크를 다루는 코딩 에이전트를 만드는 모든 팀이 겪는 문제다. Open SWE의 기반이 되는 Deep Agents 프레임워크는 파일 기반 메모리를 사용해 오프로드한다 — 큰 결과물을 대화 히스토리에 남기지 않고 파일에 기록하는 것이다.

우리의 해법은 네 가지의 독립적이고 조합 가능한 메커니즘이다. **대화 요약**은 메시지 수가 너무 많아지면 자동으로 트리거되어, 뒷부분은 원문 그대로 유지하고 앞부분은 요약으로 압축한다. **큰 도구 결과 오프로드**는 지나치게 긴 출력을 워크스페이스 파일에 기록하고, 컨텍스트에는 앞뒤로 약 2K만 남기고 `read_file` 경로 힌트를 함께 남긴다. **인자 절단**은 `write_file`에 전달되는 큰 인자도 절단한다. **오버플로 폴백**은 실제로 `context_length_exceeded`가 발생했을 때 긴급 압축을 수행하고 재시도한다.

```java
.compaction(CompactionConfig.builder()
    .triggerMessages(50).keepMessages(20)
    .truncateArgs(CompactionConfig.TruncateArgsConfig.builder()
        .maxArgLength(2000).build())
    .build())
.toolResultEviction(ToolResultEvictionConfig.defaults())
```

**이것은 선택 사항이 아니다.** 코딩 에이전트는 반드시 장시간 세션을 실행하고, 반드시 큰 diff를 만들어낸다. 이 두 가지를 활성화하지 않으면 결국 벽에 부딪히게 된다.

동시에 `MEMORY.md`는 매일 이어지는 대화의 흐름에서 장기 사실을 주기적으로 병합한다. 얼마간 실행되고 나면 에이전트는 팀의 규칙을 스스로 익히게 된다 — "이 저장소의 테스트 명령은 `mvn -pl module test`다. 루트에서 `mvn test`를 쓰면 너무 느리니 쓰지 말 것" — 그리고 다음번에는 다시 물을 필요가 없어진다.

---

## 벽 4: 여러 사람이 동시에 사용한다

앞의 세 벽 — 격리, 상태 연속성, 컨텍스트 관리 — 은 "에이전트 세션 하나가 안정적으로 실행될 수 있다"는 문제를 해결한다. 하지만 조직형 서비스는 처음부터 멀티테넌트다. 수십 개의 Issue, 수십 개의 PR, 수십 개의 IM 대화가 동시에 실행되고, 각각 자신만의 코드 저장소, 의존성 디렉터리, 대화 히스토리, 장기 메모리를 가지며, **절대로 서로 뒤섞여서는 안 된다.**

그제서야 Open SWE의 README가 "여러 작업이 병렬로 실행된다 — 각자 자신의 샌드박스 안에서, 대기열 없이"를 핵심 기능으로 꼽는 이유를 진정으로 이해하게 된다. 이는 자랑이 아니라 절대적인 요구 사항이다.

우리는 `IsolationScope`로 격리 단위를 제어한다. `SESSION`은 각 sessionId에 고유한 샌드박스를 부여하고, `USER`는 동일 사용자의 여러 대화가 하나의 저장소 클론을 공유하게 한다. 격리는 샌드박스 계층에만 국한되지 않는다 — 세션 상태, 메모리, 서브에이전트 태스크가 모두 동일한 단위를 따르므로, 개발자가 직접 신경 쓸 필요가 없다.

동시성 제어 또한 이 계층에 존재한다. `RunDispatcher` + `MessageQueueHook`은 동일한 스레드에서 한 번에 하나의 추론만 실행되도록 강제한다. 에이전트가 실행 중인 동안 사용자가 댓글을 하나 더 추가하면, 새 메시지는 현재 추론을 중단시키지 않는다. 대신 큐에 들어가서 다음 라운드 전에 주입된다 — Open SWE의 `check_message_queue_before_model` 미들웨어와 같은 발상이다. `ThreadBudgetHook`은 스레드당 모델 호출 예산 상한을 두고, `ModelCallLimitHook`은 전역 상한을 둔다 — **한 사용자의 폭주 루프가 회사 전체의 쿼터를 소진시켜서는 안 된다.**

---

## 벽 5: 에이전트는 여러 진입점을 처리해야 한다

Stripe의 Minions는 Slack을 경유하고, Coinbase의 Cloudbot도 Slack을 경유하며, Open SWE는 Slack + Linear + GitHub에 동시에 연결된다. 국내(중국) 시나리오에서는 DingTalk와 Feishu도 필요하다. 조직형 코딩 에이전트가 공유하는 믿음은 다음과 같다. **사용자에게 에이전트를 찾기 위해 새 인터페이스로 갈아타게 하지 말고, 에이전트가 사용자가 이미 있는 곳에 나타나게 하라.**

우리는 Harness 위에 채널 어댑터 계층을 추가하여 서로 다른 진입점의 이벤트를 일관되게 `(threadId, message)`로 매핑했다. `github:issue:owner/repo#42`는 SHA-256을 거쳐 하나의 UUID로 수렴하며, DingTalk와 Feishu도 마찬가지다. 이 결정론적 매핑은 동일한 Issue의 모든 댓글이 동일한 에이전트 세션으로 라우팅되도록 보장하며, 대화 히스토리는 자동으로 복원된다.

---

## 이 벽들을 넘고 나서: 몇 가지 교훈

### 컨텍스트 엔지니어링은 유행어가 아니라 엔지니어링적 필연이다

업계는 이제 "에이전트에게 컨텍스트를 어떻게 공급할 것인가"라는 질문을 컨텍스트 엔지니어링이라 부른다. 흥미로운 점은 거의 모든 주류 코딩 에이전트가 독립적으로 같은 패턴에 수렴했다는 것이다. Claude Code에는 `CLAUDE.md`가 있고, GitHub Copilot에는 `.github/copilot-instructions.md`가 있으며, Open SWE에는 `AGENTS.md`가 있다. **저장소 수준의 관례는 시스템 프롬프트에 하드코딩되어서는 안 되며, 버전 관리가 가능하고, 리뷰 가능하며, 독립적으로 업데이트 가능한 파일이어야 한다.**

우리의 워크스페이스는 이 아이디어를 한 걸음 더 밀어붙인다. 성격과 행동 관례를 정의하는 `AGENTS.md` 외에도, 팀의 SOP(커밋 관례, 테스트 관례)를 위한 `skills/`, 서브에이전트를 선언하는 `subagents/`, 도메인 지식을 위한 `knowledge/`, 그리고 장기 사실을 축적하는 `MEMORY.md`가 있다. 워크스페이스는 Git으로 관리되고, CI에서 검증되며, 배포 시 모든 레플리카에 하이드레이션된다. **자주 변경되어야 할 것은 이 파일들이지, Java 코드가 아니다.**

### 도구 큐레이션이 도구 수량보다 중요하다

Stripe가 Minions 경험을 공개적으로 공유했을 때, 자사 에이전트가 약 500개의 도구를 가지고 있다고 언급하면서도 "도구 수량보다 도구 큐레이션이 중요하다"고 강조했다. Open SWE도 같은 철학을 따라 약 15개의 핵심 도구만 노출한다. 우리의 접근 방식도 유사하다 — 내장 도구 세트는 파일 조작 + 셸 실행 + 메모리 검색으로 제한되며, 비즈니스 도구는 `toolkit.register(...)`를 통해 필요에 따라 등록된다.

### 핵심 단계는 프롬프트만으로 보장될 수 없다

**"테스트 실행하는 것 잊지 마"라고 프롬프트로만 모델에게 지시하는 것에 의존할 수는 없다. 핵심 단계는 결정론적 로직으로 보장되어야 한다.** GitHub Copilot Coding Agent는 작업을 마친 뒤 저장소의 기존 CI 파이프라인을 통해 결과를 검증한다. Open SWE에는 안전장치로 `open_pr_if_needed` 미들웨어가 있다 — 에이전트가 PR 열기를 잊으면 미들웨어가 자동으로 대신 해준다. Harness의 미들웨어 메커니즘(`MessageQueueHook`, `ThreadBudgetHook` 등)도 같은 발상을 따른다. **모델에 맡길 것과 결정론적 코드로 보장할 것 사이의 경계는 명확하게 그어야 한다.**

Open SWE 블로그의 한 구절이 정확하게 짚어낸다. agentic(모델 주도)과 deterministic(미들웨어 주도)의 분리야말로 에이전트를 "데모는 잘 동작한다"에서 "프로덕션에서 신뢰할 수 있다"로 끌어올리는 것이다.

한 가지 더. **초안 PR을 출력 계약으로 삼는다.** Copilot Coding Agent든, Open SWE든, Stripe Minions든, 에이전트의 출력은 항상 병합 전에 사람의 리뷰를 요구하는 초안 PR이다. 에이전트가 프로덕션 코드를 직접 변경하지 않는다는 것 — 이것이 조직형 코딩 에이전트의 기본적인 안전 가정이다.

### 큰 변경은 신중하게 생각한 후에

"인증 모듈 전체를 리팩터링하라"는 작업을 에이전트가 곧바로 처리하게 두는 것은 위험도가 높다 — 진행하면서 생각하고 변경하다가 도중에 무언가를 망가뜨릴 수 있다. Harness의 Plan Mode는 이를 "먼저 생각하기 → 계획 작성 → 사람 확인 → 그런 다음 실행"이라는 흐름으로 굳혀낸다. 활성화되면 에이전트는 읽기 전용 단계로 진입하며, 계획 단계를 벗어나려면 사람의 확인이 필요하다. Coinbase Cloudbot의 "Agent Councils"도 같은 발상을 따른다 — 위험도가 높은 작업 전에 사람의 승인 노드를 추가하는 것으로, **모델이 실수하지 않기를 기도하는 대신 프로세스적 제약으로 대체한다.**

### 서브에이전트는 있으면 좋은 정도가 아니다

Open SWE는 Deep Agents의 `task` 도구로 서브에이전트 디스패치를 하고, Stripe는 Blueprints로 오케스트레이션하며, Ramp는 Sessions + Child Sessions를 사용한다. Harness의 서브에이전트 사용법은 가볍다 — 워크스페이스에 책임과 도구 세트를 선언하는 마크다운 파일을 작성하면, 메인 에이전트가 `agent_spawn`을 호출해 위임할 수 있다. 백그라운드 호출에는 `timeout_seconds=0`을 추가해 메인 에이전트가 블로킹되지 않게 하며, 서브에이전트가 끝나면 프레임워크가 결과를 다음 추론 라운드에 자동으로 주입한다.

---

## 단일 머신에서 엔터프라이즈까지: 진화 경로

이 벽들을 한 번에 다 넘을 필요는 없다. Harness는 가장 단순한 형태에서 시작해 필요에 따라 업그레이드할 수 있도록 설계되었다.

**1단계: 로컬 CLI.** 별도 설정 없이 `execute`는 호스트의 `sh -c`에서 실행되고, 상태는 로컬 파일에 저장된다. 신뢰할 수 있는 로컬 환경에서만 사용한다.

**2단계: 샌드박스 추가.** 한 줄의 `.filesystem(new DockerFilesystemSpec()...)`로 모든 실행이 컨테이너 안으로 옮겨간다. 각 Issue/PR마다 임시 컨테이너가 부여되고, 호스트의 공격 표면이 노출되지 않는다.

**3단계: 다중 레플리카 분산.** `stateStore`를 Redis로 교체하고, 샌드박스 스냅샷을 OSS에 저장하며, 동시성 제어를 위해 `executionGuard`를 추가한다. 이 시점에서 수평 확장이 가능해진다 — 로드 밸런서 뒤에서 N개의 레플리카를 실행하고, 어떤 레플리카든 어떤 사용자의 어떤 대화든 이어받을 수 있다.

```java
.filesystem(new DockerFilesystemSpec()
    .image("agentscope/coding-sandbox:latest")
    .isolationScope(IsolationScope.USER)
    .snapshotSpec(new OssSnapshotSpec(ossClient, "bucket", "prefix/"))
    .executionGuard(RedisSandboxExecutionGuard.builder(jedis)
        .leaseTtl(Duration.ofMinutes(30)).build()))
.stateStore(RedisAgentStateStore.builder().lettuceClient(redisClient).build())
```

**4단계: 관측성과 레이트 리미팅.** Prometheus 메트릭, 모델 예산, 업스트림 레이트 리미팅과 재시도 — 이 모두를 합친 것이 "출시 후 온라인 상태를 유지할 수 있는" 시스템의 모습이다.

---

## 수렴이 우리에게 말해주는 것

이 글에서 언급한 프로젝트들 — Stripe Minions, Ramp Inspect, Coinbase Cloudbot, LangChain Open SWE, GitHub Copilot Coding Agent, Claude Code, 그리고 AgentScope Harness — 을 돌아보면, 이들은 언어, 생태계, 배포 형태는 다르지만 핵심 아키텍처 결정에서는 매우 일관된 모습을 보인다. 세션당 격리 샌드박스, 결정론적 스레드 ID 라우팅, 미들웨어 가로채기 체인, 에이전트 런타임에서의 메시지 큐 주입, 저장소 수준의 지시 파일, 그리고 출력 계약으로서의 초안 PR.

이 수렴은 서로를 베낀 결과가 아니다. **이는 동일한 문제 집합이 강제한 엔지니어링적 필연이다.**

---

## 마치며

코딩 에이전트 시대의 전반전은 개인 생산성에 관한 것이었다 — 더 똑똑한 모델, 더 정확한 자동완성, 더 매끄러운 로컬 도구들. 후반전은 전장을 엔지니어링으로 옮긴다. "데모 한 번은 돌릴 수 있다"를 "팀 전체를 위한 연중무휴 안정적 서비스"로 바꾸는 방법. Stripe에서 GitHub까지, LangChain에서 AgentScope까지, 모두 서로 다른 출발점에서 시작해 같은 아키텍처에 도달했다. 그 수렴 자체가 최고의 이정표다.

이 글에서 언급한 codingagent 예제는 완전하고 읽기 쉬운 샘플이다. 클론해서 한 번 실행해 본 뒤 소스 코드를 읽어보기를 권한다 — 여기서 논의한 모든 엔지니어링 문제가 실제 코드에 어떻게 매핑되는지 확인할 수 있다.

더 깊이 파고들기: [Harness Architecture](/v2/ko/docs/harness/architecture) · [Workspace](/v2/ko/docs/harness/workspace) · [Sandbox](/v2/ko/docs/harness/sandbox) · [Context Compaction](/v2/ko/docs/harness/compaction) · [Subagent](/v2/ko/docs/harness/subagent) · [Skill](/v2/ko/docs/harness/skill) · [Plan Mode](/v2/ko/docs/harness/plan-mode)
