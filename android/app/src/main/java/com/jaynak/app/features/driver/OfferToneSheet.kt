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
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.Feedback
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.design.glassSurface

/** يختار المندوب نغمة تنبيه الطلبات التي تصله — مع تجربة كل نغمة قبل اختيارها. */
@Composable
fun OfferToneSheet(onDismiss: () -> Unit) {
    val current = Feedback.offerToneId
    JSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            BrandBackground(intensity = 0.5f)
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("تم", style = MBType.callout, color = MB.green, modifier = Modifier.clickableNoRipple(onDismiss))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("نغمة الطلبات", style = MBType.headline, color = MB.ink)
                    }
                    Spacer(Modifier.padding(horizontal = 14.dp))
                }
                Text("اضغط على النغمة لتجربتها واختيارها. تُشغَّل عند وصول طلب جديد وأنت داخل التطبيق.",
                    style = MBType.caption, color = MB.ink2, textAlign = TextAlign.Center)
                Feedback.offerTones.forEach { (id, title) ->
                    val on = id == current
                    Row(
                        Modifier.fillMaxWidth()
                            .glassSurface(18.dp, if (on) MB.green.copy(alpha = 0.16f) else null, 0.08f)
                            .border(if (on) 2.dp else 0.dp, if (on) MB.green else Color.Transparent, RoundedCornerShape(18.dp))
                            .clickableNoRipple { Feedback.chooseOfferTone(id); Feedback.previewOfferTone(id) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SFIcon(if (on) "checkmark.circle.fill" else "circle", size = 20.dp, tint = if (on) MB.green else MB.ink3)
                        Spacer(Modifier.weight(1f))
                        Text(title, style = MBType.callout, color = if (on) MB.green else MB.ink)
                        SFIcon("speaker.wave.2.fill", size = 16.dp, tint = MB.ink2)
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}
