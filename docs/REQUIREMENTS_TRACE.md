# Requirements trace

Single progress ledger. Status values: not started, implemented, unit-verified, device-verified, blocked.
`*` after a phase = **my interpretation**, not stated in the blueprint. "unscheduled" = named under "Later" in the feature matrix but in no roadmap phase until you assign one.
"Evidence" says "prototype partial" where the Camera Lab touches a row; that is not implementation evidence (nothing is linted, unit-tested or run on a device), so status stays "not started".
CI evidence rows are marked unverified until the first green run is linked.

| ID | Blueprint § | Requirement | Phase | Module | Tests | Status | Evidence |
|---|---|---|---|---|---|---|---|
| P1 | 2 | Choice: Natural, Computational, Custom capture profiles | 1 Natural / 2 Custom / 3 Computational* | profiles | profile validation unit tests | not started | - |
| P2 | 2 | Truthfulness: show requested vs reported vs unavailable; never call a JPEG unprocessed | 0 | capture, diagnostics | receipt builder tests; copy lint | not started | prototype partial: PROTOTYPE_AUDIT #7,#9 |
| P3 | 2 | Preservation: never silently overwrite a source capture; original-plus-rendered | 2 | media | pairing and no-overwrite tests + instrumented MediaStore | not started | - |
| P4 | 2 | Capability-aware UI: controls only when the active camera supports them | 1 | capabilities, UI | registry fixture tests; Compose UI tests | not started | - |
| P5 | 2 | Fast default path: open, one tap, saved photo | 1 | UI, capture | cold-start-to-first-frame benchmark | not started | - |
| P6 | 2 | Privacy: local by default; AI/cloud only by explicit opt-in | 0 | all | CI merged-manifest INTERNET gate | not started | CI step added; unverified until first run |
| P7 | 2 | Incremental engineering: prove pipeline before AI and video | all | - | phase gates | not started | - |
| FM01 | 3 (Capture / MVP) | Photo, JPEG, DNG when supported, lens discovery | 1 (DNG: 2) | capture, raw, capabilities | per phase | not started | prototype partial: PROTOTYPE_AUDIT #1-4 |
| FM02 | 3 (Capture / Later) | Burst | unscheduled | capture | - | not started | - |
| FM03 | 3 (Capture / Later) | Bracketing | 4 | capture | per phase | not started | - |
| FM04 | 3 (Capture / Later) | Intervalometer | unscheduled | capture | - | not started | - |
| FM05 | 3 (Processing / MVP) | Natural profile; supported NR/edge requests; capture log | 0-1 | profiles, capture | per phase | not started | prototype partial: PROTOTYPE_AUDIT #9 |
| FM06 | 3 (Processing / Later) | RAW renderer | 3 | processing, raw | per phase | not started | - |
| FM07 | 3 (Processing / Later) | Denoise | 3 | processing | per phase | not started | - |
| FM08 | 3 (Processing / Later) | HDR fusion | 3 | processing | per phase | not started | - |
| FM09 | 3 (Processing / Later) | Presets | unscheduled | processing | - | not started | - |
| FM10 | 3 (Controls / MVP) | Exposure compensation, focus/exposure basics | 1 | capture | per phase | not started | - |
| FM11 | 3 (Controls / Later) | Full manual ISO / shutter / focus / WB | 4 | capture | per phase | not started | - |
| FM12 | 3 (Controls / Later) | Histogram | 4 | processing, UI | per phase | not started | - |
| FM13 | 3 (Controls / Later) | Focus peaking | 4 | processing, UI | per phase | not started | - |
| FM14 | 3 (Gallery / MVP) | Capture list / MediaStore save | 1 | media | per phase | not started | - |
| FM15 | 3 (Gallery / Later) | Original-rendered compare | 3 | media, UI | per phase | not started | - |
| FM16 | 3 (Gallery / Later) | Non-destructive edits | unscheduled | media, processing | - | not started | - |
| FM17 | 3 (Night / MVP) | Normal capture with low-light reporting (no Night mode) | 1 | capture, diagnostics | per phase | not started | - |
| FM18 | 3 (Night / Later) | Multi-frame Night mode | 3 (own pipeline experiment)*; product mode unscheduled | processing | per phase | not started | - |
| FM19 | 3 (Night / Later) | Tripod/astro workflow | unscheduled | capture | - | not started | - |
| FM20 | 3 (Portrait / MVP) | Standard camera capture | 1 | capture | per phase | not started | - |
| FM21 | 3 (Portrait / Later) | Depth-aware blur and user-approved retouching | 4 | processing | per phase | not started | - |
| FM22 | 3 (Video / MVP) | Not part of initial Camera Lab | - | - | - | not started | - |
| FM23 | 3 (Video / Later) | Frame-rate/resolution controls, stabilization, creator tools | 4 | camera, capture | per phase | not started | - |
| FM24 | 3 (Trust / MVP) | Capability report, processing receipts | 0 report / 2 receipts* | diagnostics | per phase | not started | - |
| FM25 | 3 (Trust / Later) | Provenance manifest and exportable processing recipe | 4 | diagnostics, media | per phase | not started | - |
| M1 | 5 | Module `camera`: CameraManager, CameraCharacteristics, device/session lifecycle | 0-3 | camera | camera lifecycle tests with fakes | not started | prototype partial: PROTOTYPE_AUDIT #21 (monolith) |
| M2 | 5 | Module `capabilities`: Supported lenses, formats, request keys, modes, sizes (immutable model) | 0-3 | capabilities | fixture-driven registry tests | not started | prototype partial: PROTOTYPE_AUDIT #21 (monolith) |
| M3 | 5 | Module `capture`: Request construction, exposure/focus control, callbacks, errors | 0-3 | capture | request builder tests | not started | prototype partial: PROTOTYPE_AUDIT #21 (monolith) |
| M4 | 5 | Module `raw`: DNG creation, RAW metadata validation, future renderer seam | 0-3 | raw | timestamp matcher tests | not started | prototype partial: PROTOTYPE_AUDIT #21 (monolith) |
| M5 | 5 | Module `processing`: Optional neutral rendering, styles, denoise, HDR fusion | 3 | processing | benchmarks | not started | prototype partial: PROTOTYPE_AUDIT #21 (monolith) |
| M6 | 5 | Module `media`: MediaStore saving, original/rendered pairing, deletion safety | 0-3 | media | pairing + storage failure tests | not started | prototype partial: PROTOTYPE_AUDIT #21 (monolith) |
| M7 | 5 | Module `profiles`: Natural / Computational / Custom settings | 0-3 | profiles | validation tests | not started | prototype partial: PROTOTYPE_AUDIT #21 (monolith) |
| M8 | 5 | Module `diagnostics`: Device/build/capability report, capture logs, exportable bundle | 0-3 | diagnostics | report schema tests | not started | prototype partial: PROTOTYPE_AUDIT #21 (monolith) |
| M9 | 5 (prompt s5) | Architecture test enforces module dependency direction | 0 | all | architecture test (JVM) | not started | - |
| PR1 | 5 | Profile Natural: request supported minimal controls, keep AF/AE, no creative effects, disclose JPEG may be processed | 1 | profiles | validation tests | not started | - |
| PR2 | 5 | Profile Computational: supported OEM extension or own multi-frame; keep original frames if preservation on | 3 | profiles, processing | benchmarks | not started | - |
| PR3 | 5 | Profile Custom: independent format/controls/renderer settings validated before capture | 2 | profiles | validation tests | not started | - |
| UI1 | 6 Main camera | Top bar: identity, flash, timer, settings | 1 | UI | Compose UI tests | not started | - |
| UI2 | 6 Main camera | Viewfinder: live preview, focus indicator, grid/level toggles | 1 | UI | Compose UI tests | not started | - |
| UI3 | 6 Main camera | Lens/zoom strip labelling optical transitions and digital crop | 1 | UI | Compose UI tests | not started | - |
| UI4 | 6 Main camera | Processing chip: Natural / Computational / Custom | 1 (chip) - profiles arrive per P1 | UI | Compose UI tests | not started | - |
| UI5 | 6 Main camera | Shutter and gallery thumbnail | 1 | UI | Compose UI tests | not started | - |
| UI6 | 6 Main camera | Slide-up Pro panel: exposure, focus, WB, processing | 1 (exposure) / 4 (manual)* | UI | Compose UI tests | not started | - |
| UI7 | 6 Main camera | Clear status whenever a control is unsupported | 1 | UI | Compose UI tests | not started | - |
| GA1 | 6 Gallery | Mark source RAW and rendered image as a related pair | 2 pairing / 3 rendered* | media, UI | pairing tests | not started | - |
| GA2 | 6 Gallery | Show capture date, lens/crop, exposure, profile | 2 | media, UI | pairing tests | not started | - |
| GA3 | 6 Gallery | Compare original/rendered versions | 3 | media, UI | pairing tests | not started | - |
| GA4 | 6 Gallery | Never silently overwrite the source when exporting an edit | 2 | media, UI | pairing tests | not started | - |
| CF01 | 7 (docx numbers 8-17) | Request camera permission | 0 prototype; hardened 1-2 | camera, capture, raw, media | orchestrator state-machine tests | not started | prototype partial: PROTOTYPE_AUDIT (permission flow present) |
| CF02 | 7 (docx numbers 8-17) | Enumerate camera IDs and inspect CameraCharacteristics | 0 prototype; hardened 1-2 | camera, capture, raw, media | orchestrator state-machine tests | not started | prototype partial: PROTOTYPE_AUDIT #10,#16 |
| CF03 | 7 (docx numbers 8-17) | Select rear camera and suitable preview size | 0 prototype; hardened 1-2 | camera, capture, raw, media | orchestrator state-machine tests | not started | prototype partial: PROTOTYPE_AUDIT #16 |
| CF04 | 7 (docx numbers 8-17) | Build session using supported output combinations | 0 prototype; hardened 1-2 | camera, capture, raw, media | orchestrator state-machine tests | not started | prototype partial: PROTOTYPE_AUDIT #1 |
| CF05 | 7 (docx numbers 8-17) | Request preview/capture settings only when supported | 0 prototype; hardened 1-2 | camera, capture, raw, media | orchestrator state-machine tests | not started | prototype partial (NR/edge only) |
| CF06 | 7 (docx numbers 8-17) | Capture JPEG and, if selected and supported, RAW_SENSOR | 0 prototype; hardened 1-2 | camera, capture, raw, media | orchestrator state-machine tests | not started | prototype partial: PROTOTYPE_AUDIT #1 |
| CF07 | 7 (docx numbers 8-17) | Associate matching TotalCaptureResult with RAW image for DngCreator | 0 prototype; hardened 1-2 | camera, capture, raw, media | orchestrator state-machine tests | not started | prototype partial: PROTOTYPE_AUDIT #2,#3 |
| CF08 | 7 (docx numbers 8-17) | Save through MediaStore and confirm success | 0 prototype; hardened 1-2 | camera, capture, raw, media | orchestrator state-machine tests | not started | prototype partial: PROTOTYPE_AUDIT #5,#8 |
| CF09 | 7 (docx numbers 8-17) | Log result metadata and any unavailable requested control | 0 prototype; hardened 1-2 | camera, capture, raw, media | orchestrator state-machine tests | not started | prototype partial: PROTOTYPE_AUDIT #9 |
| CF10 | 7 (docx numbers 8-17) | Release camera resources on pause/error and safely reopen | 0 prototype; hardened 1-2 | camera, capture, raw, media | orchestrator state-machine tests | not started | prototype partial: PROTOTYPE_AUDIT #13,#14 |
| CF11 | 7 | Production pipeline: bounded queue, explicit timestamp matching, lifecycle tests, storage-failure handling, orientation handling, per-device stream compatibility checks | 1-2 | capture, raw, media | matcher, orientation, storage tests | not started | prototype partial: PROTOTYPE_AUDIT #2,#12,#15 |
| QA01 | 10 | Camera preview starts and recovers from pause/resume | 1 | - | device validation (DV-nnn) | not started | - |
| QA02 | 10 | RAW availability decided from runtime characteristics, not model names | 0-1 | - | device validation (DV-nnn) | not started | - |
| QA03 | 10 | DNG opens in an independent RAW viewer and has usable metadata | 2 | - | device validation (DV-nnn) | not started | - |
| QA04 | 10 | Minimal controls requested only if supported | 0-1 | - | device validation (DV-nnn) | not started | - |
| QA05 | 10 | Capture results log requested and returned exposure information | 0-1 | - | device validation (DV-nnn) | not started | - |
| QA06 | 10 | Missing/unsupported settings disclosed, not silently promised | 1 | - | device validation (DV-nnn) | not started | - |
| QA07 | 10 | Source images not overwritten by edited exports | 2 | - | device validation (DV-nnn) | not started | - |
| QA08 | 10 | Capture survives interruptions and handles storage exhaustion | 1 | - | device validation (DV-nnn) | not started | - |
| QA09 | 10 | Test scenes: portrait, bright daylight, backlit faces, fine textures, night, mixed lighting, motion | 0 protocol / 3 | - | device validation (DV-nnn) | not started | - |
| QA10 | 10 | Compare stock camera, Natural JPEG, DNG reference rendering and computational output with repeated trials | 0 / 3 | - | device validation (DV-nnn) | not started | - |
| QA11 | 10 | Measure shutter latency, processing time, memory, storage, thermal behaviour, crashes | 0-3 | - | device validation (DV-nnn) | not started | - |
| R1 | 11 | Risk: Camera HAL/ISP behaviour is partly vendor-controlled | 0-4 | - | mitigation tracked in ROADMAP | not started | - |
| R2 | 11 | Risk: RAW support differs by camera ID and output combination | 0-4 | - | mitigation tracked in ROADMAP | not started | - |
| R3 | 11 | Risk: RAW alone does not guarantee better quality than a good multi-frame JPEG | 0-4 | - | mitigation tracked in ROADMAP | not started | - |
| R4 | 11 | Risk: Concurrent JPEG and RAW may be unsupported in some session combinations | 0-4 | - | mitigation tracked in ROADMAP | not started | - |
| R5 | 11 | Risk: Multi-frame processing can cause ghosting, heat, memory pressure, delay | 0-4 | - | mitigation tracked in ROADMAP | not started | - |
| R6 | 11 | Risk: Device-specific tuning creates maintenance burden (no per-model code without measurement + ADR) | 0-4 | - | mitigation tracked in ROADMAP | not started | - |
| R7 | 11 | Risk: UI must be tested with rotation, screen lock, calls, permissions, backgrounding | 0-4 | - | mitigation tracked in ROADMAP | not started | - |
| PH0 | 9 | Phase 0: Device feasibility: Camera Lab, capability report, test kit; gate = no minimal-processing claim without proof | 0 | - | phase report + gate | not started | - |
| PH1 | 9 | Phase 1: Core capture: preview, capture, lens, timer, EV, saving, lifecycle | 1 | - | phase report + gate | not started | - |
| PH2 | 9 | Phase 2: RAW and processing control: DNG reliability, profile persistence, logging, fallback UI, receipts | 2 | - | phase report + gate | not started | - |
| PH3 | 9 | Phase 3: Rendering and computational: RAW renderer, pairing, HDR/denoise experiments, benchmarks | 3 | - | phase report + gate | not started | - |
| PH4 | 9 | Phase 4: Advanced camera and video | 4 | - | phase report + gate | not started | - |
| X1 | prompt s3 | No model-specific feature claim without official spec page supplied by owner | all | - | copy review | not started | - |
| X2 | prompt s4 | Minimum permissions: CAMERA only until geotagging/video need more; geotag opt-in | all | - | manifest gate | not started | CI step added; unverified until first run |
| X3 | prompt s7 | Accessibility: TalkBack labels, 48dp targets, large fonts, portrait+landscape, one-handed shutter | 1 | UI | Compose UI tests | not started | - |
| X4 | prompt s7 | ktlint, detekt, Android lint clean in CI | 0 | build | CI | not started | lint in CI; ktlint/detekt not yet configured |
| X5 | prompt s9 | Release readiness: name clearance, privacy policy, signed release, store text | on request | - | checklist | not started | - |
