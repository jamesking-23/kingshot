package com.kingshot.camera.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.kingshot.camera.R
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Phase 0 Camera Lab screen. It only wires views to the orchestrator; it never touches CameraDevice.
 * Controls appear enabled only if the active camera lists the capability, with a reason when not.
 */
class MainActivity : Activity(), LabEvents {
    private lateinit var textureView: AutoFitTextureView
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var chkNr: CheckBox
    private lateinit var chkEdge: CheckBox
    private lateinit var chkRaw: CheckBox
    private lateinit var chkSamples: CheckBox

    private val uiHandler = Handler(Looper.getMainLooper())
    private var host: CameraHost? = null
    private var orchestrator: StillCaptureOrchestrator? = null
    private var updatingRaw = false
    private var resumedAtMs = 0L
    private var firstFrameLogged = false

    private val refreshLog = Runnable {
        logView.text = LabState.log.text()
        logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textureView = findViewById(R.id.preview)
        logView = findViewById(R.id.log)
        logScroll = findViewById(R.id.logScroll)
        chkNr = findViewById(R.id.chkNr)
        chkEdge = findViewById(R.id.chkEdge)
        chkRaw = findViewById(R.id.chkRaw)
        chkSamples = findViewById(R.id.chkSamples)

        findViewById<Button>(R.id.btnShutter).setOnClickListener {
            orchestrator?.takePhoto(ProcessingChoice(chkNr.isEnabled && chkNr.isChecked, chkEdge.isEnabled && chkEdge.isChecked), deviceDegrees())
        }
        findViewById<Button>(R.id.btnExport).setOnClickListener { exportBundle() }
        chkRaw.setOnCheckedChangeListener { _, checked -> if (!updatingRaw) host?.setRawWanted(checked) }
        LabState.log.onChanged = {
            uiHandler.removeCallbacks(refreshLog)
            uiHandler.postDelayed(refreshLog, 100)
        }
    }

    override fun onResume() {
        super.onResume()
        val h = CameraHost(this, LabState.log)
        val o = StillCaptureOrchestrator(
            h, MediaSaver(contentResolver), LabState.log, LabState.results, this,
            getSystemService(PowerManager::class.java), LabState.ids
        )
        host = h
        orchestrator = o
        h.start(o)
        o.start()
        resumedAtMs = SystemClock.elapsedRealtime()
        firstFrameLogged = false
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) = ensurePermissionThenOpen()
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                if (!firstFrameLogged) {
                    firstFrameLogged = true
                    LabState.log.add("Resume to first preview frame: ${SystemClock.elapsedRealtime() - resumedAtMs} ms (includes any permission prompt time).")
                }
            }
        }
        if (textureView.isAvailable) ensurePermissionThenOpen()
    }

    override fun onPause() {
        val h = host
        val o = orchestrator
        host = null
        orchestrator = null
        h?.shutdown() // closes the camera before returning
        if (o != null) Thread { o.shutdown() }.start() // saves may still be finishing; do not block the UI thread
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                discoverAndOpen()
            } else {
                LabState.log.add("Camera permission denied. The Camera Lab cannot run without it.")
            }
        }
    }

    // ------------------------------------------------------------ LabEvents

    override fun onPreviewSize(width: Int, height: Int) {
        // Sensor buffers are landscape; the screen is portrait-locked.
        runOnUiThread { textureView.setAspectRatio(height, width) }
    }

    override fun onRawActive(active: Boolean) {
        runOnUiThread {
            if (chkRaw.isChecked != active) {
                updatingRaw = true
                chkRaw.isChecked = active
                updatingRaw = false
            }
        }
    }

    override fun onProblem(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    // ------------------------------------------------------------ setup

    private fun ensurePermissionThenOpen() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            discoverAndOpen()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        }
    }

    private fun discoverAndOpen() {
        val h = host ?: return
        val appContext = applicationContext
        Thread {
            var cams = LabState.cameras
            if (cams.isEmpty()) {
                cams = CharacteristicsReader.readAll(appContext)
                LabState.cameras = cams
                cams.forEach { c ->
                    LabState.log.add(
                        "Camera ${c.cameraId}${c.physicalOf?.let { " (physical of $it)" } ?: ""}: facing=${c.lensFacing} level=${c.hardwareLevel} " +
                            "RAW=${c.supportsRaw()} NR-OFF=${c.supportsNoiseReductionOff()} EDGE-OFF=${c.supportsEdgeOff()}"
                    )
                }
            }
            val rear = CameraSelector.pickRear(cams)
            runOnUiThread {
                if (host !== h) return@runOnUiThread // paused or replaced while discovering
                if (rear == null) {
                    LabState.log.add("No usable rear camera was reported.")
                    return@runOnUiThread
                }
                applyCapabilities(rear)
                val tex = textureView.surfaceTexture
                if (tex == null) {
                    LabState.log.add("Preview surface not ready.")
                } else {
                    orchestrator?.activeCapabilities = rear
                    h.open(rear, tex, chkRaw.isChecked)
                }
            }
        }.start()
    }

    /** Capability-aware UI: a control is enabled only when the active camera lists it, otherwise it says why. */
    private fun applyCapabilities(c: CameraCapabilities) {
        chkNr.isEnabled = c.supportsNoiseReductionOff()
        chkEdge.isEnabled = c.supportsEdgeOff()
        chkRaw.isEnabled = c.supportsRaw()
        if (!chkNr.isEnabled) {
            chkNr.isChecked = false
            chkNr.text = getString(R.string.request_nr_off) + getString(R.string.not_listed_suffix)
        }
        if (!chkEdge.isEnabled) {
            chkEdge.isChecked = false
            chkEdge.text = getString(R.string.request_edge_off) + getString(R.string.not_listed_suffix)
        }
        if (!chkRaw.isEnabled) {
            updatingRaw = true
            chkRaw.isChecked = false
            updatingRaw = false
            chkRaw.text = getString(R.string.raw_dng) + getString(R.string.raw_unavailable_suffix)
        }
    }

    private fun deviceDegrees(): Int = when (display?.rotation) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }

    // ------------------------------------------------------------ export (user action only)

    private fun exportBundle() {
        val includeSamples = chkSamples.isChecked
        val cams = LabState.cameras
        if (cams.isEmpty()) {
            LabState.log.add("Nothing to export yet: no capability report has been built.")
            return
        }
        val resolver = contentResolver
        val receipts = LabState.results.receipts.toList()
        val logText = LabState.log.text()
        val jpeg = LabState.results.lastJpegUri
        val dng = LabState.results.lastDngUri
        val jpegName = LabState.results.lastJpegName
        val dngName = LabState.results.lastDngName
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        Thread {
            val now = System.currentTimeMillis()
            val device = DeviceInfo(
                Build.MANUFACTURER, Build.MODEL, Build.PRODUCT, Build.FINGERPRINT,
                Build.VERSION.SDK_INT, Build.VERSION.SECURITY_PATCH, Build.VERSION.RELEASE
            )
            val app = AppInfo(versionName, if (debuggable) "debug" else "release")
            val generatedAt = LabState.ids.iso(now)
            val samples = mutableListOf<BundleExporter.Sample>()
            if (includeSamples) {
                if (jpeg != null && jpegName != null) samples += BundleExporter.Sample(jpegName, jpeg)
                if (dng != null && dngName != null) samples += BundleExporter.Sample(dngName, dng)
            }
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val result = BundleExporter(resolver, MediaSaver(resolver)).export(
                "kingshot_bundle_$stamp.zip",
                CapabilityReport.toJson(generatedAt, app, device, cams),
                CapabilityReport.toText(generatedAt, app, device, cams),
                logText,
                receipts,
                samples
            )
            runOnUiThread {
                when (result) {
                    is SaveResult.Saved -> {
                        LabState.log.add("Bundle saved: Download/KingShot/${result.displayName} (${result.bytes / 1024} KB)")
                        Toast.makeText(this, R.string.bundle_saved, Toast.LENGTH_SHORT).show()
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, result.uri)
                            clipData = ClipData.newRawUri("bundle", result.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(send, "Share diagnostics bundle"))
                    }
                    is SaveResult.Failed -> {
                        LabState.log.add("Bundle export FAILED: ${result.reason}")
                        Toast.makeText(this, R.string.bundle_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }.start()
    }

    companion object {
        private const val REQ_CAMERA = 100
    }
}
