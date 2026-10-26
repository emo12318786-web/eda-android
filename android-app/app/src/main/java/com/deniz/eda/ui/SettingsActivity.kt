package com.deniz.eda.ui

import android.os.Bundle
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
     * The XML settings screen predates theme palettes. Recolor its existing controls
     * at runtime so switching themes also applies here without changing any settings logic.
     */
    private fun applySelectedTheme() {
        ThemeConfig.refresh(this)
        val palette = ThemeConfig.current
        val background = palette.background.toInt()
        val panel = palette.panel.toInt()
        val accent = palette.accent.toInt()
        val muted = palette.muted.toInt()
        window.statusBarColor = background
        window.navigationBarColor = background

        val content = findViewById<ViewGroup>(android.R.id.content)
        val scroll = content.getChildAt(0) as? ScrollView ?: return
        val artworkId = resources.getIdentifier("bg_theme_${palette.id}", "drawable", packageName)
        if (artworkId != 0) scroll.setBackgroundResource(artworkId) else scroll.setBackgroundColor(background)

        val root = scroll.getChildAt(0) as? LinearLayout ?: return
        root.setBackgroundColor(AndroidColor.TRANSPARENT)

        // This layout's direct children after the header are the four setting cards.
        for (index in 1 until root.childCount) {
            val child = root.getChildAt(index)
            if (child is LinearLayout) {
                child.background = GradientDrawable().apply {
                    setColor(AndroidColor.argb(226, AndroidColor.red(panel), AndroidColor.green(panel), AndroidColor.blue(panel)))
                    cornerRadius = dp(ThemeConfig.cornerRadius(palette, 18)).toFloat()
                    setStroke(dp(1), AndroidColor.argb(70, AndroidColor.red(accent), AndroidColor.green(accent), AndroidColor.blue(accent)))
                }
            }
        }

        val oldAccents = setOf(
            androidx.core.content.ContextCompat.getColor(this, com.deniz.eda.R.color.purple_start),
            androidx.core.content.ContextCompat.getColor(this, com.deniz.eda.R.color.purple_end),
            androidx.core.content.ContextCompat.getColor(this, com.deniz.eda.R.color.pink_start),
            androidx.core.content.ContextCompat.getColor(this, com.deniz.eda.R.color.pink_end),
            androidx.core.content.ContextCompat.getColor(this, com.deniz.eda.R.color.green_start),
            androidx.core.content.ContextCompat.getColor(this, com.deniz.eda.R.color.green_end),
            androidx.core.content.ContextCompat.getColor(this, com.deniz.eda.R.color.red_start),
            androidx.core.content.ContextCompat.getColor(this, com.deniz.eda.R.color.red_end),
            androidx.core.content.ContextCompat.getColor(this, com.deniz.eda.R.color.blue_start),
            androidx.core.content.ContextCompat.getColor(this, com.deniz.eda.R.color.blue_end),
            androidx.core.content.ContextCompat.getColor(this, com.deniz.eda.R.color.accent_cyan)
        )

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
                    view.trackTintList = ColorStateList.valueOf(AndroidColor.argb(110, AndroidColor.red(accent), AndroidColor.green(accent), AndroidColor.blue(accent)))
                }
                is Button -> {
                    view.backgroundTintList = ColorStateList.valueOf(accent)
                    view.setTextColor(if (luminance(accent) > 0.62) AndroidColor.BLACK else AndroidColor.WHITE)
                }
                is TextView -> {
                    val current = view.currentTextColor
                    if (current in oldAccents) view.setTextColor(accent)
                    else if (current == androidx.core.content.ContextCompat.getColor(this, com.deniz.eda.R.color.text_muted)) view.setTextColor(muted)
                    else if (current == androidx.core.content.ContextCompat.getColor(this, com.deniz.eda.R.color.text_secondary)) view.setTextColor(palette.secondary.toInt())
                }
            }
            if (view is TextView) {
                // Legacy XML widgets use the selected theme's actual Typeface too.
                val style = view.typeface?.style ?: Typeface.NORMAL
                view.typeface = Typeface.create(ThemeConfig.androidTypeface(palette), style)

                // Re-skin the existing settings-card pictograms without changing IDs/actions.
                val original = view.text?.toString().orEmpty()
                val replacement = when {
                    original.startsWith("👤") -> ThemeConfig.icon("brand", palette)
                    original.startsWith("🔑") -> ThemeConfig.icon("settings", palette)
                    original.startsWith("📱") -> ThemeConfig.icon("phone", palette)
                    original.startsWith("🧠") -> ThemeConfig.icon("chat", palette)
                    original.startsWith("💾") -> ThemeConfig.icon("system", palette)
                    else -> null
                }
                if (replacement != null) view.text = replacement + original.drop(1)
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) tintTree(view.getChildAt(i))
            }
        }
        tintTree(root)

        findViewById<TextView>(com.deniz.eda.R.id.geriButon)?.background = GradientDrawable().apply {
            val angular = palette.shapeStyle in setOf("cut", "hex", "sharp", "square")
            shape = if (angular) GradientDrawable.RECTANGLE else GradientDrawable.OVAL
            cornerRadius = dp(ThemeConfig.cornerRadius(palette, 14)).toFloat()
            setColor(panel)
            setStroke(dp(1), accent)
        }
    }

    private fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val c = value / 255.0
            return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(AndroidColor.red(color)) +
            0.7152 * channel(AndroidColor.green(color)) +
            0.0722 * channel(AndroidColor.blue(color))
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
