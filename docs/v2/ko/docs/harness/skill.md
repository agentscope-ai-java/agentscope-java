---
title: 스킬(Skill)
description: 4단계 스킬 합성, 스킬 마켓플레이스, 자가 학습 루프
---

스킬은 패키징된 하나의 능력이다. `SKILL.md`(목적 + 에이전트가 읽을 지침)를 담은 디렉터리이며, 참고 문서, 스크립트, 샘플이 선택적으로 포함될 수 있다. 에이전트에게 건네주면 관련이 있을 때 알아서 사용한다.

Harness는 두 곳에서 스킬을 설치할 수 있게 해준다.

- **스킬 마켓플레이스** — Git 저장소, Nacos, MySQL, classpath, 커스텀 스토어
- **워크스페이스** — `workspace/skills/`는 모두가 공유하며; `<userId>/skills/`는 사용자별로 격리된다

두 소스는 동시에 활성화되며 — 둘 중 하나만 선택할 필요가 없다. 그 위에 **자가 학습 루프**를 활성화할 수 있다: 에이전트가 스킬 초안을 작성 → 검토 게이트 → 백그라운드 curator가 정리.

스킬 디렉터리의 모습은 다음과 같다.

```
code-reviewer/
├── SKILL.md           # 필수 — YAML 프런트매터(name + description) + 에이전트를 위한 지침
├── references/        # 선택 — 에이전트가 필요에 따라 읽는 상세 문서
│   └── style-guide.md
└── scripts/           # 선택 — 에이전트가 셸로 실행할 수 있는 실행 스크립트
    └── run-checks.sh
```

SKILL.md 형식:

```markdown
---
name: code-reviewer
description: Use when the user asks for code review, style feedback, or PR audits.
---

# Code Reviewer

Steps:
1. Read `references/style-guide.md` for project conventions.
2. Run `scripts/run-checks.sh <target-path>` and summarize the output.
```

## 빠른 예제

팀의 스킬 저장소를 연결하면 에이전트가 즉시 사용할 수 있다.

```java
HarnessAgent agent = HarnessAgent.builder()
        .name("assistant")
        .model(model)
        .workspace(workspace)
        .skillRepository(new GitSkillRepository("https://github.com/your-org/team-skills.git"))
        .build();
```

추론 중에 에이전트는 저장소의 스킬을 확인하고, 필요한 것을 골라 `load_skill_through_path`를 호출한다.

## 마켓플레이스 스토어

`skillRepository(...)`는 통합된 진입점이다 — 어떤 스토어든 전달할 수 있다.

### Git

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-skill-git-repository</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

```java
.skillRepository(new GitSkillRepository("https://github.com/your-org/team-skills.git"))
```

기본적으로 매 읽기마다 가벼운 원격 확인을 수행하며, HEAD가 변경됐을 때만 pull한다. 저장소에 `skills/` 서브디렉터리가 있으면 그것이 루트가 되고, 없으면 저장소 루트가 사용된다. 동기화 시점을 직접 제어하려면: `new GitSkillRepository(url, false)`로 만든 뒤 `repo.sync()`를 수동으로 호출하라.

### Nacos

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-nacos-skill</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

```java
NacosSkillRepository market = new NacosSkillRepository(aiService, "namespace");
HarnessAgent.builder()
        .skillRepository(market)
        .build();
```

온라인 배포 + 변경 구독에 가장 적합하다. `market`은 `AutoCloseable`이므로 셧다운 시 닫아서 구독을 해제하라.

### MySQL

```java
MysqlSkillRepository registry = MysqlSkillRepository.builder(dataSource)
        .databaseName("agentscope")
        .skillsTableName("skills")
        .createIfNotExist(true)
        .writeable(true)
        .build();

HarnessAgent.builder()
        .skillRepository(registry)
        .build();
```

플랫폼 쪽에서 스킬을 관리할 때 흔히 사용된다. `writeable(true)`는 에이전트가 다시 써넣을 수 있게 해준다; 읽기 전용 배포를 원하면 `false`를 전달하라.

### Classpath

JAR 안에 스킬을 담아 배포한다.

```
src/main/resources/skills/
└── code-reviewer/
    └── SKILL.md
```

```java
.skillRepository(new ClasspathSkillRepository("skills"))
```

일반 JAR와 Spring Boot fat JAR 모두에서 동작한다.

### 여러 스토어

`skillRepository(...)`를 여러 번 호출하라; 나중에 등록한 것이 우선한다.

```java
HarnessAgent.builder()
        .skillRepository(communityMarket)
        .skillRepository(internalRegistry)
        .skillRepository(teamGitRepo)
        .build();
```

## 워크스페이스 스킬

워크스페이스 스킬은 등록이 필요 없다; 디렉터리만 제자리에 놓으면 된다.

### 모두가 공유

```
workspace/skills/
└── code-reviewer/
    ├── SKILL.md
    ├── references/
    │   └── style-guide.md
    └── scripts/
        └── run-checks.sh
```

프로젝트 고유 규칙, 내부 관례에 적합하다.

### 사용자별

특정 사용자 한 명만을 위한 스킬을 설치하거나 공유 스킬을 오버라이드하려면, 해당 사용자의 `userId`로 명명된 디렉터리 아래에 배치하라.

```
workspace/
├── skills/code-reviewer/SKILL.md   ← 공유 버전
└── alice/
    └── skills/
        └── code-reviewer/
            └── SKILL.md            ← Alice에게만 보임; 공유 버전을 오버라이드
```

이렇게 하려면 호출자가 `RuntimeContext`에서 `userId="alice"`를 전달해야 한다.

`workspace/<userId>/skills/`는 **논리적 경로**이며, 반드시 "로컬 디스크 위의 디렉터리"인 것은 아니다. 스킬 파일은 `AbstractFilesystem` 추상화를 통해 읽고 쓰이며, 실제로 어디에 물리적으로 저장되는지는 설정한 [파일 시스템 모드](/v2/ko/docs/harness/filesystem)에 달려 있다 — 따라서 사용자별 스킬 격리는 스토리지 백엔드와 분리되어 있다.

- **로컬 + shell** — 호스트 디스크 위의 실제 `workspace/alice/skills/...`.
- **공유 스토어(원격 파일 시스템)** — `skills/` 접두사가 KV 스토어로 라우팅된다; 사용자별 격리는 네임스페이스 키 `agents/<agentId>/users/alice/skills/...`로 나타나며, 레플리카 간에 일관되고, 관리 콘솔의 수정 사항은 다음 추론 스텝부터 반영된다.
- **샌드박스(샌드박스 파일 시스템)** — 호스트 쪽 사용자 디렉터리는 샌드박스 시작 시 워크스페이스 투영을 통해 컨테이너의 `/workspace`로 하이드레이션되므로, 에이전트는 샌드박스 안에서도 동일한 사본을 읽는다.

어떤 모드를 실행하든, `<userId>/skills/`는 동일한 우선순위로 공유 버전을 오버라이드한다. 모드별 격리 키, 물리적 표현, `userId`의 역할에 대한 자세한 내용은 [파일 시스템](/v2/ko/docs/harness/filesystem#멀티-유저-격리가-동작하는-방식)을 참고하라.

## 충돌 해결

네 가지 소스 모두 동일한 이름의 스킬을 만들어낼 수 있다. 우선순위는 낮음에서 높음 순이다.

| 우선순위 | 소스 | 설정 방법 |
|----------|--------|------------------|
| 1(가장 낮음) | 프로젝트 전역 디렉터리 | `projectGlobalSkillsDir(Path)`, 예: `~/.agentscope/skills/` |
| 2 | 마켓플레이스 | `skillRepository(...)`; 나중에 등록한 것이 우선 |
| 3 | 워크스페이스 공유 | `workspace/skills/` |
| 4(가장 높음) | 사용자별 | `<userId>/skills/` |

하위 계층에서 충돌하지 않는 스킬은 여전히 표시된다; 이름이 겹칠 때만 상위 계층에 가려진다.

예시: 팀 Git에 범용 `code-reviewer`가 있고, 프로젝트의 `workspace/skills/code-reviewer/`가 이 코드베이스에서는 이를 오버라이드하며, Alice의 `<alice>/skills/code-reviewer/`는 Alice에게만 이를 다시 오버라이드한다 — 다른 사용자는 여전히 프로젝트 버전을 본다.

## 공통 Builder 옵션

| 메서드 | 참고 |
|--------|-------|
| `skillRepository(repo)` | 마켓플레이스를 추가; 여러 번 호출 가능 |
| `skillRepositories(list)` | 모든 마켓플레이스를 한 번에 교체 |
| `projectGlobalSkillsDir(path)` | 프로젝트 전역 디렉터리를 활성화; 없으면 건너뜀 |
| `disableDynamicSkills()` | "매 추론 전 재병합"을 끄고, 빌드 시점에 한 번만 병합 |

서브에이전트는 부모의 마켓플레이스와 프로젝트 전역 디렉터리를 자동으로 상속한다.

`disableDynamicSkills()`를 사용할 시점: 일회성 작업이거나, 매 턴마다 다시 가져오고 싶지 않은 느린 마켓플레이스 스토어를 사용할 때. 보통은 건드리지 않아도 된다.

## 자가 학습 루프(선택 사항)

Harness는 에이전트가 스스로 스킬을 초안 작성 / 정리 / 아카이브할 수 있는 루프를 엮어 제공한다. 각 단계는 독립적으로 옵트인 가능하다.

### 1단계: 에이전트가 스킬을 작성하도록 허용

```java
HarnessAgent.builder()
    ...
    .enableSkillManageTool(SkillManageConfig.defaults())
    .build();
```

활성화되면 에이전트는 두 가지 도구를 얻는다.

- `propose_skill` — 새로운 스킬을 초안으로 `skills/_drafts/<name>/`에 작성, 검토 대기
- `skill_manage` — 기존 스킬 편집(생성 / 편집 / 부속 파일 추가 / 삭제)

"초안 → 검토" 두 단계를 건너뛰고 에이전트의 쓰기가 바로 반영되게 하려면: `.enableSkillManageTool(true)`(`autoPromote=true`). 프로덕션에는 권장하지 않는다.

프레임워크는 또한 에이전트가 `load_skill_through_path` / `read_skill`을 호출할 때마다 사용 카운터를 자동으로 증가시키며, 이는 `skills/.usage.json`에 저장된다 — 이 데이터가 아래의 정리와 카나리 롤아웃을 뒷받침한다.

### 2단계: 검토 게이트 + 가시성 필터 추가

```java
.enableSkillPromotionGate(
    new LocalApprovalGate(LocalApprovalGate.defaultPrompter()),    // 누가 검토하는가
    new CompositeFilter(List.of(                                    // 어떻게 노출할 것인가
        new EnvironmentFilter("prod", skillUsageStore),
        new CanaryFilter(0.10, skillUsageStore)
    )))
.environment("prod")
```

- **게이트** — 초안은 실제 스킬로 승격되기 전에 이를 통과해야 한다. 세 가지 내장 방식이 있다: 전체 거부(기본값), 로컬 사람 승인(stdin 등), 알림-후-대기.
- **가시성 필터** — 추론 중에 에이전트가 볼 수 있는 에이전트가 작성한 스킬을 결정한다. 배포 환경 태그, 카나리 비율, 허용 목록으로 조합할 수 있다.

### 3단계: 백그라운드 주기적 정리

```java
.enableSkillCurator(SkillCuratorConfig.builder()
    .intervalHours(7 * 24)        // 매주
    .staleAfterDays(30)
    .archiveAfterDays(90)
    .build())
```

스로틀링된 백그라운드 작업이 실행된다: 30일 이상 사용되지 않은 스킬은 stale이 되고, 90일 이상이면 `skills/.archive/`로 이동한다. 선택적으로 LLM 기반 "umbrella merge" 패스도 실행할 수 있다(기본은 dry-run — 리포트만 만들고 실제로 파일을 바꾸지는 않는다).

### 프로그래밍 방식 트리거

애플리케이션 코드에서:

```java
List<SkillAuditLog.Entry> entries = agent.queryAudit(LocalDate.now(), e -> true);

agent.runCuratorOnce()                                       // 지금 즉시 정리 실행(스로틀 무시)
     .subscribe(report -> System.out.println(report));

agent.promoteSkill("notes-taker", "alice")                   // 초안을 수동으로 승격
     .subscribe(result -> System.out.println(result));
```

## 에이전트가 스킬을 읽고 실행하는 방식

에이전트가 추론할 때, 현재 범위 안에 있는 모든 스킬을 나열한 `<available_skills>` 블록을 시스템 프롬프트에서 보게 된다.

```xml
<available_skills>
<skill>
  <name>code-reviewer</name>
  <description>Use when the user asks for code review, style feedback, or PR audits.</description>
  <skill-id>code-reviewer_workspace-namespaced</skill-id>
  <files-root>/workspace/skills/code-reviewer</files-root>
</skill>
...
</available_skills>
```

각 엔트리는 에이전트가 로드할지 여부를 판단할 수 있을 만큼의 메타데이터만 담고 있다. `<files-root>`가 있으면, 이는 에이전트가 셸 실행에 사용하는 절대 경로다(아래 참고).

### SKILL.md와 리소스 읽기

스킬을 활성화하려면 에이전트가 내장 도구인 `load_skill_through_path`를 호출한다.

- `load_skill_through_path(skillId, path="SKILL.md")`는 마크다운 본문을 반환한다
- `load_skill_through_path(skillId, path="references/style-guide.md")`는 스킬 디렉터리 아래의 다른 파일을 반환한다

파일을 가져오는 방식은 스킬이 어디서 왔는지에 따라 다르다.

| 스킬 소스 | `path`가 해석되는 방식 |
|--------------|------------------------|
| 프로젝트 전역 디렉터리(1계층) | 등록 시점에 메모리로 미리 로드됨 |
| 마켓플레이스 — Git / MySQL / Nacos / classpath(2계층) | 백엔드가 메모리로 미리 로드함 |
| `workspace/skills/` 공유(3계층) | 등록 시점에 메모리로 미리 로드됨 |
| `<userId>/skills/` 사용자별(4계층) | SKILL.md는 미리 로드되고, 나머지 파일은 필요할 때 `AbstractFilesystem`을 통해 읽음(사용자별 네임스페이스 + 샌드박스 라우팅이 자동으로 적용됨) |

에이전트는 이 차이를 알지 못한다 — `load_skill_through_path`는 항상 같은 방식으로 동작한다. 폴백 체인은 "메모리 적중 → 파일 시스템 읽기 → 실제로 사용 가능한 모든 경로를 나열한 오류"이므로, 잘못된 경로는 막다른 길이 아니라 유용한 목록을 반환한다.

### `<files-root>`와 셸 실행

스킬이 스크립트(예: `scripts/run-checks.sh`)를 담고 있으면, 에이전트는 `execute_shell_command`로 이를 실행하기 위한 절대 경로가 필요하다. 이 경로는 각 스킬 엔트리의 `<files-root>` 엘리먼트에서 온다. 해석은 파일 시스템 모드에 따라 다르다.

| FS 모드(셸 사용 가능?) | 워크스페이스 스킬 `<files-root>` | 마켓플레이스 스킬 `<files-root>` |
|----------------------------|--------------------------------|-----------------------------------|
| 샌드박스 | `/workspace/skills/<name>` | `/workspace/.skills-cache/<source>/<name>` |
| Local-with-shell | `<wsRoot>/skills/<name>` | `<wsRoot>/.skills-cache/<source>/<name>` |
| Local without shell / Composite | (렌더링되지 않음 — 셸 도구가 등록되지 않음) | (렌더링되지 않음) |

따라서 에이전트의 셸 호출은 항상 `execute_shell_command("python3 <files-root>/scripts/foo.py")` 형태다 — 경로를 추측할 필요도, 소스별 변형을 기억할 필요도 없다.

### 마켓플레이스 파일이 실제로 위치하는 곳

마켓플레이스 스킬 리소스는 처음에는 메모리 바이트로 시작한다. 셸 실행이 동작하려면, harness가 매 추론 스텝 전에 이를 `<wsRoot>/.skills-cache/<source>/<name>/`으로 실체화(materialize)한다.

- 파일별 SHA-256 중복 제거 — 변경된 파일만 다시 쓰여진다
- 고아 디렉터리(더 이상 게시되지 않는 스킬, 또는 빌더에서 제거된 저장소)는 같은 패스에서 정리된다
- 샌드박스 모드에서는 `.skills-cache`가 기본 워크스페이스 투영 루트에 포함되어 있으므로, 스테이징된 트리는 샌드박스 시작 시(그리고 콘텐츠 변경 시) `workspace/skills/`와 함께 샌드박스로 하이드레이션된다

워크스페이스 스킬(3계층 / 4계층)은 이미 워크스페이스 트리 안에 있으므로 스테이징이 필요 없다.

두 저장소가 같은 `getSource()`를 보고하면, 두 번째 것은 경고 로그와 함께 자동으로 접미사가 붙는다(`<source>_2`, `<source>_3`, …), 이는 경로와 skill-id가 절대 충돌하지 않도록 한다.

## 샌드박스에서 스킬 실행하기

[샌드박스 모드](/v2/ko/docs/harness/filesystem#모드-2-샌드박스sandboxfilesystemspec-계열)에서는 모든 파일 작업과 셸 명령이 격리된 컨테이너 안에서 실행된다 — 호스트는 전혀 영향을 받지 않는다. 이로 인해 한 가지 문제가 생긴다: 스킬의 스크립트(`scripts/run-checks.sh`, `scripts/foo.py` 등)는 호스트에서 작성되지만, 에이전트는 이를 컨테이너 안에서 실행해야 한다. Harness는 "실체화 → 투영 → 컨테이너 내부 실행"이라는 3단계 파이프라인으로 이를 투명하게 처리한다. 아래에서 하나씩 분석한다.

### 어떤 스킬이 샌드박스에 들어가는가

컨테이너 안에서 실행될 수 있는 스킬에는 두 부류가 있으며, 스테이징 지점이 서로 다르다.

| 소스 | 샌드박스로 들어가기 전 위치 | 샌드박스 안의 경로 |
|--------|-----------------------------------|-------------------------|
| 워크스페이스 스킬(3계층 `workspace/skills/`, 4계층 `<userId>/skills/`) | 이미 워크스페이스 트리 안 | `/workspace/skills/<name>` |
| 마켓플레이스 스킬(1계층 프로젝트 전역, 2계층 Git / MySQL / Nacos / classpath) | 처음에는 메모리 바이트 | `/workspace/.skills-cache/<source>/<name>` |

### 1단계: 마켓플레이스 스킬을 호스트로 실체화

마켓플레이스 스킬 리소스는 메모리 바이트로 도착한다 — 셸이 이를 직접 실행할 수 없다. 매 추론 스텝 전에, `MarketplaceStager`가 이를 호스트의 `<wsRoot>/.skills-cache/<source>/<name>/`에 기록한다.

- **파일별 SHA-256 중복 제거** — 변경된 파일만 다시 쓰이고, 변경되지 않은 파일은 건너뛴다.
- **고아 정리** — 더 이상 게시되지 않는 스킬, 또는 빌더에서 제거된 저장소가 남긴 디렉터리는 같은 패스에서 삭제된다.
- **실행 비트 복구** — 수집 과정은 리소스를 문자열로 변환하며 POSIX 모드를 버리므로, stager는 휴리스틱으로 `+x`를 다시 도출한다: 0바이트에 shebang(`#!`)이 있거나, 알려진 스크립트 접미사(`.sh`/`.bash`/`.py`/`.rb`/`.pl`/`.js`/`.mjs`)이면 실행 비트를 추가한다(`chmod +x` 시맨틱을 따름 — 이미 읽기 권한이 있는 비트에만 실행 권한 추가). 순수한 정적 자산(`.json`/`.md`/`.txt`)은 644로 유지된다.

워크스페이스 스킬(3계층 / 4계층)은 이 단계를 건너뛴다 — 이미 워크스페이스 트리 안에 있기 때문이다.

### 2단계: 워크스페이스를 샌드박스로 투영

샌드박스 `start()` 시점에, harness는 워크스페이스의 "정적 자산"을 tar로 묶어 컨테이너의 `/workspace`로 하이드레이션한다. 기본 투영 루트(`workspaceProjectionRoots`)는 스킬에 필요한 정확히 두 디렉터리를 포함한다.

```
AGENTS.md  skills/  subagents/  knowledge/  .skills-cache/
```

따라서 `workspace/skills/`(`<userId>/skills/` 포함)와 1단계에서 생성된 `.skills-cache/`가 함께 하이드레이션된다. 투영은 포함된 모든 파일에 대해 하나의 전체 SHA-256을 계산한다; 이전 실행과 일치하면 하이드레이션을 건너뛴다 — 따라서 반복되는 `call()`은 동일한 파일을 다시 전송하지 않으며, 콘텐츠는 변경이 있을 때만 다시 들어간다.

조정 가능한 항목(`DockerFilesystemSpec` / `KubernetesFilesystemSpec` / 다른 샌드박스 spec에서):

| 메서드 | 효과 |
|--------|--------|
| `workspaceProjectionRoots(List)` | 어떤 루트를 투영할지 커스터마이즈(기본값은 `skills`, `.skills-cache` 포함) |
| `workspaceProjectionEnabled(false)` | 투영을 완전히 비활성화 — 이를 끄면 샌드박스 안에 스킬 파일이 없으므로 스크립트를 실행할 수 없음 |

### 3단계: 컨테이너 안에서 스크립트 실행

샌드박스 모드에서는, `<available_skills>` 블록의 각 스킬 `<files-root>`가 **컨테이너 내부** 접두사로 렌더링된다.

| 스킬 유형 | `<files-root>` |
|------------|----------------|
| 워크스페이스 스킬 | `/workspace/skills/<name>` |
| 마켓플레이스 스킬 | `/workspace/.skills-cache/<source>/<name>` |

따라서 에이전트는 단순히 다음을 실행한다.

```
execute_shell_command("python3 /workspace/skills/code-reviewer/scripts/run-checks.sh <target>")
```

이 명령은 컨테이너 안에서 실행되며 투영된 바로 그 파일을 읽는다. 에이전트는 스킬이 어느 계층에서 왔는지 알 필요가 없다 — 프레임워크가 접두사를 계산한다.

> 샌드박스 백엔드가 워크스페이스를 기본이 아닌 위치에 마운트한다면(예: AgentRun은 `/home/agentscope/workspace`를 사용함), `<files-root>` 접두사가 그에 맞게 바뀌며, 에이전트는 여전히 올바른 절대 경로를 받는다.

### 호출 간 스크립트 부작용 유지하기

스크립트가 의존성을 설치하거나 산출물을 생성하고(`npm install`, `pip install`, 빌드 출력), 다음 `call()`에서도 그것을 유지하고 싶다면, 샌드박스에 [스냅샷](/v2/ko/docs/harness/filesystem#스냅샷-전략)(`snapshotSpec(...)`)을 지정하라. 스냅샷은 `/workspace` 전체를 캡처한다; 같은 scope 키의 다음 호출은 먼저 스냅샷을 복원한 뒤 투영을 그 위에 덧씌우므로, 설치했던 것을 다시 설치할 필요가 없다.

### 참고: SKILL.md 읽기는 샌드박스가 필요 없다

흔히 혼동하는 지점: 스킬을 **읽는 것**(`load_skill_through_path`가 `SKILL.md` / `references/`를 가져오는 것)은 메모리나 호스트 파일 시스템을 거치며 샌드박스와는 무관하다; 오직 **셸을 통해 스크립트를 실행하는 것**만이 파일이 실제로 컨테이너 안에 있어야 한다. 그러므로 투영이 비활성화되어 있거나, 스크립트를 전혀 포함하지 않는 스킬이라도, 에이전트는 여전히 정상적으로 지침과 참고 자료를 읽을 수 있다.

## 팁

**`description`이 에이전트가 당신의 스킬을 사용할지를 결정한다.** 에이전트는 처음에는 이름 + description만 보고 세부 내용을 로드할지 결정한다. "데이터 분석 도구"는 "사용자가 통계, 리포트, 트렌드 차트를 요청할 때 사용"보다 훨씬 덜 유용하다.

**`SKILL.md`는 가볍게 유지하라.** 2,000토큰 이하를 목표로 하고, 참고 자료는 `references/` 아래, 스크립트는 `scripts/` 아래에 두라. 에이전트는 필요할 때 이를 읽는다.

**SKILL.md와 스크립트에서는 상대 경로를 사용하라.** 추상 파일 시스템의 다계층 격리 때문에, 항상 SKILL.md를 기준으로 한 상대 경로로 리소스와 스크립트를 참조하라(예: `scripts/run.py`, `references/guide.md`). `/workspace/scripts/run.py`처럼 절대 경로를 하드코딩하지 **말라**. 프레임워크는 활성화된 파일 시스템 모드에 따라 각 스킬에 맞는 올바른 `<files-root>` 절대 경로 접두사를 자동으로 생성하며, 에이전트는 셸 실행 시점에 `<files-root>`를 사용해 전체 경로를 구성한다. 절대 경로를 하드코딩하면 스킬이 특정 파일 시스템 모드에서만 동작하게 된다.

**마켓플레이스에는 범용 능력을, 워크스페이스에는 프로젝트 고유의 것을.** 코드 리뷰, 표 분석 → 팀 Git에서 공동 유지보수. 내부 RPC 관례, 프로젝트 명명 규칙 → `workspace/skills/`에 두어 코드와 함께 버전 관리되도록.

**사용자별 디렉터리는 "오버라이드 + 보강" 용도이지, 주된 저장소가 아니다.** 중요한 스킬은 모든 사용자에게 보이도록 유지하라.

**자가 학습은 순서대로 활성화하라**: 아무도 새 스킬을 작성하지 않는데 curator를 먼저 돌리는 것은 의미가 없다. `enableSkillManageTool`로 시작하고, 그다음 승격 게이트를 추가하고, 마지막으로 curator를 추가하라.

## 관련 문서

- [워크스페이스](/v2/ko/docs/harness/workspace) — `skills/`의 전체 레이아웃
- [파일 시스템](/v2/ko/docs/harness/filesystem) — 멀티테넌트 격리와 사용자별 버킷 분리
- [아키텍처](/v2/ko/docs/harness/architecture) — 매 추론 스텝마다 스킬 집합이 어떻게 재구성되는지
