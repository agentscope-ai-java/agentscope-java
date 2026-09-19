---
title: モデル
description: AgentScope Java で LLM モデルプロバイダーを設定・接続する
---

## 概要

モデル層は、共通の契約とプロバイダー実装を分離しています。`agentscope-core` は共通 API(`Model`、`ChatModelBase`、`Formatter`、`ModelRegistry`、および `ModelProvider` SPI)を保持します。OpenAI、DashScope、Gemini、Anthropic、Ollama の実装は、それぞれ独自のモデル拡張モジュールに存在します。

実行時において、モデル層は2階層になっています。最上位には(`io.agentscope.core.credential` をベースにした)**Credential(認証情報)** があり、プロバイダーの API 認証フィールドを保持します。その下には、認証情報に紐づく具体的な推論実装である **Chat Model(チャットモデル)** があります。

```text
CredentialBase/
└── ChatModelBase/
    ├── OpenAIChatModel
    ├── AnthropicChatModel
    ├── DashScopeChatModel
    ├── GeminiChatModel
    └── OllamaChatModel
```

**認証情報** はプロバイダーの API 認証フィールド(`apiKey`、`baseUrl` など)を保持します。認証情報を起点に `listModels()` を呼び出すことで、そのプロバイダー配下で利用可能なモデルを列挙できます(`Mono<List<ModelCard>>` を返します)。

この階層構造は、フロントエンドにおける自然な UX(先に認証情報を登録し、その配下からモデルを選ぶ)と一致しています。そのため UI は一度認証を行うだけで、そのプロバイダーがサポートするすべてのモデルを表示できます。

## モデル拡張モジュール

プロバイダー固有のモデル実装は `agentscope-core` から独立した拡張モジュールへと移動しました。各プロバイダーモジュールは、チャットモデル、認証情報、フォーマッター、DTO、例外、SDK/API クライアントなどを自身で保有します。

| プロバイダー | Maven アーティファクト | 主なパッケージ |
|----------|----------------|--------------|
| OpenAI | `agentscope-extensions-model-openai` | `io.agentscope.extensions.model.openai` |
| DashScope | `agentscope-extensions-model-dashscope` | `io.agentscope.extensions.model.dashscope` |
| Gemini | `agentscope-extensions-model-gemini` | `io.agentscope.extensions.model.gemini` |
| Anthropic | `agentscope-extensions-model-anthropic` | `io.agentscope.extensions.model.anthropic` |
| Ollama | `agentscope-extensions-model-ollama` | `io.agentscope.extensions.model.ollama` |

### 移行チェックリスト

1. プロバイダーの拡張モジュール依存関係を追加します。例えば DashScope の場合:

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-dashscope</artifactId>
</dependency>
```

他のプロバイダーのアーティファクトも同じパターンに従います: `agentscope-extensions-model-openai`、`agentscope-extensions-model-gemini`、`agentscope-extensions-model-anthropic`、`agentscope-extensions-model-ollama`。

2. プロバイダーの import を `io.agentscope.core.model.*` から `io.agentscope.extensions.model.<provider>.*` に置き換えます。
3. プロバイダーのフォーマッターの import を `io.agentscope.core.formatter.<provider>.*` から `io.agentscope.extensions.model.<provider>.formatter.*` に置き換えます。
4. Spring Boot アプリケーションの場合、汎用的なモデル生成パスを、対応するプロバイダー専用のスターターとその `agentscope.<provider>.*` プロパティに置き換えます。

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-dashscope-spring-boot-starter</artifactId>
</dependency>
```

## 生成方法を選ぶ

### 文字列モデル ID

シンプルな非 Spring アプリケーションでは、`dashscope:qwen-plus`、`openai:gpt-4.1-mini`、`deepseek:deepseek-v4-flash` のような `ModelRegistry` の文字列 ID を使用します。対応するモデル拡張モジュールを追加し、`DASHSCOPE_API_KEY`、`OPENAI_API_KEY`、`DEEPSEEK_API_KEY` のようなプロバイダーの標準環境変数を設定したうえで、その ID をエージェントに直接渡します:

```java
ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .model("dashscope:qwen-plus") // ModelRegistry.resolve(modelId) によって内部的に解決される
                .build();
```

拡張モジュールは Java SPI を通じて検出されます。モデルプロバイダーは `DASHSCOPE_API_KEY`、`OPENAI_API_KEY`、`DEEPSEEK_API_KEY`、`GLM_API_KEY`、`ANTHROPIC_API_KEY`、`GEMINI_API_KEY` のような標準環境変数を読み取ります。Ollama は `OLLAMA_BASE_URL` が存在すればそれを読み取り、存在しない場合はローカルの Ollama エンドポイントをデフォルトとして使用します。

### 明示的なモデルビルダー

カスタムの API キー、ベース URL、フォーマッター、トランスポート、タイムアウト、生成オプション、その他プロバイダー固有の設定が必要な場合は、モデルを明示的にビルドし、`Model` インスタンスをエージェントに渡します:

```java
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;

DashScopeChatModel model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .stream(true)
                .formatter(new DashScopeChatFormatter())
                .build();

ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .model(model)
                .build();
```

### Spring Boot アプリケーション

Spring Boot では、`agentscope-openai-spring-boot-starter`、`agentscope-dashscope-spring-boot-starter`、`agentscope-gemini-spring-boot-starter`、`agentscope-anthropic-spring-boot-starter`、`agentscope-ollama-spring-boot-starter` のようなプロバイダー専用スターターの利用を推奨します。これらのスターターは対応するモデル拡張に直接依存し、Spring 管理下の `Model` Bean を生成するため、汎用スターターは AgentScope の共通インフラに専念できます。これらは静的な `ModelRegistry` を通じてモデルを生成するわけではありません。上級者は常に独自の `Model` Bean を提供できます。

OpenAI の例:

```yaml
agentscope:
  model:
    provider: openai
  openai:
    api-key: ${OPENAI_API_KEY}
    model-name: gpt-4.1-mini
    stream: true
```

#### ビルダーカスタマイザー

プロバイダー専用のスターターは、自動構成されるチャットモデルビルダー向けに、順序付けされた Spring Bean カスタマイザーも公開しています。プロパティバインディングで一般的な設定はカバーできるものの、カスタムフォーマッター、デフォルトの生成オプション、プロキシ/クライアント設定、プロバイダー固有のフラグなど、ビルダーだけが持つオプションを調整したい場合に使用します。

| スターター | カスタマイザーの型 |
|---------|-----------------|
| `agentscope-openai-spring-boot-starter` | `OpenAIChatModelBuilderCustomizer` |
| `agentscope-dashscope-spring-boot-starter` | `DashScopeChatModelBuilderCustomizer` |
| `agentscope-gemini-spring-boot-starter` | `GeminiChatModelBuilderCustomizer` |
| `agentscope-anthropic-spring-boot-starter` | `AnthropicChatModelBuilderCustomizer` |
| `agentscope-ollama-spring-boot-starter` | `OllamaChatModelBuilderCustomizer` |

カスタマイザー Bean は、スターターのプロパティがバインドされた後、`builder.build()` が呼び出される前に適用されます。複数のカスタマイザーがサポートされており、Spring の `@Order` / `Ordered` による順序付けに従います。

```java
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.spring.boot.openai.OpenAIChatModelBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration(proxyBeanMethods = false)
class ModelCustomizerConfiguration {

    @Bean
    @Order(0)
    OpenAIChatModelBuilderCustomizer openAIModelDefaults() {
        return builder ->
                builder.defaultOptions(
                        GenerateOptions.builder()
                                .temperature(0.2)
                                .parallelToolCalls(false)
                                .build());
    }
}
```

## ModelRegistry と ModelCreationContext

`ModelRegistry` は、モデルインスタンスの生成とルックアップのためのグローバルなレジストリであり、複数の解決戦略をサポートします。解決の際には、次の優先順位で試行されます: `ModelRegistry.register(name, model)` によって直接登録された名前付きモデルインスタンス、`registerFactory(regex, factory)` によって登録されたカスタムファクトリ、そして Java SPI の仕組みを通じて拡張モジュールから自動的に検出される `ModelProvider` 実装です。

シンプルなシナリオでは、`provider:model` 形式の文字列 ID とプロバイダーの標準環境変数を組み合わせて使うことを推奨します。きめ細かい制御が必要な場合は、明示的なモデルビルダーを使用してください。`ModelCreationContext` は主に、モデルを動的に解決する必要がある統合層のコード向けです。

### 高度な統合コンテキスト

`ModelCreationContext` は、マルチテナントゲートウェイ、プラグインシステム、フレームワークアダプターなど、具体的なプロバイダービルダーをインポートせずに動的にモデルを生成しなければならない統合層向けです。API キー、ベース URL、エンドポイントパス、ストリームモード、拡張定義のオプション/コンポーネントといった共通の値を SPI プロバイダーへ渡すことができます:

```java
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;

ModelCreationContext context =
        ModelCreationContext.builder()
                .apiKey(tenantApiKey)
                .baseUrl(tenantBaseUrl)
                .stream(false)
                // プロバイダーがドキュメント化した名前をキーとする、拡張定義のスカラーオプション。
                .option("contextWindowSize", 128000)
                // より豊富なプロバイダー設定、トランスポート、フォーマッターのための型キー付きコンポーネント。
                .component(
                        GenerateOptions.class,
                        GenerateOptions.builder()
                                .parallelToolCalls(false)
                                .build())
                .build();

Model model = ModelRegistry.resolve("openai:gpt-4.1-mini", context);
```

### キャッシュポリシー

`ModelRegistry` は、単純な `provider:model` 文字列から解決されたモデルをキャッシュします。コンテキストを考慮した生成はデフォルトではキャッシュされません。これは、異なるテナントの API キー、ベース URL、ストリーム設定を持つモデルインスタンスを誤って再利用してしまうのを防ぐためです。

| ポリシー | 動作 |
|--------|----------|
| `DEFAULT` | `resolve(String)` は従来のモデル ID キャッシュを維持します。`resolve(String, nonEmptyContext)` はキャッシュされません。 |
| `DISABLED` | 一切キャッシュしません。解決のたびに新しいモデルインスタンスが生成されます。 |
| `ENABLED` | 呼び出し側が明示的に opt-in した場合のみキャッシュします。テナントや設定固有の識別には `cacheId(...)` を使用してください。 |

`CachePolicy.ENABLED` を `option(...)` や `component(...)` と併用する場合、ユーザーは `cacheId` を指定する必要があります。

### ModelProvider SPI

プロバイダー拡張モジュールは、`META-INF/services/io.agentscope.core.model.spi.ModelProvider` を通じて Java SPI によって検出されます。プロバイダーは `supports(String, ModelCreationContext)` と `create(String, ModelCreationContext)` を実装することで、コンテキストの値を利用できます。シンプルなプロバイダーは、コンテキストを考慮したメソッドに互換性のあるデフォルト実装が用意されているため、従来どおり `supports(String)` と `create(String)` メソッドを実装し続けることができます。

## チャットモデル

**Chat Model(チャットモデル)** は、会話とツール呼び出しを駆動する LLM であり、入出力は複数のモダリティにまたがることがあります。AgentScope Java は現在、以下を同梱しています:

| プロバイダー | クラス | 備考 |
|----------|-------|-------|
| OpenAI | `OpenAIChatModel` | Chat Completions API。vLLM や OpenAI 互換エンドポイント(DeepSeek、Kimi など)でも動作します |
| Anthropic | `AnthropicChatModel` | Claude モデル。プロンプトキャッシュと thinking に対応 |
| DashScope | `DashScopeChatModel` | Qwen モデル。マルチモーダル(vision/audio/video)、reasoning に対応 |
| Gemini | `GeminiChatModel` | Google Gemini。マルチモーダルに対応 |
| Ollama | `OllamaChatModel` | ローカルホストの LLM。認証情報は任意 |

プロバイダーの認証情報クラスは、それぞれのモデル拡張モジュールに存在します。例えば `OpenAICredential`、`AnthropicCredential`、`DashScopeCredential`、`GeminiCredential`、`OllamaCredential` です。`DeepSeekCredential`、`KimiCredential`、`XAICredential` のような OpenAI 互換の認証情報は、引き続き core から利用できます。

### チャットモデルを作成する

各チャットモデルはビルダーを使って構築します。最も一般的なフィールドは `apiKey`、`modelName`、`stream`、`formatter`、`defaultOptions` です。代表的な3つのセットアップを次に示します:

<Tabs>

<Tab title="ストリーミング">

```java
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;

DashScopeChatModel model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .stream(true)
                .formatter(new DashScopeChatFormatter())
                .build();
```

</Tab>
<Tab title="ツール">

```java
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;

DashScopeChatModel model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .stream(false)
                .formatter(new DashScopeChatFormatter())
                .defaultOptions(
                        GenerateOptions.builder()
                                .parallelToolCalls(false)
                                .build())
                .build();
```

</Tab>
<Tab title="推論">

```java
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;

DashScopeChatModel model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen3-235b-a22b-thinking-2507")
                .stream(true)
                .enableThinking(true)
                .formatter(new DashScopeChatFormatter())
                .defaultOptions(
                        GenerateOptions.builder()
                                .thinkingBudget(2048)
                                .build())
                .build();
```

</Tab>

</Tabs>

共通のビルダーフィールド:

| フィールド | 型 | 説明 |
|-------|------|-------------|
| `apiKey` | `String` | API キー(一部のプロバイダーは `credential(...)` も受け付けます) |
| `modelName` | `String` | モデル識別子(例:`"qwen-plus"`) |
| `stream` | `boolean` | 出力をストリーミングするかどうか |
| `defaultOptions` | `GenerateOptions` | プロバイダー固有のオプション(`temperature`、`maxTokens`、`thinkingBudget`、`parallelToolCalls` など) |
| `formatter` | `Formatter` | デフォルトのメッセージフォーマッターを上書きする |
| `baseUrl` | `String` | カスタムのサービスエンドポイント(例:OpenAI 互換プロキシ) |

### チャットモデルを呼び出す

`Model` インターフェースは、統一された `stream(messages, tools, options)` を公開しており、`Flux<ChatResponse>` を返します:

```java
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import java.util.List;

DashScopeChatModel model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .stream(true)
                .formatter(new DashScopeChatFormatter())
                .build();

model.stream(
                List.of(new UserMessage("Count from 1 to 5.")),
                /* tools = */ List.of(),
                GenerateOptions.builder().build())
        .doOnNext(chunk -> System.out.println("チャンク: " + chunk.getContent()))
        .doOnComplete(() -> System.out.println("ストリーム完了"))
        .blockLast();
```

`ChatResponse` は、コンテンツブロックのリスト(`TextBlock`、`ThinkingBlock`、`ToolUseBlock`、`DataBlock`)と、トークン数と所要時間を記録する `ChatUsage` を保持します。

実際には、モデルを直接呼び出すのではなく `ReActAgent` 経由で呼び出すことがほとんどです。あるいは、ワークスペース、セッション永続化、サブエージェントも必要な場合は
[`HarnessAgent`](/v2/ja/docs/harness/architecture#harnessagent-を組み立てる) を使います——どちらのビルダーの `.model(...)` も、上記で構築した同じ `ChatModelBase`
インスタンスを受け付けます。軽量な直接呼び出しについては `agentscope-examples/documentation/.../model/ModelRegistryExample.java` を参照してください。

### 構造化出力を生成する

エージェント層は、`ReActAgent.call(msgs, structuredOutputClass, runtimeContext)` を通じて、モデル出力を Java の POJO にバインドするための便利なオーバーロードを提供します:

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import java.util.List;

public class WeatherInfo {
    public String city;
    public double temperature;
    public String unit;
}

Msg msg =
        agent.call(
                        List.of(new UserMessage("What's the weather in Shanghai?")),
                        WeatherInfo.class,
                        RuntimeContext.empty())
                .block();

WeatherInfo info = msg.getStructuredData(WeatherInfo.class);
```

仕組み: フレームワークは対象クラスから強制的な構造化ツール呼び出しを合成し、モデル出力を検証・修復したうえで、結果を `structured_output` キーの下の `Msg.metadata` に書き込みます。これにより `getStructuredData(Class)` が直接デシリアライズできるようになります。完全な例: `agentscope-examples/documentation/.../structuredoutput/StructuredOutputExample.java`。

#### 構造化出力パスの選択

フレームワークは2つの構造化出力パスを提供します:

| パス | 条件 | 仕組み |
|------|-----------|-----------|
| **Native(ネイティブ)** | `supportsNativeStructuredOutput() = true` | `response_format` + `json_schema` を使って JSON 出力を直接生成する |
| **Fallback(フォールバック)**(デフォルト) | `supportsNativeStructuredOutput() = false` | 合成ツール `generate_response` を注入する。モデルはツール呼び出し経由で構造化データを返す |

ネイティブパスが失敗した場合(例:モデルが HTTP 400 を返す)、フレームワークは**自動的に**合成ツールパスへ**フォールバック**します——ユーザー側の対応は不要です。

#### プロバイダーごとのデフォルト動作

| プロバイダー | `supportsNativeStructuredOutput` | 備考 |
|----------|----------------------------------|-------|
| OpenAI(GPT-4o など) | `true` | ネイティブな `json_schema` をサポート |
| OpenAI(DeepSeek/GLM フォーマッター) | `false` | サポートされていない。自動的にフォールバック |
| DashScope | `false` | ネイティブエンドポイントは `json_object` のみをサポートし `json_schema` はサポートしない。デフォルトでフォールバック |
| Anthropic | `false`(デフォルト) | — |

> **DashScope ユーザー向け**: Thinking モード(`enableThinking(true)`)は構造化出力をまったくサポートしません——フレームワークはフォールバックパスを強制します。

#### 明示的な設定

モデル/エンドポイントが `json_schema` をサポートしていると確認できる場合は、ビルダー経由でネイティブパスを有効化します:

```java
DashScopeChatModel model = DashScopeChatModel.builder()
        .apiKey(System.getenv("DASHSCOPE_API_KEY"))
        .modelName("qwen-plus")
        .nativeStructuredOutput(true)  // ネイティブな json_schema パスを明示的に有効化する
        .build();
```

#### ツール呼び出しを伴う構造化出力

エージェントがツールと構造化出力の両方を持つ場合、一部の OpenAI 互換プロバイダー(Kimi、Deepseek など)は `response_format` 制約を優先し、ツール呼び出しを完全にスキップしてしまいます。これを解決するには `nativeStructuredOutputWithTools(false)` を設定します:

```java
OpenAIChatModel model = OpenAIChatModel.builder()
        .apiKey("...")
        .baseUrl("https://api.moonshot.cn/v1")
        .modelName("moonshot-v1-8k")
        .nativeStructuredOutputWithTools(false)
        .build();
```

`DashScopeChatModel` もこのオプションをサポートしています。ネイティブな OpenAI モデル(GPT-4o など)では、デフォルトの動作で両方が正しく処理されるため、設定は不要です。

### Formatter

**Formatter** は、AgentScope の `Msg` オブジェクトを、各プロバイダーの API が期待するリクエストペイロードへ変換します。チャットモデルビルダーの `formatter(...)` を通じて設定します。各プロバイダーは2種類のフォーマッターを同梱しています:

| 種類 | ユースケース |
|------|----------|
| **ChatFormatter**(デフォルト) | 標準的なシングルエージェントのチャット。各 `Msg` は API メッセージ1件に1対1でマッピングされ、ロール(`USER`、`ASSISTANT`、`SYSTEM`)が保持されます。 |
| **MultiAgentFormatter** | ディベートやモデレーター構成のようなマルチエージェントのシナリオ。連続するエージェントメッセージは集約され、送信者の名前でタグ付けされます。 |

マルチエージェントモードに切り替えるには、MultiAgent バリアントを渡すだけでよく、エージェント側のコード変更は不要です:

```java
import io.agentscope.extensions.model.dashscope.formatter.DashScopeMultiAgentFormatter;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;

DashScopeChatModel model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .stream(true)
                .formatter(new DashScopeMultiAgentFormatter())
                .build();
```

プロバイダーごとのフォーマッターは、現在それぞれのプロバイダー拡張モジュールに存在します:

| プロバイダー | Chat | MultiAgent |
|----------|------|------------|
| DashScope | `DashScopeChatFormatter` | `DashScopeMultiAgentFormatter` |
| OpenAI | `OpenAIChatFormatter` | `OpenAIMultiAgentFormatter` |
| Anthropic | `AnthropicChatFormatter` | `AnthropicMultiAgentFormatter` |
| Gemini | `GeminiChatFormatter` | `GeminiMultiAgentFormatter` |
| Ollama | `OllamaChatFormatter` | `OllamaMultiAgentFormatter` |

プロバイダーのペイロードがこれらのいずれにも合わない場合は、`Formatter<TReq, TResp, TParams>` インターフェース(`io.agentscope.core.formatter`)を実装し、同じ `formatter(...)` ビルダーに渡してください。

### カスタムプロバイダー

新しいプロバイダーを追加する最小限の方法: `CredentialBase` のサブクラスと `ChatModelBase` のサブクラスを実装します。

#### ステップ1: 認証情報を定義する

`CredentialBase` を拡張し、`getChatModelClass()` を実装します:

```java
import io.agentscope.core.credential.CredentialBase;
import io.agentscope.core.model.ChatModelBase;

public class MyProviderCredential extends CredentialBase {

    private final String apiKey;
    private final String baseUrl;

    public MyProviderCredential(String apiKey, String baseUrl) {
        super("my_provider:" + apiKey.substring(0, Math.min(4, apiKey.length())));
        this.apiKey = apiKey;
        this.baseUrl = baseUrl == null ? "https://api.myprovider.com/v1" : baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public Class<? extends ChatModelBase> getChatModelClass() {
        return MyProviderChatModel.class;
    }
}
```

#### ステップ2: チャットモデルを実装する

`ChatModelBase` を拡張し、`doStream` を実装します:

```java
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;
import java.util.List;
import reactor.core.publisher.Flux;

public class MyProviderChatModel extends ChatModelBase {

    private final MyProviderCredential credential;
    private final String modelName;

    public MyProviderChatModel(MyProviderCredential credential, String modelName) {
        this.credential = credential;
        this.modelName = modelName;
    }

    @Override
    protected Flux<ChatResponse> doStream(
            List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        // プロバイダーの API を呼び出し、レスポンスを Flux<ChatResponse> にラップする。
        return Flux.empty();
    }
}
```

#### ステップ3: ModelRegistry に登録する(任意)

`ModelRegistry` を使うと、`ReActAgent.builder().model("provider:model-name")` が文字列からモデルを解決できるようになります:

```java
import io.agentscope.core.model.ModelRegistry;

ModelRegistry.registerFactory(
        "myprov:.*",
        modelId -> new MyProviderChatModel(
                new MyProviderCredential(System.getenv("MYPROV_API_KEY"), null),
                modelId.substring("myprov:".length())));

// これで:
// ReActAgent.builder().model("myprov:my-model-v1")...
```

## フロントエンド統合

### ModelCard とは

`ModelCard`(`credential/ModelCard.java`)は、モデルの能力と制約を宣言的に記述したものです。モデルピッカー、パラメータフォーム、機能トグルといったフロントエンドは、プロバイダー固有のロジックをハードコードすることなく、これを元に動的にレンダリングできます。

現時点では、`ModelCard` は最小限のレコードです:

| メソッド | 型 | 説明 |
|--------|------|-------------|
| `modelName()` | `String` | モデル識別子(例:`"claude-sonnet-4-6"`) |
| `displayName()` | `String` | 人間が読める形式のラベル(例:`"Claude Sonnet 4.6"`) |
| `contextSize()` | `Integer` | 最大コンテキストウィンドウ(トークン単位) |

<Note>

`ModelCard` のスキーマは現段階では意図的に最小限にとどめられています。モデル発見のインフラが成熟するにつれ、機能フラグ(入出力の MIME タイプ)やパラメータスキーマが追加される予定です。

</Note>

### ModelCard を取得する

`CredentialBase#listModels()` を呼び出すと、`Mono<List<ModelCard>>` が返ります:

```java
import io.agentscope.core.credential.ModelCard;
import io.agentscope.extensions.model.anthropic.credential.AnthropicCredential;
import java.util.List;

AnthropicCredential cred = new AnthropicCredential(System.getenv("ANTHROPIC_API_KEY"));
List<ModelCard> cards = cred.listModels().block();

for (ModelCard card : cards) {
    System.out.println(
            card.modelName() + ": context=" + card.contextSize());
}
```

`getChatModelClass()` は対応する `ChatModelBase` サブクラスを返します——リフレクションでデフォルトモデルを構築する際に便利です:

```java
Class<? extends io.agentscope.core.model.ChatModelBase> modelCls = cred.getChatModelClass();
```

この設計により、フロントエンドは1つの認証情報だけで、あるプロバイダー配下のすべてのモデルを発見できます——プロバイダー固有のロジックをハードコードする必要はありません。
