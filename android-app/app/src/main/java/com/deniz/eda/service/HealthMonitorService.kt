package com.deniz.eda.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.deniz.eda.health.HealthConnectManager
import kotlinx.coroutines.*

/**
 * سرویس پس‌زمینه برای پایش علائم حیاتی
 * هر ۳۰ دقیقه ضربان قلب و خواب رو چک می‌کنه
 * اگه مشکل جدی بود، به Eda اطلاع می‌ده
 */
class HealthMonitorService : Service() {

    companion object {
        private const val TAG = "EDA-HealthMonitor"
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "eda_health_channel"
        private const val CHECK_INTERVAL_MS = 30 * 60 * 1000L // 30 دقیقه
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var healthManager: HealthConnectManager

    override fun onCreate() {
        super.onCreate()
        healthManager = HealthConnectManager(this)
        createNotificationChannel()
        android.util.Log.i(TAG, "HealthMonitorService oluşturuldu")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Sağlık verileri izleniyor...")
        startForeground(NOTIF_ID, notification)

        serviceScope.launch {
            while (isActive) {
                try {
                    monitorHealth()
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Monitor hata: ${e.message}")
                }
                delay(CHECK_INTERVAL_MS)
            }
        }

        return START_STICKY
    }

    private suspend fun monitorHealth() {
        val hr = healthManager.getLatestHeartRate(30)
        val sleep = healthManager.getLastNightSleepHours()

        android.util.Log.d(TAG, "HR: $hr, Sleep: $sleep")

        // ═══ هشدارهای خودکار ═══
        if (hr != null) {
            when {
                hr > 120 -> {
                    // ضربان خیلی بالا
                    android.util.Log.w(TAG, "⚠️ Yüksek nabız: $hr")
                    notifyAlert("⚠️ Yüksek nabız: $hr atış!")
                }
                hr < 45 -> {
                    // ضربان خیلی پایین
                    android.util.Log.w(TAG, "⚠️ Düşük nabız: $hr")
                    notifyAlert("⚠️ Düşük nabız: $hr atış!")
                }
            }
        }

        // ارسال به CommandProcessor برای تصمیم‌گیری
        // (اینجا می‌تونیم به Eda اطلاع بدیم که خودش TTS کنه)
    }

    private fun notifyAlert(mesaj: String) {
        val notification = buildNotification(mesaj, priority = NotificationCompat.PRIORITY_HIGH)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID + 1, notification)
    }

    private fun buildNotification(mesaj: String, priority: Int = NotificationCompat.PRIORITY_LOW): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Eda Sağlık")
            .setContentText(mesaj)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setPriority(priority)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Eda Sağlık İzleme",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Sağlık verilerini arka planda izler"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        android.util.Log.i(TAG, "HealthMonitorService yok edildi")
    }
}
