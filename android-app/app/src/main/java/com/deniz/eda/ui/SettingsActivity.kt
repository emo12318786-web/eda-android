package com.deniz.eda.ui

import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.content.pm.PackageManager
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
        findViewById<Button>(R.id.izinMerkeziButon).setOnClickListener { showPermissionCenter() }

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

        findViewById<Switch>(R.id.voskWakeSwitch).apply {
            isChecked = Settings.voskWakeEnabled
            setOnCheckedChangeListener { _, checked ->
                Settings.voskWakeEnabled = checked
                val action = if (checked) "ACTION_VOSK_WAKE_ON" else "ACTION_VOSK_WAKE_OFF"
                startService(Intent(this@SettingsActivity, com.deniz.eda.service.EdaForegroundService::class.java).setAction(action))
                Toast.makeText(
                    this@SettingsActivity,
                    if (checked) "Çevrimdışı uyandırma etkinleştirildi" else "Çevrimdışı uyandırma kapatıldı",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // ═══ Header ═══
        findViewById<TextView>(R.id.geriButon).setOnClickListener { finish() }

        // Eda’nın sana hitap edeceği isim bu alandan değiştirilebilir.
        val kullaniciAdiInput = findViewById<EditText>(R.id.kullaniciAdiInput)
        val groqInput = findViewById<EditText>(R.id.groqInput)
        val navasanInput = findViewById<EditText>(R.id.navasanInput)
        val wakeWordInput = findViewById<EditText>(R.id.wakeWordInput)

        kullaniciAdiInput.setText(Settings.kullaniciAdi)
        groqInput.setText(Settings.groqApiKey)
        navasanInput.setText(Settings.navasanApiKey)
        wakeWordInput.setText(Settings.wakeWord)

        // ═══ Kaydet ═══
        findViewById<Button>(R.id.kaydetButon).setOnClickListener {
            Settings.kullaniciAdi = kullaniciAdiInput.text.toString().trim()
            Settings.groqApiKey = groqInput.text.toString().trim()
            Settings.navasanApiKey = navasanInput.text.toString().trim()
            val wakeWord = wakeWordInput.text.toString().trim().lowercase()
            if (!wakeWord.matches(Regex("[\\p{L}\\p{N}]{2,20}"))) {
                wakeWordInput.error = "2–20 harften veya rakamdan oluşan tek kelime gir"
                return@setOnClickListener
            }
            val wakeWordChanged = wakeWord != Settings.wakeWord
            Settings.wakeWord = wakeWord
            if (wakeWordChanged) {
                startService(Intent(this, com.deniz.eda.service.EdaForegroundService::class.java).setAction("ACTION_WAKE_WORD_CHANGED"))
            }

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

    private fun showPermissionCenter() {
        val permissions = listOf(
            "Mikrofon" to Manifest.permission.RECORD_AUDIO,
            "Kamera / fener" to Manifest.permission.CAMERA,
            "Kesin konum" to Manifest.permission.ACCESS_FINE_LOCATION,
            "Yaklaşık konum" to Manifest.permission.ACCESS_COARSE_LOCATION,
            "SMS gönderme" to Manifest.permission.SEND_SMS,
            "SMS alma" to Manifest.permission.RECEIVE_SMS,
            "SMS okuma" to Manifest.permission.READ_SMS,
            "Telefon durumu" to Manifest.permission.READ_PHONE_STATE,
            "Arama kayıtları" to Manifest.permission.READ_CALL_LOG,
            "Kişiler" to Manifest.permission.READ_CONTACTS,
            "Bildirimler" to "android.permission.POST_NOTIFICATIONS",
            "Titreşim" to Manifest.permission.VIBRATE,
            "Açılışta başlatma" to Manifest.permission.RECEIVE_BOOT_COMPLETED,
            "Yüksek örnekleme sensörleri" to "android.permission.HIGH_SAMPLING_RATE_SENSORS",
            "Health Connect — nabız" to "android.permission.health.READ_HEART_RATE",
            "Health Connect — uyku" to "android.permission.health.READ_SLEEP",
            "Health Connect — adımlar" to "android.permission.health.READ_STEPS",
            "İnternet" to Manifest.permission.INTERNET,
            "Ağ durumu" to Manifest.permission.ACCESS_NETWORK_STATE,
            "Tam zamanlı alarm" to "android.permission.SCHEDULE_EXACT_ALARM",
            "Sistem ayarlarını değiştirme" to Manifest.permission.WRITE_SETTINGS,
            "Ön plan hizmeti" to "android.permission.FOREGROUND_SERVICE",
            "Mikrofon ön plan hizmeti" to "android.permission.FOREGROUND_SERVICE_MICROPHONE",
            "Veri eşitleme hizmeti" to "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            "Sağlık hizmeti" to "android.permission.FOREGROUND_SERVICE_HEALTH",
            "Erişilebilirlik hizmeti" to "SPECIAL_ACCESSIBILITY",
            "Cihaz yöneticisi" to "SPECIAL_DEVICE_ADMIN",
            "Termux komut çalıştırma" to "com.termux.permission.RUN_COMMAND"
        )
        fun durum(p: String): String = when (p) {
            "SPECIAL_ACCESSIBILITY" -> "Sistem ayarından etkinleştirilir"
            "SPECIAL_DEVICE_ADMIN" -> "Sistem güvenlik ekranından etkinleştirilir"
            Manifest.permission.WRITE_SETTINGS -> if (AndroidSettings.System.canWrite(this)) "Verildi" else "Verilmedi"
            "android.permission.health.READ_HEART_RATE", "android.permission.health.READ_SLEEP", "android.permission.health.READ_STEPS" -> "Health Connect içinden kontrol edilir"
            "android.permission.SCHEDULE_EXACT_ALARM" -> "Android alarm ayarından kontrol edilir"
            else -> try { if (ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED) "Verildi" else "Verilmedi / özel erişim" } catch (_: Exception) { "Sistem tarafından yönetilir" }
        }
        fun open(p: String) {
            try {
                when (p) {
                    "SPECIAL_ACCESSIBILITY" -> startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
                    "SPECIAL_DEVICE_ADMIN" -> startActivity(Intent(AndroidSettings.ACTION_SECURITY_SETTINGS))
                    Manifest.permission.WRITE_SETTINGS -> startActivity(Intent(AndroidSettings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + packageName)))
                    "android.permission.SCHEDULE_EXACT_ALARM" -> if (android.os.Build.VERSION.SDK_INT >= 31) startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + packageName))) else startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName)))
                    "android.permission.health.READ_HEART_RATE", "android.permission.health.READ_SLEEP", "android.permission.health.READ_STEPS" -> try { startActivity(Intent("android.health.connect.action.HEALTH_HOME")) } catch (_: Exception) { startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName))) }
                    "android.permission.POST_NOTIFICATIONS" -> if (android.os.Build.VERSION.SDK_INT >= 33) ActivityCompat.requestPermissions(this, arrayOf(p), 270) else startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName)))
                    "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.RECEIVE_BOOT_COMPLETED", "android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_MICROPHONE", "android.permission.FOREGROUND_SERVICE_DATA_SYNC", "android.permission.FOREGROUND_SERVICE_HEALTH", "com.termux.permission.RUN_COMMAND" -> startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName)))
                    else -> if (ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED) Toast.makeText(this, "Bu izin zaten verilmiş", Toast.LENGTH_SHORT).show() else ActivityCompat.requestPermissions(this, arrayOf(p), 270)
                }
            } catch (_: Exception) { Toast.makeText(this, "İlgili Android ayarları açılamadı", Toast.LENGTH_SHORT).show() }
        }
        val labels = permissions.map { (name, p) -> name + " — " + durum(p) }.toTypedArray()
        AlertDialog.Builder(this).setTitle("İzin Merkezi (27)")
            .setItems(labels) { _, which -> open(permissions[which].second) }
            .setPositiveButton("Uygulama ayarları") { _, _ -> startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName))) }
            .setNegativeButton("Kapat", null).show()
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
