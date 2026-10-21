package com.jaynak.app.features.driver

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.Driver
import com.jaynak.app.core.DriverTier
import com.jaynak.app.core.Loyalty
import com.jaynak.app.core.money
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBType
import com.jaynak.app.design.SFIcon

/** مقابل DriverTierCard.swift. */
@Composable
fun DriverTierCard(driver: Driver, expanded: Boolean = true, modifier: Modifier = Modifier) {
    val store = LocalStore.current
    val tier = driver.tier
    val tierTint = when (tier) {
        DriverTier.bronze -> Color(0.72f, 0.45f, 0.20f)
        DriverTier.silver -> Color(0.68f, 0.68f, 0.68f)
        DriverTier.gold -> MB.gold
        DriverTier.platinum -> MB.green
    }

    GlassCard(modifier.fillMaxWidth(), tint = tierTint.copy(alpha = 0.16f)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp)
                        .shadow(9.dp, CircleShape, ambientColor = tierTint.copy(alpha = 0.45f),
                            spotColor = tierTint.copy(alpha = 0.45f))
                        .background(Brush.linearGradient(listOf(tierTint, tierTint.copy(alpha = 0.5f))), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { SFIcon(tier.symbol, size = 22.dp, tint = Color.White) }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.End) {
                    Text("رتبتك: ${tier.title}", style = MBType.ar(19, FontWeight.Bold), color = MB.ink)
                    Text("رصيدك: ${driver.tierPoints} نقطة", style = MBType.caption, color = MB.ink2)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MBChip("عمولتك ${driver.effectiveCommission(store.rules).money} للطلب",
                    symbol = "tag.fill", tint = MB.green, filled = true)
                if (tier.commissionDiscount > 0) {
                    val saved = (driver.commissionFee ?: store.rules.fixedCommission) -
                        driver.effectiveCommission(store.rules)
                    MBChip("وفّرت ${saved.money}", symbol = "arrow.down", tint = MB.gold)
                }
            }

            val next = tier.next
            if (next != null) {
                val progress by animateFloatAsState(driver.tierProgress.toFloat(), label = "tier")
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("التالي: ${next.title}", style = MBType.caption, color = MB.ink2)
                        Spacer(Modifier.weight(1f))
                        Text("باقي ${maxOf(0, next.minPoints - driver.tierPoints)} نقطة",
                            style = MBType.num(12, FontWeight.SemiBold), color = tierTint)
                    }
                    Box(Modifier.fillMaxWidth().height(7.dp)
                        .background(MB.ink.copy(alpha = 0.10f), CircleShape),
                        contentAlignment = Alignment.CenterEnd) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f))
                            .background(Brush.horizontalGradient(listOf(tierTint, tierTint.copy(alpha = 0.6f))), CircleShape))
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SFIcon("crown.fill", size = 12.dp, tint = MB.gold)
                    Text("أعلى رتبة — أحسنت", style = MBType.caption, color = MB.gold)
                }
            }

            if (expanded) {
                HorizontalDivider(Modifier.alpha(0.25f), color = MB.hairline)
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp),
                    horizontalAlignment = Alignment.End) {
                    Text("مزايا رتبتك", style = MBType.callout, color = MB.ink)
                    tier.perks.forEach { perk ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            SFIcon("checkmark.circle.fill", size = 11.dp, tint = MB.green)
                            Text(perk, style = MBType.caption, color = MB.ink2)
                        }
                    }
                }
                HorizontalDivider(Modifier.alpha(0.25f), color = MB.hairline)
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.End) {
                    Text("كيف تتغيّر النقاط", style = MBType.callout, color = MB.ink)
                    val pr = store.pointsRules
                    if (pr.dAccept != 0) PointRow("قبول طلب", pr.dAccept)
                    PointRow("توصيلة مكتملة", pr.dDelivered)
                    if (pr.dDeclineBefore != 0) PointRow("رفض طلب قبل قبوله", -pr.dDeclineBefore)
                    if (pr.dCancelAfter != 0) PointRow("إلغاء طلب قبلته", -pr.dCancelAfter)
                    store.pointsHistory.take(6).forEach { e -> PointRow(e.label, e.delta) }
                }
            }
        }
    }
}

@Composable
private fun PointRow(title: String, delta: Int) {
    Row(Modifier.fillMaxWidth()) {
        Text(title, style = MBType.caption, color = MB.ink2)
        Spacer(Modifier.weight(1f))
        Text(if (delta > 0) "+$delta" else "$delta", style = MBType.num(13, FontWeight.Bold),
            color = if (delta > 0) MB.green else if (delta < 0) MB.red else MB.ink3)
    }
}
