# OpenAI モデル

`agentscope-extensions-model-openai` は、OpenAI Chat Completions スタイルのモデルを統合します。これは、DeepSeek、GLM、Kimi、MiniMax など、そのワイヤーフォーマットが OpenAI API に従う OpenAI 互換エンドポイントにも使用するモジュールです。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-openai</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## ModelRegistry

`OPENAI_API_KEY` を設定し、`openai:<model>` の ID を使用します。

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("openai:gpt-4.1-mini") // Resolved internally by ModelRegistry.resolve(modelId)
    .build();
```

## 明示的なビルダー

カスタムエンドポイント、フォーマッター、トランスポート、または生成オプションが必要な場合はビルダーを使用します。

```java
import io.agentscope.extensions.model.openai.OpenAIChatModel;

OpenAIChatModel model = OpenAIChatModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("gpt-4.1-mini")
    .stream(true)
    .build();
```

## Spring Boot

Spring Boot アプリケーションでは OpenAI スターターを使用できます。

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-openai-spring-boot-starter</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

ビルダーの全オプション、フォーマッター、認証情報、レジストリコンテキストの詳細については [Model](../../docs/building-blocks/model.md) を参照してください。
