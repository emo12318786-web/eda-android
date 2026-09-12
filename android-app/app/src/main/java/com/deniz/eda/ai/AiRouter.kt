package com.deniz.eda.ai

import com.deniz.eda.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orijinal "AI ZINCIRI: OpenRouter > DeepSeek > Groq/Gemma" mantiginin karsiligi.
 * Sirayla dener, ilk basarili cevabi doner; hicbiri calismazsa null doner ve
 * CommandProcessor kullaniciya "internet/AI baglantisi yok" tarzi bir mesaj verir.
 *
 * NOT: API anahtarlari Settings uzerinden (SharedPreferences) okunur - ayni
 * orijinal script'teki gibi kullanicidan alinip saklanmasi gerekir. Bu
 * ortamda internet erisimi olmadigi icin bu cagrilar test edilemedi; model
 * adlari/endpoint'ler saglayicilarin genel bilinen OpenAI-uyumlu semasina
 * gore yazildi, gerekirse Settings uzerinden model adini degistirebilirsin.
 */
object AiRouter {

    private data class Saglayici(val ad: String, val baseUrl: String, val model: String, val apiKey: () -> String)

    private fun saglayicilar(): List<Saglayici> = listOf(
        Saglayici("OpenRouter", "https://openrouter.ai/api/v1/", "google/gemma-3-27b-it:free") { Settings.openrouterApiKey },
        Saglayici("DeepSeek", "https://api.deepseek.com/", "deepseek-chat") { Settings.deepseekApiKey },
        Saglayici("Groq", "https://api.groq.com/openai/v1/", "gemma2-9b-it") { Settings.groqApiKey }
    )

    suspend fun sor(
        kullaniciSorusu: String,
        sistemMesaji: String = "Sen Eda adında, kısa ve samimi cevaplar veren Türkçe bir sesli asistansın."
    ): String? = withContext(Dispatchers.IO) {
        for (s in saglayicilar()) {
            val anahtar = s.apiKey()
            if (anahtar.isBlank()) continue
            try {
                val api = OpenAiCompatibleApi.olustur(s.baseUrl)
                val istek = ChatCompletionRequest(
                    model = s.model,
                    messages = listOf(
                        ChatMessage("system", sistemMesaji),
                        ChatMessage("user", kullaniciSorusu)
                    )
                )
                val yanit = api.sohbetTamamla("Bearer $anahtar", istek)
                if (yanit.isSuccessful) {
                    val cevap = yanit.body()?.choices?.firstOrNull()?.message?.content?.trim()
                    if (!cevap.isNullOrBlank()) return@withContext cevap
                }
            } catch (e: Exception) {
                continue
            }
        }
        null
    }
}
