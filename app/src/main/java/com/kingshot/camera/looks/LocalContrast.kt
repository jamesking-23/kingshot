package com.kingshot.camera.looks

/**
 * Clarity: local contrast against a very soft version of the picture. The soft version is built cheaply from block averages
 * (16 x 16 pixels each, then box-blurred), so a full-size photo needs only a small grid in memory.
 * Feed every row to [addRow] once, call [finish], then ask [blurAt] for any pixel.
 */
class LocalContrast(private val width: Int, private val height: Int) {
    private val gridW = (width + BLOCK - 1) / BLOCK
    private val gridH = (height + BLOCK - 1) / BLOCK
    private val sum = FloatArray(gridW * gridH)
    private val count = IntArray(gridW * gridH)
    private var soft = FloatArray(gridW * gridH)

    fun addRow(y: Int, row: IntArray) {
        val base = (y / BLOCK) * gridW
        for (x in 0 until width) {
            val c = row[x]
            val l = (0.2126f * ((c shr 16) and 0xFF) + 0.7152f * ((c shr 8) and 0xFF) + 0.0722f * (c and 0xFF)) / 255f
            val i = base + x / BLOCK
            sum[i] += l
            count[i]++
        }
    }

    /** Averages each block, then blurs the grid (separable box blur of [radius] blocks, edges clamped). */
    fun finish(radius: Int = BLUR_RADIUS) {
        val mean = FloatArray(sum.size) { if (count[it] > 0) sum[it] / count[it] else 0f }
        val horizontal = FloatArray(mean.size)
        for (gy in 0 until gridH) {
            for (gx in 0 until gridW) {
                var acc = 0f
                for (d in -radius..radius) acc += mean[gy * gridW + (gx + d).coerceIn(0, gridW - 1)]
                horizontal[gy * gridW + gx] = acc / (2 * radius + 1)
            }
        }
        soft = FloatArray(mean.size)
        for (gy in 0 until gridH) {
            for (gx in 0 until gridW) {
                var acc = 0f
                for (d in -radius..radius) acc += horizontal[(gy + d).coerceIn(0, gridH - 1) * gridW + gx]
                soft[gy * gridW + gx] = acc / (2 * radius + 1)
            }
        }
    }

    /** The soft brightness (0..1) around a pixel, interpolated between block centres. */
    fun blurAt(x: Int, y: Int): Float {
        val fx = ((x + 0.5f) / BLOCK - 0.5f).coerceIn(0f, gridW - 1f)
        val fy = ((y + 0.5f) / BLOCK - 0.5f).coerceIn(0f, gridH - 1f)
        val x0 = fx.toInt().coerceAtMost(maxOf(gridW - 2, 0))
        val y0 = fy.toInt().coerceAtMost(maxOf(gridH - 2, 0))
        val x1 = minOf(x0 + 1, gridW - 1)
        val y1 = minOf(y0 + 1, gridH - 1)
        val tx = fx - x0
        val ty = fy - y0
        val top = soft[y0 * gridW + x0] * (1 - tx) + soft[y0 * gridW + x1] * tx
        val bottom = soft[y1 * gridW + x0] * (1 - tx) + soft[y1 * gridW + x1] * tx
        return top * (1 - ty) + bottom * ty
    }

    companion object {
        const val BLOCK = 16
        const val BLUR_RADIUS = 2
        private const val CLARITY_GAIN = 1.5f

        /** Strength for a Clarity value of -100..100. */
        fun amountFor(clarity: Float): Float = clarity / 100f * CLARITY_GAIN

        /** Pushes a pixel away from its soft surroundings, most in the midtones, keeping its colour ratios. */
        fun apply(argb: Int, soft: Float, amount: Float): Int {
            if (amount == 0f) return argb
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            val l = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
            if (l < 0.004f) return argb
            val mid = 1f - (2f * l - 1f) * (2f * l - 1f)
            val gain = ((l + amount * (l - soft) * mid) / l).coerceIn(0.5f, 2f)
            fun scale(v: Int) = (v * gain + 0.5f).toInt().coerceIn(0, 255)
            return (argb and (0xFF shl 24)) or (scale(r) shl 16) or (scale(g) shl 8) or scale(b)
        }
    }
}
