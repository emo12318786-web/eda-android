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
            text = "سلام! من اَدا هستم. فرمانت را بنویس یا صحبت کن.\nابزارهای محلی فعال‌اند؛ برای پاسخ هوشمند، تنظیمات مدل را وارد کن."
            textSize = 16f
            setPadding(0, 14, 0, 14)
        }
        root.addView(output, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        input = EditText(this).apply { hint = "فرمان یا پرسشت را بنویس" }
        root.addView(input)
        root.addView(Button(this).apply {
            text = "🎙 گفتن فرمان"
            setOnClickListener { startSpeech() }
        })
        root.addView(Button(this).apply {
            text = "اجرا / پرسش از هوش مصنوعی"
            setOnClickListener { handleCommand(input.text.toString()) }
        })
        root.addView(Button(this).apply {
            text = "تنظیم اتصال مدل هوش مصنوعی"
            setOnClickListener { showProviderSettings() }
        })
        root.addView(Button(this).apply {
            text = "بلندگو: تکرار پاسخ"
            setOnClickListener { speak(output.text.toString()) }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts?.language = Locale("fa", "IR")
    }

    private fun showProviderSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 4)
        }
        endpointInput = EditText(this).apply {
            hint = "HTTPS یا لوکال: http://127.0.0.1:11434/v1"
            setText(getSharedPreferences("eda_agent", MODE_PRIVATE).getString("endpoint", "https://api.openai.com/v1"))
        }
        modelInput = EditText(this).apply {
            hint = "نام مدل"
            setText(getSharedPreferences("eda_agent", MODE_PRIVATE).getString("model", "gpt-4o-mini"))
        }
        keyInput = EditText(this).apply { hint = "کلید API (برای مدل محلی خالی بگذار)" }
        box.addView(TextView(this).apply {
            text = "API سازگار با OpenAI/Ollama. HTTP فقط برای localhost/127.0.0.1 مجاز است. کلید آنلاین فقط در حافظه می‌ماند و برای مدل محلی لازم نیست."
        })
        box.addView(endpointInput)
        box.addView(modelInput)
        box.addView(keyInput)
        AlertDialog.Builder(this)
            .setTitle("اتصال هوش مصنوعی")
            .setView(box)
            .setNegativeButton("لغو", null)
            .setPositiveButton("ذخیره و اتصال") { _, _ ->
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "فرمانت را بگو")
        }
        try { startActivityForResult(intent, requestSpeech) }
        catch (_: Exception) { show("سرویس تشخیص گفتار روی گوشی در دسترس نیست.") }
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
        if (q.isBlank()) { show("فرمان خالی است."); return }
        when {
            q.contains("ساعت") || q == "time" ->
                show("ساعت " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()))
            q.contains("تاریخ") || q.contains("امروز") ->
                show("تاریخ " + SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date()))
            (q.contains("باتری") || q.contains("شارژ گوشی") || q.contains("درصد شارژ")) ->
                show(batteryStatus())
            (q.contains("وای فای") || q.contains("وای‌فای") || q.contains("wifi") || q.contains("wi-fi")) &&
                (q.contains("خاموش") || q.contains("روشن") || q.contains("قطع") || q.contains("وصل")) ->
                openSystemSettings("wifi")
            q.contains("تنظیمات صدا") || q.contains("صدای گوشی") ->
                openSystemSettings("sound")
            q.contains("تنظیمات نمایشگر") || q.contains("روشنایی") || q.contains("نور صفحه") ->
                openSystemSettings("display")
            q.contains("باز کن") && (q.contains("یوتیوب") || q.contains("youtube")) ->
                openAllowedApp("youtube")
            q.contains("باز کن") && (q.contains("مرورگر") || q.contains("کروم") || q.contains("chrome")) ->
                openAllowedApp("chrome")
            q.contains("باز کن") && (q.contains("نقشه") || q.contains("maps")) ->
                openAllowedApp("maps")
            q.contains("باز کن") && (q.contains("تلگرام") || q.contains("telegram")) ->
                openAllowedApp("telegram")
            q.contains("باز کن") && (q.contains("واتساپ") || q.contains("whatsapp")) ->
                openAllowedApp("whatsapp")
            q.contains("باز کن") && (q.contains("دوربین") || q.contains("camera")) ->
                openAllowedApp("camera")
            q.contains("چراغ") && (q.contains("روشن") || q.contains("خاموش") || q.contains("باز")) ->
                setTorch(!q.contains("خاموش"))
            q.startsWith("یادداشت ") || q.startsWith("یادداشت:") -> {
                val note = raw.substringAfter(if (q.startsWith("یادداشت:")) ":" else "یادداشت").trim()
                if (note.isBlank()) show("متن یادداشت را هم بگو.")
                else {
                    val prefs = getSharedPreferences("eda_agent", MODE_PRIVATE)
                    val old = prefs.getString("notes", "").orEmpty()
                    prefs.edit().putString("notes", (old + "\n• " + note).takeLast(20000)).apply()
                    show("یادداشت روی همین گوشی ذخیره شد.")
                }
            }
            q.contains("یادداشت‌ها") || q.contains("یادداشت ها") -> {
                val notes = getSharedPreferences("eda_agent", MODE_PRIVATE).getString("notes", "").orEmpty()
                show(if (notes.isBlank()) "هنوز یادداشتی ذخیره نشده." else notes)
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
            show("برای پرسش آزاد، اتصال مدل را تنظیم کن. مدل محلی می‌تواند بدون کلید باشد؛ فرمان‌های محلی بدون مدل هم کار می‌کنند.")
            return
        }
        busy = true
        show("دارم فکر می‌کنم…")
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

    private fun batteryStatus(): String {
        val intent = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return "وضعیت باتری خوانده نشد."
        val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        if (level < 0) return "درصد باتری خوانده نشد."
        val percent = level * 100 / scale
        val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
            status == android.os.BatteryManager.BATTERY_STATUS_FULL
        return "شارژ باتری $percent از ۱۰۰ درصد است؛ " +
            if (status == android.os.BatteryManager.BATTERY_STATUS_FULL) "باتری کاملاً پر است."
            else if (charging) "گوشی در حال شارژ است." else "گوشی به شارژر وصل نیست."
    }

    private fun openSystemSettings(kind: String) {
        val intent = when (kind) {
            "wifi" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS)
            "sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            else -> { show("این تنظیمات پشتیبانی نمی‌شود."); return }
        }
        try {
            startActivity(intent)
            show(when (kind) {
                "wifi" -> "پنل وای‌فای باز شد. اندروید ممکن است خاموش/روشن‌کردن را به تأیید خودت بسپارد."
                "sound" -> "تنظیمات صدا باز شد."
                else -> "تنظیمات نمایشگر باز شد."
            })
        } catch (_: Exception) { show("بازکردن تنظیمات ممکن نشد.") }
    }

    private fun openAllowedApp(app: String) {
        val packages = when (app) {
            "chrome" -> listOf("com.android.chrome", "com.google.android.browser")
            "youtube" -> listOf("com.google.android.youtube")
            "maps" -> listOf("com.google.android.apps.maps")
            "camera" -> listOf("com.sec.android.app.camera", "com.android.camera2", "com.google.android.GoogleCamera")
            "whatsapp" -> listOf("com.whatsapp")
            "telegram" -> listOf("org.telegram.messenger", "org.thunderdog.challegram")
            else -> emptyList()
        }
        for (pkg in packages) {
            val launch = packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                try { startActivity(launch); show("برنامه باز شد: $app") }
                catch (_: Exception) { show("بازکردن برنامه ممکن نشد.") }
                return
            }
        }
        show("این برنامه روی گوشی پیدا نشد یا نصب نیست.")
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
            if (cameraId == null) { show("این گوشی چراغ‌قوهٔ قابل‌کنترل پیدا نکرد."); return }
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
            else show("بدون اجازهٔ دوربین، کنترل چراغ‌قوه ممکن نیست.")
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
