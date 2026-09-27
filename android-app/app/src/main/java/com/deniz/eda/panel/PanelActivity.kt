package com.deniz.eda.panel

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

/**
 * Kontrol Paneli — WebView içinde localhost:PORT'u açar.
 * Auto-refresh: her 5 saniyede bir sayfayı yenile
 */
class PanelActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EDA-Panel"
        private const val AUTO_REFRESH_MS = 5000L
    }

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // WebView
        webView = WebView(this).apply {
            webViewClient = WebViewClient()
            
            // ═══ Cache kapat ═══
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            
            // ═══ Auto-refresh (JS injection) ═══
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // هر 5 saniyede bir sayfayı yenile
                    view?.evaluateJavascript(
                        """
                        if (!window._edaAutoRefresh) {
                            window._edaAutoRefresh = setInterval(function() {
                                location.reload();
                            }, $AUTO_REFRESH_MS);
                        }
                        """.trimIndent(),
                        null
                    )
                }
            }
        }

        setContentView(webView)

        // ═══ WebView'e yükle ═══
        webView.loadUrl("http://127.0.0.1:${PanelServer.PORT}/")
    }

    override fun onResume() {
        super.onResume()
        // Sayfaya geri döndüğünde yenile
        webView.reload()
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }
}
