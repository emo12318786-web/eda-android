package com.deniz.eda.ai

import android.content.Context
import com.deniz.eda.utils.OllamaLocator
import com.deniz.eda.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AiRouter {

    private data class Saglayici(
        val ad: String,
        val baseUrl: String,
        val model: String,
        val apiKey: () -> String,
        val needsKey: Boolean = true
    )

    // OLLAMA_URL artik dinamik - OllamaLocator.bul(context) ile alinir

    private suspend fun saglayicilar(context: Context): List<Saglayici> {
        val ollamaUrl = OllamaLocator.bul(context)
        val tum = listOf(
        // ═══ ۱. Ollama Gemma2 (آفلاین، سریع) ═══
        Saglayici(
            ad = "Ollama-Gemma2",
            baseUrl = "$ollamaUrl/v1/",
            model = Settings.gemmaModel,
            apiKey = { "ollama-local" },
            needsKey = false
        ),
        // ═══ ۲. Pollinations (رایگان، آنلاین) ═══
        Saglayici(
            ad = "Pollinations",
            baseUrl = "https://text.pollinations.ai/openai/",
            model = "openai-fast",
            apiKey = { "no-key" },
            needsKey = false
        ),
        // ═══ ۳. Groq ═══
        Saglayici(
            ad = "Groq",
            baseUrl = "https://api.groq.com/openai/v1/",
            model = "llama-3.3-70b-versatile",  // Groq جدید
            apiKey = { Settings.groqApiKey },
            needsKey = true
        )
        )
        // ═══ اولویت: انتخاب کاربر ═══
        val secili = Settings.aiProvider.ifBlank { "pollinations" }.lowercase()
        val oncelikli = tum.filter { it.ad.lowercase().contains(secili) || 
            (secili == "pollinations" && it.ad == "Pollinations") ||
            (secili == "groq" && it.ad == "Groq") ||
            (secili == "gemma" && it.ad.contains("Ollama"))
        }
        val fallback = tum.filter { it !in oncelikli }.sortedBy { if (it.ad.contains("Ollama")) 1 else 0 }
        return oncelikli + fallback
    }

    suspend fun sor(
        kullaniciSorusu: String,
        sistemMesaji: String = """Sen Eda'sın, deniz'in sesli asistanısın. 
KURALLAR:
1. Kullanıcıya 'deniz' diye hitap et.
2. Maksimum 1 cümle cevap ver.
3. Emoji kullanma.
4. ÖNEMLİ: Sen bir METİN asistanısın. Kamera, mikrofon, ışık, uygulama açma gibi DONANIM işlemlerini YAPAMAZSIN. 
5. Eğer kullanıcı senden donanım işlemi isterse: 'Bunu yapamam deniz, ama komut olarak söylemeyi deneyebilirsin' de. ASLA 'yaptım' deme.
6. Sadece SOHBET (selamlaşma, moral, soru-cevap, bilgi) konularında cevap ver.""",
        context: android.content.Context? = null
    ): String? = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext null
        for (s in saglayicilar(ctx)) {
            val anahtar = s.apiKey()
            if (s.needsKey && anahtar.isBlank()) continue
            try {
                val api = OpenAiCompatibleApi.olustur(s.baseUrl)
                // ═══ ابزارها رو به prompt اضافه کن ═══
                val sistemTam = if (context != null) {
                    sistemMesaji + "\n\n" + com.deniz.eda.utils.SystemTools.listTools()
                } else sistemMesaji

                // EDA V5 Agent: en fazla 3 salt-okunur araç adımı; sağlayıcıya göre
                // değişmeyen allowlist SystemTools.runTool içinde uygulanır.
                val mesajlar = mutableListOf(
                    ChatMessage("system", sistemTam + "\n\nAGENT KURALLARI: En fazla 3 araç adımı kullan. Araç gerekiyorsa yalnızca <TOOL>ALLOWLIST_ADI</TOOL> yaz. Araç sonucunu görmeden işlem yapıldığını iddia etme. Bir araç sonucu geldiyse gerekirse bir sonraki aracı seç; aksi halde kısa Türkçe cevap ver. Telefonun ayarlarını, mesajları veya kişisel verileri değiştiren bir araç bu listede yoktur; bunları yapmış gibi davranma."),
                    ChatMessage("user", kullaniciSorusu)
                )
                var sonCevap: String? = null
                var aracAdimi = 0
                while (aracAdimi <= 3) {
                    val istek = ChatCompletionRequest(
                        model = s.model,
                        messages = mesajlar.toList(),
                        temperature = 0.2,
                        max_tokens = 180
                    )
                    val yanit = api.sohbetTamamla("Bearer $anahtar", istek)
                    if (!yanit.isSuccessful) {
                        android.util.Log.w("AiRouter", "⚠️ ${s.ad}: HTTP ${yanit.code()}")
                        break
                    }
                    val cevap = yanit.body()?.choices?.firstOrNull()?.message?.content?.trim()
                    if (cevap.isNullOrBlank()) break
                    android.util.Log.d("AiRouter", "✅ ${s.ad} agent step=$aracAdimi")
                    val match = Regex("<TOOL>\\s*([A-Z_]+)\\s*</TOOL>", RegexOption.IGNORE_CASE).find(cevap)
                    if (match == null || context == null || aracAdimi >= 3) {
                        sonCevap = cevap.replace(Regex("<TOOL>.*?</TOOL>", RegexOption.DOT_MATCHES_ALL), "").trim()
                        break
                    }
                    val toolName = match.groupValues[1].uppercase(Locale.ROOT)
                    val allowlist = setOf(
                        "CHECK_INTERNET", "CHECK_OLLAMA", "CHECK_GROQ",
                        "CHECK_POLLINATIONS", "GET_BATTERY", "GET_AI", "GET_MODE", "FULL_CHECK"
                    )
                    if (toolName !in allowlist) {
                        sonCevap = "Bu araç EDA'da izinli değil."
                        break
                    }
                    val toolResult = com.deniz.eda.utils.SystemTools.runTool(toolName, context)
                    android.util.Log.i("AiRouter", "🔧 Agent tool=$toolName")
                    mesajlar.add(ChatMessage("assistant", cevap))
                    mesajlar.add(ChatMessage("user", "Araç sonucu ($toolName): $toolResult\nBu sonucu doğru yorumla. Gerekirse başka bir izinli araç seç; yoksa kullanıcıya kısa Türkçe cevap ver."))
                    aracAdimi++
                }
                if (!sonCevap.isNullOrBlank()) return@withContext sonCevap
                // Sağlayıcı başarısızsa sıradaki yapılandırılmış sağlayıcıya geç.
            } catch (e: Exception) {
                android.util.Log.e("AiRouter", "❌ ${s.ad}: ${e.message}")
            }
        }
        null
    }
}
