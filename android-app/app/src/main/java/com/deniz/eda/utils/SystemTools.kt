package com.deniz.eda.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.deniz.eda.core.Settings
import com.deniz.eda.data.DiaryStore
import com.deniz.eda.data.LearningStore
import com.deniz.eda.data.MemoryStore
import com.deniz.eda.data.ReminderStore
import java.util.Calendar
import com.deniz.eda.utils.OllamaLocator
import com.deniz.eda.utils.ExtraCommands
import com.deniz.eda.utils.FlashlightUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * SystemTools — ابزارهای خودآموز Eda
 * Eda از این ابزارها استفاده می‌کنه وقتی لازمه (Function Calling)
 */
object SystemTools {

    /**
     * لیست ابزارها برای LLM
     */
    fun listTools(): String {
        return """
ALETLER (Sadece ihtiyaç duyduğunda kullan):

Kullanıcı şu tür sorular sorarsa, sadece <TOOL>ADI</TOOL> yaz:
- "internet var mı?", "internete erişim" → <TOOL>CHECK_INTERNET</TOOL>
- "ollama çalışıyor mu?", "gemma hazır mı?" → <TOOL>CHECK_OLLAMA</TOOL>
- "groq çalışıyor mu?", "groq bağlantısı" → <TOOL>CHECK_GROQ</TOOL>
- "pollinations çalışıyor mu?" → <TOOL>CHECK_POLLINATIONS</TOOL>
- "pil nasıl?", "şarj ne kadar?" → <TOOL>GET_BATTERY</TOOL>
- "hangi AI kullanıyorsun?", "hangi modele bağlısın?" → <TOOL>GET_AI</TOOL>
- "hangi moddasın?", "uyku modunda mısın?" → <TOOL>GET_MODE</TOOL>
- "sistemi kontrol et", "her şey nasıl?" → <TOOL>FULL_CHECK</TOOL>
- "hafızanda ne var?" → <TOOL>GET_MEMORY</TOOL>
- "günlüğümü göster" → <TOOL>GET_DIARY</TOOL>
- "hatırlatmalarım neler?" → <TOOL>GET_REMINDERS</TOOL>
- "ne öğrendin?" → <TOOL>GET_LEARNED</TOOL>
- "saat kaç?" → <TOOL>GET_TIME</TOOL>
- "bugünün tarihi" → <TOOL>GET_DATE</TOOL>
- Kullanıcı açıkça feneri aç/kapat derse → <TOOL>FLASHLIGHT_ON</TOOL> / <TOOL>FLASHLIGHT_OFF</TOOL>
- Kullanıcı YouTube, Chrome, WhatsApp, Telegram, Haritalar, Telefon, Mesajlar veya Kamera aç derse → <TOOL>OPEN_APP: uygulama</TOOL>
- Kullanıcı Wi-Fi, ses veya ekran ayarlarını aç derse → <TOOL>OPEN_WIFI_SETTINGS</TOOL> / <TOOL>OPEN_SOUND_SETTINGS</TOOL> / <TOOL>OPEN_DISPLAY_SETTINGS</TOOL>
- Kullanıcı açıkça bip sesini aç/kapat derse → <TOOL>BEEP_ON</TOOL> / <TOOL>BEEP_OFF</TOOL>
- Kullanıcı müziği başlat/durdur/sonraki/önceki derse → <TOOL>MEDIA_PLAY</TOOL> / <TOOL>MEDIA_PAUSE</TOOL> / <TOOL>MEDIA_NEXT</TOOL> / <TOOL>MEDIA_PREVIOUS</TOOL>
- Kullanıcı telefon ayarlarını veya TTS ayarlarını aç derse → <TOOL>OPEN_SETTINGS</TOOL> / <TOOL>OPEN_TTS_SETTINGS</TOOL>
- Kullanıcı kamera aç/fotoğraf çek derse (kamera uygulamasını açar, gizlice fotoğraf çekmez) → <TOOL>OPEN_CAMERA</TOOL>
- Kullanıcı telefonumu bul derse → <TOOL>FIND_PHONE</TOOL>; sesi durdur derse → <TOOL>STOP_FIND_PHONE</TOOL>
- Kullanıcı telefonunu kilitle derse → <TOOL>LOCK_PHONE</TOOL>
- Kullanıcı sistem testi isterse → <TOOL>RUN_SYSTEM_TEST</TOOL>
- Öğrenme modunu açıkça aç/kapat derse → <TOOL>LEARNING_ON</TOOL> / <TOOL>LEARNING_OFF</TOOL>
- Kullanıcı açıkça "hafızana kaydet/hatırla" derse → <TOOL>SAVE_MEMORY: kaydedilecek metin</TOOL>
- Kullanıcı açıkça "günlüğüme yaz" derse → <TOOL>ADD_DIARY: günlük metni</TOOL>
- Kullanıcı belirli saatli hatırlatma isterse → <TOOL>ADD_REMINDER|SAAT|DAKİKA|hatırlatma metni</TOOL>
- Kullanıcı açıkça erişilebilir metni okumamı isterse → <TOOL>SCREEN_READ</TOOL>
- Kullanıcı ekrandaki görünür metinleri OCR ile okumamı isterse → <TOOL>SCREEN_VISION</TOOL>
- Kullanıcı ekranda görünen, hassas olmayan bir etikete dokunmamı isterse → <TOOL>SCREEN_TAP: görünen etiket</TOOL>
- Kullanıcı erişilebilirlik ağacında bulunmayan görünür bir etikete dokunmamı isterse → <TOOL>SCREEN_TAP_VISUAL: OCR'de görünen etiket</TOOL>
- Kullanıcı açıkça seçili metin alanına verdiği metni yazmamı isterse → <TOOL>SCREEN_TYPE: yazılacak metin</TOOL>
- Kullanıcı ekranı yukarı/aşağı kaydırmamı isterse → <TOOL>SCREEN_SCROLL_UP</TOOL> / <TOOL>SCREEN_SCROLL_DOWN</TOOL>
- Kullanıcı geri, ana ekran veya bildirim panelini isterse → <TOOL>SCREEN_BACK</TOOL> / <TOOL>SCREEN_HOME</TOOL> / <TOOL>SCREEN_NOTIFICATIONS</TOOL>
- Kullanıcı açıkça son uygulamaları görmek isterse → <TOOL>SCREEN_RECENTS</TOOL>
- Kullanıcı açıkça hızlı ayar panelini açmak isterse → <TOOL>SCREEN_QUICK_SETTINGS</TOOL>
- Kullanıcı açıkça Android güç menüsünü göstermek isterse → <TOOL>SCREEN_POWER_DIALOG</TOOL>
- Kullanıcı açıkça Android ekran görüntüsü eylemini isterse → <TOOL>SCREEN_TAKE_SCREENSHOT</TOOL>
- Ekran araçları için Android erişilebilirlik hizmeti kullanıcı tarafından önceden etkinleştirilmiş olmalı. Önce ekranı oku, sonra yalnızca istenen işlemi yap ve tekrar okuyarak sonucu doğrula. Bankacılık, kimlik doğrulama, parola, ödeme, para transferi, mesaj gönderme, satın alma veya silme işlemlerini tamamlama; son onayı kullanıcıya bırak.

ÖRNEK:
Kullanıcı: "internete erişimin var mı?"
Sen: <TOOL>CHECK_INTERNET</TOOL>

Kullanıcı: "merhaba nasılsın?"
Sen: Merhaba ${Settings.kullaniciAdi}, iyiyim. Sen nasılsın?
        """.trimIndent()
    }

    /**
     * ابزار X رو اجرا کن و نتیجه رو برگردون
     */
    suspend fun runTool(name: String, context: Context, arguments: String = ""): String {
        return try {
            when (name) {
                "CHECK_INTERNET" -> checkInternetText(context)
                "CHECK_OLLAMA" -> checkOllamaText(context)
                "CHECK_GROQ" -> checkGroqText()
                "CHECK_POLLINATIONS" -> checkPollinationsText()
                "GET_BATTERY" -> getBatteryText(context)
                "GET_AI" -> getAIText()
                "GET_MODE" -> getModeText()
                "FULL_CHECK" -> fullCheck(context)
                "GET_MEMORY" -> MemoryStore.goster(context)
                "GET_DIARY" -> DiaryStore.goster(context)
                "GET_REMINDERS" -> ReminderStore.listeMetni(context)
                "GET_LEARNED" -> learnedText(context)
                "GET_TIME" -> timeText()
                "GET_DATE" -> dateText()
                "OPEN_APP" -> openAllowedApp(context, arguments)
                "OPEN_WIFI_SETTINGS" -> openSettingsPanel(context, "wifi")
                "OPEN_SOUND_SETTINGS" -> openSettingsPanel(context, "sound")
                "OPEN_DISPLAY_SETTINGS" -> openSettingsPanel(context, "display")
                "SCREEN_READ" -> com.deniz.eda.phone.PhoneControlService.run("read")
                "SCREEN_VISION" -> com.deniz.eda.phone.PhoneControlService.run("vision")
                "SCREEN_TAP" -> if (arguments.isBlank()) "Dokunulacak ekrandaki etiketi belirt." else com.deniz.eda.phone.PhoneControlService.run("tap", arguments)
                "SCREEN_TAP_VISUAL" -> if (arguments.isBlank()) "Görsel olarak bulunacak etiketi belirt." else com.deniz.eda.phone.PhoneControlService.run("tap_visual", arguments)
                "SCREEN_TYPE" -> if (arguments.isBlank()) "Yazılacak metin boş." else com.deniz.eda.phone.PhoneControlService.run("type", arguments)
                "SCREEN_SCROLL_UP" -> com.deniz.eda.phone.PhoneControlService.run("up")
                "SCREEN_SCROLL_DOWN" -> com.deniz.eda.phone.PhoneControlService.run("down")
                "SCREEN_BACK" -> com.deniz.eda.phone.PhoneControlService.run("back")
                "SCREEN_HOME" -> com.deniz.eda.phone.PhoneControlService.run("home")
                "SCREEN_NOTIFICATIONS" -> com.deniz.eda.phone.PhoneControlService.run("notifications")
                "SCREEN_RECENTS" -> com.deniz.eda.phone.PhoneControlService.run("recents")
                "SCREEN_QUICK_SETTINGS" -> com.deniz.eda.phone.PhoneControlService.run("quick_settings")
                "SCREEN_POWER_DIALOG" -> com.deniz.eda.phone.PhoneControlService.run("power_dialog")
                "SCREEN_TAKE_SCREENSHOT" -> com.deniz.eda.phone.PhoneControlService.run("screenshot")
                "SAVE_MEMORY" -> arguments.trim().takeIf { it.isNotBlank() }?.let {
                    MemoryStore.ekle(context, it, "agent")
                    "Bilgi hafızaya kaydedildi."
                } ?: "Hafızaya kaydedilecek metin boş."
                "ADD_DIARY" -> arguments.trim().takeIf { it.isNotBlank() }?.let {
                    DiaryStore.ekle(context, it)
                    "Not günlüğe kaydedildi."
                } ?: "Günlüğe yazılacak metin boş."
                "ADD_REMINDER" -> addReminder(context, arguments)
                "FLASHLIGHT_ON" -> if (FlashlightUtils.ac(context)) "Fener açıldı." else "Fener açılamadı; cihaz izni veya flaş uygun olmayabilir."
                "FLASHLIGHT_OFF" -> if (FlashlightUtils.kapat(context)) "Fener kapatıldı." else "Fener kapatılamadı."
                "BEEP_ON" -> { Settings.beepAktif = true; "Bip sesi açıldı." }
                "BEEP_OFF" -> { Settings.beepAktif = false; "Bip sesi kapatıldı." }
                "MEDIA_PLAY" -> ExtraCommands.muzikBaslat(context, Settings.kullaniciAdi)
                "MEDIA_PAUSE" -> ExtraCommands.muzikDurdur(context, Settings.kullaniciAdi)
                "MEDIA_NEXT" -> ExtraCommands.muzikSonraki(context, Settings.kullaniciAdi)
                "MEDIA_PREVIOUS" -> ExtraCommands.muzikOnceki(context, Settings.kullaniciAdi)
                "OPEN_SETTINGS" -> ExtraCommands.ayarlariAc(context, Settings.kullaniciAdi)
                "OPEN_TTS_SETTINGS" -> ExtraCommands.sesMotoruDegistir(context, Settings.kullaniciAdi)
                "OPEN_CAMERA" -> ExtraCommands.fotografCek(context, Settings.kullaniciAdi)
                "FIND_PHONE" -> ExtraCommands.telefonuBul(context, Settings.kullaniciAdi)
                "STOP_FIND_PHONE" -> ExtraCommands.telefonuBulDurdur(Settings.kullaniciAdi)
                "LOCK_PHONE" -> ExtraCommands.telefonuKilitle(context, Settings.kullaniciAdi)
                "RUN_SYSTEM_TEST" -> ExtraCommands.sistemTest(context, Settings.kullaniciAdi)
                "LEARNING_ON" -> { Settings.ogrenmeAktif = true; "Öğrenme modu açıldı." }
                "LEARNING_OFF" -> { Settings.ogrenmeAktif = false; "Öğrenme modu kapatıldı." }
                else -> "Bilinmeyen araç: $name"
            }
        } catch (e: Exception) {
            "Araç hatası ($name): ${e.message}"
        }
    }

    private fun openSettingsPanel(context: Context, kind: String): String {
        val intent = when (kind) {
            "wifi" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(AndroidSettings.Panel.ACTION_WIFI)
                else Intent(AndroidSettings.ACTION_WIFI_SETTINGS)
            "sound" -> Intent(AndroidSettings.ACTION_SOUND_SETTINGS)
            "display" -> Intent(AndroidSettings.ACTION_DISPLAY_SETTINGS)
            else -> return "Bu ayar paneli desteklenmiyor."
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            when (kind) {
                "wifi" -> "Wi-Fi ayarları açıldı. Android bu sürümde Wi-Fi'yi doğrudan kapatmaya izin vermeyebilir; gerekirse panelden değiştir."
                "sound" -> "Ses ayarları açıldı."
                else -> "Ekran ayarları açıldı."
            }
        } catch (_: Exception) { "Ayarlar açılamadı." }
    }

    private fun openAllowedApp(context: Context, raw: String): String {
        val app = raw.trim().lowercase(java.util.Locale.ROOT)
        val packages = when (app) {
            "chrome", "browser", "tarayıcı", "کروم" -> listOf("com.android.chrome")
            "youtube", "یوتیوب" -> listOf("com.google.android.youtube")
            "maps", "map", "haritalar", "نقشه" -> listOf("com.google.android.apps.maps")
            "camera", "kamera", "دوربین" -> listOf("com.sec.android.app.camera", "com.android.camera2")
            "settings", "ayarlar", "تنظیمات" -> listOf("com.android.settings")
            "whatsapp", "واتساپ" -> listOf("com.whatsapp")
            "telegram", "تلگرام" -> listOf("org.telegram.messenger", "org.thunderdog.challegram")
            "phone", "telefon", "تماس" -> listOf("com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer")
            "messages", "mesajlar", "پیام‌ها" -> listOf("com.samsung.android.messaging", "com.google.android.apps.messaging")
            else -> return "Bu uygulama güvenli açma listesinde yok."
        }
        for (pkg in packages) {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: continue
            return try {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                "Uygulama açıldı: $app"
            } catch (_: Exception) { "Uygulama açılamadı." }
        }
        return "İstenen uygulama telefonda bulunamadı."
    }

    private fun addReminder(context: Context, raw: String): String {
        val parts = raw.split("|", limit = 4)
        if (parts.size != 3) return "Hatırlatma için saat, dakika ve metin gerekli."
        val hour = parts[0].trim().toIntOrNull()
        val minute = parts[1].trim().toIntOrNull()
        val text = parts[2].trim()
        if (hour == null || hour !in 0..23 || minute == null || minute !in 0..59 || text.isBlank()) {
            return "Hatırlatma bilgileri geçersiz. Saati 0–23, dakikayı 0–59 aralığında ver."
        }
        val reminder = ReminderStore.ekle(context, text, hour, minute)
        return "Hatırlatma kaydedildi: %02d:%02d — %s".format(java.util.Locale.ROOT, reminder.saat, reminder.dakika, reminder.metin)
    }

    // ═══════════════════════════════════════
    //  ابزارها
    // ═══════════════════════════════════════

    suspend fun checkInternet(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return@withContext false
            val caps = cm.getNetworkCapabilities(network) ?: return@withContext false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) { false }
    }

    private suspend fun checkInternetText(context: Context): String {
        return if (checkInternet(context)) "Internet var" else "Internet yok"
    }

    private suspend fun checkOllamaText(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val baseUrl = OllamaLocator.bul(context)
            val client = OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url("$baseUrl/v1/models").build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) "Ollama acik" else "Ollama hata ${resp.code}"
            }
        } catch (e: Exception) { "Ollama kapalı veya Termux erişilemiyor" }
    }

    private suspend fun checkGroqText(): String {
        if (Settings.groqApiKey.isBlank()) return "Groq anahtari yok"
        return withContext(Dispatchers.IO) {
            try {
                val c = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()
                val r = Request.Builder().url("https://api.groq.com/openai/v1/models").header("Authorization", "Bearer " + Settings.groqApiKey).build()
                c.newCall(r).execute().use { if (it.isSuccessful) "Groq bagli LIVE" else "Groq hata " + it.code }
            } catch (e: Exception) { "Groq erisilemedi" }
        }
    }

    private suspend fun checkPollinationsText(): String = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url("https://text.pollinations.ai/").build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) "Pollinations calisiyor LIVE" else "Pollinations hata kodu ${resp.code}"
            }
        } catch (e: Exception) { "Pollinations hata" }
    }

    private fun getBatteryText(context: Context): String {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val status = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_STATUS)
            val durum = when (status) {
                android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "sarj oluyor"
                android.os.BatteryManager.BATTERY_STATUS_FULL -> "dolu"
                android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "pilde"
                else -> ""
            }
            "Pil yuzde $level $durum"
        } catch (e: Exception) { "Pil bilgisi alinamadi" }
    }

    private fun getAIText(): String {
        return if (Settings.aiProvider.equals("ollama", ignoreCase = true)) {
            "Yapay zeka Ollama (Termux), model gemma2:2b"
        } else {
            "Yapay zeka ${Settings.aiSaglayici}, model ${Settings.gemmaModel}"
        }
    }

    private fun getModeText(): String {
        return "Mod ${Settings.sonMod}"
    }


    private fun timeText(): String {
        val now = Calendar.getInstance()
        val rawHour = now.get(Calendar.HOUR)
        val hour = if (rawHour == 0) 12 else rawHour
        val minute = now.get(Calendar.MINUTE)
        val suffix = if (now.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        return "Saat %02d:%02d %s".format(java.util.Locale.ROOT, hour, minute, suffix)
    }

    private fun dateText(): String =
        java.text.SimpleDateFormat("dd MMMM yyyy, EEEE", java.util.Locale("tr", "TR"))
            .format(java.util.Date())

    private fun learnedText(context: Context): String {
        val items = LearningStore.tumOgrendikleri(context)
        if (items.isEmpty()) return "Henüz öğretilmiş bir komut yok."
        return "Öğrendiklerim: " + items.take(10).joinToString("; ") { it.first + " → " + it.second }
    }

    suspend fun fullCheck(context: Context): String {
        val sb = StringBuilder()
        sb.append("Sistem durumu:\n")
        sb.append("• ${checkInternetText(context)}\n")
        sb.append("• ${getBatteryText(context)}\n")
        sb.append("• ${checkOllamaText(context)}\n")
        sb.append("• ${checkGroqText()}\n")
        sb.append("• ${getAIText()}\n")
        sb.append("• ${getModeText()}\n")
        return sb.toString().trim()
    }
}
