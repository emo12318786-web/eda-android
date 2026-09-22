package com.deniz.eda.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.deniz.eda.R
import com.deniz.eda.core.Settings
import com.deniz.eda.panel.PanelActivity
import com.deniz.eda.service.EdaForegroundService
import com.deniz.eda.service.BatteryNotifierService
import com.deniz.eda.ui.dashboard.DashboardActivity

class MainActivity : AppCompatActivity() {

    private lateinit var durumText: TextView
    private lateinit var durumDeger: TextView
    private lateinit var durumIndicator: android.view.View

    private val izinIstegi = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { sonuclar ->
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

        // ═══ Auto-start BatteryNotifier (مستقل از EDA) ═══
        if (Settings.batteryNotifierAktif) {
            try {
                val pilIntent = Intent(this, BatteryNotifierService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(pilIntent)
                } else {
                    startService(pilIntent)
                }
                android.util.Log.d("MainActivity", "✅ BatteryNotifier auto-start")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "❌ Battery start error: ${e.message}")
            }
        }

        // POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001
                )
            }
        }

        durumText = findViewById(R.id.durumText)
        durumDeger = findViewById(R.id.durumDeger)
        durumIndicator = findViewById(R.id.durumIndicator)

        // ═══ کلیک‌ها ═══
        findViewById<android.view.View>(R.id.ayarlarButon).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<android.widget.Button>(R.id.baslatButon).setOnClickListener {
            izinleriKontrolEt()
        }

        findViewById<android.widget.Button>(R.id.durdurButon).setOnClickListener {
            servisiDurdur()
        }

        findViewById<android.widget.Button>(R.id.dashboardButon).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }

        findViewById<android.widget.Button>(R.id.panelButon).setOnClickListener {
            startActivity(Intent(this, PanelActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        durumGuncelle()
    }

    private fun durumGuncelle() {
        when (Settings.sonMod) {
            "AKTIF" -> {
                durumText.text = "✅ Eda çalışıyor - 'Eda' de ve dinlemeye başlasın"
                durumDeger.text = "Aktif"
                durumDeger.setTextColor(0xFF10B981.toInt())
                durumIndicator.setBackgroundColor(0xFF10B981.toInt())
            }
            "UYKU" -> {
                durumText.text = "💤 Eda uyku modunda"
                durumDeger.text = "Uyku"
                durumDeger.setTextColor(0xFF60A5FA.toInt())
                durumIndicator.setBackgroundColor(0xFF60A5FA.toInt())
            }
            else -> {
                durumText.text = "💤 Servis çalışmıyor"
                durumDeger.text = "Kapalı"
                durumDeger.setTextColor(0xFFEF4444.toInt())
                durumIndicator.setBackgroundColor(0xFFEF4444.toInt())
            }
        }
    }

    private fun izinleriKontrolEt() {
        val gerekliIzinler = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gerekliIzinler.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        izinIstegi.launch(gerekliIzinler.toTypedArray())
    }

    private fun servisiBaslat() {
        // ═══ اول سرویس قبلی رو متوقف کن (اگه هست) ═══
        try {
            val stopIntent = Intent(this, EdaForegroundService::class.java)
            stopService(stopIntent)
            android.util.Log.d("MainActivity", "سرویس قبلی متوقف شد")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "stop error: ${e.message}")
        }

        // ═══ بعد سرویس جدید رو start کن ═══
        val intent = Intent(this, EdaForegroundService::class.java).apply {
            action = "ACTION_BASLAT"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        // ═══ تنظیم sonMod قبل از start ═══
        Settings.sonMod = "AKTIF"

        Toast.makeText(this, "✅ Eda başlatıldı", Toast.LENGTH_SHORT).show()

        // ═══ صبر کن و دوباره status رو بخون ═══
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            durumGuncelle()
        }, 500)
    }

    private fun servisiDurdur() {
        val intent = Intent(this, EdaForegroundService::class.java)
        stopService(intent)
        Settings.sonMod = "KAPALI"
        Toast.makeText(this, "⏹ Eda durduruldu", Toast.LENGTH_SHORT).show()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            durumGuncelle()
        }, 500)
    }
}
