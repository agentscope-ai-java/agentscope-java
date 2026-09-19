---
title: Mem0
---

`agentscope-extensions-mem0` は、長期記憶ストアとして [Mem0](https://mem0.ai/) と統合し、ベクトル検索と LLM ベースのメモリ抽出を組み合わせます。Mem0 SaaS プラットフォーム、セルフホスト型デプロイ、ローカルスタンドアロン構成のいずれもサポートします。

## 利用場面

- Agent にセッションをまたいだ事実的なメモリ（ユーザーの好み、過去の意思決定など）を持たせたい場合。
- `agentId / userId / runId` の3つのメタデータ層によるマルチテナント分離が必要な場合。
- 取得時に `category=travel` のようなカスタムメタデータでメモリをフィルタリングしたい場合。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-mem0</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## クイックスタート

```java
import io.agentscope.core.memory.mem0.Mem0LongTermMemory;
import io.agentscope.core.memory.mem0.Mem0ApiType;

// 1. メモリインスタンスを構築する（ローカル、認証なし）
Mem0LongTermMemory memory = Mem0LongTermMemory.builder()
    .agentName("Assistant")
    .userId("user_123")
    .apiBaseUrl("http://localhost:8000")
    .apiType(Mem0ApiType.SELF_HOSTED)
    .build();

// 2. Agent に組み込む
ReActAgent agent = ReActAgent.builder()
    .name("Assistant")
    .model(model)
    .longTermMemory(memory)
    .longTermMemoryMode(LongTermMemoryMode.BOTH)
    .build();

// 3. 通常どおり会話する — メモリは自動的に記録・取得される
agent.call(new UserMessage("旅行するときはホームステイが好きです")).block();
```

## デプロイモード

`Mem0ApiType` は URL の規約と認証方式を決定します。

| Enum | ユースケース | `apiBaseUrl` の例 | `apiKey` |
| --- | --- | --- | --- |
| `PLATFORM`（デフォルト） | Mem0 SaaS | `https://api.mem0.ai` | 必須 |
| `SELF_HOSTED` | セルフホスト型 Mem0 サーバー | `http://your-host:8000` | デプロイ方法による |

## マルチテナント分離

`agentName / userId / runName` は、Mem0 がメモリを整理するために使用する3つの識別子レイヤーです。

```java
Mem0LongTermMemory memory = Mem0LongTermMemory.builder()
    .agentName("travel-bot")     // ユーザー間で共有される Agent レベルのメモリ
    .userId("alice")             // ユーザー/テナントレベル
    .runName("trip-2026-spring") // セッションごと
    .apiBaseUrl("http://localhost:8000")
    .build();
```

3つのうち少なくとも1つを指定する必要があります。指定しない場合、`build()` は `IllegalArgumentException` をスローします。取得時には、メタデータが一致するメモリのみが返されます。

## カスタムメタデータフィルタリング

`metadata(...)` は書き込みと読み取りの両方に適用されます。各メモリとともに永続化され、取得時にはフィルタとして注入されます。

```java
Map<String, Object> tags = Map.of(
    "category", "travel",
    "project_id", "proj_001"
);

Mem0LongTermMemory memory = Mem0LongTermMemory.builder()
    .agentName("Assistant")
    .userId("user_123")
    .apiBaseUrl("http://localhost:8000")
    .metadata(tags)
    .build();

// これ以降のすべての record() 呼び出しには tags が付与される。retrieve() は同じ tags を持つメモリのみにマッチする
```

プロジェクトや事業ラインごとに知識を分割したい場合に便利です。ユーザー単位の分離だけで十分な場合は、`userId` だけで足ります。

## Builder リファレンス

| メソッド | 必須 | デフォルト | 備考 |
| --- | --- | --- | --- |
| `apiBaseUrl(String)` | ✅ | - | Mem0 サービスの URL |
| `apiKey(String)` | 場合による | - | API キー。ローカルの認証なしデプロイでは不要 |
| `apiType(Mem0ApiType)` | ❌ | `PLATFORM` | SaaS かセルフホストかのルーティングを選択 |
| `agentName(String)` | 3つのうち1つ | - | Agent レベルの ID |
| `userId(String)` | 3つのうち1つ | - | ユーザーレベルの ID |
| `runName(String)` | 3つのうち1つ | - | Run/セッションレベルの ID |
| `metadata(Map)` | ❌ | `null` | 書き込みと読み取りの両方に適用される追加フィルタ |
| `timeout(Duration)` | ❌ | `60s` | HTTP タイムアウト |

> `agentName / userId / runName` のうち少なくとも1つを設定する必要があります。設定しない場合、`build()` は `IllegalArgumentException` をスローします。
