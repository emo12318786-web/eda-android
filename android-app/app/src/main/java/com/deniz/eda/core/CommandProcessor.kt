package com.deniz.eda.core

import android.content.Context
import com.deniz.eda.ai.AiRouter
import com.deniz.eda.data.DiaryStore
import com.deniz.eda.data.LearningStore
import com.deniz.eda.data.MemoryStore
import com.deniz.eda.data.ReminderStore
import com.deniz.eda.utils.BatteryUtils
import com.deniz.eda.utils.CurrencyUtils
import com.deniz.eda.utils.FlashlightUtils
import com.deniz.eda.utils.JalaliCalendar
import com.deniz.eda.utils.LocationUtils
import com.deniz.eda.utils.SmsUtils
import com.deniz.eda.utils.WeatherUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class KomutSonucu {
    data class Cevap(val metin: String) : KomutSonucu()
    object UykuyaDon : KomutSonucu()
    object KapatOnayIste : KomutSonucu()
    object Kapat : KomutSonucu()
    data class GuvenlikModuDegisti(val aktif: Boolean) : KomutSonucu()
    data class ArabaModuDegisti(val aktif: Boolean) : KomutSonucu()
}

object CommandProcessor {

    private fun v(m: String, vararg k: String) = FuzzyMatcher.herhangiBiri(m, *k)

    suspend fun isle(context: Context, metinHam: String): KomutSonucu {
        val hitap = Settings.kullaniciAdi
        var metin = metinHam.lowercase(Locale.getDefault()).trim()

        LearningStore.bul(context, metin)?.let { ogrenilen -> metin = ogrenilen }

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

            v(metin, "konum", "neredeyim", "nerdeyim") ->
                KomutSonucu.Cevap(LocationUtils.konumMetni(context, hitap))

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

            v(metin, "saat", "sat", "sad") -> {
                val saat = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                KomutSonucu.Cevap("Saat $saat $hitap.")
            }

            v(metin, "tarih", "tarik") -> {
                val (tarih, gunAdi) = JalaliCalendar.bugununSemsiTarihi()
                KomutSonucu.Cevap("Bugün $tarih, $gunAdi $hitap.")
            }

            v(metin, "pil", "pıl", "batarya", "sarj", "şarj") ->
                KomutSonucu.Cevap(BatteryUtils.pilDurumuMetni(context, hitap))

            v(metin, "yardım", "yardim", "ne yapabilirsin") -> KomutSonucu.Cevap(
                "Saat, tarih, pil, fener, hatırlatma, hafıza, günlük, hava durumu, dolar/altın, " +
                        "konum, mesaj gönderme, güvenlik modu, araba modu ve serbest sohbeti biliyorum $hitap."
            )

            else -> {
                val aiCevap = AiRouter.sor(metinHam)
                if (aiCevap != null) KomutSonucu.Cevap(aiCevap)
                else KomutSonucu.Cevap("Anlayamadım $hitap, ya da şu an internetim yok.")
            }
        }
    }
}