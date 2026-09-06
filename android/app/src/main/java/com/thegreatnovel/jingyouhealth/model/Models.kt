package com.thegreatnovel.jingyouhealth.model

enum class AppLanguage(val tag: String, val rtl: Boolean = false) {
    CHINESE("zh"),
    ENGLISH("en"),
    FRENCH("fr"),
    ARABIC("ar", rtl = true),
}

enum class ThemeMode { LIGHT, SYSTEM, DARK }

enum class RootTab { TODAY, SLEEP, COACH, ACTIVITIES, BODY, TRENDS }

enum class HomeModule { READINESS, SLEEP, RECOVERY_SIGNALS, DAILY_SIGNALS, ACTIVITIES, COACH }

data class UserSummary(
    val displayName: String = "",
    val role: String = "",
)

data class DailySummary(
    val date: String? = null,
    val steps: Int? = null,
    val restingHr: Double? = null,
    val avgStress: Double? = null,
    val calories: Double? = null,
    val activeMin: Double? = null,
    val bodyBatteryCharged: Double? = null,
    val bodyBatteryDrained: Double? = null,
)

data class HrvSummary(
    val date: String? = null,
    val status: String? = null,
    val weeklyAvg: Double? = null,
    val lastNightAvg: Double? = null,
    val lastNight5MinHigh: Double? = null,
    val baselineLow: Double? = null,
    val baselineHigh: Double? = null,
)

data class SleepSummary(
    val date: String? = null,
    val score: Double? = null,
    val sleepSeconds: Double? = null,
    val deepSeconds: Double? = null,
    val remSeconds: Double? = null,
    val lightSeconds: Double? = null,
    val awakeSeconds: Double? = null,
    val start: String? = null,
    val end: String? = null,
)

data class ReadinessSummary(
    val score: Double? = null,
    val level: String? = null,
    val recoveryTime: Double? = null,
    val acuteLoad: Double? = null,
    val hrvWeeklyAverage: Double? = null,
    val date: String? = null,
    val sleepScore: Double? = null,
    val source: String? = null,
    val formulaVersion: String? = null,
    val components: List<RecoveryComponent> = emptyList(),
    val coverage: Int? = null,
)

data class RecoveryComponent(val key: String, val score: Double?, val weight: Double?, val value: Double? = null, val baseline: Double? = null)

data class SleepClockPoint(val date: String, val startLocal: String?, val endLocal: String?, val offsetChanged: Boolean = false, val source: String? = null)

data class MetricFreshness(
    val daily: String? = null,
    val hrv: String? = null,
    val sleep: String? = null,
    val bodyBattery: String? = null,
    val readiness: String? = null,
)

data class BodyBatterySummary(
    val timestamp: String? = null,
    val value: Double? = null,
)

data class ActivitySummary(
    val id: String,
    val name: String = "",
    val type: String = "",
    val startTime: String? = null,
    val distanceM: Double? = null,
    val durationS: Double? = null,
    val avgHr: Double? = null,
    val maxHr: Double? = null,
    val trainingLoad: Double? = null,
    val trainingEffect: Double? = null,
    val calories: Double? = null,
    val category: String? = null,
    val categoryOverride: String? = null,
    val effortRpe: Double? = null,
    val effortSource: String? = null,
    val internalLoad: Double? = null,
    val anaerobicTrainingEffect: Double? = null,
)

data class Dashboard(
    val user: UserSummary = UserSummary(),
    val date: String? = null,
    val daily: DailySummary? = null,
    val hrv: HrvSummary? = null,
    val sleep: SleepSummary? = null,
    val readiness: ReadinessSummary? = null,
    val bodyBattery: BodyBatterySummary? = null,
    val recentActivities: List<ActivitySummary> = emptyList(),
    val freshness: MetricFreshness? = null,
)

data class TrendPoint(
    val date: String,
    val value: Float?,
)

data class Trends(
    val hrv: List<TrendPoint> = emptyList(),
    val restingHr: List<TrendPoint> = emptyList(),
    val sleepHours: List<TrendPoint> = emptyList(),
    val stress: List<TrendPoint> = emptyList(),
    val sleepScores: List<TrendPoint> = emptyList(),
    val deepHours: List<TrendPoint> = emptyList(),
    val remHours: List<TrendPoint> = emptyList(),
    // The current trends endpoint does not expose awake time; keep missing data missing.
    val awakeHours: List<TrendPoint> = emptyList(),
    val bodyBatteryCharged: List<TrendPoint> = emptyList(),
    val bodyBatteryDrained: List<TrendPoint> = emptyList(),
    val steps: List<TrendPoint> = emptyList(),
    val lightHours: List<TrendPoint> = emptyList(),
    val sleepClocks: List<SleepClockPoint> = emptyList(),
    val readiness: List<TrendPoint> = emptyList(),
)

data class ChatThread(
    val id: String,
    val title: String,
    val updatedAt: String,
)

data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val createdAt: String,
    val status: String = "complete",
)
