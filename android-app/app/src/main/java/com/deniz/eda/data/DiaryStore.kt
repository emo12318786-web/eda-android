package com.deniz.eda.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Orijinal gunluk_yükle/gunluk_kaydet/gunluge_ekle/gunlugu_göster fonksiyonlarinin karsiligi. */
object DiaryStore {
    private const val DOSYA = "diary.json"
    private const val LIMIT = 1000

    fun ekle(context: Context, metin: String) {
        val dizi = JsonFileStore.okuDizi(context, DOSYA)
        val zaman = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        dizi.put(JSONObject().apply { put("tarih", zaman); put("metin", metin) })
        val kirpilmis = JSONArray()
        val baslangic = maxOf(0, dizi.length() - LIMIT)
        for (i in baslangic until dizi.length()) kirpilmis.put(dizi.get(i))
        JsonFileStore.yazDizi(context, DOSYA, kirpilmis)
    }

    fun goster(context: Context): String {
        val dizi = JsonFileStore.okuDizi(context, DOSYA)
        if (dizi.length() == 0) return "Günlük boş."
        val baslangic = maxOf(0, dizi.length() - 10)
        val satirlar = mutableListOf("Günlük:")
        for (i in baslangic until dizi.length()) {
            val o = dizi.getJSONObject(i)
            satirlar.add("${o.optString("tarih")}: ${o.optString("metin")}")
        }
        return satirlar.joinToString("\n")
    }
}
