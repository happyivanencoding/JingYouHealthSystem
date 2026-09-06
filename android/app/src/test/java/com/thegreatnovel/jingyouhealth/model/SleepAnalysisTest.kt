package com.thegreatnovel.jingyouhealth.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepAnalysisTest {
    @Test fun baselineUsesCalendarWindowAndExcludesTargetAndFuture() {
        val result = baseline(
            listOf(
                point("2026-08-08", 100f),
                point("2026-08-09", 6f),
                point("2026-09-05", 8f),
                point("2026-09-06", 1f),
                point("2026-09-07", 200f),
            ),
            "2026-09-06",
        )!!
        assertEquals(2, result.sampleCount)
        assertEquals(7.0, result.mean, 0.0001)
        assertEquals("2026-08-09", result.startDate)
        assertEquals("2026-09-05", result.endDate)
    }

    @Test fun baselinePreservesMissingnessAndIgnoresInvalidSamples() {
        assertNull(baseline(listOf(point("2026-09-05", null)), "2026-09-06"))
        assertNull(baseline(listOf(point("2026-09-05", Float.NaN)), "2026-09-06"))
        assertNull(baseline(listOf(point("2026-09-05", Float.POSITIVE_INFINITY)), "2026-09-06"))
        assertNull(baseline(listOf(point("invalid", 6f)), "2026-09-06"))
        assertNull(baseline(listOf(point("2026-09-05", 6f)), null))
    }

    @Test fun baselineCountsEachDateOnceAndComputesMedian() {
        val result = baseline(
            listOf(point("2026-09-03", 1f), point("2026-09-04", 6f), point("2026-09-04", 7f), point("2026-09-05", 10f)),
            "2026-09-06",
        )!!
        assertEquals(3, result.sampleCount)
        assertEquals(7.0, result.median, 0.0001)
    }

    @Test fun baselineQuartilesLinearlyInterpolateOrderedValidValues() {
        val records = series(8).reversed() + point("2026-08-09", null) + point("2026-08-10", 999f)
        val result = baseline(records, "2026-08-10")!!
        assertEquals(8, result.sampleCount)
        assertEquals(2.75, result.lowerQuartile, 0.0001)
        assertEquals(4.5, result.median, 0.0001)
        assertEquals(6.25, result.upperQuartile, 0.0001)
    }

    @Test fun baselineQuartilesSupportSingleRecordWithoutImposingUiSampleThreshold() {
        val result = baseline(listOf(point("2026-09-05", 7f)), "2026-09-06")!!
        assertEquals(1, result.sampleCount)
        assertEquals(7.0, result.lowerQuartile, 0.0001)
        assertEquals(7.0, result.upperQuartile, 0.0001)
    }

    @Test fun calendarWindowKeepsNullGapsAndHandlesMonthBoundary() {
        val result = calendarWindow(listOf(point("2026-08-31", 6f), point("2026-09-01", null), point("2026-09-02", 8f)), "2026-09-01", 2)
        assertEquals(listOf("2026-08-31", "2026-09-01"), result.map { it.date })
        assertNull(result.last().value)
        assertTrue(calendarWindow(result, "bad-date", 7).isEmpty())
    }

    @Test fun correlationAlignsPrecedingDayByDateRegardlessOfArrayOrder() {
        val sleep = series(14)
        val precedingDay = sleep.reversed().map { TrendPoint(LocalDate.parse(it.date).minusDays(1).toString(), it.value!! * 2) }
        val result = pairedCorrelation(sleep, precedingDay, "2026-08-14", otherDayOffset = -1)
        assertEquals(14, result.sampleCount)
        assertEquals(1.0, result.coefficient!!, 0.0001)
    }

    @Test fun correlationDoesNotFillMissingDatesOrShowThirteenPairs() {
        val sleep = series(14)
        val result = pairedCorrelation(sleep, sleep.filterIndexed { index, _ -> index != 5 }, "2026-08-14")
        assertEquals(13, result.sampleCount)
        assertNull(result.coefficient)
    }

    @Test fun correlationExcludesFutureAndOutOfWindowRecords() {
        val values = series(20)
        val result = pairedCorrelation(values, values, "2026-08-16", days = 14)
        assertEquals(14, result.sampleCount)
        assertEquals("2026-08-03", result.startDate)
        assertEquals(1.0, result.coefficient!!, 0.0001)
    }

    @Test fun correlationRejectsNonFiniteValuesAndZeroVariance() {
        val values = series(14)
        val nonFinite = values.dropLast(1) + values.last().copy(value = Float.NaN)
        assertEquals(13, pairedCorrelation(values, nonFinite, "2026-08-14").sampleCount)
        assertNull(pairedCorrelation(values, values.map { it.copy(value = 10f) }, "2026-08-14").coefficient)
        assertNull(pairedCorrelation(values, values, null).coefficient)
    }

    @Test fun scatterPairsAlignPreviousDayKeepCalendarWindowAndOmitMissingDates() {
        val sleep = listOf(point("2026-08-01", 6f), point("2026-08-02", 7f), point("2026-08-03", 8f), point("2026-08-04", 9f), point("2026-08-05", 10f))
        val stress = listOf(point("2026-08-03", 20f), point("2026-07-31", 90f), point("2026-08-01", 60f), point("2026-08-04", 10f))
        val pairs = sleepMetricPairs(sleep, stress, "2026-08-04", days = 3, otherDayOffset = -1)
        assertEquals(listOf(7f to 60f, 9f to 20f), pairs)
        assertEquals(pairs.size, pairedCorrelation(sleep, stress, "2026-08-04", days = 3, otherDayOffset = -1).sampleCount)
    }

    @Test fun scatterAndCorrelationUseTheSameDeduplicatedFiniteObservations() {
        val sleep = series(17) + point("2026-08-01", 2f)
        val other = series(17) + point("2026-08-03", null) + point("2026-08-04", Float.NaN) + point("2026-08-05", Float.POSITIVE_INFINITY)
        val pairs = sleepMetricPairs(sleep, other, "2026-08-17")
        val result = pairedCorrelation(sleep, other, "2026-08-17")
        assertEquals(14, pairs.size)
        assertEquals(2f to 1f, pairs.first())
        assertEquals(pairs.size, result.sampleCount)
        assertTrue(result.coefficient != null)
        assertTrue(sleepMetricPairs(sleep, other, null).isEmpty())
    }

    @Test fun ratiosUseAsleepDenominatorAndAwakeUsesTotalRecordedDuration() {
        val ratios = sleepStageRatios(SleepSummary(sleepSeconds = 3600.0, deepSeconds = 900.0, remSeconds = 720.0, awakeSeconds = 400.0))
        assertEquals(0.25, ratios.deep!!, 0.0001)
        assertEquals(0.2, ratios.rem!!, 0.0001)
        assertEquals(0.1, ratios.awake!!, 0.0001)
    }

    @Test fun ratiosNeverConvertMissingDataToZeroOrClampInvalidDurations() {
        assertEquals(SleepStageRatios(), sleepStageRatios(null))
        assertEquals(SleepStageRatios(), sleepStageRatios(SleepSummary(sleepSeconds = 0.0, awakeSeconds = 100.0)))
        val ratios = sleepStageRatios(SleepSummary(sleepSeconds = 3600.0, deepSeconds = 4000.0, remSeconds = -1.0))
        assertNull(ratios.deep)
        assertNull(ratios.rem)
        assertNull(ratios.awake)
    }

    private fun point(date: String, value: Float?) = TrendPoint(date, value)
    private fun series(count: Int) = (0 until count).map {
        TrendPoint(LocalDate.of(2026, 8, 1).plusDays(it.toLong()).toString(), (it + 1).toFloat())
    }
}
