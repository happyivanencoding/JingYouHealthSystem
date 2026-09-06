package com.thegreatnovel.jingyouhealth.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thegreatnovel.jingyouhealth.BuildConfig
import com.thegreatnovel.jingyouhealth.data.HealthApi
import com.thegreatnovel.jingyouhealth.model.ActivitySummary
import com.thegreatnovel.jingyouhealth.model.AppLanguage
import com.thegreatnovel.jingyouhealth.model.ChatMessage
import com.thegreatnovel.jingyouhealth.model.ChatThread
import com.thegreatnovel.jingyouhealth.model.Dashboard
import com.thegreatnovel.jingyouhealth.model.ThemeMode
import com.thegreatnovel.jingyouhealth.model.Trends
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


data class JingYouUiState(
    val token: String? = null,
    val connecting: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val refreshStatus: String? = null,
    val error: String? = null,
    val dashboard: Dashboard? = null,
    val trends: Trends = Trends(),
    val activities: List<ActivitySummary> = emptyList(),
    val language: AppLanguage = AppLanguage.CHINESE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val travelAtmosphere: Boolean = true,
    val settingsOpen: Boolean = false,
    val threads: List<ChatThread> = emptyList(),
    val activeThreadId: String? = null,
    val threadLoading: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val coachDraft: String = "",
    val coachThinking: Boolean = false,
    val coachAnswerFailed: Boolean = false,
    val coachStatusIndex: Int = 0,
)

class JingYouViewModel(application: Application) : AndroidViewModel(application) {
    private val api = HealthApi()
    private val prefs = application.getSharedPreferences("jingyou_health", 0)
    private val storedToken = prefs.getString("session_token", null)
    private val storedSessionBaseUrl = prefs.getString("session_base_url", null)
    private val currentToken = storedToken?.takeIf { storedSessionBaseUrl == BuildConfig.API_BASE_URL }
    private var sessionGeneration = 0L
    private var healthGeneration = 0L
    private var threadGeneration = 0L

    init {
        if (storedToken != null && currentToken == null) {
            prefs.edit().remove("session_token").remove("session_base_url").apply()
        }
    }

    private val _state = MutableStateFlow(
        JingYouUiState(
            token = currentToken,
            language = runCatching { AppLanguage.valueOf(prefs.getString("language", AppLanguage.CHINESE.name)!!) }.getOrDefault(AppLanguage.CHINESE),
            themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme", ThemeMode.SYSTEM.name)!!) }.getOrDefault(ThemeMode.SYSTEM),
            travelAtmosphere = prefs.getBoolean("travel_atmosphere", true),
        )
    )
    val state: StateFlow<JingYouUiState> = _state.asStateFlow()

    init {
        if (_state.value.token != null) loadAll()
    }

    fun connectUsbDev() {
        if (_state.value.connecting) return
        val generation = sessionGeneration
        _state.update { it.copy(connecting = true, error = null) }
        viewModelScope.launch {
            val result = requestResult { api.devLogin(BuildConfig.DEV_PROFILE) }
            if (generation != sessionGeneration) return@launch
            result
                .onSuccess { acceptSessionToken(it) }
                .onFailure { error ->
                    _state.update { it.copy(connecting = false, error = readableError(error, Failure.CONNECT)) }
                }
        }
    }

    fun acceptSessionToken(token: String) {
        val clean = token.trim().takeIf { it.isNotEmpty() } ?: return
        sessionGeneration++
        prefs.edit()
            .putString("session_token", clean)
            .putString("session_base_url", BuildConfig.API_BASE_URL)
            .apply()
        resetSessionState(clean)
        loadAll()
    }

    fun logout() {
        sessionGeneration++
        prefs.edit().remove("session_token").remove("session_base_url").apply()
        resetSessionState(null)
    }

    private fun resetSessionState(token: String?) {
        healthGeneration++
        threadGeneration++
        _state.update {
            JingYouUiState(
                token = token,
                language = it.language,
                themeMode = it.themeMode,
                travelAtmosphere = it.travelAtmosphere,
            )
        }
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString("language", language.name).apply()
        _state.update { it.copy(language = language) }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme", mode.name).apply()
        _state.update { it.copy(themeMode = mode) }
    }

    fun setTravelAtmosphere(enabled: Boolean) {
        prefs.edit().putBoolean("travel_atmosphere", enabled).apply()
        _state.update { it.copy(travelAtmosphere = enabled) }
    }

    fun setSettingsOpen(open: Boolean) {
        _state.update { it.copy(settingsOpen = open) }
    }

    fun setCoachDraft(text: String) = _state.update { it.copy(coachDraft = text) }

    fun clearCoachDraft() = setCoachDraft("")

    fun clearError() = _state.update { it.copy(error = null) }

    private fun isCurrentSession(token: String, generation: Long): Boolean =
        generation == sessionGeneration && _state.value.token == token

    private fun isCurrentThread(token: String, generation: Long, threadId: String): Boolean =
        isCurrentSession(token, generation) && _state.value.activeThreadId == threadId

    fun loadAll() {
        val token = _state.value.token ?: return
        if (_state.value.refreshing) return
        val generation = sessionGeneration
        val request = ++healthGeneration
        val threadsAtStart = threadGeneration
        _state.update { it.copy(loading = it.dashboard == null, error = null) }
        viewModelScope.launch {
            val result = requestResult {
                coroutineScope {
                    val dash = async { api.dashboard(token) }
                    val trend = async { api.trends(token) }
                    val acts = async { api.activities(token) }
                    val threads = async { api.threads(token) }
                    Quad(dash.await(), trend.await(), acts.await(), threads.await())
                }
            }
            if (!isCurrentSession(token, generation) || request != healthGeneration) return@launch
            result.onSuccess { loaded ->
                _state.update {
                    it.copy(
                        loading = false,
                        dashboard = loaded.a,
                        trends = loaded.b,
                        activities = loaded.c,
                        threads = if (threadsAtStart == threadGeneration) loaded.d else it.threads,
                    )
                }
                ensureThread()
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = readableError(error, Failure.LOAD)) }
            }
        }
    }

    fun refreshGarmin() {
        val token = _state.value.token ?: return
        if (_state.value.refreshing) return
        val generation = sessionGeneration
        val request = ++healthGeneration
        _state.update { it.copy(loading = false, refreshing = true, refreshStatus = "正在读取 Garmin", error = null) }
        viewModelScope.launch {
            val result = requestResult {
                val dashboard = api.refresh(token)
                if (isCurrentSession(token, generation) && request == healthGeneration) {
                    _state.update { it.copy(refreshStatus = "正在整理今天") }
                }
                coroutineScope {
                    val trend = async { api.trends(token) }
                    val acts = async { api.activities(token) }
                    Triple(dashboard, trend.await(), acts.await())
                }
            }
            if (!isCurrentSession(token, generation) || request != healthGeneration) return@launch
            result.onSuccess { refreshed ->
                _state.update {
                    it.copy(
                        dashboard = refreshed.first,
                        trends = refreshed.second,
                        activities = refreshed.third,
                        refreshStatus = "已更新",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(refreshStatus = "同步失败", error = readableError(error, Failure.REFRESH)) }
            }
            delay(if (result.isSuccess) 650 else 1000)
            if (isCurrentSession(token, generation) && request == healthGeneration) {
                _state.update { it.copy(refreshing = false, refreshStatus = null) }
                if (_state.value.activeThreadId == null) loadAll()
            }
        }
    }

    private fun ensureThread() {
        if (_state.value.activeThreadId != null || _state.value.threadLoading || _state.value.coachThinking) return
        val first = _state.value.threads.firstOrNull()
        if (first != null) openThread(first.id) else newThread()
    }

    fun newThread() {
        val token = _state.value.token ?: return
        if (_state.value.threadLoading || _state.value.coachThinking) return
        val generation = sessionGeneration
        val request = ++threadGeneration
        _state.update { it.copy(threadLoading = true, error = null) }
        viewModelScope.launch {
            val result = requestResult { api.createThread(token, "新对话") }
            if (!isCurrentSession(token, generation) || request != threadGeneration) return@launch
            result.onSuccess { thread ->
                _state.update {
                    it.copy(
                        threads = listOf(thread) + it.threads.filterNot { old -> old.id == thread.id },
                        activeThreadId = thread.id,
                        messages = emptyList(),
                        threadLoading = false,
                        coachAnswerFailed = false,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(threadLoading = false, error = readableError(error, Failure.NEW_THREAD)) }
            }
        }
    }

    fun openThread(threadId: String) {
        val token = _state.value.token ?: return
        if (threadId.isBlank() || _state.value.threadLoading || _state.value.coachThinking) return
        val generation = sessionGeneration
        val request = ++threadGeneration
        _state.update { it.copy(threadLoading = true, error = null) }
        viewModelScope.launch {
            val result = requestResult { api.messages(token, threadId) }
            if (!isCurrentSession(token, generation) || request != threadGeneration) return@launch
            result.onSuccess { messages ->
                _state.update {
                    it.copy(
                        activeThreadId = threadId,
                        messages = messages,
                        threadLoading = false,
                        coachAnswerFailed = messages.hasUnansweredQuestion(),
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(threadLoading = false, error = readableError(error, Failure.OPEN_THREAD)) }
            }
        }
    }

    fun sendMessage(text: String) {
        val clean = text.trim()
        val token = _state.value.token ?: return
        val threadId = _state.value.activeThreadId ?: return
        if (clean.isBlank() || _state.value.coachThinking || _state.value.threadLoading) return
        val generation = sessionGeneration
        val draftAtSend = _state.value.coachDraft
        val pending = ChatMessage(
            id = "local_${UUID.randomUUID()}",
            role = "user",
            content = clean,
            createdAt = Instant.now().toString(),
            status = "sending",
        )
        // Publish before starting the request so the conversation responds to the tap immediately.
        _state.update {
            it.copy(
                messages = it.messages + pending,
                coachThinking = true,
                coachAnswerFailed = false,
                coachStatusIndex = 0,
                error = null,
            )
        }
        viewModelScope.launch {
            val posted = requestResult { api.postMessage(token, threadId, clean) }
            if (!isCurrentThread(token, generation, threadId)) return@launch
            if (posted.isFailure) {
                _state.update {
                    it.copy(
                        messages = it.messages.filterNot { message -> message.id == pending.id },
                        coachDraft = it.coachDraft.ifBlank { clean },
                        coachThinking = false,
                        coachAnswerFailed = it.messages.filterNot { message -> message.id == pending.id }.hasUnansweredQuestion(),
                        error = readableError(posted.exceptionOrNull(), Failure.SEND),
                    )
                }
                return@launch
            }
            val message = posted.getOrThrow()
            threadGeneration++
            _state.update {
                it.copy(
                    messages = it.messages.map { old -> if (old.id == pending.id) message else old },
                    coachDraft = if (it.coachDraft == draftAtSend || it.coachDraft.trim() == clean) "" else it.coachDraft,
                    threads = it.threads.touchThread(threadId, message.createdAt),
                )
            }
            requestAnswer(token, generation, threadId)
        }
    }

    fun retryAnswer() {
        val token = _state.value.token ?: return
        val threadId = _state.value.activeThreadId ?: return
        if (!_state.value.coachAnswerFailed || _state.value.coachThinking || _state.value.threadLoading) return
        val generation = sessionGeneration
        _state.update { it.copy(coachThinking = true, coachAnswerFailed = false, coachStatusIndex = 0, error = null) }
        viewModelScope.launch {
            // A timed-out response may already have been saved. Recover it before generating again.
            val history = requestResult { api.messages(token, threadId) }
            if (!isCurrentThread(token, generation, threadId)) return@launch
            if (history.isFailure) {
                _state.update {
                    it.copy(coachThinking = false, coachAnswerFailed = true, error = readableError(history.exceptionOrNull(), Failure.ANSWER))
                }
                return@launch
            }
            val messages = history.getOrThrow()
            _state.update { it.copy(messages = messages) }
            if (!messages.hasUnansweredQuestion()) {
                _state.update { it.copy(coachThinking = false, coachAnswerFailed = false) }
                return@launch
            }
            requestAnswer(token, generation, threadId)
        }
    }

    private suspend fun requestAnswer(token: String, generation: Long, threadId: String) {
        val answer = requestResult { api.answer(token, threadId) }
        if (!isCurrentThread(token, generation, threadId)) return
        answer.onSuccess { assistant ->
            threadGeneration++
            _state.update {
                val question = it.messages.firstOrNull { message -> message.role == "user" }?.content
                it.copy(
                    messages = it.messages.filterNot { message -> message.id == assistant.id } + assistant,
                    threads = it.threads.touchThread(threadId, assistant.createdAt, question),
                    coachThinking = false,
                    coachAnswerFailed = false,
                    coachStatusIndex = 0,
                )
            }
        }.onFailure { error ->
            _state.update {
                it.copy(coachThinking = false, coachAnswerFailed = true, coachStatusIndex = 0, error = readableError(error, Failure.ANSWER))
            }
        }
    }

    private fun readableError(error: Throwable?, failure: Failure): String {
        val message = error?.message.orEmpty()
        if (message.startsWith("HTTP 401") || message.startsWith("HTTP 403")) {
            return localized("登录已过期，请在设置中退出后重新连接。", "Your session has expired. Sign out in Settings and connect again.", "Votre session a expiré. Déconnectez-vous dans les réglages, puis reconnectez-vous.", "انتهت صلاحية الجلسة. سجّل الخروج من الإعدادات ثم اتصل مجددًا.")
        }
        if (error is UnknownHostException || error is ConnectException) {
            return localized("暂时无法连接，请检查网络后重试。", "Unable to connect. Check your connection and try again.", "Connexion impossible. Vérifiez votre réseau et réessayez.", "تعذّر الاتصال. تحقّق من الشبكة وحاول مجددًا.")
        }
        if (failure == Failure.ANSWER) {
            return localized("回答暂时没有完成，问题已保存。可以重试回答。", "The answer did not finish. Your question is saved; you can retry the answer.", "La réponse n’a pas abouti. Votre question est enregistrée ; vous pouvez réessayer.", "لم تكتمل الإجابة. تم حفظ سؤالك، ويمكنك إعادة محاولة الإجابة.")
        }
        if (error is SocketTimeoutException) {
            return localized("连接超时，请稍后重试。", "The connection timed out. Please try again.", "Le délai de connexion est dépassé. Réessayez.", "انتهت مهلة الاتصال. حاول مجددًا.")
        }
        return when (failure) {
            Failure.CONNECT -> localized("暂时无法登录，请重试。", "Unable to sign in. Please try again.", "Connexion impossible. Réessayez.", "تعذّر تسجيل الدخول. حاول مجددًا.")
            Failure.LOAD -> localized("数据未能加载，请重试。", "Your data could not be loaded. Please try again.", "Vos données n’ont pas pu être chargées. Réessayez.", "تعذّر تحميل بياناتك. حاول مجددًا.")
            Failure.REFRESH -> localized("同步暂时未完成，已有数据仍然保留。", "Sync did not finish. Your existing data is still available.", "La synchronisation n’a pas abouti. Vos données restent disponibles.", "لم تكتمل المزامنة. لا تزال بياناتك السابقة متاحة.")
            Failure.NEW_THREAD -> localized("未能创建对话，请重试。", "Unable to start a conversation. Please try again.", "Impossible de créer une conversation. Réessayez.", "تعذّر بدء محادثة. حاول مجددًا.")
            Failure.OPEN_THREAD -> localized("未能打开这段对话，请重试。", "Unable to open this conversation. Please try again.", "Impossible d’ouvrir cette conversation. Réessayez.", "تعذّر فتح هذه المحادثة. حاول مجددًا.")
            Failure.SEND -> localized("消息未能发送，草稿已保留。", "The message could not be sent. Your draft is saved.", "Le message n’a pas pu être envoyé. Votre brouillon est conservé.", "تعذّر إرسال الرسالة. تم الاحتفاظ بالمسودة.")
            Failure.ANSWER -> localized("回答暂时没有完成，可以重试。", "The answer did not finish. Please retry.", "La réponse n’a pas abouti. Réessayez.", "لم تكتمل الإجابة. حاول مجددًا.")
        }
    }

    private fun localized(zh: String, en: String, fr: String, ar: String): String = when (_state.value.language) {
        AppLanguage.CHINESE -> zh
        AppLanguage.ENGLISH -> en
        AppLanguage.FRENCH -> fr
        AppLanguage.ARABIC -> ar
    }
}

private enum class Failure { CONNECT, LOAD, REFRESH, NEW_THREAD, OPEN_THREAD, SEND, ANSWER }

private suspend fun <T> requestResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

private fun List<ChatMessage>.hasUnansweredQuestion(): Boolean =
    lastOrNull { it.role == "user" || it.role == "assistant" }?.role == "user"

private fun List<ChatThread>.touchThread(threadId: String, updatedAt: String, firstQuestion: String? = null): List<ChatThread> {
    val thread = firstOrNull { it.id == threadId } ?: return this
    val title = if (thread.title in listOf("新对话", "问问我的身体") && !firstQuestion.isNullOrBlank()) {
        firstQuestion.trim().replace('\n', ' ').take(36)
    } else thread.title
    return listOf(thread.copy(title = title, updatedAt = updatedAt)) + filterNot { it.id == threadId }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
