# Phase 0 report: device feasibility

**Status: gate still OPEN. First device data is in (Google Pixel 7, Android 17); RAW, the full DV-002 protocol and the stock comparison are outstanding.**
Updated 2026-10-23. Code: CI run 39868155301 (green). Device results: `docs/VALIDATION_LOG.md`. Recorded fixture: `fixtures/capabilities/google_panther_android17_report.json`.

## What the Pixel 7 run established (3 JPEG captures, one indoor low-light scene)
- The capability report works on a real device: 7 camera IDs (back logical `0` with physical `2`, `3`, `4`; front logical `1` with `5`, `6`), nothing missing for camera `0`.
- Noise reduction OFF and edge OFF are listed on every camera; MINIMAL is listed on none.
- Requests: NOISE_REDUCTION_MODE and EDGE_MODE requested OFF, reported OFF, `honored` in 3 of 3 captures.
- JPEG matched its result by sensor timestamp in 3 of 3 captures (RAW untested).
- Active physical camera in receipts: `2` at zoom 1.0 (focal length 6.81 mm in the report).
- MediaStore save, receipts, log and the bundle export/share all worked.
- Timing baseline (one device, indoor): shutter-to-saved 411 to 465 ms; thermal LIGHT; heap about 7 MB Java and 17 MB native.

## What it did NOT establish
- RAW or DNG anything (DV-003, DV-004 not run).
- Whether the result differs from the stock camera (no comparison yet).
- Anything about other scenes, other lenses, other zoom levels or other phones.
- **An unexplained gap:** the JPEGs' EXIF ISO (1904, 4739) is higher than the capture result's sensor ISO (744 in both). We do not know why. The next build records `postRawSensitivityBoost` to test one candidate explanation. Until explained, "the device reported OFF" must not be read as "the pixels are untouched".

## Claims allowed now
1. "On a Google Pixel 7 running Android 17, in three test captures, KingShot requested noise reduction OFF and edge OFF and the device reported OFF." (test result, with those qualifiers)
2. "KingShot requests noise reduction OFF and edge OFF only when the camera lists them."
3. "KingShot shows what it requested, what the device reported, and whether they matched."
4. "KingShot captures JPEG on the Pixel 7 and saves it to Pictures/KingShot."
5. "The app has no network permission."

## Claims NOT allowed yet
"minimal processing", "unprocessed", "no processing", "disables the camera's processing", any RAW/DNG claim, any claim about quality versus the stock camera, any claim for other phone models, and any general statement that OFF is honored (three captures in one scene is not that).

## Gaps and next evidence
| Need | Why | How |
|---|---|---|
| DV-003 JPEG+RAW | RAW claims, risks R2/R4, matching for RAW | Round 2, part A in `DEVICE_VALIDATION_REQUESTS.md` |
| DV-002b: more scenes and the unticked default | generality; what the device reports when we do not ask | Round 2, part B |
| DV-004 DNG in an independent tool | DNG claim | after part A |
| Stock-vs-KingShot comparison | any quality or "less processed" claim | `PROTOCOL.md` |
| EXIF ISO vs sensor ISO | interpreting "honored" honestly | new build logs the boost; part B |

Not built yet (planned Phase 1): fakes-based lifecycle tests, instrumented tests, peak-memory and process cold-start benchmarks.

## Risks carried forward
1. OFF being "reported" does not prove the ISP leaves the image alone (see the ISO gap).
2. RAW+JPEG may be unsupported together on this or other devices; RAW-only would be a scope question for the owner.
3. The main-camera choice is evidence from one device at 1x.
4. Name collision: "Kingshot" is an existing mobile game; clearance needed before public use.

## Proposed next milestone
Close the gate with Round 2 results, then write the final Phase 0 report. If DV-003 passes: Phase 1 as planned. If JPEG+RAW is unsupported: ask the owner whether to add a RAW-only mode before Phase 2. Phase 1 does not start until the owner approves the final Phase 0 report.
