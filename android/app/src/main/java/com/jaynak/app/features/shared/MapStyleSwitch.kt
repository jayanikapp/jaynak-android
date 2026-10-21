package com.jaynak.app.features.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.design.glassChip

/** أزرار تبديل شكل الخريطة (واضحة / شوارع / قمر صناعي). */
@Composable
fun MapStyleSwitch(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        MapStyle.values().forEach { s ->
            val on = MapTiles.style == s
            Text(
                s.title,
                style = MBType.ar(12, FontWeight.SemiBold),
                color = if (on) MB.green else MB.ink,
                modifier = Modifier.padding(top = 6.dp).glassChip()
                    .clickableNoRipple { MapTiles.choose(s) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}
