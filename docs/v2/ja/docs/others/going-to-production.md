---
title: "本番投入"
description: "シングルノードのプロトタイプからマルチレプリカのデプロイへ: Agent State Store、Filesystem、Skill、Sandbox、Snapshot、Observability のコンポーネント選定と設定"
---

> ノートパソコン上で `HarnessAgent` を動かすのは簡単です。それを本番環境に出荷するのはまったく別の話です — レプリカはセッションを共有しなければならず、ユーザーは分離されたままでなければならず、信頼できないコードはサンドボックス化されなければならず、Pod は再起動後に会話の途中から再開できなければなりません。このページは**シングルノードと分散本番環境の間で変わるもの**だけを扱います: どのコンポーネントを差し替える必要があるか、何に差し替えるか、そしてなぜビルダーが何かを見落とすと `IllegalStateException` をスローするのか。

**本番投入への最速の道**: `DistributedStore` を使ってすべての分散コンポーネントを一度に設定します。

```java
DistributedStore store = RedisDistributedStore.fromJedis(jedis);
// または MysqlDistributedStore.create(dataSource);
// または OssDistributedStore.create(ossClient, bucket, prefix);

HarnessAgent.builder()
    .distributedStore(store)
    .filesystem(...)  // ワークスペースモードを選択
    .build();
```

混在ストア(例: 状態には MySQL、サンドボックスロックには Redis)もサポートされています。

```java
DistributedStore store = DistributedStore.builder()
    .agentStateStore(MysqlDistributedStore.create(ds).agentStateStore())
    .baseStore(MysqlDistributedStore.create(ds).baseStore())
    .sandboxSnapshotSpec(RedisDistributedStore.fromJedis(jedis).sandboxSnapshotSpec())
    .sandboxExecutionGuard(RedisDistributedStore.fromJedis(jedis).sandboxExecutionGuard())
    .build();
```

### 代替案: aistio ホスト型ストア

aistio コントロールプレーンを運用している場合、それは BaseStore / サンドボックスのロック & スナップショット / MessageBus / AsyncToolRegistry / **TaskRepository** / オプションの **SessionTurnGate** をホストできます。それでも **1つ** の `AgentStateStore`(Redis/MySQL/Postgres/OSS)は自分で用意する必要があります。core は `getVersioned` / `saveIfVersion` を提供しますが、ストレージ自体はコントロールプレーンの外に置かれます。

```java
ControlPlaneStores cp = ControlPlaneStores.fromEnv();
HarnessAgent.builder()
    .distributedStore(cp.withAgentStateStore(redis.agentStateStore()))
    .filesystem(new RemoteFilesystemSpec().isolationScope(IsolationScope.USER))
    .build();
```

コントロールプレーンで `--enable-hosted-store` を有効にしてください(本番では Postgres を推奨)。`withAgentStateStore` にはホスト型 TaskRepository が含まれます。**SandboxFilesystem モードでのサブエージェントのバックグラウンドタスク**にはこの経路が必要です。Redis/Postgres/MySQL/InMemory の AgentStateStore バックエンドはバージョニング CAS をサポートし、それ以外は LWW のままです。Turn gate と `ConflictPolicy.FAIL` は、マルチレプリカでの重複ターン削減のためのオプションです。正しさは CAS によって担保されます。認証はリクエストボディ内のテナント情報を伴う共有の内部トークンです — 同一コントロールプレーン上で相互に信頼できないマルチテナントエージェントには適していません。`queueDrain` は破壊的操作です(読み取り即 ack)。詳細は [分散ストレージ — aistio ホスト型ストア](../../integration/distributed/index.md#aistio-ホスト型ストア) を参照してください。

## 一目でわかる: シングルノードのデフォルト vs. 分散本番環境

| 次元 | シングルノードのデフォルト(開発 / デモ) | 分散本番環境での差し替え |
|-----------|----------------------------------|-----------------------------|
| **1行での設定** | 不要 | **`.distributedStore(RedisDistributedStore.fromJedis(jedis))`** |
| `AgentStateStore` | `JsonFileAgentStateStore`(ローカル JSON) | `distributedStore` により自動配線 |
| Filesystem | `LocalFilesystemSpec`(呼び出し不要) | `RemoteFilesystemSpec` または `SandboxFilesystemSpec`(`baseStore` はストアから自動注入) |
| サンドボックスのスナップショット | `NoopSnapshotSpec` / `LocalSnapshotSpec` | `distributedStore` により自動配線 |
| サンドボックス実行のシリアライズ | プロセス内では不要 | `distributedStore` により自動配線 |
| スキルソース | `workspace/skills/` | `GitSkillRepository` / `MysqlSkillRepository` / `NacosSkillRepository` |
| Observability | デフォルトではトレーシングなし | `OtelTracingMiddleware` + OpenTelemetry SDK |

### DistributedStore の機能マトリクス

| 機能 | Redis(`agentscope-extensions-redis`) | OSS(`agentscope-extensions-oss`) | MySQL(`agentscope-extensions-mysql`) |
|------------|:-----:|:---:|:-----:|
| `AgentStateStore` | `RedisAgentStateStore` | `OssAgentStateStore` | `MysqlAgentStateStore` |
| `BaseStore` | `RedisStore` | `OssBaseStore` | `JdbcStore` |
| `SandboxSnapshotSpec` | `RedisSnapshotSpec` | `OssSnapshotSpec` | `JdbcSnapshotSpec` |
| `SandboxExecutionGuard` | `RedisSandboxExecutionGuard` | —(オブジェクトストレージはロックができない) | `JdbcSandboxExecutionGuard` |

各コンポーネントはそれぞれ異なる本番課題を解決します。

- `AgentStateStore`: 会話履歴、コンパクションのサマリー、パーミッションルール、Plan Mode の状態、ツールの状態を含む、エージェントのランタイムセッション状態を永続化します。これにより、別のレプリカや再起動後のプロセスが、同じ `(userId, sessionId)` の続きから処理を継続できます。
- `BaseStore`: `RemoteFilesystemSpec` のための共有 KV ベースのワークスペースストレージを提供し、`MEMORY.md`、`memory/`、`skills/`、`sessions/` などのパスを運びます。マルチレプリカ構成では、異なる Pod が同じ長期記憶と共有ファイルを見られるようにします。
- `SandboxSnapshotSpec`: サンドボックスのワークスペーススナップショットを永続化します。サンドボックスコンテナが破棄されたとき、Pod が再起動したとき、あるいは次のリクエストが新しいノードに着地したときに、以前のワークスペースを復元し、`pip install` の出力、生成されたファイル、一時的なプロジェクト状態が失われないようにします。
- `SandboxExecutionGuard`: 複数ノードにまたがって、同じサンドボックススロットに対するコマンド実行をシリアライズします。`AGENT` や `GLOBAL` のような共有スコープでは、複数のレプリカが同時に同じサンドボックスに対して実行を試みることがあります。このガードは Redis/MySQL のロックを使って、並行するワークスペースの書き込みやサンドボックスの起動/停止の競合を避けます。

> OSS は `SandboxExecutionGuard` を提供しません — オブジェクトストレージは分散ロックに向いていません。サンドボックスの並行性制御が必要な OSS ユーザーは、`DistributedStore.builder()` を介して Redis ガードを混在させることができます。

**主要な検証チェーン:**
- `stateStore(...)` または `distributedStore(...)` なしで `filesystem(RemoteFilesystemSpec)` を使う → `build()` が `IllegalStateException` をスローします。
- ローカルの `AgentStateStore` とともに `filesystem(SandboxFilesystemSpec)` を使う → `build()` が**警告**をログに出力します。本番では常に `distributedStore` を渡してください。

## 1. 状態ストア: まず `AgentState` を永続的などこかに置く

> **推奨**: 1行の設定で済む `distributedStore(...)` を使ってください。以下の詳細な表は、`AgentStateStore` を個別に制御する必要がある上級ユーザー向けです。

`AgentState`(会話コンテキスト、コンパクションのサマリー、パーミッションルール、Plan Mode の状態、ツールの状態)は、[`AgentStateStore`](../../integration/session/index.md) を通じてのみプロセスをまたいで生き残ります。

| 実装 | モジュール | 使いどころ |
|----------------|--------|-------------|
| `InMemoryAgentStateStore` | `agentscope-core` | ユニットテスト向け。プロセス終了ですべて消える |
| `JsonFileAgentStateStore` | `agentscope-core` | シングルマシンでの開発向け。`(userId, sessionId)` ごとに1ディレクトリ。**HarnessAgent のデフォルト**で、`~/.agentscope/state/<agentId>/` を起点とする。**シングルマシン**向け |
| `RedisAgentStateStore` | `agentscope-extensions-redis` | **マルチレプリカ本番のデフォルト**。Jedis / Lettuce / Redisson(Standalone / Cluster / Sentinel)をサポート |
| `MysqlAgentStateStore` | `agentscope-extensions-mysql` | 状態をリレーショナルストアに置く必要がある場合(監査 / レポーティング / join) |

**3種類のいずれのクライアントアダプタでも使える Redis**、`RedisAgentStateStore.builder()` 経由:

```java
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import redis.clients.jedis.JedisPooled;

// Jedis スタンドアロン
AgentStateStore stateStore = RedisAgentStateStore.builder()
        .jedisClient(new JedisPooled("redis://localhost:6379"))
        .keyPrefix("myapp:session:")
        .build();

// Lettuce クラスタ(書き込み負荷が高い場合に適する)
// .lettuceClusterClient(RedisClusterClient.create(...))

// Redisson(すでに他の箇所で Redisson を使っている場合)
// .redissonClient(redisson)
```

**テナントごとの分離。** 素の `sessionId` だけではシングルテナントしかカバーできません。本番では、各呼び出しの `RuntimeContext` に `userId` と `sessionId` の両方を設定し、マルチテナントの呼び出しが相互に読み取れないようにしてください — ストアは `(userId, sessionId)` のペアで各スロットをアドレス指定します(`RedisAgentStateStore` は `userId` を Redis のキーに畳み込み、`MysqlAgentStateStore` はそれを主キーに畳み込みます)。他の次元(テナント、エージェント)は `sessionId` 文字列に自分で組み込んでください。

```java
agent.call(msg, RuntimeContext.builder()
        .userId(tenantId + ":" + userId)
        .sessionId(agentId + ":" + sessionId)
        .build()).block();
```

完全な仕組みは [Context & AgentState](../building-blocks/context.md) を参照してください。

## 2. ファイルシステムモードと `IsolationScope`: 「誰が誰とファイルを共有するか」を決める

3つのモードのおさらい(詳細は [Filesystem](../harness/filesystem.md)):

| モード | 設定 | シェル? | 使いどころ |
|------|--------|--------|-------------|
| **ローカル + シェル** | `filesystem(new LocalFilesystemSpec()...)` または省略 | ✅ ホストの `sh -c` | 単一プロセス / 信頼できる環境 |
| **共有ストア** | `filesystem(new RemoteFilesystemSpec(store))` | ❌(シェルが必要ならサンドボックスを使う) | マルチレプリカ / マルチ Pod での長期記憶の共有 |
| **サンドボックス** | `filesystem(new DockerFilesystemSpec()...)` とその4つの兄弟 | ✅ サンドボックス内部 | 信頼できないコード / 呼び出しをまたいだ復旧 / 強固なユーザー分離 |

**`IsolationScope` はマルチユーザー分離の鍵です。** 共有ストアモードとサンドボックスモードは、どちらも同じスコープを使って名前空間の分け方を決めます。

| スコープ | 意味 | 典型的な用途 |
|-------|---------|-------------|
| `SESSION`(サンドボックスのデフォルト) | sessionId ごとに1スロット | マルチユーザー SaaS。各会話が独立 |
| `USER`(Remote のデフォルト) | 同じ `userId` がセッションをまたいで共有 | 1人のユーザーが複数デバイスで長期記憶を共有 |
| `AGENT` | そのエージェントの全ユーザー/セッションが共有 | 公開ナレッジベースのエージェント |
| `GLOBAL` | すべてを1つの共有スロットに | 注意して使う |

```java
// distributedStore が RemoteFilesystemSpec に baseStore を自動注入する
DistributedStore store = RedisDistributedStore.fromJedis(jedis);

HarnessAgent.builder()
    .distributedStore(store)
    .filesystem(new RemoteFilesystemSpec()
            .isolationScope(IsolationScope.USER)
            .anonymousUserId("_default"))   // userId が存在しない場合のフォールバック
    .build();
```

`anonymousUserId` は本番運用上の細部です — `RuntimeContext.userId` はしばしば null になります(システムタスク、スケジューラのトリガー、管理操作など)。空文字列にフォールバックしないでください。そうしないと、匿名の呼び出し元がすべて1つの共有バケットに集約されてしまいます。

## 3. Remote モードの `BaseStore` ストア: KV の選択 — そして OSS がなぜ不向きなのか

`RemoteFilesystemSpec` は `BaseStore` インタフェースの上に成り立っています。2つの組み込み実装があります。

| 実装 | 依存関係 | 並行性の安全性 | 使いどころ |
|----------------|------------|--------------------|-------------|
| `RedisStore` | `agentscope-extensions-redis` | Lua ベースの CAS `putIfVersion`、プレフィックス検索用の `ZRANGEBYLEX` | デフォルト。マルチレプリカでの共有 |
| `JdbcStore` | `agentscope-extensions-mysql`。MySQL / PostgreSQL / SQLite / H2 の方言を自動検出 | 単一ステートメントの CAS UPDATE | 既存のリレーショナルインフラ / join が必要な場合 |
| `InMemoryStore` | — | — | テスト |

```java
// 推奨: DistributedStore による1行での設定
DistributedStore store = RedisDistributedStore.fromJedis(
        new JedisPooled("redis://prod-redis:6379"));

HarnessAgent agent = HarnessAgent.builder()
        .name("multi-tenant-agent")
        .model(model)
        .workspace(workspace)
        .distributedStore(store)           // stateStore + baseStore を自動配線する
        .filesystem(new RemoteFilesystemSpec() // baseStore はストアから注入される
                .isolationScope(IsolationScope.USER)
                .workspaceIndex(WorkspaceIndex.open(workspace)))  // ls/glob を高速化する
        .build();

// または MySQL の場合:
DistributedStore mysqlStore = MysqlDistributedStore.create(dataSource);
```

### OSS / NAS / S3 についてはどうか?

**OSS に対して `BaseStore` を実装しないでください** — `MEMORY.md` / `memory/YYYY-MM-DD.md` / `agents/<id>/context/<sid>/` は1秒間に何度も書き込まれます。OSS のレイテンシとリクエストごとのコストはすぐに爆発します。正しい役割分担は以下の通りです。

| データの形 | ストア | 所有者 |
|------------|---------|-------|
| 高頻度の小さな KV(メモリ、セッションスナップショット、タスク記録) | Redis / MySQL(`BaseStore`) | `RemoteFilesystemSpec` |
| 大きなオブジェクト(サンドボックスワークスペース全体の tar、数十 MB) | OSS / S3 | `OssSnapshotSpec` / カスタム `RemoteSnapshotSpec` |
| ノードをまたぐ共有ボリューム(複数のサンドボックスインスタンスが同じディレクトリをマウント) | NAS / EFS | `AgentRunFilesystemSpec.nasConfig(...)`(ネイティブにこれをサポートするのは AgentRun のみ) |

### `RemoteFilesystemSpec` のルーティングテーブル

サブシステム間のキー衝突を防ぐため、spec はワークスペースを独立した名前空間セグメントにスライスします。

| ワークスペースパス | 名前空間セグメント |
|----------------|-------------------|
| `AGENTS.md` / `MEMORY.md` / `tools.json` | `root` |
| `memory/` | `memory` |
| `skills/` | `skills` |
| `subagents/` | `subagents` |
| `knowledge/` | `knowledge` |
| `agents/<agentId>/sessions/` | `sessions` |
| `agents/<agentId>/tasks/` | `tasks` |
| 追加: `.addSharedPrefix("prompts/")` | 自動的に導出される |

各セグメントはさらに `IsolationScope` によってバケット化されます(`USER` → `agents/<agentId>/users/<userId>/`)。Redis のキーは最終的に `agentscope:store:item:agents\0X\0users\0alice\0memory\0memory/2026-06-02.md` のようになります。

### `CompositeFilesystem`: 2層読み取り + write-through

`RemoteFilesystemSpec.toFilesystem(...)` は実際には `CompositeFilesystem` を生成します。シェルを持たないベースの `LocalFilesystem`(ローカルテンプレート用のフォールバック)に加え、ルートごとに1つの `OverlayFilesystem`(上層 = `RemoteFilesystem`、下層 = 読み取り専用の `LocalFilesystem` テンプレート)です。

効果: **書き込みは常に Remote へ、読み取りはまず Remote を確認し、なければローカルテンプレートにフォールバックします**。これは [Workspace](../harness/workspace.md) で説明されている「2層読み取りアーキテクチャ」を Remote モード向けにインスタンス化したものです — ローカルの `<workspace>/AGENTS.md` はシード(チーム git で同期される)であり、一度書き込まれると Remote が引き継ぎます。

### `WorkspaceIndex`: オプションの SQLite インデックス

```java
.filesystem(new RemoteFilesystemSpec(store).workspaceIndex(WorkspaceIndex.open(workspace)))
```

Remote モードでの `ls` / `glob` / `exists` / `grep` を高速化します — これがないと、すべての呼び出しが KV 全体をスキャンします。`WorkspaceIndex` はベストエフォートの SQLite ファイル(`<workspace>/.index/` 以下)であり、失敗しても正しさに影響を与えることなく黙って機能を低下させます。

## 4. スキルマーケットプレイス: どの `SkillRepository` を選ぶか

スキルは優先度の低いものから高いものへ合成されます(詳細は [Skill](../harness/skill.md)):

| 層 | ソース | 設定方法 | 使いどころ |
|-------|--------|---------------|------------|
| 1 | プロジェクトグローバル | `.projectGlobalSkillsDir(Path)` | 個人の開発マシン。`~/.agentscope/skills/` |
| 2 | マーケットプレイス | `.skillRepository(...)` | プロジェクト横断での共有 |
| 3 | ワークスペース共有 | `workspace/skills/` | プロジェクト固有。git にチェックイン |
| 4 | ユーザーごと | `<userId>/skills/` | ユーザーレベルのオーバーライド |

### マーケットプレイスストア

| リポジトリ | モジュール | 備考 | 最適な用途 |
|-----------|--------|-------|----------|
| `GitSkillRepository` | `agentscope-extensions-skill-git-repository` | チームの git リポジトリ。HEAD が変わったときのみ pull。読み取り専用の配布 | 初期段階 / 小規模チーム。PR 経由でスキル変更をレビュー |
| `MysqlSkillRepository` | `agentscope-extensions-skill-mysql-repository` | `DataSource` 駆動。`writeable(true/false)` トグル。エージェントが書き戻せる | プラットフォーム側の中央ガバナンス。マルチチーム・マルチエージェント |
| `NacosSkillRepository` | `agentscope-extensions-nacos-skill` | オンライン配布 + 構成センターの変更購読。`AutoCloseable` | Aliyun エコシステム。「一度変更すれば、フリート全体に反映」 |
| `ClasspathSkillRepository` | `agentscope-core` | JAR に同梱。Spring Boot の fat JAR と互換 | 製品に焼き込まれたハードバインドの能力 |

```java
HarnessAgent agent = HarnessAgent.builder()
        // ...
        .skillRepository(new GitSkillRepository("https://github.com/your-org/team-skills.git"))
        .skillRepository(MysqlSkillRepository.builder(dataSource)
                .databaseName("agentscope")
                .skillsTableName("skills")
                .createIfNotExist(true)
                .writeable(false)                  // 読み取り専用の配布。本番環境で推奨
                .build())
        .build();
```

`skillRepository(...)` は加算的です。名前が衝突した場合は後から登録した方が優先されます。

### 本番チェックリスト

- **`MysqlSkillRepository(writeable=false)` または `NacosSkillRepository` を優先してください** — プラットフォーム側の中央ガバナンスで、エージェントは読み取り専用。書き戻しは管理コンソール + レビューフローを経由します。
- エージェントに `workspace/skills/` を見せたくない場合は `.disableDefaultWorkspaceSkills()` を使ってください。
- `enableSkillManageTool` によってエージェントが新しいスキルを起草できるようにする場合は、**必ず** `enableSkillPromotionGate(...)` と組み合わせてください。本番で `autoPromote=true` は決して使わないでください。
- `NacosSkillRepository` は `AutoCloseable` です — Spring の `@PreDestroy` や `try-with-resources` からクローズしてください。さもないと購読がリークします。

## 5. シェルが必要な場合: Sandbox を選び、Snapshot を必須にする

サンドボックスを使わなければならないのは次のような場合です。

- モデルが信頼できないコード(Python / シェル / `npm install` / コンパイル)を実行するかもしれない
- 呼び出しをまたいで**作業ディレクトリ全体**(`node_modules`、生成されたファイル、`pip install` 後の環境)を復元する必要がある
- 強固なユーザー分離が必要(他のユーザーのプロセスを覗けないようにする)

### 5種類のサンドボックスストア

| Spec | モジュール | 使いどころ |
|------|--------|------------|
| `DockerFilesystemSpec` | `io.agentscope.harness.agent.sandbox.impl.docker` | 単一マシン / ローカルクラスタ。イメージからコンテナを起動。最もなじみ深い |
| `KubernetesFilesystemSpec` | `...impl.kubernetes` | すでに K8s を運用中。Pod / Job |
| `DaytonaFilesystemSpec` | `...impl.daytona` | Daytona(dev-env-as-a-service) |
| `E2bFilesystemSpec` | `...impl.e2b` | E2B クラウドサンドボックス。最速で出荷でき、自前で管理するインフラが不要 |
| `AgentRunFilesystemSpec` | `...impl.agentrun` | **Aliyun AgentRun**。ネイティブの NAS / OSS マウント。エンタープライズグレード |

```java
.filesystem(new DockerFilesystemSpec()
        .image("ubuntu:24.04")
        .isolationScope(IsolationScope.SESSION))
```

### スナップショットはサンドボックスの分散ライフライン

サンドボックスはデフォルトで一時的なものです — 次の `call()` は、コンテナが失われた新しいノードに着地するかもしれず、すべての `pip install` と生成ファイルを失う可能性があります。`SandboxSnapshotSpec` はワークスペースを tar としてアーカイブし、次の `call()` がそれを新しいコンテナに再展開します。

| Spec | ストア | モジュール | 使いどころ |
|------|-------|--------|--------------|
| `NoopSnapshotSpec` | — | `agentscope-harness` | 本番向けではない。コンテナが失われるたびにサンドボックスがコールドスタートする |
| `LocalSnapshotSpec(Path)` | ローカルディレクトリの `tar` ファイル | `agentscope-harness` | シングルノードでのデバッグ |
| `OssSnapshotSpec` | Alibaba Cloud OSS | `agentscope-extensions-oss` | **大きなオブジェクトの第一選択**。オブジェクトストレージに自然に適合 |
| `RedisSnapshotSpec` | Redis | `agentscope-extensions-redis` | 小さなワークスペース + 短い TTL(Redis のメモリコストに注意) |
| `JdbcSnapshotSpec` | MySQL / JDBC BLOB | `agentscope-extensions-mysql` | 既存のリレーショナル DB があり、追加ミドルウェアなしで済ませたい場合 |
| カスタム `RemoteSnapshotClient` → `RemoteSnapshotSpec` | S3 / GCS / MinIO | — | 組み込みリストにないもの全般 |

```java
DistributedStore redisStore = RedisDistributedStore.fromJedis(jedis);
DistributedStore ossStore = OssDistributedStore.create(
        ossClient,
        "agentscope-sandbox-snapshots",
        "prod/");                         // 環境分離のためのキープレフィックス

DistributedStore store = DistributedStore.builder()
        .agentStateStore(redisStore.agentStateStore())
        .baseStore(redisStore.baseStore())
        .sandboxSnapshotSpec(ossStore.sandboxSnapshotSpec())
        .sandboxExecutionGuard(redisStore.sandboxExecutionGuard())
        .build();

HarnessAgent agent = HarnessAgent.builder()
        .name("coding-agent")
        .model(model)
        .workspace(workspace)
        .distributedStore(store)
        .filesystem(new DockerFilesystemSpec()
                .image("python:3.12-slim")
                .isolationScope(IsolationScope.USER))
        .build();
```

`distributedStore(...)` を使うと、スナップショット spec と実行ガードは自動的に注入されます — 手動設定は不要です。OSS のバケットやプレフィックスをカスタマイズするには、作成時に `OssDistributedStore` を設定する方法を優先してください。完全にカスタムなスナップショット実装が必要な場合にのみ、`SandboxFilesystemSpec` 上で `SandboxSnapshotSpec` を明示的に設定してください。

### サンドボックス実行のシリアライズ: `SandboxExecutionGuard`

`SESSION` / `USER` スコープでは、バケットはすでにセッション/ユーザーごとに分割されており、並行する `exec` は衝突しません。`AGENT` / `GLOBAL` スコープで複数のレプリカがある場合、N 個のノードが同じサンドボックススロットに対して `exec` を競合させる可能性があります。`distributedStore(...)` は適切な実行ガードを自動的に注入します。

| 実装 | モジュール | 仕組み |
|---------------|--------|-----------|
| `RedisSandboxExecutionGuard` | `agentscope-extensions-redis` | Redis の `SET NX PX` リース |
| `JdbcSandboxExecutionGuard` | `agentscope-extensions-mysql` | MySQL の `GET_LOCK()` / `RELEASE_LOCK()` |

推奨される方法は、やはり `DistributedStore` を介してガードを注入することです。

```java
DistributedStore store = RedisDistributedStore.fromJedis(jedis);

HarnessAgent.builder()
        .distributedStore(store)
        .filesystem(new DockerFilesystemSpec()
                .image("ubuntu:24.04")
                .isolationScope(IsolationScope.GLOBAL))
        .build();
```

リース TTL のようなカスタムロックパラメータが必要な場合にのみ、ガードを明示的にオーバーライドしてください。

```java
HarnessAgent.builder()
        .distributedStore(store)
        .filesystem(new DockerFilesystemSpec()
                .image("ubuntu:24.04")
                .isolationScope(IsolationScope.GLOBAL)
                .executionGuard(RedisSandboxExecutionGuard.builder(jedis)
                        .leaseTtl(Duration.ofMinutes(30))
                        .build()))
        .build();
```

Zookeeper、etcd、その他任意のロック機構を差し込むために、`SandboxExecutionGuard` を自分で実装することもできます。

### ワークスペース投影: シードファイルをサンドボックスに押し込む

`SandboxFilesystemSpec` は、起動時にコンテンツハッシュ化された tar アーカイブ(インクリメンタルな再書き込み)をハイドレートすることで、`AGENTS.md, skills, subagents, knowledge, .skills-cache`(5つのルート)をサンドボックスに投影します。これを調整するには:

```java
.filesystem(new DockerFilesystemSpec()
        .image("...")
        .workspaceProjectionRoots(List.of("AGENTS.md", "skills", "knowledge"))   // subagents/.skills-cache を除外
        // .workspaceProjectionEnabled(false)   // 完全に無効化
)
```

### AgentRun 固有: NAS / OSS マウント

`AgentRunFilesystemSpec` は、(NAS 経由で)**複数のサンドボックスインスタンスが同じディレクトリをマウントする**ことをネイティブにサポートする唯一のサンドボックスファイルシステムです。「1人のユーザーが異なるセッションをまたいで同じワークスペースを見る」というビジネスケースでは、毎回スナップショットを再ハイドレートするより、AgentRun + NAS の方が効率的です。

```java
.filesystem(new AgentRunFilesystemSpec()
        .apiKey(System.getenv("AGENTRUN_API_KEY"))
        .accountId(System.getenv("ALI_ACCOUNT_ID"))
        .region("cn-hangzhou")
        .templateName("python-3.12")
        .nasConfig(new AgentRunNasMountConfig().fileSystemId("...").mountTargetDomain("...").mountDir("/workspace"))
        .addOssMount(new AgentRunOssMountConfig().bucketName("data").mountDir("/mnt/oss")))
```

完全なフィールドは `AgentRunNasMountConfig` / `AgentRunOssMountConfig` のソースを参照してください。

## 6. マルチレプリカデプロイチェックリスト(まとめ)

上記の個別コンポーネントの選定を1つの表にまとめます。

| 関心事 | 推奨の組み合わせ |
|---------|-------------------|
| セッション / `AgentState` | `RedisDistributedStore`、または `AgentStateStore` を注入する混在 `DistributedStore`。`(userId, sessionId)` にテナント/ユーザー/エージェントの次元を持たせる |
| ワークスペースファイル | `distributedStore(...)` が注入する `BaseStore` + `RemoteFilesystemSpec` + `WorkspaceIndex` + `IsolationScope.USER` |
| 大きなオブジェクト / スナップショット | 混在 `DistributedStore` の中で `OssDistributedStore.sandboxSnapshotSpec()` を使う(大きなスナップショットを Redis に書き込まない) |
| ノードをまたぐサンドボックス共有 | AgentRun + NAS マウント、または自前運用の K8s + `distributedStore(...)` が注入する `SandboxExecutionGuard` |
| スキルガバナンス | `MysqlSkillRepository(writeable=false)` または `NacosSkillRepository`。エージェント側の autoPromote を無効化 |
| サブエージェントのタスク記録 | Remote / Sandbox 上の `WorkspaceTaskRepository` による自動処理。追加設定不要 |
| 公開されたサブエージェント(ユーザーがサブエージェントと直接会話する) | `distributedStore` によって自動配線されるレジストリ — `subagentId` が解決され、任意のレプリカ / 再起動後にサブエージェントが復旧する。`subagentId` のメッセージを同じノードに戻す(スティッキー)ようにルーティングし、復旧をフェイルオーバー時のみのパスにする。`GatewayBootstrap` の場合は `.distributedStore(...)` を渡す |
| グレースフルシャットダウン | `GracefulShutdownManager`(JVM フックを自動登録)。SIGTERM を処理する。実行中の待機時間は `setConfig(...)` で調整する |
| Observability | `OtelTracingMiddleware` + OpenTelemetry SDK + OTLP エクスポーター |
| レート制限 | カスタムの `MiddlewareBase`(onModelCall)。[Middleware — レート制限 Middleware](../building-blocks/middleware.md#レート制限-middleware) を参照 |

## 7. 完全な本番用ビルダーテンプレート

エージェントは呼び出しの間はステートレスです — 単一のシングルトンが並行リクエストを処理します。各 `call()` は、`RuntimeContext` の `(userId, sessionId)` を介して状態を特定し、完全に分離されます。

```java
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.extensions.redis.RedisDistributedStore;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import io.agentscope.core.memory.compaction.CompactionConfig;
import io.agentscope.core.memory.compaction.ToolResultEvictionConfig;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import redis.clients.jedis.JedisPooled;

// --- 依存関係(起動時に一度だけ作成)---
Path workspace = Paths.get("/var/agentscope/workspace");
JedisPooled jedis = new JedisPooled(System.getenv("REDIS_URI"));
DistributedStore store = RedisDistributedStore.fromJedis(jedis);

// --- シングルトンのエージェント(起動時に一度だけ作成)---
HarnessAgent agent = HarnessAgent.builder()
        .name("coding-assistant")
        .model("dashscope:qwen-plus")
        .workspace(workspace)
        .distributedStore(store)  // stateStore + snapshotSpec + executionGuard を自動配線する
        .filesystem(new DockerFilesystemSpec()
                .image("python:3.12-slim")
                .isolationScope(IsolationScope.USER))
        .compaction(CompactionConfig.builder()
                .triggerMessages(50)
                .keepMessages(20)
                .build())
        .toolResultEviction(ToolResultEvictionConfig.defaults())
        .skillRepository(io.agentscope.core.skill.repository.mysql.MysqlSkillRepository
                .builder(skillsDataSource())
                .createIfNotExist(false)
                .writeable(false)
                .build())
        .middlewares(List.of(new OtelTracingMiddleware()))
        .build();
```

呼び出し時には、ユーザー/セッションを識別するために `RuntimeContext` を渡します。異なるセッションは同じエージェントインスタンス上で並行して実行されます。

```java
// HTTP ハンドラー内
agent.call(msg, RuntimeContext.builder()
        .userId(httpRequest.tenantUserId())
        .sessionId(httpRequest.sessionId())
        .build()).block();
```

## 8. よくある落とし穴

- **`RuntimeContext` を渡し忘れる** — `sessionId` を渡さないと、すべてのリクエストが `defaultSessionId` の状態を共有し、混線が発生します。マルチユーザーのシナリオでは、**すべての `call()` に対して常に `RuntimeContext.builder().userId(...).sessionId(...).build()` を渡し**、状態の分離を保証してください。[Agent — マルチユーザー同時実行](../building-blocks/agent.md#マルチユーザー--マルチセッション同時実行) を参照してください。
- **ワークスペースの書き込みに `java.nio.Files` を使う** — サンドボックス / Remote モードでは、これは誤った場所に着地します。常に `agent.getWorkspaceManager()` を経由してください。**例外**: ビルダー時のシードファイル(`initWorkspaceIfAbsent` 的なコード)— まだランタイムコンテキストがないため、ローカルテンプレートをシードしているという意味で `java.nio.Files` が正しい選択です。
- **`tools.json` の `allow` フィルタは組み込みツールもフィルタする** — ホワイトリスト化する際は、`read_file` / `memory_search` / `agent_spawn` などをリストに残しておかないと、すべての組み込みツールが剥がされてしまいます。
- **`IsolationScope` の変更は既存データを移行しない** — 起動前に固定してください。起動後に変更することは、新しい名前空間に切り替えることと同等です。
- **ローカル `AgentStateStore` のシングルマシン制約**: 分散ファイルシステムとローカルの `JsonFileAgentStateStore` を組み合わせた K8s マルチレプリカ構成は、最初の `build()` で `IllegalStateException` をスローします。**これは意図的な動作です** — エージェントの状態を1つの Pod のローカルディスクに置くことはできません。
- **`NacosSkillRepository` をクローズしない** — 購読がリークし、フリート規模になると Nacos が不満を訴えます。Spring の `@PreDestroy` または `destroyMethod="close"` を使ってください。
- **IAM なしの OSS / NAS** — `OssSnapshotSpec` はプラットフォームの AK/SK を受け取りますが、RAM Role + STS の一時クレデンシャルの方が堅牢です。
- **サンドボックスモードでのローカル `AgentStateStore` は開発専用です** — ビルド時の警告は意図的なものです。本番でそれを無視しないでください。

## 関連ページ

- [クイックスタート](../quickstart.md) — 最初の `HarnessAgent` をエンドツーエンドで
- [Harness アーキテクチャ](../harness/architecture.md) — 各能力がどのように連携するか
- [Context & AgentState](../building-blocks/context.md) — `AgentState` / `AgentStateStore` / ノードをまたぐ復旧
- [Compaction](../harness/compaction.md) — 会話の要約、ツール結果のエビクション、オーバーフロー時の復旧
- [Workspace](../harness/workspace.md) — ディレクトリレイアウト、2層読み取り、`tools.json`
- [Filesystem](../harness/filesystem.md) — 3つのデプロイモード、`IsolationScope`
- [Sandbox](../harness/sandbox.md) — サンドボックスの詳細、5つの実装、スナップショットの仕組み
- [Skill](../harness/skill.md) — 4層構成、マーケットプレイスストア、自己学習ループ
- [Middleware](../building-blocks/middleware.md) — カスタムの observability / レート制限 / フォールバック middleware
