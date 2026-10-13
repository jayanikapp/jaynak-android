package com.jaynak.app.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AccountDeletionRequest
import com.jaynak.app.core.DeletionRequestStatus
import com.jaynak.app.core.Fmt
import com.jaynak.app.core.Id
import com.jaynak.app.core.UserRole
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.EmptyStateView
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.KeyValueRow
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBType
import com.jaynak.app.design.NavBar
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SecondaryCTA
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.StatTile
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.pressable

/** مقابل AdminDeletionRequests.swift. */
@Composable
fun AdminDeletionRequests() {
    val store = LocalStore.current
    var filter by remember { mutableStateOf<DeletionRequestStatus?>(DeletionRequestStatus.pending) }
    var selected by remember { mutableStateOf<Id?>(null) }

    val list = (if (filter == null) store.deletionRequests else store.deletionRequests.filter { it.status == filter })
        .sortedByDescending { it.createdAt }

    Box(Modifier.fillMaxSize()) {
        BrandBackground()
        Column(Modifier.fillMaxSize()) {
            NavBar(title = "طلبات حذف الحسابات")
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("قيد المراجعة", "${store.deletionRequests.count { it.status == DeletionRequestStatus.pending }}",
                        "hourglass", Modifier.weight(1f), tint = MB.red)
                    StatTile("موافَق عليه", "${store.deletionRequests.count { it.status == DeletionRequestStatus.approved }}",
                        "checkmark.circle.fill", Modifier.weight(1f))
                    StatTile("مرفوض", "${store.deletionRequests.count { it.status == DeletionRequestStatus.rejected }}",
                        "xmark.circle.fill", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdminChip("الكل", filter == null) { filter = null }
                    DeletionRequestStatus.values().forEach { s -> AdminChip(s.title, filter == s) { filter = s } }
                }

                if (list.isEmpty()) EmptyStateView("person.fill.xmark", "لا طلبات", "لا طلبات حذف حسابات بهذه الحالة")

                list.forEach { r -> DeletionRequestRow(r) { selected = r.id } }
                Spacer(Modifier.height(150.dp))
            }
        }
    }

    selected?.let { DeletionRequestDetailSheet(it) { selected = null } }
}

@Composable
private fun DeletionRequestRow(request: AccountDeletionRequest, onClick: () -> Unit) {
    val tint = when (request.status) {
        DeletionRequestStatus.pending -> MB.red
        DeletionRequestStatus.approved -> MB.green
        DeletionRequestStatus.rejected -> MB.ink3
    }
    Row(Modifier.fillMaxWidth().glassSurface(20.dp, null, 0.08f).pressable(onClick = onClick).padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(tint.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
            SFIcon(if (request.role == UserRole.driver) "car.fill" else "person.fill", size = 14.dp, tint = tint)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(request.requesterName, style = MBType.callout, color = MB.ink)
                MBChip(request.status.title, tint = tint, filled = request.status == DeletionRequestStatus.pending)
            }
            Text(request.reason, style = MBType.micro, color = MB.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Fmt.relative(request.createdAt), style = MBType.micro, color = MB.ink3)
        }
        SFIcon("chevron.forward", size = 11.dp, tint = MB.ink3)
    }
}

/** مقابل DeletionRequestDetailSheet. */
@Composable
fun DeletionRequestDetailSheet(requestID: Id, onDismiss: () -> Unit) {
    val store = LocalStore.current
    var showReject by remember { mutableStateOf(false) }
    val r = store.deletionRequests.firstOrNull { it.id == requestID }

    JSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxSize()) {
            BrandBackground(intensity = 0.5f)
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("إغلاق", style = MBType.callout, color = MB.green, modifier = Modifier.clickableNoRipple(onDismiss))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("طلب حذف حساب", style = MBType.headline, color = MB.ink)
                    }
                    Spacer(Modifier.padding(horizontal = 20.dp))
                }
                if (r != null) {
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(r.requesterName, style = MBType.headline, color = MB.ink)
                                    Spacer(Modifier.weight(1f))
                                    MBChip(r.status.title, tint = if (r.status == DeletionRequestStatus.pending) MB.red else MB.green, filled = true)
                                }
                                KeyValueRow("الدور", r.role.title, isNumber = false)
                                KeyValueRow("الرقم", r.phone)
                                KeyValueRow("تاريخ الطلب", Fmt.dayMonthTime(r.createdAt), isNumber = false)
                                r.decidedAt?.let { KeyValueRow("تاريخ القرار", Fmt.dayMonthTime(it), isNumber = false) }
                            }
                        }
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                                SectionHeader("سبب الطلب", symbol = "text.bubble.fill")
                                Text(r.reason, style = MBType.body, color = MB.ink)
                            }
                        }
                        val rejection = r.rejectionReason
                        if (!rejection.isNullOrEmpty()) {
                            GlassCard(Modifier.fillMaxWidth(), tint = MB.red.copy(alpha = 0.1f)) {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                                    SectionHeader("سبب الرفض", symbol = "xmark.shield.fill")
                                    Text(rejection, style = MBType.body, color = MB.ink)
                                }
                            }
                        }
                        if (r.status == DeletionRequestStatus.pending) {
                            Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                PrimaryCTA("موافقة وحذف الحساب", onClick = { store.approveDeletionRequest(r); onDismiss() }, symbol = "checkmark")
                                SecondaryCTA("رفض الطلب", onClick = { showReject = true }, symbol = "xmark", tint = MB.red)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }

    if (showReject) {
        TextPromptDialog("رفض طلب الحذف", "سبب الرفض يظهر لصاحب الحساب عند فتحه التطبيق — لا إشعار يُرسَل.",
            "سبب الرفض", "رفض", destructive = true,
            onConfirm = { reason ->
                r?.let { store.rejectDeletionRequest(it, reason) }
                showReject = false; onDismiss()
            }, onDismiss = { showReject = false })
    }
}
