package com.eda.phoneagent

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var endpointInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var keyInput: EditText
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val requestSpeech = 7001
    private val requestCamera = 7002
    private var pendingTorch: Boolean? = null
    @Volatile private var busy = false
    // API key intentionally remains in memory only; it is not written to disk.
    @Volatile private var apiKey: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "Eda Phone Agent · V0.2"
            textSize = 24f
        })
        output = TextView(this).apply {
            text = "Merhaba! Ben Eda. Benimle Türkçe konuşabilir veya komut yazabilirsin.\nSaat, tarih, fener ve notlar çevrimdışı çalışır. Serbest sohbet için model bağlantısını ayarla."
            textSize = 16f
            setPadding(0, 14, 0, 14)
        }
        root.addView(output, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        input = EditText(this).apply { hint = "Komutunu veya sorunu yaz" }
        root.addView(input)
        root.addView(Button(this).apply {
            text = "🎙 Konuş"
            setOnClickListener { startSpeech() }
        })
        root.addView(Button(this).apply {
            text = "Komutu çalıştır / Eda’ya sor"
            setOnClickListener { handleCommand(input.text.toString()) }
        })
        root.addView(Button(this).apply {
            text = "Yapay zekâ bağlantısını ayarla"
            setOnClickListener { showProviderSettings() }
        })
        root.addView(Button(this).apply {
            text = "Yanıtı sesli oku"
            setOnClickListener { speak(output.text.toString()) }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts?.language = Locale("tr", "TR")
    }

    private fun showProviderSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 4)
        }
        endpointInput = EditText(this).apply {
            hint = "HTTPS veya yerel: http://127.0.0.1:11434/v1"
            setText(getSharedPreferences("eda_agent", MODE_PRIVATE).getString("endpoint", "https://api.openai.com/v1"))
        }
        modelInput = EditText(this).apply {
            hint = "Model adı (ör. gemma3:1b)"
            setText(getSharedPreferences("eda_agent", MODE_PRIVATE).getString("model", "gpt-4o-mini"))
        }
        keyInput = EditText(this).apply { hint = "API anahtarı (yerel modelde boş bırak)" }
        box.addView(TextView(this).apply {
            text = "API سازگار با OpenAI/Ollama. HTTP فقط برای localhost/127.0.0.1 مجاز است. کلید آنلاین فقط در حافظه می‌ماند و برای مدل محلی لازم نیست."
        })
        box.addView(endpointInput)
        box.addView(modelInput)
        box.addView(keyInput)
        AlertDialog.Builder(this)
            .setTitle("Yapay zekâ bağlantısı")
            .setView(box)
            .setNegativeButton("لغو", null)
            .setPositiveButton("Kaydet") { _, _ ->
                val endpoint = endpointInput.text.toString().trim().trimEnd('/')
                val model = modelInput.text.toString().trim()
                val key = keyInput.text.toString().trim()
                if (!isAllowedEndpoint(endpoint) || model.isBlank()) {
                    show("آدرس باید HTTPS باشد؛ HTTP فقط برای localhost یا 127.0.0.1 مجاز است. نام مدل هم لازم است.")
                } else if (!endpoint.startsWith("http://") && key.isBlank()) {
                    show("برای سرویس آنلاین کلید API لازم است؛ برای مدل محلی کلید را خالی بگذار.")
                } else {
                    apiKey = key
                    getSharedPreferences("eda_agent", MODE_PRIVATE).edit()
                        .putString("endpoint", endpoint).putString("model", model).apply()
                    show("تنظیمات ذخیره شد؛ کلید فقط تا بسته‌شدن برنامه در حافظه می‌ماند.")
                }
            }.show()
    }

    private fun startSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Komutunu söyle")
        }
        try { startActivityForResult(intent, requestSpeech) }
        catch (_: Exception) { show("Telefonda konuşma tanıma hizmeti kullanılamıyor.") }
    }

    @Deprecated("Handled for compatibility with Android speech recognizer")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestSpeech && resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            input.setText(text)
            handleCommand(text)
        }
    }

    private fun handleCommand(raw: String) {
        val q = raw.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) { show("Komut boş."); return }
        when {
            q.contains("saat") || q == "time" ->
                show("Saat " + SimpleDateFormat("HH:mm", Locale("tr", "TR")).format(Date()))
            q.contains("tarih") || q.contains("bugün") ->
                show("Bugün " + SimpleDateFormat("dd MMMM yyyy", Locale("tr", "TR")).format(Date()))
            (q.contains("fener") || q.contains("ışık")) && (q.contains("aç") || q.contains("kapat") || q.contains("yak")) ->
                setTorch(!q.contains("kapat"))
            q.startsWith("not al ") || q.startsWith("not:") || q.startsWith("not ") -> {
                val note = raw.substringAfter(if (q.startsWith("not:")) ":" else if (q.startsWith("not al ")) "not al" else "not").trim()
                if (note.isBlank()) show("Not metnini de söyle.")
                else {
                    val prefs = getSharedPreferences("eda_agent", MODE_PRIVATE)
                    val old = prefs.getString("notes", "").orEmpty()
                    prefs.edit().putString("notes", (old + "\n• " + note).takeLast(20000)).apply()
                    show("Not Eda uygulamasında saklandı."); shareNote(note)
                }
            }
            q.contains("notlarım") || q.contains("notlar") -> {
                val notes = getSharedPreferences("eda_agent", MODE_PRIVATE).getString("notes", "").orEmpty()
                show(if (notes.isBlank()) "Henüz kaydedilmiş not yok." else notes)
            }
            else -> askModel(raw)
        }
    }

    private fun askModel(prompt: String) {
        if (busy) { show("یک درخواست در حال اجراست؛ کمی صبر کن."); return }
        val prefs = getSharedPreferences("eda_agent", MODE_PRIVATE)
        val endpoint = prefs.getString("endpoint", "https://api.openai.com/v1").orEmpty().trimEnd('/')
        val model = prefs.getString("model", "gpt-4o-mini").orEmpty()
        val key = apiKey
        if (!isAllowedEndpoint(endpoint) || (!endpoint.startsWith("http://") && key.isBlank())) {
            show("Serbest sohbet için model bağlantısını ayarla. Yerel komutlar model olmadan da çalışır.")
            return
        }
        busy = true
        show("Düşünüyorum…")
        Thread {
            val result = AgentRuntime(applicationContext).answer(prompt, endpoint, model, key)
            runOnUiThread { busy = false; show(result) }
        }.start()
    }

    private fun isAllowedEndpoint(endpoint: String): Boolean {
        return try {
            val url = URL(endpoint)
            when (url.protocol.lowercase(Locale.ROOT)) {
                "https" -> url.host.isNotBlank()
                "http" -> url.host.lowercase(Locale.ROOT) in setOf("localhost", "127.0.0.1", "[::1]", "::1")
                else -> false
            }
        } catch (_: Exception) { false }
    }

    private fun setTorch(enable: Boolean) {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingTorch = enable
            requestPermissions(arrayOf(Manifest.permission.CAMERA), requestCamera)
            return
        }
        try {
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            val cameraId = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId == null) { show("Kontrol edilebilir fener bulunamadı."); return }
            manager.setTorchMode(cameraId, enable)
            show(if (enable) "Fener açıldı." else "Fener kapatıldı.")
        } catch (e: Exception) { show("چراغ‌قوه اجرا نشد: " + (e.message ?: "خطای نامشخص")) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == requestCamera) {
            val action = pendingTorch
            pendingTorch = null
            if (results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED && action != null) setTorch(action)
            else show("Kamera izni olmadan fener kontrol edilemez.")
        }
    }

    private fun shareNote(note: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Eda notu")
            putExtra(Intent.EXTRA_TEXT, note)
        }
        try { startActivity(Intent.createChooser(send, "Notu hangi uygulamaya göndermek istersin?")) }
        catch (_: Exception) { show("Not Eda uygulamasında saklandı; paylaşım uygulaması bulunamadı.") }
    }

    private fun show(text: String) { output.text = text; speak(text) }
    private fun speak(text: String) { if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "eda-response") }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
