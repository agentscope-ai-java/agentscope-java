---
title: PostgreSQL Skill Repository
---

`agentscope-extensions-skill-postgresql-repository` は、skill を PostgreSQL に完全な CRUD 付きで保存します。管理コンソール / 業務システムで編集して保存すれば、Agent は次の読み取り時にすぐ変更を反映します。

## 使いどころ

- 管理コンソール経由で skill を運用しており、変更をすぐに反映させたい。
- すでに PostgreSQL のインフラがあり、Git への依存を避けたい。
- skill ストレージを業務データとトランザクション境界で共有したい。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-skill-postgresql-repository</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## クイックスタート

```java
import javax.sql.DataSource;
import io.agentscope.core.skill.repository.postgresql.PostgresSkillRepository;

DataSource ds = ...;  // HikariCP, PgBouncer など

// createIfNotExist=true: スキーマとテーブルを自動作成、writeable=true: 書き込みを許可
PostgresSkillRepository repo = new PostgresSkillRepository(ds, true, true);

Toolkit toolkit = new Toolkit();
repo.getAllSkills().forEach(toolkit::registerSkill);
```

## Builder の使用

```java
PostgresSkillRepository repo = PostgresSkillRepository.builder(ds)
    .schemaName("my_schema")
    .skillsTableName("my_skills")
    .resourcesTableName("my_resources")
    .createIfNotExist(true)
    .writeable(true)
    .build();
```

## スキーマ

`createIfNotExist=true` の場合、以下のテーブルが（設定されたスキーマの下に）作成されます。

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

- `agentscope_skills`: skill 本体。`name` は一意で、`skill_content` に完全な `SKILL.md` を保存します。
- `agentscope_skill_resources`: 添付リソースファイル（スクリーンショット、テンプレートなど）で、`id` によってカスケードされます。

MySQL 版とは異なり、PostgreSQL では名前空間の分離境界として**データベース**ではなく**スキーマ**を使用します — データベースは JDBC URL 経由で選択されます。

## レガシーテーブルとの互換性

- 既存のテーブルに `metadata_json` がない場合、リポジトリは `name` + `description` のみのラウンドトリップにフォールバックします。自動で `ALTER TABLE` は行いません。
- アップグレードするには、自分で `ALTER TABLE "agentscope"."agentscope_skills" ADD COLUMN metadata_json TEXT NULL;` を実行してください。

## CRUD

```java
// 書き込み（save は upsert: 既存の name -> 更新）
AgentSkill skill = ...;
repo.save(List.of(skill), /* overwrite */ true);

// 読み取り
AgentSkill loaded = repo.getSkill("calculator");
List<String> names = repo.getAllSkillNames();
boolean exists = repo.skillExists("calculator");

// 削除
repo.delete("calculator");
```

書き込みと削除はトランザクション内で実行され、リソーステーブルの `ON DELETE CASCADE` によって孤立リソースが発生しないことが保証されます。

## Builder リファレンス

| メソッド | 備考 |
| --- | --- |
| `schemaName(String)` | デフォルト `agentscope` |
| `skillsTableName(String)` | デフォルト `agentscope_skills` |
| `resourcesTableName(String)` | デフォルト `agentscope_skill_resources` |
| `createIfNotExist(boolean)` | `true` で `CREATE SCHEMA` + `CREATE TABLE` を自動実行、デフォルト `true` |
| `writeable(boolean)` | 書き込み操作を許可するかどうか、デフォルト `true` |
