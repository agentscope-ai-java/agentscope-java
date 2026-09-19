---
title: Online Training
---

`agentscope-extensions-training`은 Trinity 스타일의 트레이닝 백엔드를 AgentScope에 연결합니다: 프로덕션 트래픽을 샘플링하고, 트레이스를 수집하고, 보상(reward)을 계산하고, 주기적으로 트레이닝 작업을 커밋합니다 — 이렇게 루프를 완성합니다.

## 사용 시점

- Trinity(또는 호환 서비스)를 트레이닝 스토어로 운영할 때.
- 강화 학습이나 온라인 파인튜닝을 위해 실시간 트래픽을 사용하고 싶을 때.
- 트레이닝 파이프라인이 Agent를 호출하는 쪽에서는 투명하게 동작하기를 원할 때.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-training</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 빠른 시작

```java
import io.agentscope.core.training.runner.TrainingRunner;
import io.agentscope.core.training.strategy.SamplingRateStrategy;

TrainingRunner runner = TrainingRunner.builder()
    .trinityEndpoint("http://localhost:8080")
    .modelName("/path/to/model")
    .selectionStrategy(SamplingRateStrategy.of(0.1))   // 10% sampling
    .rewardCalculator(agent -> 0.0)                    // custom reward
    .commitIntervalSeconds(300)                        // commit every 5 minutes
    .build();

runner.start();          // intercept Agent calls and start sampling

// Business code keeps using the Agent unmodified
agent.call(msg).block();

runner.stop();           // stop the training pipeline
```

## 선택 전략

- `SamplingRateStrategy.of(0.1)`: 지정된 비율로 무작위 샘플링합니다.
- `ExplicitMarkingStrategy.create()`: 표시된 요청만 샘플링합니다.
- 또는 `TrainingSelectionStrategy`를 구현하여 커스텀 동작을 정의할 수 있습니다.

## 보상 계산

`rewardCalculator`는 `Function<AgentBase, Double>`이며, 샘플링된 궤적(trajectory)마다 한 번씩 호출됩니다.

- 람다 — 답변 길이, 도구 호출 횟수 등의 휴리스틱.
- 더 풍부한 지표를 위해 `RewardCalculator`를 구현한 커스텀 클래스.

```java
TrainingRunner runner = TrainingRunner.builder()
    .trinityEndpoint(endpoint)
    .modelName(model)
    .selectionStrategy(SamplingRateStrategy.of(0.1))
    .rewardCalculator(new MyMetricRewardCalculator())
    .build();
```

## 동작 방식

1. `runner.start()` 이후, 요청은 `TrainingRouter`를 거칩니다.
   - 샘플링됨 → Trinity 스토어로 라우팅되고, 트레이스가 수집됩니다;
   - 샘플링되지 않음 → 원래 모델이 사용되며, 부작용이 없습니다.
2. 샘플링된 궤적은 보상 계산기를 호출하고 `TrinityClient.feedback(...)`을 통해 피드백을 전달합니다.
3. `commitIntervalSeconds`마다 `commit(...)`이 트레이닝 작업을 트리거합니다.

`runner.stop()`은 타이머와 커넥션 풀을 깔끔하게 종료합니다.

## 주요 설정

| 필드 | 설명 |
| --- | --- |
| `trinityEndpoint` | Trinity 서비스 URL |
| `modelName` | 대상 모델 경로 또는 별칭 |
| `selectionStrategy` | 샘플링 전략 |
| `rewardCalculator` | 보상 함수 |
| `commitIntervalSeconds` | 커밋 간격, 기본값 300 |

## Studio와 함께 사용하기 좋음

`StudioMessageHook`을 동시에 연결하면, 어떤 세션이 샘플링되었고 보상이 어떻게 계산되었는지를 Studio에서 확인할 수 있습니다.
