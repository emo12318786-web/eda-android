package com.deniz.eda.ui

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.content.res.ColorStateList
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Switch
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.deniz.eda.ai.GemmaSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.appcompat.app.AppCompatActivity
import com.deniz.eda.R
import com.deniz.eda.core.Settings

class SettingsActivity : AppCompatActivity() {

    private lateinit var gemmaSessions: GemmaSessionManager

    private val gemmaModelPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    gemmaSessions.importModel(uri)
                }
                Toast.makeText(
                    this@SettingsActivity,
                    "Gemma 3 1B: model içe aktarıldı (${info.sizeBytes / (1024 * 1024)} MB)",
                    Toast.LENGTH_LONG
                ).show()
                refreshGemmaStatus()
            } catch (e: Exception) {
                Toast.makeText(
                    this@SettingsActivity,
                    "Model içe aktarılamadı: ${e.message ?: "bilinmeyen hata"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        Settings.init(this)
        applySelectedTheme()
        gemmaSessions = GemmaSessionManager(applicationContext)

        findViewById<Button>(R.id.gemma3ModelImport).setOnClickListener {
            gemmaModelPicker.launch(arrayOf("application/octet-stream", "*/*"))
        }
        refreshGemmaStatus()

        findViewById<Switch>(R.id.smsAnnouncementSwitch).apply {
            isChecked = Settings.smsAnnouncementEnabled
            setOnCheckedChangeListener { _, checked -> Settings.smsAnnouncementEnabled = checked }
        }
        findViewById<Switch>(R.id.smsTranslationSwitch).apply {
            isChecked = Settings.smsTranslationEnabled
            setOnCheckedChangeListener { _, checked -> Settings.smsTranslationEnabled = checked }
        }
        findViewById<Switch>(R.id.incomingCallSwitch).apply {
            isChecked = Settings.incomingCallAnnouncementEnabled
            setOnCheckedChangeListener { _, checked -> Settings.incomingCallAnnouncementEnabled = checked }
        }

        // System permission shortcuts: open Android's real settings pages.
        findViewById<Button>(R.id.appPermissionsButton).setOnClickListener {
            runCatching {
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }.onFailure {
                Toast.makeText(this, "Uygulama izinleri açılamadı.", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.accessibilitySettingsButton).setOnClickListener {
            runCatching {
                startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }.onFailure {
                Toast.makeText(this, "Erişilebilirlik ayarları açılamadı.", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.notificationSettingsButton).setOnClickListener {
            runCatching {
                startActivity(Intent("android.settings.APP_NOTIFICATION_SETTINGS").apply {
                    putExtra("android.provider.extra.APP_PACKAGE", packageName)
                    putExtra("app_package", packageName)
                    putExtra("app_uid", applicationInfo.uid)
                })
            }.onFailure {
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }
        findViewById<Button>(R.id.batterySettingsButton).setOnClickListener {
            runCatching {
                startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }.onFailure {
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }

        // ═══ Header ═══
        findViewById<TextView>(R.id.geriButon).setOnClickListener { finish() }

        // Eda’nın sana hitap edeceği isim bu alandan değiştirilebilir.
        val kullaniciAdiInput = findViewById<EditText>(R.id.kullaniciAdiInput)
        val groqInput = findViewById<EditText>(R.id.groqInput)
        val navasanInput = findViewById<EditText>(R.id.navasanInput)

        kullaniciAdiInput.setText(Settings.kullaniciAdi)
        groqInput.setText(Settings.groqApiKey)
        navasanInput.setText(Settings.navasanApiKey)

        // ═══ Kaydet ═══
        findViewById<Button>(R.id.kaydetButon).setOnClickListener {
            Settings.kullaniciAdi = kullaniciAdiInput.text.toString().trim()
            Settings.groqApiKey = groqInput.text.toString().trim()
            Settings.navasanApiKey = navasanInput.text.toString().trim()

            Toast.makeText(this, "✅ Ayarlar kaydedildi", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    /**
     * Keep the legacy XML settings screen in the requested monochrome style.
     * Deliberately do not load themed artwork or apply the selected legacy palette.
     */
    private fun applySelectedTheme() {
        val background = AndroidColor.rgb(8, 8, 8)
        val panel = AndroidColor.rgb(24, 24, 24)
        val accent = AndroidColor.rgb(235, 235, 235)
        val muted = AndroidColor.rgb(165, 165, 165)
        window.statusBarColor = background
        window.navigationBarColor = background

        val content = findViewById<ViewGroup>(android.R.id.content)
        val scroll = content.getChildAt(0) as? ScrollView ?: return
        scroll.setBackgroundColor(background)
        val root = scroll.getChildAt(0) as? LinearLayout ?: return
        root.setBackgroundColor(AndroidColor.TRANSPARENT)

        for (index in 1 until root.childCount) {
            val child = root.getChildAt(index)
            if (child is LinearLayout) {
                child.background = GradientDrawable().apply {
                    setColor(panel)
                    cornerRadius = dp(18).toFloat()
                    setStroke(dp(1), AndroidColor.rgb(70, 70, 70))
                }
            }
        }

        fun tintTree(view: View) {
            when (view) {
                is EditText -> {
                    view.setTextColor(AndroidColor.WHITE)
                    view.setHintTextColor(muted)
                    view.backgroundTintList = ColorStateList.valueOf(accent)
                }
                is Switch -> {
                    view.setTextColor(AndroidColor.WHITE)
                    view.thumbTintList = ColorStateList.valueOf(accent)
                    view.trackTintList = ColorStateList.valueOf(AndroidColor.rgb(90, 90, 90))
                }
                is Button -> {
                    view.background = GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(AndroidColor.rgb(45, 45, 45))
                        setStroke(dp(1), accent)
                    }
                    view.setTextColor(AndroidColor.WHITE)
                }
                is TextView -> {
                    view.setTextColor(if (view.currentTextColor == AndroidColor.GRAY) muted else AndroidColor.WHITE)
                    view.typeface = Typeface.create("sans-serif", view.typeface?.style ?: Typeface.NORMAL)
                }
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) tintTree(view.getChildAt(i))
            }
        }
        tintTree(root)

        findViewById<TextView>(R.id.geriButon)?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(panel)
            setStroke(dp(1), accent)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun refreshGemmaStatus() {
        lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) { gemmaSessions.status() }
            val model = if (status.modelReady) "hazır" else "içe aktarılmadı"
            val runtime = if (status.runtimeAvailable) "hazır" else "APK'da motor bulunamadı"
            findViewById<TextView>(R.id.gemmaDurum).text =
                "Yerel motor: $runtime\nTek model: Gemma 3 1B GGUF ($model)"
        }
    }
}
