package com.deniz.eda.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deniz.eda.core.Settings
import com.deniz.eda.data.db.EdaDatabase
import com.deniz.eda.utils.BatteryUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Dashboard icin veri saglayici.
 * eda.py'deki dashboard() fonksiyonunun Kotlin/Compose karsiligi.
 */
class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    data class DashboardState(
        val pilYuzde: Int = 0,
        val pilDurum: String = "?",
        val internet: Boolean = false,
        val aiSaglayici: String = "...",
        val mod: String = "AKTIF",
        val guvenlikAktif: Boolean = false,
        val arabaAktif: Boolean = false,
        val ogrenmeAktif: Boolean = true,
        val hafizaSayi: Int = 0,
        val gunlukSayi: Int = 0,
        val hatirlatmaSayi: Int = 0,
        val logSayi: Int = 0,
        val saat: String = "",
        val tarih: String = "",
        val kullaniciAdi: String = ""
    )

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val db = EdaDatabase.get(app)

    fun yenile() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(100)  // ← کمی صبر کن
            val ctx = getApplication<Application>()

            // Pil — با error handling
            var pilYuzde = 0
            var pilDurum = "?"
            try {
                val pil = BatteryUtils.pilBilgisiAl(ctx)
                if (pil != null) {
                    pilYuzde = pil.yuzde
                    pilDurum = pil.durum
                } else {
                    // Fallback: از BatteryManager استفاده کن
                    val bm = ctx.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
                    pilYuzde = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    pilDurum = "OK"
                }
            } catch (e: Exception) {
                android.util.Log.e("Dashboard", "Pil hatası: ${e.message}")
            }
            android.util.Log.d("Dashboard", "Pil: $pilYuzde% ($pilDurum)")

            // AI saglayici
            val aiAd = aktifAiSaglayici()

            // Saat & Tarih
            val simdi = Calendar.getInstance()
            val saat = String.format(Locale.getDefault(), "%02d:%02d",
                simdi.get(Calendar.HOUR_OF_DAY), simdi.get(Calendar.MINUTE))
            val tarih = SimpleDateFormat("dd MMM yyyy, EEEE", Locale("tr", "TR")).format(Date())

            // DB sayilar
            val hafizaSayi = withContext(Dispatchers.IO) { runCatching { db.memoryDao().sayi() }.getOrDefault(0) }
            val logSayi = withContext(Dispatchers.IO) { runCatching { db.logDao().sayi() }.getOrDefault(0) }
            val hatirlatmaSayi = withContext(Dispatchers.IO) {
                runCatching { db.reminderDao().bekleyenler().size }.getOrDefault(0)
            }
            val gunlukSayi = withContext(Dispatchers.IO) {
                runCatching { db.diaryDao().listele(1000).size }.getOrDefault(0)
            }

            _state.value = DashboardState(
                pilYuzde = pilYuzde,
                pilDurum = pilDurum,
                internet = true, // TODO: internet kontrolu
                aiSaglayici = aiAd,
                mod = if (Settings.arabaModuAktif) "ARABA" else "AKTIF",
                guvenlikAktif = Settings.guvenlikModuAktif,
                arabaAktif = Settings.arabaModuAktif,
                ogrenmeAktif = Settings.ogrenmeAktif,
                hafizaSayi = hafizaSayi,
                gunlukSayi = gunlukSayi,
                hatirlatmaSayi = hatirlatmaSayi,
                logSayi = logSayi,
                saat = saat,
                tarih = tarih,
                kullaniciAdi = Settings.kullaniciAdi
            )
        }
    }

    private fun aktifAiSaglayici(): String {
        val sirali = mutableListOf<String>()
        sirali.add("Pollinations")
        sirali.add("Ollama-Gemma2")
        if (Settings.groqApiKey.isNotBlank()) sirali.add("Groq")
        if (Settings.openrouterApiKey.isNotBlank()) sirali.add("OpenRouter")
        if (Settings.deepseekApiKey.isNotBlank()) sirali.add("DeepSeek")
        return sirali.joinToString(" → ")
    }
}
