package com.deniz.eda.utils

import com.deniz.eda.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object CurrencyUtils {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fiyatlariGetir(soru: String, hitap: String): String = withContext(Dispatchers.IO) {
        val anahtar = Settings.navasanApiKey
        if (anahtar.isBlank()) {
            return@withContext "Fiyat icin once ayarlardan Navasan API anahtari girmen lazim $hitap."
        }
        try {
            val url = "http://api.navasan.tech/latest/?api_key=$anahtar"
            val istek = Request.Builder()
                .url(url)
                .header("User-Agent", "EdaApp/1.0")
                .build()
            client.newCall(istek).execute().use { yanit ->
                if (!yanit.isSuccessful) {
                    return@withContext "Fiyatlar su an alinamadi $hitap."
                }
                val govde = yanit.body?.string()
                    ?: return@withContext "Fiyatlar su an alinamadi $hitap."
                val json = JSONObject(govde)
                val s = soru.lowercase()
                val cevap = when {
                    s.contains("dolar") || s.contains("دلار") -> dolarCevap(json, hitap)
                    s.contains("altin") || s.contains("altın") || s.contains("طلا") -> altinCevap(json, hitap)
                    s.contains("sekke") || s.contains("سکه") -> sekkeCevap(json, hitap)
                    s.contains("euro") || s.contains("یورو") -> euroCevap(json, hitap)
                    s.contains("usdt") || s.contains("تتر") -> usdtCevap(json, hitap)
                    else -> tumFiyatlar(json, hitap)
                }
                android.util.Log.d("EdaCur", "cevap: $cevap")
                cevap
            }
        } catch (e: Exception) {
            android.util.Log.e("EdaCur", "hata: " + e.message)
            "Fiyatlar su an alinamadi $hitap."
        }
    }

    private fun oku(json: JSONObject, key: String): String? {
        val obj = json.optJSONObject(key) ?: return null
        val v = obj.optString("value", "")
        return if (v.isNotBlank()) v else null
    }

    private fun fmt(s: String?): String {
        if (s.isNullOrBlank()) return "?"
        val temiz = s.replace(",", "").replace("،", "").trim()
        val sayi = temiz.toLongOrNull()
        return if (sayi != null) "%,d".format(sayi) else s
    }

    private fun dolarCevap(json: JSONObject, hitap: String): String {
        val d = oku(json, "usd_sell") ?: oku(json, "usd")
        val t = oku(json, "usdt")
        return "Dolar " + fmt(d) + " Riyal. Tether " + fmt(t) + " Riyal. " + hitap
    }

    private fun altinCevap(json: JSONObject, hitap: String): String {
        val g = oku(json, "18ayar")
        val a = oku(json, "abshodeh")
        return "Altin 18 ayar " + fmt(g) + " Riyal/gram. Absode " + fmt(a) + " Riyal. " + hitap
    }

    private fun sekkeCevap(json: JSONObject, hitap: String): String {
        val s = oku(json, "sekkeh")
        val b = oku(json, "bahar")
        val n = oku(json, "nim")
        val r = oku(json, "rob")
        return "Sekkeh Emami " + fmt(s) + " Riyal. Bahar " + fmt(b) + " Riyal. Nim " + fmt(n) + " Riyal. Rob " + fmt(r) + " Riyal. " + hitap
    }

    private fun euroCevap(json: JSONObject, hitap: String): String {
        val e = oku(json, "eur_sell") ?: oku(json, "eur")
        val p = oku(json, "gbp_sell")
        return "Euro " + fmt(e) + " Riyal. Pound " + fmt(p) + " Riyal. " + hitap
    }

    private fun usdtCevap(json: JSONObject, hitap: String): String {
        val t = oku(json, "usdt")
        val d = oku(json, "usd_sell")
        return "Tether " + fmt(t) + " Riyal. Dolar " + fmt(d) + " Riyal. " + hitap
    }

    private fun tumFiyatlar(json: JSONObject, hitap: String): String {
        val d = oku(json, "usd_sell") ?: oku(json, "usd")
        val g = oku(json, "18ayar")
        val s = oku(json, "sekkeh")
        val e = oku(json, "eur_sell") ?: oku(json, "eur")
        return "Fiyatlar " + hitap + ": Dolar " + fmt(d) + ". Altin 18 ayar " + fmt(g) + ". Sekkeh " + fmt(s) + ". Euro " + fmt(e) + ". Riyal."
    }
}
