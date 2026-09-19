---
title: Git Skill Repository
---

`agentscope-extensions-skill-git-repository`는 원격 Git 저장소를 스킬 저장소로 취급합니다. 매 읽기마다 가벼운 원격 ref 확인을 수행하며, 원격 HEAD가 변경된 경우에만 pull을 수행합니다 — 유휴 상태에서는 거의 오버헤드가 없습니다.

## 사용 시점

- 스킬 콘텐츠의 버전 관리와 리뷰를 Git으로 관리하고 싶은 경우
- 여러 프로젝트에서 하나의 스킬 세트를 공유하고 싶은 경우
- 프로덕션 환경에 데이터베이스나 설정 센터를 내장하고 싶지 않은 경우

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-skill-git-repository</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

내부적으로 JGit을 사용하며, HTTPS와 SSH를 모두 지원합니다.

## 빠른 시작

```java
import io.agentscope.core.skill.repository.GitSkillRepository;
import io.agentscope.core.skill.AgentSkill;

// Public repo + default branch, temporary local directory
GitSkillRepository repo = new GitSkillRepository(
    "https://github.com/agentscope/skills.git"
);

// Register all skills into a Toolkit
Toolkit toolkit = new Toolkit();
repo.getAllSkills().forEach(toolkit::registerSkill);

// Clean up the temp directory on shutdown
Runtime.getRuntime().addShutdownHook(new Thread(repo::close));
```

## 브랜치 고정 / 고정 로컬 경로 사용

```java
GitSkillRepository repo = new GitSkillRepository(
    "https://github.com/agentscope/skills.git",
    "develop",                   // branch
    Path.of("/var/skills/repo"), // local path (null = temp dir)
    "agentscope-public",         // source label (visible in Toolkit)
    true                         // autoSync
);
```

## 프라이빗 저장소 인증

`GitSkillRepository`는 시스템 수준의 Git 설정을 재사용하며, Java 코드에서 자격 증명을 직접 관리하지 않습니다.

- **HTTPS**: `~/.gitconfig`의 credential helper를 사용합니다 (osxkeychain, libsecret 등).
- **SSH**: `~/.ssh/` 아래의 키와 `ssh-agent`를 사용합니다.

```java
// Private SSH repository
GitSkillRepository repo = new GitSkillRepository(
    "git@github.com:my-org/private-skills.git"
);
```

CI 환경에서는 러너 사용자가 자격 증명을 보유하고 있거나 SSH 에이전트가 설정되어 있는지 확인하세요.

## 자동 동기화 vs. 수동 동기화

- `autoSync=true` (기본값): 모든 읽기마다 먼저 `ls-remote`를 실행하며, 원격이 변경된 경우에만 pull이 발생합니다.
- `autoSync=false`: 자동으로 pull하지 않습니다. `repo.sync()`를 호출하여 갱신하세요.

```java
GitSkillRepository repo = new GitSkillRepository(remoteUrl, false);
repo.sync();              // sync once at startup
schedule(() -> repo.sync(), 5, TimeUnit.MINUTES);
```

## 운영 참고 사항

- repo는 싱글턴 Spring Bean으로 유지하고, 종료 시 한 번만 close하세요.
- 임시 디렉터리에는 JVM 종료 훅이 걸려 있지만, 강제 종료된 프로세스는 잔여물을 남길 수 있습니다 — 필요하다면 외부에서 정리하세요.
- 다중 인스턴스 배포에서는 각 인스턴스가 자신의 클론을 유지하므로, 락 경합이 없습니다.
