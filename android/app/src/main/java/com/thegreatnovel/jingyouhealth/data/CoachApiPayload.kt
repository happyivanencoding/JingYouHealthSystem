package com.thegreatnovel.jingyouhealth.data

import com.thegreatnovel.jingyouhealth.model.CoachSleepSnapshot
import org.json.JSONArray
import org.json.JSONObject

internal fun CoachSleepSnapshot.toCoachJson(): JSONObject = JSONObject()
    .put("schema_version", schemaVersion)
    .put("source", source)
    .put("through_date", throughDate)
    .put("generated_at", generatedAt)
    .put("french_holidays", frenchHolidays)
    .put("models", JSONArray().also { array ->
        models.forEach { model ->
            array.put(JSONObject()
                .put("outcome", model.outcome.name)
                .put("status", model.status.name)
                .put("algorithm", model.algorithm.name)
                .put("factor_a", model.factorA?.name ?: JSONObject.NULL)
                .put("factor_b", model.factorB?.name ?: JSONObject.NULL)
                .put("feature_pack", model.featurePack?.name ?: JSONObject.NULL)
                .put("lag_days", model.lagDays ?: JSONObject.NULL)
                .put("train_n", model.trainN)
                .put("validation_n", model.validationN)
                .put("validation_start", model.validationStart ?: JSONObject.NULL)
                .put("validation_end", model.validationEnd ?: JSONObject.NULL)
                .put("selection_mae", model.selectionMae ?: JSONObject.NULL)
                .put("selection_reference_mae", model.selectionReferenceMae ?: JSONObject.NULL)
                .put("mae", model.mae ?: JSONObject.NULL)
                .put("reference_mae", model.referenceMae ?: JSONObject.NULL)
                .put("feature_importance", JSONArray().also { values ->
                    model.featureImportance.forEach { item -> values.put(JSONObject()
                        .put("feature", item.feature)
                        .put("mae_increase", item.maeIncrease)
                        .put("repeat_sd", item.repeatSd)) }
                })
                .put("dropped_features", JSONArray(model.droppedFeatures)))
        }
    })
    .put("timing", JSONObject()
        .put("night_count", timing.nightCount)
        .put("usual_bedtime_hour", timing.usualBedtimeHour ?: JSONObject.NULL)
        .put("usual_wake_hour", timing.usualWakeHour ?: JSONObject.NULL)
        .put("late_count", timing.lateCount)
        .put("other_count", timing.otherCount)
        .put("bedtime_shift_hours", timing.bedtimeShiftHours ?: JSONObject.NULL)
        .put("wake_shift_hours", timing.wakeShiftHours ?: JSONObject.NULL)
        .put("late_sleep_hours", timing.lateSleepHours ?: JSONObject.NULL)
        .put("other_sleep_hours", timing.otherSleepHours ?: JSONObject.NULL)
        .put("late_deep_hours", timing.lateDeepHours ?: JSONObject.NULL)
        .put("other_deep_hours", timing.otherDeepHours ?: JSONObject.NULL)
        .put("late_rem_hours", timing.lateRemHours ?: JSONObject.NULL)
        .put("other_rem_hours", timing.otherRemHours ?: JSONObject.NULL))
