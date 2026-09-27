package com.deniz.eda.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.deniz.eda.health.HealthConnectManager
import kotlinx.coroutines.*
import java.util.Calendar
import com.deniz.eda.utils.DigitUtils

/**
 * سرویس پس‌زمینه "دکتر خودکار"
 * 
 * کارها:
 * ۱. چک ضربان قلب هر ۱۵ دقیقه
 * ۲. هشدار خودکار ضربان غیرعادی (خطرناک)
 * ۳. گزارش صبحگاهی (۷-۱۰ صبح)
 * ۴. یادآوری شب (۲۲ شب)
 * ۵. سؤال‌های روزانه (صبح، ظهر، عصر)
 * ۶. گزارش هفتگی (شنبه‌ها)
 * ۷. حتی تو حالت خواب کار می‌کنه
 */
class HealthMonitorService : Service() {

    companion object {
        private const val TAG = "EDA-HealthMonitor"
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "eda_health_channel"
        
        // چک هر ۱۵ دقیقه
        private const val CHECK_INTERVAL_MS = 15 * 60 * 1000L
        
        // محدوده‌های خطرناک ضربان
        private const val HR_DANGER_HIGH = 140
        private const val HR_DANGER_LOW = 40
        private const val HR_WARNING_HIGH = 120
        private const val HR_WARNING_LOW = 50
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var healthManager: HealthConnectManager
    
    // وضعیت ردیابی (برای اینکه هر چیز یک بار باشه)
    private var lastMorningReport: Int = -1        // روز سال
    private var lastNightReminder: Int = -1        // روز سال
    private var lastWeeklyReport: Int = -1         // هفته سال
    private var lastQuestionAsk: Int = -1          // ساعت
    
    // آخرین ضربان برای تشخیص الگو
    private var lastHR: Int? = null
    private var highHRCount: Int = 0
    private var lowHRCount: Int = 0

    override fun onCreate() {
        super.onCreate()
        healthManager = HealthConnectManager(this)
        createNotificationChannel()
        android.util.Log.i(TAG, "🩺 HealthMonitorService oluşturuldu")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("🩺 Sağlık izleniyor...")
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

    // ══════════════════════════════════════════════════════
    //  منطق اصلی "دکتر خودکار"
    // ══════════════════════════════════════════════════════
    private suspend fun monitorHealth() {
        // ═══ آپدیت cache برای CommandProcessor ═══
        try {
            val hr = healthManager.getLatestHeartRate(1440)
            val sleep = healthManager.getLastNightSleepHours()
            val steps = healthManager.getTodaySteps()
            com.deniz.eda.core.CommandProcessor.updateCache(hr, sleep, steps)
            android.util.Log.d(TAG, "Cache updated: HR=$hr, Sleep=$sleep, Steps=$steps")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Cache update error: ${e.message}")
        }
        
        val calendar = Calendar.getInstance()
        val saat = calendar.get(Calendar.HOUR_OF_DAY)
        val gun = calendar.get(Calendar.DAY_OF_YEAR)
        val hafta = calendar.get(Calendar.WEEK_OF_YEAR)

        // ۱. چک ضربان قلب (همیشه)
        val hr = healthManager.getLatestHeartRate(20)
        checkHeartRate(hr)

        // ۲. گزارش صبحگاهی (۷-۱۰ صبح، یک بار در روز)
        if (saat in 7..10 && lastMorningReport != gun) {
            lastMorningReport = gun
            sendMorningReport()
        }

        // ۳. یادآوری شب (۲۲ شب، یک بار در روز)
        if (saat == 22 && lastNightReminder != gun) {
            lastNightReminder = gun
            sendNightReminder()
        }

        // ۴. سؤال‌های روزانه (۹ صبح، ۱۴ ظهر، ۱۹ عصر)
        if (saat in listOf(9, 14, 19) && lastQuestionAsk != saat) {
            lastQuestionAsk = saat
            askDailyQuestion(saat)
        }

        // ۵. گزارش هفتگی (شنبه‌ها ساعت ۱۰)
        if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY && 
            saat == 10 && lastWeeklyReport != hafta) {
            lastWeeklyReport = hafta
            sendWeeklyReport()
        }
    }

    // ─────────────────────────────────────
    //  ۱. چک ضربان و هشدار
    // ─────────────────────────────────────
    private suspend fun checkHeartRate(hr: Int?) {
        if (hr == null) return
        
        lastHR = hr
        android.util.Log.d(TAG, "HR: $hr")

        when {
            // خطرناک بالا
            hr >= HR_DANGER_HIGH -> {
                highHRCount++
                if (highHRCount >= 2) {
                    konusVeNotify("⚠️ denizçim! Nabzın çok yüksek: $hr! İyi misin?")
                    highHRCount = 0
                }
            }
            
            // خطرناک پایین
            hr <= HR_DANGER_LOW -> {
                lowHRCount++
                if (lowHRCount >= 2) {
                    konusVeNotify("⚠️ denizçim! Nabzın çok düşük: $hr! Doktora görünmen gerekebilir.")
                    lowHRCount = 0
                }
            }
            
            // هشدار بالا (نه خطرناک)
            hr >= HR_WARNING_HIGH -> {
                konus("denizçim, nabzın $hr. Biraz yüksek, sakinleş.")
            }
            
            // هشدار پایین
            hr <= HR_WARNING_LOW -> {
                konus("denizçim, nabzın $hr. Biraz düşük.")
            }
            
            // عادی — ریست
            else -> {
                highHRCount = 0
                lowHRCount = 0
            }
        }
    }

    // ─────────────────────────────────────
    //  ۲. گزارش صبحگاهی
    // ─────────────────────────────────────
    private suspend fun sendMorningReport() {
        val sleep = healthManager.getLastNightSleepHours()
        val hr = healthManager.getLatestHeartRate(60)
        
        val mesaj = buildString {
            append("Günaydın denizçim! ")
            if (sleep != null) {
                append("Dün gece " + DigitUtils.formatFloat(sleep) + " saat uyumuşsun. ")
            }
            if (hr != null) {
                append("Nabzın şu an $hr. ")
            }
            
            // توصیه
            if (sleep != null && sleep < 6) {
                append("Biraz az uyumuşsun, bugün dikkatli ol.")
            } else if (sleep != null && sleep > 8) {
                append("İyi uyumuşsun, harika!")
            } else {
                append("Bugün nasılsın?")
            }
        }
        
        android.util.Log.i(TAG, "🌅 Morning: $mesaj")
        konus(mesaj)
    }

    // ─────────────────────────────────────
    //  ۳. یادآوری شب
    // ─────────────────────────────────────
    private suspend fun sendNightReminder() {
        val hr = healthManager.getLatestHeartRate(60)
        val mesaj = if (hr != null && hr > 90) {
            "denizçim, saat 22. Nabzın $hr, hâlâ yüksek. Biraz sakinleş ve yat."
        } else {
            "denizçim, saat 22. Yatma vakti geldi, iyi geceler."
        }
        
        android.util.Log.i(TAG, "🌙 Night: $mesaj")
        konus(mesaj)
    }

    // ─────────────────────────────────────
    //  ۴. سؤال‌های روزانه
    // ─────────────────────────────────────
    private suspend fun askDailyQuestion(saat: Int) {
        val mesaj = when (saat) {
            9 -> "Günaydın denizçim! Kahvaltı yaptın mı? Su içtin mi?"
            14 -> "denizçim, öğlen oldu. Yemek yedin mi? Biraz mola ver."
            19 -> "denizçim, akşam oldu. Bugün nasılsın? İlaç içtin mi?"
            else -> return
        }
        
        android.util.Log.i(TAG, "❓ Soru ($saat): $mesaj")
        konus(mesaj)
    }

    // ─────────────────────────────────────
    //  ۵. گزارش هفتگی
    // ─────────────────────────────────────
    private suspend fun sendWeeklyReport() {
        val hr = healthManager.getLatestHeartRate(60)
        val sleep = healthManager.getLastNightSleepHours()
        
        val mesaj = buildString {
            append("denizçim, haftalık rapor: ")
            if (hr != null) append("Nabzın $hr. ")
            if (sleep != null) append("Son uykun " + DigitUtils.formatFloat(sleep) + " saat. ")
            append("Bu hafta sağlığına dikkat et, seni seviyorum. 💙")
        }
        
        android.util.Log.i(TAG, "📊 Weekly: $mesaj")
        konus(mesaj)
    }

    // ─────────────────────────────────────
    //  TTS از طریق EdaForegroundService
    // ─────────────────────────────────────
    private fun konus(mesaj: String) {
        try {
            val intent = Intent(this, EdaForegroundService::class.java).apply {
                action = "ACTION_SPEAK"
                putExtra("text", mesaj)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "konus hatası: ${e.message}")
        }
    }

    private fun konusVeNotify(mesaj: String) {
        konus(mesaj)
        notifyAlert(mesaj)
    }

    // ─────────────────────────────────────
    //  Notifications
    // ─────────────────────────────────────
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
            .setContentTitle("🩺 Eda Sağlık")
            .setContentText(mesaj)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setPriority(priority)
            .setOngoing(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mesaj))
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
        android.util.Log.i(TAG, "🩺 HealthMonitorService yok edildi")
    }
}
