package com.kingshot.camera.capture

import java.util.Locale

/**
 * Reports what the camera measured for a scene. The thresholds are KingShot's own reporting limits, not a device property,
 * and this is a report of capture results only: it does not enable, imply or name any multi-frame mode.
 */
object LowLightAdvisor {
    private const val LONG_EXPOSURE_NS = 1_000_000_000L / 30 // 1/30 s: handheld blur becomes likely
    private const val HIGH_ISO = 1600

    fun shutter(exposureNs: Long): String {
        if (exposureNs >= 1_000_000_000L) return String.format(Locale.US, "%.1f s", exposureNs / 1e9)
        val denominator = Math.round(1e9 / exposureNs)
        return "1/$denominator s"
    }

    fun advisory(exposureNs: Long?, iso: Int?): String? {
        if (exposureNs == null && iso == null) return null
        val longExposure = exposureNs != null && exposureNs >= LONG_EXPOSURE_NS
        val highIso = iso != null && iso >= HIGH_ISO
        if (!longExposure && !highIso) return null
        val parts = listOfNotNull(exposureNs?.let { shutter(it) }, iso?.let { "ISO $it" })
        return "Low light: the camera used ${parts.joinToString(" at ")}. Hold steady; the result may show blur or noise."
    }
}
