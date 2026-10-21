package com.jaynak.app.features.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.Coord
import com.jaynak.app.core.Geo
import com.jaynak.app.core.LiveTracking
import com.jaynak.app.core.Order
import com.jaynak.app.core.OrderStatus
import com.jaynak.app.core.OrdersService
import com.jaynak.app.design.GlassButton
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.ProminentButton
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.design.glassChip
import com.jaynak.app.design.glassSurface
import com.jaynak.app.features.shared.CarMarker
import com.jaynak.app.features.shared.MapCanvas
import com.jaynak.app.features.shared.NavigationLauncher
import com.jaynak.app.features.shared.PinMarker
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import com.jaynak.app.features.shared.MapStyleSwitch
import kotlin.math.roundToInt

private val BLUE = Color(0xFF007AFF)
private val ORANGE = Color(0xFFFF9500)

private fun last8(p: String) = p.filter { it.isDigit() }.takeLast(8)

@Composable
private fun statusTint(s: String): Color = when (s) {
    "searching" -> ORANGE
    "accepted", "assigned", "pickingUp" -> BLUE
    else -> MB.green
}

private fun statusTitle(s: String) = when (s) {
    "searching" -> "قيد البحث عن مندوب"
    "accepted", "assigned", "pickingUp" -> "المندوب في الطريق للاستلام"
    "pickedUp", "delivering" -> "في الطريق للتسليم"
    else -> s
}

/** عند غياب السيرفر: نبني نفس النموذج من بيانات التطبيق المحلية. */
private fun localFleet(orders: List<Order>, drivers: List<com.jaynak.app.core.Driver>, customerPhone: (Order) -> String): LiveTracking.Fleet {
    val rows = orders.filter { it.status.isLive }.map { o ->
        val drv = drivers.firstOrNull { it.id == o.driverID }
        val car = o.driverCoord
        val toDropoff = o.status == OrderStatus.pickedUp || o.status == OrderStatus.delivering
        val target = if (toDropoff) o.dropoff.coord else o.pickup.coord
        val km = car?.let { it.km(target) * 1.3 }
        val eta = km?.let { kotlin.math.max(1, (it / 30.0 * 60).toInt()) }
        LiveTracking.FleetOrder(
            id = o.id, ref = o.ref, status = o.status.name, pickup = o.pickup, dropoff = o.dropoff,
            customerPhone = customerPhone(o), recipientPhone = o.recipientPhone, total = o.price.total,
            driver = if (drv != null) LiveTracking.FleetDriver(drv.id, drv.name, drv.phone,
                car?.let { LiveTracking.Loc(it, null, 0.0, false) }) else null,
            target = if (toDropoff) "dropoff" else "pickup", distanceKm = km, etaMin = eta,
            statusLine = readable(o.status.name, drv?.name, km, eta),
        )
    }
    return LiveTracking.Fleet(rows, emptyList())
}

private fun readable(status: String, driver: String?, km: Double?, eta: Int?): String {
    val who = driver ?: "المندوب"
    val tail = if (km != null) " — يبعد ${LiveTracking.kmText(km)}" + (eta?.let { " (حوالي $it د)" } ?: "") else ""
    return when (status) {
        "searching" -> "الطلب قيد البحث عن مندوب"
        "accepted", "assigned", "pickingUp" -> "$who في الطريق لاستلام الطلب$tail"
        "pickedUp", "delivering" -> "$who استلم الطلب وهو في الطريق للعميل$tail"
        else -> status
    }
}

/** خريطة حيّة للإدارة: كل المندوبين والطلبات، بحث برقم الزبون، وجملة جاهزة للرد على الاتصال. */
@Composable
fun AdminLiveMap() {
    val store = LocalStore.current
    val ctx = LocalContext.current

    var remote by remember { mutableStateOf<LiveTracking.Fleet?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var track by remember { mutableStateOf<LiveTracking.Track?>(null) }
    var copied by remember { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            if (OrdersService.isAvailable) {
                runCatching { LiveTracking.fleet() }.getOrNull()?.let { remote = it }
            }
            delay(3000)
        }
    }

    val fleet = remote ?: localFleet(store.orders, store.drivers) { o ->
        store.customers.firstOrNull { it.id == o.customerID }?.phone ?: ""
    }

    val q = query.trim()
    val rows = if (q.isEmpty()) fleet.orders else fleet.orders.filter { r ->
        val digits = q.filter { it.isDigit() }
        r.ref.contains(q, true) ||
            (digits.length >= 3 && (r.customerPhone.contains(digits) || r.recipientPhone.contains(digits) ||
                (r.driver?.phone ?: "").contains(digits))) ||
            (r.driver?.name ?: "").contains(q, true) ||
            r.pickup.name.contains(q) || r.dropoff.name.contains(q)
    }
    val effectiveId = selectedId ?: if (q.isNotEmpty() && rows.size == 1) rows[0].id else null
    val sel = effectiveId?.let { id -> fleet.orders.firstOrNull { it.id == id } }

    LaunchedEffect(sel?.id, remote != null) {
        track = null
        val cur = sel ?: return@LaunchedEffect
        val id = cur.id
        if (remote == null || cur.driver?.loc == null) return@LaunchedEffect
        while (true) {
            runCatching { LiveTracking.track(id) }.getOrNull()?.let { track = it }
            delay(3000)
        }
    }

    // ───── عناصر الخريطة ─────
    val cars = buildList {
        rows.forEach { r ->
            val loc = r.driver?.loc ?: return@forEach
            add(CarMarker(r.id, loc.coord, loc.heading, statusTint(r.status), r.ref, r.id == sel?.id, loc.stale, loc.icon))
        }
        if (q.isEmpty()) fleet.idle.forEach { d ->
            d.loc?.let { add(CarMarker("d-${d.id}", it.coord, it.heading, MB.ink3, d.name, false, it.stale, it.icon)) }
        }
    }
    val pins = sel?.let {
        listOf(
            PinMarker(it.pickup.coord, "arrow.up.circle.fill", MB.green, "استلام"),
            PinMarker(it.dropoff.coord, "flag.checkered", MB.red, "تسليم"),
        )
    } ?: emptyList()
    val route: List<Coord>? = if (sel != null) {
        track?.route?.takeIf { it.size > 1 }?.let { r ->
            if (track?.target == "pickup") r + Geo.route(sel.pickup.coord, sel.dropoff.coord) else r
        }
    } else null
    val fitPts: List<Coord>? = if (sel != null)
        listOfNotNull(sel.driver?.loc?.coord, sel.pickup.coord, sel.dropoff.coord)
    else cars.map { it.coord }.ifEmpty { null }

    val km = track?.distanceKm ?: sel?.distanceKm
    val eta = track?.etaMin ?: sel?.etaMin
    val reply = sel?.let {
        "طلبك ${it.ref}: " + (track?.statusLine?.takeIf { s -> s.isNotBlank() } ?: it.statusLine)
    }

    Box(Modifier.fillMaxSize()) {
        MapCanvas(
            pickup = null, dropoff = null, cars = cars, pins = pins,
            routePath = route, showRoute = route != null,
            fitPoints = fitPts, fitKey = (effectiveId ?: "all") + if (cars.isEmpty()) "-0" else "-1",
            followDriver = false, onCarTap = { id -> if (!id.startsWith("d-")) selectedId = id },
        )

        // الشريط العلوي: بحث + عدّادات
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminSearchField(query, { query = it; if (it.isEmpty()) selectedId = null },
                "ابحث برقم الزبون أو رقم الطلب أو المندوب")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.glassChip().padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(7.dp).background(if (remote != null) MB.green else ORANGE, CircleShape))
                    Text(if (remote != null) "مباشر · ${fleet.orders.size} طلب" else "محلي · ${fleet.orders.size} طلب",
                        style = MBType.ar(12, FontWeight.SemiBold), color = MB.ink)
                }
                Row(Modifier.glassChip().clickableNoRipple { showList = true }.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SFIcon("list.bullet.rectangle", size = 12.dp, tint = MB.ink)
                    Text("القائمة", style = MBType.ar(12, FontWeight.SemiBold), color = MB.ink)
                }
                if (sel != null) Row(Modifier.glassChip().clickableNoRipple { selectedId = null; query = "" }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SFIcon("xmark", size = 11.dp, tint = MB.ink)
                    Text("عرض الكل", style = MBType.ar(12, FontWeight.SemiBold), color = MB.ink)
                }
            }
        }

        // تبديل شكل الخريطة
        MapStyleSwitch(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).padding(top = 40.dp))

        // الجزء السفلي (فوق شريط التبويبات)
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 150.dp)) {
            if (sel != null) {
                DragPanel(key = sel.id) {
                    DetailCard(sel, km, eta, reply ?: "", copied,
                        onCopy = {
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("reply", reply ?: ""))
                            copied = true
                        },
                        onClose = { selectedId = null; query = ""; copied = false })
                }
            } else if (rows.isNotEmpty()) {
                ProminentButton("قائمة الطلبات (" + rows.size + ")", { showList = true },
                    Modifier.padding(horizontal = 14.dp).fillMaxWidth(), symbol = "list.bullet.rectangle", vertical = 13.dp)
            }
        }
    }
    if (showList) OrderListSheet(rows, { id -> selectedId = id; copied = false; showList = false }, { showList = false })
}

/** لوحة تُسحب للأسفل لإظهار الخريطة بوضوح وتُسحب للأعلى لإظهار التفاصيل؛ النقر على المقبض يبدّل بينهما. */
@Composable
private fun DragPanel(key: Any, content: @Composable () -> Unit) {
    var panelH by remember { mutableIntStateOf(0) }
    var offset by remember(key) { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val peek = with(density) { 96.dp.toPx() }                 // الجزء الظاهر عند الطي
    val maxOff = (panelH - peek).coerceAtLeast(0f)
    val target = offset.coerceIn(0f, maxOff)
    val shown by animateFloatAsState(
        target, label = "panel",
        animationSpec = if (dragging) androidx.compose.animation.core.snap()
        else androidx.compose.animation.core.spring(stiffness = 600f))
    val drag = Modifier.pointerInput(maxOff) {
        detectVerticalDragGestures(
            onDragStart = { dragging = true },
            onDragEnd = { dragging = false },
            onDragCancel = { dragging = false },
        ) { _, dy -> offset = (offset + dy).coerceIn(0f, maxOff) }
    }
    Column(
        Modifier.fillMaxWidth().onSizeChanged { panelH = it.height }
            .offset { IntOffset(0, shown.roundToInt()) },
    ) {
        Box(Modifier.fillMaxWidth().height(34.dp).then(drag)
            .clickableNoRipple { offset = if (offset > maxOff / 2) 0f else maxOff },
            contentAlignment = Alignment.Center) {
            Box(Modifier.width(46.dp).height(5.dp).background(Color.White.copy(alpha = 0.75f), CircleShape))
        }
        Box(drag) { content() }
    }
}

@Composable
private fun MiniCard(r: LiveTracking.FleetOrder, onTap: () -> Unit) {
    Column(Modifier.width(210.dp).glassSurface(18.dp, null, 0.12f, underlay = Color.Black.copy(alpha = 0.35f))
        .clickableNoRipple(onTap).padding(12.dp),
        horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(statusTint(r.status), CircleShape))
            Text(r.ref, style = MBType.num(13, FontWeight.Bold), color = MB.ink, modifier = Modifier.weight(1f))
            r.distanceKm?.let { Text(LiveTracking.kmText(it), style = MBType.num(13, FontWeight.Bold), color = MB.green) }
        }
        Text(statusTitle(r.status), style = MBType.caption, color = MB.ink2)
        Text(r.driver?.name ?: "بلا مندوب", style = MBType.micro, color = MB.ink3)
    }
}

@Composable
private fun DetailCard(
    r: LiveTracking.FleetOrder, km: Double?, eta: Int?, reply: String, copied: Boolean,
    onCopy: () -> Unit, onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp)
            .glassSurface(26.dp, null, 0.30f, underlay = Color.Black.copy(alpha = 0.45f))
            .heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(32.dp).glassChip().clickableNoRipple(onClose), contentAlignment = Alignment.Center) {
                SFIcon("xmark", size = 12.dp, tint = MB.ink)
            }
            Box(Modifier.weight(1f))
            Text(statusTitle(r.status), style = MBType.caption, color = statusTint(r.status))
            Box(Modifier.size(8.dp).background(statusTint(r.status), CircleShape))
            Text(r.ref, style = MBType.num(15, FontWeight.Bold), color = MB.ink)
        }

        if (km != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("المسافة", LiveTracking.kmText(km), Modifier.weight(1f))
            Stat("الوقت المتوقع", if (eta != null) "$eta د" else "—", Modifier.weight(1f))
        }

        // الجملة الجاهزة للرد على الزبون
        Column(Modifier.fillMaxWidth().background(MB.green.copy(alpha = 0.12f), androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(12.dp),
            horizontalAlignment = Alignment.End) {
            Text("ما تقوله للزبون:", style = MBType.micro, color = MB.ink2)
            Text(reply, style = MBType.ar(14, FontWeight.SemiBold), color = MB.ink)
        }

        Text("من: ${r.pickup.name}  ←  إلى: ${r.dropoff.name}", style = MBType.micro, color = MB.ink2)

        if (r.customerPhone.isNotEmpty()) ContactRow("الزبون", null, r.customerPhone, MB.green)
        if (r.recipientPhone.isNotEmpty() && lastEight(r.recipientPhone) != lastEight(r.customerPhone))
            ContactRow("المستلم", null, r.recipientPhone, MB.green)
        r.driver?.takeIf { it.phone.isNotEmpty() }?.let { d -> ContactRow("المندوب", d.name, d.phone, BLUE) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(if (copied) "تم النسخ" else "نسخ", onCopy, Modifier.weight(1f),
                symbol = "doc.on.doc.fill", vertical = 10.dp)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier.glassSurface(14.dp, null, 0.06f).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MBType.num(20, FontWeight.Bold), color = MB.green)
        Text(label, style = MBType.micro, color = MB.ink2)
    }
}
