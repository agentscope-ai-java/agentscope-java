---
title: "Coding Agent: 後半戦 — 個人の生産性から組織のエンジニアリングシステムへ"
---

今なお昔ながらのやり方でコードを手書きしている開発者は、無形文化財の継承者になる訓練をしているようなものだ。大多数はすでに Claude Code や Cursor のような Coding Agent を使っている。方向性は正しいが、シナリオが違えば解決策も違う — 個人の生産性のためにローカルに AI アシスタントをインストールすることと、組織内で AI 駆動のエンジニアリングコラボレーションシステムを構築することは、まったく異なる二つの次元である。前者にはすでに成熟したプロダクトがあるが、後者はまだ始まったばかりだ。本稿は後者についての話である。

---

## この問題を考えているのはあなただけではない

2025年末から2026年初頭にかけて、興味深いことが起きた。Stripe、Ramp、Coinbase がそろって自社内製の Coding Agent を公表したのだ — Stripe は自社のものを [Minions](https://stripe.com/engineering) と呼び、Ramp は Inspect、Coinbase は Cloudbot と呼んでいる。三社は互いに参照することなく独立に構築したにもかかわらず、ほぼ同じアーキテクチャに収束した。

これは偶然ではない。Coding Agent を「ターミナルで一人が使うもの」から「Slack や GitHub Issue を通じてチーム全体がいつでもトリガーできるもの」へとアップグレードすると、同じ一連のエンジニアリング上の問題によって同じ道へと導かれる。LangChain チームはこのパターンに気づき、2026年3月に [Open SWE](https://github.com/langchain-ai/open-swe) をリリースした — Stripe/Ramp/Coinbase の共通パターンをオープンソースのフレームワークへと蒸留したものだ。Open SWE の README の冒頭は率直である:

> Elite engineering orgs like Stripe, Ramp, and Coinbase are building their own internal coding agents — Slackbots, CLIs, and web apps that meet engineers where they already work.

「エンジニアがすでにいる場所で出会う」— それはエンジニアに新しいツールを学ばせることではなく、agent がエンジニアがすでに使っている Slack チャンネル、GitHub Issue、IM の会話に滑り込み、チームのワークフローの一部になることである。

私たちも AgentScope Harness を構築する中で同じ道を歩んだ。本稿では、公式サンプルの `agentscope-codingagent` を糸口として、本番レベルの Coding Agent が実際のデプロイでどのような壁にぶつかり、それをどう乗り越えたかを説明する。

---

## Coding Agent の二つの次元

まず位置づけを明確にしよう。Claude Code が最適化しているのは **「自分でコードをより速く書く」** ことである — あなたがタイプし、それが動き、あなたはそれが動くのを見守り、いつでも中断して修正できる。状態はあなたのローカルマシンに存在し、トリガーはあなた自身であり、信頼境界は単にあなたが自分のマシンを信頼していることに尽きる。

私たちが構築しているものは異なる問題を解決している: **「チームの何かちょっとしたタスクについて、自分で見守る必要すらない — agent に投げて、実行させ、それが開いた PR をレビューするだけでよい。」** トリガーは任意の Issue のコメント投稿者かもしれない。agent は誰も見ていないところで10分から1時間ほどリモートで実行される。Stripe のエンジニアは Slack で @Minions に「このバグを直して」と頼み、後でドラフト PR を受け取る — それが組織向け Coding Agent のあるべき姿だ。

この二つの形は機能面では重なる — どちらもコードを書き、コマンドを実行し、ファイルを編集できる — が、エンジニアリング上の制約はまったく異なる。Claude Code はあなた自身の自家用車である: あなたは運転手(自分自身)を信頼しているので、エアバッグ以上の保護はほとんど必要ない。組織向け Coding Agent はタクシー会社のフリート車両である — 乗客はオーナーではなく、運転はリモートで行われ、ドライブレコーダー、GPS 追跡、走行距離の制限、緊急ブレーキ、そして一台の壊れた車がフリート全体を止めないという保証が必要になる。

Open SWE はこの哲学を一文でまとめている: **「まず隔離し、その境界の中で完全な権限を与える。」** まず隔離し、それから権限を委譲する。

実際、ベンダーもこの方向に動いている。GitHub Copilot Coding Agent はすでに Issue へのアサインでトリガーでき、クラウド上で実行された後に自動的にドラフト PR を開ける。Claude Code にも、CI からプログラム的に呼び出せるヘッドレスモードがある。哲学は本質的に同じである — サンドボックスによる隔離、非同期トリガー、PR 駆動の出力 — ベンダーは先進企業が実証したパターンをプロダクト化し、すぐに使える SaaS サービスとしてパッケージ化している。Stripe、Ramp、Coinbase が内製を選ぶのは、主に自社のエンジニアリングシステムの特殊性による: 内部システムとの深い統合、データコンプライアンス要件、ワークフローのカスタマイズの度合いである。この二つの道は矛盾するものではない。どちらがより適しているかは、組織自身の制約とニーズに依存する。

AgentScope Harness が目指しているのは、この道に沿った共通のエンジニアリング上の問題を、組み合わせ可能な基盤機能へと抽象化することであり、内製を選んだチームがゼロから始めなくて済むようにすることである。

---

## まずは動かしてみる

一番手早く試す方法は — 環境変数を一つ設定し、Maven コマンドを一つ実行すれば、ローカルで動くインタラクティブな REPL が手に入る。Docker も webhook も GitHub App も不要である。

```bash
export DASHSCOPE_API_KEY=sk-...
cd agentscope-java
mvn install -pl agentscope-examples/agents/agentscope-codingagent -am -DskipTests -q
mvn exec:java -pl agentscope-examples/agents/agentscope-codingagent
```

起動後、`You>` プロンプトに到達し、agent は自身の Workspace `~/.agentscope/codingagent/workspace/` で動作する。何も設定しなくても、すでに完全な Workspace、Session の永続化、長期記憶が手に入る。

```
You> write hello.txt with a haiku about Java
You> clone https://github.com/owner/repo into the workspace and tell me what it does
You> review https://github.com/owner/repo/pull/42
```

この時点で、ローカルデモは動く。しかし、デモと本番の間の距離は、ほとんどの人が思うよりもはるかに大きい。

---

## 壁1: 実行の隔離

agent に `execute` ツールを与えて shell コマンドを実行できるようにする。初日はエキサイティングだ — `git clone` を実行し、`mvn test` を実行し、ビルド全体を自分で通せる。二日目に問題に気づく: **トリガーはもはやあなたではない。** 任意の GitHub Issue のコメント投稿者や任意の Slack ユーザーが agent にコードを実行させることができ、あなたのホストはモデルが決定したコマンドに直接晒されることになる。

これは、組織向け Coding Agent を構築するすべてのチームが最初にぶつかる壁である。Coinbase は自社構築のサンドボックスインフラで、Ramp は Modal のクラウドコンテナで、Open SWE は Modal、Daytona、Runloop など複数のバックエンドをサポートする抽象化でこれを解決する。私たちも同じ抽象化を行った — `FilesystemSpec` が統一インターフェースであり、Docker コンテナ、リモート KV、ローカルファイルシステムがプラガブルな実装である。Docker を例に取ると:

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("coding")
    .model(model)
    .workspace(workspace)
    .filesystem(new DockerFilesystemSpec()
        .image("agentscope/coding-sandbox:latest")
        .isolationScope(IsolationScope.SESSION))
    .build();
```

この一行で、`read_file`、`write_file`、`execute` などのすべての組み込みツールは自動的にサンドボックスバックエンドに切り替わり、agent のコードはまったく変更する必要がない。`IsolationScope.SESSION` により、各 GitHub Issue / PR / IM 会話が独自の空間で実行されることが保証される。

サンドボックスは「ホストに害を及ぼさない」という問題を解決する。しかし次の問題がすぐにやってくる。

---

## 壁2: 状態の継続性が壊れる

ユーザーが PR にもう一つコメントを追加する: 「テストをもう一つ追加して」。agent は前の環境から続きをやらなければならない — 五分待って `git clone` + `npm install` をやり直すのは耐えられない。

Open SWE は「永続的なサンドボックス」でこれを解決している — 同じスレッド内のフォローアップメッセージは同じサンドボックスを再利用する。私たちの解決策はより粒度が細かい: 各 `call()` の終わりに、サンドボックスは Workspace の状態をスナップショットにパッケージ化し、必要に応じて次回復元する。コンテナがまだ存在していれば直接続行する。コンテナが失われていれば、スナップショットから新しいものを起動する。スナップショットがなければ、完全な初期化を行う。スナップショットのバックエンドはローカルファイル、OSS、Redis のいずれでもよい。本番環境では、設定を一行追加するだけでよい:

```java
.snapshotSpec(new OssSnapshotSpec(ossClient, "my-bucket", "agentscope/"))
```

持ち越す必要があるのはサンドボックスの状態だけではない。**会話履歴、圧縮された要約、Plan の状態、todo リスト、権限ルール — AgentState 全体が各 `call()` の終わりに自動的に永続化され、同じ `(userId, sessionId)` での次の `call()` で自動的にロードされる。** デフォルトではローカルファイルに保存される。マルチレプリカの本番環境では、Redis への切り替えは一行で済む。Redis に切り替えれば、ノードがクラッシュしても Session は別のノードに浮上し、ローリングリリースは新しい pod で自動的に復旧し、GitHub Issue の会話から DingTalk へ途中で切り替えることさえできる — `sessionId` が一致している限り、記憶は保持される。

---

## 壁3: コンテキストが爆発する

長い Issue が数十回の会話ラウンドを重ね、`git diff` の出力が数万文字になり、`mvn test` のログが数十キロバイトに達する — モデルのコンテキストウィンドウはあっという間に埋まってしまう。これは長時間実行タスクの Coding Agent を構築するすべてのチームがぶつかる問題である。Open SWE の基盤にある Deep Agents フレームワークは、ファイルベースのメモリを使ってオフロードを行い、大きな結果を会話履歴に残さずファイルに書き出す。

私たちの解決策は四つの独立した組み合わせ可能な仕組みである: **会話の要約** はメッセージ数が多くなりすぎると自動的にトリガーされ、末尾を逐語的に保持しつつ前半を要約に圧縮する。**大きなツール結果のオフロード** は長すぎる出力を Workspace のファイルに書き込み、コンテキストには先頭と末尾のおよそ2Kだけと `read_file` のパスのヒントを残す。**引数の切り詰め** は `write_file` への大きな引数も切り詰める。**オーバーフロー時のフォールバック** は `context_length_exceeded` に実際に遭遇したときに緊急圧縮を行いリトライする。

```java
.compaction(CompactionConfig.builder()
    .triggerMessages(50).keepMessages(20)
    .truncateArgs(CompactionConfig.TruncateArgsConfig.builder()
        .maxArgLength(2000).build())
    .build())
.toolResultEviction(ToolResultEvictionConfig.defaults())
```

**これはオプションではない。** Coding Agent は必ず長い Session を実行することになり、必ず大きな diff を生成する。これら二つを有効にしなければ、いずれ壁にぶつかる。

同時に、`MEMORY.md` は日々の会話の流れから長期的な事実を定期的にマージしていく。しばらく実行すると、agent は自分でチームのルールを学ぶ — 「このリポジトリのテストコマンドは `mvn -pl module test` だ。ルートで `mvn test` を使うと遅すぎるから使うな」— そして次回はもう聞かなくてよくなる。

---

## 壁4: 複数人が同時に使う

最初の三つの壁 — 隔離、状態の継続性、コンテキスト管理 — は「一つの agent Session が安定して動く」という問題を解決する。しかし組織向けのサービスは最初からマルチテナントである: 何十もの Issue、何十もの PR、何十もの IM 会話が同時に走っており、それぞれが独自のコードリポジトリ、依存関係ディレクトリ、会話履歴、長期記憶を持ち、**それらは決して交差してはならない**。

その時になって初めて、Open SWE の README が「複数のタスクが並列で実行される — それぞれ独自のサンドボックスで、キューイングなし」をコア機能としてリストアップしている理由が本当にわかる。それは見せびらかしではなく、必須要件なのだ。

私たちは分離の粒度を制御するために `IsolationScope` を使う。`SESSION` は各 sessionId に独自のサンドボックスを与え、`USER` は同じユーザーの複数の会話が一つのリポジトリクローンを共有できるようにする。分離はサンドボックス層だけではない — Session 状態、記憶、サブエージェントタスクもすべて同じ粒度に従うため、開発者が自分で心配する必要はない。

並行性の制御もこのレイヤーに存在する: `RunDispatcher` + `MessageQueueHook` は、同じスレッドで同時に実行される推論が一つだけであることを強制する。agent が実行中にユーザーが別のコメントを追加すると、新しいメッセージは現在の推論を中断せず、代わりにキューに入れられ、次のラウンドの前に注入される — Open SWE の `check_message_queue_before_model` ミドルウェアと同じ考え方である。`ThreadBudgetHook` はスレッドごとのモデル呼び出し予算の上限を設け、`ModelCallLimitHook` はグローバルにそれを制限する — **一人のユーザーの暴走ループが会社全体のクォータを食い尽くしてはならない**。

---

## 壁5: Agent は複数の入口を扱わなければならない

Stripe の Minions は Slack を経由し、Coinbase の Cloudbot も Slack を経由し、Open SWE は Slack + Linear + GitHub に同時に接続する。国内のシナリオでは DingTalk と Feishu も必要になる。組織向け Coding Agent に共通する信念は: **ユーザーに新しいインターフェースに切り替えて agent を探させるのではなく、agent がユーザーのいる場所に現れるようにする**ということである。

私たちは Harness の上にチャネルアダプタ層を追加し、異なる入口からのイベントを統一的に `(threadId, message)` にマッピングする。`github:issue:owner/repo#42` は SHA-256 を通じて一つの UUID に収束し、DingTalk と Feishu についても同様である。この決定論的なマッピングにより、同じ Issue へのすべてのコメントが同じ agent Session にルーティングされることが保証され、会話履歴は自動的に復元される。

---

## これらの壁を乗り越えた後: いくつかの学び

### コンテキストエンジニアリングはバズワードではなく、エンジニアリング上の必然である

業界は現在、「agent にどうコンテキストを与えるか」という問いを Context Engineering と呼んでいる。興味深いのは、ほぼすべての主要な Coding Agent が独立に同じパターンへ収束していることである: Claude Code には `CLAUDE.md` があり、GitHub Copilot には `.github/copilot-instructions.md` があり、Open SWE には `AGENTS.md` がある。**リポジトリレベルの規約は system prompt にハードコードすべきではなく、ファイルであるべきだ — バージョン管理でき、レビューでき、独立して更新できる。**

私たちの Workspace はこの考え方をさらに推し進める: ペルソナと行動規約を定義する `AGENTS.md` に加え、チームの SOP(コミット規約、テスト規約)のための `skills/`、サブエージェントを宣言するための `subagents/`、ドメイン知識のための `knowledge/`、長期的な事実を蓄積するための `MEMORY.md` がある。Workspace は Git として管理され、CI で検証され、デプロイ時にすべてのレプリカへ hydrate される。**頻繁に変わるべきはこれらのファイルであり、Java のコードではない。**

### ツールの数よりツールの選定の方が重要

Stripe が Minions の経験を公開した際、自社の agent には約500個のツールがあると述べたが、「ツールの数よりツールの選定の方が重要」だと強調した。Open SWE も同じ哲学に従い、約15個のコアツールしか公開していない。私たちのアプローチも似ている — 組み込みのツールセットはファイル操作 + shell 実行 + メモリ検索に限られ、ビジネスツールは `toolkit.register(...)` を通じて必要に応じて登録される。

### 重要なステップはプロンプトだけに頼ることはできない

**「テストを実行するのを忘れずに」とプロンプトでモデルに伝えるだけに頼ることはできない — 重要なステップは決定論的なロジックによって保証されなければならない。** GitHub Copilot Coding Agent は完了後にリポジトリの既存の CI パイプラインを通じて結果を検証する。Open SWE には `open_pr_if_needed` ミドルウェアが安全策として存在する — agent が PR を開くのを忘れた場合、ミドルウェアが自動的にそれを行う。Harness のミドルウェアの仕組み(`MessageQueueHook`、`ThreadBudgetHook` など)も同じ考え方に従う: **モデルに任せることと、決定論的なコードによって保証することの境界線は、明確に引かれなければならない。**

Open SWE のブログの一文が的確にこれを言い表している: agentic(モデル駆動)と deterministic(ミドルウェア駆動)の分離こそが、agent を「デモが動く」から「本番で信頼できる」へと引き上げるものである。

もう一つ: **出力の契約としてのドラフト PR。** Copilot Coding Agent であれ、Open SWE であれ、Stripe Minions であれ、agent の出力はドラフト PR であり、マージ前には常に人間のレビューが必要である。agent が本番のコードを直接変更することはない — これは組織向け Coding Agent にとっての基本的な安全性の前提である。

### 大きな変更は慎重に考える

agent に「認証モジュール全体をリファクタリングして」と直接取り組ませるのはリスクが高い — 進めながら考え、変更していくうちに、途中で何かを壊すかもしれない。Harness の Plan Mode は、これを「まず考える → プランを書く → 人間が確認する → それから行動する」というフローに固める。有効化すると、agent は読み取り専用のフェーズに入り、プランを抜けるには人間の確認が必要になる。Coinbase の Cloudbot の「Agent Councils」も同じ考え方に従う — リスクの高い操作の前に人間の承認ノードを追加する。**モデルが間違いを犯さないことを祈るのではなく、プロセスの制約に置き換える** のである。

### サブエージェントは単なるあれば嬉しい機能ではない

Open SWE は Deep Agents の `task` ツールをサブエージェントのディスパッチに使い、Stripe は Blueprints でオーケストレーションを行い、Ramp は Sessions + Child Sessions を使う。Harness のサブエージェントの使い方は軽量だ — Workspace に責務とツールセットを宣言する markdown ファイルを書けば、メインの agent は `agent_spawn` を呼び出して委譲できる。バックグラウンド呼び出しには `timeout_seconds=0` を追加すればメインの agent はブロックされない。サブエージェントが完了すると、フレームワークは自動的に結果を次の推論ラウンドに注入する。

---

## 単一マシンからエンタープライズへ: 進化のパス

これらすべての壁を一度に乗り越える必要はない。Harness は、最もシンプルな形から始めて、必要に応じてアップグレードできるように設計されている:

**ステージ1: ローカル CLI。** 設定なしで、`execute` はホストの `sh -c` で実行され、状態はローカルファイルに保存される。信頼されたローカル環境でのみ使用する。

**ステージ2: サンドボックスを追加する。** `.filesystem(new DockerFilesystemSpec()...)` の一行で、すべての実行がコンテナ内に移動する。各 Issue/PR が一時的なコンテナを持ち、ホストの攻撃対象は露出しない。

**ステージ3: マルチレプリカの分散化。** `stateStore` を Redis に置き換え、サンドボックスのスナップショットを OSS に保存し、並行性制御のために `executionGuard` を追加する。この時点で水平にスケールできる — ロードバランサーの背後で N 個のレプリカを実行し、どのレプリカもどのユーザーのどの会話でも引き受けられる。

```java
.filesystem(new DockerFilesystemSpec()
    .image("agentscope/coding-sandbox:latest")
    .isolationScope(IsolationScope.USER)
    .snapshotSpec(new OssSnapshotSpec(ossClient, "bucket", "prefix/"))
    .executionGuard(RedisSandboxExecutionGuard.builder(jedis)
        .leaseTtl(Duration.ofMinutes(30)).build()))
.stateStore(RedisAgentStateStore.builder().lettuceClient(redisClient).build())
```

**ステージ4: 可観測性とレート制限。** Prometheus のメトリクス、モデルの予算、上流のレート制限とリトライ — これらが揃って初めて「立ち上げ後もオンラインであり続けられる」システムになる。

---

## この収束が教えてくれること

本稿で触れたプロジェクト — Stripe Minions、Ramp Inspect、Coinbase Cloudbot、LangChain Open SWE、GitHub Copilot Coding Agent、Claude Code、そして AgentScope Harness — を振り返ると、言語、エコシステム、デプロイの形はそれぞれ異なるが、コアのアーキテクチャ上の意思決定は驚くほど一貫している: Session ごとの隔離されたサンドボックス、決定論的なスレッド ID ルーティング、ミドルウェアによるインターセプトチェーン、agent ランタイムでのメッセージキュー注入、リポジトリレベルの指示ファイル、そして出力の契約としてのドラフト PR である。

この収束は互いを模倣した結果ではない。**それは同じ一連の問題によって強制されたエンジニアリング上の必然である。**

---

## おわりに

Coding Agent 時代の前半は個人の生産性についてのものだった — より賢いモデル、より正確な補完、よりスムーズなローカルツール。後半は戦場をエンジニアリングへと移す: 「デモを一度動かせる」ことを「チーム全体のための24時間365日安定したサービス」へと変える方法である。Stripe から GitHub まで、LangChain から AgentScope まで、誰もが異なる出発点から始めて同じアーキテクチャにたどり着いた。その収束自体が、最良の道標なのだ。

本稿で触れた codingagent のサンプルは完全で読みやすいものだ。クローンして一度実行し、それからソースコードを読むことをお勧めする — ここで議論したすべてのエンジニアリング上の問題が、実際のコードにマッピングされている。

さらに深く掘り下げる: [Harness アーキテクチャ](/v2/ja/docs/harness/architecture) · [Workspace](/v2/ja/docs/harness/workspace) · [Sandbox](/v2/ja/docs/harness/sandbox) · [コンテキスト Compaction](/v2/ja/docs/harness/compaction) · [Subagent](/v2/ja/docs/harness/subagent) · [Skill](/v2/ja/docs/harness/skill) · [Plan Mode](/v2/ja/docs/harness/plan-mode)
