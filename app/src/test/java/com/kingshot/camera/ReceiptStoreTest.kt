package com.kingshot.camera

import com.kingshot.camera.diagnostics.ControlOutcome
import com.kingshot.camera.diagnostics.ControlStatus
import com.kingshot.camera.diagnostics.ExposureInfo
import com.kingshot.camera.diagnostics.ProcessingReceipt
import com.kingshot.camera.diagnostics.ReceiptStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReceiptStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun receipt(id: String) = ProcessingReceipt(
        id, "2026-11-01T10:00:00Z", "Natural", "0", null, null, emptyList(),
        listOf(ControlOutcome("EDGE_MODE", "OFF", "OFF", ControlStatus.HONORED)), ExposureInfo(), emptyList(), null, null, "NONE", "p"
    )

    @Test fun savesAndReadsBack() {
        val s = ReceiptStore(File(tmp.root, "receipts"))
        assertTrue(s.save(receipt("KS_1")))
        assertTrue(s.readText("KS_1")!!.contains("\"captureId\""))
        assertEquals(listOf("KS_1"), s.captureIds())
    }

    @Test fun missingReceiptIsNullNotAnError() {
        assertNull(ReceiptStore(File(tmp.root, "none")).readText("KS_9"))
        assertEquals(emptyList<String>(), ReceiptStore(File(tmp.root, "none")).captureIds())
    }

    @Test fun savingTwiceReplacesOnlyThatReceipt() {
        val s = ReceiptStore(File(tmp.root, "r"))
        s.save(receipt("KS_1"))
        s.save(receipt("KS_2"))
        s.save(receipt("KS_1"))
        assertEquals(listOf("KS_1", "KS_2"), s.captureIds())
    }

    @Test fun unsafeIdsCannotEscapeTheFolder() {
        val dir = File(tmp.root, "r")
        val s = ReceiptStore(dir)
        s.save(receipt("../../evil"))
        assertTrue(dir.listFiles()!!.all { it.parentFile == dir })
        assertTrue(tmp.root.listFiles()!!.none { it.name.contains("evil") })
    }
}
