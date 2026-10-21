package com.jaynak.app.features.splash

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.ImpactStyle
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.BrandMark
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.Wordmark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

/** مقابل SplashView.swift. */
@Composable
fun SplashView(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val reduceMotion = remember {
        runCatching {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }

    val markSize = remember { Animatable(132f) }
    val streaks = remember { Animatable(0f) }
    val showWord = remember { Animatable(0f) }
    val showTag = remember { Animatable(0f) }
    val loaded = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            streaks.snapTo(1f); showWord.snapTo(1f); showTag.snapTo(1f)
            launch { loaded.animateTo(1f, tween(500, easing = LinearOutSlowInEasing)) }
            delay(1000)
            onFinish()
            return@LaunchedEffect
        }
        launch { markSize.animateTo(152f, spring(dampingRatio = 0.72f, stiffness = 140f)) }
        launch { delay(100); streaks.animateTo(1f, tween(550, easing = LinearOutSlowInEasing)) }
        launch { delay(280); showWord.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 170f)) }
        launch { delay(480); showTag.animateTo(1f, tween(400, easing = LinearOutSlowInEasing)) }
        launch { delay(300); loaded.animateTo(1f, tween(1150, easing = FastOutSlowInEasing)) }
        delay(1850)
        Feedback.impact(ImpactStyle.light)
        onFinish()
    }

    Box(Modifier.fillMaxSize()) {
        BrandBackground(intensity = 1.5f)

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))

            Box(
                Modifier.height(150.dp)
                    .rotate(-6f * (1f - showWord.value.coerceIn(0f, 1f)))
                    .shadow(26.dp, CircleShape, ambientColor = MB.green.copy(alpha = 0.35f),
                        spotColor = MB.green.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) { BrandMark(size = markSize.value.dp, streakPhase = streaks.value) }

            Column(Modifier.padding(top = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val sw = showWord.value.coerceIn(0f, 1f)
                Box(
                    Modifier
                        .graphicsLayer { alpha = sw; translationY = (1f - sw) * 12.dp.toPx() }
                        .let { if (sw < 1f && android.os.Build.VERSION.SDK_INT >= 31) it.blur((6 * (1 - sw)).dp) else it }
                ) { Wordmark(30) }
            }

            Spacer(Modifier.weight(1f))

            Column(
                Modifier.padding(bottom = 54.dp).navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.width(200.dp).height(5.dp)) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(MB.ink.copy(alpha = 0.10f)))
                    // ZStack(alignment: .trailing) — في RTL تقع الحافة اليسرى
                    Box(
                        Modifier.align(Alignment.CenterEnd)
                            .width(max(6f, 200f * loaded.value).dp).height(5.dp)
                            .shadow(6.dp, CircleShape, ambientColor = MB.green.copy(alpha = 0.7f),
                                spotColor = MB.green.copy(alpha = 0.7f))
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(MB.green, MB.teal, MB.red)))
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "جاري التحضير…", style = MBType.micro, color = MB.ink3,
                    modifier = Modifier.alpha(showTag.value.coerceIn(0f, 1f)),
                )
            }
        }
    }
}
