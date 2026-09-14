---
title: "Sandbox"
description: "分離実行 + 呼び出しをまたぐ復旧 + マルチレプリカデプロイ"
---

> 3つのファイルシステムモードの比較については [Filesystem](./filesystem.md) を参照してください。このページはサンドボックスモードの使い方に焦点を当てます。

## サンドボックスが解決すること

エージェントの**ファイル操作とコマンド実行**を分離された環境に閉じ込め、ホストは無傷のままにします。さらに3つの利点があります。

1. **実行境界** — 信頼できない入力、疑わしいスクリプト、`rm -rf` のような形のコマンドはすべてサンドボックス内に留まります。
2. **呼び出しをまたぐ復旧** — 会話の状態だけでなく、`pip install`、`npm install`、生成された一時ファイル(実行環境そのもの)がスナップショット化されるため、次の `call()` は再インストールなしで同じサンドボックスの続きから再開します。
3. **マルチレプリカ対応** — 複数のレプリカが同じ論理ユーザーにサービスを提供する場合、サンドボックスの状態は1つのスロットを共有でき、どのノードでも同じワークスペースを再開できます。

## 最小限の例

ユーザーごとに分離されたローカル Docker:

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("code-agent")
    .model(model)
    .workspace(workspace)
    .filesystem(new DockerFilesystemSpec()
        .image("ubuntu:24.04"))
    .build();

agent.call(msg, RuntimeContext.builder()
    .userId("alice")
    .sessionId("conv-1")
    .build()).block();
```

`call()` をまたいで同じ `userId` を使うと → 自動的に同じサンドボックスを再利用します(またはスナップショットから復元します)。異なる `userId` → 別々のサンドボックス。`userId` が無い場合は、分離キーとして `sessionId` にフォールバックします。

## IsolationScope —— 誰がサンドボックスを共有するか

すべてのサンドボックス設定は `SandboxFilesystemSpec`(例:`DockerFilesystemSpec`)上にあります。鍵となるパラメータは `isolationScope` です。

| スコープ | 共有 | 典型的な用途 |
|-------|---------|-------------|
| `USER`(デフォルト) | 同じ `userId` のセッションが共有する;userId が無い場合は `SESSION` にフォールバックする | マルチユーザー SaaS ――各ユーザーは会話をまたいで1つのワークスペースを保持する |
| `SESSION` | 各 sessionId が独立している | 厳密な会話ごとの分離 |
| `AGENT` | このエージェントのすべてのユーザー/セッションが共有する | パブリックツール型のエージェント、共有ナレッジベース |
| `GLOBAL` | ストアごとに1つの共有スロット | 慎重に使うこと |

```java
// Explicit SESSION scope (overrides default USER)
.filesystem(new DockerFilesystemSpec()
    .image("ubuntu:24.04")
    .isolationScope(IsolationScope.SESSION))
```

`SESSION` は本質的に並行性に対して安全です(各セッションが独自のスロットを持つため)。マルチレプリカデプロイにおける `USER` / `AGENT` / `GLOBAL` は、下記の「並行制御」と組み合わせるべきです。

**USER スコープのフォールバック:** `IsolationScope.USER` がアクティブなとき(明示的、あるいはデフォルトによる)、`RuntimeContext.userId` が無い場合、フレームワークは自動的に `sessionId` を使う `SESSION` スコープにフォールバックします。つまり、userId の欠落に対してガードする必要はありません――サンドボックスは優雅に劣化します。

## 呼び出しをまたぐ復旧 = スナップショット

サンドボックスは各 `call()` の終わりにワークスペースをスナップショットし、次の開始時に復元します。

- コンテナがまだ生きていて、ワークスペースもまだそこにある → そのまま続行(最速)
- コンテナが消えている → スナップショットから再起動し、ワークスペースを復元する
- スナップショットが無い → `WorkspaceSpec` からの完全な初期化(コールドスタート)

スナップショットの保存先は `snapshotSpec` によって決まります。

| オプション | いつ |
|--------|------|
| `NoopSnapshotSpec`(デフォルト) | 永続化なし;コンテナが消えたらコールドスタート |
| `LocalSnapshotSpec` | ホストのローカルファイル(単一マシンでの長時間稼働) |
| `OssSnapshotSpec` | OSS / S3 互換(マルチレプリカ) |
| `RedisSnapshotSpec` | Redis(低レイテンシ、小さなワークスペース) |
| `JdbcSnapshotSpec` | MySQL / JDBC BLOB(既存のリレーショナル DB) |

```java
.filesystem(new DockerFilesystemSpec()
    .image("ubuntu:24.04")
    .snapshotSpec(new OssSnapshotSpec(ossClient, "my-bucket", "agentscope/")))
```

ホスト側のワークスペースファイル(`AGENTS.md` / `skills/` / `subagents/` / `knowledge/`)は、コンテンツハッシュによるゲートを伴い、開始のたびにサンドボックスへ同期されます。そのため、`skills/` 配下のスクリプトを編集すれば、次の `call()` ではサンドボックス内に新しいバージョンが存在します。

## 分散デプロイ

複数のレプリカが同じエージェントを実行し、どのレプリカでも同じユーザーの会話を引き継げる必要がある場合は、以下が必要です。

1. 分散型の `AgentStateStore`(例:Redis バックエンド)――ビルダーの `.stateStore(...)` で渡す
2. 非 `Noop` のスナップショット(OSS / Redis / リモートストア)――ファイルシステムの spec 上で `.snapshotSpec(...)` を通じて直接設定する
3. 適切な `IsolationScope`(デフォルトの `USER` が通常正しい)

すべてを1か所で設定します。

```java
HarnessAgent.builder()
    .name("assistant")
    .model(model)
    .workspace(workspace)
    .stateStore(redisStateStore)                    // distributed state
    .filesystem(new DockerFilesystemSpec()
        .image("ubuntu:24.04")
        .snapshotSpec(ossSnapshotSpec)              // cross-replica snapshot
        .isolationScope(IsolationScope.USER))       // default, can omit
    .build();
```

フレームワークは、サンドボックスのメタデータ(コンテナ ID、スナップショットへのポインタ、ワークスペース準備完了フラグ)を、エージェントのランタイム状態を保持するのと同じ `AgentStateStore` に保存します。分散ストアを提供すると、レプリカをまたぐサンドボックスの再開が自動的に有効になります――追加の設定は不要です。

サンドボックスモードでローカルの `AgentStateStore`(デフォルトの `JsonFileAgentStateStore`)を使っている場合、フレームワークはビルド時に警告をログ出力し、サンドボックスの状態が JVM の再起動を生き延びず、インスタンス間で共有できないことを注意喚起します。

## 並行制御(マルチレプリカ)

`USER` / `AGENT` / `GLOBAL` モードでレプリカをまたぐ場合、同じユーザーに同時にサービスを提供する2つのレプリカが両方とも同じスロットに書き込みます――最後の書き込みが勝ちます。それが許容できない場合は、分散ロックが必要です。

**推奨**:`distributedStore(...)` を使う――スナップショットと実行ガードが自動的に注入されます。

```java
DistributedStore store = RedisDistributedStore.fromJedis(jedis);

HarnessAgent.builder()
    .distributedStore(store)    // auto-wires stateStore + snapshotSpec + executionGuard
    .filesystem(new DockerFilesystemSpec()
        .image("ubuntu:24.04")
        .isolationScope(IsolationScope.USER))
    .build();
```

ロックパラメータをカスタマイズするには、`SandboxFilesystemSpec` 上で明示的にガードを設定します。

```java
.filesystem(new DockerFilesystemSpec()
    .image("ubuntu:24.04")
    .isolationScope(IsolationScope.USER)
    .executionGuard(RedisSandboxExecutionGuard.builder(jedis)
        .leaseTtl(Duration.ofMinutes(30)).build()))
```

組み込みの実装:`RedisSandboxExecutionGuard`(Redis の `SET NX PX`)、`JdbcSandboxExecutionGuard`(MySQL の `GET_LOCK()`)。`SandboxExecutionGuard` を実装して、Zookeeper、etcd、その他のロックストアをプラグインすることもできます。

## 自前管理のサンドボックスインスタンス(上級)

デフォルトでは、フレームワークがサンドボックスのライフサイクル全体を所有します。「自分で管理したい」という3つのシナリオがあります。

**1. すでに実行中のコンテナがあり、エージェントにそれを使わせたい**

```java
Sandbox mySandbox = dockerClient.create(workspaceSpec, snapshotSpec, options);
mySandbox.start();

SandboxContext callCtx = SandboxContext.builder()
    .client(dockerClient)
    .externalSandbox(mySandbox)       // framework only stops() at end of call, doesn't shutdown()
    .build();

agent.call(msgs, RuntimeContext.builder()
    .sessionId("my-session")
    .put(SandboxContext.class, callCtx)
    .build()).block();

// shut it down yourself when done
mySandbox.shutdown();
```

**2. 特定のスナップショット文字列があり、その時点に復元したい**

```java
SandboxState savedState = dockerClient.deserializeState(savedStateJson);
SandboxContext callCtx = SandboxContext.builder()
    .client(dockerClient)
    .externalSandboxState(savedState)  // framework restores from this state but owns the lifecycle
    .build();
```

**3. 複数のエージェントが1つのサンドボックスを共有する**

同じ `externalSandbox` を各エージェントの `call()` に渡し、完了したら自分で `shutdown()` してください。

## サンドボックスストアの選び方

| ストア | 最適な用途 |
|---------|----------|
| **Docker** | ローカル開発 / 単一マシン / 信頼できるシェル |
| **Kubernetes** | 自前ホストの K8s;完全に [agent-sandbox](https://github.com/kubernetes-sigs/agent-sandbox)(SandboxClaim / WarmPool)に基づく、PVC によるワークスペース永続化(下記参照) |
| **Daytona** | 汎用のマネージドサンドボックス HTTP API |
| **E2B** | 汎用のマネージドサンドボックス + ネイティブプラットフォームのスナップショット |
| **AgentRun** | Aliyun マネージドのサンドボックス(Function Compute FC 3.0);インスタンスごとの NAS / OSS 自動マウント;中国本土での低レイテンシ。通常の `SandboxFilesystemSpec` として扱われる――完全なセットアップの詳細(テンプレート、RAM 権限、NAS ファーストの設定)は統合ドキュメントにある |

すべてのストアは同じインターフェースを実装しています;エージェントのコード、ツールキット、`AGENTS.md` は変わりません。

## ランタイムイメージの契約

サンドボックスのイメージ(Docker の `image`、Kubernetes agent-sandbox のランタイムイメージなど)はあなたが選びますが、**どんなイメージでも動くわけではありません**。Harness のファイルツール(`read_file` / `write_file` / `edit_file` / `grep_files` / `glob_files` / `list_files`)とスナップショットの仕組みはすべて、サンドボックス内で POSIX シェルコマンドを実行することで実装されています。イメージは以下の契約を満たさなければなりません。さもないと、ツールは診断しづらい形で失敗します。

### 基本契約(すべてのバックエンド共通)

イメージは以下を提供しなければなりません。

| カテゴリ | 要件 | 使用元 |
|----------|-------------|---------|
| シェル | POSIX 互換の `sh`(`[ ]`、`&&`、パイプ、リダイレクト、ヒアドキュメント) | すべてのファイルツール、`execute` ツール |
| コアユーティリティ | `mkdir` `dirname` `rm` `mv` `test` `printf` `sort` | 個々のファイル操作 |
| テキスト/検索 | `sed`、`grep`(`-rHnF`、`--include` を含む)、`find` | `read_file` のページネーション、`grep_files`、`glob_files` |
| メタデータ | GNU スタイルの `stat -c`(BSD の `stat -f` ではない) | `list_files`、`glob_files` |
| アーカイブ/エンコーディング | `tar`、`base64`(エンコード + `-d` デコード) | スナップショットの保存/ハイドレート、ファイルのアップロード/ダウンロード |
| インタプリタ | `python3` | `edit_file`(完全一致文字列置換) |
| ファイルシステム | 書き込み可能なワークスペースルート(デフォルト `/workspace`) | すべて |

`ubuntu:24.04` や `debian` ベースのイメージはそのまま条件を満たします(`python3` はインストールが必要な場合があります);`alpine`(BusyBox の `stat` / `grep` は挙動が異なる)や distroless イメージは条件を満た**しません**。

簡易な適合性チェック(イメージ内で実行し、すべて成功する必要があります):

```bash
sh -c 'echo ok' && python3 --version && tar --version \
  && printf x | base64 | base64 -d && stat -c %Y /tmp && grep -rHnF --include='*.txt' x /tmp; true
```

### Kubernetes(agent-sandbox)向けの追加契約

agent-sandbox バックエンドはコンテナに `kubectl exec` しません;ランタイムコンテナが公開する HTTP API(デフォルトポート 8888)と通信します。イメージ内のランタイムサービスは以下を実装しなければなりません。

| エンドポイント | セマンティクス |
|----------|----------|
| `POST /execute`(body `{"command": "..."}`) | **コマンドを POSIX シェルのセマンティクス(`sh -c` と同等)で解釈しなければならず**、`{"stdout", "stderr", "exit_code"}` を返す。Harness は `cd ... && (...)`、パイプ、その他のシェル構文を含むコマンドを送る |
| `POST /upload`(multipart;`filename` は相対パス) | ファイル API のベースディレクトリ配下にファイルを書き込む |
| `GET /download/{path}` | 相対パスでファイルバイト列をダウンロードする |
| `GET /list/{path}`、`GET /exists/{path}` | ディレクトリ一覧 / 存在確認 |

**ファイル API のベースディレクトリはワークスペースルートと一致しなければなりません**(推奨:両方とも `/workspace`)。Harness は `/upload` / `/download` を通じて2種類のコンテンツを転送します:ワークスペースのスナップショット tarball(そのディレクトリ内の `.agentscope-tmp/` 配下の一時ファイル)、そしてパスがベースディレクトリ配下にある場合の `write_file` / ファイルダウンロード用の単一ファイルのバイト列(Linux は単一のコマンドライン引数を約128KiB に制限しますが、ネイティブなファイル API 転送にはこの制限がありません)。ベースディレクトリは `KubernetesSandboxClientOptions.fileApiBaseDir` で設定されます(デフォルト `/workspace`);空欄に設定すると base64-over-exec 転送にフォールバックします。

> 注:upstream の agent-sandbox リポジトリにあるサンプルランタイム(`examples/python-runtime-sandbox`)は、シェルを使わずに `shlex.split` + `subprocess.run` でコマンドを実行し、ファイル API のルートを `/app` に置いています――これはこの契約に**適合しておらず**、エンドポイントの形状の参考としてのみ使うべきです。upstream の KEP-539.2 はランタイムインターフェース(REST/gRPC 仕様 + 適合性テスト)を標準化しつつあります;この契約は、それが着地すれば公式仕様に収束できます。

### なぜこのように設計されているか

`Sandbox` 抽象化の主要なデータプレーンのエントリポイントは `exec(command)` です。これは意図的なものです――`edit_file` / `grep_files`(正規表現、文字列置換、globbing)のようなツールのセマンティクスは、小さなファイル API エンドポイント群では表現できません;イメージ内で標準的なツールチェーンに対してシェルスクリプトを実行することが、唯一のポータブルな答えです。ファイル API(アップロード/ダウンロード)は純粋なバイト転送のみを扱います:ワークスペースのスナップショットと単一ファイルのアップロード/ダウンロードはこれを経由します(バックエンドは任意の `SandboxFileTransfer` インターフェースを実装することでその能力を宣言します);それ以外はすべて `execute` を経由します。これはつまり:**イメージの契約はサンドボックスインターフェースの一部である**ということです――イメージを切り替える前に、上記の適合性チェックを実行してください。

**モデルの視点から見た境界の越え方。** 上記のファイル API(アップロード/ダウンロード)は内部的な仕組みです――LLM からは見えず、`FilesystemTool` は転送ツールを一切公開しません。サンドボックス化されたエージェントが生成した成果物をサンドボックスの外の宛先へ渡すためのサポートされた方法は、汎用の **`deliver_artifact`** ツールです。これは、`HarnessAgent.builder().artifactDeliveryTarget(...)` で `ArtifactDeliveryTarget` を設定したときにのみ登録されます。SPI はビジネスに依存しないまま保たれます――`deliver(RuntimeContext, ArtifactDeliveryRequest) -> ArtifactDeliveryResult`――そのため、宛先のロジック(例:WebDAV アップロード)はアプリケーション側にあります。このツールはサンドボックスのワークスペースからファイルバイト列をダウンロードし、転送処理を宛先に委譲します。宛先が設定されていない場合、サンドボックスのワークスペースプロンプトは、ファイルがコンテナから出られないことをはっきりと述べます。

## Kubernetes の状態永続化:PVC が第一層

Kubernetes ストアは完全に agent-sandbox に基づいています:サンドボックスポッドは agent-sandbox コントローラーによって管理され、イメージ、リソース、ストレージはすべてクラスタ側で `SandboxTemplate` / `SandboxWarmPool` として宣言されます――Java 側はインスタンスを取得(`SandboxClaim`)して接続するだけです。これにより、重要な点で他のストアとは異なります:**ワークスペースデータの永続化は、主に PVC の仕事であり、Harness のスナップショットの仕事ではありません**。この2つの層はそれぞれ1つのことを担当します。

| 層 | 何を保存するか | 復旧シナリオ |
|-------|-------------------|--------|
| **PVC**(`SandboxTemplate.volumeClaimTemplates`) | ワークスペースファイルそのもの | ポッドの再起動 / 退避 / 休止と復帰――claim はまだ生きており、ファイルはボリュームとともに戻ってくる、転送ゼロ |
| **SandboxState + snapshotSpec**(Harness 層) | identity ポインタ(claimName / namespace)+ ワークスペース tarball のスナップショット | 再開時にサンドボックスを特定する(常に必要);claim が削除された / PVC が失われた / クラスタをまたいだ場合のコールド復旧 |

フレームワークは PVC に対して特別な処理を必要としません:再開時、起動プローブが `test -d /workspace` を確認します;PVC が存在する場合、「ワークスペース保持済み」の分岐が取られ、スナップショット復元は完全にスキップされます。

**正しく設定しなければならない3つのこと:**

1. **PVC は `workspaceRoot`(デフォルト `/workspace`)にマウントされていなければなりません。** 別の場所にマウントしたり `emptyDir` を使ったりすると、ポッドが再起動するたびにワークスペースは失われます――呼び出しごとにスナップショット復元またはコールドスタートに劣化します。参照テンプレート(upstream の agent-sandbox の例より):

```yaml
apiVersion: extensions.agents.x-k8s.io/v1beta1
kind: SandboxTemplate
spec:
  podTemplate:
    spec:
      containers:
      - name: runtime
        image: your-conformant-runtime:latest   # must satisfy the runtime image contract above
        ports:
        - containerPort: 8888
        volumeMounts:
        - name: workspace
          mountPath: /workspace                 # = workspaceRoot = fileApiBaseDir
  volumeClaimTemplates:
  - metadata:
      name: workspace
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 1Gi
```

2. **claim のライフサイクル境界に注意してください。** `shutdownTime` / `shutdownPolicy: Delete` が Sandbox を削除すると、PVC ベースのウォーム復旧は終わりです――次の再開では claim が見つからず、フレームワークは(空の新しい PVC を伴う)新しいサンドボックスを作成します。ワークスペースが戻ってくるかどうかは3番目の点次第です。

3. **それに応じて `snapshotSpec` を選んでください。** PVC + `NoopSnapshotSpec`(デフォルト):呼び出しごとの tar 化とアップロードをスキップし、claim が消えたときのコールドスタートを代償とします。PVC + OSS / Redis スナップショット:念には念を――claim の期限切れ、PVC の喪失、クラスタをまたいだ移行のすべてがスナップショットから復旧しますが、呼び出しごとの完全なアーカイブを代償とします。

もう一つ注意:`SandboxState` の identity 層は決して省略できません――マルチレプリカデプロイでは分散型の `AgentStateStore` を設定してください。さもないと、他のレプリカは claimName を知ることができず、PVC 上のデータがどれだけ無傷であっても到達不能になります。

## ワークスペースがどうサンドボックスにマップされるか

`workspace/` 配下のホスト側の主要ファイル(`AGENTS.md`、`skills/`、`subagents/`、`knowledge/`)は、コンテンツハッシュによるゲートを伴い、開始のたびにサンドボックスへ同期されます――変更のない内容はスキップされます。

ホストのディレクトリ(例:コードリポジトリ)をサンドボックスにバインドするには、`BindMountEntry` を使います(Docker のみ;Kubernetes の場合は、代わりにクラスタ側の `SandboxTemplate` の pod テンプレートでマウントを宣言してください;Daytona / E2B のようなマネージドサンドボックスはクラウド上で動作し、ホストパスをマウントできません)。

サンドボックス内のファイル変更はホストに同期し戻されません――サンドボックスが生成した成果物を取得するには、エージェントにそれらを `read_file` させてください。

## 自分でサンドボックスストアを実装する

Docker 以外の分離環境(自前ホストのリモートエグゼキュータ、商用サンドボックス API、ローカルモックなど)を統合するには、Harness のソース変更は不要です――いくつかの契約インターフェースを実装し、`filesystem(...)` に渡すだけです。`agentscope-harness` のテストにある `InMemorySandbox` ファミリーが、コピーすべき最小限の骨格です。

## 関連ページ

- [Filesystem](./filesystem.md) — 3つの宣言的モードの比較
- [Workspace](./workspace.md) — `workspace/` 配下のどのファイルがサンドボックスに同期されるか
- [Architecture](./architecture.md) — call() のタイムライン上でサンドボックスの取得/解放がどこに位置するか
