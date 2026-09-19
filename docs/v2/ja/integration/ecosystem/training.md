---
title: オンライントレーニング
---

`agentscope-extensions-training` は、Trinity 方式のトレーニングバックエンドを AgentScope に組み込みます。本番トラフィックをサンプリングし、トレースを収集し、報酬を計算し、定期的にトレーニングジョブをコミットすることで、ループを閉じます。

## 使用すべき場面

- トレーニングストアとして Trinity（または互換サービス）を運用している場合。
- 強化学習やオンラインのファインチューニングにライブトラフィックを使用したい場合。
- トレーニングパイプラインを Agent の呼び出し元に対して透過的にしたい場合。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-training</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## クイックスタート

```java
import io.agentscope.core.training.runner.TrainingRunner;
import io.agentscope.core.training.strategy.SamplingRateStrategy;

TrainingRunner runner = TrainingRunner.builder()
    .trinityEndpoint("http://localhost:8080")
    .modelName("/path/to/model")
    .selectionStrategy(SamplingRateStrategy.of(0.1))   // 10% サンプリング
    .rewardCalculator(agent -> 0.0)                    // カスタム報酬
    .commitIntervalSeconds(300)                        // 5 分ごとにコミット
    .build();

runner.start();          // Agent 呼び出しをインターセプトし、サンプリングを開始する

// ビジネスコードは変更せずに Agent を使い続ける
agent.call(msg).block();

runner.stop();           // トレーニングパイプラインを停止する
```

## 選択戦略

- `SamplingRateStrategy.of(0.1)`: 指定した割合でのランダムサンプリング。
- `ExplicitMarkingStrategy.create()`: マークされたリクエストのみをサンプリングする。
- または、独自の挙動を実装するために `TrainingSelectionStrategy` を実装する。

## 報酬の計算

`rewardCalculator` は `Function<AgentBase, Double>` であり、サンプリングされた軌跡ごとに一度呼び出されます。

- ラムダ — 回答の長さやツール呼び出し回数などのヒューリスティック。
- より豊富な指標のために `RewardCalculator` を実装したカスタムクラス。

```java
TrainingRunner runner = TrainingRunner.builder()
    .trinityEndpoint(endpoint)
    .modelName(model)
    .selectionStrategy(SamplingRateStrategy.of(0.1))
    .rewardCalculator(new MyMetricRewardCalculator())
    .build();
```

## 仕組み

1. `runner.start()` の後、リクエストは `TrainingRouter` を経由します。
   - サンプリングされた場合 → Trinity ストアへルーティングされ、トレースが収集される。
   - サンプリングされなかった場合 → 元のモデルが使用され、副作用はない。
2. サンプリングされた軌跡は報酬計算機を呼び出し、`TrinityClient.feedback(...)` を通じてフィードバックする。
3. `commitIntervalSeconds` ごとに、`commit(...)` がトレーニングジョブをトリガーする。

`runner.stop()` は、タイマーとコネクションプールをきれいにシャットダウンします。

## 主要な設定

| フィールド | 補足 |
| --- | --- |
| `trinityEndpoint` | Trinity サービスの URL |
| `modelName` | 対象モデルのパスまたはエイリアス |
| `selectionStrategy` | サンプリング戦略 |
| `rewardCalculator` | 報酬関数 |
| `commitIntervalSeconds` | コミット間隔、デフォルトは 300 |

## Studio との相性

`StudioMessageHook` を同時にアタッチすれば、どのセッションがサンプリングされ、報酬がどのように計算されたかを Studio 上で確認できます。
