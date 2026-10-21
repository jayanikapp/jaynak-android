package com.jaynak.app.features.admin

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AppConfig
import com.jaynak.app.core.LiveTracking
import com.jaynak.app.core.money
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.EmptyStateView
import com.jaynak.app.design.GlassButton
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.ProminentButton
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.design.glassSurface
import com.jaynak.app.features.shared.NavigationLauncher

private val LIST_ORANGE = Color(0xFFFF9500)
private val LIST_BLUE = Color(0xFF0A84FF)

internal fun lastEight(p: String) = p.filter { it.isDigit() }.takeLast(8)

@Composable
private fun listTint(s: String): Color = when (s) {
    "searching" -> LIST_ORANGE
    "accepted", "assigned", "pickingUp" -> LIST_BLUE
    else -> MB.green
}

private fun listTitle(s: String) = when (s) {
    "searching" -> "قيد البحث عن مندوب"
    "accepted", "assigned", "pickingUp" -> "المندوب في الطريق للاستلام"
    "pickedUp", "delivering" -> "في الطريق للتسليم"
    else -> s
}

/** صف تواصل: الاسم + الرقم + اتصال + واتساب. */
@Composable
internal fun ContactRow(title: String, name: String?, phone: String, tint: Color) {
    val ctx = LocalContext.current
    val l8 = lastEight(phone)
    Column(Modifier.fillMaxWidth().glassSurface(16.dp, null, 0.06f).padding(10.dp),
        horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Text(title + (if (!name.isNullOrBlank()) "  ·  " + name else ""), style = MBType.caption, color = MB.ink2)
            Text(AppConfig.countryDialCode + " " + l8, style = MBType.num(16, FontWeight.SemiBold), color = MB.ink)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProminentButton("اتصال", { NavigationLauncher.call(ctx, l8) }, Modifier.weight(1f),
                tint = tint, symbol = "phone.fill", vertical = 9.dp)
            GlassButton("واتساب", { NavigationLauncher.whatsapp(ctx, l8) }, Modifier.weight(1f),
                tint = MB.green, symbol = "message.fill", vertical = 9.dp)
        }
    }
}

/** قائمة كاملة وواضحة بكل الطلبات المباشرة. */
@Composable
internal fun OrderListSheet(rows: List<LiveTracking.FleetOrder>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    JSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxSize()) {
            BrandBackground(intensity = 0.5f)
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("إغلاق", style = MBType.callout, color = MB.green, modifier = Modifier.clickableNoRipple(onDismiss))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("الطلبات المباشرة (" + rows.size + ")", style = MBType.headline, color = MB.ink)
                    }
                    Spacer(Modifier.width(40.dp))
                }
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (rows.isEmpty()) EmptyStateView("shippingbox", "لا طلبات مباشرة", "ستظهر الطلبات هنا لحظة وصولها")
                    rows.forEach { r -> OrderRowCard(r, onPick) }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
private fun OrderRowCard(r: LiveTracking.FleetOrder, onPick: (String) -> Unit) {
    val tint = listTint(r.status)
    Column(Modifier.fillMaxWidth().glassSurface(20.dp, null, 0.08f).padding(14.dp),
        horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(r.total.money, style = MBType.num(15, FontWeight.Bold), color = MB.green)
            Spacer(Modifier.weight(1f))
            Text(listTitle(r.status), style = MBType.caption, color = tint)
            Box(Modifier.size(9.dp).background(tint, CircleShape))
            Text(r.ref, style = MBType.num(15, FontWeight.Bold), color = MB.ink)
        }
        if (r.statusLine.isNotBlank()) Text(r.statusLine, style = MBType.caption, color = MB.ink2)
        Text("من: " + r.pickup.name, style = MBType.micro, color = MB.ink2)
        Text("إلى: " + r.dropoff.name, style = MBType.micro, color = MB.ink2)
        if (r.customerPhone.isNotEmpty()) ContactRow("الزبون", null, r.customerPhone, MB.green)
        val d = r.driver
        if (d != null && d.phone.isNotEmpty()) ContactRow("المندوب", d.name, d.phone, LIST_BLUE)
        else Text("لم يُسند لمندوب بعد", style = MBType.caption, color = LIST_ORANGE)
        GlassButton("عرض على الخريطة", { onPick(r.id) }, Modifier.fillMaxWidth(), symbol = "map.fill", vertical = 10.dp)
    }
}
