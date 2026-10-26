package com.ogh.shared.ui

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Code-drawn line icons, kept dependency-free like the Ogh mark and navigation icons. */
internal enum class OghIcon {
    SCREEN,
    CAMERA,
    FLIP_CAMERA,
    MICROPHONE,
    SPEAKER,
    EYE,
    EYE_OFF,
    STOP,
    CHEVRON_RIGHT,
    CHEVRON_DOWN,
}

/**
 * Draws [icon] on a 24-unit grid. Icons are decorative: the adjacent text label
 * carries the accessible name, so no content description is exposed.
 */
@Composable
internal fun OghIconImage(
    icon: OghIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    Canvas(modifier.size(size)) {
        val scale = this.size.minDimension / 24f
        val stroke = Stroke(width = 2f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * scale, y * scale)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, p(x1, y1), p(x2, y2), stroke.width, StrokeCap.Round)
        fun polyline(vararg points: Float, close: Boolean = false) {
            val path = Path().apply {
                moveTo(points[0] * scale, points[1] * scale)
                for (i in 2 until points.size step 2) lineTo(points[i] * scale, points[i + 1] * scale)
                if (close) close()
            }
            drawPath(path, tint, style = stroke)
        }

        when (icon) {
            OghIcon.SCREEN -> {
                roundRect(tint, 6f, 2f, 12f, 20f, 2.5f, scale, stroke)
                line(10f, 18f, 14f, 18f)
            }
            OghIcon.CAMERA -> {
                cameraBody(tint, scale, stroke)
                drawCircle(tint, 3.5f * scale, p(12f, 13.5f), style = stroke)
            }
            OghIcon.FLIP_CAMERA -> {
                cameraBody(tint, scale, stroke)
                drawArc(tint, 200f, 130f, false, p(8f, 9.5f), Size(8f * scale, 8f * scale), style = stroke)
                drawArc(tint, 20f, 130f, false, p(8f, 9.5f), Size(8f * scale, 8f * scale), style = stroke)
                polyline(14.5f, 9.6f, 15.6f, 11.4f, 13.6f, 11.8f)
                polyline(9.5f, 17.4f, 8.4f, 15.6f, 10.4f, 15.2f)
            }
            OghIcon.MICROPHONE -> {
                roundRect(tint, 9f, 3f, 6f, 11f, 3f, scale, stroke)
                drawArc(tint, 0f, 180f, false, p(5.5f, 4.5f), Size(13f * scale, 13f * scale), style = stroke)
                line(12f, 17.5f, 12f, 21f)
                line(8.5f, 21f, 15.5f, 21f)
            }
            OghIcon.SPEAKER -> {
                polyline(3f, 9f, 7f, 9f, 12f, 4.5f, 12f, 19.5f, 7f, 15f, 3f, 15f, close = true)
                drawArc(tint, -45f, 90f, false, p(11f, 7.5f), Size(9f * scale, 9f * scale), style = stroke)
                drawArc(tint, -50f, 100f, false, p(7f, 4f), Size(16f * scale, 16f * scale), style = stroke)
            }
            OghIcon.EYE, OghIcon.EYE_OFF -> {
                val eye = Path().apply {
                    moveTo(2f * scale, 12f * scale)
                    quadraticTo(12f * scale, 3f * scale, 22f * scale, 12f * scale)
                    quadraticTo(12f * scale, 21f * scale, 2f * scale, 12f * scale)
                    close()
                }
                drawPath(eye, tint, style = stroke)
                drawCircle(tint, 3f * scale, p(12f, 12f), style = stroke)
                if (icon == OghIcon.EYE_OFF) line(4f, 4f, 20f, 20f)
            }
            OghIcon.STOP -> drawRoundRect(
                color = tint,
                topLeft = p(6f, 6f),
                size = Size(12f * scale, 12f * scale),
                cornerRadius = CornerRadius(2f * scale, 2f * scale),
            )
            OghIcon.CHEVRON_RIGHT -> polyline(9f, 5f, 16f, 12f, 9f, 19f)
            OghIcon.CHEVRON_DOWN -> polyline(5f, 9f, 12f, 16f, 19f, 9f)
        }
    }
}

private fun DrawScope.roundRect(
    color: Color,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    radius: Float,
    scale: Float,
    stroke: Stroke,
) = drawRoundRect(
    color = color,
    topLeft = Offset(x * scale, y * scale),
    size = Size(width * scale, height * scale),
    cornerRadius = CornerRadius(radius * scale, radius * scale),
    style = stroke,
)

private fun DrawScope.cameraBody(color: Color, scale: Float, stroke: Stroke) {
    roundRect(color, 2f, 7f, 20f, 13f, 2.5f, scale, stroke)
    val bump = Path().apply {
        moveTo(8f * scale, 7f * scale)
        lineTo(9.5f * scale, 4f * scale)
        lineTo(14.5f * scale, 4f * scale)
        lineTo(16f * scale, 7f * scale)
    }
    drawPath(bump, color, style = stroke)
}
