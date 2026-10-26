package com.kingshot.camera

import com.kingshot.camera.capture.Countdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountdownTest {
    @Test fun showsWholeSecondsRemainingRoundedUp() {
        val c = Countdown(3)
        assertEquals(3, c.secondsLeft(0))
        assertEquals(3, c.secondsLeft(999))
        assertEquals(2, c.secondsLeft(1000))
        assertEquals(1, c.secondsLeft(2999))
    }

    @Test fun finishesAtExactlyTheTotal() {
        val c = Countdown(3)
        assertFalse(c.finished(2999))
        assertTrue(c.finished(3000))
        assertEquals(0, c.secondsLeft(3500))
    }

    @Test fun zeroSecondsIsImmediate() {
        assertTrue(Countdown(0).finished(0))
    }

    @Test fun negativeElapsedIsTreatedAsStart() {
        assertEquals(3, Countdown(3).secondsLeft(-50))
    }
}
