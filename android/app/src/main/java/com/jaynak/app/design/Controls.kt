package com.jaynak.app.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.jaynak.app.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** مقابل .buttonStyle(.glass) — كبسولة زجاجية بنص ملوّن. */
@Composable
fun GlassButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    symbol: String? = null,
    enabled: Boolean = true,
    horizontal: Dp = 16.dp,
    vertical: Dp = 10.dp,
) {
    val c = if (tint == Color.Unspecified) MB.ink else tint
    Row(
        modifier.pressable(enabled = enabled, onClick = onClick).glassChip()
            .padding(horizontal = horizontal, vertical = vertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (symbol != null) SFIcon(symbol, size = 14.dp, tint = c)
        Text(title, style = MBType.callout, color = c.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

/** مقابل .buttonStyle(.glassProminent) — كبسولة ملوّنة. */
@Composable
fun ProminentButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MB.green,
    symbol: String? = null,
    enabled: Boolean = true,
    horizontal: Dp = 16.dp,
    vertical: Dp = 10.dp,
) {
    Row(
        modifier.pressable(enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .background(tint.copy(alpha = if (enabled) 1f else 0.35f))
            .padding(horizontal = horizontal, vertical = vertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (symbol != null) SFIcon(symbol, size = 14.dp, tint = Color.White)
        Text(title, style = MBType.callout, color = Color.White)
    }
}

/** زر دائري بأيقونة — .glass أو .glassProminent. */
@Composable
fun GlassIconButton(
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    iconSize: Dp = 15.dp,
    tint: Color = MB.green,
    prominent: Boolean = false,
    enabled: Boolean = true,
) {
    val base = modifier.size(size).pressable(enabled = enabled, onClick = onClick)
    Box(
        if (prominent) base.clip(CircleShape).background(tint.copy(alpha = if (enabled) 1f else 0.35f))
        else base.glassShape(CircleShape, 18.dp, null, 0.10f),
        contentAlignment = Alignment.Center,
    ) { SFIcon(symbol, size = iconSize, tint = if (prominent) Color.White else tint) }
}

/** زر «الاتصال بنا»: سماعة + نص داخل كبسولة زجاجية حمراء. */
@Composable
fun SupportPill(onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MB.red) {
    androidx.compose.foundation.layout.Row(
        modifier.height(42.dp).pressable(onClick = onClick)
            .glassShape(androidx.compose.foundation.shape.RoundedCornerShape(50), 18.dp, null, 0.10f)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        SFIcon("headphones", size = 17.dp, tint = tint)
        com.jaynak.app.i18n.Text("الاتصال بنا", color = tint, fontSize = 14.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
}

/** حقل نص متعدد الأسطر داخل سطح زجاجي — مقابل TextField(axis: .vertical). */
@Composable
fun MBTextArea(
    text: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 3,
    maxLines: Int = 6,
    radius: Dp = 16.dp,
    shadow: Float = 0.06f,
    padding: Dp = 14.dp,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    Box(modifier.fillMaxWidth().glassSurface(radius, null, shadow).padding(padding)) {
        if (text.isEmpty()) Text(placeholder, style = MBType.body, color = MB.ink3)
        BasicTextField(
            value = text, onValueChange = onChange,
            minLines = minLines, maxLines = maxLines,
            textStyle = MBType.body.copy(color = MB.ink),
            cursorBrush = SolidColor(MB.green),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** مقابل Toggle. */
@Composable
fun MBToggle(checked: Boolean, onChange: (Boolean) -> Unit, tint: Color = MB.green, enabled: Boolean = true) {
    Switch(
        checked = checked, onCheckedChange = onChange, enabled = enabled,
        colors = SwitchDefaults.colors(checkedTrackColor = tint, checkedThumbColor = Color.White,
            uncheckedTrackColor = MB.ink.copy(alpha = 0.18f), uncheckedThumbColor = Color.White,
            uncheckedBorderColor = Color.Transparent, checkedBorderColor = Color.Transparent),
    )
}

/** مقابل Picker(.segmented). */
@Composable
fun SegmentedPicker(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MB.green,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MB.ink.copy(alpha = 0.07f)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, t ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (on) MB.raised else Color.Transparent)
                    .clickable(remember { MutableInteractionSource() }, null) { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(t, style = MBType.ar(13, if (on) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (on) MB.ink else MB.ink2, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

/** مقابل confirmationDialog / alert. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String? = null,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    cancelText: String = "إلغاء",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MB.raised,
        title = { Text(title, style = MBType.headline, color = MB.ink) },
        text = message?.let { { Text(it, style = MBType.body, color = MB.ink2) } },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmText, style = MBType.callout, color = if (destructive) MB.red else MB.green)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelText, style = MBType.callout, color = MB.ink2) }
        },
    )
}

/** رسالة بزر واحد — مقابل alert(…){ Button("حسناً") }. */
@Composable
fun InfoDialog(title: String, message: String? = null, okText: String = "حسناً", onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MB.raised,
        title = { Text(title, style = MBType.headline, color = MB.ink) },
        text = message?.let { { Text(it, style = MBType.body, color = MB.ink2) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(okText, style = MBType.callout, color = MB.green) } },
    )
}

/** مقابل شريط السحب في أعلى الورقة. */
@Composable
fun SheetGrabber() {
    Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(38.dp).height(5.dp).background(MB.ink.copy(alpha = 0.2f), CircleShape))
    }
}

/** مقابل confirmationDialog بعدة أزرار (كلها إجراءات، مع «تراجع»). */
data class DialogAction(val title: String, val destructive: Boolean = false, val onClick: () -> Unit)

@Composable
fun ActionDialog(
    title: String,
    message: String? = null,
    actions: List<DialogAction>,
    onDismiss: () -> Unit,
    cancelText: String = "تراجع",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MB.raised,
        title = { Text(title, style = MBType.headline, color = MB.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (message != null) Text(message, style = MBType.body, color = MB.ink2)
                actions.forEach { a ->
                    TextButton(onClick = { a.onClick(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text(a.title, style = MBType.callout, color = if (a.destructive) MB.red else MB.green,
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelText, style = MBType.callout, color = MB.ink2) } },
    )
}
