package com.deniz.eda.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.deniz.eda.core.Settings
import com.deniz.eda.data.DiaryStore
import com.deniz.eda.data.LearningStore
import com.deniz.eda.data.MemoryStore
import com.deniz.eda.data.ReminderStore
import java.util.Calendar
import com.deniz.eda.utils.OllamaLocator
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
- Kullanıcı açıkça "hafızana kaydet/hatırla" derse → <TOOL>SAVE_MEMORY: kaydedilecek metin</TOOL>
- Kullanıcı açıkça "günlüğüme yaz" derse → <TOOL>ADD_DIARY: günlük metni</TOOL>
- Kullanıcı belirli saatli hatırlatma isterse → <TOOL>ADD_REMINDER|SAAT|DAKİKA|hatırlatma metni</TOOL>

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
                "SAVE_MEMORY" -> arguments.trim().takeIf { it.isNotBlank() }?.let {
                    MemoryStore.ekle(context, it, "agent")
                    "Bilgi hafızaya kaydedildi."
                } ?: "Hafızaya kaydedilecek metin boş."
                "ADD_DIARY" -> arguments.trim().takeIf { it.isNotBlank() }?.let {
                    DiaryStore.ekle(context, it)
                    "Not günlüğe kaydedildi."
                } ?: "Günlüğe yazılacak metin boş."
                "ADD_REMINDER" -> addReminder(context, arguments)
                else -> "Bilinmeyen araç: $name"
            }
        } catch (e: Exception) {
            "Araç hatası ($name): ${e.message}"
        }
    }

    private fun addReminder(context: Context, raw: String): String {
        val parts = raw.split("|", limit = 4)
        if (parts.size != 4) return "Hatırlatma için saat, dakika ve metin gerekli."
        val hour = parts[1].trim().toIntOrNull()
        val minute = parts[2].trim().toIntOrNull()
        val text = parts[3].trim()
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
        } catch (e: Exception) { "Ollama kapali" }
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
        return "Yapay zeka ${Settings.aiSaglayici}, model ${Settings.gemmaModel}"
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
