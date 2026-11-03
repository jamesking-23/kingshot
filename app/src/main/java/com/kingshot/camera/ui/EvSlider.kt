package com.kingshot.camera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingshot.camera.R
import kotlin.math.roundToInt

/**
 * Vertical brightness (exposure compensation) slider for the right edge of the viewfinder.
 * Its range and step come from the camera's own reported exposure-compensation range; top is brighter, double tap resets to 0.
 */
@Composable
fun EvSlider(state: CameraUiState, vm: CameraViewModel, modifier: Modifier = Modifier) {
    val min = state.evMin
    val max = state.evMax
    if (max <= min) return
    val haptic = LocalHapticFeedback.current
    val minNow by rememberUpdatedState(min)
    val maxNow by rememberUpdatedState(max)
    val indexNow by rememberUpdatedState(state.evIndex)
    val description = stringResource(R.string.exposure)
    val hint = stringResource(R.string.exposure_slider_hint)
    val accent = Palette.Accent
    val index = state.evIndex

    Column(modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            if (index == 0) "0" else state.evLabel.removeSuffix(" EV"),
            Modifier.fillMaxWidth().height(24.dp),
            style = TextStyle(
                color = if (index == 0) Palette.Muted else accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        )
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .semantics {
                    contentDescription = "$description. $hint"
                    stateDescription = state.evLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(index.toFloat(), min.toFloat()..max.toFloat(), max - min - 1)
                    setProgress { target ->
                        vm.setEv(target.roundToInt())
                        true
                    }
                }
                .pointerInput(Unit) {
                    val pad = THUMB_RADIUS_DP.dp.toPx()
                    val pick = { y: Float ->
                        val next = indexAt(y, size.height.toFloat(), pad, minNow, maxNow)
                        if (next != indexNow) {
                            if (next == 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            vm.setEv(next)
                        }
                    }
                    detectTapGestures(onDoubleTap = { vm.resetEv() }, onTap = { pick(it.y) })
                }
                .pointerInput(Unit) {
                    val pad = THUMB_RADIUS_DP.dp.toPx()
                    val pick = { y: Float ->
                        val next = indexAt(y, size.height.toFloat(), pad, minNow, maxNow)
                        if (next != indexNow) {
                            if (next == 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            vm.setEv(next)
                        }
                    }
                    detectVerticalDragGestures(onDragStart = { pick(it.y) }, onVerticalDrag = { change, _ -> pick(change.position.y) })
                }
        ) {
            val pad = THUMB_RADIUS_DP.dp.toPx()
            val cx = size.width / 2f
            val top = pad
            val bottom = size.height - pad
            fun yOf(i: Int): Float = bottom - (i - min).toFloat() / (max - min) * (bottom - top)
            drawLine(Color(0x66FFFFFF), Offset(cx, top), Offset(cx, bottom), 3.dp.toPx(), StrokeCap.Round)
            val zeroY = yOf(0)
            val thumbY = yOf(index)
            if (index != 0) drawLine(accent, Offset(cx, zeroY), Offset(cx, thumbY), 4.dp.toPx(), StrokeCap.Round)
            drawLine(Color.White, Offset(cx - 9.dp.toPx(), zeroY), Offset(cx + 9.dp.toPx(), zeroY), 2.dp.toPx(), StrokeCap.Round)
            drawCircle(Color(0xCC000000), THUMB_RADIUS_DP.dp.toPx() + 2.dp.toPx(), Offset(cx, thumbY))
            drawCircle(if (index == 0) Color.White else accent, THUMB_RADIUS_DP.dp.toPx(), Offset(cx, thumbY))
            // A small sun: centre disc and eight rays.
            val ink = Color(0xFF1A1300)
            drawCircle(ink, 3.5.dp.toPx(), Offset(cx, thumbY))
            for (k in 0 until 8) {
                val angle = Math.PI / 4 * k
                val dx = Math.cos(angle).toFloat()
                val dy = Math.sin(angle).toFloat()
                drawLine(
                    ink,
                    Offset(cx + dx * 6.dp.toPx(), thumbY + dy * 6.dp.toPx()),
                    Offset(cx + dx * 8.5.dp.toPx(), thumbY + dy * 8.5.dp.toPx()),
                    1.5.dp.toPx(),
                    StrokeCap.Round
                )
            }
        }
    }
}

private const val THUMB_RADIUS_DP = 16

/** Maps a touch y position (top is brighter) to an exposure-compensation index inside the camera's range. */
private fun indexAt(y: Float, height: Float, pad: Float, min: Int, max: Int): Int {
    val usable = (height - 2 * pad).coerceAtLeast(1f)
    val fraction = ((height - pad - y) / usable).coerceIn(0f, 1f)
    return min + (fraction * (max - min)).roundToInt()
}
