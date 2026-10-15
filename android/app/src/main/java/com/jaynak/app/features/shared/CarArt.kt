package com.jaynak.app.features.shared

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity

/** رسومات مركبات المندوب على الخريطة (٥ أشكال). مقدّمة المركبة للأعلى والمركز عند (0,0). */
object CarArt {
    val names = listOf("سيدان", "دفع رباعي", "بيك أب", "رياضية", "دراجة نارية")

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val path = Path()

    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, p: Paint) {
        rect.set(l, t, r, b); c.drawRoundRect(rect, rad, rad, p)
    }

    private fun darker(col: Int): Int {
        val r = ((col shr 16) and 0xFF) * 0.62f; val g = ((col shr 8) and 0xFF) * 0.62f; val b = (col and 0xFF) * 0.62f
        return (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    private fun lighter(col: Int): Int {
        val r = ((col shr 16) and 0xFF); val g = ((col shr 8) and 0xFF); val b = (col and 0xFF)
        fun l(v: Int) = (v + (255 - v) * 0.38f).toInt()
        return (0xFF shl 24) or (l(r) shl 16) or (l(g) shl 8) or l(b)
    }

    /** size = طول المركبة بالبكسل، px = كثافة الشاشة (لسماكة الخطوط). */
    fun draw(c: Canvas, icon: Int, size: Float, px: Float, body: Int, alpha: Int) {
        when (icon) {
            2 -> pickup(c, size, px, body, alpha)
            4 -> bike(c, size, px, body, alpha)
            else -> car(c, icon, size, px, body, alpha)
        }
    }

    private fun a(col: Int, alpha: Int) = (col and 0x00FFFFFF) or (alpha shl 24)

    // ───────── سيدان / دفع رباعي / رياضية ─────────
    private fun car(c: Canvas, icon: Int, size: Float, px: Float, body: Int, alpha: Int) {
        val hl = size * 0.50f
        val wf = when (icon) { 1 -> 0.56f; 3 -> 0.46f; else -> 0.50f }
        val hw = hl * wf
        val fg0 = when (icon) { 1 -> -0.44f; 3 -> -0.30f; else -> -0.46f }
        val rs = when (icon) { 1 -> -0.22f; 3 -> -0.08f; else -> -0.18f }
        val re = when (icon) { 1 -> 0.52f; 3 -> 0.20f; else -> 0.30f }
        val rg = when (icon) { 1 -> 0.68f; 3 -> 0.40f; else -> 0.50f }
        fun X(u: Float) = u * hw
        fun Y(v: Float) = v * hl
        fun al(col: Int) = a(col, alpha)
        val dark = darker(body); val light = lighter(body)

        fill.shader = null
        fill.color = (0x40 * alpha / 255) shl 24
        rr(c, X(-1.06f), Y(-0.92f) + 3f * px, X(1.06f), Y(1.02f) + 3f * px, hw * 0.9f, fill)

        fill.color = al(0xFF0E0E10.toInt())
        for (v in floatArrayOf(-0.62f, 0.40f)) {
            rr(c, X(-1.10f), Y(v), X(-0.88f), Y(v + 0.30f), 3f * px, fill)
            rr(c, X(0.88f), Y(v), X(1.10f), Y(v + 0.30f), 3f * px, fill)
        }
        fill.color = al(dark)
        path.reset(); path.moveTo(X(-0.95f), Y(-0.34f)); path.lineTo(X(-1.20f), Y(-0.30f)); path.lineTo(X(-1.17f), Y(-0.20f)); path.lineTo(X(-0.95f), Y(-0.22f)); path.close()
        c.drawPath(path, fill)
        path.reset(); path.moveTo(X(0.95f), Y(-0.34f)); path.lineTo(X(1.20f), Y(-0.30f)); path.lineTo(X(1.17f), Y(-0.20f)); path.lineTo(X(0.95f), Y(-0.22f)); path.close()
        c.drawPath(path, fill)

        val bodyPath = bodyPath(::X, ::Y)
        fill.shader = LinearGradient(X(-1f), 0f, X(1f), 0f,
            intArrayOf(al(dark), al(light), al(body), al(dark)), floatArrayOf(0f, 0.30f, 0.62f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(bodyPath, fill)
        fill.shader = null
        line.color = al(0xFFFFFFFF.toInt()); line.strokeWidth = 1.6f * px
        c.drawPath(bodyPath, line)

        fill.color = 0x2EFFFFFF
        rr(c, X(-0.46f), Y(-0.90f), X(0.46f), Y(fg0 - 0.06f), hw * 0.45f, fill)
        if (rg + 0.12f < 0.9f) { fill.color = 0x1FFFFFFF; rr(c, X(-0.50f), Y(rg + 0.12f), X(0.50f), Y(0.92f), hw * 0.40f, fill) }

        if (icon == 3) { // خطوط السباق
            fill.color = al(0xFFFFFFFF.toInt())
            rr(c, X(-0.22f), Y(-0.97f), X(-0.08f), Y(0.96f), 1f * px, fill)
            rr(c, X(0.08f), Y(-0.97f), X(0.22f), Y(0.96f), 1f * px, fill)
        }

        val glass = al(0xFF101B26.toInt())
        fill.color = glass
        path.reset()
        path.moveTo(X(-0.80f), Y(fg0))
        path.cubicTo(X(-0.45f), Y(fg0 - 0.12f), X(0.45f), Y(fg0 - 0.12f), X(0.80f), Y(fg0))
        path.lineTo(X(0.68f), Y(rs)); path.lineTo(X(-0.68f), Y(rs)); path.close()
        c.drawPath(path, fill)
        fill.color = 0x33FFFFFF
        path.reset()
        path.moveTo(X(-0.55f), Y(fg0 + 0.02f)); path.lineTo(X(-0.10f), Y(fg0 + 0.00f)); path.lineTo(X(-0.28f), Y(rs - 0.04f)); path.lineTo(X(-0.62f), Y(rs - 0.04f)); path.close()
        c.drawPath(path, fill)

        fill.shader = LinearGradient(X(-1f), 0f, X(1f), 0f,
            intArrayOf(al(dark), al(light), al(body)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        rr(c, X(-0.74f), Y(rs), X(0.74f), Y(re), hw * 0.28f, fill)
        fill.shader = null
        fill.color = 0x30FFFFFF
        rr(c, X(-0.50f), Y(rs + 0.06f), X(0.30f), Y(rs + 0.26f), hw * 0.20f, fill)
        if (icon == 1) { // حامل السقف
            fill.color = al(0xFF1B1B1F.toInt())
            rr(c, X(-0.60f), Y(rs + 0.03f), X(-0.50f), Y(re - 0.03f), 1.5f * px, fill)
            rr(c, X(0.50f), Y(rs + 0.03f), X(0.60f), Y(re - 0.03f), 1.5f * px, fill)
        }

        fill.color = glass
        rr(c, X(-0.90f), Y(rs + 0.02f), X(-0.76f), Y(re - 0.02f), 2f * px, fill)
        rr(c, X(0.76f), Y(rs + 0.02f), X(0.90f), Y(re - 0.02f), 2f * px, fill)

        path.reset()
        path.moveTo(X(-0.68f), Y(re)); path.lineTo(X(0.68f), Y(re)); path.lineTo(X(0.78f), Y(rg))
        path.cubicTo(X(0.40f), Y(rg + 0.10f), X(-0.40f), Y(rg + 0.10f), X(-0.78f), Y(rg)); path.close()
        c.drawPath(path, fill)

        if (icon == 3) { // جناح خلفي
            fill.color = al(0xFF16161A.toInt())
            rr(c, X(-0.86f), Y(0.84f), X(0.86f), Y(0.93f), 2f * px, fill)
            rr(c, X(-0.55f), Y(0.78f), X(-0.45f), Y(0.86f), 1f * px, fill)
            rr(c, X(0.45f), Y(0.78f), X(0.55f), Y(0.86f), 1f * px, fill)
        }

        lights(c, ::X, ::Y, hw, alpha)
    }

    private fun bodyPath(X: (Float) -> Float, Y: (Float) -> Float) = Path().apply {
        moveTo(X(-0.55f), Y(-0.97f))
        cubicTo(X(-0.20f), Y(-1.04f), X(0.20f), Y(-1.04f), X(0.55f), Y(-0.97f))
        cubicTo(X(0.88f), Y(-0.90f), X(1.0f), Y(-0.72f), X(1.0f), Y(-0.45f))
        lineTo(X(1.0f), Y(0.55f))
        cubicTo(X(1.0f), Y(0.82f), X(0.90f), Y(0.95f), X(0.60f), Y(0.99f))
        cubicTo(X(0.20f), Y(1.03f), X(-0.20f), Y(1.03f), X(-0.60f), Y(0.99f))
        cubicTo(X(-0.90f), Y(0.95f), X(-1.0f), Y(0.82f), X(-1.0f), Y(0.55f))
        lineTo(X(-1.0f), Y(-0.45f))
        cubicTo(X(-1.0f), Y(-0.72f), X(-0.88f), Y(-0.90f), X(-0.55f), Y(-0.97f))
        close()
    }

    private fun lights(c: Canvas, X: (Float) -> Float, Y: (Float) -> Float, hw: Float, alpha: Int) {
        fill.shader = null
        fill.color = 0x40FFF4C2
        c.drawCircle(X(-0.58f), Y(-0.97f), hw * 0.34f, fill)
        c.drawCircle(X(0.58f), Y(-0.97f), hw * 0.34f, fill)
        fill.color = a(0xFFFFF7D6.toInt(), alpha)
        path.reset()
        path.moveTo(X(-0.86f), Y(-0.86f)); path.lineTo(X(-0.30f), Y(-0.96f)); path.lineTo(X(-0.34f), Y(-0.88f)); path.lineTo(X(-0.84f), Y(-0.78f)); path.close()
        c.drawPath(path, fill)
        path.reset()
        path.moveTo(X(0.86f), Y(-0.86f)); path.lineTo(X(0.30f), Y(-0.96f)); path.lineTo(X(0.34f), Y(-0.88f)); path.lineTo(X(0.84f), Y(-0.78f)); path.close()
        c.drawPath(path, fill)
        fill.color = a(0xFFFF2A38.toInt(), alpha)
        rr(c, X(-0.86f), Y(0.90f), X(-0.34f), Y(0.97f), 2f, fill)
        rr(c, X(0.34f), Y(0.90f), X(0.86f), Y(0.97f), 2f, fill)
    }

    // ───────── بيك أب ─────────
    private fun pickup(c: Canvas, size: Float, px: Float, body: Int, alpha: Int) {
        val hl = size * 0.50f
        val hw = hl * 0.52f
        fun X(u: Float) = u * hw
        fun Y(v: Float) = v * hl
        fun al(col: Int) = a(col, alpha)
        val dark = darker(body); val light = lighter(body)

        fill.shader = null
        fill.color = (0x40 * alpha / 255) shl 24
        rr(c, X(-1.06f), Y(-0.92f) + 3f * px, X(1.06f), Y(1.02f) + 3f * px, hw * 0.9f, fill)
        fill.color = al(0xFF0E0E10.toInt())
        for (v in floatArrayOf(-0.66f, 0.46f)) {
            rr(c, X(-1.10f), Y(v), X(-0.88f), Y(v + 0.30f), 3f * px, fill)
            rr(c, X(0.88f), Y(v), X(1.10f), Y(v + 0.30f), 3f * px, fill)
        }
        fill.color = al(dark)
        path.reset(); path.moveTo(X(-0.95f), Y(-0.40f)); path.lineTo(X(-1.20f), Y(-0.36f)); path.lineTo(X(-1.17f), Y(-0.26f)); path.lineTo(X(-0.95f), Y(-0.28f)); path.close()
        c.drawPath(path, fill)
        path.reset(); path.moveTo(X(0.95f), Y(-0.40f)); path.lineTo(X(1.20f), Y(-0.36f)); path.lineTo(X(1.17f), Y(-0.26f)); path.lineTo(X(0.95f), Y(-0.28f)); path.close()
        c.drawPath(path, fill)

        val bp = bodyPath(::X, ::Y)
        fill.shader = LinearGradient(X(-1f), 0f, X(1f), 0f,
            intArrayOf(al(dark), al(light), al(body), al(dark)), floatArrayOf(0f, 0.30f, 0.62f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(bp, fill)
        fill.shader = null
        line.color = al(0xFFFFFFFF.toInt()); line.strokeWidth = 1.6f * px
        c.drawPath(bp, line)

        fill.color = 0x2EFFFFFF
        rr(c, X(-0.46f), Y(-0.90f), X(0.46f), Y(-0.56f), hw * 0.45f, fill)

        // الصندوق الخلفي المكشوف
        fill.color = al(0xFF23262C.toInt())
        rr(c, X(-0.92f), Y(0.16f), X(0.92f), Y(0.97f), 3f * px, fill)
        fill.color = al(0xFF343841.toInt())
        rr(c, X(-0.74f), Y(0.24f), X(0.74f), Y(0.89f), 2f * px, fill)
        fill.color = 0x22FFFFFF
        for (v in floatArrayOf(0.34f, 0.46f, 0.58f, 0.70f, 0.82f)) rr(c, X(-0.70f), Y(v), X(0.70f), Y(v + 0.02f), 1f, fill)

        // المقصورة
        val glass = al(0xFF101B26.toInt())
        fill.color = glass
        path.reset()
        path.moveTo(X(-0.80f), Y(-0.50f))
        path.cubicTo(X(-0.45f), Y(-0.62f), X(0.45f), Y(-0.62f), X(0.80f), Y(-0.50f))
        path.lineTo(X(0.68f), Y(-0.26f)); path.lineTo(X(-0.68f), Y(-0.26f)); path.close()
        c.drawPath(path, fill)
        fill.color = 0x33FFFFFF
        path.reset()
        path.moveTo(X(-0.55f), Y(-0.48f)); path.lineTo(X(-0.10f), Y(-0.50f)); path.lineTo(X(-0.28f), Y(-0.30f)); path.lineTo(X(-0.62f), Y(-0.30f)); path.close()
        c.drawPath(path, fill)
        fill.shader = LinearGradient(X(-1f), 0f, X(1f), 0f,
            intArrayOf(al(dark), al(light), al(body)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        rr(c, X(-0.74f), Y(-0.26f), X(0.74f), Y(0.06f), hw * 0.26f, fill)
        fill.shader = null
        fill.color = glass
        rr(c, X(-0.90f), Y(-0.24f), X(-0.76f), Y(0.04f), 2f * px, fill)
        rr(c, X(0.76f), Y(-0.24f), X(0.90f), Y(0.04f), 2f * px, fill)
        rr(c, X(-0.58f), Y(0.07f), X(0.58f), Y(0.13f), 2f * px, fill)

        lights(c, ::X, ::Y, hw, alpha)
    }

    // ───────── دراجة نارية ─────────
    private fun bike(c: Canvas, size: Float, px: Float, body: Int, alpha: Int) {
        val hl = size * 0.50f
        fun bx(u: Float) = u * hl * 0.30f
        fun Y(v: Float) = v * hl
        fun al(col: Int) = a(col, alpha)
        val dark = darker(body); val light = lighter(body)

        fill.shader = null
        fill.color = (0x40 * alpha / 255) shl 24
        rr(c, bx(-1.5f), Y(-0.96f) + 3f * px, bx(1.5f), Y(0.99f) + 3f * px, hl * 0.2f, fill)

        // العجلتان
        fill.color = al(0xFF0E0E10.toInt())
        rr(c, bx(-0.55f), Y(0.46f), bx(0.55f), Y(0.99f), hl * 0.10f, fill)
        rr(c, bx(-0.50f), Y(-0.99f), bx(0.50f), Y(-0.46f), hl * 0.10f, fill)
        fill.color = 0x30FFFFFF
        rr(c, bx(-0.08f), Y(0.50f), bx(0.08f), Y(0.95f), 1f, fill)
        rr(c, bx(-0.08f), Y(-0.95f), bx(0.08f), Y(-0.50f), 1f, fill)

        // الشوكة والمقود
        line.color = al(0xFF4A4D55.toInt()); line.strokeWidth = 2.4f * px
        c.drawLine(bx(-0.35f), Y(-0.55f), bx(-0.35f), Y(-0.26f), line)
        c.drawLine(bx(0.35f), Y(-0.55f), bx(0.35f), Y(-0.26f), line)
        line.color = al(0xFF16161A.toInt()); line.strokeWidth = 3f * px
        c.drawLine(bx(-2.3f), Y(-0.40f), bx(2.3f), Y(-0.40f), line)
        fill.color = al(0xFF16161A.toInt())
        c.drawCircle(bx(-2.3f), Y(-0.40f), 2.4f * px, fill)
        c.drawCircle(bx(2.3f), Y(-0.40f), 2.4f * px, fill)
        fill.color = al(dark)
        rr(c, bx(-2.9f), Y(-0.56f), bx(-2.0f), Y(-0.48f), 2f * px, fill)
        rr(c, bx(2.0f), Y(-0.56f), bx(2.9f), Y(-0.48f), 2f * px, fill)

        // الخزان والهيكل
        fill.shader = LinearGradient(bx(-1f), 0f, bx(1f), 0f,
            intArrayOf(al(dark), al(light), al(body)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        rr(c, bx(-1.15f), Y(-0.46f), bx(1.15f), Y(0.52f), hl * 0.16f, fill)
        fill.shader = null
        line.color = al(0xFFFFFFFF.toInt()); line.strokeWidth = 1.2f * px
        rect.set(bx(-1.15f), Y(-0.46f), bx(1.15f), Y(0.52f)); c.drawRoundRect(rect, hl * 0.16f, hl * 0.16f, line)
        fill.color = 0x40FFFFFF
        rr(c, bx(-0.7f), Y(-0.40f), bx(0.2f), Y(-0.16f), hl * 0.08f, fill)
        fill.color = al(0xFF1B1B1F.toInt())
        rr(c, bx(-0.85f), Y(0.20f), bx(0.85f), Y(0.50f), hl * 0.08f, fill)

        // الراكب: كتفان وذراعان وخوذة
        val suit = al(0xFF1E2A44.toInt())
        line.color = suit; line.strokeWidth = 4.2f * px
        c.drawLine(bx(-1.35f), Y(-0.20f), bx(-2.3f), Y(-0.40f), line)
        c.drawLine(bx(1.35f), Y(-0.20f), bx(2.3f), Y(-0.40f), line)
        fill.color = suit
        rr(c, bx(-1.75f), Y(-0.30f), bx(1.75f), Y(0.14f), hl * 0.16f, fill)
        fill.color = al(0xFFF2F2F2.toInt())
        c.drawCircle(0f, Y(-0.04f), hl * 0.17f, fill)
        fill.color = al(0xFF101B26.toInt())
        rr(c, bx(-0.75f), Y(-0.16f), bx(0.75f), Y(-0.09f), 2f * px, fill)

        // المصابيح
        fill.color = 0x40FFF4C2
        c.drawCircle(0f, Y(-0.99f), hl * 0.18f, fill)
        fill.color = al(0xFFFFF7D6.toInt())
        rr(c, bx(-0.40f), Y(-1.00f), bx(0.40f), Y(-0.94f), 1.5f * px, fill)
        fill.color = al(0xFFFF2A38.toInt())
        rr(c, bx(-0.40f), Y(0.95f), bx(0.40f), Y(1.00f), 1.5f * px, fill)
    }
}

/** معاينة شكل المركبة داخل الواجهة (شاشة اختيار الأيقونة). */
@Composable
fun CarIconPreview(icon: Int, color: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    ComposeCanvas(modifier) {
        drawIntoCanvas { cv ->
            val c = cv.nativeCanvas
            c.save()
            c.translate(size.width / 2f, size.height / 2f)
            CarArt.draw(c, icon, size.minDimension * 0.92f, density, color.toArgb(), 255)
            c.restore()
        }
    }
}
