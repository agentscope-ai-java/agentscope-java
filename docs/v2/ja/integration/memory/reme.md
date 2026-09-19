---
title: ReMe
---

`agentscope-extensions-reme` は、セルフホスト型の ReMe メモリサービスと統合します。その特徴は、**トラジェクトリベース**のメモリ抽出と**ワークスペースレベル**の分離です。

## 利用場面

- 立ち上げが簡単な軽量なセルフホスト型メモリサービスが欲しい場合。
- 個々のメッセージではなく、会話のトラジェクトリ全体の要約を重視する場合。
- `userId` によって論理的なワークスペースを表現できる場合（ユーザーごとに1つのワークスペース）。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-reme</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## クイックスタート

```java
import io.agentscope.core.memory.reme.ReMeLongTermMemory;

ReMeLongTermMemory memory = ReMeLongTermMemory.builder()
    .userId("task_workspace")            // ReMe の workspace_id にマッピングされる
    .apiBaseUrl("http://localhost:8002") // あなたの ReMe サーバー
    .build();

ReActAgent agent = ReActAgent.builder()
    .name("Assistant")
    .model(model)
    .longTermMemory(memory)
    .longTermMemoryMode(LongTermMemoryMode.BOTH)
    .build();
```

`userId` は ReMe の `workspace_id` にマッピングされます。これは ReMe におけるメモリ分割の最小単位です。

## 仕組み

- **書き込み（record）**: フィルタリングされたメッセージは1つの `ReMeTrajectory` に結合され、ReMe の `add` エンドポイントに送信されます。その後サーバー側がトラジェクトリに対して LLM 抽出を実行し、検索可能なメモリスニペットを生成します。
- **取得（Retrieve）**: 現在のメッセージがクエリとして ReMe の `search` に対して使用されます。サーバー側で集約された `answer` フィールドが存在する場合はそれが返され、存在しない場合は複数のメモリスニペットが結合されて返されます。

書き込み時のフィルタリングは Bailian と同じです。

- `USER` と `ASSISTANT` のメッセージのみが保持されます。
- `ToolUseBlock`（ツール呼び出しリクエスト）を含む assistant メッセージはスキップされます。
- `<compressed_history>` マーカーを含むメッセージはスキップされます。

## Builder リファレンス

| メソッド | 必須 | デフォルト | 備考 |
| --- | --- | --- | --- |
| `userId(String)` | ✅ | - | ワークスペース ID（書き込みと読み取りの両方に使用） |
| `apiBaseUrl(String)` | ✅ | - | ReMe サービスの URL。例: `http://localhost:8002` |
| `timeout(Duration)` | ❌ | `60s` | HTTP タイムアウト |

> ReMe はまだよりきめ細かなメタデータフィルタリングを公開していません。タグベースのセグメンテーションが必要な場合は、`userId` の中にエンコードしてください（例: `tenant-a:project-1`）。
