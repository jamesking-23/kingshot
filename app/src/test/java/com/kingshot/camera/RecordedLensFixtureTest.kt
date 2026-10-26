package com.kingshot.camera

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.kingshot.camera.capabilities.EvMath
import com.kingshot.camera.capabilities.LensInfo
import com.kingshot.camera.capabilities.LensMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/** Checks the EV and lens maths against the recorded Pixel 7 report (DV-001). Recorded data only; no claim about other devices. */
class RecordedLensFixtureTest {
    private val file: File = listOf(
        "../fixtures/capabilities/google_panther_android17_report.json",
        "fixtures/capabilities/google_panther_android17_report.json"
    ).map { File(it) }.first { it.isFile }

    private val cameras: List<JsonObject> =
        JsonParser.parseString(file.readText()).asJsonObject.getAsJsonArray("cameras").map { it.asJsonObject }

    private fun id(c: JsonObject) = c.getAsJsonObject("identity").get("cameraId").asString

    private fun lensOf(c: JsonObject): LensInfo {
        val size = LensMath.parseSize(c.getAsJsonObject("sensor").get("physicalSizeMm").asString)!!
        val focal = c.getAsJsonObject("lens").getAsJsonArray("focalLengthsMm")[0].asFloat
        return LensInfo(id(c), focal, size.first, size.second)
    }

    @Test fun evRangeOfRearLogicalCameraIsPlusMinusFourEv() {
        val controls = cameras.first { id(it) == "0" }.getAsJsonObject("controls")
        val range = controls.getAsJsonArray("aeCompensationRange").let { Pair(it[0].asInt, it[1].asInt) }
        val ev = EvMath.from(range, controls.get("aeCompensationStep").asDouble)
        assertNotNull(ev)
        assertEquals("+4.0 EV", ev!!.label(range.second))
        assertEquals("-4.0 EV", ev.label(range.first))
    }

    @Test fun sensorDerivedWideRatioMatchesDeviceReportedMinimumZoom() {
        val logical = cameras.first { id(it) == "0" }
        val minZoom = logical.getAsJsonObject("controls").getAsJsonArray("zoomRatioRange")[0].asDouble
        val physical = cameras.filter { it.getAsJsonObject("identity").get("physicalOf").let { p -> !p.isJsonNull && p.asString == "0" } }
        val main = lensOf(physical.first { id(it) == "2" })
        val widest = physical.map { lensOf(it) }.minByOrNull { LensMath.equivalentMm(it)!! }!!
        assertEquals(minZoom, LensMath.opticalRatio(widest, main)!!, 0.02)
    }
}
