package com.kingshot.camera.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.kingshot.camera.capabilities.CameraCapabilities
import com.kingshot.camera.capabilities.CapabilityNames
import com.kingshot.camera.capabilities.Dim
import com.kingshot.camera.capabilities.FormatNames
import com.kingshot.camera.capabilities.FormatOutputs

/**
 * Thin adapter: CameraCharacteristics -> immutable [CameraCapabilities]. Contains no decisions.
 * Anything missing or throwing is recorded as null with a reason; the report never omits a field.
 */
object CharacteristicsReader {
    private class Reasons {
        val map = LinkedHashMap<String, String>()

        inline fun <T> read(name: String, block: () -> T?): T? {
            return try {
                val v = block()
                if (v == null) map[name] = "not reported by the device (null from CameraCharacteristics)"
                v
            } catch (e: Exception) {
                map[name] = "${e.javaClass.simpleName}: ${e.message}"
                null
            }
        }

        inline fun <T> list(name: String, block: () -> List<T>?): List<T> {
            val v = read(name, block)
            return v ?: emptyList()
        }
    }

    fun readAll(context: Context): List<CameraCapabilities> =
        readAll(context.getSystemService(Context.CAMERA_SERVICE) as CameraManager)

    /** Reads every logical camera ID, then every physical ID those report (once each). */
    fun readAll(manager: CameraManager): List<CameraCapabilities> {
        val out = mutableListOf<CameraCapabilities>()
        val seen = HashSet<String>()
        for (id in manager.cameraIdList) {
            val c = try {
                manager.getCameraCharacteristics(id)
            } catch (e: Exception) {
                out += CameraCapabilities(id, notes = listOf("getCameraCharacteristics failed: ${e.javaClass.simpleName}: ${e.message}"))
                continue
            }
            seen += id
            val logical = read(manager, id, c, null)
            out += logical
            for (pid in logical.physicalIds) {
                if (!seen.add(pid)) continue
                try {
                    out += read(manager, pid, manager.getCameraCharacteristics(pid), id)
                } catch (e: Exception) {
                    out += CameraCapabilities(
                        pid, physicalOf = id,
                        notes = listOf("Physical camera characteristics not readable: ${e.javaClass.simpleName}: ${e.message}")
                    )
                }
            }
        }
        return out
    }

    fun read(manager: CameraManager, id: String, c: CameraCharacteristics, physicalOf: String?): CameraCapabilities {
        val r = Reasons()
        val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        if (map == null) r.map["outputs"] = "SCALER_STREAM_CONFIGURATION_MAP not reported"

        val outputs = mutableListOf<FormatOutputs>()
        if (map != null) {
            for (f in map.outputFormats) {
                val sizes = map.getOutputSizes(f)?.map { Dim(it.width, it.height) }.orEmpty()
                outputs += FormatOutputs(f, FormatNames.of(f), sizes)
            }
            val preview = map.getOutputSizes(SurfaceTexture::class.java)?.map { Dim(it.width, it.height) }.orEmpty()
            outputs += FormatOutputs(FormatNames.SURFACE_TEXTURE, FormatNames.of(FormatNames.SURFACE_TEXTURE), preview)
        }

        val aeStep = r.read("aeCompensationStep") { c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toDouble() }
        val ae = r.read("aeCompensationRange") { c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) }
        val iso = r.read("isoRange") { c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) }
        val expo = r.read("exposureTimeRangeNs") { c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) }
        val zoom = r.read("zoomRatioRange") { c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) }
        val active = r.read("activeArray") { c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) }
        val pixel = r.read("pixelArray") { c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE) }
        val phys = r.read("physicalSizeMm") { c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) }

        return CameraCapabilities(
            cameraId = id,
            physicalOf = physicalOf,
            physicalIds = r.list("physicalIds") { c.physicalCameraIds.toList() },
            lensFacing = r.read("lensFacing") {
                when (c.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                    CameraCharacteristics.LENS_FACING_BACK -> "BACK"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
                    else -> null
                }
            },
            hardwareLevel = r.read("hardwareLevel") {
                when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
                    else -> null
                }
            },
            capabilities = r.list("capabilities") {
                c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.map { CapabilityNames.of(it) }
            },
            sensorOrientation = r.read("sensorOrientation") { c.get(CameraCharacteristics.SENSOR_ORIENTATION) },
            activeArray = active?.let { Dim(it.width(), it.height()) },
            pixelArray = pixel?.let { Dim(it.width, it.height) },
            physicalSizeMm = phys?.let { "${it.width}x${it.height}" },
            colorFilterArrangement = r.read("colorFilterArrangement") {
                when (c.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)) {
                    0 -> "RGGB"
                    1 -> "GRBG"
                    2 -> "GBRG"
                    3 -> "BGGR"
                    4 -> "RGB"
                    null -> null
                    else -> "UNKNOWN"
                }
            },
            focalLengthsMm = r.list("focalLengthsMm") { c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList() },
            apertures = r.list("apertures") { c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList() },
            minFocusDistanceDiopters = r.read("minFocusDistanceDiopters") { c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) },
            opticalStabilizationModes = r.list("opticalStabilizationModes") {
                c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.toList()
            },
            videoStabilizationModes = r.list("videoStabilizationModes") {
                c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)?.toList()
            },
            aeModes = r.list("aeModes") { c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.toList() },
            afModes = r.list("afModes") { c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toList() },
            awbModes = r.list("awbModes") { c.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toList() },
            aeCompensationRange = ae?.let { Pair(it.lower, it.upper) },
            aeCompensationStep = aeStep,
            isoRange = iso?.let { Pair(it.lower, it.upper) },
            exposureTimeRangeNs = expo?.let { Pair(it.lower, it.upper) },
            zoomRatioRange = zoom?.let { Pair(it.lower, it.upper) },
            flashAvailable = r.read("flashAvailable") { c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) },
            noiseReductionModes = r.list("noiseReductionModes") {
                c.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)?.toList()
            },
            edgeModes = r.list("edgeModes") { c.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)?.toList() },
            toneMapModes = r.list("toneMapModes") { c.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES)?.toList() },
            hotPixelModes = r.list("hotPixelModes") { c.get(CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES)?.toList() },
            aberrationModes = r.list("aberrationModes") {
                c.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES)?.toList()
            },
            shadingModes = r.list("shadingModes") { c.get(CameraCharacteristics.SHADING_AVAILABLE_MODES)?.toList() },
            outputs = outputs,
            mandatoryStreamCombinations = r.list("mandatoryStreamCombinations") {
                c.get(CameraCharacteristics.SCALER_MANDATORY_STREAM_COMBINATIONS)?.map { it.description.toString() }
            },
            requestKeys = r.list("requestKeys") { c.availableCaptureRequestKeys.map { it.name } },
            resultKeys = r.list("resultKeys") { c.availableCaptureResultKeys.map { it.name } },
            sessionKeys = r.list("sessionKeys") { c.availableSessionKeys?.map { it.name } },
            extensions = r.list("extensions") { manager.getCameraExtensionCharacteristics(id).supportedExtensions },
            missing = r.map
        )
    }
}
