package com.kingshot.camera

import com.kingshot.camera.capabilities.CameraCapabilities
import com.kingshot.camera.capabilities.CameraSelector
import com.kingshot.camera.capabilities.Dim
import com.kingshot.camera.capabilities.FormatNames
import com.kingshot.camera.capabilities.FormatOutputs
import com.kingshot.camera.capabilities.Orientation
import com.kingshot.camera.capabilities.SizeChooser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitiesTest {
    private val raw = FormatOutputs(FormatNames.RAW_SENSOR, "RAW_SENSOR", listOf(Dim(4000, 3000)))
    private val jpeg = FormatOutputs(FormatNames.JPEG, "JPEG", listOf(Dim(1000, 750), Dim(4000, 3000)))

    @Test
    fun rawNeedsBothCapabilityAndSize() {
        assertTrue(CameraCapabilities("0", capabilities = listOf("RAW"), outputs = listOf(raw)).supportsRaw())
        assertFalse(CameraCapabilities("0", capabilities = listOf("RAW")).supportsRaw())
        assertFalse(CameraCapabilities("0", outputs = listOf(raw)).supportsRaw())
    }

    @Test
    fun offModesOnlyWhenListed() {
        val none = CameraCapabilities("0", noiseReductionModes = listOf(1, 2), edgeModes = listOf(1))
        assertFalse(none.supportsNoiseReductionOff())
        assertFalse(none.supportsEdgeOff())
        val some = CameraCapabilities("0", noiseReductionModes = listOf(0, 3), edgeModes = listOf(0))
        assertTrue(some.supportsNoiseReductionOff())
        assertTrue(some.supportsEdgeOff())
    }

    @Test
    fun largestSizes() {
        val c = CameraCapabilities("0", outputs = listOf(jpeg, raw))
        assertEquals(Dim(4000, 3000), c.largestJpeg())
        assertEquals(Dim(4000, 3000), c.largestRaw())
        assertNull(CameraCapabilities("1").largestJpeg())
    }

    @Test
    fun selectorPicksTopLevelRearWithJpeg() {
        val front = CameraCapabilities("1", lensFacing = "FRONT", capabilities = listOf("BACKWARD_COMPATIBLE"), outputs = listOf(jpeg))
        val physical = CameraCapabilities("2", physicalOf = "0", lensFacing = "BACK", capabilities = listOf("BACKWARD_COMPATIBLE"), outputs = listOf(jpeg))
        val rear = CameraCapabilities("0", lensFacing = "BACK", capabilities = listOf("BACKWARD_COMPATIBLE"), outputs = listOf(jpeg))
        assertEquals("0", CameraSelector.pickRear(listOf(front, physical, rear))?.cameraId)
        assertNull(CameraSelector.pickRear(listOf(front, physical)))
    }

    @Test
    fun previewChoiceMatchesAspectAndCapsWidth() {
        val sizes = listOf(Dim(4000, 3000), Dim(1920, 1080), Dim(1600, 1200), Dim(1280, 960))
        assertEquals(Dim(1600, 1200), SizeChooser.choosePreview(sizes, Dim(4000, 3000)))
        assertEquals(Dim(1920, 1080), SizeChooser.choosePreview(sizes, null))
        assertNull(SizeChooser.choosePreview(emptyList(), Dim(4, 3)))
    }

    @Test
    fun orientationMath() {
        assertEquals(90, Orientation.jpegOrientation(90, 0, false))
        assertEquals(0, Orientation.jpegOrientation(90, 90, false))
        assertEquals(270, Orientation.jpegOrientation(90, 180, false))
        assertEquals(180, Orientation.jpegOrientation(90, 90, true))
        assertEquals(1, Orientation.exifFromDegrees(0))
        assertEquals(6, Orientation.exifFromDegrees(90))
        assertEquals(3, Orientation.exifFromDegrees(180))
        assertEquals(8, Orientation.exifFromDegrees(270))
    }
}
