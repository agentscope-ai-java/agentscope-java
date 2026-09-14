> **Deprecated.** This module is superseded by
> [`agentscope-extensions-jdbc`](../agentscope-extensions-jdbc/README.md), which
> provides a unified multi-database dialect (MySQL, PostgreSQL, H2, SQLite) behind
> a single API. `agentscope-extensions-mysql` remains fully functional and is
> documented below as-is for existing users; new integrations should start with
> `agentscope-extensions-jdbc` instead.

MySQL/JDBC-backed implementations of AgentScope's `DistributedStore`: agent state
persistence (`AgentStateStore`), workspace filesystem KV (`BaseStore`), sandbox
snapshot storage (`SandboxSnapshotSpec`), and distributed locking
(`SandboxExecutionGuard` via MySQL `GET_LOCK()`), all backed by a single JDBC
`DataSource`. Add this module when you deploy `HarnessAgent` across multiple
replicas and want to share state through a MySQL database you already operate.

## Installation

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-mysql</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## Quick Start

`MysqlDistributedStore.create(DataSource)` wires all four components — state
store, base store, snapshot spec, and execution guard — from a single JDBC
`DataSource`:

```java
import io.agentscope.extensions.mysql.MysqlDistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import javax.sql.DataSource;

DataSource dataSource = ...; // HikariCP, Druid, etc. pointed at your MySQL instance

HarnessAgent agent = HarnessAgent.builder()
    .name("my-agent")
    .model("dashscope:qwen-plus")
    .distributedStore(MysqlDistributedStore.create(dataSource))
    .filesystem(new DockerFilesystemSpec()
            .image("ubuntu:24.04"))
    .build();
```

This configures:

- `MysqlAgentStateStore` — agent session state in the `agentscope_sessions` table
- `JdbcStore` — workspace filesystem KV in the `agentscope_store` table
- `JdbcSnapshotSpec` — sandbox snapshots stored as BLOBs
- `JdbcSandboxExecutionGuard` — distributed locking via MySQL's native
  `GET_LOCK()` / `RELEASE_LOCK()`

### Using only the state store

If you just need cross-replica `AgentStateStore` persistence (no distributed
sandbox locking or snapshots), construct `MysqlAgentStateStore` directly instead
of the full `MysqlDistributedStore` facade:

```java
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import javax.sql.DataSource;

DataSource dataSource = ...;

// createIfNotExist=true auto-creates the "agentscope" database and
// "agentscope_sessions" table on first use.
AgentStateStore stateStore = new MysqlAgentStateStore(dataSource, true);

HarnessAgent agent = HarnessAgent.builder()
    .name("my-agent")
    .model("dashscope:qwen-plus")
    .stateStore(stateStore)
    .build();
```

`MysqlAgentStateStore` also has a constructor that takes a custom database name
and table name: `new MysqlAgentStateStore(dataSource, "my_db", "my_sessions", true)`.

## Architecture

The module is split into independently usable pieces, all consumed through
`MysqlDistributedStore` for convenience:

- **`MysqlAgentStateStore`** (`state` package) — `AgentStateStore` backed by a
  single wide table (`session_id`, `state_key`, `item_index`, `state_data`,
  `version`). Single values are stored at `item_index = 0`; list values get one
  row per item, so append-only lists are written with pure `INSERT`s instead of
  read-modify-write. `saveIfVersion` performs optimistic-concurrency updates
  against the `version` column.
- **`JdbcStore`** (`store` package) — `BaseStore` (workspace KV) implementation
  driven by a `JdbcStoreDialect`. The dialect interface supplies the four
  vendor-specific statements (`CREATE TABLE`, upsert, insert, CAS update); fixed
  select/delete/search statements have SQL-standard defaults. Four dialects ship
  out of the box — `MysqlJdbcStoreDialect`, `PostgresJdbcStoreDialect`,
  `H2JdbcStoreDialect`, and `SqliteJdbcStoreDialect` — and `JdbcStoreDialect.from(DataSource)`
  auto-detects the right one from JDBC `DatabaseMetaData`, defaulting to Postgres
  syntax for unrecognized engines. Build one explicitly via
  `JdbcStore.builder(dataSource).dialect(new MysqlJdbcStoreDialect()).initializeSchema(true).build()`.
- **`JdbcSnapshotSpec`** (`snapshot` package) — `SandboxSnapshotSpec` that stores
  sandbox workspace tar archives as BLOBs, via `JdbcRemoteSnapshotClient`.
- **`JdbcSandboxExecutionGuard`** (`sandbox` package) — `SandboxExecutionGuard`
  using MySQL's connection-scoped `GET_LOCK()` / `RELEASE_LOCK()`. Because MySQL
  named locks are server-scoped rather than database-scoped, configure a unique
  `keyPrefix` via the builder when multiple applications share a MySQL server:

  ```java
  JdbcSandboxExecutionGuard guard = JdbcSandboxExecutionGuard.builder(dataSource)
      .keyPrefix("my-app:sandbox:lock")
      .lockTimeout(Duration.ofMinutes(10))
      .build();
  ```

### Non-MySQL dialects are for `JdbcStore` only

The `store` package's `PostgresJdbcStoreDialect`, `H2JdbcStoreDialect`, and
`SqliteJdbcStoreDialect` let `JdbcStore` (the KV `BaseStore`) run against those
engines, but `MysqlAgentStateStore` and `JdbcSandboxExecutionGuard` in this
module are MySQL-only (they use `ON DUPLICATE KEY UPDATE` and `GET_LOCK()`
respectively). For state-store and locking portability across databases, use
`agentscope-extensions-jdbc` instead.

## Learn more

- [`agentscope-extensions-jdbc`](../agentscope-extensions-jdbc/README.md) — the
  modern, multi-database replacement for this module, with a migration table
  from `MysqlDistributedStore` / `MysqlAgentStateStore` to their JDBC
  equivalents.
- [Distributed Storage](../../docs/v2/en/integration/distributed/index.md) — the
  `DistributedStore` concept and capability matrix across backends.
