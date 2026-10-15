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
