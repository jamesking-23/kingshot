package com.kingshot.camera.capabilities

import kotlin.math.hypot

/** One physical lens as reported by the device: focal length and the physical sensor size, both in millimetres. */
data class LensInfo(val id: String, val focalMm: Float, val sensorWidthMm: Float, val sensorHeightMm: Float)

enum class ZoomKind { OPTICAL, DIGITAL_CROP }

/** A selectable zoom position. [lensId] is the physical lens that serves it, or null for a digital crop of another lens. */
data class ZoomStop(val ratio: Float, val kind: ZoomKind, val lensId: String?)

/** Lens and zoom maths from reported characteristics. Nothing here knows a device model. */
object LensMath {
    private const val FULL_FRAME_DIAGONAL_MM = 43.27f
    private const val SAME_STOP_TOLERANCE = 0.08f

    fun parseSize(text: String?): Pair<Float, Float>? {
        val parts = text?.split("x") ?: return null
        if (parts.size != 2) return null
        val w = parts[0].toFloatOrNull() ?: return null
        val h = parts[1].toFloatOrNull() ?: return null
        return if (w > 0f && h > 0f) Pair(w, h) else null
    }

    /** 35 mm-equivalent focal length, or null if the sensor size is unusable. */
    fun equivalentMm(lens: LensInfo): Double? {
        val diagonal = hypot(lens.sensorWidthMm, lens.sensorHeightMm)
        if (diagonal <= 0f || lens.focalMm <= 0f) return null
        return (lens.focalMm * FULL_FRAME_DIAGONAL_MM / diagonal).toDouble()
    }

    /** Field-of-view ratio of [lens] relative to [reference]; below 1 is wider. */
    fun opticalRatio(lens: LensInfo, reference: LensInfo): Double? {
        val a = equivalentMm(lens) ?: return null
        val b = equivalentMm(reference) ?: return null
        return a / b
    }

    /**
     * Zoom stops: one optical stop per lens (at its ratio to [reference]), plus the requested digital steps. A digital step that falls on
     * an optical lens (within tolerance) is dropped, so a label never calls a crop optical or the reverse. Stops outside [zoomRange] are omitted.
     */
    fun stops(lenses: List<LensInfo>, reference: String, zoomRange: Pair<Float, Float>?, digitalSteps: List<Float>): List<ZoomStop> {
        if (zoomRange == null) return emptyList()
        val ref = lenses.firstOrNull { it.id == reference } ?: return emptyList()
        val optical = lenses.mapNotNull { l -> opticalRatio(l, ref)?.let { ZoomStop(it.toFloat(), ZoomKind.OPTICAL, l.id) } }
        val digital = digitalSteps
            .filter { step -> optical.none { Math.abs(it.ratio - step) / step < SAME_STOP_TOLERANCE } }
            .map { ZoomStop(it, ZoomKind.DIGITAL_CROP, null) }
        return (optical + digital)
            .filter { it.ratio >= zoomRange.first - 0.001f && it.ratio <= zoomRange.second + 0.001f }
            .sortedBy { it.ratio }
    }
}
