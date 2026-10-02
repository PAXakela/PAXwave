package com.music.bitchord.ui.pax

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.ui.theme.PaxAqua
import com.music.bitchord.ui.theme.InterFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** The curve Apple and Material both use for things settling into place. */
private val Settle = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

/**
 * PAXwave's opening. Quiet and deliberate rather than busy:
 *
 * 1. the logo comes into focus — from a soft blur and a touch smaller —
 *    over a faint aqua glow, exactly where the system splash left it;
 * 2. a single sound wave draws itself beneath, left to right, and settles
 *    as its amplitude dies away;
 * 3. the name appears letter by letter, widely spaced;
 * 4. everything lifts very slightly and fades into the app.
 *
 * Held at least [MIN_MS] so it reads as a moment rather than a flicker, and
 * at most [MAX_MS] however long Home takes.
 */
@Composable
fun PaxIntro(ready: Boolean, onFinished: () -> Unit) {
    val focus = remember { Animatable(0f) }
    val wave = remember { Animatable(0f) }
    val name = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val isReady by rememberUpdatedState(ready)
    val finished by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        launch { focus.animateTo(1f, tween(900, easing = Settle)) }
        launch {
            delay(280)
            wave.animateTo(1f, tween(1100, easing = Settle))
        }
        launch {
            delay(560)
            name.animateTo(1f, tween(800, easing = LinearEasing))
        }
        delay(MIN_MS)
        while (!isReady && System.currentTimeMillis() - start < MAX_MS) delay(40)
        exit.animateTo(1f, tween(480, easing = Settle))
        finished()
    }

    val f = focus.value
    val e = exit.value
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - e }
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        // The glow: one soft breath of aqua behind the mark.
        Canvas(Modifier.size(360.dp).graphicsLayer { alpha = f * (1f - e) }) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(PaxAqua.copy(alpha = 0.20f), PaxAqua.copy(alpha = 0.05f), Color.Transparent),
                    center = center,
                    radius = size.minDimension / 2f,
                ),
                radius = size.minDimension / 2f,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                val lift = 1f + 0.05f * e
                scaleX = lift
                scaleY = lift
                translationY = -18.dp.toPx() * e
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(width = 94.dp, height = 108.dp)
                    .graphicsLayer {
                        alpha = f
                        val s = 0.9f + 0.1f * f
                        scaleX = s
                        scaleY = s
                    }
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(((1f - f) * 14f).dp)
                        else Modifier,
                    ),
            )
            Spacer(Modifier.height(22.dp))
            SoundWave(progress = wave.value, modifier = Modifier.size(width = 176.dp, height = 26.dp))
            Spacer(Modifier.height(20.dp))
            Wordmark("PAXwave", name.value)
        }
    }
}

/** A sine line drawn left to right, its swing fading as it goes and as time passes. */
@Composable
private fun SoundWave(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (progress <= 0f) return@Canvas
        val mid = size.height / 2f
        val steps = 120
        val path = Path()
        val drawn = (steps * progress).toInt().coerceAtLeast(1)
        for (i in 0..drawn) {
            val t = i / steps.toFloat()
            // Swells in the middle, rests at the ends; settles as progress completes.
            val envelope = sin(PI * t).toFloat() * (1f - 0.55f * progress)
            val y = mid + sin(t * 4.5f * 2f * PI.toFloat()) * envelope * size.height * 0.45f
            val x = t * size.width
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            brush = Brush.horizontalGradient(
                listOf(Color.White.copy(alpha = 0f), Color.White, PaxAqua, PaxAqua.copy(alpha = 0f)),
                startX = 0f,
                endX = size.width,
            ),
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
        )
        // The pen: a small bright point at the head of the line while it draws.
        if (progress < 1f) {
            val t = drawn / steps.toFloat()
            val envelope = sin(PI * t).toFloat() * (1f - 0.55f * progress)
            val y = mid + sin(t * 4.5f * 2f * PI.toFloat()) * envelope * size.height * 0.45f
            drawCircle(PaxAqua, radius = 3.dp.toPx(), center = Offset(t * size.width, y))
        }
    }
}

/** The name, one letter at a time, widely tracked. */
@Composable
private fun Wordmark(text: String, progress: Float) {
    Row {
        text.forEachIndexed { index, char ->
            val local = ((progress * (text.length + 3)) - index).coerceIn(0f, 1f) / 1f
            Text(
                char.toString(),
                style = TextStyle(
                    fontFamily = InterFont,
                    // As the logo writes it: PAX heavy, wave light.
                    fontWeight = if (index < 3) FontWeight.W800 else FontWeight.W400,
                    fontSize = 26.sp,
                    letterSpacing = 0.sp,
                    color = Color.White,
                ),
                modifier = Modifier.graphicsLayer {
                    alpha = local.coerceIn(0f, 1f)
                    translationY = (1f - local.coerceIn(0f, 1f)) * 10.dp.toPx()
                },
            )
            if (index < text.lastIndex) Spacer(Modifier.width(5.dp))
        }
    }
}

private const val MIN_MS = 1_700L
private const val MAX_MS = 3_200L
