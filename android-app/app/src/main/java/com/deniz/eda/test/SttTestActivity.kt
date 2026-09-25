package com.deniz.eda.test

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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

/**
 * تست STT (Speech-to-Text) در APK خودمون
 * هدف: چک کنیم SpeechRecognizer اندروید کار می‌کنه یا نه
 * (چون Termux:API روی A71 اندروید 13 جواب نداد)
 */
class SttTestActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "STT_TEST"
        private const val REQ_PERM = 9001
    }

    private var recognizer: SpeechRecognizer? = null
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
            text = "🎤 STT Test — Eda"
            textSize = 20f
            setPadding(0, 0, 0, 30)
        }

        statusView = TextView(this).apply {
            text = "Durum: hazır"
            textSize = 16f
            setPadding(0, 0, 0, 20)
        }

        val startBtn = Button(this).apply {
            text = "▶️ Başlat (5 sn dinle)"
            setOnClickListener { startListening() }
        }

        val stopBtn = Button(this).apply {
            text = "⏹ Durdur"
            setOnClickListener { stopListening() }
        }

        val clearBtn = Button(this).apply {
            text = "🗑 Logu temizle"
            setOnClickListener {
                logBuf.clear()
                logView.text = ""
            }
        }

        logView = TextView(this).apply {
            textSize = 12f
            setPadding(0, 30, 0, 0)
        }

        val scroll = ScrollView(this).apply {
            addView(logView)
        }

        root.addView(title)
        root.addView(statusView)
        root.addView(startBtn)
        root.addView(stopBtn)
        root.addView(clearBtn)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)

        log("=== SttTestActivity başladı ===")
        log("SpeechRecognizer.isRecognitionAvailable() = ${SpeechRecognizer.isRecognitionAvailable(this)}")

        checkPermissions()
    }

    private fun checkPermissions() {
        val perm = Manifest.permission.RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            log("⚠️ RECORD_AUDIO izni yok, isteniyor...")
            ActivityCompat.requestPermissions(this, arrayOf(perm), REQ_PERM)
        } else {
            log("✅ RECORD_AUDIO izni var")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERM) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                log("✅ Kullanıcı izin verdi")
            } else {
                log("❌ Kullanıcı izin vermedi")
            }
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

    private fun startListening() {
        log("\n--- Yeni dinleme başlıyor ---")
        setStatus("hazırlanıyor...")

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            log("❌ SpeechRecognizer mevcut değil! Cihazda Google/STT servisi yok?")
            setStatus("hata: STT yok")
            return
        }

        // Önceki varsa yok et
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                log("🎧 onReadyForSpeech — konuş!")
                setStatus("dinliyor... konuş")
            }
            override fun onBeginningOfSpeech() { log("🗣 onBeginningOfSpeech") }
            override fun onRmsChanged(rmsdB: Float) { /* çok log basmasın */ }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                log("⏸ onEndOfSpeech")
                setStatus("işleniyor...")
            }
            override fun onError(error: Int) {
                val errName = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
                    SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
                    SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
                    SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
                    SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
                    else -> "ERROR_UNKNOWN($error)"
                }
                log("❌ onError: $errName")
                setStatus("hata: $errName")
            }
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val conf = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                log("✅ onResults:")
                if (list.isNullOrEmpty()) {
                    log("   (boş liste)")
                } else {
                    list.forEachIndexed { i, t ->
                        val c = conf?.getOrNull(i) ?: -1f
                        log("   [$i] \"$t\"  (conf=${"%.2f".format(c)})")
                    }
                }
                setStatus("tamam: ${list?.firstOrNull() ?: "boş"}")
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!list.isNullOrEmpty()) log("… partial: ${list.first()}")
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }

        try {
            recognizer?.startListening(intent)
            log("▶️ startListening çağrıldı")
        } catch (e: Exception) {
            log("❌ startListening hatası: ${e.message}")
        }
    }

    private fun stopListening() {
        try {
            recognizer?.stopListening()
            log("⏹ stopListening çağrıldı")
        } catch (e: Exception) {
            log("stop hatası: ${e.message}")
        }
        setStatus("durduruldu")
    }

    override fun onDestroy() {
        super.onDestroy()
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        log("onDestroy")
    }
}
