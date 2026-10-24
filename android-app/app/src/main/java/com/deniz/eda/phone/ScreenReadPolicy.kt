package com.deniz.eda.phone

import java.util.Locale

/**
 * Screen text can contain private material. Only explicit read-screen requests
 * may invoke the screen reader; such requests must use the on-device provider.
 */
object ScreenReadPolicy {
    fun isExplicitReadRequest(raw: String): Boolean {
        val text = raw.lowercase(Locale.ROOT)
            .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
            .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')
        val phrases = listOf(
            "ekranda ne yaz", "ekranda ne var", "ekrani oku", "ekrandaki yazi",
            "ekrandaki metin", "bu sayfada ne yaz", "bu sayfada ne var",
            "ekrani bana anlat", "ekrani analiz et", "ekran goruntusunu oku",
            "ekranda gordugunu oku", "read the screen", "what is on the screen",
            "what's on the screen", "what does the screen say"
        )
        return phrases.any(text::contains)
    }
}