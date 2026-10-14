package com.eda.phoneagent

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.BatteryManager
import android.os.Build
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
            text = "E · D · A V107 Agent"
            textSize = 24f
        })
        output = TextView(this).apply {
            text = "Merhaba! Ben Eda. Türkçe konuşabilir veya yazabilirsin.\nYerel komutlar hazır; serbest sohbet için model ayarlarını aç."
            textSize = 16f
            setPadding(0, 14, 0, 14)
        }
        root.addView(output, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        input = EditText(this).apply { hint = "Komutunu yaz veya konuş" }
        root.addView(input)
        root.addView(Button(this).apply {
            text = "🎙 Komut söyle"
            setOnClickListener { startSpeech() }
        })
        root.addView(Button(this).apply {
            text = "Çalıştır / Yapay zekâya sor"
            setOnClickListener { handleCommand(input.text.toString()) }
        })
        root.addView(Button(this).apply {
            text = "Yapay zekâ bağlantı ayarları"
            setOnClickListener { showProviderSettings() }
        })
        root.addView(Button(this).apply {
            text = "🔊 Yanıtı seslendir"
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
            hint = "HTTPS veya yerel Ollama: http://127.0.0.1:11434/v1"
            setText(getSharedPreferences("eda_agent", MODE_PRIVATE).getString("endpoint", "https://api.openai.com/v1"))
        }
        modelInput = EditText(this).apply {
            hint = "Model adı"
            setText(getSharedPreferences("eda_agent", MODE_PRIVATE).getString("model", "gpt-4o-mini"))
        }
        keyInput = EditText(this).apply { hint = "API anahtarı (yerel modelde boş bırak)" }
        box.addView(TextView(this).apply {
            text = "OpenAI uyumlu API / Ollama. HTTP yalnızca yerel localhost adreslerinde izinlidir. API anahtarı diske kaydedilmez."
        })
        box.addView(endpointInput)
        box.addView(modelInput)
        box.addView(keyInput)
        AlertDialog.Builder(this)
            .setTitle("Yapay zekâ bağlantısı")
            .setView(box)
            .setNegativeButton("İptal", null)
            .setPositiveButton("Kaydet ve bağlan") { _, _ ->
                val endpoint = endpointInput.text.toString().trim().trimEnd('/')
                val model = modelInput.text.toString().trim()
                val key = keyInput.text.toString().trim()
                if (!isAllowedEndpoint(endpoint) || model.isBlank()) {
                    show("Adres HTTPS olmalı; HTTP yalnızca localhost için izinli. Model adı gerekli.")
                } else if (!endpoint.startsWith("http://") && key.isBlank()) {
                    show("Çevrim içi servis için API anahtarı gerekli; yerel modelde boş bırak.")
                } else {
                    apiKey = key
                    getSharedPreferences("eda_agent", MODE_PRIVATE).edit()
                        .putString("endpoint", endpoint).putString("model", model).apply()
                    show("Ayarlar kaydedildi; API anahtarı yalnızca bellekte tutuluyor.")
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
        catch (_: Exception) { show("Telefonda konuşma tanıma servisi bulunamadı. Google uygulamasında çevrimdışı Türkçe dilini indir.") }
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
            q.contains("ساعت") || q.contains("saat") || q == "time" ->
                show("Saat " + SimpleDateFormat("hh:mm a", Locale("tr", "TR")).format(Date()))
            q.contains("تاریخ") || q.contains("امروز") || q.contains("tarih") || q.contains("bugün") ->
                show("Tarih " + SimpleDateFormat("dd MMMM yyyy", Locale("tr", "TR")).format(Date()))
            (q.contains("چراغ") && (q.contains("روشن") || q.contains("خاموش") || q.contains("باز"))) || (q.contains("fener") && (q.contains("aç") || q.contains("kapat")) ) ->
                setTorch(!(q.contains("خاموش") || q.contains("kapat")))
            q.startsWith("یادداشت ") || q.startsWith("یادداشت:") -> {
                val note = raw.substringAfter(if (q.startsWith("یادداشت:")) ":" else "یادداشت").trim()
                if (note.isBlank()) show("Not metnini de söyle.")
                else {
                    val prefs = getSharedPreferences("eda_agent", MODE_PRIVATE)
                    val old = prefs.getString("notes", "").orEmpty()
                    prefs.edit().putString("notes", (old + "\n• " + note).takeLast(20000)).apply()
                    show("Not Eda’nın yerel hafızasına kaydedildi.")
                }
            }
            q.contains("یادداشت‌ها") || q.contains("یادداشت ها") || q.contains("notlarım") || q.contains("notlar") -> {
                val notes = getSharedPreferences("eda_agent", MODE_PRIVATE).getString("notes", "").orEmpty()
                show(if (notes.isBlank()) "Henüz not yok." else notes)
            }
            q.startsWith("not al ") || q.startsWith("not:") -> {
                val note = raw.substringAfter(if (q.startsWith("not:")) ":" else "not al").trim()
                if (note.isBlank()) show("Not metnini de söyle.")
                else {
                    val prefs = getSharedPreferences("eda_agent", MODE_PRIVATE)
                    val oldNotes = prefs.getString("notes", "").orEmpty()
                    prefs.edit().putString("notes", (oldNotes + "\n• " + note).takeLast(20000)).apply()
                    show("Not Eda'nın yerel hafızasına kaydedildi.")
                }
            }
            q.startsWith("günlük ") || q.startsWith("gunluk ") || q.startsWith("günlüğe yaz ") || q.startsWith("günlüğe kaydet ") -> saveDiary(raw.substringAfter(" ").trim())
            q.contains("günlüğüm") || q.contains("gunlugum") || q.contains("günlükler") || q.contains("günlüğü göster") || q.contains("günlüğümü göster") -> showDiary()
            q.startsWith("hatırlat ") || q.startsWith("hatirlat ") || q.startsWith("hatırlatıcı ") -> scheduleReminder(raw.substringAfter(" ").trim())
            q.contains("pil kaç") || q.contains("pil durumu") || q.contains("batarya") || q.contains("pil yüzde") || q.contains("şarj durumu") -> showBattery()
            q.startsWith("hesapla ") || q.startsWith("hesap ") || q.startsWith("hesapla:") -> calculate(raw.substringAfter(" ").trim())
            q.startsWith("notu paylaş ") || q.startsWith("notu paylas ") || q.startsWith("notu paylaş:") -> shareNote(raw.substringAfter(" ").trim())
            q.contains("araba modu") -> {
                val prefs = getSharedPreferences("eda_agent", MODE_PRIVATE)
                val enabled = !prefs.getBoolean("car_mode", false)
                prefs.edit().putBoolean("car_mode", enabled).apply()
                show(if (enabled) "Araba modu açık. Sürüşte ekranla uğraşma." else "Araba modu kapalı.")
            }
            else -> askModel(raw)
        }
    }


    private fun saveDiary(entry: String) {
        if (entry.isBlank()) { show("Günlük metnini söyle."); return }
        val prefs = getSharedPreferences("eda_agent", MODE_PRIVATE)
        val old = prefs.getString("diary", "").orEmpty()
        val stamp = SimpleDateFormat("dd MMM yyyy hh:mm a", Locale("tr", "TR")).format(Date())
        prefs.edit().putString("diary", (old + "\n[" + stamp + "] " + entry).takeLast(30000)).apply()
        show("Günlüğe kaydettim.")
    }

    private fun showDiary() {
        val diary = getSharedPreferences("eda_agent", MODE_PRIVATE).getString("diary", "").orEmpty()
        show(if (diary.isBlank()) "Henüz günlük kaydı yok." else diary.takeLast(5000))
    }

    private fun scheduleReminder(value: String) {
        val parts = value.split(" ", limit = 2)
        if (parts.size < 2 || !parts[0].matches(Regex("\\d{1,2}:\\d{2}"))) {
            show("Örnek: hatırlat 19:30 su iç"); return
        }
        val hm = parts[0].split(":")
        val hour = hm[0].toIntOrNull() ?: -1
        val minute = hm[1].toIntOrNull() ?: -1
        if (hour !in 0..23 || minute !in 0..59) { show("Saat geçersiz. Örnek: 19:30"); return }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7003)
        }
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour); set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val intent = Intent(this, ReminderReceiver::class.java).putExtra("message", parts[1]).putExtra("id", id)
        val pending = PendingIntent.getBroadcast(this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
            else alarm.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
            show("Hatırlatıcı kuruldu: " + SimpleDateFormat("hh:mm a", Locale("tr", "TR")).format(cal.time) + " · " + parts[1])
        } catch (_: Exception) { show("Hatırlatıcı kurulamadı; bildirim izinlerini kontrol et.") }
    }

    private fun showBattery() {
        val manager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val state = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = state?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        show("Pil: %$level · " + if (plugged != 0) "Şarj oluyor." else "Şarjda değil.")
    }

    private fun calculate(expression: String) {
        try {
            val result = SafeCalculator.eval(expression)
            show("Sonuç: " + if (result % 1.0 == 0.0) result.toLong().toString() else result.toString())
        } catch (e: Exception) { show("Hesaplanamadı: " + (e.message ?: "geçersiz işlem")) }
    }

    private fun shareNote(note: String) {
        if (note.isBlank()) { show("Örnek: notu paylaş alışveriş listesi"); return }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Eda notu")
            putExtra(Intent.EXTRA_TEXT, note)
        }
        startActivity(Intent.createChooser(send, "Notu Samsung Notes veya başka uygulamayla paylaş"))
    }

    private fun askModel(prompt: String) {
        if (busy) { show("Bir istek sürüyor; lütfen bekle."); return }
        val prefs = getSharedPreferences("eda_agent", MODE_PRIVATE)
        val endpoint = prefs.getString("endpoint", "https://api.openai.com/v1").orEmpty().trimEnd('/')
        val model = prefs.getString("model", "gpt-4o-mini").orEmpty()
        val key = apiKey
        if (!isAllowedEndpoint(endpoint) || (!endpoint.startsWith("http://") && key.isBlank())) {
            show("برای پرسش آزاد، اتصال مدل را تنظیم کن. مدل محلی می‌تواند بدون کلید باشد؛ فرمان‌های محلی بدون مدل هم کار می‌کنند.")
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
            if (cameraId == null) { show("Bu telefonda kontrol edilebilir fener bulunamadı."); return }
            manager.setTorchMode(cameraId, enable)
            show(if (enable) "چراغ‌قوه روشن شد." else "چراغ‌قوه خاموش شد.")
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

    private fun show(text: String) { output.text = text; speak(text) }
    private fun speak(text: String) { if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "eda-response") }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
