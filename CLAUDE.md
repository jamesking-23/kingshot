# KingShot: rules for Claude

Android camera app, Pixel first. Source of truth: docs/BLUEPRINT.md. Progress ledger: docs/REQUIREMENTS_TRACE.md.

## Non-negotiables
- Truthfulness: show requested against reported against unavailable. Never call a JPEG "unprocessed".
- Capabilities come from runtime CameraCharacteristics, never model names.
- Request noise reduction OFF and edge OFF only if listed as available modes.
- Never overwrite a source capture. Originals and rendered files are paired.
- Show a control only when the active camera supports it.
- No INTERNET permission and no analytics. AI or cloud features need my explicit approval.
- Do not claim device behavior you have not seen. Mark it unverified and write a Device Validation Request.
- Close every Image exactly once, keep queues bounded, match images to results by timestamp.

## Commands
./gradlew ktlintCheck lintDebug testDebugUnitTest assembleDebug
(detekt joins this line when a stable release supports current Kotlin; see docs/decisions/0002.)

## Workflow
Tests first for logic that runs on a JVM. Small commits. Update the trace. Report as: Done, Verified, Unverified, Needs from me, Next.

## Ask me before
Adding a permission, a network call, a non-permissive license or a native library; changing scope or phase order; writing any code that can delete media.
