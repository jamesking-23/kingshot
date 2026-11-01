# 0005: Receipt and pairing store

- Status: accepted
- Date: 2026-11-01
- Trace rows: P2, P3

## Context
Processing Receipts were held in memory only, so they were lost when the process ended, and the bundle could only include receipts from the current run. Phase 2 needs receipts to outlive the process and photos to be paired without touching the user's media.

## Options
1. Sidecar JSON next to each photo in Pictures/KingShot.
2. A MediaStore-visible file of another type.
3. App-private files (`filesDir/receipts/<captureId>.json`); pairing by shared file stem and the receipt's `pairing.pairId`.

## Decision
Option 3. Option 1 puts extra files into the user's gallery folder and may be blocked or confusing on scoped storage; option 2 has the same problem. App-private storage needs no permission, never appears in the gallery, and is not touched by the media-saving code, so a receipt write cannot overwrite or delete a photo. The JPEG and DNG of one shot already share a stem (`KS_<date>_<time>_<n>`); `PairNaming` groups them and reports duplicates or MediaStore auto-renames (" (1)") rather than repairing them by overwriting.

## Consequences
- Receipts are lost if the user clears app data or uninstalls; the diagnostics bundle remains the way to export them.
- The store never deletes (no code that can delete media or receipts).
- Older receipts are shown as stored JSON text rather than parsed back into objects (no JSON parser in the app; Gson is test-only).
