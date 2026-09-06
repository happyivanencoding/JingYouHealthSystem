package com.thegreatnovel.jingyouhealth.model

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepRegressionTest {
    @Test fun differencesZeroThroughThreePreserveDatesAndUseBackwardCalendarDifferences() {
        val source = (0..6).map { point(it, (it * it * it).toFloat()) }
        val expected = listOf(
            listOf(0f, 1f, 8f, 27f, 64f, 125f, 216f),
            listOf(null, 1f, 7f, 19f, 37f, 61f, 91f),
            listOf(null, null, 6f, 12f, 18f, 24f, 30f),
            listOf(null, null, null, 6f, 6f, 6f, 6f),
        )
        for (order in 0..3) {
            val actual = consecutiveDifference(source, order)
            assertEquals(source.map { it.date }, actual.map { it.date })
            assertEquals(expected[order], actual.map { it.value })
        }
    }

    @Test fun differencesNeverBridgeGapsAndRecoverOnlyAfterEnoughConsecutiveDays() {
        val source = listOf(point(0, 1f), point(1, 4f), point(3, 16f), point(4, 25f), point(5, 36f), point(6, 49f))
        assertEquals(listOf(null, 3f, null, 9f, 11f, 13f), consecutiveDifference(source, 1).map { it.value })
        assertEquals(listOf(null, null, null, null, 2f, 2f), consecutiveDifference(source, 2).map { it.value })
        assertEquals(listOf(null, null, null, null, null, 0f), consecutiveDifference(source, 3).map { it.value })
    }

    @Test fun differenceKeepsMissingAndNonfiniteValuesMissingAndLastDuplicateWins() {
        val source = listOf(point(1, 4f), point(0, -1f), point(1, null), point(2, Float.NaN), point(3, Float.POSITIVE_INFINITY), TrendPoint("invalid", 9f))
        assertEquals(listOf(point(0, -1f), point(1, null), point(2, null), point(3, null)), consecutiveDifference(source, 0))
        assertTrue(runCatching { consecutiveDifference(source, 4) }.isFailure)
    }

    @Test fun regressionAlignsLaggedFactorsAndSplitsDatesChronologically() {
        val outcome = (0 until 110).map { point(it, (it * it).toFloat()) }
        val a = (0 until 110).map { point(it, it.toFloat()) }
        val b = (0 until 110).map { point(it, sin(it * 0.73).toFloat()) }
        val result = fitSleepRegression(outcome, a, b, date(99), days = 90, lagDays = 2, includeInteraction = false)
        assertEquals(RegressionStatus.READY, result.status)
        assertEquals(90, result.availableN)
        assertEquals(72, result.trainN)
        assertEquals(18, result.holdoutN)
        assertEquals(date(10), result.trainStartDate)
        assertEquals(date(81), result.trainEndDate)
        assertEquals(date(82), result.holdoutStartDate)
        assertEquals(date(99), result.holdoutEndDate)
        assertEquals(43.5, result.trainingCenterScale!!.factorA.mean, 0.00001)
        assertEquals((82 * 82).toDouble(), result.holdout.first().observed, 0.00001)
    }

    @Test fun futureRecordsCannotChangeFitScalesCurvesOrHeldOutEvaluation() {
        val data = synthetic(160)
        val first = fit(data, through = date(119), days = 120)
        val futureA = data.a.map { if (it.date > date(119)) it.copy(value = 1_000_000f) else it }
        val futureY = data.y.map { if (it.date > date(119)) it.copy(value = -1_000_000f) else it }
        val second = fitSleepRegression(futureY, futureA, data.b, date(119), days = 120)
        assertEquals(RegressionStatus.READY, first.status)
        assertEquals(first, second)
    }

    @Test fun insufficientRecordsReturnCountsWithoutInventingCoefficientsOrForecasts() {
        val data = synthetic(60)
        val interaction = fit(data)
        assertEquals(RegressionStatus.INSUFFICIENT_DATA, interaction.status)
        assertEquals(59, interaction.availableN)
        assertEquals(47, interaction.trainN)
        assertEquals(12, interaction.holdoutN)
        assertEquals(50, interaction.requiredTrainN)
        assertTrue(interaction.coefficients.isEmpty())
        assertTrue(interaction.holdout.isEmpty())
        assertNull(interaction.holdoutMAE)
        assertEquals(RegressionStatus.READY, fitSleepRegression(data.y, data.a, data.b, date(59), days = 60, includeInteraction = false).status)
    }

    @Test fun constantFactorsOrOutcomeReturnAnExplicitReasonAndMissingFactorsDropOnlyTheirDates() {
        val data = synthetic(120)
        val constantA = data.a.map { it.copy(value = 2f) }
        val factors = fitSleepRegression(data.y, constantA, data.b, date(119), days = 120)
        assertEquals(RegressionStatus.CONSTANT_FACTOR, factors.status)
        assertTrue(factors.reason != null)
        assertTrue(factors.coefficients.isEmpty())
        val constantY = data.y.map { it.copy(value = 5f) }
        assertEquals(RegressionStatus.CONSTANT_OUTCOME, fitSleepRegression(constantY, data.a, data.b, date(119), days = 120).status)
        val missingA = data.a.map { if (it.date == date(9)) it.copy(value = null) else it }
        val missing = fitSleepRegression(data.y, missingA, data.b, date(119), days = 120)
        assertEquals(118, missing.availableN)
        assertEquals(RegressionStatus.READY, missing.status)
    }

    @Test fun constantWeekendControlIsDroppedExplicitlyRatherThanMakingFitSingular() {
        val data = synthetic(240)
        val weekdays = data.y.filter {
            val day = LocalDate.parse(it.date).dayOfWeek
            day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
        }
        val result = fitSleepRegression(weekdays, data.a, data.b, date(239), days = 240)
        assertEquals(RegressionStatus.READY, result.status)
        assertTrue("weekend" in result.droppedFeatures)
        assertFalse("weekend" in result.featureNames)
        assertFalse("weekend" in result.coefficients)
    }

    @Test fun holdoutChangesCannotLeakIntoTrainingScalingCoefficientsOrConditionalCurves() {
        val data = synthetic(120)
        val first = fit(data)
        val trainEnd = first.trainEndDate!!
        val changedA = data.a.map { if (it.date > trainEnd) it.copy(value = it.value!! + 1000f) else it }
        val changedY = data.y.map { if (it.date > trainEnd) it.copy(value = it.value!! - 1000f) else it }
        val second = fitSleepRegression(changedY, changedA, data.b, date(119), days = 120)
        assertEquals(RegressionStatus.READY, first.status)
        assertEquals(RegressionStatus.READY, second.status)
        assertEquals(first.trainingCenterScale, second.trainingCenterScale)
        assertEquals(first.coefficients, second.coefficients)
        assertEquals(first.conditionalCurves, second.conditionalCurves)
        assertEquals(first.trainingMean, second.trainingMean)
        assertNotEquals(first.holdoutMAE, second.holdoutMAE)
        assertEquals(first.trainingMean!!, second.holdout.first().baselinePredicted, 0.00001)
    }

    @Test fun realSyntheticInteractionImprovesHeldOutPredictionAndRetainsMainEffects() {
        val result = fit(synthetic(200))
        assertEquals(RegressionStatus.READY, result.status)
        assertTrue(result.coefficients.getValue("interaction") > 1.0)
        assertTrue("factor_a" in result.coefficients)
        assertTrue("factor_b" in result.coefficients)
        assertTrue(result.holdoutMAE!! < 0.2)
        assertTrue(result.holdoutMAE < result.additiveMAE!! * 0.35)
        assertTrue(result.holdoutMAE < result.controlMAE!! * 0.35)
        assertTrue(result.holdoutR2!! > 0.9)
        assertEquals(2, result.conditionalCurves.size)
        val low = result.conditionalCurves[0]
        val high = result.conditionalCurves[1]
        assertEquals(0.25, low.factorBQuantile, 0.0)
        assertEquals(0.75, high.factorBQuantile, 0.0)
        assertTrue(low.factorBValue < high.factorBValue)
        assertEquals(21, low.points.size)
        assertEquals(low.points.map { it.x }, high.points.map { it.x })
        assertTrue(high.points.last().y - high.points.first().y > low.points.last().y - low.points.first().y + 0.5)
        val trainingA = synthetic(200).a.take(result.trainN).map { it.value!!.toDouble() }
        assertTrue(low.points.first().x >= trainingA.min())
        assertTrue(low.points.last().x <= trainingA.max())
    }

    @Test fun controlOnlyBenchmarkCreditsAutocorrelationRatherThanUnrelatedUserFactors() {
        val data = synthetic(180, useFactors = false)
        val result = fit(data)
        assertEquals(RegressionStatus.READY, result.status)
        assertTrue(result.controlMAE!! < result.baselineMAE!! * 0.25)
        assertTrue(abs(result.holdoutMAE!! - result.controlMAE) < 0.08)
        assertTrue(result.holdout.all { it.controlPredicted.isFinite() })
    }

    @Test fun holdoutR2CanBeNegativeAndIsNeverClippedToZero() {
        val data = synthetic(120)
        val first = fit(data)
        val changedY = data.y.mapIndexed { index, point ->
            if (point.date > first.trainEndDate!!) point.copy(value = if (index % 2 == 0) 1000f else -1000f) else point
        }
        val result = fitSleepRegression(changedY, data.a, data.b, date(119), days = 120)
        assertEquals(RegressionStatus.READY, result.status)
        assertTrue(result.holdoutR2!! < 0.0)
    }

    @Test fun shiftingOutcomeLevelShiftsInterceptAndPredictionsWithoutPenalizingTheIntercept() {
        val data = synthetic(120)
        val first = fit(data)
        val shifted = fitSleepRegression(data.y.map { it.copy(value = it.value!! + 1000f) }, data.a, data.b, date(119), days = 120)
        assertEquals(RegressionStatus.READY, shifted.status)
        assertEquals(first.coefficients.getValue("intercept") + 1000.0, shifted.coefficients.getValue("intercept"), 0.01)
        first.holdout.zip(shifted.holdout).forEach { (original, changed) ->
            assertEquals(original.predicted + 1000.0, changed.predicted, 0.01)
        }
    }

    @Test fun regressionRejectsInvalidConfigurationAndCanFitAllSupportedDifferenceOrders() {
        val data = synthetic(200)
        assertEquals(RegressionStatus.INVALID_INPUT, fitSleepRegression(data.y, data.a, data.b, null).status)
        assertEquals(RegressionStatus.INVALID_INPUT, fitSleepRegression(data.y, data.a, data.b, date(199), differenceOrder = 4).status)
        assertEquals(RegressionStatus.INVALID_INPUT, fitSleepRegression(data.y, data.a, data.b, date(199), lagDays = -1).status)
        assertEquals(RegressionStatus.INVALID_INPUT, fitSleepRegression(data.y, data.a, data.b, date(199), days = 0).status)
        for (order in 0..3) {
            val result = fitSleepRegression(data.y, data.a, data.b, date(199), days = 200, differenceOrder = order)
            assertEquals(RegressionStatus.READY, result.status)
            assertEquals(199 - order, result.availableN)
            assertTrue(result.holdoutMAE!!.isFinite())
        }
    }

    @Test fun randomForestIsDeterministicAndLeafPredictionsDoNotExtrapolate() {
        val data = synthetic(240)
        val first = fitSleepRegression(
            data.y, data.a, data.b, date(239), days = 240,
            algorithm = SleepAlgorithm.RANDOM_FOREST,
        )
        val second = fitSleepRegression(
            data.y, data.a, data.b, date(239), days = 240,
            algorithm = SleepAlgorithm.RANDOM_FOREST,
        )
        assertEquals(RegressionStatus.READY, first.status)
        assertEquals(first, second)
        assertEquals(SleepAlgorithm.RANDOM_FOREST, first.algorithm)
        assertTrue(first.linearMAE!!.isFinite())
        assertTrue(first.featureImportances.isEmpty())
        val trainValues = data.y.drop(1).take(first.trainN).mapNotNull { it.value?.toDouble() }
        assertTrue(first.holdout.all { it.predicted in trainValues.min()..trainValues.max() })
    }

    @Test fun heldOutImportanceFindsKnownFactorAndIsDeterministic() {
        val a = (0 until 240).map { point(it, sin(it * 0.21).toFloat()) }
        val b = (0 until 240).map { point(it, cos(it * 0.77).toFloat()) }
        val y = (0 until 240).map { i ->
            val previous = if (i == 0) 0.0 else a[i - 1].value!!.toDouble()
            point(i, (20.0 + 8.0 * previous + 0.05 * sin(i * 1.7)).toFloat())
        }
        val first = fitSleepRegression(y, a, b, date(239), days = 240, includeInteraction = false, withImportance = true)
        val second = fitSleepRegression(y, a, b, date(239), days = 240, includeInteraction = false, withImportance = true)
        assertEquals(RegressionStatus.READY, first.status)
        assertEquals(first.featureImportances, second.featureImportances)
        val byKey = first.featureImportances.associateBy { it.key }
        assertTrue(byKey.containsKey("factor_a"))
        assertTrue(byKey.getValue("factor_a").increaseMae > byKey.getValue("factor_b").increaseMae)
        assertTrue(first.featureImportances.all { it.repeatSd.isFinite() })
    }

    @Test fun enrichedFeaturesUseStrictPastWindowsAndTrainOnlyContextFiltering() {
        val data = synthetic(280)
        val context = FeatureSeries(
            key = "hrv",
            labelChinese = "夜间 HRV",
            series = data.a.map { point -> point.copy(value = point.value?.plus(10f)) },
            lagDays = 1,
        )
        val result = fitSleepRegression(
            data.y, data.a, data.b, date(279), days = 280,
            featurePack = SleepFeaturePack.ENRICHED,
            contextSeries = listOf(context),
            includeFrenchHolidays = false,
        )
        assertEquals(RegressionStatus.READY, result.status)
        assertTrue(result.featureNames.count { it.contains('.') } <= 12)
        assertTrue("factor_a.diff1" in result.featureNames)
        assertTrue("factor_a.mean7" in result.featureNames)
        assertTrue("factor_a.sd7" in result.featureNames)
        assertTrue("factor_a.median28_dev" in result.featureNames)
        assertTrue("hrv" in result.featureNames)

        // Altering values after throughDate cannot change a strict-past enriched fit.
        val futureContext = context.copy(series = context.series + point(300, 99999f))
        assertEquals(
            result,
            fitSleepRegression(
                data.y, data.a, data.b, date(279), days = 280,
                featurePack = SleepFeaturePack.ENRICHED,
                contextSeries = listOf(futureContext),
                includeFrenchHolidays = false,
            ),
        )
    }

    @Test fun sparseContextIsDroppedByTrainingCoverageAndNeverFilledWithZero() {
        val data = synthetic(220)
        val sparse = FeatureSeries(
            key = "sparse_hrv",
            labelChinese = "稀疏 HRV",
            series = data.a.filterIndexed { index, _ -> index % 10 == 0 },
        )
        val result = fitSleepRegression(
            data.y, data.a, data.b, date(219), days = 220,
            featurePack = SleepFeaturePack.ENRICHED,
            contextSeries = listOf(sparse),
            includeFrenchHolidays = false,
        )
        assertTrue("sparse_hrv" in result.droppedFeatures)
        assertTrue("sparse_hrv" !in result.featureNames)
    }

    @Test fun sparseFrenchHolidayIndicatorsAreDroppedWithoutClaimingAnEffect() {
        val data = synthetic(120)
        val result = fitSleepRegression(data.y, data.a, data.b, date(119), days = 120, includeFrenchHolidays = true)
        assertEquals(RegressionStatus.READY, result.status)
        assertTrue("holiday" in result.droppedFeatures)
        assertTrue("holiday_eve" in result.droppedFeatures)
        assertTrue("holiday" !in result.featureNames)
        assertTrue("holiday_eve" !in result.featureNames)
    }

    private data class Synthetic(val y: List<TrendPoint>, val a: List<TrendPoint>, val b: List<TrendPoint>)

    private fun synthetic(count: Int, useFactors: Boolean = true): Synthetic {
        val a = (0 until count).map { sin(it * 0.43) + 0.35 * cos(it * 0.19) }
        val b = (0 until count).map { cos(it * 0.71) + 0.25 * sin(it * 0.17) }
        val values = MutableList(count) { 0.0 }
        for (i in 1 until count) {
            val day = LocalDate.parse(date(i)).dayOfWeek
            val weekend = if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) 1.0 else 0.0
            values[i] = if (useFactors) {
                5.0 + 0.2 * values[i - 1] + 1.5 * a[i - 1] - 0.75 * b[i - 1] + 2.8 * a[i - 1] * b[i - 1] + 0.4 * weekend + 0.02 * sin(i * 1.17)
            } else 1.2 + 0.85 * values[i - 1] + 0.8 * weekend
        }
        return Synthetic(values.mapIndexed { i, value -> point(i, value.toFloat()) }, a.mapIndexed { i, value -> point(i, value.toFloat()) }, b.mapIndexed { i, value -> point(i, value.toFloat()) })
    }

    private fun fit(data: Synthetic, through: String = data.y.last().date, days: Int = data.y.size): RegressionResult =
        fitSleepRegression(data.y, data.a, data.b, through, days)

    private fun date(offset: Int): String = LocalDate.of(2026, 1, 1).plusDays(offset.toLong()).toString()
    private fun point(offset: Int, value: Float?) = TrendPoint(date(offset), value)
}
