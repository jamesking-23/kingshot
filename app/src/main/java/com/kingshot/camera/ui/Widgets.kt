package com.kingshot.camera.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Palette {
    val Background = Color(0xFF000000)
    val Panel = Color(0xFF1A1A1A)
    val Text = Color(0xFFFFFFFF)
    val Muted = Color(0xFFCCCCCC)
    val Accent = Color(0xFFB8E986)
    val Disabled = Color(0xFF777777)
    val Button = Color(0xFF3A3A3A)
}

/** A checkbox row with a 48dp minimum touch target. */
@Composable
fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (enabled) Palette.Accent else Palette.Disabled
        Box(
            Modifier.size(22.dp).border(BorderStroke(2.dp, tint), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) BasicText("✓", style = TextStyle(color = tint, fontSize = 16.sp))
        }
        Spacer(Modifier.width(12.dp))
        BasicText(label, style = TextStyle(color = if (enabled) Palette.Text else Palette.Disabled, fontSize = 14.sp))
    }
}

/** A text button with a 48dp minimum touch target. */
@Composable
fun ActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .background(if (enabled) Palette.Button else Palette.Panel, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(label, style = TextStyle(color = if (enabled) Palette.Text else Palette.Disabled, fontSize = 13.sp))
    }
}
