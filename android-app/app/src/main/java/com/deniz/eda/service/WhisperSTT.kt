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
 * Whisper STT - Model: /storage/emulated/0/EdaModels/ggml-medium-q5_0.bin
 * MANAGE_EXTERNAL_STORAGE izni gerekir.
 */
class WhisperSTT(private val context: Context) {

    companion object {
        private const val TAG = "WhisperSTT"
        private const val MODEL_NAME = "ggml-medium-q5_0.bin"
        const val MODEL_DIR = "/storage/emulated/0/EdaModels"
        const val MODEL_PATH = "/storage/emulated/0/EdaModels/$MODEL_NAME"

        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private val MAX_THREADS = 2
    }

    private var whisperContext: WhisperContext? = null
    private var modelFile: File? = null

    suspend fun baslat(): Boolean = withContext(Dispatchers.IO) {
        if (whisperContext != null) return@withContext true
        try {
            val hedefDosya = File(MODEL_PATH)
            if (!hedefDosya.exists()) {
                Log.e(TAG, "Model bulunamadi: $MODEL_PATH")
                return@withContext false
            }
            Log.i(TAG, "Model bulundu: ${hedefDosya.length() / 1024 / 1024} MB")
            modelFile = hedefDosya
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
            val config = TranscribeConfig(
                numThreads = MAX_THREADS,
                maxTextCtx = 0,
                language = "tr",
                enableVad = false,
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
        if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) return null
        val bufferSize = maxOf(minBuffer, SAMPLE_RATE * 2 * sureSaniye)
        val recorder = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, CHANNEL, ENCODING, bufferSize)
        } catch (e: Exception) { return null }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) { recorder.release(); return null }
        try {
            val toplamOrnek = SAMPLE_RATE * sureSaniye
            val shortBuffer = ShortArray(toplamOrnek)
            var okunan = 0
            recorder.startRecording()
            while (okunan < toplamOrnek) {
                val parca = recorder.read(shortBuffer, okunan, toplamOrnek - okunan)
                if (parca <= 0) break
                okunan += parca
            }
            recorder.stop()
            val floatBuffer = FloatArray(okunan)
            for (i in 0 until okunan) floatBuffer[i] = shortBuffer[i] / 32768.0f
            return floatBuffer
        } catch (e: Exception) { return null
        } finally {
            try { if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() } catch (e: Exception) { }
            recorder.release()
        }
    }

    fun kapat() {
        try { runBlocking { whisperContext?.release() } }
        catch (e: Exception) { Log.e(TAG, "Kapatma hatasi: ${e.message}") }
        finally { whisperContext = null }
    }
}
