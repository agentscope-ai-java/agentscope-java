---
title: エージェントステートストア (AgentStateStore)
---

<Note>

**推奨: 1行のセットアップには [DistributedStore](/v2/ja/integration/distributed/index) を使用してください** — AgentStateStore、BaseStore、SandboxSnapshotSpec、SandboxExecutionGuard をまとめてカバーします。AgentStateStore を個別に設定する必要がある場合のみ、以下をお読みください。

</Note>

`io.agentscope.core.state.AgentStateStore` は、AgentScope がエージェントの状態を永続化するために使用するインターフェースです — Memory、Workspace、Plan などのコンポーネントは `State` オブジェクトとしてシリアライズされ、`AgentStateStore` を介して保存されます。これにより、再起動時の復旧やノード間での共有が可能になります。

状態は `(userId, sessionId)` によってアドレス指定されます:

- `sessionId` — 必須、空白不可。セッションを識別します。
- `userId` — 任意。`null` は匿名 / シングルテナント（CLI、テストなど）を意味します。

## 利用可能な実装

| 実装 | モジュール | 使用場面 |
| --- | --- | --- |
| `InMemoryAgentStateStore` | `agentscope-core` | ユニットテスト |
| `JsonFileAgentStateStore` | `agentscope-core` | シングルノード開発（**HarnessAgent のデフォルト**） |
| `RedisAgentStateStore` | `agentscope-extensions-redis` | [マルチレプリカ本番環境のデフォルト](/v2/ja/integration/distributed/redis) |
| `MysqlAgentStateStore` | `agentscope-extensions-mysql` | [既存のデータベースインフラ](/v2/ja/integration/distributed/mysql) |
| `OssAgentStateStore` | `agentscope-extensions-oss` | [Alibaba Cloud エコシステム](/v2/ja/integration/distributed/oss) |

## スタンドアロン設定

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model(model)
    .stateStore(stateStore)   // 任意の AgentStateStore 実装
    .build();
```

詳細な使用方法とコード例については、各ストアのドキュメントを参照してください:

- [Redis](/v2/ja/integration/distributed/redis#1-redisagentstatestore)
- [MySQL](/v2/ja/integration/distributed/mysql#1-mysqlagentstatestore)
- [OSS](/v2/ja/integration/distributed/oss#1-ossagentstatestore)
