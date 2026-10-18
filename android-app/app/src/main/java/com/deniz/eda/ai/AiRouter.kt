package com.deniz.eda.ai

import android.content.Context
import com.deniz.eda.core.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object AiRouter {

    // Son basarili AI saglayicisinin adi (dashboard log icin)
    @Volatile
    var lastProvider: String = ""

    @Volatile
    private var nativeGemmaSession: GemmaSessionManager? = null

    private fun nativeSession(context: Context): GemmaSessionManager =
        nativeGemmaSession ?: synchronized(this) {
            nativeGemmaSession ?: GemmaSessionManager(context.applicationContext).also {
                nativeGemmaSession = it
            }
        }

    /** Dashboard reads the same live session used by inference; never creates a fake status session. */
    fun nativeGemmaStatus(context: Context): GemmaSessionManager.Status? =
        nativeGemmaSession?.status()

    private data class Saglayici(
        val ad: String,
        val baseUrl: String,
        val model: String,
        val apiKey: () -> String,
        val needsKey: Boolean = true
    )

    // Ollama, Termux üzerinde çalışan çevrimdışı sağlayıcıdır.

    private suspend fun saglayicilar(context: Context): List<Saglayici> {
        val tum = listOf(
        // Ollama: Termux'ta çalışan çevrimdışı model
        Saglayici(
            ad = "Ollama",
            baseUrl = "http://127.0.0.1:11434/v1/",
            model = "gemma2:2b",
            apiKey = { "ollama-local" },
            needsKey = false
        ),
        // Pollinations: ücretsiz çevrimiçi yedek
        Saglayici(
            ad = "Pollinations",
            baseUrl = "https://text.pollinations.ai/openai/",
            model = "openai-fast",
            apiKey = { "no-key" },
            needsKey = false
        ),
        // Groq: çevrimiçi yedek
        Saglayici(
            ad = "Groq",
            baseUrl = "https://api.groq.com/openai/v1/",
            model = "llama-3.3-70b-versatile",  // Groq جدید
            apiKey = { Settings.groqApiKey },
            needsKey = true
        )
        )
        val selected = Settings.aiProvider.ifBlank { "ollama" }.lowercase(Locale.ROOT)
        return when (selected) {
            "groq" -> tum.sortedBy { when (it.ad) { "Groq" -> 0; "Pollinations" -> 1; else -> 2 } }
            "pollinations" -> tum.sortedBy { when (it.ad) { "Pollinations" -> 0; "Ollama" -> 1; else -> 2 } }
            else -> tum.sortedBy { when (it.ad) { "Ollama" -> 0; "Pollinations" -> 1; else -> 2 } }
        }
    }

    suspend fun sor(
        kullaniciSorusu: String,
        sistemMesaji: String = """Sen Eda'sın; kullanıcının kişisel sesli asistanısın.
KURALLAR:
1. Doğal, sıcak ve anlaşılır İstanbul Türkçesiyle konuş.
2. Kullanıcıya yalnızca kayıtlı adıyla hitap et: "${Settings.kullaniciAdi}". Ad boşsa isim kullanma. "Denizcim", "canım" veya kayıtlı olmayan lakaplar ekleme.
3. Sorulara yeterince kapsamlı ve tamamlanmış cevap ver. Basit sorularda kısa ol; açıklama isteyen sorularda önemli noktaları atlamadan yanıtla. Kullanıcı özellikle kısa istemedikçe cevabı gereğinden fazla özetleme.
4. Emoji kullanma; kullanıcı istemedikçe uzun açıklama yapma.
6. Yalnızca gerçekten bir araç sonucu veya komut sonucu varsa işlemin yapıldığını söyle. Yapılmayan bir işlemi yapılmış gibi anlatma.
7. Bu sohbet yolu doğrudan donanım kontrolü yapmaz. Bir donanım isteğinde kısa ve dürüstçe, bunu bu sohbet yolundan çalıştıramadığını söyle; varsa uygulamanın sesli komut yolunu denemesini öner.
8. Kullanıcının sorusuna doğrudan cevap ver; emin olmadığın bilgiyi kesinmiş gibi sunma.""",
        context: android.content.Context? = null
    ): String? = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext null

        // Seçilen sağlayıcı ilk sıradadır; Ollama çalışmıyorsa çevrimiçi yedekler denenir.
        for (s in saglayicilar(ctx)) {
            val anahtar = s.apiKey()
            if (s.needsKey && anahtar.isBlank()) {
                android.util.Log.w("AiRouter", "SKIP ${s.ad}: API key boş")
                continue
            }
            android.util.Log.i("AiRouter", "AI_PROVIDER_ATTEMPT provider=${s.ad} model=${s.model}")
            try {
                val api = OpenAiCompatibleApi.olustur(s.baseUrl)
                // İzinli araç açıklamalarını sistem mesajına ekle
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
                        max_tokens = if (s.ad == "Ollama") 1800 else 700
                    )
                    val yanit = api.sohbetTamamla("Bearer $anahtar", istek)
                    if (!yanit.isSuccessful) {
                        val hataGovdesi = try {
                            yanit.errorBody()?.string()?.take(500).orEmpty()
                        } catch (_: Exception) { "" }
                        android.util.Log.w("AiRouter", "HTTP_FAIL provider=${s.ad} code=${yanit.code()} body=$hataGovdesi")
                        break
                    }
                    val cevap = yanit.body()?.choices?.firstOrNull()?.message?.content?.trim()
                    if (cevap.isNullOrBlank()) {
                        android.util.Log.w("AiRouter", "EMPTY_RESPONSE provider=${s.ad} http=${yanit.code()}")
                        break
                    }
                    android.util.Log.d("AiRouter", "AI_RESPONSE_CHUNK provider=${s.ad} model=${s.model} agentStep=$aracAdimi chars=${cevap.length}")
                    val match = Regex("<TOOL>\\s*([A-Z_]+)(?:\\s*[:|]\\s*(.*?))?\\s*</TOOL>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(cevap)
                    if (match == null || context == null || aracAdimi >= 3) {
                        sonCevap = cevap.replace(Regex("<TOOL>.*?</TOOL>", RegexOption.DOT_MATCHES_ALL), "").trim()
                        break
                    }
                    val toolName = match.groupValues[1].uppercase(Locale.ROOT)
                    val allowlist = setOf(
                        "CHECK_INTERNET", "CHECK_GROQ", "CHECK_OLLAMA",
                        "CHECK_POLLINATIONS", "GET_BATTERY", "GET_AI", "GET_MODE", "FULL_CHECK",
                        "GET_MEMORY", "GET_DIARY", "GET_REMINDERS", "GET_LEARNED", "GET_TIME", "GET_DATE",
                        "SAVE_MEMORY", "ADD_DIARY", "ADD_REMINDER",
                        "FLASHLIGHT_ON", "FLASHLIGHT_OFF", "BEEP_ON", "BEEP_OFF",
                        "MEDIA_PLAY", "MEDIA_PAUSE", "MEDIA_NEXT", "MEDIA_PREVIOUS",
                        "OPEN_SETTINGS", "OPEN_TTS_SETTINGS", "OPEN_CAMERA", "FIND_PHONE",
                        "STOP_FIND_PHONE", "LOCK_PHONE", "RUN_SYSTEM_TEST", "LEARNING_ON", "LEARNING_OFF",
                        "OPEN_APP", "OPEN_WIFI_SETTINGS", "OPEN_SOUND_SETTINGS", "OPEN_DISPLAY_SETTINGS"
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
                if (!sonCevap.isNullOrBlank()) {
                    android.util.Log.i("AiRouter", "AI_RESPONSE_SUCCESS provider=${s.ad} model=${s.model} agentSteps=$aracAdimi chars=${sonCevap.length}")
                    lastProvider = s.ad
                    val kayitliAd = Settings.kullaniciAdi.trim()
                    return@withContext sonCevap
                        .replace(Regex("(?i)denizcim"), kayitliAd.ifBlank { "" })
                        .replace(Regex("\\s{2,}"), " ")
                        .trim()
                }
                android.util.Log.w("AiRouter", "AI_PROVIDER_NO_FINAL_ANSWER provider=${s.ad} model=${s.model}; trying next provider")
                // Sağlayıcı başarısızsa sıradaki yapılandırılmış sağlayıcıya geç.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(
                    "AiRouter",
                    "PROVIDER_EXCEPTION provider=${s.ad} type=${e.javaClass.simpleName} message=${e.message}",
                    e
                )
            }
        }
        null
    }
}
