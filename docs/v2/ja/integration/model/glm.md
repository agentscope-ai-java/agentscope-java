---
title: GLM モデル
---

`agentscope-extensions-model-openai` は、OpenAI 互換のモデルスタックを通じて第一級の GLM(Zhipu AI / Z.AI)サポートを提供します。OpenAI モデル拡張モジュールを追加し、`ModelRegistry` で `glm:<model>` を使用してください。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-openai</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## ModelRegistry

`ZAI_API_KEY`、`GLM_API_KEY`、または `ZHIPUAI_API_KEY` を設定し、`glm:<model>` の ID を使用します。

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("glm:glm-5.2") // Resolved internally by ModelRegistry.resolve(modelId)
    .build();
```

このプロバイダーはデフォルトで `https://open.bigmodel.cn/api/paas/v4` を使用し、モデル名を送信する前に `glm:` プレフィックスを取り除き、`io.agentscope.extensions.model.openai.compat.glm` の GLM フォーマッターを使用します。

## Thinking モード

モデルを解決する際に `GenerateOptions` を通じて GLM の thinking オプションを渡します。

```java
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;
import java.util.Map;

Model model = ModelRegistry.resolve(
    "glm:glm-5.2",
    ModelCreationContext.builder()
        .component(
            GenerateOptions.class,
            GenerateOptions.builder()
                .additionalBodyParam("thinking", Map.of("type", "disabled"))
                .reasoningEffort("max")
                .build())
        .build());
```

GLM-4.7 および GLM-5 シリーズはデフォルトで thinking が有効になっています。GLM-5.2 は `reasoning_effort` もサポートしており、`additionalBodyParam("tool_stream", true)` によってツールコール引数のストリーミングを有効化できます。

## 互換性に関する注意事項

GLM フォーマッターは OpenAI スタイルのリクエストを GLM の chat-completions API に適合させます。少なくとも1つのユーザーメッセージが存在することを保証し、サポートされていないメッセージの `name` フィールドを削除し、ツールスキーマの `strict` を省略し、`frequency_penalty`、`presence_penalty`、`thinking_budget`、`stream_options` などのサポートされていないリクエストフィールドを取り除きます。

GLM は `max_tokens` のみをサポートするため、`max_tokens` がまだ設定されていない場合、`max_completion_tokens` は `max_tokens` にマッピングされます。`temperature` と `top_p` は GLM がサポートする範囲にクランプされます。

GLM は `tool_choice=auto` のみを受け付けます。強制的な選択は `auto` に格下げされます。`ToolChoice.None` は、ツール呼び出しなしの契約を維持するためにリクエストからツールを削除します。

GLM の `response_format` は `json_object` のみをサポートするため、構造化出力はデフォルトで通常の AgentScope フォールバック動作を使用します。ネイティブな構造化出力は、対象のエンドポイントが必要な動作をサポートしていることを確認した場合にのみ有効にしてください。

互換または自己ホスト型のエンドポイントの場合は、`ModelCreationContext` を通じて `baseUrl`、`endpointPath`、生成オプション、またはフォーマッターのオーバーライドを渡してください。
