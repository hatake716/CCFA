# Architecture

## Goal

Claude Code の Linux バイナリを Android ネイティブへ再実装せず、Android 上で通常の Linux ユーザーランドを用意して実行することを目的とします。

## Components

### Android launcher

`MainActivity` は以下だけを担当します。

- Termux のインストール状態確認
- `com.termux.permission.RUN_COMMAND` の要求
- Termux 初期設定の案内
- APK assets に格納したシェルスクリプトの読み込み
- Termux `RunCommandService` への送信

Claude Code の認証情報やプロジェクトファイルを Android アプリ自身が読み取る設計にはしていません。

### Termux

Termux は Android と Linux ユーザーランドの橋渡しです。

第三者アプリからのコマンド実行には、Termux が提供する以下を利用します。

- service: `com.termux.app.RunCommandService`
- action: `com.termux.RUN_COMMAND`
- permission: `com.termux.permission.RUN_COMMAND`

さらに Termux 側で `allow-external-apps=true` をユーザー自身が明示的に設定する必要があります。

### PRoot-Distro

Ubuntu 24.04 を `claude-ubuntu` という名前で作成します。

```text
Termux
└── PRoot-Distro
    └── claude-ubuntu (Ubuntu 24.04)
```

PRoot は root 権限を要求せずに chroot に近い Linux ファイルシステム環境を提供します。ただし Linux namespaces、cgroups、systemd 等を完全には提供しません。

### Claude Code

Claude Code 自体は APK に含めません。

Ubuntu 内で Anthropic 公式インストーラーを実行します。

```bash
curl -fsSL https://claude.ai/install.sh | bash
```

これにより、Android ランチャーと Claude Code の配布を分離します。

## Workspace

Termux 側:

```text
~/claude-projects
```

Ubuntu 側:

```text
/workspace
```

起動時に PRoot-Distro の `--bind` で接続します。

## Security boundary

```text
Android app
    │
    │ Android dangerous permission
    │ com.termux.permission.RUN_COMMAND
    ▼
Termux RunCommandService
    │
    │ Termux property
    │ allow-external-apps=true
    ▼
Termux shell
    │
    ▼
PRoot Ubuntu
```

Android 権限と Termux 設定の二段階を通過しない限り、ランチャーから Termux コマンドを実行できない設計です。

## Future work

- コマンド結果を PendingIntent でアプリへ返す
- セットアップ完了状態の自動検出
- プロジェクト一覧 UI
- Git status / diff UI
- Android Storage Access Framework と workspace の連携
- Claude Code セッション管理
- タブレット / DeX 向けレイアウト
- Release APK の署名・配布フロー
