package com.jaynak.app.features.customer

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
import androidx.compose.material3.CircularProgressIndicator
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AppStore
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.Fmt
import com.jaynak.app.core.OrderStatus
import com.jaynak.app.core.WalletService
import com.jaynak.app.core.WalletTxn
import com.jaynak.app.core.WalletTxnKind
import com.jaynak.app.core.money
import com.jaynak.app.core.moneyShort
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.EmptyStateView
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.Metrics
import com.jaynak.app.design.OnResume
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.SheetGrabber
import com.jaynak.app.design.StatTile
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.pressable
import com.jaynak.app.features.auth.GuestGate
import com.jaynak.app.features.shared.PaymentWebOutcome
import com.jaynak.app.features.shared.PaymentWebView
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

private data class PaymentSession(
    val reference: String, val amount: Double, val checkoutURL: String,
    val successURL: String, val cancelURL: String,
)

private sealed class PaymentResult {
    data class Success(val balance: Double?) : PaymentResult()
    object Pending : PaymentResult()
    object Cancelled : PaymentResult()
    data class Failed(val message: String) : PaymentResult()
}

private sealed class PaymentStage {
    object Paying : PaymentStage()
    object Confirming : PaymentStage()
    data class Result(val result: PaymentResult) : PaymentStage()
}

/** مقابل WalletView.swift. */
@Composable
fun WalletView() {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var showTopUp by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf(5.0) }
    var session by remember { mutableStateOf<PaymentSession?>(null) }
    var stage by remember { mutableStateOf<PaymentStage>(PaymentStage.Paying) }

    val c = store.currentCustomer

    val spentThisMonth: Double = run {
        val cid = c?.id ?: return@run 0.0
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        store.txns(cid).filter { it.kind == WalletTxnKind.orderPayment && it.date >= start }
            .sumOf { abs(it.amount) }
    }

    suspend fun confirmTopUp(reference: String, amount: Double): PaymentResult = try {
        val result = WalletService.confirmTopUp(reference)
        when {
            result.credited -> {
                store.applyWalletTopUpConfirmation(result.balance, amount)
                PaymentResult.Success(result.balance)
            }
            result.status == "PENDING" || result.status == "PROCESSING" -> {
                // لم يُحسم بعد — نعيد المحاولة عند العودة للتطبيق بدل إسقاطه بصمت.
                store.pendingWalletTopUpReference = reference
                PaymentResult.Pending
            }
            else -> PaymentResult.Failed("لم تكتمل عملية الدفع")
        }
    } catch (e: Exception) {
        PaymentResult.Failed(e.message ?: "تعذّر التأكيد")
    }

    suspend fun startTopUp() {
        showTopUp = false
        try {
            val result = WalletService.startTopUp(amount)
            val url = result.redirectURL
            if (url == null) {
                store.toast = AppStore.Toast(text = "تعذّر فتح صفحة الدفع", kind = AppStore.Toast.Kind.error)
                return
            }
            stage = PaymentStage.Paying
            session = PaymentSession(result.reference, amount, url, result.successURL, result.cancelURL)
        } catch (e: Exception) {
            store.toast = AppStore.Toast(text = e.message ?: "خطأ", kind = AppStore.Toast.Kind.error)
        }
    }

    OnResume {
        val reference = store.pendingWalletTopUpReference ?: return@OnResume
        store.pendingWalletTopUpReference = null
        scope.launch {
            val r = confirmTopUp(reference, amount)
            if (r is PaymentResult.Failed) {
                store.toast = AppStore.Toast(text = r.message, kind = AppStore.Toast.Kind.error)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .statusBarsPadding().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("المحفظة", style = MBType.title, color = MB.ink, modifier = Modifier.padding(top = 14.dp))

            if (store.isGuest) {
                GuestGate("creditcard.fill", "محفظتك بانتظارك",
                    "سجّل الدخول لتعبّئ رصيدك وتدفع طلباتك بضغطة واحدة — وتحصل على ١ ر.ع. رصيد ترحيبي.")
                Spacer(Modifier.height(200.dp))
            } else {
                BalanceCard(c?.walletBalance ?: 0.0) { showTopUp = true }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("مصروف هذا الشهر", spentThisMonth.moneyShort, "chart.line.downtrend.xyaxis",
                        Modifier.weight(1f), tint = MB.red)
                    StatTile("طلبات مكتملة",
                        "${store.orders.count { it.customerID == c?.id && it.status == OrderStatus.delivered }}",
                        "checkmark.seal.fill", Modifier.weight(1f))
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("الحركات", symbol = "arrow.left.arrow.right")
                    val cid = c?.id
                    if (cid != null) {
                        val txns = store.txns(cid)
                        if (txns.isEmpty()) EmptyStateView("tray", "لا حركات بعد", "ستظهر هنا كل عمليات التعبئة والدفع")
                        else txns.forEach { TxnRow(it) }
                    }
                }
                Spacer(Modifier.height(150.dp))
            }
        }
    }

    if (showTopUp) {
        JSheet(onDismiss = { showTopUp = false }, fullHeight = false) {
            Box(Modifier.fillMaxWidth()) {
                BrandBackground(intensity = 0.6f)
                Column(
                    Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    SheetGrabber()
                    Text("تعبئة المحفظة", style = MBType.section, color = MB.ink)
                    Text(amount.money, style = MBType.num(38, FontWeight.Bold), color = MB.ink)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(2.0, 5.0, 10.0, 20.0).forEach { v ->
                            val on = amount == v
                            Box(
                                Modifier.weight(1f)
                                    .pressable(1f) { Feedback.selection(); amount = v }
                                    .glassSurface(14.dp, null, 0.06f)
                                    .let { if (on) it.background(MB.green, RoundedCornerShape(14.dp)) else it }
                                    .padding(vertical = 13.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(v.moneyShort, style = MBType.num(14, FontWeight.SemiBold),
                                    color = if (on) Color.White else MB.ink)
                            }
                        }
                    }
                    PrimaryCTA("تأكيد التعبئة", symbol = "checkmark", onClick = { scope.launch { startTopUp() } })
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    session?.let { s ->
        JSheet(onDismiss = { session = null; stage = PaymentStage.Paying }) {
            Box(Modifier.fillMaxSize()) {
                BrandBackground(intensity = 0.6f)
                when (val st = stage) {
                    PaymentStage.Paying -> PaymentWebView(
                        url = s.checkoutURL, successPrefix = s.successURL, cancelPrefix = s.cancelURL,
                        modifier = Modifier.fillMaxSize(),
                    ) { outcome ->
                        when (outcome) {
                            PaymentWebOutcome.success -> {
                                stage = PaymentStage.Confirming
                                scope.launch {
                                    val r = confirmTopUp(s.reference, s.amount)
                                    stage = PaymentStage.Result(r)
                                }
                            }
                            PaymentWebOutcome.cancelled -> stage = PaymentStage.Result(PaymentResult.Cancelled)
                        }
                    }
                    PaymentStage.Confirming -> Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        CircularProgressIndicator(color = MB.green)
                        Text("جارٍ التأكيد من الخادم…", style = MBType.callout, color = MB.ink2)
                    }
                    is PaymentStage.Result -> PaymentResultView(st.result) { session = null; stage = PaymentStage.Paying }
                }
            }
        }
    }
}

@Composable
private fun PaymentResultView(result: PaymentResult, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.weight(1f))
        when (result) {
            is PaymentResult.Success -> {
                SFIcon("checkmark.circle.fill", size = 56.dp, tint = MB.green)
                Text("تمت تعبئة المحفظة بنجاح", style = MBType.section, color = MB.ink)
                result.balance?.let { Text(it.money, style = MBType.num(28, FontWeight.Bold), color = MB.ink) }
            }
            PaymentResult.Pending -> {
                SFIcon("clock.fill", size = 56.dp, tint = MB.gold)
                Text("الدفع قيد التأكيد", style = MBType.section, color = MB.ink)
                Text("سنحدّث رصيدك تلقائياً بمجرد اكتمال التأكيد من البوابة",
                    style = MBType.caption, color = MB.ink2, textAlign = TextAlign.Center)
            }
            PaymentResult.Cancelled -> {
                SFIcon("xmark.circle.fill", size = 56.dp, tint = MB.ink2)
                Text("تم إلغاء عملية الدفع", style = MBType.section, color = MB.ink)
            }
            is PaymentResult.Failed -> {
                SFIcon("exclamationmark.triangle.fill", size = 56.dp, tint = MB.red)
                Text("فشلت عملية الدفع", style = MBType.section, color = MB.ink)
                Text(result.message, style = MBType.caption, color = MB.ink2, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryCTA("تم", symbol = "checkmark", onClick = onDone)
    }
}

@Composable
private fun BalanceCard(balance: Double, onTopUp: () -> Unit) {
    GlassCard(tint = MB.green.copy(alpha = 0.18f)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("رصيدك", style = MBType.caption, color = MB.ink2)
                    Text(balance.money, style = MBType.num(34, FontWeight.Bold), color = MB.ink)
                }
                Spacer(Modifier.weight(1f))
                SFIcon("creditcard.fill", size = 26.dp, tint = MB.green)
            }
            PrimaryCTA("تعبئة الرصيد", symbol = "plus", onClick = onTopUp)
            Text("نقبل الدفع بالبطاقات البنكية — فيزا وماستركارد وبطاقات الخصم المحلية — " +
                "عبر بوابة ثواني الآمنة. خدمة التوصيل خدمة واقعية خارج التطبيق، " +
                "فلا تمرّ عبر الشراء داخل التطبيق.",
                style = MBType.micro, color = MB.ink3, textAlign = TextAlign.End)
        }
    }
}

/** مقابل TxnRow — يُستعمل أيضاً في أرباح المندوب. */
@Composable
fun TxnRow(txn: WalletTxn, modifier: Modifier = Modifier) {
    val positive = txn.amount >= 0
    val tint = if (positive) MB.green else MB.red
    val symbol = when (txn.kind) {
        WalletTxnKind.topUp -> "plus"
        WalletTxnKind.orderPayment -> if (positive) "arrow.down.left" else "arrow.up.right"
        WalletTxnKind.refund -> "arrow.uturn.backward"
        WalletTxnKind.promo -> "gift.fill"
        WalletTxnKind.payout -> "banknote.fill"
        WalletTxnKind.commission -> "percent"
        WalletTxnKind.reward -> "gift.fill"
    }
    Row(
        modifier.fillMaxWidth().glassSurface(16.dp, null, 0.06f).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(38.dp).background(tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            SFIcon(symbol, size = 14.dp, tint = tint)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(txn.kind.title, style = MBType.callout, color = MB.ink)
            Text(listOfNotNull(txn.orderRef, Fmt.dayMonthTime(txn.date)).joinToString(" · "),
                style = MBType.micro, color = MB.ink2)
        }
        Text((if (positive) "+" else "−") + abs(txn.amount).money,
            style = MBType.num(15, FontWeight.Bold), color = if (positive) MB.green else MB.ink)
    }
}
