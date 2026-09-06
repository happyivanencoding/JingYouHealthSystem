package com.thegreatnovel.jingyouhealth.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thegreatnovel.jingyouhealth.model.TrainingStatus
import com.thegreatnovel.jingyouhealth.ui.components.GlassPanel
import com.thegreatnovel.jingyouhealth.ui.theme.*
import kotlin.math.abs

private fun trainingGoalLabel(goal: String) = when (goal) {
    "endurance" -> "耐力表现"
    "strength" -> "力量与增肌"
    else -> "综合体能"
}

private fun trainingHeadline(training: TrainingStatus?) = when {
    training == null -> "先积累一些记录"
    training.mode == "recover" -> "今天先轻松一点"
    training.mode == "consolidate" -> "先巩固这一周"
    training.mode == "insufficient" || training.loadTrend == "insufficient" || training.reasons.any { it in setOf("recovery_missing", "recovery_signals_partial", "coverage_insufficient") } -> "先积累一些记录"
    training.focus == "strength" -> "给力量留个位置"
    training.loadTrend == "lighter" && training.focus == "easy_aerobic" -> "把有氧基础补起来"
    else -> "可以稳步积累"
}

private fun trainingReasonLabel(reason: String): String? = when (reason) {
    "short_sleep", "sleep_short", "sleep_below_target", "sleep_below_recovery_target" -> "睡眠偏短，先照顾恢复"
    "low_recovery", "recovery_low" -> "恢复信号偏低"
    "tired", "feeling_tired", "reported_tired", "checkin_tired" -> "你今天感觉疲劳"
    "high_recent_load", "load_rising", "rising_load", "recent_load_rising" -> "记录的负荷正在上升"
    "dense_intensity", "hard_days_recent", "recent_hard_days", "recent_intensity_dense" -> "近几天高强度较密集"
    "strength_gap", "strength_days_low", "goal_strength_gap" -> "近期力量训练较少"
    "aerobic_gap", "aerobic_days_low", "goal_aerobic_gap" -> "近期有氧活动较少"
    "recent_strength", "strength_recent" -> "最近刚做过力量训练"
    "insufficient_coverage", "missing_coverage", "missing_recovery", "coverage_insufficient", "recovery_missing", "coverage_or_recovery_missing", "recovery_signals_partial" -> "记录还不够完整"
    "mostly_estimated", "record_effort", "estimated_load", "record_effort_needed" -> "负荷主要来自估算"
    "goal_endurance", "goal_strength", "goal_balanced" -> "方向随你的目标调整"
    "goal_maintain", "steady_rhythm" -> "保持平常节奏"
    else -> null
}

@Composable
fun TrainingRhythmHero(state: JingYouUiState, onBody: () -> Unit, onActivities: () -> Unit, onMethod: () -> Unit, onAsk: () -> Unit) {
    val training = state.dashboard?.training
    val recovery = state.dashboard?.readiness?.score
    val focus = when {
        training?.mode == "recover" -> "先恢复，再安排训练"
        training?.focus == "strength" -> "适量力量"
        training?.focus == "maintain" -> "保持平常节奏"
        else -> "轻松有氧"
    }
    val accent = if (training?.mode in listOf("recover", "consolidate")) Amber else ElectricCyan
    val reasons = training?.reasons.orEmpty().mapNotNull(::trainingReasonLabel).filterNot { it in setOf("负荷主要来自估算", "保持平常节奏") }.distinct().take(2)
    val translatedReasons = reasons.map { tr(it) }
    GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(22.dp), accent = accent) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("JingYou 节奏"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                RecoveryMiniGauge(recovery, onBody)
                IconButton(onClick = onMethod) { Icon(Icons.Rounded.Info, tr("我们的计算方法"), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(tr(trainingHeadline(training)), style = MaterialTheme.typography.headlineMedium)
            if (reasons.isNotEmpty()) Text(translatedReasons.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LoadContextCard(tr("近 7 天刺激"), training?.acute?.totalAu, training?.reference?.weeklyEquivalentAu,
                training?.relativeRatio, training?.loadTrend ?: "insufficient", tr("平时每周参考"), training?.reference?.scaledForCoverage == true, onActivities)
            LoadContextCard(tr("近 28 天积累"), training?.chronic?.totalAu, training?.chronicReference?.equivalentAu,
                training?.chronicRelativeRatio, training?.chronicTrend ?: "insufficient", tr("上一段28天参考"), training?.chronicReference?.scaledForCoverage == true, onActivities)
            Text(tr("浅色带是参照附近，圆点是当前负荷。"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(when {
                    training?.mode == "recover" -> Icons.Rounded.NightsStay
                    training?.focus == "strength" -> Icons.Rounded.FitnessCenter
                    else -> Icons.AutoMirrored.Rounded.DirectionsRun
                }, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr(focus), style = MaterialTheme.typography.titleMedium)
                    Text(tr(trainingGoalLabel(training?.goal ?: "balanced")), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (training?.strengthDays7 != null && training.aerobicDays7 != null) {
                Text(tr("近 7 天训练分布") + " · " + tr("力量训练") + " ${training.strengthDays7} " + tr("天") + " · " + tr("有氧训练") + " ${training.aerobicDays7} " + tr("天"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (training?.estimatedRatio?.let { it >= 0.5 } == true) Text(tr("负荷主要来自估算"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onActivities, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(tr("查看滚动四周")) }
                FilledTonalButton(onClick = onAsk, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(tr("把训练交给 Coach")) }
            }
        }
    }
}

@Composable
private fun RecoveryMiniGauge(score: Double?, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.width(58.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                val stroke = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                drawArc(accent.copy(alpha = 0.12f), 135f, 270f, false, style = stroke)
                score?.let { drawArc(accent, 135f, (270 * it / 100).toFloat().coerceIn(0f, 270f), false, style = stroke) }
            }
            Text(coreNumber(score), fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
        Text(tr("恢复"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadContextCard(label: String, value: Double?, reference: Double?, ratio: Double?, trend: String, referenceLabel: String, scaled: Boolean, onClick: () -> Unit) {
    val status = when (trend) {
        "rising" -> "比参照高"
        "lighter" -> "比参照低"
        "usual" -> "接近参照"
        "building" -> "正在建立负荷参考"
        else -> "记录还不够完整"
    }
    val accent = when (trend) { "rising" -> AuroraViolet; "lighter" -> ArcticBlue; else -> ElectricCyan }
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.30f)).clickable(onClick = onClick).padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(tr(status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(CircleShape).background(accent.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 4.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(coreNumber(value, "AU"), fontSize = 23.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(referenceLabel, style = MaterialTheme.typography.labelSmall, color = ink)
                Text(coreNumber(reference, "AU"), style = MaterialTheme.typography.labelMedium, color = ink)
                if (scaled) Text(tr("按记录天数折算"), style = MaterialTheme.typography.labelSmall, color = ink)
            }
        }
        if (value != null && reference != null && reference > 0 && ratio != null) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Canvas(Modifier.fillMaxWidth().height(27.dp)) {
                    val inset = 9.dp.toPx(); val width = size.width - inset * 2; val center = size.height / 2
                    val low = reference * 0.75; val high = reference * 1.25
                    val maximum = maxOf(value, high).coerceAtLeast(1.0) * 1.08
                    fun x(amount: Double) = inset + (amount / maximum).toFloat().coerceIn(0f, 1f) * width
                    drawLine(ink.copy(alpha = 0.13f), Offset(inset, center), Offset(size.width - inset, center), 4.dp.toPx(), StrokeCap.Round)
                    drawRoundRect(accent.copy(alpha = 0.22f), Offset(x(low), center - 6.dp.toPx()), Size(x(high) - x(low), 12.dp.toPx()), CornerRadius(5.dp.toPx()))
                    drawLine(ink.copy(alpha = 0.52f), Offset(x(reference), center - 8.dp.toPx()), Offset(x(reference), center + 8.dp.toPx()), 1.dp.toPx())
                    drawCircle(accent.copy(alpha = 0.13f), 8.dp.toPx(), Offset(x(value), center))
                    drawCircle(accent, 4.dp.toPx(), Offset(x(value), center))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingMethodSheet(state: JingYouUiState, onGoal: (String) -> Unit, onFeeling: (String?) -> Unit, onDismiss: () -> Unit) {
    val training = state.dashboard?.training
    val uri = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(tr("我们的计算方法"), style = MaterialTheme.typography.headlineMedium)
            Text(tr("JingYou 节奏") + " · " + training?.methodologyVersion.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("选择你的训练目标"), style = MaterialTheme.typography.titleMedium)
                listOf("balanced", "endurance", "strength").forEach { goal ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = !state.savingTraining) { onGoal(goal) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = (training?.goal ?: "balanced") == goal, onClick = null)
                        Text(tr(trainingGoalLabel(goal)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(tr("你的目标会决定训练方向，每个账号单独保存。"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("今天感觉如何"), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("fresh" to "精神充足", "normal" to "感觉一般", "tired" to "有些疲劳").forEach { (value, label) ->
                        FilterChip(selected = training?.feeling == value, onClick = { onFeeling(value) }, enabled = !state.savingTraining,
                            label = { Text(tr(label)) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                    }
                }
                if (training?.feeling == null) Text(tr("尚未填写"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else TextButton(onClick = { onFeeling(null) }, enabled = !state.savingTraining) { Text(tr("清除今天的感受")) }
            }
            Text(tr("恢复看你的睡眠、HRV和静息心率等个人信号；刺激看最近7天，积累看最近28天。"), style = MaterialTheme.typography.bodyMedium)
            RecoveryBreakdown(state.dashboard?.readiness)
            Text(tr("AU = 运动分钟 × 用力程度；未自评时使用类别估算，并标记来源。"), style = MaterialTheme.typography.bodyMedium)
            Text(tr("最近7天与更早独立的28天每周参考比较，避免把当前这周重复放进参考。"), style = MaterialTheme.typography.bodyMedium)
            Text(tr("平时每周参考 = 此前28天记录的AU ÷ 有记录天数 × 7。"), style = MaterialTheme.typography.bodyMedium)
            Text(tr("28天参照使用再前面的独立28天，和当前28天不重叠。"), style = MaterialTheme.typography.bodyMedium)
            Text(tr("浅色带表示参照的75%至125%，不是训练安全区。"), style = MaterialTheme.typography.bodyMedium)
            listOf("超过平时25%或低于平时25%，只标记训练习惯的变化，不是安全线。", "当参考缺少记录时，不把缺失当作休息日。", "有氧和力量分别看训练天数与最近安排，不机械比较两类AU占比。", "综合体能以每周两天力量、两天有氧作为初始安排参考；同一天不会重复计数。", "恢复优先，再看负荷变化、最近强度、你的感受和目标。", "训练量较少不等于训练不足，训练量较高也不能单独诊断过度训练。", "这些是可检查的初始规则，会随方法版本更新；不是临床验证的统一量表。").forEach { Text(tr(it), style = MaterialTheme.typography.bodyMedium) }
            Text(tr("方法与依据"), style = MaterialTheme.typography.titleMedium)
            listOf("Foster · session-RPE" to "https://pubmed.ncbi.nlm.nih.gov/11708692/", "Saw · monitoring response" to "https://pubmed.ncbi.nlm.nih.gov/26423706/", "Impellizzeri · workload ratios" to "https://pubmed.ncbi.nlm.nih.gov/32502973/").forEach { (label, url) -> TextButton(onClick = { uri.openUri(url) }) { Text(label) } }
        }
    }
}
