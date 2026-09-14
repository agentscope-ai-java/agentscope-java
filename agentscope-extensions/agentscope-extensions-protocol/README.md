# agentscope-extensions-protocol

Parent module for AgentScope Java's agent interop protocol adapters. It has no
code of its own — it just aggregates the sub-modules below, each letting an
Agent talk to the outside world over a different standard protocol. Depend on
the specific sub-module(s) you need rather than on this parent.

## Sub-modules

| Module | Protocol | Problem it solves |
| --- | --- | --- |
| [`agentscope-extensions-a2a`](agentscope-extensions-a2a) | [A2A](https://a2aproject.github.io/A2A/) | Lets Agents call each other, so you can compose multi-agent workflows across services (`-a2a-client` to call a remote Agent, `-a2a-server` to expose a local `ReActAgent`) |
| [`agentscope-extensions-agui`](agentscope-extensions-agui) | [AG-UI](https://github.com/ag-ui-protocol/ag-ui) | Converts an Agent's event stream into a standardized event feed a front-end chat UI can render live (text, reasoning, tool calls, state, HITL interrupts) |
| [`agentscope-extensions-chat-completions-web`](agentscope-extensions-chat-completions-web) | OpenAI-compatible chat completions | Exposes an Agent behind an OpenAI-style `/chat/completions` API so existing OpenAI clients/SDKs can talk to it unchanged |
| [`agentscope-extensions-agent-protocol`](agentscope-extensions-agent-protocol) | [Agent Protocol](https://agentprotocol.ai/) | Exposes a `HarnessAgent` as a `/tasks` HTTP API so external systems (CI, other agent platforms, a parent Harness calling a remote subagent) can submit and poll tasks over a uniform REST contract |

As a rule of thumb: AG-UI is user-facing (browser to app), Agent Protocol is
the internal remote-subagent / task HTTP surface, and A2A is external
agent-to-agent interop.

## Installation

Depend on the sub-module you need directly — for example, the A2A client:

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-a2a-client</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## Quick Start

Wrap a remote A2A Agent as a local `A2aAgent` (a subclass of `AgentBase`) and
call it like any other Agent:

```java
import io.a2a.spec.AgentCard;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;

AgentCard card = AgentCard.builder()
    .name("remote-translator")
    .url("http://other-service:8080")
    // ...
    .build();

A2aAgent remote = A2aAgent.builder()
    .name("remote-translator")
    .agentCard(card)
    .build();

Msg result = remote.call(
        Msg.builder().content(TextBlock.builder().text("Translate to English: 你好").build()).build())
    .block();
```

`A2aAgent` composes naturally with the rest of AgentScope Java (Pipeline,
MsgHub, Subagent, etc.) since it is just another `AgentBase`.

## Learn more

Each sub-module has its own README with the full API. For the protocol
comparison and how to choose between them, see
[docs/v2/en/integration/protocol/overview.md](../../docs/v2/en/integration/protocol/overview.md)
and the per-protocol pages:
[A2A](../../docs/v2/en/integration/protocol/a2a.md),
[AG-UI](../../docs/v2/en/integration/protocol/agui.md),
[Agent Protocol](../../docs/v2/en/integration/protocol/agent-protocol.md).
