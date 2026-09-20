package com.deniz.eda.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.deniz.eda.R
import com.deniz.eda.core.Settings
import com.deniz.eda.utils.BatteryUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * سرویس جدا برای pil bildirimi.
 * مستقل از EDA — حتی اگه EDA خاموش باشه، این کار می‌کنه.
 * 
 * قابلیت‌ها:
 *  - هر N دقیقه: pil می‌گه (تو notification)
 *  - pil < 30% → هشدار فوری
 *  - pil < 15% → بحرانی
 */
class BatteryNotifierService : Service() {

    companion object {
        private const val TAG = "BatteryNotifier"
        private const val CHANNEL_ID = "eda_battery_notifier"
        private const val CHANNEL_WARN_ID = "eda_battery_warning"
        private const val NOTIF_ID = 1001
        private const val WARN_NOTIF_ID = 1002
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var notifierJob: Job? = null
    private var sonPilSeviyesi: Int = -1

    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        kanallariOlustur()
        
        // Foreground Service شروع کن
        startForeground(NOTIF_ID, bildirimOlustur("Pil izleniyor...", false))
        
        // حلقه‌ی اصلی
        bildirimDongusu()
        
        android.util.Log.d(TAG, "BatteryNotifierService başlatıldı")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.batteryNotifierAktif) {
            android.util.Log.d(TAG, "Battery notifier kapalı — durduruluyor")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun bildirimDongusu() {
        notifierJob?.cancel()
        notifierJob = serviceScope.launch {
            while (isActive) {
                try {
                    // pil kontrol
                    val pil = BatteryUtils.pilBilgisiAl(this@BatteryNotifierService)
                    if (pil != null) {
                        val yuzde = pil.yuzde
                        sonPilSeviyesi = yuzde
                        
                        // ═══ ۱. هشدار pil بحرانی (< 15%) ═══
                        if (Settings.pilKritikUyariAktif && yuzde < Settings.pilKritikEsik) {
                            kritikUyariGonder(yuzde)
                        }
                        // ═══ ۲. هشدار pil کم (< 30%) ═══
                        else if (Settings.pilDusukUyariAktif && yuzde < Settings.pilDusukEsik) {
                            dusukUyariGonder(yuzde)
                        }
                        // ═══ ۳. pil عادی (هر 15 دقیقه) ═══
                        else {
                            normalBildirimGonder(yuzde, pil.durum)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Hata: ${e.message}")
                }
                
                // هر N دقیقه
                delay(Settings.pilBildirimAraligiDk.toLong() * 60_000L)
            }
        }
    }

    private fun normalBildirimGonder(yuzde: Int, durum: String) {
        val baslik = "🔋 Pil: $yuzde%"
        val mesaj = "Durum: $durum"
        
        val notif = bildirimOlustur(mesaj, false, baslik)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notif)
        
        android.util.Log.d(TAG, "Normal bildirim: $yuzde%")
    }

    private fun dusukUyariGonder(yuzde: Int) {
        val baslik = "⚠️ Pil azaldı: $yuzde%"
        val mesaj = "Şarj etmen iyi olur denizçim"
        
        val notif = bildirimOlustur(mesaj, true, baslik, WARN_NOTIF_ID)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(WARN_NOTIF_ID, notif)
        
        android.util.Log.d(TAG, "Uyarı: pil düşük — $yuzde%")
    }

    private fun kritikUyariGonder(yuzde: Int) {
        val baslik = "🚨 Pil kritik: $yuzde%"
        val mesaj = "Hemen şarja tak denizçim!"
        
        val notif = bildirimOlustur(mesaj, true, baslik, WARN_NOTIF_ID)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(WARN_NOTIF_ID, notif)
        
        android.util.Log.d(TAG, "Kritik uyarı: $yuzde%")
    }

    private fun bildirimOlustur(mesaj: String, acil: Boolean, baslik: String = "EDA Pil İzleyici"): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(this, if (acil) CHANNEL_WARN_ID else CHANNEL_ID)
            .setContentTitle(baslik)
            .setContentText(mesaj)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentIntent(pending)
            .setAutoCancel(false)
            .setOngoing(!acil)
        
        if (acil) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        }
        
        return builder.build()
    }

    private fun bildirimOlustur(mesaj: String, acil: Boolean, baslik: String, id: Int): Notification {
        return bildirimOlustur(mesaj, acil, baslik)
    }

    private fun kanallariOlustur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // کانال عادی
            val normal = NotificationChannel(
                CHANNEL_ID,
                "EDA Pil Bildirimi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Her 15 dakikada pil durumu"
            }
            nm.createNotificationChannel(normal)
            
            // کانال هشدار
            val warn = NotificationChannel(
                CHANNEL_WARN_ID,
                "EDA Pil Uyarıları",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pil azaldığında uyarı"
                enableVibration(true)
            }
            nm.createNotificationChannel(warn)
        }
    }

    override fun onDestroy() {
        notifierJob?.cancel()
        serviceScope.cancel()
        android.util.Log.d(TAG, "BatteryNotifierService durduruldu")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
