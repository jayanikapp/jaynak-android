package com.jaynak.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** يستخرج الإحداثيات من رابط موقع (Google Maps وغيره، حتى الروابط المختصرة) أو من نص «lat, lon». */
object MapLinks {
    private val pair = Regex("""^\s*(-?\d{1,2}\.\d+)\s*[,،]\s*(-?\d{1,3}\.\d+)\s*$""")
    private val patterns = listOf(
        Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)"""),
        Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)"""),
        Regex("""[?&](?:q|ll|query|destination|center|daddr)=(-?\d+\.\d+)(?:,|%2C|%2c)\s*(-?\d+\.\d+)"""),
        Regex("""geo:(-?\d+\.\d+),(-?\d+\.\d+)"""),
        Regex("""/maps/place/(-?\d+\.\d+),(-?\d+\.\d+)"""),
    )

    fun looksLikeLink(s: String): Boolean {
        val t = s.trim()
        return t.contains("http", true) || t.contains("goo.gl", true) || t.contains("maps.app", true) ||
            t.startsWith("geo:", true) || pair.matches(t)
    }

    private fun valid(la: String, lo: String): Coord? {
        val a = la.toDoubleOrNull() ?: return null
        val b = lo.toDoubleOrNull() ?: return null
        return if (a in -90.0..90.0 && b in -180.0..180.0) Coord(a, b) else null
    }

    fun parse(text: String): Coord? {
        pair.find(text)?.let { m -> valid(m.groupValues[1], m.groupValues[2])?.let { return it } }
        for (p in patterns) p.find(text)?.let { m -> valid(m.groupValues[1], m.groupValues[2])?.let { return it } }
        return null
    }

    /** يحلّ الرابط؛ الروابط المختصرة تُتبَّع إلى عنوانها النهائي. null إن لم نجد إحداثيات. */
    suspend fun resolve(text: String): Coord? = withContext(Dispatchers.IO) {
        parse(text)?.let { return@withContext it }
        val url = Regex("""https?://\S+""").find(text)?.value ?: return@withContext null
        runCatching {
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                .get().build()
            Net.client.newCall(req).execute().use { r ->
                parse(r.request.url.toString())
                    ?: parse(r.body?.string()?.take(400_000).orEmpty())
            }
        }.getOrNull()
    }
}
