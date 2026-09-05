package com.thegreatnovel.jingyouhealth.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.text.BidiFormatter
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
import com.thegreatnovel.jingyouhealth.ui.theme.Amber
import com.thegreatnovel.jingyouhealth.ui.theme.ArcticBlue
import com.thegreatnovel.jingyouhealth.ui.theme.AuroraViolet
import com.thegreatnovel.jingyouhealth.ui.theme.ElectricCyan
import com.thegreatnovel.jingyouhealth.ui.theme.Rose
import kotlin.math.roundToInt

@Composable
fun JingYouApp(viewModel: JingYouViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProvideAppLanguage(state.language) {
        if (state.token == null) {
            LoginScreen(state = state, onConnect = viewModel::connectUsbDev)
        } else {
            MainShell(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun LoginScreen(state: JingYouUiState, onConnect: () -> Unit) {
    LaunchedEffect(Unit) {
        if (!state.connecting && state.token == null) onConnect()
    }
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
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Text("JingYou Health", style = MaterialTheme.typography.headlineLarge)
                Text(
                    tr("手机负责体验，电脑负责 Garmin、数据库和 Agent。"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                ActionButton(
                    text = if (state.connecting) tr("正在连接") else tr("连接 JingYou"),
                    onClick = onConnect,
                    enabled = !state.connecting,
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
                RootTab.TODAY -> TodayScreen(state, viewModel::refreshGarmin)
                RootTab.TRENDS -> TrendsScreen(state)
                RootTab.ACTIVITIES -> ActivitiesScreen(state)
                RootTab.COACH -> CoachScreen(state, viewModel)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusTop + 8.dp, end = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassIcon(Icons.Rounded.Settings, tr("设置")) { viewModel.setSettingsOpen(true) }
        }

        AnimatedVisibility(
            visible = !(selected == RootTab.COACH && imeVisible),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140)),
        ) {
            FloatingHealthDock(
                selected = selected,
                onSelect = { selected = it },
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = navBottom + 10.dp),
            )
        }
    }

    if (state.settingsOpen) {
        SettingsSheet(state = state, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayScreen(state: JingYouUiState, onRefresh: () -> Unit) {
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { TodayHeader(state.dashboard) }
            item { HealthStoryHero(state.dashboard) }
            item { RecoveryContributors(state.dashboard) }
            item { SecondarySignals(state.dashboard) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("最近运动"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text(tr("下拉同步 Garmin 最新数据"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.dashboard?.recentActivities.orEmpty(), key = { it.id }) { activity -> ActivityCard(activity) }
        }
    }
}

@Composable
private fun RefreshAura(fraction: Float, refreshing: Boolean, status: String?, modifier: Modifier = Modifier) {
    val progress = fraction.coerceIn(0f, 1.25f)
    val active by animateFloatAsState(if (refreshing) 1f else progress, tween(220), label = "refresh-progress")
    val alpha = if (refreshing) 1f else progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = 0.72f + active * 0.28f
                scaleY = 0.72f + active * 0.28f
            },
        contentAlignment = Alignment.Center,
    ) {
        GlassPanel(shape = RoundedCornerShape(999.dp), padding = PaddingValues(horizontal = 16.dp, vertical = 9.dp), accent = ElectricCyan) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = active * 260f })
                Spacer(Modifier.width(8.dp))
                Text(tr(status ?: "正在读取 Garmin"), style = MaterialTheme.typography.labelLarge)
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
    Column(Modifier.padding(top = 10.dp, end = 52.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Keep the greeting and the profile name on separate typographic lines. This is
        // visually calmer and avoids mixed Arabic/Latin BiDi punctuation on supported RTL.
        Text(greeting, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        dashboard?.user?.displayName?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.headlineLarge)
        }
    }
}

@Composable
private fun HealthStoryHero(dashboard: Dashboard?) {
    val scoreValue = dashboard?.readiness?.score ?: dashboard?.sleep?.score
    val score = scoreValue ?: 0.0
    val accent = semanticAccent(scoreValue)
    val (headline, guidance) = when {
        score >= 85 -> tr("恢复状态很好") to tr("今天可以按计划训练，身体已经准备好了。")
        score >= 70 -> tr("恢复状态不错") to tr("保持正常节奏，留意训练后的身体反馈。")
        score >= 50 -> tr("恢复状态一般") to tr("今天更适合稍微降低训练强度。")
        score > 0 -> tr("身体需要恢复") to tr("优先睡眠、补水和轻量活动，让身体缓一缓。")
        else -> tr("正在了解你的状态") to tr("同步更多数据后，我会把今天最重要的信号放在这里。")
    }
    val progress = (score / 100.0).toFloat().coerceIn(0f, 1f)

    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = HeroShape,
        padding = PaddingValues(horizontal = 22.dp, vertical = 24.dp),
        accent = accent,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            dashboard?.readiness?.level?.takeIf { it.isNotBlank() }?.let {
                StatusPill(it.replace('_', ' '), accent)
            }
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
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            if (score > 0) score.roundToInt().toString() else "—",
                            fontSize = 56.sp,
                            lineHeight = 58.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-1.3).sp,
                        )
                        if (score > 0) {
                            Text(
                                " / 100",
                                modifier = Modifier.padding(bottom = 7.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    tr("今天的身体状态"),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier = Modifier.padding(bottom = 9.dp),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
            ) {
                if (progress > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(5.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.58f), accent, ElectricCyan))),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecoveryContributors(dashboard: Dashboard?) {
    val hrv = dashboard?.hrv?.lastNightAvg?.roundToInt()?.let { "$it ms" } ?: "—"
    val sleep = dashboard?.sleep?.sleepSeconds?.let { "%.1f %s".format(it / 3600.0, tr("小时")) } ?: "—"
    val battery = dashboard?.bodyBattery?.value?.roundToInt()?.toString() ?: "—"
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(horizontal = 18.dp, vertical = 17.dp),
        accent = AuroraViolet,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(tr("影响恢复"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ContributorMetric(tr("昨夜 HRV"), hrv, AuroraViolet, Modifier.weight(1f))
                ContributorMetric(tr("睡眠"), sleep, ArcticBlue, Modifier.weight(1f))
                ContributorMetric(tr("身体电量"), battery, ElectricCyan, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ContributorMetric(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
        Text(bidiMetric(value), style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SecondarySignals(dashboard: Dashboard?) {
    val restingHr = dashboard?.daily?.restingHr?.roundToInt()?.let { "$it bpm" } ?: "—"
    val stress = dashboard?.daily?.avgStress?.roundToInt()?.toString() ?: "—"
    val steps = dashboard?.daily?.steps?.let { "%,d".format(it) } ?: "—"
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(horizontal = 18.dp, vertical = 17.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text(tr("今日信号"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SignalRow(tr("静息心率"), restingHr, Rose)
            SoftDivider()
            SignalRow(tr("压力"), stress, Amber)
            SoftDivider()
            SignalRow(tr("步数"), steps, ElectricCyan)
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
private fun TrendsScreen(state: JingYouUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 34.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.padding(end = 50.dp)) {
                Text(tr("趋势"), style = MaterialTheme.typography.headlineLarge)
                Text(tr("过去 30 天"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { TrendCollection(state) }
    }
}

@Composable
private fun TrendCollection(state: JingYouUiState) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            TrendSection(tr("HRV 趋势"), state.trends.hrv, "ms", AuroraViolet)
            SoftDivider()
            TrendSection(tr("静息心率趋势"), state.trends.restingHr, "bpm", Rose)
            SoftDivider()
            TrendSection(tr("睡眠时长"), state.trends.sleepHours, tr("小时"), ArcticBlue)
            SoftDivider()
            TrendSection(tr("压力趋势"), state.trends.stress, "", Amber)
        }
    }
}

@Composable
private fun TrendSection(title: String, points: List<TrendPoint>, unit: String, accent: Color) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(accent.copy(alpha = 0.9f)))
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(3.dp))
                Text(points.lastOrNull()?.date.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val latest = points.lastOrNull { it.value != null }?.value
            val display = if (latest != null) {
                if (unit.isBlank()) "%.1f".format(latest) else "%.1f %s".format(latest, unit)
            } else {
                "—"
            }
            Text(bidiMetric(display), style = MaterialTheme.typography.headlineMedium, color = accent)
        }
        Spacer(Modifier.height(12.dp))
        Sparkline(values = points.map { it.value }, modifier = Modifier.fillMaxWidth().height(82.dp), accent = accent)
    }
}

@Composable
private fun ActivitiesScreen(state: JingYouUiState) {
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
            Column(Modifier.padding(end = 50.dp)) {
                Text(tr("运动"), style = MaterialTheme.typography.headlineLarge)
                Text("${state.activities.size} ${tr("运动")}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(state.activities, key = { it.id }) { ActivityCard(it) }
    }
}

@Composable
private fun ActivityCard(activity: ActivitySummary) {
    PressableGlassPanel(
        onClick = {},
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
                Icons.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun CoachScreen(state: JingYouUiState, viewModel: JingYouViewModel) {
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val closedBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp
    val statuses = listOf("正在读取睡眠", "正在比较最近几周", "正在形成建议")
    LaunchedEffect(state.messages.size, state.coachThinking) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex + if (state.coachThinking) 1 else 0)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 28.dp)
            .imePadding()
            .padding(bottom = if (imeVisible) 8.dp else closedBottomPadding),
    ) {
        Row(Modifier.padding(horizontal = 18.dp).padding(end = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("教练"), style = MaterialTheme.typography.headlineLarge)
                Text(tr("问问你的身体"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GlassIcon(Icons.Rounded.Add, tr("开始新的对话"), viewModel::newThread)
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (state.messages.isEmpty()) {
                item {
                    GlassPanel(modifier = Modifier.fillMaxWidth(), accent = AuroraViolet) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(tr("问问你的身体"), style = MaterialTheme.typography.titleLarge)
                            Text(tr("比如：我今天适合跑 10km 吗？"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            items(state.messages, key = { it.id }) { ChatBubble(it) }
            if (state.coachThinking) {
                item { TypingBubble(tr(statuses[state.coachStatusIndex.coerceIn(statuses.indices)])) }
            }
        }
        ChatComposer(
            value = draft,
            onValueChange = { draft = it },
            onSend = {
                val text = draft.trim()
                if (text.isNotBlank()) {
                    draft = ""
                    viewModel.sendMessage(text)
                }
            },
            enabled = !state.coachThinking,
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val mine = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .clip(RoundedCornerShape(25.dp, 25.dp, if (mine) 8.dp else 25.dp, if (mine) 25.dp else 8.dp))
                .background(
                    if (mine) Brush.linearGradient(listOf(ArcticBlue, AuroraViolet))
                    else Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)))
                )
                .padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            Text(message.content, style = MaterialTheme.typography.bodyLarge, color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface)
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
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    if (value.isBlank()) Text(tr("比如：我今天适合跑 10km 吗？"), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f), style = MaterialTheme.typography.bodyLarge)
                    inner()
                },
            )
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(AuroraViolet, ArcticBlue)))
                    .clickable(enabled = enabled && value.isNotBlank(), onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Send, contentDescription = tr("发送"), tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(state: JingYouUiState, viewModel: JingYouViewModel) {
    ModalBottomSheet(onDismissRequest = { viewModel.setSettingsOpen(false) }, containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
    val bg = if (selected) Brush.linearGradient(listOf(AuroraViolet, ArcticBlue)) else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)))
    Box(
        modifier = modifier.clip(RoundedCornerShape(18.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun GlassIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    GlassPanel(shape = RoundedCornerShape(999.dp), padding = PaddingValues(0.dp), accent = AuroraViolet) {
        Box(Modifier.size(44.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
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
