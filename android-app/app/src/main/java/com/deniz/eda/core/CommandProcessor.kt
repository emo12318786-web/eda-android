package com.deniz.eda.core

import android.content.Context
import com.deniz.eda.ai.AiRouter
import com.deniz.eda.data.DiaryStore
import com.deniz.eda.data.LearningStore
import com.deniz.eda.data.MemoryStore
import com.deniz.eda.data.ReminderStore
import com.deniz.eda.utils.BatteryUtils
import com.deniz.eda.utils.CurrencyUtils
import com.deniz.eda.utils.ExtraCommands
import com.deniz.eda.utils.FlashlightUtils
import com.deniz.eda.utils.JalaliCalendar
import com.deniz.eda.utils.LocationUtils
import com.deniz.eda.utils.SmsUtils
import com.deniz.eda.utils.WeatherUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

sealed class KomutSonucu {
    data class Cevap(val metin: String) : KomutSonucu()
    object UykuyaDon : KomutSonucu()
    object KapatOnayIste : KomutSonucu()
    object Kapat : KomutSonucu()
    data class GuvenlikModuDegisti(val aktif: Boolean) : KomutSonucu()
    data class ArabaModuDegisti(val aktif: Boolean) : KomutSonucu()
}

object CommandProcessor {

    // Ev koordinatlari (kullanici kendi koordinatlarini buraya yazabilir)
    private const val EV_LAT = 38.052686
    private const val EV_LON = 46.232105

    private fun v(m: String, vararg k: String) = FuzzyMatcher.herhangiBiri(m, *k)

    // Saat ve dakikayi sozcuklere cevir (TTS "14:30" okuyamadigi icin)
    private fun saatToKelime(saat: Int): String = when (saat) {
        0 -> "gece yarısı on iki"
        1 -> "bir"
        2 -> "iki"
        3 -> "üç"
        4 -> "dört"
        5 -> "beş"
        6 -> "altı"
        7 -> "yedi"
        8 -> "sekiz"
        9 -> "dokuz"
        10 -> "on"
        11 -> "on bir"
        12 -> "on iki"
        13 -> "bir"
        14 -> "iki"
        15 -> "üç"
        16 -> "dört"
        17 -> "beş"
        18 -> "altı"
        19 -> "yedi"
        20 -> "sekiz"
        21 -> "dokuz"
        22 -> "on"
        23 -> "on bir"
        else -> saat.toString()
    }

    private fun dakikaToKelime(dakika: Int): String = when (dakika) {
        0 -> ""
        15 -> "çeyrek geçiyor"
        30 -> "buçuk"
        45 -> "çeyrek var"
        else -> "$dakika geçiyor"
    }

    suspend fun isle(context: Context, metinHam: String): KomutSonucu {
        val hitap = Settings.kullaniciAdi
        var metin = metinHam.lowercase(Locale.getDefault()).trim()

        LearningStore.bul(context, metin)?.let { ogrenilen -> metin = ogrenilen }

        // ═══ ۱۹ بخش محبت‌آمیز (بدون AI) ═══
        AffectionResponses.bul(metin, hitap)?.let { return KomutSonucu.Cevap(it) }

        if (metin.startsWith("öğret ") && " komutu " in metin) {
            val parcalar = metin.removePrefix("öğret ").split(" komutu ", limit = 2)
            if (parcalar.size == 2) {
                val kelime = parcalar[0].trim()
                val komut = parcalar[1].trim()
                if (kelime.isNotBlank() && komut.isNotBlank()) {
                    LearningStore.ogren(context, kelime, komut)
                    return KomutSonucu.Cevap("Öğrendim $hitap: \"$kelime\" dediğinde \"$komut\" yapacağım.")
                }
            }
        }

        return when {
            v(metin, "fener", "faner") && v(metin, "kapat", "kapa", "kapt") -> {
                val basarili = FlashlightUtils.kapat(context)
                KomutSonucu.Cevap(if (basarili) "Fener kapatıldı $hitap." else "Fener kapatılamadı $hitap.")
            }
            v(metin, "fener", "faner") -> {
                val basarili = FlashlightUtils.ac(context)
                KomutSonucu.Cevap(if (basarili) "Fener açıldı $hitap." else "Fener açılamadı $hitap.")
            }

            v(metin, "bip", "beep") && v(metin, "kapat", "kapa", "kapt") -> {
                Settings.beepAktif = false
                KomutSonucu.Cevap("Bip sesi kapatıldı $hitap.")
            }
            v(metin, "bip", "beep") && v(metin, "ac", "aç", "ach") -> {
                Settings.beepAktif = true
                KomutSonucu.Cevap("Bip sesi açıldı $hitap.")
            }

            v(metin, "güvenlik", "guvenlik", "guvenlık", "guwenlik") && v(metin, "kapat", "kapa") -> {
                Settings.guvenlikModuAktif = false
                KomutSonucu.GuvenlikModuDegisti(false)
            }
            v(metin, "güvenlik", "guvenlik", "guvenlık", "guwenlik") -> {
                Settings.guvenlikModuAktif = true
                KomutSonucu.GuvenlikModuDegisti(true)
            }

            v(metin, "araba", "arabe") && v(metin, "modu", "mod") && v(metin, "kapat") -> {
                Settings.arabaModuAktif = false
                KomutSonucu.ArabaModuDegisti(false)
            }
            v(metin, "araba", "arabe") && v(metin, "modu", "mod") -> {
                Settings.arabaModuAktif = true
                KomutSonucu.ArabaModuDegisti(true)
            }

            v(metin, "hatırlatma", "hatirlatma", "hatırlatmaları") && v(metin, "temizle", "sil", "temis") -> {
                ReminderStore.tumunuTemizle(context)
                KomutSonucu.Cevap("Bütün hatırlatmaları temizledim $hitap.")
            }
            v(metin, "hatırlat", "hatirlat", "hatırla", "hatirla") -> {
                val saatEslesme = Regex("saat\\s*(\\d{1,2})(?:[:.](\\d{2}))?").find(metin)
                if (saatEslesme != null) {
                    val saat = saatEslesme.groupValues[1].toInt().coerceIn(0, 23)
                    var dakika = saatEslesme.groupValues[2].takeIf { it.isNotBlank() }?.toInt() ?: 0
                    if ("buçuk" in metin || "bucuk" in metin) dakika = 30
                    ReminderStore.ekle(context, metin, saat, dakika)
                    KomutSonucu.Cevap("Saat %02d:%02d için hatırlatma kurdum $hitap.".format(saat, dakika))
                } else {
                    KomutSonucu.Cevap("Kaçta hatırlatmamı istersin $hitap? \"saat 8 hatırlat\" gibi söyleyebilirsin.")
                }
            }
            v(metin, "hatırlatma", "hatirlatma") && v(metin, "listele", "list", "neler", "göster", "goster") ->
                KomutSonucu.Cevap(ReminderStore.listeMetni(context))

            v(metin, "günlüğe", "gunluge", "günlük", "gunluk") && v(metin, "yaz", "ekle") -> {
                val icerik = metin.substringAfter("yaz").substringAfter("ekle").trim()
                DiaryStore.ekle(context, icerik.ifBlank { metin })
                KomutSonucu.Cevap("Günlüğe yazdım $hitap.")
            }
            v(metin, "günlüğü", "gunlugu", "günlük", "gunluk") && v(metin, "göster", "goster", "oku") ->
                KomutSonucu.Cevap(DiaryStore.goster(context))

            v(metin, "hafızaya", "hafizaya", "hafıza", "hafiza") && v(metin, "ekle", "al", "not") -> {
                val icerik = metin.substringAfter("ekle").substringAfter("al").substringAfter("not").trim()
                MemoryStore.ekle(context, icerik.ifBlank { metin })
                KomutSonucu.Cevap("Hafızama ekledim $hitap.")
            }
            v(metin, "hafızamda", "hafizamda", "hafızayı", "hafizayi") && v(metin, "ne", "göster", "goster", "var") ->
                KomutSonucu.Cevap(MemoryStore.goster(context))

            v(metin, "mesaj", "mesage") && v(metin, "gönder", "gonder") -> {
                val numara = SmsUtils.numarayiAyikla(metin)
                if (numara == null) {
                    KomutSonucu.Cevap("Numarayı anlayamadım $hitap, tekrar söyler misin?")
                } else {
                    val icerik = metin.replace(numara, "").replace("mesaj", "").replace("gönder", "").replace("gonder", "").trim()
                    val basarili = SmsUtils.gonder(context, numara, icerik.ifBlank { "Eda üzerinden gönderildi." })
                    KomutSonucu.Cevap(if (basarili) "Mesajı gönderdim $hitap." else "Mesaj gönderilemedi, SMS izni verildi mi $hitap?")
                }
            }

            // Konum - aktif konum ister
            v(metin, "konum", "neredeyim", "nerdeyim") -> {
                val metinCevap = LocationUtils.konumMetni(context, hitap)
                KomutSonucu.Cevap(metinCevap)
            }

            // Eve mesafe
            v(metin, "mesafe", "eve", "evden") -> {
                val konum = LocationUtils.sonBilinenKonum(context)
                if (konum == null) {
                    KomutSonucu.Cevap("Konum alınamadı $hitap, GPS'i kontrol eder misin?")
                } else {
                    val dLat = Math.toRadians(EV_LAT - konum.enlem)
                    val dLon = Math.toRadians(EV_LON - konum.boylam)
                    val a = sin(dLat / 2) * sin(dLat / 2) +
                            cos(Math.toRadians(konum.enlem)) *
                            cos(Math.toRadians(EV_LAT)) *
                            sin(dLon / 2) * sin(dLon / 2)
                    val c = 2 * asin(sqrt(a))
                    val mesafe = 6371.0 * c
                    if (mesafe < 1) {
                        KomutSonucu.Cevap("Sadece ${(mesafe * 1000).toInt()} metre kaldı! Çok yakınsın $hitap!")
                    } else {
                        KomutSonucu.Cevap("Yaklaşık ${"%.1f".format(mesafe)} kilometre kaldı $hitap.")
                    }
                }
            }

            v(metin, "hava", "hawa") -> {
                val konum = LocationUtils.sonBilinenKonum(context)
                if (konum == null) {
                    KomutSonucu.Cevap("Hava durumu için önce konumunu almam lazım $hitap, GPS açık mı?")
                } else {
                    KomutSonucu.Cevap(WeatherUtils.havaDurumuMetni(konum.enlem, konum.boylam, hitap))
                }
            }

            v(metin, "dolar", "altın", "altin") ->
                KomutSonucu.Cevap(CurrencyUtils.fiyatlariGetir(hitap))

            v(metin, "uyku", "uygu", "uyu") -> KomutSonucu.UykuyaDon

            v(metin, "kapat", "kapa", "kapt") -> KomutSonucu.KapatOnayIste

            // Saat - sozcuklerle soyle
            v(metin, "saat", "sat", "sad") -> {
                val simdi = Calendar.getInstance()
                val saatInt = simdi.get(Calendar.HOUR_OF_DAY)
                val dakikaInt = simdi.get(Calendar.MINUTE)
                val saatKelime = saatToKelime(saatInt)
                val dakikaKelime = dakikaToKelime(dakikaInt)
                val cevap = if (dakikaKelime.isBlank()) {
                    "Saat $saatKelime $hitap."
                } else {
                    "Saat $saatKelime $dakikaKelime $hitap."
                }
                KomutSonucu.Cevap(cevap)
            }

            v(metin, "tarih", "tarik") -> {
                val (tarih, gunAdi) = JalaliCalendar.bugununSemsiTarihi()
                KomutSonucu.Cevap("Bugün $tarih, $gunAdi $hitap.")
            }

            v(metin, "pil", "pıl", "batarya", "sarj", "şarj") ->
                KomutSonucu.Cevap(BatteryUtils.pilDurumuMetni(context, hitap))

            v(metin, "yardım", "yardim", "ne yapabilirsin") -> KomutSonucu.Cevap(
                "Saat, tarih, pil, fener, hatırlatma, hafıza, günlük, hava durumu, dolar/altın, " +
                        "konum, eve mesafe, mesaj gönderme, güvenlik modu, araba modu ve serbest sohbeti biliyorum $hitap."
            )

            // ═══ ۹ دستور جدید (از eda.py Termux) ═══
            
            // ۱. Hesapla — "2+3 kaç eder"
            (metin.contains("hesapla") || metin.contains("kaç eder") || metin.contains("kac eder")) -> {
                val ifade = metin
                    .replace("hesapla", "")
                    .replace("kaç eder", "")
                    .replace("kac eder", "")
                    .trim()
                ExtraCommands.hesapla(ifade, hitap)?.let { return KomutSonucu.Cevap(it) }
                KomutSonucu.Cevap("Hesaplayamadım $hitap, ifadeyi anlayamadım.")
            }
            
            // ۲. Günün sözü
            v(metin, "günün sözü", "gunun sozu", "bugünün sözü", "bugunun sozu") ->
                KomutSonucu.Cevap(ExtraCommands.gununSozu(hitap))
            
            // ۳. Müzik
            v(metin, "müzik", "muzik") && v(metin, "aç", "ac", "başlat", "baslat", "çal", "cal") ->
                KomutSonucu.Cevap(ExtraCommands.muzikBaslat(context, hitap))
            
            v(metin, "müzik", "muzik") && v(metin, "durdur", "duraklat") ->
                KomutSonucu.Cevap(ExtraCommands.muzikDurdur(context, hitap))
            
            v(metin, "müzik", "muzik", "şarkı", "sarki") && v(metin, "sonraki", "atla") ->
                KomutSonucu.Cevap(ExtraCommands.muzikSonraki(context, hitap))
            
            v(metin, "müzik", "muzik", "şarkı", "sarki") && v(metin, "önceki", "onceki") ->
                KomutSonucu.Cevap(ExtraCommands.muzikOnceki(context, hitap))
            
            // ۴. Telefonu kilitle
            v(metin, "telefonu kilitle", "ekranı kilitle", "ekrani kilitle") ->
                KomutSonucu.Cevap(ExtraCommands.telefonuKilitle(context, hitap))
            
            // ۵. Telefonu bul
            v(metin, "telefonu bul", "telefonumu bul", "telefonu ara", "telefonumu ara") ->
                KomutSonucu.Cevap(ExtraCommands.telefonuBul(context, hitap))
            
            // ۶. Fotoğraf çek
            v(metin, "fotoğraf çek", "fotograf cek", "foto çek", "foto cek", "kamera aç", "kamera ac") ->
                KomutSonucu.Cevap(ExtraCommands.fotografCek(context, hitap))
            
            // ۷. Konum gönder
            v(metin, "konumumu gönder", "konumumu gonder", "konum gönder", "konum gonder") ->
                KomutSonucu.Cevap(ExtraCommands.konumGonder(context, hitap, Settings.guvenlikNumara))
            
            // ۸. Ayarları aç
            v(metin, "ayarları aç", "ayarlari ac", "ayarları açsana", "ayarlari acsana") ->
                KomutSonucu.Cevap(ExtraCommands.ayarlariAc(context, hitap))
            
            // ۹. Ses motoru
            v(metin, "ses motoru", "ses motorunu", "sesi değiştir", "sesi degistir") ->
                KomutSonucu.Cevap(ExtraCommands.sesMotoruDegistir(context, hitap))
            
            else -> {
                val aiCevap = AiRouter.sor(metinHam)
                if (aiCevap != null) KomutSonucu.Cevap(aiCevap)
                else KomutSonucu.Cevap("Anlayamadım $hitap, ya da şu an internetim yok.")
            }
        }
    }
}