package com.jaynak.app.features.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AccountDeletionRequest
import com.jaynak.app.core.Fmt
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBTextArea
import com.jaynak.app.design.MBType
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.clickableNoRipple

/** مقابل AccountDeletionRequestSheet.swift. */
@Composable
fun AccountDeletionRequestSheet(onDismiss: () -> Unit) {
    val store = LocalStore.current
    var reason by remember { mutableStateOf("") }

    JSheet(onDismiss = onDismiss, fullHeight = false) {
        Box(Modifier.fillMaxWidth()) {
            BrandBackground(intensity = 0.6f)
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("إغلاق", style = MBType.callout, color = MB.green,
                        modifier = Modifier.clickableNoRipple(onDismiss))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("حذف الحساب", style = MBType.headline, color = MB.ink)
                    }
                    Spacer(Modifier.padding(horizontal = 16.dp))
                }
                val pending = store.myDeletionRequest
                if (pending != null) PendingView(pending) else Column(
                    Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Box(Modifier.padding(top = 20.dp)) { SFIcon("person.fill.xmark", size = 40.dp, tint = MB.red) }
                    Text("سيُراجع فريقنا طلبك قبل حذف حسابك وبياناتك نهائياً — خطوة للتأكّد أن الحذف مقصود ولحماية أي طلبات أو مستحقات مالية قائمة.",
                        style = MBType.body, color = MB.ink2, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp))
                    MBTextArea(reason, { reason = it }, "سبب طلب الحذف", Modifier.padding(horizontal = 20.dp),
                        minLines = 3, maxLines = 6)
                    PrimaryCTA("إرسال طلب الحذف", symbol = "paperplane.fill", tint = MB.red,
                        enabled = reason.trim().isNotEmpty(),
                        modifier = Modifier.padding(horizontal = 20.dp),
                        onClick = { store.requestAccountDeletion(reason); onDismiss() })
                }
            }
        }
    }
}

@Composable
private fun PendingView(request: AccountDeletionRequest) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(Modifier.padding(top = 20.dp)) { SFIcon("hourglass", size = 40.dp, tint = MB.gold) }
        Text("طلب الحذف قيد المراجعة", style = MBType.section, color = MB.ink)
        Text("أرسلته ${Fmt.relative(request.createdAt)} — سنحذف حسابك فور موافقة الإدارة.",
            style = MBType.caption, color = MB.ink2, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp))
    }
}
