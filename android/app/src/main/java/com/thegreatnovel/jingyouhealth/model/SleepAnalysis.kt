package com.thegreatnovel.jingyouhealth.model

import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.sqrt

data class MetricBaseline(
    val mean: Double,
    val median: Double,
    val sampleCount: Int,
    val startDate: String,
    val endDate: String,
    val lowerQuartile: Double = median,
    val upperQuartile: Double = median,
)

data class MetricCorrelation(
    val coefficient: Double?,
    val sampleCount: Int,
    val startDate: String? = null,
    val endDate: String? = null,
)

data class SleepStageRatios(
    val deep: Double? = null,
    val rem: Double? = null,
    val awake: Double? = null,
)

/** A calendar window, including its final date. Null samples remain gaps for the UI. */
fun calendarWindow(
    points: List<TrendPoint>,
    throughDate: String?,
    days: Int = 30,
): List<TrendPoint> {
    val end = parseDate(throughDate) ?: return emptyList()
    if (days <= 0) return emptyList()
    val start = end.minusDays(days.toLong() - 1)
    return points.mapNotNull { point -> parseDate(point.date)?.let { it to point } }
        .filter { (date, _) -> date >= start && date <= end }
        .associate { (date, point) -> date to point }
        .toSortedMap()
        .values.toList()
}

/** Personal reference from the preceding calendar days, excluding the night under review. */
fun baseline(
    points: List<TrendPoint>,
    beforeDate: String?,
    days: Int = 28,
): MetricBaseline? {
    val target = parseDate(beforeDate) ?: return null
    if (days <= 0) return null
    val values = calendarWindow(points, target.minusDays(1).toString(), days)
        .mapNotNull { it.value?.takeIf(Float::isFinite)?.toDouble() }
        .sorted()
    if (values.isEmpty()) return null
    fun percentile(fraction: Double): Double {
        val position = values.lastIndex * fraction
        val lower = floor(position).toInt()
        val upper = (lower + 1).coerceAtMost(values.lastIndex)
        return values[lower] + (values[upper] - values[lower]) * (position - lower)
    }
    return MetricBaseline(
        mean = values.average(),
        median = percentile(0.5),
        sampleCount = values.size,
        startDate = target.minusDays(days.toLong()).toString(),
        endDate = target.minusDays(1).toString(),
        lowerQuartile = percentile(0.25),
        upperQuartile = percentile(0.75),
    )
}

/** Actual scatter points as (sleep, other), in sleep-date order, with no missing-value fill. */
fun sleepMetricPairs(
    sleep: List<TrendPoint>,
    other: List<TrendPoint>,
    targetDate: String?,
    days: Int = 90,
    otherDayOffset: Int = 0,
): List<Pair<Float, Float>> {
    val target = parseDate(targetDate) ?: return emptyList()
    if (days <= 0) return emptyList()
    val otherEnd = target.plusDays(otherDayOffset.toLong())
    val comparison = calendarWindow(other, otherEnd.toString(), days).mapNotNull { point ->
        val date = parseDate(point.date) ?: return@mapNotNull null
        val value = point.value?.takeIf(Float::isFinite) ?: return@mapNotNull null
        if (date > target) null else date to value
    }.toMap()
    return calendarWindow(sleep, targetDate, days).mapNotNull { point ->
        val date = parseDate(point.date) ?: return@mapNotNull null
        val x = point.value?.takeIf(Float::isFinite) ?: return@mapNotNull null
        val y = comparison[date.plusDays(otherDayOffset.toLong())] ?: return@mapNotNull null
        x to y
    }
}

/**
 * Descriptive Pearson association, never a causal attribution or significance test.
 * Each sleep record on D pairs with the other metric on D + otherDayOffset.
 * Use -1 for preceding-day stress and 0 for same-record-date HRV/resting HR.
 * Dates missing from either source are omitted; no interpolation or index-based pairing.
 */
fun pairedCorrelation(
    sleep: List<TrendPoint>,
    other: List<TrendPoint>,
    targetDate: String?,
    days: Int = 90,
    otherDayOffset: Int = 0,
): MetricCorrelation {
    val target = parseDate(targetDate) ?: return MetricCorrelation(null, 0)
    if (days <= 0) return MetricCorrelation(null, 0)
    val start = target.minusDays(days.toLong() - 1)
    val pairs = sleepMetricPairs(sleep, other, targetDate, days, otherDayOffset)
    fun result(value: Double?) = MetricCorrelation(value, pairs.size, start.toString(), target.toString())
    if (pairs.size < 14) return result(null)
    val meanX = pairs.map { it.first }.average()
    val meanY = pairs.map { it.second }.average()
    var covariance = 0.0
    var varianceX = 0.0
    var varianceY = 0.0
    for ((x, y) in pairs) {
        val dx = x.toDouble() - meanX
        val dy = y.toDouble() - meanY
        covariance += dx * dy
        varianceX += dx * dx
        varianceY += dy * dy
    }
    if (varianceX <= 0.0 || varianceY <= 0.0) return result(null)
    val coefficient = covariance / sqrt(varianceX * varianceY)
    return result(coefficient.takeIf(Double::isFinite)?.coerceIn(-1.0, 1.0))
}

/** Fractions of recorded duration. Awake share is not sleep efficiency or an awakening count. */
fun sleepStageRatios(sleep: SleepSummary?): SleepStageRatios {
    val asleep = sleep?.sleepSeconds?.takeIf { it.isFinite() && it > 0.0 }
        ?: return SleepStageRatios()
    fun stageRatio(value: Double?): Double? = value
        ?.takeIf { it.isFinite() && it >= 0.0 && it <= asleep }
        ?.div(asleep)
    val awake = sleep.awakeSeconds?.takeIf { it.isFinite() && it >= 0.0 }
    return SleepStageRatios(
        deep = stageRatio(sleep.deepSeconds),
        rem = stageRatio(sleep.remSeconds),
        awake = awake?.let { (it / (asleep + it)).takeIf(Double::isFinite) },
    )
}

private fun parseDate(value: String?): LocalDate? =
    value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
