---
title: Chat Completions Web
---

`agentscope-extensions-chat-completions-web` は、AgentScope の Agent を [OpenAI Chat Completions](https://platform.openai.com/docs/api-reference/chat) 互換の API として公開するため、OpenAI SDK、LangChain、LlamaIndex、ChatBox などが、あたかも OpenAI と通信しているかのように接続できます。

## 使用すべき場面

- クライアントを変更せずに、Agent を「標準的な LLM」として公開したい場合。
- OpenAI の SSE 形式に一致するツール呼び出しイベントを伴うストリーミングが必要な場合。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-chat-completions-web</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

注意: このモジュールが提供するのはフレームワーク非依存のコアアダプターのみです — HTTP/SSE は、対応する Spring Boot Starter（推奨）または独自のコントローラーを通じて配線してください。

## コアアダプター

```java
import io.agentscope.core.chat.completions.streaming.ChatCompletionsStreamingAdapter;
import io.agentscope.core.chat.completions.model.ChatCompletionsRequest;
import io.agentscope.core.chat.completions.model.ChatCompletionsChunk;
import reactor.core.publisher.Flux;

ChatCompletionsStreamingAdapter adapter = new ChatCompletionsStreamingAdapter();

// OpenAI 形式のリクエストを Agent の呼び出しへ変換し、Agent のイベントを OpenAI のチャンクへ変換する
Flux<ChatCompletionsChunk> stream = adapter.stream(agent, request);
```

このアダプターは、AgentScope の `Event` ストリーム（`REASONING` や `TOOL_RESULT` を含む）を、OpenAI 互換の `ChatCompletionsChunk` オブジェクトへ変換します。

- テキストの差分 → `delta.content`
- ツール呼び出し → `delta.tool_calls[]`
- ストリームの終了 → `finish_reason` を持つチャンク

## Spring Boot で SSE として公開する

最も簡単なのは Starter を使う方法です — コントローラーを自動登録してくれます。手動で行う場合は次のとおりです。

```java
@RestController
public class ChatController {
    private final ChatCompletionsStreamingAdapter adapter = new ChatCompletionsStreamingAdapter();
    @Autowired private Agent agent;

    @PostMapping(value = "/v1/chat/completions",
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatCompletionsRequest req) {
        return adapter.stream(agent, req)
            .map(this::toSseLine);  // シリアライズして "data: ..." でラップする
    }
}
```

## モデルのルーティング

OpenAI のクライアントは `model` フィールドを送信します。コントローラー層でルーティングしてください。

```java
String model = req.getModel();   // 例: "gpt-4o"；異なる Agent へルーティングする
Agent target = agentRegistry.lookup(model);
return adapter.stream(target, req);
```

## 相性の良い組み合わせ

- **AG-UI**: イベントセマンティクスを伴う、きめ細かい UI レンダリングに。
- **Chat Completions Web**: OpenAI 互換性に重点を置いた、シンプルな LLM 形式の統合に。
