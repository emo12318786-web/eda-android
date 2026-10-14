package com.eda.phoneagent

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.BatteryManager
import java.util.Calendar
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
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7003)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "Eda · V0.3"
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
        root.addView(Button(this).apply {
            text = "🔋 Pil durumu"
            setOnClickListener { showBattery() }
        })
        root.addView(Button(this).apply {
            text = "📔 Günlük"
            setOnClickListener { diaryDialog() }
        })
        root.addView(Button(this).apply {
            text = "⏰ Hatırlatıcılar"
            setOnClickListener { reminderDialog() }
        })
        root.addView(Button(this).apply {
            text = "🧮 Hesap makinesi"
            setOnClickListener { calculatorDialog() }
        })
        root.addView(Button(this).apply {
            text = "🚗 Araba modu"
            setOnClickListener { toggleCarMode() }
        })
        root.addView(Button(this).apply {
            text = "🧠 Hafıza / öğrenme"
            setOnClickListener { memoryDialog() }
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
            text = "OpenAI uyumlu API veya Ollama. HTTP yalnızca localhost/127.0.0.1 için kullanılabilir. API anahtarı diske kaydedilmez."
        })
        box.addView(endpointInput)
        box.addView(modelInput)
        box.addView(keyInput)
        AlertDialog.Builder(this)
            .setTitle("Yapay zekâ bağlantısı")
            .setView(box)
            .setNegativeButton("İptal", null)
            .setPositiveButton("Kaydet") { _, _ ->
                val endpoint = endpointInput.text.toString().trim().trimEnd('/')
                val model = modelInput.text.toString().trim()
                val key = keyInput.text.toString().trim()
                if (!isAllowedEndpoint(endpoint) || model.isBlank()) {
                    show("Adres HTTPS olmalı; HTTP yalnızca localhost veya 127.0.0.1 için kullanılabilir. Model adı gerekli.")
                } else if (!endpoint.startsWith("http://") && key.isBlank()) {
                    show("Çevrim içi servis için API anahtarı gerekir; yerel modelde boş bırak.")
                } else {
                    apiKey = key
                    getSharedPreferences("eda_agent", MODE_PRIVATE).edit()
                        .putString("endpoint", endpoint).putString("model", model).apply()
                    show("Ayarlar kaydedildi. API anahtarı yalnızca uygulama belleğinde tutulur.")
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
                show("Saat " + SimpleDateFormat("hh:mm a", Locale("tr", "TR")).format(Date()))
            q.contains("tarih") || q.contains("bugün") ->
                show("Bugün " + SimpleDateFormat("dd MMMM yyyy", Locale("tr", "TR")).format(Date()))
            (q.contains("fener") || q.contains("ışık")) && (q.contains("aç") || q.contains("kapat") || q.contains("yak")) ->
                setTorch(!q.contains("kapat"))
            q.contains("pil") || q.contains("batarya") -> showBattery()
            q.startsWith("hatırlat ") || q.startsWith("hatirlat ") -> createReminderFromCommand(raw)
            q.contains("hatırlatmalar") || q.contains("hatirlatmalar") -> reminderDialog()
            q.startsWith("günlük ") || q.startsWith("gunluk ") -> saveDiary(raw.substringAfter(' ').trim())
            q == "günlük" || q == "gunluk" -> diaryDialog()
            q.startsWith("öğren ") || q.startsWith("ogren ") -> teachCommand(raw.substringAfter(' ').trim())
            q.contains("araba modu") -> toggleCarMode()
            q.startsWith("hesapla ") -> calculate(raw.substringAfter("hesapla ").trim())

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
        if (busy) { show("Bir istek zaten çalışıyor; lütfen bekle."); return }
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
        } catch (e: Exception) { show("Fener çalışmadı: " + (e.message ?: "bilinmeyen hata")) }
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

    private fun showBattery() {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val status = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = status?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = plugged != 0
        show("Pil yüzde $level. " + if (charging) "Telefon şarj oluyor." else "Telefon şarjda değil.")
    }

    private fun diaryDialog() {
        val edit = EditText(this).apply { hint = "Bugün neler oldu?"; minLines = 3 }
        val prefs = getSharedPreferences("eda_agent", MODE_PRIVATE)
        val old = prefs.getString("diary", "").orEmpty()
        AlertDialog.Builder(this).setTitle("Günlük")
            .setMessage(if (old.isBlank()) "Henüz günlük kaydı yok." else old.takeLast(4000))
            .setView(edit)
            .setNegativeButton("Kapat", null)
            .setPositiveButton("Kaydet") { _, _ -> saveDiary(edit.text.toString().trim()) }.show()
    }

    private fun saveDiary(entry: String) {
        if (entry.isBlank()) { show("Günlük metni boş."); return }
        val prefs = getSharedPreferences("eda_agent", MODE_PRIVATE)
        val stamp = SimpleDateFormat("dd.MM.yyyy hh:mm a", Locale("tr", "TR")).format(Date())
        prefs.edit().putString("diary", (prefs.getString("diary", "").orEmpty() + "\n[$stamp] $entry").takeLast(30000)).apply()
        show("Günlük kaydedildi.")
    }

    private fun reminderDialog() {
        val prefs = getSharedPreferences("eda_agent", MODE_PRIVATE)
        val saved = prefs.getString("reminders", "").orEmpty()
        val edit = EditText(this).apply { hint = "Örnek: 19:30 su iç"; singleLine = true }
        AlertDialog.Builder(this).setTitle("Hatırlatıcılar")
            .setMessage(if (saved.isBlank()) "Kayıtlı hatırlatıcı yok.\nSaat ve metin gir: 19:30 su iç" else saved.takeLast(3000) + "\n\nYeni hatırlatıcı: saat metin")
            .setView(edit).setNegativeButton("Kapat", null)
            .setPositiveButton("Ekle") { _, _ -> createReminderFromCommand("hatırlat " + edit.text.toString()) }.show()
    }

    private fun createReminderFromCommand(raw: String) {
        val payload = raw.substringAfter(' ').trim()
        val time = payload.take(5)
        if (!Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$").matches(time)) {
            show("Saat ve metni yaz: hatırlat 19:30 su iç"); return
        }
        val message = payload.drop(time.length).trim()
        if (message.isBlank()) { show("Hatırlatma metni eksik."); return }
        val parts = time.split(":")
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            set(Calendar.MINUTE, parts[1].toInt())
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val alarm = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java).apply {
            putExtra("message", message); putExtra("id", id)
        }
        val pending = PendingIntent.getBroadcast(this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
        val stamp = SimpleDateFormat("dd.MM hh:mm a", Locale("tr", "TR")).format(cal.time)
        val prefs = getSharedPreferences("eda_agent", MODE_PRIVATE)
        prefs.edit().putString("reminders", (prefs.getString("reminders", "").orEmpty() + "\n$stamp — $message").takeLast(12000)).apply()
        show("Hatırlatıcı kuruldu: $stamp. Android pil ayarları teslim saatini etkileyebilir.")
    }

    private fun calculatorDialog() {
        val edit = EditText(this).apply { hint = "Örnek: 12*(5+3)"; singleLine = true }
        AlertDialog.Builder(this).setTitle("Hesap makinesi").setView(edit)
            .setNegativeButton("İptal", null).setPositiveButton("Hesapla") { _, _ -> calculate(edit.text.toString()) }.show()
    }

    private fun calculate(expression: String) {
        val safe = expression.replace(" ", "")
        if (safe.isBlank() || !safe.matches(Regex("[0-9+\\-*/().%]+")) || safe.length > 80) {
            show("Geçersiz işlem. Yalnızca sayılar ve + - * / parantez kullan."); return
        }
        try {
            val result = SafeCalculator.eval(safe)
            show("Sonuç: $result")
        } catch (_: Exception) { show("İşlem hesaplanamadı; ifadeyi kontrol et.") }
    }

    private fun toggleCarMode() {
        val prefs = getSharedPreferences("eda_agent", MODE_PRIVATE)
        val enabled = !prefs.getBoolean("car_mode", false)
        prefs.edit().putBoolean("car_mode", enabled).apply()
        show(if (enabled) "Araba modu açık. Yanıtlar kısa tutulacak; lütfen sürüşte ekrana bakma." else "Araba modu kapalı.")
    }

    private fun memoryDialog() {
        val prefs = getSharedPreferences("eda_agent", MODE_PRIVATE)
        val edit = EditText(this).apply { hint = "Öğrenilecek ifade veya hafıza notu"; minLines = 2 }
        AlertDialog.Builder(this).setTitle("Hafıza ve öğrenme")
            .setMessage("Notlar: " + prefs.getString("notes", "").orEmpty().lines().takeLast(8).joinToString("\n") +
                "\n\nÖğrenilen komutlar: " + prefs.getString("learned", "").orEmpty().takeLast(1500))
            .setView(edit).setNegativeButton("Kapat", null)
            .setPositiveButton("Kaydet") { _, _ -> teachCommand(edit.text.toString().trim()) }.show()
    }

    private fun teachCommand(text: String) {
        if (text.isBlank()) { show("Öğretilecek metin boş."); return }
        val prefs = getSharedPreferences("eda_agent", MODE_PRIVATE)
        prefs.edit().putString("learned", (prefs.getString("learned", "").orEmpty() + "\n• " + text).takeLast(10000)).apply()
        show("Bu ifade öğrenme listesine eklendi. Henüz otomatik komut olarak çalıştırılmaz.")
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
