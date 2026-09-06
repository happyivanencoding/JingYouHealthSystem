package com.thegreatnovel.jingyouhealth.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Random
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

enum class RegressionStatus {
    READY,
    INVALID_INPUT,
    INSUFFICIENT_DATA,
    CONSTANT_FACTOR,
    CONSTANT_OUTCOME,
    NUMERICAL_FAILURE,
}

enum class SleepAlgorithm(val labelChinese: String) { LINEAR("线性回归"), RANDOM_FOREST("随机森林") }

/** Feature packs keep the scout cheap while allowing an explicitly requested enriched fit. */
enum class SleepFeaturePack { BASIC, ENRICHED }

/** A caller-owned context series. The model never reads UI state or invents this series. */
data class FeatureSeries(
    val key: String,
    val labelChinese: String,
    val series: List<TrendPoint>,
    val lagDays: Int = 0,
)

/** Held-out predictive permutation importance. This is not a causal contribution. */
data class FeatureImportance(
    val key: String,
    val increaseMae: Double,
    val repeatSd: Double,
)

/** Population standard deviation of the training observations; a dropped constant has scale 0. */
data class RegressionScale(val mean: Double, val scale: Double)

data class RegressionScaling(
    val factorA: RegressionScale,
    val factorB: RegressionScale,
    val previousY: RegressionScale,
    /** Train-only scales for enriched context columns. */
    val additional: Map<String, RegressionScale> = emptyMap(),
)

data class RegressionPrediction(
    val date: String,
    val observed: Double,
    val predicted: Double,
    val additivePredicted: Double,
    val baselinePredicted: Double,
    val controlPredicted: Double,
)

data class RegressionCurvePoint(val x: Double, val y: Double)

/** A modeled slice within training marginal ranges, not a causal or observed response curve. */
data class RegressionCurve(
    val factorBQuantile: Double,
    val factorBValue: Double,
    val points: List<RegressionCurvePoint>,
)

data class RegressionResult(
    val status: RegressionStatus,
    val reason: String? = null,
    val availableN: Int = 0,
    /** Active predictor keys, excluding the intercept. */
    val featureNames: List<String> = emptyList(),
    /** Outcome remains in transformed raw units. Main continuous predictors are train-standardized. */
    val coefficients: Map<String, Double> = emptyMap(),
    /** Includes context columns removed using training-only coverage/variance checks. */
    val droppedFeatures: List<String> = emptyList(),
    val trainN: Int = 0,
    val holdoutN: Int = 0,
    val requiredTrainN: Int = 30,
    val requiredHoldoutN: Int = 10,
    val trainStartDate: String? = null,
    val trainEndDate: String? = null,
    val holdoutStartDate: String? = null,
    val holdoutEndDate: String? = null,
    val holdout: List<RegressionPrediction> = emptyList(),
    val holdoutMAE: Double? = null,
    val baselineMAE: Double? = null,
    val additiveMAE: Double? = null,
    val controlMAE: Double? = null,
    /** Linear comparator on exactly the same rows, columns and time split as a forest fit. */
    val linearMAE: Double? = null,
    /** Standard holdout R² against the holdout mean; may be negative and is null for constant truth. */
    val holdoutR2: Double? = null,
    val trainingMean: Double? = null,
    val trainingCenterScale: RegressionScaling? = null,
    val conditionalCurves: List<RegressionCurve> = emptyList(),
    val algorithm: SleepAlgorithm = SleepAlgorithm.LINEAR,
    /** Empty unless the caller explicitly asks for held-out importance. */
    val featureImportances: List<FeatureImportance> = emptyList(),
) {
    /** Short read-only alias for callers that use the singular UI label. */
    val featureImportance: List<FeatureImportance> get() = featureImportances
}

private const val RIDGE_LAMBDA = 1.0
private const val INTERCEPT = "intercept"
private const val FACTOR_A = "factor_a"
private const val FACTOR_B = "factor_b"
private const val PREVIOUS_Y = "previous_y"
private const val WEEKEND = "weekend"
private const val INTERACTION = "interaction"
private const val HOLIDAY = "holiday"
private const val HOLIDAY_EVE = "holiday_eve"
private const val MAX_ENRICHED_FEATURES = 12
private const val IMPORTANCE_REPEATS = 12
private const val IMPORTANCE_SEED = 0x5EED_7A11L

private val HOLIDAY_FEATURES = setOf(HOLIDAY, HOLIDAY_EVE)

/**
 * Backward differences on consecutive calendar days. Each order preserves the supplied valid
 * dates, sorted and deduplicated (last record wins). A missing/invalid predecessor produces null;
 * it never means zero and cannot be bridged by the previous array element.
 */
fun consecutiveDifference(points: List<TrendPoint>, order: Int): List<TrendPoint> {
    require(order in 0..3) { "Difference order must be between 0 and 3." }
    var values = regressionSeries(points)
    repeat(order) {
        val previous = values
        values = previous.mapValues { (date, current) ->
            val earlier = previous[date.minusDays(1)]
            if (current == null || earlier == null) null else {
                (current - earlier).takeIf(Double::isFinite)
            }
        }.toSortedMap()
    }
    return values.map { (date, value) -> TrendPoint(date.toString(), value?.toFloat()) }
}

/**
 * A time-ordered, one-step holdout evaluation. The default remains linear ridge regression.
 * RF uses a deterministic 32-tree, depth-3, min-leaf-8 forest and a minimum of 60 training rows.
 * All scaling, missing-value means, feature selection and conditional curve ranges use training
 * rows only. A context series is caller-owned and is ignored in BASIC mode.
 */
fun fitSleepRegression(
    outcome: List<TrendPoint>,
    factorA: List<TrendPoint>,
    factorB: List<TrendPoint>,
    throughDate: String?,
    days: Int = 90,
    differenceOrder: Int = 0,
    lagDays: Int = 1,
    includeInteraction: Boolean = true,
    splitDate: String? = null,
    algorithm: SleepAlgorithm = SleepAlgorithm.LINEAR,
    featurePack: SleepFeaturePack = SleepFeaturePack.BASIC,
    contextSeries: List<FeatureSeries> = emptyList(),
    includeFrenchHolidays: Boolean = true,
    withImportance: Boolean = false,
): RegressionResult {
    val end = regressionDate(throughDate)
        ?: return RegressionResult(RegressionStatus.INVALID_INPUT, "请选择有效的记录日期", algorithm = algorithm)
    if (days <= 0) return RegressionResult(RegressionStatus.INVALID_INPUT, "时间窗口必须大于 0 天", algorithm = algorithm)
    if (differenceOrder !in 0..3) return RegressionResult(RegressionStatus.INVALID_INPUT, "差分阶数需要在 0 到 3 之间", algorithm = algorithm)
    if (lagDays < 0) return RegressionResult(RegressionStatus.INVALID_INPUT, "滞后天数不能为负", algorithm = algorithm)
    if (contextSeries.any { it.lagDays < 0 }) {
        return RegressionResult(RegressionStatus.INVALID_INPUT, "辅助变量的滞后天数不能为负", algorithm = algorithm)
    }
    val start = end.minusDays(days.toLong() - 1)
    val y = regressionSeries(consecutiveDifference(outcome, differenceOrder))
    val a = regressionSeries(consecutiveDifference(factorA, differenceOrder))
    val b = regressionSeries(consecutiveDifference(factorB, differenceOrder))

    val derived = if (featurePack == SleepFeaturePack.ENRICHED) {
        val specs = mutableListOf<DerivedFeature>()
        specs += derivedFeatures(FACTOR_A, a, lagDays)
        specs += derivedFeatures(FACTOR_B, b, lagDays)
        val usedKeys = mutableSetOf(FACTOR_A, FACTOR_B)
        contextSeries.forEach { context ->
            val key = context.key.trim()
            if (key.isNotEmpty() && usedKeys.add(key)) {
                // Context columns are caller-supplied. This lets the caller pass an explicitly
                // defined HRV/RHR/stress change or rolling statistic without silently inventing
                // four more columns for every context series.
                specs += DerivedFeature(key, key, context.lagDays, regressionSeries(context.series))
            }
        }
        specs.take(MAX_ENRICHED_FEATURES)
    } else emptyList()
    val holidayDates = if (includeFrenchHolidays) {
        (start.year - 1..end.year + 1).flatMap { franceNationalHolidays(it) }.toSet()
    } else emptySet()
    val optionalNames = buildList {
        addAll(derived.map { it.key })
        if (includeFrenchHolidays) {
            add(HOLIDAY)
            add(HOLIDAY_EVE)
        }
    }

    val rows = y.mapNotNull { (date, value) ->
        if (date < start || date > end || value == null) return@mapNotNull null
        val factorDate = date.minusDays(lagDays.toLong())
        val first = a[factorDate] ?: return@mapNotNull null
        val second = b[factorDate] ?: return@mapNotNull null
        val previous = y[date.minusDays(1)] ?: return@mapNotNull null
        val extras = linkedMapOf<String, Double?>()
        derived.forEach { spec -> extras[spec.key] = spec.values[date.minusDays(spec.lagDays.toLong())] }
        if (includeFrenchHolidays) {
            extras[HOLIDAY] = if (date in holidayDates) 1.0 else 0.0
            extras[HOLIDAY_EVE] = if (date.plusDays(1) in holidayDates) 1.0 else 0.0
        }
        RegressionRow(
            date = date,
            y = value,
            a = first,
            b = second,
            previousY = previous,
            weekend = if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) 1.0 else 0.0,
            extras = extras,
        )
    }
    val fixedSplit = splitDate?.let { regressionDate(it) }
    if (splitDate != null && (fixedSplit == null || fixedSplit < start || fixedSplit >= end)) {
        return RegressionResult(RegressionStatus.INVALID_INPUT, "验证分界日期不在窗口内", algorithm = algorithm)
    }
    val split = rows.size * 4 / 5
    val train = if (fixedSplit == null) rows.take(split) else rows.filter { it.date <= fixedSplit }
    val holdout = if (fixedSplit == null) rows.drop(split) else rows.filter { it.date > fixedSplit }
    val base = RegressionResult(
        status = RegressionStatus.INSUFFICIENT_DATA,
        algorithm = algorithm,
        reason = "可比较的记录还不够",
        availableN = rows.size,
        trainN = train.size,
        holdoutN = holdout.size,
        requiredTrainN = if (algorithm == SleepAlgorithm.RANDOM_FOREST) 60 else 30,
        trainStartDate = train.firstOrNull()?.date?.toString(),
        trainEndDate = train.lastOrNull()?.date?.toString(),
        holdoutStartDate = holdout.firstOrNull()?.date?.toString(),
        holdoutEndDate = holdout.lastOrNull()?.date?.toString(),
    )
    if (holdout.size < 10 || train.size < 30) return base

    val baseScaling = RegressionScaling(
        factorA = regressionScale(train.map { it.a }),
        factorB = regressionScale(train.map { it.b }),
        previousY = regressionScale(train.map { it.previousY }),
    )
    val meanY = train.map { it.y }.average()
    if (baseScaling.factorA.scale == 0.0 || baseScaling.factorB.scale == 0.0) {
        return base.copy(
            status = RegressionStatus.CONSTANT_FACTOR,
            reason = "训练期的变量没有足够变化",
            trainingMean = meanY,
            trainingCenterScale = baseScaling,
        )
    }
    if (regressionScale(train.map { it.y }).scale == 0.0) {
        return base.copy(
            status = RegressionStatus.CONSTANT_OUTCOME,
            reason = "训练期的睡眠结果没有变化",
            trainingMean = meanY,
            trainingCenterScale = baseScaling,
        )
    }

    val extraScales = linkedMapOf<String, RegressionScale>()
    val extraDropped = mutableListOf<String>()
    val minimumAuxCoverage = max(10, ceil(train.size / 2.0).toInt())
    optionalNames.forEach { name ->
        val values = train.mapNotNull { it.extras[name] }
        val holidayPositive = if (name in HOLIDAY_FEATURES) train.count { (it.extras[name] ?: 0.0) > 0.5 } else Int.MAX_VALUE
        val scale = values.takeIf { it.isNotEmpty() }?.let(::regressionScale)
        val drop = when {
            name in HOLIDAY_FEATURES && holidayPositive < 5 -> true
            values.size < minimumAuxCoverage -> true
            scale == null || !scale.scale.isFinite() || scale.scale <= 1e-12 -> true
            else -> false
        }
        if (drop) extraDropped += name else extraScales[name] = scale!!
    }
    val scaling = baseScaling.copy(additional = extraScales)
    val candidateBase = listOf(FACTOR_A, FACTOR_B, PREVIOUS_Y, WEEKEND) +
        if (includeInteraction) listOf(INTERACTION) else emptyList()
    val droppedBase = candidateBase.filter { feature ->
        regressionScale(train.map { featureValue(feature, it, scaling) }).scale <= 1e-12
    }
    val features = (candidateBase + extraScales.keys).filterNot { it in droppedBase }
    val dropped = droppedBase + extraDropped
    val requiredTrain = if (algorithm == SleepAlgorithm.RANDOM_FOREST) 60 else max(30, 10 * features.size)
    val configured = base.copy(
        trainingMean = meanY,
        trainingCenterScale = scaling,
        featureNames = features,
        droppedFeatures = dropped,
        requiredTrainN = requiredTrain,
    )
    if (train.size < requiredTrain) return configured.copy(reason = "训练记录还不足以支持这些变量")

    // Auxiliary missing values are imputed with their train mean only after the feature has
    // passed train-only coverage/variance checks. Base A/B/outcome gaps still remove the row.
    val preparedTrain = train.map { it.withTrainMeans(extraScales) }
    val preparedHoldout = holdout.map { it.withTrainMeans(extraScales) }
    val coefficients = ridgeFit(preparedTrain, features, scaling)
        ?: return configured.copy(status = RegressionStatus.NUMERICAL_FAILURE, reason = "这组记录暂时无法稳定拟合")
    val additiveFeatures = features - INTERACTION
    val additive = if (INTERACTION !in features) coefficients else ridgeFit(preparedTrain, additiveFeatures, scaling)
        ?: return configured.copy(status = RegressionStatus.NUMERICAL_FAILURE, reason = "这组记录暂时无法稳定拟合")
    val control = ridgeFit(preparedTrain, features.filter { it == PREVIOUS_Y || it == WEEKEND }, scaling)
        ?: return configured.copy(status = RegressionStatus.NUMERICAL_FAILURE, reason = "这组记录暂时无法稳定拟合")
    fun vector(row: RegressionRow) = features.map { featureValue(it, row, scaling) }.toDoubleArray()
    val forest = if (algorithm == SleepAlgorithm.RANDOM_FOREST) SleepRandomForest.fit(
        preparedTrain.map(::vector), preparedTrain.map { it.y }.toDoubleArray()
    ) else null
    fun linearPredict(row: RegressionRow): Double = regressionPredict(row, coefficients, scaling)
    fun predict(row: RegressionRow): Double = forest?.predict(vector(row)) ?: linearPredict(row)
    val predictions = preparedHoldout.map { row ->
        RegressionPrediction(
            date = row.date.toString(),
            observed = row.y,
            predicted = predict(row),
            additivePredicted = regressionPredict(row, additive, scaling),
            baselinePredicted = meanY,
            controlPredicted = regressionPredict(row, control, scaling),
        )
    }
    if (predictions.any { !it.predicted.isFinite() || !it.additivePredicted.isFinite() || !it.controlPredicted.isFinite() }) {
        return configured.copy(status = RegressionStatus.NUMERICAL_FAILURE, reason = "这组记录暂时无法稳定拟合")
    }
    val holdoutMean = predictions.map { it.observed }.average()
    val squaredError = predictions.sumOf { (it.observed - it.predicted).let { error -> error * error } }
    val totalVariance = predictions.sumOf { (it.observed - holdoutMean).let { centered -> centered * centered } }
    val aValues = preparedTrain.map { it.a }.sorted()
    val bValues = preparedTrain.map { it.b }.sorted()
    val aLow = regressionQuantile(aValues, 0.1)
    val aHigh = regressionQuantile(aValues, 0.9)
    val curveExtras = extraScales.mapValues { it.value.mean }
    fun curveRow(aValue: Double, bValue: Double, previous: Double) = RegressionRow(
        date = preparedTrain.last().date,
        y = meanY,
        a = aValue,
        b = bValue,
        previousY = previous,
        weekend = 0.0,
        extras = curveExtras,
    )
    val curves = listOf(0.25, 0.75).map { quantile ->
        val fixedB = regressionQuantile(bValues, quantile)
        RegressionCurve(
            factorBQuantile = quantile,
            factorBValue = fixedB,
            points = (0..20).map { index ->
                val x = aLow + (aHigh - aLow) * index / 20.0
                RegressionCurvePoint(x, predict(curveRow(x, fixedB, scaling.previousY.mean)))
            },
        )
    }
    val holdoutMae = predictions.map { abs(it.observed - it.predicted) }.average()
    val importance = if (withImportance) calculateFeatureImportances(
        preparedHoldout,
        holdoutMae,
        features,
        scaling,
        ::predict,
        contextSeries.map { it.key.trim() },
    ) else emptyList()
    return configured.copy(
        status = RegressionStatus.READY,
        reason = null,
        coefficients = if (forest == null) coefficients else emptyMap(),
        holdout = predictions,
        holdoutMAE = holdoutMae,
        additiveMAE = predictions.map { abs(it.observed - it.additivePredicted) }.average(),
        baselineMAE = predictions.map { abs(it.observed - it.baselinePredicted) }.average(),
        controlMAE = predictions.map { abs(it.observed - it.controlPredicted) }.average(),
        linearMAE = preparedHoldout.map { abs(it.y - linearPredict(it)) }.average(),
        holdoutR2 = if (totalVariance > 0.0) (1.0 - squaredError / totalVariance).takeIf(Double::isFinite) else null,
        conditionalCurves = curves,
        featureImportances = importance,
    )
}

private data class RegressionRow(
    val date: LocalDate,
    val y: Double,
    val a: Double,
    val b: Double,
    val previousY: Double,
    val weekend: Double,
    val extras: Map<String, Double?> = emptyMap(),
)

private data class DerivedFeature(
    val key: String,
    val sourceGroup: String,
    val lagDays: Int,
    val values: Map<LocalDate, Double?>,
)

private fun RegressionRow.withTrainMeans(scales: Map<String, RegressionScale>): RegressionRow = copy(
    extras = extras.mapValues { (key, value) -> value ?: scales[key]?.mean },
)

private fun derivedFeatures(
    sourceGroup: String,
    values: Map<LocalDate, Double?>,
    lagDays: Int,
): List<DerivedFeature> {
    fun pastWindow(date: LocalDate, days: Int): List<Double>? {
        val window = (1..days).map { values[date.minusDays(it.toLong())] }
        return if (window.all { it != null && it.isFinite() }) window.filterNotNull() else null
    }
    val diff = values.mapValues { (date, current) ->
        val previous = values[date.minusDays(1)]
        if (current != null && previous != null) (current - previous).takeIf(Double::isFinite) else null
    }
    val mean7 = values.mapValues { (date, _) -> pastWindow(date, 7)?.average() }
    val sd7 = values.mapValues { (date, _) ->
        pastWindow(date, 7)?.let { window ->
            val mean = window.average()
            sqrt(window.sumOf { (it - mean) * (it - mean) } / window.size)
        }
    }
    val medianDeviation28 = values.mapValues { (date, current) ->
        val window = pastWindow(date, 28)
        if (current == null || window == null) null else current - regressionMedian(window)
    }
    return listOf(
        DerivedFeature("$sourceGroup.diff1", sourceGroup, lagDays, diff),
        DerivedFeature("$sourceGroup.mean7", sourceGroup, lagDays, mean7),
        DerivedFeature("$sourceGroup.sd7", sourceGroup, lagDays, sd7),
        DerivedFeature("$sourceGroup.median28_dev", sourceGroup, lagDays, medianDeviation28),
    )
}

private fun regressionSeries(points: List<TrendPoint>): Map<LocalDate, Double?> = points.mapNotNull { point ->
    regressionDate(point.date)?.let { it to point.value?.toDouble()?.takeIf(Double::isFinite) }
}.toMap().toSortedMap()

private fun regressionDate(value: String?): LocalDate? =
    value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private fun regressionScale(values: List<Double>): RegressionScale {
    val mean = values.average()
    return RegressionScale(mean, sqrt(values.sumOf { (it - mean).let { centered -> centered * centered } } / values.size))
}

private fun standardized(value: Double, scale: RegressionScale): Double =
    if (scale.scale > 0.0) (value - scale.mean) / scale.scale else 0.0

private fun featureValue(feature: String, row: RegressionRow, scaling: RegressionScaling): Double = when (feature) {
    FACTOR_A -> standardized(row.a, scaling.factorA)
    FACTOR_B -> standardized(row.b, scaling.factorB)
    PREVIOUS_Y -> standardized(row.previousY, scaling.previousY)
    WEEKEND -> row.weekend
    INTERACTION -> standardized(row.a, scaling.factorA) * standardized(row.b, scaling.factorB)
    else -> {
        val value = row.extras[feature] ?: scaling.additional[feature]?.mean ?: 0.0
        if (feature in HOLIDAY_FEATURES) value else standardized(value, scaling.additional.getValue(feature))
    }
}

/** Cholesky solve of X'X + lambda I. The intercept diagonal receives no penalty. */
private fun ridgeFit(rows: List<RegressionRow>, features: List<String>, scaling: RegressionScaling): Map<String, Double>? {
    val dimension = features.size + 1
    val gram = Array(dimension) { DoubleArray(dimension) }
    val rhs = DoubleArray(dimension)
    rows.forEach { row ->
        val x = doubleArrayOf(1.0, *features.map { featureValue(it, row, scaling) }.toDoubleArray())
        for (j in 0 until dimension) {
            rhs[j] += x[j] * row.y
            for (k in 0..j) gram[j][k] += x[j] * x[k]
        }
    }
    for (j in 0 until dimension) {
        if (j > 0) gram[j][j] += RIDGE_LAMBDA
        for (k in 0 until j) gram[k][j] = gram[j][k]
    }
    val lower = Array(dimension) { DoubleArray(dimension) }
    for (i in 0 until dimension) {
        for (j in 0..i) {
            var remainder = gram[i][j]
            for (k in 0 until j) remainder -= lower[i][k] * lower[j][k]
            if (!remainder.isFinite()) return null
            if (i == j) {
                if (remainder <= 0.0) return null
                lower[i][j] = sqrt(remainder)
            } else lower[i][j] = remainder / lower[j][j]
        }
    }
    val intermediate = DoubleArray(dimension)
    for (i in 0 until dimension) {
        var value = rhs[i]
        for (j in 0 until i) value -= lower[i][j] * intermediate[j]
        intermediate[i] = value / lower[i][i]
    }
    val beta = DoubleArray(dimension)
    for (i in dimension - 1 downTo 0) {
        var value = intermediate[i]
        for (j in i + 1 until dimension) value -= lower[j][i] * beta[j]
        beta[i] = value / lower[i][i]
    }
    if (beta.any { !it.isFinite() }) return null
    return (listOf(INTERCEPT) + features).mapIndexed { index, name -> name to beta[index] }.toMap()
}

private fun regressionPredict(
    row: RegressionRow,
    coefficients: Map<String, Double>,
    scaling: RegressionScaling,
): Double = coefficients.getValue(INTERCEPT) + coefficients.entries.sumOf { (feature, coefficient) ->
    if (feature == INTERCEPT) 0.0 else coefficient * featureValue(feature, row, scaling)
}

private data class ImportanceGroup(val key: String)

private fun calculateFeatureImportances(
    holdout: List<RegressionRow>,
    originalMae: Double,
    features: List<String>,
    scaling: RegressionScaling,
    predictor: (RegressionRow) -> Double,
    contextKeys: List<String>,
): List<FeatureImportance> {
    if (holdout.size < 2 || !originalMae.isFinite()) return emptyList()
    val groups = buildList {
        if (FACTOR_A in features || features.any { it.startsWith("$FACTOR_A.") }) add(ImportanceGroup(FACTOR_A))
        if (FACTOR_B in features || features.any { it.startsWith("$FACTOR_B.") }) add(ImportanceGroup(FACTOR_B))
        contextKeys.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { key ->
            if (key in features || features.any { it.startsWith("$key.") }) add(ImportanceGroup(key))
        }
        if (PREVIOUS_Y in features) add(ImportanceGroup(PREVIOUS_Y))
        if (WEEKEND in features) add(ImportanceGroup(WEEKEND))
        if (HOLIDAY in features) add(ImportanceGroup(HOLIDAY))
        if (HOLIDAY_EVE in features) add(ImportanceGroup(HOLIDAY_EVE))
    }
    return groups.map { group ->
        val increases = (0 until IMPORTANCE_REPEATS).map { repeat ->
            val donors = blockPermutation(holdout, repeat, group.key)
            val permutedMae = holdout.indices.map { index ->
                val donor = holdout[donors[index]]
                val row = permuteGroup(holdout[index], donor, group.key, scaling)
                abs(row.y - predictor(row))
            }.average()
            permutedMae - originalMae
        }
        val mean = increases.average()
        val sd = sqrt(increases.sumOf { (it - mean) * (it - mean) } / increases.size)
        FeatureImportance(group.key, mean, sd)
    }
}

/** Shuffle complete calendar-week blocks while preserving each block's row positions. */
private fun blockPermutation(rows: List<RegressionRow>, repeat: Int, key: String): IntArray {
    val blocks = rows.indices.groupBy { date ->
        val day = rows[date].date
        day.minusDays((day.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    }
        .toSortedMap().values.toList()
    val order = blocks.indices.toList().shuffled(Random(IMPORTANCE_SEED + repeat * 1009L + key.hashCode()))
    val donors = IntArray(rows.size)
    blocks.forEachIndexed { targetBlock, targetRows ->
        val sourceRows = blocks[order[targetBlock]]
        targetRows.forEachIndexed { offset, target -> donors[target] = sourceRows[offset % sourceRows.size] }
    }
    return donors
}

private fun permuteGroup(
    row: RegressionRow,
    donor: RegressionRow,
    group: String,
    scaling: RegressionScaling,
): RegressionRow {
    var a = row.a
    var b = row.b
    var previous = row.previousY
    var weekend = row.weekend
    val extras = row.extras.toMutableMap()
    fun copyPrefix(prefix: String) {
        extras.keys.filter { it == prefix || it.startsWith("$prefix.") }.forEach { key ->
            extras[key] = donor.extras[key] ?: scaling.additional[key]?.mean
        }
    }
    when (group) {
        FACTOR_A -> { a = donor.a; copyPrefix(FACTOR_A) }
        FACTOR_B -> { b = donor.b; copyPrefix(FACTOR_B) }
        PREVIOUS_Y -> previous = donor.previousY
        WEEKEND -> weekend = donor.weekend
        HOLIDAY, HOLIDAY_EVE -> extras[group] = donor.extras[group]
        else -> copyPrefix(group)
    }
    // Interaction is intentionally not copied: featureValue recomputes it from the perturbed
    // A/B values, so a factor permutation carries all of that factor's derived information.
    return row.copy(a = a, b = b, previousY = previous, weekend = weekend, extras = extras)
}

private fun regressionMedian(values: List<Double>): Double {
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
}

private fun regressionQuantile(sorted: List<Double>, fraction: Double): Double {
    val position = sorted.lastIndex * fraction
    val lower = floor(position).toInt()
    val upper = (lower + 1).coerceAtMost(sorted.lastIndex)
    return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower)
}
