# Phase 0 comparison protocol

Goal: learn, from your Pixel, which processing requests the device honors and how KingShot output compares with the stock Camera.
Nothing here proves a "minimal processing" claim until DV-002 and the comparison below are filled in and reviewed.
Describe results in the words of the report ("requested", "reported", "honored", "differs"); never "unprocessed" or "no processing".

## Methods compared
| Code | Method | Notes to record |
|---|---|---|
| `stock` | Stock Camera app, default photo mode, default settings (note whether RAW/Pro is on) | app version, mode, any HDR/Night toggles |
| `ksjpeg` | KingShot Lab JPEG, with "Request NR OFF" and "Request Edge OFF" ticked (only if listed) | the receipt's control statuses |
| `ksdng` | KingShot Lab DNG (tick RAW), rendered in an independent RAW tool | tool name, version, and every setting (white balance, profile, sharpening, noise reduction) |

Independent RAW tool: RawTherapee, darktable, Lightroom or LibRaw `dcraw_emu`. Use the same tool and settings for every DNG. Write the settings down; they decide the result as much as the device does.

## Scenes (blueprint section 10)
portrait, bright daylight, backlit faces, fine textures, night, mixed lighting, motion.

## Controls
- Tripod, same framing, same time of day per scene. (Motion: handheld pan is fine; say so.)
- At least **3 trials per scene and method**. Name files `scene_method_trial`, e.g. `finetex_ksdng_2.dng`. Keep originals untouched; work on copies.
- Battery above 50 percent, free storage above 2 GB, stock camera updated, phone not hot (note the thermal status KingShot logs).

## Record for every shot
Use `scoring_template.csv`. For KingShot shots copy from the receipt: requested and reported NR/Edge, exposure time, ISO, shutter-to-saved ms, processing ms, thermal status. Also note memory feel/lag, storage used, and any crash or freeze.

## Scoring (per shot, 1 to 5, blind if you can: have someone rename files before you score)
Detail, noise (luma/chroma), sharpening halos, color, dynamic range, overall preference. Add free text for artifacts.

## Outputs
1. `scoring_template.csv` filled in.
2. `REPORT.md` (copy `REPORT_TEMPLATE.md`) filled in.
3. The diagnostics bundle(s) from each DV request.
Send those back. I will analyze them and say what the data proves and what it does not.
