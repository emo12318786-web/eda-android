package com.deniz.eda.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.CallLog
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fast, offline phone actions. Sensitive communication is read-only; sending,
 * deleting, purchasing and confirmations are deliberately not automated here.
 */
object PhoneSystemCommands {
    fun handle(context: Context, raw: String): String? {
        val text = raw.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
        if (text.isBlank()) return null

        if (containsAny(text, "son mesajı oku", "son mesaji oku", "son mesajımı oku",
                "son mesajimi oku", "en son mesaj", "son gelen mesaj", "bana gelen son mesaj",
                "son sms", "son sms'i oku", "son sms i oku")) return latestSms(context)

        if (containsAny(text, "son arayan kim", "son aramayı oku", "son aramayi oku",
                "en son kim aradı", "en son kim aradi", "son kim aradı", "son kim aradi",
                "son arayan", "son çağrı", "son cagri", "son telefon araması")) return latestCall(context)

        if (containsAny(text, "parlaklık", "parlaklik", "ekran ışığı", "ekran isigi") &&
            containsAny(text, "artır", "artir", "yükselt", "yukselt", "azalt", "kıs", "kis",
                "düşür", "dusur", "yüzde", "yuzde", "maksimum", "minimum", "en sona", "yarıya", "yariya")) {
            return setBrightness(context, text)
        }

        if (containsAny(text, "ses", "sesi", "volume", "hoparlör", "hoparlor") &&
            containsAny(text, "artır", "artir", "yükselt", "yukselt", "azalt", "kıs", "kis",
                "düşür", "dusur", "yüzde", "yuzde", "maksimum", "minimum", "sıfırla", "sifirla")) {
            return setVolume(context, text)
        }

        if (containsAny(text, "kaç saat daha gider", "kac saat daha gider", "ne kadar şarj", "ne kadar sarj",
                "kaç saat şarj", "kac saat sarj", "pilim ne kadar dayanır", "pilim ne kadar dayanir",
                "şarjım ne kadar gider", "sarjim ne kadar gider", "kalan pil süresi", "kalan pil suresi")) {
            return batteryTime(context)
        }
        return null
    }

    private fun containsAny(text: String, vararg terms: String) = terms.any { text.contains(it) }

    private fun setVolume(context: Context, text: String): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        val max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
        val current = audio.getStreamVolume(stream)
        val number = Regex("\\b(\\d{1,3})\\s*(?:%|yüzde|yuzde)?\\b")
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val target = when {
            containsAny(text, "maksimum") -> max
            containsAny(text, "minimum", "sıfırla", "sifirla") -> 0
            number != null -> (number.coerceIn(0, 100) * max / 100.0).toInt()
            containsAny(text, "artır", "artir", "yükselt", "yukselt") -> (current + 1).coerceAtMost(max)
            else -> (current - 1).coerceAtLeast(0)
        }
        audio.setStreamVolume(stream, target, 0)
        return "Medya sesi yüzde ${(target * 100 / max)} olarak ayarlandı."
    }

    private fun setBrightness(context: Context, text: String): String {
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent)
                "Ekran parlaklığını değiştirebilmem için açılan Android ekranında EDA için sistem ayarı iznini etkinleştir; sonra komutu yeniden söyle."
            } catch (_: Exception) {
                "Parlaklık izni yok. Ayarlar > Uygulamalar > Özel erişim > Sistem ayarlarını değiştirme bölümünden EDA'ya izin ver."
            }
        }
        val current = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128)
        val number = Regex("\\b(\\d{1,3})\\s*(?:%|yüzde|yuzde)?\\b").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val target = when {
            containsAny(text, "maksimum") -> 255
            containsAny(text, "minimum") -> 8
            number != null -> (number.coerceIn(0, 100) * 255 / 100.0).toInt().coerceAtLeast(8)
            containsAny(text, "artır", "artir", "yükselt", "yukselt") -> (current + 32).coerceAtMost(255)
            containsAny(text, "yarıya", "yariya") -> (current / 2).coerceAtLeast(8)
            else -> (current - 32).coerceAtLeast(8)
        }
        return if (runCatching { Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, target) }.getOrDefault(false)) {
            "Ekran parlaklığı yaklaşık yüzde ${target * 100 / 255} olarak ayarlandı."
        } else "Android parlaklık ayarını değiştirmeme izin vermedi."
    }

    private fun latestSms(context: Context): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return "Son mesajı okumak için EDA'ya SMS okuma izni vermelisin."
        }
        return try {
            context.contentResolver.query(Uri.parse("content://sms/inbox"),
                arrayOf("address", "body", "date"), null, null, "date DESC")?.use { c ->
                if (!c.moveToFirst()) return "Gelen kutusunda okunabilecek mesaj bulunamadı."
                val sender = c.getStringOrEmpty("address")
                val body = c.getStringOrEmpty("body")
                val date = c.getLongOrZero("date")
                val stamp = if (date > 0) SimpleDateFormat("d MMM, HH:mm", Locale("tr", "TR")).format(Date(date)) else ""
                "Son gelen mesaj ${if (sender.isBlank()) "bilinmeyen göndericiden" else sender}, $stamp: ${body.take(1200)}"
            } ?: "Mesajlara erişilemiyor."
        } catch (_: SecurityException) {
            "Android son mesajı okumak için izin vermedi."
        } catch (e: Exception) {
            "Son mesaj okunamadı: ${e.localizedMessage ?: "bilinmeyen hata"}"
        }
    }

    private fun latestCall(context: Context): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return "Son arayanı öğrenmek için Android ayarlarından EDA'ya arama kayıtlarını okuma izni ver."
        }
        return try {
            context.contentResolver.query(CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE, CallLog.Calls.CACHED_NAME),
                null, null, CallLog.Calls.DATE + " DESC")?.use { c ->
                if (!c.moveToFirst()) return "Arama kaydı bulunamadı."
                val number = c.getStringOrEmpty(CallLog.Calls.NUMBER)
                val name = c.getStringOrEmpty(CallLog.Calls.CACHED_NAME).ifBlank { number.ifBlank { "bilinmeyen numara" } }
                val date = c.getLongOrZero(CallLog.Calls.DATE)
                val type = c.getIntOrZero(CallLog.Calls.TYPE)
                val kind = when (type) {
                    CallLog.Calls.INCOMING_TYPE -> "gelen arama"
                    CallLog.Calls.OUTGOING_TYPE -> "giden arama"
                    CallLog.Calls.MISSED_TYPE -> "cevapsız arama"
                    CallLog.Calls.REJECTED_TYPE -> "reddedilen arama"
                    else -> "arama"
                }
                val stamp = if (date > 0) SimpleDateFormat("d MMM, HH:mm", Locale("tr", "TR")).format(Date(date)) else ""
                "Son kayıt: $kind, $name, $stamp."
            } ?: "Arama kayıtlarına erişilemiyor."
        } catch (_: SecurityException) {
            "Android arama kayıtlarını okumak için izin vermedi."
        } catch (e: Exception) {
            "Arama kaydı okunamadı: ${e.localizedMessage ?: "bilinmeyen hata"}"
        }
    }

    private fun batteryTime(context: Context): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charge = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val current = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (level !in 1..100 || charge <= 0L || current == Long.MIN_VALUE || current == 0L) {
            return "Şarj süresini güvenilir hesaplamak için birkaç saatlik pil kullanım geçmişi gerekir. Şu an pil yüzde ${level.coerceAtLeast(0)}; Android bu cihazda kalan süreyi doğrudan vermiyor."
        }
        val currentAbs = kotlin.math.abs(current)
        val hours = charge.toDouble() / currentAbs.toDouble()
        if (!hours.isFinite() || hours <= 0.0 || hours > 72.0) {
            return "Pil yüzde $level. Anlık güç tüketimi değişken olduğu için kalan süreyi güvenilir hesaplayamıyorum."
        }
        return "Pil yüzde $level. Anlık tüketim devam ederse yaklaşık ${"%.1f".format(Locale.US, hours)} saat kalmış olabilir; ekran, ağ ve uygulama kullanımı bu tahmini ciddi değiştirebilir."
    }

    private fun Cursor.getStringOrEmpty(column: String): String =
        runCatching { getString(getColumnIndexOrThrow(column)).orEmpty() }.getOrDefault("")

    private fun Cursor.getLongOrZero(column: String): Long =
        runCatching { getLong(getColumnIndexOrThrow(column)) }.getOrDefault(0L)

    private fun Cursor.getIntOrZero(column: String): Int =
        runCatching { getInt(getColumnIndexOrThrow(column)) }.getOrDefault(0)
}
