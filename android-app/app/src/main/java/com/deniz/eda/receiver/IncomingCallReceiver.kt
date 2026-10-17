package com.deniz.eda.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.deniz.eda.service.EdaForegroundService

/** Gelen aramayı bir kez duyurur; arama bitince tekrar duyuruya hazır olur. */
class IncomingCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w("EDA-CALL", "READ_PHONE_STATE izni yok")
            return
        }

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val prefs = context.getSharedPreferences("eda_call_state", Context.MODE_PRIVATE)
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (prefs.getBoolean("announced", false)) return
                prefs.edit().putBoolean("announced", true).apply()
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER).orEmpty()
                val text = if (number.isBlank()) {
                    "Gelen bir telefon araması var."
                } else {
                    "Gelen arama var. Arayan numara: $number."
                }
                val serviceIntent = Intent(context, EdaForegroundService::class.java).apply {
                    action = "ACTION_SPEAK"
                    putExtra("text", text)
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
                    else context.startService(serviceIntent)
                    Log.i("EDA-CALL", "Gelen arama EDA'ya iletildi")
                } catch (e: Exception) {
                    Log.e("EDA-CALL", "EDA servisi başlatılamadı: ${e.message}")
                    prefs.edit().putBoolean("announced", false).apply()
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> prefs.edit().putBoolean("announced", false).apply()
        }
    }
}
