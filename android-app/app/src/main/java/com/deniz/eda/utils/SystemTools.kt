package com.deniz.eda.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.deniz.eda.core.Settings
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

ÖRNEK:
Kullanıcı: "internete erişimin var mı?"
Sen: <TOOL>CHECK_INTERNET</TOOL>

Kullanıcı: "merhaba nasılsın?"
Sen: Merhaba deniz, iyiyim sen nasılsın?
        """.trimIndent()
    }

    /**
     * ابزار X رو اجرا کن و نتیجه رو برگردون
     */
    suspend fun runTool(name: String, context: Context): String {
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
                else -> "Bilinmeyen araç: $name"
            }
        } catch (e: Exception) {
            "Araç hatası ($name): ${e.message}"
        }
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
