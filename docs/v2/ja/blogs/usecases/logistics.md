---
hide-toc: true
---

# 設定駆動からビジネスネイティブへ: AgentScope によるエンタープライズグレードのエージェント開発実践

## 01 背景

### 1.1 出発点

#### 1.1.1 ビジネス面での考察

> 注: 著者は海外物流の業務財務チーム出身であり、そのため支払いプロセスは私たちにとって中核となる能力です。

最近の要件計画に関する議論が、既存プロダクトについての深い内省のきっかけとなりました。プロダクト担当の同僚が、以前にローンチした「支払い承認エージェント」についていくつかの重要な質問を投げかけ、現行システムの限界を鋭く突いてきました。

**第一に、エージェントは本当の意味での「記憶」と「知恵」を持っているのか?**

現在の承認シナリオには、大量の繰り返し発生する却下理由が存在します。例えば、サプライヤーに未解決のマイナス請求があるという理由で支払い A を却下したあと、数日後に支払い A+ が提出された際、エージェントは以前の却下理由を覚えていて、その問題が解決済みかどうかを自動的にチェックできるでしょうか。さらに、エージェントは承認の軌跡を分析し、ユーザー A の「一発でクリアなフィードバック」のような効率的なパターンを発見し、複数ラウンドを要してようやく承認を通しているユーザー B のようなユーザーに、そうしたベストプラクティスを推奨し、全体の効率を向上させることができるでしょうか。

**第二に、やり取りをもっとパーソナライズできないか?**

エージェントは自動的に人の操作習慣を記憶し、会話の中で、そのユーザーが普段気にかけている事柄に基づいてカスタマイズされたヒントを提供できるでしょうか。

**第三に、運用担当者はセルフサービスで迅速なカスタマイズを実現できるか?**

私たちは将来、技術者ではない運用担当の同僚も参加して、誰もが自分専用のエージェントを迅速にカスタマイズできるようになることを望んでいます。例えば「請求コストのトレース分析」シナリオでは、「なぜこの費用はこのように計算されるのか」をビジネス担当者が理解できるよう手助けします。理想的には、迅速な設定、迅速なテスト、迅速な本番投入という能力を備えているべきです。

**第四に、金融グレードの業務安全性をどう保証するか?**

金融の安全性は最優先事項です——書き込み操作には厳格な承認と確認が必要です。現状の、Prompt によるヒントに頼ってモデルに二次確認を行わせるやり方では、100% のインターセプトを実現できません。自己計画・自己実行のワークフローの中で、どの書き込み操作が必ずユーザー確認を経なければならないかを厳密に制御する、より正確なメカニズムが必要です。

**最後に、正確なカナリアリリースと実験がサポートされているか?**

システムは特定のユーザーグループを対象としたカナリアリリース、さらには A/B テストをサポートし、異なる戦略の有効性を検証できる必要があります。

これらの問いは私たちに、以前構築したものが単純な Chatbox に過ぎず、真にインテリジェントなエージェントではなかったことをはっと気づかせました。この気づきがチームを深い内省へと駆り立てました。その後 1 か月にわたり、私たちは他チームの事例を研究し、複数ラウンドの分析と議論を重ね、最終的に以下の技術的な考察の方向性を導き出しました。

#### 1.1.2 技術面での考察

##### 1.1.2.1 所感

まず問いかけてみましょう——あなたのハイコードは実際どのような姿をしているでしょうか?

私たちは Google ADK や AgentScope といったフレームワークの上に構築された複数の Agent アプリケーション(Java)を観察しました。そこには驚くほど似通った構造が見られました。

```text
my-agent-app/
├── FinanceAgent.java ← new ReActAgent(), hardcoded prompt
├── HRAgent.java ← new ReActAgent(), hardcoded another prompt
├── SalesAgent.java ← new ReActAgent(), yet another prompt
├── FinanceTool.java ← @Tool annotation, registered directly
├── HRTool.java ← @Tool annotation, registered directly
├── SessionManager.java ← self-built session management
├── ContextCompressor.java ← self-built context compression
├── SSEController.java ← self-built SSE streaming output
└── ...
```

どのチームも同じことをしています。

1. **手書きの Agent クラス**: 各アプリケーションには 10 個以上、時には数十個もの手書きの Agent があり、各 Agent はコンストラクタ内でハードコードされたプロンプト、指定されたモデル、登録済みのツールを持つ Java クラスに対応しています。
2. **自前で構築したインフラ**: セッションの永続化、コンテキスト圧縮、SSE プロトコルの適応、HITL フレームワーク——どのチームも独自に構築しています。
3. **変更にはリリースが必要**: プロンプトの変更、SKILL の追加、MCP ツールの追加はすべてコード変更、コンパイル、デプロイを必要とし、単純なプロンプトのチューニングが丸ごとの開発サイクルになってしまいます。

これはエンジニアリングの問題であり、AI の問題ではありません。

##### 1.1.2.2 エージェントエンジニアリングにおける典型的な「悪臭」

Agent システムの実際の実装では、統一されたエンジニアリング標準やプラットフォームサポートの欠如により、コードベースは重複した、硬直的で保守しづらい実装パターンで埋め尽くされがちです。以下では、開発コストを増大させるだけでなく、深刻な安定性・セキュリティリスクをもたらす、7 つの典型的な「悪臭」をまとめます。

**悪臭 1: ボイラープレートの氾濫——「作る」のではなく「クローンする」**

現象: 各 Agent は独立した Java クラスとして実装され、すべてのクラスが同一のダブルチェックロッキング(DCL)によるシングルトンパターン、`@PostConstruct` の初期化ロジック、ハードコードされた依存性注入を含んでいます。

```java
// BAD: 各 Agent クラスが 200 行以上のボイラープレートをコピーしている
@Component
public class FinanceMasterAgent {
    private static volatile LlmAgent instance;
    // ... 標準的な DCL の getInstance() 実装 ...
    @PostConstruct
    public void initializeOnStartup() {
        AgentRegistry.register(getInstance());
    }
}
```

分析: 20 個以上の Agent を持つプロジェクトでは、`private static volatile instance` が 30 回以上出現し、`getInstance()` のロジックが 20 回以上コピーされます。新しい Agent を追加することは、本質的に「コピー&ペースト → クラス名/Prompt/ツールリストを修正 → `@DependsOn` を修正」という作業になります。この「クローン式開発」は極めて高いコードの冗長性をもたらし、基盤フレームワークをアップグレードするたびにすべての Agent クラスを修正する必要が生じ、保守コストが線形に増大します。

**悪臭 2: コア設定のハードコード——Prompt のイテレーションが阻害される**

現象: Agent の 3 つの中核要素——Prompt(指示)、モデルパラメータ(Temperature など)、API Key——がすべて Java の定数や文字列リテラルとしてコードにハードコードされています。

```java
// BAD: 500 行以上のビジネス判断ツリーが Java メソッドにハードコードされている
private static String buildInstruction() {
    return """
        あなたはプロフェッショナルな財務診断アシスタントです...
        ## Act 0: ユーザー入力を特定する...
        ## Act 1: 根本原因を診断する...(30 以上のエラーコード分岐)
        """;
}
// BAD: 機微情報とモデル設定がハードコードされている
public class ModelFactory {
    public static final String API_KEY = "sk-xxxxxxxx"; // Git にまでコミットされている
}
```

分析:

- 開発のボトルネック: プロンプトのチューニングは「コード変更 → コードレビュー → コンパイル → デプロイ」という完全なサイクルを経なければならず、これは週単位の時間がかかります。一方でビジネス側のプロンプトイテレーション要件は日単位で発生します。
- セキュリティリスク: API Key などの機微な情報がコードリポジトリに直接露出しています。
- 柔軟性の欠如: 異なる Agent の Temperature パラメータがあちこちに散在しており、集中管理も動的な調整もできません。

**悪臭 3: 硬直的な能力のバインディング——ツールと Skill を動的にオーケストレーションできない**

現象: Agent が持つツールセット(Tools)とスキル(Skills)はビルド時に固定され、ハードコードされたリストや Classpath のパスを通じてロードされます。

```java
// BAD: ツールリストがコードに溶接されている
.tools(List.of(
    AgentTool.create(OrderAgent.getInstance()),
    AgentTool.create(RefundAgent.getInstance())
))
// BAD: Skill のパスがハードコードされている
Skill diagnosisPlan = factory.createSkillFromResource("skills/diagnosis/diagnosis_plan");
```

分析:

- 運用の困難さ: MCP サービスが障害を起こし一時的にオフラインにする必要がある場合や、新しいツールをカナリアリリースする必要がある場合、コードを修正して新バージョンをリリースしなければならず、ランタイムでのホットスワップは不可能です。
- 高い結合度: 数百に及ぶ散在した能力のバインディングにより、依存関係のグローバルなビューを構築するのが難しくなり、Agent の能力の再利用と組み合わせが妨げられます。

**悪臭 4: MCP 管理の欠如——コネクションリークと環境の混乱**

現象: MCP Server のアドレスがコードにハードコードされ、ツールを呼び出すたびに新たに接続を確立し、コネクションプール管理を欠いています。

```java
// BAD: プレリリース環境のアドレスがハードコードされており、本番障害を起こす可能性が非常に高い
private static final String ORDER_MCP_URL = "https://...pre-region...";
// BAD: 呼び出しのたびに TCP ハンドシェイク + 初期化 + シャットダウンを実行している
public static String invokeMcpTool(...) {
    McpSyncClient client = McpClient.sync(transport).build();
    client.initialize();
    // ... 実行 ...
    client.closeGracefully();
}
```

分析:

- 高リスクな設定: リリース時にクリーンアップされずに残ったハードコードの環境アドレス(例えば `pre-`)は、本番環境がプレリリースのサービスを呼び出す原因となり、P1 レベルのインシデントを引き起こします。
- パフォーマンスの低下: ReAct ループ内で頻繁に発生する短時間接続は 100〜500ms の追加レイテンシを招き、サーキットブレーキングもないため、高並行時には容易に MCP Server を巻き込んで遅延させてしまいます。

**悪臭 5: アイデンティティ伝播の断絶——「神の視点」というセキュリティリスク**

現象: ユーザーのアイデンティティは Session レイヤーでのみマークされ、下流のツールや MCP 呼び出しには伝播されません。すべての操作はアプリケーションのサービスアカウントのアイデンティティで実行されます。

```java
// BAD: ツール呼び出し時に operator が空、またはアプリケーションレベルの認証が使われている
request.put("creatorNo", "");
ClientMcpRequestAuth auth = ClientMcpRequestAuth.of(serverUrl).generateAuthMaterial(); // アプリケーションアイデンティティ
```

分析:

- 監査証跡の欠落: どのユーザーが Agent を通じて機微な操作(ルールの作成、承認など)を行ったかを追跡することが不可能です。
- 権限の制御不能: ユーザー単位のデータ隔離と権限制御の欠如はゼロトラストのセキュリティ原則に反します。金融をはじめとする機微なシナリオでは、これは容認できないレッドラインです。

**悪臭 6: HITL(ヒューマンインザループ)のロジックが散在し脆弱**

現象: 人による確認ロジックは各ツールがそれぞれ独自の方法で実装しています。あるものは LLM が認識してくれることを期待してマジックストリングを返し、あるものは Redis をポーリングしてスレッドをブロックし、あるものは例外をスローしてフローを中断します。

```java
// BAD 6a: LLM がマジックストリングを理解することに依存しており、無限ループに陥りやすい
return "NEED_CONFIRM: amount too large, please confirm";
// BAD 6b: 確認を待って Agent のスレッドをブロックしている
Thread.sleep(1000); // 貴重なスレッドリソースを占有している
```

分析:

- 信頼性の欠如: LLM が停止シグナルを正しくパースできず、無限ループや誤実行を招く可能性があります。
- リソースの浪費: ブロッキング待機はサーバーのスレッドリソースを著しく消費します。
- 断片化した体験: 確認のやり取りがツールごとに一貫しておらず、フロントエンドの適応が困難になります。
- 不完全なカバレッジ: 外部の MCP ツールはローカルの HITL ロジックを組み込むことができないため、高リスクな操作が必要な人による監督を欠いたままになります。

**悪臭 7: 設定のバージョン管理の欠如——ロールバックはまるで「考古学」**

現象: 設定がデータベースや設定センターに外部化されている場合でも、完全なバージョンスナップショット、監査ログ、カナリアの仕組みが依然として欠けています。

分析:

- 困難なロールバック: Prompt、モデルパラメータ、ツールセットはしばしば一緒に変更されます。ハルシネーションが発生したり効果が低下したりしたとき、「先週の安定した状態」にワンクリックでロールバックする手段がありません。関連する設定が異なる Commit やレコードに散らばっているため、復旧のプロセスはまるで「考古学」のようになります。
- 追跡不能: 「誰が、いつ、何を変更したか」という監査チェーンが存在せず、A/B テストやカナリアリリースも行えないため、最適化の効果を定量化できず、リスクも制御できません。

**まとめ**: 上記の悪臭は、Agent エンジニアリングが「プロトタイプのデモ」から「本番システム」へと進化する過程で典型的に生じる成長痛を反映しています。解決策は、シングルトン管理、設定の外部化、動的オーケストレーション、コネクションプール、アイデンティティの伝播、HITL によるインターセプト、バージョン管理をプラットフォームの基盤機能として下層に押し下げる統一された Agent Runtime プラットフォームを構築することにあります。それにより、開発者は車輪の再発明ではなく、ビジネスロジックと Prompt の最適化に集中できるようになります。

#### 1.1.3 結論のまとめ

以上のビジネス面・技術面の考察に基づき、私たちは以下のように結論づけました。

1. 長期記憶は構造化され、永続化され、カスタマイズ可能でなければなりません。
2. Agent の自己進化はビジネス次元で定義される必要があり、自動クローズドループを実現するには具体的で柔軟な高度なカスタマイズが求められます。
3. 運用担当者が本当にいつでも Agent をカスタマイズできるようにするには、ゼロコードで設定ベースのアクセスが必要です。新しい Agent/SKILL/MCP の追加にコードは不要で、ページ上の設定だけで即座に本番投入でき、利用のハードルを下げます。
4. HITL の能力は、エンジニアリンググレードの正確なマッチングを実現するために、フレームワークレベルの変革が必要です——エンジニアリンググレードの HITL を構築する必要があります。
5. カナリア戦略のカスタマイズも、当然ながらエンジニアリンググレードの開発です。

当初は社内の AI プラットフォームの中に最適な解決策を見出せることを期待していましたが、上記すべてを達成するには全リンクのクローズドループを AI 技術プラットフォームに頼ることが難しいことが次第にわかってきました。その理由は以下の通りです。

1. 例えば長期記憶のビジネスカスタマイズについて——プラットフォームが通常提供するのは RAG モードであり、正確に制御可能にするのが困難です。
2. 運用の使いやすさはさらに難しく、結局のところ Agent の設定プラットフォームは技術的な詳細に満ちており、ビジネスプラットフォームとの正確な結合には大規模な技術的変革が必要です。
3. エンジニアリンググレードの HITL カスタマイズも、やはり技術プラットフォームに頼ることはできません。

こうした考察を経て、私たちの中である抑えがたい発想が育ち続けていました——ハイコード開発をベースに、業務財務プラットフォームと完全かつ深く統合された Agent プラットフォームを構築したい。迅速な設定、迅速なリリース、カナリア、そして自己進化を備えたものを!

### 1.2 プラットフォーム構築の要点まとめ

**1. 完全に設定駆動の Agent 実行エンジン**

- **ゼロコードで設定ベースのアクセス**: 新しい Agent/SKILL/MCP の追加にコードは不要で、ページ上の設定だけで即座に本番投入でき、利用のハードルを下げます。
- **軽量な隔離ランタイム**: 「使うときに構築する(build on use)」メカニズムを採用し、ランタイムで軽量な Agent インスタンスのシェルを動的に構築します。このメカニズムは下層の Skill、SKILL、MCP インスタンスを再利用し、① 効率的なリソース共有と ② 優れたパフォーマンスを保証しつつ、Agent のセッション実行環境の完全な隔離を実現し、安定性とセキュリティを保証します。

**2. さまざまなエコシステムプラットフォームとの低コストな互換性**

- 汎用的な Skill マーケットプレイス拡張機能: 他の Skill マーケットプレイスを迅速にサポート可能
    - 本番環境: Aone Skill マーケットプレイスと統合し、高可用性・高水準の本番グレードのニーズを満たします。
    - 開発・テスト: OSS Skills を構築し、柔軟なデバッグと検証環境を提供してイテレーションサイクルを加速します。

**3. Agent の効果を多次元的に進化させる**

- インナーループ: 会話とユーザー操作の軌跡を永続化・構造化し、スケジュールに基づいて自動的に嗜好とケースの統合を行います。
    - 収集戦略の有効化とカスタマイズを設定可能
    - 特徴: 自己クローズドループ
- アウターループ: ユーザーフィードバックのクローズドループ最適化。セッション評価、ツール呼び出し成功率、タスク完了率などのメトリクスを自動的に収集し、人手によるアノテーションデータと組み合わせて Prompt と Skill 戦略のイテレーションを駆動し、Agent をますます正確にします。
    - 自動クローズドループではなく、人の介入が必要
- A/B 実験とカナリアリリース: マルチバージョンの Agent の並列実行とトラフィック分割をサポートし、実際のビジネスデータで改善効果を検証し、アップグレードのたびに定量化可能な体験改善をもたらすことを保証します。
    - 自動クローズドループではなく、人の介入が必要

**4. 業務財務プラットフォームの管理コンソールと統合された、完全に垂直なビジネス Agent 設定。完全自由なページカスタマイズを実現**

- **ビジネスセマンティクスのネイティブなマッピング**: Agent の設定項目は、抽象的な技術パラメータではなく、業務財務ドメインのビジネスオブジェクト(請求書のタイプ、決済主体、費用カテゴリなど)に直接対応します。ビジネス担当者は、下層のモデル構造を理解しなくても、Agent の挙動の境界を正確に定義できます。
- **統合された管理コンソールガバナンス**: Agent の作成、公開、権限割り当て、バージョン管理はすべて業務財務の管理バックエンドに統合されており、既存の組織構造、ロール体系、承認フローとシームレスに接続され、Agent のガバナンスがエンタープライズの管理体系と整合することを保証します。
- **ゼロコードのページカスタマイズ**: ビジュアルエディタを通じて、会話インターフェース、フォームフィールド、結果表示カード、操作ボタンを自由に配置できます。異なるビジネスラインは、フロントエンド開発なしに独自の専用インタラクション体験を独立して作り込み、パーソナライズされたニーズに迅速に対応できます。

**5. 全リンクのアイデンティティマーキングときめ細かな権限制御メカニズムの確立**

元のシステムでは、ACL、データマーキング、ユーザーホワイトリストなどの手段を用いた Web レイヤーベースの権限管理を実装し、階層的な管理を行っていました。しかし MCP が HSF インターフェースレイヤーまで降りてきて、Agent が呼び出しチェーンを主導するようになると、従来の Web レイヤーの権限モデルではもはや新しい呼び出しパスをカバーできなくなります。

## 02 技術選定

Agent ランタイムフレームワークの選定において、私たちは LangChain、Google ADK、AgentScope という 3 つの主流な技術体系を体系的に評価しました。最終的に AgentScope をコア基盤として選択したのは、エンタープライズグレードの本番要件、Alibaba 社内エコシステムとの適合性、業務財務ニーズの特殊性という 3 つの次元に基づく総合的な判断でした。

### 2.1 3 つのフレームワークのコア機能比較

![3 つのフレームワークのコア機能比較](https://mmbiz.qpic.cn/mmbiz_jpg/bvDbzNRia8j3ywu8nhfPjptJvUHNNSeO8Ao4BIz4NSnHPFnLCtQyD9AckiaKTwsUocPGzpZ3OOnnknORmdHITRqGU77JOYb7oK8oYcoXibLYSw/640?wx_fmt=jpeg&from=appmsg)

### 2.2 最終的な選択

![最終的な選択: AgentScope](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j3y2iaYibfXsUyoZiayltDUG2aZjqhkAfSXqKQkhA9pwMtY0uUOAiamgaCBK54f8nSa16djxaic8FC80XOIwhUiaQPtFA6eqJCKCzWiaA/640?wx_fmt=png&from=appmsg)

## 03 AgentScope の 3 層アーキテクチャ能力分析

私は、AgentScope には 3 つの能力層があると考えています。モデル層、ReAct 推論ループ層、外部抽象化層です。

### 3.1 モデル層

アーキテクチャの最下層として、その中核は LLM とのやり取りであり、以下の 5 つのポイントを中心に構成されています。

**1. 統一された抽象化とプロトコルの分離**

- ルール: すべてのモデル実装は `ChatModelBase` を継承しなければならず、Formatter メカニズムを用いてプラットフォームに依存しない Msg をベンダー固有のリクエスト/レスポンス形式に変換します。
- ソース分析: `OpenAIChatModel` は内部に `Formatter<OpenAIMessage, OpenAIResponse, OpenAIRequest>` を保持しています。新しいモデルの追加は対応する Formatter の実装だけで済み、コアの呼び出しロジックを変更する必要はなく、OpenAI 互換プロトコルやさまざまな国内モデルを自然にサポートします。

**2. きめ細かいパラメータ制御と多段階の設定マージ**

- ルール: 生成パラメータは `GenerateOptions` によって標準化され、「ランタイムパラメータ > ビルド時のデフォルトパラメータ」という優先順位でのマージをサポートし、ベンダー固有の拡張フィールドの受け渡しも可能にします。
- ソース分析: `GenerateOptions.mergeOptions(primary, fallback)` が設定のマージを実装しています。additionalHeaders/BodyParams/QueryParams という 3 つの拡張 Map により、フレームワークが API の進化に取り残されないことを保証しています。

**3. 組み込みのエンタープライズグレードな呼び出しガバナンス**

- ルール: タイムアウト、リトライ、サーキットブレーキングといったガバナンス機能はモデル層に沈み込んでおり、外部ラッパーとしてではなく、データフローの一部として自動的に注入されます。
- ソース分析: `ModelUtils.applyTimeoutAndRetry()` が Flux チェーンに直接 `.timeout()` と `.retryWhen(Retry.backoff(...))` を注入し、ExecutionConfig に基づいて自動的に有効化されるため、ビジネスコードへの侵入はゼロです。

**4. Reactor Flux ネイティブなストリーミング出力**

- ルール: モデル呼び出しのチェーン全体が Project Reactor を完全に基盤としており、ストリーミング/非ストリーミングとも同じインターフェースから Flux を返し、バックプレッシャーとノンブロッキング I/O をサポートします。
- ソース分析: `doStream0()` は stream パラメータに応じて、SSE ストリーミングレスポンスと `Flux.defer().subscribeOn(boundedElastic())` による同期呼び出しを動的に切り替えます。ガバナンス用のオペレーターはストリームにシームレスに組み込まれており、上流は連続したデータフローとして認識します。

**5. オブザーバビリティと高度な推論機能のゼロ侵入統合**

- ルール: トレース計装、Prompt キャッシュ、ツール呼び出しの拡張はモデル層で自動的に完了し、ビジネスコードは手動での対応を必要としません。
- ソース分析: `ChatModelBase.stream()` は `TracerRegistry.get().callModel()` を通じて呼び出しを自動的にラップします。cacheControl=true の場合、`OpenAIBaseFormatter.applyCacheControl()` がキャッシュマーカーを自動的に追加します。toolChoice と parallelToolCalls パラメータはツールの挙動を直接制御します。

![モデル層アーキテクチャ I](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j2vyuibOsbQibMibMjVQOymQcVxoTOX2VY8z2jHJ6XdAN5A5FCfD8zWgxt5Abdt2sGI95MLD7eJFMF6pKYduAc8jvaMYS0VfMWw8c/640?wx_fmt=png&from=appmsg)

![モデル層アーキテクチャ II](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j1DibUdPnJYUkxRCUuFicfJYuVibukFtNia9avWGA43z3Y4wRZdibIjpELEIG5mcdibnHDaX7wavnsMHEibZz20TE8FFys2bNw8KjKkpU/640?wx_fmt=png&from=appmsg)

### 3.2 ReAct 推論ループ——「Q&A」から「マルチステップの自律的な意思決定」へ

1. **標準化された 3 段階ループ**: 「Thought → Action → Observation」というステートマシンを厳密に踏襲し、モデルが自律的に終了を判断するか、`maxIterations` に達した時点で強制終了することで、無限再帰を回避します。
2. **動的なコンテキスト注入**: 各推論ラウンドの前に、利用可能なツールのスキーマと履歴の軌跡が自動的にフォーマットされ Prompt に注入され、モデルの意思決定が最新の情報に基づいたものになり、ハルシネーションによる呼び出しを減らします。
3. **ストリーミングによる中間状態の公開**: `Flux<AgentResponse>` を返し、思考、ツール呼び出し、実行結果などのイベントをリアルタイムにプッシュします。これによりフロントエンドで推論プロセスを一語ずつ表示でき、ブラックボックス的な体験を打破します。
4. **ツール実行の安全な隔離**: ツール呼び出しは独立して実行され、パラメータの検証と返り値のフィルタリングが行われます。例外は Observation としてモデルにフィードバックされ、自己修正を可能にし、クラッシュやデータ漏洩を防ぎます。
5. **リソース境界のハード制約**: `maxIterations`、`maxTokensPerStep`、`toolTimeout` などの設定により、上限を超えたリクエストは Flux チェーン内で自動的にインターセプトされ、本番環境の SLA を制御可能な状態に保ちます。

**コア価値**: LLM の推論を、監視可能で制約可能な、インタラクティブなエンジニアリングサービスへと変換し、複雑な業務財務タスクの自律的な完了をサポートします。

![ReAct 推論ループ](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j1MeshjI7gMzwO6VSgm5Zk5BMW86TIVic964Wz9CAztjhugiba0T88libnUCLeWGCjIPiaLjQEK5eope1N8yicgeWdXqFNlW873l1fI/640?wx_fmt=png&from=appmsg)

### 3.3 外部抽象化層

#### 3.3.1 コアクラス

![コアクラス I](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j3ib9BywL0pHsIic713gTxfAiaSjG8BGuSfUuWev261JK5icTkcK3JMq5mK9N7kkaac4sSTfKDVKicdLZZ0wticn8oxB2IAlJdFOibRHg/640?wx_fmt=png&from=appmsg)

![コアクラス II](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j1iaBUVfzYHicXd1agekCxh6mIAy5QzvfVTibiarpjSOCbGvOnnV3RRib5unBhb75Wx4T7etLn6l9ETfNhjQfYmjPRZf3DwoPxL8dTU/640?wx_fmt=png&from=appmsg)

#### 3.3.2 ツールショートカットメカニズム

![ツールショートカットメカニズム](https://mmbiz.qpic.cn/sz_mmbiz_jpg/bvDbzNRia8j2aaNqJMg8QVlluoW4DZo4porVe4rTHBQ2PI9koG87c9oCPEibvSqnWEtBTsia7ctecEO4s6Pian9cZKAuYK1rcm917qZUdIKsvhw/640?wx_fmt=jpeg&from=appmsg)

## 04 再利用層の 3 層アーキテクチャ

プラットフォーム構築のポイントを検討したことに加え、実践に取り組む前に、私たちは再利用層の構築も検討しました——これは Alibaba のエコシステムと統合したあとに、よりハイレベルな即戦力の使いやすさを実現するためです!

また、将来的に他のビジネスが私たちの Agent 実装を参照して、より簡単に迅速なカスタマイズができるようにすることも考慮しました!

### 4.1 ツールクラスと Alibaba エコシステムプラットフォームの統合

![ツールクラスと Alibaba エコシステムプラットフォームの統合](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j1XPXn6Kj87p0BzWDFYssB8Mw2pzcF6OUOrUJzJt4sYBIP3hWXTkzGEBDBIlGwfcEvMKicmrKibJKhG3qh8VhwAnS8dyPKHHGv6I/640?wx_fmt=png&from=appmsg)

AgentScope はネイティブでは `AgentSkill` + `SkillBox` のインターフェース定義のみを提供し、外部ソースは一切提供しません。再利用層がエンタープライズグレードのツールと派生クラスのエコシステムを完成させます。

![再利用層が完成させたエンタープライズグレードのツールエコシステム](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j3QNqRlZnYSoAmIS1sic4TbsDYUyzicviaO3Fr2dIRwZX4OnBJJOpic0usOQOMKYK7vrJdzeJaXP1dUDmNbdWh9erBcTEmelcVqqVw/640?wx_fmt=png&from=appmsg)

この層は Alibaba マーケットプレイスへのシンプルなアクセス機能を提供します。

### 4.2 Agent および関連エコシステムの登録、発見、呼び出し

![Agent の登録と発見 I](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1o7ibI14knbyklsPOjHiaVegyIEGbvlhBPbfuUnVtY4nfQS0oE8IviaiatSdBWUaRDPAfLN84XsnojicyNmwBGticX0aoS48aq80pwY/640?wx_fmt=png&from=appmsg)

![Agent の登録と発見 II](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j0QXYD5uibKFWaVgiaT1kWH9iavqze4dnp0b13SweLZDycLQF6wc2mTETrjcvvfMLtZAPXGdKHibicFUJiaXe2wmaAlKSZTMZzQ3G9Eg/640?wx_fmt=png&from=appmsg)

### 4.3 リンクオーケストレーションと管理 | AG-UI プロトコルによるリンクオーケストレーション

![AG-UI プロトコルによるリンクオーケストレーション](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1ib4ASy4QUsSj3rlvxoNgvX5OswkBn5vWQicwTncRA3icicX0pT7vGoNTl0cYhJykPq86SXKvRhZKQwtG2AV2qovBBicgmN69AyDD8/640?wx_fmt=png&from=appmsg)

再利用層は AG-UI プロトコル(SSE)をベースに、フロントエンドから LLM までのエンドツーエンドのストリーミングを実現しています。外部には AG-UI 標準の入出力を公開し(`AguiMvcController` が `RunAgentInput` を処理し、`AguiMessage` イベントストリームを発行します)、内部では `ReActAgent` をコアオーケストレーションエンジンとして LLM の推論・ツール呼び出しループを駆動しています。リクエストレベルのコンテキスト(ユーザーアイデンティティ、RAG クエリ)は `AguiRuntimeContextBuilder` SPI と `RuntimeContext` を通じて運ばれ、セッションレベルの状態の永続化(Memory / Toolkit / AgentMeta)は `Session` SPI と `AguiSessionManager` を通じて管理されます。

![AG-UI エンドツーエンドストリーミングリンク](https://mmbiz.qpic.cn/sz_mmbiz_jpg/bvDbzNRia8j3P12U6CQY6ibyibEggtr41SicE8qmdwHan1ibf1V8Bnvxj5gam1icFzsLictMcfNf6XTFmxHkHodv36oxnYTsM6o4fvWBzdadevic2KA/640?wx_fmt=jpeg&from=appmsg)

ランタイム層には多くの概念があります。以降のセクションで、実践的なアプローチを詳細とあわせて説明していきます。

### 4.4 全リンクオブザーバビリティ

![全リンクオブザーバビリティ](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j3zVzFVHq6pZE5YFjYqgvYsHINtyFGZ3icJTozY0kZ6UuXZMja3iabfNicnohTLEGRhzo50BzR8EWalNQibNQniczOt8pFrRPvIp5Ik/640?wx_fmt=png&from=appmsg)

## 05 開発の要点に基づく実践プロセス

これまでのセクションで、AgentScope と再利用層のコアアーキテクチャと能力の境界を整理してきました。これを踏まえ、5 つの主要な構築ポイントに沿って実践のプロセスを一つずつたどっていきます——どんな問題に遭遇し、どう分解し、どう設計・実装したかです。

### 5.1 要点その一: DB 駆動の Agent 実行エンジン

#### 5.1.1 注力すべきゴール

「機能は価値に奉仕する」とよく言われます。技術的な実装に深く踏み込む前に、まずは物流コスト業務財務チームの未来の働き方を描いてみましょう。

私たちのプラットフォームには、数百人の最前線の運用担当者がいると想像してください。彼らは毎日、請求、決済、資金フロー、請求書処理、財務会計という 5 つの中核リンクの間を行き来しています。現状は、システムの規模が巨大であるにもかかわらず、大量の高価値なエネルギーが非効率な「手動照合」や「反復的な転記」に費やされているというものです——画面と画面の間で疲れた目を行き来させ、データはスプレッドシート間で機械的にコピーされています。彼らは解放を切望しており、疲れを知らず正確で効率的なデジタルアシスタントを切実に必要としています。

したがって私たちのゴールは、単に固定された自動化ツールをいくつか提供することではなく、活気ある Agent 利用エコシステムを構築することです。

このエコシステムの中で、ビジネスの痛点を最もよく理解しているのは、もはや遠く離れた開発者ではなく、最前線の同僚たちです。私たちは彼らに Agent を手動で設定する能力を与え、現在のビジネスの変動やパーソナライズされたニーズに基づいて、まるで積み木を組み立てるように、自らの手で専用の Agent を定義し訓練できるようにします。大型セール時の決済ピークに対応する場合でも、複雑な異常請求を処理する場合でも、彼らは必要に応じて設定し、即座に公開し、迅速にイテレーションできます。

これは効率の革命であるだけでなく、役割の再構築でもあります。すべての運用担当者が、単調な作業者からインテリジェントなプロセスの設計者へと進化し、真に「誰もが開発者、あらゆる場所にインテリジェンス」を実現するのです。

![Agent 利用エコシステムのビジョン](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j2jwHfUJaSKtjNoqgASzpWJhMdFmh50vX5MGl8nBtWsQiaZPSSric4Bs6wWBoZVI5PuhMLx9kxal4IC41iapwdr4sosiawWwQuwnBI/640?wx_fmt=png&from=appmsg)

#### 5.1.2 DB 設計

![DB 設計の概要](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j2cgjUo9FO8uYpiaeF4xd3mC894tHRk1YE3w9vooNP5CaMUUic9WUia302XANwqdMWwibhkQMbrexhzbzGbiaKSaUOSjgfticL1zoZ4k/640?wx_fmt=png&from=appmsg)

プロジェクトには合計 19 のテーブルがあり、機能ドメインによって 7 つのモジュールに分かれています。

![機能ドメインのモジュール I](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j3t1TeDCm6Obs94Am796JmJs5ISAWIBNPJd8AxB1FNHpRwticSvTI6DBQtUEUrINyNqNrhx9deQmL6FtwibBHibcPS4dATlpbxqE0/640?wx_fmt=png&from=appmsg)

![機能ドメインのモジュール II](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j2fnYFHhfVTia7rDxNtaEkRdbo8IMbkzfQlRumXojOcbU7jRm3McrfxDeoyKqdGAsFlP5lWMwqJbooXGY358tpjibKnO5CIvbcC4/640?wx_fmt=png&from=appmsg)

#### 5.1.3 全体フローのコア設計(再利用層と併せて確認)

![全体フローのコア設計](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j0c2NLt9WpyAeQVYM6pb7PR2uicPFQnJAuibAOcgRcnEWXEaD1HWjQPhKlJtZlv63nPQM2Cw0XgibYtgm9AO26AwqgPxvWibQrLUibs/640?wx_fmt=png&from=appmsg)

DB 駆動の Agent 実行を実現するためのコア設計ポイントは以下の通りです。

##### 5.1.3.1 コアリンクその一: 起動時の登録——DB からインメモリスナップショットへ

再利用層には多くの登録機能があり、DB 駆動設計のコアは自然に DB データを登録ポイントに接続する必要があります——これはソケットパターンの典型的な設計です!

適切なタイミングはいつでしょうか。答え: アプリケーションの起動時と、コアな SQL 属性の更新の両方が、レジストリを通じた Agent 全体の設定更新をトリガーする必要があります!

アプリケーションの起動から始めましょう——私たちは統一的にスーパー基底クラスから開始し、DB をスキャンして接続を完了します。それが `AgentFactoryRegistry` です!

接続に加えて、`AgentFactoryRegistry` のもう一つのコアなゴールは、ランタイムスナップショットを構築することです。これは以降のチャット会話で重要な役割を果たします!

`AgentFactoryRegistry`(`SmartInitializingSingleton` を実装)は、すべての Spring Bean が初期化された後に 3 段階の起動処理を実行します。

1. **静的インデックスの構築**: すべての `BaseFinanceAgentFactory` サブクラス Bean(コード内で定義された静的 Agent)を収集し、`agentCode` をキーとして `staticIndex` に格納します。
2. **DB の読み込みと分割**: `ac_agent_config` から有効化されたすべての設定を読み込み、静的セットとの積(intersection)を取ります。
    - **静的 Agent**(対応する Factory クラスあり)→ `factory.refreshFromDb()` を呼び出す
    - **動的 Agent**(DB には存在するがコードに Factory クラスがない)→ `dynamicRegistry.register(agentCode)` を呼び出して `DynamicAgentEntry` を自動作成し、最終的に refreshFromDb も呼び出す
3. **失敗の集約**: すべての Agent の処理が終わった後、失敗リストを一度にスローして fail-fast とします。

まず `AgentFactoryRegistry` を見てみましょう。

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AgentFactoryRegistry implements SmartInitializingSingleton {
    private static final Logger logger = LoggerFactory.getLogger(AgentFactoryRegistry.class);
    @Resource
    private List<BaseFinanceAgentFactory> staticFactories;
    @Resource
    private DynamicAgentRegistry dynamicRegistry;
    @Resource
    private AcAgentConfigMapper acAgentConfigMapper;
    @Resource
    private DynamicAgentProperties dynamicProperties;
    private final Map<String, BaseFinanceAgentFactory> staticIndex = new LinkedHashMap<>();
    @Override
    public void afterSingletonsInstantiated() {
        // ==================== フェーズ1: 静的インデックスの構築(防御的フォールバックとして DynamicAgentEntry を除外) ====================
        for (BaseFinanceAgentFactory f : staticFactories) {
            if (f instanceof DynamicAgentEntry) continue;
            String agentCode = f.simpleName();
            BaseFinanceAgentFactory previous = staticIndex.put(agentCode, f);
            if (previous != null) {
                logger.warn("[AgentFactoryRegistry] duplicated static agentCode '{}': {} replaced by {}",
                        agentCode, previous.getClass().getName(), f.getClass().getName());
            }
        }
        logger.info("[AgentFactoryRegistry] static factories ({}): {}",
                staticIndex.size(), staticIndex.keySet());
        // ==================== フェーズ2: DB の全件を読み込み、振り分ける ====================
        List<AcAgentConfigDO> allEnabledConfigs;
        try {
            allEnabledConfigs = acAgentConfigMapper.selectAllEnabled();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load active configs (status>=1) from ac_agent_config", e);
        }
        if (allEnabledConfigs == null) {
            allEnabledConfigs = Collections.emptyList();
        }
        // 2a. 起動時セルフチェック: _published という部分文字列を含む旧来の agentCode を禁止する(サフィックス方式 R1 の命名キー衝突を防ぐ)
        List<String> illegalCodes = allEnabledConfigs.stream()
                .map(AcAgentConfigDO::getAgentCode)
                .filter(c -> c != null && c.contains(AgentVariant.PUBLISHED_SUFFIX))
                .collect(Collectors.toList());
        if (!illegalCodes.isEmpty()) {
            String msg = "ac_agent_config contains agent_code with reserved substring '" + AgentVariant.PUBLISHED_SUFFIX
                    + "' (conflicts with PUBLISHED variant suffix): " + illegalCodes;
            if (dynamicProperties.isStrictMode()) {
                throw new IllegalStateException(msg);
            } else {
                logger.warn("[AgentFactoryRegistry] strict-mode=false, continuing despite illegal agent_code: {}", msg);
            }
        }
        List<String> dbCodes = allEnabledConfigs.stream()
                .map(AcAgentConfigDO::getAgentCode)
                .collect(Collectors.toList());
        Set<String> dbCodeSet = new LinkedHashSet<>(dbCodes);
        Map<String, Throwable> failures = new LinkedHashMap<>();
        // 2b. 静的: DB に対応する有効な行が存在しなければならない(V1 の強い制約と一致); refreshFromDb を呼び出す
        for (Map.Entry<String, BaseFinanceAgentFactory> e : staticIndex.entrySet()) {
            String code = e.getKey();
            if (!dbCodeSet.contains(code)) {
                failures.put(code, new IllegalStateException(
                        "static factory exists but ac_agent_config has no active row (status>=1) for agent_code='" + code + "'"));
                continue;
            }
            tryRefresh(code, e.getValue()::refreshFromDb, failures);
        }
        // 2c. 動的: DB の全件から静的集合を除いたもの。自動的に DynamicAgentEntry(DRAFT バリアント)を作成する
        List<String> dynamicCodes = dbCodes.stream()
                .filter(c -> !staticIndex.containsKey(c))
                .collect(Collectors.toList());
        for (String code : dynamicCodes) {
            tryRefresh(code, () -> dynamicRegistry.register(code), failures);
        }
        // 2d. current_publish_version > 0 の Agent について PUBLISHED バリアントを登録する(key=${code}_published)
        //     注: PUBLISHED バリアントの失敗は DRAFT のサービス可用性に影響しない。個別に集計し、warn のみで fail-fast はしない
        Map<String, Throwable> publishedFailures = new LinkedHashMap<>();
        List<String> publishedCodes = new ArrayList<>();
        for (AcAgentConfigDO cfg : allEnabledConfigs) {
            Integer cpv = cfg.getCurrentPublishVersion();
            if (cpv == null || cpv <= 0) {
                continue;
            }
            String code = cfg.getAgentCode();
            String publishedKey = AgentVariant.PUBLISHED.registryKey(code);
            tryRefresh(publishedKey,
                    () -> dynamicRegistry.registerPublishedVariant(code),
                    publishedFailures);
            publishedCodes.add(code);
        }
        if (!publishedFailures.isEmpty()) {
            String summary = publishedFailures.entrySet().stream()
                    .map(e -> "  - " + e.getKey() + " → " + e.getValue().getMessage())
                    .collect(Collectors.joining("\n"));
            // PUBLISHED バリアントの失敗は warn のみで、fail-fast はスローしない(DRAFT は引き続きサービス可能)
            logger.warn("[AgentFactoryRegistry] PUBLISHED variant init failed for {} agent(s), DRAFT remains available:\n{}",
                    publishedFailures.size(), summary);
        }
        // ==================== フェーズ3: 失敗の集計 + strict-mode 制御(fail-fast に関与するのは DRAFT の失敗のみ) ====================
        if (!failures.isEmpty()) {
            String summary = failures.entrySet().stream()
                    .map(e -> "  - " + e.getKey() + " → " + e.getValue().getMessage())
                    .collect(Collectors.joining("\n"));
            String msg = "Agent init failed for " + failures.size() + " of " + dbCodes.size()
                    + " agent(s):\n" + summary;
            if (dynamicProperties.isStrictMode()) {
                throw new IllegalStateException(msg);
            } else {
                logger.warn("[AgentFactoryRegistry] strict-mode=false, continuing despite failures:\n{}", msg);
            }
        }
        logger.info("[AgentFactoryRegistry] init OK. static={}, dynamic={}, published={}, total={}, strict-mode={}",
                staticIndex.size(), dynamicCodes.size(), publishedCodes.size() - publishedFailures.size(),
                dbCodes.size(), dynamicProperties.isStrictMode());
    }
    private static void tryRefresh(String agentCode, Runnable r, Map<String, Throwable> failures) {
        try {
            long start = System.currentTimeMillis();
            r.run();
            logger.info("[AgentFactoryRegistry] init OK for '{}' in {} ms",
                    agentCode, System.currentTimeMillis() - start);
        } catch (Throwable t) {
            logger.error("[AgentFactoryRegistry] init FAILED for '{}': {}", agentCode, t.getMessage(), t);
            failures.put(agentCode, t);
        }
    }
    // ==================== 外部からの照会・ディスパッチ API ====================
    /**
     * 統一検索: まず静的、ヒットしなければ動的。
     */
    public BaseFinanceAgentFactory find(String agentCode) {
        BaseFinanceAgentFactory f = staticIndex.get(agentCode);
        return f != null ? f : dynamicRegistry.get(agentCode);
    }
    /**
     * レジストリキーによるリフレッシュのディスパッチ(key は DRAFT のビジネス agentCode、または PUBLISHED の
     * {@code agentCode_published} のいずれか)。
     *
     * <p>登録済み → {@link BaseFinanceAgentFactory#refreshFromDb}; 未登録 → オンデマンドの動的登録:</p>
     * <ul>
     *   <li>PUBLISHED キー({@link AgentVariant#PUBLISHED_SUFFIX} に一致)→
     *       {@link DynamicAgentRegistry#registerPublishedVariant}(公開済みスナップショットのパス経由でロード);</li>
     *   <li>DRAFT キー → {@link DynamicAgentRegistry#register}(ドラフトのパス経由でロード)。</li>
     * </ul>
     *
     * <p>これにより、クラスタブロードキャスト {@code AGENT/xxx_published} を他ノードが受信した際にも、PUBLISHED
     * バリアントが新しい DRAFT Agent と誤認されることなく正しく登録される。</p>
     */
    public void refresh(String key) {
        BaseFinanceAgentFactory f = find(key);
        if (f != null) {
            f.refreshFromDb();
            return;
        }
        AgentVariant.ParsedKey parsed = AgentVariant.parse(key);
        if (parsed != null && parsed.variant() == AgentVariant.PUBLISHED) {
            logger.info("[AgentFactoryRegistry] PUBLISHED key '{}' not registered yet, attempting on-demand register", key);
            dynamicRegistry.registerPublishedVariant(parsed.agentCode());
        } else {
            // ランタイムで DB に新しい行が追加された → DRAFT バリアントのオンデマンド動的登録
            logger.info("[AgentFactoryRegistry] '{}' not registered yet, attempting on-demand dynamic register", key);
            dynamicRegistry.register(key);
        }
    }
    /**
     * 登録済みのすべての Agent を一括リフレッシュする。
     * <p>単一の失敗は他の Agent に影響しない。失敗した agentCode は呼び出し元に返される。</p>
     */
    public Map<String, String> refreshAll() {
        Map<String, String> failures = new LinkedHashMap<>();
        for (String code : allAgentCodes()) {
            try {
                find(code).refreshFromDb();
            } catch (Exception ex) {
                logger.error("[AgentFactoryRegistry] refresh failed for agentCode='{}'", code, ex);
                failures.put(code, ex.getMessage());
            }
        }
        return failures;
    }
    /** 登録済みのすべての agentCode(static ∪ dynamic、重複排除済み、順序あり)。 */
    public Set<String> allAgentCodes() {
        Set<String> all = new LinkedHashSet<>(staticIndex.keySet());
        all.addAll(dynamicRegistry.agentCodes());
        return Collections.unmodifiableSet(all);
    }
    /** Controller の GET エンドポイント向け: agentCode から Factory を取得する(スナップショット閲覧用)。 */
    public BaseFinanceAgentFactory get(String agentCode) {
        return find(agentCode);
    }
    /** 登録済み agentCode の一覧。既存の Controller との互換性のため旧メソッド名を維持している。 */
    public Set<String> agentCodes() {
        return allAgentCodes();
    }
}
```

なぜ afterSingletonsInstantiated を使うのでしょうか?

まず、`AgentFactoryRegistry` はすべての静的な `BaseFinanceAgentFactory` インスタンスを収集し、DB の設定データを取得する必要があります。ここで `@PostConstruct` や `InitializingBean.afterPropertiesSet()` を使うと、タイミングの問題が生じてしまいます。

これは以下の図で理解できます。

![Bean 初期化のタイミング](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j0JQ3bR9OexSyM7QcPlpfvJGXdVVjcXd09mib4tjT0MDCic2pME2K1T4Dkg4Y2aZt5678PPfiarZrpPEWpnLLjL5WQOhlljQs2DdA/640?wx_fmt=png&from=appmsg)

静的であれ動的であれ、すべての Agent は最終的に `refreshFromDb()` 操作を実行します。`refreshFromDb()` のコアな動作は、Prompt、モデルパラメータ、Toolkit(MCP Client を含む)、SkillBox(Skill コンテンツを含む)を集約した、不変な volatile スナップショットオブジェクトである `AgentConfigSnapshot` を構築することです。それ以降の `createAgent()` 呼び出しは、1 回の volatile 読み取りだけでフィールド横断的に一貫したビューを得ることができ、DB 呼び出しは発生しません。

![AgentConfigSnapshot の構築](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j2glZsQwTL1t3dSjQnSsdQtCQeJYT8QZmGocMt6JI5Ap1HJ7KmQqh9NE4fbmOgGvERgko88JFqicEknQFDuFib5da0AXToFdrUeI/640?wx_fmt=png&from=appmsg)

スナップショットに加えて、登録があります——登録ステップでは 3 つのことを行います。

- `AguiAgentRegistry` への登録: `sparkRegistry.registerFactory(agentCode, entry::createAgent)` は、メソッド参照を通じて PROTOTYPE セマンティクスを実装します(リクエストごとに新しいインスタンス)。以降、フレームワークの `DefaultAgentResolver` が agentCode で検索します。
- `ResourceRegistry` への登録: Agent を DevTool のトポロジー図に表示させます。
- A2A ゲートウェイへの登録: `DynamicA2ARegistrationAdvice` を通じて A2A プロトコルのエンドポイントとして公開されます。

##### 5.1.3.2 コアリンクその二: すべての主要テーブルと関連プロパティが管理状態にあり、管理者はページを通じて永続化する

Management State では、Agent に関連するすべての設定はフロントエンドのページを通じて操作され、リレーショナルデータベースに永続化されます。Runtime は読み取り専用の消費者としてのみ機能し、公開ステータスに基づいて対応する設定スナップショットまたはドラフトをロードすることで、設定の変更とオンライン実行の切り離しを保証します。

設定システムは、主設定ドメイン(独立したエンティティ)とバインディング関係ドメイン(関連するエンティティ)に分かれており、具体的なマッピングは以下の通りです。

![主設定ドメインとバインディング関係ドメイン I](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j2Vg1gqBrQKtcBYr67nAYLOpdWhXIYYGkacUrYPLL1bgoEGsOB7mCgUNbyhJUmBubMHFOWsRGIPA7NibXjl6avzZWaTaDqPRgcQ/640?wx_fmt=png&from=appmsg)

![主設定ドメインとバインディング関係ドメイン II](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1FyqwFXfR7MuD4l6mhPWqHOHT0zPZUUFuia6aRlaxCG4aSPKwiaoFcSEvMiazbHnGPd798YdjUIwkibRsiceEXtksyasWNvOdjFVWA/640?wx_fmt=png&from=appmsg)

「設定の柔軟性」と「オンラインの安定性」のバランスを取るため、システムは厳格なデュアルトラックのバージョン管理メカニズムを採用しています。

![デュアルトラックのバージョン管理メカニズム](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j1UY3qg9SKRfl1NVzjxXm8FUHHT4HvZiaOZTiaXd929mWSWb0b1j8kwxznAbx3VK2qK4b7Q9iaia29Vr5gOPlVToHODPictTVP9rhwg/640?wx_fmt=png&from=appmsg)

**ドラフト段階**:

- 操作: ユーザーがページ上で Prompt、モデルパラメータ、またはバインディング関係を修正します。
- 永続化: すべての変更は主テーブル `ac_agent_config` とその関連するバインディングテーブルに直接更新されます。
- ステータスマーク: `ac_agent_config.status` は `1`(有効/ドラフト)のままです。
- 影響範囲: 開発/テスト環境や、ドラフトを明示的に読み込むデバッグセッションにのみ影響し、正式なオンライントラフィックには影響しません。

**公開段階**:

- トリガー: ユーザーが「公開」ボタンをクリックします。
- スナップショットの生成: システムは現在の主テーブルとすべてのバインディングテーブルの完全な設定を JSON にシリアライズし、`ac_agent_publish_record` に書き込みます。このレコードは不変であり、監査とロールバックに使用されます。
- バージョンの昇格:
  1. `ac_agent_config.current_publish_version` がインクリメントされます。
  2. `ac_agent_config.status` が `2`(公開済み)に更新されます。
- 影響範囲: 正式なオンライントラフィックが直ちに新しく公開されたスナップショットバージョンへ切り替わります。

**ランタイムでのロード**:

- DRAFT バリアント: 主に DevTool でのデバッグやカナリアテストのシナリオに使用され、`ac_agent_config` 内の最新のドラフトデータを直接読み込みます。
- PUBLISHED バリアント: 本番環境の標準モードで、`current_publish_version` に基づいて `ac_agent_publish_record` から対応する不変スナップショットをロードします。

**利点**:

- 安全な隔離: 設定変更中の中間状態(未完了の Prompt 編集など)がオンラインサービスを汚染しません。
- 即座のロールバック: 公開後に異常が発生した場合、`current_publish_version` を前のバージョン番号に向け直すか、古いスナップショットを再公開することで秒単位のロールバックを実現します。
- 監査証跡: `ac_agent_publish_record` はすべてのリリースの完全な状況を保持しており、`ac_agent_config_audit_log` と組み合わせることで全リンクの設定変更のトレースが可能になります。

![設定公開フロー](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j3JjOgxLmxic3vLibA4icLrIHwokubCCspMCQwjZUCdDk8NWt99BHXggSFyxhEobS8vR0COZ543fxZftGOcd1dR4M4lcJEiaRKtGZA/640?wx_fmt=png&from=appmsg)

##### 5.1.3.3 コアリンクその三: ランタイム状態——再利用層の全リンク SPI カスタマイズ

第 4 章では先に再利用層のランタイムリンクを定義しました——これは、以降のすべての Agent 操作リンクを一貫させるためです! そして `AguiMvcController` こそが、再利用層のランタイム全体のエントリーを統括する存在です!

`UnifiedAguiRestController` はチャットリクエストを処理するために私たちが定義したコアコントローラーであり、リクエストを再利用層フレームワークの `AguiMvcController` に直接委譲し、AG-UI プロトコルの SSE ストリーミング、ReAct ループ、ツール呼び出しといったランタイム機能を完全に再利用しています。

再利用層の機能がカスタマイズのシナリオをカバーできない場合はどうするのでしょうか。再利用層のカスタマイズは、フレームワークが事前に定義した SPI インターフェースを通じて実現されます。

![再利用層の SPI カスタマイズポイント](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1XnmwPF6fSGNzAgJUOZBpjb07e07hHQo3QE3XPVliaHXllcgl7EC9ia0g88ZREO6y2qnlPmIVaVjhZl5Jvx5MetkdIUL3SOtc2Q/640?wx_fmt=png&from=appmsg)

**設計の要点**: 私たちはフレームワークのランタイムリンクを変更することも、リンクを拡充することもしません。代わりに、フレームワークが残した SPI ポイントでデフォルト実装を置き換えます。フレームワークはプロトコルのパースと ReAct のオーケストレーションを担い、プロジェクトはビジネスロジックとインフラの統合を担います。

#### 5.1.4 ランタイムの深掘り

前のセクションでは、ランタイムリンクが再利用層の全リンクを完全に再利用していると述べましたが、同時にそのリンクの各チェックポイントが SPI で満ちていることも示しました。このセクションでは、このリンクを分析し、私たちがどのように設計目標を達成したかを説明します。

ゴール: 軽量な隔離ランタイム——「使うときに構築する(build on use)」メカニズムを採用し、ランタイムで軽量な Agent インスタンスのシェルを動的に構築します。これは、下層の Skill、SKILL、MCP インスタンス(前述のスナップショット)を再利用しつつ、以下を保証しなければなりません。

① 効率的なリソース共有

② 優れたパフォーマンス

これらを実現しつつ、Agent のセッション実行環境の完全な隔離を達成し、安定性とセキュリティを保証します。

いくつかのリンクの概念的な詳細は、第 4 章の運用リンク層で詳しく述べられています。ここでは、実際のシナリオと組み合わせてランタイム設計をどう行うかの分析に焦点を当てます。

再利用層ランタイムのコアクラスと主要な SPI をもう一度見てみましょう。

![再利用層ランタイムのコアクラスと主要 SPI](https://mmbiz.qpic.cn/sz_mmbiz_jpg/bvDbzNRia8j0ue9sLicFyTFeiaYJRWWcIA5JqA28U3somYl4DxlSWvZLHgKEKDiczFUT1HsjSVJLicZzDP5le4pkZgfGIGWDJzzAI3rwgerYSdE8/640?wx_fmt=jpeg&from=appmsg)

再利用層 SPI の主要な設計パターン:

1. **ストラテジーパターン**: `AguiRuntimeContextBuilder`、`AguiSessionManager`、`AgentTool`、`Hook` はすべて置き換え可能なストラテジーです。
2. **ファクトリーパターン**: `AguiAgentRegistry` は `Supplier<Agent>` ファクトリーを保持し、リクエストごとに新しいインスタンスを生成します。
3. **アダプターパターン**: `AguiAgentAdapter` は ReActAgent の内部イベントを AG-UI の標準イベントに変換します。
4. **オブザーバーパターン**: `Hook` はイベントのリスニングを通じてツール呼び出しのインターセプトを実装します。

技術的な詳細に関心がなければ、以下の図で再利用層の SPI 設計ノードがどこにあるかを確認できます。

![再利用層の SPI 設計ノード](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j3twOmMLjPRVa3WopcJ61RQhC2CSvSL3Eupf2bZ0KB1C4wKKV2uWXLb7DfHGac4tIx6ttAm1KcqFYbRdiacoygF6UlQDJ1HrBsc/640?wx_fmt=png&from=appmsg)

技術者であれば、これらのコアクラスが完全な呼び出しリンク図のどこに位置するかを見てみることをお勧めします。

![完全な呼び出しリンク図におけるコアクラスノード](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j1pf1AvyaV7eZuAfmreAJmy34ItjZfKbfInhNQUH1hNic4TtUNnVIrmbdsPVUia0iaB96ibxNUzIekcN4Ed9zzQ0FZKrialhnqhFjjI/640?wx_fmt=png&from=appmsg)

ここからは、build-on-use、アイデンティティの伝播、正確な人機協調、セッションの永続化というコア機能を拡張するために、ランタイムリンクの 4 つの主要な SPI の能力をどのように組み合わせたかを見ていきましょう。

![4 つの主要な SPI カスタマイズ機能の概要](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j2F61Ra4lOcybxiaoUet1ttUZKyeAcdxQibgrWuomF2iacd8rVibvdlJG2JAhv9jPMWnZkXaxMiaER8ckonIcI7tdW5dALlhKHNMiciaA/640?wx_fmt=png&from=appmsg)

4 つの SPI はそれぞれカスタマイズされた Finance グレードのインスタンスを生み出します。以下で詳細を説明します。

##### 5.1.4.1 FinanceAguiRuntimeContextBuilder(SPI1)

FinanceAguiRuntimeContextBuilder は、私たちがランタイムリンク向けにカスタマイズした最初の SPI です。

```java
public ProcessResult process(RunAgentInput input, String headerAgentId, String pathAgentId) {
        String threadId = input.getThreadId();
        String agentId = this.resolveAgentId(input, headerAgentId, pathAgentId);
        RunAgentInput effectiveInput = input;
        if (this.agentResolver.hasMemory(threadId) && !input.hasResume()) {
            logger.debug("Using server-side memory for thread {}, extracting latest user message", threadId);
            effectiveInput = this.extractLatestUserMessage(input);
        }
        // 実行前に構築されたコンテキスト
        RuntimeContext runtimeContext = this.buildRuntimeContext(effectiveInput);
        Agent agent = this.agentResolver.resolveAgent(agentId, threadId);
        AguiAgentAdapter adapter = new AguiAgentAdapter(agent, this.config, runtimeContext);
        Flux<AguiEvent> events = adapter.run(effectiveInput).doFinally((signal) -> {
            logger.debug("Request completed for thread {}, signal: {}", threadId, signal);
            this.agentResolver.onComplete(threadId, agent);
        });
        return new ProcessResult(agent, events);
}
```

これは本質的に、AgentScope のリクエストリンク全体にまたがるコンテキストオブジェクトを構築します。再利用層フレームワークは、`AguiRuntimeContextBuilder` SPI を通じて `RuntimeContext` の作成をビジネス層によるカスタマイズに開放しています。

このプロジェクトでは、`AguiRuntimeContextBuilder` を継承することで `FinanceAguiRuntimeContextBuilder` を実装し、各 AG-UI リクエストのコンテキストデータをフレームワークの `RuntimeContext` に組み立てています。これには 4 つのフィールドが含まれます。

![RuntimeContext の 4 つのフィールド](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j2j9Ej4HMKzsGkOYGgZzrIMSMvElOudtFzH76cSqswrOz6I1Vw8tXgTP1yCoZ7qoCklicCGtDvUTXNGBp3v8VFI0wUOUvNTNFw4/640?wx_fmt=png&from=appmsg)

なぜこのようにするのでしょうか?

**理由その一: スレッドセーフティ。** 再利用層の下層モデルは非同期のスレッドプールであり、スレッドは複数のセッションをまたいで再利用されます。リクエストレベルのデータを `ThreadLocal` で受け渡すと、スレッドがプールに戻った後に残存するデータが後続のセッションを汚染する可能性があります。`RuntimeContext` はフレームワークによって `AgentBase` インスタンス(Agent インスタンスごと)に紐づけられており、リクエストとともに作成され、リクエストとともに破棄されます——自然に隔離されており、セッションをまたいだデータ漏洩のリスクはありません。


**理由その二: 全リンクでの到達可能性。** `RuntimeContext` は Agent の作成から実行までの完全なライフサイクルにまたがっており、複数の下流のリンクノードが、追加のパラメータ受け渡しなしに `agent.getRuntimeContext()` を通じて直接消費できます。

![RuntimeContext の全リンク到達可能性](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1eicYBIKHufpOPERtSVarFrRCbGAfib3n75RdOUicqaXEomK9zicOGgJbZN98nR3ic4bBFvXKfuIdYBJTkDkNX56CtX0xLmcicak2pQ/640?wx_fmt=png&from=appmsg)

2 つの設計上の利点を組み合わせると、スレッドプールの再利用によるデータの混線を回避しつつ、リクエストレベルのデータを全リンクのどのノードでもすぐに利用できるようにできます——これが、`ThreadLocal` やメソッドパラメータの受け渡しではなく `RuntimeContext` を選んだ根本的な理由です。

##### 5.1.4.2 FinanceAguiSessionManager(SPI2)

その名前だけを見ると、単なるセッションマネージャーだと思うかもしれませんが、それは正確ではありません——そのインターフェースを見てみましょう。

```java
public interface AguiSessionManager {
    Agent getOrCreateAgent(String var1, String var2, Supplier<Agent> var3);
    boolean hasMemory(String var1, String var2);
    boolean removeSession(String var1, String var2);
    void cleanupExpiredSessions();
    int getSessionCount();
    default void saveAgent(String threadId, String agentId, Agent agent) {
    }
    void clear();
}
```

**1) AguiSessionManager の設計意図**

AguiSessionManager は再利用層のコア設計の一つです。これは本質的に、Agent インスタンスと永続ストレージの間の橋渡し役であり、AG-UI プロトコルの下でステートレスな HTTP とステートフルな Agent の間の矛盾を解決します。フレームワークは 5 つのメソッドを通じて、完全なセッションライフサイクルの契約を定義しています。

![セッションライフサイクル契約](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j0fe64w7b8fR1pCZcmGEavpJ7ALLOxgDvo9iac2niaapxdr9Fk5qSXxaVibSJvGlhqsG2falUtQl7MZZQNibbiaqQ7nicF5knOX2gbJI/640?wx_fmt=png&from=appmsg)

再利用層フレームワークは、デフォルトで `InMemoryAguiSessionManager`(ConcurrentHashMap キャッシュ、プロセス再起動でデータ消失)と `SessionAwareAguiSessionManager`(フレームワークの Session SPI を経由するが、毎回メモリを完全にシリアライズ/デシリアライズする)を提供しています。どちらも本番グレードの要件を満たすことはできません。

再利用層の `AguiSessionManager` の `getOrCreateAgent` メソッドを設計した際、これは「使うときに構築する」メカニズムを実装するためであると同時に、当然ながらキャッシュメカニズムを利用できるようにするためでもありました。

明らかに、再利用層は運用リンク上にリアルタイムの Agent 作成エントリーポイントを定義することをサポートしています——フレームワークは `Supplier<Agent>` パラメータを通じて Agent の作成権限をビジネス層に渡しており、私たちの `AgentConfigSnapshot` も実際にはキャッシュメカニズムのために設計されました。

前述の通り、スナップショットは Agent に関連するすべて——MCP、Skills、SkillBox、modelParams、Toolkit など——を構築します。そのため、軽量な Agent を素早く作成し、以下を実現できます。

- **効率的なリソース共有**: スナップショットは volatile な参照であり、すべてのリクエストが同じ設定スナップショットを共有します。Agent を作成する際は 1 回の volatile 読み取りのみを行い、DB/ネットワークのオーバーヘッドはゼロです。
- **優れたパフォーマンス**: `createAgent()` はリモート呼び出しを一切トリガーしません。スナップショットから prompt + model + tools を組み立てるだけで、ミリ秒単位で完了します。
- **完全な隔離、安定性とセキュリティの保証**: 各リクエストは独立した ReActAgent インスタンス(PROTOTYPE スコープ)を得て、リクエストごとの memory と RuntimeContext が互いに干渉しません。

目標を設定した後、AguiSessionManager が何であるかを理解する必要もあります。そして AguiSessionManager を理解するには、`AguiRequestProcessor` のコアな役割を理解する必要があります。

**2) 再利用層の AguiRequestProcessor: 運用リンクの最初の 2 大ステージを制御するハブ**

細かく分解すると、再利用層の運用リンクには 20 以上のノードがありますが、大まかに分類すると 4 つのステージに分けられます。

1. **リクエストの前処理** — リクエストコンテキストの構築、各種コアコンポーネントの初期化。
2. **Agent の作成とセッション/メモリのロード** — Agent のインスタンス化と履歴状態の復元。
3. **ReAct ループのメカニズム** — LLM 推論 → ツール呼び出し → 結果の観察 → 再度推論。
4. **セッションの永続化と後処理** — 状態の保存、非同期タスクのトリガー。

`AguiRequestProcessor` は、ReAct ループのメカニズムのステージを除くすべての中核制御クラスです。フレームワークのコードから判断すると、3 つの重要なコンポーネントと 1 つのコアメソッドを保持しています——見てみましょう。

```java
public class AguiRequestProcessor {
    private final AgentResolver agentResolver;              // → AguiSessionManager への橋渡し
    private final AguiAdapterConfig config;                 // アダプタ設定
    private final AguiRuntimeContextBuilder runtimeContextBuilder;  // → 私たちの FinanceAguiRuntimeContextBuilder
}
```

process というコアメソッドが 1 つあります——見てみましょう。

![AguiRequestProcessor の process メソッド](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1n0hYq1bnIuyw72nflL3pZhH4nmxJiaQPfPjU7Yo7UTzTEobRyQPl0Dkbe4a4nGDAceQd2t5lxeDF5Fjwy4piaKWmIMsPVAOCWY/640?wx_fmt=png&from=appmsg)

このコアメソッドのオーケストレーションは、`AguiRequestProcessor` の役割を明確に示しています。それは具体的な Agent の作成、状態のロード、コンテキストの構築を担うのではなく、これらの SPI の呼び出し順序をオーケストレーションすることを担っています。各 SPI がそれぞれの役割を果たします。

![各 SPI の責務分担](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j33Jb2Q52ZMHu5fRXFxtAzfLic5Wul9GtI2TBQOtVuw5cJdInrtejOickRoQCQc3Qq0xdTh6Hr8H50AVOZuA70JqHmNrxAAowgnM/640?wx_fmt=png&from=appmsg)

`AgentResolver` は Agent インスタンスの取得に関する絶対的な権限を保持しています。spark はカスタマイズをサポートしていますが、ここでは再利用層のデフォルトである `DefaultAgentResolver` を、一切の変更なしに使用しています。

`DefaultAgentResolver` の設計意図は、ある核心的な矛盾を解決することです——`AguiRequestProcessor` は「Agent をくれ」ということだけを気にしますが、Agent の取得方法はデプロイモードによって完全に異なる場合があります。

`DefaultAgentResolver` は 2 つのことを行います。

1. threadId と agentId の関係をキャッシュします。
2. sessionManager.getOrCreateAgent メソッドを呼び出します。
   a. そして「`AguiAgentRegistry` から Agent を取得する」権限を getOrCreateAgent に渡します。

こうして、Agent を作成する方法が完全に定義可能であることがわかります。

関係を整理しましょう。`AguiRequestProcessor` は `DefaultAgentResolver` を通じて Agent を管理し、`DefaultAgentResolver` はそのライフサイクルを sessionManager に委譲します。sessionManager は本質的に SPI を通じてカスタマイズ可能であるため、私たちは FinanceAgent の Agent インスタンス、履歴、メモリを真に管理する sessionManager をカスタマイズすることに決めました。

私たちの FinanceAguiSessionManager の設計が、軽量性と隔離性というゴールをどのように達成しているか見てみましょう。

`getOrCreateAgent` メソッドの組み立てプロセスは 3 つのステージに分かれており、プロセス全体は 1 回の volatile 読み取りのみに依存しています。

1. agentSnapshot を通じて Agent を軽量に組み立てます。
2. 履歴セッション + 長期記憶をロードします。
3. 非同期の圧縮タスクを投入する必要があるかを検知します。

agentSnapshot の設計にはいくらかの配慮が必要です。前述の通り、`BaseFinanceAgentFactory` がスナップショットを構築するため、対応する `BaseFinanceAgentFactory` を素早く特定できるようにしておくのが得策です。そのため、プロジェクトの初期化時に、`BaseFinanceAgentFactory` のスナップショットベースの Agent 作成メソッドである createAgent を sparkRegistry に登録しています。

```java
 sparkRegistry.registerFactory(agentCode, entry::createAgent);
```

そうすれば registry.get で対応する作成関数を取得できます——あとは実行するだけです!

createAgent のコアロジックは ReactAgent の組み立てです。具体的には、`createAgent()` の組み立てプロセスは 3 つのステージに分かれており、プロセス全体を通じて 1 回の volatile 読み取りで得た `AgentConfigSnapshot` にのみ依存し、DB やリモート呼び出しは一切トリガーしません。

**ステージその一: Prompt の強化**

```text
Original prompt (from snapshot)
    ↓ concatenate user long-term memory (memoryService.loadInjectionText)
    ↓ concatenate RAG retrieval results (ragRetriever.retrieve, mode=inject)
    = finalPrompt
```

両方の強化ステップは致命的ではありません。いずれかのステップが失敗しても、警告をログに出力して元のプロンプトにデグレードするだけで、リクエストをブロックすることはありません。ここには重要な設計があります——長期記憶と RAG の注入タイミングは、`refreshFromDb()` ではなく `createAgent()` の時点に選ばれています。なぜなら、これらは設定レベルの静的データではなく、リクエストレベルの `userId` と `ragQuery`(`RuntimeContext` から)に依存するからです。

**ステージその二: ReActAgent.Builder の組み立て**

```java
ReActAgent.builder()
    .name(agentName())                    // snapshot.agentName
    .sysPrompt(finalPrompt)               // ステージ1で強化されたプロンプト
    .model(getModel())                    // qwenModel Bean(Spring シングルトン)
    .generateOptions(buildGenerateOptions(snap))  // temperature/topP/maxTokens/modelName
    .maxIters(resolveMaxIters(snap.modelParams()))
    .toolkit(snap.toolkit())              // 事前構築済みの Toolkit(MCP + アプリツール + RAG ツール)
    .skillBox(snap.skillBox())            // 事前構築済みの SparkSkillBox
    .hook(hitlHook)                       // Human-In-The-Loop インターセプトフック
    .build();
```

`toolkit` と `skillBox` はどちらもスナップショットの内部ですでに構築済みのオブジェクトであることに注意してください。これがスナップショットのコアな価値です——時間のかかる MCP クライアント接続の確立、Tool の登録、Skill のロードをすべて `refreshFromDb()` の段階に前倒しし、`createAgent()` は参照の受け渡しのみを行うため、各リクエストの Agent の組み立てが真に純粋なインメモリ操作になります。

**ステージその三: パラメータオーバーライドのカスケード**

`temperature`、`topP`、`maxTokens`、`maxIters`、`enablePlan`、`modelName` という 6 つのパラメータはすべて同じ優先順位チェーンに従います。

```text
HTTP Header (X-Temperature, etc.) > DB JSON field (modelParams) > DEFAULT constant
```

これにより、同じ DB 設定でも、DB を変更したりリフレッシュをトリガーしたりすることなく、リクエスト単位の粒度でのパラメータの微調整が可能になります。

**3) スナップショットの不変な設計**

`AgentConfigSnapshot` は `final` クラスであり、すべての List フィールドは `Collections.unmodifiableList()` でラップされ、外部にはゲッターのみが公開され、セッターはありません。`BaseFinanceAgentFactory` は `volatile AgentConfigSnapshot configSnapshot` フィールドを保持しています。

- **書き込み**: `refreshFromDb()` 内で参照全体を置き換えるだけです(`this.configSnapshot = newSnapshot`)。部分的な変更は存在しません。
- **読み取り**: `createAgent()` の冒頭で、1 回の volatile 読み取りをローカル変数 `snap` に格納し、このローカル参照が以降のプロセス全体で使用されます。

これにより、フィールド横断的な一貫性が保証されます——同じ `createAgent()` 呼び出しの中で、prompt、toolkit、skillBox は必ず同じバージョンから来ています。prompt は新バージョンなのに toolkit はまだ旧バージョンだという中間状態は存在しません。

**4) FinanceAguiSessionManager の全体設計**

`createAgent()` の軽量レベルの作成設計に加えて、`saveAgent`、`removeSession`、`hasMemory` のカスタマイズも、financeAgent 全体の軽量性に大きく寄与しています。

- `hasMemory()` — 二重チェックの存在確認: まず `ac_agent_session` を、次に `ac_agent_block.maxSeq` をクエリし、会話履歴を完全にロードすることなく「履歴があるかどうか」を判定します。これは、再利用層が `extractLatestUserMessage` を経由するかどうかを判断する際の重要なフックです。
- `saveAgent()` — インクリメンタルな永続化: JdbcSession のブラックリストを通じて `memory_messages` の完全書き込みをスキップし、新しいブロック(`seq > dbMaxSeq`)のみを増分的に追記することで、書き込み増幅を O(N) から O(Δ) に削減します。
- `removeSession()` — カスケードクリーンアップ: 1 回のロック取得内で `ac_agent_session` + `ac_agent_block` のバッチ削除を完了し、同時に `tailSnapshot` キャッシュをクリーンアップします。

完全なライフサイクルのパフォーマンスプロファイル表は、各ステージ(`createAgent` → `hasMemory` → `onEnter` → `saveAgent` → `removeSession`)のレイテンシと DB 操作をまとめています。

これら 4 つのメソッドが合わさって、financeAgent の軽量なランタイムを構成しています。`createAgent` は軽量な作成を解決し(< 1ms、DB アクセスゼロ)、`hasMemory` は軽量な検知を解決し(インデックスヒット)、`saveAgent` は軽量な永続化を解決し(インクリメンタルな書き込み)、`removeSession` は軽量なクリーンアップを解決します(バッチ削除)。

##### 5.1.4.3 エンジニアリンググレードのヒューマンインザループ(SPI3)

HITL 自体には 2 つの形態が存在します。1 つはモデルレベル、もう 1 つはエンジニアリングレベルです。

両者の違い:

![モデルレベル HITL とエンジニアリングレベル HITL の違い](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j08KkDGf8azaoNelSXV7SYWDJicP4rsqbqbc51cyfaRJvRib2BSEBREB8icku29hJFHa8baTicgod0NPIcSz4wL48VeAwecbT3P0aQ/640?wx_fmt=png&from=appmsg)

一つの例:

![エンジニアリングレベル HITL の例](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j3JhIzwnibSEwKWjibPWnjwODtAiahVL3oAR7BEial9L0nH2JVM4gCS7PftUZRfDKmKBCHFy9zk1PsZRejmN1Z6ZnBvuawHeX3tzicY/640?wx_fmt=png&from=appmsg)

なぜエンジニアリングレベルの HITL を使うのでしょうか? モデルレベルの HITL を使うとどんな問題が起きるのでしょうか?

![モデルレベル HITL の問題点](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j3dhLI4TM1Dy4lZoDqVel9kNzo9D7af8VibjbpNkVswZOEmiastoSbhYq72giaXVm0HW0YGmtnFR8wib9zSgqeLD8GmSIJ9sWEhLQA/640?wx_fmt=png&from=appmsg)

モデルはエンジニアリングレベルの HITL について感知していないため、エンジニアリングレベルの HITL にはインターセプト操作が必要であることがわかります。インターセプトポイントは以下の通りです。

1. ユーザーが確認アクションを行っていることをどう確認するか?
2. ユーザーがツールを使う前後にインターセプト操作が必要かどうかをどう確認するか?
   a. インターセプトポイントが発見されたら、すべての Agent の動作をどう停止させるか! 確認結果を待ってから、以前のリンクを継続する。

幸いなことに、AgentScope の hooks システムは非常に強力です。

![AgentScope の hooks システム](https://mmbiz.qpic.cn/mmbiz_jpg/bvDbzNRia8j3JvZBkDfGvriaX6YEvybArD2xo992Frx8VT3D3gubqmv2ASmUbKsAvt1THovYldvo8wzDSxytIjLsQnMLPOPOGibQCtLHCJa8Dc/640?wx_fmt=jpeg&from=appmsg)

全体のリンクの様子:

![HITL 全体リンク](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j0qOJAiaRaRibbB4amWr7VFqrfT2KZ5eQia0nybxxdG6ypQSuPLxHBNsrNYFOdLCiaXcJBHHIBG0VeDbEHLY5VXTiankvJImXTsQzjY/640?wx_fmt=png&from=appmsg)

まとめると:

1. ユーザーが渡すメッセージのフォーマットは認識可能です——HITL 確認のフォーマットを取り決めるだけです。
2. PostReaningEvent はツール呼び出しの前にあり、エンジニアリングレベルの stopAgent を持ちます。
3. PostActingEvent はツール呼び出しの後にあり、エンジニアリングレベルの stopAgent を持ちます。

基本的な HITL 能力を解決したところで、具体的な優先度戦略と動作モードを定義する必要があります!

**HitlResolver の判定優先度**: before と after の両モードについて、どの HITL がヒットするかを HitlResolver が判定します。具体的な 5 層のインターセプトロジックは以下の通りです。

![HitlResolver の 5 層インターセプトロジック I](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j0IV57m3Yz9WfjUrkcOW8VoTENtcpX2EcPpdL1CUuJYTicmlqvlcfCiaNbg6SuMjPyCRaiaQgYYDKW2r54OvkVEFP95CxFMZ44nmg/640?wx_fmt=png&from=appmsg)

![HitlResolver の 5 層インターセプトロジック II](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j34V9sN9btJVPnRzjQsBBxBQzFRf7bIjRGVA5qogHoZxMVoyWluib7zFxBJvWBbO8tjT84OoyhV1hNVHO4mEiaa12yCO0R0pCrfI/640?wx_fmt=png&from=appmsg)

3 つの動作モードの詳細な説明:

**BEFORE(実行前のインターセプト)**

![BEFORE 実行前のインターセプト](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j00MlCnAyB2iaQf8JRTX3zvWWKsMSYia267cBJjjdLDq1JIxQznWBwon4x4A40u5bqaEXUShQGvpX892x84jt8uCICQBmIxIKw8Y/640?wx_fmt=png&from=appmsg)

**AFTER(実行後のレビュー)**

![AFTER 実行後のレビュー](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j33HEd6IHGfLVUnZeSGKHnGXOtfsrId5TF3RJTd3ka39ibaw35X5zRoX7b1KoIVMcNQAiaY7l100LJYbdiaTTE7PZfln3UQXyemp8/640?wx_fmt=png&from=appmsg)

**Resume(ユーザー確認後の再開)**

![Resume ユーザー確認後の再開](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j0cPvwOrQYibW8A861O7dnKx174bYzQhaSSszObIVz8W43oEyuftWVcocsparV38sXLOFicsoyN6ztb2VXibLIvOMaKvWbfI9YtIo/640?wx_fmt=png&from=appmsg)

##### 5.1.4.4 ContextInjectingMcpTool(SPI4)

AgentTool は AgentScope のツールレベルの SPI 拡張ポイントであり、カプセル化されたツール呼び出しロジックを定義できます。

```java
public interface AgentTool {
    String getName();
    String getDescription();
    Map<String, Object> getParameters();
    Mono<ToolResultBlock> callAsync(ToolCallParam param);
}
```

`ContextInjectingMcpTool` はこのインターフェースを実装し、デコレーターパターンでフレームワークのネイティブな `McpTool` をラップすることで、次の中核的な問題を解決します——**MCP Server の認証アイデンティティはビルド時に固定できず、呼び出しのたびに現在のリクエストユーザーに動的に紐づける必要がある**、というものです。

**1) 問題の背景**

AgentScope のネイティブな `McpTool` のアイデンティティは、すでに `AoneMcpClientBuilder.buildSync()` のビルド時(DB の `ac_mcp_server.user_id`)に確定してしまっており、すべてのユーザーが MCP Server を呼び出す際に同じアイデンティティを共有しています。これは金融シナリオでは容認できません——異なるユーザーが同じ MCP ツール(承認フォームの照会など)を呼び出す際、MCP Server は権限検証とデータ隔離を行うために、呼び出し元自身のアイデンティティを見る必要があります。

**2) 設計: 呼び出し時のアイデンティティ注入**

`ContextInjectingMcpTool` はツールの外部契約(`getName` / `getDescription` / `getParameters` はすべて委譲先にパススルーされます)を変更せず、`callAsync` の境界でのみアイデンティティ注入を完了します。

![呼び出し時のアイデンティティ注入設計](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j0B4SA0OcLHF0IiaoOfQ3HOQcBc6BuD7wuIhS0tOGNo4N2VT4aKdibcygxEluvSL8WfpeMjGqqrbykiaPjLDWwVBx0xY8aZ3iczz5Y/640?wx_fmt=png&from=appmsg)

重要な点: アイデンティティの伝播は ThreadLocal ではなく、Reactor Context(リアクティブでスレッドセーフなコンテキスト伝播メカニズム)を通じて行われます。これは、MCP 呼び出しチェーンが完全に非同期(`Mono` / `Flux`)であり、ThreadLocal はスレッドをまたぐスケジューリングの過程で失われてしまうためです。

**3) McpCallIdentity: 呼び出しレベルのアイデンティティキャリア**

```java
public final class McpCallIdentity {
    public static final String CONTEXT_KEY = "mcpCallIdentity";
    private final String empId;        // BUC 従業員 ID → Normandy bucUserId
    private final String ssoToken;     // BUC SSO トークン → Normandy bucSsoToken
    private final Map<String, String> extras;  // 予約済みの拡張スロット
}
```

散在したキーではなく不変な値オブジェクトとして設計した理由: アイデンティティのフィールドは今後増えていきます(empId → +ssoToken → 将来的には +sessionId / テナント ID の可能性も)。1 つのオブジェクトに集約しておけば、伝送チェーンの中間層(`ContextInjectingMcpTool` → `McpTransportContext`)は、フィールドが追加・削除されても修正が不要になります。変更が必要なのは、組み立て側(RuntimeContext の読み取り)と消費側(認証ヘッダーの生成)という両端だけです。

![McpCallIdentity の伝送チェーン](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j2CyV1QicIHtibeKYIkZnibS3SYRYiakA7rbqUVvYthS8I5ObwStL3ibicbtCcFKbyKrgYN6NoPG4CI2NwwO1pXCugiaSM2QEJOEgkOWM/640?wx_fmt=png&from=appmsg)

#### 5.1.5 7 つの設計思想のまとめ

1. 設定より規約(Convention over configuration)。
2. DB とアプリケーション層の同形性(変換層の排除)。
3. ドラフト/公開のデュアルトラック(修正が本番に影響しない)。
4. 楽観的ロックの全面採用(並行性の競合を防止)。
5. コンポーネントマーケットプレイス + バインディングの分離(再利用 + 独立した進化)。
6. 非同期タスクの分離(メインフローをブロックしない)。
7. インクリメンタルな永続化(書き込み負荷の軽減)。

### 5.2 要点その二: さまざまなエコシステムプラットフォームとの低コストな互換性

#### 5.2.1 SkillSourceLoader

現在のシステムは `SkillSourceLoader` という SPI 抽象化層を確立しており、Skill のソースの違いをインターフェース実装層にカプセル化しています。上位の `SkillRegistry` + `BaseFinanceAgentFactory` は下層のソースの詳細を完全に意識しません。これは、新しいエコシステムプラットフォームへの接続コストが「一つの `@Component` クラスを実装する」レベルに圧縮されることを意味します。

![SkillSourceLoader SPI 抽象化層](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j1FqXialZpBTml6Uhd2fic7WZu7ibHvdHq4896L7kRpicG4k7lDN7GENbPmcUbuL3SFXneIKSUmYb9pFPeBqoLsjiajpFDeLy4icNC90/640?wx_fmt=png&from=appmsg)

**主要なインターフェース契約**:

```java
public interface SkillSourceLoader {
    String skillSource();              // ソース識別子: "AONE" / "OSS" / "GITHUB" / ...
    AgentSkill load(SkillKey key);     // Skill をロードする(ダウンロード + パース)
    default int purgeLocalCache();     // すべてのローカルキャッシュをパージする
    default Path localCacheDir();      // ローカルキャッシュのルートディレクトリ
    default void purgeLocalCacheForKey(SkillKey key); // 単一のキャッシュをパージする
}
```

**SkillKey のトリプレット**: `(skillSource, skillName, skillVersion)` — ソースはファーストクラスの要素であり、複数ソースの共存を自然にサポートします。

##### 5.2.1.1 AoneSkillSourceLoader

![AoneSkillSourceLoader](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1xm0T81jLGbATQ9R53PbhaI8Ue9x3SBl63rsTic8bM2lADrpfxZjZUt4Tgl4L3AcWZs5ck0MmGVZ3ZDCoMAeVYeHLHsVvibXDfA/640?wx_fmt=png&from=appmsg)

**本番グレードの保証**:

- **強い制約層**: `ac_agent_skill` の各行は、`enabled=1` の `ac_skill` 行を指していなければならず、そうでなければ起動時に fail-fast します(`IllegalStateException`)。「設定はされているがロードできない」という潜在リスクを排除します。
- **弱い障害層**: Aone サービスが一時的に利用できない場合でも、Agent は起動を継続できます(そのスキルをスキップします)。バックグラウンドのスケジュールされたリトライで復旧を試みます。
- **バージョン管理**: `ac_skill.skill_version` はセマンティックバージョニングをサポートしています。Skill のアップグレードは、DB に 1 行追加し `ac_agent_skill` のバインディングを更新するだけで済みます。

##### 5.2.1.2 OssSkillSourceLoader

![OssSkillSourceLoader](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j0TTmI9j8qicebqVmnT9MMXJ1Sn6tTo6QVFbsrBMs0U5kXK19m8Oy0VJOoG0ngPQoyTgqibwR1CptYTeBscK2pDBwMYg8uhevpsQ/640?wx_fmt=png&from=appmsg)

**開発を加速する価値**:

- **承認不要のイテレーション**: 開発者が SKILL.md を修正 → zip にパッケージング → OSS にアップロード → DB に `ac_skill(source=OSS)` を 1 行追加 → Agent をリフレッシュ。このプロセス全体に Aone のリリース承認は不要です。
- **迅速なバージョン切り替え**: 同じ Skill は OSS 上で複数のバージョンを維持でき、開発者は `ac_agent_skill.skill_version` を修正するだけで数秒で切り替えられます。
- **ローカルデバッグに優しい**: OSS Skills のローカルキャッシュディレクトリは AONE から隔離されており、互いに干渉しません。

##### 5.2.1.3 新しいエコシステムプラットフォームを拡張するコストの分析

新しい Skill マーケットプレイスを接続する手順:

![新しい Skill マーケットプレイスを接続する手順](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j2hxTJDtcYyRHvNKEl9QYWE5ibXZtkJWeNXfFO6hertcLXkJ5AIyoyicv7QWgohQnDlV2reGicztE1bHscjmhsCwORPGuZGunsDJc/640?wx_fmt=png&from=appmsg)

**修正が不要な部分**:

- `SkillRegistry` — 新しい Loader を自動的に発見し、修正は不要です。
- `SkillKey` — すでにソースの次元を含んでおり、自然に互換性があります。
- `BaseFinanceAgentFactory.resolveSkills()` — `SkillKey` のみを気にし、ソースの実装は気にしません。
- `ac_agent_skill` / `ac_skill` のテーブル構造 — `skill_source` フィールドはすでに開かれた文字列です。
- フロントエンドの管理ページ — ドロップダウンに新しいソースの値を追加するだけです。

##### 5.2.1.4 標準化された Skill 駆動フロー

![標準化された Skill 駆動フロー](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j3t41WGiaCOA1A6Wk1omgZoZhtmYGPXxGJc8FwaZs7KS3tWXsjVTtej5SUe5Y6LjibKR949RWkRdmFfMNqPB270zfAibxIARaXrY8/640?wx_fmt=png&from=appmsg)

##### 5.2.1.5 Agent にバインドされた Skill の完全な駆動ライフサイクルをどう監視するか

**1) Skill ライフサイクルの全体像**

![Skill ライフサイクルの全体像](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j3eBaicnFz0Jr95Z1LPHkrDp2WvQjZUljpDkcmryAMryDd4PKXGP52eIT8ibyBuyJv4fKwC8TjzaFia7JuGQN54HSGsPtFjMQLWws/640?wx_fmt=png&from=appmsg)

**2) ac_agent_skill_task ステートマシンの詳細説明**

これは Skill ライフサイクルのコアな駆動エンジンです。現在実装されている状態遷移:

![ac_agent_skill_task の状態遷移](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j2wNKiapPOBNDxWZicZq0jCSH4yOabHWAGZiclrxSrdsGSvr0Ykkicg2FqIDEz3VwS6UgbR3sgricVibGEIRO9IFgyACG9oeLhFRib3cA/640?wx_fmt=png&from=appmsg)

段階的な 6 回のリトライ戦略:

![段階的な 6 回リトライ戦略](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j3bp29OYy6aiaHOib2YtIVxbJkCxQ2EicbmDzW93KXV2U9gOShuyjiblaaH7Ha7X3qXIzRqRYVshASnib4A58FBHgmwybB43kSAMPoc/640?wx_fmt=png&from=appmsg)

**スケジューラーのデュアルトラック体制**:

- 本番環境: `AgentSkillTaskScheduler`(SchedulerX2)、推奨 cron `0/20 * * * * ?`(20 秒ごとに 1 ラウンド)。
- 開発環境: `AgentSkillTaskLocalScheduler`(Spring `@Scheduled`)、`fixedDelay=20s`、手動での有効化が必要です。

#### 5.2.2 MCP

`McpClientFactory.build()` は一つのことしか行いません——DB の行をフィールドごとに再利用層フレームワークの `AoneMcpClientBuilder` にマッピングすることです。

```java
// McpClientFactory.java:69-101
AoneMcpClientBuilder builder = AoneMcpClientBuilder
    .create(server.getMcpServerName())
    .mcpId(server.getMcpServerId());
// 各 DB カラム → 一つの builder setter、すべて if-not-blank のマッピング
if (isNotBlank(server.getMcpServerType())) builder.type(parseEnum(AoneMcpType, ...));
if (isNotBlank(server.getAuthMode()))      builder.authMode(parseEnum(AoneMcpAuthMode, ...));
if (isNotBlank(server.getRegion()))        builder.region(parseEnum(AoneMcpRegion, ...));
// ... 全部で 8 個のフィールド
return builder.buildSync();  // transport/auth/URL の解決はすべてフレームワークに委ねる
```

**重要な点**: URL の解決、トランスポートの選択、認証の実装はすべて再利用層フレームワークの内部にあります。プロジェクトのコードは「DB → Builder」という「単純なマッピング」だけを行います。フレームワークが新しい `AoneMcpType` 列挙値を追加しても、こちら側のコード変更はゼロ行です——`parseEnum()` は汎用的なリフレクションであり、新しい列挙値を自動的に認識します。

#### 5.2.3 2 つの互換性戦略: Skill と MCP

![2 つの互換性戦略: Skill と MCP](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j0TvHQIPU3ztNZWSVTCuFk5jic77oCfRIuJUPZbl0Y5dkvGHlmmXd2AFCCvjSxGGoKzzlRM4Qo6GcnAIiaqWmSlVWM3sZv4Qrxhg/640?wx_fmt=png&from=appmsg)

**設計上のトレードオフは明確です**: Skill はプラットフォームごとの差異が大きく(リポジトリのプロトコル、ファイルフォーマット、バージョンモデルがすべて異なります)、そのため独自のプラグインシステムを構築せざるを得ません。一方 MCP プロトコル自体は標準化されており(MCP スキーマ + HTTP トランスポート)、差異は URL の解決と認証方式のみであり、これらはちょうどフレームワークがすでに解決済みの問題です。そこで私たちはこの部分をフレームワークに委ね、データ駆動の設定レイヤーだけを自分たちで行うことを選びました。

### 5.3 要点その三: Agent の効果の全体的な進化

#### 5.3.1 所感

2026 年は AI 爆発の元年と位置づけられており、Hermes の現象的な台頭は「進化」を業界のバズワードにしました。さまざまな技術記事がこぞって多様な「クローズドループ進化」戦略を分析していますが、業界の中で真の「進化」とは一体何なのか、その中核となる測定指標は何なのかを明確に定義できている人はほとんどいないようです。

私たちは本質に立ち返り、重要な命題を明確にする必要があります——「変化」と「進化」をどう区別するか? Hermes の Skill 作成戦略を例に取ると、厳密に言えばこれはむしろランダムな「変化」に近いものです。効果的な価値検証メカニズムを欠いているため、新しく生成された Skill が本当にシステムの能力を向上させているかを判断することは困難です——ポジティブなフィードバックのない変化は摂動としか呼べず、進化とは呼べません。

Hermes のソースコードの一部を閲覧し、関連資料を確認した結果、次のような結論に至りました。

![Hermes が達成した能力](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j22lfS6HV4lZrbrcmicNA86ibybE6kwAudClCfz8Nriau3PfdbdAkZcO2Aqdprk2dQ2IafdibarbDXvM1v7LUU1aeN1j47GOKENVtE/640?wx_fmt=png&from=appmsg)

そして Hermes が達成していないもの:

![Hermes が達成していない能力](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j2iblwZAXHib9qtibcWKEeLdyxEMZSicw6tFz5MUJRwg1mPpLWiaAiaicDt4LVHc7CuPuENrIeLPhicwxLEgib2icd8seSlhzricJpRE1PFeI/640?wx_fmt=png&from=appmsg)

Hermes の「進化」は本質的に「自己組織化された知識ガバナンス」です——エントロピーを制御すること(冗長性のマージ、古いコンテンツのアーカイブ、名前の重複排除)には長けていますが、変化が改善であることを証明するクローズドループの効果フィードバックがありません。Curator Prompt 自体が「使用回数カウンターを統合をスキップする理由として使ってはならない」と明示的に認めており、システムがテレメトリデータから Skill の質を判断できないことを自覚していることを示しています。これが意図的な設計上のトレードオフ(構造的なガバナンスを優先)なのか、埋めるべき能力のギャップなのかは、プロダクトの位置づけ次第です。

#### 5.3.2 進化をどう定義するか

自律的な Agent とその下層の Skill を構築する過程で、私たちはしばしばある誤解に陥ります——完璧で究極的なモデル状態が存在すると信じてしまうことです。しかし、Agent と Skill の進化の中核的な駆動力は、抽象的な自己改善ではなく、特定のシナリオに基づいた効果フィードバック、特に定量化可能な効果の比較です。

一つの中核的な認識を明確にしておく必要があります——「絶対的な進化」は疑似命題であり、エンジニアリングの実践において現実的かつ実行可能な命題は「相対的な進化」だけです。

**1) なぜ「絶対的な進化」は疑似命題なのか?**

オープンドメインの自然言語処理や複雑なタスク計画には、唯一の「模範解答」は存在しません。同じユーザーの意図に複数の有効な実行パスがある場合もあれば、同じコードに複数の同等な実装がある場合もあります。したがって、普遍的に適用可能な「完璧な Agent」を定義しようとすることは、理論的に実現不可能であるだけでなく、エンジニアリング的にも無意味です。

真の進化は、有限な境界内での比較優位の中で生じます。私たちは無限の現実世界を有限な評価セット(Benchmark)にマッピングする必要があります。高品質なテストケースを構築し、3 点、6 点、9 点といったきめ細かいスコアリングメカニズムを導入することによってのみ、曖昧な「良し悪し」を、追跡可能で最適化可能な「高低」に変換できます。

この相対的な進化の論理連鎖は以下の通りです。

- ベースラインの確立: 現行バージョンの評価セット上でパフォーマンスのベースラインを確立します。
- 差分比較: 新バージョンと旧バージョンの間、あるいは異なる戦略の間で、特定のメトリクスにおけるスコアの差を測ります。
- イテレーションの方向づけ: スコアのギャップに基づいて弱点を特定し、次のラウンドのパラメータ調整やロジックの最適化を駆動します。

**2) メトリクスの定義**

![進化メトリクスの定義 I](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j0jQgVMwiaz7Nhjw5qQlqm3Fq0vdgcn8hbSmxD86QORJFvSePLcy9MNqyqU67hqfVCcphV3GyObFSoLlRo4DXRtrIABTkNnl0icg/640?wx_fmt=png&from=appmsg)

![進化メトリクスの定義 II](https://mmbiz.qpic.cn/mmbiz_jpg/bvDbzNRia8j2dlVgchT5kRaia2NNEdGsEiayqicJLXuQt69PGNfj4CU3U12TeA1o1muILIIqfEuevCfPvqOaC4iaV7YLmNMibvmb7TI3I0SaPJheY/640?wx_fmt=jpeg&from=appmsg)

重みの設計:

![メトリクスの重み設計](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j3icJEPicichoQllq9aOGmdUia7f1tTyktiav8AvbpQM4iaASFGAL6QDTaIUYYYuEZLibZeIB2VfIoDaWZoZucCyZYhahrzOC3ubqSDNo/640?wx_fmt=png&from=appmsg)

**3) 評価の定義**

![評価の定義](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1InMIyfagxHALib3tB5s4bJvhF7M44OB2MkTNmQicHI7cDcSIvm8Q5q7PbRMNyh6Q5ib3CTsZfq0bkl8qTtyB5s7I6PDh4cF4HIA/640?wx_fmt=png&from=appmsg)

#### 5.3.3 進化のクローズドループという能力をどう発展させるか?

人手によるクローズドループ vs 自動クローズドループ

**1) 人手によるクローズドループ: 実行可能な最小限の進化システム**

自動化について議論する前に、まず一つの事実を認める必要があります——**人手によるクローズドループは自動クローズドループの原始的な形態ではなく、自動クローズドループのプロトタイプ検証なのです。**

人手によるクローズドループのフローは直感的です。

![人手によるクローズドループのフロー](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j1nWicialOJYu8l691eV9VJwIUdsGXRYFANzfolf2YxHqddLEiaKcs8ca2xkFaHQM8rEkOjCP8YRFU7JoK8Ry4t03L6jibKE2RGuSs/640?wx_fmt=png&from=appmsg)

このフローは原始的に見えますが、特定の段階においては代替不可能です。

**人手によるクローズドループの真の価値**

第一に、帰属分析のメンタルモデルを確立します。

システムの初期段階では、「失敗」がどのようなものかさえわかりません。ユーザーの苦情はルーティングの問題かもしれませんし、下層のデータソースの問題かもしれませんし、あるいはユーザー自身の不合理な期待かもしれません。こうした分類は、人が数百件のケースを手作業でレビューして初めて、安定した判断の枠組みを形成できます。

このステップを飛ばしていきなり自動化に進むのは、ラベル付きデータなしで分類器を訓練するようなものです——自分が何を最適化しているのかがわからなくなります。

第二に、介入が効果的かどうかを検証します。

初期段階における最大のリスクは「進化が遅すぎる」ことではなく「間違った方向への進化」です。人手によるクローズドループを使えば、Skill の説明を修正した後、ルーティングが本当に正確になったのかを直接感じ取れます。それとも、あなたが構築したテストケースだけがたまたま通過しただけなのでしょうか。

システムが未成熟な段階では、この直感はどんな自動評価よりも信頼できます。

第三に、「修正不能な」ケースを発見します。

一部の失敗は Skill の問題ではなく、システムアーキテクチャの問題、あるいはプロダクト定義の問題ですらあります。これらのケースは人手で特定し、上流の変更を推進する必要があります。自動クローズドループにはこの判断力が欠けています。

自動クローズドループが必要かどうかの判断:

![自動クローズドループが必要かどうかの判断](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j2ACKDucc2JseNXJ2YEYd6l6xtadqumJRwb6gmG9WFhBGl7MXRw3W7ojdPy0iaLibibshOmadd3kC6LMRHuxqvOTuiaviaNFuxGuGvg/640?wx_fmt=png&from=appmsg)

**2) 人手によるクローズドループのボトルネック: なぜ必然的に天井にぶつかるのか?**

人手によるクローズドループのボトルネックは「遅さ」ではありません——遅さは表面的なものにすぎません。実際のボトルネックは 3 つあります。

**ボトルネック 1: 評価の一貫性**

同じ bad case に対して、2 人の開発者が異なる帰属結論を出すことがあります。これは能力の問題ではなく、認知バイアスの問題です。

```text
Case: user asks "last month's supplier payment term distribution"
Developer A: attributes to routing problem — should go to financial-analysis-skill,
         but was routed to data-query-skill
Developer B: attributes to capability gap — financial-analysis-skill exists,
         but lacks the "payment term analysis" sub-capability
```

どちらの帰属も妥当ですが、介入の方向はまったく異なります。帰属が一貫していないと、以降のすべての最適化が互いに打ち消し合ってしまいます——今日 A がルーティングの説明を変更し、明日 B が Skill に新しい能力を追加すれば、システムの進化の方向はランダムウォークになってしまいます。

自動クローズドループはこの問題を「意見の相違を排除する」ことによって解決するのではなく、一組の評価基準を固定化することで、すべての意思決定を同じものさしの下で行えるようにすることで解決します。

**ボトルネック 2: 評価のカバレッジ**

人によるレビューはサンプルしかカバーできず、全量はカバーできません。システムが毎日数千件のリクエストを処理する場合、人によるレビューはわずか 5% しかカバーできないかもしれません。

この 5% には深刻な選択バイアスもあります——レビューされるのはしばしば「ユーザーが苦情を言った」ケースであり、「ユーザーは苦情を言わなかったが効果はいまいち」という大量のケースは無視されがちです。

自動クローズドループは全量のトラフィックを処理できます。ユーザーの苦情を必要とせず、事前に定義された評価指標に基づいて効果の悪いケースを積極的に発見できます。

**ボトルネック 3: フィードバックの遅延**

人手によるクローズドループのフィードバックループは日単位です——今日問題を発見し、明日分析し、明後日修正し、3 日後にリリースする、というものです。

しかし Skill の問題は時間単位で発生することがあります——下層のデータソースのスキーマ変更により、SQL 生成 Skill が生成するすべての SQL がエラーになる、といったケースです。人間が発見する頃には、すでに数百件のリクエストが影響を受けています。

自動クローズドループは、フィードバックの遅延を分単位に圧縮できます——評価は継続的に実行され、メトリクスがドリフトした瞬間にアラートと自動介入がトリガーされます。

**3) 自動クローズドループ**

![自動クローズドループの 3 層アーキテクチャ](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j0BjkaicCeicq7bu3icWtia8BD1TtvpSNTevbA2bR8K9xKzwKVGoDmNMCqe9T6kibXwZrjBUZicYZmyXsibXia6nib5uJB0OIySNWwhe5q8/640?wx_fmt=png&from=appmsg)

この 3 層の関係は次の通りです——データパイプラインが原材料を提供し、実行エンジンが計算力を提供し、戦略レイヤーが評価ロジックを提供します。いずれかの層が欠けていれば、自動クローズドループは機能しません。

**評価データパイプライン: Trace から評価アセットへ** — これは自動クローズドループの中で最も過小評価されがちですが、最も重要な部分です。

**L1: Trace: 構造化された実行記録**

Trace はログではありません。Trace は構造化された実行記録です。両者の違いは、ログは人が読むためのもので、Trace は機械が分析するためのものだということです。適格な Trace には以下が含まれていなければなりません。

![適格な Trace の構成要素](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1KLbHg4BCHcOo068FUb4jjYRiaxGE6ibM3lI652bXpHaIicSkhOwmqMyyrk1Ip2LiaBqHwzNt4B8hGHfhBnkCC9HvDmGqdZcBmDLc/640?wx_fmt=png&from=appmsg)

**Outcome Signals: 結果シグナル体系**

![Outcome Signals 結果シグナル体系](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j3Y1APJcuj1t3HMq1SJnp5LWy6gUsIlrO3vMZ9JMScs62eSTVpMaibpY6Qm2rG2lrzCmhQ9JscMRcHwpM4sibgcnUHjUBN3oG1jU/640?wx_fmt=png&from=appmsg)

これらの異種なシグナルを標準化された結果ラベルへ統一するデータパイプラインが必要です。

**スコアリングメカニズム**:

![スコアリングメカニズム](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j190uVc8hZdjOMibG5AzmS8ibjxQpgvwonNt7MUBvJcVdibgzk2V8ib2x8icYswQDsMBaHbI1H1fjGAEUq4AeHDVnJfztdOY1ZX6aUM/640?wx_fmt=png&from=appmsg)

**陥りやすい落とし穴**

**落とし穴 1: 評価データセットの偏り**

評価データセットが主にユーザーの苦情(明示的なフィードバック)から来ている場合、深刻なバイアスが生じます——「ユーザーが進んで苦情を言うシナリオ」しかカバーされません。「効果はいまいちだがユーザーはわざわざフィードバックしない」という大量のケースはデータセットに含まれません。

解決策: 能動的なサンプリング。Skill、意図タイプ、時間帯ごとの層化サンプリングを行い、評価データセットの分布が実際のトラフィック分布に近くなるようにします。

**落とし穴 2: LLM-as-judge の安定性**

LLM を使って LLM の回答を評価すると、スコアリングにランダム性が生じます。同じ回答が 2 回の評価で異なるスコアを受けることがあります。

解決策:

- (オープンエンドなスコアリングではなく)4 次元のルーブリックを使用し、各次元に明確な 1〜5 点の基準を設けて、スコアリングのランダム性を減らします。
- 複数回(最低 3 回)の評価を平均します。
- 定期的に人によるアノテーションでキャリブレーションを行い、自動スコアと人によるスコアのピアソン相関係数が 0.7 を超えることを保証します。
- パフォーマンス次元は完全に自動化されたメトリクスを使用し、LLM-as-judge を経由しないことで、不要なスコアリングノイズを避けます。

**落とし穴 3: 過剰最適化**

自動クローズドループは、システムが評価データセットに「過学習」する原因となることがあります。Skill は評価では非常に良い成績を収めますが、オンラインでの効果はいまいちです。

解決策:

- 評価データセットを定期的に更新します(毎月最低 20% を入れ替えます)。
- 最適化には決して使わない、検証専用の「隠された」評価セットを保持します。
- オンラインメトリクスと評価メトリクスを同時に監視し、両者が乖離した場合は直ちに調査します。

**落とし穴 4: 評価システム自体のコストを無視する**

評価は無料ではありません。評価のたびに LLM API 呼び出し、計算リソース、ストレージリソースを消費します。評価頻度が高すぎたり評価セットが大きすぎたりすると、コストが制御不能に膨らむ可能性があります。

解決策:

- まずはインクリメンタルな評価: 影響を受けた Skill のみを評価し、フル回帰は行いません。
- 階層化された評価セット: コアセット(毎回必ず実行)+ 拡張セット(低頻度で実行)。
- LLM-as-judge には小型モデルを使用し、不確実な場合のみ大型モデルにエスカレーションします。

**落とし穴 5: 4 次元の重みのキャリブレーション不足**

デフォルトの重み(関連性 0.30 / 正確性 0.35 / 完全性 0.20 / パフォーマンス 0.15)は出発点であって終着点ではありません。実際の重みは、異なるビジネスシナリオやユーザーグループによって大きく異なる可能性があります。長期間キャリブレーションを行わないと、合成スコアはユーザーの実際の体験から乖離してしまいます。

解決策:

- 人によるアノテーションの「総合満足度」スコアを収集し、4 次元の加重スコアとの回帰分析を行い、真の重みを逆算します。
- Skill のタイプごとに差別化された重みを維持します(例えばアラート型 Skill のパフォーマンス重みを 0.30 に引き上げるなど)。
- 四半期ごとに重みの設定をレビューし、ユーザーの認識との整合性を保証します。

### 5.4 要点その四: コンテキスト圧縮戦略と長期記憶戦略

#### 5.4.1 目的

一言で言えば目的は次の通りです。

- **コンテキスト圧縮**: 1 リクエストあたりの LLM コンテキストウィンドウが会話ターンの増加とともに無限に成長するのを防ぎます。コアなアイデア: **古い会話 → LLM による要約 → 元のブロックを置き換え、直近 N 個のブロックのみを完全に非圧縮のまま保持する**。
- **長期記憶**(ユーザーレベル): 「セッションをまたいでユーザーが誰で何を好むかを覚えておく」ことを管理します。

#### 5.4.2 圧縮

![コンテキスト圧縮の設計](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j2vnMbOdRVfG1IZzlZh8kBedvNM4yVH4dt6ibDmiaShNKUZu4BOqMk7lKRm0pQBEibqL1iakX03SFqRAs7ibFhTnYUZricBa2Gz5nEY0/640?wx_fmt=png&from=appmsg)

Block の `seq` = 最初の Msg の `timestamp(ms)` であり、厳密な単調増加と冪等性を保証します。

いつ開始されるのでしょうか? 答え: これも FinanceAguiSessionManager の一機能です。

![圧縮のトリガータイミング](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j2n14coOAFrbCtHMrvMjyPNvIianbIoh6ul14TanVgfaISptEHK3jyJOcZDuotXQiaIaRJJkAPNzUPQkfmC98ZCNrbvO2BxrwOPI/640?wx_fmt=png&from=appmsg)

圧縮のタイミングは createAgent の段階で発生し、同期的な検知が行われます——3 つの閾値のいずれかを超えるとトリガーされます。

![3 つの圧縮トリガー閾値](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j3UddfNeaYjgxknpsJxdpwyrMGicQvWw1UQPRrxPIYj5CpSdT2libbMMKPUEAUpR1zJ6ljuuIRcg5o1EKZqeT9mo8IayJGKJby8s/640?wx_fmt=png&from=appmsg)

**冪等性の保証**: `INSERT IGNORE` + UK `(agent_code, thread_id, status)` → 同時に存在できる PENDING は 1 スレッドにつき 1 つだけです。

完全な設計:

![完全なコンテキスト圧縮設計](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j0kHPFq8xA5dkIqibPWRfumX8wdDhTORWFaaibgeohTkMkkTheibBkoEvCFicTUh4CLrpgR45zCbSnUgWyvfjfnfmE6ZH517icDXOe4/640?wx_fmt=png&from=appmsg)

**主要な設計ポイント**:

1. **非同期実行**: 圧縮はリクエストパス上にはなく、ユーザーのレイテンシに影響を与えません。
2. **CAS による競合制御**: 複数ワーカーに対して並行安全です(`updateStatus(id, PENDING, RUNNING)` が 0 を返す = 他の誰かがすでに取得済み)。
3. **Session ロック内での実行**: onEnter/onExit とのブロックテーブルに対する並行操作を回避します。
4. **失敗時のリトライ**: 最大 5 回まで。上限を超えると、RUNNING + last_error のまま運用の介入を待ちます。
5. **SUMMARY の seq 競合保護**: `INSERT IGNORE` が 0 を返した場合は放棄し、重複したアーカイブを回避します。
6. **次回の `createAgent` ロード時**:
   a. `blockMapper.selectActiveAsc()` は `archived=0` のブロックのみを取得します
   b. 返されるもの: `[SUMMARY block] + [直近 5 つの NORMAL blocks]`
   c. SUMMARY block の `role=SYSTEM` であり、テキストは `[Conversation Summary]` から始まり、LLM がこれを履歴サマリーとして認識できます

#### 5.4.3 長期記憶戦略

会話からユーザーの嗜好と事実を自動的に抽出し、セッションをまたいで永続化し、リクエストごとにシステムプロンプトへ注入することで、Agent がリピートユーザーを「認識」できるようにします。

![長期記憶戦略](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1BBSfDvddCPUXdoxajgz2wTrKB0BF95qgCPuslZ6zG9eibqw6qqn9Jx2U195shP7gkfMrL4UicrkCnlGJ8Khnw6swuFzS5qyor0/640?wx_fmt=png&from=appmsg)

### 5.5 要点その五: 業務財務プラットフォームの管理コンソールとの統合、自由なページカスタマイズ

![業務財務プラットフォーム管理コンソールとの統合と自由なページカスタマイズ](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j3mp5I90ZPWtxPV9wEJicetbU5001Jk7yTZCEToibRJy51W9XQTiaJwz6TwQxkgmKxjW4iafcwBaLp6bicgwZ0u1t1IxRN8lVPu2ZIw/640?wx_fmt=png&from=appmsg)

### 5.6 要点その六: 全リンクのアイデンティティマーキングときめ細かな権限制御メカニズムの確立

権限システムを設計する際は、以下の側面に注力する必要があります。

- 権限検証ロジックを Agent の呼び出しチェーンにどう組み込むか。
- ユーザーのアイデンティティとコンテキストに基づくデータ隔離をどう実装するか。
- 動的で視覚的、監査可能な権限申請・承認フローをどうサポートするか。
- MCP/Skill レイヤーと下層のデータサービスの間で権限ポリシーの一貫性をどう確保するか。

この問題を解決することは、システムのセキュリティ、コンプライアンス、拡張性に直接影響するため、アーキテクチャの進化と同期して計画・実装される必要があります。

#### 5.6.1 既存の権限システムにおける多層防御の 4 つの側面の整理

![既存の権限システムにおける多層防御の 4 つの側面](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1icPhMKJKibmExib9icfMKe0GcryBg7T8qw1K4XznXbrn835mHia74yk4VPy2ZibwuxHLIlZUtSdFRKIEMRKxd8qOPkoRicVrvzl1MGw/640?wx_fmt=png&from=appmsg)

#### 5.6.2 全リンクのアイデンティティ統一

**現状**: financeAgent プロジェクト内のアイデンティティ伝播チェーンはほぼ接続されています——BUC SSO がエントリーレイヤーで認証を完了し、`empId` + `ssoToken` が HTTP Header を介して AG-UI の `RuntimeContext` へ橋渡しされ、MCP 呼び出しレイヤーは `McpCallIdentity` + Normandy SM2 署名(`ContextInjectingMcpTool` → `McpClientFactory.buildIsolated()`)を通じてリクエストごとのアイデンティティ隔離を実現しています。このチェーンはプロジェクト内で閉じています。

**問題**: しかし、呼び出しチェーンがこのプロジェクトの境界を越えると、アイデンティティコンテキストが途切れてしまいます。

![プロジェクト境界外でのアイデンティティコンテキストの断絶](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j0gaqCmKicm7EpMBSicuYJsO0JbaWNLH8VqdTYHGavgVqonhABb7pcFx2aOzmib1DicM2mSjtheMDQaZBWPfR0WyfRvzvyrt7xx6aw/640?wx_fmt=png&from=appmsg)

**計画**:

1. **HSF 呼び出しチェーンにおけるアイデンティティ伝播**: `HsfUtil` の汎用呼び出しレイヤーで EagleEye RpcContext を注入し、`empId` を呼び出し元コンテキストとして下流の HSF サービスに伝播させます。下流のサービスは、呼び出し元のアプリケーションだけでなくユーザーレベルの認証と監査を行えるようになります。まずは AMDP データクエリチェーン(現在すべてのユーザークエリが同じ `AuthParam` を共有しており、権限外クエリのリスクがある)のカバーを優先します。
2. **MCP アイデンティティ隔離の全面カバー**: 現在 `shouldIsolate()` は `NORMANDY_AUTH` + `AONE/ZETTA` タイプに対してのみ有効です。将来的にはすべての MCP Server タイプへ拡張し、リクエストごとのアイデンティティをサポートしない MCP Server については、Normandy Auth や OAuth2 トークン交換モードへの統合を推進し、共有トークンというセキュリティ上の露出面を段階的に排除します。
3. **統一されたアイデンティティコンテキスト(Identity Context)**: 現在の `McpCallIdentity` の MCP スコープを超えた `CallerIdentity` レイヤーを抽象化し、HSF / MCP / HTTP コールバック / MetaQ メッセージプロデューサーといったすべてのアウトバウンド呼び出しシナリオをカバーします。どのチャネルからリクエストが入り、どのチャネルから出ていくかにかかわらず、エンドユーザーのアイデンティティが常にトレース可能であることを保証します。

#### 5.6.3 カナリアシステムの構築

**現状**: カナリアルーティングのコアフレームワークはすでに構築済みです——`ac_agent_gray_config` テーブル + `GrayMatcher` の 3 次元マッチング(パーセンテージ / ホワイトリスト / 環境)+ `BaseFinanceAgentFactory.buildPublishedSnapshot()` のバージョン分割ロジックにより、カナリアリリース → 検証 → 全面展開(`promoteToStable`)という完全なライフサイクルをサポートしています。

**現在の不足点と進化の方向性**:

1. **パーセンテージルーティングのユーザースティッキネス**: `GrayMatcher.matchPercentage()` は現在、リクエストごとにランダム化するために `ThreadLocalRandom` を使用しています。同じユーザーがある回のリクエストではカナリアに、次のリクエストでは安定版にヒットする可能性があります。これはトラブルシューティングにもユーザー体験にも不親切です。`hash(workNo) % 100 < percentage` による決定論的なバケッティングに変更し、同じユーザーがカナリアサイクル内で常に同じバージョンにルーティングされることを保証します。
2. **カナリアのオブザーバビリティ**: 現在、カナリアのヒット結果はバージョンロードのロジックにのみ反映されており、明示的な計装やメトリクスを欠いています。必要なもの:
   - AG-UI レスポンスの Header に `X-Gray-Hit: true/false` と `X-Agent-Version` をマークし、フロントエンドでの認識とデバッグに使用します。
   - コアメトリクス(成功率、平均レイテンシ、ツール呼び出し失敗率)をカナリア/安定版のバージョン次元で分割し、Sunfire Dashboard に接続します。
   - カナリアのヒットログを構造化された形式で出力し、`empId` + `agentCode` + `version` による 3 次元検索をサポートします。
3. **多段階のカナリアオーケストレーション**: 現在のカナリアの粒度は単一の Agent レベルです。将来的には以下をサポートする必要があります。
   - Skill レベルのカナリア: 同じ Agent 内で、一部の Skill はカナリアバージョン(例えば新しいプロンプトテンプレート)を使い、他はオンラインバージョンを維持します。
   - MCP Server レベルのカナリア: 新バージョンの MCP Server はカナリアユーザーにのみ公開し、新しいツールの不安定さがすべてのユーザーに影響するのを防ぎます。
   - 組み合わせ戦略: ホワイトリスト + パーセンテージを重ねられるようにします(まずホワイトリストによる内部検証 → その後パーセンテージによる拡大)。現在この 3 つの戦略は互いに排他的です。
4. **自動的なカナリアの昇格とロールバック**:
   - カナリア段階の自動昇格条件を設定します——カナリアユーザー数が N 以上かつ成功率が閾値以上 → パーセンテージを自動拡大 → 全面展開。
   - 自動ロールバック条件を設定します——カナリアバージョンのエラー率が(安定版に対して X% 以上)急上昇 → カナリアトラフィックを自動的に安定版へ切り戻し、Sunfire アラートを発行。
   - 現在 `promoteToStable` は手動操作です。これをベースに自動化された意思決定レイヤーを追加する必要があります。
5. **カナリアと HITL の連携**: カナリアバージョンの新しいツール/Skill は、HITL(ヒューマンインザループ)の確認レベルを自動的に引き上げるべきです——カナリアトラフィックでのツール呼び出しはデフォルトで BEFORE モードの確認を経る一方、安定版は AFTER または確認なしにダウングレードでき、カナリアバージョンの影響範囲を縮小します。

#### 5.6.4 その他のセキュリティインフラ

![その他のセキュリティインフラ](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j1yFo9I3eNlmV6MicSfQ6EKk1GK9wTzaUOXdFZTfOC5sSvfKy9Y5ibO8IbOD2VSqLoyz5iaz6WUDFYU51Nrz9TKZPT9jgj9IvMRbw/640?wx_fmt=png&from=appmsg)

上記の計画は、コードの現状の実態(`GrayMatcher`、`McpClientFactory.buildIsolated()`、`HitlHook`、`UserUtils` など)に基づいており、各改善には明確なコードのエントリーポイントがあり、優先順位に応じて段階的に実装できます。

### 5.7 全リンクオブザーバビリティの実装

![全リンクオブザーバビリティの実装](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j2RYiabI55AylqPA6QW12s0l6Z3KL9WBhOeUtHibQxtdVicPL4f8Pia0ib4smWAPiaY7Dqo9giceIPg5264PMpibQe33tSQyqqkVFe8cCg/640?wx_fmt=png&from=appmsg)

## 06 最終的な全体フレームワークまとめ

![全体フレームワークまとめ](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j1Z2sPdjkXUOwibQL1cmqYzEWrMgic2B3cTbKQ1zOUFpnpFyv4ww63oKuW8mweOxq49qYNibrpjgnX0d9AUvsZSpxVqbZQelI5HoM/640?wx_fmt=png&from=appmsg)

