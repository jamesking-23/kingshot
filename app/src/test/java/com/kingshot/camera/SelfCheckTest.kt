package com.kingshot.camera

import com.kingshot.camera.diagnostics.CheckResult
import com.kingshot.camera.diagnostics.ControlOutcome
import com.kingshot.camera.diagnostics.ControlStatus
import com.kingshot.camera.diagnostics.ExposureInfo
import com.kingshot.camera.diagnostics.OutputRecord
import com.kingshot.camera.diagnostics.ProcessingReceipt
import com.kingshot.camera.diagnostics.SelfCheck
import com.kingshot.camera.diagnostics.SelfCheckPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SelfCheckTest {
    private fun r(id: String, ev: Int, zoom: Float, raw: Boolean = false, status: ControlStatus = ControlStatus.HONORED, jpeg: Boolean = true) =
        ProcessingReceipt(
            id, "t", "Natural", "0", "2", zoom,
            listOfNotNull(
                if (jpeg) OutputRecord("jpeg", "JPEG", "u", "1x1", 10) else null,
                if (raw) OutputRecord("source_raw", "RAW_SENSOR", "u", "1x1", 10) else null
            ),
            listOf(ControlOutcome("EDGE_MODE", "OFF", "OFF", status)), ExposureInfo(evCompSteps = ev), emptyList(), 400, 100, "NONE", "p"
        )

    private fun result(checks: List<com.kingshot.camera.diagnostics.Check>, name: String) = checks.first { it.name.startsWith(name) }.result

    @Test fun emptyRunFails() {
        assertEquals(CheckResult.FAIL, SelfCheck.evaluate(emptyList(), SelfCheckPlan(true, 3, false)).single().result)
    }

    @Test fun goodRunPasses() {
        val c = SelfCheck.evaluate(listOf(r("1", 0, 1f), r("2", 6, 0.7f), r("3", 0, 2f)), SelfCheckPlan(true, 3, false))
        assertEquals(CheckResult.PASS, result(c, "JPEG saved"))
        assertEquals(CheckResult.PASS, result(c, "Processing requests"))
        assertEquals(CheckResult.PASS, result(c, "Exposure"))
        assertEquals(CheckResult.PASS, result(c, "Zoom"))
        assertEquals(CheckResult.SKIPPED, result(c, "DNG"))
    }

    @Test fun differingControlFails() {
        val c = SelfCheck.evaluate(listOf(r("1", 0, 1f, status = ControlStatus.DIFFERS)), SelfCheckPlan(false, 0, false))
        assertEquals(CheckResult.FAIL, result(c, "Processing requests"))
    }

    @Test fun evThatNeverChangedFails() {
        val c = SelfCheck.evaluate(listOf(r("1", 0, 1f), r("2", 0, 1f)), SelfCheckPlan(true, 0, false))
        assertEquals(CheckResult.FAIL, result(c, "Exposure"))
    }

    @Test fun zoomThatDidNotChangeFails() {
        val c = SelfCheck.evaluate(listOf(r("1", 0, 1f), r("2", 0, 1f)), SelfCheckPlan(false, 2, false))
        assertEquals(CheckResult.FAIL, result(c, "Zoom"))
    }

    @Test fun missingDngFailsWhenRawWasUsed() {
        val c = SelfCheck.evaluate(listOf(r("1", 0, 1f, raw = true), r("2", 0, 1f, raw = false)), SelfCheckPlan(false, 0, true))
        assertEquals(CheckResult.FAIL, result(c, "DNG"))
    }

    @Test fun missingJpegFails() {
        assertEquals(CheckResult.FAIL, result(SelfCheck.evaluate(listOf(r("1", 0, 1f, jpeg = false)), SelfCheckPlan(false, 0, false)), "JPEG saved"))
    }

    @Test fun neverGradesWhatNeedsEyes() {
        val c = SelfCheck.evaluate(listOf(r("1", 0, 1f)), SelfCheckPlan(false, 0, false))
        assertEquals(CheckResult.SKIPPED, c.last().result)
        assertFalse(SelfCheck.text(c).contains("unprocessed", ignoreCase = true))
    }
}
