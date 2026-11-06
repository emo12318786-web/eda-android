package com.deniz.eda.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Always-offline-after-first-download wake listener.
 * Downloads the small English Vosk model once; after that recognition itself is offline.
 * This is a fallback model: English acoustic/language data may not reliably recognize Turkish "Eda".
 */
class OfflineWakeWordListener(
    context: Context,
    private val wakeWord: String = "eda",
    private val onWake: () -> Unit,
    private val onStatus: (String) -> Unit = {}
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    @Volatile private var model: Model? = null
    @Volatile private var worker: Thread? = null
    @Volatile private var loading = false

    val isListening: Boolean get() = running.get()
    val isReady: Boolean get() = model != null

    fun prepare(onReady: () -> Unit, onError: (String) -> Unit) {
        if (model != null) { main.post(onReady); return }
        synchronized(this) {
            if (loading) return
            loading = true
        }
        Thread({
            try {
                val root = File(app.filesDir, "vosk-model-small-en-us-0.15")
                val marker = File(root, ".ready")
                if (!marker.exists()) {
                    root.deleteRecursively()
                    root.mkdirs()
                    main.post { onStatus("دانلود یک‌بارهٔ مدل تشخیص بیدارباش…") }
                    val archive = File(app.cacheDir, "vosk-model.zip")
                    val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20000; readTimeout = 60000; instanceFollowRedirects = true
                    }
                    try {
                        connection.connect()
                        if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                        connection.inputStream.use { input -> FileOutputStream(archive).use { input.copyTo(it) } }
                    } finally { connection.disconnect() }
                    unzipSafely(archive, root)
                    archive.delete()
                    val nested = root.listFiles()?.singleOrNull { it.isDirectory && it.name.startsWith("vosk-model") }
                    if (nested != null) {
                        nested.listFiles()?.forEach { child -> child.renameTo(File(root, child.name)) }
                        nested.deleteRecursively()
                    }
                    check(File(root, "am").exists() && File(root, "conf").exists() && File(root, "graph").exists()) {
                        "ساختار مدل Vosk ناقص است"
                    }
                    marker.writeText("ok")
                }
                model = Model(root.absolutePath)
                main.post { onStatus("مدل بیدارباش آماده است"); onReady() }
            } catch (e: Throwable) {
                Log.e(TAG, "Wake model prepare failed", e)
                main.post { onError(e.message ?: "خطای آماده‌سازی مدل") }
            } finally { loading = false }
        }, "eda-vosk-model-loader").start()
    }

    fun start(): Boolean {
        val m = model ?: return false
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            main.post { onStatus("مجوز میکروفون برای بیدارباش لازم است") }
            return false
        }
        if (!running.compareAndSet(false, true)) return true
        worker = Thread({ listen(m) }, "eda-vosk-wake").also { it.start() }
        return true
    }

    fun stop() {
        running.set(false)
        val t = worker
        if (t != null && t !== Thread.currentThread()) try { t.join(1800) } catch (_: InterruptedException) {}
    }

    fun release() {
        stop()
        try { model?.close() } catch (_: Throwable) {}
        model = null
    }

    private fun listen(m: Model) {
        var audio: AudioRecord? = null
        var recognizer: Recognizer? = null
        var hit = false
        try {
            val rate = 16000
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(min > 0) { "AudioRecord buffer unavailable: $min" }
            audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate))
            check(audio.state == AudioRecord.STATE_INITIALIZED) { "میکروفون در دسترس نیست" }
            recognizer = Recognizer(m, rate.toFloat(), "[\"eda\", \"[unk]\"]")
            audio.startRecording()
            main.post { onStatus("در انتظار واژهٔ بیدارباش «اِدا»") }
            val buffer = ShortArray(rate / 10)
            while (running.get()) {
                val n = audio.read(buffer, 0, buffer.size)
                if (n <= 0) { if (n < 0) break else continue }
                val result = if (recognizer.acceptWaveForm(buffer, n)) recognizer.result else recognizer.partialResult
                val obj = JSONObject(result)
                val text = obj.optString("text", obj.optString("partial", "")).lowercase()
                if (text.split(Regex("\\s+")).any { it == wakeWord }) { hit = true; break }
            }
        } catch (e: Throwable) {
            if (running.get()) Log.e(TAG, "Wake listening failed", e)
        } finally {
            try { audio?.stop() } catch (_: Throwable) {}
            try { audio?.release() } catch (_: Throwable) {}
            try { recognizer?.close() } catch (_: Throwable) {}
            running.set(false)
            worker = null
            if (hit) main.post { onWake() }
        }
    }

    private fun unzipSafely(zip: File, destination: File) {
        val base = destination.canonicalPath + File.separator
        ZipInputStream(BufferedInputStream(zip.inputStream())).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val out = File(destination, entry.name)
                require(out.canonicalPath.startsWith(base)) { "Unsafe model archive entry" }
                if (entry.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { stream -> zis.copyTo(stream) }
                }
                zis.closeEntry()
            }
        }
    }

    companion object {
        private const val TAG = "EdaOfflineWake"
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    }
}
