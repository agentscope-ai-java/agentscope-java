---
title: PostgreSQL Skill Repository
---

`agentscope-extensions-skill-postgresql-repository`는 스킬을 완전한 CRUD 기능과 함께 PostgreSQL에 저장합니다. 관리자 콘솔 / 비즈니스 시스템에서 편집하고 저장하면, 다음 읽기 시점에 Agent가 즉시 변경 사항을 반영합니다.

## 사용 시점

- 관리자 콘솔을 통해 스킬을 운영하며, 변경 사항이 즉시 반영되기를 원하는 경우
- 이미 PostgreSQL 인프라를 보유하고 있고 Git 의존성을 원하지 않는 경우
- 스킬 저장소가 비즈니스 데이터와 동일한 트랜잭션 경계를 공유하기를 원하는 경우

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-skill-postgresql-repository</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 빠른 시작

```java
import javax.sql.DataSource;
import io.agentscope.core.skill.repository.postgresql.PostgresSkillRepository;

DataSource ds = ...;  // HikariCP, PgBouncer, etc.

// createIfNotExist=true: auto-create schema and tables; writeable=true: allow writes
PostgresSkillRepository repo = new PostgresSkillRepository(ds, true, true);

Toolkit toolkit = new Toolkit();
repo.getAllSkills().forEach(toolkit::registerSkill);
```

## Builder 사용하기

```java
PostgresSkillRepository repo = PostgresSkillRepository.builder(ds)
    .schemaName("my_schema")
    .skillsTableName("my_skills")
    .resourcesTableName("my_resources")
    .createIfNotExist(true)
    .writeable(true)
    .build();
```

## 스키마

`createIfNotExist=true`인 경우, (설정된 schema 아래에) 다음 테이블이 생성됩니다.

```sql
CREATE TABLE IF NOT EXISTS "agentscope"."agentscope_skills" (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    skill_content TEXT NOT NULL,
    source VARCHAR(255) NOT NULL,
    metadata_json TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS "agentscope"."agentscope_skill_resources" (
    id BIGINT NOT NULL,
    resource_path VARCHAR(500) NOT NULL,
    resource_content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, resource_path),
    FOREIGN KEY (id) REFERENCES "agentscope"."agentscope_skills"(id) ON DELETE CASCADE
);
```

- `agentscope_skills`: 스킬 본체입니다. `name`은 유니크하며, `skill_content`에는 전체 `SKILL.md`가 저장됩니다.
- `agentscope_skill_resources`: 첨부된 리소스 파일(스크린샷, 템플릿 등)이며, `id`를 기준으로 cascade됩니다.

MySQL 버전과 달리, PostgreSQL은 네임스페이스 격리 경계로 데이터베이스가 아닌 **스키마(schema)**를 사용합니다 — 데이터베이스는 JDBC URL을 통해 선택됩니다.

## 레거시 테이블과의 호환성

- 기존 테이블에 `metadata_json`이 없는 경우, 저장소는 `name` + `description`만 주고받는 방식으로 폴백합니다. 자동으로 `ALTER TABLE`을 수행하지 않습니다.
- 업그레이드하려면 직접 `ALTER TABLE "agentscope"."agentscope_skills" ADD COLUMN metadata_json TEXT NULL;`을 실행하세요.

## CRUD

```java
// Write (save is upsert: existing name -> update)
AgentSkill skill = ...;
repo.save(List.of(skill), /* overwrite */ true);

// Read
AgentSkill loaded = repo.getSkill("calculator");
List<String> names = repo.getAllSkillNames();
boolean exists = repo.skillExists("calculator");

// Delete
repo.delete("calculator");
```

쓰기와 삭제는 트랜잭션 내에서 실행되며, 리소스 테이블의 `ON DELETE CASCADE`가 고아 리소스가 남지 않도록 보장합니다.

## Builder 참조

| 메서드 | 설명 |
| --- | --- |
| `schemaName(String)` | 기본값 `agentscope` |
| `skillsTableName(String)` | 기본값 `agentscope_skills` |
| `resourcesTableName(String)` | 기본값 `agentscope_skill_resources` |
| `createIfNotExist(boolean)` | `true`이면 자동으로 `CREATE SCHEMA` + `CREATE TABLE`을 수행, 기본값 `true` |
| `writeable(boolean)` | 쓰기 작업 허용 여부, 기본값 `true` |
