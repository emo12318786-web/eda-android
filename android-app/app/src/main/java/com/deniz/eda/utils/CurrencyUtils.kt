package com.deniz.eda.utils

import com.deniz.eda.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Dolar/altın/sikke/döviz/kripto fiyatları için Navasan API.
 * Tüm fiyatlar RİYAL olarak gösterilir (API'den geldiği gibi).
 */
object CurrencyUtils {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fiyatlariGetir(soru: String, hitap: String): String = withContext(Dispatchers.IO) {
        val anahtar = Settings.navasanApiKey
        if (anahtar.isBlank()) {
            return@withContext "Fiyat için önce ayarlardan Navasan API anahtarı girmen lazım $hitap."
        }
        try {
            val url = "http://api.navasan.tech/latest/?api_key=$anahtar"
            val istek = Request.Builder()
                .url(url)
                .header("User-Agent", "EdaApp/1.0")
                .build()

            client.newCall(istek).execute().use { yanit ->
                if (!yanit.isSuccessful) {
                    return@withContext "Fiyatlar şu an alınamadı $hitap."
                }
                val govde = yanit.body?.string()
                    ?: return@withContext "Fiyatlar şu an alınamadı $hitap."
                val json = JSONObject(govde)

                val s = soru.lowercase()
                when {
                    s.contains("دلار") || s.contains("dolar") -> dolarCevap(json, hitap)
                    s.contains("طلا") || s.contains("altın") || s.contains("altin") -> altinCevap(json, hitap)
                    s.contains("سکه") || s.contains("sekke") -> sekkeCevap(json, hitap)
                    s.contains("یورو") || s.contains("euro") -> euroCevap(json, hitap)
                    s.contains("تتر") || s.contains("usdt") -> usdtCevap(json, hitap)
                    s.contains("بیت") || s.contains("bitcoin") -> btcCevap(json, hitap)
                    s.contains("اتریوم") || s.contains("ethereum") -> ethCevap(json, hitap)
                    else -> tumFiyatlar(json, hitap)
                }
            }
        } catch (e: Exception) {
            "Fiyatlar şu an alınamadı $hitap."
        }
    }

    private fun dolarCevap(json: JSONObject, hitap: String): String {
        val dolar = json.optJSONObject("usd_sell")?.optString("value")
            ?: json.optJSONObject("usd")?.optString("value")
        val usdt = json.optJSONObject("usdt")?.optString("value")
        val harat = json.optJSONObject("usd_harat_sell")?.optString("value")

        return buildString {
            append("💵 ")
            if (dolar != null) append("Dolar ${formatliSayi(dolar)} Riyal. ")
            if (usdt != null) append("Tether ${formatliSayi(usdt)} Riyal. ")
            if (harat != null) append("Herat ${formatliSayi(harat)} Riyal. ")
            append(hitap)
        }
    }

    private fun altinCevap(json: JSONObject, hitap: String): String {
        val gram18 = json.optJSONObject("18ayar")?.optString("value")
        val abshodeh = json.optJSONObject("abshodeh")?.optString("value")
        val mesghal = json.optJSONObject("mesghal")?.optString("value")
        val ons = json.optJSONObject("usd_xau")?.optString("value")

        return buildString {
            append("🥇 ")
            if (gram18 != null) append("Altın 18 ayar ${formatliSayi(gram18)} Riyal/gram. ")
            if (abshodeh != null) append("Abşode ${formatliSayi(abshodeh)} Riyal. ")
            if (mesghal != null) append("Mesghal ${formatliSayi(mesghal)} Riyal. ")
            if (ons != null) append("Ons $ons dolar. ")
            append(hitap)
        }
    }

    private fun sekkeCevap(json: JSONObject, hitap: String): String {
        val sekkeh = json.optJSONObject("sekkeh")?.optString("value")
        val bahar = json.optJSONObject("bahar")?.optString("value")
        val nim = json.optJSONObject("nim")?.optString("value")
        val rob = json.optJSONObject("rob")?.optString("value")
        val gerami = json.optJSONObject("gerami")?.optString("value")

        return buildString {
            append("🪙 ")
            if (sekkeh != null) append("Sekkeh Emami ${formatliSayi(sekkeh)} Riyal. ")
            if (bahar != null) append("Sekkeh Bahar ${formatliSayi(bahar)} Riyal. ")
            if (nim != null) append("Nim ${formatliSayi(nim)} Riyal. ")
            if (rob != null) append("Rob ${formatliSayi(rob)} Riyal. ")
            if (gerami != null) append("Gerami ${formatliSayi(gerami)} Riyal. ")
            append(hitap)
        }
    }

    private fun euroCevap(json: JSONObject, hitap: String): String {
        val euro = json.optJSONObject("eur_sell")?.optString("value")
            ?: json.optJSONObject("eur")?.optString("value")
        val pound = json.optJSONObject("gbp_sell")?.optString("value")
        val lira = json.optJSONObject("try_sell")?.optString("value")
        val dirham = json.optJSONObject("aed_sell")?.optString("value")

        return buildString {
            append("💶 ")
            if (euro != null) append("Euro ${formatliSayi(euro)} Riyal. ")
            if (pound != null) append("Pound ${formatliSayi(pound)} Riyal. ")
            if (lira != null) append("Lira ${formatliSayi(lira)} Riyal. ")
            if (dirham != null) append("Dirham ${formatliSayi(dirham)} Riyal. ")
            append(hitap)
        }
    }

    private fun usdtCevap(json: JSONObject, hitap: String): String {
        val usdt = json.optJSONObject("usdt")?.optString("value")
        val usd = json.optJSONObject("usd_sell")?.optString("value")

        return buildString {
            append("💵 ")
            if (usdt != null) append("Tether ${formatliSayi(usdt)} Riyal. ")
            if (usd != null) append("Dolar ${formatliSayi(usd)} Riyal. ")
            append(hitap)
        }
    }

    private fun btcCevap(json: JSONObject, hitap: String): String {
        val btc = json.optJSONObject("usd_btc")?.optString("value")
        return if (btc != null) "₿ Bitcoin ${formatliSayi(btc)} Riyal $hitap." else "Bitcoin bilgisi yok $hitap."
    }

    private fun ethCevap(json: JSONObject, hitap: String): String {
        val eth = json.optJSONObject("usd_eth")?.optString("value")
        return if (eth != null) "Ξ Ethereum ${formatliSayi(eth)} Riyal $hitap." else "Ethereum bilgisi yok $hitap."
    }

    private fun tumFiyatlar(json: JSONObject, hitap: String): String {
        val dolar = json.optJSONObject("usd_sell")?.optString("value")
        val gram18 = json.optJSONObject("18ayar")?.optString("value")
        val sekkeh = json.optJSONObject("sekkeh")?.optString("value")
        val euro = json.optJSONObject("eur_sell")?.optString("value")

        return buildString {
            append("💰 Fiyatlar $hitap: ")
            if (dolar != null) append("Dolar ${formatliSayi(dolar)}. ")
            if (gram18 != null) append("Altın 18 ayar ${formatliSayi(gram18)}. ")
            if (sekkeh != null) append("Sekkeh ${formatliSayi(sekkeh)}. ")
            if (euro != null) append("Euro ${formatliSayi(euro)}. ")
            append("Riyal.")
        }
    }

    private fun formatliSayi(s: String): String {
        if (s.contains(".")) return s
        val sayi = s.toLongOrNull() ?: return s
        return "%,d".format(sayi)
    }
}
