package com.kingshot.camera.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.kingshot.camera.capabilities.CameraCapabilities
import com.kingshot.camera.capabilities.Dim
import com.kingshot.camera.capabilities.SizeChooser
import com.kingshot.camera.diagnostics.CaptureLog
import java.util.concurrent.Executor

enum class ImageKind { JPEG, RAW }

/** A metering rectangle in active-array pixels (weight 0..1000). */
data class HostMeter(val x: Int, val y: Int, val width: Int, val height: Int, val weight: Int)

/** Capability-checked values for the preview and still requests. A null means the key is not set. */
data class HostControls(
    val evIndex: Int? = null,
    val zoomRatio: Float? = null,
    val aeMode: Int? = null,
    val meter: HostMeter? = null
)

/** What to set on a still request. Already checked against capabilities by the capture package. */
data class StillRequestSpec(
    val noiseReductionMode: Int?,
    val edgeMode: Int?,
    val useContinuousPictureAf: Boolean,
    val controls: HostControls = HostControls(),
    val precapture: Boolean = false
)

/** Callbacks arrive on the camera thread. Implementations must not block it. */
interface CameraHostListener {
    fun onPreviewSize(width: Int, height: Int)
    fun onSessionReady(rawActive: Boolean)
    fun onSessionProblem(message: String)
    /** Ownership of [image] passes to the listener, which must close it on every path. */
    fun onImage(kind: ImageKind, image: ImageHandle)
    fun onCaptureCompleted(tag: Any?, result: TotalCaptureResult)
    fun onCaptureFailed(tag: Any?, message: String)
}

/**
 * The only class that touches CameraDevice and CameraCaptureSession.
 *
 * All state is confined to one HandlerThread: public methods post work to it, so open/close cannot race.
 * [shutdown] posts the close and then joins the thread, so the device is closed when it returns.
 */
class CameraHost(context: Context, private val log: CaptureLog) {
    private enum class State { IDLE, OPENING, OPEN }
    private enum class Support { SUPPORTED, UNSUPPORTED, UNKNOWN }

    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var listener: CameraHostListener? = null

    // Camera-thread-confined state.
    private var state = State.IDLE
    private var closeRequested = false
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var caps: CameraCapabilities? = null
    private var chars: CameraCharacteristics? = null
    private var texture: SurfaceTexture? = null
    private var previewSurface: Surface? = null
    private var jpegReader: ImageReader? = null
    private var rawReader: ImageReader? = null
    private var wantRaw = false
    private var generation = 0
    private var quitWhenIdle = false
    private var controls = HostControls()
    private var aeState: Int? = null

    @Volatile
    var rawActive: Boolean = false
        private set

    fun start(listener: CameraHostListener) {
        this.listener = listener
        val t = HandlerThread("kingshot-camera").also { it.start() }
        thread = t
        handler = Handler(t.looper)
    }

    /** Posts the close, then waits for the camera thread to finish. Safe to call from onPause. */
    fun shutdown() {
        handler?.post {
            quitWhenIdle = true
            closeInternal("shutdown")
        }
        try {
            thread?.join(3000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        thread = null
        handler = null
        listener = null
    }

    fun open(capabilities: CameraCapabilities, surfaceTexture: SurfaceTexture, wantRaw: Boolean) {
        post {
            if (state != State.IDLE) {
                log.add("open ignored: camera state is $state")
                return@post
            }
            caps = capabilities
            texture = surfaceTexture
            this.wantRaw = wantRaw
            state = State.OPENING
            closeRequested = false
            try {
                chars = manager.getCameraCharacteristics(capabilities.cameraId)
                manager.openCamera(capabilities.cameraId, stateCallback, handler)
            } catch (e: SecurityException) {
                state = State.IDLE
                log.add("openCamera denied: ${e.message}")
            } catch (e: CameraAccessException) {
                state = State.IDLE
                log.add("openCamera failed: ${e.message}")
            }
        }
    }

    fun setRawWanted(want: Boolean) {
        post {
            wantRaw = want
            if (state == State.OPEN) buildSession(want)
        }
    }

    /** Applies new controls to the running preview. Takes effect on the next preview frame. */
    fun setControls(c: HostControls) {
        post {
            controls = c
            if (state == State.OPEN) repeatPreview()
        }
    }

    /** Tap-to-focus: sets the metering regions, fires one AF trigger, then returns to continuous picture AF. */
    fun focusAt(meter: HostMeter) {
        post {
            controls = controls.copy(meter = meter)
            val dev = device
            val sess = session
            val surface = previewSurface
            if (state != State.OPEN || dev == null || sess == null || surface == null) return@post
            try {
                val b = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                b.addTarget(surface)
                applyControls(b, controls, includeAeMode = true)
                b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                b.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
                sess.capture(b.build(), null, handler)
                repeatPreview()
            } catch (e: Exception) {
                log.add("Tap-to-focus failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    fun characteristics(): CameraCharacteristics? = chars

    fun capture(tag: Any, spec: StillRequestSpec, jpegOrientation: Int) {
        post {
            if (spec.precapture) {
                runPrecapture { captureNow(tag, spec, jpegOrientation) }
            } else {
                captureNow(tag, spec, jpegOrientation)
            }
        }
    }

    // Camera thread only.
    private fun captureNow(tag: Any, spec: StillRequestSpec, jpegOrientation: Int) {
        run {
            val dev = device
            val sess = session
            val jr = jpegReader
            val c = caps
            if (state != State.OPEN || dev == null || sess == null || jr == null || c == null) {
                listener?.onCaptureFailed(tag, "camera not ready (state=$state)")
                return
            }
            try {
                val b = dev.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                b.addTarget(jr.surface)
                if (rawActive) rawReader?.let { b.addTarget(it.surface) }
                if (spec.useContinuousPictureAf) {
                    b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                }
                applyControls(b, spec.controls, includeAeMode = true)
                b.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)
                spec.noiseReductionMode?.let { b.set(CaptureRequest.NOISE_REDUCTION_MODE, it) }
                spec.edgeMode?.let { b.set(CaptureRequest.EDGE_MODE, it) }
                b.setTag(tag)
                sess.capture(b.build(), captureCallback, handler)
            } catch (e: Exception) {
                listener?.onCaptureFailed(tag, "capture request error: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    // ------------------------------------------------------------ camera-thread internals

    private fun applyControls(b: CaptureRequest.Builder, c: HostControls, includeAeMode: Boolean) {
        c.evIndex?.let { b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, it) }
        c.zoomRatio?.let { b.set(CaptureRequest.CONTROL_ZOOM_RATIO, it) }
        if (includeAeMode) c.aeMode?.let { b.set(CaptureRequest.CONTROL_AE_MODE, it) }
        val m = c.meter
        val ch = chars
        if (m != null && ch != null) {
            val rect = android.hardware.camera2.params.MeteringRectangle(m.x, m.y, m.width, m.height, m.weight)
            val maxAf = ch.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
            val maxAe = ch.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0
            if (maxAf > 0) b.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(rect))
            if (maxAe > 0) b.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(rect))
        }
    }

    private fun repeatPreview() {
        val dev = device ?: return
        val sess = session ?: return
        val surface = previewSurface ?: return
        val c = caps ?: return
        try {
            val b = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            b.addTarget(surface)
            if (c.supportsContinuousPictureAf()) b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            applyControls(b, controls, includeAeMode = true)
            sess.setRepeatingRequest(b.build(), previewCallback, handler)
        } catch (e: Exception) {
            log.add("Preview update failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Runs the AE precapture sequence (needed before a flash shot), then [then]. Gives up waiting after PRECAPTURE_TIMEOUT_MS. */
    private fun runPrecapture(then: () -> Unit) {
        val dev = device
        val sess = session
        val surface = previewSurface
        val h = handler
        if (state != State.OPEN || dev == null || sess == null || surface == null || h == null) {
            then()
            return
        }
        try {
            val b = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            b.addTarget(surface)
            applyControls(b, controls, includeAeMode = true)
            b.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
            sess.capture(b.build(), null, h)
        } catch (e: Exception) {
            log.add("AE precapture trigger failed: ${e.javaClass.simpleName}: ${e.message}")
            then()
            return
        }
        val gen = generation
        val deadline = android.os.SystemClock.elapsedRealtime() + PRECAPTURE_TIMEOUT_MS
        var sawPrecapture = false
        val poll = object : Runnable {
            override fun run() {
                if (gen != generation || state != State.OPEN) return
                val st = aeState
                if (st == CaptureResult.CONTROL_AE_STATE_PRECAPTURE) sawPrecapture = true
                val settled = st == CaptureResult.CONTROL_AE_STATE_CONVERGED || st == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED
                val timedOut = android.os.SystemClock.elapsedRealtime() >= deadline
                if ((sawPrecapture && settled) || timedOut) {
                    if (timedOut) log.add("AE precapture did not settle within ${PRECAPTURE_TIMEOUT_MS} ms; capturing anyway.")
                    then()
                } else {
                    h.postDelayed(this, PRECAPTURE_POLL_MS)
                }
            }
        }
        h.postDelayed(poll, PRECAPTURE_POLL_MS)
    }

    private val previewCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(s: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            aeState = result.get(CaptureResult.CONTROL_AE_STATE)
        }
    }

    private fun post(block: () -> Unit) {
        handler?.post(block)
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            if (closeRequested || state != State.OPENING) {
                camera.close()
                state = State.IDLE
                closeRequested = false
                quitIfRequested()
                return
            }
            device = camera
            state = State.OPEN
            buildSession(wantRaw)
        }

        override fun onDisconnected(camera: CameraDevice) {
            log.add("Camera disconnected.")
            if (state == State.OPENING) state = State.IDLE
            camera.close()
            device = null
            closeInternal("disconnected")
        }

        override fun onError(camera: CameraDevice, error: Int) {
            log.add("Camera error code $error.")
            if (state == State.OPENING) state = State.IDLE
            camera.close()
            device = null
            closeInternal("error $error")
            listener?.onSessionProblem("Camera error code $error")
        }
    }

    private fun closeInternal(reason: String) {
        if (state == State.OPENING) {
            closeRequested = true // onOpened will close the device
            return
        }
        generation++ // invalidates callbacks from the session being torn down
        try {
            session?.close()
        } catch (_: Exception) {
            // Session may already be closed.
        }
        session = null
        device?.close()
        device = null
        jpegReader?.close()
        jpegReader = null
        rawReader?.close()
        rawReader = null
        previewSurface?.release()
        previewSurface = null
        rawActive = false
        state = State.IDLE
        log.add("Camera closed ($reason).")
        quitIfRequested()
    }

    private fun quitIfRequested() {
        if (quitWhenIdle) thread?.quitSafely()
    }

    private fun checkSupport(dev: CameraDevice, outputs: List<OutputConfiguration>, executor: Executor): Support = try {
        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {}
            override fun onConfigureFailed(session: CameraCaptureSession) {}
        }
        val cfg = SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, executor, callback)
        if (dev.isSessionConfigurationSupported(cfg)) Support.SUPPORTED else Support.UNSUPPORTED
    } catch (e: UnsupportedOperationException) {
        Support.UNKNOWN
    } catch (e: IllegalArgumentException) {
        Support.UNKNOWN
    } catch (e: CameraAccessException) {
        Support.UNKNOWN
    }

    private fun buildSession(requestRaw: Boolean) {
        val dev = device ?: return
        val c = caps ?: return
        val h = handler ?: return
        val tex = texture ?: return
        val executor = Executor { h.post(it) }
        generation++
        val gen = generation

        try {
            session?.close()
        } catch (_: Exception) {
            // Already closed.
        }
        session = null
        rawActive = false
        jpegReader?.close(); jpegReader = null
        rawReader?.close(); rawReader = null
        previewSurface?.release(); previewSurface = null

        val jpegSize = c.largestJpeg()
        if (jpegSize == null) {
            listener?.onSessionProblem("This camera reports no JPEG output size.")
            return
        }
        val preview = SizeChooser.choosePreview(c.previewSizes, jpegSize)
        if (preview == null) {
            listener?.onSessionProblem("This camera reports no preview size.")
            return
        }
        listener?.onPreviewSize(preview.width, preview.height)
        tex.setDefaultBufferSize(preview.width, preview.height)
        val pSurface = Surface(tex)
        previewSurface = pSurface

        val jr = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, JPEG_MAX_IMAGES)
        jr.setOnImageAvailableListener({ r -> deliver(ImageKind.JPEG, r) }, h)
        jpegReader = jr

        var raw = requestRaw
        var rr: ImageReader? = null
        if (raw) {
            val rawSize: Dim? = c.largestRaw()
            if (!c.supportsRaw() || rawSize == null) {
                log.add("RAW requested but this camera does not report RAW capability with a RAW_SENSOR size; using JPEG only.")
                raw = false
            } else {
                rr = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, RAW_MAX_IMAGES)
                rr.setOnImageAvailableListener({ r -> deliver(ImageKind.RAW, r) }, h)
                val probe = listOf(OutputConfiguration(pSurface), OutputConfiguration(jr.surface), OutputConfiguration(rr.surface))
                when (checkSupport(dev, probe, executor)) {
                    Support.SUPPORTED -> log.add("Device reports preview + JPEG + RAW session is supported.")
                    Support.UNSUPPORTED -> {
                        log.add("Device reports preview + JPEG + RAW is NOT supported together; using JPEG only.")
                        listener?.onSessionProblem("JPEG + RAW together is not supported by this camera; capturing JPEG only.")
                        rr.close()
                        rr = null
                        raw = false
                    }
                    Support.UNKNOWN -> log.add("Device cannot pre-check the JPEG + RAW combination; attempting it.")
                }
            }
        }
        rawReader = rr

        val outputs = mutableListOf(OutputConfiguration(pSurface), OutputConfiguration(jr.surface))
        rr?.let { outputs += OutputConfiguration(it.surface) }
        val attemptedRaw = rr != null

        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                if (gen != generation || state != State.OPEN) {
                    s.close()
                    return
                }
                session = s
                try {
                    rawActive = attemptedRaw
                    repeatPreview()
                    log.add("Session ready: preview + JPEG${if (attemptedRaw) " + RAW" else ""}.")
                    listener?.onSessionReady(attemptedRaw)
                } catch (e: Exception) {
                    listener?.onSessionProblem("Preview start failed: ${e.message}")
                }
            }

            override fun onConfigureFailed(s: CameraCaptureSession) {
                if (gen != generation) return
                if (attemptedRaw) {
                    log.add("Session configuration failed with RAW; retrying JPEG only.")
                    listener?.onSessionProblem("JPEG + RAW session could not be configured; capturing JPEG only.")
                    wantRaw = false
                    buildSession(false)
                } else {
                    listener?.onSessionProblem("Session configuration failed.")
                }
            }
        }
        val cfg = SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, executor, callback)
        try {
            dev.createCaptureSession(cfg)
        } catch (e: Exception) {
            listener?.onSessionProblem("createCaptureSession failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun deliver(kind: ImageKind, reader: ImageReader) {
        val image = try {
            reader.acquireNextImage()
        } catch (e: IllegalStateException) {
            log.add("acquireNextImage failed for $kind (too many images held): ${e.message}")
            null
        } ?: return
        val l = listener
        if (l == null) {
            image.close()
            return
        }
        l.onImage(kind, ImageHandle(image))
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(s: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            listener?.onCaptureCompleted(request.tag, result)
        }

        override fun onCaptureFailed(s: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
            listener?.onCaptureFailed(request.tag, "capture failed, reason=${failure.reason}")
        }
    }

    companion object {
        private const val JPEG_MAX_IMAGES = 3
        private const val RAW_MAX_IMAGES = 3
        private const val PRECAPTURE_TIMEOUT_MS = 2000L
        private const val PRECAPTURE_POLL_MS = 50L
    }
}
