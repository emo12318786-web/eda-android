package com.deniz.eda.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/** Orijinal json_yükle()/json_kaydet() fonksiyonlarinin karsiligi - dosya tabanli JSON depolama. */
object JsonFileStore {
    private fun atomic(context: Context, dosyaAdi: String) =
        AtomicFile(File(context.filesDir, dosyaAdi))

    fun okuDizi(context: Context, dosyaAdi: String): JSONArray {
        val file = File(context.filesDir, dosyaAdi)
        if (!file.exists()) return JSONArray()
        return try {
            atomic(context, dosyaAdi).openRead().bufferedReader(StandardCharsets.UTF_8).use {
                JSONArray(it.readText())
            }
        } catch (e: Exception) {
            JSONArray()
        }
    }

    fun yazDizi(context: Context, dosyaAdi: String, veri: JSONArray) {
        atomicWrite(context, dosyaAdi, veri.toString())
    }

    fun okuNesne(context: Context, dosyaAdi: String, varsayilan: JSONObject): JSONObject {
        val file = File(context.filesDir, dosyaAdi)
        if (!file.exists()) return varsayilan
        return try {
            atomic(context, dosyaAdi).openRead().bufferedReader(StandardCharsets.UTF_8).use {
                JSONObject(it.readText())
            }
        } catch (e: Exception) {
            varsayilan
        }
    }

    fun yazNesne(context: Context, dosyaAdi: String, veri: JSONObject) {
        atomicWrite(context, dosyaAdi, veri.toString())
    }

    private fun atomicWrite(context: Context, dosyaAdi: String, content: String) {
        val file = atomic(context, dosyaAdi)
        var stream: FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(content.toByteArray(StandardCharsets.UTF_8))
            stream.fd.sync()
            file.finishWrite(stream)
            stream = null
        } catch (error: Exception) {
            if (stream != null) file.failWrite(stream)
            throw error
        }
    }
}
