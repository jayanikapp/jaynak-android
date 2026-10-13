package com.jaynak.app.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {

    val cityCenter = Coord(23.5900, 58.4000)

    val places: List<Place> = listOf(
        Place(name = "سوق مطرح", zone = "مطرح", coord = Coord(23.6178, 58.5636), details = "المدخل الرئيسي"),
        Place(name = "جامع السلطان قابوس الأكبر", zone = "بوشر", coord = Coord(23.5836, 58.3878), details = ""),
        Place(name = "سيتي سنتر مسقط", zone = "السيب", coord = Coord(23.5866, 58.3411), details = "البوابة ٣"),
        Place(name = "أفنيوز مول مسقط", zone = "بوشر", coord = Coord(23.5798, 58.3592), details = "الدور الأرضي"),
        Place(name = "مطار مسقط الدولي", zone = "السيب", coord = Coord(23.5933, 58.2844), details = "صالة الوصول"),
        Place(name = "مستشفى خولة", zone = "مطرح", coord = Coord(23.5946, 58.4295), details = "مدخل الطوارئ"),
        Place(name = "جامعة السلطان قابوس", zone = "السيب", coord = Coord(23.5905, 58.1665), details = "البوابة الرئيسية"),
        Place(name = "شاطئ القرم", zone = "مسقط", coord = Coord(23.6100, 58.4740), details = ""),
        Place(name = "روي - المنطقة التجارية", zone = "مطرح", coord = Coord(23.6000, 58.5400), details = "مبنى ١٢"),
        Place(name = "الخوير - جمعية", zone = "بوشر", coord = Coord(23.5900, 58.4200), details = "قطعة ٤"),
        Place(name = "العامرات - قطعة ٦", zone = "العامرات", coord = Coord(23.5100, 58.5000), details = "منزل ١٨، شارع ٣"),
        Place(name = "المعبيلة الجنوبية", zone = "السيب", coord = Coord(23.6300, 58.1900), details = ""),
    )

    fun place(name: String): Place = places.firstOrNull { it.name == name } ?: places[0]

    fun nearestZone(coord: Coord): String =
        places.minByOrNull { it.coord.km(coord) }?.zone ?: "مسقط"

    /** مسار منحنٍ قليلاً بين نقطتين — يحاكي طريقاً. */
    fun route(a: Coord, b: Coord): List<Coord> {
        val steps = 5
        val dx = b.lon - a.lon
        val dy = b.lat - a.lat

        val nx = -dy
        val ny = dx
        val mag = max(0.00001, sqrt(nx * nx + ny * ny))
        val out = mutableListOf(a)
        for (i in 1 until steps) {
            val t = i.toDouble() / steps
            val bend = sin(t * PI) * 0.12
            out.add(Coord(a.lat + dy * t + (ny / mag) * bend * mag,
                a.lon + dx * t + (nx / mag) * bend * mag))
        }
        out.add(b)
        return out
    }

    fun position(route: List<Coord>, t: Double): Coord {
        if (route.size <= 1) return route.firstOrNull() ?: cityCenter
        val clamped = min(max(t, 0.0), 1.0)
        val total = clamped * (route.size - 1)
        val i = min(total.toInt(), route.size - 2)
        return route[i].lerp(route[i + 1], total - i)
    }

    /** أقصر مسافة (كم) من نقطة إلى القطعة المستقيمة a→b. */
    fun distanceToPath(p: Coord, a: Coord, b: Coord): Double {
        val kmPerLat = 111.0
        val kmPerLon = 111.0 * cos(a.lat * PI / 180)

        val ax = 0.0
        val ay = 0.0
        val bx = (b.lon - a.lon) * kmPerLon
        val by = (b.lat - a.lat) * kmPerLat
        val px = (p.lon - a.lon) * kmPerLon
        val py = (p.lat - a.lat) * kmPerLat

        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq <= 0.000001) return sqrt(px * px + py * py)

        val t = max(0.0, min(1.0, (px * dx + py * dy) / lenSq))
        val cx = t * dx
        val cy = t * dy
        return sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
    }

    fun minutes(km: Double): Int = (km / 30.0 * 60.0).roundedAway().toInt() + 6
}
