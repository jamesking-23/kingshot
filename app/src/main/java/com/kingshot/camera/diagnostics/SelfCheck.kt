package com.kingshot.camera.diagnostics

enum class CheckResult { PASS, FAIL, SKIPPED }

data class Check(val name: String, val result: CheckResult, val detail: String)

/** What the scripted run tried, so the grader only judges things that were attempted. */
data class SelfCheckPlan(val ev: Boolean, val zoomStops: Int, val rawShots: Int)

/** Grades a scripted run from its Processing Receipts. It can only judge what the receipts record; what a photo looks like needs a human. */
object SelfCheck {
    fun evaluate(receipts: List<ProcessingReceipt>, plan: SelfCheckPlan): List<Check> {
        if (receipts.isEmpty()) return listOf(Check("Photos taken", CheckResult.FAIL, "No receipts were produced by the run."))
        val out = mutableListOf<Check>()
        val unsaved = receipts.filter { r -> r.outputs.none { it.role == "jpeg" } }
        out += if (unsaved.isEmpty()) {
            Check("JPEG saved for every photo", CheckResult.PASS, "${receipts.size} photos, each with a saved JPEG.")
        } else {
            Check("JPEG saved for every photo", CheckResult.FAIL, "Missing for ${unsaved.joinToString { it.captureId }}.")
        }
        val differing = receipts.flatMap { r ->
            r.controls.filter { it.status == ControlStatus.DIFFERS }.map { "${r.captureId} ${it.name}" }
        }
        out += if (differing.isEmpty()) {
            Check(
                "Processing requests match what the device reported", CheckResult.PASS,
                "No request came back different. This does not prove untouched pixels."
            )
        } else {
            Check("Processing requests match what the device reported", CheckResult.FAIL, "Differed: ${differing.joinToString()}.")
        }
        out += if (plan.ev) {
            val steps = receipts.mapNotNull { it.exposure.evCompSteps }.toSet()
            if (steps.size >= 2) {
                Check("Exposure compensation applied", CheckResult.PASS, "Results reported EV steps ${steps.sorted()}.")
            } else {
                Check("Exposure compensation applied", CheckResult.FAIL, "Results reported EV steps $steps; expected at least two different values.")
            }
        } else {
            Check("Exposure compensation applied", CheckResult.SKIPPED, "This camera reports no exposure compensation.")
        }
        out += if (plan.zoomStops >= 2) {
            val ratios = receipts.mapNotNull { it.zoomRatio }.map { Math.round(it * 10) / 10f }.toSet()
            if (ratios.size >= plan.zoomStops) {
                Check("Zoom positions applied", CheckResult.PASS, "Results reported zoom ratios ${ratios.sorted()}.")
            } else {
                Check("Zoom positions applied", CheckResult.FAIL, "Results reported ${ratios.sorted()} for ${plan.zoomStops} requested positions.")
            }
        } else {
            Check("Zoom positions applied", CheckResult.SKIPPED, "Fewer than two zoom positions on this camera.")
        }
        val physical = receipts.mapNotNull { it.activePhysicalId }.toSet()
        val lensResult = if (physical.isEmpty()) CheckResult.SKIPPED else CheckResult.PASS
        out += Check("Active lens reported", lensResult, "Active physical cameras seen: ${physical.sorted()}.")
        out += if (plan.rawShots > 0) {
            // RAW was switched on for the last [rawShots] photos of the run; earlier photos were JPEG only by design.
            val rawRun = receipts.sortedBy { order(it) }.takeLast(plan.rawShots)
            val missing = rawRun.filter { r -> r.outputs.none { it.role == "source_raw" } }
            if (missing.isEmpty()) {
                Check("DNG saved with every RAW-on photo", CheckResult.PASS, "All ${rawRun.size} RAW-on photos have a DNG. Whether it opens correctly still needs a RAW viewer.")
            } else {
                Check("DNG saved with every RAW-on photo", CheckResult.FAIL, "No DNG for ${missing.joinToString { it.captureId }}.")
            }
        } else {
            Check("DNG saved with every RAW-on photo", CheckResult.SKIPPED, "RAW was not offered or not used.")
        }
        val times = receipts.mapNotNull { it.shutterToSavedMs }.sorted()
        out += if (times.isEmpty()) {
            Check("Shutter to saved time", CheckResult.SKIPPED, "Not recorded.")
        } else {
            Check("Shutter to saved time", CheckResult.PASS, "Median ${times[times.size / 2]} ms, worst ${times.last()} ms.")
        }
        out += Check("Flash, focus, level, picture quality", CheckResult.SKIPPED, "These need a person looking at the phone; not graded here.")
        return out
    }

    private fun order(r: ProcessingReceipt): Int = r.captureId.substringAfterLast('_').toIntOrNull() ?: 0

    fun text(checks: List<Check>): String = checks.joinToString("\n") { "[${it.result}] ${it.name}: ${it.detail}" }
}
