---
title: 인프라 / 미들웨어
---

이 확장 기능들은 여러분이 이미 운영 중인 인프라 — 게이트웨이, 레지스트리, 메시지 버스, 스케줄러 — 에 AgentScope를 연결해 주어, Agent가 다른 서비스와 마찬가지로 거버넌스되고 스케줄링될 수 있도록 합니다.

| 확장 기능 | 미들웨어 | 기능 |
| --- | --- | --- |
| [Higress](/v2/ko/integration/infrastructure/higress) | [Higress](https://higress.io/) AI 게이트웨이 | 게이트웨이에서 MCP로 게시된 도구를 Toolkit으로 가져오기 |
| [Nacos](/v2/ko/integration/infrastructure/nacos) | [Nacos](https://nacos.io/) | A2A AgentCard 레지스트리/디스커버리, 프롬프트 설정 센터, 스킬 저장소 |
| [Scheduler](/v2/ko/integration/infrastructure/scheduler) | XXL-Job / Quartz | CRON 일정 또는 고정 주기로 Agent 실행 |

## 이 확장 기능이 담당하는 영역

- **Higress / Nacos**는 "게이트웨이, 레지스트리"를 AgentScope의 수평적 기능으로 전환합니다.
- **Scheduler**는 "Agent가 항상 사람이 트리거하는 것은 아니며, 때로는 스케줄러가 트리거한다"는 사용 사례를 다룹니다.

이 확장 기능들은 서로 독립적이며 자유롭게 조합할 수 있습니다.
