---
title: Ollama モデル
---

`agentscope-extensions-model-ollama` は、ローカルでホストされる Ollama モデルを統合します。ローカル開発、プライベートデプロイメント、オフラインでのモデル提供に有用です。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-ollama</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## ModelRegistry

`ollama:<model>` の ID を使用します。`OLLAMA_BASE_URL` は任意で、省略された場合はローカルの Ollama エンドポイントがデフォルトになります。

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("ollama:llama3") // Resolved internally by ModelRegistry.resolve(modelId)
    .build();
```

## 明示的なビルダー

デフォルトではない Ollama エンドポイント、フォーマッター、トランスポート、プロキシ、または Ollama オプションが必要な場合はビルダーを使用します。

```java
import io.agentscope.extensions.model.ollama.OllamaChatModel;

OllamaChatModel model = OllamaChatModel.builder()
    .modelName("llama3")
    .baseUrl("http://localhost:11434")
    .build();
```

## Spring Boot

Spring Boot アプリケーションでは Ollama スターターを使用できます。

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-ollama-spring-boot-starter</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

`agentscope.model.provider=ollama` でローカルの Ollama モデルを設定します。ベース URL
は任意で、デフォルトは `http://localhost:11434` です。

```yaml
agentscope:
  model:
    provider: ollama
  ollama:
    model-name: llama3
    # base-url: http://localhost:11434
```

ビルダーの全オプション、フォーマッター、認証情報、レジストリコンテキストの詳細については [Model](/v2/ja/docs/building-blocks/model) を参照してください。
