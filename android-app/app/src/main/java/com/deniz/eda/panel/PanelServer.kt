package com.deniz.eda.panel

import android.content.Context
import android.util.Log
import com.deniz.eda.core.Settings
import com.deniz.eda.data.db.EdaDatabase
import com.deniz.eda.utils.BatteryUtils
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * EDA Control Panel — Web Server
 * eda.py'deki panel/server.py'nin NanoHTTPD karsiligi.
 * Port 8080'de calisir, index.html'i serve eder.
 */
class PanelServer(
    private val context: Context,
    port: Int = 8080
) : NanoHTTPD(port) {

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
                put("durum", "AKTIF")
                put("pil", JSONObject().apply {
                    put("yuzde", pil?.yuzde ?: 0)
                    put("durum", pil?.durum ?: "?")
                    put("sarj", pil?.sarjOluyorMu ?: false)
                })
                put("mod", if (Settings.arabaModuAktif) "ARABA" else "AKTIF")
                put("guvenlik", Settings.guvenlikModuAktif)
                put("ogrenme", Settings.ogrenmeAktif)
                put("kullanici", Settings.kullaniciAdi)
                put("db", JSONObject().apply {
                    put("hafiza", hafizaSayi)
                    put("log", logSayi)
                    put("hatirlatma", hatirlatmaSayi)
                })
                put("saat", SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
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
            val db = EdaDatabase.get(context)
            val loglar = runBlocking { runCatching { db.logDao().listele(50) }.getOrDefault(emptyList()) }

            val arr = JSONArray()
            for (log in loglar) {
                arr.put(JSONObject().apply {
                    put("id", log.id)
                    put("kullanici", log.kullaniciMetin)
                    put("eda", log.edaCevap)
                    put("kaynak", log.kaynak)
                    put("tarih", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.tarih)))
                })
            }

            newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", arr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "logs hatasi", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "[]")
        }
    }

    private fun serveAction(uri: String): Response {
        val action = uri.removePrefix("/api/")
        val sonuc = when {
            action == "wake" -> "EDA aktif"
            action == "sleep" -> "EDA uyku moduna alındı"
            action == "stop" -> "EDA durduruldu"
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
            else -> "Bilinmeyen: $action"
        }

        val json = JSONObject().apply {
            put("ok", true)
            put("mesaj", sonuc)
        }

        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json.toString())
    }
}
