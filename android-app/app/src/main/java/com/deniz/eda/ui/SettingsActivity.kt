package com.deniz.eda.ui

import android.os.Bundle
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.widget.ProgressBar
import kotlinx.coroutines.isActive
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
import com.deniz.eda.ai.ConversationHistoryStore
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
        setupMicrophoneControls()

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

        findViewById<Switch>(R.id.conversationHistorySwitch).apply {
            isChecked = Settings.conversationHistoryEnabled
            setOnCheckedChangeListener { _, checked ->
                Settings.conversationHistoryEnabled = checked
                if (!checked) ConversationHistoryStore.clear(this@SettingsActivity)
                Toast.makeText(
                    this@SettingsActivity,
                    if (checked) "Son sohbet bağlamı etkin" else "Sohbet geçmişi kapatıldı ve kayıtlı geçmiş silindi",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        findViewById<Switch>(R.id.sherpaWakeSwitch).apply {
            isChecked = Settings.sherpaWakeEnabled
            setOnCheckedChangeListener { _, checked ->
                Settings.sherpaWakeEnabled = checked
                val action = if (checked) "ACTION_SHERPA_WAKE_ON" else "ACTION_SHERPA_WAKE_OFF"
                startService(Intent(this@SettingsActivity, com.deniz.eda.service.EdaForegroundService::class.java).setAction(action))
                Toast.makeText(this@SettingsActivity,
                    if (checked) "Sherpa ana motoru etkinleştirildi" else "Sherpa ana motoru kapatıldı",
                    Toast.LENGTH_SHORT).show()
            }
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
            val wakeWord = wakeWordInput.text.toString().trim().lowercase(java.util.Locale.ROOT)
            if (!wakeWord.matches(Regex("[a-z0-9]+([ '-][a-z0-9]+)*")) ||
                wakeWord.replace(Regex("[^a-z0-9]"), "").length !in 2..30) {
                wakeWordInput.error = "İngilizce uyandırma kelimesi veya kısa ifade gir (ör. Eda / Hey Eda)"
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
            "Arama yapma (CALL_PHONE)" to Manifest.permission.CALL_PHONE,
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
        AlertDialog.Builder(this).setTitle("İzin Merkezi (28)")
            .setItems(labels) { _, which -> open(permissions[which].second) }
            .setPositiveButton("Uygulama ayarları") { _, _ -> startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName))) }
            .setNegativeButton("Kapat", null).show()
    }


    private data class MicOption(val id: Int, val key: String, val label: String, val device: android.media.AudioDeviceInfo?)

    private fun microphoneOptions(): List<MicOption> {
        val manager = getSystemService(AUDIO_SERVICE) as AudioManager
        val inputs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        } else emptyList()
        val options = mutableListOf(MicOption(-1, "auto", "Otomatik seç (Android)", null))
        inputs.forEach { device ->
            val name = device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Ses cihazı"
            val type = when (device.type) {
                android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Telefon mikrofonu"
                android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Kablolu kulaklık"
                android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
                android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
                android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB mikrofon"
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth mikrofon"
                android.media.AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE kulaklık"
                android.media.AudioDeviceInfo.TYPE_TELEPHONY -> "Telefon sesi"
                else -> "Harici ses girişi"
            }
            val key = "${device.type}|${name}"
            options.add(MicOption(device.id, key, "$type — $name", device))
        }
        return options.distinctBy { it.key }
    }

    private fun setupMicrophoneControls() {
        refreshMicrophoneList()
        findViewById<Button>(R.id.mikrofonTaraButon).setOnClickListener { refreshMicrophoneList(showToast = true) }
        findViewById<Button>(R.id.mikrofonSecButon).setOnClickListener {
            val options = microphoneOptions()
            val labels = options.map { it.label }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Mikrofon seç")
                .setItems(labels) { _, which ->
                    Settings.preferredMicKey = options[which].key
                    refreshMicrophoneList()
                    Toast.makeText(this, "Mikrofon tercihi kaydedildi", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Kapat", null)
                .show()
        }
        findViewById<Button>(R.id.mikrofonTestButon).setOnClickListener {
            testSelectedMicrophone()
        }
    }

    private fun refreshMicrophoneList(showToast: Boolean = false) {
        val options = microphoneOptions()
        val selected = options.firstOrNull { it.key == Settings.preferredMicKey }
            ?: options.first()
        findViewById<TextView>(R.id.mikrofonDurum).text =
            "Seçili tercih: ${selected.label}\nBulunan ses girişleri: ${options.size - 1}\nSon yönlendirme durumu: ${Settings.currentMicStatus}"
        if (showToast) {
            Toast.makeText(this, "Ses girişleri tarandı: ${options.size - 1}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun testSelectedMicrophone() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 271)
            Toast.makeText(this, "Mikrofon iznini verip testi yeniden başlat", Toast.LENGTH_LONG).show()
            return
        }
        val selected = microphoneOptions().firstOrNull { it.key == Settings.preferredMicKey }
        if (selected == null || selected.key == "auto" || selected.device == null) {
            Toast.makeText(this, "Önce kullanmak istediğin harici mikrofonu seç", Toast.LENGTH_LONG).show()
            return
        }
        val manager = getSystemService(AUDIO_SERVICE) as AudioManager
        val device = selected.device
        val isBluetooth = device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            device.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET
        val minBuffer = AudioRecord.getMinBufferSize(
            16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            Toast.makeText(this, "Bu cihazda ses kaydı başlatılamadı", Toast.LENGTH_LONG).show()
            return
        }
        val level = findViewById<ProgressBar>(R.id.mikrofonSeviye)
        val status = findViewById<TextView>(R.id.mikrofonTestDurum)
        findViewById<Button>(R.id.mikrofonTestButon).isEnabled = false
        status.text = "Seçili giriş açılıyor: ${selected.label}. 8 saniye konuş…"
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                var recorder: AudioRecord? = null
                var oldMode = AudioManager.MODE_NORMAL
                var routeRequested = false
                try {
                    oldMode = manager.mode
                    if (isBluetooth) {
                        manager.mode = AudioManager.MODE_IN_COMMUNICATION
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val comm = manager.availableCommunicationDevices.firstOrNull {
                                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO &&
                                    it.productName.toString().equals(device.productName.toString(), ignoreCase = true)
                            } ?: manager.availableCommunicationDevices.firstOrNull {
                                it.type == device.type &&
                                    it.productName.toString().equals(device.productName.toString(), ignoreCase = true)
                            } ?: throw IllegalStateException("Seçilen Bluetooth kulaklık iletişim girişleri arasında yok")
                            if (!manager.setCommunicationDevice(comm)) {
                                throw IllegalStateException("Android seçilen Bluetooth iletişim yolunu kabul etmedi")
                            }
                            routeRequested = true
                            android.util.Log.i("EDA-MIC", "TEST communication route requested=${comm.productName}, id=${comm.id}")
                        } else {
                            @Suppress("DEPRECATION")
                            manager.startBluetoothSco()
                            @Suppress("DEPRECATION")
                            manager.isBluetoothScoOn = true
                            routeRequested = true
                            kotlinx.coroutines.delay(900L)
                        }
                    }
                    recorder = AudioRecord.Builder()
                        .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setSampleRate(16000)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .build()
                        )
                        .setBufferSizeInBytes(maxOf(minBuffer * 2, 8192))
                        .build()
                    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                        throw IllegalStateException("AudioRecord başlatılamadı")
                    }
                    val activeRecorder = recorder ?: throw IllegalStateException("AudioRecord yok")
                    if (!activeRecorder.setPreferredDevice(device)) {
                        throw IllegalStateException("Android seçilen giriş için setPreferredDevice isteğini reddetti")
                    }
                    activeRecorder.startRecording()
                    val routed = activeRecorder.routedDevice
                    val routeMatches = routed != null && routed.id == device.id && routed.type == device.type
                    Settings.currentMicStatus = if (routeMatches) {
                        "DOĞRULANDI: AudioRecord gerçek girişi ${routed?.productName} (id=${routed?.id})"
                    } else {
                        "HATA: Seçilen ${device.productName} (id=${device.id}); gerçek giriş ${routed?.productName ?: "bilinmiyor"} (id=${routed?.id}). Telefon mikrofonuna sessiz geçiş yok."
                    }
                    android.util.Log.i("EDA-MIC", "TEST selected=${device.productName} id=${device.id}; routed=${routed?.productName} id=${routed?.id}; matches=$routeMatches; btRoute=$routeRequested")
                    withContext(Dispatchers.Main) { refreshMicrophoneList() }
                    if (!routeMatches) throw IllegalStateException(Settings.currentMicStatus)
                    val samples = ShortArray(2048)
                    val started = System.currentTimeMillis()
                    var peak = 0
                    while (isActive && System.currentTimeMillis() - started < 8000L) {
                        val count = activeRecorder.read(samples, 0, samples.size)
                        if (count < 0) throw IllegalStateException("Ses okuma hatası: $count")
                        if (count > 0) {
                            var sum = 0.0
                            var localPeak = 0
                            for (i in 0 until count) {
                                val value = kotlin.math.abs(samples[i].toInt())
                                sum += value.toDouble() * value
                                if (value > localPeak) localPeak = value
                            }
                            peak = maxOf(peak, localPeak)
                            val rms = kotlin.math.sqrt(sum / count).toInt()
                            val percent = (rms * 100 / 12000).coerceIn(0, 100)
                            withContext(Dispatchers.Main) {
                                level.progress = percent
                                status.text = "${Settings.currentMicStatus}\nSes seviyesi: $percent% • Tepe: $peak"
                            }
                        }
                    }
                    if (routeMatches) {
                        Settings.currentMicStatus = "DOĞRULANDI: ${routed?.productName}; AudioRecord gerçek girişi seçilen cihaz"
                    }
                } catch (e: Exception) {
                    Settings.currentMicStatus = "HATA: ${e.message ?: "seçilen mikrofon açılamadı"}"
                    android.util.Log.e("EDA-MIC", "microphone test failed: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        status.text = Settings.currentMicStatus
                        refreshMicrophoneList()
                    }
                } finally {
                    try { recorder?.stop() } catch (_: Exception) {}
                    try { recorder?.release() } catch (_: Exception) {}
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) manager.clearCommunicationDevice()
                        else {
                            @Suppress("DEPRECATION")
                            manager.isBluetoothScoOn = false
                            @Suppress("DEPRECATION")
                            manager.stopBluetoothSco()
                        }
                        manager.mode = oldMode
                    } catch (e: Exception) {
                        android.util.Log.w("EDA-MIC", "route cleanup failed: ${e.message}")
                    }
                    withContext(Dispatchers.Main) {
                        findViewById<Button>(R.id.mikrofonTestButon).isEnabled = true
                        refreshMicrophoneList()
                    }
                }
            }
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
