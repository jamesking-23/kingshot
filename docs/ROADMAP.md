# Roadmap

Blueprint phases broken into ~1-day tasks. Estimates are not schedules. Each phase ends with a phase report and a stop for approval.
`[D]` = needs a Device Validation Request (owner runs it on the Pixel).

## Phase 0: Device feasibility
- P0-01 [done: ADR 0001; KGP 2.4 pairing deferred] Check current stable AGP/Kotlin/Gradle/SDK versions against official sources, update the version catalog; ADR 0001 (minSdk).
- P0-02 [ktlint done; detekt deferred, ADR 0002] Configure ktlint + detekt (justify each dependency), add to CI and CLAUDE.md Commands.
- P0-03 [done, unit-verified; adapter unverified on device] `capabilities` model + adapter + JSON serializer; unit tests on hand-written fixtures; capability report for **every** camera and physical ID (Appendix A schema, null + reason for gaps).
- P0-04 [done, unit-verified] Receipt and requested/reported/status types (`honored|differs|unavailable|not_requested`), plus tests.
- P0-05 [done, unit-verified; timestamp assumption pending DV-001] Bounded timestamp matcher (RAW and JPEG ↔ results) with timeout/orphan handling and exactly-once Image close; JVM tests; capture ID.
- P0-06 [done; device behavior pending DV-003] Session builder: `isSessionConfigurationSupported` / stream-combination check, JPEG-only fallback with user-visible reason.
- P0-07 [done; unverified on device] Diagnostics bundle (report JSON+text, capture logs, one JPEG, one DNG if supported, device/build info) via share sheet / FileProvider (needs your approval if FileProvider adds manifest entries; no new permission).
- P0-08 [done] Split the prototype into packages per ARCHITECTURE.md; architecture test; reword the "Minimal processing" label.
- P0-09 [done; fakes-based lifecycle tests not written yet] Lifecycle hardening (open races, reader close ordering, surface release) with fakes-based tests.
- P0-10 [done for Phase 0: shutter-to-saved, processing time, thermal, heap memory, resume-to-first-frame logged; process cold start and peak memory need instrumented benchmarks (Phase 1)] Latency/thermal/cold-start logging hooks.
- P0-11 [done] docs/phase0/PROTOCOL.md, scoring CSV template, REPORT.md template.
- P0-12 [done: docs/phase0/DEVICE_VALIDATION_REQUESTS.md] Write DV-001 (capability report + one shot, JPEG and DNG, timestamp assumption), DV-002 (NR/Edge OFF honored?), DV-003 (JPEG+RAW session support).
- **Gate:** [D] your bundles → VALIDATION_LOG.md, fixtures, ADR 0004, and I propose which claims are allowed (and the wording) and which are not.

## Phase 1: Core capture
P1-01 Compose shell + ViewModel/StateFlow · P1-02 Natural profile capture path · P1-03 lens discovery/selection with optical vs digital-crop labels, active physical ID from results · P1-04 timer · P1-05 EV compensation from characteristics · P1-06 flash where supported · P1-07 tap-to-focus/expose + indicator · P1-08 grid and level · P1-09 MediaStore save with confirmation, thumbnail, capture list · P1-10 low-light reporting from results · P1-11 settings + capture log · P1-12 lifecycle recovery tests · P1-13 interruption matrix (rotation, lock, call, permission revoke, backgrounding, camera in use, multi-window) [D] · P1-14 storage-exhaustion handling [D]. Gate + phase report.

## Phase 2: RAW and processing control
P2-01 DNG queue hardening and orientation/metadata · P2-02 validated JPEG+RAW path and fallback UI [D] · P2-03 profile definitions + DataStore persistence (Natural, Custom) · P2-04 ADR 0005 and receipt/pairing store · P2-05 receipts shown in capture details · P2-06 DNG opens in an independent viewer [D] · P2-07 no-overwrite and pair reconciliation tests. Gate + phase report.

## Phase 3 (after your RAW-library decision)
ADR 0006 candidate comparison (you choose; no copyleft without approval) → render pipeline writing new paired files → compare view → HDR/denoise experiments behind flags → Computational profile (extension ADR) → benchmarks → repeated-trial protocol on agreed thresholds.

## Phase 4
Manual controls, histogram, focus peaking, bracketing, portrait depth, video modes, provenance manifest and processing recipe; each capability-gated and device-validated.

## Release readiness (when you ask)
Name clearance (Kingshot is an existing well-known mobile game), package/Play Store conflicts, privacy policy matching the no-network design, signed release + shrinker rules, crash-free thresholds you approve, store text per claims discipline.

## Unscheduled (feature matrix "Later", no phase named): burst, intervalometer, presets, non-destructive edits, multi-frame Night product mode, tripod/astro workflow. Ideas go in docs/IDEAS.md.
