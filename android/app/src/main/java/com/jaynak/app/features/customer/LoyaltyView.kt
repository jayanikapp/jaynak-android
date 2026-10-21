package com.jaynak.app.features.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.Fmt
import com.jaynak.app.core.Loyalty
import com.jaynak.app.core.Reward
import com.jaynak.app.design.ConfirmDialog
import com.jaynak.app.design.EmptyStateView
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.KeyValueRow
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBType
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.appearIn
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.pressable
import com.jaynak.app.features.auth.GuestGate
import kotlin.math.max
import kotlin.math.min

/** مقابل LoyaltyView.swift. */
@Composable
fun LoyaltyView() {
    val store = LocalStore.current
    var confirming by remember { mutableStateOf<Reward?>(null) }
    val c = store.currentCustomer
    val level = Loyalty.customerLevel(c?.lifetimePoints ?: 0)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .statusBarsPadding().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("نقاطي", style = MBType.title, color = MB.ink, modifier = Modifier.padding(top = 14.dp))

            if (store.isGuest) {
                GuestGate("star.circle.fill", "اجمع نقاط مع كل طلب",
                    "سجّل الدخول لتكسب نقاطاً على كل توصيلة وتستبدلها بتوصيل مجاني وخصومات.")
                Spacer(Modifier.height(200.dp))
            } else {
                // بطاقة النقاط
                Box(Modifier.appearIn(0)) {
                    GlassCard(tint = MB.gold.copy(alpha = 0.16f)) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("نقاطك", style = MBType.caption, color = MB.ink2)
                                    Text("${c?.points ?: 0}", style = MBType.num(38, FontWeight.Bold), color = MB.ink)
                                }
                                Spacer(Modifier.weight(1f))
                                Box(
                                    Modifier.size(56.dp)
                                        .shadow(10.dp, CircleShape, ambientColor = MB.gold.copy(alpha = 0.45f), spotColor = MB.gold.copy(alpha = 0.45f))
                                        .background(MB.goldGradient, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { SFIcon(level.symbol, size = 24.dp, tint = Color.White) }
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MBChip(level.title, symbol = level.symbol, tint = MB.gold, filled = true)
                                Text("إجمالي المكتسب ${c?.lifetimePoints ?: 0}", style = MBType.micro, color = MB.ink3)
                            }
                            val next = level.next
                            val lifetime = c?.lifetimePoints
                            if (next != null && lifetime != null) {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(Modifier.fillMaxWidth().height(6.dp).background(MB.ink.copy(alpha = 0.10f), CircleShape)) {
                                        Box(Modifier.align(Alignment.CenterEnd)
                                            .fillMaxWidth(min(1.0, lifetime.toDouble() / next).toFloat())
                                            .fillMaxHeight().background(MB.goldGradient, CircleShape))
                                    }
                                    Text("${max(0, next - lifetime)} نقطة للمستوى التالي", style = MBType.micro, color = MB.ink3)
                                }
                            }
                        }
                    }
                }

                // المتجر
                Column(Modifier.appearIn(1), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("استبدل نقاطك", subtitle = "اختر ما يناسبك", symbol = "gift.fill")
                    val active = store.rewards.filter { it.isActive }
                    active.forEach { r -> RewardRow(r, affordable = (c?.points ?: 0) >= r.cost) { confirming = r } }
                    if (active.isEmpty()) EmptyStateView("gift", "لا مكافآت حالياً", "ستظهر هنا عروض يمكنك شراؤها بنقاطك")
                }

                // قسائمي
                val mine = store.usableVouchers
                if (mine.isNotEmpty()) {
                    Column(Modifier.appearIn(2), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader("قسائمي", subtitle = "تُستخدم عند تأكيد الطلب", symbol = "ticket.fill")
                        mine.forEach { v ->
                            Row(
                                Modifier.fillMaxWidth().glassSurface(16.dp, MB.green.copy(alpha = 0.12f), 0.06f).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
                                    SFIcon(v.kind.symbol, size = 16.dp, tint = MB.green)
                                }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(v.rewardTitle, style = MBType.callout, color = MB.ink)
                                    Text("تنتهي ${Fmt.dayMonth(v.expiresAt)}", style = MBType.micro, color = MB.ink2)
                                }
                                MBChip("جاهزة", symbol = "checkmark", filled = true)
                            }
                        }
                    }
                }

                // كيف تكسب
                Box(Modifier.appearIn(3)) {
                    GlassCard {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionHeader("كيف تكسب النقاط؟", symbol = "questionmark.circle")
                            val pr = store.pointsRules
                            KeyValueRow("كل طلب مكتمل", "+${pr.cDelivered} نقطة")
                            KeyValueRow("كل ١ ر.ع. من قيمة الطلب", "+${pr.cPerRial} نقطة")
                            if (pr.cCancelBefore > 0) KeyValueRow("إلغاء الطلب قبل قبول المندوب", "−${pr.cCancelBefore} نقطة")
                            if (pr.cCancelAfter > 0) KeyValueRow("إلغاء الطلب بعد قبول المندوب", "−${pr.cCancelAfter} نقطة")
                            store.pointsHistory.take(8).forEach { e ->
                                KeyValueRow(e.label + if (e.ref.isEmpty()) "" else " · " + e.ref, (if (e.delta > 0) "+" else "") + e.delta)
                            }
                            Text("النقاط تُضاف تلقائياً بعد تسليم الطلب، ولا تنتهي صلاحيتها.", style = MBType.micro, color = MB.ink3)
                        }
                    }
                }
                Spacer(Modifier.height(150.dp))
            }
        }
    }

    confirming?.let { r ->
        ConfirmDialog(
            title = "استبدال النقاط؟",
            message = "سيُخصم ${r.cost} نقطة مقابل «${r.title}». الصلاحية ${r.validDays} يوماً.",
            confirmText = "استبدال",
            onConfirm = { store.redeem(r) },
            onDismiss = { confirming = null },
        )
    }
}

@Composable
private fun RewardRow(r: Reward, affordable: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().alpha(if (affordable) 1f else 0.55f)
            .pressable(0.985f, enabled = affordable, onClick = onClick)
            .glassSurface(18.dp, null, 0.08f).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(46.dp).background(
                if (affordable) MB.greenGradient else SolidColor(MB.ink.copy(alpha = 0.08f)),
                RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) { SFIcon(r.kind.symbol, size = 18.dp, tint = if (affordable) Color.White else MB.ink3) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(r.title, style = MBType.headline, color = MB.ink)
            Text(r.detail, style = MBType.micro, color = MB.ink2, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${r.cost}", style = MBType.num(17, FontWeight.Bold), color = if (affordable) MB.gold else MB.ink3)
            Text("نقطة", style = MBType.micro, color = MB.ink3)
        }
    }
}
