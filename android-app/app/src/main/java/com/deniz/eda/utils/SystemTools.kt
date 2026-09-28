package com.deniz.eda.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.deniz.eda.core.Settings
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
Sen: Merhaba denizçim, iyiyim sen nasılsın?
        """.trimIndent()
    }

    /**
     * ابزار X رو اجرا کن و نتیجه رو برگردون
     */
    suspend fun runTool(name: String, context: Context): String {
        return try {
            when (name) {
                "CHECK_INTERNET" -> checkInternetText(context)
                "CHECK_OLLAMA" -> checkOllamaText()
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
        return if (checkInternet(context)) "internet: var" else "internet: yok"
    }

    private suspend fun checkOllamaText(): String = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .build()
            val url = "http://192.168.1.3:11434/v1/models"
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) "ollama: çalışıyor" else "ollama: hata ${resp.code}"
            }
        } catch (e: Exception) { "ollama: kapalı (${e.message})" }
    }

    private fun checkGroqText(): String {
        return if (Settings.groqApiKey.isBlank()) "groq: API key yok"
        else "groq: API key var (uzunluk ${Settings.groqApiKey.length})"
    }

    private suspend fun checkPollinationsText(): String = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url("https://text.pollinations.ai/").build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) "pollinations: çalışıyor" else "pollinations: hata ${resp.code}"
            }
        } catch (e: Exception) { "pollinations: hata (${e.message})" }
    }

    private fun getBatteryText(context: Context): String {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val status = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_STATUS)
            val durum = when (status) {
                android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "şarjda"
                android.os.BatteryManager.BATTERY_STATUS_FULL -> "dolu"
                android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "deşarjda"
                else -> "?"
            }
            "pil: %$level ($durum)"
        } catch (e: Exception) { "pil: bilgi alınamadı" }
    }

    private fun getAIText(): String {
        return "ai: ${Settings.aiSaglayici} (gemma model: ${Settings.gemmaModel})"
    }

    private fun getModeText(): String {
        return "mod: ${Settings.sonMod}"
    }

    suspend fun fullCheck(context: Context): String {
        val sb = StringBuilder()
        sb.append("Sistem durumu:\n")
        sb.append("• ${checkInternetText(context)}\n")
        sb.append("• ${getBatteryText(context)}\n")
        sb.append("• ${checkOllamaText()}\n")
        sb.append("• ${checkGroqText()}\n")
        sb.append("• ${getAIText()}\n")
        sb.append("• ${getModeText()}\n")
        return sb.toString().trim()
    }
}
