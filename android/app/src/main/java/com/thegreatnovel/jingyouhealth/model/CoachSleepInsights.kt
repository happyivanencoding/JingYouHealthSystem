package com.thegreatnovel.jingyouhealth.model

import java.time.Instant

const val COACH_SLEEP_SNAPSHOT_SCHEMA_VERSION: Int = 1
const val COACH_SLEEP_SNAPSHOT_SOURCE: String = "android_personal_sleep_v1"

data class CoachSleepFeatureImportance(
    val feature: String,
    val maeIncrease: Double,
    val repeatSd: Double,
)

data class CoachSleepModel(
    val outcome: SleepOutcome,
    val status: RegressionStatus,
    val algorithm: SleepAlgorithm = SleepAlgorithm.RANDOM_FOREST,
    val factorA: SleepFactor? = null,
    val factorB: SleepFactor? = null,
    val featurePack: SleepFeaturePack? = null,
    val lagDays: Int? = null,
    val trainN: Int = 0,
    val validationN: Int = 0,
    val validationStart: String? = null,
    val validationEnd: String? = null,
    val selectionMae: Double? = null,
    val selectionReferenceMae: Double? = null,
    val mae: Double? = null,
    val referenceMae: Double? = null,
    val featureImportance: List<CoachSleepFeatureImportance> = emptyList(),
    val droppedFeatures: List<String> = emptyList(),
)

data class CoachSleepTiming(
    val nightCount: Int,
    val usualBedtimeHour: Double?,
    val usualWakeHour: Double?,
    val lateCount: Int,
    val otherCount: Int,
    val bedtimeShiftHours: Double?,
    val wakeShiftHours: Double?,
    val lateSleepHours: Double?,
    val otherSleepHours: Double?,
    val lateDeepHours: Double?,
    val otherDeepHours: Double?,
    val lateRemHours: Double?,
    val otherRemHours: Double?,
)

data class CoachSleepSnapshot(
    val schemaVersion: Int = COACH_SLEEP_SNAPSHOT_SCHEMA_VERSION,
    val source: String = COACH_SLEEP_SNAPSHOT_SOURCE,
    val throughDate: String,
    val generatedAt: String,
    val frenchHolidays: Boolean,
    val models: List<CoachSleepModel>,
    val timing: CoachSleepTiming,
)

/**
 * Build the Coach-facing model from the scout's existing order. The first RANDOM_FOREST,
 * difference-order-zero candidate is authoritative here: weak verification is retained and no
 * second ranking or newest-segment promotion is performed.
 */
fun buildCoachSleepModel(
    report: PersonalSleepReport,
    trends: Trends,
    activities: List<ActivitySummary>,
    checkpoint: () -> Unit = {},
): CoachSleepModel {
    val candidate = firstCoachCandidate(report)
        ?: return emptyCoachSleepModel(report.outcome)
    val throughDate = report.throughDate
    if (throughDate == null) {
        return coachSleepModel(candidate, RegressionResult(RegressionStatus.INVALID_INPUT, "缺少睡眠记录日期"))
    }
    checkpoint()
    val result = fitCoachCandidate(report, candidate, trends, activities, throughDate)
    checkpoint()
    return coachSleepModel(candidate, result)
}

/** Reuse an already opened insight fit; this avoids fitting the same selected candidate twice. */
fun buildCoachSleepModel(
    report: PersonalSleepReport,
    fitted: RegressionResult,
): CoachSleepModel {
    val candidate = firstCoachCandidate(report)
        ?: return emptyCoachSleepModel(report.outcome)
    return coachSleepModel(candidate, fitted)
}

fun buildCoachSleepSnapshot(
    throughDate: String,
    models: List<CoachSleepModel>,
    trends: Trends,
    frenchHolidays: Boolean,
    generatedAt: String = Instant.now().toString(),
): CoachSleepSnapshot {
    val summary = sleepTimingSummary(trends, throughDate)
    return CoachSleepSnapshot(
        throughDate = throughDate,
        generatedAt = generatedAt,
        frenchHolidays = frenchHolidays,
        models = models.take(5),
        timing = CoachSleepTiming(
            nightCount = summary.nights.size,
            usualBedtimeHour = finiteOrNull(summary.usualBedtime),
            usualWakeHour = finiteOrNull(summary.usualWake),
            lateCount = summary.lateCount,
            otherCount = summary.otherCount,
            bedtimeShiftHours = finiteOrNull(summary.bedtimeShift),
            wakeShiftHours = finiteOrNull(summary.wakeShift),
            lateSleepHours = finiteOrNull(summary.lateSleep),
            otherSleepHours = finiteOrNull(summary.otherSleep),
            lateDeepHours = finiteOrNull(summary.lateDeep),
            otherDeepHours = finiteOrNull(summary.otherDeep),
            lateRemHours = finiteOrNull(summary.lateRem),
            otherRemHours = finiteOrNull(summary.otherRem),
        ),
    )
}

private fun firstCoachCandidate(report: PersonalSleepReport): PersonalSleepCandidate? =
    report.candidates.firstOrNull {
        it.config.algorithm == SleepAlgorithm.RANDOM_FOREST && it.config.differenceOrder == 0
    }

private fun fitCoachCandidate(
    report: PersonalSleepReport,
    candidate: PersonalSleepCandidate,
    trends: Trends,
    activities: List<ActivitySummary>,
    throughDate: String,
): RegressionResult {
    val config = candidate.config
    val context = if (config.featurePack == SleepFeaturePack.ENRICHED) {
        filterSleepContext(sleepContextSeries(trends), config.factorA, config.factorB)
    } else {
        emptyList()
    }
    return fitSleepRegression(
        outcome = config.outcome.series(trends, throughDate),
        factorA = factorSeries(config.factorA, trends, activities, throughDate),
        factorB = factorSeries(config.factorB, trends, activities, throughDate),
        throughDate = throughDate,
        days = config.days,
        differenceOrder = config.differenceOrder,
        lagDays = config.lagDays,
        includeInteraction = config.interaction,
        splitDate = report.verificationCutoff,
        algorithm = SleepAlgorithm.RANDOM_FOREST,
        featurePack = config.featurePack,
        contextSeries = context,
        includeFrenchHolidays = config.frenchHolidays,
        withImportance = true,
    )
}

private fun coachSleepModel(
    candidate: PersonalSleepCandidate,
    result: RegressionResult,
): CoachSleepModel {
    val config = candidate.config
    return CoachSleepModel(
        outcome = config.outcome,
        status = result.status,
        algorithm = SleepAlgorithm.RANDOM_FOREST,
        factorA = config.factorA,
        factorB = config.factorB,
        featurePack = config.featurePack,
        lagDays = config.lagDays.takeIf { it in 0..3 },
        trainN = result.trainN,
        validationN = result.holdoutN,
        validationStart = result.holdoutStartDate,
        validationEnd = result.holdoutEndDate,
        selectionMae = finiteOrNull(candidate.selection.holdoutMAE),
        selectionReferenceMae = finiteOrNull(candidate.selection.controlMAE),
        mae = finiteOrNull(result.holdoutMAE),
        referenceMae = finiteOrNull(result.controlMAE),
        featureImportance = result.featureImportances.mapNotNull { importance ->
            val increase = importance.increaseMae.takeIf(Double::isFinite) ?: return@mapNotNull null
            val repeatSd = importance.repeatSd.takeIf(Double::isFinite) ?: return@mapNotNull null
            CoachSleepFeatureImportance(importance.key, increase, repeatSd)
        },
        droppedFeatures = result.droppedFeatures,
    )
}

private fun emptyCoachSleepModel(outcome: SleepOutcome): CoachSleepModel = CoachSleepModel(
    outcome = outcome,
    status = RegressionStatus.INSUFFICIENT_DATA,
    algorithm = SleepAlgorithm.RANDOM_FOREST,
)

private fun finiteOrNull(value: Double?): Double? = value?.takeIf(Double::isFinite)
