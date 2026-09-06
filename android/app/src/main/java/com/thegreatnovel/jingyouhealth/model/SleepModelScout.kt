package com.thegreatnovel.jingyouhealth.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class SleepCandidateConfig(
    val outcome: SleepOutcome,
    val factorA: SleepFactor,
    val factorB: SleepFactor,
    val differenceOrder: Int,
    val lagDays: Int,
    val interaction: Boolean,
    val days: Int = 180,
    val algorithm: SleepAlgorithm = SleepAlgorithm.LINEAR,
    val featurePack: SleepFeaturePack = SleepFeaturePack.BASIC,
    val frenchHolidays: Boolean = true,
) {
    val id: String get() = "${outcome.name}:${factorA.name}:${factorB.name}:$differenceOrder:$lagDays:$interaction:$days:${algorithm.name}:${featurePack.name}:$frenchHolidays"
}

data class PersonalSleepCandidate(
    val config: SleepCandidateConfig,
    val selection: RegressionResult,
    val verification: RegressionResult,
    val selectionGain: Double,
    val verificationGain: Double?,
)

data class PersonalSleepReport(
    val outcome: SleepOutcome,
    val throughDate: String?,
    val trainingCutoff: String? = null,
    val verificationCutoff: String? = null,
    val testedCount: Int = 0,
    val eligibleCount: Int = 0,
    val candidates: List<PersonalSleepCandidate> = emptyList(),
)

/**
 * A per-user candidate shortlist. 60% / 20% / 20% boundaries are chosen from outcome dates.
 * Rank within each difference order using only the middle segment. The newest segment is never
 * used to select, sort or discard candidates; it only provides an honest later verification badge.
 * Candidate rows may differ due to missing factors, so sample counts remain part of every result.
 */
fun proposeSleepConfigurations(
    outcome: SleepOutcome,
    trends: Trends,
    activities: List<ActivitySummary>,
    throughDate: String?,
    algorithms: List<SleepAlgorithm> = listOf(SleepAlgorithm.LINEAR, SleepAlgorithm.RANDOM_FOREST),
    includeEnrichedForest: Boolean = false,
    contextSeries: List<FeatureSeries> = emptyList(),
    includeFrenchHolidays: Boolean = true,
    differenceOrders: List<Int> = listOf(0, 1, 2),
    checkpoint: () -> Unit = {},
): PersonalSleepReport {
    val end = runCatching { LocalDate.parse(throughDate) }.getOrNull()
        ?: return PersonalSleepReport(outcome, throughDate)
    val start = end.minusDays(179)
    val y = outcome.series(trends, end.toString())
    val dates = y.filter { it.value != null && it.date >= start.toString() && it.date <= end.toString() }.map { it.date }.distinct().sorted()
    if (dates.size < 65) return PersonalSleepReport(outcome, throughDate)
    val trainCut = dates[(dates.size * 0.6).toInt() - 1]
    val verifyCut = dates[(dates.size * 0.8).toInt() - 1]
    val selectionDays = (ChronoUnit.DAYS.between(start, LocalDate.parse(verifyCut)) + 1).toInt()
    val pairs = listOf(
        SleepFactor.STEPS to SleepFactor.STRESS,
        SleepFactor.TRAINING_MINUTES to SleepFactor.TRAINING_AVG_HR,
        SleepFactor.TRAINING_LOAD to SleepFactor.HRV,
        SleepFactor.STRESS to SleepFactor.RECENT_SLEEP_3,
        SleepFactor.STEPS to SleepFactor.HRV,
        SleepFactor.RHR to SleepFactor.STRESS,
        SleepFactor.BATTERY_DRAINED to SleepFactor.STEPS,
        SleepFactor.BATTERY_CHARGED to SleepFactor.STRESS,
    )
    val series = pairs.flatMap { listOf(it.first, it.second) }.distinct().associateWith {
        factorSeries(it, trends, activities, end.toString())
    }
    val selected = mutableListOf<Triple<SleepCandidateConfig, RegressionResult, Double>>()
    var tried = 0
    val requestedAlgorithms = algorithms.distinct().ifEmpty { listOf(SleepAlgorithm.LINEAR) }
    val requestedOrders = differenceOrders.filter { it in 0..2 }.distinct().ifEmpty { listOf(0) }
    for ((first, second) in pairs) for (order in requestedOrders) for (lag in 1..3) for (interaction in listOf(false, true)) {
        for (algorithm in requestedAlgorithms) {
            // RF already learns nonlinear combinations from tree splits; one RF candidate per
            // cell keeps the local scout at 216 fits instead of duplicating an explicit product.
            if (algorithm == SleepAlgorithm.RANDOM_FOREST && !interaction) continue
            checkpoint()
            tried++
            val config = SleepCandidateConfig(outcome, first, second, order, lag, interaction, algorithm = algorithm, frenchHolidays = includeFrenchHolidays)
            val result = fitSleepRegression(y, series.getValue(first), series.getValue(second), verifyCut,
                days = selectionDays, differenceOrder = order, lagDays = lag, includeInteraction = interaction,
                splitDate = trainCut, algorithm = algorithm, includeFrenchHolidays = includeFrenchHolidays)
            val gain = relativeGain(result)
            if (result.status == RegressionStatus.READY && result.holdoutN >= 12 && gain != null) selected += Triple(config, result, gain)
        }
    }
    if (includeEnrichedForest && SleepAlgorithm.RANDOM_FOREST in requestedAlgorithms && 0 in requestedOrders) {
        for ((first, second) in pairs) {
            checkpoint()
            tried++
            val config = SleepCandidateConfig(
                outcome = outcome,
                factorA = first,
                factorB = second,
                differenceOrder = 0,
                lagDays = 1,
                interaction = true,
                algorithm = SleepAlgorithm.RANDOM_FOREST,
                featurePack = SleepFeaturePack.ENRICHED,
                frenchHolidays = includeFrenchHolidays,
            )
            val result = fitSleepRegression(
                y,
                series.getValue(first),
                series.getValue(second),
                verifyCut,
                days = selectionDays,
                differenceOrder = 0,
                lagDays = 1,
                includeInteraction = true,
                splitDate = trainCut,
                algorithm = SleepAlgorithm.RANDOM_FOREST,
                featurePack = SleepFeaturePack.ENRICHED,
                contextSeries = filterSleepContext(contextSeries, first, second),
                includeFrenchHolidays = includeFrenchHolidays,
            )
            val gain = relativeGain(result)
            if (result.status == RegressionStatus.READY && result.holdoutN >= 12 && gain != null) selected += Triple(config, result, gain)
        }
    }
    val bedtimes = factorSeries(SleepFactor.BEDTIME_DELAY, trends, activities, end.toString())
    if (0 in requestedOrders && bedtimes.count { it.value != null } >= 60) {
        val priorStress = factorSeries(SleepFactor.PRIOR_DAY_STRESS, trends, activities, end.toString())
        val timing = listOf(SleepAlgorithm.LINEAR to SleepFeaturePack.BASIC, SleepAlgorithm.RANDOM_FOREST to SleepFeaturePack.BASIC,
            SleepAlgorithm.RANDOM_FOREST to SleepFeaturePack.ENRICHED)
        for ((algorithm, pack) in timing.filter { it.first in requestedAlgorithms && (it.second == SleepFeaturePack.BASIC || includeEnrichedForest) }) {
            checkpoint(); tried++
            val config = SleepCandidateConfig(outcome, SleepFactor.BEDTIME_DELAY, SleepFactor.PRIOR_DAY_STRESS, 0, 0, true,
                algorithm = algorithm, featurePack = pack, frenchHolidays = includeFrenchHolidays)
            val result = fitSleepRegression(y, bedtimes, priorStress, verifyCut, selectionDays, 0, 0, true, trainCut,
                algorithm, pack, filterSleepContext(contextSeries, config.factorA, config.factorB), includeFrenchHolidays)
            val gain = relativeGain(result)
            if (result.status == RegressionStatus.READY && result.holdoutN >= 12 && gain != null) selected += Triple(config, result, gain)
        }
    }
    // Each order is a different target (levels / changes / changes of changes). Do not mix their ranks.
    val shortlist = requestedOrders.flatMap { order ->
        selected.filter { it.first.differenceOrder == order }
            .sortedWith(compareByDescending<Triple<SleepCandidateConfig, RegressionResult, Double>> { it.third }
                .thenByDescending { it.second.holdoutN }.thenBy { it.first.id })
            .distinctBy { it.first.factorA to it.first.factorB }
            .take(3)
    }
    val verified = shortlist.map { (config, selection, gain) ->
        checkpoint()
        val finalA = series[config.factorA] ?: factorSeries(config.factorA, trends, activities, end.toString())
        val finalB = series[config.factorB] ?: factorSeries(config.factorB, trends, activities, end.toString())
        val verification = fitSleepRegression(y, finalA, finalB, end.toString(),
            days = 180, differenceOrder = config.differenceOrder, lagDays = config.lagDays,
            includeInteraction = config.interaction, splitDate = verifyCut, algorithm = config.algorithm,
            featurePack = config.featurePack, contextSeries = if (config.featurePack == SleepFeaturePack.ENRICHED) filterSleepContext(contextSeries, config.factorA, config.factorB) else emptyList(),
            includeFrenchHolidays = includeFrenchHolidays)
        PersonalSleepCandidate(config, selection, verification, gain, relativeGain(verification))
    }
    return PersonalSleepReport(outcome, throughDate, trainCut, verifyCut, tried, selected.size, verified)
}

private fun relativeGain(result: RegressionResult): Double? {
    if (result.status != RegressionStatus.READY) return null
    val reference = result.controlMAE?.takeIf { it.isFinite() && it > 1e-8 } ?: return null
    val model = result.holdoutMAE?.takeIf(Double::isFinite) ?: return null
    return ((reference - model) / reference).takeIf(Double::isFinite)
}
