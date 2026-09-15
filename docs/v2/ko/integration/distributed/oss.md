# Alibaba Cloud OSS

`agentscope-extensions-oss`는 Alibaba Cloud Object Storage Service(OSS)를 기반으로 하는 분산 스토리지를 제공하며, 대용량 데이터와 Alibaba Cloud 생태계에 이상적입니다.

## 의존성

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-oss</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 한 줄 설정

```java
import io.agentscope.extensions.oss.OssDistributedStore;

OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
DistributedStore store = OssDistributedStore.create(ossClient, "my-bucket", "agentscope/");

HarnessAgent agent = HarnessAgent.builder()
    .distributedStore(store)
    .filesystem(new RemoteFilesystemSpec()
            .isolationScope(IsolationScope.USER))
    .build();
```

## 제공되는 컴포넌트

### 1. OssAgentStateStore

에이전트 상태를 OSS 오브젝트에 영속화합니다.

### 2. OssBaseStore

워크스페이스 파일 시스템 KV 스토리지를 OSS 오브젝트로 저장합니다.

### 3. OssSnapshotSpec

샌드박스 스냅샷을 OSS에 저장합니다 — 대용량 워크스페이스 아카이브에 가장 적합한 선택입니다.

### 제공되지 않음: SandboxExecutionGuard

오브젝트 스토리지는 분산 락에 적합하지 않습니다. Redis 가드를 혼합하여 사용하세요.

```java
DistributedStore ossStore = OssDistributedStore.create(ossClient, "my-bucket", "agentscope/");

DistributedStore mixed = DistributedStore.builder()
    .agentStateStore(ossStore.agentStateStore())
    .baseStore(ossStore.baseStore())
    .sandboxSnapshotSpec(ossStore.sandboxSnapshotSpec())
    .sandboxExecutionGuard(RedisDistributedStore.fromJedis(jedis).sandboxExecutionGuard())
    .build();
```

## 사용 시점

| 시나리오 | 권장 사항 |
|----------|---------------|
| 대용량 스냅샷(워크스페이스 >100MB) | **1순위**: OSS |
| Alibaba Cloud 생태계 | OSS |
| 샌드박스 동시성 락이 필요한 경우 | OSS + Redis 혼합 |
| 최저 지연 시간 | Redis |

## 보안

- 프로덕션에서는 하드코딩된 AK/SK를 피하고 RAM Role + STS 임시 자격 증명을 사용하세요
- 버킷 수명 주기 규칙(예: 7일 자동 만료)을 설정하여 스토리지 비용을 관리하세요
