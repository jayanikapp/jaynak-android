package com.jaynak.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit


class Api(private val store: SecureStore) {

    private val demoStore = DemoStore(store)

    private val http = OkHttpClient.Builder()


        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    val base: String get() = store.serverUrl


    val demo: Boolean get() = store.demo


    private suspend fun call(
        path: String,
        method: String = "GET",
        body: JsonObject? = null,
    ): JsonObject = withContext(Dispatchers.IO) {
        if (base.isEmpty()) throw ApiError("no_server", "لم يُضبط عنوان الخادم")

        val req = Request.Builder().url(base + path)
        store.token?.let { req.header("Authorization", "Bearer $it") }
        if (body != null || method != "GET") {
            req.method(
                method,
                (body?.toString() ?: "{}").toRequestBody("application/json; charset=utf-8".toMediaType()),
            )
        }

        val res = try {
            http.newCall(req.build()).execute()
        } catch (e: IOException) {


            throw ApiError("offline", "لا يوجد اتصال — تحقّق من الشبكة وأعد المحاولة")
        }

        res.use {
            val text = it.body?.string().orEmpty()
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }
                .getOrElse { JsonObject(emptyMap()) }

            if (it.code == 401) {
                store.signOut()
                throw ApiError("unauthorized", msg(obj) ?: "انتهت جلستك، سجّل الدخول من جديد")
            }
            if (!it.isSuccessful) {
                throw ApiError(
                    obj["error"]?.jsonPrimitive?.content ?: "http_${it.code}",
                    msg(obj) ?: "تعذّر إكمال الطلب",
                )
            }
            obj
        }
    }

    private fun msg(o: JsonObject): String? = o["message"]?.jsonPrimitive?.content


    private fun orderBody(orderId: String): JsonObject =
        buildJsonObject { put("orderID", JsonPrimitive(orderId)) }

    private inline fun <reified T> decode(o: JsonObject): T = json.decodeFromJsonElement<T>(o)


    suspend fun requestOtp(phone: String): String? {
        val o = call("/auth/request-otp", "POST", buildJsonObject {
            put("phone", JsonPrimitive(phone))
        })
        return o["dev_code"]?.jsonPrimitive?.content
    }

    suspend fun verifyOtp(phone: String, code: String, role: String): Me {
        val o = call("/auth/verify-otp", "POST", buildJsonObject {
            put("phone", JsonPrimitive(phone))
            put("code", JsonPrimitive(code))
            put("role", JsonPrimitive(role))
        })
        o["token"]?.jsonPrimitive?.content?.let {
            store.token = it
            store.phone = phone
        }
        return me()
    }

    suspend fun me(): Me = if (demo) Demo.me() else decode(call("/api/me"))


    suspend fun signOut() {
        if (!demo && store.token != null) {
            runCatching { call("/auth/logout", "POST") }
        }
        store.signOut()
    }
    val signedIn: Boolean get() = store.demo || store.token != null


    fun enterDemo() { store.demo = true; store.serverUrl = "" }


    fun leaveDemo() = store.leaveDemo()


    suspend fun places(): Places = if (demo) Demo.places() else decode(call("/api/places"))

    suspend fun quote(pickup: Point, dropoff: Point, kind: String): Price {
        if (demo) return Demo.quote(pickup, dropoff, kind)
        return decode(call("/api/quote", "POST", buildJsonObject {
            put("pickup", json.encodeToJsonElement(Point.serializer(), pickup))
            put("dropoff", json.encodeToJsonElement(Point.serializer(), dropoff))
            put("kind", JsonPrimitive(kind))
        }))
    }

    suspend fun createOrder(
        pickup: Point, dropoff: Point, kind: String,
        payment: String, dispatchMode: String, note: String,
    ): Order {
        if (demo) return demoStore.create(pickup, dropoff, kind, payment, dispatchMode, note)
        val o = call("/api/orders", "POST", buildJsonObject {
            put("pickup", json.encodeToJsonElement(Point.serializer(), pickup))
            put("dropoff", json.encodeToJsonElement(Point.serializer(), dropoff))
            put("kind", JsonPrimitive(kind))
            put("payment", JsonPrimitive(payment))
            put("dispatchMode", JsonPrimitive(dispatchMode))
            put("note", JsonPrimitive(note))
        })
        return json.decodeFromJsonElement(Order.serializer(), o["order"]!!)
    }

    suspend fun orders(): List<Order> {
        if (demo) return demoStore.all()
        val o = call("/api/orders")
        return o["orders"]?.let {
            json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(Order.serializer()), it)
        } ?: emptyList()
    }

    suspend fun order(id: String): Order =
        if (demo) demoStore.one(id) else decode(call("/api/orders/$id"))

    suspend fun cancelOrder(id: String) {
        if (demo) return demoStore.cancel(id)
        call("/api/orders/cancel", "POST", orderBody(id))
    }

    suspend fun wallet(): Wallet = if (demo) Demo.wallet() else decode(call("/api/wallet"))

    suspend fun redeem(rewardId: String) {
        if (demo) return
        call("/api/rewards/redeem", "POST", buildJsonObject {
            put("rewardID", JsonPrimitive(rewardId))
        })
    }


    suspend fun driverFeed(): DriverFeed =
        if (demo) demoStore.feed() else decode(call("/api/driver/feed"))

    suspend fun setPresence(online: Boolean) {
        if (demo) return demoStore.setPresence(online)
        call("/api/driver/presence", "POST", buildJsonObject {
            put("presence", JsonPrimitive(if (online) "online" else "offline"))
        })
    }

    suspend fun accept(orderId: String) {
        if (demo) return demoStore.accept(orderId)
        call("/api/driver/accept", "POST", orderBody(orderId))
    }

    suspend fun decline(orderId: String) {
        if (demo) return demoStore.decline(orderId)
        call("/api/driver/decline", "POST", orderBody(orderId))
    }

    suspend fun advance(orderId: String) {
        if (demo) return demoStore.advance(orderId)
        call("/api/driver/advance", "POST", orderBody(orderId))
    }


    suspend fun adminOverview(): AdminOverview =
        if (demo) demoStore.admin() else decode(call("/api/admin/overview"))

    suspend fun approve(driverId: String, state: String, note: String = "") {
        call("/api/admin/approve", "POST", buildJsonObject {
            put("driverID", JsonPrimitive(driverId))
            put("approval", JsonPrimitive(state))
            if (note.isNotBlank()) put("note", JsonPrimitive(note))
        })
    }


}
