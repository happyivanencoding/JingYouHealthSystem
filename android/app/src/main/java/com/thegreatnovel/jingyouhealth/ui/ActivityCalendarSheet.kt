package com.thegreatnovel.jingyouhealth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.text.BidiFormatter
import com.thegreatnovel.jingyouhealth.model.ActivitySummary
import com.thegreatnovel.jingyouhealth.ui.components.GlassPanel
import com.thegreatnovel.jingyouhealth.ui.components.PressableGlassPanel
import com.thegreatnovel.jingyouhealth.ui.theme.ElectricCyan
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

private fun calendarMonths(earliest: LocalDate, latest: LocalDate): List<YearMonth> {
    val first = YearMonth.from(earliest)
    val last = YearMonth.from(latest)
    val count = ChronoUnit.MONTHS.between(first, last).toInt().coerceAtLeast(0)
    return (0..count).map { first.plusMonths(it.toLong()) }
}

private fun boundedDate(date: LocalDate, earliest: LocalDate, latest: LocalDate): LocalDate = when {
    date < earliest -> earliest
    date > latest -> latest
    else -> date
}

private fun monthSelection(month: YearMonth, earliest: LocalDate, latest: LocalDate): LocalDate =
    boundedDate(month.atDay(1), earliest, latest).let { candidate ->
        if (candidate.month == month.month && candidate.year == month.year) candidate else boundedDate(month.atEndOfMonth(), earliest, latest)
    }

@Composable
private fun calendarDateText(date: LocalDate): String =
    BidiFormatter.getInstance(LocalAppLanguage.current.rtl).unicodeWrap(date.toString())

private fun calendarCategoryIcon(category: String) = when (category) {
    "strength" -> Icons.Rounded.FitnessCenter
    "anaerobic" -> Icons.Rounded.FlashOn
    "hard_aerobic" -> Icons.Rounded.DirectionsRun
    else -> Icons.Rounded.DirectionsRun
}

private fun calendarAu(value: Double?): String = value?.takeIf { it.isFinite() }?.let { "${kotlin.math.round(it).toInt()} AU" } ?: "—"

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ActivityCalendarSheet(
    activities: List<ActivitySummary>,
    initialDate: LocalDate,
    earliestDate: LocalDate,
    latestDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onActivity: (ActivitySummary) -> Unit,
    onDismiss: () -> Unit,
) {
    val earliest = minOf(earliestDate, latestDate)
    val latest = maxOf(earliestDate, latestDate)
    val months = remember(earliest, latest) { calendarMonths(earliest, latest) }
    val safeInitial = boundedDate(initialDate, earliest, latest)
    val initialPage = months.indexOf(YearMonth.from(safeInitial)).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { months.size })
    val scope = rememberCoroutineScope()
    var selectedDateText by rememberSaveable(earliest, latest, initialDate) { mutableStateOf(safeInitial.toString()) }
    val selectedDate = boundedDate(LocalDate.parse(selectedDateText), earliest, latest)
    val activitiesByDate = remember(activities) {
        activities.mapNotNull { activity -> activityLocalDate(activity)?.let { it to activity } }.groupBy({ it.first }, { it.second })
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(pagerState.currentPage) {
        val pageMonth = months.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        if (YearMonth.from(selectedDate) != pageMonth) selectedDateText = monthSelection(pageMonth, earliest, latest).toString()
    }

    val previousMonthLabel = tr("上一月")
    val nextMonthLabel = tr("下一月")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("活动日历"), style = MaterialTheme.typography.headlineMedium)
                    Text(tr("滚动四周"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(calendarDateText(selectedDate), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } },
                    enabled = pagerState.currentPage > 0,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = previousMonthLabel },
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) }
                Text(
                    text = months.getOrNull(pagerState.currentPage)?.let { "${it.year}.${it.monthValue.toString().padStart(2, '0')}" }.orEmpty(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(months.lastIndex)) } },
                    enabled = pagerState.currentPage < months.lastIndex,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = nextMonthLabel },
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null) }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(390.dp)) { page ->
                CalendarMonthPage(
                    month = months[page],
                    earliest = earliest,
                    latest = latest,
                    selectedDate = selectedDate,
                    activitiesByDate = activitiesByDate,
                    onDateClick = { selectedDateText = it.toString() },
                )
            }
            CalendarDayDetails(
                date = selectedDate,
                activities = activitiesByDate[selectedDate].orEmpty(),
                onActivity = { activity ->
                    onDismiss()
                    onActivity(activity)
                },
            )
            TextButton(
                onClick = { onDateSelected(selectedDate) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(tr("查看这天结束的四周")) }
        }
    }
}

@Composable
private fun CalendarMonthPage(
    month: YearMonth,
    earliest: LocalDate,
    latest: LocalDate,
    selectedDate: LocalDate,
    activitiesByDate: Map<LocalDate, List<ActivitySummary>>,
    onDateClick: (LocalDate) -> Unit,
) {
    val firstOffset = month.atDay(1).dayOfWeek.value - 1
    val cells: List<LocalDate?> = buildList {
        repeat(firstOffset) { add(null) }
        (1..month.lengthOfMonth()).forEach { add(month.atDay(it)) }
        while (size % 7 != 0) add(null)
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        CompositionLocalProvider(LocalLayoutDirection provides if (LocalAppLanguage.current.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth()) {
                listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").forEach { weekday ->
                    Text(tr(weekday), modifier = Modifier.weight(1f).heightIn(min = 32.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        CalendarDayCell(
                            date = date,
                            earliest = earliest,
                            latest = latest,
                            selected = date == selectedDate,
                            activities = date?.let { activitiesByDate[it].orEmpty() }.orEmpty(),
                            modifier = Modifier.weight(1f),
                            onClick = { if (date != null) onDateClick(date) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate?,
    earliest: LocalDate,
    latest: LocalDate,
    selected: Boolean,
    activities: List<ActivitySummary>,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    if (date == null) {
        Spacer(modifier.heightIn(min = 56.dp).padding(2.dp))
        return
    }
    val inRange = date >= earliest && date <= latest
    val today = date == LocalDate.now()
    val categories = activities.map(::effectiveCategory).distinct().take(3)
    val cellColor = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        activities.isNotEmpty() -> categoryColor(categories.first()).copy(alpha = 0.13f)
        else -> Color.Transparent
    }
    Box(
        modifier = modifier.heightIn(min = 56.dp).padding(2.dp).clip(RoundedCornerShape(13.dp))
            .background(cellColor)
            .border(if (today) 1.5.dp else 0.dp, if (today) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(13.dp))
            .clickable(enabled = inRange, role = Role.Button, onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 4.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = if (selected || today) FontWeight.SemiBold else FontWeight.Normal, color = if (inRange) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(14.dp)) {
                categories.forEach { category -> Icon(calendarCategoryIcon(category), contentDescription = categoryLabel(category), tint = categoryColor(category), modifier = Modifier.size(12.dp)) }
                if (activities.isNotEmpty()) Text(activities.size.toString(), style = MaterialTheme.typography.labelMedium, color = categoryColor(categories.first()))
            }
        }
    }
}

@Composable
private fun CalendarDayDetails(date: LocalDate, activities: List<ActivitySummary>, onActivity: (ActivitySummary) -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(14.dp), accent = ElectricCyan) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${tr("当天活动")} · ${calendarDateText(date)}", style = MaterialTheme.typography.titleMedium)
            if (activities.isEmpty()) {
                Text(tr("未记录运动"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                activities.sortedBy { it.startTime.orEmpty() }.forEach { activity ->
                    CalendarActivityRow(activity, onClick = { onActivity(activity) })
                }
            }
        }
    }
}

@Composable
private fun CalendarActivityRow(activity: ActivitySummary, onClick: () -> Unit) {
    val category = effectiveCategory(activity)
    PressableGlassPanel(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        padding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
        accent = categoryColor(category),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(calendarCategoryIcon(category), contentDescription = categoryLabel(category), tint = categoryColor(category), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(activity.name.ifBlank { activity.type.ifBlank { tr("运动记录") } }, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("${durationText(activity.durationS?.div(3600.0))} · ${calendarAu(internalLoadValue(activity))}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isEstimated(activity)) Text(tr("估算"), style = MaterialTheme.typography.labelMedium, color = categoryColor(category))
        }
    }
}
