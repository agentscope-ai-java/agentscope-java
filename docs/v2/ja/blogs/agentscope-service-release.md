---
title: Agentscope Service Release
---

**AgentScope Service** — AgentScope Harness の上に構築された Agent コントロールプレーン。


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785989956180-2b6581fd-cf41-4155-baaf-08db90a6eb5d.png)


+ **AgentScope Service はコントロールプレーンである。** 企業内のあらゆる Agent に対して、Agent の登録、発見、分散協調のサービスを提供する。AgentScope、LangChain、ADK、Claude / Qoder といった主要な Agent ランタイムと連携し、Agent のメトリクスを確認し、稼働中の Session を操作する — 例えば Session コンテキストを圧縮する — ための単一の場所を提供する。
+ **AgentScope Service はローコードでの Agent 作成とデプロイを提供する。** AgentScope Harness ランタイムの上に構築されており、一つの Managed Agents プラットフォーム上で複数の Agent を統一的な運用のもとで実行できる。プラットフォームが Harness の機能をホストする一方、ツール実行は自分で制御するサンドボックスに委譲できる。
+ **AgentScope Service に登録された Agent は、一つまたは複数の Team として組み合わせることができる。** その Agent が自己ホストの AgentScope ランタイムであっても、ローコードの Managed Agent Harness ランタイムであっても、Agent 同士を連携させてより複雑な作業に取り組ませることができる。

## AgentScope Service とは何か

AgentScope Service は既存の Agent フレームワークを置き換えることを意図したものではない。統一されたコントロールプレーンを追加することで、Claude、OpenClaw、QwenPaw などさまざまなフレームワークとスタックで構築された Agent を、一箇所で統治できるようにする。

企業にはすでに Agent を構築する多くの方法がある。AgentScope は強力な Agent フレームワークとして、Agent 構築のためのエンドツーエンドのパスを提供する。しかし **従来型の Agent フレームワークは、もはや Agent を構築する唯一の方法ではない**。Coding Agent 製品はより多くの領域に拡大しており、Claude SDK や Qoder CLI などのツールでエンタープライズ Agent を構築することは一般的な選択肢になりつつある。

1. **Agent フレームワークを使う**
   AgentScope、LangChain、ADK などのフレームワークで agent ループをビジネスサービス内に直接実行する。柔軟性は高いが、テナント分離、バージョンのロールアウト、Session の復元、HITL、イベントの永続化、レプリカをまたぐ協調は、すべて各チームが自前で構築しなければならない。すべてのビジネスラインがそれぞれこれらの部品を再発明すると、標準がばらばらになる。
2. **Coding Agent や個人向けワークスペースアシスタントを使う**
   Claude Code、他の Coding Agent、個人向けワークスペースアシスタントなどが例である。素早く始められ、ローカルでは素晴らしい体験だが、状態は開発者のマシンに存在するため、共有しづらく、監査しづらく、複数チームでの所有には向かない。ノートパソコンを閉じると、作業もしばしばそこで止まってしまう。
3. **ローコード、あるいは Managed Agents プラットフォームを使う**
   従来のローコードプラットフォームは、ビジュアルなノードから Agent を組み立てる。始めやすい一方で、メモリ管理、コンテキストの compaction、ツールの配線などを密な設定サーフェスとして露出しがちで、品質と安定性の両方の責任をユーザーに残してしまう。Managed Agents は完全にホストされたクラウド Harness の機能を強調することで、ユーザーがその運用負担を負わなくて済むようにしつつ、顧客 VPC 内で動く Hands を含め、ツール実行についてはユーザーにより多くの制御を与える — しかし、エンタープライズのマルチエージェント協調はまだ不完全だった。

これらの道は互いに排他的ではない。一つの企業の中で、R&D は Coding Agent を使い、ビジネスプラットフォームは AgentScope を実行し、新しいプロジェクトは初日からホストされた Harness を使いたい、というような組み合わせはよくあることだ。AgentScope Service は特定の agent フレームワークやプラットフォームにロックインすることなく、agent ランタイムをまたいだ統一的なコントロールプレーンの機能を提供する。

### コントロールプレーン

コントロールプレーンは AgentScope Service の中核である。すべての Agent アプリケーションはこれを通じて登録される。SDK または Sidecar 経由で、主要な Agent フレームワーク(AgentScope、LangChain、ADK)に加え、Claude、Qoder などのランタイムをサポートする。


Dashboard はコントロールプレーンのビジュアルコンソールである。オンラインの agent、デプロイされたインスタンス、稼働中の Session、Token 使用量、その他のグローバルなシグナルについて、フリート全体のライブビューを提供し、運用者がクラスタの状態を把握できるようにする。


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785934371848-7b1b934e-11ed-4625-97cc-820f2fe5d214.png)


Dashboard からは、Session の詳細を調べたり、稼働中の Session のライブなコンテキスト状態(コンテキストの各部分がどのように寄与しているかを含む)を見たり、Session のコンテキストを動的に調整・圧縮したり、稼働中の会話に介入したりすることもできる。


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785946414310-ff29cee8-2b2b-40df-9ec8-0211ee03fe8c.png)

### Managed Agents

Managed Agents は `agentscope-builder` プラットフォームから進化したものである。それらは引き続きローコードの Agent プラットフォームとして、開発者に SaaS スタイルの Agent 定義とホスティング実行を提供する。今回のアップグレードでは、推論とツール実行の分離をさらに強調している。Harness の機能はより徹底的にホストされる一方、ツール実行はユーザーがより強く制御できるようになる。


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785948183107-014a5cb1-6fcf-4b04-93cb-f01341b35350.png)


Agent の定義は AgentScope Harness の核となる設計に従う。まず Workspace や Memory といった基礎的な概念を定義し、それから Workspace と Memory を Agent に関連付けて作成する。


Workspace を定義する:


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785948616059-4d7be456-50bf-4e68-9ebf-3d48ce0ca9d3.png)


Agent を定義する:


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785948697346-865380da-fb2c-420b-9968-4275b51a85b6.png)


今回のアップグレードにおける最大の変化は、ホストされたランタイムのロジックとアーキテクチャ — すなわち Managed Agents である。プラットフォームは静的な定義(Agent、Workspace)と動的なランタイム(Environment、Session)をきれいに分離し、Environment と Session を使って Agent が実際にどう動くかをオーケストレーションする。


Session を作成し、self-hosted なサンドボックスランタイム環境をバインドする:


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785948796759-2723bb0e-e25e-49a6-aad4-27f8eb368d8d.png)


Session を作成しただけでは SSE イベントストリームは開始しない。会話と推論の全パスは、ユーザーがメッセージを送信したときにのみ始まる。以下のように、コンソールのチャットページからユーザーメッセージを送信できる:


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785948840673-56827ddd-93f4-4091-9b7c-bbafa217a511.png)


Agent を作成 → Environment を作成 → Session を作成 → 最初のメッセージを送信 → Dashboard でイベントストリームを見る。Session を作成しただけでは Agent は起動しない。長時間実行される作業について、Managed Agents は特に **リカバリ可能性** を重視している: イベントは永続化され、状態は再構築可能で、HITL は一時停止と再開が可能である。フロントエンドのリフレッシュやサービスレプリカの変更が、最初からやり直すことを意味してはならない。


ランタイムの設計は Claude Managed Agents と密接に整合している。Harness のインフラとランタイムは完全にホストされている(AgentScope Harness Runtime に支えられている)。Brain/Hands の分離により、ユーザーはツールが実際にどこで動くかをより制御できる。デプロイはコントロールプレーンと Managed な Dataplane に分かれる — 以下の本番デプロイのセクションを参照。

### Agent Teams

AgentScope Service のコントロールプレーンに登録されたすべての Agent — フレームワーク(LangChain、AgentScope、ADK、Claude SDK など)を通じて自己デプロイ・登録されたものであれ、ローコードのパスで Managed Agent として作成されたものであれ — は、一つまたは複数の Agent Team に組み込まれ、複雑な作業で協調できる。


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785948895276-d0221173-9683-4a94-b281-55f9372cee65.png)

AgentScope Service において、Team はチャットルームではない。それは操作可能なコラボレーションユニットである: タスクはクレームでき、プランは承認でき、メンバーは起こすことができ、Session が終わったからといって状態が消えることはない。よくあるパターンは、作業を分解・受け入れる Lead と、能力に応じて調査・コーディング・検証などのサブタスクをクレームする Member である。プラットフォームがメッセージルーティング、タスクボード、ライフサイクルを所有する — ビジネスコードが一時的なマルチプロセス通信を手作りする必要はない。

一つ触れておく価値のある点として、AgentScope Framework はネイティブに Agent Teams をサポートしている。その仕組みは、分散タスク管理とスケジューリングに AgentScope Service コントロールプレーンを使う。そのため、メインエージェントの開発中に AgentScope Framework のネイティブな Teams 機能を使ってマルチエージェントコラボレーションを構成することも、コンソール上で特定の複雑なタスクのために独立した Agent を動的に組み立てることもできる。どちらのパスを選ぶかはシナリオ次第である。

## アーキテクチャ概観

### 全体アーキテクチャ


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785984168683-7e939049-046d-4ffa-b30d-1c0e6c0ff01b.png)

人間は Dashboard(ブラウザ)と REST API(SDK / curl / サードパーティ統合)という二つの入口から AgentScope Service コントロールプレーンに到達する。コントロールプレーンの下では、四つの Agent 接続モデルが統合的に管理されている: ネイティブな AgentScope 接続、`instrument()` 経由の LangChain、そして Sidecar 経由の Claude / QwenPaw である。

### Managed Agents


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785976191807-3dde2cf8-ece0-4819-b376-328b498ed00c.png)


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785976204028-86690651-b369-4c47-b3eb-73c9f7da508e.png)

### Agent Teams のコラボレーションフロー

Team のメンバーは、同じフレームワークやホスティングモデルから来る必要はない。コンソールでは、すでにコントロールプレーンに登録済みの複数の Agent を選び、誰が Lead で誰が Worker かを選ぶだけでオーケストレーションが完了する — Lead がタスクを作成・割り当て、Worker がそれをクレームして実行し、コラボレーション状態はコントロールプレーンによって維持される:


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785985724730-b8f8d88c-669a-430c-bbf6-0c9f2d64b0f7.png)

図の重要なポイント:

+ **メンバーは異種混在でよい。** 例では、Lead と Worker 1 は Managed Agent(コントロールプレーンが `teamContext` にバインドされた Session を作成し、`user.message` を届けて起動する)、Worker 2 は自己デプロイのネイティブ AgentScope ランタイム、Worker 3 は LangChain(または Sidecar 経由で接続された Claude)である。コントロールプレーンはメンバーごとに異なる参加パスを使う(Managed Agent は `find-or-create session`、BYO agent は `team_join` コマンドを使う)が、Lead に見えるコラボレーションモデルは同じである: どのメンバーもタスクを割り当てられ、Team 内でメッセージを送受信できる。
+ **Lead による割り当てと Worker によるクレームの両方が存在する。** タスクを作成する際、Lead は `owner` を設定(特定の Worker に割り当て)でき、その Worker は claim → start → complete という流れを踏む。Lead は `owner` なしでタスクを作成し、Task Board に置いておくこともでき、手の空いた Worker が `POST .../claim` で自らクレームする。両方のパターンが同じ Task Board 上で共存するため、Lead はすべての空きスロットを見張る必要はない。
+ **メッセージはユニキャストとブロードキャストの両方をサポートする。** Mailbox は `to=member` を伴う宛先指定メッセージ(例: Lead が特定の Worker をつつく)と、`to` が空のブロードキャストメッセージ(すべてのメンバーに見える)をサポートする。両者は同じ永続化チャネルを共有する。
+ **コラボレーション状態は単一の Session に紐付いていない。** Task Board と Mailbox のデータは、どのメンバーの Session ライフサイクルからも独立している: Worker のプロセス再起動や Session の終了によって、タスクの存在有無やメッセージの追跡可能性が消えることはない。だからこそ、Worker がクラッシュしたとき、コントロールプレーンはそのメンバーを `Lost` とマークして復旧をトリガーでき、Team 全体の進捗を失わずに済む。

### AgentScope ネイティブフレームワーク + コントロールプレーン

AgentScope Framework 自体がすでに完全なエンタープライズ Agent スタック — Harness、Agent Teams、マルチエージェントコラボレーション、Sandbox による分離など — を提供している。実際のエンタープライズデプロイでは、これらの機能の多くが分散協調に依存する。AgentScope Service コントロールプレーンは、AgentScope にこのネイティブな分散協調を提供する。

#### コントロールプレーンによる分散協調

AgentScope の `HarnessAgent` が単一インスタンスから複数レプリカへと移行すると、Session 状態、Workspace ファイル、Sandbox のスナップショットと並行性ロック、レプリカをまたぐメッセージング、非同期ツール、サブタスク、Turn の並行性制御は、もはや「プロセスのメモリが正本である」という前提に立てなくなる。

以下の図は **ランタイムのトポロジー**、すなわち複数の `HarnessAgent` レプリカがコントロールプレーンと `AgentStateStore` バックエンドとどのようにやり取りするかを示している — `DistributedStore` のインターフェース定義ではない。


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785985855412-a79588d2-9ae3-4922-a30b-337ff4e6e526.png)


決して互いを経由しない二つの独立したパスが最も重要である:

+ **上向き: 協調用の API 呼び出し。** 各 `HarnessAgent` レプリカは SDK を通じてコントロールプレーンを呼び出す。開発者が実際に目にするのは、四つのホスト化された Harness 機能である。その裏側では、`BaseStore`、`SandboxSnapshotSpec` / `SandboxExecutionGuard`、`MessageBus`、`TaskRepository`、`SessionTurnGate`、`AsyncToolRegistry` などのインターフェースにマップされる。協調状態はコントロールプレーン自身の Postgres に着地するため、ビジネス側は別のインフラスタックを必要としない。
    - **Workspace の共有**: Workspace ファイル(`MEMORY.md`、`skills/`、`sessions/` など)に加え、Sandbox のスナップショットと並行性ロックにより、どのレプリカも同じ Workspace を読み書き・復元できる。
    - **Agent Teams**: レプリカをまたぐメッセージ配送とサブタスクの委譲により、Lead / Member 間のユニキャスト、ブロードキャスト、タスクのクレームが、そのメンバーがたまたまどのレプリカで動いているかに依存しない。
    - **Session の並行性制御**: レプリカをまたぐ同一 Session に対する Turn レベルのゲートで、二つのレプリカが同じ会話の Turn を同時に進めてしまうことを防ぐ。
    - **非同期ツール実行**: 長時間動作するバックグラウンドツールで、その状態と結果をどのレプリカからも観測・収集できる。
+ **下向き: Session 状態バックエンドへの直接アクセス。** `AgentStateStore`(会話コンテキスト、compaction の要約、権限ルール、Plan Mode の状態など)は**コントロールプレーンを経由しない**。各レプリカはビジネス側が提供する Redis / MySQL / Postgres / OSS バックエンドに直接接続する。コントロールプレーンは、Session の並行性制御(`SessionTurnGate`)と、`AgentStateStore` 自体の `getVersioned` / `saveIfVersion` による楽観的並行性制御(CAS)を任意で補完するのみで、レプリカをまたぐ重複した LLM Turn を減らす — それが協調するのは *誰がこの Turn を実行してよいか* であって、状態データそのものではない。

開発者にとってこれが意味するのは、`HarnessAgent.builder()` に `distributedStore` を設定する(協調コンポーネントは `ControlPlaneStores.fromEnv()` を通じて、`AgentStateStore` は自前の共有バックエンドを指す)だけで、Session の復元、ファイル共有、Sandbox のスナップショット、タスクキューを一つ一つ再構築することなく、Agent に本当の水平スケールを与えられるということである。

#### 自己組織化する Agent Teams

AgentScope Framework は、閉じたループの Agent Teams 機能も備えている。チームがどう形成されるかは、コントロールプレーンによる直接的なオーケストレーションとは異なる: 開発時にメンバーを固定の Team トポロジーに事前配線する必要は **ない**。Subagent パターンと同様に、呼び出し可能な Subagent のプール(`agentRef`)を Main Agent に事前登録しておくだけでよい。実行時、Human(または上流のシステム)が Main Agent にチームが必要な作業を記述したメッセージを送ると、Main Agent 自身がチームを組むかどうか、事前登録された Subagent のうちどれを Worker として使うかを判断し、動的に Team を作成する:


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785987685062-a679fc73-257a-4f74-8ff8-d214a82b7c88.png)


上図の重要なポイント:

+ **事前に定義するのは候補メンバーのプールであり、Team ではない。** 開発時には `reviewer`、`security-scanner`、`perf-tester` のような Subagent を Main Agent に取り付けるだけである(Subagent パターンと同じ `agentRef` 登録の仕組みを共有する)。誰がいつ誰とチームを組むかは未決定のままであり、Lead / Worker の構造を事前に設計することはない。
+ **トリガーはランタイムのメッセージであり、コードやコンソールの設定ではない。** Human が Main Agent に「これに対応するチームを組んで」といった意図を含む普通のメッセージを送ると、Main Agent の推論が `createTeam`(追加メンバーが必要な場合は `spawnMember` も)を呼び出すことを判断し、自身を Lead に設定し、選ばれた Subagent を Worker としてインスタンス化する。この判断は一回の LLM の Turn の中で起こる — 人間による事前のオーケストレーションもコードの変更も不要である。
+ **一度形成された Team は同じコラボレーションの仕組みを使う。** Lead と Worker は一つの `TeamClient`(Task Board + Mailbox)を共有する。それはコントロールプレーン不要の `LocalTeamClient`(閉じたループ、`BaseStore` 上での直接的な楽観的並行性制御)でもよいし、レプリカをまたぐ協調と Dashboard の可観測性を得られる `ControlPlaneTeamClient` でもよい。これは上述のコンソールによるオーケストレーションパスと合致しており、違いは Team がどのように形成されるかだけである。

この機能と [Subagents](/v2/ja/docs/harness/subagent) パターンは同じ Subagent の定義を再利用しているが、コラボレーションモデルは完全に異なる — そして混同しやすい — ので、明示的に比較しておく価値がある:

```text
Subagent mode (one-way delegation, peers isolated)
    Main Agent ──task()──▶ Subagent A ──result──▶ Main Agent
    Main Agent ──task()──▶ Subagent B ──result──▶ Main Agent
    Subagent A and Subagent B have no communication path and do not share a task list

Agent Team mode (peer collaboration, shared state)
    Lead ──createTask / assignTask──▶ Worker A
    Worker A ◀── sendMessage / broadcastMessage ──▶ Worker B
    Worker A and Worker B can both claim unassigned tasks on the shared Task Board
```

+ **Subagent: 一方向の委譲、ピアは分離されている。** Main Agent は Task ツールを通じて Subagent に指示を送る。Subagent は独立したステートレスなコンテキストで実行され、結果を Main Agent に報告する。二つの Subagent には直接の通信パスがなく、互いの存在を知らず、タスクリストを共有することもない — すべての協調ロジックを Main Agent が単独で保持する。
+ **Agent Team: ピアによるコラボレーション、共有状態。** Team 内の Lead と Worker は一つの Task Board と Mailbox を共有する。Lead は特定の Worker に `assignTask` でき、Worker 同士は `sendMessage` / `broadcastMessage` で会話できる。未割り当ての作業は、手の空いた誰かが `claimTask` する。コラボレーション状態はもはや発起者だけが所有するものではなく、チーム全体で維持される共有データである。

要するに: Subagent は「指示を送り、結果を待つ」階層的な委譲であり、Agent Team は共有ボード、相互クレーム、直接の通信を伴うピアのコラボレーションである。この図が強調しているのは、AgentScope のネイティブな Agent Teams が、Main Agent にランタイムで、必要に応じて、Subagent プールからピアで協調する Team を、事前にチーム構造を計画することなく組み立てさせる、ということである。これは、コンソールによる動的オーケストレーションのパス(Human がコンソール上でライブに Team を組み立てる)を補完するものである。どちらのパスも同じ `TeamTool` / `TeamClient` プログラミングモデルを共有し、必要に応じて選択できる。

#### マルチエージェントコラボレーション: リモート Subagent

Agent Teams や AgentScope Subagent の委譲先は、同じプロセス内にある必要はない。それは、同じ `HarnessAgent` 内のローカルな Subagent、別の Managed Agent(Dataplane 上で動いている)、あるいは `instrument()` 経由で接続された LangChain Agent であってもよい。

リモートの場合、AgentScope Service コントロールプレーンの役割は、Agent A がターゲットがどこにあるか、どのフレームワークを使っているかを気にせずに `delegate` 呼び出しを発行できるようにすることである:


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785987846445-098ee5ce-1d73-4343-a3b2-09cc17ebf963.png)


上図は、リモート Subagent 呼び出しに対するコントロールプレーン経由の **トラフィックプロキシ** を示している — 一回限りの API 設計ではない:

+ **まずローカル — できるときはコントロールプレーンを飛ばす。** `techlead` が Agent A と同じ `HarnessAgent` プロセス内で宣言されたローカルな Subagent であれば、委譲はプロセス内の呼び出しであり、コントロールプレーンは関与しない。これは最も低レイテンシのパスであり、通常は最も一般的なパスでもある。
+ **インスタンス間 / フレームワーク間では、コントロールプレーンが三つの仕事をする: 発見、認証、プロキシである。** まず Agent A と `techlead` の間に正当なコラボレーション関係があることを確認し(同じ Team か、ホワイトリスト ACL)、次にフリートのレジストリで Agent ID からターゲットインスタンスを検索する — ターゲットは Managed Agent(Dataplane の Session Turn API に転送)かもしれないし、`aistio.instrument()` 経由で登録された LangChain Agent(報告されたチャットエンドポイントに転送)かもしれない — そして最後にリクエストをそのままプロキシし、応答を Agent A にストリームで返す。
+ **Agent A はピアのフレームワークを知る必要が一切ない。** 呼び出し側にとって、`delegate("techlead", ...)` はターゲットがローカルの Subagent か、Managed Agent か、LangChain Agent かによって変わることはない。フレームワークの差異はコントロールプレーンのルーティング層に吸収される。

だからこそ、「どう接続するか」のセクションでは、AgentScope、LangChain、Claude がそれぞれ異なる方法で同じコントロールプレーンに接続できることを強調している: 一度接続すれば、それぞれが他の Agent から発見可能になり、委譲された作業を受け取り、結果を返せるようになる — フレームワークのペアごとに点対点の統合を行う必要はない。

### 本番デプロイアーキテクチャ


![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1785987384213-cab424c2-502c-43b3-ac78-ee0e43eb9c9c.png)

四つのプレーンは次のように理解できる:

| **プレーン** | **所有するもの** | **所有しないもの** |
| --- | --- | --- |
| Gateway | 公開の入口、認証、API ルーティング | ビジネス状態と Agent の実行 |
| コントロールプレーン(`aistiod`) | プロダクトリソース、コンソール、Agent の状態、Session、Team、ランタイムコマンド | Harness の推論と Session ストリームの転送 |
| Dataplane | Managed Harness ランタイム、イベントログ、SSE、Turn Lease、HITL、Work Queue | プロダクト Catalog テーブルの直接読み取り |
| Scheduler | Channel、Cron、アウトバウンドジョブ、Self-hosted Hands Worker | 推論ループ |


もう一つ重要なプロダクト上の分離がある: **Brain** と **Hands** である。

+ **Brain**: コンテキスト、推論、ツールの意思決定、イベントログを管理する。プラットフォームがホストする AgentScope Harness によって提供される。
+ **Hands**: ツールをどこで実行するかを決める。選択肢には `local`、`sandbox`(例: E2B)、`remote`、顧客側のアウトバウンド Worker 経由の `self_hosted` がある。

これにより、企業は三つの質問を別々に答えられるようになる: モデルはどのコンテキストを見ることができるか? ツールはどのネットワークとファイルに触れられるか? どのツール結果を Brain に返してよいか? この信頼境界をこのように因数分解すれば、権限レビューとインシデントの隔離の両方がより明確になる。

これはまた、「managed」が「すべてのデータが顧客環境の外に出なければならない」ことを意味しない理由も説明する。クラウド側での匿名化が許容できるなら、ホストされたサンドボックスを使えばよい。ツールが内部システムや機密なファイルシステムに到達しなければならない場合は、Hands を顧客 VPC に置き、アウトバウンドの Worker がツールを実行して結果を返す。Brain は依然としてオーケストレーションと状態復元を所有している — 入れ替わるのは実行プレーンだけである。

Turn のパス、イベント契約、スキーマの境界についてより深く知りたい読者は、姉妹記事の技術記事 [AgentScope Service 技術詳解](/v2/ja/blogs/agentscope-service-release-tech) を読んでほしい。

## Agent の接続方法

AgentScope Service は二種類のユーザーに同時に応える。

1. プラットフォーム / プラットフォームサービスチーム — ホストされた agent を素早く構築するための SaaS パスを求めており、Console / API を通じて Managed Agents を作成する。
2. ビジネスエンジニアリングチーム — すでに異なるスタックで構築された Agent を持っており、それらを統一的なガバナンスのもとに置きたい。拡張 / SDK / Sidecar を通じてコントロールプレーンに接続する。

この二つのパスは共存する。多くのチームは、新しいプロダクトを出荷するために Managed Agents から始め、その後徐々に既存の自作 Agent(BYO)を Dashboard に持ち込んでいく。

以下では二つ目のパスに焦点を当てる: 自ら開発し、自らデプロイした Agent が、どのように AgentScope Service コントロールプレーンに接続するか。おおむね二つの接続モデルがある — SDK と Sidecar である。Agent Framework のアプリケーションであれば、SDK を導入するだけでコントロールプレーンに登録できる。

### Agent Framework

#### AgentScope

AgentScope Java はネイティブに Agent アプリケーションの接続をサポートする。`agentscope-extensions-aistio` 依存関係を追加するだけで、既存の AgentScope Runtime は自動的にコントロールプレーンに自己登録し、Managed Agents と並んで Dashboard に表示されるようになる。Session の状態、健全性、ランタイムのシグナルは同じ契約を通じて報告される。

Agent Teams のレプリカをまたぐメッセージ配送とサブタスクの委譲、ノードをまたぐ非同期タスクの追跡、Session の並行性制御、Workspace の状態同期、その他の分散デプロイのニーズは、すべてコントロールプレーンがネイティブに提供できる。

#### LangChain

コミュニティは現在 Python SDK を提供している。ユーザーは `aistio.instrument()` ラッパーを通じて接続できる。LangChain / LangGraph アプリケーションでは、コントロールプレーンが Session のスナップショット、コンテキスト、ランタイムメトリクスを帯域外(out of band)で収集する。ビジネスの主経路がまず成功し、報告の失敗が推論そのものに影響を与えることはない。

これにより、LangChain で構築された Agent も、ビジネスパスを書き直すことなく、AgentScope Service のフリート管理と Session 可観測性に参加できる。


Claude Agent SDK や Google ADK のような、より多くのフレームワークのサポートも時間をかけて追加されていく — ロードマップを参照。

### Coding Agent

バイナリとして変更しづらい Coding Agent — Claude Code、Qoder CLI など — には、**Sidecar** がそのギャップを埋めることができる: ローカルの Session ディレクトリとランタイム状態を帯域外で観測し、コントロールプレーンに報告し、compress や terminate といった運用コマンドを受け付ける。

このパスのポイントは、企業が「最も強力な Coding Agent を使う」ことと「統一的なガバナンスのもとに置く」ことのどちらかを選ばなくてよいということである。生産性ツールは開発者の環境で動き続けながら、プラットフォームはそれらを見て、管理し、必要なときに介入できる。


QwenPaw のような個人向けワークスペースアシスタントも、原理的には Sidecar 経由で接続できる — 詳細はロードマップを参照。

## ローカルで試す

AgentScope Service は急速にイテレーションしている。プロダクト全体をまず試したいなら、リポジトリをクローンしてローカル環境で起動してほしい。

1. コントロールプレーン、Managed Agents の dataplane、その他のコンポーネントを起動する(上記の本番デプロイ図の通り):

```shell
git clone https://github.com/agentscope-ai/agentscope-java.git
cd agentscope-java
```

```bash
export DASHSCOPE_API_KEY=sk-xxx
cd agentscope-service
scripts/dev-down.sh && BUILDER_REBUILD=1 scripts/dev-up.sh
```

2. [http://localhost:8080](http://localhost:8080) を開き、ユーザー名 / パスワード(`admin` / `admin`)でサインインする。

そこから Managed Agents を試し、素早く Agent を作成できる:

    1. **Managed Agents** の下で Agent を作成する。
    2. `local` Environment を作成する。
    3. **Sessions** を開き、Agent と Environment をバインドし、最初のメッセージを送る。
    4. **Dashboard** に戻り、オンライン状態、イベント、ランタイム情報を確認する。
    5. コラボレーションを試すには、**Agent Teams** を開き、チームを作成し、タスクとメンバーの状態を観察する。
3. BYO Agent の登録を試すには、リポジトリ内の `agentscope-samples/agents/agentscope-paw` にあるサンプルを使う。起動後、Dashboard に agent が正常に登録されているはずである。

## ロードマップとおわりに

AgentScope Service は、異なる方式(Framework、Coding Agent、Managed Agents など)で構築された Agent を一つのコントロールプレーンにまとめ、Agent 間のコラボレーションに統一されたビューを与える。Console から最初の Agent を作成して Harness ランタイムを AgentScope Service にホストさせるにせよ、既存の AgentScope / LangChain / Claude アプリケーションをコントロールプレーンに接続するにせよ、目標は同じである: **企業にワンストップの Agent コントロール・ガバナンスセンターを提供する** ことである。

今後、AgentScope Service はより開かれた接続、より充実した自動化、より強力なイベント駆動統合に向けて進化し続ける。当面の焦点は次の通りである:

1. **AgentScope Framework ネイティブ機能のイテレーションを続ける**
2. **より多くの Agent フレームワークと Coding Agent をサポートする**
   LangChain、ADK、Claude、Qoder、OpenAI Agents などのアダプタを深化させ、BYO 接続のコストを下げ、異種混在の Agent が同じ契約に参加しやすくする。
3. **自動化**
   Deployment、Cron、Webhook、Channel を中心に自動トリガーとクローズドループの実行を拡張し、Agent を人間主導の Session からイベント駆動のタスク処理へと移行させる。
4. **より多くのイベント駆動統合**
   GitHub / GitLab、DingTalk、WeCom などのエンジニアリング・コラボレーションの入口を接続し、コードの変更、チケット、グループメッセージを直接 Agent の Turn や Team の Task に変換する。


Alibaba Cloud 上のエンタープライズ向けオファリングに関心があれば、[Agent Teams](https://help.aliyun.com/zh/agentteams/magic-console-product-overview) と [Agent Loop](https://help.aliyun.com/zh/document_detail/3033860.html) も参照してほしい。
