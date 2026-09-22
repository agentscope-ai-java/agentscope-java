---
title: Tool
description: agent가 호출할 수 있는 능력을 정의, 등록, 관리하기
---

## 개요

도구(Tool)는 agent가 세상에 작용하는 방식이다 — 비즈니스 작업을 실행하고, API를 호출하고, 데이터를 읽고 쓴다. 각 도구는 JSON Schema 형태로 자신을 LLM에 노출하며, agent는 통일된 인터페이스를 통해 이를 호출한다.

AgentScope는 도구 관련 구성 요소를 세 가지 개념으로 조직한다.

- **Tool** — `AgentTool` 계약(contract)을 구현하는(보통 `ToolBase`를 확장해서) 모든 객체, 또는 메서드에 `@Tool` 애너테이션이 붙은 일반 클래스. Java에서는 후자를 *reflective function tool*이라고 부르며, `Toolkit#registerTool(Object)`가 리플렉션을 통해 자동으로 등록한다.
- **Toolkit** — 도구, MCP 클라이언트, skill을 등록하고 그 JSON schema를 모델에 노출하며, 각 도구 호출을 해당 도구 객체로 전달하는 컨테이너.
- **Tool Group** — 이름이 붙은 도구 / MCP 클라이언트 / skill의 묶음으로, 하나의 단위로 활성화하거나 비활성화할 수 있다. agent는 내장 meta tool을 사용해 런타임에 그룹을 전환하며, 이를 통해 컨텍스트를 집중된 상태로 유지한다.

```java
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.builtin.TodoTools;

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new TodoTools());
toolkit.registerTool(new MyCustomTools());
```

`registerTool(Object)`만 호출하면, 등록된 객체 위의 모든 `@Tool` 메서드는 예약된 `"basic"` 그룹에 합류한다 — 항상 활성 상태다. agent를 더 확장하려면 MCP 클라이언트, tool group, skill을 추가하면 된다 — 아래 절을 참고한다.

## Java 도구

Java 도구는 `AgentTool` 계약을 만족하는 모든 객체다. AgentScope는 명시적인 파라미터 스키마로 도구를 선언하기 위한 추상 기반 클래스 `ToolBase`와, 일반 메서드를 도구로 감싸는 리플렉션 어댑터를 함께 제공한다.

### AgentTool / ToolBase 계약

`ToolBase`는 `AgentTool`의 추상 구현체다. 아래 표는 그 속성과 메서드를 나열한다.

agent와 런타임에 노출되는 속성:

| 메서드 | 타입 | 설명 |
|--------|------|-------------|
| `getName()` | `String` | agent에 표시되는 도구 이름 |
| `getDescription()` | `String` | agent에 표시되는 설명 |
| `getParameters()` | `Map<String, Object>` | 파라미터를 기술하는 JSON Schema |
| `isConcurrencySafe()` | `boolean` | 도구를 동시에 호출할 수 있는가? |
| `isReadOnly()` | `boolean` | 도구가 읽기 전용/부작용이 없는가? |
| `isExternalTool()` | `boolean` | `true`이면 실행이 외부로 위임된다(자세한 내용은 [외부 실행 도구](#외부-실행-도구) 참고) |
| `isStateInjected()` | `boolean` | `true`이면 프레임워크가 `AgentState`를 추가 파라미터로 주입한다 |
| `isMcp()` | `boolean` | 도구가 MCP 서버로부터 왔는가? |
| `getMcpName()` | `String` | `isMcp()`가 `true`일 때 해당 MCP 서버 이름 |

실행 흐름 및 권한 시스템과 연동되는 메서드:

| 메서드 | 필수 여부 | 설명 |
|--------|----------|------|
| `checkPermissions(toolInput, context)` | 필수 | 실행 전 런타임 권한 검사; `Mono<PermissionDecision>`을 반환 |
| `matchRule(ruleContent, toolInput)` | 선택 | 권한 시스템을 위한 커스텀 규칙 매처; `boolean`을 반환 |
| `generateSuggestions(toolInput)` | 선택 | 현재 호출로부터 제안된 규칙을 생성; `List<PermissionRule>`을 반환 |
| `callAsync(param)` | 선택 | 도구 실행 로직; `Mono<ToolResultBlock>`을 반환. 외부 도구는 이를 구현하지 않는다. |

### 내장 도구

AgentScope는 현재 다음의 내장 도구를 제공한다.

| Tool | 설명 | 읽기 전용 |
|------|-------------|-----------|
| `TodoTools.todoWrite` | 현재 세션을 위한 구조화된 작업 목록을 유지(전체 목록 교체 방식) | 아니요 |

사용 방법:

```java
Toolkit toolkit = new Toolkit();
toolkit.registerTool(new io.agentscope.core.tool.builtin.TodoTools());
```

<Note>

`Toolkit`은 추가 tool group이나 skill이 존재할 때 `reset_tools` meta tool과 skill 뷰어 도구인 `load_skill_through_path`를 자동으로 등록한다 — 직접 인스턴스화할 필요가 없다. [자체 관리 도구](#자체-관리-도구)와 [Skill](#skill)을 참고한다.

</Note>

### `Toolkit`에 도구 등록하기

에이전트가 호출할 수 있는 것은 모두 `Toolkit`에 등록한 뒤 에이전트 builder에 넘깁니다. 가장 흔한 것은 `registerTool(Object)`로, 객체에서 `@Tool` 메서드를 리플렉션으로 훑습니다. 그 밖에 toolkit은 미리 만들어 둔 도구 인스턴스, 스키마만 있는 외부 도구, MCP 클라이언트, tool group도 받습니다.

```java
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.builtin.TodoTools;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.harness.agent.HarnessAgent;

Toolkit toolkit = new Toolkit();

// 1. 객체의 애너테이션 메서드 — @Tool 하나당 도구 하나
toolkit.registerTool(new MyDomainTools());
toolkit.registerTool(new TodoTools());

// 2. ToolBase 서브클래스를 단일 도구 인스턴스로 등록
toolkit.registerAgentTool(new WebSearchTool());

// 3. MCP 서버 — 서버가 노출하는 모든 도구를 등록
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

| 메서드 | 등록되는 것 |
|--------|-------------|
| `registerTool(Object)` | 객체에서 찾은 모든 `@Tool` 애너테이션 메서드 |
| `registerAgentTool(AgentTool)` | `AgentTool` / `ToolBase` 인스턴스 하나를 직접 |
| `registerSchema(ToolSchema)` | 스키마만 있는 외부 도구 하나 — 에이전트에는 보이고, 실행은 외부 워커를 기다리며 중단됨 |
| `registerSchemas(List<ToolSchema>)` | 스키마만 있는 외부 도구를 한 번에 여러 개 |
| `registerMcpClient(McpClientWrapper)` | MCP 서버가 노출하는 모든 도구. `Mono<Void>`를 반환하므로 `block()`하거나 체이닝 |
| `registerMetaTool()` | 에이전트가 tool group을 관리하게 하는 `reset_tools` 메타 도구 |

<Note>

`registerMcpClient`는 비동기입니다. `block()`(또는 구독) 없이 호출하면 `build()` 시점에 MCP 도구가 등록되지 않은 상태가 되고, 에이전트는 그 도구들 없이 조용히 시작됩니다.

</Note>

#### 등록 이후의 도구 관리

tool group을 쓰면 한 번에 toolkit의 일부만 노출할 수 있어 모델이 보는 스키마를 작게 유지할 수 있습니다. 등록은 단방향이 아니며, 에이전트가 도는 중에도 도구와 그룹을 추가·제거할 수 있습니다:

| 메서드 | 효과 |
|--------|------|
| `createToolGroup(name, description)` | 그룹 생성(기본 활성) |
| `createToolGroup(name, description, active)` | 초기 활성 상태를 명시해 그룹 생성 |
| `registerToolGroup(ToolGroup)` | 미리 만든 `ToolGroup` 인스턴스나 서브클래스를 등록 |
| `addToolToGroup(groupName, toolName)` | 이미 등록된 도구를 그룹으로 옮김 |
| `setActiveGroups(List<String>)` | 현재 활성 그룹 집합을 교체 |
| `removeToolGroups(List<String>)` | 그룹과 그 안의 모든 도구를 제거 |
| `removeTool(String)` | 이름으로 도구 하나 제거 |
| `removeToolIfSame(String, AgentTool)` | 등록된 인스턴스가 기대한 것일 때만 제거 — 여러 컴포넌트가 toolkit을 공유할 때의 안전한 형태 |
| `removeMcpClient(String)` | MCP 서버와 그 모든 도구를 제거. `Mono<Void>` 반환 |

에이전트가 스스로 그룹을 바꾸게 하려면 [자체 관리 도구](#자체-관리-도구)를 참고하세요.

### 커스텀 도구(애너테이션 기반)

가장 가벼운 방법: 일반 메서드에 `@Tool`과 `@ToolParam`을 붙이고 `Toolkit#registerTool(Object)`를 호출한다. 프레임워크는 Java 타입으로부터 JSON schema를, `description`으로부터 agent용 설명을 도출한다.

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
            description = "Returns the current time in a given IANA timezone.",
            readOnly = true,
            concurrencySafe = true)
    public String getCurrentTime(
            @ToolParam(name = "timezone", description = "IANA timezone, e.g. Asia/Shanghai")
                    String timezone) {
        return LocalDateTime.now(ZoneId.of(timezone))
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new SimpleTools());
```

`@Tool`의 공통 속성:

| 속성 | 타입 | 설명 |
|-----------|------|-------------|
| `name` | `String` | 도구 이름(기본값은 메서드 이름) |
| `description` | `String` | agent에 표시되는 설명 |
| `readOnly` | `boolean` | 도구가 읽기 전용인지 여부(기본값 `false`) |
| `concurrencySafe` | `boolean` | 동시 호출에 안전한지 여부(기본값 `false`) |
| `stateInjected` | `boolean` | `AgentState`를 추가 파라미터로 주입할지 여부(기본값 `false`) |
| `dangerousFiles` / `dangerousDirectories` | `String[]` | 커스텀 위험 경로 추가 |
| `converter` | `Class<? extends ToolResultConverter>` | 반환 값을 `ToolResultBlock`으로 변환하는 커스텀 변환기 |

### 파라미터 스키마(`@ToolParam`)

도구의 JSON 스키마에 들어가는 것은 `@ToolParam`이 붙은 파라미터뿐입니다. 메서드 시그니처의 나머지 파라미터는 프레임워크가 주입하거나(`ToolEmitter`, `Agent`, `AgentState`, `RuntimeContext`) runtime context에서 해석되는 값이며, 모델에 전달되는 스키마에는 나타나지 않습니다. [컨텍스트 받기](#컨텍스트-받기)를 참고하세요.

| 속성 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `name` | `String` | *필수* | 스키마상의 프로퍼티 이름. Java가 런타임에 파라미터 이름을 보존하지 않으므로 필수이며, LLM 호환성을 위해 snake_case를 권장 |
| `description` | `String` | `""` | 프로퍼티의 `description`에 기록됨. 비어 있으면 스키마에 포함되지 않음 |
| `required` | `boolean` | `true` | 해당 프로퍼티를 스키마의 `required` 배열에 넣을지 여부 |

#### Java 타입에서 JSON 스키마로

스키마는 파라미터의 **제네릭** 타입(`Parameter#getParameterizedType()`)에서 생성되므로, 컬렉션의 타입 인자는 소거되지 않고 그대로 유지됩니다.

| Java 파라미터 타입 | 생성되는 프로퍼티 스키마 |
|--------------------|--------------------------|
| `String` | `{"type": "string"}` |
| `int`, `Integer`, `long`, `Long` | `{"type": "integer"}` |
| `double`, `Double`, `float` | `{"type": "number"}` |
| `boolean`, `Boolean` | `{"type": "boolean"}` |
| `MyEnum` | `{"type": "string", "enum": ["A", "B"]}` — 열거 상수 이름 |
| `String[]`, `List<String>`, `Set<String>` | `{"type": "array", "items": {"type": "string"}}` |
| `List<Item>` | `{"type": "array", "items": {...}}`, 여기서 `items`는 `Item`의 객체 스키마 |
| `List<List<String>>` | `items`가 다시 `string`의 `array`인 `array` |
| `Map<String, Integer>` | `{"type": "object"}` — 아래 주의 사항 참고 |
| `Item`(POJO) | `Item`의 필드로 구성한 `{"type": "object", "properties": { … }}` |

`List<String>` 파라미터 두 개(하나는 필수, 하나는 선택)를 받는 도구:

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

은 다음을 생성합니다:

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

`description`은 `items`가 아니라 배열 프로퍼티 자체에 붙는다는 점에 유의하세요. 요소를 설명하려면 요소 타입의 필드 쪽에 설명을 두면 됩니다([POJO 필드의 `@ToolParam`](#pojo-필드의-toolparam) 참고).

<Warning>

`Map<K, V>` 파라미터는 아무 정보 없는 `{"type": "object"}`가 됩니다. 키와 값의 타입은 **기술되지 않으므로** 모델은 무엇을 담아야 할지 알 수 없고 검증도 이뤄지지 않습니다. 구조를 알고 있다면 `Map` 대신 POJO 파라미터나 작은 POJO의 `List`를 받으세요.

</Warning>

#### 중첩 타입과 `$defs`

중첩된 POJO는 프로퍼티 스키마 안에 인라인으로 펼쳐집니다. 두 번 이상 참조되는 타입이나 재귀 타입은 대신 `$defs` 항목으로 생성되고, 프로퍼티는 `$ref`로 이를 가리킵니다. 각 파라미터의 스키마는 독립적으로 생성되므로, AgentScope는 이 정의들을 파라미터 수준에서 도구 스키마의 루트로 끌어올려 `#/$defs/TypeName` 포인터가 문서 루트를 기준으로 해석되도록 합니다:

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

정의 키는 단순 타입 이름이므로, 단순 이름이 같은 **서로 다른** 클래스(예: 서로 다른 패키지의 `Material`)가 같은 도구 메서드에 등장하면 충돌이 나고 스키마 생성이 `IllegalStateException: Conflicting schema definition found for key: Material`로 실패합니다. 둘 중 하나의 이름을 바꾸거나, 둘을 하나의 POJO 파라미터로 묶으세요.

#### `required`가 스키마에 미치는 영향

`required`는 파라미터가 스키마에 나타날지를 결정하지 **않습니다**. `@ToolParam`이 붙은 파라미터는 언제나 `properties`에 나열되며, `required`는 스키마 최상위 `required` 배열에 포함되는지만 결정합니다:

- `required = true`(기본값) — 프로퍼티 이름이 `required`에 추가됩니다.
- `required = false` — 프로퍼티는 `properties`에 남지만 `required`에는 빠지므로, 모델이 생략할 수 있습니다.
- 필수 파라미터가 하나도 없으면 `required` 키는 빈 배열이 아니라 **아예 생략**됩니다.

호출 시 `ToolExecutor`는 메서드를 실행하기 전에 모델이 준 인자를 이 스키마로 검증합니다. 필수 프로퍼티가 빠진 호출은 거부되고 검증 오류가 모델에 반환되어 재시도로 이어지므로, 메서드는 아예 진입하지 않습니다. 선택 프로퍼티에 명시적으로 전달된 `null`은 생략한 것과 동일하게 처리됩니다.

<Warning>

생략된 선택 파라미터는 메서드에 `null`로 전달됩니다. 선택 파라미터는 원시 타입이 아니라 **박싱된** 타입(`Integer`, `Double`, `Boolean`)으로 선언하세요. `required = false`가 붙은 원시 타입 파라미터는 모델이 이를 생략하면 호출 시점에 실패합니다. `null`을 `int`나 `double`에 전달할 수 없기 때문입니다.

</Warning>

#### POJO 필드의 `@ToolParam`

`@ToolParam`은 POJO 파라미터의 필드에도 적용되어 프로퍼티 이름을 바꾸고, 설명을 붙이고, 필수 여부를 지정합니다:

```java
public class Location {

    @ToolParam(name = "city_name", description = "The city name")
    private String city;

    @ToolParam(name = "zip_code", description = "The zip code", required = false)
    private String zip;

    private String country; // 애너테이션 없음 → 선택, 필드 이름 그대로
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

메서드 파라미터와 다른 점이 둘 있습니다:

- `@ToolParam`이 없는 필드도 스키마에 포함되며, Java 필드 이름 그대로 **선택** 프로퍼티가 됩니다. 반면 `@ToolParam`이 없는 메서드 파라미터는 스키마에서 완전히 제외됩니다.
- `name`이 비어 있으면 Java 필드 이름으로 대체되고, `description`이 비어 있으면 스키마에 기록되지 않습니다.

필드에서는 Jackson의 `@JsonPropertyDescription`과 `@JsonProperty(required = true)`도 존중되므로, 기존 Jackson 애너테이션 모델을 다시 표기하지 않고 그대로 도구 파라미터로 쓸 수 있습니다.

### 커스텀 도구(`ToolBase` 확장)

커스텀 권한 정책, 외부 실행, 또는 더 복잡한 스키마가 필요할 때는 `ToolBase`를 확장한다.

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
                        .description("Search the web for information on a given query.")
                        .inputSchema(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "query", Map.of(
                                                "type", "string",
                                                "description", "The search query.")),
                                "required", List.of("query")))
                        .readOnly(true)
                        .concurrencySafe(true));
    }

    @Override
    public Mono<PermissionDecision> checkPermissions(
            Map<String, Object> toolInput, PermissionContextState context) {
        return Mono.just(PermissionDecision.allow("Web search is read-only."));
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

### 외부 실행 도구

외부 실행 도구는 실제 작업을 agent 런타임 바깥으로 위임한다 — 대개 사람 운영자나 외부 시스템에게. agent는 `RequireExternalExecutionEvent`를 발행하고 일시 정지한다. 다음 호출이 매칭되는 `ToolResultBlock`을 되돌려주면, agent는 계속 진행하기 전에 동일한 `replyId`를 가진 `ExternalExecutionResultEvent`를 발행한다.

이 패턴은 [human-in-the-loop](/v2/ko/docs/building-blocks/agent#human-in-the-loop) 흐름의 기반이다 — 일부 작업은 사람의 승인이나 사람의 실행을 필요로 한다.

외부 도구를 만들려면 `externalTool`을 `true`로 설정하고 `callAsync` 구현을 생략하면 된다.

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
                        .description("Request human approval for a sensitive operation.")
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
        return Mono.just(PermissionDecision.allow("External tool dispatch is always allowed."));
    }
}
```

실행 가능한 예시: `agentscope-examples/documentation/.../tool/ToolBaseExample.java`, `tool/ToolExecutionContextExample.java`.

## 컨텍스트 받기

`agent.call(msgs, runtimeContext)`에 전달된 [`RuntimeContext`](/v2/ko/docs/building-blocks/agent#runtimecontext-호출별-컨텍스트)는 해당 응답 내의 모든 도구 호출로 전달된다. 도구는 두 가지 방식으로 이를 읽을 수 있다: 애너테이션 기반 도구는 자동 주입을 통해, `ToolBase.callAsync`는 `ToolCallParam`을 통해 읽는다.

### 자동 주입(`@Tool` 메서드)

`@Tool` 메서드 안에서, **`@ToolParam`이 없는** 파라미터는 프레임워크가 주입하는 것으로 취급된다. 해석 순서는 다음과 같다.

| 파라미터 타입 | 출처 |
|----------------|--------|
| `ToolEmitter` | 스트리밍 emitter(설정되지 않았으면 no-op) |
| `Agent` | 현재 agent 인스턴스 |
| `AgentState` | 현재 call의 세션별 상태(`RuntimeContext.getAgentState()`를 통해) |
| `RuntimeContext` | 현재 call의 컨텍스트 |
| `ToolExecutionContext` | `runtimeContext.asToolExecutionContext()`(호환용 shim, deprecated) |
| 그 외 사용자 POJO 타입 | `runtimeContext.get(ParamType.class)` — 즉 호출자가 `RuntimeContext.builder().put(ParamType.class, value)`로 등록한 객체 |

"사용자 POJO"란: `@ToolParam`이 없고, primitive가 아니며, `ContentBlock` / `Msg`도 아니고, `java.*` / `javax.*` 아래에 속하지 않는 것을 뜻한다. 그 외의 모든 파라미터(`@ToolParam`이 붙었거나 위 타입에 해당하지 않는 것)는 이름을 기준으로 LLM이 제공한 JSON에서 읽힌다.

```java
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

public record UserContext(String username, String locale) {}

public class PersonalizedTools {

    @Tool(name = "greet", description = "Greet the user with a custom greeting")
    public String greet(
            @ToolParam(name = "greeting", description = "Greeting word, e.g. 'Hello'")
                    String greeting,                  // ← supplied by the model
            UserContext userCtx) {                    // ← injected by the framework
        return greeting + ", " + (userCtx == null ? "unknown" : userCtx.username()) + "!";
    }
}
```

호출자가 POJO 타입을 한 번 등록해 두면, 이후 매 `call`마다 이를 요청하는 모든 도구에 매칭되는 인스턴스가 자동으로 전달된다.

```java
RuntimeContext ctx =
        RuntimeContext.builder()
                .put(UserContext.class, new UserContext("alice", "en"))
                .userId("alice")
                .build();

agent.call(List.of(new UserMessage("Greet me.")), ctx).block();
```

모델은 `userCtx`를 전혀 보지 못한다 — 도구의 JSON schema에 포함되지 않기 때문이다. 전체 예시: `agentscope-examples/documentation/.../tool/ToolExecutionContextExample.java`.

### `ToolBase.callAsync`에서 컨텍스트 접근하기

`ToolBase`를 확장하는 도구는 `ToolCallParam`을 통해 컨텍스트를 읽는다.

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
        // ... apply tenantId / cfg ...
    }
}
```

`ToolCallParam`은 `getAgent()`, `getInput()`, `getEmitter()`, `getToolUseBlock()`, 그리고 (deprecated된) `getContext()`도 제공한다. 새 코드에서는 `getRuntimeContext()`를 사용하는 것이 좋다.

### hook과 도구 간의 협업

`RuntimeContext`의 문자열 계층(`put(String, Object)` / `get(String)`)은 하나의 `call` 안에서 middleware와 도구 사이의 단기 채널이다 — middleware는 `onActing`/`onReasoning`에서 값을 쓸 수 있고, `RuntimeContext` 파라미터를 주입받는 도구가 이를 읽는다. call이 끝나면 이 인스턴스는 모든 hook과 함께 agent로부터 분리된다.

## MCP

AgentScope는 [Model Context Protocol(MCP)](https://modelcontextprotocol.io/)와 통합되어, agent가 MCP 호환 도구 제공자와 대화할 수 있게 한다. 프레임워크가 프로토콜 협상, 도구 탐색, 결과 변환을 처리한다.

세 가지 전송 방식을 지원한다.

- **STDIO** — stdin/stdout을 통한 로컬 프로세스
- **SSE / Streamable HTTP** — 원격 HTTP 장기 연결

MCP 도구는 충돌을 피하기 위해 toolkit 안에서 `mcp__{server_name}__{tool_name}` 네임스페이스로 노출된다. `readOnlyHint`로 표시된 도구는 권한 시스템에 의해 자동으로 허용된다.

### MCP 도구 등록하기

`McpClientBuilder`를 사용해 `McpClientWrapper`를 만들고, 이를 `Toolkit`에 등록한다.

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

실행 가능한 예시: `agentscope-examples/documentation/.../mcp/McpStdioExample.java`, `mcp/McpSseExample.java`, `mcp/McpStreamableHttpExample.java`.

## Skill

Skill은 새로운 도구 코드를 작성하지 않고도 agent의 능력을 확장하는, markdown 기반의 명령어 집합이다. 각 skill은 frontmatter 메타데이터와 상세한 지침이 담긴 `SKILL.md` 파일을 포함하는 디렉터리다.

도구와 달리 skill은 직접 호출할 수 없다. agent는 자동 등록된 뷰어 도구인 `load_skill_through_path`를 통해 skill 지침을 읽고, 이미 가지고 있는 도구를 사용해 이를 수행한다.

### Skill 등록하기

`ReActAgent.builder().skillRepository(...)`를 통해 하나 이상의 `AgentSkillRepository`를 직접 연결한다. `build()` 시점에 빌더는 `DynamicSkillMiddleware`를 자동으로 설치하며, 이는 매 `call()`마다 설정된 소스로부터 skill prompt와 tool group을 다시 구성한다.

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

`skillRepository(...)`를 여러 번 호출하면 호출 순서대로 추가된다(낮음 → 높음 우선순위). 두 repository가 같은 이름의 skill을 노출하면 나중에 추가된 쪽이 이긴다. 목록 전체를 교체하려면 `skillRepositories(List<AgentSkillRepository>)`를 사용한다.

참고 구현: `agentscope-examples/documentation/.../skill/AgentSkillExample.java`, `skill/SkillWithToolGroupExample.java`.

### Skill 동작 방식

skill이 존재하면 `Toolkit`은 2단계 설정을 수행한다.

초기화 단계:

- toolkit은 등록된 모든 skill 소스를 스캔해서 각 skill의 이름, 설명, 디렉터리를 수집한다.
- 내장 뷰어 도구인 `load_skill_through_path`(`io.agentscope.core.skill.SkillToolFactory`에 구현됨)를 `skill-build-in-tools` 그룹에 자동으로 등록한다.
- 사용 가능한 skill(이름 + 설명) 목록을 나열하고, `load_skill_through_path`를 통해 전체 내용을 읽으라고 agent에 지시하는 system prompt 조각을 조립한다.

런타임에는, agent가 두 개의 필수 인자로 뷰어를 호출한다.

| 파라미터 | 타입 | 설명 |
| --- | --- | --- |
| `skillId` | `string`(등록된 skill ID의 enum) | 로드할 skill. |
| `path` | `string` | skill의 markdown 지침을 가져오려면 `"SKILL.md"`를 사용하거나, skill이 선언한 정확한 리소스 경로(예: `"references/guide.md"`, `"scripts/run.py"`)를 사용한다. `"."`, `"./"`, 디렉터리, 절대 경로는 전달하지 않는다. |

도구 호출 페이로드 예시:

```json
{
  "name": "load_skill_through_path",
  "input": { "skillId": "pdf-extractor", "path": "SKILL.md" }
}
```

성공적인 호출마다 두 가지 효과가 발생한다.

1. 요청한 내용을 반환한다(`SKILL.md` markdown, 또는 지정된 리소스 파일).
2. **skill을 활성화한다** — `Toolkit`에서 연관된 tool group이 활성화되어, 해당 skill에 딸린 도구들이 그 턴이 끝날 때까지 호출 가능해진다. 요청한 `path`가 존재하지 않으면, 뷰어는 사용 가능한 리소스 경로 목록(가장 먼저 `SKILL.md`)을 담은 에러를 반환하므로 agent가 다시 시도할 수 있다.

<Note>

skill은 도구가 아니다 — agent가 이를 직접 호출할 수 없다. agent는 반드시 먼저 `load_skill_through_path`를 통해 지침을 읽은 뒤, 다른 도구로 이를 실행해야 한다.

</Note>

### Skill 스크립트 실행: 셸 도구 설정하기

skill은 지침만 제공한다 — 실제 실행은 agent가 이미 가지고 있는 도구에 의존한다. skill의 지침이 스크립트 실행(예: `scripts/run.py`)을 포함한다면, agent는 셸 접근 권한이 필요하다.

- **`ReActAgent`** — toolkit에 `ShellCommandTool`을 등록한다.

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

- **`HarnessAgent`** — harness 모듈은 워크스페이스를 인지하는 셸 및 파일 도구(`execute`, `read_file`, `write_file` 등)를 기본으로 제공한다. 추가 등록이 필요 없다.

### Skill + ToolGroup: 온디맨드 도구 공개

`SkillToolGroup`은 도구 그룹을 하나의 skill 이름에 묶는다 — agent가 해당 skill을 로드하면 그룹이 자동으로 활성화되고, 그렇지 않으면 모델의 schema에서 숨겨진 채로 남아 컨텍스트 노이즈를 줄인다.

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;

Toolkit toolkit = new Toolkit();

// 1. Create a tool group bound to a skill (initially inactive)
toolkit.createSkillToolGroup(
        "analysis-tools",                // group name
        "Data analysis tools",           // description
        false,                           // initially inactive
        "data-analysis");                // bound skill name

// 2. Register tools into that group
toolkit.registration()
        .tool(new AnalysisTools())
        .group("analysis-tools")
        .apply();

// 3. Build the agent with meta tool for model-driven group switching
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

agent가 `load_skill_through_path`를 통해 `data-analysis` skill을 로드하면, `analysis-tools` 그룹이 활성화되고 그 안의 도구들이 즉시 사용 가능해진다. `enableMetaTool(true)`를 함께 사용하면 모델도 `reset_tools`를 통해 그룹 활성화를 관리할 수 있다.

참고 구현: `agentscope-examples/documentation/.../skill/SkillWithToolGroupExample.java`.

## 자체 관리 도구

내장된 **meta tool**(`reset_tools`)은 agent가 런타임에 어떤 tool group이 활성화되어 있는지를 스스로 관리하게 해준다. 이를 통해 컨텍스트가 집중된 상태로 유지된다 — 현재 작업과 관련된 도구만 모델에 노출된다.

### tool group 정의하기

`ToolGroup`은 이름이 붙은 도구 / MCP 클라이언트 / skill의 묶음이다. `Toolkit`에 그룹을 등록한 뒤 빌더를 통해 meta tool을 켠다.

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

`ToolGroup`은 이름, 설명, 범위(`ToolGroupScope`), 그리고 초기 활성화 플래그를 받는다. 예약된 이름 `"basic"`은 `Toolkit#registerTool(Object)`가 자동으로 채우며 항상 활성 상태다.

### meta tool 사용하기

basic이 아닌 tool group이 하나 이상 존재하고 `enableMetaTool(true)`가 켜져 있으면, `Toolkit`은 `reset_tools`를 자동으로 등록하고 그 schema를 agent에 노출한다. basic이 아닌 각 그룹은 boolean 필드가 되며, meta tool을 호출하는 것은 원하는 최종 상태를 선언하는 것이다.

런타임 동작:

- `"basic"` 그룹의 도구는 항상 노출되며, meta tool은 이들을 건드리지 않는다.
- `reset_tools`를 호출할 때마다 활성 집합이 **완전히 덮어써진다** — 명시적으로 `true`로 설정되지 않은 basic 외 그룹은 이전 상태와 무관하게 비활성화된다.
- 방금 활성화된 각 그룹에 대해, 그 description과(제공된 경우) 사용 지침이 meta tool의 반환 값에 삽입되어 agent에게 해당 그룹을 올바르게 사용하는 방법을 알려준다.
- 비활성 그룹의 도구는 agent의 도구 schema에 나타나지 않으며, 그만큼 더 많은 컨텍스트를 활성 도구 집합을 위해 남겨둔다.

<Warning>

meta tool의 입력은 모든 그룹의 **최종 상태**를 나타내며 델타가 아니다. 명시적으로 `true`로 설정되지 않은 그룹은 이전 상태와 무관하게 비활성화된다.

</Warning>

## 더 읽어보기

<CardGroup cols={2}>


<Card title="Agent" href="/v2/ko/docs/building-blocks/agent">


agent가 ReAct 루프에서 도구 호출을 어떻게 오케스트레이션하는가

</Card>
  <Card title="Permission System" href="/v2/ko/docs/building-blocks/permission-system">


어떤 도구가 언제 실행되는지에 대한 세밀한 제어

  </Card>
  <Card title="Middleware" href="/v2/ko/docs/building-blocks/middleware">


양파 껍질 구조의 middleware로 도구 호출을 가로채고 다시 쓰기

  </Card>
  <Card title="Human-in-the-Loop" href="/v2/ko/docs/building-blocks/agent#human-in-the-loop">


외부 실행 도구와 승인 워크플로

  </Card>


</CardGroup>
