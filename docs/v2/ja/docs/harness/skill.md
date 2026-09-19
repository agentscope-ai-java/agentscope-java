---
title: Skill
description: 四層のスキル合成、スキルマーケットプレイス、自己学習ループ
---

スキルとはパッケージ化された能力です:`SKILL.md`(目的とエージェントが読む指示)、任意のリファレンスドキュメント、スクリプト、サンプルを含むディレクトリです。エージェントに渡せば、関連するときにそれを使うようになります。

Harness では、2つの場所からスキルをインストールできます。

- **スキルマーケットプレイス** — Git リポジトリ、Nacos、MySQL、classpath、カスタムストア
- **ワークスペース** — `workspace/skills/` は全員で共有される;`<userId>/skills/` はユーザーごとに分離される

両方のソースが同時に有効です――どちらかを選ぶ必要はありません。その上で、**自己学習ループ**を有効にすることもできます:エージェントがスキルを起草する → レビューゲート → バックグラウンドのキュレーターが整理する。

スキルディレクトリは次のような見た目です。

```
code-reviewer/
├── SKILL.md           # required — YAML frontmatter (name + description) + instructions for the agent
├── references/        # optional — long-form docs the agent reads on demand
│   └── style-guide.md
└── scripts/           # optional — executable scripts the agent can shell out to
    └── run-checks.sh
```

SKILL.md のフォーマット:

```markdown
---
name: code-reviewer
description: Use when the user asks for code review, style feedback, or PR audits.
---

# Code Reviewer

Steps:
1. Read `references/style-guide.md` for project conventions.
2. Run `scripts/run-checks.sh <target-path>` and summarize the output.
```

## クイックな例

チームのスキルリポジトリをプラグインすれば、エージェントはすぐにそれを使えるようになります。

```java
HarnessAgent agent = HarnessAgent.builder()
        .name("assistant")
        .model(model)
        .workspace(workspace)
        .skillRepository(new GitSkillRepository("https://github.com/your-org/team-skills.git"))
        .build();
```

推論中、エージェントはリポジトリ内のスキルを見て、必要なものについて `load_skill_through_path` を呼び出します。

## マーケットプレイスストア

`skillRepository(...)` が統一されたエントリポイントです――どんなストアでも渡せます。

### Git

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-skill-git-repository</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

```java
.skillRepository(new GitSkillRepository("https://github.com/your-org/team-skills.git"))
```

デフォルトでは、各読み取りが軽量なリモートチェックを行い、HEAD が変わったときだけ pull します。リポジトリに `skills/` サブディレクトリがあればそれがルートになり、なければリポジトリのルートがそのまま使われます。同期のタイミングを自分で制御するには:`new GitSkillRepository(url, false)` としてから、`repo.sync()` を手動で呼び出します。

### Nacos

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-nacos-skill</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

```java
NacosSkillRepository market = new NacosSkillRepository(aiService, "namespace");
HarnessAgent.builder()
        .skillRepository(market)
        .build();
```

オンライン配信+変更購読に最適です。`market` は `AutoCloseable` です;シャットダウン時にクローズして購読を解放してください。

### MySQL

```java
MysqlSkillRepository registry = MysqlSkillRepository.builder(dataSource)
        .databaseName("agentscope")
        .skillsTableName("skills")
        .createIfNotExist(true)
        .writeable(true)
        .build();

HarnessAgent.builder()
        .skillRepository(registry)
        .build();
```

プラットフォーム側でのスキル管理によく使われます。`writeable(true)` にすると、エージェントが書き戻せるようになります;読み取り専用の配信には `false` を渡してください。

### Classpath

スキルを JAR の中に同梱します。

```
src/main/resources/skills/
└── code-reviewer/
    └── SKILL.md
```

```java
.skillRepository(new ClasspathSkillRepository("skills"))
```

標準の JAR と Spring Boot の fat JAR の両方で動作します。

### 複数ストア

`skillRepository(...)` を複数回呼び出します;後のものが勝ちます。

```java
HarnessAgent.builder()
        .skillRepository(communityMarket)
        .skillRepository(internalRegistry)
        .skillRepository(teamGitRepo)
        .build();
```

## ワークスペースのスキル

ワークスペースのスキルは登録不要です;ディレクトリを配置するだけです。

### 全員で共有

```
workspace/skills/
└── code-reviewer/
    ├── SKILL.md
    ├── references/
    │   └── style-guide.md
    └── scripts/
        └── run-checks.sh
```

プロジェクト固有のルール、社内の慣習に最適です。

### ユーザーごと

特定のユーザー専用にスキルをインストールしたり、共有版を上書きしたりするには、その `userId` にちなんだ名前のディレクトリの下に配置します。

```
workspace/
├── skills/code-reviewer/SKILL.md   ← shared version
└── alice/
    └── skills/
        └── code-reviewer/
            └── SKILL.md            ← visible only to Alice; overrides the shared version
```

これには、呼び出し元が `RuntimeContext` に `userId="alice"` を渡す必要があります。

`workspace/<userId>/skills/` は**論理パス**であり、必ずしも「ローカルディスク上のディレクトリ」ではありません。スキルファイルは `AbstractFilesystem` の抽象化を通じて読み書きされ、それらが物理的にどこに置かれるかは設定した[ファイルシステムモード](/v2/ja/docs/harness/filesystem)次第です――そのため、ユーザーごとのスキル分離はストレージのバックエンドから切り離されています。

- **ローカル+シェル** — 文字通りホストディスク上の `workspace/alice/skills/...`。
- **共有ストア(リモートファイルシステム)** — `skills/` プレフィックスは KV ストアにルーティングされます;ユーザーごとの分離は、レプリカ間で一貫した名前空間キー `agents/<agentId>/users/alice/skills/...` として現れ、管理コンソールからの編集は次の推論ステップで有効になります。
- **サンドボックス(サンドボックスファイルシステム)** — ホスト側のユーザーディレクトリは、サンドボックス起動時にワークスペース投影を通じてコンテナの `/workspace` にハイドレートされるため、エージェントはサンドボックス内で同じコピーを読み取ります。

どのモードで動かしていても、`<userId>/skills/` は同じ優先度で共有版を上書きします。モードごとの分離キー、物理的な表現、そして `userId` の役割については、[Filesystem](/v2/ja/docs/harness/filesystem#マルチユーザー分離の仕組み) を参照してください。

## 競合の解決

4つのソースすべてが同名のスキルを生む可能性があります。優先度は低い順に:

| 優先度 | ソース | 設定方法 |
|----------|--------|------------------|
| 1(最低) | プロジェクトグローバルディレクトリ | `projectGlobalSkillsDir(Path)`、例:`~/.agentscope/skills/` |
| 2 | マーケットプレイス | `skillRepository(...)`;後の登録が勝つ |
| 3 | ワークスペース共有 | `workspace/skills/` |
| 4(最高) | ユーザーごと | `<userId>/skills/` |

競合しないスキルは下位層のものでもそのまま表示されます;名前が衝突した場合にのみ隠されます。

例:チームの Git に汎用の `code-reviewer` があり、プロジェクトの `workspace/skills/code-reviewer/` がこのコードベース向けにそれを上書きする;Alice の `<alice>/skills/code-reviewer/` がさらに Alice だけについてそれを上書きする――他のユーザーにはプロジェクト版が引き続き見えます。

## 一般的なビルダーオプション

| メソッド | 備考 |
|--------|-------|
| `skillRepository(repo)` | マーケットプレイスを追加する;複数回呼び出せる |
| `skillRepositories(list)` | すべてのマーケットプレイスを一度に置き換える |
| `projectGlobalSkillsDir(path)` | プロジェクトグローバルディレクトリを有効にする;存在しなければスキップされる |
| `disableDynamicSkills()` | 「推論ごとの再マージ」をオフにする;ビルド時に一度だけマージする |

サブエージェントは親のマーケットプレイスとプロジェクトグローバルディレクトリを自動的に継承します。

`disableDynamicSkills()` を使うべきとき:単発のタスクの場合;あるいはターンごとに再取得したくない低速なマーケットプレイスストアの場合。通常は触る必要はありません。

## 自己学習ループ(オプション)

Harness は、エージェントが自分でスキルを起草/整理/アーカイブできるループを縫い合わせます。各段階は独立してオプトインできます。

### ステップ1:エージェントにスキルを書かせる

```java
HarnessAgent.builder()
    ...
    .enableSkillManageTool(SkillManageConfig.defaults())
    .build();
```

有効化すると、エージェントは2つのツールを得ます。

- `propose_skill` — 新しいスキルを草稿として `skills/_drafts/<name>/` に書き込み、レビュー待ちにする
- `skill_manage` — 既存のスキルを編集する(作成 / 編集 / 付随ファイルの追加 / 削除)

「草稿 → レビュー」の2ステップを省略し、エージェントの書き込みを直接反映させることもできます:`.enableSkillManageTool(true)`(`autoPromote=true`)。本番環境では推奨されません。

フレームワークはまた、エージェントが `load_skill_through_path` / `read_skill` を呼び出すたびに、`skills/.usage.json` に保持される使用カウンタを自動的に加算します――このデータが、下記のクリーンアップとカナリアロールアウトを支えます。

### ステップ2:レビューゲート+可視性フィルタを追加する

```java
.enableSkillPromotionGate(
    new LocalApprovalGate(LocalApprovalGate.defaultPrompter()),    // who reviews
    new CompositeFilter(List.of(                                    // how to expose
        new EnvironmentFilter("prod", skillUsageStore),
        new CanaryFilter(0.10, skillUsageStore)
    )))
.environment("prod")
```

- **ゲート** — 草稿は、実際のスキルに昇格する前にこれを通過しなければなりません。3つの組み込みの種類があります:全拒否(デフォルト)、ローカルの人間による承認(stdin など)、通知して待機。
- **可視性フィルタ** — 推論中にエージェントが見られる、エージェント自身が作成したスキルを決定します。デプロイ環境タグ、カナリアの割合、許可リストによって合成できます。

### ステップ3:バックグラウンドでの定期的なキュレーション

```java
.enableSkillCurator(SkillCuratorConfig.builder()
    .intervalHours(7 * 24)        // weekly
    .staleAfterDays(30)
    .archiveAfterDays(90)
    .build())
```

スロットル制御されたバックグラウンドジョブが実行されます:30日以上使われていないスキルは stale(陳腐)になり、90日以上で `skills/.archive/` に移動します。任意で LLM による「umbrella merge」パスも実行できます(デフォルトはドライラン――レポートを出力するだけで、実際にファイルを変更しません)。

### プログラムによるトリガー

アプリケーションコードから:

```java
List<SkillAuditLog.Entry> entries = agent.queryAudit(LocalDate.now(), e -> true);

agent.runCuratorOnce()                                       // run a curation now (bypasses throttle)
     .subscribe(report -> System.out.println(report));

agent.promoteSkill("notes-taker", "alice")                   // manually promote a draft
     .subscribe(result -> System.out.println(result));
```

## エージェントがどうスキルを読み・実行するか

エージェントが推論するとき、現在スコープ内にあるすべてのスキルを列挙する `<available_skills>` ブロックがシステムプロンプトに見えます。

```xml
<available_skills>
<skill>
  <name>code-reviewer</name>
  <description>Use when the user asks for code review, style feedback, or PR audits.</description>
  <skill-id>code-reviewer_workspace-namespaced</skill-id>
  <files-root>/workspace/skills/code-reviewer</files-root>
</skill>
...
</available_skills>
```

各エントリは、エージェントがそれをロードするかどうかを判断するのに十分なメタデータだけを運びます。`<files-root>` は、存在する場合、エージェントがシェル実行に使う絶対パスです(下記参照)。

### SKILL.md とリソースを読む

スキルを有効化するために、エージェントは組み込みツール `load_skill_through_path` を呼び出します。

- `load_skill_through_path(skillId, path="SKILL.md")` は markdown 本文を返す
- `load_skill_through_path(skillId, path="references/style-guide.md")` はスキルディレクトリ配下の他のファイルを返す

ファイルがどう取得されるかは、スキルがどこから来たかによります。

| スキルのソース | `path` がどう解決されるか |
|--------------|------------------------|
| プロジェクトグローバルディレクトリ(層1) | 登録時にメモリへプリロードされる |
| マーケットプレイス — Git / MySQL / Nacos / classpath(層2) | バックエンドによってメモリへプリロードされる |
| `workspace/skills/` 共有(層3) | 登録時にメモリへプリロードされる |
| `<userId>/skills/` ユーザーごと(層4) | SKILL.md はプリロードされる;他のファイルは `AbstractFilesystem` を通じてオンデマンドで読み取られる(ユーザーごとの名前空間+サンドボックスルーティングは自動的に尊重される) |

エージェントにはこの違いは見えません――`load_skill_through_path` は常に同じように動作します。フォールバックチェーンは「メモリ内ヒット → ファイルシステム読み取り → 実際に利用可能なすべてのパスの列挙付きエラー」であるため、誤ったパスは行き止まりではなく有用な一覧を返します。

### `<files-root>` とシェル実行

スキルがスクリプト(例:`scripts/run-checks.sh`)を同梱している場合、エージェントは `execute_shell_command` 経由でそれらを呼び出すために絶対パスを必要とします。そのパスは、各スキルエントリの `<files-root>` 要素から来ます。解決の仕方はファイルシステムモードによって決まります。

| FS モード(シェルは使えるか?) | ワークスペーススキルの `<files-root>` | マーケットプレイススキルの `<files-root>` |
|----------------------------|--------------------------------|-----------------------------------|
| サンドボックス | `/workspace/skills/<name>` | `/workspace/.skills-cache/<source>/<name>` |
| ローカル+シェル | `<wsRoot>/skills/<name>` | `<wsRoot>/.skills-cache/<source>/<name>` |
| シェルなしのローカル / Composite | (レンダリングされない――シェルツールが登録されていない) | (レンダリングされない) |

したがって、エージェントのシェル呼び出しは常に `execute_shell_command("python3 <files-root>/scripts/foo.py")` の形になります――パスを推測する必要も、ソースごとの違いを覚えておく必要もありません。

### マーケットプレイスのファイルが実際にどこに存在するか

マーケットプレイスのスキルリソースは、インメモリのバイト列として始まります。シェル実行を機能させるために、harness は各推論ステップの前にそれらを `<wsRoot>/.skills-cache/<source>/<name>/` に物質化します。

- ファイル単位の SHA-256 重複排除 — 変更されたファイルのみが書き換えられる
- 孤立したディレクトリ(もはや公開されていないスキル、またはビルダーから削除されたリポジトリのもの)は同じパスでクリーンアップされる
- サンドボックスモードでは、`.skills-cache` はデフォルトのワークスペース投影ルートに含まれるため、ステージングされたツリーは `workspace/skills/` と一緒にサンドボックス起動時(および内容変更時)にハイドレートされる

ワークスペーススキル(層3 / 層4)はステージングを必要としません――すでにワークスペースツリーの中に存在するためです。

2つのリポジトリが同じ `getSource()` を報告する場合、2つ目は警告ログとともに自動的にサフィックスが付けられます(`<source>_2`、`<source>_3`、…)。これにより、パスとスキル ID が決して衝突しないようにしています。

## サンドボックスでスキルを実行する

[サンドボックスモード](/v2/ja/docs/harness/filesystem#モード2サンドボックスsandboxfilesystemspec-ファミリー) では、すべてのファイル操作とシェルコマンドが分離されたコンテナ内で実行されます――ホストは無傷のままです。これは問題を生みます:スキルのスクリプト(`scripts/run-checks.sh`、`scripts/foo.py` など)はホスト上で書かれていますが、エージェントはそれらをコンテナ内で実行しなければなりません。Harness は「物質化 → 投影 → コンテナ内実行」という三段階のパイプラインで、これを透過的にします。以下で分解して説明します。

### どのスキルがサンドボックスに行き着くか

コンテナ内で実行できるスキルには2つのクラスがあり、それぞれステージングされるタイミングが異なります。

| ソース | サンドボックスに入る前どこに存在するか | サンドボックス内のパス |
|--------|-----------------------------------|-------------------------|
| ワークスペーススキル(層3 `workspace/skills/`、層4 `<userId>/skills/`) | すでにワークスペースツリー内にある | `/workspace/skills/<name>` |
| マーケットプレイススキル(層1 プロジェクトグローバル、層2 Git / MySQL / Nacos / classpath) | インメモリのバイト列として始まる | `/workspace/.skills-cache/<source>/<name>` |

### ステップ1:マーケットプレイスのスキルをホストに物質化する

マーケットプレイスのスキルリソースはインメモリのバイト列として届きます――シェルはそれらを直接実行できません。各推論ステップの前に、`MarketplaceStager` がそれらをホストの `<wsRoot>/.skills-cache/<source>/<name>/` に書き込みます。

- **ファイル単位の SHA-256 重複排除** — 変更されたファイルのみが書き換えられる;変更のないものはスキップされる。
- **孤立のクリーンアップ** — もはや公開されていないスキルや、ビルダーから削除されたリポジトリによって残されたディレクトリは、同じパスで削除される。
- **実行ビットの復元** — 取り込みはリソースを文字列に変換し POSIX モードを破棄するため、ステージャーはヒューリスティックに `+x` を再導出します:バイト0のシェバン(`#!`)、または既知のスクリプト拡張子(`.sh`/`.bash`/`.py`/`.rb`/`.pl`/`.js`/`.mjs`)は実行ビットを追加します(`chmod +x` のセマンティクスに従い――すでに読み取りビットを持つものだけが実行ビットを得る)。純粋な静的アセット(`.json`/`.md`/`.txt`)は 644 のままです。

ワークスペーススキル(層3 / 層4)はこのステップをスキップします――すでにワークスペースツリーの中に存在するためです。

### ステップ2:ワークスペースをサンドボックスに投影する

サンドボックスの `start()` 時に、harness はワークスペースの「静的アセット」を tar 化し、コンテナの `/workspace` にハイドレートします。デフォルトの投影ルート(`workspaceProjectionRoots`)は、スキルが必要とするちょうど2つのディレクトリをカバーします。

```
AGENTS.md  skills/  subagents/  knowledge/  .skills-cache/
```

したがって、`workspace/skills/`(`<userId>/skills/` を含む)とステップ1で生成された `.skills-cache/` が一緒にハイドレートされます。投影は、含まれるすべてのファイルに対して1つの全体的な SHA-256 を計算します;前回の実行と一致すればハイドレーションはスキップされます――そのため、繰り返しの `call()` は同一のファイルを再転送せず、内容が変わったときにのみ再入力されます。

調整可能な項目(`DockerFilesystemSpec` / `KubernetesFilesystemSpec` / その他のサンドボックス spec 上):

| メソッド | 効果 |
|--------|--------|
| `workspaceProjectionRoots(List)` | 投影されるルートをカスタマイズする(デフォルトには `skills`、`.skills-cache` が含まれる) |
| `workspaceProjectionEnabled(false)` | 投影を完全に無効化する――オフにすると、サンドボックス内にスキルファイルが存在しなくなり、スクリプトは実行できない |

### ステップ3:コンテナ内でスクリプトを実行する

サンドボックスモードでは、`<available_skills>` ブロック内の各スキルの `<files-root>` は、**コンテナ内**のプレフィックスでレンダリングされます。

| スキルの種類 | `<files-root>` |
|------------|----------------|
| ワークスペーススキル | `/workspace/skills/<name>` |
| マーケットプレイススキル | `/workspace/.skills-cache/<source>/<name>` |

そのため、エージェントは単に次のように発行します。

```
execute_shell_command("python3 /workspace/skills/code-reviewer/scripts/run-checks.sh <target>")
```

このコマンドはコンテナ内で実行され、投影されたまさにそのファイルを読み取ります。エージェントは、あるスキルがどの層から来たかを知る必要はありません――フレームワークがプレフィックスを計算します。

> サンドボックスバックエンドがワークスペースを非デフォルトの場所にマウントしている場合(例:AgentRun は `/home/agentscope/workspace` を使用)、`<files-root>` のプレフィックスはそれに応じて変わり、エージェントはやはり正しい絶対パスを得ます。

### 呼び出しをまたいでスクリプトの副作用を永続化する

スクリプトが依存関係をインストールしたり成果物を生成したりして(`npm install`、`pip install`、ビルド出力)、それらを次の `call()` でも残したい場合は、サンドボックスに[スナップショット](/v2/ja/docs/harness/filesystem#スナップショット戦略)(`snapshotSpec(...)`)を与えてください。スナップショットは `/workspace` 全体をキャプチャします;同じスコープキーへの次の呼び出しは、まずスナップショットを復元し、その上に投影を重ねるため、インストール済みの依存関係を再インストールする必要はありません。

### 注意:SKILL.md を読むのにサンドボックスは不要

よくある混乱の元:スキルを**読む**こと(`load_skill_through_path` が `SKILL.md` / `references/` を取得すること)は、メモリまたはホストのファイルシステムを経由し、サンドボックスとは無関係です;サンドボックスが必要になるのは、**シェル経由でスクリプトを実行する**場合だけで、そのファイルが実際にコンテナ内に存在する必要があります。したがって、投影が無効になっていても、あるいはスクリプトを一切同梱しないスキルであっても、エージェントはその指示とリファレンス資料を通常どおり読むことができます。

## ヒント

**`description` がエージェントがあなたのスキルを使うかどうかを決めます。** エージェントは最初、名前と description しか見ずに、詳細をロードするかどうかを判断します。「Data-analysis tool」は「Use when the user asks for stats, reports, or trend charts」よりもはるかに役に立ちません。

**`SKILL.md` は簡潔に保つ。** 目安として2千トークン以下にし、リファレンス資料は `references/` の下に、スクリプトは `scripts/` の下に置きます。エージェントはそれらをオンデマンドで読みます。

**SKILL.md とスクリプトでは相対パスを使う。** 抽象ファイルシステムの多層分離のため、リソースやスクリプトを参照するときは常に SKILL.md からの相対パス(例:`scripts/run.py`、`references/guide.md`)を使ってください。`/workspace/scripts/run.py` のような絶対パスをハードコード**しない**でください。フレームワークは、有効なファイルシステムモードに基づいて各スキルに対応する正しい `<files-root>` の絶対パスプレフィックスを自動的に生成し、エージェントはシェル実行時にそれを使ってフルパスを組み立てます。絶対パスのハードコードは、スキルを特定のファイルシステムモードでしか動作しないものにしてしまいます。

**マーケットプレイスには汎用的な能力を、ワークスペースにはプロジェクト固有のものを。** コードレビュー、表分析 → 共有メンテナンスのためにチームの Git へ。社内 RPC の慣習、プロジェクトの命名規則 → コードとバージョンを一致させるために `workspace/skills/` へ。

**ユーザーごとのディレクトリは「上書き+補強」のためのものであり、主たる保管場所ではありません。** 重要なスキルはすべてのユーザーに見えるようにしておいてください。

**自己学習は順番に有効化する**:誰もスキルを新しく書いていないのに curator を先に動かしても意味がありません。まず `enableSkillManageTool` から始め、次に昇格ゲートを追加し、最後に curator を追加してください。

## 関連ページ

- [Workspace](/v2/ja/docs/harness/workspace) — `skills/` の全体レイアウト
- [Filesystem](/v2/ja/docs/harness/filesystem) — マルチテナント分離とユーザーごとのバケット分け
- [Architecture](/v2/ja/docs/harness/architecture) — スキルセットが各推論ステップでどう再構築されるか
