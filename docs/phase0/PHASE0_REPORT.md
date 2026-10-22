# Phase 0 report: device feasibility

**Status: build and test kit complete. Gate OPEN: blocked on device results from the owner's Pixel.**
Date: 2026-10-22. Build: commit `f0fd7d9` (CI run 39388493642, green). No Device Validation Request has been run, so `docs/VALIDATION_LOG.md` is empty.

## What is done (with evidence)
| Item | Evidence |
|---|---|
| Camera Lab: permission, discovery of every camera and physical ID, live preview | code in `app/`; compiles and lints in CI; not run on a device |
| Capability report (JSON + text, null-with-reason) | `capabilities/`, `diagnostics/CapabilityReport.kt`; `DiagnosticsTest` |
| Capability-checked NR/Edge OFF requests; requested vs reported vs status | `StillRequestPlannerTest` |
| JPEG capture and optional RAW+DNG, session support check, JPEG-only fallback | `camera/CameraHost.kt`; unverified on device |
| Bounded timestamp matcher, orphan handling, exactly-once image close | `TimestampMatcherTest` |
| Processing Receipts, capture log, exportable bundle via share sheet | `ReceiptAssembler`, `BundleExporter`; receipts unit-tested, export unverified on device |
| Latency, thermal, memory, resume-to-first-frame logging | receipts + log; `docs/benchmarks/README.md` |
| Guards: INTERNET gate, architecture test, ktlint, Android lint | CI run 39388493642 |
| Test kit: protocol, scoring CSV, report template, DV-001..004 | `docs/phase0/` |

## Gaps
- **Device facts: all missing.** No result exists for DV-001 (timestamp equality, main-camera assumption), DV-002 (are NR/Edge OFF honored), DV-003 (JPEG+RAW together), DV-004 (DNG opens elsewhere), or the stock-vs-KingShot comparison.
- No fakes-based lifecycle tests, no instrumented tests, no peak-memory or process cold-start benchmark (planned for Phase 1).
- detekt deferred (ADR 0002). Release build still debug-signed (Lab only).

## Claims allowed today
Only statements about the design, each backed by a test or the code, never about device behavior:
- "KingShot requests noise-reduction OFF and edge OFF only when the camera lists them as available."
- "KingShot shows what was requested, what the device reported, and whether they matched."
- "The app has no network permission."

## Claims NOT allowed until the DV results exist
"minimal processing", "unprocessed", "RAW works on <device>", "JPEG+RAW capture", "DNG opens in <tool>", any statement that NR/Edge OFF is honored, any comparison with the stock camera, any model-specific feature.

## Proposed wording once results arrive (decide from the receipts; do not use before)
| Outcome | Wording allowed |
|---|---|
| Receipt `honored` for NR and Edge, repeatedly | "On <device>, KingShot requested noise reduction OFF and edge OFF and the device reported OFF." (still not "unprocessed") |
| `differs` | "KingShot requested OFF; this device reported <mode>." |
| `unavailable` | "This camera does not list OFF for <control>, so KingShot did not request it." |
| JPEG+RAW session works | "Captures a JPEG and a DNG together on <device>." Otherwise "JPEG only on this device: <reason>." |
| DNG opens and looks right | "DNG opens in <tool + version> on <computer>." |

## Risks carried forward
1. The device may ignore NR/Edge OFF; evidence decides whether Natural can claim anything (ADR 0004).
2. `Image.timestamp` may not equal `SENSOR_TIMESTAMP`; matching would need redesign.
3. JPEG+RAW may be unsupported together; RAW-only mode would then be a scope question for the owner.
4. The main-camera choice is an assumption; other lenses and non-Pixel phones are unverified.
5. Name collision: "Kingshot" is an existing mobile game; clearance is needed before public use.

## Proposed next milestone (from the blueprint's closing rule, subject to the data)
After DV-001..003 results: ADR 0004 (NR MINIMAL fallback decision from evidence), fix whatever the DVs break, then Phase 1 (core capture: Compose shell, Natural profile, lens selection, timer, EV, tap-to-focus, saving, lifecycle recovery) on your approval.

## Approval
Phase 1 does not start until the owner approves this report after the device results are in.
