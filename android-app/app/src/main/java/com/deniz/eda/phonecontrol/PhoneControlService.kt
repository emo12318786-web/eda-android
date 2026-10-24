package com.deniz.eda.phonecontrol

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** EDA screen control, enabled explicitly by the user in Android Accessibility settings. */
class PhoneControlService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onServiceConnected() { instance = this }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    private fun <T> onMain(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: T? = null
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { try { result = block() } finally { latch.countDown() } }
        if (!latch.await(4, TimeUnit.SECONDS)) return null
        return result
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 18 || out.size >= 300) return
            out.add(node)
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        walk(rootInActiveWindow, 0)
        return out
    }

    fun readScreen(): String = onMain {
        val lines = nodes().mapNotNull {
            val value = (it.text ?: it.contentDescription)?.toString()?.trim().orEmpty()
            if (value.isNotBlank() && it.isVisibleToUser) value.take(180) else null
        }.distinct().take(80)
        if (lines.isEmpty()) "Ekranda okunabilir metin bulunamadı. Bazı uygulamalar erişilebilirlik metnini paylaşmaz."
        else lines.joinToString("\n").take(6000)
    } ?: "Ekran okunamadı."

    fun tapText(target: String): String = onMain {
        val q = target.trim()
        if (q.isBlank()) return@onMain "Dokunulacak metni yaz."
        val risky = listOf("send", "submit", "delete", "remove", "pay", "purchase", "buy", "confirm", "transfer",
            "gönder", "sil", "öde", "satın al", "onayla", "havale", "ödeme", "ارسال", "حذف", "پرداخت", "خرید", "انتقال", "تأیید")
        if (risky.any { q.contains(it, ignoreCase = true) })
            return@onMain "Güvenlik için gönderme, silme, satın alma veya ödeme gibi hassas işlemleri EDA tamamlamaz. Son adımı kendin kontrol et."
        val node = nodes().firstOrNull { it.isVisibleToUser &&
            ((it.text?.toString()?.contains(q, true) == true) || (it.contentDescription?.toString()?.contains(q, true) == true)) }
            ?: return@onMain "Ekranda «$q» bulunamadı."
        var clickable: AccessibilityNodeInfo? = node
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        if ((clickable ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)) "«$q» öğesine dokunuldu."
        else "Öğe bulundu ama Android dokunma işlemine izin vermedi."
    } ?: "Dokunma işlemi zaman aşımına uğradı."

    fun typeText(value: String): String = onMain {
        val text = value.take(1500)
        if (text.isBlank()) return@onMain "Yazılacak metin boş."
        val field = nodes().firstOrNull { it.isVisibleToUser && it.isFocused && it.isEditable }
            ?: return@onMain "Önce uygulamadaki metin alanını kendin seç."
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) "Metin etkin alana yazıldı."
        else "Metin yazılamadı."
    } ?: "Yazma işlemi zaman aşımına uğradı."

    fun scrollPage(up: Boolean): String = onMain {
        val target = nodes().firstOrNull { it.isVisibleToUser && it.isScrollable }
            ?: return@onMain "Kaydırılabilir alan bulunamadı."
        val action = if (up) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        if (target.performAction(action)) "Ekran kaydırıldı." else "Kaydırma başarısız."
    } ?: "Kaydırma zaman aşımına uğradı."

    fun navigate(action: String): String = onMain {
        val global = when (action) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return@onMain "İzin verilmeyen işlem."
        }
        if (performGlobalAction(global)) "Tamamlandı: $action" else "Android işlemi kabul etmedi."
    } ?: "İşlem zaman aşımına uğradı."

    companion object {
        @Volatile private var instance: PhoneControlService? = null
        fun isEnabled() = instance != null
        fun runTool(name: String, arg: String = ""): String {
            val s = instance ?: return "Ekran kontrolü kapalı. Android Erişilebilirlik ayarlarından EDA ekran kontrolünü etkinleştir."
            return when (name) {
                "read" -> s.readScreen()
                "tap" -> s.tapText(arg)
                "type" -> s.typeText(arg)
                "scroll_up" -> s.scrollPage(true)
                "scroll_down" -> s.scrollPage(false)
                "back", "home", "notifications" -> s.navigate(name)
                else -> "Bu işlem desteklenmiyor."
            }
        }
    }
}
