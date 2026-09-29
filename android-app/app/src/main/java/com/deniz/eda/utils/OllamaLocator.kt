package com.deniz.eda.utils

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Ollama adresini otomatik bulur.
 * Wi-Fi / Data / VPN durumuna gore IP degisir.
 */
object OllamaLocator {
    
    private const val PORT = 11434
    private var cachedUrl: String? = null
    
    private fun yerelIpAl(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in Collections.list(iface.inetAddresses)) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress
                        if (ip != null && !ip.startsWith("127.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OllamaLocator", "IP alinamadi: ${e.message}")
        }
        return null
    }
    
    suspend fun bul(context: Context): String = withContext(Dispatchers.IO) {
        // Cache varsa ve hala calisiyorsa, onu kullan
        cachedUrl?.let { cached ->
            if (testEt(cached)) return@withContext cached
        }
        
        val adaylar = mutableListOf<String>()
        adaylar.add("127.0.0.1")
        
        val yerelIp = yerelIpAl()
        if (yerelIp != null) adaylar.add(yerelIp)
        
        // Yaygin adresler
        adaylar.add("192.168.1.3")
        adaylar.add("10.0.2.2")
        
        for (host in adaylar) {
            val url = "http://$host:$PORT"
            if (testEt(url)) {
                cachedUrl = url
                Log.i("OllamaLocator", "Ollama bulundu: $url")
                return@withContext url
            }
        }
        
        Log.w("OllamaLocator", "Ollama bulunamadi, fallback: 127.0.0.1")
        cachedUrl = "http://127.0.0.1:$PORT"
        cachedUrl!!
    }
    
    private fun testEt(baseUrl: String): Boolean {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url("$baseUrl/v1/models").build()
            client.newCall(req).execute().use { resp -> resp.isSuccessful }
        } catch (e: Exception) { false }
    }
}
