---
title: AgentScope Builder — OpenClaw の「自己進化」をチーム全体のプラットフォームへ
---

AgentScope Java 1.1.0 では、OpenClaw と Coding Agent から得た「Workspace こそが真実 + 自己進化」という体験を、Harness エンジニアリングの基盤へと昇華させた: `HarnessAgent` + `AbstractFilesystem` に組み込みの compaction と階層化されたメモリを組み合わせたものである。その際、私たちは一つの約束をした: **Agent のロジックを一度だけ書き、デプロイの形は必要に応じて切り替える — 個人のノートパソコンからエンタープライズの分散デプロイまで**。

`HarnessAgent` は大きな注目を集め、多くの開発者が実際のアプリケーション例を求めた。今日、私たちは AgentScope Claw と AgentScope Builder の両方をリリースする。これらは AgentScope Harness を実際に世に送り出したプロダクトであり、具体的なケーススタディでもある:

- **agentscope-claw** — 「単一ユーザー・ローカル」側での Harness の完全な実現。**AgentScope Harness を使って、Java 版の OpenClaw を構築した**。
- **agentscope-builder** — 「マルチユーザー・エンタープライズ」側での Harness の完全な実現。今日リリースする。**Builder は OpenClaw の分散版として理解できる**: 一つのプラットフォーム上で、チーム全体が自己進化する agent を開発・運用・共有できる。

まずは AgentScope Claw を深く説明する — Builder は何もないところから現れたのではなく、Claw では解決できなかったエンタープライズの要求から生まれたからだ。

---

## AgentScope Claw — Harness を使って OpenClaw を構築する

### これは何か

AgentScope Claw は軽量な Java 版の [OpenClaw] である: **あなた自身のコンピュータ上で動く個人アシスタント**。あなたに代わって振る舞い、あなたのファイルシステムとシェルの中で作業し、使うほどにゆっくりと「成長」していく — 学んだスキル、生み出したサブエージェント、蓄積した記憶はすべて、それが自身の Workspace に書き込むファイルである。

Claw はリポジトリの次の場所にある:

```
agentscope-examples/agents/agentscope-claw/
```

これはサンプルコードではなく、**完全な Spring Boot アプリケーション**である: JDK 17、`mvn package` を一回、`java -jar` を一回実行し、ブラウザで [http://localhost:8080](http://localhost:8080) を開くだけでよい。すべての状態は `~/.agentscope/` の Workspace 配下に永続化され、これは `CLAW_HOME` 環境変数で上書きできる。初回起動時には組み込みの `default` agent が自動的に作られるので、コードを一行も書かずにチャットを始められる。

### 三つのコア機能

Claw を面白くしているのは「チャットできる」ことではなく、以下の三つのことである — そして、これらは Harness の設計上の約束を実際のプロダクトで初めて完全に表現したものである。

**1. Workspace 駆動の自己進化**

Claw のすべての状態は、データベースではなく `~/.agentscope/claw/workspace/` の下に存在する:

- `AGENTS.md` — agent のペルソナと行動契約。推論の前に毎回自動的に system prompt として注入される
- `skills/` — agent 自身が書き、使う再利用可能なスキル
- `subagents/` — サブエージェントの仕様宣言。自動的に発見・ロードされる
- `MEMORY.md` + `memory/YYYY-MM-DD.md` — 階層化された記憶。バックグラウンドの LLM によって自動的にメンテナンスされる
- `agents/<subId>/sessions/` — 完全な会話ログ(JSONL)と圧縮されたコンテキスト

すべての会話の後、新しい事実が抽出され、日次の memory ledger に追記され、バックグラウンドのスケジューラが定期的にそれらを `MEMORY.md` にマージする。**agent のペルソナ、知識、スキルを微調整するのにコードの変更は必要ない — Workspace 内のファイルを編集するだけでよい。ファイルを変更することは agent をアップグレードすることに等しい**。OpenClaw はすでにこれを実現していた。今や Java もそれができる、しかも AgentScope Harness ランタイムに支えられて。

**2. あなたのローカルファイルシステムとシェルへの直接アクセス**

Claw は `LocalFilesystemWithShell` バックエンドを使う — サンドボックスなし、リモートサーバーなし。すべての読み書きとコマンドはローカルの OS に直接ヒットする。自分自身のマシン上では、これはバグではなく機能である: 「`~/Downloads` の中の3ヶ月以上前のファイルをアーカイブディレクトリに移動して」と頼めば、シェルを持っているので実際にそれを実行できる。

Harness はバックエンドの機能に応じて条件付きでツールを登録する — Claw のローカルモードでは、`execute` シェルツールが自動的に agent のツールセットに現れる。信頼できない環境(後述する Builder のリモートモードなど)に切り替えると、同じ agent のコードから同じシェルツールが自動的に消える。**これは「同じ agent ロジック、異なる形」の最初の具体的なデモンストレーションである**。

**3. すでに使っているアプリとの直接統合**

Claw には六つの組み込みチャネルが最初から付属する:

| `type` | トランスポート | 説明 |
| --- | --- | --- |
| `chatui` | プロセス内 | デフォルトのローカル Web UI |
| `dingtalk` | Stream(WebSocket) | DingTalk 社内アプリ、公開ポート不要 |
| `wecom` | HTTP コールバック + REST API | 自己ホストの WeCom アプリ |
| `feishu` | HTTP イベントコールバック + REST API | Feishu カスタムアプリ + イベント購読 |
| `github` | Webhook + REST API | issue / PR レビューコメントイベントをリッスン |
| `gitlab` | Webhook + REST API | Issue / MR Note Hook をリッスン |

つまり、DingTalk の DM や GitHub の issue コメントから @claw でき、完全な Workspace のコンテキストを持って応答してくれるということだ。起動時には、各 agent が `outbound_send` ツールも登録し、任意のチャネルに **能動的に** メッセージを送信できるようにする — サブタスクが完了すると、`HarnessGateway.tryDispatchAnnounce` が自動的にインバウンドのアドレスを再利用するため、完了通知は自然に、それをトリガーした DingTalk や WeCom の Session に戻っていく。

チャネル層には、デフォルトの信頼性メカニズム一式も付属する — 冪等な重複排除、bot ループの防止、WeCom の署名検証、AES-256-CBC 復号、アクセストークンの更新である。これらはエンタープライズ IM 統合における「間違えると壊れる」類の詳細であり、フレームワークがすでに処理してくれている。

### Claw の境界

Claw は意図的にシンプルさを保っている — **ログインなし、マルチテナント分離なし、Docker サンドボックスなし、水平スケーリングなし**。「インストールして即実行」という体験を壊してしまうため、それ以上のことを意図的にやっていない。

しかし、これをチームの中に置こうとした途端、問題が次々と現れる: 複数人がどうやって同じプロセスを共有するのか? 自己進化した Workspace はユーザーごとにどう分離されるのか? マルチレプリカのデプロイで、あるユーザーの記憶はノードをまたいでどう一貫性を保つのか? ユーザーが提出したコードをどう安全に実行するのか? 壊されることなく、良い agent を同僚と共有するにはどうすればよいのか?

これら五つの問題はそれぞれ単独では小さいが、合わせると **Claw は別のコンテナに詰め直さなければならない** ということを意味する。それが Builder の出発点である。

---

## Claw から Builder へ — OpenClaw のエンタープライズデプロイ形態

Claw は「一台のマシン、一人のユーザー、一つの Workspace」を前提としている。その前提をそのままチームに適用すると、五つの場所で同時に破綻する — そのどれも「Claw のプロセスをいくつか余分に動かす」だけでは修正できない:

1. **複数人が一つのプロセスを共有するが、それぞれが自分専用のビューを必要とする。** Claw は現在のローカルユーザーしか認識しない。マルチユーザーのログイン、トークンベースの認証、ユーザーごとの Session 分離は、すべて Claw のスコープ外である。
2. **各ユーザーの Workspace は他を汚染してはならない。** agent の自己進化の副作用は、それが **ファイルを書く** ことである — 学んだスキル、生成されたサブエージェント、蓄積された `MEMORY.md`。Alice の調整された agent は、Bob が見るべきでないものを Bob に見せてはならず、Bob の会話が Alice の記憶を上書きすることもあってはならない。しかし Claw は単一のグローバルな Workspace を使っている。
3. **マルチレプリカのデプロイでは、同じユーザーが一貫した Workspace を見なければならない。** 二台のマシンで二つの Claw プロセスを動かすということは、二つの孤立したローカルディスクを持つことを意味する。同じユーザーのリクエストが異なるレプリカに着地すると、二つの異なる記憶を見ることになる。
4. **サーバー上でユーザー提出のコードを実行するには、OS レベルの分離が必要である。** Claw はデフォルトでローカルシェルを有効にしている — 自分自身のマシンでは核となる体験だが、マルチテナントのサービスでは直接的な攻撃対象になる。
5. **良い agent は、壊されることなく共有可能でなければならない。** チームが必要としているのは「全体をエクスポートして誰かにインポートしてもらう」ことではなく、「あるグループに使用を許可するが、変更はさせない」というきめ細かな仕組みである。

これら五つの問題は一つのことに集約される: **「一人のユーザー、一台のマシン、一つの Workspace」は「多数のユーザー、多数のマシン、複数の名前空間で分離された Workspace」にならなければならない。** これは Claw にパッチを当てて解決できるものではなく、Harness の Workspace 抽象の上にマルチテナントで分散した分離レイヤーを構築することを必要とする。

---

## Builder のプロダクトポジション 1 — マルチテナント、分散型 OpenClaw

Builder は Claw のコア体験を **チームおよびエンタープライズ向け** の Web プラットフォームへとパッケージ化する。一文でのポジショニング:

> **Builder は OpenClaw の分散版である** — 同じ自己進化、同じ Workspace 駆動の設計、同じ Harness ランタイム。ただ「一人」から「一つの組織」へ、「一台のノートパソコン」から「水平にスケール可能な一連のサービス」へとスケールしただけである。

プラットフォームプロダクトとして、そのコア機能は次の通りである:
1. 一つのプラットフォーム上で多数のユーザーをサポートする、マルチテナント・分散版の OpenClaw。各ユーザーの agent は分離され、マルチレプリカのデプロイをサポートし、ノードをまたいでユーザーの Workspace の一貫性を保つ。
2. ノーコードの agent 開発プラットフォームであり、ユーザーは Web UI からコードを一行も書かずに自分の agent を作成、調整、共有でき、すべての agent 状態は Workspace に永続化され、自己進化を自動的に駆動する。

## Builder のプロダクトポジション 2 — ノーコードの Agent 開発プラットフォーム

この一年、LangSmith Fleet、Coze、Dify のようなプラットフォームが「ノーコードで agent を作る」という波を巻き起こした — ユーザーはコードを書かずにブラウザ上で動作する agent を組み立てられる。そのコア体験は一貫している: **テンプレートを選ぶ → パラメータを設定する → ツールを接続する → 公開する**、これによって agent 開発の参入障壁を下げている。

Builder も同じことをする: **ユーザーはブラウザからログインし、コードを書かずに自分の agent を構築する**。UI では、テンプレートを選ぶ(あるいは空のひな形から始める)、モデルを選ぶ、system prompt を書く、スキル / サブエージェント / ツール / MCP サービスにチェックを入れる、保存する、そしてチャットを始める — 参入障壁は低く、オンボーディングは速く、WYSIWYG である。他のノーコードプラットフォームと同じである。

### 本当の違い: 作成はあくまで出発点であり、agent は進化し続ける

ほとんどのノーコードプラットフォームは **静的な agent** を生み出す — できることを設定すれば、それは永遠にそのことしかしない。何か新しいことをさせたければ、管理画面に戻って手動で設定を変更する。agent の能力の上限は、作成時にあなたが考えたことすべてに等しい。

Builder は異なる。すべての agent の背後には、継続的に成長する Workspace がある:

- **自動的な記憶の蓄積**: 各会話の後、agent は新しい事実を蒸留して記憶に書き込む。次回はあなたとあなたのビジネスについて以前よりよく理解している。ナレッジベースを手動で更新する必要はない — 自分で成長していく。
- **自動的なスキルの習得**: タスクをこなしながら、agent が再利用可能なワークフローに気づいた場合、それを `skills/` ディレクトリの下に新しいスキルとして構造化できる。次に似たようなシナリオに直面したとき、ゼロから推論するのではなく、学習したスキルを呼び出す。
- **自動的なサブエージェントの生成**: あるクラスのサブタスクが繰り返し発生する場合、agent はそれを専用のサブエージェントとして切り出し(`subagents/` に書き込む)、自分でやるのではなく直接それに委譲できる。

これら三つのことは **ユーザーが管理画面に戻る必要がない** — agent が Workspace 内でファイルを書き、Workspace は会話の間じゅう進化し続ける。**あなたが設定するのはその出発点であり、その上限ではない**。

もちろん、「自動進化」は完全な自由を意味しない。Workspace の中のすべては **ファイルであり、編集可能でありバージョン管理可能である** — ユーザーは UI で `AGENTS.md`(ペルソナ)を編集し、`skills/`(スキルの追加・削除)を管理し、`knowledge/`(ドメイン知識)を投入し、`MEMORY.md`(記憶の修正)をレビューできる。「ノーコード」は「制御ゼロ」を意味しない。それは「agent を制御するのにコードは不要だが、ファイルを通じて常に制御できる」ことを意味する。

### LangSmith Fleet のようなプラットフォームとの比較

| | 典型的なノーコードプラットフォーム(Fleet / Coze / Dify) | AgentScope Builder |
|---|---|---|
| **Agent の能力上限** | 作成時に設定されたもの | 作成は出発点にすぎず、能力は**継続的に成長する** |
| **記憶とスキル** | 短期記憶 + 手動でメンテナンスされるツールコネクタ | 階層化された自動記憶 + agent が自律的にスキルを習得できる |
| **状態の格納先** | データベース / 内部構造(ユーザーが直接編集できない) | **Workspace ファイル** — 読み取り可能、編集可能、Git でバージョン管理可能 |
| **オフラインでの移行** | プラットフォームに縛られる | Workspace のサブツリー = 標準的なファイル。コピーして使える |

**一文でのまとめ**: Builder は「ノーコード + 自己進化」の agent プラットフォームである — 参入障壁は Fleet / Coze / Dify と同じくらい低いが、**作成するものは静的なツールではなく、成長するデジタルアシスタントである**。

---

## Builder のコアメカニズム: CompositeFilesystem

Builder の実装を一文で説明するなら、こうなる:

> **Builder はすべての agent を `HarnessAgent` + `CompositeFilesystem` の上で動かす — 前者が agent のランタイムオーケストレーションを担当し、後者が Workspace を、名前空間で分離され、配布可能で、サンドボックスに射影可能な資産へと変える。**

具体的なリクエストを追いながら、この二つを解きほぐしていこう。

### HarnessGateway — (ユーザー、Agent)ごとに独立したランタイム

Web レイヤーの下には **HarnessGateway** がある。その仕事はシンプルである: 各 `(userId, agentId)` のペアを独立した `HarnessAgent` インスタンスにルーティングする。

- Alice が `agent-A` を呼ぶ → `Agent(alice, agent-A)` に着地する
- Alice が `agent-B` を呼ぶ → `Agent(alice, agent-B)` に着地する
- Bob が同じ `agent-A` を呼ぶ → `Agent(bob, agent-A)` に着地する — Alice のものとは完全に独立している

各 `HarnessAgent` インスタンスは、そのユーザーとその agent の名前空間にバインドされた `CompositeFilesystem` を受け取る。言い換えれば — **HarnessGateway は分離がどう実装されているかを気にしない。「正しいリクエストを正しい agent インスタンスに渡す」ことだけを行う** — 実際の分離作業は次のレイヤーで行われる。

### CompositeFilesystem — Workspace を分離された資産に変える

`CompositeFilesystem` は Builder のすべてを機能させる鍵である。その名前は文字通りである — **それは複合ファイルシステムである**:

```
┌──────────────────────────────────────────────────┐
│  CompositeFilesystem                             │
│                                                  │
│  ┌───────────────────────────────────────────┐   │
│  │  Layer 1: Namespace routing                │  │
│  │    transparently rewrites all paths to    │  │
│  │    users/{userId}/agents/{agentId}/...    │  │
│  └───────────────────┬───────────────────────┘   │
│                      ▼                           │
│  ┌───────────────────────────────────────────┐   │
│  │  Layer 2: Storage backend                  │  │
│  │    local disk / Docker container /        │  │
│  │    remote KV — choose one                 │  │
│  └───────────────────────────────────────────┘   │
└──────────────────────────────────────────────────┘
```

- **上位レイヤーは名前空間のルーティング**: agent が `read("AGENTS.md")` を呼ぶと、CompositeFilesystem は現在の `RuntimeContext` から `(userId, agentId)` を読み取り、パスを `users/{userId}/agents/{agentId}/AGENTS.md` へと透過的に書き換える。agent のコードは「ファイルシステム全体」を操作していると思っているが、**実際に見えているのは、自分だけに属する、名前空間で刈り込まれたサブツリーである**。
- **下位レイヤーは物理ストレージバックエンド**: 名前空間のルーティングの後、データが実際にどこに着地するかはバックエンドの実装次第である。デフォルトは `LocalFilesystemWithShell` によるホストディスクであり、パスは `~/.agentscope/builder/workspace/users/{userId}/agents/{agentId}/...` に解決される。

重要なポイント: **agent のコードは、いずれのレイヤーの存在も知らない**。それは Claw と全く同じように、Harness の統一された `read / write / ls / grep` API を使い続ける。分離は、ビジネスコードが「他人のディレクトリを慎重に避ける」ことによってではなく、CompositeFilesystem の内部で実装されている。

### 書き込みのエンドツーエンドウォークスルー

この抽象を具体的にするために — Alice が UI で自分の `agent-A` に新しいスキルを学習させる(`skills/sql-helper/SKILL.md` への書き込み)よう依頼したとき、呼び出しチェーンの全体は次の通りである:

1. **Web レイヤー**: JWT の解析により `userId=alice` が得られ、URL パスの解析により `agentId=agent-A` が得られ、両方が `RuntimeContext` に付与される。
2. **HarnessGateway**: `Agent(alice, agent-A)` の `HarnessAgent` インスタンスにルーティングする。
3. **Agent の推論**: モデルは `write_file("skills/sql-helper/SKILL.md", ...)` ツールの呼び出しを決定する。
4. **CompositeFilesystem の上位レイヤー**: 呼び出しをインターセプトし、`RuntimeContext` から `(alice, agent-A)` を読み取り、パスを `users/alice/agents/agent-A/skills/sql-helper/SKILL.md` に書き換える。
5. **CompositeFilesystem の下位レイヤー**: デフォルトのローカルストレージがこの相対パスを `~/.agentscope/builder/workspace/` に追加し、最終的に `~/.agentscope/builder/workspace/users/alice/agents/agent-A/skills/sql-helper/SKILL.md` にディスク上へ書き込む。
6. **次の推論**: 次の会話が始まると、`WorkspaceContextHook` が system prompt を注入し、CompositeFilesystem を通じて `skills/` も読み取り、Alice/agent-A 自身のサブツリーを自動的に特定する。新しく学習されたスキルはツールセットに現れる。

Bob の `agent-A` が同じことをすると、パスは `users/bob/agents/agent-A/...` に書き換えられる — 物理的には Alice のものと完全に異なるディレクトリツリーである。**「これは Alice か Bob か」を尋ねるビジネスコードは存在しない。分離は抽象レイヤーから生まれる**。

### より強い分離が必要なとき: 同じ Composite の上にサンドボックスを追加する

Claw のローカルモードでは、シェルコマンドはホストに直接ヒットする。Builder のデフォルトのローカルモードはこれを継承しており、信頼されたチームや単一ノードのデプロイに適している。しかし、シナリオが **信頼できないコードが agent に入ってくる** こと(例えば、agent にユーザー提出の SQL、Python、シェルスクリプトを実行させること)を含む場合、ホストはもはやそれを直接受け止めることはできない。

Builder はこのシナリオのために「サンドボックスソリューションを再構築する」のではなく、**CompositeFilesystem の上に射影レイヤーを追加する**:

- サンドボックスモードに入ると、ランタイムは完全に Docker コンテナの中に移動する
- ホスト側は Workspace の「マスター」であり続ける。CompositeFilesystem は `AGENTS.md`、`skills/`、`subagents/`、`knowledge/` のような重要なファイルをホストからコンテナ内の `/workspace` に射影する
- コンテナ内の agent は、ホストとまったく同じ作業台を見る — 同じ `AGENTS.md` を読み、同じスキルセットを使う
- シェルコマンドはコンテナ内で実行される。ホストプロセスはユーザー入力によって直接影響を受けることは決してない

注意 — **これは「もう一つの別のファイルシステム」ではない**。それは、下位レイヤーに「ホスト ↔ コンテナ」の物理マッピングが追加された、同じ CompositeFilesystem である。agent のコード、Workspace のディレクトリ構造、UI の体験は変わらない。変わるのは、シェルコマンドが着地する境界がコンテナの壁に移動することだけである。

サンドボックスの分離粒度 — Session ごと、ユーザーごと、agent ごと、あるいはグローバル共有 — は、ビジネスシナリオごとに設定できる実際のデプロイ上の判断であり、デフォルトの `USER`(ユーザーごとに一つのコンテナ、複数の Session で共有)は、ほとんどのマルチテナント SaaS にとって妥当な出発点である。

### 一台のマシンでは足りないとき: ストレージレイヤーを分散バックエンドに入れ替える

ここまでの Builder はまだ単一ノードである — Workspace は一台のマシンのローカルディスク(あるいはそのマシン上の Docker コンテナ)に存在する。Builder をマルチレプリカにし、ユーザーのリクエストがどのノードに着地しても一貫した状態を見られるようにしたいとき、問題は最初の「レプリカ同士がどう Workspace を共有するか」という問いに戻ってくる。

CompositeFilesystem の解決策は直接的である: **下位のストレージバックエンドを「ローカルディスク」から「分散 KV」に入れ替える。** Builder は `BaseStore` インターフェースを抽象化しており、実装は Redis、オブジェクトストレージ(OSS / S3)、あるいは自前の KV サービスであってよい。入れ替えた後:

- すべての agent ランタイムの読み書きは `RemoteFilesystem` を通じて `BaseStore` に着地する
- ユーザーの Workspace を管理する Web レイヤーも同じ `BaseStore` を使う — Web が見るものと agent が見るものは同じデータである
- 分散 `Session`(典型的な実装: `RedisSession`)と組み合わせることで、Builder のプロセス自体を対等なレプリカとしてデプロイできる

図の中の「名前空間ルーティングの上位レイヤー」はまったく変わらない — 名前空間のルーティングは CompositeFilesystem の内部で行われ、ストレージバックエンドがローカルディスクであれ、Docker コンテナであれ、Redis であれ、それについて何も知らない。**これはまさに、[Harness の記事](/v2/ja/blogs/agentscope-v1-harness) の `AbstractFilesystem` が本当の力を発揮する場所である** — ビジネスコードは一行も変わらず、デプロイ側が Bean を一つ入れ替えるだけで、単一ノードから分散への移行が完了する。

---

## Builder アーキテクチャの概観

```
┌─────────────────────────────────────────────────────────────────────┐
│  AgentScope Builder (Spring Boot, port 8080)                        │
│                                                                     │
│   React SPA ──▶  REST API (JWT)                                     │
│                  │                                                  │
│                  ▼                                                  │
│   ┌──────────────────────────────────────────────────────────────┐  │
│   │  HarnessGateway                                              │  │
│   │   ├─ Agent (alice, agent-A) ──┐                              │  │
│   │   ├─ Agent (alice, agent-B)   │ one HarnessAgent per (user,id)│  │
│   │   └─ Agent (bob,   agent-A) ──┘                              │  │
│   └──────────────────────────────────┬───────────────────────────┘  │
│                                      ▼                              │
│   ┌──────────────────────────────────────────────────────────────┐  │
│   │  CompositeFilesystem                                         │  │
│   │   ├─ Top: namespace routing  (userId, agentId) → subtree     │  │
│   │   └─ Bottom: physical storage backend                        │  │
│   │         · Default: local disk                                │  │
│   │         · Sandbox mode: host ⇄ container projection          │  │
│   │         · Distributed: BaseStore (Redis / OSS / custom)      │  │
│   └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
│   User and agent metadata (default H2; production can use          │
│   MySQL / PostgreSQL)                                               │
└─────────────────────────────────────────────────────────────────────┘
```

この図全体で本当に「新しい」ものは、一番上の行 — React SPA + JWT REST API + HarnessGateway のルーティング — だけである。中間層と下位層は、Harness の `HarnessAgent` と `AbstractFilesystem` を直接組み合わせたものにすぎない。

これが Builder の設計哲学である: **agent のランタイムを再発明するのではなく、マルチテナントのエンタープライズ環境でそれを動かすために必要な運用の外殻だけを追加する**。

---

## クイックスタート

### Claw

```bash
# 1. モデルの API キーを設定する(デフォルトは DashScope)
export DASHSCOPE_API_KEY=sk-xxx

# 2. ビルドして実行する
mvn -pl agentscope-examples/agents/agentscope-claw -am clean package -DskipTests
java -jar agentscope-examples/agents/agentscope-claw/target/agentscope-claw-*.jar
```

[http://localhost:8080](http://localhost:8080) を開く。デフォルトのホームディレクトリは `~/.agentscope` である。DingTalk / WeCom / Feishu / その他のチャネルに接続するには、`~/.agentscope/agentscope.json` を編集し、対応するチャネルのエントリを追加する。詳細は [Claw README] を参照。

### Builder

```bash
export DASHSCOPE_API_KEY=sk-xxx

mvn -pl agentscope-examples/agents/agentscope-builder -am clean package -DskipTests
java -jar agentscope-examples/agents/agentscope-builder/target/agentscope-builder-*.jar
```

サービスはポート 8080 で起動する。`admin/admin`、`bob/bob`、`alice/alice` でログインすると、フル機能の UI にアクセスできる。本番デプロイ(データベースの切り替え、サンドボックスイメージ、分散バックエンド)については [Builder README] を参照。

[Claw README]: https://github.com/agentscope-ai/agentscope-java/tree/main/agentscope-examples/agents/agentscope-paw
[Builder README]: https://github.com/agentscope-ai/agentscope-java/tree/main/agentscope-service

---

## Claw vs Builder — どちらを選ぶべきか

| | Claw | Builder |
|---|---|---|
| **ユースケース** | 自分自身のノートパソコン / ワークステーション上の個人アシスタント | チームまたは企業が共同で自己進化する agent を構築・運用する |
| **ユーザー** | 1人 | 複数人 — ログインした各ユーザーが自分の Workspace を持つ |
| **入口** | Web UI + DingTalk / WeCom / Feishu / GitHub / GitLab | React SPA + JWT REST API |
| **分離** | なし — あなたとして直接実行される | `(userId, agentId)` の名前空間。オプションで Docker サンドボックス |
| **共有** | なし — 一台のマシン、一人の人間 | run / edit / fork の三段階の権限レベル |
| **分散** | 単一プロセス、単一ノード | BaseStore バックエンドに切り替えて水平スケール |
| **ファイルシステム** | `LocalFilesystemWithShell` | `CompositeFilesystem` |

**この二つのパスは互いに排他的ではない** — Harness の Workspace はファイルであり、`AGENTS.md / skills/ / subagents/` のサブツリー全体は、バージョン管理でき、コードレビューでき、Claw から Builder へ直接コピーできる資産である。よくあるワークフロー: 開発者が自分のマシン上で Claw を使って agent を満足のいくまで調整し、Workspace ディレクトリをテンプレートとしてリポジトリに提出し、運用チームが Builder を通じてチーム全体にそれをプッシュする。

---

## まとめ

[Harness の記事](/v2/ja/blogs/agentscope-v1-harness) では、「自己進化する agent ランタイム」— `HarnessAgent` + Workspace の規約 + プラガブルなファイルシステム + hook パイプライン — を届けた。

今日の記事は、そのランタイムを **二つの直接実行可能なプロダクト** に変える:

- **Claw が証明したこと**: AgentScope Harness を使えば、私たちはすでに完全な Java 版の OpenClaw — 自己進化、ローカルシェル、五つの IM チャネル統合 — を、`mvn package` で実行できる一つの Spring Boot アプリケーションとしてパッケージ化して構築できる。
- **Builder が証明したこと**: 同じ Harness ランタイムに、「Workspace の分離と共有」のための運用の外殻を加えるだけで、チームのためのマルチテナントプラットフォームへと直接進化する。Claw から Builder まで、**agent のビジネスロジックは一行も変わっていない**。変わったのは、CompositeFilesystem がどのレイヤーで分離を提供するか、そしてどのメディアにデータを永続化するかだけである。

これはまさに、Harness が最初から約束していたことである: **agent のロジックを一度だけ書き、デプロイの形は必要に応じて切り替える**。今日から、その約束はリポジトリの中に二つの動く証拠を持つことになった。

個人向けアシスタントが必要なら [Claw README] のクイックスタートから始めてほしい。チーム / 企業のプラットフォームが必要なら [Builder README] から始めてほしい。どちらのパスも、同じ Harness に収束する — だからこそ私たちはこれを構築したのだ。
