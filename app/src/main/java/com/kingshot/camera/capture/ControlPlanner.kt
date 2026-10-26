package com.kingshot.camera.capture

import com.kingshot.camera.capabilities.CameraCapabilities
import com.kingshot.camera.capabilities.EvMath

enum class FlashMode { OFF, AUTO, ON }

/** What the user asked for on the main screen. */
data class ControlRequest(
    val evIndex: Int = 0,
    val zoomRatio: Float = 1f,
    val flash: FlashMode = FlashMode.OFF,
    val meter: MeterRegion? = null
)

/**
 * Capability-checked controls. A null value means "do not set this key". Anything refused is explained in [notes], so the UI and the
 * receipt can say so instead of silently dropping it.
 */
data class ControlPlan(
    val evIndex: Int?,
    val zoomRatio: Float?,
    val flash: FlashMode,
    val aeMode: Int?,
    val meter: MeterRegion?,
    val notes: List<String>
) {
    val needsPrecapture: Boolean get() = flash != FlashMode.OFF
}

object ControlPlanner {
    private const val AE_ON_AUTO_FLASH = 2
    private const val AE_ON_ALWAYS_FLASH = 3

    fun plan(caps: CameraCapabilities, req: ControlRequest): ControlPlan {
        val notes = mutableListOf<String>()

        val ev = EvMath.from(caps.aeCompensationRange, caps.aeCompensationStep)
        val evIndex = ev?.clamp(req.evIndex)
        if (ev == null && req.evIndex != 0) notes += "Exposure compensation is not reported by this camera; not requested."

        val range = caps.zoomRatioRange
        val zoom = range?.let { req.zoomRatio.coerceIn(it.first, it.second) }
        if (range == null && req.zoomRatio != 1f) notes += "Zoom ratio is not reported by this camera; not requested."

        var flash = req.flash
        var aeMode: Int? = null
        if (flash != FlashMode.OFF) {
            val wanted = if (flash == FlashMode.AUTO) AE_ON_AUTO_FLASH else AE_ON_ALWAYS_FLASH
            when {
                caps.flashAvailable != true -> {
                    notes += "This camera reports no flash unit; flash not requested."
                    flash = FlashMode.OFF
                }
                wanted !in caps.aeModes -> {
                    notes += "The needed flash exposure mode is not listed by this camera; flash not requested."
                    flash = FlashMode.OFF
                }
                else -> aeMode = wanted
            }
        }
        return ControlPlan(evIndex, zoom, flash, aeMode, req.meter, notes)
    }
}
