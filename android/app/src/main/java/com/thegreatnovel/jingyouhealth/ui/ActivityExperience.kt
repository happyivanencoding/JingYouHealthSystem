package com.thegreatnovel.jingyouhealth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Favorite
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.BidiFormatter
import com.thegreatnovel.jingyouhealth.model.ActivitySummary
import com.thegreatnovel.jingyouhealth.model.AppLanguage
import com.thegreatnovel.jingyouhealth.ui.components.CardShape
import com.thegreatnovel.jingyouhealth.ui.components.GlassPanel
import com.thegreatnovel.jingyouhealth.ui.components.PressableGlassPanel
import com.thegreatnovel.jingyouhealth.ui.components.StatusPill
import com.thegreatnovel.jingyouhealth.ui.theme.ArcticBlue
import com.thegreatnovel.jingyouhealth.ui.theme.AuroraViolet
import com.thegreatnovel.jingyouhealth.ui.theme.ElectricCyan
import com.thegreatnovel.jingyouhealth.ui.theme.Rose
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.floor

private enum class ActivityPeriod { WEEK, MONTH }

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

private fun categoryColor(category: String): Color = when (category) {
    CATEGORY_HARD -> HardAerobicColor
    CATEGORY_ANAEROBIC -> AnaerobicColor
    CATEGORY_STRENGTH -> StrengthColor
    else -> EasyAerobicColor
}

@Composable
private fun categoryLabel(category: String): String = when (category) {
    CATEGORY_HARD -> tr("高强度有氧")
    CATEGORY_ANAEROBIC -> tr("无氧")
    CATEGORY_STRENGTH -> tr("力量训练")
    else -> tr("低强度有氧")
}

private fun activityLocalDate(activity: ActivitySummary): LocalDate? {
    val raw = activity.startTime?.trim()?.takeIf { it.length >= 10 } ?: return null
    return runCatching { LocalDate.parse(raw.substring(0, 10), activityDateFormatter) }.getOrNull()
}

private fun latestActivityDate(activities: List<ActivitySummary>): LocalDate? =
    activities.mapNotNull(::activityLocalDate).maxOrNull()

private fun inferCategory(activity: ActivitySummary): String {
    // The API normally supplies category. This conservative fallback keeps older
    // snapshots usable until their automatic classification is refreshed.
    val text = "${activity.type} ${activity.name}".lowercase()
    return when {
        listOf("strength", "weight", "weightlifting", "gym", "lift", "musculation", "力量", "健身").any(text::contains) -> CATEGORY_STRENGTH
        listOf("anaerobic", "sprint", "hiit", "interval", "tabata", "冲刺", "间歇").any(text::contains) -> CATEGORY_ANAEROBIC
        activity.trainingEffect?.let { it >= 3.8 } == true || activity.avgHr?.let { it >= 155 } == true -> CATEGORY_HARD
        else -> CATEGORY_EASY
    }
}

private fun effectiveCategory(activity: ActivitySummary): String {
    return (activity.category ?: activity.categoryOverride ?: inferCategory(activity))
        .lowercase()
        .takeIf { it in activityCategories }
        ?: inferCategory(activity)
}

private fun estimatedRpe(category: String): Double = when (category) {
    CATEGORY_HARD -> 6.0
    CATEGORY_ANAEROBIC -> 8.0
    CATEGORY_STRENGTH -> 6.0
    else -> 3.0
}

private fun internalLoadValue(activity: ActivitySummary): Double? {
    activity.internalLoad?.takeIf { it.isFinite() && it >= 0.0 }?.let { return it }
    val minutes = activity.durationS?.div(60.0)?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
    val category = effectiveCategory(activity)
    val rpe = activity.effortRpe?.takeIf { it.isFinite() && it >= 0.0 } ?: estimatedRpe(category)
    return minutes * rpe
}

private fun isEstimated(activity: ActivitySummary): Boolean =
    activity.effortSource?.lowercase() != "reported" || activity.effortRpe == null

private fun categoryActivities(activities: List<ActivitySummary>, category: String?): List<ActivitySummary> =
    activities.filter { category == null || effectiveCategory(it) == category }

private fun LocalDate.pickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun pickerDate(millis: Long?): LocalDate? = millis?.let {
    Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
}

private fun periodDates(period: ActivityPeriod, anchor: LocalDate): List<LocalDate> = when (period) {
    ActivityPeriod.WEEK -> {
        val monday = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
        (0L..6L).map(monday::plusDays)
    }
    ActivityPeriod.MONTH -> {
        val first = anchor.withDayOfMonth(1)
        (0 until anchor.lengthOfMonth()).map { first.plusDays(it.toLong()) }
    }
}

private fun shiftAnchor(period: ActivityPeriod, anchor: LocalDate, amount: Long): LocalDate = when (period) {
    ActivityPeriod.WEEK -> anchor.plusWeeks(amount)
    ActivityPeriod.MONTH -> anchor.plusMonths(amount)
}

private fun dateRangeLabel(period: ActivityPeriod, dates: List<LocalDate>): String = when (period) {
    ActivityPeriod.WEEK -> {
        val first = dates.firstOrNull() ?: return ""
        val last = dates.lastOrNull() ?: first
        "${first.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))} – ${last.format(DateTimeFormatter.ofPattern("MM.dd"))}"
    }
    ActivityPeriod.MONTH -> dates.firstOrNull()?.let {
        "${it.year}.${it.monthValue.toString().padStart(2, '0')}"
    } ?: ""
}

private fun Double.roundToIntSafe(): Int = kotlin.math.round(this).toInt().coerceAtLeast(0)

private fun formatAu(value: Double?): String = value?.takeIf { it.isFinite() }?.let { "${it.roundToIntSafe()} AU" } ?: "—"

private fun formatRpe(value: Double?): String = value?.takeIf { it.isFinite() }?.let { "RPE ${"%.1f".format(it)}" } ?: ""

@Composable
private fun activityBidi(value: String): String =
    if (LocalAppLanguage.current.rtl) BidiFormatter.getInstance(true).unicodeWrap(value) else value

/**
 * Full activity history experience. Root owns navigation and detail presentation;
 * this screen only reports the selected activity through [onActivity].
 */
@Composable
fun ActivityExperience(
    state: JingYouUiState,
    onActivity: (ActivitySummary) -> Unit,
) {
    val activities = state.activities
    val initialDate = remember(activities) { latestActivityDate(activities) ?: LocalDate.now() }
    var period by rememberSaveable { mutableStateOf(ActivityPeriod.WEEK) }
    var anchorText by rememberSaveable { mutableStateOf(initialDate.toString()) }
    var anchorManuallySet by rememberSaveable { mutableStateOf(false) }
    var categoryFilter by rememberSaveable { mutableStateOf(CATEGORY_ALL) }
    var focusedDayText by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var selectedDay by remember(anchorText) { mutableStateOf<LocalDate?>(focusedDayText?.let(::parseLocalDate)) }
    LaunchedEffect(activities) {
        if (!anchorManuallySet) {
            latestActivityDate(activities)?.let { anchorText = it.toString() }
        }
    }
    val anchor = parseLocalDate(anchorText) ?: initialDate
    val dates = remember(period, anchor) { periodDates(period, anchor) }
    val selectedCategory = categoryFilter.takeUnless { it == CATEGORY_ALL }
    val periodActivities = remember(activities, dates, selectedCategory) {
        val start = dates.firstOrNull()
        val end = dates.lastOrNull()
        categoryActivities(activities, selectedCategory).filter { date ->
            val local = activityLocalDate(date)
            local != null && start != null && end != null && local >= start && local <= end
        }.sortedWith(compareByDescending<ActivitySummary> { activityLocalDate(it) }.thenByDescending { it.startTime.orEmpty() })
    }
    val visibleActivities = remember(periodActivities, selectedDay) {
        selectedDay?.let { day -> periodActivities.filter { activityLocalDate(it) == day } } ?: periodActivities
    }
    val estimatedCount = periodActivities.count(::isEstimated)
    val selectedLoads = remember(periodActivities) { periodActivities.associateWith(::internalLoadValue) }
    val totalLoad = selectedLoads.values.filterNotNull().sum()
    val totalSeconds = periodActivities.mapNotNull { it.durationS?.takeIf(Double::isFinite) }.sum()
    val chartLoads = remember(dates, periodActivities) {
        dates.map { day ->
            periodActivities.filter { activityLocalDate(it) == day }
                .groupingBy(::effectiveCategory)
                .fold(0.0) { total, activity -> total + (internalLoadValue(activity) ?: 0.0) }
        }
    }
    val loadMax = chartLoads.maxOfOrNull { it.values.sum() } ?: 0.0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ActivityHeader(
                period = period,
                anchor = anchor,
                dates = dates,
                onCalendar = { pickerOpen = true },
                onPeriodChange = {
                    period = it
                    focusedDayText = null
                    selectedDay = null
                },
                onPrevious = {
                    val next = shiftAnchor(period, anchor, -1)
                    anchorText = next.toString()
                    anchorManuallySet = true
                    focusedDayText = null
                    selectedDay = null
                },
                onNext = {
                    val next = shiftAnchor(period, anchor, 1)
                    anchorText = next.toString()
                    anchorManuallySet = true
                    focusedDayText = null
                    selectedDay = null
                },
            )
        }
        item {
            ActivityFilterRow(categoryFilter) { next ->
                categoryFilter = next
                focusedDayText = null
                selectedDay = null
            }
        }
        item {
            ActivitySummaryPanel(
                period = period,
                count = periodActivities.size,
                seconds = totalSeconds,
                internalLoad = totalLoad,
                estimatedCount = estimatedCount,
            )
        }
        item {
            ActivityLoadChart(
                period = period,
                dates = dates,
                loads = chartLoads,
                selectedDay = selectedDay,
                maxLoad = loadMax,
                onDayClick = { day ->
                    selectedDay = day
                    focusedDayText = day.toString()
                },
            )
        }
        if (selectedDay != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${tr("当天活动")} · ${activityBidi(selectedDay.toString())}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = {
                        selectedDay = null
                        focusedDayText = null
                    }) { Text(tr("查看整个时段")) }
                }
            }
        }
        if (visibleActivities.isEmpty()) {
            item { ActivityEmptyState(hasAnyActivities = activities.isNotEmpty(), focusedDay = selectedDay != null) }
        } else {
            items(visibleActivities, key = { it.id }) { activity ->
                ActivityExperienceCard(activity = activity, onClick = { onActivity(activity) })
            }
        }
    }

    if (pickerOpen) {
        ActivityCalendarDialog(
            selectedDate = anchor,
            onDismiss = { pickerOpen = false },
            onDateSelected = { date ->
                if (date != null) {
                    anchorText = date.toString()
                    anchorManuallySet = true
                    focusedDayText = null
                    selectedDay = null
                }
                pickerOpen = false
            },
        )
    }
}

private fun parseLocalDate(value: String?): LocalDate? = value?.let {
    runCatching { LocalDate.parse(it, activityDateFormatter) }.getOrNull()
}

@Composable
private fun ActivityHeader(
    period: ActivityPeriod,
    anchor: LocalDate,
    dates: List<LocalDate>,
    onCalendar: () -> Unit,
    onPeriodChange: (ActivityPeriod) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val chooseDateLabel = tr("选择日期")
    val previousPeriodLabel = tr("上一个时段")
    val nextPeriodLabel = tr("下一个时段")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(tr("活动").ifBlank { tr("运动") }, style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = if (period == ActivityPeriod.WEEK) tr("按周查看负荷") else tr("按月查看负荷"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onCalendar,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = chooseDateLabel },
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
            }
        }
        GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(7.dp), accent = AuroraViolet) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PeriodChoice(
                    label = tr("周"),
                    selected = period == ActivityPeriod.WEEK,
                    modifier = Modifier.weight(1f),
                    onClick = { onPeriodChange(ActivityPeriod.WEEK) },
                )
                PeriodChoice(
                    label = tr("月"),
                    selected = period == ActivityPeriod.MONTH,
                    modifier = Modifier.weight(1f),
                    onClick = { onPeriodChange(ActivityPeriod.MONTH) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(48.dp).semantics { contentDescription = previousPeriodLabel },
            ) { Icon(Icons.Rounded.ArrowBack, contentDescription = null) }
            Text(
                text = dateRangeLabel(period, dates),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(
                onClick = onNext,
                modifier = Modifier.size(48.dp).semantics { contentDescription = nextPeriodLabel },
            ) { Icon(Icons.Rounded.ArrowForward, contentDescription = null) }
        }
    }
}

@Composable
private fun PeriodChoice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) ArcticBlue.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.role = Role.Tab },
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun ActivityFilterRow(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == CATEGORY_ALL,
            onClick = { onSelect(CATEGORY_ALL) },
            label = { Text(tr("全部")) },
            leadingIcon = { Icon(Icons.Rounded.FilterList, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
        activityCategories.forEach { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(categoryLabel(category)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun ActivitySummaryPanel(
    period: ActivityPeriod,
    count: Int,
    seconds: Double,
    internalLoad: Double,
    estimatedCount: Int,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(16.dp), accent = ElectricCyan) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (period == ActivityPeriod.WEEK) tr("周累计") else tr("本月累计"), style = MaterialTheme.typography.titleMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryMetric(count.toString(), tr("活动次数"), Modifier.weight(1f))
                SummaryMetric(durationText(seconds / 3600), tr("运动时长"), Modifier.weight(1f))
                SummaryMetric(formatAu(internalLoad), tr("内部负荷"), Modifier.weight(1f))
            }
            if (estimatedCount > 0) {
                Text(
                    text = "${tr("其中估算")} ${activityBidi(estimatedCount.toString())}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(activityBidi(value), style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActivityLoadChart(
    period: ActivityPeriod,
    dates: List<LocalDate>,
    loads: List<Map<String, Double>>,
    selectedDay: LocalDate?,
    maxLoad: Double,
    onDayClick: (LocalDate) -> Unit,
) {
    val chartAxisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val selectedMarkerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)
    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 16.dp), accent = HardAerobicColor) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("内部负荷趋势"), style = MaterialTheme.typography.titleMedium)
                    Text(tr("按日累加 AU"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LegendDot(category = CATEGORY_EASY)
                Spacer(Modifier.width(7.dp))
                LegendDot(category = CATEGORY_HARD)
                Spacer(Modifier.width(7.dp))
                LegendDot(category = CATEGORY_ANAEROBIC)
                Spacer(Modifier.width(7.dp))
                LegendDot(category = CATEGORY_STRENGTH)
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (period == ActivityPeriod.WEEK) 166.dp else 186.dp)
                        .pointerInput(dates, loads) {
                            detectTapGestures { offset ->
                                val slotWidth = size.width / dates.size.coerceAtLeast(1)
                                val index = floor(offset.x / slotWidth).toInt().coerceIn(0, dates.lastIndex)
                                onDayClick(dates[index])
                            }
                        },
                ) {
                    val chartTop = 8.dp.toPx()
                    val chartBottom = size.height - 14.dp.toPx()
                    val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)
                    val slotWidth = size.width / dates.size.coerceAtLeast(1)
                    val barWidth = (slotWidth * if (period == ActivityPeriod.MONTH) 0.58f else 0.62f).coerceAtLeast(4.dp.toPx())
                    dates.forEachIndexed { index, date ->
                        val values = loads.getOrNull(index).orEmpty()
                        val total = values.values.sum()
                        val x = slotWidth * index + (slotWidth - barWidth) / 2f
                        if (total <= 0.0 || maxLoad <= 0.0) {
                            drawLine(
                                color = chartAxisColor,
                                start = androidx.compose.ui.geometry.Offset(x, chartBottom),
                                end = androidx.compose.ui.geometry.Offset(x + barWidth, chartBottom),
                                strokeWidth = 1.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        } else {
                            var y = chartBottom
                            listOf(CATEGORY_EASY, CATEGORY_HARD, CATEGORY_ANAEROBIC, CATEGORY_STRENGTH).forEach { category ->
                                val amount = values[category] ?: 0.0
                                if (amount > 0.0) {
                                    val height = chartHeight * (amount / maxLoad).toFloat()
                                    y -= height
                                    drawRoundRect(
                                        color = categoryColor(category),
                                        topLeft = androidx.compose.ui.geometry.Offset(x, y),
                                        size = androidx.compose.ui.geometry.Size(barWidth, height),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                                    )
                                }
                            }
                        }
                        if (date == selectedDay) {
                            drawRoundRect(
                                color = selectedMarkerColor,
                                topLeft = androidx.compose.ui.geometry.Offset(x - 3.dp.toPx(), chartTop - 3.dp.toPx()),
                                size = androidx.compose.ui.geometry.Size(barWidth + 6.dp.toPx(), chartHeight + 6.dp.toPx()),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                                style = Stroke(width = 1.5.dp.toPx()),
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    dates.forEachIndexed { index, date ->
                        val show = period == ActivityPeriod.WEEK || index == 0 || index % 5 == 0 || index == dates.lastIndex
                        Text(
                            text = if (show) date.dayOfMonth.toString() else "",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (date == selectedDay) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (date == selectedDay) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
            ActivityLegend()
        }
    }
}

@Composable
private fun LegendDot(category: String) {
    val label = categoryLabel(category)
    Box(Modifier.size(8.dp).clip(CircleShape).background(categoryColor(category)).semantics { contentDescription = label })
}

@Composable
private fun ActivityLegend() {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        activityCategories.forEach { category ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                LegendDot(category)
                Text(categoryLabel(category), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ActivityExperienceCard(activity: ActivitySummary, onClick: () -> Unit) {
    val category = effectiveCategory(activity)
    val color = categoryColor(category)
    PressableGlassPanel(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 86.dp),
        shape = CardShape,
        padding = PaddingValues(14.dp),
        accent = color,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(58.dp).clip(RoundedCornerShape(4.dp)).background(color))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(categoryIcon(category), contentDescription = categoryLabel(category), tint = color, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(activity.name.ifBlank { activity.type.ifBlank { tr("运动记录") } }, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                val localDate = activityLocalDate(activity)?.toString() ?: tr("日期未知")
                Text(activityBidi(localDate), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(durationText(activity.durationS?.div(3600)), style = MaterialTheme.typography.labelMedium)
                    Text(activityBidi(formatAu(internalLoadValue(activity))), style = MaterialTheme.typography.labelMedium)
                    activity.avgHr?.let { hr -> Text(activityBidi("${hr.roundToIntSafe()} bpm"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                StatusPill(if (isEstimated(activity)) tr("估算") else tr("自评"), color)
                if (activity.effortRpe != null) Text(activityBidi(formatRpe(activity.effortRpe)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Text(tr("待自评"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun categoryIcon(category: String) = when (category) {
    CATEGORY_STRENGTH -> Icons.Rounded.FitnessCenter
    CATEGORY_ANAEROBIC -> Icons.Rounded.FlashOn
    CATEGORY_HARD -> Icons.Rounded.DirectionsRun
    else -> Icons.Rounded.Favorite
}

@Composable
private fun ActivityEmptyState(hasAnyActivities: Boolean, focusedDay: Boolean) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(22.dp), accent = ArcticBlue) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.AccessTime, contentDescription = null, tint = ArcticBlue, modifier = Modifier.size(25.dp))
            Text(if (focusedDay) tr("当天没有活动") else tr("该时段没有活动"), style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (hasAnyActivities) tr("换一个日期或筛选条件看看") else tr("同步健康档案后，这里会显示真实活动记录"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityCalendarDialog(selectedDate: LocalDate, onDismiss: () -> Unit, onDateSelected: (LocalDate?) -> Unit) {
    val datePickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = selectedDate.pickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onDateSelected(pickerDate(datePickerState.selectedDateMillis)) }) { Text(tr("确定")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消")) } },
    ) { DatePicker(state = datePickerState, title = { Text(tr("选择日期"), modifier = Modifier.padding(start = 24.dp, top = 16.dp)) }) }
}

/** Public editor for the activity detail sheet owned by root. */
@Composable
fun EffortEditor(
    activity: ActivitySummary,
    onSave: (Double?, String?) -> Unit,
    busy: Boolean = false,
) {
    var hasDraftRpe by remember(activity.id) { mutableStateOf(activity.effortRpe != null) }
    var draftRpe by remember(activity.id) { mutableStateOf(activity.effortRpe?.toFloat() ?: 5f) }
    var selectedOverride by remember(activity.id) { mutableStateOf(activity.categoryOverride ?: CATEGORY_AUTO) }
    val selectedCategory = selectedOverride.takeUnless { it == CATEGORY_AUTO }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(tr("主观用力程度"), style = MaterialTheme.typography.titleMedium)
        if (!hasDraftRpe) {
            Text(tr("未自评，当前为估算"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(activityBidi("%.1f".format(draftRpe)), style = MaterialTheme.typography.displaySmall)
                Text("/ 10", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Slider(
            value = draftRpe,
            onValueChange = {
                draftRpe = it
                hasDraftRpe = true
            },
            valueRange = 0f..10f,
            steps = 9,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        )
        Text(tr("分类"), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedOverride == CATEGORY_AUTO,
                onClick = { selectedOverride = CATEGORY_AUTO },
                label = { Text(tr("自动识别")) },
                enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp),
            )
            activityCategories.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedOverride = category },
                    label = { Text(categoryLabel(category)) },
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        if (activity.effortRpe != null || hasDraftRpe) {
            TextButton(
                enabled = !busy,
                onClick = {
                    hasDraftRpe = false
                    onSave(null, selectedCategory)
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(tr("清除自评"), color = Rose) }
        }
        Button(
            onClick = { onSave(if (hasDraftRpe) draftRpe.toDouble() else null, selectedCategory) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(if (busy) tr("保存中") else tr("保存自评")) }
    }
}
