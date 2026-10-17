package com.jaynak.app.core

import java.util.Calendar
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// ───────── Loyalty.swift ─────────

object Loyalty {

    const val pointsPerOrder = 10

    const val pointsPerRial = 10.0

    fun points(forOrderTotal: Double): Int =
        pointsPerOrder + floor(forOrderTotal * pointsPerRial).toInt()

    data class Level(val title: String, val next: Int?, val symbol: String)

    fun customerLevel(lifetime: Int): Level = when {
        lifetime < 200 -> Level("عضو جديد", 200, "sparkles")
        lifetime < 800 -> Level("عضو فضي", 800, "star.fill")
        lifetime < 2500 -> Level("عضو ذهبي", 2500, "crown.fill")
        else -> Level("عضو مميّز", null, "trophy.fill")
    }

    const val tierPointsPerDelivery = 10

    fun tierPoints(forRating: Int): Int = when (forRating) {
        5 -> 15
        4 -> 7
        3 -> 0
        2 -> -15
        else -> -30
    }

    const val tierPenaltyForCancel = -25

    fun defaultRewards(): List<Reward> = listOf(
        Reward(title = "توصيل مجاني",
            detail = "طلب واحد بلا رسوم توصيل",
            cost = 400, kind = RewardKind.freeDelivery, value = 1.0, maxDiscount = 3.000,
            validDays = 30),
        Reward(title = "خصم ١٠٪",
            detail = "على طلبك القادم، حتى ٥٠٠ بيسة",
            cost = 100, kind = RewardKind.percentOff, value = 0.10, maxDiscount = 0.500,
            validDays = 30),
        Reward(title = "خصم ٢٥٪",
            detail = "على طلبك القادم، حتى ١ ر.ع.",
            cost = 500, kind = RewardKind.percentOff, value = 0.25, maxDiscount = 1.000,
            validDays = 30),
        Reward(title = "خصم ٥٠٠ بيسة",
            detail = "مبلغ ثابت على أي طلب",
            cost = 150, kind = RewardKind.fixedOff, value = 0.500, maxDiscount = 0.500,
            validDays = 30),
        Reward(title = "رصيد ١ ر.ع.",
            detail = "يُضاف لمحفظتك مباشرة",
            cost = 350, kind = RewardKind.walletCredit, value = 1.000, maxDiscount = 1.000,
            validDays = 1),
    )
}

// ───────── PricingEngine.swift ─────────

object PricingEngine {

    fun quote(
        pickup: Place,
        dropoff: Place,
        kind: OrderKind,
        rules: PricingRules,
        at: Long = nowMs(),
        promo: PromoCode? = null,
        voucher: Voucher? = null,
        express: Boolean = false,
    ): PriceBreakdown {

        val km = (pickup.coord.km(dropoff.coord) * 10).roundedAway() / 10
        val minutes = Geo.minutes(km)

        val base = if (express) rules.expressBaseFare else rules.baseFare
        val distance = km * (if (express) rules.expressPerKm else rules.perKm)
        val time = minutes * (if (express) rules.expressPerMinute else rules.perMinute)

        fun zoneSurcharge(name: String): Double =
            rules.zones.firstOrNull { it.name == name && it.isActive }?.surcharge ?: 0.0
        val zoneSur = max(zoneSurcharge(pickup.zone), zoneSurcharge(dropoff.zone))

        val preKind = base + distance + time + zoneSur
        val kindAdjustment = preKind * (kind.multiplier - 1)

        val hour = Calendar.getInstance().apply { timeInMillis = at }.get(Calendar.HOUR_OF_DAY)
        var surge = 0.0
        if (rules.peakHours.contains(hour)) {
            surge = (preKind + kindAdjustment) * (rules.peakMultiplier - 1)
        }

        var subtotal = preKind + kindAdjustment + surge
        subtotal = max(subtotal, rules.minimumFare)

        var discount = 0.0
        var promoCode: String? = null
        var voucherTitle: String? = null
        if (voucher != null && voucher.isUsable) {
            discount = voucher.discount(subtotal)
            voucherTitle = voucher.rewardTitle
        } else if (promo != null && promo.isActive) {
            discount = min(subtotal * promo.percentOff, promo.maxDiscount)
            promoCode = promo.code
        }

        val total = round3(max(0.0, subtotal - discount))
        val commission = round3(
            if (express) min(total, max(rules.minCommission, total * rules.expressCommissionPerRial))
            else commission(total, rules))
        val driverPayout = round3(total - commission)

        return PriceBreakdown(
            base = round3(base), distance = round3(distance), time = round3(time),
            zoneSurcharge = round3(zoneSur), kindAdjustment = round3(kindAdjustment),
            surge = round3(surge), discount = round3(discount),
            total = total, km = km, minutes = minutes,
            commission = commission, driverPayout = driverPayout,
            promoCode = promoCode, voucherTitle = voucherTitle,
        )
    }

    /** عمولة ثابتة لكل طلب (لا نسبة) — مع خصم رتبة المندوب. */
    fun commission(
        total: Double, rules: PricingRules,
        fee: Double? = null, rateDiscount: Double = 0.0,
    ): Double {
        val base = fee ?: rules.fixedCommission
        val discounted = base * (1 - rateDiscount)
        return min(total, max(rules.minCommission, discounted))
    }
}

// ───────── Dispatch.swift ─────────

object Dispatch {

    const val maxRadiusKm: Double = 25.0

    /** بعد كم ثانية يتحوّل الطلب القريب إلى «مفتوح» لكل المندوبين. */
    const val promoteToOpenAfter: Double = 60.0

    data class Candidate(
        val driver: Driver,
        val km: Double,
        val sameZone: Boolean,
        val viaDestination: Boolean = false,
    ) {
        val id: Id get() = driver.id
    }

    enum class OfferState {
        sent, declined, notContacted;

        val title: String
            get() = when (this) {
                sent -> "وصله الطلب"
                declined -> "لم يقبل"
                notContacted -> "بالانتظار"
            }
    }

    private fun isAvailable(d: Driver, floor: Double): Boolean =
        d.presence == DriverPresence.online && d.approval == DriverApproval.approved &&
            d.canGoOnline && d.earningsBalance >= floor

    /** هل الطلب على طريق المندوب إلى وجهته؟ */
    fun matchesDestination(order: Order, d: Driver): Boolean {
        val dest = d.destination ?: return false

        val pickupOffPath = Geo.distanceToPath(order.pickup.coord, d.coord, dest.coord)
        if (pickupOffPath > d.corridorKm) return false

        val fromPickup = order.pickup.coord.km(dest.coord)
        val fromDropoff = order.dropoff.coord.km(dest.coord)
        if (fromDropoff >= fromPickup) return false

        val dropoffOffPath = Geo.distanceToPath(order.dropoff.coord, d.coord, dest.coord)
        return dropoffOffPath <= d.corridorKm * 1.5
    }

    fun ranked(order: Order, drivers: List<Driver>, balanceFloor: Double): List<Candidate> {

        if (order.dispatchMode != DispatchMode.nearby || order.promotedToOpen) return emptyList()

        val zone = order.pickup.zone
        val out = mutableListOf<Candidate>()

        for (d in drivers) {
            if (!isAvailable(d, balanceFloor)) continue
            val km = (d.coord.km(order.pickup.coord) * 10).roundedAway() / 10
            val radius = min(max(1.0, d.acceptRadiusKm), maxRadiusKm)
            val onPath = matchesDestination(order, d)

            if (!(km <= radius || onPath)) continue
            out.add(Candidate(d, km, d.zone == zone, onPath && km > radius))
        }

        return out.sortedWith(Comparator { a, b ->
            if (a.viaDestination != b.viaDestination) return@Comparator if (a.viaDestination) -1 else 1
            if (a.sameZone != b.sameZone) return@Comparator if (a.sameZone) -1 else 1
            val ea = a.km - a.driver.tier.dispatchBoostKm
            val eb = b.km - b.driver.tier.dispatchBoostKm
            if (abs(ea - eb) > 0.01) return@Comparator if (ea < eb) -1 else 1
            b.driver.rating.compareTo(a.driver.rating)
        })
    }

    fun candidates(
        order: Order, drivers: List<Driver>, balanceFloor: Double,
        fanout: Int = AppConfig.dispatchFanout,
    ): List<Driver> =
        ranked(order, drivers, balanceFloor)
            .filter { !order.declinedBy.contains(it.driver.id) }
            .take(fanout)
            .map { it.driver }

    fun state(driverID: Id, order: Order): OfferState {
        if (order.declinedBy.contains(driverID)) return OfferState.declined
        if (order.offeredAt[driverID] != null) return OfferState.sent
        return OfferState.notContacted
    }

    fun openBoard(orders: List<Order>): List<Order> =
        orders.filter {
            it.status == OrderStatus.searching &&
                (it.dispatchMode == DispatchMode.open || it.promotedToOpen)
        }.sortedByDescending { it.createdAt }

    data class BoardItem(val order: Order, val km: Double, val onPath: Boolean)

    fun board(driver: Driver?, orders: List<Order>, balanceFloor: Double): List<BoardItem> {
        val list = openBoard(orders)
        val d = driver ?: return list.map { BoardItem(it, 0.0, false) }

        if (d.earningsBalance < balanceFloor) return emptyList()
        return list.map { o ->
            BoardItem(o, (d.coord.km(o.pickup.coord) * 10).roundedAway() / 10,
                matchesDestination(o, d))
        }.sortedWith(Comparator { a, b ->
            if (a.onPath != b.onPath) return@Comparator if (a.onPath) -1 else 1
            a.km.compareTo(b.km)
        })
    }
}

// ───────── Identity.swift ─────────

@kotlinx.serialization.Serializable
data class IdentityRecord(
    val id: Id = newId(),

    val civilID: String,
    val phone: String,
    val driverID: Id,
    val name: String,
    val registeredAt: Long = nowMs(),

    val isBarred: Boolean = false,
    val barReason: String? = null,
    val barredAt: Long? = null,

    val outstandingDebt: Double = 0.0,
) {
    val maskedCivilID: String
        get() {
            if (civilID.length <= 4) return civilID
            return "•".repeat(civilID.length - 4) + civilID.takeLast(4)
        }
}

sealed class IdentityCheck {
    object Allowed : IdentityCheck()
    data class CivilIDTaken(val name: String) : IdentityCheck()
    data class PhoneTaken(val name: String) : IdentityCheck()
    data class Barred(val reason: String, val debt: Double) : IdentityCheck()

    val isAllowed: Boolean get() = this is Allowed

    val message: String
        get() = when (this) {
            is Allowed -> ""
            is CivilIDTaken ->
                "هذا الرقم المدني مسجَّل مسبقاً لحساب «$name». " +
                    "لا يمكن تسجيل أكثر من حساب مندوب برقم مدني واحد."
            is PhoneTaken ->
                "رقم الهاتف هذا مسجَّل لحساب «$name». " +
                    "استخدم حسابك الأصلي أو تواصل مع الدعم."
            is Barred -> {
                val d = if (debt > 0) " وعليه مبلغ مستحق ${debt.money}" else ""
                "هذا الرقم المدني موقوف نهائياً: $reason$d. " +
                    "تواصل مع الدعم ${AppConfig.supportPhone}."
            }
        }

    companion object {
        fun barred(reason: String, debt: Double): IdentityCheck = Barred(reason, debt)
        fun phoneTaken(name: String): IdentityCheck = PhoneTaken(name)
    }
}


/** قواعد النقاط من السيرفر (الافتراضي = السلوك السابق). */
data class PointsRules(
    val enabled: Boolean = true,
    val cDelivered: Int = 10, val cPerRial: Int = 10, val cCancelBefore: Int = 0, val cCancelAfter: Int = 20,
    val dAccept: Int = 0, val dDelivered: Int = 10, val dDeclineBefore: Int = 0, val dCancelAfter: Int = 10,
) {
    fun customerEarned(total: Double): Int =
        if (enabled) cDelivered + floor(total * cPerRial).toInt() else 0

    /** role = "customer" | "driver" لشكل api/points، وnull لشكل الإعدادات (الدوران معاً). */
    fun merged(role: String?, j: org.json.JSONObject): PointsRules {
        var r = if (j.has("enabled")) copy(enabled = j.optBoolean("enabled", enabled)) else this
        j.optJSONObject("customer")?.let { c ->
            r = r.copy(cDelivered = c.optInt("delivered", r.cDelivered), cPerRial = c.optInt("perRial", r.cPerRial),
                cCancelBefore = c.optInt("cancelBeforeAccept", r.cCancelBefore), cCancelAfter = c.optInt("cancelAfterAccept", r.cCancelAfter))
        }
        j.optJSONObject("driver")?.let { d ->
            r = r.copy(dAccept = d.optInt("accept", r.dAccept), dDelivered = d.optInt("delivered", r.dDelivered),
                dDeclineBefore = d.optInt("declineBeforeAccept", r.dDeclineBefore), dCancelAfter = d.optInt("cancelAfterAccept", r.dCancelAfter))
        }
        if (role == "customer") r = r.copy(cDelivered = j.optInt("delivered", r.cDelivered), cPerRial = j.optInt("perRial", r.cPerRial),
            cCancelBefore = j.optInt("cancelBeforeAccept", r.cCancelBefore), cCancelAfter = j.optInt("cancelAfterAccept", r.cCancelAfter))
        if (role == "driver") r = r.copy(dAccept = j.optInt("accept", r.dAccept), dDelivered = j.optInt("delivered", r.dDelivered),
            dDeclineBefore = j.optInt("declineBeforeAccept", r.dDeclineBefore), dCancelAfter = j.optInt("cancelAfterAccept", r.dCancelAfter))
        return r
    }
}

data class PointsEntry(val id: String, val at: Double, val delta: Int, val label: String, val ref: String)
