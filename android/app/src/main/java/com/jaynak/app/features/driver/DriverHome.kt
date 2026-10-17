package com.jaynak.app.features.driver

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AppConfig
import com.jaynak.app.core.DriverPresence
import com.jaynak.app.core.Order
import com.jaynak.app.core.OrderStatus
import com.jaynak.app.core.PaymentMethod
import com.jaynak.app.core.money
import com.jaynak.app.core.moneyShort
import com.jaynak.app.design.Avatar
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.EmptyStateView
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.GlassIconButton
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBType
import com.jaynak.app.design.Metrics
import com.jaynak.app.design.OrderProgressTrack
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.StatTile
import com.jaynak.app.design.StatusBadge
import com.jaynak.app.design.appearIn
import com.jaynak.app.design.glassChip
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.pressable
import com.jaynak.app.features.customer.OrderRow
import com.jaynak.app.features.shared.ChatView
import com.jaynak.app.features.shared.MapCanvas
import com.jaynak.app.features.shared.NavigationLauncher
import java.util.Calendar
import java.util.Locale

private fun isToday(ms: Long): Boolean {
    val a = Calendar.getInstance().apply { timeInMillis = ms }
    val b = Calendar.getInstance()
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

private val orange = Color(0xFFFF9500)

/** مقابل DriverHome.swift. */
@Composable
fun DriverHome(onOpenTrip: () -> Unit) {
    val store = LocalStore.current
    var showSupport by remember { mutableStateOf(false) }
    val d = store.currentDriver
    val actives = store.activeOrdersForCurrentDriver

    val todayOrders = if (d == null) emptyList() else store.orders.filter {
        it.driverID == d.id && it.status == OrderStatus.delivered && isToday(it.deliveredAt ?: it.createdAt)
    }
    val todayEarnings = todayOrders.sumOf { it.price.driverPayout }

    Box(Modifier.fillMaxSize()) {
        BrandBackground()
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // الرأس
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).appearIn(0),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(d?.name ?: "مندوب", size = 46.dp, tint = MB.red)
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(d?.name?.split(" ")?.firstOrNull { it.isNotEmpty() } ?: "مندوب",
                        style = MBType.ar(19, FontWeight.Bold), color = MB.ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(
                            when (d?.presence) {
                                DriverPresence.online -> MB.green
                                DriverPresence.busy -> orange
                                else -> MB.ink3
                            }, CircleShape))
                        Text(d?.presence?.title ?: "", style = MBType.caption, color = MB.ink2)
                    }
                }
                Spacer(Modifier.weight(1f))
                Box {
                    GlassIconButton("bubble.left.and.text.bubble.right.fill", { showSupport = true },
                        size = 42.dp, iconSize = 15.dp, tint = MB.red)
                    val un = store.unread("support")
                    if (un > 0) Box(Modifier.align(Alignment.TopEnd).size(20.dp).background(MB.red, CircleShape),
                        contentAlignment = Alignment.Center) {
                        Text("$un", style = MBType.num(11, FontWeight.Bold), color = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }

            if (store.isDriverBlockedByBalance) {
                Row(Modifier.fillMaxWidth().glassSurface(18.dp, MB.red.copy(alpha = 0.20f), 0.10f).padding(13.dp),
                    horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                    SFIcon("exclamationmark.octagon.fill", size = 20.dp, tint = MB.red)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("الطلبات متوقّفة — رصيدك بالسالب", style = MBType.callout, color = MB.ink)
                        Text("افتح «أرباحي» وسدّد المستحق لتعود للعمل", style = MBType.micro, color = MB.ink2)
                    }
                }
            }

            Box(Modifier.fillMaxWidth().appearIn(1)) {
                if (actives.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    actives.forEach { key(it.id) { ActiveTripCard(it) } }
                } else PresenceCard()
            }

            Row(Modifier.fillMaxWidth().appearIn(2), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("أرباح اليوم", todayEarnings.moneyShort, "banknote.fill", Modifier.weight(1f))
                StatTile("طلبات اليوم", "${todayOrders.size}", "shippingbox.fill", Modifier.weight(1f), tint = MB.red)
            }
            Row(Modifier.fillMaxWidth().appearIn(3), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("تقييمك",
                    if (d?.rating == 0.0) "—" else "%.1f".format(Locale.US, d?.rating ?: 0.0),
                    "star.fill", Modifier.weight(1f), tint = orange)
                StatTile("نسبة القبول", "${((d?.acceptanceRate ?: 0.0) * 100).toInt()}٪",
                    "checkmark.circle.fill", Modifier.weight(1f))
            }

            if (d != null) {
                DriverPreferencesCard(d, Modifier.appearIn(4))
                DriverTierCard(d, expanded = false, modifier = Modifier.appearIn(5))
            }

            // الخريطة
            Box(Modifier.fillMaxWidth().appearIn(6).clip(RoundedCornerShape(Metrics.cardRadius))) {
                MapCanvas(
                    pickup = null, dropoff = null,
                    modifier = Modifier.fillMaxWidth().height(175.dp),
                    driver = d?.coord, driverName = d?.name,
                    nearby = store.drivers.filter { it.id != d?.id && it.presence == DriverPresence.online }.map { it.coord },
                    showRoute = false, interactive = false,
                    driverIcon = com.jaynak.app.core.LiveTracking.myIcon,
                )
                Row(
                    Modifier.align(Alignment.TopEnd).padding(12.dp)
                        .glassChip().padding(horizontal = 11.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    SFIcon("location.fill", size = 10.dp, tint = MB.ink)
                    Text(d?.zone ?: "", style = MBType.micro, color = MB.ink)
                }
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("آخر رحلاتك", symbol = "clock.arrow.circlepath")
                if (todayOrders.isEmpty()) {
                    EmptyStateView("car", "لا رحلات اليوم", "شغّل حالة «متصل» لتستلم طلبات")
                } else {
                    todayOrders.take(3).forEach { OrderRow(it) }
                }
            }
            Spacer(Modifier.height(150.dp))
        }
    }

    if (showSupport) {
        ChatView("support-${d?.id ?: "x"}", "الدعم الفني", "", com.jaynak.app.core.UserRole.driver,
            moderatable = false, onDismiss = { showSupport = false })
    }
}

@Composable
private fun PresenceCard() {
    val store = LocalStore.current
    val d = store.currentDriver
    val online = d?.presence == DriverPresence.online
    GlassCard(Modifier.fillMaxWidth(), tint = if (online) MB.green.copy(alpha = 0.18f) else null) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (online) "أنت متصل" else "أنت غير متصل", style = MBType.ar(20, FontWeight.Bold), color = MB.ink)
                    Text(if (online) "نرسل لك الطلبات القريبة منك" else "لن تصلك طلبات حتى تتصل",
                        style = MBType.caption, color = MB.ink2)
                }
                Spacer(Modifier.weight(1f))
                SFIcon(if (online) "dot.radiowaves.left.and.right" else "moon.zzz.fill", size = 26.dp,
                    tint = if (online) MB.green else MB.ink3)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DriverPresence.values().forEach { p ->
                    val sel = d?.presence == p
                    Box(
                        Modifier.weight(1f)
                            .alpha(if (p == DriverPresence.busy) 0.5f else 1f)
                            .glassSurface(14.dp, null, 0.06f)
                            .background(if (sel) MB.green else Color.Transparent, RoundedCornerShape(14.dp))
                            .pressable(enabled = p != DriverPresence.busy) { store.setPresence(p) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(p.title, style = MBType.callout, color = if (sel) Color.White else MB.ink)
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveTripCard(order: Order) {
    val store = LocalStore.current
    val context = LocalContext.current
    var showChat by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }
    val customer = store.customers.firstOrNull { it.id == order.customerID }
    val toDropoff = order.status == OrderStatus.pickedUp || order.status == OrderStatus.delivering
    val target = if (toDropoff) order.dropoff else order.pickup
    val actionTitle = when (order.status) {
        OrderStatus.pickedUp -> "بدأت التوصيل"
        OrderStatus.delivering -> "سلّمت الطلب"
        else -> "استلمت الطلب"
    }
    var showConfirm by remember { mutableStateOf(false) }
    var showProof by remember { mutableStateOf(false) }
    val extras = com.jaynak.app.core.OrderExtras.of(order.remoteID)
    val recipientShown = order.recipientName.ifEmpty { extras?.recipientName ?: "" }

    GlassCard(Modifier.fillMaxWidth(),
        tint = MB.red.copy(alpha = if (order.dispatchMode == com.jaynak.app.core.DispatchMode.nearby) 0.34f else 0.16f)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(order.status)
                Spacer(Modifier.weight(1f))
                Text(order.price.driverPayout.money, style = MBType.num(17, FontWeight.Bold), color = MB.green)
            }

            if (order.dispatchMode == com.jaynak.app.core.DispatchMode.nearby)
                Text("🚨 طلب سريع جداً", style = MBType.callout, color = MB.red)
            OrderProgressTrack(order.status.step, Modifier.fillMaxWidth())

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    SFIcon("arrow.up.circle.fill", size = 18.dp, tint = MB.green)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(order.pickup.name, style = MBType.callout, color = MB.ink)
                        if (order.pickup.details.isNotEmpty()) Text(order.pickup.details, style = MBType.micro, color = MB.ink3)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    SFIcon("flag.checkered", size = 18.dp, tint = MB.red)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(order.dropoff.name, style = MBType.callout, color = MB.ink)
                        if (recipientShown.isNotEmpty())
                            Text("المستلم: $recipientShown" + (if (order.recipientPhone.isNotEmpty()) " · ${order.recipientPhone}" else ""),
                                style = MBType.micro, color = MB.ink3)
                    }
                }
                val customerName = customer?.name ?: extras?.customerName ?: ""
                if (customerName.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.jaynak.app.design.Avatar(customerName, size = 30.dp)
                        Text(if (customer != null) "$customerName · ${AppConfig.countryDialCode} ${customer.phone}" else customerName,
                            style = MBType.micro, color = MB.ink2)
                    }
                }
                if (order.note.isNotEmpty()) Text("ملاحظة: ${order.note}", style = MBType.micro, color = MB.ink2)
                if (order.payment == PaymentMethod.cash) {
                    MBChip("اقبض كاش ${order.price.total.moneyShort}", symbol = "banknote.fill", tint = MB.red, filled = true)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).glassSurface(23.dp, null, 0.08f)
                        .pressable { NavigationLauncher.open(context, target.coord, target.name) }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SFIcon("location.north.line.fill", size = 14.dp, tint = MB.green)
                    Text("الملاحة", style = MBType.callout, color = MB.green)
                }
                GlassIconButton("phone.fill", { customer?.let { NavigationLauncher.call(context, it.phone) } },
                    size = 46.dp, iconSize = 15.dp, tint = MB.ink)
                Box {
                    GlassIconButton("message.fill", { showChat = true }, size = 46.dp, iconSize = 15.dp, tint = MB.ink)
                    com.jaynak.app.design.UnreadBadge(store.unread("order-${order.id}"), Modifier.align(Alignment.TopEnd))
                }
            }

            PrimaryCTA(actionTitle, onClick = { showConfirm = true }, symbol = "checkmark.circle.fill")
            if (order.status == com.jaynak.app.core.OrderStatus.pickingUp ||
                order.status == com.jaynak.app.core.OrderStatus.assigned) {
                Text(
                    "إلغاء الطلب (قبل الاستلام)", style = MBType.callout, color = MB.red,
                    modifier = Modifier.fillMaxWidth().background(MB.red.copy(alpha = 0.12f), androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .pressable { showCancel = true }.padding(vertical = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }

    if (showConfirm) {
        com.jaynak.app.design.ConfirmDialog(
            title = "تأكيد: " + actionTitle + "؟",
            message = when (order.status) {
                OrderStatus.pickedUp -> "سيظهر للعميل أنك في الطريق إليه بالطلب."
                OrderStatus.delivering -> "بعد التأكيد ينتهي الطلب ويُحتسب لك."
                else -> "سيظهر للعميل أنك استلمت الطلب."
            },
            confirmText = "تأكيد",
            cancelText = "تراجع",
            onConfirm = {
                if (order.status == OrderStatus.pickedUp) store.advance(order) else showProof = true
            },
            onDismiss = { showConfirm = false },
        )
    }

    if (showProof) {
        com.jaynak.app.features.shared.ProofSheet(
            remoteID = order.remoteID,
            stage = if (order.status == OrderStatus.delivering) "delivery" else "pickup",
            onDone = { store.advance(order) },
            onDismiss = { showProof = false },
        )
    }

    if (showCancel) {
        com.jaynak.app.design.ActionDialog(
            title = "إلغاء الطلب؟",
            message = store.pointsRules.let { r ->
                if (r.enabled && r.dCancelAfter > 0) "سيعود الطلب لمندوب آخر ويُخصم منك ${r.dCancelAfter} نقطة. بعد الاستلام لا يمكن الإلغاء."
                else "سيعود الطلب لمندوب آخر. بعد الاستلام لا يمكن الإلغاء." },
            actions = listOf("لا أستطيع الوصول", "تعطّلت مركبتي", "سبب آخر").map { r ->
                com.jaynak.app.design.DialogAction(r, destructive = true) {
                    store.cancelOrder(order, r, com.jaynak.app.core.UserRole.driver)
                }
            },
            onDismiss = { showCancel = false },
        )
    }

    if (showChat) {
        ChatView("order-${order.id}", customer?.name ?: "العميل", order.ref,
            com.jaynak.app.core.UserRole.driver, moderatable = true, onDismiss = { showChat = false })
    }
}
