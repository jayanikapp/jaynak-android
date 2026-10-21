package com.jaynak.app.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AppConfig
import com.jaynak.app.core.Customer
import com.jaynak.app.core.DocumentKind
import com.jaynak.app.core.Driver
import com.jaynak.app.core.DriverApproval
import com.jaynak.app.core.DriverPresence
import com.jaynak.app.core.Fmt
import com.jaynak.app.core.Id
import com.jaynak.app.core.money
import com.jaynak.app.core.moneyShort
import com.jaynak.app.design.Avatar
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.KeyValueRow
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBType
import com.jaynak.app.design.NavBar
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SecondaryCTA
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.SegmentedPicker
import com.jaynak.app.design.StarRating
import com.jaynak.app.design.StatTile
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.pressable
import java.util.Locale

private val orange = Color(0xFFFF9500)

/** مقابل AdminUsers.swift. */
@Composable
fun AdminUsers() {
    val store = LocalStore.current
    var segment by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var selectedDriver by remember { mutableStateOf<Id?>(null) }
    var granting by remember { mutableStateOf<GrantTarget?>(null) }

    val drivers = (if (search.isEmpty()) store.drivers
    else store.drivers.filter { it.name.contains(search) || it.phone.contains(search) })
        .sortedWith(Comparator { a, b ->
            if ((a.approval == DriverApproval.pending) != (b.approval == DriverApproval.pending))
                return@Comparator if (a.approval == DriverApproval.pending) -1 else 1
            b.completedOrders.compareTo(a.completedOrders)
        })
    val customers = if (search.isEmpty()) store.customers
    else store.customers.filter { it.name.contains(search) || it.phone.contains(search) }

    Box(Modifier.fillMaxSize()) {
        BrandBackground()
        Column(Modifier.fillMaxSize()) {
            NavBar(title = "المستخدمون")
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SegmentedPicker(listOf("المندوبون (${store.drivers.size})", "العملاء (${store.customers.size})"),
                    segment, { segment = it })
                AdminSearchField(search, { search = it }, "ابحث بالاسم أو الرقم")

                if (segment == 0) {
                    drivers.forEach { d -> DriverAdminRow(d) { selectedDriver = d.id } }
                } else {
                    customers.forEach { c -> CustomerAdminRow(c) { granting = it } }
                }
                Spacer(Modifier.height(150.dp))
            }
        }
    }

    selectedDriver?.let { DriverAdminSheet(it) { selectedDriver = null } }
    granting?.let { AdminGrantSheet(it) { granting = null } }
}

@Composable
private fun DriverAdminRow(driver: Driver, onClick: () -> Unit) {
    val tint = when (driver.approval) {
        DriverApproval.approved -> MB.green
        DriverApproval.pending -> orange
        else -> MB.red
    }
    Row(Modifier.fillMaxWidth().glassSurface(20.dp, null, 0.08f).pressable(onClick = onClick).padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(driver.name, size = 44.dp, tint = tint)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(driver.name, style = MBType.callout, color = MB.ink)
                Box(Modifier.size(7.dp).background(when (driver.presence) {
                    DriverPresence.online -> MB.green
                    DriverPresence.busy -> orange
                    else -> MB.ink3
                }, CircleShape))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(driver.phone, style = MBType.num(11, FontWeight.Medium), color = MB.ink3)
                Text("· ${driver.zone}", style = MBType.micro, color = MB.ink3)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                MBChip(driver.approval.title, tint = tint, filled = driver.approval == DriverApproval.pending)
                if (driver.ratingCount > 0) StarRating(driver.rating, size = 9.dp)
            }
        }
        Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${driver.completedOrders}", style = MBType.num(15, FontWeight.Bold), color = MB.ink)
            Text("طلب", style = MBType.micro, color = MB.ink3)
            SFIcon("chevron.forward", size = 10.dp, tint = MB.ink3)
        }
    }
}

@Composable
private fun CustomerAdminRow(customer: Customer, onGrant: (GrantTarget) -> Unit) {
    val store = LocalStore.current
    val orderCount = store.orders.count { it.customerID == customer.id }
    var menu by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().glassSurface(20.dp, null, 0.08f).padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(customer.name, size = 44.dp)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(customer.name, style = MBType.callout, color = MB.ink)
            Text(customer.phone, style = MBType.num(11, FontWeight.Medium), color = MB.ink3)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MBChip("$orderCount طلب", symbol = "shippingbox.fill")
                MBChip(customer.walletBalance.moneyShort, symbol = "creditcard.fill")
                if (customer.isBlocked) MBChip("موقوف", symbol = "hand.raised.fill", tint = MB.red, filled = true)
            }
        }
        Box {
            Box(Modifier.clickableNoRipple { menu = true }) { SFIcon("ellipsis.circle", size = 20.dp, tint = MB.ink2) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(
                    text = { Text("امنح نقاط", style = MBType.body, color = MB.ink) },
                    leadingIcon = { SFIcon("star.circle.fill", size = 18.dp, tint = MB.ink) },
                    onClick = { menu = false; onGrant(GrantTarget.Customer(customer.id)) },
                )
                DropdownMenuItem(
                    text = {
                        Text(if (customer.isBlocked) "إعادة التفعيل" else "إيقاف الحساب", style = MBType.body,
                            color = if (customer.isBlocked) MB.ink else MB.red)
                    },
                    leadingIcon = {
                        SFIcon(if (customer.isBlocked) "checkmark.circle" else "hand.raised.fill", size = 18.dp,
                            tint = if (customer.isBlocked) MB.ink else MB.red)
                    },
                    onClick = { menu = false; store.setBlocked(!customer.isBlocked, customer.id) },
                )
            }
        }
    }
}

/** مقابل DriverAdminSheet. */
@Composable
fun DriverAdminSheet(driverID: Id, onDismiss: () -> Unit) {
    val store = LocalStore.current
    var granting by remember { mutableStateOf<GrantTarget?>(null) }
    var showReject by remember { mutableStateOf(false) }
    var showBar by remember { mutableStateOf(false) }
    var viewingKind by remember { mutableStateOf<DocumentKind?>(null) }
    val d = store.drivers.firstOrNull { it.id == driverID }

    JSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxSize()) {
            BrandBackground(intensity = 0.5f)
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("إغلاق", style = MBType.callout, color = MB.green, modifier = Modifier.clickableNoRipple(onDismiss))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("ملف المندوب", style = MBType.headline, color = MB.ink)
                    }
                    Row(Modifier.clickableNoRipple { granting = GrantTarget.Driver(driverID) },
                        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        SFIcon("rosette", size = 14.dp, tint = MB.green)
                        Text("نقاط ورتبة", style = MBType.caption, color = MB.green)
                    }
                }
                if (d != null) {
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Avatar(d.name, size = 62.dp, tint = MB.red)
                                Text(d.name, style = MBType.section, color = MB.ink)
                                Text("${AppConfig.countryDialCode} ${d.phone}", style = MBType.num(14, FontWeight.Medium), color = MB.ink2)
                                MBChip(d.approval.title, tint = if (d.approval == DriverApproval.approved) MB.green else orange, filled = true)
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatTile("طلبات", "${d.completedOrders}", "shippingbox.fill", Modifier.weight(1f))
                            StatTile("التقييم", if (d.ratingCount == 0) "—" else "%.1f".format(Locale.US, d.rating),
                                "star.fill", Modifier.weight(1f), tint = orange)
                            StatTile("القبول", "${(d.acceptanceRate * 100).toInt()}٪", "hand.thumbsup.fill",
                                Modifier.weight(1f), tint = MB.red)
                        }

                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
                                SectionHeader("المستندات", symbol = "doc.text.fill")
                                DocRow("البطاقة المدنية", d.docs.civilID, d.docs.civilIDImage, DocumentKind.civilIDFront) { viewingKind = it }
                                DocRow("رخصة القيادة", d.docs.licenseNumber, d.docs.licenseImage, DocumentKind.license) { viewingKind = it }
                                DocRow("ملكية المركبة", d.vehicle.ownershipNumber, d.docs.ownershipImage, DocumentKind.ownership) { viewingKind = it }
                                DocRow("صور السيارة", "${d.docs.vehicleImages} صور", d.docs.vehicleImages >= 2, DocumentKind.vehicle) { viewingKind = it }
                                DocRow("السيلفي", "", d.docs.has(DocumentKind.selfie), DocumentKind.selfie) { viewingKind = it }
                                KeyValueRow("انتهاء الرخصة", Fmt.dayMonthYear(d.docs.licenseExpiry), isNumber = false)
                            }
                        }

                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
                                SectionHeader("المركبة", symbol = "car.fill")
                                KeyValueRow("النوع", "${d.vehicle.make} ${d.vehicle.model} ${d.vehicle.year}", isNumber = false)
                                KeyValueRow("اللون", d.vehicle.color, isNumber = false)
                                KeyValueRow("اللوحة", d.vehicle.plateNumber)
                            }
                        }

                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
                                SectionHeader("التوزيع والعمولة", symbol = "slider.horizontal.3")
                                Text("منطقة العمل", style = MBType.caption, color = MB.ink2)
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    store.rules.zones.forEach { z ->
                                        AdminChip(z.name, d.zone == z.name, horizontal = 12.dp, vertical = 8.dp) {
                                            store.setZone(z.name, d.id)
                                        }
                                    }
                                }
                                HorizontalDivider(Modifier.alpha(0.25f), color = MB.hairline)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("عمولة الطلب", style = MBType.callout, color = MB.ink)
                                    Spacer(Modifier.weight(1f))
                                    Text(d.effectiveCommission(store.rules).money, style = MBType.num(16, FontWeight.Bold), color = MB.ink)
                                }
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Slider(
                                        value = (d.commissionFee ?: store.rules.fixedCommission).toFloat(),
                                        onValueChange = { store.setCommission(Math.round(it * 100) / 100.0, d.id) },
                                        valueRange = 0.100f..0.500f, steps = 39,
                                        colors = SliderDefaults.colors(thumbColor = MB.green, activeTrackColor = MB.green),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                if (d.commissionFee != null) {
                                    Text("إرجاع لقيمة القواعد", style = MBType.micro, color = MB.green,
                                        modifier = Modifier.fillMaxWidth().clickableNoRipple { store.setCommission(null, d.id) })
                                }
                            }
                        }

                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (d.approval != DriverApproval.approved) {
                                PrimaryCTA("اعتماد المندوب", onClick = { store.setApproval(DriverApproval.approved, d.id) },
                                    symbol = "checkmark.seal.fill")
                            }
                            if (d.approval != DriverApproval.suspended) {
                                SecondaryCTA("تعليق الحساب", onClick = {
                                    store.setApproval(DriverApproval.suspended, d.id, "عُلّق الحساب من الإدارة")
                                }, symbol = "hand.raised.fill", tint = MB.red)
                            }
                            if (d.approval == DriverApproval.pending) {
                                SecondaryCTA("رفض مع ملاحظة", onClick = { showReject = true }, symbol = "xmark.circle", tint = MB.red)
                            }

                            HorizontalDivider(Modifier.alpha(0.25f).padding(vertical = 2.dp), color = MB.hairline)
                            val rec = store.identityOfDriver(d.id)
                            if (rec != null && rec.isBarred) {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        SFIcon("shield.slash.fill", size = 15.dp, tint = MB.red)
                                        Text("الهوية موقوفة نهائياً", style = MBType.callout, color = MB.ink)
                                    }
                                    Text(rec.barReason ?: "قرار إداري", style = MBType.micro, color = MB.ink2)
                                    if (rec.outstandingDebt > 0) {
                                        Text("دَين مستحق ${rec.outstandingDebt.money}", style = MBType.num(13, FontWeight.SemiBold), color = MB.red)
                                    }
                                }
                                SecondaryCTA("إلغاء الإيقاف", onClick = { store.unbarIdentity(d.id) },
                                    symbol = "arrow.uturn.backward", tint = MB.green)
                            } else {
                                SecondaryCTA("إيقاف الهوية نهائياً", onClick = { showBar = true },
                                    symbol = "shield.slash.fill", tint = MB.red)
                                Text("يمنع فتح حساب جديد بنفس الرقم المدني أو الهاتف.", style = MBType.micro, color = MB.ink2,
                                    modifier = Modifier.fillMaxWidth())
                            }
                        }
                        Spacer(Modifier.height(30.dp))
                    }
                }
            }
        }
    }

    viewingKind?.let { DocumentViewerSheet(d?.phone ?: "", it) { viewingKind = null } }
    granting?.let { AdminGrantSheet(it) { granting = null } }
    if (showBar) {
        TextPromptDialog("إيقاف الهوية نهائياً", "الرقم المدني ورقم الهاتف لن يفتحا حساباً جديداً بعد هذا الإجراء.",
            "السبب — مثال: دَين عمولة غير مسدَّد", "إيقاف نهائي", destructive = true,
            onConfirm = { note ->
                store.barIdentity(driverID, if (note.isEmpty()) "قرار إداري" else note)
                showBar = false; onDismiss()
            }, onDismiss = { showBar = false })
    }
    if (showReject) {
        TextPromptDialog("سبب الرفض", null, "مثال: صورة الرخصة غير واضحة", "رفض", destructive = true,
            onConfirm = { note ->
                store.setApproval(DriverApproval.rejected, driverID, if (note.isEmpty()) "المستندات غير مكتملة" else note)
                showReject = false; onDismiss()
            }, onDismiss = { showReject = false })
    }
}

@Composable
private fun DocRow(title: String, value: String, ok: Boolean, kind: DocumentKind, onOpen: (DocumentKind) -> Unit) {
    Row(Modifier.fillMaxWidth().pressable(0.99f, enabled = ok) { onOpen(kind) },
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SFIcon(if (ok) "checkmark.circle.fill" else "exclamationmark.circle.fill", size = 13.dp, tint = if (ok) MB.green else MB.red)
        Text(title, style = MBType.callout, color = MB.ink2)
        Spacer(Modifier.weight(1f))
        Text(if (value.isEmpty()) "—" else value, style = MBType.num(13, FontWeight.SemiBold), color = MB.ink)
        if (ok) SFIcon("chevron.left", size = 10.dp, tint = MB.ink3)
    }
}
