package com.deniz.eda.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Hava durumu — wttr.in (Termux'teki gibi).
 * Bugün + Yarın + 3 günlük tahmin.
 * TTS Türkçe okur.
 */
object WeatherUtils {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * @param gun 0 = bugün, 1 = yarın, 2 = öbür gün
     */
    suspend fun havaDurumuMetni(
        enlem: Double,
        boylam: Double,
        hitap: String,
        gun: Int = 0
    ): String = withContext(Dispatchers.IO) {
        try {
            // wttr.in koordinatları "lat,lon" formatında kabul eder
            val konum = "$enlem,$boylam"
            val url = "https://wttr.in/$konum?format=j1&lang=tr"
            val istek = Request.Builder()
                .url(url)
                .header("User-Agent", "curl/8.0")
                .build()

            client.newCall(istek).execute().use { yanit ->
                if (!yanit.isSuccessful) return@withContext "Hava durumu şu an alınamadı $hitap."
                val govde = yanit.body?.string() ?: return@withContext "Hava durumu şu an alınamadı $hitap."
                val json = JSONObject(govde)

                val bugun = json.getJSONArray("weather").getJSONObject(0)

                val gunAdi = when (gun) {
                    0 -> "Bugün"
                    1 -> "Yarın"
                    2 -> "Öbür gün"
                    else -> "Bugün"
                }

                // ═══ Sıcaklık + açıklama ═══
                val maxTemp = bugun.optString("maxtempC", "?")
                val minTemp = bugun.optString("mintempC", "?")
                val aciklama = bugun.getJSONArray("hourly")
                    .getJSONObject(0)
                    .getJSONArray("lang_tr")
                    .getJSONObject(0)
                    .optString("value", "belirsiz")

                // ═══ Bugün için ekstra bilgi (nem, rüzgar) ═══
                val ekstra = if (gun == 0) {
                    try {
                        val simdi = json.getJSONArray("current_condition").getJSONObject(0)
                        val nem = simdi.optString("humidity", "?")
                        val ruzgar = simdi.optString("windspeedKmph", "?")
                        " Şu an nem yüzde $nem, rüzgar saatte $ruzgar kilometre."
                    } catch (_: Exception) { "" }
                } else ""

                return@withContext "$gunAdi hava $aciklama, en düşük $minTemp en yüksek $maxTemp derece $hitap.$ekstra"
            }
        } catch (e: Exception) {
            "Hava durumu şu an alınamadı $hitap."
        }
    }

    /**
     * Bugün + Yarın özet.
     */
    suspend fun havaDurumuOzet(enlem: Double, boylam: Double, hitap: String): String =
        withContext(Dispatchers.IO) {
            try {
                val konum = "$enlem,$boylam"
                val url = "https://wttr.in/$konum?format=j1&lang=tr"
                val istek = Request.Builder().url(url).header("User-Agent", "curl/8.0").build()

                client.newCall(istek).execute().use { yanit ->
                    if (!yanit.isSuccessful) return@withContext "Hava durumu şu an alınamadı $hitap."
                    val govde = yanit.body?.string() ?: return@withContext "Hava durumu şu an alınamadı $hitap."
                    val json = JSONObject(govde)

                    val bugun = json.getJSONArray("weather").getJSONObject(0)
                    val yarin = json.getJSONArray("weather").getJSONObject(1)

                    val bugunAciklama = bugun.getJSONArray("hourly").getJSONObject(0)
                        .getJSONArray("lang_tr").getJSONObject(0).optString("value", "belirsiz")
                    val yarinAciklama = yarin.getJSONArray("hourly").getJSONObject(0)
                        .getJSONArray("lang_tr").getJSONObject(0).optString("value", "belirsiz")

                    val bugunMax = bugun.optString("maxtempC", "?")
                    val bugunMin = bugun.optString("mintempC", "?")
                    val yarinMax = yarin.optString("maxtempC", "?")
                    val yarinMin = yarin.optString("mintempC", "?")

                    "Bugün hava $bugunAciklama, $bugunMin ile $bugunMax derece arası. " +
                    "Yarın hava $yarinAciklama, $yarinMin ile $yarinMax derece arası $hitap."
                }
            } catch (e: Exception) {
                "Hava durumu şu an alınamadı $hitap."
            }
        }
}
