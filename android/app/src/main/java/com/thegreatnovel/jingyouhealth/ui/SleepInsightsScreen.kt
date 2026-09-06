package com.thegreatnovel.jingyouhealth.ui

import androidx.activity.compose.BackHandler

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.text.BidiFormatter
import com.thegreatnovel.jingyouhealth.model.FeatureImportance
import com.thegreatnovel.jingyouhealth.model.PersonalSleepCandidate
import com.thegreatnovel.jingyouhealth.model.PersonalSleepReport
import com.thegreatnovel.jingyouhealth.model.RegressionPrediction
import com.thegreatnovel.jingyouhealth.model.RegressionResult
import com.thegreatnovel.jingyouhealth.model.RegressionStatus
import com.thegreatnovel.jingyouhealth.model.SleepAlgorithm
import com.thegreatnovel.jingyouhealth.model.SleepFactor
import com.thegreatnovel.jingyouhealth.model.SleepFeaturePack
import com.thegreatnovel.jingyouhealth.model.SleepOutcome
import com.thegreatnovel.jingyouhealth.model.factorSeries
import com.thegreatnovel.jingyouhealth.model.filterSleepContext
import com.thegreatnovel.jingyouhealth.model.fitSleepRegression
import com.thegreatnovel.jingyouhealth.model.proposeSleepConfigurations
import com.thegreatnovel.jingyouhealth.model.sleepContextSeries
import com.thegreatnovel.jingyouhealth.model.CoachSleepSnapshot
import com.thegreatnovel.jingyouhealth.model.buildCoachSleepModel
import com.thegreatnovel.jingyouhealth.model.buildCoachSleepSnapshot
import com.thegreatnovel.jingyouhealth.ui.components.GlassPanel
import com.thegreatnovel.jingyouhealth.ui.theme.ArcticBlue
import com.thegreatnovel.jingyouhealth.ui.theme.ElectricCyan
import com.thegreatnovel.jingyouhealth.ui.theme.Rose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private enum class InsightTarget { OVERALL, DEEP, REM }

private data class HistoricalReportState(
    val loading: Boolean = false,
    val report: PersonalSleepReport? = null,
)

private fun targetForOutcome(outcome: SleepOutcome): InsightTarget = when (outcome) {
    SleepOutcome.DEEP_HOURS, SleepOutcome.DEEP_PERCENT -> InsightTarget.DEEP
    SleepOutcome.REM_HOURS, SleepOutcome.REM_PERCENT -> InsightTarget.REM
    else -> InsightTarget.OVERALL
}

private fun outcomeForTarget(target: InsightTarget, percentage: Boolean): SleepOutcome = when (target) {
    InsightTarget.OVERALL -> SleepOutcome.DURATION_HOURS
    InsightTarget.DEEP -> if (percentage) SleepOutcome.DEEP_PERCENT else SleepOutcome.DEEP_HOURS
    InsightTarget.REM -> if (percentage) SleepOutcome.REM_PERCENT else SleepOutcome.REM_HOURS
}

private fun parseInsightDate(value: String?): LocalDate? = value?.let {
    runCatching { LocalDate.parse(it) }.getOrNull()
}

/** Sleep insight dates cannot move past the latest actual sleep record. */
private fun boundedSleepDate(anchorDate: String?, latestSleepDate: String?): String? {
    val latest = parseInsightDate(latestSleepDate)
    val requested = parseInsightDate(anchorDate)
    return when {
        latest == null -> requested?.toString() ?: anchorDate
        requested == null -> latest.toString()
        requested.isAfter(latest) -> latest.toString()
        else -> requested.toString()
    }
}

/**
 * Compact machine-learning sleep insight detail. Candidate ranking remains the scout's ranking;
 * this screen only takes its first RANDOM_FOREST, order-zero candidate and refits it for held-out
 * importance display. It never promotes a model based on the newest validation score.
 */
@Composable
fun SleepInsightsScreen(
    state: JingYouUiState,
    anchorDate: String?,
    onBack: () -> Unit,
    onAsk: (String, CoachSleepSnapshot) -> Unit,
    initialOutcome: SleepOutcome = SleepOutcome.DURATION_HOURS,
) {
    BackHandler(onBack = onBack)
    val initialTarget = targetForOutcome(initialOutcome)
    var targetKey by rememberSaveable(initialOutcome) { mutableStateOf(initialTarget.name) }
    var percentage by rememberSaveable(initialOutcome) {
        mutableStateOf(initialOutcome == SleepOutcome.DEEP_PERCENT || initialOutcome == SleepOutcome.REM_PERCENT)
    }
    val target = runCatching { InsightTarget.valueOf(targetKey) }.getOrDefault(InsightTarget.OVERALL)
    val outcome = outcomeForTarget(target, percentage)
    val latestSleepDate = state.dashboard?.sleep?.date ?: outcome.series(state.trends).lastOrNull()?.date
    val effectiveAnchorDate = boundedSleepDate(anchorDate, latestSleepDate)
    val cachedReport = state.personalSleepReports[outcome]?.takeIf { report ->
        effectiveAnchorDate == null || report.throughDate == effectiveAnchorDate
    }
    val historicalAnchor = anchorDate != null && effectiveAnchorDate != null && effectiveAnchorDate != latestSleepDate
    val historicalKey: Any = if (historicalAnchor && cachedReport == null) {
        listOf(state.token, outcome, effectiveAnchorDate, state.trends, state.activities, state.frenchHolidays)
    } else {
        "latest-report-cache"
    }
    val historicalState by produceState<HistoricalReportState>(HistoricalReportState(), key1 = historicalKey) {
        value = HistoricalReportState()
        if (historicalAnchor && cachedReport == null && effectiveAnchorDate != null) {
            value = HistoricalReportState(loading = true)
            val cutoff = effectiveAnchorDate
            val snapshotTrends = state.trends
            val snapshotActivities = state.activities
            val snapshotHolidays = state.frenchHolidays
            val report = try {
                withContext(Dispatchers.Default) {
                    val coroutineContext = currentCoroutineContext()
                    proposeSleepConfigurations(
                        outcome = outcome,
                        trends = snapshotTrends,
                        activities = snapshotActivities,
                        throughDate = cutoff,
                        algorithms = listOf(SleepAlgorithm.RANDOM_FOREST),
                        includeEnrichedForest = true,
                        contextSeries = sleepContextSeries(snapshotTrends),
                        includeFrenchHolidays = snapshotHolidays,
                        differenceOrders = listOf(0),
                        checkpoint = { coroutineContext.ensureActive() },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            value = HistoricalReportState(report = report)
        }
    }
    val report = cachedReport ?: historicalState.report
    val targetDate = effectiveAnchorDate ?: report?.throughDate
    val best = report?.candidates?.firstOrNull {
        it.config.differenceOrder == 0 && it.config.algorithm == SleepAlgorithm.RANDOM_FOREST
    }

    // Keep the producer on the single-key overload; Compose's multi-key overloads stop at
    // three named keys on the pinned runtime used by this app.
    val fitKey = listOf<Any?>(
        outcome,
        targetDate,
        best?.config?.id,
        report?.verificationCutoff,
        state.trends,
        state.activities,
        state.frenchHolidays,
    )
    val fittedResult by produceState<RegressionResult?>(initialValue = null, key1 = fitKey) {
        value = null
        val selectedCandidate = best
        val splitDate = report?.verificationCutoff
        val endDate = targetDate
        if (selectedCandidate != null && endDate != null && splitDate != null) {
            value = withContext(Dispatchers.Default) {
                runCatching {
                    val config = selectedCandidate.config
                    val context = filterSleepContext(
                        sleepContextSeries(state.trends),
                        config.factorA,
                        config.factorB,
                    )
                    fitSleepRegression(
                        outcome = outcome.series(state.trends, endDate),
                        factorA = factorSeries(config.factorA, state.trends, state.activities, endDate),
                        factorB = factorSeries(config.factorB, state.trends, state.activities, endDate),
                        throughDate = endDate,
                        days = config.days,
                        differenceOrder = 0,
                        lagDays = config.lagDays,
                        includeInteraction = config.interaction,
                        splitDate = splitDate,
                        algorithm = SleepAlgorithm.RANDOM_FOREST,
                        featurePack = config.featurePack,
                        contextSeries = context,
                        includeFrenchHolidays = state.frenchHolidays,
                        withImportance = true,
                    )
                }.getOrNull()
            }
        }
    }

    val subtitle = targetDate?.let { "${tr("睡眠记录日期")} · ${bidiDate(it)}" } ?: tr("最新记录")
    val currentResult = fittedResult
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 18.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DetailHeader(tr("睡眠洞察"), subtitle, onBack) }
        item {
            InsightTargetPicker(
                target = target,
                percentage = percentage,
                onTarget = {
                    targetKey = it.name
                    if (it == InsightTarget.OVERALL) percentage = false
                },
                onPercentage = { percentage = it },
            )
        }
        if (historicalState.loading || (state.scoutingSleep && report == null)) {
            item { InsightLoadingCard() }
        } else if (report == null) {
            item { InsightEmptyCard() }
        } else if (best == null) {
            item { InsightCandidateEmptyCard() }
        } else if (currentResult == null) {
            item { InsightLoadingCard() }
        } else {
            val result = currentResult
            val safeCandidate = checkNotNull(best)
            item { InsightConclusion(result) }
            if (result.featureImportances.isNotEmpty()) {
                item {
                    FeatureImportancePanel(
                        result = result,
                        labels = importanceLabels(safeCandidate, result.featureImportances.map { it.key }.toSet()),
                        unit = tr(outcome.unit),
                    )
                }
            }
            if (result.holdout.isNotEmpty()) {
                item { PredictionReviewPanel(result, tr(outcome.unit)) }
            }
            item {
                val naturalPrompt = tr("请帮我解读这组睡眠洞察，区分记录支持的线索与尚待验证的假设。")
                val coachPrompt = naturalPrompt + "\n" + tr(outcome.labelChinese) + " · " + targetDate.orEmpty()
                val analysis = remember(report, result, state.trends, state.frenchHolidays) {
                    buildCoachSleepSnapshot(checkNotNull(targetDate), listOf(buildCoachSleepModel(checkNotNull(report), result)), state.trends, state.frenchHolidays)
                }
                QuietAction(tr("交给教练解读")) { onAsk(coachPrompt, analysis) }
            }
        }
        if (state.scoutingSleep && report != null) {
            item {
                Text(
                    tr("正在更新睡眠线索"),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InsightTargetPicker(
    target: InsightTarget,
    percentage: Boolean,
    onTarget: (InsightTarget) -> Unit,
    onPercentage: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            InsightChoice(tr("整体睡眠"), target == InsightTarget.OVERALL, Modifier.weight(1f)) { onTarget(InsightTarget.OVERALL) }
            InsightChoice(tr("深睡"), target == InsightTarget.DEEP, Modifier.weight(1f)) { onTarget(InsightTarget.DEEP) }
            InsightChoice("REM", target == InsightTarget.REM, Modifier.weight(1f)) { onTarget(InsightTarget.REM) }
        }
        if (target != InsightTarget.OVERALL) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilterChip(
                    selected = !percentage,
                    onClick = { onPercentage(false) },
                    label = { Text(tr("时长")) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                FilterChip(
                    selected = percentage,
                    onClick = { onPercentage(true) },
                    label = { Text(tr("占比")) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun InsightLoadingCard() {
    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(20.dp), accent = ArcticBlue) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tr("正在寻找可解释的睡眠线索"), style = MaterialTheme.typography.titleMedium)
                Text(tr("正在积累可比较的夜晚"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InsightEmptyCard() {
    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(22.dp), accent = ArcticBlue) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = ArcticBlue, modifier = Modifier.size(24.dp))
            Text(tr("等待睡眠数据"), style = MaterialTheme.typography.titleMedium)
            Text(tr("这个日期还没有可用的睡眠洞察记录。"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InsightCandidateEmptyCard() {
    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(22.dp), accent = ArcticBlue) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("目前还没有足够记录支持这个目标的机器学习线索"), style = MaterialTheme.typography.titleMedium)
            Text(tr("继续积累可比较的夜晚后，再回来查看变量权重。"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InsightConclusion(result: RegressionResult) {
    val beatsControl = result.status == RegressionStatus.READY &&
        result.holdoutMAE?.takeIf(Double::isFinite) != null &&
        result.controlMAE?.takeIf(Double::isFinite) != null &&
        result.holdoutMAE!! < result.controlMAE!!
    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(20.dp), accent = if (beatsControl) ElectricCyan else Rose) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (beatsControl) ElectricCyan else Rose).padding(top = 5.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    "${tr("机器学习方法")} · ${tr("随机森林")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    tr(if (beatsControl) "这组线索在留出记录上比简单参考更贴近" else "线索还不稳定"),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    tr(if (beatsControl) "线索可以继续观察" else "可以结合实际记录和你的感受，继续问 Coach。"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PredictionReviewPanel(result: RegressionResult, unit: String) {
    val predictions = result.holdout
    var expanded by rememberSaveable { mutableStateOf(false) }
    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(16.dp), accent = Rose) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("这些线索靠得住吗？"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Icon(Icons.Rounded.ExpandMore, contentDescription = tr(if (expanded) "收起" else "展开"))
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("用于复核的夜晚") + " · " + predictions.size, style = MaterialTheme.typography.bodyMedium)
                    Text(predictions.firstOrNull()?.date.orEmpty() + " — " + predictions.lastOrNull()?.date.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(tr("模型平均相差") + " · " + coreNumber(result.holdoutMAE, unit, 2), style = MaterialTheme.typography.bodyMedium)
                    Text(tr("简单参考平均相差") + " · " + coreNumber(result.controlMAE, unit, 2), style = MaterialTheme.typography.bodyMedium)
                    PredictionChart(predictions)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChartLegendDot(ElectricCyan, tr("实际记录"))
                        ChartLegendDot(Rose, tr("模型预测"))
                    }
                    Text(tr("按日历日期连接；缺少日期时会断开，不把间隔填成连续记录。"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ChartLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PredictionChart(predictions: List<RegressionPrediction>) {
    val points = remember(predictions) {
        predictions.mapNotNull { prediction ->
            runCatching { LocalDate.parse(prediction.date) }.getOrNull()?.let { it to prediction }
        }.sortedBy { it.first }
    }
    val actualColor = ElectricCyan
    val predictedColor = Rose
    Canvas(Modifier.fillMaxWidth().height(156.dp)) {
        if (points.isEmpty()) return@Canvas
        val values = points.flatMap { listOf(it.second.observed, it.second.predicted) }.filter(Double::isFinite)
        if (values.isEmpty()) return@Canvas
        val min = values.minOrNull() ?: return@Canvas
        val max = values.maxOrNull() ?: return@Canvas
        val spread = (max - min).takeIf { it > 1e-6 } ?: 1.0
        val firstDate = points.first().first
        val lastDate = points.last().first
        val dateSpan = ChronoUnit.DAYS.between(firstDate, lastDate).coerceAtLeast(1).toFloat()
        val insetX = 4.dp.toPx()
        val insetY = 8.dp.toPx()
        val width = (size.width - insetX * 2).coerceAtLeast(1f)
        val height = (size.height - insetY * 2).coerceAtLeast(1f)
        fun x(date: LocalDate): Float = insetX + width * (ChronoUnit.DAYS.between(firstDate, date).toFloat() / dateSpan)
        fun y(value: Double): Float = insetY + height * (1f - ((value - min) / spread).toFloat().coerceIn(0f, 1f))
        val actualPath = Path()
        val predictedPath = Path()
        var previousDate: LocalDate? = null
        points.forEach { (date, prediction) ->
            val continuous = previousDate != null && ChronoUnit.DAYS.between(previousDate, date) == 1L
            val actualX = x(date)
            val actualY = y(prediction.observed)
            val predictedY = y(prediction.predicted)
            if (continuous) {
                actualPath.lineTo(actualX, actualY)
                predictedPath.lineTo(actualX, predictedY)
            } else {
                actualPath.moveTo(actualX, actualY)
                predictedPath.moveTo(actualX, predictedY)
            }
            drawCircle(actualColor, 2.6.dp.toPx(), Offset(actualX, actualY))
            drawCircle(predictedColor, 2.6.dp.toPx(), Offset(actualX, predictedY))
            previousDate = date
        }
        drawPath(actualPath, color = actualColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawPath(predictedPath, color = predictedColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun importanceLabels(candidate: PersonalSleepCandidate, keys: Set<String>): Map<String, String> {
    val base = mapOf(
        "factor_a" to tr(candidate.config.factorA.labelChinese),
        "factor_b" to tr(candidate.config.factorB.labelChinese),
        "previous_y" to tr("上一晚睡眠"),
        "weekend" to tr("周末"),
        "holiday" to tr("法国法定节假日"),
        "holiday_eve" to tr("节假日前一天"),
        "context_hrv_change" to tr("前夜 HRV 的变化"),
        "context_rhr_change" to tr("前日静息心率的变化"),
        "context_stress_mean7" to tr("前日压力的近周均值"),
    )
    val derivedSuffixes = mapOf(
        "diff1" to tr("日变化"),
        "mean7" to tr("近 7 天均值"),
        "sd7" to tr("近 7 天波动"),
        "median28_dev" to tr("相对近 28 天基线"),
    )
    val other = tr("其他信号")
    return keys.associateWith { key ->
        base[key] ?: when {
            key == "interaction" -> tr("共同作用")
            key.startsWith("factor_a.") -> base.getValue("factor_a") + " · " + (derivedSuffixes[key.substringAfterLast('.')] ?: other)
            key.startsWith("factor_b.") -> base.getValue("factor_b") + " · " + (derivedSuffixes[key.substringAfterLast('.')] ?: other)
            else -> other
        }
    }
}

@Composable
private fun bidiDate(date: String): String =
    if (LocalAppLanguage.current.rtl) BidiFormatter.getInstance(true).unicodeWrap(date) else date
