package com.deniz.eda.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.appcompat.widget.SwitchCompat
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.deniz.eda.R
import com.deniz.eda.core.Settings
import com.deniz.eda.panel.PanelActivity
import com.deniz.eda.service.EdaForegroundService
import com.deniz.eda.ui.dashboard.DashboardActivity

class MainActivity : AppCompatActivity() {

    private lateinit var durumText: TextView
    private lateinit var kullaniciAdiInput: EditText
    private lateinit var groqInput: EditText
    private lateinit var deepseekInput: EditText
    private lateinit var openrouterInput: EditText
    private lateinit var navasanInput: EditText

    private val izinIstegi = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { sonuclar ->
        // Sadece mikrofon izni zorunlu - konum/SMS/bildirim olmadan da servis
        // baslayabilir, sadece o komutlar calismaz.
        val mikrofonVar = sonuclar[Manifest.permission.RECORD_AUDIO] == true
        if (mikrofonVar) {
            servisiBaslat()
        } else {
            durumText.text = "⚠️ Mikrofon izni olmadan Eda çalışamaz."
        }
    }

    override fun onResume() {
        super.onResume()
        // ═══ وضعیت واقعی رو از Settings بخون ═══
        durumText.text = when (Settings.sonMod) {
            "AKTIF" -> "✅ Eda çalışıyor - 'Eda' de ve dinlemeye başlasın"
            "UYKU" -> "💤 Eda uyku modunda"
            else -> "💤 Servis durduruldu"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Settings.init(this)

        durumText = findViewById(R.id.durumText)
        kullaniciAdiInput = findViewById(R.id.kullaniciAdiInput)
        groqInput = findViewById(R.id.groqInput)
        deepseekInput = findViewById(R.id.deepseekInput)
        openrouterInput = findViewById(R.id.openrouterInput)
        navasanInput = findViewById(R.id.navasanInput)

        // Mevcut ayarlari alanlara doldur.
        kullaniciAdiInput.setText(Settings.kullaniciAdi)
        groqInput.setText(Settings.groqApiKey)
        deepseekInput.setText(Settings.deepseekApiKey)
        openrouterInput.setText(Settings.openrouterApiKey)
        navasanInput.setText(Settings.navasanApiKey)

        findViewById<android.widget.Button>(R.id.baslatButon).setOnClickListener { izinleriKontrolEt() }
        findViewById<android.widget.Button>(R.id.durdurButon).setOnClickListener { servisiDurdur() }
        findViewById<android.widget.Button>(R.id.kaydetButon).setOnClickListener { ayarlariKaydet() }

        // ═══ داشبورد ═══
        // ═══ تنظیمات پیشرفته ═══
        val batteryNotifierSwitch = findViewById<SwitchCompat>(R.id.batteryNotifierSwitch)
        val derinUykuSwitch = findViewById<SwitchCompat>(R.id.derinUykuSwitch)
        val pilBildirimSwitch = findViewById<SwitchCompat>(R.id.pilBildirimSwitch)
        val sessizModSwitch = findViewById<SwitchCompat>(R.id.sessizModSwitch)
        val pilBildirimSpinner = findViewById<Spinner>(R.id.pilBildirimSpinner)

        // مقدار اولیه
        batteryNotifierSwitch.isChecked = Settings.batteryNotifierAktif
        derinUykuSwitch.isChecked = Settings.derinUyku
        pilBildirimSwitch.isChecked = Settings.pilBildirimAktif
        sessizModSwitch.isChecked = Settings.sessizMod

        // Spinner: 15/30/60/120 dakika
        val pilSecenekler = listOf("15 dakika", "30 dakika", "60 dakika", "120 dakika")
        val pilDegerler = listOf(15, 30, 60, 120)
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, pilSecenekler)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        pilBildirimSpinner.adapter = spinnerAdapter

        // مقدار فعلی
        val mevcutIndex = pilDegerler.indexOf(Settings.pilBildirimAraligiDk)
        if (mevcutIndex >= 0) pilBildirimSpinner.setSelection(mevcutIndex)

        // ═══ لیسنرها ═══
        batteryNotifierSwitch.setOnCheckedChangeListener { _, checked ->
            Settings.batteryNotifierAktif = checked
            val intent = Intent(this, com.deniz.eda.service.BatteryNotifierService::class.java)
            if (checked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Toast.makeText(this, "🔋 Pil izleyici başlatıldı", Toast.LENGTH_SHORT).show()
            } else {
                stopService(intent)
                Toast.makeText(this, "🔋 Pil izleyici durduruldu", Toast.LENGTH_SHORT).show()
            }
        }

        derinUykuSwitch.setOnCheckedChangeListener { _, checked ->
            Settings.derinUyku = checked
            Toast.makeText(this, if (checked) "🛌 Derin uyku aktif" else "😴 Uyku bidar", Toast.LENGTH_SHORT).show()
        }

        pilBildirimSwitch.setOnCheckedChangeListener { _, checked ->
            Settings.pilBildirimAktif = checked
        }

        sessizModSwitch.setOnCheckedChangeListener { _, checked ->
            Settings.sessizMod = checked
        }

        pilBildirimSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                Settings.pilBildirimAraligiDk = pilDegerler[position]
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        findViewById<android.widget.Button>(R.id.dashboardButon).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }

        // ═══ کنترل پنل ═══
        findViewById<android.widget.Button>(R.id.panelButon).setOnClickListener {
            startActivity(Intent(this, PanelActivity::class.java))
        }
    }

    private fun ayarlariKaydet() {
        Settings.kullaniciAdi = kullaniciAdiInput.text.toString().ifBlank { Settings.KULLANICI_ADI_VARSAYILAN }
        Settings.groqApiKey = groqInput.text.toString().trim()
        Settings.deepseekApiKey = deepseekInput.text.toString().trim()
        Settings.openrouterApiKey = openrouterInput.text.toString().trim()
        Settings.navasanApiKey = navasanInput.text.toString().trim()
        Toast.makeText(this, "Ayarlar kaydedildi", Toast.LENGTH_SHORT).show()
    }

    private fun izinleriKontrolEt() {
        val gerekliIzinler = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gerekliIzinler.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        izinIstegi.launch(gerekliIzinler.toTypedArray())
    }

    private fun servisiBaslat() {
        val intent = Intent(this, EdaForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        durumText.text = "✅ Eda çalışıyor - 'Eda' de ve dinlemeye başlasın"
    }

    private fun servisiDurdur() {
        stopService(Intent(this, EdaForegroundService::class.java))
        durumText.text = "💤 Servis durduruldu"
    }
}
