# Claude Code for Android

Android から **Claude Code** を起動するための非公式ランチャーです。

Claude Code 本体を APK に同梱するのではなく、Android 上の [Termux](https://github.com/termux/termux-app) から [PRoot-Distro](https://github.com/termux/proot-distro) を使って Ubuntu 24.04 を起動し、その Linux 環境へ Anthropic 公式インストーラーで Claude Code を導入します。

## 現在の状態

**MVP / v0.1.0**

- root 不要
- Termux を実行基盤として利用
- Termux の `RUN_COMMAND` Intent で Android アプリからコマンドを起動
- Ubuntu 24.04 を PRoot-Distro で自動構築
- Claude Code は Anthropic 公式 `https://claude.ai/install.sh` からインストール
- Termux 側の `~/claude-projects` を Ubuntu 側の `/workspace` にマウント
- GitHub Actions で Debug APK をビルド

## アーキテクチャ

```text
Claude Code for Android APK
        │
        │ RUN_COMMAND Intent
        ▼
      Termux
        │
        ▼
   PRoot-Distro
        │
        ▼
 Ubuntu 24.04
        │
        ├── Git
        ├── ripgrep
        └── Claude Code (Linux ARM64 / x64)
```

詳細は [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) を参照してください。

## 必要環境

- Android 8.0 以上（minSdk 26）
- Termux
- インターネット接続
- Claude Code を利用できる Anthropic アカウント
- Ubuntu と Claude Code を保存できる空き容量

PRoot-Distro の公式ドキュメントでは Termux への導入に `pkg install proot-distro` が案内されています。このアプリのセットアップ処理が自動で実行します。

## 初回セットアップ

### 1. Termux をインストール

Termux の公式配布版をインストールして、一度起動します。

### 2. Termux で外部アプリ実行を許可

Termux のターミナルで以下を一度だけ実行します。

```bash
mkdir -p ~/.termux
touch ~/.termux/termux.properties
if grep -q '^allow-external-apps=' ~/.termux/termux.properties; then
  sed -i 's/^allow-external-apps=.*/allow-external-apps=true/' ~/.termux/termux.properties
else
  printf '\nallow-external-apps=true\n' >> ~/.termux/termux.properties
fi
termux-reload-settings
```

アプリ内の **「設定コマンドをコピー」** ボタンでも同じコマンドをクリップボードへコピーできます。

### 3. RUN_COMMAND 権限を許可

アプリの **「RUN_COMMAND 権限を許可」** を押します。

Android の権限画面では Termux が定義する追加権限 **Run commands in Termux environment** を許可します。

### 4. Linux + Claude Code をセットアップ

**「Linux + Claude Code をセットアップ」** を押します。

Termux に新しいターミナルセッションが開き、以下を自動実行します。

1. Termux パッケージ更新
2. `proot-distro`, `git`, `curl` をインストール
3. Ubuntu 24.04 を `claude-ubuntu` として作成
4. Ubuntu に `git`, `curl`, `ripgrep` 等を導入
5. Anthropic 公式インストーラーから Claude Code を導入
6. `claude --version` で確認

Ubuntu の取得があるため、初回は通信量とストレージを使用します。

### 5. Claude Code を起動

**「Claude Code を起動」** を押します。

Termux の `~/claude-projects` が Ubuntu の `/workspace` としてマウントされ、そこで Claude Code が起動します。

初回は Claude Code の認証が必要です。

## プロジェクトの保存場所

```text
Termux:  ~/claude-projects
Ubuntu:  /workspace
```

このディレクトリを Git リポジトリの作業場所として利用します。

## Android アプリをビルド

このプロジェクトは以下を基準にしています。

- Android Gradle Plugin 9.3.0
- Gradle 9.5.0
- JDK 17
- compileSdk 37
- targetSdk 37

Android Studio から開くか、Gradle 9.5.0 を用意して以下を実行します。

```bash
gradle :app:assembleDebug
```

生成先:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions の `Android CI` でも Debug APK を Artifact として生成します。

## セキュリティ上の考え方

このアプリは Termux のプライベート領域を直接読み書きしません。Termux が公開している `RUN_COMMAND` サービスを利用し、Android 権限 `com.termux.permission.RUN_COMMAND` と Termux 側の `allow-external-apps=true` の両方を必要とします。

セットアップスクリプトは APK の assets 内に含まれており、実行内容をリポジトリ上で確認できます。

## 制約

PRoot は VM や本物の chroot ではないため、以下には制約があります。

- systemd
- Docker daemon
- cgroups / Linux namespaces
- 一部の低レベル Linux 機能
- ネイティブ Linux と比較したファイル I/O 性能

通常の Git、シェル、Node/Python 系ツール、Claude Code の利用を主目的としています。

## 非公式プロジェクトについて

このリポジトリは Anthropic および Termux プロジェクトの公式製品ではありません。

**Claude Code 本体はこのリポジトリや APK に含めません。** セットアップ時に Anthropic の公式配布元から取得します。Claude Code の利用条件は Anthropic 側の条件に従います。
