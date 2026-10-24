package com.deniz.eda.phone

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Local, user-enabled accessibility control; does not bypass lock/auth screens. */
class PhoneControlService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onServiceConnected() { instance = this }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    private fun <T> main(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var value: T? = null
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { try { value = block() } finally { latch.countDown() } }
        if (!latch.await(4, TimeUnit.SECONDS)) return null
        return value
    }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 20 || result.size >= 350) return
            result.add(n)
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(rootInActiveWindow, 0)
        return result
    }
    fun read(): String? = main {
        val lines = nodes().mapNotNull { n ->
            val s = (n.text ?: n.contentDescription)?.toString()?.trim().orEmpty()
            if (s.isBlank() || !n.isVisibleToUser) null else s.take(220)
        }.distinct().take(100)
        lines.joinToString("\n").take(7000).ifBlank { "روی این صفحه متن قابل‌خواندن پیدا نشد." }
    }
    fun tap(target: String): String? = main {
        val q = target.trim()
        if (q.isBlank()) return@main "متن هدف خالی است."
        val blocked = listOf("send","submit","delete","remove","pay","purchase","buy","confirm","transfer","ارسال","فرستادن","حذف","پرداخت","خرید","انتقال","تأیید","تایید","ثبت نهایی")
        if (blocked.any { q.contains(it, true) }) return@main "اقدام حساس مسدود است؛ خودت دکمه را بررسی کن."
        val n = nodes().firstOrNull { it.isVisibleToUser &&
            (it.text?.toString()?.contains(q, true) == true || it.contentDescription?.toString()?.contains(q, true) == true) }
            ?: return@main "عنصر «$q» پیدا نشد."
        var c: AccessibilityNodeInfo? = n
        while (c != null && !c.isClickable) c = c.parent
        if ((c ?: n).performAction(AccessibilityNodeInfo.ACTION_CLICK)) "روی «$q» لمس شد." else "اندروید لمس را نپذیرفت."
    }
    fun type(value: String): String? = main {
        val n = nodes().firstOrNull { it.isVisibleToUser && it.isFocused && it.isEditable }
            ?: return@main "کادر متنی فعال نیست؛ اول خودت آن را انتخاب کن."
        val args = android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value.take(2000)) }
        if (n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) "متن وارد شد." else "ورود متن انجام نشد."
    }
    fun scroll(up: Boolean): String? = main {
        val n = nodes().firstOrNull { it.isVisibleToUser && it.isScrollable } ?: return@main "ناحیهٔ اسکرول پیدا نشد."
        val a = if (up) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        if (n.performAction(a)) "صفحه اسکرول شد." else "اسکرول انجام نشد."
    }
    fun global(action: String): String? = main {
        val id = when(action) { "back" -> GLOBAL_ACTION_BACK; "home" -> GLOBAL_ACTION_HOME; "notifications" -> GLOBAL_ACTION_NOTIFICATIONS; else -> return@main "حرکت مجاز نیست." }
        if (performGlobalAction(id)) "انجام شد: $action" else "اندروید حرکت را نپذیرفت."
    }
    companion object {
        @Volatile private var instance: PhoneControlService? = null
        fun isEnabled() = instance != null
        fun run(action: String, arg: String = ""): String {
            val s = instance ?: return "کنترل صفحه فعال نیست؛ سرویس EDA را در تنظیمات دسترس‌پذیری روشن کن."
            return when(action) {
                "read" -> s.read() ?: "خواندن صفحه زمان‌بر شد."
                "tap" -> s.tap(arg) ?: "لمس زمان‌بر شد."
                "type" -> s.type(arg) ?: "تایپ زمان‌بر شد."
                "up" -> s.scroll(true) ?: "اسکرول زمان‌بر شد."
                "down" -> s.scroll(false) ?: "اسکرول زمان‌بر شد."
                "back","home","notifications" -> s.global(action) ?: "حرکت زمان‌بر شد."
                else -> "ابزار مجاز نیست."
            }
        }
    }
}
