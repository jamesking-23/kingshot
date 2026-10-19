# Test plan

| Layer | What | Tooling | Runs in CI |
|---|---|---|---|
| JVM unit | Capability registry decisions (fixtures from real devices), profile validation, request builders, timestamp matcher (bounded, timeout, orphan, exactly-once close), receipt builder, pairing logic, storage-failure handling, orientation math | JUnit + kotlinx-coroutines-test (add with justification) | yes |
| Architecture | Dependency direction between packages | JVM test | yes |
| Static | Android lint, ktlint, detekt, merged-manifest INTERNET/permission gate | Gradle + CI step | yes (ktlint/detekt after P0-02) |
| Instrumented | Permissions, lifecycle, MediaStore writes | androidx.test on an emulator | later (needs emulator job; ask before adding cost/complexity) |
| Compose UI | Capability-aware controls (hidden or disabled with reason), 48dp targets, large fonts | Compose test | Phase 1+ |
| Device validation | Everything that depends on the HAL: NR/Edge honored, RAW+JPEG sessions, timestamp equality, DNG validity, latency, thermal, memory | Device Validation Requests DV-nnn, run by owner | no (manual) |
| Benchmarks | Shutter latency, processing time, memory, storage, thermal, crashes, cold start to first frame | harness + docs/benchmarks/ | no (manual) |

Rules: tests first for JVM logic; a regression test with every bug fix; no mocks pretending to be camera hardware behaviour;
trace status is at most `unit-verified` until a DV result is recorded in docs/VALIDATION_LOG.md.
Blueprint §10 acceptance tests are rows QA01-QA11 in the trace.
