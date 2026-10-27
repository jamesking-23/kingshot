package com.kingshot.camera

import com.kingshot.camera.capture.OpenGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenGateTest {
    @Test fun needsAllThree() {
        val g = OpenGate()
        assertFalse(g.wantOpen)
        g.onResume(true)
        assertFalse(g.wantOpen) // no surface yet
        g.onSurface(true)
        assertTrue(g.wantOpen)
    }

    @Test fun surfaceMayArriveBeforeResume() {
        val g = OpenGate()
        g.onSurface(true)
        assertFalse(g.wantOpen)
        g.onResume(true)
        assertTrue(g.wantOpen)
    }

    @Test fun pauseClosesAndResumeReopensWithoutANewSurfaceEvent() {
        val g = OpenGate()
        g.onResume(true)
        g.onSurface(true)
        g.onPause()
        assertFalse(g.wantOpen)
        g.onResume(true)
        assertTrue(g.wantOpen)
    }

    @Test fun losingTheSurfaceClosesEvenWhileResumed() {
        val g = OpenGate()
        g.onResume(true)
        g.onSurface(true)
        g.onSurface(false)
        assertFalse(g.wantOpen)
    }

    @Test fun deniedPermissionNeverOpens() {
        val g = OpenGate()
        g.onResume(false)
        g.onSurface(true)
        assertFalse(g.wantOpen)
        g.onPermission(true)
        assertTrue(g.wantOpen)
        g.onPermission(false) // revoked while open
        assertFalse(g.wantOpen)
    }

    @Test fun resumeReadsPermissionAgainSoRevocationWhileBackgroundedIsSeen() {
        val g = OpenGate()
        g.onResume(true)
        g.onSurface(true)
        g.onPause()
        g.onResume(false)
        assertFalse(g.wantOpen)
    }
}
