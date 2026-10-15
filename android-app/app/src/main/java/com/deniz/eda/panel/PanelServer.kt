package com.deniz.eda.panel

import android.content.Context
import android.util.Log
import com.deniz.eda.core.Settings
import com.deniz.eda.data.db.EdaDatabase
import com.deniz.eda.utils.BatteryUtils
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.deniz.eda.utils.DigitUtils

/**
 * EDA Control Panel — Web Server
 * eda.py'deki panel/server.py'nin NanoHTTPD karsiligi.
 * Port 8080'de calisir, index.html'i serve eder.
 */
class PanelServer(
    private val context: Context,
    port: Int = 8080
) : NanoHTTPD("127.0.0.1", port) {

    companion object {
        private const val TAG = "PanelServer"
        const val PORT = 8080
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        Log.d(TAG, "GET $uri")

        return when {
            uri == "/" || uri == "/index.html" -> serveIndex()
            uri == "/api/status" -> serveStatus()
            uri == "/api/logs" -> serveLogs()
            uri.startsWith("/api/") -> serveAction(uri)
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Not Found: $uri"
            )
        }
    }

    private fun serveIndex(): Response {
        return try {
            val html = context.assets.open("panel/index.html")
                .bufferedReader().use { it.readText() }
            newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
        } catch (e: Exception) {
            Log.e(TAG, "index.html okunamadi", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "index.html bulunamadi"
            )
        }
    }

    private fun sonModAl(): String {
        return if (Settings.sonMod != "KAPALI") Settings.sonMod 
               else com.deniz.eda.service.EdaForegroundService.sonMod
    }
    
    @Volatile private var ollamaStatusCache: JSONObject? = null
    @Volatile private var ollamaStatusCheckedAt: Long = 0L

    private fun ollamaStatus(): JSONObject {
        val now = System.currentTimeMillis()
        val cached = ollamaStatusCache
        if (cached != null && now - ollamaStatusCheckedAt < 5000L) return JSONObject(cached.toString())

        val result = try {
            runBlocking {
                val baseUrl = com.deniz.eda.utils.OllamaLocator.bul(context)
                val client = OkHttpClient.Builder()
                    .connectTimeout(1200, TimeUnit.MILLISECONDS)
                    .readTimeout(1200, TimeUnit.MILLISECONDS)
                    .callTimeout(1800, TimeUnit.MILLISECONDS)
                    .build()
                val request = Request.Builder().url("$baseUrl/v1/models").get().build()
                client.newCall(request).execute().use { response ->
                    JSONObject().apply {
                        put("connected", response.isSuccessful)
                        put("url", baseUrl)
                        put("message", if (response.isSuccessful) "Termux Ollama bağlı" else "Ollama yanıt verdi ama hata kodu: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("connected", false)
                put("url", "")
                put("message", "Termux Ollama bağlı değil")
            }
        }
        ollamaStatusCache = JSONObject(result.toString())
        ollamaStatusCheckedAt = now
        Log.i(TAG, "Ollama connection status: connected=${result.optBoolean("connected")} url=${result.optString("url")}")
        return result
    }

    private fun serveStatus(): Response {
        return try {
            val pil = BatteryUtils.pilBilgisiAl(context)
            val db = EdaDatabase.get(context)

            val hafizaSayi = runBlocking { runCatching { db.memoryDao().sayi() }.getOrDefault(0) }
            val logSayi = runBlocking { runCatching { db.logDao().sayi() }.getOrDefault(0) }
            val hatirlatmaSayi = runBlocking {
                runCatching { db.reminderDao().bekleyenler().size }.getOrDefault(0)
            }

            val json = JSONObject().apply {
                put("durum", sonModAl())
                put("pil", JSONObject().apply {
                    put("yuzde", pil?.yuzde ?: 0)
                    put("durum", pil?.durum ?: "?")
                    put("sarj", pil?.sarjOluyorMu ?: false)
                })
                put("mod", if (Settings.arabaModuAktif) "ARABA" else sonModAl())
                put("guvenlik", Settings.guvenlikModuAktif)
                put("ogrenme", Settings.ogrenmeAktif)
                put("kullanici", Settings.kullaniciAdi)
                put("db", JSONObject().apply {
                    put("hafiza", hafizaSayi)
                    put("log", logSayi)
                    put("hatirlatma", hatirlatmaSayi)
                })
                put("ai", Settings.aiProvider)
                put("ollama", ollamaStatus())
                put("gemma", Settings.gemmaModel ?: "gemma2:2b")
                put("saat", DigitUtils.formatTime("HH:mm"))
                put("tarih", SimpleDateFormat("dd MMM yyyy", Locale("tr", "TR")).format(Date()))
            }

            newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "status hatasi", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Hata: ${e.message}")
        }
    }

    private fun serveLogs(): Response {
        return try {
            val satirlar = com.deniz.eda.utils.EdaLog.oku(context, sleep = false)
            val arr = JSONArray()
            for (s in satirlar) {
                arr.put(s)
            }
            val response = JSONObject().apply {
                put("logs", arr)
                put("count", arr.length())
            }
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json; charset=utf-8",
                response.toString()
            )
        } catch (e: Exception) {
            Log.e(TAG, "logs hatasi", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "{\"logs\":[],\"count\":0}"
            )
        }
    }

    private fun serveAction(uri: String): Response {
        val action = uri.removePrefix("/api/")
        var success = true
        val sonuc = when {
            action == "wake" -> {
                val intent = android.content.Intent(context, com.deniz.eda.service.EdaForegroundService::class.java).apply {
                    this.action = "ACTION_AKTIF"
                }
                try {
                    // ═══ اول startService (چون سرویس ممکنه already running باشه) ═══
                    try {
                        context.startService(intent)
                    } catch (e: Exception) {
                        // اگه startService خطا داد، startForegroundService
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        }
                    }
                } catch (e: Exception) { success = false; Log.e(TAG, "wake failed", e) }
                "EDA aktif"
            }
            action == "sleep" -> {
                val intent = android.content.Intent(context, com.deniz.eda.service.EdaForegroundService::class.java).apply {
                    this.action = "ACTION_UYKU"
                }
                try {
                    try {
                        context.startService(intent)
                    } catch (e: Exception) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        }
                    }
                } catch (e: Exception) { success = false; Log.e(TAG, "sleep failed", e) }
                "EDA uyku moduna alındı"
            }
            action == "stop" -> {
                val intent = android.content.Intent(context, com.deniz.eda.service.EdaForegroundService::class.java).apply {
                    this.action = "ACTION_STOP"
                }
                try {
                    try {
                        context.startService(intent)
                    } catch (e: Exception) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        }
                    }
                } catch (e: Exception) { success = false; Log.e(TAG, "stop failed", e) }
                "EDA durduruldu"
            }
            action.startsWith("gemma-") -> {
                // "gemma-gemma3-1b" → "gemma3:1b"
                val model = action.removePrefix("gemma-").replaceFirst("-", ":")
                Settings.gemmaModel = model
                android.util.Log.d("PanelServer", "gemmaModel = $model")
                "Beyin: $model"
            }
            action.startsWith("ai-") -> {
                val ai = action.removePrefix("ai-")
                Settings.aiSaglayici = ai
                "AI: $ai olarak ayarlandı"
            }
            action.startsWith("guvenlik-") -> {
                val deger = action.removePrefix("guvenlik-").toBoolean()
                Settings.guvenlikModuAktif = deger
                "Güvenlik: ${if (deger) "AKTIF" else "PASIF"}"
            }
            action.startsWith("araba-") -> {
                val deger = action.removePrefix("araba-").toBoolean()
                Settings.arabaModuAktif = deger
                "Araba modu: ${if (deger) "AKTIF" else "PASIF"}"
            }
            else -> { success = false; "Bilinmeyen: $action" }
        }

        val json = JSONObject().apply {
            put("ok", success)
            put("mesaj", sonuc)
        }

        val status = if (success) Response.Status.OK else Response.Status.INTERNAL_ERROR
        return newFixedLengthResponse(status, "application/json; charset=utf-8", json.toString())
    }
}
