package com.kingshot.camera.capabilities

/** Width x height in pixels. Own type so the model has no Android dependency. */
data class Dim(val width: Int, val height: Int) {
    val pixels: Long get() = width.toLong() * height
    override fun toString(): String = "${width}x$height"
}

/** Sizes available for one output format (or for the SurfaceTexture preview target, format = [FormatNames.SURFACE_TEXTURE]). */
data class FormatOutputs(val format: Int, val name: String, val sizes: List<Dim>)

/**
 * Immutable snapshot of what one camera ID reports at runtime. Built from CameraCharacteristics by
 * an adapter; every decision in the app is made on this model, never on the device model name.
 *
 * A value the device did not report is `null` (or an empty list) and the reason is recorded in [missing].
 */
data class CameraCapabilities(
    val cameraId: String,
    /** Set when this entry was reached as a physical sub-camera of a logical camera. */
    val physicalOf: String? = null,
    val physicalIds: List<String> = emptyList(),
    val lensFacing: String? = null,
    val hardwareLevel: String? = null,
    val capabilities: List<String> = emptyList(),
    val sensorOrientation: Int? = null,
    val activeArray: Dim? = null,
    val pixelArray: Dim? = null,
    val physicalSizeMm: String? = null,
    val colorFilterArrangement: String? = null,
    val focalLengthsMm: List<Float> = emptyList(),
    val apertures: List<Float> = emptyList(),
    val minFocusDistanceDiopters: Float? = null,
    val opticalStabilizationModes: List<Int> = emptyList(),
    val videoStabilizationModes: List<Int> = emptyList(),
    val aeModes: List<Int> = emptyList(),
    val afModes: List<Int> = emptyList(),
    val awbModes: List<Int> = emptyList(),
    val aeCompensationRange: Pair<Int, Int>? = null,
    val aeCompensationStep: Double? = null,
    val isoRange: Pair<Int, Int>? = null,
    val exposureTimeRangeNs: Pair<Long, Long>? = null,
    val zoomRatioRange: Pair<Float, Float>? = null,
    val flashAvailable: Boolean? = null,
    val noiseReductionModes: List<Int> = emptyList(),
    val edgeModes: List<Int> = emptyList(),
    val toneMapModes: List<Int> = emptyList(),
    val hotPixelModes: List<Int> = emptyList(),
    val aberrationModes: List<Int> = emptyList(),
    val shadingModes: List<Int> = emptyList(),
    val outputs: List<FormatOutputs> = emptyList(),
    val mandatoryStreamCombinations: List<String> = emptyList(),
    val requestKeys: List<String> = emptyList(),
    val resultKeys: List<String> = emptyList(),
    val sessionKeys: List<String> = emptyList(),
    val extensions: List<Int> = emptyList(),
    /** field name -> why it is null/empty. */
    val missing: Map<String, String> = emptyMap(),
    val notes: List<String> = emptyList()
) {
    private fun sizesOf(format: Int): List<Dim> = outputs.firstOrNull { it.format == format }?.sizes.orEmpty()

    val jpegSizes: List<Dim> get() = sizesOf(FormatNames.JPEG)
    val rawSizes: List<Dim> get() = sizesOf(FormatNames.RAW_SENSOR)
    val previewSizes: List<Dim> get() = sizesOf(FormatNames.SURFACE_TEXTURE)

    fun largestJpeg(): Dim? = jpegSizes.maxByOrNull { it.pixels }
    fun largestRaw(): Dim? = rawSizes.maxByOrNull { it.pixels }

    fun isRear(): Boolean = lensFacing == "BACK"

    /** RAW is offered only when the RAW capability AND a RAW_SENSOR output size both exist for this camera ID. */
    fun supportsRaw(): Boolean = CapabilityNames.RAW in capabilities && rawSizes.isNotEmpty()

    /** NOISE_REDUCTION_MODE_OFF may be requested only if it is listed as available. */
    fun supportsNoiseReductionOff(): Boolean = ProcessingModes.NR_OFF in noiseReductionModes

    /** EDGE_MODE_OFF may be requested only if it is listed as available. */
    fun supportsEdgeOff(): Boolean = ProcessingModes.EDGE_OFF in edgeModes

    fun supportsContinuousPictureAf(): Boolean = ProcessingModes.AF_CONTINUOUS_PICTURE in afModes
}
