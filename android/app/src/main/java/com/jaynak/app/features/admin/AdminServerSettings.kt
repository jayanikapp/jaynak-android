package com.jaynak.app.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jaynak.app.design.ConfirmDialog
import com.jaynak.app.design.EmptyStateView
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBField
import com.jaynak.app.design.MBToggle
import com.jaynak.app.design.MBType
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SegmentedPicker
import com.jaynak.app.design.pressable
import kotlinx.coroutines.launch
import org.json.JSONObject

private val subs = listOf("price" to "التسعير", "points" to "النقاط", "rewards" to "المكافآت", "promos" to "أكواد الخصم", "team" to "الفريق", "depts" to "أقسام الدعم")

/** الإعدادات — نفس أقسام لوحة التحكم: التسعير، المكافآت، أكواد الخصم، الفريق، أقسام الدعم. */
@Composable
fun AdminServerSettings() {
    var sub by remember { mutableStateOf("price") }
    var set by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        try { set = Panel.get("api/admin/settings"); error = null } catch (e: Exception) { if (set == null) error = e.message }
    }
    PanelScreen("الإعدادات", refresh = { load() }) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            subs.forEach { (k, t) -> AdminChip(t, sub == k) { sub = k } }
        }
        val s = set
        if (error != null) EmptyStateView("lock.fill", "غير متاح", error ?: "")
        else if (s == null) androidx.compose.material3.CircularProgressIndicator(color = MB.green)
        else when (sub) {
            "price" -> PriceEditor(s) { load() }
            "points" -> PointsEditor(s) { load() }
            "rewards" -> RewardsEditor(s) { load() }
            "promos" -> PromosEditor(s) { load() }
            "team" -> TeamEditor(s) { load() }
            else -> DeptsEditor(s) { load() }
        }
    }
}

// ───────── التسعير ─────────

private val groups = listOf(
    "التسعيرة العادية (طلب مفتوح)" to listOf("baseFare" to "فتح العداد (ر.ع)", "perKm" to "سعر الكيلومتر", "perMinute" to "سعر الدقيقة", "minimumFare" to "الحد الأدنى للأجرة"),
    "التوصيل السريع (Express)" to listOf("expressBaseFare" to "فتح العداد", "expressPerKm" to "سعر الكيلومتر", "expressPerMinute" to "سعر الدقيقة", "expressCommissionPerRial" to "عمولة المنصة لكل ريال (0.10 = 10٪)"),
    "العمولات ورصيد المندوب" to listOf("fixedCommission" to "العمولة الثابتة للطلب المفتوح", "minCommission" to "الحد الأدنى للعمولة", "minEarningsBalance" to "أدنى رصيد يسمح للمندوب بالعمل", "earningsWarnBalance" to "رصيد التحذير"),
    "الحدود" to listOf("maxCustomerOrders" to "أقصى طلبات جارية للزبون", "maxDriverOpen" to "أقصى طلبات مفتوحة للمندوب", "maxDriverExpress" to "أقصى طلبات سريعة للمندوب"),
)

private class ZoneRow(val name: String, fee: Double, on: Boolean) {
    var fee by mutableStateOf(Panel.fmt(fee))
    var on by mutableStateOf(on)
}

@Composable
private fun PriceEditor(set: JSONObject, reload: suspend () -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val r = set.po("rules")
    val vals = remember(set) {
        mutableStateOf(buildMap<String, String> {
            groups.forEach { g -> g.second.forEach { (k, _) -> put(k, Panel.fmt(r.pd(k))) } }
            put("peakMultiplier", Panel.fmt(r.pd("peakMultiplier")))
        })
    }
    val peak = remember(set) { mutableStateListOf<Int>().also { l -> r.optJSONArray("peakHours")?.let { a -> for (i in 0 until a.length()) l.add(a.optInt(i)) } } }
    val zones = remember(set) {
        mutableStateListOf<ZoneRow>().also { l ->
            r.po("zones").let { z -> z.keys().forEach { k -> l.add(ZoneRow(k, z.optDouble(k, 0.0), true)) } }
            r.po("zonesOff").let { z -> z.keys().forEach { k -> l.add(ZoneRow(k, z.optDouble(k, 0.0), false)) } }
            l.sortBy { it.name }
        }
    }
    var newZone by remember { mutableStateOf("") }
    var newFee by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pvKm by remember { mutableStateOf("6") }
    var pvHour by remember { mutableStateOf("12") }
    var pvKind by remember { mutableStateOf(0) }
    var pv by remember { mutableStateOf<JSONObject?>(null) }
    val kinds = listOf("errand", "food", "parcel")

    suspend fun preview() {
        try {
            pv = Panel.post("api/admin/preview", jsonOf("km" to (Panel.num(pvKm) ?: 6.0), "hour" to (pvHour.toIntOrNull() ?: 12),
                "kind" to kinds[pvKind], "zone" to ""))
        } catch (e: Exception) {
            store.toast = com.jaynak.app.core.AppStore.Toast(text = e.message ?: "فشل", kind = com.jaynak.app.core.AppStore.Toast.Kind.error)
        }
    }

    groups.forEach { (title, fields) ->
        PanelCard {
            Text(title, style = MBType.callout, color = MB.ink)
            fields.forEach { (k, label) ->
                MBField(label, vals.value[k] ?: "", { v -> vals.value = vals.value + (k to v) }, keyboard = KeyboardType.Decimal, isRTLText = false)
            }
        }
    }
    PanelCard {
        Text("ساعات الذروة", style = MBType.callout, color = MB.ink)
        Text("اضغط على الساعات التي يُطبَّق فيها المضاعف.", style = MBType.micro, color = MB.ink2)
        (0 until 24).chunked(6).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { h ->
                    val on = h in peak
                    Box(Modifier.weight(1f).background(if (on) MB.green else MB.ink.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .pressable(0.96f) { if (on) peak.remove(h) else peak.add(h) }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center) {
                        Text("${if (h % 12 == 0) 12 else h % 12}${if (h < 12) "ص" else "م"}", style = MBType.micro,
                            color = if (on) androidx.compose.ui.graphics.Color.White else MB.ink2)
                    }
                }
            }
        }
        MBField("مضاعف الذروة (1.25 = +25٪)", vals.value["peakMultiplier"] ?: "", { v -> vals.value = vals.value + ("peakMultiplier" to v) },
            keyboard = KeyboardType.Decimal, isRTLText = false)
    }
    PanelCard {
        Text("رسوم المناطق", style = MBType.callout, color = MB.ink)
        if (zones.isEmpty()) Text("لا توجد مناطق بعد", style = MBType.caption, color = MB.ink3)
        zones.toList().forEach { z ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelButton("حذف", MB.red) { zones.remove(z) }
                Box(Modifier.weight(1f)) { MBField("ر.ع", z.fee, { z.fee = it }, keyboard = KeyboardType.Decimal, isRTLText = false) }
                MBToggle(z.on, { z.on = it })
                Text(z.name, style = MBType.caption, color = if (z.on) MB.ink else MB.ink3)
            }
        }
        MBField("اسم المنطقة (مثل: صحار)", newZone, { newZone = it })
        MBField("الرسم ر.ع", newFee, { newFee = it }, keyboard = KeyboardType.Decimal, isRTLText = false)
        PanelButton("+ إضافة", filled = true) {
            val n = newZone.trim(); val f = Panel.num(newFee)
            if (n.isEmpty() || f == null || f < 0) {
                store.toast = com.jaynak.app.core.AppStore.Toast(text = "أدخل اسم المنطقة والرسم", kind = com.jaynak.app.core.AppStore.Toast.Kind.error)
            } else { zones.removeAll { it.name == n }; zones.add(ZoneRow(n, f, true)); newZone = ""; newFee = "" }
        }
    }
    PrimaryCTA("حفظ كل التغييرات", {
        val b = JSONObject()
        vals.value.forEach { (k, v) -> b.put(k, v.trim()) }
        b.put("peakHours", jsonArr(peak.sorted()))
        val on = JSONObject(); val off = JSONObject()
        zones.forEach { z -> (if (z.on) on else off).put(z.name, Panel.num(z.fee) ?: 0.0) }
        b.put("zones", on); b.put("zonesOff", off)
        busy = true
        scope.launch {
            val ok = store.panelRun("تم حفظ التسعير وتطبيقه فوراً") { Panel.post("api/admin/rules", b) }
            busy = false
            if (ok) { runCatching { store.pullRules() }; reload(); preview() }
        }
    }, symbol = "checkmark", enabled = !busy)
    PanelCard {
        Text("معاينة السعر المباشرة", style = MBType.callout, color = MB.ink)
        Text("تحسب من القيم المحفوظة في السيرفر. احفظ ثم جرّب.", style = MBType.micro, color = MB.ink2)
        MBField("المسافة (كم)", pvKm, { pvKm = it }, keyboard = KeyboardType.Decimal, isRTLText = false)
        MBField("الساعة (0-23)", pvHour, { pvHour = it }, keyboard = KeyboardType.Number, isRTLText = false)
        SegmentedPicker(listOf("مشوار", "طعام", "طرد"), pvKind, { pvKind = it })
        PanelButton("احسب", filled = true) { scope.launch { preview() } }
        pv?.let { p ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("مفتوح" to p.po("open"), "سريع" to p.po("express")).forEach { (t, q) ->
                    Column(Modifier.weight(1f).background(MB.ink.copy(alpha = 0.05f), RoundedCornerShape(12.dp)).padding(10.dp),
                        horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t, style = MBType.caption, color = MB.green)
                        PanelKV("المسافة", "${q.ps("km")} كم · ${q.ps("minutes")} د")
                        PanelKV("فتح العداد", Panel.omr(q.pd("base")))
                        PanelKV("المسافة", Panel.omr(q.pd("distance")))
                        PanelKV("الوقت", Panel.omr(q.pd("time")))
                        if (q.pd("zoneSurcharge") != 0.0) PanelKV("المنطقة", Panel.omr(q.pd("zoneSurcharge")))
                        if (q.pd("surge") != 0.0) PanelKV("الذروة", Panel.omr(q.pd("surge")))
                        PanelKV("الإجمالي", Panel.omr(q.pd("total")))
                        PanelKV("العمولة", Panel.omr(q.pd("commission")))
                        PanelKV("ربح المندوب", Panel.omr(q.pd("driverPayout")))
                    }
                }
            }
        }
    }
}

// ───────── المكافآت ─────────

private val rewardKinds = mapOf("percentOff" to "خصم نسبة ٪", "fixedOff" to "خصم مبلغ ثابت", "walletCredit" to "رصيد للمحفظة", "freeDelivery" to "توصيل مجاني")

private data class RewardDraft(
    val rid: String = "", val title: String = "", val detail: String = "", val kind: String = "percentOff",
    val value: String = "10", val maxDiscount: String = "0.5", val cost: String = "100", val enabled: Boolean = true,
)

@Composable
private fun RewardsEditor(set: JSONObject, reload: suspend () -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<RewardDraft?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val list = set.pl("rewards")

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PanelButton("+ مكافأة جديدة", filled = true) { editing = RewardDraft() }
        if (set.pb("rewardsCustom")) PanelButton("استرجاع الافتراضي", MB.ink2) { confirmReset = true }
    }
    Text("هذه المكافآت تظهر للزبائن في متجر النقاط داخل التطبيق ويستبدلون نقاطهم بها.", style = MBType.micro, color = MB.ink2)
    if (list.isEmpty()) EmptyStateView("gift", "لا توجد مكافآت", "")
    list.forEach { r ->
        PanelCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MBToggle(r.pb("enabled", true), { on ->
                    scope.launch {
                        store.panelRun(if (on) "فُعّلت المكافأة" else "عُطّلت المكافأة") {
                            Panel.post("api/admin/rewards", jsonOf("reward" to JSONObject(r.toString()).put("enabled", on)))
                        }
                        reload()
                    }
                })
                Spacer(Modifier.weight(1f))
                MBChip(rewardKinds[r.ps("kind")] ?: r.ps("kind"), tint = MB.ink2)
                Text(r.ps("title"), style = MBType.callout, color = MB.ink)
            }
            if (r.ps("detail").isNotEmpty()) Text(r.ps("detail"), style = MBType.micro, color = MB.ink2)
            PanelKV("القيمة", when (r.ps("kind")) {
                "percentOff" -> "${Math.round(r.pd("value") * 100)}٪ حتى ${Panel.omr(r.pd("maxDiscount"))}"
                "freeDelivery" -> "حتى ${Panel.omr(r.pd("maxDiscount"))}"
                else -> Panel.omr(r.pd("value"))
            })
            PanelKV("التكلفة", "${r.pi("cost")} نقطة")
            PanelKV("استُبدلت", "${r.pi("redeemed")} مرة")
            PanelActions {
                PanelButton("حذف", MB.red) {
                    scope.launch {
                        store.panelRun("تم الحذف") { Panel.post("api/admin/rewards", jsonOf("action" to "delete", "id" to r.ps("id"))) }
                        reload()
                    }
                }
                PanelButton("تعديل") {
                    val k = r.ps("kind")
                    editing = RewardDraft(r.ps("id"), r.ps("title"), r.ps("detail"), k,
                        if (k == "percentOff") Math.round(r.pd("value") * 100).toString() else Panel.fmt(r.pd("value")),
                        Panel.fmt(r.pd("maxDiscount")), r.pi("cost").toString(), r.pb("enabled", true))
                }
            }
        }
    }

    editing?.let { d0 ->
        var d by remember(d0) { mutableStateOf(d0) }
        var busy by remember(d0) { mutableStateOf(false) }
        PanelSheet(if (d.rid.isEmpty()) "مكافأة جديدة" else "تعديل المكافأة", { editing = null }) {
            MBField("العنوان", d.title, { d = d.copy(title = it) })
            MBField("وصف قصير", d.detail, { d = d.copy(detail = it) })
            Text("النوع", style = MBType.caption, color = MB.ink2)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rewardKinds.forEach { (k, t) -> AdminChip(t, d.kind == k) { d = d.copy(kind = k) } }
            }
            if (d.kind != "freeDelivery")
                MBField(if (d.kind == "percentOff") "نسبة الخصم ٪ (مثل 10)" else "المبلغ (ر.ع)", d.value, { d = d.copy(value = it) },
                    keyboard = KeyboardType.Decimal, isRTLText = false)
            if (d.kind == "percentOff" || d.kind == "freeDelivery")
                MBField("أقصى خصم (ر.ع)", d.maxDiscount, { d = d.copy(maxDiscount = it) }, keyboard = KeyboardType.Decimal, isRTLText = false)
            MBField("التكلفة بالنقاط", d.cost, { d = d.copy(cost = it) }, keyboard = KeyboardType.Number, isRTLText = false)
            PrimaryCTA("حفظ", {
                var v = Panel.num(d.value) ?: 0.0
                var mx = Panel.num(d.maxDiscount) ?: 0.0
                when (d.kind) { "percentOff" -> v /= 100; "freeDelivery" -> v = 1.0; else -> mx = v }
                busy = true
                scope.launch {
                    val ok = store.panelRun("تم الحفظ") {
                        Panel.post("api/admin/rewards", jsonOf("reward" to jsonOf("id" to d.rid, "title" to d.title, "detail" to d.detail,
                            "kind" to d.kind, "value" to v, "maxDiscount" to mx, "cost" to d.cost, "enabled" to d.enabled)))
                    }
                    busy = false
                    if (ok) { reload(); editing = null }
                }
            }, symbol = "checkmark", enabled = !busy)
        }
    }
    if (confirmReset) ConfirmDialog("استرجاع مكافآت المتجر الافتراضية؟ ستُحذف تعديلاتك.", confirmText = "استرجاع", destructive = true,
        onConfirm = { scope.launch { store.panelRun("تم الاسترجاع") { Panel.post("api/admin/rewards", jsonOf("action" to "reset")) }; reload() } },
        onDismiss = { confirmReset = false })
}

// ───────── أكواد الخصم ─────────

private val promoKinds = mapOf("percentOff" to "خصم نسبة ٪", "fixedOff" to "خصم مبلغ ثابت", "freeDelivery" to "توصيل مجاني")

private data class PromoDraft(
    val isNew: Boolean = true, val code: String = "", val kind: String = "percentOff", val value: String = "15",
    val maxDiscount: String = "1", val maxUses: String = "100", val perCustomer: String = "1", val minOrder: String = "0",
    val expires: String = "", val note: String = "", val enabled: Boolean = true,
)

@Composable
private fun PromosEditor(set: JSONObject, reload: suspend () -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<PromoDraft?>(null) }
    val list = set.pl("promos")
    val nowSec = System.currentTimeMillis() / 1000.0

    PanelButton("+ كود جديد", filled = true) { editing = PromoDraft() }
    Text("يُدخل الزبون الكود عند طلب التوصيل ويُطبَّق الخصم من السيرفر فعلاً على السعر.", style = MBType.micro, color = MB.ink2)
    if (list.isEmpty()) EmptyStateView("tag", "لا توجد أكواد بعد", "")
    list.forEach { p ->
        PanelCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MBToggle(p.pb("enabled", true), {
                    scope.launch {
                        store.panelRun("تم التحديث") { Panel.post("api/admin/promos", jsonOf("action" to "toggle", "code" to p.ps("code"))) }
                        reload()
                    }
                })
                Spacer(Modifier.weight(1f))
                if (p.pd("expiresAt") > 0) Text(if (p.pd("expiresAt") < nowSec) "منتهي" else "ينتهي " + Panel.whenTxt(p.pd("expiresAt")),
                    style = MBType.micro, color = if (p.pd("expiresAt") < nowSec) MB.red else MB.ink2)
                Text(p.ps("code"), style = MBType.num(17, FontWeight.Bold), color = MB.ink)
            }
            var desc = when (p.ps("kind")) {
                "percentOff" -> "${Math.round(p.pd("value") * 100)}٪ حتى ${Panel.omr(p.pd("maxDiscount"))}"
                "fixedOff" -> Panel.omr(p.pd("value"))
                else -> "توصيل مجاني حتى ${Panel.omr(p.pd("maxDiscount"))}"
            }
            if (p.pd("minOrder") > 0) desc += " · حد أدنى " + Panel.omr(p.pd("minOrder"))
            if (p.ps("note").isNotEmpty()) desc += " · " + p.ps("note")
            Text(desc, style = MBType.caption, color = MB.ink2)
            PanelKV("الاستخدام", "${p.pi("uses")} / ${if (p.pi("maxUses") > 0) p.pi("maxUses").toString() else "∞"}")
            PanelKV("لكل زبون", if (p.pi("perCustomer") > 0) "${p.pi("perCustomer")}" else "بلا حد")
            PanelKV("إجمالي الخصم الممنوح", Panel.omr(p.pd("savedTotal")))
            PanelActions {
                PanelButton("حذف", MB.red) {
                    scope.launch {
                        store.panelRun("تم الحذف") { Panel.post("api/admin/promos", jsonOf("action" to "delete", "code" to p.ps("code"))) }
                        reload()
                    }
                }
                PanelButton("تعديل") {
                    val k = p.ps("kind")
                    editing = PromoDraft(false, p.ps("code"), k,
                        if (k == "percentOff") Math.round(p.pd("value") * 100).toString() else Panel.fmt(p.pd("value")),
                        Panel.fmt(p.pd("maxDiscount")), p.pi("maxUses").toString(), p.pi("perCustomer").toString(), Panel.fmt(p.pd("minOrder")),
                        if (p.pd("expiresAt") > 0) java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date((p.pd("expiresAt") * 1000).toLong())) else "",
                        p.ps("note"), p.pb("enabled", true))
                }
            }
        }
    }

    editing?.let { d0 ->
        var d by remember(d0) { mutableStateOf(d0) }
        var busy by remember(d0) { mutableStateOf(false) }
        PanelSheet(if (d.isNew) "كود خصم جديد" else "تعديل الكود", { editing = null }) {
            MBField("الكود (إنجليزي/أرقام)", d.code, { if (d.isNew) d = d.copy(code = it.uppercase()) }, placeholder = "WELCOME15", isRTLText = false)
            Text("النوع", style = MBType.caption, color = MB.ink2)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                promoKinds.forEach { (k, t) -> AdminChip(t, d.kind == k) { d = d.copy(kind = k) } }
            }
            if (d.kind != "freeDelivery")
                MBField(if (d.kind == "percentOff") "نسبة الخصم ٪ (مثل 15)" else "مبلغ الخصم (ر.ع)", d.value, { d = d.copy(value = it) },
                    keyboard = KeyboardType.Decimal, isRTLText = false)
            if (d.kind != "fixedOff")
                MBField("أقصى خصم (ر.ع)", d.maxDiscount, { d = d.copy(maxDiscount = it) }, keyboard = KeyboardType.Decimal, isRTLText = false)
            MBField("عدد الاستخدامات الكلي (0 = بلا حد)", d.maxUses, { d = d.copy(maxUses = it) }, keyboard = KeyboardType.Number, isRTLText = false)
            MBField("لكل زبون (0 = بلا حد)", d.perCustomer, { d = d.copy(perCustomer = it) }, keyboard = KeyboardType.Number, isRTLText = false)
            MBField("حد أدنى للطلب (ر.ع)", d.minOrder, { d = d.copy(minOrder = it) }, keyboard = KeyboardType.Decimal, isRTLText = false)
            MBField("تاريخ الانتهاء (YYYY-MM-DD، فارغ = بلا انتهاء)", d.expires, { d = d.copy(expires = it) }, isRTLText = false)
            MBField("ملاحظة داخلية", d.note, { d = d.copy(note = it) })
            PrimaryCTA("حفظ", {
                var v = Panel.num(d.value) ?: 0.0
                var mx = Panel.num(d.maxDiscount) ?: 0.0
                when (d.kind) { "percentOff" -> v /= 100; "freeDelivery" -> v = 1.0; else -> mx = v }
                val exp = runCatching {
                    if (d.expires.isBlank()) 0.0
                    else (java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).parse(d.expires.trim() + " 23:59:59")!!.time / 1000.0)
                }.getOrDefault(0.0)
                busy = true
                scope.launch {
                    val ok = store.panelRun("تم الحفظ") {
                        Panel.post("api/admin/promos", jsonOf("promo" to jsonOf("code" to d.code, "kind" to d.kind, "value" to v, "maxDiscount" to mx,
                            "maxUses" to d.maxUses, "perCustomer" to d.perCustomer, "minOrder" to d.minOrder, "expiresAt" to exp,
                            "note" to d.note, "enabled" to d.enabled)))
                    }
                    busy = false
                    if (ok) { reload(); editing = null }
                }
            }, symbol = "checkmark", enabled = !busy && d.code.length >= 3)
        }
    }
}

// ───────── الفريق والصلاحيات ─────────

private data class TeamDraft(val isNew: Boolean, val phone: String, val name: String, val tabs: Set<String>, val depts: Set<String>, val active: Boolean)

@Composable
private fun TeamEditor(set: JSONObject, reload: suspend () -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<TeamDraft?>(null) }
    val team = set.pl("team")
    val titles = set.po("tabTitles")
    val tabKeys = titles.keys().asSequence().toList()
    val deptList = set.pstrs("depts")

    Text("المالكون", style = MBType.callout, color = MB.ink)
    set.pstrs("owners").forEach { p ->
        PanelCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("كل الصلاحيات", style = MBType.micro, color = MB.ink2)
                Spacer(Modifier.weight(1f))
                MBChip("مالك", tint = MB.gold, filled = true)
                Text("+$p", style = MBType.num(13, FontWeight.Medium), color = MB.ink)
            }
        }
    }
    Text("أعضاء الفريق", style = MBType.callout, color = MB.ink)
    PanelButton("+ إضافة عضو", filled = true) { editing = TeamDraft(true, "", "", setOf("over"), emptySet(), true) }
    if (team.isEmpty()) EmptyStateView("person.2", "لا يوجد أعضاء بعد", "")
    team.forEach { m ->
        PanelCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MBToggle(m.pb("active", true), { on ->
                    scope.launch {
                        store.panelRun("تم التحديث") {
                            Panel.post("api/admin/team", jsonOf("phone" to m.ps("phone"), "name" to m.ps("name"), "role" to m.ps("role"),
                                "tabs" to jsonArr(m.pstrs("tabs").ifEmpty { tabKeys }), "depts" to jsonArr(m.pstrs("depts")), "active" to on))
                        }
                        reload()
                    }
                })
                Spacer(Modifier.weight(1f))
                Text("+${m.ps("phone")}", style = MBType.num(12, FontWeight.Medium), color = MB.ink3)
                Text(m.ps("name").ifEmpty { "بدون اسم" }, style = MBType.callout, color = MB.ink)
            }
            Text(m.pstrs("tabs").joinToString(" · ") { titles.optString(it, it) }.ifEmpty { "أقسام حسب الدور القديم" }, style = MBType.micro, color = MB.ink2)
            if (m.pstrs("depts").isNotEmpty()) Text("أقسام الدعم: " + m.pstrs("depts").joinToString("، "), style = MBType.micro, color = MB.ink3)
            PanelActions {
                PanelButton("إزالة", MB.red) {
                    scope.launch {
                        store.panelRun("تمت الإزالة") { Panel.post("api/admin/team", jsonOf("action" to "remove", "phone" to m.ps("phone"))) }
                        reload()
                    }
                }
                PanelButton("تعديل") { editing = TeamDraft(false, m.ps("phone"), m.ps("name"), m.pstrs("tabs").toSet(), m.pstrs("depts").toSet(), m.pb("active", true)) }
            }
        }
    }
    Text("يدخل العضو من نفس صفحة الدخول برقم جواله ورمز التحقق، فتظهر له الأقسام التي حددتها له فقط.", style = MBType.micro, color = MB.ink3)

    editing?.let { d0 ->
        var d by remember(d0) { mutableStateOf(d0) }
        var busy by remember(d0) { mutableStateOf(false) }
        PanelSheet(if (d.isNew) "إضافة رقم للإدارة" else "تعديل صلاحيات رقم", { editing = null }) {
            MBField("رقم الجوال (8 أرقام)", d.phone.removePrefix("968"), { if (d.isNew) d = d.copy(phone = it) }, keyboard = KeyboardType.Number, isRTLText = false)
            MBField("الاسم", d.name, { d = d.copy(name = it) })
            Text("الأقسام التي تظهر له", style = MBType.caption, color = MB.ink2)
            PanelButton("تحديد الكل", MB.ink2) { d = d.copy(tabs = tabKeys.toSet()) }
            tabKeys.forEach { k ->
                val on = k in d.tabs
                Row(Modifier.fillMaxWidth().background(if (on) MB.green.copy(alpha = 0.12f) else MB.ink.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                    .pressable(0.99f) { d = d.copy(tabs = if (on) d.tabs - k else d.tabs + k) }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (on) "☑" else "☐", color = if (on) MB.green else MB.ink3)
                    Spacer(Modifier.weight(1f))
                    Text(titles.optString(k, k), style = MBType.callout, color = MB.ink)
                }
            }
            if (deptList.isNotEmpty()) {
                Text("أقسام الدعم المسؤول عنها (بدون اختيار = لا يرى التذاكر المحوّلة أبداً)", style = MBType.caption, color = MB.ink2)
                deptList.forEach { k ->
                    val on = k in d.depts
                    Row(Modifier.fillMaxWidth().background(if (on) MB.green.copy(alpha = 0.12f) else MB.ink.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                        .pressable(0.99f) { d = d.copy(depts = if (on) d.depts - k else d.depts + k) }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (on) "☑" else "☐", color = if (on) MB.green else MB.ink3)
                        Spacer(Modifier.weight(1f))
                        Text(k, style = MBType.callout, color = MB.ink)
                    }
                }
            }
            PrimaryCTA("حفظ", {
                busy = true
                scope.launch {
                    val ok = store.panelRun("تم الحفظ") {
                        Panel.post("api/admin/team", jsonOf("phone" to d.phone, "name" to d.name, "tabs" to jsonArr(tabKeys.filter { it in d.tabs }),
                            "depts" to jsonArr(d.depts.toList()), "active" to d.active))
                    }
                    busy = false
                    if (ok) { reload(); editing = null }
                }
            }, symbol = "checkmark", enabled = !busy && d.phone.filter { it.isDigit() }.length >= 8 && d.tabs.isNotEmpty())
        }
    }
}

// ───────── أقسام الدعم ─────────

@Composable
private fun DeptsEditor(set: JSONObject, reload: suspend () -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var renameTo by remember { mutableStateOf("") }
    val depts = set.pstrs("depts")
    val team = set.pl("team")

    Text("أقسام تُحوَّل لها تذاكر الدعم (مثل: المالية، التقنية، الشكاوى). عيّن لكل عضو قسمه من «الفريق»، فيرى التذاكر المحوّلة لقسمه فقط.",
        style = MBType.micro, color = MB.ink2)
    MBField("اسم القسم الجديد", newName, { newName = it })
    PanelButton("+ إضافة قسم", filled = true) {
        val n = newName.trim()
        if (n.isNotEmpty()) scope.launch {
            store.panelRun("تمت إضافة القسم") { Panel.post("api/admin/depts", jsonOf("action" to "add", "name" to n)) }
            newName = ""; reload()
        }
    }
    if (depts.isEmpty()) EmptyStateView("folder", "لا توجد أقسام بعد", "")
    depts.forEach { d ->
        val who = team.filter { d in it.pstrs("depts") }.map { it.ps("name").ifEmpty { it.ps("phone") } }
        PanelCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelButton("حذف", MB.red) {
                    scope.launch {
                        store.panelRun("تم حذف القسم") { Panel.post("api/admin/depts", jsonOf("action" to "delete", "name" to d)) }
                        reload()
                    }
                }
                PanelButton("إعادة تسمية", MB.ink2) { renaming = d; renameTo = d }
                Spacer(Modifier.weight(1f))
                Text("🗂️ $d", style = MBType.callout, color = MB.ink)
            }
            Text(if (who.isEmpty()) "لا أحد معيّن — لا يراه إلا المالك" else "المسؤولون: " + who.joinToString("، "), style = MBType.micro, color = MB.ink2)
        }
    }
    renaming?.let { old ->
        PanelSheet("الاسم الجديد للقسم", { renaming = null }) {
            MBField("الاسم", renameTo, { renameTo = it })
            PrimaryCTA("حفظ", {
                val to = renameTo.trim()
                if (to.isNotEmpty()) scope.launch {
                    store.panelRun("تمت إعادة التسمية") { Panel.post("api/admin/depts", jsonOf("action" to "rename", "name" to old, "to" to to)) }
                    renaming = null; reload()
                }
            }, symbol = "checkmark")
        }
    }
}


// ───────── النقاط (خصم وإضافة عند القبول/الرفض/التسليم) — نفس تبويب ⭐ في اللوحة ─────────

private val pointGroups = listOf(
    Triple("customer", "👤 العميل", listOf(
        "cancelBeforeAccept" to "يُخصم عند إلغاء الطلب قبل قبول مندوب",
        "cancelAfterAccept" to "يُخصم عند الإلغاء بعد القبول",
        "delivered" to "يُضاف عند تسليم الطلب",
        "perRial" to "نقاط إضافية لكل ريال من قيمة الطلب",
        "suspendAt" to "إيقاف الحساب عند نقاط سالبة (0 = بلا إيقاف)")),
    Triple("driver", "🛵 المندوب", listOf(
        "declineBeforeAccept" to "يُخصم عند رفض الطلب قبل القبول",
        "cancelAfterAccept" to "يُخصم عند الإلغاء/الرفض بعد القبول",
        "accept" to "يُضاف عند قبول الطلب",
        "delivered" to "يُضاف عند تسليم الطلب",
        "suspendAt" to "إيقاف الحساب عند نقاط سالبة (0 = بلا إيقاف)")),
)

@Composable
private fun PointsEditor(set: JSONObject, reload: suspend () -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val rules = set.po("pointsRules")
    var enabled by remember { mutableStateOf(rules.pb("enabled", true)) }
    val vals = remember {
        mutableStateMapOf<String, String>().also { m ->
            pointGroups.forEach { (g, _, fs) -> fs.forEach { (k, _) -> m["$g.$k"] = rules.po(g).pi(k).toString() } }
        }
    }
    var log by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    suspend fun loadLog() {
        runCatching { Panel.get("api/admin/points-log?limit=40") }.getOrNull()?.let { log = it.pl("log") }
    }
    LaunchedEffect(Unit) { loadLog() }

    PanelCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MBToggle(enabled, { enabled = it })
            Spacer(Modifier.weight(1f))
            Text("تفعيل نظام النقاط", style = MBType.callout, color = MB.ink)
        }
        Text("القيم تُطبَّق من السيرفر فوراً على التطبيقين. الخصم لا ينزل برصيد النقاط تحت الصفر.", style = MBType.micro, color = MB.ink2)
    }
    pointGroups.forEach { (g, title, fs) ->
        PanelCard {
            Text(title, style = MBType.callout, color = MB.ink)
            fs.forEach { (k, label) ->
                MBField(label, vals["$g.$k"] ?: "", { vals["$g.$k"] = it }, keyboard = KeyboardType.Number)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PanelButton("استرجاع الافتراضي", MB.ink2) {
            scope.launch {
                store.panelRun("تم استرجاع القيم الافتراضية") { Panel.post("api/admin/points-rules", jsonOf("action" to "reset")) }
                reload(); loadLog()
            }
        }
        PanelButton("حفظ", filled = true) {
            val body = JSONObject().put("enabled", enabled)
            for ((g, _, fs) in pointGroups) {
                val sub = JSONObject()
                for ((k, label) in fs) {
                    val v = (vals["$g.$k"] ?: "").trim().toIntOrNull()
                    if (v == null || v < 0) {
                        store.toast = com.jaynak.app.core.AppStore.Toast(text = "أدخل رقماً صحيحاً (0 أو أكثر) في: $label", kind = com.jaynak.app.core.AppStore.Toast.Kind.error)
                        return@PanelButton
                    }
                    sub.put(k, v)
                }
                body.put(g, sub)
            }
            scope.launch {
                store.panelRun("تم حفظ قواعد النقاط — تُطبَّق فوراً") { Panel.post("api/admin/points-rules", body) }
                reload(); loadLog()
            }
        }
    }
    PanelCard {
        Text("آخر حركات النقاط", style = MBType.callout, color = MB.ink)
        if (log.isEmpty()) Text("لا توجد حركات بعد", style = MBType.caption, color = MB.ink3)
        log.take(30).forEach { r ->
            val d = r.pi("delta")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text((if (d > 0) "+" else "") + d, style = MBType.caption, color = if (d >= 0) MB.green else MB.red, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text((if (r.ps("role") == "driver") "مندوب " else "عميل ") + r.ps("phone"), style = MBType.caption, color = MB.ink)
                    Text(r.ps("label"), style = MBType.micro, color = MB.ink2)
                }
            }
        }
    }
}
