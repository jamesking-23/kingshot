package com.kingshot.camera.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kingshot.camera.camera.CameraHost
import com.kingshot.camera.camera.CharacteristicsReader
import com.kingshot.camera.capabilities.CameraCapabilities
import com.kingshot.camera.capabilities.CameraSelector
import com.kingshot.camera.capabilities.EvMath
import com.kingshot.camera.capabilities.LensInfo
import com.kingshot.camera.capabilities.LensMath
import com.kingshot.camera.capture.ControlRequest
import com.kingshot.camera.capture.Countdown
import com.kingshot.camera.capture.FlashMode
import com.kingshot.camera.capture.LabEvents
import com.kingshot.camera.capture.LevelMath
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val Application.settingsStore by preferencesDataStore(name = "kingshot_settings")

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
    private var evMath: EvMath? = null
    private var countdownStartMs = 0L
    private var countdown: Countdown? = null
    private var pendingDegrees = 0
    private val clearFocus = Runnable { _state.update { it.copy(focusPoint = null) } }
    private val countdownTick = object : Runnable {
        override fun run() {
            val cd = countdown ?: return
            val elapsed = SystemClock.elapsedRealtime() - countdownStartMs
            if (cd.finished(elapsed)) {
                countdown = null
                _state.update { it.copy(countdownLeft = 0) }
                shoot(pendingDegrees)
            } else {
                _state.update { it.copy(countdownLeft = cd.secondsLeft(elapsed)) }
                mainHandler.postDelayed(this, TICK_MS)
            }
        }
    }

    init {
        LabState.log.onChanged = { publishLog() }
        publishLog()
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val p = getApplication<Application>().settingsStore.data.first()
                _state.update {
                    it.copy(
                        gridOn = p[KEY_GRID] ?: it.gridOn,
                        levelOn = p[KEY_LEVEL] ?: it.levelOn,
                        timerSeconds = p[KEY_TIMER] ?: it.timerSeconds,
                        requestNr = p[KEY_NR] ?: it.requestNr,
                        requestEdge = p[KEY_EDGE] ?: it.requestEdge
                    )
                }
            } catch (e: IOException) {
                LabState.log.add("Settings could not be read; using defaults: ${e.message}")
            }
        }
    }

    private fun saveSettings() {
        val s = _state.value
        viewModelScope.launch {
            try {
                getApplication<Application>().settingsStore.edit {
                    it[KEY_GRID] = s.gridOn
                    it[KEY_LEVEL] = s.levelOn
                    it[KEY_TIMER] = s.timerSeconds
                    it[KEY_NR] = s.requestNr
                    it[KEY_EDGE] = s.requestEdge
                }
            } catch (e: IOException) {
                LabState.log.add("Settings could not be saved: ${e.message}")
            }
        }
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

    /** Shutter button: starts the self-timer if one is set (a second press cancels it), otherwise shoots now. */
    fun takePhoto(deviceDegrees: Int) {
        if (countdown != null) {
            cancelCountdown()
            return
        }
        val seconds = _state.value.timerSeconds
        if (seconds <= 0) {
            shoot(deviceDegrees)
            return
        }
        pendingDegrees = deviceDegrees
        countdown = Countdown(seconds)
        countdownStartMs = SystemClock.elapsedRealtime()
        _state.update { it.copy(countdownLeft = seconds) }
        mainHandler.postDelayed(countdownTick, TICK_MS)
    }

    private fun shoot(deviceDegrees: Int) {
        val s = _state.value
        orchestrator?.takePhoto(ProcessingChoice(s.nrAvailable && s.requestNr, s.edgeAvailable && s.requestEdge), deviceDegrees)
    }

    private fun cancelCountdown() {
        countdown = null
        mainHandler.removeCallbacks(countdownTick)
        _state.update { it.copy(countdownLeft = 0) }
    }

    fun setTimer(seconds: Int) {
        _state.update { it.copy(timerSeconds = seconds) }
        saveSettings()
    }

    fun cycleTimer() = setTimer(
        when (_state.value.timerSeconds) {
            0 -> 3
            3 -> 10
            else -> 0
        }
    )

    fun cycleFlash() {
        val s = _state.value
        if (!s.flashAvailable) return
        val next = when (s.flash) {
            FlashMode.OFF -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.ON
            FlashMode.ON -> FlashMode.OFF
        }
        _state.update { it.copy(flash = next) }
        pushControls()
    }

    fun stepEv(delta: Int) {
        val m = evMath ?: return
        val idx = m.clamp(_state.value.evIndex + delta)
        _state.update { it.copy(evIndex = idx, evLabel = m.label(idx)) }
        pushControls()
    }

    fun resetEv() {
        val m = evMath ?: return
        _state.update { it.copy(evIndex = 0, evLabel = m.label(0)) }
        pushControls()
    }

    fun setZoom(ratio: Float) {
        _state.update { it.copy(zoomRatio = ratio) }
        pushControls()
    }

    fun toggleGrid() {
        _state.update { it.copy(gridOn = !it.gridOn) }
        saveSettings()
    }

    fun toggleLevel() {
        _state.update { it.copy(levelOn = !it.levelOn, rollDegrees = null) }
        saveSettings()
    }

    fun toggleLab() = _state.update { it.copy(showLab = !it.showLab) }

    /** Accelerometer sample in the device frame (portrait-locked screen). */
    fun onGravity(ax: Float, ay: Float, az: Float) {
        val raw = LevelMath.rollOrNull(ax, ay, az)
        _state.update { s ->
            val smoothed = when {
                raw == null -> null
                s.rollDegrees == null -> raw
                else -> LevelMath.smooth(s.rollDegrees, raw, LEVEL_ALPHA)
            }
            val prev = s.rollDegrees
            val unchanged = smoothed == prev || (smoothed != null && prev != null && Math.abs(smoothed - prev) < LEVEL_MIN_CHANGE)
            if (unchanged) s else s.copy(rollDegrees = smoothed)
        }
    }

    /** Tap on the preview; coordinates are 0..1 across and down the visible preview. */
    fun onTap(nx: Float, ny: Float) {
        if (!_state.value.cameraReady) return
        orchestrator?.focusAt(nx, ny)
        _state.update { it.copy(focusPoint = Pair(nx, ny)) }
        mainHandler.removeCallbacks(clearFocus)
        mainHandler.postDelayed(clearFocus, FOCUS_INDICATOR_MS)
    }

    private fun pushControls() {
        val s = _state.value
        val plan = orchestrator?.setControls(ControlRequest(s.evIndex, s.zoomRatio, s.flash)) ?: return
        plan.notes.forEach { LabState.log.add(it) }
    }

    fun setRequestNr(v: Boolean) {
        _state.update { it.copy(requestNr = v) }
        saveSettings()
    }

    fun setRequestEdge(v: Boolean) {
        _state.update { it.copy(requestEdge = v) }
        saveSettings()
    }

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

    override fun onLowLight(advisory: String?) {
        _state.update { it.copy(lowLight = advisory) }
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
        applyCapabilityUi(rear)
        o.setControls(ControlRequest())
        h.open(rear, tex, _state.value.wantRaw && rear.supportsRaw())
    }

    private fun applyCapabilityUi(rear: CameraCapabilities) {
        val ev = EvMath.from(rear.aeCompensationRange, rear.aeCompensationStep)
        evMath = ev
        val cams = LabState.cameras
        val lenses = rear.physicalIds
            .mapNotNull { id -> cams.firstOrNull { it.cameraId == id && it.physicalOf == rear.cameraId } }
            .mapNotNull { lensOf(it) }
            .ifEmpty { listOfNotNull(lensOf(rear)) }
        val reference = if (lenses.size == 1) lenses[0].id else LensMath.referenceFor(lenses, rear.zoomRatioRange?.first)
        val stops = if (reference == null) emptyList() else LensMath.stops(lenses, reference, rear.zoomRatioRange, DIGITAL_STEPS)
        val flashOk = rear.flashAvailable == true && (2 in rear.aeModes || 3 in rear.aeModes)
        _state.update {
            it.copy(
                evAvailable = ev != null, evIndex = 0, evLabel = ev?.label(0) ?: "",
                flashAvailable = flashOk, flash = FlashMode.OFF,
                zoomChoices = stops.map { st -> ZoomChoice(LensMath.label(st), st.ratio) },
                zoomRatio = 1f
            )
        }
    }

    private fun lensOf(c: CameraCapabilities): LensInfo? {
        val size = LensMath.parseSize(c.physicalSizeMm) ?: return null
        val focal = c.focalLengthsMm.firstOrNull() ?: return null
        return LensInfo(c.cameraId, focal, size.first, size.second)
    }

    private fun closeCamera() {
        cancelCountdown()
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
        private val KEY_GRID = booleanPreferencesKey("grid")
        private val KEY_LEVEL = booleanPreferencesKey("level")
        private val KEY_TIMER = intPreferencesKey("timer_seconds")
        private val KEY_NR = booleanPreferencesKey("request_nr_off")
        private val KEY_EDGE = booleanPreferencesKey("request_edge_off")
        private const val TICK_MS = 100L
        private const val FOCUS_INDICATOR_MS = 1500L
        private const val LEVEL_ALPHA = 0.2
        private const val LEVEL_MIN_CHANGE = 0.1
        private val DIGITAL_STEPS = listOf(2f, 5f)
    }
}
