package com.kingshot.lab

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * KingShot Camera Lab (prototype).
 *
 * Feasibility tool: inspects Camera2 capabilities, captures JPEG (+ optional RAW/DNG),
 * requests noise-reduction / edge-enhancement OFF only where the device lists support,
 * and logs requested vs. returned values. It does NOT claim "unprocessed" output.
 */
class MainActivity : Activity() {

    private lateinit var textureView: AutoFitTextureView
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var chkMinimal: CheckBox
    private lateinit var chkRaw: CheckBox
    private lateinit var btnShutter: Button

    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var characteristics: CameraCharacteristics? = null

    private var cameraDevice: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var previewBuilder: CaptureRequest.Builder? = null

    private var jpegReader: ImageReader? = null
    private var rawReader: ImageReader? = null
    private var rawEnabledInSession = false
    private var previewSize: Size = Size(1440, 1080)

    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null

    // Pending RAW image / capture result, matched by sensor timestamp.
    private val pendingRaw = ConcurrentHashMap<Long, Image>()
    private val pendingResults = ConcurrentHashMap<Long, TotalCaptureResult>()

    private val timeFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textureView = findViewById(R.id.preview)
        logView = findViewById(R.id.log)
        logScroll = findViewById(R.id.logScroll)
        chkMinimal = findViewById(R.id.chkMinimal)
        chkRaw = findViewById(R.id.chkRaw)
        btnShutter = findViewById(R.id.btnShutter)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        btnShutter.setOnClickListener { capture() }
        chkRaw.setOnCheckedChangeListener { _, _ ->
            // Session outputs depend on RAW selection; rebuild it.
            if (cameraDevice != null) createSession(includeRaw = chkRaw.isChecked)
        }
    }

    override fun onResume() {
        super.onResume()
        startBackground()
        if (textureView.isAvailable) {
            ensurePermissionThenOpen()
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) =
                    ensurePermissionThenOpen()

                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
            }
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackground()
        super.onPause()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == REQ_CAMERA) {
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                log("Camera permission denied. The Camera Lab cannot run without it.")
            }
        }
    }

    // ---------------------------------------------------------------- setup

    private fun ensurePermissionThenOpen() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        }
    }

    private fun startBackground() {
        val t = HandlerThread("kingshot-camera").also { it.start() }
        bgThread = t
        bgHandler = Handler(t.looper)
    }

    private fun stopBackground() {
        bgThread?.quitSafely()
        try {
            bgThread?.join()
        } catch (_: InterruptedException) {
        }
        bgThread = null
        bgHandler = null
    }

    private fun openCamera() {
        if (cameraDevice != null) return
        try {
            val id = cameraManager.cameraIdList.firstOrNull {
                cameraManager.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            if (id == null) {
                log("No rear camera found.")
                return
            }
            cameraId = id
            characteristics = cameraManager.getCameraCharacteristics(id)
            logCapabilityReport()
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    runOnUiThread { createSession(includeRaw = chkRaw.isChecked) }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                    log("Camera disconnected.")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    log("Camera error code $error.")
                }
            }, bgHandler)
        } catch (e: SecurityException) {
            log("Permission error: ${e.message}")
        } catch (e: CameraAccessException) {
            log("Camera access error: ${e.message}")
        }
    }

    private fun closeCamera() {
        try {
            session?.close()
        } catch (_: Exception) {
        }
        session = null
        cameraDevice?.close()
        cameraDevice = null
        jpegReader?.close()
        jpegReader = null
        rawReader?.close()
        rawReader = null
        pendingRaw.values.forEach { it.close() }
        pendingRaw.clear()
        pendingResults.clear()
    }

    // ---------------------------------------------------------------- capabilities

    private fun caps(): CameraCharacteristics = characteristics!!

    private fun hasRawCapability(): Boolean {
        val c = caps()
        val hasCap = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) == true
        val sizes = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(ImageFormat.RAW_SENSOR)
        return hasCap && !sizes.isNullOrEmpty()
    }

    private fun supportsNrOff() = caps()
        .get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
        ?.contains(CaptureRequest.NOISE_REDUCTION_MODE_OFF) == true

    private fun supportsEdgeOff() = caps()
        .get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)
        ?.contains(CaptureRequest.EDGE_MODE_OFF) == true

    private fun logCapabilityReport() {
        val c = caps()
        val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val level = when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "unknown"
        }
        log("=== Capability report ===")
        log("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
        log("Build: ${Build.FINGERPRINT}")
        log("Camera IDs: ${cameraManager.cameraIdList.joinToString()}")
        log("Active rear camera ID: $cameraId   hardware level: $level")
        log("Physical IDs: ${c.physicalCameraIds.joinToString().ifEmpty { "(none; not a logical camera)" }}")
        log("Focal lengths (mm): ${c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.joinToString()}")
        log("Sensor orientation: ${c.get(CameraCharacteristics.SENSOR_ORIENTATION)}")
        log("JPEG sizes (largest): ${map?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height }}")
        log("RAW capability + RAW_SENSOR sizes: ${hasRawCapability()} ${map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.maxByOrNull { it.width.toLong() * it.height } ?: ""}")
        log("NR modes available: ${c.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)?.joinToString()}  (OFF=0 supported: ${supportsNrOff()})")
        log("Edge modes available: ${c.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)?.joinToString()}  (OFF=0 supported: ${supportsEdgeOff()})")
        log("=========================")
        runOnUiThread {
            chkRaw.isEnabled = hasRawCapability()
            if (!hasRawCapability()) {
                chkRaw.isChecked = false
                chkRaw.text = "RAW unsupported"
            }
        }
    }

    // ---------------------------------------------------------------- session

    private fun chooseSizes(includeRaw: Boolean) {
        val map = caps().get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val jpegSize = map.getOutputSizes(ImageFormat.JPEG).maxByOrNull { it.width.toLong() * it.height }!!
        val aspect = jpegSize.width.toFloat() / jpegSize.height
        previewSize = map.getOutputSizes(SurfaceTexture::class.java)
            .filter { it.width <= 1920 && Math.abs(it.width.toFloat() / it.height - aspect) < 0.02f }
            .maxByOrNull { it.width.toLong() * it.height }
            ?: map.getOutputSizes(SurfaceTexture::class.java).first()

        jpegReader?.close()
        jpegReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2).also {
            it.setOnImageAvailableListener({ r -> onJpegAvailable(r) }, bgHandler)
        }

        rawReader?.close()
        rawReader = null
        if (includeRaw && hasRawCapability()) {
            val rawSize = map.getOutputSizes(ImageFormat.RAW_SENSOR).maxByOrNull { it.width.toLong() * it.height }!!
            rawReader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 3).also {
                it.setOnImageAvailableListener({ r -> onRawAvailable(r) }, bgHandler)
            }
        }
    }

    private fun createSession(includeRaw: Boolean) {
        val device = cameraDevice ?: return
        try {
            session?.close()
            session = null
            chooseSizes(includeRaw)

            // Preview is portrait on screen; sensor buffers are landscape.
            textureView.setAspectRatio(previewSize.height, previewSize.width)
            val st = textureView.surfaceTexture ?: return
            st.setDefaultBufferSize(previewSize.width, previewSize.height)
            val previewSurface = Surface(st)

            val outputs = mutableListOf(
                OutputConfiguration(previewSurface),
                OutputConfiguration(jpegReader!!.surface)
            )
            rawReader?.let { outputs.add(OutputConfiguration(it.surface)) }
            rawEnabledInSession = rawReader != null

            previewBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }

            val config = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputs,
                { r -> bgHandler?.post(r) },
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        session = s
                        try {
                            s.setRepeatingRequest(previewBuilder!!.build(), null, bgHandler)
                            log("Session ready (preview + JPEG${if (rawEnabledInSession) " + RAW" else ""}).")
                        } catch (e: Exception) {
                            log("Preview start failed: ${e.message}")
                        }
                    }

                    override fun onConfigureFailed(s: CameraCaptureSession) {
                        log("Session configuration failed" +
                            if (includeRaw) " with RAW; this device may not support JPEG+RAW together. Falling back to JPEG only." else ".")
                        if (includeRaw) {
                            runOnUiThread {
                                chkRaw.isChecked = false // listener rebuilds the session without RAW
                            }
                        }
                    }
                }
            )
            device.createCaptureSession(config)
        } catch (e: Exception) {
            log("createSession error: ${e.message}")
        }
    }

    // ---------------------------------------------------------------- capture

    private fun capture() {
        val device = cameraDevice
        val s = session
        val jr = jpegReader
        if (device == null || s == null || jr == null) {
            log("Camera not ready.")
            return
        }
        try {
            val b = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            b.addTarget(jr.surface)
            if (rawEnabledInSession) rawReader?.let { b.addTarget(it.surface) }
            b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            b.set(CaptureRequest.JPEG_ORIENTATION, caps().get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90)
            b.set(CaptureRequest.JPEG_QUALITY, 97.toByte())

            var requestedNr: Int? = null
            var requestedEdge: Int? = null
            // (final values are copied below so the callback sees stable vals)
            if (chkMinimal.isChecked) {
                if (supportsNrOff()) {
                    requestedNr = CaptureRequest.NOISE_REDUCTION_MODE_OFF
                    b.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
                } else log("NR OFF not listed by device; not requested.")
                if (supportsEdgeOff()) {
                    requestedEdge = CaptureRequest.EDGE_MODE_OFF
                    b.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
                } else log("Edge OFF not listed by device; not requested.")
            }

            val reqNr = requestedNr
            val reqEdge = requestedEdge
            s.capture(b.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    sess: CameraCaptureSession, req: CaptureRequest, result: TotalCaptureResult
                ) {
                    val ts = result.get(CaptureResult.SENSOR_TIMESTAMP)
                    log("--- Capture result ---")
                    log("Requested NR=${reqNr ?: "default"}  returned NR=${result.get(CaptureResult.NOISE_REDUCTION_MODE)}")
                    log("Requested EDGE=${reqEdge ?: "default"}  returned EDGE=${result.get(CaptureResult.EDGE_MODE)}")
                    log("ISO=${result.get(CaptureResult.SENSOR_SENSITIVITY)}  exposure(ns)=${result.get(CaptureResult.SENSOR_EXPOSURE_TIME)}  focal=${result.get(CaptureResult.LENS_FOCAL_LENGTH)}")
                    if (ts != null && rawEnabledInSession) {
                        pendingResults[ts] = result
                        tryWriteDng(ts)
                    }
                }

                override fun onCaptureFailed(
                    sess: CameraCaptureSession, req: CaptureRequest,
                    failure: android.hardware.camera2.CaptureFailure
                ) {
                    log("Capture failed, reason=${failure.reason}")
                }
            }, bgHandler)
        } catch (e: Exception) {
            log("Capture error: ${e.message}")
        }
    }

    private fun onJpegAvailable(reader: ImageReader) {
        val image = reader.acquireNextImage() ?: return
        try {
            val buf = image.planes[0].buffer
            val bytes = ByteArray(buf.remaining())
            buf.get(bytes)
            val name = "KS_${timeFmt.format(Date())}_${image.timestamp % 1000}.jpg"
            val uri = saveToMediaStore(name, "image/jpeg") { it.write(bytes) }
            log(if (uri != null) "Saved JPEG: Pictures/KingShot/$name (${bytes.size / 1024} KB)" else "JPEG save FAILED.")
        } catch (e: Exception) {
            log("JPEG error: ${e.message}")
        } finally {
            image.close()
        }
    }

    private fun onRawAvailable(reader: ImageReader) {
        val image = reader.acquireNextImage() ?: return
        pendingRaw[image.timestamp] = image
        tryWriteDng(image.timestamp)
    }

    /** Writes the DNG once both the RAW image and its matching TotalCaptureResult have arrived. */
    @Synchronized
    private fun tryWriteDng(ts: Long) {
        val image = pendingRaw[ts] ?: return
        val result = pendingResults[ts] ?: return
        pendingRaw.remove(ts)
        pendingResults.remove(ts)
        try {
            val dng = DngCreator(caps(), result)
            dng.setOrientation(android.media.ExifInterface.ORIENTATION_ROTATE_90)
            val name = "KS_${timeFmt.format(Date())}_${ts % 1000}.dng"
            val uri = saveToMediaStore(name, "image/x-adobe-dng") { dng.writeImage(it, image) }
            dng.close()
            log(if (uri != null) "Saved DNG: Pictures/KingShot/$name" else "DNG save FAILED.")
        } catch (e: Exception) {
            log("DNG error: ${e.message}")
        } finally {
            image.close()
        }
    }

    private fun saveToMediaStore(
        name: String, mime: String, writer: (java.io.OutputStream) -> Unit
    ): android.net.Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KingShot")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use(writer) ?: throw java.io.IOException("no stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null) // storage failure: do not leave a partial file
            log("Storage error: ${e.message}")
            null
        }
    }

    // ---------------------------------------------------------------- logging

    private fun log(msg: String) {
        runOnUiThread {
            logView.append(msg + "\n")
            logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    companion object {
        private const val REQ_CAMERA = 100
    }
}
