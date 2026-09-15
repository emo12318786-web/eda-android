package com.deniz.eda.service

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import com.deniz.eda.core.CommandProcessor
import com.deniz.eda.core.KomutSonucu
import com.deniz.eda.core.ReminderChecker
import com.deniz.eda.core.SecurityMode
import com.deniz.eda.core.Settings
import com.deniz.eda.core.WakeWordDetector
import com.deniz.eda.utils.BatteryUtils
import kotlinx.coroutines.*
import java.util.Locale

/**
 * Eda Foreground Service - Whisper STT ile tamamen offline calisir.
 *
 * Eski SpeechRecognizer (Google/Samsung) TAMAMEN kaldirildi.
 * Artik mikrofon -> AudioRecord -> Whisper (AAR) -> metin -> CommandProcessor
 */
class EdaForegroundService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "EdaService"
    }

    enum class Mod { UYKU, AKTIF }

    private lateinit var tts: TextToSpeech
    private lateinit var whisper: WhisperSTT
    private var mod = Mod.UYKU
    private var kapatOnayBekleniyor = false

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var pilJob: Job? = null
    private var hatirlatmaJob: Job? = null
    private var dinlemeJob: Job? = null
    private var ttsHazir = false
    private var whisperHazir = false

    private val securityMode by lazy {
        SecurityMode(this) {
            konus("Dikkat! Hareket algılandı, güvenlik modu tetiklendi!")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        NotificationHelper.channelOlustur(this)
        tts = TextToSpeech(this, this)
        whisper = WhisperSTT(this)

        // Stream_MUSIC'i zorla ac (eski mute sorunundan kalan)
        try {
            val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
            am.adjustStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.ADJUST_UNMUTE,
                0
            )
        } catch (e: Exception) { }

        val bildirim = NotificationHelper.bildirimOlustur(this, getString(com.deniz.eda.R.string.notif_sleeping))
        ServiceCompat.startForeground(
            this, NotificationHelper.NOTIF_ID, bildirim,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )

        pilBildirimBaslat()
        hatirlatmaKontrolBaslat()

        if (Settings.guvenlikModuAktif) securityMode.baslat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("tr", "TR")
        }
        ttsHazir = true

        // Whisper'i arka planda yukle
        serviceScope.launch(Dispatchers.IO) {
            Log.i(TAG, "Whisper yukleniyor...")
            whisperHazir = whisper.baslat()
            withContext(Dispatchers.Main) {
                if (whisperHazir) {
                    Log.i(TAG, "Whisper hazir - dinleme dongusu basliyor")
                    bildirimGuncelle("Eda dinliyor (offline)")
                    dinlemeDongusu()
                } else {
                    Log.e(TAG, "Whisper yuklenemedi!")
                    bildirimGuncelle("⚠️ Whisper modeli yuklenemedi")
                }
            }
        }
    }

    // --- PIL BILDIRIMI (her N dakikada bir) ---
    private fun pilBildirimBaslat() {
        pilJob?.cancel()
        pilJob = serviceScope.launch {
            while (isActive) {
                delay(Settings.pilBildirimAraligiDk * 60_000L)
                val bilgi = BatteryUtils.pilBilgisiAl(this@EdaForegroundService)
                if (bilgi != null && mod != Mod.UYKU) {
                    konus("Pil yüzde ${bilgi.yuzde} ${Settings.kullaniciAdi}.")
                }
            }
        }
    }

    // --- HATIRLATMA KONTROLU (her 30 saniyede bir) ---
    private fun hatirlatmaKontrolBaslat() {
        hatirlatmaJob?.cancel()
        hatirlatmaJob = serviceScope.launch {
            while (isActive) {
                delay(30_000L)
                val tetiklenenler = ReminderChecker.kontrolEt(this@EdaForegroundService)
                tetiklenenler.forEach { mesaj -> konus("Hatırlatma: $mesaj") }
            }
        }
    }

    // --- DINLEME DONGUSU (Whisper tabanli) ---
    private fun dinlemeDongusu() {
        dinlemeJob?.cancel()
        dinlemeJob = serviceScope.launch {
            while (isActive && whisperHazir) {
                try {
                    // Her dongude: kayit al + Whisper'a gonder
                    val sure = if (mod == Mod.UYKU) 4 else 8
                    Log.d(TAG, "Dinleniyor: ${sure}sn (mod=$mod)")
                    val metin = whisper.dinleVeMetneCevir(sure)

                    if (!metin.isNullOrBlank()) {
                        Log.i(TAG, "Duyulan metin: $metin")
                        metniIsle(metin)
                    } else {
                        // Sessizlik - kisa bekle, donguye devam
                        delay(300L)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Dinleme dongu hatasi: ${e.message}", e)
                    delay(1000L)
                }
            }
        }
    }

    private fun metniIsle(metin: String) {
        if (mod == Mod.UYKU) {
            if (WakeWordDetector.uyandiMi(metin)) {
                mod = Mod.AKTIF
                bildirimGuncelle(getString(com.deniz.eda.R.string.notif_active))
                konus("Buradayım ${Settings.kullaniciAdi}! Dinliyorum.")
            }
            return
        }

        // --- AKTIF MOD ---
        if (kapatOnayBekleniyor) {
            kapatOnayBekleniyor = false
            val onaylandi = listOf("evet", "eminim", "tamam", "kapat")
                .any { it in metin.lowercase(Locale.getDefault()) }
            if (onaylandi) {
                konus("Görüşmek üzere ${Settings.kullaniciAdi}.")
                serviceScope.launch { delay(2000); stopSelf() }
            } else {
                konus("Tamam, kapatmıyorum ${Settings.kullaniciAdi}.")
            }
            return
        }

        if (metin.isBlank()) return

        serviceScope.launch {
            when (val sonuc = CommandProcessor.isle(this@EdaForegroundService, metin)) {
                is KomutSonucu.Cevap -> konus(sonuc.metin)
                KomutSonucu.UykuyaDon -> {
                    mod = Mod.UYKU
                    bildirimGuncelle(getString(com.deniz.eda.R.string.notif_sleeping))
                    konus("Uyku moduna dönüyorum ${Settings.kullaniciAdi}.")
                }
                KomutSonucu.KapatOnayIste -> {
                    kapatOnayBekleniyor = true
                    konus("Kapatmak istediğine emin misin ${Settings.kullaniciAdi}?")
                }
                KomutSonucu.Kapat -> {
                    konus("Görüşmek üzere ${Settings.kullaniciAdi}.")
                    delay(2000); stopSelf()
                }
                is KomutSonucu.GuvenlikModuDegisti -> {
                    if (sonuc.aktif) securityMode.baslat() else securityMode.durdur()
                    konus(if (sonuc.aktif) "Güvenlik modu açıldı." else "Güvenlik modu kapatıldı.")
                }
                is KomutSonucu.ArabaModuDegisti -> {
                    konus(if (sonuc.aktif) "Araba modu açıldı." else "Araba modu kapatıldı.")
                }
            }
        }
    }

    private fun bildirimGuncelle(metin: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NotificationHelper.NOTIF_ID, NotificationHelper.bildirimOlustur(this, metin))
    }

    // --- TTS ---
    private fun konus(metin: String) {
        if (!ttsHazir) return
        val id = "eda_${System.currentTimeMillis()}"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}
        })
        val params = Bundle()
        tts.speak(metin, TextToSpeech.QUEUE_FLUSH, params, id)
    }

    override fun onDestroy() {
        pilJob?.cancel()
        hatirlatmaJob?.cancel()
        dinlemeJob?.cancel()
        securityMode.durdur()
        serviceScope.cancel()
        whisper.kapat()
        if (ttsHazir) tts.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
