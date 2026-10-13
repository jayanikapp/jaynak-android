package com.jaynak.app.core

import android.app.Application
import com.jaynak.app.BuildConfig
import com.jaynak.app.data.SecureStore
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** يحمل سياق التطبيق للأجزاء التي لا تملك Context (مقابل static في iOS). */
object Boot {
    lateinit var app: Application
}

/** مقابل AppConfig.swift — نفس الثوابت والقيم حرفياً. */
object AppConfig {

    const val countryName = "سلطنة عمان"
    const val cityName = "مسقط"
    const val countryDialCode = "+968"
    const val nationalNumberLength = 8

    const val civilIDLength = 8
    const val currencyCode = "OMR"
    const val currencySymbol = "ر.ع."

    const val currencyFractionDigits = 3

    const val platformCommissionRate: Double = 0.18

    const val demoAdminPhone = "98061051"

    const val ownerPhone = "94202204"

    /** DEBUG أو DEMO_ACCESS فقط — مقابل #if DEBUG / DEMO_ACCESS. */
    val demoAdminBypassEnabled: Boolean
        get() = BuildConfig.DEBUG || BuildConfig.DEMO_ACCESS

    const val otpLength = 4
    const val otpResendSeconds = 45

    const val dispatchFanout = 3
    const val offerTimeoutSeconds = 20.0

    val builtInAPIBase: String get() = productionAPIBase

    const val productionAPIBase = "https://jayanikapp.onrender.com"

    private val store: SecureStore by lazy { SecureStore(Boot.app) }

    var sessionToken: String?
        get() = store.token
        set(v) { store.token = v?.takeIf { it.isNotEmpty() } }

    /** العنوان لم يعد يُضبط من داخل التطبيق — نفس سلوك iOS. */
    val apiBaseURLString: String get() = builtInAPIBase

    /** عنوان الخادم المعتمد بعد التحقّق، أو null إن لم يصلح. */
    val apiBaseURL: String?
        get() {
            val raw = apiBaseURLString.trim()
            if (raw.isEmpty()) return null
            return validateAPIBase(raw)
        }

    fun validateAPIBase(value: String): String? {
        var v = value.trim()
        if (v.isEmpty()) return null
        while (v.endsWith("/")) v = v.dropLast(1)

        val sep = v.indexOf("://")
        v = if (sep >= 0) {
            v.substring(0, sep).lowercase() + "://" + v.substring(sep + 3)
        } else {
            "http://$v"
        }
        val uri = runCatching { java.net.URI(v) }.getOrNull() ?: return null
        val scheme = uri.scheme ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host ?: return null
        if (host.isEmpty()) return null
        if (!host.contains(".") && host != "localhost") return null
        return v
    }

    const val privacyPolicyURL = "http://jayanik.om/privacy-policy"
    const val refundPolicyURL = "http://jayanik.om/refund-policy"
    const val termsURL = "http://jayanik.om/terms"
    const val supportEmail = "jayanikapp2@gmail.com"
    const val supportPhone = "+968 76004660"

    const val termsChangeNoticeDays = 7
    const val minDriverAge = 21
}

/** أرقام لاتينية — أرقام الهند الشرقية والفارسية تتحوّل إلى 0-9. */
val String.latinDigits: String
    get() = buildString(length) {
        for (ch in this@latinDigits) {
            val v = ch.code
            when (v) {
                in 0x0660..0x0669 -> append(('0'.code + (v - 0x0660)).toChar())
                in 0x06F0..0x06F9 -> append(('0'.code + (v - 0x06F0)).toChar())
                else -> append(ch)
            }
        }
    }

val String.digitsOnly: String get() = latinDigits.filter { it.isDigit() }

private val usSymbols: DecimalFormatSymbols = DecimalFormatSymbols(Locale.US)

val Double.money: String
    get() {
        val f = DecimalFormat("#,##0.000", usSymbols)
        return "${f.format(this)} ${AppConfig.currencySymbol}"
    }

val Double.moneyShort: String
    get() {
        val f = DecimalFormat(if (this < 100) "#,##0.###" else "#,##0", usSymbols)
        return f.format(this) + " " + AppConfig.currencySymbol
    }

/** تقريب «بعيداً عن الصفر» كما يفعل .rounded() في Swift (لا «الأقرب للزوجي»). */
fun Double.roundedAway(): Double =
    if (this >= 0) kotlin.math.floor(this + 0.5) else kotlin.math.ceil(this - 0.5)

fun round3(v: Double): Double = (v * 1000).roundedAway() / 1000
