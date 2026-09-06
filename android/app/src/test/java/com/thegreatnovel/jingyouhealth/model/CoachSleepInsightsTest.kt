package com.thegreatnovel.jingyouhealth.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachSleepInsightsTest {
    @Test fun weakVerificationIsRetainedAndNegativeImportanceIsPreserved() {
        val first = candidate(
            factorA = SleepFactor.STEPS,
            selection = result(mae = 0.4, reference = 1.0),
            verification = result(mae = 2.0, reference = 1.0),
        )
        val report = PersonalSleepReport(
            outcome = SleepOutcome.DURATION_HOURS,
            throughDate = "2026-06-30",
            verificationCutoff = "2026-06-18",
            candidates = listOf(first),
        )
        val fitted = result(
            mae = 2.0,
            reference = 1.0,
            importance = listOf(
                FeatureImportance("factor_a", -0.25, 0.1),
                FeatureImportance("weekend", 0.4, 0.2),
            ),
        )

        val model = buildCoachSleepModel(report, fitted)

        assertEquals(RegressionStatus.READY, model.status)
        assertEquals(2.0, model.mae!!, 0.0)
        assertEquals(1.0, model.referenceMae!!, 0.0)
        assertEquals(listOf("factor_a", "weekend"), model.featureImportance.map { it.feature })
        assertEquals(-0.25, model.featureImportance.first().maeIncrease, 0.0)
    }

    @Test fun firstExistingRandomForestOrderZeroCandidateIsUsedWithoutReranking() {
        val first = candidate(
            factorA = SleepFactor.STEPS,
            selection = result(mae = 0.8, reference = 1.0),
        )
        val laterAndBetter = candidate(
            factorA = SleepFactor.RHR,
            selection = result(mae = 0.1, reference = 1.0),
        )
        val report = PersonalSleepReport(
            outcome = SleepOutcome.DURATION_HOURS,
            throughDate = "2026-06-30",
            candidates = listOf(first, laterAndBetter),
        )

        val model = buildCoachSleepModel(report, result(mae = 0.7, reference = 1.0))

        assertEquals(SleepFactor.STEPS, model.factorA)
        assertEquals(0.8, model.selectionMae!!, 0.0)
    }

    @Test fun emptyReportReturnsTypedInsufficientDataModel() {
        val model = buildCoachSleepModel(PersonalSleepReport(SleepOutcome.REM_HOURS, "2026-06-30"), result())

        assertEquals(SleepOutcome.REM_HOURS, model.outcome)
        assertEquals(RegressionStatus.INSUFFICIENT_DATA, model.status)
        assertEquals(SleepAlgorithm.RANDOM_FOREST, model.algorithm)
        assertNull(model.factorA)
        assertEquals(0, model.trainN)
        assertEquals(0, model.validationN)
    }

    @Test fun snapshotLimitsModelsAndExcludesFutureClockRecords() {
        val trends = Trends(sleepClocks = listOf(clock(0), clock(2), clock(10)))
        val models = List(6) { CoachSleepModel(SleepOutcome.DURATION_HOURS, RegressionStatus.INSUFFICIENT_DATA) }

        val snapshot = buildCoachSleepSnapshot(
            throughDate = date(2),
            models = models,
            trends = trends,
            frenchHolidays = true,
            generatedAt = "2026-07-01T00:00:00Z",
        )

        assertEquals(1, snapshot.schemaVersion)
        assertEquals("android_personal_sleep_v1", snapshot.source)
        assertEquals(5, snapshot.models.size)
        assertEquals(2, snapshot.timing.nightCount)
        assertTrue(snapshot.timing.usualWakeHour!!.isFinite())
    }

    private fun candidate(
        factorA: SleepFactor,
        selection: RegressionResult = result(),
        verification: RegressionResult = result(),
    ) = PersonalSleepCandidate(
        config = SleepCandidateConfig(
            outcome = SleepOutcome.DURATION_HOURS,
            factorA = factorA,
            factorB = SleepFactor.STRESS,
            differenceOrder = 0,
            lagDays = 1,
            interaction = true,
            algorithm = SleepAlgorithm.RANDOM_FOREST,
            featurePack = SleepFeaturePack.ENRICHED,
        ),
        selection = selection,
        verification = verification,
        selectionGain = 0.1,
        verificationGain = null,
    )

    private fun result(
        mae: Double? = null,
        reference: Double? = null,
        importance: List<FeatureImportance> = emptyList(),
    ) = RegressionResult(
        status = if (mae == null) RegressionStatus.INSUFFICIENT_DATA else RegressionStatus.READY,
        algorithm = SleepAlgorithm.RANDOM_FOREST,
        trainN = if (mae == null) 0 else 60,
        holdoutN = if (mae == null) 0 else 12,
        holdoutStartDate = "2026-06-19",
        holdoutEndDate = "2026-06-30",
        holdoutMAE = mae,
        controlMAE = reference,
        featureImportances = importance,
    )

    private fun date(offset: Int): String = LocalDate.of(2026, 7, 1).plusDays(offset.toLong()).toString()

    private fun clock(offset: Int): SleepClockPoint {
        val date = LocalDate.of(2026, 7, 1).plusDays(offset.toLong())
        return SleepClockPoint(
            date = date.toString(),
            startLocal = date.minusDays(1).atTime(23, 0).toString(),
            endLocal = date.atTime(7, 0).toString(),
        )
    }
}
