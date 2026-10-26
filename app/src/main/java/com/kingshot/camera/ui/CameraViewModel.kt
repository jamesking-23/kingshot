package com.kingshot.camera.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import com.kingshot.camera.camera.CameraHost
import com.kingshot.camera.camera.CharacteristicsReader
import com.kingshot.camera.capabilities.CameraCapabilities
import com.kingshot.camera.capabilities.CameraSelector
import com.kingshot.camera.capture.LabEvents
import com.kingshot.camera.capture.ProcessingChoice
import com.kingshot.camera.capture.StillCaptureOrchestrator
import com.kingshot.camera.diagnostics.AppInfo
import com.kingshot.camera.diagnostics.BundleExporter
import com.kingshot.camera.diagnostics.CapabilityReport
import com.kingshot.camera.diagnostics.DeviceInfo
import com.kingshot.camera.media.MediaSaver
import com.kingshot.camera.media.SaveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Owns the camera session for the screen. The camera opens only when the screen is resumed, permission is granted and a
 * preview surface exists; it closes as soon as any of those goes away.
 */
class CameraViewModel(app: Application) : AndroidViewModel(app), LabEvents {
    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var host: CameraHost? = null
    private var orchestrator: StillCaptureOrchestrator? = null
    private var texture: SurfaceTexture? = null
    private var resumed = false
    private var permissionGranted = false
    private var resumedAtMs = 0L
    private var firstFrameLogged = false
    private var discoveryToken = 0

    init {
        LabState.log.onChanged = { publishLog() }
        publishLog()
    }

    override fun onCleared() {
        LabState.log.onChanged = null
        closeCamera()
        super.onCleared()
    }

    // ------------------------------------------------------------ lifecycle inputs

    fun onResume(permissionGranted: Boolean) {
        resumed = true
        this.permissionGranted = permissionGranted
        resumedAtMs = SystemClock.elapsedRealtime()
        firstFrameLogged = false
        maybeOpen()
    }

    fun onPause() {
        resumed = false
        closeCamera()
    }

    fun onPermissionResult(granted: Boolean) {
        permissionGranted = granted
        if (granted) maybeOpen() else LabState.log.add("Camera permission denied. The camera cannot run without it.")
    }

    fun onSurfaceAvailable(st: SurfaceTexture) {
        texture = st
        maybeOpen()
    }

    fun onSurfaceDestroyed() {
        texture = null
        closeCamera()
    }

    fun onPreviewFrame() {
        if (!firstFrameLogged) {
            firstFrameLogged = true
            LabState.log.add("Resume to first preview frame: ${SystemClock.elapsedRealtime() - resumedAtMs} ms (includes any permission prompt time).")
        }
    }

    // ------------------------------------------------------------ user actions

    fun takePhoto(deviceDegrees: Int) {
        val s = _state.value
        orchestrator?.takePhoto(ProcessingChoice(s.nrAvailable && s.requestNr, s.edgeAvailable && s.requestEdge), deviceDegrees)
    }

    fun setRequestNr(v: Boolean) = _state.update { it.copy(requestNr = v) }

    fun setRequestEdge(v: Boolean) = _state.update { it.copy(requestEdge = v) }

    fun setIncludeSamples(v: Boolean) = _state.update { it.copy(includeSamples = v) }

    fun setWantRaw(v: Boolean) {
        _state.update { it.copy(wantRaw = v) }
        host?.setRawWanted(v)
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun onShareHandled() = _state.update { it.copy(shareUri = null) }

    fun exportBundle() {
        val s = _state.value
        val cams = LabState.cameras
        if (cams.isEmpty()) {
            LabState.log.add("Nothing to export yet: no capability report has been built.")
            return
        }
        val app = getApplication<Application>()
        val resolver = app.contentResolver
        val receipts = LabState.results.receipts.toList()
        val logText = LabState.log.text()
        val jpeg = LabState.results.lastJpegUri
        val dng = LabState.results.lastDngUri
        val jpegName = LabState.results.lastJpegName
        val dngName = LabState.results.lastDngName
        val debuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val versionName = app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "?"
        Thread {
            val now = System.currentTimeMillis()
            val device = DeviceInfo(
                Build.MANUFACTURER, Build.MODEL, Build.PRODUCT, Build.FINGERPRINT,
                Build.VERSION.SDK_INT, Build.VERSION.SECURITY_PATCH, Build.VERSION.RELEASE
            )
            val appInfo = AppInfo(versionName, if (debuggable) "debug" else "release")
            val generatedAt = LabState.ids.iso(now)
            val samples = mutableListOf<BundleExporter.Sample>()
            if (s.includeSamples) {
                if (jpeg != null && jpegName != null) samples += BundleExporter.Sample(jpegName, jpeg)
                if (dng != null && dngName != null) samples += BundleExporter.Sample(dngName, dng)
            }
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val result = BundleExporter(resolver, MediaSaver(resolver)).export(
                "kingshot_bundle_$stamp.zip",
                CapabilityReport.toJson(generatedAt, appInfo, device, cams),
                CapabilityReport.toText(generatedAt, appInfo, device, cams),
                logText,
                receipts,
                samples
            )
            when (result) {
                is SaveResult.Saved -> {
                    LabState.log.add("Bundle saved: Download/KingShot/${result.displayName} (${result.bytes / 1024} KB)")
                    _state.update { it.copy(shareUri = result.uri, message = "Bundle saved to Download/KingShot") }
                }
                is SaveResult.Failed -> {
                    LabState.log.add("Bundle export FAILED: ${result.reason}")
                    _state.update { it.copy(message = "Bundle export failed; see the log") }
                }
            }
        }.start()
    }

    // ------------------------------------------------------------ LabEvents (any thread)

    override fun onPreviewSize(width: Int, height: Int) {
        _state.update { it.copy(previewWidth = width, previewHeight = height) }
    }

    override fun onRawActive(active: Boolean) {
        _state.update { it.copy(rawActive = active, wantRaw = if (it.wantRaw && !active) false else it.wantRaw, cameraReady = true) }
    }

    override fun onProblem(message: String) {
        _state.update { it.copy(message = message) }
    }

    // ------------------------------------------------------------ internals

    private fun publishLog() {
        _state.update { it.copy(logLines = LabState.log.snapshot().takeLast(MAX_LOG_LINES)) }
    }

    private fun maybeOpen() {
        if (!resumed || !permissionGranted || host != null) return
        if (texture == null) return
        val app = getApplication<Application>()
        val h = CameraHost(app, LabState.log)
        val o = StillCaptureOrchestrator(
            h, MediaSaver(app.contentResolver), LabState.log, LabState.results, this,
            app.getSystemService(PowerManager::class.java), LabState.ids
        )
        host = h
        orchestrator = o
        h.start(o)
        o.start()
        val token = ++discoveryToken
        Thread {
            var cams = LabState.cameras
            if (cams.isEmpty()) {
                cams = CharacteristicsReader.readAll(app)
                LabState.cameras = cams
                cams.forEach { c ->
                    LabState.log.add(
                        "Camera ${c.cameraId}${c.physicalOf?.let { " (physical of $it)" } ?: ""}: facing=${c.lensFacing} " +
                            "level=${c.hardwareLevel} RAW=${c.supportsRaw()} NR-OFF=${c.supportsNoiseReductionOff()} EDGE-OFF=${c.supportsEdgeOff()}"
                    )
                }
            }
            val rear = CameraSelector.pickRear(cams)
            mainHandler.post { openSelected(token, h, o, rear) }
        }.start()
    }

    private fun openSelected(token: Int, h: CameraHost, o: StillCaptureOrchestrator, rear: CameraCapabilities?) {
        if (token != discoveryToken || host !== h) return // closed or replaced while discovering
        if (rear == null) {
            LabState.log.add("No usable rear camera was reported.")
            return
        }
        val tex = texture
        if (tex == null) {
            LabState.log.add("Preview surface not ready.")
            return
        }
        _state.update {
            it.copy(
                nrAvailable = rear.supportsNoiseReductionOff(),
                edgeAvailable = rear.supportsEdgeOff(),
                rawAvailable = rear.supportsRaw()
            )
        }
        o.activeCapabilities = rear
        h.open(rear, tex, _state.value.wantRaw && rear.supportsRaw())
    }

    private fun closeCamera() {
        val h = host
        val o = orchestrator
        host = null
        orchestrator = null
        discoveryToken++
        _state.update { it.copy(cameraReady = false, rawActive = false) }
        h?.shutdown() // closes the camera before returning
        if (o != null) Thread { o.shutdown() }.start() // saves may still be finishing; do not block the UI thread
    }

    companion object {
        private const val MAX_LOG_LINES = 80
    }
}
