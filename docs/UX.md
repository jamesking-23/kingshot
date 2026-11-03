# UX notes (v0.4.0)

Goal: look and behave like a normal camera app, with KingShot's honesty rules visible but out of the way.

## Layout
- **Top bar:** flash (only if the camera has one), timer, grid, level, settings. Icons are drawn in code (no icon library). Each has a spoken label and, for on/off items, a state.
- **Preview:** fills the space between the bars at the sensor's aspect ratio. Tap to focus (ring shows). Chips: low-light readout (what the camera measured), the active look. A vertical brightness slider sits on the right edge (drag up/down, double tap resets); its range comes from the camera.
- **Zoom buttons over the preview:** one per position the camera reports. A caption under them says exactly what the selected position is: "lens", "sensor crop" or "digital crop". Never "optical" for a crop.
- **Bottom bar:** latest photo (opens in your viewer), shutter (press animation, haptic tick, screen dip on capture; turns red and cancels during the self-timer), nothing on the right (no Looks button). Swipe the viewfinder left or right to change the look; tap the look name at the top to adjust (default look, presets, sliders, grain, "Always use selected look").
- **Settings (full screen):** photo processing requests and the plain-language disclosure, RAW, recent photos with receipt Details, diagnostics (self-check, export bundle, log), about and privacy.
- **Camera permission denied:** an explanation, "Allow camera" and "Open settings". The app asks once per launch while undecided, not on every resume.

## Rules kept
- A control is hidden or disabled with a reason when the camera does not report it (flash, exposure, RAW, noise reduction, edge).
- Wording never says "unprocessed"; the processing text says the phone decides and the JPEG may still be processed.
- Touch targets are at least 48 dp; text uses sp so it follows the system font size; the screen is kept clear of the status bar, navigation bar and cutout.

## Known gaps
- Not seen on a device or emulator yet: spacing, clipping at large font sizes and the look on small screens are unchecked.
- Portrait only. Some messages built in code (toasts, log, self-check results) are English only; layout text is in `strings.xml` and ready for translation.
- No on-screen focus lock, no pro manual controls, no video (later phases).
