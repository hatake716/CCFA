# Release status

CCFA は **v1.0.0** を正式リリースとして公開します。

## v1.0.0

v1.0.0 の配布用APKは、公開CCFAソースから GitHub Actions で完全に再現ビルドされます。
外部URLからの事前ビルドAPK取得には依存しません。

- Tag: `v1.0.0`
- Version: `1.0.0` (`versionCode` 19)
- APK: `CCFA-v1.0.0-debug.apk`
- Build workflow: `.github/workflows/publish-release.yml`
- Release: https://github.com/hatake716/CCFA/releases/tag/v1.0.0

### 公開手順

1. `app/build.gradle.kts` の `versionName` を `1.0.0` に設定する。
2. `main` にリリース用の変更をマージする。
3. `v1.0.0` タグを push する。
4. `Publish Release` ワークフローが次を自動実行する。
   - Termux 公式リポジトリから PRoot ランタイムを準備 (`scripts/prepare-termux-android-proot.sh`)
   - ライセンス・対応ソースを準備 (`scripts/prepare-distribution-legal.sh`)
   - `:app:assembleDebug` で APK をビルド
   - 配布用に APK をリネームし SHA-256 を計算
   - GitHub Release を作成し、公開済み APK を再ダウンロードして SHA-256 を検証

APK の SHA-256 はビルドごとに確定するため、公開後に Release ページで確認できます。

## 過去のリリース

- **v0.9.0** — 初回公開リリース。
  Release: https://github.com/hatake716/CCFA/releases/tag/v0.9.0
