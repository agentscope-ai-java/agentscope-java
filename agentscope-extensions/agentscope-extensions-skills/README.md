# agentscope-extensions-skills

Parent module for `AgentSkillRepository` backends — pluggable marketplaces that a `HarnessAgent`
can pull [skills](../../docs/v2/en/docs/harness/skill.md) from (a `SKILL.md` plus optional
references/scripts) instead of only reading them from the local workspace. This module itself
ships no code; it just aggregates the concrete repository implementations below.

## Sub-modules

| Module | Artifact | Backend |
|--------|----------|---------|
| [`agentscope-extensions-skill-git-repository`](./agentscope-extensions-skill-git-repository) | `agentscope-extensions-skill-git-repository` | Read-only, clones/pulls a Git repo (JGit) and serves skills from its working tree |
| [`agentscope-extensions-skill-mysql-repository`](./agentscope-extensions-skill-mysql-repository) | `agentscope-extensions-skill-mysql-repository` | Reads/writes skills stored in MySQL tables |
| [`agentscope-extensions-skill-postgresql-repository`](./agentscope-extensions-skill-postgresql-repository) | `agentscope-extensions-skill-postgresql-repository` | Reads/writes skills stored in PostgreSQL tables |

All three implement `io.agentscope.core.skill.repository.AgentSkillRepository` and attach to a
`HarnessAgent` the same way, via `HarnessAgent.builder().skillRepository(...)`. Registering more
than one is fine — sources are merged, later registrations winning on a name collision.

## Installation

Depend on whichever backend(s) you need; there is nothing to add for this parent POM itself.

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-skill-git-repository</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## Quick Start

`GitSkillRepository` is the simplest backend — point it at a repo and hand it to the agent:

```java
import io.agentscope.core.skill.repository.GitSkillRepository;
import io.agentscope.harness.agent.HarnessAgent;

HarnessAgent agent =
        HarnessAgent.builder()
                .name("assistant")
                .model("dashscope:qwen-plus")
                .skillRepository(new GitSkillRepository("https://github.com/your-org/team-skills.git"))
                .build();
```

By default each read does a lightweight remote-HEAD check and only re-clones/pulls when it
changed. If the repo has a `skills/` subdirectory, that's treated as the skills root; otherwise
the repo root is used. During reasoning the agent sees these skills alongside any workspace ones
and loads a skill's `SKILL.md` (and its `references/`, `scripts/`) on demand.

The MySQL and PostgreSQL backends follow the same `.skillRepository(...)` attachment point but are
writeable stores built from a `DataSource`, e.g.:

```java
import io.agentscope.core.skill.repository.mysql.MysqlSkillRepository;

MysqlSkillRepository registry =
        MysqlSkillRepository.builder(dataSource)
                .databaseName("agentscope")
                .skillsTableName("skills")
                .createIfNotExist(true)
                .writeable(true)
                .build();

HarnessAgent.builder().skillRepository(registry).build();
```

See each sub-module's own README for its full constructor/builder options, and the docs pages
below for the wider skill system (workspace skills, per-user overrides, conflict resolution).

## Learn more

- [Skill (Harness)](../../docs/v2/en/docs/harness/skill.md) — the full skill system: sources,
  layering/priority, self-learning loop, sandbox execution of skill scripts
- [Skill Repository (Integration)](../../docs/v2/en/integration/skill/index.md)
