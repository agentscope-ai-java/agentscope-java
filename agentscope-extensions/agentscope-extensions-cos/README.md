# agentscope-extensions-cos

Tencent Cloud COS-backed implementation of AgentScope's `DistributedStore`: agent
state persistence (`AgentStateStore`), workspace filesystem KV (`BaseStore`), and
sandbox snapshot storage (`SandboxSnapshotSpec`), all as JSON/tar objects in a COS
bucket. Add this module when you deploy `HarnessAgent` across multiple replicas on
Tencent Cloud and need shared state without standing up Redis or a database.

## Installation

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-cos</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## Quick Start

`CosDistributedStore.create(...)` wires all three components — state store, base
store, and snapshot spec — from a single `COSClient` and bucket:

```java
import com.qcloud.cos.COSClient;
import io.agentscope.extensions.cos.CosDistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;

COSClient cosClient = new COSClient(cred, clientConfig); // Tencent Cloud COS SDK

HarnessAgent agent = HarnessAgent.builder()
    .name("my-agent")
    .model("dashscope:qwen-plus")
    .distributedStore(CosDistributedStore.create(cosClient, "my-bucket", "agentscope/"))
    .filesystem(new RemoteFilesystemSpec()) // baseStore auto-injected from distributedStore
    .build();
```

The `keyPrefix` argument (`"agentscope/"` above) namespaces every object COS
stores for this agent; `CosDistributedStore` appends `state/`, `store/`, and
`snapshot/` under it for the three components respectively.

### Using only the state store

If you just need cross-replica `AgentStateStore` persistence, construct
`CosAgentStateStore` directly instead of the full `CosDistributedStore` facade:

```java
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.cos.CosAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;

AgentStateStore stateStore = CosAgentStateStore.builder()
    .cosClient(cosClient)
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

`CosDistributedStore` does not implement `SandboxExecutionGuard` — object storage
is not suited to distributed locking. Combine it with a lock-capable store (e.g.
Redis) via `DistributedStore.builder()`:

```java
import io.agentscope.extensions.cos.CosDistributedStore;
import io.agentscope.harness.agent.DistributedStore;

DistributedStore cos = CosDistributedStore.create(cosClient, "my-bucket", "agentscope/");
DistributedStore mixed = DistributedStore.builder()
    .agentStateStore(cos.agentStateStore())
    .baseStore(cos.baseStore())
    .sandboxSnapshotSpec(cos.sandboxSnapshotSpec())
    .sandboxExecutionGuard(redis.sandboxExecutionGuard())
    .build();
```

## Storage layout

Objects are laid out under `keyPrefix` as plain JSON (state, KV) or tar (snapshot)
files:

```
{keyPrefix}{userId}/{sessionId}/{stateKey}.json       — AgentStateStore single value
{keyPrefix}{userId}/{sessionId}/{stateKey}.list.json  — AgentStateStore list value
{keyPrefix}{namespace...}/{key}.json                  — BaseStore item
{keyPrefix}{namespace...}/{key}.version               — BaseStore CAS version
{keyPrefix}{snapshotId}.tar                           — sandbox snapshot archive
```

`CosBaseStore.putIfVersion(...)` performs compare-and-swap using this `.version`
counter, but note `CosAgentStateStore` itself is last-writer-wins — COS has no
native object CAS, so prefer a versioning backend (Redis, JDBC) for state in
heavy multi-replica write scenarios.

## Learn more

- [Distributed Storage](../../docs/v2/en/integration/distributed/index.md) — the
  `DistributedStore` concept, capability matrix across backends, and mixed-store
  patterns.
