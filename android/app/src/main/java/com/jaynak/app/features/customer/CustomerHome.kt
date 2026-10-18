package com.jaynak.app.features.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.Coord
import com.jaynak.app.core.DriverPresence
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.Fmt
import com.jaynak.app.core.ImpactStyle
import com.jaynak.app.core.Order
import com.jaynak.app.core.OrderKind
import com.jaynak.app.core.UserRole
import com.jaynak.app.core.money
import com.jaynak.app.design.Avatar
import com.jaynak.app.design.BrandMark
import com.jaynak.app.design.Elevation
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.GlassIconButton
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBType
import com.jaynak.app.design.Metrics
import com.jaynak.app.design.OrderProgressTrack
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.ProminentButton
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.StarRating
import com.jaynak.app.design.StatusBadge
import com.jaynak.app.design.appearIn
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.pressable
import com.jaynak.app.features.shared.ChatView
import com.jaynak.app.features.shared.MapCanvas
import com.jaynak.app.features.shared.MapOverlayChip

/** مقابل CustomerHome.swift. */
@Composable
fun CustomerHome(onNewOrder: (OrderKind) -> Unit, onOpenLive: () -> Unit) {
    val store = LocalStore.current
    var showSupport by remember { mutableStateOf(false) }

    val customer = store.currentCustomer
    val live = store.liveOrderForCurrentCustomer
    val recent: List<Order> = store.session?.customerID?.let { cid ->
        store.orders.filter { it.customerID == cid && !it.status.isLive }.take(3)
    } ?: emptyList()
    LaunchedEffect(Unit) {
        com.jaynak.app.core.LocationService.locate()
        while (true) {
            store.refreshNearbyDrivers(com.jaynak.app.core.LocationService.current ?: com.jaynak.app.core.Geo.cityCenter)
            kotlinx.coroutines.delay(8_000)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .statusBarsPadding().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HomeHeader(onSupport = { showSupport = true }, modifier = Modifier.appearIn(0))

            if (live != null) LiveCard(live, onOpenLive, Modifier.appearIn(1))

            OrderCTA(onNewOrder, Modifier.appearIn(if (live == null) 1 else 2))

            if (customer != null && customer.savedPlaces.isNotEmpty()) {
                Column(Modifier.appearIn(3), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("أماكنك", symbol = "star.fill")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(customer.savedPlaces, key = { it.id }) { p ->
                            Column(
                                Modifier.width(132.dp).glassSurface(18.dp, null, 0.08f).padding(13.dp),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                SFIcon("mappin.circle.fill", size = 16.dp, tint = MB.green)
                                Text(p.name, style = MBType.callout, color = MB.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(p.zone, style = MBType.micro, color = MB.ink2)
                            }
                        }
                    }
                }
            }

            if (store.isGuest) HowItWorks(Modifier.appearIn(4)) else PromoCard(Modifier.appearIn(4))

            if (recent.isNotEmpty()) {
                Column(Modifier.appearIn(5), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("آخر طلباتك", symbol = "clock.arrow.circlepath")
                    recent.forEachIndexed { i, o -> OrderRow(o, Modifier.appearIn(5 + i)) }
                }
            }

            Spacer(Modifier.height(Metrics.tabScrollInset))
        }
    }

    if (showSupport) {
        ChatView(
            threadID = "support-${store.session?.customerID ?: "x"}",
            title = "الدعم الفني", subtitle = "نردّ خلال دقائق",
            role = UserRole.customer, moderatable = false,
            onDismiss = { showSupport = false },
        )
    }
}

@Composable
private fun HomeHeader(onSupport: () -> Unit, modifier: Modifier = Modifier) {
    val store = LocalStore.current
    val customer = store.currentCustomer
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (store.isGuest) BrandMark(46.dp) else Avatar(customer?.name ?: "زبون", size = 46.dp)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (store.isGuest) "هلا بك في جاينك"
                else "هلا، ${customer?.name?.split(" ")?.firstOrNull() ?: "بك"}",
                style = MBType.ar(19, FontWeight.Bold), color = MB.ink)
            Text("وين نوصل لك اليوم؟", style = MBType.caption, color = MB.ink2)
        }
        if (store.isGuest) {
            ProminentButton("دخول", onClick = {
                store.requestLogin(reason = "سجّل الدخول لتتابع طلباتك ومحفظتك")
            })
        } else {
            com.jaynak.app.design.SupportPill(onSupport)
        }
    }
}

@Composable
private fun LiveCard(o: Order, onOpenLive: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().pressable(1f, onClick = onOpenLive)) {
        GlassCard(tint = MB.green.copy(alpha = 0.22f)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(o.status)
                    Spacer(Modifier.weight(1f))
                    Text(o.ref, style = MBType.num(13, FontWeight.Medium), color = MB.ink2)
                }
                OrderProgressTrack(o.status.step, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SFIcon("location.fill", size = 12.dp, tint = MB.green)
                    Text(o.dropoff.name, style = MBType.callout, color = MB.ink)
                    Spacer(Modifier.weight(1f))
                    Text("متابعة", style = MBType.callout, color = MB.green)
                    SFIcon("chevron.forward", size = 11.dp, tint = MB.green)
                }
            }
        }
    }
}

@Composable
private fun OrderCTA(onNewOrder: (OrderKind) -> Unit, modifier: Modifier = Modifier) {
    val store = LocalStore.current
    var showFull by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().glassSurface(Metrics.cardRadius)) {
        Box(
            Modifier.fillMaxWidth().height(220.dp)
                .clip(RoundedCornerShape(topStart = Metrics.cardRadius, topEnd = Metrics.cardRadius)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            NearbyMap(Modifier.fillMaxSize())
            MapOverlayChip(Modifier.padding(bottom = 12.dp)) {
                Box(Modifier.size(7.dp).background(MB.green, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text("${store.nearbyCars.size} مندوب متصل ضمن ${store.nearbyRadiusKm.toInt()} كم",
                    style = MBType.micro, color = Color.White)
            }
            Box(
                Modifier.align(Alignment.TopStart).padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .clickable { showFull = true }.padding(9.dp),
            ) { SFIcon("arrow.up.left.and.arrow.down.right", size = 14.dp, tint = Color.White) }
        }
        if (showFull) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showFull = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Box(Modifier.fillMaxSize()) {
                    NearbyMap(Modifier.fillMaxSize())
                    Box(
                        Modifier.align(Alignment.TopStart).statusBarsPadding().padding(14.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .clickable { showFull = false }.padding(horizontal = 16.dp, vertical = 9.dp),
                    ) { Text("إغلاق", style = MBType.micro, color = Color.White) }
                }
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OrderKind.values().forEach { k ->
                    Column(
                        Modifier.weight(1f)
                            .pressable(0.96f) { Feedback.impact(ImpactStyle.light); onNewOrder(k) }
                            .glassSurface(16.dp, null, 0.06f)
                            .padding(vertical = 11.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        SFIcon(k.symbol, size = 17.dp, tint = MB.green)
                        Text(k.title, style = MBType.micro, color = MB.ink)
                    }
                }
            }
            PrimaryCTA("اطلب مندوب الآن", symbol = "plus.circle.fill", onClick = { onNewOrder(OrderKind.documents) })
        }
    }
}

private val steps = listOf(
    "حدّد نقطة الاستلام والتسليم" to "بموقعك الحالي أو من الخريطة",
    "شوف السعر قبل التأكيد" to "محسوب بالمسافة والوقت والولاية",
    "طلبك يوصل لأقرب ٣ مندوبين" to "أول من يقبل يثبت عليه الطلب",
    "تابعه على الخريطة لحظة بلحظة" to "واتصل به أو راسله متى شئت",
)

@Composable
private fun HowItWorks(modifier: Modifier = Modifier) {
    Box(modifier) {
        GlassCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionHeader("كيف يعمل جاينك؟", symbol = "sparkles")
                steps.forEachIndexed { i, step ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(34.dp).background(
                                if (i == 0) MB.greenGradient else SolidColor(MB.green.copy(alpha = 0.14f)), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${i + 1}", style = MBType.num(14, FontWeight.Bold),
                                color = if (i == 0) Color.White else MB.green)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(step.first, style = MBType.callout, color = MB.ink)
                            Text(step.second, style = MBType.micro, color = MB.ink2)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MBChip("خصم ٢٥٪ لأول طلب", symbol = "ticket.fill", tint = MB.red, filled = true)
                    MBChip("تسعير شفّاف", symbol = "checkmark.seal.fill", tint = MB.gold)
                }
            }
        }
    }
}

@Composable
private fun PromoCard(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().glassSurface(22.dp, MB.red.copy(alpha = 0.16f)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(48.dp).background(
                Brush.linearGradient(listOf(MB.red, MB.red.copy(alpha = 0.6f))), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) { SFIcon("ticket.fill", size = 20.dp, tint = Color.White) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("خصم ٢٥٪ على أول طلب", style = MBType.headline, color = MB.ink)
            Text("استخدم كود JAYNAK25 عند التأكيد", style = MBType.caption, color = MB.ink2)
        }
    }
}

/** مقابل OrderRow — يُستعمل أيضاً في سجل الطلبات. */
@Composable
fun OrderRow(order: Order, modifier: Modifier = Modifier, showCustomer: Boolean = false) {
    Row(
        modifier.fillMaxWidth().glassSurface(18.dp, null, 0.08f).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).background(MB.green.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            SFIcon(order.kind.symbol, size = 15.dp, tint = MB.green)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(order.dropoff.name, style = MBType.callout, color = MB.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                Text("·", color = MB.ink3)
                Text(order.ref, style = MBType.num(12, FontWeight.Medium), color = MB.ink3)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.dayMonthTime(order.createdAt), style = MBType.micro, color = MB.ink2)
                order.customerRating?.let { StarRating(it.toDouble(), size = 9.dp) }
            }
        }
        Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(order.price.total.money, style = MBType.num(15, FontWeight.Bold), color = MB.ink)
            StatusBadge(order.status)
        }
    }
}


/** خريطة المندوبين المتصلين القريبين: سيارات خضراء حيّة داخل دائرة نصف القطر التي تضبطها الإدارة. */
@Composable
private fun NearbyMap(modifier: Modifier = Modifier) {
    val store = LocalStore.current
    val me = com.jaynak.app.core.LocationService.current ?: com.jaynak.app.core.Geo.cityCenter
    val cars = store.nearbyCars.map {
        com.jaynak.app.features.shared.CarMarker(it.id, it.coord, it.heading, MB.green, icon = it.icon)
    }
    val fit = listOf(me) + store.nearbyCars.map { it.coord }
    MapCanvas(
        pickup = null, dropoff = null, modifier = modifier, showRoute = false, interactive = true,
        cars = cars, followDriver = false,
        fitPoints = if (fit.size > 1) fit else {
            val d = minOf(store.nearbyRadiusKm, 30.0) / 111.0 * 0.8
            listOf(com.jaynak.app.core.Coord(me.lat + d, me.lon + d), com.jaynak.app.core.Coord(me.lat - d, me.lon - d))
        },
        fitKey = "${store.nearbyCars.size}-${(me.lat * 50).toInt()}",
        radiusKm = store.nearbyRadiusKm, radiusCenter = me,
    )
}
