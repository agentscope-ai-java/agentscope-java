# agentscope-extensions-sandbox

Parent module for AgentScope Java's sandbox execution backends. Each sub-module implements the Harness `SandboxFilesystemSpec` contract for a different isolated-execution provider, so an agent's file operations and command execution run inside a container/cloud sandbox instead of touching the host — with cross-call recovery via snapshots and multi-replica-friendly isolation scopes. This module itself is a Maven aggregator (`packaging: pom`); it ships no code of its own.

## Sub-modules

| Module | Artifact | Backend |
|--------|----------|---------|
| Kubernetes | `agentscope-extensions-sandbox-kubernetes` | Self-hosted K8s, based on [agent-sandbox](https://github.com/kubernetes-sigs/agent-sandbox) (`SandboxClaim` / `WarmPool`); workspace persistence via PVC |
| AgentRun | `agentscope-extensions-sandbox-agentrun` | Aliyun-managed sandbox (Function Compute FC 3.0); per-instance NAS/OSS auto-mount |
| Daytona | `agentscope-extensions-sandbox-daytona` | Generic managed sandbox HTTP API |
| E2B | `agentscope-extensions-sandbox-e2b` | Generic managed sandbox with native platform snapshots |

All of them plug into the same `HarnessAgent.builder().filesystem(SandboxFilesystemSpec)` entry point — agent code, toolkit, and `AGENTS.md` don't change when you switch backends.

## Installation

Depend on the specific backend module you need (not the aggregator). For example, E2B:

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-sandbox-e2b</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

Swap the artifactId for `agentscope-extensions-sandbox-kubernetes`, `agentscope-extensions-sandbox-agentrun`, or `agentscope-extensions-sandbox-daytona` to use another backend.

## Quick Start

Each backend exposes a `*FilesystemSpec` builder that you attach to `HarnessAgent.builder().filesystem(...)`. This example uses E2B's:

```java
import io.agentscope.extensions.sandbox.e2b.E2bFilesystemSpec;
import io.agentscope.harness.agent.HarnessAgent;

HarnessAgent agent = HarnessAgent.builder()
    .name("code-agent")
    .model("dashscope:qwen-plus")
    .workspace(workspace)
    .filesystem(new E2bFilesystemSpec()
        .apiKey(System.getenv("E2B_API_KEY"))
        .templateId("base"))
    .build();

agent.call(msgs, RuntimeContext.builder()
    .userId("alice")
    .sessionId("conv-1")
    .build()).block();
```

Calls sharing the same `userId` reuse the same sandbox (or restore it from a snapshot); a different `userId` gets a separate sandbox. `E2bFilesystemSpec` also exposes `.apiBaseUrl(...)`, `.domain(...)`, `.workspaceRoot(...)`, `.sandboxTimeoutSeconds(...)`, `.persistenceMode(...)` and `.snapshotSpec(...)` for cross-replica snapshot persistence — see each backend's own Javadoc for its full option set.

## Learn more

- [Sandbox](../../docs/v2/en/docs/harness/sandbox.md) — isolation scopes, snapshot recovery, distributed deployment, the runtime image contract, and a full comparison of the available stores.
