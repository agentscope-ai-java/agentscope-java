---
title: DeepSeek モデル
---

`agentscope-extensions-model-openai` は、OpenAI 互換のモデルスタックを通じて第一級の DeepSeek サポートを提供します。OpenAI モデル拡張モジュールを追加し、`ModelRegistry` で `deepseek:<model>` を使用してください。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-openai</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## ModelRegistry

`DEEPSEEK_API_KEY` を設定し、`deepseek:<model>` の ID を使用します。

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("deepseek:deepseek-v4-flash") // Resolved internally by ModelRegistry.resolve(modelId)
    .build();
```

このプロバイダーはデフォルトで `https://api.deepseek.com` を使用し、モデル名を送信する前に `deepseek:` プレフィックスを取り除き、`io.agentscope.extensions.model.openai.compat.deepseek` の DeepSeek フォーマッターを使用します。

## Thinking モード

モデルを解決する際に `ModelCreationContext` を通じて DeepSeek の thinking モードを有効にします。

```java
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;

Model model = ModelRegistry.resolve(
    "deepseek:deepseek-v4-flash",
    ModelCreationContext.builder()
        .enableThinking(true)
        .build());
```

ストリーミングを行う呼び出し元は、`ThinkingBlockDeltaEvent` を `TextBlockDeltaEvent` とは別にレンダリングできます。

## 互換性に関する注意事項

DeepSeek フォーマッターは、`system` ロールやサポートされている `name` フィールドを含む DeepSeek 互換のメッセージフィールドを保持します。また、前のターンからの古い推論コンテンツを削除しつつ、現在のツールコールのコンテキストに必要な推論コンテンツは保持します。

DeepSeek の安定版エンドポイントはデフォルトでツールスキーマの `strict` フィールドを使用しないため、strict なスキーマ検証でツールが登録されている場合でも、デフォルトのフォーマッターは `strict` を省略します。構造化出力は、互換エンドポイント向けにネイティブな構造化出力を明示的に設定しない限り、通常の AgentScope フォールバック動作を使用します。

ベータ版または互換エンドポイントの場合は、`ModelCreationContext` を通じて `baseUrl`、`endpointPath`、生成オプション、またはフォーマッターのオーバーライドを渡してください。
