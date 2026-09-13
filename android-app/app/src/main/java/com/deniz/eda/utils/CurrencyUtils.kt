package com.deniz.eda.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Dolar ve altin fiyatlari - tgju.org API'sinden (ucretsiz, anahtarsiz).
 * Cevap tamamen Turkce (Istanbul Turkcesi).
 */
object CurrencyUtils {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun fiyatGetir(url: String): Long? {
        return try {
            val istek = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(istek).execute().use { yanit ->
                if (!yanit.isSuccessful) return null
                val govde = yanit.body?.string() ?: return null
                val json = JSONObject(govde)
                val data = json.optJSONArray("data") ?: return null
                if (data.length() == 0) return null
                val satir = data.getJSONArray(0)
                val fiyatStr = satir.optString(0).replace(",", "").trim()
                fiyatStr.toLongOrNull()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun formatSayi(n: Long): String {
        val s = n.toString()
        val sb = StringBuilder()
        for (i in s.indices) {
            if (i > 0 && (s.length - i) % 3 == 0) sb.append('.')
            sb.append(s[i])
        }
        return sb.toString()
    }

    suspend fun fiyatlariGetir(hitap: String): String = withContext(Dispatchers.IO) {
        val dolarUrl = "https://api.tgju.org/v1/market/indicator/summary-table-data/price_dollar_rl"
        val altinUrl = "https://api.tgju.org/v1/market/indicator/summary-table-data/geram18"

        val dolarRiyal = fiyatGetir(dolarUrl)
        val altinRiyal = fiyatGetir(altinUrl)

        val sb = StringBuilder("Fiyatlar $hitap: ")
        if (dolarRiyal != null) {
            val toman = dolarRiyal / 10
            sb.append("Dolar ${formatSayi(toman)} tümen. ")
        } else {
            sb.append("Dolar alınamadı. ")
        }
        if (altinRiyal != null) {
            val toman = altinRiyal / 10
            sb.append("18 ayar altın ${formatSayi(toman)} tümen.")
        } else {
            sb.append("Altın alınamadı.")
        }
        sb.toString()
    }
}