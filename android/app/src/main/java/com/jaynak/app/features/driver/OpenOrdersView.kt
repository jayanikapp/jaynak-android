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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.Dispatch
import com.jaynak.app.core.Driver
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.ImpactStyle
import com.jaynak.app.core.Order
import com.jaynak.app.core.PaymentMethod
import com.jaynak.app.core.money
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.ConfirmDialog
import com.jaynak.app.design.EmptyStateView
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.GlassIconButton
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBType
import com.jaynak.app.design.NavBar
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.pressable
import com.jaynak.app.features.shared.PlacePicker
import java.util.Locale

/** مقابل OpenOrdersView.swift. */
@Composable
fun OpenOrdersView() {
    val store = LocalStore.current
    var confirming by remember { mutableStateOf<Order?>(null) }
    val d = store.currentDriver
    val board = store.openBoard

    Box(Modifier.fillMaxSize()) {
        BrandBackground()
        Column(Modifier.fillMaxSize()) {
            NavBar(title = "طلبات مفتوحة")
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (d != null && d.isDestinationMode) DestinationBanner(d)

                if (board.isEmpty()) {
                    EmptyStateView("tray", "لا طلبات مفتوحة الآن",
                        "الطلبات التي ينشرها العملاء لكل المندوبين تظهر هنا")
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        Text("${board.size} طلب متاح", style = MBType.caption, color = MB.ink2)
                    }
                    board.forEach { item -> BoardRow(item) { confirming = item.order } }
                }
                Spacer(Modifier.height(150.dp))
            }
        }
    }

    confirming?.let { o ->
        ConfirmDialog(
            title = "قبول الطلب؟",
            message = "${o.pickup.name} ← ${o.dropoff.name}\nربحك ${o.price.driverPayout.money}\n\nبعد القبول يظهر لك رقم العميل ويثبت الطلب عليك.",
            confirmText = "قبول", cancelText = "إلغاء",
            onConfirm = { store.acceptOpenOrder(o); confirming = null },
            onDismiss = { confirming = null },
        )
    }
}

@Composable
private fun DestinationBanner(d: Driver) {
    Row(
        Modifier.fillMaxWidth().glassSurface(18.dp, MB.green.copy(alpha = 0.14f), 0.08f).padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        SFIcon("location.north.line.fill", size = 18.dp, tint = MB.green)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("وضع الوجهة: ${d.destination?.name ?: ""}", style = MBType.callout, color = MB.ink)
            Text("الطلبات على مسارك مرفوعة للأعلى", style = MBType.micro, color = MB.ink2)
        }
    }
}

@Composable
private fun BoardRow(item: Dispatch.BoardItem, onTap: () -> Unit) {
    val o = item.order
    Column(
        Modifier.fillMaxWidth()
            .glassSurface(20.dp, if (item.onPath) MB.green.copy(alpha = 0.10f) else null, 0.08f)
            .pressable(0.985f) { Feedback.impact(ImpactStyle.medium); onTap() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp), horizontalAlignment = Alignment.End,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (item.onPath) MBChip("على مسارك", symbol = "location.north.line.fill", tint = MB.green, filled = true)
            if (o.promotedToOpen) MBChip("لم يجد مندوباً قريباً", symbol = "clock.arrow.circlepath", tint = MB.gold)
            MBChip(o.kind.title, symbol = o.kind.symbol)
            Spacer(Modifier.weight(1f))
            Text(o.price.driverPayout.money, style = MBType.num(17, FontWeight.Bold), color = MB.green)
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SFIcon("arrow.up.circle.fill", size = 18.dp, tint = MB.green)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(o.pickup.name, style = MBType.callout, color = MB.ink)
                    Text(o.pickup.zone, style = MBType.micro, color = MB.ink3)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SFIcon("flag.checkered", size = 18.dp, tint = MB.red)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(o.dropoff.name, style = MBType.callout, color = MB.ink)
                    Text(o.dropoff.zone, style = MBType.micro, color = MB.ink3)
                }
            }
        }
        HorizontalDivider(Modifier.alpha(0.25f), color = MB.hairline)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Label("للاستلام", "${"%.1f".format(Locale.US, item.km)} كم", "location.fill")
            Label("المسار", "${"%.1f".format(Locale.US, o.price.km)} كم", "arrow.triangle.turn.up.right.diamond.fill")
            Label("الدفع", o.payment.title, o.payment.symbol)
            Spacer(Modifier.weight(1f))
            Text("اضغط للقبول", style = MBType.micro, color = MB.green)
        }
    }
}

@Composable
private fun Label(title: String, value: String, symbol: String) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            SFIcon(symbol, size = 9.dp, tint = MB.ink3)
            Text(title, style = MBType.micro, color = MB.ink3)
        }
        Text(value, style = MBType.num(12, FontWeight.SemiBold), color = MB.ink)
    }
}

/** مقابل DriverPreferencesCard — نطاق الاستقبال ووضع الوجهة. */
@Composable
fun DriverPreferencesCard(driver: Driver, modifier: Modifier = Modifier) {
    val store = LocalStore.current
    var showPicker by remember { mutableStateOf(false) }

    GlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.End) {
            SectionHeader("ما الذي يصلني؟", symbol = "slider.horizontal.3")

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("نطاق استقبال الطلبات", style = MBType.callout, color = MB.ink)
                    Spacer(Modifier.weight(1f))
                    Text("${"%.0f".format(Locale.US, driver.acceptRadiusKm)} كم",
                        style = MBType.num(16, FontWeight.Bold), color = MB.green)
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    val maxR = Dispatch.maxRadiusKm.toFloat()
                    Slider(
                        value = driver.acceptRadiusKm.toFloat(),
                        onValueChange = { store.setAcceptRadius(Math.round(it).toDouble()) },
                        valueRange = 1f..maxR,
                        steps = (maxR - 1f).toInt() - 1,
                        colors = SliderDefaults.colors(thumbColor = MB.green, activeTrackColor = MB.green),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text("كلما ضاق النطاق قلّت مسافة الوصول للاستلام وزاد صافي ربحك.",
                    style = MBType.micro, color = MB.ink3)
            }

            HorizontalDivider(Modifier.alpha(0.25f), color = MB.hairline)

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("وضع الوجهة", style = MBType.callout, color = MB.ink)
                        Text(if (driver.isDestinationMode) "تصلك الطلبات على مسارك فقط"
                        else "حدّد وجهتك لتصلك طلبات في طريقك", style = MBType.micro, color = MB.ink2)
                    }
                    Spacer(Modifier.weight(1f))
                    if (driver.isDestinationMode) {
                        GlassIconButton("xmark", { store.setDestination(null) },
                            size = 34.dp, iconSize = 12.dp, tint = MB.red)
                    }
                }

                Row(
                    Modifier.fillMaxWidth()
                        .glassSurface(16.dp, if (driver.isDestinationMode) MB.green.copy(alpha = 0.14f) else null, 0.05f)
                        .pressable(0.99f) { Feedback.impact(ImpactStyle.light); showPicker = true }
                        .padding(13.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    SFIcon("location.north.line.fill", size = 16.dp,
                        tint = if (driver.isDestinationMode) MB.green else MB.ink3)
                    Text(driver.destination?.name ?: "اختر وجهتك", style = MBType.callout,
                        color = if (driver.destination == null) MB.ink3 else MB.ink)
                    Spacer(Modifier.weight(1f))
                    SFIcon("chevron.forward", size = 11.dp, tint = MB.ink3)
                }

                if (driver.isDestinationMode) {
                    Text("مثال: أنت في صحار ووجهتك القرم — يصلك ما يقع على الطريق ويقرّبك من وجهتك، لا ما يرجعك للخلف.",
                        style = MBType.micro, color = MB.ink3, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
        }
    }

    if (showPicker) {
        PlacePicker(title = "وجهتك", tint = MB.green,
            onPick = { store.setDestination(it) }, onDismiss = { showPicker = false })
    }
}
