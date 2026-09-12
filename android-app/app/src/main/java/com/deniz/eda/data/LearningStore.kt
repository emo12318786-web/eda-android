package com.deniz.eda.data

import android.content.Context
import com.deniz.eda.core.Settings
import org.json.JSONObject

/** Orijinal öğren_komut_eslesmesi()/öğrenmeden_bul() fonksiyonlarinin karsiligi. */
object LearningStore {
    private const val DOSYA = "ogrenme.json"

    val KOMUT_KARA_LISTE = listOf(
        "kapat", "uyku", "aktif", "fener", "pil", "saat",
        "tarih", "sohbet", "romantik", "sessiz", "araba",
        "hatırlat", "konum", "muzik", "hava", "gunluk",
        "eda", "tamam", "evet", "hayir", "kalk", "uyan"
    )

    private fun yukle(context: Context): JSONObject =
        JsonFileStore.okuNesne(
            context, DOSYA,
            JSONObject().apply { put("eslesmeler", JSONObject()); put("istatistikler", JSONObject()) }
        )

    fun ogren(context: Context, kullaniciMetni: String, edaKomutu: String) {
        if (!Settings.ogrenmeAktif) return
        val metinKucuk = kullaniciMetni.lowercase()
        if (KOMUT_KARA_LISTE.any { it in metinKucuk }) return
        if (kullaniciMetni.trim() == edaKomutu.trim()) return
        if (kullaniciMetni.length < 3 || kullaniciMetni.length > 50) return

        val ogrenme = yukle(context)
        val eslesmeler = ogrenme.getJSONObject("eslesmeler")
        var yeniOgrenildi = false
        for (kelime in metinKucuk.split(Regex("\\s+"))) {
            if (kelime.length > 2 && eslesmeler.optString(kelime) != edaKomutu) {
                eslesmeler.put(kelime, edaKomutu)
                yeniOgrenildi = true
            }
        }
        if (yeniOgrenildi) {
            val ist = ogrenme.getJSONObject("istatistikler")
            ist.put(edaKomutu, ist.optInt(edaKomutu, 0) + 1)
            JsonFileStore.yazNesne(context, DOSYA, ogrenme)
        }
    }

    fun bul(context: Context, metinHam: String): String? {
        val ogrenme = yukle(context)
        val eslesmeler = ogrenme.getJSONObject("eslesmeler")
        val metin = metinHam.lowercase()
        if (KOMUT_KARA_LISTE.any { it == metin }) return null

        val anahtarlar = eslesmeler.keys()
        while (anahtarlar.hasNext()) {
            val kelime = anahtarlar.next()
            if (kelime in metin) {
                val komut = eslesmeler.optString(kelime)
                if (kelime == komut) continue
                if (komut in KOMUT_KARA_LISTE) continue
                if (komut.length > 30) continue
                return komut
            }
        }
        return null
    }
}
