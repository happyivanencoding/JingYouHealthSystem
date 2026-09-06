package com.thegreatnovel.jingyouhealth.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepLabDataTest {
    @Test fun outcomePercentUsesSameDateDenominatorAndExcludesFuture() {
        val trends = Trends(
            sleepHours = listOf(p("2026-09-01", 8f), p("2026-09-02", 6f), p("2026-09-03", 8f)),
            deepHours = listOf(p("2026-09-03", 1f), p("2026-09-02", 1.5f), p("2026-09-01", 2f)),
        )
        assertEquals(listOf(p("2026-09-01", 25f), p("2026-09-02", 25f)), SleepOutcome.DEEP_PERCENT.series(trends, "2026-09-02"))
    }

    @Test fun outcomePercentKeepsMissingAndImpossibleStagesMissing() {
        val dates = (1..6).map { "2026-09-0$it" }
        val trends = Trends(
            sleepHours = dates.zip(listOf(0f, 8f, 8f, 8f, null, 8f)) { date, value -> p(date, value) },
            remHours = dates.zip(listOf(0f, -1f, 9f, null, 2f, 0f)) { date, value -> p(date, value) },
        )
        assertEquals(listOf(null, null, null, null, null, 0f), SleepOutcome.REM_PERCENT.series(trends).map { it.value })
    }

    @Test fun outcomesDeduplicateByDateLastObservationWinsIncludingNull() {
        val result = SleepOutcome.SCORE.series(Trends(sleepScores = listOf(
            p("2026-09-02", 101f), p("2026-09-01", 80f), p("bad", 70f),
            p("2026-09-01", null), p("2026-09-03", Float.POSITIVE_INFINITY), p("2026-09-04", 0f),
        )), "2026-09-04")
        assertEquals(listOf(null, null, null, 0f), result.map { it.value })
        assertEquals("2026-09-01", result.first().date)
        assertTrue(SleepOutcome.DURATION_HOURS.series(Trends(), "bad").isEmpty())
    }

    @Test fun dailyFactorsRejectInvalidValuesAndPreserveDateGaps() {
        val trends = Trends(steps = listOf(p("2026-08-31", 5000f), p("2026-09-02", -1f), p("2026-09-03", 9000f)))
        val result = factorSeries(SleepFactor.STEPS, trends, emptyList(), "2026-09-02")
        assertEquals(listOf(p("2026-08-31", 5000f), p("2026-09-02", null)), result)
        assertTrue(factorSeries(SleepFactor.STEPS, trends, emptyList(), null).isEmpty())
        assertNull(factorSeries(SleepFactor.HRV, Trends(hrv = listOf(p("2026-09-01", 0f))), emptyList(), "2026-09-01").single().value)
    }

    @Test fun recentSleepRequiresThreeConsecutiveNightsAndNeverIncludesNextNight() {
        val trends = Trends(sleepHours = listOf(
            p("2026-08-29", 6f), p("2026-08-30", 7f), p("2026-08-31", 8f),
            p("2026-09-02", 9f), p("2026-09-03", 10f), p("2026-09-04", 11f), p("2026-09-05", 100f),
        ))
        val result = factorSeries(SleepFactor.RECENT_SLEEP_3, trends, emptyList(), "2026-09-04").associate { it.date to it.value }
        assertEquals(7f, result["2026-08-31"]!!, 0.0001f)
        assertNull(result["2026-09-02"])
        assertNull(result["2026-09-03"])
        assertEquals(10f, result["2026-09-04"]!!, 0.0001f)
        assertTrue("2026-09-05" !in result)
    }

    @Test fun recentSleepDoesNotReviveDuplicateReplacedWithMissing() {
        val result = factorSeries(SleepFactor.RECENT_SLEEP_3, Trends(sleepHours = listOf(
            p("2026-09-01", 6f), p("2026-09-02", 7f), p("2026-09-03", 8f), p("2026-09-02", null),
        )), emptyList(), "2026-09-03")
        assertTrue(result.all { it.value == null })
    }

    @Test fun trainingAggregatesEveryActivityAndWeightsHeartRateByDuration() {
        val events = listOf(a("edge", "2026-09-01"), a("one", "2026-09-02", 3600.0, 100.0, 15.0), a("two", "2026-09-02", 7200.0, 160.0, 30.0))
        assertEquals(3f, training(SleepFactor.TRAINING_MINUTES, events)["2026-09-02"]!!, 0.0001f)
        assertEquals(45f, training(SleepFactor.TRAINING_LOAD, events)["2026-09-02"]!!, 0.0001f)
        assertEquals(140f, training(SleepFactor.TRAINING_AVG_HR, events)["2026-09-02"]!!, 0.0001f)
        assertEquals("小时", SleepFactor.TRAINING_MINUTES.unit)
    }

    @Test fun trainingRequiresAllFieldsForEachMetricRatherThanPartialTotals() {
        val events = listOf(a("edge", "2026-09-01"), a("one", "2026-09-02"), a("two", "2026-09-02", duration = null, load = null))
        assertNull(training(SleepFactor.TRAINING_MINUTES, events)["2026-09-02"])
        assertNull(training(SleepFactor.TRAINING_LOAD, events)["2026-09-02"])
        assertNull(training(SleepFactor.TRAINING_AVG_HR, events)["2026-09-02"])
    }

    @Test fun zeroOnlyMeansNoRecordedTrainingInsideConservativeCoverage() {
        val events = listOf(a("edge", "2026-09-01"), a("last", "2026-09-03"))
        val hours = training(SleepFactor.TRAINING_MINUTES, events)
        assertNull(hours["2026-08-31"])
        assertNull(hours["2026-09-01"])
        assertEquals(0f, hours["2026-09-02"]!!, 0f)
        assertNull(hours["2026-09-04"])
        assertNull(training(SleepFactor.TRAINING_AVG_HR, events)["2026-09-02"])
        assertEquals(ActivityCoverage("2026-09-02", "2026-09-03"), activityCoverage(events, "2026-09-05"))
    }

    @Test fun activityDatesKeepGarminLocalDayEvenWithOffset() {
        val events = listOf(a("edge", "2026-08-31"), a("late", "2026-09-01T23:30:00-11:00"), a("early", "2026-09-02T00:30:00+12:00"))
        val values = training(SleepFactor.TRAINING_MINUTES, events)
        assertEquals(1f, values["2026-09-01"]!!, 0f)
        assertEquals(1f, values["2026-09-02"]!!, 0f)
    }

    @Test fun futureActivitiesCannotCreateCoverageOrEnterTotals() {
        val events = listOf(a("edge", "2026-09-01"), a("recent", "2026-09-02"), a("future", "2026-09-06", 100000.0))
        val values = training(SleepFactor.TRAINING_MINUTES, events)
        assertEquals(ActivityCoverage("2026-09-02", "2026-09-02"), activityCoverage(events, "2026-09-05"))
        assertNull(values["2026-09-03"])
        assertTrue("2026-09-06" !in values)
    }

    @Test fun unknownActivityDatesAndEmptyQueriesDoNotFabricateRestDays() {
        val events = listOf(a("edge", "2026-09-01"), a("known", "2026-09-03"), a("unknown", "not-a-date"))
        assertNull(activityCoverage(events, "2026-09-05"))
        assertTrue(training(SleepFactor.TRAINING_MINUTES, events).values.all { it == null })
        assertTrue(training(SleepFactor.TRAINING_MINUTES, emptyList()).values.all { it == null })
        assertNull(activityCoverage(listOf(a("only", "2026-09-01")), "2026-09-05"))
    }

    @Test fun priorDayStressIsAlignedToFollowingSleepDateWithoutTargetDayLeakage() {
        val result = factorSeries(
            SleepFactor.PRIOR_DAY_STRESS,
            Trends(stress = listOf(p("2026-09-01", 30f), p("2026-09-02", 40f), p("2026-09-04", 60f))),
            emptyList(),
            "2026-09-04",
        )
        assertEquals(listOf("2026-09-02", "2026-09-03"), result.map { it.date })
        assertEquals(listOf(30f, 40f), result.map { it.value })
        assertTrue(result.none { it.date == "2026-09-04" && it.value == 60f })
    }

    @Test fun duplicateActivityIdsUseLastVersionAndDoNotDoubleCount() {
        val events = listOf(a("edge", "2026-09-01"), a("same", "2026-09-02", 3600.0), a("same", "2026-09-02", 7200.0))
        assertEquals(2f, training(SleepFactor.TRAINING_MINUTES, events)["2026-09-02"]!!, 0f)
        val missing = events + a("same", "2026-09-02", duration = null)
        assertNull(training(SleepFactor.TRAINING_MINUTES, missing)["2026-09-02"])
    }

    private fun p(date: String, value: Float?) = TrendPoint(date, value)
    private fun a(id: String, date: String, duration: Double? = 3600.0, hr: Double? = 120.0, load: Double? = 20.0) =
        ActivitySummary(id, startTime = date, durationS = duration, avgHr = hr, trainingLoad = load)
    private fun training(factor: SleepFactor, activities: List<ActivitySummary>): Map<String, Float?> =
        factorSeries(factor, Trends(sleepHours = listOf(p("2026-08-31", 8f), p("2026-09-05", 8f))), activities, "2026-09-05")
            .associate { it.date to it.value }
}
