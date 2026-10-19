# KingShot Lab

Prototype Camera2 "Camera Lab" for Pixel phones (see the KingShot blueprint, v0.1).

What it does: rear-camera preview, capability report (RAW, noise-reduction and edge modes),
JPEG capture, optional RAW + DNG capture, minimal-processing requests only where the device
lists support, and an on-screen log of requested vs. returned values. Files are saved to
`Pictures/KingShot`.

It does **not** claim unprocessed output; JPEGs are always rendered by the vendor pipeline.
Not yet validated on a physical Pixel: JPEG+RAW session combinations and DNG matching need device testing.

## APK
GitHub Actions builds `app/build/outputs/apk/debug/*.apk` on every push to `main`
(Actions tab → latest run → Artifacts → `KingShot-debug-apk`).
Local: `gradle :app:assembleDebug` (JDK 17, Android SDK 34).
