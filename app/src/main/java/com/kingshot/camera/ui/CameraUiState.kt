package com.kingshot.camera.ui

import android.graphics.Bitmap
import android.net.Uri
import com.kingshot.camera.capture.FlashMode

data class PhotoItem(val name: String, val uri: Uri)

enum class PermissionState { UNKNOWN, GRANTED, DENIED }

/** [label] is the full honest name ("2× sensor crop"); [short] is what fits on the button ("2×"). */
data class ZoomChoice(val label: String, val ratio: Float, val short: String)

/** Everything the screen shows. Immutable; the ViewModel replaces it as a whole (unidirectional data flow). */
data class CameraUiState(
    val previewWidth: Int = 0,
    val previewHeight: Int = 0,
    val cameraReady: Boolean = false,
    val nrAvailable: Boolean = false,
    val edgeAvailable: Boolean = false,
    val rawAvailable: Boolean = false,
    val requestNr: Boolean = true,
    val requestEdge: Boolean = true,
    val wantRaw: Boolean = false,
    val rawActive: Boolean = false,
    val includeSamples: Boolean = false,
    val evAvailable: Boolean = false,
    val evIndex: Int = 0,
    val evLabel: String = "0.0 EV",
    val flashAvailable: Boolean = false,
    val flash: FlashMode = FlashMode.OFF,
    val zoomChoices: List<ZoomChoice> = emptyList(),
    val zoomRatio: Float = 1f,
    val timerSeconds: Int = 0,
    val countdownLeft: Int = 0,
    val gridOn: Boolean = false,
    val levelOn: Boolean = false,
    val rollDegrees: Double? = null,
    val focusPoint: Pair<Float, Float>? = null,
    val showSettings: Boolean = false,
    val showEv: Boolean = false,
    val shutterTick: Int = 0,
    val permission: PermissionState = PermissionState.UNKNOWN,
    val appVersion: String = "",
    val thumbnail: Bitmap? = null,
    val photos: List<PhotoItem> = emptyList(),
    val details: String? = null,
    val selfCheckRunning: Boolean = false,
    val selfCheckText: String? = null,
    val profileLabel: String = "Natural",
    val profileDisclosure: String = "",
    val openUri: Uri? = null,
    val lowLight: String? = null,
    val logLines: List<String> = emptyList(),
    val message: String? = null,
    val shareUri: Uri? = null
)
