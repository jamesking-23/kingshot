package com.kingshot.camera

import com.kingshot.camera.capabilities.CameraCapabilities
import com.kingshot.camera.capture.ControlPlanner
import com.kingshot.camera.capture.ControlRequest
import com.kingshot.camera.capture.FlashMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlPlannerTest {
    private val full = CameraCapabilities(
        cameraId = "0",
        aeModes = listOf(0, 1, 2, 3),
        aeCompensationRange = Pair(-24, 24),
        aeCompensationStep = 1.0 / 6,
        zoomRatioRange = Pair(0.67f, 8f),
        flashAvailable = true
    )
    private val bare = CameraCapabilities(cameraId = "9")

    @Test fun defaultsRequestNothingExtra() {
        val p = ControlPlanner.plan(full, ControlRequest())
        assertEquals(0, p.evIndex)
        assertNull(p.aeMode)
        assertTrue(p.notes.isEmpty())
    }

    @Test fun evAndZoomAreClampedToReportedRanges() {
        val p = ControlPlanner.plan(full, ControlRequest(evIndex = 99, zoomRatio = 20f))
        assertEquals(24, p.evIndex)
        assertEquals(8f, p.zoomRatio)
    }

    @Test fun unsupportedControlsAreNotRequestedAndAreExplained() {
        val p = ControlPlanner.plan(bare, ControlRequest(evIndex = 3, zoomRatio = 2f, flash = FlashMode.ON))
        assertNull(p.evIndex)
        assertNull(p.zoomRatio)
        assertNull(p.aeMode)
        assertEquals(FlashMode.OFF, p.flash)
        assertEquals(3, p.notes.size)
    }

    @Test fun autoFlashUsesAutoFlashAeModeOnlyIfListed() {
        assertEquals(2, ControlPlanner.plan(full, ControlRequest(flash = FlashMode.AUTO)).aeMode)
        val noAuto = full.copy(aeModes = listOf(0, 1))
        val p = ControlPlanner.plan(noAuto, ControlRequest(flash = FlashMode.AUTO))
        assertNull(p.aeMode)
        assertEquals(FlashMode.OFF, p.flash)
    }

    @Test fun alwaysFlashUsesAlwaysFlashAeMode() {
        assertEquals(3, ControlPlanner.plan(full, ControlRequest(flash = FlashMode.ON)).aeMode)
    }

    @Test fun flashRequestOnCameraWithoutFlashUnitIsRefused() {
        val p = ControlPlanner.plan(full.copy(flashAvailable = false), ControlRequest(flash = FlashMode.ON))
        assertEquals(FlashMode.OFF, p.flash)
        assertTrue(p.notes.single().contains("flash", ignoreCase = true))
    }

    @Test fun flashNeedsPrecaptureOnlyWhenFiring() {
        assertTrue(ControlPlanner.plan(full, ControlRequest(flash = FlashMode.AUTO)).needsPrecapture)
        assertTrue(!ControlPlanner.plan(full, ControlRequest(flash = FlashMode.OFF)).needsPrecapture)
    }
}
