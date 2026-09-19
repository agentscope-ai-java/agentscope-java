---
title: コンテキストと AgentState
description: ステートレスなエージェントエンジン、AgentState のライフサイクル、状態の永続化、RuntimeContext
---

## ステートレスなエージェントエンジン

`ReActAgent`(およびそれをラップする `HarnessAgent`)は**ステートレスなエンジン**として設計されています。エージェントのインスタンス自体は、システムプロンプト、モデル、ツール、middleware チェーンといった不変な設定のみを保持し、セッションごとに変化するすべてのデータは `(userId, sessionId)` によってインデックスされた `AgentState` に置かれます。単一のエージェントインスタンスで多数のユーザーとセッションを同時に処理でき、呼び出し側は各 `call()` に異なる `RuntimeContext` を渡すだけです。

```
┌──────────────────────────────────────────────────────────────────┐
│                     HarnessAgent (singleton)                     │
│  Immutable config: sysPrompt, model, toolkit, middlewares        │
│                                                                  │
│  ┌─ state cache ─────────────────────────────────────────────┐   │
│  │  ("alice","s1") → AgentState  ← call(…, RC(alice,s1))       │
│  │  ("bob","s2")   → AgentState  ← call(…, RC(bob,s2))        │
│  └───────────────────────────────────────────────────────────┘   │
│                                                                  │
│  per-session gate: same (uid,sid) calls serialised, others ∥     │
└──────────────────────────────────────────────────────────────────┘
```

### これが意味すること

- **ユーザーごとにエージェントを用意する必要はありません。** 1つの `HarnessAgent` インスタンスで全ユーザーに対応できます——リクエストごとに `RuntimeContext.userId` と `RuntimeContext.sessionId` を変えるだけです。
- **並行処理は組み込みです。** 異なる `(userId, sessionId)` のペアは完全に並行して実行され、同一のペアは会話の一貫性を保つために自動的に直列化されます。
- **状態は完全に内部で管理されます。** エージェントは呼び出しの開始時にストアから `AgentState` をロードし、終了時に保存します——呼び出し側が状態オブジェクトを直接扱うことはありません。
- **呼び出しごとの分離。** 各 `call()` は自身の `AgentState` スナップショット上で動作します。Middleware とツールは、呼び出しの開始時にフレームワークが注入する `RuntimeContext.getAgentState()` を通じて、呼び出しにスコープされた状態にアクセスします。そのため、並行する呼び出しが互いの状態を見ることはありません。

---

## AgentState

[`AgentStateStore`](/v2/ja/integration/session/index) は**`AgentState`**(`io.agentscope.core.state.AgentState`)——エージェントを再起動可能にするために必要なすべての完全なスナップショット——を永続化します。

| `AgentState` のフィールド | 内容 |
|---|---|
| `getSessionId()` | この状態が属するセッション識別子 |
| `getUserId()` | ユーザー識別子(匿名セッションでは null になり得る) |
| `getContext()` / `contextMutable()` | 現在の会話履歴(ユーザー / アシスタント / ツール呼び出し / ツール結果) |
| `getSummary()` | 圧縮された要約(コンパクションが有効な場合) |
| `getPermissionContext()` | ツールのパーミッションルール —— [Permissions](/v2/ja/docs/building-blocks/permission-system) を参照 |
| `getPlanModeContext()` | Plan Mode が有効かどうか、現在のプランファイルのパス |
| `getTasksContext()` | `todo_write` のタスクリスト |
| `getToolContext()` | アクティブな Toolkit のグループ(`activatedGroups`) |

`AgentState` はまた、セッションごとの中断シグナリングのための一過性かつシリアライズされない `InterruptControl` も保持しています —— 詳しくは下記の [セッション単位の中断](#セッション単位の中断) を参照してください。

各 `call()` の終了時、フレームワークは呼び出しの `(userId, sessionId)` によってアドレスされる `agent_state` というキーの下に、`AgentState` 全体を状態ストアへ書き込みます。同じ `(userId, sessionId)` での次の `call()` は、それを自動的にロードし直します。**状態ストアが分散型(例えば Redis)であれば、異なるプロセス上の——さらには物理的に異なるマシン上の——エージェントインスタンスが、同一の状態を参照できます。**

### 自動永続化と復元のフロー

```
call(msgs, RuntimeContext(userId, sessionId))
  │
  ├─ per-session gate: serialise same (uid, sid), others run in parallel
  │
  ▼
  load AgentState from cache or stateStore
  │   inject onto RuntimeContext: rc.setAgentState(state)
  │
  ▼
  reasoning loop
  │   middlewares mutate state.contextMutable()
  │   (compaction, Plan, todo_write, permissions, …)
  │
  ▼
  save AgentState
  │   stateStore.save(userId, sessionId, "agent_state", state)
  │
  ▼
  return result
```

この配線は `ReActAgent` 自身に組み込まれており、`HarnessAgent` はそれをそのまま継承します。エージェントのインスタンスは固定のセッションを保持しません——各呼び出しは、その `RuntimeContext` によって指定されたスロットを読み書きします(指定がなければビルダー設定時の `defaultSessionId` にフォールバック)。

> `call()` の途中での状態変更は、メモリ上の `AgentState` に対して行われます。**状態ストアへの書き込みは呼び出しごとに1回(およびシャットダウン時)のみで、メッセージごとではありません**——そのため、ストアへのスループット負荷は低く保たれます。

### 組み込みおよび拡張の実装

`io.agentscope.core.state.AgentStateStore` を実装するものであれば何でも動作します。デプロイ形態に応じて選んでください。

| 実装 | モジュール | ユースケース |
|---|---|---|
| `InMemoryAgentStateStore` | `agentscope-core` | 単体テスト / シングルプロセスのデモ。終了時に失われる |
| `JsonFileAgentStateStore` | `agentscope-core` | ファイル永続化を伴うローカル開発。ノードをまたがない。**`HarnessAgent` のデフォルト**で、`~/.agentscope/state/<agentId>/` を起点とする(ベースは `agentscope.state.home` システムプロパティで上書き可能)。**単一ホスト向け** |
| `RedisAgentStateStore` | `agentscope-extensions-redis` | マルチレプリカデプロイにおける**本番デフォルト**。Jedis / Lettuce / Redisson(Standalone / Cluster / Sentinel)をサポート |
| `MysqlAgentStateStore` | `agentscope-extensions-mysql` | 状態をリレーショナルストアへ流し込む必要がある場合(監査、レポーティング) |

切り替えはビルダー時の1回の呼び出しで済みます。

```java
// デフォルト(単一ホスト)—— .stateStore(...) を省略すると、ローカルの JsonFileAgentStateStore が自動的に使われる
HarnessAgent agent = HarnessAgent.builder()
    .name("MyAgent")
    .model(model)
    .workspace(workspace)
    .build();

// 本番のマルチレプリカ —— DistributedStore を使う
JedisPooled jedis = new JedisPooled("redis://redis.prod:6379");
HarnessAgent agent = HarnessAgent.builder()
        .name("MyAgent")
        .model(model)
        .workspace(workspace)
        .stateStore(new RedisAgentStateStore(jedis))
        .distributedStore(RedisDistributedStore.fromJedis(jedis))
        .build();
```

<Warning>

組み込みの `JsonFileAgentStateStore` / `InMemoryAgentStateStore` は単一ホストでのみ動作します。すでに `filesystem(SandboxFilesystemSpec)` または `filesystem(RemoteFilesystemSpec)`(分散ワークスペース)を選択している場合、HarnessAgent はビルド時にローカルの状態ストアを `IllegalStateException` で**拒否します**——サンドボックスの状態はレプリカ間で共有されなければなりません。`.distributedStore(...)`(例えば `RedisDistributedStore`)または `.stateStore(...)` で分散ストアを設定してください。

</Warning>

### プロセスとマシンをまたいだリアルタイムの再開

状態ストアが分散型(例えば Redis)であれば、マシンをまたいだ再開は**自動的に**行われます。

```java
// ノード A —— 会話を開始する
HarnessAgent agentA = HarnessAgent.builder()
    .stateStore(redisStore)
    /* ... */ .build();
agentA.call(msg, RuntimeContext.builder()
    .sessionId("alice-2026-06-02-001")
    .userId("alice")
    .build()).block();

// ノード B —— 物理的に異なるマシン、別の JVM
HarnessAgent agentB = HarnessAgent.builder()
    .stateStore(redisStore)
    /* 同じ状態ストア */ .build();

// 同じ (userId, sessionId) を使うノード B の最初の call() は、ノード A が Redis に残した AgentState をロードする
agentB.call(nextMsg, RuntimeContext.builder()
    .sessionId("alice-2026-06-02-001")
    .userId("alice")
    .build()).block();
```

これにより次が得られます。

- **フェイルオーバー**: ノードがクラッシュしても——会話は健全なノードへ引き継がれ、ユーザーは何も気づきません。
- **ローリングデプロイ**: 古い Pod はシャットダウン時に保存し、新しい Pod は最初の呼び出しでロードします——**リリースをまたいでも会話は途切れません**。
- **サーフェスをまたいだ継続性**: ユーザーが Web UI で始め、CLI に切り替えても——同じ `(userId, sessionId)` であれば、すべての記憶が残っています。

`(userId, sessionId)` のペアが名前空間を定義します。ほとんどの場合 `sessionId` だけで十分で、ユーザー単位のパーティショニングが必要なときに `userId` を追加します。

### マルチユーザーの分離

`sessionId` と `userId` は異なる問題を解決します。

- **`sessionId`** —— これがどの会話であるか。独立した `AgentState` のスナップショット。
- **`userId`** —— この会話をどのユーザーが所有しているか。どのユーザーの名前空間にファイルが配置されるかも左右します。[Filesystem](/v2/ja/docs/harness/filesystem) を参照。

```java
agent.call(msg, RuntimeContext.builder()
    .sessionId("alice-1").userId("alice").build()).block();

agent.call(msg, RuntimeContext.builder()
    .sessionId("bob-1").userId("bob").build()).block();
```

2人のユーザー——別々の状態、別々のファイルシステムパス、混線なし。本番環境で `AgentState` レベルのユーザー分離を行うには、`RuntimeContext` に `userId` を設定してください。ファイルシステムパスのバケット分けに頼るのではなく、ストアが `(userId, sessionId)` によって各スロットにアドレスします(`RedisAgentStateStore` では `userId` が Redis キーの一部になります)。

### `AgentState` を直接読み書きする

エージェントのループを迂回する必要がある場合(管理コンソール、監査、バッチ移行など):

```java
import io.agentscope.core.state.AgentState;

AgentState state = agent.getAgentState("alice", "session-001");
System.out.println("messages: " + state.getContext().size());

String json = state.toJson();
AgentState restored = AgentState.fromJsonString(json);
```

| メソッド | 説明 |
|------|------|
| `getContext()` | 現在の会話履歴(不変ビュー) |
| `contextMutable()` | 書き込み可能なビュー。取り扱いに注意 |
| `setSummary(...)` / `getSummary()` | カスタムのコンパクション要約(自前のコンパクション middleware 向け) |
| `toJson()` / `fromJsonString(String)` | シリアライズ / デシリアライズ |

### セッションの会話コンテキストをクリアする

新しいセッションを作らずにユーザーに新しい話題を始めさせるには、`clearContext` を呼びます。これは
同じ `(userId, sessionId)` を維持しつつ、パーミッション、ツール、タスク、Plan Mode といった会話以外の状態は
保持します。モデルに見えるメッセージバッファとコンパクションの要約をクリアした上で、エージェントに
`AgentStateStore` があればその結果を直ちに永続化します。

```java
agent.clearContext("alice", "session-001");

// あるいは、call で使うのと同じ RuntimeContext を使ってもよい。
agent.clearContext(RuntimeContext.builder()
    .userId("alice")
    .sessionId("session-001")
    .build());
```

これは、そのセッションの現在のリクエストが完了した後に呼び出してください。実行中の呼び出しをキャンセルするものではなく、
次の呼び出しはクリアされた会話コンテキストから開始されます。

<Note>

1.0 の `Memory` インターフェース(`InMemoryMemory` / `LongTermMemory` など)は、2.0 では `@Deprecated(forRemoval = true)` です。新しいコードでは `AgentState.getContext()` と `AgentStateStore` を使ってください。`Memory` はソース互換のシムとしてのみ残されています。

</Note>

### セッション単位の中断

各 `AgentState` は、一過性の `InterruptControl`(`io.agentscope.core.interruption.InterruptControl`)——状態ストアに**決してシリアライズされない**(`AgentState` 上で `@JsonIgnore transient` とマークされる)セッション単位の中断シグナル——を保持しています。これにより、同じエージェントインスタンス上の他の並行呼び出しに影響を与えることなく、単一セッションの実行中の呼び出しをピンポイントで中断できます。

```java
// 特定のセッションを中断する —— そのセッションの呼び出しだけがシグナルを観測する
agent.interrupt("alice", "session-001");

// 注入するユーザーメッセージ付きで中断する
agent.interrupt("alice", "session-001", Msg.userMsg("Please stop and summarise."));
```

推論ループは、各イテレーションの前に `state.interruptControl().isInterrupted()` をチェックします。トリガーされると、ループは `handleInterrupt` パスに入り、状態を保存して部分的な結果を返します。

レガシーな引数なしの `interrupt()` は、シングルセッションのシナリオでは引き続き動作します——現在アクティブなセッションの `InterruptControl` にルーティングされます。

<Note>

`InterruptControl` はランタイム限定のシグナルであり、決して永続化されません。フェイルオーバー後に別のノードでセッションが再開された場合、中断フラグはクリアされた状態から始まります。別に用意された `AgentState.shutdownInterrupted` フラグ(こちらは**永続化されます**)は、そのセッションがグレースフルシャットダウンによって中断されたかどうかを記録します——エージェントは次回のロード時にこれを検出して復旧できます。

</Note>

### 並行利用

エージェントはステートレスなエンジンであるため、単一のインスタンスが自然に並行リクエストを処理します。

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("SharedAssistant")
    .model(model)
    .workspace(workspace)
    .stateStore(redisStore)
    .build();

// 異なるユーザー —— 完全に並行、競合なし
Mono<Msg> aliceCall = agent.call(aliceMsg, RuntimeContext.builder()
    .userId("alice").sessionId("s1").build());
Mono<Msg> bobCall = agent.call(bobMsg, RuntimeContext.builder()
    .userId("bob").sessionId("s2").build());

Mono.zip(aliceCall, bobCall).block();  // 両方が並行して実行される

// 同じユーザー、同じセッション —— 自動的に直列化される
Mono<Msg> call1 = agent.call(msg1, RuntimeContext.builder()
    .userId("alice").sessionId("s1").build());
Mono<Msg> call2 = agent.call(msg2, RuntimeContext.builder()
    .userId("alice").sessionId("s1").build());

// call2 は call1 の後ろに並ぶ —— 会話履歴の一貫性が保たれる
Flux.merge(call1, call2).collectList().block();
```

**並行処理のルール:**
- **異なる `(userId, sessionId)`** → 完全に並行。各呼び出しは自身の `AgentState` 上で動作する。
- **同じ `(userId, sessionId)`** → セッション単位の非同期ゲートが FIFO 順に呼び出しを直列化する——外部ロックなしに状態の一貫性が保証される。
- **`interrupt(userId, sessionId)`** → 正確に1つのセッションを対象とし、他の実行中の呼び出しには影響しない。

<Tip>

メモリ上の状態キャッシュは、単一のエージェントインスタンスが処理してきた個別セッションの数に応じて増加します。ほとんどのデプロイ(数百セッション程度)ではこれは無視できます。非常に大規模なシナリオ(プロセスあたり数百万セッション)では、有限のインスタンスプールを持つエージェントファクトリのパターンを検討してください——ただし `AgentState` オブジェクトは軽量なので、これが必要になることはめったにありません。

</Tip>

---

## `RuntimeContext` —— 呼び出しごとのメタデータ

`RuntimeContext`(`io.agentscope.core.agent` 内)は、`agent.call(msgs, ctx)` に渡される軽量な呼び出しごとのキャリアです。hook とツールは、1回の呼び出しの間これを共有します。その自由形式 / 型付きの属性は**永続化されません**。`sessionId` / `userId` フィールドは、この呼び出しに対して状態ストアがどの `AgentState` スロットをロード/保存するかを選択します。呼び出しの開始時、フレームワークは呼び出しにスコープされた `AgentState` を `RuntimeContext` に注入するため、middleware、ツール、hook は `ctx.getAgentState()` を通じて正しい呼び出しごとの状態にアクセスできます。

```java
import io.agentscope.core.agent.RuntimeContext;

RuntimeContext ctx = RuntimeContext.builder()
        .userId("alice")
        .sessionId("s-001")
        .put("request_id", "req-2026-06-01-abc")
        .put(MyTenantInfo.class, new MyTenantInfo("tenant-7"))
        .build();

Msg result = agent.call(List.of(new UserMessage("Hi")), ctx).block();
```

利用できるアクセサ:

| メソッド | 説明 |
|------|------|
| `getSessionId()` / `getUserId()` | 状態スロットとテナントをルーティングするための組み込みフィールド |
| `getAgentState()` / `setAgentState(AgentState)` | 呼び出しの開始時にフレームワークが注入する、呼び出しにスコープされた `AgentState`。Middleware とツールは `agent.getAgentState()` ではなく、ここから状態を読み取るべき |
| `resolveAgentState(ctx, agent)` | 静的ヘルパー: `ctx.getAgentState()` が利用可能ならそれを返し、そうでなければ `agent.getAgentState()` にフォールバックする。並行処理の安全性のため、middleware / ツールではこれを使う |
| `get(String)` / `put(String, Object)` | 文字列キーによる get/put |
| `get(Class<T>)` / `put(Class<T>, T)` | 型付きシングルトンの get/put |
| `getExtra()` | 文字列属性マップへの直接アクセス(可変ビュー) |
| `RuntimeContext.empty()` | 空のコンテキスト |

<Tip>

**`AgentStateStore` はビルダー時にバインドされ、`RuntimeContext` によって呼び出しごとに切り替えることはできません。** 呼び出しごとに変わるのは、それがアドレスする `(userId, sessionId)` スロットです——ユーザー単位の分離には `userId`(あるいはストア上のカスタム `keyPrefix`)を設定してください。呼び出しごとに異なる状態ストアのインスタンスを渡そうとしないでください。

</Tip>

<Tip>

**Middleware とツールから `AgentState` にアクセスする:** 呼び出しの実行中は、`agent.getAgentState()` ではなく常に `RuntimeContext.resolveAgentState(ctx, agent)` を使ってください。並行実行下では、`agent.getAgentState()` は最後にアクティブだったセッションの状態を返します(複数の呼び出しが実行中の場合は任意の選択になります)が、`ctx.getAgentState()` は**この呼び出し**のセッションの状態を返します——これがほとんどの場合に求めているものです。

</Tip>

---

## 関連ページ

- [エージェント](/v2/ja/docs/building-blocks/agent) —— 完全な `ReActAgent` API とビルダーのフィールド
- [コンテキストのコンパクション](/v2/ja/docs/harness/compaction) —— 会話の要約、ツール結果の退避、オーバーフロー時の復旧(ここで説明した AgentState の基盤の上に構築される)
- [メモリ](/v2/ja/docs/harness/memory) —— 長期記憶、バックグラウンドでのメンテナンス
- [パーミッション](/v2/ja/docs/building-blocks/permission-system) —— パーミッションルールの永続化
