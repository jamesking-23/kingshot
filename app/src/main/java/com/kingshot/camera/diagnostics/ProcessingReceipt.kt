package com.kingshot.camera.diagnostics

/** Fixed status vocabulary from the spec. The UI shows [plain] wording. */
enum class ControlStatus(val wire: String, val plain: String) {
    HONORED("honored", "honored by the device"),
    DIFFERS("differs", "device reported something different"),
    UNAVAILABLE("unavailable", "not available on this camera"),
    NOT_REQUESTED("not_requested", "not requested")
}

data class ControlOutcome(
    val name: String,
    val requested: String?,
    val reported: String?,
    val status: ControlStatus,
    val note: String? = null
)

data class ExposureInfo(
    val exposureTimeNs: Long? = null,
    val iso: Int? = null,
    val evCompSteps: Int? = null,
    val aeState: String? = null,
    val afState: String? = null,
    val awbState: String? = null,
    /** android.control.postRawSensitivityBoost as reported (100 = no boost); helps explain EXIF ISO vs sensor ISO. */
    val postRawSensitivityBoost: Int? = null
)

data class OutputRecord(
    val role: String,
    val format: String,
    val uri: String,
    val size: String?,
    val bytes: Long
)

data class ProcessingReceipt(
    val captureId: String,
    val capturedAt: String,
    val profile: String,
    val logicalCameraId: String,
    val activePhysicalId: String?,
    val zoomRatio: Float?,
    val outputs: List<OutputRecord>,
    val controls: List<ControlOutcome>,
    val exposure: ExposureInfo,
    val disclosures: List<String>,
    val shutterToSavedMs: Long?,
    val processingMs: Long?,
    val thermalStatus: String,
    val pairId: String,
    val derivedFrom: String? = null
) {
    fun toJsonMap(): Map<String, Any?> = linkedMapOf(
        "schemaVersion" to 1,
        "captureId" to captureId,
        "capturedAt" to capturedAt,
        "profile" to profile,
        "camera" to linkedMapOf(
            "logicalId" to logicalCameraId,
            "activePhysicalId" to activePhysicalId,
            "lensLabel" to null,
            "zoomRatio" to zoomRatio,
            "digitalCrop" to null
        ),
        "outputs" to outputs.map {
            linkedMapOf("role" to it.role, "format" to it.format, "uri" to it.uri, "size" to it.size, "bytes" to it.bytes)
        },
        "controls" to controls.map {
            linkedMapOf(
                "name" to it.name, "requested" to it.requested, "reported" to it.reported,
                "status" to it.status.wire, "note" to it.note
            )
        },
        "exposure" to linkedMapOf(
            "exposureTimeNs" to exposure.exposureTimeNs, "iso" to exposure.iso, "evCompSteps" to exposure.evCompSteps,
            "aeState" to exposure.aeState, "afState" to exposure.afState, "awbState" to exposure.awbState,
            "postRawSensitivityBoost" to exposure.postRawSensitivityBoost
        ),
        "disclosures" to disclosures,
        "timing" to linkedMapOf("shutterToSavedMs" to shutterToSavedMs, "processingMs" to processingMs),
        "thermalStatus" to thermalStatus,
        "pairing" to linkedMapOf("pairId" to pairId, "role" to "source", "derivedFrom" to derivedFrom)
    )

    fun toJson(): String = Json.encode(toJsonMap())
}

object Disclosures {
    const val JPEG_MAY_BE_PROCESSED = "The JPEG may still be processed by the device."
}
