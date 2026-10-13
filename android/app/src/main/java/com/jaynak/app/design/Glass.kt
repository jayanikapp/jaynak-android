package com.jaynak.app.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * مقابل Glass.swift. Liquid Glass غير موجود على أندرويد، لذا يُقرَّب بسطح شبه شفاف
 * + طبقة تلوين + حافة ضوء + ظل بنفس معادلة iOS (shadow × (داكن ١٫٥ : فاتح ٠٫٥٥)).
 */
fun Modifier.glassSurface(
    radius: Dp = Metrics.cardRadius,
    tint: Color? = null,
    shadow: Float = 0.18f,
    underlay: Color? = null,
): Modifier = glassShape(RoundedCornerShape(radius), radius, tint, shadow, underlay)

fun Modifier.glassChip(tint: Color? = null): Modifier =
    glassShape(CircleShape, 18.dp, tint, 0.10f)

fun Modifier.glassShape(shape: Shape, shadowScale: Dp, tint: Color?, shadow: Float, underlay: Color? = null): Modifier = composed {
    val dark = MB.dark
    val fill = if (dark) Color.White.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.66f)
    val rim = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f)
    val a = shadow * (if (dark) 1.5f else 0.55f)
    val c = Color.Black.copy(alpha = a.coerceIn(0f, 1f))
    this
        .shadow(
            elevation = shadowScale * 0.5f,
            shape = shape,
            ambientColor = c,
            spotColor = c,
        )
        .clip(shape)
        .let { if (underlay != null) it.background(underlay) else it }
        .background(if (dark) MB.raised.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.30f))
        .background(fill)
        .let { if (tint != null) it.background(tint.copy(alpha = tint.alpha * 0.55f)) else it }
        .border(0.8.dp, rim, shape)
}
