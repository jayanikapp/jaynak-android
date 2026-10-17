package com.jaynak.app.driver

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.jaynak.app.MainActivity
import com.jaynak.app.core.Coord
import com.jaynak.app.core.LiveTracking
import com.jaynak.app.core.OrdersService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * يرسل موقع المندوب الحقيقي للسيرفر كل ~4 ثوانٍ طالما هو «متصل»
 * (خدمة أمامية — تعمل والشاشة مقفلة).
 */
class DriverLocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var last: Coord? = null
    private var lastHeading = 0f
    private var lastSentAt = 0L
    private var seen = setOf<String>()
    private var offersJob: kotlinx.coroutines.Job? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(r: LocationResult) {
            val loc = r.lastLocation ?: return
            val now = System.currentTimeMillis()
            if (now - lastSentAt < 2500) return
            val here = Coord(loc.latitude, loc.longitude)
            val prev = last
            // الاتجاه: من الحساس إن توفّر، وإلا من الحركة الأخيرة
            val heading = when {
                loc.hasBearing() && loc.speed > 1.0f -> loc.bearing
                prev != null && prev.km(here) * 1000 > 5 -> LiveTracking.bearing(prev, here)
                else -> lastHeading
            }
            last = here; lastHeading = heading; lastSentAt = now
            if (!OrdersService.isAvailable) return
            scope.launch { runCatching { LiveTracking.publish(here.lat, here.lon, heading, loc.speed) } }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CH, "مشاركة الموقع", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "يظهر أثناء اتصالك لتتبّع الطلبات" })
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CH_ORDERS, "طلبات جديدة", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "ينبّهك فور وصول طلب جديد"
                    enableVibration(true)
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                })
        }
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ID, notification())
        if (!hasPermission(this)) { stopSelf(); return START_NOT_STICKY }
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
            .setMinUpdateIntervalMillis(2000L)
            .setMinUpdateDistanceMeters(3f)
            .build()
        LocationServices.getFusedLocationProviderClient(this)
            .requestLocationUpdates(req, callback, Looper.getMainLooper())
        if (offersJob?.isActive != true) offersJob = scope.launch { pollOffers() }
        return START_STICKY
    }

    /** إشعار فوري لكل طلب جديد؛ ويُسحب الإشعار عندما يقبله مندوب آخر. */
    private suspend fun pollOffers() {
        val nm = getSystemService(NotificationManager::class.java)
        var first = true
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            try {
                if (OrdersService.isAvailable) {
                    val feed = OrdersService.feed()
                    val all = (feed.offers + feed.board).filter { it.status == "searching" }
                    val ids = all.map { it.id }.toSet()
                    val keys = all.map { it.id + "@" + it.offeredAt }.toSet()
                    for (o in all) {
                        val key = o.id + "@" + o.offeredAt
                        // أول قراءة بعد الدخول/الاتصال: صامتة (الطلبات القديمة في القوائم فقط)
                        if (first || key in seen) continue
                        val open = PendingIntent.getActivity(
                            this, 0, Intent(this, MainActivity::class.java),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        val body = "${o.pickup.name} ← ${o.dropoff.name}" + (o.km?.let { " · %.1f كم".format(java.util.Locale.US, it) } ?: "")
                        val n = NotificationCompat.Builder(this, CH_ORDERS)
                            .setContentTitle("طلب جديد · %.3f ر.ع. لك".format(java.util.Locale.US, o.price.driverPayout))
                            .setContentText(body)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                            .setSmallIcon(android.R.drawable.ic_dialog_map)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setCategory(NotificationCompat.CATEGORY_CALL)
                            .setAutoCancel(true)
                            .setContentIntent(open)
                            .build()
                        runCatching { nm.notify(o.id.hashCode(), n) }
                    }
                    // طلب اختفى (قَبِله غيرك أو أُلغي): نسحب إشعاره
                    for (gone in seen - keys) nm.cancel(gone.substringBefore("@").hashCode())
                    seen = keys
                    first = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
            }
            delay(4000)
        }
    }

    override fun onDestroy() {
        runCatching { LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(callback) }
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CH)
            .setContentTitle("جاينك — أنت متصل")
            .setContentText("نشارك موقعك مع الزبون أثناء التوصيل")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true).setSilent(true).setContentIntent(open).build()
    }

    companion object {
        private const val CH = "jaynak.location"
        private const val CH_ORDERS = "jaynak.orders"
        private const val ID = 1002

        @Volatile private var running = false

        fun hasPermission(c: Context) =
            ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        /** يبدأ/يوقف الخدمة حسب حالة المندوب. آمن للاستدعاء المتكرر. */
        fun ensure(c: Context, online: Boolean) {
            if (!online) {
                if (running) { running = false; runCatching { c.stopService(Intent(c, DriverLocationService::class.java)) } }
                return
            }
            if (running || !hasPermission(c)) return
            val i = Intent(c, DriverLocationService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i) else c.startService(i)
                running = true
            }
        }
    }
}
