package com.thegreatnovel.jingyouhealth.model

data class TrainingWindow(
    val totalAu: Double? = null,
    val activeDays: Int = 0,
    val coverageDays: Int = 0,
    val windowDays: Int = 0,
)

data class TrainingReference(
    val totalAu: Double? = null,
    val coverageDays: Int = 0,
    val weeklyEquivalentAu: Double? = null,
    val scaledForCoverage: Boolean = false,
    val equivalentAu: Double? = null,
)

data class TrainingStatus(
    val date: String? = null,
    val methodologyVersion: String? = null,
    val goal: String = "balanced",
    val feeling: String? = null,
    val acute: TrainingWindow = TrainingWindow(windowDays = 7),
    val chronic: TrainingWindow = TrainingWindow(windowDays = 28),
    val reference: TrainingReference = TrainingReference(),
    val chronicReference: TrainingReference = TrainingReference(),
    val chronicRelativeRatio: Double? = null,
    val chronicTrend: String = "insufficient",
    val relativeRatio: Double? = null,
    val loadTrend: String = "insufficient",
    val confidence: String = "insufficient",
    val mode: String = "insufficient",
    val focus: String = "easy_aerobic",
    val intensity: String = "conservative",
    val reasons: List<String> = emptyList(),
    val estimatedRatio: Double? = null,
    val reportedRatio: Double? = null,
    val hardDays3: Int = 0,
    val strengthDays7: Int? = null,
    val aerobicDays7: Int? = null,
)
