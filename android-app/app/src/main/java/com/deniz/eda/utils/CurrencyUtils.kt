package com.deniz.eda.utils

import com.deniz.eda.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Orijinal dolar_fiyati()/altin_fiyati() fonksiyonlarinin karsiligi.
 * navasan.tech API semasina gore yazildi (Iran Toman kurlari icin yaygin
 * kullanilan bir servis). Bu ortamda internet erisimi olmadigindan istek
 * test edilemedi - Settings uzerinden kendi API anahtarini girmen gerekiyor
 * (Ayarlar > "navasan_api_key"). Baska bir saglayici kullanmak istersen
 * sadece bu dosyadaki URL/JSON alan adlarini degistirmen yeterli.
 */
object CurrencyUtils {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun fiyatlariGetir(hitap: String): String = withContext(Dispatchers.IO) {
        val anahtar = Settings.navasanApiKey
        if (anahtar.isBlank()) {
            return@withContext "Dolar ve altın fiyatı için önce ayarlardan bir API anahtarı girmen lazım $hitap."
        }
        try {
            val url = "http://api.navasan.tech/latest/?api_key=$anahtar"
            val istek = Request.Builder().url(url).build()
            client.newCall(istek).execute().use { yanit ->
                if (!yanit.isSuccessful) return@withContext "Fiyatlar şu an alınamadı $hitap."
                val govde = yanit.body?.string() ?: return@withContext "Fiyatlar şu an alınamadı $hitap."
                val json = JSONObject(govde)
                val dolar = json.optJSONObject("usd_sell")?.optString("value")
                    ?: json.optJSONObject("usd")?.optString("value")
                val altin = json.optJSONObject("18ayar")?.optString("value")

                buildString {
                    append("Fiyatlar $hitap: ")
                    if (dolar != null) append("Dolar $dolar Toman. ") else append("Dolar bilgisi yok. ")
                    if (altin != null) append("18 ayar altın $altin Toman.") else append("Altın bilgisi yok.")
                }
            }
        } catch (e: Exception) {
            "Fiyatlar şu an alınamadı $hitap."
        }
    }
}
