package com.jaynak.app.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AppStore
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.OrdersService
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.NavBar
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.pressable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** أدوات مشتركة لشاشات الإدارة المربوطة بالسيرفر (نفس بيانات لوحة التحكم بالضبط). */
object Panel {
    suspend fun get(path: String): JSONObject = OrdersService.send(path, "GET", null)
    suspend fun post(path: String, body: JSONObject): JSONObject = OrdersService.send(path, "POST", body)

    /** نص خام (مثل ملف CSV) من السيرفر. */
    suspend fun getText(path: String): String = withContext(Dispatchers.IO) {
        val base = com.jaynak.app.core.AppConfig.apiBaseURL ?: error("لم يُضبط عنوان الخادم")
        val token = com.jaynak.app.core.AppConfig.sessionToken ?: error("سجّل الدخول")
        val req = okhttp3.Request.Builder().url(com.jaynak.app.core.Net.url(base, path))
            .header("Authorization", "Bearer $token").build()
        com.jaynak.app.core.Net.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("تعذّر تجهيز الملف")
            r.body?.string().orEmpty()
        }
    }

    fun omr(v: Double): String = String.format(java.util.Locale.US, "%.3f ر.ع", v)

    fun whenTxt(t: Double): String {
        if (t <= 0) return "—"
        val f = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT,
            java.util.Locale("ar"))
        return f.format(java.util.Date((t * 1000).toLong()))
    }

    fun num(s: String): Double? {
        val map = mapOf('٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
            '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9', '٫' to '.', ',' to '.')
        return s.trim().map { map[it] ?: it }.joinToString("").toDoubleOrNull()
    }

    fun fmt(v: Double): String =
        if (v == Math.rint(v) && Math.abs(v) < 1e9) v.toLong().toString() else String.format(java.util.Locale.US, "%s", v)
}

internal fun JSONObject.ps(k: String): String =
    if (!has(k) || isNull(k)) "" else opt(k).let { if (it is Double && it == Math.rint(it)) it.toLong().toString() else it.toString() }
internal fun JSONObject.pd(k: String): Double = if (!has(k) || isNull(k)) 0.0 else optDouble(k, 0.0)
internal fun JSONObject.pi(k: String): Int = pd(k).toInt()
internal fun JSONObject.pb(k: String, def: Boolean = false): Boolean = if (!has(k) || isNull(k)) def else optBoolean(k, def)
internal fun JSONObject.po(k: String): JSONObject = optJSONObject(k) ?: JSONObject()
internal fun JSONObject.pl(k: String): List<JSONObject> {
    val a = optJSONArray(k) ?: return emptyList()
    return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
}
internal fun JSONObject.pstrs(k: String): List<String> {
    val a = optJSONArray(k) ?: return emptyList()
    return (0 until a.length()).map { a.optString(it) }
}
internal fun jsonOf(vararg p: Pair<String, Any?>): JSONObject {
    val o = JSONObject()
    p.forEach { (k, v) -> o.put(k, v ?: JSONObject.NULL) }
    return o
}
internal fun jsonArr(items: Collection<Any?>): JSONArray { val a = JSONArray(); items.forEach { a.put(it) }; return a }

/** ينفّذ عملاً على السيرفر ويعرض رسالة نجاح/خطأ. يعيد true عند النجاح. */
suspend fun AppStore.panelRun(ok: String, work: suspend () -> Unit): Boolean =
    try {
        work()
        if (ok.isNotEmpty()) toast = AppStore.Toast(text = ok, kind = AppStore.Toast.Kind.success)
        Feedback.success()
        true
    } catch (e: Exception) {
        toast = AppStore.Toast(text = e.message ?: "فشل الطلب", kind = AppStore.Toast.Kind.error)
        false
    }

/** شاشة بخلفية العلامة وشريط عنوان وتمرير عمودي، مع تحديث دوري اختياري. */
@Composable
fun PanelScreen(
    title: String,
    pollMs: Long = 0,
    refresh: suspend () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    LaunchedEffect(Unit) {
        while (true) {
            try { refresh() } catch (_: Exception) {}
            if (pollMs <= 0) break
            delay(pollMs)
        }
    }
    Box(Modifier.fillMaxSize()) {
        BrandBackground()
        Column(Modifier.fillMaxSize()) {
            NavBar(title = title)
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = {
                    content()
                    Spacer(Modifier.height(170.dp))
                })
        }
    }
}

/** ورقة سفلية بعنوان وزر إغلاق. */
@Composable
fun PanelSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    JSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxSize()) {
            BrandBackground(intensity = 0.5f)
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("إغلاق", style = MBType.callout, color = MB.green, modifier = Modifier.clickableNoRipple(onDismiss))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(title, style = MBType.headline, color = MB.ink)
                    }
                    Spacer(Modifier.padding(horizontal = 20.dp))
                }
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp), content = {
                        content()
                        Spacer(Modifier.height(60.dp))
                    })
            }
        }
    }
}

@Composable
fun PanelCard(modifier: Modifier = Modifier, tint: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().glassSurface(18.dp, tint, 0.08f).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp), horizontalAlignment = Alignment.End, content = content)
}

@Composable
fun PanelButton(title: String, tint: Color = MB.green, filled: Boolean = false, onClick: () -> Unit) {
    Text(title, style = MBType.micro, color = if (filled) Color.White else tint, textAlign = TextAlign.Center,
        modifier = Modifier
            .background(if (filled) tint else tint.copy(alpha = 0.14f), CircleShape)
            .pressable(0.96f) { Feedback.impact(); onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp))
}

@Composable
fun PanelKV(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(v, style = MBType.caption, color = MB.ink)
        Spacer(Modifier.weight(1f))
        Text(k, style = MBType.caption, color = MB.ink2)
    }
}

/** صف أزرار قابل للتمرير أفقياً. */
@Composable
fun PanelActions(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        content()
    }
}

/** حالات الطلب كما في اللوحة. */
object PanelStatus {
    fun title(s: String) = mapOf("searching" to "بحث عن مندوب", "accepted" to "تم القبول", "pickedUp" to "تم الاستلام",
        "delivering" to "في الطريق", "delivered" to "مكتمل", "cancelled" to "ملغي")[s] ?: s
    fun tint(s: String): Color = when (s) {
        "searching" -> MB.gold
        "accepted", "pickedUp", "delivering" -> MB.green
        "cancelled" -> MB.red
        else -> MB.ink2
    }
    fun isLive(s: String) = s in listOf("searching", "accepted", "pickedUp", "delivering")
    val kinds = mapOf("errand" to "مشوار", "parcel" to "طرد", "documents" to "مستندات", "express" to "سريع", "food" to "طعام")
    val pay = mapOf("cash" to "نقداً", "wallet" to "المحفظة", "card" to "بطاقة")
}

/** حقل رقمي/نصي صغير ببادئة، يعمل على حالة خارجية. */
@Composable
fun rememberText(initial: String = "") = remember { mutableStateOf(initial) }
