package com.thegreatnovel.jingyouhealth.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.thegreatnovel.jingyouhealth.model.TrendPoint
import kotlin.math.floor

/**
 * A local, descriptive scale. [history] must contain only the caller's prior
 * 28-day window. No baseline is drawn before seven finite observations exist.
 * The caller supplies labels, units and accessibility semantics through [modifier].
 */
@Composable
fun BaselinePositionChart(
    current: Float?,
    history: List<Float>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val sorted = remember(history) { history.filter(Float::isFinite).sorted() }
    val observed = current?.takeIf(Float::isFinite)
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        if (sorted.size < 7) return@Canvas
        val inset = 12.dp.toPx()
        val width = size.width - inset * 2f
        if (width <= 0f || size.height <= 0f) return@Canvas
        val historyMin = sorted.first()
        val historyMax = sorted.last()
        val min = minOf(historyMin, observed ?: historyMin)
        val max = maxOf(historyMax, observed ?: historyMax)
        fun x(value: Float): Float = inset + width * normalized(value, min, max)
        val centerY = size.height / 2f

        // The track represents the observed historical range; the complete local
        // scale can extend farther when today's value lies outside that range.
        drawLine(
            color = ink.copy(alpha = 0.18f),
            start = Offset(x(historyMin), centerY),
            end = Offset(x(historyMax), centerY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
        val q1 = x(quantile(sorted, 0.25f))
        val q3 = x(quantile(sorted, 0.75f))
        val bandWidth = (q3 - q1).coerceAtLeast(2.dp.toPx())
        val bandHeight = minOf(14.dp.toPx(), size.height * 0.45f)
        drawRoundRect(
            color = accent.copy(alpha = 0.24f),
            topLeft = Offset((q1 + q3 - bandWidth) / 2f, centerY - bandHeight / 2f),
            size = Size(bandWidth, bandHeight),
            cornerRadius = CornerRadius(bandHeight / 2f),
        )
        val median = x(quantile(sorted, 0.5f))
        val tickHalf = minOf(10.dp.toPx(), size.height * 0.35f)
        drawLine(
            color = ink.copy(alpha = 0.68f),
            start = Offset(median, centerY - tickHalf),
            end = Offset(median, centerY + tickHalf),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        observed?.let {
            val dot = Offset(x(it), centerY)
            drawCircle(accent.copy(alpha = 0.14f), radius = 9.dp.toPx(), center = dot)
            drawCircle(accent, radius = 4.5.dp.toPx(), center = dot)
        }
    }
}

/**
 * [points] are chronological, continuous calendar-day slots supplied by the caller.
 * Null, negative and non-finite sleep durations remain missing marks. Values are
 * hours; the vertical scale always starts at zero and includes [baseline].
 */
@Composable
fun NightHistoryChart(
    points: List<TrendPoint>,
    baseline: Float?,
    accent: Color,
    modifier: Modifier = Modifier,
    selectedDate: String? = null,
) {
    val values = remember(points) { points.map { point -> point.value?.takeIf { it.isFinite() && it >= 0f } } }
    val reference = baseline?.takeIf { it.isFinite() && it >= 0f }
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val inset = 12.dp.toPx()
        val width = size.width - inset * 2f
        val height = size.height - inset * 2f
        if (width <= 0f || height <= 0f) return@Canvas
        val bottom = size.height - inset
        val max = maxOf(values.filterNotNull().maxOrNull() ?: 0f, reference ?: 0f)
        val ceiling = if (max > 0f) max * 1.12f else 1f
        val slotWidth = width / points.size
        val barWidth = minOf(slotWidth * 0.60f, 16.dp.toPx())
        fun y(value: Float) = bottom - height * (value / ceiling).coerceIn(0f, 1f)

        drawLine(ink.copy(alpha = 0.10f), Offset(inset, bottom), Offset(size.width - inset, bottom), 1.dp.toPx())
        points.forEachIndexed { index, point ->
            val centerX = inset + slotWidth * (index + 0.5f)
            val value = values[index]
            if (value == null) {
                // A separated dash signals absence rather than a zero-height bar.
                val half = minOf(3.dp.toPx(), slotWidth * 0.20f)
                drawLine(
                    ink.copy(alpha = 0.44f),
                    Offset(centerX - half, bottom - 4.dp.toPx()),
                    Offset(centerX + half, bottom - 4.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            } else {
                val barHeight = (bottom - y(value)).coerceAtLeast(1.dp.toPx())
                val top = bottom - barHeight
                val selected = point.date == selectedDate
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = if (selected) 1f else 0.65f), accent.copy(alpha = if (selected) 0.72f else 0.28f)),
                        startY = top,
                        endY = bottom,
                    ),
                    topLeft = Offset(centerX - barWidth / 2f, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(minOf(barWidth / 2f, barHeight / 2f)),
                )
            }
        }
        reference?.let {
            val baselineY = y(it)
            drawLine(
                color = ink.copy(alpha = 0.54f),
                start = Offset(inset, baselineY),
                end = Offset(size.width - inset, baselineY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        }
    }
}

/**
 * Actual paired observations: X is sleep duration; Y is the compared signal.
 * Each axis uses the finite pairs' observed min/max; a constant axis is centered.
 * There is deliberately no fitted line, jitter, significance or inferred trend.
 * The caller supplies the matching axis labels and descriptive semantics.
 */
@Composable
fun AssociationScatterPlot(
    pairs: List<Pair<Float, Float>>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val finitePairs = remember(pairs) { pairs.filter { (x, y) -> x.isFinite() && y.isFinite() } }
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val inset = 12.dp.toPx()
        val width = size.width - inset * 2f
        val height = size.height - inset * 2f
        if (width <= 0f || height <= 0f) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        drawLine(ink.copy(alpha = 0.12f), Offset(center.x, inset), Offset(center.x, size.height - inset), 1.dp.toPx())
        drawLine(ink.copy(alpha = 0.12f), Offset(inset, center.y), Offset(size.width - inset, center.y), 1.dp.toPx())
        if (finitePairs.isEmpty()) return@Canvas
        val minX = finitePairs.minOf { it.first }
        val maxX = finitePairs.maxOf { it.first }
        val minY = finitePairs.minOf { it.second }
        val maxY = finitePairs.maxOf { it.second }
        finitePairs.forEach { (x, y) ->
            drawCircle(
                color = accent.copy(alpha = 0.62f),
                radius = 3.6.dp.toPx(),
                center = Offset(inset + width * normalized(x, minX, maxX), inset + height * (1f - normalized(y, minY, maxY))),
            )
        }
    }
}

private fun normalized(value: Float, min: Float, max: Float): Float =
    if (max == min) 0.5f else ((value.toDouble() - min) / (max.toDouble() - min)).toFloat().coerceIn(0f, 1f)

/** Linear interpolation between adjacent order statistics; median is P50. */
private fun quantile(sorted: List<Float>, fraction: Float): Float {
    val position = (sorted.lastIndex * fraction).coerceIn(0f, sorted.lastIndex.toFloat())
    val lower = floor(position).toInt()
    val upper = (lower + 1).coerceAtMost(sorted.lastIndex)
    val weight = position - lower
    return (sorted[lower].toDouble() * (1f - weight) + sorted[upper].toDouble() * weight).toFloat()
}
