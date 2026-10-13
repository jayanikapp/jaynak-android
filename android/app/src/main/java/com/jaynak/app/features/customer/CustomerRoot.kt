package com.jaynak.app.features.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.ImpactStyle
import com.jaynak.app.core.Id
import com.jaynak.app.core.Order
import com.jaynak.app.core.OrderKind
import com.jaynak.app.core.OrderStatus
import com.jaynak.app.core.UserRole
import com.jaynak.app.core.nowMs
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.FullScreenCover
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.LoadingDots
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.RoleTone
import com.jaynak.app.design.RoleToneScope
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SuccessBurst
import com.jaynak.app.design.TabItem
import com.jaynak.app.design.TabScaffold
import com.jaynak.app.design.pressable
import com.jaynak.app.features.shared.OrdersHistoryView
import com.jaynak.app.features.shared.RatingSheet

/** مقابل CustomerRoot.swift. */
@Composable
fun CustomerRoot() {
    val store = LocalStore.current
    RoleToneScope(RoleTone.customer) {
        if (store.isGuest) GuestWelcomeView() else SignedIn()
    }
}

@Composable
private fun SignedIn() {
    val store = LocalStore.current
    var tab by remember { mutableStateOf(0) }
    var showNewOrder by remember { mutableStateOf(false) }
    var showTracking by remember { mutableStateOf(false) }
    var preselectedKind by remember { mutableStateOf(OrderKind.documents) }
    val skippedRatings = remember { mutableStateListOf<Id>() }

    val live = store.liveOrderForCurrentCustomer
    val pendingRating: Order? = run {
        val cid = store.session?.customerID ?: return@run null
        store.orders.firstOrNull {
            it.customerID == cid && it.status == OrderStatus.delivered &&
                it.customerRating == null && !skippedRatings.contains(it.id) &&
                (nowMs() - (it.deliveredAt ?: 0L)) / 1000.0 < 1800
        }
    }

    Box(Modifier.fillMaxSize()) {
        TabScaffold(
            tabs = listOf(
                TabItem("الرئيسية", "house.fill"),
                TabItem("طلباتي", "list.bullet.rectangle.fill"),
                TabItem("المحفظة", "creditcard.fill"),
                TabItem("نقاطي", "star.circle.fill"),
                TabItem("حسابي", "person.crop.circle.fill"),
            ),
            selected = tab,
            onSelect = { tab = it },
            accessory = {
                when {
                    live != null -> LiveOrderPill(live) { showTracking = true }
                    store.isGuest -> GuestPromoPill()
                    else -> QuickOrderPill { showNewOrder = true }
                }
            },
        ) { i ->
            when (i) {
                0 -> CustomerHome(
                    onNewOrder = { kind -> preselectedKind = kind; showNewOrder = true },
                    onOpenLive = { showTracking = true },
                )
                1 -> OrdersHistoryView(role = UserRole.customer)
                2 -> WalletView()
                3 -> LoyaltyView()
                else -> CustomerProfileView()
            }
        }

        FullScreenCover(visible = showNewOrder, onDismiss = { showNewOrder = false }) {
            NewOrderView(
                initialKind = preselectedKind,
                onClose = { showNewOrder = false },
            ) { _ ->
                showNewOrder = false
                showTracking = true
            }
        }

        FullScreenCover(visible = showTracking, onDismiss = { showTracking = false }) {
            if (live != null) TrackingView(orderID = live.id, onClose = { showTracking = false })
            else OrderClosedView { showTracking = false }
        }
    }

    pendingRating?.let { order ->
        RatingSheet(
            order = order, role = UserRole.customer,
            counterpartName = store.drivers.firstOrNull { it.id == order.driverID }?.name ?: "المندوب",
            onDismiss = { skippedRatings.add(order.id) },
        )
    }
}

// ───────── الأشرطة السفلية ─────────

@Composable
private fun PillRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun LiveOrderPill(order: Order, onTap: () -> Unit) {
    Box(Modifier.fillMaxWidth().pressable { Feedback.impact(ImpactStyle.light); onTap() }) {
        PillRow {
            Box(Modifier.size(30.dp).background(MB.green.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center) {
                SFIcon(if (order.status == OrderStatus.searching) "magnifyingglass" else "car.fill",
                    size = 13.dp, tint = MB.green)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(order.status.title, style = MBType.ar(14, FontWeight.SemiBold), color = MB.ink)
                Text("${order.ref} · ${order.dropoff.name}", style = MBType.micro, color = MB.ink2,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (order.status == OrderStatus.searching) LoadingDots()
            else Text("${order.etaMinutes} د", style = MBType.num(15, FontWeight.Bold), color = MB.green)
        }
    }
}

@Composable
fun GuestPromoPill() {
    val store = LocalStore.current
    Box(Modifier.fillMaxWidth().pressable {
        store.requestLogin(reason = "سجّل الدخول واستفد من خصم ٢٥٪ على أول طلب")
    }) {
        PillRow {
            Box(Modifier.size(30.dp).background(MB.red.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center) { SFIcon("ticket.fill", size = 13.dp, tint = MB.red) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("خصم ٢٥٪ على أول طلب", style = MBType.ar(14, FontWeight.SemiBold), color = MB.ink)
                Text("سجّل دخولك بثانية واحدة", style = MBType.micro, color = MB.ink2)
            }
            SFIcon("chevron.forward", size = 12.dp, tint = MB.green)
        }
    }
}

@Composable
fun QuickOrderPill(onTap: () -> Unit) {
    Box(Modifier.fillMaxWidth().pressable(onClick = onTap)) {
        PillRow {
            Box(Modifier.size(30.dp).background(MB.green.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center) { SFIcon("plus", size = 14.dp, tint = MB.green) }
            Text("اطلب مندوب الآن", style = MBType.ar(14, FontWeight.SemiBold), color = MB.ink,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            SFIcon("chevron.forward", size = 12.dp, tint = MB.green)
        }
    }
}

@Composable
fun OrderClosedView(onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        BrandBackground()
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SuccessBurst()
            Text("انتهى الطلب", style = MBType.title, color = MB.ink)
            PrimaryCTA("تمام", onClick = onDismiss, modifier = Modifier.padding(horizontal = 40.dp))
        }
    }
}
