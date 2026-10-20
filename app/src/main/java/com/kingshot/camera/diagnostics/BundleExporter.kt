package com.kingshot.camera.diagnostics

import android.content.ContentResolver
import android.net.Uri
import com.kingshot.camera.media.MediaSaver
import com.kingshot.camera.media.SaveResult
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds the diagnostics bundle (zip) on a USER action only. Contents: capability report (json + text), capture log,
 * receipts, and only the sample captures the user ticked. Saved to Download/KingShot through MediaStore, so no
 * permission and no FileProvider is needed; the caller shares the returned Uri with the system share sheet.
 */
class BundleExporter(private val resolver: ContentResolver, private val saver: MediaSaver) {
    class Sample(val entryName: String, val uri: Uri)

    fun export(
        fileName: String,
        reportJson: String,
        reportText: String,
        logText: String,
        receipts: List<ProcessingReceipt>,
        samples: List<Sample>
    ): SaveResult = saver.saveDownload(fileName, "application/zip") { out ->
        ZipOutputStream(out).use { zip ->
            fun text(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            text("capability_report.json", reportJson)
            text("capability_report.txt", reportText)
            text("capture_log.txt", logText)
            receipts.forEach { text("receipts/${it.captureId}.json", it.toJson()) }
            for (s in samples) {
                zip.putNextEntry(ZipEntry("samples/${s.entryName}"))
                resolver.openInputStream(s.uri)?.use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}
