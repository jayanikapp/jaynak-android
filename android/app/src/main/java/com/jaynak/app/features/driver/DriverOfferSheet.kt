package com.jaynak.app.features.driver

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jaynak.app.core.AppConfig
import com.jaynak.app.core.DispatchMode
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.Order
import com.jaynak.app.core.PaymentMethod
import com.jaynak.app.core.moneyShort
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.GlassButton
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBType
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.StatTile
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * مقابل DriverOfferSheet.swift — لوحة سفلية لا تُغلق بالسحب ولا بالضغط خارجها
 * (interactiveDismissDisabled) وارتفاعها ٥٢٠.
 */
@Composable
fun DriverOfferSheet(order: Order) {
    val store = LocalStore.current
    val d = store.currentDriver
    val distanceToPickup = d?.coord?.km(order.pickup.coord) ?: 0.0
    var remaining by remember(order.id) { mutableDoubleStateOf(AppConfig.offerTimeoutSeconds) }

    DisposableEffect(order.id) {
        Feedback.startOfferAlert(urgent = order.dispatchMode == DispatchMode.nearby)
        onDispose { Feedback.stopOfferAlert() }
    }
    LaunchedEffect(order.id) {
        remaining = AppConfig.offerTimeoutSeconds
        while (true) {
            delay(500)
            remaining -= 0.5
            if (remaining <= 0) {
                Feedback.stopOfferAlert()
                store.declineOffer(order)
                break
            }
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false,
            usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth().height(520.dp).clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))) {
                BrandBackground(intensity = 1.2f)
                if (order.dispatchMode == DispatchMode.nearby) {
                    Box(Modifier.fillMaxSize().background(MB.red.copy(alpha = 0.22f)))
                }
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    val ringTrack = MB.ink.copy(alpha = 0.12f)
                    val ringColor = if (remaining < 6) MB.red else MB.green
                    Box(Modifier.padding(top = 18.dp).size(92.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val w = 7.dp.toPx()
                            val inset = w / 2
                            val sz = Size(size.width - w, size.height - w)
                            drawArc(ringTrack, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(w))
                            drawArc(ringColor, -90f,
                                (360f * (remaining / AppConfig.offerTimeoutSeconds)).toFloat().coerceIn(0f, 360f),
                                false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${remaining.toInt()}", style = MBType.num(28, FontWeight.Bold), color = MB.ink)
                            Text("ثانية", style = MBType.micro, color = MB.ink2)
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (order.dispatchMode == DispatchMode.nearby) {
                            Text("🚨 طلب سريع جداً 🚨", style = MBType.title, color = MB.red)
                        } else Text("طلب جديد", style = MBType.title, color = MB.ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MBChip(order.kind.title, symbol = order.kind.symbol, filled = true)
                            MBChip(order.payment.title, symbol = order.payment.symbol,
                                tint = if (order.payment == PaymentMethod.cash) MB.red else MB.green)
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("ربحك", order.price.driverPayout.moneyShort, "banknote.fill", Modifier.weight(1f))
                        StatTile("للاستلام", "${"%.1f".format(Locale.US, distanceToPickup)} كم",
                            "location.fill", Modifier.weight(1f), tint = MB.red)
                        StatTile("المسار", "${"%.1f".format(Locale.US, order.price.km)} كم",
                            "arrow.triangle.turn.up.right.diamond.fill", Modifier.weight(1f), tint = androidx.compose.ui.graphics.Color(0xFFFF9500))
                    }

                    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp),
                            horizontalAlignment = Alignment.End) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                SFIcon("arrow.up.circle.fill", size = 18.dp, tint = MB.green)
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(order.pickup.name, style = MBType.callout, color = MB.ink)
                                    Text(listOf(order.pickup.zone, order.pickup.details).filter { it.isNotEmpty() }
                                        .joinToString(" · "), style = MBType.micro, color = MB.ink3)
                                }
                            }
                            HorizontalDivider(Modifier.alpha(0.25f), color = MB.hairline)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                SFIcon("flag.checkered", size = 18.dp, tint = MB.red)
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(order.dropoff.name, style = MBType.callout, color = MB.ink)
                                    Text(listOf(order.dropoff.zone, order.dropoff.details).filter { it.isNotEmpty() }
                                        .joinToString(" · "), style = MBType.micro, color = MB.ink3)
                                }
                            }
                            if (order.note.isNotEmpty()) {
                                HorizontalDivider(Modifier.alpha(0.25f), color = MB.hairline)
                                Text("ملاحظة العميل: ${order.note}", style = MBType.micro, color = MB.ink2)
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        GlassButton("رفض", onClick = {
                            Feedback.stopOfferAlert()
                            store.declineOffer(order)
                        }, modifier = Modifier.width(110.dp).height(54.dp), tint = MB.red)
                        PrimaryCTA("قبول الطلب", onClick = {
                            Feedback.stopOfferAlert()
                            store.acceptOffer(order)
                        }, modifier = Modifier.weight(1f), symbol = "checkmark.circle.fill")
                    }
                }
            }
        }
    }
}
