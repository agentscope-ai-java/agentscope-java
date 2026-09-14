# AgentScope Studio

`agentscope-extensions-studio` は、Agent を [AgentScope Studio](https://github.com/agentscope-ai/agentscope-studio) と統合します。すべての Agent 呼び出しは Studio にプッシュされ、視覚的なデバッグ、トレースのリプレイ、Human-in-the-Loop 入力に利用できます。

## 使用すべき場面

- 開発中に Studio 上でイベントストリーム、推論過程、ツール呼び出しを確認したい場合。
- Studio から `requestUserInput` を発行し、実際のユーザーに応答してもらう必要がある場合。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-studio</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## クイックスタート

```java
import io.agentscope.core.studio.StudioManager;
import io.agentscope.core.studio.StudioMessageHook;

// 1) Studio 接続を初期化する（HTTP + WebSocket）
StudioManager.init()
    .studioUrl("http://localhost:8000")
    .project("MyProject")
    .runName("experiment_001")
    .initialize()
    .block();

// 2) StudioMessageHook をアタッチし、メッセージが Studio にプッシュされるようにする
ReActAgent agent = ReActAgent.builder()
    .name("Assistant")
    .model(model)
    .hook(new StudioMessageHook(StudioManager.getClient()))
    .build();

// 3) Agent を通常どおり使用する。Studio が会話をミラーリングする
agent.call(msg).block();
```

## Studio が提供するもの

- **メッセージのプッシュ**: user / assistant / tool のすべてのメッセージが Studio にミラーリングされます。
- **トレース**: Studio は `runName` ごとにイベントをトレースツリーへと整理します。
- **Human-in-the-Loop**: `StudioUserAgent` または `requestUserInput` により、Studio の UI が実行継続前に実際のユーザーへ入力を促します。

## API 概要

| クラス | 目的 |
| --- | --- |
| `StudioManager` | シングルトンのエントリーポイント — クライアントの初期化とアクセス |
| `StudioConfig` | URL / project / runName の設定 |
| `StudioClient` | イベント、メッセージ、run 登録のための HTTP クライアント |
| `StudioWebSocketClient` | 受信コマンド（ユーザー入力など）のための WebSocket クライアント |
| `StudioMessageHook` | `Msg` を自動でプッシュする `ReActAgent` 用の `Hook` |
| `StudioUserAgent` | Studio のユーザー入力をブロック待機する「人間が演じる」Agent |

## 無効化すべき場面

本番環境では、通常このフックをアタッチしたくないでしょう（すべての呼び出しが Studio に書き込まれるため）。Spring プロファイルや `@ConditionalOnProperty` を使ってゲートします。

```java
@Bean
@ConditionalOnProperty("agentscope.studio.enabled")
StudioMessageHook studioHook() {
    StudioManager.init().studioUrl(url).project(project).initialize().block();
    return new StudioMessageHook(StudioManager.getClient());
}
```
