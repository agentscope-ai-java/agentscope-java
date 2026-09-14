# Kimi モデル

`agentscope-extensions-model-openai` は、OpenAI 互換のモデルスタックを通じて第一級の Kimi(Moonshot AI)サポートを提供します。OpenAI モデル拡張モジュールを追加し、`ModelRegistry` で `kimi:<model>` を使用してください。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-openai</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## ModelRegistry

`MOONSHOT_API_KEY` または `KIMI_API_KEY` を設定し、`kimi:<model>` の ID を使用します。

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("kimi:kimi-k3") // Resolved internally by ModelRegistry.resolve(modelId)
    .build();
```

このプロバイダーはデフォルトで `https://api.moonshot.cn/v1` を使用し、モデル名を送信する前に `kimi:` プレフィックスを取り除き、`io.agentscope.extensions.model.openai.compat.kimi` の Kimi フォーマッターを使用します。

## Thinking モード

モデルを解決する際に `GenerateOptions` を通じて Kimi の thinking オプションを渡します。

```java
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;
import java.util.Map;

Model model = ModelRegistry.resolve(
    "kimi:kimi-k2.6",
    ModelCreationContext.builder()
        .component(
            GenerateOptions.class,
            GenerateOptions.builder()
                .additionalBodyParam("thinking", Map.of("type", "disabled"))
                .maxCompletionTokens(16000)
                .build())
        .build());
```

`kimi-k3` はトップレベルの `reasoning_effort` オプション(`low`、`high`、`max`)を使用します。`kimi-k3` と `kimi-k2.7-code` は常に thinking を有効にした状態で実行されます。`kimi-k2.6` と `kimi-k2.5` はデフォルトで thinking が有効になっていますが、`additionalBodyParam("thinking", Map.of("type", "disabled"))` によって無効化できます。

## 互換性に関する注意事項

Kimi フォーマッターは OpenAI スタイルのリクエストを Kimi の chat-completions API に適合させます。ツールスキーマの `strict` を省略し、メッセージ履歴内のアシスタントの `reasoning_content` を保持し、`thinking_budget` などのサポートされていないリクエストフィールドを取り除きます。

`kimi-*` モデルでは、`temperature`、`top_p`、`n`、`frequency_penalty`、`presence_penalty` などのサンプリングパラメータはプラットフォームによって固定されており、リクエストから削除されます。`moonshot-v1` シリーズはこれらのパラメータを保持します。Kimi は `max_completion_tokens` をドキュメント化しているため、`max_completion_tokens` がまだ設定されていない場合、`max_tokens` は `max_completion_tokens` にマッピングされます。

`reasoning_effort` は `kimi-k3` のみで保持されます。K2.x の thinking 制御については、`GenerateOptions.additionalBodyParam` を通じて `thinking` ボディパラメータを渡してください。

`tool_choice=auto` と `tool_choice=none` は広くサポートされています。`tool_choice=required` は K2.x モデルでは `auto` に格下げされます。特定の関数を強制することは thinking が有効な状態とは互換性がないため、`kimi-k3`、`kimi-k2.7-code`、および `thinking.type` が明示的に `disabled` に設定されていない限り `kimi-k2.6` / `kimi-k2.5` では `auto` に格下げされます。

構造化出力はデフォルトで通常の AgentScope フォールバック動作を使用します。

互換または自己ホスト型のエンドポイントの場合は、`ModelCreationContext` を通じて `baseUrl`、`endpointPath`、生成オプション、またはフォーマッターのオーバーライドを渡してください。
