package com.thegreatnovel.jingyouhealth.model

enum class AppLanguage(val tag: String, val rtl: Boolean = false) {
    CHINESE("zh"),
    ENGLISH("en"),
    FRENCH("fr"),
    ARABIC("ar", rtl = true),
}

enum class ThemeMode { LIGHT, SYSTEM, DARK }

enum class RootTab { TODAY, TRENDS, ACTIVITIES, COACH }

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
