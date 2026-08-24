# CCFA privacy disclosure

CCFA itself does not operate an analytics, telemetry, advertising, account, or cloud-sync server.

## Data CCFA accesses locally

- App-private Linux container files and `/workspace`.
- Android storage folders explicitly enabled in **ストレージ共有設定** when the user grants the required storage access.
- By default those folders are Android `Download` and `Documents`.
- The user may disable those defaults or configure additional Android filesystem paths and Linux `/phone/...` mount points.
- Local terminal input/output needed to operate the in-app PTY.

CCFA does not automatically scan unrelated Android storage folders. A configured shared folder becomes visible to programs running inside the Linux container while that mapping is enabled and accessible.

## Network access performed by CCFA

CCFA uses network access for Linux-environment setup, including downloading the Linux Base image from its upstream provider and installing ordinary Linux packages requested by the user.

CCFA v1.0.0 does **not** automatically download, install, repair, log in to, or authenticate a proprietary third-party AI CLI. It does not obtain or proxy a third-party provider's OAuth token, API key, subscription credential, account entitlement, or rate limit.

## Third-party software installed by the user

If the user manually installs a third-party AI CLI or other networked program inside the CCFA Linux environment, that software may communicate directly with its own provider or other services.

Such traffic is governed by that third party's terms, privacy policy, account settings, data-retention choices, supported-region policy, and other applicable conditions. CCFA does not claim that this third-party traffic is processed by CCFA.

## Local credentials

Credentials created or stored by software that the user manually installs inside the Linux container remain inside the app-private Linux filesystem unless that software itself transmits, exports, or writes them elsewhere. CCFA does not intentionally inspect, upload, sell, or broker those credentials.

Removing a Linux container deletes that container rootfs. Shared `/workspace` and Android storage folders configured as bind mounts are separate and are not deleted by container deletion.

## Distribution note

A distributor that modifies CCFA to add analytics, crash reporting, advertising, cloud synchronization, authentication, telemetry, credential brokering, or another network service must update this disclosure and any applicable store/privacy documentation before distribution.
