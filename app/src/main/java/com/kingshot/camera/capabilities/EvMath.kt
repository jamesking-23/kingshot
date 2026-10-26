package com.kingshot.camera.capabilities

import java.util.Locale

/** Exposure compensation derived only from the camera's reported range and step. Null from [from] means "show no control". */
class EvMath private constructor(private val min: Int, private val max: Int, private val step: Double) {
    fun clamp(index: Int): Int = index.coerceIn(min, max)

    fun indices(): List<Int> = (min..max).toList()

    fun evOf(index: Int): Double = clamp(index) * step

    fun label(index: Int): String {
        val v = evOf(index)
        val text = String.format(Locale.US, "%.1f", Math.abs(v))
        return when {
            text == "0.0" -> "0.0 EV"
            v > 0 -> "+$text EV"
            else -> "-$text EV"
        }
    }

    companion object {
        fun from(range: Pair<Int, Int>?, step: Double?): EvMath? {
            if (range == null || step == null || step <= 0.0) return null
            if (range.first == 0 && range.second == 0) return null
            if (range.first > range.second) return null
            return EvMath(range.first, range.second, step)
        }
    }
}
