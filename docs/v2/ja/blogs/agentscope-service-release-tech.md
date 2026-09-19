---
title: "AgentScope Service 技術詳解: コントロールプレーン、データプレーン、リカバリ可能な Agent ランタイム"
---

リリース告知が「AgentScope Service で何ができるか」を答えるものだとすれば、本稿は「どう作られているか」に焦点を当てる。プロダクトのリソースモデル、プレーンの境界、Turn のライフサイクル、Brain / Hands の分離、Session のイベント契約、そしてマルチフレームワーク統合パスを一通り見て、このプラットフォームの背後にあるシステム設計を説明する。

プロダクト概要と機能サマリーについては、姉妹記事の [AgentScope Service 正式リリース](/v2/ja/blogs/agentscope-service-release) を参照してほしい。本稿は読者がすでに AgentScope 2.0 / Harness の基礎を理解しており、単一の実行可能な Agent を運用可能なプラットフォームへとスケールさせることに関心があることを前提としている。

## AgentScope Service とは何か(実装視点)

実装の観点から見ると、AgentScope Service は単一のプロセスではなく、境界が明確なコンポーネント群である。

| コンポーネント | 役割 |
| --- | --- |
| `service-gateway` | 外部からの入口: 認証、ルーティング、公開 API |
| `aistiod` | Go 製コントロールプレーン: プロダクトリソース、フリート登録、Session / Team ランタイム状態、コンソールのバックエンド |
| `service-dataplane` | Java 製データプレーン: Managed Session の Brain、AgentScope Harness を基に Turn を実行 |
| `service-scheduler` | Channel、Cron、アウトバウンドタスク、Self-hosted Hands Worker |
| PostgreSQL | スキーマで分割された正本の状態: `cp` / `rt` / `dp` |
| Web コンソール | Dashboard・Managed Agents・Agent Teams |

これは同時に二種類のワークロードに対応する。

1. **Managed Agents**: コントロールプレーンがバージョン管理された Snapshot を保持し、データプレーンが Snapshot から `HarnessAgent` を構築してイベント化された Turn を実行する。
2. **BYO Agents**: 既存の AgentScope / LangChain / Claude などのランタイムが拡張、`instrument()`、あるいは Sidecar 経由で接続し、同じフリートと Session の可観測性モデルに参加する。

コントロールプレーンは望ましい状態(desired state)とランタイム状態を管理するが、**モデルの Turn は実行しない**。推論ループはデータプレーン、または接続する側自身のランタイムにとどまる。この境界はアーキテクチャ全体を貫いている。コントロールプレーンが「片手間にモデルを動かす」ようになった瞬間、プレーンの責務、スケーリング、障害ドメインがすべて絡み合ってしまう。

デプロイ形態については、ローカル開発では Kubernetes Reconciler を無効化して Hosted Product パスを取ることができ、本番環境では Aistio の CRD / Workload 機能を有効化して、宣言的な Agent とフリートガバナンスをクラスタに接続できる。プロダクトのブランドは引き続き Agent Service であり、その基盤となるコントロールコンポーネントは `aistiod` である。

## なぜこのようなプラットフォームが必要なのか

Agent のループだけを見れば、今日のフレームワークはすでにデモを書くには十分だ。難しいのは「動く」ことを「運用できる」ことに変えることである。

1. **ローカルスクリプト / CLI**: 状態はローカルディレクトリに存在し、個人には十分だが、マルチレプリカや監査のシナリオには向かない。
2. **ビジネスサービスへの SDK 組み込み**: すべてのアプリケーションが独自に SessionStore、HITL、リース、イベントリプレイ、権限を再実装することになり、コストが重複し標準が分散する。
3. **ローコードオーケストレーション**: Harness のエンジニアリング詳細をビジネス設定者に露出してしまい、統一的なプラットフォームアップグレードを難しくする。
4. **単一の Managed ランタイム**: それ自体はうまく機能するが、フレームワークをまたぐフリート、顧客 VPC 内の Hands、チームコラボレーションの状態などは、しばしば別々のサイロになってしまう。

もう一つの隠れたコストは「状態の所有権が不明確」であることだ。多くの人が、Java/Python プロセス内の agent オブジェクト、フロントエンドの SSE ストリーム、データベース内のチャット履歴を混同している。オブジェクトは破棄されうるし、ストリームは中断されうる。連番の付いた永続化イベントとリカバリ可能な状態ストレージだけが、マルチレプリカ、フェイルオーバー、監査リプレイを支えることができる。AgentScope Service はこれをビジネスごとの選択事項ではなく、デフォルトの契約とする。

したがって、このプラットフォームの解決策は三つの文にまとめられる。

- **ガバナンスの関心事**はコントロールプレーン / データプレーンの契約に集約される。
- **推論カーネル**は AgentScope Harness に集約される。
- **ツール実行の境界**は Environment(Hands)に集約される。

ビジネス側が定義すべきは Agent の差分(prompt、ツール、Skills、権限ポリシー)だけであり、compaction、リカバリ、イベント、リース、承認はプラットフォームの機能として進化していく。プラットフォームが Harness をアップグレードした後は、各ワークフローを個別に描き直すことなく、Managed Agents が一斉にその恩恵を受けられるべきである。

## 全体アーキテクチャ

```text
┌────────────────────────────────────────────────────────────────────────────┐
│                              Agent Service                                 │
│                                                                            │
│  Web Console ──► Gateway :8080 ──┬──► aistiod :8081 （CP / RT）            │
│                                  └──► dataplane :8082 （DP Brain）         │
│                                              │                             │
│                                              ▼                             │
│                                     PostgreSQL                             │
│                              cp · rt · dp schemas                          │
│                                              ▲                             │
│                                              │                             │
│                                     scheduler :8083                        │
│                              Channel / Cron / Hands Worker                 │
└────────────────────────────────────────────────────────────────────────────┘
```

### プレーンの責務

| プレーン | 責任範囲 | 明示的に責任を負わないもの |
| --- | --- | --- |
| Gateway | JWT / 公開ルーティング | ビジネス状態、モデル呼び出し |
| Control(`aistiod`) | ユーザー、Agent バージョン、Environment、Session バインディング、Team、フリートインスタンス、ランタイムコマンド | モデルの Turn |
| Dataplane | Turn Lease、イベント永続化、SSE、HITL、Snapshot からの Harness 構築、Work Queue | Catalog のフォールバックとして `cp` テーブルを直接読むこと |
| Scheduler | Channel、Cron、アウトバウンドの Hands Worker | 推論ループ |

### データの所有権

各プレーンは単一の PostgreSQL サーバーを共有することがあるが、**テーブルは共有しない**。

| スキーマ | 所有者 | データ |
| --- | --- | --- |
| `cp` | `aistiod` プロダクト API | ユーザー、Agent、バージョン、Environment、Session、Vault、Memory、Deployment |
| `rt` | Aistio Runtime Store | フリートインスタンス、ランタイム Session、Context、Team、Task、Message |
| `dp` | Java Dataplane | Session Event、調整状態、HITL、Work Item、データプレーンの射影 |

Dataplane はコントロールプレーンの内部 API を通じて Managed Session を解決し、返却された Agent Snapshot からのみランタイムを構築する。データプレーンのレプリカは水平にスケールできるが、プロダクトの Catalog は依然としてコントロールプレーンを唯一の正とし、二重書き込みやキャッシュのずれを避けている。ローカルの Catalog フォールバックは一見便利に見えるが、長期的には「インスタンス A は更新済みだがインスタンス B は古い定義のまま動いている」という幽霊バグを生み出しがちだ。

### 一つの完全な Turn パス

1. クライアントが既存の Session に `user.message` を追加する。
2. Dataplane が Turn Lease を取得し、Session を `running` に設定する。
3. コントロールプレーンが、ピン留めされた Agent Snapshot、Environment、Workspace、Memory、Vault を解決する。
4. `SessionTurnRunner` が `HarnessAgent.streamEvents` を実行する。
5. `agent.message`、`agent.tool_use`、`span.model_request_*` などの正本イベントが PostgreSQL に書き込まれる。任意の Preview Delta はタイプライター効果のためだけに使われ、永続化はされない。
6. Session は `idle` に戻るか、HITL / Tool Result のために一時停止するか、型付きエラーで終了する。

クライアントは、永続化されたイベント列から以下を通じて回復する。

```http
GET /api/sessions/{id}/events/stream?after={seq}
```

これにより、途中からの再開(incremental resume)が可能になる。プロセス内の Agent オブジェクトと Preview Stream は正本のデータソースではない — これがリカバリ可能な Session の前提である。

Turn Lease のポイントは並行性制御にある。ある瞬間には、その Session の Turn を進めてよい実行者は一つだけであるべきで、重複したイベント連番、課金対象のモデル呼び出しの重複実行、あるいは HITL 待機中に別のレプリカが誤って処理を続けてしまうことを避ける。Lease を失った、あるいは期限切れになった後のテイクオーバーポリシーは、データプレーンのマルチレプリカ可用性における重要な詳細である。

### Brain と Hands

| Environment | 実行モード |
| --- | --- |
| `local` | Dataplane ホスト上でファイルシステムと Shell を実行。開発用途にのみ推奨 |
| `sandbox` | E2B のようなマネージドクラウドサンドボックス |
| `remote` | リモート / 分散ファイルシステム、ローカル Shell なし |
| `self_hosted` | Brain は Tool Schema のみを公開する。顧客側のアウトバウンド Worker が poll / ack / heartbeat / 結果返却を行う |

`self_hosted` はプライベートネットワークに適している。Brain は顧客のイントラネットへのインバウンドアクセスを必要とせず、代わりに Worker がツール呼び出しのために能動的にアウトバウンドで poll する。プロトコルのセマンティクスは安定した `tool_use` / `tool_result` イベントの閉ループであり、Hands をどこか別の場所に移しても、Brain の推論ループを書き換える必要はない。

したがってセキュリティ・コンプライアンスチームは、モデルのコンテキスト可視性の範囲、ツールが到達できるネットワークとファイルシステム、Brain に返される結果のデータ最小化ポリシー、という三つの質問を別々に承認できる。「Agent コンテナ全体」を単一のブラックボックスな権限オブジェクトとして扱うよりも、これははるかに実現しやすい。

## コア機能(実装レイヤー)

> UI のスクリーンショットについてはプロダクト記事を参照してほしい。ここでは仕組みに焦点を当てる。

### Dashboard: フリートとランタイムの可観測性

Dashboard のデータは主にコントロールプレーンの Runtime Store とデータプレーンのイベント射影から来ており、フロントエンドでの場当たり的な集計ではない。

- Agent / インスタンスの健全性とオンライン在庫
- Session のフェーズ(例: `active` / `idle` / `compressing` / `archived` / `terminated`)と Turn の所要時間
- コンテキスト圧、Token の増分、エラー件数
- Team メンバーの状態、タスクの進捗、ライフサイクルイベント

混同しやすい二つの概念は分けて考える必要がある。

- **Session**: リカバリ可能な会話スレッド。`phase` はそのスレッドの運用状態を表す。
- **Turn**: ユーザーのリクエストから応答までの一つの実行単位。所要時間の統計は Turn に属するものであり、誤って「稼働時間」として扱われる Session の壁時計上の生存期間には属さない。

BYO Agents については、レベル1の Session Snapshot がアダプタから定期的に報告される。コントロールプレーンはそれをフリートの統計や操作(compaction、終了など)に使う。フィールドのセマンティクスはフレームワークをまたいで一貫していなければならない。さもないと Dashboard は各アダプタがそれぞれ勝手に振る舞う「メトリクスの壁」に退化してしまう。Token のメトリクスも、絶対値の Snapshot を単純に積算するのではなく、増分の集計を優先すべきである。

### Managed Agents: バージョン管理された定義 + イベント化された Session

プロダクトのリソースモデルはおおよそ次のとおりである。

| リソース | 目的 |
| --- | --- |
| Agent | バージョン管理された system prompt、モデル、ツール、MCP、Skill、コラボレーション設定 |
| Environment | ツール実行の境界と Sandbox / Worker の設定 |
| Session | Agent バージョン、Environment、Memory、Vault、イベントストリームのステートフルなバインディング |
| Memory / Vault | Session をまたぐドキュメントと暗号化された認証情報 |
| Deployment / Channel | Cron、Webhook、手動トリガー、メッセージングチャネル |
| Team | Lead / Member、Message、Task、Plan、ライフサイクル |

いくつかの重要な設計上の選択がある。

1. **Session の作成は静的なバインディング**
   作成時にはリソース関係のみが記録され、最初の `user.message` が来るまで Agent は実行されない。

2. **イベントネイティブ**
   インバウンドイベントが作業を駆動し、アウトバウンドイベントが進捗と結果を記述する。永続化されたすべてのイベントは Session 内で単調増加する連番を持ち、クライアントは途中から再開できる。

3. **HITL をファーストクラス市民として扱う**
   Ask Policy ツールは Turn を一時停止して確認要求を発行する。`user.tool_confirmation` は続行または拒否を行いつつ、完全な履歴を保持する。

4. **正本イベント vs Preview**
   SSE は即時性のある体験のために `event_start` / `event_delta` をプッシュできるが、永続化されたイベントが最終的なものである。UI のリフレッシュ、複数デバイスでの復元、監査リプレイはすべて同じ正本のソースに依存する。

5. **バージョンのピン留め**
   Session は Agent の特定のバージョン化された Snapshot にバインドされ、実行中のホットアップデートによる再現不能な軌跡を避ける。アップグレードが必要な場合は、明示的に新しい Session を作成するか、プロダクトが定義するアップグレードパスを取る。

Managed Agents は、コントロールプレーンの Snapshot から Java Dataplane 内で構築される。基盤の実装は AgentScope 2.0 の `HarnessAgent` を直接再利用しており、コンテキストの compaction、ツール結果の削除、状態の復元、Skills / サブタスクなどのエンジニアリング上のデフォルトを、プロダクト層で別の agent ループとして再実装する必要はない。プロダクト層が追加するのは、テナントリソース、ACL、イベント契約、Turn Lease、HITL チケット、Environment、Worker キューである — これらこそが「フレームワーク」を「プラットフォーム」に変えるコストである。

### Agent Teams: Session をまたぐコラボレーションのステートマシン

Agent Teams は、マルチエージェントのコラボレーションを、どこかのプロセスのメモリ上の一時的なチャットではなく、コントロールプレーンのリソースへと変える。

- Lead / Member のトポロジーと動的なメンバーシップ(人数 / ホワイトリストの制約)
- ユニキャストとブロードキャストのメッセージ
- 共有タスク、Claim / Assign、Plan の承認
- Member の Wakeup、グレースフルシャットダウン、ライフサイクルの期限、障害からの復旧
- プロセスと Session をまたいで保持されるメッセージとタスク

Managed メンバーの場合、Wakeup はさらにデータプレーンの Session / Turn に着地しうる。BYO メンバーの場合は、コントロールプレーンのコマンドとアダプタの機能を通じて調整される。Team の状態は主に `rt` スキーマに存在し、Session ごとの `dp` イベントログとの混同を避けている — Session は一つの会話の軌跡に責任を持ち、Team はメンバーをまたぐコラボレーションの永続的な単位である。

実用的な制約として、Team はすべてのメンバーが同じソースから来ることを前提としない。Lead は Managed Harness Agent であってよく、Member は接続された Coding Agent や LangChain サービスであってよい。コントロールプレーンがトポロジー、タスク、ライフサイクルを扱い、メンバー側はコラボレーションと可観測性の契約さえ満たせばよい。異種混在のチーミングは、「まずフレームワークを統一してから、コラボレーションを語る」よりも、企業の実情に近い。

## 接続方法

### AgentScope(ネイティブ)

Java 側は `agentscope-extensions-aistio` を通じて接続する。この拡張は Runtime をコントロールプレーンに登録し、Session / Context / 健全性情報を報告し、運用コマンドを処理する。既存の AgentScope アプリケーションにとって、これは最も侵襲性が低く、契約として最も完結したパスである。Managed Agents と同じ Dashboard と Session 可観測性モデルを共有する。

両者が同じ AgentScope のイベントと状態のセマンティクスを共有しているため、レベル1 / Context / compaction の機能は通常最初にそろう。すでに `HarnessAgent` を使用しているなら、統合の限界コストは主に依存関係、登録設定、ランタイムアイデンティティであり、ビジネス prompt の書き直しではない。

### LangChain

Python SDK は `aistio.instrument()` を提供する。LangChain / LangGraph 向けに、アダプタは Callback / Checkpointer のインターセプションポイントにフックする。

```python
import aistio

aistio.instrument(
    app_or_client,
    control_plane="aistiod.aistio-system:9090",
    agent_name="my-langchain-agent",
    namespace="default",
    enable_events=False,  # レベル2イベントはデフォルトで無効。必要に応じて有効化する
)
```

設計原則は帯域外(out-of-band)の報告である。主経路がまず成功し、報告は失敗してもサイレントに degrade し、コントロールプレーンの揺らぎがビジネスの推論を巻き込んで倒れることを避ける。レベル1はフリートビューをサポートするためデフォルトで有効になっており、より粒度の細かいイベント(レベル2)はコストとコンプライアンスの要件に応じて有効化できる。Context の報告はハッシュ変化によるデバウンスを使い、無効な全量プッシュを減らす。

### Claude SDK と Sidecar

Claude Agent SDK も `instrument()` を使って、SessionStore などのパスをデコレートすることでレベル1の Snapshot と compaction / 終了機能を取得する。

Claude Code や Qoder のような、SDK の埋め込みが不便な Coding Agent には、**Sidecar** が使われる。

- メインのコンテナは元の CLI / Agent を引き続き実行する。
- Sidecar はローカルの Session ディレクトリ(例: `~/.claude/`)とランタイム状態を観測する。
- フリートと Session の情報をコントロールプレーンに報告し、サポートされている運用コマンドを転送する。
- 必要に応じて、Session ファイルの状態を外部ストレージに同期し、ノードをまたぐ復元をサポートする。

Sidecar は「agent ループを再実装している」のではなく、バイナリを変更できない場合にコントロールプレーンが必要とする最小限の可観測性と操作性のサーフェスである。これはまた、多くの Coding Agent にとって、正本のソースはデータベースではなくファイルシステムにあることを思い出させてくれる — コントロールプレーンは、すべての Runtime をまず PostgreSQL に移行させるのではなく、この状態の形を理解しなければならない。

### ローカル起動と検証

```bash
export DASHSCOPE_API_KEY=sk-xxx
cd agentscope-service
BUILDER_REBUILD=1 scripts/dev-up.sh
# Console: http://localhost:8080
scripts/smoke.sh
```

少なくとも三つのパスを検証することを推奨する。

1. Managed Session: `user.message` を送信し、イベントストリームから復元し、ページのリフレッシュ後に連番が再開することを確認する。
2. HITL: Ask Policy をトリガーし、確認後に続行し、履歴が完全であることを検証する。
3. `self_hosted`: Worker の poll / ack / heartbeat / `tool_result` の返却を行い、Turn が正しく回復することを確認する。

[`docs/guide/14-validation.md`](/v2/en/service/first-session) と、アーキテクチャノートである [`docs/guide/02-architecture.md`](/v2/en/service/index) を参照してほしい。

## 早めに避けておくべき実装上の落とし穴

1. **Preview SSE を正本のログとして扱ってしまう**
   タイプライター効果は再接続時に破棄され、再構築されうる。監査、レビュー、課金は永続化されたイベント連番に整合させるべきである。

2. **データプレーンにプロダクトの Catalog をローカルにキャッシュさせ、コントロールプレーンに到達できないときにサイレントにフォールバックさせる**
   短期的には高可用に見えるかもしれないが、長期的には最悪の障害、すなわち「未知のバージョンを実行していた」を生み出す。サイレントに古い定義を使うより、目に見える形で失敗する方がよい。

3. **Session のフェーズと Turn の所要時間を混同する**
   `active` / `idle` はスレッドの状態を表し、所要時間は Turn に属する。そうしないと、Dashboard の「誰が一番忙しいか」が、長時間ぶら下がったままの Session によって誤解を招く。

4. **BYO アダプタごとに独自のメトリクスを発明してしまう**
   フリートの KPI は意味論を共有しなければならない。新しいフレームワークをアダプトする際は、まず契約を合わせ、それから専用フィールドを検討する。

5. **Team のメッセージをメンバーの Session イベントストリームに詰め込んでコラボレーション状態を偽装する**
   Session の軌跡と Team のステートマシンのライフサイクルは異なる。これらを混ぜると、復元、クリーンアップ、権限境界がすべて壊れてしまう。

これらのポイントは単一プロセスのデモでは目立たないが、マルチレプリカ、マルチフレームワーク、マルチチームのシナリオが同時に現れた途端、本番インシデントの温床となる。

## ロードマップ(エンジニアリング視点)

1. **アダプタのカバレッジ**
   LangChain、ADK、Claude、Qoder、OpenAI Agents などについてレベル1 / レベル2 / Context の整合を深め、フレームワーク固有のフィールドを減らし、Dashboard の KPI がどこでも同じ意味を持つようにする。

2. **本番レベルのマルチテナンシーとガバナンス**
   ACL、クォータ、監査、カナリアリリース、鍵のローテーション、より厳格な Environment 分離ポリシー。Vault / Memory のライフサイクルとアクセス境界も引き続き整備していく。

3. **自動化**
   Deployment / Cron / Webhook / Channel を「トリガーできる」から「オーケストレーション可能、リプレイ可能、代償可能(compensable)」へと進化させる。自動化された Turn が失敗した場合、型付きエラー、リトライポリシー、人間によるテイクオーバーの入口が必要である。

4. **イベント駆動の入口**
   GitHub / GitLab、DingTalk、WeCom など: 外部イベントを Session の Turn や Team の Task に安定してマッピングしつつ、冪等性と認証境界を維持する。外部システムからのリトライは通常のことであり、プラットフォームは重複作業を防がなければならない。

5. **Teams とリカバリ**
   動的なメンバーシップ、プラン承認、メンバー切断時の復旧、Session をまたぐ再起動、Managed / BYO が混在する Team の整合性。障害ドメインが複数の Runtime にまたがるため、コラボレーションのステートマシンは単一の Session よりも難しい。

## 「Harness を組み込んだだけ」との違い

ビジネスサービスが `HarnessAgent` を直接組み込んでいるなら、すでに堅実な長時間タスクと compaction の機能を持っているだろう。しかしマルチテナント、マルチレプリカ、マルチチームのシナリオに直面したとき、依然として次のものを追加する必要がある。

- バージョン管理された Agent 定義と Session のピン留め
- 追記専用のイベントとカーソルベースの再開
- Turn Lease と HITL チケット
- Environment の切り替えと Self-hosted な Work Queue
- フリート登録、コンテキスト圧、compaction / 終了コマンド
- Team のタスクボードと Session をまたぐコラボレーション状態

AgentScope Service は「Harness の周りに UI 層を足しただけ」ではなく、これらの分散した責務を安定したプロダクトリソースと内部契約へと変えるものである。Harness はプラットフォームが agent ループを書き直さずに済むようにしてくれるが、プラットフォーム層は依然として状態の所有権、障害ドメイン、ガバナンスの境界に責任を負う。

## おわりに

AgentScope Service の技術的なカーネルは、三つの文にまとめられる。

1. **コントロールプレーンは desired state とランタイム状態を管理し、データプレーンが Turn を実行し、Hands がツールをどこに着地させるかを決める**。
2. **永続化されたイベント列が Session の正本であり、プロセス内のオブジェクトは使い捨てのキャッシュにすぎない**。
3. **Managed と BYO はフリートの契約を共有し、フレームワークの差異は Console 全体に散らばるのではなく、アダプタに集約される**。

「単一の Harness Agent」から「運用可能な agent フリート」への移行を考えているなら、この階層化によって多くの重複したインフラを排除できる。ぜひ [`agentscope-service/README.md`](/v2/en/service/quickstart) を直接読んでみてほしい。プロダクト機能とオンボーディングのストーリーについては [リリース記事](/v2/ja/blogs/agentscope-service-release) に戻ってほしい。
