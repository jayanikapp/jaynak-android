package com.jaynak.app.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import com.jaynak.app.core.AppConfig
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AdminPermission
import com.jaynak.app.core.AdminRole
import com.jaynak.app.core.DriverPresence
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBType
import com.jaynak.app.design.RoleTone
import com.jaynak.app.design.RoleToneScope
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.TabItem
import com.jaynak.app.design.TabScaffold

/** مقابل AdminRoot.swift — التبويبات تتبع صلاحيات المسؤول. */
@Composable
fun AdminRoot() {
    val store = LocalStore.current

    // التبويبات الرئيسية + «المزيد» (شريط الأندرويد يتّسع لخمسة تقريباً)
    val entries = buildList {
        add(0 to TabItem("نظرة عامة", "chart.bar.fill"))
        if (store.can(AdminPermission.viewOrders) || store.can(AdminPermission.manageOrders))
            add(1 to TabItem("الطلبات", "shippingbox.fill"))
        if (store.can(AdminPermission.viewOrders) || store.can(AdminPermission.manageOrders))
            add(8 to TabItem("التتبع", "point.topleft.down.to.point.bottomright.curvepath"))
        if (store.can(AdminPermission.manageDrivers) || store.can(AdminPermission.manageCustomers))
            add(2 to TabItem("المستخدمون", "person.2.fill"))
        add(20 to TabItem("المزيد", "ellipsis.circle.fill"))
    }
    var more by remember { mutableStateOf<Int?>(null) }
    val moreItems = buildList {
        val ord = store.can(AdminPermission.viewOrders) || store.can(AdminPermission.manageOrders)
        val usr = store.can(AdminPermission.manageDrivers) || store.can(AdminPermission.manageCustomers)
        if (ord) { add(6 to "الخريطة المباشرة"); add(7 to "سجل الصور") }
        if (store.can(AdminPermission.manageDrivers)) add(9 to "السحوبات")
        if (usr) { add(5 to "طلبات الحذف") }
        if (store.can(AdminPermission.manageAdmins)) add(10 to "الرتب")
        if (store.can(AdminPermission.managePricing) || store.can(AdminPermission.manageRewards) ||
            store.can(AdminPermission.manageAdmins)) add(3 to "الإعدادات")
        if (store.can(AdminPermission.manageSupport)) add(4 to "الدعم")
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            store.refreshAdminPeople()
            kotlinx.coroutines.delay(8000)
        }
    }
    var selectedValue by remember { mutableStateOf(0) }
    val index = entries.indexOfFirst { it.first == selectedValue }.coerceAtLeast(0)

    var gated by remember { mutableStateOf(false) }
    val needed by AppConfig.adminCodeNeeded.collectAsState()
    androidx.compose.runtime.LaunchedEffect(needed) {
        val r = runCatching { Panel.get("api/admin/code-status") }.getOrNull() ?: return@LaunchedEffect
        if (r.pb("set") && !r.pb("elevated")) gated = true
    }
    RoleToneScope(RoleTone.admin) {
      Box {
        TabScaffold(
            tabs = entries.map { it.second },
            selected = index,
            onSelect = { selectedValue = entries[it].first; if (entries[it].first == 20) more = null },
            accessory = { AdminLiveAccessory() },
        ) { i ->
            when (entries.getOrNull(i)?.first) {
                0 -> AdminServerOverview()
                1 -> AdminServerOrders()
                2 -> {
                    var drv by remember { mutableStateOf(true) }
                    androidx.compose.foundation.layout.Column {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AdminChip("المناديب", drv) { drv = true }
                            AdminChip("الزبائن", !drv) { drv = false }
                        }
                        AdminServerPeople(drivers = drv)
                    }
                }
                8 -> AdminServerTrace()
                20 -> {
                    val m = more
                    if (m == null) MoreMenu(moreItems) { more = it }
                    else {
                        androidx.activity.compose.BackHandler { more = null }
                        when (m) {
                            6 -> AdminLiveMap()
                            8 -> AdminServerTrace()
                            7 -> AdminProofs()
                            9 -> AdminServerPayouts()
                            5 -> AdminServerDeletions()
                            10 -> AdminServerRanks()
                            3 -> AdminServerSettings()
                            4 -> AdminSupport()
                        }
                    }
                }
                else -> AdminServerOverview()
            }
        }
        if (gated) AdminCodeGate { gated = false }
      }
    }
}

@Composable
private fun AdminCodeGate(onDone: () -> Unit) {
    val store = LocalStore.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        com.jaynak.app.design.BrandBackground()
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("رمز الإدارة", style = MBType.title, color = MB.ink)
            Text("أدخل رمز الإدارة لفتح اللوحة", style = MBType.caption, color = MB.ink2)
            androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
            com.jaynak.app.design.MBField("الرمز", code, { code = it }, isRTLText = false)
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
            com.jaynak.app.design.PrimaryCTA(if (busy) "..." else "فتح", {
                if (code.isEmpty() || busy) return@PrimaryCTA
                busy = true
                scope.launch {
                    val ok = store.panelRun("") {
                        val d = Panel.post("api/admin/code", jsonOf("action" to "verify", "code" to code))
                        AppConfig.adminToken = d.ps("token")
                    }
                    busy = false
                    if (ok) { code = ""; onDone() }
                }
            }, symbol = "lock.open.fill")
        }
    }
}

@Composable
fun AdminLiveAccessory() {
    val store = LocalStore.current
    val liveCount = store.orders.count { it.status.isLive }
    val onlineDrivers = store.drivers.count { it.presence != DriverPresence.offline }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(MB.green, CircleShape))
            Text("$liveCount طلب حيّ", style = MBType.ar(13, FontWeight.SemiBold), color = MB.ink)
        }
        Box(Modifier.width(1.dp).size(width = 1.dp, height = 16.dp).background(MB.hairline))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            SFIcon("car.fill", size = 11.dp, tint = MB.ink)
            Text("$onlineDrivers متصل", style = MBType.ar(13, FontWeight.Medium), color = MB.ink)
        }
        Box(Modifier.weight(1f))
        store.currentAdmin?.let { a ->
            MBChip(a.role.title, symbol = a.role.symbol,
                tint = if (a.role == AdminRole.owner) MB.gold else MB.green, filled = true)
        }
    }
}

@Composable
private fun MoreMenu(items: List<Pair<Int, String>>, onPick: (Int) -> Unit) {
    PanelScreen("المزيد") {
        items.forEach { (k, t) ->
            PanelCard {
                Row(Modifier.fillMaxWidth().clickable { onPick(k) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", style = MBType.callout, color = MB.ink3)
                    Spacer(Modifier.weight(1f))
                    Text(t, style = MBType.callout, color = MB.ink)
                }
            }
        }
    }
}
