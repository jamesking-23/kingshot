package com.kingshot.camera

import com.kingshot.camera.capabilities.CameraCapabilities
import com.kingshot.camera.profiles.NaturalProfile
import com.kingshot.camera.profiles.ProfileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalProfileTest {
    private val both = CameraCapabilities(cameraId = "0", noiseReductionModes = listOf(0, 1, 2), edgeModes = listOf(0, 1, 2))
    private val neither = CameraCapabilities(cameraId = "1", noiseReductionModes = listOf(1, 2), edgeModes = listOf(1))

    @Test fun requestsOffOnlyWhereListed() {
        val r = NaturalProfile.resolve(both)
        assertTrue(r.requestNoiseReductionOff && r.requestEdgeOff)
        val n = NaturalProfile.resolve(neither)
        assertFalse(n.requestNoiseReductionOff || n.requestEdgeOff)
        assertEquals(2, n.notes.size)
    }

    @Test fun disclosureNeverClaimsUnprocessed() {
        for (caps in listOf(both, neither)) {
            val d = NaturalProfile.resolve(caps).disclosure
            assertFalse(d.contains("unprocessed", ignoreCase = true))
            assertFalse(d.contains("no processing", ignoreCase = true))
            assertTrue(d.contains("may still"))
        }
    }

    @Test fun profileNameIsNaturalOnlyWhenTogglesMatch() {
        assertEquals(ProfileName.NATURAL, ProfileName.of(both, requestNr = true, requestEdge = true))
        assertEquals(ProfileName.CUSTOM, ProfileName.of(both, requestNr = false, requestEdge = true))
        assertEquals(ProfileName.NATURAL, ProfileName.of(neither, requestNr = true, requestEdge = true))
        assertEquals(ProfileName.NATURAL, ProfileName.of(neither, requestNr = false, requestEdge = false))
    }
}
