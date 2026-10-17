package com.deniz.eda.ui

import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.deniz.eda.R
import com.deniz.eda.ai.GemmaModelStore
import com.deniz.eda.ai.GemmaSessionManager
import com.deniz.eda.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GemmaModeActivity : AppCompatActivity() {
    private lateinit var sessions: GemmaSessionManager
    private var importSlot = GemmaModelStore.Slot.GEMMA31

    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        setBusy(true, "در حال واردکردن مدل…")
        lifecycleScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { sessions.importModel(uri, importSlot) }
                Toast.makeText(this@GemmaModeActivity,
                    "${info.slot.displayName} وارد شد (${info.sizeBytes / (1024 * 1024)} MB)",
                    Toast.LENGTH_LONG).show()
                refreshStatus()
            } catch (e: Exception) {
                findViewById<TextView>(R.id.gemmaScreenStatus).text =
                    "واردکردن مدل ناموفق بود: ${e.message ?: "خطای ناشناخته"}"
            } finally {
                setBusy(false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        setContentView(R.layout.activity_gemma_mode)
        sessions = GemmaSessionManager.get(applicationContext)
        findViewById<TextView>(R.id.gemmaBack).setOnClickListener { finish() }

        val modeSwitch = findViewById<Switch>(R.id.gemmaScreenSwitch)
        modeSwitch.isChecked = Settings.gemmaMode
        modeSwitch.setOnCheckedChangeListener { _, enabled ->
            Settings.gemmaMode = enabled
            if (!enabled) lifecycleScope.launch(Dispatchers.IO) { sessions.unload() }
            findViewById<TextView>(R.id.gemmaModeLabel).text =
                if (enabled) "حالت Gemma روشن است" else "حالت Gemma خاموش است"
            refreshStatus()
        }

        val group = findViewById<RadioGroup>(R.id.gemmaModelChoice)
        val gemma3 = findViewById<RadioButton>(R.id.chooseGemma3)
        val gemma2 = findViewById<RadioButton>(R.id.chooseGemma2)
        if (Settings.gemmaModel == GemmaModelStore.Slot.GEMMA22.preferenceValue) gemma2.isChecked = true
        else gemma3.isChecked = true
        group.setOnCheckedChangeListener { _, checkedId ->
            val chosen = if (checkedId == R.id.chooseGemma2) GemmaModelStore.Slot.GEMMA22 else GemmaModelStore.Slot.GEMMA31
            lifecycleScope.launch(Dispatchers.IO) {
                sessions.selectModel(chosen.preferenceValue)
                withContext(Dispatchers.Main) { refreshStatus() }
            }
        }

        findViewById<Button>(R.id.importGemma3).setOnClickListener {
            importSlot = GemmaModelStore.Slot.GEMMA31
            modelPicker.launch(arrayOf("application/octet-stream", "*/*"))
        }
        findViewById<Button>(R.id.importGemma2).setOnClickListener {
            importSlot = GemmaModelStore.Slot.GEMMA22
            modelPicker.launch(arrayOf("application/octet-stream", "*/*"))
        }
        findViewById<Button>(R.id.gemmaTestButton).setOnClickListener { runTest() }
        refreshStatus()
    }

    private fun runTest() {
        val output = findViewById<TextView>(R.id.gemmaTestOutput)
        val question = findViewById<TextView>(R.id.gemmaTestQuestion).text.toString()
        setBusy(true, "در حال بارگذاری مدل و ساخت پاسخ… اجرای اول ممکن است بیشتر طول بکشد.")
        output.text = ""
        lifecycleScope.launch {
            val started = System.nanoTime()
            val answer = withContext(Dispatchers.IO) { sessions.generate(question, 72) }
            val seconds = (System.nanoTime() - started) / 1_000_000_000.0
            output.text = if (answer.isNullOrBlank()) {
                "پاسخی دریافت نشد. بررسی کن فایل مدل انتخاب‌شده وارد شده باشد."
            } else {
                "زمان: %.1f ثانیه\n\n%s".format(java.util.Locale.getDefault(), seconds, answer.trim())
            }
            setBusy(false)
            refreshStatus()
        }
    }

    private fun setBusy(busy: Boolean, message: String = "") {
        findViewById<ProgressBar>(R.id.gemmaProgress).visibility =
            if (busy) android.view.View.VISIBLE else android.view.View.GONE
        findViewById<Button>(R.id.gemmaTestButton).isEnabled = !busy
        if (message.isNotBlank()) findViewById<TextView>(R.id.gemmaScreenStatus).text = message
    }

    private fun refreshStatus() {
        lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) { sessions.status() }
            val loaded = if (status.loaded) "بارگذاری شده؛ سؤال بعدی دوباره بارگذاری نمی‌کند" else "هنوز بارگذاری نشده"
            val ready = if (status.modelReady) "فایل مدل موجود است" else "فایل این مدل وارد نشده"
            val runtime = if (status.runtimeAvailable) "موتور محلی آماده است" else "موتور محلی در دسترس نیست"
            findViewById<TextView>(R.id.gemmaScreenStatus).text =
                "$runtime\nمدل انتخاب‌شده: ${status.selectedModel}\n$ready\n$loaded"
            findViewById<TextView>(R.id.gemmaModeLabel).text =
                if (Settings.gemmaMode) "حالت Gemma روشن است" else "حالت Gemma خاموش است"
        }
    }
}
