package com.jaynak.app.features.customer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.DispatchMode
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.Fmt
import com.jaynak.app.core.Geo
import com.jaynak.app.core.ImpactStyle
import com.jaynak.app.core.LocationService
import com.jaynak.app.core.Order
import com.jaynak.app.core.OrderKind
import com.jaynak.app.core.PaymentMethod
import com.jaynak.app.core.Place
import com.jaynak.app.core.PriceBreakdown
import com.jaynak.app.core.UserRole
import com.jaynak.app.core.Voucher
import com.jaynak.app.core.money
import com.jaynak.app.core.moneyShort
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.GlassIconButton
import com.jaynak.app.design.KeyValueRow
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBField
import com.jaynak.app.design.MBType
import com.jaynak.app.design.NavBar
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.SelectChip
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.pressable
import com.jaynak.app.features.auth.PresentsAuth
import com.jaynak.app.features.shared.MapCanvas
import com.jaynak.app.features.shared.PlacePicker
import kotlinx.coroutines.delay
import java.util.Locale

private enum class PickTarget { from, to }

/** مقابل NewOrderView.swift. */
@Composable
fun NewOrderView(
    initialKind: OrderKind = OrderKind.documents,
    onClose: () -> Unit,
    onCreated: (Order) -> Unit,
) {
    val store = LocalStore.current

    var pickup by remember { mutableStateOf<Place?>(null) }
    var dropoff by remember { mutableStateOf<Place?>(null) }
    var kind by remember { mutableStateOf(OrderKind.documents) }
    var payment by remember { mutableStateOf(PaymentMethod.wallet) }
    var note by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf("") }
    var recipientPhone by remember { mutableStateOf("") }
    var promoText by remember { mutableStateOf("") }
    var voucher by remember { mutableStateOf<Voucher?>(null) }
    var dispatchMode by remember { mutableStateOf(DispatchMode.nearby) }
    var picking by remember { mutableStateOf<PickTarget?>(null) }
    var showBreakdown by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var submitAfterLogin by remember { mutableStateOf(false) }
    var createTrigger by remember { mutableStateOf(0) }

    var serverQ by remember { mutableStateOf<PriceBreakdown?>(null) }
    var serverKey by remember { mutableStateOf("") }
    var promoError by remember { mutableStateOf<String?>(null) }
    val qKey = "${pickup?.coord?.lat},${pickup?.coord?.lon}|${dropoff?.coord?.lat},${dropoff?.coord?.lon}|$kind|$promoText|${voucher?.id}|$dispatchMode"
    androidx.compose.runtime.LaunchedEffect(Unit) { store.pullRules(); store.pullWallet() }
    androidx.compose.runtime.LaunchedEffect(qKey) {
        val p = pickup; val d = dropoff
        if (p == null || d == null) return@LaunchedEffect
        kotlinx.coroutines.delay(350)
        store.serverQuote(p, d, kind, dispatchMode == DispatchMode.nearby, promoText, voucher)?.let { (price, err) ->
            serverQ = price; serverKey = qKey; promoError = err
        }
    }
    val quote: PriceBreakdown? = run {
        val p = pickup; val d = dropoff
        if (p == null || d == null) null
        else if (serverQ != null && serverKey == qKey) serverQ
        else store.quote(p, d, kind, promoText, voucher, express = dispatchMode == DispatchMode.nearby)
    }
    val walletBalance = store.currentCustomer?.walletBalance ?: 0.0
    val canSubmit: Boolean = run {
        if (pickup == null || dropoff == null) false
        else if (store.isGuest) true
        else payment == PaymentMethod.cash || (quote?.let { walletBalance >= it.total } ?: false)
    }

    fun create() {
        val p = pickup ?: return
        val d = dropoff ?: return
        if (store.isGuest) return
        submitting = true
        store.createOrder(kind, p, d, payment, note, recipientName, recipientPhone,
            promoText, voucher, dispatchMode)?.let(onCreated)
        submitting = false
    }

    fun submit() {
        if (pickup == null || dropoff == null) return
        if (recipientName.isBlank() || recipientPhone.filter { it.isDigit() }.length < 8) {
            store.toast = com.jaynak.app.core.AppStore.Toast(
                text = "اكتب اسم المستلم ورقم هاتفه (٨ أرقام) في «تفاصيل المستلم» أسفل الصفحة",
                kind = com.jaynak.app.core.AppStore.Toast.Kind.error)
            return
        }
        if (store.isGuest) {
            submitAfterLogin = true
            store.requestLogin(UserRole.customer, "بقيت خطوة واحدة — سجّل دخولك ونثبّت طلبك على أقرب مندوب")
            return
        }
        create()
    }

    LaunchedEffect(Unit) {
        if (pickup == null) {
            pickup = LocationService.currentPlace
                ?: store.currentCustomer?.savedPlaces?.firstOrNull()
                ?: Geo.place("الخوير - جمعية")
        }
        if (store.isGuest) payment = PaymentMethod.cash
        kind = initialKind
    }

    PresentsAuth {
        if (submitAfterLogin) {
            submitAfterLogin = false
            createTrigger++
        }
    }
    LaunchedEffect(createTrigger) {
        if (createTrigger > 0) { delay(350); create() }
    }

    Box(Modifier.fillMaxSize()) {
        BrandBackground()

        Column(Modifier.fillMaxSize()) {
            // الشريط العلوي
            NavBar(title = "طلب جديد", leading = {
                Text("إلغاء", style = MBType.callout, color = MB.green,
                    modifier = Modifier.clickableNoRipple(onClose))
            })

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // رأس الخريطة
                Box(
                    Modifier.fillMaxWidth().height(190.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .border(1.dp, MB.hairline, RoundedCornerShape(26.dp))
                ) { MapCanvas(pickup = pickup, dropoff = dropoff, driver = null) }

                // بطاقة المسار
                GlassCard(padding = 14.dp) {
                    Column(Modifier.fillMaxWidth()) {
                        PointRow("من", pickup, MB.green, "arrow.up.circle.fill") { picking = PickTarget.from }
                        Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                repeat(3) { Box(Modifier.size(3.dp).background(MB.ink3, CircleShape)) }
                            }
                            Spacer(Modifier.weight(1f))
                            GlassIconButton(
                                "arrow.up.arrow.down",
                                {
                                    if (pickup != null && dropoff != null) {
                                        Feedback.impact(ImpactStyle.light)
                                        val t = pickup; pickup = dropoff; dropoff = t
                                    }
                                },
                                size = 34.dp, iconSize = 13.dp, enabled = pickup != null && dropoff != null)
                        }
                        PointRow("إلى", dropoff, MB.red, "flag.checkered") { picking = PickTarget.to }
                    }
                }

                // نوع الطلب
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("نوع الطلب", symbol = "square.grid.2x2.fill")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OrderKind.values().forEach { k ->
                            SelectChip(k.title, k.symbol, kind == k, { kind = k }, Modifier.weight(1f), subtitle = k.note)
                        }
                    }
                }

                // طريقة التوصيل
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("طريقة التوصيل", symbol = "arrow.triangle.branch")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DispatchMode.values().forEach { m ->
                            SelectChip(m.title, m.symbol, dispatchMode == m, { dispatchMode = m }, Modifier.weight(1f),
                                subtitle = m.detail, tint = if (m == DispatchMode.open) MB.gold else MB.green)
                        }
                    }
                    if (dispatchMode == DispatchMode.open) {
                        Text("طلبك سيظهر لكل المندوبين، وأول من يقبله يثبت عليه. قد يستغرق وقتاً أطول لكنه يصل حيث لا يصل النطاق القريب.",
                            style = MBType.micro, color = MB.ink3, textAlign = TextAlign.End)
                    } else if (quote != null && quote.km > 15) {
                        Row(
                            Modifier.fillMaxWidth().glassSurface(14.dp, MB.gold.copy(alpha = 0.12f), 0.04f).padding(11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SFIcon("lightbulb.fill", size = 14.dp, tint = MB.gold)
                            Text("المسافة ${"%.0f".format(Locale.US, quote.km)} كم — «طلب مفتوح» يجد لك مندوباً أسرع.",
                                style = MBType.micro, color = MB.ink2, modifier = Modifier.weight(1f))
                        }
                    }
                }

                // تفاصيل المستلم
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("تفاصيل المستلم", symbol = "person.fill")
                    MBField("اسم المستلم (مطلوب)", recipientName, { recipientName = it }, placeholder = "اكتب الاسم أولاً ليعرفه المندوب", symbol = "person")
                    MBField("رقم هاتف المستلم (مطلوب)", recipientPhone, { recipientPhone = it }, placeholder = "XXXXXXXX",
                        symbol = "phone", keyboard = KeyboardType.Number, isRTLText = false)
                    MBField("ملاحظات للمندوب", note, { note = it }, placeholder = "مثال: الدور الثالث، مكتب ٧", symbol = "text.alignright")
                }

                // طريقة الدفع
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("طريقة الدفع", symbol = "creditcard.fill")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PaymentMethod.values().forEach { m ->
                            SelectChip(
                                m.title, m.symbol, payment == m,
                                {
                                    if (m == PaymentMethod.wallet && store.isGuest) {
                                        store.requestLogin(UserRole.customer, "المحفظة تحتاج حساباً — الدخول يأخذ ثانية")
                                    } else payment = m
                                },
                                Modifier.weight(1f),
                                subtitle = if (m == PaymentMethod.wallet)
                                    (if (store.isGuest) "بعد الدخول" else "الرصيد: ${walletBalance.moneyShort}")
                                else "عند التسليم",
                                tint = if (m == PaymentMethod.wallet) MB.green else MB.red)
                        }
                    }
                    if (payment == PaymentMethod.wallet && !store.isGuest && quote != null && walletBalance < quote.total) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SFIcon("exclamationmark.triangle.fill", size = 13.dp, tint = MB.red)
                            Text("الرصيد لا يكفي — تحتاج ${(quote.total - walletBalance).money} إضافية",
                                style = MBType.caption, color = MB.red)
                        }
                    }
                }

                // القسائم
                val mine = store.usableVouchers
                if (mine.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader("قسائمك", subtitle = "من نقاط الولاء", symbol = "ticket.fill")
                        mine.forEach { v ->
                            val on = voucher?.id == v.id
                            Row(
                                Modifier.fillMaxWidth()
                                    .pressable(0.99f) { Feedback.selection(); voucher = if (on) null else v }
                                    .glassSurface(16.dp, if (on) MB.green.copy(alpha = 0.14f) else null, 0.05f)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(11.dp),
                            ) {
                                SFIcon(if (on) "largecircle.fill.circle" else "circle", size = 18.dp,
                                    tint = if (on) MB.green else MB.ink3)
                                SFIcon(v.kind.symbol, size = 14.dp, tint = MB.green)
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(v.rewardTitle, style = MBType.callout, color = MB.ink)
                                    Text("تنتهي ${Fmt.dayMonth(v.expiresAt)}", style = MBType.micro, color = MB.ink2)
                                }
                            }
                        }
                        if (voucher != null) {
                            Text("كود الخصم يُلغى تلقائياً عند استخدام قسيمة.", style = MBType.micro, color = MB.ink3)
                        }
                    }
                }

                // كود الخصم
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MBField("كود الخصم", promoText, { promoText = it }, Modifier.weight(1f),
                        placeholder = "JAYNAK25", symbol = "ticket", isRTLText = false)
                    if (quote != null && quote.discount > 0) {
                        Column(Modifier.padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            SFIcon("checkmark.circle.fill", size = 18.dp, tint = MB.green)
                            Text("−${quote.discount.moneyShort}", style = MBType.num(12, FontWeight.Bold), color = MB.green)
                        }
                    } else if (promoText.isNotEmpty()) {
                        Box(Modifier.padding(top = 24.dp)) { SFIcon("xmark.circle.fill", size = 18.dp, tint = MB.red) }
                    }
                }
                promoError?.takeIf { promoText.isNotEmpty() }?.let { Text(it, style = MBType.micro, color = MB.red) }

                // بطاقة السعر
                if (quote != null) {
                    GlassCard(tint = MB.green.copy(alpha = 0.14f)) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("السعر التقريبي", style = MBType.caption, color = MB.ink2)
                                    Text(quote.total.money, style = MBType.num(30, FontWeight.Bold), color = MB.ink)
                                }
                                Spacer(Modifier.weight(1f))
                                Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    MBChip("${"%.1f".format(Locale.US, quote.km)} كم", symbol = "point.topleft.down.to.point.bottomright.curvepath")
                                    MBChip("${quote.minutes} دقيقة", symbol = "clock.fill")
                                }
                            }

                            Row(
                                Modifier.clickableNoRipple { showBreakdown = !showBreakdown },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(if (showBreakdown) "إخفاء التفصيل" else "كيف حُسب السعر؟", style = MBType.caption, color = MB.green)
                                SFIcon(if (showBreakdown) "chevron.up" else "chevron.down", size = 10.dp, tint = MB.green)
                            }

                            AnimatedVisibility(showBreakdown, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    KeyValueRow("الأساس", quote.base.money)
                                    KeyValueRow("المسافة (${"%.1f".format(Locale.US, quote.km)} كم)", quote.distance.money)
                                    KeyValueRow("الوقت (${quote.minutes} د)", quote.time.money)
                                    if (quote.zoneSurcharge > 0) KeyValueRow("رسم المنطقة", quote.zoneSurcharge.money)
                                    if (quote.kindAdjustment != 0.0) KeyValueRow("نوع الطلب (${kind.title})", quote.kindAdjustment.money)
                                    if (quote.surge > 0) KeyValueRow("وقت الذروة", quote.surge.money, valueColor = MB.red)
                                    if (quote.discount > 0) KeyValueRow(
                                        "خصم ${quote.voucherTitle ?: quote.promoCode ?: ""}",
                                        "−" + quote.discount.money, valueColor = MB.green)
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(MB.hairline.copy(alpha = 0.3f)))
                                    KeyValueRow("الإجمالي", quote.total.money, bold = true)
                                }
                            }

                            Text("السعر تقديري وقد يتغيّر إذا تغيّر العنوان أو زاد وقت الانتظار.",
                                style = MBType.micro, color = MB.ink3, textAlign = TextAlign.End)
                        }
                    }
                }

                Spacer(Modifier.height(120.dp))
            }
        }

        // شريط التأكيد
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .navigationBarsPadding().padding(horizontal = 14.dp).padding(bottom = 10.dp)
                .glassSurface(30.dp, null, 0.22f)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("الإجمالي", style = MBType.micro, color = MB.ink2)
                Text(quote?.total?.money ?: "—", style = MBType.num(19, FontWeight.Bold), color = MB.ink)
            }
            PrimaryCTA("تأكيد الطلب", onClick = { submit() }, symbol = "checkmark.circle.fill",
                enabled = canSubmit, loading = submitting, modifier = Modifier.weight(1f))
        }
    }

    picking?.let { target ->
        PlacePicker(
            title = if (target == PickTarget.from) "نقطة الاستلام" else "نقطة التسليم",
            tint = if (target == PickTarget.from) MB.green else MB.red,
            saved = store.currentCustomer?.savedPlaces ?: emptyList(),
            onPick = { place -> if (target == PickTarget.from) pickup = place else dropoff = place },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun PointRow(title: String, place: Place?, tint: Color, symbol: String, action: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(1f) { Feedback.impact(ImpactStyle.light); action() }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).background(tint.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
            SFIcon(symbol, size = 15.dp, tint = tint)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MBType.micro, color = MB.ink2)
            Text(place?.name ?: "اختر النقطة", style = MBType.headline,
                color = if (place == null) MB.ink3 else MB.ink, maxLines = 1)
            if (place != null && (place.details.isNotEmpty() || place.zone.isNotEmpty())) {
                Text(listOf(place.zone, place.details).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MBType.micro, color = MB.ink3, maxLines = 1)
            }
        }
        SFIcon("chevron.forward", size = 12.dp, tint = MB.ink3)
    }
}
