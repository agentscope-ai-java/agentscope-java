---
title: Redis
---

`agentscope-extensions-redis` は、フルスタックの Redis 分散ストレージを提供します — マルチレプリカの本番デプロイに推奨されるストアです。

## 依存関係

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-redis</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

このモジュールは特定の Redis クライアントを強制しません — 使用しているもの（Jedis / Lettuce / Redisson）をインポートしてください。

## ワンライン セットアップ

```java
import io.agentscope.extensions.redis.RedisDistributedStore;

JedisPooled jedis = new JedisPooled("redis://localhost:6379");
DistributedStore store = RedisDistributedStore.fromJedis(jedis);

HarnessAgent agent = HarnessAgent.builder()
    .distributedStore(store)
    .filesystem(new RemoteFilesystemSpec()
            .isolationScope(IsolationScope.USER))
    .build();
```

マルチ環境分離のためのカスタムキープレフィックス:

```java
DistributedStore store = RedisDistributedStore.fromJedis(jedis, "prod:");
```

## 提供されるコンポーネント

### 1. RedisAgentStateStore

Agent の状態を Redis に永続化します。Jedis / Lettuce / Redisson をサポートします。

```java
import io.agentscope.extensions.redis.state.RedisAgentStateStore;

// Jedis
AgentStateStore store = RedisAgentStateStore.builder()
    .jedisClient(new JedisPooled("redis://localhost:6379"))
    .keyPrefix("myapp:session:")
    .build();

// Lettuce Cluster
AgentStateStore store = RedisAgentStateStore.builder()
    .lettuceClusterClient(RedisClusterClient.create(RedisURI.create("localhost", 7000)))
    .build();

// Redisson
Config config = new Config();
config.useSingleServer().setAddress("redis://localhost:6379");
AgentStateStore store = RedisAgentStateStore.builder()
    .redissonClient(Redisson.create(config))
    .build();
```

### 2. RedisStore (BaseStore)

`RemoteFilesystemSpec` 向けのワークスペースファイルシステム KV ストレージ。

```java
import io.agentscope.extensions.redis.store.RedisStore;

BaseStore store = new RedisStore(jedis);
BaseStore store = new RedisStore(jedis, "myapp:store:");
```

並行性安全: `put` / `putIfVersion` は原子性のために Lua スクリプトを使用します。

### 3. RedisSnapshotSpec

Redis のバイナリキーとして保存されるサンドボックススナップショット。小さなワークスペース + 短い TTL に最適です。

```java
import io.agentscope.extensions.redis.snapshot.RedisSnapshotSpec;

SandboxSnapshotSpec spec = new RedisSnapshotSpec(jedis, "myapp:snapshot:", 3600);
```

### 4. RedisSandboxExecutionGuard

マルチレプリカのサンドボックス同時実行制御のための、Redis の `SET NX PX` によるリースベース分散ロック。

```java
import io.agentscope.extensions.redis.sandbox.RedisSandboxExecutionGuard;

SandboxExecutionGuard guard = RedisSandboxExecutionGuard.builder(jedis)
    .keyPrefix("myapp:guard:")
    .leaseTtl(Duration.ofMinutes(30))
    .retryInterval(Duration.ofMillis(500))
    .build();
```

## 使いどころ

| シナリオ | 推奨 |
|----------|---------------|
| マルチレプリカの本番環境、低レイテンシ | **第一選択**: Redis |
| 既存の Redis クラスター | Lettuce Cluster または Redisson Sentinel |
| 小さなワークスペース + 短い TTL のスナップショット | Redis のスナップショットで動作するが、メモリに注意 |
| 大きなワークスペースのスナップショット | 混在ストア: 状態/ロックには Redis、スナップショットには OSS |
