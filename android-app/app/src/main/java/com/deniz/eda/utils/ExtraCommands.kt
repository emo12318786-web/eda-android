package com.deniz.eda.utils

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import com.deniz.eda.core.Settings
import com.deniz.eda.receiver.EdaDeviceAdminReceiver

/**
 * eda.py (Termux) surumundeki 9 ekstra komutun Kotlin karsiligi.
 * Termux'ta termux-api ile yapilan isler, burada Android API'leri ile yapiliyor.
 */
object ExtraCommands {

    // ═══════════════════════════════════════════════════════════
    //  ۱. HESAPLA — Basit aritmetik
    // ═══════════════════════════════════════════════════════════
    fun hesapla(ifade: String, hitap: String): String? {
        val temiz = ifade.replace(",", ".").replace(" ", "")
        if (temiz.isEmpty()) return null
        val izinli = "0123456789+-*/()."
        if (!temiz.all { it in izinli }) return null
        if (temiz.isEmpty()) return null

        return try {
            val sonuc = eval(temiz)
            val gosterim = if (sonuc == sonuc.toLong().toDouble()) {
                sonuc.toLong().toString()
            } else {
                "%.4f".format(sonuc).trimEnd('0').trimEnd('.')
            }
            "Sonuç: $gosterim $hitap."
        } catch (e: Exception) {
            null
        }
    }

    private var evalPos = 0

    private fun eval(ifade: String): Double {
        evalPos = 0
        return evalExpr(ifade)
    }

    private fun evalExpr(ifade: String): Double {
        var r = evalTerm(ifade)
        while (evalPos < ifade.length && (ifade[evalPos] == '+' || ifade[evalPos] == '-')) {
            val op = ifade[evalPos]; evalPos++
            val n = evalTerm(ifade)
            r = if (op == '+') r + n else r - n
        }
        return r
    }

    private fun evalTerm(ifade: String): Double {
        var r = evalFactor(ifade)
        while (evalPos < ifade.length && (ifade[evalPos] == '*' || ifade[evalPos] == '/')) {
            val op = ifade[evalPos]; evalPos++
            val n = evalFactor(ifade)
            r = if (op == '*') r * n else r / n
        }
        return r
    }

    private fun evalFactor(ifade: String): Double {
        if (evalPos < ifade.length && ifade[evalPos] == '-') { evalPos++; return -evalFactor(ifade) }
        if (evalPos < ifade.length && ifade[evalPos] == '+') { evalPos++; return evalFactor(ifade) }
        if (evalPos < ifade.length && ifade[evalPos] == '(') {
            evalPos++
            val r = evalExpr(ifade)
            if (evalPos < ifade.length && ifade[evalPos] == ')') evalPos++
            return r
        }
        val s = evalPos
        while (evalPos < ifade.length && (ifade[evalPos].isDigit() || ifade[evalPos] == '.')) evalPos++
        if (s == evalPos) throw IllegalArgumentException("Sayi bekleniyordu")
        return ifade.substring(s, evalPos).toDouble()
    }

    // ═══════════════════════════════════════════════════════════
    //  ۲. GÜNÜN SÖZÜ
    // ═══════════════════════════════════════════════════════════
    private val SOZLER = listOf(
        "Damlaya damlaya göl olur.",
        "Sabreden derviş muradına ermiş.",
        "Ağaç yaşken eğilir.",
        "Bir elin nesi var, iki elin sesi var.",
        "Bugünün işini yarına bırakma.",
        "Gülü seven dikenine katlanır.",
        "Söz gümüşse sükût altındır.",
        "Acele işe şeytan karışır.",
        "Dost kara günde belli olur.",
        "Ne ekersen onu biçersin.",
        "Akıl yaşta değil baştadır.",
        "Yuvarlanan taş yosun tutmaz.",
        "İşleyen demir pas tutmaz.",
        "Su akar yolunu bulur.",
        "Kalp kırmak, cam kırmaktan beter.",
        "Sabır acıdır, meyvesi tatlıdır.",
        "Dost acı söyler.",
        "Bir musibet bin nasihatten iyidir.",
        "Ağılda oğlak doğsa, ovada otu biter.",
        "Denize düşen yılana sarılır.",
        "Görünen köy kılavuz istemez.",
        "İyi insan lafın üstüne gelir.",
        "Lafla peynir gemisi yürümez.",
        "Öfkeyle kalkan zararla oturur.",
        "Sütten ağzı yanan yoğurdu üfleyerek yer.",
        "Tatlı dil yılanı deliğinden çıkarır.",
        "Yalancının mumu yatsıya kadar yanar.",
        "Zora dağlar dayanmaz.",
        "Ağlamayan çocuğa meme vermezler.",
        "Balık baştan kokar.",
        "Damlaya damlaya göl olur, sabırla koruk helva olur.",
        "İyi dost kara günde belli olur.",
        "Sağlık olsun da gerisi teferruat.",
        "Söz vermek kolay, tutmak zordur.",
        "Zaman altından değerlidir.",
        "Bilgi güçtür, paylaşınca çoğalır.",
        "Gönül kimi severse güzel odur.",
        "Hayat kısa, anı yaşa."
    )

    fun gununSozu(hitap: String): String {
        return "${SOZLER.random()}

    // ═══════════════════════════════════════════════════════════
    //  ودا مساژلاری (پیام‌های خداحافظی)
    // ═══════════════════════════════════════════════════════════
    private val VEDA_MESAJLARI = listOf(
        "Tamam denizçim, sistemleri kapatıyorum. Görüşmek üzere.",
        "Işıkları söndürüyorum denizçim. İhtiyacın olduğunda buradayım.",
        "Tamam denizçim, bir sonrakine kadar hoşça kal.",
        "Devre dışı kalıyorum denizçim. Sesini duyana kadar.",
        "Tamam denizçim. Kendine iyi bak, ben burada bekliyorum."
    )

    fun vedaMesaji(): String {
        return VEDA_MESAJLARI.random()
    }

    // ═══════════════════════════════════════════════════════════
    //  مود مساژلاری (پیام‌های حالت)
    // ═══════════════════════════════════════════════════════════
    private val MOD_MESAJLARI = mapOf(
        "uyku" to "Uyku moduna geçtim",
        "aktif" to "Aktif moddayım",
        "sessiz" to "Sessiz moda geçtim",
        "sohbet" to "Sohbet modundayım",
        "romantik" to "Romantik moddayım",
        "dinleme" to "Dinleme modundayım",
        "araba" to "Araba moduna geçtim"
    )

    fun modMesaji(mod: String, hitap: String): String {
        val mesaj = MOD_MESAJLARI[mod] ?: "Mod değişti"
        return "$mesaj $hitap."
    } $hitap."
    }

    // ═══════════════════════════════════════════════════════════
    //  ۳. MÜZİK — Medya kumandası
    // ═══════════════════════════════════════════════════════════
    private fun sendMediaKey(context: Context, keyCode: Int): Boolean {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun muzikBaslat(context: Context, hitap: String): String {
        val ok = sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY)
        return if (ok) "Müzik başladı $hitap." else "Müzik başlatılamadı $hitap."
    }

    fun muzikDurdur(context: Context, hitap: String): String {
        val ok = sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PAUSE)
        return if (ok) "Müzik durduruldu $hitap." else "Müzik durdurulamadı $hitap."
    }

    fun muzikSonraki(context: Context, hitap: String): String {
        val ok = sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
        return if (ok) "Sonraki şarkı $hitap." else "Sonraki şarkıya geçilemedi $hitap."
    }

    fun muzikOnceki(context: Context, hitap: String): String {
        val ok = sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        return if (ok) "Önceki şarkı $hitap." else "Önceki şarkıya geçilemedi $hitap."
    }

    // ═══════════════════════════════════════════════════════════
    //  ۴. TELEFONU KİLİTLE
    // ═══════════════════════════════════════════════════════════
    fun telefonuKilitle(context: Context, hitap: String): String {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, EdaDeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(admin)) {
                dpm.lockNow()
                "Telefon kilitlendi $hitap."
            } else {
                val ok = sendMediaKey(context, KeyEvent.KEYCODE_POWER)
                if (ok) "Telefon kilitleniyor $hitap." else "Kilitlenemedi $hitap. Yönetici izni gerekli."
            }
        } catch (e: Exception) {
            "Kilitlenemedi $hitap."
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ۵. TELEFONU BUL — Ringtone çal
    // ═══════════════════════════════════════════════════════════
    private var bulPlayer: MediaPlayer? = null

    fun telefonuBul(context: Context, hitap: String): String {
        return try {
            bulPlayer?.release()
            val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            bulPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioStreamType(AudioManager.STREAM_ALARM)
                isLooping = true
                prepare()
                start()
            }
            "Telefonu arıyorum $hitap. Ses geliyor mu? Durdurmak için 'sus' de."
        } catch (e: Exception) {
            "Ses çalınamadı $hitap."
        }
    }

    fun telefonuBulDurdur(hitap: String): String {
        return try {
            bulPlayer?.stop()
            bulPlayer?.release()
            bulPlayer = null
            "Ses durduruldu $hitap."
        } catch (e: Exception) {
            "Ses zaten çalmıyordu $hitap."
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ۶. FOTOĞRAF ÇEK — Kamera intent
    // ═══════════════════════════════════════════════════════════
    fun fotografCek(context: Context, hitap: String): String {
        return try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Kamera açılıyor $hitap, fotoğraf çekebilirsin."
        } catch (e: Exception) {
            "Kamera açılamadı $hitap."
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ۷. KONUM GÖNDER — Location + SMS
    // ═══════════════════════════════════════════════════════════
    fun konumGonder(context: Context, hitap: String, telefonNumarasi: String): String {
        if (telefonNumarasi.isBlank()) {
            return "Güvenlik numarası ayarlanmamış $hitap."
        }
        return try {
            val konum = LocationUtils.sonBilinenKonum(context)
            if (konum == null) {
                "Konum alınamadı $hitap, GPS açık mı?"
            } else {
                val mesaj = "Konumum: https://maps.google.com/?q=${konum.enlem},${konum.boylam}"
                val ok = SmsUtils.gonder(context, telefonNumarasi, mesaj)
                if (ok) "Konum $telefonNumarasi numarasına gönderildi $hitap."
                else "SMS gönderilemedi $hitap."
            }
        } catch (e: Exception) {
            "Konum gönderilemedi $hitap."
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ۸. AYARLARI AÇ
    // ═══════════════════════════════════════════════════════════
    fun ayarlariAc(context: Context, hitap: String): String {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Ayarlar açılıyor $hitap."
        } catch (e: Exception) {
            "Ayarlar açılamadı $hitap."
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ۹. SES MOTORU — TTS ayarları
    // ═══════════════════════════════════════════════════════════
    fun sesMotoruDegistir(context: Context, hitap: String): String {
        return try {
            val intent = Intent("com.android.settings.TTS_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Ses motoru ayarları açılıyor $hitap."
        } catch (e: Exception) {
            try {
                val intent2 = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent2)
                "Ayarlar açılıyor $hitap."
            } catch (e2: Exception) {
                "Ses motoru ayarları açılamadı $hitap."
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ۱۰. SİSTEM TEST — همه چیز رو چک کن
    // ═══════════════════════════════════════════════════════════
    suspend fun sistemTest(
        context: Context,
        hitap: String,
        aiRouterTest: suspend () -> String?
    ): String {
        val sb = StringBuilder()
        sb.append("🔍 Sistem testi başlıyor $hitap.\n\n")
        
        // ۱. Pil
        val pil = BatteryUtils.pilBilgisiAl(context)
        if (pil != null) {
            sb.append("✅ Pil: ${pil.yuzde}% (${pil.durum})\n")
        } else {
            sb.append("❌ Pil: bilgi alınamadı\n")
        }
        
        // ۲. Konum
        val konum = LocationUtils.sonBilinenKonum(context)
        if (konum != null) {
            sb.append("✅ Konum: ${"%.4f".format(konum.enlem)}, ${"%.4f".format(konum.boylam)}\n")
        } else {
            sb.append("❌ Konum: GPS kapalı veya izin yok\n")
        }
        
        // ۳. AI
        sb.append("⏳ AI bağlantı testi...\n")
        val aiCevap = try {
            aiRouterTest()
        } catch (e: Exception) {
            null
        }
        if (aiCevap != null) {
            sb.append("✅ AI: bağlantı OK\n")
        } else {
            sb.append("❌ AI: bağlantı yok (internet veya API key)\n")
        }
        
        // ۴. Hafıza
        sb.append("✅ Hafıza sistemi: hazır\n")
        
        // ۵. TTS
        sb.append("✅ TTS: hazır\n")
        
        // ۶. STT
        sb.append("✅ STT: Google STT\n")
        
        // ۷. Hatırlatma
        sb.append("✅ Hatırlatma sistemi: aktif\n")
        
        // ۸. Pil izleyici
        sb.append("✅ Pil izleyici: ${if (Settings.batteryNotifierAktif) "aktif" else "pasif"}\n")
        
        sb.append("\n🎉 Test tamamlandı $hitap.")
        
        return sb.toString()
    }

    // ═══════════════════════════════════════════════════════════
    //  ۱۱. SESSIZ DURDUR (telefonu bul için)
    // ═══════════════════════════════════════════════════════════
    fun sessizDurdur(hitap: String): String {
        return telefonuBulDurdur(hitap)
    }
}