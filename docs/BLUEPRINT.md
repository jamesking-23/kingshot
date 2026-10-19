<!-- Converted from KINGSHOT_BLUEPRINT.docx (v0.1, 19 October 2026) with pandoc.
     NOTE: In the .docx, the capture-flow steps are numbered 8-17 because the list continues
     from the principles list. Read them as steps 1-10. -->

# KingShot Blueprint

**Version:** 0.1 · **Date:** 19 October 2026 · **Status:** Prototype / feasibility stage

**Product thesis:** Give photographers meaningful control over capture processing, while retaining an optional high-quality computational workflow.

# 1. Executive summary

KingShot is an Android camera application designed first for Pixel phones. It combines computational photography conveniences with a photographer-first choice of capture pipeline. Its differentiator is not a promise of “zero processing” (which cannot be guaranteed through public APIs), but a transparent, capability-aware interface for requesting minimal processing, capturing RAW sensor data where supported, and applying optional rendering or computational enhancement later.

The first engineering milestone is a Camera Lab: inspect camera capabilities; capture JPEG and DNG when available; request noise-reduction and edge-enhancement Off where supported; log capture metadata; and compare the output against the stock camera under repeatable conditions.

# 2. Product principles

1.  Choice: Natural, Computational and Custom capture profiles.

2.  Truthfulness: Show what was requested, what the device reports, and what is unavailable. Do not label a JPEG “unprocessed.”

3.  Preservation: Never silently overwrite a source capture; offer original-plus-rendered workflows.

4.  Capability-aware UI: Controls appear only when the active camera supports them.

5.  Fast default path: Opening the camera and taking an ordinary photograph must remain simple.

6.  Privacy: Capture and editing work locally by default; AI/cloud features, if introduced, are explicit opt-ins.

7.  Incremental engineering: Prove the camera pipeline before building complex AI and video features.

# 3. Competitive research and feature strategy

## Google Pixel Camera inspiration

Study computational HDR, Night Sight, RAW support, lens-aware zoom, portrait rendering, motion/moment selection, camera assistance, stabilization and creator-oriented video workflows. These are product families rather than guarantees that every Pixel model exposes every feature to third-party applications.

## Apple iPhone Camera inspiration

Study accessible capture controls, photographic styles, lens/framing behavior, video resolution/frame-rate choices, stabilization, post-capture effects, and a coherent photo/video experience. Distinguish model-specific and Pro-only features.

## Current-generation verification rule

Pixel 11 and iPhone 18 are generation-specific products. Before marketing any model feature, verify the official announcement and specification page for the exact model and region. Treat leaks, rumors, beta software and third-party speculation as unconfirmed. This blueprint does not promise that KingShot can invoke proprietary Pixel Camera or iPhone camera algorithms.

## Proposed feature matrix

| **Area**   | **MVP**                                                  | **Later**                                                    |
|------------|----------------------------------------------------------|--------------------------------------------------------------|
| Capture    | Photo, JPEG, DNG when supported, lens discovery          | Burst, bracketing, intervalometer                            |
| Processing | Natural profile; supported NR/edge requests; capture log | RAW renderer, denoise, HDR fusion, presets                   |
| Controls   | Exposure compensation, focus/exposure basics             | Full manual ISO/shutter/focus/WB, histogram, focus peaking   |
| Gallery    | Capture list / MediaStore save                           | Original-rendered compare, non-destructive edits             |
| Night      | Normal capture with low-light reporting                  | Multi-frame Night mode, tripod/astro workflow                |
| Portrait   | Standard camera capture                                  | Depth-aware blur and user-approved retouching                |
| Video      | Not part of initial Camera Lab                           | Frame-rate/resolution controls, stabilization, creator tools |
| Trust      | Capability report, processing receipts                   | Provenance manifest and exportable processing recipe         |

# 4. Technical reality: what “bypass processing” means

The imaging path includes sensor/readout and hardware correction, the vendor camera HAL/ISP, Android camera controls, and KingShot's own renderer. Public Camera2 requests can control only supported settings. Some ISP behavior is not exposed. Therefore:

- RAW_SENSOR is the best available source for a flexible sensor-domain workflow where supported; it is not proof of absolutely untouched sensor data.

- JPEG is already rendered and may include unavoidable or vendor-specific processing.

- NOISE_REDUCTION_MODE_OFF and EDGE_MODE_OFF should only be requested if listed in the camera's available modes.

- Manual exposure requires appropriate device capability and available request controls.

- Camera extensions may provide vendor HDR/Night/Bokeh processing but are separate from a minimal-processing path.

- Multi-frame HDR, Night and computational zoom require additional implementation or supported OEM extensions.

# 5. Architecture

**UI (Kotlin/Compose or current XML prototype) → Capture Orchestrator → Camera2 capability registry → Camera session → JPEG / RAW readers → storage and metadata → optional render pipeline.**

## Modules

- camera: CameraManager, CameraCharacteristics, device/session lifecycle.

- capabilities: supported lenses, formats, request keys, modes and sizes.

- capture: request construction, exposure/focus control, callbacks and errors.

- raw: DNG creation, RAW metadata validation and future renderer integration.

- processing: optional neutral rendering, styles, denoising and HDR fusion.

- media: MediaStore saving, original/rendered pairing and deletion safety.

- profiles: Natural/Computational/Custom settings.

- diagnostics: device/build/capability report, capture result logs and exportable test bundle.

## Profiles

**Natural:** Request supported minimal enhancement controls, preserve autofocus/automatic exposure by default, avoid KingShot creative effects, and disclose that the JPEG may still be processed by the device.

**Computational:** Use a supported OEM extension or KingShot's own multi-frame pipeline. Keep original frames if preservation is enabled.

**Custom:** Independently choose format, supported camera controls, and KingShot renderer settings. Validate settings before starting a capture.

# 6. UI blueprint

## Main camera

- Top bar: KingShot identity, flash, timer and settings.

- Viewfinder: live preview, focus indicator, grid/level toggles.

- Lens/zoom strip: label actual lens transitions and digital crop.

- Processing chip: Natural / Computational / Custom.

- Shutter and gallery thumbnail.

- Slide-up Pro panel for exposure, focus, white balance and processing.

- Clear status if a control is unsupported.

## Gallery

- Mark source RAW and rendered image as a related pair.

- Show capture date, active lens/crop, exposure and processing profile.

- Compare original/rendered versions.

- Never silently overwrite the source when exporting an edit.

# 7. Capture flow

1.  Request camera permission.

2.  Enumerate camera IDs and inspect `CameraCharacteristics`.

3.  Select rear camera and suitable preview size.

4.  Build a capture session using supported output combinations.

5.  Request preview and capture settings only when supported.

6.  Capture JPEG and, if selected and supported, RAW_SENSOR.

7.  Associate the matching `TotalCaptureResult` with the RAW image for `DngCreator`.

8.  Save through MediaStore and confirm success.

9.  Log capture result metadata and any requested control that was unavailable.

10.  Release camera resources on pause/error and safely reopen.

A production implementation should use a bounded image-processing queue, explicit image/result timestamp matching, lifecycle tests, storage failure handling, orientation handling, and device-specific output-stream compatibility checks.

# 8. Implementation notes for the included prototype

The supplied Android Studio project is an early Camera2 lab, not a production camera app. It is intended to demonstrate:

- Camera permission and rear-camera discovery.

- Live TextureView preview.

- Capability inspection for RAW, noise-reduction and edge modes.

- A minimal-processing request where supported.

- JPEG capture and an optional RAW+DNG path.

- A capture log.

**Important prototype caveats:** It has not been built or run on a physical Pixel in this environment. The first version needs compilation and device validation. The RAW/JPEG multi-output session and asynchronous matching of RAW Image to its corresponding TotalCaptureResult require hardening before relying on DNG output. The prototype is a feasibility starting point, not proof that all Pixel processing has been bypassed. It does not yet include a full RAW renderer, image comparison screen, computational HDR fusion, or video.

# 9. Roadmap

## Phase 0 — Device feasibility (1–2 weeks)

Run on the target Pixel; collect camera characteristics, supported keys, RAW outputs and actual saved files. Compare stock Camera, KingShot JPEG and DNG rendered by an independent RAW tool.

**Gate:** Do not proceed to a “minimal processing” claim until tests prove which settings work.

## Phase 1 — Core capture (2–4 weeks)

Reliable preview, capture, lens selection, timer, exposure compensation, saving and lifecycle recovery.

## Phase 2 — RAW and processing control (3–5 weeks)

DNG reliability, profile persistence, request/result logging, support/fallback UI and processing receipts.

## Phase 3 — Rendering and computational photography (4–8 weeks)

Integrate a licensed RAW rendering library, implement original/rendered pairing, build HDR fusion and denoising experiments, and benchmark memory/latency/heat.

## Phase 4 — Advanced camera and video (4–8 weeks)

Manual controls, histogram, focus peaking, bracketing, portrait depth, video modes, stabilization and creator workflow.

These are planning estimates and can expand substantially for a solo developer or custom RAW/HDR pipeline.

# 10. QA and acceptance tests

- Camera preview starts and recovers from pause/resume.

- RAW availability is determined from runtime characteristics, not model-name assumptions.

- DNG opens in an independent RAW viewer and contains usable metadata.

- Minimal controls are requested only if supported.

- Capture results log requested and returned exposure information.

- Missing or unsupported settings are disclosed rather than silently promised.

- Source images are not overwritten by edited exports.

- Capture survives interruptions and handles storage exhaustion.

- Portrait, bright daylight, backlit faces, fine textures, night scenes, mixed lighting and motion scenes are tested.

- Compare stock camera, Natural JPEG, DNG reference rendering and computational output with repeated trials.

- Measure shutter latency, image processing time, memory, storage consumption, thermal behavior and crashes.

# 11. Risks

- Camera HAL/ISP behavior is partly vendor-controlled.

- RAW support differs by camera ID and output combination.

- RAW alone does not guarantee better image quality than a good multi-frame JPEG.

- Concurrent JPEG and RAW outputs may not be supported in every session combination.

- Multi-frame processing can cause ghosting, heat, memory pressure and delay.

- Device-specific tuning can create a maintenance burden.

- Camera UI must be tested with rotation, screen lock, incoming calls, permissions and app backgrounding.

# 12. Official technical references

- Android Camera2 CaptureRequest: https://developer.android.com/reference/android/hardware/camera2/CaptureRequest

- Android CameraCharacteristics: https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics

- Android DngCreator: https://developer.android.com/reference/android/hardware/camera2/DngCreator

- Android CameraX extensions: https://developer.android.com/media/camera/camera-extensions

- Android camera samples: https://github.com/android/camera-samples

- Google Pixel official store/specifications: https://store.google.com/

- Apple iPhone official specifications/newsroom: https://www.apple.com/iphone/ and https://www.apple.com/newsroom/

Always re-check the official model-specific spec pages before claiming exact Pixel 11 or iPhone 18 feature support.

# 13. Immediate next action

Install and run the Camera Lab on the exact Pixel device. Save the capability report, one JPEG, and one DNG if supported. Inspect those files and compare the results with the stock camera. The measured capability report should decide the next engineering milestone.
