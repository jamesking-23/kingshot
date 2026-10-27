package com.kingshot.camera.capture

/**
 * Decides whether the camera should be open: only while the screen is resumed, permission is granted and a preview surface exists.
 * Pure state, so every pause/resume/surface/permission ordering can be tested on a JVM.
 */
class OpenGate {
    private var resumed = false
    private var permission = false
    private var surface = false

    val wantOpen: Boolean get() = resumed && permission && surface

    fun onResume(permissionGranted: Boolean) {
        resumed = true
        permission = permissionGranted
    }

    fun onPause() {
        resumed = false
    }

    fun onPermission(granted: Boolean) {
        permission = granted
    }

    fun onSurface(available: Boolean) {
        surface = available
    }
}
