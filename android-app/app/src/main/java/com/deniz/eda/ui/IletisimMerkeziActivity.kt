package com.deniz.eda.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale

/** İleti gönderimini kullanıcı onayı olmadan gerçekleştirmeyen ileti merkezi. */
class IletisimMerkeziActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var metinAlani by mutableStateOf("")
    private var numaraAlani by mutableStateOf("")
    private var durum by mutableStateOf("Göndermeden önce alıcıyı ve iletiyi kontrol et.")
    private var onayBekliyor by mutableStateOf(false)
    private var tts: TextToSpeech? = null
    private var ttsHazir = false

    private val smsIzni = registerForActivityResult(ActivityResultContracts.RequestPermission()) { verildi ->
        if (verildi && onayBekliyor) iletiyiGonder()
        else durum = "SMS izni verilmedi. İleti gönderilmedi."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFB49AFF), background = Color(0xFF080A12),
                surface = Color(0xFF121626), onBackground = Color.White, onSurface = Color.White
            )) {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF080A12))
                        .verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("İLETİŞİM MERKEZİ", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text("SMS gönderimi yalnızca açık onayından sonra yapılır.", color = Color(0xFFB8C0D8))
                    OutlinedTextField(
                        value = numaraAlani, onValueChange = { numaraAlani = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Alıcının telefon numarası") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = metinAlani, onValueChange = { metinAlani = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("İleti metni") },
                        minLines = 3
                    )
                    Button(
                        onClick = {
                            if (numaraAlani.isBlank() || metinAlani.isBlank()) {
                                durum = "Alıcı numarasını ve ileti metnini doldur."
                            } else {
                                onayBekliyor = true
                                durum = "Gönderilecek ileti: $numaraAlani\n\n$metinAlani\n\nGöndermek için aşağıdaki onay düğmesine bas."
                                if (ttsHazir) tts?.speak("Alıcı: $numaraAlani. İleti: $metinAlani. Göndereyim mi?", TextToSpeech.QUEUE_FLUSH, null, "ileti_onay")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("İletiyi gözden geçir") }
                    if (onayBekliyor) {
                        Text(durum, color = Color.White)
                        Button(onClick = {
                            if (ContextCompat.checkSelfPermission(this@IletisimMerkeziActivity, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                                iletiyiGonder()
                            } else {
                                smsIzni.launch(Manifest.permission.SEND_SMS)
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("EVET, İLETİYİ GÖNDER") }
                        OutlinedButton(onClick = {
                            onayBekliyor = false
                            durum = "Gönderim iptal edildi."
                            tts?.speak("İleti gönderilmedi.", TextToSpeech.QUEUE_FLUSH, null, "iptal")
                        }, modifier = Modifier.fillMaxWidth()) { Text("İPTAL ET") }
                    }
                    Text(durum, color = Color(0xFF64E5E0))
                }
            }
        }
    }

    private fun iletiyiGonder() {
        if (!onayBekliyor || numaraAlani.isBlank() || metinAlani.isBlank()) {
            durum = "Gönderim için önce iletiyi gözden geçir ve onayla."
            return
        }
        try {
            val yonetici = getSystemService(SmsManager::class.java)
            yonetici.sendTextMessage(numaraAlani.trim(), null, metinAlani, null, null)
            durum = "SMS gönderim isteği sisteme iletildi."
            onayBekliyor = false
            tts?.speak("İleti gönderim isteği gönderildi.", TextToSpeech.QUEUE_FLUSH, null, "gonderildi")
        } catch (_: Exception) {
            durum = "SMS gönderilemedi. Telefon numarasını, izni ve SIM kartını kontrol et."
        }
    }

    override fun onInit(status: Int) {
        ttsHazir = status == TextToSpeech.SUCCESS
        if (ttsHazir) tts?.language = Locale("tr", "TR")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
