# Validation log

One entry per Device Validation Request result. Until an entry exists, a device-dependent behavior is "unverified on device".
Raw bundles are kept by the owner; the capability report is stored as a fixture in `fixtures/capabilities/`.

| DV | Date run | Build | Device / Android | Outcome | Decision | Fixture |
|---|---|---|---|---|---|---|
| DV-001 | 2026-10-22 | app 0.2.0 debug (CI build between `f0fd7d9` and `ed68089`; exact commit not recorded in the app) | Google Pixel 7 (`panther`), Android 17 / SDK 37, build `CP3A.261005.002.A1`, patch 2026-10-05 | **Partial pass** (JPEG only) | see below | `google_panther_android17_report.json` |
| DV-002 | 2026-10-22 | same | same | **Partial**: 3 captures, one indoor low-light scene, both requests ticked; no unticked comparison, one scene instead of three | see below | n/a |
| DV-003 | not run | | | RAW + JPEG together not tried (RAW box was unticked) | pending | |
| DV-004 | not run | | | no DNG exists yet | pending | |

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
