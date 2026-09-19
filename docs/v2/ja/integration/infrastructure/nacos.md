---
title: Nacos
---

`agentscope-extensions-nacos` は [Nacos](https://nacos.io/) を AgentScope の統一コントロールプレーンとして利用し、A2A Agent の登録と発見、プロンプトのホットロード、スキルのホスティングを行います。3 つのサブモジュールで構成されているので、必要なものを選んでください。

| サブモジュール | 解決する課題 |
| --- | --- |
| `agentscope-extensions-nacos-a2a` | A2A AgentCard / インスタンスのレジストリとディスカバリ |
| `agentscope-extensions-nacos-prompt` | Nacos でプロンプトテンプレートをホットアップデート付きで管理 |
| `agentscope-extensions-nacos-skill` | Nacos AI モジュールからスキルパッケージ(ZIP)をロードする |

## A2A レジストリとディスカバリ

### 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-nacos-a2a</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

### サーバー側: AgentCard を Nacos に登録する

```java
import io.agentscope.core.nacos.a2a.registry.NacosA2aRegistry;
import io.agentscope.core.nacos.a2a.registry.NacosA2aRegistryProperties;

Properties props = new Properties();
props.setProperty("serverAddr", "127.0.0.1:8848");
NacosA2aRegistry registry = new NacosA2aRegistry(props);

NacosA2aRegistryProperties props2 = new NacosA2aRegistryProperties();
// props2.setNamespace(...) / setGroup(...) など
registry.registerAgent(agentCard, props2);
```

登録後、AgentCard とサービスエンドポイントが Nacos AI Service に書き込まれ、利用側が発見できるようになります。

### クライアント側: Nacos 経由でリモートの AgentCard を解決する

```java
import io.agentscope.core.nacos.a2a.discovery.NacosAgentCardResolver;

NacosAgentCardResolver resolver = new NacosAgentCardResolver(props, "translator-agent");
A2aAgent remote = A2aAgent.builder()
    .name("translator")
    .agentCardResolver(resolver)
    .build();
```

## プロンプト設定センター

### 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-nacos-prompt</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

### 使い方

```java
import com.alibaba.nacos.api.ai.AiService;
import io.agentscope.core.nacos.prompt.NacosPromptListener;

NacosPromptListener prompts = new NacosPromptListener(aiService);

String tpl = prompts.getPrompt("system-prompt", Map.of(
    "userName", "Alice"
));
```

リスナーはローカルキャッシュを保持しており、Nacos 上でプロンプトが変更されると更新がプッシュされます。次の `getPrompt(...)` 呼び出しは、再起動なしで新しいバージョンを返します。

## スキルリポジトリ

`agentscope-extensions-nacos-skill` は、Nacos AI モジュールが管理するスキル ZIP パッケージをダウンロードして解析する `AgentSkillRepository` の実装を提供します。

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
// または SKILL_LABEL_PATH = "stable"

NacosSkillRepository repo = new NacosSkillRepository(aiService, "default-namespace", props);
AgentSkill skill = repo.getSkill("calculator");
```

バージョン/ラベルの解決順序: コンストラクタに渡された `Properties` → JVM の `-D` システムプロパティ → 環境変数。バージョンとラベルの両方が解決された場合、**バージョンが優先され**、ラベルはダウンロードに使用されません。

## 相性の良い組み合わせ

- [A2A](/v2/ja/integration/protocol/a2a): Nacos ベースの `AgentRegistry` を `AgentScopeA2aServer.builder().agentRegistry(...)` に注入し、起動時にクラスタ全体に AgentCard を公開します。
- [スキルリポジトリ](/v2/ja/integration/skill/index): Git/MySQL の `AgentSkillRepository` と共存させ、複数のソースから Toolkit を組み立てます。
