# CCFA — 日本語入力対応Linuxコンテナ

**CCFA** は、Android上でLinuxコンテナとPTYターミナルを1つのアプリとして利用するためのオープンソースアプリです。

外部のTermuxアプリやroot権限を必要とせず、アプリ内にLinux実行環境を構築します。Androidの日本語IMEでかな漢字変換した文字列をLinuxターミナルへ送信できることを重視しています。

- Version: **v0.9.0**
- 対応CPU: **ARM64 / arm64-v8a**
- Android: **8.0 (API 26) 以降**
- License: **Apache License 2.0**（同梱する第三者コンポーネントには各ライセンスが適用されます）

## CCFAでできること

### AndroidだけでLinux環境を構築

CCFAはアプリ内に以下を組み込みます。

- Android/Bionic向けPRootランタイム
- アプリ内PTYターミナル
- Linux rootfsコンテナ
- `/workspace` 作業領域
- 複数Linuxコンテナの作成・切替・削除

初回セットアップ時にLinux Baseを公式配布元から端末へ取得し、アプリ専用領域へ展開します。Linux Base自体はAPKには同梱していません。

### 日本語IME対応ターミナル

ターミナル最下部にAndroid標準の入力欄を用意しています。Gboardなどで通常どおりかな漢字変換した後、その文字列をUTF-8でPTYへ送信できます。

また、次のPC向け補助キーを画面上から利用できます。

```text
ESC  CTRL  ALT  TAB  ↑  HOME  END
PGUP ←    ↓    →    PGDN BKSP ENTER
```

ターミナル文字サイズは `A− / A+` またはピンチ操作で変更でき、設定は保存されます。

### スマートフォンストレージとの共有

Android側フォルダをLinuxコンテナ内へマウントできます。

初期設定は次のとおりです。

```text
Android Download   → /phone/Downloads
Android Documents  → /phone/Documents
```

アプリの **「スマートフォンストレージ」→「共有設定を編集」** から、以下を変更できます。

- 共有ごとのON / OFF
- Android側フォルダのパス
- Linux側 `/phone/` 以下のマウント先
- 表示名
- 共有設定の追加・削除
- 読み書き確認
- 初期設定への復元

最大8件まで登録できます。変更内容は次回ターミナル起動時から反映されます。

> 共有ストレージはAndroidとのファイル交換用途に適しています。実行ファイルやLinux開発環境本体は `/workspace` の利用を推奨します。

### 複数Linuxコンテナ

Linux環境を複数作成して使い分けられます。

例えば、開発用・検証用・AIツール用など用途ごとにrootfsを分離できます。コンテナを削除しても共有 `/workspace` やAndroid側の共有フォルダは削除されません。

## インストール方法

### 1. APKをダウンロード

GitHubの **Releases** から最新のCCFA APKをダウンロードします。

- Releases: `https://github.com/hatake716/CCFA/releases`
- 推奨バージョン: **v0.9.0**

### 2. AndroidでAPKのインストールを許可

ブラウザやファイルマネージャーからAPKを開き、必要な場合はAndroid設定で「この提供元のアプリを許可」を有効にしてインストールします。

### 3. CCFAを起動

アプリを起動したら、必要に応じてストレージ権限を設定します。

### 4. 初期Linux環境を作成

**「初回セットアップ」→「初期Linux環境を作成」** を実行します。

処理内容はおおむね次の順です。

```text
PRootランタイム確認
      ↓
Linux Baseダウンロード
      ↓
rootfs展開
      ↓
PRoot起動テスト
      ↓
基本CLIセットアップ
```

初回のみLinux Baseのダウンロードと展開に時間がかかります。

### 5. ターミナルを開く

セットアップ完了後、**「エージェントターミナルを開く」** からLinuxシェルを利用できます。

## 応用編 — AIエージェントを利用する

CCFAは特定のAIサービス専用クライアントではありません。Linux上で動作するCLI型AIエージェントを利用するための土台として使うことができます。

CCFA自身は第三者AIエージェントをAPKへ同梱せず、自動インストール・自動ログイン・認証情報の代理取得も行いません。

AIエージェントを使う場合は、次の流れを推奨します。

1. CCFAでLinux環境を作成する
2. 「エージェントターミナルを開く」からLinuxシェルを開く
3. 利用したいAIエージェントの**公式サイト・公式ドキュメントを確認する**
4. その提供元が案内しているLinux向けインストール手順に従う
5. 認証やログインも提供元の公式手順に従う
6. `/workspace` でプロジェクトを開き、AIエージェントを起動する
7. 日本語入力は画面最下部のCCFA入力欄から行う

各AIエージェントは更新頻度が高く、インストール方法や認証方法が変更される可能性があります。そのためREADMEに固定のインストールコマンドを転載せず、**常に各提供元の最新公式手順を確認してください。**

## Gitについて

CCFAにはAndroid側の専用Git GUIはありません。

Linux環境内には一般的な開発ツールとして `git` を導入できます。必要なGit操作はLinuxシェル、または利用者が導入した開発ツール・AIエージェントから行います。

## アーキテクチャ

```text
Android / CCFA APK
        │
        ├─ Android UI + 日本語IME入力欄
        ├─ TerminalView / TerminalSession
        ├─ /dev/ptmx
        │
        ▼
Android/Bionic PRoot
        │
        ▼
Linux rootfs
        ├─ /workspace
        └─ /phone/*  ← ユーザー設定可能
```

詳しくは [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) を参照してください。

## ソースからビルド

主な必要環境:

- JDK 17
- Gradle 9.5.0
- Android SDK 36
- Android Gradle Plugin 9.3.0

配布用ビルドでは、まずAndroid/Bionic PRootとライセンス資料を準備します。

```bash
bash scripts/prepare-termux-android-proot.sh
bash scripts/prepare-distribution-legal.sh
gradle :app:assembleDebug
```

現在の実装はapp-private領域のLinux userlandをPRootで実行するため、`compileSdk 36 / targetSdk 28 / minSdk 26` を使用しています。

## ライセンスと第三者コンポーネント

CCFA本体は **Apache License 2.0** で公開しています。

主な第三者コンポーネント:

- PRoot
- libandroid-shmem
- libtalloc
- Termux terminal-view / terminal-emulator
- Apache Commons Compress / Codec / IO / Lang

配布APKには必要なライセンス本文・NOTICE・GPL/LGPL対象コンポーネントの対応ソースを `assets/legal/` に同梱します。

詳細:

- [`LICENSE`](LICENSE)
- [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)
- [`TRADEMARKS.md`](TRADEMARKS.md)
- [`PRIVACY.md`](PRIVACY.md)
- [`docs/DISTRIBUTION.md`](docs/DISTRIBUTION.md)
- [`docs/PROOT-SOURCE-OFFER.md`](docs/PROOT-SOURCE-OFFER.md)

## 非提携について

CCFAは独立したオープンソースプロジェクトです。Anthropic、Canonical、Termux、Apache Software Foundation、Samba Teamその他の第三者の公式製品ではなく、承認・提携・後援を受けた製品でもありません。第三者の名称・商標は各権利者に帰属します。
