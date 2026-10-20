package com.kingshot.camera.ui

import com.kingshot.camera.capabilities.CameraCapabilities
import com.kingshot.camera.capture.CaptureIdGenerator
import com.kingshot.camera.capture.LabResults
import com.kingshot.camera.diagnostics.CaptureLog

/** Process-lifetime state that survives activity pause/resume. Phase 0 only; Phase 1 replaces it with a ViewModel. */
object LabState {
    val log = CaptureLog()
    val results = LabResults()
    val ids = CaptureIdGenerator()

    @Volatile
    var cameras: List<CameraCapabilities> = emptyList()
}
