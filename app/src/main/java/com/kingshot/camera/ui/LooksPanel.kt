package com.kingshot.camera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingshot.camera.R
import com.kingshot.camera.looks.BaseLook
import com.kingshot.camera.looks.Grain
import com.kingshot.camera.looks.LookParam
import com.kingshot.camera.looks.Preset
import kotlin.math.roundToInt

/**
 * The Looks panel, modelled on the Pixel 11 Looks screen: a default (base) look, a row of presets on top of it,
 * six -10..+10 sliders, Sharpness 0..15 and Grain, and an "Always use selected look" switch.
 */
@Composable
fun LooksPanel(state: CameraUiState, vm: CameraViewModel) {
    val sel = state.lookSelection
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 330.dp)
            .background(Palette.Surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(stringResource(R.string.looks), Modifier.weight(1f), style = Type.Title)
            ActionButton(stringResource(R.string.looks_reset), vm::resetLook, enabled = sel.preset != Preset.NONE || !sel.params.isDefault)
            Box(Modifier.width(8.dp))
            ActionButton(stringResource(R.string.looks_done), vm::closeLooks, primary = true)
        }
        Heading(stringResource(R.string.looks_default))
        OptionRow(BaseLook.values().map { it.label }, BaseLook.values().indexOf(sel.base)) { vm.setBaseLook(BaseLook.values()[it]) }
        Heading(stringResource(R.string.looks_presets))
        OptionRow(Preset.values().map { it.label }, Preset.values().indexOf(sel.preset)) { vm.setPreset(Preset.values()[it]) }
        Heading(stringResource(R.string.looks_adjust))
        for (param in LookParam.values()) {
            ParamSlider(param, sel.params[param]) { vm.setLookParam(param, it) }
        }
        Heading(stringResource(R.string.looks_grain))
        OptionRow(Grain.values().map { it.label }, Grain.values().indexOf(sel.params.grain)) { vm.setGrain(Grain.values()[it]) }
        SwitchRow(stringResource(R.string.looks_always), state.lookAlways, true, vm::setLookAlways, hint = stringResource(R.string.looks_always_hint))
        Hint(stringResource(R.string.looks_note))
    }
}

@Composable
private fun Heading(text: String) {
    BasicText(text.uppercase(), Modifier.padding(top = 12.dp, bottom = 4.dp), style = Type.Section)
}

@Composable
private fun OptionRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .background(if (on) Palette.Accent else Palette.SurfaceHigh, RoundedCornerShape(24.dp))
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(i) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    label,
                    style = TextStyle(color = if (on) Palette.OnAccent else Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                )
            }
        }
    }
}

@Composable
private fun ParamSlider(param: LookParam, value: Int, onChange: (Int) -> Unit) {
    val minNow by rememberUpdatedState(param.min)
    val maxNow by rememberUpdatedState(param.max)
    val valueNow by rememberUpdatedState(value)
    val changeNow by rememberUpdatedState(onChange)
    val changed = value != param.neutral
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(param.label, Modifier.width(92.dp), style = Type.Body)
        Canvas(
            Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .semantics {
                    contentDescription = param.label
                    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), param.min.toFloat()..param.max.toFloat(), param.max - param.min - 1)
                    setProgress { target ->
                        onChange(target.roundToInt())
                        true
                    }
                }
                .pointerInput(param) {
                    val pad = 12.dp.toPx()
                    val pick = { x: Float ->
                        val next = valueAt(x, size.width.toFloat(), pad, minNow, maxNow)
                        if (next != valueNow) changeNow(next)
                    }
                    detectTapGestures(onDoubleTap = { changeNow(param.neutral) }, onTap = { pick(it.x) })
                }
                .pointerInput(param) {
                    val pad = 12.dp.toPx()
                    val pick = { x: Float ->
                        val next = valueAt(x, size.width.toFloat(), pad, minNow, maxNow)
                        if (next != valueNow) changeNow(next)
                    }
                    detectHorizontalDragGestures(onDragStart = { pick(it.x) }, onHorizontalDrag = { change, _ -> pick(change.position.x) })
                }
        ) {
            val pad = 12.dp.toPx()
            val cy = size.height / 2f
            val left = pad
            val right = size.width - pad
            fun xOf(v: Int): Float = left + (v - param.min).toFloat() / (param.max - param.min) * (right - left)
            drawLine(Color(0x66FFFFFF), Offset(left, cy), Offset(right, cy), 3.dp.toPx(), StrokeCap.Round)
            val neutralX = xOf(param.neutral)
            val thumbX = xOf(value)
            if (changed) drawLine(Palette.Accent, Offset(neutralX, cy), Offset(thumbX, cy), 4.dp.toPx(), StrokeCap.Round)
            drawLine(Color.White, Offset(neutralX, cy - 7.dp.toPx()), Offset(neutralX, cy + 7.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            drawCircle(if (changed) Palette.Accent else Color.White, 11.dp.toPx(), Offset(thumbX, cy))
        }
        BasicText(
            if (param == LookParam.SHARPNESS) "$value" else if (value > 0) "+$value" else "$value",
            Modifier.width(44.dp).padding(start = 8.dp),
            style = TextStyle(color = if (changed) Palette.Accent else Palette.Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        )
    }
}

/** Maps a touch x position to a slider value inside [min]..[max]. */
private fun valueAt(x: Float, width: Float, pad: Float, min: Int, max: Int): Int {
    val usable = (width - 2 * pad).coerceAtLeast(1f)
    val fraction = ((x - pad) / usable).coerceIn(0f, 1f)
    return min + (fraction * (max - min)).roundToInt()
}
