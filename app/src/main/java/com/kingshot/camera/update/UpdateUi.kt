package com.kingshot.camera.update

/** What the Settings "Updates" section shows. Plain data so it is easy to test and render. */
sealed interface UpdateUi {
    data object Idle : UpdateUi

    data object Checking : UpdateUi

    data class UpToDate(val installed: String) : UpdateUi

    data class Available(val version: String, val notes: String, val sizeBytes: Long) : UpdateUi

    data class NeedsInstallPermission(val version: String) : UpdateUi

    data class Downloading(val percent: Int) : UpdateUi

    data object Installing : UpdateUi

    data class Failed(val message: String) : UpdateUi
}
