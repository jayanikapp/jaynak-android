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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.Driver
import com.jaynak.app.core.DriverApproval
import com.jaynak.app.core.DriverPresence
import com.jaynak.app.core.Id
import com.jaynak.app.core.Order
import com.jaynak.app.core.OrderStatus
import com.jaynak.app.core.UserRole
import com.jaynak.app.core.moneyShort
import com.jaynak.app.core.nowMs
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.KeyValueRow
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBToggle
import com.jaynak.app.design.MBType
import com.jaynak.app.design.RoleTone
import com.jaynak.app.design.RoleToneScope
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SecondaryCTA
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.TabItem
import com.jaynak.app.design.TabScaffold
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.features.shared.ChatView
import com.jaynak.app.features.shared.OrdersHistoryView
import com.jaynak.app.features.shared.RatingSheet
import kotlinx.coroutines.delay

/** مقابل DriverRoot.swift. */
@Composable
fun DriverRoot() {
    val store = LocalStore.current
    var browsingWhilePending by remember { mutableStateOf(false) }
    val skippedRatings = remember { mutableStateListOf<Id>() }

    val driver = store.currentDriver
    val offer = store.pendingOfferForCurrentDriver
    val pendingRating: Order? = run {
        val did = store.session?.driverID ?: return@run null
        store.orders.firstOrNull {
            it.driverID == did && it.status == OrderStatus.delivered &&
                it.driverRating == null && !skippedRatings.contains(it.id) &&
                (nowMs() - (it.deliveredAt ?: 0L)) / 1000.0 < 1800
        }
    }

    // يستطلع عروض الخادم كل ٨ ثوانٍ ما دامت شاشة المندوب ظاهرة.
    LaunchedEffect(Unit) {
        while (true) {
            store.refreshDriverFeed()
            delay(3000)
        }
    }

    RoleToneScope(RoleTone.driver) {
        Box(Modifier.fillMaxSize()) {
            when {
                driver == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MB.green)
                }
                !driver.docs.isComplete -> DriverOnboardingView(driver)
                driver.approval != DriverApproval.approved && !browsingWhilePending ->
                    ApprovalStatusView(driver) { browsingWhilePending = true }
                else -> DriverTabs(driver)
            }

            if (offer != null) DriverOfferSheet(offer)

            pendingRating?.let { order ->
                RatingSheet(
                    order = order, role = UserRole.driver,
                    counterpartName = store.customers.firstOrNull { it.id == order.customerID }?.name ?: "العميل",
                    onDismiss = { skippedRatings.add(order.id) },
                )
            }
        }
    }
}

@Composable
private fun DriverTabs(d: Driver) {
    val store = LocalStore.current
    var tab by remember { mutableStateOf(0) }
    val active = store.activeOrderForCurrentDriver

    TabScaffold(
        tabs = listOf(
            TabItem("الرئيسية", "house.fill"),
            TabItem("الطلبات", "list.bullet.rectangle.portrait.fill"),
            TabItem("رحلاتي", "clock.arrow.circlepath"),
            TabItem("أرباحي", "banknote.fill"),
            TabItem("حسابي", "person.crop.circle.fill"),
        ),
        selected = tab,
        onSelect = { tab = it },
        accessory = {
            if (active != null) LiveTripPill(active) else PresenceAccessory(d)
        },
    ) { i ->
        when (i) {
            0 -> DriverHome(onOpenTrip = { tab = 0 })
            1 -> OpenOrdersView()
            2 -> OrdersHistoryView(role = UserRole.driver)
            3 -> EarningsView()
            else -> DriverProfileView()
        }
    }
}

@Composable
fun PresenceAccessory(driver: Driver) {
    val store = LocalStore.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(if (driver.presence == DriverPresence.online) MB.green else MB.ink3, CircleShape))
        Text(if (store.hasActiveExpress) "مشغول (طلب سريع)" else driver.presence.title, style = MBType.ar(14, FontWeight.SemiBold), color = MB.ink,
            modifier = Modifier.weight(1f))
        MBToggle(driver.presence != DriverPresence.offline,
            { store.setPresence(if (it) DriverPresence.online else DriverPresence.offline) })
    }
}

@Composable
fun LiveTripPill(order: Order) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).background(MB.red.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
            SFIcon("car.fill", size = 13.dp, tint = MB.red)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(order.status.title, style = MBType.ar(14, FontWeight.SemiBold), color = MB.ink)
            Text(order.ref, style = MBType.num(11, FontWeight.Medium), color = MB.ink2)
        }
        Text(order.price.driverPayout.moneyShort, style = MBType.num(15, FontWeight.Bold), color = MB.green)
    }
}

@Composable
fun ApprovalStatusView(driver: Driver, onBrowse: () -> Unit) {
    val store = LocalStore.current
    var showSupport by remember { mutableStateOf(false) }

    val tint = when (driver.approval) {
        DriverApproval.approved -> MB.green
        DriverApproval.pending -> Color(0xFFFF9500)
        else -> MB.red
    }
    val symbol = when (driver.approval) {
        DriverApproval.approved -> "checkmark.seal.fill"
        DriverApproval.pending -> "clock.badge.checkmark"
        DriverApproval.rejected -> "xmark.seal.fill"
        DriverApproval.suspended -> "hand.raised.fill"
    }
    val message = when (driver.approval) {
        DriverApproval.pending -> "استلمنا بياناتك ومستنداتك. المراجعة تستغرق عادة أقل من ٢٤ ساعة، وسنبلّغك على واتساب."
        DriverApproval.rejected -> "لم تُعتمد بياناتك. عدّل المستندات المطلوبة وأعد الإرسال."
        DriverApproval.suspended -> "حسابك موقوف مؤقتاً. تواصل مع الدعم لمعرفة التفاصيل."
        DriverApproval.approved -> "حسابك معتمد — يمكنك استلام الطلبات."
    }

    Box(Modifier.fillMaxSize()) {
        BrandBackground(intensity = 1.1f)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(80.dp))

            Box(Modifier.size(110.dp).background(tint.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                SFIcon(symbol, size = 44.dp, tint = tint)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(driver.approval.title, style = MBType.title, color = MB.ink)
                Text(message, style = MBType.body, color = MB.ink2, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 30.dp))
            }

            driver.rejectionNote?.let { note ->
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp), tint = MB.red.copy(alpha = 0.14f)) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                        Text("ملاحظة الإدارة", style = MBType.callout, color = MB.ink)
                        Text(note, style = MBType.caption, color = MB.ink2)
                    }
                }
            }

            GlassCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
                    SectionHeader("ملف التسجيل", symbol = "doc.text.fill")
                    KeyValueRow("الاسم", driver.name, isNumber = false)
                    KeyValueRow("الهاتف", driver.phone)
                    KeyValueRow("المنطقة", driver.zone, isNumber = false)
                    KeyValueRow("المركبة", "${driver.vehicle.make} ${driver.vehicle.model}", isNumber = false)
                    KeyValueRow("اللوحة", driver.vehicle.plateDisplay)
                    KeyValueRow("المستندات", if (driver.docs.isComplete) "مكتملة" else "ناقصة",
                        valueColor = if (driver.docs.isComplete) MB.green else MB.red, isNumber = false)
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                SecondaryCTA("استعراض التطبيق", onClick = onBrowse, symbol = "eye.fill")
                SecondaryCTA("تواصل مع الدعم", onClick = { showSupport = true }, symbol = "bubble.left.fill")
                Text("تسجيل الخروج", style = MBType.callout, color = MB.red,
                    modifier = Modifier.clickableNoRipple { store.signOut() })
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showSupport) {
        ChatView("support-${driver.id}", "الدعم الفني", "", UserRole.driver,
            moderatable = false, onDismiss = { showSupport = false })
    }
}
