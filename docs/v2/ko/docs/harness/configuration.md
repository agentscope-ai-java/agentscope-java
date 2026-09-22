---
title: 설정
description: 관심사별로 정리한 HarnessAgent.builder() 전체 레퍼런스 — 아이덴티티, 모델, 도구, 워크스페이스, 파일시스템,
  상태, 서브에이전트, Skill, Plan Mode, 그리고 내장 기능을 끄는 모든 스위치
---

`HarnessAgent.builder()`는 모든 Harness 기능을 켜고, 바꾸고, 끄는 단 하나의 지점입니다. 이 페이지는 각 옵션이 해결하는 문제별로 묶은 완전한 builder 레퍼런스입니다. 워크스페이스도 메모리도 서브에이전트도 없는 순수 ReAct 루프만 필요하다면 [`ReActAgent` builder 필드](/v2/ko/docs/building-blocks/agent#builder-fields)를 보세요. 두 builder는 이름 대부분을 공유하므로 서로 옮기는 일은 거의 기계적입니다.

## 동작하는 최소 구성

`name`, `sysPrompt`, `model`만 필수이고 나머지는 모두 쓸 만한 기본값이 있습니다:

```java
import io.agentscope.harness.agent.HarnessAgent;

HarnessAgent agent =
        HarnessAgent.builder()
                .name("assistant")
                .sysPrompt("You are a helpful assistant.")
                .model("dashscope:qwen-plus")   // 환경 변수 DASHSCOPE_API_KEY를 자동으로 읽음
                .build();

String reply = agent.call("What can you do?").block().getTextContent();
System.out.println(reply);
```

`.workspace(...)`를 호출하지 않으면 에이전트는 워크스페이스를 `${user.dir}/.agentscope/workspace`로 해석하고, 상태를 `~/.agentscope/state/<agentId>/`에 저장하며, 기본 파일시스템·셸·메모리·todo 도구를 등록합니다.

## 프로덕션 구성

장기 운영 배포에서 보통 신경 쓰는 선택을 모두 고정한 구성입니다:

```java
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.compaction.CompactionConfig;
import java.nio.file.Paths;
import java.time.Duration;

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new MyDomainTools());

HarnessAgent agent =
        HarnessAgent.builder()
                // --- 아이덴티티 ---
                .name("support-agent")
                .agentId("support-agent-v3")         // 저장되는 상태의 안정적인 네임스페이스 키
                .sysPrompt("You are a support engineer.")

                // --- 모델 ---
                .model("dashscope:qwen-max")
                .fallbackModel("openai:gpt-5.5")     // 주 모델이 계속 실패할 때 사용
                .maxRetries(3)
                .modelExecutionConfig(
                        ExecutionConfig.builder()
                                .timeout(Duration.ofSeconds(90))
                                .maxAttempts(3)
                                .build())

                // --- 도구와 루프 ---
                .toolkit(toolkit)
                .maxIters(20)
                .permissionContext(
                        PermissionContextState.builder()
                                .mode(PermissionMode.DEFAULT)
                                .build())

                // --- 워크스페이스와 컨텍스트 예산 ---
                .workspace(Paths.get("/data/agent-workspace"))
                .additionalContextFile("PREFERENCES.md")
                .maxContextTokens(8000)

                // --- 히스토리 관리 ---
                .compaction(CompactionConfig.builder().build())

                .build();
```

## 아이덴티티와 프롬프트

| 메서드 | 기본값 | 하는 일 |
|--------|--------|---------|
| `name(String)` | 필수 | 메시지와 로그에 쓰이는 에이전트 식별자이자 네임스페이스 키의 폴백 |
| `sysPrompt(String)` | 필수 | 워크스페이스 내용이 얹히기 전의 기본 시스템 프롬프트 |
| `description(String)` | `null` | 사람이 읽는 설명. 이 에이전트를 서브에이전트로 쓸 때 오케스트레이터에 노출됨 |
| `agentId(String)` | `name`으로 폴백 | 저장 상태의 안정적인 네임스페이스 키(`[agents, <agentId>, users, <userId>, …]`). 명시하면 이름을 바꿔도 상태가 끊기지 않음 |
| `environmentMemory(String)` | `null` | 시스템 프롬프트의 세션 환경 블록에 session id와 나란히 덧붙는 텍스트 |
| `environment(String)` | `"prod"` | Skill `EnvironmentFilter`가 읽는 배포 환경 라벨 |

<Tip>

상태를 저장하는 배포라면 `agentId`를 반드시 설정하세요. 설정하지 않으면 `name`이 네임스페이스 키를 겸하므로, 표시 이름만 바꿔도 에이전트가 비어 있는 새 상태 네임스페이스를 가리키게 됩니다.

</Tip>

## 모델

| 메서드 | 기본값 | 하는 일 |
|--------|--------|---------|
| `model(String)` | 필수 | `ModelRegistry` id(`"<provider>:<model>"`). 해당 제공자의 API 키 환경 변수를 읽음 |
| `model(Model)` | 필수 | 엔드포인트·타임아웃·헤더를 직접 정할 때 쓰는 명시적 `ChatModelBase` |
| `fallbackModel(String)` / `fallbackModel(Model)` | `null` | 주 모델이 계속 실패할 때 전환할 모델 |
| `maxRetries(int)` | 제공자 기본값 | 페일오버 전 재시도 횟수 |
| `failoverListener(FailoverListener)` | `null` | 폴백 모델이 인계받을 때 통지받음 |
| `modelExecutionConfig(ExecutionConfig)` | `ExecutionConfig.MODEL_DEFAULTS` | 모델 호출의 타임아웃·시도 횟수·백오프 |
| `generateOptions(GenerateOptions)` | 제공자 기본값 | temperature, top-p 등 샘플링 옵션 |
| `modelResolver(Function<String, Model>)` | `null` | 모델 이름 문자열을 `Model`로 해석. **서브에이전트용** |

`ExecutionConfig`는 모델 호출과 도구 호출의 타임아웃·재시도를 각각 따로 제어합니다:

```java
import io.agentscope.core.model.ExecutionConfig;
import java.time.Duration;

HarnessAgent agent =
        HarnessAgent.builder()
                .name("agent")
                .sysPrompt("…")
                .model("dashscope:qwen-plus")
                .modelExecutionConfig(
                        ExecutionConfig.builder()
                                .timeout(Duration.ofSeconds(120))
                                .maxAttempts(3)
                                .initialBackoff(Duration.ofSeconds(1))
                                .backoffMultiplier(2.0)
                                .build())
                .toolExecutionConfig(
                        ExecutionConfig.builder()
                                .timeout(Duration.ofSeconds(30))
                                .maxAttempts(1)     // 부수 효과가 있는 도구는 재시도하지 않음
                                .build())
                .build();
```

## 도구와 ReAct 루프

| 메서드 | 기본값 | 하는 일 |
|--------|--------|---------|
| `toolkit(Toolkit)` | 기본 toolkit | 도구·MCP 클라이언트·Skill·tool group을 담는 `Toolkit` |
| `maxIters(int)` | `10` | 한 번의 호출에서 추론/행동 최대 반복 횟수 |
| `toolExecutionConfig(ExecutionConfig)` | `ExecutionConfig.TOOL_DEFAULTS` | 도구 호출의 타임아웃·시도 횟수·백오프 |
| `permissionContext(PermissionContextState)` | `DEFAULT` 모드 | allow / ask / deny 규칙. [권한 시스템](/v2/ko/docs/building-blocks/permission-system) 참고 |
| `stopOnReject(boolean)` | `false` | 도구 호출이 거부되면 계속하지 않고 루프를 멈춤 |
| `enableMetaTool(boolean)` | `false` | 자체 관리 tool group용 `reset_tools` 메타 도구 등록 |
| `enableTaskList()` / `enableTaskList(boolean)` | 꺼짐 | 내장 todo / 작업 목록 도구 등록 |
| `enablePendingToolRecovery(boolean)` | `false` | 새 메시지가 도착할 때 고아가 된 도구 호출을 복구 |
| `toolsConfig(ToolsConfig)` | `workspace/tools.json`을 읽음 | MCP 및 허용 목록 설정 파일을 코드로 덮어쓰기 |
| `registerExternalSchemas(List<ToolSchema>)` | `List.of()` | 스키마만 있는 외부 도구. 실행은 중단되고 워커가 처리 |
| `mcpServerRegistrationListener(…)` | `null` | MCP 서버 등록의 최종 결과를 수신. 서브에이전트로 전파되지 않음 |
| `webHttpClient(HttpClient)` | JDK 기본값 | 내장 `web_fetch` / `web_search`가 쓰는 `HttpClient` 교체 |
| `artifactDeliveryTarget(ArtifactDeliveryTarget)` | `null` | `deliver_artifact`를 등록해 산출물을 전달할 수 있게 함 |
| `checkRunning(boolean)` | `true` | 같은 세션의 동시 호출을 큐에 넣지 않고 거부 |

<Note>

`webHttpClient`는 주로 HTTP/1.1을 강제하기 위한 것입니다. 기본 클라이언트는 HTTP/2를 협상하고 실패하면 HTTP/1.1로 자동 폴백하는데, 일부 서버는 이 협상에 실패합니다. HTTP/1.1 전용 클라이언트를 주입하면 도구 코드를 건드리지 않고 해결됩니다.

</Note>

## 워크스페이스와 컨텍스트

| 메서드 | 기본값 | 하는 일 |
|--------|--------|---------|
| `workspace(Path)` / `workspace(String)` | 해석 순서 참고 | `AGENTS.md`, `MEMORY.md`, `skills/`, `subagents/`, `tools.json`을 담는 루트 |
| `additionalContextFile(String)` | 없음 | 워크스페이스 상대 경로 파일을 전문 그대로 시스템 프롬프트에 인라인. 여러 번 호출 가능 |
| `maxContextTokens(int)` | `8000` | `MEMORY.md` 주입 예산 |
| `useLegacyXmlWorkspaceContext(boolean)` | `false` | 워크스페이스 컨텍스트를 마크다운 대신 레거시 XML로 렌더링 |

`workspace(...)`를 호출하지 않으면 `build()`는 builder 값 → 시스템 프로퍼티 `agentscope.workspace` → 환경 변수 `AGENTSCOPE_WORKSPACE` → `${user.dir}/.agentscope/workspace` 순으로 해석합니다. 디렉터리 구조와 각 파일이 로드되는 방식은 [워크스페이스](/v2/ko/docs/harness/workspace)를 참고하세요.

## 파일시스템과 샌드박스

| 메서드 | 기본값 | 하는 일 |
|--------|--------|---------|
| `filesystem(LocalFilesystemSpec)` | 로컬 | 로컬 디스크 + 셸, 경로 허용 목록 포함 |
| `filesystem(RemoteFilesystemSpec)` | — | Redis / JDBC / OSS 기반 공유 스토어 |
| `filesystem(SandboxFilesystemSpec)` | — | 파일과 명령을 Docker / K8s 샌드박스에 격리 |
| `filesystemRoute(String, AbstractFilesystem)` | 없음 | 주 파일시스템과 나란히, 경로 프리픽스 아래에 추가 파일시스템을 마운트 |
| `abstractFilesystem(AbstractFilesystem)` | — | 탈출구: 커스텀 구현을 직접 전달 |
| `distributedStore(DistributedStore)` | `null` | 상태 스토어·원격 스토어·스냅샷 스펙을 한 번에 제공 |

[파일시스템](/v2/ko/docs/harness/filesystem)과 [샌드박스](/v2/ko/docs/harness/sandbox)를 참고하세요.

## 상태·메모리·히스토리

| 메서드 | 기본값 | 하는 일 |
|--------|--------|---------|
| `stateStore(AgentStateStore)` | `~/.agentscope/state/<agentId>/`의 `JsonFileAgentStateStore` | `(userId, sessionId)`별로 `AgentState`를 저장하는 곳 |
| `defaultSessionId(String)` | 에이전트 `name` | 호출의 `RuntimeContext`에 session id가 없을 때 쓰는 값 |
| `memory(MemoryConfig)` | `MemoryConfig.defaults()` | 장기 메모리 프롬프트와 트리거 정책 |
| `compaction(CompactionConfig)` | 기본 설정 | 대화 히스토리를 언제 어떻게 압축할지 |
| `toolResultEviction(ToolResultEvictionConfig)` | 기본값 | 지나치게 큰 도구 결과를 디스크로 옮기고 자리표시자를 남김 |
| `transcriptStore(TranscriptStore)` | 기본값 | 세션 트랜스크립트의 분할 추가 스토어를 덮어씀 |
| `transcriptTenant(String)` | `"default"` | 트랜스크립트 오브젝트 키에 쓰는 테넌트 세그먼트 |

[메모리](/v2/ko/docs/harness/memory)와 [컴팩션](/v2/ko/docs/harness/compaction)을 참고하세요.

## 서브에이전트

| 메서드 | 기본값 | 하는 일 |
|--------|--------|---------|
| `subagent(SubagentDeclaration)` | 없음 | 서브에이전트를 코드로 하나 선언 |
| `subagents(List<SubagentDeclaration>)` | 없음 | 한 번에 여러 개 선언 |
| `subagentFactory(String, Function<String, Agent>)` | 없음 | 특정 agent id의 생성을 완전히 직접 구현 |
| `subagentFactory(String, String, Function<String, Agent>)` | 없음 | 위와 같되 오케스트레이터에 보여줄 설명을 함께 지정 |
| `taskRepository(TaskRepository)` | 기본값 | 백그라운드 서브에이전트 작업 기록의 저장 위치 |
| `externalSubagentTool(Object)` | `null` | 외부 서브에이전트 도구(보통 `SessionsTool`)를 주입 |

서브에이전트는 코드 변경 없이 `workspace/subagents/`에 파일로 선언할 수도 있습니다. [서브에이전트](/v2/ko/docs/harness/subagent)를 참고하세요.

## Skill

| 메서드 | 기본값 | 하는 일 |
|--------|--------|---------|
| `skillRepository(AgentSkillRepository)` | 없음 | Skill 공급원 하나 추가(Git, Nacos, MySQL, classpath) |
| `skillRepositories(List<…>)` | 없음 | 공급원을 한 번에 여러 개 추가 |
| `projectGlobalSkillsDir(Path)` | `null` | 프로젝트 전역 Skill 디렉터리. 마켓플레이스·워크스페이스보다 낮은 우선순위 |
| `enableSkills(String...)` | 전체 | 허용 목록: 이 Skill만 노출 |
| `disableSkills(String...)` | 없음 | 차단 목록: 이들을 제외한 전부 노출 |
| `skillsEnabled(boolean)` | `true` | 모든 Skill을 한 번에 켜거나 끔 |
| `skillFilter(SkillFilter)` | `null` | 어떤 Skill이 보일지 완전히 제어 |
| `enableSkillManageTool(…)` | 꺼짐 | 에이전트가 자신의 워크스페이스 Skill을 생성·편집·보관하도록 허용 |
| `enableSkillPromotionGate(…)` | 꺼짐 | 에이전트가 만든 Skill의 승격 게이트와 가시성 필터 체인 |
| `enableSkillCurator(SkillCuratorConfig)` | 꺼짐 | 백그라운드 Skill 큐레이터. `enableSkillManageTool`이 전제 |

`enableSkills`와 `disableSkills`는 둘 다 `skillFilter`를 쓰는 축약형이라, 나중에 호출한 쪽이 이깁니다:

```java
// 저장소가 무엇을 제공하든 이 두 Skill만 노출한다.
HarnessAgent.builder()
        .name("agent")
        .sysPrompt("…")
        .model("dashscope:qwen-plus")
        .enableSkills("code-review", "changelog")
        .build();
```

[Skill](/v2/ko/docs/harness/skill)을 참고하세요.

## Plan Mode

| 메서드 | 기본값 | 하는 일 |
|--------|--------|---------|
| `enablePlanMode()` / `enablePlanMode(boolean)` | 꺼짐 | 사람이 승인해야 빠져나가는, 읽기 전용 "먼저 생각하기" 단계 |
| `planFileDirectory(String)` | `plans/` | 플랜 파일을 쓰는 위치 |
| `allowShellInPlanMode()` / `allowShellInPlanMode(boolean)` | `false` | Plan Mode 중에도 셸 도구를 허용 |

Plan Mode는 기본적으로 엄격히 읽기 전용이며 셸은 거부됩니다. 셸은 양날의 도구라 이름만으로 읽기 전용으로 분류할 수 없기 때문입니다. 셸로 조사해야 할 때만 명시적으로 켜세요. [Plan Mode](/v2/ko/docs/harness/plan-mode)를 참고하세요.

## 미들웨어와 hook

| 메서드 | 기본값 | 하는 일 |
|--------|--------|---------|
| `middleware(MiddlewareBase)` | 없음 | 미들웨어 하나 추가. 직접 넣은 것이 Harness 내장보다 **먼저** 실행됨 |
| `middlewares(List<? extends MiddlewareBase>)` | 없음 | 한 번에 여러 개 추가 |
| `hook(Hook)` / `hooks(List<Hook>)` | 없음 | 더 낮은 수준의 라이프사이클 hook |
| `enableAgentTracingLog(boolean)` | `true` | `AgentTraceMiddleware`를 통한 실행 추적 로그 |

[미들웨어](/v2/ko/docs/building-blocks/middleware)를 참고하세요.

## Teams·메시지 버스·비동기 도구

| 메서드 | 기본값 | 하는 일 |
|--------|--------|---------|
| `teamsMode(TeamClient, TeamContext)` | 꺼짐 | AgentTeams 모드: `TeamsMiddleware`와 역할별로 제한된 `team` 도구를 등록 |
| `teamsMode(TeamClient, TeamContext, String)` | 꺼짐 | 위와 같되 session id에 바인딩해 컨트롤 플레인의 team 이벤트가 도달하게 함 |
| `messageBus(MessageBus)` | `null` | 인박스 기반 전달. 각 추론 단계 전에 인박스를 비우는 `InboxMiddleware`를 자동 등록 |
| `asyncToolTimeout(Duration)` | `null` | 타임아웃을 넘긴 도구를 백그라운드로 넘김. **`messageBus`가 전제** |
| `asyncToolRegistry(AsyncToolRegistry)` | `null` | 비동기 도구 실행을 추적해 정체된 것을 감지·정리 |

## 내장 기능 끄기

Harness의 각 기능은 안전한 한 기본으로 켜져 있습니다. 아래 스위치는 그것들을 제거합니다. 디버깅, 도구 표면 축소, 또는 해당 기능이 필요 없는 곳에 에이전트를 심을 때 유용합니다:

| 메서드 | 꺼지는 것 |
|--------|-----------|
| `disableWorkspaceContext()` | `AGENTS.md` / `MEMORY.md` / `knowledge/`의 시스템 프롬프트 주입 |
| `disableSessionPersistence()` | `AgentState` 자동 저장 |
| `disableCompaction()` | 대화 컴팩션 전체 |
| `disableToolResultEviction()` | 지나치게 큰 도구 결과의 오프로딩 |
| `disableTranscript()` | 독립적인 세션 트랜스크립트 미들웨어 |
| `disableMemoryHooks()` | 메모리 플러시와 백그라운드 유지보수 |
| `disableMemoryTools()` | `memory_search` / `memory_get` / `memory_save` / `session_search` |
| `disableFilesystemTools()` | 내장 파일시스템 도구 |
| `disableShellTool()` | 내장 셸 도구 |
| `disableWebTools()` | Tavily 기반 `web_search` / `web_fetch` |
| `disableSubagents()` | 서브에이전트 서브시스템 전체 |
| `disableDynamicSubagents()` | 런타임 서브에이전트 생성. 선언된 것은 유지 |
| `disableDynamicSkills()` | 턴마다의 Skill 재병합. 빌드 시 1회 병합으로 축소 |
| `disableDefaultWorkspaceSkills()` | 기본 네임스페이스 워크스페이스 Skill 저장소 |
| `disableToolsConfig()` | `workspace/tools.json` 읽기 |
| `disableAtPathExpansion()` | 사용자 메시지의 `@path` 참조를 첨부 파일 블록으로 확장하는 처리 |

도구 표면을 자기 도구만 남기고 줄인 최소 에이전트:

```java
HarnessAgent agent =
        HarnessAgent.builder()
                .name("narrow-agent")
                .sysPrompt("You answer questions using only the provided tools.")
                .model("dashscope:qwen-plus")
                .toolkit(myToolkit)
                .disableShellTool()
                .disableFilesystemTools()
                .disableWebTools()
                .disableSubagents()
                .disableMemoryTools()
                .build();
```

## 코드 밖의 설정 소스

두 가지 설정은 환경에서 줄 수 있어, 같은 이미지를 여러 배포에 쓸 수 있습니다:

| 설정 | 시스템 프로퍼티 | 환경 변수 |
|------|-----------------|-----------|
| 워크스페이스 루트 | `agentscope.workspace` | `AGENTSCOPE_WORKSPACE` |
| 모델 API 키 | — | `DASHSCOPE_API_KEY`, `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `DEEPSEEK_API_KEY`, `GEMINI_API_KEY` |

```dockerfile
ENV AGENTSCOPE_WORKSPACE=/data/agent-workspace
ENV DASHSCOPE_API_KEY=sk-...
```

나머지는 모두 코드에서 builder로 설정합니다. 워크스페이스 프로퍼티나 변수가 공백이면 설정되지 않은 것으로 보고 다음 소스로 넘어갑니다.

## 관련 페이지

- [Harness 아키텍처](/v2/ko/docs/harness/architecture) — 이 기능들이 어떻게 조합되는지
- [워크스페이스](/v2/ko/docs/harness/workspace) — 각 옵션이 읽는 디렉터리 구조
- [ReActAgent builder 필드](/v2/ko/docs/building-blocks/agent#builder-fields) — 코어 builder
- [권한 시스템](/v2/ko/docs/building-blocks/permission-system)
- [미들웨어](/v2/ko/docs/building-blocks/middleware)
