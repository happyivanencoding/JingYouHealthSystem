package com.thegreatnovel.jingyouhealth.model

data class TrainingLoadValues(
    val load7: Double? = null,
    val load28: Double? = null,
    val referenceWeekly: Double? = null,
    val recorded7: Double? = null,
    val recorded28: Double? = null,
    val reference28: Double? = null,
)

data class TrainingLoadPoint(
    val date: String,
    val coverage7: Int = 0,
    val coverage28: Int = 0,
    val all: TrainingLoadValues = TrainingLoadValues(),
    val categories: Map<String, TrainingLoadValues> = emptyMap(),
)
