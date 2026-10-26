package com.kingshot.camera.ui

import android.net.Uri

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
    val logLines: List<String> = emptyList(),
    val message: String? = null,
    val shareUri: Uri? = null
)
