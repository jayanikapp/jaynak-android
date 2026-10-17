package com.jaynak.app.features.shared

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.jaynak.app.core.OrdersService
import com.jaynak.app.core.Oman
import com.jaynak.app.core.UserRole
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBField
import com.jaynak.app.design.MBTextArea
import com.jaynak.app.design.MBType
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.clickableNoRipple
import kotlinx.coroutines.launch
import org.json.JSONObject

private val LABELS = mapOf("name" to "الاسم", "zone" to "المنطقة", "civilID" to "الرقم المدني",
    "make" to "نوع السيارة", "model" to "الموديل", "year" to "سنة الصنع", "color" to "اللون",
    "plateNumber" to "رقم اللوحة", "plateLetters" to "حروف اللوحة")

/** طلب تعديل البيانات: يعدّل المستخدم ويكتب ملاحظة، فيصل الطلب للإدارة فوراً لتوافق أو ترفض. */
@Composable
fun ProfileEditSheet(role: UserRole, onDismiss: () -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val isDriver = role == UserRole.driver
    val d = store.currentDriver
    val c = store.currentCustomer
    val current: Map<String, String> = if (isDriver && d != null) mapOf(
        "name" to d.name, "zone" to d.zone, "civilID" to d.docs.civilID, "make" to d.vehicle.make,
        "model" to d.vehicle.model, "year" to d.vehicle.year, "color" to d.vehicle.color,
        "plateNumber" to d.vehicle.plateNumber, "plateLetters" to d.vehicle.plateLetters)
    else mapOf("name" to (c?.name ?: ""))

    val f = remember { mutableStateOf(current) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var last by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) {
        last = runCatching { OrdersService.send("api/account/profile-request", "GET", null).optJSONObject("request") }.getOrNull()
    }
    fun set(k: String, v: String) { f.value = f.value + (k to v) }
    val changes = f.value.filter { (k, v) -> (current[k] ?: "") != v.trim() }
    val pending = last?.optString("status") == "pending"

    JSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            BrandBackground(intensity = 0.6f)
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("إغلاق", style = MBType.callout, color = MB.green, modifier = Modifier.clickableNoRipple(onDismiss))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("تعديل بياناتي", style = MBType.headline, color = MB.ink)
                    }
                    Spacer(Modifier.padding(horizontal = 16.dp))
                }
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.End) {
                    last?.optString("status")?.takeIf { it.isNotEmpty() }?.let { st ->
                        val reply = last?.optString("reply").orEmpty()
                        val (t, col) = when (st) {
                            "pending" -> "طلبك السابق بانتظار موافقة الإدارة" to MB.gold
                            "approved" -> ("وافقت الإدارة على آخر تعديل" + if (reply.isNotEmpty()) " — $reply" else "") to MB.green
                            else -> ("رُفض آخر طلب" + if (reply.isNotEmpty()) " — $reply" else "") to MB.red
                        }
                        Text(t, style = MBType.callout, color = col, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    Text("عدّل ما يلزم ثم اكتب ملاحظة بما غيّرته ليراجعه فريق الإدارة ويوافق عليه. تظهر التعديلات في حسابك بعد الموافقة.",
                        style = MBType.caption, color = MB.ink2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    MBField("الاسم", f.value["name"] ?: "", { set("name", it) }, Modifier.fillMaxWidth(), placeholder = "الاسم الكامل")
                    if (isDriver) {
                        Text("منطقة العمل", style = MBType.caption, color = MB.ink2)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Oman.governorates.flatMap { it.wilayat }.forEach { w ->
                                val sel = f.value["zone"] == w
                                Box(Modifier.clickableNoRipple { set("zone", w) }) { MBChip(w, tint = if (sel) MB.green else MB.ink2, filled = sel) }
                            }
                        }
                        MBField("الرقم المدني", f.value["civilID"] ?: "", { set("civilID", it.filter { ch -> ch.isDigit() }.take(20)) },
                            Modifier.fillMaxWidth(), placeholder = "٨ أرقام", keyboard = KeyboardType.Number, isRTLText = false)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MBField("الموديل", f.value["model"] ?: "", { set("model", it) }, Modifier.weight(1f))
                            MBField("نوع السيارة", f.value["make"] ?: "", { set("make", it) }, Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MBField("اللون", f.value["color"] ?: "", { set("color", it) }, Modifier.weight(1f))
                            MBField("سنة الصنع", f.value["year"] ?: "", { set("year", it.filter { ch -> ch.isDigit() }.take(4)) },
                                Modifier.weight(1f), keyboard = KeyboardType.Number, isRTLText = false)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MBField("حروف اللوحة", f.value["plateLetters"] ?: "", { set("plateLetters", it.filter { ch -> ch.isLetter() || ch == ' ' }.take(3)) },
                                Modifier.weight(1f), isRTLText = false)
                            MBField("رقم اللوحة", f.value["plateNumber"] ?: "", { set("plateNumber", it.filter { ch -> ch.isDigit() }.take(5)) },
                                Modifier.weight(1f), keyboard = KeyboardType.Number, isRTLText = false)
                        }
                    }
                    Text("ملاحظة للإدارة (مطلوبة): ما الذي عدّلته ولماذا؟", style = MBType.caption, color = MB.ink2)
                    MBTextArea(note, { note = it }, "مثال: كتبت اسمي بالخطأ وصححته", Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
                    if (changes.isNotEmpty())
                        Text("سيُرسل تعديل: " + changes.keys.mapNotNull { LABELS[it] }.joinToString("، "), style = MBType.micro, color = MB.green)
                    error?.let { Text(it, style = MBType.caption, color = MB.red) }
                    PrimaryCTA(if (pending) "تحديث الطلب المعلّق" else "إرسال للإدارة", symbol = "paperplane.fill",
                        enabled = changes.isNotEmpty() && note.trim().isNotEmpty() && !busy, loading = busy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            busy = true; error = null
                            scope.launch {
                                runCatching {
                                    val ch = JSONObject(); f.value.forEach { (k, v) -> ch.put(k, v.trim()) }
                                    OrdersService.send("api/account/profile-request", "POST",
                                        JSONObject().put("role", if (isDriver) "driver" else "customer")
                                            .put("changes", ch).put("note", note.trim()))
                                }.onSuccess {
                                    store.toast = com.jaynak.app.core.AppStore.Toast(text = "وصل طلبك للإدارة — بانتظار الموافقة",
                                        kind = com.jaynak.app.core.AppStore.Toast.Kind.success)
                                    busy = false; onDismiss()
                                }.onFailure { busy = false; error = it.message ?: "تعذّر الإرسال" }
                            }
                        })
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
