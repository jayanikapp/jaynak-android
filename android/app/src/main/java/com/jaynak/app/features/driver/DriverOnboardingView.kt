package com.jaynak.app.features.driver

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AppConfig
import com.jaynak.app.core.DocumentKind
import com.jaynak.app.core.DocumentUploader
import com.jaynak.app.core.Driver
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.Fmt
import com.jaynak.app.core.Oman
import com.jaynak.app.core.digitsOnly
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.GlassIconButton
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBField
import com.jaynak.app.design.MBType
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SecondaryCTA
import com.jaynak.app.design.glassChip
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.pressable
import com.jaynak.app.features.shared.LegalView
import kotlinx.coroutines.launch

private val steps = listOf("بياناتك", "المركبة", "المستندات", "المراجعة")
private val lastStep = steps.size - 1

/** مقابل DriverOnboardingView.swift — تسجيل المندوب بأربع خطوات. */
@Composable
fun DriverOnboardingView(driver: Driver) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) }
    var draft by remember {
        mutableStateOf(driver.copy(docs = driver.docs.copy(
            civilID = driver.docs.civilID.filter { it.isDigit() }.take(AppConfig.civilIDLength))))
    }
    var showLegal by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    val latestDraft = rememberUpdatedState(draft)
    fun saveProgress() = store.updateDriver(latestDraft.value)
    DisposableEffect(Unit) { onDispose { saveProgress() } }

    val identityProblem: String? =
        if (draft.docs.civilID.digitsOnly.length != AppConfig.civilIDLength) null
        else store.checkIdentity(draft.docs.civilID, draft.phone, draft.id).let { if (it.isAllowed) null else it.message }

    val canContinue = when (step) {
        0 -> draft.name.length >= 3 && draft.docs.civilID.digitsOnly.length == AppConfig.civilIDLength && identityProblem == null
        1 -> draft.vehicle.make.isNotEmpty() && draft.vehicle.model.isNotEmpty() &&
            draft.vehicle.plateNumber.isNotEmpty() && draft.vehicle.plateLetters.isNotEmpty()
        2 -> draft.docs.isComplete
        else -> draft.docs.isComplete && identityProblem == null
    }

    fun submit() {
        if (submitting) return
        submitting = true
        submitError = null
        scope.launch {
            DocumentUploader.uploadPending(draft.docs.pendingUpload, draft.id) { updated ->
                draft = draft.copy(docs = draft.docs.copy(
                    assets = draft.docs.assets.map { if (it.id == updated.id) updated else it }))
            }
            submitting = false
            if (store.submitDriverDocs(draft)) {
                Feedback.success()
            } else {
                submitError = store.authError
                step = 0
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        BrandBackground(intensity = 1.0f)

        Column(Modifier.fillMaxSize()) {
            // الرأس
            Column(Modifier.fillMaxWidth().statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton("xmark", { store.signOut() }, size = 38.dp, iconSize = 14.dp, tint = MB.ink)
                    Spacer(Modifier.weight(1f))
                    Text("تسجيل مندوب", style = MBType.headline, color = MB.ink)
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.size(38.dp))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.forEachIndexed { i, name ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.fillMaxWidth().height(4.dp)
                                .background(if (i <= step) MB.red else MB.ink.copy(alpha = 0.12f), CircleShape))
                            Text(name, style = MBType.micro, color = if (i <= step) MB.red else MB.ink3)
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (step) {
                    0 -> Personal(draft, { draft = it }, identityProblem)
                    1 -> Vehicle(draft) { draft = it }
                    2 -> Documents(draft, { draft = it }, { showLegal = true })
                    else -> Review(draft, identityProblem, submitError, submitting,
                        onEdit = { step = it }, onLegal = { showLegal = true })
                }
                Spacer(Modifier.height(120.dp))
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = 14.dp).padding(bottom = 10.dp)
                .fillMaxWidth()
                .glassSurface(30.dp, null, 0.22f)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step > 0) {
                SecondaryCTA("السابق", onClick = { saveProgress(); step -= 1 },
                    modifier = Modifier.width(130.dp), symbol = "chevron.backward")
            }
            PrimaryCTA(
                if (step == lastStep) "إرسال للتحقّق" else "التالي",
                onClick = { if (step < lastStep) { saveProgress(); step += 1 } else submit() },
                modifier = Modifier.weight(1f),
                symbol = if (step == lastStep) "paperplane.fill" else "chevron.forward",
                tint = MB.red, enabled = canContinue && !submitting,
            )
        }
    }

    if (showLegal) LegalView(initialAudience = com.jaynak.app.core.UserRole.customer, onDismiss = { showLegal = false })
}

@Composable
private fun InfoNote(text: String) {
    Row(Modifier.fillMaxWidth().glassSurface(16.dp, null, 0.06f).padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
        SFIcon("lock.shield.fill", size = 16.dp, tint = MB.green)
        Text(text, style = MBType.caption, color = MB.ink2, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AreaChip(title: String, selected: Boolean, pick: () -> Unit) {
    Text(
        title, style = MBType.caption, color = if (selected) Color.White else MB.ink,
        modifier = Modifier
            .glassChip()
            .clip(CircleShape)
            .background(if (selected) MB.red else Color.Transparent, CircleShape)
            .pressable { Feedback.selection(); pick() }
            .padding(horizontal = 13.dp, vertical = 9.dp),
    )
}

@Composable
private fun Personal(draft: Driver, set: (Driver) -> Unit, identityProblem: String?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        InfoNote("بياناتك تُستخدم للتحقّق من هويتك فقط، ولا تُعرض لأي عميل.")
        MBField("الاسم الكامل", draft.name, { set(draft.copy(name = it)) },
            placeholder = "كما في البطاقة المدنية", symbol = "person.fill")
        MBField("رقم البطاقة المدنية", draft.docs.civilID, {
            val clipped = it.filter { c -> c.isDigit() }.take(AppConfig.civilIDLength)
            set(draft.copy(docs = draft.docs.copy(civilID = clipped)))
        }, placeholder = "٨ أرقام", symbol = "person.text.rectangle.fill",
            keyboard = KeyboardType.Number, isRTLText = false)

        if (draft.docs.civilID.isNotEmpty() && draft.docs.civilID.digitsOnly.length < AppConfig.civilIDLength) {
            Text("الرقم المدني يجب أن يكون ٨ أرقام.", style = MBType.micro, color = MB.red,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
        }
        if (identityProblem != null) {
            Row(Modifier.fillMaxWidth().background(MB.red.copy(alpha = 0.10f), RoundedCornerShape(14.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                SFIcon("exclamationmark.shield.fill", size = 14.dp, tint = MB.red)
                Text(identityProblem, style = MBType.micro, color = MB.red, textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f))
            }
        } else {
            Text("رقم مدني واحد = حساب مندوب واحد، ولا يُستخدم مرة ثانية.", style = MBType.micro, color = MB.ink2,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
        }
        Row(Modifier.fillMaxWidth().glassSurface(16.dp, null, 0.06f).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("رقم الهاتف", style = MBType.caption, color = MB.ink2)
            Spacer(Modifier.weight(1f))
            Text("${AppConfig.countryDialCode} ${draft.phone}", style = MBType.num(15, FontWeight.SemiBold), color = MB.ink)
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
            Text("المحافظة", style = MBType.caption, color = MB.ink2)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Oman.governorates.forEach { g ->
                    AreaChip(g.name, draft.governorate == g.name) {
                        set(draft.copy(governorate = g.name, zone = g.wilayat.firstOrNull() ?: ""))
                    }
                }
            }
            Text("الولاية", style = MBType.caption, color = MB.ink2, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Oman.wilayat(draft.governorate).forEach { w ->
                    AreaChip(w, draft.zone == w) { set(draft.copy(zone = w)) }
                }
            }
            Text("تصلك الطلبات في ولايتك أولاً، ثم القريب منها.", style = MBType.micro, color = MB.ink2,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun Vehicle(draft: Driver, set: (Driver) -> Unit) {
    val v = draft.vehicle
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        InfoNote("بيانات المركبة تظهر للعميل أثناء الطلب حتى يتعرّف عليك عند الوصول.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MBField("نوع السيارة", v.make, { set(draft.copy(vehicle = v.copy(make = it))) },
                Modifier.weight(1f), placeholder = "تويوتا")
            MBField("الموديل", v.model, { set(draft.copy(vehicle = v.copy(model = it))) },
                Modifier.weight(1f), placeholder = "كورولا")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MBField("سنة الصنع", v.year, { set(draft.copy(vehicle = v.copy(year = it))) },
                Modifier.weight(1f), placeholder = "2022", keyboard = KeyboardType.Number, isRTLText = false)
            MBField("اللون", v.color, { set(draft.copy(vehicle = v.copy(color = it))) },
                Modifier.weight(1f), placeholder = "أبيض")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MBField("رقم اللوحة", v.plateNumber, {
                set(draft.copy(vehicle = v.copy(plateNumber = it.filter { c -> c.isDigit() }.take(5))))
            }, Modifier.weight(1f), placeholder = "١٢٣٤٥", symbol = "rectangle.and.text.magnifyingglass",
                keyboard = KeyboardType.Number, isRTLText = false)
            MBField("حروف اللوحة", v.plateLetters, {
                set(draft.copy(vehicle = v.copy(plateLetters = it.filter { c -> c.isLetter() }.take(2))))
            }, Modifier.weight(1f), placeholder = "أ ب", isRTLText = false)
        }
        Text("لوحات عُمان: رقم مع حرف أو حرفين — مثال ١٢٣٤٥ أ ب", style = MBType.micro, color = MB.ink2,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Documents(draft: Driver, set: (Driver) -> Unit, onLegal: () -> Unit) {
    var showDate by remember { mutableStateOf(false) }
    // تحويل يُطبَّق على أحدث نسخة (تحديثات الرفع تصل بشكل غير متزامن)
    val latest = rememberUpdatedState(draft)
    val update: ((com.jaynak.app.core.DriverDocuments) -> com.jaynak.app.core.DriverDocuments) -> Unit = { f ->
        latest.value.let { set(it.copy(docs = f(it.docs))) }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        InfoNote("صور المستندات تُحفظ مشفّرة وتُستخدم للتحقّق فقط. يمكنك التصوير بالكاميرا أو الإرفاق من المعرض.")

        DocumentUploadCard(DocumentKind.civilIDFront, draft.docs, update)
        DocumentUploadCard(DocumentKind.civilIDBack, draft.docs, update)

        MBField("رقم رخصة القيادة", draft.docs.licenseNumber,
            { update { d -> d.copy(licenseNumber = it) } },
            placeholder = "L-12345", symbol = "creditcard.fill", isRTLText = false)

        DocumentUploadCard(DocumentKind.license, draft.docs, update)
        DocumentUploadCard(DocumentKind.ownership, draft.docs, update)
        DocumentUploadCard(DocumentKind.vehicle, draft.docs, update)
        DocumentUploadCard(DocumentKind.selfie, draft.docs, update)

        Row(Modifier.fillMaxWidth().glassSurface(16.dp, null, 0.06f).pressable(0.99f) { showDate = true }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("تاريخ انتهاء الرخصة", style = MBType.callout, color = MB.ink)
            Spacer(Modifier.weight(1f))
            Text(Fmt.dayMonthYear(draft.docs.licenseExpiry), style = MBType.callout, color = MB.green,
                modifier = Modifier.background(MB.green.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp))
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("الشروط وسياسة الخصوصية", style = MBType.caption, color = MB.green,
                modifier = Modifier.pressable(0.97f, onClick = onLegal))
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = draft.docs.licenseExpiry)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { ms -> update { d -> d.copy(licenseExpiry = ms) } }
                    showDate = false
                }) { Text("تم") }
            },
            dismissButton = { TextButton({ showDate = false }) { Text("إلغاء") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun Review(
    draft: Driver, identityProblem: String?, submitError: String?, submitting: Boolean,
    onEdit: (Int) -> Unit, onLegal: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
            Text("راجع بياناتك", style = MBType.title, color = MB.ink)
            Text("تأكّد أن كل شيء صحيح. بعد الإرسال لا يمكن التعديل حتى تنتهي المراجعة.",
                style = MBType.caption, color = MB.ink2, textAlign = TextAlign.End)
        }

        ReviewSection("بياناتك", 0, onEdit) {
            ReviewRow("الاسم الكامل", draft.name)
            ReviewRow("الرقم المدني", draft.docs.civilID.digitsOnly)
            ReviewRow("رقم الهاتف", "${AppConfig.countryDialCode} ${draft.phone}", note = "مُتحقَّق")
            ReviewRow("منطقة العمل", draft.zone)
        }
        ReviewSection("المركبة", 1, onEdit) {
            ReviewRow("المركبة", "${draft.vehicle.make} ${draft.vehicle.model}")
            ReviewRow("رقم اللوحة", draft.vehicle.plateDisplay)
        }
        ReviewSection("المستندات", 2, onEdit) {
            ReviewRow("رقم الرخصة", draft.docs.licenseNumber)
            DocumentKind.values().forEach { kind ->
                val files = draft.docs.assetsOf(kind)
                if (files.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.End) {
                        Text(kind.title, style = MBType.caption, color = MB.ink2)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { files.forEach { ReviewThumb(it) } }
                    }
                }
            }
        }

        identityProblem?.let { CalloutRow(it, MB.red, "exclamationmark.shield.fill") }
        submitError?.let { CalloutRow(it, MB.red, "exclamationmark.triangle.fill") }
        if (draft.docs.pendingUpload.isNotEmpty()) {
            CalloutRow("${draft.docs.pendingUpload.size} ملف لم يُرفع بعد — سيُرفع عند الإرسال.", MB.gold, "arrow.up.circle")
        }
        if (submitting) {
            Row(Modifier.fillMaxWidth().glassSurface(16.dp, null, 0.06f).padding(13.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = MB.red, strokeWidth = 2.dp)
                Text("جاري رفع المستندات وإرسال الطلب…", style = MBType.caption, color = MB.ink2)
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("الشروط وسياسة الخصوصية", style = MBType.caption, color = MB.red,
                modifier = Modifier.pressable(0.97f, onClick = onLegal))
        }
    }
}

@Composable
private fun ReviewSection(title: String, target: Int, onEdit: (Int) -> Unit, content: @Composable () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MBType.headline, color = MB.ink)
                Spacer(Modifier.weight(1f))
                Row(Modifier.clip(CircleShape).background(MB.ink.copy(alpha = 0.07f))
                    .pressable { onEdit(target) }.padding(horizontal = 11.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    SFIcon("pencil", size = 10.dp, tint = MB.ink)
                    Text("تعديل", style = MBType.micro, color = MB.ink)
                }
            }
            HorizontalDivider(Modifier.alpha(0.25f), color = MB.hairline)
            content()
        }
    }
}

@Composable
private fun ReviewRow(key: String, value: String, note: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = MBType.caption, color = MB.ink2)
        Spacer(Modifier.weight(1f))
        if (note != null) Text(note, style = MBType.micro, color = MB.green)
        Text(if (value.isEmpty()) "—" else value, style = MBType.callout,
            color = if (value.isEmpty()) MB.ink3 else MB.ink, textAlign = TextAlign.Start)
    }
}

@Composable
private fun CalloutRow(text: String, tint: Color, symbol: String) {
    Row(Modifier.fillMaxWidth().background(tint.copy(alpha = 0.10f), RoundedCornerShape(14.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        SFIcon(symbol, size = 14.dp, tint = tint)
        Text(text, style = MBType.micro, color = tint, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}
