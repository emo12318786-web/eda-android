package com.deniz.eda.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.whispercpp.whisper.WhisperContext
import com.whispercpp.whisper.TranscribeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Whisper tabanli, tamamen offline STT (Speech-to-Text) motoru.
 * Model: assets/models/ggml-medium-q5_0.bin
 */
class WhisperSTT(private val context: Context) {

    companion object {
        private const val TAG = "WhisperSTT"
        private const val MODEL_ASSET = "models/ggml-medium-q5_0.bin"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private var whisperContext: WhisperContext? = null
    private var modelFile: File? = null

    suspend fun baslat(): Boolean = withContext(Dispatchers.IO) {
        if (whisperContext != null) return@withContext true
        try {
            val hedefDosya = File(context.filesDir, "ggml-medium-q5_0.bin")
            if (!hedefDosya.exists()) {
                Log.i(TAG, "Model cikariliyor...")
                context.assets.open(MODEL_ASSET).use { input ->
                    FileOutputStream(hedefDosya).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }
                Log.i(TAG, "Model cikarildi: ${hedefDosya.length() / 1024 / 1024} MB")
            }
            modelFile = hedefDosya
            Log.i(TAG, "Whisper context olusturuluyor...")
            whisperContext = WhisperContext.createContextFromFile(hedefDosya.absolutePath)
            Log.i(TAG, "Whisper hazir!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Whisper baslatma hatasi: ${e.message}", e)
            false
        }
    }

    suspend fun dinleVeMetneCevir(sureSaniye: Int = 6): String? = withContext(Dispatchers.IO) {
        val ctx = whisperContext ?: return@withContext null
        try {
            val ses = kayitAl(sureSaniye) ?: return@withContext null
            if (ses.isEmpty()) return@withContext null
            Log.d(TAG, "Whisper'a gonderiliyor: ${ses.size} ornek")
            val config = TranscribeConfig(
                numThreads = 4,
                maxTextCtx = 256,
                language = "tr",
                enableVad = true,
                vadThreshold = 0.4f
            )
            val result = ctx.transcribeStream(ses, config)
            val metin = result.segments.joinToString(" ") { it.text.trim() }.trim()
            Log.i(TAG, "Duyulan: $metin")
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
            Log.d(TAG, "Kayit basladi (${sureSaniye} sn)")
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
            whisperContext?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Kapatma hatasi: ${e.message}")
        } finally {
            whisperContext = null
        }
    }
}
