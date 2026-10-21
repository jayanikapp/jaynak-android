package com.jaynak.app.features.customer

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.RepeatMode.Restart
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.Dispatch
import com.jaynak.app.core.Order
import com.jaynak.app.core.DispatchMode
import com.jaynak.app.core.nowMs
import com.jaynak.app.design.Avatar
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.glassSurface
import kotlinx.coroutines.delay
import java.util.Locale

/** مقابل SearchingPanel.swift. */
@Composable
fun SearchingPanel(order: Order, modifier: Modifier = Modifier) {
    val store = LocalStore.current
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(order.id) {
        while (true) {
            elapsed = ((nowMs() - order.createdAt) / 1000).toInt()
            delay(1000)
        }
    }

    val contacted = Dispatch.ranked(order, store.drivers, store.rules.minEarningsBalance)
        .filter { order.offeredTo.contains(it.id) || order.declinedBy.contains(it.id) }
    val isOpen = order.dispatchMode == DispatchMode.open || order.promotedToOpen

    Column(
        modifier.fillMaxWidth()
            .glassSurface(22.dp, MB.green.copy(alpha = 0.12f), 0.10f)
            .padding(15.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // الرأس
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Radar()
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (isOpen) "طلبك في اللوحة المفتوحة" else "نبحث عن مندوب في ${order.pickup.zone}",
                    style = MBType.headline, color = MB.ink)
                Text("${order.offeredTo.size} وصلهم الطلب · $elapsed ثانية", style = MBType.micro, color = MB.ink2)
            }
        }

        if (isOpen) {
            Row(
                Modifier.fillMaxWidth().glassSurface(14.dp, MB.gold.copy(alpha = 0.14f), 0.05f).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                SFIcon("list.bullet.rectangle.portrait.fill", size = 16.dp, tint = MB.gold)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (order.promotedToOpen) "لم نجد مندوباً قريباً — نشرنا طلبك للجميع"
                         else "طلبك معروض على كل المندوبين", style = MBType.callout, color = MB.ink)
                    Text("أول من يقبله يثبت عليه، وتظهر لك بياناته فوراً", style = MBType.micro, color = MB.ink2)
                }
            }
        } else if (contacted.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().glassSurface(14.dp, null, 0.05f).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                SFIcon("exclamationmark.circle.fill", size = 16.dp, tint = MB.gold)
                Text("لا يوجد مندوب متصل في ${order.pickup.zone} الآن — نوسّع البحث للولايات المجاورة",
                    style = MBType.caption, color = MB.ink2, modifier = Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                contacted.forEach { c -> CandidateRow(c, Dispatch.state(c.id, order)) }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SFIcon("info.circle", size = 10.dp, tint = MB.ink3, modifier = Modifier.padding(top = 2.dp))
            Text("أول من يقبل يثبت عليه الطلب. من يرفض يُستبدل بالأقرب بعده تلقائياً.",
                style = MBType.micro, color = MB.ink3, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Radar() {
    val tr = rememberInfiniteTransition(label = "radar")
    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        for (i in 0 until 2) {
            val t by tr.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(1800, easing = LinearOutSlowInEasing), RepeatMode.Restart, StartOffset((i * 900))),
                label = "ring$i")
            val d = (14f + 38f * t).dp
            Box(Modifier.size(d).border(2.dp, MB.green.copy(alpha = 0.55f * (1 - t)), CircleShape))
        }
        Box(Modifier.size(16.dp).background(MB.greenGradient, CircleShape))
    }
}

@Composable
private fun CandidateRow(c: Dispatch.Candidate, state: Dispatch.OfferState) {
    val tint = when (state) {
        Dispatch.OfferState.declined -> MB.red
        Dispatch.OfferState.sent -> MB.green
        else -> MB.gold
    }
    Row(
        Modifier.fillMaxWidth().glassSurface(14.dp, null, 0.04f).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Avatar(c.driver.name, size = 34.dp, tint = tint)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(c.driver.name, style = MBType.callout, color = MB.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${"%.1f".format(Locale.US, c.km)} كم", style = MBType.num(10, FontWeight.SemiBold), color = MB.ink2)
                if (c.sameZone) Text("· نفس الولاية", style = MBType.micro, color = MB.green)
            }
        }
        Row(
            Modifier.background(tint.copy(alpha = 0.14f), CircleShape).padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (state == Dispatch.OfferState.sent) {
                CircularProgressIndicator(Modifier.size(10.dp), color = tint, strokeWidth = 1.5.dp)
            } else {
                SFIcon(if (state == Dispatch.OfferState.declined) "xmark" else "clock", size = 9.dp, tint = tint)
            }
            Text(state.title, style = MBType.micro, color = tint)
        }
    }
}
