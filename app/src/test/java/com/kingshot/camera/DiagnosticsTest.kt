package com.kingshot.camera

import com.kingshot.camera.capabilities.CameraCapabilities
import com.kingshot.camera.capabilities.Dim
import com.kingshot.camera.capabilities.FormatNames
import com.kingshot.camera.capabilities.FormatOutputs
import com.kingshot.camera.capture.CaptureIdGenerator
import com.kingshot.camera.capture.ReceiptAssembler
import com.kingshot.camera.diagnostics.AppInfo
import com.kingshot.camera.diagnostics.CapabilityReport
import com.kingshot.camera.diagnostics.CaptureLog
import com.kingshot.camera.diagnostics.ControlOutcome
import com.kingshot.camera.diagnostics.ControlStatus
import com.kingshot.camera.diagnostics.DeviceInfo
import com.kingshot.camera.diagnostics.Disclosures
import com.kingshot.camera.diagnostics.ExposureInfo
import com.kingshot.camera.diagnostics.Json
import com.kingshot.camera.diagnostics.OutputRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class DiagnosticsTest {
    private val device = DeviceInfo("M", "Model", "prod", "fp", 34, "2026-10-01", "14")
    private val app = AppInfo("0.2.0", "debug")

    @Test
    fun jsonEscapesAndNests() {
        val s = Json.encode(linkedMapOf("a" to "q\"\n", "b" to listOf(1, 2), "c" to null, "d" to Pair(1, 2)))
        assertTrue(s.contains("\"a\": \"q\\\"\\n\""))
        assertTrue(s.contains("\"b\": [1, 2]"))
        assertTrue(s.contains("\"c\": null"))
        assertTrue(s.contains("\"d\": [1, 2]"))
    }

    @Test
    fun missingValuesAreNullWithAReasonNeverOmitted() {
        val cam = CameraCapabilities("0", lensFacing = "BACK", missing = mapOf("hardwareLevel" to "boom"))
        val map = CapabilityReport.cameraMap(cam)

        @Suppress("UNCHECKED_CAST") // test-only access to the nested report structure
        val identity = map["identity"] as Map<String, Any?>
        assertTrue(identity.containsKey("hardwareLevel"))
        assertNull(identity["hardwareLevel"])

        @Suppress("UNCHECKED_CAST") // test-only
        val missing = map["missing"] as Map<String, String>
        assertEquals("boom", missing["hardwareLevel"])
        assertTrue(missing["sensorOrientation"]!!.isNotEmpty())
        assertFalse(missing.containsKey("lensFacing"))
    }

    @Test
    fun reportJsonAndTextContainDeviceAndCameras() {
        val cam = CameraCapabilities(
            "0", capabilities = listOf("RAW"),
            outputs = listOf(FormatOutputs(FormatNames.RAW_SENSOR, "RAW_SENSOR", listOf(Dim(4000, 3000))))
        )
        val json = CapabilityReport.toJson("2026-10-20T00:00:00Z", app, device, listOf(cam))
        assertTrue(json.contains("\"schemaVersion\": 1"))
        assertTrue(json.contains("\"supportsRaw\": true"))
        assertTrue(json.contains("\"buildFingerprint\": \"fp\""))
        val text = CapabilityReport.toText("2026-10-20T00:00:00Z", app, device, listOf(cam))
        assertTrue(text.contains("supportsRaw: true"))
    }

    @Test
    fun captureIdsHaveSharedFormatAndIncrement() {
        val g = CaptureIdGenerator(ZoneId.of("UTC"))
        assertEquals("KS_19700101_000000_1", g.next(0))
        assertEquals("KS_19700101_000001_2", g.next(1000))
    }

    @Test
    fun logIsBounded() {
        val log = CaptureLog(capacity = 3, clockMs = { 1L })
        repeat(10) { log.add("m$it") }
        assertEquals(listOf("1 m7", "1 m8", "1 m9"), log.snapshot())
    }

    @Test
    fun receiptCompletesOnlyWhenResultAndAllOutputsAreDone() {
        val a = ReceiptAssembler("KS_1", "t", "lab", "0", expectRaw = true, shutterAtMs = 1000)
        assertFalse(a.isComplete())
        a.onResult(
            listOf(ControlOutcome("EDGE_MODE", "OFF", "OFF", ControlStatus.HONORED)),
            ExposureInfo(iso = 100), null, null, emptyList()
        )
        a.onImageArrived(1500)
        a.onOutputSaved(OutputRecord("jpeg", "JPEG", "u1", "4000x3000", 10), 1800)
        assertFalse(a.isComplete())
        a.onOutputFailed("source_raw", "disk full")
        assertTrue(a.isComplete())
        val r = a.build("NONE")
        assertEquals(800L, r.shutterToSavedMs)
        assertEquals(300L, r.processingMs)
        assertTrue(r.disclosures.contains(Disclosures.JPEG_MAY_BE_PROCESSED))
        assertTrue(r.disclosures.any { it.contains("source_raw") && it.contains("disk full") })
        assertTrue(r.toJson().contains("\"status\": \"honored\""))
        assertEquals("KS_1", r.pairId)
    }

    @Test
    fun unmatchedImageIsDisclosedNotHidden() {
        val a = ReceiptAssembler("KS_2", "t", "lab", "0", expectRaw = false, shutterAtMs = 0)
        a.onNoMatchingResult()
        a.onOutputSaved(OutputRecord("jpeg", "JPEG", "u", null, 1), 10)
        assertTrue(a.isComplete())
        assertTrue(a.build("NONE").disclosures.any { it.contains("No capture result matched") })
    }
}
