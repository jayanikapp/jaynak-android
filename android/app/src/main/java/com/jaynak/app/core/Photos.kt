package com.jaynak.app.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateMapOf
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** اسم ← رمز صورة البروفايل. `Avatar(name)` يستعمله تلقائياً فتظهر الصور في كل الشاشات. */
object AvatarRegistry {
    val map = mutableStateMapOf<String, String>()
    fun get(name: String): String? = map[name.trim()]?.takeIf { it.isNotEmpty() }
    fun put(name: String, token: String) {
        val n = name.trim()
        if (n.isNotEmpty() && token.isNotEmpty() && map[n] != token) map[n] = token
    }
}

/** بيانات إضافية للطلب تأتي من السيرفر: أسماء/صور الطرفين وصور إثبات الاستلام والتسليم. */
object OrderExtras {
    data class X(
        val customerName: String = "", val driverName: String = "", val recipientName: String = "",
        val proofPickup: String = "", val proofDelivery: String = "",
        val proofPickupAt: Double = 0.0, val proofDeliveryAt: Double = 0.0,
    )

    val map = mutableStateMapOf<String, X>()
    fun of(remoteID: String?): X? = remoteID?.let { map[it] }

    /** يمسح أي ردّ من السيرفر ويلتقط الصور والأسماء منه. */
    fun scan(v: Any?) {
        when (v) {
            is JSONObject -> {
                val nm = v.optString("name"); val av = v.optString("avatar")
                if (nm.isNotBlank() && av.isNotBlank()) AvatarRegistry.put(nm, av)
                val id = v.optString("id")
                if (id.isNotEmpty() && (v.has("proofPickup") || v.has("customerName"))) {
                    AvatarRegistry.put(v.optString("customerName"), v.optString("customerAvatar"))
                    AvatarRegistry.put(v.optString("driverName"), v.optString("driverAvatar"))
                    val x = X(
                        v.optString("customerName"), v.optString("driverName"), v.optString("recipientName"),
                        v.optString("proofPickup"), v.optString("proofDelivery"),
                        v.optDouble("proofPickupAt", 0.0), v.optDouble("proofDeliveryAt", 0.0),
                    )
                    if (map[id] != x) map[id] = x
                }
                val ks = v.keys()
                while (ks.hasNext()) scan(v.opt(ks.next()))
            }
            is JSONArray -> for (i in 0 until v.length()) scan(v.opt(i))
        }
    }
}

object PhotoApi {
    class Failed(message: String) : Exception(message)

    private val cache = LruCache<String, Bitmap>(48)
    private var lastMe = 0L

    /** يرفع صورة JPEG خام: kind = avatar | pickup | delivery. يعيد رمز الصورة. */
    suspend fun upload(kind: String, bytes: ByteArray, orderID: String? = null, role: String? = null): String {
        val base = AppConfig.apiBaseURL ?: throw Failed("لا يوجد اتصال بالسيرفر")
        val token = AppConfig.sessionToken ?: throw Failed("سجّل الدخول أولاً")
        val q = "api/photo?kind=$kind" + (orderID?.let { "&orderID=$it" } ?: "") + (role?.let { "&role=$it" } ?: "")
        val req = Request.Builder().url(Net.url(base, q))
            .header("Authorization", "Bearer $token")
            .post(bytes.toRequestBody("image/jpeg".toMediaType())).build()
        val res = try { Net.exec(req) } catch (e: java.io.IOException) { throw Failed("تعذّر الوصول للسيرفر — تأكد من الإنترنت") }
        if (res.status !in 200..299) {
            throw Failed(res.json.optString("message").ifEmpty { "فشل رفع الصورة (${res.status})" })
        }
        return res.json.optString("id")
    }

    fun cached(token: String): Bitmap? = cache.get(token)

    suspend fun bitmap(token: String): Bitmap? {
        cache.get(token)?.let { return it }
        val base = AppConfig.apiBaseURL ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(Net.url(base, "photo/$token")).get().build()
                Net.client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@use null
                    val b = r.body?.bytes() ?: return@use null
                    val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
                    BitmapFactory.decodeByteArray(b, 0, b.size, opts)?.also { cache.put(token, it) }
                }
            }.getOrNull()
        }
    }

    /** يحدّث صورة الحساب الشخصية (يلتقطها السجل من ردّ api/me). */
    suspend fun pulseMe(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastMe < 60_000) return
        lastMe = now
        runCatching { OrdersService.send("api/me", "GET", null) }
    }
}
