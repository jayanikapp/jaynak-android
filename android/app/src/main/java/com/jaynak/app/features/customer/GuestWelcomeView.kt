package com.jaynak.app.features.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.UserRole
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.BrandMark
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.Metrics
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SectionHeader

/** مقابل GuestWelcomeView.swift. */
@Composable
fun GuestWelcomeView() {
    val store = LocalStore.current
    val howItWorks = listOf(
        "حدّد نقطة الاستلام والتسليم" to "بموقعك الحالي أو من الخريطة",
        "شوف السعر قبل التأكيد" to "محسوب بالمسافة والوقت والولاية",
        "طلبك يوصل لأقرب ٣ مندوبين" to "أول من يقبل يثبت عليه الطلب",
        "تابعه على الخريطة لحظة بلحظة" to "واتصل به أو راسله متى شئت",
    )
    data class Promise(val symbol: String, val title: String, val note: String, val tint: Color)
    val promises = listOf(
        Promise("ticket.fill", "خصم ٢٥٪ على أول طلب", "يُحسب تلقائياً عند أول توصيلة", MB.red),
        Promise("checkmark.seal.fill", "تسعير شفّاف", "تشوف السعر كاملاً قبل ما تأكّد", MB.gold),
        Promise("location.fill", "تتبّع لحظة بلحظة", "تعرف وين مندوبك طول الطريق", MB.green),
        Promise("creditcard.fill", "ادفع نقداً أو من المحفظة", "اختر ما يناسبك عند الطلب", MB.teal),
    )

    Box(Modifier.fillMaxSize()) {
        BrandBackground()
        com.jaynak.app.i18n.LanguageChip(Modifier.align(Alignment.TopEnd).statusBarsPadding())

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = Metrics.gutter).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // الترويسة
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.padding(top = 10.dp)) { BrandMark(74.dp) }
                Text("جاينك", style = MBType.display.copy(brush = MB.greenGradient))
                Text("وصّل أي شي في عُمان", style = MBType.title, color = MB.ink, textAlign = TextAlign.Center)
                Text(
                    "مستندات، طرود، ومشاوير — في كل محافظات السلطنة، يوصلها لك مندوب قريب منك وتتابعه على الخريطة لحظة بلحظة.",
                    style = MBType.body, color = MB.ink2, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 6.dp))
            }

            // كيف يعمل
            GlassCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionHeader("كيف يعمل جاينك؟", symbol = "sparkles")
                    howItWorks.forEachIndexed { i, step ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier.size(34.dp).background(
                                    if (i == 0) MB.greenGradient else SolidColor(MB.green.copy(alpha = 0.14f)),
                                    CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${i + 1}", style = MBType.num(14, FontWeight.Bold),
                                    color = if (i == 0) Color.White else MB.green)
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(step.first, style = MBType.callout, color = MB.ink)
                                Text(step.second, style = MBType.micro, color = MB.ink2)
                            }
                        }
                    }
                }
            }

            // الوعود
            GlassCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    promises.forEach { p ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(30.dp).background(p.tint.copy(alpha = 0.13f), CircleShape),
                                contentAlignment = Alignment.Center) { SFIcon(p.symbol, size = 15.dp, tint = p.tint) }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(p.title, style = MBType.callout, color = MB.ink)
                                Text(p.note, style = MBType.micro, color = MB.ink2)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(215.dp))
        }

        // الأزرار السفلية
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(MB.sunken.copy(alpha = 0.88f))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Metrics.gutter).padding(top = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryCTA(
                "سجّل الآن وابدأ", symbol = "arrow.left.circle.fill", tint = MB.green,
                onClick = { store.requestLogin(UserRole.customer, "سجّل برقمك لتطلب وتتابع طلباتك") },
            )
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MB.red.copy(alpha = 0.12f))
                    .clickable { store.requestLogin(UserRole.driver, "سجّل كمندوب واستلم طلبات قريبة منك") }
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SFIcon("car.fill", size = 15.dp, tint = MB.red)
                Spacer(Modifier.size(7.dp))
                Text("أنا مندوب — سجّل معنا", style = MBType.callout, color = MB.red)
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                SFIcon("lock.fill", size = 9.dp, tint = MB.green)
                Spacer(Modifier.size(5.dp))
                Text("رقمك للتحقّق فقط — لا نشاركه مع أحد", style = MBType.micro, color = MB.ink2)
            }
        }
    }
}
