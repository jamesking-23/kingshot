package com.kingshot.camera.capture

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Capture IDs of the form KS_<yyyyMMdd>_<HHmmss>_<counter>; RAW and JPEG from one shot share one ID. */
class CaptureIdGenerator(private val zone: ZoneId = ZoneId.systemDefault()) {
    private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(zone)
    private var counter = 0

    @Synchronized
    fun next(epochMs: Long): String {
        counter += 1
        return "KS_${fmt.format(Instant.ofEpochMilli(epochMs))}_$counter"
    }

    fun iso(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(zone).toOffsetDateTime().toString()
}
