package com.jaynak.app.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private inline fun <T> List<T>.mapAt(i: Int, f: (T) -> T): List<T> {
    val m = toMutableList()
    m[i] = f(m[i])
    return m
}

/**
 * مقابل AppStore.swift حرفياً: الحالة المحلية كاملة + المحاكاة + المزامنة مع الخادم.
 * كل الشاشات تقرأ منه وتكتب إليه، تماماً كما في iOS.
 */
class AppStore {

    // ───────── الحالة ─────────

    var customers by mutableStateOf(listOf<Customer>())
    var drivers by mutableStateOf(listOf<Driver>())
    var orders by mutableStateOf(listOf<Order>())
    var transactions by mutableStateOf(listOf<WalletTxn>())
    var rules by mutableStateOf(PricingRules())
    var promos by mutableStateOf(listOf<PromoCode>())
    var tickets by mutableStateOf(listOf<SupportTicket>())
    var chats by mutableStateOf(listOf<ChatMessage>())
    var admins by mutableStateOf(listOf<AdminUser>())
    var rewards by mutableStateOf(listOf<Reward>())
    var vouchers by mutableStateOf(listOf<Voucher>())
    var deletionRequests by mutableStateOf(listOf<AccountDeletionRequest>())

    var identities by mutableStateOf(listOf<IdentityRecord>())

    var session by mutableStateOf<Session?>(null)
    var challenge by mutableStateOf<OTPService.Challenge?>(null)
    var authError by mutableStateOf<String?>(null)

    /** الاسم الذي كتبه المستخدم في شاشة الرقم، ليُستعمل عند إنشاء الحساب. */
    var pendingSignUpName by mutableStateOf("")

    var toast by mutableStateOf<Toast?>(null)
    var pendingWalletTopUpReference by mutableStateOf<String?>(null)

    var authRequest by mutableStateOf<AuthRequest?>(null)

    data class AuthRequest(
        val id: Id = newId(),
        val role: UserRole? = null,
        val reason: String = "",
    )

    data class Toast(
        val id: Id = newId(),
        val text: String,
        val kind: Kind = Kind.info,
    ) {
        enum class Kind { info, success, error }
    }

    private val progress = mutableMapOf<Id, Double>()
    private val routes = mutableMapOf<Id, List<Coord>>()
    private var tickJob: Job? = null
    private var saveJob: Job? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        val s = Persistence.load()
        if (s != null && s.seeded && s.seedVersion == currentSeedVersion) {
            customers = s.customers; drivers = s.drivers; orders = s.orders
            transactions = s.transactions; rules = s.rules; promos = s.promos
            tickets = s.tickets; chats = s.chats; session = s.session
            admins = s.admins; rewards = s.rewards; vouchers = s.vouchers
            deletionRequests = s.deletionRequests
            admins = admins.filter { it.phone != AppConfig.demoAdminPhone }
            identities = s.identities
            if (admins.isEmpty()) {
                admins = listOf(
                    AdminUser(name = "المالك", phone = AppConfig.ownerPhone, role = AdminRole.owner),
                )
            }
        } else {
            bootstrap()
        }
        rebuildRoutes()
        startTicking()
    }

    private fun persistedState() = PersistedState(
        customers = customers, drivers = drivers, orders = orders,
        transactions = transactions, rules = rules, promos = promos,
        tickets = tickets, chats = chats,
        admins = admins, rewards = rewards, vouchers = vouchers,
        deletionRequests = deletionRequests,
        identities = identities,
        session = session, seeded = true,
        seedVersion = currentSeedVersion,
    )

    /** حفظ مؤجَّل 0.6 ثانية — يُدمج التغييرات المتلاحقة في كتابة واحدة. */
    fun save() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(600)
            val state = persistedState()
            withContext(Dispatchers.IO) { Persistence.save(state) }
        }
    }

    // ───────── Bootstrap.swift ─────────

    fun bootstrap() {
        customers = emptyList()
        drivers = emptyList()
        orders = emptyList()
        transactions = emptyList()
        tickets = emptyList()
        chats = emptyList()
        vouchers = emptyList()
        promos = emptyList()
        deletionRequests = emptyList()

        rules = PricingRules()
        rewards = Loyalty.defaultRewards()

        admins = listOf(
            AdminUser(name = "المالك", phone = AppConfig.ownerPhone, role = AdminRole.owner),
                    )

        save()
    }

    // ───────── المصادقة ─────────

    fun normalize(raw: String): String =
        raw.digitsOnly.takeLast(AppConfig.nationalNumberLength)

    val isDemoAdminPhone: Boolean
        get() {
            val p = challenge?.phone ?: return false
            return p == AppConfig.demoAdminPhone
        }

    enum class AuthOutcome {
        /** دخل مباشرة بلا رمز (حساب العرض) */
        enteredDirectly,

        /** أُرسل الرمز */
        codeSent,
        failed,
    }

    suspend fun requestOTP(
        raw: String, role: UserRole, mode: AuthMode = AuthMode.signUp,
    ): AuthOutcome {
        authError = null
        val phone = normalize(raw)
        if (phone.length != AppConfig.nationalNumberLength) {
            authError = "رقم الهاتف يجب أن يكون ${AppConfig.nationalNumberLength} أرقام"
            return AuthOutcome.failed
        }

        // رقم العرض التجريبي — وليس رقم المالك الحقيقي.
        if (phone == AppConfig.demoAdminPhone && AppConfig.demoAdminBypassEnabled) {
            signInAdmin(phone, viaDemo = true)
            challenge = null
            return AuthOutcome.enteredDirectly
        }

        // حساب الإدارة يدخل برقمه أياً كان الدور المختار.
        val isAdminPhone = admins.any { it.phone == phone && it.isActive }

        if (mode == AuthMode.signIn && !isAdminPhone && !AppConfig.otpRequired && !hasAccount(phone, role)) {
            authError = if (role == UserRole.driver)
                "ما عندك حساب مندوب بهذا الرقم. ارجع واختر «إنشاء حساب»."
            else
                "ما عندك حساب بهذا الرقم. ارجع واختر «إنشاء حساب»."
            Feedback.warning()
            return AuthOutcome.failed
        }

        val existing = customers.firstOrNull { it.phone == phone }
        if (existing != null && existing.isBlocked && role == UserRole.customer) {
            authError = "هذا الحساب موقوف. تواصل مع الدعم."
            return AuthOutcome.failed
        }

        if (role == UserRole.driver) {
            val rec = identityByPhone(phone)
            if (rec != null && rec.isBarred) {
                authError = IdentityCheck.barred(rec.barReason ?: "قرار إداري",
                    rec.outstandingDebt).message
                return AuthOutcome.failed
            }
        }
        if (!AppConfig.otpRequired) { verifiedPhone = phone; challenge = null; if (isAdminPhone) { signInAdmin(phone, viaDemo = false); return if (session?.role == UserRole.admin) AuthOutcome.enteredDirectly else AuthOutcome.failed }; return if (completeSignIn(role, mode, pendingSignUpName)) AuthOutcome.enteredDirectly else AuthOutcome.failed }
        return try {
            challenge = OTPService.send(phone)
            Feedback.impact(ImpactStyle.light)
            AuthOutcome.codeSent
        } catch (e: Exception) {
            authError = e.message ?: "تعذّر إرسال الرمز"
            Feedback.warning()
            AuthOutcome.failed
        }
    }

    suspend fun resendOTP() {
        val phone = challenge?.phone ?: return
        authError = null
        try {
            challenge = OTPService.send(phone)
            toast = Toast(text = "أُرسل رمز جديد على واتساب")
            Feedback.impact(ImpactStyle.light)
        } catch (e: Exception) {
            authError = e.message ?: "تعذّر إعادة الإرسال"
            Feedback.warning()
        }
    }

    private fun signInAdmin(phone: String, viaDemo: Boolean) {
        val i = admins.indexOfFirst { it.phone == phone && it.isActive }
        if (i < 0) {
            authError = "لا يوجد حساب إدارة مفعّل بهذا الرقم"
            Feedback.warning()
            return
        }
        admins = admins.mapAt(i) { it.copy(lastSignIn = nowMs()) }
        session = Session(role = UserRole.admin, adminID = admins[i].id,
            phone = phone, viaDemoAccess = viaDemo)
        Feedback.success()
        toast = Toast(text = "أهلاً ${admins[i].name} · ${admins[i].role.title}",
            kind = Toast.Kind.success)
        save()
    }

    val currentAdmin: AdminUser?
        get() {
            val id = session?.adminID ?: return null
            return admins.firstOrNull { it.id == id }
        }

    fun can(permission: AdminPermission): Boolean =
        currentAdmin?.can?.contains(permission) ?: false

    enum class VerifyOutcome { failed, needsRole, signedIn }

    var verifiedPhone by mutableStateOf<String?>(null)

    suspend fun verifyCode(code: String): VerifyOutcome {
        val ch = challenge ?: return VerifyOutcome.failed
        try {
            OTPService.verify(code, ch)
        } catch (e: Exception) {
            challenge = ch.copy(attempts = ch.attempts + 1)
            authError = e.message ?: "رمز غير صحيح"
            Feedback.warning()
            return VerifyOutcome.failed
        }
        authError = null
        val phone = ch.phone
        verifiedPhone = phone

        if (admins.any { it.phone == phone && it.isActive }) {
            signInAdmin(phone, viaDemo = false)
            challenge = null
            verifiedPhone = null
            return if (session != null) VerifyOutcome.signedIn else VerifyOutcome.failed
        }
        return VerifyOutcome.needsRole
    }

    /** هل لهذا الرقم حساب بهذا الدور أصلاً؟ */
    fun hasAccount(raw: String, role: UserRole): Boolean {
        val phone = normalize(raw)
        return when (role) {
            UserRole.customer -> customers.any { it.phone == phone }
            UserRole.driver -> drivers.any { it.phone == phone }
            UserRole.admin -> admins.any { it.phone == phone && it.isActive }
        }
    }

    /**
     * بعد حذف التطبيق وإعادة تثبيته تكون الذاكرة المحلية فارغة، فنسأل السيرفر (المرجع)
     * هل لهذا الرقم حساب بهذا الدور، ونستعيده محلياً قبل إكمال الدخول.
     */
    suspend fun restoreAccountFromServer(role: UserRole) {
        val phone = verifiedPhone ?: return
        if (hasAccount(phone, role)) return
        try {
            val me = OrdersService.send("api/me", "GET", null)
            when (role) {
                UserRole.customer -> me.optJSONObject("customer")?.let { c ->
                    customers = customers + Customer(
                        name = c.optString("name").ifEmpty { "زبون ${phone.takeLast(4)}" },
                        phone = phone, walletBalance = c.optDouble("walletBalance", 0.0),
                        savedPlaces = listOf(Geo.place("جمعية حولي")),
                    )
                    save()
                }
                UserRole.driver -> me.optJSONObject("driver")?.let { d ->
                    val ap = runCatching { DriverApproval.valueOf(d.optString("approval")) }
                        .getOrDefault(DriverApproval.pending)
                    drivers = drivers + Driver(
                        name = d.optString("name").ifEmpty { "مندوب ${phone.takeLast(4)}" },
                        phone = phone, coord = Geo.cityCenter, approval = ap,
                        docs = DriverDocuments(adminVerified = d.optBoolean("adminCreated", false)),
                        earningsBalance = d.optDouble("earningsBalance", 0.0),
                    )
                    save()
                }
                else -> {}
            }
        } catch (_: Exception) { /* بلا اتصال: يبقى السلوك المحلي */ }
    }

    fun completeSignIn(
        role: UserRole, mode: AuthMode = AuthMode.signUp, name: String = "",
    ): Boolean {
        val phone = verifiedPhone ?: return false
        try {
            val exists = hasAccount(phone, role)

            // «الدخول بالرقم» لا يُنشئ حساباً — لا بد أن يكون موجوداً.
            if (mode == AuthMode.signIn && !exists) {
                authError = if (role == UserRole.driver)
                    "ما عندك حساب مندوب بهذا الرقم. اختر «إنشاء حساب» أولاً."
                else
                    "ما عندك حساب بهذا الرقم. اختر «إنشاء حساب» أولاً."
                challenge = null
                return false
            }

            // «إنشاء حساب» برقم مسجّل — ندخله بدل أن نوقفه.
            if (mode == AuthMode.signUp && exists) {
                toast = Toast(text = "عندك حساب بهذا الرقم — دخّلناك مباشرة.", kind = Toast.Kind.info)
            }

            when (role) {
                UserRole.customer -> {
                    val c: Customer
                    val found = customers.firstOrNull { it.phone == phone }
                    if (found != null) {
                        c = found
                    } else {
                        val new = Customer(
                            name = if (name.isEmpty()) "زبون ${phone.takeLast(4)}" else name,
                            phone = phone, walletBalance = 0.0,
                            savedPlaces = listOf(Geo.place("جمعية حولي")),
                        )
                        customers = customers + new
                        grantWelcomePromoCredit(new.id)
                        c = new
                    }
                    session = Session(role = UserRole.customer, customerID = c.id, phone = phone)
                }

                UserRole.driver -> {
                    val d: Driver
                    val found = drivers.firstOrNull { it.phone == phone }
                    if (found != null) {
                        d = found
                    } else {
                        val rec = identityByPhone(phone)
                        if (rec != null) {
                            authError = if (rec.isBarred)
                                IdentityCheck.barred(rec.barReason ?: "قرار إداري", rec.outstandingDebt).message
                            else
                                IdentityCheck.phoneTaken(rec.name).message
                            challenge = null
                            return false
                        }
                        val new = Driver(
                            name = if (name.isEmpty()) "مندوب ${phone.takeLast(4)}" else name,
                            phone = phone, coord = Geo.cityCenter,
                        )
                        drivers = drivers + new
                        d = new
                    }
                    session = Session(role = UserRole.driver, driverID = d.id, phone = phone)
                }

                UserRole.admin -> {
                    signInAdmin(phone, viaDemo = false)
                    challenge = null
                    return session != null
                }
            }

            challenge = null
            Feedback.success()
            save()
            return true
        } finally {
            verifiedPhone = null
        }
    }

    fun signInAsDemo(role: UserRole) {
        if (!AppConfig.demoAdminBypassEnabled) return
        when (role) {
            UserRole.customer -> {
                val c = customers.firstOrNull() ?: return
                session = Session(role = UserRole.customer, customerID = c.id,
                    phone = c.phone, viaDemoAccess = true)
                toast = Toast(text = "دخلت كزبون تجريبي: ${c.name}", kind = Toast.Kind.success)
            }
            UserRole.driver -> {
                val d = drivers.firstOrNull { it.approval == DriverApproval.approved } ?: return
                session = Session(role = UserRole.driver, driverID = d.id,
                    phone = d.phone, viaDemoAccess = true)
                toast = Toast(text = "دخلت كمندوب تجريبي: ${d.name}", kind = Toast.Kind.success)
            }
            UserRole.admin -> {
                session = Session(role = UserRole.admin, phone = AppConfig.demoAdminPhone,
                    viaDemoAccess = true)
                toast = Toast(text = "دخلت لوحة الإدارة — حساب عرض تجريبي", kind = Toast.Kind.success)
            }
        }
        challenge = null
        Feedback.success()
        save()
    }

    fun signOut() {
        val did = session?.driverID
        if (did != null) {
            val i = drivers.indexOfFirst { it.id == did }
            if (i >= 0) drivers = drivers.mapAt(i) { it.copy(presence = DriverPresence.offline) }
        }
        session = null
        challenge = null
        chats = emptyList(); chatSince = 0.0; chatPrimed = false
        supportTickets = emptyMap()
        runCatching { Boot.app.getSharedPreferences("jaynak.chat", 0).edit().clear().apply() }

        AppConfig.sessionToken = null
        Feedback.impact(ImpactStyle.light)
        save()
    }

    val isGuest: Boolean get() = session == null

    fun requestLogin(role: UserRole? = null, reason: String = "") {
        Feedback.impact(ImpactStyle.light)
        authRequest = AuthRequest(role = role, reason = reason)
    }

    val currentCustomer: Customer?
        get() {
            val id = session?.customerID ?: return null
            return customers.firstOrNull { it.id == id }
        }
    val currentDriver: Driver?
        get() {
            val id = session?.driverID ?: return null
            return drivers.firstOrNull { it.id == id }
        }

    fun updateCustomer(c: Customer) {
        val i = customers.indexOfFirst { it.id == c.id }
        if (i < 0) return
        customers = customers.mapAt(i) { c }; save()
    }

    fun updateDriver(d: Driver) {
        val i = drivers.indexOfFirst { it.id == d.id }
        if (i < 0) return
        drivers = drivers.mapAt(i) { d }; save()
    }

    fun updateOrder(o: Order) {
        val i = orders.indexOfFirst { it.id == o.id }
        if (i < 0) return
        orders = orders.mapAt(i) { o }; save()
    }

    // ───────── التسعير والطلبات ─────────

    fun promo(raw: String, customerID: Id?): PromoCode? {
        val code = raw.uppercase().trim()
        val p = promos.firstOrNull { it.code == code && it.isActive } ?: return null
        if (p.newCustomersOnly && customerID != null) {
            val c = customers.firstOrNull { it.id == customerID }
            if (c != null) {
                val hasHistory = orders.any {
                    it.customerID == customerID && it.status == OrderStatus.delivered
                }
                if (hasHistory || c.usedPromoCodes.contains(code)) return null
            }
        }
        return p
    }

    fun quote(
        pickup: Place, dropoff: Place, kind: OrderKind,
        promoText: String = "", voucher: Voucher? = null,
        express: Boolean = false,
    ): PriceBreakdown = PricingEngine.quote(
        pickup, dropoff, kind, rules,
        promo = promo(promoText, session?.customerID), voucher = voucher, express = express,
    )

    /** يسحب قواعد الإدارة (الأسعار والحدود) من السيرفر — غير الإدارة فقط. */
    suspend fun pullRules(force: Boolean = false) {
        if (!OrdersService.isAvailable) return
        if (!force && session?.role == UserRole.admin) return
        val j = runCatching { OrdersService.fetchRules() }.getOrNull() ?: return
        fun d(k: String, cur: Double) = if (j.has(k)) j.optDouble(k, cur) else cur
        fun n(k: String, cur: Int) = if (j.has(k)) j.optInt(k, cur) else cur
        val r = rules
        val nr = r.copy(
            baseFare = d("baseFare", r.baseFare), perKm = d("perKm", r.perKm),
            perMinute = d("perMinute", r.perMinute), minimumFare = d("minimumFare", r.minimumFare),
            peakMultiplier = d("peakMultiplier", r.peakMultiplier),
            fixedCommission = d("fixedCommission", r.fixedCommission),
            minCommission = d("minCommission", r.minCommission),
            expressBaseFare = d("expressBaseFare", r.expressBaseFare),
            expressPerKm = d("expressPerKm", r.expressPerKm),
            expressPerMinute = d("expressPerMinute", r.expressPerMinute),
            expressCommissionPerRial = d("expressCommissionPerRial", r.expressCommissionPerRial),
            maxCustomerOrders = n("maxCustomerOrders", r.maxCustomerOrders),
            maxDriverOpen = n("maxDriverOpen", r.maxDriverOpen),
            maxDriverExpress = n("maxDriverExpress", r.maxDriverExpress),
        )
        if (nr != r) { rules = nr; save() }
    }

    /** الإدارة: يرفع القواعد الحالية للسيرفر ليعتمدها الجميع. */
    fun pushRules() {
        if (!OrdersService.isAvailable) return
        val r = rules
        val b = org.json.JSONObject()
            .put("baseFare", r.baseFare).put("perKm", r.perKm).put("perMinute", r.perMinute)
            .put("minimumFare", r.minimumFare).put("peakMultiplier", r.peakMultiplier)
            .put("fixedCommission", r.fixedCommission).put("minCommission", r.minCommission)
            .put("expressBaseFare", r.expressBaseFare).put("expressPerKm", r.expressPerKm)
            .put("expressPerMinute", r.expressPerMinute).put("expressCommissionPerRial", r.expressCommissionPerRial)
            .put("maxCustomerOrders", r.maxCustomerOrders).put("maxDriverOpen", r.maxDriverOpen)
            .put("maxDriverExpress", r.maxDriverExpress)
        scope.launch {
            try { OrdersService.saveRules(b) } catch (e: Exception) {
                toast = Toast(text = "تعذّر حفظ الأسعار في السيرفر — " + (e.message ?: ""), kind = Toast.Kind.error)
            }
        }
    }

    val liveOrderForCurrentCustomer: Order?
        get() {
            val id = session?.customerID ?: return null
            return orders.firstOrNull { it.customerID == id && it.status.isLive }
        }

    fun createOrder(
        kind: OrderKind, pickup: Place, dropoff: Place,
        payment: PaymentMethod, note: String,
        recipientName: String, recipientPhone: String,
        promoText: String, voucher: Voucher? = null,
        dispatchMode: DispatchMode = DispatchMode.nearby,
    ): Order? {
        val cid = session?.customerID ?: return null
        val ci = customers.indexOfFirst { it.id == cid }
        if (ci < 0) return null

        val liveCount = orders.count { it.customerID == cid && it.status.isLive }
        if (liveCount >= rules.maxCustomerOrders) {
            toast = Toast(text = "وصلت للحد الأقصى من الطلبات الجارية (${rules.maxCustomerOrders}) — انتظر اكتمال أحدها",
                kind = Toast.Kind.error)
            Feedback.warning()
            return null
        }

        val price = quote(pickup, dropoff, kind, promoText, voucher, express = dispatchMode == DispatchMode.nearby)

        if (payment == PaymentMethod.wallet && customers[ci].walletBalance < price.total) {
            toast = Toast(text = "رصيد المحفظة لا يكفي — عبّئ المحفظة أو اختر الكاش",
                kind = Toast.Kind.error)
            Feedback.warning()
            return null
        }

        var order = Order(
            ref = nextRef(), customerID = cid, kind = kind,
            dispatchMode = dispatchMode,
            pickup = pickup, dropoff = dropoff, note = note,
            recipientName = recipientName, recipientPhone = recipientPhone,
            payment = payment, price = price,
        )

        if (dispatchMode == DispatchMode.nearby) {
            val cands = Dispatch.candidates(order, drivers, rules.minEarningsBalance)
            val offered = mutableMapOf<String, Long>()
            for (d in cands) {
                offered[d.id] = nowMs()
                val i = drivers.indexOfFirst { it.id == d.id }
                if (i >= 0) drivers = drivers.mapAt(i) { it.copy(offersReceived = it.offersReceived + 1) }
            }
            order = order.copy(offeredTo = cands.map { it.id }, offeredAt = offered)
            notifyDriverOfOffer(order, cands)
        }

        orders = listOf(order) + orders
        progress[order.id] = 0.0

        if (voucher != null) {
            val vi = vouchers.indexOfFirst { it.id == voucher.id }
            if (vi >= 0) vouchers = vouchers.mapAt(vi) { it.copy(usedOrderRef = order.ref) }
        }
        val code = price.promoCode
        if (code != null) {
            val pi = promos.indexOfFirst { it.code == code }
            if (pi >= 0) {
                promos = promos.mapAt(pi) { it.copy(timesUsed = it.timesUsed + 1) }
                customers = customers.mapAt(ci) { it.copy(usedPromoCodes = it.usedPromoCodes + code) }
            }
        }
        Feedback.success()
        save()

        // يرفع الطلب للخادم ليراه المندوبون على أجهزتهم الأخرى.
        publishToServer(order)

        return order
    }

    /** يرسل الطلب للخادم ويحفظ معرّفه البعيد عند النجاح. */
    private fun publishToServer(order: Order) {
        if (!OrdersService.isAvailable) {
            toast = Toast(
                text = "الطلب لم يُرسل للسيرفر — حسابك غير مربوط به (" +
                    (AppConfig.lastAuthError ?: "لا جلسة") + "). سجّل خروج ثم دخول",
                kind = Toast.Kind.error)
            return
        }
        val id = order.id
        scope.launch {
            try {
                val remote = OrdersService.create(
                    kind = order.kind.name,
                    pickup = point(order.pickup),
                    dropoff = point(order.dropoff),
                    payment = order.payment.name,
                    note = order.note,
                    recipientPhone = order.recipientPhone,
                    recipientName = order.recipientName,
                    dispatchMode = if (order.dispatchMode == DispatchMode.open) "open" else "nearby",
                )
                val i = orders.indexOfFirst { it.id == id }
                if (i >= 0) {
                    // السعر المعتمد هو سعر الإدارة (السيرفر) — يوحَّد عند الزبون والمندوب
                    val rp = remote.price
                    orders = orders.mapAt(i) {
                        it.copy(remoteID = remote.id,
                            price = if (rp.total > 0.0) it.price.copy(
                                total = rp.total, km = rp.km, minutes = rp.minutes,
                                base = rp.base, distance = rp.distance, time = rp.time,
                                commission = rp.commission, driverPayout = rp.driverPayout,
                                zoneSurcharge = 0.0, kindAdjustment = 0.0, surge = 0.0, discount = 0.0)
                            else it.price)
                    }
                    save()
                }
            } catch (e: OrdersService.Failure.Server) {
                // السيرفر رفضه (مثلاً تجاوز حد الطلبات) — لا نُبقيه محلياً
                orders = orders.filterNot { it.id == id }
                save()
                toast = Toast(text = e.message ?: "رفض السيرفر الطلب", kind = Toast.Kind.error)
            } catch (e: Exception) {
                toast = Toast(
                    text = "الطلب أُنشئ محلياً، لكن لم يصل للسيرفر — " + (e.message ?: ""),
                    kind = Toast.Kind.error,
                )
            }
        }
    }

    // ───────── مزامنة المستخدمين مع الخادم ─────────

    private var profileKey: String? = null

    /** يسجّل حساب الزبون/المندوب الحالي عند السيرفر (مرة لكل تغيّر). */
    suspend fun syncProfile() {
        if (!OrdersService.isAvailable) return
        val s = session ?: return
        val tok = AppConfig.sessionToken.hashCode()
        try {
            when (s.role) {
                UserRole.customer -> {
                    val c = customers.firstOrNull { it.id == s.customerID } ?: return
                    val key = "c|$tok|${c.phone}|${c.name}"
                    if (key == profileKey) return
                    DirectorySync.push("customer", c.name, null, null)
                    profileKey = key
                }
                UserRole.driver -> {
                    val d = drivers.firstOrNull { it.id == s.driverID } ?: return
                    val key = "d|$tok|${d.phone}|${d.name}|${d.zone}|${d.docs.civilID}"
                    if (key == profileKey) return
                    DirectorySync.push("driver", d.name, d.zone, d.docs.civilID.digitsOnly)
                    profileKey = key
                }
                else -> {}
            }
        } catch (e: Exception) {
            // يُعاد المحاولة في الدورة القادمة
        }
    }

    /** المالك: يجلب الزبائن والمندوبين من السيرفر ويدمجهم محلياً. */
    suspend fun refreshAdminPeople() {
        if (!OrdersService.isAvailable) return
        if (session?.role != UserRole.admin) return
        try {
            val p = DirectorySync.people()
            fun same(a: String, b: String) = a.digitsOnly.takeLast(8) == b.digitsOnly.takeLast(8)
            var cs = customers
            var ds = drivers
            for (c in p.customers) {
                if (cs.none { same(it.phone, c.phone) }) {
                    cs = cs + Customer(name = c.name, phone = c.phone.digitsOnly.takeLast(8),
                        createdAt = if (c.createdAt > 0) c.createdAt else nowMs())
                }
            }
            for (r in p.drivers) {
                val ap = runCatching { DriverApproval.valueOf(r.approval) }.getOrDefault(DriverApproval.pending)
                val pr = runCatching { DriverPresence.valueOf(r.presence) }.getOrDefault(DriverPresence.offline)
                if (ds.none { same(it.phone, r.phone) }) {
                    ds = ds + Driver(
                        name = r.name, phone = r.phone.digitsOnly.takeLast(8),
                        zone = if (r.zone.isNotEmpty()) r.zone else Oman.defaultWilaya,
                        docs = DriverDocuments(civilID = r.civilID),
                        approval = ap, presence = pr, coord = Geo.cityCenter,
                        earningsBalance = r.balance, completedOrders = r.completed,
                        createdAt = if (r.createdAt > 0) r.createdAt else nowMs(),
                    )
                } else {
                    ds = ds.map {
                        if (same(it.phone, r.phone)) it.copy(approval = ap, presence = pr) else it
                    }
                }
            }
            if (cs != customers || ds != drivers) {
                customers = cs
                drivers = ds
                save()
            }
        } catch (e: Exception) {
            // ننتظر الدورة التالية
        }
        refreshChats()
        refreshAdminOrders()
    }

    /** المالك: يجلب كل الطلبات (حيّة ومكتملة وملغية) من السيرفر ويدمجها محلياً. */
    suspend fun refreshAdminOrders() {
        if (!OrdersService.isAvailable) return
        if (session?.role != UserRole.admin) return
        try {
            val j = OrdersService.send("api/admin/orders", "GET", null)
            val arr = j.optJSONArray("orders") ?: return
            fun l8(x: String) = x.digitsOnly.takeLast(8)
            fun pl(o: org.json.JSONObject?): Place =
                Place(name = (o?.optString("name") ?: "").ifEmpty { o?.optString("zone") ?: "" },
                    zone = o?.optString("zone") ?: "", coord = Coord(o?.optDouble("lat", 0.0) ?: 0.0, o?.optDouble("lon", 0.0) ?: 0.0))
            var list = orders
            for (n in 0 until arr.length()) {
                val r = arr.getJSONObject(n)
                val rid = r.optString("id")
                if (rid.isEmpty()) continue
                val st = serverStatus(r.optString("status"))
                val dph = r.optString("driverPhone")
                val did = if (dph.isEmpty()) null else drivers.firstOrNull { l8(it.phone) == l8(dph) }?.id
                val cph = r.optString("customerPhone")
                val cid = customers.firstOrNull { l8(it.phone) == l8(cph) }?.id
                val pr = r.optJSONObject("price")
                val price = PriceBreakdown(
                    km = pr?.optDouble("km", 0.0) ?: 0.0, minutes = pr?.optInt("minutes", 0) ?: 0,
                    base = pr?.optDouble("base", 0.0) ?: 0.0, distance = pr?.optDouble("distance", 0.0) ?: 0.0,
                    time = pr?.optDouble("time", 0.0) ?: 0.0, total = pr?.optDouble("total", 0.0) ?: 0.0,
                    commission = pr?.optDouble("commission", 0.0) ?: 0.0,
                    driverPayout = pr?.optDouble("driverPayout", 0.0) ?: 0.0)
                val created = (r.optDouble("createdAt", 0.0) * 1000).toLong()
                val delivered = if (r.isNull("deliveredAt")) null else (r.optDouble("deliveredAt", 0.0) * 1000).toLong()
                val ex = list.indexOfFirst { it.remoteID == rid }
                if (ex >= 0) {
                    list = list.mapAt(ex) {
                        it.copy(status = st, driverID = did ?: it.driverID, price = price,
                            deliveredAt = delivered ?: it.deliveredAt,
                            cancelledReason = if (st == OrderStatus.cancelled && it.cancelledReason == null) "أُلغي" else it.cancelledReason)
                    }
                } else {
                    list = list + Order(
                        ref = r.optString("ref").ifEmpty { "—" },
                        remoteID = rid,
                        customerID = cid ?: newId(),
                        driverID = did,
                        kind = runCatching { OrderKind.valueOf(r.optString("kind")) }.getOrDefault(OrderKind.errand),
                        pickup = pl(r.optJSONObject("pickup")), dropoff = pl(r.optJSONObject("dropoff")),
                        note = r.optString("note"), recipientPhone = r.optString("recipientPhone"),
                        payment = runCatching { PaymentMethod.valueOf(r.optString("payment")) }.getOrDefault(PaymentMethod.cash),
                        price = price, status = st,
                        createdAt = if (created > 0) created else nowMs(),
                        deliveredAt = delivered,
                        cancelledReason = if (st == OrderStatus.cancelled) "أُلغي" else null,
                    )
                }
            }
            val sorted = list.sortedByDescending { it.createdAt }
            if (sorted != orders) { orders = sorted; save() }
        } catch (e: Exception) {
            // الدورة القادمة
        }
    }

    // ───────── تغذية المندوب من الخادم ─────────

    /** يجلب العروض التي خصّصها الخادم لهذا المندوب ويدمجها في القائمة المحلية. */
    suspend fun refreshDriverFeed() {
        if (!OrdersService.isAvailable) return
        val did = session?.driverID ?: return
        syncProfile()
        pullRules()
        refreshChats()
        try {
            // اعتماد الإدارة يصل من السيرفر
            DirectorySync.myApproval()?.let { a ->
                val ap = runCatching { DriverApproval.valueOf(a) }.getOrNull()
                val di = drivers.indexOfFirst { it.id == did }
                if (ap != null && di >= 0 && drivers[di].approval != ap) {
                    drivers = drivers.mapAt(di) {
                        it.copy(approval = ap,
                            presence = if (ap != DriverApproval.approved) DriverPresence.offline else it.presence)
                    }
                    save()
                }
            }
            // الرصيد الحقيقي من السيرفر (هو المرجع)
            DirectorySync.myDriver()?.let { dj ->
                val di = drivers.indexOfFirst { it.id == did }
                if (di >= 0 && dj.has("earningsBalance")) {
                    val bal = dj.optDouble("earningsBalance", drivers[di].earningsBalance)
                    val cnt = dj.optInt("completedOrders", drivers[di].completedOrders)
                    if (bal != drivers[di].earningsBalance || cnt != drivers[di].completedOrders) {
                        drivers = drivers.mapAt(di) { it.copy(earningsBalance = bal, completedOrders = cnt) }
                        save()
                    }
                }
                // حساب أنشأته الإدارة: يدخل مباشرة دون رفع مستندات
                if (di >= 0 && dj.optBoolean("adminCreated", false) && !drivers[di].docs.adminVerified) {
                    drivers = drivers.mapAt(di) { it.copy(docs = it.docs.copy(adminVerified = true)) }
                    save()
                }
            }
            val feed = OrdersService.feed()
            drivers.firstOrNull { it.id == did }?.let {
                com.jaynak.app.driver.DriverLocationService.ensure(Boot.app, it.presence != DriverPresence.offline)
            }
            for (remote in feed.offers) merge(remote, did)
            for (a in feed.actives) merge(a, did)
            for (remote in feed.board) merge(remote, did, asBoard = true)
            // طلبات لم تعد في التغذية (استلمها غيره أو أُلغيت) تُزال
            val live = (feed.offers + feed.board + feed.actives).map { it.id }.toSet()
            val activeIds = feed.actives.map { it.id }.toSet()
            // طلب لم يعد ضمن نطاق المندوب يُسحب منه التنبيه (يبقى في القائمة المفتوحة فقط)
            val offerIds = feed.offers.map { it.id }.toSet()
            val myCust = session?.customerID
            orders = orders.map {
                if (it.remoteID != null && it.status == OrderStatus.searching && it.customerID != myCust &&
                    it.offeredTo.contains(did) && it.remoteID !in offerIds)
                    it.copy(offeredTo = it.offeredTo.filter { x -> x != did })
                else it
            }
            var released = false
            orders = orders.map {
                if (it.driverID == did && it.remoteID != null && it.status.isLive && it.remoteID !in activeIds &&
                    nowMs() - (it.acceptedAt ?: 0L) > 20000) {
                    released = true
                    it.copy(status = OrderStatus.cancelled, cancelledReason = "أُلغي")
                } else it
            }
            if (released) {
                val di2 = drivers.indexOfFirst { it.id == did }
                if (di2 >= 0 && drivers[di2].presence == DriverPresence.busy)
                    drivers = drivers.mapAt(di2) { it.copy(presence = DriverPresence.online) }
            }
            val myCustomer = session?.customerID
            orders = orders.filterNot {
                it.remoteID != null && it.status == OrderStatus.searching && it.driverID == null &&
                    it.customerID != myCustomer && it.remoteID !in live
            }
            save()
        } catch (e: OrdersService.Failure) {
            if (e is OrdersService.Failure.Unauthorized) {
                toast = Toast(text = "انتهت الجلسة — سجّل الدخول مرة ثانية", kind = Toast.Kind.error)
            }
        } catch (e: Exception) {
            // أخطاء الشبكة العابرة تُتجاهل — التحديث القادم يعوّض.
        }
    }

    /** حالات الخادم (accepted…) إلى حالات التطبيق. */
    private fun serverStatus(s: String): OrderStatus = when (s) {
        "accepted" -> OrderStatus.pickingUp
        else -> runCatching { OrderStatus.valueOf(s) }.getOrDefault(OrderStatus.searching)
    }

    /** الزبون: يجلب حالة طلباته من السيرفر (قبول، استلام، تسليم، إلغاء). */
    suspend fun refreshMyOrders() {
        if (!OrdersService.isAvailable) return
        val cid = session?.customerID ?: return
        pullRules()
        refreshChats()
        try {
            val mine = OrdersService.mine()
            var changed = false
            for (r in mine) {
                val i = orders.indexOfFirst { it.remoteID == r.id && it.customerID == cid }
                if (i < 0) continue
                val cur = orders[i]
                val st = serverStatus(r.status)
                val newStatus = when {
                    st == cur.status -> cur.status
                    // حالات التوصيل المحلية الدقيقة لا تُرجَع للخلف
                    st == OrderStatus.pickedUp && cur.status == OrderStatus.delivering -> cur.status
                    st == OrderStatus.pickingUp && cur.status == OrderStatus.assigned -> cur.status
                    !cur.status.isLive -> cur.status
                    else -> st
                }
                val newDriver = if (st == OrderStatus.searching || st == OrderStatus.cancelled) null else (r.driverID ?: cur.driverID)
                if (newStatus != cur.status || newDriver != cur.driverID) {
                    orders = orders.mapAt(i) {
                        it.copy(
                            status = newStatus, driverID = newDriver,
                            acceptedAt = if (newStatus == OrderStatus.pickingUp && it.acceptedAt == null) nowMs() else it.acceptedAt,
                            deliveredAt = if (newStatus == OrderStatus.delivered) (it.deliveredAt ?: nowMs()) else it.deliveredAt,
                            cancelledReason = if (newStatus == OrderStatus.cancelled && it.cancelledReason == null) "أُلغي" else it.cancelledReason,
                        )
                    }
                    changed = true
                }
            }
            if (changed) save()
        } catch (e: Exception) {
            // الدورة القادمة
        }
    }

    /** يحوّل طلب الخادم إلى النموذج المحلي، أو يحدّث نسخته إن كانت موجودة. */
    private fun merge(remote: OrdersService.RemoteOrder, did: Id, asBoard: Boolean = false) {
        val status = serverStatus(remote.status)

        val existing = orders.indexOfFirst { it.remoteID == remote.id }
        if (existing >= 0) {
            orders = orders.mapAt(existing) {
                fun rank(s: OrderStatus) = when (s) {
                    OrderStatus.searching -> 0
                    OrderStatus.assigned, OrderStatus.pickingUp -> 1
                    OrderStatus.pickedUp -> 2
                    OrderStatus.delivering -> 3
                    else -> 9
                }
                // السيرفر قد يتأخر لحظة عن ضغطة المندوب — لا نُرجع الحالة للخلف أبداً
                val keepLocal = (it.status.isLive && status.isLive && it.driverID == did && rank(it.status) > rank(status)) ||
                    (it.status == OrderStatus.delivered && status != OrderStatus.cancelled)
                it.copy(
                    status = if (keepLocal) it.status else status,
                    driverID = if (remote.driverID != null && status.isLive) did else it.driverID,
                    offeredTo = if (asBoard || it.offeredTo.contains(did)) it.offeredTo else it.offeredTo + did,
                )
            }
            return
        }

        val price = PriceBreakdown(
            km = remote.price.km,
            minutes = remote.price.minutes,
            base = remote.price.base,
            distance = remote.price.distance,
            time = remote.price.time,
            total = remote.price.total,
            commission = remote.price.commission,
            driverPayout = remote.price.driverPayout,
        )

        var order = Order(
            ref = if (remote.ref.isEmpty()) "—" else remote.ref,
            customerID = newId(),
            kind = runCatching { OrderKind.valueOf(remote.kind) }.getOrDefault(OrderKind.errand),
            pickup = place(remote.pickup),
            dropoff = place(remote.dropoff),
            note = remote.note,
            payment = runCatching { PaymentMethod.valueOf(remote.payment) }.getOrDefault(PaymentMethod.cash),
            price = price,
            remoteID = remote.id,
            dispatchMode = if (remote.dispatchMode == "open") DispatchMode.open else DispatchMode.nearby,
            status = status,
            offeredTo = listOf(did),
            offeredAt = mapOf(did to nowMs()),
        )
        if (remote.createdAt > 0) order = order.copy(createdAt = (remote.createdAt * 1000).toLong())
        if (asBoard) order = order.copy(dispatchMode = DispatchMode.open, offeredTo = emptyList(), offeredAt = emptyMap())
        if (remote.driverID != null && status.isLive) order = order.copy(driverID = did, acceptedAt = nowMs())
        orders = listOf(order) + orders
    }

    companion object {
        fun point(place: Place): OrdersService.Point =
            OrdersService.Point(lat = place.coord.lat, lon = place.coord.lon,
                zone = place.zone, name = place.name)

        fun place(p: OrdersService.Point): Place =
            Place(name = if (p.name.isEmpty()) p.zone else p.name,
                zone = p.zone, coord = Coord(p.lat, p.lon))
    }

    private fun notifyDriverOfOffer(order: Order, candidates: List<Driver>) {
        val did = session?.driverID ?: return
        if (candidates.none { it.id == did }) return
        Notifications.newOffer(order.ref, order.pickup.name, order.dropoff.name,
            order.price.driverPayout)
    }

    fun cancelOrder(order: Order, reason: String, by: UserRole) {
        val i = orders.indexOfFirst { it.id == order.id }
        if (i < 0) return
        if (!orders[i].status.isLive) return
        orders = orders.mapAt(i) { it.copy(status = OrderStatus.cancelled, cancelledReason = reason) }
        val did = orders[i].driverID
        if (did != null) {
            val di = drivers.indexOfFirst { it.id == did }
            if (di >= 0) drivers = drivers.mapAt(di) { it.copy(presence = DriverPresence.online) }
        }
        progress.remove(order.id)
        toast = Toast(text = "أُلغي الطلب ${orders[i].ref}", kind = Toast.Kind.error)
        Feedback.warning()
        save()

        val remote = orders[i].remoteID
        if (remote != null && OrdersService.isAvailable) {
            val oid = order.id
            val prev = order.status
            scope.launch {
                try {
                    when (by) {
                        UserRole.driver -> OrdersService.driverCancel(remote)
                        UserRole.admin -> OrdersService.send("api/admin/cancel", "POST", org.json.JSONObject().put("orderID", remote))
                        else -> OrdersService.cancel(remote)
                    }
                } catch (e: Exception) {
                    val j = orders.indexOfFirst { it.id == oid }
                    if (j >= 0) orders = orders.mapAt(j) { it.copy(status = prev, cancelledReason = null) }
                    toast = Toast(text = e.message ?: "تعذّر إلغاء الطلب", kind = Toast.Kind.error)
                    save()
                }
            }
        }
    }

    fun rate(order: Order, stars: Int, comment: String, by: UserRole) {
        val i = orders.indexOfFirst { it.id == order.id }
        if (i < 0) return
        if (by == UserRole.customer) {
            orders = orders.mapAt(i) {
                it.copy(customerRating = stars,
                    ratingComment = if (comment.isEmpty()) null else comment)
            }
            val did = orders[i].driverID
            if (did != null) {
                val di = drivers.indexOfFirst { it.id == did }
                if (di >= 0) {
                    drivers = drivers.mapAt(di) {
                        it.copy(ratingSum = it.ratingSum + stars, ratingCount = it.ratingCount + 1)
                    }
                    awardTierPoints(Loyalty.tierPoints(stars), di, "تقييم")
                }
            }
        } else {
            orders = orders.mapAt(i) { it.copy(driverRating = stars) }
        }
        Feedback.success()
        toast = Toast(text = "شكراً على تقييمك", kind = Toast.Kind.success)
        save()
    }

    // ───────── المحفظة ─────────

    fun topUp(amount: Double) {
        val cid = session?.customerID ?: return
        val i = customers.indexOfFirst { it.id == cid }
        if (i < 0) return
        customers = customers.mapAt(i) { it.copy(walletBalance = it.walletBalance + amount) }
        transactions = listOf(WalletTxn(ownerID = cid, kind = WalletTxnKind.topUp, amount = amount)) + transactions
        Feedback.success()
        toast = Toast(text = "تمت إضافة ${amount.money} للمحفظة", kind = Toast.Kind.success)
        save()
    }

    fun applyWalletTopUpConfirmation(balance: Double?, amount: Double) {
        val cid = session?.customerID ?: return
        val i = customers.indexOfFirst { it.id == cid }
        if (i < 0) return
        if (balance != null) customers = customers.mapAt(i) { it.copy(walletBalance = balance) }
        transactions = listOf(WalletTxn(ownerID = cid, kind = WalletTxnKind.topUp, amount = amount)) + transactions
        Feedback.success()
        toast = Toast(text = "تمت إضافة ${amount.money} للمحفظة", kind = Toast.Kind.success)
        save()
    }

    // ───────── منح الإدارة اليدوي ─────────

    /** يمنح الزبون نقاطاً. [affectsLevel] يرفع رتبته أيضاً، وإلا كانت للصرف فقط. */
    fun adminGrantPoints(amount: Int, customerID: Id, affectsLevel: Boolean = true) {
        if (amount == 0) return
        val i = customers.indexOfFirst { it.id == customerID }
        if (i < 0) return

        customers = customers.mapAt(i) {
            var c = it.copy(points = max(0, it.points + amount))
            if (affectsLevel) c = c.copy(lifetimePoints = max(0, it.lifetimePoints + amount))
            c
        }

        val verb = if (amount > 0) "أُضيفت" else "خُصمت"
        toast = Toast(text = "$verb ${abs(amount)} نقطة لـ${customers[i].name}",
            kind = Toast.Kind.success)
        Feedback.success()
        save()
    }

    /** يضبط رتبة المندوب مباشرة. */
    fun adminSetDriverTier(tier: DriverTier, id: Id) {
        val i = drivers.indexOfFirst { it.id == id }
        if (i < 0) return

        val floor = tier.minPoints
        if (drivers[i].tierPoints < floor) {
            drivers = drivers.mapAt(i) { it.copy(tierPoints = floor) }
        } else if (drivers[i].tier.raw > tier.raw) {
            drivers = drivers.mapAt(i) { it.copy(tierPoints = floor) }
        }

        toast = Toast(text = "رتبة ${drivers[i].name}: ${tier.title}", kind = Toast.Kind.success)
        Feedback.success()
        save()
    }

    /** يمنح المندوب نقاط رتبة بلا قفز مباشر لرتبة بعينها. */
    fun adminGrantTierPoints(amount: Int, driverID: Id) {
        if (amount == 0) return
        val i = drivers.indexOfFirst { it.id == driverID }
        if (i < 0) return

        val before = drivers[i].tier
        drivers = drivers.mapAt(i) { it.copy(tierPoints = max(0, it.tierPoints + amount)) }
        val after = drivers[i].tier

        val verb = if (amount > 0) "أُضيفت" else "خُصمت"
        var text = "$verb ${abs(amount)} نقطة لـ${drivers[i].name}"
        if (before != after) text += " · صار ${after.title}"
        toast = Toast(text = text, kind = Toast.Kind.success)
        Feedback.success()
        save()
    }

    private fun grantWelcomePromoCredit(customerID: Id) {
        val i = customers.indexOfFirst { it.id == customerID }
        if (i < 0) return
        customers = customers.mapAt(i) { it.copy(walletBalance = it.walletBalance + 1.000) }
        transactions = listOf(WalletTxn(ownerID = customerID, kind = WalletTxnKind.promo, amount = 1.000)) + transactions
    }

    // ───────── رصيد المندوب ─────────

    val isDriverBlockedByBalance: Boolean
        get() {
            val d = currentDriver ?: return false
            return d.earningsBalance < rules.minEarningsBalance
        }

    val isDriverNearBalanceLimit: Boolean
        get() {
            val d = currentDriver ?: return false
            return d.earningsBalance < rules.earningsWarnBalance &&
                d.earningsBalance >= rules.minEarningsBalance
        }

    fun settleDriverBalance(amount: Double) {
        val did = session?.driverID ?: return
        val i = drivers.indexOfFirst { it.id == did }
        if (i < 0) return
        if (amount <= 0) return
        drivers = drivers.mapAt(i) { it.copy(earningsBalance = it.earningsBalance + amount) }
        transactions = listOf(WalletTxn(ownerID = did, kind = WalletTxnKind.topUp, amount = amount)) + transactions
        Feedback.success()
        toast = Toast(
            text = if (drivers[i].earningsBalance >= rules.minEarningsBalance)
                "سُدّد المبلغ — يمكنك الاتصال الآن"
            else
                "سُدّد ${amount.money} — ما زال رصيدك تحت الحدّ",
            kind = Toast.Kind.success,
        )
        save()
    }

    /** تاريخ الدفعة القادمة (بداية اليوم) أو null. */
    fun nextPayoutDate(from: Long = nowMs()): Long? {
        val days = rules.payoutWeekdays.sorted()
        if (days.isEmpty()) return null
        for (offset in 0..7) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = from
                add(Calendar.DAY_OF_YEAR, offset)
            }
            if (days.contains(cal.get(Calendar.DAY_OF_WEEK))) {
                if (offset == 0) continue
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                return cal.timeInMillis
            }
        }
        return null
    }

    fun withdrawEarnings(amount: Double) {
        val did = session?.driverID ?: return
        val i = drivers.indexOfFirst { it.id == did }
        if (i < 0) return
        if (!(amount > 0 && amount <= drivers[i].earningsBalance)) {
            toast = Toast(text = "المبلغ أكبر من الرصيد القابل للسحب", kind = Toast.Kind.error)
            return
        }
        drivers = drivers.mapAt(i) { it.copy(earningsBalance = it.earningsBalance - amount) }
        transactions = listOf(WalletTxn(ownerID = did, kind = WalletTxnKind.payout, amount = -amount)) + transactions
        Feedback.success()
        toast = Toast(text = "طلب سحب ${amount.money} قيد التنفيذ", kind = Toast.Kind.success)
        save()
    }

    fun txns(ownerID: Id): List<WalletTxn> =
        transactions.filter { it.ownerID == ownerID }.sortedByDescending { it.date }

    // ───────── المندوب ─────────

    val pendingOfferForCurrentDriver: Order?
        get() {
            val did = session?.driverID ?: return null
            val myCustomer = session?.customerID
            // التنبيه فقط للطلبات التي اختارها السيرفر لهذا المندوب (ضمن نطاقه وقرب الاستلام)
            return orders.firstOrNull {
                it.status == OrderStatus.searching && !it.declinedBy.contains(did) &&
                    it.offeredTo.contains(did) && it.customerID != myCustomer
            }
        }

    val activeOrdersForCurrentDriver: List<Order>
        get() {
            val did = session?.driverID ?: return emptyList()
            return orders.filter { it.driverID == did && it.status.isLive }
                .sortedBy { it.acceptedAt ?: it.createdAt }
        }

    val activeOrderForCurrentDriver: Order?
        get() {
            val did = session?.driverID ?: return null
            return orders.firstOrNull { it.driverID == did && it.status.isLive }
        }

    fun setPresence(p: DriverPresence) {
        val did = session?.driverID ?: return
        val i = drivers.indexOfFirst { it.id == did }
        if (i < 0) return

        // مشاركة الموقع الحقيقي مع الزبون والإدارة أثناء الاتصال
        if (p != DriverPresence.offline && !com.jaynak.app.driver.DriverLocationService.hasPermission(Boot.app)) {
            LocationService.locate()   // يطلب الإذن
        }
        com.jaynak.app.driver.DriverLocationService.ensure(Boot.app, p != DriverPresence.offline)

        // الخادم يوزّع على المتصلين فقط — لا بد أن يعرف حالتنا.
        if (OrdersService.isAvailable) {
            val online = p != DriverPresence.offline
            scope.launch {
                try { OrdersService.setPresence(online) }
                catch (e: Exception) {
                    toast = Toast(text = "تعذّر إبلاغ السيرفر باتصالك — " + (e.message ?: ""), kind = Toast.Kind.error)
                }
            }
        } else if (p != DriverPresence.offline) {
            toast = Toast(text = "حسابك غير مربوط بالسيرفر (" + (AppConfig.lastAuthError ?: "لا جلسة") +
                ") — لن تصلك طلبات. سجّل خروج ثم دخول", kind = Toast.Kind.error)
        }

        if (p != DriverPresence.offline && drivers[i].earningsBalance < rules.minEarningsBalance) {
            toast = Toast(text = "رصيدك ${drivers[i].earningsBalance.money} — عبّئ محفظتك لتستلم طلبات",
                kind = Toast.Kind.error)
            Feedback.warning()
            return
        }
        if (p != DriverPresence.offline && !drivers[i].canGoOnline) {
            toast = Toast(
                text = if (drivers[i].approval == DriverApproval.approved)
                    "أكمل مستندات التسجيل أولاً" else drivers[i].approval.title,
                kind = Toast.Kind.error,
            )
            Feedback.warning()
            return
        }
        drivers = drivers.mapAt(i) { it.copy(presence = p) }
        Feedback.selection()

        if (p == DriverPresence.online) {
            Notifications.requestIfNeeded()
        }
        save()
    }

    val openBoard: List<Dispatch.BoardItem>
        get() = Dispatch.board(currentDriver, orders, rules.minEarningsBalance)

    val acceptedOrders: List<Order>
        get() {
            val did = session?.driverID ?: return emptyList()
            return orders.filter { it.driverID == did && it.status.isLive }
                .sortedByDescending { it.acceptedAt ?: it.createdAt }
        }

    fun acceptOpenOrder(order: Order): Boolean {
        val did = session?.driverID ?: return false
        val d = drivers.firstOrNull { it.id == did } ?: return false
        if (!(d.canGoOnline && d.approval == DriverApproval.approved)) {
            toast = Toast(text = "حسابك غير معتمد بعد", kind = Toast.Kind.error)
            return false
        }
        if (d.earningsBalance < rules.minEarningsBalance) {
            toast = Toast(text = "رصيدك ${d.earningsBalance.money} — عبّئ محفظتك لتستلم طلبات",
                kind = Toast.Kind.error)
            Feedback.warning()
            return false
        }
        val oi = orders.indexOfFirst { it.id == order.id }
        if (oi < 0 || orders[oi].status != OrderStatus.searching || orders[oi].driverID != null) {
            toast = Toast(text = "سبقك مندوب آخر لهذا الطلب", kind = Toast.Kind.error)
            Feedback.warning()
            return false
        }
        val mine = orders.filter { it.driverID == did && it.status.isLive }
        if (orders[oi].dispatchMode == DispatchMode.nearby) {
            if (mine.count { it.dispatchMode == DispatchMode.nearby } >= rules.maxDriverExpress) {
                toast = Toast(text = "وصلت للحد الأقصى من الطلبات السريعة (${rules.maxDriverExpress})", kind = Toast.Kind.error)
                Feedback.warning(); return false
            }
        } else if (mine.count { it.dispatchMode == DispatchMode.open } >= rules.maxDriverOpen) {
            toast = Toast(text = "وصلت للحد الأقصى من الطلبات المفتوحة (${rules.maxDriverOpen})", kind = Toast.Kind.error)
            Feedback.warning(); return false
        }
        val i = drivers.indexOfFirst { it.id == did }
        if (i >= 0) drivers = drivers.mapAt(i) { it.copy(offersReceived = it.offersReceived + 1) }
        val ord = orders[oi]
        assign(ord, did, manual = true)
        confirmAcceptOnServer(ord)
        return true
    }

    /** يثبّت القبول عند السيرفر؛ إن رفضه (سبقه غيره) يُعاد الطلب كما كان. */
    private fun confirmAcceptOnServer(order: Order) {
        val remote = order.remoteID ?: return
        if (!OrdersService.isAvailable) return
        val did = session?.driverID
        scope.launch {
            try {
                OrdersService.accept(remote)
            } catch (e: Exception) {
                val i = orders.indexOfFirst { it.id == order.id }
                if (i >= 0) orders = orders.mapAt(i) {
                    it.copy(status = OrderStatus.searching, driverID = null, acceptedAt = null)
                }
                if (did != null) {
                    val di = drivers.indexOfFirst { it.id == did }
                    if (di >= 0) drivers = drivers.mapAt(di) { it.copy(presence = DriverPresence.online) }
                }
                toast = Toast(text = "تعذّر تثبيت الطلب — " + (e.message ?: ""), kind = Toast.Kind.error)
                save()
            }
        }
    }

    fun setAcceptRadius(km: Double) {
        val did = session?.driverID ?: return
        val i = drivers.indexOfFirst { it.id == did }
        if (i < 0) return
        drivers = drivers.mapAt(i) { it.copy(acceptRadiusKm = min(max(1.0, km), Dispatch.maxRadiusKm)) }
        Feedback.selection()
        save()
    }

    fun setDestination(place: Place?) {
        val did = session?.driverID ?: return
        val i = drivers.indexOfFirst { it.id == did }
        if (i < 0) return
        drivers = drivers.mapAt(i) { it.copy(destination = place) }
        toast = if (place != null) {
            Toast(text = "وضع الوجهة: ${place.name} — تصلك الطلبات على مسارك", kind = Toast.Kind.success)
        } else {
            Toast(text = "أُلغي وضع الوجهة")
        }
        Feedback.success()
        save()
    }

    fun acceptOffer(order: Order) {
        val did = session?.driverID ?: return
        Notifications.clearOffer(order.ref)
        assign(order, did, manual = true)

        // طلب جاء من الخادم — نُعلمه بالقبول وإلا بقي معروضاً للبقية.
        confirmAcceptOnServer(order)
    }

    fun declineOffer(order: Order) {
        val did = session?.driverID ?: return
        val i = orders.indexOfFirst { it.id == order.id }
        if (i < 0) return
        orders = orders.mapAt(i) {
            it.copy(declinedBy = it.declinedBy + did, offeredTo = it.offeredTo.filter { x -> x != did })
        }
        Notifications.clearOffer(orders[i].ref)

        val remote = orders[i].remoteID
        if (remote != null && OrdersService.isAvailable) {
            scope.launch { runCatching { OrdersService.decline(remote) } }
        }

        val next = Dispatch.candidates(orders[i], drivers, rules.minEarningsBalance)
        for (d in next) {
            if (orders[i].offeredTo.contains(d.id)) continue
            orders = orders.mapAt(i) {
                it.copy(offeredTo = it.offeredTo + d.id, offeredAt = it.offeredAt + (d.id to nowMs()))
            }
            val di = drivers.indexOfFirst { it.id == d.id }
            if (di >= 0) drivers = drivers.mapAt(di) { it.copy(offersReceived = it.offersReceived + 1) }
        }
        Feedback.stopOfferAlert()
        Feedback.impact(ImpactStyle.light)
        save()
    }

    private fun assign(order: Order, driverID: Id, manual: Boolean) {
        val oi = orders.indexOfFirst { it.id == order.id }
        if (oi < 0 || orders[oi].status != OrderStatus.searching) return
        val di = drivers.indexOfFirst { it.id == driverID }
        if (di < 0) return

        orders = orders.mapAt(oi) {
            it.copy(driverID = driverID, status = OrderStatus.pickingUp,
                acceptedAt = nowMs(), driverCoord = drivers[di].coord)
        }
        drivers = drivers.mapAt(di) {
            it.copy(offersAccepted = it.offersAccepted + 1)
        }
        routes[orders[oi].id] = Geo.route(drivers[di].coord, orders[oi].pickup.coord)
        progress[orders[oi].id] = 0.0

        if (manual) {
            Feedback.stopOfferAlert()
            Feedback.success()
            toast = Toast(text = "ثُبّت الطلب ${orders[oi].ref} عليك", kind = Toast.Kind.success)
        }
        save()
    }

    private val advancing = mutableSetOf<String>()

    fun advance(order: Order) {
        val i = orders.indexOfFirst { it.id == order.id }
        if (i < 0) return
        if (advancing.contains(order.id)) return      // ضغطة مزدوجة أثناء الإرسال
        val before = orders[i].status
        val expect = when (before) {
            OrderStatus.assigned, OrderStatus.pickingUp -> "accepted"
            OrderStatus.pickedUp -> "pickedUp"
            OrderStatus.delivering -> "delivering"
            else -> null
        }
        val prevOrder = orders[i]
        when (orders[i].status) {
            OrderStatus.assigned, OrderStatus.pickingUp -> {
                orders = orders.mapAt(i) { it.copy(status = OrderStatus.pickedUp) }
                routes[orders[i].id] = Geo.route(orders[i].pickup.coord, orders[i].dropoff.coord)
                progress[orders[i].id] = 0.0
                Feedback.success()
                toast = Toast(text = "تم الاستلام — توجّه للتسليم", kind = Toast.Kind.success)
            }
            OrderStatus.pickedUp -> {
                // «توصيل»: يبدأ التوصيل فقط — لا ينتهي الطلب إلا عند «تسليم»
                orders = orders.mapAt(i) { it.copy(status = OrderStatus.delivering) }
                Feedback.success()
                toast = Toast(text = "في الطريق للعميل — اضغط تسليم عند التسليم", kind = Toast.Kind.success)
            }
            OrderStatus.delivering -> complete(i)
            else -> {}
        }
        save()
        val remote = orders[i].remoteID
        if (remote != null && orders[i].status != before && OrdersService.isAvailable) {
            val oid = order.id
            advancing.add(oid)
            scope.launch {
                try {
                    OrdersService.advance(remote, expect)
                } catch (e: Exception) {
                    val j = orders.indexOfFirst { it.id == oid }
                    if (j >= 0) orders = orders.mapAt(j) { it.copy(status = before) }
                    toast = Toast(text = "تعذّر تحديث الحالة على السيرفر — " + (e.message ?: ""), kind = Toast.Kind.error)
                    save()
                } finally {
                    advancing.remove(oid)
                }
            }
        }
    }

    private fun complete(i: Int) {
        var o = orders[i].copy(
            status = OrderStatus.delivered,
            deliveredAt = nowMs(),
            driverCoord = orders[i].dropoff.coord,
        )

        // نقاط الولاء للزبون
        val ci0 = customers.indexOfFirst { it.id == o.customerID }
        if (ci0 >= 0) {
            val earned = Loyalty.points(o.price.total)
            customers = customers.mapAt(ci0) {
                it.copy(points = it.points + earned, lifetimePoints = it.lifetimePoints + earned)
            }
            o = o.copy(price = o.price.copy(pointsEarned = earned))
        }

        val ci = customers.indexOfFirst { it.id == o.customerID }
        if (ci >= 0) {
            if (o.payment == PaymentMethod.wallet) {
                customers = customers.mapAt(ci) {
                    it.copy(walletBalance = max(0.0, it.walletBalance - o.price.total))
                }
                transactions = listOf(
                    WalletTxn(ownerID = o.customerID, kind = WalletTxnKind.orderPayment,
                        amount = -o.price.total, orderRef = o.ref),
                ) + transactions
            }
        }
        val did = o.driverID
        if (did != null) {
            val di = drivers.indexOfFirst { it.id == did }
            if (di >= 0) {
                drivers = drivers.mapAt(di) {
                    it.copy(completedOrders = it.completedOrders + 1,
                        presence = DriverPresence.online, coord = o.dropoff.coord)
                }
                awardTierPoints(Loyalty.tierPointsPerDelivery, di, "توصيلة")

                val raw = if (o.dispatchMode == DispatchMode.nearby)
                    min(o.price.total, max(rules.minCommission, o.price.total * rules.expressCommissionPerRial))
                else PricingEngine.commission(
                    o.price.total, rules,
                    fee = drivers[di].commissionFee,
                    rateDiscount = drivers[di].tier.commissionDiscount,
                )
                val commission = (raw * 1000).roundedAway() / 1000
                val payout = ((o.price.total - commission) * 1000).roundedAway() / 1000
                o = o.copy(price = o.price.copy(commission = commission, driverPayout = payout))

                when (o.payment) {
                    PaymentMethod.wallet -> {
                        drivers = drivers.mapAt(di) {
                            it.copy(earningsBalance = it.earningsBalance + o.price.driverPayout)
                        }
                        transactions = listOf(
                            WalletTxn(ownerID = did, kind = WalletTxnKind.orderPayment,
                                amount = o.price.driverPayout, orderRef = o.ref),
                        ) + transactions
                    }
                    PaymentMethod.cash -> {
                        drivers = drivers.mapAt(di) {
                            it.copy(earningsBalance = it.earningsBalance - o.price.commission)
                        }
                        transactions = listOf(
                            WalletTxn(ownerID = did, kind = WalletTxnKind.commission,
                                amount = -o.price.commission, orderRef = o.ref),
                        ) + transactions
                    }
                }

                if (drivers[di].earningsBalance < rules.minEarningsBalance) {
                    drivers = drivers.mapAt(di) {
                        it.copy(presence = DriverPresence.offline, destination = null)
                    }
                    toast = Toast(
                        text = "رصيدك ${drivers[di].earningsBalance.money} — عبّئ محفظتك لتعود للعمل",
                        kind = Toast.Kind.error,
                    )
                }
            }
        }
        orders = orders.mapAt(i) { o }
        progress.remove(o.id)
        Feedback.success()
        toast = Toast(text = "تم تسليم ${o.ref}", kind = Toast.Kind.success)
    }

    // ───────── الهوية ─────────

    fun identityByCivil(civilID: String): IdentityRecord? {
        val id = civilID.digitsOnly
        if (id.isEmpty()) return null
        return identities.firstOrNull { it.civilID == id }
    }

    fun identityByPhone(phone: String): IdentityRecord? {
        val p = normalize(phone)
        if (p.isEmpty()) return null
        return identities.firstOrNull { it.phone == p }
    }

    fun identityOfDriver(driverID: Id): IdentityRecord? =
        identities.firstOrNull { it.driverID == driverID }

    fun checkIdentity(rawID: String, rawPhone: String, driverID: Id): IdentityCheck {
        val cid = rawID.digitsOnly
        val phone = normalize(rawPhone)

        val byCivil = identityByCivil(cid)
        if (byCivil != null) {
            if (byCivil.isBarred && byCivil.driverID != driverID) {
                return IdentityCheck.barred(byCivil.barReason ?: "قرار إداري", byCivil.outstandingDebt)
            }
            if (byCivil.driverID != driverID) {
                return IdentityCheck.CivilIDTaken(byCivil.name)
            }
        }
        val byPhone = identityByPhone(phone)
        if (byPhone != null && byPhone.driverID != driverID) {
            return IdentityCheck.PhoneTaken(byPhone.name)
        }

        val own = identityOfDriver(driverID)
        if (own != null && own.isBarred) {
            return IdentityCheck.barred(own.barReason ?: "قرار إداري", own.outstandingDebt)
        }
        return IdentityCheck.Allowed
    }

    private fun bindIdentity(driver: Driver) {
        val cid = driver.docs.civilID.digitsOnly
        if (cid.length < 8) return
        val i = identities.indexOfFirst { it.driverID == driver.id }
        if (i >= 0) {
            identities = identities.mapAt(i) {
                it.copy(civilID = cid, phone = driver.phone, name = driver.name)
            }
        } else {
            identities = identities + IdentityRecord(civilID = cid, phone = driver.phone,
                driverID = driver.id, name = driver.name)
        }
    }

    fun barIdentity(driverID: Id, reason: String) {
        if (!can(AdminPermission.manageDrivers)) {
            toast = Toast(text = "لا تملك صلاحية إيقاف المندوبين", kind = Toast.Kind.error)
            return
        }
        val debt = drivers.firstOrNull { it.id == driverID }
            ?.let { max(0.0, -it.earningsBalance) } ?: 0.0
        val i = identities.indexOfFirst { it.driverID == driverID }
        if (i >= 0) {
            identities = identities.mapAt(i) {
                it.copy(isBarred = true, barReason = reason, barredAt = nowMs(), outstandingDebt = debt)
            }
        } else {
            val d = drivers.firstOrNull { it.id == driverID }
            if (d != null) {
                identities = identities + IdentityRecord(
                    civilID = d.docs.civilID.digitsOnly, phone = d.phone,
                    driverID = d.id, name = d.name,
                    isBarred = true, barReason = reason, barredAt = nowMs(), outstandingDebt = debt,
                )
            }
        }
        val di = drivers.indexOfFirst { it.id == driverID }
        if (di >= 0) {
            drivers = drivers.mapAt(di) {
                it.copy(approval = DriverApproval.rejected, rejectionNote = reason,
                    presence = DriverPresence.offline)
            }
        }
        toast = Toast(
            text = "أُوقفت الهوية نهائياً" + (if (debt > 0) " — دَين ${debt.money}" else ""),
            kind = Toast.Kind.success,
        )
        save()
    }

    fun unbarIdentity(driverID: Id) {
        if (!can(AdminPermission.manageDrivers)) {
            toast = Toast(text = "لا تملك صلاحية هذا الإجراء", kind = Toast.Kind.error)
            return
        }
        val i = identities.indexOfFirst { it.driverID == driverID }
        if (i < 0) return
        identities = identities.mapAt(i) {
            it.copy(isBarred = false, barReason = null, outstandingDebt = 0.0)
        }
        val di = drivers.indexOfFirst { it.id == driverID }
        if (di >= 0) {
            drivers = drivers.mapAt(di) {
                it.copy(approval = DriverApproval.pending, rejectionNote = null)
            }
        }
        toast = Toast(text = "أُلغي إيقاف الهوية", kind = Toast.Kind.success)
        save()
    }

    fun submitDriverDocs(driver: Driver): Boolean {
        val i = drivers.indexOfFirst { it.id == driver.id }
        if (i < 0) return false

        val check = checkIdentity(driver.docs.civilID, driver.phone, driver.id)
        if (!check.isAllowed) {
            authError = check.message
            toast = Toast(text = check.message, kind = Toast.Kind.error)
            Feedback.warning()
            return false
        }

        drivers = drivers.mapAt(i) { driver }
        bindIdentity(drivers[i])
        if (drivers[i].docs.isComplete &&
            (drivers[i].approval == DriverApproval.rejected || drivers[i].approval == DriverApproval.pending)
        ) {
            drivers = drivers.mapAt(i) {
                it.copy(approval = DriverApproval.pending, rejectionNote = null)
            }
        }
        toast = Toast(text = "أُرسلت بياناتك للمراجعة", kind = Toast.Kind.success)
        Feedback.success()
        save()
        return true
    }

    // ───────── فريق الإدارة ─────────

    fun addAdmin(name: String, raw: String, role: AdminRole): Boolean {
        if (!can(AdminPermission.manageAdmins)) {
            toast = Toast(text = "لا تملك صلاحية إضافة مسؤولين", kind = Toast.Kind.error)
            return false
        }
        val phone = normalize(raw)
        if (phone.length != AppConfig.nationalNumberLength) {
            toast = Toast(text = "رقم غير صالح", kind = Toast.Kind.error)
            return false
        }
        if (admins.any { it.phone == phone }) {
            toast = Toast(text = "هذا الرقم مضاف مسبقاً", kind = Toast.Kind.error)
            return false
        }
        if (role == AdminRole.owner) {
            toast = Toast(text = "لا يمكن إنشاء مالك ثانٍ", kind = Toast.Kind.error)
            return false
        }
        admins = admins + AdminUser(
            name = if (name.isEmpty()) "مسؤول ${phone.takeLast(4)}" else name,
            phone = phone, role = role, createdByName = currentAdmin?.name,
        )
        Feedback.success()
        toast = Toast(text = "أُضيف $name كـ${role.title}", kind = Toast.Kind.success)
        save()
        return true
    }

    fun setAdminRole(role: AdminRole, id: Id) {
        if (!can(AdminPermission.manageAdmins) || role == AdminRole.owner) return
        val i = admins.indexOfFirst { it.id == id }
        if (i < 0 || admins[i].role == AdminRole.owner) return
        admins = admins.mapAt(i) { it.copy(role = role) }
        Feedback.selection()
        save()
    }

    fun setAdminActive(active: Boolean, id: Id) {
        val i = admins.indexOfFirst { it.id == id }
        if (!can(AdminPermission.manageAdmins) || i < 0 || admins[i].role == AdminRole.owner) {
            toast = Toast(text = "لا يمكن تعطيل حساب المالك", kind = Toast.Kind.error)
            return
        }
        admins = admins.mapAt(i) { it.copy(isActive = active) }
        toast = Toast(
            text = if (active) "أُعيد تفعيل ${admins[i].name}" else "أُوقف ${admins[i].name}",
        )
        save()
    }

    fun removeAdmin(id: Id) {
        val a = admins.firstOrNull { it.id == id }
        if (!can(AdminPermission.manageAdmins) || a == null || a.role == AdminRole.owner) {
            toast = Toast(text = "لا يمكن حذف حساب المالك", kind = Toast.Kind.error)
            return
        }
        admins = admins.filter { it.id != id }
        toast = Toast(text = "حُذف ${a.name}")
        save()
    }

    // ───────── المكافآت ─────────

    fun redeem(reward: Reward): Boolean {
        val cid = session?.customerID ?: return false
        val ci = customers.indexOfFirst { it.id == cid }
        if (ci < 0) return false
        if (!reward.isActive) return false
        if (customers[ci].points < reward.cost) {
            toast = Toast(text = "نقاطك لا تكفي — تحتاج ${reward.cost - customers[ci].points} نقطة",
                kind = Toast.Kind.error)
            Feedback.warning()
            return false
        }

        customers = customers.mapAt(ci) { it.copy(points = it.points - reward.cost) }
        val ri = rewards.indexOfFirst { it.id == reward.id }
        if (ri >= 0) rewards = rewards.mapAt(ri) { it.copy(timesRedeemed = it.timesRedeemed + 1) }

        if (reward.kind == RewardKind.walletCredit) {
            customers = customers.mapAt(ci) { it.copy(walletBalance = it.walletBalance + reward.value) }
            transactions = listOf(
                WalletTxn(ownerID = cid, kind = WalletTxnKind.reward, amount = reward.value),
            ) + transactions
            toast = Toast(text = "أُضيف ${reward.value.money} لمحفظتك", kind = Toast.Kind.success)
        } else {
            vouchers = listOf(
                Voucher(
                    customerID = cid, rewardTitle = reward.title, kind = reward.kind,
                    value = reward.value, maxDiscount = reward.maxDiscount,
                    expiresAt = nowMs() + (reward.validDays * DAY_MS),
                ),
            ) + vouchers
            toast = Toast(text = "حصلت على «${reward.title}» — استخدمها في طلبك القادم",
                kind = Toast.Kind.success)
        }
        Feedback.success()
        save()
        return true
    }

    val usableVouchers: List<Voucher>
        get() {
            val cid = session?.customerID ?: return emptyList()
            return vouchers.filter { it.customerID == cid && it.isUsable }
                .sortedBy { it.expiresAt }
        }

    private fun awardTierPoints(delta: Int, driverIndex: Int, reason: String) {
        if (delta == 0) return
        val before = drivers[driverIndex].tier
        drivers = drivers.mapAt(driverIndex) { it.copy(tierPoints = max(0, it.tierPoints + delta)) }
        val after = drivers[driverIndex].tier

        if (after == before) return
        val up = after.raw > before.raw
        if (session?.driverID == drivers[driverIndex].id) {
            toast = Toast(
                text = if (up) "ترقّيت إلى الرتبة ${after.title}" else "نزلت رتبتك إلى ${after.title}",
                kind = if (up) Toast.Kind.success else Toast.Kind.error,
            )
            Feedback.notify(if (up) NotifyType.success else NotifyType.warning)
        }
    }

    // ───────── إدارة المندوبين والعملاء ─────────

    fun setApproval(approval: DriverApproval, driverID: Id, note: String? = null) {
        val i = drivers.indexOfFirst { it.id == driverID }
        if (i < 0) return
        drivers = drivers.mapAt(i) {
            it.copy(approval = approval, rejectionNote = note,
                presence = if (approval != DriverApproval.approved) DriverPresence.offline else it.presence)
        }
        toast = Toast(text = "${drivers[i].name}: ${approval.title}", kind = Toast.Kind.success)
        Feedback.selection()
        save()
        val ph = drivers[i].phone
        if (OrdersService.isAvailable) {
            scope.launch {
                try { DirectorySync.setDriverState(ph, approval.name, null) }
                catch (e: Exception) {
                    toast = Toast(text = "تعذّر تسجيل القرار على السيرفر — " + (e.message ?: ""), kind = Toast.Kind.error)
                }
            }
        }
    }

    fun setCommission(fee: Double?, driverID: Id) {
        val i = drivers.indexOfFirst { it.id == driverID }
        if (i < 0) return
        drivers = drivers.mapAt(i) { it.copy(commissionFee = fee) }; save()
    }

    fun setZone(zone: String, driverID: Id) {
        val i = drivers.indexOfFirst { it.id == driverID }
        if (i < 0) return
        drivers = drivers.mapAt(i) { it.copy(zone = zone) }; save()
    }

    fun setBlocked(blocked: Boolean, customerID: Id) {
        val i = customers.indexOfFirst { it.id == customerID }
        if (i < 0) return
        customers = customers.mapAt(i) { it.copy(isBlocked = blocked) }
        toast = Toast(text = if (blocked) "أُوقف حساب ${customers[i].name}" else "أُعيد تفعيل الحساب")
        save()
    }

    fun reassign(order: Order, driverID: Id?) {
        val oi = orders.indexOfFirst { it.id == order.id }
        if (oi < 0) return
        val old = orders[oi].driverID
        if (old != null) {
            val di = drivers.indexOfFirst { it.id == old }
            if (di >= 0) drivers = drivers.mapAt(di) { it.copy(presence = DriverPresence.online) }
        }
        val ndi = if (driverID != null) drivers.indexOfFirst { it.id == driverID } else -1
        if (driverID != null && ndi >= 0) {
            orders = orders.mapAt(oi) {
                it.copy(driverID = driverID, status = OrderStatus.pickingUp,
                    driverCoord = drivers[ndi].coord)
            }
            routes[orders[oi].id] = Geo.route(drivers[ndi].coord, orders[oi].pickup.coord)
            progress[orders[oi].id] = 0.0
            toast = Toast(text = "أُعيد توزيع ${orders[oi].ref} على ${drivers[ndi].name}",
                kind = Toast.Kind.success)
        } else {
            orders = orders.mapAt(oi) {
                it.copy(driverID = null, status = OrderStatus.searching, declinedBy = emptyList())
            }
            val again = Dispatch.candidates(orders[oi], drivers, rules.minEarningsBalance)
            val offered = orders[oi].offeredAt.toMutableMap()
            for (d in again) offered[d.id] = nowMs()
            orders = orders.mapAt(oi) { it.copy(offeredTo = again.map { d -> d.id }, offeredAt = offered) }
            progress[orders[oi].id] = 0.0
            toast = Toast(text = "أُعيد ${orders[oi].ref} لقائمة البحث")
        }
        Feedback.selection()
        save()

        // يُبلَّغ السيرفر ليصل التغيير للمندوبين والزبون
        val remote = orders[oi].remoteID
        if (remote != null && OrdersService.isAvailable) {
            scope.launch {
                try {
                    val b = org.json.JSONObject().put("orderID", remote)
                    if (driverID != null) b.put("driverID", driverID)
                    OrdersService.send("api/admin/reassign", "POST", b)
                } catch (e: Exception) {
                    toast = Toast(text = "تعذّر تنفيذ إعادة التوزيع في السيرفر — " + (e.message ?: ""), kind = Toast.Kind.error)
                }
            }
        }
    }

    // ───────── الدردشة والدعم ─────────

    /** المفتاح الحقيقي للمحادثة عند السيرفر. */
    private fun canon(thread: String): String = when {
        thread.startsWith("support-") -> if (session?.role == UserRole.admin) thread else "support"
        thread.startsWith("order-") -> {
            val lid = thread.removePrefix("order-")
            "order-" + (orders.firstOrNull { it.id == lid }?.remoteID ?: lid)
        }
        else -> thread
    }

    fun thread(id: String): List<ChatMessage> {
        val t = canon(id)
        return chats.filter { it.threadID == t }.sortedBy { it.date }
    }

    private var chatSince = 0.0
    private var chatPrimed = false

    /** حالة تذاكر الدعم (المفتاح: "support" عند العميل/المندوب، و support-<id> عند الإدارة). */
    var supportTickets by mutableStateOf(mapOf<String, SupportTicketInfo>())
        private set

    fun ticketFor(thread: String): SupportTicketInfo? = supportTickets[canon(thread)]

    private var readTick by androidx.compose.runtime.mutableIntStateOf(0)
    private val readPrefs get() = Boot.app.getSharedPreferences("jaynak.chat", 0)

    /** عدد الرسائل غير المقروءة (من الطرف الآخر) في محادثة. */
    fun unread(thread: String): Int {
        readTick
        val t = canon(thread)
        val seen = readPrefs.getLong("read-$t", 0L)
        return chats.count { it.threadID == t && !it.isMine && it.kind == "msg" && it.date > seen }
    }

    /** مجموع غير المقروء في كل محادثات الدعم (للإدارة). */
    fun unreadSupportTotal(): Int {
        readTick
        return chats.filter { it.threadID.startsWith("support") }.map { it.threadID }.distinct().sumOf { unread(it) }
    }

    fun markRead(thread: String) {
        val t = canon(thread)
        val last = chats.filter { it.threadID == t }.maxOfOrNull { it.date } ?: return
        if (readPrefs.getLong("read-$t", 0L) >= last) return
        readPrefs.edit().putLong("read-$t", last).apply()
        readTick++
    }

    fun closeTicket(thread: String) {
        val t = canon(thread)
        scope.launch {
            try {
                OrdersService.chatClose(t)
                refreshChats()
            } catch (e: Exception) {
                toast = Toast(text = "تعذّر إغلاق التذكرة — " + (e.message ?: ""), kind = Toast.Kind.error)
            }
        }
    }

    fun transferTicket(thread: String, dept: String) {
        val t = canon(thread)
        scope.launch {
            try {
                OrdersService.chatTransfer(t, dept.trim())
                refreshChats()
            } catch (e: Exception) {
                toast = Toast(text = "تعذّر التحويل — " + (e.message ?: ""), kind = Toast.Kind.error)
            }
        }
    }

    /** يجلب الرسائل الجديدة من السيرفر وينبّه (صوت + إشعار) عند وصول رسالة. */
    suspend fun refreshChats() {
        if (!OrdersService.isAvailable || session == null) return
        PhotoApi.pulseMe()
        try {
            val resp = OrdersService.chatList(chatSince)
            val arr = resp.optJSONArray("messages") ?: org.json.JSONArray()
            resp.optJSONObject("tickets")?.let { tj ->
                val m = mutableMapOf<String, SupportTicketInfo>()
                tj.keys().forEach { k ->
                    val o = tj.getJSONObject(k)
                    m[k] = SupportTicketInfo(o.optInt("no"), o.optString("status", "open"), o.optString("dept"))
                }
                supportTickets = m
            }
            val known = chats.map { it.id }.toHashSet()
            val add = mutableListOf<ChatMessage>()
            val adminIds = HashSet<String>()
            var maxTs = chatSince
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                val ts = j.optDouble("ts", 0.0)
                if (ts > maxTs) maxTs = ts
                val mid = j.optString("id")
                if (mid.isEmpty() || mid in known) continue
                val kind = j.optString("kind", "msg")
                if (j.optString("role") == "admin" || kind == "transfer") adminIds += mid
                add += ChatMessage(
                    id = mid, threadID = j.optString("thread"), senderID = j.optString("phone"),
                    senderName = j.optString("name"), isMine = j.optBoolean("mine"),
                    body = j.optString("body"), date = (ts * 1000).toLong(),
                    kind = kind, ticketNo = j.optInt("ticket", 0))
            }
            chatSince = maxTs
            if (add.isNotEmpty()) {
                chats = chats + add
                save()
                val incoming = add.filter { !it.isMine && it.kind != "close" }
                if (chatPrimed && incoming.isNotEmpty()) {
                    val m = incoming.last()
                    val fromAdmin = m.id in adminIds
                    if (fromAdmin) Feedback.adminMessage() else Feedback.message()
                    Notifications.chat(m.senderName, m.body, fromAdmin)
                    toast = Toast(text = "💬 ${m.senderName}: ${m.body.take(60)}")
                }
            }
            chatPrimed = true
        } catch (e: Exception) {
            // الدورة القادمة
        }
    }

    fun send(body: String, thread: String, role: UserRole) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        val name: String
        val id: Id
        when (role) {
            UserRole.customer -> { name = currentCustomer?.name ?: "العميل"; id = session?.customerID ?: newId() }
            UserRole.driver -> { name = currentDriver?.name ?: "المندوب"; id = session?.driverID ?: newId() }
            UserRole.admin -> { name = "الدعم الفني"; id = newId() }
        }
        val th = canon(thread)
        val msg = ChatMessage(threadID = th, senderID = id, senderName = name,
            isMine = true, body = trimmed)
        chats = chats + msg
        Feedback.impact(ImpactStyle.light)
        save()
        if (OrdersService.isAvailable) {
            scope.launch {
                try {
                    OrdersService.chatSend(msg.id, th, trimmed, if (role == UserRole.driver) "driver" else "customer")
                } catch (e: Exception) {
                    toast = Toast(text = "تعذّر إرسال الرسالة — " + (e.message ?: ""), kind = Toast.Kind.error)
                }
            }
        } else autoReply(thread, role)
    }

    /** ردّ آلي تجريبي بعد ١٫٦ ثانية — كما في iOS. */
    private fun autoReply(thread: String, role: UserRole) {
        val replies = if (thread.startsWith("support"))
            listOf("وصلتنا رسالتك، نتابعها الآن.", "تم تحويل الطلب للفريق المختص.", "شكراً لصبرك — نرد عليك بأقرب وقت.")
        else
            listOf("تمام، في الطريق.", "وصلت المكان، أنتظرك.", "خلاص استلمت، جاي لك.")
        val text = replies[Random.nextInt(replies.size)]
        val senderName = if (role == UserRole.customer) (activeDriverName(thread) ?: "المندوب") else "العميل"
        scope.launch {
            delay(1_600)
            chats = chats + ChatMessage(threadID = thread, senderID = newId(),
                senderName = senderName, isMine = false, body = text)
            Feedback.impact(ImpactStyle.light)
            save()
        }
    }

    private fun activeDriverName(thread: String): String? {
        val o = orders.firstOrNull { "order-${it.id}" == thread } ?: return null
        val did = o.driverID ?: return null
        return drivers.firstOrNull { it.id == did }?.name
    }

    fun reportMessage(msg: ChatMessage) {
        val i = chats.indexOfFirst { it.id == msg.id }
        if (i < 0) return
        chats = chats.mapAt(i) { it.copy(isReported = true) }
        val ticket = SupportTicket(
            ref = "SP-${Random.nextInt(1000, 10000)}",
            openedBy = session?.customerID ?: session?.driverID ?: newId(),
            openerName = currentCustomer?.name ?: currentDriver?.name ?: "مستخدم",
            role = session?.role ?: UserRole.customer,
            subject = "إبلاغ عن رسالة مخالفة",
            messages = listOf(ChatMessage(threadID = "report", senderID = msg.senderID,
                senderName = msg.senderName,
                body = "الرسالة المُبلَّغ عنها: «${msg.body}»")),
        )
        tickets = listOf(ticket) + tickets
        toast = Toast(text = "أُبلغنا الإدارة. رقم البلاغ ${ticket.ref}", kind = Toast.Kind.success)
        Feedback.success()
        save()
    }

    fun openTicket(subject: String, body: String, orderRef: String? = null) {
        val role = session?.role ?: UserRole.customer
        val ticket = SupportTicket(
            ref = "SP-${Random.nextInt(1000, 10000)}",
            openedBy = session?.customerID ?: session?.driverID ?: newId(),
            openerName = currentCustomer?.name ?: currentDriver?.name ?: "مستخدم",
            role = role, subject = subject,
            messages = listOf(ChatMessage(threadID = "ticket", senderID = newId(),
                senderName = currentCustomer?.name ?: currentDriver?.name ?: "مستخدم",
                isMine = true, body = body)),
            orderRef = orderRef,
        )
        tickets = listOf(ticket) + tickets
        toast = Toast(text = "أُنشئت التذكرة ${ticket.ref}", kind = Toast.Kind.success)
        Feedback.success()
        save()
    }

    fun replyTicket(ticket: SupportTicket, body: String, asAdmin: Boolean) {
        val i = tickets.indexOfFirst { it.id == ticket.id }
        if (i < 0) return
        tickets = tickets.mapAt(i) {
            var t = it.copy(messages = it.messages + ChatMessage(
                threadID = "ticket", senderID = newId(),
                senderName = if (asAdmin) "الدعم الفني" else it.openerName,
                isMine = !asAdmin, body = body))
            if (asAdmin && t.status == TicketStatus.open) t = t.copy(status = TicketStatus.pending)
            t
        }
        Feedback.impact(ImpactStyle.light)
        save()
    }

    fun setTicketStatus(s: TicketStatus, ticket: SupportTicket) {
        val i = tickets.indexOfFirst { it.id == ticket.id }
        if (i < 0) return
        tickets = tickets.mapAt(i) { it.copy(status = s) }; save()
    }

    // ───────── حذف الحساب ─────────

    /** الحساب لا يُحذف فوراً — يُنشأ طلب تنتظر موافقة الإدارة. */
    fun requestAccountDeletion(reason: String) {
        val s = session ?: return
        val trimmed = reason.trim()
        if (trimmed.isEmpty()) return

        val role: UserRole
        val id: Id
        val name: String
        val cid = s.customerID
        val did = s.driverID
        val c = if (cid != null) customers.firstOrNull { it.id == cid } else null
        val d = if (did != null) drivers.firstOrNull { it.id == did } else null
        if (cid != null && c != null) {
            role = UserRole.customer; id = cid; name = c.name
        } else if (did != null && d != null) {
            role = UserRole.driver; id = did; name = d.name
        } else {
            return
        }

        if (deletionRequests.any { it.requestedBy == id && it.status == DeletionRequestStatus.pending }) return

        deletionRequests = listOf(
            AccountDeletionRequest(requestedBy = id, requesterName = name, role = role,
                phone = s.phone, reason = trimmed),
        ) + deletionRequests
        Feedback.success()
        toast = Toast(text = "أُرسل طلب حذف الحساب — سيراجعه فريقنا قريباً", kind = Toast.Kind.success)
        save()
    }

    /** الطلب المعلَّق للحساب الحالي، إن وُجد. */
    val myDeletionRequest: AccountDeletionRequest?
        get() {
            val s = session ?: return null
            val id = s.customerID ?: s.driverID ?: return null
            return deletionRequests.firstOrNull {
                it.requestedBy == id && it.status == DeletionRequestStatus.pending
            }
        }

    fun approveDeletionRequest(request: AccountDeletionRequest) {
        val i = deletionRequests.indexOfFirst { it.id == request.id }
        if (i < 0) return

        if (request.role == UserRole.customer) {
            orders = orders.filter { it.customerID != request.requestedBy }
            transactions = transactions.filter { it.ownerID != request.requestedBy }
            customers = customers.filter { it.id != request.requestedBy }
        } else if (request.role == UserRole.driver) {
            orders = orders.map {
                if (it.driverID == request.requestedBy) it.copy(driverID = null) else it
            }
            transactions = transactions.filter { it.ownerID != request.requestedBy }
            drivers = drivers.filter { it.id != request.requestedBy }
        }

        if (session?.customerID == request.requestedBy || session?.driverID == request.requestedBy) {
            session = null
            challenge = null
        }

        deletionRequests = deletionRequests.mapAt(i) {
            it.copy(status = DeletionRequestStatus.approved, decidedAt = nowMs())
        }
        toast = Toast(text = "تمت الموافقة على حذف الحساب", kind = Toast.Kind.success)
        save()
    }

    fun rejectDeletionRequest(request: AccountDeletionRequest, reason: String) {
        val i = deletionRequests.indexOfFirst { it.id == request.id }
        if (i < 0) return
        deletionRequests = deletionRequests.mapAt(i) {
            it.copy(status = DeletionRequestStatus.rejected, decidedAt = nowMs(),
                rejectionReason = reason.trim())
        }
        toast = Toast(text = "رُفض طلب الحذف", kind = Toast.Kind.info)
        save()
    }

    // ───────── محاكاة الحركة ─────────

    private fun rebuildRoutes() {
        for (o in orders) {
            if (!o.status.isLive) continue
            val dc = o.driverCoord ?: continue
            val dest = if (o.status == OrderStatus.pickedUp || o.status == OrderStatus.delivering)
                o.dropoff.coord else o.pickup.coord
            routes[o.id] = Geo.route(dc, dest)
            progress[o.id] = progress[o.id] ?: 0.0
        }
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {
                delay(500)
                tick()
            }
        }
    }

    private fun tick() {
        val sessionDriverID = session?.driverID
        var changed = false

        var i = 0
        while (i < orders.size) {
            val idx = i
            i++
            if (!orders[idx].status.isLive) continue
            var o = orders[idx]
            // السيرفر هو المصدر الوحيد للحالة: لا محاكاة للطلبات الحقيقية
            if (o.remoteID != null || OrdersService.isAvailable) continue

            when (o.status) {
                OrderStatus.searching -> {
                    // طلب مفتوح أو مُرقّى: لا يُوزَّع تلقائياً.
                    if (o.dispatchMode == DispatchMode.open || o.promotedToOpen) continue
                    val ownedBySessionDriver = sessionDriverID?.let { o.offeredTo.contains(it) } ?: false
                    if (!ownedBySessionDriver && (nowMs() - o.createdAt) / 1000.0 > 3.5) {
                        val winner = Dispatch.candidates(o, drivers, rules.minEarningsBalance)
                            .firstOrNull { it.id != sessionDriverID }
                        if (winner != null) {
                            assign(o, winner.id, manual = false)
                            changed = true
                            continue
                        }
                    }

                    if (o.dispatchMode == DispatchMode.nearby && !o.promotedToOpen &&
                        (nowMs() - o.createdAt) / 1000.0 > Dispatch.promoteToOpenAfter
                    ) {
                        o = o.copy(promotedToOpen = true)
                        orders = orders.mapAt(idx) { o }
                        changed = true
                        if (session?.customerID == o.customerID) {
                            toast = Toast(text = "لم نجد مندوباً قريباً — نشرنا طلبك لكل المندوبين",
                                kind = Toast.Kind.info)
                        }
                    }

                    if ((nowMs() - o.createdAt) / 1000.0 > 600) {
                        o = o.copy(status = OrderStatus.cancelled,
                            cancelledReason = "لم يقبل أي مندوب الطلب")
                        orders = orders.mapAt(idx) { o }
                        changed = true
                    }
                }

                OrderStatus.assigned, OrderStatus.pickingUp, OrderStatus.pickedUp, OrderStatus.delivering -> {
                    val isSessionDrivers = o.driverID != null && o.driverID == sessionDriverID
                    val leg = if (o.status == OrderStatus.pickedUp || o.status == OrderStatus.delivering) 18.0 else 14.0
                    val step = 0.5 / leg
                    var t = (progress[o.id] ?: 0.0) + step

                    val ceilingT = if (isSessionDrivers) 0.97 else 1.0
                    t = min(t, ceilingT)
                    progress[o.id] = t

                    val route = routes[o.id]
                    if (route != null) o = o.copy(driverCoord = Geo.position(route, t))
                    if (o.status == OrderStatus.pickedUp) o = o.copy(status = OrderStatus.delivering)

                    if (t >= 1.0 && !isSessionDrivers) {
                        if (o.status == OrderStatus.delivering) {
                            orders = orders.mapAt(idx) { o }
                            complete(idx)
                            changed = true
                            continue
                        } else {
                            o = o.copy(status = OrderStatus.pickedUp)
                            routes[o.id] = Geo.route(o.pickup.coord, o.dropoff.coord)
                            progress[o.id] = 0.0
                        }
                    }
                    orders = orders.mapAt(idx) { o }
                    val did = o.driverID
                    val dc = o.driverCoord
                    if (did != null && dc != null) {
                        val di = drivers.indexOfFirst { it.id == did }
                        if (di >= 0) drivers = drivers.mapAt(di) { it.copy(coord = dc) }
                    }
                }

                else -> {}
            }
        }
        if (changed) save()
    }

    private fun nextRef(): String {
        val n = 1040 + orders.size
        return "MB-$n"
    }
}
