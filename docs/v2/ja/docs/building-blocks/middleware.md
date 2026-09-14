---
title: "Middleware"
description: "エージェントのライフサイクルにおける主要なポイントで振る舞いをインターセプトし、拡張する"
---

## 概要

エージェントの Middleware を使うと、エージェントやモデルのコードを変更することなく、エージェントの実行フローの主要なポイントにカスタムロジック(ロギング、トレーシング、入力の書き換え、アクセス制御など)を差し込むことができます。

AgentScope Java では、外側の返信フローから生のモデル API 呼び出しまでをカバーする5つの箇所に hook できます。

| 位置 | 種類 | 説明 |
|----------|------|-------------|
| `onAgent` | オニオン型 | すべての ReAct ラウンド、ツール実行、最終出力を含む、返信フロー全体をラップする |
| `onReasoning` | オニオン型 | ReAct ループの1つの推論ステップ(入力の組み立て → モデル呼び出し → ストリーミングデコード)をラップする |
| `onActing` | オニオン型 | 単一のツール呼び出しの実行をラップする |
| `onModelCall` | オニオン型 | 生の `ChatModel` API 呼び出しをラップする —— モデルに最も近い |
| `onSystemPrompt` | トランスフォーマー型 | システムプロンプトが組み立てられるときにトリガーされる。複数の middleware が順番に実行され、それぞれが前の出力を変換する |

この2つの種類には違いがあります。

- **オニオン型** —— middleware は次のハンドラをラップします。`next.apply(input)` の前後にロジックを挿入し、中間のイベントストリームを観測できます。
- **トランスフォーマー型** —— middleware がパイプラインを形成します。前の出力が次の入力になります。「内側のレイヤー」という概念はありません。

次の図は、これらの hook がエージェントのライフサイクルの中でどのようにネストしているかを示しています。`onSystemPrompt` は、推論ステップがシステムプロンプトを組み立てるときに発火するため、`onReasoning` の内側にネストされています。

```text
onAgent/
└── ReAct ループ(各ラウンド)/
    ├── onReasoning/
    │   ├── onSystemPrompt(システムプロンプトを組み立てる)
    │   └── onModelCall(モデル API 呼び出し)
    └── onActing(ツール呼び出しごと)
```

:::{note}
`onActing` はエージェントランタイム内のツール実行のみをラップします。外部実行によってエージェントの外で実行されるツールは、`onActing` によって追跡されません。
:::

## Middleware を組み込む

AgentScope は、一連の hook を単一の `MiddlewareBase` 実装にまとめています——1つの middleware クラスは、5つの hook のうち任意の部分集合を実装できます(実装しなかった残りはデフォルトで `next.apply(input)` になります)。インスタンスをビルダーの `middlewares(...)` に渡します。

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import java.util.List;

ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .sysPrompt("あなたは親切なアシスタントです。")
                .model(model)
                .toolkit(toolkit)
                .middlewares(List.of(new OtelTracingMiddleware()))
                .build();
```

`middleware(...)`(単数形)は1つを追加し、`middlewares(...)` は `List<? extends MiddlewareBase>` を受け取ります。middleware が実装していない hook は、コストゼロでスキップされます。

## 組み込みの Middleware

### OtelTracingMiddleware

`OtelTracingMiddleware`(`io.agentscope.core.tracing`)は、エージェントのライフサイクルに [OpenTelemetry](https://opentelemetry.io/docs/specs/semconv/gen-ai/) のトレーシングを組み込みます。`onAgent`、`onModelCall`、`onActing` を計装し、ネストしたスパンを生成します。

- `invoke_agent <name>` —— 返信全体をラップする
- `chat <model>` —— 各モデル API 呼び出しをラップする
- `execute_tool <name>` —— 各ツール実行をラップする

OpenTelemetry SDK が設定されていない場合(デフォルトの no-op プロバイダのみの場合)、すべての hook は `next.apply(input)` へ即座に短絡します —— オーバーヘッドはほぼゼロです。

`OtelTracingMiddleware` はプロセス全体の `GlobalOpenTelemetry` インスタンスを読み取ります。自らスパンをエクスポートするアプリケーションは、AgentScope に加えて OpenTelemetry SDK と OTLP エクスポーターが必要です。OpenTelemetry の BOM を通じてバージョンを揃えてください(以下のバージョンは、AgentScope が現在使用しているものと一致しています)。

```xml
<properties>
    <opentelemetry.version>1.61.0</opentelemetry.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-bom</artifactId>
            <version>${opentelemetry.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-sdk</artifactId>
    </dependency>
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-exporter-otlp</artifactId>
    </dependency>
</dependencies>
```

エージェントを構築する前に、プロセスごとに1回 SDK をビルドして登録してください。この例のオプションの環境変数には、`Authorization` ヘッダーを要求するバックエンド(Langfuse を含む)向けに、`Basic <base64-credentials>` のような値を設定できます。

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

String endpoint =
        System.getenv().getOrDefault(
                "OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318/v1/traces");
String authorization = System.getenv("OTEL_EXPORTER_OTLP_AUTHORIZATION");

var exporterBuilder = OtlpHttpSpanExporter.builder().setEndpoint(endpoint);
if (authorization != null && !authorization.isBlank()) {
    exporterBuilder.addHeader("Authorization", authorization);
}

SdkTracerProvider tracerProvider =
        SdkTracerProvider.builder()
                .addSpanProcessor(
                        BatchSpanProcessor.builder(exporterBuilder.build()).build())
                .build();

OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .buildAndRegisterGlobal();
Runtime.getRuntime().addShutdownHook(new Thread(tracerProvider::close));

ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .sysPrompt("あなたは親切なアシスタントです。")
                .model(model)
                .toolkit(toolkit)
                .middleware(new OtelTracingMiddleware())
                .build();
```

SDK は middleware が使われる前に登録されている必要があります。ランタイム(たとえば Spring Boot の OpenTelemetry 自動設定)がすでに `GlobalOpenTelemetry` を登録している場合は、それを再利用し、middleware だけを追加してください。新しいセットアップでは、非推奨の `TracerRegistry.register(...)` を呼び出さないでください。バッチプロセッサが保留中のスパンをフラッシュできるよう、アプリケーションのシャットダウン時に `SdkTracerProvider` をクローズしてください。

各返信は、エージェント名、セッション ID、モデル名、トークン数、ツール名、入力などの属性を持つ、ネストしたスパンツリーを生成します。

### TaskReminderMiddleware

`TaskReminderMiddleware`(`io.agentscope.core.middleware`)は組み込みの `TodoTools` と対になって動作します。各推論ステップの前に、現在の `AgentState.tasksContext` を `<system-reminder>` としてレンダリングし、コンテキストに注入することで、長時間実行されるタスクを計画に沿わせ続けます。

`enableTaskList(true)` によって `TodoTools` と一緒に有効化します。

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.builtin.TodoTools;

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new TodoTools());

ReActAgent agent =
        ReActAgent.builder()
                .name("planner")
                .sysPrompt("あなたはタスクをステップごとに計画します。")
                .model(model)
                .toolkit(toolkit)
                .enableTaskList(true)
                .build();
```

### FinalAnswerFilterMiddleware

`FinalAnswerFilterMiddleware` は、最後の ReAct 推論ラウンドのテキストのみを公開します。ツール呼び出しを生成したラウンドのテキストは抑制されますが、ツールやその他のテキスト以外のイベントは通常どおりストリーミングされ続けます。

```java
import io.agentscope.core.middleware.FinalAnswerFilterMiddleware;

ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .model(model)
                .toolkit(toolkit)
                .middleware(new FinalAnswerFilterMiddleware())
                .build();
```

この middleware は、ツール呼び出しが観測されないと判明するまでそのラウンドが最終ラウンドかどうかわからないため、モデル呼び出しが終わるまで各ラウンドのテキストをバッファリングします。

## カスタム Middleware

`MiddlewareBase`(`io.agentscope.core.middleware`)を実装し、必要な hook だけをオーバーライドします。

各オニオン型 hook は `next` 関数を受け取ります——`next.apply(input)` を呼び出すと次のレイヤーに入ります。前後にロジックを挿入したり、Reactor のオペレータ(`doOnNext` / `flatMap` / `map` など)を使ってイベントストリームを観測・書き換えたりできます。

```java
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.middleware.ReasoningInput;
import java.util.function.Function;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** agent / reasoning / model_call / system_prompt を同時に観測する。 */
public class FullObservabilityMiddleware implements MiddlewareBase {

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent, RuntimeContext ctx, AgentInput input, Function<AgentInput, Flux<AgentEvent>> next) {
        System.out.println("[agent] " + agent.getName() + " を開始");
        return next.apply(input)
                .doOnComplete(() -> System.out.println("[agent] " + agent.getName() + " を終了"));
    }

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent, RuntimeContext ctx, ReasoningInput input, Function<ReasoningInput, Flux<AgentEvent>> next) {
        System.out.println("[reasoning] 開始");
        return next.apply(input).doOnComplete(() -> System.out.println("[reasoning] 終了"));
    }

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent, RuntimeContext ctx, ModelCallInput input, Function<ModelCallInput, Flux<AgentEvent>> next) {
        System.out.println("[model_call] " + input.model().getClass().getSimpleName());
        return next.apply(input).doOnComplete(() -> System.out.println("[model_call] 完了"));
    }

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext ctx, String currentPrompt) {
        System.out.println("[system_prompt] length=" + currentPrompt.length());
        return Mono.just(currentPrompt);
    }
}
```

各 hook の入力レコード型(`io.agentscope.core.middleware` 配下):

| Hook | 入力レコード | フィールド |
|------|--------------|--------|
| `onAgent` | `AgentInput` | `msgs: List<Msg>` |
| `onReasoning` | `ReasoningInput` | `messages: List<Msg>`, `tools: List<ToolSchema>`, `options: GenerateOptions` |
| `onActing` | `ActingInput` | `toolCalls: List<ToolUseBlock>` |
| `onModelCall` | `ModelCallInput` | `messages`, `tools`, `options`, `model: Model` |
| `onSystemPrompt` | `String` | 現在のプロンプト |

次のレイヤーに流れるフィールドを置き換えるには、新しい入力レコードを構築してから `next.apply(...)` を呼び出します。

実行可能な例: `agentscope-examples/documentation/.../middleware/CustomizedMiddlewareExample.java`、`middleware/ModelCallMiddlewareExample.java`、`middleware/SystemPromptMiddlewareExample.java`。

### RuntimeContext の読み取り

すべての `MiddlewareBase` hook は、この `call` / `stream` にバインドされた [`RuntimeContext`](./agent.md#runtimecontext-呼び出しごとのコンテキスト) を第2引数として受け取ります——セッションフィールドや型付き/文字列の属性を読み取ったり、下流の hook やツールに値を伝えるために書き戻したりできます。

```java
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import java.util.function.Function;
import reactor.core.publisher.Flux;

/** ユーザー / リクエスト ID をログに出力し、下流のツールのためにトレース ID を伝播する。 */
public class RequestContextMiddleware implements MiddlewareBase {

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent, RuntimeContext ctx, AgentInput input, Function<AgentInput, Flux<AgentEvent>> next) {
        System.out.printf(
                "[req] user=%s session=%s reqId=%s%n",
                ctx.getUserId(),
                ctx.getSessionId(),
                ctx.get("request_id"));
        ctx.put("trace_id", java.util.UUID.randomUUID().toString());  // 後続の hook / ツールから見える
        return next.apply(input);
    }
}
```

留意すべき点:

- 同じ `RuntimeContext` インスタンスは、その返信内のすべての hook とツールで共有されます。内部のマップはスレッドセーフなので、どの hook からの `put` も安全です。
- リクエストごとの状態を middleware インスタンスのフィールドにキャッシュしないでください——middleware のインスタンスは通常、複数のエージェント/呼び出しをまたいで再利用されます。代わりに `RuntimeContext` や Reactor の `contextWrite` を使ってください。
- ビルダーにグローバルな `toolExecutionContext` も設定されている場合、フレームワークはツールへディスパッチする際に、呼び出しごとのコンテキストの後にそれをマージします(キーが衝突した場合は呼び出しごとのコンテキストが優先されます)。

### 実行順序

オニオン型 hook(`onAgent`、`onReasoning`、`onActing`、`onModelCall`)は `MiddlewareBase.order()` によって順序付けられます——**値が大きいほど外側になります**。デフォルトの順序は `1` で、同じ順序の middleware はビルダーへの登録順を保持します。

```
middlewares = [mw1(order=2), mw2(order=1)]
// 順序:
// mw1 pre → mw2 pre → inner → mw2 post → mw1 post
```

カスタム middleware をデフォルトの順序に対して相対的に移動させるには `order()` をオーバーライドします。たとえば、順序が `0` の middleware は、デフォルトの順序である `1` を保つ middleware の内側で実行されます。

```java
MiddlewareBase lowerPriority = new MiddlewareBase() {
    @Override
    public int order() {
        return 0;
    }
};
```

ストリーミング / イベントを発行する hook では、内側の middleware が発行された各イベントを先に見ます。

```
mw1_pre → mw2_pre → mw2_event → mw1_event → ... → mw2_post → mw1_post
```

トランスフォーマー型 hook(`onSystemPrompt`)—— **左から右へのパイプライン**です。

```
middlewares = [mw1, mw2]
// originalPrompt → mw1.onSystemPrompt() → mw2.onSystemPrompt() → final
```

1回の返信全体における hook の実行順序:

```
onAgent
  └── ReAct ラウンドごとに:
        ├── onReasoning
        │     ├── モデル入力を準備 → onSystemPrompt
        │     └── onModelCall
        └── onActing(ツール呼び出しごと)
```

## 実践的な例

### 計測用 Middleware

以下の middleware は、各モデル呼び出しの実時間を記録します。

```java
import io.agentscope.core.agent.Agent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import java.util.function.Function;
import reactor.core.publisher.Flux;

public class TimingMiddleware implements MiddlewareBase {
    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent, ModelCallInput input, Function<ModelCallInput, Flux<AgentEvent>> next) {
        long start = System.nanoTime();
        return next.apply(input)
                .doFinally(sig -> {
                    long ms = (System.nanoTime() - start) / 1_000_000;
                    System.out.println(
                            "[timing] " + agent.getName() + ": " + ms + "ms");
                });
    }
}
```

### レート制限 Middleware

2回のモデル呼び出しの間に最小間隔を強制します。

```java
import io.agentscope.core.agent.Agent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RateLimitMiddleware implements MiddlewareBase {

    private final long minIntervalMs;
    private final AtomicLong lastCall = new AtomicLong(0);

    public RateLimitMiddleware(Duration minInterval) {
        this.minIntervalMs = minInterval.toMillis();
    }

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent, ModelCallInput input, Function<ModelCallInput, Flux<AgentEvent>> next) {
        long now = System.currentTimeMillis();
        long wait = minIntervalMs - (now - lastCall.get());
        Mono<Void> delay = wait > 0 ? Mono.delay(Duration.ofMillis(wait)).then() : Mono.empty();
        return delay.thenMany(next.apply(input))
                .doOnSubscribe(s -> lastCall.set(System.currentTimeMillis()));
    }
}
```

### 動的なシステムプロンプト Middleware

システムプロンプトにランタイムコンテキストを注入します。あるいは、サンプル `middleware/SystemPromptMiddlewareExample.java` を再利用してください。

```java
import io.agentscope.core.agent.Agent;
import io.agentscope.core.middleware.MiddlewareBase;
import java.time.Instant;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

public class DynamicContextMiddleware implements MiddlewareBase {

    private final Supplier<String> contextFn;

    public DynamicContextMiddleware(Supplier<String> contextFn) {
        this.contextFn = contextFn;
    }

    @Override
    public Mono<String> onSystemPrompt(Agent agent, String currentPrompt) {
        return Mono.just(currentPrompt + "\n\n## 現在のコンテキスト\n" + contextFn.get());
    }
}

// 組み込み方:
// .middlewares(List.of(new DynamicContextMiddleware(() -> "時刻: " + Instant.now())))
```

### モデルフォールバック Middleware

プライマリが失敗した場合にバックアップモデルへ切り替えます。

```java
import io.agentscope.core.agent.Agent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.Model;
import java.util.function.Function;
import reactor.core.publisher.Flux;

public class ModelFallbackMiddleware implements MiddlewareBase {

    private final Model fallback;

    public ModelFallbackMiddleware(Model fallback) {
        this.fallback = fallback;
    }

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent, ModelCallInput input, Function<ModelCallInput, Flux<AgentEvent>> next) {
        return next.apply(input)
                .onErrorResume(err -> {
                    System.err.println("プライマリモデルが失敗しました: " + err.getMessage()
                            + "。フォールバックへ切り替えます");
                    return next.apply(
                            new ModelCallInput(
                                    input.messages(),
                                    input.tools(),
                                    input.options(),
                                    fallback));
                });
    }
}
```

:::{tip}
単純なプライマリ→バックアップのフォールバックであれば、`ReActAgent.Builder` はすでに `fallbackModel(...)` と `maxRetries(...)` を直接公開しています——middleware は不要です。
:::

### すべてのツールが拒否された場合にエージェントを停止する

ユーザーが HITL を通じて、ある推論ステップのすべてのツール呼び出しを拒否した場合、エージェントはデフォルトで次の推論イテレーションへ進みます(後方互換)。このシナリオでエージェントを停止させるには、`AllToolsDeniedEvent` を観測して `RequestStopEvent` を発行する `onActing` middleware を書きます。

```java
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AllToolsDeniedEvent;
import io.agentscope.core.event.RequestStopEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import java.util.function.Function;
import reactor.core.publisher.Flux;

public class StopOnAllDeniedMiddleware implements MiddlewareBase {

    @Override
    public Flux<AgentEvent> onActing(
            Agent agent, RuntimeContext ctx, ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next) {
        return next.apply(input)
                .flatMap(event -> {
                    if (event instanceof AllToolsDeniedEvent) {
                        return Flux.just(
                                event,
                                new RequestStopEvent(
                                        "ユーザーによってすべてのツールが拒否されました",
                                        GenerateReason.ALL_TOOLS_DENIED));
                    }
                    return Flux.just(event);
                });
    }
}
```

これを組み込むと、すべてのツールが拒否されたときにエージェントは即座に停止し、`GenerateReason.ALL_TOOLS_DENIED` を返します。

```java
ReActAgent agent =
        ReActAgent.builder()
                .name("guarded")
                .sysPrompt("...")
                .model(model)
                .toolkit(toolkit)
                .middlewares(List.of(new StopOnAllDeniedMiddleware()))
                .build();
```
