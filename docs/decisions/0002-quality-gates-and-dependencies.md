# 0002: Quality gates and dependencies

- Status: accepted
- Date: 2026-10-20
- Trace rows: X4

## Context
The owner's command line asks for ktlint, detekt, Android lint and unit tests. Dependencies need a one-line justification and permissive licenses.

## Decision
- **JUnit 4.13.2** (EPL-2.0 test-only): JVM unit tests for the pure core. No runtime dependencies are added; the app has zero third-party libraries.
- **ktlint via org.jlleitschuh.gradle.ktlint 14.2.0** (MIT, build tool): style gate in CI, `intellij_idea` code style. Cosmetic wrapping rules are disabled in `.editorconfig` with a comment; reason: no local formatter is available here, and those rules only affect layout.
- **detekt deferred.** The only releases that target current Kotlin (2.x) are 2.0.0 alphas (latest alpha.6, August 2026); the owner's rule is "current stable". Android lint, ktlint, the architecture test and unit tests are the gate until detekt 2.0 is stable; then adopt it (task P0-02b).
- **No coroutines/Flow yet.** Phase 0 uses one camera HandlerThread plus one bounded save executor. Coroutines and StateFlow arrive with the Compose ViewModel in Phase 1 (never GlobalScope).
- **No JSON library.** The report only needs to be written, so a small tested writer is used. A parser or kotlinx-serialization (Apache-2.0) is added with an ADR when fixtures need to be read.

## Consequences
CI blocks on style, lint, tests and an INTERNET-permission check. Revisit detekt and the JSON choice when their triggers occur.
