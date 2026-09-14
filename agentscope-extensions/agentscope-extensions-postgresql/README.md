# agentscope-extensions-postgresql

> **Deprecated.** Superseded by
> [`agentscope-extensions-jdbc`](../agentscope-extensions-jdbc/README.md), which
> covers MySQL, PostgreSQL, H2, and SQLite behind one dialect abstraction. This
> module is preserved unchanged for backward compatibility and will be removed
> in a future major release — new projects should depend on
> `agentscope-extensions-jdbc` instead.

PostgreSQL-backed implementation of AgentScope's `DistributedStore`: agent state
persistence (`AgentStateStore`), workspace filesystem KV (`BaseStore`), sandbox
snapshot storage (`SandboxSnapshotSpec`), and distributed sandbox locking
(`SandboxExecutionGuard`), all backed by tables in a PostgreSQL database reached
through a plain `javax.sql.DataSource`. Existing deployments already wired to
this module continue to work; this README documents its current, working API
for maintenance purposes.

## Installation

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-postgresql</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## Quick Start

```java
import io.agentscope.extensions.postgresql.PostgresDistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import javax.sql.DataSource;

DataSource dataSource = ...; // HikariCP, Druid, etc. — must already point at an existing database

HarnessAgent agent = HarnessAgent.builder()
    .name("my-agent")
    .model("dashscope:qwen-plus")
    .distributedStore(PostgresDistributedStore.create(dataSource))
    .filesystem(new DockerFilesystemSpec().image("ubuntu:24.04"))
    .build();
```

`PostgresDistributedStore.create(dataSource)` wires all four components at once:

- `PostgresAgentStateStore` — agent session state in PostgreSQL, schema/table
  auto-created
- `PostgresBaseStore` — workspace filesystem KV in PostgreSQL, schema
  auto-created
- `PostgresSnapshotSpec` — sandbox snapshots stored as `BYTEA` rows
- `PostgresSandboxExecutionGuard` — distributed lock via PostgreSQL advisory
  locks (`pg_advisory_lock` / `pg_try_advisory_lock`)

The target database itself must already exist; the facade only creates the
schema and tables within it.

## Architecture

### Using only the state store

If you just need cross-replica `AgentStateStore` persistence, construct
`PostgresAgentStateStore` directly. Its no-arg-`DataSource` constructor uses the
default schema (`agentscope`) and table (`agentscope_sessions`) but does **not**
create them; pass `true` to auto-create, or use the builder for a custom
schema/table name:

```java
import io.agentscope.extensions.postgresql.state.PostgresAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import javax.sql.DataSource;

DataSource dataSource = ...;

PostgresAgentStateStore stateStore = PostgresAgentStateStore.builder(dataSource)
    .schemaName("agentscope")
    .tableName("agentscope_sessions")
    .createIfNotExist(true)
    .build();

HarnessAgent agent = HarnessAgent.builder()
    .name("my-agent")
    .model("dashscope:qwen-plus")
    .stateStore(stateStore)
    .build();
```

Single state values are stored as one JSON row (`item_index = 0`); list state is
stored as one row per item (`item_index = 0, 1, 2, ...`), with an incremental
append path plus a stored content hash (`state_key + ":_hash"`) so unchanged
lists are not rewritten on every save. `saveIfVersion` performs CAS via a
`version` column, so the store is safe to share across JVMs.

### Using only the KV store

`PostgresBaseStore` backs `BaseStore` (the workspace filesystem KV). It is built
independently through its own builder, defaulting to table
`agentscope_store` with no schema qualification and `initializeSchema(false)`:

```java
import io.agentscope.extensions.postgresql.store.PostgresBaseStore;
import javax.sql.DataSource;

DataSource dataSource = ...;

PostgresBaseStore baseStore = PostgresBaseStore.builder(dataSource)
    .schemaName("agentscope")
    .tableName("agentscope_store")
    .initializeSchema(true)
    .build();
```

Namespace segments are joined with the ASCII unit separator (`0x1F`) into a
`namespace_path` column, so `search(namespace, ...)` can match both an exact
namespace and any deeper sub-namespace via a `LIKE 'prefix%' ESCAPE '!'` query.
`putIfVersion` is a single-statement conditional `UPDATE` (or `INSERT` when
`expectedVersion == 0`), so it is CAS-safe without extra locking.

### Sandbox locking and snapshots

`PostgresSandboxExecutionGuard` acquires PostgreSQL advisory locks keyed by a
MurmurHash3 hash of the `SandboxIsolationKey`; the lock is tied to its own JDBC
connection and auto-releases if that connection drops:

```java
import io.agentscope.extensions.postgresql.sandbox.PostgresSandboxExecutionGuard;
import java.time.Duration;
import javax.sql.DataSource;

DataSource dataSource = ...;

PostgresSandboxExecutionGuard guard = PostgresSandboxExecutionGuard.builder(dataSource)
    .keyPrefix("agentscope:sandbox:lock:")
    .lockTimeout(Duration.ofMinutes(30))
    .build();
```

`PostgresSnapshotSpec` (backed by `PostgresRemoteSnapshotClient`) stores sandbox
workspace tar archives as `BYTEA` rows in table `agentscope_snapshots` by
default, auto-creating the table on construction:

```java
import io.agentscope.extensions.postgresql.snapshot.PostgresSnapshotSpec;
import javax.sql.DataSource;

DataSource dataSource = ...;

PostgresSnapshotSpec snapshotSpec = new PostgresSnapshotSpec(dataSource);
// or: new PostgresSnapshotSpec(dataSource, "my_snapshots_table")
```

## Migrating to agentscope-extensions-jdbc

| This module | `agentscope-extensions-jdbc` |
|---|---|
| `PostgresDistributedStore.create(ds)` | `JdbcDistributedStore.create(ds)` |
| `new PostgresAgentStateStore(ds, true)` | `new JdbcAgentStateStore(ds, AbstractJdbcDialect.from(ds).build())` |
| `PostgresBaseStore.builder(ds)...` | `JdbcStore.builder(ds).dialect(AbstractJdbcDialect.from(ds).build())` |

See [`agentscope-extensions-jdbc`'s README](../agentscope-extensions-jdbc/README.md)
for the full migration table, database capability matrix, and dialect
architecture.
