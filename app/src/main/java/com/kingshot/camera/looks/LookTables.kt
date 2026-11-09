package com.kingshot.camera.looks

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.hypot
import kotlin.math.pow

/** A colour-grading wheel: hue 0..360 and saturation 0..100, as in Lightroom's Color Grading panel. */
data class Grade(val hue: Double, val saturation: Double)

/**
 * A Lightroom-style recipe: the settings are the usual panel values (exposure in stops, the rest -100..100, hue in degrees).
 * KingShot turns them into pixels with its own maths (see [LookTables.transform]); they are practical approximations, not Apple's values.
 * Texture and clarity are neighbourhood effects and are applied by the renderer, not baked into the table.
 */
data class FilterRecipe(
    val exposure: Double,
    val contrast: Double,
    val highlights: Double,
    val shadows: Double,
    val whites: Double,
    val blacks: Double,
    val texture: Double,
    val clarity: Double,
    val vibrance: Double,
    val saturation: Double,
    val temperature: Double,
    val tint: Double,
    val shadowGrade: Grade?,
    val midtoneGrade: Grade?,
    val highlightGrade: Grade?,
    val balance: Double
)

/** A baked 3D colour table (sRGB in, sRGB out) with trilinear lookup. Create one wrapper per thread: [sample] writes [r], [g], [b]. */
class LookLut(val size: Int, private val data: FloatArray) {
    var r = 0f
        private set
    var g = 0f
        private set
    var b = 0f
        private set

    fun sample(ri: Float, gi: Float, bi: Float) {
        val n1 = size - 1
        val x = ri.coerceIn(0f, 1f) * n1
        val y = gi.coerceIn(0f, 1f) * n1
        val z = bi.coerceIn(0f, 1f) * n1
        val x0 = minOf(x.toInt(), size - 2)
        val y0 = minOf(y.toInt(), size - 2)
        val z0 = minOf(z.toInt(), size - 2)
        val fx = x - x0
        val fy = y - y0
        val fz = z - z0
        var or = 0f
        var og = 0f
        var ob = 0f
        for (dx in 0..1) {
            for (dy in 0..1) {
                for (dz in 0..1) {
                    val w = (if (dx == 1) fx else 1f - fx) * (if (dy == 1) fy else 1f - fy) * (if (dz == 1) fz else 1f - fz)
                    val i = (((x0 + dx) * size + (y0 + dy)) * size + (z0 + dz)) * 3
                    or += w * data[i]
                    og += w * data[i + 1]
                    ob += w * data[i + 2]
                }
            }
        }
        r = or
        g = og
        b = ob
    }

    /** Raw table value at a grid node; used by tests. */
    fun node(ri: Int, gi: Int, bi: Int, channel: Int): Float = data[((ri * size + gi) * size + bi) * 3 + channel]
}

object LookTables {
    const val SIZE = 33

    private val cache = ConcurrentHashMap<Preset, FloatArray>()

    private val VIVID_BASE = FilterRecipe(0.10, 12.0, -15.0, 8.0, 8.0, -8.0, 3.0, 4.0, 18.0, 4.0, 0.0, 0.0, null, null, Grade(45.0, 3.0), 5.0)
    private val DRAMATIC_BASE = FilterRecipe(
        0.0, 28.0, -30.0, -5.0, -5.0, -22.0, 5.0, 8.0, 5.0, -5.0, 0.0, 0.0, Grade(220.0, 4.0), null, Grade(45.0, 3.0), -5.0
    )
    private val MONO_BASE = FilterRecipe(0.05, 8.0, -12.0, 8.0, 5.0, -8.0, 2.0, 2.0, -100.0, -100.0, 0.0, 0.0, null, null, null, 0.0)

    /** The recipe for a preset, or null for [Preset.NONE]. Values are the nine classic iPhone-style filters from the owner's preset sheet. */
    fun recipeOf(preset: Preset): FilterRecipe? = when (preset) {
        Preset.NONE -> null
        Preset.VIVID -> VIVID_BASE
        Preset.VIVID_WARM -> VIVID_BASE.copy(
            saturation = 4.0, temperature = 7.0, tint = 1.0,
            shadowGrade = Grade(40.0, 4.0), midtoneGrade = Grade(45.0, 6.0), highlightGrade = Grade(45.0, 10.0), balance = 10.0
        )
        Preset.VIVID_COOL -> VIVID_BASE.copy(
            saturation = 3.0, temperature = -7.0,
            shadowGrade = Grade(220.0, 5.0), midtoneGrade = Grade(210.0, 3.0), highlightGrade = Grade(215.0, 7.0), balance = -5.0
        )
        Preset.DRAMATIC -> DRAMATIC_BASE
        Preset.DRAMATIC_WARM -> DRAMATIC_BASE.copy(
            temperature = 8.0, tint = 1.0,
            shadowGrade = Grade(35.0, 5.0), midtoneGrade = Grade(42.0, 7.0), highlightGrade = Grade(48.0, 12.0), balance = 12.0
        )
        Preset.DRAMATIC_COOL -> DRAMATIC_BASE.copy(
            temperature = -8.0,
            shadowGrade = Grade(220.0, 10.0), midtoneGrade = Grade(210.0, 4.0), highlightGrade = Grade(215.0, 6.0), balance = -12.0
        )
        Preset.MONO -> MONO_BASE
        Preset.SILVERTONE -> MONO_BASE.copy(
            contrast = 20.0, highlights = -8.0, shadows = -12.0, whites = 8.0, blacks = -25.0, texture = 4.0, clarity = 6.0,
            shadowGrade = Grade(215.0, 3.0), midtoneGrade = Grade(215.0, 2.0), highlightGrade = Grade(40.0, 2.0)
        )
        Preset.NOIR -> MONO_BASE.copy(
            exposure = -0.05, contrast = 40.0, highlights = -20.0, shadows = -15.0, whites = 5.0, blacks = -35.0, texture = 5.0, clarity = 12.0,
            shadowGrade = Grade(220.0, 4.0), highlightGrade = Grade(40.0, 3.0)
        )
    }

    /** A lookup wrapper for the preset's table (built once, then shared), or null for [Preset.NONE]. */
    fun lutFor(preset: Preset): LookLut? {
        val recipe = recipeOf(preset) ?: return null
        return LookLut(SIZE, cache.getOrPut(preset) { build(recipe, SIZE) })
    }

    /** Bakes a recipe into an N x N x N table, indexed [r][g][b], three floats per node. */
    fun build(recipe: FilterRecipe, size: Int = SIZE): FloatArray {
        val out = FloatArray(size * size * size * 3)
        val rgb = DoubleArray(3)
        for (ri in 0 until size) {
            for (gi in 0 until size) {
                for (bi in 0 until size) {
                    transform(ri / (size - 1.0), gi / (size - 1.0), bi / (size - 1.0), recipe, rgb)
                    val i = ((ri * size + gi) * size + bi) * 3
                    out[i] = rgb[0].toFloat()
                    out[i + 1] = rgb[1].toFloat()
                    out[i + 2] = rgb[2].toFloat()
                }
            }
        }
        return out
    }

    private fun smooth(edge0: Double, edge1: Double, x: Double): Double {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    private fun luma(r: Double, g: Double, b: Double) = 0.2126 * r + 0.7152 * g + 0.0722 * b

    /**
     * The colour transform for one sRGB colour (0..1), result in [out]:
     * exposure, temperature and tint in linear light; highlights, shadows, whites, blacks and an S-curve contrast on lightness
     * (colour ratios kept); saturation and vibrance; then the three colour-grading wheels in OKLab with gamut mapping.
     */
    fun transform(rIn: Double, gIn: Double, bIn: Double, k: FilterRecipe, out: DoubleArray) {
        val ev = 2.0.pow(k.exposure)
        val lr = toLinear(rIn) * ev * (1.0 + TEMPERATURE_GAIN * k.temperature)
        val lg = toLinear(gIn) * ev * (1.0 - TINT_GAIN * k.tint)
        val lb = toLinear(bIn) * ev * (1.0 - TEMPERATURE_GAIN * k.temperature)
        var r = toSrgb(lr)
        var g = toSrgb(lg)
        var b = toSrgb(lb)

        val y = luma(r, g, b)
        var y2 = y + k.highlights / 100.0 * 0.25 * smooth(0.5, 1.0, y) +
            k.shadows / 100.0 * 0.30 * (1.0 - smooth(0.0, 0.5, y)) +
            k.whites / 100.0 * 0.12 * smooth(0.7, 1.0, y) +
            k.blacks / 100.0 * 0.10 * (1.0 - smooth(0.0, 0.3, y))
        y2 = y2.coerceIn(0.0, 1.0)
        val strength = k.contrast / 100.0 * CONTRAST_GAIN
        val y3 = (y2 + (smooth(0.0, 1.0, y2) - y2) * strength).coerceIn(0.0, 1.0)
        if (y > 1e-4) {
            val gain = (y3 / y).coerceAtMost(MAX_GAIN)
            r = (r * gain).coerceIn(0.0, 1.0)
            g = (g * gain).coerceIn(0.0, 1.0)
            b = (b * gain).coerceIn(0.0, 1.0)
        } else {
            r = y3
            g = y3
            b = y3
        }

        val yn = luma(r, g, b)
        if (k.saturation <= -100.0 && k.vibrance <= -100.0) {
            r = yn
            g = yn
            b = yn
        } else {
            val mx = maxOf(r, g, b)
            val mn = minOf(r, g, b)
            val current = if (mx > 0.0) (mx - mn) / mx else 0.0
            val f = (1.0 + k.saturation / 100.0 + k.vibrance / 100.0 * (1.0 - current)).coerceIn(0.0, 3.0)
            r = (yn + (r - yn) * f).coerceIn(0.0, 1.0)
            g = (yn + (g - yn) * f).coerceIn(0.0, 1.0)
            b = (yn + (b - yn) * f).coerceIn(0.0, 1.0)
        }

        if (k.shadowGrade == null && k.midtoneGrade == null && k.highlightGrade == null) {
            out[0] = r
            out[1] = g
            out[2] = b
            return
        }
        grade(r, g, b, yn, k, out)
    }

    private fun grade(rIn: Double, gIn: Double, bIn: Double, yn: Double, k: FilterRecipe, out: DoubleArray) {
        val lab = toOklab(toLinear(rIn), toLinear(gIn), toLinear(bIn))
        val pivot = 0.5 - k.balance / 100.0 * 0.25
        val h = smooth(pivot - 0.3, pivot + 0.3, yn)
        val shadowW = (1.0 - h).pow(2)
        val highlightW = h * h
        val midW = 1.0 - shadowW - highlightW
        var a = lab[1]
        var b = lab[2]
        for ((wheel, w) in listOf(k.shadowGrade to shadowW, k.midtoneGrade to midW, k.highlightGrade to highlightW)) {
            if (wheel == null) continue
            val dir = hueDirection(wheel.hue)
            val amount = wheel.saturation / 100.0 * GRADE_STRENGTH * w
            a += dir[0] * amount
            b += dir[1] * amount
        }
        val lin = DoubleArray(3)
        var factor = 1.0
        if (!inGamut(lab[0], a, b, lin)) {
            var lo = 0.0
            var hi = 1.0
            repeat(GAMUT_STEPS) {
                val mid = (lo + hi) / 2
                if (inGamut(lab[0], a * mid, b * mid, lin)) lo = mid else hi = mid
            }
            factor = lo
        }
        fromOklab(lab[0], a * factor, b * factor, lin)
        out[0] = toSrgb(lin[0])
        out[1] = toSrgb(lin[1])
        out[2] = toSrgb(lin[2])
    }

    /** Unit direction in OKLab (a, b) of the fully saturated colour at a hue angle, so a grade of that hue pushes toward that colour. */
    private fun hueDirection(hueDegrees: Double): DoubleArray {
        val h = (hueDegrees.mod(360.0)) / 60.0
        val i = h.toInt() % 6
        val f = h - h.toInt()
        val rgb = when (i) {
            0 -> doubleArrayOf(1.0, f, 0.0)
            1 -> doubleArrayOf(1.0 - f, 1.0, 0.0)
            2 -> doubleArrayOf(0.0, 1.0, f)
            3 -> doubleArrayOf(0.0, 1.0 - f, 1.0)
            4 -> doubleArrayOf(f, 0.0, 1.0)
            else -> doubleArrayOf(1.0, 0.0, 1.0 - f)
        }
        val lab = toOklab(toLinear(rgb[0]), toLinear(rgb[1]), toLinear(rgb[2]))
        val n = hypot(lab[1], lab[2])
        return doubleArrayOf(lab[1] / n, lab[2] / n)
    }

    private fun inGamut(l: Double, a: Double, b: Double, lin: DoubleArray): Boolean {
        fromOklab(l, a, b, lin)
        return lin.all { it >= -GAMUT_EPS && it <= 1.0 + GAMUT_EPS }
    }

    fun toLinear(c: Double): Double = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    fun toSrgb(linear: Double): Double {
        val c = linear.coerceIn(0.0, 1.0)
        return if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1.0 / 2.4) - 0.055
    }

    private fun toOklab(r: Double, g: Double, b: Double): DoubleArray {
        val l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        )
    }

    private fun fromOklab(l: Double, a: Double, b: Double, out: DoubleArray) {
        val lc = l + 0.3963377774 * a + 0.2158037573 * b
        val mc = l - 0.1055613458 * a - 0.0638541728 * b
        val sc = l - 0.0894841775 * a - 1.2914855480 * b
        val lq = lc * lc * lc
        val mq = mc * mc * mc
        val sq = sc * sc * sc
        out[0] = 4.0767416621 * lq - 3.3077115913 * mq + 0.2309699292 * sq
        out[1] = -1.2684380046 * lq + 2.6097574011 * mq - 0.3413193965 * sq
        out[2] = -0.0041960863 * lq - 0.7034186147 * mq + 1.7076147010 * sq
    }

    private const val TEMPERATURE_GAIN = 0.012
    private const val TINT_GAIN = 0.008
    private const val CONTRAST_GAIN = 2.5
    private const val GRADE_STRENGTH = 0.25
    private const val MAX_GAIN = 4.0
    private const val GAMUT_STEPS = 10
    private const val GAMUT_EPS = 1e-4
}
