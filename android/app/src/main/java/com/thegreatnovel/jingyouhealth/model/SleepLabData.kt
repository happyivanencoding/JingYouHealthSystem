package com.thegreatnovel.jingyouhealth.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

/** Units are the actual numeric units supplied to the regression, not display-only labels. */
enum class SleepOutcome(val labelChinese: String, val unit: String) {
    DURATION_HOURS("睡眠时长", "小时"),
    SCORE("睡眠评分", "分"),
    DEEP_HOURS("深睡时长", "小时"),
    REM_HOURS("REM 时长", "小时"),
    DEEP_PERCENT("深睡占比", "%"),
    REM_PERCENT("REM 占比", "%");

    /** Supply throughDate when exploring a historical night. No current-night interpolation. */
    fun series(trends: Trends, throughDate: String? = null): List<TrendPoint> {
        val cutoff = throughDate?.let { labDate(it) ?: return emptyList() }
        return when (this) {
            DURATION_HOURS -> normalized(trends.sleepHours, cutoff)
            SCORE -> normalized(trends.sleepScores, cutoff) { it in 0f..100f }
            DEEP_HOURS -> normalized(trends.deepHours, cutoff)
            REM_HOURS -> normalized(trends.remHours, cutoff)
            DEEP_PERCENT -> stagePercent(trends.deepHours, trends.sleepHours, cutoff)
            REM_PERCENT -> stagePercent(trends.remHours, trends.sleepHours, cutoff)
        }
    }
}

enum class SleepFactor(
    val labelChinese: String,
    val unit: String,
    val contemporaneous: Boolean,
    val timingNoteChinese: String,
) {
    STRESS("日间压力", "分", false, "Garmin 压力反映身体信号，不等同于心理压力；默认与下一晚睡眠配对"),
    STEPS("步数", "步", false, "每日步数，默认与下一晚睡眠配对"),
    HRV("夜间 HRV", "ms", true, "HRV 与睡眠同时记录，同日关系不能解释为睡前原因"),
    RHR("静息心率", "bpm", true, "静息心率是每日汇总，可能与睡眠时段重叠"),
    BEDTIME_DELAY("晚睡幅度", "小时", true, "当晚入睡相对个人习惯的偏晚幅度；该时刻已发生，clock 预设可用 lag0"),
    HABITUAL_WAKE("平时醒来时刻", "小时", false, "只使用目标日前 42 天习惯醒来时刻，不使用目标夜实际醒来时间"),
    PRIOR_DAY_STRESS("前一日身体压力", "分", false, "将原始压力日期向后对齐到下一晚睡眠日，严格只使用前一天记录"),
    BATTERY_DRAINED("身体电量消耗", "点", false, "Garmin 复合指标中的当天电量消耗，与其他身体信号并不独立"),
    BATTERY_CHARGED("身体电量补充", "点", true, "Garmin 复合指标中的电量补充可能包含睡眠恢复，不能当作独立的睡前原因"),
    RECENT_SLEEP_3("近 3 夜睡眠", "小时", false, "截至记录日连续 3 夜的平均时长，前一天配对时不包含目标睡眠"),
    // Retain the requested factor identifier; values are hours, consistent with sleep duration.
    TRAINING_MINUTES("运动时长", "小时", false, "按运动发生地的日期汇总运动时长"),
    TRAINING_LOAD("训练负荷", "AU", false, "同一天所有已记录运动的训练负荷之和"),
    TRAINING_AVG_HR("运动平均心率", "bpm", false, "按运动时长加权的平均心率，无运动日没有心率值");

    val noteKey: String get() = timingNoteChinese
}

/**
 * Series stay in their original calendar dates. The regression applies lag and differencing.
 * activities must be the complete response to the latest-200 /api/activities query, not the
 * dashboard's five-row recentActivities preview. The endpoint has no sync-through metadata.
 */
fun factorSeries(
    factor: SleepFactor,
    trends: Trends,
    activities: List<ActivitySummary>,
    throughDate: String?,
): List<TrendPoint> {
    val cutoff = labDate(throughDate) ?: return emptyList()
    return when (factor) {
        SleepFactor.STRESS -> normalized(trends.stress, cutoff) { it in 0f..100f }
        SleepFactor.STEPS -> normalized(trends.steps, cutoff)
        SleepFactor.HRV -> normalized(trends.hrv, cutoff) { it > 0f }
        SleepFactor.RHR -> normalized(trends.restingHr, cutoff) { it > 0f }
        SleepFactor.BEDTIME_DELAY -> bedtimeDelaySeries(trends.sleepClocks, cutoff.toString())
        SleepFactor.HABITUAL_WAKE -> habitualWakeSeries(trends.sleepClocks, cutoff.toString())
        SleepFactor.PRIOR_DAY_STRESS -> shiftToFollowingSleepDay(trends.stress, cutoff)
        SleepFactor.BATTERY_DRAINED -> normalized(trends.bodyBatteryDrained, cutoff)
        SleepFactor.BATTERY_CHARGED -> normalized(trends.bodyBatteryCharged, cutoff)
        SleepFactor.RECENT_SLEEP_3 -> recentSleep(trends.sleepHours, cutoff)
        SleepFactor.TRAINING_MINUTES, SleepFactor.TRAINING_LOAD, SleepFactor.TRAINING_AVG_HR ->
            trainingSeries(factor, trends, activities, cutoff)
    }
}

/** Align a source day D-1 to the following sleep day D without consulting any target-day value. */
private fun shiftToFollowingSleepDay(points: List<TrendPoint>, cutoff: LocalDate): List<TrendPoint> =
    normalized(points, cutoff) { it in 0f..100f }.mapNotNull { point ->
        val sourceDate = labDate(point.date) ?: return@mapNotNull null
        val targetDate = sourceDate.plusDays(1)
        if (targetDate > cutoff) null else TrendPoint(targetDate.toString(), point.value)
    }.associateBy { it.date }.toSortedMap().values.toList()

/**
 * Inclusive conservative interval where absent activities can be called zero.
 * The first returned day can be cut in half by the 200-row cap; the time after the latest
 * activity has no explicit sync coverage. Neither boundary is inferred to be a rest day.
 */
data class ActivityCoverage(val fromDate: String, val throughDate: String)

fun activityCoverage(activities: List<ActivitySummary>, throughDate: String?): ActivityCoverage? {
    val cutoff = labDate(throughDate) ?: return null
    val unique = uniqueActivities(activities).filter { activity ->
        val date = activityLocalDate(activity.startTime)
        date == null || date <= cutoff
    }
    if (unique.isEmpty() || unique.any { activityLocalDate(it.startTime) == null }) return null
    val dates = unique.mapNotNull { activityLocalDate(it.startTime) }
    val firstComplete = dates.minOrNull()!!.plusDays(1)
    val lastComplete = minOf(dates.maxOrNull()!!, cutoff)
    return if (firstComplete <= lastComplete) ActivityCoverage(firstComplete.toString(), lastComplete.toString()) else null
}

private fun recentSleep(points: List<TrendPoint>, cutoff: LocalDate): List<TrendPoint> {
    val records = normalized(points, cutoff)
    val values = records.associate { LocalDate.parse(it.date) to it.value }
    return records.map { point ->
        val date = LocalDate.parse(point.date)
        val nights = (0L..2L).map { values[date.minusDays(it)] }
        val mean = if (nights.all { it != null }) nights.filterNotNull().map(Float::toDouble).average().toFloat() else null
        point.copy(value = mean?.takeIf(Float::isFinite))
    }
}

private fun trainingSeries(
    factor: SleepFactor,
    trends: Trends,
    activities: List<ActivitySummary>,
    cutoff: LocalDate,
): List<TrendPoint> {
    val unique = uniqueActivities(activities)
    val dated = unique.mapNotNull { activity ->
        activityLocalDate(activity.startTime)?.takeIf { it <= cutoff }?.let { it to activity }
    }
    val coverage = activityCoverage(unique, cutoff.toString())
    val from = coverage?.fromDate?.let(LocalDate::parse)
    val through = coverage?.throughDate?.let(LocalDate::parse)
    val grouped = dated.filter { it.first <= cutoff }.groupBy({ it.first }, { it.second })
    val earliestObserved = dated.minOfOrNull { it.first }
    val candidateDates = (trends.sleepHours + trends.sleepScores + trends.stress + trends.steps)
        .mapNotNull { labDate(it.date)?.takeIf { date -> date <= cutoff } } + grouped.keys
    val first = candidateDates.minOrNull() ?: return emptyList()
    val hasUndatedRecords = unique.any { activityLocalDate(it.startTime) == null }
    return generateSequence(first) { day -> day.plusDays(1).takeIf { it <= cutoff } }.map { day ->
        val events = grouped[day].orEmpty()
        val covered = from != null && through != null && day >= from && day <= through
        val value = when {
            hasUndatedRecords -> null // Cannot safely assign an undated activity to any daily total.
            day == earliestObserved -> null // The earliest returned date may be a partial day.
            events.isEmpty() -> if (covered && factor != SleepFactor.TRAINING_AVG_HR) 0f else null
            !covered -> null
            else -> aggregateTraining(factor, events)
        }
        TrendPoint(day.toString(), value)
    }.toList()
}

private fun aggregateTraining(factor: SleepFactor, events: List<ActivitySummary>): Float? {
    val result: Double? = when (factor) {
        SleepFactor.TRAINING_MINUTES -> {
            val durations = events.map { it.durationS?.takeIf { value -> value.isFinite() && value >= 0.0 } }
            if (durations.any { it == null }) null else durations.filterNotNull().sum() / 3600.0
        }
        SleepFactor.TRAINING_LOAD -> {
            val loads = events.map { it.trainingLoad?.takeIf { value -> value.isFinite() && value >= 0.0 } }
            if (loads.any { it == null }) null else loads.filterNotNull().sum()
        }
        SleepFactor.TRAINING_AVG_HR -> {
            val durations = events.map { it.durationS?.takeIf { value -> value.isFinite() && value > 0.0 } }
            val rates = events.map { it.avgHr?.takeIf { value -> value.isFinite() && value > 0.0 } }
            if (durations.any { it == null } || rates.any { it == null }) null
            else {
                val totalDuration = durations.filterNotNull().sum()
                if (totalDuration <= 0.0 || !totalDuration.isFinite()) null
                else events.indices.sumOf { durations[it]!! * rates[it]!! } / totalDuration
            }
        }
        else -> null
    }
    return result?.takeIf(Double::isFinite)?.toFloat()?.takeIf(Float::isFinite)
}

private fun stagePercent(stage: List<TrendPoint>, total: List<TrendPoint>, cutoff: LocalDate?): List<TrendPoint> {
    val stages = normalized(stage, cutoff).associate { it.date to it.value }
    val totals = normalized(total, cutoff).associate { it.date to it.value }
    return (stages.keys + totals.keys).sorted().map { date ->
        val numerator = stages[date]
        val denominator = totals[date]
        val percent = if (numerator != null && denominator != null && denominator > 0f && numerator <= denominator) {
            numerator.toDouble().div(denominator) * 100.0
        } else null
        TrendPoint(date, percent?.toFloat()?.takeIf(Float::isFinite))
    }
}

private fun normalized(
    points: List<TrendPoint>,
    cutoff: LocalDate?,
    valid: (Float) -> Boolean = { it >= 0f },
): List<TrendPoint> = points.mapNotNull { point ->
    val date = labDate(point.date) ?: return@mapNotNull null
    if (cutoff != null && date > cutoff) return@mapNotNull null
    date to TrendPoint(date.toString(), point.value?.takeIf { it.isFinite() && valid(it) })
}.toMap().toSortedMap().values.toList()

private fun uniqueActivities(activities: List<ActivitySummary>): List<ActivitySummary> {
    // Last occurrence wins, including null fields; never revive obsolete earlier values.
    val named = linkedMapOf<String, ActivitySummary>()
    val unnamed = mutableListOf<ActivitySummary>()
    activities.forEach { activity ->
        if (activity.id.isBlank()) unnamed += activity else named[activity.id] = activity
    }
    return named.values.toList() + unnamed
}

/** start_time is startTimeLocal in sync_garmin_history.py; never shift it into phone time. */
private fun activityLocalDate(timestamp: String?): LocalDate? {
    if (timestamp.isNullOrBlank()) return null
    val local = timestamp.trim().replace(' ', 'T')
    return runCatching { LocalDateTime.parse(local).toLocalDate() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(local).toLocalDate() }.getOrNull()
        ?: runCatching { LocalDate.parse(local) }.getOrNull()
}

private fun labDate(value: String?): LocalDate? =
    value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
