package com.deniz.eda.ui

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.deniz.eda.ai.GemmaSessionManager
import com.deniz.eda.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Dedicated text-first offline screen for long or complex questions. */
class OfflineDeepQuestionsActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var azFallback = false
    private var listeningLocale = Locale("tr", "TR")
    private var questionSetter: ((String) -> Unit)? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceInput() else voiceStatus = "Mikrofon izni gerekli"
    }
    private var voiceStatus by mutableStateOf("Ses girişi: Türkçe / Azerbaycan Türkçesi")

    private lateinit var sessions: GemmaSessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        sessions = GemmaSessionManager(applicationContext)
        tts = TextToSpeech(this, this)

        var question by mutableStateOf("")
        var answer by mutableStateOf("")
        var status by mutableStateOf("Çevrimdışı • Gemma 2 2B GGUF gerekli")
        var busy by mutableStateOf(false)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFB49AFF),
                background = Color(0xFF080A12),
                surface = Color(0xFF121626),
                onBackground = Color.White,
                onSurface = Color.White
            )) {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF080A12))
                        .verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Çevrimdışı Akıllı Analiz", color = Color(0xFFB49AFF), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("Uzun sorular ve karmaşık analizler • Offline", color = Color(0xFF98A2B9), fontSize = 13.sp)
                        }
                        TextButton(onClick = { finish() }) { Text("Geri dön") }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF121626))) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Gemma Mode’dan ayrı", color = Color(0xFF64E5E0), fontWeight = FontWeight.Bold)
                            Text("Sorular telefondaki yerel modele gider; çevrimiçi servise gönderilmez. Uzun analiz için Gemma 2 2B önerilir. Aynı anda yalnızca bir model yüklenir.", color = Color(0xFFCBD2E3), fontSize = 13.sp)
                        }
                    }
                    Text("Soru veya uzun metin", color = Color.White, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                        placeholder = { Text("Buraya yaz, yapıştır veya sesle sor…") },
                        minLines = 5,
                        maxLines = 12,
                        enabled = !busy,
                        shape = RoundedCornerShape(16.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            questionSetter = { question = it }
                            if (ContextCompat.checkSelfPermission(this@OfflineDeepQuestionsActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            } else startVoiceInput()
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text("🎙  Sesli soru sor") }
                    Text(voiceStatus, color = Color(0xFF64E5E0), fontSize = 12.sp)
                    Button(
                        onClick = {
                            val prompt = question.trim()
                            if (prompt.isBlank()) {
                                status = "Lütfen önce sorunuzu yazın."
                            } else {
                                busy = true
                                answer = ""
                                status = "Yerel model yükleniyor ve analiz ediyor. İlk çalışma uzun sürebilir…"
                                lifecycleScope.launch {
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            val state = sessions.status()
                                            if (!state.runtimeAvailable) throw IllegalStateException("Yerel yapay zekâ motoru APK içinde bulunamadı.")
                                            if (!state.deepModelReady) throw IllegalStateException("Gemma 2 2B GGUF dosyası henüz içe aktarılmadı.")
                                            val system = "Sen Eda’sın; doğru ve dikkatli bir çevrimdışı asistansın. Uzun metni adım adım incele, önemli noktaları sınıflandır, gerekirse varsayımları ve sonucu ayrı yaz. Kullanıcının dilinde yanıt ver, bilgi uydurma ve yanıtı açık, düzenli tut.\n\n"
                                            sessions.generate(GemmaSessionManager.Task.DEEP, system + "سؤال کاربر:\n" + prompt + "\n\nتحلیل و پاسخ:", 220)
                                        }
                                        answer = result?.trim()?.takeIf { it.isNotBlank() }
                                            ?: "Model yanıt üretmedi. GGUF dosyasını ve model durumunu kontrol et."
                                        status = if (result.isNullOrBlank()) "Yanıt üretilemedi" else "Yanıt yerel modelden üretildi • İnternet kullanılmadı"
                                        if (ttsReady && !result.isNullOrBlank()) tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "eda-offline-answer")
                                    } catch (e: Exception) {
                                        answer = e.message ?: "Model çalışırken bilinmeyen bir hata oluştu."
                                        status = "Çevrimdışı çalıştırma başarısız"
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) { Text(if (busy) "Analiz ediliyor…" else "Çevrimdışı analiz", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { question = ""; answer = ""; status = "Yeni soru için hazır." },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Temizle") }
                    Text(status, color = Color(0xFF64E5E0), fontSize = 13.sp)
                    if (answer.isNotBlank()) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF121626)), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("EDA yanıtı", color = Color(0xFFB49AFF), fontWeight = FontWeight.Bold)
                                Text(answer, color = Color.White, fontSize = 15.sp, lineHeight = 22.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onInit(result: Int) {
        ttsReady = result == TextToSpeech.SUCCESS
        if (ttsReady) {
            val language = tts?.setLanguage(Locale("tr", "TR"))
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                voiceStatus = "Türkçe ses paketi bulunamadı; TTS ayarlarını kontrol et"
            }
        }
    }

    private fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceStatus = "Telefonda konuşma tanıma hizmeti yok"
            return
        }
        try { recognizer?.destroy() } catch (_: Exception) {}
        azFallback = false
        listeningLocale = Locale("tr", "TR")
        createRecognizer()
    }

    private fun createRecognizer() {
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { voiceStatus = "Dinliyorum • ${listeningLocale.displayName}" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { voiceStatus = "Konuşma çözümleniyor…" }
                override fun onError(error: Int) {
                    if (!azFallback && listeningLocale.language == "tr" &&
                        error in setOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, SpeechRecognizer.ERROR_NO_MATCH)) {
                        azFallback = true
                        listeningLocale = Locale("az", "AZ")
                        createRecognizer()
                    } else voiceStatus = "Ses tanıma hatası ($error); çevrimdışı dil paketi gerekebilir"
                }
                override fun onResults(results: Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (spoken.isNotBlank()) {
                        questionSetter?.invoke(spoken)
                        voiceStatus = "Algılandı: $spoken"
                    } else voiceStatus = "Ses metne çevrilemedi"
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, listeningLocale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, listeningLocale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            sr.startListening(intent)
        }
    }

    override fun onDestroy() {
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
