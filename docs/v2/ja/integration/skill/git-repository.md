---
title: Git Skill Repository
---

`agentscope-extensions-skill-git-repository` は、リモートの Git リポジトリを skill リポジトリとして扱います。読み取りのたびに軽量なリモート ref チェックを実行し、リモート HEAD が変化したときだけ pull します — アイドル時のオーバーヘッドはほぼゼロです。

## 使いどころ

- skill コンテンツのバージョン管理とレビューを Git に任せたい。
- 単一の skill セットを複数のプロジェクトで共有したい。
- 本番環境にデータベースや設定センターを組み込みたくない。

## 依存関係の追加

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-skill-git-repository</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

内部では JGit を使用しており、HTTPS と SSH の両方をサポートします。

## クイックスタート

```java
import io.agentscope.core.skill.repository.GitSkillRepository;
import io.agentscope.core.skill.AgentSkill;

// パブリックリポジトリ + デフォルトブランチ、一時ローカルディレクトリ
GitSkillRepository repo = new GitSkillRepository(
    "https://github.com/agentscope/skills.git"
);

// すべての skill を Toolkit に登録
Toolkit toolkit = new Toolkit();
repo.getAllSkills().forEach(toolkit::registerSkill);

// シャットダウン時に一時ディレクトリをクリーンアップ
Runtime.getRuntime().addShutdownHook(new Thread(repo::close));
```

## ブランチの固定 / 固定ローカルパスの使用

```java
GitSkillRepository repo = new GitSkillRepository(
    "https://github.com/agentscope/skills.git",
    "develop",                   // ブランチ
    Path.of("/var/skills/repo"), // ローカルパス（null = 一時ディレクトリ）
    "agentscope-public",         // ソースラベル（Toolkit 上で可視）
    true                         // autoSync
);
```

## プライベートリポジトリの認証

`GitSkillRepository` はシステムレベルの Git 設定を再利用します。Java 側で資格情報を管理することはありません。

- **HTTPS**: `~/.gitconfig` の資格情報ヘルパー（osxkeychain、libsecret など）を使用します。
- **SSH**: `~/.ssh/` 配下のキーと `ssh-agent` を使用します。

```java
// プライベート SSH リポジトリ
GitSkillRepository repo = new GitSkillRepository(
    "git@github.com:my-org/private-skills.git"
);
```

CI では、実行ユーザーが資格情報を持っているか、SSH エージェントが設定されていることを確認してください。

## 自動同期 vs. 手動同期

- `autoSync=true`（デフォルト）: 読み取りのたびにまず `ls-remote` を実行し、リモートが移動している場合のみ pull します。
- `autoSync=false`: 自動では pull せず、更新するには `repo.sync()` を呼び出します。

```java
GitSkillRepository repo = new GitSkillRepository(remoteUrl, false);
repo.sync();              // 起動時に一度同期
schedule(() -> repo.sync(), 5, TimeUnit.MINUTES);
```

## 運用上の注意

- リポジトリは Spring Bean のシングルトンとして保持し、シャットダウン時に一度だけクローズしてください。
- 一時ディレクトリには JVM シャットダウンフックが設定されていますが、強制終了されたプロセスは残骸を残す場合があります — 必要に応じて外部でクリーンアップしてください。
- マルチインスタンスのデプロイでは、それぞれが独自のクローンを保持するため、ロックの競合はありません。
