package com.deniz.eda.phone

import android.content.Context
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Fast deterministic bridge from EDA's existing voice loop to the user-enabled
 * local AccessibilityService. Simple phone actions do not need an LLM round-trip.
 * Returns null for ordinary conversation so the existing CommandProcessor remains intact.
 */
object PhoneTaskRouter {
    private fun norm(value: String): String = value.lowercase(Locale.ROOT)
        .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
        .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')
        .replace('ə', 'e').replace(Regex("\\s+"), " ").trim()

    suspend fun tryHandle(context: Context, spoken: String): String? {
        val raw = spoken.trim()
        val q = norm(raw)
        val phoneIntent = listOf(
            "ekrani oku", "ekranda ne yaziyor",
            "asagi kaydir", "asagi in", "yukari kaydir", "yukari cik",
            "bildirimleri ac", "bildirim panelini ac", "hizli ayarlari ac",
            "geri git", "geri don", "ana ekrana don", "son uygulamalari ac",
            "ekrandaki metni oku", "telefon ekranini oku",
            "ekrandaki metne dokun", "ekrandaki ogeye dokun",
            "ekrandaki dugmeye bas", "ekrandaki yaziyi sec"
        ).any { q.contains(it) } || quotedTarget(raw) != null
        if (!phoneIntent) return null

        if (!PhoneControlService.isEnabled()) {
            return "Telefon ekranına erişmek için EDA'nın Erişilebilirlik iznini bir kez açmalısın. Sonra bu komutları doğrudan Eda'ya söyleyebilirsin; ayrı kontrol sayfasını açman gerekmez."
        }

        return try {
            when {
                q.contains("bildirim") -> PhoneControlService.run("notifications")
                q.contains("hizli ayar") -> PhoneControlService.run("quick_settings")
                q.contains("ana ekrana") -> PhoneControlService.run("home")
                q.contains("son uygulama") -> PhoneControlService.run("recents")
                q.contains("geri git") || q.contains("geri don") -> PhoneControlService.run("back")
                q.contains("asagi kaydir") || q.contains("asagi in") -> {
                    val action = PhoneControlService.run("down")
                    delay(350)
                    if (q.contains("oku") || q.contains("ne yaziyor")) {
                        val screenText = PhoneControlService.run("read")
                        "$action\nEkranda görünenler:\n${screenText.take(1100)}"
                    } else action
                }
                q.contains("yukari kaydir") || q.contains("yukari cik") -> PhoneControlService.run("up")
                q.contains("ekrani oku") || q.contains("ekranda ne yaziyor") ||
                    q.contains("ekrandaki metni oku") || q.contains("telefon ekranini oku") -> {
                    val text = PhoneControlService.run("read")
                    if (text.contains("okunabilen metin bulunamadı") || text.contains("Ekran okunamadı")) {
                        val visual = PhoneControlService.run("vision")
                        "Erişilebilir metin bulunamadı. Görsel okuma sonucu:\n${visual.take(1100)}"
                    } else "Ekranda görünenler:\n${text.take(1400)}"
                }
                else -> {
                    val target = quotedTarget(raw)
                        ?: return "Hangi metne dokunmamı istediğini tırnak içinde söyle. Örnek: Eda, ekrandaki metne dokun, "Ayarlar"."
                    PhoneControlService.run("tap", target)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("EDA-PhoneTask", "Voice phone action failed", e)
            "Telefon işlemi tamamlanamadı. Ekran kontrol iznini ve ekrandaki metni kontrol et."
        }
    }

    private fun quotedTarget(raw: String): String? {
        val match = Regex("\\\"([^\\\"]{1,80})\\\"|«([^»]{1,80})»|“([^”]{1,80})”").find(raw)
        return match?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.trim()
    }
}
