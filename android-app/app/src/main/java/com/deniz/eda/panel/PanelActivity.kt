package com.deniz.eda.panel

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.deniz.eda.R

/**
 * Kontrol Paneli — WebView icinde localhost:8080'i acar.
 * PanelServer arka planda calisir.
 */
class PanelActivity : AppCompatActivity() {

    private var server: PanelServer? = null
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // WebView
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
        }
        setContentView(webView)

        // Server baslat
        try {
            server = PanelServer(applicationContext, PanelServer.PORT)
            server?.start(NanoHttpServer.SOCKET_READ_TIMEOUT, false)
            Toast.makeText(this, "Panel başlatıldı: 8080", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Sunucu başlatılamadı: ${e.message}", Toast.LENGTH_LONG).show()
        }

        // WebView'e yükle
        webView.loadUrl("http://127.0.0.1:${PanelServer.PORT}/")
    }

    override fun onDestroy() {
        server?.stop()
        webView.destroy()
        super.onDestroy()
    }
}

// NanoHTTPD'nin SOCKET_READ_TIMEOUT sabiti
private object NanoHttpServer {
    const val SOCKET_READ_TIMEOUT = 5000
}
