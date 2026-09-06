package com.thegreatnovel.jingyouhealth.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thegreatnovel.jingyouhealth.model.RootTab
import com.thegreatnovel.jingyouhealth.ui.tr
import com.thegreatnovel.jingyouhealth.ui.theme.Amber
import com.thegreatnovel.jingyouhealth.ui.theme.ArcticBlue
import com.thegreatnovel.jingyouhealth.ui.theme.AuroraViolet
import com.thegreatnovel.jingyouhealth.ui.theme.Ceramic
import com.thegreatnovel.jingyouhealth.ui.theme.DeepViolet
import com.thegreatnovel.jingyouhealth.ui.theme.ElectricCyan
import com.thegreatnovel.jingyouhealth.ui.theme.Graphite
import com.thegreatnovel.jingyouhealth.ui.theme.GlassBorderDark
import com.thegreatnovel.jingyouhealth.ui.theme.GlassBorderLight
import com.thegreatnovel.jingyouhealth.ui.theme.GlassDark
import com.thegreatnovel.jingyouhealth.ui.theme.GlassLight
import com.thegreatnovel.jingyouhealth.ui.theme.LocalJingYouDarkTheme
import com.thegreatnovel.jingyouhealth.ui.theme.Mist
import com.thegreatnovel.jingyouhealth.ui.theme.NightBlue
import com.thegreatnovel.jingyouhealth.ui.theme.Rose
import com.thegreatnovel.jingyouhealth.ui.theme.Void

val HeroShape = RoundedCornerShape(32.dp)
val CardShape = RoundedCornerShape(26.dp)
val CompactShape = RoundedCornerShape(20.dp)

private fun tabColors(tab: RootTab): Triple<Color, Color, Color> = when (tab) {
    RootTab.TODAY -> Triple(ArcticBlue, ElectricCyan, AuroraViolet)
    RootTab.SLEEP -> Triple(AuroraViolet, DeepViolet, Color(0xFFB8A0D8))
    RootTab.COACH -> Triple(Color(0xFFA58AD4), Color(0xFF7E92D8), ElectricCyan)
    RootTab.ACTIVITIES -> Triple(ElectricCyan, Color(0xFF3A9CFF), Amber)
    RootTab.BODY -> Triple(ElectricCyan, ArcticBlue, Color(0xFF78B8B3))
    // Trends remains available as a legacy route and follows the Body field.
    RootTab.TRENDS -> Triple(ElectricCyan, ArcticBlue, Color(0xFF78B8B3))
}

@Composable
fun DynamicAmbientBackdrop(
    tab: RootTab,
    modifier: Modifier = Modifier,
    energy: Float = 0.7f,
    stress: Float? = null,
    sleepScore: Float? = null,
    photoEnabled: Boolean = true,
    photoReveal: Float = 0f,
) {
    val dark = LocalJingYouDarkTheme.current
    val reveal = photoReveal.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val recovery = energy.coerceIn(0f, 1f)
    val stressLevel = ((stress ?: 26f) / 100f).coerceIn(0f, 1f)
    val sleep = ((sleepScore ?: (recovery * 100f)) / 100f).coerceIn(0f, 1f)
    val (tabPrimary, tabSecondary, tabTertiary) = tabColors(tab)

    // The field reflects the body, not just the active tab. High recovery opens into
    // sea-glass tones; stress introduces a restrained warm dusk; sleep pulls toward violet.
    val c1Target = lerp(tabPrimary, lerp(AuroraViolet, ElectricCyan, recovery), 0.58f)
    val c2Target = lerp(tabSecondary, lerp(DeepViolet, ArcticBlue, sleep), 0.52f)
    val c3Target = lerp(tabTertiary, lerp(ArcticBlue, Rose, stressLevel), 0.46f)
    val c1 by animateColorAsState(c1Target, tween(720), label = "ambient-c1")
    val c2 by animateColorAsState(c2Target, tween(720), label = "ambient-c2")
    val c3 by animateColorAsState(c3Target, tween(720), label = "ambient-c3")

    val infinite = rememberInfiniteTransition(label = "ambient")
    val driftX by infinite.animateFloat(
        initialValue = -0.055f,
        targetValue = 0.075f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift-x",
    )
    val driftY by infinite.animateFloat(
        initialValue = -0.035f,
        targetValue = 0.055f,
        animationSpec = infiniteRepeatable(tween(13_500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift-y",
    )
    val glow by infinite.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(8_200), RepeatMode.Reverse),
        label = "glow",
    )

    val backdropTop by animateColorAsState(if (dark) Void else Mist, tween(450), label = "backdrop-top")
    val backdropMiddle by animateColorAsState(if (dark) Color(0xFF162130) else Color(0xFFF1F0F4), tween(450), label = "backdrop-middle")
    val backdropBottom by animateColorAsState(if (dark) NightBlue else Color(0xFFEBF1EF), tween(450), label = "backdrop-bottom")
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(backdropTop, backdropMiddle, backdropBottom),
                ),
            )
            val radius = maxOf(size.width, size.height)
            val alpha = if (dark) {
                (0.19f + recovery * 0.11f) * glow
            } else {
                (0.18f + recovery * 0.10f) * glow
            }

            val p1 = Offset(size.width * (0.88f + driftX), size.height * (0.08f + driftY))
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(c1.copy(alpha = alpha), c1.copy(alpha = alpha * 0.22f), Color.Transparent),
                    center = p1,
                    radius = radius * 0.56f,
                ),
                radius = radius * 0.56f,
                center = p1,
            )

            val p2 = Offset(size.width * (0.02f - driftX), size.height * (0.52f - driftY))
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(c2.copy(alpha = alpha * 0.88f), c2.copy(alpha = alpha * 0.10f), Color.Transparent),
                    center = p2,
                    radius = radius * 0.62f,
                ),
                radius = radius * 0.62f,
                center = p2,
            )

            val warmAlpha = (0.055f + stressLevel * 0.10f) * glow
            val p3 = Offset(size.width * 0.74f, size.height * (0.88f + driftY))
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(c3.copy(alpha = warmAlpha), c3.copy(alpha = warmAlpha * 0.12f), Color.Transparent),
                    center = p3,
                    radius = radius * 0.48f,
                ),
                radius = radius * 0.48f,
                center = p3,
            )
        }
        TravelAtmosphere(
            modifier = Modifier.fillMaxWidth().height((420f + 140f * reveal).dp).align(Alignment.TopCenter),
            enabled = photoEnabled,
            reveal = reveal,
            strength = when (tab) {
                RootTab.TODAY -> 1f
                RootTab.SLEEP -> 0.72f
                RootTab.COACH -> 0.40f
                RootTab.ACTIVITIES -> 0.56f
                RootTab.BODY, RootTab.TRENDS -> 0.68f
            },
        )
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = CardShape,
    padding: PaddingValues = PaddingValues(18.dp),
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    val dark = LocalJingYouDarkTheme.current
    val top by animateColorAsState(if (dark) GlassDark.copy(alpha = 0.56f) else GlassLight.copy(alpha = 0.64f), tween(450), label = "glass-top")
    val bottom by animateColorAsState(if (dark) NightBlue.copy(alpha = 0.34f) else Ceramic.copy(alpha = 0.40f), tween(450), label = "glass-bottom")
    val borderStart by animateColorAsState(if (dark) GlassBorderDark.copy(alpha = 0.13f) else GlassBorderLight.copy(alpha = 0.36f), tween(450), label = "glass-edge")
    val borderEnd = if (dark) Color.White.copy(alpha = 0.025f) else Graphite.copy(alpha = 0.038f)
    val accentOverlay = accent?.copy(alpha = if (dark) 0.105f else 0.065f) ?: Color.Transparent

    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .background(Brush.linearGradient(listOf(accentOverlay, Color.Transparent, Color.Transparent)))
            .border(1.dp, Brush.linearGradient(listOf(borderStart, borderEnd, borderStart.copy(alpha = 0.12f))), shape)
            .padding(padding),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

@Composable
fun PressableGlassPanel(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = CardShape,
    padding: PaddingValues = PaddingValues(18.dp),
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.975f else 1f,
        spring(dampingRatio = 0.74f, stiffness = 650f),
        label = "press-scale",
    )
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            shape = shape,
            padding = padding,
            accent = accent,
            content = content,
        )
    }
}

@Composable
fun MetricRing(
    value: Float,
    label: String,
    display: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 178.dp,
) {
    val animated by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = tween(850, easing = FastOutSlowInEasing),
        label = "metric-ring",
    )
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.075f)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 13.dp.toPx()
            val pad = stroke / 2 + 4.dp.toPx()
            val arcSize = Size(this.size.width - pad * 2, this.size.height - pad * 2)
            drawArc(
                color = trackColor,
                startAngle = 136f,
                sweepAngle = 268f,
                useCenter = false,
                topLeft = Offset(pad, pad),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(accent.copy(alpha = 0.45f), accent, ElectricCyan, accent)),
                startAngle = 136f,
                sweepAngle = 268f * animated,
                useCenter = false,
                topLeft = Offset(pad, pad),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            val angle = Math.toRadians((136f + 268f * animated).toDouble())
            val r = arcSize.width / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val dot = Offset(
                center.x + kotlin.math.cos(angle).toFloat() * r,
                center.y + kotlin.math.sin(angle).toFloat() * r,
            )
            drawCircle(accent.copy(alpha = 0.20f), radius = 13.dp.toPx(), center = dot)
            drawCircle(Color.White, radius = 3.6.dp.toPx(), center = dot)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(display, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Sparkline(
    values: List<Float?>,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    domain: ClosedFloatingPointRange<Float>? = null,
) {
    val valid = values.mapIndexedNotNull { index, value -> value?.takeIf { it.isFinite() }?.let { index to it } }
    Canvas(modifier = modifier) {
        if (valid.isEmpty()) return@Canvas
        val min = minOf(valid.minOf { it.second }, domain?.start ?: Float.POSITIVE_INFINITY)
        val max = maxOf(valid.maxOf { it.second }, domain?.endInclusive ?: Float.NEGATIVE_INFINITY)
        val spread = max - min
        val path = Path()
        val inset = 2.dp.toPx()
        val width = (size.width - inset * 2).coerceAtLeast(0f)
        valid.forEachIndexed { i, (index, value) ->
            val x = if (values.size <= 1) size.width / 2f else inset + width * index / (values.size - 1f)
            // A constant series sits centrally, rather than looking like a low score.
            val normalized = if (spread > 0.001f) (value - min) / spread else 0.5f
            val y = size.height * (0.86f - normalized * 0.72f)
            val hasPrevious = i > 0 && valid[i - 1].first == index - 1
            val hasNext = i < valid.lastIndex && valid[i + 1].first == index + 1
            if (hasPrevious) path.lineTo(x, y) else path.moveTo(x, y)
            if (!hasPrevious && !hasNext) drawCircle(accent, 2.dp.toPx(), Offset(x, y))
        }
        drawPath(path, brush = Brush.horizontalGradient(listOf(accent.copy(alpha = 0.55f), accent, ElectricCyan)), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun FloatingHealthDock(
    selected: RootTab,
    onSelect: (RootTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalJingYouDarkTheme.current
    val items = listOf(
        Triple(RootTab.TODAY, Icons.Rounded.Home, tr("主页")),
        Triple(RootTab.SLEEP, Icons.Rounded.NightsStay, tr("睡眠")),
        Triple(RootTab.COACH, Icons.Rounded.Forum, tr("教练")),
        Triple(RootTab.ACTIVITIES, Icons.Rounded.DirectionsRun, tr("活动")),
        Triple(RootTab.BODY, Icons.Rounded.Favorite, tr("身体")),
    )
    val shell = if (dark) {
        Brush.horizontalGradient(
            listOf(
                NightBlue.copy(alpha = 0.94f),
                Color(0xFF272637).copy(alpha = 0.96f),
                NightBlue.copy(alpha = 0.94f),
            ),
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                Color(0xFFF3F4F8).copy(alpha = 0.94f),
                Color(0xFFEDEAF6).copy(alpha = 0.95f),
                Color(0xFFF3F4F8).copy(alpha = 0.94f),
            ),
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth(0.94f)
            .heightIn(min = 64.dp, max = 72.dp)
            .graphicsLayer {
                shadowElevation = 6.dp.toPx()
                shape = RoundedCornerShape(27.dp)
                clip = false
            }
            .clip(RoundedCornerShape(27.dp))
            .background(shell)
            .border(
                1.dp,
                if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.45f),
                RoundedCornerShape(27.dp),
            )
            .padding(horizontal = 3.dp, vertical = 2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { (tab, icon, label) ->
                DockItem(
                    modifier = Modifier.weight(1f),
                    selected = tab == selected,
                    icon = icon,
                    label = label,
                    accent = tabColors(tab).first,
                    onClick = { onSelect(tab) },
                )
            }
        }
    }
}

@Composable
private fun DockItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val iconColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(220),
        label = "dock-icon",
    )
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.96f
            selected -> 1.08f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f),
        label = "dock-scale",
    )
    val lift by animateFloatAsState(
        targetValue = if (selected) -0.7f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 560f),
        label = "dock-lift",
    )
    val halo by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "dock-halo",
    )
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 10.5.sp, lineHeight = 12.sp)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp, max = 68.dp)
            .clip(RoundedCornerShape(24.dp))
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null,
                onClick = {
                    if (!selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
        )
            .semantics { contentDescription = label }
            .padding(horizontal = 2.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp, max = 66.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = lift.dp.toPx()
                }
                .clip(RoundedCornerShape(19.dp))
                .background(
                    if (selected) {
                        Brush.horizontalGradient(
                            listOf(
                                accent.copy(alpha = 0.10f + 0.08f * halo),
                                ArcticBlue.copy(alpha = 0.08f + 0.07f * halo),
                                Color.Transparent,
                            ),
                        )
                    } else {
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(27.dp))
                Text(label, style = labelStyle, color = iconColor, maxLines = 1)
            }
        }
    }
}

@Composable
fun TypingBubble(
    status: String,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "typing")
    val phases = listOf(0, 150, 300)
    GlassPanel(modifier = modifier, shape = RoundedCornerShape(24.dp), padding = PaddingValues(horizontal = 16.dp, vertical = 13.dp), accent = AuroraViolet) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                phases.forEach { delay ->
                    val alpha by infinite.animateFloat(
                        initialValue = 0.30f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(720, delayMillis = delay),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "dot-$delay",
                    )
                    Box(Modifier.size(7.dp).clip(CircleShape).background(AuroraViolet.copy(alpha = alpha)))
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    val foreground = if (LocalJingYouDarkTheme.current) color else lerp(color, Graphite, 0.42f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = foreground, fontSize = 11.sp)
    }
}

fun semanticAccent(score: Double?): Color = when {
    score == null -> ArcticBlue
    score >= 80 -> ElectricCyan
    score >= 60 -> ArcticBlue
    score >= 40 -> Amber
    else -> Rose
}
