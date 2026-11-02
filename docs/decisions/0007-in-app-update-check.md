# 0007: In-app update check (network use)

- Status: accepted, requested by the owner on 2026-11-02
- Trace rows: P6 (privacy)

## Context
Until now the app had no INTERNET permission. Updating meant downloading an APK from GitHub by hand. The owner asked for a "Check for updates" action in Settings that fetches updates directly. CLAUDE.md required approval for a permission and a network call; this request is that approval, limited to this feature.

## Decision
- Permissions added: INTERNET (to ask GitHub for the latest release and download it) and REQUEST_INSTALL_PACKAGES (so Android lets the app hand the downloaded APK to the system installer). No other permission.
- Network use happens only after a user tap (Check for updates, then Download and install). No background checks, no analytics, no identifiers beyond a User-Agent carrying the app version.
- Only HTTPS to GitHub hosts (`api.github.com`, `github.com`, `*.githubusercontent.com`); redirects are followed by hand and every hop is re-checked. Sizes are capped.
- A release must carry `KingShot-<version>.apk` and `KingShot-<version>.apk.sha256`. The app refuses the update unless the SHA-256 matches, the package name is the same, the version is newer, and the APK is signed with the same key as the installed app. Android then shows its own confirmation screen; the app never installs silently.
- Network code lives only in the `update` package; an architecture test fails if network APIs appear anywhere else or if the client is used outside the ViewModel. CI fails if the merged manifest declares any permission outside CAMERA, INTERNET, REQUEST_INSTALL_PACKAGES.
- Releases are built by `.github/workflows/release.yml` on a version tag and signed with one project key (`release/kingshot.p12`, a password-protected PKCS12 file). Its 40-character random password lives only in the repository secret `KS_PASSWORD` and with the owner; it is never committed.
- The app deletes files only inside its own cache folder (`cache/updates`), never user media.

## Consequences
- The privacy statement changes from "no internet access" to "internet only for the update check you start".
- Debug builds from CI are signed with a throwaway key and cannot be updated in place; only signed releases can. The first release needs a one-time uninstall of an earlier debug build (photos in the gallery are not removed).
- If the key password leaks or the key is lost, installs cannot be updated; the fix is a new key and a one-time reinstall.
- Compatibility with the checksum scheme is tested on a JVM; the download, redirect handling and installer hand-off are unverified until run on a device.
