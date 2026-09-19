---
title: "リリース計画 Agent: このデモではテキストのドラフトを生成するだけ"
---

Managed Agents は、agent をクラウド環境で実行させる: 一方で、推論、オーケストレーション、Harness 管理といったコアのステージはクラウドによって統一的にホストされるため、アーキテクチャの安定性とランタイムの品質はプラットフォームによって保証される。もう一方で、長時間実行のタスクはもはやローカルデバイスがオンラインであり続けることに依存しない — 個人のコンピュータがシャットダウンされても、タスクはクラウドで実行され続けられる。

AgentScope 2.0 の Harness カーネルと Sandbox の隔離機能を基盤として、完全なエンタープライズグレードの Managed Agents プラットフォームを素早く構築できる:

+ AgentScope 2.0 ですでに本番運用されている Harness Agent は、Brain ランタイムとして直接使用できる。ホスティングカーネルが安定した推論と Harness の機能を提供する一方、ファイルシステム、Workspace、ツール実行は Sandbox 環境内で完全に隔離される。
+ プラットフォーム層はテナント、権限、バージョニング、イベント、実行プレーンの選択を扱う。コントロールプレーン(Agent、Environment、Memory、Vault、Deployment)とデータプレーン(Session、Events、SSE)が、これらの機能をマルチテナントで監査可能かつ操作可能な Managed プロダクトへと組織する。

> 以前公開したオープンソースの Agent Builder を見たことがあるなら、Managed Agents はそのプロダクト化されたアップグレードだと考えることができる: 基盤となるランタイムと主なコードパスは同じままだが、変わったのはリソースモデル、API 契約、実行プレーンの境界、マルチテナントガバナンスである。
>

## Managed Agents の背景

市場にはすでに、百煉(Bailian)、Claude Code、LangChain などからの同様の Managed Agents プロダクトが存在する。本質的に、私は Managed Agents プロダクトが以前のローコード agent プラットフォームと形において根本的に異なるとは考えていない。それらはすべて、「Agent の定義と実行」の機能を含むホストされたプラットフォームを提供する。プロダクトとしての表現の違いは、Harness の時代において、Managed Agents が次の二点を強調していることにある:

1. **もはやビジネス開発者に Harness を組み立てさせない。** 従来のプラットフォームは、メモリのメンテナンス、コンテキストの compaction、状態の復元、ツールの権限、サブタスクのクリーンアップを、大量の設定項目に分割していることが多かった。Managed Agents はこれらの共通のエンジニアリング機能を統一された Harness に吸収するため、開発者は主にビジネスに関連する Skill、Tool、Subagent、権限ポリシーを定義するだけでよい。プラットフォームは仕組みの一貫性とアップグレード可能性を保証するが、最終的なタスクの品質は依然としてモデル、system prompt、Skill の品質、ツールの戻り値、ビジネス評価に依存する。
2. **顧客にツール実行とデータ返却の境界を制御させる。** エンタープライズユーザーにとって、agent の本当の価値はそれをエンタープライズのデータ資産に接続することから来ており、shell、ファイル I/O、MCP、ビジネスツールこそが、まさにデータフローの入口である。そのため、このシステムは意図的に **Brain(推論とオーケストレーション)** と **Hands(ツール実行)** を分離する: Brain は次のラウンドの推論、状態の復元、コンテキスト管理を担当し、Hands は実際にファイル、ネットワーク、ビジネスシステムに触れることを担当する。Hands はプラットフォームが管理するクラウド Sandbox で動くこともできれば、顧客の VPC 内の Self-hosted な Worker で動くこともできる。

ポイント1は、プラットフォームの抽象化レベルを本当に変える。従来のローコードプラットフォームは、しばしばユーザーに「いつメモリを要約するか、長すぎるコンテキストをどう切り詰めるか、ツールの例外を何回リトライするか、サブタスクをどう回収するか」を決めさせていた。これらの選択肢は柔軟に見えるが、実際には Harness のエンジニアリング上の責任をビジネス開発者に移すことになる: 同じ Agent が、設定者の経験によってまったく異なる振る舞いをしうる。Managed Agents は、ロールプロンプト、Skills、MCP、ツール権限、Environment といったビジネスの差異だけを公開し、compaction のタイミング、Session の復元、ツール結果の削除、長期記憶のリフレッシュといったことは、継続的に進化する Harness に任せる。プラットフォームが Harness をアップグレードした後、すべての Agent は各フローチャートを一つずつ修正することなく、同じエンジニアリング上の改善を得る。

ポイント2は信頼境界を変える。モデルは「何を呼ぶか」を決定するが、それはモデルが存在するプロセスが「自分でそれを実行しなければならない」ことを意味しない。ツール呼び出しが安定したスキーマ、tool_use_id、結果イベントとして表現される限り、Hands は Brain 内の推論ループを変更することなく、プラットフォームのクラウド Sandbox あるいは顧客の VPC に移動できる。これにより、セキュリティチームは三つの質問を別々に答えられるようになる: モデルはどのコンテキストを見ることができるか? ツールはどのネットワークとファイルにアクセスできるか? ツール結果のどの内容を Brain に返してよいか? これら三つの質問が分離されると、権限監査とトラブルシューティングは「一つの Agent コンテナ全体」よりもはるかに明確になる。

Claude Managed Agents を例に取ると、それが開発者に受け入れられた重要な理由の一つは、Claude Code がすでに成熟した Coding Agent Harness のプロダクト価値を証明していたことである。ユーザーが見るのはモデルの推論とタスクの結果であり、プラットフォームが実際にホストしているのは **復元可能な Session 状態、エンジニアリングされた実行ポリシー、そして入れ替え可能な Hands** である。AgentScope 2.0 は同様の階層化された設計を採用している: `HarnessAgent` が長時間タスク、コンテキストのオーバーフロー、状態の復元、タスクの委譲を扱い、Managed Agents がそれにマルチテナントリソース、Environment、安定したデータプレーン契約を追加する。

Managed Agents について、Anthropic には個人ユーザーとエンタープライズユーザーの間に段階的に高度化するいくつかのソリューションがある:

+ **Claude Code CLI** は個人または単一マシンでの開発ワークフローをターゲットとし、Agent はローカルの Workspace、ターミナル、Session の記録と直接結びついている。
+ **Claude Agent SDK** は Session、イベントストリーム、ツールのやり取りを API として公開し、エンタープライズアプリケーションへの組み込みに適している。アイデンティティ、テナント、リソースの分離は依然として統合者の責任である。
+ **Managed Agents** はさらに、Agent、Environment、Session、実行プレーンを管理されたリソースへと変え、プラットフォームがバージョニング、権限、ランタイムガバナンスを扱う。

これら三つの層の違いは単に「より厚くパッケージされている」だけではなく、状態の所有権が段階的に上位へシフトしていくことにある:

| 形態 | 主な状態がどこにあるか | 誰が分離に責任を持つか | 適したもの |
| --- | --- | --- | --- |
| CLI / 単一マシンアプリ | ローカルディレクトリとローカル Session | OS ユーザー | 個人の生産性 |
| SDK / Harness | アプリケーションが提供する SessionStore / StateStore | アプリケーション開発者 | 単一のエンタープライズアプリケーション |
| Managed Agents | プラットフォームのコントロールプレーン、共有ステートストア、Session イベントログ | プラットフォームが User / Agent / Environment ごとに管理 | マルチチーム、マルチテナントのプラットフォーム |


## なぜ AgentScope 2.0 は Managed Agents に適した基盤なのか

AgentScope 2.0 のモデル抽象化、ツールと MCP、メッセージとイベント、状態ストレージ、リモートファイルシステム / 分散 BaseStore、プラガブルなサンドボックスは、すべてプロセス外の永続化とマルチレプリカデプロイのための拡張ポイントを確保している。これは、Managed Agents が Session の復元、ツール結果の永続化、リクエストをまたぐコンテキストの継続をゼロから実装する必要がないことを意味する。データプレーンのレプリカは AgentStateStore と Workspace バックエンドを共有し、Turn のリースとノードのフェイルオーバーを正しく処理しなければならない。

その中で、Workspace は Agent が使う論理的なディレクトリであり、Filesystem と Sandbox はそれをホストする物理バックエンドである。この二つは AbstractFileSystem によって分離されている: 同じファイルツールのセットが、ローカルディレクトリ、分散 BaseStore、あるいは E2B サンドボックスを指すことができる。論理的な Workspace が物理的な実行プレーンから分離されているため、Agent の定義はビジネスプロンプトを変更することなく分離ポリシーを切り替えられる。

具体的には、HarnessAgent は Hook を通じて、ReActAgent の上に長時間実行に必要なエンジニアリング上のデフォルトを組み立てる。例えば:

+ **Workspace 駆動のペルソナと知識**: `AGENTS.md` / `MEMORY.md` / `KNOWLEDGE.md` などのファイルが system prompt に注入される。
+ **Session の永続化**: agent の状態は `sessionId` によって復元されるため、プロセス再起動後も会話を継続できる。
+ **Compaction とオーバーフローの処理**: Harness はデフォルトで compaction とツール結果の削除を有効にし、ビジネス側が閾値を上書きしたり明示的に無効化したりすることを許す。
+ **Skills / Subagents**: Workspace のスキル、タスクの委譲(task など)がすぐに使える。
+ **統一されたファイルシステム抽象化**: ローカル、リモート KV、クラウドサンドボックス(E2B など)はすべて同じツールのセマンティクスを通じて動作するため、Managed Agents は Agent のビジネス定義を変更することなく、Environment のタイプによって実行プレーンを切り替えられる。

これらの機能は独立した名詞ではない。長時間タスクは、まず AgentStateStore からメッセージと agent の状態を復元し、その後 workspace の Hook によって AGENTS.md とインストール済みの Skills が注入されるかもしれない。推論の間、コンテキストがウィンドウの限界に近づくと、compaction の Hook が履歴を圧縮し、大きなツール結果はファイルシステムに削除され、コンテキストには検索可能な参照だけが残る。並列研究が必要なとき、メインの Agent はタスクを Subagents に渡せる。最終的に、ファイルシステムがローカル、リモート KV、E2B のどこに着地しても、モデルが見るツールのセマンティクスは一貫したままである。この組み合わされた安定性こそが、プラットフォームのカーネルとしての Harness の意味である。

さらに、HarnessAgent と Session は同じライフサイクルを共有しない。前者は、共有された `AgentStateStore` と復元可能な Workspace バックエンドを持つデータプレーンのノード上で再構築されるランタイムオブジェクトである。後者は、安定した ID、イベント列、永続化された状態を持つプロダクトリソースである。この二つを区別することによってのみ、真の水平スケーリングが達成できる: ノードが失敗したとき、Java オブジェクトは破棄されてよいが、会話と長期記憶は共有状態から復元されなければならない。Workspace が継続的かどうかは、BaseStore、サンドボックスのスナップショット、あるいは顧客側の永続化に依存し、ローカルディレクトリではそれは保証されない。

単一のエンタープライズ agent アプリケーションから Managed Agents へ移行する際、鍵となるのは推論カーネルを書き直すことではなく、ランタイムの機能を安定したプラットフォームリソースへと引き上げることである。「プラットフォーム API の層を追加する」というフレーズは、単にいくつかの Controller を追加するだけではない。本当のプロダクト化には、テナント ACL、Agent バージョンのスナップショット、Session ステートマシン、追記専用のイベント、Turn のリース、HITL チケット、Environment キー、Worker キュー、共有された協調ストレージ、アーカイブ監査も必要である。Harness はプラットフォームが agent ループを書き直さなくて済むようにしてくれるが、これらの分散した責務は依然として独立したエンジニアリングシステムである。

したがって、Managed Agents の完全な形は次のようになる: リソースガバナンスを担当する SaaS コントロールプレーン、ランタイムカーネルを提供する AgentScope 2.0、そして異なる信頼境界のもとで Hands を担う FC Sandbox / E2B あるいは顧客の Worker である。

## エンタープライズ Managed Agents プラットフォーム詳解

### 全体デプロイアーキテクチャ

#### コアコンポーネント図

1. **コントロールプレーン**


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785141394899-a68e0d3b-e16b-44be-9a29-4e61f163463f.png)

2. **データプレーン**


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785141716807-8d15ef5f-4d56-4c24-958a-caf553476262.png)

#### コアデータフロー

クライアントは(session/event)インターフェースを通じて、タスクリクエストを Managed データプレーン(Brain)に送信する。Brain は共有状態から Agent を復元し、推論とオーケストレーションのフロー全体を実行する。途中でツール呼び出しがあれば、Brain は Environment の設定に従ってツール呼び出しリクエストを Worker にルーティングする(それは managed サンドボックス環境、ユーザー管理のサンドボックス環境などでありうる)。

|Session + Events + SSE| DP[Managed Data Plane]
  CP[Control Plane<br/>Agent / Environment / ACL] -->|versioned references| DP
  DP &lt;--> DB[(JDBC<br/>events / state / leases)]
  DP --> B[HarnessAgent Brain]
  B --> M[Model]
  B -->|local tools| L[Brain host FS / shell]
  B -->|E2B-compatible API| S[Cloud Sandbox]
  B -->|tool schema + queue| Q[Self-hosted Work Queue]
  W[Customer Worker] -->|outbound poll / result| Q
  W --> H[Customer-managed FS / sandbox] -->
![](https://intranetproxy.alipay.com/skylark/lark/__mermaid_v3/cee93bfbfdd56bdf1526682edd6df433.svg)

上記のアーキテクチャ分析と実装を組み合わせると、システム全体は四つの層として読める:

| 層 | 責務 |
| --- | --- |
| **コントロールプレーン** | テナントリソース、Agent バージョン、Environment、Memory/Vault、ACL |
| **データプレーン** | Session のライフサイクル、イベントの永続化、SSE、Turn のリース |
| **ランタイム Brain** | 定義を解析し、キャッシュヒットか再構築かを決め → RuntimeContext によって復元 → `HarnessAgent.streamEvents` |
| **Hands** | 実際にファイルの読み書き、shell、外部化されたツールを行う |


### Agent を作成して実行する

まず、最小限の初期化を完了する: Managed Agents にログインし、再利用可能な Workspace Copilot Agent を作成し、その後 Local、Cloud Sandbox、Self-hosted Worker の各モードをデモンストレーションする。これにより、「Agent の定義は同じままで、Hands の場所だけが変わる」ことが直感的にわかるようになる。

以下は、Managed Agents が `http://localhost:8080` で動作しており、モデルキー(例えば `DASHSCOPE_API_KEY`)がすでに設定されていることを前提としている。

**0. ログイン**

```bash
export BASE=http://localhost:8080
TOKEN=$(curl -fsS -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | jq -er .token)
```

**1. サンプル Agent を定義する**

「Workspace Copilot」Agent を作成する: 短い system prompt と read_file、list_files、write_file などのツールを組み合わせ、異なる worker モードで異なるツールがどう動くかを示す。

```bash
AGENT=$(curl -fsS -X POST "$BASE/api/agents" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Workspace Copilot",
    "description": "blog demo agent",
    "system": "You are a workspace copilot. Prefer tools when listing or reading files. Keep answers concise.",
    "tools": [{
      "type": "agent_toolset",
      "defaultConfig": {
        "enabled": true,
        "permissionPolicy": { "type": "always_allow" }
      },
      "configs": [
        { "name": "read_file", "enabled": true },
        { "name": "list_files", "enabled": true },
        { "name": "write_file", "enabled": true }
      ]
    }]
  }')
AGENT_ID=$(echo "$AGENT" | jq -er .id)
echo "AGENT_ID=$AGENT_ID"
```


次は三つの worker モードのデモンストレーションだ。同じ Agent、同じ Brain の推論とオーケストレーション環境が、三つの異なる Hands パスで動作する:

| モード | ツールがどこで実行されるか | 誰がツール呼び出しを開始するか | データ境界 | 典型的な用途 |
| --- | --- | --- | --- | --- |
| Local | Brain が現在動作しているホスト | Brain プロセス | Managed クラスタ内 | 開発と信頼された環境 |
| Cloud Sandbox | E2B / FC クラウドサンドボックス | Brain が E2B 互換 API 経由で呼び出す | プラットフォームが管理するクラウドサンドボックス | Managed な隔離実行 |
| Self-hosted | 顧客の Worker / 顧客のサンドボックス | 顧客の Worker がツールタスクを読んで実行する | 顧客の VPC | プライベートな Workspace と組み込みの shell / FS ツール |


既存の Session は、途中で worker 実行環境を切り替えることをサポートしない。信頼境界を変更するには、新しい Session を作成し、同じイベント履歴が異なる実行セマンティクスをまたがないようにする。

#### Local モードの Worker

Local モードは開発とデバッグに最適である。Session、Harness の推論、モデルのリクエスト、ツールの実行はすべて Managed クラスタによって開始され、ファイルと shell は Brain プロセスに見えるローカル環境に直接着地する。

>Brain: tool_use / text
  Brain->>LocalFS: read_file / shell on host namespace
  LocalFS-->>Brain: tool_result
  Brain-->>API: agent.* + session.status_idle
  API-->>Client: SSE / events -->
![](https://intranetproxy.alipay.com/skylark/lark/__mermaid_v3/1216077e52ed0c4fd17a788f16bea26f.svg)

**Local** モードでは、Environment は `type=local` である: ファイルシステムと(有効化されていれば)shell は managed クラスタのホストの名前空間内で完結し、独立した Hands キューはなく、クラウドサンドボックスも呼ばれない。開発デバッグと信頼された内部ネットワークに適している。

#### Cloud Sandbox モードの Worker

Cloud Sandbox は、ホストされた Brain を維持しつつ、ファイルと shell を隔離されたサンドボックスに移動させる。Harness の推論、モデルのリクエスト、ツール呼び出しの開始者は依然として Managed クラスタにあるが、実際のコマンド実行とファイル I/O は FC Sandbox / E2B 互換の環境で行われる。

>Brain: tool_use
  Note over Brain,E2B: Brain initiates sandbox lifecycle and tool calls
  Brain->>E2B: E2B-compatible API FS/shell
  E2B-->>Brain: tool_result
  Brain-->>Client: SSE agent.* / status_idle -->
![](https://intranetproxy.alipay.com/skylark/lark/__mermaid_v3/e286ba42310643e8e1a64e3db8b2417c.svg)

Agent は E2B クライアントプロトコルを通じてコンテナをリクエストし、その中で shell / FS 操作を実行する。**Brain が能動的に呼び出しを開始し、Worker は関与しない。** E2B プロトコルと互換性のある Aliyun FC Sandbox を使う場合、まずサービスのアドレス、テンプレート、API キーを準備する必要がある。

Cloud Sandbox の管理境界は三つのアクションに分解できる: **サンドボックスの作成、サンドボックス内での実行、そして Session が終了またはタイムアウトした後の回収/永続化** である。Managed Agents は、`E2bFilesystemSpec` を通じてファイルと shell のツールを同じサンドボックスコンテキストにマップする。`isolationScope=SESSION` の場合、異なる Session はデフォルトで作業ディレクトリを共有しない。スナップショットまたは TAR による永続化モードを選ぶ場合、復元ポリシーも `AgentStateStore` と一緒に考慮しなければならない: モデルのコンテキストだけ復元してファイルを復元しない、あるいはその逆は、「Agent は完了したことを覚えているが、Workspace が存在しない」という不整合を生み出す。本番システムはこの二つを一つの復元ユニットとして設計しなければならない。

#### Self-hosted モードの Worker

Self-hosted は Hands をさらに顧客環境へと移動させる。Brain は Managed クラスタ内で引き続き Harness の推論を完了させるが、ツールタスクはキューに入り、顧客側の Worker によって能動的にアウトバウンドでポーリングされる。Worker はローカルの Workspace またはサンドボックスを管理し、結果を Brain に返す。このプロセス全体を通じて、Brain は顧客のネットワークに入る必要がない。

>Brain: tool_use
  Brain->>Q: enqueue work + persist agent.tool_use
  Brain-->>Client: requires_action / suspended
  Worker->>Q: poll with EnvKey
  Worker->>Worker: work directory + local tool exec
  Worker->>API: user.tool_result
  API->>Brain: resume turn
  Brain-->>Client: agent.message + status_idle -->
![](https://intranetproxy.alipay.com/skylark/lark/__mermaid_v3/e2b1fc8dcc2e2f7c0aef1980ee91a93c.svg)

Self-hosted モードでは、Brain は **ローカルの shell/FS 実行を無効化し**、関連するツールを外部化されたスキーマとして登録する。モデルが `tool_use` を発行すると、そのイベントは永続化され、Turn は pending/queued の状態に入る。ユーザー側の Worker は Environment Key を保持しており、**アウトバウンドで** ポーリング → ローカル Workspace を管理して実行するか、顧客自身のサンドボックスに接続する → `user.tool_result` を返して再開する。これは、「Brain が能動的にサンドボックス API を呼ぶ」Cloud Sandbox とはまさに正反対である: **実行の主導権はユーザー側にあり、サンドボックスを管理するかどうか、どう管理するかも顧客側の実装によって決まる。**

Self-hosted のターゲットシナリオは、データベース、コードリポジトリ、リリースシステムを顧客の境界内に保つことだが、現在のリファレンス Worker はすぐに使える組み込みの shell / FS ツールをサポートしている。データベース、カスタムのビジネスツール、イントラネットの MCP は、今後の Worker 拡張 SPI を必要とするか、あるいはユーザーが Worker の外側でラップする必要がある。すでに Worker プロトコルと統合されているツールについては、Brain はスキーマ、呼び出し引数、最終的に返された結果を見ることができるが、顧客の VPC に直接接続する必要はない。Worker はプラットフォームへ HTTPS リクエストを能動的に開始するだけでよく、結果を返す前に匿名化、サイズ制限、監査を適用できる。

### より複雑な Agent Team オーケストレーションの例

#### 複数の Agent を定義する

以下では、AgentDev シナリオを使って三つのロールから成るチームを示す。入力は Java ライブラリのリリース計画タスクである:

+ Repo Surgeon はコード品質の観点からチェックリストを提供し、Workspace の読み取りと検索の機能のみを持つ。
+ Ops Publisher はリリースプロセスの観点からチケットのドラフトを生成する。このデモではテキストのドラフトを生成するだけで、外部 MCP の統合はオプションの設定として別途説明する。
+ Team Lead はリスクと受け入れチェックリストをまとめる。Team Lead はできるだけビジネスデータに直接触れることを避け、委譲とサマライズのみを担当すべきである。

三つの Agent に分けるのはロールを積み重ねるためではなく、Workspace の権限、外部システムへのアクセス、サマライズの責務をそれぞれ制約するためである。得られるものは最小権限と独立した監査であり、すべてのツールを一つのスーパー Agent に詰め込んでプロンプトの制約だけに頼ることではない。

まず、fan-out に直接参加できる Ops Publisher を作成する。これはリリースのドラフトを生成するだけで外部システムを呼び出さないため、`/api/multiagent/run` が人間の確認段階で詰まってしまうことはない:

```bash
OPS_BODY=$(jq -n '{
  name: "Ops Publisher",
  system: "Draft changelogs and ticket outlines. Do not invoke tools or modify external systems.",
  tools: [{
    type: "agent_toolset",
    defaultConfig: {
      enabled: false,
      permissionPolicy: {type: "deny"}
    },
    configs: [{
      name: "read_file",
      enabled: true,
      permissionPolicy: {type: "always_allow"}
    }]
  }]
}')

OPS=$(curl -fsS -X POST "$BASE/api/agents" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "$OPS_BODY")
OPS_ID=$(echo "$OPS" | jq -er .id)
```

本番でチケット MCP を統合したい場合、Agent の本体に以下のスニペットを追加できる。`enableTools` は明示的に許可されたツールのみを公開し、URL とツール名は実際の値に置き換える必要がある:

```json
{
  "mcpServers": [{
    "name": "ticket-mcp",
    "url": "https://mcp.example.com/tickets",
    "transport": "http",
    "enableTools": ["draft_ticket"]
  }]
}
```

Skill も公開したい場合は、Workspace に対応するコンテンツがインストールされていることを確認した上で `"skills": [{"type": "workspace", "name": "release-notes"}]` を追加できる。現時点では `mcp_toolset.defaultConfig.permissionPolicy` は `ToolConfirmationMiddleware` に入らないため、リスクの高い MCP の書き込み操作には、MCP ゲートウェイ側でのアイデンティティ、承認、冪等性の制御が依然として必要であり、Agent 本体の `always_ask` だけに頼ることはできない。次に、コード Workspace への読み取り専用アクセスを持つ Repo Surgeon を作成する:

```bash
# ユーザー側リソースを操作する — 例えば「code/repository」Agent: ファイルシステムツール + skills
REPO=$(curl -fsS -X POST "$BASE/api/agents" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Repo Surgeon",
    "system": "You review the user workspace in read-only mode and report release risks.",
    "tools": [{
      "type": "agent_toolset",
      "defaultConfig": { "enabled": true, "permissionPolicy": { "type": "always_allow" } },
      "configs": [
        { "name": "read_file", "enabled": true },
        { "name": "grep_files", "enabled": true },
        { "name": "list_files", "enabled": true }
      ]
    }]
  }')
REPO_ID=$(echo "$REPO" | jq -er .id)
```

コードレビューの Skill がインストールされていれば、`"skills": [{"type": "workspace", "name": "code-review"}]` も追加できる。最後に Team Lead を作成する。これは委譲と結果収集のツールを保持し、実際の `MultiagentSpec` を通じて最初の二人のメンバーを記録する。現在の実行エントリはこのフィールドだけに基づいてメンバーを自動的に起動するわけではなく、実際の実行は依然として下記の Harness による委譲かプラットフォームの fan-out によって開始される。

```bash
LEAD_BODY=$(jq -n --arg repo "$REPO_ID" --arg ops "$OPS_ID" '{
  name: "Team Lead",
  system: "You coordinate Repo Surgeon and Ops Publisher. For a direct task, delegate concrete work and collect results. When the prompt already contains member results, do not call tools or spawn sessions; only summarize risks and produce the final checklist. Use sessions_pending_completions for finished child sessions and wait_async_results only for the generic async inbox.",
  tools: [{
    type: "agent_toolset",
    defaultConfig: {
      enabled: true,
      permissionPolicy: {type: "always_allow"}
    },
    configs: [
      {name: "sessions_spawn", enabled: true},
      {name: "sessions_list", enabled: true},
      {name: "sessions_pending_completions", enabled: true},
      {name: "wait_async_results", enabled: true}
    ]
  }],
  multiagent: {
    type: "agent_team",
    agents: [
      {type: "agent", id: $repo},
      {type: "agent", id: $ops}
    ]
  }
}')

LEAD=$(curl -fsS -X POST "$BASE/api/agents" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "$LEAD_BODY")
LEAD_ID=$(echo "$LEAD" | jq -er .id)
printf 'OPS_ID=%s\nREPO_ID=%s\nLEAD_ID=%s\n' "$OPS_ID" "$REPO_ID" "$LEAD_ID"
```

`MultiagentSpec` のワイヤースキーマは `type + agents[]` であり、メンバーの参照には `type`、`id`、オプションの `version` が含まれる。`wait_async_results` は汎用の非同期インボックスをブロックして待つために使い、`sessions_pending_completions` は完了したがまだ消費されていない子 Session の結果を列挙するために使う。両者は異なる非同期パターンに対応しているため、Team Lead は両方を有効にするが、system prompt はどちらをいつ使うべきかを明確にすべきである。

#### 一緒にオーケストレーションする

このシステムは、二つの異なるマルチ Agent 実行方法を提供する:

+ **Harness ネイティブの委譲**: Team Lead は推論中に `sessions_spawn` / Subagent ツールを使ってタスクを動的に分解し、親タスクと子タスクの間に明示的な委譲と結果復旧の関係がある。
+ **プラットフォームの fan-out**: `/api/multiagent/run` は複数の Agent のために Managed Session を作成し、同じメッセージを順次または並列に送信する。独立した分析、バッチ処理、投票に適している。

### 仕組みをさらに深く見る

前のセクションでは、ユーザー視点から Brain と Hands の協調を紹介し、Agent Team の例を通じてマルチ agent オーケストレーションのシナリオをデモンストレーションした。以下では、コントロールプレーン、データプレーン、Worker をさらに分解し、各層がどんな状態を保存し、どんな障害責任を負い、AgentScope 2.0 がどんな役割を果たすかを説明する。

**一文で言えば**: コントロールプレーンは「定義と権限」を管理し、データプレーンは「実行して記録する」ことを管理し、Worker は「誰のマシンで行動するか」を管理する。AgentScope 2.0 の `HarnessAgent` + ファイルシステム/サンドボックスの抽象化はデータプレーンと Hands のカーネルであり、SaaS API は推論ループを再実装することなくプラットフォームレベルのセマンティクスを構築する。

#### コントロールプレーン

コントロールプレーンは「何が実行を許されるか、誰が使えるか」に責任を持つ。それは静的な Agent 定義とそのバージョン、そして Model、Skills、MCP、Tools、Environment、Memory、Vault、Resources といった再利用可能なリソースを管理する。リソースは個々のユーザーに属し owner / ACL で分離されることもあれば、パブリックな Skills、MCP カタログ、組み込みツールセットのようにプラットフォームによってグローバルに事前構築されていることもある。

これらのリソースは「定義、参照、マウント」という三つの関係で理解できる。Model、Tools、MCP、Skills は Agent バージョン定義に入り、Environment は独立して存在し Session によって参照され、Memory Store、Vault、Files/Resources は Session が作成されるときにマウントされる。グローバルに事前構築されたリソースはプラットフォームのデフォルトを提供し、ユーザーリソースは owner / share の ACL を運ぶ。これにより、すべての Agent にパブリックな Skills をコピーすることを避けつつ、異なるテナントが共有ディレクトリを通じて互いのデータを見てしまうことも防げる。

コントロールプレーンは変更ガバナンスも担う。Agent の更新は新しいバージョンを作成し、古い Session は現在サポートされている履歴フィールドを引き続き記録・復元できる。Environment のキーはローテーションでき、リソースは即座に物理削除されるのではなくアーカイブできる。高リスクな組み込みツールの権限はバージョン付きで記録される。本番プラットフォームにとって、これらの機能は「新しいモデルを呼び出せるかどうか」よりも重要であることが多い。なぜなら、それらはロールバック、カナリア、インシデントの責任追跡が実現可能かどうかを決めるからだ。

Environment と Session は混同しやすいが、異なる層に属する。本稿では以下の境界を使う:

+ **Environment はコントロールプレーンに属する**: それは「実行プレーンのテンプレート」(`local` / `sandbox` / `remote` / `self_hosted` + 設定 + environment key)であり、複数の Session から参照でき、アーカイブと共有を運ぶが、それ自体は会話イベントを生成しない。
+ **Session はデータプレーンに属する**: それは Agent × Environment の実行中のインスタンスであり、ステートマシンとイベントログを持つ。作成パラメータはコントロールプレーンの `agentId` / `environmentId` を参照するが、ライフサイクル API(events / stream / interrupt)はデータプレーンのコアである。

したがって:

+ Session の作成 → **データプレーン**(次のセクションで詳述)
+ Environment の定義 → **コントロールプレーン**(`POST /api/environments`、キーのローテーション、アーカイブ)

#### データプレーン

データプレーンは「Agent バージョンを記録する Session を実際に実行させ、そのプロセスを完全に記録する」ことに責任を持つ。それはモデル呼び出し、ReAct ループ、Harness の hook、Turn のリース、Session のステートマシン、イベントの永続化と SSE のプッシュをホストし、割り込み、HITL、外部化されたツール結果の再開を処理する。

これらの操作は普通の CRUD の追加機能ではなく、ステートマシンを中心に回る: Session を作成すると Agent バージョンと Environment の参照が記録され、`user.message` は状態を idle から running へ押し進め、ツール確認は requires_action を running に復元し、interrupt は現在の Turn のキャンセルを試み、archive は以降の利用を終了させつつ監査履歴を保持し、delete は session とイベントを消去する。クライアントは、内部のあるスレッドがまだ生きているかをポーリングするのではなく、イベントによって UI を駆動すべきである。

データプレーンは対等な SaaS レプリカから成り、リクエストはどのインスタンスに届いてもよい。あるレプリカは、まず `agentId` によってコントロールプレーンでバージョン定義を見つけ、次に Agent バージョン、Environment、マウント情報からビルドキーを計算する: キャッシュヒットすれば `HarnessAgent` を再利用し、そうでなければ再構築する。各 Turn は、`userId` と `sessionId` を含む `RuntimeContext` を通じて Session の状態を特定する。したがって、ここでの「ステートレスなレプリカ」とは、代替不可能な正本の状態を保持しないことを意味し、リクエストごとに Java オブジェクトを再作成しないことを意味しない。

`RuntimeContext` は、すべての状態を Map に詰め込むのではなく、実行のための「アイデンティティとリソースのロケータ」として理解できる。`userId` はマルチテナントの名前空間と ACL を決定し、`sessionId` は復元可能な短期の brain 状態を特定する。Environment はファイルシステム/サンドボックスの実装を決定し、Memory Store と Vault はビルドフェーズでファイルシステムのルートと認証情報に解決される。Harness はこれらの安定した抽象化にのみ依存するため、同じリクエストが別のレプリカにヒットしたときも、セマンティクス上等価なランタイム環境を再組み立てできる。

データプレーンは実際には、異なるライフサイクルを持つ四層の状態をホストしている:

| 状態の層 | 典型的な内容 | ライフサイクル / 正本の出所 |
| --- | --- | --- |
| Agent バージョン | name、system、model、tools、skills、MCP など | コントロールプレーンが完全なスナップショットを保存する。現在のランタイムは履歴フィールドの一部のみを再構築する |
| Session イベント | user.message、tool_use、agent.message、status | 追記専用のログ。監査とクライアントのキャッチアップストリームの正本 |
| Agent brain 状態 | モデルメッセージ、圧縮されたコンテキスト、Hook の状態 | `AgentStateStore`。userId/sessionId によって復元される |
| Workspace / Sandbox | ファイル、タスクの成果物、ツールの副作用 | Local / BaseStore / E2B / Self-hosted の実行プレーン |


これら四つの層は「会話履歴を保存する」とひとまとめにはできない。例えば、Session イベントはモデルがかつてファイルの書き込みをリクエストしたことを証明できるが、ファイル自体を置き換えることはできない。AgentStateStore はコンテキストを復元できるが、外部データベースの副作用を自動的に復元することはない。復元のフローは各層を個別に復元し、その後イベント ID、ツール呼び出し ID、リソース参照によって再関連付けしなければならない。

Harness の推論がツールを呼ぶ必要があるとき、具体的な実行は Environment によって決定される。Cloud Sandbox は Harness のファイルシステム / サンドボックス抽象化を直接再利用し、Brain が E2B 互換の呼び出しを開始する。Self-hosted はツールをスキーマのみの定義に置き換え、`agent.tool_use` の後に Turn を一時停止し、Work Queue と Worker プロトコルを通じて結果を返す。

ここでの AgentScope 2.0 の役割は非常に明確である: **HarnessAgent と FS/Sandbox の抽象化を提供し、「効果のデフォルト」と「入れ替え可能な実行プレーン」を保証すること**。Managed Agents はリース、イベント契約、マルチテナンシー、ACL に責任を持ち、もう一つのプライベートな ReAct をラップするのではない。

#### Worker

Worker は、ツールが Brain から実際の実行環境にどう到達するかに焦点を当てている。このシステムには、誰がツール呼び出しを開始し、誰がサンドボックスのライフサイクルを管理するかによって区別される二つのパスがある。

完全に管理されたモードでは、Brain が Sandbox の作成と回収に責任を持ち、AgentScope が提供する E2B 互換の API を通じてファイルまたは shell の呼び出しも能動的に開始する。バックエンドは FC Sandbox のような互換サービスによってサポートされ、ツールのプロセスと作業ディレクトリはサンドボックスインスタンス内にある。プラットフォームが完全なハンドルを保持しているため、タイムアウト、分離範囲、永続化ポリシーを統一的に設定できる。

Self-hosted モードでは、Brain がモデルからツール呼び出しを受け取った後、顧客の VPC に接続するのではなく、`agent.tool_use` を永続化して work item を作成する。顧客側の Worker がキューを能動的にポーリングし、自身のホストまたはサンドボックスでツールを実行し、`user.tool_result` を通じて結果を返すことで、Brain が次のラウンドの推論を再開できるようにする。

障害復旧の責任も異なる。完全に管理されたモードでは、Brain がサンドボックスのハンドルを知っているため、タイムアウト、スナップショット、回収ポリシーを統一的に設定できる。Self-hosted モードでは、Brain は work の状態とツール結果しか知らず、顧客の Worker がローカルサンドボックスがまだ生きているかどうか、重複タスクが安全かどうか、結果の匿名化が必要かどうかに責任を持たなければならない。プラットフォームはプロトコルとステートマシンを提供するが、ビジネスツールの冪等性のセマンティクスを顧客の代わりに定義することはできない。

Work のステートマシンは `queued → starting → active → stopping → stopped` である。

独立した Worker をデプロイするとき、Brain と顧客側のプロセスの両方に設定が必要になる。以下に最小限の起動方法と本番チェックリストを示す。

## まとめ

AgentScope 2.0 はエンタープライズグレードの分散シナリオを対象として位置づけられている。それは、エンタープライズの DataAgent、SreAgent などを構築するための分散 Agent フレームワークとして使うこともできれば、同じ Harness を使ってエンタープライズの Managed Agents をサポートし、Managed Agents の背後にある Agent Runtime になることもできる。これにより、企業は「自分でブロックを組み立てる」か「完全にブラックボックスな managed ホスティング」かを選ばなくて済むようになる — 同じ Harness カーネルが両方のモードを提供できる。

+ ドキュメント: [https://java.agentscope.io](https://java.agentscope.io)
+ GitHub: [https://github.com/agentscope-ai/agentscope-java](https://github.com/agentscope-ai/agentscope-java)
+ AgentScope Builder: [https://github.com/agentscope-ai/agentscope-java/tree/main/agentscope-service](https://github.com/agentscope-ai/agentscope-java/tree/main/agentscope-service)

