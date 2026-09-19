---
title: サブエージェント
description: サブエージェントの宣言、同期/バックグラウンド呼び出し、自動プッシュバック、リモートサブエージェント、ストリーミング転送
---

## 役割

親が「独立していて、コンテキストを大量に消費し、並列化できる」タスクを委譲できるようにし、親自身のループを肥大化させないようにします。各サブエージェントは一時的なインスタンス(ローカルの `HarnessAgent` またはリモートスタブ)であり、独自のセッションを持ち、ツール結果を通じて結果を返します。

## 最小の例

最もシンプルな方法は、仕様ファイルをワークスペースに置くことです。ファイル名が `agent_id` になります:

`workspace/subagents/reviewer.md`:

```markdown
---
description: コードレビューの専門家。ユーザーが PR のレビュー、コードの問題調査、コードスタイルのチェックを求めているときに使用する。
---

あなたはコードレビューに特化したサブエージェントです。次のフローに従ってください:
1. まず read_file / grep_files でコンテキストを集める
2. ファイルと行を指定して具体的な提案を行う
3. 最後に全体の1〜5点のスコアで締めくくる
```

親はこれを推論中に呼び出せるようになります:

```
agent_spawn agent_id="reviewer" task="このPRのすべての変更をレビューしてください"
```

登録手続きは不要です。

## 3つの宣言方法

3つのソースが、ビルド時にマージされます:

| 方法 | 用途 | やり方 |
|-----|---------|-----|
| 組み込みの `general-purpose` | 汎用のフォールバック(親の能力をそのまま反映する) | 常に存在し、設定不要 |
| ワークスペースの仕様ファイル | プロジェクト固有で、バージョン管理される | `workspace/subagents/<id>.md` |
| プログラムによる宣言 | ランタイムに決定される(リモート、動的パラメータ) | `builder.subagent(SubagentDeclaration.builder()...)` |

### ワークスペースの仕様ファイル

`workspace/subagents/*.md` を非再帰的にスキャンします。ファイル名(`.md` を除く)が `agent_id` に**なります**——フロントマターで `name` を重ねて設定しては**いけません**。

```markdown
---
description: コードレビューの専門家     # 必須。モデルがこれを見て委譲するかどうかを判断する
workspace:
  mode: isolated              # デフォルトは isolated。shared にすると親のワークスペースを使う
  path: ./defs/reviewer       # 任意。省略時はフレームワークがサブディレクトリを自動作成する
model: openai:gpt-4o-mini     # 任意。省略時は親のモデルを継承する
steps: 8                      # 任意。spawn ごとの最大イテレーション数
temperature: 0.2              # 任意。親の GenerateOptions を上書きする
top_p: 0.95                   # 任意
hidden: false                 # true にするとモデルには一覧表示されない(プログラムからは呼び出し可能)
mode: subagent                # primary / subagent / all(デフォルトは all)。primary は spawn できない
expose_to_user: true          # 任意の三値。ユーザー公開を強制/禁止する(省略 = 意見なし)
enable_pending_tool_recovery: true # 任意。省略すると親の recovery 設定を継承する
tools: [read_file, grep_files]   # 任意。継承したツールに対する許可リスト
---

あなたはコードレビューに特化したサブエージェントです。
```

### プログラムによる宣言

```java
HarnessAgent.builder()
    .name("orchestrator")
    .model(model)
    .workspace(workspace)
    .subagent(SubagentDeclaration.builder()
        .name("reviewer")
        .description("コードレビューの専門家")
        .workspace(Path.of("./defs/reviewer"))
        .workspaceMode(WorkspaceMode.ISOLATED)
        .model("qwen3-max")
        .steps(8)
        .tools(List.of("read_file", "grep_files"))
        .build())
    .subagent(SubagentDeclaration.builder()
        .name("remote-researcher")
        .description("リモートリサーチのサブエージェント")
        .url("http://agent-task-server:8080")     // リモートサブエージェント
        .headers(Map.of("Authorization", "Bearer xxx"))
        .build())
    .build();
```

3つのソースは互いに排他的です: `workspace(...)`、`inlineAgentsBody(...)`、`url(...)` ——いずれか1つを選んでください。

`general-purpose` を含む、自動的に構築されるローカルサブエージェントは、親の `HarnessAgent.Builder.enablePendingToolRecovery(...)` 設定(デフォルト `false`)を継承します。宣言は `.enablePendingToolRecovery(true)` または `.enablePendingToolRecovery(false)` でこれを上書きできます。`null` は継承を意味します。ワークスペースの仕様ファイルは `enable_pending_tool_recovery`(または `enablePendingToolRecovery`)を受け付けます。有効化すると、新しい通常のメッセージが、失敗したセッションから読み込まれた呼び出しも含め、孤立した保留中のツール呼び出しを合成エラー結果で修復します。保留中のパーミッション確認は引き続き確認が必要であり、空入力での再開や呼び出し側が提供するツール結果は既存の振る舞いを維持します。リモートエージェントとカスタムファクトリは、recovery を自身で設定します。

### 組み込みの `general-purpose`

仕様ファイルは不要で、常に利用可能です。その役割は「汎用フォールバック」です——親の能力(同じモデル、ツール、スキル)をそのまま反映し、親のワークスペースを共有します。専用の仕様を書かずに、サブタスクのためにコンテキストを分離したい場合に便利です。

## ISOLATED と SHARED

`workspaceMode` は、何をサブエージェントのワークスペースとみなすかを決定します:

- **ISOLATED**(デフォルト): サブエージェントは独自のワークスペースを持ちます(`workspace.path` が省略された場合、フレームワークがサブディレクトリを自動作成します)。サブエージェントのランタイム状態は「親の sessionId × ユーザー」単位でバケット化されます——そのため、同じユーザーの異なる会話をまたいで同じサブエージェントを spawn しても、状態が混ざり合うことはありません。
- **SHARED**: サブエージェントは親のワークスペースを直接使用します。サブエージェントの出力を親がすぐに読み取るケース(例:`general-purpose`)に適しています。

## 同期か、バックグラウンドか?

親は `agent_spawn` でサブエージェントを作成します。鍵となるパラメータは `timeout_seconds` です:

- `timeout_seconds > 0`(デフォルト 30、最大 600)——**同期**呼び出し。親はこのステップでブロックし、結果はツール結果として返ります。デフォルトでは、待機がタイムアウトすると、実行中のランは**バックグラウンドタスクへ昇格**し(`status: timeout_promoted` + `task_id`)、実行を継続します。
- `timeout_seconds = 0` ——**バックグラウンド**呼び出し。`task_id` が即座に返され、サブエージェントはバックグラウンドで実行されます。

**`RuntimeContext` 経由で同期を強制する。** アプリケーションコードは、現在の呼び出しの `RuntimeContext` に `AgentSpawnTool.CTX_FORCE_SYNC = true` を設定することで、LLM による非同期の選択を上書きできます。また、任意で `CTX_FORCE_SYNC_TIMEOUT_SECONDS` を設定すると、厳密な待機時間の上限(秒)を指定できます:

```java
RuntimeContext ctx = RuntimeContext.builder()
    .sessionId("s-1")
    .put(AgentSpawnTool.CTX_FORCE_SYNC, true)
    .put(AgentSpawnTool.CTX_FORCE_SYNC_TIMEOUT_SECONDS, 120) // 任意。LLM の timeout_seconds を上書きする
    .build();
```

有効化すると:

1. `CTX_FORCE_SYNC_TIMEOUT_SECONDS` が設定されている場合、それは LLM の `timeout_seconds` を**完全に置き換えます**(`<= 0` は 30 秒にフォールバックし、最大は 600 秒です)。
2. その上書きがない場合、LLM が指定した `timeout_seconds=0` はデフォルトの同期タイムアウト(30秒)へ強制変換されます——バックグラウンドタスクは投入され**ません**——一方、正の値の LLM タイムアウトはそのまま維持されます。
3. 同期待機がタイムアウトした場合、ツールは `status: timeout` を返してサブエージェントを中断します——バックグラウンドの `task_id` へ昇格することは**ありません**。

`agent_send` も同じスイッチを尊重します。1つのターン内で複数の force-sync `agent_spawn` 呼び出しを行っても、Toolkit のデフォルト設定の下では並行に実行されます。

目標をリソース面で競合しない独立したサブタスクに分割できる場合、親は同じ推論ターン内で複数の同期的なサブエージェント呼び出しを発行できます。Toolkit はデフォルトでツールの並行実行(`ToolkitConfig.parallel=true`)を行うため、これらの同期呼び出しは `ReActAgent` と `HarnessAgent` の両方で並行に進みます。親は、そのバッチのツール結果がすべて返ってきた後にのみ次の推論ステップに入り、同期的な fan-out / fan-in バリアを形成します。ツール呼び出しを直列化するには、`ToolkitConfig.builder().parallel(false).build()` で構築したカスタム `Toolkit` を渡してください。

まず、独立性と依存関係グラフに基づいて作業を分解してください: 依存エッジを持たないノードは並行サブエージェントの良い候補です。依存関係のあるノードは、ディスパッチやマージの前に上流の結果を待つべきです。短いタスクやクリティカルパス上のタスクは、親がその結果を使って推論を続けられるよう、同期的な待機や明示的なバリアの良い候補です。長時間のタスクは、親が他の作業を続けている間バックグラウンドで実行させ、後で結果を収集してマージできます。

### バックグラウンドタスクは自動的にプッシュバックされる

バックグラウンドタスクが完了すると、親は**ポーリングする必要がありません**——親の次の推論ステップの前に、フレームワークが完了したタスク結果を、会話の末尾にシステムリマインダーとして注入します:

```
<system-reminder>
バックグラウンドタスクが配信されました:
- task_id=xxx, agent=research-analyst, status=COMPLETED
  result summary: ...
</system-reminder>
```

親は自然に応答するか、処理を続けます。つまり、プロンプトに「task_output をポーリングするのを忘れないように」と書く**必要はありません**——それは古いやり方です。

### バックグラウンドタスクのツール

裏側では、サブエージェントのライフサイクルは2つのツール群に分かれています:

| ツール | 役割 |
|------|------|
| `agent_spawn` | サブエージェントを作成し、任意でタスクを実行する(同期またはバックグラウンド) |
| `agent_send` | 既存のサブエージェントにフォローアップメッセージを送る |
| `agent_list` | アクティブなサブエージェントインスタンスを一覧表示する |
| `task_output` | `task_id` によってバックグラウンドタスクの結果を取得する(ブロッキング/非ブロッキング) |
| `wait_async_results` | バックグラウンドの結果を待つ。特定の `task_ids` がすべて完了するのを待つことも、`wait_all=true` で現在のセッションの未完了タスクのスナップショットを待つこともできる |
| `task_cancel` | 実行中のバックグラウンドタスクをキャンセルする |
| `task_list` | すべてのバックグラウンドタスクを、現在のステータスとともに一覧表示する |

`agent_spawn` / `agent_send` はサブエージェントの**インスタンス**(作成、再利用、通信)を管理します。`task_output` / `wait_async_results` / `task_cancel` / `task_list` はバックグラウンドの**タスク結果**(状態確認、出力取得、待機、キャンセル)を管理します。両者をつなぐ橋渡しが `task_id` です——`timeout_seconds=0` のときに `agent_spawn` または `agent_send` が返します。

> ほとんどの場合、自動プッシュバックの仕組みが、明示的なツール呼び出しなしに結果を配信します。タスクツールは、逃げ道(エスケープハッチ)として有用です: プッシュバックが発火する前に進捗を確認する、まとめて揃っている必要がある一連の結果を待つ、不要になったタスクをキャンセルする、会話の圧縮後にタスク状態を復旧する、といった場合です。

非同期の結果を収集する一般的な方法は3つあります:

- **自動プッシュバック**: ブロックしない場合のデフォルトの経路です。完了した子タスクは、次の推論ステップの前に `<system-reminder>` として注入されます。
- **対象を絞ったタスク確認**: `task_output(task_id, block=false)` を呼び出して、1つのタスクの現在の状態または最終結果を確認します。
- **待機バリア(すべてを収集しなければならない場合に推奨)**: `wait_async_results(task_ids="id1,id2")` または `wait_async_results(wait_all=true)` を呼び出します。バリアモードは、対象の集合がすべて終端状態になるまで待機し、**各タスクの結果をツールの戻り値に埋め込みます**。そのため、すぐに処理を続けられます。`wait_all=true` は、呼び出し開始時点での未完了タスクのスナップショットを使用し、待機中に新しく作成されたタスクは追加されません。

> **レガシーな inbox-any**: `task_ids` も `wait_all` も指定せずに `wait_async_results` を呼び出すと、*いずれか*のインボックスメッセージが届くまでしか待機しません。これは wait-all バリアではありません——グループ内のすべてのタスクが完了する必要がある場合は、`task_ids` または `wait_all=true` を使用してください。

## 既存のサブエージェントにフォローアップを送る

`agent_spawn` は `agent_key`(ランタイムインスタンスのハンドル)を返します。フォローアップメッセージを送るには、これまたは `label` を使用します:

```
agent_send agent_key="agent:reviewer:abc-123" message="スキーマの変更もチェックしてください"
```

spawn 時に `label` を設定していれば、`agent_key` の代わりにそれを使用できます:

```
agent_spawn agent_id="reviewer" task="この PR をレビューしてください" label="pr-reviewer"
agent_send label="pr-reviewer" message="スキーマの変更もチェックしてください"
```

アクティブなサブエージェントを一覧表示するには: `agent_list`。

## 永続セッション

デフォルトでは、`agent_spawn` のたびに新しいセッションを持つ新規のサブエージェントが作成されます——それ以前の呼び出しの記憶はありません。複数回の spawn にわたって同じサブエージェントインスタンスを再利用するには、宣言で `persistSession(true)` を設定します:

```java
.subagent(SubagentDeclaration.builder()
    .name("note-taker")
    .description("会話全体を通してノートを蓄積する")
    .persistSession(true)
    .build())
```

`persistSession` が有効な場合、フレームワークは `(parentSessionId, agentId, label)` から決定的なキーを導出します。同じ組み合わせで再度 `agent_spawn` が呼び出されると、既存のエージェントインスタンスが再利用されます——その会話履歴と状態は保持されます。

## サブエージェントをユーザーに公開する

通常、サブエージェントはユーザーから見えません——親の内部ツールとして舞台裏で動作します。`expose_to_user=true` を指定すると、親は Channel を通じて、サブエージェントを**ユーザーから直接アドレス指定できる**ようにできます:

```
agent_spawn agent_id="researcher" task="AI のトレンドを調査してください" expose_to_user=true
```

これは2つのことを行います:

1. **サブエージェントを Gateway に登録します**——ユーザーからアドレス指定可能なエントリポイントとして
2. ストリーミングイベントフローに **`SubagentExposedEvent` を発行します**——`subagentId` ハンドルを伴って

ユーザーのクライアントは `SubagentExposedEvent` を受け取り、その後、親エージェントを完全にバイパスして、サブエージェントへ直接メッセージを送信できるようになります:

```java
// クライアント側: イベントストリーム内で公開されたサブエージェントを監視する
chat.sendStream(SendOptions.userId("user-1"), "researcher を spawn して AI のトレンドを調査させて")
    .doOnNext(event -> {
        if (event instanceof SubagentExposedEvent se) {
            // se.getSubagentId() → これを使ってサブエージェントと直接会話する
            // se.getAgentId()    → サブエージェントの種類(例:"researcher")
            // se.getLabel()      → 任意の人間可読な名前
        }
    })
    .blockLast();

// 公開されたサブエージェントへ直接メッセージを送る
chat.sendToSubagent(subagentId, "特に LLM エージェントに焦点を当ててください").block();
```

これは「分岐」シナリオに便利です: 親が専門のサブエージェントを spawn し、ユーザーはその専門エージェントと独立して会話を続けます。Channel 側の完全な API については [Channel — 公開されたサブエージェントとの対話](/v2/ja/docs/harness/channel#公開されたサブエージェントとの対話) を参照してください。

### 有効化する方法

`agent.channel(...)` を使用します——ブリッジは自動的に配線され、設定は一切不要です:

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("orchestrator")
    .model("dashscope:qwen-plus")
    .build();

// channel() は内部の gateway を作成しブリッジを配線する —— expose_to_user はそのまま機能する。
ChatUiChannel chat = agent.channel(ChatUiChannel.create());
```

Channel のバインディングがない場合、`agent_spawn` の `expose_to_user=true` は黙って無視されます——サブエージェントは通常どおり動作しますが、ユーザーには公開されません。`GatewayBootstrap` を使ったマルチエージェント構成については、[Channel — GatewayBootstrap でのスレッド公開](/v2/ja/docs/harness/channel#gatewaybootstrap-でのスレッド公開) を参照してください。

### コードから公開を制御する

LLM が `expose_to_user=true` を渡すことに頼るだけでは、柔軟性が十分でない場合があります。アプリケーションコードから、この決定を2つの方法で上書きできます。実効値は、次の優先順位(最も高いものから)で解決されます:

1. **`RuntimeContext` の呼び出しごとの上書き** —— 現在の呼び出し内のすべての `agent_spawn` に適用される
2. **`SubagentDeclaration` の型ごとのポリシー** —— そのサブエージェントの型に対する静的なデフォルト
3. **LLM が渡す `expose_to_user` ツール引数**
4. 上記のいずれも意見を示さない場合は **`false`**

**`RuntimeContext` 経由の呼び出しごとの上書き。** `AgentSpawnTool.CTX_EXPOSE_TO_USER` キーの下に `Boolean`(またはその文字列形式)を設定します:

```java
RuntimeContext ctx = RuntimeContext.builder()
    .userId("user-1")
    .put(AgentSpawnTool.CTX_EXPOSE_TO_USER, true)   // 強制的に有効化する。false は公開を禁止する
    .build();
```

**宣言における型ごとのポリシー。** 三値の `exposeToUser` を使用します——`TRUE` は常に公開し、`FALSE` は決して公開しません(LLM の `expose_to_user=true` を上書きします)。`null`(デフォルト)は、コンテキストの上書き、次いで LLM の引数に判断を委ねます:

```java
SubagentDeclaration decl = SubagentDeclaration.builder()
    .name("researcher")
    .description("トピックを調査し、まとめたレポートを返す。")
    .exposeToUser(true)   // このサブエージェントの型は常にユーザーからアドレス指定可能
    .build();
```

あるいは、Markdown のサブエージェント仕様のフロントマターでも同様です(こちらも三値です——キーを省略すると「意見なし」になります):

```markdown
---
name: researcher
description: トピックを調査し、まとめたレポートを返す。
expose_to_user: true
---
```

これにより、モデルの決定に関わらず公開を強制または禁止できる一方で、どちらのコード側のソースも意見を示さない場合には LLM に選択させることもできます。

### 再起動やマルチレプリカをまたいで

デフォルトでは、公開はプロセス内に限定されます: `subagentId` はそれを作成したノード上でのみ有効であり、再起動すると失われます。公開されたサブエージェントを**どのレプリカでも**、そして**再起動をまたいでも**解決可能にするには、状態やファイルシステムで使うのと同じ1行の設定である `distributedStore(...)` を使ってエージェントを構築します:

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("orchestrator")
    .model("dashscope:qwen-plus")
    .distributedStore(RedisDistributedStore.fromJedis(jedis))
    .build();

ChatUiChannel chat = agent.channel(ChatUiChannel.create());  // recovery は自動的に配線される
```

`subagentId` はストアに永続化され、サブエージェント自身の会話はセッション単位で分散 `AgentStateStore` から再読み込みされます——そのため、後続のメッセージが別のノードに届いたとしても、ユーザーは*同じ*サブエージェントと会話し続けることができます。マルチエージェントの `GatewayBootstrap` では `.distributedStore(...)` を渡してください(渡さない場合はメインエージェントの設定を継承します)。デプロイに関するガイダンス——`subagentId` を稼働中のノードへ戻すルーティング(スティッキールーティング)を含む——は [本番投入](/v2/ja/docs/others/going-to-production) にあります。

## エージェントに新しいサブエージェント仕様を書かせる

`agent_generate` ツール(**デフォルトでは無効**)を使うと、LLM は新しいサブエージェント仕様を起草し、`workspace/subagents/<name>.md` に書き込むことができます:

```java
// オプトイン(ビルド時):
// ビルダー内部の SubagentsMiddleware への参照を取得し、enableAgentGenerateTool を呼び出す
```

「作業の途中で、エージェントが新しい種類のヘルパーが必要だと気づいた」場合に便利です。本番環境では慎重に使用してください——通常は、エージェントに仕様を起草させ、ファイルを書き込む前に人間がレビューするようにします。

## 振る舞いに関する注意

- **`description` を丁寧に書く**: これはモデルが委譲を判断する際の主要なシグナルです。「コードレビュー」よりも「ユーザーが PR のレビューやコードスタイルのチェックを求めているときに使用する」のほうが、はるかに有用です。
- **再帰の安全性**: サブエージェントはさらにサブエージェントを spawn できません(強制的に葉ノードとしてマークされます)。加えて、最大3階層というハードな上限があります。
- **userId は伝播する**: 親の `RuntimeContext.userId` は子へ転送されるため、マルチテナント分離のチェーンは維持されます。
- **パーミッションの継承**: 親のすべての DENY パーミッションルールは、自動的に子へ伝播します。親であるツールが拒否されている場合、子も拒否されます——委譲によってセキュリティ境界をバイパスすることはできません。これを無効にするには、宣言で `inheritParentPermissions(false)` を設定してください。
- **ストリーミング転送**: 親の `stream()` 実行中、同期的なサブエージェントからの中間イベントは、(ソースタグ付きで)親の `Flux` へリアルタイムに転送し返されます。詳細は下記の [サブエージェントのストリーミング](#サブエージェントのストリーミング) を参照してください。

## リモートサブエージェント

`url` と任意の `headers` を設定するだけで、サブエージェントはリモートの HTTP サービス(Agent Protocol)を介して実行されます:

```java
.subagent(SubagentDeclaration.builder()
    .name("remote-researcher")
    .description("リモートリサーチのサブエージェント")
    .url("http://agent-task-server:8080")
    .headers(Map.of("Authorization", "Bearer xxx"))
    .remoteStreaming(true)          // 未設定時のデフォルト
    .remoteStreamDetail(RemoteStreamDetail.VERBOSE)  // 未設定時は FULL
    .remoteAskPolicy(RemoteAskPolicy.DENY)  // デフォルト
    .remoteContextAttributes(Map.of("region", "cn"))
    .build())
```

同じ同期(`timeout_seconds>0`)/バックグラウンド(`timeout_seconds=0`)のセマンティクスが適用されます。

リモートモード固有の宣言パラメータ:

| フィールド | デフォルト | 備考 |
|-------|---------|-------|
| `remoteStreaming` | `true`(未設定時) | 親が `streamEvents()` を使う場合、リモートタスクの SSE イベントを、`source` タグと `metadata.taskId` / `metadata.parentSessionId`(harness の `TaskRecord` / 親セッションと同じ ID)を付けて親のストリームへ転送する |
| `remoteStreamDetail` | `FULL` | リモートのイベントストリームのうち、どこまでを転送するか —— [リモートストリーミングの詳細度](#リモートストリーミングの詳細度) を参照 |
| `remoteAskPolicy` | `DENY` | リモートのツール確認(HITL)リクエストをどう解決するか —— [リモート認可](#リモート認可) を参照 |
| `remoteContextAttributes` | なし | すべての送信で `context.attributes` として送られる静的な呼び出し側属性。呼び出しごとの値をマージするには、親の `RuntimeContext` の `AgentSpawnTool.CTX_REMOTE_CONTEXT_ATTRIBUTES` キーの下に map を設定する。[コンテキスト属性](/v2/ja/integration/protocol/agent-protocol#コンテキスト属性) を参照 |

### リモートストリーミングの詳細度

ローカルのサブエージェントは、子のイベントをそのまま転送します。リモートの場合はネットワークを越える必要があり、どこまでが越えてくるかは `remoteStreamDetail`(`context.detail` として送信される)によって制御されます:

| レベル | 転送される内容 |
|-------|----------|
| `STATUS` | 実行のライフサイクル、ツール呼び出しの境界、ツール結果、確認リクエスト |
| `FULL`(デフォルト) | `STATUS` に加えて、テキストと thinking のデルタ |
| `VERBOSE` | リモートエージェントが発行するすべてのイベント——ブロックの境界、ツール引数のデルタ、**ツール出力のデルタ**、トークン使用量を伴うモデル呼び出し、ヒント、エージェントの結果、カスタムイベント |

サブエージェントがローカルであってもリモートであっても親のストリームが同じように見えるべき場合は `VERBOSE` を選んでください。これは、リモートのサブエージェントのツール出力内容とトークン使用量が親に届く唯一のレベルです。デフォルトになっていないのは、テキストのみをレンダリングする呼び出し側にとって、追加のイベントが単なる量増加でしかないためです。

専用のワイヤー型を持たないイベントは、元のイベントを `payload` フィールドにシリアライズした `AGENT_EVENT` として運ばれます。そのため親は、ID、タイムスタンプ、メタデータを含め、ローカルで受け取ったのとまったく同じクラスをデコードします。このフィールドより古いクライアントは、引き続きフラットな型ごとのフィールドを読み取るだけで、パススルーイベントを一切目にすることはありません。

### リモート認可

親の DENY パーミッションルールは、リモート送信の `context.deny_rules` に転送されます(ローカルの子と同じ継承であり、`inheritParentPermissions(false)` でオプトアウトできます)。

リモートエージェントがツール確認のために一時停止すると(`awaiting_confirm`):

- **ストリーミングする親 + `remoteAskPolicy=PROPAGATE`**: `RequireUserConfirmEvent` が、null でない `source` タグを付けて親の `streamEvents()` ストリームへ転送されます。リモートタスクを再開するには、Agent Protocol の [`POST /tasks/{id}/resume`](/v2/ja/integration/protocol/agent-protocol) を `decisions[{toolCallId, approved}]` とともに呼び出します。
- **非ストリーミングの親(`call`)、または `remoteAskPolicy=DENY`(デフォルト)**: 保留中の確認は自動的に拒否されます。ツール結果には `remote tool confirmation(s) were auto-denied` という注記が含まれます。

確認待ちの間、タスクのステータスは `RUNNING`(`awaitingConfirm=true`)のままです。そのため `wait_async_results` のようなバリアは、タスクが再開されて終端状態に達するまで待機し続けます。

## バックグラウンドタスクのストレージ

バックグラウンドタスクの状態は、デフォルトで `workspace/agents/<parentAgentId>/tasks/<sessionId>.json` に書き込まれます。そのため:

- 共有ストアモード(マルチレプリカ)では、どのノードもタスク状態を読み取れます。
- タスクの実行は**作成元のノードに固定**されますが、どのノードでも結果を読み取り、親へプッシュバックできます。
- どのノードからでも `task_cancel` によってキャンセルできます——実行中のノードがキャンセルフラグをポーリングし、中断します。

## Plan Mode 中の委譲

親が Plan Mode の場合、spawn されたサブエージェントは**読み取り専用の制約を自動的に継承します**。子は spawn 時に Plan Mode に入るため、書き込み操作を実行できません——安全境界は委譲チェーン全体を通して維持されます。

## サブエージェントのストリーミング

> 新しいコードでは `streamEvents()`(`Flux<AgentEvent>` を返す)を使用してください。従来の `stream()` 系列(`Flux<Event>`)は 2.0.0 以降 `@Deprecated(forRemoval = true)` です —— [Message & Event](/v2/ja/docs/building-blocks/message-and-event) と [V1 移行ガイド B.4](/v2/ja/docs/change-log) を参照してください。

親が `agent_spawn` / `agent_send` 経由で同期的なサブエージェントを呼び出すと、子の中間イベントは親の `streamEvents()` ストリームへ**ライブで転送**されます。各子イベントは `source` フィールド(`"main/researcher"` のような `/` 区切りのパス)を持つため、親のイベント(`source == null`)と子のイベントを区別できます。リモートの Agent Protocol の子はさらに、`metadata.taskId`(`AgentEvent.METADATA_TASK_ID`)を harness のタスク ID に、`metadata.parentSessionId`(`AgentEvent.METADATA_PARENT_SESSION_ID`)を親セッションに設定します。これにより、同じリモートエージェントへの2つの並行/同一ターンの呼び出しが同じ `source` パスを共有していても区別可能になります。

```
caller
  └─ parent.streamEvents(msg, ctx)
        │
        ├─ AGENT_START                            ← 親が開始
        ├─ TEXT_BLOCK_DELTA …                     ← 親の推論
        ├─ TOOL_CALL_START "agent_spawn"
        │
        │  [子が spawn される]
        ├─ AGENT_START          (source="main/researcher")  ← 子が開始
        ├─ TEXT_BLOCK_DELTA …   (source="main/researcher")  ← 子の推論
        ├─ TOOL_CALL_START …    (source="main/researcher")
        ├─ TOOL_RESULT_END …   (source="main/researcher")
        ├─ AGENT_END            (source="main/researcher")  ← 子が完了
        │  [agent_spawn が返る; 子の結果 → 親の TOOL_RESULT]
        │
        ├─ TOOL_RESULT_END                        ← 親がツール結果を受け取る
        ├─ TEXT_BLOCK_DELTA …                     ← 親の2ラウンド目
        └─ AGENT_END                              ← 親が完了
```

### `streamEvents()` を使う(推奨)

```java
parent.streamEvents(new UserMessage(message), ctx)
    .doOnNext(event -> {
        String src = event.getSource();
        String prefix = (src != null) ? "[" + src + "] " : "";

        if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA) {
            System.out.print(prefix + ((TextBlockDeltaEvent) event).getDelta());
        } else if (event.getType() == AgentEventType.TOOL_CALL_START) {
            System.out.println(prefix + "[tool] " + ((ToolCallStartEvent) event).getToolCallName());
        } else if (event.getType() == AgentEventType.AGENT_START) {
            if (src != null) System.out.println("── child started: " + src);
        } else if (event.getType() == AgentEventType.AGENT_END) {
            if (src != null) System.out.println("── child finished: " + src);
        }
    })
    .blockLast();
```

親と子のイベントを区別する:

```java
// 親のイベントのみ
events.filter(e -> e.getSource() == null).subscribe(…);

// 子のイベントのみ
events.filter(e -> e.getSource() != null).subscribe(…);

// 特定の子からのイベント
events.filter(e -> e.getSource() != null && e.getSource().contains("researcher")).subscribe(…);
```

### SSE 転送

```java
@GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> chat(@RequestParam String message,
                                          @RequestParam String sessionId) {
    RuntimeContext ctx = RuntimeContext.builder().sessionId(sessionId).build();
    return agent.streamEvents(new UserMessage(message), ctx)
            .map(event -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("type", event.getType().name());
                payload.put("id",   event.getId());
                if (event.getSource() != null) {
                    payload.put("source", event.getSource());
                }
                if (event instanceof TextBlockDeltaEvent delta) {
                    payload.put("delta", delta.getDelta());
                } else if (event instanceof ToolCallStartEvent start) {
                    payload.put("toolName", start.getToolCallName());
                }
                return ServerSentEvent.<String>builder()
                        .data(objectMapper.writeValueAsString(payload))
                        .build();
            });
}
```

### 振る舞いの境界

| シナリオ | ライブ転送? |
|----------|------------------|
| `streamEvents()` + 同期のローカル子(`timeout_seconds > 0`) | ✔ |
| `call()` モード(非ストリーミング) | ✗(子の結果は `tool_result` 文字列として返る) |
| `timeout_seconds = 0` のバックグラウンドタスク | ✗(結果は逆方向通知によって親の次のラウンドへプッシュされる) |
| リモートサブエージェント(Agent Protocol)+ 親の `streamEvents()` + `remoteStreaming=true`(デフォルト) | ✔ |
| リモートサブエージェント + 親の `call()` または `remoteStreaming=false` | ✗ |

### エラー処理

子が内部でエラーを送出すると、フレームワークはそれを捕捉し、`TOOL_RESULT` を親へ書き戻します。`onError` を親のストリームへ伝播すること**はしません**——子の失敗が親を壊すことはありません。親のストリーム自体がエラーになった場合は、標準的な Reactor のセマンティクス(`onErrorResume` など)を使用してください。

## 関連ページ

- [Channel](/v2/ja/docs/harness/channel) — `expose_to_user`、`SendOptions`、ユーザーからサブエージェントへの直接メッセージング
- [Workspace](/v2/ja/docs/harness/workspace) — `subagents/` と `agents/<id>/tasks/` のレイアウト
- [Plan Mode](/v2/ja/docs/harness/plan-mode) — プランフェーズ中のサブエージェントに対する制約
- [Architecture](/v2/ja/docs/harness/architecture) — 親と子がどのように協調するか
- [Agent Protocol](/v2/ja/integration/protocol/agent-protocol) — リモートタスクのエンドポイント(SSE + HITL の再開)
- [Message & Event](/v2/ja/docs/building-blocks/message-and-event) — `AgentEvent` の階層(推奨)と、非推奨の `Event` / `EventType` / `StreamOptions` 型
- [V1 移行ガイド B.4](/v2/ja/docs/change-log) — `stream()` → `streamEvents()` の非推奨化タイムライン
