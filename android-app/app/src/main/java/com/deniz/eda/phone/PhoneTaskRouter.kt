package com.deniz.eda.phone

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

    private fun hasTapVerb(normalized: String): Boolean =
        Regex("""\b(dokun|toxun|tikla|bas|sec)\b""").containsMatchIn(normalized)

    // Screen capture/OCR can wait for Android callbacks; keep that work off the voice/UI thread.
    private suspend fun runScreenAction(action: String, argument: String = ""): String =
        withContext(Dispatchers.IO) { PhoneControlService.run(action, argument) }

    /** Pure intent matcher so voice aliases can be regression-tested without a device. */
    internal fun isPhoneIntent(spoken: String): Boolean {
        val raw = spoken.trim()
        val q = norm(raw)
        return listOf(
            "ekrani oku", "ekranda ne yaziyor", "ekranda ne yazilir", "ekranda ne gorunur",
            "ekrani oxu", "ekraninda ne var", "telefon ekranini oxu",
            "asagi kaydir", "asagi in", "asagi surusdur", "asagi surukle",
            "yukari kaydir", "yukari cik", "yukari surusdur", "yukari qalx",
            "yuxari surusdur", "yuxari qalx",
            "bildirimleri ac", "bildirisleri ac", "bildirim panelini ac",
            "hizli ayarlari ac", "tez ayarlari ac",
            "geri git", "geri don", "geri qayit", "ana ekrana don", "ana sehifeye qayit",
            "son uygulamalari ac", "son proqramlari ac",
            "ekrandaki metni oku", "telefon ekranini oku",
            "ekrandaki metne dokun", "ekrandaki metne toxun", "ekrandaki ogeye dokun",
            "ekrandaki dugmeye bas", "ekrandaki yaziyi sec"
        ).any { q.contains(it) } ||
            (quotedTarget(raw) != null && hasTapVerb(q))
    }

    suspend fun tryHandle(context: Context, spoken: String): String? {
        val raw = spoken.trim()
        val q = norm(raw)
        if (!isPhoneIntent(raw)) return null

        if (!PhoneControlService.isEnabled()) {
            return "Telefon ekranına erişmek için EDA'nın Erişilebilirlik iznini bir kez açmalısın. Sonra bu komutları doğrudan Eda'ya söyleyebilirsin; ayrı kontrol sayfasını açman gerekmez."
        }

        val tapTarget = quotedTarget(raw)
        val tapVerb = hasTapVerb(q)

        return try {
            when {
                // A quoted tap target outranks navigation keyword matches inside the target label.
                tapVerb -> {
                    val target = tapTarget
                        ?: return "Hangi metne dokunmamı istediğini tırnak içinde söyle. Örnek: Eda, ekrandaki metne dokun: «Ayarlar»."
                    tapAndVerify(target)
                }
                q.contains("bildirim") || q.contains("bildiris") -> runScreenAction("notifications")
                q.contains("hizli ayar") || q.contains("tez ayar") -> runScreenAction("quick_settings")
                q.contains("ana ekrana") || q.contains("ana sehif") -> runScreenAction("home")
                q.contains("son uygulama") || q.contains("son proqram") -> runScreenAction("recents")
                q.contains("geri git") || q.contains("geri don") || q.contains("geri qayit") -> runScreenAction("back")
                q.contains("asagi kaydir") || q.contains("asagi in") || q.contains("asagi surusdur") || q.contains("asagi surukle") || q.contains("asagi cek") -> {
                    val action = runScreenAction("down")
                    delay(350)
                    if (q.contains("oku") || q.contains("oxu") || q.contains("ne yaziyor") || q.contains("ne yazilir") || q.contains("ne gorunur")) {
                        val screenText = runScreenAction("read")
                        "Kaydırma sonucu: $action\nEkranda görünenler:\n${screenText.take(1100)}"
                    } else action
                }
                q.contains("yukari kaydir") || q.contains("yukari cik") || q.contains("yukari surusdur") || q.contains("yukari qalx") || q.contains("yuxari surusdur") || q.contains("yuxari qalx") || q.contains("yuxari cek") -> runScreenAction("up")
                q.contains("ekrani oku") || q.contains("ekranda ne yaziyor") || q.contains("ekranda ne yazilir") ||
                    q.contains("ekranda ne gorunur") || q.contains("ekrani oxu") || q.contains("ekraninda ne var") ||
                    q.contains("ekrana bax") || q.contains("telefon ekranini oku") || q.contains("telefon ekranini oxu") ||
                    q.contains("ekrandaki metni oku") || q.contains("ekrandaki yazilari oxu") -> {
                    val text = runScreenAction("read")
                    if (text.contains("okunabilen metin bulunamadı", ignoreCase = true) || text.contains("Ekran okunamadı", ignoreCase = true)) {
                        val visual = runScreenAction("vision")
                        "Erişilebilir metin bulunamadı. Görsel okuma sonucu:\n${visual.take(1100)}"
                    } else "Ekranda görünenler:\n${text.take(1400)}"
                }
                else -> "Telefon ekranında hangi işlemi istediğini net söyle. Örnek: ekranı oku, aşağı kaydır, bildirimleri aç veya «Ayarlar» metnine dokun."
            }
        } catch (e: Exception) {
            android.util.Log.e("EDA-PhoneTask", "Voice phone action failed", e)
            "Telefon işlemi tamamlanamadı. Ekran kontrol iznini ve ekrandaki metni kontrol et."
        }
    }

    private suspend fun tapAndVerify(target: String): String {
        // Match Phone Harness behavior: accessible-label tap first, OCR fallback second.
        val accessibleResult = runScreenAction("tap", target)
        val tapResult = if (accessibleResult.contains("Erişilebilirlik ağacında")) {
            runScreenAction("tap_visual", target)
        } else accessibleResult
        val tapWasSent = tapResult.contains("öğesine dokunuldu", ignoreCase = true) ||
            tapResult.contains("görsel dokunma gönderildi", ignoreCase = true)
        if (!tapWasSent) return tapResult

        delay(500)
        val updatedScreen = runScreenAction("read")
        return "Dokunma sonucu: $tapResult\nGüncel ekran metni:\n${updatedScreen.take(800)}"
    }

    private fun quotedTarget(raw: String): String? {
        val match = Regex("\\\"([^\\\"]{1,80})\\\"|«([^»]{1,80})»|“([^”]{1,80})”").find(raw)
        return match?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.trim()
    }
}
