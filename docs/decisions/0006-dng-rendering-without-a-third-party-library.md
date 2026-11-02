# 0006: DNG rendering without a third-party library

- Status: accepted
- Date: 2026-11-02
- Trace rows: P3, Phase 3

## Context
Phase 3 planned "a licensed RAW rendering library". The owner's rule: if there is no free-licensed renderer, leave it. The project rules also require asking before adding a non-permissive license or a native library.

## Options
1. LibRaw (LGPL / CDDL, native), RawTherapee or darktable (GPL, native): capable, but copyleft and native code.
2. dcraw-style ports: old, unmaintained, mixed licensing.
3. The Android platform's own DNG decoding (`ImageDecoder` / `BitmapFactory` on a DNG file): part of the OS, no extra license or library, no native code shipped by KingShot; quality and colour handling are the vendor's.
4. Leave rendering out.

## Decision
No third-party RAW library. The DNG files KingShot writes stay as the preserved source. If a rendered copy is offered later, it uses option 3 and is saved as a separate paired file, never over the source. Multi-frame HDR fusion and denoising stay unscheduled (Later), as in the feature matrix.

## Consequences
- No new license, permission or native library.
- A platform-rendered copy will look as the phone renders DNGs, not as a dedicated RAW engine would; it is a convenience, not a claim of quality.
- Nothing about rendering is verified on a device yet.
