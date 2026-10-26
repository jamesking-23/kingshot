package com.kingshot.camera.ui

import android.graphics.SurfaceTexture
import android.view.TextureView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Column(Modifier.fillMaxSize().background(Palette.Background)) {
        val previewDescription = stringResource(R.string.preview_description)
        Box(Modifier.fillMaxWidth().weight(5f).background(Palette.Panel), contentAlignment = Alignment.Center) {
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
                        // The view may already hold a surface (e.g. after returning to the screen).
                        surfaceTexture?.let { vm.onSurfaceAvailable(it) }
                    }
                },
                update = { view ->
                    // Sensor buffers are landscape; the screen is portrait-locked.
                    if (state.previewWidth > 0 && state.previewHeight > 0) view.setAspectRatio(state.previewHeight, state.previewWidth)
                },
                modifier = Modifier.fillMaxSize().semantics { contentDescription = previewDescription }
            )
        }

        BasicText(
            stringResource(R.string.disclosure),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            style = TextStyle(color = Palette.Muted, fontSize = 12.sp)
        )

        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
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
        }

        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleRow(stringResource(R.string.include_samples), state.includeSamples, true, vm::setIncludeSamples, Modifier.weight(1f))
            ActionButton(stringResource(R.string.export_bundle), onClick = { vm.exportBundle() })
            ActionButton(stringResource(R.string.capture), onClick = { vm.takePhoto(deviceDegrees()) }, enabled = state.cameraReady)
        }

        val scroll = rememberScrollState()
        LaunchedEffect(state.logLines.size) { scroll.animateScrollTo(scroll.maxValue) }
        Box(Modifier.fillMaxWidth().weight(3f).background(Palette.Panel).verticalScroll(scroll).padding(8.dp)) {
            BasicText(
                state.logLines.joinToString("\n"),
                style = TextStyle(color = Palette.Accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            )
        }
    }
}
