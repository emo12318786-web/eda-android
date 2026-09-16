package com.deniz.eda.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.whispercpp.whisper.WhisperContext
import com.whispercpp.whisper.TranscribeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Whisper STT - maksimum kalite icin optimize edildi.
 * - Model: ggml-medium-q5_0.bin
 * - 8 thread (Snapdragon icin)
 * - VAD kapali (daha uzun ve kesintisiz metin)
 * - maxTextCtx = 0 (whisper otomatik belirler)
 */
class WhisperSTT(private val context: Context) {

    companion object {
        private const val TAG = "WhisperSTT"
        private const val MODEL_NAME = "ggml-medium-q5_0.bin"

        private val MODEL_SOURCES = listOf(
            "/storage/emulated/0/EdaModels/$MODEL_NAME",
            "/storage/emulated/0/Download/$MODEL_NAME"
        )

        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        // حداکثر کردن تعداد تردها بر اساس CPU
        private val MAX_THREADS = Runtime.getRuntime().availableProcessors().coerceAtLeast(4)
    }

    private var whisperContext: WhisperContext? = null
    private var modelFile: File? = null

    suspend fun baslat(): Boolean = withContext(Dispatchers.IO) {
        if (whisperContext != null) return@withContext true
        try {
            val appDir = context.getExternalFilesDir(null)
            val hedefDosya = File(appDir, MODEL_NAME)

            if (!hedefDosya.exists()) {
                Log.i(TAG, "Model uygulama klasorunde yok, kaynak aranıyor...")
                var kaynakBulundu = false
                for (kaynakYol in MODEL_SOURCES) {
                    val kaynak = File(kaynakYol)
                    if (kaynak.exists()) {
                        Log.i(TAG, "Kaynak bulundu: ${kaynak.absolutePath} (${kaynak.length() / 1024 / 1024} MB)")
                        Log.i(TAG, "Kopyalaniyor...")
                        kaynak.copyTo(hedefDosya, overwrite = true)
                        Log.i(TAG, "Kopyalandi: ${hedefDosya.length() / 1024 / 1024} MB")
                        kaynakBulundu = true
                        break
                    }
                }
                if (!kaynakBulundu) {
                    Log.e(TAG, "Model hicbir kaynakta bulunamadi!")
                    MODEL_SOURCES.forEach { Log.e(TAG, "  - $it") }
                    return@withContext false
                }
            } else {
                Log.i(TAG, "Model zaten uygulama klasorunde: ${hedefDosya.length() / 1024 / 1024} MB")
            }

            modelFile = hedefDosya
            Log.i(TAG, "Whisper context olusturuluyor (${MAX_THREADS} threads)...")
            whisperContext = WhisperContext.createContextFromFile(hedefDosya.absolutePath)
            Log.i(TAG, "Whisper hazir! Threads: $MAX_THREADS")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Whisper baslatma hatasi: ${e.message}", e)
            false
        }
    }

    suspend fun dinleVeMetneCevir(sureSaniye: Int = 10): String? = withContext(Dispatchers.IO) {
        val ctx = whisperContext ?: return@withContext null
        try {
            val ses = kayitAl(sureSaniye) ?: return@withContext null
            if (ses.isEmpty()) return@withContext null

            Log.d(TAG, "Whisper'a gonderiliyor: ${ses.size} ornek (${sureSaniye}sn)")

            // حداکثر کیفیت: 8 ترد، VAD خاموش، context خودکار
            val config = TranscribeConfig(
                numThreads = MAX_THREADS,
                maxTextCtx = 0,          // whisper خودکار
                language = "tr",
                enableVad = false,       // VAD خاموش = پاسخ‌های بلندتر
                vadThreshold = 0.0f
            )

            val result = ctx.transcribeStream(ses, config)
            val metin = result.segments.joinToString(" ") { it.text.trim() }.trim()
            Log.i(TAG, "Duyulan (${metin.length} karakter): $metin")
            if (metin.isBlank()) null else metin
        } catch (e: Exception) {
            Log.e(TAG, "STT hatasi: ${e.message}", e)
            null
        }
    }

    private fun kayitAl(sureSaniye: Int): FloatArray? {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "getMinBufferSize hatasi: $minBuffer")
            return null
        }
        val bufferSize = maxOf(minBuffer, SAMPLE_RATE * 2 * sureSaniye)
        val recorder = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, CHANNEL, ENCODING, bufferSize)
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord olusturulamadi: ${e.message}", e)
            return null
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }
        try {
            val toplamOrnek = SAMPLE_RATE * sureSaniye
            val shortBuffer = ShortArray(toplamOrnek)
            var okunan = 0
            recorder.startRecording()
            Log.d(TAG, "Kayit basladi (${sureSaniye} sn, ${toplamOrnek} ornek)")
            while (okunan < toplamOrnek) {
                val kalan = toplamOrnek - okunan
                val parca = recorder.read(shortBuffer, okunan, kalan)
                if (parca <= 0) break
                okunan += parca
            }
            recorder.stop()
            Log.d(TAG, "Kayit bitti: $okunan ornek")
            val floatBuffer = FloatArray(okunan)
            for (i in 0 until okunan) {
                floatBuffer[i] = shortBuffer[i] / 32768.0f
            }
            return floatBuffer
        } catch (e: Exception) {
            Log.e(TAG, "Kayit hatasi: ${e.message}", e)
            return null
        } finally {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            } catch (e: Exception) { }
            recorder.release()
        }
    }

    fun kapat() {
        try {
            runBlocking { whisperContext?.release() }
        } catch (e: Exception) {
            Log.e(TAG, "Kapatma hatasi: ${e.message}")
        } finally {
            whisperContext = null
        }
    }
}
