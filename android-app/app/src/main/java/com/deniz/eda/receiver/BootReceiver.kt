package com.deniz.eda.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.deniz.eda.service.EdaForegroundService

/**
 * Orijinal Termux:Boot davranisinin native karsiligi. NOT: Android 6+'da
 * RECORD_AUDIO izni onceden en az bir kez Activity uzerinden kullaniciya
 * sorulup verilmis olmasi gerekir - aksi halde servis izinsiz baslayip
 * hemen hata verir. Bu yuzden ilk kurulumda uygulamayi bir kez elle
 * acip "Eda'yı Başlat"a basmak gerekiyor.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        
        // ═══ EDA Servisi ═══
        val servisIntent = Intent(context, EdaForegroundService::class.java)
        ContextCompat.startForegroundService(context, servisIntent)
        
        // ═══ Battery Notifier (bağımsız pil servisi) ═══
        val pilIntent = Intent(context, com.deniz.eda.service.BatteryNotifierService::class.java)
        ContextCompat.startForegroundService(context, pilIntent)
    }
}
