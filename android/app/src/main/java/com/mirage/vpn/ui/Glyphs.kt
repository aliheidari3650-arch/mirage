package com.mirage.vpn.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Small canvas-drawn glyphs (the core icon set has no power / globe icons).

/** Power symbol: a gap-topped circle with a vertical bar. Coordinates follow a 24x24 grid. */
@Composable
fun PowerGlyph(color: Color, size: Dp = 46.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val stroke = Stroke(width = 2.2f * s, cap = StrokeCap.Round)
        drawLine(color, Offset(12f * s, 3f * s), Offset(12f * s, 11f * s), strokeWidth = 2.2f * s, cap = StrokeCap.Round)
        drawArc(
            color = color,
            startAngle = -45f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(4f * s, 4.4f * s),
            size = Size(16f * s, 16f * s),
            style = stroke,
        )
    }
}

@Composable
fun GlobeGlyph(color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val stroke = Stroke(width = 2f * s)
        drawCircle(color, radius = 9f * s, center = Offset(12f * s, 12f * s), style = stroke)
        drawLine(color, Offset(3f * s, 12f * s), Offset(21f * s, 12f * s), strokeWidth = 2f * s)
        drawOval(color, topLeft = Offset(8f * s, 3f * s), size = Size(8f * s, 18f * s), style = stroke)
    }
}
