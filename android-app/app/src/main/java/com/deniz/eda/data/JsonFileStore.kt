package com.deniz.eda.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Orijinal json_yükle()/json_kaydet() fonksiyonlarinin karsiligi - dosya tabanli JSON depolama. */
object JsonFileStore {
    fun okuDizi(context: Context, dosyaAdi: String): JSONArray {
        val dosya = File(context.filesDir, dosyaAdi)
        if (!dosya.exists()) return JSONArray()
        return try {
            JSONArray(dosya.readText())
        } catch (e: Exception) {
            JSONArray()
        }
    }

    fun yazDizi(context: Context, dosyaAdi: String, veri: JSONArray) {
        File(context.filesDir, dosyaAdi).writeText(veri.toString())
    }

    fun okuNesne(context: Context, dosyaAdi: String, varsayilan: JSONObject): JSONObject {
        val dosya = File(context.filesDir, dosyaAdi)
        if (!dosya.exists()) return varsayilan
        return try {
            JSONObject(dosya.readText())
        } catch (e: Exception) {
            varsayilan
        }
    }

    fun yazNesne(context: Context, dosyaAdi: String, veri: JSONObject) {
        File(context.filesDir, dosyaAdi).writeText(veri.toString())
    }
}
