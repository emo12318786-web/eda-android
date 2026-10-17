package com.deniz.eda.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.deniz.eda.ai.GemmaSessionManager
import com.deniz.eda.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Dedicated text-first offline screen for long or complex questions. */
class OfflineDeepQuestionsActivity : ComponentActivity() {
    private lateinit var sessions: GemmaSessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        sessions = GemmaSessionManager(applicationContext)

        var question by mutableStateOf("")
        var answer by mutableStateOf("")
        var status by mutableStateOf("کاملاً آفلاین • نیازمند فایل مدل Gemma 2 2B")
        var busy by mutableStateOf(false)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFB49AFF),
                background = Color(0xFF080A12),
                surface = Color(0xFF121626),
                onBackground = Color.White,
                onSurface = Color.White
            )) {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF080A12))
                        .verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("آفلاین هوشمند", color = Color(0xFFB49AFF), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("برای سؤال‌های طولانی و تحلیل‌های پیچیده", color = Color(0xFF98A2B9), fontSize = 13.sp)
                        }
                        TextButton(onClick = { finish() }) { Text("بازگشت") }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF121626))) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("حالت مستقل از Gemma Mode", color = Color(0xFF64E5E0), fontWeight = FontWeight.Bold)
                            Text("این صفحه از موتور محلی استفاده می‌کند و سؤال را به سرویس آنلاین نمی‌فرستد. برای سؤال‌های بزرگ، مدل Gemma 2 2B مناسب‌تر است؛ فقط یک مدل در هر لحظه بارگذاری می‌شود.", color = Color(0xFFCBD2E3), fontSize = 13.sp)
                        }
                    }
                    Text("سؤال یا متن طولانی", color = Color.White, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                        placeholder = { Text("متن را اینجا بنویس یا جای‌گذاری کن…") },
                        minLines = 5,
                        maxLines = 12,
                        enabled = !busy,
                        shape = RoundedCornerShape(16.dp)
                    )
                    Button(
                        onClick = {
                            val prompt = question.trim()
                            if (prompt.isBlank()) {
                                status = "اول سؤال را وارد کن."
                            } else {
                                busy = true
                                answer = ""
                                status = "مدل محلی در حال بارگذاری و تحلیل است؛ اجرای اول ممکن است طول بکشد…"
                                lifecycleScope.launch {
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            val state = sessions.status()
                                            if (!state.runtimeAvailable) throw IllegalStateException("موتور محلی در APK موجود نیست.")
                                            if (!state.deepModelReady) throw IllegalStateException("فایل GGUF مدل Gemma 2 2B وارد نشده است.")
                                            val system = "تو Eda هستی؛ یک دستیار آفلاین دقیق. متن بلند را مرحله‌به‌مرحله تحلیل کن، نکات مهم را دسته‌بندی کن، اگر لازم بود فرض‌ها و نتیجه را جدا بنویس. به زبان متن کاربر پاسخ بده، اطلاعات را جعل نکن و پاسخ را روشن و منظم نگه دار.\n\n"
                                            sessions.generate(GemmaSessionManager.Task.DEEP, system + "سؤال کاربر:\n" + prompt + "\n\nتحلیل و پاسخ:", 220)
                                        }
                                        answer = result?.trim()?.takeIf { it.isNotBlank() }
                                            ?: "مدل پاسخ تولید نکرد. فایل مدل و گزارش خطا را بررسی کن."
                                        status = if (result.isNullOrBlank()) "پاسخی تولید نشد" else "پاسخ با مدل محلی تولید شد • بدون اینترنت"
                                    } catch (e: Exception) {
                                        answer = e.message ?: "خطای ناشناخته در اجرای مدل"
                                        status = "اجرای آفلاین ناموفق بود"
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) { Text(if (busy) "در حال تحلیل…" else "تحلیل آفلاین", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { question = ""; answer = ""; status = "آماده برای سؤال جدید" },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("پاک کردن") }
                    Text(status, color = Color(0xFF64E5E0), fontSize = 13.sp)
                    if (answer.isNotBlank()) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF121626)), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("پاسخ EDA", color = Color(0xFFB49AFF), fontWeight = FontWeight.Bold)
                                Text(answer, color = Color.White, fontSize = 15.sp, lineHeight = 22.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
