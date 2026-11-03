package com.roman.zemzeme.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Built-in decorative wallpaper patterns drawn on a canvas.
 * They are pure vector patterns so they cost nothing in storage and stay sharp.
 */
object WallpaperPatterns {
    const val DOTS = 0
    const val GEOMETRIC = 1
    const val WAVES = 2
    const val HEXAGONS = 3
    const val CIRCUIT = 4
    const val TOPOGRAPHY = 5
    const val COUNT = 6

    fun draw(scope: DrawScope, pattern: Int, color: Color, alpha: Float) {
        when (pattern) {
            DOTS -> drawDots(scope, color, alpha)
            GEOMETRIC -> drawGeometric(scope, color, alpha)
            WAVES -> drawWaves(scope, color, alpha)
            HEXAGONS -> drawHexagons(scope, color, alpha)
            CIRCUIT -> drawCircuit(scope, color, alpha)
            TOPOGRAPHY -> drawTopography(scope, color, alpha)
        }
    }

    private fun drawDots(scope: DrawScope, color: Color, alpha: Float) {
        val step = 48.dp.toPx()
        val radius = 1.6.dp.toPx()
        var y = step
        while (y < scope.size.height) {
            var x = step / 2
            while (x < scope.size.width) {
                scope.drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = radius,
                    center = Offset(x, y)
                )
                x += step
            }
            y += step
        }
    }

    private fun drawGeometric(scope: DrawScope, color: Color, alpha: Float) {
        val spacing = 64.dp.toPx()
        val stroke = 1.dp.toPx()
        var offset = -scope.size.height
        while (offset < scope.size.width + scope.size.height) {
            scope.drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(offset, 0f),
                end = Offset(offset + scope.size.height, scope.size.height),
                strokeWidth = stroke
            )
            offset += spacing
        }
        val box = 10.dp.toPx()
        var y = spacing / 2
        while (y < scope.size.height) {
            var x = spacing / 2
            while (x < scope.size.width) {
                scope.drawRect(
                    color = color.copy(alpha = alpha * 1.6f),
                    topLeft = Offset(x - box / 2, y - box / 2),
                    size = Size(box, box)
                )
                x += spacing
            }
            y += spacing
        }
    }

    private fun drawWaves(scope: DrawScope, color: Color, alpha: Float) {
        val bandHeight = 56.dp.toPx()
        val amplitude = 9.dp.toPx()
        val stroke = 1.2.dp.toPx()
        var band = 0
        var y = 0f
        while (y < scope.size.height + bandHeight) {
            val path = Path()
            val step = 12.dp.toPx()
            var x = 0f
            var up = true
            path.moveTo(0f, y)
            while (x < scope.size.width) {
                val next = x + step
                val controlY = if (up) y - amplitude else y + amplitude
                path.quadraticTo(x + step / 2, controlY, next, y)
                up = !up
                x = next
            }
            scope.drawPath(
                path = path,
                color = color.copy(alpha = alpha),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            band++
            y += bandHeight
        }
    }

    private fun drawHexagons(scope: DrawScope, color: Color, alpha: Float) {
        val radius = 26.dp.toPx()
        val stroke = 1.dp.toPx()
        val horizontal = radius * 1.5f
        val vertical = radius * 1.732f
        var row = 0
        var y = 0f
        while (y < scope.size.height + vertical) {
            var x = if (row % 2 == 0) 0f else horizontal / 2
            while (x < scope.size.width + horizontal) {
                val path = Path()
                for (i in 0 until 6) {
                    val angle = PI / 3.0 * i
                    val px = x + radius * cos(angle).toFloat()
                    val py = y + radius * sin(angle).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                scope.drawPath(
                    path = path,
                    color = color.copy(alpha = alpha),
                    style = Stroke(width = stroke)
                )
                x += horizontal
            }
            row++
            y += vertical / 2
        }
    }

    private fun drawCircuit(scope: DrawScope, color: Color, alpha: Float) {
        val grid = 44.dp.toPx()
        val stroke = 1.dp.toPx()
        val pad = 3.dp.toPx()
        var y = grid / 2
        var row = 0
        while (y < scope.size.height) {
            var x = if (row % 2 == 0) grid / 2 else grid
            while (x < scope.size.width) {
                val length = grid * (1 + (row + (x / grid).toInt()) % 3)
                val endX = minOf(x + length, scope.size.width)
                scope.drawLine(
                    color = color.copy(alpha = alpha),
                    start = Offset(x, y),
                    end = Offset(endX, y),
                    strokeWidth = stroke
                )
                scope.drawCircle(
                    color = color.copy(alpha = alpha * 1.8f),
                    radius = pad,
                    center = Offset(endX, y)
                )
                x += grid * 4
            }
            row++
            y += grid
        }
    }

    private fun drawTopography(scope: DrawScope, color: Color, alpha: Float) {
        val stroke = 1.dp.toPx()
        val base = 18.dp.toPx()
        val cx = scope.size.width * 0.5f
        val cy = scope.size.height * 0.5f
        var ring = 1
        while (ring <= 14) {
            val radius = base * ring
            val path = Path()
            val steps = 48
            for (i in 0..steps) {
                val angle = 2.0 * PI * i / steps
                val wobble = 1f + 0.08f * sin(angle * 3.0 + ring)
                val px = cx + radius * wobble * cos(angle).toFloat()
                val py = cy + radius * wobble * sin(angle).toFloat() * 0.8f
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            scope.drawPath(
                path = path,
                color = color.copy(alpha = alpha),
                style = Stroke(width = stroke)
            )
            ring++
        }
    }
}
