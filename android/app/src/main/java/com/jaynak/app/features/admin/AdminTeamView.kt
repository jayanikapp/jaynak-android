package com.jaynak.app.features.admin

import androidx.compose.foundation.background
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AdminRole
import com.jaynak.app.core.AdminPermission
import com.jaynak.app.core.AdminUser
import com.jaynak.app.core.AppConfig
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.Fmt
import com.jaynak.app.core.digitsOnly
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBField
import com.jaynak.app.design.MBType
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SecondaryCTA
import com.jaynak.app.design.SectionHeader
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.pressable

/** مقابل AdminTeamView.swift. */
@Composable
fun AdminTeamView() {
    val store = LocalStore.current
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!store.can(AdminPermission.manageAdmins)) {
            Row(Modifier.fillMaxWidth().glassSurface(16.dp, null, 0.06f).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SFIcon("lock.fill", size = 15.dp, tint = MB.ink3)
                Text("إدارة الفريق متاحة للمالك فقط", style = MBType.caption, color = MB.ink2)
            }
        } else {
            SectionHeader("فريق الإدارة", subtitle = "${store.admins.size} مسؤول", symbol = "person.2.badge.key.fill")
            store.admins.forEach { AdminRow(it) }
            SecondaryCTA("إضافة مسؤول", onClick = { showAdd = true }, symbol = "person.badge.plus")

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
                    SectionHeader("ماذا يستطيع كل دور؟", symbol = "info.circle")
                    AdminRole.values().forEach { r ->
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.End) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                SFIcon(r.symbol, size = 11.dp, tint = if (r == AdminRole.owner) MB.gold else MB.green)
                                Text(r.title, style = MBType.callout, color = MB.ink)
                                Spacer(Modifier.weight(1f))
                                Text("${r.permissions.size} صلاحية", style = MBType.micro, color = MB.ink3)
                            }
                            Text(r.detail, style = MBType.micro, color = MB.ink2)
                        }
                        if (r != AdminRole.values().last()) HorizontalDivider(Modifier.alpha(0.2f), color = MB.hairline)
                    }
                }
            }
        }
    }

    if (showAdd) AddAdminSheet { showAdd = false }
}

@Composable
private fun AdminRow(a: AdminUser) {
    val store = LocalStore.current
    val isOwner = a.role == AdminRole.owner
    val isMe = store.currentAdmin?.id == a.id
    var menu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().glassSurface(18.dp, null, 0.08f).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).then(
                if (isOwner) Modifier.background(MB.goldGradient, CircleShape)
                else Modifier.background(MB.green.copy(alpha = 0.16f), CircleShape)),
                contentAlignment = Alignment.Center) {
                SFIcon(a.role.symbol, size = 16.dp, tint = if (isOwner) Color.White else MB.green)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(a.name, style = MBType.callout, color = MB.ink)
                    if (isMe) MBChip("أنت", tint = MB.green)
                }
                Text("${AppConfig.countryDialCode} ${a.phone}", style = MBType.num(11, FontWeight.Medium), color = MB.ink3)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MBChip(a.role.title, symbol = a.role.symbol, tint = if (isOwner) MB.gold else MB.green, filled = isOwner)
                    if (!a.isActive) MBChip("موقوف", symbol = "hand.raised.fill", tint = MB.red, filled = true)
                }
            }
            if (!isOwner) {
                Box {
                    Box(Modifier.clickableNoRipple { menu = true }) { SFIcon("ellipsis.circle", size = 20.dp, tint = MB.ink2) }
                    DropdownMenu(menu, { menu = false }) {
                        AdminRole.values().filter { it != AdminRole.owner }.forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r.title, style = MBType.body, color = MB.ink) },
                                leadingIcon = { SFIcon(r.symbol, size = 18.dp, tint = MB.ink) },
                                onClick = { menu = false; store.setAdminRole(r, a.id) },
                            )
                        }
                        HorizontalDivider(color = MB.hairline)
                        DropdownMenuItem(
                            text = { Text(if (a.isActive) "إيقاف الحساب" else "إعادة التفعيل", style = MBType.body,
                                color = if (a.isActive) MB.red else MB.ink) },
                            leadingIcon = { SFIcon(if (a.isActive) "hand.raised.fill" else "checkmark.circle", size = 18.dp,
                                tint = if (a.isActive) MB.red else MB.ink) },
                            onClick = { menu = false; store.setAdminActive(!a.isActive, a.id) },
                        )
                        DropdownMenuItem(
                            text = { Text("حذف", style = MBType.body, color = MB.red) },
                            leadingIcon = { SFIcon("trash.fill", size = 18.dp, tint = MB.red) },
                            onClick = { menu = false; store.removeAdmin(a.id) },
                        )
                    }
                }
            }
        }
        a.createdByName?.let { by ->
            Text("أضافه $by · ${Fmt.dayMonthYear(a.createdAt)}", style = MBType.micro, color = MB.ink3)
        }
    }
}

/** مقابل AddAdminSheet. */
@Composable
fun AddAdminSheet(onDismiss: () -> Unit) {
    val store = LocalStore.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(AdminRole.support) }

    JSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxSize()) {
            BrandBackground(intensity = 0.6f)
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("إلغاء", style = MBType.callout, color = MB.green, modifier = Modifier.clickableNoRipple(onDismiss))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("إضافة مسؤول", style = MBType.headline, color = MB.ink)
                    }
                    Spacer(Modifier.padding(horizontal = 20.dp))
                }
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.End) {
                    MBField("الاسم", name, { name = it }, Modifier.fillMaxWidth(), placeholder = "اسم المسؤول", symbol = "person.fill")
                    MBField("رقم الهاتف", phone, { phone = it }, Modifier.fillMaxWidth(), placeholder = "9XXXXXXX",
                        symbol = "phone.fill", keyboard = KeyboardType.Number, isRTLText = false)

                    Text("الدور", style = MBType.caption, color = MB.ink2)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AdminRole.values().filter { it != AdminRole.owner }.forEach { r ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .glassSurface(16.dp, if (role == r) MB.green.copy(alpha = 0.14f) else null, 0.05f)
                                    .pressable(0.99f) { Feedback.selection(); role = r }.padding(13.dp),
                                horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SFIcon(if (role == r) "largecircle.fill.circle" else "circle", size = 18.dp,
                                    tint = if (role == r) MB.green else MB.ink3)
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(r.title, style = MBType.callout, color = MB.ink)
                                    Text(r.detail, style = MBType.micro, color = MB.ink2, textAlign = TextAlign.End)
                                }
                            }
                        }
                    }

                    Text("يدخل المسؤول برقمه ورمز واتساب — لا كلمة مرور، ولا يرى إلا ما يسمح به دوره.",
                        style = MBType.micro, color = MB.ink3, textAlign = TextAlign.End)

                    PrimaryCTA("إضافة", symbol = "person.badge.plus", modifier = Modifier.fillMaxWidth(),
                        enabled = phone.filter { it.isDigit() }.length == AppConfig.nationalNumberLength,
                        onClick = { if (store.addAdmin(name, phone, role)) onDismiss() })
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}
