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

                val istek = ChatCompletionRequest(
                    model = s.model,
                    messages = listOf(
                        ChatMessage("system", sistemTam),
                        ChatMessage("user", kullaniciSorusu)
                    ),
                    temperature = 0.3,
                    max_tokens = 150
                )
                val yanit = api.sohbetTamamla("Bearer $anahtar", istek)
                if (yanit.isSuccessful) {
                    val cevap = yanit.body()?.choices?.firstOrNull()?.message?.content?.trim()
                    if (!cevap.isNullOrBlank()) {
                        android.util.Log.d("AiRouter", "✅ ${s.ad}: $cevap")
                        
                        // ═══ چک کن اگه <TOOL> داشت ═══
                        if (context != null && cevap.contains("<TOOL>")) {
                            val match = Regex("<TOOL>(.+?)</TOOL>").find(cevap)
                            val toolName = match?.groupValues?.getOrNull(1)
                            if (toolName != null) {
                                android.util.Log.i("AiRouter", "🔧 Tool: $toolName")
                                val toolResult = com.deniz.eda.utils.SystemTools.runTool(toolName.trim(), context)
                                android.util.Log.i("AiRouter", "🔧 Sonuç: $toolResult")
                                
                                // نتیجه رو به LLM برگردون برای جواب نهایی
                                val istekFinal = ChatCompletionRequest(
                                    model = s.model,
                                    messages = listOf(
                                        ChatMessage("system", sistemMesaji),
                                        ChatMessage("user", "Soru: $kullaniciSorusu\nTool sonucu: $toolResult\nKısa Türkçe cevap ver, deniz diye hitap et.")
                                    ),
                                    temperature = 0.3,
                                    max_tokens = 100
                                )
                                val yanitFinal = api.sohbetTamamla("Bearer $anahtar", istekFinal)
                                if (yanitFinal.isSuccessful) {
                                    val cevapFinal = yanitFinal.body()?.choices?.firstOrNull()?.message?.content?.trim()
                                    if (!cevapFinal.isNullOrBlank()) {
                                        android.util.Log.d("AiRouter", "✅ Final: $cevapFinal")
                                        return@withContext cevapFinal
                                    }
                                }
                                // اگه LLM جواب نداد، خودمون نتیجه رو بگیم
                                return@withContext "Sonuç: $toolResult"
                            }
                        }
                        
                        return@withContext cevap
                    }
                } else {
                    android.util.Log.w("AiRouter", "⚠️ ${s.ad}: HTTP ${yanit.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("AiRouter", "❌ ${s.ad}: ${e.message}")
            }
        }
        null
    }
}
