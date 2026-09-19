---
title: リリースノート
description: AgentScope Java のバージョンごとの変更記録
---

このページは AgentScope Java 2.0 のバージョンごとの変更を追跡します。1.x からの全体的な移行ガイドについては、[V1 移行ガイド](/v2/ja/docs/change-log) を参照してください。

---

## 2.0.1

> リリース日: 2026-08-05

AgentScope Java 2.0.1 は 2.0.0 GA 以降で最初のメンテナンスリリースです。モデルプロバイダーのエコシステムを拡張し、Harness のサブエージェント / HITL / パーミッションの挙動を強化し、本番環境で重要な一連の問題を修正しています。

**クイックリンク:** [クイックスタート](/v2/ja/docs/quickstart) | [V1 移行ガイド](/v2/ja/docs/change-log) | [本番投入](/v2/ja/docs/others/going-to-production)

### 追加

**Core / Agent**

- `MiddlewareBase.order()` によるミドルウェアの実行順序(値が大きいほど外側をラップする)。`ReActAgent.Builder.build()` はすべての登録後に降順で安定ソートする ([#2532](https://github.com/agentscope-ai/agentscope-java/pull/2532), [#2449](https://github.com/agentscope-ai/agentscope-java/issues/2449))
- 新しいセッションを作らずにモデルに見える会話コンテキストをクリアする、`ReActAgent` / `HarnessAgent` 上のセッションコンテキストクリア API ([#2499](https://github.com/agentscope-ai/agentscope-java/pull/2499), [#2496](https://github.com/agentscope-ai/agentscope-java/issues/2496))
- 長寿命インスタンス向けに `ReActAgent` の状態キャッシュクリーンアップ API を公開 ([#2572](https://github.com/agentscope-ai/agentscope-java/pull/2572))
- パーミッション HITL の再開時に `UserConfirmResultEvent` を発行し、`replyId` によって直前の `RequireUserConfirmEvent` と相関付け可能にする ([#2511](https://github.com/agentscope-ai/agentscope-java/pull/2511))
- Anthropic: `disable_parallel_tool_use` の設定をサポート ([#2257](https://github.com/agentscope-ai/agentscope-java/pull/2257))

**Model Providers**

- サードパーティ互換ベンダーのための共有ベースとして OpenAI 互換の拡張パッケージを追加 ([#2208](https://github.com/agentscope-ai/agentscope-java/pull/2208))
- DeepSeek をファーストクラスのモデルプロバイダーとして追加(`deepseek:<model>`、`DEEPSEEK_API_KEY`) ([#2307](https://github.com/agentscope-ai/agentscope-java/pull/2307), [#2211](https://github.com/agentscope-ai/agentscope-java/issues/2211))
- GLM(智谱 AI)プロバイダーと専用フォーマッターを追加 ([#2316](https://github.com/agentscope-ai/agentscope-java/pull/2316))
- Kimi(Moonshot AI)プロバイダーと専用フォーマッターを追加 ([#2320](https://github.com/agentscope-ai/agentscope-java/pull/2320), [#2213](https://github.com/agentscope-ai/agentscope-java/issues/2213))
- MiniMax OpenAI 互換プロバイダーを追加 ([#2299](https://github.com/agentscope-ai/agentscope-java/pull/2299))

**Harness / Tools**

- リモートサブエージェントのイベントストリーミングと HITL の再開 ([#2559](https://github.com/agentscope-ai/agentscope-java/pull/2559))
- `taskId` による非同期ツール結果の待機 ([#2529](https://github.com/agentscope-ai/agentscope-java/pull/2529))
- イメージパッケージング向けに `AGENTSCOPE_WORKSPACE` 環境変数によるデフォルトワークスペース ([#2310](https://github.com/agentscope-ai/agentscope-java/pull/2310))

**AG-UI**

- AG-UI モジュールのイベント機構をアップグレード ([#2306](https://github.com/agentscope-ai/agentscope-java/pull/2306), [#2202](https://github.com/agentscope-ai/agentscope-java/issues/2202))
- マルチモーダルな AG-UI メッセージのための型付き `MessageContent` / `InputContent` を導入 ([#2518](https://github.com/agentscope-ai/agentscope-java/pull/2518), [#551](https://github.com/agentscope-ai/agentscope-java/issues/551))

**Spring Boot Starters**

- Ollama Spring Boot Starter を追加 ([#2176](https://github.com/agentscope-ai/agentscope-java/pull/2176), [#2172](https://github.com/agentscope-ai/agentscope-java/issues/2172))

### リファクタリング

- Toolkit のデフォルト実行モードを並列に変更し、関連ドキュメントを改善 ([#2558](https://github.com/agentscope-ai/agentscope-java/pull/2558)、[#2529](https://github.com/agentscope-ai/agentscope-java/pull/2529) のフォローアップ)
- セッションメタデータのストレージを抽象化し、ビルダーを具体的なストア実装から切り離す ([#2258](https://github.com/agentscope-ai/agentscope-java/pull/2258), [#2068](https://github.com/agentscope-ai/agentscope-java/issues/2068))
- Kubernetes サンドボックスストアを [agent-sandbox](https://github.com/kubernetes-sigs/agent-sandbox) の CRD / コントローラーの上に再構築し、クラスタがサンドボックスのライフサイクルとウォームプールを所有するようにする ([#2308](https://github.com/agentscope-ai/agentscope-java/pull/2308))

### 修正

**Core / Agent**

- pending recovery が HITL の承認を消費してしまうのを防止 ([#2109](https://github.com/agentscope-ai/agentscope-java/pull/2109), [#2534](https://github.com/agentscope-ai/agentscope-java/issues/2534))
- 変換された `onModelCall` のテキスト差分を最終メッセージに適用し、ネイティブの構造化出力パーサーが古いテキストを見ないようにする ([#2469](https://github.com/agentscope-ai/agentscope-java/pull/2469), [#2385](https://github.com/agentscope-ai/agentscope-java/issues/2385))
- 完全な raw JSON から null のストリーミングツール引数を修復 ([#2451](https://github.com/agentscope-ai/agentscope-java/pull/2451), [#768](https://github.com/agentscope-ai/agentscope-java/issues/768))
- グレースフルシャットダウンのレジストリ増大 / OOM を防ぐため、`ReActAgent.close()` で state-saver のバインドを解除 ([#2322](https://github.com/agentscope-ai/agentscope-java/pull/2322), [#2321](https://github.com/agentscope-ai/agentscope-java/issues/2321))
- メモリリークを修正するため、`ReActAgent.close()` で `ShutdownStateSaver` のバインドを解除 ([#2384](https://github.com/agentscope-ai/agentscope-java/pull/2384))
- ユーザーによる割り込みに割り込み理由をマーク ([#2260](https://github.com/agentscope-ai/agentscope-java/pull/2260))
- エージェント状態ファイルの書き込み時に不正な Unicode を処理(`UnmappableCharacterException`) ([#2255](https://github.com/agentscope-ai/agentscope-java/pull/2255), [#2204](https://github.com/agentscope-ai/agentscope-java/issues/2204))
- 推論ミドルウェアのイベント(例: `InboxMiddleware` の `HintBlockEvent`)を `streamEvents()` に転送 ([#2179](https://github.com/agentscope-ai/agentscope-java/pull/2179), [#2160](https://github.com/agentscope-ai/agentscope-java/issues/2160))
- `ToolResultBlock.error` を構造化エラーとしてマーク ([#2174](https://github.com/agentscope-ai/agentscope-java/pull/2174), [#2157](https://github.com/agentscope-ai/agentscope-java/issues/2157), [#2111](https://github.com/agentscope-ai/agentscope-java/issues/2111))

**Model Providers**

- DashScope: `qwen3.8-max` をマルチモーダルエンドポイントへルーティング ([#2553](https://github.com/agentscope-ai/agentscope-java/pull/2553))
- DashScope: 呼び出し元が `request_id` を読み取れるよう、SSE のエラーレスポンスボディを保持 ([#2278](https://github.com/agentscope-ai/agentscope-java/pull/2278), [#2197](https://github.com/agentscope-ai/agentscope-java/issues/2197))
- OpenAI: リトライが HTTP リクエストを再発行するよう、ストリーミング分岐を `Flux.defer` でラップ ([#2079](https://github.com/agentscope-ai/agentscope-java/pull/2079))
- OpenAI: `[DONE]` センチネルでストリームを終了 ([#2104](https://github.com/agentscope-ai/agentscope-java/pull/2104))
- OpenAI: コンテンツの重複を避けるため、非チャンクのサマリーイベントメッセージを破棄 ([#2367](https://github.com/agentscope-ai/agentscope-java/pull/2367))
- OpenAI: `OpenAIMessageConverter` の `name` フィールドをサニタイズ ([#2346](https://github.com/agentscope-ai/agentscope-java/pull/2346))
- OpenAI AutoConfiguration: api-key をオプションにする ([#2175](https://github.com/agentscope-ai/agentscope-java/pull/2175))
- DeepSeek フォーマッター: `system` ロールを保持 ([#2189](https://github.com/agentscope-ai/agentscope-java/pull/2189), [#2168](https://github.com/agentscope-ai/agentscope-java/issues/2168))
- Ollama: `OllamaChatModel` で `stream` フラグを尊重 ([#2415](https://github.com/agentscope-ai/agentscope-java/pull/2415))
- Anthropic: `ToolChoice.None` をツールの無効化にマップ(以前は誤ってツール使用を強制していた) ([#2232](https://github.com/agentscope-ai/agentscope-java/pull/2232), [#2221](https://github.com/agentscope-ai/agentscope-java/issues/2221))
- モデルプロバイダーの最適化と互換性の微調整 ([#2474](https://github.com/agentscope-ai/agentscope-java/pull/2474))

**Harness / Tools / Sandbox**

- リモートサブエージェントが転送するイベントに `taskId` を刻印 ([#2575](https://github.com/agentscope-ai/agentscope-java/pull/2575))
- disable フラグに応じてメモリのプロンプトガイダンスをゲート ([#2565](https://github.com/agentscope-ai/agentscope-java/pull/2565))
- イベントの取りこぼしを避けるため、親の完了より先にサブエージェント終了を発行 ([#2544](https://github.com/agentscope-ai/agentscope-java/pull/2544))
- 親がキャンセルされたときにサブエージェントのイベントストリームをクローズ ([#2481](https://github.com/agentscope-ai/agentscope-java/pull/2481), [#2480](https://github.com/agentscope-ai/agentscope-java/issues/2480))
- 生成されたサブエージェントに対して親の DENY ルールを強制 ([#2477](https://github.com/agentscope-ai/agentscope-java/pull/2477))
- スキルのプロモーション中に `RuntimeContext` を保持 ([#2465](https://github.com/agentscope-ai/agentscope-java/pull/2465))
- サブエージェントに対して Plan Mode を強制 ([#2377](https://github.com/agentscope-ai/agentscope-java/pull/2377))
- ワークスペースのパストラバーサル(例: `../`)を拒否 ([#2358](https://github.com/agentscope-ai/agentscope-java/pull/2358))
- Windows のローカルシェル実行をサポート(作業ディレクトリ付きコマンドと文字コードのデコード) ([#2304](https://github.com/agentscope-ai/agentscope-java/pull/2304), [#2268](https://github.com/agentscope-ai/agentscope-java/issues/2268))
- マルチテナントの混信を防ぐため、静的なサブエージェントレジストリをランタイムコンテキストごとに分離 ([#2371](https://github.com/agentscope-ai/agentscope-java/pull/2371), [#2328](https://github.com/agentscope-ai/agentscope-java/issues/2328))
- チェーンされたコンパクションで以前のサマリーを保持し、ユーザーの意図を保存 ([#2360](https://github.com/agentscope-ai/agentscope-java/pull/2360))
- スキルの分離とツール結果履歴を保持 ([#2319](https://github.com/agentscope-ai/agentscope-java/pull/2319))
- `RemoteFilesystem` の再帰 glob が検索ルート自体のファイルにもマッチするように修正 ([#2343](https://github.com/agentscope-ai/agentscope-java/pull/2343))
- オプションの FilesystemTool パラメータを `required=false` としてマーク ([#2227](https://github.com/agentscope-ai/agentscope-java/pull/2227))
- shell-execute の `working_directory` パラメータとツール使用ヒントを最適化 ([#2107](https://github.com/agentscope-ai/agentscope-java/pull/2107))
- 宣言されたサブエージェントが親の `modelExecutionConfig` / `toolExecutionConfig` を継承 ([#2252](https://github.com/agentscope-ai/agentscope-java/pull/2252))
- `sessionId` パラメータの説明を修正 ([#2195](https://github.com/agentscope-ai/agentscope-java/pull/2195))

**Storage / Transport**

- PostgreSQL BaseStore のスキーマサポート ([#2273](https://github.com/agentscope-ai/agentscope-java/pull/2273), [#2192](https://github.com/agentscope-ai/agentscope-java/issues/2192))
- PostgreSQL の upsert SQL 構文エラーを修正 ([#2167](https://github.com/agentscope-ai/agentscope-java/pull/2167), [#2166](https://github.com/agentscope-ai/agentscope-java/issues/2166))
- `JdkHttpTransport` の SSE ストリームが絶対タイムアウトによって切断される問題を修正 ([#1322](https://github.com/agentscope-ai/agentscope-java/pull/1322), [#1302](https://github.com/agentscope-ai/agentscope-java/issues/1302))

**Spring Boot / Examples**

- Spring Boot スターターのパッケージ名を修正 ([#2264](https://github.com/agentscope-ai/agentscope-java/pull/2264))
- サンプルで生の DashScope モデル名を使用(無効な `dashscope:` プレフィックスを削除) ([#2318](https://github.com/agentscope-ai/agentscope-java/pull/2318))
- `RuntimeContextExample` の DashScope モデル名を修正 ([#2228](https://github.com/agentscope-ai/agentscope-java/pull/2228), [#2229](https://github.com/agentscope-ai/agentscope-java/issues/2229))
- スキルサンプルのリソースパスを修正 ([#2250](https://github.com/agentscope-ai/agentscope-java/pull/2250))
- ドキュメントとサンプルを改善 ([#2508](https://github.com/agentscope-ai/agentscope-java/pull/2508))

### ドキュメント

- README の Java 2.0 機能一覧に Agent Evolution を追加 ([#2494](https://github.com/agentscope-ai/agentscope-java/pull/2494))
- オールインワン依存関係にモデルプロバイダーが含まれることを明記 ([#2425](https://github.com/agentscope-ai/agentscope-java/pull/2425), [#840](https://github.com/agentscope-ai/agentscope-java/issues/840))
- ドキュメントのリンクリダイレクトを修正 ([#2203](https://github.com/agentscope-ai/agentscope-java/pull/2203), [#2198](https://github.com/agentscope-ai/agentscope-java/issues/2198))
- バージョン別スコープの `llms.txt` アーティファクトを生成(`/v1`、`/v2`) ([#2188](https://github.com/agentscope-ai/agentscope-java/pull/2188), [#2185](https://github.com/agentscope-ai/agentscope-java/issues/2185))
- モデルビルダーのカスタマイザをドキュメント化 ([#2092](https://github.com/agentscope-ai/agentscope-java/pull/2092))
- モデルのドキュメントを更新 ([#2100](https://github.com/agentscope-ai/agentscope-java/pull/2100))
- README のドキュメントリンクとリリースノートの URL を修正 ([#2099](https://github.com/agentscope-ai/agentscope-java/pull/2099))
- AG-UI のドキュメントを更新 ([#2274](https://github.com/agentscope-ai/agentscope-java/pull/2274))

---

## 2.0.0 (GA)

> リリース日: 2026-07-10

AgentScope Java 2.0.0 が Generally Available になりました。これは 2.0 系列で初めての本番運用対応リリースであり、AgentScope Java の進化における「透明な開発」から「システムエンジニアリング」への1つのマイルストーンです。

**クイックリンク:** [クイックスタート](/v2/ja/docs/quickstart) | [V1 移行ガイド](/v2/ja/docs/change-log) | [本番投入](/v2/ja/docs/others/going-to-production)

### 2.0 のコア設計概要

AgentScope Java 2.0 は、**エージェントが信頼性をもってタスクを完了できるようにする**という1つの目標を中心に据えた体系的なアップグレードです。以下はそのコア設計の概要です。

**2層のエージェントアーキテクチャ**

- **ReActAgent**: 「推論 → ツール呼び出し → 応答」の ReAct ループを提供するステートレスな推論コア。2.0 では、エージェントのインスタンスは完全にステートレスです — 呼び出しごとの可変状態はすべて Reactor Context を介して伝播され、単一のインスタンスが複数の `(userId, sessionId)` の組み合わせに安全かつ同時にサービスを提供できます
- **HarnessAgent**: Middleware と Toolkit のチャネルを通じて ReActAgent を拡張し、ワークスペース、メモリ、サンドボックス、サブエージェント、スキル、Plan Mode をエンジニアリングインフラストラクチャとして追加します — コアの推論ループは維持され、拡張されるだけです

**メッセージとイベントストリーム**

統一された ContentBlock メッセージモデル(TextBlock / DataBlock / ToolUseBlock / ToolResultBlock / HintBlock など)と、28種類の型付き AgentEvent を発行する `streamEvents()` を組み合わせることで、エージェントの実行を観測可能・対話可能・中断可能にします。フロントエンド UI は、テキスト差分、ツール呼び出し、ユーザー確認、その他のライフサイクルイベントをリアルタイムで追跡できます

**パーミッションシステム**

新しい PermissionEngine が、ツール呼び出しに対する3状態の意思決定メカニズムを確立します: 許可 / ユーザー承認が必要 / 拒否。決定は静的ルール、ツールの種類、入力内容の分析に基づいて行われます。機微な操作は自動的に HITL 承認フローに入ります

**Middleware 拡張メカニズム**

5段階のオニオン + パイプラインのハイブリッドモデル(`onAgent` / `onReasoning` / `onActing` / `onModelCall` / `onSystemPrompt`)は、コアフレームワークを安定させたまま、ロギング、トレーシング、セキュリティチェック、ビジネスポリシー、コンテキスト注入のための柔軟な拡張ポイントを提供します

**コンテキストエンジニアリング**

構造化コンパクションはタスクの目標、現在の状態、重要な発見、次のステップを保持します。過大なツール結果は自動的にディスクへオフロードされ、コンテキストにはプレースホルダーのみが残ります。ファイルツールは「編集前に読む」ポリシーを強制し、組み込みのキャッシュで冗長な IO を削減します

**Workspace 抽象化**

「エージェントが何をするか」を「どこで実行するか」から切り離します。ローカルファイルシステム、Docker、Kubernetes、E2B クラウドサンドボックスのバックエンドが単一のインタフェースの背後で統一されます。組み込みのウォームアッププールが、並列の RL ロールアウトのシナリオをサポートします

**モデルのフォールトトレランス**

Qwen / OpenAI / Anthropic / Gemini / DeepSeek / Ollama をカバーする、統一された Credential + ModelRegistry の抽象化。設定可能な最大リトライ回数とフォールバックモデル — プライマリモデルが利用不能なときに自動フェイルオーバーします

**エンタープライズ分散デプロイ**

1行の `DistributedBackend` 設定(Redis / OSS / MySQL / PostgreSQL / COS)。`AgentStateStore` は `(userId, sessionId)` によって自動的に分割されます。レプリカをまたぐセッション復旧、サンドボックスの状態スナップショット、レプリカをまたぐサブエージェントのルーティング

**プロトコルの相互運用性**

組み込みの A2A(Agent-to-Agent)と MCP(Model Context Protocol)のサポートに加え、AG-UI プロトコルへの適応も提供し、標準化されたエージェント間通信とフロントエンドのレンダリングニーズをカバーします

**マルチエージェントオーケストレーション**

宣言的なサブエージェント仕様(YAML / Markdown)、同期ブロッキングとバックグラウンド委譲の両モードを備えたランタイムの `agent_spawn` / `agent_send`。サブエージェントのイベントストリームは、親の `streamEvents()` へリアルタイムに転送できます

**スキルシステム**

4層のスキル合成(Classpath / FileSystem / Nacos / Marketplace)+ SkillFilter による細粒度のフィルタリング + 自己学習のクローズドループ(提案 → キュレーション → プロモーション)

---

### RC5 からの変更点

以下は、2.0.0-RC5(2026-07-07)と GA リリースの間の増分の変更です。

#### 追加

- HITL がすべてのツール呼び出しを拒否したときに `AllToolsDeniedEvent` フックを発火させ、全拒否シナリオのアプリケーションレベルでの処理を可能にする ([#2083](https://github.com/agentscope-ai/agentscope-java/pull/2083))
- 繰り返される長時間のブロッキング待機を防ぐため、`wait_async_results` にガードレールを追加 ([#2093](https://github.com/agentscope-ai/agentscope-java/pull/2093))
- PostgreSQL バックエンドの分散 HarnessAgent 状態のために `PostgresDistributedStore` を追加 ([#2054](https://github.com/agentscope-ai/agentscope-java/pull/2054))
- Spring Boot スターターにおける OpenAI、DashScope、Anthropic モデル向けのビルダーカスタマイザを追加 ([#2045](https://github.com/agentscope-ai/agentscope-java/pull/2045))

#### 修正

**Core / Agent**

- NIO スレッド上での `block()` を避けるため、`seedSystemMsg` をリアクティブにする ([#2086](https://github.com/agentscope-ai/agentscope-java/pull/2086))
- PERMISSION_ASKING の結果メッセージに ASKING の ToolUseBlock を含める ([#2082](https://github.com/agentscope-ai/agentscope-java/pull/2082))
- `activateOnSkill` フィールドで SkillToolGroup をアクティベート ([#2057](https://github.com/agentscope-ai/agentscope-java/pull/2057))
- セッション喪失を防ぐため、ユーザーによる割り込み時にエージェント状態を保存 ([#1970](https://github.com/agentscope-ai/agentscope-java/pull/1970))

**Model Providers**

- Anthropic: API の要件を満たすため、並列ツール呼び出しを交互のメッセージに分割 ([#2090](https://github.com/agentscope-ai/agentscope-java/pull/2090))
- OpenAI: `nativeStructuredOutput` を設定可能にする ([#2069](https://github.com/agentscope-ai/agentscope-java/pull/2069))

**Harness / Tools / Sandbox**

- 外部ツール実行が正しくサスペンド結果を生成するように修正 ([#2071](https://github.com/agentscope-ai/agentscope-java/pull/2071))
- `isReadOnly` を AgentTool インタフェースに昇格させることで、Plan Mode 中の SkillLoadTool を許可 ([#2067](https://github.com/agentscope-ai/agentscope-java/pull/2067))
- AgentSpawnTool の親の購読がキャンセルされたときに、孤立したサブエージェントを割り込む ([#2064](https://github.com/agentscope-ai/agentscope-java/pull/2064))
- MemoryFlushMiddleware における不要な ReActAgent 型制限を削除 ([#2078](https://github.com/agentscope-ai/agentscope-java/pull/2078))
- ROOTED モードで先頭が `/` のパスをワークスペース相対として解決 ([#2049](https://github.com/agentscope-ai/agentscope-java/pull/2049))
- ワークスペース投影の前にマーケットプレイスのスキルを事前ステージング ([#2059](https://github.com/agentscope-ai/agentscope-java/pull/2059))
- Kubernetes の `hydrateWithArchive` で null の終了コードを成功として扱う ([#1915](https://github.com/agentscope-ai/agentscope-java/pull/1915))
- 永続化された状態から再開する際に更新された WorkspaceSpec を使用 ([#1928](https://github.com/agentscope-ai/agentscope-java/pull/1928))
- AgentRun の MCP レスポンスでネストされた JSON とバナープレフィックスをサポート ([#1930](https://github.com/agentscope-ai/agentscope-java/pull/1930))
- Docker の workspaceRoot に解決済みの workingDir を使用 ([#2033](https://github.com/agentscope-ai/agentscope-java/pull/2033))

**Channel**

- グループメッセージのルーティングを修正するため、OutboundAddress に PeerKind を含める ([#2060](https://github.com/agentscope-ai/agentscope-java/pull/2060))

**A2A**

- 断片化を避けるため、ストリーミングのテキストチャンクをマージ ([#2058](https://github.com/agentscope-ai/agentscope-java/pull/2058))

---

## 2.0.0-RC5

> リリース日: 2026-07-07

### 破壊的変更

- **モデルプロバイダーのモジュール化** — OpenAI、Gemini、Anthropic、DashScope、Ollama のモデルプロバイダーが `agentscope-core` から独立した `agentscope-extensions-model-*` 拡張モジュールへ移動しました。アプリケーションは対応する拡張の依存関係を追加する必要があります ([#1890](https://github.com/agentscope-ai/agentscope-java/pull/1890), [#1916](https://github.com/agentscope-ai/agentscope-java/pull/1916), [#1947](https://github.com/agentscope-ai/agentscope-java/pull/1947), [#1972](https://github.com/agentscope-ai/agentscope-java/pull/1972))

### 追加

- すべてのプロバイダーのメッセージコンバーター(OpenAI、DashScope、Gemini、Anthropic)で統一された `DataBlock` サポート。単一エージェント、マルチエージェント、ツール結果のパスをカバー ([#1933](https://github.com/agentscope-ai/agentscope-java/pull/1933))
- ツールと組み合わせたネイティブの構造化出力処理 — 構造化出力をサポートするモデルは、ツール呼び出しと並行して JSON スキーマ制約を強制できる ([#1904](https://github.com/agentscope-ai/agentscope-java/pull/1904))
- DashScope モデル向けのネイティブ構造化出力サポート ([#1935](https://github.com/agentscope-ai/agentscope-java/pull/1935))
- 動的なトークン注入(例: OAuth のリフレッシュ)のための、`McpClientBuilder` における `httpRequestCustomizer` サポート ([#1992](https://github.com/agentscope-ai/agentscope-java/pull/1992))
- `AguiEvent` を AG-UI プロトコル仕様に合わせる — 不足していたイベントタイプを追加 ([#1862](https://github.com/agentscope-ai/agentscope-java/pull/1862))
- サブエージェント向けのオプションのスキル許可リストフィルタ ([#1873](https://github.com/agentscope-ai/agentscope-java/pull/1873))
- `NacosSkillRepository` における `knownSkillNames` サポート ([#1853](https://github.com/agentscope-ai/agentscope-java/pull/1853))
- Tencent Cloud COS バックエンドの状態永続化のための `CosAgentStateStore`、`CosBaseStore`、`CosDistributedStore` ([#1857](https://github.com/agentscope-ai/agentscope-java/pull/1857))
- `ChatUsage` でキャッシュされたプロンプトトークンを公開 ([#1868](https://github.com/agentscope-ai/agentscope-java/pull/1868))

### 修正

**Core / Agent**

- ユーザーによる割り込みからの復旧時にエージェント状態を永続化 ([#2008](https://github.com/agentscope-ai/agentscope-java/pull/2008))
- フォールバックモデルを `ReActAgent` に配線 ([#1851](https://github.com/agentscope-ai/agentscope-java/pull/1851))
- `ReActAgent` のストリームイベントのブロック終了順序を修正 ([#1829](https://github.com/agentscope-ai/agentscope-java/pull/1829))
- エージェントコンテキストに追加する前に `ToolResultBlock` の状態を更新 ([#1886](https://github.com/agentscope-ai/agentscope-java/pull/1886))
- リソースリークを避けるため、classpath スキルの JAR ファイルシステムを再利用 ([#1981](https://github.com/agentscope-ai/agentscope-java/pull/1981))
- `Flux.create` コールバックにおける `serializeOnKey` ゲートのリークを解決 ([#1796](https://github.com/agentscope-ai/agentscope-java/pull/1796))

**Model Providers**

- `thinkingBudget` を OpenAI 互換 API リクエストにマップ ([#2028](https://github.com/agentscope-ai/agentscope-java/pull/2028))
- Anthropic のストリーミング思考イベント処理を修正 ([#1943](https://github.com/agentscope-ai/agentscope-java/pull/1943))
- `OllamaOptions` の `fromOptions`/`toBuilder` で `executionConfig` を保持 ([#2011](https://github.com/agentscope-ai/agentscope-java/pull/2011))
- DashScope の thinking モードで強制ツール選択をデグレードさせる ([#1882](https://github.com/agentscope-ai/agentscope-java/pull/1882))

**Harness / Sandbox**

- リモートスナップショットの状態デシリアライズを復元 — Jackson のラウンドトリップ後に `RemoteSnapshotClient` を再注入 ([#2013](https://github.com/agentscope-ai/agentscope-java/pull/2013))
- リクエストごとにインスタンスを再作成する際に THROTTLED メモリ保存モードが状態を失う問題を修正 ([#1788](https://github.com/agentscope-ai/agentscope-java/pull/1788))
- ウェイクアップディスパッチを通じて `userId` を伝播 ([#2001](https://github.com/agentscope-ai/agentscope-java/pull/2001))
- メッセージバスのハートビートを `parallel` ではなく `boundedElastic` スケジューラ上で実行 ([#1974](https://github.com/agentscope-ai/agentscope-java/pull/1974))
- `fromAgent` における `GracefulShutdownMiddleware` の重複を回避 ([#1952](https://github.com/agentscope-ai/agentscope-java/pull/1952))
- `ShellPathPolicy` が返すスキルパス中のスペースをエスケープ ([#2031](https://github.com/agentscope-ai/agentscope-java/pull/2031))
- YAML のパースに失敗した場合にシンプルなキー値抽出にフォールバック ([#2027](https://github.com/agentscope-ai/agentscope-java/pull/2027))
- `ls` でサンドボックスのファイルサイズを報告 ([#1838](https://github.com/agentscope-ai/agentscope-java/pull/1838))
- Windows の `list_files` のパスを正規化 ([#1892](https://github.com/agentscope-ai/agentscope-java/pull/1892))
- `LocalFilesystem.edit()` におけるファイル内容の `\r\n` を `\n` に正規化 ([#2020](https://github.com/agentscope-ai/agentscope-java/pull/2020))
- `CompositeFilesystem` で `"."` をルート相当として扱う ([#1830](https://github.com/agentscope-ai/agentscope-java/pull/1830))
- 名前空間の脱出を防ぐため、`working_directory` を検証 ([#1834](https://github.com/agentscope-ai/agentscope-java/pull/1834))
- 分散 `AgentStateStore` が設定されていない場合に `LocalFilesystemSpec` にフォールバック ([#1841](https://github.com/agentscope-ai/agentscope-java/pull/1841))
- `exit=null` を引き起こす Kubernetes の `hydrateWithArchive` における WebSocket の競合を修正 ([#1903](https://github.com/agentscope-ai/agentscope-java/pull/1903))
- ラップされたサンドボックスの base64 ダウンロードを許容 ([#1866](https://github.com/agentscope-ai/agentscope-java/pull/1866))
- `AgentRun` サンドボックスの API バージョンプレフィックスを削除 ([#1891](https://github.com/agentscope-ai/agentscope-java/pull/1891))
- E2B サンドボックス向けに connect JSON コーデックサポートを追加 ([#1844](https://github.com/agentscope-ai/agentscope-java/pull/1844))

**Tracing / Observability**

- Reactor の `ContextView` から親の OTel Context を読み取ることで、`OtelTracingMiddleware` における孤立スパンを修正 ([#1940](https://github.com/agentscope-ai/agentscope-java/pull/1940))
- `OtelTracingMiddleware` で子スパンが正しい親スパンを見られない問題を修正 ([#1909](https://github.com/agentscope-ai/agentscope-java/pull/1909))
- Reactor コンテキストを chunk イベントフックへ伝播 ([#1923](https://github.com/agentscope-ai/agentscope-java/pull/1923))

**Subagent**

- 親の `RuntimeContext` を子エージェントへ伝播 ([#1833](https://github.com/agentscope-ai/agentscope-java/pull/1833))
- 親の middleware をサブエージェントへ伝播 ([#1843](https://github.com/agentscope-ai/agentscope-java/pull/1843))

**A2A**

- ストリーミングのバックプレッシャーを処理 ([#1734](https://github.com/agentscope-ai/agentscope-java/pull/1734))
- A2A 変換をまたいで AgentScope のメッセージロールを保持 ([#1995](https://github.com/agentscope-ai/agentscope-java/pull/1995))

**AG-UI**

- run の入力とフロントエンドツールを伝播 ([#1895](https://github.com/agentscope-ai/agentscope-java/pull/1895))

**その他**

- 時期尚早な評価を防ぐため、middleware の `doFlush` を `Mono.defer` でラップ ([#1880](https://github.com/agentscope-ai/agentscope-java/pull/1880))
- Nacos の自動設定はオプトインにすべき(`matchIfMissing=false`)で、A2A の server-addr オーバーライドを修正 ([#1709](https://github.com/agentscope-ai/agentscope-java/pull/1709))
- DataAgent の `MarketContributionService` 向けに `ObjectMapper` Bean を追加 ([#1993](https://github.com/agentscope-ai/agentscope-java/pull/1993))

### ドキュメント

- ストリームイベントの `blockId` のセマンティクスを明確化 ([#2016](https://github.com/agentscope-ai/agentscope-java/pull/2016))
- モデルプロバイダーのドキュメントを改善 ([#1986](https://github.com/agentscope-ai/agentscope-java/pull/1986))
- 無効な `ChatResponse.isLast` への参照を削除 ([#1921](https://github.com/agentscope-ai/agentscope-java/pull/1921))
- マルチレプリカ Redis サンプルを修正 — jedis の依存関係を宣言し `stateStore` を追加 ([#1869](https://github.com/agentscope-ai/agentscope-java/pull/1869))
- メモリファイルを表示しコンパクションを発火させるように `MemoryCompactionExample` を修正 ([#1978](https://github.com/agentscope-ai/agentscope-java/pull/1978))

---

## 2.0.0-RC4

> リリース日: 2026-06-18

### 追加

- Agent harness がメッセージバス、非同期ツールレジストリ、スケジュールされたウェイクアップディスパッチを含む、非同期ツール実行と通知をサポート ([#1802](https://github.com/agentscope-ai/agentscope-java/pull/1802))
- エージェント呼び出しのための String/Message 利便オーバーロード。すべてのフォーマッターが `HintBlock` をサポート ([#1802](https://github.com/agentscope-ai/agentscope-java/pull/1802))
- ツールコンテキスト状態内の永続的な spawn レジストリにより、サブエージェントのレプリカをまたぐルーティングとセッション復旧が可能に ([#1817](https://github.com/agentscope-ai/agentscope-java/pull/1817))
- `DynamicSkillMiddleware` が `ToolkitAware` を実装し、解決済みの toolkit を動的に受け取る ([#1828](https://github.com/agentscope-ai/agentscope-java/pull/1828))
- Kubernetes サンドボックスが Pod への環境変数の注入をサポート ([#1789](https://github.com/agentscope-ai/agentscope-java/pull/1789))

### 修正

- 2フェーズのアーカイブ戦略を使うことで、Kubernetes のファイルアップロードにおける SIGKILL の競合状態を修正 ([#1826](https://github.com/agentscope-ai/agentscope-java/pull/1826))
- タイムアウトしたサブエージェントがリトライ時に割り込まれないリソースリークを修正 ([#1784](https://github.com/agentscope-ai/agentscope-java/pull/1784))
- `RuntimeContext` をコピーする際に型付き属性が失われる問題を修正 ([#1813](https://github.com/agentscope-ai/agentscope-java/pull/1813))
- MySQL の utf8mb4 文字セットの下で `JdbcStore` のテーブル初期化が失敗する問題を修正 ([#1781](https://github.com/agentscope-ai/agentscope-java/pull/1781))
- 重複書き込みを防ぐため、セッションの JSONL オフロードを冪等にする ([#1774](https://github.com/agentscope-ai/agentscope-java/pull/1774))
- `TelemetryTracer` における OpenTelemetry のコンテキスト伝播を修正 ([#1799](https://github.com/agentscope-ai/agentscope-java/pull/1799))
- tool choice の取得時にオプションが null の場合の `OllamaChatModel` における NPE を修正 ([#1803](https://github.com/agentscope-ai/agentscope-java/pull/1803))
- 正しいシリアライズのため、`LocalSandboxSnapshot` に不足していた Jackson アノテーションを追加 ([#1825](https://github.com/agentscope-ai/agentscope-java/pull/1825))
- サンドボックスの glob が `**/` の再帰パターンをサポートしていなかった問題を修正 ([#1684](https://github.com/agentscope-ai/agentscope-java/pull/1684))
- `SkillFilter` のマッチングがスキル名ではなく複合 ID を使用していた問題を修正 ([#1771](https://github.com/agentscope-ai/agentscope-java/pull/1771))
- `MultiModalTool` でカスタムのデフォルトビジョンモデルを許可 ([#1701](https://github.com/agentscope-ai/agentscope-java/pull/1701))

### ドキュメント

- middleware ドキュメント内の誤ったフックシグネチャを修正 ([#1835](https://github.com/agentscope-ai/agentscope-java/pull/1835))
- ドキュメントのサンプル内で存在しない `.sandboxContext()` への参照を修正 ([#1792](https://github.com/agentscope-ai/agentscope-java/pull/1792))
- v2 ドキュメントの `getToolName()` → `getToolCallName()` を修正 ([#1760](https://github.com/agentscope-ai/agentscope-java/pull/1760))
- ドキュメントサイトに AI コンテキストメニューを追加

---

## 2.0.0-RC3

> リリース日: 2026-06-11

### 追加

- **`AgentResultEvent`** — エージェントが処理を終えたとき、`AgentEndEvent` の直前に発行される新しいイベントタイプで、最終的な `Msg` の結果を運びます。`streamEvents()` の購読者は、別途 `Mono<Msg>` の戻り値を購読することなく、イベントストリームから直接結果を取得できます
- **`CustomEvent`** — 状態変更、チームの更新など、アプリケーションレベルの通知をミドルウェアがフロントエンドの購読者へプッシュするための汎用の拡張可能イベントで、ユースケースごとの `AgentEventType` エントリを追加する必要がありません。組み込みの既知の名前: `state_updated`、`team_updated`
- **`HintBlockEvent`** — ストリーミングされるテキスト/思考ブロックとは対照的に、チームメッセージ、バックグラウンドのツール結果、ユーザーの割り込みなど、完全なコンテンツを配信するためのワンショットのヒントブロックイベント
- **`WorkspacePathNormalizer`** — 絶対パスをワークスペース相対の形式に変換するファイルパス正規化ユーティリティ。アクティブなファイルシステムモード(ローカル / サンドボックス)に基づいてプレフィックスを登録し、モードをまたぐプレフィックスの衝突を防ぎます
- **ツールイベントの `toolCallName`** — `ToolCallDeltaEvent`、`ToolCallEndEvent`、`ToolResultDataDeltaEvent`、`ToolResultEndEvent`、`ToolResultTextDeltaEvent` が `toolCallName` フィールドを持つようになり、購読者は開始イベントからの名前マッピングをキャッシュする必要がなくなりました

### 変更

- **統一された `call()` / `streamEvents()` コア** — `call()` と `streamEvents()` の両方に共通する実装として、内部の `buildAgentStream` メソッドを導入し、`onAgent` middleware チェーンがすべての呼び出しパスで一貫して発火するようにしました。`call()` は現在、イベントストリーム内の `AgentResultEvent` から結果を抽出します。レガシーの独立した `agentImpl` ロジックは削除されました
- **分散デプロイでセッション状態を常にストアから再読み込み** — `AgentStateStore` が設定されている場合、`activateSlotForContext` は各呼び出しの開始時にストアからエージェント状態とパーミッションエンジンを再読み込みするようになり、同じ sessionId がマシンをまたいでドリフトしたときに古いローカルキャッシュを読んでしまう問題を防ぎます
- **`ToolResultEvictionMiddleware` のタイミング修正** — (状態がまだ書き込まれておらずエビクションが no-op になってしまっていた)`onActing` から `onReasoning` へ移動し、エビクションが実行される前にツール結果が永続化されるようにしました
- **`LocalFilesystem` のパス解決を簡素化** — 冗長なコードを減らすためパス解決ロジックをリファクタリング

### 修正

- テストで `RuntimeContext` が `userId` を設定しておらず、ユーザー分離が不正確になっていた問題を修正

---

## 2.0.0-RC2

> リリース日: 2026-06-09

### 追加

- **`projectWritable` モード**(`LocalFilesystemSpec`) — 有効にすると、エージェントのファイル書き込みはパスによってルーティングされます: ワークスペースのメタデータ(`MEMORY.md`、`agents/`、`skills/` など)はワークスペースへ、それ以外(コード、設定)はプロジェクトディレクトリへ着地します。コード生成エージェント向けに設計されています。[Filesystem · Project-writable mode](/v2/ja/docs/harness/filesystem#プロジェクト書き込み可能モードprojectwritable) を参照
- **ランタイムのパーミッションモード切り替え** — ランタイムでセッションごとにパーミッションモードを動的に調整するための新しい `HarnessAgent.setPermissionMode()` / `getPermissionMode()`
- **サブエージェントのイベントストリーム転送** — `streamEvents()` は、子エージェントの中間イベント(`TextBlockDelta`、`ToolCallStart` など)をリアルタイムで転送するようになり、それぞれが発信元のエージェントを識別する `source` パスを運びます
- **`AgentEvent.source` フィールド** — すべての `AgentEvent` インスタンスが `source` フィールドを持つようになり、同一のイベントストリーム内でメインエージェントのイベント(`source = null`)とサブエージェントのイベント(`source = "main/researcher"` のパス形式)を区別できます。これにより、購読者側で追加の状態を持たずにデマルチプレックスできます
- **Compaction / Memory 向けのカスタムプロンプトとモデル** — `CompactionConfig` と `MemoryConfig` が `.model()` と `.prompt()` のビルダーメソッドを獲得し、エージェントのプライマリモデルの代わりに、コンテキストコンパクションとメモリ抽出のための専用の軽量モデルとカスタムプロンプトを使えるようになりました
- **Qwen 3.7 モデルサポート** — `ModelRegistry` が `dashscope:qwen3.7-plus` などの Qwen 3.7 シリーズのモデルを解決できるようになりました
- **サブエージェントへの直接メッセージング** — `agent_send` により、呼び出し元は親エージェントの推論ループを経由せずに、宣言されたサブエージェントへ直接メッセージを送り、その応答を受け取れます
- **Channel モジュール** — IM プラットフォーム統合(DingTalk、Feishu/Lark、WeCom、GitHub、GitLab)のための新しい `agentscope-extensions-channel` モジュールファミリー。すぐに使える会話インタフェースのための組み込み ChatUI を含みます
- **`DistributedBackend` 統一インタフェース** — すべての分散ストレージコンポーネント(`AgentStateStore`、`BaseStore`、`SandboxSnapshotSpec`)を単一の設定ポイントに統合する、新しい `DistributedBackend` 抽象化。組み込みの実装には `RedisDistributedBackend`、`OssDistributedBackend`、`MysqlDistributedBackend` が含まれます。`HarnessAgent.builder().distributedBackend(backend)` の1回の呼び出しで分散バックエンド全体が配線されます — もはや stateStore、baseStore、snapshotSpec を個別に設定する必要はありません

### 変更

- **エージェントが完全にステートレスに** — `ReActAgent` はもはやセッションごとの可変状態を一切保持しません。すべての可変状態は内部の `CallExecution` にカプセル化され、Reactor Context を介して伝播されます。単一のエージェントインスタンスが、複数の `(userId, sessionId)` の組み合わせに安全かつ同時にサービスを提供できます
- **セッションインタフェースが `AgentStateStore` に置き換わる** — `SessionManager`、`StatePersistence`、および関連するレガシーインタフェースを削除し、`AgentStateStore`(組み込み: `InMemoryAgentStateStore`、`JsonFileAgentStateStore`、`RedisAgentStateStore`、`MysqlAgentStateStore`)に統一。`(userId, sessionId)` によって自動的に分割されます
- **`BaseStore` インタフェースのパッケージをリネーム** — `BaseStore` と関連するインタフェースが新しいパッケージへ移動しました。古いインポートパスを使用しているコードは更新が必要です
- **拡張モジュールの座標を統合** — いくつかの拡張の Maven 座標が、機能単位で再編成されました。例えば `agentscope-extensions-session-redis` は、`RedisAgentStateStore`、`RedisStore`、`RedisSnapshotSpec` などをまとめた `agentscope-extensions-redis` になりました。古い座標を使用していた場合は、pom の `<artifactId>` を更新してください
- **サンドボックス実装を harness コアから抽出** — Docker、Kubernetes、E2B、Daytona、AgentRun のサンドボックスバックエンドが `agentscope-harness` から独立した拡張モジュール(`agentscope-extensions-sandbox-*`)へ移動しました。harness コアは抽象インタフェース(`SandboxFilesystemSpec` など)のみを保持し、具体的なサンドボックスの依存関係を推移的に引き込まなくなりました。サンドボックスサポートが必要な場合は、対応する拡張を明示的に追加してください。例えば Docker には `agentscope-extensions-sandbox-docker` を追加します
- **Plan Mode の改善** — プランファイルの永続化と復旧を改善し、`plan_enter` / `plan_write` / `plan_exit` のツールチェーンの相互作用をより滑らかにし、HITL 承認フローをより堅牢にしました
- **スキルの自己進化の強化** — propose(`ProposeSkillTool`)→ curate(`SkillCurator`)→ promote(`SkillPromoter`)のクローズドループを洗練し、スキルのマッチング精度とセッションをまたいだ再利用を改善しました
- `DashScopeHttpClient` のリクエストタイムアウトとリトライポリシーの調整
- `ModelRegistry` のモデル解決ロジックの改善
- `AgentState` のシリアライズフォーマットの更新

### 修正

- クロスセッション復元中に `PermissionContextState` が状態を失う問題を修正
- `agentscope-all` に4つのサンドボックス拡張モジュール(`sandbox-kubernetes`、`sandbox-agentrun`、`sandbox-daytona`、`sandbox-e2b`)が欠けていた問題を修正

---

## 2.0.0-RC1

> リリース日: 2025-05-28

最初の 2.0 リリース候補です。1.x からの完全なアーキテクチャアップグレードを含みます:

- Harness エンジニアリング(ワークスペース、メモリ、スキル、サブエージェント、Plan Mode、コンテキストコンパクション)
- エンタープライズグレードの分散デプロイ(マルチテナント分離、サンドボックス実行、パーミッションシステム、セッション復旧)
- コアフレームワークの再設計(イベントストリーム、メッセージモデル、Middleware、HITL)

完全な 1.x → 2.0 の変更リストについては、[V1 移行ガイド](/v2/ja/docs/change-log) を参照してください。
