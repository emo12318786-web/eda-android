package com.deniz.eda.ui.dashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dashboard ekrani — eda.py'deki dashboard() fonksiyonunun grafik karsiligi.
 * Pil, AI, saat, tarih, mod ve DB istatistiklerini gosterir.
 */
class DashboardActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.yenile()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF9C27B0),
                    background = Color(0xFF0F0F1E),
                    surface = Color(0xFF1A1A3E)
                )
            ) {
                DashboardScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "E · D · A  V4  PRO",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE1BEE7)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A3E)
                )
            )
        },
        containerColor = Color(0xFF0F0F1E)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ─── کارت ۱: خوش‌آمد ───
            StatCard(
                baslik = "👋 Merhaba",
                deger = state.kullaniciAdi,
                renk = Color(0xFF9C27B0)
            )

            // ─── کارت ۲: ساعت و تاریخ ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    StatCard(
                        baslik = "⏰ Saat",
                        deger = state.saat,
                        renk = Color(0xFF03A9F4)
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    StatCard(
                        baslik = "📅 Tarih",
                        deger = state.tarih.split(",").firstOrNull() ?: state.tarih,
                        renk = Color(0xFF03A9F4)
                    )
                }
            }

            // ─── کارت ۳: پیل ───
            StatCard(
                baslik = "🔋 Batarya",
                deger = "${state.pilYuzde}%  ${state.pilDurum}",
                renk = when {
                    state.pilYuzde > 50 -> Color(0xFF4CAF50)
                    state.pilYuzde > 20 -> Color(0xFFFF9800)
                    else -> Color(0xFFF44336)
                }
            )

            // ─── کارت ۴: AI Chain ───
            StatCard(
                baslik = "🤖 AI Zinciri",
                deger = state.aiSaglayici,
                renk = Color(0xFF00BCD4)
            )

            // ─── کارت ۵: وضعیت ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    StatCard(
                        baslik = "🎯 Mod",
                        deger = state.mod,
                        renk = if (state.arabaAktif) Color(0xFFFF9800) else Color(0xFF4CAF50)
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    StatCard(
                        baslik = "🛡️ Güvenlik",
                        deger = if (state.guvenlikAktif) "AKTİF" else "PASİF",
                        renk = if (state.guvenlikAktif) Color(0xFF4CAF50) else Color(0xFF757575)
                    )
                }
            }

            // ─── کارت ۶: یادگیری ───
            StatCard(
                baslik = "📚 Öğrenme",
                deger = if (state.ogrenmeAktif) "AKTİF" else "PASİF",
                renk = if (state.ogrenmeAktif) Color(0xFF4CAF50) else Color(0xFF757575)
            )

            // ─── خط جداکننده ───
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color(0xFF2A2A4E)
            )

            // ─── عنوان: دیتابیس ───
            Text(
                "💾 Veritabanı",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE1BEE7),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // ─── کارت‌های DB ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    StatCard(
                        baslik = "🧠 Hafıza",
                        deger = "${state.hafizaSayi} kayıt",
                        renk = Color(0xFF9C27B0)
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    StatCard(
                        baslik = "📖 Günlük",
                        deger = "${state.gunlukSayi} kayıt",
                        renk = Color(0xFF9C27B0)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    StatCard(
                        baslik = "⏰ Hatırlatma",
                        deger = "${state.hatirlatmaSayi} bekliyor",
                        renk = Color(0xFF9C27B0)
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    StatCard(
                        baslik = "📋 Log",
                        deger = "${state.logSayi} kayıt",
                        renk = Color(0xFF9C27B0)
                    )
                }
            }

            // ─── دکمه رفرش ───
            Button(
                onClick = { viewModel.yenile() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("🔄 Yenile")
            }

            // ─── پایین ───
            Text(
                "🎙️ Söyle: Eda ➜ Uyandır",
                fontSize = 12.sp,
                color = Color(0xFF757575),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun StatCard(
    baslik: String,
    deger: String,
    renk: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A3E)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                baslik,
                fontSize = 12.sp,
                color = Color(0xFF9E9E9E)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                deger,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = renk
            )
        }
    }
}
