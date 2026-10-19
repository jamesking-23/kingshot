# Architecture

Pipeline (blueprint §5): UI → Capture Orchestrator → Camera2 capability registry → Camera session → JPEG/RAW readers → storage and metadata → optional render pipeline.

## Packages (`com.kingshot.*`), one Gradle module `app` until a boundary earns a split (ADR then)
| Package | Owns | Rules |
|---|---|---|
| `camera` | CameraManager, CameraCharacteristics access, CameraDevice/CameraCaptureSession lifecycle | The only package that touches `CameraDevice` / `CameraCaptureSession`. Callbacks on a dedicated HandlerThread. |
| `capabilities` | Immutable, serializable `CameraCapabilities` model per camera ID and physical ID, built from `CameraCharacteristics` by one thin adapter | All decisions run on the model, so they unit-test against JSON fixtures recorded from real devices. Never `Build.MODEL`. |
| `capture` | Capability-checked request builders, exposure/focus control, result callbacks, Capture Orchestrator state machine | Results always carry requested + reported + status (honored / differs / unavailable / not_requested). |
| `raw` | DNG creation, RAW metadata validation, renderer seam | An Image is matched to its TotalCaptureResult (by sensor timestamp, bounded, with timeout and orphan handling) before `DngCreator` runs. |
| `processing` | Neutral rendering, styles, denoise, HDR fusion | Phase 3 only, behind feature flags until benchmarks pass. Not RenderScript. |
| `media` | MediaStore saving, original/rendered pairing, deletion safety | Never overwrites. Reconciles pairs on launch. No delete code without owner approval. |
| `profiles` | Natural / Computational / Custom, validation, persistence (DataStore) | Validation returns reasons the UI can show. |
| `diagnostics` | Capability report (JSON + text), Processing Receipts, capture logs, exportable bundle | User-initiated share only. Key names, no vendor-private values. |
| `ui` | Compose screens, ViewModels with StateFlow | Unidirectional data flow. Talks to the Orchestrator, never to Camera2. |

## Dependency direction (enforced by an architecture test, task P0-08)
`ui → capture → {camera, capabilities, raw, media, profiles, diagnostics}`; `camera → capabilities`; `raw → capabilities`;
`diagnostics → capabilities`; `processing → raw, media` (Phase 3). `capabilities` and `profiles` depend on nothing in the app. No cycles.

## Key design points
- **Pure core, thin Android edge.** Matching, request building, validation, receipt building, pairing and storage-failure handling are Kotlin classes with no Android types in their signatures, so they run as JVM tests. Android types stay in adapters.
- **Image lifetime.** Every Image has one owner; a `use`-style wrapper closes it exactly once on every path (match, timeout, error, shutdown). Queues are bounded to ImageReader `maxImages - 1`; excess shutter taps are rejected with a visible reason, not buffered.
- **Threading.** Camera callbacks on one dedicated thread; encode/save on an IO dispatcher; coroutines + Flow; never GlobalScope.
- **Camera2 first.** Extensions are evaluated only for the Computational profile (ADR in Phase 3).
- **Storage.** DataStore for settings/profiles. Receipts and pairing: Room vs sidecar JSON decided in an ADR (Phase 2); must survive files moved/deleted outside the app.
- **Privacy.** CAMERA permission only; no INTERNET; CI gate on the merged manifest.

## Initial ADRs to write (docs/decisions/)
0001 minimum SDK (proposal: 31, current prototype uses 29) · 0002 UI toolkit and when Compose replaces XML · 0003 capability model schema/versioning · 0004 NR MINIMAL fallback (Phase 0, from evidence) · 0005 receipt/pairing store (Phase 2) · 0006 RAW library (Phase 3, owner decides).
