package com.kingshot.camera.camera

import android.media.Image
import com.kingshot.camera.raw.OnceCloseable

/** Owns one Image; [close] is idempotent, so every code path may call it safely. */
class ImageHandle(val image: Image) : AutoCloseable {
    private val once = OnceCloseable(image)
    val timestamp: Long = image.timestamp
    val width: Int = image.width
    val height: Int = image.height

    override fun close() = once.close()
}
