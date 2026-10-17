package com.jaynak.app.features.admin

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.ImpactStyle
import com.jaynak.app.core.Loyalty
import com.jaynak.app.core.Reward
import com.jaynak.app.core.RewardKind
import com.jaynak.app.core.money
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.GlassIconButton
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.KeyValueRow
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBField
import com.jaynak.app.design.MBToggle
import com.jaynak.app.design.MBType
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SecondaryCTA
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.SegmentedPicker
import com.jaynak.app.design.StatTile
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.design.glassSurface

/** مقابل AdminRewardsView.swift. */
@Composable
fun AdminRewardsView() {
    val store = LocalStore.current
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("متجر النقاط", subtitle = "${store.rewards.count { it.isActive }} عرض مفعّل", symbol = "gift.fill")

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("إجمالي الاستبدالات", "${store.rewards.sumOf { it.timesRedeemed }}",
                "arrow.left.arrow.right", Modifier.weight(1f), tint = MB.gold)
            StatTile("قسائم قائمة", "${store.vouchers.count { it.isUsable }}", "ticket.fill", Modifier.weight(1f))
        }

        store.rewards.forEachIndexed { i, r -> RewardRow(i, r) }

        SecondaryCTA("إضافة عرض", onClick = { showAdd = true }, symbol = "plus")

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                SectionHeader("قواعد كسب النقاط", symbol = "plus.forwardslash.minus")
                KeyValueRow("لكل طلب مكتمل", "${store.pointsRules.cDelivered} نقطة")
                KeyValueRow("لكل ١ ر.ع.", "${store.pointsRules.cPerRial} نقطة")
                Text("تُعدَّل قواعد النقاط من الإعدادات ← النقاط، وتُطبَّق من السيرفر على الجميع.", style = MBType.micro, color = MB.ink3)
            }
        }
    }

    if (showAdd) AddRewardSheet { showAdd = false }
}

@Composable
private fun RewardRow(i: Int, r: Reward) {
    val store = LocalStore.current
    fun update(f: (Reward) -> Reward) {
        store.rewards = store.rewards.mapIndexed { j, rr -> if (j == i) f(rr) else rr }; store.save()
    }
    Column(Modifier.fillMaxWidth().glassSurface(18.dp, null, 0.08f).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
            MBToggle(r.isActive, { on -> update { it.copy(isActive = on) } })
            Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                SFIcon(r.kind.symbol, size = 15.dp, tint = if (r.isActive) MB.green else MB.ink3)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(r.title, style = MBType.callout, color = MB.ink)
                Text(r.detail, style = MBType.micro, color = MB.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("${r.cost}", style = MBType.num(15, FontWeight.Bold), color = MB.gold)
                Text("نقطة", style = MBType.micro, color = MB.ink3)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("التكلفة", style = MBType.caption, color = MB.ink2)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassIconButton("minus", { update { it.copy(cost = maxOf(10, it.cost - 25)) } }, size = 32.dp, iconSize = 12.dp, tint = MB.ink)
                    GlassIconButton("plus", { update { it.copy(cost = maxOf(10, it.cost + 25)) } }, size = 32.dp, iconSize = 12.dp, tint = MB.ink)
                }
            }
            Spacer(Modifier.weight(1f))
            Text("استُبدل ${r.timesRedeemed}×", style = MBType.micro, color = MB.ink3)
            Box(Modifier.clickableNoRipple {
                store.rewards = store.rewards.filterIndexed { j, _ -> j != i }
                Feedback.impact(ImpactStyle.medium)
                store.save()
            }) { SFIcon("trash", size = 13.dp, tint = MB.red) }
        }
    }
}

/** مقابل AddRewardSheet. */
@Composable
fun AddRewardSheet(onDismiss: () -> Unit) {
    val store = LocalStore.current
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(RewardKind.percentOff) }
    var cost by remember { mutableDoubleStateOf(200.0) }
    var value by remember { mutableDoubleStateOf(25.0) }
    var maxDiscount by remember { mutableDoubleStateOf(1.0) }

    JSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxSize()) {
            BrandBackground(intensity = 0.6f)
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("إلغاء", style = MBType.callout, color = MB.green, modifier = Modifier.clickableNoRipple(onDismiss))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("عرض جديد", style = MBType.headline, color = MB.ink)
                    }
                    Spacer(Modifier.padding(horizontal = 20.dp))
                }
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.End) {
                    MBField("عنوان العرض", title, { title = it }, Modifier.fillMaxWidth(), placeholder = "خصم ٣٠٪")
                    MBField("الوصف", detail, { detail = it }, Modifier.fillMaxWidth(), placeholder = "على طلبك القادم")

                    Text("النوع", style = MBType.caption, color = MB.ink2)
                    SegmentedPicker(RewardKind.values().map { it.title }, kind.ordinal, { kind = RewardKind.values()[it] })

                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text("التكلفة بالنقاط", style = MBType.callout, color = MB.ink)
                                Spacer(Modifier.weight(1f))
                                Text("${cost.toInt()}", style = MBType.num(16, FontWeight.Bold), color = MB.ink)
                            }
                            LtrSlider(cost, { cost = Math.round(it / 25.0) * 25.0 }, 50f..2000f, 77, MB.gold)

                            if (kind == RewardKind.percentOff) {
                                Row(Modifier.fillMaxWidth()) {
                                    Text("نسبة الخصم", style = MBType.callout, color = MB.ink)
                                    Spacer(Modifier.weight(1f))
                                    Text("${value.toInt()}٪", style = MBType.num(16, FontWeight.Bold), color = MB.ink)
                                }
                                LtrSlider(value, { value = Math.round(it / 5.0) * 5.0 }, 5f..70f, 12, MB.green)
                            }

                            Row(Modifier.fillMaxWidth()) {
                                Text(if (kind == RewardKind.percentOff) "سقف الخصم" else "القيمة", style = MBType.callout, color = MB.ink)
                                Spacer(Modifier.weight(1f))
                                Text(maxDiscount.money, style = MBType.num(15, FontWeight.Bold), color = MB.ink)
                            }
                            LtrSlider(maxDiscount, { maxDiscount = Math.round(it / 0.25) * 0.25 }, 0.25f..5f, 18, MB.green)
                        }
                    }

                    PrimaryCTA("إضافة العرض", symbol = "plus", enabled = title.isNotEmpty(), modifier = Modifier.fillMaxWidth(), onClick = {
                        store.rewards = store.rewards + Reward(
                            title = title,
                            detail = if (detail.isEmpty()) "عرض ولاء" else detail,
                            cost = cost.toInt(),
                            kind = kind,
                            value = if (kind == RewardKind.percentOff) value / 100 else maxDiscount,
                            maxDiscount = maxDiscount,
                        )
                        Feedback.success()
                        store.save()
                        onDismiss()
                    })
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
private fun LtrSlider(value: Double, onChange: (Double) -> Unit, range: ClosedFloatingPointRange<Float>, steps: Int,
                      color: androidx.compose.ui.graphics.Color) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Slider(value.toFloat(), { onChange(it.toDouble()) }, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color),
            modifier = Modifier.fillMaxWidth())
    }
}
