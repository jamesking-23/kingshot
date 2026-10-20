package com.kingshot.camera.media

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import java.io.IOException
import java.io.OutputStream

sealed class SaveResult {
    data class Saved(val uri: Uri, val displayName: String, val bytes: Long) : SaveResult()

    /** [partialUri] is the pending MediaStore row, if one was created. It is left in place (never deleted by this app). */
    data class Failed(val reason: String, val partialUri: Uri?) : SaveResult()
}

class CountingOutputStream(private val out: OutputStream) : OutputStream() {
    var count: Long = 0
        private set

    override fun write(b: Int) {
        out.write(b)
        count += 1
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        count += len
    }

    override fun flush() = out.flush()
    override fun close() = out.close()
}

/**
 * Saves through MediaStore. Every save creates a NEW item (MediaStore renames on a name clash), so a source
 * capture is never overwritten. On a failed write this class does NOT delete anything: the hidden pending item
 * is left for the system and the failure is reported to the caller.
 */
class MediaSaver(private val resolver: ContentResolver) {
    fun saveImage(displayName: String, mime: String, write: (OutputStream) -> Unit): SaveResult =
        save(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "Pictures/KingShot", displayName, mime, write)

    fun saveDownload(displayName: String, mime: String, write: (OutputStream) -> Unit): SaveResult =
        save(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "Download/KingShot", displayName, mime, write)

    private fun save(collection: Uri, relativePath: String, displayName: String, mime: String, write: (OutputStream) -> Unit): SaveResult {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val inserted: Uri? = try {
            resolver.insert(collection, values)
        } catch (e: Exception) {
            return SaveResult.Failed("MediaStore insert failed: ${e.javaClass.simpleName}: ${e.message}", null)
        }
        val uri = inserted ?: return SaveResult.Failed("MediaStore insert returned null (storage unavailable or full?)", null)

        return try {
            var counted = 0L
            val stream = resolver.openOutputStream(uri) ?: throw IOException("could not open an output stream")
            stream.use {
                val counting = CountingOutputStream(it)
                write(counting)
                counting.flush()
                counted = counting.count
            }
            val published = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, published, null, null)
            SaveResult.Saved(uri, displayName, counted)
        } catch (e: Exception) {
            SaveResult.Failed("write failed: ${e.javaClass.simpleName}: ${e.message}", uri)
        }
    }
}
