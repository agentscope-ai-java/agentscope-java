# Bailian メモリ

`agentscope-extensions-memory-bailian` は、Alibaba Cloud Bailian の長期記憶サービスと統合します。完全マネージド型で、リランク（rerank）、判定（judge）、書き換え（rewrite）などの高度な検索機能をサポートします。

## 利用場面

- すでに Alibaba Cloud Bailian を利用しており、プラットフォーム上のメモリライブラリを再利用したい場合。
- 検索品質を重視し、Bailian のリランク / 判定 / 書き換えパイプラインを使いたい場合。
- `userId` + `memoryLibraryId` + `projectId` による3段階の分離が必要な場合。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-memory-bailian</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## クイックスタート

```java
import io.agentscope.core.memory.bailian.BailianLongTermMemory;

try (BailianLongTermMemory memory = BailianLongTermMemory.builder()
        .apiKey(System.getenv("DASHSCOPE_API_KEY"))
        .userId("user_001")
        .memoryLibraryId("lib_xxxxx")
        .projectId("proj_xxxxx")
        .build()) {

    ReActAgent agent = ReActAgent.builder()
        .name("Assistant")
        .model(model)
        .longTermMemory(memory)
        .longTermMemoryMode(LongTermMemoryMode.BOTH)
        .build();

    agent.call(new UserMessage("毎日午前9時に水を飲むようリマインドしてください")).block();
}
```

`BailianLongTermMemory` は `AutoCloseable` を実装しています。基盤となる HTTP 接続を確実に解放するため、try-with-resources の使用を推奨します。

## 検索機能スイッチ

基本的な想起（recall）に加えて、Bailian は3つのパイプラインスイッチをサポートしています。

```java
BailianLongTermMemory memory = BailianLongTermMemory.builder()
    .apiKey(apiKey)
    .userId("user_001")
    .memoryLibraryId("lib_xxxxx")
    .topK(20)
    .minScore(0.4)
    .enableRerank(true)   // 結果を再ランキングする。より正確になるが速度は遅くなる
    .enableJudge(true)    // LLM に結果が実際に関連しているかどうかを判定させる
    .enableRewrite(true)  // 書き込み時にメモリを書き換え/マージする
    .build();
```

必要がない限りデフォルトではオフにしておいてください。これらはレイテンシとコストを増加させます。

## メッセージフィルタリング

Bailian メモリは、自然な user/assistant のやり取りのみを保存します。

- `MsgRole.USER` と `MsgRole.ASSISTANT` のメッセージのみが書き込まれます。
- `ToolUseBlock`（ツール呼び出しリクエスト）を含む assistant メッセージはスキップされます。
- 重複した圧縮履歴の保存を避けるため、`<compressed_history>` マーカーが付いたメッセージはスキップされます。

ツールの実行結果をメモリに含めたい場合は、`record(...)` を呼び出す前に、より上位のロジックで自分で書き込んでください。

## Builder リファレンス

| メソッド | 必須 | デフォルト | 備考 |
| --- | --- | --- | --- |
| `apiKey(String)` | ✅ | - | Bailian DashScope API キー |
| `userId(String)` | ✅ | - | ユーザーレベルの ID |
| `memoryLibraryId(String)` | ❌ | - | メモリライブラリ ID |
| `projectId(String)` | ❌ | - | プロジェクト ID |
| `profileSchema(String)` | ❌ | - | ユーザープロフィールスキーマ ID |
| `apiBaseUrl(String)` | ❌ | `https://dashscope.aliyuncs.com` | カスタムゲートウェイ用のオーバーライド |
| `topK(Integer)` | ❌ | `10` | 取得するアイテムの最大数 |
| `minScore(Double)` | ❌ | `0.3` | 最小類似度しきい値 (0–1) |
| `enableRerank(Boolean)` | ❌ | `false` | リランクを有効化 |
| `enableJudge(Boolean)` | ❌ | `false` | LLM 判定を有効化 |
| `enableRewrite(Boolean)` | ❌ | `false` | 書き込み時の書き換えを有効化 |
| `metadata(Map)` | ❌ | - | 各メモリとともに保存されるカスタムメタデータ |
| `httpTransport(HttpTransport)` | ❌ | default | HTTP クライアントを置き換える |
