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
        val size = dp(46f)
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
        c.translate(-size / 2, -size / 2)
        val w = size; val h = size
        val cw = w * 0.50f; val ch = h * 0.92f
        val x0 = (w - cw) / 2; val y0 = (h - ch) / 2
        val body = s.color

        fun a(col: Int) = (col and 0x00FFFFFF) or (alpha shl 24)
        // ظل على الأرض
        fill.color = (0x47 * alpha / 255) shl 24
        rr(c, x0 - 2f, y0 + h * 0.05f, x0 + cw + 2f, y0 + ch + h * 0.05f, cw * 0.42f, fill)
        // مرايا
        fill.color = a(0xFF1B1B1F.toInt())
        rr(c, x0 - cw * 0.14f, y0 + ch * 0.27f, x0 + cw * 0.04f, y0 + ch * 0.335f, 4f, fill)
        rr(c, x0 + cw * 0.96f, y0 + ch * 0.27f, x0 + cw * 1.14f, y0 + ch * 0.335f, 4f, fill)
        // عجلات
        fill.color = a(0xFF111114.toInt())
        for (fy in floatArrayOf(0.16f, 0.70f)) {
            rr(c, x0 - cw * 0.07f, y0 + ch * fy, x0 + cw * 0.03f, y0 + ch * (fy + 0.16f), 3f, fill)
            rr(c, x0 + cw * 0.97f, y0 + ch * fy, x0 + cw * 1.07f, y0 + ch * (fy + 0.16f), 3f, fill)
        }
        // الجسم بتدرّج جانبي
        val dark = darker(body)
        fill.shader = LinearGradient(x0, 0f, x0 + cw, 0f, intArrayOf(a(dark), a(body), a(dark)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        rr(c, x0, y0, x0 + cw, y0 + ch, cw * 0.40f, fill)
        fill.shader = null
        line.color = a(0xFFFFFFFF.toInt()); line.strokeWidth = w * 0.035f
        rr(c, x0, y0, x0 + cw, y0 + ch, cw * 0.40f, line)
        // لمعة غطاء المحرك
        fill.color = 0x29FFFFFF
        rr(c, x0 + cw * 0.18f, y0 + ch * 0.05f, x0 + cw * 0.82f, y0 + ch * 0.22f, cw * 0.25f, fill)
        // الزجاج الأمامي
        val glass = a(0xFF14202B.toInt())
        fill.color = glass
        path.reset()
        path.moveTo(x0 + cw * 0.14f, y0 + ch * 0.37f); path.lineTo(x0 + cw * 0.22f, y0 + ch * 0.24f)
        path.lineTo(x0 + cw * 0.78f, y0 + ch * 0.24f); path.lineTo(x0 + cw * 0.86f, y0 + ch * 0.37f); path.close()
        c.drawPath(path, fill)
        // السقف
        fill.color = a(body)
        rr(c, x0 + cw * 0.15f, y0 + ch * 0.37f, x0 + cw * 0.85f, y0 + ch * 0.61f, cw * 0.10f, fill)
        fill.color = 0x38FFFFFF
        rr(c, x0 + cw * 0.22f, y0 + ch * 0.40f, x0 + cw * 0.78f, y0 + ch * 0.48f, cw * 0.06f, fill)
        // الزجاج الخلفي
        fill.color = glass
        path.reset()
        path.moveTo(x0 + cw * 0.15f, y0 + ch * 0.61f); path.lineTo(x0 + cw * 0.85f, y0 + ch * 0.61f)
        path.lineTo(x0 + cw * 0.76f, y0 + ch * 0.73f); path.lineTo(x0 + cw * 0.24f, y0 + ch * 0.73f); path.close()
        c.drawPath(path, fill)
        // مصابيح
        fill.color = a(0xFFFFF4C2.toInt())
        rr(c, x0 + cw * 0.10f, y0 + ch * 0.015f, x0 + cw * 0.34f, y0 + ch * 0.065f, 3f, fill)
        rr(c, x0 + cw * 0.66f, y0 + ch * 0.015f, x0 + cw * 0.90f, y0 + ch * 0.065f, 3f, fill)
        fill.color = a(0xFFFF1F2D.toInt())
        rr(c, x0 + cw * 0.10f, y0 + ch * 0.935f, x0 + cw * 0.36f, y0 + ch * 0.975f, 3f, fill)
        rr(c, x0 + cw * 0.64f, y0 + ch * 0.935f, x0 + cw * 0.90f, y0 + ch * 0.975f, 3f, fill)
        c.restore()

        s.label?.let { lb ->
            text.textSize = dp(11f)
            val tw = text.measureText(lb) + dp(16f); val th = dp(20f)
            val top = y + dp(30f)
            rect.set(x - tw / 2, top, x + tw / 2, top + th)
            fill.color = 0xCC000000.toInt()
            c.drawRoundRect(rect, th / 2, th / 2, fill)
            c.drawText(lb, x, top + th / 2 - (text.ascent() + text.descent()) / 2, text)
        }
    }

    private fun darker(col: Int): Int {
        val r = ((col shr 16) and 0xFF) * 0.62f; val g = ((col shr 8) and 0xFF) * 0.62f; val b = (col and 0xFF) * 0.62f
        return (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    override fun onSingleTapConfirmed(e: MotionEvent, map: MapView): Boolean {
        val proj = map.projection ?: return false
        val r = dp(28f)
        for ((id, s) in states.entries.reversed()) {
            proj.toPixels(GeoPoint(s.lat, s.lon), pt)
            if (abs(pt.x - e.x) < r && abs(pt.y - e.y) < r) { onTap(id); return true }
        }
        return false
    }
}
