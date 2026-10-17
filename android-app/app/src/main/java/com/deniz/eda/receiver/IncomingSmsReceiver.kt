package com.deniz.eda.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import com.deniz.eda.service.EdaForegroundService

/** Gelen SMS'leri EDA'nın Türkçe TTS yoluna iletir. */
class IncomingSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            Log.e("EDA-SMS", "SMS çözümlenemedi: ${e.message}")
            return
        }
        if (messages.isEmpty()) return

        val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }.trim()
        if (body.isBlank()) return

        val text = if (sender.isBlank()) {
            "Yeni mesaj geldi. Mesaj şöyle: $body"
        } else {
            "Yeni mesaj, $sender tarafından: $body"
        }
        val serviceIntent = Intent(context, EdaForegroundService::class.java).apply {
            action = "ACTION_SPEAK"
            putExtra("text", text)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
            else context.startService(serviceIntent)
            Log.i("EDA-SMS", "Gelen SMS sesli okuma için iletildi")
        } catch (e: Exception) {
            Log.e("EDA-SMS", "EDA servisine SMS iletilemedi: ${e.message}")
        }
    }
}
