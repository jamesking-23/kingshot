# Validation log

One entry per Device Validation Request result. Until an entry exists, a device-dependent behavior is "unverified on device".
Raw bundles are kept by the owner; the capability report is stored as a fixture in `fixtures/capabilities/`.

| DV | Date run | Build | Device / Android | Outcome | Decision | Fixture |
|---|---|---|---|---|---|---|
| DV-001 | 2026-10-22 | app 0.2.0 debug (CI build between `f0fd7d9` and `ed68089`; exact commit not recorded in the app) | Google Pixel 7 (`panther`), Android 17 / SDK 37, build `CP3A.261005.002.A1`, patch 2026-10-05 | **Partial pass** (JPEG only) | see below | `google_panther_android17_report.json` |
| DV-002 | 2026-10-22 | same | same | **Partial**: 3 captures, one indoor low-light scene, both requests ticked; no unticked comparison, one scene instead of three | see below | n/a |
| DV-003 | 2026-11-01 | self-check build (CI run 41309527673 or later) | Google Pixel 7, Android 17 | **Partial pass**: 2 RAW+JPEG shots saved as DNG+JPEG pairs; rapid-shot test not run | see DV-S1 | n/a |
| DV-004 | not run | | | DNGs exist (24.5 MB) but have not been opened in an independent RAW tool | pending | |
| DV-S2 | 2026-11-01 | self-check build with runWhenIdle fix | Google Pixel 7, Android 17 | **Pass (7 of 7 graded checks)**; one labelling issue found | see below | bundle `kingshot_bundle_20261101_163714` |
| DV-S1 | 2026-11-01 | self-check build | Google Pixel 7, Android 17 | **Mostly pass; one real bug found** | see below | bundle `kingshot_bundle_20261101_161427` |

## DV-001 details (facts only)
- Capability report built for **7** camera IDs: back logical `0` (physical `2`, `3`, `4`), front logical `1` (physical `5`, `6`). All FULL hardware level; all list the RAW capability and NR/Edge modes OFF, FAST, HIGH_QUALITY (MINIMAL is not listed on any of them). No field in camera `0` came back missing.
- Camera `0`: RAW_SENSOR 4080x3072 (also 4080x2288, 2032x1536), JPEG up to 4080x3072, 31 mandatory stream combinations listed, supported extension id list `[4]` (also on front `1`), 86 request keys, 107 result keys, 25 session keys (including vendor-prefixed keys; KingShot uses none).
- JPEG matched its capture result **by sensor timestamp in 3 of 3 captures** (two in the bundles, one in the owner's screenshot). No orphan lines. RAW timestamp matching: not tested.
- The rear camera chosen was `0`. Receipts report `activePhysicalId = 2` at zoom ratio 1.0 (focal length 6.81 mm in the report). Not tested at other zoom ratios.
- Both JPEGs: 4080x3072, EXIF orientation 6 (rotate 90), Make Google / Model Pixel 7.

## DV-002 details (facts only)
- NOISE_REDUCTION_MODE and EDGE_MODE: requested OFF, reported OFF, status `honored` in **3 of 3** captures.
- Exposure: 42.0 ms and 66.7 ms; sensor ISO reported 744 in all three; AE converged, AF passive-focused.
- **Open question:** EXIF ISO in the two JPEGs is 1904 and 4739, while the capture result's sensor ISO is 744 in both. The report lists `android.control.postRawSensitivityBoost` as a request and result key. Whether a boost explains the gap is **unknown**; this build did not record the boost. The next build logs it (receipt `exposure.postRawSensitivityBoost`).
- Timing/perf: shutter-to-saved 411, 433, 465 ms; processing 159 and 73 ms (the two receipts); thermal status LIGHT; heap 6-7 MB Java, 16-18 MB native; resume-to-first-frame 764, 307, 14, 9 ms (warm resumes, not a cold start).

## Decisions from the evidence
1. The assumption "`Image.timestamp` equals `SENSOR_TIMESTAMP`" holds for JPEG on this device (3/3). It stays unverified for RAW and on other devices.
2. The "first rear BACKWARD_COMPATIBLE camera is the main camera" assumption held on this device at 1x. Keep it, mark it device-specific evidence, and revisit when lens selection is built (Phase 1).
3. Natural may *request* OFF where listed. What the UI may *say* is in `docs/phase0/PHASE0_REPORT.md`.
4. MINIMAL is not listed on this device, so the "MINIMAL as a fallback" question does not arise here (ADR 0004).

## DV-S1 details: first one-tap self-check (facts only)
- 9 photos taken by the script. 8 saved a JPEG; shot 7 saved nothing (see bug).
- **Session with RAW:** the device reported "preview + JPEG + RAW session is supported"; the session was built with RAW; shots 8 and 9 each saved a DNG (24,518 KB) and a JPEG with the same file name. RAW and JPEG both matched their results by sensor timestamp (2 of 2). The DNG was written with camera `0`'s characteristics while the result reported active physical camera `2`. Whether that DNG is correct is **unverified** (no RAW viewer yet).
- **Lens mapping (matches the labels):** zoom 0.672 was served by physical camera `3`; zoom 1.0, 2.0 and 5.0 by physical camera `2`. So the strip's "lens" (0.7) versus "crop" (2, 5) wording agrees with what the camera reported. Camera `4` never became active. This phone has no separate telephoto lens in this run.
- **EV:** results reported the requested steps (+6, -6). Sensor exposure times ISO changed as 3.1 vs 1.3 (ms x ISO/100 units: shot 2 at +1 EV was only about 0.2 stop above the 0 EV shots; shot 3 at -1 EV was about 1 stop below). **Open question**, not a finding: scene change, AE settling or tone mapping could explain the asymmetry; JPEG brightness was not compared.
- NR and Edge: requested OFF, reported OFF, `honored` in 9 of 9. Same caveat as before: this does not prove untouched pixels.
- Timing: shutter-to-saved 251-472 ms (median 296). Thermal status went MODERATE then SEVERE from the third photo on; the app's own contribution is unknown (the phone may have been warm already).
- Low-light message appeared at about 1/29 s ISO 47-82. That is the message working as designed (exposure time at or slower than 1/30 s), though at such low ISO the "noise" wording is probably too strong; wording to be revisited.
- **Bug found and fixed:** ticking RAW rebuilt the camera session while shot 7 was still being delivered; its result arrived, its image never did ("unmatched: timeout"). Fix: the session rebuild now waits until no capture is in flight (`runWhenIdle`). Unverified on device until the next self-check.
- **Grader bug found and fixed:** the self-check wrongly demanded a DNG for the 7 photos taken before RAW was switched on.

## DV-S2 details: second self-check, after the fixes (facts only)
- All 9 photos saved a JPEG; the 2 RAW-on photos saved a DNG each. The lost-photo bug did not recur (RAW switch waited for the in-flight photo). Median shutter-to-saved 273 ms, worst 463 ms. NR/Edge requests honored 9 of 9 (same caveat). Thermal status MODERATE, SEVERE only on the last photo.
- **Zoom 2.0 and 5.0 were served by physical camera 4** this time (the first run used camera 2 at those zooms). So which physical camera serves a zoom ratio is the HAL's choice and can vary between runs; the app must report what the result says, never assume it from the label.
- **Camera 4 is not a separate lens.** The capability report gives it the main camera's focal length (6.81 mm) on a physical size of 4.896x3.6864 mm, exactly a quarter of the main sensor's area (9.792x7.3728 mm). The earlier build would have called it "2x lens". Now: same focal length on a clearly smaller sensor window is labelled "sensor crop" (the strip reads 0.7x lens, 1x lens, 2x sensor crop, 5x digital crop). This rests on characteristics only; how the HAL produces the 2x image (for example crop and remosaic) is not known from them.
- Exposure at +1 EV (10.2 ms, ISO 240) and -1 EV (11.1 ms, ISO 113) against baseline (33.3 ms, ISO 74-83): the sensor exposure moved in opposite directions to its time/ISO split but the open question about EV brightness is unchanged: JPEG brightness has still not been compared.
- Low-light wording changed: motion-blur wording for long exposures, noise wording only for high ISO.
