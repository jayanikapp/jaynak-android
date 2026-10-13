package com.jaynak.app.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** مقابل Animation.mbMove … mbEnter (spring(duration:bounce:)) تقريباً بنفس الإحساس. */
object MBAnim {
    // bounce 0 = تخميد حرج، bounce>0 = تخميد أقل.
    private fun <T> sp(bounce: Float, stiffness: Float): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f - bounce, stiffness = stiffness)

    fun <T> move(): FiniteAnimationSpec<T> = sp(0f, 220f)
    fun <T> snap(): FiniteAnimationSpec<T> = sp(0f, 520f)
    fun <T> sheet(): FiniteAnimationSpec<T> = sp(0.20f, 420f)
    fun <T> flick(): FiniteAnimationSpec<T> = sp(0.22f, 220f)
    fun <T> fade(): FiniteAnimationSpec<T> = tween(220, easing = LinearOutSlowInEasing)
    fun <T> enter(): FiniteAnimationSpec<T> = sp(0.18f, 150f)
}

/** مقابل appearIn(index): يظهر بتأخير متدرّج مع إزاحة رأسية وتكبير طفيف. */
fun Modifier.appearIn(index: Int, distance: Dp = 18.dp): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val delay = min(index, 9) * 55L
        kotlinx.coroutines.delay(delay)
        p.animateTo(1f, MBAnim.enter())
    }
    val dist = with(androidx.compose.ui.platform.LocalDensity.current) { distance.toPx() }
    Modifier.graphicsLayer {
        val t = p.value
        alpha = t.coerceIn(0f, 1f)
        translationY = (1f - t) * dist
        val sc = 0.985f + 0.015f * t
        scaleX = sc; scaleY = sc
        transformOrigin = TransformOrigin(0.5f, 0f)
    }
}

/** مقابل Brand.sheen + SheenSweep: لمعة تعبر الزر مرة عند الظهور. */
fun Modifier.sheen(active: Boolean = true, delayMs: Int = 500): Modifier = composed {
    val x = remember { Animatable(-1.4f) }
    LaunchedEffect(active) {
        if (active) {
            kotlinx.coroutines.delay(delayMs.toLong())
            x.animateTo(1.4f, tween(1100, easing = FastOutSlowInEasing))
        }
    }
    Modifier.drawWithContent {
        drawContent()
        if (active) {
            val w = size.width * 0.55f
            val left = x.value * size.width
            drawRect(
                brush = Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0f),
                    0.45f to Color.White.copy(alpha = 0.28f),
                    1f to Color.White.copy(alpha = 0f),
                    start = Offset(left, 0f), end = Offset(left + w, size.height),
                ),
                topLeft = Offset(left, 0f),
                size = androidx.compose.ui.geometry.Size(w, size.height),
            )
        }
    }
}

/** مقابل SuccessBurst: ثلاث حلقات تتمدّد + دائرة خضراء وعلامة. */
@Composable
fun SuccessBurst(tint: Color = MB.green, symbol: String = "checkmark") {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(Unit) { phase.animateTo(1f, tween(1000, easing = LinearOutSlowInEasing)) }
    val grad = MB.greenGradient
    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(140.dp)) {
            val p = phase.value
            for (i in 0 until 3) {
                val sc = 0.4f + p * (1.2f + i * 0.35f)
                drawCircle(
                    color = tint.copy(alpha = 0.5f * (1 - p)),
                    radius = size.minDimension / 2 * sc,
                    style = Stroke(2.5.dp.toPx()),
                )
            }
        }
        Box(
            Modifier.size(68.dp)
                .shadow(18.dp, CircleShape, ambientColor = tint.copy(alpha = 0.55f), spotColor = tint.copy(alpha = 0.55f))
                .background(grad, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                sfVector(symbol), null, tint = Color.White,
                modifier = Modifier.size(30.dp).scale(0.6f + min(1f, phase.value * 2.2f) * 0.4f),
            )
        }
    }
}

/** مقابل BrandLoader: ثلاث نقاط (أخضر/سماوي/أحمر) تنبض. */
@Composable
fun BrandLoader(size: Dp = 34.dp) {
    val t by rememberInfiniteTransition(label = "loader").animateFloat(
        0f, 1f,
        InfiniteRepeatableSpec(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "t",
    )
    val colors = listOf(MB.green, MB.teal, MB.red)
    Row(
        Modifier.height(size * 0.4f),
        horizontalArrangement = Arrangement.spacedBy(size * 0.18f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until 3) {
            val sc = 0.7f + 0.4f * abs(sin((t + i * 0.33f) * PI.toFloat()))
            Box(
                Modifier.size(size * 0.26f)
                    .graphicsLayer { scaleX = sc; scaleY = sc; alpha = 0.45f + 0.55f * sc }
                    .background(colors[i], CircleShape)
            )
        }
    }
}

fun projectMomentum(velocity: Double, decelerationRate: Double = 0.998): Double =
    (velocity / 1000) * decelerationRate / (1 - decelerationRate)

fun rubberband(overshoot: Double, dimension: Double, constant: Double = 0.55): Double =
    (overshoot * dimension * constant) / (dimension + constant * abs(overshoot))
