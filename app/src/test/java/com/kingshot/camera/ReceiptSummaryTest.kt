package com.kingshot.camera

import com.kingshot.camera.diagnostics.ControlOutcome
import com.kingshot.camera.diagnostics.ControlStatus
import com.kingshot.camera.diagnostics.Disclosures
import com.kingshot.camera.diagnostics.ExposureInfo
import com.kingshot.camera.diagnostics.OutputRecord
import com.kingshot.camera.diagnostics.ProcessingReceipt
import com.kingshot.camera.diagnostics.ReceiptSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptSummaryTest {
    private fun receipt(controls: List<ControlOutcome>, exposure: ExposureInfo = ExposureInfo(8_000_000L, 100)) = ProcessingReceipt(
        "KS_1", "2026-11-01T10:00:00Z", "Natural", "0", "2", 1f,
        listOf(OutputRecord("jpeg", "JPEG", "content://x", "4080x3072", 2048 * 1024L)),
        controls, exposure, listOf(Disclosures.JPEG_MAY_BE_PROCESSED), 500, 120, "NONE", "pair1"
    )

    @Test fun showsRequestedReportedAndStatus() {
        val t = ReceiptSummary.text(receipt(listOf(ControlOutcome("NOISE_REDUCTION_MODE", "OFF", "OFF", ControlStatus.HONORED))))
        assertTrue(t.contains("requested OFF, device reported OFF"))
        assertTrue(t.contains("honored by the device"))
        assertTrue(t.contains("profile Natural"))
        assertTrue(t.contains("active lens camera 2"))
        assertTrue(t.contains("1/125 s"))
    }

    @Test fun unavailableIsSaidPlainly() {
        val t = ReceiptSummary.text(receipt(listOf(ControlOutcome("EDGE_MODE", "OFF", null, ControlStatus.UNAVAILABLE, "not listed"))))
        assertTrue(t.contains("not available on this camera"))
        assertTrue(t.contains("not listed"))
    }

    @Test fun missingExposureIsNotInvented() {
        val t = ReceiptSummary.text(receipt(emptyList(), ExposureInfo()))
        assertTrue(t.contains("not reported"))
    }

    @Test fun keepsTheJpegDisclosureAndNeverSaysUnprocessed() {
        val t = ReceiptSummary.text(receipt(emptyList()))
        assertTrue(t.contains("may still be processed"))
        assertFalse(t.contains("unprocessed", ignoreCase = true))
    }
}
