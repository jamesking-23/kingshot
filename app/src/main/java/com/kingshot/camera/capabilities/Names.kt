package com.kingshot.camera.capabilities

/** Public Camera2 constant values (stable platform ints) with their names, kept pure for JVM tests. */
object ProcessingModes {
    const val NR_OFF = 0
    const val EDGE_OFF = 0
    const val AF_CONTINUOUS_PICTURE = 4

    fun noiseReductionName(mode: Int?): String? = when (mode) {
        null -> null
        0 -> "OFF"
        1 -> "FAST"
        2 -> "HIGH_QUALITY"
        3 -> "MINIMAL"
        4 -> "ZERO_SHUTTER_LAG"
        else -> "UNKNOWN($mode)"
    }

    fun edgeName(mode: Int?): String? = when (mode) {
        null -> null
        0 -> "OFF"
        1 -> "FAST"
        2 -> "HIGH_QUALITY"
        3 -> "ZERO_SHUTTER_LAG"
        else -> "UNKNOWN($mode)"
    }
}

object CapabilityNames {
    const val RAW = "RAW"
    private val names = listOf(
        "BACKWARD_COMPATIBLE", "MANUAL_SENSOR", "MANUAL_POST_PROCESSING", "RAW", "PRIVATE_REPROCESSING",
        "READ_SENSOR_SETTINGS", "BURST_CAPTURE", "YUV_REPROCESSING", "DEPTH_OUTPUT", "CONSTRAINED_HIGH_SPEED_VIDEO",
        "MOTION_TRACKING", "LOGICAL_MULTI_CAMERA", "MONOCHROME", "SECURE_IMAGE_DATA", "SYSTEM_CAMERA",
        "OFFLINE_PROCESSING", "ULTRA_HIGH_RESOLUTION_SENSOR", "REMOSAIC_REPROCESSING", "DYNAMIC_RANGE_TEN_BIT",
        "STREAM_USE_CASE", "COLOR_SPACE_PROFILES"
    )

    fun of(code: Int): String = names.getOrNull(code) ?: "UNKNOWN($code)"
}

object FormatNames {
    const val SURFACE_TEXTURE = -1
    const val RAW_SENSOR = 32
    const val JPEG = 256

    fun of(format: Int): String = when (format) {
        SURFACE_TEXTURE -> "SurfaceTexture"
        4 -> "RGB_565"
        16 -> "NV16"
        17 -> "NV21"
        20 -> "YUY2"
        32 -> "RAW_SENSOR"
        34 -> "PRIVATE"
        35 -> "YUV_420_888"
        36 -> "RAW_PRIVATE"
        37 -> "RAW10"
        38 -> "RAW12"
        54 -> "YCBCR_P010"
        256 -> "JPEG"
        4101 -> "JPEG_R"
        0x48454946 -> "HEIC"
        else -> "FORMAT_$format"
    }
}

object StateNames {
    fun ae(s: Int?): String? = when (s) {
        null -> null
        0 -> "INACTIVE"; 1 -> "SEARCHING"; 2 -> "CONVERGED"; 3 -> "LOCKED"; 4 -> "FLASH_REQUIRED"; 5 -> "PRECAPTURE"
        else -> "UNKNOWN($s)"
    }

    fun af(s: Int?): String? = when (s) {
        null -> null
        0 -> "INACTIVE"; 1 -> "PASSIVE_SCAN"; 2 -> "PASSIVE_FOCUSED"; 3 -> "ACTIVE_SCAN"
        4 -> "FOCUSED_LOCKED"; 5 -> "NOT_FOCUSED_LOCKED"; 6 -> "PASSIVE_UNFOCUSED"
        else -> "UNKNOWN($s)"
    }

    fun awb(s: Int?): String? = when (s) {
        null -> null
        0 -> "INACTIVE"; 1 -> "SEARCHING"; 2 -> "CONVERGED"; 3 -> "LOCKED"
        else -> "UNKNOWN($s)"
    }

    /** PowerManager thermal status ints (API 29+). */
    fun thermal(s: Int?): String = when (s) {
        null -> "unknown"
        0 -> "NONE"; 1 -> "LIGHT"; 2 -> "MODERATE"; 3 -> "SEVERE"; 4 -> "CRITICAL"; 5 -> "EMERGENCY"; 6 -> "SHUTDOWN"
        else -> "UNKNOWN($s)"
    }
}

/** Chooses preview size from discovered sizes; no model-specific logic. */
object SizeChooser {
    fun choosePreview(sizes: List<Dim>, target: Dim?, maxWidth: Int = 1920): Dim? {
        if (sizes.isEmpty()) return null
        val capped = sizes.filter { it.width <= maxWidth }
        if (target != null) {
            val aspect = target.width.toDouble() / target.height
            val matching = capped.filter { kotlin.math.abs(it.width.toDouble() / it.height - aspect) < 0.02 }
            matching.maxByOrNull { it.pixels }?.let { return it }
        }
        return capped.maxByOrNull { it.pixels } ?: sizes.minByOrNull { it.pixels }
    }
}
