package com.deniz.eda.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Reminder(
    val id: Int,
    val metin: String,
    val saat: Int,
    val dakika: Int,
    var yapildi: Boolean = false
)

/** Orijinal hatirlatma_yukle/kaydet/eda_ile_hatırlat/listele/temizle fonksiyonlarinin karsiligi. */
object ReminderStore {
    private const val DOSYA = "hatirlatmalar.json"

    fun hepsi(context: Context): List<Reminder> {
        val dizi = JsonFileStore.okuDizi(context, DOSYA)
        val liste = mutableListOf<Reminder>()
        for (i in 0 until dizi.length()) {
            val o = dizi.getJSONObject(i)
            liste.add(
                Reminder(
                    id = o.optInt("id", i),
                    metin = o.optString("metin", "Hatırlatma"),
                    saat = o.optInt("saat"),
                    dakika = o.optInt("dakika"),
                    yapildi = o.optBoolean("yapildi", false)
                )
            )
        }
        return liste
    }

    private fun kaydet(context: Context, liste: List<Reminder>) {
        val dizi = JSONArray()
        for (r in liste) {
            dizi.put(
                JSONObject().apply {
                    put("id", r.id); put("metin", r.metin)
                    put("saat", r.saat); put("dakika", r.dakika)
                    put("yapildi", r.yapildi)
                }
            )
        }
        JsonFileStore.yazDizi(context, DOSYA, dizi)
    }

    fun ekle(context: Context, metin: String, saat: Int, dakika: Int): Reminder {
        val liste = hepsi(context).toMutableList()
        val yeniId = (liste.maxOfOrNull { it.id } ?: 0) + 1
        val yeni = Reminder(yeniId, metin.ifBlank { "Hatırlatma" }, saat, dakika)
        liste.add(yeni)
        kaydet(context, liste)
        return yeni
    }

    fun tumunuTemizle(context: Context) = kaydet(context, emptyList())

    fun tamamlandiIsaretle(context: Context, id: Int) {
        val liste = hepsi(context).map { if (it.id == id) it.copy(yapildi = true) else it }
        kaydet(context, liste)
    }

    fun bekleyenler(context: Context): List<Reminder> = hepsi(context).filter { !it.yapildi }

    fun listeMetni(context: Context): String {
        val bekleyenler = bekleyenler(context)
        if (bekleyenler.isEmpty()) return "Bekleyen hatırlatman yok denizçim."
        val parcalar = bekleyenler.take(5).map {
            "Saat %02d:%02d - %s".format(it.saat, it.dakika, it.metin)
        }
        return "Hatırlatmaların: " + parcalar.joinToString(". ")
    }
}
