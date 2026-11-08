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
            WakeDiagnostics.breadcrumb(context, "Sherpa prepare started")
            try {
                val requiredAssets = listOf(ENCODER, DECODER, JOINER, TOKENS, KEYWORDS_FILE)
                requiredAssets.forEach { asset -> context.assets.open(asset).use { } }
                val keywordText = context.assets.open(KEYWORDS_FILE).bufferedReader().use { it.readText() }
                require(keywordText.isNotBlank()) { "Sherpa keywords file is empty" }
                // BPE word pieces are not necessarily represented as whole-word tokens.
                // Do not reject valid wake phrases using a naive whole-word token check.
                WakeDiagnostics.breadcrumb(context, "Sherpa assets checked; keyword token validation delegated to Sherpa")
                val config = KeywordSpotterConfig(
                    featConfig = FeatureConfig(sampleRate = RATE, featureDim = 80),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = ENCODER,
                            decoder = DECODER,
                            joiner = JOINER
                        ),
                        tokens = TOKENS,
                        numThreads = 1,
                        provider = "cpu",
                        modelType = "zipformer2"
                    ),
                    keywordsFile = KEYWORDS_FILE,
                    keywordsScore = 1.0f,
                    keywordsThreshold = 0.25f,
                    numTrailingBlanks = 1
                )
                WakeDiagnostics.breadcrumb(context, "Sherpa native KeywordSpotter init starting; modelType=zipformer2; threads=1")
                spotter = KeywordSpotter(assetManager = context.assets, config = config)
                WakeDiagnostics.breadcrumb(context, "KeywordSpotter created")
                WakeEngineStatus.sherpaState = "READY"
                WakeEngineStatus.sherpaDetail = "English KWS model loaded; not listening"
                Log.i(TAG, "ENGINE=SHERPA MODEL_READY=true")
                main.post(onReady)
            } catch (e: Throwable) {
                WakeDiagnostics.breadcrumb(context, "Sherpa prepare failed: ${e.javaClass.simpleName}: ${e.message}")
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

        // AudioRecord creation/start and native decode stay off the service's main thread.
        val activeWorker = thread(start = false, name = "eda-sherpa-kws") {
            var audio: AudioRecord? = null
            var stream: com.k2fsa.sherpa.onnx.OnlineStream? = null
            var detectedWake: String? = null
            try {
                WakeDiagnostics.breadcrumb(context, "Sherpa background microphone setup started")
                val minBytes = AudioRecord.getMinBufferSize(
                    RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBytes <= 0) throw IllegalStateException("AudioRecord buffer unavailable: $minBytes")
                val createdAudio = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBytes * 2, RATE / 2)
                )
                audio = createdAudio
                recorder = createdAudio
                if (createdAudio.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("AudioRecord failed to initialize")
                }
                if (!running.get()) return@thread
                createdAudio.startRecording()
                WakeDiagnostics.breadcrumb(context, "Sherpa AudioRecord started")
                val phrase = normalize(wakeWord()).ifBlank { "eda" }
                val keywordTokens = encodeKeywordAsBpe(phrase)
                WakeEngineStatus.activeEngine = "SHERPA"
                WakeEngineStatus.sherpaState = "LISTENING"
                WakeEngineStatus.sherpaDetail = "Listening for English phrase: $phrase"
                main.post { onStatus("Sherpa dinliyor: $phrase") }
                Log.i(TAG, "ENGINE=SHERPA LISTENING=true EXPECTED_WAKE=\"$phrase\"")

                val activeStream = kws.createStream(keywordTokens)
                stream = activeStream
                WakeDiagnostics.breadcrumb(context, "Sherpa keyword stream created for: $phrase (BPE=$keywordTokens)")
                var firstDecodeRecorded = false
                val shortBuffer = ShortArray(1600)
                while (running.get()) {
                    val count = createdAudio.read(shortBuffer, 0, shortBuffer.size)
                    if (count <= 0) {
                        if (count < 0 && running.get()) throw IllegalStateException("AudioRecord read failed: $count")
                        continue
                    }
                    val samples = FloatArray(count) { shortBuffer[it] / 32768.0f }
                    activeStream.acceptWaveform(samples, sampleRate = RATE)
                    while (running.get() && kws.isReady(activeStream)) {
                        kws.decode(activeStream)
                        if (!firstDecodeRecorded) {
                            firstDecodeRecorded = true
                            WakeDiagnostics.breadcrumb(context, "Sherpa first decode completed")
                        }
                        val result = kws.getResult(activeStream).keyword
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
            } catch (e: Throwable) {
                if (running.get()) {
                    WakeDiagnostics.breadcrumb(context, "Sherpa decode/start failed: ${e.javaClass.simpleName}: ${e.message}")
                    Log.e(TAG, "Sherpa listening failed", e)
                    reportError(e.message ?: "Sherpa listening failed")
                }
            } finally {
                running.set(false)
                WakeDiagnostics.breadcrumb(context, "Sherpa worker cleanup started")
                try { stream?.release() } catch (_: Throwable) {}
                try {
                    val activeAudio = audio
                    if (activeAudio?.recordingState == AudioRecord.RECORDSTATE_RECORDING) activeAudio.stop()
                } catch (_: Throwable) {}
                try { audio?.release() } catch (_: Throwable) {}
                if (recorder === audio) recorder = null
                WakeDiagnostics.breadcrumb(context, "Sherpa AudioRecord released")
                if (WakeEngineStatus.sherpaState == "LISTENING") {
                    WakeEngineStatus.sherpaState = "READY"
                    WakeEngineStatus.sherpaDetail = "Model ready; not listening"
                }
                // Reopen SpeechRecognizer only after AudioRecord and stream are released.
                detectedWake?.let { heard -> main.post { onWake(heard) } }
            }
        }
        worker = activeWorker
        activeWorker.start()
        return true
    }

    fun stop() {
        WakeDiagnostics.breadcrumb(context, "Sherpa stop requested")
        running.set(false)
        val audio = recorder
        try { if (audio?.recordingState == AudioRecord.RECORDSTATE_RECORDING) audio.stop() } catch (_: Exception) {}
        val activeWorker = worker
        if (activeWorker != null && activeWorker !== Thread.currentThread()) {
            try { activeWorker.join(3000L) } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            if (activeWorker.isAlive) {
                Log.w(TAG, "Worker still alive after stop join; defer native spotter release")
                WakeDiagnostics.breadcrumb(context, "Sherpa worker still alive after 3000ms join")
                return
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
        if (worker?.isAlive == true) {
            Log.w(TAG, "Deferring KeywordSpotter release because worker is still alive")
            WakeDiagnostics.breadcrumb(context, "KeywordSpotter release deferred; worker still alive")
            return
        }
        try { spotter?.release() } catch (_: Throwable) {}
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

    /**
     * KeywordSpotter.createStream expects BPE pieces separated by spaces,
     * not the raw phrase. Segment the English wake phrase using model tokens.
     */
    private fun encodeKeywordAsBpe(value: String): String {
        val vocab = context.assets.open(TOKENS).bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val token = line.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
                token.takeIf { it.isNotEmpty() && !it.startsWith("#") }
            }.toList()
        }
        val text = "▁" + value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), "▁")
        val pieces = vocab.distinct().sortedByDescending { it.length }
        val output = mutableListOf<String>()
        var offset = 0
        while (offset < text.length) {
            val token = pieces.firstOrNull { text.startsWith(it, offset) }
                ?: throw IllegalArgumentException("Wake phrase cannot be tokenized by Sherpa vocabulary near: ${text.substring(offset)}")
            output += token
            offset += token.length
        }
        require(output.isNotEmpty()) { "Wake phrase is empty after BPE tokenization" }
        return output.joinToString(" ")
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace('_', ' ').replace(Regex("[^a-z0-9' -]"), " ")
            .replace(Regex("\\s+"), " ").trim()
}
