package com.jaynak.app.features.auth
import androidx.compose.foundation.layout.union
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AppConfig
import com.jaynak.app.core.AppStore
import com.jaynak.app.core.AuthMode
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.ImpactStyle
import com.jaynak.app.core.OTPService
import com.jaynak.app.core.UserRole
import com.jaynak.app.core.digitsOnly
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.BrandLoader
import com.jaynak.app.design.BrandMark
import com.jaynak.app.design.CircleGlassButton
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.LocalNav
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBField
import com.jaynak.app.design.MBType
import com.jaynak.app.design.NavBar
import com.jaynak.app.design.NavStack
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.Wordmark
import com.jaynak.app.design.glassChip
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.pressable
import com.jaynak.app.features.shared.LegalView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ───────── الغلاف (AuthSheet) ─────────

/** مقابل AuthSheet: ورقة بمكدّس خاص — اختيار الدور → النمط → الرقم → الرمز. */
@Composable
fun AuthSheet(initialRole: UserRole?, reason: String = "", onDismiss: () -> Unit) {
    val store = LocalStore.current
    LaunchedEffect(store.session) { if (store.session != null) onDismiss() }

    JSheet(onDismiss = onDismiss, fullHeight = true) {
        Box(Modifier.fillMaxSize()) {
            NavStack {
                val nav = LocalNav.current
                // الجذر
                if (initialRole != null) {
                    ModeChoiceView(
                        role = initialRole,
                        onBack = null,
                    ) { picked ->
                        nav.push {
                            PhoneStep(initialRole, picked, reason)
                        }
                    }
                } else {
                    RoleChoiceView(reason = reason) { pickedRole ->
                        nav.push {
                            ModeChoiceView(
                                role = pickedRole,
                                onBack = { nav.pop() },
                            ) { mode ->
                                nav.push { PhoneStep(pickedRole, mode, reason) }
                            }
                        }
                    }
                }
            }
            // زر الإغلاق (topBarLeading)
            Box(Modifier.align(Alignment.TopStart).padding(14.dp)) {
                CircleGlassButton("xmark", onDismiss)
            }
        }
    }
}

@Composable
private fun PhoneStep(role: UserRole, mode: AuthMode, reason: String) {
    val nav = LocalNav.current
    PhoneEntryView(role = role, mode = mode, reason = reason, onBack = { nav.pop() }) { outcome ->
        if (outcome == AppStore.AuthOutcome.codeSent) {
            nav.push { OTPView(role = role, mode = mode, onBack = { nav.pop() }) }
        }
    }
}

// ───────── عناصر مشتركة ─────────

@Composable
private fun AuthOrb(symbol: String, tint: Color, size: Float = 96f) {
    val t by rememberInfiniteTransition(label = "orb").animateFloat(
        0.94f, 1.08f,
        androidx.compose.animation.core.InfiniteRepeatableSpec(
            tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    Box(Modifier.height((size * 1.1f).dp), contentAlignment = Alignment.Center) {
        val glow = Modifier.size((size * 1.5f).dp).scale(t)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            Box(glow.blur((size * 0.42f).dp).background(tint.copy(alpha = 0.45f), CircleShape))
        } else {
            Box(glow.background(Brush.radialGradient(listOf(tint.copy(alpha = 0.45f), Color.Transparent)), CircleShape))
        }
        Box(
            Modifier.size(size.dp)
                .shadow((size * 0.18f).dp, CircleShape, ambientColor = tint.copy(alpha = 0.5f), spotColor = tint.copy(alpha = 0.5f))
                .background(
                    Brush.linearGradient(
                        listOf(tint, tint.copy(alpha = 0.45f)),
                        start = Offset(Float.POSITIVE_INFINITY, 0f), end = Offset(0f, Float.POSITIVE_INFINITY)),
                    CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { SFIcon(symbol, size = (size * 0.40f).dp, tint = Color.White) }
    }
}

@Composable
private fun TrustNote(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SFIcon("lock.fill", size = 10.dp, tint = MB.green)
        Spacer(Modifier.width(7.dp))
        Text(text, style = MBType.micro, color = MB.ink3, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AuthPage(
    onBack: (() -> Unit)?,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        BrandBackground(intensity = 1f)
        Column(Modifier.fillMaxSize()) {
            NavBar(onBack = onBack)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
                if (bottomBar != null) Spacer(Modifier.height(100.dp))
            }
        }
        if (bottomBar != null) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) { bottomBar() }
        }
    }
}

// ───────── اختيار النمط ─────────

@Composable
fun ModeChoiceView(role: UserRole, onBack: (() -> Unit)?, onPick: (AuthMode) -> Unit) {
    val tint = if (role == UserRole.driver) MB.red else MB.green
    AuthPage(onBack) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Box(Modifier.padding(top = 8.dp)) { AuthOrb(role.symbol, tint) }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (role == UserRole.driver) "دخول المندوبين" else "دخول الزبائن",
                    style = MBType.title, color = MB.ink)
                Text("عندك حساب من قبل؟ ادخل برقمك. وإلا أنشئ حساباً جديداً.",
                    style = MBType.caption, color = MB.ink2, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 36.dp))
            }

            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ModeCard(AuthMode.signIn, tint, "رقمك ورمز التحقّق — وتدخل مباشرة") { onPick(AuthMode.signIn) }
                ModeCard(
                    AuthMode.signUp, tint,
                    if (role == UserRole.driver) "تعبّي بياناتك ومركبتك ومستنداتك مرة واحدة"
                    else "دقيقة واحدة وتصير جاهزاً للطلب",
                ) { onPick(AuthMode.signUp) }
            }

            TrustNote("رقمك للتحقّق فقط — لا نشاركه مع أحد", Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun ModeCard(mode: AuthMode, tint: Color, note: String, action: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .pressable { Feedback.impact(ImpactStyle.medium); action() }
            .glassSurface(22.dp, null, 0.10f)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(56.dp).background(
                if (mode == AuthMode.signUp)
                    Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.55f)))
                else SolidColor(tint.copy(alpha = 0.16f)),
                CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SFIcon(
                if (mode == AuthMode.signIn) "arrow.right.to.line.circle.fill" else "person.badge.plus.fill",
                size = 22.dp, tint = if (mode == AuthMode.signUp) Color.White else tint)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(mode.title, style = MBType.headline, color = MB.ink)
            Text(note, style = MBType.micro, color = MB.ink2, textAlign = TextAlign.End)
        }
        SFIcon("chevron.backward", size = 13.dp, tint = MB.ink3)
    }
}

// ───────── اختيار الدور ─────────

@Composable
fun RoleChoiceView(reason: String = "", onPick: (UserRole) -> Unit) {
    AuthPage(onBack = null) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(Modifier.padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BrandMark(88.dp)
                Wordmark(22)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("كيف تحب تدخل؟", style = MBType.title, color = MB.ink)
                Text(
                    reason.ifEmpty { "اختر نوع الحساب — الدخول يأخذ ثانية واحدة" },
                    style = MBType.caption, color = MB.ink2, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 36.dp))
            }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RoleCard(UserRole.customer, MB.green) { onPick(UserRole.customer) }
                RoleCard(UserRole.driver, MB.red) { onPick(UserRole.driver) }
            }
            TrustNote("رقمك للتحقّق فقط — لا نشاركه مع أحد", Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun RoleCard(role: UserRole, tint: Color, action: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .pressable { Feedback.impact(ImpactStyle.medium); action() }
            .glassSurface(24.dp)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(56.dp)
                .shadow(10.dp, CircleShape, ambientColor = tint.copy(alpha = 0.45f), spotColor = tint.copy(alpha = 0.45f))
                .background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.55f))), CircleShape),
            contentAlignment = Alignment.Center,
        ) { SFIcon(role.symbol, size = 23.dp, tint = Color.White) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(role.loginLabel, style = MBType.ar(19, FontWeight.Bold), color = MB.ink)
            Text(role.subtitle, style = MBType.caption, color = MB.ink2, textAlign = TextAlign.End)
        }
        SFIcon("chevron.forward", size = 14.dp, tint = tint)
    }
}

// ───────── إدخال الرقم ─────────

@Composable
fun PhoneEntryView(
    role: UserRole,
    mode: AuthMode = AuthMode.signUp,
    reason: String = "",
    onBack: (() -> Unit)?,
    onSent: (AppStore.AuthOutcome) -> Unit,
) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val tint = if (role == UserRole.driver) MB.red else MB.green

    var digits by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var agreed by remember { mutableStateOf(false) }
    var showLegal by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    val complete = digits.length == AppConfig.nationalNumberLength
    val needsName = mode == AuthMode.signUp
    val valid = complete && agreed && (!needsName || name.trim().length >= (if (role == UserRole.driver) 3 else 2))

    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val lockLeft = remember(tick) { com.jaynak.app.core.SendLock.remainingSec() }

    LaunchedEffect(Unit) { delay(450); runCatching { focus.requestFocus() } }
    LaunchedEffect(complete) { if (complete) Feedback.selection() }

    AuthPage(
        onBack = onBack,
        bottomBar = {
            PrimaryCTA(
                title = if (lockLeft > 0) "مقفل — ${com.jaynak.app.core.SendLock.format(lockLeft)}"
                    else if (sending) "جاري الإرسال…" else "إرسال الرمز",
                symbol = "paperplane.fill", tint = tint, enabled = valid && lockLeft == 0, loading = sending,
                onClick = {
                    sending = true
                    scope.launch {
                        store.pendingSignUpName = name
                        val outcome = store.requestOTP(digits, role, mode)
                        sending = false
                        onSent(outcome)
                    }
                },
            )
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.padding(top = 4.dp)) { AuthOrb(role.symbol, tint) }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (role == UserRole.driver) "سجّل كمندوب" else "رقم هاتفك",
                    style = MBType.title, color = MB.ink)
                Text(
                    reason.ifEmpty { "نرسل لك رمز تحقّق على واتساب — بلا كلمة مرور" },
                    style = MBType.caption, color = MB.ink2, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 28.dp))
            }

            // حقل الرقم
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val grouped = if (digits.length > 4) "${digits.take(4)} ${digits.drop(4)}" else digits
                Row(
                    Modifier.fillMaxWidth().height(72.dp)
                        .glassSurface(22.dp, if (focused) tint.copy(alpha = 0.18f) else null)
                        .border(
                            if (complete) 2.dp else 1.5.dp,
                            if (complete) tint else if (focused) tint.copy(alpha = 0.6f) else Color.Transparent,
                            RoundedCornerShape(22.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            focus.requestFocus()
                        }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SFIcon("phone.fill", size = 15.dp, tint = tint)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(AppConfig.countryDialCode, style = MBType.num(20, FontWeight.Medium), color = MB.ink2)
                    }
                    Box(Modifier.width(1.dp).height(28.dp).background(MB.hairline))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        val st = MBType.num(26, FontWeight.SemiBold).copy(color = MB.ink, textAlign = TextAlign.End, textDirection = androidx.compose.ui.text.style.TextDirection.Ltr)
                        if (grouped.isEmpty()) Text("9XXX XXXX", style = st.copy(color = MB.ink3), maxLines = 1)
                        BasicTextField(
                            value = androidx.compose.ui.text.input.TextFieldValue(grouped, androidx.compose.ui.text.TextRange(grouped.length)),
                            onValueChange = { digits = it.text.digitsOnly.take(AppConfig.nationalNumberLength) },
                            singleLine = true, textStyle = st, cursorBrush = SolidColor(tint),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus)
                                .onFocusChanged { focused = it.isFocused },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (i in 0 until AppConfig.nationalNumberLength) {
                        Box(Modifier.weight(1f).height(3.dp).background(
                            if (i < digits.length) tint else MB.ink.copy(alpha = 0.12f), CircleShape))
                    }
                }
            }

            if (needsName) {
                MBField("الاسم الكامل", name, { name = it }, Modifier.padding(horizontal = 20.dp),
                    placeholder = if (role == UserRole.driver) "كما في البطاقة المدنية" else "اكتب اسمك ليعرفك المندوب", symbol = "person.fill")
            }

            // ملاحظة واتساب
            Row(
                Modifier.padding(horizontal = 20.dp).glassChip().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                SFIcon("message.badge.filled.fill", size = 13.dp, tint = MB.green)
                Text("يوصلك الرمز على واتساب على هذا الرقم", style = MBType.caption, color = MB.ink2)
            }

            // الموافقة
            Row(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                    .pressable(0.99f) { Feedback.selection(); agreed = !agreed; if (!agreed) { /* iOS: focused=false */ } }
                    .glassSurface(18.dp, null, 0.08f)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(
                    Modifier.size(24.dp)
                        .background(if (agreed) tint else Color.Transparent, RoundedCornerShape(7.dp))
                        .border(1.5.dp, if (agreed) tint else MB.ink3, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) { if (agreed) SFIcon("checkmark", size = 12.dp, tint = Color.White) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("أوافق على الشروط وسياسة الخصوصية", style = MBType.callout, color = MB.ink)
                    Text("اقرأها قبل الموافقة", style = MBType.micro, color = tint,
                        modifier = Modifier.clickable { showLegal = true })
                }
            }

            if (lockLeft > 0) {
                Text("تم إيقاف الإرسال مؤقتاً لكثرة المحاولات — يُفتح تلقائياً بعد ${com.jaynak.app.core.SendLock.format(lockLeft)}",
                    style = MBType.caption, color = MB.red, modifier = Modifier.padding(horizontal = 20.dp))
            }

            store.authError?.let { err ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 20.dp)) {
                    SFIcon("exclamationmark.triangle.fill", size = 13.dp, tint = MB.red)
                    Text(err, style = MBType.caption, color = MB.red)
                }
            }

            TrustNote("بياناتك محمية ولا نستخدم رقمك في أي إعلان", Modifier.padding(horizontal = 30.dp).padding(top = 2.dp))
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showLegal) LegalView(initialAudience = role, onDismiss = { showLegal = false })
}

// ───────── رمز التحقق ─────────

@Composable
fun OTPView(role: UserRole, mode: AuthMode = AuthMode.signUp, onBack: (() -> Unit)?) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val tint = if (role == UserRole.driver) MB.red else MB.green

    var code by remember { mutableStateOf("") }
    var remaining by remember { mutableStateOf(AppConfig.otpResendSeconds) }
    var verifying by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { delay(450); runCatching { focus.requestFocus() } }
    LaunchedEffect(Unit) { while (remaining > 0) { delay(1000); remaining-- } }

    fun submit() {
        if (verifying) return
        verifying = true
        scope.launch {
            val outcome = store.verifyCode(code)
            if (outcome == AppStore.VerifyOutcome.needsRole) {
                store.restoreAccountFromServer(role)
                if (store.completeSignIn(role, mode, store.pendingSignUpName)) {
                    // سجّل الحساب على السيرفر فوراً — كي لا يضيع لو حُذف التطبيق قبل أول طلب
                    runCatching { store.syncProfile() }
                }
            }
            val ok = outcome != AppStore.VerifyOutcome.failed
            verifying = false
            if (ok) return@launch
            shake.animateTo(9f, spring(dampingRatio = 0.4f, stiffness = 600f))
            delay(220)
            shake.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 600f))
            code = ""
        }
    }

    AuthPage(
        onBack = onBack,
        bottomBar = {
            PrimaryCTA(
                title = if (verifying) "جاري التحقّق…" else "تأكيد",
                symbol = "checkmark", tint = tint,
                enabled = code.length == AppConfig.otpLength, loading = verifying,
                onClick = { submit() },
            )
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.padding(top = 4.dp)) { AuthOrb("message.badge.filled.fill", tint, 88f) }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("أدخل الرمز", style = MBType.title, color = MB.ink)
                Text(
                    if (AppConfig.apiBaseURL == null) "${AppConfig.otpLength} أرقام — وضع تجربة محلي"
                    else "أرسلنا ${AppConfig.otpLength} أرقام على واتساب",
                    style = MBType.caption, color = MB.ink2)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text("${AppConfig.countryDialCode} ${store.challenge?.phone ?: ""}",
                        style = MBType.num(15, FontWeight.SemiBold), color = MB.ink)
                }
            }

            // خانات الرمز
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                BasicTextField(
                    value = code,
                    onValueChange = { raw ->
                        val new = raw.digitsOnly.take(AppConfig.otpLength)
                        code = new
                        Feedback.selection()
                        if (new.length == AppConfig.otpLength) submit()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.size(1.dp).alpha(0.01f).focusRequester(focus),
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        Modifier.fillMaxWidth().offset(x = shake.value.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                focus.requestFocus()
                            },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (i in 0 until AppConfig.otpLength) {
                            val filled = i < code.length
                            val active = i == code.length
                            val sc by animateFloatAsState(if (filled) 1f else if (active) 1.04f else 0.98f,
                                spring(stiffness = 520f), label = "box")
                            Box(
                                Modifier.weight(1f).height(68.dp).scale(sc)
                                    .glassSurface(20.dp, if (filled) tint.copy(alpha = 0.28f) else null)
                                    .border(
                                        if (active) 2.dp else 1.5.dp,
                                        if (active) tint else if (filled) tint.copy(alpha = 0.55f) else Color.Transparent,
                                        RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (filled) code[i].toString() else "",
                                    style = MBType.num(30, FontWeight.Bold), color = MB.ink)
                            }
                        }
                    }
                }
            }

            store.authError?.let { err ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SFIcon("exclamationmark.triangle.fill", size = 13.dp, tint = MB.red)
                    Text(err, style = MBType.caption, color = MB.red)
                }
            }

            // إعادة الإرسال
            if (remaining > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val frac by animateFloatAsState(
                        remaining.toFloat() / AppConfig.otpResendSeconds,
                        tween(1000, easing = androidx.compose.animation.core.LinearEasing), label = "ring")
                    val track = MB.ink.copy(alpha = 0.12f)
                    Canvas(Modifier.size(20.dp)) {
                        val sw = 2.5.dp.toPx()
                        val inset = sw / 2
                        val sz = Size(size.width - sw, size.height - sw)
                        drawArc(track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(sw))
                        drawArc(tint, -90f, 360f * frac, false, Offset(inset, inset), sz,
                            style = Stroke(sw, cap = StrokeCap.Round))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("إعادة الإرسال بعد", style = MBType.caption, color = MB.ink3)
                        Text("$remaining", style = MBType.num(13, FontWeight.SemiBold), color = MB.ink3)
                        Text("ثانية", style = MBType.caption, color = MB.ink3)
                    }
                }
            } else {
                Row(
                    Modifier.pressable {
                        scope.launch {
                            store.resendOTP()
                            code = ""
                            remaining = store.challenge?.resendAfter ?: AppConfig.otpResendSeconds
                        }
                    }.glassChip().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SFIcon("arrow.clockwise", size = 14.dp, tint = tint)
                    Text("إعادة إرسال الرمز", style = MBType.callout, color = tint)
                }
            }

            store.challenge?.takeIf { it.devCode != null }?.let { ch -> DemoBar(ch) { code = ch.devCode ?: "" } }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DemoBar(ch: OTPService.Challenge, onPaste: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth()
            .glassSurface(18.dp, MB.gold.copy(alpha = 0.20f), 0.10f)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SFIcon("hammer.fill", size = 16.dp, tint = MB.gold)
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (ch.isRemote) "السيرفر في وضع التطوير" else "وضع التجربة — لا يوجد سيرفر",
                style = MBType.micro, color = MB.ink2)
            Text("الرمز: ${ch.devCode ?: ""}", style = MBType.num(17, FontWeight.Bold), color = MB.ink)
        }
        Spacer(Modifier.weight(1f))
        Text("لصق", style = MBType.callout, color = MB.gold,
            modifier = Modifier.pressable(onClick = onPaste).glassChip().padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

// ───────── بوابة الضيف + مراقب الدخول ─────────

/** مقابل GuestGate. */
@Composable
fun GuestGate(
    symbol: String,
    title: String,
    message: String,
    cta: String = "تسجيل الدخول",
    role: UserRole? = UserRole.customer,
) {
    val store = LocalStore.current
    GlassCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(66.dp).background(MB.green.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center) {
                SFIcon(symbol, size = 26.dp, tint = MB.green)
            }
            Text(title, style = MBType.section, color = MB.ink)
            Text(message, style = MBType.caption, color = MB.ink2, textAlign = TextAlign.Center)
            PrimaryCTA(cta, onClick = { store.requestLogin(role, message) },
                symbol = "arrow.forward.circle.fill", modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** مقابل presentsAuth(onSignedIn:) — الورقة نفسها تُعرض من الجذر، وهنا فقط مراقبة الدخول. */
@Composable
fun PresentsAuth(onSignedIn: () -> Unit) {
    val store = LocalStore.current
    var was by remember { mutableStateOf(store.session != null) }
    LaunchedEffect(store.session) {
        val now = store.session != null
        if (!was && now) onSignedIn()
        was = now
    }
}
