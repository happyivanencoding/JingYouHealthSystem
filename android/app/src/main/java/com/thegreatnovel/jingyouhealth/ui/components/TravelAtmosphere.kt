package com.thegreatnovel.jingyouhealth.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.thegreatnovel.jingyouhealth.ui.theme.LocalJingYouDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** A quiet fragment of a shared place. The original stays intact in private assets. */
@Composable
internal fun TravelAtmosphere(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    strength: Float = 1f,
    reveal: Float = 0f,
) {
    val assets = LocalContext.current.assets
    val dark = LocalJingYouDarkTheme.current
    // Decode away from the main thread, once per backdrop composition. The image is
    // optional, so builds without the private photograph retain the ambient field.
    val artwork by produceState<ImageBitmap?>(initialValue = null, key1 = assets, key2 = enabled) {
        // Retain the decoded image on disable so the fade can finish naturally.
        if (enabled && value == null) value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                assets.open("travel/azulejo.jpg").use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1_600) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                assets.open("travel/azulejo.jpg").use {
                    BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    val opacity by animateFloatAsState(
        targetValue = if (enabled && artwork != null) (if (dark) 0.24f else 0.29f) * strength else 0f,
        animationSpec = tween(700),
        label = "travel-atmosphere",
    )
    // The pull gesture owns its motion. Only the resting atmosphere is animated;
    // photo opacity and masks follow the finger without a trailing tween.
    val revealProgress = reveal.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val photoOpacity = if (enabled) opacity + (1f - opacity) * revealProgress else 0f

    Canvas(modifier.graphicsLayer {
        alpha = photoOpacity
        compositingStrategy = CompositingStrategy.Offscreen
    }) {
        val image = artwork ?: return@Canvas
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        // Only the upper tilework enters the composition. This is a draw-time
        // viewport, not a derivative image; the person in the lower half is absent.
        val tileHeight = image.height * 0.53f
        val scale = maxOf(size.width / image.width, size.height / tileHeight)
        val sourceWidth = (size.width / scale).roundToInt().coerceIn(1, image.width)
        val sourceHeight = (size.height / scale).roundToInt().coerceIn(1, tileHeight.toInt())
        drawImage(
            image = image,
            srcOffset = IntOffset(((image.width - sourceWidth) * 0.58f).roundToInt(), 0),
            srcSize = IntSize(sourceWidth, sourceHeight),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        )
        fun mask(restingAlpha: Float) = Color.Black.copy(alpha = restingAlpha + (1f - restingAlpha) * revealProgress)
        drawRect(
            brush = Brush.verticalGradient(
                0f to mask(0.50f),
                0.18f to Color.Black,
                0.50f to mask(0.74f),
                0.82f to mask(0.18f),
                0.85f to mask(0.15f),
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
        // At rest, leave a quiet reading margin. At full pull, the upper photo
        // is unfiltered; only the final 15% fades into the ambient background.
        drawRect(
            brush = Brush.linearGradient(
                0f to mask(0.28f),
                0.45f to mask(0.60f),
                0.88f to Color.Black,
                1f to Color.Black,
                start = Offset.Zero,
                end = Offset(size.width, size.height * 0.16f),
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}
