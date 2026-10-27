package com.kingshot.camera.capture

import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.net.Uri
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import com.kingshot.camera.camera.CameraHost
import com.kingshot.camera.camera.CameraHostListener
import com.kingshot.camera.camera.HostControls
import com.kingshot.camera.camera.HostMeter
import com.kingshot.camera.camera.ImageHandle
import com.kingshot.camera.camera.ImageKind
import com.kingshot.camera.camera.StillRequestSpec
import com.kingshot.camera.capabilities.CameraCapabilities
import com.kingshot.camera.capabilities.Orientation
import com.kingshot.camera.capabilities.StateNames
import com.kingshot.camera.diagnostics.CaptureLog
import com.kingshot.camera.diagnostics.ExposureInfo
import com.kingshot.camera.diagnostics.OutputRecord
import com.kingshot.camera.diagnostics.ProcessingReceipt
import com.kingshot.camera.media.MediaSaver
import com.kingshot.camera.media.SaveResult
import com.kingshot.camera.raw.DngWriter
import com.kingshot.camera.raw.TimestampMatcher
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** UI-facing events, delivered on arbitrary threads. */
interface LabEvents {
    fun onPreviewSize(width: Int, height: Int)
    fun onRawActive(active: Boolean)
    fun onProblem(message: String)

    /** Plain-language report of what the camera measured, or null when the scene was not a low-light one. */
    fun onLowLight(advisory: String?)

    /** A JPEG was saved to MediaStore. */
    fun onPhotoSaved(uri: Uri, name: String)
}

/** Process-lifetime results the UI and the diagnostics bundle read. */
class LabResults {
    val receipts = CopyOnWriteArrayList<ProcessingReceipt>()
    @Volatile var lastJpegUri: Uri? = null
    @Volatile var lastDngUri: Uri? = null
    @Volatile var lastJpegName: String? = null
    @Volatile var lastDngName: String? = null
}

private class CaptureTag(val captureId: String, val plan: StillPlan, val jpegOrientation: Int, val rawExpected: Boolean)

private class Completed(val captureId: String, val result: TotalCaptureResult)

private class Record(val assembler: ReceiptAssembler, val startedAtMs: Long, val jpegOrientation: Int)

/**
 * Drives one still capture end to end: capability-checked plan -> request -> result and images matched by
 * sensor timestamp -> MediaStore saves -> Processing Receipt. Bounded everywhere: at most [MAX_IN_FLIGHT]
 * captures, bounded matcher capacity, bounded save queue. Every ImageHandle is closed exactly once.
 */
class StillCaptureOrchestrator(
    private val host: CameraHost,
    private val saver: MediaSaver,
    private val log: CaptureLog,
    private val results: LabResults,
    private val events: LabEvents,
    private val powerManager: PowerManager?,
    private val ids: CaptureIdGenerator,
    private val monotonicMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallMs: () -> Long = { System.currentTimeMillis() }
) : CameraHostListener {

    @Volatile
    var activeCapabilities: CameraCapabilities? = null

    private val jpegMatcher = TimestampMatcher<ImageHandle, Completed>(MATCHER_CAPACITY, MATCH_TIMEOUT_MS)
    private val rawMatcher = TimestampMatcher<ImageHandle, Completed>(MATCHER_CAPACITY, MATCH_TIMEOUT_MS)
    private val records = ConcurrentHashMap<String, Record>()
    private val inFlight = AtomicInteger(0)
    private val io = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(SAVE_QUEUE))
    private var sweeper: ScheduledExecutorService? = null

    fun start() {
        val s = Executors.newSingleThreadScheduledExecutor()
        s.scheduleWithFixedDelay({ sweepSafely() }, 1, 1, TimeUnit.SECONDS)
        sweeper = s
    }

    /** Call after [CameraHost.shutdown] so no new images can arrive. */
    fun shutdown() {
        sweeper?.shutdownNow()
        io.shutdown()
        try {
            io.awaitTermination(5, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        val closed = jpegMatcher.closeAll() + rawMatcher.closeAll()
        if (closed > 0) log.add("Shutdown closed $closed unmatched image(s).")
    }

    @Volatile
    private var controlRequest = ControlRequest()

    private fun toHost(plan: ControlPlan) = HostControls(
        plan.evIndex, plan.zoomRatio, plan.aeMode,
        plan.meter?.let { HostMeter(it.x, it.y, it.width, it.height, it.weight) }
    )

    /** Applies EV, zoom, flash mode and any metering region to the live preview, each checked against the camera's capabilities. */
    fun setControls(request: ControlRequest): ControlPlan? {
        controlRequest = request.copy(meter = controlRequest.meter)
        val caps = activeCapabilities ?: return null
        val plan = ControlPlanner.plan(caps, controlRequest)
        host.setControls(toHost(plan))
        return plan
    }

    /** Tap-to-focus at a point of the portrait preview (0..1 each way). */
    fun focusAt(nx: Float, ny: Float) {
        val caps = activeCapabilities ?: return
        val active = caps.activeArray ?: return
        val region = MeteringMath.region(nx, ny, caps.sensorOrientation ?: 90, active, controlRequest.zoomRatio)
        controlRequest = controlRequest.copy(meter = region)
        host.focusAt(HostMeter(region.x, region.y, region.width, region.height, region.weight))
    }

    fun takePhoto(choice: ProcessingChoice, deviceDegrees: Int) {
        val caps = activeCapabilities
        if (caps == null) {
            log.add("No active camera yet.")
            return
        }
        if (inFlight.incrementAndGet() > MAX_IN_FLIGHT) {
            inFlight.decrementAndGet()
            log.add("Still saving previous photos; shutter ignored (max $MAX_IN_FLIGHT in flight).")
            return
        }
        val plan = StillRequestPlanner.plan(caps, choice)
        plan.notes.forEach { log.add(it) }
        val controlPlan = ControlPlanner.plan(caps, controlRequest)
        controlPlan.notes.forEach { log.add(it) }
        val rawExpected = host.rawActive
        val wall = wallMs()
        val id = ids.next(wall)
        val orientation = Orientation.jpegOrientation(caps.sensorOrientation ?: 90, deviceDegrees, caps.lensFacing == "FRONT")
        val assembler = ReceiptAssembler(id, ids.iso(wall), "lab", caps.cameraId, rawExpected, monotonicMs())
        records[id] = Record(assembler, monotonicMs(), orientation)
        log.add("Capture $id requested (RAW expected: $rawExpected).")
        host.capture(
            CaptureTag(id, plan, orientation, rawExpected),
            StillRequestSpec(
                plan.noiseReductionMode, plan.edgeMode, plan.useContinuousPictureAf,
                toHost(controlPlan), controlPlan.needsPrecapture
            ),
            orientation
        )
    }

    // ------------------------------------------------------------ CameraHostListener (camera thread)

    override fun onPreviewSize(width: Int, height: Int) = events.onPreviewSize(width, height)

    override fun onSessionReady(rawActive: Boolean) = events.onRawActive(rawActive)

    override fun onSessionProblem(message: String) {
        log.add(message)
        events.onProblem(message)
    }

    override fun onImage(kind: ImageKind, image: ImageHandle) {
        val matcher = if (kind == ImageKind.JPEG) jpegMatcher else rawMatcher
        handle(kind, matcher.onImage(image.timestamp, image, monotonicMs()))
    }

    override fun onCaptureCompleted(tag: Any?, result: TotalCaptureResult) {
        val t = tag as? CaptureTag ?: return
        val record = records[t.captureId] ?: return
        val reportedNr = result.get(CaptureResult.NOISE_REDUCTION_MODE)
        val reportedEdge = result.get(CaptureResult.EDGE_MODE)
        val controls = StillRequestPlanner.evaluate(t.plan, reportedNr, reportedEdge)
        val exposure = ExposureInfo(
            exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
            iso = result.get(CaptureResult.SENSOR_SENSITIVITY),
            evCompSteps = result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION),
            aeState = StateNames.ae(result.get(CaptureResult.CONTROL_AE_STATE)),
            afState = StateNames.af(result.get(CaptureResult.CONTROL_AF_STATE)),
            awbState = StateNames.awb(result.get(CaptureResult.CONTROL_AWB_STATE)),
            postRawSensitivityBoost = result.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST)
        )
        record.assembler.onResult(
            controls, exposure,
            result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID),
            result.get(CaptureResult.CONTROL_ZOOM_RATIO),
            t.plan.notes
        )
        controls.forEach { c ->
            log.add("${c.name}: requested=${c.requested ?: "-"} reported=${c.reported ?: "-"} -> ${c.status.plain}${c.note?.let { " ($it)" } ?: ""}")
        }
        log.add("Exposure: ${exposure.exposureTimeNs} ns, ISO ${exposure.iso} (post-RAW boost ${exposure.postRawSensitivityBoost})")
        log.add("States: AE ${exposure.aeState}, AF ${exposure.afState}, AWB ${exposure.awbState}")
        val advisory = LowLightAdvisor.advisory(exposure.exposureTimeNs, exposure.iso)
        advisory?.let { log.add(it) }
        events.onLowLight(advisory)

        val ts = result.get(CaptureResult.SENSOR_TIMESTAMP)
        if (ts == null) {
            log.add("Result for ${t.captureId} has no SENSOR_TIMESTAMP; images cannot be matched to it.")
            return
        }
        val completed = Completed(t.captureId, result)
        val now = monotonicMs()
        handle(ImageKind.JPEG, jpegMatcher.onResult(ts, completed, now))
        if (t.rawExpected) handle(ImageKind.RAW, rawMatcher.onResult(ts, completed, now))
        finalizeIfComplete(t.captureId, record)
    }

    override fun onCaptureFailed(tag: Any?, message: String) {
        log.add("Capture failed: $message")
        val t = tag as? CaptureTag ?: return
        if (records.remove(t.captureId) != null) inFlight.decrementAndGet()
    }

    // ------------------------------------------------------------ matching outcomes

    private fun handle(kind: ImageKind, outcome: TimestampMatcher.Outcome<ImageHandle, Completed>) {
        outcome.match?.let { m ->
            log.add("$kind image matched to ${m.result.captureId} by sensor timestamp ${m.timestamp}.")
            dispatch(kind, m.image, m.result)
        }
        outcome.orphans.forEach { o -> handleOrphan(kind, o) }
    }

    private fun handleOrphan(kind: ImageKind, orphan: TimestampMatcher.Orphan<ImageHandle, Completed>) {
        when (orphan) {
            is TimestampMatcher.Orphan.Image -> {
                log.add("$kind image ts=${orphan.timestamp} unmatched: ${orphan.reason}")
                if (kind == ImageKind.JPEG) saveOrphanJpeg(orphan.image) else orphan.image.close()
            }
            is TimestampMatcher.Orphan.Result -> {
                log.add("$kind result for ${orphan.result.captureId} unmatched: ${orphan.reason}")
                val role = if (kind == ImageKind.JPEG) "jpeg" else "source_raw"
                val rec = records[orphan.result.captureId]
                if (rec != null) {
                    rec.assembler.onOutputFailed(role, orphan.reason)
                    finalizeIfComplete(orphan.result.captureId, rec)
                }
            }
        }
    }

    private fun dispatch(kind: ImageKind, image: ImageHandle, completed: Completed) {
        val rec = records[completed.captureId]
        if (rec == null) {
            image.close()
            return
        }
        rec.assembler.onImageArrived(monotonicMs())
        try {
            io.execute {
                try {
                    if (kind == ImageKind.JPEG) saveJpeg(image, completed.captureId, rec) else saveRaw(image, completed, rec)
                } finally {
                    image.close()
                }
                finalizeIfComplete(completed.captureId, rec)
            }
        } catch (e: RejectedExecutionException) {
            image.close()
            rec.assembler.onOutputFailed(if (kind == ImageKind.JPEG) "jpeg" else "source_raw", "save queue full or shut down")
            log.add("$kind save rejected: queue full or shutting down.")
            finalizeIfComplete(completed.captureId, rec)
        }
    }

    private fun saveJpeg(image: ImageHandle, captureId: String, rec: Record) {
        try {
            val buf = image.image.planes[0].buffer
            val bytes = ByteArray(buf.remaining())
            buf.get(bytes)
            val name = "$captureId.jpg"
            when (val r = saver.saveImage(name, "image/jpeg") { it.write(bytes) }) {
                is SaveResult.Saved -> {
                    rec.assembler.onOutputSaved(OutputRecord("jpeg", "JPEG", r.uri.toString(), "${image.width}x${image.height}", r.bytes), monotonicMs())
                    results.lastJpegUri = r.uri
                    results.lastJpegName = name
                    events.onPhotoSaved(r.uri, name)
                    log.add("Saved JPEG Pictures/KingShot/$name (${r.bytes / 1024} KB)")
                }
                is SaveResult.Failed -> {
                    rec.assembler.onOutputFailed("jpeg", r.reason)
                    log.add("JPEG save FAILED: ${r.reason}${if (r.partialUri != null) " (a hidden pending item was left; nothing was deleted)" else ""}")
                }
            }
        } catch (e: Exception) {
            rec.assembler.onOutputFailed("jpeg", "${e.javaClass.simpleName}: ${e.message}")
            log.add("JPEG error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun saveRaw(image: ImageHandle, completed: Completed, rec: Record) {
        try {
            val chars = host.characteristics()
            if (chars == null) {
                rec.assembler.onOutputFailed("source_raw", "camera characteristics unavailable")
                return
            }
            val exif = Orientation.exifFromDegrees(rec.jpegOrientation)
            val name = "${completed.captureId}.dng"
            when (val r = saver.saveImage(name, "image/x-adobe-dng") { out -> DngWriter.write(chars, completed.result, image.image, exif, out) }) {
                is SaveResult.Saved -> {
                    val record = OutputRecord("source_raw", "RAW_SENSOR", r.uri.toString(), "${image.width}x${image.height}", r.bytes)
                    rec.assembler.onOutputSaved(record, monotonicMs())
                    results.lastDngUri = r.uri
                    results.lastDngName = name
                    log.add("Saved DNG Pictures/KingShot/$name (${r.bytes / 1024} KB)")
                }
                is SaveResult.Failed -> {
                    rec.assembler.onOutputFailed("source_raw", r.reason)
                    log.add("DNG save FAILED: ${r.reason}${if (r.partialUri != null) " (a hidden pending item was left; nothing was deleted)" else ""}")
                }
            }
        } catch (e: Exception) {
            rec.assembler.onOutputFailed("source_raw", "${e.javaClass.simpleName}: ${e.message}")
            log.add("DNG error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** A JPEG with no matching result: keep the photo, record that its receipt data is missing. */
    private fun saveOrphanJpeg(image: ImageHandle) {
        val wall = wallMs()
        val id = ids.next(wall)
        val assembler = ReceiptAssembler(id, ids.iso(wall), "lab", activeCapabilities?.cameraId ?: "?", false, monotonicMs())
        assembler.onNoMatchingResult()
        assembler.onImageArrived(monotonicMs())
        val rec = Record(assembler, monotonicMs(), 0)
        records[id] = rec
        inFlight.incrementAndGet()
        try {
            io.execute {
                try {
                    saveJpeg(image, id, rec)
                } finally {
                    image.close()
                }
                finalizeIfComplete(id, rec)
            }
        } catch (e: RejectedExecutionException) {
            image.close()
            assembler.onOutputFailed("jpeg", "save queue full or shut down")
            finalizeIfComplete(id, rec)
        }
    }

    private fun finalizeIfComplete(id: String, rec: Record) {
        if (!rec.assembler.isComplete()) return
        if (records.remove(id) == null) return // already finalized by another path
        inFlight.decrementAndGet()
        val thermal = StateNames.thermal(powerManager?.currentThermalStatus)
        val receipt = rec.assembler.build(thermal)
        results.receipts += receipt
        while (results.receipts.size > MAX_RECEIPTS) results.receipts.removeAt(0)
        val rt = Runtime.getRuntime()
        log.add("Memory: java heap ${(rt.totalMemory() - rt.freeMemory()) / MB} MB, native heap ${Debug.getNativeHeapAllocatedSize() / MB} MB.")
        log.add("Receipt ready for $id: ${receipt.outputs.size} output(s), shutter-to-saved ${receipt.shutterToSavedMs} ms, thermal $thermal.")
    }

    private fun sweepSafely() {
        try {
            val now = monotonicMs()
            jpegMatcher.sweep(now).forEach { handleOrphan(ImageKind.JPEG, it) }
            rawMatcher.sweep(now).forEach { handleOrphan(ImageKind.RAW, it) }
            // Records that never completed (e.g. no SENSOR_TIMESTAMP) must not hold an in-flight slot forever.
            records.entries.removeIf { e ->
                val expired = now - e.value.startedAtMs > RECORD_EXPIRY_MS
                if (expired) {
                    inFlight.decrementAndGet()
                    log.add("Capture ${e.key} expired without completing; receipt not produced.")
                }
                expired
            }
        } catch (e: Exception) {
            log.add("sweep error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    companion object {
        const val MAX_IN_FLIGHT = 2
        private const val MB = 1024L * 1024L
        private const val MATCHER_CAPACITY = 4
        private const val MATCH_TIMEOUT_MS = 8000L
        private const val RECORD_EXPIRY_MS = 30000L
        private const val SAVE_QUEUE = 8
        private const val MAX_RECEIPTS = 50
    }
}
