package com.thegreatnovel.jingyouhealth.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState

/** Material handles gesture consumption; this state supplies the release spring. */
@OptIn(ExperimentalMaterial3Api::class)
class ElasticPullState : PullToRefreshState {
    private val distance = Animatable(0f)
    override val distanceFraction: Float get() = distance.value
    override val isAnimating: Boolean get() = distance.isRunning
    override suspend fun snapTo(targetValue: Float) { distance.snapTo(targetValue) }
    override suspend fun animateToThreshold() { distance.animateTo(1f, spring(dampingRatio = 0.74f, stiffness = 340f)) }
    override suspend fun animateToHidden() { distance.animateTo(0f, spring(dampingRatio = 0.64f, stiffness = 310f)) }
}

fun elasticContentOffset(fraction: Float): Float {
    val positive = fraction.coerceAtLeast(0f)
    return if (fraction < 0f) fraction * 55f else 310f * positive / (1f + positive)
}
