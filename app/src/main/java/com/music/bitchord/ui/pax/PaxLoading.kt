package com.music.bitchord.ui.pax

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.theme.PaxAqua
import kotlin.math.PI
import kotlin.math.sin

/** How long one bar takes to rise and fall. */
private const val WAVE_PERIOD_MS = 1100f

/**
 * PAXwave's loading mark: a few aqua bars rising and falling one after another,
 * like a sound wave passing through. Shown beside a page title while its
 * content is on the way.
 */
@Composable
fun PaxLoadingWave(
    modifier: Modifier = Modifier,
    height: Dp = 22.dp,
    color: Color = PaxAqua,
    bars: Int = 4,
) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { t -> time.floatValue = t.toFloat() }
    }
    val barWidth = 4.dp
    val gap = 3.dp
    Canvas(modifier.size(width = barWidth * bars + gap * (bars - 1), height = height)) {
        val w = barWidth.toPx()
        val g = gap.toPx()
        for (i in 0 until bars) {
            // Each bar lags the one before it by a fifth of a cycle.
            val phase = (time.floatValue / WAVE_PERIOD_MS - i * 0.2f) * 2 * PI
            val level = 0.28f + 0.72f * ((sin(phase) + 1) / 2).toFloat()
            val h = size.height * level
            drawRoundRect(
                color = color.copy(alpha = 0.55f + 0.45f * level),
                topLeft = Offset(i * (w + g), (size.height - h) / 2),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2, w / 2),
            )
        }
    }
}
