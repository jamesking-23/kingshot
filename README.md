# KingShot

Android camera app, Pixel first. A transparent, capability-aware interface for requesting minimal
processing, capturing RAW where supported, and optional rendering later. Product intent lives in
[docs/BLUEPRINT.md](docs/BLUEPRINT.md); progress in [docs/REQUIREMENTS_TRACE.md](docs/REQUIREMENTS_TRACE.md).

**Status: Phase 0 (device feasibility): code and test kit are ready, waiting for the first run on a Pixel.**
The app is a Camera Lab: capability report for every camera, JPEG and optional RAW+DNG capture, capability-checked
noise-reduction/edge OFF *requests*, per-capture Processing Receipts, and an exportable diagnostics bundle.
CI proves it compiles, lints and passes its JVM unit tests; nothing is verified on a device, and no "minimal processing"
claim is made until the Phase 0 results exist (docs/phase0/).
Targets Android 14+ (minSdk 34) on any phone; behavior comes from runtime capabilities, never from the model name.
The name and application ID `com.kingshot.camera` are placeholders pending a name check (docs/ROADMAP.md).

## Build
Requires JDK 17 and the Android SDK (platform 34). Android Studio works as is.

```
./gradlew ktlintCheck lintDebug testDebugUnitTest assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`. CI (`.github/workflows/build-apk.yml`) runs the same
tasks plus a check that the merged manifest has no INTERNET permission, and uploads the APK as the
`KingShot-debug-apk` artifact.

## Docs
`docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `docs/TEST_PLAN.md`, `docs/PROTOTYPE_AUDIT.md`, `docs/decisions/` (ADRs),
`docs/phase0/` (comparison protocol, DV requests), `docs/VALIDATION_LOG.md`.
