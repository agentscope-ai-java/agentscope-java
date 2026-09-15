# Memory

`LongTermMemory`는 여러 턴과 세션에 걸쳐 사용자 선호, 사실, 핵심 요점을 영속화하기 위한 AgentScope 인터페이스입니다. `agentscope-extensions-*` 저장소는 주요 메모리 저장소에 대해 바로 사용할 수 있는 구현체를 제공합니다.

| 확장 | 백엔드 | 적합한 용도 |
| --- | --- | --- |
| [Mem0](mem0.md) | [Mem0](https://mem0.ai/) Platform / 자체 호스팅 | 멀티테넌트 격리와 메타데이터 필터링을 갖춘 범용 시맨틱 메모리 |
| [Bailian](bailian.md) | 알리바바 클라우드 바이리안 메모리 서비스 | rerank / judge / rewrite 기능을 갖춘 클라우드 관리형 메모리 |
| [ReMe](reme.md) | 자체 호스팅 ReMe 서비스 | 궤적(trajectory) 요약 기능을 갖춘 워크스페이스 수준 메모리 |

세 가지 모두 동일한 `io.agentscope.core.memory.LongTermMemory` 인터페이스를 구현하며, Agent에 연결하는 방법도 동일합니다.

```java
ReActAgent agent = ReActAgent.builder()
    .name("Assistant")
    .model(model)
    .longTermMemory(memory)                       // any of the three implementations
    .longTermMemoryMode(LongTermMemoryMode.BOTH)  // record AND retrieve
    .build();
```

## 구현체 선택하기

- **로컬에서 `docker run` 한 번으로 시작하고 싶다면** → Mem0 또는 ReMe
- **이미 알리바바 클라우드 바이리안을 사용 중이라면** → Bailian
- **메타데이터 필터링(비즈니스 차원별 메모리 분할)이 필요하다면** → Mem0
- **엔드투엔드 대화 궤적 요약을 중시한다면** → ReMe

각 구현체는 초기화 파라미터와 필터 의미론에서만 차이가 있으며, Agent 입장에서는 투명하게 동작합니다. 자세한 내용은 각 하위 페이지를 참고하세요.
