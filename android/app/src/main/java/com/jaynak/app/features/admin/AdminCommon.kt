package com.jaynak.app.features.admin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.jaynak.app.core.Feedback
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.glassChip
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.pressable

/** حقل البحث الزجاجي (أيقونة + TextField) كما في AdminOrders/AdminUsers. */
@Composable
fun AdminSearchField(text: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(46.dp).glassSurface(16.dp, null, 0.06f).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        SFIcon("magnifyingglass", size = 16.dp, tint = MB.ink3)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text(placeholder, style = MBType.body, color = MB.ink3)
            BasicTextField(text, onChange, singleLine = true,
                textStyle = MBType.body.copy(color = MB.ink, textAlign = TextAlign.Start),
                cursorBrush = SolidColor(MB.green), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** كبسولة اختيار زجاجية (filterChip في iOS). */
@Composable
fun AdminChip(title: String, selected: Boolean, modifier: Modifier = Modifier, tint: Color = MB.green,
              horizontal: androidx.compose.ui.unit.Dp = 14.dp, vertical: androidx.compose.ui.unit.Dp = 9.dp,
              onClick: () -> Unit) {
    Text(
        title, style = MBType.caption, color = if (selected) Color.White else MB.ink,
        textAlign = TextAlign.Center,
        modifier = modifier
            .glassChip()
            .clip(CircleShape)
            .background(if (selected) tint else Color.Transparent, CircleShape)
            .pressable { Feedback.selection(); onClick() }
            .padding(horizontal = horizontal, vertical = vertical),
    )
}

/** مقابل .alert مع TextField في iOS. */
@Composable
fun TextPromptDialog(
    title: String,
    message: String? = null,
    placeholder: String,
    confirmText: String,
    destructive: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MB.raised,
        title = { Text(title, style = MBType.headline, color = MB.ink) },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (message != null) Text(message, style = MBType.caption, color = MB.ink2)
                Box(Modifier.fillMaxWidth().glassSurface(12.dp, null, 0.04f).padding(horizontal = 12.dp, vertical = 11.dp)) {
                    if (text.isEmpty()) Text(placeholder, style = MBType.body, color = MB.ink3)
                    BasicTextField(text, { text = it }, textStyle = MBType.body.copy(color = MB.ink),
                        cursorBrush = SolidColor(MB.green), modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton({ onConfirm(text) }) {
                Text(confirmText, color = if (destructive) MB.red else MB.green)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onDismiss) { Text("إلغاء", color = MB.ink2) }
        },
    )
}
