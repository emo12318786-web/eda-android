package com.deniz.eda.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.deniz.eda.R
import com.deniz.eda.core.Settings
import com.deniz.eda.service.EdaForegroundService

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
