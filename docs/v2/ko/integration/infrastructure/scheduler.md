# Scheduler

`agentscope-extensions-scheduler`는 Agent를 주기적으로 실행합니다 — 예를 들어 "매일 오전 8시에 daily-report Agent를 실행" 또는 "5초마다 health-check Agent를 실행"과 같은 방식입니다. 이 모듈은 통합된 `AgentScheduler` 인터페이스를 추상화하고 두 가지 구현을 제공합니다.

| 하위 모듈 | 구현 | 배포 |
| --- | --- | --- |
| `agentscope-extensions-scheduler-quartz` | [Quartz](https://www.quartz-scheduler.org/) | 단독 실행 또는 클러스터(Quartz DB 공유) |
| `agentscope-extensions-scheduler-xxl-job` | [XXL-Job](https://www.xuxueli.com/xxl-job/) | 분산 스케줄링, 관리 서버 필요 |

SPI는 `agentscope-extensions-scheduler-common`에 있으므로 다른 스케줄러를 연결할 수도 있습니다.

## 공통 개념

- `AgentConfig`(또는 `RuntimeAgentConfig`): Agent를 구성하는 방법 — 이름, 모델 설정, 시스템 프롬프트, Toolkit.
- `ScheduleConfig`: 스케줄링 정책 — CRON, 고정 주기, 최대 병렬 처리 수.
- `AgentScheduler`: 핵심 인터페이스 — `schedule(...)` / `pause(...)` / `resume(...)` / `cancel(...)` / `shutdown()`.
- `ScheduleAgentTask`: 등록 시 반환되는 핸들이며, 하나의 예약된 작업을 나타냅니다.

각 트리거는 상태 누수를 방지하기 위해 실행할 때마다 새 Agent 인스턴스를 생성합니다.

## Quartz 모드

### 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-scheduler-quartz</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

### 사용법

```java
import io.agentscope.extensions.scheduler.AgentScheduler;
import io.agentscope.extensions.scheduler.config.AgentConfig;
import io.agentscope.extensions.scheduler.config.DashScopeModelConfig;
import io.agentscope.extensions.scheduler.config.ScheduleConfig;
import io.agentscope.extensions.scheduler.config.ScheduleMode;
import io.agentscope.extensions.scheduler.quartz.QuartzAgentScheduler;

AgentScheduler scheduler = QuartzAgentScheduler.builder()
    .autoStart(true)
    .build();

AgentConfig agent = AgentConfig.builder()
    .name("DailyReportAgent")
    .modelConfig(DashScopeModelConfig.builder()
        .apiKey(apiKey).modelName("qwen-plus").build())
    .sysPrompt("You are a report assistant; please generate a sales summary every day.")
    .build();

ScheduleConfig schedule = ScheduleConfig.builder()
    .scheduleMode(ScheduleMode.FIXED_RATE)
    .fixedRate(5_000L)   // every 5s
    // or .scheduleMode(ScheduleMode.CRON).cron("0 0 8 * * ?")
    .build();

scheduler.schedule(agent, schedule);
```

런타임 제어:

```java
scheduler.pause("DailyReportAgent");
scheduler.resume("DailyReportAgent");
scheduler.cancel("DailyReportAgent");
scheduler.shutdown();
```

## XXL-Job 모드

### 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-scheduler-xxl-job</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

### 사용법

```java
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import io.agentscope.extensions.scheduler.xxljob.XxlJobAgentScheduler;

// 1) XXL-Job executor 기동
XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
executor.setAdminAddresses("http://localhost:8080/xxl-job-admin");
executor.setAppname("agentscope-demo");
executor.setAccessToken("xxxxxxxx");
executor.setPort(9999);
executor.start();

// 2) AgentScheduler로 감싸기
AgentScheduler scheduler = new XxlJobAgentScheduler(executor);

// 3) Agent를 JobHandler로 등록
ScheduleAgentTask task = scheduler.schedule(agentConfig, ScheduleConfig.builder().build());
```

등록 후에는 **XXL-Job 관리 콘솔에서 일정(CRON, 병렬 처리 수, 라우팅)을 설정**하세요. Agent 이름 `DailyReportAgent`가 그곳에 JobHandler로 표시됩니다.

## 도구 바인딩

Toolkit을 바인딩해야 할 때는 `RuntimeAgentConfig`(향후 변경될 수 있는 과도기적 API)를 사용하세요.

```java
RuntimeAgentConfig agent = RuntimeAgentConfig.builder()
    .name("OpsAgent")
    .modelConfig(modelConfig)
    .sysPrompt("Run health checks and send alerts.")
    .toolkit(toolkit)
    .build();
```

## 선택 기준

- **외부 스케줄러 서비스 없이 로컬 또는 소규모 클러스터** → Quartz
- **콘솔, 노드 간 라우팅, 작업 로그가 필요** → XXL-Job
- **직접 만든 스케줄러 프레임워크를 사용하고 싶다면** → `AgentScheduler` SPI를 구현
