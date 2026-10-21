package com.jaynak.app.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jaynak.app.design.EmptyStateView
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBType
import com.jaynak.app.design.PhotoViewer
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.RemoteImage
import com.jaynak.app.design.SecondaryCTA
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.StatTile
import com.jaynak.app.design.ConfirmDialog
import com.jaynak.app.design.pressable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** بطاقة طلب بنفس أزرار اللوحة: التفاصيل والصور، إلغاء، إعادة توزيع. */
@Composable
fun PanelOrderCard(order: JSONObject, onChange: suspend () -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var showDetail by remember { mutableStateOf(false) }
    var showReassign by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    val st = order.ps("status")
    val pickup = order.po("pickup").ps("name").ifEmpty { "—" }
    val dropoff = order.po("dropoff").ps("name").ifEmpty { "—" }

    PanelCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Panel.omr(order.po("price").pd("total")), style = MBType.num(14, androidx.compose.ui.text.font.FontWeight.SemiBold), color = MB.ink)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            MBChip(PanelStatus.title(st), tint = PanelStatus.tint(st), filled = true)
            Text("#" + order.ps("ref").ifEmpty { order.ps("id") }, style = MBType.callout, color = MB.ink)
        }
        Text("$pickup ← $dropoff", style = MBType.caption, color = MB.ink2)
        PanelKV("الزبون", order.ps("customerPhone").ifEmpty { "—" })
        if (order.ps("driverName").isNotEmpty() || order.ps("driverPhone").isNotEmpty())
            PanelKV("المندوب", "${order.ps("driverName")} ${order.ps("driverPhone")}")
        Text(Panel.whenTxt(order.pd("createdAt")), style = MBType.micro, color = MB.ink3)
        if (order.ps("note").isNotEmpty()) Text("ملاحظة: " + order.ps("note"), style = MBType.micro, color = MB.ink2)
        PanelActions {
            if (PanelStatus.isLive(st)) {
                PanelButton("إعادة توزيع", MB.ink2) { showReassign = true }
                PanelButton("إلغاء الطلب", MB.red) { confirmCancel = true }
            }
            PanelButton("التفاصيل والصور", filled = true) { showDetail = true }
        }
    }
    if (showDetail) PanelOrderDetail(order.ps("id"), onChange) { showDetail = false }
    if (showReassign) PanelReassignSheet(order.ps("id"), onChange) { showReassign = false }
    if (confirmCancel) ConfirmDialog("إلغاء هذا الطلب؟", confirmText = "إلغاء الطلب", destructive = true,
        onConfirm = {
            scope.launch {
                store.panelRun("تم إلغاء الطلب") { Panel.post("api/admin/cancel", jsonOf("orderID" to order.ps("id"))) }
                onChange()
            }
        }, onDismiss = { confirmCancel = false })
}

/** تفاصيل الطلب الكاملة (تتحدّث كل 4 ثوانٍ). */
@Composable
fun PanelOrderDetail(orderID: String, onChange: suspend () -> Unit, onDismiss: () -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var o by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var showReassign by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }

    LaunchedEffect(orderID) {
        while (true) {
            try { o = Panel.get("api/orders/$orderID"); error = null } catch (e: Exception) { if (o == null) error = e.message }
            delay(4000)
        }
    }

    PanelSheet("تفاصيل الطلب", onDismiss) {
        val d = o
        if (error != null) EmptyStateView("exclamationmark.triangle.fill", "تعذّر التحميل", error ?: "")
        else if (d == null) androidx.compose.material3.CircularProgressIndicator(color = MB.green)
        else {
            val st = d.ps("status"); val pr = d.po("price"); val drv = d.po("driver")
            PanelCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MBChip(PanelStatus.pay[d.ps("payment")] ?: d.ps("payment"), tint = MB.ink2)
                    MBChip(PanelStatus.kinds[d.ps("kind")] ?: d.ps("kind"), tint = MB.ink2)
                    MBChip(PanelStatus.title(st), tint = PanelStatus.tint(st), filled = true)
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    Text("#" + d.ps("ref").ifEmpty { orderID }, style = MBType.headline, color = MB.ink)
                }
                Text("أُنشئ " + Panel.whenTxt(d.pd("createdAt")) +
                    (if (d.pd("deliveredAt") > 0) " · سُلّم " + Panel.whenTxt(d.pd("deliveredAt")) else ""),
                    style = MBType.micro, color = MB.ink2)
            }
            PanelCard {
                Text("المسار", style = MBType.callout, color = MB.ink)
                StopRow("A", d.po("pickup"), "نقطة الاستلام")
                StopRow("B", d.po("dropoff"), "نقطة التسليم")
                if (d.ps("note").isNotEmpty()) Text("ملاحظة الزبون: " + d.ps("note"), style = MBType.caption, color = MB.ink)
            }
            PanelCard {
                Text("الزبون", style = MBType.callout, color = MB.ink)
                PanelKV("الاسم", d.ps("customerName").ifEmpty { "بدون اسم" })
                PhoneRow(d.ps("customerPhone"))
                if (d.ps("recipientPhone").isNotEmpty() || d.ps("recipientName").isNotEmpty()) {
                    PanelKV("المستلم", d.ps("recipientName"))
                    PhoneRow(d.ps("recipientPhone"))
                }
            }
            PanelCard {
                Text("المندوب", style = MBType.callout, color = MB.ink)
                if (d.ps("driverID").isEmpty() && drv.length() == 0) {
                    Text("لم يُسند لمندوب بعد (${if (d.ps("dispatchMode") == "nearby") "توزيع للأقرب" else d.ps("dispatchMode")})",
                        style = MBType.caption, color = MB.ink2)
                } else {
                    PanelKV("الاسم", d.ps("driverName").ifEmpty { drv.ps("name") })
                    PhoneRow(d.ps("driverPhone").ifEmpty { drv.ps("phone") })
                    if (drv.length() > 0) {
                        PanelKV("التقييم", if (drv.ps("rating").isEmpty()) "—" else "★ " + drv.ps("rating"))
                        PanelKV("طلبات مكتملة", "${drv.pi("completedOrders")}")
                    }
                }
            }
            PanelCard {
                Text("السعر", style = MBType.callout, color = MB.ink)
                listOf("الأساسي" to pr.pd("base"), "المسافة (${pr.ps("km")} كم)" to pr.pd("distance"), "الوقت" to pr.pd("time"),
                    "رسوم المنطقة" to pr.pd("zoneSurcharge"), "تعديل النوع" to pr.pd("kindAdjustment"), "ذروة" to pr.pd("surge"))
                    .filter { it.second != 0.0 }.forEach { PanelKV(it.first, Panel.omr(it.second)) }
                if (pr.pd("discount") != 0.0)
                    PanelKV("خصم" + (if (pr.ps("voucherTitle").isEmpty()) "" else " (${pr.ps("voucherTitle")})"), "−" + Panel.omr(pr.pd("discount")))
                PanelKV("الإجمالي", Panel.omr(pr.pd("total")))
                PanelKV("عمولة المنصة", Panel.omr(pr.pd("commission")))
                PanelKV("حصة المندوب", Panel.omr(pr.pd("driverPayout")))
            }
            PanelCard {
                Text("صور الطلب", style = MBType.callout, color = MB.ink)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Shot(d.ps("proofPickup"), "صورة الاستلام", d.pd("proofPickupAt"), Modifier.weight(1f)) { viewing = it }
                    Shot(d.ps("proofDelivery"), "صورة التسليم", d.pd("proofDeliveryAt"), Modifier.weight(1f)) { viewing = it }
                }
            }
            if (PanelStatus.isLive(st)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryCTA("إعادة توزيع", { showReassign = true }, Modifier.weight(1f))
                    PrimaryCTA("إلغاء الطلب", { confirmCancel = true }, Modifier.weight(1f), tint = MB.red)
                }
            }
        }
    }
    viewing?.let { PhotoViewer(it) { viewing = null } }
    if (showReassign) PanelReassignSheet(orderID, { onChange() }) { showReassign = false }
    if (confirmCancel) ConfirmDialog("إلغاء هذا الطلب؟", confirmText = "إلغاء الطلب", destructive = true,
        onConfirm = {
            scope.launch {
                store.panelRun("تم إلغاء الطلب") { Panel.post("api/admin/cancel", jsonOf("orderID" to orderID)) }
                onChange()
            }
        }, onDismiss = { confirmCancel = false })
}

@Composable
private fun PhoneRow(p: String) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (p.isNotEmpty()) {
            Text("واتساب", style = MBType.micro, color = MB.green, modifier = Modifier.pressable {
                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://wa.me/$p"))) }
            })
            Text("اتصال", style = MBType.micro, color = MB.green, modifier = Modifier.pressable {
                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL,
                    android.net.Uri.parse("tel:+$p"))) }
            })
        }
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        Text(p.ifEmpty { "—" }, style = MBType.num(12, androidx.compose.ui.text.font.FontWeight.Medium), color = MB.ink)
    }
}

@Composable
private fun StopRow(tag: String, p: JSONObject, fallback: String) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(p.ps("name").ifEmpty { fallback }, style = MBType.caption, color = MB.ink)
            if (p.ps("zone").isNotEmpty()) Text(p.ps("zone"), style = MBType.micro, color = MB.ink2)
            if (p.pd("lat") != 0.0) Text("فتح في الخرائط", style = MBType.micro, color = MB.green, modifier = Modifier.pressable {
                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://www.google.com/maps?q=${p.pd("lat")},${p.pd("lon")}"))) }
            })
        }
        Box(Modifier.size(24.dp).background(if (tag == "A") MB.green else MB.red, CircleShape), contentAlignment = Alignment.Center) {
            Text(tag, style = MBType.micro, color = androidx.compose.ui.graphics.Color.White)
        }
    }
}

@Composable
private fun Shot(token: String, cap: String, at: Double, modifier: Modifier, onOpen: (String) -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(12.dp)).background(MB.ink.copy(alpha = 0.06f))
            .then(if (token.isNotEmpty()) Modifier.pressable { onOpen(token) } else Modifier),
            contentAlignment = Alignment.Center) {
            if (token.isEmpty()) Text("لا توجد صورة بعد", style = MBType.micro, color = MB.ink3)
            else RemoteImage(token, Modifier.fillMaxWidth().height(130.dp))
        }
        Text(cap + (if (at > 0) " · " + Panel.whenTxt(at) else ""), style = MBType.micro, color = MB.ink2)
    }
}

/** إعادة توزيع الطلب على مندوب معيّن أو للجميع. */
@Composable
fun PanelReassignSheet(orderID: String, onChange: suspend () -> Unit, onDismiss: () -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var drivers by remember { mutableStateOf(listOf<JSONObject>()) }
    var pick by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { drivers = Panel.get("api/admin/people").pl("drivers").filter { it.ps("approval") == "approved" } }
    }
    @Composable
    fun row(id: String, title: String) {
        Row(Modifier.fillMaxWidth().background(if (pick == id) MB.green.copy(alpha = 0.12f) else MB.ink.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
            .pressable(0.99f) { pick = id }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (pick == id) "●" else "○", color = if (pick == id) MB.green else MB.ink3)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Text(title, style = MBType.callout, color = MB.ink)
        }
    }
    PanelSheet("إعادة توزيع الطلب", onDismiss) {
        row("", "إعادة للجميع (أي مندوب)")
        drivers.forEach { d -> row(d.ps("id"), "${d.ps("name").ifEmpty { d.ps("phone") }} — ${d.ps("phone")}") }
        PrimaryCTA("تأكيد", {
            busy = true
            scope.launch {
                val ok = store.panelRun("تمت إعادة التوزيع") {
                    Panel.post("api/admin/reassign", jsonOf("orderID" to orderID, "driverID" to pick))
                }
                busy = false
                if (ok) { onChange(); onDismiss() }
            }
        }, enabled = !busy)
    }
}

/** تبويب الطلبات — من السيرفر مثل اللوحة (بحث + فلتر حالة). */
@Composable
fun AdminServerOrders() {
    var orders by remember { mutableStateOf(listOf<JSONObject>()) }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val statuses = listOf("", "searching", "accepted", "pickedUp", "delivering", "delivered", "cancelled")
    suspend fun load() { orders = Panel.get("api/admin/orders").pl("orders"); loading = false }

    val q = query.trim().lowercase().replace("#", "")
    val rows = orders.filter { o ->
        if (status.isNotEmpty() && o.ps("status") != status) false
        else if (q.isEmpty()) true
        else listOf(o.ps("ref"), o.ps("customerPhone"), o.ps("recipientPhone"), o.ps("driverPhone"), o.ps("driverName"),
            o.po("pickup").ps("name"), o.po("dropoff").ps("name")).joinToString(" ").lowercase().contains(q)
    }
    PanelScreen("الطلبات", pollMs = 6000, refresh = { load() }) {
        AdminSearchField(query, { query = it }, "بحث: رقم الطلب، هاتف، اسم...")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            statuses.forEach { s -> AdminChip(if (s.isEmpty()) "كل الحالات" else PanelStatus.title(s), status == s) { status = s } }
        }
        if (loading && orders.isEmpty()) androidx.compose.material3.CircularProgressIndicator(color = MB.green)
        else if (rows.isEmpty()) EmptyStateView("shippingbox", "لا طلبات", "لا توجد طلبات بهذه المعايير")
        rows.forEach { o -> PanelOrderCard(o) { load() } }
    }
}

/** نظرة عامة — إحصاءات اليوم والطلبات الجارية والدفعة القادمة من السيرفر. */
@Composable
fun AdminServerOverview() {
    var d by remember { mutableStateOf(JSONObject()) }
    suspend fun load() { d = Panel.get("api/admin/overview") }
    val s = d.po("stats")
    PanelScreen("نظرة عامة", pollMs = 6000, refresh = { load() }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("مبيعات اليوم", Panel.omr(s.pd("revenueToday")), "banknote.fill", Modifier.weight(1f))
            StatTile("طلبات اليوم", "${s.pi("ordersToday")}", "shippingbox.fill", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("عمولة اليوم", Panel.omr(s.pd("commissionToday")), "percent", Modifier.weight(1f), tint = MB.gold)
            StatTile("طلبات جارية", "${s.pi("live")}", "bolt.fill", Modifier.weight(1f), tint = MB.gold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("متصلون الآن", "${s.pi("online")}", "dot.radiowaves.left.and.right", Modifier.weight(1f))
            StatTile("المناديب", "${s.pi("drivers")}", "car.fill", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("بانتظار القبول", "${s.pi("pending")}", "hourglass", Modifier.weight(1f), tint = MB.red)
            StatTile("الزبائن", "${s.pi("customers")}", "person.2.fill", Modifier.weight(1f))
        }
        SectionHeader("الطلبات الجارية", symbol = "bolt.fill")
        val live = d.pl("live")
        if (live.isEmpty()) EmptyStateView("tray", "لا طلبات جارية", "ستظهر الطلبات هنا فور وصولها")
        live.forEach { o -> PanelOrderCard(o) { load() } }
        val np = d.ps("nextPayout")
        if (np.isNotEmpty()) {
            SectionHeader("الدفعة القادمة", symbol = "calendar")
            PanelCard { Text(np, style = MBType.caption, color = MB.ink) }
        }
    }
}
