# Changelog

## Unreleased
- Phase 0 Camera Lab rebuilt as packages (camera, capabilities, capture, raw, media, diagnostics, ui) with JVM tests.
- Capability report (JSON + text) for every camera and physical ID; Processing Receipts; diagnostics bundle via share sheet.
- Bounded timestamp matcher for JPEG/RAW; session support check with JPEG-only fallback.
- Removed media-delete code from the save path (failed saves leave the hidden pending item and are reported).
- Toolchain: AGP 9.4.0, Gradle 9.6.0, minSdk 34, ktlint in CI (ADR 0001, 0002).
- Memory and resume-to-first-frame logging; docs/benchmarks/.
- Phase 0 test kit: docs/phase0/ (protocol, scoring CSV, report template, DV-001..004).
