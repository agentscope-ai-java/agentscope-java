---
title: "コンテキスト圧縮"
description: "重要な情報を失うことなく、会話をモデルのトークン予算内に収める"
---

:::{note}
このページでは、`HarnessAgent` が会話をモデルのトークン予算内に収めるために使う戦略――**コンテキスト圧縮**――を扱います。これは [Context & AgentState](../building-blocks/context.md) で説明されているステートレスなエンジン設計と `AgentState` の永続化の上に成り立っています。まだ読んでいなければ先にそちらを読んでください――圧縮は、永続化層が保存・復元するのと同じ `AgentState` に対して動作します。

**両者がどう連携するか**:圧縮はメモリ上で `AgentState.contextMutable()` を変更します。ステートストアは呼び出しの最後に更新後の `AgentState` を書き込みます。この2つの経路は独立していますが、常にこの順序で実行されます――ステートストアが目にするのは圧縮後の状態です。
:::

モデルのトークン予算は有限です。長時間続く会話は、事前に積極的に圧縮するか、最終的にモデルのハードリミットに衝突するかのどちらかです。`HarnessAgent` はフルセットの圧縮スタックを同梱しており、`.compaction(...)` / `.toolResultEviction(...)` でオプトインします。

## `HarnessAgent` が同梱するもの

| 戦略 | 何を解決するか | いつ発火するか | ミドルウェア |
|------|----------|----------|--------|
| **会話の要約** | コンテキストが*深すぎる*――メッセージ数/トークン合計が積み上がる | 各モデル推論呼び出しの前 | `CompactionMiddleware` |
| **大きなツール結果の退避** | コンテキストが*広すぎる*――単一のツール結果が巨大 | ツール実行の後 | `ToolResultEvictionMiddleware` |
| **オーバーフローのセーフティネット** | モデルが実際に `context_length_exceeded` を返した | `call()` が例外を投げたとき | `HarnessAgent.recoverFromOverflow` |
| **要約前の引数切り詰め** | ツール呼び出しの引数(例:`write_file` の本文)は大きいが、後で誰も読まない | 要約前の軽量な事前パス | `CompactionConfig.TruncateArgsConfig` |

この4つは**直交しており、自由に組み合わせられます**。4つともデフォルトでは無効です。

### 1. 会話の要約(`CompactionMiddleware`)

メッセージ数または推定トークン数に基づいて発火します。会話の**プレフィックス**を1回の LLM 呼び出しで構造化された要約に蒸留し、**直近 N 件のメッセージをそのまま保持**して、`[summary] + [recent tail]` を `AgentState.contextMutable()` に書き戻します。

```java
HarnessAgent.builder()
    .compaction(CompactionConfig.builder()
        .triggerMessages(30)     // fire at 30 messages
        .keepMessages(10)        // keep last 10 verbatim
        .build())
    .build();
```

デフォルトの要約プロンプトは内容を `SESSION INTENT / SUMMARY / ARTIFACTS / NEXT STEPS` に整理します――エンジニアリングやオーケストレーション向けのエージェントでうまく機能します。`CompactionConfig` は、要約用の LLM 呼び出しに専用モデルを指定する `.model(...)` もサポートしています(未設定の場合はエージェントのプライマリモデルにフォールバックします)。完全な設定サーフェス(`triggerTokens`、`keepTokens`、`flushBeforeCompact`、`offloadBeforeCompact`、`model`、`TruncateArgsConfig`)と要約プロンプトのテンプレートは [Memory — 圧縮を有効にする](./memory.md#圧縮を有効にする) にあり、ここでは重複掲載しません。

### 2. 大きなツール結果の退避(`ToolResultEvictionMiddleware`)

要約とは独立しています。ツール結果がしきい値(デフォルト 8万文字 ≈ 2万トークン)を超えると、全出力がワークスペースディレクトリに書き込まれ、**コンテキスト内のメッセージは先頭 + 末尾のプレビュー(それぞれ約2千文字)と `read_file` へのポインタに置き換えられます**。エージェントは必要に応じて全文を読み込みます。

```java
HarnessAgent.builder()
    .toolResultEviction(ToolResultEvictionConfig.defaults())
    .build();
```

`read_file` / `write_file` / `edit_file` / `list_files` / `memory_*` / `session_search` はデフォルトで除外されます――これらは自己ページネーションするか、小さいペイロードしか返さないためです。`grep_files` と `glob_files` は結果件数の上限を強制しますが、個々のマッチが異常に大きい場合の第二のセーフティネットとして退避の対象には残ります。**シェルの `execute` は意図的に除外されていません**。コマンド出力はいくらでも大きくなり得るためです。

詳細は [Memory — 大きなツール実行結果のオフロード](./memory.md#大きなツール実行結果のオフロード) にあります。

### 3. オーバーフローのセーフティネット

モデルが `context_length_exceeded` / `maximum context` / `token limit` エラーを返した場合、`HarnessAgent.recoverFromOverflow()` は強制的に `triggerMessages=1` の極端な圧縮を実行し、**自動的に一度だけリトライ**します。ビルド時に `.compaction(...)` が設定されている必要があります――そうでなければエラーはそのまま伝播します。

追加の設定は不要です:圧縮をオンにすれば、オーバーフロー復旧も付いてきます。

### 4. 要約前の引数切り詰め(任意)

LLM 要約パスの前に、**LLM を使わない**文字列切り詰めパスが、肥大化したツール呼び出しの引数(`write_file`、`edit_file` の本文)を切り詰めます。

```java
CompactionConfig.builder()
    .triggerMessages(80)
    .truncateArgs(CompactionConfig.TruncateArgsConfig.builder()
        .maxArgLength(2000)
        .truncationText("... [truncated] ...")
        .build())
    .build();
```

多くのワークロードでは、この1ステップだけで要約トリガーの発火をほぼゼロコストでかなり先送りできます。

## Memory との連携

`CompactionConfig.flushBeforeCompact`(デフォルト `true`)は、**要約する前に会話プレフィックスから事実を抽出して長期記憶へ書き込むかどうか**を決定します――`MemoryFlushMiddleware` + `MemoryFlushManager` が処理し、`<workspace>/MEMORY.md` と `memory/*.md` を読み取って新しい事実を段階的に追記します。要約によってプレフィックスのメッセージが落とされても、情報は残ります:エージェントは `memory_search` / `memory_get` でそれを取り戻せます。

同様に、`offloadBeforeCompact`(デフォルト `true`)は要約の前に**生のメッセージ**を非圧縮の `*.log.jsonl` に書き込むため、`session_search` は引き続きそこへアクセスできます。

> 完全な Memory サブシステム――二層構造、バックグラウンドメンテナンス(アーカイブ、マージ)、記憶ツール――は [Memory](./memory.md) にあります。圧縮と記憶はよく一緒に使われますが、独立したスイッチを持ちます。

## 圧縮が触れないもの

`ConversationCompactor` は `AgentState.contextMutable()` 内の**会話メッセージリスト**にのみ作用します。以下は他の `AgentState` フィールドに存在し、**要約の影響を受けません**。

- **プランモードの状態**(`AgentState.getPlanModeContext()`):プランモードがアクティブかどうか、現在のプランファイルのパス。プランファイル自体はワークスペースの `plans/` 配下にあり、プランモード独自のライフサイクルによって管理されます。[Plan Mode](./plan-mode.md) を参照してください。
- **サブエージェントのバックグラウンドタスク**(`task_id`、ステータス、結果):`<workspace>/agents/<parentAgentId>/tasks/<sessionId>.json` に保存され、`TaskRepository` によって管理されます。完了した結果は次の推論ターンでシステムリマインダーとして親に注入し返されます――これらは**会話メッセージストリームには入らない**ため、要約はそれらに触れることができません。[Subagent — バックグラウンドタスクのストレージ](./subagent.md#バックグラウンドタスクのストレージ) を参照してください。
- **`todo_write` タスクリスト**(`AgentState.getTasksContext()`):独立したフィールドで、`AgentState` と一緒に永続化されますが、圧縮の経路には含まれません。[Plan Mode — `todo_write` との連携](./plan-mode.md#todo_write-との連携) を参照してください。
- **パーミッションルール**(`getPermissionContext()`):独立したフィールドで、自己永続化します。

これらはそれぞれ独自のステートマシンと復旧経路を持っており、圧縮の経路はそれらにとって透過的です――プランや実行中のバックグラウンドタスクを失う心配をせずに `.compaction(...)` を有効にできます。

## エージェントに自分の履歴を調べさせる

セッション機能が有効な場合(デフォルトで有効)、3つの検索ツールが自動的に登録されます。

- `session_list agentId="..."` — あるエージェントの過去のセッションを一覧表示する。
- `session_history agentId="..." sessionId="..." lastN=20` — あるセッションの直近 N 件のメッセージ。
- `session_search query="..." agentId="..."` — 履歴全体をキーワード検索する。

これらのツールは**非圧縮の会話ログ**(`<workspace>/agents/<agentId>/sessions/<sessionId>.log.jsonl`)を読み取るため、コンテキスト内の会話が要約されていても、エージェントは元のメッセージを取り出すことができます。

---

## 関連ページ

- [Context & AgentState](../building-blocks/context.md) — ステートレスなエンジン設計、`AgentState` の構造、状態の永続化、`RuntimeContext`
- [Architecture](./architecture.md) — 1つの呼び出しの中でコンテキスト、状態の永続化、ワークスペースがどう協調するか
- [Memory](./memory.md) — 長期記憶、完全な圧縮設定、大きなツール結果のオフロード、バックグラウンドメンテナンス
- [Plan Mode](./plan-mode.md) — プラン状態の独立した永続化と復旧
- [Subagent](./subagent.md) — バックグラウンドタスクがどこに存在し、ノードの移行をどう生き延びるか
- [Filesystem](./filesystem.md) — `userId` に基づくマルチテナントのパス分離
