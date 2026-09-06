package com.thegreatnovel.jingyouhealth.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class SleepTimingNight(val date: String, val bedtimeHour: Double, val wakeHour: Double)
data class SleepTimingSummary(
    val nights: List<SleepTimingNight>,
    val usualBedtime: Double?,
    val usualWake: Double?,
    val lateCount: Int = 0,
    val otherCount: Int = 0,
    val bedtimeShift: Double? = null,
    val wakeShift: Double? = null,
    val lateSleep: Double? = null,
    val otherSleep: Double? = null,
    val lateDeep: Double? = null,
    val otherDeep: Double? = null,
    val lateRem: Double? = null,
    val otherRem: Double? = null,
)

/** Keep recorded local wall clocks. Never convert a historical night to the phone's current zone. */
fun sleepTimingNights(clocks: List<SleepClockPoint>, throughDate: String?): List<SleepTimingNight> {
    val cutoff = runCatching { LocalDate.parse(throughDate) }.getOrNull() ?: return emptyList()
    return clocks.associateBy { it.date }.values.mapNotNull { point ->
        if (point.offsetChanged) return@mapNotNull null
        val date = runCatching { LocalDate.parse(point.date) }.getOrNull() ?: return@mapNotNull null
        if (date > cutoff) return@mapNotNull null
        val start = runCatching { LocalDateTime.parse(point.startLocal) }.getOrNull() ?: return@mapNotNull null
        val end = runCatching { LocalDateTime.parse(point.endLocal) }.getOrNull() ?: return@mapNotNull null
        val duration = ChronoUnit.MINUTES.between(start, end)
        // Analyse main overnight records anchored on the recorded waking date, not naps/ambiguous offsets.
        if (end.toLocalDate() != date || duration <= 0 || duration > 24 * 60) return@mapNotNull null
        val bed = ChronoUnit.MINUTES.between(date.atStartOfDay(), start) / 60.0
        val wake = ChronoUnit.MINUTES.between(date.atStartOfDay(), end) / 60.0
        if (bed !in -12.0..12.0 || wake !in 0.0..18.0) return@mapNotNull null
        SleepTimingNight(point.date, bed, wake)
    }.sortedBy { it.date }
}

fun bedtimeDelaySeries(clocks: List<SleepClockPoint>, throughDate: String?): List<TrendPoint> {
    val nights = sleepTimingNights(clocks, throughDate)
    return nights.map { night ->
        val reference = priorTiming(nights, night.date).map { it.bedtimeHour }
        TrendPoint(night.date, if (reference.size >= 7) (night.bedtimeHour - timingMedian(reference)!!).toFloat() else null)
    }
}

fun habitualWakeSeries(clocks: List<SleepClockPoint>, throughDate: String?): List<TrendPoint> {
    val nights = sleepTimingNights(clocks, throughDate)
    return nights.map { night ->
        val reference = priorTiming(nights, night.date).map { it.wakeHour }
        TrendPoint(night.date, if (reference.size >= 7) timingMedian(reference)?.toFloat() else null)
    }
}

private fun priorTiming(nights: List<SleepTimingNight>, date: String): List<SleepTimingNight> {
    val start = LocalDate.parse(date).minusDays(42).toString()
    return nights.filter { it.date >= start && it.date < date }
}

fun sleepTimingSummary(trends: Trends, throughDate: String?, days: Int = 90): SleepTimingSummary {
    val end = runCatching { LocalDate.parse(throughDate) }.getOrNull() ?: return SleepTimingSummary(emptyList(), null, null)
    val nights = sleepTimingNights(trends.sleepClocks, throughDate).filter { it.date >= end.minusDays(days.toLong() - 1).toString() }
    val recent = nights.filter { it.date >= end.minusDays(41).toString() }
    val usualBed = timingMedian(recent.map { it.bedtimeHour })
    val usualWake = timingMedian(recent.map { it.wakeHour })
    val initial = SleepTimingSummary(nights, usualBed, usualWake)
    if (nights.size < 28) return initial
    val sorted = nights.sortedWith(compareBy<SleepTimingNight> { it.bedtimeHour }.thenBy { it.date })
    val late = sorted.takeLast(sorted.size / 4)
    val other = sorted.dropLast(sorted.size / 4)
    val shift = timingMedian(late.map { it.bedtimeHour })!! - timingMedian(other.map { it.bedtimeHour })!!
    if (shift < 0.25) return initial // Essentially identical clock times do not support a late-night comparison.
    fun medianFor(group: List<SleepTimingNight>, points: List<TrendPoint>): Double? {
        val dates = group.map { it.date }.toSet()
        val values = points.associateBy { it.date }.values.filter { it.date in dates }.mapNotNull { it.value?.takeIf { value -> value.isFinite() && value >= 0f }?.toDouble() }
        return if (values.size >= 5) timingMedian(values) else null
    }
    return initial.copy(
        lateCount = late.size, otherCount = other.size,
        bedtimeShift = shift,
        wakeShift = timingMedian(late.map { it.wakeHour })!! - timingMedian(other.map { it.wakeHour })!!,
        lateSleep = medianFor(late, trends.sleepHours), otherSleep = medianFor(other, trends.sleepHours),
        lateDeep = medianFor(late, trends.deepHours), otherDeep = medianFor(other, trends.deepHours),
        lateRem = medianFor(late, trends.remHours), otherRem = medianFor(other, trends.remHours),
    )
}

private fun timingMedian(values: List<Double>): Double? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}
