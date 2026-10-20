package com.kingshot.camera.capabilities

/** Orientation math per the Camera2 documentation. Pure, so it is unit-tested. */
object Orientation {
    /** [deviceDegrees] is the display rotation in degrees clockwise (0, 90, 180, 270). */
    fun jpegOrientation(sensorOrientation: Int, deviceDegrees: Int, frontFacing: Boolean): Int {
        val d = ((deviceDegrees % 360) + 360) % 360
        val r = if (frontFacing) sensorOrientation + d else sensorOrientation - d
        return ((r % 360) + 360) % 360
    }

    /** Maps JPEG_ORIENTATION degrees to android.media.ExifInterface ORIENTATION_* values. */
    fun exifFromDegrees(degrees: Int): Int = when (((degrees % 360) + 360) % 360) {
        90 -> 6
        180 -> 3
        270 -> 8
        else -> 1
    }
}
