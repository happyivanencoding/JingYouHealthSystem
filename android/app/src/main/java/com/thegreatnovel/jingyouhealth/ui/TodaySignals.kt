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
import androidx.compose.runtime.remember
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
            BatteryReading(state)
            SignalsDivider()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onSleep)
                    .heightIn(min = 56.dp).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.NightsStay, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(tr("查看这晚睡眠"), style = MaterialTheme.typography.labelLarge)
                    Text(tr("回看睡眠、压力与恢复"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun DailySignalsPanel(state: JingYouUiState, onMetric: (HealthMetric) -> Unit) {
    val daily = state.dashboard?.daily
    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tr("今日信号"), style = MaterialTheme.typography.titleMedium)
                Text(tr("和自己的平时相比"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SignalBaselineRow(
                label = tr("静息心率"),
                current = signalCurrent(state.trends.restingHr, daily?.date, daily?.restingHr),
                points = state.trends.restingHr,
                date = daily?.date,
                unit = "bpm",
                accent = Rose,
                onClick = { onMetric(HealthMetric.RHR) },
            )
            SignalsDivider()
            SignalBaselineRow(
                label = tr("压力"),
                current = signalCurrent(state.trends.stress, daily?.date, daily?.avgStress),
                points = state.trends.stress,
                date = daily?.date,
                unit = "",
                accent = Amber,
                onClick = { onMetric(HealthMetric.STRESS) },
            )
            SignalsDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("步数"), style = MaterialTheme.typography.bodyMedium)
                    Text(signalDate(daily?.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(signalNumber(daily?.steps?.takeIf { it >= 0 }?.toFloat(), grouping = true), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

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
        calendarWindow(points, previousDay, days = 28)
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
