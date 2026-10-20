package com.kingshot.camera.diagnostics

import com.kingshot.camera.capabilities.CameraCapabilities
import com.kingshot.camera.capabilities.ProcessingModes

data class DeviceInfo(
    val manufacturer: String?,
    val model: String?,
    val product: String?,
    val fingerprint: String?,
    val sdkInt: Int,
    val securityPatch: String?,
    val release: String?
)

data class AppInfo(val version: String, val buildType: String)

/**
 * Builds the Capability Report (one JSON file plus a readable twin). A value the device did not report is
 * written as null with a reason under `missing`; nothing is omitted. Device facts are recorded for diagnosis only.
 */
object CapabilityReport {
    const val SCHEMA_VERSION = 1

    fun toMap(generatedAt: String, app: AppInfo, device: DeviceInfo, cameras: List<CameraCapabilities>): Map<String, Any?> =
        linkedMapOf(
            "schemaVersion" to SCHEMA_VERSION,
            "generatedAt" to generatedAt,
            "app" to linkedMapOf("version" to app.version, "buildType" to app.buildType),
            "device" to linkedMapOf(
                "manufacturer" to device.manufacturer, "model" to device.model, "product" to device.product,
                "buildFingerprint" to device.fingerprint, "sdkLevel" to device.sdkInt,
                "securityPatch" to device.securityPatch, "release" to device.release
            ),
            "cameras" to cameras.map { cameraMap(it) }
        )

    fun toJson(generatedAt: String, app: AppInfo, device: DeviceInfo, cameras: List<CameraCapabilities>): String =
        Json.encode(toMap(generatedAt, app, device, cameras))

    fun toText(generatedAt: String, app: AppInfo, device: DeviceInfo, cameras: List<CameraCapabilities>): String =
        Json.toText(toMap(generatedAt, app, device, cameras), "KingShot capability report")

    /** Names of fields that are null/empty in the model; each must carry a reason in the output. */
    fun cameraMap(c: CameraCapabilities): Map<String, Any?> {
        val reasons = LinkedHashMap<String, String>()
        fun <T> f(name: String, value: T?): T? {
            if (value == null) reasons[name] = c.missing[name] ?: "not reported by the device (no reason recorded)"
            return value
        }
        fun <T> l(name: String, value: List<T>): List<T> {
            if (value.isEmpty()) reasons[name] = c.missing[name] ?: "none reported"
            return value
        }

        val map = linkedMapOf<String, Any?>(
            "identity" to linkedMapOf(
                "cameraId" to c.cameraId,
                "physicalOf" to c.physicalOf,
                "isLogical" to c.physicalIds.isNotEmpty(),
                "physicalIds" to c.physicalIds,
                "lensFacing" to f("lensFacing", c.lensFacing),
                "hardwareLevel" to f("hardwareLevel", c.hardwareLevel),
                "capabilities" to l("capabilities", c.capabilities)
            ),
            "sensor" to linkedMapOf(
                "activeArray" to f("activeArray", c.activeArray?.toString()),
                "pixelArray" to f("pixelArray", c.pixelArray?.toString()),
                "physicalSizeMm" to f("physicalSizeMm", c.physicalSizeMm),
                "orientation" to f("sensorOrientation", c.sensorOrientation),
                "colorFilterArrangement" to f("colorFilterArrangement", c.colorFilterArrangement)
            ),
            "lens" to linkedMapOf(
                "focalLengthsMm" to l("focalLengthsMm", c.focalLengthsMm),
                "apertures" to l("apertures", c.apertures),
                "minFocusDistanceDiopters" to f("minFocusDistanceDiopters", c.minFocusDistanceDiopters),
                "opticalStabilizationModes" to l("opticalStabilizationModes", c.opticalStabilizationModes),
                "videoStabilizationModes" to l("videoStabilizationModes", c.videoStabilizationModes)
            ),
            "controls" to linkedMapOf(
                "aeModes" to l("aeModes", c.aeModes),
                "aeCompensationRange" to f("aeCompensationRange", c.aeCompensationRange),
                "aeCompensationStep" to f("aeCompensationStep", c.aeCompensationStep),
                "isoRange" to f("isoRange", c.isoRange),
                "exposureTimeRangeNs" to f("exposureTimeRangeNs", c.exposureTimeRangeNs),
                "afModes" to l("afModes", c.afModes),
                "awbModes" to l("awbModes", c.awbModes),
                "zoomRatioRange" to f("zoomRatioRange", c.zoomRatioRange),
                "flashAvailable" to f("flashAvailable", c.flashAvailable)
            ),
            "processingModes" to linkedMapOf(
                "noiseReduction" to l("noiseReductionModes", c.noiseReductionModes).map { ProcessingModes.noiseReductionName(it) },
                "edge" to l("edgeModes", c.edgeModes).map { ProcessingModes.edgeName(it) },
                "toneMap" to l("toneMapModes", c.toneMapModes),
                "hotPixel" to l("hotPixelModes", c.hotPixelModes),
                "aberration" to l("aberrationModes", c.aberrationModes),
                "shading" to l("shadingModes", c.shadingModes)
            ),
            "derived" to linkedMapOf(
                "supportsRaw" to c.supportsRaw(),
                "supportsNoiseReductionOff" to c.supportsNoiseReductionOff(),
                "supportsEdgeOff" to c.supportsEdgeOff()
            ),
            "outputs" to linkedMapOf(
                "formats" to l("outputs", c.outputs).map {
                    linkedMapOf("format" to it.name, "count" to it.sizes.size, "largest" to it.sizes.maxByOrNull { d -> d.pixels }?.toString())
                },
                "rawSizes" to l("rawSizes", c.rawSizes).map { it.toString() },
                "jpegSizes" to l("jpegSizes", c.jpegSizes).map { it.toString() },
                "mandatoryStreamCombinations" to l("mandatoryStreamCombinations", c.mandatoryStreamCombinations)
            ),
            "keys" to linkedMapOf(
                "request" to l("requestKeys", c.requestKeys),
                "result" to l("resultKeys", c.resultKeys),
                "session" to l("sessionKeys", c.sessionKeys)
            ),
            "extensions" to l("extensions", c.extensions),
            "notes" to c.notes
        )
        map["missing"] = reasons
        return map
    }
}
