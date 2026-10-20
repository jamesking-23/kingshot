package com.kingshot.camera.capture

import com.kingshot.camera.diagnostics.ControlOutcome
import com.kingshot.camera.diagnostics.Disclosures
import com.kingshot.camera.diagnostics.ExposureInfo
import com.kingshot.camera.diagnostics.OutputRecord
import com.kingshot.camera.diagnostics.ProcessingReceipt

/**
 * Collects the pieces of one capture (result, JPEG, optional RAW) as they arrive on different threads and
 * builds the Processing Receipt when everything expected has either been saved or has failed.
 */
class ReceiptAssembler(
    val captureId: String,
    private val capturedAtIso: String,
    private val profile: String,
    private val logicalCameraId: String,
    private val expectRaw: Boolean,
    private val shutterAtMs: Long
) {
    private var controls: List<ControlOutcome> = emptyList()
    private var exposure = ExposureInfo()
    private var activePhysicalId: String? = null
    private var zoomRatio: Float? = null
    private var resultSeen = false
    private var resultMissing = false
    private val outputs = mutableListOf<OutputRecord>()
    private val failures = mutableMapOf<String, String>()
    private val extraDisclosures = mutableListOf<String>()
    private var firstImageAtMs: Long? = null
    private var lastSavedAtMs: Long? = null

    @Synchronized
    fun onResult(controls: List<ControlOutcome>, exposure: ExposureInfo, activePhysicalId: String?, zoomRatio: Float?, notes: List<String>) {
        this.controls = controls
        this.exposure = exposure
        this.activePhysicalId = activePhysicalId
        this.zoomRatio = zoomRatio
        extraDisclosures += notes
        resultSeen = true
    }

    /** The image arrived but no result carried a matching sensor timestamp. */
    @Synchronized
    fun onNoMatchingResult() {
        resultMissing = true
        extraDisclosures += "No capture result matched this image's timestamp, so requested-versus-reported values are unknown."
    }

    @Synchronized
    fun onImageArrived(nowMs: Long) {
        if (firstImageAtMs == null) firstImageAtMs = nowMs
    }

    @Synchronized
    fun onOutputSaved(record: OutputRecord, nowMs: Long) {
        outputs += record
        lastSavedAtMs = nowMs
    }

    @Synchronized
    fun onOutputFailed(role: String, reason: String) {
        failures[role] = reason
    }

    private fun expectedRoles(): List<String> = if (expectRaw) listOf("jpeg", "source_raw") else listOf("jpeg")

    @Synchronized
    fun isComplete(): Boolean {
        val resultKnown = resultSeen || resultMissing
        val allOutputs = expectedRoles().all { role -> outputs.any { it.role == role } || failures.containsKey(role) }
        return resultKnown && allOutputs
    }

    @Synchronized
    fun build(thermalStatus: String): ProcessingReceipt {
        val disclosures = mutableListOf(Disclosures.JPEG_MAY_BE_PROCESSED)
        disclosures += extraDisclosures
        failures.forEach { (role, reason) -> disclosures += "Output '$role' was not saved: $reason" }
        val saved = lastSavedAtMs
        val first = firstImageAtMs
        return ProcessingReceipt(
            captureId = captureId,
            capturedAt = capturedAtIso,
            profile = profile,
            logicalCameraId = logicalCameraId,
            activePhysicalId = activePhysicalId,
            zoomRatio = zoomRatio,
            outputs = outputs.toList(),
            controls = controls,
            exposure = exposure,
            disclosures = disclosures,
            shutterToSavedMs = saved?.let { it - shutterAtMs },
            processingMs = if (saved != null && first != null) saved - first else null,
            thermalStatus = thermalStatus,
            pairId = captureId
        )
    }
}
