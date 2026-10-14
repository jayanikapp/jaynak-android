package com.jaynak.app.features.shared

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Typeface
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * دبابيس تُرسم داخل الخريطة نفسها: تستعمل نفس إسقاط osmdroid في كل إطار،
 * فتبقى ثابتة على مكانها تماماً أثناء السحب والتكبير (لا علاقة لها بتخطيط Compose أو RTL).
 */
class MapPinOverlay(private val density: Float) : Overlay() {
    class Pin(val lat: Double, val lon: Double, val symbol: String, val color: Int, val label: String)
    class Dot(val lat: Double, val lon: Double)

    var pins: List<Pin> = emptyList()
    var dots: List<Dot> = emptyList()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val pt = Point()
    private val rect = RectF()
    private val path = Path()

    private fun dp(v: Float) = v * density

    override fun draw(c: Canvas, map: MapView, shadow: Boolean) {
        if (shadow) return
        val proj = map.projection ?: return

        for (d in dots) {
            proj.toPixels(GeoPoint(d.lat, d.lon), pt)
            fill.color = 0x8034C759.toInt()
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), dp(5f), fill)
            stroke.color = 0xB3FFFFFF.toInt(); stroke.strokeWidth = dp(1f)
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), dp(5f), stroke)
        }

        for (p in pins) {
            proj.toPixels(GeoPoint(p.lat, p.lon), pt)
            val x = pt.x.toFloat(); val y = pt.y.toFloat()
            val r = dp(15f)
            val cy = y - dp(26f)

            // ذيل الدبوس
            path.reset()
            path.moveTo(x - dp(8f), cy + dp(9f)); path.lineTo(x + dp(8f), cy + dp(9f)); path.lineTo(x, y); path.close()
            fill.color = p.color
            c.drawPath(path, fill)
            c.drawCircle(x, cy, r, fill)
            stroke.color = 0xFFFFFFFF.toInt(); stroke.strokeWidth = dp(2f)
            c.drawCircle(x, cy, r, stroke)

            // الأيقونة
            stroke.color = 0xFFFFFFFF.toInt(); stroke.strokeWidth = dp(2.4f)
            fill.color = 0xFFFFFFFF.toInt()
            when {
                p.symbol.contains("arrow") -> {
                    c.drawLine(x, cy + dp(7f), x, cy - dp(7f), stroke)
                    c.drawLine(x, cy - dp(7f), x - dp(5f), cy - dp(1.5f), stroke)
                    c.drawLine(x, cy - dp(7f), x + dp(5f), cy - dp(1.5f), stroke)
                }
                p.symbol.contains("flag") -> {
                    c.drawLine(x - dp(5f), cy - dp(8f), x - dp(5f), cy + dp(8f), stroke)
                    rect.set(x - dp(5f), cy - dp(8f), x + dp(6f), cy + dp(1f))
                    c.drawRect(rect, fill)
                }
                else -> c.drawCircle(x, cy, dp(4.5f), fill)
            }

            // اللافتة
            if (p.label.isNotEmpty()) {
                text.textSize = dp(12f)
                val w = text.measureText(p.label) + dp(20f)
                val h = dp(22f)
                val top = cy - r - dp(5f) - h
                rect.set(x - w / 2, top, x + w / 2, top + h)
                fill.color = 0xCC000000.toInt()
                c.drawRoundRect(rect, h / 2, h / 2, fill)
                c.drawText(p.label, x, top + h / 2 - (text.ascent() + text.descent()) / 2, text)
            }
        }
    }
}
