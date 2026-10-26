package com.kingshot.camera

import com.kingshot.camera.capture.LowLightAdvisor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LowLightAdvisorTest {
    @Test fun formatsShortAndLongShutterTimes() {
        assertEquals("1/125 s", LowLightAdvisor.shutter(8_000_000L))
        assertEquals("1/4 s", LowLightAdvisor.shutter(250_000_000L))
        assertEquals("2.0 s", LowLightAdvisor.shutter(2_000_000_000L))
    }

    @Test fun brightSceneHasNoAdvisory() {
        assertNull(LowLightAdvisor.advisory(exposureNs = 4_000_000L, iso = 50))
    }

    @Test fun longExposureIsReportedWithTheMeasuredValues() {
        val a = LowLightAdvisor.advisory(exposureNs = 125_000_000L, iso = 800)!!
        assertTrue(a.contains("1/8 s"))
        assertTrue(a.contains("ISO 800"))
    }

    @Test fun highIsoAloneIsReported() {
        assertTrue(LowLightAdvisor.advisory(exposureNs = 10_000_000L, iso = 3200) != null)
    }

    @Test fun missingValuesGiveNoAdvisoryRatherThanAGuess() {
        assertNull(LowLightAdvisor.advisory(exposureNs = null, iso = null))
    }

    @Test fun neverClaimsAFeatureTheAppDoesNotHave() {
        val a = LowLightAdvisor.advisory(exposureNs = 500_000_000L, iso = 1600)!!
        assertFalse(a.contains("Night", ignoreCase = true))
        assertFalse(a.contains("unprocessed", ignoreCase = true))
    }
}
