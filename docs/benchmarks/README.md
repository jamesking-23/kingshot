# Benchmarks

Phase 0 measures only what the Lab already logs. Every number is a **baseline from one device**, not a budget.

| Metric | Where it appears | Source |
|---|---|---|
| Shutter to saved (ms) | receipt `timing.shutterToSavedMs`, log "Receipt ready" | tap to last output saved |
| Image processing time (ms) | receipt `timing.processingMs` | first image arrival to last output saved |
| Thermal status | receipt `thermalStatus` | platform thermal status at completion |
| Java / native heap (MB) | log "Memory:" line after each capture | Runtime and Debug APIs |
| Resume to first preview frame (ms) | log line after each resume | includes a permission prompt if one is shown |
| Storage used | file sizes in the log ("Saved JPEG ... KB") | MediaStore saves |
| Crashes / freezes | your notes in the scoring CSV | manual |

Provisional budgets: none until Phase 0 results exist. Peak memory and a proper cold-start (process start) measurement need an
instrumented benchmark and are scheduled with the Phase 1 lifecycle tests.
