package com.jaynak.app.core

import okhttp3.Request
import org.json.JSONObject

/** مسار حقيقي على الشوارع (OSRM) للخريطة؛ يعيد null عند الفشل فيُستخدم المسار التقريبي. */
object RoadRouter {

    private const val BASE = "https://router.project-osrm.org"
    private val cache = object : LinkedHashMap<String, List<Coord>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Coord>>?) = size > 60
    }

    suspend fun route(points: List<Coord>): List<Coord>? {
        if (points.size < 2) return null
        val coords = points.joinToString(";") { "%.5f,%.5f".format(java.util.Locale.US, it.lon, it.lat) }
        synchronized(cache) { cache[coords]?.let { return it } }
        return try {
            val req = Request.Builder()
                .url("$BASE/route/v1/driving/$coords?overview=full&geometries=geojson")
                .header("User-Agent", "jaynak-android")
                .build()
            val res = Net.exec(req)
            if (res.status !in 200..299) return null
            val arr = res.json.optJSONArray("routes")?.optJSONObject(0)
                ?.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
            val out = (0 until arr.length()).map {
                val p = arr.getJSONArray(it); Coord(p.getDouble(1), p.getDouble(0))
            }
            if (out.size < 2) return null
            synchronized(cache) { cache[coords] = out }
            out
        } catch (e: Exception) {
            null
        }
    }
}
