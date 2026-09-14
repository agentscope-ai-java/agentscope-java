# agentscope-extensions-redis

Redis-backed distributed implementations for AgentScope Java: `AgentStateStore` (agent
session state), `BaseStore` (workspace filesystem KV), `SandboxSnapshotSpec` (sandbox
snapshot archives), and `SandboxExecutionGuard` (distributed sandbox locking). Add this
module when you deploy `HarnessAgent` across multiple replicas and want a single Redis
instance to back all shared state — `RedisDistributedStore` wires all four components
from one client with one call. The module ships three interchangeable Redis client
adapters (Jedis, Lettuce, Redisson) so you can reuse whichever client is already on your
classpath instead of pulling in a second one.

## Installation

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-redis</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## Quick Start

`RedisDistributedStore.fromJedis(...)` is the recommended entry point — it configures
all four components from a single Jedis connection:

```java
import io.agentscope.extensions.redis.RedisDistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import redis.clients.jedis.JedisPooled;

JedisPooled jedis = new JedisPooled("localhost", 6379);
RedisDistributedStore store = RedisDistributedStore.fromJedis(jedis);

HarnessAgent agent = HarnessAgent.builder()
    .name("my-agent")
    .model("dashscope:qwen-plus")
    .distributedStore(store)
    .filesystem(new RemoteFilesystemSpec()) // baseStore auto-injected from distributedStore
    .build();
```

`RedisDistributedStore.fromJedis(jedis, keyPrefix)` overloads with a custom key prefix
(default `"agentscope:"`); the store appends `session:`, `store:`, `snapshot:`, and
`guard:` under it for the four components respectively.

### Using only the state store

If you just need cross-replica `AgentStateStore` persistence (no distributed sandbox
locking, workspace KV, or snapshots), build `RedisAgentStateStore` directly instead of
the full `RedisDistributedStore` facade:

```java
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import redis.clients.jedis.JedisPooled;

JedisPooled jedis = new JedisPooled("localhost", 6379);

AgentStateStore stateStore = RedisAgentStateStore.builder()
    .jedisClient(jedis)
    .keyPrefix("myapp:session:")
    .build();

HarnessAgent agent = HarnessAgent.builder()
    .name("my-agent")
    .model("dashscope:qwen-plus")
    .stateStore(stateStore)
    .build();
```

## Architecture

### Three pluggable client adapters

`RedisAgentStateStore` — the module's unified session store — does not talk to any
Redis client directly. It delegates every operation to a `RedisClientAdapter`, a small
internal interface (`set`/`get`, list ops, set ops, key ops, `evalScript`, `close`) that
each client library implements once:

| Client | Adapter | Deployment modes supported |
|--------|---------|------------------------------|
| [Jedis](https://github.com/redis/jedis) | `JedisClientAdapter` | Standalone, Cluster, Sentinel — any `UnifiedJedis` subtype |
| [Lettuce](https://github.com/redis/lettuce) | `LettuceClientAdapter` | Standalone/Sentinel (`RedisClient`), Cluster (`RedisClusterClient`) |
| [Redisson](https://github.com/redisson/redisson) | `RedissonClientAdapter` | Standalone, Cluster, Sentinel, Master/Slave — unified via `RedissonClient` |

`RedisAgentStateStore.Builder` picks the adapter for you based on which setter you call:

```java
RedisAgentStateStore.builder().jedisClient(unifiedJedis).build();      // -> JedisClientAdapter
RedisAgentStateStore.builder().lettuceClient(redisClient).build();     // -> LettuceClientAdapter
RedisAgentStateStore.builder().lettuceClusterClient(clusterClient)     // -> LettuceClientAdapter
        .build();
RedisAgentStateStore.builder().redissonClient(redissonClient).build(); // -> RedissonClientAdapter
```

Only one client should be set per builder. Pick whichever client your application
already depends on for other purposes — there is no functional difference in what
`RedisAgentStateStore` does on top of it. `RedisDistributedStore` (the one-line facade
used in Quick Start) is built on Jedis specifically, because `RedisStore`,
`RedisSandboxExecutionGuard`, and `RedisRemoteSnapshotClient` — the workspace KV,
sandbox lock, and snapshot components it also wires — are implemented directly against
`UnifiedJedis` rather than through the adapter abstraction. If you need those three
components on Lettuce or Redisson, assemble a `DistributedStore` by hand from
`RedisAgentStateStore` plus your own implementations, or via
`DistributedStore.builder()` mixing Redis with another module's components.

You can also implement `RedisClientAdapter` yourself and pass it via
`RedisAgentStateStore.builder().clientAdapter(myAdapter)` to plug in a client this
module doesn't cover.

### Key layout

`RedisAgentStateStore` (and the deprecated single-client `JedisAgentStateStore` /
`RedissonAgentStateStore`, which predate the adapter abstraction and are kept only for
backward compatibility) store session state under:

```
{prefix}{userId}/{sessionId}:{stateKey}         — String, JSON-encoded single value
{prefix}{userId}/{sessionId}:{stateKey}:list     — List, JSON-encoded items
{prefix}{userId}/{sessionId}:{stateKey}:list:_hash — Hash for incremental-append detection
{prefix}{userId}/{sessionId}:_keys               — Set tracking every key written for the session
```

Versioned saves (`saveIfVersion`) run as a single Lua script (`EVAL`) so the
version-check-and-write is atomic, making the store safe for concurrent writers across
replicas. `RedisStore` (the `BaseStore` implementation) uses the same atomic-script
pattern for its `putIfVersion` compare-and-swap.

### Sandbox locking and snapshots

`RedisSandboxExecutionGuard` implements `SandboxExecutionGuard` with a `SET NX PX` lease
per isolation key, released via a Lua CAS script that only deletes the key if it still
holds the caller's token — so a lease that outlived its TTL can't be released out from
under a new holder. `RedisRemoteSnapshotClient` stores sandbox snapshot archives as
plain Redis binary values (`{prefix}{snapshotId}.tar`), with an optional TTL.

## Testing

```bash
mvn -pl agentscope-extensions/agentscope-extensions-redis -am test
```
