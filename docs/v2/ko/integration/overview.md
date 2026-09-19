---
title: 통합 개요
---

이 섹션은 타사 시스템 및 생태계 서비스에 연결되는 AgentScope Java 확장 기능을 모아 놓았습니다. 각 확장 기능은 `agentscope-extensions/` 하위의 독립적인 Maven 모듈이므로 — 필요한 것만 가져다 쓰면 됩니다.

확장 기능은 주제별로 그룹화되어 있습니다:

## 모델 제공자

모든 모델 제공자는 독립적인 모델 확장 모듈로 이전되었으며, `agentscope-core`는 공유 모델 계약만 유지합니다. 전체 생성 경로, Spring Boot 설정, 포매터, 자격 증명, 고급 레지스트리 동작은 [Model](/v2/ko/docs/building-blocks/model)을 참조하세요.

| 제공자 | Maven artifact | `ModelRegistry` id | 표준 환경 변수 | 문서 |
|----------|----------------|--------------------|-------------------------------|------|
| OpenAI | `agentscope-extensions-model-openai` | `openai:<model>` | `OPENAI_API_KEY` | <a class="reference internal" href="/v2/ko/integration/model/openai">OpenAI</a> |
| DeepSeek | `agentscope-extensions-model-openai` | `deepseek:<model>` | `DEEPSEEK_API_KEY` | <a class="reference internal" href="/v2/ko/integration/model/deepseek">DeepSeek</a> |
| GLM | `agentscope-extensions-model-openai` | `glm:<model>` | `ZAI_API_KEY` / `GLM_API_KEY` / `ZHIPUAI_API_KEY` | <a class="reference internal" href="/v2/ko/integration/model/glm">GLM</a> |
| Kimi | `agentscope-extensions-model-openai` | `kimi:<model>` | `MOONSHOT_API_KEY` / `KIMI_API_KEY` | <a class="reference internal" href="/v2/ko/integration/model/kimi">Kimi</a> |
| MiniMax | `agentscope-extensions-model-openai` | `minimax:<model>` | `MINIMAX_API_KEY` | <a class="reference internal" href="/v2/ko/integration/model/minimax">MiniMax</a> |
| DashScope | `agentscope-extensions-model-dashscope` | `dashscope:<model>` / `qwen*` | `DASHSCOPE_API_KEY` | <a class="reference internal" href="/v2/ko/integration/model/dashscope">DashScope</a> |
| Gemini | `agentscope-extensions-model-gemini` | `gemini:<model>` | `GEMINI_API_KEY` | <a class="reference internal" href="/v2/ko/integration/model/gemini">Gemini</a> |
| Anthropic | `agentscope-extensions-model-anthropic` | `anthropic:<model>` | `ANTHROPIC_API_KEY` | <a class="reference internal" href="/v2/ko/integration/model/anthropic">Anthropic</a> |
| Ollama | `agentscope-extensions-model-ollama` | `ollama:<model>` | `OLLAMA_BASE_URL` optional | <a class="reference internal" href="/v2/ko/integration/model/ollama">Ollama</a> |

<Note>

`agentscope-extensions-model-e2e-tests`는 사용자 대상 모델 통합 의존성이 아니라 저장소 테스트 모듈입니다.

</Note>

## 분산 스토리지 (Distributed Store)

다중 레플리카 프로덕션 배포를 위한 풀스택 분산 스토리지 컴포넌트입니다. 단일 `DistributedStore`로 에이전트 상태, 워크스페이스 파일 시스템, 샌드박스 스냅샷, 동시성 락을 구성할 수 있습니다.

- [분산 스토리지 개요](/v2/ko/integration/distributed/index) — `DistributedStore` API, 기능 매트릭스, 혼합 스토어
- [Redis](/v2/ko/integration/distributed/redis) — `AgentStateStore` + `BaseStore` + `SandboxSnapshotSpec` + `SandboxExecutionGuard`
- [MySQL / JDBC](/v2/ko/integration/distributed/mysql) — `AgentStateStore` + `JdbcStore` + `JdbcSnapshotSpec` + `JdbcSandboxExecutionGuard`
- [Alibaba Cloud OSS](/v2/ko/integration/distributed/oss) — `AgentStateStore` + `OssBaseStore` + `OssSnapshotSpec`

## 샌드박스 실행 환경

격리된 코드 실행 스토어입니다. Docker는 내장되어 있으며, 나머지는 독립적인 확장 모듈입니다.

- Docker — 내장 기본값, 추가 의존성 불필요
- [Kubernetes](/v2/ko/docs/harness/sandbox) — `agentscope-extensions-sandbox-kubernetes`
- [AgentRun (Alibaba Cloud)](/v2/ko/docs/harness/sandbox) — `agentscope-extensions-sandbox-agentrun`
- [Daytona](/v2/ko/docs/harness/sandbox) — `agentscope-extensions-sandbox-daytona`
- [E2B](/v2/ko/docs/harness/sandbox) — `agentscope-extensions-sandbox-e2b`

## 메모리

세션 간 사용자 선호도와 사실을 지속시킵니다. 모든 구현체는 `LongTermMemory` 인터페이스를 만족합니다.

- [Mem0](/v2/ko/integration/memory/mem0)
- [Bailian Memory](/v2/ko/integration/memory/bailian)
- [ReMe](/v2/ko/integration/memory/reme)

## RAG 지식 베이스

통합된 `Knowledge` 인터페이스 뒤에 다양한 검색 스토어를 연결합니다.

- [Simple (DIY 임베딩 + 벡터 스토어)](/v2/ko/integration/rag/simple)
- [Bailian Knowledge](/v2/ko/integration/rag/bailian)
- [Dify](/v2/ko/integration/rag/dify)
- [HayStack](/v2/ko/integration/rag/haystack)
- [RAGFlow](/v2/ko/integration/rag/ragflow)

## 스킬 저장소

`AgentSkillRepository`의 여러 스토리지 구현체입니다.

- [Git 스킬 저장소](/v2/ko/integration/skill/git-repository)
- [MySQL 스킬 저장소](/v2/ko/integration/skill/mysql-repository)
- [PostgreSQL 스킬 저장소](/v2/ko/integration/skill/postgresql-repository)
- [Nacos 스킬 저장소](/v2/ko/integration/infrastructure/nacos#스킬-저장소)도 참조하세요

## 채널 어댑터

Harness Channel 인터페이스를 통해 Agent를 메시징 플랫폼에 연결합니다.

- [DingTalk](/v2/ko/integration/channel/dingtalk)
- [Feishu / Lark](/v2/ko/integration/channel/feishu)
- [GitHub](/v2/ko/integration/channel/github)
- [GitLab](/v2/ko/integration/channel/gitlab)
- [WeCom](/v2/ko/integration/channel/wecom)

## Agent 프로토콜

Agent가 외부 세계와 통신하는 표준화된 방법입니다.

- [A2A (Agent-to-Agent)](/v2/ko/integration/protocol/a2a)
- [AG-UI](/v2/ko/integration/protocol/agui)
- [Agent Protocol](/v2/ko/integration/protocol/agent-protocol)

## 인프라 / 미들웨어

Agent를 엔터프라이즈 인프라에 연결합니다.

- [Higress AI Gateway](/v2/ko/integration/infrastructure/higress)
- [Nacos](/v2/ko/integration/infrastructure/nacos)
- [Scheduler (Quartz / XXL-Job)](/v2/ko/integration/infrastructure/scheduler)

## 생태계

런타임, 언어, 디버깅, 트레이닝 확장 기능입니다.

- [Chat Completions Web](/v2/ko/integration/ecosystem/chat-completions-web)
- [AgentScope Studio](/v2/ko/integration/ecosystem/studio)
- [Online Training](/v2/ko/integration/ecosystem/training)

<Note>

Spring Boot 사용자를 위해, 위의 확장 기능 대부분은 수동 연결을 없애는 원라인 통합을 위한 대응 `agentscope-spring-boot-starter-*`를 제공합니다.

</Note>
