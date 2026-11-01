package com.kingshot.camera.diagnostics

import java.io.File
import java.io.IOException

/**
 * Keeps one JSON receipt per capture in app-private storage (never in the user's photo folders, never on the network).
 * Writes go to a temp file first and are then renamed, so a crash cannot leave half a receipt. Nothing is ever deleted here.
 */
class ReceiptStore(private val dir: File) {
    fun save(receipt: ProcessingReceipt): Boolean = try {
        if (!dir.isDirectory && !dir.mkdirs()) {
            false
        } else {
            val target = File(dir, fileName(receipt.captureId))
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(receipt.toJson())
            tmp.renameTo(target)
        }
    } catch (_: IOException) {
        false
    }

    /** The stored JSON text, or null if there is none for this capture. */
    fun readText(captureId: String): String? {
        val f = File(dir, fileName(captureId))
        return try {
            if (f.isFile) f.readText() else null
        } catch (_: IOException) {
            null
        }
    }

    fun captureIds(): List<String> = dir.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) }
        ?.map { it.name.removeSuffix(SUFFIX) }
        ?.sorted()
        .orEmpty()

    private fun fileName(captureId: String): String = safe(captureId) + SUFFIX

    private fun safe(id: String): String = id.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    companion object {
        private const val SUFFIX = ".json"
    }
}
