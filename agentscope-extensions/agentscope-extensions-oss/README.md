# agentscope-extensions-oss

Alibaba Cloud OSS-backed implementation of AgentScope's `DistributedStore`: agent
state persistence (`AgentStateStore`), workspace filesystem KV (`BaseStore`), and
sandbox snapshot storage (`SandboxSnapshotSpec`), all as JSON/tar objects in an OSS
bucket. Add this module when you deploy `HarnessAgent` across multiple replicas on
Alibaba Cloud and need shared state without standing up Redis or a database.

## Installation

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-oss</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## Quick Start

`OssDistributedStore.create(...)` wires all three components — state store, base
store, and snapshot spec — from a single `OSS` client and bucket:

```java
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import io.agentscope.extensions.oss.OssDistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;

OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

HarnessAgent agent = HarnessAgent.builder()
    .name("my-agent")
    .model("dashscope:qwen-plus")
    .distributedStore(OssDistributedStore.create(ossClient, "my-bucket", "agentscope/"))
    .filesystem(new RemoteFilesystemSpec()) // baseStore auto-injected from distributedStore
    .build();
```

The `keyPrefix` argument (`"agentscope/"` above) namespaces every object OSS
stores for this agent; `OssDistributedStore` appends `state/`, `store/`, and
`snapshot/` under it for the three components respectively.

### Using only the state store

If you just need cross-replica `AgentStateStore` persistence, construct
`OssAgentStateStore` directly instead of the full `OssDistributedStore` facade:

```java
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.oss.OssAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;

AgentStateStore stateStore = OssAgentStateStore.builder()
    .ossClient(ossClient)
    .bucketName("my-agentscope-bucket")
    .keyPrefix("agentscope/state/")
    .build();

HarnessAgent agent = HarnessAgent.builder()
    .name("my-agent")
    .model("dashscope:qwen-plus")
    .stateStore(stateStore)
    .build();
```

### Mixing with other stores

`OssDistributedStore` does not implement `SandboxExecutionGuard` — object storage
is not suited to distributed locking. Combine it with a lock-capable store (e.g.
Redis) via `DistributedStore.builder()`:

```java
import io.agentscope.extensions.oss.OssDistributedStore;
import io.agentscope.harness.agent.DistributedStore;

DistributedStore oss = OssDistributedStore.create(ossClient, "my-bucket", "agentscope/");
DistributedStore mixed = DistributedStore.builder()
    .agentStateStore(oss.agentStateStore())
    .baseStore(oss.baseStore())
    .sandboxSnapshotSpec(oss.sandboxSnapshotSpec())
    .sandboxExecutionGuard(redis.sandboxExecutionGuard())
    .build();
```

## Storage layout

Objects are laid out under `keyPrefix` as plain JSON (state, KV) or tar (snapshot)
files:

```
{keyPrefix}{userId}/{sessionId}/{stateKey}.json       — AgentStateStore single value
{keyPrefix}{userId}/{sessionId}/{stateKey}.list.json  — AgentStateStore list value
{keyPrefix}{userId}/{sessionId}/{stateKey}.list.hash  — incremental-append change hash
{keyPrefix}{namespace...}/{key}.json                  — BaseStore item
{keyPrefix}{namespace...}/{key}.version               — BaseStore CAS version
{keyPrefix}{snapshotId}.tar                           — sandbox snapshot archive
```

`OssBaseStore.putIfVersion(...)` performs compare-and-swap using this `.version`
counter, but note `OssAgentStateStore` itself is last-writer-wins — OSS has no
native object CAS, so prefer a versioning backend (Redis, JDBC) for state in
heavy multi-replica write scenarios.

## Standalone snapshot client

`OssRemoteSnapshotClient` (wrapped by `OssSnapshotSpec`, a `RemoteSnapshotSpec`)
can also be constructed directly from endpoint/credential settings, without
building an `OSS` client yourself first:

```java
import io.agentscope.extensions.oss.OssSnapshotSpec;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;

SandboxSnapshotSpec snapshotSpec =
        new OssSnapshotSpec(endpoint, accessKeyId, accessKeySecret, "my-bucket", "agentscope/snapshot/");
```
