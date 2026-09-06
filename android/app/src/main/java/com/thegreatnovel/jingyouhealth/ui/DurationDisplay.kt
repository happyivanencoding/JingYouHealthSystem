package com.thegreatnovel.jingyouhealth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import com.thegreatnovel.jingyouhealth.model.AppLanguage
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun durationText(hours: Double?): String {
    if (hours == null || !hours.isFinite()) return "—"
    val totalMinutes = (abs(hours) * 60).roundToInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    val language = LocalAppLanguage.current
    val text = if (totalMinutes == 0 && hours != 0.0) {
        val seconds = (abs(hours) * 3600).roundToInt()
        if (seconds == 0) tr("不足 1 秒") else "$seconds ${tr("秒") }"
    } else if (language == AppLanguage.CHINESE) {
        if (h == 0) "${m}分钟" else if (m == 0) "${h}小时" else "${h}小时${m}分钟"
    } else {
        if (h == 0) "$m ${tr("分钟")}" else if (m == 0) "$h ${tr("小时")}" else "$h ${tr("小时")} $m ${tr("分钟") }"
    }
    return BidiFormatter.getInstance(language.rtl).unicodeWrap((if (hours < 0) "−" else "") + text, TextDirectionHeuristicsCompat.LTR)
}

@Composable
fun SleepDurationDisplay(hours: Double?, size: Int = 40) {
    if (hours == null || !hours.isFinite()) { Text("—", fontSize = size.sp); return }
    val minutes = (hours.coerceAtLeast(0.0) * 60).roundToInt()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text((minutes / 60).toString(), modifier = Modifier.alignByBaseline(), fontSize = size.sp, fontWeight = FontWeight.Medium)
            Text(tr("小时"), modifier = Modifier.alignByBaseline(), fontSize = (size * 0.36).coerceAtLeast(11.0).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(3.dp))
            Text((minutes % 60).toString(), modifier = Modifier.alignByBaseline(), fontSize = (size * 0.82).sp, fontWeight = FontWeight.Medium)
            Text(tr("分钟"), modifier = Modifier.alignByBaseline(), fontSize = (size * 0.36).coerceAtLeast(11.0).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
