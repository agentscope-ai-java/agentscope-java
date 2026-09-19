---
title: Filesystem
description: "3つのデプロイモード:ローカル+シェル / 共有ストア / サンドボックス;IsolationScope の各次元;マルチユーザー分離;各モードでのスキルとツールの振る舞い"
---

## 役割

`HarnessAgent` は、エージェントから見たワークスペースを「必ずローカルディスクでなければならない」という制約から切り離し、統一されたインターフェースにします。すべてのファイルツール(`read_file` / `write_file` / `edit_file` / `grep_files` / `glob_files` / `list_files`)と任意の `execute`(シェル)はこの抽象化を経由します。

これによって得られるもの:**エージェントのコードを変更せずに**3つのデプロイモードを切り替えられます。

- ローカル+シェル — 単一プロセス、ローカル、信頼できる環境;
- 共有ストア — 複数のレプリカ/ポッドが同じ長期記憶を共有する;
- サンドボックス — ファイルとコマンドが分離されたコンテナ内で実行され、同じワークスペース状態が呼び出しをまたいで復元される。

## 3つの宣言的モード

`HarnessAgent.Builder` の `filesystem(...)` で1つを選びます(呼び出しがなければデフォルトでモード3)。

| モード | 設定 | シェル? | いつ使うか |
|------|--------|--------|-------------|
| **1・共有ストア** | `filesystem(new RemoteFilesystemSpec(store))` | なし | 複数のレプリカが `MEMORY.md` / 会話ログ / サブタスク記録を KV 経由で共有する;**ホスト上にシェルなし** |
| **2・サンドボックス** | `filesystem(new DockerFilesystemSpec()...)`、または K8s / Daytona / E2B / AgentRun | あり(サンドボックス内) | 分離実行、呼び出しをまたぐワークスペース復旧、任意のスナップショット+分散 |
| **3・ローカル+シェル**(デフォルト) | `filesystem(new LocalFilesystemSpec()...)` または**省略** | あり(ホストの `sh -c`) | 単一プロセス / ローカル / 信頼できる環境 / スクリプトとテスト |

> `filesystem(...)` は `abstractFilesystem(...)` と排他的です。後者は完全に自前管理のファイルシステム向けの脱出口であり、めったに必要になりません。

---

### モード1:共有ストア(`RemoteFilesystemSpec`)

「複数レプリカだが、ユーザーの長期記憶は同期を保たなければならない」ための構成です。`BaseStore` の実装(Redis / JDBC / インメモリ)を渡すと、フレームワークがワークスペースのファイルをパスの接頭辞に基づいて自動的に KV ストアへルーティングします。

```java
// minimal config (recommended: use DistributedStore for one-line setup)
DistributedStore store = RedisDistributedStore.fromJedis(jedis);

HarnessAgent agent = HarnessAgent.builder()
    .name("store-agent")
    .model(model)
    .workspace(workspace)
    .distributedStore(store)
    .filesystem(new RemoteFilesystemSpec()   // baseStore auto-injected from store
        .isolationScope(IsolationScope.USER))
    .build();
```

#### 設定オプションの全一覧

| メソッド | 説明 | デフォルト |
|--------|-------------|---------|
| `isolationScope(IsolationScope)` | 名前空間の分離次元(下記の [IsolationScope](#isolationscope--ユーザーとレプリカ間のバケット分け) を参照) | `USER` |
| `anonymousUserId(String)` | `userId` が無い場合のフォールバック識別子 | `"_default"` |
| `addSharedPrefix(String)` | 追加のワークスペース相対プレフィックスを KV へルーティングする(例:`"prompts/"`、`"configs/"`) | なし |
| `workspaceIndex(WorkspaceIndex)` | リモートの ls/glob/grep を高速化する SQLite インデックス | なし(フォールバックとしてストア全体をスキャン) |

#### 組み込みのルーティングルール

フレームワークは以下のパスを自動的に共有 KV へルーティングし、キーの衝突を防ぐためにそれぞれ独自の名前空間セグメントに入れます。

| パス | KV 名前空間セグメント |
|------|---------------------|
| `AGENTS.md`、`MEMORY.md`、`tools.json` | `root` |
| `memory/` | `memory` |
| `skills/` | `skills` |
| `subagents/` | `subagents` |
| `knowledge/` | `knowledge` |
| `agents/<agentId>/sessions/` | `sessions` |
| `agents/<agentId>/tasks/` | `tasks` |

上記の表にないパスは、ローカルの `LocalFilesystem`(シェルなし)にフォールスルーします。

#### 例:マルチレプリカのカスタマーサービスエージェント

3つのポッドがそれぞれ `HarnessAgent` を実行し、1つの Redis を `BaseStore` として共有します。

```java
DistributedStore store = RedisDistributedStore.fromJedis(
        new JedisPooled("redis://shared-redis:6379"));

HarnessAgent agent = HarnessAgent.builder()
    .name("customer-service")
    .model(model)
    .workspace(Paths.get("/opt/agent/workspace"))
    .distributedStore(store)                  // stateStore + baseStore in one call
    .filesystem(new RemoteFilesystemSpec()
        .isolationScope(IsolationScope.USER)      // one namespace per user
        .anonymousUserId("anonymous"))            // fallback for unauthenticated callers
    .build();
```

- 各ポッドのローカルの `AGENTS.md` / `knowledge/` / `skills/` は読み取り専用のテンプレートとして機能します(git 同期);
- 実行時の出力(`MEMORY.md`、`memory/`、会話ログ)は自動的に Redis に保存されます――どのポッドも最新の状態を読み取れます;
- Alice の記憶は KV キー `agents/customer-service/users/alice/memory/...` の下に存在します。

このモードは**意図的に**シェルを提供**しません**――シェルが必要な場合は、モード2(サンドボックス)または3(ローカル)を使ってください。

#### 利用可能な `BaseStore` 実装

| 実装 | 説明 |
|---------------|-------------|
| `RedisStore` | Jedis ベース、低レイテンシ・高並行向け | `agentscope-extensions-redis` |
| `JdbcStore` | JDBC ベース、MySQL / PostgreSQL / H2 向け | `agentscope-extensions-mysql` |
| `InMemoryStore` | インメモリ、テスト向け | `agentscope-harness` |

---

### モード2:サンドボックス(`SandboxFilesystemSpec` ファミリー)

「コードが信頼できない操作を実行する可能性がある」場合、あるいは「本番ホストから隔離したい」場合に使います。すべてのファイル操作とシェルコマンドはサンドボックスへ送られ、ホストには一切触れません。

#### Docker サンドボックス

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("sandbox-agent")
    .model(model)
    .workspace(workspace)
    .filesystem(new DockerFilesystemSpec()
        .image("ubuntu:24.04")
        .isolationScope(IsolationScope.SESSION)
        .memorySizeBytes(512 * 1024 * 1024L)   // 512 MB memory limit
        .cpuCount(2L)
        .network("host")
        .exposedPorts(8080, 3000)
        .environment(Map.of("NODE_ENV", "development"))
        .snapshotSpec(new LocalSnapshotSpec("/data/snapshots")))
    .build();
```

`DockerFilesystemSpec` — すべてのオプション:

| メソッド | 説明 | デフォルト |
|--------|-------------|---------|
| `image(String)` | Docker イメージ | 必須 |
| `isolationScope(IsolationScope)` | 分離の次元 | `SESSION` |
| `memorySizeBytes(Long)` | コンテナのメモリ制限 | Docker のデフォルト |
| `cpuCount(Long)` | CPU の制限 | Docker のデフォルト |
| `network(String)` | Docker ネットワーク | Docker のデフォルト |
| `exposedPorts(int...)` | 公開するポート | なし |
| `environment(Map)` | コンテナの環境変数 | なし |
| `workspaceRoot(String)` | コンテナ内のワークスペースマウントポイント | `/workspace` |
| `additionalRunArgs(String...)` | 追加の `docker run` 引数 | なし |
| `snapshotSpec(SandboxSnapshotSpec)` | スナップショット戦略 | `NoopSnapshotSpec`(スナップショットなし) |
| `workspaceSpec(WorkspaceSpec)` | ワークスペースのマウントルール | デフォルト |
| `executionGuard(SandboxExecutionGuard)` | AGENT / GLOBAL スコープ向けの並行制御ガード | なし |
| `workspaceProjectionEnabled(boolean)` | ホスト → サンドボックスの静的アセット投影を有効にする | `true` |
| `workspaceProjectionRoots(List)` | 投影に含めるルートパス | `AGENTS.md`、`skills`、`subagents`、`knowledge`、`.skills-cache` |

#### Kubernetes サンドボックス(agent-sandbox)

Kubernetes ストアは完全に [agent-sandbox](https://github.com/kubernetes-sigs/agent-sandbox) に基づいています:サンドボックスポッドはクラスタ内の agent-sandbox コントローラーによって管理され、イメージ、リソース、PVC はすべて `SandboxTemplate` / `SandboxWarmPool` としてクラスタ側で宣言されます(Java から設定するものではありません)。Java 側はウォームプールから `SandboxClaim` 経由でインスタンスを取得します。使用前に agent-sandbox コントローラーをインストールし、テンプレートとウォームプールを作成しておいてください。

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("k8s-agent")
    .model(model)
    .workspace(workspace)
    .filesystem(new KubernetesFilesystemSpec()
        .namespace("agents")
        .warmPoolName("agent-pool")        // SandboxWarmPool name
        .isolationScope(IsolationScope.USER))
    .build();
```

主な `KubernetesFilesystemSpec` オプション:

| メソッド | 説明 | デフォルト |
|--------|-------------|---------|
| `namespace(String)` | SandboxClaim の namespace | `default` |
| `warmPoolName(String)` | `SandboxWarmPool` の名前 | 必須 |
| `workspaceRoot(String)` | サンドボックス内のワークスペースルート;**テンプレートで宣言された PVC マウント上になければならない** | `/workspace` |
| `fileApiBaseDir(String)` | ランタイムファイル API のベースディレクトリ;`workspaceRoot` と一致させること;空欄なら base64-over-exec 転送にフォールバック | `/workspace` |
| `apiUrl(String)` | ランタイム API への直接 URL(設定時はこちらが優先) | なし |
| `gatewayName(String)` / `gatewayNamespace(String)` / `gatewayScheme(String)` | Gateway API 経由でサンドボックスに到達する | なし |
| `serverPort(int)` | ランタイム HTTP API のポート | `8888` |
| `kubernetesClient(KubernetesClient)` | カスタム fabric8 クライアント | kubeconfig を自動ロード |
| `snapshotSpec(SandboxSnapshotSpec)` | スナップショット戦略(PVC のトレードオフについては sandbox のページを参照) | `NoopSnapshotSpec` |

`apiUrl` も `gateway*` も設定されていない場合、`kubectl port-forward` によるローカルトンネルが使われます(開発向け)。ランタイムイメージは [ランタイムイメージの契約](/v2/ja/docs/harness/sandbox#ランタイムイメージの契約) を満たす必要があります。**ワークスペースの永続化はテンプレートで設定された PVC に依存します**――[Sandbox - Kubernetes の状態永続化](/v2/ja/docs/harness/sandbox#kubernetes-の状態永続化pvc-が第一層) を参照してください。

#### E2B サンドボックス

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("e2b-agent")
    .model(model)
    .workspace(workspace)
    .filesystem(new E2bFilesystemSpec()
        .apiKey("${E2B_API_KEY}")
        .templateId("my-template")
        .sandboxTimeoutSeconds(300)
        .isolationScope(IsolationScope.SESSION))
    .build();
```

#### Daytona サンドボックス

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("daytona-agent")
    .model(model)
    .workspace(workspace)
    .filesystem(new DaytonaFilesystemSpec()
        .apiKey("${DAYTONA_API_KEY}")
        .controlPlaneBaseUrl("https://api.daytona.io")
        .image("python:3.12-slim")
        .cpu(2)
        .memory(4)        // GiB
        .disk(10)         // GiB
        .isolationScope(IsolationScope.USER))
    .build();
```

#### AgentRun サンドボックス(Alibaba Cloud)

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("agentrun-agent")
    .model(model)
    .workspace(workspace)
    .filesystem(new AgentRunFilesystemSpec()
        .apiKey("${AGENTRUN_API_KEY}")
        .accountId("your-account-id")
        .region("cn-hangzhou")
        .templateName("python3.12")
        .sandboxIdleTimeoutSeconds(600)
        .isolationScope(IsolationScope.USER))
    .build();
```

#### `SandboxFilesystemSpec` から継承される共通オプション

| メソッド | 説明 | デフォルト |
|--------|-------------|---------|
| `isolationScope(IsolationScope)` | 分離の次元 | ストア固有(通常は `SESSION`) |
| `snapshotSpec(SandboxSnapshotSpec)` | スナップショット戦略 | `NoopSnapshotSpec` |
| `executionGuard(SandboxExecutionGuard)` | AGENT/GLOBAL スコープ向けの並行直列化ガード | なし |
| `workspaceProjectionEnabled(boolean)` | ホストからサンドボックスへ静的アセットを投影する | `true` |
| `workspaceProjectionRoots(List)` | 投影に含めるルートパス | `AGENTS.md`、`skills`、`subagents`、`knowledge`、`.skills-cache` |

#### スナップショット戦略

スナップショットにより、次の `call()` が前回のサンドボックス状態(インストール済み依存、生成されたファイルなど)を復元できます。

| 実装 | 説明 |
|---------------|-------------|
| `NoopSnapshotSpec` | スナップショットなし(デフォルト) |
| `LocalSnapshotSpec(Path)` | ホストのローカルディスクに保存されるスナップショット |
| `RedisSnapshotSpec` | Redis に保存されるスナップショット |
| `OssSnapshotSpec` | オブジェクトストレージ(Alibaba Cloud OSS)に保存されるスナップショット |
| `RemoteSnapshotSpec` | `BaseStore` に保存されるスナップショット |

#### 例:コーディングアシスタント(Docker + ローカルスナップショット)

```java
HarnessAgent codingAgent = HarnessAgent.builder()
    .name("coder")
    .model(model)
    .workspace(Paths.get(".agentscope/workspace"))
    .filesystem(new DockerFilesystemSpec()
        .image("node:20-slim")
        .isolationScope(IsolationScope.USER)
        .memorySizeBytes(1024 * 1024 * 1024L)
        .snapshotSpec(new LocalSnapshotSpec("/data/sandbox-snapshots")))
    .distributedStore(store)
    .build();

// Alice's first call: npm install inside sandbox, snapshot saved afterward
RuntimeContext rc = RuntimeContext.builder()
    .userId("alice")
    .sessionId("dev-session-1")
    .build();
agent.call(Msg.user("npm install && npm test"), rc).block();

// Alice's second call: snapshot restored, node_modules still present
agent.call(Msg.user("npm run build"), rc).block();
```

#### ワークスペース投影

サンドボックスが起動すると、フレームワークはワークスペースの「静的アセット」を tar 化し、コンテナ内の `/workspace` にハイドレートします。これには以下が含まれます。

- `AGENTS.md`(ペルソナファイル)
- `skills/`(スキルディレクトリ)
- `subagents/`(サブエージェントの宣言)
- `knowledge/`(ナレッジベース)
- `.skills-cache/`(スキルキャッシュ)

投影は内容を SHA-256 で比較します。変更のないファイルはハイドレーションをスキップします。含めるパスは `workspaceProjectionRoots(List)` でカスタマイズするか、`workspaceProjectionEnabled(false)` で完全に無効化できます。

---

### モード3:ローカル+シェル(デフォルト)

`filesystem(...)` の呼び出しがない場合に得られる構成:ワークスペースは `${cwd}/.agentscope/workspace/` に存在し、シェルはホスト上で動作します。

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("local-agent")
    .model(model)
    .workspace(workspace)
    // .filesystem(...) omitted = local + shell
    .build();
```

#### 設定オプションの全一覧

```java
.filesystem(new LocalFilesystemSpec()
    .executeTimeoutSeconds(120)       // shell command timeout
    .maxOutputBytes(100_000)          // max output bytes per command
    .env("MY_VAR", "value")          // extra environment variables
    .inheritEnv(true)                // inherit parent process env
    .mode(LocalFsMode.ROOTED)        // path policy
    .project(Paths.get("/my/project")) // project root (shell cwd + overlay lower)
    .addRoot(Paths.get("/extra/dir"))) // extra allowed directory
```

| メソッド | 説明 | デフォルト |
|--------|-------------|---------|
| `executeTimeoutSeconds(int)` | シェルコマンドのタイムアウト(秒) | 120 |
| `maxOutputBytes(int)` | コマンドごとにキャプチャする出力の最大バイト数 | 100,000 |
| `env(String, String)` | シェルの環境変数を追加する | なし |
| `inheritEnv(boolean)` | 親プロセスの環境を継承する | `false` |
| `mode(LocalFsMode)` | パス解決ポリシー | `ROOTED` |
| `project(Path)` | プロジェクトルートディレクトリ(オーバーレイの下層 + シェルの cwd) | `System.getProperty("user.dir")` |
| `addRoot(Path)` | エージェントがアクセスできる追加のホストディレクトリ | なし |
| `additionalRoots(Collection)` | 追加ディレクトリを一括設定する | なし |
| `projectWritable(boolean)` | ワークスペース以外への書き込みをワークスペースではなくプロジェクトディレクトリへルーティングする | `false` |

#### パス解決ポリシー(`LocalFsMode`)

| モード | 動作 |
|------|------|
| `ROOTED`(デフォルト) | 絶対パスは `workspace` + `project` + `additionalRoots` 配下でのみ許可される;`..` によるトラバーサルは拒否される |
| `SANDBOXED` | すべてのパスはワークスペースルートを基準にする;絶対パスと `..` の両方が拒否される |
| `UNRESTRICTED` | 絶対パスはそのまま通過する。テストまたは完全に信頼できる環境専用 |

#### オーバーレイファイルシステム

ローカルモードは実際には `OverlayFilesystem` を生成します。

- **上層**(読み書き可能):`LocalFilesystemWithShell`。`workspace` をルートとし、シェルを提供する;
- **下層**(読み取り専用):`LocalFilesystem`。`project` をルートとする。

読み取りはまずワークスペースを確認し、次にプロジェクトへフォールバックします(コピーオンライトのセマンティクス)。シェルの `pwd` はプロジェクトディレクトリなので、`ls` にはプロジェクトのファイルが表示されます。

#### プロジェクト書き込み可能モード(`projectWritable`)

デフォルトでは、すべての書き込みはワークスペースに行われます――読み取り/分析シナリオには問題ありませんが、エージェントの仕事が**コードを生成する**こと(例:マイクロサービスのひな形を作る)であれば、ファイルはプロジェクトディレクトリではなく `.agentscope/workspace/` に行き着いてしまいます。

`projectWritable(true)` を有効にすると、フレームワークはパスに基づいて書き込みをルーティングします。

| パスの種類 | 書き込み先 | 例 |
|-----------|-----------|----------|
| ワークスペースのメタデータ | ワークスペース | `MEMORY.md`、`memory/`、`agents/`、`skills/`、`knowledge/`、`plans/`、`subagents/`、`rules/`、`tools.json` |
| それ以外すべて | プロジェクトディレクトリ | `src/main/java/App.java`、`pom.xml`、`README.md`、`docker-compose.yml` |

```java
.filesystem(new LocalFilesystemSpec()
    .projectWritable(true)      // code files go to the project directory
    .inheritEnv(true))
```

読み取りの挙動は変わりません――ワークスペースが先、プロジェクトはフォールバックです。

#### 例:ローカル開発アシスタント

```java
HarnessAgent devHelper = HarnessAgent.builder()
    .name("dev-helper")
    .model(model)
    .workspace(Paths.get(".agentscope/workspace"))
    .filesystem(new LocalFilesystemSpec()
        .project(Paths.get("/Users/alice/my-project"))
        .addRoot(Paths.get("/Users/alice/.config"))
        .mode(LocalFsMode.ROOTED)
        .inheritEnv(true)
        .executeTimeoutSeconds(300))
    .build();
```

エージェントは `/Users/alice/my-project` と `/Users/alice/.config` 配下のファイルを読み書きでき、cwd を `/Users/alice/my-project` にしてシェルコマンドを実行できますが、他のホストディレクトリにはアクセスできません。

---

## IsolationScope —— ユーザーとレプリカ間のバケット分け

モード1(共有ストア)とモード2(サンドボックス)の両方が、**誰が誰と状態を共有するか**を決めるために同じ `IsolationScope` の概念を使います。

| スコープ | 意味 | 名前空間キー | 典型的な用途 |
|-------|---------|--------------|-------------|
| `SESSION` | 各 sessionId が独立している | `agents/<agentId>/sessions/<sessionId>/...` | マルチユーザー SaaS、各会話が完全に分離される |
| `USER`(デフォルト) | 同じ `userId` がセッションをまたいで共有する | `agents/<agentId>/users/<userId>/...` | 同一ユーザーの複数セッションが長期記憶を共有する |
| `AGENT` | このエージェントのすべてのユーザー/セッションが共有する | `agents/<agentId>/shared/...` | パブリックナレッジベース型のエージェント |
| `GLOBAL` | すべてに対して1つの共有スロット | `global/...` | 慎重に使うこと |

### スコープごとのフォールバックルール

- `USER` スコープの下で `RuntimeContext.userId` が無い場合、`SESSION`(sessionId で分離)にフォールバックします。
- `SESSION` スコープの下で `RuntimeContext.sessionId` が無い場合、状態のルックアップはスキップされ、新しい環境が作成されます。
- `AGENT` スコープは名前空間キーとしてエージェント名(ビルド時に固定)を使うため、コンテキストフィールドの欠落によって劣化することはありません。

### サンドボックスモードでの並行性

サンドボックスモードの `IsolationScope` は、ライブインスタンスの共有ではなく、**逐次的な再利用**による共有です。同じスコープキーへの同時呼び出しはそれぞれ独自の実行中コンテナを持ちます。呼び出しの終わりには、最後に書き込まれたスナップショットが勝ちます。複数ユーザーが状態を共有する `AGENT` / `GLOBAL` スコープでは、`executionGuard(SandboxExecutionGuard)` を使って同時アクセスを直列化してください。

### 例:さまざまなビジネスニーズに応じたスコープの組み合わせ

**シナリオ1:ユーザーごとのコーディングサンドボックスで、セッションをまたいでインストール済み依存を保持する**

```java
.filesystem(new DockerFilesystemSpec()
    .image("python:3.12")
    .isolationScope(IsolationScope.USER)       // all of Alice's sessions share one snapshot
    .snapshotSpec(new LocalSnapshotSpec("/snapshots")))
```

**シナリオ2:会話ごとの使い捨てサンドボックス**

```java
.filesystem(new DockerFilesystemSpec()
    .image("ubuntu:24.04")
    .isolationScope(IsolationScope.SESSION))   // each sessionId independent
```

**シナリオ3:共有ナレッジ型のカスタマーサービスエージェント(共有ストア)**

```java
.distributedStore(store)
    .filesystem(new RemoteFilesystemSpec()
    .isolationScope(IsolationScope.AGENT))     // all users and sessions share memory / skills
```

---

## マルチユーザー分離の仕組み

`RuntimeContext.userId` はマルチユーザー分割の鍵です。

| モード | userId が何をするか | 物理的な現れ方 |
|------|-----------------|----------------------|
| ローカル | ユーザーレベルのファイルは `workspace/<userId>/...` に配置される。例:`workspace/alice/skills/code-reviewer/SKILL.md` は Alice にのみ適用される | パスプレフィックス |
| 共有ストア | KV 名前空間プレフィックス `agents/<agentId>/users/<userId>/...` として使われる | KV キープレフィックス |
| サンドボックス | サンドボックスのスナップショットスロットキーとして使われる(`IsolationScope.USER` と組み合わせる) | サンドボックスインスタンスの分離 |

`userId` が無い場合、シングルテナントのデフォルトが適用され、全員が1つのルートを共有します。

### 実行時データ vs 静的アセット

**実行時データ**(会話ログ、タスク、記憶)は `IsolationScope` / `userId` に従い、自動的に分離されます。

**静的アセット**(`AGENTS.md`、`tools.json`、`knowledge/`)はすべてのユーザー間で共有され、userId によって自動的に分割**されません**。差別化は、ユーザーごとの上書きディレクトリを通じてのみ可能です。

```
workspace/
├── skills/code-reviewer/SKILL.md     ← shared (visible to everyone)
└── alice/
    └── skills/code-reviewer/SKILL.md ← only applies to Alice; overrides shared
```

---

## 各モードでのスキルとツールの振る舞い

### スキル

`DynamicSkillMiddleware` は各推論ターンの前にリポジトリの一覧からスキルをマージし、それらをシステムプロンプトにレンダリングします。スキルファイルの読み込みは `AbstractFilesystem` インターフェースを経由するため、3つのモードすべてで透過的に動作します。

| モード | スキルの読み込み方法 |
|------|----------------|
| ローカル | ローカルディスクの `workspace/skills/` から直接読み取る;ユーザーごとの上書きは `<userId>/skills/` |
| 共有ストア | `skills/` は KV にルーティングされる――まずリモートを確認し、次にローカルテンプレートにフォールバックする。管理者による編集は、すべてのレプリカで次の推論ターンに反映される |
| サンドボックス | ホストの `skills/` は、起動時のワークスペース投影によってサンドボックスの `/workspace/skills/` に注入される |

四層の優先順位は変わりません(低 → 高):`projectGlobalSkillsDir` → `skillRepository` → `workspace/skills/` → `<userId>/skills/`。

### ファイルツール(read_file / write_file / edit_file / ...)

すべてのファイルツールは `AbstractFilesystem` インターフェースを介して呼び出され、操作のたびに現在の `RuntimeContext` を渡します。実際の読み書きの場所はファイルシステムの実装が決定します。エージェントのコードはモードを一切意識しません。

| モード | 読み書きの挙動 |
|------|-------------------|
| ローカル | `OverlayFilesystem`:書き込みはワークスペース(上層)に行く;読み取りはまずワークスペース、次にプロジェクト(下層)を確認する。`projectWritable(true)` の場合、メタデータでない書き込みはプロジェクトディレクトリへルーティングされる |
| 共有ストア | `CompositeFilesystem`:ルーティングされたパスは KV オーバーレイ(リモートが上層、ローカルテンプレートが下層)を経由する;それ以外はローカルへ行く |
| サンドボックス | すべてのファイル操作がサンドボックスコンテナに転送される |

### シェル実行(execute)

| モード | シェルは使えるか? | どこで実行されるか |
|------|-----------------|--------------|
| ローカル | はい | ホストの `sh -c`、cwd = `project` ディレクトリ |
| 共有ストア | いいえ | シェルは提供されない |
| サンドボックス | はい | サンドボックスコンテナ内 |

### tools.json / MCP サーバー

`tools.json` は `build()` 時にワークスペースから一度だけ読み込まれ(`WorkspaceManager` を経由し、二層読み込みをサポート)、MCP サーバーを登録し、allow/deny フィルタを適用します。**この振る舞いは3つのモードすべてで同じです**――設定はビルド時に読み込まれ、実行時のファイルシステムモードには影響されません。

共有ストアモードでは、`tools.json` も上述の「リモートが上層、ローカルテンプレートが下層」のオーバーレイに従います:管理者コンソール経由で `tools.json` を変更しても、有効にするには**エージェントの再ビルド**が必要です(MCP サーバーの登録は一度きりの操作です)。

---

## ワークスペースにおける二層読み込み

`AGENTS.md`、`MEMORY.md`、`KNOWLEDGE.md` のような主要ファイルは、読み取り時に「二層フォールバック」を持ちます:まず設定されたファイルシステムを確認し、見つからなければローカルディスクにフォールバックします。これはモード1(共有ストア)における**「テンプレートファイル」**に役立ちます:最初のレプリカのローカルにはテンプレートの `AGENTS.md` があるためすぐに動作し、後続のレプリカは共有ストアから最新版を読み取ります。

書き込みは常に設定されたファイルシステムストアを経由します。

## 完全な自前管理:`abstractFilesystem(...)`

3つのモードのいずれも合わない場合は、完全に自前実装のファイルシステムを渡します。

```java
HarnessAgent.builder()
    ...
    .abstractFilesystem(myCustomFilesystem)   // mutually exclusive with filesystem(...)
    .build();
```

通常は不要です――3つのモードでユースケースの約95%をカバーします。

## 関連ページ

- [Sandbox](/v2/ja/docs/harness/sandbox) — モード2の実行時の詳細(コンテナのライフサイクル、スナップショット復旧チェーン)
- [Workspace](/v2/ja/docs/harness/workspace) — ディレクトリレイアウト、読み込みの仕組み、二層読み込みの「下層」
- [Context](/v2/ja/docs/building-blocks/context) — `AgentState` と `AgentStateStore`、`(userId, sessionId)` によるアドレス指定
- [Skills](/v2/ja/docs/harness/skill) — 四層合成、自己学習ループ、`<available_skills>` ブロック
- [Tools](/v2/ja/docs/building-blocks/tool) — `read_file` / `write_file` / `execute` のパラメータ
- [Architecture](/v2/ja/docs/harness/architecture) — ファイルシステムとランタイムコンテキストがどう協調するか
