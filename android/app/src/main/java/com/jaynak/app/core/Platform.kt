package com.jaynak.app.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.jaynak.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

// ───────── Persistence.swift ─────────

@Serializable
data class PersistedState(
    val customers: List<Customer> = emptyList(),
    val drivers: List<Driver> = emptyList(),
    val orders: List<Order> = emptyList(),
    val transactions: List<WalletTxn> = emptyList(),
    val rules: PricingRules = PricingRules(),
    val promos: List<PromoCode> = emptyList(),
    val tickets: List<SupportTicket> = emptyList(),
    val chats: List<ChatMessage> = emptyList(),
    val admins: List<AdminUser> = emptyList(),
    val rewards: List<Reward> = emptyList(),
    val vouchers: List<Voucher> = emptyList(),
    val deletionRequests: List<AccountDeletionRequest> = emptyList(),

    val identities: List<IdentityRecord> = emptyList(),
    val session: Session? = null,
    val seeded: Boolean = false,

    val seedVersion: Int = 0,
)

const val currentSeedVersion = 8

object Persistence {
    const val filename = "jaynak-state.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val file: File get() = File(Boot.app.filesDir, filename)

    fun load(): PersistedState? = runCatching {
        val f = file
        if (!f.exists()) null else json.decodeFromString(PersistedState.serializer(), f.readText())
    }.getOrNull()

    /** كتابة ذرّية: ملف مؤقّت ثم إعادة تسمية. */
    fun save(state: PersistedState) {
        runCatching {
            val text = json.encodeToString(PersistedState.serializer(), state)
            val tmp = File(Boot.app.filesDir, "$filename.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }
    }

    fun wipe() {
        runCatching { file.delete() }
    }
}

// ───────── Feedback.swift ─────────

enum class ImpactStyle { light, medium, heavy }
enum class NotifyType { success, warning, error }

/** نغمات وهزّات بديلة لـ AVAudioEngine وUIFeedbackGenerator. */
object Feedback {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var repeatJob: Job? = null

    private data class Tone(val freq: Double, val dur: Double)

    private const val sampleRate = 44_100

    private fun buffer(tones: List<Tone>, gain: Float): ShortArray {
        val total = tones.sumOf { it.dur }
        val frames = (total * sampleRate).toInt()
        val out = ShortArray(frames)
        var idx = 0
        for (tone in tones) {
            val n = (tone.dur * sampleRate).toInt()
            var i = 0
            while (i < n && idx < frames) {
                val t = i.toDouble() / sampleRate
                val attack = 0.008
                val release = 0.10
                val env = when {
                    t < attack -> t / attack
                    tone.dur - t < release -> maxOf(0.0, (tone.dur - t) / release)
                    else -> 1.0
                }
                val s = sin(2 * PI * tone.freq * t) * 0.75 +
                    sin(2 * PI * tone.freq * 2 * t) * 0.25
                out[idx] = (s * env * gain * Short.MAX_VALUE).toInt().toShort()
                idx++
                i++
            }
        }
        return out
    }

    private fun play(tones: List<Tone>, gain: Float = 0.5f, alarm: Boolean = false) {
        scope.launch {
            runCatching {
                val pcm = buffer(tones, gain)
                if (pcm.isEmpty()) return@runCatching
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(if (alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                delay((tones.sumOf { it.dur } * 1000).toLong() + 150)
                track.release()
            }
        }
    }

    fun newOffer() {
        play(listOf(Tone(880.0, 0.16), Tone(1174.7, 0.30)), 0.6f)
        impact(ImpactStyle.heavy)
    }

    /** نغمة رسالة جديدة (مختلفة عن الطلبات). */
    fun message() {
        play(listOf(Tone(1046.5, 0.09), Tone(1568.0, 0.16)), 0.7f)
        impact(ImpactStyle.medium)
    }

    /** صفّارة عالية لطلب سريع جداً. */
    fun urgentOffer() {
        scope.launch {
            runCatching {
                // صفّارة طوارئ: نغمة متصاعدة ثم هابطة (مثل الإسعاف)، موجة مربّعة عالية
                val frames = (2.2 * sampleRate).toInt()
                val pcm = ShortArray(frames)
                var phase = 0.0
                for (i in 0 until frames) {
                    val t = i.toDouble() / sampleRate
                    val sweep = (t % 1.1) / 1.1
                    val f = 650.0 + 900.0 * (if (sweep < 0.5) sweep * 2 else (1 - sweep) * 2)
                    phase += 2 * PI * f / sampleRate
                    pcm[i] = (if (sin(phase) >= 0) 0.9 * Short.MAX_VALUE else -0.9 * Short.MAX_VALUE).toInt().toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                delay(2_350)
                track.release()
            }
        }
        impact(ImpactStyle.heavy)
        notify(NotifyType.error)
    }

    fun success() {
        play(listOf(Tone(659.3, 0.12), Tone(987.8, 0.22)), 0.45f)
        notify(NotifyType.success)
    }

    fun warning() {
        play(listOf(Tone(440.0, 0.18)), 0.4f)
        notify(NotifyType.warning)
    }

    /** تنبيه متكرّر لعرض طلب — يتوقف عند القبول أو الرفض. */
    fun startOfferAlert(urgent: Boolean = false) {
        stopOfferAlert()
        repeatJob = scope.launch {
            for (i in 0 until (if (urgent) 6 else 4)) {
                if (urgent) { urgentOffer(); delay(2_400) } else { newOffer(); delay(2_200) }
                if (!isActive) return@launch
            }
        }
    }

    fun stopOfferAlert() {
        repeatJob?.cancel()
        repeatJob = null
    }

    // ── هزّات ──

    private val vibrator: Vibrator?
        get() = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Boot.app.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                Boot.app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()

    @SuppressLint("MissingPermission")
    private fun buzz(ms: Long, amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE) {
        val v = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, amplitude))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        }
    }

    private fun buzzPattern(pattern: LongArray) {
        val v = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, -1)
            }
        }
    }

    fun impact(style: ImpactStyle = ImpactStyle.light) {
        when (style) {
            ImpactStyle.light -> buzz(10, 60)
            ImpactStyle.medium -> buzz(18, 130)
            ImpactStyle.heavy -> buzz(32, 230)
        }
    }

    fun selection() = buzz(6, 40)

    fun notify(type: NotifyType) {
        when (type) {
            NotifyType.success -> buzzPattern(longArrayOf(0, 12, 50, 18))
            NotifyType.warning -> buzzPattern(longArrayOf(0, 30, 60, 30))
            NotifyType.error -> buzzPattern(longArrayOf(0, 40, 50, 40, 50, 40))
        }
    }
}

// ───────── Notifications.swift ─────────

object Notifications {

    private const val CH_OFFERS = "jaynak.offers"

    /** يضبطه MainActivity ليطلب إذن الإشعارات (أندرويد ١٣+) عند الحاجة. */
    var requestPermission: (() -> Unit)? = null

    val authorized: Boolean
        get() = NotificationManagerCompat.from(Boot.app).areNotificationsEnabled()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = Boot.app.getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CH_OFFERS, "طلبات جديدة", NotificationManager.IMPORTANCE_HIGH)
            .apply {
                description = "ينبّهك عند وصول طلب — يظهر والشاشة مقفلة"
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
        nm.createNotificationChannel(ch)
    }

    /** يطلب الإذن إن لزم ويعيد هل الإشعارات مفعّلة الآن. */
    fun requestIfNeeded(): Boolean {
        ensureChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(Boot.app, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission?.invoke()
        }
        return authorized
    }

    @SuppressLint("MissingPermission")
    fun newOffer(ref: String, from: String, to: String, payout: Double) {
        if (!authorized) return
        ensureChannel()
        val open = PendingIntent.getActivity(
            Boot.app, 0, Intent(Boot.app, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = "$from ← $to"
        val n = NotificationCompat.Builder(Boot.app, CH_OFFERS)
            .setContentTitle("طلب جديد · ${payout.moneyShort}")
            .setContentText(body)
            .setSubText(ref)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching {
            NotificationManagerCompat.from(Boot.app).notify("offer-$ref".hashCode(), n)
        }
    }

    @SuppressLint("MissingPermission")
    fun chat(from: String, text: String) {
        if (!authorized) return
        ensureChannel()
        val open = PendingIntent.getActivity(
            Boot.app, 0, Intent(Boot.app, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(Boot.app, CH_OFFERS)
            .setContentTitle("رسالة من $from")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching {
            NotificationManagerCompat.from(Boot.app).notify("chat-$from".hashCode(), n)
        }
    }

    fun clearOffer(ref: String) {
        NotificationManagerCompat.from(Boot.app).cancel("offer-$ref".hashCode())
    }
}

// ───────── LocationService.swift ─────────

enum class LocationAuth { notDetermined, denied, authorized }

object LocationService {

    var authorization by mutableStateOf(LocationAuth.notDetermined)
        private set
    var current by mutableStateOf<Coord?>(null)
        private set

    var resolvedName by mutableStateOf<String?>(null)
        private set
    var isResolving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    /** يضبطه MainActivity ليطلب إذن الموقع. */
    var requestPermission: (() -> Unit)? = null

    private var askedOnce = false

    val isDenied: Boolean get() = authorization == LocationAuth.denied

    private fun granted(): Boolean =
        ContextCompat.checkSelfPermission(Boot.app, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(Boot.app, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** يحدّد موقع المستخدم الحالي (يطلب الإذن أولاً إن لزم). */
    fun locate() {
        error = null
        if (granted()) {
            authorization = LocationAuth.authorized
            fetch()
            return
        }
        if (!askedOnce) {
            askedOnce = true
            isResolving = true
            requestPermission?.invoke()
        } else {
            authorization = LocationAuth.denied
            error = "الوصول للموقع مرفوض. فعّله من الإعدادات لنحدّد نقطتك تلقائياً."
        }
    }

    /** يستدعيها MainActivity بعد ردّ المستخدم على طلب الإذن. */
    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            authorization = LocationAuth.authorized
            fetch()
        } else {
            authorization = LocationAuth.denied
            isResolving = false
            error = "الوصول للموقع مرفوض. فعّله من الإعدادات لنحدّد نقطتك تلقائياً."
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetch() {
        isResolving = true
        val client = LocationServices.getFusedLocationProviderClient(Boot.app)
        val cts = CancellationTokenSource()
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                if (loc == null) {
                    isResolving = false
                    error = "تعذّر تحديد موقعك الآن. جرّب مرة ثانية أو اختر النقطة على الخريطة."
                } else {
                    current = Coord(loc.latitude, loc.longitude)
                    Feedback.success()
                    reverseGeocode(loc.latitude, loc.longitude)
                }
            }
            .addOnFailureListener {
                isResolving = false
                error = "تعذّر تحديد موقعك الآن. جرّب مرة ثانية أو اختر النقطة على الخريطة."
            }
    }

    private fun reverseGeocode(lat: Double, lon: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            val name = runCatching {
                @Suppress("DEPRECATION")
                val marks = Geocoder(Boot.app, Locale("ar", "OM")).getFromLocation(lat, lon, 1)
                val m = marks?.firstOrNull()
                val parts = listOfNotNull(m?.subLocality ?: m?.locality, m?.thoroughfare, m?.subThoroughfare)
                    .filter { it.isNotEmpty() }
                if (parts.isEmpty()) "موقعي الحالي" else parts.joinToString(" - ")
            }.getOrDefault("موقعي الحالي")
            withContext(Dispatchers.Main) {
                resolvedName = name
                isResolving = false
            }
        }
    }

    val currentPlace: Place?
        get() {
            val c = current ?: return null
            return Place(
                name = resolvedName ?: "موقعي الحالي",
                zone = Geo.nearestZone(c),
                coord = c,
                details = "حُدِّد تلقائياً",
            )
        }
}
