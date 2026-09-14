---
title: "V1 移行ガイド"
description: "AgentScope Java 1.x から 2.0 への完全な移行ガイド"
---

:::{tip}
バージョンごとの変更記録をお探しですか? [リリースノート](others/release-notes.md) を参照してください。
:::

AgentScope Java 2.0 は、可能な限り 1.x との互換性を維持し、ほとんどのユーザーがスムーズにアップグレードできることを目指しています。とはいえ、2.0 は API レベルの変更を導入しています。このページでは、それらの変更を2つのセクションに分けています。

- **移行ガイド** — 1.x に対して何が変わったか、2段階に分けて:
  - **Part A · 必須** — 移行しないとコードがコンパイルに失敗するか、実行時にスローされます
  - **Part B · 推奨** — 現時点では動作しますが `@Deprecated(forRemoval = true)` であり、次のマイナーバージョンで削除されます
- **新機能** — 移行ガイドには現れない、純粋に新規追加された機能

## 移行ガイド

### Part A — 必須(移行しないとコンパイルエラーまたは実行時例外)

このセクションの項目は削除・リネームされたか、セマンティクスが厳格化されています。1.x で動作していたコードは、2.0 ではそのままでは動作しません。

#### A.1 削除された `ReActAgent.Builder` のメソッド

| 2.0 で削除 | 置き換え |
|---|---|
| `.memory(Memory)` | `.stateStore(AgentStateStore)` — `AgentState.getContext()` が会話を保持し、設定された `AgentStateStore` が `call()` のたびに、`RuntimeContext` からのその呼び出しの `(userId, sessionId)` をキーとして自動的に保存/読み込みを行う |
| `.statePersistence(StatePersistence)` | 同上 — `AgentStateStore` が永続化を包含する |
| `.structuredOutputReminder(StructuredOutputReminder)` | 不要になった — 構造化出力は現在モデル層でネイティブに処理される(`Model.supportsNativeStructuredOutput()`)。フレームワークが自動的にネイティブ JSON スキーマを選択するか、tool-choice にフォールバックする |

詳細 → [Context](building-blocks/context.md)

#### A.2 削除されたパッケージとクラス

| 2.0 で削除 | 置き換え |
|---|---|
| `io.agentscope.core.session.SessionManager` | エージェントビルダーで `.stateStore(AgentStateStore)` を設定する。永続化は `(userId, sessionId)` ごとに自動的に行われる |
| `io.agentscope.core.pipeline.*` — `Pipeline`、`Pipelines`、`SequentialPipeline`、`FanoutPipeline`、`MsgHub` | マルチエージェントオーケストレーションのために middleware + サブエージェント + イベントストリームを合成する。サブエージェントガイドを参照 → [Subagent](harness/subagent.md) |
| `io.agentscope.core.model.tts.*`(14ファイル、DashScope TTS / Realtime TTS / `AudioPlayer` など) | Core はもはや TTS を同梱しない。TTS が必要な場合は上流のプロバイダー SDK を直接統合する |
| `io.agentscope.core.model.StructuredOutputReminder` | 不要になった — 構造化出力はモデル層でネイティブに処理される |
| `io.agentscope.core.agent.StructuredOutputCapableAgent` | 削除 — 構造化出力の能力はネイティブなモデル層サポートとともに `ReActAgent` にインライン化された |
| `io.agentscope.core.hook.PendingToolRecoveryHook` | `Builder.enablePendingToolRecovery(boolean)` を使う |
| `io.agentscope.core.hook.TTSHook` | TTS モジュールとともに削除 |

#### A.3 モデルプロバイダーが core から移出

OpenAI、Gemini、Anthropic、DashScope、Ollama のチャットモデル実装は、もはや `agentscope-core` にはパッケージされていません。Core は現在、`Model`、`ChatModelBase`、`Formatter`、`ModelRegistry`、`ModelProvider` SPI といった共有のモデル契約のみを保持します。

v1 のコードが core からプロバイダーのクラスをインポートしていた場合は、対応するモデル拡張モジュールに置き換えてください。

| v1 の import / 依存関係 | v2 での置き換え |
|---|---|
| `io.agentscope.core.model.OpenAIChatModel` | `agentscope-extensions-model-openai` を追加。`io.agentscope.extensions.model.openai.OpenAIChatModel` をインポート |
| `io.agentscope.core.model.GeminiChatModel` | `agentscope-extensions-model-gemini` を追加。`io.agentscope.extensions.model.gemini.GeminiChatModel` をインポート |
| `io.agentscope.core.model.AnthropicChatModel` | `agentscope-extensions-model-anthropic` を追加。`io.agentscope.extensions.model.anthropic.AnthropicChatModel` をインポート |
| `io.agentscope.core.model.DashScopeChatModel` | `agentscope-extensions-model-dashscope` を追加。`io.agentscope.extensions.model.dashscope.DashScopeChatModel` をインポート |
| `io.agentscope.core.model.OllamaChatModel` | `agentscope-extensions-model-ollama` を追加。`io.agentscope.extensions.model.ollama.OllamaChatModel` をインポート |
| `io.agentscope.core.formatter.<provider>.*` | `io.agentscope.extensions.model.<provider>.formatter.*` |
| `io.agentscope.core.credential.<Provider>Credential` | `io.agentscope.extensions.model.<provider>.credential.<Provider>Credential` |

`ModelRegistry` の文字列 ID は引き続き機能しますが、それは対応する拡張モジュールがクラスパス上にある場合に限られます。

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("dashscope:qwen-plus")
    .build();
```

Spring Boot アプリケーションは、汎用の core モデルパスに頼るのではなく、プロバイダー固有のスターターを使うべきです。

| プロバイダー | Spring Boot スターター |
|---|---|
| OpenAI | `agentscope-openai-spring-boot-starter` |
| DashScope | `agentscope-dashscope-spring-boot-starter` |
| Gemini | `agentscope-gemini-spring-boot-starter` |
| Anthropic | `agentscope-anthropic-spring-boot-starter` |
| Ollama | `agentscope-ollama-spring-boot-starter` |

詳細 → [Model](building-blocks/model.md)、[Model Providers](../integration/overview.md)

#### A.4 `state` パッケージの再構成(コンパイルエラー)

| v1 | v2 |
|---|---|
| `AgentMetaState` | `AgentState` |
| `StateModule` | **削除** — `Memory`、`Toolkit` などのスーパークラスではなくなった |
| `StatePersistence` | **削除** — `AgentStateStore` 抽象化に置き換え |
| `ToolkitState` | `io.agentscope.core.state.legacy.ToolkitState` に移動(互換性のためだけに残されている。新しいコードでは参照しないこと) |
| (新規) | `Task`、`TaskContextState`、`ToolContextState`、`PlanModeContextState`、`ReadCacheEntry` |

`io.agentscope.core.state` から `AgentMetaState`、`StateModule`、`StatePersistence`、`ToolkitState` をインポートするコードはすべてコンパイルに失敗します。詳細 → [Context](building-blocks/context.md)

#### A.5 `PlanNotebook` を削除 — 代わりに `HarnessAgent.enablePlanMode()` を使う

`io.agentscope.core.plan` パッケージ全体(`PlanNotebook`、`Plan`、`SubTask`、`PlanStorage`、`PlanToHint` および関連クラス)は、非推奨のブリッジなしで削除されました。

**変更内容**: `PlanNotebook` は、状態機械(todo → in_progress → done → abandoned)と8つのツール関数を持つ構造化された `Plan` + `SubTask` オブジェクトとしてプランをモデル化していました。v2 での置き換えは根本的に異なる設計です — Plan Mode は、エージェントが書き込みアクセスを得る前に、プレーンな markdown ファイル内でアプローチを設計する**読み取り専用の調査フェーズ**になりました。

| v1 の `PlanNotebook` | v2 の Plan Mode |
|---|---|
| `ReActAgent.builder().planNotebook(PlanNotebook.builder().build())` | `HarnessAgent.builder().enablePlanMode()` |
| 状態機械を持つ構造化された `Plan` + `SubTask` オブジェクト | プレーンな markdown ファイル(`plans/PLAN.md`) |
| 8つのツール: `createPlan`、`reviseCurrentPlan`、`updateSubtaskState`、`finishSubtask`、`finishPlan`、`viewSubtasks`、`viewHistoricalPlans`、`recoverHistoricalPlan` | 3つのツール: `plan_enter`、`plan_write`、`plan_exit` |
| プランと実行が混在 — 読み取り専用の制限なし | Plan Mode は読み取り専用。`plan_exit` はエージェントが書き込みアクセスを取り戻す前に HITL ゲートを発火させる |
| `PlanToHint` が推論ステップごとに文脈的なヒントを注入 | `PlanModeMiddleware` が Plan Mode 中に変更系ツールをブロックする |
| `PlanStorage`(インメモリ)+ `StateModule` による永続化 | プランファイルは `WorkspaceManager` 経由で書き込まれ、状態は `AgentState.planModeContext` にある |

**サブタスクの追跡**: v1 のコードが `PlanNotebook` のサブタスク状態追跡(作業をサブタスクに分割し、実行中にそれらをチェックオフする)に依存していた場合、v2 の相当物は**タスクリスト**です — ビルダーで `.enableTaskList(true)` を有効にすると、`TodoTools` と `TaskReminderMiddleware` が登録されます。

#### A.6 `Msg` のコンテンツ検証がより厳格に(実行時例外)

`Msg` は構築時に `content` を `role` に照らして検証するようになりました。

- `USER` — `TextBlock` / `DataBlock` / `ImageBlock` / `AudioBlock` / `VideoBlock` のみ
- `SYSTEM` — `TextBlock` のみ
- `ASSISTANT` — 制限なし

v1 で許容されていた組み合わせ(例えば `ToolUseBlock` を運ぶ `USER` メッセージ)は、構築時にスローされるようになりました。呼び出し箇所でロールとコンテンツの互換性を明確にするために、ロール固定のサブクラス `UserMessage` / `AssistantMessage` / `SystemMessage` / `ToolResultMessage` を使ってください。詳細 → [Message & Event](building-blocks/message-and-event.md)

#### A.7 Agent が完全にステートレスに(アーキテクチャの変更)

`ReActAgent` は現在**完全にステートレス**です — インスタンス自体は可変な「現在のセッション」状態を一切保持しません。呼び出しごとのすべての可変状態(`AgentState`、`PermissionEngine`、イベントシンク)は内部の `CallExecution` オブジェクトにカプセル化され、Reactor Context を介して呼び出しチェーンを通じて伝播されます。単一の Agent インスタンスは、セッション間の干渉なしに、複数の `(userId, sessionId)` の組み合わせに安全に同時対応できます。

**v1 → v2 への影響**:

| 削除 | 置き換え |
|---|---|
| `ReActAgent.getCurrentSessionId()` | `call()` の時点で `RuntimeContext.getSessionId()` から供給される |
| `ReActAgent.getCurrentUserId()` | `call()` の時点で `RuntimeContext.getUserId()` から供給される |
| `AgentBase(name, desc, checkRunning, hooks)` コンストラクタ | `AgentBase(name, desc, hooks)` を使う — `checkRunning` はもはや不要。並行性はセッションごとのシリアライズによって保証される |
| `ReActAgent.getState()` | `ReActAgent.getAgentState()` または `getAgentState(userId, sessionId)` |

`isCheckRunning()` は引き続き呼び出し可能(`false` を返す)で、`Builder.checkRunning(boolean)` も引き続き呼び出し可能(無視される)です — どちらも `@Deprecated` です。

#### A.8 `TracerRegistry` + `TelemetryTracer` → `OtelTracingMiddleware`

古いトレーシングのセットアップは、フレームワークレベルの `Tracer` をグローバルに登録していました。

```java
TracerRegistry.register(TelemetryTracer.builder().tracer(tracer).build());
```

現在の 2.0 ソースツリーでは、`TelemetryTracer` は `agentscope-core` ではなく `agentscope-extensions-studio` モジュールに存在します。Studio との統合のためには引き続き利用可能ですが、アプリケーション全体のトレーシングを回復するためだけに Studio 拡張を追加することは推奨される移行方法ではありません。`Tracer` インタフェースと `TracerRegistry` は削除のために非推奨とされています。

代わりに、標準の OpenTelemetry コンポーネントを通じてトレーシングを設定してください。

| 従来のセットアップ | 2.0 での置き換え |
|---|---|
| `TelemetryTracer.builder().endpoint(...)` | `OtlpHttpSpanExporter` を構築し、`SdkTracerProvider` にアタッチする |
| `TelemetryTracer.builder().addHeader(...)` | `OtlpHttpSpanExporter.builder().addHeader(...)` を呼び出す |
| `TracerRegistry.register(...)` | `OpenTelemetrySdk.buildAndRegisterGlobal()` で SDK を登録する |
| フレームワークグローバルなトレーサー | スパンを発行すべき各エージェントに `new OtelTracingMiddleware()` を追加する |
| `TracerRegistry.resetToNoop()` / トレーサーのシャットダウン | シャットダウン時にアプリケーションが所有する `SdkTracerProvider` をクローズする |

middleware は `GlobalOpenTelemetry` を読み取るため、SDK はエージェントが middleware を使用する前に登録されている必要があります。必要な依存関係と、カスタム認証ヘッダーを使った完全な OTLP の例については、[Middleware — OtelTracingMiddleware](building-blocks/middleware.md#oteltracingmiddleware) を参照してください。

---

### Part B — 推奨(`@Deprecated(forRemoval = true)`、現在も呼び出し可能)

このセクションの項目は 2.0 でコンパイル・実行できますが、それぞれ次のマイナーバージョンでの削除に向けてマークされています。ご自身のペースで移行してください。早めに行うことを推奨します。

#### B.1 `SkillBox` → スキルリポジトリ

- `SkillBox`(クラス)と `Builder.skillBox(SkillBox)` は、いずれも `@Deprecated(forRemoval = true, since = "2.0.0")` です。
- 推奨される方法: `Builder.skillRepository(...)` / `.skillRepositories(...)` を介して、1つ以上の `AgentSkillRepository` 実装(組み込み: `ClasspathSkillRepository`、`FileSystemSkillRepository`)を登録することです。少なくとも1つのリポジトリが登録されると、`DynamicSkillMiddleware` が自動的にインストールされ、`call()` のたびにスキルプロンプトを再構築します。
- 細粒度のフィルタリング: `Builder.skillFilter(SkillFilter)`。

詳細 → [Skill](harness/skill.md)

#### B.2 Hook → Middleware

`io.agentscope.core.hook` パッケージ全体 — `Hook` インタフェース、`HookEvent`、`HookEventType`、そしてすべての `*Event` クラス — は `@Deprecated(forRemoval = true, since = "2.0.0")` です。既存の import は引き続きコンパイルでき、`Builder.hook(...)` / `.hooks(...)` は `LegacyHookDispatcher` を介して呼び出し可能なままなので、v1 のコードが一夜にして壊れることはありません。推奨される拡張面は、現在は `io.agentscope.core.middleware` です。

- `MiddlewareBase` は5つのステージを公開します: オニオン型の `onAgent` / `onReasoning` / `onActing` / `onModelCall`、そしてパイプライン型の `onSystemPrompt`。
- ビルダーメソッド: `.middleware(MiddlewareBase)` と `.middlewares(List<? extends MiddlewareBase>)`。
- 組み込み: `TaskReminderMiddleware`(`TodoTools` と対になり、各推論ステップの前にタスクリストを再注入する)。

詳細 → [Middleware](building-blocks/middleware.md)

#### B.3 `Memory` → `AgentStateStore` + `AgentState`

- `io.agentscope.core.memory.Memory` インタフェースとすべての実装(`InMemoryMemory`、`LongTermMemory` など)は `@Deprecated(forRemoval = true, since = "2.0.0")` です。
- `Memory` はもはや `StateModule` を拡張しません。既存の実装が `AgentStateStore` を介して往復できるようにするブリッジとして、`saveTo(AgentStateStore, userId, sessionId)` / `loadFrom(AgentStateStore, userId, sessionId)` を獲得しました。
- 推奨されるモデル:
  - **会話履歴** は `AgentState.getContext()` に存在します。
  - **永続化** は `AgentStateStore` 抽象化(組み込み: `InMemoryAgentStateStore`、`JsonFileAgentStateStore`)を使い、`(userId, sessionId)` のペアで分割されます。
  - ビルダーチェーン: `.stateStore(AgentStateStore)` — `AgentState` は、呼び出しの `RuntimeContext` が運ぶ `(userId, sessionId)` をキーとして、`call()` のたびに自動的に保存/読み込みされます。

詳細 → [Context](building-blocks/context.md)

#### B.4 イベント購読: hook + chunk イベント → `streamEvents()`

v1 で `Hook` + `*ChunkEvent` を介してテキストやツール呼び出しの差分を監視していたコードは、`agent.streamEvents()` に移行できます。これはエージェントのライフサイクル全体と HITL フロー(`RequireUserConfirmEvent`、`RequireExternalExecutionEvent`、`UserConfirmResultEvent`、`ExternalExecutionResultEvent` など)をカバーする28種類の型付きイベントにわたる `Flux<AgentEvent>` を返します。

新しいイベントストリームに加えて、`Msg` のリファクタリングは以下を追加します。

- `DataBlock` — 統一されたマルチモーダルブロック。base64 または URL のソースを受け付ける
- `HintBlock` — エージェントのガイダンス / 中間的な推論
- `ToolUseBlock` / `ToolResultBlock` 上の `ToolCallState` / `ToolResultState` — ツール呼び出しのライフサイクル
- すべてのブロックにある `id` フィールド — ストリームをまたいだ安定した参照

詳細 → [Message & Event](building-blocks/message-and-event.md)

##### `stream()` → `streamEvents()`(Python 2.0 との整合)

Python 2.0 の `agent.reply_stream()` は、Java 側のきめ細かい `io.agentscope.core.event.AgentEvent` 階層に直接マッピングされる、単一のストリーミングシグネチャ(`AsyncGenerator[AgentEvent, None]`)を公開しています。これに合わせるため、Java 側の粗粒度な `Flux<Event> stream(...)` API は 2.0.0 時点で `@Deprecated` です。

- **メソッド(`forRemoval = true`、次のマイナーバージョンで削除)**
  - `StreamableAgent.stream(...)` — インタフェース上のすべての11個の `stream(...)` オーバーロード(デフォルト + 抽象)
  - `AgentBase.stream(...)` — 3つの `Flux<Event>` 実装
  - `ReActAgent.stream(..., RuntimeContext)` — 4つの `RuntimeContext` 付きオーバーロード
  - `HarnessAgent.stream(...)` — 9個のオーバーロード(3つのインタフェース `@Override` + 6つの `RuntimeContext` バリアント)。`HarnessAgent` は、サンドボックスのライフサイクル `acquireForCall` / `releaseForCall` を再利用しながら `ReActAgent.streamEvents(...)` に委譲する、新しい4つの `streamEvents(Msg/List<Msg>[, RuntimeContext])` メソッドを獲得します
  - `ReActAgent.streamEvents(..., RuntimeContext)` が追加 — コンテキスト伝播のために `call(..., RuntimeContext)` を鏡写しにする
- **型(ソフト非推奨。まだ `forRemoval` ではない)**
  - `io.agentscope.core.agent.Event`、`EventType`、`EventSource`
  - 依然として harness 内部で消費されています(サブエージェントのイベント転送: `SubAgentTool` / `SubagentEventBus` / `DefaultAgentManager` / `AgentSpawnTool`)。AGUI、A2A、chat-completions-web、そして Kotlin 拡張モジュールにおいて、イベントバス / アダプタの入力として使われています。下流全体が1つのリリースで警告の洪水にさらされないよう、これらのモジュールが `AgentEvent` に移行した後にのみ `forRemoval = true` に切り替わります。
  - サブエージェントのイベントは、`HarnessAgent.streamEvents(...)` 上で non-null の `source` パスとともに転送されます(`remoteStreaming` が有効な場合、リモートの Agent Protocol の子も含む)。

新しいコードでは以下を使ってください。

```java
agent.streamEvents(new UserMessage("Hello"))
        .doOnNext(event -> {
            if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA) {
                System.out.print(((TextBlockDeltaEvent) event).getDelta());
            }
        })
        .blockLast();
```

#### B.5 RAG モジュール — 進行中

- `Knowledge`、`KnowledgeRetrievalTools`、`RAGMode`、`GenericRAGHook` はすべて `@Deprecated(forRemoval = true, since = "2.0.0")` です。
- ビルダーメソッド `.knowledge(...)` / `.knowledges(...)` / `.ragMode(...)` / `.retrieveConfig(...)` も同様に非推奨です。
- v2 での書き直しが進行中です。新しいナレッジベース、ドキュメントリーダー、ストアの API が今後のマイナーリリースで登場します。v1 の実装は 2.0 でも互換性のために呼び出し可能ですが、**新しいコードはこれらに依存すべきではありません**。

#### B.6 長期記憶モジュール — 進行中

- `LongTermMemory`、`LongTermMemoryMode`、`LongTermMemoryTools` はすべて `@Deprecated(forRemoval = true, since = "2.0.0")` です。
- ビルダーメソッド `.longTermMemory(...)` / `.longTermMemoryMode(...)` / `.longTermMemoryAsyncRecord(...)` も同様に非推奨です。
- 状況は同じです — v2 のアーキテクチャ上で書き直しが進行中です。新しいコードは現在の API に依存すべきではありません。

#### B.7 Core のシェル / ファイルツール — もはや非推奨ではない

- `io.agentscope.core.tool.coding.*`(`ShellCommandTool`、`CommandValidator`、`UnixCommandValidator`、`WindowsCommandValidator`)と `io.agentscope.core.tool.file.*`(`ReadFileTool`、`WriteFileTool`、`FileToolUtils`)は、2.0.0-RC1 時点で**もはや `@Deprecated` ではありません**。
- これらのツールは、ホストプロセスに対して直接コマンドを実行し、ファイルを読み書きします。ワークスペース / サンドボックスの分離を必要としない `ReActAgent` ユーザーにとっては、エージェントにシェルとファイルアクセスを与えるための推奨される方法です。

```java
Toolkit toolkit = new Toolkit();
toolkit.registerTool(new ReadFileTool("/path/to/base/dir"));
toolkit.registerTool(new WriteFileTool("/path/to/base/dir"));
toolkit.registerTool(new ShellCommandTool());

ReActAgent agent = ReActAgent.builder()
    .toolkit(toolkit)
    /* ... */
    .build();
```

- `HarnessAgent` ユーザー向けには、harness モジュールが、統一されたローカル / Docker / クラウドサンドボックスのストア、パーミッションの分離、読み取り/書き込みキャッシュ、HITL 承認を備えた、独自のワークスペース対応ファイル/シェルツール(`read_file`、`write_file`、`execute` など)を提供します。ワークスペース統合されたシナリオでは、組み込みの harness ツールを使うことが推奨されます。

詳細 → [Harness filesystem](harness/filesystem.md)

---

## 新機能

以下の能力は 2.0 で加算的です — どれも 1.x のコードを壊しません。上記の移行ガイドがすでにイベントシステム、メッセージのリファクタリング、middleware メカニズムをカバーしているため、ここでは繰り返しません。

### AG-UI v2

- AG-UI アダプタは現在、v2 の `streamEvents()` パスを使用します。通常の `RUN_STARTED` / `RUN_FINISHED` イベントは `AgentStartEvent` / `AgentEndEvent` から変換されます。エラーパスは `RUN_ERROR` とフォールバックの `RUN_FINISHED` を発行します。
- 新しい `AgentEventConverter` と `AguiEventEnricher` の拡張ポイント: コンバーターはセマンティックなマッピングを扱い、エンリッチャーは `timestamp` / `rawEvent` のような横断的なプロパティを扱います。Spring Boot スターターは両方の Bean タイプを自動的に収集します。
- すべての `AguiEvent` が AG-UI の基本イベントプロパティをサポートします。`BaseEventPropertiesEnricher` はデフォルトで無効です。明示的に有効化された場合、欠けている `timestamp` の値のみを埋め、`rawEvent` をデフォルト値にすることはありません。
- `AguiAdapterConfig.emitTokenUsage` は、モデル呼び出しの差分と run レベルの累積トークン使用量を伴う `CUSTOM token_usage` イベントを発行できます。
- **挙動の変更:** `source != null` を持つ AgentEvent(サブエージェントのイベント)は、ネイティブの `TEXT_MESSAGE_*` / `RUN_*` の代わりに AG-UI の `CUSTOM` イベント(`subagent.lifecycle`、`subagent.text`、`subagent.thinking`、`subagent.tool_call`、`subagent.tool_result`、`subagent.require_confirm`)として発行されます。従来のネイティブなマッピングに戻すには `emitSubagentEventsAsNative(true)` を設定してください。
- Spring Boot スターターは、`AguiRuntimeContextResolver`、カスタムの `AguiAgentAdapterFactory`、フロントエンドツールの注入 / マージモード、HITL の割り込み出力をサポートします。

詳細 → [AG-UI](../integration/protocol/agui.md)

### Toolkit とパーミッション

ツール実行は 2.0 における主要な拡張面であり、パーミッションシステムはその実行パスの直上に位置します — そのため両者をまとめて紹介します。

- **Toolkit のアップグレード**:
  - 統一された基底クラス: `ToolBase` / `AgentTool`
  - ツールグループ: `ToolGroup` / `ToolGroupScope` / `MetaToolFactory` — 必要に応じてアクティベートされる。予約済みの `basic` グループは常に有効
  - アノテーション駆動の登録: `ReflectiveFunctionTool` + `@Tool` / `@ToolParam`。`Toolkit#registerTool(Object)` はアノテーションが付いたメソッドをリフレクティブに登録する
  - 組み込みのタスクツール: `io.agentscope.core.tool.builtin.TodoTools.todoWrite`(`TaskReminderMiddleware` と対になる)
- **パーミッションシステム**(新しいパッケージ `io.agentscope.core.permission`):
  - `PermissionEngine`、`PermissionRule`、`PermissionMode`(`DEFAULT` / `ACCEPT_EDITS` / `EXPLORE` / `BYPASS` / `DONT_ASK`)、`PermissionBehavior`
  - すべてのツール呼び出しは `PermissionEngine` を通過します: 許可 / ユーザー確認が必要 / 拒否。HITL の決定は `UserConfirmResultEvent` として戻ってきます。

詳細 → [Tool](building-blocks/tool.md)、[Permission System](building-blocks/permission-system.md)

### モデルのフォールトトレランスと認証情報

- 新しいパッケージ `io.agentscope.core.credential` — 共有の認証情報契約と `ModelCard`。プロバイダー固有の認証情報はモデル拡張モジュールに存在する
- `ModelRegistry` は、対応するモデル拡張モジュールがクラスパス上にある場合、`"provider:model"` 文字列(例: `dashscope:qwen-max`、`openai:gpt-5`)からモデルを解決する
- ビルダーへの追加: `.model(String)`、`.maxRetries(int)`、`.fallbackModel(Model)` / `.fallbackModel(String)`、`.stopOnReject(boolean)` — プライマリモデルの失敗を自動的にリトライし、フォールバックする

詳細 → [Model](building-blocks/model.md)

### Workspace(Harness モジュール)

- Workspace 抽象化が、ローカルファイルシステム、Docker、E2B クラウドサンドボックスの実行を単一のインタフェースの背後に統一する
- ウォームアッププール — 実行環境をバッチで事前に初期化する。並列の RL ロールアウトに有用

詳細 → [Workspace](harness/workspace.md)

### その他の新しい Builder メソッド

- `.enableTaskList(...)` / `.enableTaskList(boolean)` — 組み込みの `TodoTools` を有効にする
- `.permissionContext(PermissionContextState)` — パーミッションルールを事前に読み込む
- `ReActAgent.Builder.fromAgent(ReActAgent)` — 既存のエージェントの観測可能な設定(name、description、システムプロンプト、model、maxIters、generateOptions、toolkit)から新しいビルダーを導出する
- `HarnessAgent.Builder.fromAgent(ReActAgent)` — ReActAgent → HarnessAgent の移行ヘルパー。`ReActAgent.Builder.fromAgent` と同じ7つのフィールドに加え、**ReActAgent 上の他のすべての観測可能な設定**を継承します: `stateStore` / `defaultSessionId`、`ModelConfig`(`maxRetries` / `fallbackModel`)、`ReactConfig.stopOnReject`、`modelExecutionConfig` / `toolExecutionConfig` / `toolExecutionContext`、`enablePendingToolRecovery`、`checkRunning`、`permissionContext`、`middlewares`、そして `hooks`。コピーされない唯一のフラグは `enableMetaTool` / `enableTaskList` です — これらはビルダー時に toolkit を変更するフラグであり、toolkit のコピーにはすでにそれらが登録したツールが含まれています。Harness 固有の設定(workspace / filesystem / subagents / skills / plan mode / `disable*` トグル)は、依然として明示的に設定する必要があります。完全な表は javadoc を参照してください。
- **上記の移行をサポートするための、ReActAgent / 親クラスへの新しい getter**: `getModelExecutionConfig()` / `getToolExecutionConfig()` / `getToolExecutionContext()` / `isPendingToolRecoveryEnabled()` / `getPermissionContext()`(`ReActAgent` 上)。`isCheckRunning()`(`AgentBase` 上。非推奨で、常に `false` を返す)。

詳細 → [Agent](building-blocks/agent.md)

### Memory / Compaction 向けの専用モデル

`MemoryConfig` と `CompactionConfig` は `.model(Model)` / `.model(String)` のビルダーメソッドを獲得し、エージェントのプライマリな推論モデルとは独立に、メモリのフラッシュ、統合、コンテキストコンパクションの操作のために専用(通常はより軽量で安価)のモデルを使えるようになりました。設定されていない場合は、エージェントのプライマリモデルが使われます(既存の挙動を維持)。

```java
HarnessAgent.builder()
    .model("openai:o3")
    .memory(MemoryConfig.builder()
        .model("openai:gpt-4.1-mini")
        .build())
    .compaction(CompactionConfig.builder()
        .model("openai:gpt-4.1-mini")
        .build())
    .build();
```
