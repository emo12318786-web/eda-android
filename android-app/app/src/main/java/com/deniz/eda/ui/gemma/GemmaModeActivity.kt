package com.deniz.eda.ui.gemma

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.deniz.eda.ai.GemmaSessionManager
import com.deniz.eda.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Separate local chat screen; does not alter EDA background sleep/wake behavior. */
class GemmaModeActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var sessions: GemmaSessionManager
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var requestJob: Job? = null
    private var generation = 0
    private var currentLocale = Locale("tr", "TR")
    private var triedAzerbaijani = false

    private var statusText by mutableStateOf("Hazır — Dinle düğmesine bas")
    private var heardText by mutableStateOf("")
    private var answerText by mutableStateOf("")
    private var busy by mutableStateOf(false)

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else statusText = "Mikrofon izni gerekli"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        sessions = GemmaSessionManager(applicationContext)
        tts = TextToSpeech(this, this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF64E5E0), background = Color(0xFF080A12), surface = Color(0xFF121626))) {
                Column(Modifier.fillMaxSize().background(Color(0xFF080A12)).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔒 Gemma 2.2B (Offline)", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { stopPageWork(); finish() }) { Text("Kapat") }
                    }
                    Text("Durum: " + statusText, color = Color(0xFF64E5E0), fontSize = 14.sp)
                    Text("Giriş: Türkçe (Türkiye) ve Azerbaycan Türkçesi. Yanıt dili: İstanbul Türkçesi.", color = Color(0xFF98A2B9), fontSize = 12.sp)
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF121626)), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Siz:", color = Color(0xFF64E5E0), fontWeight = FontWeight.Bold)
                            Text(heardText.ifBlank { "Henüz bir şey söylemediniz." }, color = Color.White)
                            HorizontalDivider(color = Color(0xFF30384E))
                            Text("Eda:", color = Color(0xFFB49AFF), fontWeight = FontWeight.Bold)
                            Text(answerText.ifBlank { "Seni dinlemeye hazırım." }, color = Color.White)
                        }
                    }
                    Button(onClick = { beginListeningWithPermission() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(62.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF69E6A6))) { Text("🎤  DİNLE", color = Color(0xFF080A12), fontWeight = FontWeight.Bold) }
                    Button(onClick = { stopPageWork(); statusText = "Durduruldu" }, modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF788B))) { Text("⏹  DURDUR", color = Color(0xFF080A12), fontWeight = FontWeight.Bold) }
                    Button(onClick = { heardText = ""; answerText = ""; beginListeningWithPermission() }, modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64B5F6))) { Text("🔄  TEKRAR", color = Color(0xFF080A12), fontWeight = FontWeight.Bold) }
                    Text("Bu sayfa EDA'nın arka plan dinleme ve uyku düzenini değiştirmez. Yanıtlar yalnızca telefondaki içe aktarılmış Gemma modelinden üretilir.", color = Color(0xFF98A2B9), fontSize = 11.sp)
                }
            }
        }
        val modelStatus = sessions.status()
        statusText = when {
            !modelStatus.runtimeAvailable -> "Yerel Gemma motoru APK'da hazır değil"
            modelStatus.deepModelReady -> "Hazır — Gemma 2.2B modeli bulundu"
            modelStatus.lightModelReady -> "Derin model yok; Gemma 3 1B yedek olarak kullanılabilir"
            else -> "Model bulunamadı — Ayarlar'dan GGUF modelini içe aktar"
        }
    }

    override fun onInit(result: Int) {
        ttsReady = result == TextToSpeech.SUCCESS
        if (ttsReady) {
            val chosen = tts?.setLanguage(Locale("tr", "TR"))
            if (chosen == TextToSpeech.LANG_MISSING_DATA || chosen == TextToSpeech.LANG_NOT_SUPPORTED) statusText = "Türkçe ses paketi telefonda desteklenmiyor"
        }
    }

    private fun beginListeningWithPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) micPermission.launch(Manifest.permission.RECORD_AUDIO)
        else startListening()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { statusText = "Telefonda konuşma tanıma hizmeti bulunamadı"; return }
        stopRecognizer()
        triedAzerbaijani = false
        currentLocale = Locale("tr", "TR")
        createRecognizer()
    }

    private fun createRecognizer() {
        stopRecognizer()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { statusText = "Dinliyorum (" + currentLocale.displayName + ")…" }
                override fun onBeginningOfSpeech() { statusText = "Konuşmanı algıladım…" }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { statusText = "Konuşma çözümleniyor…" }
                override fun onError(error: Int) {
                    if (!triedAzerbaijani && currentLocale.language == "tr" &&
                        error in setOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, SpeechRecognizer.ERROR_NO_MATCH)) {
                        triedAzerbaijani = true
                        currentLocale = Locale("az", "AZ")
                        statusText = "Azerbaycan Türkçesi deneniyor…"
                        createRecognizer()
                    } else statusText = when (error) {
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Konuşma hizmeti bağlantı hatası; çevrimdışı dil paketi gerekebilir"
                        SpeechRecognizer.ERROR_NO_MATCH -> "Söylenen anlaşılmadı; tekrar deneyebilirsin"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon izni gerekli"
                        else -> "Konuşma tanıma hatası (" + error + ")"
                    }
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isBlank()) { statusText = "Ses metne çevrilemedi"; return }
                    heardText = text
                    askLocalGemma(text)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLocale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLocale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            sr.startListening(intent)
        }
    }

    private fun askLocalGemma(text: String) {
        requestJob?.cancel()
        val requestId = ++generation
        busy = true
        statusText = "Yerel Gemma yanıt hazırlıyor…"
        requestJob = lifecycleScope.launch {
            try {
                statusText = "Model kontrol ediliyor…"
                val result = withContext(Dispatchers.IO) {
                    val modelStatus = sessions.status()
                    android.util.Log.i("GemmaMode", "runtime=${modelStatus.runtimeAvailable}, deep=${modelStatus.deepModelReady}, light=${modelStatus.lightModelReady}, loaded=${modelStatus.loadedSlot}")
                    if (!modelStatus.runtimeAvailable) throw IllegalStateException("Native Gemma motoru APK içinde bulunamadı")
                    if (!modelStatus.deepModelReady) throw IllegalStateException("Gemma 2.2B GGUF dosyası içe aktarılmamış")
                    val system = "Sen Eda'sın. Her zaman doğal, kısa ve anlaşılır İstanbul Türkçesiyle cevap ver. Kullanıcı Azerbaycan Türkçesi veya İstanbul Türkçesi konuşabilir; ikisini de anlamaya çalış, ama cevabı daima İstanbul Türkçesiyle ver. Yanıtı genellikle 1-3 kısa cümle tut. Bilmediğin şeyi uydurma. Bu sohbet ekranında telefon donanımını kontrol ettiğini iddia etme."
                    val prompt = system + "\n\nKullanıcı: " + text + "\nEda:"
                    sessions.generate(GemmaSessionManager.Task.DEEP, prompt, 180)
                }
                if (requestId != generation || isFinishing || isDestroyed) return@launch
                answerText = result?.trim()?.takeIf { it.isNotBlank() } ?: "Model dosyası bulundu ama yanıt üretilemedi. Durumu kontrol edip tekrar dene."
                statusText = if (result.isNullOrBlank()) "Model yanıt üretmedi; günlük kaydı için GemmaMode etiketi" else "Yanıt hazır — yalnızca yerel model"
                if (ttsReady && !result.isNullOrBlank()) tts?.speak(answerText, TextToSpeech.QUEUE_FLUSH, null, "eda-gemma-answer")
            } catch (error: Throwable) {
                android.util.Log.e("GemmaMode", "Local generation failed", error)
                if (requestId != generation || isFinishing || isDestroyed) return@launch
                answerText = when {
                    error.message?.contains("APK", ignoreCase = true) == true -> "Bu APK'da yerel Gemma motoru eksik. Yeni sürümün kurulması gerekiyor."
                    error.message?.contains("GGUF", ignoreCase = true) == true -> "Gemma 2.2B modeli içe aktarılmamış. Ayarlar'dan yalnızca Gemma 2.2B GGUF dosyasını seç."
                    else -> "Gemma yanıt üretemedi: " + (error.message ?: error.javaClass.simpleName)
                }
                statusText = "Hata yakalandı — ayrıntılar gösterildi"
            } finally {
                if (requestId == generation && !isFinishing && !isDestroyed) busy = false
            }
        }
    }

    private fun stopPageWork() {
        generation++
        requestJob?.cancel()
        requestJob = null
        busy = false
        stopRecognizer()
        tts?.stop()
    }

    private fun stopRecognizer() {
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    override fun onDestroy() {
        stopPageWork()
        tts?.shutdown()
        // Gemma 2.2B is a Gemma Mode-only resource: release its native model
        // after any in-flight synchronized inference finishes, without blocking UI teardown.
        Thread({
            try {
                sessions.unload()
            } catch (error: Exception) {
                android.util.Log.w("GemmaMode", "Could not unload local model on exit", error)
            }
        }, "EdaGemmaUnload").start()
        super.onDestroy()
    }
}