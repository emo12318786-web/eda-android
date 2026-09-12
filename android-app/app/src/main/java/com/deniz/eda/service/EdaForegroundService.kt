package com.deniz.eda.service

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
 * Orijinal Python surumundeki başlat() (uyku dongusu) + komut_dongusu() (aktif mod)
 * ikilisinin tek bir Android Foreground Service icindeki karsiligidir.
 *
 * Mimari notu: Termux surumunde "dusuk pil tuketimi" icin manuel wake-lock
 * acip/kapatma, RMS tabanli sessizlik filtresi ve Google/Whisper zincirini
 * uyku modunda devre disi birakma gibi hileler gerekiyordu. Burada bunlarin
 * cogu gerek kalmiyor cunku:
 *  - Foreground Service + bildirim, Android'in kendi Doze/App-Standby
 *    kurallarindan servisi zaten koruyor (manuel wake-lock hilesine gerek yok).
 *  - Android SpeechRecognizer, sesi sisteme gondermeden once kendi VAD'i ile
 *    sessizligi zaten eliyor (bizim yazdigimiz RMS filtresinin native karsiligi
 *    isletim sistemi tarafindan hazir geliyor).
 *  - EXTRA_PREFER_OFFLINE ile uyku modunda (sadece uyanma kelimesi icin) agir
 *    online tanima yerine cihaz uzerindeki hafif modeli tercih ediyoruz.
 */
class EdaForegroundService : Service(), TextToSpeech.OnInitListener {

    enum class Mod { UYKU, AKTIF }

    private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null
    private var mod = Mod.UYKU
    private var kapatOnayBekleniyor = false

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var pilJob: Job? = null
    private var hatirlatmaJob: Job? = null
    private var dinlemeAktif = false
    private var ttsHazir = false

    // FAZ 2: guvenlik modu - hareket algilanirsa TTS ile yuksek uyari verir.
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

        val bildirim = NotificationHelper.bildirimOlustur(this, getString(com.deniz.eda.R.string.notif_sleeping))
        ServiceCompat.startForeground(
            this, NotificationHelper.NOTIF_ID, bildirim,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(recognitionListener)
        }

        pilBildirimBaslat()
        hatirlatmaKontrolBaslat()

        // Servis yeniden baslatildiysa (orn. telefon acilinca) daha once
        // acik birakilmis guvenlik modunu geri yukle.
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
        // TTS hazir olur olmaz dinlemeye basla
        baslatDinleme()
    }

    // --- PIL BILDIRIMI (her N dakikada bir, mod ne olursa olsun) ---
    private fun pilBildirimBaslat() {
        pilJob?.cancel()
        pilJob = serviceScope.launch {
            while (isActive) {
                delay(Settings.pilBildirimAraligiDk * 60_000L)
                val bilgi = BatteryUtils.pilBilgisiAl(this@EdaForegroundService)
                if (bilgi != null) {
                    konus("Pil yüzde ${bilgi.yuzde} ${Settings.kullaniciAdi}.")
                }
            }
        }
    }

    // --- HATIRLATMA KONTROLU (her 30 saniyede bir, mod ne olursa olsun) ---
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

    // --- DINLEME DONGUSU ---
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as android.media.AudioManager }

    private fun baslatDinleme() {
        if (dinlemeAktif) return
        dinlemeAktif = true
        val uykuModu = mod == Mod.UYKU
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            // Uyku modunda (sadece uyanma kelimesi icin) cihaz-ustu/hafif tanimayi tercih et.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, uykuModu)
            // Cok erken "ERROR_SPEECH_TIMEOUT"/"ERROR_NO_MATCH" verip hemen yeniden
            // baslamasini (ve bip sesinin ust uste binmesini) onlemek icin sessizlik
            // toleransini uzatiyoruz.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
        }
        sistemBipSesiniGecicKapat()
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            dinlemeAktif = false
            yenidenDenemeyiPlanla()
        }
    }

    /**
     * Android'in "dinlemeye basladi" bip sesi genelde STREAM_MUSIC uzerinden
     * calinir. Surekli yeniden baslama dongusunde bu bip'in ust uste/pes pese
     * calmasi rahatsiz edici oluyor - dinlemeyi baslatirken kisa sureligine
     * susturup otomatik geri aciyoruz.
     */
    private fun sistemBipSesiniGecicKapat() {
        try {
            audioManager.adjustStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.ADJUST_MUTE,
                0
            )
        } catch (e: Exception) {
            // Bazi cihazlarda bu stream'i susturmaya izin verilmeyebilir - sorun degil.
        }
        serviceScope.launch {
            delay(800L)
            try {
                audioManager.adjustStreamVolume(
                    android.media.AudioManager.STREAM_MUSIC,
                    android.media.AudioManager.ADJUST_UNMUTE,
                    0
                )
            } catch (e: Exception) {
            }
        }
    }

    private fun yenidenDenemeyiPlanla() {
        serviceScope.launch {
            delay(if (mod == Mod.UYKU) Settings.uykuBeklemeAraligiMs else 300L)
            baslatDinleme()
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            dinlemeAktif = false
            val metin = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            metniIsle(metin)
        }

        override fun onError(error: Int) {
            dinlemeAktif = false
            // ERROR_NO_MATCH / ERROR_SPEECH_TIMEOUT gibi hatalar uyku modunda normaldir
            // (ortam sessiz) - sadece dongude devam ediyoruz, pil dostu bekleme ile.
            yenidenDenemeyiPlanla()
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun metniIsle(metin: String?) {
        if (mod == Mod.UYKU) {
            if (WakeWordDetector.uyandiMi(metin)) {
                mod = Mod.AKTIF
                bildirimGuncelle(getString(com.deniz.eda.R.string.notif_active))
                konus("Buradayım ${Settings.kullaniciAdi}! Dinliyorum.") { baslatDinleme() }
            } else {
                baslatDinleme()
            }
            return
        }

        // --- AKTIF MOD ---
        if (kapatOnayBekleniyor) {
            kapatOnayBekleniyor = false
            val onaylandi = metin != null && listOf("evet", "eminim", "tamam", "kapat")
                .any { it in metin.lowercase(Locale.getDefault()) }
            if (onaylandi) {
                konus("Görüşmek üzere ${Settings.kullaniciAdi}.") { stopSelf() }
            } else {
                konus("Tamam, kapatmıyorum ${Settings.kullaniciAdi}.") { baslatDinleme() }
            }
            return
        }

        if (metin.isNullOrBlank()) {
            baslatDinleme()
            return
        }

        // CommandProcessor.isle askida kalabilir (AI/hava durumu/konum agdan
        // cekiyor) - bu yuzden coroutine icinde cagiriyoruz, dinleme donguyu
        // bloklamiyor.
        serviceScope.launch {
            when (val sonuc = CommandProcessor.isle(this@EdaForegroundService, metin)) {
                is KomutSonucu.Cevap -> konus(sonuc.metin) { baslatDinleme() }
                KomutSonucu.UykuyaDon -> {
                    mod = Mod.UYKU
                    bildirimGuncelle(getString(com.deniz.eda.R.string.notif_sleeping))
                    konus("Uyku moduna dönüyorum ${Settings.kullaniciAdi}.") { baslatDinleme() }
                }
                KomutSonucu.KapatOnayIste -> {
                    kapatOnayBekleniyor = true
                    konus("Kapatmak istediğine emin misin ${Settings.kullaniciAdi}?") { baslatDinleme() }
                }
                KomutSonucu.Kapat -> konus("Görüşmek üzere ${Settings.kullaniciAdi}.") { stopSelf() }
                is KomutSonucu.GuvenlikModuDegisti -> {
                    if (sonuc.aktif) securityMode.baslat() else securityMode.durdur()
                    konus(
                        if (sonuc.aktif) "Güvenlik modu açıldı ${Settings.kullaniciAdi}."
                        else "Güvenlik modu kapatıldı ${Settings.kullaniciAdi}."
                    ) { baslatDinleme() }
                }
                is KomutSonucu.ArabaModuDegisti -> {
                    konus(
                        if (sonuc.aktif) "Araba modu açıldı ${Settings.kullaniciAdi}."
                        else "Araba modu kapatıldı ${Settings.kullaniciAdi}."
                    ) { baslatDinleme() }
                }
            }
        }
    }

    private fun bildirimGuncelle(metin: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NotificationHelper.NOTIF_ID, NotificationHelper.bildirimOlustur(this, metin))
    }

    // --- TTS ---
    private fun konus(metin: String, tamamlaninca: (() -> Unit)? = null) {
        val id = "eda_${System.currentTimeMillis()}"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == id) serviceScope.launch(Dispatchers.Main) { tamamlaninca?.invoke() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == id) serviceScope.launch(Dispatchers.Main) { tamamlaninca?.invoke() }
            }
        })
        val params = Bundle()
        tts.speak(metin, TextToSpeech.QUEUE_FLUSH, params, id)
    }

    override fun onDestroy() {
        pilJob?.cancel()
        hatirlatmaJob?.cancel()
        securityMode.durdur()
        serviceScope.cancel()
        speechRecognizer?.destroy()
        if (ttsHazir) tts.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
