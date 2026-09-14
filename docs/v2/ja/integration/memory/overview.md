# メモリ

`LongTermMemory` は、複数のターンやセッションにまたがってユーザーの好み、事実、重要な要点を永続化するための AgentScope のインターフェースです。`agentscope-extensions-*` リポジトリには、主要なメモリストア向けにすぐ使える実装が同梱されています。

| 拡張機能 | バックエンド | 最適な用途 |
| --- | --- | --- |
| [Mem0](mem0.md) | [Mem0](https://mem0.ai/) Platform / セルフホスト | マルチテナント分離とメタデータフィルタリングを備えた汎用セマンティックメモリ |
| [Bailian](bailian.md) | Alibaba Cloud Bailian メモリサービス | リランク / 判定 / 書き換え機能を備えたクラウドマネージド型メモリ |
| [ReMe](reme.md) | セルフホスト型 ReMe サービス | トラジェクトリ要約機能を備えたワークスペースレベルのメモリ |

3つすべてが同じ `io.agentscope.core.memory.LongTermMemory` インターフェースを実装しており、Agent への組み込み方法も同じです。

```java
ReActAgent agent = ReActAgent.builder()
    .name("Assistant")
    .model(model)
    .longTermMemory(memory)                       // 3つの実装のいずれか
    .longTermMemoryMode(LongTermMemoryMode.BOTH)  // 記録と取得の両方
    .build();
```

## 実装の選び方

- **単一の `docker run` でローカルに起動したい** → Mem0 または ReMe
- **すでに Alibaba Cloud Bailian を利用している** → Bailian
- **メタデータフィルタリングが必要（ビジネス上の切り口でメモリを分割）** → Mem0
- **エンドツーエンドの会話トラジェクトリ要約を重視する** → ReMe

各実装の違いは、初期化パラメータとフィルタのセマンティクスのみです。Agent 自体から見れば透過的です。詳細は各サブページを参照してください。
