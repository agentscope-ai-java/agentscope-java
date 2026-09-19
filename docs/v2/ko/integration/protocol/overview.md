---
title: 에이전트 프로토콜
---

AgentScope는 에이전트가 외부 세계와 통신할 수 있도록 몇 가지 프로토콜 어댑터를 제공한다. 각각은 서로 다른 문제를 해결한다:

| 확장 | 프로토콜 | 해결하는 문제 |
| --- | --- | --- |
| [A2A](/v2/ko/integration/protocol/a2a) | [Agent-to-Agent](https://a2aproject.github.io/A2A/) | 에이전트끼리 서로 호출하거나 멀티 에이전트 워크플로로 구성하기 |
| [AG-UI](/v2/ko/integration/protocol/agui) | [AG-UI Protocol](https://github.com/ag-ui-protocol/ag-ui) | 프런트엔드 UI를 위한 표준화된 이벤트 스트림 |
| [Agent Protocol](/v2/ko/integration/protocol/agent-protocol) | [Agent Protocol](https://agentprotocol.ai/) | 다른 시스템이 HTTP 기반으로 "작업(task)"을 제출하는 방법 |

> [Chat Completions Web](/v2/ko/integration/ecosystem/chat-completions-web)도 참고한다: OpenAI 호환 API 뒤에 에이전트를 노출한다.

## 선택하기

- **프런트엔드 UI로 에이전트 이벤트를 스트리밍하기(ThinkingBlock 포함)** → AG-UI
- **다른 백엔드 시스템이 REST로 에이전트를 스케줄링하게 하거나 원격 서브에이전트를 호스팅하기** → Agent Protocol
- **여러 에이전트(자체 또는 서드파티)가 서로 호출하게 하기** → A2A

> 계층 구조: AG-UI는 사용자 대면용이고, Agent Protocol은 내부 원격 서브에이전트/작업용 HTTP 표면이며, A2A는 외부 상호운용을 위한 것이다.
