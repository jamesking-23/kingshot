package com.kingshot.camera

import com.kingshot.camera.capture.LevelMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelMathTest {
    @Test fun uprightPortraitIsZero() {
        assertEquals(0.0, LevelMath.rollDegrees(0f, 9.81f), 0.01)
    }

    @Test fun tiltedRightGivesPositiveRoll() {
        assertEquals(10.0, LevelMath.rollDegrees(9.81f * Math.sin(Math.toRadians(10.0)).toFloat(), 9.81f * Math.cos(Math.toRadians(10.0)).toFloat()), 0.05)
    }

    @Test fun levelThresholdIsInclusive() {
        assertTrue(LevelMath.isLevel(0.9))
        assertTrue(LevelMath.isLevel(-1.0))
        assertFalse(LevelMath.isLevel(1.5))
    }

    @Test fun lowPassMovesTowardTheSampleWithoutJumping() {
        val s = LevelMath.smooth(previous = 0.0, sample = 10.0, alpha = 0.2)
        assertEquals(2.0, s, 1e-9)
    }

    @Test fun lyingFlatGivesNoRoll() {
        // Phone flat on a table: gravity is almost all on Z, so roll is meaningless.
        assertEquals(null, LevelMath.rollOrNull(0.1f, 0.1f, 9.8f))
    }
}
