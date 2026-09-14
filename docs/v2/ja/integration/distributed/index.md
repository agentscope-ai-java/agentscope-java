# 分散ストレージ（Distributed Store）

AgentScope は、分散永続化が必要なすべてのコンポーネントを `DistributedStore` インターフェースの下に統合します。1 行の設定で、エージェントの状態、ワークスペースファイルシステム、サンドボックスのスナップショット、並行処理ロックを同じ分散ストアに切り替えられます。

## クイックスタート

```java
// Redis — 1 行セットアップ
DistributedStore store = RedisDistributedStore.fromJedis(
        new JedisPooled("redis://localhost:6379"));

HarnessAgent agent = HarnessAgent.builder()
    .name("my-agent")
    .model("dashscope:qwen-plus")
    .distributedStore(store)
    .filesystem(new RemoteFilesystemSpec()            // baseStore は自動注入される
            .isolationScope(IsolationScope.USER))
    .build();
```

## 機能マトリクス

| コンポーネント | インターフェース | Redis | OSS | MySQL |
|-----------|----------|:-----:|:---:|:-----:|
| エージェント状態の永続化 | `AgentStateStore` | `RedisAgentStateStore` | `OssAgentStateStore` | `MysqlAgentStateStore` |
| ワークスペースファイルシステム KV | `BaseStore` | `RedisStore` | `OssBaseStore` | `JdbcStore` |
| サンドボックスのスナップショット | `SandboxSnapshotSpec` | `RedisSnapshotSpec` | `OssSnapshotSpec` | `JdbcSnapshotSpec` |
| サンドボックス並行処理ロック | `SandboxExecutionGuard` | `RedisSandboxExecutionGuard` | — | `JdbcSandboxExecutionGuard` |

> OSS は `SandboxExecutionGuard` を提供しません — オブジェクトストレージは分散ロックに適していません。`DistributedStore.builder()` を使って Redis のガードを組み合わせてください。

## 複数ストアの組み合わせ

異なるコンポーネントを異なるストレージストアから組み合わせることができます。

```java
DistributedStore mysql = MysqlDistributedStore.create(dataSource);
DistributedStore redis = RedisDistributedStore.fromJedis(jedis);

// 状態とファイルは MySQL、サンドボックスのロックとスナップショットは Redis
DistributedStore mixed = DistributedStore.builder()
    .agentStateStore(mysql.agentStateStore())
    .baseStore(mysql.baseStore())
    .sandboxSnapshotSpec(redis.sandboxSnapshotSpec())
    .sandboxExecutionGuard(redis.sandboxExecutionGuard())
    .build();

HarnessAgent.builder()
    .distributedStore(mixed)
    .filesystem(new DockerFilesystemSpec()
            .image("ubuntu:24.04"))
    .build();
```

## コンポーネント

### AgentStateStore — エージェント状態の永続化

`(userId, sessionId)` で識別される会話コンテキスト、圧縮サマリー、権限ルール、Plan Mode の状態です。`distributedStore` によって自動配線されます。`.stateStore(...)` で上書きすることもできます。

### BaseStore — ワークスペースファイルシステム KV

`RemoteFilesystemSpec` 向けのストレージプロバイダーで、`MEMORY.md`、`memory/`、`skills/`、`sessions/` を共有 KV ストレージにルーティングします。引数なしコンストラクタを使用する場合、`RemoteFilesystemSpec` に自動注入されます。

### SandboxSnapshotSpec — サンドボックスのスナップショット

Docker/K8s サンドボックスのワークスペースを tar アーカイブとして永続化し、呼び出しをまたいだ復旧を可能にします。`distributedStore` によって `SandboxFilesystemSpec` に自動配線されます。

### SandboxExecutionGuard — サンドボックス並行処理ロック

マルチレプリカ構成での `AGENT` / `GLOBAL` 分離スコープ向けの分散ロックです。`distributedStore` によって `SandboxFilesystemSpec` に自動配線されます。

## 優先順位

```
明示的な builder メソッド (.stateStore()、FilesystemSpec の .snapshotSpec() など)
    > distributedStore の自動配線
        > ローカルのデフォルト (JsonFileAgentStateStore、NoopSnapshotSpec など)
```

## ストアのドキュメント

- [Redis](redis.md) — 全機能をカバー、マルチレプリカの本番環境に推奨
- [MySQL / JDBC](mysql.md) — 既存のリレーショナルデータベース基盤向け
- [Alibaba Cloud OSS](oss.md) — オブジェクトストレージ、大容量スナップショットに最適

## aistio ホスト型ストア

すでに aistio コントロールプレーンを運用している場合、`DistributedStore` の協調部分（BaseStore、サンドボックスのロック/スナップショット、MessageBus、AsyncToolRegistry、**TaskRepository**、任意の **SessionTurnGate**）をホストさせることができます。**`AgentStateStore`** バックエンドは引き続き自分で 1 つ用意する必要があります（Redis / MySQL / Postgres / OSS）。core は `getVersioned` / `saveIfVersion` による楽観的並行制御を公開しますが、状態ストレージはコントロールプレーンには置かれません。

```java
ControlPlaneStores cp = ControlPlaneStores.fromEnv();
HarnessAgent.builder()
    .distributedStore(cp.withAgentStateStore(redis.agentStateStore()))
    .filesystem(new RemoteFilesystemSpec().isolationScope(IsolationScope.USER))
    .build();
```

- コントロールプレーンで `--enable-hosted-store` を指定して有効化します（本番環境では Postgres を推奨）。
- **`withAgentStateStore` にはホストされた** `TaskRepository` と `SessionTurnGate` が含まれます。**`SandboxFilesystemSpec` とサブエージェントのバックグラウンドタスク**を使う場合はこの方式を使ってください — ワークスペース側の `TaskRepository` はレプリカをまたいでタスクを永続化できません。
- **AgentStateStore のバージョニング**: Redis、Postgres、MySQL、InMemory は CAS をサポートします。JsonFile、OSS、COS、JPA は引き続き last-writer-wins です。マルチレプリカ構成ではバージョニングをサポートするバックエンドを優先してください。
- **ターンゲートと `ConflictPolicy.FAIL`** は任意です。マルチレプリカ構成での LLM ターンの重複を減らしますが、正しさはバックエンドがバージョニングをサポートしている場合の CAS に由来します。
- 現在の認証は共有の内部トークンによるもので、テナント（`agentName` / `namespace`）はリクエストボディから取得されます — 1 つのコントロールプレーン上で互いを信頼しないマルチテナントのエージェントには**使用しないでください**。
- `MessageBus.queueDrain` は **破壊的**（読み取り時に ack）です。テナントのキーを誤るとメッセージが失われます。
