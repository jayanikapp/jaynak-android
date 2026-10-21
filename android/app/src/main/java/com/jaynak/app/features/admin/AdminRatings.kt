package com.jaynak.app.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AdminRole
import com.jaynak.app.design.EmptyStateView
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBField
import com.jaynak.app.design.MBType
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.i18n.Text
import kotlinx.coroutines.launch
import org.json.JSONObject

/** كل التقييمات والملاحظات (زبون ← مندوب، ومندوب ← زبون) + مفتاح تشغيل/إيقاف نافذة التقييم. */
@Composable
fun AdminRatings() {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var switching by remember { mutableStateOf(false) }
    val isOwner = store.currentAdmin?.role == AdminRole.owner

    suspend fun load() {
        try { data = Panel.get("api/admin/ratings"); error = null }
        catch (e: Exception) { if (data == null) error = e.message ?: "غير متاح" }
    }

    fun stars(n: Int) = "★".repeat(n.coerceIn(0, 5)) + "☆".repeat(5 - n.coerceIn(0, 5))

    PanelScreen("التقييمات", refresh = { load() }) {
        val d = data
        if (error != null) {
            EmptyStateView("lock.fill", "غير متاح", error ?: "")
        } else if (d == null) {
            androidx.compose.material3.CircularProgressIndicator(color = MB.green)
        } else {
            val enabled = d.pb("ratingsEnabled", true)
            if (isOwner) {
                PanelCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = enabled, enabled = !switching, onCheckedChange = { on ->
                            switching = true
                            scope.launch {
                                val ok = store.panelRun(if (on) "تم تفعيل نافذة التقييم" else "تم إيقاف نافذة التقييم") {
                                    Panel.post("api/admin/flags", jsonOf("ratingsEnabled" to on))
                                }
                                if (ok) { load(); store.pullRules(force = true) }
                                switching = false
                            }
                        })
                        Spacer(Modifier.weight(1f))
                        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.End) {
                            Text("نافذة التقييم في التطبيقات", style = MBType.callout, color = MB.ink)
                            Text(if (enabled) "مفعّلة: تظهر للزبون والمندوب بعد التسليم."
                                 else "موقوفة: لا تظهر لأحد ولا تُقبل تقييمات جديدة.",
                                style = MBType.micro, color = MB.ink2, textAlign = TextAlign.End)
                        }
                    }
                }
            }
            MBField("بحث", query, { query = it }, placeholder = "اسم أو رقم أو ملاحظة")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("الكل", "منخفضة (١–٢)", "فيها ملاحظة").forEachIndexed { i, t ->
                    AdminChip(t, filter == i) { filter = i }
                }
            }
            val q = query.trim().lowercase()
            val rows = d.pl("ratings").filter { r ->
                val td = r.optJSONObject("toDriver"); val tc = r.optJSONObject("toCustomer")
                val low = (td != null && td.pi("stars") <= 2) || (tc != null && tc.pi("stars") <= 2)
                val note = (td?.ps("note").orEmpty().isNotEmpty()) || (tc?.ps("note").orEmpty().isNotEmpty())
                val okF = filter == 0 || (filter == 1 && low) || (filter == 2 && note)
                val hay = listOf(r.ps("ref"), r.po("customer").ps("name"), r.po("customer").ps("phone"),
                    r.po("driver").ps("name"), r.po("driver").ps("phone"),
                    td?.ps("note").orEmpty(), tc?.ps("note").orEmpty()).joinToString(" ").lowercase()
                okF && (q.isEmpty() || hay.contains(q))
            }
            if (rows.isEmpty()) {
                EmptyStateView("star.slash", "لا توجد تقييمات", "ستظهر هنا تقييمات الزبائن والمناديب بعد التسليم.")
            }
            rows.forEach { r ->
                val c = r.po("customer"); val dr = r.po("driver")
                val cn = c.ps("name").ifEmpty { c.ps("phone") }
                val dn = dr.ps("name").ifEmpty { "المندوب" }
                PanelCard {
                    Row(Modifier.fillMaxWidth()) {
                        Text(r.ps("ref"), style = MBType.callout, color = MB.ink)
                        Spacer(Modifier.weight(1f))
                        Text(Panel.whenTxt(r.pd("at")), style = MBType.micro, color = MB.ink2)
                    }
                    r.optJSONObject("toDriver")?.let { RatingLine("الزبون $cn قيّم المندوب $dn", stars(it.pi("stars")), it.ps("note")) }
                    r.optJSONObject("toCustomer")?.let { RatingLine("المندوب $dn قيّم الزبون $cn", stars(it.pi("stars")), it.ps("note")) }
                }
            }
            Board("تقييم المناديب", d.pl("drivers"))
            Board("تقييم الزبائن", d.pl("customers"))
        }
    }
}

@Composable
private fun RatingLine(title: String, stars: String, note: String) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stars, style = MBType.callout, color = MB.gold)
            Spacer(Modifier.weight(1f))
            Text(title, style = MBType.caption, color = MB.ink)
        }
        if (note.isNotEmpty()) {
            Text(note, style = MBType.caption, color = MB.ink2, textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().background(MB.gold.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(8.dp))
        }
    }
}

@Composable
private fun Board(title: String, list: List<JSONObject>) {
    SectionHeader(title)
    PanelCard {
        if (list.isEmpty()) Text("لا يوجد", style = MBType.caption, color = MB.ink3)
        list.take(15).forEach { x ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("(${x.pi("count")})", style = MBType.micro, color = MB.ink2)
                Spacer(Modifier.padding(horizontal = 3.dp))
                Text(String.format(java.util.Locale.US, "%.2f", x.pd("avg")), style = MBType.callout, color = MB.ink)
                Spacer(Modifier.weight(1f))
                Text(x.ps("phone"), style = MBType.micro, color = MB.ink2)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(x.ps("name").ifEmpty { "—" }, style = MBType.caption, color = MB.ink)
            }
        }
        if (list.isNotEmpty()) Text("الأقل تقييماً أولاً", style = MBType.micro, color = MB.ink3)
    }
}
