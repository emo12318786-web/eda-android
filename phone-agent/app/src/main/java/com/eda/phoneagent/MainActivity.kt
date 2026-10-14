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
import android.view.Gravity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
        window.statusBarColor = Color.rgb(76, 49, 177)
        window.navigationBarColor = Color.rgb(13, 7, 29)
        window.decorView.systemUiVisibility = 0
        tts = TextToSpeech(this, this)

        val prefs = getSharedPreferences("eda_agent", MODE_PRIVATE)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(8, 5, 22), Color.rgb(24, 12, 48), Color.rgb(19, 10, 39))
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brandColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        brandColumn.addView(TextView(this).apply {
            text = "E · D · A"
            textSize = 30f
            letterSpacing = 0.18f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        brandColumn.addView(TextView(this).apply {
            text = "✦  Eda hazır · Dokununca dinler"
            textSize = 14f
            setTextColor(Color.rgb(210, 202, 244))
            setPadding(0, dp(4), 0, 0)
        })
        header.addView(brandColumn)
        val settings = Button(this).apply {
            text = "⚙"
            textSize = 25f
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(37, 27, 70), Color.rgb(116, 86, 232), dp(28), dp(2))
            minWidth = dp(58); minimumWidth = dp(58); minHeight = dp(58)
            setPadding(0, 0, 0, 0)
            setOnClickListener { showProviderSettings() }
        }
        header.addView(settings, LinearLayout.LayoutParams(dp(62), dp(62)))
        page.addView(header)
        addGap(page, 28)

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(Color.rgb(25, 20, 51), Color.rgb(54, 42, 95), dp(25), dp(1))
        }
        statusCard.addView(TextView(this).apply {
            text = "■"
            textSize = 19f
            setTextColor(Color.rgb(91, 164, 255))
            setPadding(0, 0, dp(14), 0)
        })
        statusCard.addView(TextView(this).apply {
            text = "Durum"
            textSize = 16f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        statusCard.addView(TextView(this).apply {
            text = "Hazır"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        page.addView(statusCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76)))
        addGap(page, 18)

        output = TextView(this).apply {
            text = "Merhaba! Ben Eda. Benimle Türkçe konuşabilir veya yazabilirsin.\nSaat, tarih, fener, notlar, günlük, hatırlatıcı ve hesaplama çevrimdışı çalışır. Serbest sohbet ve canlı hava durumu için bir yapay zekâ modeli gerekir."
            textSize = 15f
            setTextColor(Color.rgb(235, 231, 248))
            setPadding(dp(4), dp(4), dp(4), dp(12))
            setTextIsSelectable(true)
        }
        page.addView(output)
        input = EditText(this).apply {
            hint = "Komutunu yaz veya konuş"
            setHintTextColor(Color.rgb(177, 167, 209))
            setTextColor(Color.WHITE)
            textSize = 17f
            isSingleLine = false
            maxLines = 3
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(Color.rgb(22, 16, 44), Color.rgb(87, 68, 141), dp(12), dp(1))
        }
        page.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addGap(page, 14)

        page.addView(actionButton("▶  EDA'YI BAŞLAT", true) {
            if (input.text.isNullOrBlank()) startSpeech() else handleCommand(input.text.toString())
        }, buttonParams())
        addGap(page, 12)
        page.addView(actionButton("🎙  KOMUT SÖYLE", true) { startSpeech() }, buttonParams())
        addGap(page, 12)
        page.addView(actionButton("▦  KONTROL PANELİ", true) { showControlPanel() }, buttonParams())
        addGap(page, 12)
        page.addView(actionButton("🔊  YANITI SESLENDİR", false) { speak(output.text.toString()) }, buttonParams())
        addGap(page, 14)
        page.addView(actionButton("ÇALIŞTIR / YAPAY ZEKÂYA SOR", false) {
            handleCommand(input.text.toString())
        }, buttonParams())

        val footer = TextView(this).apply {
            text = "EDA V107 · Agent"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(169, 158, 201))
            setPadding(0, dp(28), 0, dp(8))
        }
        page.addView(footer)
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(Color.rgb(13, 7, 29))
            addView(page)
        })
    }


    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun rounded(fill: Int, stroke: Int, radius: Int, strokeWidth: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius.toFloat()
            setStroke(strokeWidth, stroke)
        }

    private fun addGap(parent: LinearLayout, height: Int) {
        parent.addView(android.view.View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun buttonParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(66)
    )

    private fun actionButton(label: String, primary: Boolean, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 14f
            letterSpacing = 0.035f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = if (primary)
                rounded(Color.rgb(105, 73, 222), Color.rgb(127, 103, 245), dp(22), dp(1))
            else rounded(Color.rgb(34, 25, 64), Color.rgb(76, 59, 126), dp(20), dp(1))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun showControlPanel() {
        val options = arrayOf(
            "Pil ve şarj durumu",
            "Notlarımı göster",
            "Günlüğümü göster",
            "Komut listesi",
            "Yapay zekâ bağlantısı"
        )
        AlertDialog.Builder(this)
            .setTitle("EDA · Kontrol paneli")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showBattery()
                    1 -> handleCommand("notlarım")
                    2 -> showDiary()
                    3 -> handleCommand("yardım")
                    4 -> showProviderSettings()
                }
            }
            .setNegativeButton("Kapat", null)
            .show()
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
            q == "yardım" || q == "yardim" || q == "komutlar" || q == "help" ->
                show("Komutlar: saat kaç; bugün tarih; feneri aç/kapat; not al ...; notlarım; notlarımı paylaş; günlük ...; günlüğümü göster; hatırlat 19:30 su iç; pil durumu; hesapla 12*(5+3); hava durumu Tebriz. Hava durumu için internet gerekir. Serbest sohbet için Yapay Zekâ bağlantısı kur.")
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
            q == "notlarımı paylaş" || q == "notlarimi paylas" -> shareAllNotes()
            q.startsWith("notu paylaş ") || q.startsWith("notu paylas ") || q.startsWith("notu paylaş:") -> shareNote(raw.substringAfter(" ").trim())
            q == "hava durumu" || q == "hava nasıl" || q == "hava nasil" ->
                show("Hangi şehir için hava durumunu istiyorsun? Örnek: hava durumu Tebriz")
            q.startsWith("hava durumu ") -> fetchWeather(raw.substringAfter("hava durumu").trim())
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


    private fun fetchWeather(city: String) {
        if (city.isBlank()) { show("Şehir adı yaz. Örnek: hava durumu Tebriz"); return }
        if (busy) { show("Bir istek sürüyor; lütfen bekle."); return }
        busy = true
        show("$city için güncel hava durumu aranıyor…")
        Thread {
            val result = try {
                fun getJson(url: String): JSONObject {
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10000; readTimeout = 12000; instanceFollowRedirects = false
                        setRequestProperty("Accept", "application/json")
                    }
                    try {
                        val code = conn.responseCode
                        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                        if (code !in 200..299) error("HTTP $code")
                        return JSONObject(body)
                    } finally { conn.disconnect() }
                }
                val encodedCity = java.net.URLEncoder.encode(city, "UTF-8")
                val geo = getJson("https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity&count=1&language=tr&format=json")
                val place = geo.optJSONArray("results")?.optJSONObject(0) ?: error("Şehir bulunamadı")
                val lat = place.getDouble("latitude")
                val lon = place.getDouble("longitude")
                val name = place.optString("name", city)
                val country = place.optString("country", "")
                val wx = getJson("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m&timezone=auto")
                val current = wx.getJSONObject("current")
                val code = current.optInt("weather_code", -1)
                val desc = when (code) {
                    0 -> "Açık"; 1 -> "Çoğunlukla açık"; 2 -> "Parçalı bulutlu"; 3 -> "Kapalı"
                    45, 48 -> "Sisli"; 51, 53, 55 -> "Çisenti"; 56, 57 -> "Dondurucu çisenti"
                    61, 63, 65 -> "Yağmurlu"; 66, 67 -> "Dondurucu yağmur"
                    71, 73, 75, 77 -> "Karlı"; 80, 81, 82 -> "Sağanak yağış"
                    85, 86 -> "Kar sağanağı"; 95 -> "Gök gürültülü fırtına"; 96, 99 -> "Dolu ihtimalli fırtına"
                    else -> "Hava durumu"
                }
                val placeName = if (country.isBlank()) name else "$name, $country"
                "$placeName güncel hava: $desc. Sıcaklık ${current.optDouble("temperature_2m")}°C, hissedilen ${current.optDouble("apparent_temperature")}°C, nem %${current.optInt("relative_humidity_2m")}, rüzgâr ${current.optDouble("wind_speed_10m")} km/sa. Veriler internetten alındı."
            } catch (e: Exception) {
                "Hava durumu alınamadı. İnternet bağlantısını ve şehir adını kontrol et. Ayrıntı: ${e.message ?: e.javaClass.simpleName}"
            }
            runOnUiThread { busy = false; show(result) }
        }.start()
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


    private fun shareAllNotes() {
        val notes = getSharedPreferences("eda_agent", MODE_PRIVATE).getString("notes", "").orEmpty()
        if (notes.isBlank()) { show("Henüz kaydedilmiş not yok."); return }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Eda notlarım")
            putExtra(Intent.EXTRA_TEXT, notes)
        }
        startActivity(Intent.createChooser(send, "Notları Samsung Notes veya başka uygulamayla paylaş"))
    }

    private fun askModel(prompt: String) {
        if (busy) { show("Bir istek sürüyor; lütfen bekle."); return }
        val prefs = getSharedPreferences("eda_agent", MODE_PRIVATE)
        val endpoint = prefs.getString("endpoint", "https://api.openai.com/v1").orEmpty().trimEnd('/')
        val model = prefs.getString("model", "gpt-4o-mini").orEmpty()
        val key = apiKey
        if (!isAllowedEndpoint(endpoint) || (!endpoint.startsWith("http://") && key.isBlank())) {
            show("Serbest sohbet veya canlı hava durumu için Yapay Zekâ bağlantısını ayarla. Yerel komutlar model olmadan da çevrimdışı çalışır.")
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
            show(if (enable) "Fener açıldı." else "Fener kapatıldı.")
        } catch (e: Exception) { show("Fener çalıştırılamadı: " + (e.message ?: "bilinmeyen hata")) }
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
