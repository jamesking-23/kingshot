package com.kingshot.camera.capture

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Horizon level from the accelerometer's gravity vector. The screen is portrait-locked, so X is across the phone and Y is along it. */
object LevelMath {
    private const val LEVEL_TOLERANCE_DEGREES = 1.0
    private const val FLAT_THRESHOLD = 0.9 // fraction of gravity on Z above which roll is not meaningful

    /** Roll in degrees; positive when the phone's top leans to the right. */
    fun rollDegrees(ax: Float, ay: Float): Double = Math.toDegrees(atan2(ax.toDouble(), ay.toDouble()))

    /** Null when the phone is lying (nearly) flat, where a horizon line would be meaningless. */
    fun rollOrNull(ax: Float, ay: Float, az: Float): Double? {
        val g = hypot(hypot(ax.toDouble(), ay.toDouble()), az.toDouble())
        if (g <= 0.0 || abs(az) / g > FLAT_THRESHOLD) return null
        return rollDegrees(ax, ay)
    }

    fun isLevel(rollDegrees: Double): Boolean = abs(rollDegrees) <= LEVEL_TOLERANCE_DEGREES

    fun smooth(previous: Double, sample: Double, alpha: Double): Double = previous + alpha * (sample - previous)
}
