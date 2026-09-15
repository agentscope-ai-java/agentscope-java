---
hide-toc: true
---

# AgentScope 2.0 프로덕션 레디: 엔터프라이즈급 Harness 기술 심층 해부!

<font style="color:rgb(44, 44, 43);">AgentScope Java 2.0의 핵심 아이디어는 </font>`ReActAgent`<font style="color:rgb(44, 44, 43);"> 추론 커널 위에 </font>`Harness`<font style="color:rgb(44, 44, 43);"> 엔지니어링 레이어를 추가하는 것이다. 개발자는 가벼운 ReAct 루프를 계속 사용할 수도 있고, 선택적으로 Workspace, 영속 메모리, Session, Sandbox, Skill, Subagent 기능을 활성화하여 동일한 Agent 로직을 엔터프라이즈급 분산 서비스로 구현할 수도 있다.</font>

5차례의 RC 이터레이션을 거쳐, AgentScope Java 2.0 GA 릴리스가 공식적으로 공개되었다.

+ 문서: [https://java.agentscope.io](https://java.agentscope.io)
+ GitHub: [https://github.com/agentscope-ai/agentscope-java](https://github.com/agentscope-ai/agentscope-java)
+ 릴리스 노트: [https://github.com/agentscope-ai/agentscope-java/releases/tag/v2.0.0](https://github.com/agentscope-ai/agentscope-java/releases/tag/v2.0.0)

> 이 글은 2026년 7월 Liu Jun의 AgentScope 2.0에 관한 공개 기술 강연을 정리한 것으로, 현장 발표 내용을 충실히 재현한다.
>

##  <font style="color:#5e5e5e;">AgentScope 2.0 소개</font>
AgentScope는 등장한 지 2년이 되었다. 올해 상반기에 2.0 버전을 릴리스했다. 2.0의 핵심 역량은 Harness 솔루션 전체를 프레임워크에 통합하는 것이다. 이는 또한 우리가 목표로 하는 시나리오가 주로 엔터프라이즈급 분산 에이전트 시나리오라는 것을 의미한다.

이 글은 세 부분으로 구성된다. 첫 번째 부분은 모두가 관심을 가지는 2.0의 핵심 역량을 다룬다. 기업 내부를 포함해 일부 개발자들은 이미 AgentScope 1.0으로 많은 프로덕션급 애플리케이션과 에이전트를 구축했기 때문에, 설계상의 차이점과 마이그레이션 방법을 간단히 소개하겠다. 중간의 가장 큰 부분은 Harness의 핵심 설계와 그것이 제공하는 역량을 소개한다. 마지막으로 몇 가지 예제를 통해 실제 엔터프라이즈급 활용 사례를 살펴본다.

### <font style="color:#5e5e5e;">AgentScope 생태계 파노라마</font>
<!-- 这是一张图片，ocr 内容为：A2A QDRANT MEMO POSTGRESQL MYSQL.. TABLESTORE ORACLE EVENTBRIDGE A2UI REDIS MONGODB MILVUS SQL SERVER SQLITE OCEANBASE OSS AG-UI AGENTSCOPE-SAMPLES QWENPAW RESPONSE API CUSTOMIZABLE AGENTS TOOL & SKILL SAFETY BROWSER-USE DEEP RESEARCH DATA-JUICER AGENT HIGRESS AL GATEWAY MEMORY MULTI-AGENT HIGLAW EVO TRADER MORE... SMALL-LARGE MODEL COLLABORATION MANAGER-WORKERS ARCHITECTURE 米 SPARK DESIGN AGENTSCOPE 2.0 TS AGENT SERVICE DATA-JUICER CLAUDE SESSION MANAGEMENT USER AUTHENTICATION & AUTHORIZATION BACKGROUND TASK MANAGEMENT STORAGE/DB MANAGEMENT CRON JOB MANAGEMENT WORKSPACE POOL DEEPSEEK GEMINI OPENJUDGE AGENT ENGINE WORKSPACE EVENT SYSTEM GLM REASONING LOCAL FILE SYSTEM MESSAGE & EVENT DOCKER EVENT STREAMING PERMISSION SYSTEM TOOLKIT OPENAL ISREISIE HUMAN-IN-THE-LOOP CLOUD SANDBOX BATCH ACTING QWEN AGENT MIDDLEWARE MODEL AZURE ACTING REASONING TRINITY-RFT STRUCTURED  COMPRESSION RETRIES CHAT MODEL SYSTEM PROMPT TTS/REALTIME REPLY MODEL STUDIO FALLBACK CONTEXT OFFLOAD OLLAMA DOTONO LOONG SUITE HIGRESS ARMS LANGFUSE DOCKER LLM 目 SLS ROCKETMQ LANGSMITH PHOENIX LOPENTELEMETRY E2B ?SGL NACOS. -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122675006-d6f5a6c1-4239-447e-a1fb-92eb318317ac.png)

큰 그림부터 시작해 보자. AgentScope를 하나의 프레임워크로 생각할 수 있는데, 이는 다이어그램의 파란색 부분이다.

프레임워크로서 우리는 현재 Python, Java, TypeScript로 구현체를 가지고 있으며, Go 구현체도 개발 중이므로 이 프레임워크는 사실상 모든 주요 언어를 아우른다.

프레임워크 계층은 주로 에이전트가 어떻게 개발되고 정의되는지를 정의한다. 예를 들어 가운데에는 전체 Agent Loop가 있으며, 신경 쓸 필요 없이 잘 설계된 Reasoning과 Tool Call 구현이 있다. Model, 그리고 Event와 Message의 전달도 모두 내장되어 있다.

2.0에서는 Workspace를 매우 핵심적인 추상화로 추가했고, 컨텍스트 관리도 더 많이 수행했다. 이것이 파란색 가운데 부분의 프레임워크다.

바깥쪽으로 확장하면, 에이전트 구축 프로세스를 둘러싼 다양한 생태계 어댑테이션이 있다. 모델 쪽에서는 왼쪽 부분이 국내(중국) DeepSeek, OpenAI 호환 모델, Qwen 모델을 다루며 모두 지원된다.

관측성 측면에서는 프레임워크가 이제 기본적으로 OpenTelemetry 계측을 갖추고 있어, 관측 데이터를 오픈소스 LangFuse와 같은 OpenTelemetry 호환 플랫폼이나, 알리바바 클라우드 제품 — 마이크로서비스 시대에는 ARMS였던 것이, 이제는 Agent Loop를 타깃으로 하는 Agent 시대 전용 제품으로 — 어디든 보고할 수 있으며, 모두 플러그인으로 연결할 수 있다.

그다음은 Higress다. 앞서 Agent Teams에 대해 이야기할 때 언급했듯, 모델 프록시든 MCP 프록시든, Skill이나 MCP 마켓플레이스 관리를 위해 Nacos를 사용하는 것을 포함하여, 전체 에이전트 생태계가 완전히 통합되어 있다.

더 위로 올라가면 QwenPaw와 AgentTeams는 이 프레임워크 생태계에서 파생된 구체적인 제품이자 엔터프라이즈급 에이전트 관리 역량이다.

### <font style="color:#5e5e5e;">ReActAgent 커널과 핵심 컴포넌트</font>
<!-- 这是一张图片，ocr 内容为：MODEL模型层 TOOL工具系统 CONTEXT 上下文 AGENT智能体 无状态引擎+AGENTSTATE 持久化 REACT推理-行动循环引擎 @TOOL 注解/TOOLBASE 继承 CREDENTIAL+CHATMODEL 两层架构 多用户/多会话并发安全 MCP 协议集成(STDIO/SSE/HTTP) 5大厂商:DASHSCOPE/OPENAL/ RUNTIMECONTEXT PER-CALL 元数据 ANTHROPIC/GEMINI/OLLAMA REDIS/MYSQL分布式状态共享 流式事件&结构化输出 SKILL 热加载MARKDOWN 指令集 STREAMING&THINKING&多模态 跨节点故障转移会话恢复 中断/恢复&人机交互 TOOL GROUP 按需激活/自管理 可扩展自定义PROVIDER MESSAGE & EVENT PERMISSION 权限 MIDDLEWARE中间件 5个生命周期HOOK位置 MSG类型化内容块体系 RULES +MODE +BUILT-IN CHECKS 洋葱式(ONION)+变换式(TRANSFORMER) 5种模式:DEFAULT/EXPLORE/BYPASS AGENTEVENT流式增量传输 /ACCEPT_EDITS/DONT ASK START DELTA END 生命周期 OPENTELEMETRY全链路追踪 建议规则自动生成&持久化 限速/回退/动态 PROMPT SSE推送&断点重建消息 危险路径不可绕过保护 -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122684082-53e4938d-8fbc-4d7a-af69-0538ce8955c6.png)



큰 그림을 살펴본 후, AgentScope 프레임워크 자체로 돌아가 보자. 하부 계층은 2.0에서도 변하지 않는다. ReAct Agent의 핵심 추론-도구 루프는 그대로다.

여기 몇 가지 핵심 역량을 나열했다. 1.0과의 차이는 크지 않으며, 하부 계층 역량은 변하지 않았고 일부 설계 최적화만 있었다. 혹은 아래를 보면 Permission을 추가했는데, 이는 도구 호출 권한 제어를 위한 추가 설계다. 이전에는 도구 권한 관리 역량이 없었기 때문이다.

가운데에는 Middleware도 있는데, 이는 이전의 Hook에 해당한다. 새 버전에서는 이벤트 전달과 중간 개입을 최적화했다. 전체적으로 보면 Model, Tool 정의, 그리고 가운데의 컨텍스트(이전에 우리가 만들었던 내용)는 대체로 비슷하다 — 이것이 전체 ReAct Agent 부분이다.

### <font style="color:#5e5e5e;">1.0 -> 2.0 마이그레이션 가이드</font>
<!-- 这是一张图片，ocr 内容为：破坏性变更-必须改造 总体兼容-平滑升级 已废弃-V2.1将移除 核心API保持一致 状态管理架构重构 MEMORY 接口 INMEMORYMEMORY/LONGTERMMEMORY 等标记 REACTAGENT BUILDER 模式不变,MODEL/TOOLKIT/SYSPROMPT 等 无状态 AGENT 引擎:AGENTSTATE 按(USERLD,SESSIONLD)寻址, 参数平滑迁移 不再绑定单一AGENT实例 @DEPRECATED(FORREMOVALTRUE) 迁移到AGENTSTATE.GETCONTEXT()+AGENTSTATESTORE RUNTIMECONTEXT 替代旧调用模式 @TOOL注解完全兼容 已有@TOOL/@TOOLPARAM标注的工具类无需任何修改即可在 CALL()必须传入RUNTIMECONTEXT;PER-CALL元数据不再挂在 TOOLEXECUTIONCONTEXT 2.0中注册使用 AGENT实例上 标记@DEPRECATED;底层自动桥接到RUNTIMECONTEXT,老代 码暂不失效 AGENTSTATESTORE 强制配置 MCP客户端无缝衔接 分布式部署(SANDBOX)下必须配置REDIS/MYSQL等分布式状 MCPCLIENTBUILDER API 不变; STDIO/SSE/STREAMABLE HTTP  三 迁移到 RUNTIMECONTEXT 态后端,否则BUILD()抛异常 种连接方式保持兼容 TOOLCALLPARAM.GETCONTEXT() MIDDLEWARE注册方式变更 MODEL层PROVIDER兼容 已废弃 新增5层HOOK 体系;旧回调/拦截器需迁移为 DASHSCOPE/OPENAL/ANTHROPIC/GEMINI/OLLAMA各 迁移到GETRUNTIMECONTEXT() CHATMODEL BUILDER 接口不变 MIDDLEWAREBASE实现 IMAGEBLOCK/AUDIOBLOCK/VIDEOBLOCK 消息体系向后兼容 PERMISSION SYSTEM全新引入 仍兼容但新代码建议统--使用 DATABLOCK 工具执行前置权限检查为必选项;需配置 USERMESSAGE/ASSISTANTMESSAGE/SYSTEMMESSAGE 构造方式不 变 PERMISSIONCONTEXTSTATE(至少选择MODE) 迁移到DATABLOCK+MEDIA TYPE 旧INTERRUPT()无参重载 单 SESSION 场景仍有效, 多 SESSION 下行为不确定 迁移到 INTERRUPT(USERLD,SESSIONLD) -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122690675-75e21e37-f202-4cd0-ab71-c28649694fa7.png)

핵심 하부 로직은 변하지 않았지만, 1.0에서 2.0으로 넘어갈 때 주의해야 할 세 가지 마이그레이션 포인트를 나열해 보겠다. 왼쪽에서 오른쪽으로 세 단계를 살펴보자.

먼저 녹색 부분. 업그레이드 과정에서 우리는 전반적인 호환성을 보장한다. 즉 앞서 언급한 모든 역량이 대체로 호환된다는 의미다. 일부 API가 사용 중단(deprecated)되었더라도 — 예를 들어 Hook이 설계상 Middleware로 대체되는 경우 — 2.0 릴리스에서는 여전히 사용 중단으로 표시만 하고 남겨둔다. 그래서 이론적으로는 대부분의 역량이 호환되며 부드럽게 업그레이드할 수 있다.

가운데 부분은 반드시 변경해야 하는 것들을 나열한다. 대부분의 역량은 호환되지만, 일부는 변경되었다. 이 부분을 변경하지 않으면 컴파일 오류나 런타임 오류가 발생할 수 있다. 이는 주로 다음 몇 가지 측면에서 나타난다.

첫 번째는 상태 관리다. 이제 프레임워크 전반에 Agent State라는 개념을 도입했다. 모든 Agent 런타임 상태는 Agent State를 통해 관리된다. 이는 하부 데이터 포맷 면에서 이전의 Session과 다르므로 유의해야 한다. 만약 이전에 1.0 Agent 상태를 실행 중이었다면, 우리는 호환성 레이어를 제공한다 — 2.0으로 전환하여 배포하면 여전히 이전 1.0 상태를 인식한다. 하지만 API부터 구현까지 상태 관리가 변경되었다는 점은 알아두어야 한다.

또 다른 큰 변화는, 우리가 멀티테넌시를 강하게 강조하기 때문에 — User 차원 격리든 Session 차원 격리든 — Agent의 `call`과 `stream` 메서드 진입점에 Runtime Context 개념을 추가했다는 것이다. 즉 어떤 User, 어떤 Session인지와 같은 런타임 컨텍스트를 반드시 전달해야 한다. 동시에 확장성도 제공되므로, 이를 기반으로 많은 확장을 구축할 수 있다.

다음 항목들은 모두 State와 Session에 강하게 연관되어 있다.

마지막 부분은 점진적으로 마이그레이션할 수 있는 것들이다. 가운데 부분의 breaking API 변경을 고치고 나면, 나머지는 2.1 버전에서 제거될 수 있는 사용 중단 항목들이므로, 먼저 2.0으로 마이그레이션한 다음 단계적으로 업그레이드하면 된다.

이 부분은 다소 번거롭다. 공식 웹사이트에 전용 마이그레이션 링크가 있으니 참고하기 바란다.



## AgentScope Harness 핵심 설계와 기능 상세

다음으로 오늘의 중요한 부분, 전체 AgentScope Harness의 설계로 넘어가자. 먼저 AgentScope에서 Harness의 전체 아키텍처를 살펴보자.

### <font style="color:#5e5e5e;">Harness 전체 아키텍처</font>
<!-- 这是一张图片，ocr 内容为：在REACTAGENT之上.把长期运行AGENT必备的工程能力打包 应用用户请求 HARNESSAGENT 薄包装能力叠加在REAC循环的关键时机内部MIDDEWARE顺序固定 FILESYSTEM WORKSPACE MEMORY SKILLS COMPACTION SUBAGENTS 本机/KV/沙箱 上下文压缩 双层长期记忆 技能装配 人格/知识 子AGENT编排 PLAN MODE CHANNEL/GATEWAY PERMISSION 工具白名单 只读思考+HITL 会话路由SSE REACTAGENT.推理循环(CORE).HARNESS 不改写此层.只叠加钩子 共享对象(能力间零耦合,只通过这三者通信) WORKSPACE RUNTIMECONTEXT AGENTSTATESTORE AGENTS.MD`MEMORY`SKILLS 跨请求恢复运行时状态 USERLD-SESSIONLD.EXTRA -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122696150-b486a69e-f316-407b-b023-edf68b00e52f.png)



가운데의 큰 파란 부분을 보라. Harness는 AgentScope의 하부 에이전트 추론 및 실행 컴포넌트 — 1.0이든 현재 버전이든 — 위에 구축된다. 이는 1.0 ReAct Agent를 감싸는 또 하나의 레이어로 이해할 수 있다.

이 레이어 위에서, 장시간 실행되는 에이전트에 필수적인 역량들 — 컨텍스트 관리, 컨텍스트 압축, 에이전트 오케스트레이션, Skill 실행, 샌드박스 환경에서의 격리된 도구 실행, 추론 계획과 태스크 상태 추적, 심지어 IM 메시징 시스템과의 통합과 도구 권한 제어까지 — 를 프레임워크의 하부 계층에 내장된 Harness 스위트로 패키징한다. 몇 가지 스위치를 켜거나, Harness 개발 패턴을 따름으로써 이를 활성화할 수 있다. 이는 이러한 역량 레이어를 하나 더하는 것과 같다.

### <font style="color:#5e5e5e;">Harness 빠른 둘러보기</font>
여기서는 Java 예제를 사용한다. AgentScope Java에서 Harness 레이어를 어떻게 사용할까? 먼저 의존성을 추가해야 한다. 우리가 그 위에 레이어를 하나 더 추가했으므로, 그 레이어의 의존성을 추가해야 한다.

<!-- 这是一张图片，ocr 内容为：<DEPENDENCY> <GROUPID>IO.AGENTSCOPE</GROUPID> </ARTIFACTID> <ARTIFACTID>AGENTSCOPE-HARNESS< <VERSION>${AGENTSCOPE.VERSION}</VERSION> </DEPENDENCY> -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122707767-503a3d8a-41c0-4b3e-a975-ffec45443c78.png)



다음은 개발 진입점이다. ReActAgent API 진입점은 여전히 존재하지만, 이제 HarnessAgent라는 새로운 API 진입점이 생겼다. 이를 사용해 직접 에이전트를 구축할 수 있다 — 내부적으로는 여전히 ReAct Agent를 사용하지만, API 레벨에서는 Harness Agent를 직접 사용할 수 있다.



이 둘 사이의 차이를 살펴보자. 위쪽 부분은 동일하다 — Name, System, Model. 아래를 보면 Workspace 개념이 있음을 알 수 있는데, Workspace를 지정하고, 몇 가지 압축 전략과 더 많은 설정, Sandbox 격리 설정을 포함해 모두 API를 통해 직접 지정할 수 있다.



아래의 차이점은, 호출 시 앞서 언급한 Context가 필요하다는 것이다. 여기서 Runtime Context가 정의된다. 호출 시 주로 User와 멀티테넌트 격리 정보를 전달한다.



<!-- 这是一张图片，ocr 内容为：PUBLIC CLASS FIRSTAGENT { PUBLIC STATIC VOID MAIN(STRING[] ARGS) { HARNESSAGENT.BUILDER() HARNESSAGENT AGENT NAME("NOTE-TAKER") "SYSPROMPT("你是一个帮助用户做笔记的助手.") 字符串形式由MODELREGISTRY 解析--自动读取 DASHSCOPE_API_KEY; 1/字符串 切换其他厂商时改用"OPENAI:GPT-5.5","ANTHROPIC:CLAUDE-SONNET-4-5", // "GEMINI:GEMINI-2.0-FLASH" 或"OLLAMA:LLAMA3". ,MODEL("DASHSCOPE:QWEN-PLUS") ,WORKSPACE(PATHS.GET(".AGENTSCOPE/WORKSPACE") COMPACTION(COMPACTIONCONFIG.BUILDER() .TRIGGERMESSAGES(30) KEEPMESSAGES(10) .BUILD()) . BUILD(); RUNTIMECONTEXT CTX - RUNTIMECONTEXT.BUILDER() SESSIONID("DEMO-SESSION") ,USERID("ALICE") .BUILD(); 当天的事 第一轮:自我介绍+ AGENT.CALL(NEW USERMESSAGE("我叫天宇,今天准备一个关于 REACT 的技术分享."), CTX),BLOCK(); 11第二轮:同 SESSIONID,自动恢复上一轮状态后回答 AGENT.CALL(NEW USERMESSAGE("我叫什么?我今天要干什么?"),CTX).BLOCK(); 子 -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122710684-609ea62c-3cd3-42ff-a07c-818ad562d0ca.png)



### <font style="color:#5e5e5e;">Workspace – 에이전트 진화의 진실 원천(Source of Truth)</font>
<!-- 这是一张图片，ocr 内容为：智能体是什么]+学到了什么]都是文件 四条设计线索 内容按生命周期分三类 静态资产 工程师编辑 定义与进化都是文件 不散落代码,不绑数据库;一个目录拷走完整AGENT AGENTS.MD , KNOWLEDGE/,SKILLS/ LLS/ - SUBAGENTS/ . TOOLS.JSON 生命周期三分 2 运行时文件 静态/运行时/长期记忆走不同读写路径 框架/AGENT写 AGENTS/<ID>/SESSIONS/  AGENTS/<ID>/TASKS/ PLANS/ 原生多租户隔离 ISOLATIONSCOPE:SESSION/USER/AGENT/GLOBAL 长期记忆 AGENT+后台任务  MEMORY.MD . MEMORY/YYY-MM-DD.MD WORKSPACE 1 FILESYSTEM 同一一份目录布局+三种物理后端可切换 -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784125157045-a1a7a8e3-e9de-45f9-ae0f-5f2562316c75.png)



Workspace는 오늘날 주류 에이전트들 — 에이전트 제품이든 에이전트 프레임워크든 — 에서 핵심적인 설계다. 우리는 이를 논리적 개념으로 이해할 수 있다. 여기에는 어떤 자산이 포함되어 있을까?



첫 번째 부분은 정적 자산으로, `AGENTS.md`, Skills, Sub-Agent와 같은 에이전트 정의와 관련된 것들이다. 이들은 이 비즈니스 지향 에이전트에 어떤 것들이 존재하는지를 정의한다. 이것들은 내가 정의하고 내 이미지와 함께 패키징하는 것으로, 정적 자산이라 불린다.



또 다른 부분은 런타임 데이터다. 이 데이터는 에이전트 런타임 동안 생성되며 사용자 상호작용을 통해 축적된다 — 실시간 Session 상태 기록이든, Task 상태 정보든, `MEMORY.md` 형태로 축적된 메모리든. 이 모든 정적 또는 런타임 자산은 Workspace에 정착한다. 이것이 Workspace의 핵심 개념이다.



### <font style="color:#5e5e5e;">추상 파일 시스템 – Workspace의 물리적 매개체</font>
<!-- 这是一张图片，ocr 内容为：同一份逻辑目录,三种物理后端,AGENT代码零改动 WORKSPACE.逻辑目录布局 ABSTRACTFILESYSTEM 接口.WORKSPACEMANAGER 路由 多副本 隔离 默认 LOCAL+SHELL SANDBOX REMOTE KV /提供SHELL X不提供SHELL /容器内SHELL DOCKER ` E2B DAYTONA `AGENTRUN REDIS JDBC OSS NACOS OVERLAY.WORKSPACE +PROJECT 路径策略ROOTED/SANDBOXED 本机模板+远端覆盖(两层读) WORKSPACE PROJECTION (SHA-256 增量) 单进程本机开发信任环境 多副本共享MEMORY/SESSIONS 快照恢复PIP/NPM INSTALL 状态 管理台改文件下轮生效 生产跑不可信代码首选 快,简单,无外部依赖 -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784125168400-b13a9d3d-9d2f-420b-808f-61c1cea17803.png)



AgentScope에서는 Workspace를 더 세밀한 단위로 다룬다. 예를 들어 에이전트 하나가 Workspace 하나를 가지지만, 그 에이전트 하나는 많은 사용자가 사용한다. 서로 다른 사용자를 위해 이 Workspace 안에서 논리적 멀티테넌트 격리를 수행한다 — user 레벨, Session 레벨, agent 레벨이 될 수 있다. 이것들은 서로 다른 격리 차원이다.

하부 계층에서 Workspace는 논리적 개념이다. 그렇다면 물리적 저장소는 무엇일까? 가장 직관적인 이해는 디스크이며, 이는 가장 직접적인 방식이다. 하지만 디스크에는 문제가 있다. 예를 들어 온프레미스 시나리오에서는 로컬 디스크에만 있을 수 있는데, 이는 디스크에 묶인 Workspace의 한계다.

이를 해결하기 위해 — 특히 우리가 엔터프라이즈급 분산 시나리오를 타깃으로 하기 때문에 — 상위 논리적 구현에서 하부 물리적 구현으로 넘어갈 때 인터페이스 하나를 추상화했다. 가운데 검은 부분인 Abstract File System, 즉 추상 파일 시스템 인터페이스다.

에이전트가 Workspace를 조작할 때, 물리 계층은 이 추상 파일 시스템 인터페이스를 사용한다. 우리는 여기에 세 가지 기본 구현을 제공하며, 물론 임의로 확장할 수도 있다.

+ 첫 번째는 이 머신에서의 로컬 온프레미스로, 디스크를 직접 조작한다.
+ 사용자 격리를 원한다면 트리 파일시스템, 즉 트리 구조다.
+ 프로덕션 배포에서는 에이전트 하나가 여러 인스턴스에 배포되므로 각 인스턴스가 동일한 Workspace를 보아야 한다. 이때 추상 파일 시스템 인터페이스를 MySQL이나 Redis 같은 데이터베이스, 또는 알리바바 클라우드 OSS에 연결할 수 있다. 이렇게 하면 Workspace 공유가 이루어진다 — 동일한 Workspace 인스턴스를 서로 다른 에이전트 인스턴스에서 볼 수 있다.

Workspace에 더 높은 격리 요구 사항이 있다면 — 예를 들어 도구 실행(도구 실행도 Workspace 공간에서 일어난다) — Sandbox에 연결할 수 있다. Workspace 하나가 Sandbox 하나에 매핑되며, Sandbox 생명주기만 잘 관리되면 멀티테넌트 격리가 달성된다.

이것이 Harness에서 Workspace의 논리적 개념과 물리적 저장소 구현이며, 이를 통해 분산 시나리오를 지원한다.

### <font style="color:#5e5e5e;">내장 컨텍스트 압축 전략 – 네 겹의 방어선</font>
<!-- 这是一张图片，ocr 内容为：让对话保持在TOKEN预算内.同时不丢矣键信息 压缩流水线(按触发时机排布) TOOL 执行 OVERFLOW RECOVER RESULT EVICTION SUMMARY LLM TRUNCATE ARGS 真的撞墙极端压缩 产生工具结果 大参数字符串截断 单条>80K落盘 前缀结构化摘要 尾部保留原文 上下文留首尾+指针 零LLM 成本 自动重试一次 可调节的杠杆 压缩不会触碰的内容 触发阈值 PLAN MODE 状态 TRIGGERMESSAGES TRIGGERTOKENS AGENTSTATE.PLANMODECONTEXT 子AGENT后台任务 保留窗口 KEEPMESSAGES,KEEPTOKENS AGENTS/<ID>/TASKS/<SID>.JSON FLUSH 时机 TODO_WRITE 清单 ALWAYS NEVER,THROTTLED AGENTSTATE.TASKSCONTEXT 权限规则 独立小模型 COMPACTION.MODEL / MEMORY.MODEL AGENTSTATE.PERMISSIONCONTEXT 卸载排除 READ_FILE GREP_FILES 默认排除 永不压缩对话日志 SESSIONS/<SID>.LOG.JSONL -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784125116995-c234913e-e1b1-4dcb-8f53-e58380e9bbcb.png)



Workspace 안의 전체 컨텍스트를 어떻게 관리할까? 먼저 내장 압축 전략을 제공한다. 세션 실행 중 모델에는 컨텍스트 윈도우 제한이 있는데, 어떻게 컨텍스트를 그 한계 안에 유지할까?



여기 몇 가지 압축 전략이 제공된다. 그림에는 일부만 표시되어 있으며 실제로는 더 세부적인 설정들이 있다. 예를 들어 도구 실행 결과가 일정 크기를 초과하면, 절단-오프로드 구현이 있다 — 디스크로 오프로드되고 파일 참조 경로로 제공된다. 도구 입력 파라미터가 너무 클 때도 길이 기반 절단 전략이 있다. 이것들이 기본 조치다. 또한 과거 메시지를 압축하고 최근 것들을 유지하는 것도 포함되는데, 이는 익숙한 일상적인 압축 전략이다.



압축하는 동안에도 몇 가지 유의할 점이 있다. 가장 전형적인 것은 압축할 때 정보를 잃지 않도록 노력해야 한다는 것이다. 어떤 정보를 잃어서는 안 될까?



오른쪽 아래 부분에 몇 가지 예가 있다. 예를 들어 전체 태스크 실행 계획 — 일부 복잡한 태스크는 계획이 필요하며, 이 계획은 처음 몇 개 메시지에 있을 수 있다. 특별한 처리 없이 바로 압축하면 계획이 사라질 것이다.



또한 이 계획을 기반으로 일부 서브에이전트를 생성했을 수 있는데, 그중 일부는 비동기이거나 태스크가 비동기적일 수 있다. 비동기인 경우 태스크가 아직 반환되지 않았을 수 있으므로, 태스크 상태를 계속 추적해야 한다. 이때 거칠게 압축하면 서브에이전트 상태가 사라질 수 있다.



따라서 이렇게 전역적으로 업데이트되는 상태 — 계획 세부사항이든, 서브에이전트의 비동기 태스크 상태든, 내 TODO 목록이든, 다양한 도구 권한 승인 기록이든 — 이 정보는 절대 압축되지 않도록 보장되어야 한다. 그래서 이 두 부분은 차별화된 처리가 필요하다.



### <font style="color:#5e5e5e;">이중 계층 장기 메모리 – 사실이 자동으로 영속화된다</font>
<!-- 这是一张图片，ocr 内容为：让AGENT记住跨会话的事实,同时避免上下文无限膨胀 第一层日流水账 对话MESSAGES FLUSH UM 当前上下文 MEMORY/YYY-MM-DD.MD  追加.未去重 每次 CALL 结束 CONSOLIDATION LLM 后台节流,最少30 MIN/次 每轮注入 SYSTEM PROMPT 第二层MEMORY.MD 合并去重策划后的长期记忆 <MEMORY_CONTEXT> AGENT主动查询工具 见到MEMORY.MD 截断提示时会自动调用 MEMORY SEARCH MEMORY_GET SESSION SEARCH 管线里的三次独立UM调用(定制入口) FLUSH CONSOLIDATION COMPACTION SUMMARY MEMORYCONFIG. FLUSHPROMPT COMPACTIONCONFIG.SUMMARYPROMPT MEMORYCONFIG.CONSOLIDATIONPROMPT 把对话前缀压成一条摘要(当下上下文) 从对话窗口抽取长期事实到日流水账 把每日流水账合并去重到MEMCRY.MD -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784124420246-a74faf0f-3b3e-4498-b058-4e15955eb0d8.png)



또 다른 역량은 장기 메모리 침전(sedimentation)이다. 앞서 논의한 압축은 주로 일시적인 상태 — 런타임 중의 순간적인 상태를 관리하고 제어하는 것 — 에 관한 것이다. 압축할 때 일부 정보는 필연적으로 손실되는데, 이 정보는 장기 메모리로 침전될 수 있다.



프레임워크의 전략은 다음과 같다. 세션 압축 전에 Flush 정렬을 할 수 있다. 첫 번째 계층은 일별 파일에 기록되는데, QwenPaw와 비슷한 구조로 기록될 수 있다.



동시에 백그라운드 태스크도 있어서, 그날의 메모리와 전체 Memory 침전물을 주기적으로 스캔하여 전역 `MEMORY.md`로 증류한다. 이 `MEMORY.md`는 모든 요청마다 System Prompt에 전역적으로 로드되므로, 그 크기와 데이터 품질이 매우 중요하다.



Memory Search, Memory Get, Session Search와 같은 몇 가지 메모리 관리 도구도 제공된다. 모델은 `MEMORY.md`의 안내에 따라 적절한 시점에 당신의 원장(ledger)과 기타 정보를 조회한다. 이것이 협업적인 장기 메모리 침전 전략이다.



이 모든 단계 — 일별 원장 메모리 추출이든, `MEMORY.md`의 주기적 증류든, 압축이든 — 내부의 Prompt는 커스터마이징 가능하며, 서로 다른 시나리오에서 더 나은 추출과 메모리 구현을 유도하기 편리하게 되어 있다. 프레임워크의 다음 항목들은 모두 Prompt 설정 진입점이다.

### <font style="color:#5e5e5e;">서브에이전트 오케스트레이션, 위임, 병렬 처리, 비동기 알림</font>
<!-- 这是一张图片，ocr 内容为：把独立,上下文重,可并行的任务交给专家型子AGENT PARENT AGENT AGENT_SPAWN / AGENT_SEND 远程子AGENT 同步子AGENT 后台任务 暴露给用户 URL+HEADERS TIMEOUT0 TIMEOUT>0 EXPOSE TO USER-TRUE 用户可直连子AGENT 阻塞等结果流式转发 返回TASK_ID 反向通知 AGENT PROTOCOL HTTP SYSTEM-REMINDER 自动反向注入 关键能力 ISOLATED/SHARED PLAN MODE自动只读 流式事件带SOURCE路径 递归3层硬上限 DENY 权限自动继承 PERSISTSESSION 复用实例 WORKSPACE -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784125262501-e219f86e-b8c1-4b93-92c0-f8d8a51cf131.png)



Harness에서 또 다른 매우 중요한 점은 에이전트 오케스트레이션이다. 이 다이어그램이 표현하는 것은 부모 에이전트가 모든 서브에이전트를 직접 관리한다는 것이다.



태스크가 부모 에이전트에 진입한 후, Agent Fork, Agent Spawn과 같은 내장 도구가 있다. 부모 에이전트는 필요에 따라 서브에이전트를 생성한다. 생성된 서브에이전트는 먼저 두 가지 유형이 있다 — 동기 서브에이전트와 비동기 서브에이전트. 비동기 서브에이전트는 장시간 실행되는 태스크에 적합하며, 이제 완료 시 부모 에이전트에게 결과를 능동적으로 알리는 것을 지원한다.



또 다른 특수한 유형은 원격 서브에이전트로, 이 또한 지원되어 원격 서브에이전트를 생성할 수 있다. 동시에 부모 에이전트에게는 모든 지원 도구를 아우르는 Task List 툴킷이 제공된다. 부모 에이전트는 어떤 서브에이전트가 존재하는지와 각 서브에이전트의 상태를 능동적으로 확인할 수 있으며, 이를 위한 지원 도구가 있다.



언급할 가치가 있는 또 하나의 포인트, 많은 엔터프라이즈 사용자의 핵심 요구 사항: 보통 사용자는 부모 에이전트와 직접 대화하며, 부모 에이전트가 서브에이전트를 생성하는 것은 자신의 비즈니스다 — 태스크를 완료하기 위해 서브에이전트를 관리한다. 하지만 실제로는 많은 사용자들이(Claude Code를 사용할 때를 포함해) 부모 에이전트가 생성한 서브에이전트로 직접 전환해서 그 서브에이전트와 대화하며 서브태스크를 안내하고 싶어 한다.



AgentScope도 서브에이전트와 직접 대화하는 것을 지원한다. 이 서브에이전트가 부모 에이전트에 의해 생성되었더라도, 이를 노출시켜 직접 대화할 수 있는 방법이 있다.



서브에이전트의 전체 설계는, 앞서 언급한 더 큰 레이어들 외에도 세부 사항들이 있다. 예를 들어 부모 에이전트와 서브에이전트 사이에 컨텍스트가 공유되는지가 아래에 나열되어 있다. 서브에이전트 이벤트가 부모 에이전트를 통해 어떻게 전달되는지, 그리고 이벤트가 서브에이전트에서 온 것인지 부모 에이전트에서 온 것인지 어떻게 구분하는지(모두의 이벤트 스트림이 섞여 있기 때문에)를 포함하여, 우리는 마커를 가지고 있다.



그다음은 권한 문제다 — 부모 에이전트가 서브에이전트를 생성한 후, 서브에이전트의 권한은 무엇이며, 부모 에이전트의 권한을 상속하는지 여부. 이런 세부적인 사안들에 대해 전체 프레임워크에는 메커니즘이 마련되어 있다.

### <font style="color:#5e5e5e;">Sandbox 관리: 격리, 복구, 분산</font>
<!-- 这是一张图片，ocr 内容为：把危险操作关进容器,把安装状态存进快照 CALL0生命周期沙箱决策 执行边界 01 文件与SHELL命令都在隔离容器里执行,宿主完全不参与 容器&WORKSPACE 还在吗? 跨调用恢复 02 有复用 PIPINSTALL NPMINSTALL 临时文件都随快照保留,下次 CALL 无需重装 重装 最快路径 无容器有快照恢复 多副本可用 快照重建 03 通过分布式STORE+远端快照,任意节点都能RESUME出同一份工 都没有冷启动 工作区 WORKSPACESPEC全量初始化 快照后端 后端矩阵DOCKER  KUBERNETES`DAYTONA -E2B AGENTRUN LOCAL  本地磁盘 REDIS低延迟 ISOLATIONSCAPE决定沙箱 LDT,复用(USER 黑 JDBC.关系库 OSS/S3多副本 串行化) 07 -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122744046-584de68f-e539-424a-8293-694603ad0ab6.png)

Sandbox에 관한 메커니즘도 있다. Sandbox는 주로 에이전트 실행의 보안 문제를 해결하는데, 이는 매우 중요한 부분이다. 에이전트 도구 실행 중 도구 실행을 Sandbox에 넣을 수 있으며, 프레임워크에는 Sandbox 생명주기 관리 시스템이 있다. 여기서는 자세히 다루지 않겠다. 관심 있는 독자는 공식 문서를 참고하기 바란다.

### <font style="color:#5e5e5e;">Skills: 4계층 레지스트리와 Sandbox 내부 실행</font>
<!-- 这是一张图片，ocr 内容为：SKILL.MD+REFERENCES/+SCRIPTS/可复用的能力包 注册中心四层优先级(低高) 沙箱内执行三步透明化 高 物化STAGER USERIDYSKILLS/ 01 L4 市场SKILL从内存写到宿主SKILLS-CACHE 用户级隔离目录.覆盖共用版 SHA-256文件级去重 后发式恢复 CHMOD+X WORKSPACE/SKILLS/ L3 WORKSPACE PROJECTION 02 工作区共用项目特有约定 把 SKILLSKILLS-CACHE/打成 TAR HYDRATE 进沙箱/WORKSPACE 内容SHA-256整体比对增量 SKILLREPOSITORY(..) L2 市场后端:GIT/NACOS/MYSQL/CLASSPATH 容器内执行 03 <FILES-ROOT>用容器内绝对路径 AGENT不用猜路径 低 PROJECTGLOBALSKILLSDIR 快照保留PIP/NPM INSTALL 副作用 项目全局目录:~/.AGENTSCOPE/SKILLS/ -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122747931-aaa39b3a-95fc-40c1-b572-cc652a409fbc.png)



Skill에 대해 다룰 가치가 있는 부분이 두 가지 있다.



첫 번째 부분은 Skill 관리다. Skill 관리를 위한 AgentScope Harness는 Nacos와 같은 중앙화된 Skill 관리 시스템에 연결되어, 중앙에서 관리되는 Skill을 자동으로 로드해 로컬에서 인식하고 사용한다. 동시에 앞서 언급한 Workspace의 세밀한 관리 메커니즘을 기반으로, 서로 다른 사용자 간에 Skill 격리도 구현할 수 있다 — 이 사용자는 자신만의 Skill을 가지고, 다른 사용자는 다른 Skill을 가지며, 서로에게 보이지 않는다. 이 또한 구현 가능하다.



다른 부분은 Skill 실행이다. Skill은 때때로 단순한 워크플로가 아니다 — 지원 스크립트와 리소스 파일도 포함하므로, 그 실행은 보안 통제되어야 한다. 이제 우리는 전체 Skill을 Sandbox에 투영하는 것을 지원하여, 모든 Skill 스크립트가 Sandbox 내부에서 닫힌 루프로 실행될 수 있게 한다.



### <font style="color:#5e5e5e;">Plan Mode: 충분히 생각하기 -> 적어두기 -> 그런 다음 행동하기</font>
<!-- 这是一张图片，ocr 内容为：只读思考阶段+计划文件+HITL退出 工作流(明确固化四步:设计划人确认人确认执行 执行阶段 PLAN WRITE 只读调查 用户请求 HITL确认 PLAN_ENTER PLAN_EXIT PLAN.MD 工具解禁 复用权限系统的ASK PLAN阶段的白名单 四种终态(别只看ISPLANMODEACTIVE) READ_FILEGREP_FILES GLOB_FILES 只读文件工具 末进入 模型直接BUILD,任务与WORKSPACE不匹配 LIST FILES MEMORY_SEARCH , MEMORY_GET 内存查询工具 SESSION_SEARCH 进入PLAN_EXIT 成功:规划完+获批+BUILD模式 PLAN 三件套 PLAN_ENTER PLAN_WRITE - PLAN_EXIT 仍PLAN有PLAN 已起草但未退出;后续消息可批准继续 任务清单 TODO WRITE 仍PLAN无PLAN 只说不做:文本像计划但没写出 SHELL(可选) ALLOWSHELLINP LANMODE()后放开为只读用途 -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122751350-9d17d85b-4f11-4a40-b87c-5e996beff223.png)



Harness는 이제 Plan mode도 지원한다. 1.0에 익숙한 사용자라면 1.0에도 Plan mode가 있었다는 것을 알 것이다 — 태스크가 주어지면 먼저 계획하고 그다음 실행하는 방식이다.



1.0 구현은 내부 상태 머신에 더 가까웠으며, 상태 머신에 의해 구동되었다. 2.0에서는 전체 Plan mode를 최적화했다. 먼저 PlanEnter, PlanExit 등과 같은 Plan 관련 지원 도구 세트가 내장되어 있다.



사용자 요청이 들어오면 Plan을 직접 활성화할 수 있다. Coding Agent에 익숙하다면 이를 Coding Agent의 Plan mode와 동일한 것으로 이해할 수 있다 — 예를 들어 Codex나 Claude Code에서는 Plan을 켤 수 있다. AgentScope로 개발된 비즈니스 에이전트도 마찬가지다 — Plan mode를 활성화하도록 알려주는 인터페이스가 있어서, 질문을 하면 Plan을 생성하고, 이후 Agent mode로 전환하여 실행하며, 이전 Plan을 기반으로 직접 실행한다.



또는 자율 인식 모드로 진입하게 할 수도 있는데, 여기서는 태스크 자체를 기반으로 Plan mode로 전환할 수 있다. 각 도구에는 Permission이 있기 때문에, Plan mode로 전환할 때 먼저 당신에게 물어볼 수 있다. Plan이 실행된 후에는 Coding Agent처럼 Agent mode로 다시 전환할 때 확인 창이 뜬다. 전체 흐름은 동일하며, 프런트엔드 UI에서 이를 하나로 엮을 수 있다.

### <font style="color:#5e5e5e;">Channel: 메시징 플랫폼 -> 게이트웨이 -> 에이전트</font>
<!-- 这是一张图片，ocr 内容为：一行接线.自动完成会话管理并发路由.流式 GATEWAY路由与治理 消息平台 HARNESSAGENT实例池 HTTP/SSE GATEWAYBOOTSTRAP SALES MAIN WEBSOCKET 会话管理 USERLD映稳定SESSIONLD映射 钉钉 SUPPORT PER-SESSION 并发 HARNESSAGENT 同 SESSION 消息公平排队 飞书 AGENT 路由 企业微信 BILLING 多AGENT场景按AGENTID分发 HA RNESSAGENT GITHUB 子 AGENT 桥 EXPOSE.TO_USER 反向暴露给客户端 GITLAB REVIEWER 流式转发 EXPOSED SUBAGENT FLUX<AGENTEVENT>SSE直通 -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122754918-bd884356-94db-4615-b469-9ae38f00dab2.png)



<font style="color:#080808;background-color:#ffffff;">일부 엔터프라이즈 비즈니스 시나리오에서는 Channel 플랫폼에 연결할 필요가 있다 — 백그라운드 태스크를 엔터프라이즈 IM 시스템과 연결하는 것이다. 프레임워크는 이를 위한 네이티브 지원을 제공한다.</font>



## 실전에서의 AgentScope 엔터프라이즈 에이전트(공식 예제)
<font style="color:#080808;background-color:#ffffff;">여기까지가 Harness 부분이다. Harness 설계는 상당히 방대하다. 구체적인 사용법은 공식 문서를 참고하기 바란다. 마지막으로 몇 가지 예제를 소개하겠다.</font>

### **<font style="color:#1f2937;">Personal Assistant — 로컬 FS 및 Shell을 직접 사용, 사용하면서 성장</font>**
<!-- 这是一张图片，ocr 内容为：定位 你的本机(SINGLE PROCESS  SINGLE NODE) 装在你自己电脑上的个人助手--以你的身份,在 你的FS/ SHELL 里干活. CHANNEL 适配器 (每个AGENT一个实例) HARNESSAGENT LLM推理循环 CHATUI (WEB UI) SKILLS-SUB-AGENTS-MCP 关键设计 单进程,无鉴权,无租户,无DOCKER SANDBOX;随 DINGTALK/WECOM/FEISHU 自进化闭环今WORKSPACE文件 随用随长--SKILLS/SUBAGENTS/MEMORY 都是自写 自写文件. GITHUB /GITLAB LOCALFILESYSTEMWITHSHELL 内置通道 一没有SANDBOX,没有租户命名空间,没有远端存储 直连宿主机 CHATUI(WEB UL,默认) 钉钉企微飞书 本机 SHELL BASH/ZSH 本机文件系统~/.AGENTSCOPE/... GITHUB/GITLAB WEBHOOK WORKSPACE目录(SELF-EVOLVING) 1人1节点 直连本机SHELL SELF-EVOLVING AGENTS.MD - SKILLS/ - SUBAGENTS/ - TOOLS.JSON - MEMORY/ - SESSIONS/ - KNOWLEDGE/ -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122784444-12b760c1-6fb4-4c21-b808-186230266ea5.png)

첫 번째 예제 — 이 예제들은 모두 공식 GitHub 저장소에 있다 — 는 AgentScope Java를 위한 QwenPaw 유사 제품이다. 이는 매우 단순화된 QwenPaw를 구현한 것으로, 구체적으로 출시된 제품이 아니라 AgentScope로 개인 비서 제품을 개발하는 방법을 검증하기 위해 만든 것이다.



앞서 언급했듯이, 이것의 Workspace 모드는 완전히 로컬 디스크에 묶여 있으므로 분산 배포를 지원하지 않는다.

### **<font style="color:#1f2937;">멀티테넌트 Managed Agent 플랫폼 — 조직이 공유하는 하나의 자기진화형 에이전트</font>**
<!-- 这是一张图片，ocr 内容为：定位 SPRING BOOT:8080  REACT SPA+REST APL(JWT) 一个团队/一家公司共建,运营自进化AGENT的平台 台--从浏览器登录,不写代码搭AGENT. REST API(JWT 鉴权) REACT SPA HARNESSGATEWAY 每(USER,AGENTID) 独占一个 HARNESSAGENT 关键设计 每(USER,AGENT)独立WORKSPACE;三档共享:RUN/  AGENT (ALICE, AGENT-B)  AGENT (BOB, AGENT-A)  AGENT (ALICE, AGENT-A) /EDIT/FORK;一开关切换LOCAL.SANDBOX  REMOTE. COMPOSITEFILESYSTEM (PER(USERLD,AGENTLD)命名空间隔离) 三种FS-SPEC模式共用同一形状--只是底层存储引擎变了 SANDBOX 隔离粒度 SESSION`USER.AGENT`GLOBAL--由 LOCAL SANDBOX REMOTE BUILDER.SANDBOX.ISOLATION 控制. BASESTORE  REDIS/OSS 横向扩展 默认.本机FS+SHELL DOCKER? 展 SESSION/USER/AGENT/GLOBAL 共享分级 多租户JWT 可分布式 持久化,H2(开箱) MYSQL/POSTGRESQL(生产) 用户.AGENT定义共享授权 -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122793414-88f0d3ad-ba3b-4ab8-8f57-1902692e8c40.png)



또 다른 예제는 멀티테넌트 에이전트 플랫폼으로, Agent Builder라는 노코드 에이전트 개발 플랫폼으로 이해할 수 있다.



UI 인터페이스를 가지고 있으며 회사 내에 중앙 집중적으로 배포할 수 있다. 배포 후에는 SaaS 플랫폼이 되어, 회사 내 모든 사람이 이 위에서 에이전트를 만들 수 있다. 관리자로서 나는 회사 내 모든 사람이 사용할 수 있는 공유 에이전트를 만들 수도 있다.



각 사용자가 같은 에이전트를 사용하더라도, 하부 계층에서는 멀티테넌트 격리를 달성할 수 있다 — 아래의 Workspace와 물리적 File System 그룹핑에 의존한다. 이를 통해 각 사용자의 데이터가 격리된 멀티테넌트 에이전트 플랫폼을 실현한다. 동시에 나는 내 자신의 에이전트를 정의하고 다른 사람들과 공유할 수 있다.



<font style="color:rgb(44, 44, 43);">이는 본질적으로 Claude Managed Agents, Langchain Managed Agents, Qoder Cloud Agents 플랫폼의 프로토타입이다. AgentScope 2.0을 사용하면 컨트롤 플레인과 데이터 플레인 API만 노출함으로써 이를 매우 빠르게 구축할 수 있다.</font>

### **<font style="color:#1f2937;">Data Agent 플랫폼 — 사용자별 진화 + 승인 기반 역량 마켓플레이스</font>**
<!-- 这是一张图片，ocr 内容为：定位 SPRING BOOT WEBFLUX :8080  分布式一等公民(REDIS 后端) 每位数据分析师一个专属SQL/图表/报表AGENT 越用越懂本组数据源与报表习惯. HARNESSGATEWAY UDA-{USERID}-{AGENTID} (PER-用户 FORK) DATA-AGENT (全局骨架 GLOBAL) 三项关键 OVERLAYFILESYSTEM (SKILLS/ SUBAGENTS/) 多人并行进化?互不干扰 能力市场--有闸门的知识流动 上层.PER-用户REMOTEFILESYSTEM (可写) SANDBOX  交由应用方治理 MEMORY/ , MEMORY.MD " SESSIONS/ . TASKS/ 下层:SHARED/{SKILLS,SUBAGENTS)(只读.审批后合入) KNOWLEDGE/ . AGENTS,MD 亦只读 通道&存储 CHATUI DINGTALK 通用WEBHOOK (HMAC) H2MYSQL/POSTGRESQL  REDIS分布式 (脚本执行.生命周期由应用方掌握) SANDBOXFILESYSTEM 容器规格,回收节奏.数据库驱动/NOTEBOOK工具链--由安全与运维口味决定 分布式 共享库 审批合入 能力市场用户贡献 SHARED/自下而上生长 -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784122802713-c8373afe-1cc1-4083-a8a7-2865074a89c6.png)



마지막으로 두 가지 예제가 더 있다. 하나는 Data Agent인데, 이 또한 멀티테넌트 시나리오 예제다.



이 Data Agent는 각 사용자에게 자신의 데이터 기반을 위한 격리된 공간을 제공한다. 각 사용자는 자신만의 Skill도 가질 수 있으며, 서로 다른 사용자가 침전시킨 Skill은 이 시스템 안에서 승인 메커니즘을 거친다 — 내 Skill은 공유되도록 신청할 수 있고, 승인 프로세스를 거쳐 통과하면 공유되어 모든 사용자가 사용할 수 있게 된다.



### **<font style="color:#1f2937;">Autonomous Coding Bot — 스레드 라우팅 + 일회성 Docker 컨테이너</font>**
<!-- 这是一张图片，ocr 内容为：定位 ISSUEW.PRREVIEW.行内迭代.永不动本机/构建机FS 企业内可部署的自主编码机器人--ISSUE里留言就 给你开PR;PR 上加它当 REVIEWER 就有 REVIEW. GITHUBWEBHOOK.CLI.钉钉.飞书 所有触达通道 CHANNEL 适配器 两条安全底线 HMAC 校验,过滤自评 永不动宿主机FS--全在SANDBOX 内 SANDBOX 生命周期由框架自动管--按SESSION 拉 THREADLD FACTORY 起,复用,销毁 SHA-256 UUID GITHUB:ISSUE:OWNER/REPO#42 RUNDISPATCHER 立即派发THREAD忙时入队 MESSAGE/BUDGET/LIMIT HOOK 默认安全网 WEBHOOK 签名事件去重PER-SESSION 限流 HARNESSGATEWAY 模型预算上游限流透明重试 REVIEWER (REVIEW_REQUESTED) CODING(ISSUE/ PR 选代) SANDBOXFILESYSTEM . PER-THREAD DOCKER 容器 可横向扩展 GH/IM通道 PER-SESSION AGENTSCOPE/CODING-SANDBOX:LATEST .运行时托管  首次拉起  同 SESSION 复用 GITHUB API  目标仓库 -->
![](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/54037/1784124780453-da758680-dccd-4e49-9eab-ec5529cb782a.png)

마지막 예제는 Coding Agent다. 이 Coding Agent는 Claude Code나 Cursor 같은 로컬 설치형 도구들과는 다르다.



이는 공유 에이전트 시나리오를 위한 엔터프라이즈급 서비스다. 예를 들어 기업 내에 이 Coding Agent 서비스를 배포한 후, 전형적인 시나리오는 GitLab에 연결하는 것이다 — 배포된 Coding Agent 서비스를 GitLab에 연결한다.



모든 사람이 GitLab에서 Issue나 Pull Request Review를 처리할 때, 전송된 모든 요청은 이 Coding Agent 서비스에서 수신된다. 당신의 태스크 런타임과 다른 사용자들의 런타임 환경은 격리되어 있다 — 당신의 사용자를 전담해서 서비스하는 Sandbox를 생성한다. 당신이 처리하는 모든 Issue와 Pull Request 상태의 연속성을 보장하며, 사용자들 사이에 상호 영향이 없다.



각 Issue마다 연속적인 대화를 이어갈 때도, 전체 Issue의 상태는 다른 Issue나 Pull Request와 섞이거나 서로 영향을 주지 않는다. 그러므로 이는 R&D 협업을 위해 기업 내부에 배포된 Coding Agent 예제다. CI/CD 플랫폼으로 구축하고 AI로 구동하는 것도 가능하다. 그 아래의 전체 메커니즘은 AgentScope Harness 설계를 사용한다.



## AgentScope는 기업들에 널리 채택되고 있다
<font style="color:rgb(44, 44, 43);">2024년 오픈소스로 공개된 이후, AgentScope 에이전트 프레임워크는 점차 엔터프라이즈 사용자들에게 널리 채택된 Agent Framework가 되었으며, 특히 분산형 프로덕션 레디 에이전트 시나리오에서 그러하다.</font>

<font style="color:rgb(44, 44, 43);">알리바바 그룹 내에서 AgentScope(Java와 Python)는 이미 가장 널리 사용되는 프레임워크로, Fliggy, 타오바오 플래시 구매, Hujing Entertainment, AIDC, Alibaba Holdings, 타오바오 트레이딩, 타오바오 모바일, 1688, Qwen APP, Amap, 알리바바 클라우드, Ant International, Ant Global Payments 등의 비즈니스 라인을 아우른다.</font>

<font style="color:rgb(44, 44, 43);">오픈소스와 알리바바 클라우드 퍼블릭 클라우드 사용자 측면에서는, 금융, 운송/물류, 소비재 유통, 제조, 에너지, 헬스케어, 교육/정부-미디어, 인터넷, SaaS, 컨설팅 등 다양한 산업의 선도 기업들에 널리 사용되고 있다.</font>


