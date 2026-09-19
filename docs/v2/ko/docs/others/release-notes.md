---
title: 릴리스 노트
description: AgentScope Java의 버전별 변경 기록
---

이 페이지는 AgentScope Java 2.0의 버전별 변경 사항을 추적합니다. 1.x로부터의 전체 마이그레이션 가이드는 [V1 마이그레이션 가이드](/v2/ko/docs/change-log)를 참고하세요.

---

## 2.0.1

> 릴리스: 2026-08-05

AgentScope Java 2.0.1은 2.0.0 GA 이후 첫 번째 유지보수 릴리스입니다. 모델 프로바이더 생태계를 확장하고, Harness 서브에이전트 / HITL / 권한 동작을 강화하며, 프로덕션에 치명적인 일련의 문제들을 수정합니다.

**빠른 링크:** [Quickstart](/v2/ko/docs/quickstart) | [V1 마이그레이션 가이드](/v2/ko/docs/change-log) | [프로덕션으로 가기](/v2/ko/docs/others/going-to-production)

### 추가됨

**Core / Agent**

- `MiddlewareBase.order()`를 통한 미들웨어 실행 순서 지정(값이 클수록 바깥쪽으로 감쌈); `ReActAgent.Builder.build()`는 모든 등록이 끝난 뒤 내림차순으로 안정 정렬함 ([#2532](https://github.com/agentscope-ai/agentscope-java/pull/2532), [#2449](https://github.com/agentscope-ai/agentscope-java/issues/2449))
- 새 세션을 생성하지 않고 모델에 보이는 대화 컨텍스트를 지우는 `ReActAgent` / `HarnessAgent`의 세션 컨텍스트 초기화 API ([#2499](https://github.com/agentscope-ai/agentscope-java/pull/2499), [#2496](https://github.com/agentscope-ai/agentscope-java/issues/2496))
- 장기 실행 인스턴스를 위한 `ReActAgent` 상태 캐시 정리 API 노출 ([#2572](https://github.com/agentscope-ai/agentscope-java/pull/2572))
- 권한 HITL을 재개할 때 `UserConfirmResultEvent`를 방출하며, `replyId`를 통해 이전의 `RequireUserConfirmEvent`와 상호 연관시킬 수 있음 ([#2511](https://github.com/agentscope-ai/agentscope-java/pull/2511))
- Anthropic: `disable_parallel_tool_use` 설정 지원 ([#2257](https://github.com/agentscope-ai/agentscope-java/pull/2257))

**Model Providers**

- 서드파티 호환 벤더를 위한 공유 기반으로서 OpenAI 호환 확장 패키지 추가 ([#2208](https://github.com/agentscope-ai/agentscope-java/pull/2208))
- DeepSeek을 1급 모델 프로바이더로 추가(`deepseek:<model>`, `DEEPSEEK_API_KEY`) ([#2307](https://github.com/agentscope-ai/agentscope-java/pull/2307), [#2211](https://github.com/agentscope-ai/agentscope-java/issues/2211))
- GLM(Zhipu AI) 프로바이더와 전용 포매터 추가 ([#2316](https://github.com/agentscope-ai/agentscope-java/pull/2316))
- Kimi(Moonshot AI) 프로바이더와 전용 포매터 추가 ([#2320](https://github.com/agentscope-ai/agentscope-java/pull/2320), [#2213](https://github.com/agentscope-ai/agentscope-java/issues/2213))
- MiniMax OpenAI 호환 프로바이더 추가 ([#2299](https://github.com/agentscope-ai/agentscope-java/pull/2299))

**Harness / Tools**

- 원격 서브에이전트 이벤트 스트리밍과 HITL 재개 ([#2559](https://github.com/agentscope-ai/agentscope-java/pull/2559))
- `taskId`로 비동기 도구 결과를 기다림 ([#2529](https://github.com/agentscope-ai/agentscope-java/pull/2529))
- 이미지 패키징을 위한 `AGENTSCOPE_WORKSPACE` 환경 변수를 통한 기본 워크스페이스 ([#2310](https://github.com/agentscope-ai/agentscope-java/pull/2310))

**AG-UI**

- AG-UI 모듈 이벤트 메커니즘 업그레이드 ([#2306](https://github.com/agentscope-ai/agentscope-java/pull/2306), [#2202](https://github.com/agentscope-ai/agentscope-java/issues/2202))
- 멀티모달 AG-UI 메시지를 위한 타입화된 `MessageContent` / `InputContent` 도입 ([#2518](https://github.com/agentscope-ai/agentscope-java/pull/2518), [#551](https://github.com/agentscope-ai/agentscope-java/issues/551))

**Spring Boot Starters**

- Ollama Spring Boot Starter 추가 ([#2176](https://github.com/agentscope-ai/agentscope-java/pull/2176), [#2172](https://github.com/agentscope-ai/agentscope-java/issues/2172))

### 리팩터링됨

- Toolkit 기본 실행 모드를 병렬로 변경하고 관련 문서 개선 ([#2558](https://github.com/agentscope-ai/agentscope-java/pull/2558), [#2529](https://github.com/agentscope-ai/agentscope-java/pull/2529)의 후속 작업)
- 빌더를 구체적인 store 구현체로부터 분리하기 위해 세션 메타데이터 저장소를 추상화 ([#2258](https://github.com/agentscope-ai/agentscope-java/pull/2258), [#2068](https://github.com/agentscope-ai/agentscope-java/issues/2068))
- [agent-sandbox](https://github.com/kubernetes-sigs/agent-sandbox) CRD / 컨트롤러 위로 Kubernetes 샌드박스 스토어를 재구성하여, 클러스터가 샌드박스 생명주기와 warm pool을 소유하도록 함 ([#2308](https://github.com/agentscope-ai/agentscope-java/pull/2308))

### 수정됨

**Core / Agent**

- pending recovery가 HITL 승인을 소비하지 못하도록 방지 ([#2109](https://github.com/agentscope-ai/agentscope-java/pull/2109), [#2534](https://github.com/agentscope-ai/agentscope-java/issues/2534))
- 변환된 `onModelCall` 텍스트 델타를 최종 메시지에 적용하여, 네이티브 구조화된 출력 파싱이 오래된 텍스트를 보지 않도록 함 ([#2469](https://github.com/agentscope-ai/agentscope-java/pull/2469), [#2385](https://github.com/agentscope-ai/agentscope-java/issues/2385))
- 완전한 raw JSON으로부터 null인 스트리밍 도구 인자를 복구 ([#2451](https://github.com/agentscope-ai/agentscope-java/pull/2451), [#768](https://github.com/agentscope-ai/agentscope-java/issues/768))
- 우아한 종료 레지스트리가 커지거나 OOM이 발생하지 않도록 `ReActAgent.close()`에서 상태 저장자(state-saver)를 언바인드 ([#2322](https://github.com/agentscope-ai/agentscope-java/pull/2322), [#2321](https://github.com/agentscope-ai/agentscope-java/issues/2321))
- 메모리 누수를 고치기 위해 `ReActAgent.close()`에서 `ShutdownStateSaver`를 언바인드 ([#2384](https://github.com/agentscope-ai/agentscope-java/pull/2384))
- 사용자 인터럽트에 중단 사유를 표시 ([#2260](https://github.com/agentscope-ai/agentscope-java/pull/2260))
- 에이전트 상태 파일을 쓸 때 잘못된 형식의 유니코드 처리(`UnmappableCharacterException`) ([#2255](https://github.com/agentscope-ai/agentscope-java/pull/2255), [#2204](https://github.com/agentscope-ai/agentscope-java/issues/2204))
- 추론 미들웨어 이벤트(예: `InboxMiddleware`의 `HintBlockEvent`)를 `streamEvents()`로 전달 ([#2179](https://github.com/agentscope-ai/agentscope-java/pull/2179), [#2160](https://github.com/agentscope-ai/agentscope-java/issues/2160))
- `ToolResultBlock.error`를 구조화된 오류로 표시 ([#2174](https://github.com/agentscope-ai/agentscope-java/pull/2174), [#2157](https://github.com/agentscope-ai/agentscope-java/issues/2157), [#2111](https://github.com/agentscope-ai/agentscope-java/issues/2111))

**Model Providers**

- DashScope: `qwen3.8-max`를 멀티모달 엔드포인트로 라우팅 ([#2553](https://github.com/agentscope-ai/agentscope-java/pull/2553))
- DashScope: 호출자가 `request_id`를 읽을 수 있도록 SSE 오류 응답 본문을 보존 ([#2278](https://github.com/agentscope-ai/agentscope-java/pull/2278), [#2197](https://github.com/agentscope-ai/agentscope-java/issues/2197))
- OpenAI: 재시도가 HTTP 요청을 다시 보낼 수 있도록 스트리밍 브랜치를 `Flux.defer`로 감쌈 ([#2079](https://github.com/agentscope-ai/agentscope-java/pull/2079))
- OpenAI: `[DONE]` sentinel에서 스트림을 종료 ([#2104](https://github.com/agentscope-ai/agentscope-java/pull/2104))
- OpenAI: 콘텐츠 중복을 피하기 위해 청크가 아닌 요약 이벤트 메시지를 제거 ([#2367](https://github.com/agentscope-ai/agentscope-java/pull/2367))
- OpenAI: `OpenAIMessageConverter`의 `name` 필드를 정제(sanitize) ([#2346](https://github.com/agentscope-ai/agentscope-java/pull/2346))
- OpenAI AutoConfiguration: api-key를 선택 사항으로 변경 ([#2175](https://github.com/agentscope-ai/agentscope-java/pull/2175))
- DeepSeek 포매터: `system` role을 보존 ([#2189](https://github.com/agentscope-ai/agentscope-java/pull/2189), [#2168](https://github.com/agentscope-ai/agentscope-java/issues/2168))
- Ollama: `OllamaChatModel`에서 `stream` 플래그를 준수 ([#2415](https://github.com/agentscope-ai/agentscope-java/pull/2415))
- Anthropic: `ToolChoice.None`을 도구 비활성화로 매핑(이전에는 잘못되게 도구 사용을 강제했음) ([#2232](https://github.com/agentscope-ai/agentscope-java/pull/2232), [#2221](https://github.com/agentscope-ai/agentscope-java/issues/2221))
- 모델 프로바이더 최적화와 호환성 조정 ([#2474](https://github.com/agentscope-ai/agentscope-java/pull/2474))

**Harness / Tools / Sandbox**

- 원격 서브에이전트가 전달한 이벤트에 `taskId`를 표시 ([#2575](https://github.com/agentscope-ai/agentscope-java/pull/2575))
- 비활성화 플래그에 따라 메모리 프롬프트 가이드를 게이트 ([#2565](https://github.com/agentscope-ai/agentscope-java/pull/2565))
- 이벤트 누락을 방지하기 위해 부모 완료 전에 서브에이전트 종료를 방출 ([#2544](https://github.com/agentscope-ai/agentscope-java/pull/2544))
- 부모가 취소되면 서브에이전트 이벤트 스트림을 닫음 ([#2481](https://github.com/agentscope-ai/agentscope-java/pull/2481), [#2480](https://github.com/agentscope-ai/agentscope-java/issues/2480))
- 생성된 서브에이전트에 부모의 DENY 규칙을 강제 적용 ([#2477](https://github.com/agentscope-ai/agentscope-java/pull/2477))
- 스킬 승격(promotion) 중에 `RuntimeContext`를 보존 ([#2465](https://github.com/agentscope-ai/agentscope-java/pull/2465))
- 서브에이전트에 Plan Mode를 강제 적용 ([#2377](https://github.com/agentscope-ai/agentscope-java/pull/2377))
- 워크스페이스 경로 순회(예: `../`)를 거부 ([#2358](https://github.com/agentscope-ai/agentscope-java/pull/2358))
- Windows 로컬 셸 실행 지원(작업 디렉터리 명령과 문자 인코딩 디코딩) ([#2304](https://github.com/agentscope-ai/agentscope-java/pull/2304), [#2268](https://github.com/agentscope-ai/agentscope-java/issues/2268))
- 멀티 테넌트 간섭을 방지하기 위해 정적 서브에이전트 레지스트리를 런타임 컨텍스트별로 격리 ([#2371](https://github.com/agentscope-ai/agentscope-java/pull/2371), [#2328](https://github.com/agentscope-ai/agentscope-java/issues/2328))
- 사용자 의도를 보존하기 위해 연쇄된 압축(chained compaction)에서 이전 요약을 유지 ([#2360](https://github.com/agentscope-ai/agentscope-java/pull/2360))
- 스킬 격리와 도구 결과 이력을 보존 ([#2319](https://github.com/agentscope-ai/agentscope-java/pull/2319))
- `RemoteFilesystem`의 재귀적 glob이 검색 루트의 파일과도 매치됨 ([#2343](https://github.com/agentscope-ai/agentscope-java/pull/2343))
- 선택적인 FilesystemTool 파라미터를 `required=false`로 표시 ([#2227](https://github.com/agentscope-ai/agentscope-java/pull/2227))
- shell-execute의 `working_directory` 파라미터와 도구 사용 힌트를 최적화 ([#2107](https://github.com/agentscope-ai/agentscope-java/pull/2107))
- 선언된 서브에이전트가 부모의 `modelExecutionConfig` / `toolExecutionConfig`를 상속함 ([#2252](https://github.com/agentscope-ai/agentscope-java/pull/2252))
- `sessionId` 파라미터 설명을 수정 ([#2195](https://github.com/agentscope-ai/agentscope-java/pull/2195))

**Storage / Transport**

- PostgreSQL BaseStore 스키마 지원 ([#2273](https://github.com/agentscope-ai/agentscope-java/pull/2273), [#2192](https://github.com/agentscope-ai/agentscope-java/issues/2192))
- PostgreSQL upsert SQL 구문 오류 수정 ([#2167](https://github.com/agentscope-ai/agentscope-java/pull/2167), [#2166](https://github.com/agentscope-ai/agentscope-java/issues/2166))
- `JdkHttpTransport`의 SSE 스트림이 절대 타임아웃에 의해 잘리는 문제 수정 ([#1322](https://github.com/agentscope-ai/agentscope-java/pull/1322), [#1302](https://github.com/agentscope-ai/agentscope-java/issues/1302))

**Spring Boot / Examples**

- Spring Boot 스타터 패키지 이름 수정 ([#2264](https://github.com/agentscope-ai/agentscope-java/pull/2264))
- 예제에서 원본 DashScope 모델 이름을 사용(잘못된 `dashscope:` 접두사 제거) ([#2318](https://github.com/agentscope-ai/agentscope-java/pull/2318))
- `RuntimeContextExample`의 DashScope 모델 이름 수정 ([#2228](https://github.com/agentscope-ai/agentscope-java/pull/2228), [#2229](https://github.com/agentscope-ai/agentscope-java/issues/2229))
- 스킬 예제 리소스 경로 수정 ([#2250](https://github.com/agentscope-ai/agentscope-java/pull/2250))
- 문서와 예제 개선 ([#2508](https://github.com/agentscope-ai/agentscope-java/pull/2508))

### 문서

- README의 Java 2.0 기능 목록에 Agent Evolution 추가 ([#2494](https://github.com/agentscope-ai/agentscope-java/pull/2494))
- all-in-one 의존성에 모델 프로바이더가 포함됨을 명확히 함 ([#2425](https://github.com/agentscope-ai/agentscope-java/pull/2425), [#840](https://github.com/agentscope-ai/agentscope-java/issues/840))
- 문서 링크 리다이렉트 수정 ([#2203](https://github.com/agentscope-ai/agentscope-java/pull/2203), [#2198](https://github.com/agentscope-ai/agentscope-java/issues/2198))
- 버전별 `llms.txt` 산출물 생성(`/v1`, `/v2`) ([#2188](https://github.com/agentscope-ai/agentscope-java/pull/2188), [#2185](https://github.com/agentscope-ai/agentscope-java/issues/2185))
- 모델 빌더 커스터마이저 문서화 ([#2092](https://github.com/agentscope-ai/agentscope-java/pull/2092))
- 모델 문서 업데이트 ([#2100](https://github.com/agentscope-ai/agentscope-java/pull/2100))
- README 문서 링크와 릴리스 노트 URL 수정 ([#2099](https://github.com/agentscope-ai/agentscope-java/pull/2099))
- AG-UI 문서 업데이트 ([#2274](https://github.com/agentscope-ai/agentscope-java/pull/2274))

---

## 2.0.0 (GA)

> 릴리스: 2026-07-10

AgentScope Java 2.0.0이 이제 정식 출시(Generally Available)되었습니다. 이는 2.0 라인의 첫 번째 프로덕션 준비 릴리스이며, AgentScope Java가 "투명한 개발"에서 "시스템 엔지니어링"으로 진화하는 여정의 이정표입니다.

**빠른 링크:** [Quickstart](/v2/ko/docs/quickstart) | [V1 마이그레이션 가이드](/v2/ko/docs/change-log) | [프로덕션으로 가기](/v2/ko/docs/others/going-to-production)

### 2.0 핵심 설계 개요

AgentScope Java 2.0은 단 하나의 목표를 중심으로 한 체계적인 업그레이드입니다: **에이전트가 안정적으로 작업을 완료할 수 있게 하는 것**. 다음은 핵심 설계에 대한 개요입니다.

**이중 계층 에이전트 아키텍처**

- **ReActAgent**: "추론 → 도구 호출 → 응답"이라는 ReAct 루프를 제공하는 상태 비저장 추론 코어입니다. 2.0에서 에이전트 인스턴스는 완전히 상태 비저장입니다 — 호출별 가변 상태는 모두 Reactor Context를 통해 전파되어, 단일 인스턴스가 여러 `(userId, sessionId)` 조합을 동시에 안전하게 처리할 수 있습니다
- **HarnessAgent**: Middleware와 Toolkit 채널을 통해 ReActAgent를 확장하며, 워크스페이스, 메모리, 샌드박스, 서브에이전트, 스킬, Plan Mode를 엔지니어링 인프라로 추가합니다 — 핵심 추론 루프는 보존되고, 확장될 뿐입니다

**메시지 & 이벤트 스트림**

통합된 ContentBlock 메시지 모델(TextBlock / DataBlock / ToolUseBlock / ToolResultBlock / HintBlock 등)과, 28가지 타입화된 AgentEvent 유형을 방출하는 `streamEvents()`가 짝을 이뤄, 에이전트 실행을 관찰 가능하고, 상호작용 가능하며, 중단 가능하게 만듭니다. 프런트엔드 UI는 텍스트 델타, 도구 호출, 사용자 확인 등의 생명주기 이벤트를 실시간으로 따라갈 수 있습니다

**권한 시스템**

새로운 PermissionEngine은 도구 호출에 대한 3단계 결정 메커니즘을 확립합니다: 허용 / 사용자 승인 필요 / 거부. 결정은 정적 규칙, 도구 유형, 입력 콘텐츠 분석을 기반으로 합니다. 민감한 작업은 자동으로 HITL 승인 흐름에 진입합니다

**미들웨어 확장 메커니즘**

5단계의 onion + pipeline 하이브리드 모델(`onAgent` / `onReasoning` / `onActing` / `onModelCall` / `onSystemPrompt`)이 코어 프레임워크를 안정적으로 유지하면서도 로깅, 트레이싱, 보안 검사, 비즈니스 정책, 컨텍스트 주입을 위한 유연한 확장 지점을 제공합니다

**컨텍스트 엔지니어링**

구조화된 압축은 작업 목표, 현재 상태, 핵심 발견, 다음 단계를 보존합니다. 지나치게 큰 도구 결과는 자동으로 디스크로 오프로드되며 컨텍스트에는 플레이스홀더만 남습니다. 파일 도구는 중복 IO를 줄이기 위해 내장 캐싱과 함께 "편집 전 읽기" 정책을 강제합니다

**Workspace 추상화**

"에이전트가 무엇을 하는가"와 "어디서 실행되는가"를 분리합니다. 로컬 파일 시스템, Docker, Kubernetes, E2B 클라우드 샌드박스 백엔드가 단일 인터페이스 뒤로 통합됩니다. 내장된 warm-up pool이 병렬 RL rollout 시나리오를 지원합니다

**모델 장애 허용**

Qwen / OpenAI / Anthropic / Gemini / DeepSeek / Ollama를 아우르는 통합 Credential + ModelRegistry 추상화. 설정 가능한 최대 재시도 횟수와 폴백 모델 — 기본 모델을 사용할 수 없을 때 자동으로 failover됩니다

**엔터프라이즈 분산 배포**

한 줄로 설정하는 `DistributedBackend`(Redis / OSS / MySQL / PostgreSQL / COS). `AgentStateStore`는 `(userId, sessionId)`별로 자동 파티셔닝됩니다. 레플리카 간 세션 복구, 샌드박스 상태 스냅샷, 레플리카 간 서브에이전트 라우팅

**프로토콜 상호운용성**

내장된 A2A(Agent-to-Agent)와 MCP(Model Context Protocol) 지원, 그리고 AG-UI 프로토콜 어댑테이션이, 표준화된 에이전트 간 통신과 프런트엔드 렌더링 요구를 아우릅니다

**멀티 에이전트 오케스트레이션**

선언적 서브에이전트 spec(YAML / Markdown), 동기 블로킹과 백그라운드 위임 모드를 갖춘 런타임 `agent_spawn` / `agent_send`. 서브에이전트 이벤트 스트림은 실시간으로 부모의 `streamEvents()`로 전달될 수 있습니다

**스킬 시스템**

4계층 스킬 합성(Classpath / FileSystem / Nacos / Marketplace) + SkillFilter를 통한 세밀한 필터링 + 자가 학습 폐루프(propose → curate → promote)

---

### RC5 이후 변경 사항

다음은 2.0.0-RC5(2026-07-07)와 GA 릴리스 사이의 점진적인 변경 사항입니다.

#### 추가됨

- HITL이 모든 도구 호출을 거부할 때 `AllToolsDeniedEvent` 훅을 발생시켜, 애플리케이션 레벨에서 전체 거부 시나리오를 처리할 수 있게 함 ([#2083](https://github.com/agentscope-ai/agentscope-java/pull/2083))
- 반복적인 장기 블로킹 대기를 막기 위해 `wait_async_results`에 가드레일 추가 ([#2093](https://github.com/agentscope-ai/agentscope-java/pull/2093))
- PostgreSQL 기반 분산 HarnessAgent 상태를 위한 `PostgresDistributedStore` 추가 ([#2054](https://github.com/agentscope-ai/agentscope-java/pull/2054))
- Spring Boot 스타터에서 OpenAI, DashScope, Anthropic 모델을 위한 빌더 커스터마이저 추가 ([#2045](https://github.com/agentscope-ai/agentscope-java/pull/2045))

#### 수정됨

**Core / Agent**

- NIO 스레드에서 `block()`을 피하기 위해 `seedSystemMsg`를 리액티브하게 만듦 ([#2086](https://github.com/agentscope-ai/agentscope-java/pull/2086))
- PERMISSION_ASKING 결과 메시지에 ASKING ToolUseBlock을 포함 ([#2082](https://github.com/agentscope-ai/agentscope-java/pull/2082))
- `activateOnSkill` 필드를 통해 SkillToolGroup을 활성화 ([#2057](https://github.com/agentscope-ai/agentscope-java/pull/2057))
- 세션 손실을 방지하기 위해 사용자 인터럽트 시 에이전트 상태를 저장 ([#1970](https://github.com/agentscope-ai/agentscope-java/pull/1970))

**Model Providers**

- Anthropic: API 요구 사항을 준수하기 위해 병렬 도구 호출을 교대 메시지로 분할 ([#2090](https://github.com/agentscope-ai/agentscope-java/pull/2090))
- OpenAI: `nativeStructuredOutput`을 설정 가능하게 만듦 ([#2069](https://github.com/agentscope-ai/agentscope-java/pull/2069))

**Harness / Tools / Sandbox**

- 외부 도구 실행이 이제 정상적으로 일시 중지된(suspended) 결과를 생성함 ([#2071](https://github.com/agentscope-ai/agentscope-java/pull/2071))
- `isReadOnly`를 AgentTool 인터페이스로 끌어올려 Plan Mode에서 SkillLoadTool을 허용 ([#2067](https://github.com/agentscope-ai/agentscope-java/pull/2067))
- AgentSpawnTool 부모 구독이 취소될 때 고아가 된 서브에이전트를 인터럽트 ([#2064](https://github.com/agentscope-ai/agentscope-java/pull/2064))
- MemoryFlushMiddleware에서 불필요한 ReActAgent 타입 제약을 제거 ([#2078](https://github.com/agentscope-ai/agentscope-java/pull/2078))
- ROOTED 모드에서 선행 `/` 경로를 워크스페이스 기준 상대 경로로 해석 ([#2049](https://github.com/agentscope-ai/agentscope-java/pull/2049))
- 워크스페이스 투영 전에 마켓플레이스 스킬을 미리 스테이징 ([#2059](https://github.com/agentscope-ai/agentscope-java/pull/2059))
- Kubernetes의 `hydrateWithArchive`에서 null exit code를 성공으로 처리 ([#1915](https://github.com/agentscope-ai/agentscope-java/pull/1915))
- 영속화된 상태로부터 재개할 때 업데이트된 WorkspaceSpec을 사용 ([#1928](https://github.com/agentscope-ai/agentscope-java/pull/1928))
- AgentRun MCP 응답에서 중첩된 JSON과 배너 접두사를 지원 ([#1930](https://github.com/agentscope-ai/agentscope-java/pull/1930))
- Docker workspaceRoot에 해석된 workingDir을 사용 ([#2033](https://github.com/agentscope-ai/agentscope-java/pull/2033))

**Channel**

- 그룹 메시지 라우팅을 고치기 위해 OutboundAddress에 PeerKind를 포함 ([#2060](https://github.com/agentscope-ai/agentscope-java/pull/2060))

**A2A**

- 파편화를 피하기 위해 스트리밍 텍스트 청크를 병합 ([#2058](https://github.com/agentscope-ai/agentscope-java/pull/2058))

---

## 2.0.0-RC5

> 릴리스: 2026-07-07

### 호환성이 깨지는 변경

- **모델 프로바이더 모듈화** — OpenAI, Gemini, Anthropic, DashScope, Ollama 모델 프로바이더가 `agentscope-core`에서 독립적인 `agentscope-extensions-model-*` 확장 모듈로 이동되었습니다. 애플리케이션은 해당하는 확장 의존성을 추가해야 합니다 ([#1890](https://github.com/agentscope-ai/agentscope-java/pull/1890), [#1916](https://github.com/agentscope-ai/agentscope-java/pull/1916), [#1947](https://github.com/agentscope-ai/agentscope-java/pull/1947), [#1972](https://github.com/agentscope-ai/agentscope-java/pull/1972))

### 추가됨

- 모든 프로바이더 메시지 컨버터(OpenAI, DashScope, Gemini, Anthropic)에서 통합된 `DataBlock` 지원, 단일 에이전트, 멀티 에이전트, 도구 결과 경로를 아우름 ([#1933](https://github.com/agentscope-ai/agentscope-java/pull/1933))
- 도구와 함께 사용하는 네이티브 구조화된 출력 처리 — 구조화된 출력을 지원하는 모델은 도구 호출과 함께 JSON 스키마 제약을 강제할 수 있음 ([#1904](https://github.com/agentscope-ai/agentscope-java/pull/1904))
- DashScope 모델을 위한 네이티브 구조화된 출력 지원 ([#1935](https://github.com/agentscope-ai/agentscope-java/pull/1935))
- 동적 토큰 주입(예: OAuth 갱신)을 위한 `McpClientBuilder`의 `httpRequestCustomizer` 지원 ([#1992](https://github.com/agentscope-ai/agentscope-java/pull/1992))
- `AguiEvent`를 AG-UI 프로토콜 스펙에 맞춤 — 누락된 이벤트 유형 추가 ([#1862](https://github.com/agentscope-ai/agentscope-java/pull/1862))
- 서브에이전트를 위한 선택적 스킬 허용목록 필터 ([#1873](https://github.com/agentscope-ai/agentscope-java/pull/1873))
- `NacosSkillRepository`에서 `knownSkillNames` 지원 ([#1853](https://github.com/agentscope-ai/agentscope-java/pull/1853))
- Tencent Cloud COS 기반 상태 영속성을 위한 `CosAgentStateStore`, `CosBaseStore`, `CosDistributedStore` ([#1857](https://github.com/agentscope-ai/agentscope-java/pull/1857))
- `ChatUsage`에 캐시된 프롬프트 토큰 노출 ([#1868](https://github.com/agentscope-ai/agentscope-java/pull/1868))

### 수정됨

**Core / Agent**

- 사용자 인터럽트 복구 시 에이전트 상태를 영속화 ([#2008](https://github.com/agentscope-ai/agentscope-java/pull/2008))
- 폴백 모델을 `ReActAgent`에 연결 ([#1851](https://github.com/agentscope-ai/agentscope-java/pull/1851))
- `ReActAgent` 스트림 이벤트의 블록 종료 순서 수정 ([#1829](https://github.com/agentscope-ai/agentscope-java/pull/1829))
- 에이전트 컨텍스트에 추가하기 전에 `ToolResultBlock` 상태를 업데이트 ([#1886](https://github.com/agentscope-ai/agentscope-java/pull/1886))
- 리소스 누수를 방지하기 위해 classpath 스킬 JAR 파일 시스템을 재사용 ([#1981](https://github.com/agentscope-ai/agentscope-java/pull/1981))
- `Flux.create` 콜백에서 `serializeOnKey` 게이트 누수를 해결 ([#1796](https://github.com/agentscope-ai/agentscope-java/pull/1796))

**Model Providers**

- `thinkingBudget`을 OpenAI 호환 API 요청에 매핑 ([#2028](https://github.com/agentscope-ai/agentscope-java/pull/2028))
- Anthropic 스트림 thinking 이벤트 처리 수정 ([#1943](https://github.com/agentscope-ai/agentscope-java/pull/1943))
- `OllamaOptions`의 `fromOptions`/`toBuilder`에서 `executionConfig`를 보존 ([#2011](https://github.com/agentscope-ai/agentscope-java/pull/2011))
- DashScope thinking 모드에서 강제된 tool choice를 완화(degrade) ([#1882](https://github.com/agentscope-ai/agentscope-java/pull/1882))

**Harness / Sandbox**

- 원격 스냅샷 상태 역직렬화 복원 — Jackson 왕복 후 `RemoteSnapshotClient`를 다시 주입 ([#2013](https://github.com/agentscope-ai/agentscope-java/pull/2013))
- 요청마다 인스턴스를 재생성할 때 THROTTLED 메모리 저장 모드가 상태를 잃는 문제 수정 ([#1788](https://github.com/agentscope-ai/agentscope-java/pull/1788))
- wakeup 디스패치 전체에 `userId`를 전파 ([#2001](https://github.com/agentscope-ai/agentscope-java/pull/2001))
- 메시지 버스 heartbeat를 `parallel` 스케줄러 대신 `boundedElastic`에서 실행 ([#1974](https://github.com/agentscope-ai/agentscope-java/pull/1974))
- `fromAgent`에서 `GracefulShutdownMiddleware`가 중복되는 것을 방지 ([#1952](https://github.com/agentscope-ai/agentscope-java/pull/1952))
- `ShellPathPolicy`가 반환하는 스킬 경로의 공백을 이스케이프 ([#2031](https://github.com/agentscope-ai/agentscope-java/pull/2031))
- YAML 파싱이 실패할 때 단순 key-value 추출로 폴백 ([#2027](https://github.com/agentscope-ai/agentscope-java/pull/2027))
- `ls`에서 샌드박스 파일 크기를 보고 ([#1838](https://github.com/agentscope-ai/agentscope-java/pull/1838))
- Windows `list_files` 경로를 정규화 ([#1892](https://github.com/agentscope-ai/agentscope-java/pull/1892))
- `LocalFilesystem.edit()`에서 파일 콘텐츠의 `\r\n`을 `\n`으로 정규화 ([#2020](https://github.com/agentscope-ai/agentscope-java/pull/2020))
- `CompositeFilesystem`에서 `"."`을 루트와 동등하게 처리 ([#1830](https://github.com/agentscope-ai/agentscope-java/pull/1830))
- 네임스페이스 이탈을 방지하기 위해 `working_directory`를 검증 ([#1834](https://github.com/agentscope-ai/agentscope-java/pull/1834))
- 분산 `AgentStateStore`가 설정되지 않았을 때 `LocalFilesystemSpec`으로 폴백 ([#1841](https://github.com/agentscope-ai/agentscope-java/pull/1841))
- Kubernetes `hydrateWithArchive`에서 `exit=null`을 일으키던 WebSocket 경쟁 조건 수정 ([#1903](https://github.com/agentscope-ai/agentscope-java/pull/1903))
- 래핑된 샌드박스 base64 다운로드를 허용 ([#1866](https://github.com/agentscope-ai/agentscope-java/pull/1866))
- `AgentRun` 샌드박스 API 버전 접두사를 제거 ([#1891](https://github.com/agentscope-ai/agentscope-java/pull/1891))
- E2B 샌드박스를 위한 connect JSON 코덱 지원 추가 ([#1844](https://github.com/agentscope-ai/agentscope-java/pull/1844))

**Tracing / Observability**

- Reactor `ContextView`로부터 부모 OTel Context를 읽어 `OtelTracingMiddleware`의 고아 스팬을 수정 ([#1940](https://github.com/agentscope-ai/agentscope-java/pull/1940))
- `OtelTracingMiddleware`에서 자식 스팬이 올바른 부모 스팬을 보지 못하던 문제 수정 ([#1909](https://github.com/agentscope-ai/agentscope-java/pull/1909))
- Reactor 컨텍스트를 청크 이벤트 훅으로 전파 ([#1923](https://github.com/agentscope-ai/agentscope-java/pull/1923))

**Subagent**

- 부모 `RuntimeContext`를 자식 에이전트로 전파 ([#1833](https://github.com/agentscope-ai/agentscope-java/pull/1833))
- 부모 미들웨어를 서브에이전트로 전파 ([#1843](https://github.com/agentscope-ai/agentscope-java/pull/1843))

**A2A**

- 스트리밍 백프레셔 처리 ([#1734](https://github.com/agentscope-ai/agentscope-java/pull/1734))
- A2A 변환 전반에서 AgentScope 메시지 role을 보존 ([#1995](https://github.com/agentscope-ai/agentscope-java/pull/1995))

**AG-UI**

- 실행 입력과 프런트엔드 도구를 전파 ([#1895](https://github.com/agentscope-ai/agentscope-java/pull/1895))

**기타**

- 이른 평가를 방지하기 위해 미들웨어 `doFlush`를 `Mono.defer`로 감쌈 ([#1880](https://github.com/agentscope-ai/agentscope-java/pull/1880))
- Nacos 자동 설정은 옵트인이어야 함(`matchIfMissing=false`) 및 A2A server-addr 오버라이드 수정 ([#1709](https://github.com/agentscope-ai/agentscope-java/pull/1709))
- DataAgent의 `MarketContributionService`를 위한 `ObjectMapper` 빈 추가 ([#1993](https://github.com/agentscope-ai/agentscope-java/pull/1993))

### 문서

- 스트림 이벤트 `blockId` 시맨틱을 명확히 함 ([#2016](https://github.com/agentscope-ai/agentscope-java/pull/2016))
- 모델 프로바이더 문서 개선 ([#1986](https://github.com/agentscope-ai/agentscope-java/pull/1986))
- 유효하지 않은 `ChatResponse.isLast` 참조 제거 ([#1921](https://github.com/agentscope-ai/agentscope-java/pull/1921))
- 다중 레플리카 Redis 예제 수정 — jedis 의존성 선언과 `stateStore` 추가 ([#1869](https://github.com/agentscope-ai/agentscope-java/pull/1869))
- `MemoryCompactionExample`이 메모리 파일을 보여주고 압축을 발생시키도록 수정 ([#1978](https://github.com/agentscope-ai/agentscope-java/pull/1978))

---

## 2.0.0-RC4

> 릴리스: 2026-06-18

### 추가됨

- 에이전트 harness가 이제 메시지 버스, 비동기 도구 레지스트리, 예약된 wakeup 디스패치를 포함해 비동기 도구 실행과 알림을 지원함 ([#1802](https://github.com/agentscope-ai/agentscope-java/pull/1802))
- 에이전트 호출을 위한 String/Message 편의 오버로드; 이제 모든 포매터가 `HintBlock`을 지원함 ([#1802](https://github.com/agentscope-ai/agentscope-java/pull/1802))
- 도구 컨텍스트 상태의 영속적인 spawn 레지스트리가 레플리카 간 서브에이전트 라우팅과 세션 복구를 가능하게 함 ([#1817](https://github.com/agentscope-ai/agentscope-java/pull/1817))
- `DynamicSkillMiddleware`가 `ToolkitAware`를 구현하여 해석된 toolkit을 동적으로 수신함 ([#1828](https://github.com/agentscope-ai/agentscope-java/pull/1828))
- Kubernetes 샌드박스가 이제 pod에 환경 변수를 주입하는 것을 지원함 ([#1789](https://github.com/agentscope-ai/agentscope-java/pull/1789))

### 수정됨

- 2단계 아카이브 전략을 사용해 Kubernetes 파일 업로드의 SIGKILL 경쟁 조건 수정 ([#1826](https://github.com/agentscope-ai/agentscope-java/pull/1826))
- 재시도 시 타임아웃된 서브에이전트가 인터럽트되지 않던 리소스 누수 수정 ([#1784](https://github.com/agentscope-ai/agentscope-java/pull/1784))
- `RuntimeContext`를 복사할 때 타입 속성이 손실되던 문제 수정 ([#1813](https://github.com/agentscope-ai/agentscope-java/pull/1813))
- MySQL utf8mb4 charset에서 `JdbcStore` 테이블 초기화가 실패하던 문제 수정 ([#1781](https://github.com/agentscope-ai/agentscope-java/pull/1781))
- 중복 쓰기를 방지하기 위해 세션 JSONL 오프로드를 멱등(idempotent)하게 만듦 ([#1774](https://github.com/agentscope-ai/agentscope-java/pull/1774))
- `TelemetryTracer`의 OpenTelemetry 컨텍스트 전파 수정 ([#1799](https://github.com/agentscope-ai/agentscope-java/pull/1799))
- tool choice 조회 시 옵션이 null일 때 `OllamaChatModel`의 NPE 수정 ([#1803](https://github.com/agentscope-ai/agentscope-java/pull/1803))
- 올바른 직렬화를 위해 `LocalSandboxSnapshot`에 누락된 Jackson 애너테이션 추가 ([#1825](https://github.com/agentscope-ai/agentscope-java/pull/1825))
- 샌드박스 glob이 `**/` 재귀 패턴을 지원하지 않던 문제 수정 ([#1684](https://github.com/agentscope-ai/agentscope-java/pull/1684))
- 스킬 이름 대신 합성 ID를 사용하던 `SkillFilter` 매칭 수정 ([#1771](https://github.com/agentscope-ai/agentscope-java/pull/1771))
- `MultiModalTool`에서 커스텀 기본 vision 모델 허용 ([#1701](https://github.com/agentscope-ai/agentscope-java/pull/1701))

### 문서

- 미들웨어 문서의 잘못된 훅 시그니처 수정 ([#1835](https://github.com/agentscope-ai/agentscope-java/pull/1835))
- 문서 예제에서 존재하지 않는 `.sandboxContext()` 참조 수정 ([#1792](https://github.com/agentscope-ai/agentscope-java/pull/1792))
- v2 문서에서 `getToolName()` → `getToolCallName()` 수정 ([#1760](https://github.com/agentscope-ai/agentscope-java/pull/1760))
- 문서 사이트에 AI 컨텍스트 메뉴 추가

---

## 2.0.0-RC3

> 릴리스: 2026-06-11

### 추가됨

- **`AgentResultEvent`** — 에이전트가 처리를 마쳤을 때 `AgentEndEvent` 바로 직전에 방출되는 새로운 이벤트 유형으로, 최종 `Msg` 결과를 담습니다. `streamEvents()`의 구독자는 별도로 `Mono<Msg>` 반환값을 구독하지 않고도 이벤트 스트림에서 직접 결과를 얻을 수 있습니다
- **`CustomEvent`** — 사용 사례별로 `AgentEventType` 항목을 추가하지 않고도 미들웨어가 애플리케이션 수준의 알림(상태 변경, 팀 업데이트 등)을 프런트엔드 구독자에게 푸시할 수 있는 범용 확장 이벤트입니다. 내장된 잘 알려진 이름: `state_updated`, `team_updated`
- **`HintBlockEvent`** — 스트리밍되는 텍스트/thinking 블록과 달리, 팀 메시지, 백그라운드 도구 결과, 사용자 인터럽션 같은 완전한 콘텐츠를 전달하기 위한 일회성 힌트 블록 이벤트입니다
- **`WorkspacePathNormalizer`** — 절대 경로를 워크스페이스 상대 형태로 변환하는 파일 경로 정규화 유틸리티입니다. 크로스 모드 접두사 충돌을 방지하기 위해 활성 파일 시스템 모드(로컬 / 샌드박스)에 따라 접두사를 등록합니다
- **도구 이벤트의 `toolCallName`** — `ToolCallDeltaEvent`, `ToolCallEndEvent`, `ToolResultDataDeltaEvent`, `ToolResultEndEvent`, `ToolResultTextDeltaEvent`가 이제 `toolCallName` 필드를 가지므로, 구독자가 더 이상 시작 이벤트로부터 이름 매핑을 캐시할 필요가 없습니다

### 변경됨

- **통합된 `call()` / `streamEvents()` 코어** — `call()`과 `streamEvents()` 모두의 공유 구현으로서 내부 `buildAgentStream` 메서드가 도입되어, `onAgent` 미들웨어 체인이 모든 호출 경로에서 일관되게 실행되도록 보장합니다. `call()`은 이제 이벤트 스트림의 `AgentResultEvent`로부터 결과를 추출합니다; 레거시 단독 `agentImpl` 로직은 제거되었습니다
- **분산 배포에서 세션 상태가 항상 store로부터 다시 로드됨** — `AgentStateStore`가 설정되어 있으면, `activateSlotForContext`는 이제 모든 호출 시작 시점에 store로부터 에이전트 상태와 권한 엔진을 다시 로드하여, 동일한 sessionId가 여러 머신에 걸쳐 있을 때 오래된 로컬 캐시를 읽는 문제를 방지합니다
- **`ToolResultEvictionMiddleware` 타이밍 수정** — (상태가 아직 기록되지 않아 축출이 아무 효과가 없던) `onActing`에서 `onReasoning`으로 이동되어, 축출이 실행되기 전에 도구 결과가 영속화되도록 보장합니다
- **`LocalFilesystem` 경로 해석 단순화** — 중복 코드를 줄이기 위해 경로 해석 로직을 리팩터링

### 수정됨

- 테스트에서 `RuntimeContext`가 `userId`를 설정하지 않아 사용자 격리가 부정확했던 문제 수정

---

## 2.0.0-RC2

> 릴리스: 2026-06-09

### 추가됨

- **`projectWritable` 모드**(`LocalFilesystemSpec`) — 활성화되면, 에이전트의 파일 쓰기가 경로에 따라 라우팅됩니다: 워크스페이스 메타데이터(`MEMORY.md`, `agents/`, `skills/` 등)는 워크스페이스로 가고, 그 외(코드, 설정)는 프로젝트 디렉터리에 놓입니다. 코드 생성 에이전트를 위해 설계되었습니다. [Filesystem · 프로젝트 쓰기 가능 모드](/v2/ko/docs/harness/filesystem#프로젝트-쓰기-가능-모드projectwritable)를 참고하세요
- **런타임 권한 모드 전환** — 런타임에 세션별로 권한 모드를 동적으로 조정하기 위한 새로운 `HarnessAgent.setPermissionMode()` / `getPermissionMode()`
- **서브에이전트 이벤트 스트림 전달** — `streamEvents()`가 이제 자식 에이전트의 중간 이벤트(`TextBlockDelta`, `ToolCallStart` 등)를 실시간으로 전달하며, 각 이벤트는 발생 에이전트를 식별하는 `source` 경로를 담습니다
- **`AgentEvent.source` 필드** — 모든 `AgentEvent` 인스턴스가 이제 `source` 필드를 가져, 동일한 이벤트 스트림 내에서 메인 에이전트 이벤트(`source = null`)와 서브에이전트 이벤트(`source = "main/researcher"` 경로 형식)를 구분할 수 있으며, 구독자 측에서 별도 상태 없이 디먹싱할 수 있습니다
- **Compaction / Memory를 위한 커스텀 프롬프트와 모델** — `CompactionConfig`와 `MemoryConfig`가 `.model()`과 `.prompt()` 빌더 메서드를 얻어, 에이전트의 주 모델 대신 컨텍스트 압축과 메모리 추출을 위한 전용 경량 모델과 커스텀 프롬프트를 사용할 수 있습니다
- **Qwen 3.7 모델 지원** — `ModelRegistry`가 이제 `dashscope:qwen3.7-plus`와 그 외 Qwen 3.7 시리즈 모델을 해석합니다
- **직접 서브에이전트 메시징** — `agent_send`는 호출자가 부모 에이전트의 추론 루프를 거치지 않고 선언된 서브에이전트에 직접 메시지를 보내고 그 응답을 받을 수 있게 합니다
- **Channel 모듈** — IM 플랫폼 통합(DingTalk, Feishu/Lark, WeCom, GitHub, GitLab)을 위한 새로운 `agentscope-extensions-channel` 모듈 패밀리로, 즉시 사용 가능한 대화형 인터페이스를 위한 내장 ChatUI를 포함합니다
- **`DistributedBackend` 통합 인터페이스** — 모든 분산 스토리지 컴포넌트(`AgentStateStore`, `BaseStore`, `SandboxSnapshotSpec`)를 하나의 설정 지점으로 통합하는 새로운 `DistributedBackend` 추상화입니다. 내장 구현체로는 `RedisDistributedBackend`, `OssDistributedBackend`, `MysqlDistributedBackend`가 있습니다. `HarnessAgent.builder().distributedBackend(backend)`를 한 번 호출하면 전체 분산 백엔드가 연결됩니다 — 더 이상 stateStore, baseStore, snapshotSpec을 별도로 설정할 필요가 없습니다

### 변경됨

- **에이전트가 완전히 상태 비저장이 됨** — `ReActAgent`는 더 이상 세션별 가변 상태를 보관하지 않습니다; 모든 가변 상태는 내부 `CallExecution`에 캡슐화되어 Reactor Context를 통해 전파됩니다. 단일 에이전트 인스턴스는 여러 `(userId, sessionId)` 조합을 동시에 안전하게 처리할 수 있습니다
- **세션 인터페이스가 `AgentStateStore`로 대체됨** — `SessionManager`, `StatePersistence`, 관련 레거시 인터페이스가 제거되었습니다; `AgentStateStore`(내장: `InMemoryAgentStateStore`, `JsonFileAgentStateStore`, `RedisAgentStateStore`, `MysqlAgentStateStore`)로 통합되었으며, `(userId, sessionId)`별로 자동 파티셔닝됩니다
- **`BaseStore` 인터페이스 패키지 이름 변경** — `BaseStore`와 관련 인터페이스가 새 패키지로 이동했습니다; 기존 import 경로를 사용하는 코드는 업데이트가 필요합니다
- **확장 모듈 좌표 통합** — 여러 확장 Maven 좌표가 역량에 따라 재구성되었습니다. 예를 들어, `agentscope-extensions-session-redis`는 이제 `agentscope-extensions-redis`입니다(`RedisAgentStateStore`, `RedisStore`, `RedisSnapshotSpec` 등을 묶음). 기존 좌표를 사용하고 있었다면 pom의 `<artifactId>`를 업데이트하세요
- **샌드박스 구현체가 harness 코어에서 분리됨** — Docker, Kubernetes, E2B, Daytona, AgentRun 샌드박스 백엔드가 `agentscope-harness`에서 독립적인 확장 모듈(`agentscope-extensions-sandbox-*`)로 이동했습니다. harness 코어는 추상 인터페이스(`SandboxFilesystemSpec` 등)만 유지하며, 더 이상 구체적인 샌드박스 의존성을 전이적으로 가져오지 않습니다. 샌드박스 지원이 필요하면 해당 확장을 명시적으로 추가하세요(예: Docker의 경우 `agentscope-extensions-sandbox-docker`)
- **Plan Mode 개선** — 계획 파일 영속성과 복구가 개선되었고, `plan_enter` / `plan_write` / `plan_exit` 도구 체인 상호작용이 더 매끄러워졌으며, HITL 승인 흐름이 더 견고해졌습니다
- **스킬 자가 진화 강화** — propose(`ProposeSkillTool`) → curate(`SkillCurator`) → promote(`SkillPromoter`) 폐루프가 다듬어졌고, 스킬 매칭 정확도와 세션 간 재사용이 개선되었습니다
- `DashScopeHttpClient` 요청 타임아웃과 재시도 정책 조정
- `ModelRegistry` 모델 해석 로직 개선
- `AgentState` 직렬화 형식 업데이트

### 수정됨

- 세션 간 복원 중 `PermissionContextState`가 상태를 잃던 문제 수정
- `agentscope-all`에서 4개의 샌드박스 확장 모듈(`sandbox-kubernetes`, `sandbox-agentrun`, `sandbox-daytona`, `sandbox-e2b`)이 누락되던 문제 수정

---

## 2.0.0-RC1

> 릴리스: 2025-05-28

첫 번째 2.0 Release Candidate입니다. 1.x로부터의 전체 아키텍처 업그레이드를 포함합니다.

- Harness 엔지니어링(워크스페이스, 메모리, 스킬, 서브에이전트, Plan Mode, 컨텍스트 압축)
- 엔터프라이즈급 분산 배포(멀티 테넌트 격리, 샌드박스 실행, 권한 시스템, 세션 복구)
- 코어 프레임워크 재설계(이벤트 스트림, 메시지 모델, Middleware, HITL)

전체 1.x → 2.0 변경 목록은 [V1 마이그레이션 가이드](/v2/ko/docs/change-log)를 참고하세요.
