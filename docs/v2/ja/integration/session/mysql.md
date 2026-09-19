---
title: MySQL ステートストア
---

<Note>

このページは [分散ストレージ — MySQL](/v2/ja/integration/distributed/mysql) に統合されました。以下の内容は参考のために残しています。

</Note>

`agentscope-extensions-mysql` は AgentScope のエージェント状態を MySQL に永続化します。既存の MySQL インフラがある場合や、トランザクション / SQL ベースでの状態データアクセスが必要な場合に適しています。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-mysql</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

対応する JDBC ドライバは自分で用意してください（例: `mysql:mysql-connector-j`）。

## クイックスタート

```java
import com.zaxxer.hikari.HikariDataSource;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;

HikariDataSource ds = new HikariDataSource();
ds.setJdbcUrl("jdbc:mysql://localhost:3306/agentscope?serverTimezone=UTC");
ds.setUsername("root");
ds.setPassword("***");

// 第2引数 createIfNotExist=true: データベースとテーブルを自動作成
AgentStateStore stateStore = new MysqlAgentStateStore(ds, true);

ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model(model)
    .stateStore(stateStore)
    .build();
```

データベースとテーブルが事前に作成されている場合は、より安全な形式を使用してください:

```java
AgentStateStore stateStore = new MysqlAgentStateStore(ds);          // 存在しない場合は IllegalStateException をスロー
AgentStateStore stateStore = new MysqlAgentStateStore(ds, false);   // 明示的に指定
```

## カスタムデータベース / テーブル名

```java
AgentStateStore stateStore = new MysqlAgentStateStore(
    ds,
    "agentscope_prod",        // データベース名
    "session_state",          // テーブル名
    true                      // 自動作成
);
```

データベース名とテーブル名は SQL インジェクションを避けるため、`[a-zA-Z_][a-zA-Z0-9_-]*` に一致し、64 文字以下である必要があります。

## スキーマ

`createIfNotExist=true` の場合、テーブルは自動的に作成されます:

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

- `(userId, sessionId)` のペアは `session_id` カラムに `{userSegment}:{sessionId}` の形式でパックされます（`userSegment` は `userId`、または匿名セッションの場合は `__anon__`）。
- 単一値: `item_index = 0`
- リスト: `item_index = 0, 1, 2, ...` — 項目ごとに1行。変更検出には `state_key='xxx:_hash'` を持つ追加の行が使用されます。

## 直接 API を使用する

`MysqlAgentStateStore` は `AgentStateStore` インターフェースを実装しています:

```java
// 単一値（匿名セッションでは userId は null でも可）
stateStore.save("user-42", "session-1", "agent_state", state);
Optional<MyState> got = stateStore.get("user-42", "session-1", "agent_state", MyState.class);

// リスト（追記のみで増加。変更時は全体を書き換え）
stateStore.save("user-42", "session-1", "messages", listOfMessages);
List<MyState> all = stateStore.getList("user-42", "session-1", "messages", MyState.class);

// メンテナンス
boolean exists = stateStore.exists("user-42", "session-1");
stateStore.delete("user-42", "session-1");
Set<String> sessions = stateStore.listSessionIds("user-42");   // null の場合は匿名セッションを一覧表示

// クリーンアップ（注意して使用。テスト/運用のみ）
stateStore.truncateAllSessions();
```

## 設定

| コンストラクタ / パラメータ | 説明 |
| --- | --- |
| `dataSource` | 必須。推奨: HikariCP / Druid プール |
| `databaseName` | デフォルト `agentscope` |
| `tableName` | デフォルト `agentscope_sessions` |
| `createIfNotExist` | `true` の場合、`CREATE DATABASE` + `CREATE TABLE` を自動的に実行 |

> `truncateAllSessions()` は `TRUNCATE TABLE` を発行し、DROP 権限が必要です。DDL はロールバックできないため、テストや運用のクリーンアップ専用としてください。
