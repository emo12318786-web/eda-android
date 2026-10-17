package com.deniz.eda.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
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
        gemmaSessions = GemmaSessionManager(applicationContext)

        findViewById<Button>(R.id.gemma3ModelImport).setOnClickListener {
            gemmaModelPicker.launch(arrayOf("application/octet-stream", "*/*"))
        }
        refreshGemmaStatus()

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
