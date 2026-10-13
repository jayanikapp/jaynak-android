package com.jaynak.app.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** علامة جاينك — حرف J مع سهم السرعة وثلاثة خطوط (مقابل BrandMark.swift). */
@Composable
fun BrandMark(size: Dp = 120.dp, streakPhase: Float = 1f, modifier: Modifier = Modifier) {
    val green = MB.green
    val teal = MB.teal
    val ink = Color(0.0f, 0.184f, 0.173f)
    val shape = RoundedCornerShape(size * 0.225f)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier
                .size(size)
                .shadow(size * 0.10f, shape, ambientColor = green.copy(alpha = 0.35f),
                    spotColor = green.copy(alpha = 0.35f))
                .background(
                    Brush.linearGradient(
                        listOf(green, Color(0.588f, 0.961f, 0.902f), teal),
                        start = Offset.Zero,
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                    ),
                    shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(size * 0.66f)) {
                drawPath(jGlyphPath(this.size.width, streakPhase), ink)
            }
        }
    }
}

/** الحرف J مع السهم والخطوط، مرسوماً في مربّع ١٠٠×١٠٠ ثم مُكبَّراً. */
fun jGlyphPath(side: Float, phase: Float = 1f): Path {
    val s = side / 100f
    fun x(v: Float) = v * s
    val path = Path()

    // السهم المائل أعلى الحرف
    path.moveTo(x(42.6f), x(20.2f))
    path.lineTo(x(83f), x(7.9f))
    path.lineTo(x(77.5f), x(31.3f))
    path.lineTo(x(42.5f), x(32.2f))
    path.close()

    // ثلاثة خطوط السرعة — تنكمش من اليسار حسب الطور
    val bars = listOf(Triple(24f, 38f, 31f), Triple(16f, 48f, 33f), Triple(24f, 58f, 20f))
    for ((bx, by, bw) in bars) {
        val w = bw * max(0.12f, phase)
        path.addRoundRect(
            RoundRect(
                Rect(Offset(x(bx + bw - w), x(by)), Size(x(w), x(5.6f))),
                CornerRadius(x(2.8f), x(2.8f)),
            )
        )
    }

    // جسم الحرف وذيله
    path.moveTo(x(66.5f), x(31.5f))
    path.lineTo(x(78.3f), x(31.5f))
    path.lineTo(x(70.0f), x(72.4f))
    path.cubicTo(x(68.0f), x(82.6f), x(59.6f), x(89.7f), x(48.6f), x(89.7f))
    path.cubicTo(x(39.0f), x(89.7f), x(31.0f), x(84.7f), x(27.6f), x(76.3f))
    path.lineTo(x(38.2f), x(72.2f))
    path.cubicTo(x(40.1f), x(76.7f), x(43.9f), x(79.2f), x(48.8f), x(79.2f))
    path.cubicTo(x(54.2f), x(79.2f), x(58.1f), x(75.9f), x(59.2f), x(70.2f))
    path.close()
    return path
}

/** اسم العلامة مكتوباً. */
@Composable
fun Wordmark(size: Int = 26) {
    Text(
        "جاينك",
        style = MBType.ar(size, FontWeight.Bold).copy(
            brush = MB.greenGradient,
        ),
    )
}
