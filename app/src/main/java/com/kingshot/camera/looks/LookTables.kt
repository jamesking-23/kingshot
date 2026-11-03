package com.kingshot.camera.looks

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/** One hue band of a look: where it sits, how wide it is, how much its colourfulness changes and how far its hue turns. */
data class HueBump(val hueDegrees: Double, val widthDegrees: Double, val chromaChange: Double, val hueShiftDegrees: Double)

/** A tint added to shadows or highlights, as a hue angle and a strength in OKLab chroma units. */
data class Tint(val hueDegrees: Double, val chroma: Double)

/**
 * The design of one colour table, expressed in OKLab (a perceptual colour space), so a "+12%" change looks like +12% to the eye:
 *  1. lightness goes through an S-curve (amount blends it in), then black lift and white drop (in OKLab L units);
 *  2. colourfulness is scaled overall and per hue band, with a soft knee so already vivid colours do not blow out;
 *  3. shadows and highlights get a split-tone tint;
 *  4. anything outside the sRGB gamut is pulled back along constant lightness and hue, not clipped channel by channel.
 * [mono] mixes the sRGB channels into one grey first (a black-and-white filter), then the rest applies.
 */
data class ColourRecipe(
    val curveAmount: Double,
    val curveSteepness: Double,
    val curvePivot: Double,
    val blackLift: Double,
    val whiteDrop: Double,
    val saturation: Double,
    val bumps: List<HueBump>,
    val shadowTint: Tint,
    val highlightTint: Tint,
    val mono: Triple<Double, Double, Double>? = null
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

    /** The designed recipe for a preset, or null for [Preset.NONE]. */
    fun recipeOf(preset: Preset): ColourRecipe? = when (preset) {
        Preset.NONE -> null
        // Crisp and clean: firm contrast, cooler shadows, calmer greens and blues, skin left alone.
        Preset.EDITORIAL -> ColourRecipe(
            0.55, 5.5, 0.50, 0.0, 0.0, 1.06,
            listOf(HueBump(142.0, 40.0, -0.15, 0.0), HueBump(264.0, 40.0, -0.08, 0.0), HueBump(55.0, 30.0, 0.02, 0.0)),
            Tint(250.0, 0.008), Tint(250.0, 0.0)
        )
        // Soft and warm: gentle curve, lifted blacks, rich reds and oranges, magenta shadows, golden highlights.
        Preset.VELVET -> ColourRecipe(
            0.25, 4.0, 0.50, 0.12, 0.02, 1.10,
            listOf(HueBump(29.0, 30.0, 0.18, -3.0), HueBump(55.0, 30.0, 0.12, 0.0), HueBump(264.0, 40.0, -0.15, 0.0), HueBump(142.0, 40.0, -0.12, 8.0)),
            Tint(345.0, 0.016), Tint(65.0, 0.020)
        )
        // Colour-negative film: faded blacks, softened greens turned yellow, teal shadows, warm highlights.
        Preset.CLASSIC -> ColourRecipe(
            0.40, 5.0, 0.50, 0.18, 0.03, 0.88,
            listOf(HueBump(142.0, 45.0, -0.30, 14.0), HueBump(264.0, 40.0, -0.18, -10.0), HueBump(55.0, 30.0, 0.06, 0.0)),
            Tint(190.0, 0.022), Tint(80.0, 0.024)
        )
        // Early digital compact: hard curve, clipped whites, vivid blues and reds, cyan shadows.
        Preset.DIGI -> ColourRecipe(
            0.75, 7.0, 0.50, 0.0, -0.04, 1.28,
            listOf(HueBump(264.0, 45.0, 0.14, -5.0), HueBump(29.0, 30.0, 0.12, 0.0), HueBump(142.0, 40.0, 0.10, -6.0)),
            Tint(215.0, 0.014), Tint(230.0, 0.004)
        )
        // Black and white through a slightly red-leaning filter with a strong curve and a faint cool shadow.
        Preset.BLACK_TIE -> ColourRecipe(
            0.70, 6.5, 0.50, 0.08, 0.02, 0.0, emptyList(),
            Tint(250.0, 0.004), Tint(250.0, 0.0), mono = Triple(0.40, 0.50, 0.10)
        )
        // Quiet and matte: low colour, soft curve, raised blacks, skin tones kept warm.
        Preset.MINIMAL -> ColourRecipe(
            0.15, 3.5, 0.50, 0.14, 0.03, 0.65,
            listOf(HueBump(55.0, 35.0, 0.15, 0.0), HueBump(142.0, 45.0, -0.15, 0.0), HueBump(264.0, 45.0, -0.10, 0.0)),
            Tint(245.0, 0.005), Tint(245.0, 0.0)
        )
    }

    /** A lookup wrapper for the preset's table (built once, then shared), or null for [Preset.NONE]. */
    fun lutFor(preset: Preset): LookLut? {
        val recipe = recipeOf(preset) ?: return null
        return LookLut(SIZE, cache.getOrPut(preset) { build(recipe, SIZE) })
    }

    /** Bakes a recipe into an N x N x N table, indexed [r][g][b], three floats per node. */
    fun build(recipe: ColourRecipe, size: Int = SIZE): FloatArray {
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

    /** The colour transform for one sRGB colour (0..1). Result goes to [out] as sRGB 0..1. */
    fun transform(rIn: Double, gIn: Double, bIn: Double, k: ColourRecipe, out: DoubleArray) {
        var r = toLinear(rIn)
        var g = toLinear(gIn)
        var b = toLinear(bIn)
        val mono = k.mono
        if (mono != null) {
            val gray = toLinear(mono.first * rIn + mono.second * gIn + mono.third * bIn)
            r = gray
            g = gray
            b = gray
        }
        val lms = DoubleArray(3)
        val lab = toOklab(r, g, b, lms)
        val chroma = hypot(lab[1], lab[2])
        val hue = Math.toDegrees(atan2(lab[2], lab[1])).let { if (it < 0) it + 360.0 else it }

        var scale = k.saturation
        var turn = 0.0
        for (bump in k.bumps) {
            val d = Math.abs(((hue - bump.hueDegrees) + 180.0).mod(360.0) - 180.0)
            val w = exp(-0.5 * (d / (bump.widthDegrees / 1.7)).pow(2))
            scale *= 1.0 + bump.chromaChange * w
            turn += bump.hueShiftDegrees * w
        }
        val c2 = if (chroma > 1e-6) CHROMA_KNEE * tanh(chroma * scale / CHROMA_KNEE) else 0.0
        val h2 = Math.toRadians((hue + turn).mod(360.0))
        val l2 = tone(lab[0], k)
        var a2 = c2 * cos(h2)
        var b2 = c2 * sin(h2)
        val shadowW = (1.0 - l2).coerceIn(0.0, 1.0).pow(2)
        val highlightW = l2.coerceIn(0.0, 1.0).pow(2)
        a2 += k.shadowTint.chroma * shadowW * cos(Math.toRadians(k.shadowTint.hueDegrees)) +
            k.highlightTint.chroma * highlightW * cos(Math.toRadians(k.highlightTint.hueDegrees))
        b2 += k.shadowTint.chroma * shadowW * sin(Math.toRadians(k.shadowTint.hueDegrees)) +
            k.highlightTint.chroma * highlightW * sin(Math.toRadians(k.highlightTint.hueDegrees))

        // Gamut mapping: keep lightness and hue, give up chroma only as far as needed.
        val lin = DoubleArray(3)
        var factor = 1.0
        if (!inGamut(l2, a2, b2, lin)) {
            var lo = 0.0
            var hi = 1.0
            repeat(GAMUT_STEPS) {
                val mid = (lo + hi) / 2
                if (inGamut(l2, a2 * mid, b2 * mid, lin)) lo = mid else hi = mid
            }
            factor = lo
        }
        fromOklab(l2, a2 * factor, b2 * factor, lin)
        out[0] = toSrgb(lin[0])
        out[1] = toSrgb(lin[1])
        out[2] = toSrgb(lin[2])
    }

    private fun tone(l: Double, k: ColourRecipe): Double {
        val s0 = logistic(0.0, k)
        val s1 = logistic(1.0, k)
        val curved = (logistic(l, k) - s0) / (s1 - s0)
        val blended = l + (curved - l) * k.curveAmount
        return k.blackLift + (1.0 - k.blackLift - k.whiteDrop) * blended
    }

    private fun logistic(x: Double, k: ColourRecipe) = 1.0 / (1.0 + exp(-k.curveSteepness * (x - k.curvePivot)))

    private fun inGamut(l: Double, a: Double, b: Double, lin: DoubleArray): Boolean {
        fromOklab(l, a, b, lin)
        return lin.all { it >= -GAMUT_EPS && it <= 1.0 + GAMUT_EPS }
    }

    fun toLinear(c: Double): Double = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    fun toSrgb(linear: Double): Double {
        val c = linear.coerceIn(0.0, 1.0)
        return if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1.0 / 2.4) - 0.055
    }

    private fun toOklab(r: Double, g: Double, b: Double, scratch: DoubleArray): DoubleArray {
        scratch[0] = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        scratch[1] = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        scratch[2] = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * scratch[0] + 0.7936177850 * scratch[1] - 0.0040720468 * scratch[2],
            1.9779984951 * scratch[0] - 2.4285922050 * scratch[1] + 0.4505937099 * scratch[2],
            0.0259040371 * scratch[0] + 0.7827717662 * scratch[1] - 0.8086757660 * scratch[2]
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

    private const val CHROMA_KNEE = 0.33
    private const val GAMUT_STEPS = 10
    private const val GAMUT_EPS = 1e-4
}
