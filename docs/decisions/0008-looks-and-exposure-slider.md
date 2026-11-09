# 0008: Looks (Pixel 11 style) and the exposure slider

- Status: accepted, requested by the owner on 2026-11-03
- Trace rows: P7 (looks), P8 (exposure slider)

## Context
The owner asked for (1) a brightness slider on the right of the camera screen instead of the EV button, and (2) Pixel 11 "Camera Looks" "with its exact functionality".

Public reporting (DPReview, Android Central, tested on Pixel 11 series) describes Camera Looks as two layers chosen before the shot:
- **Base looks** (Original, Natural, Shadows, Vanilla): said to change how the camera collects and processes light (HDR merge strength, shadow depth, edge sharpening) inside Google's pipeline on the Tensor G6 ISP, "not filters".
- **Creative presets** (Editorial, Velvet, Classic, Digi, Black Tie, Minimal): colour looks on top of a base look, described as emulating film chemistry, digital eras or monochrome.
- **Controls:** Contrast, Highlights, Shadows, Vibrance, Tint, Warmth (-10..+10), Sharpness (0..15, default 10), Grain (None, Weak, Moderate, Strong); "Always use selected look" keeps the look when the camera reopens.

## What KingShot can match exactly
The structure and numbers above: the two layers, the names, the control set and its ranges, the sharpness default of 10, the four grain steps, and "Always use selected look". These are tested in `LookMathTest`.

## What KingShot cannot match, and says so
- Google's base looks run inside Google's closed capture pipeline. Camera2 gives third-party apps no access to HDR merge strength or Google's ISP tuning, so a base look here is a set of tone and sharpness adjustments applied by KingShot to a copy of the saved JPEG, not a change in how the frame is captured.
- Google's preset colour tables are not public. KingShot's presets are its own parameter recipes with the same names. They will not look identical to Google's.
- The viewfinder preview is a colour matrix: it shows warmth, tint, contrast, black lift, saturation and monochrome. Shadows, highlights, sharpness and grain appear only in the saved copy.
- Processing happens after capture, on the phone's JPEG. The JPEG has already been processed by the device, so a look is a second edit, never "unprocessed".

## Decision
- Looks are pure Kotlin maths (`looks/LookMath.kt`, JVM-tested) plus a thin Android renderer (`looks/LookRenderer.kt`).
- A look never changes the original. It writes a new JPEG named `<original stem>_<look>.jpg` beside it (the pair rule in CLAUDE.md). The orientation tag is copied from the original; other EXIF fields are not.
- Renders run one at a time on a worker with a bounded queue (8). If the queue is full the look is skipped and logged; the original is always saved.
- The default (base) look is remembered. The preset and sliders are remembered only with "Always use selected look", matching the Pixel behaviour described above.
- No permission, no network, no new library. Nothing here deletes media.
- The exposure slider uses the camera's own exposure-compensation range and step; it is hidden when the camera reports none. Double tap resets to 0. TalkBack gets a range with set-progress support.

## Consequences
- Unverified on a device: slider feel and placement, preview filter, render time and memory on a full-size Pixel JPEG, orientation of the copy, look appearance.
- A renderer limit of 24 megapixels (larger photos are rendered at half size and the log says so).
- Possible later work: GPU live preview of all controls, applying a look to the DNG through a platform renderer (ADR 0006), user-tuned presets.

## Update 2026-11-03 (v0.7.0): designed colour tables and swipe switching
- Google's tables are not public, so KingShot designs its own. Each preset is a recipe in OKLab (perceptual lightness and colour): an S-curve on lightness with black lift and white drop, overall and per-hue colourfulness and hue shifts, shadow and highlight tints, and a gamut-mapping step that gives up colour at constant lightness and hue instead of clipping channels. The recipes (`looks/LookTables.kt`) are baked into 33x33x33 tables and read with trilinear interpolation. The same maths was prototyped in Python on a real photograph and a colour chart to judge the looks by eye before porting; the Kotlin is tested for grey-axis monotonicity, neutral Black Tie, faded Classic blacks, valid colours, skin staying warm, and the preview fit.
- The recipes were designed by eye on one indoor photo and a chart, not tuned across many scenes or skin tones; expect to adjust them after you shoot with them.
- Order: base look and sliders first, then the preset's table, then grain. Presets no longer add slider offsets.
- Switching: swipe the viewfinder horizontally (64 dp, mostly horizontal) to move through Original and the six presets; tap-to-focus is unchanged. TalkBack gets Next look / Previous look actions. The name chip at the top opens the adjust panel.
- The viewfinder preview is a 4x5 colour matrix fitted by least squares to the real pipeline. It follows colour balance and saturation but cannot bend a curve, so it is least accurate for Digi (fit error about 0.15 in sRGB units on a colour grid).

## Update 2026-11-09 (v0.8.0): presets replaced by the owner's iPhone-style sheet
- The owner found the six OKLab presets repetitive (each looked like the previous one with a little more or less of the same effect) and supplied nine Lightroom-style presets (Vivid, Vivid Warm, Vivid Cool, Dramatic, Dramatic Warm, Dramatic Cool, Mono, Silvertone, Noir) to use instead. The sheet says they are practical approximations of the iPhone filters, not Apple's values.
- The sheet's numbers are used as given. How a unit becomes pixels is KingShot's own choice (`LookTables.transform`): exposure in stops; temperature and tint as channel gains in linear light (1.2% and 0.8% per unit, chosen so Warm and Cool differ visibly); highlights, shadows, whites and blacks as smooth lightness adjustments; contrast as an S-curve; vibrance and saturation on chroma, with -100 / -100 meaning monochrome; the three colour wheels as OKLab tints weighted by lightness, with balance moving the crossover, and gamut mapping. Lightroom's own scaling is not public, so a given number will not match Lightroom exactly.
- Texture is added to the Sharpness unsharp mask. Clarity is local contrast against a block-averaged soft copy (`LocalContrast`).
- The nine are three families (Vivid, Dramatic, Mono) with variants that differ mainly in temperature and colour grading. That structure comes from the sheet, so Vivid / Warm / Cool will share a character by design.
- Dramatic and Noir deliberately crush blacks (Blacks -22 / -35). On deep skin tones that darkens the subject a lot (a test skin tone drops from 0.24 to 0.17 and 0.12 in lightness); this is the preset doing what it says, not a bug. Use the Shadows slider or exposure compensation to open it up.
- The earlier OKLab designed presets are removed. Photos already saved with them are unchanged.
