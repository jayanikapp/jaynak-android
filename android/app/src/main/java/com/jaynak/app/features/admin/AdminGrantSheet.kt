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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.DriverTier
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.Id
import com.jaynak.app.core.Loyalty
import com.jaynak.app.design.Avatar
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.GlassIconButton
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBToggle
import com.jaynak.app.design.MBType
import com.jaynak.app.design.Metrics
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.design.glassChip
import com.jaynak.app.design.pressable
import kotlin.math.abs

/** مقابل AdminGrantSheet.Target. */
sealed class GrantTarget {
    data class Customer(val id: Id) : GrantTarget()
    data class Driver(val id: Id) : GrantTarget()
}

private val presets = listOf(25, 50, 100, 250, 500)

/** مقابل AdminGrantSheet.swift — منح الإدارة اليدوي للنقاط والرتب. */
@Composable
fun AdminGrantSheet(target: GrantTarget, onDismiss: () -> Unit) {
    val store = LocalStore.current
    var amount by remember { mutableIntStateOf(100) }
    var affectsLevel by remember { mutableStateOf(true) }

    val customer = (target as? GrantTarget.Customer)?.let { t -> store.customers.firstOrNull { it.id == t.id } }
    val driver = (target as? GrantTarget.Driver)?.let { t -> store.drivers.firstOrNull { it.id == t.id } }
    val name = customer?.name ?: driver?.name ?: "—"
    val tint = if (driver != null) MB.red else MB.green

    JSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxSize()) {
            BrandBackground(intensity = 0.9f)
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("تمّ", style = MBType.callout, color = MB.green, modifier = Modifier.clickableNoRipple(onDismiss))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("منح نقاط", style = MBType.headline, color = MB.ink)
                    }
                    Spacer(Modifier.padding(horizontal = 14.dp))
                }
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .padding(Metrics.gutter).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    // الحالة الآن
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(name, size = 44.dp)
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(name, style = MBType.headline, color = MB.ink)
                                    Text(customer?.phone ?: driver?.phone ?: "", style = MBType.num(11, FontWeight.Medium), color = MB.ink3)
                                }
                            }
                            HorizontalDivider(color = MB.hairline)
                            customer?.let { c ->
                                GrantRow("النقاط المتاحة", "${c.points}")
                                GrantRow("النقاط التراكمية", "${c.lifetimePoints}")
                                GrantRow("المستوى", Loyalty.customerLevel(c.lifetimePoints).title)
                            }
                            driver?.let { d ->
                                GrantRow("نقاط الرتبة", "${d.tierPoints}")
                                GrantRow("الرتبة", d.tier.title)
                                GrantRow("الطلبات المكتملة", "${d.completedOrders}")
                            }
                        }
                    }

                    // المقدار
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
                            SectionHeader("المقدار", symbol = "plus.forwardslash.minus")

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                presets.forEach { p ->
                                    Text("$p", style = MBType.num(13, FontWeight.SemiBold),
                                        color = if (amount == p) Color.White else MB.ink,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.weight(1f)
                                            .glassChip()
                                            .background(if (amount == p) tint else Color.Transparent, RoundedCornerShape(12.dp))
                                            .pressable { Feedback.selection(); amount = p }
                                            .padding(vertical = 9.dp))
                                }
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("نقاط", style = MBType.caption, color = MB.ink2)
                                Spacer(Modifier.weight(1f))
                                Text("$amount", style = MBType.num(17, FontWeight.Bold), color = tint)
                                GlassIconButton("minus", { amount = (amount - 25).coerceAtLeast(-5000) }, size = 34.dp, iconSize = 13.dp, tint = MB.ink)
                                GlassIconButton("plus", { amount = (amount + 25).coerceAtMost(5000) }, size = 34.dp, iconSize = 13.dp, tint = MB.ink)
                            }

                            if (customer != null) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("ترفع المستوى أيضاً", style = MBType.caption, color = MB.ink)
                                        Text("أطفئها لتمنحه نقاطاً للصرف بلا تغيير رتبته", style = MBType.micro, color = MB.ink2)
                                    }
                                    MBToggle(affectsLevel, { affectsLevel = it }, tint = tint)
                                }
                            }

                            PrimaryCTA(
                                if (amount >= 0) "امنح ${abs(amount)} نقطة" else "اخصم ${abs(amount)} نقطة",
                                onClick = {
                                    when (target) {
                                        is GrantTarget.Customer -> store.adminGrantPoints(amount, target.id, affectsLevel)
                                        is GrantTarget.Driver -> store.adminGrantTierPoints(amount, target.id)
                                    }
                                },
                                symbol = if (amount >= 0) "plus.circle.fill" else "minus.circle.fill",
                                tint = tint, enabled = amount != 0,
                            )
                            Text("الخصم بإدخال رقم سالب. لا تنزل النقاط تحت الصفر.", style = MBType.micro, color = MB.ink2,
                                modifier = Modifier.fillMaxWidth())
                        }
                    }

                    if (driver != null) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
                                SectionHeader("ارفع الرتبة مباشرة", symbol = "rosette")
                                DriverTier.values().forEach { t ->
                                    Row(Modifier.fillMaxWidth().pressable(0.99f) {
                                        (target as? GrantTarget.Driver)?.let { store.adminSetDriverTier(t, it.id) }
                                    }.padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        SFIcon(if (driver.tier == t) "largecircle.fill.circle" else "circle", size = 18.dp,
                                            tint = if (driver.tier == t) tint else MB.ink3)
                                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Text(t.title, style = MBType.callout, color = MB.ink)
                                            Text("تبدأ من ${t.minPoints} نقطة", style = MBType.micro, color = MB.ink2)
                                        }
                                    }
                                }
                                Text("رفع الرتبة يضبط نقاطها لحدّها الأدنى حتى لا تهبط بعد أول تحديث.",
                                    style = MBType.micro, color = MB.ink2, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GrantRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(key, style = MBType.caption, color = MB.ink2)
        Spacer(Modifier.weight(1f))
        Text(value, style = MBType.num(14, FontWeight.Bold), color = MB.ink)
    }
}
