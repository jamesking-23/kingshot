# Device Validation Requests, Phase 0

Common to all requests.
- **Build:** the commit shown in the Actions run (I will state it in the hand-off). Install: GitHub repo -> Actions tab -> latest green run of `CI` -> Artifacts -> `KingShot-debug-apk` -> download, unzip, open `app-debug.apk` on the Pixel (allow installs from your browser/files app if asked).
- **Preconditions:** battery above 50 percent, at least 2 GB free storage, stock camera app up to date, normal indoor light with a textured subject (a printed page or bookshelf), phone resting on a table or tripod. The app asks for CAMERA permission only: allow it.
- **Send back:** the bundle zip (Export bundle button, then share it to yourself/me), screenshots of the screen log, and any notes. Tick "Include latest JPEG/DNG in bundle" only if you are happy to share those two photos.

## DV-001: capability report, one shot, timestamp matching
- **Why:** trace rows CF02, CF03, CF07, QA02, QA05, M2; assumption "Image.timestamp equals SENSOR_TIMESTAMP"; assumption "first rear camera is the main camera".
- **Steps:**
  1. Open KingShot Lab. Allow camera. Wait for "Session ready" in the log.
  2. Leave all checkboxes as they are (NR and Edge ticked where enabled, RAW unticked). Tap Capture once.
  3. Wait for "Receipt ready".
  4. Tick "Include latest JPEG/DNG in bundle" (optional). Tap Export bundle and share it.
- **Expected output:** `Pictures/KingShot/KS_<date>_<time>_1.jpg`; bundle with `capability_report.json/.txt`, `capture_log.txt`, `receipts/KS_..._1.json`.
- **Decision rule:** log says "JPEG image matched ... by sensor timestamp" -> assumption holds for JPEG on this device. "unmatched: timeout" -> timestamps differ; I redesign matching (e.g. frame-number/tag based) before any further work. The active camera ID's facing/focal lengths in the report tell us whether the first-rear-camera assumption picked the main lens.

## DV-002: are noise-reduction OFF and edge OFF honored?
- **Why:** trace rows P2, QA04, QA05, FM05; the Phase 0 gate.
- **Steps:** repeat in three scenes (fine texture, bright daylight, night/dim room), three captures each: (a) NR and Edge ticked, (b) both unticked. Handheld or tripod, same framing within a scene.
- **Expected output:** 18 JPEGs and 18 receipts (the bundle holds the receipts and log; samples only the last JPEG).
- **Decision rule:**
  - Receipt says `honored` for NR/Edge -> Natural may *request* OFF on this device, and the UI may say "requested OFF, device reported OFF". It still may not say "unprocessed" or "minimal processing" until DV-004 (comparison) supports it.
  - `differs` -> UI says what the device reported; Natural stops claiming OFF here.
  - `unavailable` (OFF not listed) -> Natural does not request it, and the UI says so. I will then ask whether MINIMAL is a fair fallback (ADR 0004), from your comparison scores.

## DV-003: JPEG + RAW together
- **Why:** trace rows CF04, CF06, FM01, R2, R4, QA03.
- **Steps:** tick "Also capture RAW + DNG" (if enabled). Watch the log for the support-check line. Capture 3 times, 3 seconds apart, then 3 times rapidly.
- **Expected output:** `KS_..._n.jpg` and `KS_..._n.dng` pairs (same stem), receipts with two outputs each. If RAW is disabled in the UI, the reason is shown and this request ends there.
- **Decision rule:** "supported" and session ready and pairs saved -> JPEG+RAW is usable on this device. "NOT supported" / fallback toast -> JPEG-only on this device; RAW would need a RAW-only mode (I will ask before adding it). Orphan or timeout lines -> I harden matching before Phase 2.

## DV-004: DNG validity (after DV-003)
- **Why:** QA03, CF07.
- **Steps:** copy one `.dng` to a computer; open it in the independent RAW tool; check orientation, that exposure/ISO metadata show, and colors look sane after default rendering.
- **Decision rule:** opens and looks right -> DNG path may be called "opens in <tool> on <device>". Otherwise attach the file; I will check metadata and orientation handling.

---
# Round 2 (after the first Pixel 7 results)
Install the newest APK from the latest green CI run (it now records `postRawSensitivityBoost`). Everything under "Common to all requests" still applies.

## Part A: DV-003 JPEG + RAW together (RAW box ticked)
1. Open the app, wait for "Session ready". Tick "Also capture RAW + DNG". Read the log: it says whether the device reports the combination as supported, and the status line shows whether the session is "preview + JPEG + RAW".
2. Tap Capture 3 times, 3 seconds apart. Then 3 times quickly in a row.
3. Tick "Include latest JPEG/DNG in bundle", tap Export bundle, share it.
Decision rule as in DV-003 above. If the RAW box is greyed out or switches itself off, send a screenshot with the log: that is the answer.

## Part B: DV-002b more scenes and the unticked default
In a **bright, textured scene** (window light or outdoors, same framing for all four): untick RAW, then take 2 captures with NR and Edge ticked and 2 with both unticked. Repeat once in a **dim** scene if you can. Export a second bundle.
- **Why:** shows what the device reports when we do not ask (the receipt's `not_requested` rows show the default modes), whether OFF is honored in other light, and the sensitivity boost value for the ISO question.
- **Decision rule:** `honored` throughout, with different reported modes when unticked -> the request visibly changes the device's mode on this phone (still not a "minimal processing" claim). Same reported mode ticked or unticked -> the request makes no reported difference; I will say so.

## Send back
Both bundle zips, plus screenshots of any toast or message. If you can, tell me which APK run you installed.
