package com.thegreatnovel.jingyouhealth

import android.content.Intent
import android.content.Context
import android.content.res.Configuration
import android.app.LocaleManager
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thegreatnovel.jingyouhealth.ui.JingYouApp
import com.thegreatnovel.jingyouhealth.ui.JingYouViewModel
import com.thegreatnovel.jingyouhealth.ui.theme.JingYouTheme
import com.thegreatnovel.jingyouhealth.model.AppLanguage
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val pendingSession = mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        if (Build.VERSION.SDK_INT >= 33) {
            super.attachBaseContext(newBase)
        } else {
            val stored = newBase.getSharedPreferences("jingyou_health", 0).getString("language", AppLanguage.CHINESE.name)
            val language = runCatching { AppLanguage.valueOf(stored!!) }.getOrDefault(AppLanguage.CHINESE)
            val configuration = Configuration(newBase.resources.configuration).apply { setLocale(Locale.forLanguageTag(language.tag)) }
            super.attachBaseContext(newBase.createConfigurationContext(configuration))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingSession.value = intent?.data?.getQueryParameter("token")
        setContent {
            val vm: JingYouViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.language) {
                if (Build.VERSION.SDK_INT >= 33) {
                    val manager = getSystemService(LocaleManager::class.java)
                    val requested = LocaleList.forLanguageTags(state.language.tag)
                    if (manager.applicationLocales != requested) manager.applicationLocales = requested
                } else if (resources.configuration.locales[0].language != state.language.tag) {
                    recreate()
                }
            }
            LaunchedEffect(pendingSession.value) {
                pendingSession.value?.takeIf { it.isNotBlank() }?.let {
                    vm.acceptSessionToken(it)
                    pendingSession.value = null
                }
            }
            JingYouTheme(themeMode = state.themeMode) {
                JingYouApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingSession.value = intent.data?.getQueryParameter("token")
    }
}
