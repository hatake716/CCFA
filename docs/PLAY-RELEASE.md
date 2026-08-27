# Google Play リリース手順と残課題（google-play ブランチ）

このブランチは **CCFA を Google Play で公開するためのビルド構成** を持つ。
GitHub Releases 向けの debug 署名サイドロード版（`main` ブランチ）とは
別構成であり、両者を混同しないこと。

- 対象リポジトリ: `github.com/hatake716/CCFA`
- ブランチ: `google-play`
- 成果物: 署名済み **Android App Bundle (`.aab`)**

---

## main（sideload 版）からの差分

| 項目 | sideload 版 (`main`) | Play 版 (`google-play`) |
|---|---|---|
| targetSdk | 28 | **36** |
| 署名 | debug 鍵 | **アップロード鍵（Play App Signing）** |
| 成果物 | `CCFA-*-debug.apk` | **`app-release.aab`** |
| debuggable | true（debug ビルド） | **false** |
| 全ファイルアクセス | `MANAGE_EXTERNAL_STORAGE` | **撤去** |
| 旧ストレージ権限 | READ/WRITE_EXTERNAL_STORAGE + requestLegacyExternalStorage | **撤去（scoped storage）** |
| Android フォルダ共有 | 任意パスを PRoot へ常時 bind | **SAF 選択 + /workspace/phone/ と双方向ミラー同期** |

---

## 署名（Play App Signing）

新規アプリは AAB 必須で、Play App Signing への登録が必須。開発者は
**アップロード鍵** だけを保持し、Google が **アプリ署名鍵** を保持・再署名する。
アップロード鍵は漏洩時にリセットできるので、必ず git 外で管理する。

### アップロード鍵の作成

```bash
keytool -genkeypair -v \
  -keystore upload-keystore.jks -alias ccfa-upload \
  -keyalg RSA -keysize 2048 -validity 9125 -storetype JKS
```

### ローカルビルド

`keystore.properties.example` を `keystore.properties` にコピーして値を記入。
このファイルは `.gitignore` 済み。

```bash
cp keystore.properties.example keystore.properties
# 値を編集したうえで
gradle :app:bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

`keystore.properties` が無い場合でも `bundleRelease` は動作し、
**署名なしの .aab**（構成確認用）を生成する。

### CI ビルド（.github/workflows/play-release.yml）

次の GitHub secrets を設定して `Play Release Bundle` ワークフローを
`workflow_dispatch` で起動する:

| secret | 内容 |
|---|---|
| `CCFA_UPLOAD_KEYSTORE_BASE64` | `openssl base64 -A -in upload-keystore.jks` の出力 |
| `CCFA_UPLOAD_STORE_PASSWORD` | キーストアのパスワード |
| `CCFA_UPLOAD_KEY_ALIAS` | 鍵エイリアス（例 `ccfa-upload`） |
| `CCFA_UPLOAD_KEY_PASSWORD` | 鍵のパスワード |

`app/build.gradle.kts` の `signingValue()` は、まず `keystore.properties`、
次に環境変数（`CCFA_UPLOAD_*`）の順で署名情報を読む。

---

## ビルド検証状況（このブランチで確認済み）

ローカル（Gradle 9.5.0 / JDK 17 / Android SDK 36、ネイティブ .so はダミー）で:

- `:app:compileReleaseKotlin` → **BUILD SUCCESSFUL**（SAF 同期含む全 Kotlin がコンパイル可）
- `:app:bundleRelease` → **BUILD SUCCESSFUL**
  - `signReleaseBundle` 実行、`lintVitalRelease` 通過（debuggable 等の必須チェック合格）
  - `app-release.aab` に `base/lib/arm64-v8a/*.so` と `base/assets/legal/**` を含むことを確認

> 注: 上記はダミーの .so を使った構成検証。実バイナリでの動作確認と実機テストは
> 下記「残課題」で必須。

---

## 残課題（提出前に必ず対応）— 重要

このブランチはビルド構成を Play 準拠にしたが、**次の 2 点は
アーキテクチャに関わる本質的課題**であり、実機検証が必須。

### 1. W^X — targetSdk 36 での PRoot / rootfs バイナリ実行（最重要）

Android 10 (API 29)+ かつ targetSdk 29+ では、アプリのデータ領域
(`/data/data/<pkg>/`) に書き込んだファイルの直接 `execve()` が
SELinux ポリシーで拒否される（W^X）。CCFA は targetSdk を 28→36 に
上げるため、この制約に正面から当たる。

- **PRoot 本体**は `libproot.so` として APK の `nativeLibraryDir`
  (`lib/arm64-v8a/`) に置かれる。ここは exec 可能領域なので
  **PRoot の起動自体は W^X の影響を受けないと考えられる**。
- **問題は rootfs 内バイナリ**（`/bin/bash` 等、実行時に filesDir へ
  展開したもの）。これらは直接 exec できない。ただし PRoot は
  ptrace ベースのローダ経由でゲストを実行するため、Termux 系と同様に
  この制約を回避できる**可能性が高い**が、**未検証**。

**やること**:
- targetSdk 36 の実機（Android 15/16）で「初期Linux環境を作成」→
  PRoot セルフテスト → `/bin/bash` 起動 → `apt-get` まで通るか確認。
- 動かない場合は Termux の `system_linker_exec` 相当（`linker64` 経由で
  実行する PRoot 呼び出し）を導入する。`EmbeddedRuntimeManager` の
  `baseProotArgs` / ローダ設定の調整が要る。

参考: termux-packages wiki「Termux and Android 10」「Termux execution environment」、
developer.android.com/about/versions/10/behavior-changes-10。

### 2. 16 KB ページサイズ（ネイティブ .so）

targetSdk 35+ かつネイティブ .so 同梱アプリは、全 .so が
**16 KB ページアライン（LOAD セグメント align = 2\*\*14）** でないと
Play へのアップロードがブロックされる。

CCFA が同梱する `.so`（PRoot・libtalloc・libandroid-shmem・Termux
terminal-emulator）は **プリビルドのサードパーティバイナリ** であり、
Gradle のパッケージング設定では 16 KB 化できない。**バイナリ自体を
NDK r28+ / `-Wl,-z,max-page-size=16384` で再ビルドする必要がある**。

**確認コマンド**（各 .so が 16KB アラインか）:

```bash
llvm-readelf -l libproot.so | grep LOAD   # Align 列が 0x4000 (16384) なら OK、0x1000 (4096) なら NG
# AAB 全体:
bundletool dump config --bundle=app-release.aab | grep alignment  # PAGE_ALIGNMENT_16K を期待
```

`play-release.yml` の «Verify bundled .so are 16 KB page aligned» ステップが
この検証を自動で行い、未整合ならビルドを失敗させる。

**やること**: `scripts/prepare-termux-android-proot.sh` が取得する Termux
パッケージが 16KB アライン済みか確認し、未対応なら 16KB 対応版の取得元へ
切り替えるか、`build-proot-loader.sh` を NDK r28+ でビルドする。

### 3. ストレージ共有の SAF 移行（実装済み・要実機確認）

`MANAGE_EXTERNAL_STORAGE` を撤去し、`SafSyncManager` による SAF 双方向
ミラー同期へ移行済み（scoped storage 準拠）。

- ユーザーが `ACTION_OPEN_DOCUMENT_TREE` でフォルダを選ぶ
- URI を `takePersistableUriPermission` で恒久保持
- 「今すぐ同期」で `/workspace/phone/<名前>` と双方向コピー

**制約（sideload 版との違い）**: scoped storage では PRoot が
`/storage/emulated/0/...` を直接 open できないため、**常時マウント
（リアルタイム反映）は原理的に不可**。共有は「任意タイミングの手動
ミラー同期」になる。

**やること**: 実機で SAF 選択 → 同期 → Linux 側での読み書き →
再同期の往復を確認。大きいファイルのコピー時間・容量 2 倍を許容できるか
UX 確認。

---

## 提出時のストア掲載・ポリシー対応（別途必須）

ビルド以外に Play Console 側で必要なもの:

- **プライバシーポリシー**を公開 URL（HTML・非 PDF・非編集）で用意し、
  Play Console とアプリ内（法的情報画面）両方からリンク。`PRIVACY.md` を基に作成。
- **データ セーフティ フォーム**を提出（データ収集なしでも必須）。
- **ストア掲載物**に Anthropic / Claude / Ubuntu / Termux のロゴ・提携を
  匂わす表現を入れない（`TRADEMARKS.md` / `DISTRIBUTION.md` の方針を踏襲）。
- 初回起動時の**ネットワーク不通・DL 失敗をグレースフルに処理**し、
  審査端末で「空のターミナル／ハング」に見えないようにする。審査ノートに
  テスト手順を記載。
- **コード実行（PRoot / apt）** はデバイス/ネットワーク不正使用ポリシーの
  インタプリタ/VM 除外規定に収まる旨を説明できるようにし、DL はユーザー
  操作起点・明示同意であることを掲載文とデータ セーフティに反映。
