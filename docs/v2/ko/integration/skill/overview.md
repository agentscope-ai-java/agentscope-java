---
title: Skill Repository
---

`AgentSkill`은 재사용 가능한 "스킬"을 기술하기 위한 AgentScope의 Markdown + 리소스 파일 포맷입니다 ([Harness · Skill](/v2/ko/docs/harness/skill) 참고). `AgentSkillRepository` 인터페이스는 외부 스토리지에서 스킬을 로드하여 `Toolkit` / `ReActAgent`에 전달합니다.

`agentscope-extensions-*` 저장소는 바로 사용할 수 있는 다음 구현체들을 제공합니다.

| 확장 | 백엔드 | 적합한 용도 |
| --- | --- | --- |
| [Git Repository](/v2/ko/integration/skill/git-repository) | 원격 Git 저장소 | Git 기반 버전 관리 및 리뷰 |
| [MySQL Repository](/v2/ko/integration/skill/mysql-repository) | MySQL 데이터베이스 | 관리자 콘솔 / 비즈니스 시스템을 통한 온라인 편집 |
| [PostgreSQL Repository](/v2/ko/integration/skill/postgresql-repository) | PostgreSQL 데이터베이스 | 기존 PostgreSQL 인프라, 온라인 편집 |

> Nacos도 `AgentSkillRepository` 구현체를 제공합니다. [Nacos](/v2/ko/integration/infrastructure/nacos)를 참고하세요.

## 연결하기

```java
AgentSkillRepository repo = ...;        // any implementation
List<AgentSkill> skills = repo.getAllSkills();

Toolkit toolkit = new Toolkit();
skills.forEach(toolkit::registerSkill);

ReActAgent agent = ReActAgent.builder()
    .name("Assistant")
    .model(model)
    .toolkit(toolkit)
    .build();
```

## 선택 가이드

- **Git PR 플로우와 리뷰 가능한 텍스트를 원한다면** → Git
- **관리자 콘솔 / 실시간 설정 편집을 원한다면** → MySQL, PostgreSQL 또는 Nacos
- **여러 소스를 혼합하고 싶다면** → `AgentSkillRepository`를 직접 구현하거나, 동일한 toolkit에 여러 repository를 등록하세요
