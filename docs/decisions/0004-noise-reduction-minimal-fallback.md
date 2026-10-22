# 0004: Noise-reduction MINIMAL as a fallback for OFF

- Status: accepted for Phase 0 scope (no MINIMAL fallback is implemented); revisit when a device lists OFF as unavailable
- Date: 2026-10-23
- Trace rows: P2, QA04, FM05

## Context
The prompt asks whether `NOISE_REDUCTION_MODE_MINIMAL` is an acceptable fallback when OFF is not listed, to be decided from evidence.
Evidence (DV-001/002, Pixel 7, Android 17): every camera lists OFF, FAST and HIGH_QUALITY, and **no** camera lists MINIMAL. OFF was requested and reported as OFF in 3 of 3 captures.

## Options
1. Never fall back; if OFF is not listed, do not request anything and say so.
2. Fall back to MINIMAL when OFF is unlisted but MINIMAL is listed.

## Decision
Option 1 for now. On the only device measured, the question does not arise (OFF is listed, MINIMAL is not). With no device where OFF is unlisted and MINIMAL is listed, there is nothing to measure, and silently substituting a different mode would blur "requested versus reported". Revisit if a DV run on any phone shows that case; then compare MINIMAL against stock using the Phase 0 protocol before adopting it, and show it in the UI as a distinct request.

## Consequences
The Natural profile requests OFF only when listed and says so when it does not. No MINIMAL code path exists to maintain.
