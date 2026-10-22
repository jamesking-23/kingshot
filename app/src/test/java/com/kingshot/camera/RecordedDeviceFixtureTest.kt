package com.kingshot.camera

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.kingshot.camera.capabilities.CameraCapabilities
import com.kingshot.camera.capabilities.CameraSelector
import com.kingshot.camera.capabilities.Dim
import com.kingshot.camera.capabilities.FormatNames
import com.kingshot.camera.capabilities.FormatOutputs
import com.kingshot.camera.capture.ProcessingChoice
import com.kingshot.camera.capture.StillRequestPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs the app's capability decisions against a capability report recorded from a real device (DV-001). */
class RecordedDeviceFixtureTest {
    private val file: File = listOf(
        "../fixtures/capabilities/google_panther_android17_report.json",
        "fixtures/capabilities/google_panther_android17_report.json"
    ).map { File(it) }.first { it.isFile }

    private fun strings(e: JsonElement?): List<String> =
        if (e == null || e.isJsonNull) emptyList() else e.asJsonArray.map { it.asString }

    private fun ints(e: JsonElement?): List<Int> =
        if (e == null || e.isJsonNull) emptyList() else e.asJsonArray.map { it.asInt }

    private fun dims(names: List<String>): List<Dim> = names.map { n ->
        val (w, h) = n.split("x")
        Dim(w.toInt(), h.toInt())
    }

    private fun nrMode(name: String) = listOf("OFF", "FAST", "HIGH_QUALITY", "MINIMAL", "ZERO_SHUTTER_LAG").indexOf(name)

    private fun edgeMode(name: String) = listOf("OFF", "FAST", "HIGH_QUALITY", "ZERO_SHUTTER_LAG").indexOf(name)

    private fun load(): List<CameraCapabilities> {
        val root = JsonParser.parseString(file.readText()).asJsonObject
        return root.getAsJsonArray("cameras").map { el ->
            val c = el.asJsonObject
            val id: JsonObject = c.getAsJsonObject("identity")
            val out: JsonObject = c.getAsJsonObject("outputs")
            val proc: JsonObject = c.getAsJsonObject("processingModes")
            CameraCapabilities(
                cameraId = id.get("cameraId").asString,
                physicalOf = id.get("physicalOf").takeIf { !it.isJsonNull }?.asString,
                physicalIds = strings(id.get("physicalIds")),
                lensFacing = id.get("lensFacing").asString,
                capabilities = strings(id.get("capabilities")),
                afModes = ints(c.getAsJsonObject("controls").get("afModes")),
                noiseReductionModes = strings(proc.get("noiseReduction")).map { nrMode(it) },
                edgeModes = strings(proc.get("edge")).map { edgeMode(it) },
                outputs = listOf(
                    FormatOutputs(FormatNames.RAW_SENSOR, "RAW_SENSOR", dims(strings(out.get("rawSizes")))),
                    FormatOutputs(FormatNames.JPEG, "JPEG", dims(strings(out.get("jpegSizes"))))
                )
            )
        }
    }

    @Test
    fun fixtureHasEveryLogicalAndPhysicalCamera() {
        val cams = load()
        assertEquals(7, cams.size)
        assertEquals(listOf("2", "3", "4"), cams.first { it.cameraId == "0" }.physicalIds)
        assertEquals("0", cams.first { it.cameraId == "3" }.physicalOf)
    }

    @Test
    fun rearSelectionPicksTheLogicalRearCamera() {
        assertEquals("0", CameraSelector.pickRear(load())?.cameraId)
    }

    @Test
    fun offModesAreRequestedBecauseTheyWereListed() {
        val cam = load().first { it.cameraId == "0" }
        assertTrue(cam.supportsNoiseReductionOff())
        assertTrue(cam.supportsEdgeOff())
        val plan = StillRequestPlanner.plan(cam, ProcessingChoice(requestNoiseReductionOff = true, requestEdgeOff = true))
        assertEquals(0, plan.noiseReductionMode)
        assertEquals(0, plan.edgeMode)
        assertTrue(plan.useContinuousPictureAf)
    }

    @Test
    fun rawIsOfferedOnlyWhereCapabilityAndSizeExist() {
        val cam = load().first { it.cameraId == "0" }
        assertTrue(cam.supportsRaw())
        assertEquals(Dim(4080, 3072), cam.largestRaw())
        assertFalse(cam.copy(capabilities = cam.capabilities - "RAW").supportsRaw())
    }
}
