# Camera Lab prototype audit (Step 0)

Subject: `app/src/main/java/com/kingshot/lab/MainActivity.kt` and `app/src/main/res/layout/activity_main.xml`
(the prototype rebuilt from blueprint section 8, because no Android Studio project was attached).

**Verification status:** CI has compiled and assembled it. It has not been linted, unit-tested, installed or run on any device.
Severity: **H** breaks a non-negotiable or a blueprint caveat, **M** correctness or robustness, **L** cleanup.

## Status after the Phase 0 rewrite (2026-10-20, CI run 39354771975)
The original prototype was replaced by the package structure in docs/ARCHITECTURE.md. Item by item (none verified on a device):
| # | Status |
|---|---|
| 1 | addressed: `isSessionConfigurationSupported` check, then JPEG-only fallback with a reason; device behavior pending DV-003 |
| 2, 4 | addressed: bounded `TimestampMatcher` for JPEG and RAW, orphan handling, exactly-once close; unit-tested |
| 3 | **open**, DV-001 |
| 5 | addressed: the delete call was removed. A failed save leaves the hidden pending MediaStore item and reports the failure; nothing is deleted (owner delegated the choice; reversible later) |
| 6 | addressed: labels now say "Request ... OFF (only if listed)" and a disclosure line is shown |
| 7 | partly: receipts are built per capture and exported in the bundle; persistence is Phase 2 |
| 8 | addressed for naming (shared capture ID stem); pairing store is Phase 2 |
| 9, 10, 11 | addressed: status vocabulary, full report for every camera and physical ID (JSON + text), capability model |
| 12 | addressed: `acquireNextImage` guarded, `maxImages` 3, at most 2 captures in flight |
| 13, 14, 17, 18 | addressed by the thread-confined `CameraHost` state machine; unverified on device |
| 15 | partly: orientation math is unit-tested; landscape stays untested (screen is portrait-locked) |
| 16 | partly: top-level rear BACKWARD_COMPATIBLE camera is chosen, active physical ID is read from results; assumption pending DV-001 |
| 19 | addressed (capture ID names; MediaStore never overwrites) |
| 20 | partly: shutter-to-saved, processing time, thermal status, heap memory and resume-to-first-frame are logged (docs/benchmarks/); process cold start and peak memory are not |
| 21, 22, 23 | addressed: packages + architecture test; ADR 0001 (minSdk 34); applicationId `com.kingshot.camera` (placeholder) |
| 24 | open: release build is still debug-signed (Lab only) |
| 25 | partly: ktlint, Android lint and JVM tests exist; detekt deferred (ADR 0002); no instrumented tests yet |

The original findings are kept below for history.

## Against the blueprint section 8 caveats
| # | Sev | Defect / risk | Where | Planned fix |
|---|-----|---------------|-------|-------------|
| 1 | H | JPEG+RAW session is created with no `isSessionConfigurationSupported` or stream-combination check. It only falls back after `onConfigureFailed`. | `createSession` | P0-06: check support first, then fall back and tell the user why |
| 2 | H | RAW Image to `TotalCaptureResult` matching uses two unbounded maps keyed by timestamp. There is no timeout and no orphan handling. A RAW image whose result never arrives is never closed (leaks until `closeCamera`), and the reverse leaves a stale result. | `pendingRaw`, `pendingResults`, `tryWriteDng` | P0-05: bounded matcher in a pure Kotlin class, JVM-tested |
| 3 | M | Assumes `Image.timestamp == SENSOR_TIMESTAMP`. Unverified on device. | `onRawAvailable`, `onCaptureCompleted` | DV-001 |
| 4 | M | The JPEG is never matched to its result at all. | `onJpegAvailable` | P0-05 |

## Against the non-negotiables
| # | Sev | Defect | Where | Planned fix |
|---|-----|--------|-------|-------------|
| 5 | H | **Code that can delete media:** a failed save calls `resolver.delete(uri, ...)` to remove the partial MediaStore row the app itself just created. CLAUDE.md requires asking first. | `saveToMediaStore` | **Needs your decision** (open question Q5). Until then I will not extend it. |
| 6 | H | The checkbox says "Minimal processing", which is a claim the Phase 0 gate forbids before evidence. | `activity_main.xml` | Reword to "Request NR/Edge OFF (if listed)" |
| 7 | H | No Processing Receipt, no capture ID, and no "JPEG may still be processed by the device" disclosure in the UI. | whole app | P0-04 and Phase 2 |
| 8 | H | JPEG and DNG from one shot are not paired. Each gets its own `Date()` call, so filenames can differ by a second, and there is no shared capture ID. | `onJpegAvailable`, `tryWriteDng` | capture ID in P0-05; pairing store Phase 2 |
| 9 | M | Requested-against-returned logging exists, but it is free text with no honored / differs / unavailable / not_requested status. | `capture` callback | P0-04 |
| 10 | M | Capability report covers only the first rear ID, as unstructured text. No physical IDs per camera, no key names, no stream combinations, no extensions, no JSON, no export bundle. | `logCapabilityReport` | P0-03 and P0-07 |
| 11 | L | Controls are shown whenever the layout has them, and the capability check lives in the Activity instead of a registry model. | `MainActivity` | capabilities module (P0-03) |

## Robustness and lifecycle
| # | Sev | Defect | Where |
|---|-----|--------|-------|
| 12 | M | `acquireNextImage()` is outside any try block in both reader callbacks; `IllegalStateException` when `maxImages` is reached would crash the camera thread. Rapid shutter taps are not bounded. | `onJpegAvailable`, `onRawAvailable` |
| 13 | M | `openCamera()` is asynchronous but `cameraDevice` is set only in `onOpened`, so two quick `onResume` calls can open twice. `onPause` during an open leaks the device. | `openCamera`, `closeCamera` |
| 14 | M | `closeCamera()` closes the ImageReaders while the camera thread may still be inside their listeners. | `closeCamera` |
| 15 | M | Orientation is hard-coded: DNG always `ROTATE_90`, JPEG orientation equals sensor orientation. This only holds for the portrait-locked phone case, and no rotation handling exists. | `capture`, `tryWriteDng` |
| 16 | M | The first back-facing ID in list order is assumed to be the main camera. The active physical camera is never read from results. | `openCamera` |
| 17 | L | `Surface(st)` for the preview is never released. | `createSession` |
| 18 | L | Toggling RAW inside `onConfigureFailed` re-enters `createSession` through the checkbox listener. It works but is fragile. | `createSession` |
| 19 | L | Filenames use second resolution plus `timestamp % 1000`. MediaStore renames on collision, so nothing is overwritten, but names are not stable identifiers. | `onJpegAvailable` |
| 20 | L | No shutter-latency, processing-time, thermal or memory measurement. | whole app |

## Structure and build
| # | Sev | Note |
|---|-----|------|
| 21 | M | One 500-line Activity touches CameraDevice, storage and UI. It does not match the eight-module contract and has no testable pure logic. Split per docs/ARCHITECTURE.md. |
| 22 | L | `minSdk 29` and XML UI differ from the prompt's defaults (API 31, Compose). The minimum SDK needs an ADR, and Compose arrives after Phase 0. |
| 23 | L | `applicationId com.kingshot.lab` is not the default `com.kingshot.camera`. Both are placeholders pending the name check. |
| 24 | L | The `release` build type is signed with the debug key. Acceptable for a lab build only. Remove before any release. |
| 25 | L | No ktlint or detekt, no unit tests, no instrumented tests. |

## What is fine
- Only the CAMERA permission is declared. The merged manifest is now checked in CI for INTERNET.
- NR OFF and Edge OFF are requested only when listed in the available modes, and returned values are logged.
- RAW is offered only when the capability and a RAW_SENSOR output size both exist.
- No model-name branching.
