package com.jaynak.app.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    // (المعرّف الأصلي في iOS، العنصر)
    val entries = buildList {
        add(0 to TabItem("نظرة عامة", "chart.bar.fill"))
        if (store.can(AdminPermission.viewOrders) || store.can(AdminPermission.manageOrders))
            add(6 to TabItem("الخريطة", "map.fill"))
        if (store.can(AdminPermission.viewOrders) || store.can(AdminPermission.manageOrders))
            add(1 to TabItem("الطلبات", "shippingbox.fill"))
        if (store.can(AdminPermission.manageDrivers) || store.can(AdminPermission.manageCustomers)) {
            add(2 to TabItem("المستخدمون", "person.2.fill"))
            add(5 to TabItem("طلبات الحذف", "person.fill.xmark"))
        }
        if (store.can(AdminPermission.managePricing) || store.can(AdminPermission.manageRewards) ||
            store.can(AdminPermission.manageAdmins))
            add(3 to TabItem("الإعدادات", "slider.horizontal.3"))
        if (store.can(AdminPermission.manageSupport))
            add(4 to TabItem("الدعم", "lifepreserver.fill"))
    }
    var selectedValue by remember { mutableStateOf(0) }
    val index = entries.indexOfFirst { it.first == selectedValue }.coerceAtLeast(0)

    RoleToneScope(RoleTone.admin) {
        TabScaffold(
            tabs = entries.map { it.second },
            selected = index,
            onSelect = { selectedValue = entries[it].first },
            accessory = { AdminLiveAccessory() },
        ) { i ->
            when (entries.getOrNull(i)?.first) {
                0 -> AdminOverview()
                6 -> AdminLiveMap()
                1 -> AdminOrders()
                2 -> AdminUsers()
                5 -> AdminDeletionRequests()
                3 -> AdminSettings()
                4 -> AdminSupport()
                else -> AdminOverview()
            }
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
