package com.deniz.eda.utils

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import android.Manifest

/**
 * Orijinal sms_gonder() fonksiyonunun karsiligi.
 *
 * NOT: Sesle soylenen bir cumleden telefon numarasi + mesaj ayirmak
 * (orn. "ayseye mesaj gonder ..." gibi rehber-ismi eslestirmesi) FAZ 3
 * kapsamina birakildi - burada sadece numara+metin dogrudan verildiginde
 * SMS'i gonderen alt seviye fonksiyon var. CommandProcessor bunu simdilik
 * yalnizca komutta acik bir telefon numarasi (rakam dizisi) gectiginde
 * kullaniyor.
 */
object SmsUtils {

    fun izinVarMi(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun gonder(context: Context, numara: String, mesaj: String): Boolean {
        if (!izinVarMi(context)) return false
        return try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(numara, null, mesaj, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Komut metninden bir telefon numarasi (7+ haneli rakam dizisi) yakalamayi dener. */
    fun numarayiAyikla(metin: String): String? =
        Regex("\\d{7,}").find(metin)?.value
}
