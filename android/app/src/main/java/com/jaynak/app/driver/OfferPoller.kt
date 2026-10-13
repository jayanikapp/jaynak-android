package com.jaynak.app.driver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jaynak.app.MainActivity
import com.jaynak.app.data.Api
import com.jaynak.app.data.SecureStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


class OfferService : Service() {

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var lastSeen = setOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        channels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ONGOING_ID, ongoing("متصل — في انتظار الطلبات"))
        if (job?.isActive != true) job = scope.launch { loop() }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private suspend fun loop() {
        val store = SecureStore(applicationContext)
        val api = Api(store)
        while (currentCoroutineContext().isActive) {


            try {
                val feed = api.driverFeed()
                val ids = (feed.offers + feed.board).map { it.id }.toSet()


                val fresh = feed.offers.filter { it.id !in lastSeen }
                lastSeen = ids
                fresh.forEach { o ->
                    notifyOffer(
                        o.id,
                        "طلب جديد · ${"%.3f".format(o.price.driverPayout)} ر.ع. لك",
                        "${o.pickup.name} ← ${o.dropoff.name}" +
                            (o.km?.let { " · %.1f كم".format(it) } ?: ""),
                    )
                }
                val n = feed.offers.size
                update(if (n > 0) "$n طلب معروض عليك" else "متصل — في انتظار الطلبات")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {


            }
            delay(POLL_MS)
        }
    }


    private fun channels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)


        val offers = NotificationChannel(
            CH_OFFERS, "طلبات جديدة", NotificationManager.IMPORTANCE_HIGH,
        ).apply {
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

        val ongoing = NotificationChannel(
            CH_ONGOING, "حالة الاتصال", NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "إشعار دائم يبيّن أنك متصل" }

        nm.createNotificationChannel(offers)
        nm.createNotificationChannel(ongoing)
    }

    private fun open(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ongoing(text: String): Notification =
        NotificationCompat.Builder(this, CH_ONGOING)
            .setContentTitle("جاينك")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open())
            .build()

    private fun update(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(ONGOING_ID, ongoing(text))
    }

    private fun notifyOffer(id: String, title: String, body: String) {
        val n = NotificationCompat.Builder(this, CH_OFFERS)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(open())
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(id.hashCode(), n)
    }

    companion object {
        private const val CH_OFFERS = "jaynak.offers"
        private const val CH_ONGOING = "jaynak.ongoing"
        private const val ONGOING_ID = 1001

        private const val POLL_MS = 8_000L

        fun start(ctx: Context) {
            val i = Intent(ctx, OfferService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, OfferService::class.java))
    }
}
