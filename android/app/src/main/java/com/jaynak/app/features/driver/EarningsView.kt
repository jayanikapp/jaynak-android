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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AppConfig
import com.jaynak.app.core.Fmt
import com.jaynak.app.core.Order
import com.jaynak.app.core.OrderStatus
import com.jaynak.app.core.WalletService
import com.jaynak.app.core.money
import com.jaynak.app.core.moneyShort
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.EmptyStateView
import com.jaynak.app.design.GlassButton
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.KeyValueRow
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.NavBar
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.SegmentedPicker
import com.jaynak.app.design.SheetGrabber
import com.jaynak.app.design.StatTile
import com.jaynak.app.design.glassSurface
import com.jaynak.app.features.customer.TxnRow
import com.jaynak.app.features.shared.PaymentWebOutcome
import com.jaynak.app.features.shared.PaymentWebView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private data class DayTotal(val day: Long, val total: Double, val count: Int)

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

private data class DrvPaySession(val reference: String, val amount: Double, val url: String,
                                  val successURL: String, val cancelURL: String)

private sealed class DrvPayStage {
    object Paying : DrvPayStage()
    object Confirming : DrvPayStage()
    data class Done(val ok: Boolean, val pending: Boolean, val message: String) : DrvPayStage()
}

/** مقابل EarningsView.swift. */
@Composable
fun EarningsView() {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var paySession by remember { mutableStateOf<DrvPaySession?>(null) }
    var payStage by remember { mutableStateOf<DrvPayStage>(DrvPayStage.Paying) }
    var range by remember { mutableIntStateOf(0) }
    var showWithdraw by remember { mutableStateOf(false) }
    var withdrawAmount by remember { mutableDoubleStateOf(0.0) }
    var showSettle by remember { mutableStateOf(false) }
    var settleAmount by remember { mutableDoubleStateOf(2.0) }

    val d = store.currentDriver
    val completed: List<Order> = d?.let { dr -> store.orders.filter { it.driverID == dr.id && it.status == OrderStatus.delivered } } ?: emptyList()

    val now = System.currentTimeMillis()
    val weekly: List<DayTotal> = (0..6).reversed().map { back ->
        val day = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -back) }.timeInMillis
        val items = completed.filter { sameDay(it.deliveredAt ?: it.createdAt, day) }
        DayTotal(day, items.sumOf { it.price.driverPayout }, items.size)
    }
    val todayTotal = weekly.lastOrNull()?.total ?: 0.0
    val weekTotal = weekly.sumOf { it.total }
    val maxDay = maxOf(0.001, weekly.maxOfOrNull { it.total } ?: 1.0)
    val balance = d?.earningsBalance ?: 0.0

    Box(Modifier.fillMaxSize()) {
        BrandBackground()
        Column(Modifier.fillMaxSize()) {
            NavBar(title = "أرباحي")
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (store.isDriverBlockedByBalance) {
                    GlassCard(Modifier.fillMaxWidth(), tint = MB.red.copy(alpha = 0.20f)) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.End) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                SFIcon("exclamationmark.octagon.fill", size = 22.dp, tint = MB.red)
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("الطلبات متوقّفة", style = MBType.headline, color = MB.ink)
                                    Text("رصيدك ${balance.money} — الحدّ ${store.rules.minEarningsBalance.money}",
                                        style = MBType.caption, color = MB.ink2)
                                }
                            }
                            Text("العمولة تُخصم من رصيدك في الطلبات النقدية لأنك قبضت المبلغ كاملاً. سدّد المستحق لتعود للعمل فوراً.",
                                style = MBType.caption, color = MB.ink2)
                            PrimaryCTA("تسديد المستحق", symbol = "creditcard.fill", tint = MB.red, onClick = {
                                settleAmount = maxOf(1.0, -balance + 1)
                                showSettle = true
                            })
                        }
                    }
                } else if (store.isDriverNearBalanceLimit) {
                    Row(
                        Modifier.fillMaxWidth().glassSurface(18.dp, MB.gold.copy(alpha = 0.16f), 0.08f).padding(13.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SFIcon("exclamationmark.triangle.fill", size = 18.dp, tint = MB.gold)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("رصيدك يقترب من الحدّ", style = MBType.callout, color = MB.ink)
                            Text("عند ${store.rules.minEarningsBalance.money} تتوقّف الطلبات حتى تسدّد",
                                style = MBType.micro, color = MB.ink2)
                        }
                        GlassButton("سدّد", onClick = { settleAmount = 2.0; showSettle = true },
                            tint = MB.gold, horizontal = 12.dp, vertical = 7.dp)
                    }
                }

                // بطاقة الرصيد
                GlassCard(Modifier.fillMaxWidth(), tint = MB.green.copy(alpha = 0.18f)) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.End) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("الرصيد القابل للسحب", style = MBType.caption, color = MB.ink2)
                                Text(balance.money, style = MBType.num(32, FontWeight.Bold), color = MB.ink)
                            }
                            Spacer(Modifier.weight(1f))
                            SFIcon("wallet.bifold.fill", size = 26.dp, tint = MB.green)
                        }
                        PrimaryCTA("سحب الأرباح", symbol = "arrow.down.circle.fill", enabled = balance > 0, onClick = {
                            withdrawAmount = balance
                            showWithdraw = true
                        })
                        PrimaryCTA("عبّئ محفظتك", symbol = "plus.circle.fill", tint = MB.gold, onClick = {
                            settleAmount = maxOf(2.0, Math.ceil(-balance + 1))
                            showSettle = true
                        })
                        Text("السحب يُحوّل على حسابك البنكي خلال ٢٤–٤٨ ساعة عمل.", style = MBType.micro, color = MB.ink3)
                    }
                }

                // موعد الصرف
                val next = store.nextPayoutDate()
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.End) {
                        SectionHeader("موعد الصرف", symbol = "calendar.badge.clock")
                        val names = mapOf(1 to "الأحد", 2 to "الاثنين", 3 to "الثلاثاء", 4 to "الأربعاء",
                            5 to "الخميس", 6 to "الجمعة", 7 to "السبت")
                        KeyValueRow("أيام الصرف",
                            store.rules.payoutWeekdays.sorted().mapNotNull { names[it] }.joinToString(" و"), isNumber = false)
                        if (next != null) {
                            KeyValueRow("الصرف القادم", Fmt.weekdayDayMonth(next), isNumber = false)
                            val days = ((next - now) / 86_400_000L).toInt()
                            KeyValueRow("بعد", "${maxOf(0, days)} يوم")
                        }
                        Text("يُحوَّل الرصيد القابل للسحب على حسابك البنكي في يوم الصرف.", style = MBType.micro, color = MB.ink3)
                    }
                }

                SegmentedPicker(listOf("اليوم", "الأسبوع"), range, { range = it })

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(if (range == 0) "أرباح اليوم" else "أرباح الأسبوع",
                        (if (range == 0) todayTotal else weekTotal).moneyShort, "banknote.fill", Modifier.weight(1f))
                    StatTile("طلبات",
                        "${if (range == 0) (weekly.lastOrNull()?.count ?: 0) else weekly.sumOf { it.count }}",
                        "shippingbox.fill", Modifier.weight(1f), tint = MB.red)
                }

                // الرسم البياني
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.End) {
                        SectionHeader("آخر ٧ أيام", symbol = "chart.bar.fill")
                        Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom) {
                            weekly.reversed().forEach { day ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(if (day.total > 0) day.total.moneyShort.replace(" ${AppConfig.currencySymbol}", "") else "",
                                        style = MBType.num(9, FontWeight.SemiBold), color = MB.ink2, maxLines = 1)
                                    Box(Modifier.fillMaxWidth().height(maxOf(5.0, 96 * (day.total / maxDay)).dp)
                                        .background(Brush.verticalGradient(listOf(MB.green, MB.green.copy(alpha = 0.45f))),
                                            RoundedCornerShape(7.dp)))
                                    Text(SimpleDateFormat("EEEEE", Fmt.locale).format(java.util.Date(day.day)),
                                        style = MBType.micro, color = MB.ink3)
                                }
                            }
                        }
                    }
                }

                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.End) {
                        SectionHeader("تفاصيل المحاسبة", symbol = "percent")
                        KeyValueRow("العمولة لكل طلب",
                            (d?.effectiveCommission(store.rules) ?: store.rules.fixedCommission).money, isNumber = false)
                        KeyValueRow("إجمالي الطلبات المكتملة", "${completed.size}")
                        KeyValueRow("إجمالي قيمة الطلبات", completed.sumOf { it.price.total }.money)
                        KeyValueRow("عمولة مخصومة", completed.sumOf { it.price.commission }.money, valueColor = MB.red)
                        HorizontalDivider(Modifier.alpha(0.3f), color = MB.hairline)
                        KeyValueRow("صافي أرباحك", completed.sumOf { it.price.driverPayout }.money,
                            valueColor = MB.green, bold = true)
                    }
                }

                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("حركات مالية", symbol = "arrow.left.arrow.right")
                    if (d != null) {
                        val txns = store.txns(d.id)
                        if (txns.isEmpty()) EmptyStateView("tray", "لا حركات", "ستظهر أرباحك وسحوباتك هنا")
                        else txns.take(12).forEach { TxnRow(it) }
                    }
                }
                Spacer(Modifier.height(150.dp))
            }
        }
    }

    // السحب الحقيقي: يُرسَل للسيرفر ويظهر للإدارة في تبويب «السحوبات»
    if (showWithdraw) WithdrawSheet { showWithdraw = false }

    if (showSettle) {
        JSheet(onDismiss = { showSettle = false }, fullHeight = false) {
            Box(Modifier.fillMaxWidth()) {
                BrandBackground(intensity = 0.6f)
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    SheetGrabber()
                    Text("تسديد المستحق", style = MBType.section, color = MB.ink)
                    Text(settleAmount.money, style = MBType.num(36, FontWeight.Bold), color = MB.ink)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Slider(settleAmount.toFloat(), { settleAmount = Math.round(it * 2) / 2.0 },
                            valueRange = 0.5f..20f, steps = 38,
                            colors = SliderDefaults.colors(thumbColor = MB.green, activeTrackColor = MB.green))
                    }
                    KeyValueRow("رصيدك الحالي", balance.money, valueColor = if (balance < 0) MB.red else MB.ink)
                    KeyValueRow("بعد التسديد", (balance + settleAmount).money, valueColor = MB.green, bold = true)
                    PrimaryCTA("ادفع الآن عبر ثواني", symbol = "creditcard.fill", onClick = {
                        showSettle = false
                        val amt = settleAmount
                        scope.launch {
                            try {
                                val r = WalletService.startTopUp(amt, forDriver = true)
                                val url = r.redirectURL
                                if (url == null) {
                                    store.toast = com.jaynak.app.core.AppStore.Toast(
                                        text = "تعذّر فتح صفحة الدفع", kind = com.jaynak.app.core.AppStore.Toast.Kind.error)
                                } else {
                                    payStage = DrvPayStage.Paying
                                    paySession = DrvPaySession(r.reference, amt, url, r.successURL, r.cancelURL)
                                }
                            } catch (e: Exception) {
                                store.toast = com.jaynak.app.core.AppStore.Toast(
                                    text = e.message ?: "خطأ", kind = com.jaynak.app.core.AppStore.Toast.Kind.error)
                            }
                        }
                    })
                    Text("يُضاف المبلغ لرصيدك تلقائياً فور نجاح الدفع.", style = MBType.micro, color = MB.ink3,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    paySession?.let { ps ->
        JSheet(onDismiss = { paySession = null; payStage = DrvPayStage.Paying }) {
            Box(Modifier.fillMaxSize()) {
                BrandBackground(intensity = 0.6f)
                when (val st = payStage) {
                    DrvPayStage.Paying -> PaymentWebView(
                        url = ps.url, successPrefix = ps.successURL, cancelPrefix = ps.cancelURL,
                        modifier = Modifier.fillMaxSize(),
                    ) { outcome ->
                        when (outcome) {
                            PaymentWebOutcome.success -> {
                                payStage = DrvPayStage.Confirming
                                scope.launch {
                                    payStage = try {
                                        val r = WalletService.confirmTopUp(ps.reference)
                                        when {
                                            r.credited || r.status == "PAID" -> {
                                                runCatching { store.refreshDriverFeed() }
                                                DrvPayStage.Done(true, false, "")
                                            }
                                            r.status == "PENDING" || r.status == "PROCESSING" ->
                                                DrvPayStage.Done(false, true, "")
                                            else -> DrvPayStage.Done(false, false, "لم تكتمل عملية الدفع")
                                        }
                                    } catch (e: Exception) {
                                        DrvPayStage.Done(false, false, e.message ?: "تعذّر التأكيد")
                                    }
                                }
                            }
                            PaymentWebOutcome.cancelled -> payStage = DrvPayStage.Done(false, false, "تم إلغاء عملية الدفع")
                        }
                    }
                    DrvPayStage.Confirming -> Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        CircularProgressIndicator(color = MB.green)
                        Text("جارٍ التأكيد من الخادم…", style = MBType.callout, color = MB.ink2)
                    }
                    is DrvPayStage.Done -> Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
                    ) {
                        when {
                            st.ok -> {
                                SFIcon("checkmark.circle.fill", size = 56.dp, tint = MB.green)
                                Text("تم شحن رصيدك بنجاح", style = MBType.section, color = MB.ink)
                            }
                            st.pending -> {
                                SFIcon("clock.fill", size = 56.dp, tint = MB.gold)
                                Text("الدفع قيد التأكيد", style = MBType.section, color = MB.ink)
                                Text("سيُضاف المبلغ لرصيدك تلقائياً فور اكتمال التأكيد",
                                    style = MBType.caption, color = MB.ink2, textAlign = TextAlign.Center)
                            }
                            else -> {
                                SFIcon("xmark.circle.fill", size = 56.dp, tint = MB.red)
                                Text(st.message.ifEmpty { "فشلت عملية الدفع" }, style = MBType.section, color = MB.ink,
                                    textAlign = TextAlign.Center)
                            }
                        }
                        PrimaryCTA("تم", symbol = "checkmark", onClick = { paySession = null; payStage = DrvPayStage.Paying })
                    }
                }
            }
        }
    }
}
