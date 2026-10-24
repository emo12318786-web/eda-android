package com.deniz.eda.phone

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Opt-in read-only screen understanding for EDA. Visible labels are read locally.
 * When the accessibility tree has no text, bundled ML Kit OCR processes a screenshot
 * on-device. Images and extracted text are not uploaded or saved to disk.
 */
class ScreenReaderService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    override fun onServiceConnected() { instance = this; super.onServiceConnected() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    fun readScreen(): String {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return "Ekran okuma ana arayüz iş parçacığında çalıştırılamaz; arka planda yeniden dene."
        }
        val text = readAccessibleTree()
        if (text.isNotBlank()) return "Ekranda görünen metin (erişilebilirlik):\n$text"
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return "Bu ekran erişilebilir metin sunmuyor. OCR için işlemi arka planda başlat."
        }
        val ocr = readOcr()
        return if (ocr.isBlank()) {
            "Ekran metni okunamadı. Bu ekran korumalı olabilir veya Android görüntü yakalamayı engellemiş olabilir."
        } else "Ekranda görünen metin (cihaz içi OCR):\n$ocr"
    }

    private fun readAccessibleTree(): String {
        val latch = CountDownLatch(1)
        val result = AtomicReference("")
        handler.post {
            try {
                val root = rootInActiveWindow
                if (root != null) {
                    val lines = LinkedHashSet<String>()
                    fun walk(node: AccessibilityNodeInfo?, depth: Int) {
                        if (node == null || depth > 25 || lines.sumOf { it.length } > 5000) return
                        if (node.isVisibleToUser && !node.isPassword) {
                            val label = (node.text?.toString() ?: node.contentDescription?.toString())
                                ?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
                            if (label.isNotBlank()) lines.add(label)
                        }
                        for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
                    }
                    walk(root, 0)
                    result.set(lines.joinToString("\n").take(6000))
                }
            } catch (_: Exception) { result.set("") }
            finally { latch.countDown() }
        }
        if (!latch.await(3, TimeUnit.SECONDS)) return ""
        return result.get()
    }

    private fun readOcr(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Looper.myLooper() == Looper.getMainLooper()) return ""
        val bitmap = captureScreenshot() ?: return ""
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val latch = CountDownLatch(1)
        val output = AtomicReference("")
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    output.set(result.text.take(6000))
                    latch.countDown()
                }
                .addOnFailureListener { latch.countDown() }
            latch.await(5, TimeUnit.SECONDS)
        } catch (_: Exception) {
            output.set("")
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
        return output.get()
    }

    private fun captureScreenshot(): Bitmap? {
        val output = AtomicReference<Bitmap?>()
        val latch = CountDownLatch(1)
        handler.post {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                latch.countDown()
                return@post
            }
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    try {
                        val buffer = screenshot.hardwareBuffer
                        val wrapped = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                        output.set(wrapped?.copy(Bitmap.Config.ARGB_8888, false))
                        wrapped?.recycle()
                        buffer.close()
                    } catch (_: Exception) {
                        output.set(null)
                    } finally { latch.countDown() }
                }
                override fun onFailure(errorCode: Int) { latch.countDown() }
            })
        }
        if (!latch.await(5, TimeUnit.SECONDS)) return null
        return output.get()
    }

    companion object {
        @Volatile private var instance: ScreenReaderService? = null
        fun isEnabled(): Boolean = instance != null
        fun readVisibleScreen(): String {
            val service = instance ?: return "Önce Android Erişilebilirlik ayarlarından EDA ekran okuyucusunu etkinleştir."
            return try { service.readScreen() } catch (e: Exception) { "Ekran okuma hatası: ${e.message ?: "bilinmeyen hata"}" }
        }
    }
}