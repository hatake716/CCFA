# CCFA privacy disclosure

Effective date: 2026-08-27
Application: CCFA (package `io.github.hatake716.ccfa`)
Published policy URL: https://hatake716.github.io/CCFA/privacy.html

CCFA itself does not operate an analytics, telemetry, advertising, account, or cloud-sync server. CCFA does not collect, transmit, sell, or share personal data with the developer or any third party.

## Data CCFA accesses locally

- App-private Linux container files and `/workspace` (both inside the app-private storage area, `Context.filesDir`).
- Local terminal input/output needed to operate the in-app PTY.
- Android folders that the user explicitly selects with the system folder picker (Storage Access Framework, `ACTION_OPEN_DOCUMENT_TREE`) in **ストレージ共有設定**. No Android folder is shared until the user selects one; the persisted folder permission can be revoked by the user at any time by disabling or removing the mapping.

Selected folders are not mounted into the Linux container. Instead, when the user runs a sync, their contents are mirror-copied between the selected Android folder and `/workspace/phone/<name>` inside the app-private area (bidirectional, import-only, or export-only, as configured). Programs inside the Linux container can read whatever has been copied into `/workspace` while the mapping is enabled.

CCFA does not scan unrelated Android storage. It holds no broad storage permission: the Google Play build requests only `INTERNET`.

## Network access performed by CCFA

CCFA uses network access only for Linux-environment setup started by the user: downloading the Linux Base image from its upstream provider (Canonical's `cdimage.ubuntu.com`) and installing ordinary Linux packages the user requests (for example via `apt`, which contacts the configured package mirrors directly).

CCFA does **not** automatically download, install, repair, log in to, or authenticate a proprietary third-party AI CLI. It does not obtain or proxy a third-party provider's OAuth token, API key, subscription credential, account entitlement, or rate limit.

## Third-party software installed by the user

If the user manually installs a third-party AI CLI or other networked program inside the CCFA Linux environment, that software may communicate directly with its own provider or other services.

Such traffic is governed by that third party's terms, privacy policy, account settings, data-retention choices, supported-region policy, and other applicable conditions. CCFA does not claim that this third-party traffic is processed by CCFA.

## Local credentials

Credentials created or stored by software that the user manually installs inside the Linux container remain inside the app-private Linux filesystem unless that software itself transmits, exports, or writes them elsewhere. CCFA does not intentionally inspect, upload, sell, or broker those credentials.

## Data deletion

- Removing a Linux container deletes that container rootfs.
- `/workspace` (including `/workspace/phone/` mirror copies) is app-private and is deleted when the app is uninstalled.
- Files in the Android folders the user selected for sharing live outside the app and remain under the user's control; CCFA only changes them when the user runs a sync whose direction writes to them.

## Children

CCFA does not collect personal data from any user, including children.

## Distribution channels

This policy describes the Google Play build (package `io.github.hatake716.ccfa`). The separately distributed sideload build (GitHub Releases, historical package `io.github.hatake716.claudecodeandroid`) differs in one respect: it uses Android's all-files-access permission and binds user-configured Android folders directly into the Linux container instead of SAF mirror sync. It performs no additional data collection.

## Changes and contact

Changes to this policy are published at the URL above with an updated effective date.

- Developer: hatake716 (individual developer)
- Contact: https://github.com/hatake716/CCFA/issues (or the developer contact address shown on the Google Play store listing)

## Distribution note

A distributor that modifies CCFA to add analytics, crash reporting, advertising, cloud synchronization, authentication, telemetry, credential brokering, or another network service must update this disclosure and any applicable store/privacy documentation before distribution.
