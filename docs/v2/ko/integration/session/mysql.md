```{note}
이 페이지는 [분산 스토리지 — MySQL](../distributed/mysql.md)로 대체되었습니다. 아래 내용은 참고용으로 남겨둡니다.
```

# MySQL 상태 저장소

`agentscope-extensions-mysql`은 AgentScope 에이전트 상태를 MySQL에 영속화합니다. 이미 MySQL 인프라를 보유하고 있거나 트랜잭션 / SQL 기반의 상태 데이터 접근이 필요한 경우에 적합합니다.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-mysql</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

일치하는 JDBC 드라이버(예: `mysql:mysql-connector-j`)는 직접 준비해야 합니다.

## 빠른 시작

```java
import com.zaxxer.hikari.HikariDataSource;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;

HikariDataSource ds = new HikariDataSource();
ds.setJdbcUrl("jdbc:mysql://localhost:3306/agentscope?serverTimezone=UTC");
ds.setUsername("root");
ds.setPassword("***");

// Second arg createIfNotExist=true: auto-create database and table
AgentStateStore stateStore = new MysqlAgentStateStore(ds, true);

ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model(model)
    .stateStore(stateStore)
    .build();
```

데이터베이스와 테이블이 미리 생성되어 있다면, 더 안전한 형태를 사용하세요:

```java
AgentStateStore stateStore = new MysqlAgentStateStore(ds);          // throws IllegalStateException if missing
AgentStateStore stateStore = new MysqlAgentStateStore(ds, false);   // explicit
```

## 사용자 지정 데이터베이스 / 테이블 이름

```java
AgentStateStore stateStore = new MysqlAgentStateStore(
    ds,
    "agentscope_prod",        // database name
    "session_state",          // table name
    true                      // auto-create
);
```

SQL 인젝션을 방지하기 위해 데이터베이스와 테이블 이름은 `[a-zA-Z_][a-zA-Z0-9_-]*`와 일치해야 하며 64자 이하여야 합니다.

## 스키마

`createIfNotExist=true`일 때, 테이블은 자동으로 생성됩니다:

```sql
CREATE TABLE IF NOT EXISTS agentscope_sessions (
    session_id VARCHAR(255) NOT NULL,
    state_key  VARCHAR(255) NOT NULL,
    item_index INT NOT NULL DEFAULT 0,
    state_data LONGTEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id, state_key, item_index)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

- `(userId, sessionId)` 쌍은 `session_id` 컬럼에 `{userSegment}:{sessionId}` 형태로 포장됩니다 (`userSegment` = `userId`, 익명 세션의 경우 `__anon__`).
- 단일 값: `item_index = 0`
- 리스트: `item_index = 0, 1, 2, ...` — 항목당 한 행이며, 변경 감지를 위해 `state_key='xxx:_hash'`인 추가 행이 사용됩니다.

## 직접 API 사용

`MysqlAgentStateStore`는 `AgentStateStore` 인터페이스를 구현합니다:

```java
// Single value (userId may be null for anonymous sessions)
stateStore.save("user-42", "session-1", "agent_state", state);
Optional<MyState> got = stateStore.get("user-42", "session-1", "agent_state", MyState.class);

// List (append-only growth; full rewrite on change)
stateStore.save("user-42", "session-1", "messages", listOfMessages);
List<MyState> all = stateStore.getList("user-42", "session-1", "messages", MyState.class);

// Maintenance
boolean exists = stateStore.exists("user-42", "session-1");
stateStore.delete("user-42", "session-1");
Set<String> sessions = stateStore.listSessionIds("user-42");   // null lists anonymous sessions

// Cleanup (use with care, test/ops only)
stateStore.truncateAllSessions();
```

## 설정

| 생성자 / 파라미터 | 참고 |
| --- | --- |
| `dataSource` | 필수. 권장: HikariCP / Druid 풀 |
| `databaseName` | 기본값 `agentscope` |
| `tableName` | 기본값 `agentscope_sessions` |
| `createIfNotExist` | `true`이면 `CREATE DATABASE` + `CREATE TABLE`을 자동으로 실행 |

> `truncateAllSessions()`는 `TRUNCATE TABLE`을 실행하며 DROP 권한이 필요합니다. DDL은 롤백할 수 없으므로 테스트나 운영 정리 용도로만 사용하세요.
