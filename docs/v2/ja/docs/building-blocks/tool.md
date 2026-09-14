---
title: "ツール"
description: "エージェントが呼び出せる能力を定義・登録・管理する"
---

## 概要

ツールは、エージェントが世界に働きかける手段です——ビジネスオペレーションの実行、API 呼び出し、データの読み書きなどを行います。各ツールは JSON Schema として LLM に自身を公開し、エージェントは統一されたインターフェースを通じてそれを呼び出します。

AgentScope は、ツール関連のビルディングブロックを次の3つの概念に整理しています:

- **Tool** —— `AgentTool` 契約を実装する任意のオブジェクト(通常は `ToolBase` を継承する)、またはメソッドに `@Tool` アノテーションが付与された任意の通常クラスです。Java では後者を *リフレクション関数ツール* と呼びます——`Toolkit#registerTool(Object)` はリフレクションによってそれらを自動的に登録します。
- **Toolkit** —— ツール、MCP クライアント、スキルを登録し、それらの JSON スキーマをモデルに公開し、各ツール呼び出しを対応するツールオブジェクトへディスパッチするコンテナです。
- **Tool Group** —— ツール / MCP クライアント / スキルを1つの単位としてまとめ、有効化・無効化できる名前付きの束です。エージェントは組み込みのメタツールを使ってランタイムにグループを切り替え、コンテキストを絞り込んだ状態に保ちます。

```java
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.builtin.TodoTools;

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new TodoTools());
toolkit.registerTool(new MyCustomTools());
```

`registerTool(Object)` だけを呼び出した場合、登録したオブジェクト上のすべての `@Tool` メソッドは予約済みの `"basic"` グループに参加します——これは常にアクティブです。エージェントをさらに拡張するには、MCP クライアント、ツールグループ、スキルを追加してください——詳細は以下のセクションを参照してください。

## Java ツール

Java ツールとは、`AgentTool` 契約を満たす任意のオブジェクトです。AgentScope は、明示的なパラメータスキーマでツールを宣言するための抽象基底クラス `ToolBase` と、通常のメソッドをツールへとラップするリフレクションアダプターを同梱しています。

### AgentTool / ToolBase の契約

`ToolBase` は `AgentTool` の抽象実装です。以下の表はそのプロパティとメソッドを一覧にしたものです。

エージェントとランタイムに公開されるプロパティ:

| メソッド | 型 | 説明 |
|--------|------|-------------|
| `getName()` | `String` | エージェントに表示されるツール名 |
| `getDescription()` | `String` | エージェントに表示される説明 |
| `getParameters()` | `Map<String, Object>` | パラメータを記述する JSON Schema |
| `isConcurrencySafe()` | `boolean` | ツールを並行して呼び出せるかどうか |
| `isReadOnly()` | `boolean` | ツールが読み取り専用 / 副作用なしかどうか |
| `isExternalTool()` | `boolean` | `true` の場合、実行は外部へ委譲される([外部実行](#外部実行ツール) を参照) |
| `isStateInjected()` | `boolean` | `true` の場合、フレームワークは `AgentState` を追加パラメータとして注入する |
| `isMcp()` | `boolean` | ツールが MCP サーバー由来かどうか |
| `getMcpName()` | `String` | `isMcp()` が `true` のときの MCP サーバー名 |

実行フローおよびパーミッションシステムと統合するメソッド:

| メソッド | 必須 | 説明 |
|--------|----------|-------------|
| `checkPermissions(toolInput, context)` | はい | 実行前のランタイムパーミッションチェック。`Mono<PermissionDecision>` を返す |
| `matchRule(ruleContent, toolInput)` | 任意 | パーミッションシステム向けのカスタムルールマッチャー。`boolean` を返す |
| `generateSuggestions(toolInput)` | 任意 | 現在の呼び出しから提案ルールを生成する。`List<PermissionRule>` を返す |
| `callAsync(param)` | 任意 | ツールの実行。`Mono<ToolResultBlock>` を返す。外部ツールはこれを実装しない。 |

### 組み込みツール

AgentScope は現在、次の組み込みツールを同梱しています:

| ツール | 説明 | 読み取り専用 |
|------|-------------|-----------|
| `TodoTools.todoWrite` | 現在のセッションの構造化されたタスクリストを維持する(リスト全体を置き換えるセマンティクス) | いいえ |

使用方法:

```java
Toolkit toolkit = new Toolkit();
toolkit.registerTool(new io.agentscope.core.tool.builtin.TodoTools());
```

:::{note}
追加のツールグループやスキルが存在する場合、`Toolkit` は `reset_tools` メタツールと `load_skill_through_path` スキルビューアツールを自動的に登録します——手動でインスタンス化する必要はありません。[自己管理型ツール](#自己管理型ツール) と [スキル](#スキル) を参照してください。
:::

### カスタムツール(アノテーションベース)

最も軽量な方法は、通常のメソッドに `@Tool` と `@ToolParam` を付与し、`Toolkit#registerTool(Object)` を呼び出すことです。フレームワークは Java の型から JSON スキーマを、そしてエージェント向けの `description` を導出します。

```java
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class SimpleTools {

    @Tool(
            name = "get_current_time",
            description = "指定された IANA タイムゾーンにおける現在時刻を返します。",
            readOnly = true,
            concurrencySafe = true)
    public String getCurrentTime(
            @ToolParam(name = "timezone", description = "IANA タイムゾーン。例:Asia/Shanghai")
                    String timezone) {
        return LocalDateTime.now(ZoneId.of(timezone))
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new SimpleTools());
```

一般的な `@Tool` 属性:

| 属性 | 型 | 説明 |
|-----------|------|-------------|
| `name` | `String` | ツール名(デフォルトはメソッド名) |
| `description` | `String` | エージェントに表示される説明 |
| `readOnly` | `boolean` | ツールが読み取り専用かどうか(デフォルト `false`) |
| `concurrencySafe` | `boolean` | ツールが並行呼び出しに対して安全かどうか(デフォルト `false`) |
| `stateInjected` | `boolean` | `AgentState` を追加パラメータとして注入する(デフォルト `false`) |
| `dangerousFiles` / `dangerousDirectories` | `String[]` | カスタムの危険パスを追加する |
| `converter` | `Class<? extends ToolResultConverter>` | 戻り値を `ToolResultBlock` へ変換するカスタムコンバーター |

### カスタムツール(`ToolBase` を継承する)

カスタムのパーミッションポリシー、外部実行、あるいはより複雑なスキーマが必要な場合は、`ToolBase` を継承します:

```java
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

public class WebSearchTool extends ToolBase {

    public WebSearchTool() {
        super(
                ToolBase.builder()
                        .name("WebSearch")
                        .description("指定されたクエリについての情報をウェブで検索する。")
                        .inputSchema(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "query", Map.of(
                                                "type", "string",
                                                "description", "検索クエリ。")),
                                "required", List.of("query")))
                        .readOnly(true)
                        .concurrencySafe(true));
    }

    @Override
    public Mono<PermissionDecision> checkPermissions(
            Map<String, Object> toolInput, PermissionContextState context) {
        return Mono.just(PermissionDecision.allow("ウェブ検索は読み取り専用です。"));
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        String query = (String) param.getInput().get("query");
        return doSearchAsync(query)
                .map(text ->
                        ToolResultBlock.builder()
                                .id(param.getId())
                                .name(getName())
                                .output(List.of(TextBlock.builder().text(text).build()))
                                .build());
    }
}
```

### 外部実行ツール

外部実行ツールは、実際の作業をエージェントのランタイムの外部——典型的には人間のオペレーターや外部システム——に委譲します。エージェントは `RequireExternalExecutionEvent` を発行して一時停止します。次の呼び出しで対応する `ToolResultBlock` がフィードバックされると、エージェントは処理を続ける前に、同じ `replyId` を持つ `ExternalExecutionResultEvent` を発行します。

このパターンは [human-in-the-loop](./agent.md#human-in-the-loop) フローの基盤です——一部の操作には人間の承認や人間による実行が必要です。

外部ツールを作成するには、`externalTool` を `true` に設定し、`callAsync` の実装を省略します:

```java
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.ToolBase;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

public class HumanApprovalTool extends ToolBase {

    public HumanApprovalTool() {
        super(
                ToolBase.builder()
                        .name("HumanApproval")
                        .description("機密性の高い操作について人間の承認を要求する。")
                        .inputSchema(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "action", Map.of("type", "string"),
                                        "reason", Map.of("type", "string")),
                                "required", List.of("action", "reason")))
                        .readOnly(false)
                        .concurrencySafe(true)
                        .externalTool(true));
    }

    @Override
    public Mono<PermissionDecision> checkPermissions(
            Map<String, Object> toolInput, PermissionContextState context) {
        return Mono.just(PermissionDecision.allow("外部ツールのディスパッチは常に許可されます。"));
    }
}
```

実行可能な例: `agentscope-examples/documentation/.../tool/ToolBaseExample.java`、`tool/ToolExecutionContextExample.java`。

## コンテキストを受け取る

`agent.call(msgs, runtimeContext)` に渡された [`RuntimeContext`](./agent.md#runtimecontext-呼び出しごとのコンテキスト) は、その返信内のすべてのツール呼び出しへ転送されます。ツールはこれを2つの方法で読み取れます: アノテーションベースのツールは自動注入によって、`ToolBase.callAsync` は `ToolCallParam` によってです。

### 自動注入(`@Tool` メソッド)

`@Tool` メソッドの内部では、**`@ToolParam` の付いていない**パラメータはすべてフレームワークによる注入対象として扱われます。解決順序は次のとおりです:

| パラメータの型 | ソース |
|----------------|--------|
| `ToolEmitter` | ストリーミング用エミッター(未設定の場合は no-op) |
| `Agent` | 現在のエージェントインスタンス |
| `AgentState` | 現在の呼び出しにおけるセッション単位の状態(`RuntimeContext.getAgentState()` 経由) |
| `RuntimeContext` | 現在の呼び出しごとのコンテキスト |
| `ToolExecutionContext` | `runtimeContext.asToolExecutionContext()`(互換性のためのシム、非推奨) |
| その他のユーザー POJO 型 | `runtimeContext.get(ParamType.class)` —— つまり呼び出し側が `RuntimeContext.builder().put(ParamType.class, value)` を通じて登録したオブジェクト |

「ユーザー POJO」とは、`@ToolParam` が付いておらず、プリミティブ型でなく、`ContentBlock` / `Msg` でもなく、`java.*` / `javax.*` 配下でもないものを指します。それ以外のすべてのパラメータ(`@ToolParam` が付いているもの、または上記の型に当てはまらないもの)は、LLM が渡す JSON から名前で読み取られます。

```java
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

public record UserContext(String username, String locale) {}

public class PersonalizedTools {

    @Tool(name = "greet", description = "Greet the user with a custom greeting")
    public String greet(
            @ToolParam(name = "greeting", description = "Greeting word, e.g. 'Hello'")
                    String greeting,                  // ← モデルから供給される
            UserContext userCtx) {                    // ← フレームワークによって注入される
        return greeting + ", " + (userCtx == null ? "unknown" : userCtx.username()) + "!";
    }
}
```

呼び出し側は POJO を型ごとに一度登録するだけで、以降の各 `call` はそれを要求するすべてのツールへ、一致するインスタンスをルーティングします:

```java
RuntimeContext ctx =
        RuntimeContext.builder()
                .put(UserContext.class, new UserContext("alice", "en"))
                .userId("alice")
                .build();

agent.call(List.of(new UserMessage("Greet me.")), ctx).block();
```

モデルは `userCtx` を一切見ることがありません——ツールの JSON スキーマの一部ではないためです。完全な例: `agentscope-examples/documentation/.../tool/ToolExecutionContextExample.java`。

### `ToolBase.callAsync` でコンテキストにアクセスする

`ToolBase` を継承するツールは、`ToolCallParam` を通じてコンテキストを読み取ります:

```java
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;

public class TenantAwareTool extends ToolBase {

    public TenantAwareTool() {
        super(/* builder ... */);
    }

    @Override
    public Mono<io.agentscope.core.message.ToolResultBlock> callAsync(ToolCallParam param) {
        RuntimeContext rc = param.getRuntimeContext();
        String tenantId = rc != null ? rc.getUserId() : null;
        TenantConfig cfg = rc != null ? rc.get(TenantConfig.class) : null;
        // ... tenantId / cfg を適用する ...
    }
}
```

`ToolCallParam` は、`getAgent()`、`getInput()`、`getEmitter()`、`getToolUseBlock()`、および非推奨の `getContext()` も公開しています。新しいコードでは `getRuntimeContext()` を優先してください。

### hook とツール間の連携

`RuntimeContext` の文字列レイヤー(`put(String, Object)` / `get(String)`)は、1回の `call` の間だけ存在する、middleware とツールの間の短命なチャネルです——middleware は `onActing`/`onReasoning` で書き込み、`RuntimeContext` パラメータを注入するツールがそれを読み取ります。呼び出しが終わると、このインスタンスは(すべての hook とともに)エージェントから切り離されます。

## MCP

AgentScope は [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) と統合されており、エージェントが任意の MCP 互換ツールプロバイダーと通信できるようにします。フレームワークがプロトコルのネゴシエーション、ツールの発見、結果の変換を処理します。

3種類のトランスポートがサポートされています:

- **STDIO** —— 標準入出力(stdin/stdout)経由のローカルプロセス
- **SSE / Streamable HTTP** —— リモートの HTTP 長時間接続

MCP ツールは、衝突を避けるために `mcp__{server_name}__{tool_name}` という名前空間の下で Toolkit に公開されます。`readOnlyHint` が付いたツールは、パーミッションシステムによって自動的に許可されます。

### MCP ツールを登録する

`McpClientBuilder` を使って `McpClientWrapper` を構築し、それを `Toolkit` に登録します:

::::{tab-set}
:::{tab-item} STDIO
```java
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;

McpClientWrapper filesystem =
        McpClientBuilder.stdio()
                .name("filesystem")
                .command("mcp-server-filesystem")
                .args("--root", "/my/project")
                .build();

Toolkit toolkit = new Toolkit();
toolkit.registerMcpClient(filesystem).block();
```
:::
:::{tab-item} Streamable HTTP
```java
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;

McpClientWrapper weather =
        McpClientBuilder.streamableHttp()
                .name("weather")
                .url("https://api.weather.com/mcp")
                .header("Authorization", "Bearer xxx")
                .build();

Toolkit toolkit = new Toolkit();
toolkit.registerMcpClient(weather).block();
```
:::
:::{tab-item} SSE
```java
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;

McpClientWrapper search =
        McpClientBuilder.sse()
                .name("search")
                .url("https://api.search.com/mcp/sse")
                .build();

Toolkit toolkit = new Toolkit();
toolkit.registerMcpClient(search).block();
```
:::
::::

実行可能な例: `agentscope-examples/documentation/.../mcp/McpStdioExample.java`、`mcp/McpSseExample.java`、`mcp/McpStreamableHttpExample.java`。

## スキル

スキルは、新しいツールコードを書くことなくエージェントの能力を拡張する、Markdown ベースの指示セットです。各スキルは、フロントマターのメタデータと詳細な指示を含む `SKILL.md` ファイルを持つディレクトリです。

ツールとは異なり、スキルは直接呼び出すことができません。エージェントは `load_skill_through_path` という名前の自動登録されたビューアツールを通じてスキルの指示を読み取り、その後、既に持っているツールを使ってそれを実行します。

### スキルを登録する

1つ以上の `AgentSkillRepository` を `ReActAgent.builder().skillRepository(...)` を通じて直接アタッチします。`build()` の時点で、ビルダーは `DynamicSkillMiddleware` を自動的にインストールします。これは、設定されたソースから、`call()` のたびにスキルプロンプトとツールグループを再構築します:

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.skill.repository.FileSystemSkillRepository;
import java.nio.file.Paths;

ReActAgent agent =
        ReActAgent.builder()
                .name("SkillCreator")
                .sysPrompt("...")
                .model(model)
                .skillRepository(new FileSystemSkillRepository(Paths.get("/path/to/skills"), false))
                .build();
```

複数回の `skillRepository(...)` 呼び出しは順に追加されます(優先度は低→高)。2つのリポジトリが同じ名前のスキルを公開している場合、後の登録が優先されます。リストを丸ごと置き換えるには `skillRepositories(List<AgentSkillRepository>)` を使用してください。

参照実装: `agentscope-examples/documentation/.../skill/AgentSkillExample.java`、`skill/SkillWithToolGroupExample.java`。

### スキルの仕組み

スキルが存在する場合、`Toolkit` は2フェーズのセットアップを行います。

初期化:

- Toolkit は、登録されたすべてのスキルソースをスキャンし、各スキルの名前、説明、ディレクトリを収集します。
- 組み込みのビューアツール `load_skill_through_path`(`io.agentscope.core.skill.SkillToolFactory` に実装)を `skill-build-in-tools` グループに自動登録します。
- 利用可能なスキル(名前 + 説明)を列挙し、`load_skill_through_path` 経由で全文を読むようエージェントに指示する、システムプロンプトの断片を組み立てます。

ランタイムでは、エージェントは2つの必須引数を伴ってビューアを呼び出します:

| パラメータ | 型 | 説明 |
| --- | --- | --- |
| `skillId` | `string`(登録済みスキル ID の enum) | 読み込むスキル。 |
| `path` | `string` | スキルの Markdown 指示を取得するには `"SKILL.md"` を使うか、`"references/guide.md"` や `"scripts/run.py"` のようにスキルが宣言する正確なリソースパスを使います。`"."`、`"./"`、ディレクトリ、絶対パスは渡さないでください。 |

ツール呼び出しペイロードの例:

```json
{
  "name": "load_skill_through_path",
  "input": { "skillId": "pdf-extractor", "path": "SKILL.md" }
}
```

成功した呼び出しには、2つの効果があります:

1. 要求されたコンテンツ(`SKILL.md` の Markdown、または指定されたリソースファイル)を返します。
2. **スキルを有効化します** —— 関連するツールグループが `Toolkit` 内で有効化され、そのスキルにバンドルされたツールが、そのターンの残りの間呼び出し可能になります。要求された `path` が存在しない場合、ビューアはエラーを返し、利用可能なリソースパスの一覧(`SKILL.md` を先頭に)を提示するので、エージェントは再試行できます。

:::{note}
スキルはツールではありません——エージェントはそれを直接呼び出すことができません。エージェントはまず `load_skill_through_path` 経由で指示を読み、その後、他のツールを使ってそれを実行しなければなりません。
:::

### スキルのスクリプト実行:シェルツールを設定する

スキルは指示を提供するだけです——実際の実行は、エージェントが既に持っているツールに依存します。スキルの指示にスクリプトの実行(例:`scripts/run.py`)が含まれる場合、エージェントにはシェルアクセスが必要です:

- **`ReActAgent`** —— Toolkit に `ShellCommandTool` を登録します:

```java
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.coding.ShellCommandTool;
import io.agentscope.core.tool.file.ReadFileTool;
import io.agentscope.core.tool.file.WriteFileTool;

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new ShellCommandTool());
toolkit.registerTool(new ReadFileTool("/path/to/base/dir"));
toolkit.registerTool(new WriteFileTool("/path/to/base/dir"));

ReActAgent agent =
        ReActAgent.builder()
                .name("SkillAgent")
                .sysPrompt("...")
                .model(model)
                .toolkit(toolkit)
                .skillRepository(skillRepo)
                .build();
```

- **`HarnessAgent`** —— harness モジュールは、ワークスペースを意識したシェルツールおよびファイルツール(`execute`、`read_file`、`write_file` など)を標準で同梱しています。追加の登録は不要です。

### Skill + ToolGroup:オンデマンドのツール開示

`SkillToolGroup` は、ツールのグループをスキル名に紐づけます——エージェントがそのスキルをロードすると自動的にグループが有効化され、それ以外の場合はモデルのスキーマから隠されたままとなり、コンテキストのノイズを減らします。

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;

Toolkit toolkit = new Toolkit();

// 1. スキルに紐づくツールグループを作成する(初期状態は非アクティブ)
toolkit.createSkillToolGroup(
        "analysis-tools",                // グループ名
        "Data analysis tools",           // 説明
        false,                           // 初期状態は非アクティブ
        "data-analysis");                // 紐づくスキル名

// 2. そのグループへツールを登録する
toolkit.registration()
        .tool(new AnalysisTools())
        .group("analysis-tools")
        .apply();

// 3. モデル駆動のグループ切り替えのため、メタツール付きでエージェントを構築する
ReActAgent agent =
        ReActAgent.builder()
                .name("AnalysisAgent")
                .sysPrompt("...")
                .model(model)
                .toolkit(toolkit)
                .skillRepository(skillRepo)
                .enableMetaTool(true)
                .build();
```

エージェントが `load_skill_through_path` 経由で `data-analysis` スキルをロードすると、`analysis-tools` グループが有効化され、そのツールがただちに利用可能になります。`enableMetaTool(true)` を指定すると、モデルは `reset_tools` を通じてグループの有効化も自分で管理できます。

参照実装: `agentscope-examples/documentation/.../skill/SkillWithToolGroupExample.java`。

## 自己管理型ツール

組み込みの **メタツール**(`reset_tools`)を使うと、エージェントはランタイムにどのツールグループをアクティブにするかを自ら管理でき、コンテキストを絞り込んだ状態に保てます——現在のタスクに関連するツールだけがモデルに公開されます。

### ツールグループを定義する

`ToolGroup` は、ツール / MCP クライアント / スキルの名前付きの束です。グループを `Toolkit` に登録し、ビルダーを通じてメタツールを有効にします:

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolGroup;
import io.agentscope.core.tool.ToolGroupScope;

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new BasicTools());

ToolGroup database =
        new ToolGroup(
                "database",
                "Tools for database operations.",
                ToolGroupScope.SESSION,
                /* active = */ false);
database.addTool("db_query");
database.addTool("db_migrate");
toolkit.registerTool(new DatabaseTools());
toolkit.registerToolGroup(database);

ToolGroup deployment =
        new ToolGroup(
                "deployment",
                "Tools for deploying services.",
                ToolGroupScope.SESSION,
                /* active = */ false);
deployment.addTool("deploy");
deployment.addTool("rollback");
toolkit.registerTool(new DeploymentTools());
toolkit.registerToolGroup(deployment);

ReActAgent agent =
        ReActAgent.builder()
                .name("router")
                .toolkit(toolkit)
                .enableMetaTool(true)
                .build();
```

`ToolGroup` は、名前、説明、スコープ(`ToolGroupScope`)、初期のアクティブフラグを受け取ります。予約名 `"basic"` は `Toolkit#registerTool(Object)` によって自動的に投入され、常にアクティブです。

### メタツールを使う

basic 以外のツールグループが少なくとも1つ存在し、`enableMetaTool(true)` が有効な場合、`Toolkit` は `reset_tools` を自動登録し、そのスキーマをエージェントに公開します。basic 以外の各グループは boolean フィールドになります。メタツールを呼び出すことで、望ましい最終状態を宣言します。

ランタイムでの振る舞い:

- `"basic"` グループのツールは常に公開されます。メタツールはそれらに関与しません。
- `reset_tools` の呼び出しはそれぞれ、アクティブな集合を**完全に上書き**します——明示的に `true` に設定されなかった basic 以外のグループは、以前の状態に関わらず非アクティブ化されます。
- 新たにアクティブになった各グループについて、その説明と(提供されていれば)指示がメタツールの戻り値に組み込まれ、正しい使い方をエージェントに伝えます。
- 非アクティブなグループのツールはエージェントのツールスキーマに現れず、アクティブなツールセットのためのコンテキストをより多く残します。

:::{warning}
メタツールの入力は、差分ではなく、すべてのグループの**最終状態**を表します。明示的に `true` に設定されなかったグループは、以前の状態に関わらず非アクティブ化されます。
:::

## さらに読む

::::{grid} 2

:::{grid-item-card} エージェント
:link: ./agent.html

エージェントが ReAct ループの中でどのようにツール呼び出しをオーケストレーションするか
:::
  :::{grid-item-card} パーミッションシステム
:link: ./permission-system.html

どのツールが、いつ実行されるかをきめ細かく制御する
:::
  :::{grid-item-card} Middleware
:link: ./middleware.html

オニオン型の middleware を使ってツール呼び出しをインターセプトし、書き換える
:::
  :::{grid-item-card} Human-in-the-Loop
:link: ./agent.html#human-in-the-loop

外部実行ツールと承認ワークフロー
:::

::::
