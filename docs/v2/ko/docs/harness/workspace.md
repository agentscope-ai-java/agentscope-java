---
title: 워크스페이스(Workspace)
description: "에이전트 정의와 진화를 위한 source of truth: 디렉터리 레이아웃, 워크스페이스-vs-API 등가성, 네이티브 멀티테넌트 격리, 파일 시스템 모드, 핵심 콘텐츠 심층 분석"
---

## 설계 철학

워크스페이스는 `HarnessAgent`의 **에이전트 정의와 진화를 위한 source of truth**다. 에이전트가 무엇인지를 정의하는 모든 것, 그리고 에이전트가 시간이 지남에 따라 학습하는 모든 것이 코드 곳곳에 흩어지지 않고, 특정 데이터베이스 테이블에 고정되지도 않은 채, 일반 Markdown / JSON 파일들의 디렉터리로 여기에 존재한다.

네 가지 핵심 아이디어:

**1. 에이전트 정의와 장기 진화 모두를 위한 source of truth.**

에이전트의 *정의* — 에이전트가 누구이며 어떻게 행동하는지 — 는 워크스페이스 안에 완전히 선언될 수 있다.

| 정의할 대상 | 파일 |
|----------------|------|
| 페르소나, 행동 규칙, 시스템 지침 | `AGENTS.md` |
| 도메인 지식 | `knowledge/KNOWLEDGE.md` + 참조 파일 |
| 스킬(재사용 가능한 능력 패키지) | `skills/<skill-name>/SKILL.md` |
| 서브에이전트 선언 | `subagents/<agent-id>.md` |
| 도구 허용 목록 + MCP 서버 | `tools.json` |

> **모든 워크스페이스 설정 파일은 선택 사항이다.** 모든 파일에는 완전히 동등한 API 대응물이 있다: 동일한 설정을 빌더 메서드(`.systemPrompt(...)`, `.skill(SkillDeclaration...)`, `.subagent(SubagentDeclaration...)`, `.toolsConfig(...)` 등)를 통해서도 전달할 수 있다. 워크스페이스와 API는 항상 대등하다 — 어느 쪽을 사용할지는 전적으로 당신의 선택이다.
>
> **그렇다면 왜 워크스페이스인가?** 정의를 (코드가 아닌) 파일로 표현하는 것이야말로 하나의 에이전트를 네이티브 멀티테넌트로 만들어주기 때문이다. *동일한* 에이전트 로직이 사용자별로 *다른* 페르소나, 지식 베이스, 스킬 세트를 가질 수 있으며, 이는 그저 사용자별 오버라이드 디렉터리를 떨어뜨려 놓는 것만으로 가능하다 — 코드 분기도, 별도의 배포도 필요 없다. 아래 [사용자별로 맞춤화된 하나의 에이전트 로직](#사용자별로-맞춤화된-하나의-에이전트-로직)을 참고하라.

에이전트의 *진화* — 세션에 걸쳐 에이전트가 학습하거나 축적하는 모든 것 — 는 명시적인 생명주기 관리 없이도 자동으로 워크스페이스에 저장된다.

- **장기 메모리**(`MEMORY.md` + `memory/`) — 대화에서 추출된 사실이며, 백그라운드 작업에 의해 유지·압축되고, 매 턴마다 주입된다.
- **자가 학습 스킬**(`skills/`) — 에이전트는 성공 패턴으로부터 새로운 스킬을 초안 작성하며, 선택적인 검토 게이트를 거친 후 재사용 가능한 능력이 되고, 이후 백그라운드 curator가 사용되지 않는 것을 노후화/아카이브한다.
- **계획**(`plans/`) — 플랜 모드 중 작성된 계획은 영속화되어 호출 간에도 유지되며, "파악하는 것"과 "실행하는 것"을 분리된 상태로 유지시켜준다.
- **오프로드된 도구 결과**(압축) — 지나치게 큰 도구 출력은 디스크에 기록되고, 컨텍스트에서는 head/tail 미리보기와 `read_file` 포인터로 대체된다. 그래서 에이전트는 프롬프트를 부풀리지 않고도 나중에 이를 다시 읽을 수 있다.
- **세션 로그**(`agents/<agentId>/sessions/`) — 절대 압축되지 않는 전체 대화 로그이며, 언제든 조회 가능하다.

진화 데이터는 기본적으로 수명이 길다: 메모리는 무한정 누적되며, 세션 로그는 추가만 되고 자동으로 삭제되지 않는다. 각 채널이 어떻게 생성되고 유지되는지는 아래 [에이전트는 어떻게 진화하는가](#에이전트는-어떻게-진화하는가)에서 자세히 다룬다.

(휘발성인 호출별 *런타임 컨텍스트* — `AgentState` — 는 이 목록에 **포함되지 않는다**: 이는 진행 중인 대화를 위한 재개 스냅샷이며, `AgentStateStore`에 별도로 영속화되고 절대 워크스페이스에 저장되지 않는다. 아래 아이디어 2의 콜아웃을 참고하라.)

**2. 콘텐츠는 세 가지 생명주기로 나뉘며, 이들은 서로 구분된 채 유지된다.**

| 종류 | 작성 주체 | 읽는 주체 | 예시 |
|------|------------|---------|----------|
| **정적 자산**(엔지니어가 편집) | 당신 / 당신의 팀 | 프레임워크가 매 턴 시스템 프롬프트에 주입하거나, 호출 시점에 필요에 따라 읽음 | `AGENTS.md`, `knowledge/`, `skills/`, `subagents/`, `tools.json` |
| **런타임 파일**(매 호출마다 다시 쓰임) | 프레임워크 / 에이전트 | 프레임워크가 다음 호출에서 복원 | `agents/<agentId>/sessions/`, `agents/<agentId>/tasks/`, `plans/` |
| **장기 메모리**(세션에 걸쳐 누적) | 에이전트 + 백그라운드 작업 | 프레임워크가 시스템 프롬프트에 주입 + 에이전트가 도구로 조회 | `MEMORY.md`, `memory/YYYY-MM-DD.md` |

이들이 하나의 트리 안에 공존하는 것은 순전히 배포 편의를 위해서다(디렉터리 하나를 복사하면 완전한 에이전트를 얻는다). 프레임워크 내부에서는 이들이 서로 다른 읽기/쓰기 경로를 거친다.

> **`AgentState`는 워크스페이스 콘텐츠가 아니다 — 이 둘을 혼동하지 말라.** 에이전트가 대화 중간에 재개하기 위해 필요한 진행 중인 컨텍스트(대화 버퍼, 롤링 요약, 권한/도구/작업/플랜모드 하위 컨텍스트, 그리고 활성 계획 파일과 같은 워크스페이스 산출물을 가리키는 *메타데이터*)는 하나의 `AgentState` 문서로 직렬화되어 별도의 서브시스템인 **`AgentStateStore`**(기본값 `~/.agentscope/state/<agentId>/`, 워크스페이스 트리 바깥에 완전히 존재함)에 저장된다. 이 분리는 의도적이다: 워크스페이스는 영속적인 *파일 산출물*(절대 압축되지 않는 세션 로그, 계획 마크다운, 작업 기록, 메모리)을 담고, `AgentState`는 휘발성인 *런타임 컨텍스트 + 워크스페이스 메타데이터*를 담는다. 두 개의 스토어, 두 개의 생명주기 — [컨텍스트](/v2/ko/docs/building-blocks/context) 참고.

**3. 태생적으로 멀티테넌트다.** 워크스페이스 데이터(메모리, 세션, 작업, 스킬, 샌드박스 상태)는 단 하나의 `IsolationScope`로 버킷이 나뉜다 — 애플리케이션 수준의 분할 코드가 필요 없다. 이 scope가 누가 하나의 버킷을 공유할지를 결정한다.

| `IsolationScope` | 누가 하나의 버킷을 공유하는가 | 일반적인 용도 |
|------------------|----------------------|-------------|
| `SESSION` | 각 `sessionId`가 완전히 격리됨 | 대화별 격리; 일회용 샌드박스 |
| `USER`(기본값) | 동일한 `userId`의 모든 세션 | 한 사용자의 세션들이 장기 메모리/스킬을 공유(`userId`가 없으면 `SESSION`으로 폴백) |
| `AGENT` | 이 에이전트의 모든 사용자 & 세션 | 공유 지식 베이스형 에이전트 |
| `GLOBAL` | 전체 스토어 인스턴스에 대해 하나의 버킷 | 신중하게 사용할 것 — 모든 에이전트/사용자가 같은 슬롯을 두고 경쟁함 |

선택된 scope는 파일 시스템 모드별로 서로 다르게 물리화된다(로컬 디스크의 경로 접두사, 공유 스토어의 KV 네임스페이스, 샌드박스의 샌드박스 상태 슬롯). 전체 시맨틱, 폴백 규칙, 동시성에 관한 사항은 [파일 시스템 — IsolationScope](/v2/ko/docs/harness/filesystem#isolationscope--사용자와-레플리카-간-버킷-분리)를 참고하라.

> `IsolationScope`는 위의 **워크스페이스/파일 시스템** 버킷을 지배한다. `AgentState`는 그 자체의 독립적인 어드레싱 방식을 갖는다: scope와 무관하게 `AgentStateStore` 안에서 항상 `(userId, sessionId)`로 키가 지정된다.

하나의 `HarnessAgent` 인스턴스가 사용자 간 데이터 유출 없이 수천 명의 동시 사용자를 서비스할 수 있다.

**4. 워크스페이스는 파일 시스템으로부터 분리된다.** 동일한 디렉터리 레이아웃이 로컬 디스크, 공유 KV 스토어(Redis / JDBC), 또는 샌드박스 컨테이너 중 한 곳에 위치할 수 있다. 이 분리 덕분에 에이전트 코드를 건드리지 않고도 배포 형태를 전환할 수 있다. 세 가지 모드는 [파일 시스템](/v2/ko/docs/harness/filesystem)을 참고하라.

## 워크스페이스 디렉터리 레이아웃

```
.agentscope/workspace/
├── AGENTS.md                    ← static: persona + behavior rules
├── MEMORY.md                    ← long-term: curated long-term facts
├── tools.json                   ← static: MCP servers + tool allow/deny (optional)
├── memory/                      ← long-term: append-only daily fact log
│   └── YYYY-MM-DD.md
├── knowledge/                   ← static: knowledge entry + reference files
│   ├── KNOWLEDGE.md
│   └── ...
├── skills/                      ← static: one subdir per skill, each with a SKILL.md
│   └── <skill-name>/SKILL.md
├── subagents/                   ← static: subagent specs (filename = agent_id)
│   └── <agent-id>.md
├── plans/                       ← runtime: plan files written in Plan Mode
│   └── PLAN.md
└── agents/<agentId>/            ← runtime: each agent's runtime root
    ├── sessions/                ← runtime: session index + never-compacted log
    │   ├── sessions.json
    │   └── <sessionId>.log.jsonl
    └── tasks/                   ← runtime: subagent background task records
        └── <sessionId>.json
```

> **이 트리는 *논리적* 레이아웃이며, 고정된 디스크 상 경로가 아니다.** `.agentscope/workspace/...`로 그려져 있지만, 이는 오직 기본적인 로컬 배치일 뿐이다. 정확히 동일한 레이아웃이 물리적으로 **로컬 디스크**에, **원격 분산 스토어**(Redis / JDBC / OSS, `RemoteFilesystemSpec`을 통해)에 존재하거나, **샌드박스 컨테이너로 투영**(`SandboxFilesystemSpec`)될 수 있다 — 아래의 상대 경로들은 세 경우 모두에서 동일하며, 오직 백엔드 스토어만 바뀔 뿐 에이전트 코드는 바뀌지 않는다. 백엔드 스토어는 [파일 시스템](/v2/ko/docs/harness/filesystem)으로 선택하라; 이 문서의 모든 내용은 논리적 레이아웃을 기준으로 작성되었다.

**실제로 작성해야 하는 것은 오직 `AGENTS.md`뿐이다**(이를 생략해도 에이전트는 여전히 실행된다 — 다만 페르소나 주입을 잃을 뿐이다). 나머지는 해당하는 기능을 켤 때마다 나타난다.

- 메모리 압축 활성화(`.compaction(...)`) → `memory/` + `MEMORY.md`
- 서브에이전트 spec 배치 → `subagents/`
- 스킬 설치 → `skills/`
- 플랜 모드 활성화 → `plans/`
- 어떤 `call()` 실행이든 → `agents/<agentId>/`

## Builder 설정

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("MyAgent")
    .model(model)
    .workspace(Paths.get(".agentscope/workspace"))   // omit → see resolution order below
    .additionalContextFile("SOUL.md")                // any workspace-relative path, inlined in full
    .additionalContextFile("PREFERENCES.md")
    .maxContextTokens(8000)                          // MEMORY injection budget
    .build();
```

### 워크스페이스 해석 순서

`workspace(...)`를 명시적으로 호출하지 않으면, `build()`는 다음 우선순위(가장 높은 것부터)로
워크스페이스 디렉터리를 해석한다.

| 우선순위 | 소스 | 참고 |
|----------|--------|-------|
| 1 | `workspace(Path)` / `workspace(String)` | 명시적인 builder 값, 다른 모든 것을 오버라이드 |
| 2 | `agentscope.workspace` 시스템 프로퍼티 | `-Dagentscope.workspace=/data/workspace` |
| 3 | `AGENTSCOPE_WORKSPACE` 환경 변수 | `export AGENTSCOPE_WORKSPACE=/data/workspace` |
| 4 | 기본값 | `${user.dir}/.agentscope/workspace` |

시스템 프로퍼티와 환경 변수가 존재하는 주된 이유는 **이미지 패키징 / 컨테이너 배포** 때문이다:
경로를 애플리케이션 코드 밖에 두고, 이미지 빌드 시점이나 컨테이너 시작 시점에 주입할 수 있다. 예를 들어:

```dockerfile
ENV AGENTSCOPE_WORKSPACE=/data/agent-workspace
```

```yaml
# k8s / docker-compose
env:
  - name: AGENTSCOPE_WORKSPACE
    value: /data/agent-workspace
```

> 빈 값(예: `"   "`)은 설정되지 않은 것으로 취급되어 다음 단계로 넘어간다.

최소 `AGENTS.md` 스켈레톤:

```markdown
# MyAgent

You are an XX assistant. Follow these behavior guidelines.

## Behavior
- ...
- ...
```

옵트아웃 스위치(프로덕션에서는 드물게 쓰이며, 디버깅이나 직접 관리에 유용):

| 메서드 | 비활성화되는 것 |
|--------|------------------|
| `disableWorkspaceContext()` | 시스템 프롬프트 주입(`AGENTS.md` / `MEMORY.md` / `knowledge/`) |
| `disableMemoryHooks()` | 메모리 플러시 + 백그라운드 유지보수; 시스템 프롬프트에서 "자동으로 추출됨" Persistence 줄도 제거. `disableMemoryTools()`와 함께 사용하면 `<memory_context>`(`MEMORY.md`) 주입도 생략됨 |
| `disableMemoryTools()` | `memory_search` / `memory_get` / `memory_save` / `session_search` 도구; 시스템 프롬프트에서 Memory Recall과 도구 기반 Persistence 안내도 생략됨 |
| `disableSubagents()` | 서브에이전트 서브시스템 전체 |
| `disableDynamicSkills()` | 턴마다의 스킬 재병합; 빌드 시점의 일회성 병합으로 폴백 |
| `disableToolsConfig()` | `tools.json` 읽기 |
| `disableSessionPersistence()` | AgentState 자동 영속화 |

## 워크스페이스 콘텐츠가 로드되는 방식

워크스페이스는 논리적 레이아웃이므로(위의 콜아웃 참고), "로딩"은 결코 평범한 로컬 디렉터리를 가정하지 않는다 — 모든 읽기는 설정된 `AbstractFilesystem`을 거치므로, 파일이 로컬 디스크에 있든, 원격 스토어에 있든, 샌드박스 안에 있든 동일한 로직이 동작한다. 아래의 [2계층 읽기](#2단계-읽기파일-시스템-우선--로컬-폴백)는 이 백엔드 독립성을 구체화한 것이며; [파일 시스템](/v2/ko/docs/harness/filesystem)은 각 모드가 물리적으로 경로를 어떻게 해석하는지를 다룬다.

### 턴마다의 시스템 프롬프트 조립

매 추론 스텝 전에, `WorkspaceContextMiddleware`(`io.agentscope.harness.agent.middleware`)는 다음 섹션들을 조립하여 빌더에 설정한 `sysPrompt`에 **추가**해서 최종 시스템 메시지를 구성한다.

| 섹션 | 소스 | 예산 제한? |
|---------|--------|---------|
| `## Session Context` | 템플릿(오늘 날짜, OS, 워크스페이스 절대 경로, 임시 디렉터리, 현재 `sessionId`) | 없음 |
| `## Domain Knowledge` / `## Memory Recall` / `## Memory Persistence` 안내 | 내장 템플릿(모델에게 메모리 사용법 + 지식 탐색법을 가르침). `disableMemoryTools()` / `disableMemoryHooks()`가 설정되면 메모리 섹션은 생략/축소됨 | 없음 |
| `## Workspace` 섹션 | 템플릿, **파일 시스템 모드별로 분기**(아래 참고) — 모델이 로컬 / 샌드박스 / 원격 스토어 중 어디서 실행 중인지 알려줌 | 없음 |
| `## Workspace Files (Injected)` 알림 | 프레임워크가 다음 파일들을 워크스페이스에서 자동으로 불러와 `<loaded_context>` XML 블록으로 만듦 | 아래 참고 |
| `<agents_context>` | 전체 `AGENTS.md` | 무제한 |
| `<memory_context>` | `MEMORY.md`, 남은 예산을 초과하면 문자 수 기준으로 절단되고 "오래된 항목은 memory_search를 사용하라"는 안내가 붙음(도구가 비활성화되어 있으면 단순 절단 안내; 메모리 도구와 훅이 모두 비활성화되어 있으면 완전히 생략됨) | `maxContextTokens`, 기본값 8000 |
| `<domain_knowledge_context>` | 전체 `knowledge/KNOWLEDGE.md` + `knowledge/` 아래 모든 파일 목록 | 무제한(카탈로그로는 파일명만) |
| `<x_md>` / `<y_md>` | `additionalContextFile("X.md")`로 추가한 모든 것 | 무제한 |

핵심 포인트:

- **매 턴마다 다시 조립된다.** `AGENTS.md`나 `MEMORY.md`를 수정하면 다음 `call()`이 그 변경을 즉시 반영한다 — 재시작도, 재빌드도 필요 없다.
- **`MEMORY.md`는 주입 전에 토큰 추정을 거친다.** 오버플로가 발생하면 문자 수 기준으로 절단되며, 모델을 `memory_search` 쪽으로 유도하는 안내 문구가 뒤에 붙는다.
- **`knowledge/`는 디렉터리 인덱스 + 진입 파일이다.** 전체 트리가 프롬프트에 들어가는 일은 없다 — 오직 `KNOWLEDGE.md`와 경로 목록만 들어가며, 에이전트는 필요한 것을 `read_file`로 읽는다.

### 2단계 읽기(파일 시스템 우선 + 로컬 폴백)

프롬프트에 주입되는 모든 "파일"(`AGENTS.md` / `MEMORY.md` / `knowledge/KNOWLEDGE.md` / `additionalContextFile`)에 대해, `WorkspaceManager.readWithOverride()`는 **2단계 읽기**를 수행한다.

```
1. 설정된 AbstractFilesystem에게 묻는다: 이 상대 경로를 가지고 있는가?
   ├─ 있음 → 그 내용을 반환한다("오버라이드" 계층)
   └─ 없음 → 2단계로 넘어간다
2. workspace.resolve(relativePath)의 로컬 디스크를 읽는다
```

쓰기는 항상 1계층(파일 시스템 스토어)을 거치며, 절대 로컬 디스크에 직접 쓰지 않는다.

이 패턴은 **공유 스토어 모드**에서 진가를 발휘한다: 첫 번째 레플리카는 팀의 git으로 동기화된 `AGENTS.md` 템플릿을 로컬 디스크에서 바로 사용할 수 있어 즉시 동작한다; 이후 어떤 오버라이드든(예: 관리 콘솔 편집기로부터) 공유 KV에 저장되고, 모든 레플리카의 다음 `call()`이 최신 버전을 읽는다. 템플릿은 폴백이고, 원격 오버라이드가 진실이다.

### 여러 사용자가 하나의 워크스페이스를 공유할 때의 오버라이드 우선순위

`RuntimeContext.userId`는 멀티유저 키다 — 이는 하나의 에이전트 인스턴스가 서로 간섭 없이 여러 사용자를 서비스할 수 있게 해준다.

**런타임 데이터**(세션 / 작업 / 메모리)의 경우, 프레임워크는 설정된 `NamespaceFactory`를 통해 경로에 접두사를 붙인다(로컬 모드 → 경로 접두사, 원격 모드 → KV 네임스페이스, 샌드박스 모드 → 상태 슬롯). 자세한 내용은 다음 섹션 "런타임 데이터와 메모리는 어떻게 저장되는가"를 참고하라.

**정적 자산**(특히 `skills/`와 `subagents/`)의 경우, 사용자별 디렉터리가 워크스페이스 공유 버전을 **오버라이드**한다.

```
workspace/
├── skills/code-reviewer/SKILL.md     ← 공유(모두에게 보임)
├── subagents/researcher.md           ← 공유
└── alice/
    ├── skills/
    │   └── code-reviewer/
    │       └── SKILL.md              ← Alice에게만 보임; 공유 버전을 오버라이드
    └── subagents/
        └── researcher.md             ← Alice에게만 보임
```

`RuntimeContext.userId="alice"`로 호출되면, 프레임워크는 먼저 `alice/skills/code-reviewer/`를 찾고 없으면 `skills/code-reviewer/`로 폴백한다. 하위 계층에만 있는 고유한 스킬은 계속 보인다; 이름이 같을 때만 상위 계층에 가려진다. 전체 우선순위 표는 [스킬 — 충돌 해결](/v2/ko/docs/harness/skill#충돌-해결)에 있다.

#### 사용자별로 맞춤화된 하나의 에이전트 로직

이 오버라이드 메커니즘 덕분에, 코드를 포크하거나 별도의 배포를 띄우지 않고도 **하나의 `HarnessAgent` 인스턴스가 테넌트마다 다른 에이전트처럼 동작**할 수 있다. 하나의 바이너리, 하나의 에이전트 정의를 배포하고; 각 사용자는 그 위에 자신만의 조각을 얹는다.

| 사용자별 계층 | 커스터마이즈하는 것 | 해석 방식 |
|----------------|--------------------|------------|
| `<userId>/AGENTS.md`(오버라이드를 통해) | 해당 사용자를 위한 페르소나/행동 | 2계층 읽기의 상위 계층(공유 `AGENTS.md`가 폴백) |
| `<userId>/knowledge/` | 해당 사용자가 볼 수 있는 도메인 지식 | 사용자별 디렉터리, 공유 `knowledge/`가 베이스 |
| `<userId>/skills/` | 해당 사용자만 해제하는 능력 | 동일한 이름의 공유 스킬을 오버라이드; 고유한 것들은 쌓임 |
| `<userId>/subagents/` | 해당 사용자만 생성할 수 있는 서브에이전트 | 동일한 이름의 공유 spec을 오버라이드 |
| 런타임 데이터(메모리 / 세션 / 작업) | 해당 사용자가 축적한 진화 | `userId`별로 네임스페이스가 분리됨(경로 접두사 / KV 네임스페이스 / 샌드박스 슬롯) |

그 결과는 **한 번에 두 계층의 멀티테넌시**다: *정의*는 사용자마다 다르고(오버라이드 디렉터리를 통해), *진화*는 사용자마다 격리된다(네임스페이싱을 통해). 공유 베이스는 모두에게 공통으로 유지되며, 각 사용자의 커스터마이제이션과 학습된 상태는 같은 에이전트 프로세스 안에서도 테넌트 간에 절대 유출되지 않는다. 이것이 바로 [선택적 설정에 관한 콜아웃](#설계-철학)이 가리키는 파일 기반의 이점이다: 정의가 데이터이기 때문에, 사용자별 커스터마이제이션은 또 다른 코드 경로가 아니라 그저 또 다른 파일일 뿐이다.

### 각 파일 시스템 모드에서의 로딩 동작

워크스페이스는 논리적 레이아웃이다; 물리적 배치는 [파일 시스템](/v2/ko/docs/harness/filesystem)에 달려 있다. 같은 디렉터리라도 모드에 따라 로딩 방식이 달라진다 — 아래에서 설명한다.

**모드 1 · 공유 스토어(`RemoteFilesystemSpec`) — 템플릿 + 원격 오버라이드**

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("store")
    .model(model)
    .workspace(workspace)
    .distributedStore(store)
    .filesystem(new RemoteFilesystemSpec()
        .isolationScope(IsolationScope.USER))      // namespace per userId
    .build();
```

- **로딩 방식**: 매 턴마다, `AGENTS.md` / `MEMORY.md` / `tools.json`은 원격 KV를 상위 계층으로, 워크스페이스 템플릿을 읽기 전용 하위 계층으로 하는 오버레이를 통해 제공된다. 로컬의 `<workspace>/AGENTS.md`는 **읽기 전용 시드**다 — 최초 부팅 시나 레플리카 간 동기화에 사용된다; 원격 KV에 같은 키로 사용자별 사본이 있으면 원격이 우선한다.
- **라우팅**: `memory/` / `skills/` / `subagents/` / `knowledge/` / `agents/<id>/sessions/` / `agents/<id>/tasks/`는 `IsolationScope`별로 네임스페이스가 나뉜다(기본값 USER → `userId`당 하나의 네임스페이스; [파일 시스템 — IsolationScope](/v2/ko/docs/harness/filesystem#isolationscope--사용자와-레플리카-간-버킷-분리) 참고).
- **모범 사례**: 팀이 합의한 `AGENTS.md` / `knowledge/` / 공유 `skills/`를 모든 레플리카의 로컬 디스크에 템플릿으로서 git-sync하라; 런타임 산출물(`MEMORY.md`, `memory/`, `agents/<id>/...`)은 KV에 쌓이도록 두라.

**모드 2 · 샌드박스(`DockerFilesystemSpec` / K8s / E2B / AgentRun) — 투영 + 하이드레이션**

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("sandbox")
    .model(model)
    .workspace(workspace)
    .filesystem(new DockerFilesystemSpec()
        .image("ubuntu:24.04")
        .isolationScope(IsolationScope.SESSION))
    .build();
```

- **로딩 방식**: 샌드박스가 시작될 때, 프레임워크는 워크스페이스의 "정적 자산"(`AGENTS.md`, `skills/`, `subagents/`, `knowledge/`, 그리고 그 외 투영 루트)을 tar로 묶어 컨테이너 내부의 `/workspace`로 하이드레이션한다. `AGENTS.md` 등은 여전히 2계층 읽기를 따른다(샌드박스 우선, 호스트 템플릿 폴백).
- **중복 제거 & 증분**: 투영은 콘텐츠 해시로 비교된다; 변경 없음 → 건너뜀; 변경된 파일은 SHA-256으로 증분 재작성된다.
- **런타임 데이터**: `MEMORY.md`, `memory/`, `agents/<id>/...`는 모두 샌드박스 안에 존재한다; 샌드박스 스냅샷이 이를 보존하므로 — 같은 `sessionId`로 들어오는 다음 `call()`은 `node_modules`, `pip install` 결과, 그 외 모든 것을 복원한다.
- **모범 사례**: 코드 실행 / 셸을 호스트 밖에 두라. 호스트는 워크스페이스 "시드"(팀 git으로 동기화된 페르소나 + 공유 스킬 + 지식)만 담는다. 이는 프로덕션에서 신뢰할 수 없는 코드를 실행할 때의 기본 모드다.

**모드 3 · 로컬 + shell(기본값 `LocalFilesystemSpec` 또는 `filesystem(...)` 없음) — 직접 읽기/쓰기**

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("local")
    .model(model)
    .workspace(workspace)
    // omit .filesystem(...) = local + shell
    .build();
```

- **로딩 방식**: 모든 파일이 `<workspace>/`에서 직접 읽힌다; 오버레이 없음. `<userId>/skills/`와 같은 사용자별 오버라이드는 단순한 디렉터리 접두사 전환이다.
- **경로 안전성**: 기본 `ROOTED` 모드 — 절대 경로는 `workspace`와 `project`(셸 `cwd`) 루트 아래에서만 허용되며; `..` 트래버설은 경로 정책에 의해 거부된다.
- **모범 사례**: 단일 프로세스 / 로컬 개발 / 단위 테스트 / 신뢰할 수 있는 환경. 여기서 프로덕션 상황의 신뢰할 수 없는 코드를 **실행하지 말라** — `execute`는 호스트 `sh -c`다.

## 런타임 데이터와 메모리는 어떻게 저장되는가

프레임워크는 두 개의 데이터 평면을 자동으로 기록하며 — 이 둘은 **서로 다른 두 곳**에 존재한다. 이 둘을 구분해서 기억하라.

| 데이터 평면 | 정체 | 위치 |
|------------|-----------|----------------|
| **`AgentState`** | 휘발성 런타임 컨텍스트: 채팅 버퍼, 압축 요약, 권한/도구/작업/플랜모드 컨텍스트, 그리고 워크스페이스 산출물을 가리키는 메타데이터 | **`AgentStateStore`** — 별도의 서브시스템이며, 워크스페이스가 **아니다**(기본값 `~/.agentscope/state/<agentId>/`) |
| **워크스페이스 런타임/장기 파일** | 영속적인 산출물: 세션 로그, 작업 기록, `MEMORY.md` + `memory/` | 워크스페이스 트리 내부이며, 물리적 위치는 파일 시스템 모드를 따른다 |

이 둘은 직접 편집하는 대상이 아니다. 이 섹션의 나머지 부분은 두 평면을 차례로 살펴본다.

### 에이전트 상태 — 워크스페이스가 아니라 별도의 스토어

`AgentState`는 `(userId, sessionId)`별 런타임 컨텍스트이며, 의도적으로 **워크스페이스 트리 밖에** 유지된다. `call()`이 완료되면, 이는 JSON으로 직렬화되어 설정된 [`AgentStateStore`](/v2/ko/integration/session/index)를 통해 영속화되며, 해당 호출의 `(userId, sessionId)`로 주소가 지정된다. 같은 `(userId, sessionId)`로 다음 `call()`이 오면 이를 다시 불러온다.

기본적으로 `HarnessAgent`는 워크스페이스 **바깥**의 `~/.agentscope/state/<agentId>/`를 루트로 하는 `JsonFileAgentStateStore`를 사용한다(기준 경로는 `agentscope.state.home` 시스템 프로퍼티로 오버라이드 가능), 그래서 런타임 상태는 워크스페이스 데이터와 분리된 채로 유지된다. `.stateStore(...)`를 통해 다른 스토어를 설정할 수 있다.

### 세션 로그(이것은 *실제* 워크스페이스 파일이다)

`AgentState`와는 구분되게, 워크스페이스는 `agents/<agentId>/sessions/` 아래에 **대화 로그**를 담고 있다.

- **`sessions.json`** — 해당 에이전트의 세션 인덱스(키 = sessionId, 값 = 요약 + updatedAt).
- **`<sessionId>.log.jsonl`** — **절대 압축되지 않는** 원본 대화 로그이며, 추가만 된다. `session_search` / `session_history`가 이를 조회한다.

> 기본 `JsonFileAgentStateStore`는 단일 머신 전용이다. 멀티 레플리카 프로덕션에서는 분산 스토어(`RedisAgentStateStore` / `MysqlAgentStateStore` / …)로 전환해야 한다. `filesystem(SandboxFilesystemSpec)` 또는 `filesystem(RemoteFilesystemSpec)`을 설정했는데 분산 상태 스토어로 교체하지 않았다면, `build()`는 `IllegalStateException`을 던진다 — 런타임 상태를 단일 장애점으로 만들지 말라는 강제 알림이다.

전체 세부 사항(복구 흐름, 크로스 노드 지속, `(userId, sessionId)` 어드레싱)은 [컨텍스트](/v2/ko/docs/building-blocks/context)에 있다.

### 메모리(장기)

두 계층:

```
workspace/
├── MEMORY.md                  ← curated long-term memory, injected each turn
└── memory/
    └── YYYY-MM-DD.md          ← append-only daily fact log (no dedup)
```

쓰기 경로:

- 압축 전에, `MemoryFlushMiddleware`는 대화 접두부에서 새로운 사실을 추출하여 `memory/YYYY-MM-DD.md`에 추가한다.
- 스로틀링된 백그라운드 작업이 주기적으로 `memory/`를 병합/중복 제거하고 `MEMORY.md`를 다시 작성한다.
- `MEMORY.md`는 매 턴마다(예산 제한하에) 시스템 프롬프트에 주입된다.

읽기 경로:

- 프레임워크 자체가 `MEMORY.md`를 읽는다(2계층; 파일 시스템 우선).
- 에이전트는 더 오래된 항목을 위해 능동적으로 `memory_search` / `memory_get`을 호출할 수 있다. [메모리](/v2/ko/docs/harness/memory) 참고.

### 네임스페이스 격리가 물리적 위치로 매핑되는 방식

`WorkspaceManager.resolveRuntimeDataPath()`는 `NamespaceFactory`에게 현재 `RuntimeContext`가 어떤 네임스페이스로 매핑되는지 묻는다. 이 네임스페이스는 파일 시스템 모드별로 다르게 물리화된다.

| 모드 | 런타임 데이터의 물리적 위치 | 멀티유저 격리 메커니즘 |
|------|----------------------------------|-------------------------------|
| 로컬 + shell | `<workspace>/<userId>/agents/<agentId>/...` | 경로 접두사 |
| 공유 스토어(KV) | KV 키 접두사, 예: `namespace=alice/memory/...` | KV 네임스페이스 |
| 샌드박스 | 샌드박스 상태 슬롯 키(`IsolationScope.USER`와 함께) | 샌드박스 인스턴스 격리 |

`userId`가 없으면 싱글 테넌트 기본값이 적용되어 모두가 하나의 루트를 공유한다.

> **정적 자산** vs **런타임 데이터**: `AGENTS.md`, `tools.json`, `knowledge/` 등은 userId별로 자동 분할되지 **않는다** — 이들은 사용자 간에 공유되며, 구분하는 유일한 방법은 사용자별 오버라이드 디렉터리(`<userId>/skills/...`, `<userId>/subagents/...`)를 추가하는 것이다. `userId`를 따라가는 것은 런타임 데이터(세션, 작업, 메모리)다.

## 에이전트는 어떻게 진화하는가

정적 정의를 넘어서, 워크스페이스는 에이전트의 *축적된 경험*이 쌓이는 곳이다. 다섯 가지 채널이 자동으로 누적된다 — 해당하는 기능을 켜면 데이터가 워크스페이스에 쌓이기 시작하며, 다른 모든 것과 똑같이 테넌트별로 격리된다. 각각에는 전용 심층 페이지가 있다; 이 표는 그 색인이다.

| 채널 | 위치 | 켜는 법 | 누적되는 방식 | 심층 분석 |
|---------|----------------|------------|----------------|-----------|
| **장기 메모리** | `MEMORY.md` + `memory/YYYY-MM-DD.md` | `.compaction(...)` | `MemoryFlushMiddleware`가 압축 전 대화 접두부에서 사실을 추출; 스로틀링된 백그라운드 작업이 이를 병합 + 중복 제거하여 `MEMORY.md`로 만들고, 매 턴 다시 주입됨 | [메모리](/v2/ko/docs/harness/memory) |
| **자가 학습 스킬** | `skills/`, `skills/_drafts/`, `skills/.archive/` | `.enableSkillManageTool(...)` | 에이전트가 동작한 패턴으로부터 스킬 초안을 작성하기 위해 `propose_skill`을 호출 → 선택적 승격 게이트가 이를 승인 → 백그라운드 curator가 사용되지 않는 스킬을 stale(30일)로 표시하고 아카이브(90일)함 | [스킬 — 자가 학습 루프](/v2/ko/docs/harness/skill#자가-학습-루프선택-사항) |
| **계획** | `plans/PLAN.md` | `.enablePlanMode()` | 읽기 전용 계획 단계가 `plan_write`를 통해 계획을 작성함; 호출 간에도 영속화되며 실행 단계를 이끌어, 의도를 행동으로부터 분리함 | [플랜 모드](/v2/ko/docs/harness/plan-mode) |
| **오프로드된 도구 결과** | 워크스페이스 아래의 eviction 디렉터리 | `.toolResultEviction(...)` | 단일 도구 결과가 임계값(기본 80K자)을 초과하면, 전체 출력이 디스크에 기록되고 컨텍스트 내 메시지는 head/tail 미리보기 + `read_file` 포인터로 대체됨 | [압축](/v2/ko/docs/harness/compaction) |
| **세션 로그** | `agents/<agentId>/sessions/`(워크스페이스) | 기본적으로 켜짐 | 매 `call()`이 절대 압축되지 않는 JSONL 로그에 추가됨; `session_search` / `session_history`가 이를 조회함 | [컨텍스트](/v2/ko/docs/building-blocks/context) |

통합된 아이디어: **에이전트는 당신이 별도의 저장소를 연결하지 않아도 실행 간에 개선된다.** 메모리, 스킬, 계획, 세션 로그, 오프로드된 결과 모두 워크스페이스 안의 파일일 뿐이다 — 이들은 이 페이지의 다른 모든 것과 동일한 테넌트별 격리, 동일한 2계층 읽기, 동일한 파일 시스템 모드 이식성을 갖는다. (휘발성인 `AgentState` 런타임 컨텍스트는 유일한 예외다 — 이는 워크스페이스가 아니라 별도의 `AgentStateStore`에 존재한다; [런타임 데이터와 메모리는 어떻게 저장되는가](#런타임-데이터와-메모리는-어떻게-저장되는가) 참고.)

## 핵심 디렉터리 심층 분석

### `skills/`

스킬은 패키징된 능력이다 — `SKILL.md`(설명 + 에이전트를 위한 지침)를 담은 디렉터리이며, 참고 문서와 스크립트가 선택적으로 함께 들어갈 수 있다.

```
skills/code-reviewer/
├── SKILL.md               ← YAML frontmatter (name + description) + instructions
├── references/style-guide.md   ← optional, agent reads on demand
└── scripts/run-checks.sh       ← optional, agent invokes via execute_shell_command
```

네 개의 등록 계층이 있다(낮음 → 높은 우선순위):

1. `projectGlobalSkillsDir(Path)` — 프로젝트 전역, 예: `~/.agentscope/skills/`
2. `skillRepository(...)` — 마켓플레이스 스토어(Git / Nacos / MySQL / classpath)
3. `workspace/skills/` — 워크스페이스 공유
4. `<userId>/skills/` — 사용자별(위 모든 것을 오버라이드)

하위 계층의 고유한 스킬은 계속 보인다; 이름이 같은 스킬은 상위 계층에 가려진다. 매 턴마다, `DynamicSkillMiddleware`는 재병합하여 `<available_skills>` 블록(이름 + description만)을 시스템 프롬프트에 렌더링한다. 에이전트는 관련이 있을 때 전체 세부 내용을 가져오기 위해 `load_skill_through_path`를 호출한다. 전체 메커니즘은 [스킬](/v2/ko/docs/harness/skill)에 있다.

### `subagents/`

각 `<agent-id>.md`는 하나의 서브에이전트 선언이다(파일 이름 = `agent_id`). YAML 프런트매터는 정체성, 모델, 도구 허용 목록, 워크스페이스 전략을 설명하며; 본문은 서브에이전트의 시스템 프롬프트다.

```markdown
---
description: Code review specialist. Use when the user needs a PR review, style feedback, or static checks.
workspace:
  mode: isolated         # isolated (default) | shared
model: qwen3-max         # optional; defaults to inheriting the parent
tools: [read_file, grep_files]   # optional; inherited-tool allowlist
---

You are a code review subagent…
```

로딩: `AgentSpecLoader`는 빌드 시점에 `workspace/subagents/*.md`를 **비재귀적으로** 스캔하며, `.subagent(SubagentDeclaration...)`을 통해 프로그래밍적으로 등록한 선언들과 병합한다. 메인 에이전트는 `agent_spawn agent_id="reviewer" task="..."`를 통해 이들을 호출한다.
전체 세부 사항(동기 vs 백그라운드, 원격 서브에이전트, 스트림 전달, 작업 저장소)은 [서브에이전트](/v2/ko/docs/harness/subagent)에 있다.

### `tools.json`

워크스페이스 루트에 위치한 JSON 파일이며, `build()` 중 한 번만 읽힌다.

```jsonc
{
  // allowlist: when non-empty, only listed tools survive
  "allow": ["read_file", "grep_files", "execute"],
  // denylist: listed tools are always removed (wins over allow)
  "deny":  ["write_file"],
  // MCP servers, keyed by name
  "mcpServers": {
    "amap": {
      "transport": "streamableHttp",
      "url": "https://mcp.amap.com/mcp?key=${AMAP_API_KEY}"
    },
    "local-py": {
      "transport": "stdio",
      "command": "python",
      "args": ["mcp_servers/my_server.py"],
      "env": {"PYTHONUNBUFFERED": "1"}
    }
  }
}
```

동작에 관한 참고 사항:

- **MCP 서버는 빌드 시점에 한 번 툴킷에 등록된다**; 에이전트는 이들이 노출하는 도구를 보게 된다.
- **`allow` / `deny`는 모든 도구가 등록된 뒤에 적용된다** — Harness 내장 도구(`read_file` / `memory_search` / `agent_spawn` / …) 포함. **`allow`로 화이트리스트를 만들 때는 유지하고 싶은 내장 도구도 함께 나열하라**, 그렇지 않으면 나머지와 함께 걸러진다.
- `${ENV_VAR}` 문법은 환경 변수로 치환된다; 누락된 변수는 경고를 남기고 빈 문자열로 치환된다.
- 파일을 원하지 않는가? `builder.toolsConfig(ToolsConfig.builder()...)`를 직접 전달하거나, `disableToolsConfig()`로 읽기를 완전히 비활성화하라.
- 공유 스토어 모드에서는, `tools.json`도 위에서 설명한 "원격 상위, 로컬 템플릿 하위" 오버레이를 따른다.

### `plans/`

플랜 모드에서 작성된 계획 파일이 여기에 위치한다. 기본값은 `plans/PLAN.md`이며, `.planFileDirectory("design-docs")`로 변경할 수 있다.

```
plans/
└── PLAN.md           ← current plan written by plan_write
```

참고: `PlanModeContext`(계획 단계가 활성화되어 있는지, 현재 계획 파일 경로)는 `AgentState`에 위치한다 — 이는 **런타임 상태**이며 `AgentStateStore`(기본값 `~/.agentscope/state/<agentId>/`, 워크스페이스 바깥)를 통해 영속화된다. `plans/` 아래의 파일은 오직 마크다운 콘텐츠 그 자체일 뿐이다. [플랜 모드](/v2/ko/docs/harness/plan-mode) 참고.

### `agents/<agentId>/`

이것은 **런타임 루트**이며, 프레임워크가 작성하고 손으로 직접 편집하는 일은 거의 없다.

```
agents/<agentId>/
├── sessions/
│   ├── sessions.json          ← session index for this agent
│   └── <sessionId>.log.jsonl  ← never-compacted raw conversation log (append-only)
└── tasks/
    └── <sessionId>.json       ← subagent background task records (taskId → TaskRecord)
```

> 직렬화된 `AgentState`(`agent_state`)는 기본적으로 워크스페이스에 위치하지 **않는다** — 이는 설정된 `AgentStateStore`(기본값 `~/.agentscope/state/<agentId>/`)에 있다. 위의 대화 로그와 작업 기록만이 워크스페이스에 남는다.

크로스 노드 복구 / 멀티 레플리카 배포에서는 이 데이터가 공유되어야 한다(`RedisAgentStateStore` + `RemoteFilesystemSpec`, 또는 분산 상태를 가진 샌드박스 중 하나). [컨텍스트](/v2/ko/docs/building-blocks/context)와 [파일 시스템](/v2/ko/docs/harness/filesystem)을 참고하라.

### `knowledge/`

```
knowledge/
├── KNOWLEDGE.md         ← entry / overview, injected in full into the system prompt
├── api-reference.md
├── domain-terms.md
└── ...
```

로드 시점에:

- 전체 `KNOWLEDGE.md`가 `<domain_knowledge_context>`에 들어간다.
- 같은 트리 아래의 다른 파일들(어떤 깊이든)은 오직 **경로 목록**만 프롬프트에 기여한다; 에이전트는 필요할 때 `read_file` / `grep_files` / `glob_files`로 이를 읽는다.

"세부 사항은 디스크에, 인덱스는 프롬프트에"라는 이 패턴은 지식 베이스가 커도 토큰 예산을 일정 범위로 유지시켜준다.

## 워크스페이스에 쓸 때의 안전 규칙

`additionalContextFile`, `writeUtf8WorkspaceRelative`, `memory_get` 등은 **워크스페이스 상대 경로**를 받아들인다. 프레임워크는 기본적인 경로 트래버설 검증을 수행한다(`../../etc/passwd`와 같은 탈출 시도를 거부).

파일을 써야 할 때는, **`java.nio.Files`가 아니라 `HarnessAgent#getWorkspaceManager()`를 거쳐야 한다** — 샌드박스나 공유 스토어 모드에서는 후자가 잘못된 위치에 쓰기 때문이다(샌드박스/KV 안이 아니라 호스트 디스크에 남게 된다). 예외: 빌더 시점의 부트스트랩 스크립트(예: `AGENTS.md`를 시드하는 `initWorkspaceIfAbsent`) — 이 시점에는 아직 런타임 컨텍스트가 없으며, 의도가 로컬 템플릿을 쓰는 것이므로 `java.nio.Files`가 옳다.

## 관련 문서

- [아키텍처](/v2/ko/docs/harness/architecture) — 시스템 프롬프트가 어떻게 조립되고 각 기능이 어떻게 협력하는지
- [파일 시스템](/v2/ko/docs/harness/filesystem) — 워크스페이스가 물리적으로 어디에 위치하는지(로컬 / 샌드박스 / 공유 스토어), `IsolationScope`, 멀티유저 격리
- [컨텍스트](/v2/ko/docs/building-blocks/context) — `AgentState`와 `AgentStateStore` 영속화, 크로스 노드 복구
- [메모리](/v2/ko/docs/harness/memory) — `MEMORY.md` / `memory/`가 어떻게 생성되고 유지되는지, 압축, 에빅션
- [스킬](/v2/ko/docs/harness/skill) — 4단계 합성, 자가 학습 루프, `<available_skills>` 블록
- [서브에이전트](/v2/ko/docs/harness/subagent) — `subagents/` 선언, 동기 vs 백그라운드, 스트림 전달
- [플랜 모드](/v2/ko/docs/harness/plan-mode) — `plans/` 파일, 읽기 전용 단계, HITL 종료
