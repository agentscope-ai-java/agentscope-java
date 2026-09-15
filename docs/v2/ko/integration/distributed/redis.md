# Redis

`agentscope-extensions-redis`는 풀스택 Redis 분산 스토리지를 제공합니다 — 다중 레플리카 프로덕션 배포에 권장되는 스토어입니다.

## 의존성

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-redis</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

이 모듈은 특정 Redis 클라이언트를 강제하지 않습니다 — 사용 중인 것(Jedis / Lettuce / Redisson)을 import 하세요.

## 한 줄 설정

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

다중 환경 격리를 위한 커스텀 키 프리픽스:

```java
DistributedStore store = RedisDistributedStore.fromJedis(jedis, "prod:");
```

## 제공되는 컴포넌트

### 1. RedisAgentStateStore

에이전트 상태를 Redis에 영속화합니다. Jedis / Lettuce / Redisson을 지원합니다.

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

`RemoteFilesystemSpec`을 위한 워크스페이스 파일 시스템 KV 스토리지입니다.

```java
import io.agentscope.extensions.redis.store.RedisStore;

BaseStore store = new RedisStore(jedis);
BaseStore store = new RedisStore(jedis, "myapp:store:");
```

동시성 안전: `put` / `putIfVersion`은 원자성을 위해 Lua 스크립트를 사용합니다.

### 3. RedisSnapshotSpec

샌드박스 스냅샷을 Redis 바이너리 키로 저장합니다. 작은 워크스페이스와 짧은 TTL에 가장 적합합니다.

```java
import io.agentscope.extensions.redis.snapshot.RedisSnapshotSpec;

SandboxSnapshotSpec spec = new RedisSnapshotSpec(jedis, "myapp:snapshot:", 3600);
```

### 4. RedisSandboxExecutionGuard

다중 레플리카 샌드박스 동시성 제어를 위한 Redis `SET NX PX` 리스(lease) 기반 분산 락입니다.

```java
import io.agentscope.extensions.redis.sandbox.RedisSandboxExecutionGuard;

SandboxExecutionGuard guard = RedisSandboxExecutionGuard.builder(jedis)
    .keyPrefix("myapp:guard:")
    .leaseTtl(Duration.ofMinutes(30))
    .retryInterval(Duration.ofMillis(500))
    .build();
```

## 사용 시점

| 시나리오 | 권장 사항 |
|----------|---------------|
| 다중 레플리카 프로덕션, 낮은 지연 시간 | **1순위**: Redis |
| 기존 Redis 클러스터 | Lettuce Cluster 또는 Redisson Sentinel |
| 작은 워크스페이스 + 짧은 TTL 스냅샷 | Redis 스냅샷도 동작하지만 메모리를 주시하세요 |
| 대용량 워크스페이스 스냅샷 | 혼합 스토어: 상태/락은 Redis, 스냅샷은 OSS |
