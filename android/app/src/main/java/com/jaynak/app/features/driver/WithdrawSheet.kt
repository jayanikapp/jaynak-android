package com.jaynak.app.features.driver

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.OrdersService
import com.jaynak.app.core.AppStore.Toast
import com.jaynak.app.core.money
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBField
import com.jaynak.app.design.MBType
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SheetGrabber
import kotlinx.coroutines.launch
import org.json.JSONObject

private val WD_DAYS = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")

/** سحب أرباح المندوب: يطلب المبلغ ويحوّله الأدمن في أقرب يوم سحب. */
@Composable
fun WithdrawSheet(onDismiss: () -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var holder by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(tick) {
        try {
            val j = OrdersService.withdrawals()
            data = j
            j.optJSONObject("bank")?.let { b ->
                if (bankName.isEmpty()) bankName = b.optString("bankName")
                if (holder.isEmpty()) holder = b.optString("holder")
                if (account.isEmpty()) account = b.optString("account")
            }
        } catch (e: Exception) { error = e.message }
    }

    JSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxWidth()) {
            BrandBackground(intensity = 0.6f)
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SheetGrabber()
                Text("سحب الأرباح", style = MBType.section, color = MB.ink)
                val d = data
                if (d == null) {
                    Text(error ?: "جارٍ التحميل…", style = MBType.callout, color = MB.ink2)
                } else {
                    val balance = d.optDouble("balance", 0.0)
                    val min = d.optDouble("minAmount", 5.0)
                    val days = d.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: emptyList()
                    val enabled = d.optBoolean("enabled", true)
                    Text(balance.money, style = MBType.num(36, FontWeight.Bold), color = MB.ink)
                    Text("رصيدك المتاح للسحب", style = MBType.caption, color = MB.ink2)
                    Text(
                        "التحويل كل: " + days.mapNotNull { WD_DAYS.getOrNull(it) }.joinToString(" و ") +
                            " · أقرب موعد " + d.optString("nextPayout") + " · الحد الأدنى " + min.money,
                        style = MBType.micro, color = MB.ink2,
                    )
                    if (!enabled) {
                        Text("السحب متوقف مؤقتاً من الإدارة", style = MBType.callout, color = MB.red)
                    } else {
                        MBField("المبلغ (ر.ع)", amount, { amount = it }, keyboard = KeyboardType.Decimal,
                            isRTLText = false, modifier = Modifier.fillMaxWidth())
                        MBField("اسم البنك أو المحفظة", bankName, { bankName = it }, modifier = Modifier.fillMaxWidth())
                        MBField("اسم صاحب الحساب", holder, { holder = it }, modifier = Modifier.fillMaxWidth())
                        MBField("رقم الحساب / المحفظة", account, { account = it }, keyboard = KeyboardType.Number,
                            isRTLText = false, modifier = Modifier.fillMaxWidth())
                        PrimaryCTA("اطلب السحب", symbol = "checkmark", loading = busy, onClick = {
                            val v = amount.replace(',', '.').trim().toDoubleOrNull()
                            if (v == null) {
                                store.toast = Toast(text = "أدخل مبلغاً صحيحاً", kind = Toast.Kind.error)
                            } else {
                                busy = true
                                scope.launch {
                                    try {
                                        OrdersService.withdraw(v, bankName.trim(), holder.trim(), account.trim())
                                        store.toast = Toast(text = "تم إرسال طلب السحب", kind = Toast.Kind.success)
                                        amount = ""
                                        runCatching { store.refreshDriverFeed() }
                                        tick++
                                    } catch (e: Exception) {
                                        store.toast = Toast(text = e.message ?: "تعذّر الإرسال", kind = Toast.Kind.error)
                                    }
                                    busy = false
                                }
                            }
                        })
                    }
                    val list = d.optJSONArray("withdrawals")
                    if (list != null && list.length() > 0) {
                        Text("طلباتك السابقة", style = MBType.section, color = MB.ink)
                        for (i in 0 until list.length()) {
                            val w = list.getJSONObject(i)
                            val st = w.optString("status")
                            GlassCard(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(w.optDouble("amount").money, style = MBType.num(16, FontWeight.SemiBold), color = MB.ink)
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            when (st) { "paid" -> "تم التحويل ✅"; "rejected" -> "مرفوض ❌"; else -> "قيد التحويل ⏳" },
                                            style = MBType.callout,
                                            color = when (st) { "paid" -> MB.green; "rejected" -> MB.red; else -> MB.ink2 },
                                        )
                                        if (st == "rejected" && w.optString("reason").isNotEmpty())
                                            Text(w.optString("reason") + " — أُعيد المبلغ لرصيدك", style = MBType.micro, color = MB.ink2)
                                        if (st == "paid" && w.optString("ref").isNotEmpty())
                                            Text("رقم الحوالة: " + w.optString("ref"), style = MBType.micro, color = MB.ink2)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}
