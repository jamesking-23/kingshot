# Device Validation Requests, Phase 2

Same install and preconditions as Phase 0/1. Unverified until you report back. DV-003 and DV-004 from Phase 0 (RAW+JPEG, DNG validity) are the main Phase 2 gates; the new items below check what changed since.

## DV-201: receipts on screen and after restart
- **Steps:** take 2 photos. Lab -> Recent photos -> Details for each. Force-stop the app, reopen, take no photo, open Lab -> Recent photos (the list starts empty after a restart: expected, see report). Take one more photo and check its Details.
- **Report:** the Details text for each photo (profile name should be Natural, or Custom if you changed the Lab toggles); any "receipt could not be stored" line in the log.

## DV-202: RAW status line and fallback
- **Steps:** Lab -> tick "Also capture RAW + DNG". Read the "RAW:" line. Take 3 photos.
- **Report:** the text of the RAW line; whether each photo produced a .jpg and a .dng with the same name in `Pictures/KingShot`; the log line "DNG uses the characteristics of camera ...".
- **Decision rule:** if the log says an active physical camera differs from the logical camera, DV-004 must check that DNG's color and lens metadata; if it looks wrong I switch DNG creation to that physical camera's characteristics.

## DV-203: pairs after a rapid burst
- **Steps:** with RAW on, press the shutter 6 times quickly.
- **Report:** how many photos were taken versus "shutter ignored" lines; whether every .jpg has a matching .dng; any "unmatched" or "orphan" lines.

(Repeat Phase 0 DV-003 and DV-004 if not yet done: they decide whether the DNG is valid.)
