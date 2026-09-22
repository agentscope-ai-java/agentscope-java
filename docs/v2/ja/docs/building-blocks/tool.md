---
title: ツール
description: エージェントが呼び出せる能力を定義・登録・管理する
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

AgentScope には 2 系統の組み込みツールがあり、エージェントへの届き方が異なります。

**コア組み込み**は `agentscope-core` にあり、自分で登録します:

| ツール | パラメータ | 読み取り専用 |
|--------|------------|--------------|
| `todo_write` | `todos`(`List<TodoItem>`、必須) — **完全な**更新後リスト。既存のリストを丸ごと置き換える | いいえ |

```java
Toolkit toolkit = new Toolkit();
toolkit.registerTool(new io.agentscope.core.tool.builtin.TodoTools());
```

**Harness 組み込み**は `agentscope-harness` にあり、`HarnessAgent` が自動で登録します。有効化ではなく無効化する対象です([設定](/v2/ja/docs/harness/configuration#組み込み機能を切る)を参照)。

ファイルシステムツール(`FilesystemTool`、`disableFilesystemTools()` で除去):

| ツール | パラメータ | 読み取り専用 |
|--------|------------|--------------|
| `read_file` | `path`(必須)· `offset`(`Integer`、既定 `0`)· `limit`(`Integer`、既定 `0` = 全行) | はい |
| `write_file` | `path`(必須)· `content`(必須) — 親ディレクトリを自動作成 | いいえ |
| `edit_file` | `path` · `old_string` · `new_string`(すべて必須)· `replace_all`(`Boolean`、既定 `false`) — `replace_all` でない限り `old_string` は一意である必要がある | いいえ |
| `list_files` | `path`(必須) | はい |
| `glob_files` | `pattern`(必須、例 `**/*.java`)· `path`(基準ディレクトリ)· `limit`(`Integer`、既定 200) | はい |
| `grep_files` | `pattern`(必須、リテラル文字列)· `path` · `glob`(例 `*.java`)· `limit`(`Integer`、既定 100) | はい |

シェルツール(`ShellExecuteTool`、`disableShellTool()` で除去):

| ツール | パラメータ | 読み取り専用 |
|--------|------------|--------------|
| `execute` | `command`(必須)· `working_directory`(ワークスペースルートからの相対)· `timeout`(`Integer`、秒、既定 `30`) | いいえ |

Web ツール(`WebTools`、`disableWebTools()` で除去):

| ツール | パラメータ | 読み取り専用 |
|--------|------------|--------------|
| `web_fetch` | `url`(必須)· `max_chars`(`Integer`、既定 `20000`) | はい |
| `web_search` | `query`(必須)· `max_results`(`Integer`、既定 `5`) | はい |

メモリツール(`disableMemoryTools()` で除去): `memory_search`、`memory_get`、`memory_save`、`session_search` — [メモリ](/v2/ja/docs/harness/memory)を参照。

<Note>

シェルツールの名前は `execute_shell_command` ではなく `execute` です。`@Tool` アノテーションが `name` を設定していないため、ツール名が Java のメソッド名にフォールバックします。権限ルールや `tools.json` の許可/拒否リストではこの名前を使ってください。

</Note>

<Note>

追加のツールグループやスキルがある場合、`Toolkit` は `reset_tools` メタツールと `load_skill_through_path` スキルビューアツールを自動登録します。手動でインスタンス化する必要はありません。[自己管理型ツール](#自己管理型ツール)と [Skill](#スキル)を参照してください。

</Note>

### `Toolkit` にツールを登録する

エージェントが呼べるものはすべて `Toolkit` に登録し、それをエージェントの builder に渡します。もっとも一般的なのは `registerTool(Object)` で、オブジェクト上の `@Tool` メソッドをリフレクションで走査します。このほか toolkit は、組み立て済みのツールインスタンス、スキーマのみの外部ツール、MCP クライアント、ツールグループも受け付けます。

```java
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.builtin.TodoTools;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.harness.agent.HarnessAgent;

Toolkit toolkit = new Toolkit();

// 1. オブジェクト上のアノテーション付きメソッド — @Tool 1 つにつき 1 ツール
toolkit.registerTool(new MyDomainTools());
toolkit.registerTool(new TodoTools());

// 2. ToolBase のサブクラスを、単一のツールインスタンスとして登録
toolkit.registerAgentTool(new WebSearchTool());

// 3. MCP サーバー — そのサーバーが公開する全ツールを登録
McpClientWrapper amap =
        McpClientBuilder.streamableHttp()
                .name("amap")
                .url("https://mcp.amap.com/mcp?key=" + System.getenv("AMAP_API_KEY"))
                .build();
toolkit.registerMcpClient(amap).block();

HarnessAgent agent =
        HarnessAgent.builder()
                .name("assistant")
                .sysPrompt("You are a helpful assistant.")
                .model("dashscope:qwen-plus")
                .toolkit(toolkit)
                .build();
```

| メソッド | 登録されるもの |
|----------|----------------|
| `registerTool(Object)` | オブジェクト上で見つかった `@Tool` 付きメソッドすべて |
| `registerAgentTool(AgentTool)` | `AgentTool` / `ToolBase` のインスタンスを直接 1 つ |
| `registerSchema(ToolSchema)` | スキーマのみの外部ツール 1 つ。エージェントからは見え、実行は外部 worker 待ちで中断する |
| `registerSchemas(List<ToolSchema>)` | スキーマのみの外部ツールをまとめて |
| `registerMcpClient(McpClientWrapper)` | MCP サーバーが公開する全ツール。`Mono<Void>` を返すので `block()` するかチェーンする |
| `registerMetaTool()` | エージェント管理のツールグループ用 `reset_tools` メタツール |

<Note>

`registerMcpClient` は非同期です。`block()`(または購読)せずに呼ぶと `build()` 時点で MCP ツールが未登録のままとなり、エージェントはそれらを持たないまま黙って起動します。

</Note>

#### 登録後のツール管理

ツールグループを使うと、一度に toolkit の一部だけを公開でき、モデルが見るスキーマを小さく保てます。登録は一方通行ではなく、エージェントの稼働中でもツールやグループを追加・削除できます:

| メソッド | 効果 |
|----------|------|
| `createToolGroup(name, description)` | グループを作成(既定で有効) |
| `createToolGroup(name, description, active)` | 初期の有効状態を明示してグループを作成 |
| `registerToolGroup(ToolGroup)` | 組み立て済みの `ToolGroup` インスタンスやサブクラスを登録 |
| `addToolToGroup(groupName, toolName)` | 登録済みツールをグループへ移す |
| `setActiveGroups(List<String>)` | 現在有効なグループ集合を置き換える |
| `removeToolGroups(List<String>)` | グループとその中の全ツールを削除 |
| `removeTool(String)` | 名前を指定してツールを 1 つ削除 |
| `removeToolIfSame(String, AgentTool)` | 登録済みインスタンスが期待どおりのときだけ削除 — 複数コンポーネントが toolkit を共有する場合の安全な形 |
| `removeMcpClient(String)` | MCP サーバーとその全ツールを削除。`Mono<Void>` を返す |

エージェント自身にグループを切り替えさせる方法は[自己管理型ツール](#自己管理型ツール)を参照してください。

#### 登録内容を確認する

エージェントに実際に何が渡るのかを確かめたいとき — ヘルスチェック、起動時アサーション、テスト — toolkit を読み戻せます:

```java
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.AgentTool;
import java.util.List;
import java.util.Set;

Set<String> names = toolkit.getToolNames();
System.out.println("registered: " + names);

// モデルが実際に受け取るスキーマ。有効なツールグループで絞り込まれる
List<ToolSchema> schemas = toolkit.getToolSchemas();
for (ToolSchema schema : schemas) {
    System.out.println(schema.getName() + " -> " + schema.getParameters());
}

// 期待したツールが登録されていなければ起動時に落とす
// (registerMcpClient の block() 忘れでよく起きる)
if (!names.contains("amap_maps_geo")) {
    throw new IllegalStateException("MCP tools missing: " + names);
}

AgentTool tool = toolkit.getTool("read_file");
```

| メソッド | 戻り値 |
|----------|--------|
| `getToolNames()` | `Set<String>` — 登録済みツールすべての名前 |
| `getTool(String)` | `AgentTool` — 名前で 1 つ取得 |
| `getToolSchemas()` | `List<ToolSchema>` — モデルへ送られるスキーマ。toolkit の現在有効なグループで絞り込まれる |
| `getToolSchemas(Collection<String>)` | `List<ToolSchema>` — 同じだが、明示的に渡したグループ集合で絞り込む。toolkit の共有有効フラグを無視するステートレスな呼び出し単位の版 |
| `getActiveGroups()` | `List<String>` — 現在有効なツールグループ名 |

各 `ToolSchema` は `getName()`、`getDescription()`、`getParameters()`(JSON Schema の map)、`getOutputSchema()`、`getStrict()` を公開します。

<Tip>

`getToolSchemas()` は「モデルが何を見ているか」の唯一の真実です。登録したのに一度も呼ばれないツールがあれば、これを出力して[パラメータスキーマの規則](#パラメータスキーマtoolparam)と突き合わせてください。原因はたいてい `Map` パラメータか `@ToolParam` の付け忘れです。

</Tip>

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

### パラメータスキーマ(`@ToolParam`)

ツールの JSON スキーマに含まれるのは、`@ToolParam` を付けたパラメータだけです。メソッドシグネチャ上のそれ以外のパラメータは、フレームワークによって自動注入される(`ToolEmitter`、`Agent`、`AgentState`、`RuntimeContext`)か、runtime context から解決されるもので、モデルに送られるスキーマには現れません。[コンテキストを受け取る](#コンテキストを受け取る)を参照してください。

| 属性 | 型 | 既定値 | 説明 |
|------|----|--------|------|
| `name` | `String` | *必須* | スキーマ上のプロパティ名。Java は実行時にパラメータ名を保持しないため必須。LLM 互換性のため snake_case を推奨 |
| `description` | `String` | `""` | プロパティの `description` に書き出される。空の場合はスキーマに含まれない |
| `required` | `boolean` | `true` | そのプロパティをスキーマの `required` 配列に載せるかどうか |

#### Java の型から JSON スキーマへの対応

スキーマはパラメータの**ジェネリック**型(`Parameter#getParameterizedType()`)から導出されるため、コレクションの型引数は消去されずに保持されます。

| Java のパラメータ型 | 生成されるプロパティスキーマ |
|---------------------|------------------------------|
| `String` | `{"type": "string"}` |
| `int`、`Integer`、`long`、`Long` | `{"type": "integer"}` |
| `double`、`Double`、`float` | `{"type": "number"}` |
| `boolean`、`Boolean` | `{"type": "boolean"}` |
| `MyEnum` | `{"type": "string", "enum": ["A", "B"]}` — 列挙定数名 |
| `String[]`、`List<String>`、`Set<String>` | `{"type": "array", "items": {"type": "string"}}` |
| `List<Item>` | `{"type": "array", "items": {...}}`。`items` は `Item` のオブジェクトスキーマ |
| `List<List<String>>` | `items` 自体が `string` の `array` である `array` |
| `Map<String, Integer>` | `{"type": "object"}` — 下記の注意を参照 |
| `Item`(POJO) | `Item` のフィールドから構築した `{"type": "object", "properties": { … }}` |

`List<String>` を 2 つ(必須と任意)受け取るツール:

```java
@Tool(name = "tag_files", description = "Attach tags to a set of files.")
public String tagFiles(
        @ToolParam(name = "paths", description = "Absolute file paths to tag")
                List<String> paths,
        @ToolParam(name = "tags", description = "Tags to attach", required = false)
                List<String> tags) {
    // Implementation
}
```

は次を生成します:

```json
{
  "type": "object",
  "properties": {
    "paths": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Absolute file paths to tag"
    },
    "tags": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Tags to attach"
    }
  },
  "required": ["paths"]
}
```

`description` が付くのは配列プロパティ自身であって、`items` ではない点に注意してください。要素を説明したい場合は、要素型のフィールド側に説明を書きます([POJO フィールドへの `@ToolParam`](#pojo-フィールドへの-toolparam)を参照)。

<Warning>

`Map<K, V>` のパラメータは素の `{"type": "object"}` になります。キーと値の型は**記述されない**ため、中身についてモデルに与えられる手がかりはなく、検証も行われません。構造が分かっているなら、`Map` ではなく POJO パラメータ、あるいは小さな POJO の `List` を受け取ってください。

</Warning>

#### ネストした型と `$defs`

ネストした POJO はプロパティスキーマにインライン展開されます。複数回参照される型や再帰的な型は、代わりに `$defs` のエントリとして出力され、プロパティからは `$ref` で参照されます。各パラメータのスキーマは独立に生成されるため、AgentScope はそれらの定義をパラメータ階層からツールスキーマのルートへ引き上げ、`#/$defs/TypeName` のポインタがドキュメントルートを基準に解決できるようにします:

```json
{
  "type": "object",
  "properties": {
    "recipe": {
      "type": "object",
      "properties": {
        "materials": { "type": "array", "items": { "$ref": "#/$defs/Material" } },
        "substitutes": { "type": "array", "items": { "$ref": "#/$defs/Material" } }
      }
    }
  },
  "required": ["recipe"],
  "$defs": {
    "Material": { "type": "object", "properties": { "name": { "type": "string" } } }
  }
}
```

定義のキーは単純型名なので、単純名が同じ**別の**クラス(たとえば異なるパッケージの `Material`)が同じツールメソッドに現れると衝突し、スキーマ生成は `IllegalStateException: Conflicting schema definition found for key: Material` で失敗します。どちらかをリネームするか、両方を 1 つの POJO パラメータにまとめてください。

#### `required` がスキーマに与える影響

`required` はパラメータがスキーマに現れるかどうかを決めるものでは**ありません**。`@ToolParam` を付けたパラメータは常に `properties` に列挙されます。`required` が決めるのは、スキーマ最上位の `required` 配列に載るかどうかだけです:

- `required = true`(既定) — プロパティ名が `required` に追加されます。
- `required = false` — プロパティは `properties` に残りますが `required` には入らないため、モデルは省略できます。
- 必須のパラメータが 1 つもない場合、`required` キーは空配列としてではなく**まるごと省略**されます。

呼び出し時、`ToolExecutor` はメソッドを呼ぶ前にモデルの引数をこのスキーマで検証します。必須プロパティが欠けた呼び出しは拒否され、検証エラーがモデルに返されて再試行となるため、メソッドには一切入りません。任意プロパティに明示的に渡された `null` は、省略されたのと同じに扱われます。

<Warning>

省略された任意パラメータはメソッドに `null` として渡されます。任意パラメータはプリミティブ型ではなく**ボックス化**された型(`Integer`、`Double`、`Boolean`)で宣言してください。`required = false` を付けたプリミティブ型のパラメータは、モデルがそれを省略すると呼び出し時に失敗します。`null` を `int` や `double` に渡せないためです。

</Warning>

#### POJO フィールドへの `@ToolParam`

`@ToolParam` は POJO パラメータのフィールドにも適用でき、プロパティ名の変更、説明の付与、必須かどうかの指定を行います:

```java
public class Location {

    @ToolParam(name = "city_name", description = "The city name")
    private String city;

    @ToolParam(name = "zip_code", description = "The zip code", required = false)
    private String zip;

    private String country; // アノテーションなし → 任意、フィールド名のまま
}
```

```json
{
  "type": "object",
  "properties": {
    "city_name": { "type": "string", "description": "The city name" },
    "zip_code": { "type": "string", "description": "The zip code" },
    "country": { "type": "string" }
  },
  "required": ["city_name"]
}
```

メソッドパラメータとの違いが 2 つあります:

- `@ToolParam` の無いフィールドもスキーマには含まれ、Java のフィールド名のまま**任意**プロパティになります。一方、`@ToolParam` の無いメソッドパラメータはスキーマからまったく除外されます。
- `name` が空ならフィールド名にフォールバックし、`description` が空ならスキーマに書かれません。

フィールドでは Jackson の `@JsonPropertyDescription` と `@JsonProperty(required = true)` も尊重されるため、既存の Jackson アノテーション付きモデルは付け直すことなくツールパラメータとして使えます。

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

このパターンは [human-in-the-loop](/v2/ja/docs/building-blocks/agent#human-in-the-loop) フローの基盤です——一部の操作には人間の承認や人間による実行が必要です。

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

`agent.call(msgs, runtimeContext)` に渡された [`RuntimeContext`](/v2/ja/docs/building-blocks/agent#runtimecontext-呼び出しごとのコンテキスト) は、その返信内のすべてのツール呼び出しへ転送されます。ツールはこれを2つの方法で読み取れます: アノテーションベースのツールは自動注入によって、`ToolBase.callAsync` は `ToolCallParam` によってです。

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

<Tabs>

<Tab title="STDIO">

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

</Tab>
<Tab title="Streamable HTTP">

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

</Tab>
<Tab title="SSE">

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

</Tab>

</Tabs>

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

<Note>

スキルはツールではありません——エージェントはそれを直接呼び出すことができません。エージェントはまず `load_skill_through_path` 経由で指示を読み、その後、他のツールを使ってそれを実行しなければなりません。

</Note>

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

<Warning>

メタツールの入力は、差分ではなく、すべてのグループの**最終状態**を表します。明示的に `true` に設定されなかったグループは、以前の状態に関わらず非アクティブ化されます。

</Warning>

## さらに読む

<CardGroup cols={2}>


<Card title="エージェント" href="/v2/ja/docs/building-blocks/agent">


エージェントが ReAct ループの中でどのようにツール呼び出しをオーケストレーションするか

</Card>
  <Card title="パーミッションシステム" href="/v2/ja/docs/building-blocks/permission-system">


どのツールが、いつ実行されるかをきめ細かく制御する

  </Card>
  <Card title="Middleware" href="/v2/ja/docs/building-blocks/middleware">


オニオン型の middleware を使ってツール呼び出しをインターセプトし、書き換える

  </Card>
  <Card title="Human-in-the-Loop" href="/v2/ja/docs/building-blocks/agent#human-in-the-loop">


外部実行ツールと承認ワークフロー

  </Card>


</CardGroup>
