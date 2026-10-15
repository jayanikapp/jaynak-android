package com.jaynak.app.features.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.Coord
import com.jaynak.app.core.LiveTracking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** علامة سيارة على الخريطة (للإدارة: عدّة سيارات). */
data class CarMarker(
    val id: String,
    val coord: Coord,
    val heading: Float? = null,
    val tint: Color = Color(0xFFE5303B),
    val label: String? = null,
    val selected: Boolean = false,
    val stale: Boolean = false,
    val icon: Int = 0,
)

/** دبوس عام (نقطة استلام/تسليم) للخريطة الحيّة. */
data class PinMarker(val coord: Coord, val symbol: String, val tint: Color, val label: String)

/** إحداثيات تتحرّك بنعومة نحو الهدف + اتجاه محسوب من الحركة إن لم يُعطَ اتجاه. */
@Composable
fun rememberSmoothCar(target: Coord, givenHeading: Float?): Triple<Coord, Float, Boolean> {
    val lat = remember { Animatable(target.lat.toFloat()) }
    val lon = remember { Animatable(target.lon.toFloat()) }
    var prev by remember { mutableStateOf(target) }
    var heading by remember { mutableStateOf(givenHeading ?: 0f) }
    var first by remember { mutableStateOf(true) }

    LaunchedEffect(target.lat, target.lon) {
        if (first) { first = false; lat.snapTo(target.lat.toFloat()); lon.snapTo(target.lon.toFloat()); prev = target; return@LaunchedEffect }
        val moved = prev.km(target) * 1000
        if (moved > 4) {
            heading = if (givenHeading != null && givenHeading != 0f) givenHeading else LiveTracking.bearing(prev, target)
            prev = target
        }
        // قفزة مباشرة إن كانت المسافة كبيرة جداً، وإلا حركة خطية ناعمة
        if (moved > 1500) { lat.snapTo(target.lat.toFloat()); lon.snapTo(target.lon.toFloat()) }
        else coroutineScope {
            launch { lat.animateTo(target.lat.toFloat(), tween(3500, easing = LinearEasing)) }
            launch { lon.animateTo(target.lon.toFloat(), tween(3500, easing = LinearEasing)) }
        }
    }
    // الدوران يمرّ بأقصر طريق
    val rot = remember { Animatable(heading) }
    LaunchedEffect(heading) {
        var d = (heading - rot.value) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        rot.animateTo(rot.value + d, tween(700, easing = FastOutSlowInEasing))
    }
    return Triple(Coord(lat.value.toDouble(), lon.value.toDouble()), rot.value, true)
}

/** سيارة بمنظر علوي، مقدمتها لأعلى، تدور حسب الاتجاه. */
@Composable
fun LiveCar(
    heading: Float,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    tint: Color = Color(0xFFE5303B),
    pulse: Boolean = true,
    dim: Boolean = false,
    selected: Boolean = false,
) {
    val t by rememberInfiniteTransition(label = "car").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "p")
    Box(modifier.size(size * 1.9f).alpha(if (dim) 0.55f else 1f), contentAlignment = Alignment.Center) {
        if (pulse) {
            val d = size * (0.9f + 0.9f * t)
            Box(Modifier.size(d).alpha(0.8f * (1 - t)).background(tint.copy(alpha = 0.22f), CircleShape))
        }
        if (selected) Box(Modifier.size(size * 1.25f).background(Color.White.copy(alpha = 0.35f), CircleShape))
        Canvas(Modifier.size(size).rotate(heading)) { drawCar(tint) }
    }
}

private fun DrawScope.drawCar(body: Color) {
    val w = size.width
    val h = size.height
    val cw = w * 0.50f          // عرض الجسم
    val ch = h * 0.92f          // طول الجسم
    val x0 = (w - cw) / 2
    val y0 = (h - ch) / 2

    // ظلّ
    drawRoundRect(Color.Black.copy(alpha = 0.28f), Offset(x0 - 1f, y0 + h * 0.04f), Size(cw + 2f, ch),
        CornerRadius(cw * 0.42f))

    // مرايا جانبية
    val mirror = Color(0xFF1B1B1F)
    drawRoundRect(mirror, Offset(x0 - cw * 0.14f, y0 + ch * 0.27f), Size(cw * 0.18f, ch * 0.065f), CornerRadius(4f))
    drawRoundRect(mirror, Offset(x0 + cw * 0.96f, y0 + ch * 0.27f), Size(cw * 0.18f, ch * 0.065f), CornerRadius(4f))

    // العجلات
    val tire = Color(0xFF111114)
    for (fy in listOf(0.16f, 0.70f)) {
        drawRoundRect(tire, Offset(x0 - cw * 0.07f, y0 + ch * fy), Size(cw * 0.10f, ch * 0.16f), CornerRadius(3f))
        drawRoundRect(tire, Offset(x0 + cw * 0.97f, y0 + ch * fy), Size(cw * 0.10f, ch * 0.16f), CornerRadius(3f))
    }

    // الجسم
    drawRoundRect(
        Brush.horizontalGradient(listOf(body.copy(alpha = 0.78f), body, body.copy(alpha = 0.78f)), x0, x0 + cw),
        Offset(x0, y0), Size(cw, ch), CornerRadius(cw * 0.40f))
    drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(x0, y0), Size(cw, ch), CornerRadius(cw * 0.40f),
        style = Stroke(width = w * 0.035f))

    // غطاء المحرك (لمعة)
    drawRoundRect(Color.White.copy(alpha = 0.16f), Offset(x0 + cw * 0.18f, y0 + ch * 0.05f),
        Size(cw * 0.64f, ch * 0.17f), CornerRadius(cw * 0.25f))

    // الزجاج الأمامي
    val glass = Color(0xFF14202B)
    val ws = Path().apply {
        moveTo(x0 + cw * 0.14f, y0 + ch * 0.37f)
        lineTo(x0 + cw * 0.22f, y0 + ch * 0.24f)
        lineTo(x0 + cw * 0.78f, y0 + ch * 0.24f)
        lineTo(x0 + cw * 0.86f, y0 + ch * 0.37f)
        close()
    }
    drawPath(ws, glass)
    // السقف
    drawRoundRect(body.copy(alpha = 0.95f), Offset(x0 + cw * 0.15f, y0 + ch * 0.37f), Size(cw * 0.70f, ch * 0.24f),
        CornerRadius(cw * 0.10f))
    drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(x0 + cw * 0.22f, y0 + ch * 0.40f), Size(cw * 0.56f, ch * 0.08f),
        CornerRadius(cw * 0.06f))
    // الزجاج الخلفي
    val rs = Path().apply {
        moveTo(x0 + cw * 0.15f, y0 + ch * 0.61f)
        lineTo(x0 + cw * 0.85f, y0 + ch * 0.61f)
        lineTo(x0 + cw * 0.76f, y0 + ch * 0.73f)
        lineTo(x0 + cw * 0.24f, y0 + ch * 0.73f)
        close()
    }
    drawPath(rs, glass)

    // المصابيح الأمامية والخلفية
    val head = Color(0xFFFFF4C2)
    drawRoundRect(head, Offset(x0 + cw * 0.10f, y0 + ch * 0.015f), Size(cw * 0.24f, ch * 0.05f), CornerRadius(3f))
    drawRoundRect(head, Offset(x0 + cw * 0.66f, y0 + ch * 0.015f), Size(cw * 0.24f, ch * 0.05f), CornerRadius(3f))
    val tail = Color(0xFFFF1F2D)
    drawRoundRect(tail, Offset(x0 + cw * 0.10f, y0 + ch * 0.935f), Size(cw * 0.26f, ch * 0.04f), CornerRadius(3f))
    drawRoundRect(tail, Offset(x0 + cw * 0.64f, y0 + ch * 0.935f), Size(cw * 0.26f, ch * 0.04f), CornerRadius(3f))
}
