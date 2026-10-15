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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.Fmt
import com.jaynak.app.core.Id
import com.jaynak.app.core.SupportTicket
import com.jaynak.app.core.TicketStatus
import com.jaynak.app.core.UserRole
import com.jaynak.app.features.shared.ChatView
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.EmptyStateView
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.GlassIconButton
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.KeyValueRow
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBTextArea
import com.jaynak.app.design.MBType
import com.jaynak.app.design.NavBar
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.StatTile
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.pressable

private val orange = Color(0xFFFF9500)

@Composable
private fun ticketTint(s: TicketStatus) = when (s) {
    TicketStatus.open -> MB.red
    TicketStatus.pending -> orange
    TicketStatus.closed -> MB.green
}

/** مقابل AdminSupport.swift. */
@Composable
fun AdminSupport() {
    val store = LocalStore.current
    var filter by remember { mutableStateOf<TicketStatus?>(null) }
    var selected by remember { mutableStateOf<SupportTicket?>(null) }
    var chat by remember { mutableStateOf<Pair<String, String>?>(null) }

    val base = if (filter == null) store.tickets else store.tickets.filter { it.status == filter }
    val list = base.sortedWith(Comparator { a, b ->
        if ((a.status == TicketStatus.open) != (b.status == TicketStatus.open))
            return@Comparator if (a.status == TicketStatus.open) -1 else 1
        b.createdAt.compareTo(a.createdAt)
    })

    Box(Modifier.fillMaxSize()) {
        BrandBackground()
        Column(Modifier.fillMaxSize()) {
            NavBar(title = "الدعم")
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("مفتوحة", "${store.tickets.count { it.status == TicketStatus.open }}",
                        "envelope.badge.fill", Modifier.weight(1f), tint = MB.red)
                    StatTile("قيد المعالجة", "${store.tickets.count { it.status == TicketStatus.pending }}",
                        "clock.fill", Modifier.weight(1f), tint = orange)
                    StatTile("مغلقة", "${store.tickets.count { it.status == TicketStatus.closed }}",
                        "checkmark.circle.fill", Modifier.weight(1f))
                }

                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdminChip("الكل", filter == null) { filter = null }
                    TicketStatus.values().forEach { s -> AdminChip(s.title, filter == s) { filter = s } }
                }

                val threads = store.chats.filter { it.threadID.startsWith("support-") }
                    .groupBy { it.threadID }.values
                    .map { it.sortedBy { m -> m.date } }.sortedByDescending { it.last().date }
                if (threads.isNotEmpty()) {
                    Text("محادثات الدعم", style = MBType.headline, color = MB.ink, modifier = Modifier.fillMaxWidth())
                    threads.forEach { th ->
                        val last = th.last()
                        val who = th.lastOrNull { !it.isMine }?.senderName ?: "مستخدم"
                        Row(Modifier.fillMaxWidth().glassSurface(20.dp, null, 0.08f)
                            .pressable { chat = last.threadID to who }.padding(13.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).background(MB.red.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                                SFIcon("bubble.left.and.text.bubble.right.fill", size = 14.dp, tint = MB.red)
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(who, style = MBType.callout, color = MB.ink, maxLines = 1)
                                Text(last.body, style = MBType.micro, color = MB.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            SFIcon("chevron.forward", size = 11.dp, tint = MB.ink3)
                        }
                    }
                }

                if (list.isEmpty()) EmptyStateView("lifepreserver.fill", "لا تذاكر", "كل شيء هادئ الآن")

                list.forEach { t -> TicketRow(t) { selected = t } }
                Spacer(Modifier.height(150.dp))
            }
        }
    }

    selected?.let { TicketDetailSheet(it.id) { selected = null } }
    chat?.let { ChatView(it.first, it.second, "", UserRole.admin, moderatable = false, onDismiss = { chat = null }) }
}

@Composable
private fun TicketRow(ticket: SupportTicket, onClick: () -> Unit) {
    val tint = ticketTint(ticket.status)
    Row(Modifier.fillMaxWidth().glassSurface(20.dp, null, 0.08f).pressable(onClick = onClick).padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(tint.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
            SFIcon(if (ticket.role == UserRole.driver) "car.fill" else "person.fill", size = 14.dp, tint = tint)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(ticket.ref, style = MBType.num(12, FontWeight.Bold), color = MB.ink)
                MBChip(ticket.status.title, tint = tint, filled = ticket.status == TicketStatus.open)
            }
            Text(ticket.subject, style = MBType.callout, color = MB.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${ticket.openerName} · ${Fmt.relative(ticket.createdAt)}", style = MBType.micro, color = MB.ink3)
        }
        SFIcon("chevron.forward", size = 11.dp, tint = MB.ink3)
    }
}

/** مقابل TicketDetailSheet. */
@Composable
fun TicketDetailSheet(ticketID: Id, onDismiss: () -> Unit) {
    val store = LocalStore.current
    var reply by remember { mutableStateOf("") }
    val t = store.tickets.firstOrNull { it.id == ticketID }

    JSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxSize()) {
            BrandBackground(intensity = 0.5f)
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("إغلاق", style = MBType.callout, color = MB.green, modifier = Modifier.clickableNoRipple(onDismiss))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("تذكرة دعم", style = MBType.headline, color = MB.ink)
                    }
                    Spacer(Modifier.padding(horizontal = 20.dp))
                }
                if (t != null) {
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(t.ref, style = MBType.num(14, FontWeight.Bold), color = MB.ink)
                                    Spacer(Modifier.weight(1f))
                                    MBChip(t.status.title, tint = if (t.status == TicketStatus.open) MB.red else MB.green, filled = true)
                                }
                                Text(t.subject, style = MBType.headline, color = MB.ink)
                                KeyValueRow("المُبلِّغ", t.openerName, isNumber = false)
                                KeyValueRow("النوع", t.role.title, isNumber = false)
                                t.orderRef?.let { KeyValueRow("الطلب", it) }
                                KeyValueRow("التاريخ", Fmt.dayMonthTime(t.createdAt), isNumber = false)
                            }
                        }

                        t.messages.forEach { m ->
                            Row(Modifier.fillMaxWidth()) {
                                if (!m.isMine) Spacer(Modifier.weight(0.12f))
                                Column(
                                    Modifier.weight(0.88f)
                                        .glassSurface(16.dp, if (m.isMine) null else MB.green.copy(alpha = 0.16f), 0.06f)
                                        .padding(13.dp),
                                    horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(m.senderName, style = MBType.micro, color = MB.ink3)
                                    Text(m.body, style = MBType.body, color = MB.ink)
                                }
                                if (m.isMine) Spacer(Modifier.weight(0.12f))
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }

                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            MBTextArea(reply, { reply = it }, "ردّ الدعم…", Modifier.weight(1f),
                                minLines = 1, maxLines = 3, radius = 20.dp, padding = 12.dp)
                            GlassIconButton("arrow.up", {
                                store.replyTicket(t, reply, true)
                                reply = ""
                            }, size = 44.dp, iconSize = 16.dp, tint = MB.green, prominent = true,
                                enabled = reply.trim().isNotEmpty())
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TicketStatus.values().forEach { s ->
                                AdminChip(s.title, t.status == s, Modifier.weight(1f), vertical = 9.dp) {
                                    store.setTicketStatus(s, t)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
