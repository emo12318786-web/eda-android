package com.deniz.eda.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Konum alma — en doğru sonucu verir.
 * GPS her zaman açık olduğu için requestSingleUpdate kullanılır.
 */
object LocationUtils {

    data class KonumBilgisi(
        val enlem: Double,
        val boylam: Double,
        val sehir: String?,
        val mahalle: String?,
        val cadde: String?,
        val adres: String?
    )

    fun izinVarMi(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * En doğru konumu al (requestSingleUpdate ile).
     * GPS her zaman açık olduğu için 5 saniye içinde yanıt gelir.
     */
    suspend fun enDogruKonum(context: Context, timeoutMs: Long = 15000): Location? {
        if (!izinVarMi(context)) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // ═══ ۱. اول cache رو چک کن (سریع‌ترین راه) ═══
        val cacheMax = System.currentTimeMillis() - (5 * 60 * 1000L)
        var enIyiCache: Location? = null
        for (p in listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )) {
            try {
                val k = lm.getLastKnownLocation(p) ?: continue
                if (k.time >= cacheMax) {
                    if (enIyiCache == null || k.accuracy < enIyiCache!!.accuracy) {
                        enIyiCache = k
                    }
                }
            } catch (_: Exception) {}
        }
        if (enIyiCache != null) {
            android.util.Log.d("LocationUtils",
                "✅ cache hit (${enIyiCache.provider}, age=${(System.currentTimeMillis() - enIyiCache.time) / 1000}s)")
            return enIyiCache
        }

        // ═══ ۲. GPS_PROVIDER ═══
        val gps = tryLocationUpdates(lm, LocationManager.GPS_PROVIDER, timeoutMs / 2)
        if (gps != null) {
            android.util.Log.d("LocationUtils", "✅ GPS fix")
            return gps
        }

        // ═══ ۳. NETWORK_PROVIDER ═══
        val net = tryLocationUpdates(lm, LocationManager.NETWORK_PROVIDER, timeoutMs / 2)
        if (net != null) {
            android.util.Log.d("LocationUtils", "✅ NETWORK fix")
            return net
        }

        // ═══ ۴. آخرین راه — قدیمی‌ترین cache ═══
        var enIyi: Location? = null
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                val k = lm.getLastKnownLocation(p) ?: continue
                if (enIyi == null || k.time > enIyi!!.time) enIyi = k
            } catch (_: Exception) {}
        }
        if (enIyi != null) {
            android.util.Log.w("LocationUtils",
                "⚠️ fallback cache قدیمی (age=${(System.currentTimeMillis() - enIyi.time) / 1000}s)")
        }
        return enIyi
    }

    /**
     * requestLocationUpdates با timeout — پایدارتر از requestSingleUpdate
     */
    private suspend fun tryLocationUpdates(
        lm: LocationManager,
        provider: String,
        timeoutMs: Long
    ): Location? = withContext(Dispatchers.IO) {
        try {
            if (!lm.isProviderEnabled(provider)) return@withContext null
        } catch (_: Exception) { return@withContext null }

        suspendCancellableCoroutine { cont ->
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            var bitti = false

            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (bitti) return
                    bitti = true
                    try { lm.removeUpdates(this) } catch (_: Exception) {}
                    handler.removeCallbacksAndMessages(null)
                    if (cont.isActive) cont.resume(location)
                }
                override fun onProviderDisabled(p: String) {}
                override fun onProviderEnabled(p: String) {}
                override fun onStatusChanged(p: String?, status: Int, extras: android.os.Bundle?) {}
            }

            try {
                @Suppress("MissingPermission")
                lm.requestLocationUpdates(
                    provider, 0L, 0f, listener,
                    android.os.Looper.getMainLooper()
                )

                handler.postDelayed({
                    if (bitti) return@postDelayed
                    bitti = true
                    try { lm.removeUpdates(listener) } catch (_: Exception) {}
                    if (cont.isActive) cont.resume(null)
                }, timeoutMs)
            } catch (e: Exception) {
                if (!bitti) {
                    bitti = true
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    /**
     * requestSingleUpdate با timeout.
     */
    

    /**
     * Adres bilgisi (şehir, mahalle, cadde) al.
     */
    /**
     * Adres bilgisi — اول Google Geocoder، اگه نشد Nominatim
     */
    private suspend fun adresBilgisi(context: Context, lat: Double, lon: Double): Address? =
        withContext(Dispatchers.IO) {
            // ═══ ۱. Google Geocoder ═══
            try {
                val geocoder = Geocoder(context, Locale("tr", "TR"))
                @Suppress("DEPRECATION")
                val sonuc = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
                if (sonuc != null) {
                    android.util.Log.d("LocationUtils", "✅ Geocoder Google")
                    return@withContext sonuc
                }
            } catch (e: Exception) {
                android.util.Log.w("LocationUtils", "Geocoder Google hatasi: ${e.message}")
            }

            // ═══ ۲. Nominatim (OpenStreetMap) — Iran'da calisir ═══
            try {
                val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&accept-language=tr&zoom=18"
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val istek = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "EdaApp/1.0")
                    .build()
                client.newCall(istek).execute().use { yanit ->
                    if (!yanit.isSuccessful) return@withContext null
                    val govde = yanit.body?.string() ?: return@withContext null
                    val json = org.json.JSONObject(govde)
                    val addr = json.optJSONObject("address") ?: return@withContext null

                    // ساخت Address مصنوعی
                    val a = Address(Locale("tr", "TR"))
                    a.latitude = lat
                    a.longitude = lon
                    addr.optString("city").takeIf { it.isNotBlank() }?.let { a.locality = it }
                    addr.optString("town").takeIf { it.isNotBlank() }?.let { a.locality = it }
                    addr.optString("state").takeIf { it.isNotBlank() }?.let { if (a.locality == null) a.locality = it }
                    addr.optString("suburb").takeIf { it.isNotBlank() }?.let { a.subLocality = it }
                    addr.optString("neighbourhood").takeIf { it.isNotBlank() }?.let { if (a.subLocality == null) a.subLocality = it }
                    addr.optString("road").takeIf { it.isNotBlank() }?.let { a.thoroughfare = it }
                    a.setAddressLine(0, json.optString("display_name", ""))

                    android.util.Log.d("LocationUtils", "✅ Nominatim: ${a.locality} / ${a.subLocality}")
                    return@withContext a
                }
            } catch (e: Exception) {
                android.util.Log.w("LocationUtils", "Nominatim hatasi: ${e.message}")
            }

            null
        }

    /**
     * "neredeyim" — Türkçe konum metni.
     * TTS Türkçe okur, sadece Türkçe karakter kullanılır.
     */
    suspend fun konumMetni(context: Context, hitap: String): String {
        if (!izinVarMi(context)) {
            return "Konum izni verilmemiş $hitap. Ayarlardan izin ver lütfen."
        }

        val lokasyon = enDogruKonum(context)
            ?: return "Konum alınamadı $hitap. GPS açık mı kontrol et."

        val adres = adresBilgisi(context, lokasyon.latitude, lokasyon.longitude)

        if (adres == null) {
            return "Konumun enlem ${"%.4f".format(lokasyon.latitude)}, boylam ${"%.4f".format(lokasyon.longitude)} $hitap."
        }

        // ═══ Şehir + Mahalle + Cadde ═══
        val parcalar = mutableListOf<String>()

        adres.locality?.let { if (it.isNotBlank()) parcalar.add(it) }
        adres.subLocality?.let { if (it.isNotBlank()) parcalar.add(it) }
        adres.thoroughfare?.let { if (it.isNotBlank()) parcalar.add(it) }

        return if (parcalar.isNotEmpty()) {
            "Şu an ${parcalar.joinToString(", ")} bölgesindesin $hitap."
        } else {
            val adresSatiri = adres.getAddressLine(0)
            if (!adresSatiri.isNullOrBlank()) {
                "Şu an $adresSatiri civarındasın $hitap."
            } else {
                "Konumun enlem ${"%.4f".format(lokasyon.latitude)}, boylam ${"%.4f".format(lokasyon.longitude)} $hitap."
            }
        }
    }

    /**
     * "hava durumu" için koordinat al.
     */
    suspend fun havaIcinKonum(context: Context): Pair<Double, Double>? {
        val lokasyon = enDogruKonum(context) ?: return null
        return Pair(lokasyon.latitude, lokasyon.longitude)
    }

    // ═══ Geriye dönük uyumluluk ═══
    @Suppress("MissingPermission")
    fun sonBilinenKonum(context: Context): KonumBilgisi? {
        if (!izinVarMi(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val saglayicilar = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        var enIyi: Location? = null
        for (p in saglayicilar) {
            try {
                val konum = lm.getLastKnownLocation(p) ?: continue
                if (enIyi == null || konum.accuracy < enIyi!!.accuracy) enIyi = konum
            } catch (_: Exception) {}
        }
        val bulunan = enIyi ?: return null
        return KonumBilgisi(bulunan.latitude, bulunan.longitude, null, null, null, null)
    }
}
