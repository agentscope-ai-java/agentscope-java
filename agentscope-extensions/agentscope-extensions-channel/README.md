# agentscope-extensions-channel

Parent module for IM channel adapters that connect an AgentScope Harness Gateway agent to real-world
messaging platforms. Each adapter implements the Harness `Channel` interface, handling
platform-specific authentication, webhook/stream verification, message parsing, and reply delivery
so your agent code stays platform-agnostic.

## Sub-modules

| Module | Platform | Transport |
| --- | --- | --- |
| [`agentscope-extensions-channel-common`](agentscope-extensions-channel-common) | — | Shared `IdempotencyStore` and `BotLoopGuard` utilities used by every adapter |
| [`agentscope-extensions-channel-dingtalk`](agentscope-extensions-channel-dingtalk) | DingTalk (钉钉) | Stream protocol (persistent WebSocket) |
| [`agentscope-extensions-channel-feishu`](agentscope-extensions-channel-feishu) | Feishu / Lark (飞书) | Event subscription callback (HTTP) |
| [`agentscope-extensions-channel-github`](agentscope-extensions-channel-github) | GitHub | Webhook (HTTP) |
| [`agentscope-extensions-channel-gitlab`](agentscope-extensions-channel-gitlab) | GitLab | Webhook (HTTP) |
| [`agentscope-extensions-channel-wecom`](agentscope-extensions-channel-wecom) | WeCom (企业微信) | Encrypted callback (HTTP) |

`agentscope-extensions-channel` itself is a `pom`-packaged aggregator — it has no code, only the
`<modules>` list above. Depend on the specific adapter module(s) you need, not on this parent.

Every adapter depends on `agentscope-extensions-channel-common` (transitively) and on
`agentscope-harness` (`provided` scope — bring your own version via your application).

## Installation

Pick the adapter for your platform. For example, DingTalk:

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-channel-dingtalk</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## Quick Start (DingTalk)

Every adapter exposes a `fromProperties(channelId, ChannelConfig, Map<String, Object>)` factory and
implements `io.agentscope.harness.agent.gateway.channel.Channel`, so it plugs straight into
`GatewayBootstrap`:

```java
import io.agentscope.extensions.channel.dingtalk.DingTalkChannel;
import io.agentscope.harness.agent.gateway.GatewayBootstrap;
import io.agentscope.harness.agent.gateway.channel.ChannelConfig;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.Map;

HarnessAgent agent = HarnessAgent.builder()
    .name("main")
    .model("dashscope:qwen-plus")
    .build();

DingTalkChannel channel = DingTalkChannel.fromProperties(
    "my-dingtalk",
    ChannelConfig.of("my-dingtalk", "main"),
    Map.of(
        "appKey",    "your-app-key",
        "appSecret", "your-app-secret",
        "robotCode", "your-robot-code"
    ));

GatewayBootstrap gw = GatewayBootstrap.builder()
    .agent("main", agent)
    .channel(channel)
    .build();

gw.start();   // opens the Stream WebSocket and begins dispatching
```

Internally, `DingTalkChannel` wires up the shared `IdempotencyStore` and `BotLoopGuard` from
`agentscope-extensions-channel-common` around inbound message handling before routing through the
Gateway — the same pattern every other adapter in this module follows.

## Learn more

Each platform has its own prerequisites, configuration properties, and message-flow details — see
the [Channel Adapters integration docs](../../docs/v2/en/integration/channel/index.md), and the
per-platform pages: [DingTalk](../../docs/v2/en/integration/channel/dingtalk.md),
[Feishu](../../docs/v2/en/integration/channel/feishu.md),
[GitHub](../../docs/v2/en/integration/channel/github.md),
[GitLab](../../docs/v2/en/integration/channel/gitlab.md),
[WeCom](../../docs/v2/en/integration/channel/wecom.md).
