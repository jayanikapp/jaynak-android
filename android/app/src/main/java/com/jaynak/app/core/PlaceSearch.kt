package com.jaynak.app.core

import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** بحث عناوين حقيقي (Geocoder ثم OpenStreetMap) وتحويل الإحداثيات لاسم عنوان. */
object PlaceSearch {

    suspend fun search(q: String): List<Place> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Place>()
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(Boot.app, Locale("ar", "OM")).getFromLocationName(q, 8, 16.5, 51.9, 26.5, 59.9)
                ?.forEach { a ->
                    val line = a.getAddressLine(0).orEmpty()
                    val name = listOfNotNull(a.featureName, a.thoroughfare, a.subLocality)
                        .filter { it.isNotBlank() }.distinct().joinToString(" - ").ifEmpty { line }
                    val c = Coord(a.latitude, a.longitude)
                    if (name.isNotEmpty()) out.add(Place(name = name, zone = Geo.nearestZone(c), coord = c, details = line))
                }
        }
        if (out.isEmpty()) runCatching {
            val u = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=8&countrycodes=om&accept-language=ar&q=" +
                URLEncoder.encode(q, "UTF-8")
            val arr = JSONArray(get(u))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val c = Coord(o.getString("lat").toDouble(), o.getString("lon").toDouble())
                val full = o.optString("display_name")
                val name = o.optString("name").ifEmpty { full.substringBefore("،").substringBefore(",") }
                out.add(Place(name = name, zone = Geo.nearestZone(c), coord = c, details = full))
            }
        }
        out
    }

    /** اسم العنوان لإحداثيات (الشارع / الحي) — null إن تعذّر. */
    suspend fun reverse(c: Coord): String? = withContext(Dispatchers.IO) {
        val g = runCatching {
            @Suppress("DEPRECATION")
            val m = Geocoder(Boot.app, Locale("ar", "OM")).getFromLocation(c.lat, c.lon, 1)?.firstOrNull()
            val parts = listOfNotNull(m?.featureName?.takeIf { it.any { ch -> !ch.isDigit() } },
                m?.thoroughfare, m?.subLocality ?: m?.locality).filter { it.isNotBlank() }.distinct()
            if (parts.isEmpty()) m?.getAddressLine(0) else parts.joinToString(" - ")
        }.getOrNull()
        if (!g.isNullOrBlank()) return@withContext g
        runCatching {
            val u = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&accept-language=ar&zoom=18&lat=${c.lat}&lon=${c.lon}"
            val o = JSONObject(get(u))
            val a = o.optJSONObject("address")
            val parts = listOf("road", "neighbourhood", "suburb", "city").mapNotNull { a?.optString(it)?.takeIf { s -> s.isNotBlank() } }
            if (parts.isNotEmpty()) parts.take(2).joinToString(" - ") else o.optString("display_name").substringBefore("،")
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** الولاية/المنطقة الفعلية للإحداثيات (بدل أقرب منطقة من قائمة ثابتة). */
    suspend fun area(c: Coord): String? = withContext(Dispatchers.IO) {
        val g = runCatching {
            @Suppress("DEPRECATION")
            val m = Geocoder(Boot.app, Locale("ar", "OM")).getFromLocation(c.lat, c.lon, 1)?.firstOrNull()
            listOf(m?.subAdminArea, m?.locality, m?.adminArea).firstOrNull { !it.isNullOrBlank() }
        }.getOrNull()
        if (!g.isNullOrBlank()) return@withContext g
        runCatching {
            val u = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&accept-language=ar&zoom=10&lat=${c.lat}&lon=${c.lon}"
            val a = JSONObject(get(u)).optJSONObject("address")
            listOf("county", "city", "town", "state_district", "state").firstNotNullOfOrNull { k -> a?.optString(k)?.takeIf { it.isNotBlank() } }
        }.getOrNull()
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "jaynak-android")
        c.connectTimeout = 8000; c.readTimeout = 8000
        return c.inputStream.bufferedReader().use { it.readText() }
    }
}
