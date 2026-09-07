package com.thegreatnovel.jingyouhealth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import com.thegreatnovel.jingyouhealth.model.AppLanguage
import com.thegreatnovel.jingyouhealth.model.TrendPoint
import com.thegreatnovel.jingyouhealth.model.calendarWindow
import com.thegreatnovel.jingyouhealth.ui.components.BaselinePositionChart
import com.thegreatnovel.jingyouhealth.ui.components.GlassPanel
import com.thegreatnovel.jingyouhealth.ui.components.StateTimelineChart
import com.thegreatnovel.jingyouhealth.ui.theme.Amber
import com.thegreatnovel.jingyouhealth.ui.theme.AuroraViolet
import com.thegreatnovel.jingyouhealth.ui.theme.ElectricCyan
import com.thegreatnovel.jingyouhealth.ui.theme.Rose
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/** Each signal keeps its own measurement date; latest metrics need not belong to one day. */
@Composable
fun RecoverySignalsPanel(
    state: JingYouUiState,
    onSleep: () -> Unit,
    onMetric: (HealthMetric) -> Unit,
) {
    val hrv = state.dashboard?.hrv
    val hrvValue = signalCurrent(state.trends.hrv, hrv?.date, hrv?.lastNightAvg)
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(18.dp),
        accent = AuroraViolet,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("恢复的线索"), style = MaterialTheme.typography.titleMedium)
            SignalBaselineRow(
                label = tr("昨夜 HRV"),
                current = hrvValue,
                points = state.trends.hrv,
                date = hrv?.date,
                unit = "ms",
                accent = AuroraViolet,
                onClick = { onMetric(HealthMetric.HRV) },
            )
            SignalsDivider()
            SignalBaselineRow(
                label = tr("静息心率"),
                current = signalCurrent(state.trends.restingHr, state.dashboard?.daily?.date, state.dashboard?.daily?.restingHr),
                points = state.trends.restingHr,
                date = state.dashboard?.daily?.date,
                unit = "bpm",
                accent = Rose,
                onClick = { onMetric(HealthMetric.RHR) },
            )
        }
    }
}

@Composable
fun DailySignalsPanel(state: JingYouUiState, onMetric: (HealthMetric) -> Unit) {
    val timeline = state.stateTimeline
    val summary = timeline?.summary
    val bodyPlot = remember(timeline?.bodyBattery) {
        timeline?.bodyBattery.orEmpty().mapNotNull { point ->
            val minute = stateMinute(point.timestamp)
            val value = point.value?.takeIf { it.isFinite() && it in 0f..100f }
            if (minute == null || value == null) null else minute to value
        }
    }
    val stressPlot = remember(timeline?.stress) {
        timeline?.stress.orEmpty().mapNotNull { point ->
            stateMinute(point.timestamp)?.let { it to point.value?.takeIf { value -> value.isFinite() && value in 0f..100f } }
        }
    }
    val activityMinutes = remember(timeline?.activities) {
        timeline?.activities.orEmpty().mapNotNull { stateMinute(it.startTime) }
    }
    val sleepRange = remember(timeline?.sleepStart, timeline?.sleepEnd, timeline?.date) {
        stateSleepRange(timeline?.sleepStart, timeline?.sleepEnd, timeline?.date)
    }
    val currentMinute = remember(timeline?.generatedAt, timeline?.date) {
        timeline?.generatedAt?.takeIf { it.take(10) == timeline?.date }?.let(::stateMinute)
    }
    val latestMinute = bodyPlot.lastOrNull()?.first ?: stressPlot.lastOrNull()?.first
    var cursorMinute by remember(timeline?.date, timeline?.generatedAt) {
        mutableFloatStateOf(latestMinute ?: currentMinute ?: 0f)
    }
    val selectedEnergy = bodyPlot.minByOrNull { kotlin.math.abs(it.first - cursorMinute) }
        ?.takeIf { kotlin.math.abs(it.first - cursorMinute) <= 18f }?.second
    val selectedStress = stressPlot.filter { it.second != null }.minByOrNull { kotlin.math.abs(it.first - cursorMinute) }
        ?.takeIf { kotlin.math.abs(it.first - cursorMinute) <= 18f }?.second
    val stressHistory = remember(state.trends.stress, timeline?.date) {
        val previous = timeline?.date?.let { runCatching { LocalDate.parse(it).minusDays(1).toString() }.getOrNull() }
        calendarWindow(state.trends.stress, previous, days = 42)
            .mapNotNull { it.value?.takeIf { value -> value.isFinite() && value >= 0f } }
            .sorted()
    }
    val stressDetail = when {
        summary?.averageStress == null -> tr("暂无数据")
        stressHistory.size < 7 -> tr("正在积累你的平时范围")
        summary.averageStress < signalQuantile(stressHistory, 0.25f) -> tr("低于你的平时范围")
        summary.averageStress > signalQuantile(stressHistory, 0.75f) -> tr("高于你的平时范围")
        else -> tr("在你的平时范围内")
    }

    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(18.dp), accent = ElectricCyan) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tr("今日状态"), style = MaterialTheme.typography.titleMedium)
                Text(tr("精力余量与压力，按一天展开"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StateMetric(
                    label = tr("精力余量"),
                    value = signalNumber(summary?.latestBodyBattery),
                    detail = energyLabel(summary?.latestBodyBattery),
                    accent = ElectricCyan,
                    modifier = Modifier.weight(1f),
                )
                StateMetric(
                    label = tr("今日压力"),
                    value = signalNumber(summary?.averageStress),
                    detail = stressDetail,
                    accent = Amber,
                    modifier = Modifier.weight(1f),
                    onClick = { onMetric(HealthMetric.STRESS) },
                )
            }
            if (timeline == null || (bodyPlot.isEmpty() && stressPlot.isEmpty())) {
                Text(tr("同步 Garmin 后显示今天的精力与压力变化"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    stateClockLabel(cursorMinute) + " · " + tr("精力") + " " + signalNumber(selectedEnergy) + " · " + tr("压力") + " " + signalNumber(selectedStress),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StateTimelineChart(
                    bodyBattery = bodyPlot,
                    stress = stressPlot,
                    sleepRange = sleepRange,
                    activityMinutes = activityMinutes,
                    currentMinute = currentMinute,
                    cursorMinute = cursorMinute,
                    energyStart = ElectricCyan,
                    energyEnd = AuroraViolet,
                    stressAccent = Amber,
                    sleepAccent = AuroraViolet,
                    activityAccent = Rose,
                    onCursorMinute = { cursorMinute = it },
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("00", "06", "12", "18", "24").forEach {
                        Text(signalBidi(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    StateLegend(ElectricCyan, tr("精力"))
                    StateLegend(Amber, tr("压力"))
                    if (sleepRange != null) StateLegend(AuroraViolet, tr("睡眠"))
                    if (activityMinutes.isNotEmpty()) StateLegend(Rose, tr("运动"))
                }
                summary?.highestStressWindow?.let { window ->
                    val start = stateClockFromTimestamp(window.start)
                    val end = stateClockFromTimestamp(window.end)
                    if (start != null && end != null) {
                        Text(
                            tr("压力最集中的时段") + " · " + signalBidi("$start–$end"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                summary?.bodyBatteryDropSincePeak?.takeIf { it > 0f }?.let {
                    Text(
                        tr("从今日精力高点已消耗") + " " + signalNumber(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StateMetric(
    label: String,
    value: String,
    detail: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val card = modifier
        .clip(RoundedCornerShape(16.dp))
        .background(accent.copy(alpha = 0.07f))
        .then(if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick))
        .padding(horizontal = 14.dp, vertical = 12.dp)
    Column(card, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Medium)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = accent)
    }
}

@Composable
private fun StateLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.width(14.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = 0.82f)))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun energyLabel(value: Float?): String = tr(
    when {
        value == null -> "暂无数据"
        value <= 20f -> "很低"
        value <= 40f -> "偏低"
        value <= 70f -> "中等"
        else -> "充足"
    }
)

private fun stateMinute(value: String?): Float? {
    if (value.isNullOrBlank()) return null
    val time = runCatching { OffsetDateTime.parse(value).toLocalTime() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value.replace(' ', 'T')).toLocalTime() }.getOrNull()
        ?: return null
    return time.hour * 60f + time.minute + time.second / 60f
}

private fun stateSleepRange(start: String?, end: String?, target: String?): Pair<Float, Float>? {
    val targetDate = target?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    fun local(value: String?): LocalDateTime? = value?.let {
        runCatching { OffsetDateTime.parse(it).toLocalDateTime() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(it.replace(' ', 'T')) }.getOrNull()
    }
    val from = local(start) ?: return null
    val to = local(end) ?: return null
    val startMinute = if (from.toLocalDate().isBefore(targetDate)) 0f else stateMinute(from.toString()) ?: return null
    val endMinute = if (to.toLocalDate().isAfter(targetDate)) 1440f else stateMinute(to.toString()) ?: return null
    return if (endMinute > startMinute) startMinute to endMinute else null
}

@Composable
private fun stateClockLabel(minute: Float): String {
    val total = minute.toInt().coerceIn(0, 1439)
    val locale = Locale.forLanguageTag(LocalAppLanguage.current.tag)
    return signalBidi(String.format(locale, "%02d:%02d", total / 60, total % 60))
}

@Composable
private fun stateClockFromTimestamp(value: String?): String? =
    stateMinute(value)?.let { stateClockLabel(it) }

@Composable
private fun SignalBaselineRow(
    label: String,
    current: Float?,
    points: List<TrendPoint>,
    date: String?,
    unit: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val history = remember(points, date) {
        val previousDay = date?.let { runCatching { LocalDate.parse(it).minusDays(1).toString() }.getOrNull() }
        calendarWindow(points, previousDay, days = 42)
            .mapNotNull { it.value?.takeIf { value -> value.isFinite() && value >= 0f } }
    }
    val sorted = remember(history) { history.sorted() }
    val enoughHistory = sorted.size >= 7
    val low = if (enoughHistory) signalQuantile(sorted, 0.25f) else null
    val high = if (enoughHistory) signalQuantile(sorted, 0.75f) else null
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(signalDate(date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(signalNumber(current, unit), fontSize = 25.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(9.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        }
        when {
            enoughHistory -> {
                Text(
                    tr(when {
                        current == null -> "这次读数暂无数据"
                        current < low!! -> "低于你的平时范围"
                        current > high!! -> "高于你的平时范围"
                        else -> "在你的平时范围内"
                    }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BaselinePositionChart(current, history, accent, Modifier.fillMaxWidth().height(48.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (current != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
                            Text(tr("当前读数"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.width(20.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(accent.copy(alpha = 0.25f)))
                        Text(
                            tr("平时多在") + " " + signalRange(low!!, high!!, unit),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            else -> {
                Text(tr("正在积累你的平时范围"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(tr("至少需要 7 天记录"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BatteryReading(state: JingYouUiState) {
    val reading = state.dashboard?.bodyBattery
    val value = reading?.value?.takeIf { it.isFinite() && it in 0.0..100.0 }?.toFloat()
    val language = LocalAppLanguage.current
    val timestamp = remember(reading?.timestamp, language) { signalTimestamp(reading?.timestamp, language) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("身体电量"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(signalNumber(value), fontSize = 25.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            if (timestamp == null) tr("读数时间未提供") else tr("最近读数") + " · " + signalBidi(timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                .then(if (value == null) Modifier else Modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..100f) }),
        ) {
            if (value != null && value > 0f) {
                Box(Modifier.fillMaxWidth(value / 100f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(ElectricCyan))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(signalNumber(0f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (value == null) Text(tr("暂无数据"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(signalNumber(100f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(tr("身体电量是最近一次读数，不代表整夜恢复量"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SignalsDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f)))
}

private fun signalCurrent(points: List<TrendPoint>, date: String?, fallback: Double?): Float? {
    val exact = date?.let { target -> points.lastOrNull { it.date == target }?.value }
    return (exact?.takeIf { it.isFinite() && it >= 0f }
        ?: fallback?.takeIf { it.isFinite() && it >= 0.0 }?.toFloat())?.takeIf(Float::isFinite)
}

private fun signalQuantile(sorted: List<Float>, fraction: Float): Float {
    val position = (sorted.size - 1) * fraction
    val lower = floor(position).toInt()
    val upper = ceil(position).toInt()
    return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower)
}

@Composable
private fun signalNumber(value: Float?, unit: String = "", grouping: Boolean = false): String {
    if (value == null || !value.isFinite()) return "—"
    val locale = Locale.forLanguageTag(LocalAppLanguage.current.tag)
    val number = if (grouping) String.format(locale, "%,.0f", value) else String.format(locale, "%.0f", value)
    return signalBidi(number + if (unit.isBlank()) "" else " $unit")
}

@Composable
private fun signalRange(low: Float, high: Float, unit: String): String {
    val locale = Locale.forLanguageTag(LocalAppLanguage.current.tag)
    return signalBidi(String.format(locale, "%.0f–%.0f", low, high) + if (unit.isBlank()) "" else " $unit")
}

@Composable
private fun signalDate(date: String?): String {
    if (date == null) return tr("记录日期未提供")
    val locale = Locale.forLanguageTag(LocalAppLanguage.current.tag)
    val formatted = runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }.getOrDefault(date)
    return signalBidi(formatted)
}

@Composable
private fun signalBidi(text: String): String =
    BidiFormatter.getInstance(LocalAppLanguage.current.rtl).unicodeWrap(text, TextDirectionHeuristicsCompat.LTR)

private fun signalTimestamp(timestamp: String?, language: AppLanguage): String? {
    if (timestamp.isNullOrBlank()) return null
    val zone = ZoneId.systemDefault()
    val date = runCatching { OffsetDateTime.parse(timestamp).atZoneSameInstant(zone).toLocalDateTime() }.getOrNull()
        ?: runCatching { Instant.parse(timestamp).atZone(zone).toLocalDateTime() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(timestamp.replace(' ', 'T')) }.getOrNull()
        ?: return timestamp
    return date.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(Locale.forLanguageTag(language.tag)))
}
