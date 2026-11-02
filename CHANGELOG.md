# Changelog

## Unreleased
- v0.5.0: in-app "Check for updates" (Settings, Updates) with checksum and signing-key checks; signed GitHub Releases via tag-triggered workflow (docs/RELEASING.md, ADR 0007). New permissions INTERNET and REQUEST_INSTALL_PACKAGES, used only by that check.
- v0.4.0 production UI/UX (docs/UX.md): icon top bar, zoom buttons with honest lens / sensor crop / digital crop captions, shutter feedback, full-screen settings, camera-permission screen, app icon and name "KingShot". Sensor-crop detection and low-light wording fixes from the self-check bundles.
- Phase 3 decision (ADR 0006): no third-party RAW library; DNG stays the preserved source.
- Fix (found by the first self-check on a Pixel 7): switching RAW on while a photo was still in flight lost that photo; the session now rebuilds only when no capture is in flight. Self-check grades DNG only for RAW-on photos.
- One-tap self-check in Lab (scripted photos graded from receipts, SelfCheck tests); docs/DEVICE_CHECK.md replaces routine per-phase test requests.
- Fix: controls no longer sit under the status bar (system-bar insets).
- Phase 2 software side (gate open): receipts with real profile names, receipt details view, persistent receipt store (ADR 0005), PairNaming and preservation tests, RAW status line; docs/phase2/.
- Phase 1 complete in code (gate open): controls UI, Natural profile, thumbnail/recent photos, DataStore settings, OpenGate lifecycle tests; docs/phase1/.
- Phase 1 (in progress): Compose UI with ViewModel/StateFlow replaces the XML Lab screen (Lab features kept); compileSdk 37 required by Compose 1.12, targetSdk stays 36.
- Phase 1 logic with JVM tests: EvMath, LensMath (optical vs digital-crop stops), MeteringMath, LevelMath, Countdown, LowLightAdvisor. Lens ratio estimate checked against the Pixel 7 report (device min zoom 0.6704 vs computed 0.66).
- First device validation (Pixel 7, Android 17): fixtures/capabilities, docs/VALIDATION_LOG.md, ADR 0004; receipts and log now record postRawSensitivityBoost.
- Phase 0 Camera Lab rebuilt as packages (camera, capabilities, capture, raw, media, diagnostics, ui) with JVM tests.
- Capability report (JSON + text) for every camera and physical ID; Processing Receipts; diagnostics bundle via share sheet.
- Bounded timestamp matcher for JPEG/RAW; session support check with JPEG-only fallback.
- Removed media-delete code from the save path (failed saves leave the hidden pending item and are reported).
- Toolchain: AGP 9.4.0, Gradle 9.6.0, minSdk 34, ktlint in CI (ADR 0001, 0002).
- Memory and resume-to-first-frame logging; docs/benchmarks/.
- Phase 0 report (gate open, blocked on device results): docs/phase0/PHASE0_REPORT.md.
- Phase 0 test kit: docs/phase0/ (protocol, scoring CSV, report template, DV-001..004).
