package com.kingshot.camera

import com.kingshot.camera.looks.BaseLook
import com.kingshot.camera.looks.Grain
import com.kingshot.camera.looks.LookMath
import com.kingshot.camera.looks.LookParam
import com.kingshot.camera.looks.LookParams
import com.kingshot.camera.looks.LookRecipe
import com.kingshot.camera.looks.LookSelection
import com.kingshot.camera.looks.LookTables
import com.kingshot.camera.looks.Preset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LookMathTest {
    private fun px(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun red(c: Int) = (c shr 16) and 0xFF
    private fun grn(c: Int) = (c shr 8) and 0xFF
    private fun blu(c: Int) = c and 0xFF

    @Test
    fun defaultSelectionIsAnIdentityAndMakesNoCopy() {
        val r = LookMath.recipe(LookSelection())
        assertTrue(r.isIdentity)
        assertEquals(px(120, 80, 200), LookMath.applyPixel(px(120, 80, 200), r, 3, 4))
    }

    @Test
    fun parameterRangesMatchTheDocumentedControls() {
        for (p in LookParam.values()) {
            if (p == LookParam.SHARPNESS) {
                assertEquals(0, p.min)
                assertEquals(15, p.max)
                assertEquals(10, p.neutral)
            } else {
                assertEquals(-10, p.min)
                assertEquals(10, p.max)
                assertEquals(0, p.neutral)
            }
        }
        assertEquals(listOf("None", "Weak", "Moderate", "Strong"), Grain.values().map { it.label })
        assertEquals(listOf("Original", "Natural", "Shadows", "Vanilla"), BaseLook.values().map { it.label })
        assertEquals(
            listOf("None", "Vivid", "Vivid Warm", "Vivid Cool", "Dramatic", "Dramatic Warm", "Dramatic Cool", "Mono", "Silvertone", "Noir"),
            Preset.values().map { it.label }
        )
    }

    @Test
    fun valuesAreClampedToEachRange() {
        val p = LookParams().with(LookParam.CONTRAST, 99).with(LookParam.SHARPNESS, -4).with(LookParam.WARMTH, -50)
        assertEquals(10, p.contrast)
        assertEquals(0, p.sharpness)
        assertEquals(-10, p.warmth)
    }

    @Test
    fun layersAddAndStayInRange() {
        val sel = LookSelection(BaseLook.SHADOWS, Preset.VIVID, LookParams(contrast = 9))
        val r = LookMath.recipe(sel)
        assertEquals(10f, r.contrast, 0f) // 9 + 2 clamps to 10
        assertEquals(-4f, r.shadows, 0f)
    }

    @Test
    fun grainComesOnlyFromTheGrainControl() {
        assertEquals(Grain.STRONG.amount, LookMath.recipe(LookSelection(BaseLook.ORIGINAL, Preset.NOIR, LookParams(grain = Grain.STRONG))).grain, 0f)
        assertEquals(0f, LookMath.recipe(LookSelection(BaseLook.ORIGINAL, Preset.NOIR)).grain, 0f)
    }

    @Test
    fun presetsBringTheirOwnTextureAndClarity() {
        val noir = LookMath.recipe(LookSelection(BaseLook.ORIGINAL, Preset.NOIR))
        assertEquals(5f, noir.texture, 0f)
        assertEquals(12f, noir.clarity, 0f)
        assertEquals(0f, LookMath.recipe(LookSelection()).clarity, 0f)
    }

    @Test
    fun warmthRaisesRedAndLowersBlue() {
        val r = LookMath.recipe(LookSelection(params = LookParams(warmth = 10)))
        val out = LookMath.applyPixel(px(128, 128, 128), r, 0, 0)
        assertTrue(red(out) > 128)
        assertTrue(blu(out) < 128)
    }

    @Test
    fun contrastSpreadsTonesAroundMidGrey() {
        val r = LookMath.recipe(LookSelection(params = LookParams(contrast = 10)))
        assertTrue(red(LookMath.applyPixel(px(200, 200, 200), r, 0, 0)) > 200)
        assertTrue(red(LookMath.applyPixel(px(60, 60, 60), r, 0, 0)) < 60)
    }

    @Test
    fun shadowsSliderLiftsDarkTonesMoreThanBrightOnes() {
        val r = LookMath.recipe(LookSelection(params = LookParams(shadows = 10)))
        val dark = red(LookMath.applyPixel(px(40, 40, 40), r, 0, 0)) - 40
        val bright = red(LookMath.applyPixel(px(230, 230, 230), r, 0, 0)) - 230
        assertTrue("dark +$dark bright +$bright", dark > 10 && bright <= 1)
    }

    @Test
    fun highlightsSliderPullsBrightTonesDown() {
        val r = LookMath.recipe(LookSelection(params = LookParams(highlights = -10)))
        assertTrue(red(LookMath.applyPixel(px(240, 240, 240), r, 0, 0)) < 240)
        assertEquals(40, red(LookMath.applyPixel(px(40, 40, 40), r, 0, 0)))
    }

    @Test
    fun monoIsMonochromeThroughItsTable() {
        val r = LookMath.recipe(LookSelection(BaseLook.ORIGINAL, Preset.MONO))
        val out = LookMath.applyPixel(px(200, 60, 30), r, 5, 5, LookTables.lutFor(Preset.MONO))
        assertTrue(Math.abs(red(out) - grn(out)) <= 4)
        assertTrue(Math.abs(grn(out) - blu(out)) <= 4)
    }

    @Test
    fun aPresetAloneIsNotAnIdentity() {
        assertFalse(LookMath.recipe(LookSelection(BaseLook.ORIGINAL, Preset.VIVID_WARM)).isIdentity)
    }

    @Test
    fun vibranceChangesMutedColoursMoreThanSaturatedOnes() {
        val r = LookMath.recipe(LookSelection(params = LookParams(vibrance = 10)))
        val muted = LookMath.applyPixel(px(140, 120, 110), r, 0, 0)
        val vivid = LookMath.applyPixel(px(250, 20, 20), r, 0, 0)
        val mutedRatio = (red(muted) - blu(muted)) / 30f
        val vividRatio = (red(vivid) - blu(vivid)) / 230f
        assertTrue("muted x$mutedRatio vivid x$vividRatio", mutedRatio > 1.3f && vividRatio < 1.15f)
    }

    @Test
    fun grainIsDeterministicAndSmall() {
        val r = LookMath.recipe(LookSelection(params = LookParams(grain = Grain.STRONG)))
        val a = LookMath.applyPixel(px(128, 128, 128), r, 10, 20)
        assertEquals(a, LookMath.applyPixel(px(128, 128, 128), r, 10, 20))
        assertNotEquals(a, LookMath.applyPixel(px(128, 128, 128), r, 11, 20))
        assertTrue(Math.abs(red(a) - 128) <= 25)
    }

    @Test
    fun detailRowSharpensAnEdgeAndSoftensWithNegativeAmount() {
        val w = 5
        val dark = IntArray(w) { px(50, 50, 50) }
        val edge = IntArray(w) { if (it < 2) px(50, 50, 50) else px(200, 200, 200) }
        val out = IntArray(w)
        LookMath.detailRow(edge, edge, edge, out, w, 0.8f)
        assertTrue(red(out[1]) <= red(edge[1]))
        assertTrue(red(out[2]) >= red(edge[2]))
        val soft = IntArray(w)
        LookMath.detailRow(edge, edge, edge, soft, w, -1f)
        assertTrue(red(soft[1]) > 50)
        assertTrue(red(soft[2]) < 200)
        val same = IntArray(w)
        LookMath.detailRow(dark, dark, dark, same, w, 0f)
        assertEquals(dark.toList(), same.toList())
    }

    @Test
    fun detailAmountIsNeutralAtTenAndSymmetricInSign() {
        assertEquals(0f, LookMath.detailAmount(10f), 0f)
        assertTrue(LookMath.detailAmount(15f) > 0f)
        assertTrue(LookMath.detailAmount(0f) < 0f)
    }

    @Test
    fun previewMatrixForIdentityIsTheIdentityMatrix() {
        val m = LookMath.previewMatrix(LookMath.recipe(LookSelection()), null)
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        assertTrue(LookMath.maxDifference(m, identity) < 1e-3f)
    }

    @Test
    fun previewMatrixFollowsWarmth() {
        val m = LookMath.previewMatrix(LookMath.recipe(LookSelection(params = LookParams(warmth = 10))), null)
        val redOut = m[0] * 128 + m[1] * 128 + m[2] * 128 + m[4]
        val blueOut = m[10] * 128 + m[11] * 128 + m[12] * 128 + m[14]
        assertTrue("red $redOut blue $blueOut", redOut > 128f && blueOut < 128f)
    }

    @Test
    fun labelsAndFileTags() {
        assertEquals("Original", LookSelection().label())
        assertEquals("Original", LookSelection().shortLabel())
        assertEquals("Vivid Warm", LookSelection(BaseLook.NATURAL, Preset.VIVID_WARM).shortLabel())
        assertEquals("Vivid Warm on Natural", LookSelection(BaseLook.NATURAL, Preset.VIVID_WARM).label())
        assertEquals("vividwarmonnatural", LookSelection(BaseLook.NATURAL, Preset.VIVID_WARM).fileTag())
        assertEquals("Custom", LookSelection(params = LookParams(contrast = 2)).label())
        assertFalse(LookRecipe.NEUTRAL_SHARPNESS == 0f)
    }
}
