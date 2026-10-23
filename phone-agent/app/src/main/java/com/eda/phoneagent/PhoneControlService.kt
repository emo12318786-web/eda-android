package com.eda.phoneagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * User-enabled local screen control. Only exposes a small allowlist of UI actions;
 * it does not bypass lock screens or protected system surfaces.
 */
class PhoneControlService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* screen state is read on demand */ }
    override fun onInterrupt() = Unit

    override fun onServiceConnected() { instance = this }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    private fun <T> onMain(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: T? = null
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            try { result = block() } finally { latch.countDown() }
        }
        if (!latch.await(4, TimeUnit.SECONDS)) return null
        return result
    }

    private fun walk(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>, depth: Int = 0) {
        if (node == null || depth > 20 || out.size > 350) return
        out.add(node)
        for (i in 0 until node.childCount) walk(node.getChild(i), out, depth + 1)
    }

    private fun visibleNodes(): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        walk(rootInActiveWindow, out)
        return out
    }

    fun screenText(): String? = onMain {
        val lines = visibleNodes().mapNotNull { n ->
            val text = (n.text ?: n.contentDescription)?.toString()?.trim().orEmpty()
            if (text.isBlank() || !n.isVisibleToUser) null else text.take(220)
        }.distinct().take(100)
        if (lines.isEmpty()) "متن قابل‌خواندن روی صفحه پیدا نشد؛ شاید این صفحه از دسترس‌پذیری پشتیبانی نکند."
        else lines.joinToString("\n").take(7000)
    }

    fun tapText(target: String): String? = onMain {
        val query = target.trim()
        if (query.isBlank()) return@onMain "متن هدف خالی است."
        val sensitive = listOf("send", "submit", "delete", "remove", "pay", "purchase", "buy", "confirm", "transfer", "ارسال", "فرستادن", "حذف", "پرداخت", "خرید", "انتقال", "تأیید", "تایید", "ثبت نهایی")
        if (sensitive.any { query.contains(it, ignoreCase = true) }) {
            return@onMain "برای ایمنی، EDA این اقدام حساس را انجام نمی‌دهد. خودت دکمه را بررسی و لمس کن."
        }
        val nodes = visibleNodes()
        val node = nodes.firstOrNull {
            it.isVisibleToUser && ((it.text?.toString()?.contains(query, true) == true) ||
                (it.contentDescription?.toString()?.contains(query, true) == true))
        } ?: return@onMain "عنصر «$query» روی صفحه پیدا نشد؛ صفحه را بخوان و عبارت دقیق‌تری بده."
        var candidate: AccessibilityNodeInfo? = node
        while (candidate != null && !candidate.isClickable) candidate = candidate.parent
        val clicked = (candidate ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (clicked) "روی «$query» لمس شد." else "عنصر پیدا شد اما اندروید اجازهٔ لمس آن را نداد."
    }

    fun typeText(value: String): String? = onMain {
        val text = value.take(2000)
        if (text.isBlank()) return@onMain "متن خالی است."
        val node = visibleNodes().firstOrNull { it.isFocused && it.isEditable }
            ?: return@onMain "کادر متنی فعال نیست. اول کادر موردنظر را انتخاب کن."
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) "متن وارد شد."
        else "ورود متن از طریق دسترس‌پذیری ممکن نشد."
    }

    fun scroll(direction: String): String? = onMain {
        val nodes = visibleNodes()
        val scrollable = nodes.firstOrNull { it.isVisibleToUser && it.isScrollable }
            ?: return@onMain "ناحیهٔ قابل‌اسکرول پیدا نشد."
        val action = if (direction.equals("up", true)) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        if (scrollable.performAction(action)) "صفحه اسکرول شد."
        else "اسکرول انجام نشد."
    }

    fun globalAction(action: String): String? = onMain {
        val id = when (action) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return@onMain "این حرکت مجاز نیست."
        }
        if (performGlobalAction(id)) "انجام شد: $action" else "اندروید این حرکت را نپذیرفت."
    }

    companion object {
        @Volatile private var instance: PhoneControlService? = null
        fun isEnabled(): Boolean = instance != null
        fun runTool(name: String, arg: String = ""): String {
            val service = instance ?: return "کنترل صفحه فعال نیست. کاربر باید Eda Phone Agent را در تنظیمات دسترس‌پذیری فعال کند."
            return when (name) {
                "screen_text" -> service.screenText() ?: "خواندن صفحه زمان‌بر شد."
                "tap_text" -> service.tapText(arg) ?: "لمس زمان‌بر شد."
                "type_text" -> service.typeText(arg) ?: "ورود متن زمان‌بر شد."
                "scroll_up", "scroll_down" -> service.scroll(if (name == "scroll_up") "up" else "down") ?: "اسکرول زمان‌بر شد."
                "back", "home", "notifications" -> service.globalAction(name) ?: "حرکت زمان‌بر شد."
                else -> "این ابزار صفحه مجاز نیست."
            }
        }
    }
}
