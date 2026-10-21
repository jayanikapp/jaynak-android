package com.jaynak.app.features.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jaynak.app.design.EmptyStateView
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import org.json.JSONObject

/** تتبّع الطلب — سجل الأحداث ومسار المندوب (نفس بيانات تبويب «تتبّع» في اللوحة). */
@Composable
fun AdminServerTrace() {
    var orders by remember { mutableStateOf(listOf<JSONObject>()) }
    var query by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<String?>(null) }
    suspend fun load() { orders = Panel.get("api/admin/orders").pl("orders") }
    val q = query.trim().lowercase().replace("#", "")
    val rows = orders.filter { o ->
        q.isEmpty() || listOf(o.ps("ref"), o.ps("customerPhone"), o.ps("driverPhone"), o.ps("driverName")).joinToString(" ").lowercase().contains(q)
    }.take(60)

    if (sel != null) {
        TraceDetail(sel!!) { sel = null }
        return
    }
    PanelScreen("تتبّع الطلبات", pollMs = 10000, refresh = { load() }) {
        AdminSearchField(query, { query = it }, "رقم الطلب أو هاتف أو مندوب")
        if (rows.isEmpty()) EmptyStateView("location", "لا طلبات", "")
        rows.forEach { o ->
            PanelCard {
                Row(Modifier.fillMaxWidth().clickable { sel = o.ps("id") }, verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(PanelStatus.title(o.ps("status")), style = MBType.micro, color = MB.green)
                    Spacer(Modifier.weight(1f))
                    Text(o.ps("driverName").ifEmpty { "بدون مندوب" }, style = MBType.caption, color = MB.ink2)
                    Text("#${o.ps("ref")}", style = MBType.num(14, androidx.compose.ui.text.font.FontWeight.Bold), color = MB.ink)
                }
                Text("${o.po("pickup").ps("name")} ← ${o.po("dropoff").ps("name")}", style = MBType.micro, color = MB.ink3)
            }
        }
    }
}

@Composable
private fun TraceDetail(id: String, onBack: () -> Unit) {
    var d by remember(id) { mutableStateOf<JSONObject?>(null) }
    var err by remember(id) { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        try { d = Panel.get("api/admin/order-log?id=" + java.net.URLEncoder.encode(id, "UTF-8")) } catch (e: Exception) { err = e.message }
    }
    androidx.activity.compose.BackHandler(onBack = onBack)
    PanelScreen("سجل الطلب") {
        PanelButton("رجوع", MB.ink2) { onBack() }
        err?.let { EmptyStateView("exclamationmark.triangle.fill", "تعذّر التحميل", it) }
        val o = d ?: run {
            if (err == null) androidx.compose.material3.CircularProgressIndicator(color = MB.green)
            return@PanelScreen
        }
        val ord = o.po("order")
        PanelCard {
            PanelKV("الطلب", "#${ord.ps("ref")}")
            PanelKV("الحالة", PanelStatus.title(ord.ps("status")))
            PanelKV("من", ord.po("pickup").ps("name"))
            PanelKV("إلى", ord.po("dropoff").ps("name"))
            PanelKV("المندوب", ord.ps("driverName").ifEmpty { "—" })
        }
        val ev = o.pl("events")
        val trail = o.optJSONArray("trail")
        PanelCard {
            Text("الأحداث", style = MBType.callout, color = MB.ink)
            if (ev.isEmpty()) Text("لا أحداث", style = MBType.micro, color = MB.ink3)
            ev.forEach { e ->
                val t0 = ev.first().pd("at")
                val dt = ((e.pd("at") - t0) / 60).toInt()
                PanelKV(PanelStatus.title(e.ps("k")).ifEmpty { e.ps("k") },
                    Panel.whenTxt(e.pd("at")) + if (dt > 0) " (+${dt} د)" else "")
            }
            if (trail != null && trail.length() > 1) {
                var km = 0.0
                for (i in 1 until trail.length()) {
                    val a = trail.getJSONArray(i - 1); val b = trail.getJSONArray(i)
                    val r = 6371.0
                    val dLat = Math.toRadians(b.getDouble(0) - a.getDouble(0)); val dLon = Math.toRadians(b.getDouble(1) - a.getDouble(1))
                    val h = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(a.getDouble(0))) * Math.cos(Math.toRadians(b.getDouble(0))) * Math.sin(dLon / 2).let { it * it }
                    km += 2 * r * Math.asin(Math.sqrt(h))
                }
                PanelKV("مسار المندوب", "${trail.length()} نقطة · ${"%.1f".format(km)} كم")
            }
        }
    }
}
