---
title: OSS ステートストア
---

<Note>

このページは [分散ストレージ — OSS](/v2/ja/integration/distributed/oss) に統合されました。以下の内容は参考のために残しています。

</Note>

`agentscope-extensions-oss` は AgentScope のエージェント状態を Alibaba Cloud Object Storage Service (OSS) に永続化します。大容量データや Alibaba Cloud エコシステムに最適です。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-oss</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## クイックスタート

```java
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.oss.OssAgentStateStore;

OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

AgentStateStore stateStore = OssAgentStateStore.builder()
    .ossClient(ossClient)
    .bucketName("my-agentscope-bucket")
    .keyPrefix("agentscope/state/")
    .build();

ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model(model)
    .stateStore(stateStore)
    .build();
```

## キーのレイアウト

`(userId, sessionId)` のペアは OSS オブジェクトパスにパックされます:

| 種類 | キーパターン |
| --- | --- |
| 単一値 | `{keyPrefix}{userId}/{sessionId}/{stateKey}.json` |
| リスト | `{keyPrefix}{userId}/{sessionId}/{stateKey}.list.json` |
| リストハッシュ | `{keyPrefix}{userId}/{sessionId}/{stateKey}.list.hash`（変更検出用） |

匿名セッション（`userId` が null）では、ユーザーセグメントとして `__anon__` が使用されます。

## ビルダーリファレンス

| メソッド | 説明 |
| --- | --- |
| `ossClient(OSS)` | 必須。Alibaba Cloud OSS クライアント |
| `bucketName(String)` | 必須。OSS バケット名 |
| `keyPrefix(String)` | デフォルト `agentscope/state/` |

## セキュリティ

- 本番環境では RAM Role + STS 一時クレデンシャルを使用してください — AK/SK のハードコードは避けてください
- ストレージコストを抑えるため、バケットのライフサイクルルール（例: 7日間の自動有効期限切れ）を設定してください
