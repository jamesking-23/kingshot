package com.kingshot.camera.raw

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import java.io.OutputStream

/** Writes a DNG. The caller must pass the TotalCaptureResult that matches [image] (see TimestampMatcher). */
object DngWriter {
    fun write(characteristics: CameraCharacteristics, result: TotalCaptureResult, image: Image, exifOrientation: Int, out: OutputStream) {
        val dng = DngCreator(characteristics, result)
        try {
            dng.setOrientation(exifOrientation)
            dng.writeImage(out, image)
        } finally {
            dng.close()
        }
    }
}
