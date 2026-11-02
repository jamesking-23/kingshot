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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kingshot.camera.R
import com.kingshot.camera.capture.FlashMode
import com.kingshot.camera.capture.LevelMath

@Composable
fun CameraScreen(
    vm: CameraViewModel,
    deviceDegrees: () -> Int,
    onShare: (android.net.Uri) -> Unit,
    onOpen: (android.net.Uri) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
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
    LaunchedEffect(state.openUri) {
        state.openUri?.let {
            onOpen(it)
            vm.onOpenHandled()
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
    BackHandler(enabled = state.showSettings || state.showEv) {
        if (state.showSettings) vm.closeSettings() else vm.toggleEv()
    }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        // Android 15+ draws edge to edge: keep every control clear of the status bar, navigation bar and display cutout.
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (state.permission == PermissionState.DENIED) {
                PermissionPanel(onRequestPermission, onOpenSettings, Modifier.weight(1f))
            } else {
                TopBar(state, vm)
                PreviewArea(state, vm, Modifier.weight(1f))
                if (state.showEv && state.evAvailable) EvPanel(state, vm)
                BottomBar(state, vm, deviceDegrees)
            }
        }
        if (state.showSettings) SettingsSheet(state, vm)
    }
}

// ---------------------------------------------------------------- top bar

@Composable
private fun TopBar(state: CameraUiState, vm: CameraViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.flashAvailable) {
                val (icon, label) = when (state.flash) {
                    FlashMode.OFF -> KsIcon.FLASH_OFF to stringResource(R.string.flash_off)
                    FlashMode.AUTO -> KsIcon.FLASH_AUTO to stringResource(R.string.flash_auto)
                    FlashMode.ON -> KsIcon.FLASH_ON to stringResource(R.string.flash_on)
                }
                TopPill(icon, label, "${stringResource(R.string.flash)} $label", state.flash != FlashMode.OFF, vm::cycleFlash)
            }
            val timerLabel = if (state.timerSeconds == 0) stringResource(R.string.timer_off) else stringResource(R.string.timer_seconds, state.timerSeconds)
            TopPill(KsIcon.TIMER, timerLabel, "${stringResource(R.string.timer)} $timerLabel", state.timerSeconds != 0, vm::cycleTimer)
            TopPill(KsIcon.GRID, null, stringResource(R.string.grid), state.gridOn, vm::toggleGrid, stateText(state.gridOn))
            TopPill(KsIcon.LEVEL, null, stringResource(R.string.level), state.levelOn, vm::toggleLevel, stateText(state.levelOn))
        }
        TopPill(KsIcon.SETTINGS, null, stringResource(R.string.settings), false, vm::openSettings)
    }
}

@Composable
private fun stateText(on: Boolean) = stringResource(if (on) R.string.state_on else R.string.state_off)

@Composable
private fun TopPill(icon: KsIcon, label: String?, description: String, active: Boolean, onClick: () -> Unit, state: String? = null) {
    val tint = if (active) Palette.Accent else Palette.Text
    Row(
        Modifier
            .size(width = if (label == null) 48.dp else 78.dp, height = 48.dp)
            .background(if (active) Palette.SurfaceHigh else Color.Transparent, RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                if (state != null) stateDescription = state
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        KsIconView(icon, tint)
        if (label != null) {
            Spacer(Modifier.size(6.dp))
            BasicText(label, style = TextStyle(color = tint, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        }
    }
}

// ---------------------------------------------------------------- preview

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun PreviewArea(state: CameraUiState, vm: CameraViewModel, modifier: Modifier) {
    val previewDescription = stringResource(R.string.preview_description)
    val shutterFlash = remember { Animatable(0f) }
    LaunchedEffect(state.shutterTick) {
        if (state.shutterTick > 0) {
            shutterFlash.snapTo(0.55f)
            shutterFlash.animateTo(0f, tween(220))
        }
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = shutterFlash.value)))
            if (state.countdownLeft > 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText("${state.countdownLeft}", style = TextStyle(color = Palette.Text, fontSize = 96.sp, fontWeight = FontWeight.Light))
                }
            }
        }
        PreviewChips(state, vm)
        if (state.zoomChoices.size > 1) ZoomPills(state, vm, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun PreviewChips(state: CameraUiState, vm: CameraViewModel) {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.lowLight?.let { Chip(it, Palette.Accent, Modifier.fillMaxWidth()) }
        if (state.evAvailable && state.evIndex != 0) {
            val evText = stringResource(R.string.exposure_value, state.evLabel)
            Chip(state.evLabel, Palette.Accent, Modifier.clickable(role = Role.Button) { vm.toggleEv() }.semantics { contentDescription = evText })
        }
    }
}

@Composable
private fun Chip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(Color(0x99000000), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        BasicText(text, style = TextStyle(color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun ZoomPills(state: CameraUiState, vm: CameraViewModel, modifier: Modifier) {
    val selected = state.zoomChoices.minByOrNull { Math.abs(it.ratio - state.zoomRatio) }?.takeIf { Math.abs(it.ratio - state.zoomRatio) < 0.05f }
    Column(modifier.padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // The caption always says what the selected position really is: a lens, a sensor crop or a digital crop.
        selected?.let { Chip(it.label, Palette.Text) }
        Row(
            Modifier.background(Color(0x99000000), CircleShape).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            state.zoomChoices.forEach { z ->
                val isSelected = z == selected
                val description = if (isSelected) stringResource(R.string.zoom_selected, z.label) else z.label
                Box(
                    Modifier
                        .size(48.dp)
                        .background(if (isSelected) Palette.Accent else Color.Transparent, CircleShape)
                        .clickable(role = Role.Button) { vm.setZoom(z.ratio) }
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        z.short,
                        style = TextStyle(color = if (isSelected) Palette.OnAccent else Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

@Composable
private fun Overlays(state: CameraUiState) {
    Canvas(Modifier.fillMaxSize()) {
        if (state.gridOn) {
            val c = Palette.Text.copy(alpha = 0.45f)
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

// ---------------------------------------------------------------- exposure panel and bottom bar

@Composable
private fun EvPanel(state: CameraUiState, vm: CameraViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundButton("−", stringResource(R.string.exposure_decrease)) { vm.stepEv(-1) }
        BasicText(
            state.evLabel,
            Modifier.size(width = 96.dp, height = 48.dp).padding(top = 12.dp),
            style = TextStyle(color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        )
        RoundButton("+", stringResource(R.string.exposure_increase)) { vm.stepEv(1) }
        ActionButton(stringResource(R.string.exposure_reset), vm::resetEv, enabled = state.evIndex != 0)
    }
}

@Composable
private fun RoundButton(text: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .background(Palette.SurfaceHigh, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        BasicText(text, style = TextStyle(color = Palette.Text, fontSize = 24.sp))
    }
}

@Composable
private fun BottomBar(state: CameraUiState, vm: CameraViewModel, deviceDegrees: () -> Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val latest = state.photos.firstOrNull()
        val thumb = state.thumbnail
        val openDescription = stringResource(R.string.open_latest_photo)
        val exposureDescription = stringResource(R.string.exposure)
        if (latest != null && thumb != null) {
            Image(
                bitmap = thumb.asImageBitmap(),
                contentDescription = openDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .border(2.dp, Palette.Text.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                    .padding(2.dp)
                    .background(Palette.Surface, RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { vm.openPhoto(latest.uri) }
            )
        } else {
            Box(Modifier.size(56.dp).border(2.dp, Palette.SurfaceHigh, RoundedCornerShape(14.dp)))
        }
        ShutterButton(enabled = state.cameraReady, counting = state.countdownLeft > 0) { vm.takePhoto(deviceDegrees()) }
        if (state.evAvailable) {
            val evOpen = state.showEv
            Box(
                Modifier
                    .size(56.dp)
                    .background(if (evOpen) Palette.SurfaceHigh else Color.Transparent, CircleShape)
                    .clickable(role = Role.Button) { vm.toggleEv() }
                    .semantics { contentDescription = exposureDescription },
                contentAlignment = Alignment.Center
            ) {
                KsIconView(KsIcon.EXPOSURE, if (state.evIndex != 0 || evOpen) Palette.Accent else Palette.Text)
            }
        } else {
            Spacer(Modifier.size(56.dp))
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, counting: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "shutter")
    val haptic = LocalHapticFeedback.current
    val description = stringResource(if (counting) R.string.cancel_timer else R.string.take_photo)
    val ring = if (enabled) Palette.Text else Palette.Disabled
    Box(
        Modifier
            .size(80.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(80.dp).border(4.dp, ring, CircleShape))
        Box(Modifier.size(62.dp).background(if (counting) Palette.Danger else ring, CircleShape))
    }
}

// ---------------------------------------------------------------- permission

@Composable
private fun PermissionPanel(onRequest: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        BasicText(stringResource(R.string.permission_title), style = Type.Title.copy(textAlign = TextAlign.Center))
        BasicText(stringResource(R.string.permission_body), style = Type.Body.copy(color = Palette.Muted, textAlign = TextAlign.Center))
        ActionButton(stringResource(R.string.permission_allow), onRequest, primary = true)
        ActionButton(stringResource(R.string.permission_open_settings), onOpenSettings)
    }
}
