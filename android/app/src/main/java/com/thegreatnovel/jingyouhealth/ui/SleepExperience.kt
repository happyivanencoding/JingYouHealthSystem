package com.thegreatnovel.jingyouhealth.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import com.thegreatnovel.jingyouhealth.model.*
import com.thegreatnovel.jingyouhealth.ui.components.*
import com.thegreatnovel.jingyouhealth.ui.theme.*
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

enum class HealthMetric(val label: String, val unit: String) {
    SLEEP("睡眠时长", "小时"), HRV("昨夜 HRV", "ms"), RHR("静息心率", "bpm"), STRESS("压力", "")
}

private fun HealthMetric.points(trends: Trends) = when (this) {
    HealthMetric.SLEEP -> trends.sleepHours
    HealthMetric.HRV -> trends.hrv
    HealthMetric.RHR -> trends.restingHr
    HealthMetric.STRESS -> trends.stress
}

private fun HealthMetric.accent() = when (this) {
    HealthMetric.SLEEP -> ArcticBlue
    HealthMetric.HRV -> AuroraViolet
    HealthMetric.RHR -> Rose
    HealthMetric.STRESS -> Amber
}

@Composable
private fun metricText(value: Double?, unit: String = "", decimals: Int = 0): String {
    val language = LocalAppLanguage.current
    return if (value == null || !value.isFinite()) "—" else BidiFormatter.getInstance(language.rtl).unicodeWrap(
        String.format(Locale.forLanguageTag(language.tag), "%.${decimals}f", value) + (if (unit.isBlank()) "" else " $unit"), TextDirectionHeuristicsCompat.LTR)
}

@Composable
private fun metricRange(low: Float?, high: Float?, unit: String): String {
    if (low == null || high == null) return "—"
    val language = LocalAppLanguage.current
    val raw = String.format(Locale.forLanguageTag(language.tag), "%.1f–%.1f %s", low, high, unit)
    return BidiFormatter.getInstance(language.rtl).unicodeWrap(raw, TextDirectionHeuristicsCompat.LTR)
}

private fun sleepDate(state: JingYouUiState) = state.dashboard?.sleep?.date

@Composable
fun SleepEntry(state: JingYouUiState, onOpen: () -> Unit) {
    val sleep = state.dashboard?.sleep
    PressableGlassPanel(onClick = onOpen, modifier = Modifier.fillMaxWidth(), accent = ArcticBlue,
        padding = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NightsStay, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
                Text(tr("这一晚，发生了什么"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(20.dp))
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(metricText(sleep?.sleepSeconds?.div(3600), tr("小时"), 1), fontSize = 32.sp, fontWeight = FontWeight.Medium)
                    Text(sleep?.date ?: tr("等待睡眠数据"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SleepScoreOrbit(sleep?.score)
            }
            RecentNights(state, 7, compact = true)
            Text(tr("为什么没睡好？从这里开始"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SleepDetailScreen(state: JingYouUiState, onBack: () -> Unit, onAsk: (String) -> Unit, onMetric: (HealthMetric, String?) -> Unit) {
    BackHandler(onBack = onBack)
    val sleep = state.dashboard?.sleep
    val date = sleep?.date
    val base = remember(state.trends, date) { baseline(state.trends.sleepHours, date)?.takeIf { it.sampleCount >= 7 } }
    val hours = sleep?.sleepSeconds?.div(3600)
    val prompt = tr("请复盘这晚睡眠，结合前一天压力、同夜 HRV 和静息心率，区分观测、相关性和待验证原因。") + "\n" +
        tr("睡眠记录日期") + ": " + (date ?: tr("暂无数据")) + "\n" +
        tr("睡眠时长") + ": " + metricText(hours, tr("小时"), 1) + "\n" +
        tr("睡眠评分") + ": " + metricText(sleep?.score, "/ 100") + "\n" +
        tr("平时范围") + ": " + metricRange(base?.lowerQuartile?.toFloat(), base?.upperQuartile?.toFloat(), tr("小时"))
    LazyColumn(contentPadding = detailPadding(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { DetailHeader(tr("睡眠复盘"), date ?: tr("等待睡眠数据"), onBack) }
        item {
            GlassPanel(modifier = Modifier.fillMaxWidth(), accent = ArcticBlue, padding = PaddingValues(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(tr(when {
                        sleep?.sleepSeconds == null -> "这一晚，尚待了解"
                        base != null && hours != null && hours < base.lowerQuartile -> "这一晚，比平时短一些"
                        base != null && hours != null && hours > base.upperQuartile -> "这一晚，比平时长一些"
                        base != null -> "时长接近你的平时"
                        else -> "从这一晚，了解自己"
                    }), style = MaterialTheme.typography.headlineMedium)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text(metricText(sleep?.sleepSeconds?.div(3600), tr("小时"), 1), fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.Medium)
                            Text(tr("实际睡眠"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SleepScoreOrbit(sleep?.score)
                    }
                    RecentNights(state, 7)
                    Text(tr("分数和感受不必一致。先看记录，再寻找线索。"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SectionHeading("01", tr("和自己的平时相比"), tr("色带是过去 28 天的平时范围，圆点是这次读数"))
            Spacer(Modifier.height(12.dp))
            GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ComparisonRow(tr("睡眠时长"), sleep?.sleepSeconds?.div(3600), state.trends.sleepHours, date, tr("小时"), 1) { onMetric(HealthMetric.SLEEP, date) }
                    val hrv = state.trends.hrv.firstOrNull { it.date == date }?.value?.toDouble()
                        ?: state.dashboard?.hrv?.takeIf { it.date == date }?.lastNightAvg
                    ComparisonRow(tr("同夜 HRV"), hrv, state.trends.hrv, date, "ms") { onMetric(HealthMetric.HRV, date) }
                    val rhr = state.trends.restingHr.firstOrNull { it.date == date }?.value?.toDouble()
                        ?: state.dashboard?.daily?.takeIf { it.date == date }?.restingHr
                    ComparisonRow(tr("醒来当天静息心率"), rhr, state.trends.restingHr, date, "bpm") { onMetric(HealthMetric.RHR, date) }
                    val prior = runCatching { LocalDate.parse(date).minusDays(1).toString() }.getOrNull()
                    val stress = state.trends.stress.firstOrNull { it.date == prior }?.value?.toDouble()
                    ComparisonRow(tr("前一天压力"), stress, state.trends.stress, prior, "") { onMetric(HealthMetric.STRESS, prior) }
                }
            }
        }
        item {
            SectionHeading("02", tr("睡眠的组成"), tr("阶段总量，不是整夜时间线"))
            Spacer(Modifier.height(12.dp))
            SleepComposition(sleep, state.trends)
        }
        item {
            SectionHeading("03", tr("寻找反复出现的联动"), tr("睡眠时长与其他信号的个人历史关系"))
            Spacer(Modifier.height(12.dp))
            AssociationPanel(state, date)
        }
        item {
            SectionHeading("04", tr("把感受也带进来"), tr("手表看不到的部分，可以告诉教练"))
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("睡够了，还是觉得累", "睡前发生了什么", "今晚可以试着调整什么").forEach { key ->
                    val detail = tr(key)
                    QuietAction(detail) { onAsk("$prompt\n$detail") }
                }
                PrimaryInsightAction(tr("带着这些数据问教练")) { onAsk(prompt) }
            }
        }
        item { MethodNotes() }
    }
}

@Composable
private fun SleepScoreOrbit(score: Double?) {
    val accent = MaterialTheme.colorScheme.primary
    val caption = tr("睡眠评分")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(64.dp).semantics { contentDescription = caption }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val stroke = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
                drawArc(accent.copy(alpha = 0.14f), 135f, 270f, false, style = stroke)
                score?.let { drawArc(accent, 135f, (it / 100 * 270).toFloat().coerceIn(0f, 270f), false, style = stroke) }
            }
            Text(metricText(score), style = MaterialTheme.typography.titleLarge)
        }
        Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecentNights(state: JingYouUiState, count: Int, compact: Boolean = false) {
    val date = state.dashboard?.sleep?.date
    val points = remember(state.trends.sleepHours, date) { calendarWindow(state.trends.sleepHours, date, count) }
    val base = remember(state.trends.sleepHours, date) { baseline(state.trends.sleepHours, date)?.takeIf { it.sampleCount >= 7 } }
    val caption = tr("最近 7 晚")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            NightHistoryChart(points, base?.median?.toFloat(), ArcticBlue, Modifier.fillMaxWidth().height(if (compact) 64.dp else 88.dp).semantics { contentDescription = caption }, date)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (base != null) Text(tr("虚线：平时睡眠") + " " + metricText(base.median, tr("小时"), 1), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ComparisonRow(label: String, value: Double?, points: List<TrendPoint>, date: String?, unit: String,
                          decimals: Int = 0, onClick: () -> Unit) {
    val base = remember(points, date) { baseline(points, date)?.takeIf { it.sampleCount >= 7 } }
    val history = remember(points, base) { calendarWindow(points, base?.endDate, 28).mapNotNull { it.value } }
    val accent = if (unit == "ms") AuroraViolet else ArcticBlue
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(metricText(value, unit, decimals), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val status = tr(when {
            value == null -> "暂无数据"
            base == null -> "正在积累你的平时范围"
            value < base.lowerQuartile -> "低于你平时的范围"
            value > base.upperQuartile -> "高于你平时的范围"
            else -> "在你平时的范围内"
        })
        Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BaselinePositionChart(value?.toFloat(), history, accent, Modifier.fillMaxWidth().height(42.dp).semantics { contentDescription = status })
        }
        if (base != null) Text(tr("平时范围") + " " + metricRange(base.lowerQuartile.toFloat(), base.upperQuartile.toFloat(), unit),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SleepComposition(sleep: SleepSummary?, trends: Trends) {
    val ratios = sleepStageRatios(sleep)
    val light = sleep?.lightSeconds?.takeIf { it >= 0 }?.let { sec -> sleep.sleepSeconds?.takeIf { it > 0 && sec <= it }?.let { sec / it } }
    val fractions = listOf(ratios.deep, ratios.rem, light)
    val stageSeconds = listOf(sleep?.deepSeconds, sleep?.remSeconds, sleep?.lightSeconds)
    val colors = listOf(ArcticBlue, AuroraViolet, ElectricCyan)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    fun ratioHistory(numerator: List<TrendPoint>): List<TrendPoint> {
        val totals = trends.sleepHours.associate { it.date to it.value }
        return numerator.map { item -> TrendPoint(item.date, item.value?.let { v -> totals[item.date]?.takeIf { it > 0 && v >= 0 && v <= it }?.let { v / it * 100 } }) }
    }
    val deepBase = remember(trends, sleep?.date) { baseline(ratioHistory(trends.deepHours), sleep?.date)?.takeIf { it.sampleCount >= 7 } }
    val remBase = remember(trends, sleep?.date) { baseline(ratioHistory(trends.remHours), sleep?.date)?.takeIf { it.sampleCount >= 7 } }
    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                        val stroke = Stroke(12.dp.toPx(), cap = StrokeCap.Butt)
                        drawArc(track, 0f, 360f, false, style = stroke)
                        var start = -90f
                        fractions.forEachIndexed { index, value ->
                            val sweep = ((value ?: 0.0) * 360).toFloat().coerceIn(0f, (270f - start).coerceAtLeast(0f))
                            if (sweep > 0) drawArc(colors[index], start + 1.5f, (sweep - 3f).coerceAtLeast(0f), false, style = stroke)
                            start += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(metricText(sleep?.sleepSeconds?.div(3600), decimals = 1), style = MaterialTheme.typography.headlineMedium)
                        Text(tr("小时"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    listOf(Triple("深睡", ratios.deep, deepBase), Triple("REM", ratios.rem, remBase), Triple("浅睡", light, null)).forEachIndexed { index, (label, fraction, reference) ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(colors[index]))
                                Spacer(Modifier.width(6.dp))
                                Text(tr(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(metricText(fraction?.times(100), "%"), style = MaterialTheme.typography.titleMedium)
                            }
                            Text(metricText(stageSeconds[index]?.div(3600), tr("小时"), 2), style = MaterialTheme.typography.labelLarge)
                            if (reference != null) Text(tr("平时") + " " + metricText(reference.median, "%"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            StageRow(tr("清醒"), sleep?.awakeSeconds, ratios.awake, Amber)
            Text(tr("深睡与 REM 占实际睡眠的比例；清醒占睡眠与清醒总时长的比例。缺失数据保留为空。"),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StageRow(label: String, seconds: Double?, ratio: Double?, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(metricText(seconds?.div(3600), tr("小时"), 2) + " · " + metricText(ratio?.times(100), "%", if (ratio != null && ratio > 0 && ratio < 0.01) 1 else 0), style = MaterialTheme.typography.labelLarge)
        }
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f))) {
            if (ratio != null && ratio > 0) Box(Modifier.fillMaxWidth(ratio.toFloat().coerceIn(0f, 1f)).height(5.dp).clip(CircleShape).background(accent))
        }
    }
}

@Composable
fun AssociationPanel(state: JingYouUiState, date: String?, days: Int = 90) {
    var selected by rememberSaveable { mutableIntStateOf(1) }
    var methodOpen by rememberSaveable { mutableStateOf(false) }
    val associations = listOf(
        Triple("前一天压力", state.trends.stress, -1),
        Triple("同夜 HRV", state.trends.hrv, 0),
        Triple("静息心率", state.trends.restingHr, 0),
    )
    val (label, points, offset) = associations[selected]
    val chipLabels = listOf(tr("压力"), "HRV", tr("静息心率"))
    val unit = if (selected == 1) "ms" else if (selected == 2) "bpm" else ""
    val pairs = remember(state.trends.sleepHours, points, date, days) {
        sleepMetricPairs(state.trends.sleepHours, points, date, days, offset)
    }
    val result = remember(pairs) { pairedCorrelation(state.trends.sleepHours, points, date, days, offset) }
    val insight = tr(when {
        result.coefficient == null -> "正在积累可比较的夜晚"
        kotlin.math.abs(result.coefficient!!) < 0.3 -> "这段记录里，关系还不明显"
        result.coefficient!! > 0 -> "睡得较久时，这个读数也倾向更高"
        else -> "睡得较久时，这个读数倾向更低"
    })
    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                associations.forEachIndexed { index, item ->
                    InsightChoice(chipLabels[index], index == selected, Modifier.weight(1f)) { selected = index }
                }
            }
            Text(insight, style = MaterialTheme.typography.titleLarge)
            Text(tr("每个点，都是你的一晚"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tr(label) + if (unit.isNotEmpty()) " · $unit" else "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row {
                    Column(Modifier.height(142.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text(metricText(pairs.maxOfOrNull { it.second }?.toDouble()), style = MaterialTheme.typography.labelMedium)
                        Text(metricText(pairs.minOfOrNull { it.second }?.toDouble()), style = MaterialTheme.typography.labelMedium)
                    }
                    AssociationScatterPlot(pairs, AuroraViolet, Modifier.weight(1f).height(142.dp).semantics { contentDescription = insight })
                }
                Row(Modifier.fillMaxWidth().padding(start = 28.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(metricText(pairs.minOfOrNull { it.first }?.toDouble(), tr("小时"), 1), style = MaterialTheme.typography.labelMedium)
                    Text(metricText(pairs.maxOfOrNull { it.first }?.toDouble(), tr("小时"), 1), style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(tr("睡眠时长") + " →", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tr("共同变化提供线索；原因还需要结合生活记录。"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { methodOpen = !methodOpen }, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("查看计算依据"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ExpandMore, tr(if (methodOpen) "收起" else "展开"))
            }
            AnimatedVisibility(methodOpen) {
                Text(tr("配对记录") + " ${result.sampleCount} · Pearson r = " + metricText(result.coefficient, decimals = 2) + "\n" +
                    tr("相关不等于原因。至少 14 对记录才显示 r；日期缺失不填补，同夜信号可能共享测量来源。"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MetricExplorer(state: JingYouUiState, initial: HealthMetric = HealthMetric.SLEEP, anchorDate: String? = null, onBack: (() -> Unit)? = null, onSleep: () -> Unit, onAsk: (String) -> Unit) {
    var selected by rememberSaveable(initial) { mutableStateOf(initial) }
    var days by rememberSaveable { mutableIntStateOf(30) }
    if (onBack != null) BackHandler(onBack = onBack)
    val all = selected.points(state.trends)
    val end = anchorDate ?: state.dashboard?.date ?: all.maxOfOrNull { it.date }
    val points = remember(all, end, days) { calendarWindow(all, end, days) }
    var cursor by rememberSaveable(selected, days, anchorDate) {
        mutableFloatStateOf(points.indexOfFirst { it.date == anchorDate }.takeIf { it >= 0 && points.size > 1 }?.let { it.toFloat() / points.lastIndex } ?: 1f)
    }
    val index = if (points.isEmpty()) 0 else (cursor * points.lastIndex).roundToInt().coerceIn(points.indices)
    val point = points.getOrNull(index)
    val reference = remember(all, point?.date) { baseline(all, point?.date)?.takeIf { it.sampleCount >= 7 } }
    val domainValues = points.mapNotNull { it.value } + listOfNotNull(reference?.lowerQuartile?.toFloat(), reference?.upperQuartile?.toFloat())
    val chartLow = domainValues.minOrNull() ?: 0f
    val chartHigh = domainValues.maxOrNull() ?: 1f
    val unit = tr(selected.unit)
    LazyColumn(contentPadding = if (onBack != null) detailPadding() else screenPadding(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            if (onBack != null) DetailHeader(tr("指标探索"), tr("从趋势走向理解"), onBack)
            else Column(Modifier.fillMaxWidth().padding(end = 48.dp)) {
                Text(tr("趋势"), style = MaterialTheme.typography.headlineLarge)
                Text(tr("从趋势走向理解"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HealthMetric.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { metric -> InsightChoice(tr(metric.label), metric == selected, Modifier.weight(1f)) { selected = metric } }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 90).forEach { length -> InsightChoice("$length " + tr("天"), days == length, Modifier.weight(1f)) { days = length } }
            }
        }
        item {
            GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(22.dp), accent = selected.accent()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr(selected.label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(metricText(point?.value?.toDouble(), unit, if (selected == HealthMetric.SLEEP) 1 else 0), fontSize = 42.sp, fontWeight = FontWeight.Medium)
                    Text(point?.date ?: tr("暂无数据"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val cursorLabel = (point?.date ?: tr("暂无数据")) + " · " + metricText(point?.value?.toDouble(), unit, 1)
                    val chartLabel = tr(selected.label)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        val accent = selected.accent()
                        Box(Modifier.fillMaxWidth().height(128.dp)) {
                            Canvas(Modifier.fillMaxSize()) {
                                reference?.let { base ->
                                    fun y(value: Double): Float = size.height * (0.86f - (if (chartHigh > chartLow) ((value.toFloat() - chartLow) / (chartHigh - chartLow)) else 0.5f) * 0.72f)
                                    val top = y(base.upperQuartile)
                                    val bottom = y(base.lowerQuartile)
                                    drawRoundRect(accent.copy(alpha = 0.10f), topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(size.width, (bottom - top).coerceAtLeast(3.dp.toPx())))
                                    drawLine(accent.copy(alpha = 0.40f), Offset(0f, y(base.median)), Offset(size.width, y(base.median)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())))
                                }
                            }
                            Sparkline(points.map { it.value }, Modifier.fillMaxSize(), accent, domain = chartLow..chartHigh)
                            Canvas(Modifier.fillMaxSize()) {
                                if (points.isNotEmpty()) {
                                    val inset = 2.dp.toPx()
                                    val x = inset + (size.width - inset * 2) * index / points.lastIndex.coerceAtLeast(1)
                                    drawLine(accent.copy(alpha = 0.32f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                                    val values = points.mapNotNull { it.value?.takeIf(Float::isFinite) }
                                    point?.value?.let { value ->
                                        val low = chartLow
                                        val high = chartHigh
                                        val normalized = if (high - low > 0.001f) (value - low) / (high - low) else 0.5f
                                        drawCircle(accent, 5.dp.toPx(), Offset(x, size.height * (0.86f - normalized * 0.72f)))
                                    }
                                }
                            }
                        }
                        if (points.size > 1) {
                            Slider(value = cursor, onValueChange = { cursor = it }, steps = (points.size - 2).coerceAtLeast(0),
                                modifier = Modifier.semantics { contentDescription = chartLabel; stateDescription = cursorLabel },
                                thumb = { Box(Modifier.size(16.dp).clip(CircleShape).background(accent)) },
                                track = { slider ->
                                    Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f))) {
                                        Box(Modifier.fillMaxWidth(slider.value.coerceIn(0f, 1f)).height(3.dp).background(accent))
                                    }
                                },
                                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(points.first().date.takeLast(5), style = MaterialTheme.typography.labelMedium)
                                Text(points.last().date.takeLast(5), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Text(tr("滑动查看每一天"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (reference != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(selected.accent().copy(alpha = 0.23f)))
                            Spacer(Modifier.width(7.dp))
                            Text(tr("你的平时范围") + " " + metricRange(reference.lowerQuartile.toFloat(), reference.upperQuartile.toFloat(), unit), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val values = points.mapNotNull { it.value?.takeIf(Float::isFinite) }
                    Text(tr("有效记录") + " ${values.size}/$days · " + tr("范围") + " " +
                        metricRange(values.minOrNull(), values.maxOrNull(), unit),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            if (selected == HealthMetric.SLEEP) QuietAction(tr("打开最近一晚复盘"), onSleep)
            val title = tr(selected.label)
            val question = tr("请解释这个指标的近期变化、计算方法及与睡眠的关系。")
            QuietAction(tr("问教练这个变化意味着什么")) { onAsk("$question\n$title · ${point?.date.orEmpty()}") }
        }
        item { SectionHeading("↗", tr("睡眠与其他信号"), "$days " + tr("天") + " · " + tr("按日期配对")) }
        item { AssociationPanel(state, end, days) }
        item { MethodNotes() }
    }
}

private fun calendarWindow(points: List<TrendPoint>, end: String?, days: Int): List<TrendPoint> {
    val date = runCatching { LocalDate.parse(end) }.getOrNull() ?: return emptyList()
    val map = points.associate { it.date to it.value }
    return (days - 1 downTo 0).map { offset -> date.minusDays(offset.toLong()).toString().let { TrendPoint(it, map[it]?.takeIf(Float::isFinite)) } }
}

@Composable
private fun MethodNotes() {
    var open by rememberSaveable { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("方法与数据边界"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ExpandMore, tr(if (open) "收起" else "展开"))
            }
            AnimatedVisibility(open) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(tr("个人基线：取记录日期前 28 个日历日的有效值中位数，至少 7 条才比较。它不是 Garmin HRV 基线或医学阈值。"), style = MaterialTheme.typography.bodyMedium)
                    Text(tr("色带覆盖此前 28 天中间 50% 的有效读数，不是医学正常范围；竖线为中位数。"), style = MaterialTheme.typography.bodyMedium)
                    Text(tr("Pearson r：在所选窗口按日期配对，计算 cov(x,y)/(σx·σy)。范围 −1 到 1；零方差或少于 14 对时不显示。0.3 仅作弱线性关系的描述界线，不代表显著性。"), style = MaterialTheme.typography.bodyMedium)
                    Text(tr("睡眠日期沿用 Garmin 记录日期。压力取前一天；HRV 与静息心率取同一记录日期。不同日期的数据不会混用，也不推算缺失值。"), style = MaterialTheme.typography.bodyMedium)
                    Text(tr("睡眠阶段是设备根据心率、HRV 与活动估计的结果。这些信号可能并不独立，不能据此确定没睡好的原因。"), style = MaterialTheme.typography.bodyMedium)
                    QuietAction(tr("Garmin：睡眠测量方法")) { uri.openUri("https://www.garmin.com/en-US/blog/fitness/how-garmin-watches-track-your-sleep-calculate-sleep-score/") }
                    QuietAction(tr("Garmin：HRV 与个人基线")) { uri.openUri("https://www.garmin.com/en-IE/garmin-technology/health-science/hrv-status/") }
                }
            }
        }
    }
}

@Composable
fun DetailHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("返回"))
        }
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun QuietAction(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = onClick)
        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.42f)).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun PrimaryInsightAction(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(20.dp)) {
        Text(label, modifier = Modifier.padding(vertical = 6.dp))
    }
}

@Composable
fun InsightChoice(label: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.semantics { selected = isSelected }.clip(RoundedCornerShape(16.dp))
        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
        .clickable(role = Role.Tab, onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SectionHeading(number: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(number, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun detailPadding() = PaddingValues(start = 20.dp, end = 20.dp,
    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 28.dp)

@Composable
private fun screenPadding() = PaddingValues(start = 18.dp, end = 18.dp,
    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 34.dp,
    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp)
