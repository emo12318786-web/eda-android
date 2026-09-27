package com.deniz.eda.panel

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

/**
 * Kontrol Paneli — WebView içinde localhost:8080'i açar.
 * Auto-refresh: her 5 saniyede bir sayfayı yenile (ama state korunsun)
 */
class PanelActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EDA-Panel"
        private const val AUTO_REFRESH_MS = 5000L
    }

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // به جای reload، فقط توابع refresh صفحه رو صدا بزن (اگه وجود داره)
                    view?.evaluateJavascript(
                        """
                        (function() {
                            if (window._edaAutoRefresh) clearInterval(window._edaAutoRefresh);
                            window._edaAutoRefresh = setInterval(function() {
                                // صفحه داخلی خودش refresh کنه، نه reload کامل
                                if (typeof window.yenile === 'function') {
                                    window.yenile();
                                } else if (typeof window.loadStatus === 'function') {
                                    window.loadStatus();
                                } else if (typeof window.refresh === 'function') {
                                    window.refresh();
                                }
                            }, $AUTO_REFRESH_MS);
                        })();
                        """.trimIndent(),
                        null
                    )
                }
            }
        }

        setContentView(webView)
        webView.loadUrl("http://127.0.0.1:${PanelServer.PORT}/")
    }

    override fun onResume() {
        super.onResume()
        // فقط وقتی کاربر برمی‌گرده، reload کن
        webView.loadUrl("http://127.0.0.1:${PanelServer.PORT}/")
    }

    override fun onDestroy() {
        super.onDestroy()
        // پاک کردن auto-refresh
        webView.evaluateJavascript("if (window._edaAutoRefresh) clearInterval(window._edaAutoRefresh);", null)
        webView.destroy()
    }
}
