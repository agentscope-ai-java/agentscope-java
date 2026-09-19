---
title: Higress AI Gateway
---

`agentscope-extensions-higress` は、[Higress](https://higress.io/) 上で MCP (Model Context Protocol) として公開されたツールを AgentScope に取り込みます。Higress はゲートウェイ層でツール検索、認証、レート制限、可観測性を処理し、Agent は結果として得られたツールを呼び出すだけです。

## 使いどころ

- すでに Higress を AI ゲートウェイとして運用しており、そのツールを Agent に取り込みたい場合。
- ツールガバナンス(ルーティング、認証、クォータ)を Agent のビジネスロジックから切り離したい場合。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-higress</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## クイックスタート

```java
import io.agentscope.extensions.higress.HigressMcpClientBuilder;
import io.agentscope.extensions.higress.HigressMcpClientWrapper;
import io.agentscope.extensions.higress.HigressToolkit;

// 1) Higress が公開する MCP エンドポイントに対してクライアントを作成する
HigressMcpClientWrapper client = HigressMcpClientBuilder
    .create("higress")
    .streamableHttpEndpoint("http://gateway/mcp-servers/union-tools-search")
    .build();

// 2) HigressToolkit(Higress クライアントをキャッシュする Toolkit のサブクラス)に登録する
HigressToolkit toolkit = new HigressToolkit();
toolkit.registerMcpClient(client).block();

// 3) Agent から利用する
ReActAgent agent = ReActAgent.builder()
    .name("Assistant")
    .model(model)
    .toolkit(toolkit)
    .build();
```

## ツールを選択的に有効化する

`HigressToolkit` は、グループ / 許可リストによるきめ細かい制御のために、標準の `Toolkit` の fluent 登録 API を再利用します。

```java
toolkit.registration()
    .mcpClient(client)
    .enableTools(List.of("search-doc", "fetch-url"))
    .group("knowledge")
    .apply();
```

## 基盤となる MCP クライアントへのアクセス

Higress 固有の拡張機能(例: `HigressToolSearchResult` によるツール検索)を呼び出す必要がある場合:

```java
HigressMcpClientWrapper higressClient = toolkit.getHigressMcpClient();
```

> ツールガバナンス(認証、レート制限、ルーティング、可観測性)はゲートウェイ側にあるため、Agent 側で再実装する必要はありません。
