package com.kingshot.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Palette {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF16161A)
    val SurfaceHigh = Color(0xFF26262C)
    val Scrim = Color(0xE6000000)
    val Text = Color(0xFFFFFFFF)
    val Muted = Color(0xFFB9B9C2)
    val Accent = Color(0xFFFFC83D)
    val OnAccent = Color(0xFF1A1300)
    val Danger = Color(0xFFFF6B6B)
    val Disabled = Color(0xFF7A7A84)
}

object Type {
    val Title = TextStyle(color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    val Section = TextStyle(color = Palette.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
    val Body = TextStyle(color = Palette.Text, fontSize = 15.sp)
    val Caption = TextStyle(color = Palette.Muted, fontSize = 13.sp)
    val Mono = TextStyle(color = Palette.Accent, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
}

/** A switch row with a 48dp minimum touch target; the whole row toggles. */
@Composable
fun SwitchRow(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, hint: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(label, style = Type.Body.copy(color = if (enabled) Palette.Text else Palette.Disabled))
            hint?.let { BasicText(it, style = Type.Caption) }
        }
        Spacer(Modifier.size(12.dp))
        val track = when {
            !enabled -> Palette.SurfaceHigh
            checked -> Palette.Accent
            else -> Palette.SurfaceHigh
        }
        val knob = when {
            !enabled -> Palette.Disabled
            checked -> Palette.OnAccent
            else -> Palette.Muted
        }
        Box(Modifier.size(width = 52.dp, height = 30.dp).background(track, CircleShape).padding(3.dp)) {
            Box(
                Modifier
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .size(24.dp)
                    .background(knob, CircleShape)
            )
        }
    }
}

/** Text button, 48dp minimum height. [primary] uses the accent colour. */
@Composable
fun ActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = false) {
    val bg = when {
        !enabled -> Palette.Surface
        primary -> Palette.Accent
        else -> Palette.SurfaceHigh
    }
    val fg = when {
        !enabled -> Palette.Disabled
        primary -> Palette.OnAccent
        else -> Palette.Text
    }
    Box(
        modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .background(bg, RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(label, style = TextStyle(color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium))
    }
}

/** A titled group of settings on a raised surface. */
@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        BasicText(title.uppercase(), Modifier.padding(start = 4.dp, bottom = 8.dp), style = Type.Section)
        Column(Modifier.fillMaxWidth().background(Palette.Surface, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) {
            content()
        }
    }
}

@Composable
fun Divider() {
    Spacer(Modifier.fillMaxWidth().height(1.dp).background(Palette.SurfaceHigh))
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier.padding(vertical = 6.dp), style = Type.Caption)
}

@Composable
fun VerticalSpace(height: Int) = Spacer(Modifier.height(height.dp))

@Composable
fun SpaceBetween(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { content() }
}
