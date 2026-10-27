package com.kingshot.camera.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Surface
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

/** Hosts the Compose screen and forwards lifecycle and permission events to the ViewModel. It never touches the camera. */
class MainActivity : ComponentActivity() {
    private val vm: CameraViewModel by viewModels()
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onPermissionResult(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CameraScreen(vm, ::deviceDegrees, ::share, ::openPhoto) }
    }

    override fun onResume() {
        super.onResume()
        val granted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        vm.onResume(granted)
        if (!granted) permission.launch(Manifest.permission.CAMERA)
    }

    override fun onPause() {
        vm.onPause()
        super.onPause()
    }

    private fun deviceDegrees(): Int = when (display?.rotation) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }

    private fun openPhoto(uri: Uri) {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/jpeg")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(view)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app can open this photo. It is saved in Pictures/KingShot.", Toast.LENGTH_LONG).show()
        }
    }

    private fun share(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("bundle", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share diagnostics bundle"))
    }
}
