package com.deniz.eda.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Orijinal hava_durumu() fonksiyonunun karsiligi. Open-Meteo (api-key
 * gerektirmeyen, ucretsiz) kullanildi - orijinaldeki saglayici hangisiyse
 * onun yerine gecirilebilir, sadece bu dosya degismesi yeterli.
 */
object WeatherUtils {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private fun weatherCodeAciklama(kod: Int): String = when (kod) {
        0 -> "açık"
        1, 2 -> "az bulutlu"
        3 -> "kapalı"
        45, 48 -> "sisli"
        51, 53, 55, 56, 57 -> "çiseleme"
        61, 63, 65, 66, 67 -> "yağmurlu"
        71, 73, 75, 77 -> "karlı"
        80, 81, 82 -> "sağanak yağışlı"
        95, 96, 99 -> "fırtınalı"
        else -> "belirsiz"
    }

    suspend fun havaDurumuMetni(enlem: Double, boylam: Double, hitap: String): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$enlem&longitude=$boylam&current_weather=true"
            val istek = Request.Builder().url(url).build()
            client.newCall(istek).execute().use { yanit ->
                if (!yanit.isSuccessful) return@withContext "Hava durumu şu an alınamadı $hitap."
                val govde = yanit.body?.string() ?: return@withContext "Hava durumu şu an alınamadı $hitap."
                val guncelHava = JSONObject(govde).getJSONObject("current_weather")
                val sicaklik = guncelHava.getDouble("temperature")
                val kod = guncelHava.getInt("weathercode")
                "Hava şu an ${weatherCodeAciklama(kod)}, sıcaklık ${sicaklik.toInt()} derece $hitap."
            }
        } catch (e: Exception) {
            "Hava durumu şu an alınamadı $hitap."
        }
    }
}
