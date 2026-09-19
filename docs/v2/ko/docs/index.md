---
title: AgentScope 2.0이란?
description: Harness 엔지니어링, 엔터프라이즈급 분산 배포, 그리고 재설계된 기반.
---

AgentScope Java 2.0은 "에이전트를 만드는" 툴킷에서 **프로덕션 환경에서 에이전트를 운영**하기 위한 완전한 플랫폼으로 한 단계 도약합니다. 이러한 개선 사항은 각각 서로 다른 문제를 해결하는 세 가지 초점 영역으로 나뉩니다.

<Note>

AgentScope Java 2.0은 가능한 한 1.x와의 호환성을 유지해 대부분의 사용자가 원활하게 업그레이드할 수 있도록 하는 것을 목표로 하지만, 핵심 추상화, API, 아키텍처에 대한 중요한 개선과 함께 API 수준의 호환성이 깨지는 변경도 도입합니다. 전체 마이그레이션 가이드는 [V1 마이그레이션 가이드](/v2/ko/docs/change-log)를 참고하세요.

</Note>

## 1 · Harness 엔지니어링 — 장기 실행되는 복잡한 작업을 위해 구축됨

순수한 ReAct 루프는 하나의 추론 턴만 해결합니다. 실제 작업은 몇 시간씩 실행되고, 상태가 누적되며, 지속적인 스킬을 요구합니다. **Harness**는 에이전트가 무기한 안정적으로 유지되고, 역량을 축적하며, 수동으로 컨텍스트를 손보지 않고도 복잡한 작업을 완료할 수 있도록 엔지니어링 뼈대를 구축합니다 — 추론 코어는 그대로 유지되고, 그 위에 역량이 계층으로 쌓입니다.

<CardGroup cols={2}>


<Card title="자체 진화 & 스킬 저장소" href="/v2/ko/docs/harness/skill">


성공한 패턴은 `workspace/skills/`에 Markdown 스킬로 저장되며, 각 단계마다 로드되고 세션 간에 공유됩니다 — 실행할 때마다 노하우가 축적됩니다.

</Card>

<Card title="계층화된 메모리 관리" href="/v2/ko/docs/harness/memory">


세 계층: 컨텍스트 내 대화, 에이전트가 큐레이션한 `MEMORY.md`, 디스크 상의 사실 기록. 자동 압축이 프롬프트 크기를 제한하고, `memory_*` 도구가 명시적인 회상을 제공합니다.

</Card>

<Card title="서브에이전트" href="/v2/ko/docs/harness/subagent">


Markdown 스펙으로 자식 에이전트를 선언하세요. `agent_spawn` / `agent_send`를 통해 동기 또는 백그라운드로 실행합니다. 백그라운드 결과는 `system-reminder`를 통해 다시 푸시됩니다 — 폴링이 필요 없습니다.

</Card>

<Card title="자동 컨텍스트 관리" href="/v2/ko/docs/harness/compaction">


구조화된 압축은 목표, 상태, 발견, 다음 단계를 보존합니다. 지나치게 큰 도구 결과는 디스크로 오프로드되고, context-overflow 재시도가 안전망 역할을 합니다.

</Card>

<Card title="복잡한 작업을 위한 Plan Mode" href="/v2/ko/docs/harness/plan-mode">


장기 작업을 위한 읽기 전용 계획 상태입니다. 계획은 `workspace/plans/` 아래에 영속화되어 실행을 이끌며, 의도와 행동을 분리된 상태로 유지합니다.

</Card>

<Card title="기반으로서의 Workspace" href="/v2/ko/docs/harness/workspace">


페르소나, 지식, 스킬, 서브에이전트 스펙, 세션 로그 — 모두 디스크 상의 Markdown / JSON으로 존재하며, 매 턴마다 프롬프트에 자동으로 주입됩니다.

</Card>


</CardGroup>

## 2 · 엔터프라이즈급 분산 배포

프로덕션 에이전트는 많은 테넌트에 서비스를 제공하고, 신뢰할 수 없는 도구 코드를 안전하게 실행하며, 진행 중인 컨텍스트를 잃지 않고 롤링 재시작을 견뎌야 합니다. AgentScope 2.0은 **상태 비저장 수평 확장**을 위해 구축되었습니다: 어떤 레플리카든 모든 사용자의 전체 컨텍스트를 이어받을 수 있고, 샌드박스 상태는 프로세스 간에도 재개되며, 권한 게이트와 다차원 격리가 모든 테넌트의 데이터를 분리된 상태로 유지합니다.

<CardGroup cols={2}>


<Card title="멀티 테넌트 격리" href="/v2/ko/docs/building-blocks/context">


`session` / `user` / `agent` / `org` 간에 상태를 격리합니다. `RuntimeContext` 키는 워크스페이스 경로, KV 네임스페이스, 샌드박스 상태 슬롯을 통해 흐릅니다.

</Card>

<Card title="안전한 샌드박스 실행" href="/v2/ko/docs/harness/sandbox">


도구는 로컬 서브프로세스, Docker, 원격 AgentRun 등 격리된 환경에서 실행되며, 스냅샷/재개를 통해 장기 작업이 재시작을 견뎌냅니다.

</Card>

<Card title="도구 권한 제어" href="/v2/ko/docs/building-blocks/permission-system">


정적 규칙, 도구 카테고리, 입력 분석 위에 구축된 3단계 엔진(허용 / 승인 / 거부). 민감한 도구는 사람의 승인을 요구합니다 — HITL은 일급 시민입니다.

</Card>

<Card title="우아한 시작/중지 & 세션 복구" href="/v2/ko/docs/building-blocks/context">


동일한 `(userId, sessionId)`가 어떤 프로세스에서든 전체 대화를 재개합니다. `AgentStateStore`(인메모리 / JSON 파일 / MySQL / Redis)가 무중단 롤링 배포와 크래시 복구를 뒷받침합니다.

</Card>


</CardGroup>

## 3 · 기반 프레임워크 — 더 가볍고 개발자 친화적인 코어

가장 아래 레이어가 재설계되었습니다. 메시지, 이벤트, 확장 모델은 이제 더 작고, 더 직교적이며, 다루기에 더 즐겁습니다 — 그리고 HITL과 이벤트 스트리밍은 더 이상 위에 덧붙인 부가 기능이 아니라, 프레임워크가 동작하는 방식의 일부입니다.

<CardGroup cols={2}>


<Card title="내장된 이벤트 스트리밍" href="/v2/ko/docs/building-blocks/message-and-event">


모델 호출, 텍스트 델타, 도구 실행, 도구 결과 — 모든 단계가 하나의 스트림 위에서 타입화된 이벤트로 드러납니다. 한 번만 구독하면 UI가 실시간으로 따라옵니다.

</Card>

<Card title="더 단순한 메시지 모델" href="/v2/ko/docs/building-blocks/message-and-event">


텍스트, 파일, 이미지, 오디오, 비디오, 사고(thinking), 도구 결과 — 모두 하나의 `ContentBlock` 형태입니다. role이 엄격한 생성 방식이 빌드 시점에 잘못된 메시지를 잡아냅니다.

</Card>

<Card title="hook이 아닌 Middleware" href="/v2/ko/docs/building-blocks/middleware">


다섯 단계(`onAgent` / `onReasoning` / `onActing` / `onModelCall` / `onSystemPrompt`)가 v1의 느슨한 hook을 대체합니다. 각 관심사는 자신의 레이어에 머물며 깔끔하게 조합됩니다.

</Card>

<Card title="일급 시민인 Human-in-the-loop" href="/v2/ko/docs/building-blocks/permission-system">


도구 인자를 확인하거나, 민감한 작업을 승인하거나, 실행 도중 외부 시스템으로 넘길 수 있습니다. 에이전트는 멈췄던 바로 그 지점에서 정확히 일시 중지하고 재개합니다 — 별도의 뼈대가 필요 없습니다.

</Card>


</CardGroup>

---

아직 마이그레이션 여부를 검토 중이라면, [V1 마이그레이션 가이드](/v2/ko/docs/change-log)가 마이그레이션 가이드(필수 vs 권장)와 새로운 기능 섹션으로 모든 변경 사항을 분석해 드립니다 — 업그레이드를 처음부터 끝까지 계획하기에 충분합니다. 버전별 변경 사항은 [릴리스 노트](/v2/ko/docs/others/release-notes)를 참고하세요.
