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
import kotlinx.coroutines.async
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
    val settingsOpen: Boolean = false,
    val threads: List<ChatThread> = emptyList(),
    val activeThreadId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val coachThinking: Boolean = false,
    val coachStatusIndex: Int = 0,
)

class JingYouViewModel(application: Application) : AndroidViewModel(application) {
    private val api = HealthApi()
    private val prefs = application.getSharedPreferences("jingyou_health", 0)
    private val storedToken = prefs.getString("session_token", null)
    private val storedSessionBaseUrl = prefs.getString("session_base_url", null)
    private val currentToken = storedToken?.takeIf { storedSessionBaseUrl == BuildConfig.API_BASE_URL }

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
        )
    )
    val state: StateFlow<JingYouUiState> = _state.asStateFlow()

    init {
        if (_state.value.token != null) loadAll()
    }

    fun connectUsbDev() {
        if (_state.value.connecting) return
        viewModelScope.launch {
            _state.update { it.copy(connecting = true, error = null) }
            runCatching { api.devLogin(BuildConfig.DEV_PROFILE) }
                .onSuccess { acceptSessionToken(it) }
                .onFailure { error -> _state.update { it.copy(connecting = false, error = error.message ?: "Connection failed") } }
        }
    }

    fun acceptSessionToken(token: String) {
        prefs.edit()
            .putString("session_token", token)
            .putString("session_base_url", BuildConfig.API_BASE_URL)
            .apply()
        _state.update { it.copy(token = token, connecting = false, error = null) }
        loadAll()
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString("language", language.name).apply()
        _state.update { it.copy(language = language) }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme", mode.name).apply()
        _state.update { it.copy(themeMode = mode) }
    }

    fun setSettingsOpen(open: Boolean) {
        _state.update { it.copy(settingsOpen = open) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun loadAll() {
        val token = _state.value.token ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = it.dashboard == null, error = null) }
            runCatching {
                val dash = async { api.dashboard(token) }
                val trend = async { api.trends(token) }
                val acts = async { api.activities(token) }
                val threads = async { api.threads(token) }
                Quad(dash.await(), trend.await(), acts.await(), threads.await())
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        loading = false,
                        dashboard = result.a,
                        trends = result.b,
                        activities = result.c,
                        threads = result.d,
                    )
                }
                ensureThread()
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = error.message ?: "Load failed") }
            }
        }
    }

    fun refreshGarmin() {
        val token = _state.value.token ?: return
        if (_state.value.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, refreshStatus = "正在读取 Garmin", error = null) }
            runCatching {
                val dashboard = api.refresh(token)
                _state.update { it.copy(refreshStatus = "正在整理今天") }
                val trend = async { api.trends(token) }
                val acts = async { api.activities(token) }
                Triple(dashboard, trend.await(), acts.await())
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        dashboard = result.first,
                        trends = result.second,
                        activities = result.third,
                        refreshStatus = "已更新",
                    )
                }
                delay(650)
                _state.update { it.copy(refreshing = false, refreshStatus = null) }
            }.onFailure { error ->
                _state.update { it.copy(refreshing = false, refreshStatus = "同步失败", error = error.message ?: "Refresh failed") }
                delay(1000)
                _state.update { it.copy(refreshStatus = null) }
            }
        }
    }

    private fun ensureThread() {
        val token = _state.value.token ?: return
        if (_state.value.activeThreadId != null) return
        viewModelScope.launch {
            val first = _state.value.threads.firstOrNull()
            if (first != null) {
                openThread(first.id)
                return@launch
            }
            runCatching { api.createThread(token, "问问我的身体") }.onSuccess { thread ->
                _state.update { it.copy(threads = listOf(thread), activeThreadId = thread.id, messages = emptyList()) }
            }
        }
    }

    fun newThread() {
        val token = _state.value.token ?: return
        viewModelScope.launch {
            runCatching { api.createThread(token, "新对话") }.onSuccess { thread ->
                _state.update { it.copy(threads = listOf(thread) + it.threads, activeThreadId = thread.id, messages = emptyList()) }
            }
        }
    }

    fun openThread(threadId: String) {
        val token = _state.value.token ?: return
        viewModelScope.launch {
            runCatching { api.messages(token, threadId) }.onSuccess { messages ->
                _state.update { it.copy(activeThreadId = threadId, messages = messages) }
            }
        }
    }

    fun sendMessage(text: String) {
        val clean = text.trim()
        val token = _state.value.token ?: return
        val threadId = _state.value.activeThreadId ?: return
        if (clean.isBlank() || _state.value.coachThinking) return
        viewModelScope.launch {
            _state.update { it.copy(coachThinking = true, coachStatusIndex = 0, error = null) }
            val posted = runCatching { api.postMessage(token, threadId, clean) }
            if (posted.isFailure) {
                _state.update { it.copy(coachThinking = false, error = posted.exceptionOrNull()?.message ?: "Message failed") }
                return@launch
            }
            _state.update { it.copy(messages = it.messages + posted.getOrThrow()) }

            val statusJob = launch {
                while (true) {
                    delay(1200)
                    _state.update { it.copy(coachStatusIndex = (it.coachStatusIndex + 1) % 3) }
                }
            }
            val answer = runCatching { api.answer(token, threadId) }
            statusJob.cancel()
            answer
                .onSuccess { assistant ->
                    _state.update {
                        it.copy(
                            messages = it.messages + assistant,
                            coachThinking = false,
                            coachStatusIndex = 0,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(coachThinking = false, coachStatusIndex = 0, error = error.message ?: "Coach failed") }
                }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
