package com.deniz.eda.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Offline English keyword spotting using Sherpa-ONNX.
 * Model assets are bundled into the APK by the Android CI workflow.
 */
class SherpaWakeWordListener(
    private val context: Context,
    private val wakeWord: () -> String,
    private val onWake: (String) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "EdaSherpaWake"
        private const val RATE = 16000
        private const val MODEL_DIR = "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01"
        private const val ENCODER = "$MODEL_DIR/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
        private const val DECODER = "$MODEL_DIR/decoder-epoch-12-avg-2-chunk-16-left-64.onnx"
        private const val JOINER = "$MODEL_DIR/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
        private const val TOKENS = "$MODEL_DIR/tokens.txt"
        private const val BPE = "$MODEL_DIR/bpe.model"
        private const val KEYWORDS_FILE = "$MODEL_DIR/test_wavs/test_keywords.txt"
    }

    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    @Volatile private var spotter: KeywordSpotter? = null
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var worker: Thread? = null
    @Volatile private var released = false

    fun prepare(onReady: () -> Unit) {
        WakeEngineStatus.sherpaState = "PREPARING"
        WakeEngineStatus.sherpaDetail = "Loading bundled English KWS model"
        thread(name = "eda-sherpa-prepare") {
            try {
                val config = KeywordSpotterConfig(
                    featConfig = FeatureConfig(sampleRate = RATE, featureDim = 80),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = ENCODER,
                            decoder = DECODER,
                            joiner = JOINER
                        ),
                        tokens = TOKENS,
                        numThreads = 2,
                        provider = "cpu",
                        modelingUnit = "bpe",
                        bpeVocab = BPE
                    ),
                    keywordsFile = KEYWORDS_FILE,
                    keywordsScore = 1.0f,
                    keywordsThreshold = 0.25f,
                    numTrailingBlanks = 1
                )
                spotter = KeywordSpotter(assetManager = context.assets, config = config)
                WakeEngineStatus.sherpaState = "READY"
                WakeEngineStatus.sherpaDetail = "English KWS model loaded; not listening"
                Log.i(TAG, "ENGINE=SHERPA MODEL_READY=true")
                main.post(onReady)
            } catch (e: Exception) {
                WakeEngineStatus.sherpaState = "ERROR"
                WakeEngineStatus.sherpaDetail = e.message ?: "Sherpa model initialization failed"
                Log.e(TAG, "ENGINE=SHERPA MODEL_READY=false", e)
                main.post { onError(e.message ?: "Sherpa model initialization failed") }
            }
        }
    }

    fun start(): Boolean {
        val kws = spotter ?: run {
            Log.w(TAG, "Cannot start: model is not ready")
            return false
        }
        if (released || !running.compareAndSet(false, true)) return running.get()
        val minBytes = AudioRecord.getMinBufferSize(
            RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBytes <= 0) {
            running.set(false)
            reportError("AudioRecord buffer unavailable: $minBytes")
            return false
        }
        try {
            val audio = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBytes * 2, RATE / 2)
            )
            if (audio.state != AudioRecord.STATE_INITIALIZED) {
                audio.release()
                running.set(false)
                reportError("AudioRecord failed to initialize")
                return false
            }
            recorder = audio
            audio.startRecording()
            val phrase = normalize(wakeWord()).ifBlank { "eda" }
            WakeEngineStatus.activeEngine = "SHERPA"
            WakeEngineStatus.sherpaState = "LISTENING"
            WakeEngineStatus.sherpaDetail = "Listening for English phrase: $phrase"
            onStatus("Sherpa dinliyor: $phrase")
            Log.i(TAG, "ENGINE=SHERPA LISTENING=true EXPECTED_WAKE=\"$phrase\"")
            worker = thread(name = "eda-sherpa-kws") {
                var stream: com.k2fsa.sherpa.onnx.OnlineStream? = null
                var detectedWake: String? = null
                try {
                    stream = kws.createStream(phrase)
                    val shortBuffer = ShortArray(1600)
                    while (running.get()) {
                        val count = audio.read(shortBuffer, 0, shortBuffer.size)
                        if (count <= 0) {
                            if (count < 0 && running.get()) throw IllegalStateException("AudioRecord read failed: $count")
                            continue
                        }
                        val samples = FloatArray(count) { shortBuffer[it] / 32768.0f }
                        stream.acceptWaveform(samples, sampleRate = RATE)
                        while (running.get() && kws.isReady(stream)) {
                            kws.decode(stream)
                            val result = kws.getResult(stream).keyword
                            if (result.isNotBlank()) {
                                val heard = normalize(result)
                                WakeEngineStatus.sherpaExpected = phrase
                                WakeEngineStatus.sherpaLastHeard = heard
                                WakeEngineStatus.sherpaLastMatch = heard == phrase || heard.replace('_', ' ') == phrase
                                WakeEngineStatus.activeEngine = "SHERPA"
                                Log.i(TAG, "ENGINE=SHERPA FINAL_HEARD=\"$heard\" EXPECTED_WAKE=\"$phrase\" MATCH=true")
                                Log.i(TAG, "WAKE_REASON=SHERPA_KEYWORD_SPOTTER HEARD=\"$heard\"")
                                WakeEngineStatus.sherpaState = "TRIGGERED"
                                WakeEngineStatus.sherpaDetail = "Wake phrase detected: $heard"
                                detectedWake = heard
                                running.set(false)
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (running.get()) {
                        Log.e(TAG, "Sherpa listening failed", e)
                        reportError(e.message ?: "Sherpa listening failed")
                    }
                } finally {
                    running.set(false)
                    try { stream?.release() } catch (_: Exception) {}
                    try { if (audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) audio.stop() } catch (_: Exception) {}
                    try { audio.release() } catch (_: Exception) {}
                    if (recorder === audio) recorder = null
                    if (WakeEngineStatus.sherpaState == "LISTENING") {
                        WakeEngineStatus.sherpaState = "READY"
                        WakeEngineStatus.sherpaDetail = "Model ready; not listening"
                    }
                    // Do not reopen SpeechRecognizer until Sherpa has released AudioRecord.
                    detectedWake?.let { heard -> main.post { onWake(heard) } }
                }
            }
            return true
        } catch (e: Exception) {
            running.set(false)
            reportError(e.message ?: "Could not start Sherpa microphone")
            return false
        }
    }

    fun stop() {
        running.set(false)
        val audio = recorder
        try { if (audio?.recordingState == AudioRecord.RECORDSTATE_RECORDING) audio.stop() } catch (_: Exception) {}
        val activeWorker = worker
        if (activeWorker != null && activeWorker !== Thread.currentThread()) {
            try { activeWorker.join(1500L) } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        if (recorder == null && WakeEngineStatus.sherpaState == "LISTENING") {
            WakeEngineStatus.sherpaState = if (spotter != null) "READY" else "STOPPED"
            WakeEngineStatus.sherpaDetail = "Model ready; not listening"
        }
    }

    fun release() {
        released = true
        stop()
        try { spotter?.release() } catch (_: Exception) {}
        spotter = null
        WakeEngineStatus.sherpaState = "STOPPED"
        WakeEngineStatus.sherpaDetail = "Released"
    }

    private fun reportError(message: String) {
        WakeEngineStatus.sherpaState = "ERROR"
        WakeEngineStatus.sherpaDetail = message
        Log.e(TAG, "ENGINE=SHERPA ERROR=\"$message\"")
        main.post { onError(message) }
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace('_', ' ').replace(Regex("[^a-z0-9' -]"), " ")
            .replace(Regex("\\s+"), " ").trim()
}
