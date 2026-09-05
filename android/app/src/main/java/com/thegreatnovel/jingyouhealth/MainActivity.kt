package com.thegreatnovel.jingyouhealth

import android.content.Intent
import android.os.Bundle
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

class MainActivity : ComponentActivity() {
    private val pendingSession = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingSession.value = intent?.data?.getQueryParameter("token")
        setContent {
            val vm: JingYouViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
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
