package com.deniz.eda.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Orijinal hafiza_yukle/hafiza_kaydet/hafizaya_ekle/hafizayi_goster fonksiyonlarinin karsiligi. */
object MemoryStore {
    private const val DOSYA = "memory.json"
    private const val LIMIT = 1000

    fun ekle(context: Context, metin: String, kategori: String = "genel") {
        val dizi = JsonFileStore.okuDizi(context, DOSYA)
        val zaman = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        dizi.put(
            JSONObject().apply {
                put("tarih", zaman); put("kategori", kategori); put("metin", metin)
            }
        )
        JsonFileStore.yazDizi(context, DOSYA, sonNTaneAl(dizi, LIMIT))
    }

    fun goster(context: Context): String {
        val dizi = JsonFileStore.okuDizi(context, DOSYA)
        if (dizi.length() == 0) return "Hafızamda bir şey yok."
        val gosterilecek = sonNTaneAl(dizi, 10)
        val satirlar = mutableListOf("Hafızamda:")
        for (i in 0 until gosterilecek.length()) {
            satirlar.add("${i + 1}. ${gosterilecek.getJSONObject(i).optString("metin")}")
        }
        return satirlar.joinToString("\n")
    }

    private fun sonNTaneAl(dizi: JSONArray, n: Int): JSONArray {
        val sonuc = JSONArray()
        val baslangic = maxOf(0, dizi.length() - n)
        for (i in baslangic until dizi.length()) sonuc.put(dizi.get(i))
        return sonuc
    }
}
