package com.kingshot.camera

import com.kingshot.camera.capabilities.Dim
import com.kingshot.camera.capture.MeteringMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeteringMathTest {
    private val active = Dim(4000, 3000)

    private fun near(expected: Int, actual: Int, tolerance: Int) = assertTrue("expected $expected±$tolerance but was $actual", Math.abs(expected - actual) <= tolerance)

    @Test fun centerTapMapsToCenterOfSensor() {
        val r = MeteringMath.region(0.5f, 0.5f, 90, active, 1f)
        near(2000, r.x + r.width / 2, 2)
        near(1500, r.y + r.height / 2, 2)
    }

    @Test fun portraitTopLeftTapMapsToSensorBottomLeftForOrientation90() {
        val r = MeteringMath.region(0f, 0f, 90, active, 1f)
        assertTrue(r.x + r.width / 2 < 800)
        assertTrue(r.y + r.height / 2 > 2200)
    }

    @Test fun orientation0IsIdentity() {
        val r = MeteringMath.region(0.25f, 0.75f, 0, active, 1f)
        near(1000, r.x + r.width / 2, 400)
        near(2250, r.y + r.height / 2, 400)
    }

    @Test fun regionStaysInsideActiveArrayAtTheEdges() {
        for (o in listOf(0, 90, 180, 270)) {
            for (p in listOf(0f, 1f)) {
                val r = MeteringMath.region(p, p, o, active, 1f)
                assertTrue(r.x >= 0 && r.y >= 0)
                assertTrue(r.x + r.width <= active.width)
                assertTrue(r.y + r.height <= active.height)
                assertTrue(r.width > 0 && r.height > 0)
            }
        }
    }

    @Test fun zoomNarrowsWhichSensorAreaTheViewShows() {
        // At 2x the view covers the central half; a tap at the view's right edge lands at 3/4 of the sensor width (orientation 0).
        val r = MeteringMath.region(1f, 0.5f, 0, active, 2f)
        near(3000, r.x + r.width / 2, 400)
    }

    @Test fun weightIsWithinMeteringRange() {
        assertEquals(1000, MeteringMath.region(0.5f, 0.5f, 90, active, 1f).weight)
    }
}
