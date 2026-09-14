# agentscope-extensions-nacos

Parent module for [Nacos](https://nacos.io/)-backed integrations: A2A AgentCard registry/discovery, hot-reloadable prompt templates, and a skill package repository, all served from the Nacos AI module. It is a `pom`-packaged aggregator — depend on the sub-module(s) you actually need, not on this parent artifact.

## Sub-modules

| Module | Artifact | Provides |
| --- | --- | --- |
| A2A registry & discovery | `agentscope-extensions-nacos-a2a` | `NacosA2aRegistry` (publish an `AgentCard` + endpoints) and `NacosAgentCardResolver` (resolve a remote `AgentCard` for `A2aAgent`) |
| Prompt config center | `agentscope-extensions-nacos-prompt` | `NacosPromptListener` — fetch and render prompt templates stored in Nacos, with live hot updates |
| Skill repository | `agentscope-extensions-nacos-skill` | `NacosSkillRepository`, an `AgentSkillRepository` that downloads skill ZIPs from the Nacos AI module |

## Installation

Add whichever sub-module(s) you need — for example, the A2A registry:

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-nacos-a2a</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

Swap the `artifactId` for `agentscope-extensions-nacos-prompt` or `agentscope-extensions-nacos-skill` as needed; all three share the `io.agentscope` `groupId` and version.

## Quick Start

The most common entry point is resolving a remote agent's `AgentCard` from Nacos and wiring it into an `A2aAgent`, backed by `agentscope-extensions-nacos-a2a`:

```java
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.AiFactory;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.nacos.a2a.discovery.NacosAgentCardResolver;
import java.util.Properties;

Properties properties = new Properties();
properties.put("serverAddr", "127.0.0.1:8848");
// properties.put(PropertyKeyConst.NAMESPACE, "my-namespace");

// Or reuse an existing Nacos client: new NacosAgentCardResolver(aiService)
NacosAgentCardResolver resolver = new NacosAgentCardResolver(properties);

A2aAgent remote = A2aAgent.builder()
    .name("translator-agent")
    .agentCardResolver(resolver)
    .build();
```

`NacosAgentCardResolver` subscribes to AgentCard updates for each agent name it resolves, so the card is kept current without restarting the process. To publish an `AgentCard` from the server side instead, use `NacosA2aRegistry`:

```java
import io.agentscope.core.nacos.a2a.registry.NacosA2aRegistry;
import io.agentscope.core.nacos.a2a.registry.NacosA2aRegistryProperties;

NacosA2aRegistry registry = new NacosA2aRegistry(properties);
NacosA2aRegistryProperties registryProperties =
        NacosA2aRegistryProperties.builder()
                .setAsLatest(true)
                .build();

registry.registerAgent(agentCard, registryProperties);
```

For prompt templates and skill packages, see `agentscope-extensions-nacos-prompt`'s `NacosPromptListener` and `agentscope-extensions-nacos-skill`'s `NacosSkillRepository`.

## Learn more

- [Nacos integration guide](../../docs/v2/en/integration/infrastructure/nacos.md) — full walkthrough of all three sub-modules, including the prompt and skill APIs.
