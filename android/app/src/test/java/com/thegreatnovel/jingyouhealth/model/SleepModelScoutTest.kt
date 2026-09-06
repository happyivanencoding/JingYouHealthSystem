package com.thegreatnovel.jingyouhealth.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.math.sin
import kotlin.math.cos

class SleepModelScoutTest {
    @Test fun explicitDateBoundaryIsSharedDespiteMissingRows() {
        val data = data()
        val result = fitSleepRegression(data.sleepHours, data.steps.filterIndexed { i, _ -> i % 13 != 0 }, data.stress,
            date(179), 180, splitDate = date(130))
        assertEquals(RegressionStatus.READY, result.status)
        assertTrue(result.trainEndDate!! <= date(130))
        assertTrue(result.holdoutStartDate!! > date(130))
        assertTrue(result.holdout.all { it.date > date(130) })
    }

    @Test fun newestVerificationOutcomesNeverChooseOrSortCandidates() {
        val original = data()
        val changed = original.copy(sleepHours = original.sleepHours.mapIndexed { i, p -> if (i >= 144) p.copy(value = p.value!! + 5f) else p })
        val first = proposeSleepConfigurations(SleepOutcome.DURATION_HOURS, original, emptyList(), date(179))
        val second = proposeSleepConfigurations(SleepOutcome.DURATION_HOURS, changed, emptyList(), date(179))
        assertTrue(first.candidates.isNotEmpty())
        assertEquals(first.candidates.map { it.config.id }, second.candidates.map { it.config.id })
        first.candidates.zip(second.candidates).forEach { (a, b) ->
            assertEquals(a.selectionGain, b.selectionGain, 1e-10)
            assertTrue(a.selection.holdoutEndDate!! <= first.verificationCutoff!!)
            assertTrue(a.verification.holdoutStartDate!! > first.verificationCutoff!!)
        }
        assertTrue(first.candidates.zip(second.candidates).any { (a, b) -> a.verification.holdoutMAE != b.verification.holdoutMAE })
    }

    @Test fun candidatesSeparateDifferenceTargetsAndOnlyUseEarlierFactors() {
        val report = proposeSleepConfigurations(SleepOutcome.DURATION_HOURS, data(), emptyList(), date(179))
        assertEquals(216, report.testedCount)
        assertTrue(report.eligibleCount >= report.candidates.size)
        assertTrue(report.candidates.all { it.config.lagDays in 1..3 && it.config.differenceOrder in 0..2 && it.selection.holdoutN >= 12 })
        for (order in 0..2) {
            val group = report.candidates.filter { it.config.differenceOrder == order }
            assertTrue(group.size <= 3)
            assertEquals(group.size, group.map { it.config.factorA to it.config.factorB }.distinct().size)
            assertEquals(group.map { it.selectionGain }.sortedDescending(), group.map { it.selectionGain })
        }
    }

    @Test fun theSuppliedUserDataOwnsEveryResultAndShortHistoryStaysEmpty() {
        val first = data()
        val other = first.copy(sleepHours = first.sleepHours.map { it.copy(value = it.value!! + 2f) })
        val a = proposeSleepConfigurations(SleepOutcome.DURATION_HOURS, first, emptyList(), date(179))
        val b = proposeSleepConfigurations(SleepOutcome.DURATION_HOURS, other, emptyList(), date(179))
        assertTrue(a.candidates.isNotEmpty() && b.candidates.isNotEmpty())
        assertNotEquals(a.candidates.first().verification.trainingMean, b.candidates.first().verification.trainingMean)
        assertTrue(proposeSleepConfigurations(SleepOutcome.DURATION_HOURS, first, emptyList(), date(35)).candidates.isEmpty())
    }

    @Test fun aCancelledSnapshotStopsBeforeSearchingTheRemainingConfigurations() {
        var checkpoints = 0
        try {
            proposeSleepConfigurations(SleepOutcome.DURATION_HOURS, data(), emptyList(), date(179)) {
                checkpoints++
                if (checkpoints == 4) throw IllegalStateException("cancelled snapshot")
            }
            fail("Expected cancellation to propagate")
        } catch (e: IllegalStateException) {
            assertEquals("cancelled snapshot", e.message)
            assertEquals(4, checkpoints)
        }
    }

    private fun data(): Trends {
        val steps = (0..179).map { p(it, (9000 + 2600 * sin(it * 1.13) + 1800 * cos(it * 0.34)).toFloat()) }
        val stress = (0..179).map { p(it, (35 + 8 * sin(it * 0.69) + 5 * cos(it * 0.21)).toFloat()) }
        val sleep = (0..179).map { i ->
            val a = steps[(i - 1).coerceAtLeast(0)].value!! / 10000.0
            val b = stress[(i - 1).coerceAtLeast(0)].value!! / 40.0
            p(i, (5.8 + 0.7 * a - 0.4 * b + 0.5 * a * b + 0.08 * sin(i * 2.71)).toFloat())
        }
        return Trends(sleepHours = sleep, steps = steps, stress = stress,
            hrv = (0..179).map { p(it, (55 + 7 * sin(it * 0.4)).toFloat()) },
            restingHr = (0..179).map { p(it, (57 + 4 * cos(it * 0.37)).toFloat()) })
    }
    private fun date(i: Int) = LocalDate.of(2026, 1, 1).plusDays(i.toLong()).toString()
    private fun p(i: Int, value: Float) = TrendPoint(date(i), value)
}
