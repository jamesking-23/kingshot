package com.kingshot.camera

import com.kingshot.camera.capabilities.CameraCapabilities
import com.kingshot.camera.capture.ProcessingChoice
import com.kingshot.camera.capture.StillRequestPlanner
import com.kingshot.camera.diagnostics.ControlStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StillRequestPlannerTest {
    private val offListed = CameraCapabilities("0", noiseReductionModes = listOf(0, 1), edgeModes = listOf(0, 2), afModes = listOf(4))
    private val offNotListed = CameraCapabilities("0", noiseReductionModes = listOf(1, 3), edgeModes = listOf(2))
    private val both = ProcessingChoice(requestNoiseReductionOff = true, requestEdgeOff = true)

    @Test
    fun requestsOffOnlyWhenListed() {
        val p = StillRequestPlanner.plan(offListed, both)
        assertEquals(0, p.noiseReductionMode)
        assertEquals(0, p.edgeMode)
        assertTrue(p.useContinuousPictureAf)
        assertTrue(p.notes.isEmpty())
    }

    @Test
    fun neverRequestsUnlistedModeAndSaysSo() {
        val p = StillRequestPlanner.plan(offNotListed, both)
        assertNull(p.noiseReductionMode)
        assertNull(p.edgeMode)
        assertEquals(2, p.notes.size)
        val outcomes = StillRequestPlanner.evaluate(p, reportedNr = 1, reportedEdge = 2)
        assertEquals(ControlStatus.UNAVAILABLE, outcomes[0].status)
        assertEquals(ControlStatus.UNAVAILABLE, outcomes[1].status)
        assertEquals("FAST", outcomes[0].reported)
    }

    @Test
    fun honoredWhenReportedMatches() {
        val p = StillRequestPlanner.plan(offListed, both)
        val o = StillRequestPlanner.evaluate(p, 0, 0)
        assertEquals(ControlStatus.HONORED, o[0].status)
        assertEquals("OFF", o[0].requested)
        assertEquals("OFF", o[0].reported)
    }

    @Test
    fun differsWhenReportedIsDifferentOrMissing() {
        val p = StillRequestPlanner.plan(offListed, both)
        val o = StillRequestPlanner.evaluate(p, reportedNr = 2, reportedEdge = null)
        assertEquals(ControlStatus.DIFFERS, o[0].status)
        assertEquals("HIGH_QUALITY", o[0].reported)
        assertEquals(ControlStatus.DIFFERS, o[1].status)
        assertNull(o[1].reported)
    }

    @Test
    fun notRequestedWhenUserDidNotAsk() {
        val p = StillRequestPlanner.plan(offListed, ProcessingChoice(false, false))
        assertNull(p.noiseReductionMode)
        val o = StillRequestPlanner.evaluate(p, 1, 1)
        assertEquals(ControlStatus.NOT_REQUESTED, o[0].status)
        assertNull(o[0].requested)
    }
}
