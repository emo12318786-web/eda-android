package com.eda.phoneagent

import android.Manifest
import android.app.Activity
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var input: EditText
    private lateinit var output: TextView
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val requestSpeech = 7001
    private val requestCamera = 7002
    private var pendingTorch: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "Eda Phone Agent · V0.1"
            textSize = 24f
        })
        output = TextView(this).apply {
            text = "سلام! من اَدا هستم. فرمانت را بنویس یا صحبت کن.\nفعلاً ابزارهای محلی محدود فعال‌اند."
            textSize = 16f
            setPadding(0, 18, 0, 18)
        }
        root.addView(output, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        input = EditText(this).apply { hint = "مثلاً ساعت چنده؟ یا چراغ‌قوه را روشن کن" }
        root.addView(input)
        root.addView(Button(this).apply {
            text = "🎙 گفتن فرمان"
            setOnClickListener { startSpeech() }
        })
        root.addView(Button(this).apply {
            text = "اجرای فرمان"
            setOnClickListener { handleCommand(input.text.toString()) }
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
            else -> show("این فرمان هنوز ابزار اجرایی ندارد. این نسخهٔ اولیه است و هنوز به مدل هوش مصنوعی متصل نشده.")
        }
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
