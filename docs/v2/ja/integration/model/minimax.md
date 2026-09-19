---
title: MiniMax モデル
---

`agentscope-extensions-model-openai` は、OpenAI 互換のモデルスタックを通じて第一級の MiniMax サポートを提供します。OpenAI モデル拡張モジュールを追加し、`ModelRegistry` で `minimax:<model>` を使用してください。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-openai</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## ModelRegistry

`MINIMAX_API_KEY` を設定し、`minimax:<model>` の ID を使用します。

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("minimax:MiniMax-M3") // Resolved internally by ModelRegistry.resolve(modelId)
    .build();
```

プロバイダーのベース URL はデフォルトで `https://api.minimaxi.com/v1` です。`OpenAIClient` はデフォルトの chat completions エンドポイントを追加するため、最終的なリクエスト URL は `https://api.minimaxi.com/v1/chat/completions` となり、MiniMax の OpenAI 互換 API に一致します。このプロバイダーはモデル名を送信する前に `minimax:` プレフィックスを取り除き、`io.agentscope.extensions.model.openai.compat.minimax` の MiniMax フォーマッターを使用します。

## Thinking モード

モデルを解決する際に `ModelCreationContext` を通じて MiniMax の thinking オプションを渡します。

```java
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;

Model model = ModelRegistry.resolve(
    "minimax:MiniMax-M3",
    ModelCreationContext.builder()
        .enableThinking(false)
        .build());
```

`enableThinking(false)` は `thinking: {"type": "disabled"}` を送信し、`enableThinking(true)` は `thinking: {"type": "adaptive"}` を送信します。MiniMax-M3 は `thinking` が省略された場合、デフォルトで adaptive thinking を使用します。M2.x モデルは、無効化が要求された場合でも thinking を有効なままにします。フォーマッターはデフォルトで `reasoning_split` を有効にするため、MiniMax の thinking コンテンツを `ThinkingBlock` として解析できます。

## 互換性に関する注意事項

MiniMax フォーマッターは OpenAI スタイルのリクエストを MiniMax の OpenAI 互換 Chat Completions API に適合させます。MiniMax が `max_tokens` を非推奨としてマークしているため、`max_tokens` を `max_completion_tokens` にマッピングします。

MiniMax のツール定義は関数ツールをサポートしていますが、公式のスキーマにはツールスキーマの `strict` フィールドが含まれていないため、strict なスキーマ検証でツールが登録されている場合でも、デフォルトのフォーマッターは `strict` を省略します。MiniMax は `tool_choice` もドキュメント化していないため、明示的なツール選択の設定は MiniMax のリクエストから削除されます。

フォーマッターは、`reasoning_effort`、`frequency_penalty`、`presence_penalty`、`thinking_budget`、`parallel_tool_calls`、`response_format`、`seed` などのサポートされていない OpenAI 専用のリクエストフィールドを削除します。MiniMax はスキーマ制約付き出力に対する OpenAI の `response_format` サポートをドキュメント化していないため、構造化出力はデフォルトで通常の AgentScope フォールバック動作を使用します。

互換または自己ホスト型のエンドポイントの場合は、`ModelCreationContext` を通じて `baseUrl`、`endpointPath`、生成オプション、またはフォーマッターのオーバーライドを渡してください。
