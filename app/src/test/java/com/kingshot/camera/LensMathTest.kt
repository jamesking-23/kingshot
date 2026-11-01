package com.kingshot.camera

import com.kingshot.camera.capabilities.LensInfo
import com.kingshot.camera.capabilities.LensMath
import com.kingshot.camera.capabilities.ZoomKind
import com.kingshot.camera.capabilities.ZoomStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LensMathTest {
    // Values recorded from the Pixel 7 report: main 6.81 mm on 9.792x7.3728, ultrawide 2.35 mm on 5.04x3.77.
    private val main = LensInfo("2", 6.81f, 9.792f, 7.3728f)
    private val ultra = LensInfo("3", 2.35f, 5.04f, 3.77f)

    @Test fun equivalentFocalLengthComesFromSensorDiagonal() {
        val eq = LensMath.equivalentMm(main)!!
        assertEquals(24.4, eq, 0.5) // 6.81 * 43.27 / 12.26
    }

    @Test fun parsesPhysicalSizeString() {
        assertEquals(Pair(9.792f, 7.3728f), LensMath.parseSize("9.792x7.3728"))
        assertNull(LensMath.parseSize("garbage"))
        assertNull(LensMath.parseSize(null))
    }

    @Test fun ratioIsRelativeToReferenceLens() {
        // The device itself reports a minimum zoom ratio of 0.6704 on the logical camera; the sensor-derived estimate must agree.
        val r = LensMath.opticalRatio(ultra, main)!!
        assertEquals(0.6704, r, 0.02)
    }

    @Test fun classifiesOpticalAndDigitalStops() {
        val stops = LensMath.stops(listOf(main, ultra), reference = "2", zoomRange = Pair(0.67f, 8f), digitalSteps = listOf(2f))
        // Ultrawide ~0.67x optical, main 1x optical, 2x has no lens -> digital crop.
        assertEquals(listOf(ZoomKind.OPTICAL, ZoomKind.OPTICAL, ZoomKind.DIGITAL_CROP), stops.map { it.kind })
        assertEquals("3", stops[0].lensId)
        assertNull(stops[2].lensId)
    }

    @Test fun omitsStopsOutsideTheReportedZoomRange() {
        val stops = LensMath.stops(listOf(main, ultra), reference = "2", zoomRange = Pair(1f, 3f), digitalSteps = listOf(2f, 5f))
        assertEquals(listOf(1f, 2f), stops.map { Math.round(it.ratio * 10f) / 10f })
    }

    @Test fun noZoomRangeMeansNoZoomStops() {
        assertEquals(emptyList<Any>(), LensMath.stops(listOf(main), reference = "2", zoomRange = null, digitalSteps = listOf(2f)))
    }

    @Test fun referenceLensComesFromReportedMinimumZoom() {
        assertEquals("2", LensMath.referenceFor(listOf(ultra, main), 0.6704f))
        assertNull(LensMath.referenceFor(listOf(ultra, main), null))
        assertNull(LensMath.referenceFor(emptyList(), 0.67f))
    }

    @Test fun labelsSeparateLensFromCrop() {
        assertEquals("0.7× lens", LensMath.label(ZoomStop(0.67f, ZoomKind.OPTICAL, "3")))
        assertEquals("1× lens", LensMath.label(ZoomStop(1f, ZoomKind.OPTICAL, "2")))
        assertEquals("2× digital crop", LensMath.label(ZoomStop(2f, ZoomKind.DIGITAL_CROP, null)))
        assertEquals("2× sensor crop", LensMath.label(ZoomStop(2f, ZoomKind.SENSOR_CROP, "4")))
    }

    // Recorded from the Pixel 7 report: physical camera 4 has the main camera's 6.81 mm focal length on a quarter-area sensor window.
    private val crop = LensInfo("4", 6.81f, 4.896f, 3.6864f)

    @Test fun sameFocalLengthOnASmallerSensorIsASensorCropNotALens() {
        val stops = LensMath.stops(listOf(main, ultra, crop), reference = "2", zoomRange = Pair(0.67f, 8f), digitalSteps = listOf(2f, 5f))
        assertEquals(listOf(ZoomKind.OPTICAL, ZoomKind.OPTICAL, ZoomKind.SENSOR_CROP, ZoomKind.DIGITAL_CROP), stops.map { it.kind })
        assertEquals("4", stops[2].lensId)
        assertEquals(listOf("0.7× lens", "1× lens", "2× sensor crop", "5× digital crop"), stops.map { LensMath.label(it) })
    }

    @Test fun twoDifferentLensesAreNeverMarkedAsCrops() {
        val stops = LensMath.stops(listOf(main, ultra), reference = "2", zoomRange = zoomRatioRangeForTest(), digitalSteps = emptyList())
        assertEquals(listOf(ZoomKind.OPTICAL, ZoomKind.OPTICAL), stops.map { it.kind })
    }

    private fun zoomRatioRangeForTest() = Pair(0.5f, 8f)
}
