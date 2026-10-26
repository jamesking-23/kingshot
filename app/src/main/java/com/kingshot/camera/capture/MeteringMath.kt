package com.kingshot.camera.capture

import com.kingshot.camera.capabilities.Dim
import kotlin.math.max
import kotlin.math.min

/** A metering region in active-array pixels. [weight] is 0..1000, the range Camera2 accepts. */
data class MeterRegion(val x: Int, val y: Int, val width: Int, val height: Int, val weight: Int)

/** Maps a tap on the portrait preview to a Camera2 metering region. Pure geometry; the caller supplies the sensor orientation. */
object MeteringMath {
    private const val REGION_FRACTION = 0.15f // region side as a fraction of the shorter active-array side
    private const val MAX_WEIGHT = 1000

    /**
     * @param nx tap position across the view, 0 (left) .. 1 (right)
     * @param ny tap position down the view, 0 (top) .. 1 (bottom)
     * @param sensorOrientation SENSOR_ORIENTATION: clockwise degrees the sensor image must turn to appear upright on the natural display
     * @param zoomRatio current zoom; at z the view shows the central 1/z of the sensor
     */
    fun region(nx: Float, ny: Float, sensorOrientation: Int, active: Dim, zoomRatio: Float): MeterRegion {
        val (rx, ry) = when (((sensorOrientation % 360) + 360) % 360) {
            90 -> Pair(ny, 1f - nx)
            180 -> Pair(1f - nx, 1f - ny)
            270 -> Pair(1f - ny, nx)
            else -> Pair(nx, ny)
        }
        val z = if (zoomRatio < 1f) 1f else zoomRatio
        val sx = (0.5f + (rx.coerceIn(0f, 1f) - 0.5f) / z).coerceIn(0f, 1f)
        val sy = (0.5f + (ry.coerceIn(0f, 1f) - 0.5f) / z).coerceIn(0f, 1f)
        val side = max(1, (min(active.width, active.height) * REGION_FRACTION).toInt())
        val cx = (sx * active.width).toInt()
        val cy = (sy * active.height).toInt()
        val x = (cx - side / 2).coerceIn(0, max(0, active.width - side))
        val y = (cy - side / 2).coerceIn(0, max(0, active.height - side))
        return MeterRegion(x, y, min(side, active.width), min(side, active.height), MAX_WEIGHT)
    }
}
