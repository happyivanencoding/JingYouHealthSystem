package com.thegreatnovel.jingyouhealth.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.text.BidiFormatter
import com.thegreatnovel.jingyouhealth.BuildConfig
import com.thegreatnovel.jingyouhealth.R
import com.thegreatnovel.jingyouhealth.model.ActivitySummary
import com.thegreatnovel.jingyouhealth.model.AppLanguage
import com.thegreatnovel.jingyouhealth.model.ChatMessage
import com.thegreatnovel.jingyouhealth.model.Dashboard
import com.thegreatnovel.jingyouhealth.model.RootTab
import com.thegreatnovel.jingyouhealth.model.ThemeMode
import com.thegreatnovel.jingyouhealth.model.TrendPoint
import com.thegreatnovel.jingyouhealth.ui.components.DynamicAmbientBackdrop
import com.thegreatnovel.jingyouhealth.ui.components.FloatingHealthDock
import com.thegreatnovel.jingyouhealth.ui.components.GlassPanel
import com.thegreatnovel.jingyouhealth.ui.components.HeroShape
import com.thegreatnovel.jingyouhealth.ui.components.MetricRing
import com.thegreatnovel.jingyouhealth.ui.components.PressableGlassPanel
import com.thegreatnovel.jingyouhealth.ui.components.Sparkline
import com.thegreatnovel.jingyouhealth.ui.components.StatusPill
import com.thegreatnovel.jingyouhealth.ui.components.TypingBubble
import com.thegreatnovel.jingyouhealth.ui.components.semanticAccent
import com.thegreatnovel.jingyouhealth.ui.components.ElasticPullState
import com.thegreatnovel.jingyouhealth.ui.components.elasticContentOffset
import com.thegreatnovel.jingyouhealth.ui.theme.Amber
import com.thegreatnovel.jingyouhealth.ui.theme.ArcticBlue
import com.thegreatnovel.jingyouhealth.ui.theme.AuroraViolet
import com.thegreatnovel.jingyouhealth.ui.theme.ElectricCyan
import com.thegreatnovel.jingyouhealth.ui.theme.LocalJingYouDarkTheme
import com.thegreatnovel.jingyouhealth.ui.theme.Rose
import kotlin.math.roundToInt

@Composable
fun JingYouApp(viewModel: JingYouViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProvideAppLanguage(state.language) {
        if (state.token == null) {
            LoginScreen(state = state)
        } else {
            MainShell(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun LoginScreen(state: JingYouUiState) {
    val context = LocalContext.current
    val loginUrl = "${BuildConfig.API_BASE_URL}/api/mobile-auth/bridge"
    Box(Modifier.fillMaxSize()) {
        DynamicAmbientBackdrop(tab = RootTab.COACH, modifier = Modifier.fillMaxSize(), energy = 0.82f)
        GlassPanel(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(36.dp),
            padding = PaddingValues(26.dp),
            accent = AuroraViolet,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(
                    Modifier
                        .size(78.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.linearGradient(listOf(AuroraViolet, ArcticBlue, ElectricCyan))),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(R.drawable.jingyou_symbol_v2), contentDescription = null, modifier = Modifier.fillMaxSize())
                }
                Text("JingYou Health", style = MaterialTheme.typography.headlineLarge)
                Text(
                    tr("通过浏览器安全登录，JingYou 会自动连接你的健康档案。"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                ActionButton(
                    text = tr("连接 JingYou"),
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(loginUrl)))
                    },
                    enabled = true,
                )
                state.error?.let {
                    Text(tr("连接失败"), color = Rose, style = MaterialTheme.typography.labelLarge)
                    Text(it.take(240), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun MainShell(state: JingYouUiState, viewModel: JingYouViewModel) {
    var selected by rememberSaveable { mutableStateOf(RootTab.TODAY) }
    var sleepOpen by rememberSaveable { mutableStateOf(false) }
    var metricOpen by rememberSaveable { mutableStateOf<HealthMetric?>(null) }
    var metricAnchor by rememberSaveable { mutableStateOf<String?>(null) }
    val detailState = rememberSaveableStateHolder()
    var pullFraction by remember { mutableFloatStateOf(0f) }
    var activityOpen by remember { mutableStateOf<ActivitySummary?>(null) }
    var activitiesOpen by rememberSaveable { mutableStateOf(false) }
    val detailOpen = sleepOpen || metricOpen != null || activitiesOpen
    val askCoach: (String) -> Unit = { question ->
        viewModel.setCoachDraft(question)
        sleepOpen = false
        metricOpen = null
        activitiesOpen = false
        selected = RootTab.COACH
    }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val energy = ((state.dashboard?.readiness?.score ?: state.dashboard?.bodyBattery?.value ?: 70.0) / 100.0).toFloat()

    Box(Modifier.fillMaxSize()) {
        DynamicAmbientBackdrop(
            tab = selected,
            modifier = Modifier.fillMaxSize(),
            energy = energy,
            stress = state.dashboard?.daily?.avgStress?.toFloat(),
            sleepScore = state.dashboard?.sleep?.score?.toFloat(),
            photoEnabled = state.travelAtmosphere,
            photoReveal = if (selected == RootTab.TODAY && !detailOpen) (pullFraction / 1.2f).coerceIn(0f, 1f) else 0f,
        )
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                (slideInHorizontally(tween(360)) { it / 5 * direction } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(330)) { -it / 6 * direction } + fadeOut(tween(250)))
            },
            label = "root-tabs",
            modifier = Modifier.fillMaxSize(),
        ) { tab ->
            when (tab) {
                RootTab.TODAY -> TodayScreen(state, viewModel::refreshGarmin, { sleepOpen = true }, { metric ->
                    metricAnchor = if (metric == HealthMetric.HRV) state.dashboard?.hrv?.date else state.dashboard?.daily?.date
                    metricOpen = metric
                }, { activitiesOpen = true }, { activityOpen = it }, { pullFraction = it })
                RootTab.TRENDS -> MetricExplorer(state, onSleep = { sleepOpen = true }, onAsk = askCoach)
                RootTab.ACTIVITIES -> ActivitiesScreen(state) { activityOpen = it }
                RootTab.COACH -> CoachScreen(state, viewModel)
            }
        }

        if (!detailOpen) Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .graphicsLayer { translationY = if (selected == RootTab.TODAY) elasticContentOffset(pullFraction).dp.toPx() else 0f }
                .padding(top = statusTop + 8.dp, end = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassIcon(Icons.Rounded.Settings, tr("设置")) { viewModel.setSettingsOpen(true) }
        }

        AnimatedVisibility(
            visible = !detailOpen && !(selected == RootTab.COACH && imeVisible),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140)),
        ) {
            FloatingHealthDock(
                selected = selected,
                onSelect = { selected = it },
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = navBottom + 10.dp)
                    .graphicsLayer { translationY = if (selected == RootTab.TODAY) elasticContentOffset(pullFraction).dp.toPx() else 0f },
            )
        }
        if (detailOpen) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                DynamicAmbientBackdrop(tab = RootTab.TRENDS, modifier = Modifier.fillMaxSize(), energy = energy, photoEnabled = state.travelAtmosphere)
                when {
                    metricOpen != null -> detailState.SaveableStateProvider("metric-${metricOpen}-${metricAnchor}") {
                        MetricExplorer(state, initial = metricOpen!!, anchorDate = metricAnchor, onBack = { metricOpen = null }, onSleep = { metricOpen = null; sleepOpen = true }, onAsk = askCoach)
                    }
                    sleepOpen -> detailState.SaveableStateProvider("sleep") {
                        SleepDetailScreen(state, { sleepOpen = false }, askCoach) { metric, date -> metricAnchor = date; metricOpen = metric }
                    }
                    activitiesOpen -> {
                        BackHandler { activitiesOpen = false }
                        ActivitiesScreen(state, onBack = { activitiesOpen = false }) { activityOpen = it }
                    }
                }
            }
        }
        if (state.loading && state.dashboard == null) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(tr("正在加载你的健康记录"), style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        state.error?.let { error ->
            GlassPanel(modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 18.dp).padding(bottom = if (imeVisible) 8.dp else navBottom + 90.dp), padding = PaddingValues(16.dp)) {
                Column {
                    Text(error, style = MaterialTheme.typography.bodyMedium)
                    Row {
                        if (!state.coachAnswerFailed && state.dashboard == null) TextButton(onClick = viewModel::loadAll) { Text(tr("重新加载")) }
                        TextButton(onClick = viewModel::clearError) { Text(tr("关闭")) }
                    }
                }
            }
        }
    }

    if (state.settingsOpen) {
        SettingsSheet(state = state, viewModel = viewModel)
    }
    activityOpen?.let { activity -> ActivityDetailSheet(activity) { activityOpen = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayScreen(state: JingYouUiState, onRefresh: () -> Unit, onSleep: () -> Unit,
                        onMetric: (HealthMetric) -> Unit, onActivities: () -> Unit, onActivity: (ActivitySummary) -> Unit,
                        onPull: (Float) -> Unit) {
    val pullState = remember { ElasticPullState() }
    val effectivePull = pullState.distanceFraction
    LaunchedEffect(pullState) { snapshotFlow { pullState.distanceFraction }.collect { onPull(it) } }
    DisposableEffect(Unit) { onDispose { onPull(0f) } }
    PullToRefreshBox(
        // The photo gesture is always available, even during a long Garmin request.
        // Material releases its gesture spring independently; the ViewModel owns
        // the real request and prevents concurrent syncs.
        isRefreshing = false,
        onRefresh = { if (!state.refreshing) onRefresh() },
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            RefreshAura(
                fraction = pullState.distanceFraction,
                refreshing = state.refreshing,
                status = state.refreshStatus,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().graphicsLayer { translationY = elasticContentOffset(effectivePull).dp.toPx() },
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Box(Modifier.graphicsLayer { alpha = (1f - effectivePull.coerceAtLeast(0f) / 1.2f).coerceIn(0f, 1f) }) {
                    TodayHeader(state.dashboard)
                }
            }
            item { HealthStoryHero(state.dashboard) }
            item { SleepEntry(state, onSleep) }
            item { RecoverySignalsPanel(state, onSleep, onMetric) }
            item { DailySignalsPanel(state, onMetric) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("最近运动"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onActivities) { Text(tr("查看全部")) }
                }
            }
            items(state.dashboard?.recentActivities.orEmpty(), key = { it.id }) { activity -> ActivityCard(activity) { onActivity(activity) } }
            item { Text(tr("下拉同步 Garmin 最新数据"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun RefreshAura(fraction: Float, refreshing: Boolean, status: String?, modifier: Modifier = Modifier) {
    if (!refreshing && fraction <= 0.01f) return
    val progress = fraction.coerceIn(0f, 1.25f)
    val active by animateFloatAsState(if (refreshing) 1f else progress, tween(220), label = "refresh-progress")
    val indicatorAlpha = if (refreshing) 1f else progress.coerceIn(0f, 1f)
    val rotation = if (refreshing) {
        val infinite = rememberInfiniteTransition(label = "refresh-orbit")
        val angle by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(1700, easing = LinearEasing)), label = "refresh-orbit-angle")
        angle
    } else active * 260f
    Box(
        modifier = modifier
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp)
            .graphicsLayer {
                this.alpha = indicatorAlpha
                scaleX = 0.72f + active * 0.28f
                scaleY = 0.72f + active * 0.28f
            },
        contentAlignment = Alignment.Center,
    ) {
        GlassPanel(shape = RoundedCornerShape(999.dp), padding = PaddingValues(horizontal = 16.dp, vertical = 9.dp), accent = ElectricCyan) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation })
                Spacer(Modifier.width(8.dp))
                Text(tr(status ?: if (fraction >= 1f) "松开即可同步" else "拉开，看看蓝瓷"), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun TodayHeader(dashboard: Dashboard?) {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> tr("早上好")
        in 12..17 -> tr("下午好")
        else -> tr("晚上好")
    }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, end = 52.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Keep the greeting and the profile name on separate typographic lines. This is
        // visually calmer and avoids mixed Arabic/Latin BiDi punctuation on supported RTL.
        Text(greeting, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        dashboard?.user?.displayName?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.headlineLarge)
        }
        dashboard?.date?.let { Text(tr("最新记录") + " · " + bidiMetric(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun HealthStoryHero(dashboard: Dashboard?) {
    val dark = LocalJingYouDarkTheme.current
    val scoreValue = dashboard?.readiness?.score
    val score = scoreValue ?: 0.0
    val accent = semanticAccent(scoreValue)
    val (headline, guidance) = when {
        score >= 85 -> tr("恢复状态很好") to tr("今天可以按计划训练，身体已经准备好了。")
        score >= 70 -> tr("恢复状态不错") to tr("保持正常节奏，留意训练后的身体反馈。")
        score >= 50 -> tr("恢复状态一般") to tr("今天更适合稍微降低训练强度。")
        scoreValue != null -> tr("身体需要恢复") to tr("优先睡眠、补水和轻量活动，让身体缓一缓。")
        else -> tr("恢复准备度暂不可用") to tr("同步后可查看 Garmin 恢复准备度")
    }
    val progress = (score / 100.0).toFloat().coerceIn(0f, 1f)
    val heroGradient = if (dark) {
        Brush.linearGradient(
            listOf(
                Color(0xFF171E33),
                Color(0xFF17302F),
                Color(0xFF261C39),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFFE9EEFF),
                Color(0xFFE4F3EF),
                Color(0xFFF1EAF8),
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HeroShape)
            .background(heroGradient)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = if (dark) 0.28f else 0.24f),
                        Color.Transparent,
                    ),
                    radius = 640f,
                ),
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = if (dark) 0.13f else 0.80f),
                        accent.copy(alpha = 0.10f),
                        Color.White.copy(alpha = if (dark) 0.04f else 0.30f),
                    ),
                ),
                HeroShape,
            )
            .padding(horizontal = 22.dp, vertical = 24.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(160.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            ElectricCyan.copy(alpha = if (dark) 0.13f else 0.20f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(132.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            AuroraViolet.copy(alpha = if (dark) 0.13f else 0.17f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(headline, style = MaterialTheme.typography.headlineMedium)
                Text(
                    guidance,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(tr("恢复准备度"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                      Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            if (scoreValue != null) score.roundToInt().toString() else "—",
                            fontSize = 58.sp,
                            lineHeight = 60.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-1.4).sp,
                        )
                        if (scoreValue != null) {
                            Text(
                                " / 100",
                                modifier = Modifier.padding(bottom = 7.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                      }
                    }
                }
                Text(
                    dashboard?.readiness?.date ?: tr("今天的身体状态"),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier = Modifier.padding(bottom = 9.dp),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
            ) {
                if (progress > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        AuroraViolet.copy(alpha = 0.78f),
                                        accent,
                                        ElectricCyan,
                                    ),
                                ),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalRow(label: String, value: String, accent: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(accent.copy(alpha = 0.88f)))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(bidiMetric(value), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SoftDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f)))
}

@Composable
private fun bidiMetric(value: String): String =
    if (LocalAppLanguage.current.rtl) BidiFormatter.getInstance(true).unicodeWrap(value) else value

@Composable
private fun ActivitiesScreen(state: JingYouUiState, onBack: (() -> Unit)? = null, onActivity: (ActivitySummary) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 34.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            if (onBack != null) DetailHeader(tr("运动"), "${state.activities.size} " + tr("运动"), onBack)
            else Column(Modifier.padding(end = 50.dp)) {
                Text(tr("运动"), style = MaterialTheme.typography.headlineLarge)
                Text("${state.activities.size} ${tr("运动")}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(state.activities, key = { it.id }) { activity -> ActivityCard(activity) { onActivity(activity) } }
    }
}

@Composable
private fun ActivityCard(activity: ActivitySummary, onClick: () -> Unit) {
    PressableGlassPanel(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        padding = PaddingValues(horizontal = 16.dp, vertical = 15.dp),
        accent = null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(15.dp)).background(ElectricCyan.copy(alpha = 0.09f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(activity.type.take(1).uppercase(), fontWeight = FontWeight.Bold, color = ElectricCyan.copy(alpha = 0.9f))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(activity.name.ifBlank { activity.type }, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(
                    bidiMetric(activity.startTime?.take(16)?.replace('T', ' ').orEmpty()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    activity.distanceM?.let {
                        Text(bidiMetric("%.1f %s".format(it / 1000.0, tr("公里"))), style = MaterialTheme.typography.labelLarge)
                    }
                    activity.durationS?.let {
                        Text(bidiMetric("${(it / 60.0).roundToInt()} ${tr("分钟")}"), style = MaterialTheme.typography.labelLarge)
                    }
                    activity.avgHr?.let {
                        Text(bidiMetric("${it.roundToInt()} bpm"), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoachScreen(state: JingYouUiState, viewModel: JingYouViewModel) {
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val busy = state.coachThinking || state.threadLoading
    LaunchedEffect(state.messages.size, state.coachThinking, state.activeThreadId) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex + if (state.coachThinking) 1 else 0)
    }
    Column(Modifier.fillMaxSize()
        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 28.dp)
        .imePadding().padding(bottom = if (imeVisible) 8.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp)) {
        Row(Modifier.padding(horizontal = 18.dp).padding(end = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("教练"), style = MaterialTheme.typography.headlineLarge)
                Text(tr("问问你的身体"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GlassIcon(Icons.Rounded.History, tr("历史对话"), enabled = !busy) { historyOpen = true }
            Spacer(Modifier.width(6.dp))
            GlassIcon(Icons.Rounded.Add, tr("开始新的对话"), enabled = !busy) { viewModel.newThread() }
        }
        if (state.threadLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp))
        if (state.messages.isEmpty() && !state.coachThinking) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.NightsStay, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(18.dp))
                Text(tr("从这一晚，了解自己"), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                listOf("为什么昨晚没睡好", "解释睡眠与 HRV 的关系", "训练之后怎么恢复").forEach { key ->
                    val question = tr(key)
                    QuietAction(question) { viewModel.setCoachDraft(question) }
                    Spacer(Modifier.height(8.dp))
                }
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.messages, key = { it.id }) { ChatBubble(it) }
                if (state.coachThinking) item { TypingBubble(tr("教练正在分析")) }
                if (state.coachAnswerFailed) item {
                    QuietAction(tr("继续生成回答"), viewModel::retryAnswer)
                }
            }
        }
        ChatComposer(state.coachDraft, viewModel::setCoachDraft, { viewModel.sendMessage(state.coachDraft) },
            enabled = !busy && !state.coachAnswerFailed && state.activeThreadId != null)
    }
    if (historyOpen) ModalBottomSheet(onDismissRequest = { historyOpen = false }, containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(tr("历史对话"), style = MaterialTheme.typography.headlineMedium) }
            if (state.threads.isEmpty()) item { Text(tr("暂无数据")) }
            items(state.threads, key = { it.id }) { thread ->
                QuietAction(thread.title + " · " + thread.updatedAt.take(10)) { viewModel.openThread(thread.id); historyOpen = false }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val mine = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (mine) 0.86f else 0.96f)
                .clip(RoundedCornerShape(25.dp, 25.dp, if (mine) 8.dp else 25.dp, if (mine) 25.dp else 8.dp))
                .background(
                    if (mine) Brush.linearGradient(listOf(ArcticBlue, AuroraViolet))
                    else Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)))
                )
                .padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            SelectionContainer {
                Text(readableCoachText(message.content), style = MaterialTheme.typography.bodyLarge, color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun ChatComposer(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, enabled: Boolean) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(30.dp),
        padding = PaddingValues(7.dp, 7.dp, 7.dp, 7.dp),
        accent = AuroraViolet,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(max = 160.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    if (value.isBlank()) Text(tr("问问你的身体"), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyLarge)
                    inner()
                },
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { alpha = if (enabled && value.isNotBlank()) 1f else 0.35f }
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(AuroraViolet, ArcticBlue)))
                    .clickable(enabled = enabled && value.isNotBlank(), onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = tr("发送"), tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(state: JingYouUiState, viewModel: JingYouViewModel) {
    ModalBottomSheet(onDismissRequest = { viewModel.setSettingsOpen(false) }, containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, contentDescription = null, tint = AuroraViolet)
                Spacer(Modifier.width(9.dp))
                Text(tr("设置"), style = MaterialTheme.typography.headlineMedium)
            }
            SettingSection(Icons.Rounded.DarkMode, tr("外观")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(tr("亮色"), state.themeMode == ThemeMode.LIGHT, Modifier.weight(1f)) { viewModel.setThemeMode(ThemeMode.LIGHT) }
                    ChoiceChip(tr("跟随系统"), state.themeMode == ThemeMode.SYSTEM, Modifier.weight(1f)) { viewModel.setThemeMode(ThemeMode.SYSTEM) }
                    ChoiceChip(tr("暗色"), state.themeMode == ThemeMode.DARK, Modifier.weight(1f)) { viewModel.setThemeMode(ThemeMode.DARK) }
                }
            }
            SettingSection(Icons.Rounded.Language, tr("语言")) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip("中文", state.language == AppLanguage.CHINESE, Modifier.weight(1f)) { viewModel.setLanguage(AppLanguage.CHINESE) }
                        ChoiceChip("English", state.language == AppLanguage.ENGLISH, Modifier.weight(1f)) { viewModel.setLanguage(AppLanguage.ENGLISH) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip("Français", state.language == AppLanguage.FRENCH, Modifier.weight(1f)) { viewModel.setLanguage(AppLanguage.FRENCH) }
                        ChoiceChip("العربية", state.language == AppLanguage.ARABIC, Modifier.weight(1f)) { viewModel.setLanguage(AppLanguage.ARABIC) }
                    }
                }
            }
            SettingSection(Icons.Rounded.MoreHoriz, tr("连接状态")) {
                GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(14.dp), accent = ElectricCyan) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(ElectricCyan))
                        Spacer(Modifier.width(10.dp))
                        Text(tr("已连接私人健康服务器"), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("旅行氛围"), style = MaterialTheme.typography.titleMedium)
                    Text(tr("蓝瓷与玻璃"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val travelLabel = tr("旅行氛围")
                Switch(state.travelAtmosphere, viewModel::setTravelAtmosphere, modifier = Modifier.semantics { contentDescription = travelLabel })
            }
            TextButton(onClick = viewModel::logout) { Text(tr("退出登录")) }
        }
    }
}

@Composable
private fun SettingSection(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    InsightChoice(text, selected, modifier, onClick)
}

@Composable
private fun GlassIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    GlassPanel(shape = RoundedCornerShape(999.dp), padding = PaddingValues(0.dp), accent = AuroraViolet) {
        Box(Modifier.size(48.dp).graphicsLayer { alpha = if (enabled) 1f else 0.4f }.clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
        }
    }
}

private fun readableCoachText(source: String) = buildAnnotatedString {
    val cleaned = source.replace(Regex("(?m)^#{1,6}\\s+"), "").replace(Regex("(?m)^[-*]\\s+"), "• ")
    var start = 0
    Regex("\\*\\*(.+?)\\*\\*").findAll(cleaned).forEach { match ->
        append(cleaned.substring(start, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(match.groupValues[1]) }
        start = match.range.last + 1
    }
    append(cleaned.substring(start))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityDetailSheet(activity: ActivitySummary, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(tr("运动详情"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(activity.name.ifBlank { activity.type }, style = MaterialTheme.typography.headlineMedium)
            Text(activity.startTime?.take(16)?.replace('T', ' ') ?: "—", style = MaterialTheme.typography.bodyMedium)
            listOf(
                Triple("公里", activity.distanceM?.div(1000), "km"),
                Triple("分钟", activity.durationS?.div(60), tr("分钟")),
                Triple("平均心率", activity.avgHr, "bpm"),
                Triple("最高心率", activity.maxHr, "bpm"),
                Triple("训练负荷", activity.trainingLoad, ""),
                Triple("训练效果", activity.trainingEffect, ""),
                Triple("热量", activity.calories, "kcal"),
            ).forEach { (label, value, unit) ->
                SignalRow(tr(label), value?.let { "%.1f %s".format(it, unit) } ?: "—", ArcticBlue)
            }
            Text(tr("数据由 Garmin 提供"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val alpha = if (enabled) 1f else 0.55f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(AuroraViolet, ArcticBlue, ElectricCyan)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, fontSize = 15.sp)
    }
}
