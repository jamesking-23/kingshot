# Device Validation Requests, Phase 1

Same install steps and preconditions as `docs/phase0/DEVICE_VALIDATION_REQUESTS.md`. The main screen now has the camera controls; the old Lab tools are under the "Lab" button (top right). Nothing below has been run on a device by me; every item is **unverified** until you report back.

## DV-101: preview, capture, saving
- **Why:** trace PH1, UI1, M1, QA01.
- **Steps:** open the app, allow camera. Check the preview is upright and not stretched. Take 5 photos. Open the Photos or Files app and look in `Pictures/KingShot`.
- **Report:** do the 5 JPEGs exist; are they upright (portrait and landscape: rotate the phone for 2 of them); any crash or freeze.

## DV-102: lens strip and zoom labels
- **Why:** trace UI3, CF10 (lens labels). The strip is built from the camera's own reported sensor sizes, focal lengths and zoom range.
- **Steps:** tap each button in the lens strip and look at the preview. Then take one photo at each position and open the Lab panel log.
- **Report:** the labels shown (for example "0.7× lens", "1× lens", "2× crop"); whether the picture really changes view when a "lens" button is tapped; whether a "crop" button looks like a zoomed-in version of the 1× view; the log line showing the active physical camera ID for each photo.
- **Decision rule:** if a "lens" button does not change to a different lens, or a "crop" button switches lenses, the labelling logic is wrong for this device and I will fix it before anything else.

## DV-103: exposure compensation
- **Steps:** point at a mid-grey scene. Press + four times, then − eight times, watching the preview. Take a photo at +1 and one at −1.
- **Report:** does the preview get brighter then darker; the EV label; the "Exposure" line in the log for the two photos (the receipt records the requested EV step count).

## DV-104: flash (rear camera, if your phone shows the Flash button)
- **Steps:** in a dim room, set Flash to auto, take a photo; set it to on, take a photo; set it to off, take a photo.
- **Report:** whether the flash fired each time; whether there is a visible delay between pressing the shutter and the photo; any "AE precapture did not settle" line in the log.

## DV-105: tap-to-focus
- **Steps:** place one near object and one far object in view. Tap the near one, wait, then tap the far one.
- **Report:** does the ring appear where you tapped; does focus and brightness change to match the tapped object; does focus stay on it or drift back.

## DV-106: timer, grid, level
- **Steps:** Timer 3 s: press shutter and watch the countdown; press shutter again during the countdown to cancel. Grid on/off. Level on, tilt the phone left and right, then lay it flat on a table.
- **Report:** countdown numbers shown; whether cancelling works; grid lines look like thirds; the level line turns green when you hold the phone straight; the level line disappears when flat; whether tilting right moves the line the way you expect (this sign convention is unverified).

## DV-107: low-light readout
- **Steps:** in a dim room take a photo with no flash.
- **Report:** the message under the preview, and the exposure time and ISO lines in the log. The message must only repeat what the camera measured; tell me if it ever says anything else.

## DV-108: lifecycle
- **Steps:** (a) press Home during preview, then return; (b) lock the screen and unlock; (c) switch to another app and back; (d) take a photo, then immediately press Home.
- **Report:** whether the preview recovers each time and how long it takes (the log shows "Resume to first preview frame"); any black screen, freeze or crash; whether the photo from (d) still appears in `Pictures/KingShot`.

## DV-109: interruptions and storage (do after the others)
- **Steps:** (a) receive or place a phone call while the camera is open, then return; (b) open the camera in another app, then come back; (c) revoke camera permission in Settings while the app is open; (d) only if easy: fill storage nearly full and take a photo.
- **Report:** what the app shows in each case; any crash; for (d) whether the app says the save failed.
