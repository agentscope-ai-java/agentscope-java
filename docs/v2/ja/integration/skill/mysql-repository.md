---
title: MySQL Skill Repository
---

`agentscope-extensions-skill-mysql-repository` は、skill を MySQL に完全な CRUD 付きで保存します。管理コンソール / 業務システムで編集して保存すれば、Agent は次の読み取り時にすぐ変更を反映します。

## 使いどころ

- 管理コンソール経由で skill を運用しており、変更をすぐに反映させたい。
- すでに MySQL のインフラがあり、Git への依存を避けたい。
- skill ストレージを業務データとトランザクション境界で共有したい。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-skill-mysql-repository</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## クイックスタート

```java
import com.zaxxer.hikari.HikariDataSource;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.skill.repository.mysql.MysqlSkillRepository;

HikariDataSource ds = new HikariDataSource();
ds.setJdbcUrl("jdbc:mysql://localhost:3306/agentscope");
ds.setUsername("root");
ds.setPassword("***");

// 第2引数 createIfNotExist=true: データベースとテーブルを自動作成
MysqlSkillRepository repo = new MysqlSkillRepository(ds, true);

ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model(model)
    .skillRepository(repo)
    .build();
```

## スキーマ

`createIfNotExist=true` の場合、以下のテーブルが作成されます。

```sql
CREATE TABLE IF NOT EXISTS agentscope_skills (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    skill_content LONGTEXT NOT NULL,
    source VARCHAR(255) NOT NULL,
    metadata_json LONGTEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agentscope_skill_resources (
    id BIGINT NOT NULL,
    resource_path VARCHAR(500) NOT NULL,
    resource_content LONGTEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id, resource_path),
    FOREIGN KEY (id) REFERENCES agentscope_skills(id) ON DELETE CASCADE
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

- `agentscope_skills`: skill 本体。`name` は一意で、`skill_content` に完全な `SKILL.md` を保存します。
- `agentscope_skill_resources`: 添付リソースファイル（スクリーンショット、テンプレートなど）で、`id` によってカスケードされます。

## レガシーテーブルとの互換性

- 既存のテーブルに `metadata_json` がない場合、リポジトリは `name` + `description` のみのラウンドトリップにフォールバックします。自動で `ALTER TABLE` は行いません。
- アップグレードするには、自分で `ALTER TABLE agentscope_skills ADD COLUMN metadata_json LONGTEXT NULL;` を実行してください。

## カスタムデータベース名 / テーブル名

```java
MysqlSkillRepository repo = new MysqlSkillRepository(
    ds,
    "skill_center",          // データベース
    "ops_skills",            // skill テーブル
    "ops_skill_resources",   // リソーステーブル
    true                     // 自動作成
);
```

## CRUD

```java
// 書き込み（save は upsert: 既存の name → 更新）
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
