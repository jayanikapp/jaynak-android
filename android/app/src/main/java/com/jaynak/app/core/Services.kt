package com.jaynak.app.core

import com.jaynak.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

// ───────── نقل مشترك (مقابل URLSession) ─────────

internal object Net {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val jsonType = "application/json; charset=utf-8".toMediaType()

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    class Res(val status: Int, val json: JSONObject)

    /** ينفّذ الطلب؛ يرمي IOException عند فشل الشبكة. */
    suspend fun exec(req: Request, c: OkHttpClient = client): Res = withContext(Dispatchers.IO) {
        c.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            val obj = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            Res(r.code, obj)
        }
    }

    fun url(base: String, path: String): String = base.trimEnd('/') + "/" + path.trimStart('/')
}

internal fun JSONObject.str(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key)

// ───────── OTPService.swift ─────────

object OTPService {

    data class Challenge(
        val phone: String,
        val sentAt: Long,
        val expiresIn: Double = 300.0,
        val resendAfter: Int = AppConfig.otpResendSeconds,
        val attempts: Int = 0,
        val channel: String = "WhatsApp",

        /** يظهر فقط عندما يكون الخادم في وضع التطوير. */
        val devCode: String? = null,

        /** رمز مولَّد محلياً (DEBUG بلا خادم). */
        val localCode: String? = null,

        /** معرّف تحقّق Firebase. */
        val verificationId: String? = null,
    ) {
        val expired: Boolean get() = (nowMs() - sentAt) / 1000.0 > expiresIn
        val isRemote: Boolean get() = localCode == null
    }

    sealed class Failure(override val message: String) : Exception(message) {
        object InvalidPhone : Failure("رقم غير صالح")
        class Cooldown(val seconds: Int) : Failure("انتظر $seconds ثانية قبل إعادة الإرسال")
        class RateLimited(m: String) : Failure(m)
        class SendFailed(m: String) : Failure(m)
        class WrongCode(val left: Int?) : Failure(
            if (left != null && left > 0) "رمز غير صحيح — بقيت $left محاولات" else "رمز غير صحيح",
        )
        object Expired : Failure("انتهت صلاحية الرمز، أعد الإرسال")
        class Unreachable(m: String) : Failure(m)
    }

    suspend fun send(phone: String): Challenge {
        if (AppConfig.firebaseEnabled) {
            val id = FirebasePhone.send(phone)
            return Challenge(phone = phone, sentAt = nowMs(), expiresIn = 120.0, channel = "SMS", localCode = null, verificationId = id)
        }
        val base = AppConfig.apiBaseURL
        if (base == null) {
            // بلا خادم: في بناء DEBUG نولّد رمزاً محلياً، وفي الإنتاج خطأ صريح.
            if (BuildConfig.DEBUG) return localChallenge(phone)
            throw Failure.Unreachable("لم يُضبط عنوان الخادم")
        }

        val json = post("auth/request-otp", base, JSONObject().put("phone", phone))
        return Challenge(
            phone = phone,
            sentAt = nowMs(),
            expiresIn = json.optDouble("expires_in", 300.0),
            resendAfter = json.optInt("resend_after", AppConfig.otpResendSeconds),
            devCode = json.str("dev_code"),
            localCode = null,
        )
    }

    suspend fun verify(code: String, challenge: Challenge): Boolean {
        val vid = challenge.verificationId
        if (AppConfig.firebaseEnabled && vid != null) {
            val idToken = FirebasePhone.verify(code.digitsOnly, vid)
            val base = AppConfig.apiBaseURL
            if (base != null) {
                try {
                    post("auth/firebase", base, JSONObject().put("id_token", idToken)).str("token")
                        ?.let { AppConfig.sessionToken = it }
                    AppConfig.lastAuthError = null
                } catch (e: Exception) {
                    AppConfig.lastAuthError = e.message ?: e.javaClass.simpleName
                }
            }
            return true
        }
        if (BuildConfig.DEBUG) {
            val local = challenge.localCode
            if (local != null) {
                if (challenge.expired) throw Failure.Expired
                if (code.digitsOnly != local) throw Failure.WrongCode(null)
                return true
            }
        }

        val base = AppConfig.apiBaseURL ?: throw Failure.Unreachable("لا يوجد سيرفر مضبوط")
        val json = post(
            "auth/verify-otp", base,
            JSONObject().put("phone", challenge.phone).put("code", code.digitsOnly),
        )
        json.str("token")?.let { AppConfig.sessionToken = it }
        return true
    }

    /** يفحص الخادم — يعيد وضعه أو null. */
    suspend fun health(base: String): String? {
        val req = Request.Builder().url(Net.url(base, "health")).build()
        val c = Net.client.newBuilder().readTimeout(6, TimeUnit.SECONDS).build()
        return try {
            val res = Net.exec(req, c)
            if (res.status != 200) null else (res.json.str("mode") ?: "ok")
        } catch (e: IOException) {
            null
        }
    }

    private fun localChallenge(phone: String): Challenge {
        val code = (0 until AppConfig.otpLength).joinToString("") { Random.nextInt(0, 10).toString() }
        return Challenge(phone = phone, sentAt = nowMs(), devCode = code, localCode = code)
    }

    private suspend fun post(path: String, base: String, body: JSONObject): JSONObject {
        val req = Request.Builder()
            .url(Net.url(base, path))
            .post(body.toString().toRequestBody(Net.jsonType))
            .build()
        val res = try {
            Net.exec(req)
        } catch (e: IOException) {
            throw Failure.Unreachable("تعذّر الوصول للسيرفر — تأكد أنه يعمل وأنك على نفس الشبكة")
        }
        if (res.status != 200) throw mapError(res.status, res.json)
        return res.json
    }

    private fun mapError(status: Int, json: JSONObject): Failure {
        val message = json.str("message") ?: "حدث خطأ غير متوقّع"
        return when (json.str("error")) {
            "invalid_phone", "invalid_input" -> Failure.InvalidPhone
            "cooldown" -> Failure.Cooldown(json.optInt("retry_after", 45))
            "rate_limited", "too_many_attempts" -> Failure.RateLimited(message)
            "send_failed" -> Failure.SendFailed(message)
            "wrong_code" -> Failure.WrongCode(if (json.has("attempts_left") && !json.isNull("attempts_left")) json.optInt("attempts_left") else null)
            "expired", "no_challenge" -> Failure.Expired
            else -> if (status >= 500) Failure.SendFailed(message) else Failure.RateLimited(message)
        }
    }
}

// ───────── OrdersService.swift ─────────

/** طبقة الشبكة للطلبات — تقابل نقاط الخادم نفسها التي يستعملها iOS. */
object OrdersService {

    sealed class Failure(override val message: String) : Exception(message) {
        object NoServer : Failure("لا يوجد سيرفر مضبوط")
        object Unauthorized : Failure("انتهت الجلسة — سجّل الدخول مرة ثانية")
        class Unreachable(m: String) : Failure(m)
        class Server(m: String) : Failure(m)
    }

    @Serializable
    data class Point(
        val lat: Double,
        val lon: Double,
        val zone: String = "",
        val name: String = "",
    )

    @Serializable
    data class RemotePrice(
        val km: Double = 0.0,
        val minutes: Int = 0,
        val base: Double = 0.0,
        val distance: Double = 0.0,
        val time: Double = 0.0,
        val total: Double = 0.0,
        val commission: Double = 0.0,
        val driverPayout: Double = 0.0,
    )

    @Serializable
    data class RemoteOrder(
        val id: String,
        val ref: String = "",
        val kind: String = "errand",
        val status: String = "searching",
        val pickup: Point,
        val dropoff: Point,
        val price: RemotePrice = RemotePrice(),
        val payment: String = "cash",
        val note: String = "",
        val createdAt: Double = 0.0,
        val driverID: String? = null,
        val customerPhone: String = "",

        /** تأتي في تغذية المندوب فقط */
        val km: Double? = null,
        val onPath: Boolean? = null,
        val dispatchMode: String = "nearby",
    )

    data class Feed(
        val offers: List<RemoteOrder> = emptyList(),
        val board: List<RemoteOrder> = emptyList(),
        val active: RemoteOrder? = null,
        val actives: List<RemoteOrder> = emptyList(),
    )

    // ───────── الزبون ─────────

    suspend fun create(
        kind: String, pickup: Point, dropoff: Point,
        payment: String, note: String,
        recipientPhone: String,
        dispatchMode: String,
        recipientName: String = "",
    ): RemoteOrder {
        val body = JSONObject()
            .put("kind", kind)
            .put("pickup", encode(pickup))
            .put("dropoff", encode(dropoff))
            .put("payment", payment)
            .put("note", note)
            .put("recipientPhone", recipientPhone)
            .put("recipientName", recipientName)
            .put("dispatchMode", dispatchMode)
        val json = send("api/orders", "POST", body)
        val raw = json.optJSONObject("order") ?: throw Failure.Server("ردّ غير متوقّع من السيرفر")
        return Net.json.decodeFromString(RemoteOrder.serializer(), raw.toString())
    }

    suspend fun mine(): List<RemoteOrder> {
        val json = send("api/orders", "GET", null)
        val arr = json.optJSONArray("orders") ?: return emptyList()
        return (0 until arr.length()).mapNotNull {
            runCatching {
                Net.json.decodeFromString(RemoteOrder.serializer(), arr.getJSONObject(it).toString())
            }.getOrNull()
        }
    }

    suspend fun cancel(orderID: String) {
        send("api/orders/cancel", "POST", JSONObject().put("orderID", orderID))
    }

    // ───────── المندوب ─────────

    suspend fun feed(): Feed {
        val json = send("api/driver/feed", "GET", null)
        fun list(key: String): List<RemoteOrder> {
            val arr = json.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).mapNotNull {
                runCatching {
                    Net.json.decodeFromString(RemoteOrder.serializer(), arr.getJSONObject(it).toString())
                }.getOrNull()
            }
        }
        val active = json.optJSONObject("active")?.let {
            runCatching { Net.json.decodeFromString(RemoteOrder.serializer(), it.toString()) }.getOrNull()
        }
        val actives = list("actives").ifEmpty { listOfNotNull(active) }
        return Feed(offers = list("offers"), board = list("board"), active = active, actives = actives)
    }

    suspend fun chatSend(id: String, thread: String, body: String, role: String) {
        send("api/chat/send", "POST",
            JSONObject().put("id", id).put("thread", thread).put("body", body).put("role", role))
    }

    suspend fun chatList(since: Double): JSONObject =
        send("api/chat?since=$since", "GET", null)

    suspend fun chatClose(thread: String) {
        send("api/chat/close", "POST", JSONObject().put("thread", thread))
    }

    suspend fun chatTransfer(thread: String, dept: String) {
        send("api/chat/transfer", "POST", JSONObject().put("thread", thread).put("dept", dept))
    }

    suspend fun fetchRules(): JSONObject? =
        send("api/rules", "GET", null).optJSONObject("rules")

    /** سحب أرباح المندوب: الرصيد وسجل الطلبات وأيام السحب. */
    suspend fun withdrawals(): JSONObject = send("api/driver/withdrawals", "GET", null)

    suspend fun withdraw(amount: Double, bankName: String, holder: String, account: String): JSONObject =
        send("api/driver/withdraw", "POST", JSONObject()
            .put("amount", amount).put("bankName", bankName)
            .put("holder", holder).put("account", account))

    suspend fun saveRules(body: JSONObject) {
        send("api/admin/rules", "POST", body)
    }

    suspend fun setPresence(online: Boolean) {
        send("api/driver/presence", "POST",
            JSONObject().put("presence", if (online) "online" else "offline"))
    }

    suspend fun accept(orderID: String) {
        send("api/driver/accept", "POST", JSONObject().put("orderID", orderID))
    }

    suspend fun driverCancel(orderID: String) {
        send("api/driver/cancel", "POST", JSONObject().put("orderID", orderID))
    }

    suspend fun decline(orderID: String) {
        send("api/driver/decline", "POST", JSONObject().put("orderID", orderID))
    }

    /** ينقل الطلب للحالة التالية: pickedUp ← accepted، delivered ← pickedUp. */
    suspend fun advance(orderID: String, expect: String? = null) {
        val b = JSONObject().put("orderID", orderID)
        if (expect != null) b.put("expect", expect)
        send("api/driver/advance", "POST", b)
    }

    // ───────── النقل ─────────

    /** هل يستطيع التطبيق التحدّث مع الخادم الآن؟ */
    val isAvailable: Boolean
        get() = AppConfig.apiBaseURL != null && AppConfig.sessionToken != null

    internal suspend fun send(path: String, method: String, body: JSONObject?): JSONObject {
        val base = AppConfig.apiBaseURL ?: throw Failure.NoServer
        val token = AppConfig.sessionToken ?: throw Failure.Unauthorized

        val b = Request.Builder()
            .url(Net.url(base, path))
            .header("Authorization", "Bearer $token")
        if (body != null) b.method(method, body.toString().toRequestBody(Net.jsonType))
        else b.method(method, if (method == "GET") null else "{}".toRequestBody(Net.jsonType))

        val res = try {
            Net.exec(b.build())
        } catch (e: IOException) {
            throw Failure.Unreachable("تعذّر الوصول للسيرفر — تأكد من الإنترنت")
        }

        if (res.status !in 200..299) {
            if (res.status == 401) throw Failure.Unauthorized
            val message = res.json.str("message") ?: res.json.str("error")
                ?: "فشل الطلب (${res.status})"
            throw Failure.Server(message)
        }
        OrderExtras.scan(res.json)
        return res.json
    }

    private fun encode(p: Point): JSONObject =
        JSONObject().put("lat", p.lat).put("lon", p.lon).put("zone", p.zone).put("name", p.name)
}

// ───────── WalletService.swift ─────────

object WalletService {

    data class CheckoutResult(
        val reference: String,
        val redirectURL: String?,
        val successURL: String,
        val cancelURL: String,
    )

    data class ConfirmResult(
        val status: String,
        val credited: Boolean,
        val balance: Double?,
    )

    sealed class Failure(override val message: String) : Exception(message) {
        class Unreachable(m: String) : Failure(m)
        class Server(m: String) : Failure(m)
    }

    suspend fun startTopUp(amount: Double, forDriver: Boolean = false): CheckoutResult {
        val body = JSONObject().put("amount", amount)
        if (forDriver) body.put("for", "driver")
        val json = post("api/wallet/topup/checkout", body)
        val reference = json.str("reference") ?: throw Failure.Server("تعذّر بدء عملية الدفع")
        return CheckoutResult(
            reference = reference,
            redirectURL = json.str("redirectURL"),
            successURL = json.str("successURL") ?: "",
            cancelURL = json.str("cancelURL") ?: "",
        )
    }

    suspend fun confirmTopUp(reference: String): ConfirmResult {
        val json = post("api/wallet/topup/confirm", JSONObject().put("reference", reference))
        return ConfirmResult(
            status = json.str("status") ?: "UNKNOWN",
            credited = json.optBoolean("credited", false),
            balance = if (json.has("balance") && !json.isNull("balance")) json.optDouble("balance") else null,
        )
    }

    private suspend fun post(path: String, body: JSONObject): JSONObject {
        val base = AppConfig.apiBaseURL ?: throw Failure.Unreachable("لا يوجد سيرفر مضبوط")
        val token = AppConfig.sessionToken ?: throw Failure.Unreachable("سجّل الدخول أولاً")
        val req = Request.Builder()
            .url(Net.url(base, path))
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(Net.jsonType))
            .build()
        val res = try {
            Net.exec(req)
        } catch (e: IOException) {
            throw Failure.Unreachable("تعذّر الوصول للسيرفر — تأكد أنه يعمل وأنك على نفس الشبكة")
        }
        if (res.status !in 200..299) {
            throw Failure.Server(res.json.str("message") ?: "حدث خطأ غير متوقّع")
        }
        return res.json
    }
}

// ───────── DocumentUploader.swift ─────────

object DocumentUploader {

    private val inFlight = mutableSetOf<Id>()
    private val lock = Mutex()

    private class UploadError(message: String) : Exception(message)

    /** يرفع ملفاً واحداً ويبلّغ عن كل تغيّر في حالته عبر [progress]. */
    suspend fun upload(
        asset: DocumentAsset,
        driverID: Id?,
        progress: suspend (DocumentAsset) -> Unit,
    ) {
        val already = lock.withLock { !inFlight.add(asset.id) }
        if (already) return

        try {
            val base = AppConfig.apiBaseURL
            if (base == null) {
                // بلا خادم: يبقى محفوظاً على الجهاز.
                progress(asset.copy(state = DocumentState.stored, failureReason = null))
                return
            }
            val data = DocumentStore.data(asset)
            if (data == null) {
                progress(asset.copy(state = DocumentState.failed,
                    failureReason = "الملف غير موجود على الجهاز"))
                return
            }

            progress(asset.copy(state = DocumentState.uploading, failureReason = null))

            try {
                val remoteID = send(data, asset, driverID, base)
                progress(asset.copy(state = DocumentState.uploaded, remoteID = remoteID,
                    uploadedAt = nowMs(), failureReason = null))
            } catch (e: Exception) {
                progress(asset.copy(state = DocumentState.failed,
                    failureReason = (e as? UploadError)?.message ?: "تعذّر الرفع — تحقّق من الاتصال"))
            }
        } finally {
            lock.withLock { inFlight.remove(asset.id) }
        }
    }

    suspend fun uploadPending(
        assets: List<DocumentAsset>,
        driverID: Id?,
        progress: suspend (DocumentAsset) -> Unit,
    ) {
        for (asset in assets) upload(asset, driverID, progress)
    }

    private suspend fun send(data: ByteArray, asset: DocumentAsset, driverID: Id?, base: String): String {
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("kind", asset.kind.name)
        if (driverID != null) form.addFormDataPart("owner", driverID)
        form.addFormDataPart(
            "file", "upload",
            data.toRequestBody(asset.mime.toMediaType()),
        )

        val rb = Request.Builder().url(Net.url(base, "documents/upload")).post(form.build())
        AppConfig.sessionToken?.let { rb.header("Authorization", "Bearer $it") }

        val c = Net.client.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
        val res = try {
            Net.exec(rb.build(), c)
        } catch (e: IOException) {
            throw UploadError("تعذّر الوصول للسيرفر")
        }
        val id = res.json.str("file_id")
        if (res.status != 200 || id == null) {
            throw UploadError(res.json.str("message") ?: "رفض الخادم الملف")
        }
        return id
    }
}

// ───────── AdminDocumentService.swift ─────────

/** يجلب روابط موقّعة مؤقّتة لمستندات مندوب. */
object AdminDocumentService {

    data class DocLink(val url: String) {
        val id: String get() = url
    }

    sealed class Failure(override val message: String) : Exception(message) {
        class Unreachable(m: String) : Failure(m)
        class Server(m: String) : Failure(m)
    }

    suspend fun links(phone: String, kind: DocumentKind): List<DocLink> {
        val base = AppConfig.apiBaseURL ?: throw Failure.Unreachable("لا يوجد سيرفر مضبوط")
        val token = AppConfig.sessionToken ?: throw Failure.Unreachable("سجّل الدخول أولاً")

        val url = Net.url(base, "api/admin/driver-document-url").toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("phone", phone)
            ?.addQueryParameter("kind", kind.name)
            ?.build() ?: throw Failure.Server("تعذّر تكوين الطلب")

        val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        val res = try {
            Net.exec(req)
        } catch (e: IOException) {
            throw Failure.Unreachable("تعذّر الوصول للسيرفر — تأكد أنه يعمل وأنك على نفس الشبكة")
        }
        if (res.status !in 200..299) {
            throw Failure.Server(res.json.str("message") ?: "تعذّر جلب المستند")
        }

        val files = res.json.optJSONArray("files") ?: return emptyList()
        val baseUri = java.net.URI(base.trimEnd('/') + "/")
        return (0 until files.length()).mapNotNull { i ->
            val s = files.optJSONObject(i)?.str("url") ?: return@mapNotNull null
            runCatching { DocLink(baseUri.resolve(s).toString()) }.getOrNull()
        }
    }
}
