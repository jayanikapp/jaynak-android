package com.jaynak.app.features.driver

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AppConfig
import com.jaynak.app.core.DriverApproval
import com.jaynak.app.core.Fmt
import com.jaynak.app.core.UserRole
import com.jaynak.app.design.Avatar
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.ConfirmDialog
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.KeyValueRow
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBType
import com.jaynak.app.design.NavBar
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.StarRating
import com.jaynak.app.design.StatTile
import com.jaynak.app.features.customer.SettingsGroup
import com.jaynak.app.features.customer.SettingsRow
import com.jaynak.app.features.shared.AccountDeletionRequestSheet
import com.jaynak.app.features.shared.ChatView
import com.jaynak.app.features.shared.LegalView

/** مقابل DriverProfileView.swift. */
@Composable
fun DriverProfileView() {
    val store = LocalStore.current
    var showLegal by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showSignOut by remember { mutableStateOf(false) }
    var showDocs by remember { mutableStateOf(false) }
    var showCar by remember { mutableStateOf(false) }
    var showAvatar by remember { mutableStateOf(false) }
    val d = store.currentDriver

    Box(Modifier.fillMaxSize()) {
        BrandBackground()
        Column(Modifier.fillMaxSize()) {
            NavBar(title = "حسابي")
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (d != null) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Avatar(d.name, size = 68.dp, tint = MB.red)
                            Text(d.name, style = MBType.section, color = MB.ink)
                            Text("${AppConfig.countryDialCode} ${d.phone}", style = MBType.num(14, FontWeight.Medium), color = MB.ink2)
                            StarRating(d.rating, count = d.ratingCount, size = 14.dp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MBChip(d.approval.title,
                                    symbol = if (d.approval == DriverApproval.approved) "checkmark.seal.fill" else "clock",
                                    tint = if (d.approval == DriverApproval.approved) MB.green else Color(0xFFFF9500),
                                    filled = true)
                                MBChip(d.zone, symbol = "location.fill")
                            }
                        }
                    }

                    DriverTierCard(d)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("طلبات مكتملة", "${d.completedOrders}", "checkmark.seal.fill", Modifier.weight(1f))
                        StatTile("نسبة القبول", "${(d.acceptanceRate * 100).toInt()}٪", "hand.thumbsup.fill",
                            Modifier.weight(1f), tint = MB.red)
                    }

                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.End) {
                            SectionHeader("مركبتك", symbol = "car.fill")
                            KeyValueRow("النوع", "${d.vehicle.make} ${d.vehicle.model} ${d.vehicle.year}", isNumber = false)
                            KeyValueRow("اللون", d.vehicle.color, isNumber = false)
                            KeyValueRow("رقم اللوحة", d.vehicle.plateDisplay)
                            KeyValueRow("رخصة القيادة", d.docs.licenseNumber)
                            KeyValueRow("انتهاء الرخصة", Fmt.dayMonthYear(d.docs.licenseExpiry), isNumber = false)
                        }
                    }

                    SettingsGroup("الحساب", listOf(
                        SettingsRow("تعديل المستندات والبيانات", "doc.badge.gearshape", action = { showDocs = true }),
                        SettingsRow("صورة الحساب", "person.crop.circle.fill", detail = "تغيير", action = { showAvatar = true }),
                        SettingsRow("شكل مركبتي على الخريطة", "car.fill",
                            detail = com.jaynak.app.features.shared.CarArt.names[com.jaynak.app.core.LiveTracking.myIcon.coerceIn(0, 4)],
                            action = { showCar = true }),
                        SettingsRow("الدعم الفني", "bubble.left.and.text.bubble.right.fill", badge = store.unread("support"), action = { showSupport = true }),
                        SettingsRow("الشروط وسياسة الخصوصية", "hand.raised.fill", action = { showLegal = true }),
                    ), tint = MB.red)

                    SettingsGroup("خطر", listOf(
                        SettingsRow("تسجيل الخروج", "rectangle.portrait.and.arrow.right", action = { showSignOut = true }),
                        SettingsRow(if (store.myDeletionRequest != null) "طلب الحذف قيد المراجعة" else "حذف الحساب نهائياً",
                            "trash.fill", destructive = true, action = { showDelete = true }),
                    ), tint = MB.red)
                }
                Spacer(Modifier.height(150.dp))
            }
        }
    }

    if (showCar) VehicleIconSheet { showCar = false }
    if (showAvatar && d != null) com.jaynak.app.features.shared.AvatarEditSheet("driver", d.name) { showAvatar = false }
    if (showLegal) LegalView(initialAudience = UserRole.driver, onDismiss = { showLegal = false })
    if (showDocs && d != null) {
        JSheet(onDismiss = { showDocs = false }) { Box(Modifier.fillMaxSize()) { DriverOnboardingView(d) } }
    }
    if (showSupport) {
        ChatView("support-${d?.id ?: "x"}", "الدعم الفني", "", UserRole.driver,
            moderatable = false, onDismiss = { showSupport = false })
    }
    if (showSignOut) ConfirmDialog("تسجيل الخروج؟", confirmText = "خروج", destructive = true,
        onConfirm = { store.signOut() }, onDismiss = { showSignOut = false })
    if (showDelete) AccountDeletionRequestSheet { showDelete = false }
}
