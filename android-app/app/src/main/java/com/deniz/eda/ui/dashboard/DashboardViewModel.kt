package com.deniz.eda.ui.dashboard

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deniz.eda.core.Settings
import com.deniz.eda.data.db.EdaDatabase
import com.deniz.eda.utils.BatteryUtils
import com.deniz.eda.utils.EdaLog
import com.deniz.eda.utils.SystemTools
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
        val kullaniciAdi: String = "",
        val sonLoglar: List<String> = emptyList(),
        // ═══ AI Durumu ═══
        val ollamaStatus: String = "...",
        val groqStatus: String = "...",
        val pollinationsStatus: String = "...",
        val internetStatus: Boolean = false,
        val aiModel: String = "..."
    )

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val db = EdaDatabase.get(app)

    fun yenile() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(100)
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
            val saat = String.format(Locale.US, "%02d:%02d %s",
                (((simdi.get(Calendar.HOUR) + 11) % 12) + 1), simdi.get(Calendar.MINUTE),
                if (simdi.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM")
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

            // ═══ AI Durumu kontrol ═══
            val internet = try { SystemTools.checkInternet(ctx) } catch (e: Exception) { false }
            val ollama = try {
                withContext(Dispatchers.IO) { SystemTools.runTool("CHECK_OLLAMA", ctx) }
            } catch (e: Exception) { "Ollama kontrol edilemedi" }
            val groq = try {
                withContext(Dispatchers.IO) { SystemTools.runTool("CHECK_GROQ", ctx) }
            } catch (e: Exception) { "Groq yok" }
            val pollinations = try {
                withContext(Dispatchers.IO) { SystemTools.runTool("CHECK_POLLINATIONS", ctx) }
            } catch (e: Exception) { "Pollinations hata" }

            _state.value = DashboardState(
                pilYuzde = pilYuzde,
                pilDurum = pilDurum,
                internet = internet,
                aiSaglayici = aiAd,
                mod = Settings.sonMod.ifBlank { "KAPALI" },
                guvenlikAktif = Settings.guvenlikModuAktif,
                arabaAktif = Settings.arabaModuAktif,
                ogrenmeAktif = Settings.ogrenmeAktif,
                hafizaSayi = hafizaSayi,
                gunlukSayi = gunlukSayi,
                hatirlatmaSayi = hatirlatmaSayi,
                logSayi = logSayi,
                saat = saat,
                tarih = tarih,
                kullaniciAdi = Settings.kullaniciAdi,
                sonLoglar = EdaLog.oku(ctx).takeLast(15).reversed(),
                ollamaStatus = ollama,
                groqStatus = groq,
                pollinationsStatus = pollinations,
                internetStatus = internet,
                aiModel = if (Settings.aiProvider.equals("ollama", ignoreCase = true)) "gemma2:2b" else Settings.gemmaModel
            )
        }
    }

    private fun aktifAiSaglayici(): String {
        val secim = Settings.aiProvider.ifBlank { "ollama" }.lowercase()
        val sirali = mutableListOf<String>()
        when (secim) {
            "groq" -> {
                if (Settings.groqApiKey.isNotBlank()) sirali.add("Groq")
                sirali.add("Pollinations")
                sirali.add("Ollama · Termux (gemma2:2b)")
            }
            "ollama" -> {
                sirali.add("Gemma 3 1B")
                sirali.add("Pollinations")
                if (Settings.groqApiKey.isNotBlank()) sirali.add("Groq")
            }
            else -> {
                sirali.add("Pollinations")
                sirali.add("Gemma 3 1B")
                if (Settings.groqApiKey.isNotBlank()) sirali.add("Groq")
            }
        }
        return sirali.joinToString(" → ")
    }

    fun setAiProvider(provider: String) {
        Settings.aiProvider = provider
        yenile()
    }
}
