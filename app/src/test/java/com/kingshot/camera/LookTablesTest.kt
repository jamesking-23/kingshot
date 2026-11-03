package com.kingshot.camera

import com.kingshot.camera.looks.BaseLook
import com.kingshot.camera.looks.ColourRecipe
import com.kingshot.camera.looks.LookMath
import com.kingshot.camera.looks.LookSelection
import com.kingshot.camera.looks.LookTables
import com.kingshot.camera.looks.Preset
import com.kingshot.camera.looks.Tint
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

    @Test
    fun noneHasNoTableAndEveryPresetHasOne() {
        assertNull(LookTables.recipeOf(Preset.NONE))
        assertNull(LookTables.lutFor(Preset.NONE))
        presets.forEach { assertNotNull(LookTables.recipeOf(it)) }
    }

    @Test
    fun aNeutralRecipeLeavesTheGreyAxisAlone() {
        val neutral = ColourRecipe(0.0, 5.0, 0.5, 0.0, 0.0, 1.0, emptyList(), Tint(0.0, 0.0), Tint(0.0, 0.0))
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
            for (r in 0 until table.size step 4) for (g in 0 until table.size step 4) for (b in 0 until table.size step 4) {
                for (c in 0..2) {
                    val v = table.node(r, g, b, c)
                    assertTrue("$p [$r,$g,$b,$c] = $v", v in 0f..1f)
                }
            }
        }
    }

    @Test
    fun editorialKeepsPureBlackAndWhite() {
        val t = lut(Preset.EDITORIAL)
        val top = t.size - 1
        for (c in 0..2) {
            assertTrue(t.node(0, 0, 0, c) < 0.004f)
            assertTrue(t.node(top, top, top, c) > 0.996f)
        }
    }

    @Test
    fun classicFadesTheBlacks() {
        val t = lut(Preset.CLASSIC)
        for (c in 0..2) assertTrue("channel $c = ${t.node(0, 0, 0, c)}", t.node(0, 0, 0, c) > 0.03f)
    }

    @Test
    fun blackTieStaysNeutralEverywhere() {
        val t = lut(Preset.BLACK_TIE)
        for (r in 0 until t.size step 2) for (g in 0 until t.size step 2) for (b in 0 until t.size step 2) {
            val tr = t.node(r, g, b, 0)
            val tg = t.node(r, g, b, 1)
            val tb = t.node(r, g, b, 2)
            assertTrue("[$r,$g,$b]", Math.abs(tr - tg) < 0.03f && Math.abs(tg - tb) < 0.03f)
        }
    }

    @Test
    fun digiAddsColourAndMinimalRemovesIt() {
        val digi = transform(Preset.DIGI, 0.4, 0.45, 0.65)
        assertTrue(sat(digi[0], digi[1], digi[2]) > sat(0.4, 0.45, 0.65) + 0.05)
        val minimal = transform(Preset.MINIMAL, 0.8, 0.3, 0.2)
        assertTrue(sat(minimal[0], minimal[1], minimal[2]) < sat(0.8, 0.3, 0.2) - 0.1)
    }

    @Test
    fun skinTonesStayWarmAndKeepTheirBrightnessInTheGentlePresets() {
        val skin = doubleArrayOf(0.87, 0.68, 0.55)
        val before = luma(skin[0].toFloat(), skin[1].toFloat(), skin[2].toFloat())
        for (p in presets.filter { it != Preset.BLACK_TIE }) {
            val o = transform(p, skin[0], skin[1], skin[2])
            assertTrue("$p stays warm: ${o.toList()}", o[0] > o[1] && o[1] > o[2])
        }
        for (p in listOf(Preset.EDITORIAL, Preset.VELVET, Preset.MINIMAL)) {
            val o = transform(p, skin[0], skin[1], skin[2])
            val after = luma(o[0].toFloat(), o[1].toFloat(), o[2].toFloat())
            assertTrue("$p brightness moved ${after - before}", Math.abs(after - before) < 0.12f)
        }
    }

    @Test
    fun samplingAtAGridNodeReturnsThatNode() {
        val t = lut(Preset.VELVET)
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
            for (r in 0..5) for (g in 0..5) for (b in 0..5) {
                val x = floatArrayOf(r / 5f, g / 5f, b / 5f)
                LookMath.transform(x[0], x[1], x[2], recipe, ref, tmp)
                for (c in 0..2) {
                    val fit = m[c * 5] * x[0] + m[c * 5 + 1] * x[1] + m[c * 5 + 2] * x[2] + m[c * 5 + 4] / 255f
                    sum += (fit - tmp[c]) * (fit - tmp[c])
                    n++
                }
            }
            val rms = Math.sqrt(sum / n)
            assertTrue("$p preview rms $rms", rms < 0.2)
        }
    }
}
