package com.kingshot.camera

import com.kingshot.camera.looks.LocalContrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalContrastTest {
    private fun px(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    private fun grid(width: Int, height: Int, value: (Int, Int) -> Int): LocalContrast {
        val lc = LocalContrast(width, height)
        val row = IntArray(width)
        for (y in 0 until height) {
            for (x in 0 until width) row[x] = px(value(x, y))
            lc.addRow(y, row)
        }
        lc.finish()
        return lc
    }

    @Test
    fun aFlatPictureHasAFlatSoftVersion() {
        val lc = grid(100, 70) { _, _ -> 128 }
        for ((x, y) in listOf(0 to 0, 50 to 30, 99 to 69)) assertEquals(128 / 255f, lc.blurAt(x, y), 0.002f)
    }

    @Test
    fun theSoftVersionSitsBetweenDarkAndBrightNeighbours() {
        val lc = grid(160, 64) { x, _ -> if (x < 80) 40 else 220 }
        val nearEdge = lc.blurAt(79, 30)
        assertTrue("$nearEdge", nearEdge > 40 / 255f + 0.1f && nearEdge < 220 / 255f - 0.1f)
        assertTrue(lc.blurAt(2, 30) < nearEdge)
    }

    @Test
    fun clarityPushesABrightPixelBrighterAndADarkOneDarker() {
        val soft = 0.5f
        val bright = LocalContrast.apply(px(160), soft, 0.3f) and 0xFF
        val dark = LocalContrast.apply(px(90), soft, 0.3f) and 0xFF
        assertTrue(bright > 160)
        assertTrue(dark < 90)
    }

    @Test
    fun zeroAmountAndNearBlackPixelsAreUntouched() {
        assertEquals(px(120), LocalContrast.apply(px(120), 0.2f, 0f))
        assertEquals(px(0), LocalContrast.apply(px(0), 0.5f, 0.5f))
    }

    @Test
    fun clarityKeepsTheColourRatiosOfAPixel() {
        val src = (0xFF shl 24) or (180 shl 16) or (90 shl 8) or 45
        val out = LocalContrast.apply(src, 0.3f, 0.4f)
        val r = ((out shr 16) and 0xFF).toFloat()
        val g = ((out shr 8) and 0xFF).toFloat()
        assertEquals(2f, r / g, 0.1f)
    }

    @Test
    fun clarityStrengthScalesWithTheSheetValue() {
        assertEquals(0f, LocalContrast.amountFor(0f), 0f)
        assertTrue(LocalContrast.amountFor(12f) > LocalContrast.amountFor(4f))
    }
}
