package com.kingshot.camera.looks

import kotlin.math.abs

/** Base looks: what Pixel 11 calls the "Default Look". KingShot's versions are approximations; see docs/decisions/0008. */
enum class BaseLook(val label: String) {
    ORIGINAL("Original"),
    NATURAL("Natural"),
    SHADOWS("Shadows"),
    VANILLA("Vanilla")
}

/** Creative presets that sit on top of a base look. KingShot's colour recipes only; Google's LUTs are not public. */
enum class Preset(val label: String) {
    NONE("None"),
    EDITORIAL("Editorial"),
    VELVET("Velvet"),
    CLASSIC("Classic"),
    DIGI("Digi"),
    BLACK_TIE("Black Tie"),
    MINIMAL("Minimal")
}

enum class Grain(val label: String, val amount: Float) {
    NONE("None", 0f),
    WEAK("Weak", 0.025f),
    MODERATE("Moderate", 0.05f),
    STRONG("Strong", 0.09f)
}

/** The adjustable controls. Ranges follow the Pixel 11 Looks screen: six sliders -10..+10, Sharpness 0..15 (10 is neutral), Grain in four steps. */
enum class LookParam(val label: String, val min: Int, val max: Int, val neutral: Int) {
    CONTRAST("Contrast", -10, 10, 0),
    HIGHLIGHTS("Highlights", -10, 10, 0),
    SHADOWS("Shadows", -10, 10, 0),
    VIBRANCE("Vibrance", -10, 10, 0),
    TINT("Tint", -10, 10, 0),
    WARMTH("Warmth", -10, 10, 0),
    SHARPNESS("Sharpness", 0, 15, 10)
}

data class LookParams(
    val contrast: Int = 0,
    val highlights: Int = 0,
    val shadows: Int = 0,
    val vibrance: Int = 0,
    val tint: Int = 0,
    val warmth: Int = 0,
    val sharpness: Int = 10,
    val grain: Grain = Grain.NONE
) {
    operator fun get(p: LookParam): Int = when (p) {
        LookParam.CONTRAST -> contrast
        LookParam.HIGHLIGHTS -> highlights
        LookParam.SHADOWS -> shadows
        LookParam.VIBRANCE -> vibrance
        LookParam.TINT -> tint
        LookParam.WARMTH -> warmth
        LookParam.SHARPNESS -> sharpness
    }

    fun with(p: LookParam, value: Int): LookParams {
        val v = value.coerceIn(p.min, p.max)
        return when (p) {
            LookParam.CONTRAST -> copy(contrast = v)
            LookParam.HIGHLIGHTS -> copy(highlights = v)
            LookParam.SHADOWS -> copy(shadows = v)
            LookParam.VIBRANCE -> copy(vibrance = v)
            LookParam.TINT -> copy(tint = v)
            LookParam.WARMTH -> copy(warmth = v)
            LookParam.SHARPNESS -> copy(sharpness = v)
        }
    }

    val isDefault: Boolean get() = this == LookParams()
}

data class LookSelection(val base: BaseLook = BaseLook.ORIGINAL, val preset: Preset = Preset.NONE, val params: LookParams = LookParams()) {
    /** Short human label for logs and file names, e.g. "Velvet on Natural" or "Original". */
    fun label(): String = when {
        preset != Preset.NONE -> "${preset.label} on ${base.label}"
        !params.isDefault && base == BaseLook.ORIGINAL -> "Custom"
        !params.isDefault -> "${base.label} (adjusted)"
        else -> base.label
    }

    /** The name shown over the viewfinder: the preset if one is chosen, otherwise the default look. */
    fun shortLabel(): String = if (preset != Preset.NONE) preset.label else base.label

    /** File-name suffix, letters only. */
    fun fileTag(): String = label().filter { it.isLetterOrDigit() }.lowercase()
}

/** Final numbers for one photo: slider units (-10..10), sharpness 0..15, grain strength and the preset whose colour table applies. */
data class LookRecipe(
    val contrast: Float,
    val highlights: Float,
    val shadows: Float,
    val vibrance: Float,
    val tint: Float,
    val warmth: Float,
    val sharpness: Float,
    val grain: Float,
    val preset: Preset
) {
    /** True when the recipe would leave every pixel unchanged, so no copy is made. */
    val isIdentity: Boolean
        get() {
            val flat = listOf(contrast, highlights, shadows, vibrance, tint, warmth, grain).all { it == 0f }
            return flat && sharpness == NEUTRAL_SHARPNESS && preset == Preset.NONE
        }

    companion object {
        const val NEUTRAL_SHARPNESS = 10f
    }
}

object LookMath {
    private data class Delta(
        val contrast: Int = 0,
        val highlights: Int = 0,
        val shadows: Int = 0,
        val vibrance: Int = 0,
        val sharpness: Int = 0
    )

    private fun delta(base: BaseLook): Delta = when (base) {
        BaseLook.ORIGINAL -> Delta()
        BaseLook.NATURAL -> Delta(contrast = -1, shadows = 1, vibrance = -1, sharpness = -2)
        BaseLook.SHADOWS -> Delta(contrast = 2, shadows = -4, highlights = -1)
        BaseLook.VANILLA -> Delta(contrast = -3, highlights = -2, shadows = 2, vibrance = -1, sharpness = -2)
    }

    /** Film-like grain that comes with a preset, on top of the Grain control. */
    private fun presetGrain(preset: Preset): Grain = when (preset) {
        Preset.CLASSIC, Preset.BLACK_TIE -> Grain.WEAK
        else -> Grain.NONE
    }

    /** Base look and the user's sliders add together (clamped to each range); the preset adds its own colour table and grain. */
    fun recipe(sel: LookSelection): LookRecipe {
        val b = delta(sel.base)
        val p = sel.params
        fun sum(param: LookParam, userValue: Int, db: Int): Float = (userValue + db).coerceIn(param.min, param.max).toFloat()
        return LookRecipe(
            contrast = sum(LookParam.CONTRAST, p.contrast, b.contrast),
            highlights = sum(LookParam.HIGHLIGHTS, p.highlights, b.highlights),
            shadows = sum(LookParam.SHADOWS, p.shadows, b.shadows),
            vibrance = sum(LookParam.VIBRANCE, p.vibrance, b.vibrance),
            tint = p.tint.toFloat(),
            warmth = p.warmth.toFloat(),
            sharpness = sum(LookParam.SHARPNESS, p.sharpness, b.sharpness),
            grain = maxOf(p.grain.amount, presetGrain(sel.preset).amount),
            preset = sel.preset
        )
    }

    private fun smooth(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun luma(r: Float, g: Float, b: Float) = 0.2126f * r + 0.7152f * g + 0.0722f * b

    /** Deterministic film-like noise in -1..1 for a pixel position (same photo always gets the same grain). */
    fun noiseAt(x: Int, y: Int): Float {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return ((h and 0xFFFF) / 32767.5f) - 1f
    }

    /**
     * Tone and colour for one sRGB colour (0..1): the sliders and base look first, then the preset's colour table.
     * The result is left in [out]. Grain and sharpness are separate steps.
     */
    fun transform(rIn: Float, gIn: Float, bIn: Float, k: LookRecipe, lut: LookLut?, out: FloatArray) {
        var r = rIn
        var g = gIn
        var b = bIn
        val w = k.warmth / 10f
        val t = k.tint / 10f
        r *= 1f + WARMTH_GAIN * w
        b *= 1f - WARMTH_GAIN * w
        g *= 1f - TINT_GAIN * t

        val l = luma(r, g, b)
        var l2 = l
        l2 += k.highlights / 10f * HIGHLIGHT_RANGE * smooth(0.45f, 1f, l)
        l2 += k.shadows / 10f * SHADOW_RANGE * (1f - smooth(0f, 0.55f, l))
        l2 = (0.5f + (l2 - 0.5f) * (1f + CONTRAST_RANGE * k.contrast / 10f)).coerceIn(0f, 1f)
        if (l > 1e-4f) {
            val gain = (l2 / l).coerceAtMost(MAX_GAIN)
            r = (r * gain).coerceIn(0f, 1f)
            g = (g * gain).coerceIn(0f, 1f)
            b = (b * gain).coerceIn(0f, 1f)
        } else {
            r = l2
            g = l2
            b = l2
        }

        if (k.vibrance != 0f) {
            val lum = luma(r, g, b)
            val mx = maxOf(r, g, b)
            val mn = minOf(r, g, b)
            val sat = if (mx > 0f) (mx - mn) / mx else 0f
            val f = 1f + k.vibrance / 10f * VIBRANCE_RANGE * (1f - sat)
            r = (lum + (r - lum) * f).coerceIn(0f, 1f)
            g = (lum + (g - lum) * f).coerceIn(0f, 1f)
            b = (lum + (b - lum) * f).coerceIn(0f, 1f)
        }
        if (lut != null) {
            lut.sample(r, g, b)
            r = lut.r
            g = lut.g
            b = lut.b
        }
        out[0] = r
        out[1] = g
        out[2] = b
    }

    /** One ARGB pixel through [transform], then grain. [scratch] must hold 3 floats; pass the same array for every pixel. */
    fun applyPixel(argb: Int, k: LookRecipe, x: Int, y: Int, lut: LookLut? = null, scratch: FloatArray = FloatArray(3)): Int {
        val a = (argb ushr 24) and 0xFF
        transform(((argb shr 16) and 0xFF) / 255f, ((argb shr 8) and 0xFF) / 255f, (argb and 0xFF) / 255f, k, lut, scratch)
        var r = scratch[0]
        var g = scratch[1]
        var b = scratch[2]
        if (k.grain > 0f) {
            val n = noiseAt(x, y) * k.grain
            r = (r + n).coerceIn(0f, 1f)
            g = (g + n).coerceIn(0f, 1f)
            b = (b + n).coerceIn(0f, 1f)
        }
        return (a shl 24) or (toByte(r) shl 16) or (toByte(g) shl 8) or toByte(b)
    }

    private fun toByte(v: Float): Int = (v * 255f + 0.5f).toInt().coerceIn(0, 255)

    /** Unsharp-mask strength for a Sharpness setting: 10 is neutral, above sharpens, below softens. */
    fun detailAmount(sharpness: Float): Float = ((sharpness - LookRecipe.NEUTRAL_SHARPNESS) / 5f * 0.8f).coerceIn(-1f, 1f)

    /**
     * Sharpen (positive [amount]) or soften (negative) one row using a 3x3 box average of the rows above and below.
     * [prev], [cur] and [next] are ARGB rows of [width] pixels; pass the same row for a missing neighbour at an edge.
     */
    fun detailRow(prev: IntArray, cur: IntArray, next: IntArray, out: IntArray, width: Int, amount: Float) {
        if (amount == 0f) {
            System.arraycopy(cur, 0, out, 0, width)
            return
        }
        for (x in 0 until width) {
            val xl = if (x == 0) 0 else x - 1
            val xr = if (x == width - 1) x else x + 1
            val c = cur[x]
            var result = c and (0xFF shl 24)
            for (shift in SHIFTS) {
                val sum = ch(prev[xl], shift) + ch(prev[x], shift) + ch(prev[xr], shift) +
                    ch(cur[xl], shift) + ch(c, shift) + ch(cur[xr], shift) +
                    ch(next[xl], shift) + ch(next[x], shift) + ch(next[xr], shift)
                val blur = sum / 9f
                val v = (ch(c, shift) + amount * (ch(c, shift) - blur)).toInt().coerceIn(0, 255)
                result = result or (v shl shift)
            }
            out[x] = result
        }
    }

    private fun ch(argb: Int, shift: Int): Int = (argb shr shift) and 0xFF

    /**
     * A 4x5 colour matrix (Android ColorMatrix layout, offsets in 0..255) for the live viewfinder preview, fitted by least squares to the
     * real pipeline over a grid of colours. A matrix cannot bend a curve, so it follows the overall colour balance, saturation and
     * contrast but not the S-curve, lifted blacks or per-hue changes; the saved copy gets the full look.
     */
    fun previewMatrix(k: LookRecipe, lut: LookLut?): FloatArray {
        val steps = 6
        val points = steps * steps * steps
        val x = Array(points) { FloatArray(3) }
        val y = Array(points) { FloatArray(3) }
        val tmp = FloatArray(3)
        var n = 0
        for (ri in 0 until steps) {
            for (gi in 0 until steps) {
                for (bi in 0 until steps) {
                    x[n][0] = ri / (steps - 1f)
                    x[n][1] = gi / (steps - 1f)
                    x[n][2] = bi / (steps - 1f)
                    transform(x[n][0], x[n][1], x[n][2], k, lut, tmp)
                    y[n][0] = tmp[0]
                    y[n][1] = tmp[1]
                    y[n][2] = tmp[2]
                    n++
                }
            }
        }
        val m = FloatArray(20)
        for (channel in 0 until 3) {
            val w = fitChannel(x, y, channel)
            m[channel * 5] = w[0]
            m[channel * 5 + 1] = w[1]
            m[channel * 5 + 2] = w[2]
            m[channel * 5 + 4] = w[3] * 255f
        }
        m[18] = 1f
        return m
    }

    /** Least squares for out = w0*r + w1*g + w2*b + w3, by the normal equations and Gaussian elimination. */
    private fun fitChannel(x: Array<FloatArray>, y: Array<FloatArray>, channel: Int): FloatArray {
        val a = Array(4) { DoubleArray(5) }
        for (i in x.indices) {
            val row = doubleArrayOf(x[i][0].toDouble(), x[i][1].toDouble(), x[i][2].toDouble(), 1.0)
            for (r in 0 until 4) {
                for (c in 0 until 4) a[r][c] += row[r] * row[c]
                a[r][4] += row[r] * y[i][channel]
            }
        }
        for (col in 0 until 4) {
            var pivot = col
            for (r in col + 1 until 4) if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
            val swap = a[col]
            a[col] = a[pivot]
            a[pivot] = swap
            val d = a[col][col]
            if (abs(d) < 1e-9) continue
            for (c in col until 5) a[col][c] /= d
            for (r in 0 until 4) {
                if (r == col) continue
                val f = a[r][col]
                for (c in col until 5) a[r][c] -= f * a[col][c]
            }
        }
        return FloatArray(4) { a[it][4].toFloat() }
    }

    /** Largest absolute difference between two matrices; used by tests. */
    fun maxDifference(a: FloatArray, b: FloatArray): Float = a.indices.maxOf { abs(a[it] - b[it]) }

    private val SHIFTS = intArrayOf(16, 8, 0)
    private const val WARMTH_GAIN = 0.10f
    private const val TINT_GAIN = 0.08f
    private const val HIGHLIGHT_RANGE = 0.20f
    private const val SHADOW_RANGE = 0.25f
    private const val CONTRAST_RANGE = 0.6f
    private const val VIBRANCE_RANGE = 0.6f
    private const val MAX_GAIN = 4f
}
