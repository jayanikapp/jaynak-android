package com.jaynak.app.features.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.jaynak.app.design.ConfirmDialog
import com.jaynak.app.design.EmptyStateView
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBField
import com.jaynak.app.design.MBType
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SecondaryCTA
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.StatTile
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

// ───────── الرتب ─────────

private data class RankEdit(val rid: String, val kind: String, val title: String, val minPoints: Int)

@Composable
fun AdminServerRanks() {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var ranks by remember { mutableStateOf(listOf<JSONObject>()) }
    var loading by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<RankEdit?>(null) }
    var deleting by remember { mutableStateOf<JSONObject?>(null) }
    suspend fun load() { ranks = Panel.get("api/ranks").pl("ranks"); loading = false }

    PanelScreen("الرتب", refresh = { load() }) {
        if (loading && ranks.isEmpty()) androidx.compose.material3.CircularProgressIndicator(color = MB.green)
        listOf("driver" to "رتب المناديب", "customer" to "رتب الزبائن").forEach { (kind, title) ->
            SectionHeader(title, symbol = if (kind == "driver") "car.fill" else "person.fill")
            ranks.filter { it.ps("kind") == kind }.sortedBy { it.pi("minPoints") }.forEach { r ->
                PanelCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PanelButton("حذف", MB.red) { deleting = r }
                        PanelButton("تعديل") { editing = RankEdit(r.ps("id"), kind, r.ps("title"), r.pi("minPoints")) }
                        Spacer(Modifier.weight(1f))
                        Text("من ${r.pi("minPoints")} نقطة", style = MBType.caption, color = MB.ink2)
                        Text(r.ps("title"), style = MBType.callout, color = MB.ink)
                    }
                }
            }
            SecondaryCTA("إضافة رتبة", { editing = RankEdit("", kind, "", 0) }, symbol = "plus")
        }
    }

    editing?.let { e ->
        var t by remember(e) { mutableStateOf(e.title) }
        var pts by remember(e) { mutableStateOf(e.minPoints.toString()) }
        var busy by remember(e) { mutableStateOf(false) }
        PanelSheet(if (e.rid.isEmpty()) "رتبة جديدة" else "تعديل رتبة", { editing = null }) {
            MBField("الاسم", t, { t = it }, placeholder = "اسم الرتبة")
            MBField("الحد الأدنى للنقاط", pts, { pts = it }, placeholder = "0", keyboard = KeyboardType.Number, isRTLText = false)
            PrimaryCTA("حفظ", {
                busy = true
                scope.launch {
                    val ok = store.panelRun("تم الحفظ") {
                        Panel.post("api/admin/ranks", jsonOf("action" to "save", "id" to e.rid, "kind" to e.kind,
                            "title" to t, "minPoints" to (pts.toIntOrNull() ?: 0)))
                    }
                    busy = false
                    if (ok) { load(); editing = null }
                }
            }, symbol = "checkmark", enabled = !busy && t.isNotEmpty() && pts.toIntOrNull() != null)
        }
    }
    deleting?.let { r ->
        ConfirmDialog("حذف هذه الرتبة؟", confirmText = "حذف", destructive = true, onConfirm = {
            scope.launch {
                store.panelRun("تم الحذف") { Panel.post("api/admin/ranks", jsonOf("action" to "delete", "id" to r.ps("id"))) }
                load()
            }
        }, onDismiss = { deleting = null })
    }
}

// ───────── طلبات حذف الحسابات (من السيرفر) ─────────

@Composable
fun AdminServerDeletions() {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var reqs by remember { mutableStateOf(listOf<JSONObject>()) }
    var loading by remember { mutableStateOf(true) }
    var approving by remember { mutableStateOf<JSONObject?>(null) }
    var rejecting by remember { mutableStateOf<JSONObject?>(null) }
    suspend fun load() { reqs = Panel.get("api/admin/deletions").pl("requests"); loading = false }

    PanelScreen("طلبات حذف الحسابات", pollMs = 8000, refresh = { load() }) {
        StatTile("طلبات حذف بانتظار المراجعة", "${reqs.count { it.ps("status") == "pending" }}", "hourglass", tint = MB.red)
        Text("عند الموافقة يُحذف الحساب نهائياً ويُسجَّل خروج صاحبه، ولا يدخل به أبداً — إن سجّل الدخول لاحقاً فيُنشئ حساباً جديداً فارغاً.",
            style = MBType.micro, color = MB.ink2)
        if (loading && reqs.isEmpty()) androidx.compose.material3.CircularProgressIndicator(color = MB.green)
        else if (reqs.isEmpty()) EmptyStateView("person.fill.xmark", "لا طلبات", "لا توجد طلبات حذف")
        reqs.forEach { r ->
            val st = r.ps("status"); val drv = r.ps("role") == "driver"
            val t = mapOf("pending" to ("قيد المراجعة" to MB.gold), "approved" to ("تم الحذف" to MB.red))[st] ?: ("مرفوض" to MB.ink2)
            PanelCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(r.ps("phone"), style = MBType.num(11, androidx.compose.ui.text.font.FontWeight.Medium), color = MB.ink3)
                    Spacer(Modifier.weight(1f))
                    MBChip(t.first, tint = t.second, filled = true)
                    MBChip(if (drv) "مندوب" else "زبون", tint = MB.ink2)
                    Text(r.ps("name").ifEmpty { "بدون اسم" }, style = MBType.callout, color = MB.ink)
                }
                Text("السبب: ${r.ps("reason").ifEmpty { "—" }} · ${Panel.whenTxt(r.pd("createdAt"))}", style = MBType.micro, color = MB.ink2)
                if (st == "pending") {
                    val warn = buildList {
                        if (r.pi("activeOrders") > 0) add("⚠ لديه ${r.pi("activeOrders")} طلب جارٍ")
                        if (r.pi("pendingWithdrawals") > 0) add("⚠ لديه سحب معلّق")
                        if (Math.abs(r.pd("balance")) > 0.0005) add("${if (drv) "رصيد الأرباح" else "رصيد المحفظة"}: ${Panel.omr(r.pd("balance"))}")
                        if (!r.pb("accountExists", true)) add("الحساب غير موجود على السيرفر")
                    }
                    if (warn.isNotEmpty()) Text(warn.joinToString(" · "), style = MBType.micro, color = MB.gold)
                    PanelActions {
                        PanelButton("رفض الطلب", MB.ink2) { rejecting = r }
                        PanelButton("موافقة وحذف الحساب", MB.red, filled = true) { approving = r }
                    }
                } else {
                    Text(Panel.whenTxt(r.pd("decidedAt")) + (if (r.ps("note").isEmpty()) "" else " · " + r.ps("note")),
                        style = MBType.micro, color = MB.ink3)
                }
            }
        }
    }

    approving?.let { r ->
        val bal = Math.abs(r.pd("balance")) > 0.0005
        ConfirmDialog("حذف الحساب نهائياً؟",
            "حذف حساب ${r.ps("name").ifEmpty { r.ps("phone") }} نهائياً؟" +
                (if (bal) "\nرصيده ${Panel.omr(r.pd("balance"))} سيسقط ولا يمكن استرجاعه." else "") + "\nلا يمكن التراجع.",
            confirmText = "موافقة وحذف", destructive = true, onConfirm = {
                scope.launch {
                    store.panelRun("تم حذف الحساب") {
                        Panel.post("api/admin/deletions", jsonOf("id" to r.ps("id"), "action" to "approve", "force" to bal))
                    }
                    load()
                }
            }, onDismiss = { approving = null })
    }
    rejecting?.let { r ->
        var note by remember(r) { mutableStateOf("") }
        PanelSheet("رفض طلب الحذف", { rejecting = null }) {
            MBField("السبب (يظهر في السجل)", note, { note = it })
            PrimaryCTA("رفض", {
                scope.launch {
                    store.panelRun("رُفض الطلب") {
                        Panel.post("api/admin/deletions", jsonOf("id" to r.ps("id"), "action" to "reject", "note" to note))
                    }
                    rejecting = null
                    load()
                }
            }, symbol = "xmark", tint = MB.red)
        }
    }
}
