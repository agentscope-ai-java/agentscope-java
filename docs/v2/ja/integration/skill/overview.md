# Skill Repository

`AgentSkill` は、再利用可能な「skill」を記述するための AgentScope の Markdown + リソースファイル形式です（[Harness · Skill](../../docs/harness/skill.md) を参照）。`AgentSkillRepository` インターフェースは、外部ストレージから skill をロードし、`Toolkit` / `ReActAgent` に渡します。

`agentscope-extensions-*` リポジトリは、以下のすぐに使える実装を提供します。

| 拡張機能 | バックエンド | 最適な用途 |
| --- | --- | --- |
| [Git Repository](git-repository.md) | リモート Git リポジトリ | Git ベースのバージョン管理とレビュー |
| [MySQL Repository](mysql-repository.md) | MySQL データベース | 管理コンソール / 業務システム経由のオンライン編集 |
| [PostgreSQL Repository](postgresql-repository.md) | PostgreSQL データベース | 既存の PostgreSQL インフラ、オンライン編集 |

> Nacos も `AgentSkillRepository` の実装を提供しています。[Nacos](../infrastructure/nacos.md) を参照してください。

## 配線

```java
AgentSkillRepository repo = ...;        // 任意の実装
List<AgentSkill> skills = repo.getAllSkills();

Toolkit toolkit = new Toolkit();
skills.forEach(toolkit::registerSkill);

ReActAgent agent = ReActAgent.builder()
    .name("Assistant")
    .model(model)
    .toolkit(toolkit)
    .build();
```

## 選び方

- **Git の PR フロー、レビュー可能なテキストが欲しい** → Git
- **管理コンソール / ライブな設定編集が欲しい** → MySQL、PostgreSQL、または Nacos
- **複数のソースを混在させたい** → `AgentSkillRepository` を実装するか、同じ toolkit に複数のリポジトリを登録する
