package com.kingshot.camera

import com.kingshot.camera.looks.BaseLook
import com.kingshot.camera.looks.FilterRecipe
import com.kingshot.camera.looks.LookMath
import com.kingshot.camera.looks.LookSelection
import com.kingshot.camera.looks.LookTables
import com.kingshot.camera.looks.Preset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LookTablesTest {
    private val presets = Preset.values().filter { it != Preset.NONE }

    private fun lut(p: Preset) = LookTables.lutFor(p)!!

    private fun luma(r: Float, g: Float, b: Float) = 0.2126f * r + 0.7152f * g + 0.0722f * b

    private fun sat(r: Double, g: Double, b: Double): Double {
        val mx = maxOf(r, g, b)
        return if (mx <= 0.0) 0.0 else (mx - minOf(r, g, b)) / mx
    }

    private fun transform(p: Preset, r: Double, g: Double, b: Double): DoubleArray {
        val out = DoubleArray(3)
        LookTables.transform(r, g, b, LookTables.recipeOf(p)!!, out)
        return out
    }

    private fun greyAt(p: Preset, level: Double) = transform(p, level, level, level)

    @Test
    fun noneHasNoTableAndEveryPresetHasOne() {
        assertNull(LookTables.recipeOf(Preset.NONE))
        assertNull(LookTables.lutFor(Preset.NONE))
        presets.forEach { assertNotNull(LookTables.recipeOf(it)) }
        assertEquals(9, presets.size)
    }

    @Test
    fun recipesCarryTheValuesFromThePresetSheet() {
        val vivid = LookTables.recipeOf(Preset.VIVID)!!
        assertEquals(0.10, vivid.exposure, 0.0)
        assertEquals(12.0, vivid.contrast, 0.0)
        assertEquals(18.0, vivid.vibrance, 0.0)
        val warm = LookTables.recipeOf(Preset.VIVID_WARM)!!
        assertEquals(7.0, warm.temperature, 0.0)
        assertEquals(10.0, warm.balance, 0.0)
        assertEquals(10.0, warm.highlightGrade!!.saturation, 0.0)
        val noir = LookTables.recipeOf(Preset.NOIR)!!
        assertEquals(40.0, noir.contrast, 0.0)
        assertEquals(-35.0, noir.blacks, 0.0)
        assertEquals(-100.0, noir.saturation, 0.0)
        assertEquals(215.0, LookTables.recipeOf(Preset.SILVERTONE)!!.shadowGrade!!.hue, 0.0)
        val cool = LookTables.recipeOf(Preset.DRAMATIC_COOL)!!
        assertEquals(-8.0, cool.temperature, 0.0)
        assertEquals(-12.0, cool.balance, 0.0)
    }

    @Test
    fun aNeutralRecipeLeavesTheGreyAxisAlone() {
        val neutral = FilterRecipe(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, null, null, null, 0.0)
        val out = DoubleArray(3)
        for (i in 0..16) {
            val v = i / 16.0
            LookTables.transform(v, v, v, neutral, out)
            for (c in 0..2) assertEquals("grey $i channel $c", v, out[c], 0.003)
        }
    }

    @Test
    fun greyRampNeverGetsDarkerAsInputGetsBrighter() {
        for (p in presets) {
            val table = lut(p)
            var previous = -1f
            for (i in 0 until table.size) {
                val l = luma(table.node(i, i, i, 0), table.node(i, i, i, 1), table.node(i, i, i, 2))
                assertTrue("$p at $i: $l after $previous", l >= previous - 1e-5f)
                previous = l
            }
        }
    }

    @Test
    fun everyTableValueIsAValidColour() {
        for (p in presets) {
            val table = lut(p)
            for (r in 0 until table.size step 4) {
                for (g in 0 until table.size step 4) {
                    for (b in 0 until table.size step 4) {
                        for (c in 0..2) {
                            val v = table.node(r, g, b, c)
                            assertTrue("$p [$r,$g,$b,$c] = $v", v in 0f..1f)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun monoIsExactlyNeutralAndSilvertoneAndNoirOnlyFaintlyToned() {
        for ((p, limit) in listOf(Preset.MONO to 0.004f, Preset.SILVERTONE to 0.03f, Preset.NOIR to 0.03f)) {
            val t = lut(p)
            for (r in 0 until t.size step 2) {
                for (g in 0 until t.size step 2) {
                    for (b in 0 until t.size step 2) {
                        val tr = t.node(r, g, b, 0)
                        val tg = t.node(r, g, b, 1)
                        val tb = t.node(r, g, b, 2)
                        assertTrue("$p [$r,$g,$b]", Math.abs(tr - tg) < limit && Math.abs(tg - tb) < limit)
                    }
                }
            }
        }
    }

    @Test
    fun warmFiltersTurnGreyWarmAndCoolFiltersTurnItCool() {
        for (p in listOf(Preset.VIVID_WARM, Preset.DRAMATIC_WARM)) {
            val o = greyAt(p, 0.5)
            assertTrue("$p ${o.toList()}", o[0] > o[2] + 0.05)
        }
        for (p in listOf(Preset.VIVID_COOL, Preset.DRAMATIC_COOL)) {
            val o = greyAt(p, 0.5)
            assertTrue("$p ${o.toList()}", o[2] > o[0] + 0.05)
        }
        val plain = greyAt(Preset.VIVID, 0.5)
        assertTrue(Math.abs(plain[0] - plain[2]) < 0.05)
    }

    @Test
    fun vividAddsColourToMutedTones() {
        val o = transform(Preset.VIVID, 0.4, 0.45, 0.65)
        assertTrue(sat(o[0], o[1], o[2]) > sat(0.4, 0.45, 0.65) + 0.02)
    }

    @Test
    fun dramaticHoldsHighlightsAndDeepensShadowsComparedWithVivid() {
        val dramaticHigh = greyAt(Preset.DRAMATIC, 0.95)
        val vividHigh = greyAt(Preset.VIVID, 0.95)
        assertTrue(dramaticHigh[1] < vividHigh[1] - 0.02)
        assertTrue(greyAt(Preset.DRAMATIC, 0.2)[1] < greyAt(Preset.VIVID, 0.2)[1] - 0.05)
    }

    @Test
    fun noirIsTheHardestBlackAndWhite() {
        val noirBlack = greyAt(Preset.NOIR, 0.0)
        for (c in 0..2) assertTrue(noirBlack[c] < 0.01)
        assertTrue(greyAt(Preset.NOIR, 0.2)[1] < greyAt(Preset.MONO, 0.2)[1] - 0.08)
        assertTrue(greyAt(Preset.SILVERTONE, 0.2)[1] < greyAt(Preset.MONO, 0.2)[1])
    }

    @Test
    fun samplingAtAGridNodeReturnsThatNode() {
        val t = lut(Preset.VIVID_WARM)
        t.sample(10 / 32f, 20 / 32f, 5 / 32f)
        assertEquals(t.node(10, 20, 5, 0), t.r, 1e-3f)
        assertEquals(t.node(10, 20, 5, 1), t.g, 1e-3f)
        assertEquals(t.node(10, 20, 5, 2), t.b, 1e-3f)
    }

    @Test
    fun thePreviewMatrixStaysCloseEnoughToEachTable() {
        for (p in presets) {
            val recipe = LookMath.recipe(LookSelection(BaseLook.ORIGINAL, p))
            val m = LookMath.previewMatrix(recipe, LookTables.lutFor(p))
            val ref = LookTables.lutFor(p)
            var sum = 0.0
            var n = 0
            val tmp = FloatArray(3)
            for (r in 0..5) {
                for (g in 0..5) {
                    for (b in 0..5) {
                        val x = floatArrayOf(r / 5f, g / 5f, b / 5f)
                        LookMath.transform(x[0], x[1], x[2], recipe, ref, tmp)
                        for (c in 0..2) {
                            val fit = m[c * 5] * x[0] + m[c * 5 + 1] * x[1] + m[c * 5 + 2] * x[2] + m[c * 5 + 4] / 255f
                            sum += (fit - tmp[c]) * (fit - tmp[c])
                            n++
                        }
                    }
                }
            }
            val rms = Math.sqrt(sum / n)
            assertTrue("$p preview rms $rms", rms < 0.12)
        }
    }
}
