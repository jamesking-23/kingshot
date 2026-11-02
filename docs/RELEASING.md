# Releasing

1. One-time: in GitHub, open the repo, Settings, Secrets and variables, Actions, New repository secret. Name `KS_PASSWORD`, value = the signing password the owner was given. Nothing else is needed (the keystore file is `release/kingshot.p12`).
2. Bump `versionCode` and `versionName` in `app/build.gradle.kts`, update `CHANGELOG.md`, push to `main` and wait for CI to pass.
3. Tag and push: `git tag v<versionName>` then `git push origin v<versionName>`. The Release workflow checks the tag matches `versionName`, builds and signs the APK, verifies the signature, and publishes `KingShot-<version>.apk` and `.sha256` as a GitHub Release.
4. In the app: Settings, Updates, Check for updates.

Notes
- Without `KS_PASSWORD` the workflow stops with a clear error; it never publishes an unsigned or debug-signed release.
- Updates install only over builds signed with the same key. The first signed release replaces a debug-signed CI build only after a one-time uninstall.
- Keep the password safe. It is not recoverable from the repository.
