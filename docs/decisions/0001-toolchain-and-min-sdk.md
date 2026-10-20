# 0001: Toolchain versions and minimum SDK

- Status: accepted (owner chose scope on 2026-10-20: Android 14 and later, all Pixel models, other Android phones supported)
- Date: 2026-10-20
- Trace rows: X4, P4

## Context
Owner requirement: works on all Pixel models and, as far as capabilities allow, any Android phone, on Android 14 and every later version. Pixel is the emphasis because of its heavy processing. Versions checked on developer.android.com (AGP release notes, 2026-10-19) and plugins.gradle.org:
- AGP 9.4.0 is the newest stable: needs Gradle 9.6.0, JDK 17, SDK Build Tools 36.0.0, supports up to API 37. AGP 9 has built-in Kotlin (no Kotlin Gradle plugin applied; AGP pulls KGP 2.2.10 or newer).
- Kotlin 2.4.0 exists (June 2026); we use AGP's bundled KGP for now to avoid an untested AGP/KGP pairing. Revisit in P0-01 follow-up.
- Android 17 stable is rolling out on Pixel; we compile against API 36 (installed on CI runners) and keep targetSdk 36 until the API 37 behavior changes are reviewed.

## Options considered
1. minSdk 31 (prompt default). Needless complexity: owner wants Android 14+.
2. **minSdk 34 (Android 14).** Every Camera2 API we use is present, so no API-level guards are needed; the code path is the same on every supported phone.
3. minSdk 29. Rejected: contradicts the owner's scope and adds guard code for versions we will not support.

## Decision
minSdk 34, compileSdk/targetSdk 36, AGP 9.4.0, Gradle 9.6.0, JDK 17. Capabilities are always read at runtime, so "all Android phones" means every phone runs the same capability-driven code; nothing branches on manufacturer or model. Support on a given phone is unverified until a DV result exists for it.

## Consequences
Phones below Android 14 are out of scope. Raising targetSdk to 37 needs a behavior-change review. Because the owner has only Pixel hardware, non-Pixel behavior stays "unverified on device" until someone runs the DV requests there.
