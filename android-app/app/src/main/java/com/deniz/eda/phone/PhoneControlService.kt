package com.deniz.eda.phone

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * User-enabled screen assistance for EDA.
 * Prefer accessibility nodes; on Android 11+ use local on-device OCR for text missing from the tree.
 * This service never taps consequential buttons or types into password fields.
 */
class PhoneControlService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onServiceConnected() { instance = this }
    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun <T> main(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var value: T? = null
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            try { value = block() } finally { latch.countDown() }
        }
        if (!latch.await(4, TimeUnit.SECONDS)) return null
        return value
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 20 || out.size >= 350) return
            out.add(node)
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        walk(rootInActiveWindow, 0)
        return out
    }

    fun read(): String = main {
        val lines = nodes().mapNotNull { node ->
            val text = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
            if (text.isBlank() || !node.isVisibleToUser) null else text.take(220)
        }.distinct().take(100)
        lines.joinToString("\n").take(7000).ifBlank {
            "Bu ekranda erişilebilirlik üzerinden okunabilen metin bulunamadı. Görsel OCR aracını deneyebilirsin."
        }
    } ?: "Ekran okunamadı."

    private data class VisualLine(val text: String, val centerX: Int, val centerY: Int)

    /** Screenshot capture is provided by Android AccessibilityService API 30+, no MediaProjection dialog needed. */
    private fun captureScreenshot(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val bitmapRef = AtomicReference<Bitmap?>(null)
        val latch = CountDownLatch(1)
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, DIRECT_EXECUTOR, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    try {
                        val wrapped = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                        bitmapRef.set(wrapped?.copy(Bitmap.Config.ARGB_8888, false))
                    } catch (_: Exception) {
                        bitmapRef.set(null)
                    } finally {
                        buffer.close()
                        latch.countDown()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    latch.countDown()
                }
            })
        } catch (_: Exception) {
            return null
        }
        if (!latch.await(5, TimeUnit.SECONDS)) return null
        return bitmapRef.get()
    }

    /** Bundled Latin OCR works offline and covers EDA's Turkish/Azerbaijani Latin UI. */
    private fun recognizeVisualLines(): List<VisualLine>? {
        val bitmap = captureScreenshot() ?: return null
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val latch = CountDownLatch(1)
        val linesRef = AtomicReference<List<VisualLine>>(emptyList())
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener(DIRECT_EXECUTOR) { result ->
                    try {
                        linesRef.set(result.textBlocks.flatMap { block ->
                            block.lines.mapNotNull { line ->
                                val rect = line.boundingBox ?: return@mapNotNull null
                                val text = line.text.trim()
                                if (text.isBlank()) null else VisualLine(text, rect.centerX(), rect.centerY())
                            }
                        })
                    } finally {
                        latch.countDown()
                    }
                }
                .addOnFailureListener(DIRECT_EXECUTOR) {
                    latch.countDown()
                }
            if (!latch.await(10, TimeUnit.SECONDS)) return null
            return linesRef.get()
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    fun readVisual(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return "Görsel OCR için Android 11 veya üzeri gerekir; erişilebilir metin okuma bu cihazda kullanılabilir."
        }
        val lines = recognizeVisualLines()
            ?: return "Ekran görüntüsü alınamadı. Uygulama ekran görüntüsünü engelliyor olabilir veya Android kısa aralıkla çekime izin vermemiş olabilir."
        if (lines.isEmpty()) return "Görüntüde okunabilir metin bulunamadı."
        return lines.joinToString("\n") { "${it.text} [${it.centerX},${it.centerY}]" }.take(7000)
    }

    private fun blockedTarget(target: String): Boolean =
        PhoneActionSafety.isDangerousTarget(target)

    private fun visibleScreenText(nodes: List<AccessibilityNodeInfo>): String =
        nodes.asSequence()
            .filter { it.isVisibleToUser }
            .mapNotNull { node ->
                listOfNotNull(
                    node.text?.toString(),
                    node.contentDescription?.toString(),
                    node.hintText?.toString()
                ).joinToString(" ").takeIf { it.isNotBlank() }
            }
            .distinct()
            .joinToString("\n")

    fun tap(target: String): String? = main {
        val query = target.trim()
        if (query.isBlank()) return@main "Dokunulacak metin boş."
        if (blockedTarget(query)) {
            return@main "Güvenlik için gönderme, silme, satın alma, ödeme, transfer veya nihai onay düğmesine dokunulmadı."
        }
        val screenNodes = nodes()
        if (PhoneActionSafety.isSensitiveScreen(visibleScreenText(screenNodes))) {
            return@main "Güvenlik nedeniyle banka, ödeme veya finansal işlem ekranında otomatik dokunma kapalı. Bu adımı kendin kontrol et."
        }
        val node = screenNodes.firstOrNull {
            it.isVisibleToUser &&
                (it.text?.toString()?.contains(query, true) == true ||
                    it.contentDescription?.toString()?.contains(query, true) == true)
        } ?: return@main "Erişilebilirlik ağacında «$query» bulunamadı. Ekran OCR'siyle görsel dokunmayı dene."
        var clickable: AccessibilityNodeInfo? = node
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        if ((clickable ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)) "«$query» öğesine dokunuldu."
        else "Android dokunma işlemine izin vermedi."
    }

    /** OCR-based text tap supports labels missing from the accessibility tree. */
    fun tapVisual(target: String): String {
        val query = target.trim()
        if (query.isBlank()) return "Görsel olarak bulunacak metni belirt."
        if (blockedTarget(query)) {
            return "Güvenlik için gönderme, silme, satın alma, ödeme, transfer veya nihai onay düğmesine dokunulmadı."
        }
        val visualLines = recognizeVisualLines()
            ?: return "Görsel OCR başarısız oldu. Ekran korumalı olabilir veya Android ekran görüntüsüne izin vermemiş olabilir."
        if (PhoneActionSafety.isSensitiveScreen(visualLines.joinToString(" ") { it.text })) {
            return "Güvenlik nedeniyle banka, ödeme veya finansal işlem ekranında OCR ile otomatik dokunma kapalı."
        }
        val match = visualLines.firstOrNull { it.text.contains(query, ignoreCase = true) }
            ?: return "Görsel OCR'de «$query» bulunamadı. Ekran korumalı olabilir veya metin net olmayabilir."
        val accepted = main {
            val path = Path().apply { moveTo(match.centerX.toFloat(), match.centerY.toFloat()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 70))
                .build()
            dispatchGesture(gesture, null, Handler(Looper.getMainLooper()))
        } ?: false
        return if (accepted) {
            "«${match.text}» için görsel dokunma gönderildi (${match.centerX}, ${match.centerY}). Sonucu ekranı yeniden okuyarak doğrula."
        } else "Android görsel dokunmayı kabul etmedi."
    }

    fun type(value: String): String? = main {
        val screenNodes = nodes()
        val node = screenNodes.firstOrNull { it.isVisibleToUser && it.isFocused && it.isEditable }
            ?: return@main "Önce metin alanını kendin seç."
        val fieldLabels = StringBuilder()
        var fieldNode: AccessibilityNodeInfo? = node
        var ancestorDepth = 0
        while (fieldNode != null && ancestorDepth < 4) {
            fieldLabels.append(' ')
                .append(fieldNode.text?.toString().orEmpty()).append(' ')
                .append(fieldNode.contentDescription?.toString().orEmpty()).append(' ')
                .append(fieldNode.hintText?.toString().orEmpty()).append(' ')
                .append(fieldNode.viewIdResourceName.orEmpty())
            fieldNode = fieldNode.parent
            ancestorDepth++
        }
        if (node.isPassword || PhoneActionSafety.isSensitiveField(fieldLabels.toString()) ||
            PhoneActionSafety.isSensitiveScreen(visibleScreenText(screenNodes))) {
            return@main "Güvenlik için EDA parola, OTP, doğrulama kodu, kart veya finansal bilgi alanına metin yazmaz."
        }
        val safeValue = value.take(2000)
        if (safeValue.isBlank()) return@main "Yazılacak metin boş."
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, safeValue)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            "Metin yazıldı; hiçbir gönderme veya onay düğmesine basılmadı."
        } else "Metin yazılamadı."
    }

    fun scroll(up: Boolean): String? = main {
        val node = nodes().firstOrNull { it.isVisibleToUser && it.isScrollable }
            ?: return@main "Kaydırılabilir alan bulunamadı."
        if (node.performAction(if (up) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            "Ekran kaydırıldı."
        } else "Kaydırma başarısız oldu."
    }

    fun global(action: String): String? = main {
        val id = when (action) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "power_dialog" -> GLOBAL_ACTION_POWER_DIALOG
            "lock_screen" -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@main "Ekranı kilitleme işlemi Android 9 veya üzeri gerektirir."
                GLOBAL_ACTION_LOCK_SCREEN
            }
            "screenshot" -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@main "Ekran görüntüsü işlemi Android 9 veya üzeri gerektirir."
                GLOBAL_ACTION_TAKE_SCREENSHOT
            }
            else -> return@main "Bu gezinme işlemi desteklenmiyor."
        }
        if (performGlobalAction(id)) "İşlem istendi: $action. Sonucu ekranı yeniden okuyarak doğrula." else "Android bu sistem işlemini kabul etmedi."
    }

    companion object {
        private val DIRECT_EXECUTOR = Executor { command -> command.run() }
        @Volatile private var instance: PhoneControlService? = null

        fun isEnabled() = instance != null

        fun run(action: String, arg: String = ""): String {
            val service = instance
                ?: return "Ekran kontrolü etkin değil. Android Erişilebilirlik ayarlarından EDA hizmetini etkinleştir."
            return when (action) {
                "read" -> service.read() ?: "Ekran okunamadı."
                "vision" -> service.readVisual()
                "tap" -> service.tap(arg) ?: "Dokunma zaman aşımına uğradı."
                "tap_visual" -> service.tapVisual(arg)
                "type" -> service.type(arg) ?: "Metin yazma zaman aşımına uğradı."
                "up" -> service.scroll(true) ?: "Kaydırma zaman aşımına uğradı."
                "down" -> service.scroll(false) ?: "Kaydırma zaman aşımına uğradı."
                "back", "home", "recents", "notifications", "quick_settings", "power_dialog", "lock_screen", "screenshot" -> service.global(action) ?: "Sistem işlemi zaman aşımına uğradı."
                else -> "Bu ekran aracı desteklenmiyor."
            }
        }
    }
}
