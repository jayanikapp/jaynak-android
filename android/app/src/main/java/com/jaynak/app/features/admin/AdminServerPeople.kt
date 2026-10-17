package com.jaynak.app.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jaynak.app.design.ConfirmDialog
import com.jaynak.app.design.EmptyStateView
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBField
import com.jaynak.app.design.MBType
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SecondaryCTA
import com.jaynak.app.design.StatTile
import com.jaynak.app.design.pressable
import kotlinx.coroutines.launch
import org.json.JSONObject

/** المناديب / الزبائن — نفس بيانات وأزرار لوحة التحكم (من السيرفر مباشرة). */
@Composable
fun AdminServerPeople(drivers: Boolean) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf(JSONObject()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    var adjust by remember { mutableStateOf<JSONObject?>(null) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var docs by remember { mutableStateOf<String?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var delTarget by remember { mutableStateOf<JSONObject?>(null) }
    var delForce by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    suspend fun load() { data = Panel.get("api/admin/people"); loading = false }

    val q = query.trim().lowercase()
    val rows = data.pl(if (drivers) "drivers" else "customers").filter { u ->
        if (drivers && filter.isNotEmpty() && u.ps("approval") != filter) false
        else if (q.isEmpty()) true
        else listOf(u.ps("name"), u.ps("phone"), u.ps("zone")).joinToString(" ").lowercase().contains(q)
    }
    val approvals = listOf("" to "الكل", "pending" to "بانتظار القبول", "approved" to "مقبول", "rejected" to "مرفوض", "suspended" to "موقوف")

    PanelScreen(if (drivers) "المناديب" else "الزبائن", pollMs = 8000, refresh = { load() }) {
        if (!drivers) StatTile("المحفظة الأساسية للمنصة", Panel.omr(data.pd("platformWallet")), "building.columns.fill")
        AdminSearchField(query, { query = it }, "بحث بالاسم أو الهاتف")
        if (drivers) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            approvals.forEach { a -> AdminChip(a.second, filter == a.first) { filter = a.first } }
        }
        SecondaryCTA(if (drivers) "إضافة مندوب" else "إضافة زبون", { showNew = true }, symbol = "person.badge.plus")
        if (loading && rows.isEmpty()) androidx.compose.material3.CircularProgressIndicator(color = MB.green)
        else if (rows.isEmpty()) EmptyStateView("person.2", "لا نتائج", if (drivers) "لا يوجد مناديب" else "لا يوجد زبائن")
        rows.forEach { u ->
            PanelCard {
                val ap = u.ps("approval")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(u.ps("phone"), style = MBType.num(11, androidx.compose.ui.text.font.FontWeight.Medium), color = MB.ink3)
                    Spacer(Modifier.weight(1f))
                    if (drivers) {
                        MBChip(if (u.ps("presence") == "online") "متصل" else "غير متصل",
                            tint = if (u.ps("presence") == "online") MB.green else MB.ink3)
                        val t = mapOf("approved" to ("مقبول" to MB.green), "pending" to ("بانتظار القبول" to MB.gold),
                            "rejected" to ("مرفوض" to MB.red), "suspended" to ("موقوف" to MB.red))[ap] ?: (ap to MB.ink3)
                        MBChip(t.first, tint = t.second, filled = true)
                    }
                    Text(u.ps("name").ifEmpty { "بدون اسم" }, style = MBType.callout, color = MB.ink)
                }
                if (drivers) {
                    PanelKV("المنطقة", u.ps("zone").ifEmpty { "—" })
                    PanelKV("الرتبة", "${u.ps("rank").ifEmpty { "—" }} (${u.pi("points")} نقطة)")
                    PanelKV("رصيد الأرباح", Panel.omr(u.pd("earningsBalance")))
                    PanelKV("طلبات مكتملة", "${u.pi("completedOrders")}")
                    if (u.ps("civilID").isNotEmpty()) PanelKV("الرقم المدني", u.ps("civilID"))
                    PanelActions {
                        listOf("approved", "rejected", "suspended").filter { it != ap }.forEach { s ->
                            PanelButton(mapOf("approved" to "قبول", "rejected" to "رفض", "suspended" to "إيقاف")[s] ?: s,
                                if (s == "approved") MB.green else MB.red, filled = s == "approved") {
                                pending = Triple(u.ps("id"), u.ps("name"), s)
                            }
                        }
                        PanelButton("التفاصيل") { detail = u }
                        PanelButton("المستندات", MB.ink2) { docs = u.ps("phone") }
                        PanelButton("تعديل الرصيد/النقاط", MB.gold) { adjust = u }
                        PanelButton("حذف الحساب", MB.red) { delForce = false; delTarget = u }
                    }
                } else {
                    PanelKV("المحفظة", Panel.omr(u.pd("walletBalance")))
                    PanelKV("النقاط", "${u.pi("points")}")
                    PanelKV("الرتبة", u.ps("rank").ifEmpty { "—" })
                    PanelKV("الطلبات", "${u.pi("ordersCount")}")
                    PanelKV("انضم", Panel.whenTxt(u.pd("createdAt")))
                    PanelActions {
                        PanelButton("تعديل المحفظة/النقاط", MB.gold) { adjust = u }
                        PanelButton("التفاصيل") { detail = u }
                        PanelButton("حذف الحساب", MB.red) { delForce = false; delTarget = u }
                    }
                }
            }
        }
    }

    pending?.let { (id, name, ap) ->
        ConfirmDialog("${mapOf("approved" to "قبول", "rejected" to "رفض", "suspended" to "إيقاف")[ap] ?: ap} المندوب؟", name,
            confirmText = "تأكيد", onConfirm = {
                scope.launch {
                    store.panelRun("تم التحديث") { Panel.post("api/admin/approve", jsonOf("driverID" to id, "approval" to ap)) }
                    load()
                }
            }, onDismiss = { pending = null })
    }
    delTarget?.let { u ->
        ConfirmDialog(
            if (delForce) "لديه طلبات جارية أو سحب معلّق" else "هل أنت متأكد من حذف الحساب؟",
            if (delForce) "الحذف القسري سيُلغي طلباته الجارية. هل أنت متأكد؟"
            else "${u.ps("name").ifEmpty { u.ps("phone") }}: سيُمسح الحساب وكل بياناته وسجلاته نهائياً ولا يمكن التراجع.",
            confirmText = "نعم، احذف نهائياً", destructive = true,
            onConfirm = {
                val force = delForce
                delTarget = null
                scope.launch {
                    try {
                        Panel.post("api/admin/delete-account", jsonOf("phone" to u.ps("phone"),
                            "role" to (if (drivers) "driver" else "customer"), "confirm" to true, "force" to force))
                        store.toast = com.jaynak.app.core.AppStore.Toast(text = "تم حذف الحساب وكل بياناته نهائياً", kind = com.jaynak.app.core.AppStore.Toast.Kind.success)
                        load()
                    } catch (e: Exception) {
                        val m = e.message ?: "تعذّر الحذف"
                        if (!force && (m.contains("طلب جارٍ") || m.contains("سحب معلّق"))) { delForce = true; delTarget = u }
                        else store.toast = com.jaynak.app.core.AppStore.Toast(text = m, kind = com.jaynak.app.core.AppStore.Toast.Kind.error)
                    }
                }
            },
            onDismiss = { delTarget = null })
    }
    adjust?.let { u -> AdjustSheet(u, if (drivers) "driver" else "customer", { load() }) { adjust = null } }
    detail?.let { u -> UserDetailSheet(u, if (drivers) "driver" else "customer") { detail = null } }
    docs?.let { p -> DriverDocsSheet(p) { docs = null } }
    if (showNew) NewAccountSheet(if (drivers) "driver" else "customer", { load() }) { showNew = false }
}

@Composable
private fun AdjustSheet(user: JSONObject, role: String, onDone: suspend () -> Unit, onDismiss: () -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var wallet by remember { mutableStateOf("") }
    var points by remember { mutableStateOf("") }
    var deduct by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    PanelSheet(user.ps("name").ifEmpty { user.ps("phone") }, onDismiss) {
        Text("اختر إضافة أو خصم ثم اكتب القيمة. النقاط يجوز أن تصير سالبة (مثلاً 10 − 12 = −2). مبلغ المحفظة يقابله حساب المحفظة الأساسية.", style = MBType.micro, color = MB.ink2)
        com.jaynak.app.design.SegmentedPicker(listOf("إضافة", "خصم"), if (deduct) 1 else 0, { deduct = it == 1 })
        MBField("تعديل الرصيد (ر.ع)", wallet, { wallet = it }, placeholder = "0", keyboard = KeyboardType.Decimal, isRTLText = false)
        MBField("تعديل النقاط", points, { points = it }, placeholder = "0", keyboard = KeyboardType.Number, isRTLText = false)
        PrimaryCTA("حفظ", {
            val sg = if (deduct) -1 else 1
            val w = kotlin.math.abs(Panel.num(wallet) ?: 0.0) * sg
            val p = kotlin.math.abs((Panel.num(points) ?: 0.0).toInt()) * sg
            if (w == 0.0 && p == 0) {
                store.toast = com.jaynak.app.core.AppStore.Toast(text = "أدخل قيمة صحيحة", kind = com.jaynak.app.core.AppStore.Toast.Kind.error)
            } else {
                busy = true
                scope.launch {
                    val ok = store.panelRun("تم التعديل") {
                        Panel.post("api/admin/adjust", jsonOf("phone" to user.ps("phone"), "role" to role, "walletDelta" to w, "pointsDelta" to p))
                    }
                    busy = false
                    if (ok) { onDone(); onDismiss() }
                }
            }
        }, symbol = "checkmark", enabled = !busy)
    }
}

@Composable
private fun UserDetailSheet(user: JSONObject, role: String, onDismiss: () -> Unit) {
    var orders by remember { mutableStateOf(listOf<JSONObject>()) }
    var loading by remember { mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching {
            val phone = user.ps("phone")
            orders = Panel.get("api/admin/orders").pl("orders").filter {
                if (role == "driver") it.ps("driverPhone") == phone else it.ps("customerPhone") == phone
            }
        }
        loading = false
    }
    PanelSheet("التفاصيل", onDismiss) {
        PanelCard {
            Text(user.ps("name").ifEmpty { "بدون اسم" }, style = MBType.headline, color = MB.ink)
            Text(user.ps("phone"), style = MBType.num(12, androidx.compose.ui.text.font.FontWeight.Medium), color = MB.ink3)
            if (role == "driver") {
                PanelKV("الحالة", user.ps("approval")); PanelKV("المنطقة", user.ps("zone"))
                PanelKV("الرتبة", "${user.ps("rank")} (${user.pi("points")} نقطة)")
                PanelKV("رصيد الأرباح", Panel.omr(user.pd("earningsBalance")))
                PanelKV("طلبات مكتملة", "${user.pi("completedOrders")}")
                PanelKV("الرقم المدني", user.ps("civilID").ifEmpty { "—" })
            } else {
                PanelKV("المحفظة", Panel.omr(user.pd("walletBalance"))); PanelKV("النقاط", "${user.pi("points")}")
                PanelKV("الرتبة", user.ps("rank")); PanelKV("انضم", Panel.whenTxt(user.pd("createdAt")))
            }
            val done = orders.filter { it.ps("status") == "delivered" }
            PanelKV("إجمالي الطلبات (آخر 300)", "${orders.size}")
            PanelKV("مكتملة", "${done.size}")
            PanelKV("قيمة المكتملة", Panel.omr(done.sumOf { it.po("price").pd("total") }))
        }
        Text("آخر الطلبات", style = MBType.callout, color = MB.ink)
        if (loading) androidx.compose.material3.CircularProgressIndicator(color = MB.green)
        orders.take(15).forEach { o ->
            PanelCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(Panel.omr(o.po("price").pd("total")), style = MBType.num(13, androidx.compose.ui.text.font.FontWeight.SemiBold), color = MB.ink)
                    Spacer(Modifier.weight(1f))
                    MBChip(PanelStatus.title(o.ps("status")), tint = PanelStatus.tint(o.ps("status")))
                    Text("#" + o.ps("ref").ifEmpty { o.ps("id") }, style = MBType.caption, color = MB.ink)
                }
                Text("${o.po("pickup").ps("name")} ← ${o.po("dropoff").ps("name")}", style = MBType.micro, color = MB.ink2)
                Text(Panel.whenTxt(o.pd("createdAt")), style = MBType.micro, color = MB.ink3)
            }
        }
        if (!loading && orders.isEmpty()) Text("لا توجد طلبات", style = MBType.caption, color = MB.ink3)
    }
}

@Composable
private fun DriverDocsSheet(phone: String, onDismiss: () -> Unit) {
    val kinds = listOf("civilIDFront" to "الهوية (أمام)", "civilIDBack" to "الهوية (خلف)", "license" to "رخصة القيادة",
        "ownership" to "ملكية المركبة", "vehicle" to "صور المركبة", "selfie" to "صورة شخصية")
    var files by remember { mutableStateOf<Map<String, List<JSONObject>?>>(emptyMap()) }
    var viewing by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(phone) {
        val out = HashMap<String, List<JSONObject>?>()
        for ((k, _) in kinds) {
            out[k] = runCatching { Panel.get("api/admin/driver-document-url?phone=${java.net.URLEncoder.encode(phone, "UTF-8")}&kind=$k").pl("files") }.getOrNull()
        }
        files = out
    }
    PanelSheet("مستندات المندوب", onDismiss) {
        Text(phone, style = MBType.num(12, androidx.compose.ui.text.font.FontWeight.Medium), color = MB.ink3)
        kinds.forEach { (k, title) ->
            PanelCard {
                Text(title, style = MBType.callout, color = MB.ink)
                val fs = files[k]
                if (!files.containsKey(k)) Text("جارٍ التحميل…", style = MBType.micro, color = MB.ink3)
                else if (fs == null) Text("تعذّر التحميل", style = MBType.micro, color = MB.ink3)
                else if (fs.isEmpty()) Text("لم يُرفع", style = MBType.micro, color = MB.ink3)
                else fs.forEach { f ->
                    val url = f.ps("url")
                    Text("فتح الملف (${Panel.whenTxt(f.pd("uploadedAt"))})", style = MBType.caption, color = MB.green,
                        modifier = Modifier.pressable { viewing = url })
                }
            }
        }
    }
    viewing?.let { u ->
        val ctx = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.runtime.LaunchedEffect(u) {
            runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u))) }
            viewing = null
        }
    }
}

@Composable
private fun NewAccountSheet(role: String, onDone: suspend () -> Unit, onDismiss: () -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val isDriver = role == "driver"
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf("مسقط") }
    var civil by remember { mutableStateOf("") }
    var license by remember { mutableStateOf("") }
    var vMake by remember { mutableStateOf("") }
    var vModel by remember { mutableStateOf("") }
    var vPlate by remember { mutableStateOf("") }
    var approve by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val zones = listOf("مسقط", "مطرح", "بوشر", "السيب", "العامرات", "قريات")
    val digits = phone.filter { it.isDigit() }.takeLast(8)

    PanelSheet(if (isDriver) "إضافة مندوب" else "إضافة زبون", onDismiss) {
        Text("يُنشأ الحساب فوراً. يدخل صاحبه من التطبيق برقمه ورمز التحقق دون أي تسجيل أو رفع مستندات.", style = MBType.micro, color = MB.ink2)
        MBField("الاسم", name, { name = it }, placeholder = "الاسم الكامل")
        MBField("رقم الهاتف (8 أرقام)", phone, { phone = it }, placeholder = "9XXXXXXX", keyboard = KeyboardType.Number, isRTLText = false)
        if (isDriver) {
            Text("المنطقة", style = MBType.caption, color = MB.ink2)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                zones.forEach { z -> AdminChip(z, zone == z) { zone = z } }
            }
            MBField("الرقم المدني (اختياري)", civil, { civil = it }, keyboard = KeyboardType.Number, isRTLText = false)
            MBField("رقم الرخصة (اختياري)", license, { license = it }, isRTLText = false)
            MBField("نوع المركبة", vMake, { vMake = it })
            MBField("الموديل", vModel, { vModel = it })
            MBField("رقم اللوحة", vPlate, { vPlate = it }, isRTLText = false)
            Row(Modifier.fillMaxWidth().background(MB.ink.copy(alpha = 0.05f), RoundedCornerShape(14.dp)).pressable(0.99f) { approve = !approve }.padding(13.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (approve) "☑" else "☐", color = if (approve) MB.green else MB.ink3)
                Spacer(Modifier.weight(1f))
                Text("اعتماد المندوب فوراً (يستلم الطلبات)", style = MBType.callout, color = MB.ink)
            }
        }
        PrimaryCTA("إنشاء الحساب", {
            busy = true
            val body = jsonOf("role" to role, "name" to name, "phone" to digits)
            if (isDriver) {
                body.put("zone", zone).put("civilID", civil).put("licenseNumber", license)
                    .put("vehicle", jsonOf("make" to vMake, "model" to vModel, "plateNumber" to vPlate)).put("approve", approve)
            }
            scope.launch {
                var created = false
                val ok = store.panelRun("") { created = Panel.post("api/admin/account", body).pb("created") }
                busy = false
                if (ok) {
                    store.toast = com.jaynak.app.core.AppStore.Toast(
                        text = if (created) "تم إنشاء الحساب ✔" else "الحساب موجود — حُدّثت بياناته ✔",
                        kind = com.jaynak.app.core.AppStore.Toast.Kind.success)
                    onDone(); onDismiss()
                }
            }
        }, symbol = "checkmark", enabled = !busy && name.length >= 2 && digits.length == 8)
    }
}
