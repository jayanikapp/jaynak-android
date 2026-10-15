package com.jaynak.app.features.shared

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import com.jaynak.app.core.Coord
import com.jaynak.app.core.LiveTracking
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.abs
import kotlin.math.exp

/**
 * سيارات حيّة تُرسم داخل الخريطة نفسها: تثبت على موقعها الجغرافي أثناء السحب والتكبير،
 * وتنزلق بنعومة نحو آخر موقع وتدور مقدمتها باتجاه الحركة.
 */
class MapCarOverlay(private val density: Float) : Overlay() {
    class Car(
        val id: String, val lat: Double, val lon: Double, val heading: Float?,
        val color: Int, val label: String? = null, val selected: Boolean = false, val stale: Boolean = false,
        val icon: Int = 0,
    )

    private class State(var lat: Double, var lon: Double, var tLat: Double, var tLon: Double, var rot: Float, var tRot: Float) {
        var color = 0; var label: String? = null; var selected = false; var stale = false
        var given: Float? = null
        var icon = 0
    }

    var onTap: (String) -> Unit = {}
    private val states = LinkedHashMap<String, State>()
    private var last = 0L

    fun setCars(list: List<Car>) {
        val ids = list.map { it.id }.toSet()
        states.keys.retainAll(ids)
        for (c in list) {
            val s = states[c.id]
            if (s == null) {
                states[c.id] = State(c.lat, c.lon, c.lat, c.lon, c.heading ?: 0f, c.heading ?: 0f)
                    .also { it.color = c.color; it.label = c.label; it.selected = c.selected; it.stale = c.stale; it.given = c.heading; it.icon = c.icon }
            } else {
                val moved = Coord(s.tLat, s.tLon).km(Coord(c.lat, c.lon)) * 1000
                if (moved > 3) {
                    val h = if (c.heading != null && c.heading != 0f) c.heading
                    else LiveTracking.bearing(Coord(s.tLat, s.tLon), Coord(c.lat, c.lon)).toFloat()
                    s.tRot = h
                    if (moved > 1500) { s.lat = c.lat; s.lon = c.lon; s.rot = h }
                    s.tLat = c.lat; s.tLon = c.lon
                } else if (c.heading != null && c.heading != 0f) s.tRot = c.heading
                s.color = c.color; s.label = c.label; s.selected = c.selected; s.stale = c.stale; s.given = c.heading; s.icon = c.icon
            }
        }
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val pt = Point()
    private val rect = RectF()
    private val path = Path()

    private fun dp(v: Float) = v * density

    override fun draw(c: Canvas, map: MapView, shadow: Boolean) {
        if (shadow || states.isEmpty()) return
        val proj = map.projection ?: return
        val now = System.nanoTime()
        val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0.001f, 0.1f)
        last = now
        val k = 1f - exp(-dt / 0.9f)        // انزلاق ناعم نحو الهدف
        val kr = 1f - exp(-dt / 0.25f)      // دوران سريع نسبياً
        var animating = true

        for ((_, s) in states) {
            s.lat += (s.tLat - s.lat) * k
            s.lon += (s.tLon - s.lon) * k
            var d = (s.tRot - s.rot) % 360f
            if (d > 180f) d -= 360f
            if (d < -180f) d += 360f
            s.rot += d * kr

            proj.toPixels(GeoPoint(s.lat, s.lon), pt)
            drawCar(c, s, pt.x.toFloat(), pt.y.toFloat(), now)
        }
        if (animating) map.postInvalidateOnAnimation()
    }

    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, p: Paint) {
        rect.set(l, t, r, b); c.drawRoundRect(rect, rad, rad, p)
    }

    private fun drawCar(c: Canvas, s: State, x: Float, y: Float, now: Long) {
        val size = dp(60f)
        val alpha = if (s.stale) 140 else 255

        // نبض حيّ
        val t = ((now / 1_000_000L) % 1800L) / 1800f
        fill.shader = null
        fill.color = (s.color and 0x00FFFFFF) or (((0.22f * (1 - t) * alpha).toInt()) shl 24)
        c.drawCircle(x, y, size * (0.5f + 0.5f * t), fill)
        if (s.selected) {
            fill.color = 0x59FFFFFF
            c.drawCircle(x, y, size * 0.62f, fill)
        }

        c.save()
        c.translate(x, y)
        c.rotate(s.rot)
        CarArt.draw(c, s.icon, size, density, s.color, alpha)
        c.restore()

        s.label?.let { lb ->
            text.textSize = dp(11f)
            val tw = text.measureText(lb) + dp(16f); val th = dp(20f)
            val top = y + dp(36f)
            rect.set(x - tw / 2, top, x + tw / 2, top + th)
            fill.color = 0xCC000000.toInt()
            c.drawRoundRect(rect, th / 2, th / 2, fill)
            c.drawText(lb, x, top + th / 2 - (text.ascent() + text.descent()) / 2, text)
        }
    }

    private fun lighter(col: Int): Int {
        val r = ((col shr 16) and 0xFF); val g = ((col shr 8) and 0xFF); val b = (col and 0xFF)
        fun l(v: Int) = (v + (255 - v) * 0.38f).toInt()
        return (0xFF shl 24) or (l(r) shl 16) or (l(g) shl 8) or l(b)
    }

    private fun darker(col: Int): Int {
        val r = ((col shr 16) and 0xFF) * 0.62f; val g = ((col shr 8) and 0xFF) * 0.62f; val b = (col and 0xFF) * 0.62f
        return (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    override fun onSingleTapConfirmed(e: MotionEvent, map: MapView): Boolean {
        val proj = map.projection ?: return false
        val r = dp(34f)
        for ((id, s) in states.entries.reversed()) {
            proj.toPixels(GeoPoint(s.lat, s.lon), pt)
            if (abs(pt.x - e.x) < r && abs(pt.y - e.y) < r) { onTap(id); return true }
        }
        return false
    }
}
package com.jaynak.app.features.shared

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import com.jaynak.app.core.Coord
import com.jaynak.app.core.LiveTracking
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.abs
import kotlin.math.exp

/**
 * سيارات حيّة تُرسم داخل الخريطة نفسها: تثبت على موقعها الجغرافي أثناء السحب والتكبير،
 * وتنزلق بنعومة نحو آخر موقع وتدور مقدمتها باتجاه الحركة.
 */
class MapCarOverlay(private val density: Float) : Overlay() {
    class Car(
        val id: String, val lat: Double, val lon: Double, val heading: Float?,
        val color: Int, val label: String? = null, val selected: Boolean = false, val stale: Boolean = false,
    )

    private class State(var lat: Double, var lon: Double, var tLat: Double, var tLon: Double, var rot: Float, var tRot: Float) {
        var color = 0; var label: String? = null; var selected = false; var stale = false
        var given: Float? = null
    }

    var onTap: (String) -> Unit = {}
    private val states = LinkedHashMap<String, State>()
    private var last = 0L

    fun setCars(list: List<Car>) {
        val ids = list.map { it.id }.toSet()
        states.keys.retainAll(ids)
        for (c in list) {
            val s = states[c.id]
            if (s == null) {
                states[c.id] = State(c.lat, c.lon, c.lat, c.lon, c.heading ?: 0f, c.heading ?: 0f)
                    .also { it.color = c.color; it.label = c.label; it.selected = c.selected; it.stale = c.stale; it.given = c.heading }
            } else {
                val moved = Coord(s.tLat, s.tLon).km(Coord(c.lat, c.lon)) * 1000
                if (moved > 3) {
                    val h = if (c.heading != null && c.heading != 0f) c.heading
                    else LiveTracking.bearing(Coord(s.tLat, s.tLon), Coord(c.lat, c.lon)).toFloat()
                    s.tRot = h
                    if (moved > 1500) { s.lat = c.lat; s.lon = c.lon; s.rot = h }
                    s.tLat = c.lat; s.tLon = c.lon
                } else if (c.heading != null && c.heading != 0f) s.tRot = c.heading
                s.color = c.color; s.label = c.label; s.selected = c.selected; s.stale = c.stale; s.given = c.heading
            }
        }
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val pt = Point()
    private val rect = RectF()
    private val path = Path()

    private fun dp(v: Float) = v * density

    override fun draw(c: Canvas, map: MapView, shadow: Boolean) {
        if (shadow || states.isEmpty()) return
        val proj = map.projection ?: return
        val now = System.nanoTime()
        val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0.001f, 0.1f)
        last = now
        val k = 1f - exp(-dt / 0.9f)        // انزلاق ناعم نحو الهدف
        val kr = 1f - exp(-dt / 0.25f)      // دوران سريع نسبياً
        var animating = true

        for ((_, s) in states) {
            s.lat += (s.tLat - s.lat) * k
            s.lon += (s.tLon - s.lon) * k
            var d = (s.tRot - s.rot) % 360f
            if (d > 180f) d -= 360f
            if (d < -180f) d += 360f
            s.rot += d * kr

            proj.toPixels(GeoPoint(s.lat, s.lon), pt)
            drawCar(c, s, pt.x.toFloat(), pt.y.toFloat(), now)
        }
        if (animating) map.postInvalidateOnAnimation()
    }

    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, p: Paint) {
        rect.set(l, t, r, b); c.drawRoundRect(rect, rad, rad, p)
    }

    private fun drawCar(c: Canvas, s: State, x: Float, y: Float, now: Long) {
        val size = dp(60f)
        val alpha = if (s.stale) 140 else 255

        // نبض حيّ
        val t = ((now / 1_000_000L) % 1800L) / 1800f
        fill.shader = null
        fill.color = (s.color and 0x00FFFFFF) or (((0.22f * (1 - t) * alpha).toInt()) shl 24)
        c.drawCircle(x, y, size * (0.5f + 0.5f * t), fill)
        if (s.selected) {
            fill.color = 0x59FFFFFF
            c.drawCircle(x, y, size * 0.62f, fill)
        }

        c.save()
        c.translate(x, y)
        c.rotate(s.rot)
        val hl = size * 0.50f            // نصف الطول (المقدمة للأعلى)
        val hw = hl * 0.50f              // نصف العرض
        fun a(col: Int) = (col and 0x00FFFFFF) or (alpha shl 24)
        fun X(u: Float) = u * hw
        fun Y(v: Float) = v * hl
        val body = s.color
        val dark = darker(body)
        val light = lighter(body)

        // ظل ناعم على الأرض
        fill.shader = null
        fill.color = (0x40 * alpha / 255) shl 24
        rr(c, X(-1.06f), Y(-0.92f) + dp(3f), X(1.06f), Y(1.02f) + dp(3f), hw * 0.9f, fill)

        // العجلات
        fill.color = a(0xFF0E0E10.toInt())
        for (v in floatArrayOf(-0.62f, 0.40f)) {
            rr(c, X(-1.10f), Y(v), X(-0.88f), Y(v + 0.30f), dp(3f), fill)
            rr(c, X(0.88f), Y(v), X(1.10f), Y(v + 0.30f), dp(3f), fill)
        }
        // المرايا الجانبية
        fill.color = a(dark)
        path.reset(); path.moveTo(X(-0.95f), Y(-0.34f)); path.lineTo(X(-1.20f), Y(-0.30f)); path.lineTo(X(-1.17f), Y(-0.20f)); path.lineTo(X(-0.95f), Y(-0.22f)); path.close()
        c.drawPath(path, fill)
        path.reset(); path.moveTo(X(0.95f), Y(-0.34f)); path.lineTo(X(1.20f), Y(-0.30f)); path.lineTo(X(1.17f), Y(-0.20f)); path.lineTo(X(0.95f), Y(-0.22f)); path.close()
        c.drawPath(path, fill)

        // هيكل السيارة (أنف مقوّس + مؤخرة مستديرة)
        val bodyPath = Path().apply {
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
        fill.shader = LinearGradient(X(-1f), 0f, X(1f), 0f,
            intArrayOf(a(dark), a(light), a(body), a(dark)), floatArrayOf(0f, 0.30f, 0.62f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(bodyPath, fill)
        fill.shader = null
        line.color = a(0xFFFFFFFF.toInt()); line.strokeWidth = dp(1.6f)
        c.drawPath(bodyPath, line)

        // خطوط غطاء المحرك وصندوق الخلف
        fill.color = 0x2EFFFFFF
        rr(c, X(-0.46f), Y(-0.90f), X(0.46f), Y(-0.52f), hw * 0.45f, fill)
        fill.color = 0x1FFFFFFF
        rr(c, X(-0.50f), Y(0.62f), X(0.50f), Y(0.92f), hw * 0.40f, fill)

        // الزجاج الأمامي
        val glass = a(0xFF101B26.toInt())
        fill.color = glass
        path.reset()
        path.moveTo(X(-0.80f), Y(-0.46f))
        path.cubicTo(X(-0.45f), Y(-0.58f), X(0.45f), Y(-0.58f), X(0.80f), Y(-0.46f))
        path.lineTo(X(0.68f), Y(-0.18f)); path.lineTo(X(-0.68f), Y(-0.18f)); path.close()
        c.drawPath(path, fill)
        // انعكاس على الزجاج
        fill.color = 0x33FFFFFF
        path.reset()
        path.moveTo(X(-0.55f), Y(-0.44f)); path.lineTo(X(-0.10f), Y(-0.46f)); path.lineTo(X(-0.28f), Y(-0.22f)); path.lineTo(X(-0.62f), Y(-0.22f)); path.close()
        c.drawPath(path, fill)

        // السقف
        fill.shader = LinearGradient(X(-1f), 0f, X(1f), 0f,
            intArrayOf(a(dark), a(light), a(body)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        rr(c, X(-0.74f), Y(-0.18f), X(0.74f), Y(0.30f), hw * 0.28f, fill)
        fill.shader = null
        fill.color = 0x30FFFFFF
        rr(c, X(-0.50f), Y(-0.12f), X(0.30f), Y(0.08f), hw * 0.20f, fill)

        // النوافذ الجانبية
        fill.color = glass
        rr(c, X(-0.90f), Y(-0.16f), X(-0.76f), Y(0.28f), dp(2f), fill)
        rr(c, X(0.76f), Y(-0.16f), X(0.90f), Y(0.28f), dp(2f), fill)

        // الزجاج الخلفي
        path.reset()
        path.moveTo(X(-0.68f), Y(0.30f)); path.lineTo(X(0.68f), Y(0.30f)); path.lineTo(X(0.78f), Y(0.50f))
        path.cubicTo(X(0.40f), Y(0.60f), X(-0.40f), Y(0.60f), X(-0.78f), Y(0.50f)); path.close()
        c.drawPath(path, fill)

        // المصابيح الأمامية (توهّج أبيض مائل للأصفر)
        fill.color = 0x40FFF4C2
        c.drawCircle(X(-0.58f), Y(-0.97f), hw * 0.34f, fill)
        c.drawCircle(X(0.58f), Y(-0.97f), hw * 0.34f, fill)
        fill.color = a(0xFFFFF7D6.toInt())
        path.reset()
        path.moveTo(X(-0.86f), Y(-0.86f)); path.lineTo(X(-0.30f), Y(-0.96f)); path.lineTo(X(-0.34f), Y(-0.88f)); path.lineTo(X(-0.84f), Y(-0.78f)); path.close()
        c.drawPath(path, fill)
        path.reset()
        path.moveTo(X(0.86f), Y(-0.86f)); path.lineTo(X(0.30f), Y(-0.96f)); path.lineTo(X(0.34f), Y(-0.88f)); path.lineTo(X(0.84f), Y(-0.78f)); path.close()
        c.drawPath(path, fill)
        // المصابيح الخلفية
        fill.color = a(0xFFFF2A38.toInt())
        rr(c, X(-0.86f), Y(0.90f), X(-0.34f), Y(0.97f), dp(2f), fill)
        rr(c, X(0.34f), Y(0.90f), X(0.86f), Y(0.97f), dp(2f), fill)
        c.restore()

        s.label?.let { lb ->
            text.textSize = dp(11f)
            val tw = text.measureText(lb) + dp(16f); val th = dp(20f)
            val top = y + dp(36f)
            rect.set(x - tw / 2, top, x + tw / 2, top + th)
            fill.color = 0xCC000000.toInt()
            c.drawRoundRect(rect, th / 2, th / 2, fill)
            c.drawText(lb, x, top + th / 2 - (text.ascent() + text.descent()) / 2, text)
        }
    }

    private fun lighter(col: Int): Int {
        val r = ((col shr 16) and 0xFF); val g = ((col shr 8) and 0xFF); val b = (col and 0xFF)
        fun l(v: Int) = (v + (255 - v) * 0.38f).toInt()
        return (0xFF shl 24) or (l(r) shl 16) or (l(g) shl 8) or l(b)
    }

    private fun darker(col: Int): Int {
        val r = ((col shr 16) and 0xFF) * 0.62f; val g = ((col shr 8) and 0xFF) * 0.62f; val b = (col and 0xFF) * 0.62f
        return (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    override fun onSingleTapConfirmed(e: MotionEvent, map: MapView): Boolean {
        val proj = map.projection ?: return false
        val r = dp(34f)
        for ((id, s) in states.entries.reversed()) {
            proj.toPixels(GeoPoint(s.lat, s.lon), pt)
            if (abs(pt.x - e.x) < r && abs(pt.y - e.y) < r) { onTap(id); return true }
        }
        return false
    }
}
