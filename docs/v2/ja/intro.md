---
hide-toc: true
---

```{raw} html
<script>document.body.classList.add('agentscope-home');</script>

<div class="agentscope-landing">

<!-- ============================================================
     Hero
     ============================================================ -->
<div class="hs-hero">
  <div>
    <h1 class="hs-hero__headline"><span class="hs-hero__accent">分散型・エンタープライズグレード</span>のエージェントを構築する。</h1>
    <p class="hs-hero__desc">AgentScope Java 2.0 は、分散型・エンタープライズグレードのエージェントを構築するための本番運用対応フレームワークです。高度化するモデル性能に対応する本質的な抽象化と、長時間稼働・安全に制御されたエージェント実行への組み込みサポートを提供します。</p>
    <div class="hs-hero__actions">
      <a href="docs/quickstart.html" class="hs-btn hs-btn--primary">はじめる →</a>
      <a href="https://github.com/agentscope-ai/agentscope-java" class="hs-btn hs-btn--secondary">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.477 2 2 6.477 2 12c0 4.42 2.865 8.167 6.839 9.49.5.09.682-.217.682-.48 0-.237-.008-.866-.013-1.7-2.782.603-3.369-1.342-3.369-1.342-.454-1.155-1.11-1.462-1.11-1.462-.908-.62.069-.608.069-.608 1.003.07 1.531 1.03 1.531 1.03.892 1.529 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.11-4.555-4.943 0-1.091.39-1.984 1.029-2.683-.103-.253-.446-1.27.098-2.647 0 0 .84-.268 2.75 1.026A9.578 9.578 0 0112 6.836c.85.004 1.705.114 2.504.336 1.909-1.294 2.747-1.026 2.747-1.026.546 1.377.203 2.394.1 2.647.64.699 1.028 1.592 1.028 2.683 0 3.842-2.339 4.687-4.566 4.935.359.309.678.919.678 1.852 0 1.336-.012 2.415-.012 2.741 0 .267.18.577.688.48C19.138 20.163 22 16.418 22 12c0-5.523-4.477-10-10-10z"/></svg>
        GitHub
      </a>
    </div>
  </div>
  <div>
    <div class="hs-window">
      <div class="hs-window__bar">
        <div class="hs-window__dots">
          <div class="hs-window__dot hs-window__dot--r"></div>
          <div class="hs-window__dot hs-window__dot--y"></div>
          <div class="hs-window__dot hs-window__dot--g"></div>
        </div>
        <div class="hs-window__tabs">
          <div class="hs-tab active" data-panel="ja-harness">HarnessAgent</div>
        </div>
      </div>
      <div class="hs-code-panel" id="ja-harness"><pre><span class="kw">var</span> agent = <span class="ty">HarnessAgent</span>.builder()
    .name(<span class="str">"coder"</span>)
    .model(<span class="str">"dashscope:qwen-max"</span>)                                <span class="cm">// ModelRegistry 経由で解決され、DASHSCOPE_API_KEY を読み取る</span>
    .workspace(<span class="ty">Paths</span>.get(<span class="str">".agentscope/workspace"</span>))   <span class="cm">// AGENTS.md · MEMORY.md · skills · subagents</span>
    .filesystem(<span class="kw">new</span> <span class="ty">DockerFilesystemSpec</span>()           <span class="cm">// サンドボックス実行: ローカル · Docker · リモート KV を1行で切り替え</span>
        .isolationScope(<span class="ty">IsolationScope</span>.USER))           <span class="cm">// 同一ユーザーのセッション間で共有</span>
    .build();
agent.call(msg, <span class="ty">RuntimeContext</span>.builder()
    .sessionId(<span class="str">"demo"</span>).userId(<span class="str">"alice"</span>).build()).block();</pre></div>
      <div class="hs-install">
        <code>io.agentscope:agentscope-harness:${agentscope.version}</code>
        <button class="hs-copy-btn"
                data-copy="&lt;dependency&gt;&#10;    &lt;groupId&gt;io.agentscope&lt;/groupId&gt;&#10;    &lt;artifactId&gt;agentscope-harness&lt;/artifactId&gt;&#10;    &lt;version&gt;${agentscope.version}&lt;/version&gt;&#10;&lt;/dependency&gt;">Maven XML をコピー</button>
      </div>
    </div>
  </div>
</div>

<!-- Stats strip: adoption showcase -->
<div class="hs-adoption">
  <div class="hs-adoption__eyebrow">
    <span class="hs-adoption__eyebrow-dot"></span>本番環境で実証済み
  </div>
  <div class="hs-adoption__stats">
    <div class="hs-stat">
      <span class="hs-stat__val">アリババグループ</span>
      <span class="hs-stat__label">社内で最も広く使われているエージェントフレームワーク（Java & Python）。13以上の事業部門の本番環境で稼働</span>
    </div>
    <div class="hs-stat">
      <span class="hs-stat__val">オープンソースコミュニティ</span>
      <span class="hs-stat__label">オープンソースおよびAlibaba Cloud経由で10以上の業界のリーディングカンパニーに採用</span>
    </div>
  </div>
  <div class="hs-adoption__row">
    <span class="hs-adoption__row-label">アリババグループ</span>
    <div class="hs-marquee">
      <div class="hs-marquee__track">
        <span class="hs-marquee__group">
          <span class="hs-tag">Fliggy</span><span class="hs-tag">Taobao Instant Commerce</span><span class="hs-tag">Whale Entertainment</span><span class="hs-tag">AIDC</span><span class="hs-tag">Alibaba Holding</span><span class="hs-tag">Taotian Trade</span><span class="hs-tag">Taobao App</span><span class="hs-tag">1688</span><span class="hs-tag">Qwen App</span><span class="hs-tag">Amap</span><span class="hs-tag">Alibaba Cloud</span><span class="hs-tag">Ant International</span><span class="hs-tag">Ant Global Payments</span><span class="hs-tag hs-tag--more">…</span>
        </span>
        <span class="hs-marquee__group" aria-hidden="true">
          <span class="hs-tag">Fliggy</span><span class="hs-tag">Taobao Instant Commerce</span><span class="hs-tag">Whale Entertainment</span><span class="hs-tag">AIDC</span><span class="hs-tag">Alibaba Holding</span><span class="hs-tag">Taotian Trade</span><span class="hs-tag">Taobao App</span><span class="hs-tag">1688</span><span class="hs-tag">Qwen App</span><span class="hs-tag">Amap</span><span class="hs-tag">Alibaba Cloud</span><span class="hs-tag">Ant International</span><span class="hs-tag">Ant Global Payments</span><span class="hs-tag hs-tag--more">…</span>
        </span>
      </div>
    </div>
  </div>
  <div class="hs-adoption__row">
    <span class="hs-adoption__row-label">オープンソース · クラウド</span>
    <div class="hs-marquee hs-marquee--reverse">
      <div class="hs-marquee__track">
        <span class="hs-marquee__group">
          <span class="hs-tag">金融</span><span class="hs-tag">運輸・物流</span><span class="hs-tag">小売</span><span class="hs-tag">製造</span><span class="hs-tag">エネルギー</span><span class="hs-tag">ヘルスケア</span><span class="hs-tag">教育・行政メディア</span><span class="hs-tag">インターネット</span><span class="hs-tag">SaaS</span><span class="hs-tag">コンサルティング</span><span class="hs-tag hs-tag--more">その他多数の業界リーダー</span>
        </span>
        <span class="hs-marquee__group" aria-hidden="true">
          <span class="hs-tag">金融</span><span class="hs-tag">運輸・物流</span><span class="hs-tag">小売</span><span class="hs-tag">製造</span><span class="hs-tag">エネルギー</span><span class="hs-tag">ヘルスケア</span><span class="hs-tag">教育・行政メディア</span><span class="hs-tag">インターネット</span><span class="hs-tag">SaaS</span><span class="hs-tag">コンサルティング</span><span class="hs-tag hs-tag--more">その他多数の業界リーダー</span>
        </span>
      </div>
    </div>
  </div>
</div>

<!-- Feature 1: Harness -->
<div class="hs-section">
  <div class="hs-split">
    <div class="hs-split__text">
      <div class="hs-chip">Harness エンジニアリング</div>
      <h2>稼働し続けるエージェントのためのエンジニアリング基盤。</h2>
      <p>素の ReActAgent が解決するのは「1回の推論ターン」だけです。<code>HarnessAgent</code> は Middleware と Toolkit という2つの拡張チャネルを使い、ワークスペース、メモリ、サンドボックス、サブエージェント、スキル、Plan Mode を、長時間稼働するエージェントのための完全なインフラストラクチャとしてパッケージ化します。推論ループはそのまま維持され、harness はその上に積み重なるだけで置き換えることはありません。</p>
      <ul>
        <li><strong>アイデンティティが持続する</strong> — ワークスペースはエージェントのペルソナ + 長期記憶 + ドメイン知識であり、毎ターン再注入される</li>
        <li><strong>コンテキストが有界に保たれる</strong> — 自動コンパクション、大きなツール結果のオフロード、そして最後の手段としてのコンテキストオーバーフロー再試行</li>
        <li><strong>状態を復元できる</strong> — 同じ <code>sessionId</code> でプロセスをまたいで会話全体を再開でき、サンドボックスもスナップショットされる</li>
        <li><strong>能力が蓄積する</strong> — キュレーションゲート付きの4層 Skill 合成、宣言的なサブエージェントオーケストレーション</li>
      </ul>
      <a href="docs/harness/architecture.html" class="hs-btn hs-btn--secondary" style="margin-top:4px">Harness について学ぶ →</a>
    </div>
    <div class="hs-split__visual">
      <div class="hs-visual">
        <div class="hs-visual__bar">
          <div class="hs-visual__bar-dots">
            <div class="hs-window__dot hs-window__dot--r"></div>
            <div class="hs-window__dot hs-window__dot--y"></div>
            <div class="hs-window__dot hs-window__dot--g"></div>
          </div>
          <span class="hs-visual__bar-title">エージェントランタイムコア · モジュールマップ</span>
        </div>
        <img src="../../imgs/v2/as2-release-01.png" alt="AgentScope 2.0 のエージェントランタイムコア: Agent Service · Workspace · Middleware · Permission · Context · Model · Messages & Events" style="display:block;width:100%;height:auto;border:0"/>
      </div>
    </div>
  </div>
</div>

<!-- Feature 2: Events + Permissions -->
<div class="hs-section">
  <div class="hs-split hs-split--rev">
    <div class="hs-split__visual">
      <div class="hs-visual">
        <div class="hs-visual__bar">
          <div class="hs-visual__bar-dots">
            <div class="hs-window__dot hs-window__dot--r"></div>
            <div class="hs-window__dot hs-window__dot--y"></div>
            <div class="hs-window__dot hs-window__dot--g"></div>
          </div>
          <span class="hs-visual__bar-title">統一コンテンツブロック → イベントストリーム → ライブ UI</span>
        </div>
        <img src="../../imgs/v2/as2-release-03.jpg" alt="統一されたメッセージブロック（テキスト/ファイル/ツール結果/モデルの思考）がイベントストリーム（テキスト差分/ツール実行/ユーザー確認）として流れ、UIをリアルタイムに駆動する" style="display:block;width:100%;height:auto;border:0"/>
      </div>
    </div>
    <div class="hs-split__text">
      <div class="hs-chip">イベント · パーミッション</div>
      <h2>実行を可観測かつ中断可能にする。</h2>
      <p>メッセージは型付きの <code>ContentBlock</code> — テキスト、ファイル、画像、モデルの思考、ツール結果 — として流れます。単一の <code>call()</code> はもはや最終的なテキストを返すだけではなく、モデル呼び出し、テキスト差分、ツール呼び出し、ツール結果、ユーザー確認といった型付きイベントをストリーミングします。Human-in-the-loop とパーミッション承認は、フレームワークの一級市民です。</p>
      <ul>
        <li><strong>型付きイベント</strong> — <code>streamEvents()</code> がステップごとに発行し、フロントエンドでの手動での差分検出は不要</li>
        <li><strong>マルチモーダルメッセージ</strong> — <code>DataBlock</code> は base64 と URL の両方のデータソースを受け付ける</li>
        <li><strong>3状態のパーミッション</strong> — 静的ルール + ツールカテゴリ + 入力分析 → 許可 / 承認 / 拒否</li>
        <li><strong>外部実行ループ</strong> — ツールは外部システムによる完了を待って一時停止し、その後タスクを再開できる</li>
      </ul>
      <a href="docs/building-blocks/message-and-event.html" class="hs-btn hs-btn--secondary" style="margin-top:4px">イベントとパーミッションについて学ぶ →</a>
    </div>
  </div>
</div>

<!-- Feature Cards -->
<div class="hs-section">
  <div class="hs-section-hd">
    <h2>信頼できるエージェントシステムを構成するビルディングブロック</h2>
    <p>モデルのフォールトトレランスからサンドボックス実行まで、AgentScope Java 2.0 はエージェントを安定稼働させるために必要なすべてのエンジニアリングパーツを提供します。</p>
  </div>
  <div class="hs-cards">
    <a class="hs-card" href="docs/building-blocks/model.html">
      <svg class="hs-card__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182m0-4.991v4.99"/></svg>
      <h3>モデルのフォールトトレランス</h3>
      <p>モデル拡張モジュールを通じて、Qwen / OpenAI / Anthropic / Gemini / DeepSeek / Ollama にまたがる統一された Credential + ChatModel 抽象を提供。最大リトライ回数とフォールバックモデルを設定でき、プライマリが利用できないときにフレームワークが自動的に切り替えます。</p>
      <span class="hs-card__link">モデルについて学ぶ →</span>
    </a>
    <a class="hs-card" href="docs/harness/memory.html">
      <svg class="hs-card__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3.75 12h16.5M3.75 19.5h16.5M3.75 4.5h16.5M7.5 7.5a3 3 0 110-6 3 3 0 010 6zm9 0a3 3 0 110-6 3 3 0 010 6zm-9 13.5a3 3 0 110-6 3 3 0 010 6zm9 0a3 3 0 110-6 3 3 0 010 6z"/></svg>
      <h3>コンテキストエンジニアリング</h3>
      <p>構造化コンパクションが目標 / 状態 / 重要な発見 / 次のステップを保持。過大なツール結果はディスクにオフロードされ、コンテキストにはプレースホルダーのみが残る。ファイル IO は「編集前に読む」を強制し、冗長な読み取りを削減する。</p>
      <span class="hs-card__link">メモリについて学ぶ →</span>
    </a>
    <a class="hs-card" href="docs/building-blocks/middleware.html">
      <svg class="hs-card__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M6 6.878V6a2.25 2.25 0 012.25-2.25h7.5A2.25 2.25 0 0118 6v.878m-12 0c.235-.083.487-.128.75-.128h10.5c.263 0 .515.045.75.128m-12 0A2.25 2.25 0 004.5 9v.878m13.5-3A2.25 2.25 0 0119.5 9v.878m0 0a2.246 2.246 0 00-.75-.128H5.25c-.263 0-.515.045-.75.128m15 0A2.25 2.25 0 0121 12v6a2.25 2.25 0 01-2.25 2.25H5.25A2.25 2.25 0 013 18v-6c0-.98.626-1.813 1.5-2.122"/></svg>
      <h3>Middleware</h3>
      <p>4つのオニオン型フック（<code>onAgent / onReasoning / onActing / onModelCall</code>）と <code>onSystemPrompt</code> トランスフォーマー。ロギング、トレーシング、パーミッションチェック、コンテキスト注入、ビジネスポリシーを、コアをフォークすることなく差し込めます。</p>
      <span class="hs-card__link">Middleware について学ぶ →</span>
    </a>
    <a class="hs-card" href="docs/harness/workspace.html">
      <svg class="hs-card__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 7.5l-9-5.25L3 7.5m18 0l-9 5.25m9-5.25v9l-9 5.25M3 7.5l9 5.25M3 7.5v9l9 5.25m0-9v9"/></svg>
      <h3>Workspace 抽象</h3>
      <p>「エージェントが何をするか」と「どこで実行するか」を分離します。WorkspaceBase はアイデンティティ、ライフサイクル、リソース検出、コンテキストオフロードを統一。ローカルディスク、Docker、E2B クラウドサンドボックスを1行で切り替えられ、組み込みのウォームプールは RL ロールアウトに適合します。</p>
      <span class="hs-card__link">Workspace について学ぶ →</span>
    </a>
    <a class="hs-card" href="docs/harness/subagent.html">
      <svg class="hs-card__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M18 18.72a9.094 9.094 0 003.741-.479 3 3 0 00-4.682-2.72m.94 3.198l.001.031c0 .225-.012.447-.037.666A11.944 11.944 0 0112 21c-2.17 0-4.207-.576-5.963-1.584A6.062 6.062 0 016 18.719m12 0a5.971 5.971 0 00-.941-3.197m0 0A5.995 5.995 0 0012 12.75a5.995 5.995 0 00-5.058 2.772m0 0a3 3 0 00-4.681 2.72 8.986 8.986 0 003.74.477m.94-3.197a5.971 5.971 0 00-.94 3.197M15 6.75a3 3 0 11-6 0 3 3 0 016 0zm6 3a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0zm-13.5 0a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0z"/></svg>
      <h3>マルチエージェント</h3>
      <p>Markdown でサブエージェントの仕様を宣言し、親エージェントが必要に応じて <code>agent_spawn</code> / <code>agent_send</code> で同期または背後モードで起動します。バックグラウンドタスクの完了は <code>system-reminder</code> 経由でプッシュバックされ、ポーリングは不要です。</p>
      <span class="hs-card__link">マルチエージェントについて学ぶ →</span>
    </a>
    <a class="hs-card" href="docs/building-blocks/tool.html">
      <svg class="hs-card__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M11.42 15.17L17.25 21A2.652 2.652 0 0021 17.25l-5.877-5.877M11.42 15.17l2.496-3.03c.317-.384.74-.626 1.208-.766M11.42 15.17l-4.655 5.653a2.548 2.548 0 11-3.586-3.586l6.837-5.63m5.108-.233c.55-.164 1.163-.188 1.743-.14a4.5 4.5 0 004.486-6.336l-3.276 3.277a3.004 3.004 0 01-2.25-2.25l3.276-3.276a4.5 4.5 0 00-6.336 4.486c.091 1.076-.071 2.264-.904 2.95l-.102.085m-1.745 1.437L5.909 7.5H4.5L2.25 3.75l1.5-1.5L7.5 4.5v1.409l4.26 4.26m-1.745 1.437l1.745-1.437m6.615 8.206L15.75 15.75M4.867 19.125h.008v.008h-.008v-.008z"/></svg>
      <h3>ツール & MCP</h3>
      <p>アノテーション駆動のツール登録と、ツールの属性に基づくバッチ / 順次 / 並行実行の自動振り分け。あらゆる MCP 互換サーバー（ファイルシステム、データベース、ブラウザ、コードインタプリタ）を、中央集約された <code>workspace/tools.json</code> の許可リストで差し込めます。</p>
      <span class="hs-card__link">ツールについて学ぶ →</span>
    </a>
  </div>
</div>

<!-- CTA -->
<div class="hs-cta">
  <h2>構築を始める準備はできましたか？</h2>
  <p>クイックスタートに沿って、数分で ReActAgent を動かしてみましょう。長時間稼働のためのエンジニアリング層が必要になったら <code>HarnessAgent</code> に切り替えてください — 推論コアは同じまま、必要な能力だけを積み重ね、ビジネスコードには手を触れません。</p>
  <a href="docs/quickstart.html" class="hs-btn hs-btn--primary">構築を始める →</a>
</div>

<!-- FAQ -->
<div class="hs-faq">
  <div class="hs-faq__hd">
    <h2>よくある質問</h2>
    <p>完全な Q&amp;A は <a href="docs/others/faq.html" style="color:var(--hs-accent)">FAQ</a> を、あるいは <a href="https://github.com/agentscope-ai/agentscope-java/discussions" style="color:var(--hs-accent)">GitHub Discussions</a> でご質問ください。</p>
  </div>
  <details class="hs-faq-item">
    <summary>必要な Java のバージョンは？</summary>
    <p><code>JDK 17</code> 以降です。フレームワークは Records、Sealed Classes などの最新機能に依存し、Project Reactor によるノンブロッキングなリアクティブ実行モデル上で動作します。極めて低いコールドスタートレイテンシが必要な場合は、Quarkus 経由で GraalVM ネイティブイメージをビルドできます。</p>
  </details>
  <details class="hs-faq-item">
    <summary>どの LLM プロバイダーがサポートされていますか？</summary>
    <p>モデル拡張モジュールを通じてサポートされます: OpenAI（vLLM、DeepSeek、Kimi、Moonshot を含む OpenAI 互換エンドポイント）、Anthropic Claude、DashScope 経由の Alibaba Qwen、Google Gemini、xAI Grok、そしてローカルの Ollama。それぞれが統一されたビルダーの背後にある専用の <code>ChatModel</code> 実装です。リトライとフォールバックモデルはモデル層で設定でき、グレースフルなフェイルオーバーが可能です。</p>
  </details>
  <details class="hs-faq-item">
    <summary>Harness は素の ReActAgent とどう違いますか？</summary>
    <p><code>ReActAgent</code> は「推論 → ツール → 応答」というコアループです。<code>HarnessAgent</code> は同じコアの上に、Middleware と Toolkit を通じてワークスペース、メモリ、コンパクション、サブエージェント、サンドボックス、Plan Mode、スキルを積み重ねます。<code>ReActAgent</code> から始めて、長時間稼働の安定性が必要になったら、ビジネスロジックに触れることなく <code>HarnessAgent</code> に移行できます。</p>
  </details>
  <details class="hs-faq-item">
    <summary>2.0 は 1.0 と後方互換性がありますか？</summary>
    <p>AgentScope Java 2.0 は、ほとんどのユーザーがスムーズにアップグレードできるよう、可能な限り 1.x との互換性を維持することを目指しています。とはいえ、2.0 は API レベルの破壊的変更（型付きイベント、パーミッションシステム、Middleware スタック、Workspace 抽象など)を導入しています。詳細は <a href="docs/change-log.html">V1 移行ガイド</a> を参照してください。</p>
  </details>
  <details class="hs-faq-item">
    <summary>Spring Boot や Quarkus と組み合わせて使えますか？</summary>
    <p>はい。コアモジュールはフレームワークに依存しない Java ライブラリであり、Spring Boot、Quarkus、Micronaut、あるいは任意の JVM アプリケーションにそのまま組み込めます。Quarkus では GraalVM ネイティブイメージをコンパイルして、100ms 未満のコールドスタートを実現することもできます。</p>
  </details>
  <details class="hs-faq-item">
    <summary>本番環境でどのように水平スケールしますか？</summary>
    <p>AgentScope Java はステートレスな水平スケーリングのために構築されています。エージェントの状態は <code>AgentStateStore</code>（デフォルトはローカルの <code>JsonFileAgentStateStore</code>。マルチレプリカでは <code>RedisAgentStateStore</code> に切り替え）によって永続化され、<code>(userId, sessionId)</code> でアドレス指定されます。ワークスペースはリモートの KV / オブジェクトストアにマウントでき、サンドボックスモードでは実行環境そのものすら呼び出しをまたいで再開されます。Kubernetes と HPA を組み合わせれば、どのレプリカでも任意のユーザーの完全なコンテキストを引き継げます。</p>
  </details>
</div>

</div><!-- .agentscope-landing -->

<script>
(function () {
  document.addEventListener('click', function (e) {
    var tab = e.target.closest('.hs-tab');
    if (!tab) return;
    var win = tab.closest('.hs-window');
    if (!win) return;
    var panelId = tab.getAttribute('data-panel');
    win.querySelectorAll('.hs-tab').forEach(function (t) { t.classList.remove('active'); });
    win.querySelectorAll('.hs-code-panel').forEach(function (p) { p.style.display = 'none'; });
    tab.classList.add('active');
    var panel = document.getElementById(panelId);
    if (panel) panel.style.display = 'block';
  });
  document.querySelectorAll('.hs-copy-btn').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var text = btn.getAttribute('data-copy');
      if (!text || !navigator.clipboard) return;
      navigator.clipboard.writeText(text).then(function () {
        var orig = btn.textContent;
        btn.textContent = '✓ コピーしました';
        setTimeout(function () { btn.textContent = orig; }, 1800);
      });
    });
  });
})();
</script>
```
