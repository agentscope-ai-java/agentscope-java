---
title: Nacos
---

`agentscope-extensions-nacos`는 [Nacos](https://nacos.io/)를 AgentScope의 통합 제어 플레인으로 사용하여 A2A Agent를 등록 및 탐색하고, 프롬프트를 핫로드하며, 스킬을 호스팅합니다. 세 개의 하위 모듈로 구성되어 있으므로 필요한 것만 선택해서 사용하세요.

| 하위 모듈 | 해결하는 문제 |
| --- | --- |
| `agentscope-extensions-nacos-a2a` | A2A AgentCard / 인스턴스 레지스트리 및 디스커버리 |
| `agentscope-extensions-nacos-prompt` | Nacos에서 프롬프트 템플릿을 관리하며 핫 업데이트 지원 |
| `agentscope-extensions-nacos-skill` | Nacos AI 모듈에서 스킬 패키지(ZIP)를 로드 |

## A2A 레지스트리 및 디스커버리

### 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-nacos-a2a</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

### 서버 측: AgentCard를 Nacos에 등록

```java
import io.agentscope.core.nacos.a2a.registry.NacosA2aRegistry;
import io.agentscope.core.nacos.a2a.registry.NacosA2aRegistryProperties;

Properties props = new Properties();
props.setProperty("serverAddr", "127.0.0.1:8848");
NacosA2aRegistry registry = new NacosA2aRegistry(props);

NacosA2aRegistryProperties props2 = new NacosA2aRegistryProperties();
// props2.setNamespace(...) / setGroup(...) / etc.
registry.registerAgent(agentCard, props2);
```

등록이 끝나면 AgentCard와 서비스 엔드포인트가 Nacos AI Service에 기록되어 소비자가 탐색할 수 있게 됩니다.

### 클라이언트 측: Nacos를 통해 원격 AgentCard 해석하기

```java
import io.agentscope.core.nacos.a2a.discovery.NacosAgentCardResolver;

NacosAgentCardResolver resolver = new NacosAgentCardResolver(props, "translator-agent");
A2aAgent remote = A2aAgent.builder()
    .name("translator")
    .agentCardResolver(resolver)
    .build();
```

## 프롬프트 설정 센터

### 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-nacos-prompt</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

### 사용법

```java
import com.alibaba.nacos.api.ai.AiService;
import io.agentscope.core.nacos.prompt.NacosPromptListener;

NacosPromptListener prompts = new NacosPromptListener(aiService);

String tpl = prompts.getPrompt("system-prompt", Map.of(
    "userName", "Alice"
));
```

리스너는 로컬 캐시를 유지합니다. Nacos에서 프롬프트가 변경되면 업데이트가 푸시되며, 다음 `getPrompt(...)` 호출은 재시작 없이 새 버전을 반환합니다.

## 스킬 저장소

`agentscope-extensions-nacos-skill`은 Nacos AI 모듈이 관리하는 스킬 ZIP 패키지를 다운로드하고 파싱하는 `AgentSkillRepository` 구현을 제공합니다.

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-nacos-skill</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

```java
import io.agentscope.core.nacos.skill.NacosSkillRepository;

Properties props = new Properties();
props.setProperty(NacosSkillRepository.SKILL_VERSION_PATH, "1.2.0");
// or SKILL_LABEL_PATH = "stable"

NacosSkillRepository repo = new NacosSkillRepository(aiService, "default-namespace", props);
AgentSkill skill = repo.getSkill("calculator");
```

버전/레이블 해석 순서: 생성자에 전달된 `Properties` → JVM `-D` 시스템 프로퍼티 → 환경 변수. 버전과 레이블이 모두 해석되면 **버전이 우선**하며 레이블은 다운로드에 사용되지 않습니다.

## 함께 쓰기 좋은 조합

- [A2A](/v2/ko/integration/protocol/a2a): Nacos 기반 `AgentRegistry`를 `AgentScopeA2aServer.builder().agentRegistry(...)`에 주입하여 시작 시 클러스터 전체에 AgentCard를 게시합니다.
- [스킬 저장소](/v2/ko/integration/skill/index): Git/MySQL `AgentSkillRepository`와 공존시켜 여러 소스로부터 Toolkit을 구성합니다.
