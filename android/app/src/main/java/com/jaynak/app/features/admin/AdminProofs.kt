package com.jaynak.app.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.Fmt
import com.jaynak.app.core.Order
import com.jaynak.app.core.OrderExtras
import com.jaynak.app.design.Avatar
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.EmptyStateView
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.NavBar
import com.jaynak.app.design.PhotoViewer
import com.jaynak.app.design.RemoteImage
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.StatusBadge
import com.jaynak.app.design.clickableNoRipple

/** سجل صور الاستلام والتسليم: صور المندوب مع بيانات المندوب والزبون لكل طلب. */
@Composable
fun AdminProofs() {
    val store = LocalStore.current
    var search by remember { mutableStateOf("") }
    val list = store.orders.filter {
        val x = OrderExtras.of(it.remoteID)
        x != null && (x.proofPickup.isNotEmpty() || x.proofDelivery.isNotEmpty())
    }.filter {
        search.isBlank() || it.ref.contains(search, ignoreCase = true) ||
            (OrderExtras.of(it.remoteID)?.let { x -> x.customerName.contains(search) || x.driverName.contains(search) } == true)
    }.sortedByDescending { it.createdAt }

    Box(Modifier.fillMaxSize()) {
        BrandBackground()
        Column(Modifier.fillMaxSize()) {
            NavBar(title = "سجل الصور")
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AdminSearchField(search, { search = it }, "ابحث برقم الطلب أو اسم المندوب/الزبون")
                if (list.isEmpty()) EmptyStateView("photo", "لا توجد صور بعد",
                    "تظهر هنا صور الاستلام والتسليم التي يلتقطها المندوبون")
                list.forEach { ProofCard(it) }
                Spacer(Modifier.height(150.dp))
            }
        }
    }
}

@Composable
private fun ProofCard(o: Order) {
    val store = LocalStore.current
    val x = OrderExtras.of(o.remoteID) ?: return
    val driver = store.drivers.firstOrNull { it.id == o.driverID }
    val customer = store.customers.firstOrNull { it.id == o.customerID }
    val dName = driver?.name ?: x.driverName.ifEmpty { "مندوب" }
    val cName = customer?.name ?: x.customerName.ifEmpty { "زبون" }
    var viewing by remember { mutableStateOf<String?>(null) }

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(o.status)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(o.ref, style = MBType.num(15, FontWeight.Bold), color = MB.ink)
                    Text(Fmt.dateTimeShort(o.createdAt), style = MBType.micro, color = MB.ink3)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PersonTile("المندوب", dName, driver?.phone ?: "", Modifier.weight(1f), MB.red)
                PersonTile("الزبون", cName, customer?.phone ?: "", Modifier.weight(1f), MB.green)
            }
            if (x.recipientName.isNotEmpty() || o.recipientPhone.isNotEmpty())
                Text("المستلم: ${x.recipientName} ${o.recipientPhone}".trim(), style = MBType.caption, color = MB.ink2)
            Text("${o.pickup.name}  ←  ${o.dropoff.name}", style = MBType.micro, color = MB.ink3,
                maxLines = 1, overflow = TextOverflow.Ellipsis)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Triple("الاستلام", x.proofPickup, x.proofPickupAt), Triple("التسليم", x.proofDelivery, x.proofDeliveryAt))
                    .forEach { (label, tok, at) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp))
                                .background(MB.ink.copy(alpha = 0.07f))
                                .clickableNoRipple { if (tok.isNotEmpty()) viewing = tok },
                                contentAlignment = Alignment.Center) {
                                if (tok.isNotEmpty()) RemoteImage(tok, Modifier.fillMaxWidth().height(150.dp))
                                else SFIcon("photo", size = 26.dp, tint = MB.ink3)
                            }
                            Text(if (tok.isNotEmpty() && at > 0) "$label · ${Fmt.timeShort((at * 1000).toLong())}" else label,
                                style = MBType.micro, color = MB.ink2)
                        }
                    }
            }
        }
    }
    viewing?.let { PhotoViewer(it) { viewing = null } }
}

@Composable
private fun PersonTile(role: String, name: String, phone: String, modifier: Modifier, tint: androidx.compose.ui.graphics.Color) {
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(MB.ink.copy(alpha = 0.05f)).padding(9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name, size = 38.dp, tint = tint)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(role, style = MBType.micro, color = MB.ink3)
            Text(name, style = MBType.callout, color = MB.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (phone.isNotBlank()) Text(phone.filter { it.isDigit() }.takeLast(8), style = MBType.num(12, FontWeight.Medium), color = MB.green)
        }
    }
}
