package com.jaynak.app.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.LayoutDirection
import org.json.JSONObject
import java.util.Locale

/** اللغات المدعومة. العربية هي الافتراضية والباقي اختياري. */
enum class AppLang(val code: String, val nativeName: String, val rtl: Boolean) {
    AR("ar", "العربية", true),
    EN("en", "English", false),
    UR("ur", "اردو", true),
    HI("hi", "हिन्दी", false),
    BN("bn", "বাংলা", false);

    val layoutDirection get() = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    companion object {
        fun from(code: String?) = values().firstOrNull { it.code == code } ?: AR
    }
}

object Lang {
    private const val PREFS = "jaynak_lang"
    private const val KEY = "lang"
    private var appContext: Context? = null

    /** حالة قابلة للمراقبة: تتغيّر الواجهة عند تبديل اللغة. */
    var current by mutableStateOf(AppLang.AR)
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        val code = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "ar")
        current = AppLang.from(code)
        Translator.use(current, appContext)
    }

    fun set(l: AppLang) {
        if (l == current) return
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY, l.code)?.apply()
        Translator.use(l, appContext)
        current = l
    }
}

/** مترجم نصوص وقت التشغيل: المفتاح هو النص العربي الأصلي. */
object Translator {
    private class Template(val regex: Regex, val groups: Int, val target: String)

    private val dicts = HashMap<AppLang, Map<String, String>>()
    private val templates = HashMap<AppLang, List<Template>>()
    private val cache = HashMap<AppLang, HashMap<String, String>>()

    @Volatile private var lang = AppLang.AR

    @Synchronized
    fun use(l: AppLang, ctx: Context?) {
        lang = l
        if (l == AppLang.AR || dicts.containsKey(l) || ctx == null) return
        val map = HashMap<String, String>()
        try {
            val text = ctx.assets.open("i18n/${l.code}.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val o = JSONObject(text)
            val it = o.keys()
            while (it.hasNext()) { val k = it.next(); map[k] = o.getString(k) }
        } catch (_: Exception) {}
        dicts[l] = map
        val ph = Regex("""\{\d+\}""")
        val ts = ArrayList<Pair<Int, Template>>()
        for ((k, v) in map) {
            if (!k.contains("{1}")) continue
            val sb = StringBuilder("^")
            var literal = 0
            var groups = 0
            var last = 0
            for (m in ph.findAll(k)) {
                val lit = k.substring(last, m.range.first)
                literal += lit.length
                sb.append(Regex.escape(lit)).append("(.+?)")
                groups++
                last = m.range.last + 1
            }
            val tail = k.substring(last)
            literal += tail.length
            sb.append(Regex.escape(tail)).append("$")
            ts.add(literal to Template(Regex(sb.toString(), RegexOption.DOT_MATCHES_ALL), groups, v))
        }
        templates[l] = ts.sortedByDescending { it.first }.map { it.second }
    }

    @Synchronized
    fun t(s: String): String {
        val l = lang
        if (l == AppLang.AR || s.isEmpty()) return s
        val c = cache.getOrPut(l) { HashMap() }
        c[s]?.let { return it }
        val out = translate(s, l, 0)
        if (c.size > 6000) c.clear()
        c[s] = out
        return out
    }

    private fun isLead(ch: Char) = ch.isWhitespace() || ch in "—–-·)"
    private fun isTrail(ch: Char) = ch.isWhitespace() || ch in "—–-·("

    private fun translate(s: String, l: AppLang, depth: Int): String {
        val d = dicts[l] ?: return s
        if (depth > 6) return s
        d[s]?.let { return it }
        var a = 0
        var b = s.length
        while (a < b && isLead(s[a])) a++
        while (b > a && isTrail(s[b - 1])) b--
        var prefix = s.substring(0, a)
        val suffix = s.substring(b)
        var core = s.substring(a, b)
        if (core.startsWith(". ")) { prefix += ". "; core = core.substring(2) }
        else if (core.startsWith("،")) { prefix += "،"; core = core.substring(1).trim() }
        if (core.isEmpty()) return s
        if (core != s) d[core]?.let { return prefix + it + suffix }
        viaTemplate(core, l, depth)?.let { return prefix + it + suffix }
        viaSegments(core, l, depth)?.let { return prefix + it + suffix }
        return s
    }

    private fun viaTemplate(s: String, l: AppLang, depth: Int): String? {
        val ts = templates[l] ?: return null
        for (t in ts) {
            val m = t.regex.matchEntire(s) ?: continue
            if (m.groupValues.size != t.groups + 1) continue
            var out = t.target
            for (g in 1..t.groups) {
                out = out.replace("{$g}", translate(m.groupValues[g], l, depth + 1))
            }
            return out
        }
        return null
    }

    private val sep = Regex(""" · | — |\n| • | \| |(?<=[.!؟?])\s+""")

    private fun viaSegments(s: String, l: AppLang, depth: Int): String? {
        val seps = sep.findAll(s).toList()
        if (seps.isEmpty()) return null
        val sb = StringBuilder()
        var cursor = 0
        var changed = false
        fun piece(p: String) {
            val tr = translate(p, l, depth + 1)
            if (tr != p) changed = true
            sb.append(tr)
        }
        for (m in seps) {
            piece(s.substring(cursor, m.range.first))
            sb.append(m.value)
            cursor = m.range.last + 1
        }
        piece(s.substring(cursor))
        return if (changed) sb.toString() else null
    }
}
