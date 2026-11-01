# Phase 2 report: RAW and processing control (software side)

**Gate: OPEN.** Needs Phase 0 DV-003/DV-004 and DV-201..203 from a device.

## Done
- Receipts carry the real profile (Natural or Custom); a plain-language "Details" view per photo built from the receipt (requested, reported, unavailable, exposure, disclosure).
- Receipts persist in app-private storage (ADR 0005); older ones are shown as stored JSON text. Nothing in the store can delete.
- PairNaming: JPEG and DNG pair by shared stem; duplicates and MediaStore auto-renames are detected, never overwritten. ID uniqueness tested (1000 IDs in one millisecond).
- RAW status line explains whether RAW is offered, active, requested-but-JPEG-only, or off. Log states which camera's characteristics fed the DNG.
- Natural/Custom settings persist (done in Phase 1 via DataStore).
- New pure files are guarded by the no-Android-imports architecture test.

## Verified
CI green (ktlint, lint, unit tests, APK, no INTERNET). Unit tests: ReceiptSummary, ReceiptStore (temp folder), PairNaming/ID uniqueness.

## Unverified
Everything on a device: DNG validity in an independent RAW tool, JPEG+RAW stability under rapid shooting, whether logical-camera characteristics are right for the DNG when another physical camera is active.

## Known gaps
- Recent photos list is not rebuilt from MediaStore after a restart.
- Pairing is not shown in the list (stems are named alike but no "JPEG + DNG" badge yet).
- DNG with physical-camera characteristics deliberately not changed without device evidence.
- Pair reconciliation is naming-based only; no instrumented MediaStore test (no emulator in CI).

## Needs from me
Phase 0 DV-003/DV-004, then DV-201..203; plus the earlier DV-101..109.

## Next
Phase 3 starts only after your RAW-library decision (licensed renderer). Until device results arrive I will fix issues reported and tidy the known gaps above.
