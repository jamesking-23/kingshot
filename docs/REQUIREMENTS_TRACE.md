# Requirements trace

Single progress ledger. Status values: not started, implemented, unit-verified, device-verified, blocked.
`*` after a phase = **my interpretation**, not stated in the blueprint. "unscheduled" = named under "Later" in the feature matrix but in no roadmap phase until you assign one.
"implemented" = code exists and CI is green, but it has not run on a device. "unit-verified" = JVM tests cover the logic. No row is device-verified: no device run exists yet (docs/VALIDATION_LOG.md is empty).

| ID | Blueprint § | Requirement | Phase | Module | Tests | Status | Evidence |
|---|---|---|---|---|---|---|---|
| P1 | 2 | Choice: Natural, Computational, Custom capture profiles | 1 Natural / 2 Custom / 3 Computational* | profiles | profile validation unit tests | not started | - |
| P2 | 2 | Truthfulness: show requested vs reported vs unavailable; never call a JPEG unprocessed | 0 | capture, diagnostics | receipt builder tests; copy lint | implemented | requested/reported/status model + receipts + UI disclosure; unit tests (DiagnosticsTest, StillRequestPlannerTest); unverified on device |
| P3 | 2 | Preservation: never silently overwrite a source capture; original-plus-rendered | 2 | media | pairing and no-overwrite tests + instrumented MediaStore | not started | - |
| P4 | 2 | Capability-aware UI: controls only when the active camera supports them | 1 | capabilities, UI | registry fixture tests; Compose UI tests | not started | - |
| P5 | 2 | Fast default path: open, one tap, saved photo | 1 | UI, capture | cold-start-to-first-frame benchmark | not started | - |
| P6 | 2 | Privacy: local by default; AI/cloud only by explicit opt-in | 0 | all | CI merged-manifest INTERNET gate | implemented | INTERNET gate + no-network-API architecture test; CAMERA only; https://github.com/jamesking-23/kingshot/actions/runs/39354771975 |
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
| M1 | 5 | Module `camera`: CameraManager, CameraCharacteristics, device/session lifecycle | 0-3 | camera | camera lifecycle tests with fakes | implemented | camera/CameraHost, CharacteristicsReader; thread-confined lifecycle; no device run |
| M2 | 5 | Module `capabilities`: Supported lenses, formats, request keys, modes, sizes (immutable model) | 0-3 | capabilities | fixture-driven registry tests | unit-verified | capabilities model+decisions tested (CapabilitiesTest); adapter unverified on device |
| M3 | 5 | Module `capture`: Request construction, exposure/focus control, callbacks, errors | 0-3 | capture | request builder tests | unit-verified | StillRequestPlanner tests; orchestrator unverified on device |
| M4 | 5 | Module `raw`: DNG creation, RAW metadata validation, future renderer seam | 0-3 | raw | timestamp matcher tests | unit-verified | TimestampMatcher tests (bounded, orphans, exactly-once); DNG path unverified on device (DV-003/004) |
| M5 | 5 | Module `processing`: Optional neutral rendering, styles, denoise, HDR fusion | 3 | processing | benchmarks | not started | prototype partial: PROTOTYPE_AUDIT #21 (monolith) |
| M6 | 5 | Module `media`: MediaStore saving, original/rendered pairing, deletion safety | 0-3 | media | pairing + storage failure tests | implemented | MediaSaver: new item per save, no delete code; no tests yet |
| M7 | 5 | Module `profiles`: Natural / Computational / Custom settings | 0-3 | profiles | validation tests | not started | prototype partial: PROTOTYPE_AUDIT #21 (monolith) |
| M8 | 5 | Module `diagnostics`: Device/build/capability report, capture logs, exportable bundle | 0-3 | diagnostics | report schema tests | unit-verified | CapabilityReport/receipt/log tests; bundle export unverified on device |
| M9 | 5 (prompt s5) | Architecture test enforces module dependency direction | 0 | all | architecture test (JVM) | unit-verified | ArchitectureTest https://github.com/jamesking-23/kingshot/actions/runs/39354771975 |
| PR1 | 5 | Profile Natural: request supported minimal controls, keep AF/AE, no creative effects, disclose JPEG may be processed | 1 | profiles | validation tests | not started | Natural profile arrives Phase 1; Lab uses profile name 'lab' |
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
| CF01 | 7 (docx numbers 8-17) | Request camera permission | 0 prototype; hardened 1-2 | camera, capture, raw, media | orchestrator state-machine tests | implemented | permission request in ui/MainActivity |
| CF02 | 7 (docx numbers 8-17) | Enumerate camera IDs and inspect CameraCharacteristics | 0 prototype; hardened 1-2 | camera, capture, raw, media | orchestrator state-machine tests | implemented | CharacteristicsReader.readAll (all IDs + physical IDs); DV-001 |
| CF03 | 7 (docx numbers 8-17) | Select rear camera and suitable preview size | 0 prototype; hardened 1-2 | camera, capture, raw, media | orchestrator state-machine tests | implemented | CameraSelector (assumption pending DV-001) + SizeChooser (unit-tested) |
| CF04 | 7 (docx numbers 8-17) | Build session using supported output combinations | 0 prototype; hardened 1-2 | camera, capture, raw, media | orchestrator state-machine tests | implemented | CameraHost.buildSession: isSessionConfigurationSupported + fallback; DV-003 |
| CF05 | 7 (docx numbers 8-17) | Request preview/capture settings only when supported | 0 prototype; hardened 1-2 | camera, capture, raw, media | orchestrator state-machine tests | implemented | StillRequestPlanner only requests listed modes (unit-tested); AF mode only if listed |
| CF06 | 7 (docx numbers 8-17) | Capture JPEG and, if selected and supported, RAW_SENSOR | 0 prototype; hardened 1-2 | camera, capture, raw, media | orchestrator state-machine tests | implemented | CameraHost.capture, JPEG + optional RAW targets; DV-003 |
| CF07 | 7 (docx numbers 8-17) | Associate matching TotalCaptureResult with RAW image for DngCreator | 0 prototype; hardened 1-2 | camera, capture, raw, media | orchestrator state-machine tests | unit-verified | TimestampMatcher + orchestrator; timestamp equality unverified (DV-001) |
| CF08 | 7 (docx numbers 8-17) | Save through MediaStore and confirm success | 0 prototype; hardened 1-2 | camera, capture, raw, media | orchestrator state-machine tests | implemented | MediaSaver + orchestrator; storage-failure path left pending item, reported; no tests yet |
| CF09 | 7 (docx numbers 8-17) | Log result metadata and any unavailable requested control | 0 prototype; hardened 1-2 | camera, capture, raw, media | orchestrator state-machine tests | implemented | control outcomes + exposure logged per capture and in receipts |
| CF10 | 7 (docx numbers 8-17) | Release camera resources on pause/error and safely reopen | 0 prototype; hardened 1-2 | camera, capture, raw, media | orchestrator state-machine tests | implemented | CameraHost state machine, shutdown joins camera thread; no lifecycle tests yet |
| CF11 | 7 | Production pipeline: bounded queue, explicit timestamp matching, lifecycle tests, storage-failure handling, orientation handling, per-device stream compatibility checks | 1-2 | capture, raw, media | matcher, orientation, storage tests | implemented | bounded matcher/queues/in-flight cap, orientation math (tested), storage failure reported; per-device stream check via isSessionConfigurationSupported; lifecycle tests missing |
| QA01 | 10 | Camera preview starts and recovers from pause/resume | 1 | - | device validation (DV-nnn) | not started | - |
| QA02 | 10 | RAW availability decided from runtime characteristics, not model names | 0-1 | - | device validation (DV-nnn) | not started | - |
| QA03 | 10 | DNG opens in an independent RAW viewer and has usable metadata | 2 | - | device validation (DV-nnn) | not started | - |
| QA04 | 10 | Minimal controls requested only if supported | 0-1 | - | device validation (DV-nnn) | not started | - |
| QA05 | 10 | Capture results log requested and returned exposure information | 0-1 | - | device validation (DV-nnn) | implemented | receipt + log carry requested and returned values; DV-002 |
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
| PH0 | 9 | Phase 0: Device feasibility: Camera Lab, capability report, test kit; gate = no minimal-processing claim without proof | 0 | - | phase report + gate | blocked | docs/phase0/PHASE0_REPORT.md (gate open: waiting on DV-001..004 from the owner's device) |
| PH1 | 9 | Phase 1: Core capture: preview, capture, lens, timer, EV, saving, lifecycle | 1 | - | phase report + gate | not started | - |
| PH2 | 9 | Phase 2: RAW and processing control: DNG reliability, profile persistence, logging, fallback UI, receipts | 2 | - | phase report + gate | not started | - |
| PH3 | 9 | Phase 3: Rendering and computational: RAW renderer, pairing, HDR/denoise experiments, benchmarks | 3 | - | phase report + gate | not started | - |
| PH4 | 9 | Phase 4: Advanced camera and video | 4 | - | phase report + gate | not started | - |
| X1 | prompt s3 | No model-specific feature claim without official spec page supplied by owner | all | - | copy review | not started | - |
| X2 | prompt s4 | Minimum permissions: CAMERA only until geotagging/video need more; geotag opt-in | all | - | manifest gate | implemented | CI gate green on prototype: https://github.com/jamesking-23/kingshot/actions/runs/39317677036 |
| X3 | prompt s7 | Accessibility: TalkBack labels, 48dp targets, large fonts, portrait+landscape, one-handed shutter | 1 | UI | Compose UI tests | not started | - |
| X4 | prompt s7 | ktlint, detekt, Android lint clean in CI | 0 | build | CI | implemented | ktlint + Android lint green in CI https://github.com/jamesking-23/kingshot/actions/runs/39354771975; detekt deferred (ADR 0002) |
| X5 | prompt s9 | Release readiness: name clearance, privacy policy, signed release, store text | on request | - | checklist | not started | - |
