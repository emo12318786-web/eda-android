package com.deniz.eda.utils

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import android.Manifest
import java.util.Locale

/**
 * Orijinal konum_al() fonksiyonunun karsiligi. Play Services'e bagimli
 * FusedLocationProviderClient yerine platformun kendi LocationManager'i
 * kullanildi - ekstra bagimlilik gerektirmiyor, GPS'i olmayan/Play
 * Services'siz cihazlarda da calisir.
 */
object LocationUtils {

    data class KonumBilgisi(val enlem: Double, val boylam: Double, val adres: String?)

    fun izinVarMi(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

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
            } catch (e: Exception) {
                // saglayici cihazda yok, devam et
            }
        }
        val bulunan = enIyi ?: return null

        val adres = try {
            val geocoder = android.location.Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(bulunan.latitude, bulunan.longitude, 1)
                ?.firstOrNull()?.let { "${it.locality ?: ""} ${it.adminArea ?: ""}".trim() }
        } catch (e: Exception) {
            null
        }

        return KonumBilgisi(bulunan.latitude, bulunan.longitude, adres)
    }

    fun konumMetni(context: Context, hitap: String): String {
        val k = sonBilinenKonum(context) ?: return "Konum bilgisi alınamadı $hitap, GPS'i kontrol eder misin?"
        return if (!k.adres.isNullOrBlank()) {
            "Şu an ${k.adres} civarındasın $hitap."
        } else {
            "Konumun: enlem ${"%.4f".format(k.enlem)}, boylam ${"%.4f".format(k.boylam)} $hitap."
        }
    }
}
