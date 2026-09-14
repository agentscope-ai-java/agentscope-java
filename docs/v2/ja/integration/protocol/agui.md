# AG-UI

## 互換性に関する注意事項

`agentscope-extensions-agui` は、AgentScope v2 の `AgentEvent` ストリームを [AG-UI Protocol](https://github.com/ag-ui-protocol/ag-ui) のイベントに変換し、フロントエンド UI がテキスト、推論、ツール呼び出し、状態、カスタムイベント、トークン使用量、HITL 割り込みを含むエージェントの実行をリアルタイムに描画できるようにします。

`RUN_ERROR` と `RUN_FINISHED` は互いに排他的な終端イベントです。従来の `RUN_ERROR` + `RUN_FINISHED` の順序がどうしても必要な場合にのみ `emitRunFinishedAfterError=true` を設定してください。

`AguiMessage.content` は型付きのメッセージコンテンツとして表現されます。テキストのみを扱うコードパスでは `getTextContent()` を使用してください。

マルチモーダル入力はサポートされていますが、ドキュメント型はまだサポートされていません。

`AguiMessageConverter.toAguiMessage()` は現在のところテキストとツール呼び出しのフィールドのみを保持します。画像、音声、動画、ドキュメントのコンテンツブロックは AG-UI のメッセージコンテンツにシリアライズし直されません。

## いつ使うか

- AgentScope エージェントを AG-UI 互換のフロントエンドやカスタムチャット UI に接続したい場合。
- `RUN_*`、`TEXT_MESSAGE_*`、`TOOL_CALL_*`、`CUSTOM` などの関連する AG-UI イベントを SSE でストリーミングしたい場合。
- フロントエンドツール、ユーザー承認の割り込み、ランタイムコンテキストの伝播、カスタムのイベント変換拡張が必要な場合。

## 依存関係を追加する

手動でアダプターを使う場合は、次を追加します。

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-agui</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

Spring Boot アプリケーションでは starter を使えます。

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-agui-spring-boot-starter</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## クイックスタート

```java
import io.agentscope.core.agui.adapter.AguiAdapterConfig;
import io.agentscope.core.agui.adapter.AguiAgentAdapter;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.agui.model.RunAgentInput;
import reactor.core.publisher.Flux;

AguiAdapterConfig config = AguiAdapterConfig.builder()
    .enableReasoning(true)
    .emitTokenUsage(true)
    .runTimeout(Duration.ofMinutes(5))
    .build();

AguiAgentAdapter adapter = new AguiAgentAdapter(agent, config);

// SSE 経由でフロントエンドに送信するイベント
Flux<AguiEvent> events = adapter.run(runAgentInput);
```

フロントエンドは `threadId`、`runId`、`messages`、`tools`、`state` などのフィールドを含む `RunAgentInput` を渡します。アダプターは AG-UI のメッセージを AgentScope の `Msg` オブジェクトに変換し、v2 の `streamEvents(...)` を呼び出し、各 `AgentEvent` を AG-UI イベントに変換します。

## イベントのマッピング

v2 のパスは `AgentEvent` を消費します。組み込みのコンバーターがセマンティックなマッピングを処理し、マッピングされないイベントは公式の `RAW` イベントにフォールバックします。

| AgentScope のイベント / コンテンツ             | AG-UI のイベント |
|----------------------------------------| --- |
| `AgentStartEvent`                      | `RUN_STARTED` |
| `AgentEndEvent`                        | `RUN_FINISHED` |
| テキスト                              | `TEXT_MESSAGE_START` / `TEXT_MESSAGE_CONTENT` / `TEXT_MESSAGE_END` |
| 思考（`enableReasoning=true`） | `REASONING_MESSAGE_START` / `REASONING_MESSAGE_CONTENT` / `REASONING_MESSAGE_END` |
| ツール呼び出しと引数のデルタ          | `TOOL_CALL_START` / `TOOL_CALL_ARGS` / `TOOL_CALL_END` |
| ツールの結果                            | `TOOL_CALL_RESULT` |
| `CustomEvent`                          | `CUSTOM` |
| トークン使用量（`emitTokenUsage=true`）    | `CUSTOM`、`name=token_usage` |
| マッピングされない `AgentEvent`                  | `RAW`（公式の `event` と `source` フィールド付き） |

通常の `RUN_STARTED` と `RUN_FINISHED` イベントは、上流の `AgentStartEvent` と `AgentEndEvent` によって駆動されます。上流の `AgentEndEvent` が来ないまま通常のストリームが完了した場合、アダプターは `RUN_FINISHED` を合成しません。エラー発生時、アダプターは `timestamp` 付きの `RUN_ERROR` を発行します。`RUN_ERROR` と `RUN_FINISHED` は互いに排他的な終端イベントです。エラー後も終了イベントを期待するレガシークライアント向けにのみ、`emitRunFinishedAfterError=true`（または Spring Boot の `agentscope.agui.emit-run-finished-after-error=true`）を設定してください。

## サブエージェントのイベント

デフォルト（`emitSubagentEventsAsNative=false`）では、`source` が非 null の AgentEvent（子 / リモートサブエージェントのイベント）はネイティブの `TEXT_MESSAGE_*` / `RUN_*` / ツール呼び出しイベントには**マッピングされません**。これらは親の実行ライフサイクルやテキストストリームを汚染しないよう、`subagent.*` 名前空間の下の AG-UI `CUSTOM` イベントになります。

| CUSTOM の `name` | 典型的な AgentEvent |
| --- | --- |
| `subagent.lifecycle` | `AgentStartEvent` / `AgentEndEvent` |
| `subagent.text` | `TextBlockDeltaEvent` |
| `subagent.thinking` | `ThinkingBlockDeltaEvent` |
| `subagent.tool_call` | `ToolCallStartEvent` / `ToolCallEndEvent` |
| `subagent.tool_result` | `ToolResultEndEvent` |
| `subagent.require_confirm` | `RequireUserConfirmEvent` |

各ペイロードには、少なくとも `source` と `type`（さらに `delta` や `toolCallId` などの型固有のフィールド）が含まれます。

子イベントが親と同じネイティブコンバーターを使っていた以前の挙動に戻すには、次のようにします。

```java
AguiAdapterConfig config = AguiAdapterConfig.builder()
    .emitSubagentEventsAsNative(true)
    .build();
```

## AG-UI のベースイベントプロパティ

すべての `AguiEvent` は、任意指定の `timestamp` と `rawEvent` という公式のベースイベントプロパティをサポートします。

デフォルト設定では `BaseEventPropertiesEnricher` は有効になっていないため、フレームワークはデフォルトではすべてのイベントに `timestamp` を追加せず、また内部の `AgentEvent` オブジェクトをデフォルトで `rawEvent` として公開しません。タイムスタンプを埋めたい場合は、デフォルトのエンリッチャーを明示的に有効化してください。

```java
AguiAdapterConfig config = AguiAdapterConfig.builder()
    .baseEventPropertiesEnricherEnabled(true)
    .build();
```

`BaseEventPropertiesEnricher` は、欠落している `timestamp` を埋めるだけです。既存のタイムスタンプはそのまま保持し、`rawEvent` は書き込みません。`rawEvent` を公開したい場合は、カスタムの `AguiEventEnricher` を登録してください。

Spring Boot の starter は、デフォルトのベースプロパティエンリッチャーを暗黙的に有効化しません。この挙動が必要な場合は、`BaseEventPropertiesEnricher` Bean か独自の `AguiEventEnricher` Bean を公開してください。

## カスタムコンバーターとエンリッチャー

`AgentEventConverter` はセマンティックなマッピングを拡張または上書きします。同じ `AgentEvent` 型に対しては、ユーザー定義のコンバーターが組み込みのコンバーターを上書きします。

```java
@Bean
AgentEventConverter customEventConverter() {
    return new AgentEventConverter() {
        @Override
        public Set<Class<? extends AgentEvent>> eventTypes() {
            return Set.of(CustomEvent.class);
        }

        @Override
        public void convert(AgentEvent event, AguiStreamContext context) {
            CustomEvent customEvent = (CustomEvent) event;
            context.emit(new AguiEvent.Custom(
                context.getThreadId(),
                context.getRunId(),
                customEvent.getName(),
                customEvent.getValue()));
        }
    };
}
```

`AguiEventEnricher` は変換の後に実行されます。`timestamp`、`rawEvent`、トレーシングのフィールドなど、横断的な関心事のイベント装飾を目的としています。コンバーターの出力を変更、追加、あるいはフィルタすることができます。

```java
@Bean
AguiEventEnricher timestampEnricher() {
    return (source, events, context) -> events.stream()
        .map(event -> AguiEvents.withBaseProperties(
            event,
            event.timestamp() != null ? event.timestamp() : System.currentTimeMillis(),
            event.rawEvent()))
        .toList();
}
```

Spring Boot の starter は `AgentEventConverter` と `AguiEventEnricher` の Bean を自動的に収集し、`orderedStream()` を使うため `@Order` / `Ordered` が尊重されます。

## トークン使用量

トークン使用量はデフォルトでは無効です。手動での設定:

```java
AguiAdapterConfig config = AguiAdapterConfig.builder()
    .emitTokenUsage(true)
    .build();
```

Spring Boot の設定:

```yaml
agentscope:
  agui:
    emit-token-usage: true
```

有効化すると、使用量を伴うすべての `ModelCallEndEvent` は `CUSTOM` イベントを発行します。`delta` は今回のモデル呼び出しの使用量です。`cumulative` は現在の AG-UI 実行内で累積された使用量です。

## RuntimeContext

`AguiAgentAdapter.run(input, runtimeContext)` は、呼び出し元が指定した `RuntimeContext` を受け付けます。アダプターはまず呼び出し元のコンテキストをコピーし、その後 AG-UI プロトコルのメタデータを適用するため、必要なデフォルト値が失われることはありません。

| RuntimeContext のエントリ | ソース |
| --- | --- |
| `sessionId` | `RunAgentInput.threadId` |
| `RunAgentInput.class` | `RunAgentInput` 全体 |
| `agui.threadId` | `RunAgentInput.threadId` |
| `agui.runId` | `RunAgentInput.runId` |
| `agui.messages` | `RunAgentInput.messages` |
| `agui.tools` | `RunAgentInput.tools` |
| `agui.context` | `RunAgentInput.context` |
| `agui.state` | `RunAgentInput.state` |
| `agui.forwardedProps` | `RunAgentInput.forwardedProps` |
| `agui.resume` | `RunAgentInput.resume` |

`sessionId` は常に `threadId` に由来するため、同じエージェントインスタンスであっても AG-UI のスレッドをまたいで分離が保たれます。

## Spring Boot 統合

starter は MVC または WebFlux のエンドポイントを自動的に登録します。よくある設定:

```yaml
agentscope:
  agui:
    path-prefix: /agui
    cors-enabled: true
    run-timeout: 10m
    default-agent-id: default
    enable-path-routing: true
    agent-id-header: X-Agent-Id
    emit-state-events: true
    emit-tool-call-args: true
    emit-token-usage: false
    enable-reasoning: false
    emit-run-finished-after-error: false
    server-side-memory: false
    interrupt-on-disconnect: true
```

`interrupt-on-disconnect` は、MVC/WebFlux の SSE 接続が閉じられた、タイムアウトした、あるいはイベント送信中に失敗した場合に Agent の実行を中断するかどうかを制御します。後方互換性のためデフォルトは `true` です。クライアントが切断した後も Agent を実行し続けさせたい場合は `false` に設定してください。接続が閉じている間に生成されたイベントは、starter によって再送されません。

デフォルトのチェーンは、Bean を使って拡張できます。

- `AgentEventConverter`: カスタムのイベントセマンティックマッピング。
- `AguiEventEnricher`: 横断的なイベントエンリッチメント。
- `AguiRuntimeContextResolver`: リクエストスコープの `RuntimeContext` 注入。
- `AguiAgentAdapterFactory`: デフォルトの `AguiAgentAdapter` 構築の置き換え。

`AguiRuntimeContextResolver` は、トランスポート、パス上のエージェント ID、ヘッダーのエージェント ID、ヘッダー、クエリパラメータ、ネイティブの Web リクエストを読み取ることができます。

```java
@Bean
AguiRuntimeContextResolver runtimeContextResolver() {
    return request -> RuntimeContext.builder()
        .put("tenantId", request.firstHeader("X-Tenant-Id"))
        .put("traceId", request.firstHeader("X-Trace-Id"))
        .build();
}
```

`forwardedProps` はクライアントのリクエストボディから来るもので、UI オプションやフロントエンドのコンテキストに適しています。これを信頼できる ID の情報源として扱わないでください。サーバー側のユーザー ID は、認証またはサーバー側のリゾルバーから取得すべきです。

## フロントエンドツールとマージモード

AG-UI フロントエンドは `RunAgentInput.tools` を通じてツールスキーマを渡すことができます。アダプターは、実行開始時にそれらのツールをエージェントのツールキットに注入し、実行が完了またはキャンセルされた後にクリーンアップします。

| `ToolMergeMode` | 動作 |
| --- | --- |
| `FRONTEND_ONLY` | フロントエンドが提供したツールのみを使用し、既存のエージェントツールを一時的に隠す |
| `AGENT_ONLY` | フロントエンドが提供したツールを無視し、エージェントのツールキットのみを使用する |
| `MERGE_FRONTEND_PRIORITY` | 両方をマージし、名前が衝突した場合はフロントエンドのツールを優先する |

デフォルトは `MERGE_FRONTEND_PRIORITY` です。注入は実行スコープであり、エージェントのツールキットを永続的に変更することはありません。

## HITL 割り込み

実行がツールの判断待ちで一時停止すると、AG-UI アダプターは `RUN_FINISHED` 上で公式の割り込み結果を発行します。AgentScope Java には、ツール呼び出しの割り込み経路が組み込みで 2 つあります。

- **ツールの一時停止 / 外部実行**: 一時停止した `ToolResultBlock` が `tool_call` の割り込みになり、`ToolResultBlock` として再開されます。
- **権限確認**: `RequireUserConfirmEvent` が AgentScope のメタデータを持つ `tool_call` の割り込みになり、`ConfirmResult` として再開されます。

割り込みは特定の `toolCallId` に紐づいているため、どちらも公式の `reason: "tool_call"` を使います。これらのツールに紐づいた承認に `reason: "confirmation"` を使わないでください。

```json
{
  "type": "RUN_FINISHED",
  "outcome": {
    "type": "interrupt",
    "interrupts": [
      {
        "id": "reply-1:call-1",
        "reason": "tool_call",
        "toolCallId": "call-1",
        "message": "このツールを実行する前に承認が必要です",
        "responseSchema": {
          "type": "object",
          "properties": {
            "approved": { "type": "boolean" },
            "editedArgs": {
              "type": "object",
              "description": "Full replacement of the tool args. Not merged."
            }
          },
          "required": ["approved"]
        },
        "metadata": {
          "agentscope.interruptKind": "permission_confirm",
          "toolName": "request_approval",
          "toolInput": { "path": "/tmp/report.txt" },
          "toolContent": "{\"path\":\"/tmp/report.txt\"}",
          "replyId": "reply-1"
        }
      }
    ]
  }
}
```

フロントエンドは承認 UI や外部実行 UI を表示できます。ユーザーが操作した後は、同じ `threadId` に対する次の `runAgent` リクエストで、公式の `resume[]` フィールドを送信してください。

```json
{
  "threadId": "thread-1",
  "runId": "run-2",
  "messages": [],
  "resume": [
    {
      "interruptId": "reply-1:call-1",
      "status": "resolved",
      "payload": {
        "approved": true,
        "editedArgs": {
          "path": "/tmp/reviewed-report.txt"
        }
      }
    }
  ]
}
```

`status` は公式の `resolved` と `cancelled` の値をサポートします。ユーザーがツールリクエストを拒否するという一般的な承認ケースでは、`resolved` を使い、ビジネス上の判断を `payload`（例: `{ "approved": false }`）で表現することを推奨します。`cancelled` は割り込み自体がキャンセルされた場合に使います。

権限確認の場合、ツールを承認するには `payload.approved` がブール値の `true` である必要があります。欠落している場合、ブール値でない場合、または `false` の場合はすべて拒否として扱われます。`payload.editedArgs` は、存在する場合は JSON オブジェクトである必要があり、元のツール引数の**完全な置き換え**であって部分的なマージではありません。AgentScope Java は `editedArgs` から `ToolUseBlock.input` と生の JSON である `ToolUseBlock.content` の両方を再構築するため、承認されたツールは編集後の引数で実行されます。

フロントエンドは `resume[]` の中で `metadata` を再送する必要はなく、`interruptId`、`status`、`payload` のみを送信します。Spring の `AguiRequestProcessor` エントリポイントを通じて、AgentScope Java は最新の `RUN_FINISHED.outcome.interrupts[]` をサーバー側で記録し、次の `resume[]` が未解決の割り込みをすべてカバーしていることを検証し、元となった割り込みをアダプターに渡して変換させます。

## サンプルプロジェクト

完全なサンプルは [agentscope-examples/agui](https://github.com/agentscope-ai/agentscope-java/tree/main/agentscope-examples/agui) を参照してください。

```bash
export DASHSCOPE_API_KEY=your-key
cd agentscope-examples/agui
mvn spring-boot:run
```

起動後、http://localhost:8080 にアクセスしてください。このサンプルは、マルチエージェントルーティング、カスタムコンバーター、カスタムエンリッチャー、トークン使用量、HITL 割り込みを実演します。
