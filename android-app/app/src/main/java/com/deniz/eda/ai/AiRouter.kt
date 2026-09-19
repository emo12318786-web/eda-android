package com.deniz.eda.ai

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

    private const val OLLAMA_URL = "http://192.168.1.2:11434/v1/"

    private fun saglayicilar(): List<Saglayici> = listOf(
        // ═══ ۱. Pollinations (رایگان، بدون API Key) ═══
        Saglayici(
            ad = "Pollinations",
            baseUrl = "https://text.pollinations.ai/openai/",
            model = "openai",
            apiKey = { "no-key" },
            needsKey = false
        ),
        // ═══ ۲. Ollama Gemma2 (شبکه محلی) ═══
        Saglayici(
            ad = "Ollama-Gemma2",
            baseUrl = OLLAMA_URL,
            model = "gemma2:2b",
            apiKey = { "ollama-local" },
            needsKey = false
        ),
        // ═══ ۳. Groq ═══
        Saglayici(
            ad = "Groq",
            baseUrl = "https://api.groq.com/openai/v1/",
            model = "gemma2-9b-it",
            apiKey = { Settings.groqApiKey },
            needsKey = true
        ),
        // ═══ ۴. OpenRouter ═══
        Saglayici(
            ad = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1/",
            model = "google/gemma-2-9b-it:free",
            apiKey = { Settings.openrouterApiKey },
            needsKey = true
        ),
        // ═══ ۵. DeepSeek ═══
        Saglayici(
            ad = "DeepSeek",
            baseUrl = "https://api.deepseek.com/",
            model = "deepseek-chat",
            apiKey = { Settings.deepseekApiKey },
            needsKey = true
        )
    )

    suspend fun sor(
        kullaniciSorusu: String,
        sistemMesaji: String = "Sen Eda'sın. Kullanıcıya 'denizçim' diye hitap et. Maksimum 1 cümle cevap ver. Emoji kullanma."
    ): String? = withContext(Dispatchers.IO) {
        for (s in saglayicilar()) {
            val anahtar = s.apiKey()
            if (s.needsKey && anahtar.isBlank()) continue
            try {
                val api = OpenAiCompatibleApi.olustur(s.baseUrl)
                val istek = ChatCompletionRequest(
                    model = s.model,
                    messages = listOf(
                        ChatMessage("system", sistemMesaji),
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
