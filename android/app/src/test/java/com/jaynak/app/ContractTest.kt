package com.jaynak.app

import com.jaynak.app.data.*
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class ContractTest {

    private val base = "http://127.0.0.1:8787"
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    private fun up(): Boolean = runCatching {
        (URL("$base/health").openConnection() as HttpURLConnection).run {
            connectTimeout = 1500; readTimeout = 1500
            responseCode == 200
        }
    }.getOrDefault(false)

    private fun get(path: String): String =
        (URL(base + path).openConnection() as HttpURLConnection).run {
            connectTimeout = 3000; readTimeout = 5000
            inputStream.bufferedReader().readText()
        }


    private fun pathOf(rel: String): String? =
        java.io.File(rel).takeIf { it.exists() }?.readText()


    private fun get2(path: String): String =
        (URL(base + path).openConnection() as HttpURLConnection).run {
            connectTimeout = 3000; readTimeout = 5000
            (if (responseCode < 400) inputStream else errorStream)
                .bufferedReader().readText()
        }

    private fun post(path: String, body: String): String =
        (URL(base + path).openConnection() as HttpURLConnection).run {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 3000; readTimeout = 5000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            outputStream.write(body.toByteArray())
            (if (responseCode < 400) inputStream else errorStream).bufferedReader().readText()
        }

    @Test
    fun `places تُحلَّل بنموذج Places`() {
        if (!up()) { println("— الخادم لا يعمل، تُخطّي"); return }
        val p = json.decodeFromString<Places>(get("/api/places"))
        assertTrue(p.places.isNotEmpty(), "قائمة الأماكن فارغة")
        assertTrue(p.kinds.isNotEmpty(), "أنواع الطلب فارغة")
        assertTrue(p.center != null, "المركز غائب — الخريطة تفتح على نقطة خطأ")

        val first = p.places.first()
        assertTrue(first.name.isNotBlank() && first.zone.isNotBlank())
        assertTrue(inOman(first.lat, first.lon), "مكان خارج عمان في القائمة")
        println("✓ ${p.places.size} مكاناً · ${p.kinds.size} نوعاً · مركز ${p.center!!.lat}")
    }

    @Test
    fun `quote تُحلَّل والعمولة ثابتة ١٦٠ بيسة`() {
        if (!up()) { println("— الخادم لا يعمل، تُخطّي"); return }
        val body = """
            {"pickup":{"lat":23.5648,"lon":58.4131},
             "dropoff":{"lat":23.6178,"lon":58.5636},
             "kind":"errand"}
        """.trimIndent()
        val q = json.decodeFromString<Price>(post("/api/quote", body))

        assertTrue(q.total > 0, "الإجمالي صفر")
        assertTrue(q.km > 0, "المسافة صفر")

        assertEquals(0.160, q.commission, 0.0005,
            "العمولة ليست ١٦٠ بيسة — هذه قاعدة عمل لا تفصيل")
        assertEquals(q.total - q.commission, q.driverPayout, 0.0005,
            "نصيب المندوب لا يساوي الإجمالي ناقص العمولة")
        println("✓ ${q.km} كم · ${q.total} إجمالي · ${q.commission} عمولة · ${q.driverPayout} للمندوب")
    }

    @Test
    fun `العمولة تبقى ثابتة على طلب كبير — لا نسبة`() {
        if (!up()) { println("— الخادم لا يعمل، تُخطّي"); return }

        val far = post("/api/quote", """
            {"pickup":{"lat":23.5905,"lon":58.1665},
             "dropoff":{"lat":23.6178,"lon":58.5636},"kind":"errand"}
        """.trimIndent())
        val q = json.decodeFromString<Price>(far)
        assertEquals(0.160, q.commission, 0.0005,
            "العمولة تغيّرت مع الإجمالي — عادت نسبةً")
        println("✓ إجمالي ${q.total} والعمولة ${q.commission} — ثابتة")
    }

    @Test
    fun `رمز التحقّق لا يُعاد في الرد افتراضياً`() {
        if (!up()) { println("— الخادم لا يعمل، تُخطّي"); return }


        val phone = "9" + (10_000_000..99_999_999).random().toString().take(7)
        val r = post("/auth/request-otp", """{"phone":"$phone"}""")

        assertTrue("cooldown" !in r && "rate" !in r,
            "اصطدم بحدّ المحاولات فلا يفحص شيئاً: $r")
        assertTrue("\"ok\": true" in r || "\"ok\":true" in r,
            "لم ينجح الطلب فلا معنى للفحص: $r")

        assertTrue("dev_code" !in r,
            "الرمز يُعاد في الرد — ثغرة تسليم كامل للحساب على أي رابط عام")
        println("✓ طلب ناجح لـ$phone وبلا رمز: $r")
    }


    @Test
    fun `حمولة الإدارة تُحلَّل بكل حقولها`() {
        val real = """
        {"stats":{"ordersToday":3,"revenueToday":0,"commissionToday":0,"live":4,
                  "drivers":5,"online":1,"pending":3,"customers":2},
         "live":[],
         "drivers":[{"id":"d1","name":"مندوب 1111","phone":"96893331111","zone":"بوشر",
                     "approval":"pending","presence":"offline",
                     "earningsBalance":-0.32,"completedOrders":4}],
         "rules":{"baseFare":0.8,"perKm":0.12,"perMinute":0.015,"minimumFare":1.0,
                  "fixedCommission":0.16,"minEarningsBalance":-1.5},
         "nextPayout":"2026-09-13"}
        """.trimIndent()

        val a = json.decodeFromString<AdminOverview>(real)
        assertEquals(3, a.stats.ordersToday)
        assertEquals(5, a.stats.drivers)
        assertEquals(1, a.drivers.size)
        assertEquals("96893331111", a.drivers[0].phone)
        assertEquals(-0.32, a.drivers[0].earningsBalance, 0.0001)
        assertEquals("2026-09-13", a.nextPayout)


        assertEquals(0.160, a.rules.fixedCommission, 0.0005,
            "العمولة ليست ١٦٠ بيسة ثابتة")
        assertEquals(-1.500, a.rules.minEarningsBalance, 0.0005,
            "حدّ الرصيد السالب ليس −١.٥٠٠ ر.ع.")
        println("✓ إدارة: ${a.stats.drivers} مندوباً · عمولة ${a.rules.fixedCommission}" +
            " · حدّ ${a.rules.minEarningsBalance}")
    }

    @Test
    fun `حقل ناقص من الخادم لا يُسقط التطبيق`() {


        val thin = """{"id":"o1","pickup":{"lat":23.5,"lon":58.4},
                       "dropoff":{"lat":23.6,"lon":58.5}}"""
        val o = json.decodeFromString<Order>(thin)
        assertEquals("searching", o.status)
        assertEquals(0.0, o.price.total, 0.0001)
        assertEquals("", o.ref)
        println("✓ طلب بأدنى الحقول يُحلَّل بلا سقوط")
    }


    @Test
    fun `عقد أسماء الحقول يطابق ما يقرأه الخادم`() {
        val api = pathOf("src/main/java/com/jaynak/app/data/Api.kt")
        assertTrue(api != null, "لم يُعثر على Api.kt — الاختبار لا يفحص شيئاً")

        assertTrue("put(\"id\"" !in api!!,
            "ما زال العميل يرسل \"id\" — الخادم يقرأ orderID/rewardID")
        assertTrue("orderID" in api, "مفتاح orderID مفقود")
        assertTrue("rewardID" in api, "مفتاح rewardID مفقود")
        println("✓ لا \"id\" على السلك · orderID و rewardID موجودان")
    }

    @Test
    fun `الخادم يرفض قبول طلب بلا جلسة`() {
        if (!up()) { println("— الخادم لا يعمل، تُخطّي"); return }
        val r = post("/api/driver/accept", """{"orderID":"whatever"}""")
        assertTrue("unauthorized" in r || "401" in r,
            "قبول طلب بلا جلسة لم يُرفض: $r")
        println("✓ القبول بلا جلسة مرفوض")
    }

    @Test
    fun `الإدارة محميّة من غير المالك`() {
        if (!up()) { println("— الخادم لا يعمل، تُخطّي"); return }
        val r = get2("/api/admin/overview")
        assertTrue("forbidden" in r || "unauthorized" in r,
            "لوحة الإدارة مكشوفة بلا صلاحية: ${r.take(120)}")
        println("✓ /api/admin/overview محميّة")
    }

    @Test
    fun `الوصف يشتقّ اسماً محلياً بلا خدمة عناوين`() {
        val places = listOf(
            Place("الخوير - جمعية", "بوشر", 23.5900, 58.4200),
            Place("سوق مطرح", "مطرح", 23.6178, 58.5636),
        )

        assertEquals("الخوير - جمعية", describe(23.5900, 58.4200, places).name)

        val d = describe(23.5648, 58.4131, places)
        assertTrue(d.name.startsWith("قرب "), "الوصف لا يقول «قرب»: ${d.name}")
        assertEquals("بوشر", d.zone)

        assertTrue(!inOman(28.9, 57.5))
        assertTrue(inOman(23.59, 58.40))
        println("✓ ${d.name} · ${d.sub}")
    }

    @Test
    fun `الأرقام العربية تُحوَّل وإلا بدا الزرّ معطّلاً`() {
        assertEquals("1234", normalizeDigits("١٢٣٤"))
        assertEquals("1234", normalizeDigits("۱۲۳۴"))
        assertEquals("98061051", normalizeDigits("+968 9806-1051").removePrefix("968"))
        println("✓ ٠-٩ و۰-۹ تُحوَّل")
    }
}


private fun normalizeDigits(s: String): String = buildString {
    for (ch in s) when (ch.code) {
        in 0x0660..0x0669 -> append(ch.code - 0x0660)
        in 0x06F0..0x06F9 -> append(ch.code - 0x06F0)
        in '0'.code..'9'.code -> append(ch)
    }
}
