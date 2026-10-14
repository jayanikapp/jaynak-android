package com.jaynak.app.features.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AdminData
import com.jaynak.app.core.AppConfig
import com.jaynak.app.core.AppStore
import com.jaynak.app.core.money
import com.jaynak.app.design.Avatar
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.EmptyStateView
import com.jaynak.app.design.GlassButton
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBField
import com.jaynak.app.design.MBType
import com.jaynak.app.design.NavBar
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.ProminentButton
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SecondaryCTA
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.SegmentedPicker
import com.jaynak.app.design.StatTile
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.pressable
import com.jaynak.app.features.shared.NavigationLauncher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val orange = Color(0xFFFF9500)

private fun dg(s: String): String =
    s.map { if (it.isDigit()) ('0' + Character.digit(it, 10)) else it }.joinToString("")

private fun approvalTitle(a: String) = when (a) {
    "approved" -> "معتمد"
    "pending" -> "بانتظار الموافقة"
    "rejected" -> "مرفوض"
    "suspended" -> "موقوف"
    else -> ""
}

private fun last8(p: String) = p.filter { it.isDigit() }.takeLast(8)

/** شاشة المستخدمين: قائمة واضحة + تحكم كامل بالمحفظة والنقاط والرتب + شاشة المرتبات. */
@Composable
fun AdminAccounts() {
    var segment by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var showRanks by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            runCatching { AdminData.refresh() }
            delay(4000)
        }
    }

    val role = if (segment == 0) "driver" else "customer"
    val all = AdminData.people.filter { it.role == role }
    val list = all.filter { search.isEmpty() || it.name.contains(search) || it.phone.contains(dg(search)) }
        .sortedWith(compareBy<AdminData.Person>({ if (it.approval == "pending") 0 else 1 }, { -it.orders }))
    val nDrivers = AdminData.people.count { it.role == "driver" }
    val nCustomers = AdminData.people.count { it.role == "customer" }

    Box(Modifier.fillMaxSize()) {
        BrandBackground()
        Column(Modifier.fillMaxSize()) {
            NavBar(title = "المستخدمون")
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SegmentedPicker(listOf("المندوبون (" + nDrivers + ")", "العملاء (" + nCustomers + ")"),
                    segment, { segment = it })
                AdminSearchField(search, { search = it }, "ابحث بالاسم أو الرقم")

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).glassSurface(18.dp, null, 0.08f).padding(12.dp),
                        horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("رصيد المحفظة الأساسية", style = MBType.micro, color = MB.ink2)
                        Text(AdminData.platform.money, style = MBType.num(17, FontWeight.Bold),
                            color = if (AdminData.platform < 0) MB.red else MB.green)
                    }
                    GlassButton("المرتبات", { showRanks = true }, tint = MB.gold, symbol = "rosette", vertical = 14.dp)
                }

                if (list.isEmpty()) EmptyStateView("person.2.fill", "لا يوجد مستخدمون", "سيظهرون هنا فور تسجيلهم")
                list.forEach { p -> PersonCard(p) { selected = p.role + "|" + p.phone } }
                Spacer(Modifier.height(150.dp))
            }
        }
    }

    selected?.let { AccountSheet(it) { selected = null } }
    if (showRanks) RanksSheet { showRanks = false }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier, color: Color) {
    Column(modifier.glassSurface(12.dp, null, 0.05f).padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MBType.num(15, FontWeight.Bold), color = color)
        Text(label, style = MBType.micro, color = MB.ink3)
    }
}

@Composable
private fun PersonCard(p: AdminData.Person, onClick: () -> Unit) {
    val isDriver = p.role == "driver"
    val tint = if (!isDriver) MB.green else when (p.approval) {
        "approved" -> MB.green
        "pending" -> orange
        else -> MB.red
    }
    val balLabel = if (!isDriver) "المحفظة" else if (p.wallet < 0) "عليه" else "له"
    val balText = if (isDriver && p.wallet < 0) (-p.wallet).money else p.wallet.money
    val balColor = if (isDriver && p.wallet < 0) MB.red else MB.green

    Column(Modifier.fillMaxWidth().glassSurface(20.dp, null, 0.08f).pressable(onClick = onClick).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.name, size = 44.dp, tint = tint)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(p.name, style = MBType.callout, color = MB.ink)
                Text(AppConfig.countryDialCode + " " + last8(p.phone) + (if (p.zone.isNotEmpty()) "  ·  " + p.zone else ""),
                    style = MBType.num(11, FontWeight.Medium), color = MB.ink3)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (p.rank.isNotEmpty()) MBChip(p.rank, symbol = "rosette", tint = MB.gold)
                    if (isDriver && p.approval.isNotEmpty())
                        MBChip(approvalTitle(p.approval), tint = tint, filled = p.approval == "pending")
                }
            }
            SFIcon("chevron.forward", size = 10.dp, tint = MB.ink3)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat(if (isDriver) "توصيلات" else "طلبات", p.orders.toString(), Modifier.weight(1f), MB.ink)
            MiniStat("النقاط", p.points.toString(), Modifier.weight(1f), orange)
            MiniStat(balLabel, balText, Modifier.weight(1f), balColor)
        }
    }
}

@Composable
private fun SheetTop(title: String, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("إغلاق", style = MBType.callout, color = MB.green, modifier = Modifier.clickableNoRipple(onDismiss))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(title, style = MBType.headline, color = MB.ink)
        }
        Spacer(Modifier.width(40.dp))
    }
}

@Composable
private fun AccountSheet(key: String, onDismiss: () -> Unit) {
    val store = LocalStore.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val role = key.substringBefore("|")
    val phone = key.substringAfter("|")
    val p = AdminData.people.firstOrNull { it.role == role && it.phone == phone }
    var amount by remember { mutableStateOf("") }
    var pts by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showDriver by remember { mutableStateOf(false) }
    val ranks = AdminData.ranks.filter { it.kind == role }.sortedBy { it.minPoints }
    val isDriver = role == "driver"
    val localDriver = if (isDriver) store.drivers.firstOrNull { last8(it.phone) == last8(phone) } else null
    val localCustomer = if (!isDriver) store.customers.firstOrNull { last8(it.phone) == last8(phone) } else null

    fun err(m: String) { store.toast = AppStore.Toast(text = m, kind = AppStore.Toast.Kind.error) }

    fun act(wallet: Double, points: Int, rankId: String? = null, setRank: Boolean = false, done: String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                AdminData.adjust(phone, role, wallet, points, rankId, setRank)
                store.toast = AppStore.Toast(text = done, kind = AppStore.Toast.Kind.success)
            } catch (e: Exception) {
                err(e.message ?: "تعذّر التنفيذ")
            }
            busy = false
        }
    }

    JSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxSize()) {
            BrandBackground(intensity = 0.5f)
            Column(Modifier.fillMaxSize()) {
                SheetTop(if (isDriver) "حساب المندوب" else "حساب الزبون", onDismiss)
                if (p != null) {
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Avatar(p.name, size = 62.dp, tint = if (isDriver) MB.red else MB.green)
                                Text(p.name, style = MBType.section, color = MB.ink)
                                Text(AppConfig.countryDialCode + " " + last8(p.phone),
                                    style = MBType.num(14, FontWeight.Medium), color = MB.ink2)
                                if (p.rank.isNotEmpty()) MBChip(p.rank, symbol = "rosette", tint = MB.gold, filled = true)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ProminentButton("اتصال", { NavigationLauncher.call(ctx, last8(p.phone)) },
                                        Modifier.weight(1f), symbol = "phone.fill", vertical = 10.dp)
                                    GlassButton("واتساب", { NavigationLauncher.whatsapp(ctx, last8(p.phone)) },
                                        Modifier.weight(1f), tint = MB.green, symbol = "message.fill", vertical = 10.dp)
                                }
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatTile(if (isDriver) "توصيلات" else "طلبات", p.orders.toString(), "shippingbox.fill", Modifier.weight(1f))
                            StatTile("النقاط", p.points.toString(), "star.circle.fill", Modifier.weight(1f), tint = orange)
                            StatTile(if (!isDriver) "المحفظة" else if (p.wallet < 0) "عليه" else "له",
                                if (isDriver && p.wallet < 0) (-p.wallet).money else p.wallet.money,
                                "wallet.bifold.fill", Modifier.weight(1f),
                                tint = if (isDriver && p.wallet < 0) MB.red else MB.green)
                        }

                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
                                SectionHeader(if (isDriver) "محفظة المندوب" else "محفظة الزبون", symbol = "wallet.bifold.fill")
                                MBField("المبلغ (ر.ع.)", amount, { amount = dg(it) }, placeholder = "مثال: 5",
                                    keyboard = KeyboardType.Decimal)
                                val a = dg(amount).toDoubleOrNull()
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ProminentButton("إضافة للمحفظة", {
                                        if (a != null && a > 0) { act(a, 0, done = "أُضيف " + a.money + " للمحفظة"); amount = "" }
                                        else err("اكتب مبلغاً صحيحاً")
                                    }, Modifier.weight(1f), symbol = "plus", enabled = !busy)
                                    GlassButton("خصم من المحفظة", {
                                        if (a != null && a > 0) { act(-a, 0, done = "خُصم " + a.money + " من المحفظة"); amount = "" }
                                        else err("اكتب مبلغاً صحيحاً")
                                    }, Modifier.weight(1f), tint = MB.red, symbol = "minus", enabled = !busy)
                                }
                                Text("الإضافة تُحسب من المحفظة الأساسية، والخصم يرجع إليها.", style = MBType.micro, color = MB.ink3)
                            }
                        }

                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
                                SectionHeader("النقاط", symbol = "star.circle.fill")
                                MBField("عدد النقاط", pts, { pts = dg(it).filter { c -> c.isDigit() } }, placeholder = "مثال: 50",
                                    keyboard = KeyboardType.Number)
                                val n = dg(pts).toIntOrNull()
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ProminentButton("إضافة نقاط", {
                                        if (n != null && n > 0) { act(0.0, n, done = "أُضيفت " + n + " نقطة"); pts = "" }
                                        else err("اكتب عدد نقاط صحيحاً")
                                    }, Modifier.weight(1f), tint = orange, symbol = "plus", enabled = !busy)
                                    GlassButton("خصم نقاط", {
                                        if (n != null && n > 0) { act(0.0, -n, done = "خُصمت " + n + " نقطة"); pts = "" }
                                        else err("اكتب عدد نقاط صحيحاً")
                                    }, Modifier.weight(1f), tint = MB.red, symbol = "minus", enabled = !busy)
                                }
                            }
                        }

                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
                                SectionHeader("الرتبة", symbol = "rosette")
                                Text("الرتبة الحالية: " + (if (p.rank.isEmpty()) "—" else p.rank) +
                                    (if (p.rankId != null) " (معيّنة يدوياً)" else " (تلقائية حسب النقاط)"),
                                    style = MBType.caption, color = MB.ink2)
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AdminChip("تلقائي", p.rankId == null, horizontal = 12.dp, vertical = 8.dp) {
                                        act(0.0, 0, null, true, "رُجعت الرتبة للتلقائي")
                                    }
                                    ranks.forEach { r ->
                                        AdminChip(r.title + " · " + r.minPoints, p.rankId == r.id, tint = MB.gold,
                                            horizontal = 12.dp, vertical = 8.dp) {
                                            act(0.0, 0, r.id, true, "تم تعيين الرتبة: " + r.title)
                                        }
                                    }
                                }
                            }
                        }

                        if (localDriver != null) {
                            SecondaryCTA("ملف المندوب والمستندات والاعتماد", onClick = { showDriver = true },
                                symbol = "person.text.rectangle.fill")
                        }
                        if (localCustomer != null) {
                            SecondaryCTA(if (localCustomer.isBlocked) "إعادة تفعيل الحساب" else "إيقاف الحساب",
                                onClick = { store.setBlocked(!localCustomer.isBlocked, localCustomer.id) },
                                symbol = if (localCustomer.isBlocked) "checkmark.circle" else "hand.raised.fill",
                                tint = if (localCustomer.isBlocked) MB.green else MB.red)
                        }
                        Spacer(Modifier.height(30.dp))
                    }
                }
            }
        }
    }

    if (showDriver && localDriver != null) DriverAdminSheet(localDriver.id) { showDriver = false }
}

@Composable
private fun RanksSheet(onDismiss: () -> Unit) {
    JSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxSize()) {
            BrandBackground(intensity = 0.5f)
            Column(Modifier.fillMaxSize()) {
                SheetTop("المرتبات", onDismiss)
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("كل رتبة لها عدد نقاط يلزم الوصول إليه. يحصل الشخص عليها تلقائياً عند بلوغ النقاط، أو تعيّنها له يدوياً من حسابه.",
                        style = MBType.caption, color = MB.ink2)
                    RankSection("مرتبات المندوبين", "driver")
                    RankSection("مرتبات الزبائن", "customer")
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
private fun RankSection(title: String, kind: String) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var editId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var min by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val list = AdminData.ranks.filter { it.kind == kind }.sortedBy { it.minPoints }

    fun run(done: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
                store.toast = AppStore.Toast(text = done, kind = AppStore.Toast.Kind.success)
            } catch (e: Exception) {
                store.toast = AppStore.Toast(text = e.message ?: "تعذّر التنفيذ", kind = AppStore.Toast.Kind.error)
            }
            busy = false
        }
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
            SectionHeader(title, symbol = "rosette")
            list.forEach { r ->
                Row(Modifier.fillMaxWidth().glassSurface(14.dp, null, 0.06f).padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clickableNoRipple { run("حُذفت الرتبة") { AdminData.deleteRank(r.id) } }) {
                        SFIcon("trash", size = 16.dp, tint = MB.red)
                    }
                    Box(Modifier.clickableNoRipple { editId = r.id; name = r.title; min = r.minPoints.toString() }) {
                        SFIcon("pencil", size = 16.dp, tint = MB.ink2)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(r.minPoints.toString() + " نقطة", style = MBType.num(13, FontWeight.Medium), color = MB.ink2)
                    Text(r.title, style = MBType.callout, color = MB.ink)
                }
            }
            MBField("اسم الرتبة", name, { name = it }, placeholder = "مثال: ماسي")
            MBField("النقاط المطلوبة", min, { min = dg(it).filter { c -> c.isDigit() } }, placeholder = "مثال: 1500",
                keyboard = KeyboardType.Number)
            PrimaryCTA(if (editId == null) "إضافة رتبة" else "حفظ التعديل", onClick = {
                val pts = dg(min).toIntOrNull() ?: 0
                val id = editId
                val nm = name.trim()
                run(if (id == null) "أُضيفت الرتبة" else "تم حفظ التعديل") { AdminData.saveRank(id, kind, nm, pts) }
                editId = null; name = ""; min = ""
            }, enabled = name.isNotBlank() && min.isNotEmpty() && !busy)
            if (editId != null) GlassButton("إلغاء التعديل", { editId = null; name = ""; min = "" })
        }
    }
}
