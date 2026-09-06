package com.thegreatnovel.jingyouhealth.data

import com.thegreatnovel.jingyouhealth.BuildConfig
import com.thegreatnovel.jingyouhealth.model.ActivitySummary
import com.thegreatnovel.jingyouhealth.model.BodyBatterySummary
import com.thegreatnovel.jingyouhealth.model.ChatMessage
import com.thegreatnovel.jingyouhealth.model.ChatThread
import com.thegreatnovel.jingyouhealth.model.DailySummary
import com.thegreatnovel.jingyouhealth.model.Dashboard
import com.thegreatnovel.jingyouhealth.model.HrvSummary
import com.thegreatnovel.jingyouhealth.model.MetricFreshness
import com.thegreatnovel.jingyouhealth.model.ReadinessSummary
import com.thegreatnovel.jingyouhealth.model.SleepSummary
import com.thegreatnovel.jingyouhealth.model.TrendPoint
import com.thegreatnovel.jingyouhealth.model.Trends
import com.thegreatnovel.jingyouhealth.model.UserSummary
import com.thegreatnovel.jingyouhealth.model.RecoveryComponent
import com.thegreatnovel.jingyouhealth.model.SleepClockPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

class HealthApi(private val baseUrl: String = BuildConfig.API_BASE_URL) {
    suspend fun devLogin(profile: String): String = withContext(Dispatchers.IO) {
        val json = request("POST", "/api/dev/login/${profile.encodePath()}", null, null)
        JSONObject(json).getString("token")
    }

    suspend fun dashboard(token: String): Dashboard = withContext(Dispatchers.IO) {
        parseDashboard(JSONObject(request("GET", "/api/dashboard", token, null)))
    }

    suspend fun refresh(token: String): Dashboard = withContext(Dispatchers.IO) {
        val payload = JSONObject(request("POST", "/api/refresh", token, null))
        parseDashboard(payload.getJSONObject("dashboard"))
    }

    suspend fun trends(token: String, days: Int = 180): Trends = withContext(Dispatchers.IO) {
        parseTrends(JSONObject(request("GET", "/api/trends?days=$days", token, null)))
    }

    suspend fun activities(token: String): List<ActivitySummary> = withContext(Dispatchers.IO) {
        val records = linkedMapOf<String, ActivitySummary>()
        var offset = 0
        while (true) {
            val page = parseActivities(JSONArray(request("GET", "/api/activities?limit=200&offset=$offset", token, null)))
            val before = records.size
            page.forEach { records.putIfAbsent(it.id, it) }
            if (page.size < 200 || records.size == before) break
            offset += 200
        }
        records.values.toList()
    }

    suspend fun setActivityEffort(token: String, activityId: String, rpe: Double?, category: String?) = withContext(Dispatchers.IO) {
        request("PUT", "/api/activities/${activityId.encodePath()}/effort", token,
            JSONObject().put("rpe", rpe ?: JSONObject.NULL).put("category", category ?: JSONObject.NULL).toString())
        Unit
    }

    suspend fun threads(token: String): List<ChatThread> = withContext(Dispatchers.IO) {
        val array = JSONArray(request("GET", "/api/chat/threads", token, null))
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(ChatThread(item.getString("id"), item.optString("title"), item.optString("updated_at")))
            }
        }
    }

    suspend fun createThread(token: String, title: String): ChatThread = withContext(Dispatchers.IO) {
        val item = JSONObject(request("POST", "/api/chat/threads", token, JSONObject().put("title", title).toString()))
        ChatThread(item.getString("id"), item.optString("title"), item.optString("updated_at"))
    }

    suspend fun messages(token: String, threadId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val array = JSONArray(request("GET", "/api/chat/threads/$threadId/messages", token, null))
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    ChatMessage(
                        id = item.getString("id"),
                        role = item.optString("role"),
                        content = item.optString("content"),
                        createdAt = item.optString("created_at"),
                        status = item.optString("status", "complete"),
                    )
                )
            }
        }
    }

    suspend fun postMessage(token: String, threadId: String, content: String): ChatMessage = withContext(Dispatchers.IO) {
        parseMessage(
            JSONObject(
                request(
                    "POST",
                    "/api/chat/threads/$threadId/messages",
                    token,
                    JSONObject().put("content", content).toString(),
                )
            )
        )
    }

    suspend fun answer(token: String, threadId: String): ChatMessage = withContext(Dispatchers.IO) {
        parseMessage(JSONObject(request("POST", "/api/chat/threads/$threadId/answer", token, null)))
    }

    private fun parseMessage(item: JSONObject): ChatMessage = ChatMessage(
        id = item.getString("id"),
        role = item.optString("role"),
        content = item.optString("content"),
        createdAt = item.optString("created_at"),
        status = item.optString("status", "complete"),
    )

    private fun request(method: String, path: String, token: String?, body: String?): String {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 8_000
        connection.readTimeout = 190_000
        connection.setRequestProperty("Accept", "application/json")
        if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${connection.responseCode}: $text")
        }
        return text
    }

    private fun parseDashboard(root: JSONObject): Dashboard {
        val userObj = root.optJSONObject("user")
        val daily = root.optJSONObject("daily")
        val hrv = root.optJSONObject("hrv")
        val sleep = root.optJSONObject("sleep")
        val readiness = root.optJSONObject("readiness")
        val battery = root.optJSONObject("body_battery")
        val freshness = root.optJSONObject("freshness")
        return Dashboard(
            user = UserSummary(userObj?.optString("display_name").orEmpty(), userObj?.optString("role").orEmpty()),
            date = root.optStringOrNull("date"),
            daily = daily?.let {
                DailySummary(
                    date = it.optStringOrNull("date"),
                    steps = it.optNullableInt("steps"),
                    restingHr = it.optNullableDouble("resting_hr"),
                    avgStress = it.optNullableDouble("avg_stress"),
                    calories = it.optNullableDouble("calories"),
                    activeMin = it.optNullableDouble("active_min"),
                    bodyBatteryCharged = it.optNullableDouble("body_battery_charged"),
                    bodyBatteryDrained = it.optNullableDouble("body_battery_drained"),
                )
            },
            hrv = hrv?.let {
                HrvSummary(
                    date = it.optStringOrNull("date"),
                    status = it.optStringOrNull("status"),
                    weeklyAvg = it.optNullableDouble("weekly_avg"),
                    lastNightAvg = it.optNullableDouble("last_night_avg"),
                    lastNight5MinHigh = it.optNullableDouble("last_night_5min_high"),
                    baselineLow = it.optNullableDouble("baseline_balanced_low"),
                    baselineHigh = it.optNullableDouble("baseline_balanced_upper"),
                )
            },
            sleep = sleep?.let {
                SleepSummary(
                    date = it.optStringOrNull("date"),
                    score = it.optNullableDouble("sleep_score"),
                    sleepSeconds = it.optNullableDouble("sleep_time_sec"),
                    deepSeconds = it.optNullableDouble("deep_sleep_sec"),
                    remSeconds = it.optNullableDouble("rem_sleep_sec"),
                    lightSeconds = it.optNullableDouble("light_sleep_sec"),
                    awakeSeconds = it.optNullableDouble("awake_sleep_sec"),
                    start = it.optStringOrNull("sleep_start"),
                    end = it.optStringOrNull("sleep_end"),
                )
            },
            readiness = readiness?.let {
                ReadinessSummary(
                    score = it.optNullableDouble("score"),
                    level = it.optStringOrNull("level"),
                    recoveryTime = it.optNullableDouble("recovery_time"),
                    acuteLoad = it.optNullableDouble("acute_load"),
                    hrvWeeklyAverage = it.optNullableDouble("hrv_weekly_average"),
                    date = it.optStringOrNull("date"),
                    sleepScore = it.optNullableDouble("sleep_score"),
                    source = it.optStringOrNull("source"),
                    formulaVersion = it.optStringOrNull("formula_version"),
                    coverage = it.optNullableInt("coverage"),
                    components = buildList {
                        val values = it.optJSONArray("components") ?: JSONArray()
                        for (i in 0 until values.length()) {
                            val c = values.getJSONObject(i)
                            add(RecoveryComponent(c.optString("key"), c.optNullableDouble("score"), c.optNullableDouble("weight"), c.optNullableDouble("value"), c.optNullableDouble("baseline")))
                        }
                    },
                )
            },
            bodyBattery = battery?.let { BodyBatterySummary(it.optStringOrNull("timestamp"), it.optNullableDouble("value")) },
            recentActivities = parseActivities(root.optJSONArray("recent_activities") ?: JSONArray()),
            freshness = freshness?.let {
                MetricFreshness(
                    daily = it.optStringOrNull("daily"),
                    hrv = it.optStringOrNull("hrv"),
                    sleep = it.optStringOrNull("sleep"),
                    bodyBattery = it.optStringOrNull("body_battery"),
                    readiness = it.optStringOrNull("readiness"),
                )
            },
        )
    }

    private fun parseTrends(root: JSONObject): Trends {
        val hrv = root.optJSONArray("hrv") ?: JSONArray()
        val daily = root.optJSONArray("daily") ?: JSONArray()
        val sleep = root.optJSONArray("sleep") ?: JSONArray()
        return Trends(
            hrv = points(hrv, "last_night_avg") { it?.toFloat() },
            restingHr = points(daily, "resting_hr") { it?.toFloat() },
            stress = points(daily, "avg_stress") { it?.toFloat() },
            sleepHours = points(sleep, "sleep_time_sec") { value -> value?.div(3600.0)?.toFloat() },
            sleepScores = points(sleep, "sleep_score") { it?.toFloat() },
            deepHours = points(sleep, "deep_sleep_sec") { it?.div(3600.0)?.toFloat() },
            remHours = points(sleep, "rem_sleep_sec") { it?.div(3600.0)?.toFloat() },
            bodyBatteryCharged = points(daily, "body_battery_charged") { it?.toFloat() },
            bodyBatteryDrained = points(daily, "body_battery_drained") { it?.toFloat() },
            steps = points(daily, "steps") { it?.toFloat() },
            lightHours = points(sleep, "light_sleep_sec") { it?.div(3600.0)?.toFloat() },
            awakeHours = points(sleep, "awake_sleep_sec") { it?.div(3600.0)?.toFloat() },
            readiness = points(root.optJSONArray("readiness") ?: JSONArray(), "score") { it?.toFloat() },
            sleepClocks = buildList {
                for (i in 0 until sleep.length()) {
                    val item = sleep.getJSONObject(i)
                    add(SleepClockPoint(item.optString("date"), item.optStringOrNull("sleep_start_local"), item.optStringOrNull("sleep_end_local"), item.optBoolean("clock_offset_changed", false), item.optStringOrNull("clock_source")))
                }
            },
        )
    }

    private fun points(array: JSONArray, key: String, transform: (Double?) -> Float?): List<TrendPoint> = buildList {
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            add(TrendPoint(item.optString("date"), transform(item.optNullableDouble(key))))
        }
    }

    private fun parseActivities(array: JSONArray): List<ActivitySummary> = buildList {
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            add(
                ActivitySummary(
                    id = item.optString("activity_id"),
                    name = item.optString("activity_name"),
                    type = item.optString("activity_type"),
                    startTime = item.optStringOrNull("start_time"),
                    distanceM = item.optNullableDouble("distance_m"),
                    durationS = item.optNullableDouble("duration_s"),
                    avgHr = item.optNullableDouble("avg_hr"),
                    maxHr = item.optNullableDouble("max_hr"),
                    trainingLoad = item.optNullableDouble("internal_load"),
                    trainingEffect = item.optNullableDouble("training_effect"),
                    calories = item.optNullableDouble("calories"),
                    category = item.optStringOrNull("category"),
                    categoryOverride = item.optStringOrNull("category_override"),
                    effortRpe = item.optNullableDouble("effort_rpe").takeIf { item.optString("effort_source") == "reported" },
                    effortSource = item.optStringOrNull("effort_source"),
                    internalLoad = item.optNullableDouble("internal_load"),
                    anaerobicTrainingEffect = item.optNullableDouble("anaerobic_training_effect"),
                )
            )
        }
    }
}

private fun JSONObject.optNullableDouble(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

private fun JSONObject.optNullableInt(key: String): Int? =
    if (!has(key) || isNull(key)) null else optInt(key)

private fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

private fun String.encodePath(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
