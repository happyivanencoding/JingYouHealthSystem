package com.thegreatnovel.jingyouhealth.model

import java.time.LocalDate

/** Extra recorded-history inputs, lagged one day before a target sleep date. */
fun sleepContextSeries(trends: Trends): List<FeatureSeries> = listOf(
    FeatureSeries("context_hrv_change", "前夜 HRV 的变化", consecutiveDifference(trends.hrv, 1), 1),
    FeatureSeries("context_rhr_change", "前日静息心率的变化", consecutiveDifference(trends.restingHr, 1), 1),
    FeatureSeries("context_stress_mean7", "前日压力的近周均值", observedMeanSeven(trends.stress), 1),
)

fun filterSleepContext(context: List<FeatureSeries>, a: SleepFactor, b: SleepFactor): List<FeatureSeries> {
    val selected = setOf(a, b)
    return context.filterNot {
        (it.key == "context_hrv_change" && SleepFactor.HRV in selected) ||
            (it.key == "context_rhr_change" && SleepFactor.RHR in selected) ||
            (it.key == "context_stress_mean7" && (SleepFactor.STRESS in selected || SleepFactor.PRIOR_DAY_STRESS in selected))
    }
}

private fun observedMeanSeven(points: List<TrendPoint>): List<TrendPoint> {
    val byDate = points.associate { it.date to it.value?.takeIf(Float::isFinite) }
    return points.map { point ->
        val date = runCatching { LocalDate.parse(point.date) }.getOrNull()
        val values = date?.let { d -> (0L..6L).map { byDate[d.minusDays(it).toString()] } }
        TrendPoint(point.date, if (values != null && values.all { it != null }) values.filterNotNull().average().toFloat() else null)
    }.distinctBy { it.date }
}
