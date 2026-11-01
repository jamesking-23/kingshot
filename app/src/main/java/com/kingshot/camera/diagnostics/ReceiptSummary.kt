package com.kingshot.camera.diagnostics

import java.util.Locale

/** Plain-language view of a Processing Receipt for the capture details panel. Reports requested, reported and unavailable; never "unprocessed". */
object ReceiptSummary {
    fun lines(r: ProcessingReceipt): List<String> {
        val out = mutableListOf<String>()
        out += "Capture ${r.captureId} · profile ${r.profile}"
        val phys = r.activePhysicalId?.let { ", active lens camera $it" } ?: ""
        val zoom = r.zoomRatio?.let { String.format(Locale.US, ", zoom %.2f×", it) } ?: ""
        out += "Camera ${r.logicalCameraId}$phys$zoom"
        r.outputs.forEach { out += "Saved ${it.format}${it.size?.let { s -> " $s" } ?: ""} (${it.bytes / 1024} KB)" }
        r.controls.forEach { c ->
            out += "${c.name}: requested ${c.requested ?: "-"}, device reported ${c.reported ?: "-"} → ${c.status.plain}" + (c.note?.let { " ($it)" } ?: "")
        }
        val e = r.exposure
        val shutter = e.exposureTimeNs?.let { shutterText(it) }
        out += "Exposure: ${shutter ?: "not reported"}, ISO ${e.iso ?: "not reported"}"
        r.disclosures.forEach { out += it }
        return out
    }

    private fun shutterText(ns: Long): String? = when {
        ns <= 0L -> null
        ns >= 1_000_000_000L -> String.format(Locale.US, "%.1f s", ns / 1e9)
        else -> "1/${Math.round(1e9 / ns)} s"
    }

    fun text(r: ProcessingReceipt): String = lines(r).joinToString("\n")
}
