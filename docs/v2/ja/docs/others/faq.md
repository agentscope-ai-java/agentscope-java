---
title: "FAQ"
description: "AgentScope Java 2.0 に関するよくある質問"
---

:::{dropdown} AgentScope Java 2.0 は 1.0 と互換性がありますか?
AgentScope Java 2.0 は、ほとんどのユーザーがスムーズにアップグレードできるよう、可能な限り 1.x との互換性を維持することを目指しています。とはいえ、2.0 は API レベルの破壊的変更を導入しています — 再設計されたエージェント抽象化、新しいイベントシステム、パーミッションシステム、Middleware スタックを含みます。詳細は [V1 移行ガイド](../change-log.md) を参照してください。

    新規プロジェクトでは、新機能の恩恵を受けるために 2.0 を直接採用することを推奨します。既存ユーザー向けに 1.0 のドキュメントも引き続き利用可能です。
:::

  :::{dropdown} AgentScope Java 2.0 に同梱されるフロントエンドはありますか?
はい。リポジトリには `agentscope-admin` モジュールが含まれています。これは `ReActAgent` と同じプロトコルを話す、すぐに使える Web アプリです。カスタム UI コードなしで動作し、イベントシステム(`AgentEvent`)とパーミッションシステムの HITL フローにきれいに統合されます。
:::

  :::{dropdown} 2.0 は RAG と長期記憶を提供しますか?
はい。`io.agentscope.core.rag` と `io.agentscope.core.memory.LongTermMemory` はすでにリポジトリに存在しますが、ナレッジベース、ドキュメントリーダーなどの関連コンポーネントはまだ完成途上です — 進捗は [リリースノート](release-notes.md) と GitHub のリリースで追跡できます。
:::

  :::{dropdown} モデルプロバイダーを切り替えるには?
`.model(...)` に渡す `<provider>:<model-name>` 文字列を変更し、対応する
`agentscope-extensions-model-*` の依存関係を追加し、そのプロバイダーの API キー環境変数を設定してください。
他にコードの変更は必要ありません — `ModelRegistry` が実行時にプロバイダーを解決します。

```java
ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .model("openai:gpt-4.1")   // 変更前は "dashscope:qwen-plus"
                .build();
```

```bash
# 上で選んだ行に対応する環境変数を設定する
export OPENAI_API_KEY=sk-your-key-here
```

| Provider | Model string prefix | Env var |
|---|---|---|
| DashScope | `dashscope:qwen-plus` | `DASHSCOPE_API_KEY` |
| OpenAI | `openai:gpt-4.1` | `OPENAI_API_KEY` |
| Anthropic | `anthropic:claude-sonnet-4-7` | `ANTHROPIC_API_KEY` |
| Gemini | `gemini:gemini-2.0-flash` | `GEMINI_API_KEY` |
| Ollama (local) | `ollama:llama3` | none (optional `OLLAMA_BASE_URL`) |

完全なプロバイダー一覧と明示的なビルダーの設定については [Model](../building-blocks/model.md) を参照してください。
:::

  :::{dropdown} なぜ `.model("...")` が "model not found" をスローするのですか?
これは `ModelRegistry` が `<provider>:<model-name>` 文字列を解決できなかったことを意味します — ほぼ常に、
対応する `agentscope-extensions-model-*` モジュールがまだクラスパスに存在しないことが原因です。追加してください。

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-dashscope</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

ID が `provider:model-name` の規則に従っているか再確認してください(例: `"openai:gpt-5.5"`、
`"dashscope:qwen-max"`、`"gemini:gemini-2.0-flash"`) — プロバイダープレフィックスのタイポは、
依存関係が存在していても同じエラーを引き起こします。
:::

  :::{dropdown} エージェントが何をしているかを確認する(推論とツール呼び出しをトレースする)には?
組み込みの `AgentTraceMiddleware` をアタッチしてください — `ReActAgent` と `HarnessAgent` の両方で動作し、
各推論ステップ、ツール呼び出し、結果が発生するたびにログを記録します。

```java
import io.agentscope.harness.agent.middleware.AgentTraceMiddleware;

ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .model("dashscope:qwen-plus")
                .middleware(new AgentTraceMiddleware())
                .build();
```

トレース以外に(あるいはトレースと並行して)独自のロジックを実装するには、`MiddlewareBase` を実装して
`onAgent` / `onReasoning` / `onActing` / `onModelCall` にフックしてください — [Middleware](../building-blocks/middleware.md) を参照してください。
:::

  :::{dropdown} Java 以外のエディションはありますか?
はい。AgentScope は3つの独立した言語エディションを、それぞれ独自のリポジトリで提供しています。

    - **Java** — [`agentscope-ai/agentscope-java`](https://github.com/agentscope-ai/agentscope-java)(このドキュメントサイト)
    - **Python** — [`agentscope-ai/agentscope`](https://github.com/agentscope-ai/agentscope)
    - **TypeScript** — [`agentscope-ai/agentscope-typescript`](https://github.com/agentscope-ai/agentscope-typescript)
:::
