package com.thegreatnovel.jingyouhealth.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thegreatnovel.jingyouhealth.ui.components.GlassPanel

@Composable
fun CoachMemoryPanel(state: JingYouUiState, onLoad: () -> Unit, onForget: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
            expanded = !expanded
            if (expanded) onLoad()
        }, verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Coach 记住了什么"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ExpandMore, tr(if (expanded) "收起" else "展开"))
        }
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("目标、习惯和偏好会随对话逐渐积累。你可以直接纠正 Coach，也可以在这里让它忘记一条。"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    state.loadingCoachMemory -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    state.coachMemories.isEmpty() -> Text(tr("还没有长期记忆。继续聊聊你在意的事。"), style = MaterialTheme.typography.bodyMedium)
                    else -> state.coachMemories.forEach { item -> key(item.key) {
                        GlassPanel(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(item.text, style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(tr(if (item.confidence == "user_stated") "你告诉 Coach 的" else "待你确认的理解"), style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    TextButton(enabled = state.forgettingMemoryKey == null, onClick = { onForget(item.key) }) { Text(tr("忘记这条")) }
                                }
                            }
                        }
                    } }
                }
            }
        }
    }
}
