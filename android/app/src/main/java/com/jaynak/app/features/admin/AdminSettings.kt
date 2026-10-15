package com.jaynak.app.features.admin
import com.jaynak.app.core.fillShowcaseData
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AdminPermission
import com.jaynak.app.core.AppConfig
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.Geo
import com.jaynak.app.core.OrderKind
import com.jaynak.app.core.PricingEngine
import com.jaynak.app.core.PromoCode
import com.jaynak.app.core.money
import com.jaynak.app.core.moneyShort
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.GlassIconButton
import com.jaynak.app.design.KeyValueRow
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBToggle
import com.jaynak.app.design.MBType
import com.jaynak.app.design.NavBar
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SecondaryCTA
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.SegmentedPicker
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.pressable
import java.util.Locale

/** مقابل AdminSettings.swift. */
@Composable
fun AdminSettings() {
    val store = LocalStore.current
    var section by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        BrandBackground()
        Column(Modifier.fillMaxSize()) {
            NavBar(title = "الإعدادات")
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SegmentedPicker(listOf("التسعير", "المكافآت", "الفريق"), section, { section = it })

                when (section) {
                    0 -> if (store.can(AdminPermission.managePricing)) {
                        BaseRules(); ZonesCard(); PeakCard(); PromosCard(); PreviewCard()
                    } else Locked("تعديل الأسعار")
                    1 -> if (store.can(AdminPermission.manageRewards)) AdminRewardsView() else Locked("إدارة المكافآت")
                    else -> AdminTeamView()
                }

                if (store.can(AdminPermission.manageAdmins)) {
                    if (AppConfig.demoAdminBypassEnabled) {
                        SecondaryCTA("تعبئة بيانات عرض (للقطات المتجر)", onClick = { store.fillShowcaseData() },
                            symbol = "photo.on.rectangle.angled", tint = MB.gold)
                    }
                }
                Spacer(Modifier.height(150.dp))
            }
        }
    }
}

@Composable
private fun Locked(what: String) {
    val store = LocalStore.current
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SFIcon("lock.fill", size = 28.dp, tint = MB.ink3)
        Text("$what غير متاح لدورك", style = MBType.headline, color = MB.ink)
        Text("دورك: ${store.currentAdmin?.role?.title ?: "—"} · تواصل مع المالك لترقية صلاحيتك",
            style = MBType.caption, color = MB.ink2, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PriceStepper(title: String, value: Double, step: Double, onChange: (Double) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MBType.callout, color = MB.ink)
        Spacer(Modifier.weight(1f))
        Text(value.money, style = MBType.num(15, FontWeight.Bold), color = MB.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GlassIconButton("minus", {
                Feedback.selection(); onChange(maxOf(0.0, Math.round((value - step) * 1000) / 1000.0))
            }, size = 34.dp, iconSize = 12.dp, tint = MB.ink)
            GlassIconButton("plus", {
                Feedback.selection(); onChange(Math.round((value + step) * 1000) / 1000.0)
            }, size = 34.dp, iconSize = 12.dp, tint = MB.green)
        }
    }
}

@Composable
private fun CountStepper(title: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MBType.callout, color = MB.ink)
        Spacer(Modifier.weight(1f))
        Text("$value", style = MBType.num(15, FontWeight.Bold), color = MB.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GlassIconButton("minus", { Feedback.selection(); onChange(maxOf(1, value - 1)) },
                size = 34.dp, iconSize = 12.dp, tint = MB.ink)
            GlassIconButton("plus", { Feedback.selection(); onChange(minOf(50, value + 1)) },
                size = 34.dp, iconSize = 12.dp, tint = MB.green)
        }
    }
}

@Composable
private fun BaseRules() {
    val store = LocalStore.current
    val r = store.rules
    fun set(f: (com.jaynak.app.core.PricingRules) -> com.jaynak.app.core.PricingRules) {
        store.rules = f(store.rules); store.save(); store.pushRules()
    }
    LaunchedEffect(Unit) { store.pullRules(force = true) }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.End) {
            SectionHeader("قواعد السعر", subtitle = "تُطبَّق على كل طلب جديد", symbol = "plus.forwardslash.minus")
            PriceStepper("السعر الأساسي", r.baseFare, 0.050) { v -> set { it.copy(baseFare = v) } }
            PriceStepper("سعر الكيلومتر", r.perKm, 0.010) { v -> set { it.copy(perKm = v) } }
            PriceStepper("سعر الدقيقة", r.perMinute, 0.005) { v -> set { it.copy(perMinute = v) } }
            PriceStepper("الحد الأدنى للطلب", r.minimumFare, 0.050) { v -> set { it.copy(minimumFare = v) } }
            HorizontalDivider(Modifier.alpha(0.25f), color = MB.hairline)
            PriceStepper("عمولة المنصة لكل طلب", r.fixedCommission, 0.010) { v -> set { it.copy(fixedCommission = v) } }
            PriceStepper("أرضية العمولة بعد الرتبة", r.minCommission, 0.010) { v -> set { it.copy(minCommission = v) } }
            HorizontalDivider(Modifier.alpha(0.25f), color = MB.hairline)
            SectionHeader("التوصيل السريع 🚨", subtitle = "أسعار مستقلة عن الطلب المفتوح", symbol = "bolt.fill")
            PriceStepper("السعر الأساسي للسريع", r.expressBaseFare, 0.050) { v -> set { it.copy(expressBaseFare = v) } }
            PriceStepper("سعر الكيلومتر للسريع", r.expressPerKm, 0.010) { v -> set { it.copy(expressPerKm = v) } }
            PriceStepper("سعر الدقيقة للسريع", r.expressPerMinute, 0.005) { v -> set { it.copy(expressPerMinute = v) } }
            PriceStepper("عمولة السريع من كل ريال", r.expressCommissionPerRial, 0.010) { v -> set { it.copy(expressCommissionPerRial = v) } }
            PriceStepper("عمولة السريع من كل ريال", r.expressCommissionPerRial, 0.010) { v -> set { it.copy(expressCommissionPerRial = v) } }
            HorizontalDivider(Modifier.alpha(0.25f), color = MB.hairline)
            SectionHeader("الحدود", subtitle = "تُطبَّق من السيرفر على الجميع", symbol = "slider.horizontal.3")
            CountStepper("أقصى طلبات جارية للزبون", r.maxCustomerOrders) { v -> set { it.copy(maxCustomerOrders = v) } }
            CountStepper("أقصى طلبات مفتوحة للمندوب", r.maxDriverOpen) { v -> set { it.copy(maxDriverOpen = v) } }
            CountStepper("أقصى طلبات سريعة للمندوب", r.maxDriverExpress) { v -> set { it.copy(maxDriverExpress = v) } }
            Text("عمولة ثابتة لكل طلب بلا نسبة — يعرف المندوب المخصوم مسبقاً.", style = MBType.micro, color = MB.ink2,
                modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ZonesCard() {
    val store = LocalStore.current
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
            SectionHeader("رسوم المناطق", subtitle = "يُطبَّق الأعلى بين الاستلام والتسليم", symbol = "map.fill")
            val zones = store.rules.zones
            zones.forEachIndexed { i, z ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    MBToggle(z.isActive, { on ->
                        store.rules = store.rules.copy(zones = store.rules.zones.mapIndexed { j, zz ->
                            if (j == i) zz.copy(isActive = on) else zz
                        }); store.save()
                    })
                    Text(z.name, style = MBType.callout, color = if (z.isActive) MB.ink else MB.ink3)
                    Spacer(Modifier.weight(1f))
                    Text(z.surcharge.money, style = MBType.num(13, FontWeight.SemiBold), color = MB.ink)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GlassIconButton("minus", {
                                store.rules = store.rules.copy(zones = store.rules.zones.mapIndexed { j, zz ->
                                    if (j == i) zz.copy(surcharge = maxOf(0.0, Math.round((zz.surcharge - 0.050) * 1000) / 1000.0)) else zz
                                }); store.save()
                            }, size = 32.dp, iconSize = 12.dp, tint = MB.ink)
                            GlassIconButton("plus", {
                                store.rules = store.rules.copy(zones = store.rules.zones.mapIndexed { j, zz ->
                                    if (j == i) zz.copy(surcharge = Math.round((zz.surcharge + 0.050) * 1000) / 1000.0) else zz
                                }); store.save()
                            }, size = 32.dp, iconSize = 12.dp, tint = MB.ink)
                        }
                    }
                }
                if (i < zones.size - 1) HorizontalDivider(Modifier.alpha(0.2f), color = MB.hairline)
            }
        }
    }
}

@Composable
private fun PeakCard() {
    val store = LocalStore.current
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
            SectionHeader("وقت الذروة", subtitle = "ساعات يُضاعف فيها السعر", symbol = "clock.badge.exclamationmark.fill")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("معامل الذروة", style = MBType.callout, color = MB.ink)
                Spacer(Modifier.weight(1f))
                Text("×${"%.2f".format(Locale.US, store.rules.peakMultiplier)}", style = MBType.num(16, FontWeight.Bold), color = MB.ink)
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Slider(
                    value = store.rules.peakMultiplier.toFloat(),
                    onValueChange = {
                        store.rules = store.rules.copy(peakMultiplier = Math.round(it * 20) / 20.0); store.save()
                    },
                    valueRange = 1.0f..2.0f, steps = 19,
                    colors = SliderDefaults.colors(thumbColor = MB.red, activeTrackColor = MB.red),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("الساعات المفعّلة", style = MBType.caption, color = MB.ink2)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0 until 24).chunked(6).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { h ->
                                val on = store.rules.peakHours.contains(h)
                                Text("$h", style = MBType.num(12, FontWeight.SemiBold),
                                    color = if (on) Color.White else MB.ink2, textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                        .background(if (on) MB.red else MB.ink.copy(alpha = 0.06f), RoundedCornerShape(9.dp))
                                        .pressable {
                                            Feedback.selection()
                                            val hours = if (on) store.rules.peakHours.filter { it != h } else store.rules.peakHours + h
                                            store.rules = store.rules.copy(peakHours = hours); store.save()
                                        }.padding(vertical = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromosCard() {
    val store = LocalStore.current
    var newCode by remember { mutableStateOf("") }
    var newPercent by remember { mutableDoubleStateOf(25.0) }

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
            SectionHeader("أكواد الخصم", symbol = "ticket.fill")
            store.promos.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    MBToggle(p.isActive, { on ->
                        store.promos = store.promos.mapIndexed { j, pp -> if (j == i) pp.copy(isActive = on) else pp }; store.save()
                    })
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(p.code, style = MBType.num(14, FontWeight.Bold), color = MB.ink)
                        Text("${(p.percentOff * 100).toInt()}٪ · حتى ${p.maxDiscount.moneyShort}${if (p.newCustomersOnly) " · جديد فقط" else ""}",
                            style = MBType.micro, color = MB.ink2)
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("${p.timesUsed}", style = MBType.num(13, FontWeight.Bold), color = MB.ink)
                        Text("استخدام", style = MBType.micro, color = MB.ink3)
                    }
                }
                HorizontalDivider(Modifier.alpha(0.2f), color = MB.hairline)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(42.dp).glassSurface(12.dp, null, 0.04f).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart) {
                    if (newCode.isEmpty()) Text("كود جديد", style = MBType.num(14, FontWeight.SemiBold), color = MB.ink3)
                    BasicTextField(newCode, { newCode = it }, singleLine = true,
                        textStyle = MBType.num(14, FontWeight.SemiBold).copy(color = MB.ink),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        cursorBrush = SolidColor(MB.green), modifier = Modifier.fillMaxWidth())
                }
                Text("${newPercent.toInt()}٪", style = MBType.num(14, FontWeight.Bold), color = MB.ink,
                    textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlassIconButton("minus", { newPercent = (newPercent - 5).coerceAtLeast(5.0) }, size = 34.dp, iconSize = 12.dp, tint = MB.ink)
                        GlassIconButton("plus", { newPercent = (newPercent + 5).coerceAtMost(70.0) }, size = 34.dp, iconSize = 12.dp, tint = MB.ink)
                    }
                }
                GlassIconButton("plus", {
                    if (newCode.isNotEmpty()) {
                        store.promos = store.promos + PromoCode(code = newCode.uppercase(), percentOff = newPercent / 100, maxDiscount = 1.500)
                        newCode = ""
                        Feedback.success()
                        store.save()
                    }
                }, size = 42.dp, iconSize = 13.dp, tint = MB.green, prominent = true)
            }
        }
    }
}

@Composable
private fun PreviewCard() {
    val store = LocalStore.current
    val q = PricingEngine.quote(Geo.place("الخوير - جمعية"), Geo.place("سوق مطرح"), OrderKind.parcel, store.rules)
    GlassCard(Modifier.fillMaxWidth(), tint = MB.green.copy(alpha = 0.12f)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp), horizontalAlignment = Alignment.End) {
            SectionHeader("معاينة", subtitle = "برج التحرير ← جمعية حولي · طرد", symbol = "eye.fill")
            KeyValueRow("المسافة", "${"%.1f".format(Locale.US, q.km)} كم")
            KeyValueRow("السعر للعميل", q.total.money, bold = true)
            KeyValueRow("عمولة المنصة", q.commission.money, valueColor = MB.green)
            KeyValueRow("صافي المندوب", q.driverPayout.money, valueColor = MB.red)
        }
    }
}
