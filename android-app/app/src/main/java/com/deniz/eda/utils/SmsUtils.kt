package com.deniz.eda.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.util.Locale

/** Contact lookup + guarded SMS sending. */
object SmsUtils {
    data class PendingSms(val name: String, val number: String, val message: String)
    @Volatile private var pendingSms: PendingSms? = null

    fun izinVarMi(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    fun rehberIzniVarMi(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace('ı', 'i')
            .replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()

    private var pendingRecipientMessage: String? = null

    /** Holds the message while the user clarifies an ambiguous contact name. */
    fun setPendingRecipientMessage(message: String) { pendingRecipientMessage = message }
    fun takePendingRecipientMessage(): String? {
        val value = pendingRecipientMessage
        pendingRecipientMessage = null
        return value
    }

    /** Prefer exact saved-name/relationship-alias matches before considering partial matches. */
    fun kisiBul(context: Context, spokenName: String): Pair<String, String>? {
        if (!rehberIzniVarMi(context) || spokenName.isBlank()) return null
        val wanted = normalize(spokenName)
        val aliases = when (wanted) {
            "anneme", "annemi", "annem", "anneye", "anne" -> setOf("anne", "annem", "mom", "mother")
            "babami", "babama", "babam", "baba" -> setOf("baba", "babam", "dad", "father", "ata", "atam")
            else -> setOf(wanted)
        }
        val all = linkedSetOf<Pair<String, String>>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
            )?.use { cursor ->
                val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol)?.trim().orEmpty()
                    val number = cursor.getString(numberCol)?.trim().orEmpty()
                    if (name.isNotBlank() && number.isNotBlank()) all.add(name to number)
                }
            }
        } catch (_: Exception) { return null }

        val exact = all.filter { (name, _) -> normalize(name) in aliases }
        if (exact.isNotEmpty()) return exact.distinct().singleOrNull()

        val partial = all.filter { (name, _) ->
            val normalized = normalize(name)
            aliases.any { alias -> normalized.contains(alias) || alias.contains(normalized) }
        }
        return partial.distinct().singleOrNull()
    }

    fun setPending(name: String, number: String, message: String) { pendingSms = PendingSms(name, number, message) }
    fun pending(): PendingSms? = pendingSms
    fun cancelPending() { pendingSms = null }

    fun confirmPending(context: Context): String {
        val pending = pendingSms ?: return "Onay bekleyen bir mesaj yok."
        pendingSms = null
        if (!izinVarMi(context)) return "SMS izni yok; mesaj gönderilmedi."
        return if (gonder(context, pending.number, pending.message))
            "Mesaj ${pending.name} kişisine gönderildi."
        else "Mesaj gönderilemedi. SMS iznini ve SIM durumunu kontrol et."
    }

    fun gonder(context: Context, numara: String, mesaj: String): Boolean {
        if (!izinVarMi(context)) return false
        return try {
            context.getSystemService(SmsManager::class.java).sendTextMessage(numara, null, mesaj, null, null)
            true
        } catch (_: Exception) { false }
    }

    fun numarayiAyikla(metin: String): String? = Regex("\\d{7,}").find(metin)?.value
}