package com.jaynak.app.data

import java.util.Calendar
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong


object Demo {


    const val BASE_FARE = 0.800
    const val PER_KM = 0.120
    const val PER_MINUTE = 0.015
    const val MINIMUM_FARE = 1.000
    const val PEAK_MULTIPLIER = 1.25
    val PEAK_HOURS = setOf(7, 8, 13, 14, 17, 18, 19)


    const val FIXED_COMMISSION = 0.160
    const val MIN_COMMISSION = 0.100
    const val MIN_EARNINGS_BALANCE = -1.500

    private val ZONE_SURCHARGE = mapOf(
        "مسقط" to 0.0, "مطرح" to 0.0, "بوشر" to 0.0,
        "السيب" to 0.100, "العامرات" to 0.200, "قريات" to 0.400,
    )
    private val KIND_MULTIPLIER = mapOf("errand" to 1.0, "parcel" to 1.10, "documents" to 0.95)

    val KINDS = listOf(
        Kind("errand", "مشوار", 1.0),
        Kind("parcel", "طرد", 1.10),
        Kind("documents", "مستندات", 0.95),
    )

    val PLACES = listOf(
        Place("سوق مطرح", "مطرح", 23.6178, 58.5636, "المدخل الرئيسي"),
        Place("جامع السلطان قابوس الأكبر", "بوشر", 23.5836, 58.3878),
        Place("سيتي سنتر مسقط", "السيب", 23.5866, 58.3411, "البوابة ٣"),
        Place("أفنيوز مول مسقط", "بوشر", 23.5798, 58.3592, "الدور الأرضي"),
        Place("مطار مسقط الدولي", "السيب", 23.5933, 58.2844, "صالة الوصول"),
        Place("مستشفى خولة", "مطرح", 23.5946, 58.4295, "مدخل الطوارئ"),
        Place("جامعة السلطان قابوس", "السيب", 23.5905, 58.1665, "البوابة الرئيسية"),
        Place("شاطئ القرم", "مسقط", 23.6100, 58.4740),
        Place("روي - المنطقة التجارية", "مطرح", 23.6000, 58.5400, "مبنى ١٢"),
        Place("الخوير - جمعية", "بوشر", 23.5900, 58.4200, "قطعة ٤"),
        Place("العامرات - قطعة ٦", "العامرات", 23.5100, 58.5000, "منزل ١٨، شارع ٣"),
        Place("المعبيلة الجنوبية", "السيب", 23.6300, 58.1900),
    )

    val ZONES = listOf("مسقط", "مطرح", "بوشر", "السيب", "العامرات", "قريات")

    fun places() = Places(PLACES, ZONES, KINDS, Point(23.5900, 58.4000))


    private fun r3(v: Double): Double = ((v + 1e-9) * 1000).roundToLong() / 1000.0


    private fun minutesForKm(km: Double): Int = max(4, (km / 22.0 * 60).roundToInt())


    fun zoneFor(lat: Double, lon: Double): String =
        PLACES.minByOrNull { kmBetween(lat, lon, it.lat, it.lon) }?.zone ?: "مسقط"

    fun quote(pickup: Point, dropoff: Point, kind: String, hour: Int = nowHour()): Price {
        val km = (kmBetween(pickup.lat, pickup.lon, dropoff.lat, dropoff.lon) * 10)
            .roundToLong() / 10.0
        val minutes = minutesForKm(km)

        val distance = km * PER_KM
        val timeCost = minutes * PER_MINUTE
        val zPickup = pickup.zone.ifBlank { zoneFor(pickup.lat, pickup.lon) }
        val zDrop = dropoff.zone.ifBlank { zoneFor(dropoff.lat, dropoff.lon) }
        val surcharge = max(ZONE_SURCHARGE[zPickup] ?: 0.0, ZONE_SURCHARGE[zDrop] ?: 0.0)

        val preKind = BASE_FARE + distance + timeCost + surcharge
        val kindAdj = preKind * ((KIND_MULTIPLIER[kind] ?: 1.0) - 1)

        val isPeak = hour in PEAK_HOURS
        val surge = if (isPeak) (preKind + kindAdj) * (PEAK_MULTIPLIER - 1) else 0.0

        val subtotal = max(preKind + kindAdj + surge, MINIMUM_FARE)
        val total = r3(max(0.0, subtotal))
        val commission = r3(min(total, max(MIN_COMMISSION, FIXED_COMMISSION)))

        return Price(
            km = km, minutes = minutes,
            base = r3(BASE_FARE), distance = r3(distance), time = r3(timeCost),
            zoneSurcharge = r3(surcharge), kindAdjustment = r3(kindAdj),
            surge = r3(surge), discount = 0.0, voucherTitle = null,
            total = total, commission = commission,
            driverPayout = r3(total - commission), isPeak = isPeak,
        )
    }

    private fun nowHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)


    fun me() = Me(
        phone = "96890000000",
        name = "حساب تجربة",
        isOwner = true,
        customer = Customer("demo-c", "حساب تجربة", walletBalance = 12.500,
            points = 140, lifetimePoints = 140),
        driver = Driver("demo-d", "حساب تجربة", approval = "approved",
            presence = "offline", earningsBalance = 3.200, completedOrders = 18),
        nextPayout = "—",
    )

    fun wallet() = Wallet(
        balance = 12.500, points = 140, lifetimePoints = 140,
        rewards = listOf(
            Reward("r1", "خصم ١٠٪", 100, "على طلبك القادم، بحدّ ٠.٥٠٠ ر.ع.", true),
            Reward("r2", "خصم ٢٥٪", 500, "على طلبك القادم، بحدّ ١.٠٠٠ ر.ع.", false),
        ),
    )

    fun tier() = Tier("bronze", "برونزي", FIXED_COMMISSION, 140, "فضي", 60)
}
