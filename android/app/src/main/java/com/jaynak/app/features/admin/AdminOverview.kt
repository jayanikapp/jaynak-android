package com.jaynak.app.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.Customer
import com.jaynak.app.core.Driver
import com.jaynak.app.core.DriverApproval
import com.jaynak.app.core.Fmt
import com.jaynak.app.core.Order
import com.jaynak.app.core.OrderKind
import com.jaynak.app.core.OrderStatus
import com.jaynak.app.core.money
import com.jaynak.app.core.moneyShort
import com.jaynak.app.design.Avatar
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.BrandMark
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.GlassIconButton
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.KeyValueRow
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SecondaryCTA
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.SegmentedPicker
import com.jaynak.app.design.StarRating
import com.jaynak.app.design.StatTile
import com.jaynak.app.design.appearIn
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.design.glassSurface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val orange = Color(0xFFFF9500)

data class DayStat(val day: Long, val count: Int, val revenue: Double, val commission: Double)
data class TodayStat(val count: Int, val revenue: Double, val commission: Double)

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

/** مقابل AdminMetrics. */
class AdminMetrics(val orders: List<Order>, val drivers: List<Driver>, val customers: List<Customer>) {
    private val delivered get() = orders.filter { it.status == OrderStatus.delivered }

    val liveCount: Int get() = orders.count { it.status.isLive }
    val grossRevenue: Double get() = delivered.sumOf { it.price.total }
    val commission: Double get() = delivered.sumOf { it.price.commission }
    val driverPayouts: Double get() = delivered.sumOf { it.price.driverPayout }
    val avgOrder: Double get() = delivered.let { if (it.isEmpty()) 0.0 else grossRevenue / it.size }
    val cancelRate: Double
        get() {
            val closed = orders.count { !it.status.isLive }
            if (closed <= 0) return 0.0
            return orders.count { it.status == OrderStatus.cancelled }.toDouble() / closed
        }
    val avgRating: Double
        get() {
            val rated = drivers.filter { it.ratingCount > 0 }
            if (rated.isEmpty()) return 0.0
            return rated.sumOf { it.rating } / rated.size
        }
    val pendingDrivers: Int get() = drivers.count { it.approval == DriverApproval.pending }

    fun today(): TodayStat {
        val now = System.currentTimeMillis()
        val items = delivered.filter { sameDay(it.deliveredAt ?: it.createdAt, now) }
        return TodayStat(items.size, items.sumOf { it.price.total }, items.sumOf { it.price.commission })
    }

    fun daily(): List<DayStat> {
        val now = System.currentTimeMillis()
        val del = delivered
        return (0..6).reversed().map { back ->
            val day = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -back) }.timeInMillis
            val items = del.filter { sameDay(it.deliveredAt ?: it.createdAt, day) }
            DayStat(day, items.size, items.sumOf { it.price.total }, items.sumOf { it.price.commission })
        }
    }

    fun topDrivers(n: Int = 5): List<Driver> =
        drivers.filter { it.completedOrders > 0 }
            .sortedWith(compareByDescending<Driver> { it.completedOrders }.thenByDescending { it.rating })
            .take(n)

    fun ordersByKind(): List<Pair<OrderKind, Int>> =
        OrderKind.values().map { k -> k to delivered.count { it.kind == k } }
}

/** مقابل AdminOverview.swift. */
@Composable
fun AdminOverview() {
    val store = LocalStore.current
    var showReport by remember { mutableStateOf(false) }
    val m = AdminMetrics(store.orders, store.drivers, store.customers)
    val today = m.today()

    Box(Modifier.fillMaxSize()) {
        BrandBackground()
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).appearIn(0),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                BrandMark(size = 54.dp)
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(store.currentAdmin?.name ?: "لوحة الإدارة", style = MBType.ar(20, FontWeight.Bold), color = MB.ink)
                    Text(listOfNotNull(store.currentAdmin?.role?.title, Fmt.weekdayDayMonth(System.currentTimeMillis()))
                        .joinToString(" · "), style = MBType.caption, color = MB.ink2)
                }
                Spacer(Modifier.weight(1f))
                GlassIconButton("rectangle.portrait.and.arrow.right", { store.signOut() },
                    size = 40.dp, iconSize = 14.dp, tint = MB.red)
            }

            if (m.pendingDrivers > 0) {
                Row(Modifier.fillMaxWidth().appearIn(1).glassSurface(20.dp, orange.copy(alpha = 0.18f)).padding(15.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SFIcon("person.badge.clock.fill", size = 20.dp, tint = orange)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${m.pendingDrivers} مندوب بانتظار الموافقة", style = MBType.headline, color = MB.ink)
                        Text("راجع مستنداتهم من تبويب المستخدمون", style = MBType.micro, color = MB.ink2)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("طلبات اليوم", "${today.count}", "shippingbox.fill", Modifier.weight(1f))
                StatTile("إيراد اليوم", today.revenue.moneyShort, "banknote.fill", Modifier.weight(1f), tint = MB.red)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("عمولة اليوم", today.commission.moneyShort, "percent", Modifier.weight(1f), tint = orange)
                StatTile("طلبات حيّة", "${m.liveCount}", "dot.radiowaves.left.and.right", Modifier.weight(1f))
            }

            WeeklyChart(m, Modifier.appearIn(4))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("متوسط الطلب", m.avgOrder.moneyShort, "chart.bar.fill", Modifier.weight(1f))
                StatTile("نسبة الإلغاء", "${(m.cancelRate * 100).toInt()}٪", "xmark.circle.fill", Modifier.weight(1f), tint = MB.red)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("متوسط التقييم", "%.2f".format(Locale.US, m.avgRating), "star.fill", Modifier.weight(1f), tint = orange)
                StatTile("إجمالي المندوبين", "${store.drivers.size}", "car.fill", Modifier.weight(1f))
            }

            KindBreakdown(m, Modifier.appearIn(6))
            TopDrivers(m, Modifier.appearIn(7))

            SecondaryCTA("التقارير المالية", onClick = { showReport = true }, symbol = "doc.text.magnifyingglass")
            Spacer(Modifier.height(150.dp))
        }
    }

    if (showReport) AdminReportSheet { showReport = false }
}

@Composable
private fun WeeklyChart(m: AdminMetrics, modifier: Modifier) {
    GlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.End) {
            SectionHeader("آخر ٧ أيام", subtitle = "الإيراد وعدد الطلبات", symbol = "chart.line.uptrend.xyaxis")
            val data = m.daily()
            val maxRev = maxOf(0.001, data.maxOfOrNull { it.revenue } ?: 1.0)

            if (data.all { it.revenue <= 0 && it.count == 0 }) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    SFIcon("chart.bar.doc.horizontal", size = 18.dp, tint = MB.ink3)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("لا طلبات هذا الأسبوع بعد", style = MBType.callout, color = MB.ink2)
                        Text("يظهر الرسم البياني مع أول طلب", style = MBType.micro, color = MB.ink3)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Bottom) {
                    data.reversed().forEach { d ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(if (d.count > 0) "${d.count}" else "", style = MBType.num(9, FontWeight.Bold), color = MB.ink2)
                            Box(Modifier.fillMaxWidth().height(110.dp).background(MB.ink.copy(alpha = 0.07f), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.BottomCenter) {
                                Box(Modifier.fillMaxWidth().height(maxOf(4.0, 110 * (d.revenue / maxRev)).dp)
                                    .background(Brush.verticalGradient(listOf(MB.green, MB.red.copy(alpha = 0.6f))),
                                        RoundedCornerShape(6.dp)))
                            }
                            Text(SimpleDateFormat("EEEEE", Fmt.locale).format(Date(d.day)), style = MBType.micro, color = MB.ink3)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KindBreakdown(m: AdminMetrics, modifier: Modifier) {
    GlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
            SectionHeader("توزيع أنواع الطلبات", symbol = "square.grid.2x2.fill")
            val data = m.ordersByKind()
            val total = maxOf(1, data.sumOf { it.second })
            data.forEach { (kind, count) ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        SFIcon(kind.symbol, size = 12.dp, tint = MB.green)
                        Text(kind.title, style = MBType.callout, color = MB.ink)
                        Spacer(Modifier.weight(1f))
                        Text("$count · ${(count.toDouble() / total * 100).toInt()}٪",
                            style = MBType.num(12, FontWeight.SemiBold), color = MB.ink2)
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).background(MB.ink.copy(alpha = 0.08f), CircleShape),
                        contentAlignment = Alignment.CenterEnd) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth((count.toFloat() / total).coerceIn(0f, 1f))
                            .background(MB.green, CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
private fun TopDrivers(m: AdminMetrics, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("أفضل المندوبين", symbol = "trophy.fill")
        m.topDrivers().forEachIndexed { i, d ->
            Row(Modifier.fillMaxWidth().glassSurface(16.dp, null, 0.06f).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = MBType.num(14, FontWeight.Bold), color = if (i == 0) MB.green else MB.ink3,
                    modifier = Modifier.width(20.dp))
                Avatar(d.name, size = 38.dp, tint = if (i == 0) MB.green else MB.red)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(d.name, style = MBType.callout, color = MB.ink)
                    StarRating(d.rating, count = d.ratingCount, size = 9.dp)
                }
                Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${d.completedOrders}", style = MBType.num(15, FontWeight.Bold), color = MB.ink)
                    Text("طلب", style = MBType.micro, color = MB.ink3)
                }
            }
        }
    }
}

/** مقابل AdminReportSheet. */
@Composable
fun AdminReportSheet(onDismiss: () -> Unit) {
    val store = LocalStore.current
    var period by remember { mutableIntStateOf(0) }
    val m = AdminMetrics(store.orders, store.drivers, store.customers)

    JSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxSize()) {
            BrandBackground(intensity = 0.5f)
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("إغلاق", style = MBType.callout, color = MB.green, modifier = Modifier.clickableNoRipple(onDismiss))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("التقارير المالية", style = MBType.headline, color = MB.ink)
                    }
                    Spacer(Modifier.padding(horizontal = 20.dp))
                }
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SegmentedPicker(listOf("يومي", "أسبوعي"), period, { period = it })

                    GlassCard(Modifier.fillMaxWidth(), tint = MB.green.copy(alpha = 0.12f)) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp), horizontalAlignment = Alignment.End) {
                            SectionHeader(if (period == 0) "ملخّص اليوم" else "ملخّص الأسبوع", symbol = "doc.text.fill")
                            val d = m.daily()
                            val rows = if (period == 0) listOf(d.last()) else d
                            val count = rows.sumOf { it.count }
                            val rev = rows.sumOf { it.revenue }
                            val com = rows.sumOf { it.commission }
                            KeyValueRow("عدد الطلبات المكتملة", "$count")
                            KeyValueRow("إجمالي المبيعات", rev.money)
                            KeyValueRow("عمولة المنصة", com.money, valueColor = MB.green)
                            KeyValueRow("مستحقات المندوبين", (rev - com).money, valueColor = MB.red)
                            HorizontalDivider(Modifier.alpha(0.3f), color = MB.hairline)
                            KeyValueRow("متوسط قيمة الطلب", (if (count == 0) 0.0 else rev / count).money, bold = true)
                        }
                    }

                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
                            SectionHeader("تفصيل يومي", symbol = "calendar")
                            m.daily().reversed().forEach { d ->
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(SimpleDateFormat("EEE d MMM", Fmt.locale).format(Date(d.day)),
                                            style = MBType.callout, color = MB.ink)
                                        Spacer(Modifier.weight(1f))
                                        Text("${d.count} طلب", style = MBType.num(12, FontWeight.Medium), color = MB.ink2)
                                        Text(d.revenue.money, style = MBType.num(13, FontWeight.Bold), color = MB.ink)
                                    }
                                    HorizontalDivider(Modifier.alpha(0.2f), color = MB.hairline)
                                }
                            }
                        }
                    }

                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
                            SectionHeader("الإجمالي التاريخي", symbol = "plus.forwardslash.minus")
                            KeyValueRow("إجمالي الإيرادات", m.grossRevenue.money)
                            KeyValueRow("إجمالي العمولات", m.commission.money, valueColor = MB.green)
                            KeyValueRow("إجمالي مستحقات المندوبين", m.driverPayouts.money, valueColor = MB.red)
                            KeyValueRow("عدد العملاء", "${store.customers.size}")
                            KeyValueRow("عدد المندوبين المعتمدين", "${store.drivers.count { it.approval == DriverApproval.approved }}")
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}
