---
title: Agent Protocol
---

`agentscope-extensions-agent-protocol` は AgentScope の [Harness Agent](/v2/ja/docs/harness/architecture) を標準的な [Agent Protocol](https://agentprotocol.ai/) HTTP API として公開し、外部システム（CI、他のエージェントプラットフォーム、自動化ジョブ）が統一された契約を使って「タスク」を投入できるようにします — 実装の詳細を知る必要はありません。

## いつ使うか

- Agent をクラウド関数のようにリモートからスケジュール実行したい。
- 既存のチームが Agent Protocol クライアントを使っており、そこに直接接続したい。
- Spring Boot サービスに Harness Agent を組み込み、`/tasks` REST エンドポイントを自動公開したい。
- 他の Harness の親から HTTP 経由で呼び出される [リモートサブエージェント](/v2/ja/docs/harness/subagent#リモートサブエージェント) をホストしたい。

## プロトコルのレイヤー構成

AgentScope は、信頼境界や UX の境界ごとに異なるプロトコルを使い分けます。

| レイヤー | 役割 |
| --- | --- |
| **AG-UI** | ユーザー向けチャット UI のイベントストリーム（ブラウザ ↔ アプリ） |
| **Agent Protocol** | 内部のリモートサブエージェント / タスク HTTP API（親 harness ↔ リモートエージェントサービス） |
| **A2A** | 外部のエージェント間相互運用（別の拡張機能であり、このリモートサブエージェントのストリーミング / HITL の作業には含まれない） |

## 依存関係を追加する

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-agent-protocol</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 有効化する

このモジュールは Spring Boot の自動構成として提供されます。Spring Boot アプリでは次のようにします。

1. `HarnessAgent` の Bean（またはカスタムの `AgentFactory`）を用意する。
2. `application.yml` で有効化する。

```yaml
agentscope:
  agent-protocol:
    enabled: true
    # 省略可 — コントロールプレーンの TaskRecord ディレクトリ（実行エージェントのワークスペースではない）
    # task-store-path: ${user.dir}/.agentscope/agent-protocol
```

これで `/tasks` REST エンドポイントが自動的に登録されます。

### コントロールプレーンと実行ワークスペース

`AgentProtocolTaskStore` は、プロトコルのタスクメタデータ（送信 / 再開 / スナップショット用の `TaskRecord`）を専用の `ProtocolTaskRepository` を通じて永続化します。デフォルトでは、このリポジトリは `agentscope.agent-protocol.task-store-path`（`${user.dir}/.agentscope/agent-protocol`）を起点とし、合成バケット `agents/_agentscope_protocol/tasks/` の下に置かれます。

このパスは、各 `HarnessAgent` 自身の `WorkspaceManager`（MEMORY、セッション、スキル）とは**独立**しています。マルチエージェントのファクトリでは、エージェントごとに異なる `.workspace(...)` を与えることがありますが、プロトコルの `task_id` の検索は常にこのコントロールプレーンのリポジトリを通じて行われます。

デフォルトを上書きするために、独自の `ProtocolTaskRepository` Bean を提供することもできます。`AgentProtocolTaskStore` を構築する際は `ProtocolTaskRepository` のみを渡し、実行エージェントの `WorkspaceManager` を渡さないでください。

## 並行実行

エージェントは呼び出し間でステートレスです — 1 つのシングルトンが複数の並行タスクを処理します。各タスクは `RuntimeContext` を介して独自の `(userId, sessionId)` を持つため、状態は完全に分離されます。

```java
@Bean
public HarnessAgent harnessAgent() {
    return HarnessAgent.builder()
            .name("protocol-agent")
            .model("dashscope:qwen-plus")
            .build();
}
```

同一セッションに対する並行リクエストは自動的に直列化され、異なるセッションは並行して実行されます。

## エージェントの選択（`AgentFactory`）

どのエージェントがタスクを実行するかは、`AgentFactory` Bean によって決まります。何も指定しない場合、デフォルトのファクトリはすべてのタスクに対して単一の `HarnessAgent` Bean を返します。

`agent_id`、テナント、あるいは任意のカスタム送信コンテキストキーでルーティングしたい場合は、独自の Bean を定義してください。

```java
@Bean
AgentFactory agentFactory(Map<String, HarnessAgent> agentsByName) {
    return request -> {
        String tenant = request.contextString("tenant");
        log.info("task {} agent_id={} tenant={} resume={}",
                request.taskId(), request.agentId(), tenant, request.resume());
        return agentsByName.getOrDefault(request.agentId(), agentsByName.get("default"));
    };
}
```

`AgentRequest` のフィールド:

| フィールド | 備考 |
| --- | --- |
| `taskId()` | タスク識別子。エージェントのセッション ID でもある |
| `agentId()` | 送信元が要求した `agent_id` |
| `input()` | ユーザー入力。再開実行時は空 |
| `userId()` / `parentSessionId()` | `context.user_id` / `context.parent_session_id` から解析される |
| `resume()` | ツールの確認待ちだったタスクを再実行する場合に `true` |
| `context()` | 送信された `context` マップをカスタムキーも含めそのまま反映したもの。`contextValue(key)` / `contextString(key)` が便利なアクセサとして使える |
| `attributes()` | `context.attributes` マップのみ。`attributeString(key)` が便利なアクセサとして使える |

ファクトリは実行ごとに 1 回、つまり送信時と `/resume` のたびに、元の送信コンテキストとともに呼び出されるため、HITL の一時停止をまたいでもルーティングの判断は安定します。

タスクが並行して実行される場合は、呼び出しごとに別のインスタンス（たとえば prototype スコープの Bean）を返してください。`null` を返すとタスクはエラーステータスで失敗します。

## コンテキスト属性

呼び出し元は `context.attributes` の中に独自のデータを渡します。プロトコル自身のコンテキストフィールドと混ざらないよう、ネストされています。`AgentFactory` から参照できるだけでなく、属性は `RuntimeContext` を通じて実行中のエージェントにも届きます。

これらは、単一の名前空間付きキー `AgentProtocolConstants.RUNTIME_CONTEXT_ATTRIBUTES_KEY`（`agentprotocol.context.attributes`）の下にある**1 つのマップ**として渡されます。

```java
Map<String, Object> attributes = ctx.get(AgentProtocolConstants.RUNTIME_CONTEXT_ATTRIBUTES_KEY);
String tenant = attributes != null ? (String) attributes.get("tenant") : null;
```

属性がトップレベルのキーとしてではなく名前空間に分けて書き込まれるのは、フレームワーク自身がいくつかの素のランタイムコンテキストキーを読み取るためです — `agentId` は非同期ツールのウェイクアップルーティングを制御し、`outboundAddress` はゲートウェイの応答アドレスを運びます。呼び出し元がこれらのいずれかと同じ名前で属性を付けると、エージェントの挙動が意図せず変わってしまいます。属性がシステムプロンプトにレンダリングされることはないため、モデルが見る内容には影響しません。

### 属性を独自のキーへ昇格させる

ツール側が `ctx.get("tenant")` のような素のキーを期待している場合や、属性を型付きの値に変換したい場合は、`RuntimeContextCustomizer` Bean を登録します。`RuntimeContextCustomizer.flatten` は明示的な許可リストをコピーし、フレームワークが予約している名前は黙ってスキップします。

```java
@Bean
RuntimeContextCustomizer promoteTenantKeys() {
    return RuntimeContextCustomizer.flatten("tenant", "ticket_id");
}

@Bean
RuntimeContextCustomizer tenantContext(TenantService tenants) {
    return (request, builder) -> {
        String tenant = request.attributeString("tenant");
        if (tenant != null) {
            builder.put(TenantInfo.class, tenants.load(tenant));
        }
    };
}
```

すべてのカスタマイザ Bean は、名前空間付きの注入の後、`@Order` の順序ですべての実行に適用されます — 後のカスタマイザが前のものを上書きします。手書きのカスタマイザは信頼されており、予約済みのキーを含め任意のキーに書き込むことができます。

### 親エージェントから属性を送る

リモートサブエージェントに委譲する親エージェントは、2 つの方法で属性を渡すことができ、呼び出しごとの値がマージ時に優先されます。

```java
// 静的、サブエージェントごと
SubagentDeclaration.builder()
        .name("researcher")
        .description("Remote researcher")
        .url("http://remote:8080")
        .remoteContextAttributes(Map.of("region", "cn"))
        .build();

// 呼び出しごと、親の RuntimeContext 上
RuntimeContext.builder()
        .sessionId("sess-1")
        .put(AgentSpawnTool.CTX_REMOTE_CONTEXT_ATTRIBUTES, Map.of("tenant", "acme"))
        .build();
```

値は JSON にシリアライズ可能である必要があります。

## エンドポイント

### タスクを送信する

`POST /tasks`

```json
{
  "task_id": "task_123",
  "agent_id": "researcher",
  "input": "最新のリリースノートを要約してください",
  "context": {
    "user_id": "u-1",
    "parent_session_id": "sess-parent",
    "stream": true,
    "detail": "full",
    "deny_rules": [
      {
        "tool_name": "bash",
        "behavior": "DENY",
        "source": "parent"
      }
    ],
    "attributes": {
      "tenant": "acme",
      "ticket_id": "INC-1001"
    }
  }
}
```

任意指定の `context` フィールド:

| フィールド | 備考 |
| --- | --- |
| `user_id` | リモートエージェントの `RuntimeContext` に転送される |
| `parent_session_id` | 親セッションの識別子（トレース / 相関のため） |
| `stream` | 呼び出し元が SSE イベントを受け取るつもりかどうか |
| `detail` | `status`（デフォルト）、`full`、または `verbose` — [ストリームの詳細レベル](#ストリームの詳細レベル) を参照 |
| `deny_rules` | リモート側で適用する親の DENY 権限ルール |
| `attributes` | ルーティングおよび実行の `RuntimeContext` 用に呼び出し元が定義するキー/値。[コンテキスト属性](#コンテキスト属性) を参照 |

成功時のレスポンス: `{ "task_id", "status": "pending" }`。

### ポーリング / 待機 / キャンセル

| メソッド | パス | 備考 |
| --- | --- | --- |
| `GET` | `/tasks/{taskId}` | スナップショット（`status`、結果、エラー、確認待ちの内容） |
| `GET` | `/tasks/{taskId}/wait?timeout_seconds=7200` | 終了状態（または `awaiting_confirm`）までブロックする |
| `POST` | `/tasks/{taskId}/cancel` | キャンセルを要求する |

リモートエージェントがツールの確認を待っている間、スナップショットは `status: awaiting_confirm` を報告しますが、保存されている `TaskStatus` は `RUNNING` のままなので、親側のバリアは待機を続けます。

### イベントをストリーミングする（SSE）

`GET /tasks/{taskId}/events`

エージェントの進行状況の Server-Sent Events です。`agentscope.agent-protocol.streaming-enabled=true`（デフォルト）が必要です。

再接続 / 再開:

- クエリパラメータ `from_seq` — このシーケンス番号の後から開始する
- ヘッダー `Last-Event-ID` — `from_seq` が省略された場合に使われる（意味は同じ）

各 SSE メッセージは、イベントのシーケンス番号を `id` に、リモートのイベントタイプを `event` に、JSON ボディを `data` に使います。

#### ストリームの詳細レベル

送信時の `context.detail` によって、購読者にどこまでの実行内容が届くかが決まります。各レベルは前のレベルのスーパーセットです。

| `detail` | ストリーム上のイベントタイプ |
|----------|---------------------------|
| `status`（デフォルト） | `RUN_STARTED`、`RUN_FINISHED`、`RUN_ERROR`、`TOOL_CALL_START`、`TOOL_CALL_END`、`TOOL_RESULT`、`REQUIRE_CONFIRM`、`STATUS` |
| `full` | 上記に加えて `TEXT_DELTA`、`THINKING_DELTA` |
| `verbose` | 上記に加えて `AGENT_EVENT` — ブロック境界、ツール引数やツール出力のデルタ、トークン使用量付きのモデル呼び出し、エージェントの結果、カスタムイベントを含む、残りすべてのエージェントイベント |

エージェント自身のイベントストリームを完全に再現するのは `verbose` だけです。認識できない値は `status` として扱われます。

どのイベントボディにも、型固有のフィールドに加えて次の 2 つのフィールドが含まれます。

| フィールド | 意味 |
|-------|---------|
| `eventType` | 元となった `AgentEventType` の名前（例: `MODEL_CALL_END`）。クライアントが `payload` を解析せずにフィルタやログ出力を行える |
| `payload` | 元の `AgentEvent` をそのままシリアライズしたもの。id、タイムスタンプ、メタデータを保持したまま元のイベントを復元でき、`AGENT_EVENT` の唯一の表現でもある |

どちらも追加的なものです。これらを無視するクライアントは、これまでどおりフラットなフィールド（`text`、`toolCallId`、`status` など）を読み続けられますし、`AGENT_EVENT` より前のクライアントはそれらのメッセージを単にスキップします。

### HITL 後の再開

`POST /tasks/{taskId}/resume`

```json
{
  "decisions": [
    { "toolCallId": "call-1", "approved": true },
    { "toolCallId": "call-2", "approved": false }
  ]
}
```

`tool_call_id` は `toolCallId` のエイリアスとしても受け付けられます。`agentscope.agent-protocol.hitl-enabled=true`（デフォルト）が必要です。成功時は `{ "task_id", "status": "running" }` を返します。

リモート HITL が呼び出し元の親 harness とどのように連携するかは、[リモート認可](/v2/ja/docs/harness/subagent#リモート認可) に記載されています。

## 設定

| プロパティ | 型 | デフォルト | 備考 |
| --- | --- | --- | --- |
| `agentscope.agent-protocol.enabled` | boolean | `false` | `/tasks` REST エンドポイントを登録するかどうか |
| `agentscope.agent-protocol.streaming-enabled` | boolean | `true` | SSE `GET /tasks/{id}/events` を公開する |
| `agentscope.agent-protocol.hitl-enabled` | boolean | `true` | ツール確認のためにタスクを一時停止し、`/resume` を受け付ける |
| `agentscope.agent-protocol.sse-replay-buffer-size` | int | `256` | 遅れて購読した SSE クライアント向けのタスクごとのリプレイバッファ |
| `agentscope.agent-protocol.sse-timeout-ms` | long | `10800000` | SSE 購読の最大継続時間（ms） |

例:

```yaml
agentscope:
  agent-protocol:
    enabled: true
    streaming-enabled: true
    hitl-enabled: true
    sse-replay-buffer-size: 256
    sse-timeout-ms: 10800000
```

> `enabled` が `false`（デフォルト）の場合、この依存関係は無効な状態のままです — REST エンドポイントは何も公開されないため、安全に出荷できます。

## ワークスペース統合

各タスクは `WorkspaceManager` から分離されたワークスペースを受け取ります。タスクが完了すると、ワークスペース内のファイルとログは標準の Agent Protocol エンドポイント経由で公開され、外部クライアントが成果物を取得できるようになります。
