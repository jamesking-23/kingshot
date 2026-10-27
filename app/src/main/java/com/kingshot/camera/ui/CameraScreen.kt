package com.kingshot.camera.ui

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.MotionEvent
import android.view.TextureView
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kingshot.camera.R
import com.kingshot.camera.capture.FlashMode
import com.kingshot.camera.capture.LevelMath

@Composable
fun CameraScreen(vm: CameraViewModel, deviceDegrees: () -> Int, onShare: (android.net.Uri) -> Unit) {
    val state = vm.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.onMessageShown()
        }
    }
    LaunchedEffect(state.shareUri) {
        state.shareUri?.let {
            onShare(it)
            vm.onShareHandled()
        }
    }
    DisposableEffect(state.levelOn) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) = vm.onGravity(e.values[0], e.values[1], e.values[2])
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        if (state.levelOn && sm != null && sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(listener) }
    }

    Column(Modifier.fillMaxSize().background(Palette.Background)) {
        TopBar(state, vm)
        PreviewArea(state, vm, Modifier.weight(1f))
        if (state.zoomChoices.isNotEmpty()) ZoomStrip(state, vm)
        if (state.evAvailable) EvRow(state, vm)
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
            val cancelling = state.countdownLeft > 0
            ActionButton(
                if (cancelling) "Cancel timer" else stringResource(R.string.capture),
                onClick = { vm.takePhoto(deviceDegrees()) },
                modifier = Modifier.fillMaxWidth(0.6f),
                enabled = state.cameraReady
            )
        }
        if (state.showLab) LabPanel(state, vm)
    }
}

@Composable
private fun TopBar(state: CameraUiState, vm: CameraViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.flashAvailable) {
            val f = when (state.flash) {
                FlashMode.OFF -> "Flash off"
                FlashMode.AUTO -> "Flash auto"
                FlashMode.ON -> "Flash on"
            }
            ActionButton(f, vm::cycleFlash, Modifier.weight(1f))
        }
        ActionButton(if (state.timerSeconds == 0) "Timer off" else "Timer ${state.timerSeconds} s", vm::cycleTimer, Modifier.weight(1f))
        ActionButton(if (state.gridOn) "Grid on" else "Grid off", vm::toggleGrid, Modifier.weight(1f))
        ActionButton(if (state.levelOn) "Level on" else "Level off", vm::toggleLevel, Modifier.weight(1f))
        ActionButton(if (state.showLab) "Lab ▲" else "Lab ▼", vm::toggleLab, Modifier.weight(1f))
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun PreviewArea(state: CameraUiState, vm: CameraViewModel, modifier: Modifier) {
    val previewDescription = stringResource(R.string.preview_description)
    Box(modifier.fillMaxWidth().background(Palette.Panel), contentAlignment = Alignment.Center) {
        // The sensor buffer is landscape and the screen is portrait-locked, so the visible ratio is height / width.
        val sized = if (state.previewWidth > 0 && state.previewHeight > 0) {
            Modifier.aspectRatio(state.previewHeight.toFloat() / state.previewWidth)
        } else {
            Modifier.fillMaxSize()
        }
        Box(sized) {
            AndroidView(
                factory = { ctx ->
                    AutoFitTextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) = vm.onSurfaceAvailable(st)
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                vm.onSurfaceDestroyed()
                                return true
                            }

                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = vm.onPreviewFrame()
                        }
                        setOnTouchListener { v, e ->
                            if (e.actionMasked == MotionEvent.ACTION_UP && v.width > 0 && v.height > 0) {
                                vm.onTap((e.x / v.width).coerceIn(0f, 1f), (e.y / v.height).coerceIn(0f, 1f))
                                v.performClick()
                            }
                            true
                        }
                        // The view may already hold a surface (e.g. after returning to the screen).
                        surfaceTexture?.let { vm.onSurfaceAvailable(it) }
                    }
                },
                modifier = Modifier.fillMaxSize().semantics { contentDescription = previewDescription }
            )
            Overlays(state)
            if (state.countdownLeft > 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText("${state.countdownLeft}", style = TextStyle(color = Palette.Text, fontSize = 96.sp))
                }
            }
        }
    }
}

@Composable
private fun Overlays(state: CameraUiState) {
    Canvas(Modifier.fillMaxSize()) {
        if (state.gridOn) {
            val c = Palette.Text.copy(alpha = 0.5f)
            for (i in 1..2) {
                drawLine(c, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), 1.dp.toPx())
                drawLine(c, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), 1.dp.toPx())
            }
        }
        val roll = state.rollDegrees
        if (state.levelOn && roll != null) {
            val color = if (LevelMath.isLevel(roll)) Palette.Accent else Palette.Text
            rotate(degrees = (-roll).toFloat(), pivot = center) {
                drawLine(color, Offset(size.width * 0.3f, center.y), Offset(size.width * 0.7f, center.y), 2.dp.toPx())
            }
        }
        state.focusPoint?.let { (nx, ny) ->
            drawCircle(Palette.Accent, radius = 36.dp.toPx(), center = Offset(nx * size.width, ny * size.height), style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
private fun ZoomStrip(state: CameraUiState, vm: CameraViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        state.zoomChoices.forEach { z ->
            val selected = Math.abs(state.zoomRatio - z.ratio) < 0.05f
            ActionButton(if (selected) "● ${z.label}" else z.label, { vm.setZoom(z.ratio) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun EvRow(state: CameraUiState, vm: CameraViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        ActionButton("−", { vm.stepEv(-1) })
        BasicText(
            state.evLabel,
            Modifier.weight(1f),
            style = TextStyle(color = Palette.Text, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
        )
        ActionButton("+", { vm.stepEv(1) })
        ActionButton("Reset", vm::resetEv)
    }
}

@Composable
private fun LabPanel(state: CameraUiState, vm: CameraViewModel) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(scroll)) {
        BasicText(
            stringResource(R.string.disclosure),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            style = TextStyle(color = Palette.Muted, fontSize = 12.sp)
        )
        ToggleRow(
            stringResource(R.string.request_nr_off) + if (state.nrAvailable) "" else stringResource(R.string.not_listed_suffix),
            state.requestNr && state.nrAvailable, state.nrAvailable, vm::setRequestNr
        )
        ToggleRow(
            stringResource(R.string.request_edge_off) + if (state.edgeAvailable) "" else stringResource(R.string.not_listed_suffix),
            state.requestEdge && state.edgeAvailable, state.edgeAvailable, vm::setRequestEdge
        )
        ToggleRow(
            stringResource(R.string.raw_dng) + if (state.rawAvailable) "" else stringResource(R.string.raw_unavailable_suffix),
            state.wantRaw && state.rawAvailable, state.rawAvailable, vm::setWantRaw
        )
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleRow(stringResource(R.string.include_samples), state.includeSamples, true, vm::setIncludeSamples, Modifier.weight(1f))
            ActionButton(stringResource(R.string.export_bundle), onClick = { vm.exportBundle() })
        }
        Box(Modifier.fillMaxWidth().background(Palette.Panel).padding(8.dp)) {
            BasicText(
                state.logLines.joinToString("\n"),
                style = TextStyle(color = Palette.Accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            )
        }
    }
}
