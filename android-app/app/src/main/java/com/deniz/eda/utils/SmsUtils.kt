package com.deniz.eda.utils

import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
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


    /** Finds a phone number by a spoken contact name contained in the command. */
    fun kisiNumarasiBul(context: Context, metin: String): Pair<String, String>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val normalized = metin.lowercase(java.util.Locale.ROOT)
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                var best: Pair<String, String>? = null
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)?.trim().orEmpty()
                    val number = cursor.getString(numberIndex)?.trim().orEmpty()
                    if (name.isNotBlank() && number.isNotBlank() &&
                        normalized.contains(name.lowercase(java.util.Locale.ROOT)) &&
                        (best == null || name.length > best!!.second.length)) {
                        best = number to name
                    }
                }
                best
            }
        } catch (_: Exception) { null }
    }

    /** Extracts the message after common Turkish/Azerbaijani "send message" phrases. */
    fun mesajMetniAyikla(metin: String, kisiAdi: String? = null): String {
        var body = metin
        val triggers = listOf("mesajı gönder", "mesaji gonder", "mesaj gönder", "mesaj gonder", "mesaj göndər", "mesajı göndər", "mesaj yaz")
        val trigger = triggers.firstOrNull { body.contains(it, ignoreCase = true) }
        if (trigger != null) body = body.substringAfter(trigger, "")
        if (kisiAdi != null) body = body.replace(Regex(Regex.escape(kisiAdi) + "(ə|a|e|ye|yə)?", RegexOption.IGNORE_CASE), "")
        body = body.replace(Regex("\\bmesaj(ı|i|ı|i)?\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(gönder|gonder|göndər|göndər|yaz)\\b", RegexOption.IGNORE_CASE), "")
            .trim(' ', ':', ',', '.', '-', '"', '\'')
        return body
    }

    /** Komut metninden bir telefon numarasi (7+ haneli rakam dizisi) yakalamayi dener. */
    fun numarayiAyikla(metin: String): String? =
        Regex("\\d{7,}").find(metin)?.value
}
