# MySQL / JDBC

`agentscope-extensions-mysql` は、既存のリレーショナルデータベース基盤を持つチーム向けに、フルスタックな JDBC ベースの分散ストレージを提供します。

## 依存関係

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-mysql</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

データベースドライバ（例: `mysql-connector-j`、`postgresql`）は別途追加してください。

## 1 行セットアップ

```java
import io.agentscope.extensions.mysql.MysqlDistributedStore;

DataSource dataSource = ...;  // HikariCP、Druid など
DistributedStore store = MysqlDistributedStore.create(dataSource);

HarnessAgent agent = HarnessAgent.builder()
    .distributedStore(store)
    .filesystem(new RemoteFilesystemSpec()
            .isolationScope(IsolationScope.USER))
    .build();
```

## 提供されるコンポーネント

### 1. MysqlAgentStateStore

エージェントの状態を MySQL のテーブルに永続化します。

```java
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;

AgentStateStore store = new MysqlAgentStateStore(dataSource, true);  // スキーマを自動作成
AgentStateStore store = new MysqlAgentStateStore(
    dataSource, "agentscope_prod", "session_state", true);  // DB/テーブル名をカスタム指定
```

### 2. JdbcStore（BaseStore）

方言を自動検出するワークスペースファイルシステム KV ストレージです。

```java
import io.agentscope.extensions.mysql.store.JdbcStore;

BaseStore store = JdbcStore.builder(dataSource)
    .initializeSchema(true)
    .build();
```

対応する方言（自動検出）: MySQL、PostgreSQL、H2、SQLite。

### 3. JdbcSnapshotSpec

サンドボックスのスナップショットをデータベースのテーブルに LONGBLOB として保存します。

```java
import io.agentscope.extensions.mysql.snapshot.JdbcSnapshotSpec;

SandboxSnapshotSpec spec = new JdbcSnapshotSpec(dataSource);
```

### 4. JdbcSandboxExecutionGuard

MySQL の `GET_LOCK()` / `RELEASE_LOCK()` による分散ロックです。

```java
import io.agentscope.extensions.mysql.sandbox.JdbcSandboxExecutionGuard;

SandboxExecutionGuard guard = JdbcSandboxExecutionGuard.builder(dataSource)
    .keyPrefix("myapp:lock:")
    .lockTimeout(Duration.ofMinutes(30))
    .build();
```

ロックは JDBC の接続に紐づいており、接続がクローズされると自動的に解放されます。

## 使い分けの目安

| シナリオ | 推奨 |
|----------|---------------|
| 既存の MySQL があり、Redis を使いたくない | **第一選択**: MySQL |
| SQL による監査 / レポート / join が必要 | MySQL |
| 大きなスナップショット（100MB 超） | MySQL の BLOB でも動作するが OSS も検討 |
| 最も低いレイテンシ | Redis |
