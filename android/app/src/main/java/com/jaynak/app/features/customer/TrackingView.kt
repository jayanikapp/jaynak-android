package com.jaynak.app.features.customer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.Coord
import com.jaynak.app.core.Driver
import com.jaynak.app.core.Geo
import com.jaynak.app.core.LiveTracking
import com.jaynak.app.core.OrdersService
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.ImpactStyle
import com.jaynak.app.core.Order
import com.jaynak.app.core.OrderStatus
import com.jaynak.app.core.PaymentMethod
import com.jaynak.app.core.UserRole
import com.jaynak.app.core.money
import com.jaynak.app.design.ActionDialog
import com.jaynak.app.design.Avatar
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.DialogAction
import com.jaynak.app.design.GlassButton
import com.jaynak.app.design.GlassIconButton
import com.jaynak.app.design.KeyValueRow
import com.jaynak.app.design.LoadingDots
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.OrderProgressTrack
import com.jaynak.app.design.ProminentButton
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SecondaryCTA
import com.jaynak.app.design.SheetGrabber
import com.jaynak.app.design.StarRating
import com.jaynak.app.design.glassChip
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.projectMomentum
import com.jaynak.app.design.rubberband
import com.jaynak.app.features.shared.ChatView
import com.jaynak.app.features.shared.MapCanvas
import com.jaynak.app.features.shared.NavigationLauncher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** مقابل TrackingView.swift. */
@Composable
fun TrackingView(orderID: String, onClose: () -> Unit) {
    val store = LocalStore.current
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()

    var showChat by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }
    val sheetOffset = remember { Animatable(0f) }          // dp
    var dragStart by remember { mutableStateOf(0f) }
    val collapsedDrop = 230f

    val order = store.orders.firstOrNull { it.id == orderID }
    val driver: Driver? = order?.driverID?.let { did -> store.drivers.firstOrNull { it.id == did } }

    // تتبّع حيّ من السيرفر: موقع المندوب الحقيقي + المسار + المسافة
    var live by remember { mutableStateOf<LiveTracking.Track?>(null) }
    val remoteID = order?.remoteID
    val isLive = order?.status?.isLive == true
    LaunchedEffect(remoteID, isLive) {
        if (remoteID == null || !isLive) return@LaunchedEffect
        while (true) {
            if (OrdersService.isAvailable) {
                runCatching { LiveTracking.track(remoteID) }.getOrNull()?.let { live = it }
            }
            delay(4000)
        }
    }
    val liveLoc = live?.loc
    // بيانات المندوب الحقيقية من السيرفر (اسم + رقم) حتى لو لم يكن في قائمة هذا الجهاز
    val driverShown: Driver? = when {
        driver != null -> driver.copy(
            name = live?.driverName?.takeIf { it.isNotBlank() } ?: driver.name,
            phone = live?.driverPhone?.takeIf { it.isNotBlank() } ?: driver.phone)
        !live?.driverName.isNullOrBlank() ->
            Driver(name = live!!.driverName!!, phone = live?.driverPhone ?: "", coord = Coord(0.0, 0.0))
        else -> null
    }
    val carCoord: Coord? = liveLoc?.coord ?: order?.driverCoord
    val targetCoord: Coord? = order?.let {
        if (live?.target == "dropoff" || it.status == OrderStatus.pickedUp || it.status == OrderStatus.delivering) it.dropoff.coord
        else it.pickup.coord
    }
    val distanceKm: Double? = live?.distanceKm
        ?: if (carCoord != null && targetCoord != null) carCoord.km(targetCoord) * 1.3 else null
    val etaMin: Int? = live?.etaMin
        ?: distanceKm?.let { kotlin.math.max(1, (it / 30.0 * 60).toInt()) }
    val routeLine: List<Coord>? = live?.route?.takeIf { it.size > 1 }?.let { r ->
        if (live?.target == "pickup" && order != null) r + Geo.route(order.pickup.coord, order.dropoff.coord) else r
    }

    LaunchedEffect(order?.status) {
        val s = order?.status ?: return@LaunchedEffect
        if (s == OrderStatus.delivered || s == OrderStatus.cancelled) { delay(1200); onClose() }
    }

    Box(Modifier.fillMaxSize()) {
        if (order != null) {
            MapCanvas(pickup = order.pickup, dropoff = order.dropoff,
                driver = carCoord, driverName = driver?.name,
                driverHeading = liveLoc?.heading, routePath = routeLine,
                driverStale = liveLoc?.stale == true, driverIcon = liveLoc?.icon ?: 0)

            // الشريط العلوي
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp).padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassIconButton("chevron.down", { Feedback.impact(ImpactStyle.light); onClose() }, tint = MB.ink)
                Spacer(Modifier.weight(1f))
                if (carCoord != null && distanceKm != null && order.status.isLive) {
                    Row(
                        Modifier.glassChip().padding(horizontal = 13.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        SFIcon("car.fill", size = 13.dp, tint = MB.red)
                        Text("يبعد ${LiveTracking.kmText(distanceKm)}", style = MBType.ar(13, FontWeight.Bold), color = MB.ink)
                    }
                }
                Row(
                    Modifier.glassChip().padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(Modifier.size(7.dp).background(MB.green, CircleShape))
                    Text(order.ref, style = MBType.num(13, FontWeight.SemiBold), color = MB.ink)
                }
            }

            // اللوحة السفلية القابلة للسحب
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .offset { IntOffset(0, (sheetOffset.value * density).toInt()) }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            val raw = sheetOffset.value + delta / density
                            // تقريب لسلوك rubberband: خارج الحدود نخفّف الحركة
                            val shown = when {
                                raw < 0 -> -rubberband((-raw).toDouble(), 260.0).toFloat()
                                raw > collapsedDrop -> collapsedDrop + rubberband((raw - collapsedDrop).toDouble(), 200.0).toFloat()
                                else -> raw
                            }
                            scope.launch { sheetOffset.snapTo(shown) }
                        },
                        onDragStopped = { velocity ->
                            val projected = sheetOffset.value +
                                projectMomentum((velocity / density).toDouble(), 0.99).toFloat()
                            val target = if (projected > collapsedDrop / 2) collapsedDrop else 0f
                            dragStart = target
                            Feedback.impact(ImpactStyle.light)
                            sheetOffset.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 300f))
                        },
                    )
            ) { Panel(order, driverShown, distanceKm, etaMin, liveLoc?.stale == true, onChat = { showChat = true }, onCancel = { showCancel = true }) }
        } else {
            BrandBackground()
            OrderClosedView(onClose)
        }
    }

    if (showChat && order != null) {
        ChatView(
            threadID = "order-${order.id}", title = driver?.name ?: "المندوب", subtitle = order.ref,
            role = UserRole.customer, moderatable = true, onDismiss = { showChat = false })
    }

    if (showCancel) {
        ActionDialog(
            title = "إلغاء الطلب؟",
            message = store.pointsRules.let { r ->
                if (r.enabled && (r.cCancelBefore > 0 || r.cCancelAfter > 0))
                    "الإلغاء قبل قبول المندوب يخصم ${r.cCancelBefore} نقطة، وبعد القبول يخصم ${r.cCancelAfter} نقطة."
                else "الإلغاء بعد تعيين المندوب قد يترتّب عليه رسم." },
            actions = listOf("تغيّرت خطتي", "المندوب تأخّر", "أخطأت في العنوان", "سبب آخر").map { r ->
                DialogAction(r, destructive = true) { order?.let { store.cancelOrder(it, r, UserRole.customer) } }
            },
            onDismiss = { showCancel = false },
        )
    }
}

@Composable
private fun Panel(o: Order, driver: Driver?, distanceKm: Double?, etaMin: Int?, stale: Boolean, onChat: () -> Unit, onCancel: () -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp).padding(bottom = 6.dp)
            .glassSurface(32.dp, null, 0.30f, underlay = Color.Black.copy(alpha = 0.42f))
            .padding(horizontal = 20.dp).padding(bottom = 14.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SheetGrabber()

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)) {
                Text(o.status.title, style = MBType.ar(21, FontWeight.Bold), color = MB.ink)
                Text(etaText(o), style = MBType.caption, color = MB.ink2)
                if (distanceKm != null && o.status.isLive && o.status != OrderStatus.searching) {
                    Text(
                        if (stale) "آخر موقع للمندوب قديم — قد يكون في منطقة ضعيفة التغطية"
                        else "المندوب يبعد ${LiveTracking.kmText(distanceKm)} عن ${
                            if (o.status == OrderStatus.pickedUp || o.status == OrderStatus.delivering) "وجهتك" else "نقطة الاستلام"}",
                        style = MBType.micro, color = if (stale) MB.red else MB.green)
                }
            }
            if (o.status == OrderStatus.searching) LoadingDots()
            else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("${etaMin ?: o.etaMinutes}", style = MBType.num(26, FontWeight.Bold), color = MB.green)
                Text("دقيقة", style = MBType.micro, color = MB.ink2)
                if (distanceKm != null) Text(LiveTracking.kmText(distanceKm), style = MBType.num(12, FontWeight.SemiBold), color = MB.ink)
            }
        }

        OrderProgressTrack(o.status.step, Modifier.fillMaxWidth())

        if (o.status == OrderStatus.searching) SearchingPanel(o)
        else if (driver != null) DriverBlock(driver, onChat, com.jaynak.app.design.LocalStore.current.unread("order-${o.id}"))

        Box(Modifier.fillMaxWidth().height(1.dp).background(MB.hairline.copy(alpha = 0.3f)))

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyValueRow("من", o.pickup.name, isNumber = false)
            KeyValueRow("إلى", o.dropoff.name, isNumber = false)
            KeyValueRow(if (o.payment == PaymentMethod.wallet) "مدفوع من المحفظة" else "كاش عند التسليم",
                o.price.total.money, bold = true)
        }

        if (o.status.isLive) SecondaryCTA("إلغاء الطلب", onCancel, symbol = "xmark.circle", tint = MB.red)
    }
}

@Composable
private fun DriverBlock(d: Driver, onChat: () -> Unit, unread: Int = 0) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth().glassSurface(20.dp, null, 0.10f).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(d.name, size = 46.dp, tint = MB.red)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(d.name, style = MBType.headline, color = MB.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (d.phone.isNotBlank()) Text("+968 " + d.phone.filter { it.isDigit() }.takeLast(8),
                    style = MBType.num(13, FontWeight.SemiBold), color = MB.green)
                StarRating(d.rating, count = d.ratingCount, size = 10.dp)
                Text(vehicleLine(d), style = MBType.micro, color = MB.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProminentButton("اتصال", { NavigationLauncher.call(ctx, d.phone) }, Modifier.weight(1f),
                symbol = "phone.fill", vertical = 11.dp)
            GlassButton("واتساب", { NavigationLauncher.whatsapp(ctx, d.phone) }, Modifier.weight(1f), tint = MB.green,
                symbol = "message.fill", vertical = 11.dp)
            Box(Modifier.weight(1f)) {
                GlassButton("محادثة", onChat, Modifier.fillMaxWidth(), tint = MB.ink,
                    symbol = "bubble.left.fill", vertical = 11.dp)
                com.jaynak.app.design.UnreadBadge(unread, Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

private fun vehicleLine(d: Driver): String =
    listOf(d.vehicle.model, d.vehicle.color, d.vehicle.plateNumber).filter { it.isNotEmpty() }.joinToString(" · ")

private fun etaText(o: Order): String = when (o.status) {
    OrderStatus.searching -> "نبحث عن أقرب مندوب متصل"
    OrderStatus.assigned, OrderStatus.pickingUp -> "المندوب في الطريق لنقطة الاستلام"
    OrderStatus.pickedUp -> "استلم الطلب من ${o.pickup.name}"
    OrderStatus.delivering -> "في الطريق إلى ${o.dropoff.name}"
    OrderStatus.delivered -> "تم التسليم — شكراً لك"
    OrderStatus.cancelled -> o.cancelledReason ?: "أُلغي الطلب"
}
