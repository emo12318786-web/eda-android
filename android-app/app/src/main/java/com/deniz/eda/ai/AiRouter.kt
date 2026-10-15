package com.deniz.eda.ai

import android.content.Context
import com.deniz.eda.utils.OllamaLocator
import com.deniz.eda.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

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
        sistemMesaji: String = """Sen Eda'sın; kullanıcının kişisel sesli asistanısın.
KURALLAR:
1. Doğal, sıcak ve anlaşılır İstanbul Türkçesiyle konuş.
2. Kullanıcıya yalnızca kayıtlı adı varsa onunla hitap et: "${Settings.kullaniciAdi}". Ad boşsa isim uydurma.
3. Gereksiz resmiyet, tekrar ve kalıp cümlelerden kaçın; genellikle 1-2 kısa cümle kullan.
4. Emoji kullanma; kullanıcı istemedikçe uzun açıklama yapma.
5. Yalnızca gerçekten bir araç sonucu veya komut sonucu varsa işlemin yapıldığını söyle. Yapılmayan bir işlemi yapılmış gibi anlatma.
6. Bu sohbet yolu doğrudan donanım kontrolü yapmaz. Bir donanım isteğinde kısa ve dürüstçe, bunu bu sohbet yolundan çalıştıramadığını söyle; varsa uygulamanın sesli komut yolunu denemesini öner.
7. Kullanıcının sorusuna doğrudan cevap ver; emin olmadığın bilgiyi kesinmiş gibi sunma.""",
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
                    ChatMessage("system", sistemTam + "\n\nAGENT KURALLARI: En fazla 3 araç adımı kullan. Salt okunur araçları <TOOL>ARAÇ_ADI</TOOL> biçiminde çağır. Kullanıcı açıkça bir bilgiyi hafızaya kaydet derse <TOOL>SAVE_MEMORY: metin</TOOL>; günlüğe yaz derse <TOOL>ADD_DIARY: metin</TOOL>; belirli saatli hatırlatma isterse <TOOL>ADD_REMINDER|SAAT|DAKİKA|metin</TOOL> biçimini kullan. Kayıt araçlarını ve telefon eylemlerini yalnızca kullanıcı açıkça istediğinde çalıştır. Araç sonucunu görmeden işlem yapıldığını iddia etme. SMS gönderme veya konum paylaşma araçları bu Agent içinde yoktur; bunları yaptığını söyleme. Kamera aracı yalnızca kamera uygulamasını açar. Güvenlik/car modunu yalnızca bayrak değiştirerek etkinleştirdiğini iddia etme."),
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
                    val match = Regex("<TOOL>\\s*([A-Z_]+)(?:\\s*[:|]\\s*(.*?))?\\s*</TOOL>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(cevap)
                    if (match == null || context == null || aracAdimi >= 3) {
                        sonCevap = cevap.replace(Regex("<TOOL>.*?</TOOL>", RegexOption.DOT_MATCHES_ALL), "").trim()
                        break
                    }
                    val toolName = match.groupValues[1].uppercase(Locale.ROOT)
                    val allowlist = setOf(
                        "CHECK_INTERNET", "CHECK_OLLAMA", "CHECK_GROQ",
                        "CHECK_POLLINATIONS", "GET_BATTERY", "GET_AI", "GET_MODE", "FULL_CHECK",
                        "GET_MEMORY", "GET_DIARY", "GET_REMINDERS", "GET_LEARNED", "GET_TIME", "GET_DATE",
                        "SAVE_MEMORY", "ADD_DIARY", "ADD_REMINDER",
                        "FLASHLIGHT_ON", "FLASHLIGHT_OFF", "BEEP_ON", "BEEP_OFF",
                        "MEDIA_PLAY", "MEDIA_PAUSE", "MEDIA_NEXT", "MEDIA_PREVIOUS",
                        "OPEN_SETTINGS", "OPEN_TTS_SETTINGS", "OPEN_CAMERA", "FIND_PHONE",
                        "STOP_FIND_PHONE", "LOCK_PHONE", "RUN_SYSTEM_TEST", "LEARNING_ON", "LEARNING_OFF"
                    )
                    if (toolName !in allowlist) {
                        sonCevap = "Bu araç EDA'da izinli değil."
                        break
                    }
                    val toolArgs = match.groupValues.getOrNull(2).orEmpty().trim()
                    val toolResult = com.deniz.eda.utils.SystemTools.runTool(toolName, context, toolArgs)
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
