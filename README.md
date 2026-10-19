# KingShot

Android camera app, Pixel first. A transparent, capability-aware interface for requesting minimal
processing, capturing RAW where supported, and optional rendering later. Product intent lives in
[docs/BLUEPRINT.md](docs/BLUEPRINT.md); progress in [docs/REQUIREMENTS_TRACE.md](docs/REQUIREMENTS_TRACE.md).

**Status: Phase 0 (device feasibility), not yet started on a device.** The code in `app/` is an
early Camera Lab prototype that has only been compiled by CI. Nothing in it is verified on a Pixel,
and no "minimal processing" claim is made until Phase 0 evidence exists.
The application ID `com.kingshot.lab` and the name are placeholders pending a name check (see docs/ROADMAP.md).

## Build
Requires JDK 17 and the Android SDK (platform 34). Android Studio works as is.

```
./gradlew lintDebug testDebugUnitTest assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`. CI (`.github/workflows/build-apk.yml`) runs the same
tasks plus a check that the merged manifest has no INTERNET permission, and uploads the APK as the
`KingShot-debug-apk` artifact.

## Docs
`docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `docs/TEST_PLAN.md`, `docs/PROTOTYPE_AUDIT.md`, `docs/decisions/` (ADRs).
