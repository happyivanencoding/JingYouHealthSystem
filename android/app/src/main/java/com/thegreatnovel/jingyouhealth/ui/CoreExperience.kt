package com.thegreatnovel.jingyouhealth.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import com.thegreatnovel.jingyouhealth.model.*
import com.thegreatnovel.jingyouhealth.ui.components.GlassPanel
import com.thegreatnovel.jingyouhealth.ui.theme.*
import java.util.Locale

@Composable
fun HomeCoachPrompt(onOpen: () -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(20.dp), accent = AuroraViolet) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("今天，想问身体什么？"), style = MaterialTheme.typography.titleLarge)
            Text(tr("把睡眠、训练和真实感受放在一起看。"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            QuietAction(tr("打开教练"), onOpen)
        }
    }
}

fun homeModuleLabel(module: HomeModule): String = when (module) {
    HomeModule.READINESS -> "恢复概览"
    HomeModule.SLEEP -> "睡眠复盘"
    HomeModule.RECOVERY_SIGNALS -> "恢复线索"
    HomeModule.DAILY_SIGNALS -> "身体信号"
    HomeModule.ACTIVITIES -> "最近运动"
    HomeModule.COACH -> "教练提问"
}

@Composable
fun HomeModulesEditor(modules: List<HomeModule>, onChange: (List<HomeModule>) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text(tr("安排我的主页"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ExpandMore, tr(if (open) "收起" else "展开"))
        }
        AnimatedVisibility(open) {
            Column {
                Text(tr("选择想看的模块，再调整它们的顺序。五个主要页面始终保留。"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                (modules + HomeModule.entries.filterNot { it in modules }).forEach { module ->
                    val index = modules.indexOf(module)
                    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(index >= 0, { checked -> onChange(if (checked) modules + module else modules - module) })
                        Text(tr(homeModuleLabel(module)), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(enabled = index > 0, onClick = {
                            val changed = modules.toMutableList(); changed.removeAt(index); changed.add(index - 1, module); onChange(changed)
                        }) { Icon(Icons.Rounded.ArrowUpward, tr("上移")) }
                        IconButton(enabled = index >= 0 && index < modules.lastIndex, onClick = {
                            val changed = modules.toMutableList(); changed.removeAt(index); changed.add(index + 1, module); onChange(changed)
                        }) { Icon(Icons.Rounded.ArrowDownward, tr("下移")) }
                    }
                }
            }
        }
    }
}

@Composable
fun RecoveryBreakdown(readiness: ReadinessSummary?) {
    var method by rememberSaveable { mutableStateOf(false) }
    val parts = readiness?.takeIf { it.source == "jingyou" }?.components.orEmpty()
    val labels = mapOf("sleep" to "睡眠储备", "hrv" to "心率变异恢复", "rhr" to "静息心率", "load" to "近期训练负荷")
    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(tr("恢复，由哪些信号组成"), style = MaterialTheme.typography.titleLarge)
            Text(tr("每项都先与自己的历史比较，再组成恢复参考。"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            parts.forEach { part ->
                val accent = when (part.key) { "sleep" -> ArcticBlue; "hrv" -> AuroraViolet; "rhr" -> Rose; else -> ElectricCyan }
                val progress by animateFloatAsState(((part.score ?: 0.0) / 100).toFloat().coerceIn(0f, 1f), tween(550), label = "recovery-${part.key}")
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row {
                        Text(tr(labels[part.key] ?: "其他信号"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(coreNumber(part.score), style = MaterialTheme.typography.labelLarge)
                    }
                    Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f))) {
                        if (part.score != null) Box(Modifier.fillMaxWidth(progress).height(7.dp).clip(CircleShape).background(accent))
                    }
                }
            }
            if (parts.isEmpty()) Text(tr("还在建立个人恢复基线"), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { method = !method }, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("我们的恢复参考怎么算"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Rounded.ExpandMore, tr(if (method) "收起" else "展开"))
            }
            AnimatedVisibility(method) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("睡眠占 40%，夜间 HRV 占 30%，静息心率占 20%，近期训练负荷占 10%。缺少的部分不补造，会调整可用权重。"), style = MaterialTheme.typography.bodyMedium)
                    Text(tr("使用此前 42 天个人基线；HRV 看近 7 天，静息心率看近 3 天。睡眠同时考虑这一晚和近 3 晚，负荷看近 3 天相对过去 28 天。"), style = MaterialTheme.typography.bodyMedium)
                    Text(tr("这是有依据的信号组成的初始参考规则，不是已有临床验证的统一量表，也不是 Garmin 准备度。"), style = MaterialTheme.typography.bodyMedium)
                    Text("${readiness?.formulaVersion.orEmpty()} · ${tr("可用组成")} ${readiness?.coverage ?: 0}/4", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun FeatureImportancePanel(result: RegressionResult, labels: Map<String, String>, unit: String) {
    var all by rememberSaveable { mutableStateOf(false) }
    val importance = result.featureImportances.sortedByDescending { it.increaseMae }
    if (importance.isEmpty()) return
    val scale = importance.maxOf { kotlin.math.abs(it.increaseMae) }.coerceAtLeast(0.001)
    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(tr("哪些信号，对这个模型更重要？"), style = MaterialTheme.typography.titleLarge)
            Text(tr("打乱一个信号后，预测会多错多少。条越长，模型越依赖它。"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if ((result.holdoutMAE ?: Double.MAX_VALUE) >= (result.controlMAE ?: 0.0)) Text(tr("这个模型尚未超过简单参考，重要性先作探索。"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (if (all) importance else importance.take(5)).forEach { item ->
                val positive = item.increaseMae > 0
                val accent = if (positive) ArcticBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Text(labels[item.key] ?: tr("其他信号"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(coreNumber(item.increaseMae, unit, 3), style = MaterialTheme.typography.labelMedium)
                    }
                    Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f))) {
                        Box(Modifier.fillMaxWidth((kotlin.math.abs(item.increaseMae) / scale).toFloat().coerceIn(0f, 1f)).height(7.dp).clip(CircleShape).background(accent))
                    }
                }
            }
            if (importance.size > 5) TextButton(onClick = { all = !all }) { Text(tr(if (all) "收起" else "查看全部信号")) }
            Text(tr("这是预测贡献，不是原因占比。相关信号会分摊信息；负数表示打乱后反而更准。"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (result.droppedFeatures.any { it.contains("holiday") }) Text(tr("部分节假日样本较少，暂不单独估计。"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun coreNumber(value: Double?, unit: String = "", decimals: Int = 0): String {
    if (unit == tr("小时")) return durationText(value)
    if (value == null || !value.isFinite()) return "—"
    val language = LocalAppLanguage.current
    val text = String.format(Locale.forLanguageTag(language.tag), "%.${decimals}f", value) + if (unit.isBlank()) "" else " $unit"
    return BidiFormatter.getInstance(language.rtl).unicodeWrap(text, TextDirectionHeuristicsCompat.LTR)
}
