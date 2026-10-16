package com.jaynak.app.features.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.jaynak.app.design.EmptyStateView
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBField
import com.jaynak.app.design.MBToggle
import com.jaynak.app.design.MBType
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SecondaryCTA
import com.jaynak.app.design.StatTile
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

private val wdDays = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")

/** السحوبات (أرباح المناديب) — نفس تبويب اللوحة. */
@Composable
fun AdminServerPayouts() {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var data by remember { mutableStateOf(JSONObject()) }
    var filter by remember { mutableStateOf("pending") }
    var payFor by remember { mutableStateOf<String?>(null) }
    var rejectFor by remember { mutableStateOf<String?>(null) }
    var payAll by remember { mutableStateOf(false) }
    var showCfg by remember { mutableStateOf(false) }
    var showTopups by remember { mutableStateOf(false) }
    suspend fun load() { data = Panel.get("api/admin/withdrawals") }

    val ws = data.pl("withdrawals")
    val cfg = data.po("cfg")
    val pend = ws.filter { it.ps("status") == "pending" }
    val nowSec = System.currentTimeMillis() / 1000.0
    val paidWeek = ws.filter { it.ps("status") == "paid" && it.pd("decidedAt") > nowSec - 7 * 86400 }
    val shown = ws.filter { it.ps("status") == filter }
    val days = (cfg.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: emptyList())
    val owner = store.currentAdmin?.role == com.jaynak.app.core.AdminRole.owner

    PanelScreen("السحوبات", pollMs = 8000, refresh = { load() }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("بانتظار التحويل", "${pend.size}", "hourglass", Modifier.weight(1f), tint = MB.gold)
            StatTile("إجمالي المطلوب", Panel.omr(pend.sumOf { it.pd("amount") }), "banknote.fill", Modifier.weight(1f), tint = MB.gold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("حُوِّل آخر 7 أيام", Panel.omr(paidWeek.sumOf { it.pd("amount") }), "checkmark.circle.fill", Modifier.weight(1f))
            StatTile("يوم السحب القادم", data.ps("nextPayout").ifEmpty { "—" }, "calendar", Modifier.weight(1f))
        }
        PanelCard {
            Text("أيام السحب: ${days.joinToString(" و ") { wdDays.getOrElse(it) { "?" } }} · الحد الأدنى: ${Panel.omr(cfg.pd("minAmount"))}",
                style = MBType.caption, color = MB.ink)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (owner) PanelButton("⚙ تعديل", MB.ink2) { showCfg = true }
                Spacer(Modifier.weight(1f))
                MBChip(if (cfg.pb("enabled", true)) "السحب مفعّل" else "السحب متوقف", tint = if (cfg.pb("enabled", true)) MB.green else MB.red, filled = true)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("pending" to "⏳ قيد التحويل", "paid" to "✅ تم", "rejected" to "❌ مرفوضة").forEach { (k, t) ->
                AdminChip("$t ${ws.count { it.ps("status") == k }}", filter == k) { filter = k }
            }
        }
        PanelActions {
            if (owner) PanelButton("💳 دفعات الشحن", MB.ink2) { showTopups = true }
            if (pend.isNotEmpty()) {
                PanelButton("⬇ تجهيز ملف التحويل", MB.ink2) {
                    scope.launch {
                        store.panelRun("") {
                            val csv = Panel.getText("api/admin/withdrawals/export")
                            val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/csv"; putExtra(android.content.Intent.EXTRA_TEXT, csv)
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "jaynak-payouts.csv")
                            }
                            ctx.startActivity(android.content.Intent.createChooser(i, "ملف التحويل").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }
                PanelButton("✔ تم تحويل الكل", filled = true) { payAll = true }
            }
        }
        if (shown.isEmpty()) EmptyStateView("tray", "لا توجد طلبات هنا", "")
        shown.forEach { w ->
            val b = w.po("bank")
            PanelCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(Panel.omr(w.pd("amount")), style = MBType.num(17, FontWeight.Bold), color = MB.green)
                    Spacer(Modifier.weight(1f))
                    Text(w.ps("phone"), style = MBType.num(11, FontWeight.Medium), color = MB.ink3)
                    Text(w.ps("name").ifEmpty { "مندوب" }, style = MBType.callout, color = MB.ink)
                }
                PanelKV("البنك", b.ps("bankName").ifEmpty { "—" })
                PanelKV("صاحب الحساب", b.ps("holder").ifEmpty { "—" })
                PanelKV("الحساب", b.ps("account").ifEmpty { "—" })
                Text("طُلب ${Panel.whenTxt(w.pd("createdAt"))}" +
                    (if (w.ps("status") == "paid") " · حُوِّل ${Panel.whenTxt(w.pd("decidedAt"))}" + (if (w.ps("ref").isNotEmpty()) " · رقم الحوالة: ${w.ps("ref")}" else "") else "") +
                    (if (w.ps("status") == "rejected") " · السبب: ${w.ps("reason").ifEmpty { "—" }}" else ""),
                    style = MBType.micro, color = MB.ink2)
                if (w.ps("status") == "pending") PanelActions {
                    PanelButton("رفض", MB.red) { rejectFor = w.ps("id") }
                    PanelButton("✔ تم التحويل", filled = true) { payFor = w.ps("id") }
                } else MBChip(if (w.ps("status") == "paid") "تم التحويل" else "مرفوض",
                    tint = if (w.ps("status") == "paid") MB.green else MB.red)
            }
        }
    }

    payFor?.let { id ->
        var ref by remember(id) { mutableStateOf("") }
        PanelSheet("تأكيد التحويل", { payFor = null }) {
            MBField("رقم الحوالة (اختياري)", ref, { ref = it }, isRTLText = false)
            PrimaryCTA("تم التحويل", {
                scope.launch {
                    val ok = store.panelRun("تم تسجيل التحويل") { Panel.post("api/admin/withdrawals", jsonOf("action" to "pay", "id" to id, "ref" to ref)) }
                    if (ok) { payFor = null; load() }
                }
            }, symbol = "checkmark")
        }
    }
    rejectFor?.let { id ->
        var reason by remember(id) { mutableStateOf("") }
        PanelSheet("رفض الطلب", { rejectFor = null }) {
            Text("سيعود المبلغ لرصيد المندوب.", style = MBType.micro, color = MB.ink2)
            MBField("السبب", reason, { reason = it }, placeholder = "مثل: رقم الحساب غير صحيح")
            PrimaryCTA("رفض", {
                scope.launch {
                    val ok = store.panelRun("تم الرفض وأُعيد المبلغ") { Panel.post("api/admin/withdrawals", jsonOf("action" to "reject", "id" to id, "reason" to reason)) }
                    if (ok) { rejectFor = null; load() }
                }
            }, symbol = "xmark", tint = MB.red)
        }
    }
    if (payAll) {
        var ref by remember { mutableStateOf("") }
        PanelSheet("تأكيد تحويل الكل", { payAll = false }) {
            Text("ستُعلَّم ${pend.size} طلبات كمحوَّلة. تأكد أنك رفعت الملف على البنك.", style = MBType.micro, color = MB.ink2)
            MBField("رقم الدفعة / مرجع (اختياري)", ref, { ref = it }, isRTLText = false)
            PrimaryCTA("نعم، تم التحويل", {
                scope.launch {
                    val ok = store.panelRun("تم تعليم الكل كمحوَّل") { Panel.post("api/admin/withdrawals", jsonOf("action" to "payall", "ref" to ref)) }
                    if (ok) { payAll = false; load() }
                }
            }, symbol = "checkmark")
        }
    }
    if (showCfg) {
        val sel = remember { androidx.compose.runtime.mutableStateListOf<Int>().also { it.addAll(days) } }
        var min by remember { mutableStateOf(Panel.fmt(cfg.pd("minAmount"))) }
        var on by remember { mutableStateOf(cfg.pb("enabled", true)) }
        PanelSheet("إعدادات السحب", { showCfg = false }) {
            Text("أيام السحب", style = MBType.caption, color = MB.ink2)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                wdDays.forEachIndexed { i, n -> AdminChip(n, i in sel) { if (i in sel) sel.remove(i) else sel.add(i) } }
            }
            MBField("الحد الأدنى للسحب (ر.ع)", min, { min = it }, keyboard = KeyboardType.Decimal, isRTLText = false)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MBToggle(on, { on = it })
                Spacer(Modifier.weight(1f))
                Text("السحب مفعّل (أوقفه مؤقتاً عند الحاجة)", style = MBType.callout, color = MB.ink)
            }
            PrimaryCTA("حفظ", {
                scope.launch {
                    val ok = store.panelRun("تم الحفظ") {
                        Panel.post("api/admin/payout-config", jsonOf("days" to jsonArr(sel.sorted()), "minAmount" to min, "enabled" to on))
                    }
                    if (ok) { showCfg = false; load() }
                }
            }, symbol = "checkmark")
        }
    }
    if (showTopups) TopupsSheet { showTopups = false }
}

@Composable
private fun TopupsSheet(onDismiss: () -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var r by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun load() { try { r = Panel.get("api/admin/payments"); error = null } catch (e: Exception) { error = e.message } }
    LaunchedEffect(Unit) { load() }
    val st = mapOf("PAID" to "مدفوعة", "PENDING" to "معلّقة", "PROCESSING" to "قيد المعالجة", "FAILED" to "فشلت", "CANCELLED" to "أُلغيت", "EXPIRED" to "انتهت")
    PanelSheet("دفعات الشحن الأخيرة", onDismiss) {
        Text("إن دفع مندوب ولم يصله المبلغ: اضغط «تحقق وأضف». وإن أُضيف المبلغ بالخطأ لمحفظة «زبون» بنفس رقمه فاضغط «نقل إلى رصيد المندوب».",
            style = MBType.micro, color = MB.ink2)
        if (error != null) EmptyStateView("exclamationmark.triangle.fill", "تعذّر التحميل", error ?: "")
        val d = r
        if (d == null && error == null) androidx.compose.material3.CircularProgressIndicator(color = MB.green)
        if (d != null && !d.pb("provider")) Text("بوّابة الدفع غير مفعّلة على السيرفر", style = MBType.micro, color = MB.red)
        d?.pl("payments")?.forEach { p ->
            val sure = p.ps("status") != "PAID" || !p.pb("credited")
            PanelCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(Panel.omr(p.pd("amount")), style = MBType.num(16, FontWeight.Bold), color = MB.ink)
                    Spacer(Modifier.weight(1f))
                    MBChip(if (p.ps("kind") == "driver") "شحن مندوب" else "شحن زبون", tint = if (p.ps("kind") == "driver") MB.green else MB.ink2)
                    Text(p.ps("name").ifEmpty { "بدون اسم" }, style = MBType.callout, color = MB.ink)
                }
                Text(p.ps("phone"), style = MBType.num(11, FontWeight.Medium), color = MB.ink3)
                Text("${Panel.whenTxt(p.pd("createdAt"))} · ${st[p.ps("status")] ?: p.ps("status")} · " +
                    (if (p.pb("credited")) (if (p.pb("moved")) "✔ نُقلت لرصيد المندوب" else "✔ أُضيفت للمحفظة") else "⚠ لم تُضَف بعد") +
                    " · ${p.ps("reference").take(14)}", style = MBType.micro, color = MB.ink2)
                PanelActions {
                    if (p.ps("kind") == "customer" && p.pb("isDriverPhone") && p.pb("credited") && !p.pb("moved"))
                        PanelButton("نقل إلى رصيد المندوب", MB.gold) {
                            scope.launch {
                                val ok = store.panelRun("") {
                                    val o = Panel.post("api/admin/payments/recover", jsonOf("reference" to p.ps("reference"), "action" to "move"))
                                    store.toast = com.jaynak.app.core.AppStore.Toast(text = "نُقل ${Panel.omr(o.pd("moved"))} لرصيد المندوب ✔", kind = com.jaynak.app.core.AppStore.Toast.Kind.success)
                                }
                                if (ok) load()
                            }
                        }
                    if (sure) PanelButton("تحقق وأضف", filled = true) {
                        scope.launch {
                            store.panelRun("") {
                                val o = Panel.post("api/admin/payments/recover", jsonOf("reference" to p.ps("reference"), "action" to "verify"))
                                store.toast = com.jaynak.app.core.AppStore.Toast(
                                    text = if (o.pb("credited")) "تمت إضافة المبلغ ✔" else if (o.ps("status") == "PAID") "مدفوعة ومضافة مسبقاً" else "حالة الدفع: ${o.ps("status").ifEmpty { "—" }}",
                                    kind = com.jaynak.app.core.AppStore.Toast.Kind.success)
                            }
                            load()
                        }
                    }
                }
            }
        }
        if (d != null && d.pl("payments").isEmpty()) Text("لا توجد دفعات", style = MBType.caption, color = MB.ink3)
    }
}
