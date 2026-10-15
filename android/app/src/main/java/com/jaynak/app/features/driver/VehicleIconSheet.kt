package com.jaynak.app.features.driver

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.LiveTracking
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.design.glassSurface
import com.jaynak.app.features.shared.CarArt
import com.jaynak.app.features.shared.CarIconPreview

/** يختار المندوب شكل مركبته التي يراها العميل والإدارة على خريطة التتبّع. */
@Composable
fun VehicleIconSheet(onDismiss: () -> Unit) {
    val current = LiveTracking.myIcon
    JSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            BrandBackground(intensity = 0.5f)
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("تم", style = MBType.callout, color = MB.green, modifier = Modifier.clickableNoRipple(onDismiss))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("شكل مركبتي على الخريطة", style = MBType.headline, color = MB.ink)
                    }
                    Spacer(Modifier.padding(horizontal = 14.dp))
                }
                Text("اختر الشكل الذي يراه العميل والإدارة أثناء تتبّعك", style = MBType.caption, color = MB.ink2,
                    textAlign = TextAlign.Center)
                (0..4).chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { i ->
                            val on = i == current
                            Column(
                                Modifier.weight(1f)
                                    .glassSurface(20.dp, if (on) MB.green.copy(alpha = 0.16f) else null, 0.08f)
                                    .border(if (on) 2.dp else 0.dp, if (on) MB.green else androidx.compose.ui.graphics.Color.Transparent,
                                        RoundedCornerShape(20.dp))
                                    .clickableNoRipple { LiveTracking.chooseIcon(i) }
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                CarIconPreview(i, MB.red, Modifier.fillMaxWidth().height(110.dp))
                                Text(CarArt.names[i], style = MBType.callout.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (on) MB.green else MB.ink)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}
