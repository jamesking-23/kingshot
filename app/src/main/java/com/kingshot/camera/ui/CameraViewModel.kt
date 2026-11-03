package com.kingshot.camera.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Size
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
import com.kingshot.camera.capture.OpenGate
import com.kingshot.camera.capture.ProcessingChoice
import com.kingshot.camera.capture.StillCaptureOrchestrator
import com.kingshot.camera.diagnostics.AppInfo
import com.kingshot.camera.diagnostics.BundleExporter
import com.kingshot.camera.diagnostics.CapabilityReport
import com.kingshot.camera.diagnostics.DeviceInfo
import com.kingshot.camera.diagnostics.ReceiptStore
import com.kingshot.camera.diagnostics.ReceiptSummary
import com.kingshot.camera.diagnostics.SelfCheck
import com.kingshot.camera.diagnostics.SelfCheckPlan
import com.kingshot.camera.looks.BaseLook
import com.kingshot.camera.looks.Grain
import com.kingshot.camera.looks.LookMath
import com.kingshot.camera.looks.LookParam
import com.kingshot.camera.looks.LookParams
import com.kingshot.camera.looks.LookRenderer
import com.kingshot.camera.looks.LookResult
import com.kingshot.camera.looks.LookSelection
import com.kingshot.camera.looks.Preset
import com.kingshot.camera.media.MediaSaver
import com.kingshot.camera.media.SaveResult
import com.kingshot.camera.profiles.NaturalProfile
import com.kingshot.camera.profiles.ProfileName
import com.kingshot.camera.update.ApkInstaller
import com.kingshot.camera.update.GitHubUpdateClient
import com.kingshot.camera.update.InstallEvents
import com.kingshot.camera.update.UpdateAssets
import com.kingshot.camera.update.UpdatePolicy
import com.kingshot.camera.update.UpdateUi
import com.kingshot.camera.update.VersionCompare
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

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
    private val gate = OpenGate()
    private var resumedAtMs = 0L
    private var firstFrameLogged = false
    private var discoveryToken = 0
    private var evMath: EvMath? = null
    private var selfCheckToken = 0
    private var pendingAssets: UpdateAssets? = null
    private var pendingVersion = ""
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
        val app = getApplication<Application>()
        val version = try {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: ""
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            ""
        }
        _state.update { it.copy(appVersion = version) }
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
                        requestEdge = p[KEY_EDGE] ?: it.requestEdge,
                        lookAlways = p[KEY_LOOK_ALWAYS] ?: it.lookAlways,
                        lookSelection = readLookSelection(p)
                    )
                }
            } catch (e: IOException) {
                LabState.log.add("Settings could not be read; using defaults: ${e.message}")
            }
        }
    }

    /** Pixel behaviour: the default (base) look always persists; the preset and sliders persist only with "Always use selected look". */
    private fun readLookSelection(p: androidx.datastore.preferences.core.Preferences): LookSelection {
        val base = BaseLook.values().firstOrNull { it.name == p[KEY_LOOK_BASE] } ?: BaseLook.ORIGINAL
        if (p[KEY_LOOK_ALWAYS] != true) return LookSelection(base = base)
        val preset = Preset.values().firstOrNull { it.name == p[KEY_LOOK_PRESET] } ?: Preset.NONE
        var params = LookParams(grain = Grain.values().firstOrNull { it.name == p[KEY_LOOK_GRAIN] } ?: Grain.NONE)
        for (param in LookParam.values()) {
            p[intPreferencesKey("look_${param.name.lowercase()}")]?.let { params = params.with(param, it) }
        }
        return LookSelection(base, preset, params)
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
                    it[KEY_LOOK_ALWAYS] = s.lookAlways
                    it[KEY_LOOK_BASE] = s.lookSelection.base.name
                    val keep = if (s.lookAlways) s.lookSelection else LookSelection(base = s.lookSelection.base)
                    it[KEY_LOOK_PRESET] = keep.preset.name
                    it[KEY_LOOK_GRAIN] = keep.params.grain.name
                    for (param in LookParam.values()) it[intPreferencesKey("look_${param.name.lowercase()}")] = keep.params[param]
                }
            } catch (e: IOException) {
                LabState.log.add("Settings could not be saved: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        LabState.log.onChanged = null
        InstallEvents.onResult = null
        closeCamera()
        lookWorker.shutdown()
        super.onCleared()
    }

    // ------------------------------------------------------------ lifecycle inputs

    fun onResume(permissionGranted: Boolean) {
        gate.onResume(permissionGranted)
        _state.update {
            val next = when {
                permissionGranted -> PermissionState.GRANTED
                it.permission == PermissionState.DENIED -> PermissionState.DENIED
                else -> PermissionState.UNKNOWN
            }
            it.copy(permission = next)
        }
        resumedAtMs = SystemClock.elapsedRealtime()
        firstFrameLogged = false
        maybeOpen()
    }

    fun onPause() {
        gate.onPause()
        closeCamera()
    }

    fun onPermissionResult(granted: Boolean) {
        gate.onPermission(granted)
        _state.update { it.copy(permission = if (granted) PermissionState.GRANTED else PermissionState.DENIED) }
        if (granted) maybeOpen() else LabState.log.add("Camera permission denied. The camera cannot run without it.")
    }

    fun onSurfaceAvailable(st: SurfaceTexture) {
        texture = st
        gate.onSurface(true)
        maybeOpen()
    }

    fun onSurfaceDestroyed() {
        texture = null
        gate.onSurface(false)
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
        _state.update { it.copy(shutterTick = it.shutterTick + 1) }
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

    /** Slider position: an index in the camera's own exposure-compensation range. */
    fun setEv(index: Int) {
        if (index != _state.value.evIndex) setEvIndex(index)
    }

    fun resetEv() {
        val m = evMath ?: return
        _state.update { it.copy(evIndex = 0, evLabel = m.label(0)) }
        pushControls()
    }

    private fun setEvIndex(index: Int) {
        val m = evMath ?: return
        val idx = m.clamp(index)
        _state.update { it.copy(evIndex = idx, evLabel = m.label(idx)) }
        pushControls()
    }

    /**
     * One-tap scripted run: takes photos with different controls, then grades the receipts (see SelfCheck). It can only grade what the
     * receipts record; flash, focus, level and picture quality still need a person.
     */
    fun runSelfCheck() {
        val s = _state.value
        if (!s.cameraReady || s.selfCheckRunning) return
        val baseline = LabState.results.receipts.map { it.captureId }.toSet()
        val rawBefore = s.wantRaw
        val steps = mutableListOf<Pair<Long, () -> Unit>>()
        steps += 0L to { setEvIndex(0) }
        steps += 0L to { setZoom(1f) }
        steps += STEP_MS to { shoot(0) }
        if (s.evAvailable) {
            steps += 0L to { setEvIndex(SELF_CHECK_EV) }
            steps += STEP_MS to { shoot(0) }
            steps += 0L to { setEvIndex(-SELF_CHECK_EV) }
            steps += STEP_MS to { shoot(0) }
            steps += 0L to { setEvIndex(0) }
        }
        s.zoomChoices.forEach { z ->
            steps += 0L to { setZoom(z.ratio) }
            steps += STEP_MS to { shoot(0) }
        }
        if (s.zoomChoices.isNotEmpty()) steps += 0L to { setZoom(1f) }
        if (s.rawAvailable) {
            if (!rawBefore) steps += 0L to { setWantRaw(true) }
            steps += RAW_REBUILD_MS to { shoot(0) }
            repeat(RAW_SHOTS - 1) { steps += STEP_MS to { shoot(0) } }
            if (!rawBefore) steps += STEP_MS to { setWantRaw(false) }
        }
        steps += FINISH_WAIT_MS to {
            val fresh = LabState.results.receipts.filter { it.captureId !in baseline }
            val plan = SelfCheckPlan(s.evAvailable, s.zoomChoices.size, if (s.rawAvailable) RAW_SHOTS else 0)
            val text = SelfCheck.text(SelfCheck.evaluate(fresh, plan))
            LabState.log.add("SELF-CHECK RESULT\n$text")
            _state.update { it.copy(selfCheckRunning = false, selfCheckText = text, message = "Self-check finished. Tap Export bundle and share it.") }
        }
        val token = ++selfCheckToken
        _state.update { it.copy(selfCheckRunning = true, selfCheckText = null) }
        LabState.log.add("Self-check started (${steps.size} steps). Keep the phone still, pointed at a normal scene.")
        runStep(steps, 0, token)
    }

    private fun runStep(steps: List<Pair<Long, () -> Unit>>, index: Int, token: Int) {
        if (token != selfCheckToken || index >= steps.size) return
        val (delay, action) = steps[index]
        val go = Runnable {
            if (token == selfCheckToken) {
                action()
                runStep(steps, index + 1, token)
            }
        }
        if (delay <= 0L) go.run() else mainHandler.postDelayed(go, delay)
    }

    private fun abortSelfCheck() {
        selfCheckToken++
        if (_state.value.selfCheckRunning) {
            LabState.log.add("Self-check stopped because the camera closed.")
            _state.update { it.copy(selfCheckRunning = false) }
        }
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

    fun openSettings() = _state.update { it.copy(showSettings = true, showLooks = false) }

    fun closeSettings() = _state.update { it.copy(showSettings = false, details = null) }

    fun openLooks() = _state.update { it.copy(showLooks = true) }

    fun closeLooks() = _state.update { it.copy(showLooks = false) }

    fun setBaseLook(base: BaseLook) = updateLook { it.copy(base = base) }

    fun setPreset(preset: Preset) = updateLook { it.copy(preset = preset) }

    /** Moves to the next (+1) or previous (-1) preset in the carousel: Original, then the six presets. False at either end. */
    fun swipeLook(direction: Int): Boolean {
        val presets = Preset.values()
        val current = presets.indexOf(_state.value.lookSelection.preset)
        val next = (current + direction).coerceIn(0, presets.lastIndex)
        if (next == current) return false
        setPreset(presets[next])
        return true
    }

    fun setLookParam(param: LookParam, value: Int) = updateLook { it.copy(params = it.params.with(param, value)) }

    fun setGrain(grain: Grain) = updateLook { it.copy(params = it.params.copy(grain = grain)) }

    fun resetLook() = updateLook { LookSelection(base = it.base) }

    fun setLookAlways(on: Boolean) {
        _state.update { it.copy(lookAlways = on) }
        saveSettings()
    }

    private fun updateLook(change: (LookSelection) -> LookSelection) {
        _state.update { it.copy(lookSelection = change(it.lookSelection)) }
        saveSettings()
    }

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

    private fun refreshProfile(caps: CameraCapabilities? = orchestrator?.activeCapabilities) {
        val c = caps ?: return
        val s = _state.value
        _state.update {
            it.copy(profileLabel = ProfileName.of(c, s.requestNr, s.requestEdge).label, profileDisclosure = NaturalProfile.DISCLOSURE)
        }
    }

    private fun pushControls() {
        val s = _state.value
        val plan = orchestrator?.setControls(ControlRequest(s.evIndex, s.zoomRatio, s.flash)) ?: return
        plan.notes.forEach { LabState.log.add(it) }
    }

    fun setRequestNr(v: Boolean) {
        _state.update { it.copy(requestNr = v) }
        refreshProfile()
        saveSettings()
    }

    fun setRequestEdge(v: Boolean) {
        _state.update { it.copy(requestEdge = v) }
        refreshProfile()
        saveSettings()
    }

    fun setIncludeSamples(v: Boolean) = _state.update { it.copy(includeSamples = v) }

    fun setWantRaw(v: Boolean) {
        _state.update { it.copy(wantRaw = v) }
        val h = host
        // Rebuilding the session while a photo is still being delivered loses that photo, so wait until none is in flight.
        orchestrator?.runWhenIdle { h?.setRawWanted(v) } ?: h?.setRawWanted(v)
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun onShareHandled() = _state.update { it.copy(shareUri = null) }

    // ------------------------------------------------------------ updates (the only network use; runs only on a user tap)

    private fun setUpdate(u: UpdateUi) = _state.update { it.copy(update = u) }

    private fun updateClient() = GitHubUpdateClient("KingShot/${_state.value.appVersion} (Android)")

    private fun updateBusy() = _state.value.update.let { it is UpdateUi.Checking || it is UpdateUi.Downloading || it is UpdateUi.Installing }

    fun checkForUpdates() {
        if (updateBusy()) return
        setUpdate(UpdateUi.Checking)
        val installed = _state.value.appVersion
        Thread {
            val result = try {
                val release = updateClient().fetchLatest()
                val assets = UpdatePolicy.pickAssets(release.assets)
                when {
                    !VersionCompare.isNewer(installed, release.tag) -> UpdateUi.UpToDate(installed)
                    assets == null -> UpdateUi.Failed("Release ${release.tag} has no verified download.")
                    else -> {
                        pendingAssets = assets
                        pendingVersion = release.tag.removePrefix("v")
                        UpdateUi.Available(pendingVersion, release.notes.take(NOTES_MAX), assets.apk.size)
                    }
                }
            } catch (e: GitHubUpdateClient.UpdateException) {
                UpdateUi.Failed(e.message ?: "The update check failed.")
            } catch (e: IOException) {
                UpdateUi.Failed("Could not reach GitHub. Check your connection and try again.")
            }
            setUpdate(result)
        }.start()
    }

    fun installUpdate() {
        val assets = pendingAssets ?: return
        if (updateBusy()) return
        val version = pendingVersion
        val app = getApplication<Application>()
        if (!app.packageManager.canRequestPackageInstalls()) {
            setUpdate(UpdateUi.NeedsInstallPermission(version))
            return
        }
        setUpdate(UpdateUi.Downloading(0))
        Thread {
            val outcome = try {
                downloadVerifyAndInstall(app, assets, version)
                UpdateUi.Installing
            } catch (e: GitHubUpdateClient.UpdateException) {
                UpdateUi.Failed(e.message ?: "The update failed.")
            } catch (e: IOException) {
                UpdateUi.Failed("The download failed. Check your connection and try again.")
            }
            setUpdate(outcome)
        }.start()
    }

    private fun downloadVerifyAndInstall(app: Application, assets: UpdateAssets, version: String) {
        val client = updateClient()
        val dir = File(app.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // only this app's own cache folder, never user media
        val apk = File(dir, assets.apk.name)
        val expected = UpdatePolicy.parseSha256(client.getText(assets.checksum.url, "application/octet-stream"))
            ?: throw GitHubUpdateClient.UpdateException("The checksum file could not be read.")
        val actual = client.download(assets.apk.url, apk, assets.apk.size) { setUpdate(UpdateUi.Downloading(it)) }
        if (actual != expected) {
            apk.delete()
            throw GitHubUpdateClient.UpdateException("The download does not match its checksum, so it was discarded.")
        }
        val installer = ApkInstaller(app)
        installer.problemWith(apk)?.let { throw GitHubUpdateClient.UpdateException(it) }
        InstallEvents.onResult = { ok, message -> setUpdate(if (ok) UpdateUi.UpToDate(version) else UpdateUi.Failed(message)) }
        installer.install(apk)
        LabState.log.add("Update $version downloaded, verified and handed to the Android installer.")
    }

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

    private val lookSources = ConcurrentHashMap<String, String>()
    private val lookWorker = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(LOOK_QUEUE))

    /** Renders the selected look onto a paired COPY of a freshly saved JPEG. The original stays untouched. */
    private fun scheduleLook(uri: Uri, name: String) {
        val selection = _state.value.lookSelection
        if (LookMath.recipe(selection).isIdentity) return
        val app = getApplication<Application>()
        try {
            lookWorker.execute {
                val renderer = LookRenderer(app.contentResolver, MediaSaver(app.contentResolver))
                when (val result = renderer.render(uri, name, selection)) {
                    is LookResult.Failed -> LabState.log.add("Look \"${selection.label()}\" was not applied to $name: ${result.reason}. The original is saved.")
                    is LookResult.Rendered -> {
                        lookSources[result.name] = name.removeSuffix(".jpg")
                        LabState.log.add(
                            "Look \"${selection.label()}\" applied by KingShot to a copy of $name (${result.note}). " +
                                "Saved as ${result.name}; the original is unchanged."
                        )
                        showSaved(result.uri, result.name)
                    }
                }
            }
        } catch (e: RejectedExecutionException) {
            LabState.log.add("Look \"${selection.label()}\" skipped for $name: too many copies waiting. The original is saved.")
        }
    }

    override fun onPhotoSaved(uri: Uri, name: String) {
        showSaved(uri, name)
        scheduleLook(uri, name)
    }

    private fun showSaved(uri: Uri, name: String) {
        val app = getApplication<Application>()
        val bmp = try {
            app.contentResolver.loadThumbnail(uri, Size(THUMB_PX, THUMB_PX), null)
        } catch (e: java.io.IOException) {
            LabState.log.add("Thumbnail could not be loaded: ${e.message}")
            null
        }
        _state.update { it.copy(thumbnail = bmp ?: it.thumbnail, photos = (listOf(PhotoItem(name, uri)) + it.photos).take(MAX_PHOTOS)) }
    }

    fun showDetails(photoName: String) {
        val id = lookSources[photoName] ?: photoName.removeSuffix(".jpg")
        val r = LabState.results.receipts.firstOrNull { it.captureId == id }
        val text = when {
            r != null -> ReceiptSummary.text(r)
            else -> ReceiptStore(java.io.File(getApplication<Application>().filesDir, "receipts")).readText(id)
                ?.let { "Stored receipt (raw JSON):\n$it" }
                ?: "No receipt found for $id."
        }
        _state.update { it.copy(details = text) }
    }

    fun closeDetails() = _state.update { it.copy(details = null) }

    fun openPhoto(uri: Uri) = _state.update { it.copy(openUri = uri) }

    fun onOpenHandled() = _state.update { it.copy(openUri = null) }

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
        if (!gate.wantOpen || host != null || texture == null) return
        val app = getApplication<Application>()
        val h = CameraHost(app, LabState.log)
        val o = StillCaptureOrchestrator(
            h, MediaSaver(app.contentResolver), LabState.log, LabState.results, this,
            app.getSystemService(PowerManager::class.java), LabState.ids,
            receiptStore = ReceiptStore(java.io.File(app.filesDir, "receipts"))
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
        refreshProfile(rear)
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
                evMin = ev?.minIndex ?: 0, evMax = ev?.maxIndex ?: 0,
                flashAvailable = flashOk, flash = FlashMode.OFF,
                zoomChoices = stops.map { st -> LensMath.label(st).let { l -> ZoomChoice(l, st.ratio, l.substringBefore(' ')) } },
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
        abortSelfCheck()
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
        private val KEY_LOOK_ALWAYS = booleanPreferencesKey("look_always")
        private val KEY_LOOK_BASE = stringPreferencesKey("look_base")
        private val KEY_LOOK_PRESET = stringPreferencesKey("look_preset")
        private val KEY_LOOK_GRAIN = stringPreferencesKey("look_grain")
        private const val LOOK_QUEUE = 8
        private const val NOTES_MAX = 600
        private const val STEP_MS = 2500L
        private const val RAW_REBUILD_MS = 3500L
        private const val FINISH_WAIT_MS = 5000L
        private const val SELF_CHECK_EV = 6
        private const val RAW_SHOTS = 2
        private const val THUMB_PX = 160
        private const val MAX_PHOTOS = 20
        private const val TICK_MS = 100L
        private const val FOCUS_INDICATOR_MS = 1500L
        private const val LEVEL_ALPHA = 0.2
        private const val LEVEL_MIN_CHANGE = 0.1
        private val DIGITAL_STEPS = listOf(2f, 5f)
    }
}
