package com.jaynak.app.i18n

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text as M3Text

/** نافذة اختيار اللغة (اختياري؛ العربية افتراضية). */
@Composable
fun LanguageDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
        title = { Text("اللغة") },
        text = {
            Column {
                AppLang.values().forEach { l ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { Lang.set(l); onDismiss() }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = Lang.current == l, onClick = { Lang.set(l); onDismiss() })
                        Spacer(Modifier.width(8.dp))
                        M3Text(l.nativeName)
                    }
                }
            }
        },
    )
}

/** زر صغير لتبديل اللغة قبل تسجيل الدخول. */
@Composable
fun LanguageChip(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = modifier) {
        M3Text("🌐 " + Lang.current.nativeName)
    }
    if (open) LanguageDialog { open = false }
}
