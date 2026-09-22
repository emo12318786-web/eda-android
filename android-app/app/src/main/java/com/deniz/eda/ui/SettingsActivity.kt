package com.deniz.eda.ui

import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.deniz.eda.R
import com.deniz.eda.core.Settings
import com.deniz.eda.service.BatteryNotifierService
import android.content.Intent

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        Settings.init(this)

        // ═══ Header ═══
        findViewById<TextView>(R.id.geriButon).setOnClickListener { finish() }

        // ═══ فیلدها ═══
        val kullaniciAdiInput = findViewById<EditText>(R.id.kullaniciAdiInput)
        val groqInput = findViewById<EditText>(R.id.groqInput)
        val navasanInput = findViewById<EditText>(R.id.navasanInput)

        kullaniciAdiInput.setText(Settings.kullaniciAdi)
        groqInput.setText(Settings.groqApiKey)
        navasanInput.setText(Settings.navasanApiKey)

        // ═══ سوئیچ‌ها ═══
        val batteryNotifierSwitch = findViewById<SwitchCompat>(R.id.batteryNotifierSwitch)
        val derinUykuSwitch = findViewById<SwitchCompat>(R.id.derinUykuSwitch)
        val pilBildirimSwitch = findViewById<SwitchCompat>(R.id.pilBildirimSwitch)
        val sessizModSwitch = findViewById<SwitchCompat>(R.id.sessizModSwitch)
        val pilBildirimSpinner = findViewById<Spinner>(R.id.pilBildirimSpinner)

        batteryNotifierSwitch.isChecked = Settings.batteryNotifierAktif
        derinUykuSwitch.isChecked = Settings.derinUyku
        pilBildirimSwitch.isChecked = Settings.pilBildirimAktif
        sessizModSwitch.isChecked = Settings.sessizMod

        // Spinner
        val pilSecenekler = listOf("15 dakika", "30 dakika", "60 dakika", "120 dakika")
        val pilDegerler = listOf(15, 30, 60, 120)
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, pilSecenekler)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        pilBildirimSpinner.adapter = spinnerAdapter

        val mevcutIndex = pilDegerler.indexOf(Settings.pilBildirimAraligiDk)
        if (mevcutIndex >= 0) pilBildirimSpinner.setSelection(mevcutIndex)

        // Battery switch
        batteryNotifierSwitch.setOnCheckedChangeListener { _, checked ->
            Settings.batteryNotifierAktif = checked
            val intent = Intent(this, BatteryNotifierService::class.java)
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

        // Kaydet
        findViewById<Button>(R.id.kaydetButon).setOnClickListener {
            Settings.kullaniciAdi = kullaniciAdiInput.text.toString().trim()
            Settings.groqApiKey = groqInput.text.toString().trim()
            Settings.navasanApiKey = navasanInput.text.toString().trim()
            Settings.pilBildirimAraligiDk = pilDegerler[pilBildirimSpinner.selectedItemPosition]
            Settings.derinUyku = derinUykuSwitch.isChecked
            Settings.pilBildirimAktif = pilBildirimSwitch.isChecked
            Settings.sessizMod = sessizModSwitch.isChecked

            Toast.makeText(this, "✅ Ayarlar kaydedildi", Toast.LENGTH_SHORT).show()
            finish()
        }

        // Auto-start pil servisi
        if (Settings.batteryNotifierAktif) {
            val pilIntent = Intent(this, BatteryNotifierService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(pilIntent)
            } else {
                startService(pilIntent)
            }
        }
    }
}
