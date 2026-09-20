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
    suspend fun enDogruKonum(context: Context, timeoutMs: Long = 5000): Location? {
        if (!izinVarMi(context)) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // ═══ ۱. GPS_PROVIDER با requestSingleUpdate ═══
        val gps = trySingleUpdate(context, lm, LocationManager.GPS_PROVIDER, timeoutMs)
        if (gps != null) return gps

        // ═══ ۲. NETWORK_PROVIDER با requestSingleUpdate ═══
        val network = trySingleUpdate(context, lm, LocationManager.NETWORK_PROVIDER, timeoutMs)
        if (network != null) return network

        // ═══ ۳. getLastKnownLocation (cache) ═══
        var enIyi: Location? = null
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                val k = lm.getLastKnownLocation(p) ?: continue
                if (enIyi == null || k.accuracy < enIyi!!.accuracy) enIyi = k
            } catch (_: Exception) {}
        }

        return enIyi
    }

    /**
     * requestSingleUpdate با timeout.
     */
    private suspend fun trySingleUpdate(
        context: Context,
        lm: LocationManager,
        provider: String,
        timeoutMs: Long
    ): Location? = withContext(Dispatchers.IO) {
        try {
            if (!lm.isProviderEnabled(provider)) return@withContext null
        } catch (_: Exception) { return@withContext null }

        suspendCancellableCoroutine { cont ->
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    try { lm.removeUpdates(this) } catch (_: Exception) {}
                    if (cont.isActive) cont.resume(location)
                }
                override fun onProviderDisabled(p: String) {
                    try { lm.removeUpdates(this) } catch (_: Exception) {}
                    if (cont.isActive) cont.resume(null)
                }
                override fun onProviderEnabled(p: String) {}
                override fun onStatusChanged(p: String?, status: Int, extras: android.os.Bundle?) {}
            }

            try {
                @Suppress("MissingPermission")
                lm.requestSingleUpdate(provider, listener, null)

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try { lm.removeUpdates(listener) } catch (_: Exception) {}
                    if (cont.isActive) cont.resume(null)
                }, timeoutMs)

            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    /**
     * Adres bilgisi (şehir, mahalle, cadde) al.
     */
    private suspend fun adresBilgisi(context: Context, lat: Double, lon: Double): Address? =
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale("tr", "TR"))
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
            } catch (e: Exception) {
                null
            }
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
