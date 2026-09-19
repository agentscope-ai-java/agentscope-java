---
title: MySQL / JDBC
---

`agentscope-extensions-mysql`는 기존 관계형 데이터베이스 인프라를 보유한 팀을 위해 JDBC 기반의 풀스택 분산 스토리지를 제공합니다.

## 의존성

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-mysql</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

데이터베이스 드라이버(예: `mysql-connector-j`, `postgresql`)는 별도로 추가하세요.

## 한 줄 설정

```java
import io.agentscope.extensions.mysql.MysqlDistributedStore;

DataSource dataSource = ...;  // HikariCP, Druid, etc.
DistributedStore store = MysqlDistributedStore.create(dataSource);

HarnessAgent agent = HarnessAgent.builder()
    .distributedStore(store)
    .filesystem(new RemoteFilesystemSpec()
            .isolationScope(IsolationScope.USER))
    .build();
```

## 제공되는 컴포넌트

### 1. MysqlAgentStateStore

에이전트 상태를 MySQL 테이블에 영속화합니다.

```java
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;

AgentStateStore store = new MysqlAgentStateStore(dataSource, true);  // auto-create schema
AgentStateStore store = new MysqlAgentStateStore(
    dataSource, "agentscope_prod", "session_state", true);  // custom DB/table names
```

### 2. JdbcStore (BaseStore)

방언(dialect)을 자동 감지하는 워크스페이스 파일 시스템 KV 스토리지입니다.

```java
import io.agentscope.extensions.mysql.store.JdbcStore;

BaseStore store = JdbcStore.builder(dataSource)
    .initializeSchema(true)
    .build();
```

지원되는 방언(자동 감지): MySQL, PostgreSQL, H2, SQLite.

### 3. JdbcSnapshotSpec

샌드박스 스냅샷을 데이터베이스 테이블에 LONGBLOB 형태로 저장합니다.

```java
import io.agentscope.extensions.mysql.snapshot.JdbcSnapshotSpec;

SandboxSnapshotSpec spec = new JdbcSnapshotSpec(dataSource);
```

### 4. JdbcSandboxExecutionGuard

MySQL의 `GET_LOCK()` / `RELEASE_LOCK()`을 통한 분산 락입니다.

```java
import io.agentscope.extensions.mysql.sandbox.JdbcSandboxExecutionGuard;

SandboxExecutionGuard guard = JdbcSandboxExecutionGuard.builder(dataSource)
    .keyPrefix("myapp:lock:")
    .lockTimeout(Duration.ofMinutes(30))
    .build();
```

락은 JDBC 커넥션에 종속되어 있으며 — 커넥션이 닫히면 자동으로 해제됩니다.

## 사용 시점

| 시나리오 | 권장 사항 |
|----------|---------------|
| 기존 MySQL을 사용 중이며 Redis를 도입하고 싶지 않은 경우 | **1순위**: MySQL |
| SQL 감사 / 리포팅 / 조인이 필요한 경우 | MySQL |
| 대용량 스냅샷(>100MB) | MySQL BLOB도 동작하지만 OSS를 고려하세요 |
| 최저 지연 시간 | Redis |
