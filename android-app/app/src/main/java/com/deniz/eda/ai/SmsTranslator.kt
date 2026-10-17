package com.deniz.eda.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * SMS translation only. Uses Google's unofficial public web-translate endpoint;
 * it may be rate-limited or unavailable. Failure is represented by null.
 * Never return the original source as a fallback.
 */
object SmsTranslator {
    suspend fun translateToTurkish(originalText: String): String? = withContext(Dispatchers.IO) {
        val source = originalText.trim()
        if (source.isBlank()) return@withContext null
        var connection: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(source, "UTF-8")
            val endpoint = URL(
                "https://translate.googleapis.com/translate_a/single" +
                    "?client=gtx&sl=auto&tl=tr&dt=t&q=$encoded"
            )
            connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 7000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            if (connection.responseCode !in 200..299) {
                Log.w("SmsTranslator", "Translation HTTP status=${connection.responseCode}")
                return@withContext null
            }
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONArray(response)
            val chunks = root.optJSONArray(0) ?: return@withContext null
            val translated = buildString {
                for (i in 0 until chunks.length()) {
                    val chunk = chunks.optJSONArray(i) ?: continue
                    append(chunk.optString(0, ""))
                }
            }.trim()
            if (translated.isBlank()) return@withContext null

            // Never speak an untranslated non-Latin source as if it were Turkish.
            val hasNonLatinSource = source.any { Character.UnicodeScript.of(it.code) in setOf(
                Character.UnicodeScript.ARABIC,
                Character.UnicodeScript.CYRILLIC,
                Character.UnicodeScript.HEBREW,
                Character.UnicodeScript.DEVANAGARI,
                Character.UnicodeScript.HAN,
                Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA
            ) }
            if (hasNonLatinSource && translated.equals(source, ignoreCase = true)) {
                Log.w("SmsTranslator", "Translation output identical to non-Latin source; refusing to speak source")
                return@withContext null
            }
            translated
        } catch (e: Exception) {
            Log.w("SmsTranslator", "Translation failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }
}
