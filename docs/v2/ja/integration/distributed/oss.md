---
title: Alibaba Cloud OSS
---

`agentscope-extensions-oss` は Alibaba Cloud Object Storage Service（OSS）を利用した分散ストレージを提供し、大容量データや Alibaba Cloud のエコシステムに最適です。

## 依存関係

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-oss</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 1 行セットアップ

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

## 提供されるコンポーネント

### 1. OssAgentStateStore

エージェントの状態を OSS のオブジェクトに永続化します。

### 2. OssBaseStore

ワークスペースファイルシステムの KV ストレージを OSS のオブジェクトに保存します。

### 3. OssSnapshotSpec

サンドボックスのスナップショットを OSS に保存します — 大きなワークスペースアーカイブに最適な選択肢です。

### 提供されないもの: SandboxExecutionGuard

オブジェクトストレージは分散ロックに適していません。Redis のガードを組み合わせてください。

```java
DistributedStore ossStore = OssDistributedStore.create(ossClient, "my-bucket", "agentscope/");

DistributedStore mixed = DistributedStore.builder()
    .agentStateStore(ossStore.agentStateStore())
    .baseStore(ossStore.baseStore())
    .sandboxSnapshotSpec(ossStore.sandboxSnapshotSpec())
    .sandboxExecutionGuard(RedisDistributedStore.fromJedis(jedis).sandboxExecutionGuard())
    .build();
```

## 使い分けの目安

| シナリオ | 推奨 |
|----------|---------------|
| 大きなスナップショット（100MB 超のワークスペース） | **第一選択**: OSS |
| Alibaba Cloud のエコシステム | OSS |
| サンドボックス並行処理ロックが必要 | OSS + Redis の組み合わせ |
| 最も低いレイテンシ | Redis |

## セキュリティ

- 本番環境では RAM Role + STS の一時的な認証情報を使用し、AK/SK のハードコードは避けてください
- ストレージコストを抑えるため、バケットのライフサイクルルール（例: 7 日で自動失効）を設定してください
