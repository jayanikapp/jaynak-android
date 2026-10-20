package com.jaynak.app.features.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.Fmt
import com.jaynak.app.core.Order
import com.jaynak.app.core.OrderStatus
import com.jaynak.app.core.UserRole
import com.jaynak.app.core.money
import com.jaynak.app.design.Avatar
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.EmptyStateView
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.KeyValueRow
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.NavBar
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.SegmentedPicker
import com.jaynak.app.design.StarRating
import com.jaynak.app.design.StatusBadge
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.design.pressable
import com.jaynak.app.features.auth.GuestGate
import com.jaynak.app.features.customer.OrderRow

private enum class OrderFilter(val title: String) {
    all("الكل"), live("جارية"), delivered("مكتملة"), cancelled("ملغية")
}

/** مقابل OrdersHistoryView.swift. */
@Composable
fun OrdersHistoryView(role: UserRole) {
    val store = LocalStore.current
    var filterIdx by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Order?>(null) }
    val filter = OrderFilter.values()[filterIdx]

    val all: List<Order> = when (role) {
        UserRole.customer -> {
            val cid = store.session?.customerID
            if (cid == null) emptyList() else store.orders.filter { it.customerID == cid }
        }
        UserRole.driver -> {
            val did = store.session?.driverID
            if (did == null) emptyList() else store.orders.filter { it.driverID == did }
        }
        UserRole.admin -> store.orders
    }
    val mine = when (filter) {
        OrderFilter.all -> all
        OrderFilter.live -> all.filter { it.status.isLive }
        OrderFilter.delivered -> all.filter { it.status == OrderStatus.delivered }
        OrderFilter.cancelled -> all.filter { it.status == OrderStatus.cancelled }
    }

    Box(Modifier.fillMaxSize()) {
        BrandBackground()
        Column(Modifier.fillMaxSize()) {
            NavBar(title = if (role == UserRole.driver) "رحلاتي" else "طلباتي")
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (store.isGuest && role == UserRole.customer) {
                    GuestGate(
                        symbol = "list.bullet.rectangle.fill",
                        title = "سجل طلباتك",
                        message = "سجّل الدخول لتشوف طلباتك السابقة وحالتها وفواتيرها.",
                    )
                    Spacer(Modifier.height(220.dp))
                } else {
                    SegmentedPicker(OrderFilter.values().map { it.title }, filterIdx,
                        { filterIdx = it }, Modifier.padding(top = 6.dp))

                    if (mine.isEmpty()) {
                        EmptyStateView(
                            symbol = "shippingbox",
                            title = "لا طلبات هنا",
                            message = if (filter == OrderFilter.all) "طلباتك ستظهر في هذه الصفحة"
                            else "لا يوجد طلبات بهذه الحالة",
                        )
                    } else {
                        mine.forEach { o ->
                            OrderRow(o, Modifier.pressable { selected = o },
                                showCustomer = role != UserRole.customer)
                        }
                    }
                    Spacer(Modifier.height(150.dp))
                }
            }
        }
    }

    selected?.let { o ->
        OrderDetailSheet(order = o, role = role, onDismiss = { selected = null })
    }
}

/** مقابل OrderDetailSheet. */
@Composable
fun OrderDetailSheet(order: Order, role: UserRole, onDismiss: () -> Unit) {
    val store = LocalStore.current
    // الطلب يُقرأ حيّاً من المتجر ليتحدّث مع المحاكاة
    val live = store.orders.firstOrNull { it.id == order.id } ?: order
    val driver = store.drivers.firstOrNull { it.id == live.driverID }
    val customer = store.customers.firstOrNull { it.id == live.customerID }
    var fullMap by remember { mutableStateOf(false) }
    if (fullMap) OrderFullMap(live) { fullMap = false }

    JSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxSize()) {
            BrandBackground(intensity = 0.5f)
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("إغلاق", style = MBType.callout, color = MB.green,
                        modifier = Modifier.clickableNoRipple(onDismiss))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("تفاصيل الطلب", style = MBType.headline, color = MB.ink)
                    }
                    Spacer(Modifier.padding(horizontal = 20.dp))
                }
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(22.dp))
                        .clickableNoRipple { fullMap = true }) {
                        MapCanvas(
                            pickup = live.pickup, dropoff = live.dropoff,
                            modifier = Modifier.fillMaxSize(),
                            driver = if (live.status.isLive) live.driverCoord else null,
                            interactive = false,
                        )
                        Text("اضغط لفتح الخريطة والتحكم بها", style = MBType.micro, color = Color.White,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 6.dp))
                    }

                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                StatusBadge(live.status)
                                Spacer(Modifier.weight(1f))
                                Text(live.ref, style = MBType.num(14, FontWeight.SemiBold), color = MB.ink)
                            }
                            KeyValueRow("النوع", live.kind.title, isNumber = false)
                            KeyValueRow("من", live.pickup.name, isNumber = false)
                            KeyValueRow("إلى", live.dropoff.name, isNumber = false)
                            if (live.recipientName.isNotEmpty())
                                KeyValueRow("المستلم", live.recipientName, isNumber = false)
                            if (live.recipientPhone.isNotEmpty())
                                KeyValueRow("رقم المستلم", live.recipientPhone)
                            if (live.note.isNotEmpty())
                                KeyValueRow("ملاحظة", live.note, isNumber = false)
                            KeyValueRow("التاريخ", Fmt.dateTimeShort(live.createdAt), isNumber = false)
                            live.cancelledReason?.let {
                                KeyValueRow("سبب الإلغاء", it, valueColor = MB.red, isNumber = false)
                            }
                        }
                    }

                    if (live.remoteID != null) {
                        com.jaynak.app.features.shared.ProofRow(live.remoteID)
                    }

                    if (role != UserRole.customer && customer != null) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SectionHeader("العميل", symbol = "person.fill")
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(customer.name, size = 42.dp)
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(customer.name, style = MBType.headline, color = MB.ink)
                                        Text(customer.phone, style = MBType.num(13, FontWeight.Medium), color = MB.ink2)
                                    }
                                }
                            }
                        }
                    }

                    if (driver != null) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SectionHeader("المندوب", symbol = "car.fill")
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(driver.name, size = 42.dp, tint = MB.red)
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(driver.name, style = MBType.headline, color = MB.ink)
                                        StarRating(driver.rating, count = driver.ratingCount, size = 10.dp)
                                        Text("${driver.vehicle.make} ${driver.vehicle.model} · ${driver.vehicle.plateNumber}",
                                            style = MBType.micro, color = MB.ink2)
                                    }
                                }
                            }
                        }
                    }

                    val p = live.price
                    GlassCard(Modifier.fillMaxWidth(), tint = MB.green.copy(alpha = 0.12f)) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionHeader("الفاتورة", symbol = "doc.text.fill")
                            KeyValueRow("الأساس", p.base.money)
                            KeyValueRow("المسافة", p.kmText(p.distance), isNumber = false)
                            KeyValueRow("الوقت", p.minutesText(p.time), isNumber = false)
                            if (p.zoneSurcharge > 0) KeyValueRow("رسم المنطقة", p.zoneSurcharge.money)
                            if (p.kindAdjustment != 0.0) KeyValueRow(
                                if (p.kindAdjustment < 0) "نوع الطلب (خصم)" else "نوع الطلب (زيادة)", p.signed(p.kindAdjustment),
                                valueColor = if (p.kindAdjustment < 0) MB.green else MB.red, isNumber = false)
                            if (p.minFareTopUp > 0) KeyValueRow("الحد الأدنى للأجرة", "+" + p.minFareTopUp.money, isNumber = false)
                            if (p.surge > 0) KeyValueRow("ذروة", p.surge.money, valueColor = MB.red)
                            if (p.discount > 0)
                                KeyValueRow("خصم ${p.promoCode ?: ""}", "−" + p.discount.money, valueColor = MB.green)
                            HorizontalDivider(Modifier.alpha(0.3f), color = MB.hairline)
                            KeyValueRow("الإجمالي", p.total.money, bold = true)
                            KeyValueRow("طريقة الدفع", live.payment.title, isNumber = false)
                            if (role != UserRole.customer) {
                                HorizontalDivider(Modifier.alpha(0.3f), color = MB.hairline)
                                KeyValueRow("عمولة المنصة", p.commission.money, valueColor = MB.red)
                                KeyValueRow("صافي المندوب", p.driverPayout.money, valueColor = MB.green, bold = true)
                            }
                        }
                    }

                    live.customerRating?.let { r ->
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionHeader("تقييم العميل", symbol = "star.fill")
                                StarRating(r.toDouble(), size = 15.dp)
                                live.ratingComment?.let {
                                    Text(it, style = MBType.caption, color = MB.ink2)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}


/** خريطة الطلب بملء الشاشة: تحريك وتكبير حر، مع اختيار شكل الخريطة وإعادة التوسيط. */
@Composable
private fun OrderFullMap(o: Order, onClose: () -> Unit) {
    var fitKey by remember { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(MB.sunken)) {
            MapCanvas(
                pickup = o.pickup, dropoff = o.dropoff,
                modifier = Modifier.fillMaxSize(),
                driver = if (o.status.isLive) o.driverCoord else null,
                interactive = true, followDriver = false, fitKey = fitKey,
            )
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    .clickableNoRipple(onClose), contentAlignment = Alignment.Center) {
                    Text("✕", style = MBType.callout, color = Color.White)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(o.ref, style = MBType.num(15, FontWeight.Bold), color = Color.White,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 8.dp))
                }
                Box(Modifier.size(42.dp))
            }
            MapStyleSwitch(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("إظهار المسار كاملاً", style = MBType.ar(13, FontWeight.SemiBold), color = Color.White,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(50))
                        .clickableNoRipple { fitKey++ }
                        .padding(horizontal = 18.dp, vertical = 11.dp))
            }
        }
    }
}
