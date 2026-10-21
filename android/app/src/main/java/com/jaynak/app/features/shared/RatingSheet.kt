package com.jaynak.app.features.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.Order
import com.jaynak.app.core.UserRole
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBField
import com.jaynak.app.design.MBType
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SheetGrabber
import com.jaynak.app.design.StarRating
import com.jaynak.app.design.SuccessBurst
import com.jaynak.app.design.glassChip

/** مقابل RatingSheet.swift. */
@Composable
fun RatingSheet(order: Order, role: UserRole, counterpartName: String, onDismiss: () -> Unit) {
    val store = LocalStore.current
    var stars by remember { mutableStateOf(0) }
    var comment by remember { mutableStateOf("") }
    val tags = remember { mutableStateListOf<String>() }

    val tagOptions = if (role == UserRole.customer)
        listOf("سريع", "مهذّب", "تعامل ممتاز", "وصل بالوقت", "الطلب سليم")
    else listOf("عنوان واضح", "متعاون", "جاهز بالوقت", "دفع بلا مشاكل")

    JSheet(onDismiss = onDismiss, fullHeight = false) {
        Box(Modifier.fillMaxWidth()) {
            BrandBackground(intensity = 0.7f)
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SheetGrabber()
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuccessBurst(symbol = "checkmark.seal.fill")
                    Text("تم تسليم الطلب ${order.ref}", style = MBType.section, color = MB.ink)
                    Text(if (role == UserRole.customer) "كيف كان $counterpartName؟" else "كيف كان تعامل العميل؟",
                        style = MBType.caption, color = MB.ink2)
                }

                StarRating(stars.toDouble(), size = 34.dp, editable = true,
                    onChange = { stars = it }, modifier = Modifier.padding(vertical = 4.dp))

                if (stars > 0) FlowTags(tagOptions, tags)

                MBField("ملاحظة (اختياري)", comment, { comment = it },
                    placeholder = "شاركنا تفاصيل أكثر", symbol = "text.bubble")

                PrimaryCTA("إرسال التقييم", symbol = "paperplane.fill", enabled = stars > 0, onClick = {
                    val note = (listOf(comment) + tags).filter { it.isNotEmpty() }.joinToString(" · ")
                    store.rate(order, stars, note, role)
                    onDismiss()
                })

                Text("تخطّي", style = MBType.callout, color = MB.ink3,
                    modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) { onDismiss() })
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** مقابل FlowTags: صفوف من وسمين، تُختار وتُلغى. */
@Composable
fun FlowTags(options: List<String>, selected: MutableList<String>) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tag ->
                    val on = selected.contains(tag)
                    Text(
                        tag, style = MBType.caption, color = if (on) Color.White else MB.ink,
                        modifier = Modifier
                            .clickable(remember { MutableInteractionSource() }, null) {
                                Feedback.selection()
                                if (on) selected.remove(tag) else selected.add(tag)
                            }
                            .glassChip()
                            .let { if (on) it.background(MB.green, CircleShape) else it }
                            .padding(horizontal = 13.dp, vertical = 8.dp))
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
