package com.deniz.eda.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import com.deniz.eda.ai.SmsTranslator
import com.deniz.eda.core.Settings
import com.deniz.eda.service.EdaForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Gelen SMS metnini mümkünse Ollama/Termux ile İstanbul Türkçesine çevirip sesli okur. */
class IncomingSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Settings.init(context)
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!Settings.smsAnnouncementEnabled && !Settings.smsTranslationEnabled) return
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

        val pending = goAsync()
        val appContext = context.applicationContext
        // Mesaj bildirimi AI/çeviri yanıtını beklemesin; model kapalı olsa bile kullanıcı haberdar olsun.
        fun speak(text: String) {
            val serviceIntent = Intent(appContext, EdaForegroundService::class.java).apply {
                action = "ACTION_SPEAK"
                putExtra("text", text)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) appContext.startForegroundService(serviceIntent)
            else appContext.startService(serviceIntent)
        }
        if (Settings.smsAnnouncementEnabled) {
            try {
                speak("Yeni mesajınız var.")
            } catch (e: Exception) {
                Log.e("EDA-SMS", "Mesaj bildirimi başlatılamadı: " + e.message, e)
            }
        }
        if (!Settings.smsTranslationEnabled) {
            pending.finish()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val translated = withTimeoutOrNull(75_000L) {
                    SmsTranslator.translateToTurkish(appContext, body)
                }?.trim()?.takeIf { it.isNotBlank() && it.length < 5000 }
                val text = if (translated == null) {
                    "Mesaj geldi, ancak çeviri yapılamadı. Lütfen telefonunu kontrol et."
                } else if (sender.isBlank()) {
                    "Mesajın Türkçe çevirisi: $translated"
                } else {
                    "$sender kişisinden gelen mesajın Türkçe çevirisi: $translated"
                }
                speak(text)
                Log.i("EDA-SMS", if (translated != null) "SMS translated to Turkish and sent to speech service" else "Translation failed; source text was not spoken")
            } catch (e: Exception) {
                Log.e("EDA-SMS", "SMS işleme hatası: ${e.message}", e)
            } finally {
                pending.finish()
            }
        }
    }
}
