package com.kingshot.camera.looks

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import com.kingshot.camera.media.MediaSaver
import com.kingshot.camera.media.SaveResult

sealed class LookResult {
    data class Rendered(val uri: Uri, val name: String, val note: String) : LookResult()

    data class Failed(val reason: String) : LookResult()
}

/**
 * Applies a look to a COPY of a saved JPEG. The source file is only read; the copy is a new MediaStore item named
 * "<source stem>_<look>.jpg", so the original and the rendered file stay paired and nothing is overwritten.
 */
class LookRenderer(private val resolver: ContentResolver, private val saver: MediaSaver) {
    fun render(source: Uri, sourceName: String, selection: LookSelection): LookResult {
        val recipe = LookMath.recipe(selection)
        if (recipe.isIdentity) return LookResult.Failed("the selected look changes nothing")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (e: java.io.IOException) {
            return LookResult.Failed("could not read ${e.message}")
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return LookResult.Failed("could not read the image size")
        var sample = 1
        while (bounds.outWidth.toLong() * bounds.outHeight / (sample.toLong() * sample) > MAX_PIXELS) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = try {
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: java.io.IOException) {
            return LookResult.Failed("could not decode: ${e.message}")
        } catch (e: OutOfMemoryError) {
            return LookResult.Failed("not enough memory to render this photo")
        } ?: return LookResult.Failed("could not decode the photo")

        val orientation = try {
            resolver.openInputStream(source)?.use { ExifInterface(it).getAttribute(ExifInterface.TAG_ORIENTATION) }
        } catch (e: java.io.IOException) {
            null
        }

        process(bitmap, recipe)

        val name = "${sourceName.substringBeforeLast('.')}_${selection.fileTag()}.jpg"
        val saved = saver.saveImage(name, "image/jpeg") { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) throw java.io.IOException("JPEG encoder refused the image")
        }
        bitmap.recycle()
        return when (saved) {
            is SaveResult.Failed -> LookResult.Failed(saved.reason)
            is SaveResult.Saved -> {
                val exifNote = copyOrientation(saved.uri, orientation)
                val scaled = if (sample > 1) " at 1/$sample size to fit memory" else ""
                LookResult.Rendered(saved.uri, saved.displayName, "rendered$scaled; $exifNote")
            }
        }
    }

    /** Clarity grid (if the look has Clarity), then sharpness and texture in a rolling 3-row window, then tone and colour per pixel. */
    private fun process(bitmap: Bitmap, recipe: LookRecipe) {
        val w = bitmap.width
        val h = bitmap.height
        val amount = LookMath.detailAmount(recipe)
        val clarity = LocalContrast.amountFor(recipe.clarity)
        val local = if (clarity != 0f) buildLocal(bitmap) else null
        var prev = IntArray(w)
        var cur = IntArray(w)
        var next = IntArray(w)
        val out = IntArray(w)
        val lut = LookTables.lutFor(recipe.preset)
        val scratch = FloatArray(3)
        bitmap.getPixels(cur, 0, w, 0, 0, w, 1)
        System.arraycopy(cur, 0, prev, 0, w)
        for (y in 0 until h) {
            if (y + 1 < h) bitmap.getPixels(next, 0, w, 0, y + 1, w, 1) else System.arraycopy(cur, 0, next, 0, w)
            LookMath.detailRow(prev, cur, next, out, w, amount)
            for (x in 0 until w) {
                val local0 = if (local != null) LocalContrast.apply(out[x], local.blurAt(x, y), clarity) else out[x]
                out[x] = LookMath.applyPixel(local0, recipe, x, y, lut, scratch)
            }
            bitmap.setPixels(out, 0, w, 0, y, w, 1)
            val recycled = prev
            prev = cur
            cur = next
            next = recycled
        }
    }

    private fun buildLocal(bitmap: Bitmap): LocalContrast {
        val w = bitmap.width
        val local = LocalContrast(w, bitmap.height)
        val row = IntArray(w)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            local.addRow(y, row)
        }
        local.finish()
        return local
    }

    private fun copyOrientation(uri: Uri, orientation: String?): String {
        if (orientation == null) return "no orientation tag in the source"
        return try {
            resolver.openFileDescriptor(uri, "rw")?.use { fd ->
                val exif = ExifInterface(fd.fileDescriptor)
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation)
                exif.saveAttributes()
            }
            "orientation tag copied"
        } catch (e: java.io.IOException) {
            "orientation tag NOT copied (${e.message}); the copy may open rotated"
        }
    }

    private companion object {
        const val MAX_PIXELS = 24_000_000L
        const val JPEG_QUALITY = 95
    }
}
