# Phase 1 report: core capture

**Gate: OPEN.** Everything below is built and CI-green; nothing in the new UI has run on a device. The gate closes when DV-101..DV-109 (docs/phase1/DEVICE_VALIDATION_REQUESTS.md) come back.

## Done
- Compose UI (foundation + ui only) with ViewModel/StateFlow; the Phase 0 Lab tools live under the "Lab" button.
- Controls, each checked against runtime CameraCharacteristics and hidden when unsupported: lens/zoom strip (optical "lens" vs "crop" labels from sensor size, focal length and the reported zoom range), flash off/auto/on with AE precapture, self-timer 3/10 s with cancel, exposure compensation, tap-to-focus/expose, grid, level.
- Natural profile (requests NR/Edge OFF only where listed; disclosure says the JPEG may still be processed). Shown as "Profile: Natural" or "Custom" when the Lab toggles differ.
- Low-light readout built only from the capture result's exposure time and ISO. No Night mode exists and none is implied.
- Thumbnail, "Recent photos" list, open in the system viewer. Settings (grid, level, timer, NR/Edge requests) persist in DataStore.
- Camera open/close decision extracted as `OpenGate` and unit-tested for pause, resume, surface loss and permission changes.

## Verified
- CI: ktlint, lint, unit tests, debug APK, INTERNET-permission gate (permissions: CAMERA only).
- JVM tests: EvMath, LensMath, MeteringMath, LevelMath, Countdown, LowLightAdvisor, ControlPlanner, NaturalProfile, OpenGate.
- On recorded Pixel 7 data: the sensor-derived ultrawide ratio (0.66) matches the phone's reported minimum zoom (0.6704); EV range is ±4.0 EV.

## Unverified (needs a device)
- All UI behavior, flash timing and precapture, tap-to-focus, the level's sign convention, lens switching and labels, orientation of saved photos, thumbnail loading.
- Interruption handling (calls, permission revoked, camera taken by another app) and storage exhaustion: the code paths exist for save failure (pending item left, failure reported) but no real low-storage test has been run.
- Whether the Natural JPEG is less processed than the stock camera: not claimed; Phase 0 Round 2 comparison still outstanding.

## Known gaps
- Receipts still record the profile name as "lab", not "Natural"/"Custom".
- The active physical camera ID is logged from capture results but is not yet shown on screen.
- No Compose UI tests or instrumented tests (no emulator in CI).
- Interruption matrix and storage-exhaustion behavior are requests only (DV-108, DV-109).
- Sensor-derived "equivalent focal length" is an estimate; only one device's data supports it.

## Needs from me
Run DV-101..DV-109 on the Pixel and send the bundle plus notes. Phase 0 Round 2 results are still outstanding too.

## Next
Fix whatever the device tests show. Then Phase 2 (DNG reliability, profile persistence, receipts with profile names, fallback UI) per docs/ROADMAP.md, unless you decide otherwise.
