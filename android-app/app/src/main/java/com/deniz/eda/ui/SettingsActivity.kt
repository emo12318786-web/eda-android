package com.deniz.eda.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.deniz.eda.R
import com.deniz.eda.core.Settings

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

        // ═══ Kaydet ═══
        findViewById<Button>(R.id.kaydetButon).setOnClickListener {
            Settings.kullaniciAdi = kullaniciAdiInput.text.toString().trim()
            Settings.groqApiKey = groqInput.text.toString().trim()
            Settings.navasanApiKey = navasanInput.text.toString().trim()

            Toast.makeText(this, "✅ Ayarlar kaydedildi", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
