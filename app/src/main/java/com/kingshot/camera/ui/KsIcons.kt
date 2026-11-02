package com.kingshot.camera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class KsIcon { FLASH_OFF, FLASH_AUTO, FLASH_ON, TIMER, GRID, LEVEL, SETTINGS, EXPOSURE, CLOSE }

/** Simple line icons drawn in a 24-unit space, so there is no icon library or font dependency. Decorative: the parent supplies the description. */
@Composable
fun KsIconView(icon: KsIcon, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (icon) {
            KsIcon.FLASH_ON -> drawPath(bolt(u), tint, style = Fill)
            KsIcon.FLASH_AUTO -> {
                drawPath(bolt(u), tint, style = stroke)
                drawLine(tint, p(19f, 3f, u), p(19f, 8f, u), 2f * u, StrokeCap.Round)
                drawLine(tint, p(22f, 8f, u), p(19f, 3f, u), 2f * u, StrokeCap.Round)
            }
            KsIcon.FLASH_OFF -> {
                drawPath(bolt(u), tint, style = stroke)
                drawLine(tint, p(3f, 3f, u), p(21f, 21f, u), 2f * u, StrokeCap.Round)
            }
            KsIcon.TIMER -> {
                drawCircle(tint, radius = 8f * u, center = p(12f, 13f, u), style = stroke)
                drawLine(tint, p(12f, 13f, u), p(12f, 8.5f, u), 2f * u, StrokeCap.Round)
                drawLine(tint, p(9f, 3f, u), p(15f, 3f, u), 2f * u, StrokeCap.Round)
            }
            KsIcon.GRID -> grid(u, tint, stroke)
            KsIcon.LEVEL -> {
                drawLine(tint, p(2f, 12f, u), p(7.5f, 12f, u), 2f * u, StrokeCap.Round)
                drawLine(tint, p(16.5f, 12f, u), p(22f, 12f, u), 2f * u, StrokeCap.Round)
                drawCircle(tint, radius = 3.5f * u, center = p(12f, 12f, u), style = stroke)
            }
            KsIcon.SETTINGS -> {
                listOf(6f to 8f, 12f to 16f, 18f to 10f).forEach { (y, x) ->
                    drawLine(tint, p(3f, y, u), p(21f, y, u), 2f * u, StrokeCap.Round)
                    drawCircle(Color.Black, radius = 3f * u, center = p(x, y, u))
                    drawCircle(tint, radius = 3f * u, center = p(x, y, u), style = stroke)
                }
            }
            KsIcon.EXPOSURE -> {
                drawRoundRect(tint, p(3f, 3f, u), Size(18f * u, 18f * u), CornerRadius(3f * u), stroke)
                drawLine(tint, p(3f, 21f, u), p(21f, 3f, u), 2f * u, StrokeCap.Round)
                drawLine(tint, p(6f, 8f, u), p(10f, 8f, u), 2f * u, StrokeCap.Round)
                drawLine(tint, p(8f, 6f, u), p(8f, 10f, u), 2f * u, StrokeCap.Round)
                drawLine(tint, p(14f, 16f, u), p(18f, 16f, u), 2f * u, StrokeCap.Round)
            }
            KsIcon.CLOSE -> {
                drawLine(tint, p(5f, 5f, u), p(19f, 19f, u), 2f * u, StrokeCap.Round)
                drawLine(tint, p(19f, 5f, u), p(5f, 19f, u), 2f * u, StrokeCap.Round)
            }
        }
    }
}

private fun p(x: Float, y: Float, u: Float) = Offset(x * u, y * u)

private fun bolt(u: Float): Path = Path().apply {
    moveTo(13f * u, 2f * u)
    lineTo(5f * u, 13.5f * u)
    lineTo(11f * u, 13.5f * u)
    lineTo(10f * u, 22f * u)
    lineTo(19f * u, 10f * u)
    lineTo(13f * u, 10f * u)
    close()
}

private fun DrawScope.grid(u: Float, tint: Color, stroke: Stroke) {
    drawRoundRect(tint, p(3f, 3f, u), Size(18f * u, 18f * u), CornerRadius(2f * u), stroke)
    listOf(9f, 15f).forEach { v ->
        drawLine(tint, p(v, 3f, u), p(v, 21f, u), 1.5f * u)
        drawLine(tint, p(3f, v, u), p(21f, v, u), 1.5f * u)
    }
}
