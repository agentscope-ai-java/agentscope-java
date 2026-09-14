---
title: "Memory"
description: "二層構造の長期記憶、会話の圧縮、大きなツール結果のオフロード;プロンプトとトリガーポリシーはカスタマイズ可能"
---

## 役割

会話のコンテキストを有界に保ちながら、エージェントが「セッションをまたいで事実を記憶する」ことを可能にします。Harness は記憶を二層に分けます。

- **層1・日次ログ** `memory/YYYY-MM-DD.md` — 毎日追記専用で、生のまま重複排除されない;
- **層2・整理された長期記憶** `MEMORY.md` — LLM によって定期的にマージ+重複排除される;推論の各ステップで長期記憶としてシステムプロンプトに注入される。

3つの補助的な仕組み:

- **会話の圧縮** — コンテキストが長すぎるときに履歴を要約し、直近のテールを保持する;
- **オーバーフローのセーフティネット** — モデルが実際にエラーを起こしたとき、強制的に圧縮してリトライする;
- **大きなツール結果のオフロード** — 単一のツールが多すぎる量を返したとき、ディスク+プレースホルダーへオフロードする。

## 3つの LLM 呼び出しを俯瞰する

記憶パイプラインは**3つの独立した LLM 呼び出し**を実行し、それぞれ独自のプロンプトとトリガールールを持ちます。カスタマイズする際に最も混乱しやすい部分です。

| # | 操作 | 書き込み先 | デフォルトのプロンプト | カスタマイズ方法 |
|---|------|----------|---------------|----------|
| 1 | **Flush** — 会話ウィンドウから長期的な事実を抽出する | `memory/YYYY-MM-DD.md`(追記) | `MemoryFlushManager.DEFAULT_FLUSH_PROMPT` | `MemoryConfig.builder().flushPrompt(...)` |
| 2 | **Consolidation** — 日次台帳を `MEMORY.md` にマージする | `MEMORY.md`(全面書き換え) | `MemoryConsolidator.DEFAULT_CONSOLIDATION_PROMPT` | `MemoryConfig.builder().consolidationPrompt(...)` |
| 3 | **Compaction summary** — 会話のプレフィックスを1つの要約メッセージに蒸留する | 現在のコンテキストに注入される | `CompactionConfig.DEFAULT_SUMMARY_PROMPT` | `CompactionConfig.builder().summaryPrompt(...)` |

最初の2つは「長期記憶の定着」であり `MemoryConfig` に存在します。3つ目は「コンテキスト内圧縮」であり `CompactionConfig` に存在します。この3つの LLM 呼び出しはすべてデフォルトでエージェントのプライマリモデルを共有しますが、`MemoryConfig` と `CompactionConfig` はそれぞれ `.model(...)` による上書きをサポートしているため、これらの補助操作には軽量なモデルを使うことができます。

## 二層がどう機能するか

```{mermaid}
graph LR
    Conv["conversation messages"]
    Conv -->|each call end / can be throttled| Flush["Flush LLM call"]
    Flush -->|extract new facts| Daily["memory/YYYY-MM-DD.md"]
    Conv -->|over threshold| Compactor["conversation compaction"]
    Compactor -->|offload raw| Sess["sessions/&lt;id&gt;.log.jsonl"]
    Compactor -->|flush again before summarizing| Flush
    Daily -. throttled background consolidation .-> MEM["MEMORY.md"]
    MEM -->|injected each reasoning step| SYS["system prompt"]
```

要点:

- 層1は追記のみで重複排除は一切行いません;層2は全体として定期的に書き換えられます;**この2つの層は互いを上書きすることはありません**。
- プロンプトに注入されるのは層2のみです;層1はマージされるのを待ちます。
- 圧縮中に落とされた生のメッセージも、後の監査や `session_search` のために、決して圧縮されないログファイル(`*.log.jsonl`)に保存されます。

## Flush はいつ発火するか

Flush(経路1)は3つの異なるタイミングで発火します。

1. **すべての `call()` の終了時** — `MemoryFlushMiddleware` のデフォルトの振る舞い。`flushTrigger` によって `NEVER` または `THROTTLED(Duration)` に調整できます。
2. **圧縮前の抽出** — `CompactionConfig.flushBeforeCompact = true`(デフォルト)のとき、会話のプレフィックスは要約される前に一度フラッシュされます。
3. **オーバーフローのセーフティネット** — モデルが実際に `context_length_exceeded` を返したとき、フレームワークはフラッシュを含む緊急圧縮を実行します。

この3つの箇所はすべて**同じ** `flushPrompt` を共有するため、それをカスタマイズすると3つすべてが変わります。

flush とオフロードはどちらも**非同期**です:レスポンスストリームが終わった後に `doOnComplete` を介してファイア・アンド・フォーゲット方式で起動されるため、現在の `call()` の戻りをブロックすることはありません。呼び出し元はまず完全なレスポンスを受け取り、flush の LLM 呼び出しと JSONL のオフロードはその後バックグラウンドで実行されます。

## 圧縮を有効にする

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("MyAgent")
    .model(model)
    .workspace(workspace)
    .compaction(CompactionConfig.builder()
        .triggerMessages(30)     // fire at 30 messages
        .keepMessages(10)        // keep the last 10 after compaction
        .build())
    .build();
```

一般的なオプション:

| フィールド | デフォルト | 意味 |
|-------|---------|------|
| `triggerMessages` | `50` | メッセージ数でトリガーする(`0` = オフ) |
| `triggerTokens` | `80_000` | 推定トークン数でトリガーする(`0` = オフ) |
| `keepMessages` | `20` | 保持するテールメッセージの数 |
| `keepTokens` | `0` | 非ゼロの場合、トークン予算で遡る;`keepMessages` を上書きする |
| `flushBeforeCompact` | `true` | 圧縮前に日次ログへ新しい事実を抽出する(経路2) |
| `offloadBeforeCompact` | `true` | 圧縮前に生のメッセージを非圧縮ログに追記する |
| `summaryPrompt` | `DEFAULT_SUMMARY_PROMPT` を参照 | 経路3の要約プロンプト(`{messages}` を含まなければならない) |
| `model` | `null`(エージェントのプライマリモデルを使用) | 圧縮要約呼び出し専用のモデル |

**オーバーフロー時の自動復旧**:モデルが `context_length_exceeded`(またはそれに類するもの)を返したとき、フレームワークは1回の圧縮を強制してリトライします――ただし `compaction(...)` が設定されている場合のみです;それ以外の場合、エラーはそのまま伝播します。

### もっと軽くしたい?まず引数を切り詰める

`write_file` のようなツール呼び出しは、後で誰も読まない巨大な引数を持ちます。LLM による要約の前に、**LLM を使わない**文字列切り詰めを実行できます。

```java
CompactionConfig.builder()
    .triggerMessages(80)
    .truncateArgs(CompactionConfig.TruncateArgsConfig.builder()
        .maxArgLength(2000)
        .truncationText("... [truncated] ...")
        .build())
    .build();
```

## 記憶パイプラインのカスタマイズ:`MemoryConfig`

`MemoryConfig` は、flush / consolidation のプロンプト、スロットル、保持期間、そして呼び出しごとの flush トリガーを設定する唯一の場所です。すべてのフィールドにデフォルトがあります。`.memory(...)` を呼ばなければ、これまでの振る舞いをビット単位で再現します。

呼び出しごとの flush とバックグラウンドの consolidation は、それぞれ独立したスロットルウィンドウを持ちます。どちらの場合も、最初に条件を満たした `call()` は即座に処理を実行します。最小間隔は以降の実行にのみ制限をかけるものであり、初期遅延ではありません。

### 例1:呼び出しごとの flush をスロットルしてトークンを節約する

すべてのエージェント呼び出しの後に flush LLM 呼び出しが走ると、長いセッションでは積み重なってしまいます。最大10分に1回にスロットルします。

```java
HarnessAgent.builder()
    ...
    .memory(MemoryConfig.builder()
        .flushTrigger(MemoryConfig.FlushTrigger.throttled(Duration.ofMinutes(10)))
        .build())
    .build();
```

補足:

- `THROTTLED` は**経路1**(呼び出しごとの flush)にのみ影響します。圧縮に組み込まれた flush(経路2)とオーバーフロー flush(経路3)は、それぞれ独自のトリガーで発火し続けます――圧縮はまれなので、この2つは構造上頻度が低くなります。
- 最初に条件を満たした呼び出しは即座に flush されます;`Duration.ofMinutes(10)` はそれ以降の呼び出しごとの flush のみを制限します。
- **オフロードは影響を受けません**。セッション JSONL は毎回完全に書き込まれ続けます。`session_search` とセッションの再開は引き続き機能します。

### 例2:呼び出しごとの flush を完全に無効化する

```java
.memory(MemoryConfig.builder()
    .flushTrigger(MemoryConfig.FlushTrigger.never())
    .build())
```

これで、flush は圧縮が発生したときだけ行われるようになります(素の圧縮と同じコスト)。

> flush と背後のメンテナンスの**両方**をオフにするには `.disableMemoryHooks()` を使ってください;`flushTrigger(NEVER)` は呼び出しごとの flush を止めるだけで、バックグラウンドの consolidation は動き続けます。

### 例3:デフォルトのプロンプトにプロジェクトルールを追加する

```java
.memory(MemoryConfig.builder()
    .flushPrompt(MemoryFlushManager.DEFAULT_FLUSH_PROMPT + """

        Additional project rules:
        - Never record customer PII (names, emails, phone numbers).
        - Always use English for project-internal vocabulary.
        """)
    .build())
```

### 例4:完全にカスタムな consolidation プロンプト

```java
.memory(MemoryConfig.builder()
    .consolidationPrompt("""
        You are merging daily memory ledgers into MEMORY.md.
        Keep within %d tokens (~%d chars). Output the complete file in markdown.
        ... your custom rules ...
        """)
    .build())
```

> **重要**:カスタムの consolidation プロンプトは、ちょうど2つの `%d` プレースホルダー(最大トークン数、次に最大文字数)を含んで**いなければなりません**。それ以外のものは、実行時に `MissingFormatArgumentException` に遭遇しないよう、Builder が構築時点で拒否します。

### 例5:バックグラウンドメンテナンスを調整する

```java
.memory(MemoryConfig.builder()
    .consolidationMinGap(Duration.ofHours(2))   // first call may run; later runs at least 2h apart
    .dailyFileRetentionDays(30)                 // archive daily logs after 30 days
    .sessionRetentionDays(60)                   // prune session JSONL after 60 days
    .consolidationMaxTokens(8_000)              // raise MEMORY.md cap to 8K tokens
    .build())
```

### 例6:記憶操作に小さいモデルを使う

flush と consolidation にはプライマリ推論モデルのフルパワーは必要ありません――コストを抑えるために安いモデルを使います。

```java
HarnessAgent.builder()
    .model("openai:o3")                   // primary reasoning model
    .memory(MemoryConfig.builder()
        .model("openai:gpt-4.1-mini")     // lighter model for memory ops
        .build())
    .compaction(CompactionConfig.builder()
        .model("openai:gpt-4.1-mini")     // lighter model for compaction
        .build())
    .build();
```

`model(String)` は `ModelRegistry.resolve()` を通じて解決されます;`Model` インスタンスを渡すこともできます。未設定の場合、エージェントのプライマリモデルにフォールバックします。

### `MemoryConfig` のフィールドリファレンス

| フィールド | デフォルト | 目的 |
|------|------|------|
| `model` | `null`(エージェントのプライマリモデルを使用) | flush / consolidation 専用のモデル;`Model` インスタンスまたは `"provider:model"` 文字列を受け付ける |
| `flushPrompt` | `null`(`DEFAULT_FLUSH_PROMPT` を使用) | 経路1の SYSTEM プロンプト |
| `consolidationPrompt` | `null`(`DEFAULT_CONSOLIDATION_PROMPT` を使用) | 経路2のテンプレート(2つの `%d` を含まなければならない) |
| `consolidationMaxTokens` | `4_000` | `MEMORY.md` のトークン上限 |
| `consolidationMinGap` | `30分` | バックグラウンドメンテナンス実行間の間隔;最初に条件を満たした呼び出しは即座に実行される |
| `dailyFileRetentionDays` | `90` | 日次ログが `memory/archive/` に移動するまでの日数 |
| `sessionRetentionDays` | `180` | `*.log.jsonl` が刈り取られるまでの日数 |
| `flushTrigger` | `FlushTrigger.always()` | `ALWAYS` / `NEVER` / `THROTTLED(Duration)` |

## 大きなツール結果のオフロード

圧縮とは独立しています。単一のツール呼び出しがしきい値を超える量を返した場合、全文がディレクトリに書き込まれ、先頭/末尾のプレビューとプレースホルダーだけがコンテキストに残されます。エージェントは全文を `read_file` できます。

```java
HarnessAgent.builder()
    ...
    .toolResultEviction(ToolResultEvictionConfig.defaults())
    .build();
```

デフォルト:

- 8万文字でトリガー
- 先頭+末尾に約2千文字ずつ保持し、「full content at `{path}`」という行を残す
- `read_file` はデフォルトで除外される(読み戻したばかりのものを再びオフロードしてしまうのを避けるため)

しきい値や書き込み先は `ToolResultEvictionConfig.builder()...build()` でカスタマイズできます。

## エージェント自身が使えるツール

記憶が有効な場合、エージェントは2つのツールを得ます。

- `memory_search query="..."` — `MEMORY.md` + `memory/*.md` へのキーワードスキャン、最大30件
- `memory_get path="memory/2026-06-02.md" startLine=10 endLine=40` — 特定の行範囲を読む

プロンプト内に「MEMORY truncated」という注記が現れると、モデルは通常 `memory_search` を呼び出してさらに遡って調べます。

## バックグラウンドメンテナンス

記憶が有効な場合、スロットル制御されたバックグラウンドジョブも実行されます。最初に条件を満たした `call()` は即座にそれを実行します;以降の呼び出しは最小間隔(デフォルト30分)を観測します。

- `dailyFileRetentionDays`(デフォルト90日)より古い日次ログを `memory/archive/` にアーカイブする
- `MEMORY.md` の consolidation パスを1回実行する
- `sessionRetentionDays`(デフォルト180日)より古いセッションログを刈り取る

メンテナンスに入ったからといって必ずしもモデルを呼び出すわけではありません:前回成功した consolidation 以降に新しい日次台帳のエントリがない場合、consolidation は LLM リクエストをスキップします。`FlushTrigger.never()` はこのメンテナンス経路を無効化しません。

すべてのしきい値は `.memory(MemoryConfig.builder()...)` で調整可能ですが、ほとんどのプロジェクトでは触る必要はありません。

## 完全にオフにする

記憶を自分で扱いたい場合や、独自のツールを配線したい場合:

```java
HarnessAgent.builder()
    ...
    .disableMemoryHooks()      // disables flush + background maintenance (+ auto-extract prompt line)
    .disableMemoryTools()      // skips memory_search / memory_get / memory_save / session_search
                               // and matching Memory Recall / tool Persistence guidance
    .build();
```

これらを組み合わせると、Domain Knowledge / AGENTS / knowledge のコンテキストは維持したまま、`<memory_context>`(`MEMORY.md`)の注入もスキップされます。

`disableMemoryHooks()` はバックグラウンドの記憶作業に対する核オプションです;単にスロットルしたいだけなら、代わりに `.memory(MemoryConfig.builder().flushTrigger(...).build())` を使ってください。

## 関連ページ

- [Workspace](./workspace.md) — ワークスペース内での `MEMORY.md` / `memory/` の配置場所
- [Context](../building-blocks/context.md) — 決して圧縮されない `*.log.jsonl` 会話ログ
- [Architecture](./architecture.md) — 長い会話の中の事実が `MEMORY.md` にどう定着するか
