```{note}
このページは [分散ストレージ — Redis](../distributed/redis.md) に統合されました。以下の内容は参考のために残しています。
```

# Redis ステートストア

`agentscope-extensions-redis` は AgentScope のエージェント状態を Redis に永続化します。統一された `RedisClientAdapter` が **Jedis、Lettuce、Redisson** を抽象化し、Standalone、Cluster、Sentinel のデプロイモードをカバーします。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-redis</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

このモジュールは特定の Redis クライアントに固定されていません — 既に使用しているもの（Jedis / Lettuce / Redisson）をそのまま利用できます。

## クイックスタート（Lettuce、スタンドアロン）

```java
import io.lettuce.core.RedisClient;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;

RedisClient redisClient = RedisClient.create("redis://localhost:6379");

AgentStateStore stateStore = RedisAgentStateStore.builder()
    .lettuceClient(redisClient)
    .build();
```

## 各クライアントの接続

### Jedis

```java
import redis.clients.jedis.UnifiedJedis;

UnifiedJedis jedis = new redis.clients.jedis.JedisPooled("localhost", 6379);
AgentStateStore stateStore = RedisAgentStateStore.builder()
    .jedisClient(jedis)   // UnifiedJedis、JedisCluster、JedisSentineled のいずれも使用可能
    .build();
```

### Lettuce クラスター

```java
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.RedisURI;

RedisClusterClient clusterClient = RedisClusterClient.create(
    RedisURI.create("redis://localhost:7000"));

AgentStateStore stateStore = RedisAgentStateStore.builder()
    .lettuceClusterClient(clusterClient)
    .build();
```

### Redisson

```java
import org.redisson.Redisson;
import org.redisson.config.Config;

Config config = new Config();
config.useSingleServer().setAddress("redis://localhost:6379");
RedissonClient redisson = Redisson.create(config);

AgentStateStore stateStore = RedisAgentStateStore.builder()
    .redissonClient(redisson)
    .build();
```

> Redisson は `useClusterServers()` / `useSentinelServers()` / `useMasterSlaveServers()` もサポートしています。生成された `RedissonClient` を `redissonClient(...)` に渡してください。

## カスタムキープレフィックス

デフォルトでは、すべてのキーは `agentscope:session:{userSegment}/{sessionId}:...`（`userSegment` は `userId`、または匿名セッションの場合は `__anon__`）という形式になります。複数のプロジェクトで同じ Redis を共有する場合は、これを上書きしてください:

```java
AgentStateStore stateStore = RedisAgentStateStore.builder()
    .lettuceClient(redisClient)
    .keyPrefix("myapp:session:")
    .build();
```

## キーのレイアウト

`(userId, sessionId)` のペアは、単一のスロット ID `{userSegment}/{sessionId}`（`userSegment` は `userId`、`userId` が null の場合は `__anon__`）にパックされます。

| 種類 | キーパターン |
| --- | --- |
| 単一値 | `{prefix}{userSegment}/{sessionId}:{stateKey}`（Redis String、JSON 値） |
| リスト | `{prefix}{userSegment}/{sessionId}:{stateKey}:list`（Redis List、要素ごとに1つの JSON アイテム） |
| リストハッシュ | `{prefix}{userSegment}/{sessionId}:{stateKey}:list:_hash`（変更検出用） |
| セッションインデックス | `{prefix}{userSegment}/{sessionId}:_keys`（すべての stateKey を追跡する Redis Set） |

`_keys` インデックスにより、`KEYS *` を使用せずに `delete(userId, sessionId)` と `exists(userId, sessionId)` を O(1) で実行できます。

## エージェントへの接続

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model(model)
    .stateStore(stateStore)
    .build();
```

これ以降、Memory、Workspace、Plan などは自動的に Redis を介して永続化されます。各呼び出しが読み書きするスロットは、呼び出しごとに `RuntimeContext` から選択されます:

```java
RuntimeContext rc = RuntimeContext.builder()
    .userId("alice")
    .sessionId("session-1")
    .build();

agent.call(msg, rc).block();
```

## カスタムアダプター

Redis 互換ストア（KeyDB、Tair など）を対象とする場合は、`RedisClientAdapter` を実装し、`clientAdapter(...)` 経由で注入してください:

```java
AgentStateStore stateStore = RedisAgentStateStore.builder()
    .clientAdapter(new MyCustomAdapter(...))
    .build();
```

## ビルダーリファレンス

| メソッド | 説明 |
| --- | --- |
| `jedisClient(UnifiedJedis)` | Jedis スタンドアロン / クラスター / センチネル |
| `lettuceClient(RedisClient)` | Lettuce スタンドアロン / センチネル |
| `lettuceClusterClient(RedisClusterClient)` | Lettuce クラスター |
| `redissonClient(RedissonClient)` | Redisson、任意のデプロイモード |
| `clientAdapter(RedisClientAdapter)` | カスタムアダプター |
| `keyPrefix(String)` | デフォルト `agentscope:session:` |

> クライアントのセッターは互いに排他的です — いずれか1つのみを設定してください。
