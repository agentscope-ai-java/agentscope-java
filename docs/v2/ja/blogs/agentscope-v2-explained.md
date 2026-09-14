---
hide-toc: true
---

# AgentScope 2.0 は本番運用可能に: エンタープライズグレード Harness 技術詳解!

<font style="color:rgb(44, 44, 43);">AgentScope Java 2.0 の背後にあるコアの考え方は、</font>`ReActAgent`<font style="color:rgb(44, 44, 43);"> の推論カーネルの上に </font>`Harness`<font style="color:rgb(44, 44, 43);"> エンジニアリング層を追加することである。開発者は軽量な ReAct ループを使い続けることも、Workspace、永続的な記憶、Session、Sandbox、Skill、Subagent の機能を選択的に有効化して、同じ Agent ロジックをエンタープライズグレードの分散サービスに落とし込むこともできる。</font>

五回の RC を経て、AgentScope Java 2.0 の GA リリースが正式に公開された:

+ ドキュメント: [https://java.agentscope.io](https://java.agentscope.io)
+ GitHub: [https://github.com/agentscope-ai/agentscope-java](https://github.com/agentscope-ai/agentscope-java)
+ リリースノート: [https://github.com/agentscope-ai/agentscope-java/releases/tag/v2.0.0](https://github.com/agentscope-ai/agentscope-java/releases/tag/v2.0.0)

> 本稿は、劉軍(Liu Jun)氏が2026年7月に行った AgentScope 2.0 に関する公開技術講演をまとめたもので、現地でのプレゼンテーションを忠実に再現している。
>

## <font style="color:#5e5e5e;">AgentScope 2.0 の紹介</font>
AgentScope は2年間存在している。今年の前半に 2.0 バージョンをリリースした。2.0 のコア機能は、Harness ソリューション全体をフレームワークに統合することである。これはまた、私たちが対象としているシナリオが主にエンタープライズグレードの分散 agent シナリオであることも意味する。

本稿は三部構成である。第一部は、誰もが気にしている 2.0 のコア機能をカバーする。企業内部を含む一部の開発者はすでに AgentScope 1.0 で多くの本番グレードのアプリケーションと agent を構築しているので、設計上の違いと移行方法を簡単に紹介する。中盤の最大のパートは、Harness のコア設計とそれが提供する機能を紹介する。最後に、いくつかの例を見て、それが実際のエンタープライズグレードのことをどうこなせるかを見ていく。

### <font style="color:#5e5e5e;">AgentScope エコシステム全景</font>
<!-- 这是一张图片，ocr 内容为：A2A QDRANT MEMO POSTGRESQL MYSQL.. TABLESTORE ORACLE EVENTBRIDGE A2UI REDIS MONGODB MILVUS SQL SERVER SQLITE OCEANBASE OSS AG-UI AGENTSCOPE-SAMPLES QWENPAW RESPONSE API CUSTOMIZABLE AGENTS TOOL & SKILL SAFETY BROWSER-USE DEEP RESEARCH DATA-JUICER AGENT HIGRESS AL GATEWAY MEMORY MULTI-AGENT HIGLAW EVO TRADER MORE... SMALL-LARGE MODEL COLLABORATION MANAGER-WORKERS ARCHITECTURE 米 SPARK DESIGN AGENTSCOPE 2.0 TS AGENT SERVICE DATA-JUICER CLAUDE SESSION MANAGEMENT USER AUTHENTICATION & AUTHORIZATION BACKGROUND TASK MANAGEMENT STORAGE/DB MANAGEMENT CRON JOB MANAGEMENT WORKSPACE POOL DEEPSEEK GEMINI OPENJUDGE AGENT ENGINE WORKSPACE EVENT SYSTEM GLM REASONING LOCAL FILE SYSTEM MESSAGE & EVENT DOCKER EVENT STREAMING PERMISSION SYSTEM TOOLKIT OPENAL ISREISIE HUMAN-IN-THE-LOOP CLOUD SANDBOX BATCH ACTING QWEN AGENT MIDDLEWARE MODEL AZURE ACTING REASONING TRINITY-RFT STRUCTURED  COMPRESSION RETRIES CHAT MODEL SYSTEM PROMPT TTS/REALTIME REPLY MODEL STUDIO FALLBACK CONTEXT OFFLOAD OLLAMA DOTONO LOONG SUITE HIGRESS ARMS LANGFUSE DOCKER LLM 目 SLS ROCKETMQ LANGSMITH PHOENIX LOPENTELEMETRY E2B ?SGL NACOS. -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122675006-d6f5a6c1-4239-447e-a1fb-92eb318317ac.png)

まず全体像から始めよう。AgentScope はフレームワークだと考えることができる。図の青い部分がそれにあたる。

フレームワークとして、私たちは現在 Python、Java、TypeScript での実装を持っており、Go の実装も開発中である。つまりフレームワークは基本的にすべての主要言語をカバーしている。

フレームワーク層は主に、agent がどう開発・定義されるかを定義する。例えば、中央には Agent Loop 全体があり、あなたが気にする必要のない、よく設計された Reasoning と Tool Call の実装がある。モデル、そして Event と Message の配信もすべて組み込まれている。

2.0 では、非常にコアな抽象として Workspace を追加し、より多くのコンテキスト管理も行った。これが青い中央部分のフレームワークである。

外側に広がると、agent 構築プロセスを取り巻く多くのエコシステム上の適応がある。モデル側では、左側の部分が国内の DeepSeek、OpenAI 互換モデル、Qwen モデルをカバーしており、すべてサポートされている。

可観測性については、フレームワークは現在デフォルトで OpenTelemetry のインストルメンテーションを持っているため、可観測性データは、オープンソースの LangFuse のような任意の OpenTelemetry 互換プラットフォームや、Alibaba Cloud の製品 — マイクロサービス時代のかつての ARMS、そして今では Agent Loop をターゲットとする Agent 時代専用の製品 — に報告でき、すべてプラグインできる。

そして Higress がある。Agent Teams について前述した通り、モデルのプロキシであれ MCP のプロキシであれ、Skill や MCP マーケットプレイス管理のための Nacos の利用を含めて、agent エコシステム全体が完全に統合されている。

さらに上では、QwenPaw と AgentTeams が、このフレームワークエコシステムから派生した具体的なプロダクトとエンタープライズグレードの agent 管理機能である。

### <font style="color:#5e5e5e;">ReActAgent カーネルとコアコンポーネント</font>
<!-- 这是一张图片，ocr 内容为：MODEL模型层 TOOL工具系统 CONTEXT 上下文 AGENT智能体 无状态引擎+AGENTSTATE 持久化 REACT推理-行动循环引擎 @TOOL 注解/TOOLBASE 继承 CREDENTIAL+CHATMODEL 两层架构 多用户/多会话并发安全 MCP 协议集成(STDIO/SSE/HTTP) 5大厂商:DASHSCOPE/OPENAL/ RUNTIMECONTEXT PER-CALL 元数据 ANTHROPIC/GEMINI/OLLAMA REDIS/MYSQL分布式状态共享 流式事件&结构化输出 SKILL 热加载MARKDOWN 指令集 STREAMING&THINKING&多模态 跨节点故障转移会话恢复 中断/恢复&人机交互 TOOL GROUP 按需激活/自管理 可扩展自定义PROVIDER MESSAGE & EVENT PERMISSION 权限 MIDDLEWARE中间件 5个生命周期HOOK位置 MSG类型化内容块体系 RULES +MODE +BUILT-IN CHECKS 洋葱式(ONION)+变换式(TRANSFORMER) 5种模式:DEFAULT/EXPLORE/BYPASS AGENTEVENT流式增量传输 /ACCEPT_EDITS/DONT ASK START DELTA END 生命周期 OPENTELEMETRY全链路追踪 建议规则自动生成&持久化 限速/回退/动态 PROMPT SSE推送&断点重建消息 危险路径不可绕过保护 -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122684082-53e4938d-8fbc-4d7a-af69-0538ce8955c6.png)



大枠の話の後、AgentScope フレームワーク自体に話を戻そう。最下層について、2.0 は変わっていない: ReAct Agent のコアとなる推論とツールのループは変わっていない。

ここにいくつかのコア機能をリストアップした。1.0 との違いは大きくなく、最下層の機能は変わっておらず、いくつかの設計上の最適化があるだけだ。あるいは下を見ると、Permission を追加した。これはツール呼び出しの権限制御のための追加設計であり、以前はツール権限管理の機能がなかったためだ。

中央には Middleware もあり、これは以前の Hook に相当する。新しいバージョンでは、イベント配信と中間介入を最適化した。全体として、Model、ツール定義、そして中央のコンテキスト(以前に私たちが作った内容)はほぼ似ている — これが ReAct Agent 部分の全体である。

### <font style="color:#5e5e5e;">1.0 -> 2.0 移行ガイド</font>
<!-- 这是一张图片，ocr 内容为：破坏性变更-必须改造 总体兼容-平滑升级 已废弃-V2.1将移除 核心API保持一致 状态管理架构重构 MEMORY 接口 INMEMORYMEMORY/LONGTERMMEMORY 等标记 REACTAGENT BUILDER 模式不变,MODEL/TOOLKIT/SYSPROMPT 等 无状态 AGENT 引擎:AGENTSTATE 按(USERLD,SESSIONLD)寻址, 参数平滑迁移 不再绑定单一AGENT实例 @DEPRECATED(FORREMOVALTRUE) 迁移到AGENTSTATE.GETCONTEXT()+AGENTSTATESTORE RUNTIMECONTEXT 替代旧调用模式 @TOOL注解完全兼容 已有@TOOL/@TOOLPARAM标注的工具类无需任何修改即可在 CALL()必须传入RUNTIMECONTEXT;PER-CALL元数据不再挂在 TOOLEXECUTIONCONTEXT 2.0中注册使用 AGENT实例上 标记@DEPRECATED;底层自动桥接到RUNTIMECONTEXT,老代 码暂不失效 AGENTSTATESTORE 强制配置 MCP客户端无缝衔接 分布式部署(SANDBOX)下必须配置REDIS/MYSQL等分布式状 MCPCLIENTBUILDER API 不变; STDIO/SSE/STREAMABLE HTTP  三 迁移到 RUNTIMECONTEXT 态后端,否则BUILD()抛异常 种连接方式保持兼容 TOOLCALLPARAM.GETCONTEXT() MIDDLEWARE注册方式变更 MODEL层PROVIDER兼容 已废弃 新增5层HOOK 体系;旧回调/拦截器需迁移为 DASHSCOPE/OPENAL/ANTHROPIC/GEMINI/OLLAMA各 迁移到GETRUNTIMECONTEXT() CHATMODEL BUILDER 接口不变 MIDDLEWAREBASE实现 IMAGEBLOCK/AUDIOBLOCK/VIDEOBLOCK 消息体系向后兼容 PERMISSION SYSTEM全新引入 仍兼容但新代码建议统--使用 DATABLOCK 工具执行前置权限检查为必选项;需配置 USERMESSAGE/ASSISTANTMESSAGE/SYSTEMMESSAGE 构造方式不 变 PERMISSIONCONTEXTSTATE(至少选择MODE) 迁移到DATABLOCK+MEDIA TYPE 旧INTERRUPT()无参重载 单 SESSION 场景仍有效, 多 SESSION 下行为不确定 迁移到 INTERRUPT(USERLD,SESSIONLD) -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122690675-75e21e37-f202-4cd0-ab71-c28649694fa7.png)

最下層のコアロジックは変わっていないが、それでも 1.0 から 2.0 への移行で注意すべき三つのポイントを挙げておく。左から右へ三つのレベルで見ていこう。

まず緑の部分だ。アップグレード中、私たちは全体的な互換性を保証している。つまり、これまで触れてきた機能はすべて概ね互換性がある。Hook が設計上 Middleware に置き換えられたように、一部の API が非推奨になったとしても、2.0 リリースでは引き続き非推奨としてマークしつつ維持している。だから理論上は、ほとんどの機能は互換性があり、スムーズにアップグレードできる。

中央の部分は、変更しなければならないものをリストしている。ほとんどの機能は互換性を保っているが、いくつかは変更された。この部分を変更しないと、コンパイルエラーやランタイムエラーが発生する可能性がある。これは主にいくつかの側面に現れる:

まず状態管理だ。フレームワーク全体に Agent State という概念を導入した。すべての Agent ランタイム状態は Agent State を通じて管理される。これは以前の Session とは基盤のデータフォーマットが異なるので、注意が必要だ。以前に稼働していた 1.0 の Agent State があった場合、互換レイヤーを提供している — 2.0 に切り替えて公開すれば、以前の 1.0 の状態も引き続き認識される。しかし、API から実装まで、状態管理が変わったことは知っておくべきだ。

もう一つの大きな変更: 私たちはマルチテナンシーを強く重視しているため — ユーザー次元の分離であれ Session 次元の分離であれ — Agent の `call` と `stream` メソッドのエントリポイントに Runtime Context という概念を追加した。つまり、どのユーザーでどの Session かといったランタイムコンテキストを渡さなければならない。同時に、拡張性も提供しており、その上に多くの拡張を構築できる。

以下の項目はすべて State と Session に強く関連している。

最後の部分は、段階的に移行できるものである。中央部分の破壊的な API 変更を修正すれば、残りは 2.1 バージョンで削除されるかもしれない非推奨項目なので、まず 2.0 に移行してから段階的にアップグレードしていける。

この部分はやや退屈なものだ。公式サイトに専用の移行リンクがあるので、そちらを確認してほしい。



## AgentScope Harness のコア設計と機能詳解
次に、今日の重要な部分に来た: AgentScope Harness 全体の設計だ。まず AgentScope 上の Harness の全体アーキテクチャを見てみよう。

### <font style="color:#5e5e5e;">Harness 全体アーキテクチャ</font>
<!-- 这是一张图片，ocr 内容为：在REACTAGENT之上.把长期运行AGENT必备的工程能力打包 应用用户请求 HARNESSAGENT 薄包装能力叠加在REAC循环的关键时机内部MIDDEWARE顺序固定 FILESYSTEM WORKSPACE MEMORY SKILLS COMPACTION SUBAGENTS 本机/KV/沙箱 上下文压缩 双层长期记忆 技能装配 人格/知识 子AGENT编排 PLAN MODE CHANNEL/GATEWAY PERMISSION 工具白名单 只读思考+HITL 会话路由SSE REACTAGENT.推理循环(CORE).HARNESS 不改写此层.只叠加钩子 共享对象(能力间零耦合,只通过这三者通信) WORKSPACE RUNTIMECONTEXT AGENTSTATESTORE AGENTS.MD`MEMORY`SKILLS 跨请求恢复运行时状态 USERLD-SESSIONLD.EXTRA -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122696150-b486a69e-f316-407b-b023-edf68b00e52f.png)



中央の大きな青い部分を見てほしい。Harness は、1.0 であれ現在のものであれ、AgentScope の基盤となる agent 推論・実行コンポーネントの上に構築されている。これは、1.0 の ReAct Agent の周りにもう一層巻かれたものと理解できる。

このレイヤーの上に、長期実行される agent にとって不可欠な機能 — コンテキスト管理、コンテキストの compaction、agent オーケストレーション、Skill の実行、サンドボックス環境での隔離されたツール実行、推論プランニングとタスク状態の追跡、さらには IM メッセージングシステムとの統合、ツール権限制御まで — を、フレームワークの最下層に組み込まれた Harness スイートとしてパッケージ化している。いくつかのスイッチで有効化することも、Harness の開発パターンに従うことでも使える。これは、このレイヤーの機能を追加するようなものだ。

### <font style="color:#5e5e5e;">Harness クイックツアー</font>
ここでは Java の例を使う。AgentScope Java で Harness レイヤーをどう使うのか? まず、依存関係を追加する必要がある。私たちはさらに一層追加したので、その層の依存関係を追加する必要がある。

<!-- 这是一张图片，ocr 内容为：<DEPENDENCY> <GROUPID>IO.AGENTSCOPE</GROUPID> </ARTIFACTID> <ARTIFACTID>AGENTSCOPE-HARNESS< <VERSION>${AGENTSCOPE.VERSION}</VERSION> </DEPENDENCY> -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122707767-503a3d8a-41c0-4b3e-a975-ffec45443c78.png)



次は開発のエントリポイントだ。ReActAgent の API エントリは引き続き存在するが、今は HarnessAgent という新しい API エントリがある。これを使って agent を直接構築できる — 内部では引き続き ReAct Agent を使っているが、API レベルでは直接 Harness Agent を使える。



両者の違いを見てみよう: 上の部分は同じだ — Name、System、Model。その下には Workspace の概念があるのがわかる。Workspace を指定でき、いくつかの compaction 戦略を指定でき、Sandbox の隔離設定を含む、より多くの設定を API 経由で直接行える。



下の部分の違いは、呼び出す際に前述した Context が必要になることだ。ここで Runtime Context が定義されており、呼び出す際は主に User とマルチテナント分離の情報を渡す。



<!-- 这是一张图片，ocr 内容为：PUBLIC CLASS FIRSTAGENT { PUBLIC STATIC VOID MAIN(STRING[] ARGS) { HARNESSAGENT.BUILDER() HARNESSAGENT AGENT NAME("NOTE-TAKER") "SYSPROMPT("你是一个帮助用户做笔记的助手.") 字符串形式由MODELREGISTRY 解析--自动读取 DASHSCOPE_API_KEY; 1/字符串 切换其他厂商时改用"OPENAI:GPT-5.5","ANTHROPIC:CLAUDE-SONNET-4-5", // "GEMINI:GEMINI-2.0-FLASH" 或"OLLAMA:LLAMA3". ,MODEL("DASHSCOPE:QWEN-PLUS") ,WORKSPACE(PATHS.GET(".AGENTSCOPE/WORKSPACE") COMPACTION(COMPACTIONCONFIG.BUILDER() .TRIGGERMESSAGES(30) KEEPMESSAGES(10) .BUILD()) . BUILD(); RUNTIMECONTEXT CTX - RUNTIMECONTEXT.BUILDER() SESSIONID("DEMO-SESSION") ,USERID("ALICE") .BUILD(); 当天的事 第一轮:自我介绍+ AGENT.CALL(NEW USERMESSAGE("我叫天宇,今天准备一个关于 REACT 的技术分享."), CTX),BLOCK(); 11第二轮:同 SESSIONID,自动恢复上一轮状态后回答 AGENT.CALL(NEW USERMESSAGE("我叫什么?我今天要干什么?"),CTX).BLOCK(); 子 -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122710684-609ea62c-3cd3-42ff-a07c-818ad562d0ca.png)



### <font style="color:#5e5e5e;">Workspace — Agent 進化の唯一の正</font>
<!-- 这是一张图片，ocr 内容为：智能体是什么]+学到了什么]都是文件 四条设计线索 内容按生命周期分三类 静态资产 工程师编辑 定义与进化都是文件 不散落代码,不绑数据库;一个目录拷走完整AGENT AGENTS.MD , KNOWLEDGE/,SKILLS/ LLS/ - SUBAGENTS/ . TOOLS.JSON 生命周期三分 2 运行时文件 静态/运行时/长期记忆走不同读写路径 框架/AGENT写 AGENTS/<ID>/SESSIONS/  AGENTS/<ID>/TASKS/ PLANS/ 原生多租户隔离 ISOLATIONSCOPE:SESSION/USER/AGENT/GLOBAL 长期记忆 AGENT+后台任务  MEMORY.MD . MEMORY/YYY-MM-DD.MD WORKSPACE 1 FILESYSTEM 同一一份目录布局+三种物理后端可切换 -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784125157045-a1a7a8e3-e9de-45f9-ae0f-5f2562316c75.png)



Workspace は、agent プロダクトであれ agent フレームワークであれ、今日の主流の agent における核となる設計である。これは論理的な概念だと理解できる。どんな資産を含んでいるのか?



最初の部分は静的な資産であり、`AGENTS.md`、Skills、あるいは Sub-Agent といった agent 定義に関連するものだ。これらは、このビジネス指向の agent にどんなものが存在するかを定義する。これらは私が定義し、自分のイメージと一緒にパッケージするものであり、静的な資産と呼ばれる。



もう一つの部分はランタイムデータだ。このデータは agent の実行中に生成され、ユーザーとのインタラクションを通じて蓄積される — リアルタイムの Session 状態の記録であれ、Task の状態情報であれ、`MEMORY.md` のような沈殿した記憶であれ。これらの静的またはランタイムの資産はすべて Workspace に定着する。これが Workspace のコアコンセプトだ。



### <font style="color:#5e5e5e;">抽象ファイルシステム — Workspace の物理的キャリア</font>
<!-- 这是一张图片，ocr 内容为：同一份逻辑目录,三种物理后端,AGENT代码零改动 WORKSPACE.逻辑目录布局 ABSTRACTFILESYSTEM 接口.WORKSPACEMANAGER 路由 多副本 隔离 默认 LOCAL+SHELL SANDBOX REMOTE KV /提供SHELL X不提供SHELL /容器内SHELL DOCKER ` E2B DAYTONA `AGENTRUN REDIS JDBC OSS NACOS OVERLAY.WORKSPACE +PROJECT 路径策略ROOTED/SANDBOXED 本机模板+远端覆盖(两层读) WORKSPACE PROJECTION (SHA-256 增量) 单进程本机开发信任环境 多副本共享MEMORY/SESSIONS 快照恢复PIP/NPM INSTALL 状态 管理台改文件下轮生效 生产跑不可信代码首选 快,简单,无外部依赖 -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784125168400-b13a9d3d-9d2f-420b-808f-61c1cea17803.png)



そして AgentScope では、私たちは Workspace をより細かい粒度で扱っている。例えば、一つの agent は一つの Workspace を持つが、一つの agent は多くのユーザーに使われる。異なるユーザーについて、私たちはこの Workspace 内で論理的なマルチテナント分離を行う — ユーザーレベル、Session レベル、あるいは agent レベルであってよい。これらは異なる分離の次元だ。

最下層では、Workspace は論理的な概念だ。その物理ストレージは何か? 最も直感的な理解はディスクであり、これが最も直接的だ。しかしディスクには問題がある: 例えば、オンプレミスのシナリオでは、それはあなたのローカルディスクにしか存在できない。これが、ディスクに縛られた Workspace の制約だ。

これを解決するために — 特に私たちがエンタープライズグレードの分散シナリオをターゲットにしていることから — 上位レイヤーの論理的な実装から下位の物理的な実装に移る際に、一つのインターフェースを抽象化した。中央の黒い部分、Abstract File System、抽象ファイルシステムインターフェースと呼ばれるものだ。

agent が Workspace を操作するとき、物理レイヤーはこの抽象ファイルシステムインターフェースを使う。私たちはそのために三つのデフォルト実装を提供しており、もちろん自由に拡張することもできる:

+ 一つ目は、このマシン上のローカルなオンプレミスで、ディスクを直接操作する。
+ ユーザー分離が欲しい場合、それはツリー型のファイルシステム、ツリー状の構造だ。
+ 本番デプロイでは、一つの agent が複数インスタンスにデプロイされるため、各インスタンスは同じ Workspace を見なければならない。この時点で、抽象ファイルシステムインターフェースを MySQL や Redis のようなデータベース、あるいは Alibaba Cloud OSS に接続できる。これにより Workspace の共有が実現する — 同じ Workspace インスタンスを異なる agent インスタンスが見ることができる。

Workspace により高い分離要件がある場合 — 例えば、ツール実行(ツール実行も Workspace のスペースで起こる) — それを Sandbox に接続できる。一つの Workspace は一つの Sandbox にマップされる。Sandbox のライフサイクルさえきちんと管理されていれば、マルチテナント分離が実現する。

これが、Harness における Workspace の論理的な概念と物理ストレージの実装であり、これによって分散シナリオをサポートしている。

### <font style="color:#5e5e5e;">組み込みのコンテキスト Compaction 戦略 — 四つの防衛線</font>
<!-- 这是一张图片，ocr 内容为：让对话保持在TOKEN预算内.同时不丢矣键信息 压缩流水线(按触发时机排布) TOOL 执行 OVERFLOW RECOVER RESULT EVICTION SUMMARY LLM TRUNCATE ARGS 真的撞墙极端压缩 产生工具结果 大参数字符串截断 单条>80K落盘 前缀结构化摘要 尾部保留原文 上下文留首尾+指针 零LLM 成本 自动重试一次 可调节的杠杆 压缩不会触碰的内容 触发阈值 PLAN MODE 状态 TRIGGERMESSAGES TRIGGERTOKENS AGENTSTATE.PLANMODECONTEXT 子AGENT后台任务 保留窗口 KEEPMESSAGES,KEEPTOKENS AGENTS/<ID>/TASKS/<SID>.JSON FLUSH 时机 TODO_WRITE 清单 ALWAYS NEVER,THROTTLED AGENTSTATE.TASKSCONTEXT 权限规则 独立小模型 COMPACTION.MODEL / MEMORY.MODEL AGENTSTATE.PERMISSIONCONTEXT 卸载排除 READ_FILE GREP_FILES 默认排除 永不压缩对话日志 SESSIONS/<SID>.LOG.JSONL -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784125116995-c234913e-e1b1-4dcb-8f53-e58380e9bbcb.png)



Workspace 内のすべてのコンテキストをどう管理するのか? まず、組み込みの compaction 戦略を提供する。Session の実行中、モデルにはコンテキストウィンドウの制限がある。コンテキストがその制限内に収まることをどう保証するのか?



ここではいくつかの compaction 戦略を提供しており、図はその一部しか示していない。実際にはより詳細な設定がある。例えば、ツール実行結果が一定のサイズを超えた後、私たちには切り詰めてオフロードする実装がある — ディスクにオフロードし、ファイル参照パスとして与える。ツールの入力パラメータが大きすぎる場合も、長さベースの切り詰め戦略がある。これらは基本的な措置だ。過去のメッセージを圧縮し、最新のものを保持することも含まれ、これはおなじみの日常的な compaction 戦略だ。



compaction の間にも、いくつか注意すべき点がある。最も典型的なのは: 圧縮する際にできるだけ情報を失わないようにすることだ。どんな情報を失ってはいけないのか?



右下の部分にいくつかの例がある。例えば、全体のタスク実行プランだ — いくつかの複雑なタスクにはプランニングが必要で、このプランは最初の数メッセージにあるかもしれない。特別な処理をしないと、直接の圧縮はそのプランを落としてしまう。



また、このプランに基づいて、いくつかのサブエージェントを生成しているかもしれない。その一部は非同期であったり、タスクが非同期であったりする。非同期の場合、タスクがまだ返ってきていないかもしれない — タスクの状態を継続的に追跡する必要がある。この時点で、私たちが乱暴に圧縮すると、サブエージェントの状態が失われるかもしれない。



したがって、このようなグローバルに更新される状態については — プランの詳細であれ、サブエージェントの非同期タスク状態であれ、私の TODO リストであれ、様々なツール権限の認可記録であれ — この情報は圧縮されないことが保証されなければならない。だからこの二つの部分は差別化された扱いが必要だ。



### <font style="color:#5e5e5e;">二層構造の長期記憶 — 事実が自動的に永続化される</font>
<!-- 这是一张图片，ocr 内容为：让AGENT记住跨会话的事实,同时避免上下文无限膨胀 第一层日流水账 对话MESSAGES FLUSH UM 当前上下文 MEMORY/YYY-MM-DD.MD  追加.未去重 每次 CALL 结束 CONSOLIDATION LLM 后台节流,最少30 MIN/次 每轮注入 SYSTEM PROMPT 第二层MEMORY.MD 合并去重策划后的长期记忆 <MEMORY_CONTEXT> AGENT主动查询工具 见到MEMORY.MD 截断提示时会自动调用 MEMORY SEARCH MEMORY_GET SESSION SEARCH 管线里的三次独立UM调用(定制入口) FLUSH CONSOLIDATION COMPACTION SUMMARY MEMORYCONFIG. FLUSHPROMPT COMPACTIONCONFIG.SUMMARYPROMPT MEMORYCONFIG.CONSOLIDATIONPROMPT 把对话前缀压成一条摘要(当下上下文) 从对话窗口抽取长期事实到日流水账 把每日流水账合并去重到MEMCRY.MD -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784124420246-a74faf0f-3b3e-4498-b058-4e15955eb0d8.png)



もう一つの機能は長期記憶の沈殿だ。前述の compaction はより一時的な状態に関するものだ — 実行時の瞬間的な状態を管理・制御する。圧縮する際、一部の情報は必然的に失われる。この情報は長期記憶として沈殿させることができる。



フレームワークの戦略は: Session の compaction の前に、Flush の整理を行うことができる。第一層は日次ファイルに記録される。QwenPaw に似た構造で、そこに記録できる。



同時に、その日のメモリ全体とメモリの沈殿を定期的にスキャンし、グローバルな `MEMORY.md` に蒸留するバックグラウンドタスクもある。この `MEMORY.md` は、すべてのリクエストの System Prompt にグローバルにロードされるため、そのサイズとデータ品質は非常に重要だ。



いくつかのメモリ管理ツールも提供されている: Memory Search、Memory Get、Session Search だ。モデルは `MEMORY.md` のガイダンスに基づいて、適切なタイミングであなたの台帳やその他の情報を照会する。これが協調された長期記憶沈殿戦略だ。



これらのステップすべて — 日次台帳のメモリ抽出であれ、`MEMORY.md` の定期的な蒸留であれ、compaction であれ — 内部のプロンプトはカスタマイズ可能であり、異なるシナリオがより良い抽出とメモリ実装を誘導するのに便利になっている。フレームワーク内の以下の項目はすべて Prompt の設定エントリポイントだ。

### <font style="color:#5e5e5e;">サブエージェントのオーケストレーション、委譲、並列処理、非同期通知</font>
<!-- 这是一张图片，ocr 内容为：把独立,上下文重,可并行的任务交给专家型子AGENT PARENT AGENT AGENT_SPAWN / AGENT_SEND 远程子AGENT 同步子AGENT 后台任务 暴露给用户 URL+HEADERS TIMEOUT0 TIMEOUT>0 EXPOSE TO USER-TRUE 用户可直连子AGENT 阻塞等结果流式转发 返回TASK_ID 反向通知 AGENT PROTOCOL HTTP SYSTEM-REMINDER 自动反向注入 关键能力 ISOLATED/SHARED PLAN MODE自动只读 流式事件带SOURCE路径 递归3层硬上限 DENY 权限自动继承 PERSISTSESSION 复用实例 WORKSPACE -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784125262501-e219f86e-b8c1-4b93-92c0-f8d8a51cf131.png)



Harness におけるもう一つ非常に重要なポイントは agent オーケストレーションだ。この図が表しているのは: 親 agent がすべてのサブエージェントを直接管理するということだ。



タスクが親 agent に入った後、私たちには Agent Fork や Agent Spawn のような組み込みツールがあり、親 agent は必要に応じてサブエージェントを生成する。生成されたサブエージェントには、まず二つのタイプがある: 同期サブエージェントと非同期サブエージェントだ。非同期サブエージェントは長時間実行のタスクに適しており、今では完了時に結果を親 agent に能動的に通知できるようになっている。



もう一つ特別なタイプはリモートサブエージェントで、これもサポートされており、リモートサブエージェントを生成できる。同時に、私たちは親 agent にすべてのサポートツールをカバーする Task List ツールキットを提供する。親 agent は、サポートツールを使って、どんなサブエージェントが存在し、各サブエージェントの状態がどうかを能動的にチェックできる。



言及する価値のあるもう一つのポイント、そして多くのエンタープライズユーザーのコアのニーズだが: 通常、ユーザーは親 agent と直接話をし、親 agent がサブエージェントを生成するのはそれ自身のビジネスであり、それはサブエージェントを管理してタスクを完了させる。しかし実際には、多くのユーザー(私たちが Claude Code を使うときを含む)は、親 agent が生成したサブエージェントに直接切り替えて、そのサブエージェントと話をし、サブタスクを通じてそれを導きたいと考えている。



AgentScope はサブエージェントと直接話すこともサポートしている。このサブエージェントは親 agent によって生成されるものだが、それを直接あなたと話せるように公開する方法がある。



サブエージェントの設計全体には、前述したより大きなレイヤーに加えて、詳細もある。例えば、親 agent とサブエージェントの間でコンテキストが共有されるかどうかは以下にリストされている。サブエージェントのイベントがどのように親 agent を通じて転送されるか、そしてあるイベントがサブエージェントからのものか親 agent からのものかをどう区別するか(みんなのイベントストリームは混ざり合っているため)を含めて、私たちにはマーカーがある。



そして権限の問題がある — 親 agent がサブエージェントを生成した後、サブエージェントの権限は何で、親 agent の権限を継承するかどうかだ。これらの詳細な事項について、フレームワーク全体にメカニズムが整備されている。

### <font style="color:#5e5e5e;">サンドボックス管理: 隔離、復元、分散</font>
<!-- 这是一张图片，ocr 内容为：把危险操作关进容器,把安装状态存进快照 CALL0生命周期沙箱决策 执行边界 01 文件与SHELL命令都在隔离容器里执行,宿主完全不参与 容器&WORKSPACE 还在吗? 跨调用恢复 02 有复用 PIPINSTALL NPMINSTALL 临时文件都随快照保留,下次 CALL 无需重装 重装 最快路径 无容器有快照恢复 多副本可用 快照重建 03 通过分布式STORE+远端快照,任意节点都能RESUME出同一份工 都没有冷启动 工作区 WORKSPACESPEC全量初始化 快照后端 后端矩阵DOCKER  KUBERNETES`DAYTONA -E2B AGENTRUN LOCAL  本地磁盘 REDIS低延迟 ISOLATIONSCAPE决定沙箱 LDT,复用(USER 黑 JDBC.关系库 OSS/S3多副本 串行化) 07 -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122744046-584de68f-e539-424a-8293-694603ad0ab6.png)

サンドボックスに関するメカニズムもある。サンドボックスは主に agent 実行のセキュリティ問題を解決するもので、これは非常に重要な部分だ。agent のツール実行中、ツール実行をサンドボックスに入れることができ、フレームワークにはサンドボックスのライフサイクル管理システムがある。ここでは詳しく展開しない。興味のある読者は公式ドキュメントを参照してほしい。

### <font style="color:#5e5e5e;">スキル: 四層レジストリとサンドボックス内での実行</font>
<!-- 这是一张图片，ocr 内容为：SKILL.MD+REFERENCES/+SCRIPTS/可复用的能力包 注册中心四层优先级(低高) 沙箱内执行三步透明化 高 物化STAGER USERIDYSKILLS/ 01 L4 市场SKILL从内存写到宿主SKILLS-CACHE 用户级隔离目录.覆盖共用版 SHA-256文件级去重 后发式恢复 CHMOD+X WORKSPACE/SKILLS/ L3 WORKSPACE PROJECTION 02 工作区共用项目特有约定 把 SKILLSKILLS-CACHE/打成 TAR HYDRATE 进沙箱/WORKSPACE 内容SHA-256整体比对增量 SKILLREPOSITORY(..) L2 市场后端:GIT/NACOS/MYSQL/CLASSPATH 容器内执行 03 <FILES-ROOT>用容器内绝对路径 AGENT不用猜路径 低 PROJECTGLOBALSKILLSDIR 快照保留PIP/NPM INSTALL 副作用 项目全局目录:~/.AGENTSCOPE/SKILLS/ -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122747931-aaa39b3a-95fc-40c1-b572-cc652a409fbc.png)



Skill について、取り上げる価値のある部分が二つある。



最初の部分は Skill 管理だ。Skill 管理のための AgentScope Harness は、Nacos のような集中管理された Skill 管理システムに接続し、ローカルでの認識と利用のために集中管理された Skill を自動的にロードする。同時に、前述した Workspace のきめ細かい管理メカニズムに基づいて、異なるユーザー間での Skill の分離も実装できる — このユーザーには自分の Skill があり、別のユーザーには別の Skill があり、互いに見えない。これも実現できる。



もう一つの部分は Skill 実行だ。Skill は単純なワークフローではないこともあり、補助スクリプトやリソースファイルも含むため、その実行はセキュリティ制御されなければならない。今では、Skill 全体を Sandbox に射影することをサポートしており、すべての Skill スクリプトが Sandbox 内でクローズドループで実行できるようにしている。



### <font style="color:#5e5e5e;">Plan Mode: よく考える -> 書き留める -> それから行動する</font>
<!-- 这是一张图片，ocr 内容为：只读思考阶段+计划文件+HITL退出 工作流(明确固化四步:设计划人确认人确认执行 执行阶段 PLAN WRITE 只读调查 用户请求 HITL确认 PLAN_ENTER PLAN_EXIT PLAN.MD 工具解禁 复用权限系统的ASK PLAN阶段的白名单 四种终态(别只看ISPLANMODEACTIVE) READ_FILEGREP_FILES GLOB_FILES 只读文件工具 末进入 模型直接BUILD,任务与WORKSPACE不匹配 LIST FILES MEMORY_SEARCH , MEMORY_GET 内存查询工具 SESSION_SEARCH 进入PLAN_EXIT 成功:规划完+获批+BUILD模式 PLAN 三件套 PLAN_ENTER PLAN_WRITE - PLAN_EXIT 仍PLAN有PLAN 已起草但未退出;后续消息可批准继续 任务清单 TODO WRITE 仍PLAN无PLAN 只说不做:文本像计划但没写出 SHELL(可选) ALLOWSHELLINP LANMODE()后放开为只读用途 -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122751350-9d17d85b-4f11-4a40-b87c-5e996beff223.png)



Harness は今、Plan Mode もサポートしている。1.0 に慣れているユーザーは、1.0 にも Plan モードがあったことを知っているだろう — タスクが与えられたら、まずプランを立ててから実行する。



1.0 の実装は、内部的なステートマシンにより近く、ステートマシンによって駆動されていた。2.0 では、Plan モード全体を最適化した: まず、PlanEnter、PlanExit などの Plan 関連の組み込みサポートツール一式だ。



ユーザーのリクエストが入ってくると、直接 Plan を有効にできる。Coding Agent に慣れているなら、これは Coding Agent の Plan モードと同じだと理解できる — 例えば Codex や Claude Code では Plan を開ける。AgentScope で開発されたビジネス agent も同様だ: Plan モードを有効にするよう伝えるインターフェースがあり、その後質問すると Plan を生成し、その後 Agent モードに切り替えて実行し、以前の Plan に基づいて直接実行する。



あるいは、自律的な認識モードに入らせることもでき、タスク自体に基づいて Plan モードに切り替えることができる。各ツールには Permission があるため、Plan モードに切り替えるときはまずあなたに尋ねるかもしれない。Plan が実行された後、Coding Agent と同様に、Agent モードに戻る際にウィンドウをポップアップして尋ねる。フロー全体は同じであり、フロントエンド UI 上でそれをつなげることができる。

### <font style="color:#5e5e5e;">Channel: メッセージングプラットフォーム -> Gateway -> Agent</font>
<!-- 这是一张图片，ocr 内容为：一行接线.自动完成会话管理并发路由.流式 GATEWAY路由与治理 消息平台 HARNESSAGENT实例池 HTTP/SSE GATEWAYBOOTSTRAP SALES MAIN WEBSOCKET 会话管理 USERLD映稳定SESSIONLD映射 钉钉 SUPPORT PER-SESSION 并发 HARNESSAGENT 同 SESSION 消息公平排队 飞书 AGENT 路由 企业微信 BILLING 多AGENT场景按AGENTID分发 HA RNESSAGENT GITHUB 子 AGENT 桥 EXPOSE.TO_USER 反向暴露给客户端 GITLAB REVIEWER 流式转发 EXPOSED SUBAGENT FLUX<AGENTEVENT>SSE直通 -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122754918-bd884356-94db-4615-b469-9ae38f00dab2.png)



<font style="color:#080808;background-color:#ffffff;">一部のエンタープライズのビジネスシナリオでは、Channel プラットフォームへの接続 — バックグラウンドタスクをエンタープライズ IM システムに接続する — が必要になる。フレームワークはこれをネイティブにサポートしている。</font>



## AgentScope エンタープライズ Agent の実践(公式サンプル)
<font style="color:#080808;background-color:#ffffff;">これで Harness の部分をカバーした。Harness の設計は比較的広範なので、具体的な使い方については公式ドキュメントを確認してほしい。最後に、いくつかの例を紹介しよう。</font>

### **<font style="color:#1f2937;">個人アシスタント — ローカル FS と Shell への直接アクセス、使うほどに成長する</font>**
<!-- 这是一张图片，ocr 内容为：定位 你的本机(SINGLE PROCESS  SINGLE NODE) 装在你自己电脑上的个人助手--以你的身份,在 你的FS/ SHELL 里干活. CHANNEL 适配器 (每个AGENT一个实例) HARNESSAGENT LLM推理循环 CHATUI (WEB UI) SKILLS-SUB-AGENTS-MCP 关键设计 单进程,无鉴权,无租户,无DOCKER SANDBOX;随 DINGTALK/WECOM/FEISHU 自进化闭环今WORKSPACE文件 随用随长--SKILLS/SUBAGENTS/MEMORY 都是自写 自写文件. GITHUB /GITLAB LOCALFILESYSTEMWITHSHELL 内置通道 一没有SANDBOX,没有租户命名空间,没有远端存储 直连宿主机 CHATUI(WEB UL,默认) 钉钉企微飞书 本机 SHELL BASH/ZSH 本机文件系统~/.AGENTSCOPE/... GITHUB/GITLAB WEBHOOK WORKSPACE目录(SELF-EVOLVING) 1人1节点 直连本机SHELL SELF-EVOLVING AGENTS.MD - SKILLS/ - SUBAGENTS/ - TOOLS.JSON - MEMORY/ - SESSIONS/ - KNOWLEDGE/ -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122784444-12b760c1-6fb4-4c21-b808-186230266ea5.png)

最初の例 — これらの例はすべて私たちの公式 GitHub リポジトリにある — は、AgentScope Java 向けの QwenPaw 風プロダクトだ。これは非常に簡略化された QwenPaw を実装したもので、具体的にリリースされたプロダクトではない。AgentScope で個人アシスタントプロダクトをどう開発するかを検証するために構築しただけだ。



前述の通り、その Workspace モードは完全にローカルディスクにバインドされているため、分散デプロイはサポートしていない。

### **<font style="color:#1f2937;">マルチテナント Managed Agent プラットフォーム — 一つの自己進化する Agent を組織全体で共有する</font>**
<!-- 这是一张图片，ocr 内容为：定位 SPRING BOOT:8080  REACT SPA+REST APL(JWT) 一个团队/一家公司共建,运营自进化AGENT的平台 台--从浏览器登录,不写代码搭AGENT. REST API(JWT 鉴权) REACT SPA HARNESSGATEWAY 每(USER,AGENTID) 独占一个 HARNESSAGENT 关键设计 每(USER,AGENT)独立WORKSPACE;三档共享:RUN/  AGENT (ALICE, AGENT-B)  AGENT (BOB, AGENT-A)  AGENT (ALICE, AGENT-A) /EDIT/FORK;一开关切换LOCAL.SANDBOX  REMOTE. COMPOSITEFILESYSTEM (PER(USERLD,AGENTLD)命名空间隔离) 三种FS-SPEC模式共用同一形状--只是底层存储引擎变了 SANDBOX 隔离粒度 SESSION`USER.AGENT`GLOBAL--由 LOCAL SANDBOX REMOTE BUILDER.SANDBOX.ISOLATION 控制. BASESTORE  REDIS/OSS 横向扩展 默认.本机FS+SHELL DOCKER? 展 SESSION/USER/AGENT/GLOBAL 共享分级 多租户JWT 可分布式 持久化,H2(开箱) MYSQL/POSTGRESQL(生产) 用户.AGENT定义共享授权 -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122793414-88f0d3ad-ba3b-4ab8-8f57-1902692e8c40.png)



もう一つの例はマルチテナント agent プラットフォームで、Agent Builder と呼ばれるノーコード agent 開発プラットフォームと理解できる。



UI インターフェースを持ち、企業内に集中デプロイできる。デプロイ後は SaaS プラットフォームになり、会社の誰もがその上で agent を作成できる。管理者として、私も会社の全員が使える共有 agent を作成できる。



各ユーザーが同じ agent を使っていても、下位レイヤーではマルチテナント分離を実現できる — 以下の Workspace と物理的な File System のグループ分けに依存している。これにより、各ユーザーのデータが分離されたマルチテナント agent プラットフォームが実現する。同時に、私は自分の agent を定義して他者と共有することもできる。



<font style="color:rgb(44, 44, 43);">これは本質的に、Claude Managed Agents、Langchain Managed Agents、Qoder Cloud Agents プラットフォームのプロトタイプだ。AgentScope 2.0 を使えば、コントロールプレーンとデータプレーンの API を公開するだけで、非常に素早く構築できる。</font>

### **<font style="color:#1f2937;">データ Agent プラットフォーム — ユーザーごとの進化 + 承認ベースの能力マーケットプレイス</font>**
<!-- 这是一张图片，ocr 内容为：定位 SPRING BOOT WEBFLUX :8080  分布式一等公民(REDIS 后端) 每位数据分析师一个专属SQL/图表/报表AGENT 越用越懂本组数据源与报表习惯. HARNESSGATEWAY UDA-{USERID}-{AGENTID} (PER-用户 FORK) DATA-AGENT (全局骨架 GLOBAL) 三项关键 OVERLAYFILESYSTEM (SKILLS/ SUBAGENTS/) 多人并行进化?互不干扰 能力市场--有闸门的知识流动 上层.PER-用户REMOTEFILESYSTEM (可写) SANDBOX  交由应用方治理 MEMORY/ , MEMORY.MD " SESSIONS/ . TASKS/ 下层:SHARED/{SKILLS,SUBAGENTS)(只读.审批后合入) KNOWLEDGE/ . AGENTS,MD 亦只读 通道&存储 CHATUI DINGTALK 通用WEBHOOK (HMAC) H2MYSQL/POSTGRESQL  REDIS分布式 (脚本执行.生命周期由应用方掌握) SANDBOXFILESYSTEM 容器规格,回收节奏.数据库驱动/NOTEBOOK工具链--由安全与运维口味决定 分布式 共享库 审批合入 能力市场用户贡献 SHARED/自下而上生长 -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122802713-c8373afe-1cc1-4083-a8a7-2865074a89c6.png)



最後に、あと二つの例だ。一つは Data Agent で、これもマルチテナントシナリオの例である。



この Data Agent は、各ユーザーにデータ基盤のための隔離されたスペースを提供する。各ユーザーは自分の Skill を持つこともできる。異なるユーザーによって沈殿した Skill には、このシステムに承認の仕組みがある — 私の Skill は共有を申請でき、承認プロセスを経て、通過すると共有され、すべてのユーザーが利用できるようになる。



### **<font style="color:#1f2937;">自律型 Coding ボット — スレッドルーティング + ワンショット Docker コンテナ</font>**
<!-- 这是一张图片，ocr 内容为：定位 ISSUEW.PRREVIEW.行内迭代.永不动本机/构建机FS 企业内可部署的自主编码机器人--ISSUE里留言就 给你开PR;PR 上加它当 REVIEWER 就有 REVIEW. GITHUBWEBHOOK.CLI.钉钉.飞书 所有触达通道 CHANNEL 适配器 两条安全底线 HMAC 校验,过滤自评 永不动宿主机FS--全在SANDBOX 内 SANDBOX 生命周期由框架自动管--按SESSION 拉 THREADLD FACTORY 起,复用,销毁 SHA-256 UUID GITHUB:ISSUE:OWNER/REPO#42 RUNDISPATCHER 立即派发THREAD忙时入队 MESSAGE/BUDGET/LIMIT HOOK 默认安全网 WEBHOOK 签名事件去重PER-SESSION 限流 HARNESSGATEWAY 模型预算上游限流透明重试 REVIEWER (REVIEW_REQUESTED) CODING(ISSUE/ PR 选代) SANDBOXFILESYSTEM . PER-THREAD DOCKER 容器 可横向扩展 GH/IM通道 PER-SESSION AGENTSCOPE/CODING-SANDBOX:LATEST .运行时托管  首次拉起  同 SESSION 复用 GITHUB API  目标仓库 -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784124780453-da758680-dccd-4e49-9eab-ec5529cb782a.png)

最後の例は Coding Agent だ。この Coding Agent は、Claude Code や Cursor のようなローカルにインストールされるツールとは異なる。



これは共有 agent シナリオのためのエンタープライズグレードのサービスだ。例えば、企業内でこの Coding Agent サービスをデプロイした後の典型的なシナリオは、GitLab に接続すること — デプロイされた Coding Agent サービスを GitLab に接続することだ。



誰もが GitLab で Issue や Pull Request のレビューを処理するとき、送信されたすべてのリクエストはこの Coding Agent サービスによって受け取られる。あなたのタスクのランタイムと他のユーザーのランタイム環境は分離されている — あなたのユーザー専用にサンドボックスを生成する。それは、あなたが処理するすべての Issue と Pull Request の状態の継続性を保証し、ユーザー間で相互の影響がないようにする。



各 Issue について継続的な会話をする場合も含めて、Issue 全体の状態が他の Issue や Pull Request と混ざったり、互いに影響を与えたりすることはない。したがって、これは企業内の R&D コラボレーションのためにデプロイされた Coding Agent の例だ。これを CI/CD プラットフォームとして構築し、AI で駆動することも可能だ。この下にある仕組み全体は AgentScope Harness の設計を使っている。



## AgentScope はエンタープライズで広く採用されている
<font style="color:rgb(44, 44, 43);">2024年のオープンソース化以来、AgentScope agent フレームワークは、特に分散した本番対応の agent シナリオにおいて、エンタープライズユーザーに広く採用される Agent Framework へと徐々に成長してきた。</font>

<font style="color:rgb(44, 44, 43);">アリババグループ内では、AgentScope(Java & Python)はすでに最も広く使われているフレームワークであり、Fliggy、淘宝闪购(Taobao Flash Purchase)、互鲸娱乐(Hujing Entertainment)、AIDC、Alibaba Holdings、淘宝交易(Taobao Trading)、淘宝手机(Taobao Mobile)、1688、Qwen APP、Amap、Alibaba Cloud、Ant International、Ant Global Payments などのビジネスラインをカバーしている。</font>

<font style="color:rgb(44, 44, 43);">オープンソースおよび Alibaba Cloud パブリッククラウドのユーザー側では、金融、輸送/物流、消費財小売、製造、エネルギー、ヘルスケア、教育・行政メディア、インターネット、SaaS、コンサルティングなど、多くの業界の主要企業に広く使われている。</font>



