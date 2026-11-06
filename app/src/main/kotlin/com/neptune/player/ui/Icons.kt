package com.neptune.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Hand-drawn icon set — everything is vector geometry in a 24×24 grid. */
enum class NeIcon {
    Play, Pause, Next, Prev, Shuffle, Repeat, RepeatOne,
    Heart, HeartFill, Home, Search, Library, Sliders,
    ChevronDown, ChevronUp, More, Queue, Volume, Mute, Mic, Share,
    Plus, Close, Check, Sparkle, Wave, Disc, Artist, Equalizer, Moon, Reset
}

@Composable
fun NeIconView(
    icon: NeIcon,
    size: Dp = 24.dp,
    color: Color = Ne.Text,
    modifier: Modifier = Modifier,
    weight: Float = 1.85f,
) {
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            drawIcon(icon, color, weight)
        }
    }
}

private fun DrawScope.drawIcon(icon: NeIcon, c: Color, w: Float) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(c, Offset(x1, y1), Offset(x2, y2), strokeWidth = w, cap = StrokeCap.Round)

    fun path(data: Path, fill: Boolean = false) {
        if (fill) {
            drawPath(data, c)
            drawPath(data, c, style = Stroke(width = w * 0.9f, join = StrokeJoin.Round, cap = StrokeCap.Round))
        } else {
            drawPath(data, c, style = stroke)
        }
    }

    when (icon) {
        NeIcon.Play -> {
            val p = Path().apply {
                moveTo(8.4f, 5.0f); lineTo(19.0f, 12f); lineTo(8.4f, 19.0f); close()
            }
            path(p, fill = true)
        }

        NeIcon.Pause -> {
            drawRoundRect(c, Offset(7.1f, 5.2f), Size(3.2f, 13.6f), androidx.compose.ui.geometry.CornerRadius(1.5f))
            drawRoundRect(c, Offset(13.7f, 5.2f), Size(3.2f, 13.6f), androidx.compose.ui.geometry.CornerRadius(1.5f))
        }

        NeIcon.Next -> {
            path(Path().apply { moveTo(6.6f, 5.8f); lineTo(15.4f, 12f); lineTo(6.6f, 18.2f); close() }, fill = true)
            drawRoundRect(c, Offset(16.6f, 5.0f), Size(2.2f, 14f), androidx.compose.ui.geometry.CornerRadius(1.1f))
        }

        NeIcon.Prev -> {
            path(Path().apply { moveTo(17.4f, 5.8f); lineTo(8.6f, 12f); lineTo(17.4f, 18.2f); close() }, fill = true)
            drawRoundRect(c, Offset(5.2f, 5.0f), Size(2.2f, 14f), androidx.compose.ui.geometry.CornerRadius(1.1f))
        }

        NeIcon.Shuffle -> {
            strokePath(
                Path().apply {
                    moveTo(3.4f, 7.6f); lineTo(5.8f, 7.6f)
                    cubicTo(8.8f, 7.6f, 10.4f, 9.1f, 12f, 12f)
                    cubicTo(13.6f, 14.9f, 15.2f, 16.4f, 18.2f, 16.4f)
                    lineTo(20.4f, 16.4f)
                }, c, stroke
            )
            strokePath(Path().apply { moveTo(18.0f, 14.1f); lineTo(20.5f, 16.4f); lineTo(18.0f, 18.7f) }, c, stroke)
            strokePath(
                Path().apply {
                    moveTo(3.4f, 16.4f); lineTo(5.8f, 16.4f)
                    cubicTo(8.8f, 16.4f, 10.4f, 14.9f, 12f, 12f)
                    cubicTo(13.6f, 9.1f, 15.2f, 7.6f, 18.2f, 7.6f)
                    lineTo(20.4f, 7.6f)
                }, c, stroke
            )
            strokePath(Path().apply { moveTo(18.0f, 5.3f); lineTo(20.5f, 7.6f); lineTo(18.0f, 9.9f) }, c, stroke)
        }

        NeIcon.Repeat, NeIcon.RepeatOne -> {
            strokePath(
                Path().apply {
                    moveTo(4.6f, 12f); lineTo(4.6f, 10.2f)
                    cubicTo(4.6f, 7.3f, 6.9f, 5.1f, 9.8f, 5.1f)
                    lineTo(16.4f, 5.1f)
                }, c, stroke
            )
            strokePath(Path().apply { moveTo(14.0f, 2.7f); lineTo(16.6f, 5.1f); lineTo(14.0f, 7.5f) }, c, stroke)
            strokePath(
                Path().apply {
                    moveTo(19.4f, 12f); lineTo(19.4f, 13.8f)
                    cubicTo(19.4f, 16.7f, 17.1f, 18.9f, 14.2f, 18.9f)
                    lineTo(7.6f, 18.9f)
                }, c, stroke
            )
            strokePath(Path().apply { moveTo(10.0f, 21.3f); lineTo(7.4f, 18.9f); lineTo(10.0f, 16.5f) }, c, stroke)
            if (icon == NeIcon.RepeatOne) {
                drawCircle(c, radius = 1.5f, center = Offset(12f, 12f))
            }
        }

        NeIcon.Heart, NeIcon.HeartFill -> {
            val p = Path().apply {
                moveTo(12f, 20.4f)
                cubicTo(11.2f, 19.7f, 3.6f, 14.1f, 3.6f, 9.4f)
                cubicTo(3.6f, 6.4f, 5.9f, 4.3f, 8.5f, 4.3f)
                cubicTo(10.2f, 4.3f, 11.4f, 5.2f, 12f, 6.4f)
                cubicTo(12.6f, 5.2f, 13.8f, 4.3f, 15.5f, 4.3f)
                cubicTo(18.1f, 4.3f, 20.4f, 6.4f, 20.4f, 9.4f)
                cubicTo(20.4f, 14.1f, 12.8f, 19.7f, 12f, 20.4f)
                close()
            }
            path(p, fill = icon == NeIcon.HeartFill)
        }

        NeIcon.Home -> {
            strokePath(Path().apply { moveTo(4.0f, 11.2f); lineTo(12f, 4.2f); lineTo(20f, 11.2f) }, c, stroke)
            strokePath(
                Path().apply {
                    moveTo(6.3f, 9.7f); lineTo(6.3f, 18.6f)
                    cubicTo(6.3f, 19.5f, 7.0f, 20.2f, 7.9f, 20.2f)
                    lineTo(16.1f, 20.2f)
                    cubicTo(17.0f, 20.2f, 17.7f, 19.5f, 17.7f, 18.6f)
                    lineTo(17.7f, 9.7f)
                }, c, stroke
            )
        }

        NeIcon.Search -> {
            drawCircle(c, radius = 6.1f, center = Offset(10.7f, 10.7f), style = stroke)
            line(15.3f, 15.3f, 20.2f, 20.2f)
        }

        NeIcon.Library -> {
            line(9.6f, 6.4f, 19.9f, 6.4f)
            line(9.6f, 12f, 19.9f, 12f)
            line(9.6f, 17.6f, 19.9f, 17.6f)
            drawCircle(c, radius = 1.15f, center = Offset(5.3f, 6.4f))
            drawCircle(c, radius = 1.15f, center = Offset(5.3f, 12f))
            drawCircle(c, radius = 1.15f, center = Offset(5.3f, 17.6f))
        }

        NeIcon.Sliders -> {
            val rows = listOf(6.4f to 15.2f, 12f to 8.4f, 17.6f to 16.4f)
            rows.forEach { (y, knob) ->
                line(4.0f, y, knob - 3.6f, y)
                line(knob + 3.6f, y, 20.0f, y)
                drawCircle(c, radius = 2.25f, center = Offset(knob, y))
            }
        }

        NeIcon.ChevronDown -> strokePath(
            Path().apply { moveTo(5.6f, 9.2f); lineTo(12f, 15.6f); lineTo(18.4f, 9.2f) }, c, stroke
        )

        NeIcon.ChevronUp -> strokePath(
            Path().apply { moveTo(5.6f, 14.8f); lineTo(12f, 8.4f); lineTo(18.4f, 14.8f) }, c, stroke
        )

        NeIcon.More -> {
            drawCircle(c, 1.7f, Offset(5.4f, 12f))
            drawCircle(c, 1.7f, Offset(12f, 12f))
            drawCircle(c, 1.7f, Offset(18.6f, 12f))
        }

        NeIcon.Queue -> {
            line(10.2f, 6.6f, 20.0f, 6.6f)
            line(10.2f, 12f, 20.0f, 12f)
            line(10.2f, 17.4f, 16.4f, 17.4f)
            drawCircle(c, 1.9f, Offset(6.4f, 17.0f))
            line(8.3f, 17.0f, 8.3f, 8.4f)
            line(8.3f, 8.4f, 12.0f, 7.6f)
        }

        NeIcon.Volume, NeIcon.Mute -> {
            strokePath(
                Path().apply {
                    moveTo(3.6f, 9.5f); lineTo(6.6f, 9.5f); lineTo(11.0f, 5.6f)
                    lineTo(11.0f, 18.4f); lineTo(6.6f, 14.5f); lineTo(3.6f, 14.5f); close()
                }, c, stroke
            )
            if (icon == NeIcon.Volume) {
                drawArc(
                    c, startAngle = -50f, sweepAngle = 100f, useCenter = false,
                    topLeft = Offset(9.6f, 7.6f), size = Size(7.0f, 8.8f), style = stroke
                )
                drawArc(
                    c, startAngle = -52f, sweepAngle = 104f, useCenter = false,
                    topLeft = Offset(10.6f, 4.6f), size = Size(11.0f, 14.8f), style = stroke
                )
            } else {
                line(14.6f, 9.4f, 19.4f, 14.6f)
                line(19.4f, 9.4f, 14.6f, 14.6f)
            }
        }

        NeIcon.Mic -> {
            drawRoundRect(
                c, Offset(9.0f, 3.2f), Size(6.0f, 11.2f),
                androidx.compose.ui.geometry.CornerRadius(3.0f), style = stroke
            )
            drawArc(
                c, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(6.0f, 8.6f), size = Size(12.0f, 9.0f), style = stroke
            )
            line(12f, 17.6f, 12f, 21.0f)
        }

        NeIcon.Share -> {
            strokePath(
                Path().apply {
                    moveTo(12f, 15.4f); lineTo(12f, 3.6f)
                }, c, stroke
            )
            strokePath(Path().apply { moveTo(7.6f, 8.0f); lineTo(12f, 3.6f); lineTo(16.4f, 8.0f) }, c, stroke)
            strokePath(
                Path().apply {
                    moveTo(5.2f, 13.0f); lineTo(5.2f, 18.8f)
                    cubicTo(5.2f, 19.7f, 5.9f, 20.4f, 6.8f, 20.4f)
                    lineTo(17.2f, 20.4f)
                    cubicTo(18.1f, 20.4f, 18.8f, 19.7f, 18.8f, 18.8f)
                    lineTo(18.8f, 13.0f)
                }, c, stroke
            )
        }

        NeIcon.Plus -> {
            line(12f, 5.2f, 12f, 18.8f)
            line(5.2f, 12f, 18.8f, 12f)
        }

        NeIcon.Close -> {
            line(6.2f, 6.2f, 17.8f, 17.8f)
            line(17.8f, 6.2f, 6.2f, 17.8f)
        }

        NeIcon.Check -> strokePath(
            Path().apply { moveTo(5.0f, 12.8f); lineTo(9.8f, 17.6f); lineTo(19.2f, 6.4f) }, c, stroke
        )

        NeIcon.Sparkle -> {
            val p = Path().apply {
                moveTo(12f, 3.0f)
                cubicTo(13.0f, 8.4f, 15.6f, 11.0f, 21.0f, 12.0f)
                cubicTo(15.6f, 13.0f, 13.0f, 15.6f, 12f, 21.0f)
                cubicTo(11.0f, 15.6f, 8.4f, 13.0f, 3.0f, 12.0f)
                cubicTo(8.4f, 11.0f, 11.0f, 8.4f, 12f, 3.0f)
                close()
            }
            path(p, fill = true)
        }

        NeIcon.Wave, NeIcon.Equalizer -> {
            val bars = if (icon == NeIcon.Wave)
                listOf(3.6f to 8f, 7.6f to 14f, 11.6f to 17f, 15.6f to 11f, 19.6f to 6f)
            else
                listOf(3.6f to 6f, 7.6f to 12f, 11.6f to 18f, 15.6f to 9f, 19.6f to 14f)
            bars.forEach { (x, h) ->
                drawRoundRect(
                    c, Offset(x, 12f - h / 2f), Size(2.2f, h),
                    androidx.compose.ui.geometry.CornerRadius(1.1f)
                )
            }
        }

        NeIcon.Disc -> {
            drawCircle(c, radius = 8.6f, center = Offset(12f, 12f), style = stroke)
            drawCircle(c, radius = 2.4f, center = Offset(12f, 12f), style = stroke)
            drawCircle(c, radius = 0.8f, center = Offset(12f, 12f))
        }

        NeIcon.Artist -> {
            drawCircle(c, radius = 3.6f, center = Offset(12f, 8.2f), style = stroke)
            drawArc(
                c, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(5.0f, 13.0f), size = Size(14f, 8.4f), style = stroke
            )
        }

        NeIcon.Moon -> {
            strokePath(
                Path().apply {
                    moveTo(16.4f, 3.8f)
                    cubicTo(10.4f, 5.0f, 7.6f, 11.4f, 11.0f, 16.2f)
                    cubicTo(13.4f, 19.6f, 17.6f, 20.4f, 20.4f, 19.0f)
                    cubicTo(14.6f, 17.2f, 13.6f, 8.2f, 16.4f, 3.8f)
                    close()
                }, c, stroke
            )
        }

        NeIcon.Reset -> {
            drawArc(
                c, startAngle = 40f, sweepAngle = 285f, useCenter = false,
                topLeft = Offset(4.0f, 4.0f), size = Size(16f, 16f), style = stroke
            )
            strokePath(Path().apply { moveTo(16.4f, 2.4f); lineTo(19.6f, 6.2f); lineTo(15.2f, 7.2f) }, c, stroke)
        }
    }
}

private fun DrawScope.strokePath(path: Path, color: Color, stroke: Stroke) {
    drawPath(path, color, style = stroke)
}
