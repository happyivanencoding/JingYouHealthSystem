package com.thegreatnovel.jingyouhealth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import com.thegreatnovel.jingyouhealth.model.*
import com.thegreatnovel.jingyouhealth.ui.components.GlassPanel
import com.thegreatnovel.jingyouhealth.ui.theme.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun SleepTimingPanel(trends: Trends, date: String?, onExplore: ((SleepOutcome) -> Unit)?) {
    val summary = remember(trends, date) { sleepTimingSummary(trends, date) }
    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(20.dp), accent = ArcticBlue) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(tr("晚睡，醒来会一起推迟吗？"), style = MaterialTheme.typography.titleLarge)
            if (summary.nights.size < 7) {
                Text(tr("需要更多明确的当地入睡与醒来记录，才能查看这个作息窗口。"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("通常入睡"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(clockLabel(summary.usualBedtime), style = MaterialTheme.typography.headlineMedium)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(tr("通常醒来"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(clockLabel(summary.usualWake), style = MaterialTheme.typography.headlineMedium)
                    }
                }
                val nights = summary.nights.takeLast(7)
                val start = floor(nights.minOf { it.bedtimeHour }) - 0.5
                val end = ceil(nights.maxOf { it.wakeHour }) + 0.5
                val ink = MaterialTheme.colorScheme.onSurfaceVariant
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        nights.forEach { night ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(night.date.takeLast(5), style = MaterialTheme.typography.labelMedium, color = ink, modifier = Modifier.width(44.dp))
                                Canvas(Modifier.weight(1f).height(22.dp)) {
                                    fun x(hour: Double) = ((hour - start) / (end - start)).toFloat() * (size.width - 8.dp.toPx()) + 4.dp.toPx()
                                    drawLine(ArcticBlue.copy(alpha = if (night == nights.last()) 0.88f else 0.30f), Offset(x(night.bedtimeHour), size.height / 2), Offset(x(night.wakeHour), size.height / 2), 10.dp.toPx(), cap = StrokeCap.Round)
                                    summary.usualWake?.let { wake -> drawLine(ink.copy(alpha = 0.65f), Offset(x(wake), 0f), Offset(x(wake), size.height), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))) }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(start = 44.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(clockLabel(start), style = MaterialTheme.typography.labelMedium)
                            Text(clockLabel(end), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Text(tr("每条是入睡到醒来的记录窗口，虚线是通常醒来的时刻。"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (summary.lateCount > 0) {
                    Text(tr("把最晚入睡的四分之一夜晚单独看"), style = MaterialTheme.typography.titleMedium)
                    Text(tr("这些夜晚的典型入睡时间更晚") + " " + durationText(summary.bedtimeShift) + " · " +
                        tr(if ((summary.wakeShift ?: 0.0) >= 0) "醒来更晚" else "醒来更早") + " " + durationText(summary.wakeShift?.let { kotlin.math.abs(it) }),
                        style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(tr("其他夜晚") + " / " + tr("较晚入睡"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TimingCompare(tr("睡眠时长"), summary.otherSleep, summary.lateSleep, ArcticBlue)
                    TimingCompare(tr("深睡"), summary.otherDeep, summary.lateDeep, ElectricCyan)
                    TimingCompare("REM", summary.otherRem, summary.lateRem, AuroraViolet)
                    Text("${summary.otherCount} / ${summary.lateCount} " + tr("晚") + " · " + tr("比较的是各组中位数，不是单独改变睡觉时间的实验。"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(tr("时长和占比要一起看：少睡可能缩短某个阶段，但不一定改变它的占比。"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onExplore != null) {
                    QuietAction(tr("探索深睡的因素")) { onExplore(SleepOutcome.DEEP_HOURS) }
                    QuietAction(tr("探索 REM 的因素")) { onExplore(SleepOutcome.REM_HOURS) }
                }
            }
        }
    }
}

@Composable
private fun TimingCompare(label: String, other: Double?, late: Double?, color: Color) {
    val max = maxOf(other ?: 0.0, late ?: 0.0).coerceAtLeast(0.001)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(durationText(other) + " / " + durationText(late), style = MaterialTheme.typography.labelLarge)
        }
        listOf(other to 0.28f, late to 0.82f).forEach { (value, alpha) ->
            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(color.copy(alpha = 0.07f))) {
                if (value != null && value >= 0) Box(Modifier.fillMaxWidth((value / max).toFloat().coerceIn(0f, 1f)).height(5.dp).clip(CircleShape).background(color.copy(alpha = alpha)))
            }
        }
    }
}

@Composable
private fun clockLabel(hour: Double?): String {
    if (hour == null) return "—"
    val minute = ((hour * 60).roundToInt() % 1440 + 1440) % 1440
    val value = "%02d:%02d".format(minute / 60, minute % 60)
    return BidiFormatter.getInstance(LocalAppLanguage.current.rtl).unicodeWrap(value, TextDirectionHeuristicsCompat.LTR)
}
