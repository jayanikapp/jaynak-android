package com.jaynak.app.features.shared

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.ChatMessage
import com.jaynak.app.core.Fmt
import com.jaynak.app.core.UserRole
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.ConfirmDialog
import com.jaynak.app.design.EmptyStateView
import com.jaynak.app.design.GlassIconButton
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.sfVector

/** مقابل ChatView.swift (ورقة بمحادثة). */
@Composable
fun ChatView(
    threadID: String,
    title: String,
    subtitle: String,
    role: UserRole,
    moderatable: Boolean = true,
    onDismiss: () -> Unit,
) {
    val store = LocalStore.current
    var draft by remember { mutableStateOf("") }
    var blocked by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val messages = store.thread(threadID)
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    JSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxSize()) {
            BrandBackground(intensity = 0.5f)
            Column(Modifier.fillMaxSize()) {
                // الشريط العلوي: «إغلاق» (leading) — العنوان — قائمة (trailing)
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("إغلاق", style = MBType.callout, color = MB.green,
                        modifier = Modifier.combinedClickableNoRipple(onDismiss))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(title, style = MBType.headline, color = MB.ink)
                    }
                    if (moderatable) {
                        Box {
                            GlassIconButton("ellipsis.circle", { menu = true }, size = 36.dp, tint = MB.green)
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("إبلاغ عن المحادثة", style = MBType.body) },
                                    leadingIcon = { SFIcon("flag.fill", size = 16.dp) },
                                    onClick = { menu = false; messages.lastOrNull()?.let { store.reportMessage(it) } })
                                DropdownMenuItem(
                                    text = { Text(if (blocked) "إلغاء الحجب" else "حجب المستخدم",
                                        style = MBType.body, color = if (blocked) MB.ink else MB.red) },
                                    leadingIcon = {
                                        SFIcon(if (blocked) "hand.raised.slash" else "hand.raised.fill", size = 16.dp,
                                            tint = if (blocked) MB.ink else MB.red)
                                    },
                                    onClick = { menu = false; if (blocked) blocked = false else showBlockConfirm = true })
                            }
                        }
                    } else Spacer(Modifier.size(36.dp))
                }

                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (messages.isEmpty()) item {
                        EmptyStateView("bubble.left.and.bubble.right", "ابدأ المحادثة",
                            if (moderatable) "نسّق تفاصيل الاستلام والتسليم هنا"
                            else "اكتب مشكلتك وسيردّ عليك فريق الدعم")
                    }
                    items(messages, key = { it.id }) { m ->
                        Bubble(m, moderatable) { store.reportMessage(m) }
                    }
                    if (blocked) item {
                        Text("حجبت هذا المستخدم — لن تصلك رسائل جديدة منه",
                            style = MBType.micro, color = MB.red, modifier = Modifier.padding(top = 6.dp))
                    }
                }

                // المُحرّر
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.weight(1f).glassSurface(22.dp, null, 0.08f)
                        .padding(horizontal = 14.dp, vertical = 12.dp)) {
                        if (draft.isEmpty()) Text("اكتب رسالة…", style = MBType.body, color = MB.ink3)
                        BasicTextField(
                            value = draft, onValueChange = { draft = it },
                            maxLines = 4, textStyle = MBType.body.copy(color = MB.ink),
                            cursorBrush = SolidColor(MB.green), modifier = Modifier.fillMaxWidth())
                    }
                    GlassIconButton(
                        "arrow.up", {
                            store.send(draft, threadID, role)
                            draft = ""
                        },
                        size = 46.dp, iconSize = 17.dp, prominent = true,
                        enabled = draft.trim().isNotEmpty() && !blocked)
                }
            }
        }
    }

    if (showBlockConfirm) {
        ConfirmDialog(
            title = "حجب هذا المستخدم؟",
            message = "لن تصلك رسائل منه، وسيُراجع الفريق المحادثة.",
            confirmText = "حجب", destructive = true,
            onConfirm = {
                blocked = true
                store.openTicket("حجب مستخدم من محادثة", "حُجب الطرف الآخر في المحادثة $threadID")
            },
            onDismiss = { showBlockConfirm = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableNoRipple(onClick: () -> Unit): Modifier = composed {
    this.combinedClickable(
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null, onClick = onClick)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(message: ChatMessage, moderatable: Boolean, onReport: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth()) {
        if (message.isMine) Spacer(Modifier.weight(1f).widthIn(min = 40.dp))
        Box {
            Column(
                horizontalAlignment = if (message.isMine) Alignment.Start else Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!message.isMine) Text(message.senderName, style = MBType.micro, color = MB.ink3)
                Text(
                    message.body, style = MBType.body,
                    color = if (message.isMine) Color.White else MB.ink,
                    modifier = Modifier
                        .combinedClickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null, onClick = {},
                            onLongClick = { if (moderatable && !message.isMine && !message.isReported) menu = true })
                        .glassSurface(18.dp, null, 0.08f)
                        .let {
                            if (message.isMine) it.background(
                                Brush.verticalGradient(listOf(MB.green, MB.green.copy(alpha = 0.82f))),
                                RoundedCornerShape(18.dp))
                            else it
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.timeShort(message.date), style = MBType.micro, color = MB.ink3)
                    if (message.isReported) {
                        SFIcon("flag.fill", size = 10.dp, tint = MB.red)
                        Text("مُبلَّغ عنها", style = MBType.micro, color = MB.red)
                    }
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("إبلاغ عن هذه الرسالة", style = MBType.body, color = MB.red) },
                    leadingIcon = { SFIcon("flag.fill", size = 16.dp, tint = MB.red) },
                    onClick = { menu = false; onReport() })
            }
        }
        if (!message.isMine) Spacer(Modifier.weight(1f).widthIn(min = 40.dp))
    }
}
