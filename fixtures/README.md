# Recorded device fixtures

Real capability reports exported by the Camera Lab bundle (`capability_report.json`), used by JVM tests so decisions are
checked against what real devices reported. They hold device/build identifiers (model, build fingerprint) and camera data only:
no photos, no personal data. Add one per validated device; never edit values by hand.

| File | Device | Android | Source |
|---|---|---|---|
| `capabilities/google_panther_android17_report.json` | Google Pixel 7 | 17 (SDK 37), patch 2026-10-05 | DV-001, 2026-10-22, build 0.2.0 debug |
