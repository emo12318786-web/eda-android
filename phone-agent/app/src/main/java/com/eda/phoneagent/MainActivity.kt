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
            hint = "آدرس API، مثلاً https://api.example.com/v1"
            setText(getSharedPreferences("eda_agent", MODE_PRIVATE).getString("endpoint", "https://api.openai.com/v1"))
        }
        modelInput = EditText(this).apply {
            hint = "نام مدل"
            setText(getSharedPreferences("eda_agent", MODE_PRIVATE).getString("model", "gpt-4o-mini"))
        }
        keyInput = EditText(this).apply { hint = "کلید API (در حافظه می‌ماند؛ ذخیره نمی‌شود)" }
        box.addView(TextView(this).apply {
            text = "API سازگار با OpenAI. آدرس و نام مدل ذخیره می‌شوند؛ کلید فقط تا بسته‌شدن برنامه در حافظه می‌ماند."
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
                if (!endpoint.startsWith("https://") || model.isBlank() || key.isBlank()) {
                    show("آدرس HTTPS، نام مدل و کلید API لازم است.")
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
                show("ساعت " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
            q.contains("تاریخ") || q.contains("امروز") ->
                show("تاریخ " + SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date()))
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
        if (key.isBlank()) {
            show("برای پرسش آزاد، از دکمهٔ «تنظیم اتصال مدل هوش مصنوعی» استفاده کن. فرمان‌های محلی بدون مدل کار می‌کنند.")
            return
        }
        busy = true
        show("دارم فکر می‌کنم…")
        Thread {
            var result = ""
            var conn: HttpURLConnection? = null
            try {
                val url = URL("$endpoint/chat/completions")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $key")
                }
                val body = JSONObject()
                    .put("model", model)
                    .put("messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content",
                            "You are Eda, a concise and kind Persian-speaking Android assistant. Answer in the user's language. You cannot directly execute phone actions from the AI connection. Never claim to have performed an action unless the app confirms it. The app itself handles simple time/date, flashlight and local-note commands before calling you. Ask before sensitive actions."))
                        .put(JSONObject().put("role", "user").put("content", prompt)))
                    .put("temperature", 0.4)
                    .toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val response = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                if (code in 200..299) {
                    result = JSONObject(response).getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").optString("content", "").trim()
                    if (result.isBlank()) result = "مدل پاسخ خالی فرستاد."
                } else {
                    result = "اتصال مدل ناموفق بود (HTTP $code). آدرس، مدل، کلید و اینترنت را بررسی کن."
                }
            } catch (e: Exception) {
                result = "اتصال برقرار نشد. اینترنت و آدرس API را بررسی کن. جزئیات: ${e.javaClass.simpleName}"
            } finally {
                conn?.disconnect()
                val finalResult = result
                runOnUiThread { busy = false; show(finalResult) }
            }
        }.start()
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
