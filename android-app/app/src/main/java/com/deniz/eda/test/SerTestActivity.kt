package com.deniz.eda.test

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.FileOutputStream
import kotlin.concurrent.thread

/**
 * تست همزمانی AudioRecord + SpeechRecognizer
 * هدف: ببینیم می‌تونیم همزمان ضبط کنیم (برای SER) و STT کنیم
 */
class SerTestActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SER_TEST"
        private const val REQ_PERM = 9002
        private const val SR = 16000
    }

    private var recognizer: SpeechRecognizer? = null
    private var audioRecord: AudioRecord? = null
    @Volatile private var isRecording = false
    private var sttResult = ""

    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private val logBuf = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        val title = TextView(this).apply {
            text = "🎙 SER Test — AudioRecord + STT"
            textSize = 20f
            setPadding(0, 0, 0, 20)
        }

        statusView = TextView(this).apply {
            text = "Durum: hazır"
            textSize = 16f
            setPadding(0, 0, 0, 20)
        }

        val startBtn = Button(this).apply {
            text = "▶️ Test başlat (5 sn)"
            setOnClickListener { runSimultaneousTest() }
        }

        val stopBtn = Button(this).apply {
            text = "⏹ Durdur"
            setOnClickListener { stopAll() }
        }

        val clearBtn = Button(this).apply {
            text = "🗑 Logu temizle"
            setOnClickListener {
                logBuf.clear()
                logView.text = ""
            }
        }

        logView = TextView(this).apply {
            textSize = 11f
            setPadding(0, 20, 0, 0)
        }

        val scroll = ScrollView(this).apply { addView(logView) }

        root.addView(title)
        root.addView(statusView)
        root.addView(startBtn)
        root.addView(stopBtn)
        root.addView(clearBtn)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)

        log("=== SerTestActivity başladı ===")
        log("AudioRecord + STT eşzamanlılık testi")

        checkPerms()
    }

    private fun checkPerms() {
        val perm = Manifest.permission.RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(perm), REQ_PERM)
        } else {
            log("✅ RECORD_AUDIO izni var")
        }
    }

    private fun log(msg: String) {
        Log.i(TAG, msg)
        logBuf.append(msg).append("\n")
        runOnUiThread { logView.text = logBuf.toString() }
    }

    private fun setStatus(s: String) {
        runOnUiThread { statusView.text = "Durum: $s" }
    }

    private fun runSimultaneousTest() {
        log("\n═══════════════════════════════════════")
        log("TEST: AudioRecord + SpeechRecognizer")
        log("═══════════════════════════════════════")
        sttResult = ""

        // ۱) AudioRecord شروع
        thread { startAudioRecord() }

        // ۲) نیم ثانیه بعد STT شروع
        android.os.Handler(mainLooper).postDelayed({
            runOnUiThread { startStt() }
        }, 500)

        // ۳) ۷ ثانیه بعد همه چیز رو ببند
        android.os.Handler(mainLooper).postDelayed({
            log("\n⏱ Süre doldu, test bitiyor")
            stopAll()
            log("\n═══════ SONUÇ ═══════")
            log("STT metni: \"$sttResult\"")
            if (sttResult.isNotEmpty()) {
                log("✅ EŞZAMANLI ÇALIŞIYOR — SER mümkün!")
            } else {
                log("❌ STT boş — çakışma var")
            }
        }, 7500)
    }

    private fun startAudioRecord() {
        try {
            val bufSize = AudioRecord.getMinBufferSize(
                SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            log("AudioRecord minBuf = $bufSize")

            if (bufSize <= 0) {
                log("❌ bufSize geçersiz")
                return
            }

            val ar = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                bufSize * 2
            )

            if (ar.state != AudioRecord.STATE_INITIALIZED) {
                log("❌ AudioRecord init olmadı")
                return
            }

            ar.startRecording()
            isRecording = true
            log("🎙 AudioRecord başladı (state=${ar.recordingState})")

            val file = java.io.File(cacheDir, "ser_test.pcm")
            val fos = FileOutputStream(file)
            val buf = ShortArray(bufSize)
            var total = 0

            val t0 = System.currentTimeMillis()
            while (isRecording && System.currentTimeMillis() - t0 < 7000) {
                val n = ar.read(buf, 0, buf.size)
                if (n > 0) {
                    total += n
                    val bytes = ByteArray(n * 2)
                    for (i in 0 until n) {
                        bytes[i * 2] = (buf[i].toInt() and 0xFF).toByte()
                        bytes[i * 2 + 1] = ((buf[i].toInt() shr 8) and 0xFF).toByte()
                    }
                    fos.write(bytes)
                }
            }

            fos.close()
            try { ar.stop() } catch (_: Exception) {}
            try { ar.release() } catch (_: Exception) {}
            audioRecord = null
            isRecording = false

            log("🎙 AudioRecord bitti: $total sample, ${file.length()} byte")
        } catch (e: Exception) {
            log("❌ AudioRecord hata: ${e.message}")
        }
    }

    private fun startStt() {
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)

            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { log("🎧 STT: ready") }
                override fun onBeginningOfSpeech() { log("🗣 STT: begin") }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() { log("⏸ STT: end") }
                override fun onError(e: Int) {
                    val name = when (e) {
                        SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
                        SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
                        SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
                        else -> "ERROR($e)"
                    }
                    log("❌ STT: $name")
                }
                override fun onResults(b: Bundle?) {
                    val list = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!list.isNullOrEmpty()) {
                        sttResult = list.first()
                        log("✅ STT sonuç: \"$sttResult\"")
                    } else {
                        log("⚠ STT boş sonuç")
                    }
                }
                override fun onPartialResults(b: Bundle?) {
                    val list = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!list.isNullOrEmpty()) log("… STT partial: ${list.first()}")
                }
                override fun onEvent(t: Int, b: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            recognizer?.startListening(intent)
            log("🎧 STT startListening çağrıldı")
            setStatus("dinliyor... konuş!")
        } catch (e: Exception) {
            log("❌ STT hata: ${e.message}")
        }
    }

    private fun stopAll() {
        isRecording = false
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        try { recognizer?.stopListening() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        setStatus("durdu")
        log("⏹ Durduruldu")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAll()
    }
}
