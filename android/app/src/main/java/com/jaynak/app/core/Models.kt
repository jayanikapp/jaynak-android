package com.jaynak.app.core

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** مقابل UUID في iOS — نص، يُولَّد عشوائياً. */
typealias Id = String

fun newId(): Id = UUID.randomUUID().toString()
fun nowMs(): Long = System.currentTimeMillis()

const val DAY_MS: Long = 86_400_000L

/** هل المستخدم يدخل لحساب قائم أم ينشئ حساباً جديداً؟ */
@Serializable
enum class AuthMode {
    signIn, signUp;

    val title: String get() = if (this == signIn) "الدخول بالرقم" else "إنشاء حساب"
}

@Serializable
enum class UserRole {
    customer, driver, admin;

    val title: String
        get() = when (this) {
            customer -> "زبون"
            driver -> "مندوب"
            admin -> "إدارة"
        }
    val subtitle: String
        get() = when (this) {
            customer -> "اطلب مندوب وتابعه لحظة بلحظة"
            driver -> "استلم طلبات وزِد دخلك"
            admin -> "لوحة تحكم النظام"
        }
    val loginLabel: String
        get() = when (this) {
            customer -> "أنا زبون"
            driver -> "أنا مندوب"
            admin -> "لوحة الإدارة"
        }

    /** اسم رمز SF في iOS — يُترجم إلى أيقونة في طبقة التصميم. */
    val symbol: String
        get() = when (this) {
            customer -> "bag.fill"
            driver -> "car.fill"
            admin -> "slider.horizontal.3"
        }
}

@Serializable
enum class AdminRole {
    owner, manager, support, finance;

    val title: String
        get() = when (this) {
            owner -> "المالك"
            manager -> "مدير عمليات"
            support -> "دعم فني"
            finance -> "مالية"
        }

    val detail: String
        get() = when (this) {
            owner -> "كل الصلاحيات، وإدارة بقية المسؤولين"
            manager -> "الطلبات والمندوبين والعملاء والدعم"
            support -> "تذاكر الدعم ومتابعة الطلبات فقط"
            finance -> "التقارير المالية والتسعير والمكافآت"
        }

    val symbol: String
        get() = when (this) {
            owner -> "crown.fill"
            manager -> "person.badge.shield.checkmark.fill"
            support -> "lifepreserver.fill"
            finance -> "chart.pie.fill"
        }

    val permissions: Set<AdminPermission>
        get() = when (this) {
            owner -> AdminPermission.values().toSet()
            manager -> setOf(AdminPermission.viewOverview, AdminPermission.manageOrders,
                AdminPermission.manageDrivers, AdminPermission.manageCustomers,
                AdminPermission.manageSupport)
            support -> setOf(AdminPermission.viewOverview, AdminPermission.viewOrders,
                AdminPermission.manageSupport)
            finance -> setOf(AdminPermission.viewOverview, AdminPermission.viewOrders,
                AdminPermission.viewFinance, AdminPermission.managePricing,
                AdminPermission.manageRewards)
        }
}

@Serializable
enum class AdminPermission {
    viewOverview,
    viewOrders,
    manageOrders,
    manageDrivers,
    manageCustomers,
    managePricing,
    manageRewards,
    manageSupport,
    viewFinance,
    manageAdmins,
}

@Serializable
data class AdminUser(
    val id: Id = newId(),
    val name: String,
    val phone: String,
    val role: AdminRole,
    val isActive: Boolean = true,
    val createdAt: Long = nowMs(),

    val createdByName: String? = null,
    val lastSignIn: Long? = null,
    /** أقسام اللوحة المسموحة كما يحددها السيرفر؛ null = حسب الدور. */
    val tabs: List<String>? = null,
) {
    val can: Set<AdminPermission>
        get() {
            if (!isActive) return emptySet()
            if (role == AdminRole.owner) return role.permissions
            val t = tabs ?: return role.permissions
            return t.flatMap { AdminTabs.permissions(it) }.toSet()
        }
}

object AdminTabs {
    fun permissions(tab: String): Set<AdminPermission> = when (tab) {
        "over" -> setOf(AdminPermission.viewOverview)
        "map", "trace", "proofs" -> setOf(AdminPermission.viewOrders)
        "orders" -> setOf(AdminPermission.viewOrders, AdminPermission.manageOrders)
        "drivers" -> setOf(AdminPermission.manageDrivers)
        "cust" -> setOf(AdminPermission.manageCustomers, AdminPermission.viewFinance)
        "support" -> setOf(AdminPermission.manageSupport)
        "payouts" -> setOf(AdminPermission.viewFinance)
        else -> emptySet()
    }
}

@Serializable
data class Coord(val lat: Double, val lon: Double) {

    /** المسافة بالكيلومتر (هافرساين). */
    fun km(o: Coord): Double {
        val r = 6371.0
        val dLat = (o.lat - lat) * Math.PI / 180
        val dLon = (o.lon - lon) * Math.PI / 180
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat * Math.PI / 180) * cos(o.lat * Math.PI / 180) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun lerp(o: Coord, t: Double): Coord =
        Coord(lat + (o.lat - lat) * t, lon + (o.lon - lon) * t)
}

@Serializable
data class Place(
    val id: Id = newId(),
    val name: String,
    val zone: String,
    val coord: Coord,
    val details: String = "",
    /** رابط الموقع الأصلي الذي ألصقه الزبون (جوجل ماب…) — يفتحه المندوب مباشرة. */
    val link: String? = null,
)

@Serializable
enum class DispatchMode {
    /** يُعرض على أقرب مندوب */
    nearby,

    /** يراه كل المندوبين */
    open;

    val title: String
        get() = when (this) {
            nearby -> "توصيل سريع"
            open -> "طلب مفتوح"
        }
    val detail: String
        get() = when (this) {
            nearby -> "لأقرب مندوب — أسرع استجابة"
            open -> "يراه كل المندوبين — أنسب للمسافات الطويلة"
        }
    val symbol: String
        get() = when (this) {
            nearby -> "bolt.fill"
            open -> "list.bullet.rectangle.portrait.fill"
        }
}

@Serializable
enum class OrderKind {
    documents, parcel, errand;

    val title: String
        get() = when (this) {
            documents -> "مستندات"
            parcel -> "طرد"
            errand -> "مشاوير"
        }
    val note: String
        get() = when (this) {
            documents -> "أوراق، عقود، شيكات"
            parcel -> "حتى ١٠ كجم"
            errand -> "شراء وتوصيل"
        }
    val symbol: String
        get() = when (this) {
            documents -> "doc.text.fill"
            parcel -> "shippingbox.fill"
            errand -> "cart.fill"
        }

    val multiplier: Double
        get() = when (this) {
            documents -> 1.0
            parcel -> 1.15
            errand -> 1.35
        }
}

@Serializable
enum class PaymentMethod {
    wallet, cash;

    val title: String get() = if (this == wallet) "المحفظة" else "كاش للمندوب"
    val symbol: String get() = if (this == wallet) "creditcard.fill" else "banknote.fill"
}

@Serializable
enum class OrderStatus {
    searching,
    assigned,
    pickingUp,
    pickedUp,
    delivering,
    delivered,
    cancelled;

    val title: String
        get() = when (this) {
            searching -> "نبحث عن مندوب"
            assigned -> "تم تعيين مندوب"
            pickingUp -> "في الطريق للاستلام"
            pickedUp -> "استلم الطلب"
            delivering -> "في الطريق إليك"
            delivered -> "تم التسليم"
            cancelled -> "ملغي"
        }
    val isLive: Boolean
        get() = when (this) {
            delivered, cancelled -> false
            else -> true
        }

    val step: Int
        get() = when (this) {
            searching -> 0
            assigned, pickingUp -> 1
            pickedUp -> 2
            delivering -> 3
            delivered -> 4
            cancelled -> 0
        }
}

@Serializable
data class PriceBreakdown(
    val base: Double = 0.0,
    val distance: Double = 0.0,
    val time: Double = 0.0,
    val zoneSurcharge: Double = 0.0,
    val kindAdjustment: Double = 0.0,
    val surge: Double = 0.0,
    val discount: Double = 0.0,
    val total: Double = 0.0,
    val km: Double = 0.0,
    val minutes: Int = 0,
    val commission: Double = 0.0,
    val driverPayout: Double = 0.0,
    val promoCode: String? = null,

    val voucherTitle: String? = null,

    val pointsEarned: Int = 0,
)

@Serializable
data class Order(
    val id: Id = newId(),

    /** معرّف الطلب على الخادم (token_hex) — فارغ للطلبات المحلية فقط. */
    val remoteID: String? = null,
    val ref: String,
    val customerID: Id,
    val driverID: Id? = null,
    val kind: OrderKind,
    val dispatchMode: DispatchMode = DispatchMode.nearby,

    val promotedToOpen: Boolean = false,
    val pickup: Place,
    val dropoff: Place,
    val note: String = "",
    val recipientName: String = "",
    val recipientPhone: String = "",
    val payment: PaymentMethod,
    val price: PriceBreakdown,
    val status: OrderStatus = OrderStatus.searching,
    val createdAt: Long = nowMs(),
    val acceptedAt: Long? = null,
    val deliveredAt: Long? = null,
    val cancelledReason: String? = null,

    val driverCoord: Coord? = null,
    val customerRating: Int? = null,
    val driverRating: Int? = null,
    val ratingComment: String? = null,

    val offeredTo: List<Id> = emptyList(),
    val declinedBy: List<Id> = emptyList(),

    val offeredAt: Map<String, Long> = emptyMap(),
) {
    val etaMinutes: Int get() = max(1, price.minutes)
}

@Serializable
data class Customer(
    val id: Id = newId(),
    val name: String,
    val phone: String,
    val walletBalance: Double = 0.0,
    val createdAt: Long = nowMs(),
    val isBlocked: Boolean = false,
    val savedPlaces: List<Place> = emptyList(),
    val usedPromoCodes: List<String> = emptyList(),

    val points: Int = 0,

    val lifetimePoints: Int = 0,
)

@Serializable
data class Vehicle(
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val plateNumber: String = "",
    val plateLetters: String = "",
    val color: String = "",
    val ownershipNumber: String = "",
) {
    val plateDisplay: String
        get() = if (plateLetters.isEmpty()) plateNumber else "$plateNumber $plateLetters"
}

@Serializable
data class DriverDocuments(
    val civilID: String = "",
    val licenseNumber: String = "",
    val licenseExpiry: Long = nowMs() + 365 * DAY_MS,

    val assets: List<DocumentAsset> = emptyList(),
    /** حساب أنشأته الإدارة وتحقّقت من بياناته — لا يُطلب من المندوب رفع مستندات. */
    val adminVerified: Boolean = false,
) {
    fun assetsOf(kind: DocumentKind): List<DocumentAsset> = assets.filter { it.kind == kind }
    fun has(kind: DocumentKind): Boolean = assetsOf(kind).size >= kind.minimumCount

    val civilIDImage: Boolean get() = has(DocumentKind.civilIDFront)
    val licenseImage: Boolean get() = has(DocumentKind.license)
    val ownershipImage: Boolean get() = has(DocumentKind.ownership)
    val vehicleImages: Int get() = assetsOf(DocumentKind.vehicle).size

    val missing: List<DocumentKind>
        get() = DocumentKind.values().filter { it.isRequired && !has(it) }

    val pendingUpload: List<DocumentAsset>
        get() = assets.filter { it.state == DocumentState.stored || it.state == DocumentState.failed }

    val isComplete: Boolean
        get() = adminVerified || (civilID.digitsOnly.length == AppConfig.civilIDLength &&
            missing.isEmpty())
}

@Serializable
enum class DriverApproval {
    pending, approved, rejected, suspended;

    val title: String
        get() = when (this) {
            pending -> "بانتظار موافقة الإدارة"
            approved -> "معتمد"
            rejected -> "مرفوض"
            suspended -> "موقوف"
        }
}

@Serializable
enum class DriverPresence {
    offline, online, busy;

    val title: String
        get() = when (this) {
            offline -> "غير متصل"
            online -> "متصل"
            busy -> "مشغول"
        }
}

@Serializable
data class Driver(
    val id: Id = newId(),
    val name: String,
    val phone: String,
    val governorate: String = Oman.defaultGovernorate,
    val zone: String = Oman.defaultWilaya,
    val vehicle: Vehicle = Vehicle(),
    val docs: DriverDocuments = DriverDocuments(),
    val approval: DriverApproval = DriverApproval.pending,
    val presence: DriverPresence = DriverPresence.offline,
    val coord: Coord,
    val ratingSum: Int = 0,
    val ratingCount: Int = 0,
    val completedOrders: Int = 0,
    val offersReceived: Int = 0,
    val offersAccepted: Int = 0,
    val earningsBalance: Double = 0.0,

    val commissionFee: Double? = null,
    val rejectionNote: String? = null,
    val createdAt: Long = nowMs(),

    val acceptRadiusKm: Double = 3.0,

    val destination: Place? = null,

    val corridorKm: Double = 6.0,

    val tierPoints: Int = 0,
) {
    val isDestinationMode: Boolean get() = destination != null

    val tier: DriverTier get() = DriverTier.forPoints(max(0, tierPoints))

    fun effectiveCommission(rules: PricingRules): Double {
        val base = commissionFee ?: rules.fixedCommission
        return max(rules.minCommission, base * (1 - tier.commissionDiscount))
    }

    val tierProgress: Double
        get() {
            val next = tier.next ?: return 1.0
            val span = (next.minPoints - tier.minPoints).toDouble()
            if (span <= 0) return 1.0
            return min(1.0, max(0.0, (tierPoints - tier.minPoints) / span))
        }

    val rating: Double get() = if (ratingCount == 0) 0.0 else ratingSum.toDouble() / ratingCount
    val acceptanceRate: Double
        get() = if (offersReceived == 0) 0.0 else offersAccepted.toDouble() / offersReceived
    val canGoOnline: Boolean get() = approval == DriverApproval.approved && docs.isComplete
}

@Serializable
enum class WalletTxnKind {
    topUp, orderPayment, refund, promo, payout, commission, reward;

    val title: String
        get() = when (this) {
            topUp -> "تعبئة رصيد"
            orderPayment -> "دفع طلب"
            refund -> "استرجاع"
            promo -> "رصيد ترويجي"
            payout -> "سحب أرباح"
            commission -> "عمولة المنصة"
            reward -> "مكافأة نقاط"
        }
}

@Serializable
data class WalletTxn(
    val id: Id = newId(),
    val ownerID: Id,
    val kind: WalletTxnKind,
    val amount: Double,
    val date: Long = nowMs(),
    val orderRef: String? = null,
)

@Serializable
data class PromoCode(
    val id: Id = newId(),
    val code: String,
    val percentOff: Double,
    val maxDiscount: Double,
    val newCustomersOnly: Boolean = true,
    val isActive: Boolean = true,
    val timesUsed: Int = 0,
)

@Serializable
enum class RewardKind {
    freeDelivery,
    percentOff,
    fixedOff,
    walletCredit;

    val title: String
        get() = when (this) {
            freeDelivery -> "توصيل مجاني"
            percentOff -> "خصم نسبة"
            fixedOff -> "خصم مبلغ"
            walletCredit -> "رصيد محفظة"
        }
    val symbol: String
        get() = when (this) {
            freeDelivery -> "shippingbox.fill"
            percentOff -> "percent"
            fixedOff -> "tag.fill"
            walletCredit -> "creditcard.fill"
        }
}

@Serializable
data class Reward(
    val id: Id = newId(),
    val title: String,
    val detail: String,
    val cost: Int,
    val kind: RewardKind,

    val value: Double,

    val maxDiscount: Double = 2.000,
    val validDays: Int = 30,
    val isActive: Boolean = true,
    val timesRedeemed: Int = 0,
)

@Serializable
data class Voucher(
    val id: Id = newId(),
    val customerID: Id,
    val rewardTitle: String,
    val kind: RewardKind,
    val value: Double,
    val maxDiscount: Double,
    val earnedAt: Long = nowMs(),
    val expiresAt: Long,
    val usedOrderRef: String? = null,
) {
    val isUsed: Boolean get() = usedOrderRef != null
    val isExpired: Boolean get() = nowMs() > expiresAt
    val isUsable: Boolean get() = !isUsed && !isExpired

    fun discount(total: Double): Double = when (kind) {
        RewardKind.freeDelivery -> total
        RewardKind.percentOff -> min(total * value, maxDiscount)
        RewardKind.fixedOff -> min(value, total)
        RewardKind.walletCredit -> 0.0
    }
}

@Serializable
enum class DriverTier(val raw: Int) {
    bronze(0), silver(1), gold(2), platinum(3);

    val title: String
        get() = when (this) {
            bronze -> "برونزي"
            silver -> "فضي"
            gold -> "ذهبي"
            platinum -> "بلاتيني"
        }

    val minPoints: Int
        get() = when (this) {
            bronze -> 0
            silver -> 300
            gold -> 900
            platinum -> 2000
        }

    val commissionDiscount: Double
        get() = when (this) {
            bronze -> 0.0
            silver -> 0.02
            gold -> 0.04
            platinum -> 0.06
        }

    val dispatchBoostKm: Double
        get() = when (this) {
            bronze -> 0.0
            silver -> 0.5
            gold -> 1.2
            platinum -> 2.0
        }

    val perks: List<String>
        get() = when (this) {
            bronze -> listOf("العمولة الأساسية", "سحب الأرباح أسبوعياً")
            silver -> listOf("عمولة أقل ٢٪", "أفضلية خفيفة في التوزيع")
            gold -> listOf("عمولة أقل ٤٪", "أفضلية في التوزيع", "دعم أسرع")
            platinum -> listOf("عمولة أقل ٦٪", "أعلى أفضلية في التوزيع",
                "سحب فوري للأرباح", "دعم مخصّص")
        }

    val symbol: String
        get() = when (this) {
            bronze -> "shield.fill"
            silver -> "shield.lefthalf.filled"
            gold -> "rosette"
            platinum -> "crown.fill"
        }

    val next: DriverTier? get() = values().firstOrNull { it.raw == raw + 1 }

    companion object {
        fun forPoints(p: Int): DriverTier =
            values().lastOrNull { p >= it.minPoints } ?: bronze
    }
}

@Serializable
data class ZoneRule(
    val id: Id = newId(),
    val name: String,
    val surcharge: Double,
    val isActive: Boolean = true,
)

@Serializable
data class PricingRules(
    val baseFare: Double = 0.800,
    val perKm: Double = 0.120,
    val perMinute: Double = 0.015,
    val minimumFare: Double = 1.000,
    val peakMultiplier: Double = 1.25,
    val peakHours: List<Int> = listOf(7, 8, 13, 14, 17, 18, 19),

    val expressBaseFare: Double = 1.000,
    val expressPerKm: Double = 0.180,
    val expressPerMinute: Double = 0.025,
    val expressCommissionPerRial: Double = 0.100,
    val maxCustomerOrders: Int = 3,
    val maxDriverOpen: Int = 3,
    val maxDriverExpress: Int = 1,

    val fixedCommission: Double = 0.160,

    val minCommission: Double = 0.100,

    val minEarningsBalance: Double = -1.500,

    val earningsWarnBalance: Double = -0.750,

    val payoutWeekdays: List<Int> = listOf(1, 4),

    val zones: List<ZoneRule> = listOf(
        ZoneRule(name = "مسقط", surcharge = 0.200),
        ZoneRule(name = "مطرح", surcharge = 0.100),
        ZoneRule(name = "بوشر", surcharge = 0.0),
        ZoneRule(name = "السيب", surcharge = 0.150),
        ZoneRule(name = "العامرات", surcharge = 0.300),
        ZoneRule(name = "قريات", surcharge = 0.700),
        ZoneRule(name = "بركاء", surcharge = 0.600, isActive = false),
        ZoneRule(name = "صحار", surcharge = 1.500, isActive = false),
        ZoneRule(name = "نزوى", surcharge = 1.800, isActive = false),
        ZoneRule(name = "صلالة", surcharge = 2.500, isActive = false),
    ),
)

@Serializable
enum class ChatChannel { orderChat, support }

@Serializable
data class ChatMessage(
    val id: Id = newId(),
    val threadID: String,
    val senderID: Id,
    val senderName: String,
    val isMine: Boolean = false,
    val body: String,
    val date: Long = nowMs(),
    val isReported: Boolean = false,
    val kind: String = "msg",     // msg | transfer | close
    val ticketNo: Int = 0,
)

data class SupportTicketInfo(val no: Int, val status: String, val dept: String) {
    val isClosed: Boolean get() = status == "closed"
}

@Serializable
enum class TicketStatus {
    open, pending, closed;

    val title: String
        get() = when (this) {
            open -> "مفتوحة"
            pending -> "قيد المعالجة"
            closed -> "مغلقة"
        }
}

@Serializable
data class SupportTicket(
    val id: Id = newId(),
    val ref: String,
    val openedBy: Id,
    val openerName: String,
    val role: UserRole,
    val subject: String,
    val status: TicketStatus = TicketStatus.open,
    val createdAt: Long = nowMs(),
    val messages: List<ChatMessage> = emptyList(),
    val orderRef: String? = null,
)

@Serializable
enum class DeletionRequestStatus {
    pending, approved, rejected;

    val title: String
        get() = when (this) {
            pending -> "قيد المراجعة"
            approved -> "تمت الموافقة"
            rejected -> "مرفوض"
        }
}

/** طلب حذف حساب — لا يُحذف الحساب فوراً، بل ينتظر موافقة الإدارة. */
@Serializable
data class AccountDeletionRequest(
    val id: Id = newId(),
    val requestedBy: Id,
    val requesterName: String,
    val role: UserRole,
    val phone: String,
    val reason: String,
    val status: DeletionRequestStatus = DeletionRequestStatus.pending,
    val createdAt: Long = nowMs(),
    val decidedAt: Long? = null,
    val rejectionReason: String? = null,
)

@Serializable
data class Session(
    val role: UserRole,
    val customerID: Id? = null,
    val driverID: Id? = null,

    val adminID: Id? = null,
    val phone: String,

    val viaDemoAccess: Boolean = false,
)
