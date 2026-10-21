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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.DriverApproval
import com.jaynak.app.core.DriverPresence
import com.jaynak.app.core.DispatchMode
import com.jaynak.app.core.Order
import com.jaynak.app.core.OrderStatus
import com.jaynak.app.core.UserRole
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
import com.jaynak.app.design.MBType
import com.jaynak.app.design.NavBar
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.SegmentedPicker
import com.jaynak.app.design.StarRating
import com.jaynak.app.design.StatusBadge
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.pressable
import com.jaynak.app.features.shared.OrderDetailSheet
import java.util.Locale

private val orange = Color(0xFFFF9500)

/** مقابل AdminOrders.swift. */
@Composable
fun AdminOrders() {
    val store = LocalStore.current
    var filter by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Order?>(null) }
    var reassigning by remember { mutableStateOf<Order?>(null) }

    var out = store.orders
    when (filter) {
        1 -> out = out.filter { it.status.isLive }
        2 -> out = out.filter { it.status == OrderStatus.delivered }
        3 -> out = out.filter { it.status == OrderStatus.cancelled }
    }
    if (search.isNotEmpty()) {
        out = out.filter {
            it.ref.contains(search, ignoreCase = true) || it.pickup.name.contains(search) || it.dropoff.name.contains(search)
        }
    }

    Box(Modifier.fillMaxSize()) {
        BrandBackground()
        Column(Modifier.fillMaxSize()) {
            NavBar(title = "الطلبات")
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AdminSearchField(search, { search = it }, "ابحث برقم الطلب أو المكان")
                SegmentedPicker(listOf("الكل", "حيّة", "مكتملة", "ملغية"), filter, { filter = it })

                if (out.isEmpty()) EmptyStateView("shippingbox", "لا نتائج", "غيّر الفلتر أو كلمة البحث")

                out.forEach { o ->
                    AdminOrderRow(o, onOpen = { selected = o }, onReassign = { reassigning = o },
                        onCancel = { store.cancelOrder(o, "أُلغي من الإدارة", UserRole.admin) })
                }
                Spacer(Modifier.height(150.dp))
            }
        }
    }

    selected?.let { OrderDetailSheet(it, UserRole.admin) { selected = null } }
    reassigning?.let { ReassignSheet(it) { reassigning = null } }
}

@Composable
private fun AdminOrderRow(order: Order, onOpen: () -> Unit, onReassign: () -> Unit, onCancel: () -> Unit) {
    val store = LocalStore.current
    val driverName = store.drivers.firstOrNull { it.id == order.driverID }?.name

    Column(Modifier.fillMaxWidth().glassSurface(20.dp, null, 0.08f).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(MB.green.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                SFIcon(order.kind.symbol, size = 14.dp, tint = MB.green)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(order.ref, style = MBType.num(13, FontWeight.Bold), color = MB.ink)
                    StatusBadge(order.status)
                }
                Text("${order.pickup.name} ← ${order.dropoff.name}", style = MBType.micro, color = MB.ink2,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(order.price.total.money, style = MBType.num(14, FontWeight.Bold), color = MB.ink)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (driverName != null) MBChip(driverName, symbol = "car.fill", tint = MB.red)
            else MBChip("بلا مندوب", symbol = "exclamationmark.triangle.fill", tint = orange)
            MBChip(order.payment.title, symbol = order.payment.symbol)
            if (order.dispatchMode == DispatchMode.open || order.promotedToOpen) {
                MBChip(if (order.promotedToOpen) "رُفع للوحة" else "مفتوح",
                    symbol = "list.bullet.rectangle.portrait.fill", tint = MB.gold, filled = true)
            }
        }

        if (order.status.isLive) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("تفاصيل", onOpen, horizontal = 14.dp, vertical = 8.dp)
                GlassButton("إعادة توزيع", onReassign, tint = MB.green, horizontal = 14.dp, vertical = 8.dp)
                GlassButton("إلغاء", onCancel, tint = MB.red, horizontal = 14.dp, vertical = 8.dp)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                GlassButton("تفاصيل", onOpen, horizontal = 14.dp, vertical = 8.dp)
            }
        }
    }
}

/** مقابل ReassignSheet. */
@Composable
fun ReassignSheet(order: Order, onDismiss: () -> Unit) {
    val store = LocalStore.current
    val candidates = store.drivers
        .filter { it.approval == DriverApproval.approved && it.id != order.driverID }
        .sortedBy { it.coord.km(order.pickup.coord) }

    JSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxSize()) {
            BrandBackground(intensity = 0.5f)
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("إغلاق", style = MBType.callout, color = MB.green, modifier = Modifier.clickableNoRipple(onDismiss))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("إعادة توزيع", style = MBType.headline, color = MB.ink)
                    }
                    Spacer(Modifier.padding(horizontal = 20.dp))
                }
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassCard(Modifier.fillMaxWidth(), tint = MB.green.copy(alpha = 0.12f)) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                            Text("الطلب ${order.ref}", style = MBType.headline, color = MB.ink)
                            Text("${order.pickup.name} ← ${order.dropoff.name}", style = MBType.caption, color = MB.ink2)
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().glassSurface(18.dp, orange.copy(alpha = 0.14f), 0.06f)
                            .pressable { store.reassign(order, null); onDismiss() }.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SFIcon("arrow.clockwise.circle.fill", size = 18.dp, tint = orange)
                        Text("إرجاع لقائمة البحث", style = MBType.callout, color = MB.ink)
                    }

                    SectionHeader("أقرب المندوبين", symbol = "car.fill")

                    candidates.forEach { d ->
                        Row(
                            Modifier.fillMaxWidth().glassSurface(18.dp, null, 0.06f)
                                .pressable { store.reassign(order, d.id); onDismiss() }.padding(13.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(d.name, size = 40.dp, tint = MB.red)
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(d.name, style = MBType.callout, color = MB.ink)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    StarRating(d.rating, size = 9.dp)
                                    Text("· ${d.presence.title}", style = MBType.micro,
                                        color = if (d.presence == DriverPresence.online) MB.green else MB.ink3)
                                }
                            }
                            Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text("%.1f".format(Locale.US, d.coord.km(order.pickup.coord)),
                                    style = MBType.num(14, FontWeight.Bold), color = MB.ink)
                                Text("كم", style = MBType.micro, color = MB.ink3)
                            }
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}
