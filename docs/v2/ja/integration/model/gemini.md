---
title: Gemini モデル
---

`agentscope-extensions-model-gemini` は、Gemini API を通じて Google Gemini モデルを統合し、明示的な設定により Vertex AI 経路もサポートします。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-gemini</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## ModelRegistry

`GEMINI_API_KEY` を設定し、`gemini:<model>` の ID を使用します。

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("gemini:gemini-2.0-flash") // Resolved internally by ModelRegistry.resolve(modelId)
    .build();
```

## 明示的なビルダー

カスタム API 設定、Vertex AI の認証情報、フォーマッター、トランスポート、または生成オプションが必要な場合はビルダーを使用します。

```java
import io.agentscope.extensions.model.gemini.GeminiChatModel;

GeminiChatModel model = GeminiChatModel.builder()
    .apiKey(System.getenv("GEMINI_API_KEY"))
    .modelName("gemini-2.0-flash")
    .streamEnabled(true)
    .build();
```

## Spring Boot

Spring Boot アプリケーションでは Gemini スターターを使用できます。

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-gemini-spring-boot-starter</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

ビルダーの全オプション、フォーマッター、認証情報、レジストリコンテキストの詳細については [Model](/v2/ja/docs/building-blocks/model) を参照してください。
