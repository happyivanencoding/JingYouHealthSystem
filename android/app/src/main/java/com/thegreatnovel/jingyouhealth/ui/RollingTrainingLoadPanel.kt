package com.thegreatnovel.jingyouhealth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.thegreatnovel.jingyouhealth.model.TrainingLoadPoint
import com.thegreatnovel.jingyouhealth.model.TrainingLoadValues
import com.thegreatnovel.jingyouhealth.ui.components.GlassPanel
import com.thegreatnovel.jingyouhealth.ui.theme.ArcticBlue
import com.thegreatnovel.jingyouhealth.ui.theme.AuroraViolet
import com.thegreatnovel.jingyouhealth.ui.theme.ElectricCyan
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val VIEWPORT_DAYS = 28
private val ROLLING_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd")

/**
 * Rolling 7/28-day load graph. The LazyRow intentionally owns all history while the viewport is
 * fixed to 28 integer-pixel slots, so it can share the Activity timeline state without making a
 * narrow day column. Missing rolling values remain gaps; recorded partial totals are shown only
 * in the readout and never converted into a zero-height line.
 */
@Composable
fun RollingTrainingLoadPanel(
    history: List<TrainingLoadPoint>,
    allDates: List<LocalDate>,
    windowStartIndex: Int,
    selectedDay: LocalDate?,
    timelineState: LazyListState,
    category: String?,
    windowDays: Int,
    onDayClick: (LocalDate) -> Unit,
    onMethod: () -> Unit,
) {
    val effectiveWindowDays = if (windowDays == 7) 7 else 28
    val byDate = remember(history) { history.associateBy { it.date } }
    val values = remember(history, category, effectiveWindowDays) {
        history.mapNotNull { point ->
            val date = runCatching { LocalDate.parse(point.date) }.getOrNull() ?: return@mapNotNull null
            date to point.valuesFor(category)
        }.toMap()
    }
    val maximum = remember(history, category, effectiveWindowDays) {
        history.asSequence()
            .map { it.valuesFor(category) }
            .flatMap { item ->
                sequenceOf(
                    if (effectiveWindowDays == 7) item.load7 else item.load28,
                    if (effectiveWindowDays == 7) item.referenceWeekly else item.reference28,
                )
            }
            .filter { it != null && it.isFinite() && it >= 0.0 }
            .map { it!! }
            .maxOrNull()
            ?.coerceAtLeast(1.0)
            ?: 1.0
    }
    val itemCount = timelineState.layoutInfo.totalItemsCount
    val maxStart = (allDates.size - VIEWPORT_DAYS).coerceAtLeast(0)
    val startIndex = if (itemCount == allDates.size && allDates.isNotEmpty()) {
        timelineState.firstVisibleItemIndex.coerceIn(0, maxStart)
    } else {
        windowStartIndex.coerceIn(0, maxStart)
    }
    val visibleDates = allDates.drop(startIndex).take(VIEWPORT_DAYS)
    val visibleEnd = visibleDates.lastOrNull()
    val readoutDate = selectedDay ?: visibleEnd
    val readoutValues = readoutDate?.let { values[it] }
    val readoutLoad = readoutValues?.let { if (effectiveWindowDays == 7) it.load7 else it.load28 }
    val readoutRecorded = readoutValues?.let { if (effectiveWindowDays == 7) it.recorded7 else it.recorded28 }
    val readoutCoverage = readoutDate?.let { byDate[it.toString()]?.coverage(effectiveWindowDays) } ?: 0
    val readoutWindowLabel = tr(if (effectiveWindowDays == 7) "近 7 天刺激" else "近 28 天积累")

    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        accent = ElectricCyan,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(tr("滚动训练负荷"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr(if (effectiveWindowDays == 7) "7天滚动" else "28天滚动"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onMethod) {
                    Icon(Icons.Rounded.Info, contentDescription = tr("节奏的计算方法"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        readoutDate?.let { "${rollingDateLabel(it)} · $readoutWindowLabel" } ?: readoutWindowLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        readoutLoad?.let { coreNumber(it, "AU") } ?: tr("暂无完整窗口"),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    if (readoutLoad == null && readoutRecorded != null) {
                        Text(
                            tr("已记录") + " " + coreNumber(readoutRecorded, "AU"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "$readoutCoverage/$effectiveWindowDays ${tr("天")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (effectiveWindowDays == 7) {
                val reference = readoutValues?.referenceWeekly
                if (reference != null && reference.isFinite()) {
                    Text(
                        tr("平时一周参考") + " · " + coreNumber(reference, "AU"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val reference = readoutValues?.reference28
                if (reference != null && reference.isFinite()) {
                    Text(
                        tr("上一段28天参考") + " · " + coreNumber(reference, "AU"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val viewportPx = with(density) { maxWidth.toPx().toInt().coerceAtLeast(1) }
                val slotWidthPx = (viewportPx / VIEWPORT_DAYS).coerceAtLeast(1)
                val slotWidth = with(density) { slotWidthPx.toDp() }
                val timelineWidth = with(density) { (slotWidthPx * VIEWPORT_DAYS).toDp() }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(Modifier.width(timelineWidth)) {
                            LazyRow(
                                state = timelineState,
                                modifier = Modifier.fillMaxWidth().height(150.dp),
                                horizontalArrangement = Arrangement.spacedBy(0.dp),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                itemsIndexed(allDates, key = { _, date -> date.toString() }) { index, date ->
                                    RollingLoadSlot(
                                        modifier = Modifier.width(slotWidth).height(150.dp),
                                        index = index,
                                        date = date,
                                        allDates = allDates,
                                        values = values,
                                        maximum = maximum,
                                        windowDays = effectiveWindowDays,
                                        selected = date == selectedDay,
                                        onClick = { onDayClick(date) },
                                    )
                                }
                            }
                            RollingDateAxis(visibleDates, slotWidth)
                        }
                    }
                }
            }
            RollingLoadLegend(effectiveWindowDays)
            Text(
                tr("缺少完整覆盖时只显示已记录值，不补造为零；虚线只是个人参考。"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RollingLoadLegend(windowDays: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        RollingLegendItem(
            color = if (windowDays == 7) ElectricCyan else ArcticBlue,
            label = tr(if (windowDays == 7) "近 7 天刺激" else "近 28 天积累"),
        )
        RollingLegendItem(
            color = AuroraViolet,
            label = tr(if (windowDays == 7) "平时一周参考" else "上一段28天参考"),
            dashed = true,
        )
    }
}

@Composable
private fun RollingLegendItem(color: Color, label: String, dashed: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(20.dp).height(8.dp)) {
            val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())) else null
            drawLine(color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 2.dp.toPx(), StrokeCap.Round, effect)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RollingLoadSlot(
    modifier: Modifier,
    index: Int,
    date: LocalDate,
    allDates: List<LocalDate>,
    values: Map<LocalDate, TrainingLoadValues>,
    maximum: Double,
    windowDays: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val current = values[date]
    val previousDate = allDates.getOrNull(index - 1)
    val nextDate = allDates.getOrNull(index + 1)
    val previous = previousDate?.takeIf { it.plusDays(1) == date }?.let { values[it] }
    val next = nextDate?.takeIf { date.plusDays(1) == it }?.let { values[it] }
    val value = current?.load(windowDays)
    val previousValue = previous?.load(windowDays)
    val nextValue = next?.load(windowDays)
    val reference = if (windowDays == 7) current?.referenceWeekly else current?.reference28
    val previousReference = if (windowDays == 7) previous?.referenceWeekly else previous?.reference28
    val nextReference = if (windowDays == 7) next?.referenceWeekly else next?.reference28
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = date.toString() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val top = 10.dp.toPx()
            val bottom = size.height - 10.dp.toPx()
            val axisColor = onSurfaceColor.copy(alpha = 0.12f)
            val lineColor = if (windowDays == 7) ElectricCyan else ArcticBlue
            val referenceColor = AuroraViolet.copy(alpha = 0.70f)
            drawLine(axisColor, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx(), StrokeCap.Round)

            fun yFor(raw: Double): Float = bottom - ((raw / maximum).toFloat().coerceIn(0f, 1f) * (bottom - top))
            fun drawSeries(currentValue: Double?, previousValue: Double?, nextValue: Double?, color: Color, stroke: Float, dashed: Boolean = false) {
                if (currentValue == null || !currentValue.isFinite()) return
                val x = size.width / 2f
                val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null
                val y = yFor(currentValue)
                previousValue?.takeIf { it.isFinite() }?.let { drawLine(color, Offset(0f, yFor((it + currentValue) / 2)), Offset(x, y), stroke, StrokeCap.Round, effect) }
                nextValue?.takeIf { it.isFinite() }?.let { drawLine(color, Offset(x, y), Offset(size.width, yFor((it + currentValue) / 2)), stroke, StrokeCap.Round, effect) }
                if (!dashed || selected) drawCircle(color, if (selected) 4.dp.toPx() else 2.5.dp.toPx(), Offset(x, y))
                if (selected) drawCircle(onSurfaceColor.copy(alpha = 0.70f), 6.dp.toPx(), Offset(x, y), style = Stroke(1.dp.toPx()))
            }

            drawSeries(value, previousValue, nextValue, lineColor, 2.dp.toPx())
            drawSeries(reference, previousReference, nextReference, referenceColor, 1.5.dp.toPx(), dashed = true)
        }
    }
}

@Composable
private fun RollingDateAxis(visibleDates: List<LocalDate>, slotWidth: Dp) {
    Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        visibleDates.chunked(7).forEach { group ->
            Box(Modifier.width(slotWidth * group.size)) {
                Text(
                    group.firstOrNull()?.format(ROLLING_DATE_FORMAT).orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun TrainingLoadValues.load(windowDays: Int): Double? = if (windowDays == 7) load7 else load28

private fun TrainingLoadPoint.valuesFor(category: String?): TrainingLoadValues =
    if (category == null) all else categories[category] ?: TrainingLoadValues()

private fun TrainingLoadPoint.coverage(windowDays: Int): Int = if (windowDays == 7) coverage7 else coverage28

private fun rollingDateLabel(date: LocalDate): String = date.format(ROLLING_DATE_FORMAT)
