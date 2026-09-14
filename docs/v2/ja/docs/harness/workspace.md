---
title: "ワークスペース"
description: "エージェント定義と進化の信頼できる唯一の情報源:ディレクトリレイアウト、ワークスペースと API の等価性、ネイティブなマルチテナント分離、ファイルシステムモード、主要な内容の詳細解説"
---

## 設計思想

ワークスペースは、`HarnessAgent` における**エージェント定義と進化の信頼できる唯一の情報源**です。エージェントが何であるかを定義するすべてのもの、そしてエージェントが時間とともに学習するすべてのものが、ここにプレーンな Markdown / JSON ファイルのディレクトリとして存在します——コードのあちこちに散らばることも、特定のデータベーステーブルに固定されることもありません。

4つの指針となる考え方:

**1. エージェント定義と長期的な進化の両方に対する信頼できる情報源であること。**

エージェントの*定義*——エージェントが何者で、どう振る舞うか——は、ワークスペース内で完全に宣言できます:

| 定義する内容 | ファイル |
|----------------|------|
| ペルソナ、振る舞いのルール、システム指示 | `AGENTS.md` |
| ドメイン知識 | `knowledge/KNOWLEDGE.md` + 参照ファイル |
| スキル(再利用可能な能力パッケージ) | `skills/<skill-name>/SKILL.md` |
| サブエージェントの宣言 | `subagents/<agent-id>.md` |
| ツール許可リスト + MCP サーバー | `tools.json` |

> **すべてのワークスペース設定ファイルは任意です。** すべてのファイルには、完全に等価な API の対応物があります:同じ設定を、ビルダーメソッド(`.systemPrompt(...)`、`.skill(SkillDeclaration...)`、`.subagent(SubagentDeclaration...)`、`.toolsConfig(...)` など)経由で渡すこともできます。ワークスペースと API は常に等価であり——どちらを使うかは完全にあなた次第です。
>
> **では、なぜワークスペースなのか?** 定義を(コードではなく)ファイルとして表現することこそが、1つのエージェントをネイティブにマルチテナント化する方法だからです:*同じ*エージェントロジックが、ユーザーごとの上書きディレクトリを配置するだけで、ユーザーごとに*異なる*ペルソナ、ナレッジベース、スキルセットを持てます——コードの分岐も、別々のデプロイも不要です。詳細は下記の [1つのエージェントロジックをユーザーごとにカスタマイズする](#1つのエージェントロジックをユーザーごとにカスタマイズする) を参照してください。

エージェントの*進化*——エージェントがセッションをまたいで学習・蓄積するすべてのもの——は、明示的なライフサイクル管理を必要とせず、自動的にワークスペースへ保存されます:

- **長期記憶**(`MEMORY.md` + `memory/`)—— 会話から抽出された事実。バックグラウンドタスクによって維持・圧縮され、各ターンに注入されます。
- **自己学習スキル**(`skills/`)—— エージェントは成功パターンから新しいスキルを起草します。任意のレビューゲートを経て再利用可能な能力になり、その後バックグラウンドのキュレーターが未使用のものを経年劣化させる/アーカイブします。
- **プラン**(`plans/`)—— Plan Mode 中に書かれたプランは永続化され、呼び出しをまたいで残ります。「考える」ことを「実行する」ことから切り離した状態に保ちます。
- **オフロードされたツール結果**(圧縮)—— サイズが大きすぎるツール出力はディスクへ書き込まれ、コンテキスト内では head/tail プレビュー + `read_file` へのポインターに置き換えられます。これにより、エージェントはプロンプトを肥大化させることなく、後で読み直せます。
- **セッションログ**(`agents/<agentId>/sessions/`)—— 決して圧縮されない完全な会話ログで、いつでも問い合わせ可能です。

進化データはデフォルトで長期にわたって保持されます:メモリは無期限に蓄積され、セッションログは追記専用で自動的にパージされることはありません。各チャネルがどのように生成・維持されるかについては、下記の [エージェントはどのように進化するか](#エージェントはどのように進化するか) で詳しく説明します。

(呼び出しごとの揮発性の*ランタイムコンテキスト*——`AgentState`——はこのリストには**含まれません**:これは進行中の会話を再開するためのスナップショットであり、`AgentStateStore` に別途永続化され、決してワークスペースには置かれません。下記の考え方2のコールアウトを参照してください。)

**2. コンテンツは3つのライフサイクルに分かれており、それぞれ明確に区別されます。**

| 種類 | 書き込み元 | 読み取り元 | 例 |
|------|------------|---------|----------|
| **静的アセット**(エンジニアが編集する) | あなた/あなたのチーム | フレームワークが各ターンでシステムプロンプトに注入するか、呼び出し時にオンデマンドで読み取る | `AGENTS.md`、`knowledge/`、`skills/`、`subagents/`、`tools.json` |
| **ランタイムファイル**(呼び出しごとに書き換えられる) | フレームワーク/エージェント | フレームワークが次の呼び出し時に復元する | `agents/<agentId>/sessions/`、`agents/<agentId>/tasks/`、`plans/` |
| **長期記憶**(セッションをまたいで蓄積される) | エージェント + バックグラウンドタスク | フレームワークがシステムプロンプトに注入する + エージェントがツール経由で問い合わせる | `MEMORY.md`、`memory/YYYY-MM-DD.md` |

これらは、単にデプロイの利便性のために1つのツリーに存在しています(ディレクトリをコピーすれば、完全なエージェントが手に入ります)。フレームワーク内部では、それぞれ異なる読み書きの経路をたどります。

> **`AgentState` はワークスペースのコンテンツではありません——両者を混同しないでください。** エージェントが会話の途中から再開するために必要な、進行中のコンテキスト(会話バッファ、ローリングサマリー、パーミッション/ツール/タスク/Plan-Mode のサブコンテキスト、加えてアクティブなプランファイルなどのワークスペース内の成果物を指す*メタデータ*)は、単一の `AgentState` ドキュメントとして、独立したサブシステムである**`AgentStateStore`**(デフォルトは `~/.agentscope/state/<agentId>/`。ワークスペースツリーの完全に外側)にシリアライズされます。この分離は意図的なものです:ワークスペースは永続的な*ファイル成果物*(決して圧縮されないセッションログ、プランの Markdown、タスクレコード、メモリ)を保持し、一方 `AgentState` は揮発性の*ランタイムコンテキスト + ワークスペースのメタデータ*を保持します。2つのストア、2つのライフサイクル——[Context](../building-blocks/context.md) を参照してください。

**3. ネイティブにマルチテナントであること。** ワークスペースのデータ(メモリ、セッション、タスク、スキル、サンドボックスの状態)は、単一の `IsolationScope` によってバケット化されます——アプリケーションレベルの分割コードは不要です。このスコープが、誰が1つのバケットを共有するかを決めます:

| `IsolationScope` | 1つのバケットを共有する対象 | 典型的な用途 |
|------------------|----------------------|-------------|
| `SESSION` | 各 `sessionId` が完全に分離される | 会話ごとの分離;使い捨てのサンドボックス |
| `USER`(デフォルト) | 同じ `userId` のすべてのセッション | あるユーザーのセッションが長期記憶/スキルを共有する(`userId` が無い場合は `SESSION` にフォールバックする) |
| `AGENT` | このエージェントのすべてのユーザー & セッション | 共有ナレッジベース型のエージェント |
| `GLOBAL` | ストアインスタンス全体で1つのバケット | 慎重に使うこと——すべてのエージェント/ユーザーが同じスロットを奪い合う |

選択したスコープは、ファイルシステムモードごとに異なる形で具体化されます(ローカルディスク上のパスプレフィックス、共有ストアの KV 名前空間、サンドボックスの状態スロット)。完全なセマンティクス、フォールバックルール、並行性に関する注意点は [Filesystem — IsolationScope](./filesystem.md#isolationscope-ユーザーとレプリカ間のバケット分け) を参照してください。

> `IsolationScope` は、上記の**ワークスペース/ファイルシステム**のバケットを管理します。`AgentState` は独自の、直交するアドレッシングを持っています:スコープに関わらず、`AgentStateStore` の中では常に `(userId, sessionId)` によってキー付けされます。

単一の `HarnessAgent` インスタンスで、ユーザー間のデータ漏洩を一切起こすことなく、数千の同時ユーザーに対応できます。

**4. ワークスペースはファイルシステムから分離されている。** 同じディレクトリレイアウトは、ローカルディスク、共有 KV ストア(Redis / JDBC)、サンドボックスコンテナの3つの場所のいずれかに着地します。この分離こそが、エージェントのコードに触れることなくデプロイ形態を切り替えられる理由です。3つのモードについては [Filesystem](./filesystem.md) を参照してください。

## ワークスペースのディレクトリレイアウト

```
.agentscope/workspace/
├── AGENTS.md                    ← 静的:ペルソナ + 振る舞いのルール
├── MEMORY.md                    ← 長期記憶:整理された長期的な事実
├── tools.json                   ← 静的:MCP サーバー + ツールの許可/拒否(任意)
├── memory/                      ← 長期記憶:追記専用の日次事実ログ
│   └── YYYY-MM-DD.md
├── knowledge/                   ← 静的:ナレッジのエントリ + 参照ファイル
│   ├── KNOWLEDGE.md
│   └── ...
├── skills/                      ← 静的:スキルごとに1つのサブディレクトリ、それぞれに SKILL.md
│   └── <skill-name>/SKILL.md
├── subagents/                   ← 静的:サブエージェントの仕様(ファイル名 = agent_id)
│   └── <agent-id>.md
├── plans/                       ← ランタイム:Plan Mode 中に書かれるプランファイル
│   └── PLAN.md
└── agents/<agentId>/            ← ランタイム:各エージェントのランタイムルート
    ├── sessions/                ← ランタイム:セッションインデックス + 決して圧縮されないログ
    │   ├── sessions.json
    │   └── <sessionId>.log.jsonl
    └── tasks/                   ← ランタイム:サブエージェントのバックグラウンドタスクレコード
        └── <sessionId>.json
```

> **このツリーは*論理的な*レイアウトであり、固定されたディスク上のパスではありません。** ここでは `.agentscope/workspace/...` として描かれていますが、それはあくまでデフォルトのローカル配置にすぎません。まったく同じレイアウトが、物理的には**ローカルディスク**、**リモートの分散ストア**(`RemoteFilesystemSpec` 経由の Redis / JDBC / OSS)、あるいは**サンドボックスコンテナへの投影**(`SandboxFilesystemSpec`)のいずれにも存在しえます——以下の相対パスは3つすべてで同一であり、変わるのはバックエンドのストアだけで、エージェントのコードは変わりません。バックエンドのストアは [Filesystem](./filesystem.md) で選択してください。本ドキュメントの内容はすべて、この論理レイアウトに基づいて書かれています。

**実際に書く必要があるのは `AGENTS.md` だけです**(省略してもエージェントは動作します——ペルソナの注入が失われるだけです)。それ以外はすべて、対応する機能を有効にすると現れます:

- メモリ圧縮を有効にする(`.compaction(...)`)→ `memory/` + `MEMORY.md`
- サブエージェントの仕様を配置する → `subagents/`
- スキルをインストールする → `skills/`
- Plan Mode を有効にする → `plans/`
- 何らかの `call()` を実行する → `agents/<agentId>/`

## ビルダー設定

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("MyAgent")
    .model(model)
    .workspace(Paths.get(".agentscope/workspace"))   // 省略した場合 → 下記の解決順序を参照
    .additionalContextFile("SOUL.md")                // 任意のワークスペース相対パス。全文がインラインされる
    .additionalContextFile("PREFERENCES.md")
    .maxContextTokens(8000)                          // MEMORY 注入の予算
    .build();
```

### ワークスペースの解決順序

`workspace(...)` が明示的に呼ばれない場合、`build()` は次の優先順位(最も高いものから)でワークスペースディレクトリを解決します:

| 優先順位 | ソース | 備考 |
|----------|--------|-------|
| 1 | `workspace(Path)` / `workspace(String)` | 明示的なビルダーの値。すべてに優先する |
| 2 | `agentscope.workspace` システムプロパティ | `-Dagentscope.workspace=/data/workspace` |
| 3 | `AGENTSCOPE_WORKSPACE` 環境変数 | `export AGENTSCOPE_WORKSPACE=/data/workspace` |
| 4 | デフォルト | `${user.dir}/.agentscope/workspace` |

システムプロパティと環境変数は、主に**イメージのパッケージング / コンテナデプロイ**のために存在します:パスをアプリケーションコードの外に出し、イメージのビルド時またはコンテナの起動時に注入します。例:

```dockerfile
ENV AGENTSCOPE_WORKSPACE=/data/agent-workspace
```

```yaml
# k8s / docker-compose
env:
  - name: AGENTSCOPE_WORKSPACE
    value: /data/agent-workspace
```

> 空白の値(例:`"   "`)は未設定として扱われ、次のレベルへフォールスルーします。

最小限の `AGENTS.md` の骨組み:

```markdown
# MyAgent

あなたは XX アシスタントです。次の振る舞いガイドラインに従ってください。

## Behavior
- ...
- ...
```

オプトアウトのスイッチ(本番環境では稀ですが、デバッグや自己管理に便利です):

| メソッド | 無効化されるもの |
|--------|------------------|
| `disableWorkspaceContext()` | システムプロンプトへの注入(`AGENTS.md` / `MEMORY.md` / `knowledge/`) |
| `disableMemoryHooks()` | メモリの flush + バックグラウンドメンテナンス。システムプロンプトから「自動的に抽出された」Persistence の行も取り除かれる。`disableMemoryTools()` と組み合わせると、`<memory_context>`(`MEMORY.md`)の注入もスキップされる |
| `disableMemoryTools()` | `memory_search` / `memory_get` / `memory_save` / `session_search` ツール。システムプロンプトから Memory Recall とツールベースの Persistence ガイダンスも省略される |
| `disableSubagents()` | サブエージェントのサブシステム全体 |
| `disableDynamicSkills()` | ターンごとのスキル再マージ。ビルド時のワンショットマージにフォールバックする |
| `disableToolsConfig()` | `tools.json` の読み取り |
| `disableSessionPersistence()` | AgentState の自動永続化 |

## ワークスペースの内容がどう読み込まれるか

ワークスペースは論理的なレイアウトであるため(上記のコールアウトを参照)、「読み込み」はプレーンなローカルディレクトリを前提にすることは決してありません——すべての読み取りは設定された `AbstractFilesystem` を経由するため、ファイルがローカルディスク、リモートストア、サンドボックスのいずれにあっても同じロジックが機能します。下記の[二層読み込み](#二層読み込みファイルシステム優先-ローカルフォールバック)が、このバックエンドストアからの独立性を具体化するものです。各モードが物理的にパスをどう解決するかは [Filesystem](./filesystem.md) が扱います。

### ターンごとのシステムプロンプト組み立て

推論ステップのたびに、`WorkspaceContextMiddleware`(`io.agentscope.harness.agent.middleware`)が次のセクションを組み立て、ビルダーで設定した `sysPrompt` に**追記**して最終的なシステムメッセージを構成します:

| セクション | ソース | 予算制限 |
|---------|--------|----------|
| `## Session Context` | テンプレート(今日の日付、OS、ワークスペースの絶対パス、一時ディレクトリ、現在の `sessionId`) | なし |
| `## Domain Knowledge` / `## Memory Recall` / `## Memory Persistence` ガイダンス | 組み込みテンプレート(メモリの使い方とナレッジの参照方法をモデルに教える)。`disableMemoryTools()` / `disableMemoryHooks()` が設定されている場合、メモリ関連のセクションは省略/削減される | なし |
| `## Workspace` セクション | テンプレート。**ファイルシステムモードごとに分岐する**(下記参照)——ローカル/サンドボックス/リモートストアのいずれで動作しているかをモデルに伝える | なし |
| `## Workspace Files (Injected)` の通知 | フレームワークが、ワークスペースから次のファイルを自動的に読み込み、`<loaded_context>` XML ブロックへ入れる | 下記参照 |
| `<agents_context>` | `AGENTS.md` の全文 | 無制限 |
| `<memory_context>` | `MEMORY.md`。残り予算を超える場合は文字数で切り詰められ、「古いエントリには memory_search を使ってください」という注記が付く(ツールが無効な場合はプレーンな切り詰め注記になり、メモリツールと hook の両方が無効な場合は完全に省略される) | `maxContextTokens`。デフォルト 8000 |
| `<domain_knowledge_context>` | `knowledge/KNOWLEDGE.md` の全文 + `knowledge/` 配下のすべてのファイルの一覧 | 無制限(カタログとしてはファイル名のみ) |
| `<x_md>` / `<y_md>` | `additionalContextFile("X.md")` で追加したもの | 無制限 |

重要なポイント:

- **毎ターン再構築されます。** `AGENTS.md` や `MEMORY.md` を編集すれば、次の `call()` がその変更を取り込みます——再起動もリビルドも不要です。
- **`MEMORY.md` は注入前にトークン数が見積もられます。** 予算を超えた分は文字数で切り詰められ、末尾に `memory_search` の利用を促す注記が付きます。
- **`knowledge/` はディレクトリのインデックス + エントリファイルです。** ツリー全体がプロンプトに入ることは決してなく——`KNOWLEDGE.md` とパスの一覧だけが入ります。エージェントは必要なものを `read_file` で読み取ります。

### 二層読み込み(ファイルシステム優先 + ローカルフォールバック)

プロンプトに注入される「ファイル」(`AGENTS.md` / `MEMORY.md` / `knowledge/KNOWLEDGE.md` / `additionalContextFile`)はすべて、`WorkspaceManager.readWithOverride()` が**二層読み込み**を行います:

```
1. 設定された AbstractFilesystem に尋ねる:この相対パスを持っているか?
   ├─ yes → その内容を返す(「オーバーライド」層)
   └─ no  → ステップ2へフォールスルー
2. workspace.resolve(relativePath) でローカルディスクを読む
```

書き込みは常に層1(ファイルシステムストア)を経由し、ローカルディスクへ直接書き込まれることは決してありません。

このパターンが真価を発揮するのは**共有ストアモード**です:最初のレプリカは、チームで Git 同期された `AGENTS.md` テンプレートがローカルディスクに用意された状態で起動するため、即座に動作します。後で(例えば管理コンソールのエディタからの)何らかのオーバーライドが共有 KV に入ると、以降どのレプリカの次の `call()` も最新版を読み取ります。テンプレートはフォールバック、リモートのオーバーライドが真実です。

### 複数ユーザーが1つのワークスペースを共有する場合のオーバーライド優先順位

`RuntimeContext.userId` はマルチユーザーのキーです——これにより、1つのエージェントインスタンスが、混線することなく多くのユーザーに対応できます。

**実行時データ**(セッション / タスク / メモリ)については、フレームワークは設定された `NamespaceFactory` を通じてパスにプレフィックスを付けます(ローカルモード → パスプレフィックス、リモートモード → KV 名前空間、サンドボックスモード → 状態スロット)。詳細は次のセクション「ランタイムデータとメモリはどのように保存されるか」を参照してください。

**静的アセット**(特に `skills/` と `subagents/`)については、ユーザーごとのディレクトリがワークスペース共有バージョンを**上書き**します:

```
workspace/
├── skills/code-reviewer/SKILL.md     ← 共有(全員に見える)
├── subagents/researcher.md           ← 共有
└── alice/
    ├── skills/
    │   └── code-reviewer/
    │       └── SKILL.md              ← alice にのみ見える;共有版を上書きする
    └── subagents/
        └── researcher.md             ← alice にのみ見える
```

`RuntimeContext.userId="alice"` で呼び出されると、フレームワークはまず `alice/skills/code-reviewer/` を探し、`skills/code-reviewer/` にフォールバックします。下位層にしかないスキルは見え続けます。同名の衝突だけが上位層によって覆い隠されます。完全な優先順位の表は [Skills — 競合の解決](./skill.md#競合の解決) を参照してください。

#### 1つのエージェントロジックをユーザーごとにカスタマイズする

このオーバーライドの仕組みこそが、コードをフォークしたり別々のデプロイを立ち上げたりすることなく、**単一の `HarnessAgent` インスタンスがテナントごとに異なるエージェントのように振る舞う**ことを可能にします。1つのバイナリ、1つのエージェント定義を出荷し、各ユーザーはその上に自分専用のレイヤーを持ちます:

| ユーザーごとのレイヤー | カスタマイズする内容 | 解決方法 |
|----------------|--------------------|------------|
| `<userId>/AGENTS.md`(オーバーライド経由) | そのユーザー向けのペルソナ/振る舞い | 二層読み込みの上位層(共有の `AGENTS.md` がフォールバック) |
| `<userId>/knowledge/` | そのユーザーが閲覧を許可されているドメイン知識 | ユーザーごとのディレクトリ。共有の `knowledge/` をベースとする |
| `<userId>/skills/` | そのユーザーだけがアンロックする能力 | 同名の共有スキルを上書きする;固有のものは積み重なる |
| `<userId>/subagents/` | そのユーザーだけが spawn できるサブエージェント | 同名の共有仕様を上書きする |
| 実行時データ(メモリ/セッション/タスク) | そのユーザーの蓄積された進化 | `userId` ごとに名前空間化される(パスプレフィックス/KV 名前空間/サンドボックススロット) |

結果として得られるのは**同時に2層のマルチテナンシー**です:*定義*はユーザーごとに異なり(オーバーライドディレクトリ経由)、*進化*はユーザーごとに分離されます(名前空間経由)。共有のベースは全員に共通のまま保たれ、各ユーザーのカスタマイズと学習済みの状態は、同じエージェントプロセスからでありながらテナントをまたいで決して漏れません。これが、[任意設定に関するコールアウト](#設計思想) が指し示していた、ファイルベースであることの見返りです:定義がデータである以上、ユーザーごとのカスタマイズは単なる別のファイルにすぎず、別のコードパスではありません。

### 各ファイルシステムモードでの読み込みの振る舞い

ワークスペースは論理的なレイアウトであり、物理的な配置は [Filesystem](./filesystem.md) 次第です。同じディレクトリでも、モードによって読み込まれ方が異なります——以下で説明します。

**モード1・共有ストア(`RemoteFilesystemSpec`)—— テンプレート + リモートオーバーライド**

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("store")
    .model(model)
    .workspace(workspace)
    .distributedStore(store)
    .filesystem(new RemoteFilesystemSpec()
        .isolationScope(IsolationScope.USER))      // userId ごとの名前空間
    .build();
```

- **読み込み方**:各ターンで、`AGENTS.md` / `MEMORY.md` / `tools.json` は、リモートの KV を上位層、ワークスペースのテンプレートを読み取り専用の下位層とするオーバーレイによって提供されます。ローカルの `<workspace>/AGENTS.md` は**読み取り専用のシード**です——初回起動時、またはレプリカ間の同期に使われます。リモートの KV に同じキーの下でユーザーごとのコピーがあれば、リモートが優先されます。
- **ルーティング**:`memory/` / `skills/` / `subagents/` / `knowledge/` / `agents/<id>/sessions/` / `agents/<id>/tasks/` は `IsolationScope` ごとに名前空間化されます(デフォルトは USER → `userId` ごとに1つの名前空間;[Filesystem — IsolationScope](./filesystem.md#isolationscope-ユーザーとレプリカ間のバケット分け) を参照)。
- **ベストプラクティス**:チームで合意した `AGENTS.md` / `knowledge/` / 共有 `skills/` を、テンプレートとして各レプリカのローカルディスクへ git 同期してください。ランタイムの出力(`MEMORY.md`、`memory/`、`agents/<id>/...`)は KV に蓄積させます。

**モード2・サンドボックス(`DockerFilesystemSpec` / K8s / E2B / AgentRun)—— 投影 + ハイドレート**

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("sandbox")
    .model(model)
    .workspace(workspace)
    .filesystem(new DockerFilesystemSpec()
        .image("ubuntu:24.04")
        .isolationScope(IsolationScope.SESSION))
    .build();
```

- **読み込み方**:サンドボックスが起動すると、フレームワークはワークスペースの「静的アセット」(`AGENTS.md`、`skills/`、`subagents/`、`knowledge/`、その他の投影ルート)を tar 化し、コンテナ内の `/workspace` へハイドレートします。`AGENTS.md` などは、引き続き二層読み込み(サンドボックスが先、ホストのテンプレートがフォールバック)に従います。
- **重複排除と差分適用**:投影はコンテンツハッシュで比較されます。変更が無ければスキップされ、変更されたファイルは SHA-256 を使って増分的に書き換えられます。
- **実行時データ**:`MEMORY.md`、`memory/`、`agents/<id>/...` はすべてサンドボックス内に存在します。サンドボックスのスナップショットがこれらを保存するため、同じ `sessionId` での次の `call()` は、`node_modules`、`pip install` の結果、その他すべてを復元します。
- **ベストプラクティス**:コード実行/シェルはホストの外に留めてください。ホストが運ぶのはワークスペースの「シード」(チームで git 同期されたペルソナ + 共有スキル + ナレッジ)だけです。これは、本番環境で信頼できないコードを実行する際のデフォルトモードです。

**モード3・ローカル + シェル(デフォルトの `LocalFilesystemSpec`、または `filesystem(...)` を省略)—— 直接読み書き**

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("local")
    .model(model)
    .workspace(workspace)
    // .filesystem(...) を省略 = ローカル + シェル
    .build();
```

- **読み込み方**:すべてのファイルは `<workspace>/` から直接読み取られます。オーバーレイはありません。`<userId>/skills/` のようなユーザーごとのオーバーライドは、単純なディレクトリプレフィックスの切り替えです。
- **パスの安全性**:デフォルトは `ROOTED` モードです——絶対パスは `workspace` と `project`(シェルの `cwd`)のルート配下でのみ許可され、`..` によるトラバーサルはパスポリシーによって拒否されます。
- **ベストプラクティス**:単一プロセス/ローカル開発/ユニットテスト/信頼できる環境向け。本番環境で信頼できないコードをここで実行しては**いけません**——`execute` はホストの `sh -c` です。

## ランタイムデータとメモリはどのように保存されるか

フレームワークは2つのデータプレーンを自動的に書き込みます——そしてそれらは**2つの異なる場所**に存在します。この2つを区別してください:

| データプレーン | 内容 | 存在場所 |
|------------|-----------|----------------|
| **`AgentState`** | 揮発性のランタイムコンテキスト:チャットバッファ、圧縮サマリー、パーミッション/ツール/タスク/Plan-Mode のコンテキスト、加えてワークスペースの成果物を指すメタデータ | **`AgentStateStore`**——ワークスペースとは**別の**独立したサブシステム(デフォルトは `~/.agentscope/state/<agentId>/`) |
| **ワークスペースのランタイム/長期ファイル** | 永続的な成果物:セッションログ、タスクレコード、`MEMORY.md` + `memory/` | ワークスペースツリー内。物理的な場所はファイルシステムモードに従う |

どちらも手作業で編集することはありません。このセクションの残りでは、2つのプレーンを順に見ていきます。

### エージェントの状態 —— ワークスペースとは別の独立したストア

`AgentState` は `(userId, sessionId)` ごとのランタイムコンテキストであり、意図的に**ワークスペースツリーの外側**に保たれています。`call()` が完了すると、それは JSON にシリアライズされ、設定された [`AgentStateStore`](../../integration/session/index.md) 経由で、その呼び出しの `(userId, sessionId)` をアドレスとして永続化されます。同じ `(userId, sessionId)` での次の `call()` がそれを読み戻します。

デフォルトでは `HarnessAgent` は、ワークスペースの**外側**の `~/.agentscope/state/<agentId>/` をルートとする `JsonFileAgentStateStore` を使用します(ベースは `agentscope.state.home` システムプロパティで上書き可能)。これにより、ランタイムの状態はワークスペースのデータから分離された状態に保たれます。別のストアを設定するには `.stateStore(...)` を使用してください。

### セッションログ(これらは*実際に*ワークスペースファイルである)

`AgentState` とは異なり、ワークスペースは `agents/<agentId>/sessions/` 配下に**会話ログ**を保持します:

- **`sessions.json`** —— エージェントのセッションインデックス(キー = sessionId、値 = サマリー + updatedAt)。
- **`<sessionId>.log.jsonl`** —— **決して圧縮されない**生の会話ログ。追記専用です。`session_search` / `session_history` がこれを問い合わせます。

> デフォルトの `JsonFileAgentStateStore` は単一マシンのみに対応しています。マルチレプリカの本番環境では、分散ストア(`RedisAgentStateStore` / `MysqlAgentStateStore` / …)に切り替える必要があります。分散状態ストアに切り替えずに `filesystem(SandboxFilesystemSpec)` や `filesystem(RemoteFilesystemSpec)` を設定した場合、`build()` は `IllegalStateException` を送出します——ランタイムの状態を単一障害点にしないための、強制的なリマインダーです。

完全な詳細(復旧フロー、ノードをまたいだ継続、`(userId, sessionId)` によるアドレッシング)は [Context](../building-blocks/context.md) にあります。

### メモリ(長期記憶)

2つの層:

```
workspace/
├── MEMORY.md                  ← 整理された長期記憶。各ターンに注入される
└── memory/
    └── YYYY-MM-DD.md          ← 追記専用の日次事実ログ(重複排除なし)
```

書き込みの経路:

- 圧縮の前に、`MemoryFlushMiddleware` が会話のプレフィックスから新しい事実を抽出し、`memory/YYYY-MM-DD.md` に追記します。
- スロットルされたバックグラウンドタスクが定期的に `memory/` をマージ/重複排除し、`MEMORY.md` を書き換えます。
- `MEMORY.md` は毎ターン、(予算内で)システムプロンプトに注入されます。

読み取りの経路:

- フレームワークが `MEMORY.md` 自体を読み取ります(二層;ファイルシステムが優先)。
- エージェントは、古いエントリについて `memory_search` / `memory_get` を能動的に呼び出せます。[Memory](./memory.md) を参照してください。

### 名前空間分離が物理的な場所にどう対応するか

`WorkspaceManager.resolveRuntimeDataPath()` は、現在の `RuntimeContext` がどの名前空間にマッピングされるかを `NamespaceFactory` に尋ねます。その名前空間は、ファイルシステムモードごとに具体化されます:

| モード | 実行時データの物理的な場所 | マルチユーザー分離の仕組み |
|------|----------------------------------|-------------------------------|
| ローカル + シェル | `<workspace>/<userId>/agents/<agentId>/...` | パスプレフィックス |
| 共有ストア(KV) | KV キープレフィックス。例:`namespace=alice/memory/...` | KV 名前空間 |
| サンドボックス | サンドボックスの状態スロットキー(`IsolationScope.USER` の場合) | サンドボックスインスタンスの分離 |

`userId` が無い場合、シングルテナントのデフォルトが適用され、全員が1つのルートを共有します。

> **静的アセット** vs **実行時データ**:`AGENTS.md`、`tools.json`、`knowledge/` などは、userId ごとに自動的に分割**されません**——これらはユーザー間で共有され、区別する唯一の方法はユーザーごとのオーバーライドディレクトリ(`<userId>/skills/...`、`<userId>/subagents/...`)を追加することです。`userId` に従うのは実行時データ(セッション、タスク、メモリ)です。

## エージェントはどのように進化するか

静的な定義を超えて、ワークスペースはエージェントの*蓄積された経験*が着地する場所です。5つのチャネルが自動的に蓄積していきます——対応する機能を有効にすれば、データはワークスペースに溜まり始め、他のすべてとまったく同じようにテナントごとに分離されます。それぞれに専用の詳細ページがあります。この表がその索引です:

| チャネル | 存在場所 | 有効化方法 | どう蓄積するか | 詳細 |
|---------|----------------|------------|----------------|-----------|
| **長期記憶** | `MEMORY.md` + `memory/YYYY-MM-DD.md` | `.compaction(...)` | 圧縮の前に `MemoryFlushMiddleware` が会話のプレフィックスから事実を抽出する → スロットルされたバックグラウンドタスクがそれらを `MEMORY.md` にマージ + 重複排除し、毎ターン再注入する | [Memory](./memory.md) |
| **自己学習スキル** | `skills/`、`skills/_drafts/`、`skills/.archive/` | `.enableSkillManageTool(...)` | エージェントが `propose_skill` を呼び出し、うまくいったパターンからスキルを起草する → 任意の昇格ゲートがそれを承認する → バックグラウンドのキュレーターが未使用のスキルを stale とマークし(30日)、アーカイブする(90日) | [Skills — 自己学習ループ](./skill.md#自己学習ループオプション) |
| **プラン** | `plans/PLAN.md` | `.enablePlanMode()` | 読み取り専用の計画フェーズが `plan_write` 経由でプランを書き込む;呼び出しをまたいで永続化され、実行フェーズを駆動し、意図と行動を切り離す | [Plan Mode](./plan-mode.md) |
| **オフロードされたツール結果** | ワークスペース配下の退避ディレクトリ | `.toolResultEviction(...)` | 単一のツール結果が閾値(デフォルト 80K 文字)を超えると、完全な出力がディスクに書き込まれ、コンテキスト内のメッセージは head/tail プレビュー + `read_file` へのポインターに置き換えられる | [Compaction](./compaction.md) |
| **セッションログ** | `agents/<agentId>/sessions/`(ワークスペース) | デフォルトで有効 | すべての `call()` が、決して圧縮されない JSONL ログに追記する;`session_search` / `session_history` がこれを問い合わせる | [Context](../building-blocks/context.md) |

統一的な考え方はこうです:**あなたが何もストレージを配線しなくても、エージェントは実行の合間に改善していきます。** メモリ、スキル、プラン、セッションログ、オフロードされた結果は、すべて単なるワークスペース内のファイルです——このページの他のすべてと同じテナントごとの分離、同じ二層読み込み、同じファイルシステムモードの移植性を得ます。(揮発性の `AgentState` ランタイムコンテキストだけが例外です——これは独立した `AgentStateStore` に存在し、ワークスペースには存在しません;[ランタイムデータとメモリはどのように保存されるか](#ランタイムデータとメモリはどのように保存されるか) を参照してください。)

## 主要なディレクトリの詳細解説

### `skills/`

スキルとは、パッケージ化された能力です——`SKILL.md`(エージェント向けの説明 + 指示)を含むディレクトリで、任意で参照ドキュメントやスクリプトを伴います。

```
skills/code-reviewer/
├── SKILL.md               ← YAML フロントマター(name + description)+ 指示
├── references/style-guide.md   ← 任意。エージェントがオンデマンドで読む
└── scripts/run-checks.sh       ← 任意。エージェントが execute_shell_command 経由で呼び出す
```

4つの登録レイヤー(低 → 高優先度)があります:

1. `projectGlobalSkillsDir(Path)` —— プロジェクトグローバル。例:`~/.agentscope/skills/`
2. `skillRepository(...)` —— マーケットプレイスストア(Git / Nacos / MySQL / classpath)
3. `workspace/skills/` —— ワークスペース共有
4. `<userId>/skills/` —— ユーザーごと(上記すべてを上書きする)

下位層にしかない固有のスキルは見え続けます。同名のスキルは上位層によって覆い隠されます。毎ターン、`DynamicSkillMiddleware` が再マージし、`<available_skills>` ブロック(名前 + 説明のみ)をシステムプロンプトへレンダリングします。エージェントは、関連する場合に `load_skill_through_path` を呼び出して完全な詳細を取得します。完全な仕組みは [Skills](./skill.md) にあります。

### `subagents/`

各 `<agent-id>.md` はサブエージェントの宣言です(ファイル名 = `agent_id`)。YAML フロントマターは、アイデンティティ、モデル、ツールの許可リスト、ワークスペース戦略を記述します。本文はサブエージェントのシステムプロンプトです。

```markdown
---
description: コードレビューの専門家。ユーザーが PR のレビュー、スタイルに関するフィードバック、静的チェックを必要とするときに使用する。
workspace:
  mode: isolated         # isolated(デフォルト) | shared
model: qwen3-max         # 任意;デフォルトは親を継承する
tools: [read_file, grep_files]   # 任意;継承したツールに対する許可リスト
---

あなたはコードレビューのサブエージェントです……
```

読み込み:`AgentSpecLoader` はビルド時に `workspace/subagents/*.md` を**非再帰的に**スキャンし、`.subagent(SubagentDeclaration...)` でプログラムから登録した宣言とマージします。メインエージェントは `agent_spawn agent_id="reviewer" task="..."` 経由でこれらを呼び出します。
完全な詳細(同期 vs バックグラウンド、リモートサブエージェント、ストリーム転送、タスクストレージ)は [Subagent](./subagent.md) にあります。

### `tools.json`

ワークスペースルートにある JSON ファイルで、`build()` の間に一度だけ読み取られます:

```jsonc
{
  // 許可リスト:空でない場合、リストされたツールだけが残る
  "allow": ["read_file", "grep_files", "execute"],
  // 拒否リスト:リストされたツールは常に除去される(allow より優先される)
  "deny":  ["write_file"],
  // MCP サーバー。名前をキーとする
  "mcpServers": {
    "amap": {
      "transport": "streamableHttp",
      "url": "https://mcp.amap.com/mcp?key=${AMAP_API_KEY}"
    },
    "local-py": {
      "transport": "stdio",
      "command": "python",
      "args": ["mcp_servers/my_server.py"],
      "env": {"PYTHONUNBUFFERED": "1"}
    }
  }
}
```

振る舞いに関する注意:

- **MCP サーバーは、ビルド時に一度だけ Toolkit へ登録されます**;エージェントは、それらが公開するツールを見ることができます。
- **`allow` / `deny` は、すべてのツールが登録された後に適用されます**——Harness の組み込みツール(`read_file` / `memory_search` / `agent_spawn` / …)も含みます。**`allow` でホワイトリストを作る場合は、残しておきたい組み込みツールもリストしてください**。そうしないと、それらも他のすべてと一緒にフィルタリングされてしまいます。
- `${ENV_VAR}` 構文は環境変数を置換します。変数が存在しない場合は警告を出し、空文字列に置換します。
- ファイルを使いたくない場合は?`builder.toolsConfig(ToolsConfig.builder()...)` を直接渡すか、`disableToolsConfig()` で読み取りを完全に無効化してください。
- 共有ストアモードでは、`tools.json` も上記で説明した「リモートが上位、ローカルテンプレートが下位」のオーバーレイに従います。

### `plans/`

Plan Mode で書かれたプランファイルはここに着地します。デフォルトは `plans/PLAN.md` で、`.planFileDirectory("design-docs")` で変更できます。

```
plans/
└── PLAN.md           ← plan_write によって書かれた現在のプラン
```

注:`PlanModeContext`(プランフェーズがアクティブかどうか、現在のプランファイルのパス)は `AgentState` に存在します——これは**ランタイムの状態**であり、`AgentStateStore`(デフォルトは `~/.agentscope/state/<agentId>/`、ワークスペースの外側)経由で永続化されます。`plans/` 配下のファイルは、Markdown のコンテンツそのものだけです。[Plan Mode](./plan-mode.md) を参照してください。

### `agents/<agentId>/`

これは**ランタイムルート**であり、フレームワークが書き込み、手動で編集することはほとんどありません:

```
agents/<agentId>/
├── sessions/
│   ├── sessions.json          ← このエージェントのセッションインデックス
│   └── <sessionId>.log.jsonl  ← 決して圧縮されない生の会話ログ(追記専用)
└── tasks/
    └── <sessionId>.json       ← サブエージェントのバックグラウンドタスクレコード(taskId → TaskRecord)
```

> シリアライズされた `AgentState`(`agent_state`)は、デフォルトではワークスペースには**存在しません**——設定された `AgentStateStore`(デフォルトは `~/.agentscope/state/<agentId>/`)に存在します。ワークスペースに残るのは、上記の会話ログとタスクレコードだけです。

ノードをまたいだ復旧/マルチレプリカのデプロイでは、このデータを共有する必要があります(`RedisAgentStateStore` + `RemoteFilesystemSpec`、または分散状態を持つサンドボックスのいずれか)。[Context](../building-blocks/context.md) と [Filesystem](./filesystem.md) を参照してください。

### `knowledge/`

```
knowledge/
├── KNOWLEDGE.md         ← エントリ/概要。全文がシステムプロンプトに注入される
├── api-reference.md
├── domain-terms.md
└── ...
```

読み込み時:

- `KNOWLEDGE.md` の全文が `<domain_knowledge_context>` に入ります。
- 同じツリー配下の他のファイル(任意の深さ)は、プロンプトに**パスの一覧**だけを寄与します。エージェントは、`read_file` / `grep_files` / `glob_files` でオンデマンドにそれらを読み取ります。

この「詳細はディスクに、索引はプロンプトに」というパターンは、大規模なナレッジベースであってもトークン予算を有界に保ちます。

## ワークスペースへの書き込みに関する安全ルール

`additionalContextFile`、`writeUtf8WorkspaceRelative`、`memory_get` などは**ワークスペース相対パス**を受け付けます。フレームワークは基本的なパストラバーサルの検証を行います(`../../etc/passwd` のような脱出を拒否します)。

ファイルを書き込む必要がある場合は、`java.nio.Files` ではなく**`HarnessAgent#getWorkspaceManager()` を経由してください**——後者は、サンドボックスモードや共有ストアモードでは間違った場所へ書き込んでしまいます(サンドボックス内/KV 内ではなく、ホストディスクに着地します)。例外:ビルド時のブートストラップスクリプト(例:`AGENTS.md` をシードする `initWorkspaceIfAbsent`)——このときはまだランタイムコンテキストが存在せず、意図がローカルテンプレートを書くことであるため、`java.nio.Files` が正しい選択です。

## 関連ページ

- [Architecture](./architecture.md) — システムプロンプトがどのように組み立てられ、各機能がどう協調するか
- [Filesystem](./filesystem.md) — ワークスペースが物理的にどこに存在するか(ローカル/サンドボックス/共有ストア)、`IsolationScope`、マルチユーザー分離
- [Context](../building-blocks/context.md) — `AgentState` と `AgentStateStore` の永続化、ノードをまたいだ復旧
- [Memory](./memory.md) — `MEMORY.md` / `memory/` がどのように生成・維持されるか、圧縮、退避
- [Skills](./skill.md) — 四層構成、自己学習ループ、`<available_skills>` ブロック
- [Subagent](./subagent.md) — `subagents/` の宣言、同期 vs バックグラウンド、ストリーム転送
- [Plan Mode](./plan-mode.md) — `plans/` ファイル、読み取り専用フェーズ、HITL による離脱
