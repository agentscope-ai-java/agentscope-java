---
title: Redis 상태 저장소
---

<Note>

이 페이지는 [분산 스토리지 — Redis](/v2/ko/integration/distributed/redis)로 대체되었습니다. 아래 내용은 참고용으로 남겨둡니다.

</Note>

`agentscope-extensions-redis`는 AgentScope 에이전트 상태를 Redis에 영속화합니다. 통합된 `RedisClientAdapter`는 **Jedis, Lettuce, Redisson**을 추상화하며, Standalone, Cluster, Sentinel 배포 모드를 모두 지원합니다.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-redis</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

이 모듈은 특정 Redis 클라이언트를 강제하지 않습니다 — 이미 사용 중인 것(Jedis / Lettuce / Redisson)을 그대로 사용하면 됩니다.

## 빠른 시작 (Lettuce, standalone)

```java
import io.lettuce.core.RedisClient;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;

RedisClient redisClient = RedisClient.create("redis://localhost:6379");

AgentStateStore stateStore = RedisAgentStateStore.builder()
    .lettuceClient(redisClient)
    .build();
```

## 클라이언트별 연결

### Jedis

```java
import redis.clients.jedis.UnifiedJedis;

UnifiedJedis jedis = new redis.clients.jedis.JedisPooled("localhost", 6379);
AgentStateStore stateStore = RedisAgentStateStore.builder()
    .jedisClient(jedis)   // UnifiedJedis, JedisCluster, JedisSentineled all work
    .build();
```

### Lettuce cluster

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

> Redisson은 `useClusterServers()` / `useSentinelServers()` / `useMasterSlaveServers()`도 지원합니다. 결과로 얻은 `RedissonClient`를 `redissonClient(...)`에 전달하세요.

## 사용자 지정 키 프리픽스

기본적으로 모든 키는 `agentscope:session:{userSegment}/{sessionId}:...` 형태입니다 (`userSegment`는 `userId`이며, 익명 세션의 경우 `__anon__`입니다). 여러 프로젝트가 동일한 Redis를 공유하는 경우 다음과 같이 재정의하세요:

```java
AgentStateStore stateStore = RedisAgentStateStore.builder()
    .lettuceClient(redisClient)
    .keyPrefix("myapp:session:")
    .build();
```

## 키 구성

`(userId, sessionId)` 쌍은 단일 슬롯 id `{userSegment}/{sessionId}`로 포장됩니다 (`userSegment` = `userId`, `userId`가 null이면 `__anon__`).

| 유형 | 키 패턴 |
| --- | --- |
| 단일 값 | `{prefix}{userSegment}/{sessionId}:{stateKey}` (Redis String, JSON 값) |
| 리스트 | `{prefix}{userSegment}/{sessionId}:{stateKey}:list` (Redis List, 요소마다 하나의 JSON 항목) |
| 리스트 해시 | `{prefix}{userSegment}/{sessionId}:{stateKey}:list:_hash` (변경 감지) |
| 세션 인덱스 | `{prefix}{userSegment}/{sessionId}:_keys` (모든 stateKey를 추적하는 Redis Set) |

`_keys` 인덱스 덕분에 `KEYS *`가 필요 없이 `delete(userId, sessionId)`와 `exists(userId, sessionId)`가 O(1)로 동작합니다.

## 에이전트에 연결하기

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model(model)
    .stateStore(stateStore)
    .build();
```

이렇게 하면 Memory, Workspace, Plan 등이 자동으로 Redis를 통해 영속화됩니다. 각 호출이 읽고 쓰는 슬롯은 호출마다 `RuntimeContext`로부터 결정됩니다:

```java
RuntimeContext rc = RuntimeContext.builder()
    .userId("alice")
    .sessionId("session-1")
    .build();

agent.call(msg, rc).block();
```

## 사용자 지정 어댑터

Redis 호환 저장소(KeyDB, Tair 등)를 대상으로 하는 경우, `RedisClientAdapter`를 구현하고 `clientAdapter(...)`를 통해 주입하세요:

```java
AgentStateStore stateStore = RedisAgentStateStore.builder()
    .clientAdapter(new MyCustomAdapter(...))
    .build();
```

## 빌더 참조

| 메서드 | 참고 |
| --- | --- |
| `jedisClient(UnifiedJedis)` | Jedis standalone / cluster / sentinel |
| `lettuceClient(RedisClient)` | Lettuce standalone / sentinel |
| `lettuceClusterClient(RedisClusterClient)` | Lettuce cluster |
| `redissonClient(RedissonClient)` | Redisson, 모든 배포 모드 |
| `clientAdapter(RedisClientAdapter)` | 사용자 지정 어댑터 |
| `keyPrefix(String)` | 기본값 `agentscope:session:` |

> 클라이언트 세터는 상호 배타적입니다 — 정확히 하나만 설정하세요.
