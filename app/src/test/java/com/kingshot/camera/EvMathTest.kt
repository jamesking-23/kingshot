package com.kingshot.camera

import com.kingshot.camera.capabilities.EvMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvMathTest {
    @Test fun unsupportedWhenRangeMissingOrZero() {
        assertNull(EvMath.from(null, 0.1667))
        assertNull(EvMath.from(Pair(-12, 12), null))
        assertNull(EvMath.from(Pair(0, 0), 0.1667))
        assertNull(EvMath.from(Pair(-12, 12), 0.0))
    }

    @Test fun clampsIndexToReportedRange() {
        val ev = EvMath.from(Pair(-12, 12), 1.0 / 6)!!
        assertEquals(12, ev.clamp(40))
        assertEquals(-12, ev.clamp(-99))
        assertEquals(3, ev.clamp(3))
    }

    @Test fun labelsUseStepNotIndex() {
        val ev = EvMath.from(Pair(-12, 12), 1.0 / 6)!!
        assertEquals("0.0 EV", ev.label(0))
        assertEquals("+1.0 EV", ev.label(6))
        assertEquals("-2.0 EV", ev.label(-12))
        assertEquals("+0.3 EV", ev.label(2))
    }

    @Test fun stepsAreOrderedAndIncludeZero() {
        val ev = EvMath.from(Pair(-6, 6), 0.5)!!
        val s = ev.indices()
        assertEquals(13, s.size)
        assertTrue(0 in s)
        assertEquals(s.sorted(), s)
    }
}
