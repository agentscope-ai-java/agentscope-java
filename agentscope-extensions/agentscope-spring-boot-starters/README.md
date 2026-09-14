# agentscope-spring-boot-starters

Parent (aggregator) module for every Spring Boot integration of AgentScope Java. Each
sub-module is an independent starter you add individually — pulling in this parent
artifact alone brings in no code, just Maven dependency management for the child
starters.

## Sub-modules

| Artifact | Purpose |
|----------|---------|
| `agentscope-spring-boot-starter` | Core auto-configuration: `Memory`, `Toolkit` and `ReActAgent` beans wired from any `Model` bean on the context. |
| `agentscope-openai-spring-boot-starter` | Auto-configures an OpenAI `Model` bean from `agentscope.openai.*` properties. |
| `agentscope-dashscope-spring-boot-starter` | Auto-configures a DashScope `Model` bean from `agentscope.dashscope.*` properties. |
| `agentscope-gemini-spring-boot-starter` | Auto-configures a Gemini `Model` bean from `agentscope.gemini.*` properties. |
| `agentscope-anthropic-spring-boot-starter` | Auto-configures an Anthropic `Model` bean from `agentscope.anthropic.*` properties. |
| `agentscope-ollama-spring-boot-starter` | Auto-configures an Ollama `Model` bean from `agentscope.ollama.*` properties. |
| `agentscope-a2a-spring-boot-starter` | Exposes a configured agent over the A2A (Agent-to-Agent) JSON-RPC protocol. |
| `agentscope-agui-spring-boot-starter` | Exposes agents over the AG-UI protocol, with both Spring MVC and WebFlux support. |
| `agentscope-chat-completions-web-starter` | Exposes an AgentScope agent through an OpenAI Chat Completions-style HTTP API. |
| `agentscope-nacos-spring-boot-starter` | Integrates AgentScope with Nacos for A2A service discovery/registration and prompt management. |
| `agentscope-admin-spring-boot-starter` | Adds admin/ops REST and Actuator endpoints (status, agents, tools, permissions, usage, shutdown, …) for running agents. |

Only the provider-specific model starters (`openai`, `dashscope`, `gemini`,
`anthropic`, `ollama`) create `Model` beans; add exactly one of them alongside the
core `agentscope-spring-boot-starter`, or supply your own `Model` bean.

## Installation

Add the parent as dependency management in your application's `pom.xml`, then depend
on the individual starters you need:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.agentscope</groupId>
            <artifactId>agentscope-spring-boot-starters</artifactId>
            <version>${agentscope.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.agentscope</groupId>
        <artifactId>agentscope-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>io.agentscope</groupId>
        <artifactId>agentscope-dashscope-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

## Quick Start

This example wires up the core starter together with the DashScope model starter,
which auto-configures a `ReActAgent` bean ready to inject.

`src/main/resources/application.yml`:

```yaml
agentscope:
  agent:
    enabled: true
    name: "Assistant"
    sys-prompt: "You are a helpful AI assistant."
    max-iters: 10
  dashscope:
    api-key: ${DASHSCOPE_API_KEY}
    model-name: qwen-plus
    stream: true
```

`Application.java`:

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @RestController
    static class ChatController {

        private final ReActAgent agent;

        ChatController(ReActAgent agent) {
            this.agent = agent;
        }

        @GetMapping("/chat")
        String chat(@RequestParam String message) {
            Msg reply =
                    agent.call(message, RuntimeContext.builder().build()).block();
            return reply.getTextContent();
        }
    }
}
```

No explicit `@Bean` definitions are needed: `agentscope-spring-boot-starter`
auto-configures the `ReActAgent` once it finds a `Model` bean on the context, and
`agentscope-dashscope-spring-boot-starter` supplies that `Model` bean from the
`agentscope.dashscope.*` properties above. Swap in `agentscope-openai-spring-boot-starter`,
`-gemini-`, `-anthropic-`, or `-ollama-` (with its matching `agentscope.<provider>.*`
properties) to change model providers without touching application code.

## Learn more

See the [Spring Boot applications](../../docs/v2/en/docs/building-blocks/model.md#spring-boot-applications)
section of the model building block docs for provider selection, explicit model
builders, and builder customizer beans.
