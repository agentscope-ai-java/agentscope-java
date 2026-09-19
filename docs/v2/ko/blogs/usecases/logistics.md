---
title: "구성 주도에서 비즈니스 네이티브로: AgentScope를 활용한 엔터프라이즈급 Agent 개발 실천"
---

## 01 배경

### 1.1 출발점

#### 1.1.1 비즈니스 관점의 사고

> 참고: 저자는 해외 물류 비즈니스-파이낸스 팀 소속이므로, 결제 프로세스는 우리에게 핵심 역량입니다.

최근의 요구사항 계획 논의는 기존 제품에 대한 깊은 성찰을 촉발했습니다. 제품 동료가 우리가 이전에 출시한 "결제 승인 Agent"에 대해 몇 가지 핵심 질문을 제기했으며, 이는 현재 시스템의 한계를 직접적으로 겨냥한 것이었습니다.

**첫째, Agent는 진정한 "기억"과 "지혜"를 가지고 있는가?**

현재 승인 시나리오에는 반복적인 거부 사유가 대량으로 존재합니다. 예를 들어, 공급업체에 미해결된 마이너스 청구서가 있다는 이유로 결제 A를 거부한 후, 며칠 뒤 결제 A+가 제출되었을 때, Agent는 이전 거부 사유를 기억하고 그 문제가 해결되었는지 자동으로 확인할 수 있는가? 나아가, Agent는 승인 궤적을 분석하여 사용자 A의 "한 번에 명확한 피드백"과 같은 효율적인 패턴을 발견하고, 승인을 통과시키기 위해 여러 라운드가 필요한 사용자 B 같은 이들에게 이러한 모범 사례를 추천하여 전체 효율을 향상시킬 수 있는가?

**둘째, 상호작용이 더 개인화될 수 있는가?**

Agent가 한 사람의 운영 습관을 자동으로 기억하고, 대화 중에 사용자가 평소 관심을 두는 것을 기반으로 맞춤형 팁을 제공할 수 있는가?

**셋째, 운영 담당자가 셀프 서비스로 빠른 커스터마이징을 할 수 있는가?**

우리는 향후 비기술직 운영 동료들이 참여하여 누구나 빠르게 자신만의 전용 Agent를 커스터마이징할 수 있기를 바랍니다. 예를 들어 "청구된 비용 추적 분석" 시나리오에서, 비즈니스 담당자가 "이 요금이 왜 이렇게 계산되는지"를 이해하도록 돕는 것입니다. 이상적으로는 빠른 구성, 빠른 테스트, 빠른 출시 역량을 갖춰야 합니다.

**넷째, 금융급 운영 안전성을 어떻게 보장할 것인가?**

금융 안전은 최우선 순위입니다 — 모든 쓰기 작업은 엄격한 승인과 확인이 필요합니다. 모델이 2차 확인을 수행하도록 Prompt 힌트에 의존하는 현재 관행으로는 100% 차단을 달성할 수 없습니다. 자율 계획 및 자율 실행 워크플로 내에서 어떤 쓰기 작업이 반드시 사용자 확인을 거쳐야 하는지 엄격하게 제어하는 더 정밀한 메커니즘이 필요합니다.

**마지막으로, 정밀한 카나리 릴리스와 실험이 지원되는가?**

시스템은 특정 사용자 그룹을 대상으로 한 카나리 릴리스, 나아가 A/B 테스트까지 지원하여 서로 다른 전략의 효과를 검증할 수 있어야 합니다.

이러한 질문들은 우리가 이전에 구축한 것이 단순한 Chatbox에 불과할 뿐, 진정으로 지능적인 Agent가 아니었음을 갑자기 깨닫게 했습니다. 이 통찰은 팀을 깊은 성찰로 이끌었습니다. 이후 한 달 동안 우리는 다른 팀의 사례를 연구하고, 여러 차례 분석과 토론을 거쳐 마침내 다음과 같은 기술적 사고 방향을 도출했습니다.

#### 1.1.2 기술적 사고

##### 1.1.2.1 생각

먼저 질문해 봅시다: 여러분의 high-code는 실제로 어떤 모습입니까?

우리는 Google ADK와 AgentScope 같은 프레임워크 위에 구축된 여러 Agent 애플리케이션(Java)을 관찰했습니다. 이들은 놀랍도록 비슷한 구조를 보였습니다.

```text
my-agent-app/
├── FinanceAgent.java ← new ReActAgent(), 하드코딩된 프롬프트
├── HRAgent.java ← new ReActAgent(), 하드코딩된 또 다른 프롬프트
├── SalesAgent.java ← new ReActAgent(), 또 다른 프롬프트
├── FinanceTool.java ← @Tool 애너테이션, 직접 등록
├── HRTool.java ← @Tool 애너테이션, 직접 등록
├── SessionManager.java ← 자체 구축한 세션 관리
├── ContextCompressor.java ← 자체 구축한 컨텍스트 압축
├── SSEController.java ← 자체 구축한 SSE 스트리밍 출력
└── ...
```

모든 팀이 같은 일을 하고 있습니다.

1. **직접 작성한 Agent 클래스**: 각 애플리케이션마다 10개 이상, 심지어 수십 개의 직접 작성된 Agent가 있으며; 각 Agent는 생성자에서 하드코딩된 프롬프트, 지정된 모델, 등록된 도구를 가진 Java 클래스에 대응합니다.
2. **자체 구축한 인프라**: 세션 영속화, 컨텍스트 압축, SSE 프로토콜 적응, HITL 프레임워크 — 모든 팀이 각자 만듭니다.
3. **변경에는 릴리스가 필요함**: 프롬프트 변경, SKILL 추가, MCP 도구 추가는 모두 코드 변경, 컴파일, 배포가 필요합니다 — 단순한 프롬프트 튜닝이 전체 개발 주기가 됩니다.

이것은 AI 문제가 아니라 엔지니어링 문제입니다.

##### 1.1.2.2 Agent 엔지니어링의 전형적인 악취(Bad Smell)

Agent 시스템의 실제 구현에서는, 통합된 엔지니어링 표준과 플랫폼 지원이 부족하기 때문에 코드베이스가 종종 대량의 중복되고, 경직되고, 유지보수하기 어려운 구현 패턴으로 채워집니다. 다음은 개발 비용을 증가시킬 뿐만 아니라 심각한 안정성 및 보안 위험까지 초래하는 7가지 전형적인 "악취"를 정리한 것입니다.

**악취 1: 보일러플레이트 확산 — "생성"이 아닌 "복제"**

현상: 각 Agent는 독립된 Java 클래스로 구현되며, 모든 클래스는 동일한 이중 검사 잠금(DCL) 싱글턴 패턴, `@PostConstruct` 초기화 로직, 하드코딩된 의존성 주입을 포함합니다.

```java
// 나쁜 예: 모든 Agent 클래스가 200줄 이상의 보일러플레이트를 복제함
@Component
public class FinanceMasterAgent {
    private static volatile LlmAgent instance;
    // ... 표준 DCL getInstance() 구현 ...
    @PostConstruct
    public void initializeOnStartup() {
        AgentRegistry.register(getInstance());
    }
}
```

분석: Agent가 20개 이상인 프로젝트에서 `private static volatile instance`는 30번 이상 나타나며, `getInstance()` 로직은 20번 이상 복사됩니다. 새 Agent를 추가하는 것은 본질적으로 "복사 & 붙여넣기 → 클래스명/프롬프트/도구 목록 수정 → `@DependsOn` 고치기"가 됩니다. 이러한 "복제식 개발"은 극도로 높은 코드 중복을 초래하며, 기반 프레임워크가 업그레이드될 때마다 모든 Agent 클래스를 수정해야 하므로 유지보수 비용이 선형적으로 증가합니다.

**악취 2: 핵심 구성 하드코딩 — 프롬프트 반복이 막힘**

현상: Agent의 세 가지 핵심 요소 — 프롬프트(지시문), 모델 파라미터(Temperature 등), API Key — 가 모두 코드에 Java 상수나 문자열 리터럴로 하드코딩되어 있습니다.

```java
// 나쁜 예: 500줄 이상의 비즈니스 의사결정 트리가 Java 메서드에 하드코딩됨
private static String buildInstruction() {
    return """
        당신은 전문 금융 진단 어시스턴트입니다...
        ## Act 0: 사용자 입력 식별...
        ## Act 1: 근본 원인 진단... (30개 이상의 오류 코드 분기)
        """;
}
// 나쁜 예: 민감한 정보와 모델 구성이 하드코딩됨
public class ModelFactory {
    public static final String API_KEY = "sk-xxxxxxxx"; // Git에까지 커밋됨
}
```

분석:

- 개발 병목: 프롬프트 튜닝은 "코드 변경 → 코드 리뷰 → 컴파일 → 배포"의 전체 주기를 거쳐야 하며 몇 주가 걸리는 반면, 비즈니스 프롬프트 반복 요구는 며칠 단위로 필요합니다.
- 보안 위험: API Key와 같은 민감한 정보가 코드 저장소에 직접 노출됩니다.
- 유연성 부족: 서로 다른 Agent의 Temperature 파라미터가 여기저기 흩어져 있어 중앙에서 제어하거나 동적으로 조정할 수 없습니다.

**악취 3: 경직된 역량 바인딩 — 도구와 Skill을 동적으로 오케스트레이션할 수 없음**

현상: Agent가 보유한 도구 집합(Tools)과 스킬(Skills)은 빌드 시점에 고정되며, 하드코딩된 목록이나 Classpath 경로를 통해 로드됩니다.

```java
// 나쁜 예: 도구 목록이 코드에 용접되어 있음
.tools(List.of(
    AgentTool.create(OrderAgent.getInstance()),
    AgentTool.create(RefundAgent.getInstance())
))
// 나쁜 예: Skill 경로가 하드코딩됨
Skill diagnosisPlan = factory.createSkillFromResource("skills/diagnosis/diagnosis_plan");
```

분석:

- 운영 어려움: MCP 서비스에 장애가 발생하여 일시적으로 오프라인 처리해야 하거나, 새 도구를 카나리 릴리스해야 할 때 코드를 수정하고 새 버전을 릴리스해야 합니다 — 런타임 핫스왑이 불가능합니다.
- 높은 결합도: 수백 개의 흩어진 역량 바인딩으로 인해 의존성에 대한 전역적인 뷰를 구축하기 어려워, Agent 역량의 재사용과 조합을 방해합니다.

**악취 4: MCP 관리 부재 — 연결 누수와 환경 혼동**

현상: MCP Server 주소가 코드에 하드코딩되어 있고, 모든 도구 호출이 연결을 다시 수립하며, 연결 풀 관리가 없습니다.

```java
// 나쁜 예: 사전 릴리스 환경 주소가 하드코딩되어 있어, 프로덕션 장애를 일으킬 가능성이 매우 높음
private static final String ORDER_MCP_URL = "https://...pre-region...";
// 나쁜 예: 매 호출마다 TCP 핸드셰이크 + 초기화 + 종료를 수행함
public static String invokeMcpTool(...) {
    McpSyncClient client = McpClient.sync(transport).build();
    client.initialize();
    // ... 실행 ...
    client.closeGracefully();
}
```

분석:

- 고위험 구성: 릴리스 시 정리되지 않은 하드코딩된 환경 주소(예: `pre-`)는 프로덕션이 사전 릴리스 서비스를 호출하게 만들어 P1급 장애를 초래합니다.
- 성능 저하: ReAct 루프에서의 잦은 단기 연결은 100~500ms의 추가 지연을 일으키며, 서킷 브레이킹이 없어 고동시성 상황에서 MCP Server를 쉽게 끌어내릴 수 있습니다.

**악취 5: 신원 전파 단절 — "신의 시점" 보안 위험**

현상: 사용자 신원은 Session 계층에서만 표시되며 하위 도구나 MCP 호출로 전파되지 않습니다; 모든 작업이 애플리케이션 서비스 계정 신원으로 실행됩니다.

```java
// 나쁜 예: 도구가 호출될 때 operator가 비어 있거나 애플리케이션 수준 인증이 사용됨
request.put("creatorNo", "");
ClientMcpRequestAuth auth = ClientMcpRequestAuth.of(serverUrl).generateAuthMaterial(); // 애플리케이션 신원
```

분석:

- 감사 추적 누락: Agent를 통해 어떤 사용자가 민감한 작업(예: 규칙 생성, 승인)을 수행했는지 추적할 수 없습니다.
- 권한 통제 불가: 사용자 수준 데이터 격리와 권한 제어의 부재는 제로 트러스트 보안 원칙을 위반합니다. 금융 등 민감한 시나리오에서 이는 용납할 수 없는 레드라인입니다.

**악취 6: HITL(인간 개입) 로직이 분산되고 취약함**

현상: 인간 확인 로직이 각 도구마다 다양한 방식으로 개별 구현됩니다: 일부는 LLM이 인식하기를 바라며 매직 문자열을 반환하고, 일부는 Redis를 폴링하며 스레드를 블록하며, 일부는 흐름을 중단시키기 위해 예외를 던집니다.

```java
// 나쁜 예 6a: LLM이 매직 문자열을 이해하는 것에 의존하며, 무한 루프에 빠지기 쉬움
return "NEED_CONFIRM: amount too large, please confirm";
// 나쁜 예 6b: 확인을 기다리며 Agent 스레드를 블록함
Thread.sleep(1000); // 귀중한 스레드 리소스를 점유함
```

분석:

- 신뢰할 수 없음: LLM이 중지 신호를 올바르게 파싱하지 못할 수 있으며, 무한 루프나 오작동을 일으킬 수 있습니다.
- 리소스 낭비: 블로킹 대기는 서버 스레드 리소스를 심각하게 소모합니다.
- 파편화된 경험: 도구마다 확인 상호작용이 일관되지 않아 프론트엔드 적응이 어렵습니다.
- 불완전한 커버리지: 외부 MCP 도구는 로컬 HITL 로직을 내장할 수 없어, 고위험 작업이 필요한 인간 감독 없이 방치됩니다.

**악취 7: 구성 버전 관리 부재 — 롤백이 "고고학"과 같음**

현상: 구성이 데이터베이스나 구성 센터로 외부화되었더라도, 완전한 버전 스냅샷, 감사 로그, 카나리 메커니즘은 여전히 부재합니다.

분석:

- 어려운 롤백: 프롬프트, 모델 파라미터, 도구 집합이 종종 함께 변경됩니다. 환각이 발생하거나 효과가 떨어질 때, 관련 구성이 서로 다른 Commit이나 레코드에 흩어져 있기 때문에 "지난주의 안정된 상태"로 원클릭 롤백할 방법이 없습니다 — 복구 과정이 "고고학"과 같습니다.
- 추적 불가: "누가 언제 무엇을 변경했는지"에 대한 감사 체인이 없으며, A/B 테스트나 카나리 릴리스도 수행할 수 없어, 최적화 효과를 정량화할 수 없고 리스크를 통제할 수 없습니다.

**요약**: 위의 악취들은 "프로토타입 데모"에서 "프로덕션 시스템"으로 진화하는 Agent 엔지니어링의 전형적인 성장통을 반영합니다. 해결책은 싱글턴 관리, 구성 외부화, 동적 오케스트레이션, 연결 풀, 신원 전파, HITL 인터셉션, 버전 제어를 플랫폼 기반 역량으로 하향시키는 통합 Agent Runtime 플랫폼을 구축하여, 개발자가 바퀴를 재발명하는 대신 비즈니스 로직과 프롬프트 최적화에 집중할 수 있도록 하는 데 있습니다.

#### 1.1.3 결론 요약

위의 비즈니스 및 기술적 사고를 바탕으로 다음과 같이 결론짓습니다.

1. 장기 메모리는 구조화되고, 영속화되며, 커스터마이징 가능해야 합니다.
2. Agent의 자기 진화는 비즈니스 차원에서 정의되어야 하며, 자동 폐루프를 달성하려면 구체적이고 유연한 높은 커스터마이징이 필요합니다.
3. 운영 담당자가 언제든지 진정으로 Agent를 커스터마이징할 수 있도록 하려면 무코드, 구성 기반 접근이 필요합니다: 새 Agent/SKILL/MCP를 추가하는 데 코드가 필요 없어야 하며 — 페이지 구성만으로 즉시 출시할 수 있어야 하고, 사용 장벽을 낮춰야 합니다.
4. HITL 역량이 엔지니어링급의 정밀한 매칭을 달성하려면 프레임워크 수준의 변혁이 필요합니다 — 엔지니어링급 HITL을 구축해야 합니다.
5. 카나리 전략 커스터마이징 역시 당연히 엔지니어링급 개발이 필요합니다.

우리는 처음에는 내부 AI 플랫폼 내에서 최적의 솔루션을 찾기를 바랐지만, 점차 위의 모든 것을 달성하는 데 있어 AI 기술 플랫폼에 전체 링크 폐루프를 의존하기 어렵다는 것을 발견했으며, 그 이유는 다음과 같습니다.

1. 예를 들어, 장기 메모리의 비즈니스 커스터마이징 — 플랫폼이 일반적으로 제공하는 것은 RAG 모드이며, 이는 정밀하게 제어하기 어렵습니다.
2. 운영 사용성은 더욱 어렵습니다 — 결국 Agent 구성 플랫폼은 기술적 세부사항으로 가득 차 있으며, 비즈니스 플랫폼과의 정밀한 바인딩에는 상당한 기술적 변혁이 필요합니다.
3. 엔지니어링급 HITL 커스터마이징 역시 기술 플랫폼에 의존할 수 없습니다.

위의 사고를 바탕으로, 억누를 수 없는 아이디어가 우리 마음속에서 계속 자라났습니다: high-code 개발을 기반으로, 비즈니스-파이낸스 플랫폼과 완전히 깊이 통합된 Agent 플랫폼을 구축하고 싶다는 것입니다 — 빠른 구성, 빠른 릴리스, 카나리, 자기 진화를 갖춘.

### 1.2 플랫폼 구축 핵심 포인트 요약

**1. 완전히 구성 주도적인 Agent 실행 엔진**

- **무코드, 구성 기반 접근**: 새 Agent/SKILL/MCP를 추가하는 데 코드가 필요 없습니다 — 페이지 구성만으로 즉시 출시하며, 사용 장벽을 낮춥니다.
- **경량 격리 Runtime**: "사용 시 빌드" 메커니즘을 채택하여, 런타임에 경량 Agent 인스턴스 셸을 동적으로 구성합니다. 이 메커니즘은 하부 Skill, SKILL, MCP 인스턴스를 재사용하여 ① 효율적인 리소스 공유와 ② 우수한 성능을 보장하는 동시에, Agent 세션 실행 환경의 완전한 격리를 달성하여 안정성과 보안을 보장합니다.

**2. 다양한 생태계 플랫폼과의 저비용 호환성**

- 범용 Skill 마켓플레이스 확장 역량: 다른 Skill 마켓플레이스를 빠르게 지원할 수 있음
    - 프로덕션 환경: Aone Skill 마켓플레이스와 통합하여, 고가용성, 고표준의 프로덕션급 요구사항을 충족합니다.
    - 개발 및 테스트: OSS Skill을 구축하여, 유연한 디버깅 및 검증 환경을 제공해 반복 주기를 가속화합니다.

**3. Agent 효과 진화의 전방위적 촉진**

- 내부 루프: 대화 및 사용자 작업 궤적을 영속화하고 구조화하며, 일정에 따라 자동으로 선호도와 사례를 통합합니다.
    - 수집 전략의 구성 가능한 활성화와 커스터마이징 지원
    - 특징: 자기 폐루프(self-closing-loop)
- 외부 루프: 사용자 피드백 폐루프 최적화, 세션 평점, 도구 호출 성공률, 작업 완료율과 같은 메트릭을 자동으로 수집하고, 인간이 주석을 단 데이터와 결합하여 프롬프트와 Skill 전략 반복을 주도하여, Agent를 점점 더 정확하게 만듭니다.
    - 자동 폐루프가 아니며, 인간의 개입이 필요함
- A/B 실험과 카나리 릴리스: 다중 버전 Agent 병렬 실행과 트래픽 분할을 지원하며, 실제 비즈니스 데이터로 개선 효과를 검증하여, 매 업그레이드가 정량화 가능한 경험 개선을 가져오도록 보장합니다.
    - 자동 폐루프가 아니며, 인간의 개입이 필요함

**4. 완전히 수직적인 비즈니스 Agent 구성, 비즈니스-파이낸스 플랫폼 관리 콘솔과의 통합, 완전히 자유로운 형태의 페이지 커스터마이징 달성**

- **비즈니스 시맨틱 네이티브 매핑**: Agent 구성 항목은 추상적인 기술 파라미터가 아니라 비즈니스-파이낸스 도메인 비즈니스 객체(예: 인보이스 유형, 정산 엔티티, 비용 카테고리)에 직접 대응합니다; 비즈니스 담당자는 하부 모델 구조를 이해하지 않고도 Agent 동작 경계를 정밀하게 정의할 수 있습니다.
- **통합 관리 콘솔 거버넌스**: Agent 생성, 게시, 권한 할당, 버전 관리가 모두 비즈니스-파이낸스 관리 백엔드에 통합되어, 기존 조직 구조, 역할 시스템, 승인 흐름과 매끄럽게 연결되어, Agent 거버넌스가 엔터프라이즈 관리 시스템과 일치하도록 보장합니다.
- **무코드 페이지 커스터마이징**: 시각적 에디터를 통해 대화 인터페이스, 폼 필드, 결과 표시 카드, 작업 버튼을 자유롭게 배치할 수 있도록 지원합니다; 서로 다른 비즈니스 라인이 프론트엔드 개발 없이도 독립적으로 전용 상호작용 경험을 만들어, 개인화된 요구에 빠르게 대응할 수 있습니다.

**5. 전체 링크 신원 표시 및 세밀한 권한 제어 메커니즘 구축**

원래 시스템에서 우리는 Web 계층을 기반으로 권한 관리를 구현했으며, ACL, 데이터 마킹, 사용자 화이트리스트 등의 수단으로 계층적 관리를 수행했습니다. 하지만 MCP가 HSF 인터페이스 계층까지 내려가고 Agent가 호출 체인을 주도하게 되면서, 전통적인 Web 계층 권한 모델은 더 이상 새로운 호출 경로를 커버할 수 없게 되었습니다.

## 02 기술 선정

Agent 런타임 프레임워크 선정에서, 우리는 LangChain, Google ADK, AgentScope라는 세 가지 주류 기술 시스템을 체계적으로 평가했습니다. 최종적으로 AgentScope를 핵심 기반으로 선택한 것은 엔터프라이즈급 프로덕션 요구사항, Alibaba 내부 생태계 적합성, 비즈니스-파이낸스 요구사항의 특수성이라는 세 가지 차원에 근거한 종합적인 결정이었습니다.

### 2.1 세 프레임워크의 핵심 역량 비교

![세 프레임워크의 핵심 역량 비교](https://mmbiz.qpic.cn/mmbiz_jpg/bvDbzNRia8j3ywu8nhfPjptJvUHNNSeO8Ao4BIz4NSnHPFnLCtQyD9AckiaKTwsUocPGzpZ3OOnnknORmdHITRqGU77JOYb7oK8oYcoXibLYSw/640?wx_fmt=jpeg&from=appmsg)

### 2.2 최종 선택

![최종 선택: AgentScope](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j3y2iaYibfXsUyoZiayltDUG2aZjqhkAfSXqKQkhA9pwMtY0uUOAiamgaCBK54f8nSa16djxaic8FC80XOIwhUiaQPtFA6eqJCKCzWiaA/640?wx_fmt=png&from=appmsg)

## 03 AgentScope의 3계층 아키텍처 역량 분석

저는 AgentScope가 세 계층의 역량을 가지고 있다고 생각합니다: 모델 계층, ReAct 추론 루프 계층, 외부 추상화 계층입니다.

### 3.1 모델 계층

아키텍처의 가장 하위 계층으로서, 그 핵심은 LLM과의 상호작용이며, 다음 5가지 요소를 중심으로 합니다.

**1. 통합 추상화와 프로토콜 분리**

- 규칙: 모든 모델 구현은 `ChatModelBase`를 상속해야 하며, Formatter 메커니즘을 사용하여 플랫폼에 독립적인 Msg를 벤더별 요청/응답 형식으로 변환합니다.
- 소스 분석: `OpenAIChatModel`은 내부적으로 `Formatter<OpenAIMessage, OpenAIResponse, OpenAIRequest>`를 보유합니다; 새 모델을 추가하려면 핵심 호출 로직을 수정하지 않고 해당 Formatter만 구현하면 되며, OpenAI 호환 프로토콜과 다양한 국내 모델을 자연스럽게 지원합니다.

**2. 세밀한 파라미터 제어와 다층 구성 병합**

- 규칙: 생성 파라미터는 `GenerateOptions`를 통해 표준화되며, "런타임 파라미터 > 빌드 시점 기본 파라미터" 우선순위 병합을 지원하고, 벤더별 확장 필드가 통과할 수 있도록 허용합니다.
- 소스 분석: `GenerateOptions.mergeOptions(primary, fallback)`이 구성 병합을 구현합니다; additionalHeaders/BodyParams/QueryParams라는 세 개의 확장 Map은 프레임워크가 API 진화에 뒤처지지 않도록 보장합니다.

**3. 내장된 엔터프라이즈급 호출 거버넌스**

- 규칙: 타임아웃, 재시도, 서킷 브레이킹과 같은 거버넌스 역량이 모델 계층으로 가라앉아, 외부 래퍼가 아닌 데이터 흐름의 일부로 자동 주입됩니다.
- 소스 분석: `ModelUtils.applyTimeoutAndRetry()`가 Flux 체인에 `.timeout()`과 `.retryWhen(Retry.backoff(...))`을 직접 주입하며, ExecutionConfig를 기반으로 자동으로 적용되어 비즈니스 코드에 침투하지 않습니다.

**4. Reactor Flux 네이티브 스트리밍 출력**

- 규칙: 전체 모델 호출 체인은 완전히 Project Reactor를 기반으로 합니다; 스트리밍/비스트리밍 모두 동일한 인터페이스에서 Flux를 반환하며, 백프레셔와 논블로킹 I/O를 지원합니다.
- 소스 분석: `doStream0()`은 stream 파라미터에 따라 SSE 스트리밍 응답과 `Flux.defer().subscribeOn(boundedElastic())` 동기 호출 사이를 동적으로 전환합니다; 거버넌스 연산자는 스트림에 매끄럽게 내장되며, 상위 계층은 연속적인 데이터 흐름을 인지합니다.

**5. 관측 가능성과 고급 추론 역량의 무침투 통합**

- 규칙: Trace 계측, 프롬프트 캐싱, 도구 호출 강화가 모델 계층에서 자동으로 완료됩니다; 비즈니스 코드는 수동 처리가 필요하지 않습니다.
- 소스 분석: `ChatModelBase.stream()`은 `TracerRegistry.get().callModel()`을 통해 호출을 자동으로 래핑합니다; cacheControl=true일 때, `OpenAIBaseFormatter.applyCacheControl()`이 캐시 마커를 자동으로 추가합니다; toolChoice와 parallelToolCalls 파라미터가 도구 동작을 직접 제어합니다.

![모델 계층 아키텍처 I](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j2vyuibOsbQibMibMjVQOymQcVxoTOX2VY8z2jHJ6XdAN5A5FCfD8zWgxt5Abdt2sGI95MLD7eJFMF6pKYduAc8jvaMYS0VfMWw8c/640?wx_fmt=png&from=appmsg)

![모델 계층 아키텍처 II](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j1DibUdPnJYUkxRCUuFicfJYuVibukFtNia9avWGA43z3Y4wRZdibIjpELEIG5mcdibnHDaX7wavnsMHEibZz20TE8FFys2bNw8KjKkpU/640?wx_fmt=png&from=appmsg)

### 3.2 ReAct 추론 루프 — "Q&A"에서 "다단계 자율 의사결정"으로

1. **표준화된 3단계 루프**: "Thought → Action → Observation" 상태 머신을 엄격히 따르며, 모델이 `maxIterations`에 도달했을 때 자율적으로 종료를 결정하거나 강제 종료함으로써 무한 재귀를 방지합니다.
2. **동적 컨텍스트 주입**: 매 추론 라운드 전에, 사용 가능한 도구 Schema와 이력 궤적이 자동으로 포맷팅되어 프롬프트에 주입되며, 모델 결정이 최신 정보에 기반하도록 보장하고 환각 호출을 줄입니다.
3. **스트리밍 중간 상태 노출**: `Flux<AgentResponse>`를 반환하며, 생각, 도구 호출, 실행 결과 등의 이벤트를 실시간으로 푸시하여, 프론트엔드가 추론 과정을 단어 단위로 표시할 수 있도록 지원하며, 블랙박스 경험을 깨뜨립니다.
4. **도구 실행 안전 격리**: 도구 호출은 독립적으로 실행되며, 파라미터 검증과 반환값 필터링이 이루어집니다; 예외는 모델에 Observation으로 피드백되어 자기 교정에 활용되며, 크래시나 데이터 유출을 방지합니다.
5. **리소스 경계에 대한 강한 제약**: `maxIterations`, `maxTokensPerStep`, `toolTimeout` 등의 구성을 통해, 한도를 초과하는 요청이 Flux 체인에서 자동으로 차단되어, 프로덕션 환경 SLA를 제어 가능하게 유지합니다.

**핵심 가치**: LLM 추론을 모니터링 가능하고, 제약 가능하며, 상호작용 가능한 엔지니어링 서비스로 변환하여, 복잡한 비즈니스-파이낸스 작업의 자율적 완료를 지원합니다.

![ReAct 추론 루프](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j1MeshjI7gMzwO6VSgm5Zk5BMW86TIVic964Wz9CAztjhugiba0T88libnUCLeWGCjIPiaLjQEK5eope1N8yicgeWdXqFNlW873l1fI/640?wx_fmt=png&from=appmsg)

### 3.3 외부 추상화 계층

#### 3.3.1 핵심 클래스

![핵심 클래스 I](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j3ib9BywL0pHsIic713gTxfAiaSjG8BGuSfUuWev261JK5icTkcK3JMq5mK9N7kkaac4sSTfKDVKicdLZZ0wticn8oxB2IAlJdFOibRHg/640?wx_fmt=png&from=appmsg)

![핵심 클래스 II](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j1iaBUVfzYHicXd1agekCxh6mIAy5QzvfVTibiarpjSOCbGvOnnV3RRib5unBhb75Wx4T7etLn6l9ETfNhjQfYmjPRZf3DwoPxL8dTU/640?wx_fmt=png&from=appmsg)

#### 3.3.2 도구 단축 메커니즘

![도구 단축 메커니즘](https://mmbiz.qpic.cn/sz_mmbiz_jpg/bvDbzNRia8j2aaNqJMg8QVlluoW4DZo4porVe4rTHBQ2PI9koG87c9oCPEibvSqnWEtBTsia7ctecEO4s6Pian9cZKAuYK1rcm917qZUdIKsvhw/640?wx_fmt=jpeg&from=appmsg)

## 04 재사용 계층의 3계층 아키텍처

플랫폼 구축 포인트를 고려하는 것 외에도, 실천에 앞서 우리는 재사용 계층 구축도 고려했습니다 — 이는 Alibaba 생태계와의 통합 이후 더 높은 수준의 즉시 사용 가능성(out-of-the-box usability)을 달성하기 위함입니다!

또한 향후 우리의 Agent 구현을 참조하고자 하는 다른 비즈니스들이 더 편리하게 빠른 커스터마이징을 수행할 수 있도록 하는 것도 고려했습니다!

### 4.1 도구 클래스와 Alibaba 생태계 플랫폼 통합

![도구 클래스와 Alibaba 생태계 플랫폼의 통합](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j1XPXn6Kj87p0BzWDFYssB8Mw2pzcF6OUOrUJzJt4sYBIP3hWXTkzGEBDBIlGwfcEvMKicmrKibJKhG3qh8VhwAnS8dyPKHHGv6I/640?wx_fmt=png&from=appmsg)

AgentScope는 네이티브하게 `AgentSkill` + `SkillBox` 인터페이스 정의만 제공하며 어떤 외부 소스도 제공하지 않습니다. 재사용 계층이 엔터프라이즈급 도구와 파생 클래스의 생태계를 완성합니다.

![재사용 계층이 완성한 엔터프라이즈급 도구 생태계](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j3QNqRlZnYSoAmIS1sic4TbsDYUyzicviaO3Fr2dIRwZX4OnBJJOpic0usOQOMKYK7vrJdzeJaXP1dUDmNbdWh9erBcTEmelcVqqVw/640?wx_fmt=png&from=appmsg)

이 계층은 Alibaba 마켓플레이스에 대한 간단한 접근 역량을 제공합니다.

### 4.2 Agent 및 관련 생태계의 등록, 발견, 호출

![Agent 등록 및 발견 I](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1o7ibI14knbyklsPOjHiaVegyIEGbvlhBPbfuUnVtY4nfQS0oE8IviaiatSdBWUaRDPAfLN84XsnojicyNmwBGticX0aoS48aq80pwY/640?wx_fmt=png&from=appmsg)

![Agent 등록 및 발견 II](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j0QXYD5uibKFWaVgiaT1kWH9iavqze4dnp0b13SweLZDycLQF6wc2mTETrjcvvfMLtZAPXGdKHibicFUJiaXe2wmaAlKSZTMZzQ3G9Eg/640?wx_fmt=png&from=appmsg)

### 4.3 링크 오케스트레이션과 관리 | AG-UI 프로토콜 링크 오케스트레이션

![AG-UI 프로토콜 링크 오케스트레이션](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1ib4ASy4QUsSj3rlvxoNgvX5OswkBn5vWQicwTncRA3icicX0pT7vGoNTl0cYhJykPq86SXKvRhZKQwtG2AV2qovBBicgmN69AyDD8/640?wx_fmt=png&from=appmsg)

재사용 계층은 AG-UI 프로토콜(SSE)을 기반으로 프론트엔드에서 LLM까지의 종단 간 스트리밍을 구현합니다: 외부적으로는 AG-UI 표준 입출력을 노출하며(`AguiMvcController`가 `RunAgentInput`을 처리하고 `AguiMessage` 이벤트 스트림을 발행함); 내부적으로는 `ReActAgent`를 핵심 오케스트레이션 엔진으로 사용하여 LLM 추론-도구 호출 루프를 구동하고, `AguiRuntimeContextBuilder` SPI와 `RuntimeContext`를 통해 요청 수준 컨텍스트(사용자 신원, RAG 쿼리)를 운반하며, `Session` SPI와 `AguiSessionManager`를 통해 세션 수준 상태 영속화(Memory / Toolkit / AgentMeta)를 관리합니다.

![AG-UI 종단 간 스트리밍 링크](https://mmbiz.qpic.cn/sz_mmbiz_jpg/bvDbzNRia8j3P12U6CQY6ibyibEggtr41SicE8qmdwHan1ibf1V8Bnvxj5gam1icFzsLictMcfNf6XTFmxHkHodv36oxnYTsM6o4fvWBzdadevic2KA/640?wx_fmt=jpeg&from=appmsg)

런타임 계층에는 많은 개념이 있습니다; 다음 섹션에서 세부사항과 결합하여 실천 방법을 자세히 설명하겠습니다.

### 4.4 전체 링크 관측 가능성

![전체 링크 관측 가능성](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j3zVzFVHq6pZE5YFjYqgvYsHINtyFGZ3icJTozY0kZ6UuXZMja3iabfNicnohTLEGRhzo50BzR8EWalNQibNQniczOt8pFrRPvIp5Ik/640?wx_fmt=png&from=appmsg)

## 05 개발 핵심 포인트를 기반으로 한 실천 과정

앞선 섹션들에서 AgentScope와 재사용 계층의 핵심 아키텍처와 역량 경계를 정리했습니다. 이를 바탕으로, 다섯 가지 주요 구축 포인트를 중심으로 실천 과정을 하나씩 살펴봅니다 — 우리가 어떤 문제에 부딪혔는지, 어떻게 분해했는지, 그리고 해결책을 어떻게 설계하고 구현했는지.

### 5.1 핵심 포인트 하나: DB 주도 Agent 실행 엔진

#### 5.1.1 집중 목표

속담에 "기능은 가치를 위해 봉사한다"는 말이 있습니다. 기술 구현에 깊이 들어가기 전에, 먼저 물류 비용 비즈니스-파이낸스 팀의 미래 업무 현장을 그려봅시다.

우리 플랫폼에 수백 명의 프론트라인 운영 담당자가 있다고 상상해 보세요. 매일 그들은 청구, 정산, 자금 흐름, 인보이스 처리, 재무 회계라는 5가지 핵심 링크 사이를 오갑니다. 현재의 현실은: 거대한 시스템에도 불구하고, 대량의 고가치 에너지가 비효율적인 "수동 검증"과 "반복적 이동"에 소모되고 있다는 것입니다 — 눈은 지친 채 화면 사이를 오가고, 데이터는 스프레드시트 사이에서 기계적으로 복사됩니다. 그들은 해방을 갈망하며, 지치지 않고 정확하며 효율적인 디지털 어시스턴트를 절실히 필요로 합니다.

따라서 우리의 목표는 단순히 몇 가지 고정된 자동화 도구를 제공하는 것이 아니라, 번성하는 Agent 사용 생태계를 구축하는 것입니다.

이 생태계에서, 비즈니스 페인 포인트를 가장 잘 이해하는 사람은 더 이상 원격의 개발자가 아니라 프론트라인 동료들입니다. 우리는 그들에게 Agent를 수동으로 구성할 수 있는 능력을 부여하여, 현재의 비즈니스 변동과 개인화된 요구를 바탕으로 마치 블록을 조립하듯 직접 전용 Agent를 정의하고 훈련할 수 있게 할 것입니다. 대규모 판매 기간 동안의 정산 피크를 처리하든, 복잡한 이상 청구서를 처리하든, 그들은 필요에 따라 구성하고, 즉시 게시하며, 빠르게 반복할 수 있습니다.

이는 효율성 혁명일 뿐만 아니라 역할의 재구성이기도 합니다: 모든 운영 담당자는 지루한 운영자에서 지능형 프로세스의 설계자로 진화하며, "누구나 개발자, 어디에나 지능"을 진정으로 실현합니다.

![Agent 사용 생태계 비전](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j2jwHfUJaSKtjNoqgASzpWJhMdFmh50vX5MGl8nBtWsQiaZPSSric4Bs6wWBoZVI5PuhMLx9kxal4IC41iapwdr4sosiawWwQuwnBI/640?wx_fmt=png&from=appmsg)

#### 5.1.2 DB 설계

![DB 설계 개요](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j2cgjUo9FO8uYpiaeF4xd3mC894tHRk1YE3w9vooNP5CaMUUic9WUia302XANwqdMWwibhkQMbrexhzbzGbiaKSaUOSjgfticL1zoZ4k/640?wx_fmt=png&from=appmsg)

프로젝트는 총 19개의 테이블을 가지며, 기능 도메인에 따라 7개의 모듈로 나뉩니다.

![기능 도메인 모듈 I](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j3t1TeDCm6Obs94Am796JmJs5ISAWIBNPJd8AxB1FNHpRwticSvTI6DBQtUEUrINyNqNrhx9deQmL6FtwibBHibcPS4dATlpbxqE0/640?wx_fmt=png&from=appmsg)

![기능 도메인 모듈 II](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j2fnYFHhfVTia7rDxNtaEkRdbo8IMbkzfQlRumXojOcbU7jRm3McrfxDeoyKqdGAsFlP5lWMwqJbooXGY358tpjibKnO5CIvbcC4/640?wx_fmt=png&from=appmsg)

#### 5.1.3 전체 흐름 핵심 설계(재사용 계층과 함께 참조)

![전체 흐름 핵심 설계](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j0c2NLt9WpyAeQVYM6pb7PR2uicPFQnJAuibAOcgRcnEWXEaD1HWjQPhKlJtZlv63nPQM2Cw0XgibYtgm9AO26AwqgPxvWibQrLUibs/640?wx_fmt=png&from=appmsg)

DB 주도 Agent 실행을 달성하기 위한 핵심 설계 포인트는 다음과 같습니다.

##### 5.1.3.1 핵심 링크 하나: 시작 등록 — DB에서 메모리 내 스냅샷까지

재사용 계층은 대량의 등록 역량을 가지고 있습니다; DB 주도 설계의 핵심은 자연스럽게 DB 데이터를 등록 지점에 연결해야 합니다 — 이는 소켓 패턴의 전형적인 설계입니다!

언제가 연결하기에 적절한 시점일까요? 답: 애플리케이션 시작과 핵심 SQL 속성의 업데이트 모두 레지스트리를 통해 전체 Agent 구성 업데이트를 트리거해야 합니다!

애플리케이션 시작부터 시작하자면: 우리는 하나의 슈퍼 베이스 클래스에서 통합적으로 시작하여, DB를 스캔하고, 연결을 완료합니다 — 그것이 바로 `AgentFactoryRegistry`입니다!

연결 외에도, `AgentFactoryRegistry`의 또 다른 핵심 목표는 런타임 스냅샷을 구축하는 것이며, 이는 이후의 채팅 대화에서 핵심적인 역할을 합니다!

`AgentFactoryRegistry`(`SmartInitializingSingleton`을 구현함)는 모든 Spring Bean이 초기화된 후 3단계 시작을 수행합니다.

1. **정적 인덱스 구축**: 모든 `BaseFinanceAgentFactory` 하위 클래스 Bean(코드에 정의된 정적 Agent)을 수집하여, `agentCode`별로 `staticIndex`에 넣습니다.
2. **DB 읽기 및 분할**: `ac_agent_config`에서 활성화된 모든 구성을 읽고, 정적 집합과 교집합을 취합니다.
    - **정적 Agent**(해당 Factory 클래스가 있음) → `factory.refreshFromDb()` 호출
    - **동적 Agent**(DB에는 있지만 코드에 Factory 클래스가 없음) → `dynamicRegistry.register(agentCode)`를 호출하여 `DynamicAgentEntry`를 자동으로 생성하고, 최종적으로 refreshFromDb도 호출
3. **실패 집계**: 모든 Agent가 끝난 후, fail-fast를 위해 실패 목록이 한 번에 던져집니다.

먼저 `AgentFactoryRegistry`를 살펴봅시다.

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AgentFactoryRegistry implements SmartInitializingSingleton {
    private static final Logger logger = LoggerFactory.getLogger(AgentFactoryRegistry.class);
    @Resource
    private List<BaseFinanceAgentFactory> staticFactories;
    @Resource
    private DynamicAgentRegistry dynamicRegistry;
    @Resource
    private AcAgentConfigMapper acAgentConfigMapper;
    @Resource
    private DynamicAgentProperties dynamicProperties;
    private final Map<String, BaseFinanceAgentFactory> staticIndex = new LinkedHashMap<>();
    @Override
    public void afterSingletonsInstantiated() {
        // ==================== 1단계: 정적 인덱스 구축(방어적 폴백으로 DynamicAgentEntry는 제외) ====================
        for (BaseFinanceAgentFactory f : staticFactories) {
            if (f instanceof DynamicAgentEntry) continue;
            String agentCode = f.simpleName();
            BaseFinanceAgentFactory previous = staticIndex.put(agentCode, f);
            if (previous != null) {
                logger.warn("[AgentFactoryRegistry] duplicated static agentCode '{}': {} replaced by {}",
                        agentCode, previous.getClass().getName(), f.getClass().getName());
            }
        }
        logger.info("[AgentFactoryRegistry] static factories ({}): {}",
                staticIndex.size(), staticIndex.keySet());
        // ==================== 2단계: 전체 DB 집합 읽기, 분할 ====================
        List<AcAgentConfigDO> allEnabledConfigs;
        try {
            allEnabledConfigs = acAgentConfigMapper.selectAllEnabled();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load active configs (status>=1) from ac_agent_config", e);
        }
        if (allEnabledConfigs == null) {
            allEnabledConfigs = Collections.emptyList();
        }
        // 2a. 시작 자체 점검: _published 서브스트링을 포함하는 레거시 agentCode 금지(접미사 방식 R1 네이밍 키 충돌 방어)
        List<String> illegalCodes = allEnabledConfigs.stream()
                .map(AcAgentConfigDO::getAgentCode)
                .filter(c -> c != null && c.contains(AgentVariant.PUBLISHED_SUFFIX))
                .collect(Collectors.toList());
        if (!illegalCodes.isEmpty()) {
            String msg = "ac_agent_config contains agent_code with reserved substring '" + AgentVariant.PUBLISHED_SUFFIX
                    + "' (conflicts with PUBLISHED variant suffix): " + illegalCodes;
            if (dynamicProperties.isStrictMode()) {
                throw new IllegalStateException(msg);
            } else {
                logger.warn("[AgentFactoryRegistry] strict-mode=false, continuing despite illegal agent_code: {}", msg);
            }
        }
        List<String> dbCodes = allEnabledConfigs.stream()
                .map(AcAgentConfigDO::getAgentCode)
                .collect(Collectors.toList());
        Set<String> dbCodeSet = new LinkedHashSet<>(dbCodes);
        Map<String, Throwable> failures = new LinkedHashMap<>();
        // 2b. 정적: DB에 해당하는 활성화된 행이 있어야 함(V1 강한 제약과 일치); refreshFromDb 호출
        for (Map.Entry<String, BaseFinanceAgentFactory> e : staticIndex.entrySet()) {
            String code = e.getKey();
            if (!dbCodeSet.contains(code)) {
                failures.put(code, new IllegalStateException(
                        "static factory exists but ac_agent_config has no active row (status>=1) for agent_code='" + code + "'"));
                continue;
            }
            tryRefresh(code, e.getValue()::refreshFromDb, failures);
        }
        // 2c. 동적: 전체 DB 집합 - 정적 집합, DynamicAgentEntry(DRAFT variant) 자동 생성
        List<String> dynamicCodes = dbCodes.stream()
                .filter(c -> !staticIndex.containsKey(c))
                .collect(Collectors.toList());
        for (String code : dynamicCodes) {
            tryRefresh(code, () -> dynamicRegistry.register(code), failures);
        }
        // 2d. current_publish_version > 0인 agent들을 위해 PUBLISHED variant 등록(key=${code}_published)
        //     참고: PUBLISHED variant 실패는 DRAFT 서비스 가용성에 영향을 주지 않음; 별도로 집계, warn-only, fail-fast 없음
        Map<String, Throwable> publishedFailures = new LinkedHashMap<>();
        List<String> publishedCodes = new ArrayList<>();
        for (AcAgentConfigDO cfg : allEnabledConfigs) {
            Integer cpv = cfg.getCurrentPublishVersion();
            if (cpv == null || cpv <= 0) {
                continue;
            }
            String code = cfg.getAgentCode();
            String publishedKey = AgentVariant.PUBLISHED.registryKey(code);
            tryRefresh(publishedKey,
                    () -> dynamicRegistry.registerPublishedVariant(code),
                    publishedFailures);
            publishedCodes.add(code);
        }
        if (!publishedFailures.isEmpty()) {
            String summary = publishedFailures.entrySet().stream()
                    .map(e -> "  - " + e.getKey() + " → " + e.getValue().getMessage())
                    .collect(Collectors.joining("\n"));
            // PUBLISHED variant 실패는 warn-only이며, fail-fast로 던지지 않음(DRAFT는 계속 서비스 가능)
            logger.warn("[AgentFactoryRegistry] PUBLISHED variant init failed for {} agent(s), DRAFT remains available:\n{}",
                    publishedFailures.size(), summary);
        }
        // ==================== 3단계: 실패 집계 + strict-mode 제어(DRAFT 실패만 fail-fast에 참여) ====================
        if (!failures.isEmpty()) {
            String summary = failures.entrySet().stream()
                    .map(e -> "  - " + e.getKey() + " → " + e.getValue().getMessage())
                    .collect(Collectors.joining("\n"));
            String msg = "Agent init failed for " + failures.size() + " of " + dbCodes.size()
                    + " agent(s):\n" + summary;
            if (dynamicProperties.isStrictMode()) {
                throw new IllegalStateException(msg);
            } else {
                logger.warn("[AgentFactoryRegistry] strict-mode=false, continuing despite failures:\n{}", msg);
            }
        }
        logger.info("[AgentFactoryRegistry] init OK. static={}, dynamic={}, published={}, total={}, strict-mode={}",
                staticIndex.size(), dynamicCodes.size(), publishedCodes.size() - publishedFailures.size(),
                dbCodes.size(), dynamicProperties.isStrictMode());
    }
    private static void tryRefresh(String agentCode, Runnable r, Map<String, Throwable> failures) {
        try {
            long start = System.currentTimeMillis();
            r.run();
            logger.info("[AgentFactoryRegistry] init OK for '{}' in {} ms",
                    agentCode, System.currentTimeMillis() - start);
        } catch (Throwable t) {
            logger.error("[AgentFactoryRegistry] init FAILED for '{}': {}", agentCode, t.getMessage(), t);
            failures.put(agentCode, t);
        }
    }
    // ==================== 외부 조회 / 디스패치 API ====================
    /**
     * 통합 조회: 정적을 먼저 조회하고, 히트하지 않으면 동적을 조회.
     */
    public BaseFinanceAgentFactory find(String agentCode) {
        BaseFinanceAgentFactory f = staticIndex.get(agentCode);
        return f != null ? f : dynamicRegistry.get(agentCode);
    }
    /**
     * 레지스트리 키를 기준으로 새로고침을 디스패치함(키는 DRAFT 비즈니스 agentCode이거나, PUBLISHED인
     * {@code agentCode_published}일 수 있음).
     *
     * <p>등록됨 → {@link BaseFinanceAgentFactory#refreshFromDb}; 등록되지 않음 → 필요 시 동적 등록:</p>
     * <ul>
     *   <li>PUBLISHED 키({@link AgentVariant#PUBLISHED_SUFFIX}와 매칭) →
     *       {@link DynamicAgentRegistry#registerPublishedVariant}(게시된 스냅샷 경로를 통해 로드됨);</li>
     *   <li>DRAFT 키 → {@link DynamicAgentRegistry#register}(드래프트 경로를 통해 로드됨).</li>
     * </ul>
     *
     * <p>이렇게 하면, 클러스터 브로드캐스트 {@code AGENT/xxx_published}가 다른 노드에서 수신될 때,
     * PUBLISHED variant도 올바르게 등록될 수 있으며 새로운 DRAFT agent로 오인되지 않습니다.</p>
     */
    public void refresh(String key) {
        BaseFinanceAgentFactory f = find(key);
        if (f != null) {
            f.refreshFromDb();
            return;
        }
        AgentVariant.ParsedKey parsed = AgentVariant.parse(key);
        if (parsed != null && parsed.variant() == AgentVariant.PUBLISHED) {
            logger.info("[AgentFactoryRegistry] PUBLISHED key '{}' not registered yet, attempting on-demand register", key);
            dynamicRegistry.registerPublishedVariant(parsed.agentCode());
        } else {
            // 런타임에 DB에 새 행이 추가됨 → DRAFT variant의 필요 시 동적 등록
            logger.info("[AgentFactoryRegistry] '{}' not registered yet, attempting on-demand dynamic register", key);
            dynamicRegistry.register(key);
        }
    }
    /**
     * 등록된 모든 Agent의 전체 새로고침.
     * <p>단일 실패는 다른 Agent에 영향을 주지 않음; 실패한 agentCode는 호출자에게 반환됨.</p>
     */
    public Map<String, String> refreshAll() {
        Map<String, String> failures = new LinkedHashMap<>();
        for (String code : allAgentCodes()) {
            try {
                find(code).refreshFromDb();
            } catch (Exception ex) {
                logger.error("[AgentFactoryRegistry] refresh failed for agentCode='{}'", code, ex);
                failures.put(code, ex.getMessage());
            }
        }
        return failures;
    }
    /** 등록된 모든 agentCode(정적 ∪ 동적, 중복 제거, 정렬됨). */
    public Set<String> allAgentCodes() {
        Set<String> all = new LinkedHashSet<>(staticIndex.keySet());
        all.addAll(dynamicRegistry.agentCodes());
        return Collections.unmodifiableSet(all);
    }
    /** Controller GET 엔드포인트용: agentCode로 Factory를 가져옴(스냅샷을 조회하는 데 사용됨). */
    public BaseFinanceAgentFactory get(String agentCode) {
        return find(agentCode);
    }
    /** 등록된 agentCode 목록; 기존 Controller와의 호환성을 위해 옛 메서드명을 유지함. */
    public Set<String> agentCodes() {
        return allAgentCodes();
    }
}
```

왜 afterSingletonsInstantiated를 사용할까요?

첫째, `AgentFactoryRegistry`는 모든 정적 `BaseFinanceAgentFactory` 인스턴스를 수집하고 DB 구성 데이터를 얻어야 합니다. 여기서 `@PostConstruct`나 `InitializingBean.afterPropertiesSet()`을 사용했다면, 타이밍 문제가 있었을 것입니다.

이는 다음 다이어그램을 통해 이해할 수 있습니다.

![Bean 초기화 타이밍](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j0JQ3bR9OexSyM7QcPlpfvJGXdVVjcXd09mib4tjT0MDCic2pME2K1T4Dkg4Y2aZt5678PPfiarZrpPEWpnLLjL5WQOhlljQs2DdA/640?wx_fmt=png&from=appmsg)

정적이든 동적이든, 모든 Agent는 궁극적으로 `refreshFromDb()` 작업을 수행합니다. `refreshFromDb()`의 핵심 동작은 프롬프트, 모델 파라미터, Toolkit(MCP Client 포함), SkillBox(Skill 콘텐츠 포함)를 집계한 불변의 volatile 스냅샷 객체인 `AgentConfigSnapshot`을 구축하는 것입니다. 이후의 `createAgent()` 호출은 DB 호출 없이 한 번의 volatile 읽기만으로 크로스 필드 일관된 뷰를 얻을 수 있습니다.

![AgentConfigSnapshot 구축](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j2glZsQwTL1t3dSjQnSsdQtCQeJYT8QZmGocMt6JI5Ap1HJ7KmQqh9NE4fbmOgGvERgko88JFqicEknQFDuFib5da0AXToFdrUeI/640?wx_fmt=png&from=appmsg)

스냅샷 외에도 등록이 있습니다 — 등록 단계는 세 가지를 수행합니다.

- `AguiAgentRegistry`에 등록: `sparkRegistry.registerFactory(agentCode, entry::createAgent)`, 메서드 참조를 통해 PROTOTYPE 시맨틱을 구현합니다(요청마다 새 인스턴스); 이후 프레임워크의 `DefaultAgentResolver`가 agentCode로 조회합니다.
- `ResourceRegistry`에 등록: Agent가 DevTool 토폴로지 다이어그램에 나타나도록 합니다.
- A2A 게이트웨이에 등록: `DynamicA2ARegistrationAdvice`를 통해 A2A 프로토콜 엔드포인트로 노출됩니다.

##### 5.1.3.2 핵심 링크 둘: 모든 메인 테이블과 연관 속성은 관리 상태이며, 관리자는 페이지를 통해 영속화함

관리 상태(Management State)에서, 모든 Agent 관련 구성은 프론트엔드 페이지를 통해 작업되고 관계형 데이터베이스에 영속화됩니다. Runtime은 읽기 전용 소비자 역할만 하며, 게시 상태에 따라 해당 구성 스냅샷이나 드래프트를 로드하여, 구성 변경과 온라인 실행 사이의 분리를 보장합니다.

구성 시스템은 메인 구성 도메인(독립 엔티티)과 바인딩 관계 도메인(연관 엔티티)으로 나뉘며, 구체적인 매핑은 다음과 같습니다.

![메인 구성 도메인과 바인딩 관계 도메인 I](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j2Vg1gqBrQKtcBYr67nAYLOpdWhXIYYGkacUrYPLL1bgoEGsOB7mCgUNbyhJUmBubMHFOWsRGIPA7NibXjl6avzZWaTaDqPRgcQ/640?wx_fmt=png&from=appmsg)

![메인 구성 도메인과 바인딩 관계 도메인 II](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1FyqwFXfR7MuD4l6mhPWqHOHT0zPZUUFuia6aRlaxCG4aSPKwiaoFcSEvMiazbHnGPd798YdjUIwkibRsiceEXtksyasWNvOdjFVWA/640?wx_fmt=png&from=appmsg)

"구성 유연성"과 "온라인 안정성"의 균형을 맞추기 위해, 시스템은 엄격한 이원 트랙 버전 관리 메커니즘을 채택합니다.

![이원 트랙 버전 관리 메커니즘](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j1UY3qg9SKRfl1NVzjxXm8FUHHT4HvZiaOZTiaXd929mWSWb0b1j8kwxznAbx3VK2qK4b7Q9iaia29Vr5gOPlVToHODPictTVP9rhwg/640?wx_fmt=png&from=appmsg)

**드래프트 단계**:

- 작업: 사용자가 페이지에서 프롬프트, 모델 파라미터, 바인딩 관계를 수정합니다.
- 영속화: 모든 변경 사항은 메인 테이블 `ac_agent_config`와 그 연관 바인딩 테이블에 직접 업데이트됩니다.
- 상태 마킹: `ac_agent_config.status`는 `1`(활성화됨/드래프트)로 유지됩니다.
- 영향 범위: 개발/테스트 환경이나 드래프트를 명시적으로 읽는 디버그 세션에만 영향을 주며; 공식 온라인 트래픽에는 영향을 주지 않습니다.

**게시 단계**:

- 트리거: 사용자가 "게시" 버튼을 클릭합니다.
- 스냅샷 생성: 시스템은 현재 메인 테이블과 모든 바인딩 테이블의 완전한 구성을 JSON으로 직렬화하여 `ac_agent_publish_record`에 씁니다. 이 레코드는 불변이며, 감사와 롤백에 사용됩니다.
- 버전 승격:
  1. `ac_agent_config.current_publish_version`이 증가합니다.
  2. `ac_agent_config.status`가 `2`(게시됨)로 업데이트됩니다.
- 영향 범위: 공식 온라인 트래픽이 즉시 새로 게시된 스냅샷 버전으로 전환됩니다.

**런타임 로딩**:

- DRAFT Variant: 주로 DevTool 디버깅이나 카나리 테스트 시나리오에 사용되며, `ac_agent_config`의 최신 드래프트 데이터를 직접 읽습니다.
- PUBLISHED Variant: 프로덕션 환경의 표준 모드로, `current_publish_version`을 기반으로 `ac_agent_publish_record`에서 해당 불변 스냅샷을 로드합니다.

**장점**:

- 안전 격리: 구성 수정 중의 중간 상태(예: 완료되지 않은 프롬프트 편집)가 온라인 서비스를 오염시키지 않습니다.
- 즉시 롤백: 게시 후 이상이 발생하면, `current_publish_version`을 이전 버전 번호로 지정하거나 이전 스냅샷을 다시 게시하여 초 단위 롤백을 달성합니다.
- 감사 추적: `ac_agent_publish_record`는 매 릴리스의 완전한 장면을 보존합니다; `ac_agent_config_audit_log`와 결합하여, 전체 링크 구성 변경 추적을 달성할 수 있습니다.

![구성 게시 흐름](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j3JjOgxLmxic3vLibA4icLrIHwokubCCspMCQwjZUCdDk8NWt99BHXggSFyxhEobS8vR0COZ543fxZftGOcd1dR4M4lcJEiaRKtGZA/640?wx_fmt=png&from=appmsg)

##### 5.1.3.3 핵심 링크 셋: 런타임 상태 — 재사용 계층의 전체 링크 SPI 커스터마이징

앞선 4장에서는 재사용 계층의 런타임 링크를 정의했습니다 — 이는 이후 모든 Agent 작업 링크를 일관되게 유지하기 위함입니다! 그리고 `AguiMvcController`가 바로 재사용 계층의 전체 런타임 진입점을 관장합니다!

`UnifiedAguiRestController`는 우리가 채팅 요청을 처리하기 위해 정의한 핵심 컨트롤러입니다; 이는 요청을 재사용 계층 프레임워크의 `AguiMvcController`로 직접 위임하며, AG-UI 프로토콜 SSE 스트리밍, ReAct 루프, 도구 호출과 같은 런타임 역량을 완전히 재사용합니다.

재사용 계층의 역량이 커스터마이징 시나리오를 커버할 수 없다면 어떻게 할까요? 재사용 계층의 커스터마이징은 프레임워크가 미리 정의한 SPI 인터페이스를 통해 이루어집니다.

![재사용 계층 SPI 커스터마이징 포인트](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1XnmwPF6fSGNzAgJUOZBpjb07e07hHQo3QE3XPVliaHXllcgl7EC9ia0g88ZREO6y2qnlPmIVaVjhZl5Jvx5MetkdIUL3SOtc2Q/640?wx_fmt=png&from=appmsg)

**핵심 설계**: 우리는 프레임워크 런타임 링크를 수정하지도, 링크를 풍부하게 하지도 않습니다; 대신 프레임워크가 남겨둔 SPI 포인트에서 기본 구현을 대체합니다. 프레임워크는 프로토콜 파싱과 ReAct 오케스트레이션을 담당하며; 프로젝트는 비즈니스 로직과 인프라 통합을 담당합니다.

#### 5.1.4 런타임 심층 분석

이전 섹션에서 런타임 링크가 재사용 계층 전체 링크를 완전히 재사용한다고 했지만, 동시에 링크의 모든 체크포인트가 SPI로 가득하다는 것도 나타냈습니다. 이 섹션에서는 이 링크를 분석하고 우리가 설계 목표를 어떻게 달성했는지 설명합니다.

목표: 경량 격리 Runtime: "사용 시 빌드" 메커니즘을 채택하여, 런타임에 경량 Agent 인스턴스 셸을 동적으로 구성합니다. 이는 하부 Skill, SKILL, MCP 인스턴스(앞서 언급한 스냅샷)를 재사용해야 하며, 다음을 보장해야 합니다.

① 효율적인 리소스 공유

② 우수한 성능

동시에 Agent 세션 실행 환경의 완전한 격리를 달성하여, 안정성과 보안을 보장합니다.

일부 링크 개념의 세부사항은 4장의 운영 링크 계층에 상세한 내용이 있습니다; 여기서는 실제 시나리오와 결합하여 런타임 설계를 어떻게 수행할지 분석하는 데 초점을 맞춥니다.

재사용 계층 런타임의 핵심 클래스와 핵심 SPI를 다시 살펴봅시다.

![재사용 계층 런타임 핵심 클래스와 핵심 SPI](https://mmbiz.qpic.cn/sz_mmbiz_jpg/bvDbzNRia8j0ue9sLicFyTFeiaYJRWWcIA5JqA28U3somYl4DxlSWvZLHgKEKDiczFUT1HsjSVJLicZzDP5le4pkZgfGIGWDJzzAI3rwgerYSdE8/640?wx_fmt=jpeg&from=appmsg)

재사용 계층 SPI의 핵심 설계 패턴:

1. **전략 패턴**: `AguiRuntimeContextBuilder`, `AguiSessionManager`, `AgentTool`, `Hook`은 모두 교체 가능한 전략입니다.
2. **팩토리 패턴**: `AguiAgentRegistry`는 `Supplier<Agent>` 팩토리를 저장하며, 요청마다 새 인스턴스를 생성합니다.
3. **어댑터 패턴**: `AguiAgentAdapter`는 ReActAgent의 내부 이벤트를 AG-UI 표준 이벤트로 변환합니다.
4. **옵저버 패턴**: `Hook`은 이벤트 리스닝을 통해 도구 호출 인터셉션을 구현합니다.

기술적 세부사항에 관심이 없다면, 다음 다이어그램을 통해 재사용 계층 SPI 설계 노드가 어디에 있는지 확인할 수 있습니다.

![재사용 계층 SPI 설계 노드](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j3twOmMLjPRVa3WopcJ61RQhC2CSvSL3Eupf2bZ0KB1C4wKKV2uWXLb7DfHGac4tIx6ttAm1KcqFYbRdiacoygF6UlQDJ1HrBsc/640?wx_fmt=png&from=appmsg)

기술자라면, 이러한 핵심 클래스가 전체 호출 링크 다이어그램에서 어디에 위치하는지 살펴보는 것을 권장합니다.

![전체 호출 링크 다이어그램에서의 핵심 클래스 노드](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j1pf1AvyaV7eZuAfmreAJmy34ItjZfKbfInhNQUH1hNic4TtUNnVIrmbdsPVUia0iaB96ibxNUzIekcN4Ed9zzQ0FZKrialhnqhFjjI/640?wx_fmt=png&from=appmsg)

이제 우리가 런타임 링크의 4대 SPI 역량을 어떻게 결합하여 사용 시 빌드, 신원 전파, 정밀한 인간-기계 협업, 세션 영속화라는 핵심 역량을 확장했는지 살펴봅시다.

![4대 SPI 커스터마이징 역량 개요](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j2F61Ra4lOcybxiaoUet1ttUZKyeAcdxQibgrWuomF2iacd8rVibvdlJG2JAhv9jPMWnZkXaxMiaER8ckonIcI7tdW5dALlhKHNMiciaA/640?wx_fmt=png&from=appmsg)

4개의 SPI 각각은 커스터마이징된 Finance급 인스턴스를 생성합니다; 아래에서 자세한 설명을 확인하세요.

##### 5.1.4.1 FinanceAguiRuntimeContextBuilder (SPI1)

FinanceAguiRuntimeContextBuilder는 우리가 런타임 링크를 위해 커스터마이징한 첫 번째 SPI입니다.

```java
public ProcessResult process(RunAgentInput input, String headerAgentId, String pathAgentId) {
        String threadId = input.getThreadId();
        String agentId = this.resolveAgentId(input, headerAgentId, pathAgentId);
        RunAgentInput effectiveInput = input;
        if (this.agentResolver.hasMemory(threadId) && !input.hasResume()) {
            logger.debug("Using server-side memory for thread {}, extracting latest user message", threadId);
            effectiveInput = this.extractLatestUserMessage(input);
        }
        // 실행 전에 구축되는 컨텍스트
        RuntimeContext runtimeContext = this.buildRuntimeContext(effectiveInput);
        Agent agent = this.agentResolver.resolveAgent(agentId, threadId);
        AguiAgentAdapter adapter = new AguiAgentAdapter(agent, this.config, runtimeContext);
        Flux<AguiEvent> events = adapter.run(effectiveInput).doFinally((signal) -> {
            logger.debug("Request completed for thread {}, signal: {}", threadId, signal);
            this.agentResolver.onComplete(threadId, agent);
        });
        return new ProcessResult(agent, events);
}
```

이는 본질적으로 전체 AgentScope 요청 링크에 걸쳐 있는 컨텍스트 객체를 구축합니다. 재사용 계층 프레임워크는 `AguiRuntimeContextBuilder` SPI를 통해 `RuntimeContext`의 생성을 비즈니스 계층이 커스터마이징할 수 있도록 개방합니다.

이 프로젝트는 `AguiRuntimeContextBuilder`를 상속하여 `FinanceAguiRuntimeContextBuilder`를 구현했으며, 각 AG-UI 요청의 컨텍스트 데이터를 프레임워크의 `RuntimeContext`로 조립합니다. 이는 4개의 필드를 포함합니다.

![RuntimeContext의 4개 필드](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j2j9Ej4HMKzsGkOYGgZzrIMSMvElOudtFzH76cSqswrOz6I1Vw8tXgTP1yCoZ7qoCklicCGtDvUTXNGBp3v8VFI0wUOUvNTNFw4/640?wx_fmt=png&from=appmsg)

왜 이렇게 했을까요?

**이유 하나: 스레드 안전성.** 재사용 계층의 하부 모델은 비동기 스레드 풀이며, 스레드는 여러 세션에서 재사용됩니다. 요청 수준 데이터가 `ThreadLocal`을 통해 전달된다면, 스레드가 풀로 돌아간 후 남아 있는 데이터가 이후 세션을 오염시킬 수 있습니다. `RuntimeContext`는 프레임워크에 의해 `AgentBase` 인스턴스에 바인딩되며(인스턴스별), 요청과 함께 생성되고 소멸됩니다 — 자연스럽게 격리되어 세션 간 데이터 유출 위험이 없습니다.


**이유 둘: 전체 링크 도달 가능성.** `RuntimeContext`는 생성부터 실행까지 Agent의 완전한 생명주기에 걸쳐 있습니다; 여러 하위 링크 노드는 추가적인 파라미터 전달 없이 `agent.getRuntimeContext()`를 통해 직접 소비할 수 있습니다.

![RuntimeContext의 전체 링크 도달 가능성](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1eicYBIKHufpOPERtSVarFrRCbGAfib3n75RdOUicqaXEomK9zicOGgJbZN98nR3ic4bBFvXKfuIdYBJTkDkNX56CtX0xLmcicak2pQ/640?wx_fmt=png&from=appmsg)

두 가지 설계 장점을 결합하면: 스레드 풀 재사용으로 인한 데이터 혼선을 방지하는 동시에, 요청 수준 데이터를 전체 링크의 어느 노드에서든 쉽게 사용할 수 있게 합니다 — 이것이 `ThreadLocal`이나 메서드 파라미터 전달 대신 `RuntimeContext`를 선택한 근본적인 이유입니다.

##### 5.1.4.2 FinanceAguiSessionManager (SPI2)

이름만 보면 단순한 세션 매니저라고 생각할 수 있지만, 완전히 그런 것은 아닙니다 — 인터페이스를 살펴봅시다.

```java
public interface AguiSessionManager {
    Agent getOrCreateAgent(String var1, String var2, Supplier<Agent> var3);
    boolean hasMemory(String var1, String var2);
    boolean removeSession(String var1, String var2);
    void cleanupExpiredSessions();
    int getSessionCount();
    default void saveAgent(String threadId, String agentId, Agent agent) {
    }
    void clear();
}
```

**1) AguiSessionManager의 설계 의도**

AguiSessionManager는 재사용 계층의 핵심 설계 중 하나입니다. 이는 본질적으로 Agent 인스턴스와 영속 저장소 사이의 다리입니다 — AG-UI 프로토콜 아래에서 상태 비저장 HTTP와 상태 유지 Agent 사이의 모순을 해결합니다. 프레임워크는 5개의 메서드를 통해 완전한 세션 생명주기 계약을 정의합니다.

![세션 생명주기 계약](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j0fe64w7b8fR1pCZcmGEavpJ7ALLOxgDvo9iac2niaapxdr9Fk5qSXxaVibSJvGlhqsG2falUtQl7MZZQNibbiaqQ7nicF5knOX2gbJI/640?wx_fmt=png&from=appmsg)

재사용 계층 프레임워크는 기본적으로 `InMemoryAguiSessionManager`(ConcurrentHashMap 캐시, 프로세스 재시작 시 데이터 손실)와 `SessionAwareAguiSessionManager`(프레임워크 Session SPI를 거치지만, 매번 메모리를 완전히 직렬화/역직렬화함)를 제공합니다. 둘 다 프로덕션급 요구사항을 충족할 수 없습니다.

우리가 재사용 계층에서 `AguiSessionManager`의 `getOrCreateAgent` 메서드를 설계했을 때, 이는 "사용 시 빌드" 메커니즘을 구현하기 위한 것이었을 뿐만 아니라, 물론 캐싱 메커니즘을 사용할 수 있도록 하기 위한 것이기도 했습니다.

명백히, 재사용 계층은 운영 링크에서 실시간 Agent 생성 진입점을 정의할 수 있도록 지원합니다 — 프레임워크는 `Supplier<Agent>` 파라미터를 통해 Agent 생성 권한을 비즈니스 계층에 넘기며, 우리의 `AgentConfigSnapshot`도 실제로는 캐싱 메커니즘을 위해 설계된 것입니다.

앞서 언급했듯이, 스냅샷은 Agent와 관련된 모든 것을 구축합니다: MCP, Skills, SkillBox, modelParams, Toolkit 등입니다. 따라서 우리는 경량 에이전트를 빠르게 생성하고 다음을 달성할 수 있습니다.

- **효율적인 리소스 공유**: 스냅샷은 volatile 참조입니다; 모든 요청이 동일한 구성 스냅샷을 공유하며, Agent 생성은 한 번의 volatile 읽기만 수행합니다 — DB/네트워크 오버헤드가 전혀 없습니다.
- **우수한 성능**: `createAgent()`는 원격 호출을 전혀 트리거하지 않습니다; 스냅샷에서 프롬프트 + 모델 + 도구를 조립하기만 하며, 밀리초 단위로 완료됩니다.
- **완전한 격리로 안정성과 보안을 보장**: 각 요청은 독립적인 ReActAgent 인스턴스(PROTOTYPE 스코프)를 받으며, 요청별 메모리와 RuntimeContext는 서로 간섭하지 않습니다.

목표를 설정한 후, AguiSessionManager가 무엇인지 이해해야 합니다; AguiSessionManager를 이해하려면, `AguiRequestProcessor`의 핵심 역할을 이해해야 합니다.

**2) 재사용 계층 AguiRequestProcessor: 운영 링크의 처음 두 단계에 대한 제어 허브**

세밀하게 분해하면, 재사용 계층의 운영 링크는 20개 이상의 노드를 가지지만, 대략 분류하면 4단계로 나눌 수 있습니다.

1. **요청 전처리** — 요청 컨텍스트 구축, 다양한 핵심 컴포넌트 초기화.
2. **Agent 생성과 세션/메모리 로딩** — Agent 인스턴스화와 과거 상태 복원.
3. **ReAct 루프 메커니즘** — LLM 추론 → 도구 호출 → 결과 관찰 → 다시 추론.
4. **세션 영속화와 마무리** — 상태 저장, 비동기 작업 트리거.

`AguiRequestProcessor`는 ReAct 루프 메커니즘 단계를 제외한 모든 것에 대한 핵심 제어 클래스입니다. 프레임워크 코드로 볼 때, 이는 세 개의 핵심 컴포넌트와 하나의 핵심 메서드를 보유합니다 — 살펴봅시다.

```java
public class AguiRequestProcessor {
    private final AgentResolver agentResolver;              // → AguiSessionManager와 연결됨
    private final AguiAdapterConfig config;                 // 어댑터 구성
    private final AguiRuntimeContextBuilder runtimeContextBuilder;  // → 우리의 FinanceAguiRuntimeContextBuilder
}
```

이는 process라는 하나의 핵심 메서드를 가지고 있습니다; 살펴봅시다.

![AguiRequestProcessor의 process 메서드](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1n0hYq1bnIuyw72nflL3pZhH4nmxJiaQPfPjU7Yo7UTzTEobRyQPl0Dkbe4a4nGDAceQd2t5lxeDF5Fjwy4piaKWmIMsPVAOCWY/640?wx_fmt=png&from=appmsg)

이 핵심 메서드의 오케스트레이션은 `AguiRequestProcessor`의 역할을 명확하게 보여줍니다: 이는 구체적인 Agent 생성, 상태 로딩, 컨텍스트 구축을 담당하는 것이 아니라, 이러한 SPI들의 호출 순서를 오케스트레이션하는 것을 담당합니다. 각 SPI는 자신의 역할을 수행합니다.

![각 SPI의 책임 분담](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j33Jb2Q52ZMHu5fRXFxtAzfLic5Wul9GtI2TBQOtVuw5cJdInrtejOickRoQCQc3Qq0xdTh6Hr8H50AVOZuA70JqHmNrxAAowgnM/640?wx_fmt=png&from=appmsg)

`AgentResolver`는 Agent 인스턴스를 얻는 데 대한 절대적인 권한을 가집니다; spark는 커스터마이징을 지원하며, 여기서는 재사용 계층의 `DefaultAgentResolver`를 수정 없이 기본값으로 사용합니다.

`DefaultAgentResolver`의 설계 의도는 하나의 핵심 모순을 해결하는 것입니다: `AguiRequestProcessor`는 "Agent를 달라"는 것에만 관심이 있지만, Agent를 얻는 방식은 배포 모드에 따라 완전히 다를 수 있습니다.

`DefaultAgentResolver`는 두 가지 일을 합니다.

1. threadId와 agentId 사이의 관계를 캐시합니다.
2. sessionManager.getOrCreateAgent 메서드를 호출합니다.
   a. 그리고 `AguiAgentRegistry에서 Agent를 얻는` 권한을 getOrCreateAgent에 전달합니다.

따라서 우리는 Agent를 생성하는 방식이 완전히 정의 가능함을 알 수 있습니다.

관계를 정리해 봅시다: `AguiRequestProcessor`는 `DefaultAgentResolver`를 통해 Agent를 관리하고, `DefaultAgentResolver`는 다시 생명주기를 sessionManager에 위임합니다. sessionManager는 본질적으로 SPI를 통해 커스터마이징 가능하므로, 우리는 FinanceAgent의 Agent 인스턴스, 이력, 메모리를 진정으로 관리할 sessionManager를 커스터마이징하기로 결정했습니다.

우리의 FinanceAguiSessionManager 설계가 경량성과 격리라는 목표를 어떻게 달성하는지 살펴봅시다.

`getOrCreateAgent` 메서드의 조립 과정은 세 단계로 나뉘며, 전체 과정은 하나의 volatile 읽기에만 의존합니다.

1. agentSnapshot을 통해 Agent를 경량으로 조립합니다.
2. 과거 세션 + 장기 메모리를 로드합니다.
3. 비동기 압축 작업이 제출되어야 하는지 감지합니다.

agentSnapshot의 설계에는 약간의 고려가 필요합니다. 앞서 `BaseFinanceAgentFactory`가 스냅샷을 구축한다고 언급했으므로, 해당하는 `BaseFinanceAgentFactory`를 빠르게 찾는 것이 좋습니다. 따라서 프로젝트 초기화 중에, 우리는 `BaseFinanceAgentFactory`의 스냅샷 기반 Agent 생성 메서드인 createAgent를 sparkRegistry에 등록합니다.

```java
 sparkRegistry.registerFactory(agentCode, entry::createAgent);
```

그러면 registry.get이 해당하는 생성 함수를 얻을 수 있습니다 — 실행만 하면 됩니다!

createAgent의 핵심 로직은 ReactAgent를 조립하는 것입니다; 구체적으로, `createAgent()`의 조립 과정은 세 단계로 나뉘며, 전체 과정에서 한 번의 volatile 읽기로 얻은 `AgentConfigSnapshot`에만 의존하며, DB나 원격 호출을 전혀 트리거하지 않습니다.

**1단계: 프롬프트 강화**

```text
원본 프롬프트(스냅샷에서)
    ↓ 사용자 장기 메모리 연결(memoryService.loadInjectionText)
    ↓ RAG 검색 결과 연결(ragRetriever.retrieve, mode=inject)
    = finalPrompt
```

두 강화 단계 모두 치명적이지 않습니다: 둘 중 하나가 실패해도 경고 로그만 남기고 원본 프롬프트로 폴백되며, 요청을 절대 차단하지 않습니다. 여기에 핵심 설계가 있습니다 — 장기 메모리와 RAG의 주입 타이밍은 `refreshFromDb()`가 아닌 `createAgent()`에서 선택되는데, 이는 요청 수준의 `userId`와 `ragQuery`(`RuntimeContext`에서)에 의존하기 때문이며, 구성 수준의 정적 데이터가 아니기 때문입니다.

**2단계: ReActAgent.Builder 조립**

```java
ReActAgent.builder()
    .name(agentName())                    // snapshot.agentName
    .sysPrompt(finalPrompt)               // 1단계에서 강화된 프롬프트
    .model(getModel())                    // qwenModel bean(Spring 싱글턴)
    .generateOptions(buildGenerateOptions(snap))  // temperature/topP/maxTokens/modelName
    .maxIters(resolveMaxIters(snap.modelParams()))
    .toolkit(snap.toolkit())              // 미리 구축된 Toolkit(MCP + App Tools + RAG Tools)
    .skillBox(snap.skillBox())            // 미리 구축된 SparkSkillBox
    .hook(hitlHook)                       // Human-In-The-Loop 인터셉션 훅
    .build();
```

`toolkit`과 `skillBox`는 모두 스냅샷 내부에 이미 구축된 객체임에 주목하세요. 이것이 스냅샷의 핵심 가치입니다: 시간이 많이 소요되는 MCP 클라이언트 연결 수립, 도구 등록, Skill 로딩을 모두 `refreshFromDb()` 단계로 앞당기며; `createAgent()`는 참조 전달만 수행하여, 매 요청에 대한 Agent 조립이 진정한 순수 메모리 내 작업이 되도록 합니다.

**3단계: 파라미터 오버라이드 캐스케이드**

`temperature`, `topP`, `maxTokens`, `maxIters`, `enablePlan`, `modelName`이라는 여섯 개의 파라미터는 모두 동일한 우선순위 체인을 따릅니다.

```text
HTTP Header(X-Temperature 등) > DB JSON 필드(modelParams) > DEFAULT 상수
```

이를 통해 동일한 DB 구성이 DB를 수정하거나 새로고침을 트리거하지 않고도 요청별 세밀도의 파라미터 미세 조정을 지원할 수 있습니다.

**3) 스냅샷의 불변 설계**

`AgentConfigSnapshot`은 `final` 클래스입니다; 모든 List 필드는 `Collections.unmodifiableList()`로 래핑되며, 외부에는 getter만 노출되고 setter는 없습니다. `BaseFinanceAgentFactory`는 `volatile AgentConfigSnapshot configSnapshot` 필드를 보유합니다.

- **쓰기**: `refreshFromDb()`에서 전체 참조만 교체합니다(`this.configSnapshot = newSnapshot`); 부분 수정은 존재하지 않습니다.
- **읽기**: `createAgent()`의 시작 부분에서, 한 번의 volatile 읽기가 로컬 변수 `snap`에 저장되며, 이 로컬 참조가 이후 전체 과정에서 사용됩니다.

이는 크로스 필드 일관성을 보장합니다 — 동일한 `createAgent()` 호출 내에서, 프롬프트, toolkit, skillBox는 반드시 동일한 버전에서 옵니다; 프롬프트는 새 버전이지만 toolkit은 여전히 옛 버전인 중간 상태는 존재하지 않습니다.

**4) FinanceAguiSessionManager의 전체 설계**

`createAgent()`의 경량 수준 생성 설계 외에도, `saveAgent`, `removeSession`, `hasMemory`의 커스터마이징도 financeAgent의 전체적인 경량성에 크게 기여합니다.

- `hasMemory()` — 이중 확인 존재 프로브: 먼저 `ac_agent_session`을 조회한 다음 `ac_agent_block.maxSeq`를 조회하여, 대화 이력 전체를 로드하지 않고도 "이력이 있는지"를 판단합니다. 이는 재사용 계층이 `extractLatestUserMessage`를 거칠지 결정하는 핵심 훅입니다.
- `saveAgent()` — 증분 영속화: JdbcSession 블랙리스트를 통해 `memory_messages`의 전체 쓰기를 건너뛰고, 새로운 블록만 증분 추가합니다(`seq > dbMaxSeq`), 쓰기 증폭을 O(N)에서 O(Δ)로 줄입니다.
- `removeSession()` — 계단식 정리: 단일 잠금 획득 내에서 `ac_agent_session` + `ac_agent_block`의 일괄 삭제를 완료하고, `tailSnapshot` 캐시를 동기적으로 정리합니다.

전체 생명주기 성능 프로파일 테이블은 각 단계의 지연 시간과 DB 작업을 요약합니다: `createAgent` → `hasMemory` → `onEnter` → `saveAgent` → `removeSession`.

이 네 가지 메서드는 함께 finance agent의 경량 런타임을 구성합니다: `createAgent`는 경량 생성(&lt;1ms, DB 없음)을 해결하고, `hasMemory`는 경량 프로빙(인덱스 히트)을 해결하며, `saveAgent`는 경량 영속화(증분 쓰기)를 해결하고, `removeSession`은 경량 정리(일괄 삭제)를 해결합니다.

##### 5.1.4.3 엔지니어링급 Human in the Loop (SPI3)

HITL 자체는 두 가지 형태로 존재합니다: 하나는 모델 수준이고, 다른 하나는 엔지니어링 수준입니다.

이들의 차이:

![모델 수준 HITL과 엔지니어링 수준 HITL의 차이](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j08KkDGf8azaoNelSXV7SYWDJicP4rsqbqbc51cyfaRJvRib2BSEBREB8icku29hJFHa8baTicgod0NPIcSz4wL48VeAwecbT3P0aQ/640?wx_fmt=png&from=appmsg)

예시:

![엔지니어링 수준 HITL 예시](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j3JhIzwnibSEwKWjibPWnjwODtAiahVL3oAR7BEial9L0nH2JVM4gCS7PftUZRfDKmKBCHFy9zk1PsZRejmN1Z6ZnBvuawHeX3tzicY/640?wx_fmt=png&from=appmsg)

왜 엔지니어링급 HITL을 사용할까요? 모델 수준 HITL을 사용하면 어떤 문제가 발생할까요?

![모델 수준 HITL의 문제점](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j3dhLI4TM1Dy4lZoDqVel9kNzo9D7af8VibjbpNkVswZOEmiastoSbhYq72giaXVm0HW0YGmtnFR8wib9zSgqeLD8GmSIJ9sWEhLQA/640?wx_fmt=png&from=appmsg)

모델이 엔지니어링 수준 HITL을 인지하지 못하기 때문에, 엔지니어링 수준 HITL은 인터셉션 작업이 필요함을 알 수 있습니다. 인터셉션 지점은 다음과 같습니다.

1. 사용자가 확인 작업을 수행하고 있음을 어떻게 확인할 것인가?
2. 사용자가 도구를 사용하기 전/후에 인터셉션 작업이 필요한지 어떻게 확인할 것인가?
   a. 인터셉션 지점이 발견되면, 모든 Agent 행동을 어떻게 중지할 것인가! 확인 결과를 기다린 후 이전 링크를 계속 진행합니다.

다행히 AgentScope의 훅 시스템은 매우 강력합니다.

![AgentScope 훅 시스템](https://mmbiz.qpic.cn/mmbiz_jpg/bvDbzNRia8j3JvZBkDfGvriaX6YEvybArD2xo992Frx8VT3D3gubqmv2ASmUbKsAvt1THovYldvo8wzDSxytIjLsQnMLPOPOGibQCtLHCJa8Dc/640?wx_fmt=jpeg&from=appmsg)

전체 링크 프레젠테이션:

![HITL 전체 링크](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j0qOJAiaRaRibbB4amWr7VFqrfT2KZ5eQia0nybxxdG6ypQSuPLxHBNsrNYFOdLCiaXcJBHHIBG0VeDbEHLY5VXTiankvJImXTsQzjY/640?wx_fmt=png&from=appmsg)

요약하면:

1. 사용자가 전달하는 메시지 형식을 인식할 수 있습니다 — HITL 확인 형식에 대해 합의하기만 하면 됩니다.
2. PostReaningEvent는 도구 호출 전이며 엔지니어링 수준의 stopAgent를 가집니다.
3. PostActingEvent는 도구 호출 후이며 엔지니어링 수준의 stopAgent를 가집니다.

기본 HITL 역량을 해결했으니, 여전히 구체적인 우선순위 전략과 작업 모드를 정의해야 합니다!

**HitlResolver의 결정 우선순위**: before와 after 모드 모두에 대해, HitlResolver는 어떤 HITL이 히트하는지 결정합니다; 구체적인 5계층 인터셉션 로직은 다음과 같습니다.

![HitlResolver 5계층 인터셉션 로직 I](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j0IV57m3Yz9WfjUrkcOW8VoTENtcpX2EcPpdL1CUuJYTicmlqvlcfCiaNbg6SuMjPyCRaiaQgYYDKW2r54OvkVEFP95CxFMZ44nmg/640?wx_fmt=png&from=appmsg)

![HitlResolver 5계층 인터셉션 로직 II](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j34V9sN9btJVPnRzjQsBBxBQzFRf7bIjRGVA5qogHoZxMVoyWluib7zFxBJvWBbO8tjT84OoyhV1hNVHO4mEiaa12yCO0R0pCrfI/640?wx_fmt=png&from=appmsg)

세 가지 작업 모드에 대한 상세 설명:

**BEFORE(실행 전 인터셉션)**

![BEFORE 실행 전 인터셉션](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j00MlCnAyB2iaQf8JRTX3zvWWKsMSYia267cBJjjdLDq1JIxQznWBwon4x4A40u5bqaEXUShQGvpX892x84jt8uCICQBmIxIKw8Y/640?wx_fmt=png&from=appmsg)

**AFTER(실행 후 검토)**

![AFTER 실행 후 검토](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j33HEd6IHGfLVUnZeSGKHnGXOtfsrId5TF3RJTd3ka39ibaw35X5zRoX7b1KoIVMcNQAiaY7l100LJYbdiaTTE7PZfln3UQXyemp8/640?wx_fmt=png&from=appmsg)

**Resume(사용자 확인 후 재개)**

![Resume 사용자 확인 후 재개](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j0cPvwOrQYibW8A861O7dnKx174bYzQhaSSszObIVz8W43oEyuftWVcocsparV38sXLOFicsoyN6ztb2VXibLIvOMaKvWbfI9YtIo/640?wx_fmt=png&from=appmsg)

##### 5.1.4.4 ContextInjectingMcpTool (SPI4)

AgentTool은 AgentScope의 도구 수준 SPI 확장 지점으로, 캡슐화된 도구 호출 로직을 정의할 수 있습니다.

```java
public interface AgentTool {
    String getName();
    String getDescription();
    Map<String, Object> getParameters();
    Mono<ToolResultBlock> callAsync(ToolCallParam param);
}
```

`ContextInjectingMcpTool`은 이 인터페이스를 구현하며, 프레임워크의 네이티브 `McpTool`을 데코레이터 패턴으로 래핑하여 하나의 핵심 문제를 해결합니다: **MCP Server의 인증 신원은 빌드 시점에 용접될 수 없으며, 매 호출마다 현재 요청 사용자에게 동적으로 바인딩되어야 합니다**.

**1) 문제 배경**

AgentScope의 네이티브 `McpTool`의 신원은 이미 `AoneMcpClientBuilder.buildSync()`의 빌드 시점에 결정됩니다(DB `ac_mcp_server.user_id`); 모든 사용자는 MCP Server를 호출할 때 동일한 신원을 공유합니다. 이는 금융 시나리오에서 용납할 수 없습니다 — 서로 다른 사용자가 동일한 MCP 도구를 호출할 때(예: 승인 양식 조회), MCP Server는 권한 검증과 데이터 격리를 수행하기 위해 호출자 자신의 신원을 볼 수 있어야 합니다.

**2) 설계: 호출 시점의 신원 주입**

`ContextInjectingMcpTool`은 도구의 외부 계약을 변경하지 않습니다(`getName` / `getDescription` / `getParameters`는 모두 델리게이트로 전달됨); `callAsync` 경계에서만 신원 주입을 완료합니다.

![호출 시점 신원 주입 설계](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j0B4SA0OcLHF0IiaoOfQ3HOQcBc6BuD7wuIhS0tOGNo4N2VT4aKdibcygxEluvSL8WfpeMjGqqrbykiaPjLDWwVBx0xY8aZ3iczz5Y/640?wx_fmt=png&from=appmsg)

핵심 포인트: 신원 전파는 ThreadLocal이 아니라 Reactor Context(리액티브하고 스레드 안전한 컨텍스트 전파 메커니즘)를 통해 이루어집니다. 이는 MCP 호출 체인이 완전히 비동기적이며(`Mono` / `Flux`), ThreadLocal은 스레드 간 스케줄링 중 손실되기 때문입니다.

**3) McpCallIdentity: 호출 수준 신원 캐리어**

```java
public final class McpCallIdentity {
    public static final String CONTEXT_KEY = "mcpCallIdentity";
    private final String empId;        // BUC 직원 ID → Normandy bucUserId
    private final String ssoToken;     // BUC SSO 토큰 → Normandy bucSsoToken
    private final Map<String, String> extras;  // 예약된 확장 슬롯
}
```

산재된 키가 아닌 불변 값 객체로 설계한 이유: 신원 필드는 계속 늘어날 것입니다(empId → +ssoToken → 향후 +sessionId / 테넌트 ID 가능). 하나의 객체로 수렴시킨 후에는, 필드가 추가되거나 제거되어도 전송 체인의 중간 계층들(`ContextInjectingMcpTool` → `McpTransportContext`)은 수정이 필요하지 않습니다; 조립 측(RuntimeContext 읽기)과 소비 측(인증 헤더 생성)이라는 양 끝단만 변경하면 됩니다.

![McpCallIdentity 전송 체인](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j2CyV1QicIHtibeKYIkZnibS3SYRYiakA7rbqUVvYthS8I5ObwStL3ibicbtCcFKbyKrgYN6NoPG4CI2NwwO1pXCugiaSM2QEJOEgkOWM/640?wx_fmt=png&from=appmsg)

#### 5.1.5 7가지 설계 철학 요약

1. 구성보다 관례.
2. DB와 애플리케이션 계층의 동형성(변환 계층 제거).
3. 드래프트/게시 이원 트랙(수정이 프로덕션에 영향을 주지 않음).
4. 낙관적 잠금의 전면 커버(동시성 충돌 방지).
5. 컴포넌트 마켓플레이스 + 바인딩 분리(재사용 + 독립적 진화).
6. 비동기 작업 분리(메인 플로우를 블록하지 않음).
7. 증분 영속화(쓰기 압력 감소).

### 5.2 핵심 포인트 둘: 다양한 생태계 플랫폼과의 저비용 호환성

#### 5.2.1 SkillSourceLoader

현재 시스템은 `SkillSourceLoader` SPI 추상화 계층을 구축했으며, 인터페이스 구현 계층에서 Skill 소스 차이를 캡슐화합니다; 상위 계층의 `SkillRegistry` + `BaseFinanceAgentFactory`는 하부 소스 세부사항을 전혀 인지하지 못합니다. 이는 새로운 생태계 플랫폼에 연결하는 비용이 "하나의 `@Component` 클래스를 구현하는" 수준으로 압축됨을 의미합니다.

![SkillSourceLoader SPI 추상화 계층](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j1FqXialZpBTml6Uhd2fic7WZu7ibHvdHq4896L7kRpicG4k7lDN7GENbPmcUbuL3SFXneIKSUmYb9pFPeBqoLsjiajpFDeLy4icNC90/640?wx_fmt=png&from=appmsg)

**핵심 인터페이스 계약**:

```java
public interface SkillSourceLoader {
    String skillSource();              // 소스 식별자: "AONE" / "OSS" / "GITHUB" / ...
    AgentSkill load(SkillKey key);     // 스킬 로드(다운로드 + 파싱)
    default int purgeLocalCache();     // 로컬 캐시 전체 삭제
    default Path localCacheDir();      // 로컬 캐시 루트 디렉터리
    default void purgeLocalCacheForKey(SkillKey key); // 단일 캐시 삭제
}
```

**SkillKey 삼중항**: `(skillSource, skillName, skillVersion)` — 소스가 일급 시민이며, 여러 소스의 공존을 자연스럽게 지원합니다.

##### 5.2.1.1 AoneSkillSourceLoader

![AoneSkillSourceLoader](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1xm0T81jLGbATQ9R53PbhaI8Ue9x3SBl63rsTic8bM2lADrpfxZjZUt4Tgl4L3AcWZs5ck0MmGVZ3ZDCoMAeVYeHLHsVvibXDfA/640?wx_fmt=png&from=appmsg)

**프로덕션급 보증**:

- **강한 제약 계층**: `ac_agent_skill`의 모든 행은 `enabled=1`인 `ac_skill` 행을 가리켜야 하며, 그렇지 않으면 시작 시 fail-fast(`IllegalStateException`)하여, "구성되었지만 로드할 수 없는" 숨겨진 위험을 제거합니다.
- **약한 실패 계층**: Aone 서비스가 일시적으로 사용 불가할 때, Agent는 여전히 시작할 수 있으며(해당 Skill을 건너뜀), 복구를 위한 백그라운드 예약 재시도가 있습니다.
- **버전 관리**: `ac_skill.skill_version`은 시맨틱 버저닝을 지원합니다; Skill 업그레이드는 DB에 행 하나를 추가하고 `ac_agent_skill` 바인딩을 업데이트하기만 하면 됩니다.

##### 5.2.1.2 OssSkillSourceLoader

![OssSkillSourceLoader](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j0TTmI9j8qicebqVmnT9MMXJ1Sn6tTo6QVFbsrBMs0U5kXK19m8Oy0VJOoG0ngPQoyTgqibwR1CptYTeBscK2pDBwMYg8uhevpsQ/640?wx_fmt=png&from=appmsg)

**개발 가속화 가치**:

- **승인 없는 반복**: 개발자가 SKILL.md를 수정 → zip으로 패키징 → OSS에 업로드 → DB에 `ac_skill(source=OSS)` 행 하나 추가 → Agent 새로고침; 전체 과정은 Aone 릴리스 승인이 필요하지 않습니다.
- **빠른 버전 전환**: 동일한 Skill이 OSS에서 여러 버전을 유지할 수 있습니다; 개발자는 `ac_agent_skill.skill_version`을 수정하여 몇 초 만에 전환합니다.
- **로컬 디버깅 친화적**: OSS Skill의 로컬 캐시 디렉터리는 AONE과 격리되어 서로 간섭하지 않습니다.

##### 5.2.1.3 새로운 생태계 플랫폼 확장 비용 분석

새로운 Skill 마켓플레이스 연결 단계:

![새로운 Skill 마켓플레이스 연결 단계](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j2hxTJDtcYyRHvNKEl9QYWE5ibXZtkJWeNXfFO6hertcLXkJ5AIyoyicv7QWgohQnDlV2reGicztE1bHscjmhsCwORPGuZGunsDJc/640?wx_fmt=png&from=appmsg)

**수정이 필요 없는 부분**:

- `SkillRegistry` — 새로운 Loader를 자동으로 발견하며, 수정이 필요하지 않습니다.
- `SkillKey` — 이미 소스 차원을 포함하고 있어, 자연스럽게 호환됩니다.
- `BaseFinanceAgentFactory.resolveSkills()` — `SkillKey`만 신경 쓰며, 소스 구현은 신경 쓰지 않습니다.
- `ac_agent_skill` / `ac_skill` 테이블 구조 — `skill_source` 필드는 이미 개방된 문자열입니다.
- 프론트엔드 관리 페이지 — 드롭다운에 새 소스 값을 추가하기만 하면 됩니다.

##### 5.2.1.4 표준화된 Skill 주도 흐름

![표준화된 Skill 주도 흐름](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j3t41WGiaCOA1A6Wk1omgZoZhtmYGPXxGJc8FwaZs7KS3tWXsjVTtej5SUe5Y6LjibKR949RWkRdmFfMNqPB270zfAibxIARaXrY8/640?wx_fmt=png&from=appmsg)

##### 5.2.1.5 Agent에 바인딩된 Skill의 전체 주도 생명주기를 모니터링하는 방법

**1) Skill 생명주기 파노라마**

![Skill 생명주기 파노라마](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j3eBaicnFz0Jr95Z1LPHkrDp2WvQjZUljpDkcmryAMryDd4PKXGP52eIT8ibyBuyJv4fKwC8TjzaFia7JuGQN54HSGsPtFjMQLWws/640?wx_fmt=png&from=appmsg)

**2) ac_agent_skill_task 상태 머신 상세 설명**

이것이 Skill 생명주기의 핵심 구동 엔진입니다; 현재 구현된 상태 전이:

![ac_agent_skill_task 상태 전이](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j2wNKiapPOBNDxWZicZq0jCSH4yOabHWAGZiclrxSrdsGSvr0Ykkicg2FqIDEz3VwS6UgbR3sgricVibGEIRO9IFgyACG9oeLhFRib3cA/640?wx_fmt=png&from=appmsg)

단계적 6회 재시도 전략:

![단계적 6회 재시도 전략](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j3bp29OYy6aiaHOib2YtIVxbJkCxQ2EicbmDzW93KXV2U9gOShuyjiblaaH7Ha7X3qXIzRqRYVshASnib4A58FBHgmwybB43kSAMPoc/640?wx_fmt=png&from=appmsg)

**스케줄러 이원 트랙 시스템**:

- 프로덕션 환경: `AgentSkillTaskScheduler`(SchedulerX2), 권장 cron `0/20 * * * * ?`(20초마다 한 라운드).
- 개발 환경: `AgentSkillTaskLocalScheduler`(Spring `@Scheduled`), `fixedDelay=20s`, 수동 활성화가 필요합니다.

#### 5.2.2 MCP

`McpClientFactory.build()`는 한 가지만 합니다 — DB 행을 필드별로 재사용 계층 프레임워크의 `AoneMcpClientBuilder`에 매핑하는 것입니다.

```java
// McpClientFactory.java:69-101
AoneMcpClientBuilder builder = AoneMcpClientBuilder
    .create(server.getMcpServerName())
    .mcpId(server.getMcpServerId());
// 각 DB 컬럼 → 하나의 builder setter, 모두 if-not-blank 매핑
if (isNotBlank(server.getMcpServerType())) builder.type(parseEnum(AoneMcpType, ...));
if (isNotBlank(server.getAuthMode()))      builder.authMode(parseEnum(AoneMcpAuthMode, ...));
if (isNotBlank(server.getRegion()))        builder.region(parseEnum(AoneMcpRegion, ...));
// ... 총 8개 필드
return builder.buildSync();  // 트랜스포트/인증/URL 해석이 모두 프레임워크에 넘겨짐
```

**핵심 포인트**: URL 해석, 트랜스포트 선택, 인증 구현은 모두 재사용 계층 프레임워크 내부에 있습니다. 프로젝트 코드는 "DB → Builder"라는 "멍청한 매핑"만 수행합니다. 프레임워크가 새로운 `AoneMcpType` 열거형 값을 추가하면, 이쪽에서는 코드 변경이 전혀 없습니다 — `parseEnum()`은 범용 리플렉션이며, 새 열거형을 자동으로 인식합니다.

#### 5.2.3 두 가지 호환성 전략: Skill vs MCP

![두 가지 호환성 전략: Skill vs MCP](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j0TvHQIPU3ztNZWSVTCuFk5jic77oCfRIuJUPZbl0Y5dkvGHlmmXd2AFCCvjSxGGoKzzlRM4Qo6GcnAIiaqWmSlVWM3sZv4Qrxhg/640?wx_fmt=png&from=appmsg)

**설계 트레이드오프는 명확합니다**: Skill은 플랫폼마다 차이가 큽니다(저장소 프로토콜, 파일 형식, 버전 모델이 모두 다름), 그래서 우리는 자체 플러그인 시스템을 구축해야 합니다; MCP 프로토콜 자체는 표준화되어 있으며(MCP 스키마 + HTTP 트랜스포트), 차이는 URL 해석과 인증 방법에만 있습니다 — 이는 마침 프레임워크가 이미 해결한 문제들이므로, 우리는 이를 프레임워크에 맡기고 데이터 주도 구성 계층만 직접 수행하기로 선택합니다.

### 5.3 핵심 포인트 셋: 전체 Agent 효과 진화

#### 5.3.1 생각

2026년은 AI 폭발의 원년으로 여겨지며, Hermes의 현상적인 부상은 "진화"를 업계의 화두로 만들었습니다. 다양한 기술 아티클들이 "폐루프 진화" 전략을 압도적으로 분석하지만, 업계에서 명확하게 정의할 수 있는 사람은 드물어 보입니다: 진정한 "진화"란 정확히 무엇인가? 그 핵심 측정 지표는 무엇인가?

본질로 돌아가 핵심 명제를 명확히 해야 합니다: "변화"와 "진화"를 어떻게 구분할 것인가? Hermes의 Skill 생성 전략을 예로 들면, 엄밀히 말해 이는 무작위 "변화"에 더 가깝습니다. 효과적인 가치 검증 메커니즘이 부족하기 때문에, 새로 생성된 Skill이 실제로 시스템 역량을 향상시키는지 판단하기 어렵습니다 — 긍정적 피드백 없는 변화는 교란(perturbation)이라고밖에 부를 수 없으며, 진화가 아닙니다.

저는 Hermes의 소스 코드 일부를 살펴보고 관련 자료를 확인하여, 다음과 같은 결론에 도달했습니다.

![Hermes가 달성한 역량](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j22lfS6HV4lZrbrcmicNA86ibybE6kwAudClCfz8Nriau3PfdbdAkZcO2Aqdprk2dQ2IafdibarbDXvM1v7LUU1aeN1j47GOKENVtE/640?wx_fmt=png&from=appmsg)

그리고 Hermes가 달성하지 못한 것:

![Hermes가 달성하지 못한 역량](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j2iblwZAXHib9qtibcWKEeLdyxEMZSicw6tFz5MUJRwg1mPpLWiaAiaicDt4LVHc7CuPuENrIeLPhicwxLEgib2icd8seSlhzricJpRE1PFeI/640?wx_fmt=png&from=appmsg)

Hermes의 "진화"는 본질적으로 "자기 조직화된 지식 거버넌스"입니다 — 엔트로피 제어(중복 병합, 오래된 콘텐츠 아카이빙, 이름 중복 제거)에는 능숙하지만, 변화가 개선임을 증명하는 폐루프 효과 피드백이 없습니다. Curator Prompt 자체가 "통합을 건너뛰는 이유로 사용 카운터를 사용하지 마라"고 명시적으로 인정하고 있는데, 이는 시스템이 텔레메트리 데이터로부터 Skill 품질을 판단할 수 없음을 알고 있다는 것을 나타냅니다. 이것이 의도적인 설계 트레이드오프(구조적 거버넌스를 우선시)인지, 채워야 할 역량 격차인지는 제품 포지셔닝에 달려 있습니다.

#### 5.3.2 진화를 어떻게 정의할 것인가

자율 Agent와 그 하부 Skill을 구축할 때, 우리는 종종 완벽하고 궁극적인 모델 상태가 존재한다는 오해에 빠집니다. 그러나 Agent와 Skill 진화의 핵심 원동력은 추상적인 자기 개선이 아니라, 구체적인 시나리오에 기반한 효과 피드백, 특히 정량화 가능한 효과 비교입니다.

명확히 해야 할 하나의 핵심 인식이 있습니다: "절대적 진화"는 가짜 명제이며; "상대적 진화"만이 엔지니어링 실무에서 실재하고 실행 가능한 명제입니다.

**1) 왜 "절대적 진화"는 가짜 명제인가?**

개방 도메인 자연어 처리나 복잡한 작업 계획에는 유일한 "표준 답안"이 없습니다. 동일한 사용자 의도가 여러 유효한 실행 경로를 가질 수 있으며; 동일한 코드 조각이 여러 동등한 구현을 가질 수 있습니다. 따라서 보편적으로 적용 가능한 "완벽한 Agent"를 정의하려는 시도는 이론적으로 실행 불가능할 뿐만 아니라 엔지니어링적으로도 무의미합니다.

진정한 진화는 유한한 경계 내에서의 비교 우위에서 발생합니다. 우리는 무한한 현실 세계를 유한한 평가 세트(벤치마크)에 매핑해야 합니다. 고품질 테스트 케이스를 구축하고 3점, 6점, 9점 척도와 같은 세밀한 채점 메커니즘을 도입해야만, 모호한 "좋고 나쁨"을 추적 가능하고 최적화 가능한 "높고 낮음"으로 전환할 수 있습니다.

이러한 상대적 진화의 논리 체인은 다음과 같습니다.

- 베이스라인 수립: 현재 버전의 평가 세트에서 성능 베이스라인을 수립합니다.
- 차등 비교: 새 버전과 옛 버전, 또는 서로 다른 전략 간의 특정 메트릭에 대한 점수 차이입니다.
- 반복 방향 설정: 점수 격차를 기반으로 약점을 찾아, 다음 라운드의 파라미터 조정이나 로직 최적화를 주도합니다.

**2) 지표 정의**

![진화 지표 정의 I](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j0jQgVMwiaz7Nhjw5qQlqm3Fq0vdgcn8hbSmxD86QORJFvSePLcy9MNqyqU67hqfVCcphV3GyObFSoLlRo4DXRtrIABTkNnl0icg/640?wx_fmt=png&from=appmsg)

![진화 지표 정의 II](https://mmbiz.qpic.cn/mmbiz_jpg/bvDbzNRia8j2dlVgchT5kRaia2NNEdGsEiayqicJLXuQt69PGNfj4CU3U12TeA1o1muILIIqfEuevCfPvqOaC4iaV7YLmNMibvmb7TI3I0SaPJheY/640?wx_fmt=jpeg&from=appmsg)

가중치 설계:

![지표 가중치 설계](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j3icJEPicichoQllq9aOGmdUia7f1tTyktiav8AvbpQM4iaASFGAL6QDTaIUYYYuEZLibZeIB2VfIoDaWZoZucCyZYhahrzOC3ubqSDNo/640?wx_fmt=png&from=appmsg)

**3) 평가 정의**

![평가 정의](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1InMIyfagxHALib3tB5s4bJvhF7M44OB2MkTNmQicHI7cDcSIvm8Q5q7PbRMNyh6Q5ib3CTsZfq0bkl8qTtyB5s7I6PDh4cF4HIA/640?wx_fmt=png&from=appmsg)

#### 5.3.3 진화 폐루프 역량을 어떻게 개발할 것인가?

인간 폐루프 대 자동 폐루프

**1) 인간 폐루프: 최소 실행 가능한 진화 시스템**

자동화를 논하기 전에, 먼저 하나를 인정해야 합니다: **인간 폐루프는 자동 폐루프의 원시적 형태가 아니라, 자동 폐루프의 프로토타입 검증입니다.**

인간 폐루프의 흐름은 직관적입니다.

![인간 폐루프 흐름](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j1nWicialOJYu8l691eV9VJwIUdsGXRYFANzfolf2YxHqddLEiaKcs8ca2xkFaHQM8rEkOjCP8YRFU7JoK8Ry4t03L6jibKE2RGuSs/640?wx_fmt=png&from=appmsg)

이 흐름은 원시적으로 보이지만, 특정 단계에서는 대체 불가능합니다.

**인간 폐루프의 진정한 가치**

첫째, 귀속(attribution)의 정신 모델을 확립합니다.

시스템 초기 단계에서는 "실패"가 어떤 모습인지조차 모릅니다. 사용자 불만은 라우팅 문제일 수도, 하부 데이터 소스 문제일 수도, 사용자 자신의 비합리적인 기대일 수도 있습니다. 이러한 분류는 수백 건의 사례를 인간이 수동으로 검토한 후에야 안정적인 판단 체계를 형성할 수 있습니다.

이 단계를 건너뛰고 바로 자동화로 가는 것은 레이블이 없는 데이터로 분류기를 훈련시키는 것과 같습니다 — 무엇을 최적화하고 있는지 알 수 없습니다.

둘째, 개입이 효과적인지 검증합니다.

초기에 가장 큰 위험은 "진화가 너무 느림"이 아니라 "잘못된 방향으로의 진화"입니다. 인간 폐루프를 통해 직접 인지할 수 있습니다: 스킬 설명을 수정한 후, 라우팅이 정말로 더 정확해졌는가? 아니면 우연히 여러분이 구성한 테스트 케이스만 통과했는가?

시스템이 미성숙할 때 이러한 직관은 어떤 자동화된 평가보다 더 신뢰할 수 있습니다.

셋째, "고칠 수 없는" 케이스를 발견합니다.

일부 실패는 Skill 문제가 아니라 시스템 아키텍처 문제, 심지어 제품 정의 문제입니다. 이러한 케이스는 수동으로 식별되어야 하고 상위 변경을 추진해야 합니다; 자동 폐루프는 이러한 판단력이 없습니다.

자동 폐루프가 필요한지 판단하기:

![자동 폐루프가 필요한지 판단하기](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j2ACKDucc2JseNXJ2YEYd6l6xtadqumJRwb6gmG9WFhBGl7MXRw3W7ojdPy0iaLibibshOmadd3kC6LMRHuxqvOTuiaviaNFuxGuGvg/640?wx_fmt=png&from=appmsg)

**2) 인간 폐루프의 병목: 왜 불가피하게 한계에 부딪히는가?**

인간 폐루프의 병목은 "느림"이 아닙니다 — 느림은 단지 표면일 뿐입니다. 실제 병목은 세 가지입니다.

**병목 1: 평가 일관성**

동일한 나쁜 케이스에 대해, 두 명의 개발자가 서로 다른 귀속 결론을 낼 수 있습니다. 이는 역량 문제가 아니라 인지적 편향입니다.

```text
케이스: 사용자가 "지난달 공급업체 결제 기한 분포"를 묻습니다
개발자 A: 라우팅 문제로 귀속 — financial-analysis-skill로 가야 하는데,
         data-query-skill로 라우팅됨
개발자 B: 역량 격차로 귀속 — financial-analysis-skill이 존재하지만,
         "결제 기한 분석" 하위 역량이 부족함
```

두 귀속 모두 합리적이지만, 개입 방향은 완전히 다릅니다. 귀속이 일관되지 않으면, 이후의 모든 최적화가 서로를 상쇄합니다 — 오늘은 A가 라우팅 설명을 바꾸고, 내일은 B가 Skill에 새 역량을 추가하며, 시스템의 진화 방향은 무작위 행보가 됩니다.

자동 폐루프는 "의견 불일치를 제거"함으로써가 아니라, 하나의 평가 표준 세트를 고정시켜 모든 결정이 동일한 잣대 아래에서 내려지도록 함으로써 이 문제를 해결합니다.

**병목 2: 평가 커버리지**

인간 검토는 표본만 커버할 수 있을 뿐, 전체 물량을 커버할 수 없습니다. 시스템이 매일 수천 건의 요청을 처리할 때, 인간 검토는 5%만 커버할 수도 있습니다.

이 5%조차도 심각한 선택 편향이 있습니다 — 검토되는 것은 종종 "사용자가 불만을 제기한" 케이스이며, "사용자가 불만을 제기하지는 않았지만 효과가 평범한" 대량의 케이스는 무시됩니다.

자동 폐루프는 전체 트래픽 물량을 처리할 수 있습니다. 사용자 불만이 필요하지 않습니다 — 사전 정의된 평가 지표를 기반으로 효과가 나쁜 케이스를 능동적으로 발견할 수 있습니다.

**병목 3: 피드백 지연**

인간 폐루프의 피드백 루프는 일 단위입니다: 오늘 문제를 발견하고, 내일 분석하고, 모레 수정하고, 3일 후 릴리스합니다.

하지만 Skill 문제는 시간 단위일 수 있습니다 — 하부 데이터 소스의 스키마 변경으로 SQL 생성 Skill이 생성하는 모든 SQL이 오류를 일으킵니다. 인간이 발견할 무렵에는 이미 수백 건의 요청이 영향을 받았을 것입니다.

자동 폐루프는 피드백 지연을 분 단위로 압축할 수 있습니다: 평가가 지속적으로 실행되며, 지표가 드리프트하는 순간 알림과 자동 개입이 트리거됩니다.

**3) 자동 폐루프**

![자동 폐루프 3계층 아키텍처](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j0BjkaicCeicq7bu3icWtia8BD1TtvpSNTevbA2bR8K9xKzwKVGoDmNMCqe9T6kibXwZrjBUZicYZmyXsibXia6nib5uJB0OIySNWwhe5q8/640?wx_fmt=png&from=appmsg)

이 세 계층의 관계는: 데이터 파이프라인이 원자재를 제공하고, 실행 엔진이 컴퓨팅 파워를 제공하며, 전략 계층이 평가 로직을 제공합니다. 어느 계층이라도 없으면 자동 폐루프는 작동할 수 없습니다.

**평가 데이터 파이프라인: Trace에서 평가 자산까지** — 이것이 자동 폐루프에서 가장 과소평가되지만 가장 중요한 부분입니다.

**L1: Trace: 구조화된 실행 기록**

Trace는 로그가 아닙니다; Trace는 구조화된 실행 기록입니다. 둘의 차이: 로그는 사람이 읽기 위한 것이고, Trace는 기계가 분석하기 위한 것입니다. 적격한 Trace는 다음을 포함해야 합니다.

![적격한 Trace의 구성](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1KLbHg4BCHcOo068FUb4jjYRiaxGE6ibM3lI652bXpHaIicSkhOwmqMyyrk1Ip2LiaBqHwzNt4B8hGHfhBnkCC9HvDmGqdZcBmDLc/640?wx_fmt=png&from=appmsg)

**결과 신호: 결과 신호 시스템**

![결과 신호 시스템](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j3Y1APJcuj1t3HMq1SJnp5LWy6gUsIlrO3vMZ9JMScs62eSTVpMaibpY6Qm2rG2lrzCmhQ9JscMRcHwpM4sibgcnUHjUBN3oG1jU/640?wx_fmt=png&from=appmsg)

이러한 이질적인 신호들을 표준화된 결과 레이블로 통합하기 위해서는 데이터 파이프라인이 필요합니다.

**채점 메커니즘**:

![채점 메커니즘](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j190uVc8hZdjOMibG5AzmS8ibjxQpgvwonNt7MUBvJcVdibgzk2V8ib2x8icYswQDsMBaHbI1H1fjGAEUq4AeHDVnJfztdOY1ZX6aUM/640?wx_fmt=png&from=appmsg)

**빠지기 쉬운 함정들**

**함정 1: 평가 데이터셋의 편향**

평가 데이터셋이 주로 사용자 불만(명시적 피드백)에서 나온다면, 심각한 편향이 생깁니다 — "사용자가 기꺼이 불만을 제기하는" 시나리오만 커버합니다. "효과가 평범하지만 사용자가 피드백을 주기 귀찮아하는" 대량의 케이스는 데이터셋에 포함되지 않습니다.

해결책: 능동적 샘플링. Skill, 의도 유형, 기간별로 계층화된 샘플링을 수행하여, 평가 데이터셋의 분포가 실제 트래픽 분포에 가깝도록 보장합니다.

**함정 2: LLM-as-judge의 안정성**

LLM을 사용하여 LLM의 답변을 평가하면 채점에 무작위성이 도입됩니다. 동일한 답변이 두 번의 평가에서 다른 점수를 받을 수 있습니다.

해결책:

- 개방형 채점 대신 4차원 루브릭을 사용하며, 각 차원에 명확한 1-5점 기준을 두어 채점 무작위성을 줄입니다.
- 여러 번(최소 3회)의 평가를 평균냅니다.
- 인간 주석과 정기적으로 보정하여, 자동 점수와 인간 점수 간의 피어슨 상관계수가 0.7보다 크도록 보장합니다.
- 성능 차원은 불필요한 채점 잡음을 피하기 위해 LLM-as-judge를 거치지 않고 자동화된 메트릭을 전적으로 사용합니다.

**함정 3: 과최적화**

자동 폐루프는 시스템이 평가 데이터셋에 "과적합"하도록 만들 수 있습니다. Skill이 평가에서는 매우 우수하지만, 온라인 효과는 평범합니다.

해결책:

- 평가 데이터셋을 정기적으로 업데이트합니다(매월 최소 20% 교체).
- 최적화에는 절대 사용하지 않고 검증에만 사용하는 "숨겨진" 평가 세트를 유지합니다.
- 온라인 지표와 평가 지표를 동시에 모니터링하며, 둘이 발산하면 즉시 조사합니다.

**함정 4: 평가 시스템 자체의 비용 무시**

평가는 무료가 아닙니다. 매 평가마다 LLM API 호출, 컴퓨팅 리소스, 저장소 리소스를 소비합니다. 평가 빈도가 너무 높거나 평가 세트가 너무 크면 비용이 통제 불능이 될 수 있습니다.

해결책:

- 증분 평가 우선: 영향을 받은 Skill만 평가하며, 전체 회귀 테스트는 하지 않습니다.
- 계층화된 평가 세트: 핵심 세트(매번 반드시 실행) + 확장 세트(저빈도 실행).
- LLM-as-judge에 소형 모델을 사용하며, 불확실할 때만 대형 모델로 에스컬레이션합니다.

**함정 5: 4차원 가중치의 보정 부족**

기본 가중치(관련성 0.30 / 정확성 0.35 / 완전성 0.20 / 성능 0.15)는 시작점이지 종착점이 아닙니다. 실제 가중치는 서로 다른 비즈니스 시나리오와 사용자 그룹에 따라 크게 달라질 수 있습니다. 오랫동안 보정하지 않으면, 종합 점수가 사용자의 실제 경험과 괴리됩니다.

해결책:

- 인간이 주석을 단 "전반적 만족도" 점수를 수집하고, 4차원 가중 점수와 회귀 분석을 수행하여 실제 가중치를 역산합니다.
- Skill 유형별로 차별화된 가중치를 유지합니다(예: 알림형 Skill의 성능 가중치를 0.30으로 높임).
- 사용자 인식과의 정합성을 보장하기 위해 분기별로 가중치 구성을 검토합니다.

### 5.4 핵심 포인트 넷: 컨텍스트 압축 전략과 장기 메모리 전략

#### 5.4.1 목적

한 문장으로 목적을 말하면:

- **컨텍스트 압축**: 단일 요청의 LLM 컨텍스트 윈도우가 대화 턴에 따라 무한히 커지는 것을 방지합니다. 핵심 아이디어: **오래된 대화 → LLM 요약 → 원본 블록 대체, 가장 최근 N개 블록은 완전히 비압축 상태로 유지**.
- **장기 메모리**(사용자 수준): "사용자가 누구이며 세션을 넘어 무엇을 선호하는지 기억하는 것"을 관리합니다.

#### 5.4.2 압축

![컨텍스트 압축 설계](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j2vnMbOdRVfG1IZzlZh8kBedvNM4yVH4dt6ibDmiaShNKUZu4BOqMk7lKRm0pQBEibqL1iakX03SFqRAs7ibFhTnYUZricBa2Gz5nEY0/640?wx_fmt=png&from=appmsg)

블록의 `seq`는 첫 번째 Msg의 `timestamp(ms)`이며, 엄격한 증가와 멱등성을 보장합니다.

언제 시작할까요? 답: 여전히 FinanceAguiSessionManager의 역량입니다.

![압축 트리거 타이밍](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j2n14coOAFrbCtHMrvMjyPNvIianbIoh6ul14TanVgfaISptEHK3jyJOcZDuotXQiaIaRJJkAPNzUPQkfmC98ZCNrbvO2BxrwOPI/640?wx_fmt=png&from=appmsg)

압축 타이밍은 createAgent 단계에서 동기적 프로빙으로 이루어지며 — 세 임계값 중 하나라도 초과하면 트리거됩니다.

![세 가지 압축 트리거 임계값](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j3UddfNeaYjgxknpsJxdpwyrMGicQvWw1UQPRrxPIYj5CpSdT2libbMMKPUEAUpR1zJ6ljuuIRcg5o1EKZqeT9mo8IayJGKJby8s/640?wx_fmt=png&from=appmsg)

**멱등성 보장**: `INSERT IGNORE` + UK `(agent_code, thread_id, status)` → 동시에 스레드당 PENDING이 하나만 존재.

완전한 설계:

![완전한 컨텍스트 압축 설계](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j0kHPFq8xA5dkIqibPWRfumX8wdDhTORWFaaibgeohTkMkkTheibBkoEvCFicTUh4CLrpgR45zCbSnUgWyvfjfnfmE6ZH517icDXOe4/640?wx_fmt=png&from=appmsg)

**핵심 설계 포인트**:

1. **비동기 실행**: 압축은 요청 경로에 있지 않으며 사용자 지연 시간에 영향을 주지 않습니다.
2. **CAS 경합**: 여러 워커에 대해 동시성 안전합니다(`updateStatus(id, PENDING, RUNNING)`이 0을 반환 = 다른 누군가가 이미 가져감).
3. **Session 잠금 내에서 실행**: onEnter/onExit와의 블록 테이블 동시 작업을 회피합니다.
4. **실패 재시도**: 최대 5회; 한도 초과 후 RUNNING + last_error 상태를 유지하며 운영 개입을 대기합니다.
5. **SUMMARY seq 충돌 방지**: `INSERT IGNORE`가 0을 반환하면 중단하여, 중복 아카이빙을 방지합니다.
6. **다음 `createAgent` 로드 시**:
   a. `blockMapper.selectActiveAsc()`는 `archived=0`인 블록만 가져옵니다
   b. 반환: `[SUMMARY 블록] + [가장 최근 5개의 NORMAL 블록]`
   c. SUMMARY 블록의 `role=SYSTEM`이며, 텍스트가 `[Conversation Summary]`로 시작하고, LLM이 이를 과거 요약으로 인식할 수 있습니다

#### 5.4.3 장기 메모리 전략

대화에서 사용자 선호도와 사실을 자동으로 추출하고, 세션을 넘어 영속화하며, 매 요청마다 시스템 프롬프트에 주입하여, Agent가 돌아온 사용자를 "알아보게" 합니다.

![장기 메모리 전략](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1BBSfDvddCPUXdoxajgz2wTrKB0BF95qgCPuslZ6zG9eibqw6qqn9Jx2U195shP7gkfMrL4UicrkCnlGJ8Khnw6swuFzS5qyor0/640?wx_fmt=png&from=appmsg)

### 5.5 핵심 포인트 다섯: 비즈니스-파이낸스 플랫폼 관리 콘솔과의 통합, 자유로운 형태의 페이지 커스터마이징

![비즈니스-파이낸스 플랫폼 관리 콘솔과의 통합 및 자유로운 형태의 페이지 커스터마이징](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j3mp5I90ZPWtxPV9wEJicetbU5001Jk7yTZCEToibRJy51W9XQTiaJwz6TwQxkgmKxjW4iafcwBaLp6bicgwZ0u1t1IxRN8lVPu2ZIw/640?wx_fmt=png&from=appmsg)

### 5.6 핵심 포인트 여섯: 전체 링크 신원 표시와 세밀한 권한 제어 메커니즘 구축

권한 시스템을 설계할 때, 다음 측면에 집중해야 합니다.

- 권한 검증 로직을 Agent 호출 체인에 어떻게 내장할 것인가.
- 사용자 신원과 컨텍스트를 기반으로 데이터 격리를 어떻게 구현할 것인가.
- 동적이고, 시각적이며, 감사 가능한 권한 신청과 승인 흐름을 어떻게 지원할 것인가.
- MCP/Skill 계층과 하부 데이터 서비스 간의 권한 정책 일관성을 어떻게 보장할 것인가.

이 문제를 해결하는 것은 시스템의 보안, 컴플라이언스, 확장성에 직접적인 영향을 미치며, 아키텍처 진화와 동기화하여 계획하고 구현해야 합니다.

#### 5.6.1 기존 권한 시스템의 4가지 심층 방어 측면 정리

![기존 권한 시스템의 4가지 심층 방어 측면](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j1icPhMKJKibmExib9icfMKe0GcryBg7T8qw1K4XznXbrn835mHia74yk4VPy2ZibwuxHLIlZUtSdFRKIEMRKxd8qOPkoRicVrvzl1MGw/640?wx_fmt=png&from=appmsg)

#### 5.6.2 전체 링크 신원 통합

**현재 상태**: financeAgent 프로젝트 내의 신원 전파 체인은 대부분 연결되어 있습니다 — BUC SSO가 진입 계층에서 인증을 완료하고, `empId` + `ssoToken`이 HTTP 헤더를 통해 AG-UI의 `RuntimeContext`로 연결되며, MCP 호출 계층은 `McpCallIdentity` + Normandy SM2 서명(`ContextInjectingMcpTool` → `McpClientFactory.buildIsolated()`)을 통해 요청별 신원 격리를 달성합니다. 이 체인은 프로젝트 내에서 닫혀 있습니다.

**문제**: 하지만 호출 체인이 이 프로젝트의 경계를 넘어설 때, 신원 컨텍스트가 끊어집니다.

![프로젝트 경계 밖에서의 신원 컨텍스트 단절](https://mmbiz.qpic.cn/mmbiz_png/bvDbzNRia8j0gaqCmKicm7EpMBSicuYJsO0JbaWNLH8VqdTYHGavgVqonhABb7pcFx2aOzmib1DicM2mSjtheMDQaZBWPfR0WyfRvzvyrt7xx6aw/640?wx_fmt=png&from=appmsg)

**계획**:

1. **HSF 호출 체인 신원 전파**: `HsfUtil` 범용 호출 계층에 EagleEye RpcContext를 주입하여, `empId`를 호출자 컨텍스트로 하위 HSF 서비스에 전파합니다. 이를 통해 하위 서비스는 호출 애플리케이션만 식별하는 것이 아니라 사용자 수준 인증과 감사를 수행할 수 있습니다. AMDP 데이터 쿼리 체인 커버를 우선시합니다(현재 모든 사용자 쿼리가 동일한 `AuthParam`을 공유하고 있어, 무단 쿼리 위험이 있음).
2. **MCP 신원 격리의 전면 커버**: 현재 `shouldIsolate()`는 `NORMANDY_AUTH` + `AONE/ZETTA` 유형에만 적용됩니다. 향후 모든 MCP Server 유형으로 확장되어야 합니다; 요청별 신원을 지원하지 않는 MCP Server의 경우, Normandy Auth나 OAuth2 토큰 교환 모드를 통합하도록 유도하여, 공유 토큰의 보안 표면을 점진적으로 제거해야 합니다.
3. **통합 신원 컨텍스트(Identity Context)**: 현재 `McpCallIdentity`의 MCP 범위를 넘어서는 `CallerIdentity` 계층을 추상화하여, HSF / MCP / HTTP 콜백 / MetaQ 메시지 프로듀서와 같은 모든 아웃바운드 호출 시나리오를 커버합니다. 요청이 어떤 채널로 들어오고 나가든, 최종 사용자 신원이 항상 추적 가능하도록 보장합니다.

#### 5.6.3 카나리 시스템 구축

**현재 상태**: 카나리 라우팅을 위한 핵심 프레임워크는 이미 구축되어 있습니다 — `ac_agent_gray_config` 테이블 + `GrayMatcher` 3차원 매칭(백분율 / 화이트리스트 / 환경) + `BaseFinanceAgentFactory.buildPublishedSnapshot()`의 버전 분할 로직으로, 카나리 릴리스 → 검증 → 전면 롤아웃(`promoteToStable`)의 완전한 생명주기를 지원합니다.

**현재 부족한 점과 진화 방향**:

1. **백분율 라우팅 사용자 고착성**: `GrayMatcher.matchPercentage()`는 현재 요청별 무작위화를 위해 `ThreadLocalRandom`을 사용합니다; 동일한 사용자가 한 요청에서는 카나리를, 다음 요청에서는 안정 버전을 맞을 수 있습니다. 이는 문제 해결과 사용자 경험 모두에 비우호적입니다. `hash(workNo) % 100 < percentage`를 통한 결정론적 버킷팅으로 변경하여, 카나리 주기 내에서 동일한 사용자가 항상 동일한 버전으로 라우팅되도록 보장해야 합니다.
2. **카나리 관측 가능성**: 현재 카나리 히트 결과는 버전 로딩 로직에만 반영되며, 명시적인 계측과 메트릭이 부족합니다. 필요한 것:
   - AG-UI 응답 헤더에 `X-Gray-Hit: true/false`와 `X-Agent-Version`을 표시하여, 프론트엔드 인지와 디버깅에 사용합니다.
   - 핵심 메트릭(성공률, 평균 지연 시간, 도구 호출 실패율)을 카나리/안정 버전 차원별로 분할하여, Sunfire 대시보드에 연결합니다.
   - `empId` + `agentCode` + `version` 3차원 검색을 지원하는 구조화된 형태로 카나리 히트 로그를 출력합니다.
3. **다층 카나리 오케스트레이션**: 현재 카나리 세밀도는 단일 Agent 수준입니다. 향후 다음을 지원해야 합니다.
   - Skill 수준 카나리: 동일한 Agent 내에서, 일부 Skill은 카나리 버전(예: 새 프롬프트 템플릿)을 사용하고 다른 Skill은 온라인 버전을 유지합니다.
   - MCP Server 수준 카나리: 신버전 MCP Server는 카나리 사용자에게만 공개되어, 새 도구의 불안정성이 모든 사용자에게 영향을 미치는 것을 방지합니다.
   - 결합 전략: 화이트리스트 + 백분율을 중첩할 수 있습니다(먼저 화이트리스트로 내부 검증 → 그다음 백분율 확대); 현재는 세 전략이 상호 배타적입니다.
4. **자동 카나리 승격과 롤백**:
   - 카나리 단계에 대한 자동 승격 조건을 설정합니다: 카나리 사용자 수 ≥ N이고 성공률 ≥ 임계값 → 자동으로 백분율 확대 → 전면 롤아웃.
   - 자동 롤백 조건을 설정합니다: 카나리 버전 오류율이 급증(안정 버전 대비 > X%) → 카나리 트래픽을 안정 버전으로 자동 전환, Sunfire 알림 발행.
   - 현재 `promoteToStable`은 수동 작업입니다; 이를 기반으로 자동화된 결정 계층을 추가해야 합니다.
5. **카나리-HITL 연동**: 카나리 버전의 신규 도구/Skill은 HITL(Human-in-the-Loop) 확인 수준을 자동으로 높여야 합니다 — 카나리 트래픽의 도구 호출은 기본적으로 BEFORE 모드 확인을 거치며, 안정 버전은 AFTER나 확인 없음으로 다운그레이드할 수 있어, 카나리 버전의 영향 반경을 줄일 수 있습니다.

#### 5.6.4 기타 보안 인프라

![기타 보안 인프라](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j1yFo9I3eNlmV6MicSfQ6EKk1GK9wTzaUOXdFZTfOC5sSvfKy9Y5ibO8IbOD2VSqLoyz5iaz6WUDFYU51Nrz9TKZPT9jgj9IvMRbw/640?wx_fmt=png&from=appmsg)

위 계획은 코드의 현재 실제 상태(`GrayMatcher`, `McpClientFactory.buildIsolated()`, `HitlHook`, `UserUtils` 등)를 기반으로 하며; 각 개선사항은 명확한 코드 진입점을 가지고 있어 우선순위에 따라 단계적으로 구현할 수 있습니다.

### 5.7 전체 링크 관측 가능성의 구현

![전체 링크 관측 가능성의 구현](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j2RYiabI55AylqPA6QW12s0l6Z3KL9WBhOeUtHibQxtdVicPL4f8Pia0ib4smWAPiaY7Dqo9giceIPg5264PMpibQe33tSQyqqkVFe8cCg/640?wx_fmt=png&from=appmsg)

## 06 최종 전체 프레임워크 요약

![전체 프레임워크 요약](https://mmbiz.qpic.cn/sz_mmbiz_png/bvDbzNRia8j1Z2sPdjkXUOwibQL1cmqYzEWrMgic2B3cTbKQ1zOUFpnpFyv4ww63oKuW8mweOxq49qYNibrpjgnX0d9AUvsZSpxVqbZQelI5HoM/640?wx_fmt=png&from=appmsg)
