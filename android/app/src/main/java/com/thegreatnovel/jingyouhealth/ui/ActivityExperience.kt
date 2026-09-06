package com.thegreatnovel.jingyouhealth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.BidiFormatter
import com.thegreatnovel.jingyouhealth.model.ActivitySummary
import com.thegreatnovel.jingyouhealth.model.Trends
import com.thegreatnovel.jingyouhealth.ui.components.CardShape
import com.thegreatnovel.jingyouhealth.ui.components.GlassPanel
import com.thegreatnovel.jingyouhealth.ui.components.PressableGlassPanel
import com.thegreatnovel.jingyouhealth.ui.components.StatusPill
import com.thegreatnovel.jingyouhealth.ui.theme.ArcticBlue
import com.thegreatnovel.jingyouhealth.ui.theme.AuroraViolet
import com.thegreatnovel.jingyouhealth.ui.theme.ElectricCyan
import com.thegreatnovel.jingyouhealth.ui.theme.Rose
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.math.floor

private const val WINDOW_DAYS = 28
private const val CATEGORY_EASY = "easy_aerobic"
private const val CATEGORY_HARD = "hard_aerobic"
private const val CATEGORY_ANAEROBIC = "anaerobic"
private const val CATEGORY_STRENGTH = "strength"
private const val CATEGORY_ALL = "all"
private const val CATEGORY_AUTO = "__auto__"

private val activityCategories = listOf(CATEGORY_EASY, CATEGORY_HARD, CATEGORY_ANAEROBIC, CATEGORY_STRENGTH)
private val activityDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val EasyAerobicColor = ElectricCyan
private val HardAerobicColor = Color(0xFF4D6CC5)
private val AnaerobicColor = Color(0xFFE38B89)
private val StrengthColor = Color(0xFFB8A1E3)

internal fun categoryColor(category: String): Color = when (category) {
    CATEGORY_HARD -> HardAerobicColor
    CATEGORY_ANAEROBIC -> AnaerobicColor
    CATEGORY_STRENGTH -> StrengthColor
    else -> EasyAerobicColor
}

@Composable
internal fun categoryLabel(category: String): String = when (category) {
    CATEGORY_HARD -> tr("高强度有氧")
    CATEGORY_ANAEROBIC -> tr("无氧")
    CATEGORY_STRENGTH -> tr("力量训练")
    else -> tr("低强度有氧")
}

internal fun activityLocalDate(activity: ActivitySummary): LocalDate? {
    val raw = activity.startTime?.trim()?.takeIf { it.length >= 10 } ?: return null
    return runCatching { LocalDate.parse(raw.substring(0, 10), activityDateFormatter) }.getOrNull()
}

private fun parseActivityDate(value: String?): LocalDate? = value?.let {
    runCatching { LocalDate.parse(it.take(10), activityDateFormatter) }.getOrNull()
}

private fun trendDates(trends: Trends): List<LocalDate> = listOf(
    trends.hrv, trends.restingHr, trends.sleepHours, trends.stress, trends.sleepScores,
    trends.deepHours, trends.remHours, trends.awakeHours, trends.bodyBatteryCharged,
    trends.bodyBatteryDrained, trends.steps, trends.lightHours, trends.readiness,
).flatMap { points -> points.mapNotNull { parseActivityDate(it.date) } } +
    trends.sleepClocks.mapNotNull { parseActivityDate(it.date) }

private fun knownHistoryDates(state: JingYouUiState): List<LocalDate> = buildList {
    state.dashboard?.date?.let(::parseActivityDate)?.let(::add)
    state.activities.mapNotNull(::activityLocalDate).forEach(::add)
    trendDates(state.trends).forEach(::add)
}.distinct().sorted()

private fun calendarRange(start: LocalDate, end: LocalDate): List<LocalDate> {
    val days = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt().coerceAtLeast(0)
    return (0..days).map { start.plusDays(it.toLong()) }
}

private fun inferCategory(activity: ActivitySummary): String {
    val text = "${activity.type} ${activity.name}".lowercase()
    return when {
        listOf("strength", "weight", "weightlifting", "gym", "lift", "musculation", "力量", "健身").any(text::contains) -> CATEGORY_STRENGTH
        listOf("anaerobic", "sprint", "hiit", "interval", "tabata", "冲刺", "间歇").any(text::contains) -> CATEGORY_ANAEROBIC
        activity.trainingEffect?.let { it >= 3.8 } == true || activity.avgHr?.let { it >= 155 } == true -> CATEGORY_HARD
        else -> CATEGORY_EASY
    }
}

internal fun effectiveCategory(activity: ActivitySummary): String =
    (activity.category ?: activity.categoryOverride ?: inferCategory(activity)).lowercase()
        .takeIf { it in activityCategories } ?: inferCategory(activity)

private fun estimatedRpe(category: String): Double = when (category) {
    CATEGORY_HARD -> 6.0
    CATEGORY_ANAEROBIC -> 8.0
    CATEGORY_STRENGTH -> 6.0
    else -> 3.0
}

internal fun internalLoadValue(activity: ActivitySummary): Double? {
    activity.internalLoad?.takeIf { it.isFinite() && it >= 0.0 }?.let { return it }
    val minutes = activity.durationS?.div(60.0)?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
    val rpe = activity.effortRpe?.takeIf { it.isFinite() && it >= 0.0 } ?: estimatedRpe(effectiveCategory(activity))
    return minutes * rpe
}

internal fun isEstimated(activity: ActivitySummary): Boolean =
    activity.effortSource?.lowercase() != "reported" || activity.effortRpe == null

private fun categoryActivities(activities: List<ActivitySummary>, category: String?): List<ActivitySummary> =
    activities.filter { category == null || effectiveCategory(it) == category }

private fun LocalDate.pickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun pickerDate(millis: Long?): LocalDate? = millis?.let {
    Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
}

private fun Double.roundToIntSafe(): Int = kotlin.math.round(this).toInt().coerceAtLeast(0)
private fun formatAu(value: Double?): String = value?.takeIf { it.isFinite() }?.let { "${it.roundToIntSafe()} AU" } ?: "—"
private fun formatRpe(value: Double?): String = value?.takeIf { it.isFinite() }?.let { "RPE ${"%.1f".format(it)}" } ?: ""

@Composable
private fun activityBidi(value: String): String =
    if (LocalAppLanguage.current.rtl) BidiFormatter.getInstance(true).unicodeWrap(value) else value

@Composable
fun ActivityExperience(state: JingYouUiState, onMethod: () -> Unit = {}, onActivity: (ActivitySummary) -> Unit) {
    val activities = state.activities
    val knownDates = remember(state.dashboard?.date, state.trends, activities) { knownHistoryDates(state) }
    val latestRecordDate = knownDates.maxOrNull()
    val backendEndDate = parseActivityDate(state.dashboard?.date)
    val maxEndDate = maxOf(backendEndDate ?: latestRecordDate ?: LocalDate.now(), latestRecordDate ?: backendEndDate ?: LocalDate.now())
    val defaultEndDate = backendEndDate ?: latestRecordDate ?: maxEndDate
    val historyStart = knownDates.minOrNull() ?: defaultEndDate
    val timelineStart = historyStart.minusDays((WINDOW_DAYS - 1).toLong())
    val allDates = remember(timelineStart, maxEndDate) { calendarRange(timelineStart, maxEndDate) }
    val initialStartDate = maxOf(timelineStart, defaultEndDate.minusDays((WINDOW_DAYS - 1).toLong()))
    val initialIndex = allDates.indexOf(initialStartDate).coerceAtLeast(0)
    val maxStartIndex = (allDates.size - WINDOW_DAYS).coerceAtLeast(0)
    val timelineState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var windowStartIndex by remember { mutableStateOf(initialIndex) }
    var categoryFilter by rememberSaveable { mutableStateOf(CATEGORY_ALL) }
    var selectedDayText by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var loadWindowDays by rememberSaveable { mutableStateOf(0) }
    val selectedCategory = categoryFilter.takeUnless { it == CATEGORY_ALL }

    LaunchedEffect(allDates, initialIndex) {
        windowStartIndex = initialIndex.coerceAtMost(maxStartIndex)
        timelineState.scrollToItem(windowStartIndex)
    }
    LaunchedEffect(timelineState, allDates, maxStartIndex) {
        snapshotFlow { timelineState.firstVisibleItemIndex }.distinctUntilChanged().collect { index ->
            val clamped = index.coerceIn(0, maxStartIndex)
            if (index != clamped) timelineState.scrollToItem(clamped)
            windowStartIndex = clamped
        }
    }

    val windowDates = remember(allDates, windowStartIndex) {
        if (allDates.isEmpty()) emptyList() else allDates.drop(windowStartIndex.coerceIn(0, allDates.lastIndex)).take(WINDOW_DAYS)
    }
    val windowEndDate = windowDates.lastOrNull() ?: defaultEndDate
    val selectedDay = parseActivityDate(selectedDayText)?.takeIf { it in windowDates }
    LaunchedEffect(windowStartIndex, windowDates) {
        if (selectedDayText != null && parseActivityDate(selectedDayText) !in windowDates) selectedDayText = null
    }
    val windowActivities = remember(activities, windowDates, selectedCategory) {
        val start = windowDates.firstOrNull()
        val end = windowDates.lastOrNull()
        categoryActivities(activities, selectedCategory).filter { activity ->
            val date = activityLocalDate(activity)
            date != null && start != null && end != null && date >= start && date <= end
        }.sortedWith(compareByDescending<ActivitySummary> { activityLocalDate(it) }.thenByDescending { it.startTime.orEmpty() })
    }
    val visibleActivities = remember(windowActivities, selectedDay) {
        selectedDay?.let { day -> windowActivities.filter { activityLocalDate(it) == day } } ?: windowActivities
    }
    val estimatedCount = windowActivities.count(::isEstimated)
    val totalSeconds = windowActivities.mapNotNull { it.durationS?.takeIf(Double::isFinite) }.sum()
    val totalLoad = windowActivities.mapNotNull(::internalLoadValue).sum()
    val chartActivities = categoryActivities(activities, selectedCategory)
    val dailyLoads = remember(chartActivities, allDates) {
        allDates.associateWith { day ->
            chartActivities.filter { activityLocalDate(it) == day }.groupingBy(::effectiveCategory)
                .fold(0.0) { total, activity -> total + (internalLoadValue(activity) ?: 0.0) }
        }
    }
    val allLoadMax = dailyLoads.values.maxOfOrNull { it.values.sum() } ?: 0.0

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = screenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ActivityHeader(windowEndDate, windowDates) { pickerOpen = true } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(0 to "每日负荷", 7 to "7天滚动", 28 to "28天滚动").forEach { (days, label) ->
                    InsightChoice(tr(label), loadWindowDays == days, Modifier.weight(1f)) {
                        loadWindowDays = days
                        scope.launch { timelineState.scrollToItem(windowStartIndex) }
                    }
                }
            }
        }
        item {
            if (loadWindowDays == 0) ActivityLoadTimeline(allDates, windowStartIndex, selectedDay, timelineState, dailyLoads, allLoadMax) { selectedDayText = it.toString() }
            else RollingTrainingLoadPanel(state.trends.trainingHistory, allDates, windowStartIndex, selectedDay, timelineState, selectedCategory, loadWindowDays,
                { selectedDayText = it.toString() }, onMethod)
        }
        item { ActivityFilterRow(categoryFilter) { categoryFilter = it; selectedDayText = null } }
        item { ActivitySummaryPanel(windowActivities.size, totalSeconds, totalLoad, estimatedCount) }
        if (selectedDay != null) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${tr("当天活动")} · ${activityBidi(selectedDay.toString())}", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { selectedDayText = null }) { Text(tr("查看整个时段")) }
                }
            }
        }
        if (visibleActivities.isEmpty()) item { ActivityEmptyState(activities.isNotEmpty(), selectedDay != null) }
        else items(visibleActivities, key = { it.id }) { activity -> ActivityExperienceCard(activity) { onActivity(activity) } }
    }

    if (pickerOpen) {
        ActivityCalendarSheet(activities, windowEndDate, historyStart, maxEndDate, { selected ->
                val bounded = when { selected < historyStart -> historyStart; selected > maxEndDate -> maxEndDate; else -> selected }
                val targetStart = maxOf(timelineStart, bounded.minusDays((WINDOW_DAYS - 1).toLong()))
                val index = allDates.indexOf(targetStart).coerceIn(0, maxStartIndex)
                selectedDayText = null
                scope.launch { timelineState.animateScrollToItem(index) }
            pickerOpen = false
        }, onActivity, { pickerOpen = false })
    }
}

@Composable
private fun ActivityHeader(endDate: LocalDate, dates: List<LocalDate>, onCalendar: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tr("活动").ifBlank { tr("运动") }, style = MaterialTheme.typography.headlineLarge)
                Text(tr("滚动四周"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(tr("左右滑动查看"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val endDateLabel = tr("选择结束日期")
            IconButton(onClick = onCalendar, modifier = Modifier.size(48.dp).semantics { contentDescription = endDateLabel }) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
            }
        }
        GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(14.dp), accent = AuroraViolet) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("近四周累计"), style = MaterialTheme.typography.titleMedium)
                    Text(dateRangeLabel(dates), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${tr("结束日")} ${activityBidi(endDate.toString())}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun dateRangeLabel(dates: List<LocalDate>): String {
    val first = dates.firstOrNull() ?: return ""
    val last = dates.lastOrNull() ?: first
    return "${first.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))} – ${last.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))}"
}

@Composable
private fun ActivityLoadTimeline(
    allDates: List<LocalDate>,
    windowStartIndex: Int,
    selectedDay: LocalDate?,
    timelineState: androidx.compose.foundation.lazy.LazyListState,
    dailyLoads: Map<LocalDate, Map<String, Double>>,
    maxLoad: Double,
    onDayClick: (LocalDate) -> Unit,
) {
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val markerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)
    val visibleDates = allDates.drop(windowStartIndex.coerceIn(0, allDates.lastIndex.coerceAtLeast(0))).take(WINDOW_DAYS)
    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 10.dp, vertical = 14.dp), accent = HardAerobicColor) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("内部负荷趋势"), style = MaterialTheme.typography.titleMedium)
                    Text(tr("左右滑动查看"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(dateRangeLabel(visibleDates), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                activityCategories.forEach { category -> LegendDot(category); Spacer(Modifier.width(6.dp)) }
            }
            if (allDates.size < WINDOW_DAYS) {
                Text(tr("历史不足四周"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val maxWidthPx = with(density) { maxWidth.toPx().roundToInt() }
                val slotWidthPx = (maxWidthPx / WINDOW_DAYS).coerceAtLeast(1)
                val slotWidth = with(density) { slotWidthPx.toDp() }
                val timelineWidth = with(density) { (slotWidthPx * WINDOW_DAYS).toDp() }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(Modifier.width(timelineWidth)) {
                        LazyRow(
                            state = timelineState,
                            modifier = Modifier.fillMaxWidth().height(158.dp),
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            itemsIndexed(allDates, key = { _, date -> date.toString() }) { index, date ->
                            val values = dailyLoads[date].orEmpty()
                            val total = values.values.sum()
                            Column(
                                modifier = Modifier.width(slotWidth).height(158.dp).semantics { contentDescription = "$date · ${formatAu(total)}" }.clickable(role = Role.Button) { onDayClick(date) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Canvas(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 2.dp, vertical = 2.dp)) {
                                    val barWidth = size.width.coerceAtLeast(2.dp.toPx())
                                    val bottom = size.height - 4.dp.toPx()
                                    val top = 4.dp.toPx()
                                    val barHeight = (bottom - top).coerceAtLeast(1f)
                                    if (total <= 0.0 || maxLoad <= 0.0) {
                                        drawLine(axisColor, androidx.compose.ui.geometry.Offset(0f, bottom), androidx.compose.ui.geometry.Offset(size.width, bottom), 1.dp.toPx(), StrokeCap.Round)
                                    } else {
                                        var y = bottom
                                        activityCategories.forEach { category ->
                                            val amount = values[category] ?: 0.0
                                            if (amount > 0.0) {
                                                val partHeight = barHeight * (amount / maxLoad).toFloat()
                                                y -= partHeight
                                                drawRoundRect(categoryColor(category), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Size(barWidth, partHeight), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()))
                                            }
                                        }
                                    }
                                    if (date == selectedDay) drawRoundRect(markerColor, androidx.compose.ui.geometry.Offset(-2.dp.toPx(), top - 2.dp.toPx()), androidx.compose.ui.geometry.Size(size.width + 4.dp.toPx(), barHeight + 4.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                                }
                                Box(Modifier.size(if (date == selectedDay) 10.dp else 6.dp).clip(CircleShape).background(if (date == selectedDay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)))
                            }
                            }
                        }
                        Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                            for (offset in listOf(0, 7, 14, 21)) {
                                Text(visibleDates.getOrNull(offset)?.format(DateTimeFormatter.ofPattern("MM/dd")).orEmpty(),
                                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                        }
                    }
                }
            }
            Text(tr("按日累加 AU"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ActivityLegend()
        }
    }
}

@Composable
private fun ActivityFilterRow(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected == CATEGORY_ALL, onClick = { onSelect(CATEGORY_ALL) }, label = { Text(tr("全部")) }, leadingIcon = { Icon(Icons.Rounded.FilterList, contentDescription = null, modifier = Modifier.size(18.dp)) }, modifier = Modifier.heightIn(min = 48.dp))
        activityCategories.forEach { category -> FilterChip(selected = selected == category, onClick = { onSelect(category) }, label = { Text(categoryLabel(category)) }, modifier = Modifier.heightIn(min = 48.dp)) }
    }
}

@Composable
private fun ActivitySummaryPanel(count: Int, seconds: Double, internalLoad: Double, estimatedCount: Int) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(16.dp), accent = ElectricCyan) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Tune, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr("近四周累计"), style = MaterialTheme.typography.titleMedium) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryMetric(count.toString(), tr("活动次数"), Modifier.weight(0.65f))
                SummaryMetric(durationText(seconds / 3600.0), tr("运动时长"), Modifier.weight(1.45f))
                SummaryMetric(formatAu(internalLoad), tr("内部负荷"), Modifier.weight(1f))
            }
            if (estimatedCount > 0) Text("${tr("其中估算")} ${activityBidi(estimatedCount.toString())}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SummaryMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(activityBidi(value), style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false); Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun LegendDot(category: String) { val label = categoryLabel(category); Box(Modifier.size(8.dp).clip(CircleShape).background(categoryColor(category)).semantics { contentDescription = label }) }

@Composable
private fun ActivityLegend() { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) { activityCategories.forEach { category -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { LegendDot(category); Text(categoryLabel(category), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }

@Composable
private fun ActivityExperienceCard(activity: ActivitySummary, onClick: () -> Unit) {
    val category = effectiveCategory(activity); val color = categoryColor(category)
    PressableGlassPanel(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 86.dp), shape = CardShape, padding = PaddingValues(14.dp), accent = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(58.dp).clip(RoundedCornerShape(4.dp)).background(color)); Spacer(Modifier.width(12.dp))
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Icon(categoryIcon(category), contentDescription = categoryLabel(category), tint = color, modifier = Modifier.size(21.dp)) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(activity.name.ifBlank { activity.type.ifBlank { tr("运动记录") } }, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(activityBidi(activityLocalDate(activity)?.toString() ?: tr("日期未知")), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) { Text(activityBidi(durationText(activity.durationS?.div(3600.0))), style = MaterialTheme.typography.labelMedium); Text(activityBidi(formatAu(internalLoadValue(activity))), style = MaterialTheme.typography.labelMedium); activity.avgHr?.let { hr -> Text(activityBidi("${hr.roundToIntSafe()} bpm"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) { StatusPill(if (isEstimated(activity)) tr("估算") else tr("自评"), color); if (activity.effortRpe != null) Text(activityBidi(formatRpe(activity.effortRpe)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) else Text(tr("待自评"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun categoryIcon(category: String) = when (category) { CATEGORY_STRENGTH -> Icons.Rounded.FitnessCenter; CATEGORY_ANAEROBIC -> Icons.Rounded.FlashOn; CATEGORY_HARD -> Icons.Rounded.DirectionsRun; else -> Icons.Rounded.Favorite }

@Composable
private fun ActivityEmptyState(hasAnyActivities: Boolean, focusedDay: Boolean) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(22.dp), accent = ArcticBlue) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.AccessTime, contentDescription = null, tint = ArcticBlue, modifier = Modifier.size(25.dp)); Text(if (focusedDay) tr("当天没有活动") else tr("该时段没有活动"), style = MaterialTheme.typography.titleMedium); Text(if (hasAnyActivities) tr("换一个日期或筛选条件看看") else tr("同步健康档案后，这里会显示真实活动记录"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityCalendarDialog(selectedDate: LocalDate, minimumDate: LocalDate, maximumDate: LocalDate, onDismiss: () -> Unit, onDateSelected: (LocalDate?) -> Unit) {
    val pickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = selectedDate.pickerMillis())
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = { val selected = pickerDate(pickerState.selectedDateMillis); onDateSelected(selected?.let { if (it < minimumDate) minimumDate else if (it > maximumDate) maximumDate else it }) }) { Text(tr("确定")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消")) } }) { DatePicker(state = pickerState, title = { Text(tr("选择结束日期"), Modifier.padding(start = 24.dp, top = 16.dp)) }) }
}

/** Public editor for the activity detail sheet owned by root. */
@Composable
fun EffortEditor(activity: ActivitySummary, onSave: (Double?, String?) -> Unit, busy: Boolean = false) {
    var hasDraftRpe by remember(activity.id) { mutableStateOf(activity.effortRpe != null) }
    var draftRpe by remember(activity.id) { mutableStateOf(activity.effortRpe?.toFloat() ?: 5f) }
    var selectedOverride by remember(activity.id) { mutableStateOf(activity.categoryOverride ?: CATEGORY_AUTO) }
    val selectedCategory = selectedOverride.takeUnless { it == CATEGORY_AUTO }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(tr("主观用力程度"), style = MaterialTheme.typography.titleMedium)
        if (!hasDraftRpe) Text(tr("未自评，当前为估算"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) else Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(activityBidi("%.1f".format(draftRpe)), style = MaterialTheme.typography.displaySmall); Text("/ 10", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Slider(value = draftRpe, onValueChange = { draftRpe = it; hasDraftRpe = true }, valueRange = 0f..10f, steps = 9, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
        Text(tr("分类"), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selectedOverride == CATEGORY_AUTO, onClick = { selectedOverride = CATEGORY_AUTO }, label = { Text(tr("自动识别")) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp))
            activityCategories.forEach { category -> FilterChip(selected = selectedCategory == category, onClick = { selectedOverride = category }, label = { Text(categoryLabel(category)) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) }
        }
        if (activity.effortRpe != null || hasDraftRpe) TextButton(enabled = !busy, onClick = { hasDraftRpe = false; onSave(null, selectedCategory) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("清除自评"), color = Rose) }
        Button(onClick = { onSave(if (hasDraftRpe) draftRpe.toDouble() else null, selectedCategory) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (busy) tr("保存中") else tr("保存自评")) }
    }
}
