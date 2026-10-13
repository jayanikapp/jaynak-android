package com.jaynak.app.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt


private val OMAN_LAT = 16.0..27.0
private val OMAN_LON = 51.0..60.5

fun inOman(lat: Double, lon: Double) = lat in OMAN_LAT && lon in OMAN_LON


fun kmBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dp = p2 - p1
    val dl = Math.toRadians(lon2 - lon1)
    val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    return 2 * r * asin(min(1.0, sqrt(a)))
}

data class Described(val name: String, val sub: String, val zone: String)


fun describe(lat: Double, lon: Double, places: List<Place>): Described {
    val best = places.minByOrNull { kmBetween(lat, lon, it.lat, it.lon) }
        ?: return Described("موقع على الخريطة", "", "")
    val d = kmBetween(lat, lon, best.lat, best.lon)
    if (d <= 0.25) return Described(best.name, best.zone, best.zone)
    val far = if (d < 1) "${(d * 1000).toInt()} م" else "%.1f كم".format(d)
    return Described("قرب ${best.name}", "${best.zone} · $far", best.zone)
}
