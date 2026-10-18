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
        Regex("""[?&;](?:q|ll|query|destination|center|daddr|markers)=(?:[a-z]+(?:%3A|:)[a-z]+(?:%7C|\|))?(-?\d+\.\d+)(?:,|%2C|%2c)\s*(-?\d+\.\d+)"""),
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

    private val agents = listOf(
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36",
    )
    private val appState = Regex("""\[\[\[-?[\d.]+,(-?\d+\.\d+),(-?\d+\.\d+)\]""")

    /** ينظّف نص الصفحة من ترميز HTML/JSON حتى تُقرأ الإحداثيات منه. */
    private fun unescape(s: String) = s.replace("&amp;", "&").replace("\\u003d", "=")
        .replace("\\u0026", "&").replace("\\/", "/")

    /** إحداثيات من محتوى صفحة خرائط جوجل (صورة الخريطة الثابتة أو بيانات الصفحة الأولية). */
    private fun parseBody(raw: String): Coord? {
        val body = unescape(raw)
        parse(body)?.let { return it }
        // APP_INITIALIZATION_STATE: [[[مسافة, خط الطول, خط العرض], ...
        appState.find(body)?.let { m -> valid(m.groupValues[2], m.groupValues[1])?.let { return it } }
        return null
    }

    /** اسم المكان من عنوان الرابط النهائي (/maps/place/الاسم/ أو q=الاسم). */
    private fun placeName(url: String): String? {
        Regex("""/maps/place/([^/@?]+)""").find(url)?.let { m ->
            val n = runCatching { java.net.URLDecoder.decode(m.groupValues[1], "UTF-8") }.getOrDefault(m.groupValues[1])
            return n.replace('+', ' ').trim().ifEmpty { null }
        }
        Regex("""[?&](?:q|query|destination)=([^&]+)""").find(url)?.let { m ->
            val n = runCatching { java.net.URLDecoder.decode(m.groupValues[1], "UTF-8") }.getOrDefault(m.groupValues[1])
            return n.trim().ifEmpty { null }
        }
        return null
    }

    /** يحلّ الرابط؛ الروابط المختصرة تُتبَّع إلى عنوانها النهائي. null إن لم نجد إحداثيات. */
    suspend fun resolve(text: String): Coord? = withContext(Dispatchers.IO) {
        parse(text)?.let { return@withContext it }
        val url = Regex("""https?://\S+""").find(text)?.value ?: return@withContext null
        var lastFinal: String? = null
        for (ua in agents) {
            val found = runCatching {
                val req = Request.Builder().url(url)
                    .header("User-Agent", ua).header("Accept-Language", "ar,en;q=0.8")
                    .get().build()
                Net.client.newCall(req).execute().use { r ->
                    var final = r.request.url.toString()
                    // صفحة الموافقة: الرابط الحقيقي داخل continue=
                    if (r.request.url.host.contains("consent.google")) {
                        r.request.url.queryParameter("continue")?.let { final = it }
                    }
                    lastFinal = final
                    parse(final) ?: parseBody(r.body?.string()?.take(600_000).orEmpty())
                }
            }.getOrNull()
            if (found != null) return@withContext found
        }
        // احتياطي: لا إحداثيات في الرابط — نبحث باسم المكان
        lastFinal?.let { placeName(it) }?.let { name ->
            runCatching { PlaceSearch.search(name).firstOrNull()?.coord }.getOrNull()?.let { return@withContext it }
        }
        null
    }
}
