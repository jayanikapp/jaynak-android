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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AppConfig
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.Fmt
import com.jaynak.app.core.ImpactStyle
import com.jaynak.app.core.UserRole
import com.jaynak.app.design.Avatar
import com.jaynak.app.design.BrandMark
import com.jaynak.app.design.ConfirmDialog
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBType
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.Wordmark
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.pressable
import com.jaynak.app.features.shared.AccountDeletionRequestSheet
import com.jaynak.app.features.shared.ChatView
import com.jaynak.app.features.shared.LegalView

/** مقابل CustomerProfileView.swift. (ServerSettingsSheet غير مستعمل في iOS فلم يُنقل.) */
@Composable
fun CustomerProfileView() {
    val store = LocalStore.current
    var showLegal by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showSignOut by remember { mutableStateOf(false) }
    val c = store.currentCustomer

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .statusBarsPadding().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("حسابي", style = MBType.title, color = MB.ink, modifier = Modifier.padding(top = 14.dp))

            if (store.isGuest) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    GlassCard {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            BrandMark(76.dp)
                            Wordmark(21)
                            Text("تتصفّح كزائر", style = MBType.callout, color = MB.ink2)
                            Text("تقدر تشوف الأسعار وتجرّب الطلب. سجّل الدخول لتأكيد الطلبات وتتبّعها.",
                                style = MBType.caption, color = MB.ink3, textAlign = TextAlign.Center)
                        }
                    }
                    PrimaryCTA("تسجيل الدخول", symbol = "arrow.right.circle.fill", tint = MB.green,
                        onClick = { store.requestLogin(reason = "اختر نوع حسابك ثم أدخل رقمك") })
                }
            } else {
                GlassCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(c?.name ?: "زبون", size = 68.dp)
                        Text(c?.name ?: "", style = MBType.section, color = MB.ink)
                        Text("${AppConfig.countryDialCode} ${c?.phone ?: ""}", style = MBType.num(14, FontWeight.Medium), color = MB.ink2)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MBChip("زبون", symbol = "bag.fill", filled = true)
                            MBChip("عضو منذ ${c?.createdAt?.let { Fmt.monthYear(it) } ?: ""}", symbol = "calendar", tint = MB.gold)
                        }
                    }
                }
                SettingsGroup("الحساب", listOf(
                    SettingsRow("الأماكن المحفوظة", "star.fill", detail = "${c?.savedPlaces?.size ?: 0}"),
                    SettingsRow("الإشعارات", "bell.fill", detail = "مفعّلة"),
                    SettingsRow("اللغة", "globe", detail = "العربية"),
                ))
            }

            SettingsGroup("المساعدة", listOf(
                SettingsRow("الدعم الفني", "bubble.left.and.text.bubble.right.fill", action = {
                    if (store.isGuest) store.requestLogin(reason = "الدخول يربط محادثتك بطلباتك حتى يساعدك الفريق أسرع")
                    else showSupport = true
                }),
                SettingsRow("الشروط وسياسة الخصوصية", "hand.raised.fill", action = { showLegal = true }),
            ))

            if (!store.isGuest) {
                SettingsGroup("خطر", listOf(
                    SettingsRow("تسجيل الخروج", "rectangle.portrait.and.arrow.right", action = { showSignOut = true }),
                    SettingsRow(
                        if (store.myDeletionRequest != null) "طلب الحذف قيد المراجعة" else "حذف الحساب نهائياً",
                        "trash.fill", destructive = true, action = { showDelete = true }),
                ), tint = MB.red)
            }

            Text("جاينك · الإصدار 1.0\n${AppConfig.supportEmail}", style = MBType.micro, color = MB.ink3,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            Spacer(Modifier.height(150.dp))
        }
    }

    if (showLegal) LegalView(initialAudience = null, onDismiss = { showLegal = false })
    if (showSupport) ChatView("support-${c?.id ?: "x"}", "الدعم الفني", "", UserRole.customer,
        moderatable = false, onDismiss = { showSupport = false })
    if (showSignOut) ConfirmDialog("تسجيل الخروج؟", confirmText = "خروج", destructive = true,
        onConfirm = { store.signOut() }, onDismiss = { showSignOut = false })
    if (showDelete) AccountDeletionRequestSheet { showDelete = false }
}

/** مقابل SettingsRow (يُستعمل في شاشات الحساب للمندوب والإدارة أيضاً). */
data class SettingsRow(
    val title: String,
    val symbol: String,
    val detail: String? = null,
    val destructive: Boolean = false,
    val action: (() -> Unit)? = null,
)

@Composable
fun SettingsGroup(title: String, rows: List<SettingsRow>, tint: Color = Color.Unspecified) {
    val t = if (tint == Color.Unspecified) MB.green else tint
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title)
        Column(Modifier.fillMaxWidth().glassSurface(22.dp)) {
            rows.forEachIndexed { i, row ->
                Row(
                    Modifier.fillMaxWidth()
                        .pressable(0.99f, enabled = row.action != null) {
                            Feedback.impact(ImpactStyle.light); row.action?.invoke()
                        }
                        .padding(vertical = 14.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                        SFIcon(row.symbol, size = 14.dp, tint = if (row.destructive) MB.red else t)
                    }
                    Text(row.title, style = MBType.body, color = if (row.destructive) MB.red else MB.ink,
                        modifier = Modifier.weight(1f))
                    row.detail?.let { Text(it, style = MBType.caption, color = MB.ink2) }
                    if (row.action != null) SFIcon("chevron.forward", size = 11.dp, tint = MB.ink3)
                }
                if (i < rows.size - 1) {
                    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp)
                        .background(MB.hairline.copy(alpha = 0.25f)))
                }
            }
        }
    }
}
