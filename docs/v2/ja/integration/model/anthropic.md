---
title: Anthropic モデル
---

`agentscope-extensions-model-anthropic` は Anthropic Claude モデルを統合し、Anthropic 固有のフォーマッターおよびリクエスト DTO のサポートを含みます。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-anthropic</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## ModelRegistry

`ANTHROPIC_API_KEY` を設定し、`anthropic:<model>` の ID を使用します。

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("anthropic:claude-sonnet-4.5") // Resolved internally by ModelRegistry.resolve(modelId)
    .build();
```

## 明示的なビルダー

カスタムエンドポイント、フォーマッター、トランスポート、プロンプトキャッシュ、thinking、または生成オプションが必要な場合はビルダーを使用します。

```java
import io.agentscope.extensions.model.anthropic.AnthropicChatModel;

AnthropicChatModel model = AnthropicChatModel.builder()
    .apiKey(System.getenv("ANTHROPIC_API_KEY"))
    .modelName("claude-sonnet-4.5")
    .stream(true)
    .build();
```

## Spring Boot

Spring Boot アプリケーションでは Anthropic スターターを使用できます。

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-anthropic-spring-boot-starter</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

ビルダーの全オプション、フォーマッター、認証情報、レジストリコンテキストの詳細については [Model](/v2/ja/docs/building-blocks/model) を参照してください。
