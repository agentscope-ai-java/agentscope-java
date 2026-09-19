---
title: DashScope モデル
---

`agentscope-extensions-model-dashscope` は、Alibaba Cloud DashScope の Qwen モデルを統合し、マルチモーダルおよび推論機能を備えた Qwen モデルを含みます。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-dashscope</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## ModelRegistry

`DASHSCOPE_API_KEY` を設定し、`dashscope:<model>` または Qwen の短縮形式のいずれかを使用します。

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("dashscope:qwen-plus") // Resolved internally by ModelRegistry.resolve(modelId)
    .build();
```

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("qwen-plus") // Resolved internally by ModelRegistry.resolve(modelId)
    .build();
```

## 明示的なビルダー

エンドポイントタイプ、thinking、検索、暗号化など DashScope 固有のオプションが必要な場合はビルダーを使用します。

```java
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;

DashScopeChatModel model = DashScopeChatModel.builder()
    .apiKey(System.getenv("DASHSCOPE_API_KEY"))
    .modelName("qwen-plus")
    .stream(true)
    .build();
```

## Spring Boot

Spring Boot アプリケーションでは DashScope スターターを使用できます。

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-dashscope-spring-boot-starter</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

ビルダーの全オプション、フォーマッター、認証情報、レジストリコンテキストの詳細については [Model](/v2/ja/docs/building-blocks/model) を参照してください。
