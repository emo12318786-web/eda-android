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

/**
 * Orijinal komut_isle_tek_direkt() / komut_isle() fonksiyonlarinin Kotlin karsiligi.
 *
 * FAZ 2: hatirlatma, hafiza, gunluk, AI sohbet zinciri, hava durumu, dolar/altin,
 * konum, guvenlik modu, araba modu, SMS ve basit ogrenme (paraphrase) eklendi.
 *
 * NOT (ogrenme sistemi hakkinda): LearningStore su an sadece PASIF bir sozluk -
 * yani "X derse Y komutunu calistir" seklinde ogretilmis eslesmeleri kullanir,
 * ama hicbir yerde otomatik olarak yeni eslesme OGRETILMIYOR (orijinal Python
 * script'teki tam otomatik ogrenme mantigi konusma baglamina ihtiyac duyuyordu,
 * bu FAZ'da o kismi basitlestirdik). Yeni bir eslesme eklemek istersen simdilik
 * "öğret KELIME komutu KOMUT" seklinde soyleyebilirsin (asagida eklendi).
 */
sealed class KomutSonucu {
    data class Cevap(val metin: String) : KomutSonucu()
    object UykuyaDon : KomutSonucu()
    object KapatOnayIste : KomutSonucu()
    object Kapat : KomutSonucu()
    data class GuvenlikModuDegisti(val aktif: Boolean) : KomutSonucu()
    data class ArabaModuDegisti(val aktif: Boolean) : KomutSonucu()
}

object CommandProcessor {

    suspend fun isle(context: Context, metinHam: String): KomutSonucu {
        val hitap = Settings.kullaniciAdi
        var metin = metinHam.lowercase(Locale.getDefault()).trim()

        // Daha once ogretilmis bir eslesme varsa, kanonik komuta çevir.
        LearningStore.bul(context, metin)?.let { ogrenilen -> metin = ogrenilen }

        // --- "öğret KELIME komutu KOMUT" - manuel ogretme ---
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

        // NOT: "fener kapat" gibi bilesik ifadeler, genel "kapat" (uygulamayi
        // kapat) komutundan ONCE kontrol edilmeli - yoksa "kapat" kelimesi
        // gecen her cumle yanlislikla kapanma onayi ister. Ayni mantik butun
        // "kapat" iceren alt-komutlar icin (guvenlik/araba modu kapat vb.)
        // gecerli, bu yuzden hepsi genel "kapat" satirindan ONCE.
        return when {
            "fener" in metin && "kapat" in metin -> {
                val basarili = FlashlightUtils.kapat(context)
                KomutSonucu.Cevap(if (basarili) "Fener kapatıldı $hitap." else "Fener kapatılamadı $hitap.")
            }
            "fener" in metin -> {
                val basarili = FlashlightUtils.ac(context)
                KomutSonucu.Cevap(if (basarili) "Fener açıldı $hitap." else "Fener açılamadı $hitap.")
            }

            "güvenlik" in metin && "kapat" in metin -> {
                Settings.guvenlikModuAktif = false
                KomutSonucu.GuvenlikModuDegisti(false)
            }
            "güvenlik" in metin -> {
                Settings.guvenlikModuAktif = true
                KomutSonucu.GuvenlikModuDegisti(true)
            }

            "araba modu" in metin && "kapat" in metin -> {
                Settings.arabaModuAktif = false
                KomutSonucu.ArabaModuDegisti(false)
            }
            "araba modu" in metin -> {
                Settings.arabaModuAktif = true
                KomutSonucu.ArabaModuDegisti(true)
            }

            "hatırlatmaları temizle" in metin || "hatırlatmalarımı temizle" in metin -> {
                ReminderStore.tumunuTemizle(context)
                KomutSonucu.Cevap("Bütün hatırlatmaları temizledim $hitap.")
            }
            "hatırlat" in metin -> {
                val saatEslesme = Regex("saat\\s*(\\d{1,2})(?:[:.](\\d{2}))?").find(metin)
                if (saatEslesme != null) {
                    val saat = saatEslesme.groupValues[1].toInt().coerceIn(0, 23)
                    var dakika = saatEslesme.groupValues[2].takeIf { it.isNotBlank() }?.toInt() ?: 0
                    if ("buçuk" in metin) dakika = 30
                    ReminderStore.ekle(context, metin, saat, dakika)
                    KomutSonucu.Cevap("Saat %02d:%02d için hatırlatma kurdum $hitap.".format(saat, dakika))
                } else {
                    KomutSonucu.Cevap("Kaçta hatırlatmamı istersin $hitap? \"saat 8 hatırlat\" gibi söyleyebilirsin.")
                }
            }
            "hatırlatma" in metin && ("listele" in metin || "neler" in metin || "var mı" in metin) ->
                KomutSonucu.Cevap(ReminderStore.listeMetni(context))

            "günlüğe yaz" in metin || "günlüğe ekle" in metin -> {
                val icerik = metin.substringAfter("günlüğe yaz").substringAfter("günlüğe ekle").trim()
                DiaryStore.ekle(context, icerik.ifBlank { metin })
                KomutSonucu.Cevap("Günlüğe yazdım $hitap.")
            }
            "günlüğü göster" in metin || "günlük göster" in metin || "günlüğü oku" in metin ->
                KomutSonucu.Cevap(DiaryStore.goster(context))

            "hafızaya ekle" in metin || "hafızaya al" in metin || "not al" in metin -> {
                val icerik = metin.substringAfter("hafızaya ekle")
                    .substringAfter("hafızaya al")
                    .substringAfter("not al").trim()
                MemoryStore.ekle(context, icerik.ifBlank { metin })
                KomutSonucu.Cevap("Hafızama ekledim $hitap.")
            }
            "hafızamda ne var" in metin || "hafızayı göster" in metin ->
                KomutSonucu.Cevap(MemoryStore.goster(context))

            "mesaj gönder" in metin || "sms gönder" in metin -> {
                val numara = SmsUtils.numarayiAyikla(metin)
                if (numara == null) {
                    KomutSonucu.Cevap("Numarayı anlayamadım $hitap, tekrar söyler misin?")
                } else {
                    val icerik = metin.replace(numara, "").replace("mesaj gönder", "").replace("sms gönder", "").trim()
                    val basarili = SmsUtils.gonder(context, numara, icerik.ifBlank { "Eda üzerinden gönderildi." })
                    KomutSonucu.Cevap(if (basarili) "Mesajı gönderdim $hitap." else "Mesaj gönderilemedi, SMS izni verildi mi $hitap?")
                }
            }

            "konum" in metin || "neredeyim" in metin ->
                KomutSonucu.Cevap(LocationUtils.konumMetni(context, hitap))

            "hava" in metin -> {
                val konum = LocationUtils.sonBilinenKonum(context)
                if (konum == null) {
                    KomutSonucu.Cevap("Hava durumu için önce konumunu almam lazım $hitap, GPS açık mı?")
                } else {
                    KomutSonucu.Cevap(WeatherUtils.havaDurumuMetni(konum.enlem, konum.boylam, hitap))
                }
            }

            "dolar" in metin || "altın" in metin -> KomutSonucu.Cevap(CurrencyUtils.fiyatlariGetir(hitap))

            "uyku" in metin -> KomutSonucu.UykuyaDon

            // Bilesik ifadeler elendikten sonra artik genel "kapat" guvenle
            // "uygulamayi kapat" anlamina gelir.
            "kapat" in metin -> KomutSonucu.KapatOnayIste

            "saat" in metin -> {
                val saat = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                KomutSonucu.Cevap("Saat $saat $hitap.")
            }

            "tarih" in metin -> {
                val (tarih, gunAdi) = JalaliCalendar.bugununSemsiTarihi()
                KomutSonucu.Cevap("Bugün $tarih, $gunAdi $hitap.")
            }

            "pil" in metin -> KomutSonucu.Cevap(BatteryUtils.pilDurumuMetni(context, hitap))

            "yardım" in metin || "ne yapabilirsin" in metin -> KomutSonucu.Cevap(
                "Saat, tarih, pil, fener, hatırlatma, hafıza, günlük, hava durumu, dolar/altın, " +
                        "konum, mesaj gönderme, güvenlik modu, araba modu ve serbest sohbeti biliyorum $hitap."
            )

            else -> {
                // Bilinen hicbir komutla eslesmedi - serbest sohbet olarak AI zincirine sor.
                val aiCevap = AiRouter.sor(metinHam)
                if (aiCevap != null) KomutSonucu.Cevap(aiCevap)
                else KomutSonucu.Cevap("Anlayamadım $hitap, ya da şu an internetim yok.")
            }
        }
    }
}
